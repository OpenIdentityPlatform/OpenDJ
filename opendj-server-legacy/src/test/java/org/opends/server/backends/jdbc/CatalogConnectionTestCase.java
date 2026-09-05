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

	private ProbeDriver probeDriver;

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
			storageFor(ProbeDriver.URL).newCatalogConnection().close();
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
			storageFor(ProbeDriver.URL).newCatalogConnection().close();
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
			storageFor(ProbeDriver.URL).newCatalogConnection().close();
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
			storageFor(ProbeDriver.URL).newCatalogConnection();
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
		probeDriver.refusal = new SQLException("the database system is starting up", "57P03");
		probeDriver.refusalsLeft.set(Integer.MAX_VALUE);
		try {
			storageFor(ProbeDriver.URL).newCatalogConnection();
			fail("a connect refused for the whole deadline was not given up on");
		} catch (SQLTimeoutException expected) {
			// the state of the driver's own refusal and not one of this code's making: a manufactured
			// 08001 is read by write() as a connection the database dropped, which would replay an
			// attempt whose pooled connection is healthy and distrust the pool over it
			assertEquals(expected.getSQLState(), "57P03",
				"the failure the deadline ended carried another state than the driver's own");
			assertNotNull(expected.getCause(), "the driver's own failure was not carried as the cause");
			assertTrue(probeDriver.attempts.get() > 1,
				"the deadline was reached without the connect having been retried at all");
		} finally {
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
			storageFor(ProbeDriver.URL).newCatalogConnection().close();
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
			final Connection con = storageFor(ProbeDriver.URL).newCatalogConnection();
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
			final Connection con = storageFor(ProbeDriver.URL).newCatalogConnection();
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
			final Connection con = storageFor(ProbeDriver.URL).newCatalogConnection();
			assertNotNull(con, "a connection whose read bound would not come off was not handed back");
			verify(con).setAutoCommit(false);
			verify(con, never()).close();
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
			storageFor(ProbeDriver.URL).newCatalogConnection();
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

		volatile Properties lastProperties;

		/** The connection to answer with, for a test about what is done with it; a fresh mock otherwise. */
		volatile Connection answer;

		/** How many attempts to refuse before answering, and with what; for the cases about the retry. */
		final AtomicInteger refusalsLeft = new AtomicInteger();
		volatile SQLException refusal;

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
				throw refusal;
			}
			return answer != null ? answer : mock(Connection.class);
		}

		@Override
		public boolean acceptsURL(String url) {
			return url != null && url.startsWith(URL);
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
