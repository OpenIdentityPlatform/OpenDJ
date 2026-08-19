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
 * Copyright 2024-2026 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import java.sql.*;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CachedConnection implements Connection {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    static final String TTL_PROPERTY = "org.openidentityplatform.opendj.jdbc.ttl";
    static final long DEFAULT_TTL_MS = 15000;

    /** Bounds the connect and the login of one attempt to establish a connection, in seconds; 0 for no bound. */
    static final String CONNECT_TIMEOUT_PROPERTY = "org.openidentityplatform.opendj.jdbc.connect.timeout";
    static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;

    /** Bounds a whole borrow - every connect attempt and every wait for a pooled connection - in seconds; 0 for no bound. */
    static final String POOL_TIMEOUT_PROPERTY = "org.openidentityplatform.opendj.jdbc.pool.timeout";
    static final long DEFAULT_POOL_TIMEOUT_SECONDS = 60;

    /** Bound of the validation of a pooled connection: isValid(0) means "no timeout" in the JDBC contract. */
    static final int VALIDATION_TIMEOUT_SECONDS = 5;

    static final long MAX_BACKOFF_MS = 1000;
    static final long STALL_WARNING_AFTER_MS = 1000;
    static final long STALL_WARNING_INTERVAL_MS = 10000;

    // setNetworkTimeout() takes the executor its timeout handling runs on: the drivers it is used
    // with here only set a socket option in it, so it costs a call rather than a thread.
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private static final AtomicLong lastStallWarning = new AtomicLong();
    private static final AtomicBoolean readBoundWarned = new AtomicBoolean();

    final Connection parent;

    static LoadingCache<String, BlockingQueue<CachedConnection>> cached = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMillis(getCacheTtlMillis()))
        .removalListener((String key, BlockingQueue<CachedConnection> value, RemovalCause cause) -> {
            for (CachedConnection con : value) {
                try {
                    if (!con.isClosed()) {
                        con.parent.close();
                    }
                } catch (SQLException e) {
                    // ignore
                }
            }
        })
        .build(conStr -> new LinkedBlockingQueue<>());

    /**
     * Returns the time after which an idle pooled connection is closed, as configured by the
     * {@value #TTL_PROPERTY} system property. An invalid value is ignored in favor of the default.
     */
    private static long getCacheTtlMillis() {
        return getNonNegativeProperty(TTL_PROPERTY, DEFAULT_TTL_MS);
    }

    /**
     * Returns the value of a numeric system property, ignoring a value that is not a non-negative
     * number in favor of the default.
     */
    private static long getNonNegativeProperty(String name, long defaultValue) {
        final String value = System.getProperty(name);
        if (value != null) {
            try {
                final long parsed = Long.parseLong(value.trim());
                if (parsed >= 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
            logger.warn(LocalizableMessage.raw("Ignoring invalid value \"%s\" of the %s property, using %d",
                value, name, defaultValue));
        }
        return defaultValue;
    }

    /**
     * The drivers this backend is used with, recognized by the prefix of the connection string,
     * together with the properties that bound one attempt to establish a connection. Not one of
     * them bounds the attempt with a single property: the one named first covers the socket
     * connect, and the login behind it - the reads of the prelogin handshake, of TLS and of
     * authentication, the phase a proxy at its connection limit or a moved VIP leaves unanswered -
     * needs the second. That holds for the SQL Server driver too, whose loginTimeout leaves the
     * read of the prelogin answer unbounded (CachedConnectionTestCase covers every one of them
     * against a socket that never answers).
     */
    enum ConnectDialect {
        /** postgresql: both properties take seconds; loginTimeout bounds the login the driver runs on a thread of its own. */
        POSTGRES("jdbc:postgresql:", "connectTimeout", 1, "loginTimeout", 1, false, new int[]{}),
        /** mysql: both properties take milliseconds; socketTimeout is a socket read timeout that outlives the login. */
        MYSQL("jdbc:mysql:", "connectTimeout", 1000, "socketTimeout", 1000, true, new int[]{1040, 1203}),
        /** oracle: both properties take milliseconds; ReadTimeout is a socket read timeout that outlives the login. */
        ORACLE("jdbc:oracle:", "oracle.net.CONNECT_TIMEOUT", 1000, "oracle.jdbc.ReadTimeout", 1000, true,
            new int[]{20, 12516, 12518, 12519, 12520}),
        /** ms sql server: loginTimeout takes seconds, socketTimeout milliseconds; the latter is a socket read timeout that outlives the login. */
        MICROSOFT("jdbc:sqlserver:", "loginTimeout", 1, "socketTimeout", 1000, true, new int[]{17809, 10928, 10929});

        final String urlPrefix;
        final String connectProperty;
        final int connectUnitsPerSecond;
        final String readProperty;
        final int readUnitsPerSecond;
        /** whether the read bound of the login stays in force for every statement issued afterwards */
        final boolean readBoundOutlivesLogin;
        /** the vendor codes of this dialect for "no further connection is accepted" */
        final int[] connectionLimitCodes;

        ConnectDialect(String urlPrefix, String connectProperty, int connectUnitsPerSecond,
                       String readProperty, int readUnitsPerSecond, boolean readBoundOutlivesLogin,
                       int[] connectionLimitCodes) {
            this.urlPrefix = urlPrefix;
            this.connectProperty = connectProperty;
            this.connectUnitsPerSecond = connectUnitsPerSecond;
            this.readProperty = readProperty;
            this.readUnitsPerSecond = readUnitsPerSecond;
            this.readBoundOutlivesLogin = readBoundOutlivesLogin;
            this.connectionLimitCodes = connectionLimitCodes;
        }

        /** The dialect of a connection string, or null for a driver whose property names are not known here. */
        static ConnectDialect of(String connectionString) {
            final String url = connectionString.toLowerCase(Locale.ROOT);
            for (final ConnectDialect dialect : values()) {
                if (url.startsWith(dialect.urlPrefix)) {
                    return dialect;
                }
            }
            return null;
        }

        /**
         * Fills in the properties bounding one connect attempt, leaving out every property the
         * connection string sets itself - an explicit setting of the administrator keeps
         * precedence, and the SQL Server driver gives a supplied property precedence over the url.
         * Returns whether a read bound outliving the login was set and has to be lifted once the
         * connection is established.
         */
        boolean bound(String connectionString, Properties properties, long timeoutSeconds) {
            if (!declaredInUrl(connectionString, connectProperty)) {
                properties.setProperty(connectProperty, Long.toString(timeoutSeconds * connectUnitsPerSecond));
            }
            if (readProperty != null && !declaredInUrl(connectionString, readProperty)) {
                properties.setProperty(readProperty, Long.toString(timeoutSeconds * readUnitsPerSecond));
                return readBoundOutlivesLogin;
            }
            return false;
        }

        boolean isConnectionLimit(SQLException e) {
            for (final int code : connectionLimitCodes) {
                if (e.getErrorCode() == code) {
                    return true;
                }
            }
            return false;
        }

        // Whether the connection string sets this property itself. The dialects separate their
        // parameters differently - "?a=1&b=2" (postgresql, mysql), ";a=1;b=2" (sql server),
        // "(A=1)" inside the descriptor of an oracle tns url, where the property also goes by the
        // last segment of its name alone - so a parameter is recognized by the delimiter in front
        // of it and the "=" behind it rather than by parsing the url syntax of every driver.
        private static boolean declaredInUrl(String connectionString, String property) {
            if (containsParameter(connectionString, property)) {
                return true;
            }
            final int dot = property.lastIndexOf('.');
            return dot >= 0 && containsParameter(connectionString, property.substring(dot + 1));
        }

        private static boolean containsParameter(String connectionString, String property) {
            final String url = connectionString.toLowerCase(Locale.ROOT);
            final String name = property.toLowerCase(Locale.ROOT);
            for (int i = url.indexOf(name); i >= 0; i = url.indexOf(name, i + name.length())) {
                final int end = i + name.length();
                if (i > 0 && "?&;(,".indexOf(url.charAt(i - 1)) >= 0 && end < url.length() && url.charAt(end) == '=') {
                    return true;
                }
            }
            return false;
        }
    }

    final String connectionString;
    public CachedConnection(String connectionString, Connection parent) {
        this.connectionString = connectionString;
        this.parent = parent;
    }

    /**
     * Borrows a connection: a usable one out of the pool, or a newly established one. Bounded in
     * both phases - every operation of this backend, the open of a backend and the import
     * included, comes through here, and an unbounded borrow turns a database that listens but does
     * not answer into a hang rather than into an error the caller can report.
     */
    static Connection getConnection(String connectionString) throws Exception {
        final ConnectDialect dialect = ConnectDialect.of(connectionString);
        final long connectTimeoutSeconds = Math.min(
            getNonNegativeProperty(CONNECT_TIMEOUT_PROPERTY, DEFAULT_CONNECT_TIMEOUT_SECONDS), Integer.MAX_VALUE / 1000);
        final long poolTimeoutSeconds = getNonNegativeProperty(POOL_TIMEOUT_PROPERTY, DEFAULT_POOL_TIMEOUT_SECONDS);
        final long startedAt = System.currentTimeMillis();
        final long deadline = (poolTimeoutSeconds == 0 || poolTimeoutSeconds >= Long.MAX_VALUE / 1000)
            ? Long.MAX_VALUE : startedAt + poolTimeoutSeconds * 1000;
        long waitMs = 0;
        long backoffMs = 0;
        int attempts = 0;
        while (true) {
            final CachedConnection pooled = poll(connectionString, waitMs);
            if (pooled != null) {
                return pooled;
            }
            attempts++;
            try {
                return connect(connectionString, dialect, connectTimeoutSeconds);
            } catch (SQLException e) {
                // A database that accepts no further connection is the one failure worth waiting
                // out: one of ours is going to come back to the pool. Everything else - a password
                // that is not accepted, a database that is down, a driver that is not on the
                // classpath - is reported to the caller instead of being retried behind its back.
                if (!isConnectionLimit(e, dialect)) {
                    throw e;
                }
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new SQLTimeoutException("no connection to " + safeUrl(connectionString) + " could be borrowed within "
                        + poolTimeoutSeconds + "s (" + attempts + " attempts): the database accepts no further connection"
                        + " and none was returned to the pool", e);
                }
                backoffMs = Math.min(backoffMs == 0 ? 1 : backoffMs * 2, MAX_BACKOFF_MS);
                waitMs = Math.min(backoffMs, remaining);
                warnStall(connectionString, attempts, startedAt, e);
            }
        }
    }

    /** Takes a usable connection out of the pool, waiting up to waitMs for one to be returned to it. */
    private static CachedConnection poll(String connectionString, long waitMs) throws InterruptedException {
        CachedConnection con = cached.get(connectionString).poll(waitMs, TimeUnit.MILLISECONDS);
        while (con != null) {
            if (isUsable(con)) {
                return con;
            }
            closeQuietly(con.parent);
            con = cached.get(connectionString).poll();
        }
        return null;
    }

    private static boolean isUsable(CachedConnection con) {
        try {
            // The validation needs a bound of its own: isValid(0) means "no timeout" in the JDBC
            // contract, and a connection whose socket is half-open answers it no sooner than it
            // answers anything else.
            return con.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) { // a driver reporting the validation as an error: discard it
            return false;
        }
    }

    private static CachedConnection connect(String connectionString, ConnectDialect dialect, long connectTimeoutSeconds)
            throws SQLException {
        // A driver is free to write into the map it is handed, so it gets one of its own.
        final Properties properties = new Properties();
        final boolean readBoundSet = dialect != null && connectTimeoutSeconds > 0
            && dialect.bound(connectionString, properties, connectTimeoutSeconds);
        final Connection conNew = DriverManager.getConnection(connectionString, properties);
        try {
            // still under the read bound: both of these are round trips of their own
            conNew.setAutoCommit(false);
            conNew.setTransactionIsolation(TRANSACTION_READ_COMMITTED);
            if (readBoundSet) {
                relaxReadBound(conNew);
            }
        } catch (SQLException e) { // nothing holds this connection yet: it would leak
            closeQuietly(conNew);
            throw e;
        }
        return new CachedConnection(connectionString, conNew);
    }

    // The second bound of the login is a socket read timeout on mysql, oracle and sql server, in
    // force for the whole life of the connection: left in place it would break every statement
    // slower than it - an import batch, the statistics of a freshly loaded table - so it is lifted
    // as soon as the login is through, restoring the behaviour of a connection this class
    // established before. A read bound the connection string sets itself is never touched here:
    // it is not set at all, so nothing of the administrator's is lifted along with it.
    private static void relaxReadBound(Connection con) {
        try {
            con.setNetworkTimeout(DIRECT_EXECUTOR, 0);
        } catch (SQLException | RuntimeException e) {
            if (readBoundWarned.compareAndSet(false, true)) {
                logger.warn(LocalizableMessage.raw(
                    "The read bound of the login could not be lifted (%s): statements taking longer than the %s"
                        + " property will fail on connections of this backend", e.getMessage(), CONNECT_TIMEOUT_PROPERTY));
            }
        }
    }

    /** Whether the database refused the connection because it accepts no further one. */
    static boolean isConnectionLimit(SQLException e, ConnectDialect dialect) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException) {
                final SQLException sql = (SQLException) t;
                // class 53, insufficient_resources, is how the standard - and postgresql, with
                // 53300 too_many_connections - reports a server taking no further connection
                final String sqlState = sql.getSQLState();
                if ((sqlState != null && sqlState.startsWith("53"))
                    || (dialect != null && dialect.isConnectionLimit(sql))) {
                    return true;
                }
            }
        }
        return false;
    }

    // A stall has to reach the server log: without it a database accepting no further connection
    // is indistinguishable from a hang. Throttled, since every operation of the backend borrows
    // through here and would otherwise log a copy of its own.
    private static void warnStall(String connectionString, int attempts, long startedAt, SQLException cause) {
        final long now = System.currentTimeMillis();
        if (now - startedAt < STALL_WARNING_AFTER_MS) {
            return;
        }
        final long last = lastStallWarning.get();
        if (now - last >= STALL_WARNING_INTERVAL_MS && lastStallWarning.compareAndSet(last, now)) {
            logger.warn(LocalizableMessage.raw(
                "%s accepts no further connection: waiting %d ms for a pooled one so far (%d attempts), last error: %s",
                safeUrl(connectionString), now - startedAt, attempts, cause.getMessage()));
        }
    }

    // The connection string carries the credentials of the backend, so it is never logged as it
    // stands. Parameters - "?user=...&password=..." on postgresql and mysql, ";password=..." on
    // sql server - are cut off behind their first separator, while the credentials of a url that
    // carries them in front of an "@" ("user/password@//host" on an oracle thin url, the userinfo
    // of a url) are cut off in front of it, leaving the scheme and the host they stand between.
    static String safeUrl(String connectionString) {
        int cut = connectionString.length();
        for (final char separator : new char[]{'?', ';'}) {
            final int at = connectionString.indexOf(separator);
            if (at >= 0 && at < cut) {
                cut = at;
            }
        }
        final String url = connectionString.substring(0, cut);
        final int at = url.indexOf('@');
        if (at < 0) {
            return url;
        }
        final int scheme = url.indexOf(':', "jdbc:".length()) + 1; // the end of "jdbc:<subprotocol>:"
        return url.substring(0, scheme) + url.substring(at);
    }

    private static void closeQuietly(Connection con) {
        try {
            con.close();
        } catch (SQLException e) {
            // ignore: it is on its way out anyway
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        return parent.createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return parent.prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return parent.prepareCall(sql);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return parent.nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        parent.setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return parent.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        parent.commit();
    }

    @Override
    public void rollback() throws SQLException {
        parent.rollback();
    }

    @Override
    public void close() throws SQLException {
        try {
            rollback();
        } catch (SQLException e) {
            // A connection that cannot be rolled back must not be handed to the next borrower -
            // and must not be dropped on the floor either: nothing else holds it any more.
            closeQuietly(parent);
            throw e;
        }
        cached.get(connectionString).add(this);
    }

    @Override
    public boolean isClosed() throws SQLException {
        return parent.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return parent.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        parent.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return parent.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        parent.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return parent.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        parent.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return parent.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return parent.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        parent.clearWarnings();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return parent.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return parent.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return parent.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return parent.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        parent.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        parent.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return parent.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return parent.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return parent.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        parent.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        parent.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return parent.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return parent.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return parent.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return parent.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return parent.prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return parent.prepareStatement(sql, columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        return parent.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return parent.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return parent.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return parent.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return parent.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        parent.setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        parent.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return parent.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return parent.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return parent.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return parent.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        parent.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return parent.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        parent.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        parent.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return parent.getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return parent.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return parent.isWrapperFor(iface);
    }
}
