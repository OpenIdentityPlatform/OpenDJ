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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CachedConnection implements Connection {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    static final String TTL_PROPERTY = "org.openidentityplatform.opendj.jdbc.ttl";
    static final long DEFAULT_TTL_MS = 15000;

    /**
     * Bounds the connect and the login of one attempt to establish a connection, in seconds; 0 for
     * no bound of its own - the deadline of {@value #POOL_TIMEOUT_PROPERTY} still bounds the
     * attempt, since it stands for the whole borrow. Setting both to 0 is what leaves a connect
     * unbounded.
     */
    static final String CONNECT_TIMEOUT_PROPERTY = "org.openidentityplatform.opendj.jdbc.connect.timeout";
    static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;

    /**
     * Bounds a whole borrow - every connect attempt and every wait for a pooled connection - in
     * seconds; 0 for no bound. Not to the millisecond: the connection in hand is validated
     * whatever the deadline says, and an attempt is never given less than a second, so a borrow
     * can return a validation and a last attempt past it.
     */
    static final String POOL_TIMEOUT_PROPERTY = "org.openidentityplatform.opendj.jdbc.pool.timeout";
    static final long DEFAULT_POOL_TIMEOUT_SECONDS = 60;

    /** Bound of the validation of a pooled connection: isValid(0) means "no timeout" in the JDBC contract. */
    static final int VALIDATION_TIMEOUT_SECONDS = 5;

    /** 08001, sqlclient_unable_to_establish_sqlconnection: the state of a connect that did not happen. */
    private static final String CONNECT_FAILED_SQL_STATE = "08001";
    /** 53300, too_many_connections: how the standard - and postgresql - reports a server taking no further connection. */
    private static final String CONNECTION_LIMIT_SQL_STATE = "53300";
    /** 57P03, cannot_connect_now: postgresql starting up, shutting down or in recovery. */
    private static final String NOT_ACCEPTING_YET_SQL_STATE = "57P03";

    static final long MAX_BACKOFF_MS = 1000;
    static final long STALL_WARNING_AFTER_MS = 1000;
    static final long STALL_WARNING_INTERVAL_MS = 10000;

    /** How many links of the cause and getNextException() chains of a failure are looked at. */
    private static final int MAX_CHAIN_LENGTH = 32;

    /** What a connection string is cut down to where this cannot tell its credentials from the rest of it. */
    static final String CREDENTIALS_HIDDEN = "<credentials hidden>";
    /**
     * A password standing where neither the userinfo nor the parameters of a url are looked for.
     * The name goes by more than one spelling: mysql numbers the factors of a multi-factor login
     * (password1, password2), and a wallet or a key store carries one under a name of its own
     * (oracle.net.wallet_password, javax.net.ssl.keyStorePassword).
     * The value of one ends at a separator of a connection string or at the first space: a driver
     * is free to name a parameter in the middle of a sentence ("password=hunter2 for user u at
     * h:5432"), and a value class running to the end of the string would take the host, the port
     * and the cause of the failure into the blank along with the password.
     */
    private static final Pattern SECRET_PARAMETER =
        Pattern.compile("(?i)([\\w.]*(password|passwd|pwd)\\d*)\\s*=([^\\s,)&;?]*)");

    /** The parameters of a connection string worth keeping in a message: which database, not who connects. */
    private static final Set<String> IDENTIFYING_PARAMETERS = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList("databasename", "database", "instancename", "currentschema", "servicename")));

    // setNetworkTimeout() takes the executor its timeout handling runs on: the drivers it is used
    // with here only set a socket option in it, so it costs a call rather than a thread.
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    // Throttled per connection string: two JDBC backends stalling at once have a stall of their
    // own to report, and a single timestamp would let one of them starve the other. Keyed by the
    // safe form of it, the way warnedOnce below is: a static field of this class outlives every
    // borrow, and the password of the backend has no business in one.
    private static final Map<String, AtomicLong> lastStallWarning = new ConcurrentHashMap<>();
    private static final AtomicLong lastReadBoundWarning = new AtomicLong();

    // What has been reported once already. Every one of these reports a setting rather than an
    // event - a property that is not a number, a url no bound of this class can reach, a driver
    // whose property names are not known here - so it does not become truer by being repeated,
    // and every operation of the backend comes through here.
    static final Set<String> warnedOnce = ConcurrentHashMap.newKeySet();

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
        return getNonNegativeProperty(TTL_PROPERTY, DEFAULT_TTL_MS, "ms");
    }

    /**
     * Returns the value of a numeric system property, ignoring a value that is not a non-negative
     * number in favor of the default. The unit is the one the property is read in, so that the
     * value the message names is not mistaken for another.
     */
    private static long getNonNegativeProperty(String name, long defaultValue, String unit) {
        final String value = System.getProperty(name);
        if (value != null) {
            try {
                final long parsed = Long.parseLong(value.trim());
                if (parsed >= 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
            // reported once for this value: both properties are read on every borrow, so a
            // "30s" of a typo would otherwise put two lines in the log per backend operation
            warnOnce(name + "=" + value, "Ignoring invalid value \"%s\" of the %s property, using %d %s",
                value, name, defaultValue, unit);
        }
        return defaultValue;
    }

    /** Reports something about a setting once for the life of the jvm, however many borrows meet it. */
    private static void warnOnce(String key, String format, Object... args) {
        if (warnedOnce.add(key)) {
            logger.warn(LocalizableMessage.raw(format, args));
        }
    }

    /**
     * The drivers this backend is used with, recognized by the prefix of the connection string,
     * together with the properties that bound one attempt to establish a connection. Not one of
     * them bounds the attempt with a single property: the one named first covers the socket
     * connect, and the login behind it - the reads of the prelogin handshake, of TLS and of
     * authentication, the phase a proxy at its connection limit or a moved VIP leaves unanswered -
     * needs the read bound behind it. That holds for the SQL Server driver too, whose loginTimeout
     * leaves the read of the prelogin answer unbounded - and for pgjdbc, whose loginTimeout is not
     * a bound of the socket at all: Driver.connect hands the login to a daemon thread of its own
     * and abandons it at the timeout, so an unbounded read there leaks a thread and a socket per
     * borrow instead of failing one (CachedConnectionTestCase covers every one of them against a
     * socket that never answers).
     */
    enum ConnectDialect {
        /**
         * postgresql: every property of the three takes seconds. connectTimeout covers the socket
         * connect and socketTimeout the reads of the login: pgjdbc puts an SO_TIMEOUT on the login
         * socket only where socketTimeout is set (ConnectionFactoryImpl.tryConnect, both before and
         * after enableSSL), and it defaults to none. loginTimeout is kept on top of the two for a
         * url naming more than one host, where each of them costs a login of its own - the connect
         * is one budget for all of them, taken from the single System.nanoTime() in front of the
         * loop over the hosts - but it is not a bound this class could rely on alone: Driver.connect
         * runs the login on a daemon thread, gives up on the thread rather than on the login, and
         * the thread stays parked in the read for as long as the read lasts.
         */
        POSTGRES("jdbc:postgresql:", '?',
            new String[]{"connectTimeout", "loginTimeout"}, 1, 0,
            new String[]{"socketTimeout"}, 1, true,
            new int[]{}, new int[]{}),
        /** mysql: both properties take milliseconds; socketTimeout is a socket read timeout that outlives the login. */
        MYSQL("jdbc:mysql:", '?',
            new String[]{"connectTimeout"}, 1000, 0,
            new String[]{"socketTimeout"}, 1000, true,
            new int[]{1040, 1203}, new int[]{1053}),
        /** oracle: both properties take milliseconds; ReadTimeout is a socket read timeout that outlives the login. */
        ORACLE("jdbc:oracle:", '?',
            new String[]{"oracle.net.CONNECT_TIMEOUT"}, 1000, 0,
            // the read bound goes by two names the driver reads: the property set here and the
            // property of oracle net it stands for, inside a tns descriptor by the last segment of
            // either. A bound under one of them is a bound of the administrator, so ours is not set
            // on top of it - and neither of theirs is lifted with ours once the login is through.
            // Only the first of the two is a name the driver also reads out of the system
            // properties (SYSTEM_PROPERTY_NAMES below): oracle.net.READ_TIMEOUT reaches the socket
            // from the connection properties alone, so a -D of it bounds nothing and must not be
            // taken for a bound of theirs.
            // RECV_TIMEOUT is not one of them: it is a parameter of sqlnet.ora and of the listener,
            // and the name does not appear in ojdbc8 at all, so a descriptor carrying one would
            // have taken our bound off a connection that never had one of its own.
            new String[]{"oracle.jdbc.ReadTimeout", "oracle.net.READ_TIMEOUT"}, 1000, true,
            // ORA-01033 and ORA-01034: the instance is starting up or not there yet; ORA-01089:
            // it is shutting down. ORA-12514 is left out of these on purpose - a listener that
            // does not know the service is also what a service name of a typo looks like, forever
            new int[]{20, 12516, 12518, 12519, 12520}, new int[]{1033, 1034, 1089}),
        /**
         * ms sql server: loginTimeout takes seconds, socketTimeout milliseconds; the latter is a
         * socket read timeout that outlives the login. loginTimeout is the one property of the
         * four with a range of its own - SQLServerDriverIntProperty.LOGIN_TIMEOUT is validated
         * against [0, 65535], and a value beyond it fails every connect the driver is asked for.
         */
        MICROSOFT("jdbc:sqlserver:", ';',
            new String[]{"loginTimeout"}, 1, 65535,
            new String[]{"socketTimeout"}, 1000, true,
            // 921 and 922: the database has not been recovered yet, or is being recovered; 927: it
            // is in the middle of a restore; 40613: azure sql reporting it not available for now
            new int[]{17809, 10928, 10929}, new int[]{921, 922, 927, 40613});

        final String urlPrefix;
        /** the character that separates the parameters of this dialect from the url in front of them */
        final char parameterSeparator;
        /** the properties bounding the connect: the socket connect, and whatever the driver wraps it in */
        final String[] connectProperties;
        final int connectUnitsPerSecond;
        /** the largest value the driver accepts for a connect property, 0 for a driver that takes any */
        final long maxConnectSeconds;
        /** the read bound of the login: the first name is the one set here, the rest are the names it also goes by */
        final String[] readProperties;
        final int readUnitsPerSecond;
        /** whether the read bound of the login stays in force for every statement issued afterwards */
        final boolean readBoundOutlivesLogin;
        /** the vendor codes of this dialect for "no further connection is accepted" */
        final int[] connectionLimitCodes;
        /** the vendor codes of this dialect for "not accepting connections yet": a database on its way up */
        final int[] notAcceptingYetCodes;

        ConnectDialect(String urlPrefix, char parameterSeparator,
                       String[] connectProperties, int connectUnitsPerSecond, long maxConnectSeconds,
                       String[] readProperties, int readUnitsPerSecond, boolean readBoundOutlivesLogin,
                       int[] connectionLimitCodes, int[] notAcceptingYetCodes) {
            this.urlPrefix = urlPrefix;
            this.parameterSeparator = parameterSeparator;
            this.connectProperties = connectProperties;
            this.connectUnitsPerSecond = connectUnitsPerSecond;
            this.maxConnectSeconds = maxConnectSeconds;
            this.readProperties = readProperties;
            this.readUnitsPerSecond = readUnitsPerSecond;
            this.readBoundOutlivesLogin = readBoundOutlivesLogin;
            this.connectionLimitCodes = connectionLimitCodes;
            this.notAcceptingYetCodes = notAcceptingYetCodes;
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
         * Fills in the properties bounding one connect attempt, leaving out what the administrator
         * bounded themselves - an explicit setting of theirs keeps precedence, on the SQL Server,
         * mysql and oracle drivers because a supplied property outranks the url, and on postgresql
         * because the url outranks the property. A driver with a range of its own for its connect
         * property is not handed a value beyond it: a bound it rejects is no bound at all, it is a
         * connect that never happens.
         * Returns whether a read bound outliving the login was set and has to be lifted once the
         * connection is established.
         */
        boolean bound(String connectionString, Properties properties, long timeoutSeconds) {
            final long connectSeconds = maxConnectSeconds > 0
                ? Math.min(timeoutSeconds, maxConnectSeconds) : timeoutSeconds;
            // The connect side is one budget rather than a set of independent knobs, so a bound of
            // the administrator under any of its names leaves all of them alone. On postgresql
            // connectTimeout bounds the socket connect and loginTimeout the login behind it:
            // filling in the one they left out caps the one they set, and a "?connectTimeout=300"
            // answered with a loginTimeout of ours is a login pgjdbc gives up on at 30 s - Driver
            // .connect branches into its own thread as soon as loginTimeout is anything but 0.
            if (!declared(connectionString, connectProperties)) {
                for (final String property : connectProperties) {
                    properties.setProperty(property, Long.toString(connectSeconds * connectUnitsPerSecond));
                }
            }
            reportBoundTurnedOffInUrl(connectionString);
            if (!declared(connectionString, readProperties)) {
                properties.setProperty(readProperties[0], Long.toString(timeoutSeconds * readUnitsPerSecond));
                return readBoundOutlivesLogin;
            }
            return false;
        }

        /**
         * Reports a url that turns a bound off where nothing this class supplies can put one back.
         * A parameter of a postgresql url outranks the property this class hands the driver, so a
         * "socketTimeout=0" there is not a default to be replaced - it is the administrator asking
         * for an unbounded read, and a borrow that meets a database accepting the connection and
         * answering nothing is then parked with no deadline able to reach it.
         */
        private void reportBoundTurnedOffInUrl(String connectionString) {
            if (!urlOutranksProperties()) {
                return;
            }
            // Every one of them, and keyed by the property rather than by the url alone: a url
            // turns off the read bound and the login bound both ("?socketTimeout=0&loginTimeout=0",
            // where the second is the per-host budget of a failover url), and safeUrl() keeps none
            // of the timeout parameters - so a single key would report the first offender and
            // leave the administrator to find the rest of them on their own.
            for (final String[] properties : new String[][]{readProperties, connectProperties}) {
                for (final String property : properties) {
                    final String value = parameterValue(connectionString, property);
                    if (value != null && !isBound(value)) {
                        warnOnce(safeUrl(connectionString) + "|unbounded|" + property,
                            "%s sets \"%s=%s\": a parameter of a postgresql url outranks the property this backend"
                                + " supplies, so that phase of a connect carries no bound. An operation reaching a"
                                + " database that accepts the connection and does not answer stays parked",
                            safeUrl(connectionString), property, value);
                    }
                }
            }
        }

        /** Whether a vendor code of this dialect is one that waiting for the database can clear. */
        boolean isWorthRetrying(int errorCode) {
            return contains(connectionLimitCodes, errorCode) || contains(notAcceptingYetCodes, errorCode);
        }

        private static boolean contains(int[] codes, int code) {
            for (final int candidate : codes) {
                if (candidate == code) {
                    return true;
                }
            }
            return false;
        }

        // Whether the administrator bounded one of these properties themselves. The dialects
        // separate their parameters differently - "?a=1&b=2" (postgresql, mysql), ";a=1;b=2" (sql
        // server), "(A=1)" inside the descriptor of an oracle tns url, where the property also goes
        // by the last segment of its name alone - so a parameter is recognized by the delimiter in
        // front of it and the "=" behind it rather than by parsing the url syntax of every driver.
        // The connection string is not the only channel of theirs: the oracle driver reads some of
        // its properties out of the system properties as well, which is how a whole jvm is bounded
        // with -Doracle.jdbc.ReadTimeout, and a property supplied to a driver outranks the system
        // property without a word - and would then be lifted after the login as if it were ours,
        // leaving a connection with no read bound where the administrator had set one.
        private boolean declared(String connectionString, String... properties) {
            for (final String property : properties) {
                if (declaredInUrl(connectionString, property) || setAsSystemProperty(property)) {
                    return true;
                }
            }
            return false;
        }

        /** Whether the connection string bounds this property, under its own name or the last segment of it. */
        private boolean declaredInUrl(String connectionString, String property) {
            if (containsParameter(connectionString, property)) {
                return true;
            }
            final int dot = property.lastIndexOf('.');
            return dot >= 0 && containsParameter(connectionString, property.substring(dot + 1));
        }

        // Which names a driver reads out of the system properties, listed rather than told from
        // the shape of the name: ojdbc8 resolves oracle.jdbc.ReadTimeout and
        // oracle.net.CONNECT_TIMEOUT in three tiers (the properties it was supplied, then
        // System.getProperty, then the properties of the data source), while
        // oracle.net.READ_TIMEOUT - a dotted name of the same driver - is read out of the
        // connection properties alone: the six classes carrying the literal hand it to
        // Properties.get, and none of them to System.getProperty. Taking a -D of it for a bound of
        // the administrator would leave the login with no read bound at all - theirs not read by
        // the driver and ours not set, because we believed theirs was in force.
        private static final Set<String> SYSTEM_PROPERTY_NAMES = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("oracle.jdbc.ReadTimeout", "oracle.net.CONNECT_TIMEOUT")));

        private static boolean setAsSystemProperty(String property) {
            return SYSTEM_PROPERTY_NAMES.contains(property) && isBound(System.getProperty(property));
        }

        // pgjdbc parses the url over the properties it was handed - Driver.connect copies them
        // into a flat map and parseURL then writes the parameters of the url on top - so a value
        // standing in a postgresql url is the value the driver uses, and the one supplied here
        // never reaches the socket. A zero there is not a default of the driver to be replaced: it
        // cannot be replaced, and setting ours on top of it would leave this class lifting a read
        // bound the login never had. The other three let a supplied property win, so a zero of
        // theirs is ours to override.
        private boolean urlOutranksProperties() {
            return this == POSTGRES;
        }

        /** Whether this property is bounded by the connection string, as the driver of this dialect reads it. */
        private boolean containsParameter(String connectionString, String property) {
            final String value = parameterValue(connectionString, property);
            return value != null && (urlOutranksProperties() || isBound(value));
        }

        // Matched the way the driver of this dialect matches it: pgjdbc and Connector/J look their
        // properties up by their exact name - PropertyKey.fromValue answers null for a name of
        // another case and the parameter is then a parameter of nobody, so "?SocketTimeout=" must
        // not be taken for a bound of the administrator - while the SQL Server driver
        // (getNormalizedPropertyName) and the keywords of an oracle descriptor match either way.
        private String parameterValue(String connectionString, String property) {
            final boolean exact = this == POSTGRES || this == MYSQL;
            final String url = exact ? connectionString : connectionString.toLowerCase(Locale.ROOT);
            final String name = exact ? property : property.toLowerCase(Locale.ROOT);
            String value = null;
            for (int i = url.indexOf(name); i >= 0; i = url.indexOf(name, i + name.length())) {
                final int end = i + name.length();
                if (i > 0 && "?&;(,".indexOf(url.charAt(i - 1)) >= 0 && end < url.length() && url.charAt(end) == '=') {
                    // the last of them: a driver parsing a url into a map lets the last assignment stand
                    value = valueOf(url, end + 1);
                }
            }
            return value;
        }

        /** The value of the parameter that starts here: up to the delimiter in front of the next one. */
        private static String valueOf(String url, int from) {
            int end = from;
            while (end < url.length() && "&;),?".indexOf(url.charAt(end)) < 0) {
                end++;
            }
            return url.substring(from, end);
        }

        /**
         * Whether a value of the administrator bounds anything. Every one of these drivers reads 0
         * as "wait as long as it takes", so a property set to it is not a bound of theirs to stay
         * out of the way of - it is the default this class exists to replace, and one of ours goes
         * on top of it wherever a supplied property outranks the url. Where it does not, on
         * postgresql, the zero stands and is reported instead of being written over. A value that
         * is no number is left to the driver it belongs to.
         */
        private static boolean isBound(String value) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }
            try {
                return Double.parseDouble(value.trim()) != 0; // pgjdbc takes a float for its loginTimeout
            } catch (NumberFormatException notANumber) {
                return true;
            }
        }
    }

    final String connectionString;
    /**
     * Whether this connection may go back into the pool once it is closed. A connection carrying a
     * read bound that could not be lifted serves the borrower waiting for it and is closed
     * afterwards: left in the pool it would fail every statement slower than that bound - an
     * import batch among them - for every borrow the pool hands it to.
     */
    private final boolean poolable;

    public CachedConnection(String connectionString, Connection parent) {
        this(connectionString, parent, true);
    }

    CachedConnection(String connectionString, Connection parent, boolean poolable) {
        this.connectionString = connectionString;
        this.parent = parent;
        this.poolable = poolable;
    }

    /**
     * Borrows a connection: a usable one out of the pool, or a newly established one. Bounded in
     * both phases - every operation of this backend, the open of a backend and the import
     * included, comes through here, and an unbounded borrow turns a database that listens but does
     * not answer into a hang rather than into an error the caller can report.
     */
    static Connection getConnection(String connectionString) throws Exception {
        final ConnectDialect dialect = ConnectDialect.of(connectionString);
        reportUnknownDialect(connectionString, dialect);
        final long connectTimeoutSeconds = Math.min(
            getNonNegativeProperty(CONNECT_TIMEOUT_PROPERTY, DEFAULT_CONNECT_TIMEOUT_SECONDS, "s"),
            Integer.MAX_VALUE / 1000);
        final long poolTimeoutSeconds = getNonNegativeProperty(POOL_TIMEOUT_PROPERTY, DEFAULT_POOL_TIMEOUT_SECONDS, "s");
        final long startedAt = System.currentTimeMillis();
        final long deadline = (poolTimeoutSeconds == 0 || poolTimeoutSeconds >= Long.MAX_VALUE / 1000)
            ? Long.MAX_VALUE : startedAt + poolTimeoutSeconds * 1000;
        long waitMs = 0;
        long backoffMs = 0;
        int attempts = 0;
        while (true) {
            final CachedConnection pooled = poll(connectionString, waitMs, deadline);
            if (pooled != null) {
                return pooled;
            }
            attempts++;
            try {
                return connect(connectionString, dialect, attemptSeconds(connectTimeoutSeconds, deadline));
            } catch (SQLException e) {
                // A database that takes no connection for the moment is the failure worth waiting
                // out: it is at its connection limit, and one of ours is going to come back to the
                // pool - or it is on its way up, and the state clears itself in seconds. Everything
                // else - a password that is not accepted, a database that is down, a driver that is
                // not on the classpath - is reported to the caller instead of being retried behind
                // its back.
                if (!isWorthRetrying(e, dialect)) {
                    throw reported(e, connectionString);
                }
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    // 08001, the state of a connect that did not happen, rather than none at all:
                    // this is the failure of a borrow, and a caller reading the state of what it
                    // caught would otherwise see null where the driver's own exception carried one
                    final SQLTimeoutException timeout = new SQLTimeoutException("no connection to "
                        + safeUrl(connectionString) + " could be borrowed within " + poolTimeoutSeconds + "s ("
                        + attempts + " attempts): the database took no connection for the moment and none was"
                        + " returned to the pool, last error: " + redact(e.getMessage(), connectionString),
                        CONNECT_FAILED_SQL_STATE);
                    timeout.initCause(reported(e, connectionString));
                    throw timeout;
                }
                backoffMs = Math.min(backoffMs == 0 ? 1 : backoffMs * 2, MAX_BACKOFF_MS);
                waitMs = Math.min(backoffMs, remaining);
                warnStall(connectionString, attempts, startedAt, e);
            } catch (RuntimeException e) {
                // a driver reporting a connect it will not make as an unchecked failure carries the
                // connection string of the backend in its message as readily as a SQLException does
                throw reportedUnchecked(e, connectionString);
            }
        }
    }

    /**
     * Reports a connection string this class knows no bound for. The properties bounding a connect
     * are the ones of a driver, so a driver outside the four leaves every attempt unbounded - and
     * the deadline of the borrow cannot reach into a connect that is already under way, since the
     * driver is the only thing holding the socket.
     */
    private static void reportUnknownDialect(String connectionString, ConnectDialect dialect) {
        if (dialect != null) {
            return;
        }
        final StringBuilder known = new StringBuilder();
        for (final ConnectDialect candidate : ConnectDialect.values()) {
            known.append(known.length() > 0 ? ", " : "").append(candidate.urlPrefix);
        }
        warnOnce(safeUrl(connectionString) + "|unknown-dialect",
            "%s names a driver whose timeout properties are not known to this backend (%s are): a connect to a"
                + " database that accepts it and does not answer is left without a bound, and the %s property"
                + " cannot end it",
            safeUrl(connectionString), known, POOL_TIMEOUT_PROPERTY);
    }

    /**
     * The bound of one connect attempt. The deadline of the borrow bounds it as well - the
     * {@value #POOL_TIMEOUT_PROPERTY} property stands for the whole borrow, and an attempt of its
     * own left to run out would overrun it by a full connect timeout. That holds for an attempt
     * the {@value #CONNECT_TIMEOUT_PROPERTY} property gives no bound of its own, too: turning the
     * per-attempt bound off must not turn the bound of the borrow off with it. Never 0 for an
     * attempt that is bounded at all: 0 is the value that stands for no bound. And never past what
     * an int of milliseconds takes - the pool timeout has no upper bound of its own, while the SQL
     * Server driver rejects a socketTimeout beyond Integer.MAX_VALUE outright, failing every
     * connect of that backend with the name of a property nobody typed.
     */
    static long attemptSeconds(long connectTimeoutSeconds, long deadline) {
        if (deadline == Long.MAX_VALUE) {
            // 0 stands for an attempt with no bound of its own and stays 0; anything else is
            // clamped here as well, so that the range holds whichever branch answers
            return connectTimeoutSeconds == 0 ? 0 : Math.min(connectTimeoutSeconds, Integer.MAX_VALUE / 1000);
        }
        final long remainingSeconds = (deadline - System.currentTimeMillis() + 999) / 1000;
        final long bound = connectTimeoutSeconds == 0
            ? remainingSeconds : Math.min(connectTimeoutSeconds, remainingSeconds);
        return Math.max(1, Math.min(bound, Integer.MAX_VALUE / 1000));
    }

    /**
     * Takes a usable connection out of the pool, waiting up to waitMs for one to be returned to it.
     * The validation of a connection costs a round trip, and the pool has no upper bound on the
     * number of them it holds, so draining a pool the database no longer answers is given the
     * deadline of the borrow as well: past it, establishing a connection is the faster answer.
     * The connection in hand is always validated first, whatever the deadline says - a database at
     * its connection limit has no other source of connections than the ones coming back, and one
     * returned to the pool a moment before the deadline is the very connection this borrow waited
     * for. Only a connection the database no longer answers is closed here.
     */
    private static CachedConnection poll(String connectionString, long waitMs, long deadline) throws InterruptedException {
        CachedConnection con = cached.get(connectionString).poll(waitMs, TimeUnit.MILLISECONDS);
        while (con != null) {
            if (isUsable(con)) {
                return con;
            }
            closeQuietly(con.parent);
            if (System.currentTimeMillis() >= deadline) {
                return null;
            }
            con = cached.get(connectionString).poll();
        }
        return null;
    }

    private static boolean isUsable(CachedConnection con) {
        // The validation needs a bound of its own: isValid(0) means "no timeout" in the JDBC
        // contract, and a connection whose socket is half-open answers it no sooner than it
        // answers anything else. isValid(n) is not that bound on every driver either - the SQL
        // Server driver turns it into a query timeout (setQueryTimeout, then "SELECT 1"), which
        // needs an answer from the server to fire at all - so the socket is bounded here, for the
        // validation only.
        final int restore = boundValidation(con.parent);
        if (restore == VALIDATION_BOUND_FAILED) {
            // the bound of the validation is not in force, and the driver may well have applied it
            // before failing: validating here would be the unbounded isValid() this exists to
            // avoid, and pooling it would hand out a connection carrying a bound of ours
            return false;
        }
        boolean usable;
        try {
            usable = con.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException | RuntimeException e) { // a driver reporting the validation as an error: discard it
            // an unchecked failure out of a driver would unwind through poll(), which stands
            // outside every try of the borrow, and leave this connection dequeued and unclosed
            usable = false;
        }
        if (!usable) {
            // On its way out, and the driver knows it: Connector/J answers a failed validation by
            // aborting the connection and the SQL Server driver by terminating it, so putting the
            // previous bound back would fail as well - and warn about a bound of a connection that
            // is about to be closed, over a reaped idle connection that is nobody's problem.
            return false;
        }
        if (restore >= 0 && !setNetworkTimeout(con.parent, restore,
                "the connection is closed rather than pooled")) {
            return false; // it would carry the bound of the validation into every statement
        }
        return true;
    }

    /** A connection left alone by {@link #boundValidation}: no bound of ours to put back afterwards. */
    private static final int VALIDATION_BOUND_LEFT_ALONE = -1;
    /** A connection {@link #boundValidation} could not bound, which may still carry the bound it failed to report. */
    private static final int VALIDATION_BOUND_FAILED = -2;

    /**
     * Bounds the socket of a pooled connection for the length of its validation, returning the
     * network timeout to put back afterwards - or {@link #VALIDATION_BOUND_LEFT_ALONE} for a
     * connection left alone, either because the driver does not take a network timeout or because
     * it is bounded at least as tightly already, by a read timeout of the connection string that
     * is not ours to widen.
     * A driver that takes the call and then fails inside it is told apart from both: it is free to
     * have applied the bound before failing, and a connection put back into the pool carrying five
     * seconds of ours fails every statement slower than that for the rest of its life.
     */
    private static int boundValidation(Connection con) {
        final int bound = VALIDATION_TIMEOUT_SECONDS * 1000;
        final int previous;
        try {
            previous = con.getNetworkTimeout();
        } catch (SQLException | RuntimeException e) { // a driver that does not take one: nothing was changed
            return VALIDATION_BOUND_LEFT_ALONE;
        }
        if (previous > 0 && previous <= bound) {
            return VALIDATION_BOUND_LEFT_ALONE;
        }
        try {
            con.setNetworkTimeout(DIRECT_EXECUTOR, bound);
        } catch (SQLException | RuntimeException e) {
            return VALIDATION_BOUND_FAILED;
        }
        // A driver answering a negative timeout is outside the contract of getNetworkTimeout(),
        // where 0 stands for no limit and nothing below it stands for anything. Handed back as it
        // is, it would be one of the two sentinels above: the bound just set would be read as a
        // bound that was never set, and the connection would go into the pool carrying five
        // seconds of ours into every statement of whoever borrows it next.
        return previous < 0 ? 0 : previous;
    }

    static CachedConnection connect(String connectionString, ConnectDialect dialect, long connectTimeoutSeconds)
            throws SQLException {
        // A driver is free to write into the map it is handed, so it gets one of its own.
        final Properties properties = new Properties();
        final boolean readBoundSet = dialect != null && connectTimeoutSeconds > 0
            && dialect.bound(connectionString, properties, connectTimeoutSeconds);
        final Connection conNew = DriverManager.getConnection(connectionString, properties);
        boolean poolable = true;
        try {
            // still under the read bound: both of these are round trips of their own
            conNew.setAutoCommit(false);
            conNew.setTransactionIsolation(TRANSACTION_READ_COMMITTED);
            if (readBoundSet) {
                // a driver that will not take the bound back has warned about it already: the
                // connection serves the borrower that is waiting for it and is closed rather than
                // pooled, so the bound of the login does not outlive it in the pool
                poolable = relaxReadBound(conNew, connectTimeoutSeconds);
            }
        } catch (SQLException | RuntimeException e) { // nothing holds this connection yet: it would leak
            closeQuietly(conNew);
            throw e;
        }
        return new CachedConnection(connectionString, conNew, poolable);
    }

    // The second bound of the login is a socket read timeout on mysql, oracle and sql server, in
    // force for the whole life of the connection: left in place it would break every statement
    // slower than it - an import batch, the statistics of a freshly loaded table - so it is lifted
    // as soon as the login is through, restoring the behaviour of a connection this class
    // established before. A read bound the connection string sets itself is never touched here:
    // it is not set at all, so nothing of the administrator's is lifted along with it. Returns
    // whether the bound is gone - a connection still carrying it must not be pooled.
    // Named by the bound the login was given rather than by the property it came from: with
    // CONNECT_TIMEOUT_PROPERTY at 0 the attempt takes its bound from what is left of the deadline
    // of the borrow, so naming that property would point at the one setting that is not in force.
    private static boolean relaxReadBound(Connection con, long boundSeconds) {
        return setNetworkTimeout(con, 0, "statements taking longer than the " + boundSeconds
            + "s the login of this connection was bounded by fail on it, and it is closed rather than pooled");
    }

    /**
     * Puts a network timeout on a connection, reporting a driver that will not take one. The
     * consequence is the caller's to name: the same failure ends a freshly established connection
     * carrying the read bound of its login and a pooled one whose bound could not be put back.
     */
    private static boolean setNetworkTimeout(Connection con, int millis, String consequence) {
        try {
            con.setNetworkTimeout(DIRECT_EXECUTOR, millis);
            return true;
        } catch (SQLException | RuntimeException e) {
            // Throttled rather than reported once for the life of the JVM: every connection this
            // happens to carries a read bound it was never meant to keep, and a statement dying of
            // it hours later needs a warning of its own to be traced back to here.
            final long now = System.currentTimeMillis();
            final long last = lastReadBoundWarning.get();
            if (now - last >= STALL_WARNING_INTERVAL_MS && lastReadBoundWarning.compareAndSet(last, now)) {
                logger.warn(LocalizableMessage.raw(
                    "The read bound of a JDBC connection could not be set to %d ms (%s): %s",
                    millis, e.getMessage(), consequence));
            }
            return false;
        }
    }

    /**
     * Whether the database took no connection for the moment, rather than refusing one for good:
     * it is at its connection limit - one of our own connections is on its way back to the pool -
     * or it is not accepting connections yet, the state a database on its way up reports while it
     * recovers - the one JDBCStorage.open() has no second attempt of its own for, so a backend
     * that meets it stays locked down until the server is restarted. Both clear themselves in
     * seconds; every other failure is the caller's to see.
     */
    static boolean isWorthRetrying(SQLException e, ConnectDialect dialect) {
        // a failure of the driver is often wrapped, and a SQLException carries two chains of its
        // own: the causes behind it and the further exceptions of getNextException()
        final Deque<Throwable> pending = new ArrayDeque<>();
        final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        enqueue(pending, visited, e);
        for (int links = 0; !pending.isEmpty() && links < MAX_CHAIN_LENGTH; links++) {
            final Throwable t = pending.poll();
            if (t instanceof SQLException) {
                final SQLException sql = (SQLException) t;
                final String sqlState = sql.getSQLState();
                if (CONNECTION_LIMIT_SQL_STATE.equals(sqlState) || NOT_ACCEPTING_YET_SQL_STATE.equals(sqlState)
                    || (dialect != null && dialect.isWorthRetrying(sql.getErrorCode()))) {
                    return true;
                }
                enqueue(pending, visited, sql.getNextException());
            }
            enqueue(pending, visited, t.getCause());
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
        final AtomicLong lastOfThisUrl =
            lastStallWarning.computeIfAbsent(safeUrl(connectionString), url -> new AtomicLong());
        final long last = lastOfThisUrl.get();
        if (now - last >= STALL_WARNING_INTERVAL_MS && lastOfThisUrl.compareAndSet(last, now)) {
            logger.warn(LocalizableMessage.raw("%s", stallMessage(connectionString, attempts, now - startedAt, cause)));
        }
    }

    /**
     * The stall as it reaches the log. Built apart from the logging of it so that the rule it has
     * to keep - neither the connection string nor the message of the driver reaches a log as it
     * stands - is a rule a test can hold it to.
     */
    static String stallMessage(String connectionString, int attempts, long waitedMs, SQLException cause) {
        return String.format("%s takes no further connection: waiting %d ms for a pooled one so far (%d attempts),"
            + " last error: %s", safeUrl(connectionString), waitedMs, attempts,
            redact(cause.getMessage(), connectionString));
    }

    /**
     * The failure of a connect as it may leave this class: the exception itself where nothing of it
     * names the credentials of the backend, and a redacted rebuild of its whole chain where
     * something does. Rebuilt rather than wrapped: a wrapper keeps its cause, and everything that
     * prints a failure prints the causes along with it - a debug build of
     * stackTraceToSingleLineString walks them, the config manager traces them, and
     * RootContainer.open() makes the message of the cause the message of what it throws - so a
     * link left as it stands would carry the password past the wrapper. The SQLState and the
     * vendor code of every link survive it: they are what tells a caller what happened.
     */
    static SQLException reported(SQLException e, String connectionString) {
        return holdsCredentials(e, connectionString)
            ? redactedCopy(e, connectionString, new int[] { MAX_CHAIN_LENGTH })
            : e;
    }

    /** The same of an unchecked failure: a driver is free to report a connect it will not make as one. */
    static Exception reportedUnchecked(RuntimeException e, String connectionString) {
        if (!holdsCredentials(e, connectionString)) {
            return e;
        }
        final SQLException redacted = new SQLNonTransientConnectionException(e.getClass().getName()
            + (e.getMessage() == null ? "" : ": " + redact(e.getMessage(), connectionString)),
            CONNECT_FAILED_SQL_STATE);
        redacted.setStackTrace(e.getStackTrace());
        return redacted;
    }

    /**
     * Whether anything in the chain of a failure names what a connection string keeps out of the
     * log. A chain longer than this walk is given answers "yes": what is reported unredacted is
     * what this class has looked at whole, and a link it never reached is not that. The cost of
     * being wrong that way is a chain rebuilt - bounded in its turn - while the cost of being
     * wrong the other way is the password of the backend in the server error log.
     */
    private static boolean holdsCredentials(Throwable failure, String connectionString) {
        final Deque<Throwable> pending = new ArrayDeque<>();
        final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        enqueue(pending, visited, failure);
        for (int links = 0; !pending.isEmpty(); links++) {
            if (links >= MAX_CHAIN_LENGTH) {
                return true;
            }
            final Throwable t = pending.poll();
            final String message = t.getMessage();
            if (message != null && !message.equals(redact(message, connectionString))) {
                return true;
            }
            if (t instanceof SQLException) {
                enqueue(pending, visited, ((SQLException) t).getNextException());
            }
            enqueue(pending, visited, t.getCause());
        }
        return false;
    }

    // By identity rather than by equals(): a link of a chain carries a cause and a next exception
    // both, and a driver is free to make the two the same failure. Enqueued twice, a chain of
    // those fans out into a copy of itself at every step and spends the budget of a walk on links
    // it has already looked at - five levels of one are enough to exhaust MAX_CHAIN_LENGTH.
    private static void enqueue(Deque<Throwable> pending, Set<Throwable> visited, Throwable t) {
        if (t != null && visited.add(t)) {
            pending.add(t);
        }
    }

    // The budget counts the links this rebuilds, the way holdsCredentials() counts the ones it
    // visits - not how deep it has gone. A link of a chain carries a cause and a next exception
    // both, and a driver is free to make them the same failure, so a bound on depth alone leaves
    // room for a chain that fans out into two copies of itself at every step.
    private static SQLException redactedCopy(SQLException e, String connectionString, int[] budget) {
        budget[0]--;
        final SQLException copy =
            new SQLException(redact(e.getMessage(), connectionString), e.getSQLState(), e.getErrorCode());
        copy.setStackTrace(e.getStackTrace());
        if (e.getNextException() != null) {
            copy.setNextException(budget[0] > 0
                ? redactedCopy(e.getNextException(), connectionString, budget) : droppedTail());
        }
        if (e.getCause() != null) {
            copy.initCause(budget[0] > 0 ? redactedLink(e.getCause(), connectionString, budget) : droppedTail());
        }
        return copy;
    }

    // What stands where the budget ran out. Without it the same failure logs its root cause when
    // the url of the backend has no password in it and loses it without a word when it has, which
    // is a report of a connect nobody can read against a report of one they can.
    private static SQLException droppedTail() {
        return new SQLException("the rest of this failure was left out: a chain of more than "
            + MAX_CHAIN_LENGTH + " links is rebuilt only that far");
    }

    // A link that is no SQLException keeps its class name in the message: its type is not one this
    // can rebuild, and the name of the failure is what a reader of the log is after.
    private static Throwable redactedLink(Throwable t, String connectionString, int[] budget) {
        if (t instanceof SQLException) {
            return redactedCopy((SQLException) t, connectionString, budget);
        }
        budget[0]--;
        final Throwable copy = new Throwable(t.getClass().getName()
            + (t.getMessage() == null ? "" : ": " + redact(t.getMessage(), connectionString)));
        copy.setStackTrace(t.getStackTrace());
        if (t.getCause() != null) {
            copy.initCause(budget[0] > 0 ? redactedLink(t.getCause(), connectionString, budget) : droppedTail());
        }
        return copy;
    }

    /**
     * A message of a driver as it may be logged. A driver is free to put the connection string it
     * was handed into it - the jdk itself does, "No suitable driver found for " + url, which is
     * what the ordinary oracle misconfiguration of a driver jar left out of lib/extensions arrives
     * as - and that connection string is where the credentials of this backend live.
     * What it cannot answer for is a driver quoting back a part of a url it failed to parse:
     * a whole credential is replaced, a fragment of one is not.
     */
    static String redact(String message, String connectionString) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String redacted = message.replace(connectionString, safeUrl(connectionString));
        // The parameter before the values: a password blanked here is one the loop below no longer
        // finds, while the other way round a "<credentials hidden>" standing behind a "password="
        // would be cut in half by a pattern that ends its value at the first space.
        redacted = SECRET_PARAMETER.matcher(redacted).replaceAll("$1=***");
        for (final String secret : secretsOf(connectionString)) {
            redacted = secretPattern(secret).matcher(redacted)
                .replaceAll(Matcher.quoteReplacement(CREDENTIALS_HIDDEN));
        }
        return redacted;
    }

    /**
     * A secret as it is looked for in a message: the value itself, wherever it does not stand
     * inside a longer run of letters and digits. A password is free to be one character long, and
     * a bare one of those is a substring of half the lines a driver writes - a password of "1"
     * takes "ORA-12541: TNS:no listener" apart into a line nobody can read, and it makes every
     * failure of that backend one this class believes names the credentials, so the whole chain is
     * rebuilt as well. A redaction that destroys the diagnostic it protects is the worse of the
     * two failures. What a driver quotes back is a credential standing on its own - between the
     * delimiters of a url, or in a sentence of its own - and that is still replaced.
     */
    private static Pattern secretPattern(String secret) {
        final String before = isAlphanumeric(secret.charAt(0)) ? "(?<![A-Za-z0-9])" : "";
        final String after = isAlphanumeric(secret.charAt(secret.length() - 1)) ? "(?![A-Za-z0-9])" : "";
        return Pattern.compile(before + Pattern.quote(secret) + after);
    }

    private static boolean isAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /** What of a connection string must not stand in a message: the credentials safeUrl() takes out of it. */
    private static List<String> secretsOf(String connectionString) {
        final List<String> secrets = new ArrayList<>();
        final ConnectDialect dialect = ConnectDialect.of(connectionString);
        final String separators = dialect == null ? "?;" : String.valueOf(dialect.parameterSeparator);
        final int scheme = connectionString.indexOf(':', "jdbc:".length()) + 1;
        if (scheme <= 0) {
            return secrets;
        }
        final int authority = startOfAuthority(connectionString, scheme);
        if (authority < 0) {
            final int at = connectionString.indexOf('@', scheme);
            if (at > scheme) {
                addSecret(secrets, connectionString.substring(credentialsStart(connectionString, scheme, at), at));
            }
        } else {
            final int end = endOfAuthority(connectionString, authority, separators);
            for (final String host : connectionString.substring(authority, end).split(",", -1)) {
                final int at = host.lastIndexOf('@');
                if (at > 0) {
                    addSecret(secrets, host.substring(0, at));
                }
            }
        }
        final Matcher parameter = SECRET_PARAMETER.matcher(connectionString);
        while (parameter.find()) {
            addSecret(secrets, parameter.group(3));
        }
        return secrets;
    }

    // The credentials of one host, and the password inside them without the user name in front of
    // it: a driver quoting a url back names either.
    private static void addSecret(List<String> secrets, String credentials) {
        if (credentials.isEmpty()) {
            return;
        }
        secrets.add(credentials);
        final int password = indexOfAny(credentials, ":/", 0);
        if (password >= 0 && password + 1 < credentials.length()) {
            secrets.add(credentials.substring(password + 1));
        }
    }

    // The connection string carries the credentials of the backend - JDBCStorage hands the whole
    // db-directory of the configuration to this class, so the url is the only place they live -
    // and it is never logged as it stands. Three shapes hold them and all three are taken off: the
    // "user/password@" in front of an oracle descriptor; the userinfo of an authority, one per
    // host of it, since a url of Connector/J gives every host credentials of its own
    // ("//u:p@h1:3306,u2:p2@h2:3306"); and the parameters behind their first separator,
    // "?user=...&password=..." on postgresql, mysql and oracle, ";password=..." on sql server.
    // What is left is looked over once more: the key-value host syntax of Connector/J puts a
    // password inside the authority itself ("//address=(host=h)(user=u)(password=p)"), where
    // neither of the first two shapes stands, so a "password=" of any case is blanked out wherever
    // it is left standing.
    //
    // A password is free to hold either of the delimiters, so neither of them is looked for in the
    // whole string. The credentials of an oracle url stand between the subprotocol and the first
    // "@", which is the delimiter of its descriptor, so a "?" of one is part of the password
    // rather than the start of the parameters. Everywhere else they stand inside the authority,
    // between "//" and the path behind it, so a "?" of a password is inside them and an "@" of a
    // parameter value ("?user=u@example.com") is not mistaken for the end of them: the host
    // survives in the message either way.
    //
    // And a url none of this took apart is not logged past its subprotocol. An "@" left standing
    // anywhere but where the credentials of an oracle url ended is one this did not recognize - a
    // password holding a "/" inside an authority, a quoted one holding an "@" - and the host of a
    // stall report is worth less than a password in the server log.
    static String safeUrl(String connectionString) {
        final ConnectDialect dialect = ConnectDialect.of(connectionString);
        final String separators = dialect == null ? "?;" : String.valueOf(dialect.parameterSeparator);
        final int scheme = connectionString.indexOf(':', "jdbc:".length()) + 1; // the end of "jdbc:<subprotocol>:"
        if (scheme <= 0) {
            return CREDENTIALS_HIDDEN;
        }
        final String stripped = stripCredentials(connectionString, scheme, separators);
        final int parameters = indexOfAny(stripped, separators, scheme);
        final String url = parameters < 0 ? stripped : stripped.substring(0, parameters);
        final String redacted = SECRET_PARAMETER.matcher(url).replaceAll("$1=***");
        // an "@" standing anywhere but where the credentials of an oracle url ended is one this
        // did not recognize - a password holding a "/" inside an authority, a quoted one holding
        // an "@" - and the host of a stall report is worth less than a password in the server log
        if (redacted.lastIndexOf('@') > endOfRecognizedCredentials(connectionString, scheme)) {
            return redacted.substring(0, scheme) + CREDENTIALS_HIDDEN;
        }
        return parameters < 0 ? redacted
            : redacted + identifyingParameters(stripped.substring(parameters), dialect);
    }

    /**
     * The parameters worth keeping in a message: the ones naming the database rather than whoever
     * connects to it. Two backends of one host answer to the same url up to their parameters, and
     * a stall report that cannot tell them apart is a stall report of neither. Everything else is
     * dropped rather than looked at - a name this does not know is a name free to carry a secret.
     */
    private static String identifyingParameters(String parameters, ConnectDialect dialect) {
        final char separator = dialect == null ? ';' : dialect.parameterSeparator;
        final StringBuilder kept = new StringBuilder();
        for (final String parameter : parameters.split("[?&;]")) {
            final int equals = parameter.indexOf('=');
            if (equals > 0
                && IDENTIFYING_PARAMETERS.contains(parameter.substring(0, equals).toLowerCase(Locale.ROOT))) {
                kept.append(kept.length() == 0 || separator != '?' ? separator : '&').append(parameter);
            }
        }
        return kept.toString();
    }

    private static String stripCredentials(String url, int scheme, String separators) {
        final int authority = startOfAuthority(url, scheme);
        if (authority < 0) {
            // no authority: the credentials of an oracle url stand between the subprotocol and the
            // first "@", which is the delimiter of the descriptor behind it - a password holding
            // an "@" of its own has to be quoted for the driver itself
            final int at = url.indexOf('@', scheme);
            return at < 0 ? url : url.substring(0, credentialsStart(url, scheme, at)) + url.substring(at);
        }
        final int end = endOfAuthority(url, authority, separators);
        return url.substring(0, authority) + withoutUserinfo(url.substring(authority, end)) + url.substring(end);
    }

    /**
     * Where the credentials of a url that names no authority start: behind the token naming the
     * kind of driver, which stands in front of them ("jdbc:oracle:thin:user/pw@...") and is worth
     * keeping - thin against oci is a first question of an oracle connect. The token is the one
     * right behind the subprotocol rather than the last one in front of the "@", since a password
     * is free to hold a ":" of its own.
     */
    private static int credentialsStart(String url, int scheme, int at) {
        final int driverType = url.indexOf(':', scheme);
        return driverType >= 0 && driverType < at ? driverType + 1 : scheme;
    }

    /**
     * The last position a stripped url may still carry an "@" at: where the credentials of an
     * oracle url ended, since the "@" is the delimiter of the descriptor behind them and stays.
     * An authority keeps none of its own - every userinfo of it is taken off, delimiter included.
     */
    private static int endOfRecognizedCredentials(String url, int scheme) {
        final int at = url.indexOf('@', scheme);
        return startOfAuthority(url, scheme) < 0 && at > scheme ? credentialsStart(url, scheme, at) : scheme;
    }

    /**
     * Where the hosts of a url of this shape start, or -1 for a url that names no authority. The
     * subprotocol is free to name the kind of connection in front of it - "jdbc:mysql:replication://"
     * - so the "//" is looked for rather than expected right behind the subprotocol. An "@" in
     * front of it belongs to an oracle url ("jdbc:oracle:thin:user/pw@//host"), whose credentials
     * stand where an authority has no place for them.
     */
    private static int startOfAuthority(String url, int scheme) {
        final int slashes = url.indexOf("//", scheme);
        if (slashes < 0 || url.lastIndexOf('@', slashes) >= scheme) {
            return -1;
        }
        // ... and so does a "/" in front of them: it is what separates the credentials of an
        // oracle url ("thin:user/pw@..."), so a password holding a "//" of its own would start an
        // authority inside itself. The "@" ending the credentials stands behind that point, the
        // userinfo taken off is a piece of the password rather than the whole of it, and the "@"
        // the guard of safeUrl() looks for is gone with it - leaving the user name and the head of
        // the password in the message of a stall.
        final int slash = url.indexOf('/', scheme);
        return slash >= 0 && slash < slashes ? -1 : slashes + 2;
    }

    /**
     * Where the hosts of an authority end: at the path behind them - a password holds a "?" more
     * readily than a "/" - or at the first parameter of a url that has no path.
     */
    private static int endOfAuthority(String url, int authority, String separators) {
        final int path = url.indexOf('/', authority);
        if (path >= 0) {
            return path;
        }
        final int parameter = indexOfAny(url, separators, authority);
        return parameter < 0 ? url.length() : parameter;
    }

    /** The hosts of an authority, each of them without the credentials a url may give it. */
    private static String withoutUserinfo(String authority) {
        final StringBuilder hosts = new StringBuilder();
        final String[] split = authority.split(",", -1);
        for (int i = 0; i < split.length; i++) {
            if (i > 0) { // by the position rather than by what is in hand: a first host may be empty
                hosts.append(',');
            }
            final int at = split[i].lastIndexOf('@');
            hosts.append(at < 0 ? split[i] : split[i].substring(at + 1));
        }
        return hosts.toString();
    }

    private static int indexOfAny(String url, String separators, int from) {
        int found = -1;
        for (int i = 0; i < separators.length(); i++) {
            final int at = url.indexOf(separators.charAt(i), from);
            if (at >= 0 && (found < 0 || at < found)) {
                found = at;
            }
        }
        return found;
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
        if (!poolable) {
            closeQuietly(parent);
            return;
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
