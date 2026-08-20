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

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.mockito.InOrder;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * The pool every operation of the JDBC backend borrows from must bound both of its phases and
 * report a connect it cannot make, rather than retrying it out of sight of the caller (#872).
 * Needs no database: the dialects are exercised against a socket that never answers and against a
 * driver of this test, so a regression fails the build wherever it runs.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "jdbc" }, sequential = true)
public class CachedConnectionTestCase extends DirectoryServerTestCase {

	/** A connect attempt of a bounded dialect must give up in about this long, plus room for a slow machine. */
	private static final long BOUND_SECONDS = 2;
	private static final long BOUND_MARGIN_MS = 60000;

	private final StubDriver stub = new StubDriver();

	@BeforeClass
	public void registerStubDriver() throws Exception {
		DriverManager.registerDriver(stub);
	}

	@AfterClass
	public void deregisterStubDriver() throws Exception {
		DriverManager.deregisterDriver(stub);
	}

	@AfterMethod
	public void clearProperties() {
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.clearProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
	}

	/**
	 * A driver that is not on the classpath - the JDBC backend needs one dropped into
	 * lib/extensions by hand - is a configuration error the caller has to see. Retried, it is
	 * indistinguishable from a database that hangs.
	 */
	@Test(timeOut = 120000)
	public void testMissingDriverIsReportedAtOnce() throws Exception {
		final long startedAt = System.currentTimeMillis();
		try {
			CachedConnection.getConnection("jdbc:nosuchengine://127.0.0.1:5432/opendj");
			fail("a connection string no registered driver accepts must be reported");
		} catch (SQLException expected) {
			assertTrue(expected.getMessage().contains("No suitable driver"), expected.getMessage());
		}
		assertElapsedWithinBound(startedAt, 0);
	}

	/**
	 * A database that is not listening at all: every dialect reports it instead of retrying the
	 * refused connect until the caller gives up on the operation.
	 */
	@Test(timeOut = 120000)
	public void testRefusedConnectIsReportedAtOnce() throws Exception {
		final int closedPort = closedPort();
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, Long.toString(BOUND_SECONDS));
		for (final String url : urlsOf(closedPort)) {
			final long startedAt = System.currentTimeMillis();
			try {
				CachedConnection.getConnection(url);
				fail("a refused connect must be reported: " + CachedConnection.safeUrl(url));
			} catch (SQLException expected) {
				// the failure of the moment, reported rather than retried
			}
			assertElapsedWithinBound(startedAt, BOUND_SECONDS * 1000);
		}
	}

	/**
	 * The failure this bound exists for: a database that completes the TCP connection and then
	 * says nothing - a moved VIP, a proxy at its connection limit, a host that lost its answer -
	 * leaving the login of the driver, and with it the operation, without an end. The connection
	 * of the accept queue is never answered here, so every dialect has to give up on its own.
	 */
	@Test(timeOut = 300000)
	public void testLoginIsBoundedWhenTheDatabaseNeverAnswers() throws Exception {
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, Long.toString(BOUND_SECONDS));
		// a socket that is bound and never accepted: the kernel completes the handshake, so the
		// connect of the driver succeeds and every read of the login that follows hangs
		try (final ServerSocket blackhole = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			for (final String url : urlsOf(blackhole.getLocalPort())) {
				// borrowed on a thread of its own: a bound that a driver does not honour has to
				// fail this test at once, and not by hanging the run it is part of
				final FutureTask<Connection> borrow = new FutureTask<>(() -> CachedConnection.getConnection(url));
				final Thread thread = new Thread(borrow, "borrow-" + CachedConnection.safeUrl(url));
				thread.setDaemon(true);
				thread.start();
				try {
					final Connection con = borrow.get(BOUND_SECONDS * 1000 + BOUND_MARGIN_MS, TimeUnit.MILLISECONDS);
					fail("a database that never answers must not hand out a connection: " + con);
				} catch (TimeoutException e) {
					fail("the login of " + CachedConnection.safeUrl(url) + " is not bounded: it never gave up");
				} catch (ExecutionException expected) {
					assertTrue(expected.getCause() instanceof SQLException, String.valueOf(expected.getCause()));
				}
			}
		}
	}

	/**
	 * The deadline of the borrow bounds the attempt inside it as well: the pool timeout stands for
	 * the whole borrow, and an attempt left to run out its own bound would overrun it by that bound.
	 */
	@Test(timeOut = 120000)
	public void testTheAttemptIsBoundedByTheDeadlineOfTheBorrow() throws Exception {
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "600");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "2");
		try (final ServerSocket blackhole = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			final String url = "jdbc:postgresql://127.0.0.1:" + blackhole.getLocalPort() + "/opendj?user=opendj&password=opendj";
			final long startedAt = System.currentTimeMillis();
			try {
				CachedConnection.getConnection(url);
				fail("a database that never answers must not hand out a connection");
			} catch (SQLException expected) {
				// reported, and within the borrow it was given rather than the 600 s of the attempt
			}
			final long elapsed = System.currentTimeMillis() - startedAt;
			assertTrue(elapsed < 30000, "the attempt outlived the deadline of the borrow: " + elapsed + " ms");
		}
	}

	/** Pool exhaustion stays a retry - one of our own connections is on its way back to the pool. */
	@Test(timeOut = 120000)
	public void testConnectionLimitIsRetried() throws Exception {
		final String url = StubDriver.PREFIX + "retried";
		stub.failWith(tooManyConnections(), 2);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "30");

		final Connection con = CachedConnection.getConnection(url);

		assertNotNull(con);
		assertEquals(stub.attempts.get(), 3, "the connect must be retried while the database is at its limit");
	}

	/** ... but under a deadline: the retry used to double its wait from 1 ms with no end to it. */
	@Test(timeOut = 120000)
	public void testConnectionLimitGivesUpAtTheDeadline() throws Exception {
		final String url = StubDriver.PREFIX + "deadline";
		stub.failWith(tooManyConnections(), StubDriver.ALWAYS);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "2");

		final long startedAt = System.currentTimeMillis();
		try {
			CachedConnection.getConnection(url);
			fail("a database that stays at its connection limit must be reported, not waited out forever");
		} catch (SQLTimeoutException expected) {
			assertTrue(expected.getMessage().contains("2s"), expected.getMessage());
			assertEquals(((SQLException) expected.getCause()).getSQLState(), "53300");
		}
		final long elapsed = System.currentTimeMillis() - startedAt;
		assertTrue(elapsed >= 2000, "gave up after " + elapsed + " ms, before the deadline it was given");
		assertElapsedWithinBound(startedAt, 2000);
		assertTrue(stub.attempts.get() > 1, "the connect must be retried while the deadline lasts");
	}

	/**
	 * A database on its way up - starting, recovering, shutting down - says so, and says it for
	 * seconds: the backend it belongs to would otherwise stay locked down until the next restart
	 * of the server, since nothing above JDBCStorage.open() attempts it a second time.
	 */
	@Test(timeOut = 120000)
	public void testDatabaseOnItsWayUpIsRetried() throws Exception {
		final String url = StubDriver.PREFIX + "starting-up";
		stub.failWith(new SQLException("the database system is starting up", "57P03"), 2);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "30");

		final Connection con = CachedConnection.getConnection(url);

		assertNotNull(con);
		assertEquals(stub.attempts.get(), 3, "a database that is starting up must be waited out");
	}

	/** ... and it is recognized however the driver wrapped it: a SQLException carries two chains. */
	@Test(timeOut = 120000)
	public void testTheWholeChainOfTheFailureIsLookedAt() throws Exception {
		final String url = StubDriver.PREFIX + "wrapped";
		final SQLException wrapped = new SQLException("could not connect to the server", "08006");
		wrapped.setNextException(tooManyConnections());
		stub.failWith(wrapped, 1);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "30");

		assertNotNull(CachedConnection.getConnection(url));
		assertEquals(stub.attempts.get(), 2, "the failure behind the one reported must be looked at");
	}

	/**
	 * The rest of the insufficient_resources class is not worth waiting out: a server out of disk
	 * is not made whole by a connection of ours coming back to the pool.
	 */
	@Test(timeOut = 120000)
	public void testDiskFullIsNotRetried() throws Exception {
		final String url = StubDriver.PREFIX + "disk-full";
		stub.failWith(new SQLException("could not extend file: No space left on device", "53100"), StubDriver.ALWAYS);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "30");

		try {
			CachedConnection.getConnection(url);
			fail("a database out of disk must be reported to the caller");
		} catch (SQLException expected) {
			assertEquals(expected.getSQLState(), "53100");
		}
		assertEquals(stub.attempts.get(), 1, "a failure that waiting cannot clear must be attempted once");
	}

	/**
	 * Every other failure is the caller's to report. A password the database does not accept is
	 * never going to be accepted by waiting, and the retry that swallowed it left the operation
	 * hanging with nothing in the log.
	 */
	@Test(timeOut = 120000)
	public void testRejectedLoginIsNotRetried() throws Exception {
		final String url = StubDriver.PREFIX + "rejected";
		stub.failWith(new SQLException("password authentication failed", "28P01"), StubDriver.ALWAYS);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "30");

		try {
			CachedConnection.getConnection(url);
			fail("a rejected login must be reported to the caller");
		} catch (SQLException expected) {
			assertEquals(expected.getSQLState(), "28P01");
		}
		assertEquals(stub.attempts.get(), 1, "a rejected login must be attempted once");
	}

	/** A connection the setup of which failed belongs to nobody: it has to be closed, not leaked. */
	@Test(timeOut = 120000)
	public void testConnectionIsClosedWhenItsSetupFails() throws Exception {
		final String url = StubDriver.PREFIX + "setup-failure";
		final Connection broken = mock(Connection.class);
		doThrow(new SQLException("read only")).when(broken).setAutoCommit(false);
		stub.answerWith(broken);

		try {
			CachedConnection.getConnection(url);
			fail("a connection that cannot be set up must be reported");
		} catch (SQLException expected) {
			assertEquals(expected.getMessage(), "read only");
		}
		verify(broken).close();
	}

	/** The same, for a driver whose failure in the setup is not a SQLException but an unchecked one. */
	@Test(timeOut = 120000)
	public void testConnectionIsClosedWhenItsSetupFailsWithAnUncheckedError() throws Exception {
		final String url = StubDriver.PREFIX + "setup-unchecked";
		final Connection broken = mock(Connection.class);
		doThrow(new IllegalStateException("driver internal")).when(broken).setTransactionIsolation(anyInt());
		stub.answerWith(broken);

		try {
			CachedConnection.getConnection(url);
			fail("a connection that cannot be set up must be reported");
		} catch (IllegalStateException expected) {
			assertEquals(expected.getMessage(), "driver internal");
		}
		verify(broken).close();
	}

	/**
	 * isValid(n) is not a bound at the socket on every driver - the SQL Server driver turns it
	 * into a query timeout, which needs an answer from the server to fire - and the read bound of
	 * the login was lifted the moment the connection was established, so the socket carries the
	 * bound of the validation, for the length of the validation only.
	 */
	@Test(timeOut = 120000)
	public void testValidationOfAPooledConnectionIsBoundedAtTheSocket() throws Exception {
		final String url = StubDriver.PREFIX + "validation-bound";
		final Connection pooled = mock(Connection.class);
		when(pooled.isValid(anyInt())).thenReturn(true);
		when(pooled.getNetworkTimeout()).thenReturn(0);
		CachedConnection.cached.get(url).add(new CachedConnection(url, pooled));

		final Connection borrowed = CachedConnection.getConnection(url);

		assertSame(((CachedConnection) borrowed).parent, pooled);
		final InOrder inOrder = inOrder(pooled);
		inOrder.verify(pooled).setNetworkTimeout(any(Executor.class), eq(CachedConnection.VALIDATION_TIMEOUT_SECONDS * 1000));
		inOrder.verify(pooled).isValid(CachedConnection.VALIDATION_TIMEOUT_SECONDS);
		inOrder.verify(pooled).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/** A read bound of the connection string is tighter than ours and stays untouched. */
	@Test(timeOut = 120000)
	public void testValidationLeavesTheBoundOfTheConnectionStringAlone() throws Exception {
		final String url = StubDriver.PREFIX + "validation-tighter";
		final Connection pooled = mock(Connection.class);
		when(pooled.isValid(anyInt())).thenReturn(true);
		when(pooled.getNetworkTimeout()).thenReturn(2000);
		CachedConnection.cached.get(url).add(new CachedConnection(url, pooled));

		assertNotNull(CachedConnection.getConnection(url));

		verify(pooled, never()).setNetworkTimeout(any(Executor.class), anyInt());
	}

	/**
	 * The pool has no upper bound on the number of connections it holds, and a validation is a
	 * round trip: after a failover that left them half-open, draining the pool must not outlive
	 * the deadline of the borrow - establishing a connection is the faster answer past it.
	 */
	@Test(timeOut = 120000)
	public void testDrainOfThePoolStopsAtTheDeadline() throws Exception {
		final String url = StubDriver.PREFIX + "drain-deadline";
		final int pooled = 8;
		final AtomicInteger validated = new AtomicInteger();
		for (int i = 0; i < pooled; i++) {
			final Connection stale = mock(Connection.class);
			when(stale.isValid(anyInt())).thenAnswer(invocation -> {
				validated.incrementAndGet();
				Thread.sleep(500); // a database that no longer answers: every validation waits out its bound
				return false;
			});
			CachedConnection.cached.get(url).add(new CachedConnection(url, stale));
		}
		final Connection fresh = mock(Connection.class);
		stub.answerWith(fresh);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "1");

		final Connection borrowed = CachedConnection.getConnection(url);

		assertSame(((CachedConnection) borrowed).parent, fresh);
		assertTrue(validated.get() < pooled,
			"the whole pool was validated past the deadline: " + validated.get() + " of " + pooled);
	}

	/** A pooled connection that no longer validates is closed and replaced, not handed out. */
	@Test(timeOut = 120000)
	public void testBrokenPooledConnectionIsDiscarded() throws Exception {
		final String url = StubDriver.PREFIX + "broken-pooled";
		final Connection stale = mock(Connection.class);
		when(stale.isValid(anyInt())).thenReturn(false);
		CachedConnection.cached.get(url).add(new CachedConnection(url, stale));
		final Connection fresh = mock(Connection.class);
		when(fresh.isValid(anyInt())).thenReturn(true);
		stub.answerWith(fresh);

		final Connection borrowed = CachedConnection.getConnection(url);

		assertSame(((CachedConnection) borrowed).parent, fresh);
		verify(stale).close();
		// the validation of a pooled connection needs a bound of its own as well
		verify(stale, never()).isValid(0);
		verify(stale).isValid(CachedConnection.VALIDATION_TIMEOUT_SECONDS);
	}

	/** A connection that cannot be rolled back must not go back into the pool - nor be dropped. */
	@Test(timeOut = 120000)
	public void testConnectionThatCannotBeRolledBackIsClosed() throws Exception {
		final String url = StubDriver.PREFIX + "rollback-failure";
		final Connection parent = mock(Connection.class);
		doThrow(new SQLException("connection is closed")).when(parent).rollback();

		try {
			new CachedConnection(url, parent).close();
			fail("a failed rollback must be reported");
		} catch (SQLException expected) {
			assertEquals(expected.getMessage(), "connection is closed");
		}
		verify(parent).close();
		assertTrue(CachedConnection.cached.get(url).isEmpty(), "a connection that cannot be rolled back was pooled");
	}

	@Test
	public void testDialectIsRecognizedByTheConnectionString() throws Exception {
		assertEquals(CachedConnection.ConnectDialect.of("jdbc:postgresql://h:5432/db"), CachedConnection.ConnectDialect.POSTGRES);
		assertEquals(CachedConnection.ConnectDialect.of("jdbc:mysql://h:3306/db"), CachedConnection.ConnectDialect.MYSQL);
		assertEquals(CachedConnection.ConnectDialect.of("jdbc:oracle:thin:@//h:1521/svc"), CachedConnection.ConnectDialect.ORACLE);
		assertEquals(CachedConnection.ConnectDialect.of("jdbc:sqlserver://h:1433;databaseName=db"), CachedConnection.ConnectDialect.MICROSOFT);
		assertNull(CachedConnection.ConnectDialect.of("jdbc:h2:mem:db"), "an unknown engine must not be fed the properties of another");
	}

	/** Both phases are bounded, in the units of the driver: the connect alone leaves the login open. */
	@Test
	public void testBothPhasesOfTheLoginAreBounded() throws Exception {
		// pgjdbc puts an SO_TIMEOUT on the login socket only where socketTimeout is set: without it
		// loginTimeout bounds the caller alone, and the thread the driver runs the login on stays
		// parked in the read it abandoned
		final Properties postgres = new Properties();
		assertTrue(CachedConnection.ConnectDialect.POSTGRES.bound("jdbc:postgresql://h:5432/db", postgres, 7),
			"the read bound of postgresql outlives the login and has to be lifted");
		assertEquals(postgres.getProperty("connectTimeout"), "7");
		assertEquals(postgres.getProperty("socketTimeout"), "7");
		assertEquals(postgres.getProperty("loginTimeout"), "7", "the bound of a url naming more than one host");

		final Properties mysql = new Properties();
		assertTrue(CachedConnection.ConnectDialect.MYSQL.bound("jdbc:mysql://h:3306/db", mysql, 7),
			"the read bound of mysql outlives the login and has to be lifted");
		assertEquals(mysql.getProperty("connectTimeout"), "7000");
		assertEquals(mysql.getProperty("socketTimeout"), "7000");

		final Properties oracle = new Properties();
		assertTrue(CachedConnection.ConnectDialect.ORACLE.bound("jdbc:oracle:thin:@//h:1521/svc", oracle, 7));
		assertEquals(oracle.getProperty("oracle.net.CONNECT_TIMEOUT"), "7000");
		assertEquals(oracle.getProperty("oracle.jdbc.ReadTimeout"), "7000");

		// the loginTimeout of the sql server driver leaves the read of the prelogin answer open
		final Properties microsoft = new Properties();
		assertTrue(CachedConnection.ConnectDialect.MICROSOFT.bound("jdbc:sqlserver://h:1433;databaseName=db", microsoft, 7));
		assertEquals(microsoft.getProperty("loginTimeout"), "7");
		assertEquals(microsoft.getProperty("socketTimeout"), "7000");
	}

	/**
	 * A driver with a range of its own for its connect property is never handed a value beyond it:
	 * SQLServerDriverIntProperty.LOGIN_TIMEOUT is validated against [0, 65535], so a bound past
	 * that would not widen the connect, it would fail every one of them.
	 */
	@Test
	public void testConnectBoundStaysInTheRangeTheDriverTakes() throws Exception {
		final Properties microsoft = new Properties();
		CachedConnection.ConnectDialect.MICROSOFT.bound("jdbc:sqlserver://h:1433;databaseName=db", microsoft, 100000);
		assertEquals(microsoft.getProperty("loginTimeout"), "65535");
		assertEquals(microsoft.getProperty("socketTimeout"), "100000000", "the read bound takes any value");
	}

	/**
	 * A bound the administrator put into the connection string by hand - the only workaround this
	 * backend had - keeps precedence, property by property.
	 */
	@Test
	public void testConnectionStringKeepsPrecedence() throws Exception {
		final Properties postgres = new Properties();
		CachedConnection.ConnectDialect.POSTGRES.bound(
			"jdbc:postgresql://h:5432/db?user=u&password=p&loginTimeout=30&socketTimeout=300", postgres, 7);
		assertNull(postgres.getProperty("loginTimeout"), "the setting of the connection string was overridden");
		assertNull(postgres.getProperty("socketTimeout"), "the setting of the connection string was overridden");
		assertEquals(postgres.getProperty("connectTimeout"), "7", "the property it leaves open must still be bounded");

		// the sql server driver gives a supplied property precedence over the one of the url
		final Properties microsoft = new Properties();
		CachedConnection.ConnectDialect.MICROSOFT.bound("jdbc:sqlserver://h:1433;loginTimeout=45;databaseName=db", microsoft, 7);
		assertNull(microsoft.getProperty("loginTimeout"), "the setting of the connection string was overridden");
		assertEquals(microsoft.getProperty("socketTimeout"), "7000");

		// inside the descriptor of an oracle tns url the property goes by the last segment of its name
		final Properties oracle = new Properties();
		CachedConnection.ConnectDialect.ORACLE.bound(
			"jdbc:oracle:thin:@(DESCRIPTION=(CONNECT_TIMEOUT=3)(ADDRESS=(HOST=h)(PORT=1521)))", oracle, 7);
		assertNull(oracle.getProperty("oracle.net.CONNECT_TIMEOUT"));
		assertEquals(oracle.getProperty("oracle.jdbc.ReadTimeout"), "7000");

		// inside the descriptor the read bound goes by RECV_TIMEOUT, and one of the administrator
		// is never lifted after the login, because ours is not set on top of it
		final Properties recv = new Properties();
		assertFalse(CachedConnection.ConnectDialect.ORACLE.bound(
			"jdbc:oracle:thin:@(DESCRIPTION=(RECV_TIMEOUT=30)(ADDRESS=(HOST=h)(PORT=1521)))", recv, 7),
			"a read bound of the connection string must not be lifted once the login is through");
		assertNull(recv.getProperty("oracle.jdbc.ReadTimeout"));

		// a name that only appears as the tail of another parameter is not a setting of its own
		final Properties mysql = new Properties();
		CachedConnection.ConnectDialect.MYSQL.bound("jdbc:mysql://h:3306/db?xconnectTimeout=1&socketTimeoutX=2", mysql, 7);
		assertEquals(mysql.getProperty("connectTimeout"), "7000");
		assertEquals(mysql.getProperty("socketTimeout"), "7000");
	}

	/**
	 * A parameter is recognized the way the driver of its dialect recognizes it: pgjdbc looks its
	 * properties up by their exact name, so a name of another case is a parameter of nobody and
	 * must not pass for a bound the administrator set - while the other three match either way.
	 */
	@Test
	public void testTheCaseOfAParameterIsTheOneOfItsDriver() throws Exception {
		final Properties postgres = new Properties();
		CachedConnection.ConnectDialect.POSTGRES.bound("jdbc:postgresql://h:5432/db?ConnectTimeout=5", postgres, 7);
		assertEquals(postgres.getProperty("connectTimeout"), "7", "pgjdbc ignores a parameter of another case");

		final Properties mysql = new Properties();
		CachedConnection.ConnectDialect.MYSQL.bound("jdbc:mysql://h:3306/db?SocketTimeout=1", mysql, 7);
		assertNull(mysql.getProperty("socketTimeout"), "Connector/J matches its properties without case");

		final Properties microsoft = new Properties();
		CachedConnection.ConnectDialect.MICROSOFT.bound("jdbc:sqlserver://h:1433;LoginTimeout=45", microsoft, 7);
		assertNull(microsoft.getProperty("loginTimeout"), "the sql server driver normalizes the name of a property");
	}

	/** The connection string holds the credentials of the backend: a stall report must not carry them. */
	@Test
	public void testLoggedConnectionStringCarriesNoCredentials() throws Exception {
		assertEquals(CachedConnection.safeUrl("jdbc:postgresql://h:5432/db?user=u&password=secret"), "jdbc:postgresql://h:5432/db");
		assertEquals(CachedConnection.safeUrl("jdbc:sqlserver://h:1433;databaseName=db;password=secret"), "jdbc:sqlserver://h:1433");
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:scott/secret@//h:1521/svc"), "jdbc:oracle:@//h:1521/svc");
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://u:secret@h:3306/db"), "jdbc:mysql://h:3306/db");
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:@//h:1521/svc"), "jdbc:oracle:@//h:1521/svc");
		// a password holding the parameter separator of another dialect: ";" separates nothing on
		// an oracle url, so the credentials are cut in front of the "@" rather than inside them
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:scott/pa;ss@//h:1521/svc"), "jdbc:oracle:@//h:1521/svc");
		// ... and an "@" that stands inside a parameter is not the end of credentials: the host survives
		assertEquals(CachedConnection.safeUrl("jdbc:postgresql://h:5432/db?user=u@example.com&password=secret"),
			"jdbc:postgresql://h:5432/db");
		// a password holding the parameter separator of its own dialect: on an oracle url the
		// parameters stand behind the descriptor, so a "?" in front of the "@" is part of the
		// password and cutting there would leave the start of it in the log
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:scott/pa?ss@//h:1521/svc"), "jdbc:oracle:@//h:1521/svc");
		// the same inside an authority, where the credentials end at the path rather than at a "?"
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://u:sec?ret@h:3306/db"), "jdbc:mysql://h:3306/db");
	}

	/**
	 * pgjdbc enforces loginTimeout out of process: Driver.connect hands the login to a daemon
	 * thread of its own and gives up on the thread rather than on the login. Against the database
	 * this bound exists for - one that completes the handshake and then says nothing - an
	 * unbounded read there leaves that thread, and the socket it holds, behind on every borrow;
	 * a few operations a second are enough to run the server out of threads and file descriptors.
	 */
	@Test(timeOut = 300000)
	public void testTheLoginThreadOfPostgresDoesNotOutliveTheBorrow() throws Exception {
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, Long.toString(BOUND_SECONDS));
		try (final ServerSocket blackhole = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			final String url = "jdbc:postgresql://127.0.0.1:" + blackhole.getLocalPort() + "/opendj?user=opendj&password=opendj";
			try {
				CachedConnection.getConnection(url);
				fail("a database that never answers must not hand out a connection");
			} catch (SQLException expected) {
				// reported to the caller, as the bound of the attempt promises
			}
			final long giveUpAt = System.currentTimeMillis() + BOUND_SECONDS * 1000 + BOUND_MARGIN_MS;
			while (loginThreadsOfPostgres() > 0 && System.currentTimeMillis() < giveUpAt) {
				Thread.sleep(100);
			}
			assertEquals(loginThreadsOfPostgres(), 0,
				"the login thread pgjdbc abandoned outlived the borrow: the read of the login is not bounded");
		}
	}

	private static int loginThreadsOfPostgres() {
		int alive = 0;
		for (final Thread thread : Thread.getAllStackTraces().keySet()) {
			if (thread.isAlive() && thread.getName().startsWith("PostgreSQL JDBC driver connection thread")) {
				alive++;
			}
		}
		return alive;
	}

	/**
	 * The deadline of the borrow stands for the whole borrow, so it bounds the attempt inside it
	 * even where the per-attempt property gives it no bound of its own: turning that property off
	 * must not turn the bound of the borrow off with it.
	 */
	@Test(timeOut = 300000)
	public void testTheDeadlineBoundsAnAttemptTheConnectPropertyDoesNot() throws Exception {
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "0");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "2");
		try (final ServerSocket blackhole = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			final String url = "jdbc:postgresql://127.0.0.1:" + blackhole.getLocalPort() + "/opendj?user=opendj&password=opendj";
			final long startedAt = System.currentTimeMillis();
			try {
				CachedConnection.getConnection(url);
				fail("a database that never answers must not hand out a connection");
			} catch (SQLException expected) {
				// bounded by what is left of the deadline of the borrow
			}
			assertElapsedWithinBound(startedAt, 2000);
		}
	}

	/**
	 * The deadline stops the drain of the pool; it does not throw away the connection in hand. A
	 * database at its connection limit has no other source of connections than the ones coming
	 * back to the pool, and closing one unvalidated takes it out of that source for good - while
	 * the borrow that closed it fails with a timeout anyway.
	 */
	@Test(timeOut = 120000)
	public void testAPooledConnectionIsNotDiscardedUnvalidatedAtTheDeadline() throws Exception {
		final String url = StubDriver.PREFIX + "unvalidated-at-deadline";
		final Connection stale = mock(Connection.class);
		when(stale.isValid(anyInt())).thenAnswer(invocation -> {
			Thread.sleep(1500); // a database that no longer answers: the validation waits out its bound
			return false;
		});
		final Connection good = mock(Connection.class);
		when(good.isValid(anyInt())).thenReturn(true);
		CachedConnection.cached.get(url).add(new CachedConnection(url, stale));
		CachedConnection.cached.get(url).add(new CachedConnection(url, good));
		final Connection fresh = mock(Connection.class);
		stub.answerWith(fresh);
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "1");

		final Connection borrowed = CachedConnection.getConnection(url);

		assertSame(((CachedConnection) borrowed).parent, fresh, "the drain must stop at the deadline");
		verify(stale).close();
		verify(good, never()).close();
		assertFalse(CachedConnection.cached.get(url).isEmpty(), "a connection the deadline was reached in front of was lost");
	}

	/**
	 * A connection the validation of which failed is on its way out, and its driver knows it:
	 * Connector/J answers a failed validation by aborting the connection and the SQL Server driver
	 * by terminating it. Putting the previous bound back on it fails, and warns about statements
	 * of a connection that is being closed - over an idle connection the server reaped, which is
	 * nobody's problem.
	 */
	@Test(timeOut = 120000)
	public void testAConnectionOnItsWayOutIsNotGivenItsBoundBack() throws Exception {
		final String url = StubDriver.PREFIX + "reaped-idle";
		final Connection reaped = mock(Connection.class);
		when(reaped.getNetworkTimeout()).thenReturn(0);
		when(reaped.isValid(anyInt())).thenReturn(false);
		CachedConnection.cached.get(url).add(new CachedConnection(url, reaped));
		final Connection fresh = mock(Connection.class);
		stub.answerWith(fresh);

		assertSame(((CachedConnection) CachedConnection.getConnection(url)).parent, fresh);

		verify(reaped).setNetworkTimeout(any(Executor.class), eq(CachedConnection.VALIDATION_TIMEOUT_SECONDS * 1000));
		verify(reaped, never()).setNetworkTimeout(any(Executor.class), eq(0));
		verify(reaped).close();
	}

	/**
	 * The read bound of the login is lifted once the login is through, because left in place it
	 * fails every statement slower than it. A driver that will not take it back leaves a
	 * connection that must not be pooled: it would carry that bound into every borrow the pool
	 * hands it to, an import batch among them.
	 */
	@Test(timeOut = 120000)
	public void testAConnectionStillCarryingTheBoundOfItsLoginIsNotPooled() throws Exception {
		final String url = StubDriver.PREFIX + "unliftable-bound";
		final Connection parent = mock(Connection.class);
		doThrow(new SQLException("setNetworkTimeout is not supported"))
			.when(parent).setNetworkTimeout(any(Executor.class), eq(0));
		stub.answerWith(parent);

		final CachedConnection borrowed = CachedConnection.connect(url, CachedConnection.ConnectDialect.MYSQL, 30);
		borrowed.close();

		verify(parent).close();
		assertTrue(CachedConnection.cached.get(url).isEmpty(),
			"a connection still carrying the read bound of its login went back into the pool");
	}

	private static SQLException tooManyConnections() {
		// 53300, too_many_connections, of the insufficient_resources class
		return new SQLException("sorry, too many clients already", "53300");
	}

	/** A connection string of every dialect pointing at one host and port. */
	private static String[] urlsOf(int port) {
		return new String[]{
			"jdbc:postgresql://127.0.0.1:" + port + "/opendj?user=opendj&password=opendj",
			"jdbc:mysql://127.0.0.1:" + port + "/opendj?user=opendj&password=opendj",
			"jdbc:oracle:thin:opendj/opendj@//127.0.0.1:" + port + "/free",
			"jdbc:sqlserver://127.0.0.1:" + port + ";databaseName=opendj;user=opendj;password=opendj;encrypt=false"
		};
	}

	private static int closedPort() throws Exception {
		try (final ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			return socket.getLocalPort();
		} // closed again: nothing listens there any more
	}

	private static void assertElapsedWithinBound(long startedAt, long boundMs) {
		final long elapsed = System.currentTimeMillis() - startedAt;
		assertTrue(elapsed < boundMs + BOUND_MARGIN_MS, "gave up only after " + elapsed + " ms");
	}

	/**
	 * Stands in for a database whose answer to a connect is the point of the test: the vendor
	 * codes and SQL states below are what the retry has to tell apart, and no engine is needed to
	 * produce them.
	 */
	private static final class StubDriver implements Driver {
		static final String PREFIX = "jdbc:opendj-stub:";
		static final int ALWAYS = -1;

		final AtomicInteger attempts = new AtomicInteger();
		private volatile SQLException failure;
		private volatile int failuresLeft;
		private volatile Connection answer;

		void failWith(SQLException failure, int times) {
			this.failure = failure;
			this.failuresLeft = times;
			this.answer = null;
			this.attempts.set(0);
		}

		void answerWith(Connection answer) {
			this.failure = null;
			this.failuresLeft = 0;
			this.answer = answer;
			this.attempts.set(0);
		}

		@Override
		public Connection connect(String url, Properties info) throws SQLException {
			if (!acceptsURL(url)) {
				return null;
			}
			attempts.incrementAndGet();
			if (failuresLeft != 0) {
				if (failuresLeft > 0) {
					failuresLeft--;
				}
				throw failure;
			}
			if (answer != null) {
				return answer;
			}
			final Connection con = mock(Connection.class);
			when(con.isValid(anyInt())).thenReturn(true);
			return con;
		}

		@Override
		public boolean acceptsURL(String url) {
			return url != null && url.startsWith(PREFIX);
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
		public Logger getParentLogger() {
			return Logger.getLogger(StubDriver.class.getName());
		}
	}
}
