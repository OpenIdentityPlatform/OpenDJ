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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import java.sql.*;
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

    /** The greatest number of connections one pool holds to one database; 0 for no bound. */
    static final String POOL_MAX_PROPERTY = "org.openidentityplatform.opendj.jdbc.pool.max";
    /**
     * Sized like the worker thread pool of the server ({@code Platform.computeNumberOfThreads(16, 2)}),
     * since an operation borrows one connection for its duration: the bound is there to keep a burst
     * from opening as many connections as the database will accept, not to throttle steady traffic.
     */
    static final int DEFAULT_POOL_MAX = Math.max(16, Runtime.getRuntime().availableProcessors() * 2);

    /** How long a borrow waits for a connection to be returned before looking at the pool again. */
    private static final long POOL_FULL_POLL_MS = 250;
    /** The sweep runs at half the TTL, and no more often than this. */
    private static final long MIN_SWEEP_INTERVAL_MS = 1000;

    static final long MAX_BACKOFF_MS = 1000;
    static final long STALL_WARNING_AFTER_MS = 1000;
    static final long STALL_WARNING_INTERVAL_MS = 10000;

    // setNetworkTimeout() takes the executor its timeout handling runs on: the drivers it is used
    // with here only set a socket option in it, so it costs a call rather than a thread.
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private static final AtomicLong lastStallWarning = new AtomicLong();
    private static final AtomicLong lastPoolFullWarning = new AtomicLong();
    private static final AtomicBoolean readBoundWarned = new AtomicBoolean();

    final Connection parent;

    /** The pool this connection belongs to, held directly so that the return needs no lookup. */
    private final Pool pool;
    /** Whether this connection holds a permit of its pool: a reentrant borrow does not. */
    private final boolean metered;
    /** The thread that borrowed it, so that a return on another thread does not corrupt the count. */
    private volatile Thread owner;
    /** When it was last returned to the pool, which is what the TTL is measured from. */
    volatile long returnedAtMillis;
    private final AtomicBoolean permitReleased = new AtomicBoolean();
    /** Whether it has been handed back already: JDBC makes close() on a closed connection a no-op. */
    private final AtomicBoolean returned = new AtomicBoolean();

    /** The pool of every connection string in use, kept until the last storage using it closes. */
    static final ConcurrentMap<String, Pool> pools = new ConcurrentHashMap<>();

    /** The sweep that closes connections nothing has borrowed for the TTL, started with the first pool. */
    private static volatile ScheduledExecutorService sweeper;

    /** Where the sweep closes what it reaped, so that a close which does not return keeps it: see {@link Pool#sweep}. */
    private static volatile Executor closer = DIRECT_EXECUTOR;

    /**
     * Returns the time after which an idle pooled connection is closed, as configured by the
     * {@value #TTL_PROPERTY} system property. An invalid value is ignored in favor of the default.
     * <p>
     * Read on every borrow and every sweep rather than once, so that it can be changed on a running
     * server the way the bounds of a borrow can.
     */
    static long getCacheTtlMillis() {
        return getNonNegativeProperty(TTL_PROPERTY, DEFAULT_TTL_MS);
    }

    /** The pool of a connection string, created on first use. */
    static Pool poolOf(String connectionString) {
        final Pool pool = pools.computeIfAbsent(connectionString, Pool::new);
        startSweeper();
        return pool;
    }

    private static void startSweeper() {
        if (sweeper != null) {
            return;
        }
        synchronized (pools) {
            if (sweeper == null) {
                // A thread per close in flight, and none while nothing is being closed. One thread
                // shared by all of them would only move the head of the line, which is the point
                // of not closing on the sweeper in the first place.
                closer = Executors.newCachedThreadPool(runnable -> {
                    final Thread thread = new Thread(runnable, "JDBC backend connection pool closer");
                    thread.setDaemon(true);
                    return thread;
                });
                final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    final Thread thread = new Thread(runnable, "JDBC backend connection pool sweeper");
                    thread.setDaemon(true);
                    return thread;
                });
                final long interval = Math.max(MIN_SWEEP_INTERVAL_MS, getCacheTtlMillis() / 2);
                service.scheduleWithFixedDelay(CachedConnection::sweep, interval, interval, TimeUnit.MILLISECONDS);
                sweeper = service;
            }
        }
    }

    // Expiry has to happen without a borrow behind it. Caffeine was left without a scheduler, so an
    // entry was only ever expired by a later cache operation - and a backend that has gone idle,
    // the one case the TTL exists for, performs none (issue #878).
    static void sweep() {
        final long ttlMillis = getCacheTtlMillis();
        final Executor closeOn = closer;
        for (final Pool pool : pools.values()) {
            try {
                pool.sweep(ttlMillis, closeOn);
            } catch (Throwable t) {
                // Error included: scheduleWithFixedDelay cancels a task that throws, so anything
                // escaping here would stop the expiry of every pool in the JVM for good - and
                // silently, which is the failure mode the hand-off of the close exists to avoid.
                logger.traceException(t);
            }
        }
    }

    /**
     * Registers a storage as a user of the pool of a connection string. Reference counted because a
     * pool belongs to a database rather than to a backend: two backends may address one database,
     * and closing one of them must not take the connections of the other with it.
     */
    static void openPool(String connectionString) {
        poolOf(connectionString).addUser();
    }

    /** Unregisters a storage; the connections are released once the last user is gone. */
    static void closePool(String connectionString) {
        final Pool pool = pools.get(connectionString);
        if (pool != null) {
            pool.removeUser();
        }
    }

    /** Closes every idle connection of a connection string, leaving the pool usable. */
    static void invalidate(String connectionString) {
        final Pool pool = pools.get(connectionString);
        if (pool != null) {
            pool.drainIdle();
        }
    }

    /**
     * The connections of one connection string.
     * <p>
     * This replaces the cache entry that used to hold them. That one carried the TTL on the pool
     * rather than on a connection - {@code expireAfterAccess} keyed by the connection string, reset
     * by every borrow and every return - so under continuous traffic nothing ever expired and the
     * peak count of a burst stayed open for as long as the backend saw any traffic at all. It also
     * had no bound, so the only ceiling on the connections of a backend was the {@code
     * max_connections} of the database itself (issue #878).
     */
    static final class Pool {
        final String connectionString;
        /** Idle connections, most recently returned first: the ones a burst opened sink to the bottom, where the sweep finds them. */
        private final LinkedBlockingDeque<CachedConnection> idle = new LinkedBlockingDeque<>();
        /** One permit per live connection, borrowed or idle. Sized once: this is how large the pool may grow, not a rate. */
        private final Semaphore permits;
        private final int max;
        /**
         * How many connections of this pool the current thread holds. A borrow made while one is
         * already held may exceed the bound, because the two are held at the same time and waiting
         * for the first to be returned would wait for this very thread:
         * {@code PersistentCompressedSchema.store()} opens a write of its own - the definition has
         * to commit independently of the entry - and {@code EntryContainer.modifyDN} reaches it
         * from inside a transaction, having encoded the entry there. The exemption is from the
         * wait rather than from the pool: a nested borrow served out of the idle deque carries the
         * permit that connection already holds and is pooled again on return like any other. Only
         * one that had to establish a connection of its own, because the pool stood at its bound,
         * holds no permit - and that one is closed rather than pooled when it comes back, so the
         * pool does not grow past its bound.
         * <p>
         * Counted per pool rather than per thread, because that deadlock only exists within one
         * pool: a count shared by all of them would judge a thread holding a connection to one
         * database reentrant while it borrows from another, passing the bound of a pool it holds
         * nothing of and destroying the connection instead of pooling it, on every operation.
         */
        private final ThreadLocal<int[]> held = ThreadLocal.withInitial(() -> new int[1]);
        /** Open storages using this pool, guarded by this. */
        private int users;
        /**
         * Set when the last storage using this pool closed. A pool no storage ever registered with -
         * a borrow made straight through {@link CachedConnection#getConnection}, as the tests do -
         * is not closed and pools normally; only one that had a user and lost it stops keeping
         * connections for a borrower that is not going to come.
         */
        private volatile boolean closed;

        Pool(String connectionString) {
            this.connectionString = connectionString;
            final long configured = getNonNegativeProperty(POOL_MAX_PROPERTY, DEFAULT_POOL_MAX);
            this.max = (configured == 0 || configured > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) configured;
            this.permits = new Semaphore(max);
        }

        int max() {
            return max;
        }

        /** Whether the calling thread already holds a connection of this pool. */
        boolean heldByCurrentThread() {
            return held.get()[0] > 0;
        }

        void enter() {
            held.get()[0]++;
        }

        void leave() {
            final int[] depth = held.get();
            if (depth[0] > 0) {
                depth[0]--;
            }
        }

        int idleCount() {
            return idle.size();
        }

        /** The connections this pool holds, borrowed and idle together. */
        int liveCount() {
            return max - permits.availablePermits();
        }

        synchronized void addUser() {
            users++;
            closed = false;
        }

        void removeUser() {
            final boolean wasLast;
            synchronized (this) {
                wasLast = users > 0 && --users == 0;
                if (wasLast) {
                    closed = true;
                }
            }
            if (wasLast) {
                // Outside the monitor: closing a connection is a round trip, and an open of the
                // same database has no reason to wait behind it. The borrowed ones are not here to
                // be closed - give() closes them when they come back, since a pool nobody uses must
                // not keep them for a borrower that is not going to come.
                logger.trace(LocalizableMessage.raw("releasing %d pooled connections of %s: its last user closed",
                    idle.size(), safeUrl(connectionString)));
                drainIdle();
            }
        }

        void drainIdle() {
            for (CachedConnection con = idle.pollFirst(); con != null; con = idle.pollFirst()) {
                destroy(con);
            }
        }

        /**
         * Takes a connection out of the pool, waiting up to waitMs for one to be returned, and
         * discarding the ones that are broken or have been idle for longer than the TTL.
         * <p>
         * Bounded by the deadline of the borrow, and not only by waitMs: a poll of no duration
         * still hands out whatever the deque holds, and discarding a connection whose socket is
         * half-open costs the validation timeout apiece. The pool holds as many of those as its
         * bound allows, so draining the deque overran the bound the operator set - by minutes on a
         * large pool, before the connect that follows it had even started (issue #878).
         */
        CachedConnection pollIdle(long waitMs, long ttlMillis, long deadline) throws InterruptedException {
            long remainingWait = waitMs;
            while (true) {
                final long polledAt = System.currentTimeMillis();
                final CachedConnection con = idle.pollFirst(remainingWait, TimeUnit.MILLISECONDS);
                if (con == null) {
                    return null;
                }
                if (System.currentTimeMillis() - con.returnedAtMillis <= ttlMillis && isUsable(con, deadline)) {
                    return con;
                }
                destroy(con);
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                // one more look, since a connection may have been returned in the meantime
                remainingWait = Math.min(Math.max(0, remainingWait - (System.currentTimeMillis() - polledAt)), remaining);
            }
        }

        /** Takes the right to hold one more connection, or reports that the pool is full. */
        boolean tryReserve() {
            return permits.tryAcquire();
        }

        void cancelReservation() {
            permits.release();
        }

        /** Hands a connection back, closing it rather than pooling it when it may not be kept. */
        void give(CachedConnection con) {
            // An unmetered connection holds no permit, so pooling it would put the pool one over its
            // bound for good; and a closed pool has nobody left to hand it to.
            if (con.metered && !closed) {
                addIdle(con);
                if (closed) {
                    // The last user left while this one was on its way back, so it missed the drain.
                    drainIdle();
                }
            } else {
                destroy(con);
            }
        }

        /** Puts a connection into the pool. The caller must hold the right to keep it there. */
        void addIdle(CachedConnection con) {
            con.returnedAtMillis = System.currentTimeMillis();
            idle.addFirst(con);
        }

        void destroy(CachedConnection con) {
            try {
                closeQuietly(con.parent);
            } finally {
                // However the close went, the pool holds one connection fewer. A permit not given
                // back here is given back by nothing at all: only a live connection carries one,
                // and this one is gone (issue #878).
                con.releasePermit();
            }
        }

        void sweep(long ttlMillis) {
            sweep(ttlMillis, DIRECT_EXECUTOR);
        }

        /**
         * Closes the connections nothing has borrowed for the TTL, handing each to the executor
         * given rather than closing it here. The sweep of every pool shares one thread and
         * {@code scheduleWithFixedDelay} never overlaps its runs, so one close that does not
         * return would stop the expiry of every pool in the JVM - and silently, since only a
         * thrown exception is logged. Oracle logs off over the network, and the read bound of the
         * login has been lifted by then (issue #878).
         */
        void sweep(long ttlMillis, Executor closeOn) {
            final long deadline = System.currentTimeMillis() - ttlMillis;
            // From the tail: the least recently returned connection is the first to have expired,
            // and once one has not, neither has anything in front of it.
            for (CachedConnection con = idle.peekLast(); con != null; con = idle.peekLast()) {
                if (con.returnedAtMillis > deadline) {
                    return;
                }
                if (!idle.removeLastOccurrence(con)) {
                    // A borrow took it between the two. What is behind it may still have expired,
                    // and ending the cycle here would leave every one of those open until the
                    // next sweep.
                    continue;
                }
                final CachedConnection expired = con;
                try {
                    closeOn.execute(() -> destroy(expired));
                } catch (RuntimeException e) { // no thread to close it on: here rather than nowhere
                    destroy(expired);
                }
            }
        }
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

    /** A connection outside the accounting of its pool: it holds no permit and is never pooled. */
    public CachedConnection(String connectionString, Connection parent) {
        this(connectionString, parent, poolOf(connectionString), false);
    }

    CachedConnection(String connectionString, Connection parent, Pool pool, boolean metered) {
        this.connectionString = connectionString;
        this.parent = parent;
        this.pool = pool;
        this.metered = metered;
    }

    /** Gives back the right to hold this connection, once and only if it was taken. */
    void releasePermit() {
        if (metered && permitReleased.compareAndSet(false, true)) {
            pool.cancelReservation();
        }
    }

    /** Records that the borrowing thread holds this connection, so a borrow nested in it is recognized. */
    private static CachedConnection borrowed(CachedConnection con) {
        con.owner = Thread.currentThread();
        con.returned.set(false);
        con.pool.enter();
        return con;
    }

    /**
     * Borrows a connection: a usable one out of the pool, or a newly established one. Bounded in
     * both phases - every operation of this backend, the open of a backend and the import
     * included, comes through here, and an unbounded borrow turns a database that listens but does
     * not answer into a hang rather than into an error the caller can report.
     */
    static Connection getConnection(String connectionString) throws Exception {
        final Pool pool = poolOf(connectionString);
        final ConnectDialect dialect = ConnectDialect.of(connectionString);
        final long connectTimeoutSeconds = Math.min(
            getNonNegativeProperty(CONNECT_TIMEOUT_PROPERTY, DEFAULT_CONNECT_TIMEOUT_SECONDS), Integer.MAX_VALUE / 1000);
        final long poolTimeoutSeconds = getNonNegativeProperty(POOL_TIMEOUT_PROPERTY, DEFAULT_POOL_TIMEOUT_SECONDS);
        final long ttlMillis = getCacheTtlMillis();
        final long startedAt = System.currentTimeMillis();
        final long deadline = (poolTimeoutSeconds == 0 || poolTimeoutSeconds >= Long.MAX_VALUE / 1000)
            ? Long.MAX_VALUE : startedAt + poolTimeoutSeconds * 1000;
        // A thread already holding a connection is not made to wait for one: the two are held at
        // the same time, so waiting for the first to come back would wait for itself.
        final boolean reentrant = pool.heldByCurrentThread();
        long waitMs = 0;
        long backoffMs = 0;
        int attempts = 0;
        while (true) {
            final CachedConnection pooled = pool.pollIdle(waitMs, ttlMillis, deadline);
            if (pooled != null) {
                return borrowed(pooled);
            }
            if (!reentrant && !pool.tryReserve()) {
                // The pool holds as many connections as it may: only a returned one can serve this
                // borrow now, and the deadline decides how long that is worth waiting for. This is
                // the point of the bound - without it the borrow would open one more connection,
                // and the only ceiling left would be the max_connections of the database itself.
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    final String message = "no connection to " + safeUrl(connectionString)
                        + " could be borrowed within " + poolTimeoutSeconds + "s: all " + pool.max()
                        + " connections of the pool are in use (raise " + POOL_MAX_PROPERTY + " to allow more)";
                    // The one failure the bound introduces has to reach the server log too: an
                    // installation whose peak sits above the default would otherwise see its
                    // operations fail with nothing in the log naming the pool behind it.
                    warnPoolFull(message);
                    throw new SQLTimeoutException(message);
                }
                waitMs = Math.min(POOL_FULL_POLL_MS, remaining);
                continue;
            }
            attempts++;
            CachedConnection established = null;
            boolean handedOff = false;
            try {
                established = connect(connectionString, dialect, connectTimeoutSeconds, pool, !reentrant);
                final CachedConnection con = borrowed(established);
                handedOff = true;
                return con;
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
            } finally {
                // What the attempt took is given back on every way out of it, not only on the
                // SQLException a driver is supposed to throw. DriverManager catches SQLException
                // alone, so an unchecked failure of a driver reaches here - Connector/J hands a url
                // with a "%" in it to URLDecoder, and this backend keeps its credentials in the url
                // - and a permit left behind is left behind for good: only a live connection
                // carries one, and a failed attempt has none to give (issue #878).
                if (!handedOff) {
                    if (established != null) {
                        pool.destroy(established); // the permit went with it, and comes back with it
                    } else if (!reentrant) {
                        pool.cancelReservation();
                    }
                }
            }
        }
    }

    private static boolean isUsable(CachedConnection con, long deadline) {
        try {
            // The validation needs a bound of its own: isValid(0) means "no timeout" in the JDBC
            // contract, and a connection whose socket is half-open answers it no sooner than it
            // answers anything else. Never longer than what is left of the borrow either, since
            // that is the bound the caller was given - and never 0, which would lift it entirely.
            final long remainingSeconds = (deadline - System.currentTimeMillis() + 999) / 1000;
            return con.isValid((int) Math.max(1, Math.min(VALIDATION_TIMEOUT_SECONDS, remainingSeconds)));
        } catch (SQLException e) { // a driver reporting the validation as an error: discard it
            return false;
        }
    }

    private static CachedConnection connect(String connectionString, ConnectDialect dialect, long connectTimeoutSeconds,
            Pool pool, boolean metered) throws SQLException {
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
        } catch (Throwable t) { // nothing holds this connection yet: it would leak, whatever it is
            closeQuietly(conNew);
            throw t;
        }
        return new CachedConnection(connectionString, conNew, pool, metered);
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

    // The bound of the pool is a reason for an operation to fail that no version before it had,
    // so it belongs in the server log as well as in the error the client is given. Throttled like
    // the stall warning: every worker thread reaches it at once when the pool stands full.
    private static void warnPoolFull(String message) {
        final long now = System.currentTimeMillis();
        final long last = lastPoolFullWarning.get();
        if (now - last >= STALL_WARNING_INTERVAL_MS && lastPoolFullWarning.compareAndSet(last, now)) {
            logger.warn(LocalizableMessage.raw("%s", message));
        }
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
        } catch (SQLException | RuntimeException e) {
            // ignore: it is on its way out anyway, and the caller has a permit to give back
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
        // JDBC makes close() on a closed connection a no-op, and this one has to be one: a second
        // return would put the same connection into the pool twice, to be handed to two borrowers.
        if (!returned.compareAndSet(false, true)) {
            return;
        }
        if (owner == Thread.currentThread()) {
            pool.leave();
        }
        owner = null;
        try {
            rollback();
        } catch (SQLException e) {
            // A connection that cannot be rolled back must not be handed to the next borrower -
            // and must not be dropped on the floor either: nothing else holds it any more.
            pool.destroy(this);
            throw e;
        }
        // Straight to the pool it came from rather than through a lookup of its connection string:
        // the entry the lookup returned could be evicted between the two, leaving the connection in
        // a queue nothing referred to any more - never handed out, never closed (issue #878).
        pool.give(this);
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
