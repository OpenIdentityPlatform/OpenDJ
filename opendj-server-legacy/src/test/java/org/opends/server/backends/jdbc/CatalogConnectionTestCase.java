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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * The connection the tree catalog of a backend is read and written on (#888): what it hands its
 * driver, and what it does with the connection it gets back. It is established outside the pool -
 * the caller of {@code openTree()} is holding a pooled connection already - so nothing the pool is
 * asserted on covers it, and a container suite covers it only where a container starts: it skips
 * itself whole otherwise, which leaves a bound nothing exercises.
 * <p>
 * No database is needed for any of it. The url is a postgresql one the pgjdbc driver cannot parse,
 * so {@code DriverManager} falls through to the probe of this class, while {@code
 * CachedConnection.ConnectDialect} still reads it as postgres - which is what makes the connect fill
 * in bounds at all, and what a url of an engine of nobody's would not.
 */
@SuppressWarnings("javadoc")
public class CatalogConnectionTestCase extends DirectoryServerTestCase {

	/**
	 * What a caller with no replay above it hands the connect: the importer is the one such caller in
	 * the product, and every case here that is not about the window itself asks the way it asks, so
	 * that what it pins is the property and not a window of the test's own.
	 */
	private static final long NO_REPLAY_WINDOW = Long.MAX_VALUE;

	private ProbeDriver probeDriver;

	/** The standing read bound as this JVM was started with it, put back before every case. */
	private static final int CONFIGURED_READ_TIMEOUT_MILLIS = CachedConnection.readTimeoutMillis;

	@BeforeClass
	public void registerProbeDriver() throws SQLException {
		probeDriver = new ProbeDriver();
		DriverManager.registerDriver(probeDriver);
	}

	@AfterClass(alwaysRun = true)
	public void deregisterProbeDriver() throws SQLException {
		if (probeDriver != null) {
			DriverManager.deregisterDriver(probeDriver);
		}
	}

	/**
	 * Nothing of one case reaches the next: the probe is a field of the class and a case that fails
	 * before its finally would otherwise leave its stubbed connection, its refusals or the properties
	 * of its last attempt to be read by whatever runs after it.
	 */
	@BeforeMethod
	public void resetProbe() {
		probeDriver.lastProperties = null;
		probeDriver.answer = null;
		probeDriver.refusal = null;
		probeDriver.refusalsLeft.set(0);
		probeDriver.attempts.set(0);
		probeDriver.refusalDelayMs = 0;
		probeDriver.interruptOnAttempt = false;
		// the same for the bound the connect reads off the class: a case that varies it and fails
		// before its finally would otherwise hand its value to whatever runs after it
		CachedConnection.readTimeoutMillis = CONFIGURED_READ_TIMEOUT_MILLIS;
	}

	private static JDBCStorage storageFor(String url) {
		final JDBCBackendCfg cfg = mockCfg(JDBCBackendCfg.class);
		when(cfg.getBackendId()).thenReturn("catalogProbe");
		when(cfg.getDBDirectory()).thenReturn(url);
		return new JDBCStorage(cfg, null);
	}

	/**
	 * The bound of the connect is the one the operator configured for this backend's connects, and
	 * not a literal of the code: a deployment whose login legitimately takes longer than the default
	 * raises {@link CachedConnection#CONNECT_TIMEOUT_PROPERTY} for it, and a catalog connect bounded
	 * tighter than that fails in 08001 - which is no conflict a write replays, so the backend stops
	 * opening on an installation that opened before this connection existed.
	 * <p>
	 * Asked with no deadline over it, so that what the case pins is the configured bound alone: what
	 * a deadline does to it is the case below.
	 */
	@Test
	public void testTheCatalogConnectTakesTheConfiguredBound() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "120");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "0");
		try {
			probeDriver.lastProperties = null;
			storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW).close();
			assertBoundedAt(probeDriver.lastProperties, 120);
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * One attempt is never left to run past the deadline the retry of this connect is given, which is
	 * the deadline of a borrow ({@link CachedConnection#POOL_TIMEOUT_PROPERTY}): an attempt bounded
	 * looser than what is left of it would overrun it by a whole connect timeout. The pool bounds its
	 * own attempts by exactly this rule, and a connect established the way a pooled one is takes it.
	 */
	@Test
	public void testTheCatalogConnectIsNeverBoundedPastTheDeadlineOfItsRetry() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "120");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "20");
		try {
			storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW).close();
			// a range and not the 20 exactly: the bound is what is left of the deadline when the attempt
			// is made, so a pause of a second anywhere before it - a collection, the first touch of a
			// class on a loaded box - makes it 19, and the case is about the deadline and not the clock
			assertBoundedWithin(probeDriver.lastProperties, 15, 20);
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * A database taking no connection <em>for the moment</em> - at its connection limit, or still
	 * recovering - is waited out rather than reported: one attempt loses a race the borrow beside it
	 * wins, and this connect is on the critical path of the first read-write open of every backend.
	 */
	@Test
	public void testTheCatalogConnectWaitsOutADatabaseTakingNoConnectionForTheMoment() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		// both, since both are read on every connect: an ambient connect bound would change the
		// per-attempt bound these cases run under without changing anything they assert on
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "60");
		probeDriver.refusal = new SQLException("too many clients already", "53300");
		probeDriver.refusalsLeft.set(2);
		try {
			storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW).close();
			assertEquals(probeDriver.attempts.get(), 3,
				"a connect refused for the moment was not retried the way a borrow of the pool retries it");
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * And everything else is the caller's to see rather than waited out behind its back: a password
	 * that is not accepted does not become a minute of silence and then the same failure.
	 * <p>
	 * A guard rather than a regression test, and worth saying so: the head before this connect had a
	 * retry made one attempt and reported it, so it satisfies this case by having no loop at all.
	 * What the case is here for is the loop that does exist staying this narrow - a predicate widened
	 * to any refusal turns a wrong password into a minute of silence per open, and nothing else in
	 * this suite would notice.
	 */
	@Test
	public void testTheCatalogConnectDoesNotRetryAFailureThatWillNotClear() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		// both, since both are read on every connect: an ambient connect bound would change the
		// per-attempt bound these cases run under without changing anything they assert on
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "60");
		probeDriver.refusal = new SQLException("password authentication failed", "28P01");
		probeDriver.refusalsLeft.set(Integer.MAX_VALUE);
		try {
			storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW);
			fail("a connect that will not clear was retried instead of being reported");
		} catch (SQLException expected) {
			assertEquals(expected.getSQLState(), "28P01", "the failure of the driver was not the one reported");
			assertEquals(probeDriver.attempts.get(), 1, "a failure that will not clear was attempted more than once");
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * The wait ends at the deadline of a borrow, as a timeout by type and carrying the state of the
	 * driver's own last refusal: a state of this code's making would be read by {@code write()} as a
	 * connection the database dropped, and the retry must change no classification.
	 */
	@Test
	public void testTheCatalogConnectGivesUpAtTheDeadlineOfABorrow() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		// both, since both are read on every connect: an ambient connect bound would change the
		// per-attempt bound these cases run under without changing anything they assert on
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "1");
		// a vendor code beside the state, since both are carried over: an oracle failure says what it
		// is in the ORA number and not in the SQLState, so a timeout dropping the code would answer 0
		// where the classifier reading it expects the driver's own
		probeDriver.refusal = new SQLException("the database system is starting up", "57P03", 3113);
		probeDriver.refusalsLeft.set(Integer.MAX_VALUE);
		try {
			storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW);
			fail("a connect refused for the whole deadline was not given up on");
		} catch (SQLTimeoutException expected) {
			// the state of the driver's own refusal and not one of this code's making: a manufactured
			// 08001 is read by write() as a connection the database dropped, which would replay an
			// attempt whose pooled connection is healthy and distrust the pool over it
			assertEquals(expected.getSQLState(), "57P03",
				"the failure the deadline ended carried another state than the driver's own");
			assertEquals(expected.getErrorCode(), 3113,
				"the failure the deadline ended dropped the vendor code of the driver's own");
			assertNotNull(expected.getCause(), "the driver's own failure was not carried as the cause");
			assertTrue(probeDriver.attempts.get() > 1,
				"the deadline was reached without the connect having been retried at all");
			// and it says which of the two bounds ran out, the property being the thing to raise only
			// where the property is what ended the wait
			assertTrue(expected.getMessage().contains(CachedConnection.POOL_TIMEOUT_PROPERTY + "=1s"),
				"the timeout did not name the bound that ended it: " + expected.getMessage());
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * The wait may not outlast the replay window of the {@code write()} it runs inside: this loop
	 * runs within one attempt of that one, so a refusal waited out past the window reaches it with
	 * the window already spent and is thrown unreplayed - the retry would cost the caller the replay
	 * it had before there was a retry here at all. The shorter of the two bounds is the deadline of
	 * the wait; the bound of one attempt is not taken from it, and this case pins that too.
	 */
	@Test
	public void testTheCatalogConnectDoesNotOutlastTheReplayWindowOfItsCaller() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		// a minute, which is the default and six times the window of a write: the property is what
		// this connect would wait out if the window did not reach it
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "60");
		probeDriver.refusal = new SQLException("the database system is starting up", "57P03");
		probeDriver.refusalsLeft.set(Integer.MAX_VALUE);
		final long startedAt = System.currentTimeMillis();
		try {
			storageFor(ProbeDriver.URL).newCatalogConnection(startedAt + 300);
			fail("a connect refused past the replay window of its caller was not given up on");
		} catch (SQLTimeoutException expected) {
			final long waited = System.currentTimeMillis() - startedAt;
			assertTrue(waited < 30_000,
				"the connect waited " + waited + " ms, which is the pool timeout rather than the window above it");
			assertTrue(probeDriver.attempts.get() > 1,
				"the window was spent without the connect having been retried at all");
			assertEquals(expected.getSQLState(), "57P03",
				"the failure the window ended carried another state than the driver's own");
			// the line has to send an operator to the right knob: raising the pool timeout moves
			// nothing where the window of the write is the shorter bound
			assertTrue(expected.getMessage().contains("replay window"),
				"the timeout did not say which of the two bounds ended it: " + expected.getMessage());
			// and one attempt keeps the bound the operator configured for a login of this database:
			// the window decides how long it is worth retrying, not how long a login may take, and
			// an attempt cut to what is left of the window is the connect dying where the pooled one
			// beside it succeeds - the backend that stops opening
			assertBoundedAt(probeDriver.lastProperties, CachedConnection.DEFAULT_CONNECT_TIMEOUT_SECONDS);
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * A thread asked to stop is not answered by sleeping out the rest of a pool timeout: the flag is
	 * put back - every frame above this one reads it to decide whether to unwind - and the driver's
	 * own refusal is what the caller is told, the interrupt riding along with it so that a connect
	 * cut short by a shutdown is not read off the log as a database refusing connections.
	 */
	@Test
	public void testTheCatalogConnectReportsAnInterruptRatherThanSleepingPastIt() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "60");
		probeDriver.refusal = new SQLException("the database system is starting up", "57P03");
		probeDriver.refusalsLeft.set(Integer.MAX_VALUE);
		// raised inside the attempt rather than by another thread racing this one: the first backoff
		// is a millisecond, and a sleep entered with the flag already up throws at once
		probeDriver.interruptOnAttempt = true;
		try {
			storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW);
			fail("a connect interrupted while it waited was not reported at all");
		} catch (SQLException expected) {
			assertFalse(expected instanceof SQLTimeoutException,
				"an interrupt was reported as the deadline of the wait running out");
			assertEquals(expected.getSQLState(), "57P03",
				"the interrupt replaced the driver's own failure instead of riding along with it");
			assertEquals(probeDriver.attempts.get(), 1, "the wait went on past the interrupt");
			boolean carried = false;
			for (final Throwable suppressed : expected.getSuppressed()) {
				carried |= suppressed instanceof InterruptedException;
			}
			assertTrue(carried, "the interrupt was dropped rather than carried on the failure reported");
			assertTrue(Thread.currentThread().isInterrupted(),
				"the flag Thread.sleep() cleared was not put back, so nothing above can read it");
		} finally {
			Thread.interrupted(); // cleared here, or every case running after this one on this thread meets it
			probeDriver.interruptOnAttempt = false;
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * A connect of this backend that stalls says so in the log, and is throttled apart from the
	 * borrows of the pool that address the same database: the two stall for the same reason, and a
	 * borrow that reported a moment ago would otherwise silence the connect that is about to fail -
	 * which is the one of the two an operator has no other line about.
	 * <p>
	 * The stall is a real one rather than a call of the formatter: the guard of the throttle passes
	 * only where a connect has been retrying for a second, so nothing under it is reached by a case
	 * whose refusals come back at once.
	 */
	@Test
	public void testAStallOfTheCatalogConnectIsThrottledApartFromTheBorrowsOfThePool() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "60");
		// one refusal, slow enough that the connect has been retrying for longer than the guard of
		// the throttle, and then a connection: what is asserted is the warning, not the failure
		probeDriver.refusal = new SQLException("the database system is starting up", "57P03");
		probeDriver.refusalsLeft.set(1);
		probeDriver.refusalDelayMs = CachedConnection.STALL_WARNING_AFTER_MS + 100;
		try {
			// a url of its own: the throttle is keyed by url, and a case sharing one with another
			// would read that one's stamp instead of its own
			storageFor(ProbeDriver.STALL_URL).newCatalogConnection(NO_REPLAY_WINDOW).close();
			final long now = System.currentTimeMillis();
			final long longEnoughAgo = now - 2 * CachedConnection.STALL_WARNING_AFTER_MS;
			// the connect reported its stall: the moment is filed, so the next one inside the interval
			// is not due. Nothing else of this suite touches this url
			assertFalse(CachedConnection.stallWarningDue(ProbeDriver.STALL_URL, "|tree catalog", longEnoughAgo, now),
				"the connect stalled for longer than the guard and reported nothing");
			// and a borrow of the pool on that very url is still due one of its own, which is the half
			// of the throttle key that keeps the two waits from silencing each other
			assertTrue(CachedConnection.stallWarningDue(ProbeDriver.STALL_URL, "", longEnoughAgo, now),
				"a stall of this connect silenced the borrows of the pool addressing the same database");
		} finally {
			probeDriver.refusalDelayMs = 0;
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * Nothing configured is the default of the pool, which is what this connect used to take always.
	 * The deadline is pinned rather than left to its own default: the attempt takes the shorter of
	 * the two, so a pool timeout set anywhere - the surefire configuration, the environment, another
	 * suite - would otherwise decide what this case asserts.
	 */
	@Test
	public void testTheCatalogConnectTakesTheDefaultWhereNothingIsConfigured() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "0");
		try {
			probeDriver.lastProperties = null;
			storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW).close();
			assertBoundedAt(probeDriver.lastProperties, CachedConnection.DEFAULT_CONNECT_TIMEOUT_SECONDS);
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * A property of 0 is the operator asking for no bound at all - the pool reads it that way - and a
	 * connect that bounded itself anyway would be answering a setting with the opposite of it. Nothing
	 * is handed to the driver then, and there is no read bound to lift once the login is through.
	 * <p>
	 * Both properties, which is what the pool itself says leaves a connect unbounded: the deadline of
	 * the retry bounds the attempt where there is one, so turning the per-attempt bound off alone
	 * leaves the attempt bounded by what is left of that deadline - the case above.
	 */
	@Test
	public void testTheCatalogConnectIsUnboundedWhereTheOperatorTurnedTheBoundOff() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "0");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "0");
		try {
			probeDriver.lastProperties = null;
			final Connection con = storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW);
			con.close();
			assertNotNull(probeDriver.lastProperties, "no properties were handed to the driver at all");
			assertTrue(probeDriver.lastProperties.isEmpty(),
				"a connect the operator asked for no bound on was bounded anyway: " + probeDriver.lastProperties);
			verify(con, never()).setNetworkTimeout(any(), anyInt());
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * What the connection is handed back for: rows of its own, committed where they are written. The
	 * read bound of the login is lifted as soon as the login is through (#872) - it is a bound of the
	 * connect and not of the statements of the catalog - and the isolation is the pool's, a repeatable
	 * read gap-locking a catalog two transactions enrol into.
	 */
	@Test
	public void testTheCatalogConnectionIsSetUpForItsRows() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		// the read bound is lifted only where the attempt was given one, and the attempt takes the
		// shorter of the two properties: a deadline of 0 elsewhere would leave nothing to lift here
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "0");
		try {
			final Connection con = storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW);
			con.close();
			verify(con).setAutoCommit(false);
			verify(con).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			verify(con).setNetworkTimeout(any(), eq(0));
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * A driver that will not take the read bound of the login back does not cost the backend its
	 * catalog: the pool meets the same failure and hands the connection to the borrower waiting for
	 * it, and a connect failing where the pooled one beside it succeeds is a backend that stops
	 * opening on an installation which opened before this connection existed. Reported, and kept.
	 * <p>
	 * A guard rather than a regression test, like the one above: the head before this round already
	 * caught that failure and returned the connection, so nothing of the round makes this case pass.
	 * It is here because the answer was reached for twice - once as "fail the connect", which this
	 * round took back out - and a third attempt at it would go unnoticed otherwise.
	 */
	@Test
	public void testTheCatalogConnectionIsKeptWhereTheReadBoundWillNotComeOff() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "0");
		final Connection keeping = mock(Connection.class);
		doThrow(new SQLFeatureNotSupportedException("no network timeout here"))
			.when(keeping).setNetworkTimeout(any(), anyInt());
		probeDriver.answer = keeping;
		try {
			final Connection con = storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW);
			assertNotNull(con, "a connection whose read bound would not come off was not handed back");
			verify(con).setAutoCommit(false);
			verify(con, never()).close();
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * The catalog connection carries the read bound a deployment asked for
	 * ({@link CachedConnection#READ_TIMEOUT_PROPERTY}), exactly as a connection of the pool does.
	 * <p>
	 * Its statements are bounded by the class of the work they belong to, and nothing else on it is:
	 * the {@code commit()} that writes a catalog row, the {@code rollback()} of a session given up
	 * and the {@code close()} of one that lost the race have no bound of their own, so against a
	 * database which stops answering after the login they wait for as long as the socket does. That
	 * is the gap #885 closed for every connection of the pool - this one was written beside them,
	 * one round before the property existed, and was left with the lift alone.
	 */
	@Test
	public void testTheCatalogConnectionCarriesTheReadBoundAskedFor() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "30");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "0");
		CachedConnection.readTimeoutMillis = 90000;
		try {
			final Connection con = storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW);
			con.close();
			verify(con).setNetworkTimeout(any(), eq(90000));
			// and the bound of the login is gone with it: the value that replaces it is the whole of
			// what this connection carries, not a lift followed by a second call putting one back
			verify(con, never()).setNetworkTimeout(any(), eq(0));
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * And it carries it whether or not the login had a bound of its own to lift: the read bound of a
	 * login is only ever set where the connect is bounded, so a deployment running with
	 * {@code connect.timeout=0} - the setting that leaves a connect to the deadline of the retry
	 * alone - would otherwise set this property and get nothing for it on this connection.
	 */
	@Test
	public void testTheCatalogConnectionCarriesTheReadBoundWhereItsLoginHadNoneToLift() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "0");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "0");
		CachedConnection.readTimeoutMillis = 90000;
		try {
			final Connection con = storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW);
			con.close();
			verify(con).setNetworkTimeout(any(), eq(90000));
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * A read bound standing in the connection string is the deployment's own: it is not replaced by
	 * the configured one here, exactly as it is not on a connection of the pool, and exactly as the
	 * read bound of a login is not set on top of it. A guard rather than a regression test - the
	 * bound is asked of {@link CachedConnection#standingReadBoundMillis}, which answers 0 for such a
	 * url - and it is here because a bound put on from the value of the property alone would pass
	 * every other case of this class.
	 */
	@Test
	public void testAReadBoundOfTheUrlIsNotReplacedOnTheCatalogConnection() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "30");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "0");
		CachedConnection.readTimeoutMillis = 90000;
		try {
			final Connection con = storageFor(ProbeDriver.URL + "?socketTimeout=30")
				.newCatalogConnection(NO_REPLAY_WINDOW);
			con.close();
			verify(con, never()).setNetworkTimeout(any(), anyInt());
		} finally {
			restore(previous);
			restorePool(previousPool);
		}
	}

	/**
	 * A connection whose set-up failed is held by nobody - the caller is answered with the failure -
	 * so it is closed here or it leaks for the life of the process, one per open of a storage.
	 */
	@Test
	public void testAConnectionWhoseSetUpFailsIsClosed() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		final String previousPool = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		// pinned like every other case of this class: both are read from the system properties on every
		// connect, so an ambient value would have this case exercise another path than the one it names
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "0");
		final Connection failing = mock(Connection.class);
		doThrow(new SQLException("no transaction here", "08006")).when(failing).setAutoCommit(false);
		probeDriver.answer = failing;
		try {
			storageFor(ProbeDriver.URL).newCatalogConnection(NO_REPLAY_WINDOW);
			fail("a connection this backend could not set up was handed to the catalog");
		} catch (SQLException expected) {
			// the failure of the set-up itself, reported to the caller rather than swallowed
		} finally {
			probeDriver.answer = null;
			restore(previous);
			restorePool(previousPool);
		}
		verify(failing).close();
	}

	/** What the dialect of this url declares, at the given number of seconds. */
	private static void assertBoundedAt(Properties handed, long seconds) {
		assertNotNull(handed, "no properties were handed to the driver at all");
		final CachedConnection.ConnectDialect dialect = CachedConnection.ConnectDialect.of(ProbeDriver.URL);
		assertNotNull(dialect, "the url of this test is read as an engine of nobody's, so it is bounded by nothing");
		for (final String property : dialect.connectProperties) {
			assertEquals(handed.getProperty(property), Long.toString(seconds * dialect.connectUnitsPerSecond),
				property + " did not reach the driver at the configured bound");
		}
		assertEquals(handed.getProperty(dialect.readProperties[0]),
			Long.toString(seconds * dialect.readUnitsPerSecond),
			dialect.readProperties[0] + " did not reach the driver at the configured bound");
	}

	/** The same where the value is what is left of a deadline, which no case may pin to the millisecond. */
	private static void assertBoundedWithin(Properties handed, long atLeastSeconds, long atMostSeconds) {
		assertNotNull(handed, "no properties were handed to the driver at all");
		final CachedConnection.ConnectDialect dialect = CachedConnection.ConnectDialect.of(ProbeDriver.URL);
		assertNotNull(dialect, "the url of this test is read as an engine of nobody's, so it is bounded by nothing");
		final String property = dialect.connectProperties[0];
		final String handedValue = handed.getProperty(property);
		assertNotNull(handedValue, property + " did not reach the driver at all");
		final long seconds = Long.parseLong(handedValue) / dialect.connectUnitsPerSecond;
		assertTrue(seconds >= atLeastSeconds && seconds <= atMostSeconds,
			property + " reached the driver at " + seconds + "s, outside the deadline it is taken from ("
				+ atLeastSeconds + ".." + atMostSeconds + "s)");
	}

	private static void restore(String previous) {
		if (previous == null) {
			System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		} else {
			System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, previous);
		}
	}

	private static void restorePool(String previous) {
		if (previous == null) {
			System.clearProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		} else {
			System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, previous);
		}
	}

	/** Records the properties a catalog connection hands its driver, and connects to nothing. */
	private static final class ProbeDriver implements Driver {
		/**
		 * A postgresql url with a port that is not a number: pgjdbc cannot parse it and answers the
		 * DriverManager with null - or with a failure, which it records and walks past all the same -
		 * so this probe is the driver that ends up answering, while ConnectDialect still reads the
		 * prefix as postgres.
		 */
		static final String URL = "jdbc:postgresql://catalog-probe:not-a-port/db";

		/**
		 * The same for the one case about the stall warning, which reads the throttle this connect
		 * files its report in: that throttle is keyed by url, so a case sharing one with any other
		 * would be asserting on whichever of them ran first.
		 */
		static final String STALL_URL = "jdbc:postgresql://catalog-probe-stall:not-a-port/db";

		volatile Properties lastProperties;

		/** The connection to answer with, for a test about what is done with it; a fresh mock otherwise. */
		volatile Connection answer;

		/** How many attempts to refuse before answering, and with what; for the cases about the retry. */
		final AtomicInteger refusalsLeft = new AtomicInteger();
		volatile SQLException refusal;

		/** How long a refused attempt takes, for the one case that needs a wait the throttle counts. */
		volatile long refusalDelayMs;

		/**
		 * Whether an attempt raises the interrupt flag of the thread asking for it, so that the case
		 * about an interrupted wait does not have to race a backoff of one millisecond from outside.
		 */
		volatile boolean interruptOnAttempt;

		/** Every attempt this driver was asked to make, refused ones included. */
		final AtomicInteger attempts = new AtomicInteger();

		@Override
		public Connection connect(String url, Properties info) throws SQLException {
			if (!acceptsURL(url)) {
				return null; // not ours: DriverManager goes on to the next driver
			}
			attempts.incrementAndGet();
			lastProperties = info;
			if (refusal != null && refusalsLeft.getAndDecrement() > 0) {
				if (refusalDelayMs > 0) {
					try {
						Thread.sleep(refusalDelayMs);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
				if (interruptOnAttempt) {
					Thread.currentThread().interrupt();
				}
				throw refusal;
			}
			return answer != null ? answer : mock(Connection.class);
		}

		@Override
		public boolean acceptsURL(String url) {
			return url != null && (url.startsWith(URL) || url.startsWith(STALL_URL));
		}

		@Override
		public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
			return new DriverPropertyInfo[0];
		}

		@Override
		public int getMajorVersion() {
			return 1;
		}

		@Override
		public int getMinorVersion() {
			return 0;
		}

		@Override
		public boolean jdbcCompliant() {
			return false;
		}

		@Override
		public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
			throw new SQLFeatureNotSupportedException();
		}
	}
}
