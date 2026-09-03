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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

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
	 */
	@Test
	public void testTheCatalogConnectTakesTheConfiguredBound() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "120");
		try {
			probeDriver.lastProperties = null;
			storageFor(ProbeDriver.URL).newCatalogConnection().close();
			assertBoundedAt(probeDriver.lastProperties, 120);
		} finally {
			restore(previous);
		}
	}

	/** Nothing configured is the default of the pool, which is what this connect used to take always. */
	@Test
	public void testTheCatalogConnectTakesTheDefaultWhereNothingIsConfigured() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		try {
			probeDriver.lastProperties = null;
			storageFor(ProbeDriver.URL).newCatalogConnection().close();
			assertBoundedAt(probeDriver.lastProperties, CachedConnection.DEFAULT_CONNECT_TIMEOUT_SECONDS);
		} finally {
			restore(previous);
		}
	}

	/**
	 * A property of 0 is the operator asking for no bound at all - the pool reads it that way - and a
	 * connect that bounded itself anyway would be answering a setting with the opposite of it. Nothing
	 * is handed to the driver then, and there is no read bound to lift once the login is through.
	 */
	@Test
	public void testTheCatalogConnectIsUnboundedWhereTheOperatorTurnedTheBoundOff() throws Exception {
		final String previous = System.getProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, "0");
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
		System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		try {
			final Connection con = storageFor(ProbeDriver.URL).newCatalogConnection();
			con.close();
			verify(con).setAutoCommit(false);
			verify(con).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			verify(con).setNetworkTimeout(any(), eq(0));
		} finally {
			restore(previous);
		}
	}

	/**
	 * A connection whose set-up failed is held by nobody - the caller is answered with the failure -
	 * so it is closed here or it leaks for the life of the process, one per open of a storage.
	 */
	@Test
	public void testAConnectionWhoseSetUpFailsIsClosed() throws Exception {
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

	private static void restore(String previous) {
		if (previous == null) {
			System.clearProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY);
		} else {
			System.setProperty(CachedConnection.CONNECT_TIMEOUT_PROPERTY, previous);
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

		@Override
		public Connection connect(String url, Properties info) throws SQLException {
			if (!acceptsURL(url)) {
				return null; // not ours: DriverManager goes on to the next driver
			}
			lastProperties = info;
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
