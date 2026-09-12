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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.jdbc.JDBCStorage.Dialect;
import org.opends.server.backends.jdbc.JDBCStorage.StatementBound;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * What a DDL of this backend is told to do about a lock another session holds (#885). It needs no
 * database: the connection is a mock, so what each engine is told - and what it is told to put back
 * afterwards - is pinned wherever the build runs, while the container suites cover a drop really
 * queued behind another session.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "jdbc" }, sequential = true)
public class JDBCDdlLockBoundTestCase extends DirectoryServerTestCase {

	/** The tree the cases below name; the table behind it is a hash of that name. */
	private static final TreeName TREE = new TreeName("dc=example,dc=com", "id2entry");
	/** A second one, for the loop that drops every table of a backend under a single bound. */
	private static final TreeName OTHER_TREE = new TreeName("dc=example,dc=com", "dn2id");

	/** The DDL itself, as it appears among the session statements issued around it. */
	private static final String THE_DDL = "the ddl";

	/** The backend the storage of a case is configured as, which is what names its tree catalog. */
	private static final String BACKEND_ID = "ddlLockBound";
	/**
	 * That catalog's table, which the connection of a case answers as not being there (see engine()):
	 * a case reaching {@code openTree()} therefore takes the branch that creates it.
	 */
	private static final String CATALOG_TABLE =
		JDBCStorage.toTableName(new TreeName(JDBCStorage.CATALOG_BASE_DN, BACKEND_ID));

	/** What the connection of a case was asked to run, in the order it was asked to run it. */
	private final List<String> issued = new ArrayList<>();

	/**
	 * What the catalog's own connection was asked to run. The create table of the catalog is issued
	 * on that connection rather than on the caller's ({@code CatalogSession}), so what is issued
	 * around it is read off a list of its own instead of out of the middle of the caller's.
	 */
	private final List<String> catalogIssued = new ArrayList<>();

	private JDBCStorage storage;

	@BeforeMethod
	public void createStorage() {
		storage = new JDBCStorage(backendCfg(), null);
		issued.clear();
		catalogIssued.clear();
	}

	/** A configuration naming this backend, which is all any case here reads off one. */
	private static JDBCBackendCfg backendCfg() {
		final JDBCBackendCfg cfg = mockCfg(JDBCBackendCfg.class);
		when(cfg.getBackendId()).thenReturn(BACKEND_ID);
		return cfg;
	}

	@AfterMethod
	public void clearProperties() {
		System.clearProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY);
	}

	/**
	 * The stamp of the same open is already bounded at five seconds
	 * ({@code COMMENT_LOCK_TIMEOUT_SECONDS}), and it takes its lock on the very tables this DDL
	 * creates and drops.
	 */
	@Test
	public void testTheDefaultGivesUpOnALockAfterFiveSeconds() {
		assertEquals(JDBCStorage.ddlLockBoundSeconds(), 5);
	}

	/** Zero is "wait as this backend waited before this bound existed", and so is anything under it. */
	@Test
	public void testAValueOfZeroOrLessLeavesTheWaitUnbounded() {
		System.setProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY, "0");
		assertEquals(JDBCStorage.ddlLockBoundSeconds(), 0);
		System.setProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY, "-1");
		assertEquals(JDBCStorage.ddlLockBoundSeconds(), 0);
	}

	/**
	 * A value that is not a number leaves the default in force rather than reading as a zero, which
	 * is what {@code Integer.getInteger()} does with one - so a typo does not silently take the bound
	 * off.
	 */
	@Test
	public void testAValueThatIsNotANumberKeepsTheDefault() {
		System.setProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY, "five seconds");
		assertEquals(JDBCStorage.ddlLockBoundSeconds(), 5);
	}

	/** The ceiling every bound of this backend is taken down to, for the reason recorded there. */
	@Test
	public void testAValuePastTheCeilingIsTakenDownToIt() {
		System.setProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY, String.valueOf(Integer.MAX_VALUE));
		assertEquals(JDBCStorage.ddlLockBoundSeconds(), JDBCStorage.MAX_BOUND_SECONDS);
	}

	@DataProvider
	public Object[][] engines() {
		return new Object[][] {
			// postgres takes milliseconds, and "set local" is discarded by the commit that ends the
			// DDL: there is nothing to read beforehand and nothing to put back afterwards
			{ "postgres", Dialect.POSTGRES, "0", asList("set local lock_timeout = 5000", THE_DDL) },
			// mysql takes seconds, and the setting outlives the transaction on a pooled connection:
			// what the session carried (a year, by default) is read first and put back after
			{ "mysql", Dialect.MYSQL, "31536000", asList("select @@session.lock_wait_timeout",
				"set session lock_wait_timeout=5", THE_DDL, "set session lock_wait_timeout=31536000") },
			// sql server takes milliseconds, and its setting bounds every lock wait of the session -
			// -1, wait forever, is what a session of it carries until something says otherwise
			{ "sql server", Dialect.MICROSOFT, "-1", asList("select @@lock_timeout",
				"set lock_timeout 5000", THE_DDL, "set lock_timeout -1") },
			// oracle is left to its own ddl_lock_timeout, which is tighter than anything set here
			{ "oracle", Dialect.ORACLE, "0", singletonList(THE_DDL) },
		};
	}

	/** What each engine is told around a DDL of this backend, in the order it is told it. */
	@Test(dataProvider = "engines")
	public void testWhatEachEngineIsToldAroundADdl(String name, Dialect dialect, String carries,
			List<String> expected) throws Exception {
		storage.withDdlLockBound(recording(mock(Connection.class), carries), dialect, theDdl());
		assertEquals(issued, expected, name);
	}

	/**
	 * Oracle gives up on a DDL lock at once ({@code ddl_lock_timeout} is 0), so a bound of ours would
	 * only loosen it - and a deployment that raised it globally did so on purpose. Putting it back
	 * would mean reading {@code v$parameter}, which the account of a backend often may not.
	 */
	@Test
	public void testOracleIsLeftToItsOwnDdlLockTimeout() {
		assertNull(Dialect.ORACLE.ddlLockBoundSql(5, null));
	}

	/** An engine none of these statements fit is fed none of them, as its statistics are left alone. */
	@Test
	public void testAnEngineThisBackendDoesNotKnowIsLeftAlone() throws Exception {
		storage.withDdlLockBound(recording(mock(Connection.class), "0"), null, theDdl());
		assertEquals(issued, singletonList(THE_DDL));
	}

	/**
	 * ... and is told so once, rather than left to be found out. {@code dialectOf()} keys on the class
	 * name of the driver, so a mariadb, percona or aurora driver against a live mysql answers null
	 * here - and that is a session whose {@code lock_wait_timeout} is a year, which is the wait this
	 * bound exists to end. The strict parsing of the property exists so that a deployment which asked
	 * for a bound is never quietly left with none, and this is the same silence one property later.
	 */
	@Test
	public void testAnEngineThisBackendDoesNotKnowIsReported() throws Exception {
		storage.withDdlLockBound(recording(mock(Connection.class), "0"), null, theDdl());

		assertTrue(storage.ddlLockBoundEngineUnknownWarned.get(),
			"a driver this backend knows no lock bound for left the wait of every DDL unbounded and unsaid");
	}

	/** A deployment that turned the bound off asked for none anywhere, and has nothing to act on. */
	@Test
	public void testAWaitNobodyAskedToBoundIsNotReportedAsAnUnknownEngine() throws Exception {
		System.setProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY, "0");

		storage.withDdlLockBound(recording(mock(Connection.class), "0"), null, theDdl());

		assertFalse(storage.ddlLockBoundEngineUnknownWarned.get(),
			"a bound nobody asked for was reported as an engine this backend does not know");
	}

	/**
	 * And oracle is an engine this backend knows perfectly well: it is left to its own
	 * {@code ddl_lock_timeout} on purpose, which is a decision rather than a gap to report.
	 */
	@Test
	public void testOracleIsNotReportedAsAnEngineThisBackendDoesNotKnow() throws Exception {
		storage.withDdlLockBound(recording(mock(Connection.class), "0"), Dialect.ORACLE, theDdl());

		assertFalse(storage.ddlLockBoundEngineUnknownWarned.get(),
			"the engine left alone deliberately was reported as one this backend cannot bound");
	}

	/** Turning the bound off costs no round trip either: the DDL waits exactly as it did before. */
	@Test
	public void testAnUnboundedWaitIssuesNoSessionStatement() throws Exception {
		System.setProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY, "0");
		storage.withDdlLockBound(recording(mock(Connection.class), "31536000"), Dialect.MYSQL, theDdl());
		assertEquals(issued, singletonList(THE_DDL));
	}

	/**
	 * The setting is put back even when the DDL fails. It is a pooled connection, and
	 * {@code CachedConnection.close()} only rolls back: on sql server the setting left behind would
	 * bound every lock wait of whoever borrows the connection next, row locks included, and
	 * {@code write()} replays no conflict of those.
	 */
	@Test
	public void testTheBoundIsPutBackWhenTheDdlFails() throws Exception {
		final SQLException rejected = new SQLException("table already exists", "42S01");
		try {
			storage.withDdlLockBound(recording(mock(Connection.class), "31536000"), Dialect.MYSQL, () -> {
				issued.add(THE_DDL);
				throw rejected;
			});
			fail("the failure of the ddl was swallowed");
		}catch (SQLException e) {
			assertSame(e, rejected, "the failure of the ddl was replaced");
		}
		assertEquals(issued, asList("select @@session.lock_wait_timeout", "set session lock_wait_timeout=5",
			THE_DDL, "set session lock_wait_timeout=31536000"));
	}

	/**
	 * A setting can reach the server and still fail on the close() of the statement that carried it -
	 * a connection that broke in between - and no driver tells that apart from a setting that never
	 * arrived. The session has it either way, so it is taken off again once the DDL is through: this
	 * connection goes back to a pool, and on sql server a lock_timeout left behind ends every lock wait
	 * of whoever borrows it next, row locks included.
	 */
	@Test
	public void testASettingThatBrokeOnTheCloseOfItsStatementIsStillTakenOff() throws Exception {
		final Connection con = breakingOnTheCloseOfASetting(mock(Connection.class), "31536000");

		storage.withDdlLockBound(con, Dialect.MYSQL, theDdl());

		assertEquals(issued, asList("select @@session.lock_wait_timeout", "set session lock_wait_timeout=5",
			THE_DDL, "set session lock_wait_timeout=31536000"));
	}

	/**
	 * And the DDL it wrapped is reported the way a bounded one is. The setting reached the server, so
	 * the wait really was bounded - reporting that failure as the bare 55P03 it arrives as is the gap
	 * this bound exists to close, and the log line above it says only that the bound may not be there.
	 */
	@Test
	public void testADdlBoundedByASettingWhoseCloseFailedStillNamesTheProperty() throws Exception {
		final SQLException lockWait = new SQLException("Lock wait timeout exceeded", "40001", 1205);
		try {
			storage.withDdlLockBound(breakingOnTheCloseOfASetting(mock(Connection.class), "31536000"),
				Dialect.MYSQL, () -> {
					throw lockWait;
				});
			fail("the lock timeout was swallowed");
		}catch (SQLException e) {
			assertTrue(e.getMessage().contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY), e.getMessage());
			assertSame(e.getCause(), lockWait, "the failure of the engine was not chained");
		}
	}

	/**
	 * A value that could not be given back does not fail a DDL that went through. The restore runs
	 * from a finally while the caller may be being unwound, where a throw takes the place of whatever
	 * brought it there (JLS 14.20.2) - here a create table the engine accepted.
	 */
	@Test
	public void testAValueThatCouldNotBeGivenBackDoesNotFailADdlThatWentThrough() throws Exception {
		final Connection con = refusingToGiveTheValueBack(mock(Connection.class), "31536000");

		storage.withDdlLockBound(con, Dialect.MYSQL, theDdl());

		assertEquals(issued, asList("select @@session.lock_wait_timeout", "set session lock_wait_timeout=5",
			THE_DDL, "set session lock_wait_timeout=31536000"));
	}

	/** And it does not displace the failure of one that did not: that failure is what says what went wrong. */
	@Test
	public void testAValueThatCouldNotBeGivenBackDoesNotDisplaceTheFailureOfADdl() throws Exception {
		final SQLException rejected = new SQLException("table already exists", "42S01");
		try {
			storage.withDdlLockBound(refusingToGiveTheValueBack(mock(Connection.class), "31536000"),
				Dialect.MYSQL, () -> {
					throw rejected;
				});
			fail("the failure of the ddl was swallowed");
		}catch (SQLException e) {
			assertSame(e, rejected, "the failure of the ddl was replaced by the one of the restore");
		}
	}

	/**
	 * A bound with no way of putting back what it displaces is not set at all: a server whose session
	 * does not have the variable - a mysql-compatible one behind connector/j - would otherwise be
	 * given a bound this backend could never take off the pooled connection again.
	 */
	@Test
	public void testABoundThatCannotBeReadBackIsNotSetAtAll() throws Exception {
		final Connection con = mock(Connection.class);
		final Statement statement = mock(Statement.class);
		when(statement.executeQuery(anyString()))
			.thenThrow(new SQLException("unknown system variable", "HY000", 1193));
		when(con.createStatement()).thenReturn(statement);

		storage.withDdlLockBound(con, Dialect.MYSQL, theDdl());

		assertEquals(issued, singletonList(THE_DDL));
		verify(statement, never()).execute(anyString());
	}

	/** The same where the session answers with something no setting of it would take back. */
	@Test
	public void testAValueTheSessionCouldNotBeGivenBackIsNotDisplaced() throws Exception {
		storage.withDdlLockBound(recording(mock(Connection.class), "unlimited"), Dialect.MYSQL, theDdl());
		assertEquals(issued, asList("select @@session.lock_wait_timeout", THE_DDL));
	}

	/**
	 * A connection that refuses the setting outright leaves the DDL unbounded rather than failing it.
	 * The bound is an improvement on a wait: a backend that opened before this bound existed has to
	 * open still, and a session statement that fails on a connection whose DDL would have gone through
	 * is not a reason to fail that DDL.
	 */
	@Test
	public void testAConnectionThatRefusesTheSettingStillRunsTheDdl() throws Exception {
		final Connection con = mock(Connection.class);
		when(con.createStatement()).thenThrow(new SQLException("no session statement here", "42000"));

		storage.withDdlLockBound(con, Dialect.POSTGRES, theDdl());

		assertEquals(issued, singletonList(THE_DDL));
	}

	/**
	 * A DDL that gave up at the bound arrives as a bare 55P03 / 1205 / 1222, naming neither the wait
	 * it ended nor the property that ended it - the gap {@code timedOut()} closes for a statement
	 * bound.
	 */
	@Test
	public void testALockTheDdlGaveUpOnNamesTheProperty() throws Exception {
		final SQLException lockNotAvailable = new SQLException("canceling statement due to lock timeout", "55P03");
		try {
			storage.withDdlLockBound(recording(mock(Connection.class), "0"), Dialect.POSTGRES, () -> {
				throw lockNotAvailable;
			});
			fail("the lock timeout was swallowed");
		}catch (SQLException e) {
			assertTrue(e.getMessage().contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY), e.getMessage());
			assertSame(e.getCause(), lockNotAvailable, "the failure of the engine was not chained");
			assertEquals(e.getSQLState(), "55P03", "the state a caller classifies this by was dropped");
		}
	}

	/**
	 * The state and the vendor number are carried over, so a caller classifying the failure reads
	 * exactly what it read before this bound existed: a mysql lock wait arrives in class 40, and that
	 * state alone is what {@code write()} replays a conflict on.
	 */
	@Test
	public void testARewrittenMysqlLockWaitStaysTheConflictAWriteKnows() throws Exception {
		final SQLException lockWait = new SQLException("Lock wait timeout exceeded; try restarting transaction",
			"40001", 1205);
		try {
			storage.withDdlLockBound(recording(mock(Connection.class), "31536000"), Dialect.MYSQL, () -> {
				throw lockWait;
			});
			fail("the lock timeout was swallowed");
		}catch (SQLException e) {
			assertTrue(e.getMessage().contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY), e.getMessage());
			assertEquals(e.getErrorCode(), 1205, "the number a caller classifies this by was dropped");
			assertEquals(JDBCStorage.conflictVerdict(e, "com.mysql.cj.jdbc.ConnectionImpl").conflict,
				JDBCStorage.Conflict.AFTER_LOCK_WAIT,
				"a conflict write() replayed before this bound existed is no longer read as one");
		}
	}

	/**
	 * And a sql server lock wait is left as unreplayable as it was: error 1222 is no conflict of
	 * {@code isConflict()}, and a DDL made to look like one would be replayed into the same wait.
	 */
	@Test
	public void testARewrittenSqlServerLockWaitIsMadeNoMoreReplayable() throws Exception {
		final SQLException lockWait = new SQLException("Lock request time out period exceeded.", "S0001", 1222);
		try {
			storage.withDdlLockBound(recording(mock(Connection.class), "-1"), Dialect.MICROSOFT, () -> {
				throw lockWait;
			});
			fail("the lock timeout was swallowed");
		}catch (SQLException e) {
			assertTrue(e.getMessage().contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY), e.getMessage());
			assertEquals(e.getErrorCode(), 1222, "the number a caller classifies this by was dropped");
			assertEquals(JDBCStorage.conflictVerdict(e, "com.microsoft.sqlserver.jdbc.SQLServerConnection").conflict,
				JDBCStorage.Conflict.NONE,
				"a lock wait of a ddl was made a conflict write() would replay");
		}
	}

	/**
	 * A failure of another kind is left exactly as it is: naming a property that had nothing to do
	 * with it sends an operator to raise a value that changes nothing about what they saw.
	 */
	@Test
	public void testAFailureThatIsNotALockIsLeftExactlyAsItIs() throws Exception {
		final SQLException denied = new SQLException("permission denied for schema public", "42501");
		try {
			storage.withDdlLockBound(recording(mock(Connection.class), "0"), Dialect.POSTGRES, () -> {
				throw denied;
			});
			fail("the failure was swallowed");
		}catch (SQLException e) {
			assertSame(e, denied, "a failure that is not a lock wait was reported as one");
		}
	}

	/**
	 * And so is a statement the bound of its own class cancelled: {@code bulk.timeout} ends a create
	 * index the engine was working on, which is not a lock this DDL was queued for, and
	 * {@code timedOut()} has already named the property that ended it.
	 */
	@Test
	public void testAStatementItsOwnBoundCancelledIsNotReportedAsALockWait() throws Exception {
		final SQLException cancelled = new SQLTimeoutException("jdbc: the statement took 100200 ms, reaching the"
			+ " 100s of " + StatementBound.BULK.property, "57014", 0);
		try {
			storage.withDdlLockBound(recording(mock(Connection.class), "0"), Dialect.POSTGRES, () -> {
				throw cancelled;
			});
			fail("the cancelled statement was swallowed");
		}catch (SQLException e) {
			assertSame(e, cancelled, "a statement its own bound cancelled was reported as a lock wait");
		}
	}

	/**
	 * The drop of a tree goes through the funnel every DDL of a transaction takes, so it is bounded
	 * wherever it is issued from - {@code deleteTree()} here, and the create table and create index of
	 * {@code openTree()} the same way.
	 */
	@Test
	public void testTheDropOfATreeIsBounded() throws Exception {
		final JDBCStorage bounded = storageHandingOut(engine(postgresConnection.class, "0"));

		bounded.write(txn -> txn.deleteTree(TREE));

		// the search path in front of them is the lookup that decides whether there is a table to drop
		// at all, narrowed to the schemas an unqualified name of this connection resolves in (#888): it
		// reads a session setting rather than the data, and takes a bound of its own
		assertEquals(issued, asList("select unnest(current_schemas(true))",
			"set local lock_timeout = 5000",
			"drop table " + JDBCStorage.toTableName(TREE)));
	}

	/**
	 * The delete that empties a tree before an import is no DDL: it waits for row locks, which
	 * {@code write()} replays a conflict of and which a bound meant for the metadata lock of a DDL has
	 * no business ending.
	 */
	@Test
	public void testTheDeleteThatEmptiesATreeIsNotBounded() throws Exception {
		final JDBCStorage bounded = storageHandingOut(engine(postgresConnection.class, "0"));

		// closed the way an import closes one: the importer holds a borrowed connection, and only its
		// close() gives that connection - and the permit it took - back. The statements of that close are
		// no part of this case, so what was issued is read before it.
		try (final Importer importer = bounded.new ImporterImpl()) {
			importer.clearTree(TREE);

			assertEquals(issued, singletonList("delete from " + JDBCStorage.toTableName(TREE)));
		}
	}

	/**
	 * The drop loop of {@code removeStorageFiles()} bypasses that funnel and commits once at the end,
	 * so the bound is set once around the whole loop rather than once per table - on postgres one
	 * {@code set local} covers every drop of the single transaction it runs in.
	 */
	@Test
	public void testTheDropLoopOfARemovedBackendIsBoundedOnce() throws Exception {
		final Connection con = engine(postgresConnection.class, "0");
		final JDBCStorage.TableScope scope = JDBCStorage.TableScope.of(storage, con);
		issued.clear(); // the search path the scope read is no part of what this case is about

		final JDBCStorage.ClearCounts counts = storage.dropCatalogTables(con, scope, catalogOf(TREE, OTHER_TREE));

		assertEquals(issued, asList("set local lock_timeout = 5000",
			"drop table " + JDBCStorage.toTableName(TREE),
			"drop table " + JDBCStorage.toTableName(OTHER_TREE)));
		assertEquals(counts.dropped, 2, "the clear did not account for the tables it dropped under the bound");
	}

	/**
	 * And a clear with no row to act on is committed without the bound: putting it on costs a readback
	 * and a restore of its own, and the first clear of a backend upgraded from a version that kept no
	 * catalog - the case {@code CLEAR_DROPPED_NOTHING} describes - has no DDL for them to bound.
	 */
	@Test
	public void testAClearWithNoTableToDropIsGivenNoBound() throws Exception {
		final Connection con = engine(postgresConnection.class, "0");
		final JDBCStorage.TableScope scope = JDBCStorage.TableScope.of(storage, con);
		issued.clear();

		storage.dropCatalogTables(con, scope, Collections.<TreeName, String>emptyMap());

		assertEquals(issued, emptyList());
	}

	/**
	 * The lookup deciding each drop of that loop runs under the same bound as the drop it decides, and
	 * it answers with a {@link StorageRuntimeException} rather than with the failure the engine gave
	 * it. A lock this bound ended must be named there too: an operator meeting a bare 55P03 out of a
	 * clear is the unexplained state this bound exists to stop shipping, and the drop one line away
	 * would have named the property for the very same wait.
	 */
	@Test
	public void testALockTheLookupOfAClearGaveUpOnNamesTheProperty() throws Exception {
		final Connection con = engine(postgresConnection.class, "0");
		final JDBCStorage.TableScope scope = JDBCStorage.TableScope.of(storage, con);
		final SQLException lockNotAvailable = new SQLException("canceling statement due to lock timeout", "55P03");
		givingUpOnTheLookup(con, lockNotAvailable);

		try {
			storage.dropCatalogTables(con, scope, catalogOf(TREE));
			fail("the clear went through although its lookup gave up on a lock");
		} catch (StorageRuntimeException e) {
			assertTrue(e.getCause() instanceof SQLTimeoutException,
				"the lookup's failure was left as the engine reported it: " + e.getCause());
			assertTrue(e.getCause().getMessage().contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY),
				e.getCause().getMessage());
			assertSame(e.getCause().getCause(), lockNotAvailable, "the failure of the engine was not chained");
		}
	}

	/**
	 * And a failure of that same lookup which was no lock wait is given back exactly as it arrived: the
	 * rename says one thing about one wait, and a table that is not there or a connection that went
	 * must not come out of a clear wearing the name of a property that had nothing to do with it.
	 */
	@Test
	public void testAFailureOfTheLookupThatWasNoLockWaitIsLeftExactlyAsItIs() throws Exception {
		final Connection con = engine(postgresConnection.class, "0");
		final JDBCStorage.TableScope scope = JDBCStorage.TableScope.of(storage, con);
		final SQLException noSuchTable = new SQLException("relation does not exist", "42P01");
		givingUpOnTheLookup(con, noSuchTable);

		try {
			storage.dropCatalogTables(con, scope, catalogOf(TREE));
			fail("the clear went through although its lookup failed");
		} catch (StorageRuntimeException e) {
			assertSame(e.getCause(), noSuchTable, "a failure that was no lock wait was renamed");
		}
	}

	/** A connection whose table lookup answers with the given failure, as the drop loop asks it. */
	private void givingUpOnTheLookup(final Connection con, final SQLException failure) throws SQLException {
		final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
		when(metaData.getTables(any(), any(), any(), any())).thenThrow(failure);
		when(con.getMetaData()).thenReturn(metaData);
	}

	/**
	 * The create table of the tree catalog is the DDL of this backend that goes through neither
	 * {@code commitStatement()} nor the drop loop: {@code openTree()} creates that table on the
	 * catalog's own connection before it enrols the tree it is opening, and a backend upgraded from a
	 * version that kept no catalog meets it on the open of every one of its trees.
	 */
	@Test
	public void testTheCreateOfTheCatalogTableIsBounded() throws Exception {
		final JDBCStorage bounded = storageHandingOut(engine(postgresConnection.class, "0"),
			engine(postgresConnection.class, "0", catalogIssued));

		bounded.write(txn -> txn.openTree(TREE, true));

		assertEquals(firstOfTheCatalog(2), asList("set local lock_timeout = 5000",
			"create table " + CATALOG_TABLE + " (h char(128),k bytea,v bytea,primary key(h,k))"));
	}

	/**
	 * And it goes through the same helper every other DDL of this backend does, so the session of an
	 * engine whose setting outlives the transaction is read back first and given its value back after
	 * - on a connection which is not the pool's, and which the write that opened it closes.
	 */
	@Test
	public void testTheCreateOfTheCatalogTableGivesMysqlItsValueBack() throws Exception {
		final JDBCStorage bounded = storageHandingOut(engine(mysqlConnection.class, "31536000"),
			engine(mysqlConnection.class, "31536000", catalogIssued));

		bounded.write(txn -> txn.openTree(TREE, true));

		assertEquals(firstOfTheCatalog(4), asList("select @@session.lock_wait_timeout",
			"set session lock_wait_timeout=5",
			"create table " + CATALOG_TABLE + " (h char(128),k varbinary(255),v longblob,primary key(h,k))",
			"set session lock_wait_timeout=31536000"));
	}

	/**
	 * A lock that create gave up on names the property that ended the wait, all the way out to the
	 * operator: the failure is wrapped as the backend not being able to create the table which holds
	 * its catalog, and a bare 55P03 inside that says nothing about which wait ended or what to raise.
	 */
	@Test
	public void testALockTheCreateOfTheCatalogTableGaveUpOnNamesTheProperty() throws Exception {
		final JDBCStorage bounded = storageHandingOut(engine(postgresConnection.class, "0"),
			failingTheCreate(engine(postgresConnection.class, "0", catalogIssued),
				new SQLException("canceling statement due to lock timeout", "55P03")));

		try {
			bounded.write(txn -> txn.openTree(TREE, true));
			fail("a create of the catalog table that gave up on a lock has to reach the caller");
		}catch (Exception expected) {
			assertTrue(namesTheProperty(expected),
				"the lock this bound ended reached the operator unnamed: " + expected);
		}
	}

	/** The statements of the catalog's connection a case reads, without running off its end. */
	private List<String> firstOfTheCatalog(int statements) {
		return catalogIssued.subList(0, Math.min(statements, catalogIssued.size()));
	}

	/** Whether the failure, or any link of its chain, names the property that ended the wait. */
	private static boolean namesTheProperty(Throwable failure) {
		for (Throwable link = failure; link != null; link = link.getCause()) {
			if (link.getMessage() != null
					&& link.getMessage().contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY)) {
				return true;
			}
		}
		return false;
	}

	/** A connection whose create table is the statement the engine refuses, with the given failure. */
	private Connection failingTheCreate(final Connection con, final SQLException failure) throws SQLException {
		// doAnswer() rather than when(): the connection has been given a prepareStatement() already, and
		// calling it inside a when() would run that answer - which stubs a mock of its own - in the
		// middle of this stubbing, which mockito reads as a stubbing that never named a method
		doAnswer(invocation -> {
			final String sql = (String) invocation.getArguments()[0];
			catalogIssued.add(sql);
			final PreparedStatement statement = mock(PreparedStatement.class);
			when(statement.getConnection()).thenReturn(con);
			if (sql.startsWith("create table ")) {
				when(statement.executeUpdate()).thenThrow(failure);
			}
			return statement;
		}).when(con).prepareStatement(anyString());
		return con;
	}

	/** A catalog naming each of the given trees at the table its name hashes to. */
	private static Map<TreeName, String> catalogOf(TreeName... trees) {
		final Map<TreeName, String> catalog = new LinkedHashMap<>();
		for (final TreeName tree : trees) {
			catalog.put(tree, JDBCStorage.toTableName(tree));
		}
		return catalog;
	}

	/**
	 * A session already giving up sooner than this bound keeps exactly what it has: a deployment that
	 * set {@code lock_wait_timeout} tighter did so on purpose, and loosening it to ours for the length
	 * of a DDL is the very thing that leaves oracle alone. Nothing is set, so nothing is put back
	 * either.
	 */
	@Test
	public void testAMysqlSessionAlreadyTighterThanTheBoundKeepsWhatItHas() throws Exception {
		storage.withDdlLockBound(recording(mock(Connection.class), "1"), Dialect.MYSQL, theDdl());

		assertEquals(issued, asList("select @@session.lock_wait_timeout", THE_DDL));
	}

	/**
	 * On sql server 0 is "do not wait at all", which is tighter than any bound of ours, while -1 is
	 * "wait forever" and is replaced - the case the data provider above covers. A value read as a
	 * number that happens to be negative must not be mistaken for a tight one.
	 */
	@Test
	public void testASqlServerSessionThatDoesNotWaitAtAllKeepsWhatItHas() throws Exception {
		storage.withDdlLockBound(recording(mock(Connection.class), "0"), Dialect.MICROSOFT, theDdl());

		assertEquals(issued, asList("select @@lock_timeout", THE_DDL));
	}

	/**
	 * Outside a transaction block postgres answers {@code SET LOCAL} with a warning and does nothing
	 * with it: the driver raises nothing, so the DDL would run with no bound at all and the log would
	 * read exactly like a bounded one. The setting is not issued there.
	 */
	@Test
	public void testAConnectionInAutoCommitIsGivenNoSetLocal() throws Exception {
		final Connection con = recording(mock(Connection.class), "0");
		when(con.getAutoCommit()).thenReturn(true);

		storage.withDdlLockBound(con, Dialect.POSTGRES, theDdl());

		assertEquals(issued, singletonList(THE_DDL));
	}

	/**
	 * A statement that fails inside a postgres transaction aborts it, and the DDL after it would then
	 * fail with 25P02 rather than running unbounded as it did before this bound existed - a backend
	 * that used to open would stop opening because of the bound meant to protect it. The transaction is
	 * taken back to the point before the setting, which also undoes a {@code set local} that did reach
	 * the server.
	 */
	@Test
	public void testTheTransactionIsTakenBackToBeforeASettingThatFailed() throws Exception {
		final Connection con = mock(Connection.class);
		final Savepoint beforeTheBound = mock(Savepoint.class);
		when(con.setSavepoint()).thenReturn(beforeTheBound);
		when(con.createStatement()).thenThrow(new SQLException("current transaction is aborted", "25P02"));

		storage.withDdlLockBound(con, Dialect.POSTGRES, theDdl());

		verify(con).rollback(beforeTheBound);
		assertEquals(issued, singletonList(THE_DDL));
	}

	/** And a setting that went through is left standing: it is what bounds the DDL that follows it. */
	@Test
	public void testATransactionWhoseSettingWentThroughIsNotTakenBack() throws Exception {
		final Connection con = recording(mock(Connection.class), "0");
		final Savepoint beforeTheBound = mock(Savepoint.class);
		when(con.setSavepoint()).thenReturn(beforeTheBound);

		storage.withDdlLockBound(con, Dialect.POSTGRES, theDdl());

		verify(con, never()).rollback(beforeTheBound);
		assertEquals(issued, asList("set local lock_timeout = 5000", THE_DDL));
	}

	/**
	 * More than one wait of an engine reports the same number: mysql reports the row lock of
	 * {@code innodb_lock_wait_timeout} - 50 s by default, and what a create index under
	 * {@code ALGORITHM=COPY} waits on - as the same ERROR 1205 as a metadata lock. A wait that ran far
	 * longer than this bound was ended by something else, and naming this property for it would send an
	 * operator to raise the one setting that cannot help.
	 */
	@Test
	public void testALockWaitFarPastTheBoundIsLeftExactlyAsItIs() {
		final SQLException rowLock = new SQLException("Lock wait timeout exceeded; try restarting transaction",
			"40001", 1205);
		final long fiftySecondsAgo = System.nanoTime() - 50L * 1000 * 1000 * 1000;

		assertSame(storage.gaveUpOnTheLock(rowLock, Dialect.MYSQL, 5, fiftySecondsAgo), rowLock,
			"a wait of innodb_lock_wait_timeout was reported as the bound this backend sets");
	}

	/** While one that ended where this bound is is renamed, which is what the bound exists to say. */
	@Test
	public void testALockWaitTheBoundCouldHaveEndedIsRenamed() {
		final SQLException lockWait = new SQLException("Lock wait timeout exceeded", "40001", 1205);

		final SQLException renamed = storage.gaveUpOnTheLock(lockWait, Dialect.MYSQL, 5, System.nanoTime());

		assertTrue(renamed.getMessage().contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY), renamed.getMessage());
		assertSame(renamed.getCause(), lockWait, "the failure of the engine was not chained");
	}

	/**
	 * A connection left carrying a bound this backend could not take off again does not go back into
	 * the pool. Leaving it to the next borrow to notice does not work: that validation is
	 * {@code isValid()}, a liveness check such a connection passes - and on sql server the setting left
	 * on it would cut every lock wait of the next borrower, row locks included, which
	 * {@code isConflict()} classifies as no replayable conflict.
	 */
	@Test
	public void testAConnectionWhoseBoundCouldNotBeTakenOffIsKeptOutOfThePool() throws Exception {
		final AtomicBoolean keptOut = new AtomicBoolean();
		final Connection parent = refusingToGiveTheValueBack(mock(Connection.class), "31536000");
		try (final CachedConnection con = new CachedConnection("jdbc:mock", parent) {
			@Override
			void keepOutOfThePool() {
				keptOut.set(true);
				super.keepOutOfThePool();
			}
		}) {
			storage.withDdlLockBound(con, Dialect.MYSQL, theDdl());
		}

		assertTrue(keptOut.get(), "a connection left carrying our bound was handed back to the pool");
	}

	/**
	 * The round trips of the bound itself carry a bound of their own. Unbounded, a readback on a
	 * connection whose peer went quiet with the socket still open parks the thread opening a backend
	 * for good - the hang #877 and #882 exist to end - and the restore does it from the finally of a
	 * DDL that has already failed. The DDL between them keeps the class it had, which ships unbounded.
	 */
	@Test
	public void testTheRoundTripsOfTheBoundCarryOneOfTheirOwn() throws Exception {
		final List<Integer> armed = new ArrayList<>();
		final Connection con = recording(mock(Connection.class), "31536000");
		doAnswer(invocation -> {
			armed.add((Integer) invocation.getArguments()[1]);
			return null;
		}).when(con).setNetworkTimeout(any(), anyInt());

		storage.withDdlLockBound(con, Dialect.MYSQL, theDdl());

		assertTrue(armed.contains((JDBCStorage.SESSION_STATEMENT_BOUND_SECONDS
				+ JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000),
			"the session statements of the bound armed no socket read timeout: " + armed);
	}

	/** The DDL itself, recording that it ran in among the session statements issued around it. */
	private JDBCStorage.Execution<Void> theDdl() {
		return () -> {
			issued.add(THE_DDL);
			return null;
		};
	}

	/**
	 * A connection recording every session statement it is given, and answering the readback of the
	 * setting with the value a session of that engine carries.
	 */
	private Connection recording(final Connection con, final String carries) throws SQLException {
		return recording(con, carries, issued);
	}

	/** The same, recording into the list the statements of this connection belong in. */
	private Connection recording(final Connection con, final String carries, final List<String> into)
			throws SQLException {
		final Statement statement = mock(Statement.class);
		when(statement.execute(anyString())).thenAnswer(invocation -> {
			into.add((String) invocation.getArguments()[0]);
			return false;
		});
		when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
			into.add((String) invocation.getArguments()[0]);
			final ResultSet carried = mock(ResultSet.class);
			when(carried.next()).thenReturn(true, false);
			when(carried.getString(1)).thenReturn(carries);
			return carried;
		});
		when(con.createStatement()).thenReturn(statement);
		return con;
	}

	/**
	 * The same, on a connection that takes a setting through and then breaks as the statement that
	 * carried it is closed - all a driver reports of a session that went in between.
	 */
	private Connection breakingOnTheCloseOfASetting(final Connection con, final String carries)
			throws SQLException {
		final Statement statement = recording(con, carries).createStatement();
		final AtomicBoolean carried = new AtomicBoolean();
		doAnswer(invocation -> {
			issued.add((String) invocation.getArguments()[0]);
			carried.set(true);
			return false;
		}).when(statement).execute(anyString());
		doAnswer(invocation -> {
			if (carried.get()) {
				throw new SQLException("the connection went as the statement was closed", "08006");
			}
			return null;
		}).when(statement).close();
		return con;
	}

	/** And one that takes the bound and will not take back the value that bound displaced. */
	private Connection refusingToGiveTheValueBack(final Connection con, final String carries) throws SQLException {
		final Statement statement = recording(con, carries).createStatement();
		final AtomicBoolean bound = new AtomicBoolean();
		doAnswer(invocation -> {
			issued.add((String) invocation.getArguments()[0]);
			if (!bound.compareAndSet(false, true)) {
				throw new SQLException("the connection went before the value could be given back", "08006");
			}
			return false;
		}).when(statement).execute(anyString());
		return con;
	}

	/**
	 * Connections whose class names carry the engine the way the drivers' own do - pgjdbc's
	 * {@code org.postgresql.jdbc.PgConnection}. That name is what {@code dialectOf()} reads the
	 * engine off, and the name of a mock is derived from the type it mocks, so a mock of plain
	 * {@link Connection} reaches no engine branch at all. Lowercase because the match is case
	 * sensitive.
	 */
	interface postgresConnection extends Connection {
	}

	/** The same for mysql, whose setting outlives the transaction and is read back and put back. */
	interface mysqlConnection extends Connection {
	}

	/**
	 * A connection of the given engine, recording the statements it is asked to run - the DDL among the
	 * session settings around it - over a catalog holding the table of every tree named here.
	 */
	private Connection engine(Class<? extends Connection> engine, String carries) throws SQLException {
		return engine(engine, carries, issued);
	}

	/** The same, recording into the list the statements of this connection belong in. */
	private Connection engine(Class<? extends Connection> engine, String carries, List<String> into)
			throws SQLException {
		final Connection con = recording(mock(engine), carries, into);
		when(con.isValid(anyInt())).thenReturn(true);
		when(con.prepareStatement(anyString())).thenAnswer(invocation -> {
			into.add((String) invocation.getArguments()[0]);
			final PreparedStatement statement = mock(PreparedStatement.class);
			when(statement.getConnection()).thenReturn(con);
			return statement;
		});
		final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
		when(metaData.getTables(any(), any(), any(), any())).thenAnswer(invocation -> {
			final ResultSet tables = mock(ResultSet.class);
			final String asked = (String) invocation.getArguments()[2];
			// Every tree of a case is found to exist, and the tree catalog of the backend is found not
			// to be there: what these cases are about is the statements issued around a DDL, and a
			// backend upgraded from a version that kept no catalog takes the shortest way to the funnel
			// carrying them - the catalog would otherwise want a connection of its own, which is not a
			// connection this mock hands out. CatalogConnectionTestCase covers that one.
			when(tables.next()).thenReturn(!CATALOG_TABLE.equals(asked), false);
			// the name the catalog was asked about, so that every tree of a case is found to exist
			when(tables.getString("TABLE_NAME")).thenReturn(asked);
			return tables;
		});
		// The index of a tree is found missing, which is the shortest way through openTree() to the
		// catalog table it creates on the way: a getIndexInfo() no case answers comes back null, which
		// is a NullPointerException inside the lookup rather than a case. The create index that then
		// follows is issued on the caller's connection, where these cases read nothing.
		when(metaData.getIndexInfo(any(), any(), anyString(), anyBoolean(), anyBoolean()))
			.thenAnswer(invocation -> {
				final ResultSet indexes = mock(ResultSet.class);
				when(indexes.next()).thenReturn(false);
				return indexes;
			});
		when(con.getMetaData()).thenReturn(metaData);
		return con;
	}

	/**
	 * A storage handing out the given connection, through the seam an import of
	 * {@code JDBCStatementBoundTestCase} borrows through: what these cases are about is the statements
	 * around a DDL, not the pool that produced the connection carrying them.
	 */
	private JDBCStorage storageHandingOut(final Connection con) {
		return storageHandingOut(con, null);
	}

	/**
	 * The same, handing out the given connection for the tree catalog as well: that connection is not
	 * a pooled one - {@code CatalogSession} opens it through {@code newCatalogConnection()} - so it is
	 * given its own seam rather than borrowed through the one above.
	 */
	private JDBCStorage storageHandingOut(final Connection con, final Connection catalogCon) {
		final JDBCStorage handing = new JDBCStorage(backendCfg(), null) {
			@Override
			Connection getConnection(boolean trusted) {
				return new CachedConnection("jdbc:mock", con);
			}

			@Override
			Connection newCatalogConnection(long budgetDeadline) throws SQLException {
				if (catalogCon == null) {
					throw new SQLException("this case opens no catalog connection");
				}
				return catalogCon;
			}

			@Override
			Connection newStampConnection(Dialect dialect) throws SQLException {
				// The stamp of an open is no part of any case here, and commentTable() takes this for
				// what it is - a stamp connection that could not be made, which leaves the table
				// unstamped and the open unaffected. Answered here rather than left to fail on its own,
				// because failing on its own means DriverManager: this suite needs no database, and a
				// mock-only case has no business registering every jdbc driver on the classpath.
				throw new SQLException("this case stamps nothing");
			}

			@Override
			public StorageStatus getStorageStatus() {
				return StorageStatus.working(); // open already, so an importer borrows and no more
			}
		};
		handing.accessMode = AccessMode.READ_WRITE;
		return handing;
	}
}
