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
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * The bounds of the connection the table comment statements run on, and the classification of a
 * stamp that failed. None of it needs a database: the first two cases are about what the backend
 * hands its driver and what the driver then does with a server that never answers, the last is a
 * pure function of an exception. Keeping them out of the container suites is the point - those
 * skip themselves whole when no docker is reachable, and a bound nothing exercises is a bound
 * that can be deleted without a single test going red.
 */
@SuppressWarnings("javadoc")
public class StampConnectionTestCase extends DirectoryServerTestCase {

	/**
	 * A ceiling generous enough that a loaded machine cannot cross it, and far below what an
	 * unbounded driver does: a login left unbounded against a silent server does not come back at
	 * all - it sits in the read of the prelogin answer until something else tears the socket down.
	 */
	private static final long GIVE_UP_CEILING_SECONDS = 120;

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

	private static JDBCStorage storageFor(String url) {
		final JDBCBackendCfg cfg = mockCfg(JDBCBackendCfg.class);
		when(cfg.getBackendId()).thenReturn("stampProbe");
		when(cfg.getDBDirectory()).thenReturn(url);
		return new JDBCStorage(cfg, null);
	}

	/**
	 * Every dialect must bound both phases of a login attempt: the socket connect, and the reads
	 * behind it - of the prelogin handshake, of tls, of authentication. Not one of the four
	 * drivers covers both with a single property, the sql server one included: its loginTimeout
	 * bounds the connect and leaves the prelogin read open.
	 */
	@Test
	public void testEveryDialectDeclaresBothBounds() {
		for (final JDBCStorage.Dialect dialect : JDBCStorage.Dialect.values()) {
			assertTrue(dialect.connectProperties.size() >= 2,
				"the login of " + dialect + " is not bounded in both phases: " + dialect.connectProperties);
			for (final String name : dialect.connectProperties.stringPropertyNames()) {
				assertTrue(Integer.parseInt(dialect.connectProperties.getProperty(name)) > 0,
					name + " of " + dialect + " bounds nothing: " + dialect.connectProperties.getProperty(name));
			}
		}
	}

	/**
	 * The declaration above is worth nothing unless it reaches the driver, and nothing else in the
	 * suite notices if it stops doing so: dropping the properties from the connect call leaves
	 * every stamp unbounded again, which no round-trip test can see against a database that
	 * answers.
	 */
	@Test
	public void testStampConnectionHandsItsBoundsToTheDriver() throws Exception {
		final JDBCStorage storage = storageFor(ProbeDriver.URL);
		for (final JDBCStorage.Dialect dialect : JDBCStorage.Dialect.values()) {
			probeDriver.lastProperties = null;
			storage.newStampConnection(dialect).close();
			final Properties handed = probeDriver.lastProperties;
			assertNotNull(handed, "no properties were handed to the driver for " + dialect);
			for (final Map.Entry<Object, Object> bound : dialect.connectProperties.entrySet()) {
				assertEquals(handed.getProperty((String) bound.getKey()), bound.getValue(),
					bound.getKey() + " of " + dialect + " did not reach the driver");
			}
			// a driver is free to write into the map it is passed: the declaration must not be it
			assertNotSame(handed, dialect.connectProperties,
				"the driver was handed the declaration of " + dialect + " rather than a copy of it");
		}
	}

	/**
	 * What the bounds are for: a database that keeps its established connections alive but accepts
	 * no new ones - a moved vip, a proxy at its connection limit - usually completes the tcp
	 * connect and then goes quiet, which leaves the driver in a read. Unbounded, that hangs the
	 * open of a tree; dsconfig create-backend-index opens one on a running server.
	 * <p>
	 * The four dialects are attempted at once, so the suite pays the bound of the slowest of them
	 * rather than the sum of all four.
	 */
	@Test
	public void testEveryDriverGivesUpOnASilentServer() throws Exception {
		try (final SilentServer silent = new SilentServer()) {
			final List<Callable<String>> attempts = new ArrayList<>();
			for (final JDBCStorage.Dialect dialect : JDBCStorage.Dialect.values()) {
				final JDBCStorage storage = storageFor(silent.urlFor(dialect));
				attempts.add(new Callable<String>() {
					@Override
					public String call() {
						final long startedAt = System.nanoTime();
						try (final Connection con = storage.newStampConnection(dialect)) {
							return dialect + " connected to a server that never answered";
						} catch (Exception expected) {
							// the failure itself is the point: which one it is belongs to the driver
						}
						final long tookSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt);
						return tookSeconds <= GIVE_UP_CEILING_SECONDS ? null
							: dialect + " took " + tookSeconds + "s to give up on a silent server";
					}
				});
			}
			final ExecutorService attempted = Executors.newFixedThreadPool(attempts.size());
			try {
				final StringBuilder failures = new StringBuilder();
				for (final Future<String> attempt : attempted.invokeAll(attempts,
						GIVE_UP_CEILING_SECONDS * 2, TimeUnit.SECONDS)) {
					final String failure = attempt.isCancelled() // the driver never came back at all
						? "a driver did not give up on a silent server within "
							+ (GIVE_UP_CEILING_SECONDS * 2) + "s"
						: attempt.get();
					if (failure != null) {
						failures.append(failure).append('\n');
					}
				}
				if (failures.length() > 0) {
					fail(failures.toString());
				}
			} finally {
				attempted.shutdownNow();
			}
		}
	}

	/**
	 * A stamp that the database rejected is remembered, so that an account which may not comment
	 * its tables does not reissue the statement for every tree on every open. A stamp that lost
	 * its connection ends the sweep, since every tree behind it needs that same connection. A
	 * stamp that gave up on a lock is neither: the lock belongs to that one table.
	 */
	@Test
	public void testFailureScopeTellsTheThreeApart() {
		assertEquals(JDBCStorage.failureScope(
				new SQLException("permission denied for table", "42501"), JDBCStorage.Dialect.POSTGRES),
			JDBCStorage.FailureScope.TREE, "a rejected statement was not remembered");
		assertEquals(JDBCStorage.failureScope(
				new SQLException("lock not available", "55P03"), JDBCStorage.Dialect.POSTGRES),
			JDBCStorage.FailureScope.MOMENT, "a lock timeout was not read as one of the moment");
		assertEquals(JDBCStorage.failureScope(
				new SQLException("connection closed", "08006"), JDBCStorage.Dialect.POSTGRES),
			JDBCStorage.FailureScope.SESSION, "a connection exception did not end the sweep");
		assertEquals(JDBCStorage.failureScope(
				new SQLNonTransientConnectionException("socket closed"), JDBCStorage.Dialect.MICROSOFT),
			JDBCStorage.FailureScope.SESSION, "a connection exception of the driver did not end the sweep");
		assertEquals(JDBCStorage.failureScope(
				new SQLTimeoutException("query timed out"), JDBCStorage.Dialect.MYSQL),
			JDBCStorage.FailureScope.MOMENT, "a timeout was not read as a failure of the moment");
	}

	/**
	 * A driver reports the vendor error of a failed statement as the next exception of a generic
	 * one at least as often as it reports it as the cause. Reading only the cause chain classifies
	 * a lock timeout as a rejection, which leaves the tree unstamped until the next start over a
	 * moment of contention.
	 */
	@Test
	public void testFailureScopeWalksBothChains() {
		final SQLException reportedAsTheCause = new SQLException("statement failed",
			new SQLException("lock wait timeout exceeded", "HY000", 1205));
		assertEquals(JDBCStorage.failureScope(reportedAsTheCause, JDBCStorage.Dialect.MYSQL),
			JDBCStorage.FailureScope.MOMENT, "a lock timeout on the cause chain was missed");

		final SQLException reportedAsTheNext = new SQLException("statement failed");
		reportedAsTheNext.setNextException(new SQLException("lock wait timeout exceeded", "HY000", 1205));
		assertEquals(JDBCStorage.failureScope(reportedAsTheNext, JDBCStorage.Dialect.MYSQL),
			JDBCStorage.FailureScope.MOMENT, "a lock timeout on the next-exception chain was missed");

		// a connection exception anywhere in either chain outweighs the rest: the session is gone
		final SQLException connectionGone = new SQLException("statement failed");
		connectionGone.setNextException(new SQLException("communications link failure", "08S01"));
		assertEquals(JDBCStorage.failureScope(connectionGone, JDBCStorage.Dialect.MYSQL),
			JDBCStorage.FailureScope.SESSION, "a connection exception on the next-exception chain was missed");

		// a driver that chains an exception back to itself must not make the walk loop
		final SQLException selfReferring = new SQLException("statement failed");
		selfReferring.setNextException(selfReferring);
		assertEquals(JDBCStorage.failureScope(selfReferring, JDBCStorage.Dialect.MYSQL),
			JDBCStorage.FailureScope.TREE, "a self-referring chain was not walked to an end");
	}

	/** Accepts connections and answers nothing at all, the shape of a proxy at its connection limit. */
	private static final class SilentServer implements AutoCloseable {
		private final ServerSocket listening;
		private final List<Socket> accepted = new ArrayList<>();
		private final Thread acceptor;

		SilentServer() throws IOException {
			listening = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
			acceptor = new Thread(new Runnable() {
				@Override
				public void run() {
					while (!Thread.currentThread().isInterrupted()) {
						try {
							final Socket socket = listening.accept();
							synchronized (accepted) { // held open, and never written to
								accepted.add(socket);
							}
						} catch (IOException closed) {
							return;
						}
					}
				}
			}, "silent-server");
			acceptor.setDaemon(true);
			acceptor.start();
		}

		String urlFor(JDBCStorage.Dialect dialect) {
			final String host = listening.getInetAddress().getHostAddress();
			final int port = listening.getLocalPort();
			switch (dialect) {
			case POSTGRES:
				return "jdbc:postgresql://" + host + ":" + port + "/probe?user=probe&password=probe";
			case MYSQL:
				return "jdbc:mysql://" + host + ":" + port + "/probe?user=probe&password=probe";
			case ORACLE:
				return "jdbc:oracle:thin:probe/probe@//" + host + ":" + port + "/probe";
			case MICROSOFT:
				return "jdbc:sqlserver://" + host + ":" + port + ";databaseName=probe;user=probe;password=probe";
			default:
				throw new IllegalStateException("no probe url for dialect " + dialect);
			}
		}

		@Override
		public void close() throws IOException {
			acceptor.interrupt();
			listening.close();
			synchronized (accepted) {
				for (final Socket socket : accepted) {
					try {
						socket.close();
					} catch (IOException ignored) {
					}
				}
			}
		}
	}

	/** Records the properties a stamp connection hands its driver, and connects to nothing. */
	private static final class ProbeDriver implements Driver {
		static final String URL = "jdbc:stampprobe:";

		volatile Properties lastProperties;

		@Override
		public Connection connect(String url, Properties info) throws SQLException {
			if (!acceptsURL(url)) {
				return null; // not ours: DriverManager goes on to the next driver
			}
			lastProperties = info;
			final Connection con = mock(Connection.class);
			when(con.createStatement()).thenReturn(mock(Statement.class));
			return con;
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
