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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOnlyStorageException;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
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
		return createBackendCfg(getBackendId());
	}

	/**
	 * A configuration of another backend on the database of this suite: backends sharing one database
	 * URL is a configuration nothing forbids, and what one of them clears must be its own tables.
	 */
	protected JDBCBackendCfg createBackendCfg(String backendId) {
		JDBCBackendCfg backendCfg = mockCfg(JDBCBackendCfg.class);
		when(backendCfg.getBackendId()).thenReturn(backendId);
		when(backendCfg.getDBDirectory()).thenReturn(getJdbcUrl());
		return backendCfg;
	}

	/**
	 * The same, reached over a connection string of the caller's own: the pools of this backend are
	 * keyed by it, so a case wanting connections established differently - in another schema of the
	 * search path, say - asks for them by asking for another url.
	 */
	protected JDBCBackendCfg createBackendCfg(String backendId, String jdbcUrl) {
		final JDBCBackendCfg backendCfg = createBackendCfg(backendId);
		when(backendCfg.getDBDirectory()).thenReturn(jdbcUrl);
		return backendCfg;
	}

	/**
	 * The same, serving the given base DN: what a clear compares the tree stamp of a table against
	 * when it says whether the table is this backend's own or another's (#866).
	 */
	protected JDBCBackendCfg createBackendCfg(String backendId, DN baseDN) {
		final JDBCBackendCfg backendCfg = createBackendCfg(backendId);
		final TreeSet<DN> baseDNs = new TreeSet<>();
		baseDNs.add(baseDN);
		when(backendCfg.getBaseDN()).thenReturn(baseDNs);
		return backendCfg;
	}

	/** Asked of the database itself, by listing its tables, so that no folding rule of the backend is trusted here. */
	protected boolean isExistsTable(String tableName) throws SQLException {
		try (final Connection con = DriverManager.getConnection(getJdbcUrl())) {
			return isExistingTable(con, tableName);
		}
	}

	/** Drops a table behind the back of the storage that owns it, which no code path of the backend does. */
	private void dropTableBehindTheBackend(String tableName) throws SQLException {
		try (final Connection con = DriverManager.getConnection(getJdbcUrl());
			 final Statement st = con.createStatement()) {
			st.execute("drop table " + tableName);
		}
	}

	/** Clears a backend of a test without letting the failure of the clear replace the failure being reported. */
	protected static void clearQuietly(JDBCStorage storage) {
		try {
			storage.removeStorageFiles();
		} catch (Exception ignored) {
		} finally {
			storage.close();
		}
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

	/**
	 * The second property bounding a login is a socket read timeout on mysql, oracle and sql
	 * server: in force for the whole life of the connection it would fail every statement slower
	 * than it - an import batch, the statistics of a freshly loaded table - so it has to be lifted
	 * as soon as the login is through (#872).
	 */
	@Test(timeOut = 120000)
	public void testLoginBoundDoesNotOutliveTheLogin() throws Exception {
		final String url = createBackendCfg().getDBDirectory();
		final CachedConnection.ConnectDialect dialect = CachedConnection.ConnectDialect.of(url);
		assertNotNull(dialect, "the dialect of the container is one this backend bounds: " + CachedConnection.safeUrl(url));
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "2");
		try {
			// the bound this lifts has to be in force first, or the assertion below holds of a
			// connection that never carried one: established here with the very properties the
			// borrow uses, and read back off the socket of this driver
			final Properties bounding = new Properties();
			assertTrue(dialect.bound(url, bounding, 2),
				"the read bound of the login is not set for this dialect, so there is nothing to lift");
			try (final Connection bounded = DriverManager.getConnection(url, bounding)) {
				assertEquals(bounded.getNetworkTimeout(), 2000,
					"the property this dialect names does not bound the socket of its login");
			}

			// a pooled connection would be handed back without being established again
			CachedConnection.cached.invalidate(url);
			try (final Connection con = CachedConnection.getConnection(url)) {
				assertEquals(con.getNetworkTimeout(), 0, "the read bound of the login is still in force");
			}
		} finally {
			System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		}
	}

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
	 * treeExists() has to answer for a table that was never created rather than fail: it is how the
	 * compressed schema tells a backend with nothing to migrate from one whose definitions are still
	 * under the shared prefix (#873), and every other statement of this storage fails outright on a
	 * table that does not exist.
	 */
	@Test
	public void testTreeExistsAnswersForAMissingTable() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName present = new TreeName("testTreeExists", "present");
		final TreeName absent = new TreeName("testTreeExists", "absent");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(present, true);
					assertTrue(txn.treeExists(present));
					assertFalse(txn.treeExists(absent));
				}
			});
			// the read path has to answer as well: export-ldif and verify-index open read-only, where
			// no tree is created and the question cannot be settled by writing one
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					assertTrue(txn.treeExists(present));
					assertFalse(txn.treeExists(absent));
					return null;
				}
			});
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.deleteTree(present);
					assertFalse(txn.treeExists(present));
				}
			});
		} finally {
			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(present);
					}
				});
			} catch (Exception ignored) {}
			storage.close();
		}
	}

	/**
	 * The compressed schema definitions of this backend must live in a table of its own. The tree
	 * name they used to carry held no backend qualifier, so its table name was a constant that every
	 * JDBC backend of every server sharing the database mapped to, and two of them overwrote each
	 * other's token definitions there (#873). The backend of this suite has been opened and populated
	 * by PluggableBackendImplTestCase#setUp, so its own table exists by now.
	 */
	@Test
	public void testCompressedSchemaTableIsQualifiedByBackendId() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			final String shared = JDBCStorage.toTableName(new TreeName("compressed_schema", "compressed_attributes"));
			final String own = JDBCStorage.toTableName(
					new TreeName("compressed_schema_" + getBackendId(), "compressed_attributes"));
			assertFalse(shared.equals(own), "the qualified tree name must map to a table of its own");
			try (final Connection con = DriverManager.getConnection(getJdbcUrl())) {
				assertTrue(isExistingTable(con, own), own + " (this backend's own definitions) is missing");
				assertFalse(isExistingTable(con, shared), shared + " is the table every backend used to share");
			}
		} finally {
			storage.close();
		}
	}

	/**
	 * Reading a tree must not put it up for removal: a clear drops what the catalog of the backend
	 * names (#888), and the compressed schema reads the tree its definitions used to be shared under
	 * - which on a shared database is another backend's to keep (#873). Asking whether the tree is
	 * there is only the first of those reads: the migration counts it and copies it out too, so one
	 * guarded statement would not be enough.
	 * <p>
	 * The two storages are two backends and not one addressing the same database, which is what the
	 * case is about: what a backend owns is recorded in a catalog named after its backend id and
	 * outlives the process that opened the tree, so a second storage of the same id would be shown
	 * the tree its own earlier open had enrolled - and would be right to be.
	 */
	@Test
	public void testProbingATreeDoesNotPutItUpForRemoval() throws Exception {
		final TreeName foreign = new TreeName("testProbe", "foreign");
		final JDBCStorage owner = new JDBCStorage(createBackendCfg(), null);
		owner.open(AccessMode.READ_WRITE);
		owner.write(new WriteOperation() {
			@Override
			public void run(WriteableTransaction txn) throws Exception {
				txn.openTree(foreign, true);
				txn.put(foreign, key(1), value(1));
			}
		});
		owner.close();

		// a second backend on the same database, which never opened that tree - the shape of two
		// backends addressing one database
		final JDBCStorage other = new JDBCStorage(createBackendCfg(getBackendId() + "_probe"), null);
		try {
			other.open(AccessMode.READ_WRITE);
			other.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					// every read the compressed schema runs against a tree it does not own: it asks
					// whether the tree is there, counts it, reads a key of it and walks it (#873)
					assertTrue(txn.treeExists(foreign));
					assertEquals(txn.getRecordCount(foreign), 1);
					assertEquals(txn.read(foreign, key(1)), value(1));
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(foreign)) {
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), key(1));
					}
					return null;
				}
			});
			assertFalse(other.listTrees().contains(foreign), "a tree only read must not be listed for removal");

			other.removeStorageFiles();

			try (final Connection con = DriverManager.getConnection(getJdbcUrl())) {
				assertTrue(isExistingTable(con, JDBCStorage.toTableName(foreign)),
						"clearing one backend dropped a table it had only asked about");
			}
		} finally {
			other.close();
			final JDBCStorage cleanup = new JDBCStorage(createBackendCfg(), null);
			try {
				cleanup.open(AccessMode.READ_WRITE);
				cleanup.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(foreign);
					}
				});
			} catch (Exception ignored) {
			} finally {
				cleanup.close();
			}
		}
	}

	/**
	 * The other side of the same rule: a cursor reads through the non-enrolling name, but deleting
	 * through one writes to the tree, so it is a tree this backend owns and removeStorageFiles()
	 * has to be able to name it.
	 */
	@Test
	public void testDeletingThroughACursorPutsTheTreeUpForRemoval() throws Exception {
		final TreeName tree = new TreeName("testCursorDelete", "tree");
		final JDBCStorage owner = new JDBCStorage(createBackendCfg(), null);
		owner.open(AccessMode.READ_WRITE);
		owner.write(new WriteOperation() {
			@Override
			public void run(WriteableTransaction txn) throws Exception {
				txn.openTree(tree, true);
				txn.put(tree, key(1), value(1));
			}
		});
		owner.close();

		// a second storage of the same backend, which never opened that tree itself: the delete takes
		// the enrolling name and the table it writes to is the one the tree names. What puts a tree up
		// for removal is the row its backend's catalog holds (#888) - written by the openTree above and
		// outliving the storage that made it - so this asserts the listing of a backend and not a
		// side effect of the statement, which is what a listing of a catalog can assert
		final JDBCStorage other = new JDBCStorage(createBackendCfg(), null);
		try {
			other.open(AccessMode.READ_WRITE);
			other.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						assertTrue(cursor.next());
						cursor.delete();
					}
				}
			});
			assertTrue(other.listTrees().contains(tree), "a tree written through a cursor must be listed for removal");
		} finally {
			other.close();
			final JDBCStorage cleanup = new JDBCStorage(createBackendCfg(), null);
			try {
				cleanup.open(AccessMode.READ_WRITE);
				cleanup.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(tree);
					}
				});
			} catch (Exception ignored) {
			} finally {
				cleanup.close();
			}
		}
	}

	private static boolean isExistingTable(Connection con, String tableName) throws SQLException {
		try (final ResultSet rs = con.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
			while (rs.next()) {
				if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A statement of this backend has to end even when another session holds what it needs: a row
	 * locked by a transaction that never commits used to park the worker thread that issued the
	 * write for good, with nothing in the log to say so (#877).
	 */
	@Test(timeOut = 600000)
	public void testWriteBlockedByAnotherSessionGivesUpAtItsBound() throws Exception {
		assertBoundedWhileRowsAreLocked("testStatementBound", JDBCStorage.StatementBound.OPERATION,
			new BlockedOperation() {
				@Override
				public void run(JDBCStorage storage, TreeName tree) throws Exception {
					storage.write(new WriteOperation() {
						@Override
						public void run(WriteableTransaction txn) throws Exception {
							txn.put(tree, key(1), value(2));
						}
					});
				}
			});
	}

	/**
	 * The bulk class keeps a bound of its own: a count or the delete that empties a tree before an
	 * import legitimately takes minutes, so it must not be cut at the bound of an entry read - and
	 * must still be able to give up (#877).
	 */
	@Test(timeOut = 600000)
	public void testBulkStatementGivesUpAtItsOwnBound() throws Exception {
		assertBoundedWhileRowsAreLocked("testBulkBound", JDBCStorage.StatementBound.BULK,
			new BlockedOperation() {
				@Override
				public void run(JDBCStorage storage, TreeName tree) throws Exception {
					// the importer is where "delete from <table>" - the bulk class - is reachable:
					// AbstractTwoPhaseImportStrategy clears every tree before an import writes to it
					try (final Importer importer = storage.startImport()) {
						importer.clearTree(tree);
					}
				}
			});
	}

	private interface BlockedOperation {
		void run(JDBCStorage storage, TreeName tree) throws Exception;
	}

	/**
	 * Whether the failure the operation gave up with is the one its bound produced: the message of
	 * a statement classified as having reached its bound names the property that bounded it, and it
	 * arrives wrapped in whatever the storage throws to its caller.
	 */
	private static boolean namesTheBound(Throwable failure, JDBCStorage.StatementBound bound) {
		return namedInTheChain(failure, bound.property);
	}

	/**
	 * Whether the statement ran under the socket read timeout alone, which is what
	 * {@code timedOut()} says of one whose driver would not take the cancel. That degradation is by
	 * design - {@code JDBCStorage.setQueryTimeout()} warns once and carries on - and it is
	 * therefore silent: with a ceiling wide enough for the second layer, a run with the first one
	 * gone entirely ends at the backstop and passes as the bound doing its work.
	 */
	private static boolean ranUnderTheBackstopAlone(Throwable failure) {
		return namedInTheChain(failure, JDBCStorage.BACKSTOP_ALONE);
	}

	/** Cause hops walked below, as {@code JDBCStorage} bounds its own classifier: a guard against a cycle. */
	private static final int MAX_CAUSE_HOPS = 16;

	private static boolean namedInTheChain(Throwable failure, String text) {
		// bounded by hops rather than by t != t.getCause(), which only catches a cause that is its
		// own: a wrapper re-attaching an exception it has already wrapped makes a cycle of two, and
		// walking that one spins until the harness times the whole suite out
		Throwable t = failure;
		for (int hops = 0; t != null && hops < MAX_CAUSE_HOPS; t = t.getCause(), hops++) {
			if (t.getMessage() != null && t.getMessage().contains(text)) {
				return true;
			}
			if (t == t.getCause()) {
				break;
			}
		}
		return false;
	}

	/**
	 * Runs the given operation while another session holds every row of the tree in an uncommitted
	 * transaction, with only the property of the given class bounding it: the operation must give
	 * up inside that bound instead of waiting for a lock that is never released.
	 */
	private void assertBoundedWhileRowsAreLocked(String treeId, JDBCStorage.StatementBound bound, BlockedOperation blocked)
			throws Exception {
		final int boundSeconds = 5;
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName(treeId, "tree");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					txn.put(tree, key(1), value(1));
				}
			});
			// another session takes an exclusive lock on every row of the table and keeps it: the
			// same statement clearTree() issues, so it is known to parse on all four dialects
			try (final Connection blocker = DriverManager.getConnection(getJdbcUrl())) {
				blocker.setAutoCommit(false);
				try (final Statement lock = blocker.createStatement()) {
					lock.executeUpdate("delete from " + storage.getTableName(tree));
				}
				// the rows go back whatever the assertions below do with the run: the cleanup of
				// this method drops the table, which is a bulk statement and unbounded here, so a
				// lock still held would park it until the timeout of the harness and turn one
				// failed assertion into a stalled build
				try {
					// only the class under test is bounded, so a pass through the other one cannot
					// be mistaken for the bound working
					for (final JDBCStorage.StatementBound each : JDBCStorage.StatementBound.values()) {
						System.setProperty(each.property, each == bound ? Integer.toString(boundSeconds) : "0");
					}
					// the monotonic clock, which is what timedOut() measures the bound with: a step of
					// the wall clock can neither lengthen nor shorten what the assertions below allow
					final long startedAt = System.nanoTime();
					Exception failure = null;
					try {
						blocked.run(storage, tree);
						fail("the operation must give up while the rows it needs are locked");
					} catch (Exception expected) {
						failure = expected; // the bound was reached and the transaction rolled back
					}
					final long elapsed = (System.nanoTime() - startedAt) / 1000000L;
					// The failure has to be the one the bound produces, not any failure at all: an
					// operation that fell over at once for an unrelated reason would otherwise pass
					// this test at t=0. timedOut() names the property in the message of everything it
					// classifies as reaching the bound.
					assertTrue(namesTheBound(failure, bound), "gave up with " + stackTraceToSingleLineString(failure)
						+ ", which does not name " + bound.property);
					// And under the layer it is supposed to be under. The ceiling below has to be
					// wide enough for the second one, since that is what ends the wait on oracle,
					// and a ceiling that wide cannot tell a working first layer from a missing one:
					// a driver that stops taking setQueryTimeout degrades to the backstop silently
					// by design, ends there, and would be scored as the bound doing its work. The
					// message says which layer it was, so this assertion can too.
					assertFalse(ranUnderTheBackstopAlone(failure), "the driver would not take a query timeout, so "
						+ "the statement ran under the socket read timeout alone: "
						+ stackTraceToSingleLineString(failure));
					// And it has to arrive at the bound rather than at something else that happens to
					// end the wait inside a generous ceiling: with the bound deleted, mysql would still
					// come back after its own innodb_lock_wait_timeout of 50 s, and the assertion has
					// to fail then. The ceiling is what the bound really allows a statement, which is
					// the second layer rather than the property: holdBackstop() arms the socket read
					// timeout at the bound plus its margin on every engine, not only on oracle, and a
					// run where the cancel of the driver does not land ends there. Scoring that as a
					// failure would fail this suite for the second layer doing exactly what it exists
					// to do - and on oracle, where a session in a row-lock enqueue never acts on the
					// break its driver sends, that is not an edge case but the normal path.
					final long ceilingSeconds = boundSeconds + JDBCStorage.BACKSTOP_MARGIN_SECONDS + 10;
					// with a little slack under the bound: a driver keeps its timer in whole seconds and
					// may report the cancel a few milliseconds before the bound is arithmetically due,
					// which is the slack timedOut() classifies such a statement with
					assertTrue(elapsed >= boundSeconds * 1000L - JDBCStorage.CLOCK_SLACK_MILLIS,
						"gave up after " + elapsed + " ms, before its bound of "
						+ boundSeconds + " s: something other than the bound ended the wait");
					assertTrue(elapsed < ceilingSeconds * 1000L, "gave up only after " + elapsed + " ms, past the "
						+ ceilingSeconds + " s this bound of " + boundSeconds + " s allows");
				}finally {
					// in a catch of its own: a rollback that throws would otherwise replace the
					// assertion above, and the run would report an unrelated connection problem
					// instead of the bound that was missed. Nothing is lost by swallowing it - a
					// session that cannot roll back has no rows left locked either.
					try {
						blocker.rollback();
					} catch (SQLException releasingTheRows) {
						// the assertions above are the outcome of this test, not this
					}
				}
			}
		} finally {
			for (final JDBCStorage.StatementBound each : JDBCStorage.StatementBound.values()) {
				System.clearProperty(each.property);
			}
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

	/**
	 * A storage opened READ_ONLY must still hand out the write transaction {@code RootContainer.open()} asks for
	 * there - otherwise the offline export-ldif, verify-index and backendstat fail before reading anything - and
	 * that transaction must serve exactly what the open needs and nothing more: opening an existing tree, reads,
	 * cursors and record counts, while every mutation, including a delete through a cursor it opened, is
	 * refused (#874).
	 */
	@Test
	public void testReadOnlyTransactionReadsButRefusesWrites() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("testReadOnlyTransaction", "tree");
		final TreeName absent = new TreeName("testReadOnlyTransaction", "absent");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					txn.put(tree, key(0), value(0));
					txn.put(tree, key(1), value(1));
				}
			});
			storage.close();

			storage.open(AccessMode.READ_ONLY);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					// what RootContainer.open() does through this transaction in read-only mode
					txn.openTree(tree, false);
					assertEquals(txn.read(tree, key(0)), value(0));
					assertEquals(txn.getRecordCount(tree), 2);
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), key(0));
						try {
							cursor.delete();
							fail("delete() through a cursor of a read-only transaction must fail");
						} catch (UnsupportedOperationException expected) {}
					}

					assertReadOnly("openTree(createOnDemand)", () -> txn.openTree(absent, true));
					assertReadOnly("put", () -> txn.put(tree, key(2), value(2)));
					assertReadOnly("update", () -> txn.update(tree, key(0), old -> value(3)));
					assertReadOnly("delete", () -> txn.delete(tree, key(0)));
					assertReadOnly("deleteTree", () -> txn.deleteTree(tree));
				}
			});

			// nothing above reached the database
			storage.close();
			storage.open(AccessMode.READ_WRITE);
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					assertEquals(txn.getRecordCount(tree), 2);
					assertEquals(txn.read(tree, key(0)), value(0));
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

	private static void assertReadOnly(String operation, Runnable mutation) {
		try {
			mutation.run();
			fail(operation + " must fail on a read-only storage");
		} catch (ReadOnlyStorageException expected) {}
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

	/** The dialect of the database this suite runs against, as the backend detects it from the driver. */
	JDBCStorage.Dialect dialect() {
		final String url = getJdbcUrl();
		if (url.startsWith("jdbc:postgresql")) {
			return JDBCStorage.Dialect.POSTGRES;
		} else if (url.startsWith("jdbc:mysql")) {
			return JDBCStorage.Dialect.MYSQL;
		} else if (url.startsWith("jdbc:oracle")) {
			return JDBCStorage.Dialect.ORACLE;
		} else if (url.startsWith("jdbc:sqlserver")) {
			return JDBCStorage.Dialect.MICROSOFT;
		}
		throw new SkipException("no dialect for " + url);
	}

	/**
	 * What this connection reports as its lock bound, in the unit and the rendering of its own
	 * engine, or null where reading it needs a privilege the test user does not have: oracle
	 * keeps ddl_lock_timeout in v$parameter, which an application user cannot select from.
	 */
	String sessionLockBound(Connection con) throws Exception {
		final String url = getJdbcUrl();
		final String sql;
		if (url.startsWith("jdbc:postgresql")) {
			sql = "show lock_timeout";
		} else if (url.startsWith("jdbc:mysql")) {
			sql = "select @@session.lock_wait_timeout";
		} else if (url.startsWith("jdbc:sqlserver")) {
			sql = "select @@lock_timeout";
		} else {
			return null;
		}
		try (final Statement st = con.createStatement();
			 final ResultSet rs = st.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
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
			assertEquals(storage.commentTable(tree, dialect()), JDBCStorage.CommentResult.UP_TO_DATE, "an up-to-date comment was re-stamped");
			writeTableComment(storage.getTableName(tree), "stale");
			assertEquals(storage.commentTable(tree, dialect()), JDBCStorage.CommentResult.STAMPED, "a stale comment was not re-stamped");
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
				final JDBCStorage.CommentResult result = storage.commentTable(tree, dialect());
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
			Connection newStampConnection(Dialect dialect) throws SQLException {
				stampAttempts.incrementAndGet();
				throw new SQLException("injected comment failure"); // no sql state, no vendor code: a rejection, not a failure of the moment
			}
		};
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					// pending in this transaction, and it has to still be pending when the stamp is
					// attempted: both trees were enrolled by the storage above, so the openTree below
					// records nothing in the catalog and commits nothing of what is written here
					txn.put(written, key(1), value(1));
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
			// the failure is remembered: an unstampable table is not asked again while this backend is open.
			// Counted from what the open itself attempted rather than from one: the open stamps the tree and
			// the catalog of the backend, and how many tables an open has to stamp is not what this is about
			final int attemptsOfTheOpen = stampAttempts.get();
			assertEquals(storage.commentTable(stamped, dialect()), JDBCStorage.CommentResult.FAILED);
			assertEquals(stampAttempts.get(), attemptsOfTheOpen, "a failed stamp was reissued");
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
			assertEquals(storage.commentTable(tree, dialect()), JDBCStorage.CommentResult.FAILED);
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
	 * open after an upgrade stamps them all: the trees of one open must share one connection
	 * rather than make a physical connect each. One per open is what the comment machinery costs,
	 * readback included - the readback runs on that same connection, because the thread doing the
	 * open is inside a transaction and holding a pooled connection already.
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
			// three trees, one more connect: the open that finds every comment in place issues no
			// statement and takes no lock, and pays one connection for the whole sweep either way
			assertEquals(connects.get(), 2, "the trees of an open that found every comment in place did not share a connection");
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
	 * The bound a stamp connection is given must survive a stamp that failed. Postgres undoes a
	 * plain SET when the transaction that ran it is rolled back, and a failed stamp is rolled
	 * back with the connection kept and reused - one connection serves every tree of a backend
	 * open - so every tree stamped after the first failure used to run with no bound at all,
	 * which is what the bound exists to prevent.
	 */
	@Test
	public void testLockBoundSurvivesAFailedStamp() throws Exception {
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		// no table was ever created for this tree, so its comment statement fails - on a connection
		// that stays usable, which is the case the session rolls back rather than replaces
		final TreeName missing = new TreeName("o=lockBound", "neverCreated");
		try {
			storage.open(AccessMode.READ_WRITE);
			final JDBCStorage.Dialect dialect = dialect();
			final String bound;
			try (final Connection fresh = storage.newStampConnection(dialect)) {
				bound = sessionLockBound(fresh); // what a connection carrying the bound reports
			}
			try (final JDBCStorage.StampSession session = storage.new StampSession()) {
				assertEquals(storage.commentTable(missing, dialect, session), JDBCStorage.CommentResult.FAILED,
					"stamping a table that does not exist was reported as done");
				if (bound != null) { // oracle: ddl_lock_timeout is only in v$parameter, which the test user cannot read
					assertEquals(sessionLockBound(session.connection(dialect)), bound,
						"the lock bound was lost when the failed stamp was rolled back");
				}
			}
		} finally {
			storage.close();
		}
	}

	/**
	 * A stamp that lost its connection ends the sweep it happened in: every tree behind it needs
	 * that same connection, so each would pay the same connect attempt again. That is about 25 of
	 * them for a stock suffix, all for a diagnostic aid. Nothing is remembered, so the next open
	 * tries again.
	 */
	@Test
	public void testConnectionFailureEndsTheSweep() throws Exception {
		final TreeName[] trees = {
			new TreeName("o=sweepGiveUp", "dn2id"),
			new TreeName("o=sweepGiveUp", "id2entry"),
			new TreeName("o=sweepGiveUp", "state") };
		final AtomicInteger stampAttempts = new AtomicInteger();
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null) {
			@Override
			Connection newStampConnection(Dialect dialect) throws SQLException {
				stampAttempts.incrementAndGet();
				throw new SQLException("injected connection failure", "08006"); // connection exception: the session is gone
			}
		};
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					for (final TreeName tree : trees) {
						txn.openTree(tree, true);
					}
				}
			});
			assertEquals(stampAttempts.get(), 1, "a connection that was gone was paid once per tree of the same open");
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					for (final TreeName tree : trees) {
						txn.openTree(tree, true);
					}
				}
			});
			assertEquals(stampAttempts.get(), 2, "the open after a lost connection did not try again");
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
	 * A lock belongs to the table it is held on, so a stamp that gave up on one must not cost the
	 * trees behind it their comments: the trees of an open are stamped in a fixed order, and a
	 * table left permanently contended by another session would otherwise mean nothing is ever
	 * stamped, on any open. Nothing is remembered either - the open that follows stamps the table
	 * whose moment has passed.
	 * <p>
	 * The failure is injected at the readback rather than at the comment statement, which is built
	 * inline; what is under test is the classification of the failure and what the sweep does with
	 * it, and those do not depend on which of the two statements produced it.
	 */
	@Test
	public void testContendedTableDoesNotEndTheSweep() throws Exception {
		final TreeName[] trees = {
			new TreeName("o=sweepContended", "dn2id"),
			new TreeName("o=sweepContended", "id2entry"),
			new TreeName("o=sweepContended", "state") };
		final AtomicInteger contended = new AtomicInteger(1); // the first tree, for one sweep only
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null) {
			@Override
			String readStoredComment(Connection con, Dialect dialect, String tableName) throws SQLException {
				if (tableName.equals(getTableName(trees[0])) && contended.getAndDecrement() > 0) {
					throw lockTimeoutOf(dialect); // as if another session held this one table
				}
				return super.readStoredComment(con, dialect, tableName);
			}
		};
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					for (final TreeName tree : trees) {
						txn.openTree(tree, true);
					}
				}
			});
			assertNotEquals(readTableComment(storage.getTableName(trees[0])), trees[0].toString(),
				"the contended table was stamped anyway");
			for (int i = 1; i < trees.length; i++) {
				assertEquals(readTableComment(storage.getTableName(trees[i])), trees[i].toString(),
					"one contended table cost the trees behind it their comments");
			}
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					for (final TreeName tree : trees) {
						txn.openTree(tree, true);
					}
				}
			});
			assertEquals(readTableComment(storage.getTableName(trees[0])), trees[0].toString(),
				"a table left unstamped by a contended moment was not stamped by the open that followed");
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

	/** The failure a dialect reports when a statement gave up on the lock bound it was given. */
	static SQLException lockTimeoutOf(JDBCStorage.Dialect dialect) {
		switch (dialect) {
		case POSTGRES:
			return new SQLException("canceling statement due to lock timeout", "55P03");
		case MYSQL:
			return new SQLException("Lock wait timeout exceeded; try restarting transaction", "HY000", 1205);
		case ORACLE:
			return new SQLException("ORA-00054: resource busy and acquire with NOWAIT specified", "61000", 54);
		case MICROSOFT:
			return new SQLException("Lock request time out period exceeded", "HY000", 1222);
		default:
			throw new IllegalStateException("no lock timeout failure for dialect " + dialect);
		}
	}

	/**
	 * @@sql_mode decides whether a backslash escapes inside the comment literal. It belongs to
	 * the session, and the stamps of one open share a connection, so it is asked once for the
	 * whole sweep rather than once per tree - and only on mysql, the one engine whose literal
	 * depends on it.
	 */
	@Test
	public void testSqlModeProbedOncePerSweep() throws Exception {
		final TreeName[] trees = {
			new TreeName("o=sqlModeProbe", "dn2id"),
			new TreeName("o=sqlModeProbe", "id2entry"),
			new TreeName("o=sqlModeProbe", "state") };
		final AtomicInteger probes = new AtomicInteger();
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null) {
			@Override
			boolean isMysqlBackslashEscape(Connection con) throws SQLException {
				probes.incrementAndGet();
				return super.isMysqlBackslashEscape(con);
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
			assertEquals(probes.get(), dialect() == JDBCStorage.Dialect.MYSQL ? 1 : 0,
				"the sql mode of one sweep was not asked exactly once");
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

	// The bounds of a stamp connection are covered by StampConnectionTestCase: what they are worth
	// is whether they reach the driver and whether the driver then gives up on a server that never
	// answers, and neither needs - nor can be staged by - a database container.

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
	/**
	 * Two or more <em>distinct new</em> keys written into one tree per transaction is the shape the primary key
	 * seek made able to deadlock: on the NOT MATCHED path the seek range-locks the gap before the next existing
	 * key, that lock is self-incompatible, and the key hash scatters logically ordered keys across the index, so
	 * two writers inserting different keys can each end up holding what the other needs. The ascending key order
	 * that {@code IndexBuffer} maintains does not help there. Nothing may escape {@link JDBCStorage#write}, which
	 * replays the conflict, and no record may be lost to it (#867).
	 */
	@Test(timeOut = 600000)
	public void testConcurrentWritersInsertingDistinctKeys() throws Exception {
		final int writers = 4;
		final int rounds = 25;
		final int keysPerTransaction = 3;
		final int seeded = 10;
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("testConcurrentInsert", "tree");
		final ExecutorService executor = Executors.newFixedThreadPool(writers);
		try {
			storage.open(AccessMode.READ_WRITE);
			// seeded, so that every insert below takes the NOT MATCHED path with a gap to lock in front of it
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					for (int i = 0; i < seeded; i++) {
						txn.put(tree, key(i), value(i));
					}
				}
			});
			final List<Callable<Void>> concurrent = new ArrayList<>();
			for (int writer = 0; writer < writers; writer++) {
				final int id = writer;
				concurrent.add(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						for (int round = 0; round < rounds; round++) {
							final int current = round;
							storage.write(new WriteOperation() {
								@Override
								public void run(WriteableTransaction txn) throws Exception {
									for (int i = 0; i < keysPerTransaction; i++) {
										txn.put(tree,
												ByteString.valueOfUtf8(String.format("w%02d-r%03d-k%d", id, current, i)),
												value(i));
									}
								}
							});
						}
						return null;
					}
				});
			}
			for (final Future<Void> written : executor.invokeAll(concurrent)) {
				// a conflict the storage did not replay surfaces here, as it would reach an LDAP client
				written.get();
			}
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					assertEquals(txn.getRecordCount(tree), seeded + writers * rounds * keysPerTransaction);
					return null;
				}
			});
		} finally {
			executor.shutdownNow();
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
	 * removeStorageFiles() has to clear a backend this process has never opened: offline import-ldif
	 * configures the backend and calls it before anything opens the root container, so answering from
	 * the trees this process happens to have touched dropped nothing at all - an offline
	 * "import-ldif --clearBackend" cleared a JDBC backend of nothing (#888).
	 */
	@Test
	public void testABackendIsClearedByAProcessThatNeverOpenedIt() throws Exception {
		final TreeName tree = new TreeName("testOfflineClear", "tree");
		// the neighbour serves a base DN of its own, so that its table is one it reports as its own:
		// what this case asserts of the clear next door is then an absence and not a vacuity
		final DN neighbourBaseDN = DN.valueOf("dc=offline-clear-neighbour,dc=com");
		final TreeName neighbourTree = new TreeName(neighbourBaseDN.toNormalizedUrlSafeString(), "tree");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_cleared"), null);
		final JDBCStorage neighbour =
			new JDBCStorage(createBackendCfg(getBackendId() + "_neighbour", neighbourBaseDN), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					txn.put(tree, key(1), value(1));
				}
			});
			neighbour.open(AccessMode.READ_WRITE);
			neighbour.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(neighbourTree, true);
					txn.put(neighbourTree, key(1), value(1));
				}
			});
		} catch (Exception e) {
			// the clears of the case below are reached by no failure of this half, and nothing but
			// @BeforeClass ever drops what it leaves behind
			clearQuietly(storage);
			clearQuietly(neighbour);
			throw e;
		} finally {
			storage.close();
			neighbour.close();
		}

		// configured and never opened, nothing touched: what BackendImpl.importLDIF holds offline
		final JDBCStorage offline = new JDBCStorage(createBackendCfg(getBackendId() + "_cleared"), null);
		try {
			assertTrue(offline.listTrees().contains(tree),
				"the tree of a backend this process never opened has to be named by its catalog");

			offline.removeStorageFiles();

			assertFalse(isExistsTable(offline.getTableName(tree)), "the table of the tree survived the clear");
			assertFalse(isExistsTable(offline.getTableName(offline.getCatalogTree())), "the catalog survived the clear");
			final Set<TreeName> cleared = offline.listTrees();
			assertFalse(cleared.contains(tree), "a cleared backend still names its tree");
			assertFalse(cleared.contains(offline.getCatalogTree()), "a cleared backend still names its catalog");
			// the neighbour is named by a catalog of its own: what one backend clears is never another's
			assertTrue(isExistsTable(neighbour.getTableName(neighbourTree)),
				"the clear of one backend dropped the table of another backend of the same database");
			// nor does it report another backend's tables as tables of its own: a table is named after
			// the hash of its tree name and says nothing about whose it is, but it is stamped with that
			// tree name (#866), and the neighbour's trees are trees of no base DN this backend serves
			assertReportsNothingOf(offline, neighbour, neighbourTree);
		} finally {
			// in a finally of their own: a failed assertion above must not leave the tables of either
			// backend behind for the rest of the class, which nothing but @BeforeClass ever drops
			clearQuietly(neighbour);
			clearQuietly(offline);
		}
	}

	/**
	 * A dropped tree has to leave the catalog together with its table: a row outliving its table
	 * would make backendstat name a tree that is not there, and would put a table that is already
	 * gone up for removal (#888).
	 */
	@Test
	public void testADeletedTreeIsNoLongerNamedByTheCatalog() throws Exception {
		final TreeName kept = new TreeName("testCatalogDelete", "kept");
		final TreeName dropped = new TreeName("testCatalogDelete", "dropped");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_deleted"), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(kept, true);
					txn.openTree(dropped, true);
				}
			});
			final Set<TreeName> opened = storage.listTrees();
			assertTrue(opened.contains(kept) && opened.contains(dropped), "an opened tree is not named by the catalog");

			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.deleteTree(dropped);
				}
			});

			final Set<TreeName> remaining = storage.listTrees();
			assertTrue(remaining.contains(kept), "the catalog forgot a tree that is still there");
			assertFalse(remaining.contains(dropped), "the catalog still names a tree that was deleted");
			// and the removal that follows must not stumble over the tree it no longer names
			storage.removeStorageFiles();
			assertFalse(isExistsTable(storage.getTableName(kept)), "the table of the tree survived the clear");
		} finally {
			clearQuietly(storage);
		}
	}

	/**
	 * A row of the catalog whose table is not there any more must not fail the clear, and must not
	 * stop it dropping the rest. Nothing of the backend leaves such a row behind - deleteTree() takes
	 * it out in the commit that drops the table - but a table dropped by hand, or a catalog restored
	 * from a backup older than the database, leaves exactly this (#888).
	 */
	@Test
	public void testAClearSkipsACatalogRowWhoseTableIsGone() throws Exception {
		final TreeName kept = new TreeName("testStaleCatalogRow", "kept");
		final TreeName vanished = new TreeName("testStaleCatalogRow", "vanished");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_stale"), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(kept, true);
					txn.openTree(vanished, true);
				}
			});
			dropTableBehindTheBackend(storage.getTableName(vanished));
			assertTrue(storage.listTrees().contains(vanished),
				"the catalog was expected to go on naming the tree whose table was dropped behind its back");

			storage.removeStorageFiles();

			assertFalse(isExistsTable(storage.getTableName(kept)),
				"a row of the catalog whose table is gone stopped the clear dropping the rest");
			assertFalse(isExistsTable(storage.getTableName(storage.getCatalogTree())), "the catalog survived the clear");
		} finally {
			clearQuietly(storage);
		}
	}

	/**
	 * Naming a tree in order to read it must never put it up for removal: the tree read may be held
	 * by another backend of the same database, which nothing forbids (#873). Only
	 * openTree(createOnDemand) enrols.
	 */
	@Test
	public void testReadingATreeDoesNotPutItUpForRemoval() throws Exception {
		final TreeName owned = new TreeName("testReadDoesNotEnrol", "owned");
		final TreeName foreign = new TreeName("testReadDoesNotEnrolForeign", "tree");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_reader"), null);
		final JDBCStorage owner = new JDBCStorage(createBackendCfg(getBackendId() + "_owner"), null);
		try {
			owner.open(AccessMode.READ_WRITE);
			owner.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(foreign, true);
					txn.put(foreign, key(1), value(1));
				}
			});

			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(owned, true); // the catalog of this backend comes into being here
					txn.openTree(foreign, false); // read, not owned
				}
			});
			assertEquals(storage.read(new ReadOperation<ByteString>() {
				@Override
				public ByteString run(ReadableTransaction txn) throws Exception {
					return txn.read(foreign, key(1));
				}
			}), value(1), "the tree of the other backend could not be read");

			assertFalse(storage.listTrees().contains(foreign), "reading a tree enrolled it in the catalog");
			storage.removeStorageFiles();
			assertTrue(isExistsTable(owner.getTableName(foreign)),
				"the clear dropped a tree this backend had only read");
			assertFalse(isExistsTable(storage.getTableName(owned)), "the table of the backend's own tree survived the clear");
		} finally {
			clearQuietly(owner);
			clearQuietly(storage);
		}
	}

	/**
	 * The compressed schema trees named from a literal carry no backend qualifier, so on a database
	 * addressed by several backends they are the same pair for all of them: a clear must leave them
	 * where they lie (#881). A tool asking a backend what trees it holds has to be shown them all the
	 * same, which is what keeps them out of the catalog and inside listTrees().
	 */
	@Test
	public void testTheSharedCompressedSchemaTreesAreNamedButNeverCleared() throws Exception {
		final TreeName owned = new TreeName("testSharedCompressedSchema", "owned");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_schema"), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(owned, true);
					// opened, never written to: since #881 no backend of this class makes the literal-named
					// pair, so this openTree is what creates these two tables - and the finally below is what
					// removes them again, a clear being required to leave them exactly where they lie
					for (final TreeName shared : JDBCStorage.SHARED_COMPRESSED_SCHEMA_TREES) {
						txn.openTree(shared, true);
					}
				}
			});
			// both of them: the pair is a hand-copy of two privates of PersistentCompressedSchema, and
			// a literal naming a tree that does not exist would go unseen if one of them were never asked
			// for - the tree it names would be neither shown by listTrees() nor spared by a clear
			final Set<TreeName> named = storage.listTrees();
			for (final TreeName shared : JDBCStorage.SHARED_COMPRESSED_SCHEMA_TREES) {
				assertTrue(named.contains(shared),
					"a tool asking this backend for its trees was not shown " + shared);
			}

			storage.removeStorageFiles();

			for (final TreeName shared : JDBCStorage.SHARED_COMPRESSED_SCHEMA_TREES) {
				assertTrue(isExistsTable(JDBCStorage.toTableName(shared)),
					"the clear dropped " + shared + ", which another backend of this database may be the only owner of");
			}
			assertFalse(isExistsTable(storage.getTableName(owned)), "the table of the backend's own tree survived the clear");
		} finally {
			// the pair is dropped by hand here, and by nothing of the backend: a clear must leave it
			// where it lies, which is the whole of what this case asserts. It is this case's to remove
			// because it is this case that made it - since #881 each backend keeps its definitions in a
			// pair of its own, so the literal-named pair belongs to no backend of this class any more
			// and the openTree above is what created these two tables. Left standing they would be a
			// legacy pair this database does not have, which
			// testCompressedSchemaTableIsQualifiedByBackendId asserts about and TestNG may run after
			// this case as easily as before it
			clearQuietly(storage);
			for (final TreeName shared : JDBCStorage.SHARED_COMPRESSED_SCHEMA_TREES) {
				try {
					dropTableBehindTheBackend(JDBCStorage.toTableName(shared));
				} catch (SQLException ignored) { // a case that failed before it made them leaves none to drop
				}
			}
		}
	}

	/**
	 * The row of a deleted tree must not be left to the enclosing transaction: a terminal failure
	 * later in it - write() replays a class 40 conflict and rethrows everything else - would roll the
	 * row back over a table that is already gone, and nothing would put it right, a deleted tree not
	 * being opened again (#888).
	 */
	@Test
	public void testADeletedTreeStaysOutOfTheCatalogWhenItsTransactionFails() throws Exception {
		final TreeName kept = new TreeName("testCatalogDeleteRollback", "kept");
		final TreeName deleted = new TreeName("testCatalogDeleteRollback", "deleted");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_rollback"), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(kept, true);
					txn.openTree(deleted, true);
				}
			});

			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(deleted);
						// terminal, and no conflict for write() to replay: everything this transaction
						// still owes goes back, and the row of the deleted tree must not be part of it
						throw new IllegalStateException("the transaction of a deleteTree failed");
					}
				});
				fail("the write was expected to fail");
			} catch (Exception expected) {
				// what the case is about is what the failure left behind
			}

			assertFalse(isExistsTable(storage.getTableName(deleted)), "the failed transaction brought a dropped table back");
			final Set<TreeName> remaining = storage.listTrees();
			assertFalse(remaining.contains(deleted),
				"the catalog names a tree whose table the failed transaction left dropped");
			assertTrue(remaining.contains(kept), "the catalog forgot a tree that is still there");
		} finally {
			clearQuietly(storage);
		}
	}

	/**
	 * The same, for the branch a deleteTree takes when the table is not there any more: nothing is
	 * dropped, so there is no commit of a drop for the row to be carried out of the catalog by, and
	 * the commit the delete is given on the catalog's own connection is the whole of what takes it
	 * out. Left to the enclosing transaction, the row would go back with it and the catalog would name
	 * a tree with no table for good - the state a clear can only skip and report, never repair (#888).
	 */
	@Test
	public void testADeletedTreeStaysOutOfTheCatalogWhenItsTableIsAlreadyGone() throws Exception {
		final TreeName kept = new TreeName("testCatalogDeleteNoTable", "kept");
		final TreeName deleted = new TreeName("testCatalogDeleteNoTable", "deleted");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_noTable"), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(kept, true);
					txn.openTree(deleted, true);
				}
			});
			// what an interrupted change of an earlier run leaves: a row of the catalog naming a table
			// that is not there any more. The deleteTree below therefore drops nothing at all
			dropTableBehindTheBackend(storage.getTableName(deleted));

			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(deleted);
						// terminal, and no conflict for write() to replay: everything this transaction
						// still owes goes back, and the row of the deleted tree must not be part of it
						throw new IllegalStateException("the transaction of a deleteTree failed");
					}
				});
				fail("the write was expected to fail");
			} catch (Exception expected) {
				// what the case is about is what the failure left behind
			}

			final Set<TreeName> remaining = storage.listTrees();
			assertFalse(remaining.contains(deleted),
				"the catalog names a tree whose table was already gone when it was deleted");
			assertTrue(remaining.contains(kept), "the catalog forgot a tree that is still there");
		} finally {
			clearQuietly(storage);
		}
	}

	/**
	 * The row of a tree whose table is already standing must not be left to the enclosing transaction
	 * either. That is the open which fills the catalog of a backend upgraded from a version keeping
	 * none: it creates no table, so nothing else of openTree() commits anything, and a transaction
	 * failing after the enrolment would take the whole of it back - leaving a backend whose tables
	 * are named by no catalog and whose next clear therefore drops nothing at all (#888).
	 * <p>
	 * The row is written and committed on a connection of the catalog's own, so this holds on every
	 * engine for the same reason: nothing the caller's transaction does - or fails to do - reaches it.
	 * On the branch before this one the row rode the caller's connection, and the case was green on
	 * postgres for a reason of that engine alone (openTree() asks there for the cursor index of every
	 * tree on every open and commits that, carrying the row with it) while the other three lost it.
	 */
	@Test
	public void testAReopenedTreeStaysInTheCatalogWhenItsTransactionFails() throws Exception {
		final TreeName tree = new TreeName("testCatalogEnrolRollback", "tree");
		final JDBCStorage setUp = new JDBCStorage(createBackendCfg(getBackendId() + "_enrol"), null);
		try { // the tables of the backend, made by a storage that then goes away
			setUp.open(AccessMode.READ_WRITE);
			setUp.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
		} finally {
			setUp.close();
		}
		// and the rest of what an installation upgraded to a version keeping a catalog holds: a
		// catalog naming none of those tables
		emptyTheCatalog(setUp.getTableName(setUp.getCatalogTree()));

		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_enrol"), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			assertFalse(storage.listTrees().contains(tree), "the catalog of the case was not emptied");

			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						// the table is there, so this open creates none: the enrolment is the only thing
						// this transaction has written when it fails
						txn.openTree(tree, true);
						// terminal, and no conflict for write() to replay: everything this transaction
						// still owes goes back, and the row naming a standing table must not be part of it
						throw new IllegalStateException("the transaction of an openTree failed");
					}
				});
				fail("the write was expected to fail");
			} catch (Exception expected) {
				// what the case is about is what the failure left behind
			}

			assertTrue(storage.listTrees().contains(tree),
				"the catalog forgot a tree whose table is standing: a clear of this backend would drop nothing");
		} finally {
			clearQuietly(storage);
		}
	}

	/**
	 * A tree the catalog already records at the table this version records it at is not enrolled
	 * again by an open: the row would be the row that is already there. A row recording any other
	 * table is, though - a removal drops the table the row records, so a row naming one this backend
	 * would not create leaves the real table standing, named by nothing and dropped by no clear ever
	 * after. Which of the two a row is has to be decided by what it records and not by its presence.
	 */
	@Test
	public void testARowRecordingAnotherTableIsEnrolledAgain() throws Exception {
		final TreeName tree = new TreeName("testCatalogStaleRow", "tree");
		final JDBCStorage setUp = new JDBCStorage(createBackendCfg(getBackendId() + "_staleRow"), null);
		try { // the table and its row, by a storage that then goes away
			setUp.open(AccessMode.READ_WRITE);
			setUp.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
		} finally {
			setUp.close();
		}
		final String catalogTable = setUp.getTableName(setUp.getCatalogTree());
		// what a version naming its tables otherwise would have left: a row of the right tree
		// recording a table this one would never create
		recordAnotherTable(catalogTable, "opendj_00000000000000000000000000000000000000000000000000000000");

		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_staleRow"), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});

			try (final Connection con = DriverManager.getConnection(getJdbcUrl())) {
				assertEquals(
					storage.catalogTables(con, JDBCStorage.TableScope.of(storage, con)).get(tree),
					storage.getTableName(tree),
					"a row recording a table this backend does not hold was left as it was: its tree is named at a table no clear can drop");
			}
		} finally {
			clearQuietly(storage);
		}
	}

	/**
	 * A clear which removed no tree of its backend says why, and says it where the one table it did
	 * drop was its own catalog: a catalog standing over rows that name nothing - the state a backup
	 * restored beside older tables leaves - is one drop and no tree removed, which is the outcome of
	 * #888 exactly and not a clear that did something.
	 * <p>
	 * Decided on the drops of trees and not on every drop for that reason. Counted the other way the
	 * line is silent here, since dropping the catalog makes the count one.
	 */
	@Test
	public void testAClearWhichRemovedNoTreeSaysWhyEvenWhereItDroppedItsCatalog() throws Exception {
		final DN baseDN = DN.valueOf("dc=clear-catalog-only,dc=com");
		final TreeName owned = new TreeName(baseDN.toNormalizedUrlSafeString(), "id2entry");
		final ReportingStorage storage =
			new ReportingStorage(createBackendCfg(getBackendId() + "_catalogOnly", baseDN));
		final String catalogTable = storage.getTableName(storage.getCatalogTree());
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(owned, true);
				}
			});
			// the catalog table is there and names nothing, so the clear below has exactly one table to
			// drop - its own - and leaves the tree standing, named by nothing
			emptyTheCatalog(catalogTable);
			storage.close();

			storage.removeStorageFiles();

			assertFalse(isExistsTable(catalogTable), "the clear left its own catalog table standing");
			assertTrue(isExistsTable(storage.getTableName(owned)),
				"a table named by no catalog was dropped: nothing may be dropped that cannot be attributed");
			storage.assertReported("a clear which dropped its catalog and removed no tree of the backend"
					+ " said nothing about why, which is the silence of #888",
				"removed no tree of this backend", "has to be started once");
		} finally {
			clearQuietly(storage);
			// left standing on purpose above: its catalog is gone, so no clear of this backend names it
			dropTableIfExists(storage.getTableName(owned));
		}
	}

	/**
	 * A row recording a name outside the namespace this backend names its tables in is passed over
	 * rather than reaching a {@code drop table} built from a value read back out of a table - and the
	 * clear accounts for it, no other line of its report being able to: what such a row records is
	 * outside the {@code opendj} names the scan of what a clear left standing walks, and is dropped
	 * by nothing. The row is not there to be read again either - the catalog names itself last, so
	 * the clear drops that table with the row still in it - which is why the line is asserted here
	 * along with the drop: it is the only surviving copy of what the row said.
	 * <p>
	 * Nothing this version writes makes such a row, which is why the case makes one by hand.
	 */
	@Test
	public void testAClearAccountsForACatalogRowItCannotActOn() throws Exception {
		final TreeName tree = new TreeName("testCatalogForeignRow", "tree");
		final ReportingStorage storage = new ReportingStorage(createBackendCfg(getBackendId() + "_foreignRow"));
		final String tableName = storage.getTableName(tree);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
			final String catalogTable = storage.getTableName(storage.getCatalogTree());
			recordAnotherTable(catalogTable, "a_table_of_something_else");

			try (final Connection con = DriverManager.getConnection(getJdbcUrl())) {
				final List<String> skipped = new ArrayList<>();
				assertFalse(storage.readCatalogRows(con, catalogTable, skipped).containsKey(tree),
					"a row recording a name no table of this backend goes by was read as a tree to drop");
				assertEquals(skipped.size(), 1, "the row the read passed over was not described to its caller: " + skipped);
				assertTrue(skipped.get(0).contains("a_table_of_something_else"),
					"what the row records is named by nothing the clear could report: " + skipped);
			}

			// the clear still drops what it can: the catalog itself, which it names last
			storage.removeStorageFiles();
			assertTrue(isExistsTable(tableName),
				"the clear dropped the table of a tree its catalog names at another name than that table's");
			assertFalse(isExistsTable(catalogTable),
				"the clear left its own catalog table standing, so the row it passed over is still readable"
					+ " and the line reporting it is not the last copy of what it said");
			// the report itself and not the read behind it: reportSkippedRows() writes to nothing else,
			// so both of its call sites could be deleted and every assertion above would still hold
			storage.assertReported("the row the clear could not act on was reported by no line of it",
				"a_table_of_something_else", "passed over");
		} finally {
			clearQuietly(storage);
			// left standing on purpose above, so this case removes it rather than the next one meeting it
			dropTableIfExists(tableName);
		}
	}

	/**
	 * A clear drops the table its catalog records for a tree, not one it derives again from the tree
	 * name, so that a removal drops what was enrolled even if the naming of tables were ever to
	 * change. A row recording no table at all - all a version recording the name alone would have
	 * left - falls back to the derived name rather than naming nothing.
	 */
	@Test
	public void testAClearDropsTheTableTheCatalogRecords() throws Exception {
		final TreeName tree = new TreeName("testCatalogValue", "tree");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_value"), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
				}
			});
			try (final Connection con = DriverManager.getConnection(getJdbcUrl())) {
				// asked the way a clear asks it, narrowed to where an unqualified name of the connection
				// resolves: what the removal reads is this and not a lookup of a shape of its own
				final Map<TreeName, String> recorded =
					storage.catalogTables(con, JDBCStorage.TableScope.of(storage, con));
				assertEquals(recorded.get(tree), storage.getTableName(tree),
					"the catalog does not record the table holding the tree its row names");

				emptyTheRecordedTableNames(storage.getTableName(storage.getCatalogTree()));
				assertEquals(storage.catalogTables(con, JDBCStorage.TableScope.of(storage, con)).get(tree),
					storage.getTableName(tree),
					"a row recording no table name did not fall back to the name derived from the tree");
			}

			storage.removeStorageFiles();

			assertFalse(isExistsTable(storage.getTableName(tree)), "the table the catalog named survived the clear");
		} finally {
			clearQuietly(storage);
		}
	}

	/**
	 * A clear drops the catalog last, after every tree it names: what names the trees has to outlive
	 * them. Dropping a table is DDL, which mysql and oracle commit as they go, so a clear that fails
	 * halfway leaves a catalog still naming what is left - and the next attempt finishes it - where one
	 * that had dropped the catalog first would leave tables nothing names any more and no clear could
	 * ever reach.
	 * <p>
	 * Taken from the drops themselves and not from the map the loop walks: the map is built with the
	 * catalog put last by hand, so an assertion on it would hold of any loop at all - one that sorted
	 * the keys, or copied them into a HashSet, included.
	 */
	@Test
	public void testAClearDropsTheCatalogAfterEveryTreeItNames() throws Exception {
		final TreeName first = new TreeName("testCatalogDropOrder", "first");
		final TreeName second = new TreeName("testCatalogDropOrder", "second");
		final List<String> order = new ArrayList<>();
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_order"), null) {
			@Override
			void dropTable(Connection con, String tableName) throws SQLException {
				order.add(tableName);
				super.dropTable(con, tableName);
			}
		};
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(first, true);
					txn.openTree(second, true);
				}
			});

			storage.removeStorageFiles();

			final String catalogTable = storage.getTableName(storage.getCatalogTree());
			assertTrue(order.contains(storage.getTableName(first)) && order.contains(storage.getTableName(second)),
				"the clear did not drop the tables of the trees its catalog names: " + order);
			assertEquals(order.get(order.size() - 1), catalogTable,
				"the clear dropped the catalog before a tree it names, which no later clear could reach: " + order);
			assertEquals(order.indexOf(catalogTable), order.size() - 1,
				"the catalog was dropped more than once: " + order);
		} finally {
			clearQuietly(storage);
		}
	}

	/**
	 * What a clear leaves standing it reports, and it reports it as what it is: a table stamped with a
	 * tree of a base DN this backend serves is its own and can be removed by hand, while a table of a
	 * backend sharing this database (#873) is that backend's business and no part of this outcome.
	 * Told apart by the stamp of #866 and by nothing else - a table name is a bare hash.
	 */
	@Test
	public void testAClearReportsTheTablesItCanAttributeToThisBackend() throws Exception {
		final DN baseDN = DN.valueOf("dc=clear-report,dc=com");
		final TreeName owned = new TreeName(baseDN.toNormalizedUrlSafeString(), "id2entry");
		// a base DN of its own, so that the neighbour is a backend that reports this table as its own:
		// what this case asserts about the clear of the other one is then an absence and not a vacuity
		final DN neighbourBaseDN = DN.valueOf("dc=clear-report-neighbour,dc=com");
		final TreeName neighbourTree = new TreeName(neighbourBaseDN.toNormalizedUrlSafeString(), "id2entry");
		final JDBCStorage storage = new JDBCStorage(createBackendCfg(getBackendId() + "_reported", baseDN), null);
		final JDBCStorage neighbour =
			new JDBCStorage(createBackendCfg(getBackendId() + "_reportedNeighbour", neighbourBaseDN), null);
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(owned, true);
				}
			});
			neighbour.open(AccessMode.READ_WRITE);
			neighbour.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(neighbourTree, true);
				}
			});
			// the state of a backend upgraded from a version keeping no catalog: its tables are there
			// and nothing names them, so the clear that follows drops nothing at all
			dropTableBehindTheBackend(storage.getTableName(storage.getCatalogTree()));
			storage.close();

			storage.removeStorageFiles();

			assertTrue(isExistsTable(storage.getTableName(owned)),
				"a table named by no catalog was dropped: nothing may be dropped that cannot be attributed");
			try (final Connection con = DriverManager.getConnection(getJdbcUrl())) {
				// asked the way a clear asks it, through the same normalisation: a driver naming its
				// catalog with an empty string names no catalog, and a metadata pattern reads that as
				// "the tables belonging to no catalog at all", which would answer nothing
				final JDBCStorage.ClearLeftovers leftovers =
					storage.leftoverTables(con, JDBCStorage.TableScope.of(storage, con));
				assertNotNull(leftovers, "the database would not say which tables the clear left standing");
				assertTrue(leftovers.ours.toString().toLowerCase().contains(storage.getTableName(owned).toLowerCase()),
					"a table of a base DN this backend serves was not reported as its own: " + leftovers.ours);
				assertFalse(leftovers.unattributed.toString().toLowerCase().contains(storage.getTableName(owned).toLowerCase()),
					"a table this backend can name was reported as attributable to nobody: " + leftovers.unattributed);
				assertTrue(leftovers.unreadable.isEmpty(),
					"the stamp of a table this database does give up was reported as unreadable: " + leftovers.unreadable);
			}
			assertReportsNothingOf(storage, neighbour, neighbourTree);
		} finally {
			clearQuietly(neighbour);
			// the catalog of this one is gone, so its clear names nothing: the table it left standing on
			// purpose is dropped here by hand, as the report says such a table has to be
			clearQuietly(storage);
			dropTableIfExists(storage.getTableName(owned));
		}
	}

	/**
	 * Asserts that the clear of one backend says nothing whatsoever about the tables of another - and
	 * that the silence is one about tables the scan does reach: the backend those tables belong to is
	 * asked the same question and reports them as its own, so an absence here is a decision and not a
	 * scan that enumerated nothing.
	 */
	private void assertReportsNothingOf(JDBCStorage cleared, JDBCStorage other, TreeName otherTree) throws SQLException {
		try (final Connection con = DriverManager.getConnection(getJdbcUrl())) {
			final JDBCStorage.ClearLeftovers leftovers =
				cleared.leftoverTables(con, JDBCStorage.TableScope.of(cleared, con));
			assertNotNull(leftovers, "the database would not say which tables the clear left standing");
			final String reported =
				(leftovers.ours + " " + leftovers.unattributed + " " + leftovers.unreadable).toLowerCase();
			assertFalse(reported.contains(other.getTableName(otherTree).toLowerCase()),
				"the clear of one backend reported the table of another: " + reported);
			assertFalse(reported.contains(other.getTableName(other.getCatalogTree()).toLowerCase()),
				"the clear of one backend reported the catalog of another: " + reported);

			final JDBCStorage.ClearLeftovers theirs =
				other.leftoverTables(con, JDBCStorage.TableScope.of(other, con));
			assertNotNull(theirs, "the database would not say which tables the neighbour is holding");
			assertTrue(theirs.ours.toString().toLowerCase().contains(other.getTableName(otherTree).toLowerCase()),
				"the table left unreported is one the scan does not reach at all: " + theirs.ours);
		}
	}

	/**
	 * Takes every row out of a catalog, leaving the tables it named standing: what a backend upgraded
	 * from a version keeping no catalog holds before its first read-write open fills one in.
	 */
	private void emptyTheCatalog(String catalogTable) throws SQLException {
		try (final Connection con = DriverManager.getConnection(getJdbcUrl());
			 final Statement st = con.createStatement()) {
			st.executeUpdate("delete from " + catalogTable);
		}
	}

	/**
	 * Records the given table for every row of a catalog, as a version naming its tables otherwise
	 * would have left them: the row names the right tree and a table this version never creates.
	 */
	private void recordAnotherTable(String catalogTable, String tableName) throws SQLException {
		try (final Connection con = DriverManager.getConnection(getJdbcUrl());
			 final PreparedStatement statement = con.prepareStatement("update " + catalogTable + " set v=?")) {
			statement.setBytes(1, tableName.getBytes(StandardCharsets.UTF_8));
			statement.executeUpdate();
		}
	}

	/** Empties the recorded table name of every row of a catalog, as a version recording none would have left it. */
	private void emptyTheRecordedTableNames(String catalogTable) throws SQLException {
		try (final Connection con = DriverManager.getConnection(getJdbcUrl());
			 final PreparedStatement statement = con.prepareStatement("update " + catalogTable + " set v=?")) {
			statement.setBytes(1, new byte[0]);
			statement.executeUpdate();
		}
	}

	/**
	 * Drops a table a clear left standing on purpose, so that it is not left behind for the rest of
	 * the class. A failure here is swallowed rather than replacing the failure of the case it cleans
	 * up after: what it leaves is dropped by the dropStaleTrees() of the next run of the class.
	 */
	private void dropTableIfExists(String tableName) {
		try {
			if (isExistsTable(tableName)) {
				dropTableBehindTheBackend(tableName);
			}
		} catch (SQLException ignored) {
		}
	}

	/**
	 * A storage which keeps the lines every clear it runs reports, so that a case can hold that
	 * account to what it says.
	 * <p>
	 * Those lines change no state whatsoever, so a case asserting on the database a clear leaves
	 * behind passes just as well with all of them deleted - which is how this report came to be
	 * changed in three rounds of review with nothing able to fail. Here rather than in the case that
	 * needed it first, for the same reason: the next line of the report wants an assertion too, and a
	 * helper per case is what got the report where it was. See {@link JDBCStorage#reportClearLine}.
	 */
	protected static final class ReportingStorage extends JDBCStorage {
		private final List<String> lines = Collections.synchronizedList(new ArrayList<String>());

		ReportingStorage(JDBCBackendCfg cfg) {
			super(cfg, null);
		}

		@Override
		void reportClearLine(LocalizableMessage line) {
			lines.add(line.toString());
			super.reportClearLine(line); // and on to the log, which is where an operator meets it
		}

		/** Every line reported so far, in the order the clears that reported them ran. */
		List<String> reported() {
			synchronized (lines) {
				return new ArrayList<>(lines);
			}
		}

		/**
		 * Fails unless one reported line holds every one of the fragments. By fragments and not by the
		 * whole line: what a case is entitled to pin is the thing the line is about, and a report
		 * asserted word for word is a report nobody may improve the wording of.
		 */
		void assertReported(String whatWentUnsaid, String... fragments) {
			for (final String line : reported()) {
				boolean holdsAll = true;
				for (final String fragment : fragments) {
					holdsAll &= line.contains(fragment);
				}
				if (holdsAll) {
					return;
				}
			}
			fail(whatWentUnsaid + "; the clear reported: " + reported());
		}
	}
}
