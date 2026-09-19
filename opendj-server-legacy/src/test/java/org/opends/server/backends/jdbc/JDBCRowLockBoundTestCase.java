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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

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
			"a wait for a row lock this backend bounded", name);
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
