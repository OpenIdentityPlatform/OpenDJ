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
	 * ... and the report of it carries no password. This is the path the credentials leave by: the
	 * jdk itself builds "No suitable driver found for " + url, a missing driver jar is the ordinary
	 * oracle misconfiguration, and what this class throws reaches the server error log in full -
	 * JDBCStorage.open() hands it to RootContainer, which makes the message of the cause the
	 * message of what it throws, and BackendConfigManager logs that at ERROR and answers a config
	 * change with it. Every link of the chain is asserted, not only the message on top: everything
	 * that prints a failure prints its causes along with it.
	 */
	@Test(timeOut = 120000)
	public void testAReportedConnectCarriesNoCredentials() throws Exception {
		final String url = "jdbc:nosuchengine://opendj:S3cretOfTheBackend@127.0.0.1:5432/opendj";
		try {
			CachedConnection.getConnection(url);
			fail("a connection string no registered driver accepts must be reported");
		} catch (SQLException expected) {
			assertNoCredentials(expected);
			assertTrue(expected.getMessage().contains("No suitable driver"),
				"the failure has to stay recognizable: " + expected.getMessage());
			assertTrue(expected.getMessage().contains("127.0.0.1:5432"),
				"the host is what a report of a connect is read for: " + expected.getMessage());
		}
	}

	/**
	 * A url this class knows no timeout properties for is reported once. The properties bounding a
	 * connect are the ones of a driver, so a driver outside the four - an admin-added mariadb, an
	 * h2 - leaves every attempt unbounded, and the deadline of the borrow cannot reach into a
	 * connect already under way: the driver is the only thing holding the socket. Silently, that
	 * is #872 again, for a backend nobody thinks of as unbounded.
	 */
	@Test(timeOut = 120000)
	public void testAUrlThisBackendCannotBoundIsReportedOnce() throws Exception {
		final String url = "jdbc:nosuchengine-unbounded://127.0.0.1:5432/opendj";
		try {
			CachedConnection.getConnection(url);
			fail("a connection string no registered driver accepts must be reported");
		} catch (SQLException expected) {
			// the point of the test is what was logged on the way, not what came back
		}
		assertTrue(CachedConnection.warnedOnce.contains(CachedConnection.safeUrl(url) + "|unknown-dialect"),
			"a connection string no bound of this class can reach was not reported");
	}

	/**
	 * ... and so is a postgresql url that turns the read bound off: a parameter of one outranks the
	 * property this class supplies, so a "socketTimeout=0" there cannot be replaced. Nothing is
	 * left to end a borrow that reaches a database accepting the connection and answering nothing,
	 * and an administrator who wrote that zero has to be able to find it in the log.
	 */
	@Test
	public void testAReadBoundTurnedOffInAPostgresUrlIsReportedOnce() throws Exception {
		final String url = "jdbc:postgresql://reported:5432/db?socketTimeout=0";
		assertFalse(CachedConnection.ConnectDialect.POSTGRES.bound(url, new Properties(), 7));
		assertTrue(CachedConnection.warnedOnce.contains(CachedConnection.safeUrl(url) + "|unbounded-read"),
			"a url leaving the reads of its login unbounded was not reported");
	}

	/** Nothing in the chain of a failure names the password of the backend, however deep it stands. */
	private static void assertNoCredentials(Throwable failure) {
		for (Throwable t = failure; t != null; t = t.getCause()) {
			assertFalse(String.valueOf(t.getMessage()).contains("S3cretOfTheBackend"),
				"the password of the backend reached a message: " + t);
			if (t instanceof SQLException) {
				for (SQLException next = ((SQLException) t).getNextException(); next != null;
						next = next.getNextException()) {
					assertFalse(String.valueOf(next.getMessage()).contains("S3cretOfTheBackend"),
						"the password of the backend reached a message: " + next);
				}
			}
			if (t.getCause() == t) {
				break;
			}
		}
	}

	/**
	 * A link of a chain carries a cause and a next exception both, and a driver is free to make the
	 * two the same failure. Rebuilt under a bound on the depth alone, a chain of those is copied
	 * twice over at every step - 2^32 links for one reaching the bound, which is a report of a
	 * failed connect that never comes back. What bounds the redaction is the number of links it
	 * rebuilds, the way the walk looking for credentials is bounded by the ones it visits.
	 */
	@Test(timeOut = 60000)
	public void testAFailureWhoseCauseIsItsNextExceptionIsRedactedInBoundedTime() throws Exception {
		final String url = "jdbc:postgresql://opendj:S3cretOfTheBackend@127.0.0.1:5432/opendj";
		SQLException chain = new SQLException("connect to " + url + " failed", "08006", 1);
		for (int i = 0; i < 64; i++) {
			final SQLException link = new SQLException("link " + i + " of " + url, "08006", i);
			link.setNextException(chain);
			link.initCause(chain);
			chain = link;
		}
		final SQLException reported = CachedConnection.reported(chain, url);
		assertNoCredentials(reported);
		assertEquals(reported.getSQLState(), "08006", "the SQLState of a link has to survive its redaction");
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
			// the state of a connect that did not happen, rather than none at all: this is the
			// failure of a borrow, and monitoring reading the state off what it caught would
			// otherwise see null where the driver's own exception carried one
			assertEquals(expected.getSQLState(), "08001");
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

	/**
	 * A driver that takes the call bounding the validation and then fails inside it is told apart
	 * from one that never takes a network timeout at all: it is free to have applied the bound
	 * before failing, so the connection is discarded rather than validated unbounded and handed
	 * out - pooled, it would carry five seconds of ours into every statement for the rest of its
	 * life, and the import batch of a backend open is the first thing to die of that.
	 */
	@Test(timeOut = 120000)
	public void testAPooledConnectionThatCannotBeBoundedForItsValidationIsDiscarded() throws Exception {
		final String url = StubDriver.PREFIX + "validation-bound-fails";
		final Connection pooled = mock(Connection.class);
		when(pooled.getNetworkTimeout()).thenReturn(0);
		when(pooled.isValid(anyInt())).thenReturn(true);
		doThrow(new SQLException("the driver took the bound and then failed"))
			.when(pooled).setNetworkTimeout(any(Executor.class), anyInt());
		CachedConnection.cached.get(url).add(new CachedConnection(url, pooled));
		final Connection fresh = mock(Connection.class);
		stub.answerWith(fresh);

		final Connection borrowed = CachedConnection.getConnection(url);

		assertSame(((CachedConnection) borrowed).parent, fresh, "a connection this could not bound was handed out");
		verify(pooled, never()).isValid(anyInt());
		verify(pooled).close();
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
		assertTrue(validated.get() > 0 && validated.get() < pooled,
			"the drain has to start and to stop at the deadline: " + validated.get() + " of " + pooled);
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

	/**
	 * The same for a driver whose answer to the validation is an unchecked failure: it unwinds
	 * through poll(), which stands outside every try of the borrow, so the connection it was
	 * raised over is already out of the pool and would be held by nobody.
	 */
	@Test(timeOut = 120000)
	public void testAPooledConnectionWhoseValidationThrowsIsDiscarded() throws Exception {
		final String url = StubDriver.PREFIX + "validation-unchecked";
		final Connection broken = mock(Connection.class);
		when(broken.isValid(anyInt())).thenThrow(new IllegalStateException("driver internal"));
		CachedConnection.cached.get(url).add(new CachedConnection(url, broken));
		final Connection fresh = mock(Connection.class);
		stub.answerWith(fresh);

		final Connection borrowed = CachedConnection.getConnection(url);

		assertSame(((CachedConnection) borrowed).parent, fresh);
		verify(broken).close();
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
		assertNull(postgres.getProperty("connectTimeout"),
			"the connect side is one budget: a bound of the administrator under either of its names is theirs");

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

		// inside the descriptor the read bound goes by READ_TIMEOUT, and one of the administrator
		// is never lifted after the login, because ours is not set on top of it
		final Properties read = new Properties();
		assertFalse(CachedConnection.ConnectDialect.ORACLE.bound(
			"jdbc:oracle:thin:@(DESCRIPTION=(READ_TIMEOUT=30)(ADDRESS=(HOST=h)(PORT=1521)))", read, 7),
			"a read bound of the connection string must not be lifted once the login is through");
		assertNull(read.getProperty("oracle.jdbc.ReadTimeout"));

		// ... while RECV_TIMEOUT is a parameter of sqlnet.ora and of the listener that ojdbc8 does
		// not read - the name appears nowhere in the driver - so a descriptor carrying one is not
		// a read bound of the connection and must not take ours off it
		final Properties recv = new Properties();
		assertTrue(CachedConnection.ConnectDialect.ORACLE.bound(
			"jdbc:oracle:thin:@(DESCRIPTION=(RECV_TIMEOUT=30)(ADDRESS=(HOST=h)(PORT=1521)))", recv, 7),
			"a parameter no driver reads left the login of this connection unbounded");
		assertEquals(recv.getProperty("oracle.jdbc.ReadTimeout"), "7000");

		// a name that only appears as the tail of another parameter is not a setting of its own
		final Properties mysql = new Properties();
		CachedConnection.ConnectDialect.MYSQL.bound("jdbc:mysql://h:3306/db?xconnectTimeout=1&socketTimeoutX=2", mysql, 7);
		assertEquals(mysql.getProperty("connectTimeout"), "7000");
		assertEquals(mysql.getProperty("socketTimeout"), "7000");
	}

	/**
	 * A parameter is recognized the way the driver of its dialect recognizes it: pgjdbc and
	 * Connector/J look their properties up by their exact name, so a name of another case is a
	 * parameter of nobody - neither side bounds anything by it - and must not pass for a bound the
	 * administrator set, while the other two match either way.
	 */
	@Test
	public void testTheCaseOfAParameterIsTheOneOfItsDriver() throws Exception {
		final Properties postgres = new Properties();
		CachedConnection.ConnectDialect.POSTGRES.bound("jdbc:postgresql://h:5432/db?ConnectTimeout=5", postgres, 7);
		assertEquals(postgres.getProperty("connectTimeout"), "7", "pgjdbc ignores a parameter of another case");

		// PropertyKey.fromValue("SocketTimeout") answers null, and Connector/J then reads no bound
		// out of the url either: a mis-cased parameter left the borrower parked on a host that
		// completes the handshake and says nothing
		final Properties mysql = new Properties();
		CachedConnection.ConnectDialect.MYSQL.bound("jdbc:mysql://h:3306/db?SocketTimeout=1", mysql, 7);
		assertEquals(mysql.getProperty("socketTimeout"), "7000", "Connector/J ignores a parameter of another case");

		final Properties microsoft = new Properties();
		CachedConnection.ConnectDialect.MICROSOFT.bound("jdbc:sqlserver://h:1433;LoginTimeout=45", microsoft, 7);
		assertNull(microsoft.getProperty("loginTimeout"), "the sql server driver normalizes the name of a property");

		// the keywords of an oracle descriptor are matched without regard to case as well
		final Properties oracle = new Properties();
		CachedConnection.ConnectDialect.ORACLE.bound(
			"jdbc:oracle:thin:@(description=(connect_timeout=3)(address=(host=h)(port=1521)))", oracle, 7);
		assertNull(oracle.getProperty("oracle.net.CONNECT_TIMEOUT"), "an oracle descriptor is read without case");
	}

	/**
	 * The connection string is not the only channel of the administrator: the oracle driver reads
	 * some of its properties out of the system properties as well, which is how a whole jvm is
	 * bounded with -Doracle.jdbc.ReadTimeout. A property supplied to the driver outranks that one
	 * without a word, and this class would then lift it once the login is through as if it were
	 * its own - leaving a connection with no read bound at all where the administrator set one.
	 */
	@Test
	public void testASystemPropertyOfTheAdministratorKeepsPrecedence() throws Exception {
		System.setProperty("oracle.jdbc.ReadTimeout", "30000");
		try {
			final Properties oracle = new Properties();
			assertFalse(CachedConnection.ConnectDialect.ORACLE.bound("jdbc:oracle:thin:@//h:1521/svc", oracle, 7),
				"a read bound of the administrator must not be lifted once the login is through");
			assertNull(oracle.getProperty("oracle.jdbc.ReadTimeout"), "the setting of the administrator was overridden");
			assertEquals(oracle.getProperty("oracle.net.CONNECT_TIMEOUT"), "7000",
				"the property it leaves open must still be bounded");
		} finally {
			System.clearProperty("oracle.jdbc.ReadTimeout");
		}

		// the connect property of the same driver, over the same channel
		System.setProperty("oracle.net.CONNECT_TIMEOUT", "30000");
		try {
			final Properties oracle = new Properties();
			CachedConnection.ConnectDialect.ORACLE.bound("jdbc:oracle:thin:@//h:1521/svc", oracle, 7);
			assertNull(oracle.getProperty("oracle.net.CONNECT_TIMEOUT"), "the setting of the administrator was overridden");
		} finally {
			System.clearProperty("oracle.net.CONNECT_TIMEOUT");
		}

		// a plain name is common enough to be somebody else's system property: only a name a
		// driver of these actually reads out of them is one of the administrator's
		System.setProperty("socketTimeout", "30000");
		try {
			final Properties mysql = new Properties();
			assertTrue(CachedConnection.ConnectDialect.MYSQL.bound("jdbc:mysql://h:3306/db", mysql, 7));
			assertEquals(mysql.getProperty("socketTimeout"), "7000");
		} finally {
			System.clearProperty("socketTimeout");
		}
	}

	/**
	 * ... and a dotted name is not a name a driver reads out of the system properties by the shape
	 * of it. ojdbc8 resolves oracle.jdbc.ReadTimeout and oracle.net.CONNECT_TIMEOUT in three tiers
	 * (the properties it was supplied, then System.getProperty, then the properties of the data
	 * source), while oracle.net.READ_TIMEOUT - a dotted name of the same driver, and the name the
	 * socket option is finally read under - reaches the socket from the connection properties
	 * alone: the classes carrying the literal hand it to Properties.get, none of them to
	 * System.getProperty. Taken for a bound of the administrator, a -D of it leaves the login with
	 * no read bound whatever: theirs is not read and ours is not set.
	 */
	@Test
	public void testASystemPropertyNoDriverReadsIsNoBound() throws Exception {
		System.setProperty("oracle.net.READ_TIMEOUT", "30000");
		try {
			final Properties oracle = new Properties();
			assertTrue(CachedConnection.ConnectDialect.ORACLE.bound("jdbc:oracle:thin:@//h:1521/svc", oracle, 7),
				"a -D the driver never reads left this login with no read bound at all");
			assertEquals(oracle.getProperty("oracle.jdbc.ReadTimeout"), "7000");
		} finally {
			System.clearProperty("oracle.net.READ_TIMEOUT");
		}

		// ... while the same name written into the connection string is one the driver does read
		final Properties declared = new Properties();
		assertFalse(CachedConnection.ConnectDialect.ORACLE.bound(
			"jdbc:oracle:thin:@(DESCRIPTION=(READ_TIMEOUT=30)(ADDRESS=(HOST=h)(PORT=1521)))", declared, 7));
		assertNull(declared.getProperty("oracle.jdbc.ReadTimeout"));
	}

	/**
	 * A property the administrator set to 0 is not a bound of theirs: every one of these drivers
	 * reads 0 as "wait as long as it takes", which is the default this class exists to replace -
	 * and on the three whose driver lets a supplied property win, ours is set on top of it.
	 * Postgresql is the one where it cannot be, and is covered on its own below.
	 */
	@Test
	public void testAZeroIsNotABoundOfTheAdministrator() throws Exception {
		final Properties mysql = new Properties();
		CachedConnection.ConnectDialect.MYSQL.bound("jdbc:mysql://h:3306/db?connectTimeout=0&socketTimeout=0", mysql, 7);
		assertEquals(mysql.getProperty("connectTimeout"), "7000");
		assertEquals(mysql.getProperty("socketTimeout"), "7000");

		// ... and neither is a property left without a value
		final Properties microsoft = new Properties();
		CachedConnection.ConnectDialect.MICROSOFT.bound("jdbc:sqlserver://h:1433;socketTimeout=;databaseName=db", microsoft, 7);
		assertEquals(microsoft.getProperty("socketTimeout"), "7000");

		// the same of a system property, and of the descriptor of an oracle url
		System.setProperty("oracle.jdbc.ReadTimeout", "0");
		try {
			final Properties oracle = new Properties();
			assertTrue(CachedConnection.ConnectDialect.ORACLE.bound(
				"jdbc:oracle:thin:@(DESCRIPTION=(READ_TIMEOUT=0)(ADDRESS=(HOST=h)(PORT=1521)))", oracle, 7));
			assertEquals(oracle.getProperty("oracle.jdbc.ReadTimeout"), "7000");
		} finally {
			System.clearProperty("oracle.jdbc.ReadTimeout");
		}

		// a value that is no number is left to the driver it belongs to: it is not this class's to read
		final Properties unreadable = new Properties();
		assertFalse(CachedConnection.ConnectDialect.MYSQL.bound(
			"jdbc:mysql://h:3306/db?socketTimeout=PT30S", unreadable, 7));
		assertNull(unreadable.getProperty("socketTimeout"));
	}

	/**
	 * On postgresql a parameter of the url outranks the property this class supplies: Driver
	 * .connect copies what it was handed into a flat map and parseURL then writes the parameters of
	 * the url on top of it. So a "socketTimeout=0" there cannot be replaced, and setting ours
	 * regardless would leave this class believing it bounded a login that carries no bound - and
	 * lifting a read bound after it that was never in force. The effective values are read back
	 * through the parser of the driver itself, since asserting on the map handed to it is
	 * asserting on the half of the story this bug lived in.
	 */
	@Test
	public void testAParameterOfAPostgresUrlOutranksTheBoundOfThisClass() throws Exception {
		final String url = "jdbc:postgresql://h:5432/db?connectTimeout=0&socketTimeout=0&loginTimeout=0";
		final Properties supplied = new Properties();
		assertFalse(CachedConnection.ConnectDialect.POSTGRES.bound(url, supplied, 7),
			"a read bound that never reaches the driver must not be reported as one to lift");
		assertNull(supplied.getProperty("socketTimeout"));
		assertNull(supplied.getProperty("connectTimeout"));
		assertNull(supplied.getProperty("loginTimeout"));

		final Properties effective = org.postgresql.Driver.parseURL(url, supplied);
		assertEquals(effective.getProperty("socketTimeout"), "0", "the url is what the driver ends up reading");
		assertEquals(effective.getProperty("connectTimeout"), "0");
		assertEquals(effective.getProperty("loginTimeout"), "0");
	}

	/**
	 * The connect side of a dialect is one budget rather than a set of independent knobs, so a
	 * bound of the administrator under any of its names leaves all of them alone. On postgresql
	 * connectTimeout bounds the socket connect and loginTimeout the login behind it: filling in the
	 * one they left out caps the one they set, since Driver.connect hands the login to a thread of
	 * its own as soon as loginTimeout is anything but 0 and gives up on it there.
	 */
	@Test
	public void testAConnectBudgetOfTheAdministratorIsNotCappedByThisClass() throws Exception {
		final String url = "jdbc:postgresql://h:5432/db?connectTimeout=300";
		final Properties supplied = new Properties();
		assertTrue(CachedConnection.ConnectDialect.POSTGRES.bound(url, supplied, 30),
			"the read bound is a budget of its own and is still set");
		assertNull(supplied.getProperty("loginTimeout"),
			"a loginTimeout of ours caps the connectTimeout the administrator set");
		assertNull(supplied.getProperty("connectTimeout"));
		assertEquals(supplied.getProperty("socketTimeout"), "30");

		final Properties effective = org.postgresql.Driver.parseURL(url, supplied);
		assertEquals(effective.getProperty("connectTimeout"), "300", "the budget of the administrator, in full");
		assertNull(effective.getProperty("loginTimeout"), "nothing of ours hands this login to a thread to abandon");
	}

	/**
	 * The bound handed to a driver stays inside the range an int of milliseconds takes. Where the
	 * per-attempt property is off, the attempt takes what is left of the deadline of the borrow,
	 * and the pool timeout has no upper bound of its own - while the SQL Server driver rejects a
	 * socketTimeout past Integer.MAX_VALUE outright, failing every connect of that backend with
	 * the name of a property nobody typed.
	 */
	@Test(timeOut = 120000)
	public void testTheBoundHandedToADriverStaysInTheRangeAnIntTakes() throws Exception {
		final long deadline = System.currentTimeMillis() + 3000000L * 1000; // 34 days: past 2^31 ms
		assertEquals(CachedConnection.attemptSeconds(0, deadline), Integer.MAX_VALUE / 1000);

		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "0");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "3000000");
		// a socket that answers the connect and closes it, rather than a port bound and released:
		// with every bound of this borrow turned off, anything taking that port in between would
		// leave the test hanging on the timeOut instead of failing
		try (final ServerSocket rejecting = rejectingSocket()) {
			final String url = "jdbc:sqlserver://127.0.0.1:" + rejecting.getLocalPort()
				+ ";databaseName=opendj;user=opendj;password=opendj;encrypt=false";
			final long startedAt = System.currentTimeMillis();
			try {
				CachedConnection.getConnection(url);
				fail("a connect the database closes must be reported");
			} catch (SQLException expected) {
				assertFalse(expected.getMessage().contains("socketTimeout"),
					"the driver was handed a bound it does not take: " + expected.getMessage());
			}
			assertElapsedWithinBound(startedAt, 0);
		}
	}

	/** The clamp of the range holds where the borrow has no deadline to take it from either. */
	@Test
	public void testTheBoundOfAnAttemptStaysInRangeWithoutADeadline() throws Exception {
		assertEquals(CachedConnection.attemptSeconds(Long.MAX_VALUE, Long.MAX_VALUE), Integer.MAX_VALUE / 1000);
		assertEquals(CachedConnection.attemptSeconds(0, Long.MAX_VALUE), 0, "0 stands for an attempt with no bound");
		assertEquals(CachedConnection.attemptSeconds(30, Long.MAX_VALUE), 30);
	}

	/** The connection string holds the credentials of the backend: a stall report must not carry them. */
	@Test
	public void testLoggedConnectionStringCarriesNoCredentials() throws Exception {
		assertEquals(CachedConnection.safeUrl("jdbc:postgresql://h:5432/db?user=u&password=secret"), "jdbc:postgresql://h:5432/db");
		// the parameter naming the database stays: two backends of one sql server host answer to
		// the same url up to it, and a stall report that cannot tell them apart is one of neither
		assertEquals(CachedConnection.safeUrl("jdbc:sqlserver://h:1433;databaseName=db;password=secret"),
			"jdbc:sqlserver://h:1433;databaseName=db");
		// ... and the token naming the kind of oracle driver stays as well: thin against oci is a
		// first question of an oracle connect, and it stands in front of the credentials
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:scott/secret@//h:1521/svc"), "jdbc:oracle:thin:@//h:1521/svc");
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://u:secret@h:3306/db"), "jdbc:mysql://h:3306/db");
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:@//h:1521/svc"), "jdbc:oracle:thin:@//h:1521/svc");
		// a password holding the parameter separator of another dialect: ";" separates nothing on
		// an oracle url, so the credentials are cut in front of the "@" rather than inside them
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:scott/pa;ss@//h:1521/svc"), "jdbc:oracle:thin:@//h:1521/svc");
		// ... and one holding a ":" is cut in front of it too: the token of the driver is the one
		// behind the subprotocol, not the last one standing in front of the "@"
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:scott/pa:ss@//h:1521/svc"), "jdbc:oracle:thin:@//h:1521/svc");
		// ... and an "@" that stands inside a parameter is not the end of credentials: the host survives
		assertEquals(CachedConnection.safeUrl("jdbc:postgresql://h:5432/db?user=u@example.com&password=secret"),
			"jdbc:postgresql://h:5432/db");
		// a password holding the parameter separator of its own dialect: on an oracle url the
		// parameters stand behind the descriptor, so a "?" in front of the "@" is part of the
		// password and cutting there would leave the start of it in the log
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:scott/pa?ss@//h:1521/svc"), "jdbc:oracle:thin:@//h:1521/svc");
		// the same inside an authority, where the credentials end at the path rather than at a "?"
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://u:sec?ret@h:3306/db"), "jdbc:mysql://h:3306/db");
		// a descriptor of an oracle url carries no credentials and survives whole
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=h)(PORT=1521)))"),
			"jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=h)(PORT=1521)))");
		// a first host with nothing in front of the comma keeps the comma: what is left is the
		// hosts of a url, not one host of it
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://,h2:3306/db"), "jdbc:mysql://,h2:3306/db");
		// a password under a name of its own, and one numbered by the factor it belongs to
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://(host=h,user=u,password2=secret)/db"),
			"jdbc:mysql://(host=h,user=u,password2=***)/db");

		// a url of Connector/J gives every host of it credentials of its own, and every one of
		// them goes: the second used to stay in the message with the password of the failover host
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://u:p@h1:3306,u2:p2@h2:3306/db"),
			"jdbc:mysql://h1:3306,h2:3306/db");
		// ... including a url whose subprotocol names the kind of connection in front of the hosts
		assertEquals(CachedConnection.safeUrl("jdbc:mysql:replication://master:p1@h1:3306,slave:p2@h2:3306/db"),
			"jdbc:mysql:replication://h1:3306,h2:3306/db");
		// the key-value host syntax of Connector/J puts the credentials inside the authority, where
		// neither the userinfo nor the parameters of a url stand
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://address=(host=h)(port=3306)(user=u)(password=secret)/db"),
			"jdbc:mysql://address=(host=h)(port=3306)(user=u)(password=***)/db");
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://(host=h,port=3306,user=u,password=secret)/db"),
			"jdbc:mysql://(host=h,port=3306,user=u,password=***)/db");
		assertEquals(CachedConnection.safeUrl("jdbc:sqlserver://h:1433;databaseName=db;PWD=secret"),
			"jdbc:sqlserver://h:1433;databaseName=db");

		// a shape none of this took apart is not logged past its subprotocol: a password holding a
		// "/" ends the authority in front of the "@" that would have given the credentials away
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://u:pa/ss@h:3306/db"),
			"jdbc:mysql:" + CachedConnection.CREDENTIALS_HIDDEN);
		// ... and so does a password that holds an "@" and was quoted for the driver
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:scott/\"pa@ss\"@//h:1521/svc"),
			"jdbc:oracle:" + CachedConnection.CREDENTIALS_HIDDEN);
		// a string that is no connection string of any driver carries nothing to the log either
		assertEquals(CachedConnection.safeUrl("h:5432/db?password=secret"), CachedConnection.CREDENTIALS_HIDDEN);
	}

	/**
	 * A driver is free to quote the connection string it was handed back into the message of its
	 * failure, and that message travels: RootContainer makes it the message of what it throws,
	 * BackendConfigManager logs it at ERROR and answers a config change with it. So the message is
	 * redacted rather than the two call sites that happen to log a url.
	 */
	@Test
	public void testAMessageOfADriverCarriesNoCredentials() throws Exception {
		final String url = "jdbc:postgresql://opendj:S3cret@h:5432/db";
		assertEquals(CachedConnection.redact("No suitable driver found for " + url, url),
			"No suitable driver found for jdbc:postgresql://h:5432/db");
		// a driver naming the credentials alone, without the url around them
		assertEquals(CachedConnection.redact("authentication of opendj:S3cret failed", url),
			"authentication of " + CachedConnection.CREDENTIALS_HIDDEN + " failed");
		// ... and naming the password alone
		assertEquals(CachedConnection.redact("the password S3cret was not accepted", url),
			"the password " + CachedConnection.CREDENTIALS_HIDDEN + " was not accepted");
		// the credentials of an oracle url stand in front of its descriptor
		final String oracle = "jdbc:oracle:thin:scott/S3cret@//h:1521/svc";
		assertEquals(CachedConnection.redact("IO Error connecting to " + oracle, oracle),
			"IO Error connecting to jdbc:oracle:thin:@//h:1521/svc");
		// a password of a parameter is blanked wherever the message carries it
		assertEquals(CachedConnection.redact("bad url jdbc:sqlserver://h:1433;password=S3cret",
			"jdbc:sqlserver://h:1433;password=S3cret"), "bad url jdbc:sqlserver://h:1433");
		// a message naming nothing of the connection string is left as it stands
		assertEquals(CachedConnection.redact("Connection to h:5432 refused", url), "Connection to h:5432 refused");
		assertNull(CachedConnection.redact(null, url));

		// the stall report is the other way a driver's message reaches the log, and it carries the
		// url of the backend alongside it
		final String stall = CachedConnection.stallMessage(url, 3, 4000,
			new SQLException("FATAL: too many connections for " + url));
		assertFalse(stall.contains("S3cret"), stall);
		assertTrue(stall.contains("jdbc:postgresql://h:5432/db"), stall);
		assertTrue(stall.contains("4000 ms") && stall.contains("(3 attempts)"), stall);
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
		// counted as a delta of this borrow rather than as a count of the jvm: three tests of this
		// file open a pgjdbc login against a socket that never answers, and the order they run in
		// is not contractual - a thread left by any of them would be reported here
		final int before = loginThreadsOfPostgres();
		try (final ServerSocket blackhole = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			final String url = "jdbc:postgresql://127.0.0.1:" + blackhole.getLocalPort() + "/opendj?user=opendj&password=opendj";
			try {
				CachedConnection.getConnection(url);
				fail("a database that never answers must not hand out a connection");
			} catch (SQLException expected) {
				// reported to the caller, as the bound of the attempt promises
			}
			final long giveUpAt = System.currentTimeMillis() + BOUND_SECONDS * 1000 + BOUND_MARGIN_MS;
			while (loginThreadsOfPostgres() > before && System.currentTimeMillis() < giveUpAt) {
				Thread.sleep(100);
			}
			assertTrue(loginThreadsOfPostgres() <= before,
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

	/**
	 * A socket that answers a connect and closes it at once. A port bound and released is the
	 * shape of a refused connect a test would reach for, but it races whatever else on the host
	 * may take that port; this one is the failure it stands for and belongs to nobody else.
	 */
	private static ServerSocket rejectingSocket() throws Exception {
		final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		final Thread accepting = new Thread(() -> {
			while (!socket.isClosed()) {
				try {
					socket.accept().close();
				} catch (Exception closed) {
					return;
				}
			}
		}, "opendj-test-rejecting-socket");
		accepting.setDaemon(true);
		accepting.start();
		return socket;
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
