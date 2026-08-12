/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2024-2026 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;



public abstract class TestCase extends PluggableBackendImplTestCase<JDBCBackendCfg> {

	JdbcDatabaseContainer container;

	@BeforeClass
	@Override
	public void setUp() throws Exception {
		if(DockerClientFactory.instance().isDockerAvailable()) {
			try {
				container = getContainer();
				container.start();
			} catch (Exception e) {
				// The database container could not be started (e.g. a slow/flaky image
				// pull or DB initialization failure on CI). Skip the test instead of
				// failing the whole build - container startup is an infrastructure
				// concern, not a regression in the JDBC backend under test.
				throw new SkipException(getContainerDockerCommand());
			}
		}
		try(Connection con = DriverManager.getConnection(createBackendCfg().getDBDirectory())){
			dropStaleTrees(con);
		} catch (Exception e) {
			throw new SkipException(getContainerDockerCommand());
		}
		super.setUp();
	}

	/**
	 * Backend test classes sharing one database map the same tree names to the same tables,
	 * so a previous run may leave trees behind — including entries encrypted with a lost cipher key.
	 */
	static void dropStaleTrees(Connection con) throws SQLException {
		final List<String> stale = new ArrayList<>();
		try (final ResultSet rs = con.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
			while (rs.next()) {
				final String name = rs.getString("TABLE_NAME");
				if (name.toLowerCase().startsWith("opendj_")) {
					stale.add(name);
				}
			}
		}
		try (final Statement st = con.createStatement()) {
			for (final String name : stale) {
				st.execute("drop table " + name);
			}
		}
	}

	@Override
	protected Backend createBackend() {
		return new Backend();
	}

	@Override
	protected JDBCBackendCfg createBackendCfg() {
		JDBCBackendCfg backendCfg = mockCfg(JDBCBackendCfg.class);
		when(backendCfg.getBackendId()).thenReturn(getBackendId());
		when(backendCfg.getDBDirectory()).thenReturn(getJdbcUrl());
		return backendCfg;
	}

	@AfterClass
	@Override
	public void cleanUp() throws Exception {
		super.cleanUp();
		if(container != null) {
			container.close();
		}
	}

	protected abstract JdbcDatabaseContainer<?> getContainer();

	protected abstract String getContainerDockerCommand();

	protected abstract String getBackendId();

	protected abstract String getJdbcUrl();

	private static ByteString key(int i) {
		return ByteString.valueOfUtf8(String.format("key%02d", i));
	}

	private static ByteString value(int i) {
		return ByteString.valueOfUtf8("value" + i);
	}

	/**
	 * Forward repositioning inside the already-fetched batch must be served from the buffer without SQL,
	 * and batch sizes must grow from "fetchsize.initial" to "fetchsize" on sequential reads (#860).
	 */
	@Test
	public void testPositionToKeyOrNextServedFromBuffer() throws Exception {
		System.setProperty("org.openidentityplatform.opendj.jdbc.fetchsize", "8");
		System.setProperty("org.openidentityplatform.opendj.jdbc.fetchsize.initial", "2");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("testCursorBuffer", "tree");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					for (int i = 0; i < 40; i++) {
						txn.put(tree, key(i), value(i));
					}
				}
			});
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						final JDBCStorage.CursorImpl impl = (JDBCStorage.CursorImpl) cursor;

						assertTrue(cursor.next()); // fetch #1: initial batch of 2 (key00, key01)
						assertEquals(cursor.getKey(), key(0));
						assertEquals(impl.fetchCount, 1);
						assertTrue(cursor.next()); // key01 is buffered
						assertEquals(impl.fetchCount, 1);
						assertTrue(cursor.next()); // fetch #2: grown batch of 8 (key02..key09)
						assertEquals(cursor.getKey(), key(2));
						assertEquals(impl.fetchCount, 2);

						// forward repositioning within the fetched range must not run SQL
						assertTrue(cursor.positionToKeyOrNext(key(5)));
						assertEquals(cursor.getKey(), key(5));
						assertEquals(cursor.getValue(), value(5));
						assertEquals(impl.fetchCount, 2);
						assertTrue(cursor.positionToKeyOrNext(ByteString.valueOfUtf8("key051"))); // between rows
						assertEquals(cursor.getKey(), key(6));
						assertEquals(impl.fetchCount, 2);
						assertTrue(cursor.positionToKeyOrNext(key(9))); // last buffered row
						assertEquals(cursor.getKey(), key(9));
						assertEquals(impl.fetchCount, 2);

						assertTrue(cursor.positionToKeyOrNext(key(20))); // fetch #3: beyond the buffer
						assertEquals(cursor.getKey(), key(20));
						assertEquals(impl.fetchCount, 3);
						assertTrue(cursor.positionToKeyOrNext(key(1))); // fetch #4: backward
						assertEquals(cursor.getKey(), key(1));
						assertEquals(impl.fetchCount, 4);

						// emulate DN2ID.ChildrenCursor: reposition to currentKey+0x01 for every row.
						// Before the fix every reposition re-fetched a full batch: 38 fetches here.
						final long fetchesBefore = impl.fetchCount;
						int rows = 1; // standing on key01
						while (cursor.positionToKeyOrNext(
								new ByteStringBuilder().appendBytes(cursor.getKey()).appendByte(0x01).toByteString())) {
							rows++;
						}
						assertEquals(rows, 39); // key01..key39
						assertTrue(impl.fetchCount - fetchesBefore <= 8,
								"sibling scan took " + (impl.fetchCount - fetchesBefore) + " fetches");
					}
					return null;
				}
			});
		} finally {
			System.clearProperty("org.openidentityplatform.opendj.jdbc.fetchsize");
			System.clearProperty("org.openidentityplatform.opendj.jdbc.fetchsize.initial");
			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(tree);
					}
				});
			} catch (Exception ignored) {}
			storage.close();
		}
	}

	/** Buffer-served repositioning relies on the database collating keys in unsigned byte order. */
	@Test
	public void testCursorKeyOrderIsUnsigned() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("testCursorOrder", "tree");
		final ByteString low = ByteString.valueOfBytes(new byte[] { 0x7F });
		final ByteString high = ByteString.valueOfBytes(new byte[] { (byte) 0x80, 0x01 });
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					txn.put(tree, low, value(1));
					txn.put(tree, high, value(2));
				}
			});
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						// with a signed collation 0x80 would sort before 0x7F and these would fail
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), low);
						assertTrue(cursor.positionToKeyOrNext(ByteString.valueOfBytes(new byte[] { (byte) 0x80 })));
						assertEquals(cursor.getKey(), high);
						assertFalse(cursor.next());
						assertTrue(cursor.positionToLastKey());
						assertEquals(cursor.getKey(), high);
					}
					return null;
				}
			});
		} finally {
			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(tree);
					}
				});
			} catch (Exception ignored) {}
			storage.close();
		}
	}

	/** Cursor operations must keep working when the tree spans several "fetchsize" batches. */
	@Test
	public void testCursorCrossesFetchSizeBatches() throws Exception {
		System.setProperty("org.openidentityplatform.opendj.jdbc.fetchsize", "2");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("testCursorBatch", "tree");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					for (int i = 0; i < 7; i++) {
						txn.put(tree, key(i), value(i));
					}
				}
			});
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						for (int i = 0; i < 7; i++) {
							assertTrue(cursor.next(), "next() at " + i);
							assertEquals(cursor.getKey(), key(i));
							assertEquals(cursor.getValue(), value(i));
						}
						assertFalse(cursor.next());
						assertFalse(cursor.isDefined());
						try {
							cursor.getKey();
							fail("getKey() on undefined cursor must fail");
						} catch (NoSuchElementException expected) {}

						assertTrue(cursor.positionToKeyOrNext(key(3)));
						assertEquals(cursor.getKey(), key(3));
						assertTrue(cursor.positionToKeyOrNext(ByteString.valueOfUtf8("key031")));
						assertEquals(cursor.getKey(), key(4));
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), key(5));
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), key(6));
						assertFalse(cursor.next());
						assertFalse(cursor.positionToKeyOrNext(ByteString.valueOfUtf8("z")));
						assertFalse(cursor.isDefined());

						assertTrue(cursor.positionToKey(key(5)));
						assertEquals(cursor.getValue(), value(5));
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), key(6));
						assertFalse(cursor.positionToKey(ByteString.valueOfUtf8("key99")));
						assertFalse(cursor.isDefined());

						assertTrue(cursor.positionToIndex(0));
						assertEquals(cursor.getKey(), key(0));
						assertTrue(cursor.positionToIndex(5));
						assertEquals(cursor.getKey(), key(5));
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), key(6));
						assertFalse(cursor.positionToIndex(7));
						assertFalse(cursor.positionToIndex(-1));

						assertTrue(cursor.positionToLastKey());
						assertEquals(cursor.getKey(), key(6));
						assertFalse(cursor.next());

						assertTrue(cursor.positionToKey(key(0)));
						try {
							cursor.delete();
							fail("delete() on read-only cursor must fail");
						} catch (UnsupportedOperationException expected) {}
					}
					return null;
				}
			});
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						assertTrue(cursor.positionToKey(key(3)));
						cursor.delete();
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), key(4));
					}
					assertNull(txn.read(tree, key(3)));
					assertEquals(txn.getRecordCount(tree), 6);
				}
			});
		} finally {
			System.clearProperty("org.openidentityplatform.opendj.jdbc.fetchsize");
			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(tree);
					}
				});
			} catch (Exception ignored) {}
			storage.close();
		}
	}
}
