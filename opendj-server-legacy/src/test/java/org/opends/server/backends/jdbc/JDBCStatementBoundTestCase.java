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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.jdbc.JDBCStorage.StatementBound;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.ReadOnlyStorageException;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.singletonList;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Which bound a statement of the JDBC backend is given, and what reaching it looks like to the
 * caller (#877). Needs no database: the statement is a mock, so the policy is pinned wherever the
 * build runs, while the container suites cover a statement really blocked on a lock.
 */
@SuppressWarnings("javadoc")
// Every wait of this suite is bounded where it is taken - awaitOrFail() and Background.joinOrFail()
// - rather than by a timeOut on this annotation: a method carrying a @Test of its own replaces the
// one of the class outright, only the groups of the two being merged, so a timeOut declared here
// would bound nothing. A statement of another thread that never arrives fails this suite there.
@Test(groups = { "precommit", "jdbc" }, sequential = true)
public class JDBCStatementBoundTestCase extends DirectoryServerTestCase {

	private JDBCStorage storage;

	/**
	 * A storage of its own for every test. Not merely tidy: a storage carries latches that are
	 * meant to be one-shot for its whole life - the driver having no network timeout, and the
	 * warnings for that and for a refused query timeout - so a test that trips one on a shared
	 * instance would leave {@code applyBackstop()} returning at its first guard for every test
	 * after it, turning their {@code verify(con, never())} assertions green on a run that reached
	 * nothing. Constructing one costs a mock configuration and no connection at all.
	 */
	@BeforeMethod
	public void createStorage() {
		storage = new JDBCStorage(mockCfg(JDBCBackendCfg.class), null);
	}

	@AfterMethod
	public void clearProperties() {
		for (final StatementBound bound : StatementBound.values()) {
			System.clearProperty(bound.property);
		}
		System.clearProperty(JDBCStorage.STATISTICS_TIMEOUT_PROPERTY);
		storage.accessMode = AccessMode.READ_ONLY; // an import test opens it for writing
	}

	/** How long a test waits for a statement running on another thread before it fails. */
	private static final long WAIT_MILLIS = 30000;

	/**
	 * A storage whose clock the test moves by hand. Whether a failure is the bound arriving turns
	 * on a few milliseconds either side of it, and a sleep cannot pin that: a loaded box lengthens
	 * one, so a test that oversleeps passes whether the slack under the bound exists or not - and
	 * a bound of a second costs the suite a second of waiting to say so.
	 */
	private static final class SteppedClockStorage extends JDBCStorage {
		/** Milliseconds since the statement under test started, as the classification will see it. */
		volatile long millis;

		SteppedClockStorage() {
			super(mockCfg(JDBCBackendCfg.class), null);
		}

		@Override
		long nanoTime() {
			return millis * 1000000L;
		}
	}

	private static void awaitOrFail(CountDownLatch latch, String what) throws InterruptedException {
		assertTrue(latch.await(WAIT_MILLIS, TimeUnit.MILLISECONDS), what + " within " + WAIT_MILLIS + " ms");
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** A statement that reports it is running and then waits for the test to let it finish. */
	private PreparedStatement lingering(Connection con, CountDownLatch running, CountDownLatch mayFinish)
			throws SQLException {
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);
		when(statement.executeUpdate()).thenAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				running.countDown();
				awaitOrFail(mayFinish, "the statement was never let go");
				return 1;
			}
		});
		return statement;
	}

	private interface Execution {
		void run() throws Exception;
	}

	/**
	 * A statement running on a thread of its own, with whatever it threw kept for the assertion:
	 * {@code Thread.join()} does not rethrow, so a failure in the background would otherwise leave
	 * the verifications of a test passing on a run that never reached the state they check.
	 */
	private static final class Background {
		final Thread thread;
		final AtomicReference<Throwable> failure = new AtomicReference<>();

		Background(String name, final Execution execution) {
			thread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						execution.run();
					} catch (Throwable t) {
						failure.set(t);
					}
				}
			}, name);
		}

		void joinOrFail() throws Exception {
			thread.join(WAIT_MILLIS);
			assertFalse(thread.isAlive(), thread.getName() + " did not finish within " + WAIT_MILLIS + " ms");
			final Throwable thrown = failure.get();
			if (thrown instanceof Exception) {
				throw (Exception) thrown;
			}
			if (thrown != null) {
				throw new AssertionError(thrown);
			}
		}
	}

	private static Background start(String name, Execution execution) {
		final Background background = new Background(name, execution);
		background.thread.start();
		return background;
	}

	/**
	 * An entry read that has not come back in two minutes is stuck, while a count or the delete
	 * that empties a tree before an import legitimately takes longer than anything can guess - so
	 * the bulk class stays unbounded until a deployment says otherwise.
	 */
	@Test
	public void testDefaultsBoundAnOperationAndLeaveBulkAlone() throws Exception {
		assertEquals(StatementBound.OPERATION.seconds(), 120);
		assertEquals(StatementBound.BULK.seconds(), 0);
	}

	@Test
	public void testEachClassIsConfiguredByItsOwnProperty() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		assertEquals(StatementBound.OPERATION.seconds(), 7);
		assertEquals(StatementBound.BULK.seconds(), 0, "the bulk class followed the operation one");

		System.setProperty(StatementBound.BULK.property, "900");
		assertEquals(StatementBound.BULK.seconds(), 900);
		assertEquals(StatementBound.OPERATION.seconds(), 7);
	}

	@Test
	public void testAValueThatIsNoBoundLeavesTheStatementUnbounded() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "0");
		assertEquals(StatementBound.OPERATION.seconds(), 0);

		System.setProperty(StatementBound.OPERATION.property, "-1");
		assertEquals(StatementBound.OPERATION.seconds(), 0);
	}

	/**
	 * A value that is not a number is not a way to switch the bound off: it is ignored in favour
	 * of the default, as {@code Integer.getInteger()} has it, so a typo leaves the class bounded
	 * rather than silently unbounding it.
	 */
	@Test
	public void testAValueThatIsNotANumberFallsBackToTheDefault() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "two minutes");
		assertEquals(StatementBound.OPERATION.seconds(), 120);

		// from a bound that took, so that the fallback is what the assertion below can be seeing:
		// the default of this class is 0, which is also what a value read as a number would give
		System.setProperty(StatementBound.BULK.property, "900");
		assertEquals(StatementBound.BULK.seconds(), 900);
		System.setProperty(StatementBound.BULK.property, "as long as it takes");
		assertEquals(StatementBound.BULK.seconds(), 0);
	}

	/**
	 * A bound larger than the second layer can hold is taken down to what it can hold, and stays a
	 * bound: that layer is a socket read timeout, which is milliseconds of an {@code int}, so
	 * {@code Integer.MAX_VALUE} - the usual "no bound" idiom - has no value of it to be given, and
	 * a negative one is a call every driver refuses, leaving the connection carrying whatever the
	 * statement before it armed. {@code 0} is what says "no bound" here, and the ceiling is not it.
	 */
	@Test
	public void testABoundLargerThanTheBackstopCanHoldIsTakenDownToIt() throws Exception {
		assertEquals(JDBCStorage.clampSeconds(Integer.MAX_VALUE), JDBCStorage.MAX_BOUND_SECONDS);
		System.setProperty(StatementBound.OPERATION.property, String.valueOf(Integer.MAX_VALUE));
		assertEquals(StatementBound.OPERATION.seconds(), JDBCStorage.MAX_BOUND_SECONDS);

		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);

		storage.execute(statement);

		verify(statement).setQueryTimeout(JDBCStorage.MAX_BOUND_SECONDS);
		// and what the second layer is armed with is still a positive int of milliseconds, which is
		// the whole of what the ceiling is for: a negative one is the call a driver refuses
		verify(con).setNetworkTimeout(any(Executor.class),
			eq((JDBCStorage.MAX_BOUND_SECONDS + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
	}

	@Test
	public void testTheBoundReachesTheStatement() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final PreparedStatement statement = mock(PreparedStatement.class);

		storage.execute(statement);

		verify(statement).setQueryTimeout(7);
	}

	/** An unbounded class costs no call of its own: a fresh statement is unbounded already. */
	@Test
	public void testAnUnboundedClassSetsNothing() throws Exception {
		final PreparedStatement statement = mock(PreparedStatement.class);

		storage.execute(statement, StatementBound.BULK);

		verify(statement, never()).setQueryTimeout(anyInt());
	}

	/**
	 * Behind the cancel is a socket read timeout, for the databases that do not act on a cancel:
	 * it is armed for the statement and put back once nothing is running on the connection any
	 * more. What a connection carrying several statements at once does with it is pinned by the
	 * three tests below.
	 */
	@Test
	public void testTheBackstopIsArmedAndPutBack() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0); // no bound of its own
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);

		storage.execute(statement);

		final InOrder inOrder = inOrder(con);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/**
	 * The backstop only ever tightens. A read timeout a deployment gave its connections is the
	 * bound it asked for, and this one - deliberately the looser of the two, so that the cancel
	 * has room to arrive first - must not stand in for it while a statement runs.
	 */
	@Test
	public void testTheBackstopDoesNotLoosenATighterBound() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(5000); // tighter than 7s plus the margin
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);

		storage.execute(statement);

		verify(con, never()).setNetworkTimeout(any(Executor.class), anyInt());
	}

	/**
	 * A statement of a class that carries no bound takes the backstop off the connection for as
	 * long as it runs. The socket read timeout is a property of the connection, and an importer
	 * writes to a single one from every phase-one worker and every phase-two task, so the bulk
	 * {@code delete from} that empties a tree would otherwise be cut at the bound of an entry read
	 * happening to run beside it - and cut without ever naming a property, since a statement of an
	 * unbounded class has none to name. Two threads, because that is how the two meet.
	 */
	@Test
	public void testAnUnboundedStatementTakesTheBackstopOffWhileItRuns() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		System.setProperty(StatementBound.BULK.property, "0");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final CountDownLatch operationRunning = new CountDownLatch(1);
		final CountDownLatch operationMayFinish = new CountDownLatch(1);
		final CountDownLatch bulkRunning = new CountDownLatch(1);
		final CountDownLatch bulkMayFinish = new CountDownLatch(1);
		final PreparedStatement operation = lingering(con, operationRunning, operationMayFinish);
		final PreparedStatement bulk = lingering(con, bulkRunning, bulkMayFinish);

		final Background entryRead = start("entry-read", () -> storage.execute(operation));
		awaitOrFail(operationRunning, "the entry read never started");
		final Background clearTree = start("clear-tree", () -> storage.execute(bulk, StatementBound.BULK));
		awaitOrFail(bulkRunning, "the bulk statement never started");
		bulkMayFinish.countDown();
		clearTree.joinOrFail();
		operationMayFinish.countDown();
		entryRead.joinOrFail();

		final InOrder inOrder = inOrder(con);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0)); // the bulk statement takes it off
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0)); // and the entry read is through
	}

	/**
	 * The backstop belongs to the connection, not to the statement that armed it: the first
	 * statement to finish must not take it away from the statements still running there.
	 */
	@Test
	public void testTheBackstopOutlastsTheStatementThatArmedIt() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final CountDownLatch running = new CountDownLatch(1);
		final CountDownLatch mayFinish = new CountDownLatch(1);
		final PreparedStatement lingering = lingering(con, running, mayFinish);
		final PreparedStatement passing = mock(PreparedStatement.class);
		when(passing.getConnection()).thenReturn(con);
		when(passing.executeUpdate()).thenReturn(1);

		final Background outliving = start("outliving", () -> storage.execute(lingering));
		awaitOrFail(running, "the statement that arms the backstop never started");
		storage.execute(passing); // joins that connection and is through while the other one runs

		verify(con, never()).setNetworkTimeout(any(Executor.class), eq(0));
		mayFinish.countDown();
		outliving.joinOrFail();
		final InOrder inOrder = inOrder(con);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/**
	 * With bounds of two classes in flight on one connection, the value armed is the loosest of
	 * them: a socket read timeout is shared by everything running on the connection, so tightening
	 * it to the bound of an entry read would cut the bulk statement beside it long before the bound
	 * that statement was actually given.
	 */
	@Test
	public void testTheBackstopFollowsTheLoosestBoundInFlight() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		System.setProperty(StatementBound.BULK.property, "100");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final CountDownLatch bulkRunning = new CountDownLatch(1);
		final CountDownLatch bulkMayFinish = new CountDownLatch(1);
		final PreparedStatement bulk = lingering(con, bulkRunning, bulkMayFinish);
		final PreparedStatement operation = mock(PreparedStatement.class);
		when(operation.getConnection()).thenReturn(con);
		when(operation.executeUpdate()).thenReturn(1);

		final Background count = start("count", () -> storage.execute(bulk, StatementBound.BULK));
		awaitOrFail(bulkRunning, "the bulk statement never started");
		storage.execute(operation); // an entry read of another thread, with a tighter bound
		bulkMayFinish.countDown();
		count.joinOrFail();

		final InOrder inOrder = inOrder(con);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((100 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
		verify(con, never()).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));

	}

	/**
	 * The other order, which is the one that moves the backstop while a statement is running: a
	 * bound looser than what is armed re-arms the connection to its own value, and the tighter
	 * statement left behind gets its bound back the moment the looser one is through.
	 */
	@Test
	public void testALooserBoundRearmsTheBackstopAndTheTighterOneGetsItBack() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		System.setProperty(StatementBound.BULK.property, "100");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final CountDownLatch operationRunning = new CountDownLatch(1);
		final CountDownLatch operationMayFinish = new CountDownLatch(1);
		final CountDownLatch bulkRunning = new CountDownLatch(1);
		final CountDownLatch bulkMayFinish = new CountDownLatch(1);
		final PreparedStatement operation = lingering(con, operationRunning, operationMayFinish);
		final PreparedStatement bulk = lingering(con, bulkRunning, bulkMayFinish);

		final Background entryRead = start("entry-read", () -> storage.execute(operation));
		awaitOrFail(operationRunning, "the entry read never started");
		final Background count = start("count", () -> storage.execute(bulk, StatementBound.BULK));
		awaitOrFail(bulkRunning, "the bulk statement never started");
		bulkMayFinish.countDown();
		count.joinOrFail();
		operationMayFinish.countDown();
		entryRead.joinOrFail();

		final InOrder inOrder = inOrder(con);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((100 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/**
	 * A failure that arrives before the bound is the caller's to classify and must reach it as it
	 * stands: a lock wait reported in class 40 is the conflict {@code JDBCStorage.write()} replays,
	 * and wrapping it would take it out of that class.
	 */
	@Test
	public void testAFailureInsideTheBoundIsPassedThrough() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "60");
		final SQLException conflict = new SQLException("lock wait timeout exceeded", "40001", 1205);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeUpdate()).thenThrow(conflict);

		try {
			storage.execute(statement);
			fail("the failure of the statement must reach the caller");
		} catch (SQLException e) {
			assertSame(e, conflict);
		}
	}

	/**
	 * A failure that arrives at the bound names the property that produced it - every driver
	 * reports a cancelled statement differently, and none of them knows why it was cancelled - and
	 * still carries the SQL state and the error number of the failure it replaces.
	 */
	@Test
	public void testAFailureAtTheBoundNamesTheProperty() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeUpdate()).thenAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Thread.sleep(1100); // the driver cancelled it at the bound this test set
				throw new SQLException("canceling statement due to user request", "57014", 0);
			}
		});

		try {
			storage.execute(statement);
			fail("the failure of the statement must reach the caller");
		} catch (SQLTimeoutException e) {
			assertEquals(e.getSQLState(), "57014");
			assertTrue(e.getMessage().contains(StatementBound.OPERATION.property), e.getMessage());
			assertEquals(((SQLException) e.getCause()).getSQLState(), "57014");
		}
	}

	/**
	 * A driver reporting the cancel a few milliseconds before the bound is arithmetically due -
	 * its timer is kept in whole seconds, while this classification is measured to the millisecond
	 * - is still the bound arriving, and the failure has to name the property all the same.
	 * Without the slack under it, the one failure this classification exists to name reached the
	 * caller as a bare 57014 or ORA-01013, which no caller can tell from a cancel of an operator.
	 */
	@Test
	public void testAFailureJustUnderTheBoundStillNamesTheProperty() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final SteppedClockStorage clocked = new SteppedClockStorage();
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeUpdate()).thenAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				// a literal, deliberately not derived from CLOCK_SLACK_MILLIS: a test computing its
				// own input from the constant it pins follows that constant to zero and pins nothing
				clocked.millis = 875; // 125 ms under the bound, inside a slack of 250
				throw new SQLException("canceling statement due to user request", "57014", 0);
			}
		});

		try {
			clocked.execute(statement);
			fail("the failure of the statement must reach the caller");
		} catch (SQLTimeoutException e) {
			assertTrue(e.getMessage().contains(StatementBound.OPERATION.property), e.getMessage());
			// and the time it names is the one measured, so that this cannot pass on a clock that
			// simply ran past the bound while the assertion above looked only at the message
			assertTrue(e.getMessage().contains("875 ms"), e.getMessage());
		}
	}

	/**
	 * The other side of that slack, which is what keeps it a slack rather than a second bound: a
	 * failure further under the bound than a driver's whole-second timer could account for is a
	 * failure of its own, and the caller has to see it as one.
	 */
	@Test
	public void testAFailureFurtherUnderTheBoundIsStillPassedThrough() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final SteppedClockStorage clocked = new SteppedClockStorage();
		final SQLException conflict = new SQLException("lock wait timeout exceeded", "40001", 1205);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeUpdate()).thenAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				clocked.millis = 749; // a millisecond further out than the slack of 250 reaches
				throw conflict;
			}
		});

		try {
			clocked.execute(statement);
			fail("the failure of the statement must reach the caller");
		} catch (SQLException e) {
			assertSame(e, conflict);
		}
	}

	/**
	 * And the edge of that slack belongs to the bound. The two cases above sit either side of it,
	 * so both of them pass whether the comparison is {@code <} or {@code <=} - the point where the
	 * two differ is exactly a slack under the bound, and that is what this pins.
	 */
	@Test
	public void testAFailureExactlyASlackUnderTheBoundIsStillTheBound() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final SteppedClockStorage clocked = new SteppedClockStorage();
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeUpdate()).thenAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				// a literal for the reason the case above gives: derived from CLOCK_SLACK_MILLIS it
				// would follow that constant wherever it went and pin nothing
				clocked.millis = 750; // the bound of a second, less a slack of 250
				throw new SQLException("canceling statement due to user request", "57014", 0);
			}
		});

		try {
			clocked.execute(statement);
			fail("the failure of the statement must reach the caller");
		} catch (SQLTimeoutException e) {
			assertTrue(e.getMessage().contains(StatementBound.OPERATION.property), e.getMessage());
			assertTrue(e.getMessage().contains("750 ms"), e.getMessage());
		}
	}

	/**
	 * A statement that neither layer bounded reaches its caller exactly as it is. Asking for the
	 * second layer is not having it: a driver may have no network timeout at all, a connection may
	 * refuse the call, one may already carry a timeout of a deployment's own, and a statement of an
	 * unbounded class running beside this one takes the backstop off outright. A catalog lookup
	 * takes no query timeout by construction, so with the backstop unarmed nothing ends its wait -
	 * and the failure that finally does is the driver's own. Reported as the bound, it sent an
	 * operator to raise a property that had bounded nothing about the wait they had just watched.
	 */
	@Test
	public void testAFailureOfAStatementNeitherLayerBoundedIsPassedThrough() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final SteppedClockStorage clocked = new SteppedClockStorage(); // its own, for its own latch
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		doThrow(new SQLFeatureNotSupportedException("no network timeout"))
			.when(con).setNetworkTimeout(any(Executor.class), anyInt());
		final SQLException reset = new SQLException("connection reset by peer", "08006", 0);

		try {
			clocked.bounded(con, StatementBound.OPERATION, () -> {
				clocked.millis = 600000; // ten minutes on a metadata lock, with nothing to end it
				throw reset;
			});
			fail("the failure of the lookup must reach the caller");
		} catch (SQLException e) {
			assertSame(e, reset, "a statement neither layer bounded was reported as having reached a bound");
		}
	}

	/**
	 * The failure reports the time the statement really took rather than the bound it reached.
	 * A cancel that is armed is not a cancel that is acted upon - a session blocked in a row-lock
	 * enqueue on oracle does not process the break its driver sends - and the wait then ends at
	 * the socket read timeout behind it, a margin later than the property that armed it. Reported
	 * as the property alone, the message put a time next to a clock that disagreed with it.
	 */
	@Test
	public void testAFailureAtTheBoundReportsTheTimeItReallyTook() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeUpdate()).thenAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Thread.sleep(1500); // the cancel was armed at 1 s and the database did not act on it
				throw new SQLException("connection reset by peer", "08006", 0);
			}
		});

		try {
			storage.execute(statement);
			fail("the failure of the statement must reach the caller");
		} catch (SQLTimeoutException e) {
			final Matcher took = Pattern.compile("took (\\d+) ms").matcher(e.getMessage());
			assertTrue(took.find(), e.getMessage());
			assertTrue(Long.parseLong(took.group(1)) >= 1500, e.getMessage());
		}
	}

	/**
	 * The rows are read while the bound is still armed. A driver hands them over as they are asked
	 * for - oracle prefetches ten at a time, mssql buffers adaptively - so a drain that happened
	 * after the bound was released would be a wait with nothing bounding it, which is the hang
	 * #877 is about rather than a detail of where the call sits.
	 */
	@Test
	public void testTheRowsAreReadWhileTheBoundIsStillArmed() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final ResultSet rows = mock(ResultSet.class);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(storage.executeResultSet(statement, ResultSet::next), Boolean.FALSE);

		final InOrder inOrder = inOrder(con, rows);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(rows).next();
		inOrder.verify(rows).close(); // the rows are done with before the backstop goes back
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/** A transfer of rows cut at the bound names the property that cut it, as an execution does. */
	@Test
	public void testAFailureWhileTheRowsAreReadIsMeasuredAgainstTheBound() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenAnswer(new Answer<Boolean>() {
			@Override
			public Boolean answer(InvocationOnMock invocation) throws Throwable {
				Thread.sleep(1100); // the driver cancelled the transfer at the bound this test set
				throw new SQLException("canceling statement due to user request", "57014", 0);
			}
		});
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeQuery()).thenReturn(rows);

		try {
			storage.executeResultSet(statement, ResultSet::next);
			fail("the failure of the transfer must reach the caller");
		} catch (SQLTimeoutException e) {
			assertTrue(e.getMessage().contains(StatementBound.OPERATION.property), e.getMessage());
			assertEquals(e.getSQLState(), "57014");
		}
	}

	/**
	 * A driver is allowed to have no query timeout at all, and this backend takes whatever URL a
	 * deployment configures. Such a driver has to keep working, with the socket read timeout as
	 * its whole bound, rather than fail every statement it is given.
	 */
	@Test
	public void testADriverWithoutAQueryTimeoutKeepsWorkingUnderTheBackstop() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);
		doThrow(new SQLFeatureNotSupportedException("no query timeout")).when(statement).setQueryTimeout(anyInt());
		when(statement.executeUpdate()).thenReturn(1);

		assertEquals(storage.execute(statement), 1);

		verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
	}

	/**
	 * The catalog lookups of {@code openTree()} are bounded too. {@code DatabaseMetaData} takes no
	 * query timeout, so the socket read timeout behind the cancel is the only layer they can be
	 * given - and they run once per tree on every open of a backend, behind the same locks as the
	 * {@code create table} they guard.
	 */
	@Test
	public void testACatalogLookupIsBoundedByTheBackstopAlone() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);

		assertEquals(storage.bounded(con, StatementBound.OPERATION, () -> "asked the catalog"), "asked the catalog");

		final InOrder inOrder = inOrder(con);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/**
	 * Which class a batch of a cursor belongs to follows what the database has to do to answer it.
	 * {@code positionToLastKey()} has no key to seek on, so it is an {@code order by k desc} over
	 * the whole table - a scan and a sort of it on mssql, where {@code k} cannot be an index key -
	 * and every open of a backend runs it once per base DN through
	 * {@code EntryContainer.getHighestEntryID()}, outside the try/catch of
	 * {@code BackendImpl.openBackend()}. Bounding that as an entry read would turn a large backend
	 * that opens slowly into one that does not open at all, while the batches the cursor walks
	 * along its index stay operations and keep the bound of one.
	 */
	@Test
	public void testTheScanBehindTheHighestEntryIdIsBulkAndTheBatchesOfACursorAreNot() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		System.setProperty(StatementBound.BULK.property, "0");
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
		final Connection parent = mock(Connection.class);
		when(parent.prepareStatement(anyString())).thenReturn(statement);
		final JDBCStorage.CursorImpl cursor = storage.new CursorImpl(true, new CachedConnection("jdbc:mock", parent),
			new TreeName("dc=example,dc=com", "id2entry"), StatementBound.OPERATION);

		cursor.positionToLastKey();
		verify(statement, never()).setQueryTimeout(anyInt());

		cursor.next();
		verify(statement).setQueryTimeout(7);
	}

	/**
	 * A cursor opened for a walk of a whole tree takes bulk batches, however ordinary the statement
	 * looks: nobody is waiting on that walk, and on mssql it is not even a walk along an index -
	 * {@code k} is a {@code varbinary(max)} there, which cannot be an index key, so every batch is
	 * a scan and a sort of the whole table. This is the class an export, a verify, a rebuild and
	 * the load of a tree at open ask for through {@code ReadableTransaction.openBulkCursor()},
	 * while the cursor of a search keeps the bound of an operation.
	 */
	@Test
	public void testTheBatchesOfABulkCursorAreBulkAndThoseOfASearchAreNot() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		System.setProperty(StatementBound.BULK.property, "0");
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
		final Connection parent = mock(Connection.class);
		when(parent.prepareStatement(anyString())).thenReturn(statement);
		final JDBCStorage.ReadableTransactionImpl txn =
			storage.new ReadableTransactionImpl(new CachedConnection("jdbc:mock", parent));
		final TreeName tree = new TreeName("dc=example,dc=com", "id2entry");

		txn.openBulkCursor(tree).next();
		verify(statement, never()).setQueryTimeout(anyInt());

		txn.openCursor(tree).next();
		verify(statement).setQueryTimeout(7);
	}

	/**
	 * Every statement an import issues is bulk, by the class of the transactions it works through:
	 * phase one writes the trees through {@code put()}, phase two reads them back through
	 * {@code read()} and walks them through {@code openCursor()}, and no client is waiting on any
	 * of it. An upsert of an online import blocked by an LDAP write on the same table would
	 * otherwise sit until the bound of an entry read and then fail the whole import - {@code h} is
	 * the primary key on every dialect, and the default lock wait is forever on three of the four.
	 */
	@Test
	public void testEveryStatementOfAnImportIsBulk() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		System.setProperty(StatementBound.BULK.property, "0");
		final Connection parent = mock(Connection.class);
		// a read timeout of a deployment's own, and deliberately not zero: restoring it and taking
		// the backstop off are one and the same call when what came before was zero, and the
		// assertions below would then hold whichever of the two the code did
		when(parent.getNetworkTimeout()).thenReturn(90000);
		final PreparedStatement statement = mock(PreparedStatement.class);
		// the statement reports the connection it runs on, as CachedConnection.prepareStatement()
		// has it: without this the import would be measured against the first layer alone, and the
		// second one - the layer that decides whether an import can hang on a peer that stopped
		// answering - would never be entered at all
		when(statement.getConnection()).thenReturn(parent);
		when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
		when(parent.prepareStatement(anyString())).thenReturn(statement);
		storage.accessMode = AccessMode.READ_WRITE; // an import has the storage open for writing
		// Borrowed through the seam rather than handed to the constructor: the importer takes its own
		// connection now (#878). It is the same physical connection the entry read below runs on,
		// which is what this test pins - the backstop is keyed on the connection, not on the storage.
		final JDBCStorage importing = new JDBCStorage(mockCfg(JDBCBackendCfg.class), null) {
			@Override
			Connection getConnection(boolean trusted) {
				return new CachedConnection("jdbc:mock", parent);
			}

			@Override
			public StorageStatus getStorageStatus() {
				return StorageStatus.working(); // open already, so the importer borrows and no more
			}
		};
		importing.accessMode = AccessMode.READ_WRITE;
		final JDBCStorage.ImporterImpl importer = importing.new ImporterImpl();
		final TreeName tree = new TreeName("dc=example,dc=com", "id2entry");

		// an entry read of a client arms the backstop on the very connection the import writes to,
		// which is the shape of an online import: one connection, statements of both classes on it
		final CountDownLatch running = new CountDownLatch(1);
		final CountDownLatch mayFinish = new CountDownLatch(1);
		final PreparedStatement operation = lingering(parent, running, mayFinish);
		final Background entryRead = start("entry-read", () -> storage.execute(operation));
		awaitOrFail(running, "the entry read never started");
		// atLeastOnce, not the implicit times(1) of a bare verify: every statement of the import
		// below takes the backstop off and its release arms it again while the entry read is still
		// in flight, so this holds by where it stands in the method rather than by what it pins
		verify(parent, atLeastOnce())
			.setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));

		importer.openCursor(tree).next();
		importer.read(tree, ByteString.valueOfUtf8("key"));
		importer.put(tree, ByteString.valueOfUtf8("key"), ByteString.valueOfUtf8("value"));

		verify(statement, never()).setQueryTimeout(anyInt());
		// and none of them runs under the socket read timeout of the entry read beside it either:
		// while that read is still in flight, the import takes the backstop off the connection,
		// which is what putting the connection's own value back looks like
		verify(parent, atLeastOnce()).setNetworkTimeout(any(Executor.class), eq(90000));
		// and never a zero: nothing here has a zero to put back, so a run that reached this state
		// by restoring one would be a run that read the previous value of another connection
		verify(parent, never()).setNetworkTimeout(any(Executor.class), eq(0));

		mayFinish.countDown();
		entryRead.joinOrFail();
	}

	/**
	 * A statement bounded by the socket read timeout alone is measured against what that layer
	 * really allows it - its bound plus the margin the layer carries - rather than against the
	 * property: nothing cuts a catalog lookup at the bound itself, so a connection reset arriving
	 * just after it is the caller's failure to see, not a query timeout that never happened.
	 */
	@Test
	public void testAFailureBeforeTheBackstopOfACatalogLookupIsPassedThrough() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final SQLException reset = new SQLException("connection reset by peer", "08006", 0);

		try {
			storage.bounded(con, StatementBound.OPERATION, () -> {
				sleep(1100); // past the property, well inside the margin of the layer behind it
				throw reset;
			});
			fail("the failure of the lookup must reach the caller");
		} catch (SQLException e) {
			assertSame(e, reset);
		}
	}

	/**
	 * A driver with no network timeout at all is asked once and then left alone: it says so with
	 * {@code SQLFeatureNotSupportedException}, and asking it again costs a throw on every statement
	 * for the life of the storage. Its own storage here, since that is the scope of the latch.
	 */
	@Test
	public void testADriverWithoutANetworkTimeoutIsNotAskedAgain() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final JDBCStorage isolated = new JDBCStorage(mockCfg(JDBCBackendCfg.class), null);
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		doThrow(new SQLFeatureNotSupportedException("no network timeout"))
			.when(con).setNetworkTimeout(any(Executor.class), anyInt());
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);
		when(statement.executeUpdate()).thenReturn(1);

		assertEquals(isolated.execute(statement), 1);
		assertEquals(isolated.execute(statement), 1);

		verify(con, times(1)).setNetworkTimeout(any(Executor.class), anyInt());
	}

	/**
	 * A connection that failed the call says nothing about the driver - it may be the very one
	 * that reached this timeout - so the next statement is armed as usual. The two causes share a
	 * catch and must not share a verdict: taking one for the other silences the backstop of a
	 * whole storage on a single dying connection.
	 */
	@Test
	public void testAConnectionThatFailedTheBackstopDoesNotSpeakForTheDriver() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		doThrow(new SQLException("the connection is closed", "08003", 0))
			.when(con).setNetworkTimeout(any(Executor.class), anyInt());
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);
		when(statement.executeUpdate()).thenReturn(1);

		assertEquals(storage.execute(statement), 1);
		assertEquals(storage.execute(statement), 1);

		verify(con, times(2)).setNetworkTimeout(any(Executor.class),
			eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
	}

	/**
	 * The statistics refresh after an import runs under a bound of its own - it takes as long as a
	 * scan of the table it describes, which no class of {@link StatementBound} can be asked to
	 * allow - and under both layers of it. The second one is the reason: on oracle this statement
	 * is {@code dbms_stats.gather_table_stats}, the engine whose session does not act on the break
	 * its driver sends, and it runs at the very end of a successful import, where a cancel that
	 * never arrives would park the import with its data already committed.
	 */
	@Test
	public void testTheStatisticsRefreshRunsUnderItsOwnBoundAndTheBackstop() throws Exception {
		System.setProperty(JDBCStorage.STATISTICS_TIMEOUT_PROPERTY, "60");
		final PreparedStatement statement = mock(PreparedStatement.class);
		final Connection con = mock(oracleConnection.class); // the dialect is read off the connection
		when(con.getNetworkTimeout()).thenReturn(0);
		when(con.prepareStatement(anyString())).thenReturn(statement);

		assertTrue(storage.updateTableStatistics(con, singletonList(new TreeName("dc=example,dc=com", "id2entry"))));

		verify(statement).setQueryTimeout(60);
		final InOrder inOrder = inOrder(con, statement);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((60 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(statement).execute();
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/**
	 * A connection whose driver refuses the call mid-flight is given back what it carried before.
	 * The entry holding that value is dropped as soon as the last statement on the connection is
	 * through, so a backstop left armed goes back to the pool as the connection's own read timeout
	 * - and the next borrower, which only ever tightens, reads it as the value of a deployment and
	 * keeps it from then on, cutting a statement of an unbounded class at a bound it never had.
	 */
	@Test
	public void testAConnectionThatFailedTheBackstopIsGivenBackWhatItCarried() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		System.setProperty(StatementBound.BULK.property, "100");
		final Connection con = mock(Connection.class);
		// a read timeout of a deployment's own, and looser than either bound in flight below: a
		// tighter one is what the backstop declines to loosen, and it would put that value back
		// instead of ever reaching the call that fails here
		when(con.getNetworkTimeout()).thenReturn(200000);
		// the arming of the tighter bound goes through, and the re-arm of the looser one does not
		doThrow(new SQLException("the connection is closed", "08003", 0)).when(con)
			.setNetworkTimeout(any(Executor.class), eq((100 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		final CountDownLatch running = new CountDownLatch(1);
		final CountDownLatch mayFinish = new CountDownLatch(1);
		final PreparedStatement operation = lingering(con, running, mayFinish);
		final PreparedStatement bulk = mock(PreparedStatement.class);
		when(bulk.getConnection()).thenReturn(con);
		when(bulk.executeUpdate()).thenReturn(1);

		final Background entryRead = start("entry-read", () -> storage.execute(operation));
		awaitOrFail(running, "the entry read never started");
		assertEquals(storage.execute(bulk, StatementBound.BULK), 1); // its re-arm is what fails
		mayFinish.countDown();
		entryRead.joinOrFail();

		final InOrder inOrder = inOrder(con);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(200000));
	}

	/**
	 * The connection an import would have held goes back to the pool when the importer cannot be
	 * built on it. That is a designed path rather than an accident: an import of a read-only storage
	 * throws {@code ReadOnlyStorageException} where the importer is built, and the connection
	 * borrowed for the import - the one it keeps for its whole duration - was leaving the pool for
	 * good there, with the transaction it had already begun.
	 */
	@Test
	public void testStartImportGivesTheConnectionBackWhenTheImporterCannotBeBuilt() throws Exception {
		final Connection con = mock(Connection.class);
		final AtomicInteger borrows = new AtomicInteger();
		final JDBCStorage readOnly = new JDBCStorage(mockCfg(JDBCBackendCfg.class), null) {
			@Override
			Connection getConnection(boolean trusted) {
				borrows.incrementAndGet();
				return con;
			}

			@Override
			public StorageStatus getStorageStatus() {
				return StorageStatus.working(); // open already, so that startImport() borrows and no more
			}
		};
		readOnly.accessMode = AccessMode.READ_ONLY;

		try {
			readOnly.startImport();
			fail("an import of a read-only storage must not be handed an importer");
		} catch (ReadOnlyStorageException expected) {
			// the designed path this test is about
		}

		// Nothing to give back. With the borrow inside the importer's constructor (#878) the refusal
		// stands in front of it, so an import of a read-only storage takes no connection at all
		// rather than taking one and returning it. Pinned as never borrowed rather than dropped: the
		// leak this covers - a connection out of the pool for good, holding a transaction it had
		// already begun - is the same one, and never taking it is the state that cannot leak it.
		assertEquals(borrows.get(), 0);
		verify(con, never()).close();
	}

	/**
	 * And the storage goes back with the connection where this method is what opened it:
	 * {@code ImporterImpl.close()} is the only thing that closes a storage an import opened, so a
	 * failure between the open and the importer that would have held it leaves it open for good.
	 * The borrow of the connection was already covered that way; the build of the importer was not.
	 * <p>
	 * What fails the build here is a storage that is not writeable - the one failure of the
	 * importer's constructor a test can produce from outside it, and it takes an open that leaves
	 * the storage read-only to get there. What it stands for is any {@code Error} out of that
	 * constructor, which is what the {@code finally} is for.
	 */
	@Test
	public void testStartImportClosesTheStorageItOpenedWhenTheImporterCannotBeBuilt() throws Exception {
		final Connection con = mock(Connection.class);
		final AtomicInteger opens = new AtomicInteger();
		final AtomicInteger closes = new AtomicInteger();
		final JDBCStorage notOpen = new JDBCStorage(mockCfg(JDBCBackendCfg.class), null) {
			@Override
			Connection getConnection(boolean trusted) {
				return con;
			}

			@Override
			public StorageStatus getStorageStatus() {
				return StorageStatus.lockedDown(LocalizableMessage.raw("closed")); // so startImport() opens it
			}

			@Override
			public void open(AccessMode accessMode) {
				opens.incrementAndGet(); // and leaves this storage read-only, so that the build below fails
			}

			@Override
			public void close() {
				closes.incrementAndGet();
			}
		};

		try {
			notOpen.startImport();
			fail("an import that cannot be given an importer must not report one");
		} catch (ReadOnlyStorageException expected) {
			// the build failing after this method opened the storage, which is the path under test
		}

		assertEquals(opens.get(), 1, "the storage was not opened by the importer, so nothing was owed back");
		// the connection is not owed back here either: the refusal stands in front of the borrow now
		// (#878), so what this path has to give back is the storage alone
		verify(con, never()).close();
		assertEquals(closes.get(), 1, "the storage the importer opened was left open");
	}

	/**
	 * A row whose {@code v} is null is a row that exists, and reading it has to fail rather than
	 * report the key as absent - which is what {@code read()} of the same row does. Reading the
	 * rows inside the bound had turned the value into a raw {@code byte[]} on the way out of the
	 * handler, and null then stood in for both.
	 */
	@Test
	public void testARowWithoutAValueFailsRatherThanReportingTheKeyAsAbsent() throws Exception {
		final TreeName treeName = new TreeName("dc=example,dc=com", "id2entry");
		final ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getBytes("v")).thenReturn(null); // the schema allows it, however this backend writes
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeQuery()).thenReturn(rows);
		final Connection parent = mock(Connection.class);
		when(parent.prepareStatement(anyString())).thenReturn(statement);
		final JDBCStorage.CursorImpl cursor = storage.new CursorImpl(true, new CachedConnection("jdbc:mock", parent),
			treeName, StatementBound.OPERATION);

		try {
			cursor.positionToKey(ByteString.valueOfUtf8("key"));
			fail("a row whose value is null must not be read as a key that is not there");
		} catch (StorageRuntimeException expected) {
			// the failure the production path names, not whatever null happens to reach first: a bare
			// NullPointerException out of ByteString.wrap is satisfied by any unrelated one later
			// introduced into positionToKey, and it says nothing about which table holds the row
			assertTrue(expected.getMessage().contains("no value"), expected.getMessage());
			assertTrue(expected.getMessage().contains(storage.getTableName(treeName)), expected.getMessage());
		}
	}

	/**
	 * And so does a batch of a cursor, which is the third reader of a value and the one that would
	 * fail furthest from the row: a batch is buffered whole and unwrapped a row at a time
	 * afterwards, so left unchecked it fails from {@code advanceFromBuffer()} - outside the bound
	 * and outside the {@code catch} of the batch that read it.
	 */
	@Test
	public void testABatchWithARowWithoutAValueFailsTheSameWay() throws Exception {
		final TreeName treeName = new TreeName("dc=example,dc=com", "id2entry");
		final ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getBytes(1)).thenReturn(ByteString.valueOfUtf8("key").toByteArray());
		when(rows.getBytes(2)).thenReturn(null);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeQuery()).thenReturn(rows);
		final Connection parent = mock(Connection.class);
		when(parent.prepareStatement(anyString())).thenReturn(statement);
		final JDBCStorage.CursorImpl cursor = storage.new CursorImpl(true, new CachedConnection("jdbc:mock", parent),
			treeName, StatementBound.OPERATION);

		try {
			cursor.next();
			fail("a batch holding a row whose value is null must not hand that row out");
		} catch (StorageRuntimeException expected) {
			assertTrue(expected.getMessage().contains("no value"), expected.getMessage());
			assertTrue(expected.getMessage().contains(storage.getTableName(treeName)), expected.getMessage());
		}
	}

	/**
	 * And a read of the same row fails the same way, which is the whole of what {@code null} means
	 * here: the two answer with it for one reason only, and a row that exists is not that reason.
	 */
	@Test
	public void testAReadOfARowWithoutAValueFailsTheSameWay() throws Exception {
		final TreeName treeName = new TreeName("dc=example,dc=com", "id2entry");
		final ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getBytes("v")).thenReturn(null);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeQuery()).thenReturn(rows);
		final Connection parent = mock(Connection.class);
		when(parent.prepareStatement(anyString())).thenReturn(statement);

		try {
			storage.new ReadableTransactionImpl(new CachedConnection("jdbc:mock", parent))
				.read(treeName, ByteString.valueOfUtf8("key"));
			fail("a row whose value is null must not be read as a key that is not there");
		} catch (StorageRuntimeException expected) {
			assertTrue(expected.getMessage().contains("no value"), expected.getMessage());
		}
	}

	// JDBCStorage.dialectOf() reads the engine off the class name of the connection, so a mock of
	// this interface is an oracle connection as far as the storage is concerned - which is the
	// whole reason for the lower case name here.
	private interface oracleConnection extends Connection {}
}
