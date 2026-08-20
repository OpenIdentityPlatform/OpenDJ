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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
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
		System.clearProperty(CachedConnection.POOL_MAX_PROPERTY);
		System.clearProperty(CachedConnection.TTL_PROPERTY);
	}

	/**
	 * Nothing used to limit how many connections a backend opened: the pool was an unbounded queue
	 * behind a cache with no maximum size, so a burst of concurrent operations opened as many
	 * connections as there were threads asking, and the only ceiling left was the max_connections of
	 * the database itself (#878).
	 */
	@Test(timeOut = 120000)
	public void testThePoolDoesNotGrowPastItsBound() throws Exception {
		final String url = StubDriver.PREFIX + "bounded";
		System.setProperty(CachedConnection.POOL_MAX_PROPERTY, "2");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "1");
		stub.answerWith(null);

		// One thread per borrow, as the worker threads of the server are: two borrows on one thread
		// are nested by definition, and a nested one is allowed past the bound on purpose.
		final Connection first = borrowOnAThreadOfItsOwn(url);
		final Connection second = borrowOnAThreadOfItsOwn(url);
		assertEquals(CachedConnection.poolOf(url).liveCount(), 2);
		try {
			borrowOnAThreadOfItsOwn(url);
			fail("a third connection was opened past the bound of two");
		} catch (ExecutionException e) {
			assertTrue(e.getCause() instanceof SQLTimeoutException, String.valueOf(e.getCause()));
			assertTrue(e.getCause().getMessage().contains("all 2 connections"), e.getCause().getMessage());
		}

		// The bound waits for a returned connection rather than refusing outright: it is a ceiling
		// on the connections held, not on the operations served.
		first.close();
		final Connection third = borrowOnAThreadOfItsOwn(url);
		assertSame(third, first);
		third.close();
		second.close();
		CachedConnection.invalidate(url);
	}

	/** Borrows the way the server does, one operation to a thread. */
	private static Connection borrowOnAThreadOfItsOwn(String url) throws Exception {
		final FutureTask<Connection> borrow = new FutureTask<>(() -> CachedConnection.getConnection(url));
		final Thread thread = new Thread(borrow, "borrow-" + url);
		thread.setDaemon(true);
		thread.start();
		return borrow.get(120, TimeUnit.SECONDS);
	}

	/**
	 * A borrow made while this thread already holds a connection must not wait for the bound: the
	 * two are held at once, so it would wait for itself. PersistentCompressedSchema.store() opens a
	 * write of its own and is reached from inside a transaction by EntryContainer.importEntry and
	 * EntryContainer.modifyDN, both of which encode the entry inside it.
	 */
	@Test(timeOut = 120000)
	public void testABorrowNestedInAnotherMayPassTheBound() throws Exception {
		final String url = StubDriver.PREFIX + "reentrant";
		System.setProperty(CachedConnection.POOL_MAX_PROPERTY, "1");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "1");
		stub.answerWith(null);

		final Connection outer = CachedConnection.getConnection(url);
		final Connection nested = CachedConnection.getConnection(url);
		assertNotSame(nested, outer);

		// It holds no permit of the pool, so pooling it would leave the pool one connection over
		// its bound for good: it is closed instead.
		nested.close();
		verify(((CachedConnection) nested).parent).close();
		assertEquals(CachedConnection.poolOf(url).idleCount(), 0);

		outer.close();
		assertEquals(CachedConnection.poolOf(url).idleCount(), 1);
		CachedConnection.invalidate(url);
	}

	/**
	 * The TTL used to sit on the pool rather than on a connection - keyed by the connection string,
	 * and touched by every borrow and every return - so under continuous traffic nothing in it ever
	 * expired (#878).
	 */
	@Test(timeOut = 120000)
	public void testAnIdleConnectionIsClosedAfterItsTtl() throws Exception {
		final String url = StubDriver.PREFIX + "ttl";
		stub.answerWith(null);

		final CachedConnection first = (CachedConnection) CachedConnection.getConnection(url);
		first.close();
		assertEquals(CachedConnection.poolOf(url).idleCount(), 1);
		first.returnedAtMillis = System.currentTimeMillis() - 60000;
		System.setProperty(CachedConnection.TTL_PROPERTY, "1000");

		final Connection second = CachedConnection.getConnection(url);

		assertNotSame(second, first, "a connection idle far longer than the TTL was handed out");
		verify(first.parent).close();
		second.close();
		CachedConnection.invalidate(url);
	}

	/**
	 * Expiry has to happen without a borrow behind it: the cache was built without a scheduler, so
	 * an entry was only ever expired by a later cache operation - and a backend that has gone idle,
	 * the one case the TTL exists for, performs none (#878). This is what the sweeper thread runs.
	 */
	@Test(timeOut = 120000)
	public void testTheSweepClosesAnIdleConnectionWithNoBorrowBehindIt() throws Exception {
		final String url = StubDriver.PREFIX + "sweep";
		stub.answerWith(null);
		final CachedConnection con = (CachedConnection) CachedConnection.getConnection(url);
		con.close();
		con.returnedAtMillis = System.currentTimeMillis() - 60000;
		final CachedConnection.Pool pool = CachedConnection.poolOf(url);
		assertEquals(pool.idleCount(), 1);

		pool.sweep(1000);

		assertEquals(pool.idleCount(), 0);
		verify(con.parent).close();
		assertEquals(pool.liveCount(), 0, "a swept connection kept its place in the pool");
	}

	/** A closed backend has no use for its connections; they used to be left open (#878). */
	@Test(timeOut = 120000)
	public void testClosingTheLastUserReleasesTheConnections() throws Exception {
		final String url = StubDriver.PREFIX + "release";
		stub.answerWith(null);
		CachedConnection.openPool(url);
		final CachedConnection con = (CachedConnection) CachedConnection.getConnection(url);
		con.close();
		assertEquals(CachedConnection.poolOf(url).idleCount(), 1);

		CachedConnection.closePool(url);

		assertEquals(CachedConnection.poolOf(url).idleCount(), 0);
		verify(con.parent).close();
		assertEquals(CachedConnection.poolOf(url).liveCount(), 0);
	}

	/**
	 * A pool belongs to a database rather than to a backend: two backends may address one database,
	 * and closing one of them must not take the connections of the other with it.
	 */
	@Test(timeOut = 120000)
	public void testConnectionsSurviveWhileAnotherBackendStillUsesTheDatabase() throws Exception {
		final String url = StubDriver.PREFIX + "shared";
		stub.answerWith(null);
		CachedConnection.openPool(url);
		CachedConnection.openPool(url);
		final CachedConnection con = (CachedConnection) CachedConnection.getConnection(url);
		con.close();

		CachedConnection.closePool(url);
		assertEquals(CachedConnection.poolOf(url).idleCount(), 1, "the second backend lost its connections");

		CachedConnection.closePool(url);
		assertEquals(CachedConnection.poolOf(url).idleCount(), 0);
		verify(con.parent).close();
	}

	/** A connection out on loan when the last backend closed is closed when it comes back. */
	@Test(timeOut = 120000)
	public void testAConnectionReturnedAfterTheLastUserLeftIsClosed() throws Exception {
		final String url = StubDriver.PREFIX + "return-after-close";
		stub.answerWith(null);
		CachedConnection.openPool(url);
		final CachedConnection con = (CachedConnection) CachedConnection.getConnection(url);

		CachedConnection.closePool(url);
		con.close();

		verify(con.parent).close();
		assertEquals(CachedConnection.poolOf(url).idleCount(), 0);
	}

	/** A backend closed and opened again pools its connections as before: addUser() clears the flag. */
	@Test(timeOut = 120000)
	public void testABackendClosedAndOpenedAgainPoolsItsConnections() throws Exception {
		final String url = StubDriver.PREFIX + "reopen";
		stub.answerWith(null);
		CachedConnection.openPool(url);
		CachedConnection.getConnection(url).close();
		CachedConnection.closePool(url);
		assertEquals(CachedConnection.poolOf(url).idleCount(), 0);

		CachedConnection.openPool(url);
		CachedConnection.getConnection(url).close();

		assertEquals(CachedConnection.poolOf(url).idleCount(), 1, "a reopened backend stopped pooling its connections");
		CachedConnection.closePool(url);
	}

	/**
	 * A connect that fails with something other than a SQLException must not cost the pool a
	 * permit. DriverManager catches SQLException alone, so an unchecked failure of a driver reaches
	 * the borrow: Connector/J hands a url with a "%" in it to URLDecoder, and this backend keeps
	 * its credentials in the url. Only a live connection carries a permit, so one left behind is
	 * left behind for good - after as many failures as the bound the pool would report that every
	 * connection is in use while holding none (#878).
	 */
	@Test(timeOut = 120000)
	public void testAConnectFailingUncheckedCostsThePoolNothing() throws Exception {
		final String url = StubDriver.PREFIX + "unchecked";
		System.setProperty(CachedConnection.POOL_MAX_PROPERTY, "2");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "1");
		final CachedConnection.Pool pool = CachedConnection.poolOf(url);
		stub.failWith(new IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern"),
			StubDriver.ALWAYS);

		for (int i = 1; i <= 2 * pool.max(); i++) {
			try {
				CachedConnection.getConnection(url);
				fail("the connect did not fail");
			} catch (IllegalArgumentException expected) {
				// reported to the caller, as a configuration error has to be
			}
			assertEquals(pool.liveCount(), 0, "attempt " + i + " kept a permit of the pool");
		}

		// and the pool still serves, rather than reporting connections it does not hold as in use
		stub.answerWith(null);
		final Connection con = CachedConnection.getConnection(url);
		assertNotNull(con);
		con.close();
		CachedConnection.invalidate(url);
	}

	/**
	 * The exemption of a nested borrow belongs to one pool: a thread holding a connection to one
	 * database holds nothing of another, so the bound of that other pool applies and its connection
	 * comes back to it rather than being closed.
	 */
	@Test(timeOut = 120000)
	public void testHoldingAConnectionToOneDatabaseDoesNotExemptABorrowFromAnother() throws Exception {
		final String first = StubDriver.PREFIX + "held-first";
		final String second = StubDriver.PREFIX + "held-second";
		stub.answerWith(null);

		final Connection held = CachedConnection.getConnection(first);
		final Connection other = CachedConnection.getConnection(second);
		assertEquals(CachedConnection.poolOf(second).liveCount(), 1, "the borrow passed the bound of the other pool");
		other.close();

		assertEquals(CachedConnection.poolOf(second).idleCount(), 1, "the borrow was taken for a nested one and closed");
		held.close();
		CachedConnection.invalidate(first);
		CachedConnection.invalidate(second);
	}

	/** JDBC makes close() on a closed connection a no-op; a second return would pool the same one twice. */
	@Test(timeOut = 120000)
	public void testASecondCloseDoesNotPoolTheConnectionTwice() throws Exception {
		final String url = StubDriver.PREFIX + "double-close";
		stub.answerWith(null);
		final Connection con = CachedConnection.getConnection(url);
		con.close();
		con.close();

		assertEquals(CachedConnection.poolOf(url).idleCount(), 1, "one connection was pooled twice");
		CachedConnection.invalidate(url);
	}

	/**
	 * What the sweeper runs hands the close elsewhere instead of running it. The sweep of every
	 * pool shares one thread and scheduleWithFixedDelay never overlaps its runs, so one close that
	 * does not return would stop the expiry of every pool in the JVM, silently (#878).
	 */
	@Test(timeOut = 120000)
	public void testTheSweepDoesNotCloseOnTheSweeperThread() throws Exception {
		final String url = StubDriver.PREFIX + "sweep-elsewhere";
		stub.answerWith(null);
		final CachedConnection con = (CachedConnection) CachedConnection.getConnection(url);
		con.close();
		con.returnedAtMillis = System.currentTimeMillis() - 60000;
		final CachedConnection.Pool pool = CachedConnection.poolOf(url);
		final List<Runnable> handedOff = new ArrayList<>();

		pool.sweep(1000, handedOff::add);

		assertEquals(pool.idleCount(), 0, "the expired connection kept its place in the pool");
		verify(con.parent, never()).close();
		assertEquals(handedOff.size(), 1);

		handedOff.get(0).run();
		verify(con.parent).close();
		assertEquals(pool.liveCount(), 0, "a swept connection kept its permit");
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

	/** A pooled connection that no longer validates is closed and replaced, not handed out. */
	@Test(timeOut = 120000)
	public void testBrokenPooledConnectionIsDiscarded() throws Exception {
		final String url = StubDriver.PREFIX + "broken-pooled";
		final Connection stale = mock(Connection.class);
		when(stale.isValid(anyInt())).thenReturn(false);
		CachedConnection.poolOf(url).addIdle(new CachedConnection(url, stale));
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
		assertEquals(CachedConnection.poolOf(url).idleCount(), 0, "a connection that cannot be rolled back was pooled");
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
		final Properties postgres = new Properties();
		assertFalse(CachedConnection.ConnectDialect.POSTGRES.bound("jdbc:postgresql://h:5432/db", postgres, 7));
		assertEquals(postgres.getProperty("connectTimeout"), "7");
		assertEquals(postgres.getProperty("loginTimeout"), "7");

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
	 * A bound the administrator put into the connection string by hand - the only workaround this
	 * backend had - keeps precedence, property by property.
	 */
	@Test
	public void testConnectionStringKeepsPrecedence() throws Exception {
		final Properties postgres = new Properties();
		CachedConnection.ConnectDialect.POSTGRES.bound(
			"jdbc:postgresql://h:5432/db?user=u&password=p&loginTimeout=30&socketTimeout=300", postgres, 7);
		assertNull(postgres.getProperty("loginTimeout"), "the setting of the connection string was overridden");
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

		// a name that only appears as the tail of another parameter is not a setting of its own
		final Properties mysql = new Properties();
		CachedConnection.ConnectDialect.MYSQL.bound("jdbc:mysql://h:3306/db?xconnectTimeout=1&socketTimeoutX=2", mysql, 7);
		assertEquals(mysql.getProperty("connectTimeout"), "7000");
		assertEquals(mysql.getProperty("socketTimeout"), "7000");
	}

	/** The connection string holds the credentials of the backend: a stall report must not carry them. */
	@Test
	public void testLoggedConnectionStringCarriesNoCredentials() throws Exception {
		assertEquals(CachedConnection.safeUrl("jdbc:postgresql://h:5432/db?user=u&password=secret"), "jdbc:postgresql://h:5432/db");
		assertEquals(CachedConnection.safeUrl("jdbc:sqlserver://h:1433;databaseName=db;password=secret"), "jdbc:sqlserver://h:1433");
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:scott/secret@//h:1521/svc"), "jdbc:oracle:@//h:1521/svc");
		assertEquals(CachedConnection.safeUrl("jdbc:mysql://u:secret@h:3306/db"), "jdbc:mysql:@h:3306/db");
		assertEquals(CachedConnection.safeUrl("jdbc:oracle:thin:@//h:1521/svc"), "jdbc:oracle:@//h:1521/svc");
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
		/** A SQLException, or the unchecked failure a driver is free to throw at DriverManager instead. */
		private volatile Throwable failure;
		private volatile int failuresLeft;
		private volatile Connection answer;

		void failWith(Throwable failure, int times) {
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
				if (failure instanceof SQLException) {
					throw (SQLException) failure;
				}
				throw (RuntimeException) failure;
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
