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
import org.opends.server.backends.jdbc.JDBCStorage.ArmedLockBound;
import org.opends.server.backends.jdbc.JDBCStorage.Dialect;
import org.opends.server.backends.jdbc.JDBCStorage.LockBound;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * What a transaction of {@code JDBCStorage.write()} is told to do about a row lock another session
 * holds (#915), and what the replay makes of the failure that ends such a wait.
 * <p>
 * It needs no database: the connection is a mock, so what each engine is told - and what it is told
 * to put back before the connection goes to the next borrower - is pinned wherever the build runs,
 * while the container suites cover a write really queued behind another session's row lock. The
 * replay that this bound exists for is driven end to end in {@code JDBCStorageRetryTest}.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "jdbc" }, sequential = true)
public class JDBCRowLockBoundTestCase extends DirectoryServerTestCase {

	/** A driver name of an engine none of the settings fit, which is what the null dialect stands for. */
	private static final String UNKNOWN_ENGINE = "com.example.jdbc.Connection";
	private static final String MYSQL = "com.mysql.cj.jdbc.ConnectionImpl";

	/** What the connection of a case was asked to run, in the order it was asked to run it. */
	private final List<String> issued = new ArrayList<>();

	private JDBCStorage storage;

	@BeforeMethod
	public void createStorage() {
		storage = new JDBCStorage(backendCfg(), null);
		issued.clear();
	}

	private static JDBCBackendCfg backendCfg() {
		final JDBCBackendCfg cfg = mockCfg(JDBCBackendCfg.class);
		when(cfg.getBackendId()).thenReturn("rowLockBound");
		return cfg;
	}

	@AfterMethod
	public void clearProperties() {
		System.clearProperty(JDBCStorage.ROW_LOCK_TIMEOUT_PROPERTY);
		// the cases arming a DDL bound inside this one raise it: the two are decided against each other
		System.clearProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY);
	}

	/**
	 * The window bounding the replays is a clock, and a clock only bounds them while an attempt is
	 * shorter than it: the default is a third of that window, so a write meeting a lock gets three
	 * attempts inside it rather than spending the whole window on one and being refused a replay
	 * (#903). A default at or past the window would put this back exactly as it was.
	 */
	@Test
	public void testTheDefaultLeavesTheReplayWindowRoomForReplays() {
		assertEquals(JDBCStorage.rowLockBoundSeconds(), 3);
		assertTrue(JDBCStorage.rowLockBoundSeconds() * 1000L * 1000L * 1000L * 3 <= JDBCStorage.RETRY_WINDOW_NANOS,
			"the default bound leaves the replay window room for fewer than three attempts");
	}

	/** Zero is what a deployment that would rather wait for its row lock sets, and so is a negative value. */
	@Test
	public void testTheBoundIsTurnedOffByZero() {
		System.setProperty(JDBCStorage.ROW_LOCK_TIMEOUT_PROPERTY, "0");
		assertEquals(JDBCStorage.rowLockBoundSeconds(), 0);
		System.setProperty(JDBCStorage.ROW_LOCK_TIMEOUT_PROPERTY, "-1");
		assertEquals(JDBCStorage.rowLockBoundSeconds(), 0);
	}

	/** A value that is not a number keeps the default, so a typo cannot silently unbound the wait. */
	@Test
	public void testAValueThatIsNotANumberKeepsTheDefault() {
		System.setProperty(JDBCStorage.ROW_LOCK_TIMEOUT_PROPERTY, "three seconds");
		assertEquals(JDBCStorage.rowLockBoundSeconds(), JDBCStorage.ROW_LOCK_TIMEOUT_SECONDS);
	}

	/** And a value past what a bound of this backend can hold is taken down to it, not read as no bound. */
	@Test
	public void testAValueBeyondTheCeilingIsClamped() {
		System.setProperty(JDBCStorage.ROW_LOCK_TIMEOUT_PROPERTY, String.valueOf(Integer.MAX_VALUE));
		assertEquals(JDBCStorage.rowLockBoundSeconds(), JDBCStorage.MAX_BOUND_SECONDS);
	}

	/**
	 * What each engine is told around the transaction, in the unit its own setting takes: the row lock
	 * of mysql is innodb_lock_wait_timeout and never the lock_wait_timeout of a metadata lock, sql
	 * server has one LOCK_TIMEOUT for every lock wait of a session, postgres takes a set local that the
	 * transaction discards, and oracle has no setting for this wait at all.
	 */
	@DataProvider
	public Object[][] engines() {
		return new Object[][] {
			{ "postgres takes a set local and needs no value back", Dialect.POSTGRES, "0",
				singletonList("set local lock_timeout = 3000"), true },
			{ "mysql bounds the row lock, not the metadata lock", Dialect.MYSQL, "50",
				asList("select @@session.innodb_lock_wait_timeout", "set session innodb_lock_wait_timeout=3",
					"set session innodb_lock_wait_timeout=50"), true },
			{ "sql server replaces the -1 that waits forever", Dialect.MICROSOFT, "-1",
				asList("select @@lock_timeout", "set lock_timeout 3000", "set lock_timeout -1"), true },
			{ "oracle has no session setting for a row lock", Dialect.ORACLE, "0", emptyList(), false },
			{ "an engine this backend does not know is told nothing", null, "0", emptyList(), false },
		};
	}

	@Test(dataProvider = "engines")
	public void testWhatEachEngineIsToldAroundTheTransaction(String name, Dialect dialect, String carries,
			List<String> expected, boolean bounded) throws Exception {
		final Connection con = recording(mock(Connection.class), carries);

		final ArmedLockBound armed = storage.armLockBound(con, dialect, LockBound.ROW);
		storage.releaseLockBound(con, dialect, armed);

		assertEquals(issued, expected, name);
		assertEquals(armed.bounded, bounded, name + ": the wait was reported as bounded when it is not, or the other"
			+ " way round - which is what decides whether the replay may take that wait again");
	}

	/**
	 * A session that gives up sooner than this bound keeps exactly what it has - the bound is never
	 * loosened to ours - and the wait is bounded all the same, which is the whole reason for leaving
	 * that value alone. The replay reads that, not whether a statement of ours was issued.
	 */
	@DataProvider
	public Object[][] sessionsAlreadyTighter() {
		return new Object[][] {
			{ "mysql giving up after a second", Dialect.MYSQL, "1" },
			{ "sql server told not to wait at all", Dialect.MICROSOFT, "0" },
		};
	}

	@Test(dataProvider = "sessionsAlreadyTighter")
	public void testASessionAlreadyTighterKeepsWhatItHasAndIsStillBounded(String name, Dialect dialect,
			String carries) throws Exception {
		final Connection con = recording(mock(Connection.class), carries);

		final ArmedLockBound armed = storage.armLockBound(con, dialect, LockBound.ROW);
		storage.releaseLockBound(con, dialect, armed);

		assertEquals(issued, singletonList(dialect == Dialect.MYSQL
			? "select @@session.innodb_lock_wait_timeout" : "select @@lock_timeout"), name);
		assertTrue(armed.bounded, name + ": a wait the session itself bounds was reported as unbounded");
	}

	/** While a session looser than this bound is given ours, and gets its own value back afterwards. */
	@Test
	public void testASessionLooserThanTheBoundIsGivenOursAndGetsItBack() throws Exception {
		final Connection con = recording(mock(Connection.class), "5000");

		final ArmedLockBound armed = storage.armLockBound(con, Dialect.MICROSOFT, LockBound.ROW);
		storage.releaseLockBound(con, Dialect.MICROSOFT, armed);

		assertEquals(issued, asList("select @@lock_timeout", "set lock_timeout 3000", "set lock_timeout 5000"));
	}

	/** Turned off, nothing is asked of the session and nothing is claimed about the wait. */
	@Test
	public void testTheBoundTurnedOffAsksTheSessionNothing() throws Exception {
		System.setProperty(JDBCStorage.ROW_LOCK_TIMEOUT_PROPERTY, "0");
		final Connection con = recording(mock(Connection.class), "50");

		final ArmedLockBound armed = storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW);
		storage.releaseLockBound(con, Dialect.MYSQL, armed);

		assertEquals(issued, emptyList());
		assertFalse(armed.bounded, "a wait nothing bounds was reported as bounded");
	}

	/**
	 * A session answering the readback with something no SET of it would take back is left alone
	 * altogether: nothing of ours is ever set where it could not be taken off again.
	 */
	@Test
	public void testASessionThatWillNotSayWhatItCarriesIsLeftAlone() throws Exception {
		final Connection con = recording(mock(Connection.class), "unlimited");

		final ArmedLockBound armed = storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW);
		storage.releaseLockBound(con, Dialect.MYSQL, armed);

		assertEquals(issued, singletonList("select @@session.innodb_lock_wait_timeout"));
		assertFalse(armed.bounded, "a session that never took the bound was reported as bounded");
	}

	/**
	 * A setting the session refuses leaves the write running as it ran before this bound existed - the
	 * bound is an improvement on a wait and never a reason to fail a write that would have gone
	 * through - and the wait is not claimed to be bounded, so the replay does not take it again.
	 */
	@Test
	public void testASettingTheSessionRefusesLeavesTheWaitUnbounded() throws Exception {
		final Connection con = recording(mock(Connection.class), "50");
		final Statement statement = con.createStatement();
		doAnswer(invocation -> {
			issued.add((String) invocation.getArguments()[0]);
			throw new SQLException("this session takes no such setting", "42000");
		}).when(statement).execute(anyString());

		final ArmedLockBound armed = storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW);
		storage.releaseLockBound(con, Dialect.MYSQL, armed);

		assertFalse(armed.bounded, "a setting the session refused was reported as bounding the wait");
		// the value is given back all the same: a setting can reach the server and fail only as the
		// statement carrying it is closed, and giving back a value the session may never have left
		// costs a round trip and changes nothing
		assertEquals(issued, asList("select @@session.innodb_lock_wait_timeout",
			"set session innodb_lock_wait_timeout=3", "set session innodb_lock_wait_timeout=50"));
	}

	/**
	 * A connection left carrying a bound this backend could not take off again does not go back into
	 * the pool: on sql server that setting would cut every lock wait of the next borrower, and a read -
	 * which replays nothing, deliberately - would see the error 1222 it ends with.
	 */
	@Test
	public void testAConnectionWhoseBoundCouldNotBeTakenOffIsKeptOutOfThePool() throws Exception {
		final AtomicBoolean keptOut = new AtomicBoolean();
		final Connection parent = refusingToGiveTheValueBack(mock(Connection.class), "-1");
		try (final CachedConnection con = new CachedConnection("jdbc:mock", parent) {
			@Override
			void keepOutOfThePool() {
				keptOut.set(true);
				super.keepOutOfThePool();
			}
		}) {
			storage.releaseLockBound(con, Dialect.MICROSOFT,
				storage.armLockBound(con, Dialect.MICROSOFT, LockBound.ROW));
		}

		assertTrue(keptOut.get(), "a connection left carrying our bound was handed back to the pool");
	}

	/**
	 * Postgres needs a transaction block for a set local to mean anything: outside one the server
	 * answers it with a warning no driver raises, so the write would run unbounded while the log read
	 * exactly like a bounded one.
	 */
	@Test
	public void testPostgresInAutoCommitIsToldNothing() throws Exception {
		final Connection con = recording(mock(Connection.class), "0");
		when(con.getAutoCommit()).thenReturn(true);

		final ArmedLockBound armed = storage.armLockBound(con, Dialect.POSTGRES, LockBound.ROW);

		assertEquals(issued, emptyList());
		assertFalse(armed.bounded, "a set local that reaches no transaction was reported as bounding the wait");
	}

	/**
	 * A failed setting takes the transaction back to the savepoint in front of it: a statement that
	 * fails inside a postgres transaction aborts it, and the write would then fail with 25P02 rather
	 * than running as unbounded as it ran before this bound existed.
	 */
	@Test
	public void testPostgresTakesASavepointInFrontOfTheSetting() throws Exception {
		final Connection con = recording(mock(Connection.class), "0");

		storage.armLockBound(con, Dialect.POSTGRES, LockBound.ROW);

		verify(con).setSavepoint();
	}

	/**
	 * And lets go of it once the setting is on: a savepoint is a subtransaction of the write, and one
	 * left open spans the whole attempt - every row that attempt writes would then carry the
	 * subtransaction's own xid, which readers of those rows resolve through {@code pg_subtrans}. The
	 * {@code set local} survives the release, so the bound it was taken in front of stays on.
	 */
	@Test
	public void testPostgresLetsGoOfThatSavepointOnceTheSettingIsOn() throws Exception {
		final Connection con = recording(mock(Connection.class), "0");
		final Savepoint beforeTheBound = mock(Savepoint.class);
		when(con.setSavepoint()).thenReturn(beforeTheBound);

		storage.armLockBound(con, Dialect.POSTGRES, LockBound.ROW);

		assertEquals(issued, singletonList("set local lock_timeout = 3000"),
			"the bound itself was not issued, or was taken back");
		verify(con).releaseSavepoint(beforeTheBound);
		verify(con, never()).rollback(beforeTheBound);
	}

	/**
	 * A setting that failed is taken back to that point instead, and the point is not let go of in front
	 * of a rollback that still has to reach it.
	 */
	@Test
	public void testASettingThatFailedIsTakenBackToThatSavepoint() throws Exception {
		final Connection con = refusingTheSetting(mock(Connection.class), "0");
		final Savepoint beforeTheBound = mock(Savepoint.class);
		when(con.setSavepoint()).thenReturn(beforeTheBound);

		final ArmedLockBound armed = storage.armLockBound(con, Dialect.POSTGRES, LockBound.ROW);

		assertFalse(armed.bounded, "a setting the session refused was reported as bounding the wait");
		verify(con).rollback(beforeTheBound);
		verify(con, never()).releaseSavepoint(beforeTheBound);
	}

	/**
	 * The readback is a round trip, and this bound is armed around every write of the server where the
	 * DDL bound is armed around an open: it is paid once per pooled connection. Only this backend
	 * writes that setting on a connection of this pool, every write puts the value back, and a
	 * connection whose restore failed is kept out of the pool - so what was read cannot go stale.
	 */
	@Test
	public void testTheReadbackIsPaidOncePerPooledConnection() throws Exception {
		try (final CachedConnection con = new CachedConnection("jdbc:mock",
				recording(mock(Connection.class), "50"))) {
			storage.releaseLockBound(con, Dialect.MYSQL, storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW));
			storage.releaseLockBound(con, Dialect.MYSQL, storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW));
		}

		assertEquals(issued, asList("select @@session.innodb_lock_wait_timeout",
			"set session innodb_lock_wait_timeout=3", "set session innodb_lock_wait_timeout=50",
			"set session innodb_lock_wait_timeout=3", "set session innodb_lock_wait_timeout=50"),
			"the value a pooled session carries was read back more than once");
	}

	/**
	 * A connection that is not one of this pool is asked every time: the memo above is a property of a
	 * session this backend owns for its life, and the catalog and stamp connections are not that.
	 */
	@Test
	public void testAConnectionOutsideThePoolIsAskedEveryTime() throws Exception {
		final Connection con = recording(mock(Connection.class), "50");

		storage.releaseLockBound(con, Dialect.MYSQL, storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW));
		storage.releaseLockBound(con, Dialect.MYSQL, storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW));

		assertEquals(issued.stream().filter("select @@session.innodb_lock_wait_timeout"::equals).count(), 2L,
			"a connection outside the pool was asked for the value it carries only once");
	}

	/**
	 * The failure an engine ends a bounded wait with is replayable, and only where this backend bounded
	 * that wait: 55P03 and error 1222 are no conflict - the engine rolled nothing back and the blocker
	 * is still holding the lock - and what makes them worth replaying is that the wait they cost fits
	 * inside the replay window. Where nothing bounded the wait they stay exactly as unreplayable as
	 * they were: a bound an operator set for themselves is not a licence for this loop to take that
	 * wait again, and a wait nothing bounds is the one thing the window cannot govern (#903).
	 */
	@DataProvider
	public Object[][] lockTimeouts() {
		return new Object[][] {
			{ "postgres lock_timeout", Dialect.POSTGRES, new SQLException("canceling statement due to lock timeout",
				"55P03") },
			{ "sql server LOCK_TIMEOUT", Dialect.MICROSOFT, new SQLException("Lock request time out period exceeded.",
				"HY000", 1222) },
		};
	}

	@Test(dataProvider = "lockTimeouts")
	public void testALockTimeoutIsReplayedOnlyWhereThisBackendBoundedTheWait(String name, Dialect dialect,
			SQLException failure) {
		assertEquals(replayReason(failure, dialect, ArmedLockBound.alreadyTighter(LockBound.ROW, 3)),
			"a lock wait of an attempt this backend bounded", name);
		assertNull(replayReason(failure, dialect, ArmedLockBound.none(LockBound.ROW)),
			name + ": a wait this backend put no bound on was replayed on a clock that cannot bound it");
	}

	/**
	 * A mysql lock wait timeout arrives in class 40, which is the conflict this loop has replayed since
	 * #867: it keeps that reason whether or not this bound is armed, since the line should name the
	 * strongest thing that can be said of the failure.
	 */
	@Test
	public void testAMysqlLockWaitTimeoutStaysTheConflictItWas() {
		final SQLException lockWait = new SQLException("Lock wait timeout exceeded", "40001", 1205);

		assertEquals(replayReason(lockWait, MYSQL, false, false, false, Dialect.MYSQL,
			ArmedLockBound.none(LockBound.ROW)), "a conflict");
		assertEquals(replayReason(lockWait, MYSQL, false, false, false, Dialect.MYSQL,
			ArmedLockBound.alreadyTighter(LockBound.ROW, 3)), "a conflict");
	}

	/**
	 * Not while committing, for the reason a dropped connection is not replayed there: a commit that
	 * did not answer leaves the outcome unknown, and this loop must not apply a write twice.
	 */
	@Test
	public void testALockTimeoutReportedByTheCommitIsNotReplayed() {
		final SQLException lockTimeout = new SQLException("Lock request time out period exceeded.", "HY000", 1222);

		assertNull(replayReason(lockTimeout, UNKNOWN_ENGINE, true, false, false, Dialect.MICROSOFT,
			ArmedLockBound.alreadyTighter(LockBound.ROW, 3)));
	}

	/** And never once the attempt has committed part of its own work, whatever the failure says. */
	@Test
	public void testALockTimeoutOfAnAttemptThatCommittedPartOfItsWorkIsNotReplayed() {
		final SQLException lockTimeout = new SQLException("Lock request time out period exceeded.", "HY000", 1222);

		assertNull(replayReason(lockTimeout, UNKNOWN_ENGINE, false, true, false, Dialect.MICROSOFT,
			ArmedLockBound.alreadyTighter(LockBound.ROW, 3)));
	}

	/**
	 * Read from the failure of the operation only, never from the release of the connection: the
	 * rollback that gives a connection back runs after the outcome was decided, so a lock timeout
	 * reported there says nothing about the statement that failed.
	 */
	@Test
	public void testALockTimeoutOfTheReleaseIsNotReplayed() {
		final SQLException rejected = new SQLException("duplicate key", "23000", 2627);
		rejected.addSuppressed(new SQLException("Lock request time out period exceeded.", "HY000", 1222));

		assertNull(replayReason(rejected, Dialect.MICROSOFT, ArmedLockBound.alreadyTighter(LockBound.ROW, 3)));
	}

	/**
	 * sql server has one {@code LOCK_TIMEOUT} for both waits, and every DDL of this backend but the
	 * off-write catalog drop is issued from inside a write - which armed this bound one statement
	 * earlier. Read live, that DDL would see the 3 s of the row bound, answer "already tighter" to its
	 * own 5 s and run at the row bound instead: {@code DDL_LOCK_TIMEOUT_PROPERTY} would govern no DDL of
	 * a write at all. What decides it is the value the deployment set, which the row bound remembered on
	 * the connection when it displaced it.
	 * <p>
	 * The readback itself is still paid, and paid live: what the DDL has to put back is the value the
	 * session carried a statement ago - the row bound of this very write - rather than the one the
	 * connection was borrowed with, which is why only the row bound is remembered per pooled connection.
	 */
	@Test
	public void testTheDdlBoundInsideAWriteIsDecidedAgainstWhatTheDeploymentSet() throws Exception {
		try (final CachedConnection con = new CachedConnection("jdbc:mock",
				liveLockTimeout(mock(Connection.class), "-1"))) {
			final ArmedLockBound row = storage.armLockBound(con, Dialect.MICROSOFT, LockBound.ROW);
			storage.withDdlLockBound(con, Dialect.MICROSOFT, () -> {
				issued.add("the ddl");
				return null;
			});
			storage.releaseLockBound(con, Dialect.MICROSOFT, row);
		}

		assertEquals(issued, asList(
			// the row bound of the write, against what the session carried
			"select @@lock_timeout", "set lock_timeout 3000",
			// the DDL inside it, read live and armed at its own property rather than left at the row bound
			"select @@lock_timeout", "set lock_timeout 5000",
			"the ddl",
			// what the session carried a statement before the DDL, which is the row bound of this write
			"set lock_timeout 3000",
			// and the value the deployment set, once the write is through
			"set lock_timeout -1"));
	}

	/**
	 * And a DDL that gives up at that bound is reported as what it is, naming its own property - the
	 * rename of #885, which a DDL left at the row bound would lose along with the bound: it would arrive
	 * as the bare error 1222, sending an operator to neither property.
	 */
	@Test
	public void testADdlInsideAWriteThatGivesUpNamesItsOwnProperty() throws Exception {
		try (final CachedConnection con = new CachedConnection("jdbc:mock",
				liveLockTimeout(mock(Connection.class), "-1"))) {
			storage.armLockBound(con, Dialect.MICROSOFT, LockBound.ROW);

			try {
				storage.withDdlLockBound(con, Dialect.MICROSOFT, () -> {
					throw new SQLException("Lock request time out period exceeded.", "HY000", 1222);
				});
				fail("a DDL that gave up on its lock went through");
			} catch (SQLException e) {
				assertTrue(e.getMessage().contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY), e.getMessage());
			}
		}
	}

	/**
	 * And that value is put on the session, not merely decided against. A deployment giving up sooner
	 * than the DDL bound keeps its own figure - the argument that leaves oracle alone - but "keeps what
	 * it has" is not what the session has once the row bound of this write is on that very setting: at a
	 * {@code LOCK_TIMEOUT} of 4 s the DDL would run at the 3 s of the row bound, tighter than either
	 * property, and give up as the bare error 1222. What the deployment set goes on for the length of
	 * the DDL, and the row bound of the write goes back on behind it.
	 */
	@Test
	public void testADdlInsideAWriteRunsAtWhatTheDeploymentSetWhereThatIsTighter() throws Exception {
		try (final CachedConnection con = new CachedConnection("jdbc:mock",
				liveLockTimeout(mock(Connection.class), "4000"))) {
			final ArmedLockBound row = storage.armLockBound(con, Dialect.MICROSOFT, LockBound.ROW);
			storage.withDdlLockBound(con, Dialect.MICROSOFT, () -> {
				issued.add("the ddl");
				return null;
			});
			storage.releaseLockBound(con, Dialect.MICROSOFT, row);
		}

		assertEquals(issued, asList(
			// the row bound of the write, over a deployment looser than it
			"select @@lock_timeout", "set lock_timeout 3000",
			// the DDL inside it, at the value the deployment set rather than at the row bound it met
			"select @@lock_timeout", "set lock_timeout 4000",
			"the ddl",
			// the row bound of the write back, and the deployment's value once the write is through
			"set lock_timeout 3000", "set lock_timeout 4000"));
	}

	/**
	 * The same where an operator raised the DDL bound for an index build and the deployment bounds every
	 * lock wait of its sessions: 30 s asked for, 10 s allowed, and the row bound of the write is neither.
	 */
	@Test
	public void testTheSameWhereTheDdlBoundWasRaisedForAnIndexBuild() throws Exception {
		System.setProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY, "30");
		try (final CachedConnection con = new CachedConnection("jdbc:mock",
				liveLockTimeout(mock(Connection.class), "10000"))) {
			storage.armLockBound(con, Dialect.MICROSOFT, LockBound.ROW);
			storage.withDdlLockBound(con, Dialect.MICROSOFT, () -> {
				issued.add("the ddl");
				return null;
			});
		}

		assertEquals(issued, asList(
			"select @@lock_timeout", "set lock_timeout 3000",
			"select @@lock_timeout", "set lock_timeout 10000",
			"the ddl",
			"set lock_timeout 3000"));
	}

	/**
	 * And a DDL that gives up under that value is left exactly as it arrived: what ended the wait is the
	 * deployment's own {@code LOCK_TIMEOUT}, and naming this property for it would send an operator to
	 * raise a value that governs nothing while the deployment's own is the tighter one.
	 */
	@Test
	public void testADdlThatGaveUpAtTheDeploymentsValueIsNotNamedByThisProperty() throws Exception {
		try (final CachedConnection con = new CachedConnection("jdbc:mock",
				liveLockTimeout(mock(Connection.class), "4000"))) {
			storage.armLockBound(con, Dialect.MICROSOFT, LockBound.ROW);

			try {
				storage.withDdlLockBound(con, Dialect.MICROSOFT, () -> {
					throw new SQLException("Lock request time out period exceeded.", "HY000", 1222);
				});
				fail("a DDL that gave up on its lock went through");
			} catch (SQLException e) {
				assertFalse(e.getMessage().contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY),
					"a wait the deployment's own value ended was reported as this property's doing: " + e.getMessage());
				assertEquals(e.getErrorCode(), 1222, "the failure of the engine was not handed through as it arrived");
			}
		}
	}

	/**
	 * And the same of a lookup, which is the other kind of work this bound is armed around: the one
	 * deciding each drop of a clear wraps whatever it sees in a {@code StorageRuntimeException}
	 * ({@code isExistsTable}), so it reaches the rename by the unchecked arm rather than the checked
	 * one. Both arms read the same thing - whether the session is carrying this bound's own figure -
	 * and keying either of them on "a setting of ours was issued" instead would name this property for
	 * a wait the deployment's own value ended.
	 */
	@Test
	public void testALookupThatGaveUpAtTheDeploymentsValueIsNotNamedByThisPropertyEither() throws Exception {
		try (final CachedConnection con = new CachedConnection("jdbc:mock",
				liveLockTimeout(mock(Connection.class), "4000"))) {
			storage.armLockBound(con, Dialect.MICROSOFT, LockBound.ROW);

			try {
				storage.withDdlLockBound(con, Dialect.MICROSOFT, () -> {
					throw new StorageRuntimeException(
						new SQLException("Lock request time out period exceeded.", "HY000", 1222));
				});
				fail("a lookup that gave up on its lock went through");
			} catch (StorageRuntimeException e) {
				assertFalse(String.valueOf(e.getMessage()).contains(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY),
					"a wait the deployment's own value ended was reported as this property's doing: " + e.getMessage());
			}
		}
	}

	/**
	 * On mysql the two waits are two variables - {@code innodb_lock_wait_timeout} for the row lock,
	 * {@code lock_wait_timeout} for the metadata lock a DDL waits for - so what the row bound displaced
	 * describes neither the other's session nor its default. Reading it there would leave a DDL waiting
	 * a year because a deployment had tightened the row lock to a second.
	 */
	@Test
	public void testTheDdlBoundOfAMysqlWriteIsDecidedAgainstItsOwnVariable() throws Exception {
		try (final CachedConnection con = new CachedConnection("jdbc:mock",
				answering(mock(Connection.class),
					"select @@session.innodb_lock_wait_timeout", "1",
					"select @@session.lock_wait_timeout", "31536000"))) {
			storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW);
			storage.withDdlLockBound(con, Dialect.MYSQL, () -> {
				issued.add("the ddl");
				return null;
			});
		}

		assertEquals(issued, asList(
			// the row bound: this session gives up sooner than ours would, so it keeps what it has
			"select @@session.innodb_lock_wait_timeout",
			// and the metadata lock is bounded all the same, against the variable that bounds it
			"select @@session.lock_wait_timeout", "set session lock_wait_timeout=5",
			"the ddl",
			"set session lock_wait_timeout=31536000"));
	}

	/**
	 * The latches these bounds warn through are per bound, not per storage: they are armed by different
	 * code on different paths, and an open whose DDL bound this session would not take says nothing
	 * about the writes behind it. Through one latch, the first open of a backend would silence every
	 * write of it - which is what these two assertions, taken together, keep from happening.
	 */
	@Test
	public void testTheWarningLatchesAreOnePerBound() throws Exception {
		final Connection con = refusingTheSetting(mock(Connection.class), "31536000");

		storage.armLockBound(con, Dialect.MYSQL, LockBound.DDL);

		assertTrue(storage.lockBoundNotSetWarned.get(LockBound.DDL).get(),
			"a setting the session refused was not reported for the bound that was armed");
		assertFalse(storage.lockBoundNotSetWarned.get(LockBound.ROW).get(),
			"the DDL bound of an open silenced the row lock bound of every write behind it");

		storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW);

		assertTrue(storage.lockBoundNotSetWarned.get(LockBound.ROW).get(),
			"the row lock bound of a write said nothing of its own");
	}

	/**
	 * The same, for the moment throttling what a bound left behind says - and asked of the row bound,
	 * which is the one a single shared moment would leave unsaid: what the two of them have to be is
	 * one per bound, in both directions.
	 */
	@Test
	public void testTheLatchOfABoundLeftBehindIsOnePerBoundToo() throws Exception {
		final Connection con = refusingToGiveTheValueBack(mock(Connection.class), "31536000");

		storage.releaseLockBound(con, Dialect.MYSQL, storage.armLockBound(con, Dialect.MYSQL, LockBound.ROW));

		assertTrue(storage.lockBoundLeftBehindWarned.get(LockBound.ROW).get() != 0,
			"a bound that could not be taken off was not reported for the bound that was armed");
		assertEquals(storage.lockBoundLeftBehindWarned.get(LockBound.DDL).get(), 0L,
			"the row lock bound of a write silenced the DDL bound of every open of this backend");
	}

	private static String replayReason(SQLException failure, Dialect dialect, ArmedLockBound rowLock) {
		return replayReason(failure, UNKNOWN_ENGINE, false, false, false, dialect, rowLock);
	}

	/**
	 * The two questions {@code write()} asks after a failed attempt, composed here the way it composes
	 * them: the conflict class is read off the failure once and handed to the reason, rather than being
	 * asked for again.
	 */
	private static String replayReason(SQLException failure, String driver, boolean committing,
			boolean partlyCommitted, boolean connectionClosed, Dialect dialect, ArmedLockBound rowLock) {
		return JDBCStorage.replayReason(JDBCStorage.conflictVerdict(failure, driver).conflict, failure, committing,
			partlyCommitted, connectionClosed, dialect, rowLock);
	}

	/**
	 * A connection recording every session statement it is given, and answering the readback of the
	 * setting with the value a session of that engine carries.
	 */
	private Connection recording(final Connection con, final String carries) throws SQLException {
		final Statement statement = mock(Statement.class);
		when(statement.execute(anyString())).thenAnswer(invocation -> {
			issued.add((String) invocation.getArguments()[0]);
			return false;
		});
		when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
			issued.add((String) invocation.getArguments()[0]);
			final ResultSet carried = mock(ResultSet.class);
			when(carried.next()).thenReturn(true, false);
			when(carried.getString(1)).thenReturn(carries);
			return carried;
		});
		when(con.createStatement()).thenReturn(statement);
		return con;
	}

	/**
	 * A connection answering each readback its own value, which is what an engine whose two waits are
	 * two variables does: every other fixture here answers one value to every query, so a case over one
	 * of them cannot tell the value of one setting from the value of the other.
	 *
	 * @param answers the query and the value it is answered with, in pairs
	 */
	private Connection answering(final Connection con, final String... answers) throws SQLException {
		final Statement statement = mock(Statement.class);
		when(statement.execute(anyString())).thenAnswer(invocation -> {
			issued.add((String) invocation.getArguments()[0]);
			return false;
		});
		when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
			final String query = (String) invocation.getArguments()[0];
			issued.add(query);
			final ResultSet carried = mock(ResultSet.class);
			when(carried.next()).thenReturn(true, false);
			for (int i = 0; i < answers.length; i += 2) {
				if (answers[i].equals(query)) {
					when(carried.getString(1)).thenReturn(answers[i + 1]);
					return carried;
				}
			}
			throw new SQLException("this fixture answers no " + query, "42000");
		});
		when(con.createStatement()).thenReturn(statement);
		return con;
	}

	/**
	 * A sql server connection answering the readback with what the last {@code set lock_timeout} left on
	 * it - a live session rather than a fixed value, which is what a bound armed inside another one
	 * meets.
	 */
	private Connection liveLockTimeout(final Connection con, final String initially) throws SQLException {
		final AtomicReference<String> carried = new AtomicReference<>(initially);
		final Statement statement = mock(Statement.class);
		when(statement.execute(anyString())).thenAnswer(invocation -> {
			final String sql = (String) invocation.getArguments()[0];
			issued.add(sql);
			if (sql.startsWith("set lock_timeout ")) {
				carried.set(sql.substring("set lock_timeout ".length()));
			}
			return false;
		});
		when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
			issued.add((String) invocation.getArguments()[0]);
			final ResultSet rows = mock(ResultSet.class);
			when(rows.next()).thenReturn(true, false);
			when(rows.getString(1)).thenReturn(carried.get());
			return rows;
		});
		when(con.createStatement()).thenReturn(statement);
		return con;
	}

	/** A connection that says what it carries and will not take the setting of a bound at all. */
	private Connection refusingTheSetting(final Connection con, final String carries) throws SQLException {
		final Statement statement = recording(con, carries).createStatement();
		doAnswer(invocation -> {
			issued.add((String) invocation.getArguments()[0]);
			throw new SQLException("this session takes no such setting", "42000");
		}).when(statement).execute(anyString());
		return con;
	}

	/** A connection that takes the bound and will not take back the value that bound displaced. */
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
}
