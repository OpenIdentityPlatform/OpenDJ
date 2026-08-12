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
 * Copyright 2023-2026 3A Systems, LLC.
 */
package org.opends.server.backends.cassandra;

import static org.mockito.Mockito.when;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.server.config.server.CASBackendCfg;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CassandraContainer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;

import java.net.InetSocketAddress;
import java.util.NoSuchElementException;

//docker run --rm -it -p 9042:9042 --name cassandra cassandra

//TestListener refuses a class that declares test methods of its own without sequential=true,
//and the cursor tests below share one storage, so they must not be interleaved either
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class TestCase extends PluggableBackendImplTestCase<CASBackendCfg> {

	private static final String PAGE_INITIAL = "org.openidentityplatform.opendj.cassandra.fetchsize.initial";
	private static final String PAGE_MAX = "org.openidentityplatform.opendj.cassandra.fetchsize";

	CassandraContainer cassandraContainer;
	@Override
	protected Backend createBackend() {
		if(DockerClientFactory.instance().isDockerAvailable()) {
			cassandraContainer = new CassandraContainer<>("cassandra:latest").withExposedPorts(9042);
			cassandraContainer.start();
			InetSocketAddress contactPoint = cassandraContainer.getContactPoint();
			final String contactPointString = String.format("%s:%s", contactPoint.getHostName(), contactPoint.getPort());
			System.setProperty("datastax-java-driver.basic.contact-points.0", contactPointString);
			System.setProperty("datastax-java-driver.basic.load-balancing-policy.local-datacenter", cassandraContainer.getLocalDatacenter());
		}

		//test allow cassandra
		try(CqlSession session=CqlSession.builder()
				.withConfigLoader(DriverConfigLoader.fromDefaults(CASStorage.class.getClassLoader()))
				.build()){
			session.close();
		}catch (AllNodesFailedException e) {
			throw new SkipException("run before test: docker run --rm -it -p 9042:9042 --name cassandra cassandra");
		}
		return new Backend();
	}

	@Override
	protected CASBackendCfg createBackendCfg() {
		CASBackendCfg backendCfg = mockCfg(CASBackendCfg.class);
		when(backendCfg.getBackendId()).thenReturn("CASTestCase");
		when(backendCfg.getDBDirectory()).thenReturn("CASTestCase");
		return backendCfg;
	}

	@AfterClass
	@Override
	public void cleanUp() throws Exception {
		super.cleanUp();
		if(cassandraContainer != null) {
			cassandraContainer.close();
		}
	}

	private static ByteString key(int i) {
		return ByteString.valueOfUtf8(String.format("key%02d", i));
	}

	private static ByteString value(int i) {
		return ByteString.valueOfUtf8("value" + i);
	}

	/**
	 * A cursor reads its page sizes when the storage is built, so pinning them here keeps the query
	 * counts below independent of the driver default (5000 rows) and of the storage defaults.
	 */
	private CASStorage openStorage(int initialPage, int maxPage) throws Exception {
		System.setProperty(PAGE_INITIAL, String.valueOf(initialPage));
		System.setProperty(PAGE_MAX, String.valueOf(maxPage));
		try {
			final CASStorage storage = new CASStorage(createBackendCfg(), null);
			storage.open(AccessMode.READ_WRITE);
			return storage;
		} finally {
			System.clearProperty(PAGE_INITIAL);
			System.clearProperty(PAGE_MAX);
		}
	}

	/** Rows left behind by an interrupted run would break the counts, so the tree starts empty. */
	private static void fill(CASStorage storage, final TreeName tree, final int rows) throws Exception {
		storage.write(new WriteOperation() {
			@Override
			public void run(WriteableTransaction txn) throws Exception {
				txn.deleteTree(tree);
				for (int i = 0; i < rows; i++) {
					txn.put(tree, key(i), value(i));
				}
			}
		});
	}

	private static void dropTree(CASStorage storage, final TreeName tree) {
		try {
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.deleteTree(tree);
				}
			});
		} catch (Exception e) { //a failed cleanup must be visible, but must not hide a test failure
			System.err.println("cannot drop " + tree + ": " + e);
		}
	}

	/**
	 * The driver ResultSet is consumed once and cannot be rewound, so every repositioning that is
	 * not a forward move within the already-fetched rows must run a new server-side slice query.
	 * The old implementation "restarted" the iterator via rc.iterator(), which is a no-op: backward
	 * repositioning returned the wrong row and positionToIndex counted from the current position.
	 */
	@Test
	public void testCursorReposition() throws Exception {
		final CASStorage storage = openStorage(32, 1000);
		final TreeName tree = new TreeName("testCursorReposition", "tree");
		try {
			fill(storage, tree, 40);
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						final CASStorage.CursorImpl impl = (CASStorage.CursorImpl) cursor;
						assertEquals(impl.queryCount, 0); // opening a cursor runs no query

						assertTrue(cursor.positionToKeyOrNext(key(5))); // server-side seek
						assertEquals(cursor.getKey(), key(5));
						assertEquals(impl.queryCount, 1);
						assertTrue(cursor.positionToKeyOrNext(key(5))); // same key: stays, no query
						assertEquals(cursor.getKey(), key(5));
						assertEquals(impl.queryCount, 1);
						assertTrue(cursor.positionToKeyOrNext(key(9))); // forward: served from fetched rows
						assertEquals(cursor.getKey(), key(9));
						assertEquals(cursor.getValue(), value(9));
						assertEquals(impl.queryCount, 1);

						// backward: the old no-op "restart" returned the next remaining row instead
						assertTrue(cursor.positionToKeyOrNext(key(2)));
						assertEquals(cursor.getKey(), key(2));
						assertEquals(cursor.getValue(), value(2));
						assertTrue(cursor.positionToKeyOrNext(ByteString.valueOfUtf8("key021"))); // between rows
						assertEquals(cursor.getKey(), key(3));

						assertTrue(cursor.positionToKey(key(1))); // backward exact match
						assertEquals(cursor.getKey(), key(1));
						final long queries = impl.queryCount;
						assertFalse(cursor.positionToKey(ByteString.valueOfUtf8("key011"))); // missing key
						assertFalse(cursor.isDefined());
						assertEquals(impl.queryCount, queries); // the miss was decided within the page
						assertTrue(cursor.next()); // a miss stops just before the next key (like pdb)
						assertEquals(cursor.getKey(), key(2));
						assertTrue(cursor.positionToKey(key(1)));
						assertTrue(cursor.next()); // next() continues right after the positioned key (DN2ID)
						assertEquals(cursor.getKey(), key(2));

						// positionToIndex counts from the first row, not from the current position
						assertTrue(cursor.positionToIndex(0));
						assertEquals(cursor.getKey(), key(0));
						assertTrue(cursor.positionToIndex(39));
						assertEquals(cursor.getKey(), key(39));
						assertFalse(cursor.positionToIndex(40));

						assertTrue(cursor.positionToLastKey()); // LIMIT 1 query, no partition scan
						assertEquals(cursor.getKey(), key(39));
						assertFalse(cursor.next());
						assertTrue(cursor.positionToKeyOrNext(key(0))); // reposition after exhaustion
						assertEquals(cursor.getKey(), key(0));
						assertFalse(cursor.positionToKeyOrNext(ByteString.valueOfUtf8("key99"))); // beyond last

						// VLVIndex.evaluateVLVRequestByAssertion: seek to the assertion, then to the start
						assertTrue(cursor.positionToKeyOrNext(key(20)) && cursor.positionToIndex(0));
						assertEquals(cursor.getKey(), key(0));
					}

					// EntryContainer.deleteSubtree/renameSubtree walk an ascending key list on one
					// shared id2entry cursor: forward moves must be served from the fetched rows,
					// otherwise every entry costs a page-sized slice of full entries
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						final CASStorage.CursorImpl impl = (CASStorage.CursorImpl) cursor;
						for (int i = 0; i < 40; i++) {
							assertTrue(cursor.positionToKey(key(i)), "missing " + key(i));
							assertEquals(cursor.getValue(), value(i));
						}
						assertEquals(impl.queryCount, 2, "ascending walk took " + impl.queryCount + " queries");
					}

					// DN2ID.ChildrenCursor: reposition to currentKey+0x01 for every row; forward
					// repositioning is served from the fetched rows, so the scan stays at ~2 queries
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						final CASStorage.CursorImpl impl = (CASStorage.CursorImpl) cursor;
						assertTrue(cursor.positionToKeyOrNext(key(0)));
						int rows = 1;
						while (cursor.positionToKeyOrNext(
								new ByteStringBuilder().appendBytes(cursor.getKey()).appendByte(0x01).toByteString())) {
							rows++;
						}
						assertEquals(rows, 40);
						assertEquals(impl.queryCount, 3, "sibling scan took " + impl.queryCount + " queries");
					}
					return null;
				}
			});
		} finally {
			dropTree(storage, tree);
			storage.close();
		}
	}

	/**
	 * Everything a cursor does once its page runs out - continuing a scan, falling back to a slice,
	 * growing the page - only runs on trees bigger than one page, so the page is pinned small here.
	 * With the driver default of 5000 rows none of these branches would be exercised at all.
	 */
	@Test
	public void testCursorPagingAcrossPages() throws Exception {
		final CASStorage storage = openStorage(4, 8);
		final TreeName tree = new TreeName("testCursorPaging", "tree");
		try {
			fill(storage, tree, 40);
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						final CASStorage.CursorImpl impl = (CASStorage.CursorImpl) cursor;
						int rows = 0;
						while (cursor.next()) { // the scan continues across page boundaries
							assertEquals(cursor.getKey(), key(rows));
							assertEquals(cursor.getValue(), value(rows));
							rows++;
						}
						assertEquals(rows, 40);
						assertFalse(cursor.next()); // and stops for good at the end of the partition
						assertEquals(impl.pageSize, 8); // the page grew, but not past the maximum
						assertTrue(impl.queryCount <= 8, "scan took " + impl.queryCount + " queries");
					}

					// forward repositioning falls back to a server-side slice when the page runs out
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						final CASStorage.CursorImpl impl = (CASStorage.CursorImpl) cursor;
						assertTrue(cursor.positionToKeyOrNext(key(0)));
						int rows = 1;
						while (cursor.positionToKeyOrNext(
								new ByteStringBuilder().appendBytes(cursor.getKey()).appendByte(0x01).toByteString())) {
							assertEquals(cursor.getKey(), key(rows));
							rows++;
						}
						assertEquals(rows, 40);
						assertTrue(impl.queryCount <= 9, "sibling scan took " + impl.queryCount + " queries");
					}

					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						// rows far beyond the first page are still reachable in one seek
						assertTrue(cursor.positionToKey(key(37)));
						assertEquals(cursor.getValue(), value(37));
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), key(38));
						assertTrue(cursor.positionToKeyOrNext(ByteString.valueOfUtf8("key385")));
						assertEquals(cursor.getKey(), key(39));
						assertTrue(cursor.positionToIndex(39));
						assertEquals(cursor.getKey(), key(39));
						assertTrue(cursor.positionToIndex(20));
						assertEquals(cursor.getKey(), key(20));
						assertTrue(cursor.positionToLastKey());
						assertEquals(cursor.getKey(), key(39));
						assertFalse(cursor.next());
					}

					// DN2ID.openCursor0: position, then iterate with next() over several pages
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						assertTrue(cursor.positionToKey(key(2)));
						for (int i = 3; i < 40; i++) {
							assertTrue(cursor.next());
							assertEquals(cursor.getKey(), key(i));
						}
						assertFalse(cursor.next());
					}
					return null;
				}
			});
		} finally {
			dropTree(storage, tree);
			storage.close();
		}
	}

	/** Serving forward repositioning from fetched rows relies on the unsigned blob clustering order. */
	@Test
	public void testCursorKeyOrderIsUnsigned() throws Exception {
		final CASStorage storage = openStorage(32, 1000);
		final TreeName tree = new TreeName("testCursorOrder", "tree");
		final ByteString low = ByteString.valueOfBytes(new byte[] { 0x7F });
		final ByteString high = ByteString.valueOfBytes(new byte[] { (byte) 0x80, 0x01 });
		try {
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.deleteTree(tree);
					txn.put(tree, low, value(1));
					txn.put(tree, high, value(2));
				}
			});
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						final CASStorage.CursorImpl impl = (CASStorage.CursorImpl) cursor;
						// with a signed collation 0x80 would sort before 0x7F and these would fail
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), low);
						final long queries = impl.queryCount;
						// {0x80} is above {0x7F} and below {0x80,0x01}, so this is a forward move served
						// from the fetched rows: it is the client-side comparison that is checked here
						assertTrue(cursor.positionToKeyOrNext(ByteString.valueOfBytes(new byte[] { (byte) 0x80 })));
						assertEquals(cursor.getKey(), high);
						assertEquals(impl.queryCount, queries);
						assertFalse(cursor.next()); // {0x80,0x01} is the last key
						assertTrue(cursor.positionToLastKey());
						assertEquals(cursor.getKey(), high);
					}
					return null;
				}
			});
		} finally {
			dropTree(storage, tree);
			storage.close();
		}
	}

	/** A closed cursor is undefined and every navigation on it returns false, like EmptyCursor. */
	@Test
	public void testCursorAfterClose() throws Exception {
		final CASStorage storage = openStorage(32, 1000);
		final TreeName tree = new TreeName("testCursorClose", "tree");
		try {
			fill(storage, tree, 4);
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree);
					assertTrue(cursor.positionToKeyOrNext(key(0)));
					cursor.close();
					assertFalse(cursor.isDefined());
					assertFalse(cursor.next());
					assertFalse(cursor.positionToKey(key(0)));
					assertFalse(cursor.positionToKeyOrNext(key(0)));
					assertFalse(cursor.positionToLastKey());
					assertFalse(cursor.positionToIndex(0));
					try {
						cursor.getKey();
						fail("a closed cursor has no key");
					} catch (NoSuchElementException expected) {
						// a closed cursor has no current row
					}
					cursor.close(); // closing twice is not an error
					return null;
				}
			});
		} finally {
			dropTree(storage, tree);
			storage.close();
		}
	}
}
