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
import org.opends.server.backends.pluggable.spi.Importer;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

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
	 * openTree() and deleteTree() ask the catalog whether the table of a tree is there. The name is
	 * looked up in the form the catalog stores it - an unquoted identifier is folded to upper case
	 * on oracle and to lower case on postgresql - so getting that wrong makes a second open try to
	 * create a table that is already there, and a second delete drop one that is already gone (#885).
	 */
	@Test
	public void testTableOfATreeIsFoundByName() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("testCatalogLookup", "tree");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					txn.put(tree, key(1), value(1));
				}
			});
			// the table is there now: opening the tree again must find it, not create it a second time
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
			assertEquals(storage.read(new ReadOperation<ByteString>() {
				@Override
				public ByteString run(ReadableTransaction txn) throws Exception {
					return txn.read(tree, key(1));
				}
			}), value(1));

			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.deleteTree(tree);
				}
			});
			// and gone now: deleting it again must find nothing rather than drop what is not there
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.deleteTree(tree);
				}
			});
		} finally {
			storage.close();
		}
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

	/**
	 * Each table must be stamped with the tree name it stores: table names are opaque SHA-224
	 * hashes, so without the comment there is no way to tell the trees apart on the database
	 * side (#859). The single quote in the base DN exercises the comment escaping.
	 */
	@Test
	public void testTreeNameStoredAsTableComment() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		// a quote and a backslash in the tree name exercise the literal escaping (backslash is an escape character in mysql)
		final TreeName tree = new TreeName("o=comment'te\\st", "dn2id");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
			assertEquals(readTableComment(storage.getTableName(tree)), tree.toString());
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

	String readTableComment(String tableName) throws Exception {
		final String url = getJdbcUrl();
		final String sql;
		if (url.startsWith("jdbc:postgresql")) {
			sql = "select obj_description('" + tableName + "'::regclass, 'pg_class')";
		} else if (url.startsWith("jdbc:mysql")) {
			sql = "select table_comment from information_schema.tables where table_schema=database() and table_name='" + tableName + "'";
		} else if (url.startsWith("jdbc:oracle")) {
			sql = "select comments from user_tab_comments where table_name='" + tableName.toUpperCase() + "'";
		} else if (url.startsWith("jdbc:sqlserver")) {
			// class=1 is the table itself: major_id is only unique within a class
			sql = "select cast(value as nvarchar(4000)) from sys.extended_properties where class=1 and major_id=object_id('" + tableName + "') and minor_id=0 and name='MS_Description'";
		} else {
			throw new SkipException("no table comment query for " + url);
		}
		try (final Connection con = DriverManager.getConnection(url);
			 final Statement st = con.createStatement();
			 final ResultSet rs = st.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

	void writeTableComment(String tableName, String comment) throws Exception {
		final String url = getJdbcUrl();
		final String sql;
		if (url.startsWith("jdbc:postgresql") || url.startsWith("jdbc:oracle")) {
			sql = "comment on table " + tableName + " is '" + comment + "'";
		} else if (url.startsWith("jdbc:mysql")) {
			sql = "alter table " + tableName + " comment '" + comment + "'";
		} else if (url.startsWith("jdbc:sqlserver")) {
			// exec arguments must be constants or variables: schema_name() cannot be passed inline
			sql = "declare @s sysname = schema_name()"
				+ " exec sys.sp_updateextendedproperty N'MS_Description', N'" + comment + "', N'SCHEMA', @s, N'TABLE', N'" + tableName + "'";
		} else {
			throw new SkipException("no table comment statement for " + url);
		}
		try (final Connection con = DriverManager.getConnection(url);
			 final Statement st = con.createStatement()) {
			st.execute(sql);
		}
	}

	/**
	 * Removes the stored comment, so that the next stamp has to create one rather than replace
	 * it: on sql server that is sp_addextendedproperty, which is the statement reported to wait
	 * for an uncommitted row of another session.
	 */
	void clearTableComment(String tableName) throws Exception {
		final String url = getJdbcUrl();
		if (!url.startsWith("jdbc:sqlserver")) {
			writeTableComment(tableName, "stale"); // the other engines have one statement for both cases
			return;
		}
		try (final Connection con = DriverManager.getConnection(url);
			 final Statement st = con.createStatement()) {
			st.execute("declare @s sysname = schema_name()"
				+ " exec sys.sp_dropextendedproperty N'MS_Description', N'SCHEMA', @s, N'TABLE', N'" + tableName + "'");
		}
	}

	/**
	 * Comment statements are DDL (a metadata lock on mysql, a ddl lock on oracle), so a table
	 * whose stored comment already matches its tree name must not be re-stamped on subsequent
	 * opens - while a stale comment must be refreshed.
	 */
	@Test
	public void testCommentStampSkippedWhenAlreadyStored() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("o=commentSkip", "dn2id");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true); // stamps the freshly created table
				}
			});
			assertEquals(readTableComment(storage.getTableName(tree)), tree.toString());
			// UP_TO_DATE and not FAILED: the statement was skipped, not rejected
			assertEquals(storage.commentTable(tree), JDBCStorage.CommentResult.UP_TO_DATE, "an up-to-date comment was re-stamped");
			writeTableComment(storage.getTableName(tree), "stale");
			assertEquals(storage.commentTable(tree), JDBCStorage.CommentResult.STAMPED, "a stale comment was not re-stamped");
			assertEquals(readTableComment(storage.getTableName(tree)), tree.toString());
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

	/**
	 * A stamp must never queue behind another session's transaction. Comment statements take a
	 * lock (a metadata lock on mysql, a schema modification lock on sql server) and both engines
	 * wait for it without limit by default - lock_wait_timeout is a year, lock_timeout is
	 * infinite - so an unbounded stamp could hang the backend open and, on mysql, park every
	 * other query on that table behind itself. Whether an uncommitted row of another session
	 * conflicts with the statement at all differs between engines and versions, so the assertion
	 * is on the timing: the call comes back rather than waiting for that transaction to end.
	 */
	@Test(timeOut = 180000)
	public void testCommentStampGivesUpOnLock() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("o=commentLock", "dn2id");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
			final String tableName = storage.getTableName(tree);
			clearTableComment(tableName); // no comment stored: the next attempt must issue a statement
			try (final Connection blocker = DriverManager.getConnection(getJdbcUrl())) {
				blocker.setAutoCommit(false);
				try (final PreparedStatement st = blocker.prepareStatement("insert into " + tableName + " (h,k) values (?,?)")) {
					st.setString(1, String.format("%1$-128s", "blocker").replace(' ', 'x'));
					st.setBytes(2, new byte[]{1});
					st.executeUpdate();
				}
				// the row is left uncommitted, so the lock it holds is still there
				final long start = System.currentTimeMillis();
				final JDBCStorage.CommentResult result = storage.commentTable(tree);
				final long elapsedMs = System.currentTimeMillis() - start;
				blocker.rollback();
				// giving up and stamping anyway are both fine here - the engines differ in whether an
				// uncommitted row of another session conflicts with the comment statement at all.
				// Waiting for that session to finish is what must never happen.
				// the bound is 5 s (COMMENT_LOCK_TIMEOUT_SECONDS): the slack is for the connect and the
				// statement around it, not for a regression of the bound itself
				assertTrue(elapsedMs < 20000, "the comment statement waited " + elapsedMs + " ms for a lock, result " + result);
				if (result == JDBCStorage.CommentResult.STAMPED) { // it reported success: the comment must be there
					assertEquals(readTableComment(tableName), tree.toString());
				}
			}
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

	/**
	 * A failing comment stamp must never disturb the transaction that opened the tree: it used
	 * to roll back the caller's connection, silently discarding writes pending in the same
	 * transaction (the way DefaultIndex.afterOpen() writes the trusted flag between openTree() calls).
	 * The write pending during the failing stamp deliberately targets another tree: a statement
	 * left pending on the very table being stamped - a write, or on mysql any statement, since a
	 * transaction holds a shared metadata lock on every table it touched - would make the comment
	 * statement wait for the caller's own lock, which is a shape no production path has.
	 */
	@Test
	public void testCommentFailureLeavesTransactionIntact() throws Exception {
		final TreeName stamped = new TreeName("o=commentFailure", "dn2id");
		final TreeName written = new TreeName("o=commentFailure", "id2entry");
		final JDBCStorage setUp = new JDBCStorage(createBackendCfg(), null);
		try { // create both tables up front, with a storage that stamps them normally
			setUp.open(AccessMode.READ_WRITE);
			setUp.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(stamped, true);
					txn.openTree(written, true);
				}
			});
		} finally {
			setUp.close();
		}
		final AtomicInteger stampAttempts = new AtomicInteger();
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null) {
			@Override
			String readStoredComment(Connection con, String tableName) throws SQLException {
				return null; // pretend the table carries no comment, so a stamp is attempted
			}
			@Override
			Connection newStampConnection(Dialect dialect) throws SQLException {
				stampAttempts.incrementAndGet();
				throw new SQLException("injected comment failure"); // no sql state, no vendor code: not a failure of the moment
			}
		};
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.put(written, key(1), value(1)); // pending in this transaction...
					txn.openTree(stamped, true); // ...while the comment machinery fails
				}
			});
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					assertEquals(txn.read(written, key(1)), value(1), "failing comment stamp discarded a pending write");
					return null;
				}
			});
			// the failure is remembered: an unstampable table is not asked again in this JVM
			assertEquals(storage.commentTable(stamped), JDBCStorage.CommentResult.FAILED);
			assertEquals(stampAttempts.get(), 1, "a failed stamp was reissued");
		} finally {
			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(stamped);
						txn.deleteTree(written);
					}
				});
			} catch (Exception ignored) {}
			storage.close();
		}
	}

	/**
	 * A stamp that failed for a reason of the moment - the lock timeout the statement is given,
	 * a connection that broke - must be attempted again: only a failure saying that this table
	 * cannot be commented at all is remembered, or one contended moment would leave a backend
	 * unstamped until it is restarted.
	 */
	@Test
	public void testTransientStampFailureIsRetried() throws Exception {
		final TreeName tree = new TreeName("o=transientStamp", "dn2id");
		final AtomicInteger stampAttempts = new AtomicInteger();
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null) {
			@Override
			String readStoredComment(Connection con, String tableName) throws SQLException {
				return null; // pretend the table carries no comment, so a stamp is attempted
			}
			@Override
			Connection newStampConnection(Dialect dialect) throws SQLException {
				stampAttempts.incrementAndGet();
				throw new SQLException("injected connection failure", "08006"); // connection exception: a failure of the moment
			}
		};
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true); // stamp #1, fails
				}
			});
			assertEquals(storage.commentTable(tree), JDBCStorage.CommentResult.FAILED);
			assertEquals(stampAttempts.get(), 2, "a stamp that failed for a reason of the moment was not attempted again");
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

	/**
	 * Opening a backend opens every tree it holds - about 25 for a stock suffix - and the first
	 * open after an upgrade stamps them all: the stamps must share one connection rather than
	 * make a physical connect each. An open that finds the comments in place must make none.
	 */
	@Test
	public void testCommentStampsShareOneConnection() throws Exception {
		final TreeName[] trees = {
			new TreeName("o=commentSweep", "dn2id"),
			new TreeName("o=commentSweep", "id2entry"),
			new TreeName("o=commentSweep", "state") };
		final AtomicInteger connects = new AtomicInteger();
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null) {
			@Override
			Connection newStampConnection(Dialect dialect) throws SQLException {
				connects.incrementAndGet();
				return super.newStampConnection(dialect);
			}
		};
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					for (final TreeName tree : trees) {
						txn.openTree(tree, true); // freshly created: every one of them is stamped
					}
				}
			});
			for (final TreeName tree : trees) {
				assertEquals(readTableComment(storage.getTableName(tree)), tree.toString());
			}
			assertEquals(connects.get(), 1, "the stamps of one open did not share a connection");
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					for (final TreeName tree : trees) {
						txn.openTree(tree, true);
					}
				}
			});
			assertEquals(connects.get(), 1, "an open that found every comment in place still connected");
		} finally {
			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						for (final TreeName tree : trees) {
							txn.deleteTree(tree);
						}
					}
				});
			} catch (Exception ignored) {}
			storage.close();
		}
	}

	/**
	 * An import that failed or was cancelled leaves trees holding an incomplete import that is
	 * going to be run again: refreshing statistics of it describes data nobody will query, and on
	 * oracle it is a full scan per table between the failure and its report.
	 */
	@Test
	public void testAbortedImportSkipsStatistics() throws Exception {
		final TreeName tree = new TreeName("o=abortedImport", "dn2id");
		final AtomicInteger refreshes = new AtomicInteger();
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null) {
			@Override
			boolean updateTableStatistics(Connection con, Collection<TreeName> trees) {
				refreshes.incrementAndGet();
				return super.updateTableStatistics(con, trees);
			}
		};
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
			try (final Importer importer = storage.startImport()) {
				importer.put(tree, key(1), value(1));
				importer.aborted(); // what OnDiskMergeImporter reports when the import throws or is cancelled
			}
			assertEquals(refreshes.get(), 0, "statistics were refreshed for an import that was aborted");
			try (final Importer importer = storage.startImport()) {
				importer.put(tree, key(2), value(2));
			}
			assertEquals(refreshes.get(), 1, "statistics were not refreshed for an import that finished");
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

	/**
	 * The statistics refresh must be possible to turn off: on oracle it gathers with
	 * AUTO_SAMPLE_SIZE, a full scan of every table the import wrote.
	 */
	@Test
	public void testStatisticsRefreshCanBeTurnedOff() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("o=statisticsOff", "dn2id");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
			System.setProperty(JDBCStorage.STATISTICS_PROPERTY, "false");
			try (final Connection con = CachedConnection.getConnection(getJdbcUrl())) {
				assertFalse(storage.updateTableStatistics(con, Collections.singleton(tree)),
					"the refresh ran with " + JDBCStorage.STATISTICS_PROPERTY + "=false");
			}
		} finally {
			System.clearProperty(JDBCStorage.STATISTICS_PROPERTY);
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

	/** deleteTree() must forget the tree: statistics refresh iterates known trees and must skip dropped tables. */
	@Test
	public void testDeleteTreeForgetsTree() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("o=deleteTree", "dn2id");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
			assertTrue(storage.listTrees().contains(tree));
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.deleteTree(tree);
				}
			});
			assertFalse(storage.listTrees().contains(tree), "deleteTree() left the tree in the tree-to-table cache");
		} finally {
			storage.close();
		}
	}

	/** A bulk import must refresh optimizer statistics: fresh tables were never analyzed (#859). */
	@Test
	public void testImportRefreshesTableStatistics() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("testImportAnalyze", "tree");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
			suspendAutomaticStatistics(storage.getTableName(tree));
			try (final Importer importer = storage.startImport()) {
				for (int i = 0; i < 40; i++) {
					importer.put(tree, key(i), value(i));
				}
			}
			assertTableStatisticsFresh(storage.getTableName(tree));
			// import swallows statistics failures by design: assert directly that the
			// dialect-specific refresh statement is accepted by this database
			try (final Connection con = CachedConnection.getConnection(getJdbcUrl())) {
				assertTrue(storage.updateTableStatistics(con, Collections.singleton(tree)), "statistics refresh reported failures");
			}
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

	/**
	 * Suspends the automatic statistics upkeep of the engines that have it, so that what the
	 * assertion below sees was produced by the refresh of the import and by nothing else: InnoDB
	 * recalculates innodb_table_stats.n_rows on its own (innodb_stats_auto_recalc is on by
	 * default), which would let the assertion pass with no "analyze table" ever issued.
	 */
	void suspendAutomaticStatistics(String tableName) throws Exception {
		final String url = getJdbcUrl();
		if (!url.startsWith("jdbc:mysql")) {
			return; // nothing refreshes what is asserted below on the other engines within a test run
		}
		try (final Connection con = DriverManager.getConnection(url);
			 final Statement st = con.createStatement()) {
			st.execute("alter table " + tableName + " stats_auto_recalc=0");
		}
	}

	void assertTableStatisticsFresh(String tableName) throws Exception {
		final String url = getJdbcUrl();
		final String sql;
		if (url.startsWith("jdbc:postgresql")) {
			// reltuples stays -1/0 until the first ANALYZE
			sql = "select reltuples::bigint from pg_class where relname='" + tableName + "'";
		} else if (url.startsWith("jdbc:oracle")) {
			// num_rows stays null until dbms_stats gathers statistics
			sql = "select num_rows from user_tables where table_name='" + tableName.toUpperCase() + "'";
		} else if (url.startsWith("jdbc:mysql")) {
			// n_rows in the persistent stats table is refreshed by ANALYZE TABLE, and - with the
			// automatic recalculation suspended above - by nothing else: it stays 0 without it
			sql = "select n_rows from mysql.innodb_table_stats where database_name=database() and table_name='" + tableName + "'";
		} else if (url.startsWith("jdbc:sqlserver")) {
			// last_updated stays null until the first UPDATE STATISTICS
			sql = "select count(*) from sys.stats s cross apply sys.dm_db_stats_properties(s.object_id, s.stats_id) p"
				+ " where s.object_id=object_id('" + tableName + "') and p.last_updated is not null";
		} else {
			throw new SkipException("no statistics query for " + url);
		}
		try (final Connection con = DriverManager.getConnection(url);
			 final Statement st = con.createStatement();
			 final ResultSet rs = st.executeQuery(sql)) {
			assertTrue(rs.next(), "table " + tableName + " not found");
			final long rows = rs.getLong(1);
			assertFalse(rs.wasNull(), "statistics were never gathered for " + tableName);
			assertTrue(rows > 0, "statistics of " + tableName + " look stale: " + rows);
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
