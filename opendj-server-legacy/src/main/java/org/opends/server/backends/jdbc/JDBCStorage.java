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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.backends.pluggable.spi.*;
import org.opends.server.core.ServerContext;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.BackupManager;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.opends.server.backends.pluggable.spi.StorageUtils.addErrorMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

public class JDBCStorage implements org.opends.server.backends.pluggable.spi.Storage, ConfigurationChangeListener<JDBCBackendCfg>{
	
	private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

	/** Number of attempts a {@link #write} makes before it propagates the conflict to the caller. */
	private static final int MAX_RETRIES = 10;

	/**
	 * Wall-clock budget the replays of a {@link #write} may spend, in nanoseconds. It is checked between attempts,
	 * so an attempt already running is never interrupted: the loop returns after at most this window plus one
	 * attempt. It bounds the conflicts that are slow to report, which {@link #MAX_RETRIES} alone does not - MySQL
	 * reports a lock wait timeout only after innodb_lock_wait_timeout, 50 s by default and not overridden here, so
	 * ten attempts would park a worker thread for eight minutes where a single one released it after 50 s. The
	 * deadlocks this retry exists for keep their full attempt budget, since every engine reports one in well under
	 * a second.
	 */
	private static final long MAX_RETRY_WINDOW_NANOS = 10L * 1000L * 1000L * 1000L; //10 s

	/** Upper bound of the random delay before the second attempt, in milliseconds; it doubles with every attempt. */
	private static final double BASE_SLEEP_ON_RETRY_MS = 50.0;

	/** Upper bound the doubled delay is capped at, in milliseconds. */
	private static final double MAX_SLEEP_ON_RETRY_MS = 1000.0;

	/**
	 * Number of links walked when classifying a failure, also a guard against a chain long enough to matter. One
	 * number for three chains at once - the causes, the next exceptions and the suppressed exceptions are walked
	 * together and counted together - so it is set well above the depth a wrapped failure of this backend reaches:
	 * mssql-jdbc chains every error of one message it received through {@code setNextException}, and a budget spent
	 * on those would never reach the cause the wrapper carries.
	 */
	private static final int MAX_CHAIN_LINKS = 64;

	/** The budget of {@link #failureScope}, which walks to the end of the chains: see the comment above it. */
	private static final int EVERY_LINK = Integer.MAX_VALUE;

	/** SQL Server error number of the transaction picked as the deadlock victim: "Rerun the transaction". */
	private static final int MSSQL_DEADLOCK_VICTIM = 1205;

	/** Oracle error number of a detected deadlock: ORA-00060, reported with SQLState 61000 rather than class 40. */
	private static final int ORACLE_DEADLOCK_DETECTED = 60;

	/**
	 * Class 40 states that are transaction rollbacks but must not be replayed. 40003 leaves the outcome of the
	 * transaction unknown, so replaying an add that in fact committed would answer the client with
	 * "entry already exists", and 40002 is an integrity constraint violation, which a replay repeats rather than
	 * resolves. Neither is reachable with the drivers shipped here - of class 40, Connector/J emits only 40000 and
	 * 40001, Oracle only ORA-02091/02092, and mssql-jdbc and PostgreSQL report their deadlock as 40001 and 40P01 -
	 * so they are excluded from the blanket class 40 match rather than that match being narrowed to a whitelist,
	 * which would fail a further engine reporting a conflict of its own.
	 */
	private static final Set<String> NON_REPLAYABLE_ROLLBACK_STATES =
			Collections.unmodifiableSet(new HashSet<>(Arrays.asList("40002", "40003")));

	/** SQLState class 08, connection exception: the connection is gone, whatever the statement asked for. */
	private static final String CONNECTION_FAILURE_CLASS = "08";

	/**
	 * The states outside class 08 that also say the connection is gone rather than the statement wrong. PostgreSQL
	 * announces the connection it is about to drop as 57P01 (admin_shutdown - a pg_terminate_backend of an idle
	 * connection reaper, or a shutdown of the server), 57P02 (crash_shutdown) or 57P03 (cannot_connect_now), and
	 * only the next use of that connection is reported as class 08. They are the states of the list HikariCP
	 * evicts a connection on that a driver of this backend reports: of the rest, JZ0C0 and JZ0C1 belong to a Sybase
	 * driver this backend is not used with, 01002 is a disconnect none of these four drivers reports, and 0A000 is
	 * the standard "feature not supported", which says nothing about the connection at all.
	 */
	private static final Set<String> CONNECTION_FAILURE_STATES =
			Collections.unmodifiableSet(new HashSet<>(Arrays.asList("57P01", "57P02", "57P03")));

	private JDBCBackendCfg config;

	public JDBCStorage(JDBCBackendCfg cfg, ServerContext serverContext) {
		this.config = cfg;
		cfg.addJDBCChangeListener(this);
	}

	//config
	@Override
	public boolean isConfigurationChangeAcceptable(JDBCBackendCfg configuration,List<LocalizableMessage> unacceptableReasons) {
		return true;
	}

	@Override
	public ConfigChangeResult applyConfigurationChange(JDBCBackendCfg cfg) {
		final ConfigChangeResult ccr = new ConfigChangeResult();
		try
		{
			this.config = cfg;
		}
		catch (Exception e)
		{
			addErrorMessage(ccr, LocalizableMessage.raw(stackTraceToSingleLineString(e)));
		}
		return ccr;
	}

	/**
	 * What a statement of this backend may legitimately take, and the property bounding it. One
	 * value cannot serve both: an entry read is a single row of an index, while the count of a
	 * tree and the delete that empties one before an import are a scan and a rewrite of a whole
	 * table, which take minutes on a populated backend and are not a symptom of anything.
	 */
	enum StatementBound {
		/** one row by primary key, or one batch of a cursor along its index */
		OPERATION("org.openidentityplatform.opendj.jdbc.query.timeout", 120),
		/**
		 * a whole table at once: count(*), the delete of clearTree, the scan behind the highest
		 * entry id, create index, drop table, and every batch of a cursor walking a tree whole.
		 * This class ships <em>unbounded</em>: what such a statement legitimately takes follows the
		 * size of the backend and the speed of its database, neither of which can be guessed here,
		 * so the deployment that knows both sets the property - until it does, a create index
		 * waiting for a metadata lock still waits for as long as the engine lets it, and so do the
		 * walks a backend makes while it opens (the load of the compressed schema, the read that
		 * checks id2entry is there) and the export behind the generation ID of a replicated domain.
		 * That is what this backend did before any of these bounds existed; bounding them as the
		 * work of a client operation, which is the only other value there was to give them, stopped
		 * a large backend from opening at all.
		 */
		BULK("org.openidentityplatform.opendj.jdbc.bulk.timeout", 0);

		final String property;
		final int defaultSeconds;

		StatementBound(String property, int defaultSeconds) {
			this.property = property;
			this.defaultSeconds = defaultSeconds;
		}

		/**
		 * The bound in seconds, as configured by {@link #property}: 0, or a negative value, leaves
		 * the statement unbounded, as it was before this bound existed, while a value that is not a
		 * number is ignored in favour of {@link #defaultSeconds} - {@code Integer.getInteger()}
		 * falls back to its default rather than reading such a value as a zero. A value above
		 * {@link JDBCStorage#MAX_BOUND_SECONDS} is taken down to it, for the reason recorded there.
		 */
		int seconds() {
			return clampSeconds(Integer.getInteger(property, defaultSeconds));
		}
	}

	/** What a caller of {@link #executeResultSet} makes of the rows, while the bound is still armed. */
	interface RowsHandler<T> {
		T handle(ResultSet rows) throws SQLException;
	}

	/**
	 * The value of a row that is there. A row whose {@code v} is null is one this backend never
	 * wrote - the column is nullable, however it is written - and it must not be answered with the
	 * {@code null} a single-row read uses, which is already taken and means "no such key": read that
	 * way, a key that exists is reported as absent. Named rather than left to the bare
	 * {@code NullPointerException} of {@code ByteString.wrap}, which names neither the fault nor the
	 * table it is in, and a {@code RuntimeException} rather than an {@code SQLException}, so that a
	 * corrupt row is never weighed against the bound of the statement that read it and reported as a
	 * timeout of a property that would have changed nothing. The key is left out of the message for
	 * the reason {@link #timedOut} leaves the statement out of its own: it is entry data.
	 */
	static ByteString valueOfRow(ResultSet rows, String tableName) throws SQLException {
		return ByteString.wrap(valueOfRow(rows.getBytes("v"), tableName));
	}

	/**
	 * The same check where a batch of a cursor reads the value beside its key, by position. Checked
	 * as the rows are taken off the statement rather than as they are handed out one by one: there
	 * the failure is inside the bound and inside the {@code catch} of the batch, while a batch
	 * buffered whole and unwrapped later fails from {@code advanceFromBuffer()} - outside both, and
	 * as the bare {@code NullPointerException} this exists to replace.
	 */
	static byte[] valueOfRow(byte[] value, String tableName) {
		if (value == null) {
			throw new StorageRuntimeException("jdbc: a row of "+tableName+" is present with no value");
		}
		return value;
	}

	<T> T executeResultSet(PreparedStatement statement, RowsHandler<T> rows) throws SQLException {
		return executeResultSet(statement, StatementBound.OPERATION, rows);
	}

	/**
	 * Runs a query under the bound of its class and hands the rows to {@code rows} while that bound
	 * is still armed. They are read there rather than after this method returns because a driver
	 * transfers them as they are asked for: read outside, the transfer - up to a whole batch of a
	 * cursor - would run with neither layer of the bound covering it, which is exactly where a
	 * database that stops answering mid-drain parks the worker thread. {@code setQueryTimeout}
	 * covering {@code ResultSet.next()} is optional in the JDBC contract ("drivers <em>may</em>
	 * also apply this limit"), and the two drivers of this backend that do not buffer a result
	 * whole - oracle prefetches ten rows at a time, mssql buffers adaptively - are the ones that
	 * do not.
	 */
	<T> T executeResultSet(PreparedStatement statement, StatementBound bound, RowsHandler<T> rows) throws SQLException {
		if (logger.isTraceEnabled()) {
			logger.trace(LocalizableMessage.raw("jdbc: %s",statement));
		}
		return bounded(statement, bound, () -> {
			try (final ResultSet rs=statement.executeQuery()) {
				return rows.handle(rs);
			}
		});
	}

	int execute(PreparedStatement statement) throws SQLException {
		return execute(statement, StatementBound.OPERATION);
	}

	int execute(PreparedStatement statement, StatementBound bound) throws SQLException {
		if (logger.isTraceEnabled()) {
			logger.trace(LocalizableMessage.raw("jdbc: %s",statement));
		}
		return bounded(statement, bound, statement::executeUpdate);
	}

	interface Execution<T> {
		T run() throws SQLException;
	}

	/**
	 * Runs a statement under the bound of its class. A statement of a class that carries one has to
	 * end: a row locked by an unrelated session, a table waiting for a metadata lock or a database
	 * that stops answering mid-query would otherwise park the worker thread that issued it for
	 * good. A class configured with no bound - which {@link StatementBound#BULK} ships as - takes
	 * neither of the two layers below and waits as this backend waited before they existed.
	 * <p>
	 * The bound is asked of the driver rather than of the session, because a pooled connection
	 * cannot carry a session setting - {@code CachedConnection.close()} only rolls back, so a
	 * {@code statement_timeout} of one operation would apply to whoever borrows the connection
	 * next - and it is applied in two layers, since the first one is not answered everywhere:
	 * {@code setQueryTimeout} cancels the statement and keeps the connection, while the socket read
	 * timeout behind it ends the wait even when the cancel is not acted upon. Oracle needs that
	 * second layer: a session blocked in a row-lock enqueue does not process the break its driver
	 * sends, so the timeout is armed and never arrives (the container suites cover it). That second
	 * layer belongs to the connection rather than to the statement, so it is arbitrated between the
	 * statements running on one - see {@link Backstop}.
	 */
	private <T> T bounded(PreparedStatement statement, StatementBound bound, Execution<T> execution) throws SQLException {
		final int seconds=bound.seconds();
		// whether the cancel is in force: a driver is free to refuse the query timeout, and then the
		// socket read timeout behind it is the only layer this statement has - one that arrives later
		final boolean cancelArmed=seconds > 0 && setQueryTimeout(statement, seconds);
		// an unbounded class is announced to the connection all the same: a statement told it may
		// take as long as it needs must not be cut by the socket read timeout of a concurrent one
		return bounded(connectionOf(statement), bound.property, seconds, cancelArmed, execution);
	}

	/**
	 * Runs the catalog lookups of {@code openTree()} under the bound of their class. They ask
	 * {@code DatabaseMetaData}, which takes no query timeout, so the socket read timeout behind the
	 * cancel is the only layer they can be given - and they do need one: they run once per tree on
	 * every open of a backend, and the catalog is answered by the same engine, behind the same
	 * locks, as the {@code create table} they guard.
	 * <p>
	 * That layer is only as good as what it actually arms, which is not always something: a driver
	 * with no network timeout, a connection that failed the call, one already carrying a tighter
	 * timeout of a deployment's own, and a statement of an unbounded class running beside this one
	 * each leave such a lookup with no bound at all. It is then reported as what it is - see
	 * {@link #timedOut} - rather than as a property that bounded nothing.
	 */
	<T> T bounded(Connection con, StatementBound bound, Execution<T> execution) throws SQLException {
		// no cancel to arm: DatabaseMetaData takes no query timeout, so the socket read timeout behind
		// it is the only layer these have, and nothing ends their wait before the margin of that layer
		return bounded(con, bound.property, bound.seconds(), false, execution);
	}

	/**
	 * Runs a statement under a bound of its own rather than under the bound of a class, for the one
	 * statement that has a property of its own: the statistics refresh after an import, which
	 * legitimately takes as long as a scan of the table it describes.
	 */
	private <T> T bounded(Connection con, String property, int seconds, boolean cancelArmed, Execution<T> execution)
			throws SQLException {
		final long startedAt=nanoTime();
		final Backstop backstop=holdBackstop(con, seconds);
		try {
			return execution.run();
		}catch (SQLException e) {
			// what the second layer carries is read here rather than at the top: it is arbitrated
			// between the statements in flight, so it is the value at the moment of the failure that
			// bounded this statement - and it is read before the release below takes it back off
			throw timedOut(e, property, seconds, cancelArmed, armedMillis(backstop), startedAt);
		}finally {
			releaseBackstop(backstop, con, seconds);
		}
	}

	/**
	 * What the socket read timeout of a connection carries for the statements on it right now, or 0
	 * where this layer is not in force for them at all. It is not enough that a bound was asked for:
	 * {@link #applyBackstop} arms nothing on a connection whose driver refused the call or has no
	 * network timeout to give, nothing on one already carrying a timeout of a deployment's own that
	 * is tighter than ours, and nothing while a statement of an unbounded class runs beside this one.
	 */
	private static int armedMillis(Backstop state) {
		if (state == null) {
			return 0; // no connection to arm it on: the cancel is the whole bound of such a statement
		}
		synchronized (state) {
			return state.armed;
		}
	}

	/**
	 * Asks the driver to cancel the statement at the bound. Not every driver has one: the JDBC
	 * contract allows {@code SQLFeatureNotSupportedException} and this backend takes whatever URL a
	 * deployment configures, so a driver without it degrades to the socket read timeout behind it
	 * rather than failing every statement it is given.
	 */
	private boolean setQueryTimeout(PreparedStatement statement, int seconds) {
		try {
			statement.setQueryTimeout(seconds);
			return true;
		}catch (SQLException | RuntimeException e) {
			if (queryTimeoutWarned.compareAndSet(false, true)) {
				logger.warn(LocalizableMessage.raw("jdbc: the driver would not take a query timeout (%s): a statement of this"
					+ " backend is left to the socket read timeout behind it", e.getMessage()));
			}
			return false;
		}
	}

	private Connection connectionOf(PreparedStatement statement) {
		try {
			return statement.getConnection();
		}catch (SQLException | RuntimeException e) {
			return null; // nothing to arm the backstop on; the cancel above is the whole bound
		}
	}

	/** How long the socket read timeout outlasts the cancel it backs up, giving it room to arrive. */
	static final int BACKSTOP_MARGIN_SECONDS = 30;

	/** How far under its bound a driver may report the cancel, its timer being kept in whole seconds. */
	static final long CLOCK_SLACK_MILLIS = 250;

	/**
	 * Ceiling of every bound this backend arms, in seconds - 24.9 days, which is what a socket read
	 * timeout can hold at all: {@code setNetworkTimeout} takes milliseconds of an {@code int}, and a
	 * bound past this one has no value of that layer to be given. It is <em>not</em> what keeps the
	 * arithmetic of {@link #backstopMillis} in range - the {@code long} multiply under the
	 * {@code Math.min} there does that on its own, up to the point where adding the margin overflows
	 * an {@code int} before the multiply ever runs - so a reader who later takes that {@code Math.min}
	 * away must not read this clamp as covering them.
	 * <p>
	 * Clamped rather than refused, and clamped rather than read as "no bound": a bound this large
	 * cancels nothing a database will not have ended first, so taking a nonsensical value down to it
	 * costs a deployment nothing, while reading it as an unbound would take a bound away from a
	 * deployment that asked for one. A property set to {@code Integer.MAX_VALUE} therefore bounds a
	 * statement at 24.9 days rather than leaving it unbounded; {@code 0} is what leaves it unbounded.
	 */
	static final int MAX_BOUND_SECONDS = Integer.MAX_VALUE/1000 - BACKSTOP_MARGIN_SECONDS;

	static int clampSeconds(int seconds) {
		return Math.max(0, Math.min(MAX_BOUND_SECONDS, seconds));
	}

	/**
	 * What {@link #timedOut} calls the second layer when that layer is the only one a statement ran
	 * under, so that a test can tell the two apart in a message: a run where the first layer stopped
	 * working degrades to this one by design, silently, and a suite that only measures how long a
	 * statement waited would go green with the cancel gone entirely.
	 */
	static final String BACKSTOP_ALONE = "the socket read timeout behind ";

	// The clock a bound is measured on, in one place so that a test can drive it: the classification
	// below turns on a few milliseconds either side of the bound, and a mock statement cannot be made
	// to take a real second without the suite taking one too. Monotonic, so that a step of the wall
	// clock can neither lengthen nor shorten what a statement is measured to have taken.
	long nanoTime() {
		return System.nanoTime();
	}

	// setNetworkTimeout() takes the executor its timeout handling runs on; the drivers of this
	// backend only set a socket option in it, so it costs a call rather than a thread.
	private static final Executor DIRECT_EXECUTOR = Runnable::run;

	// Set when the driver of this storage has no network timeout to give at all, which is a property
	// of the driver rather than of a connection: asking it again would cost a throw per statement,
	// and the entry a connection's Backstop lives in is gone as soon as nothing runs on it. Held per
	// storage rather than per JVM, like the warnings below: a driver that will not take one of these
	// says so once for every backend running on it, instead of one backend silencing it for all.
	private final AtomicBoolean backstopUnsupported = new AtomicBoolean();
	private final AtomicBoolean backstopUnsupportedWarned = new AtomicBoolean();
	private final AtomicBoolean backstopFailedWarned = new AtomicBoolean();
	private final AtomicBoolean queryTimeoutWarned = new AtomicBoolean();

	/**
	 * The socket read timeout of one connection, and the statements running on it. This second
	 * layer of the bound is a property of the socket rather than of a statement, so it cannot be
	 * armed and put back per statement wherever a connection carries more than one at a time: an
	 * {@code ImporterImpl} holds a single connection for the whole of an import and writes to it
	 * from every phase-one worker and every phase-two task, and there the first statement to finish
	 * would take the backstop away from every statement still in flight - while a statement whose
	 * class carries no bound at all would run under whatever value a concurrent one happened to
	 * arm, dying at it with nothing to say which property cut it, since such a statement never
	 * reaches {@link #timedOut}.
	 * <p>
	 * So the value armed is the loosest of the bounds of the statements in flight, and a statement
	 * with no bound of its own takes it off for as long as it runs: this backstop exists to end a
	 * wait nothing else would end, never to cut a statement that was told it may take as long as it
	 * needs. What the connection carried before is put back when the last of them is through.
	 */
	private static final class Backstop {
		/** Bounds of the statements in flight, in milliseconds and by count, the loosest last. */
		final TreeMap<Integer,Integer> bounds=new TreeMap<>();
		/** Statements in flight with no bound of their own, which no backstop may cut short. */
		int unbounded;
		/** Statements holding this entry, bounded or not: at zero it leaves {@link #backstops}. */
		int holders;
		/** What the connection carried before the backstop armed it, and is given back afterwards. */
		int previous;
		/** What the backstop has armed, or 0 when the connection carries {@link #previous}. */
		int armed;
		/**
		 * Set when the driver would not take a network timeout on this connection: it is not asked
		 * again while the statements holding this entry run. A connection is the right scope for
		 * that: the common cause is a connection on its way out, and a driver that has no network
		 * timeout at all is remembered for the whole storage instead - see {@link #backstopUnsupported}.
		 */
		boolean failed;
	}

	// Keyed by identity on the connection of the driver: CachedConnection.prepareStatement() hands
	// the statement to the connection it wraps, so that is the one a statement reports, while the
	// catalog lookups above hold the wrapper of that same connection - both have to find the same
	// entry, so a wrapper is unwrapped on the way in. Static because the pool these connections
	// come from is static; an entry lives only while statements are running on its connection.
	private static final Map<Connection,Backstop> backstops = new IdentityHashMap<>();

	private static Connection physical(Connection con) {
		return con instanceof CachedConnection ? ((CachedConnection)con).parent : con;
	}

	/**
	 * Puts the bound of a statement about to run on the connection that will run it, and makes the
	 * socket read timeout of that connection fit every statement in flight on it. Reaching this
	 * bound, unlike reaching the cancel it backs up, costs the connection: the driver closes it,
	 * which is the price of a wait the database was never going to end on its own.
	 */
	private Backstop holdBackstop(Connection con, int seconds) {
		final Connection physical=physical(con);
		if (physical == null) {
			return null;
		}
		final Backstop state;
		synchronized (backstops) {
			state=backstops.computeIfAbsent(physical, c -> new Backstop());
			state.holders++; // held from here, so that the entry outlives a concurrent release
		}
		synchronized (state) {
			if (seconds > 0) {
				state.bounds.merge(backstopMillis(seconds), 1, Integer::sum);
			}else {
				state.unbounded++;
			}
			applyBackstop(physical, state);
		}
		return state;
	}

	private void releaseBackstop(Backstop state, Connection con, int seconds) {
		if (state == null) {
			return;
		}
		final Connection physical=physical(con);
		try {
			synchronized (state) {
				if (seconds > 0) {
					final int millis=backstopMillis(seconds);
					final Integer inFlight=state.bounds.get(millis);
					if (inFlight == null || inFlight <= 1) {
						state.bounds.remove(millis);
					}else {
						state.bounds.put(millis, inFlight-1);
					}
				}else {
					state.unbounded--;
				}
				applyBackstop(physical, state);
			}
		}finally { // the entry is let go whatever the driver did, so that it cannot outlive its connection
			synchronized (backstops) {
				if (--state.holders <= 0) { // nothing is running on it: the connection is on its own again
					backstops.remove(physical);
				}
			}
		}
	}

	private static int backstopMillis(int seconds) {
		return (int) Math.min(Integer.MAX_VALUE, (seconds+BACKSTOP_MARGIN_SECONDS)*1000L);
	}

	/**
	 * Makes the socket read timeout of the connection what the statements in flight on it need: the
	 * loosest of their bounds, or nothing of ours at all while one of them carries no bound. Called
	 * with the monitor of {@code state} held, since it both reads those counts and acts on the
	 * driver.
	 */
	private void applyBackstop(Connection con, Backstop state) {
		if (state.failed || backstopUnsupported.get()) {
			// but a connection this backstop has already armed does not keep carrying it: the entry
			// remembering what it carried before is dropped when its last statement is through, and the
			// value would go back to the pool as the connection's own read timeout. Reachable through
			// the second guard, which is a latch of the whole storage: a connection armed before it was
			// set would otherwise never be disarmed. Where nothing was armed this costs no call.
			restorePrevious(con, state);
			return;
		}
		final int wanted=state.unbounded > 0 || state.bounds.isEmpty() ? 0 : state.bounds.lastKey();
		try {
			if (wanted == 0) {
				if (state.armed != 0) {
					con.setNetworkTimeout(DIRECT_EXECUTOR, state.previous);
					state.armed=0;
				}
				return;
			}
			if (state.armed == 0) {
				state.previous=con.getNetworkTimeout();
			}
			// only ever tighten: a connection that already carries a read timeout carries one a
			// deployment asked for, and this backstop exists to cap a cancel that is not acted
			// upon, not to relax anything. 0 is "no timeout" in the JDBC contract, so it is the
			// one value there is always something to gain by replacing.
			if (state.previous > 0 && state.previous <= wanted) {
				if (state.armed != 0) {
					con.setNetworkTimeout(DIRECT_EXECUTOR, state.previous);
					state.armed=0;
				}
				return;
			}
			if (state.armed != wanted) {
				con.setNetworkTimeout(DIRECT_EXECUTOR, wanted);
				state.armed=wanted;
			}
		}catch (SQLException | RuntimeException e) {
			state.failed=true; // whatever the cause, this connection is not asked again while it runs
			// and what it carried before goes back, while there is still an entry saying what that was:
			// this one is dropped as soon as the last statement on the connection is through, and a
			// backstop left armed would go back to the pool as the connection's own read timeout - which
			// is exactly how the next borrower reads it, tightening to it and never replacing it.
			restorePrevious(con, state);
			// The two causes are told apart, because they deserve opposite treatment and one of them
			// would otherwise spend the single warning the other needs: a driver with no network
			// timeout at all says so through SQLFeatureNotSupportedException, and there is nothing to
			// gain by asking it once per statement for the life of the storage, while a connection on
			// its way out - it may be the one that reached this very timeout - says nothing about the
			// driver and must not disable the backstop for the connections that are still healthy.
			if (e instanceof SQLFeatureNotSupportedException) {
				backstopUnsupported.set(true);
				if (backstopUnsupportedWarned.compareAndSet(false, true)) {
					logger.warn(LocalizableMessage.raw("jdbc: the driver takes no socket read timeout (%s): a statement the"
						+ " database does not cancel will wait for it indefinitely, unless the connect properties of the URL"
						+ " configured for this backend carry one", e.getMessage()));
				}
			}else if (backstopFailedWarned.compareAndSet(false, true)) {
				logger.warn(LocalizableMessage.raw("jdbc: the socket read timeout backing up a cancelled statement could not"
					+ " be set on a connection (%s): a statement the database does not cancel will wait for it"
					+ " indefinitely there", e.getMessage()));
			}
		}
	}

	/**
	 * Gives the connection back the read timeout it carried before this backstop armed one, and
	 * forgets having armed it. Best effort by construction: the caller reaches this from a driver
	 * call that has just failed, so the connection may well be gone - and where it is, it is the
	 * driver that closes it rather than this backend.
	 */
	private static void restorePrevious(Connection con, Backstop state) {
		if (state.armed == 0) {
			return; // the connection carries its own value already
		}
		try {
			con.setNetworkTimeout(DIRECT_EXECUTOR, state.previous);
		}catch (SQLException | RuntimeException ignored) {
			// nothing further can be done for this connection here, and the failure to report is the
			// one that brought us into the catch above
		}finally {
			state.armed=0;
		}
	}

	// Every driver reports a cancelled statement differently - postgresql as 57014, oracle as
	// ORA-01013, and neither of them as a SQLTimeoutException - so the bound is recognized by the
	// time the statement took rather than by the class or the state of its failure, and that time
	// is taken from the monotonic clock, which a step of the wall clock can neither lengthen nor
	// shorten. What it cannot tell apart is a failure of another kind arriving after the bound,
	// which is why the failure it replaces is chained rather than swallowed. The SQL state and the
	// error number are carried over as well, since a failure that arrives at the bound may still be
	// one a caller classifies: a mysql lock wait, reported in class 40, ends inside a longer bound
	// and stays the replayable conflict it is. The statement itself is left out of the message: a
	// driver renders it with its parameters bound, and those are entry data.
	private SQLException timedOut(SQLException e, String property, int seconds, boolean cancelArmed,
			int backstopArmedMillis, long startedAt) {
		if (seconds <= 0) {
			return e;
		}
		// Which layer was really in force, and until when. Where the cancel is armed, the property
		// ends the wait at its own value. Where it is not - a statement of DatabaseMetaData takes no
		// query timeout, and a driver is free to refuse one - the socket read timeout behind it is the
		// only layer there is, and that one arrives a margin later: measuring such a statement against
		// the property alone reported a connection reset at 121 s as a query timeout of 120 s and sent
		// the operator to a property that bounded nothing. Asking for that layer is not having it,
		// which is why the value armed is passed in rather than derived from the property here: a
		// driver with no network timeout, a connection that failed the call, one already carrying a
		// tighter timeout of its own, and a statement of an unbounded class running beside this one
		// each leave it unarmed. A statement neither layer bounded reached no bound of ours at all, so
		// its failure is the driver's own and is left exactly as it is: naming a property that armed
		// nothing sends an operator to raise a value that changes nothing about the wait they saw.
		final long endsAfterMillis=cancelArmed ? seconds*1000L : backstopArmedMillis;
		if (endsAfterMillis <= 0) {
			return e;
		}
		final long elapsedMillis=(nanoTime()-startedAt)/1000000L;
		// The bound is allowed a little slack under it: a driver keeps its timer in whole seconds and
		// reports the cancel a few milliseconds before the bound is arithmetically due, and measured
		// to the millisecond such a statement would arrive as a bare 57014 or ORA-01013, naming
		// neither the property that cancelled it nor the fact that it was cancelled at all.
		if (elapsedMillis < endsAfterMillis-CLOCK_SLACK_MILLIS) {
			return e;
		}
		// The time is reported as measured rather than as the bound. Where the database does not act
		// on the cancel - a session blocked in a row-lock enqueue on oracle - the wait ends at the
		// socket read timeout, a margin past the property that armed it, and "did not finish within
		// the 120s" of a statement that waited 150 s is a message an operator cannot put next to a
		// clock. The property named still governs both layers, since backstopMillis() derives the
		// second one from it, so raising it stays the remedy either way.
		return new SQLTimeoutException("jdbc: the statement took "+elapsedMillis+" ms, reaching the "
			+(endsAfterMillis/1000L)+"s of "
			+(cancelArmed ? property : BACKSTOP_ALONE+property+" (the only layer bounding a statement that takes no"
				+" query timeout; it is armed at the loosest bound of the statements sharing this connection, this"
				+" one's being "+seconds+"s plus the margin of that layer)")
			+": raise that property, or set it to 0 for no bound", e.getSQLState(), e.getErrorCode(), e);
	}

	// Unlike execute(), tolerates a statement that returns a result set - the comment statement of
	// mssql is a batch that ends in an exec - and, unlike it, carries no bound of its own: what is
	// left of this method runs on a stamp connection, which is given a lock timeout of its own
	// (Dialect.lockTimeoutSql) and a socket read timeout in its connect properties.
	void executeAny(PreparedStatement statement) throws SQLException {
		if (logger.isTraceEnabled()) {
			logger.trace(LocalizableMessage.raw("jdbc: %s",statement));
		}
		statement.execute();
	}

	Connection getConnection() throws Exception {
		return getConnection(true);
	}

	/**
	 * Borrows a connection the pool validates whatever the alive window of
	 * {@link CachedConnection#ALIVE_BYPASS_PROPERTY} says, for the borrows this class compensates a dropped
	 * connection on in no other way: {@link #open(AccessMode)}, {@link #removeStorageFiles()} and the importer
	 * issue their statements far from the borrow, and the open issues none at all, so a connection dropped inside
	 * the window would surface out of the rollback that releases it. One round trip on a path taken once per open,
	 * per import or per removal buys back exactly what master did on every borrow.
	 */
	Connection getValidatedConnection() throws Exception {
		return getConnection(false);
	}

	// The one borrow of this storage: both methods above go through it, so that whatever stands in
	// for the pool stands in for every path that takes a connection. A stand-in of the trusted
	// borrow alone let the open, the import and the removal - the three that ask for a validated
	// one - reach a real database instead.
	Connection getConnection(boolean trusted) throws Exception {
		return CachedConnection.getConnection(config.getDBDirectory(), trusted);
	}


	AccessMode accessMode=AccessMode.READ_ONLY;
	@Override
	public void open(AccessMode accessMode) throws Exception {
		try (final Connection con=getValidatedConnection()) {
			this.accessMode = accessMode;
			storageStatus = StorageStatus.working();
		}
	}

	private StorageStatus storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
	@Override
	public StorageStatus getStorageStatus() {
		return storageStatus;
	}
	
	@Override
	public void close() {
		storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
		// a stamp that the database rejected is remembered for as long as the storage is open, so
		// that it is not reissued for every tree on every open; disabling and re-enabling the
		// backend is the way to try again once the privilege has been granted
		unstampableTrees.clear();
		// what this storage knows of its catalog holds no longer than the open it learnt it in: the
		// table may well be gone by the next one, dropped by an offline tool run in the meantime
		catalogTableOpened=false;
		enrolledTrees.clear();
	}

	// The trees this storage has taken an interest in, and the tables they map to: a memo, so that
	// naming the table of a tree costs a map lookup rather than a digest. What a backend owns is
	// recorded in its catalog and not here (#888) - listTrees() and removeStorageFiles() read that
	// - but the distinction the two names below draw is kept all the same: a tree merely asked
	// about is not one this storage has taken an interest in, and it stays out of the memo.
	final LoadingCache<TreeName,String> tree2table = Caffeine.newBuilder()
		.build(JDBCStorage::toTableName);

	/**
	 * The table a tree name maps to. A pure function of the name, so that a tree can be read without
	 * being entered into tree2table: the compressed schema reads the tree its definitions used to be
	 * shared under (#873), which is a tree this backend does not own.
	 * <p>
	 * Which of the two names a statement takes therefore says whether this backend is claiming the
	 * tree it names: a path that creates or writes one - openTree(), clearTree(), deleteTree(), put(),
	 * update(), delete() - takes the enrolling {@link #getTableName(TreeName)}, and a path that only
	 * asks - read(), getRecordCount(), isExistsTable(), the cursor, and the read of what the catalog
	 * records - takes {@link #readTableName(TreeName)}, which computes this only for a tree that is not
	 * enrolled already. What a clear may drop is decided by the catalog of the backend (#888) and no
	 * longer by this memo, so an entry of it puts no table up for removal; the two names are what keeps
	 * the memo an account of the trees this backend claims all the same.
	 */
	static String toTableName(TreeName treeName) {
		try {
			final MessageDigest md = MessageDigest.getInstance("SHA-224");
			final byte[] messageDigest = md.digest(treeName.toString().getBytes());
			final StringBuilder hashtext = new StringBuilder(56);
			for (byte b : messageDigest) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) hashtext.append('0');
				hashtext.append(hex);
			}
			return "opendj_" + hashtext;
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	String getTableName(TreeName treeName) {
		return tree2table.get(treeName);
	}

	/**
	 * The pseudo base DN of the tree naming the trees of a backend. Every real tree of a backend is
	 * named after an entry container, whose prefix is a normalized DN and so always holds a "=",
	 * which an identifier of this form cannot collide with.
	 */
	static final String CATALOG_BASE_DN="opendj_catalog";

	/**
	 * The base DN the compressed schema trees were named under before #881 gave each backend a pair
	 * of its own. It carries no backend qualifier, so on a database addressed by several backends -
	 * which nothing forbids (#873) - that pair of trees is the same pair for all of them, and a
	 * backend must not put a tree another one may be the owner of up for removal. It is the pair
	 * {@code PersistentCompressedSchema} migrates from and never writes to again, and it is left
	 * exactly where it lies: the definitions of a backend that has not been started since the
	 * upgrade are still in it. The pair each backend owns is named after its backend id, is under no
	 * such literal, and is enrolled like any other tree.
	 */
	static final String SHARED_COMPRESSED_SCHEMA_BASE_DN="compressed_schema";

	/**
	 * The pair named under {@link #SHARED_COMPRESSED_SCHEMA_BASE_DN}, spelled out here because the
	 * names are private to {@code PersistentCompressedSchema} - where they are the LEGACY_ pair of
	 * #881. They are never enrolled, so nothing but this constant can name them - and a tool asking
	 * a backend what trees it holds has to be told about them all the same, which is what {@link
	 * #listTrees()} uses this for.
	 */
	static final List<TreeName> SHARED_COMPRESSED_SCHEMA_TREES=Collections.unmodifiableList(Arrays.asList(
		new TreeName(SHARED_COMPRESSED_SCHEMA_BASE_DN, "compressed_attributes"),
		new TreeName(SHARED_COMPRESSED_SCHEMA_BASE_DN, "compressed_object_classes")));

	/**
	 * The tree naming the trees this backend owns: one row per tree, the tree name as its key and the
	 * table holding that tree as its value.
	 * <p>
	 * A table is named after the hash of its tree name, so the catalog of a database can neither be
	 * filtered by a per-backend prefix nor read back into a {@link TreeName}. Without a record of its
	 * own a backend can therefore only name the trees this very process has already touched - which
	 * is precisely what {@link #removeStorageFiles()} cannot have, running as it does before the root
	 * container is open. In the offline {@code import-ldif} nothing has touched a tree at all, so
	 * {@code --clearBackend} used to clear nothing whatsoever (#888).
	 * <p>
	 * The catalog is per backend and named after the backend id alone: a process that has opened
	 * nothing can still find its table, and backends sharing one database URL - which nothing
	 * forbids (#873) - never name each other's trees. The id goes in escaped, for the reason {@link
	 * #escapedBackendId} states: a name that does not survive being read back is a table of this
	 * backend that its own clear cannot recognize.
	 */
	TreeName getCatalogTree() {
		return new TreeName(CATALOG_BASE_DN, escapedBackendId());
	}

	/**
	 * Whether the table of the catalog was created, or found, by this storage. A tree is enrolled on
	 * every open - about 25 of them for a stock suffix - and asking the catalog whether the table is
	 * there would cost a metadata round trip per tree.
	 */
	private volatile boolean catalogTableOpened=false;

	/**
	 * Serializes the one step above: two transactions opening trees at the same time would otherwise
	 * both find the table of the catalog absent and both create it, the second failing the open it
	 * belongs to. Held across the lookup and the statement that answer it, and across nothing else.
	 */
	private final Object catalogLock=new Object();

	/**
	 * The trees the catalog already records at the table this version would record them at, read
	 * from it when this storage first opens it and added to as it enrols. A tree named here needs no
	 * row written for it: the row would be the one that is already there, and writing one is a
	 * statement and a commit on a connection this backend then has to have opened - a stock suffix
	 * has about 25 trees, and every open after the first enrols none of them.
	 * <p>
	 * A row recording another table than {@link #getTableName} would give is not in here: what a
	 * removal drops is the table the row records, so a row of a version naming its tables otherwise
	 * has to be rewritten rather than trusted. Held no longer than the open it was read in, like
	 * {@link #catalogTableOpened}, and given up whenever the catalog itself is.
	 */
	private final Set<TreeName> enrolledTrees=ConcurrentHashMap.newKeySet();

	/**
	 * The table a tree name maps to, for a statement that only reads it. Answered from the memo of
	 * {@link #getTableName(TreeName)} where the tree is in it, and computed without being put there
	 * otherwise.
	 * <p>
	 * Every tree this backend owns is enrolled as it is opened, so the per-entry read path stays a
	 * map lookup: {@link #toTableName(TreeName)} takes a JCA provider lookup and a digest per call,
	 * which read() would otherwise pay for every entry of every search. Only a tree this backend
	 * does not own - the shared compressed schema tree the migration of #873 reads - is computed,
	 * twice per open of the backend.
	 */
	String readTableName(TreeName treeName) {
		final String enrolled=tree2table.getIfPresent(treeName);
		return enrolled!=null ? enrolled : toTableName(treeName);
	}

	/**
	 * The form a catalog pattern has to take to match an identifier this backend created unquoted.
	 * An unquoted identifier is folded when it is stored - to upper case on oracle, to lower case
	 * on postgresql - and a metadata pattern is matched against the stored form, not against the
	 * name as it was written. The driver is asked which way it folds, rather than its class name
	 * being matched, since this is what the JDBC contract exposes these two methods for.
	 */
	static String storedIdentifier(DatabaseMetaData metaData, String name) throws SQLException {
		if (metaData.storesUpperCaseIdentifiers()) {
			return name.toUpperCase();
		}
		if (metaData.storesLowerCaseIdentifiers()) {
			return name.toLowerCase();
		}
		return name;
	}

	private static final String[] NO_ARGS=new String[0];

	// Comment statements take a lock (a metadata lock on mysql, a schema modification lock on sql
	// server, a ddl lock on oracle), and mysql and sql server wait for it without limit by
	// default (lock_wait_timeout is a year, lock_timeout is infinite): a stamp could queue behind
	// an unrelated transaction of another session on the same database and - on mysql - park
	// every other query on the table behind itself. The stamp is a diagnostic aid, so every
	// dialect is told to give up after this many seconds instead of waiting.
	private static final int COMMENT_LOCK_TIMEOUT_SECONDS=5;

	// The comment statement runs on a connection of its own (newStampConnection() below), and a
	// driver waits for a connect attempt without limit unless it is told otherwise: a database
	// that keeps its established connections alive but accepts no new ones (a moved vip, a proxy
	// at its connection limit) would otherwise hang the open of a tree - dsconfig
	// create-backend-index opens one on a running server - instead of leaving a table unstamped.
	// Every dialect gets the same bound, in the unit its own driver property takes.
	private static final int STAMP_CONNECT_TIMEOUT_SECONDS=10;

	// Not one of the four drivers bounds the whole login attempt with its connect property alone:
	// postgres and mysql apply theirs to socket.connect(), oracle's own reference says
	// CONNECT_TIMEOUT "doesn't include user authentication", and the sql server driver leaves the
	// read of the prelogin answer unbounded - TDSChannel.open() gives the socket
	// min(what is left of loginTimeout, socketTimeout) and socketTimeout defaults to 0, which is
	// "wait forever". Those reads - of the prelogin handshake, of tls, of authentication - are
	// exactly where a proxy that accepts a connection and then goes quiet leaves the driver, so
	// every dialect carries a read bound as well. All four are socket read timeouts, so the bound
	// outlives the login phase and covers the comment statement too, which is why it is kept well
	// clear of the lock bound above: the statement gives up on a contended lock long before the
	// socket gives up on the server. On a mysql connection with tls (the sslMode=PREFERRED default
	// of connector/j) the wall clock of a dead peer is twice this, since closing an SSLSocket
	// drains input waiting for close_notify and pays the read bound a second time.
	private static final int STAMP_READ_TIMEOUT_SECONDS=30;

	// Trees whose stamp failed for a reason that is not going to change by itself: an account that
	// may not comment its tables (no ALTER privilege, for instance) would otherwise reissue the
	// statement for every tree on every open. A failure that says nothing about the table - a lock
	// timeout, a connection that broke - is not remembered (failureScope() below), so a contended
	// moment does not leave the backend unstamped until it is restarted. Forgotten when the
	// storage is closed, so re-enabling the backend is enough to try again once the privilege has
	// been granted, without a restart of the server.
	private final Set<TreeName> unstampableTrees=ConcurrentHashMap.newKeySet();

	/**
	 * The engines whose comment statement, comment readback and statistics refresh this backend
	 * knows, with the session settings a comment statement needs: the driver properties that
	 * bound the connect attempt of the connection it runs on, and the statement that bounds its
	 * wait for the table lock.
	 */
	enum Dialect {
		/** postgresql: lock_timeout takes milliseconds; connectTimeout bounds socket.connect(), loginTimeout the whole login the driver runs on a thread of its own, socketTimeout every read after it - all three in seconds. */
		POSTGRES("set lock_timeout = "+(COMMENT_LOCK_TIMEOUT_SECONDS*1000),
			"connectTimeout", STAMP_CONNECT_TIMEOUT_SECONDS, "loginTimeout", STAMP_CONNECT_TIMEOUT_SECONDS,
			"socketTimeout", STAMP_READ_TIMEOUT_SECONDS),
		/** mysql: lock_wait_timeout takes seconds; connectTimeout bounds the socket connect and socketTimeout every read after it, both in milliseconds. */
		MYSQL("set session lock_wait_timeout="+COMMENT_LOCK_TIMEOUT_SECONDS,
			"connectTimeout", STAMP_CONNECT_TIMEOUT_SECONDS*1000, "socketTimeout", STAMP_READ_TIMEOUT_SECONDS*1000),
		/** oracle: ddl_lock_timeout takes seconds and defaults to 0 (give up at once), but it can be raised globally; the connect and read bounds take milliseconds. */
		ORACLE("alter session set ddl_lock_timeout="+COMMENT_LOCK_TIMEOUT_SECONDS,
			"oracle.net.CONNECT_TIMEOUT", STAMP_CONNECT_TIMEOUT_SECONDS*1000, "oracle.jdbc.ReadTimeout", STAMP_READ_TIMEOUT_SECONDS*1000),
		/** ms sql server: lock_timeout takes milliseconds; loginTimeout bounds the socket connect, in seconds, and socketTimeout the prelogin read it leaves open - and every read after it - in milliseconds. */
		MICROSOFT("set lock_timeout "+(COMMENT_LOCK_TIMEOUT_SECONDS*1000),
			"loginTimeout", STAMP_CONNECT_TIMEOUT_SECONDS, "socketTimeout", STAMP_READ_TIMEOUT_SECONDS*1000);

		final String lockTimeoutSql;
		// The driver properties bounding the login attempt of a stamp connection: the one bounding
		// the socket connect and the one bounding the reads behind it, each in the unit its own
		// driver takes. newStampConnection() hands the driver a copy of them: a driver is free to
		// write into the map it is passed, and the sql server one gives a supplied property
		// precedence over the same property of the url.
		final Properties connectProperties=new Properties();

		/** For the three drivers whose connect property and read property bound the login between them. */
		Dialect(String lockTimeoutSql, String connectProperty, int connectValue, String readProperty, int readValue) {
			this.lockTimeoutSql=lockTimeoutSql;
			connectProperties.setProperty(connectProperty, String.valueOf(connectValue));
			connectProperties.setProperty(readProperty, String.valueOf(readValue));
		}

		/** For the one driver bounding the login itself, on top of the socket connect and the reads behind it. */
		Dialect(String lockTimeoutSql, String connectProperty, int connectValue, String loginProperty, int loginValue,
				String readProperty, int readValue) {
			this(lockTimeoutSql, connectProperty, connectValue, readProperty, readValue);
			connectProperties.setProperty(loginProperty, String.valueOf(loginValue));
		}
	}

	/** Returns the class name of the driver behind the given connection, which names the engine it talks to. */
	static String driverNameOf(Connection con) {
		// a stamp connection comes straight from the driver, a transaction one from the pool
		return ((con instanceof CachedConnection) ? ((CachedConnection) con).parent : con).getClass().getName();
	}

	// The dialect behind a pooled connection, or null for an engine none of the statements of this
	// class fit: it is left unstamped and its statistics untouched rather than fed untested SQL.
	static Dialect dialectOf(Connection con) {
		final String driverName=driverNameOf(con);
		if (driverName.contains("postgres")) {
			return Dialect.POSTGRES;
		}else if (driverName.contains("mysql")) {
			return Dialect.MYSQL;
		}else if (driverName.contains("oracle")) {
			return Dialect.ORACLE;
		}else if (driverName.contains("microsoft")) {
			return Dialect.MICROSOFT;
		}
		return null;
	}

	/** Outcome of a comment stamp: openTree() ignores it, tests tell the cases apart. */
	enum CommentResult {
		/** the table now carries its tree name */
		STAMPED,
		/** the stored comment already matched: no statement was issued */
		UP_TO_DATE,
		/** neither comment syntax nor readback is known for this engine */
		UNSUPPORTED,
		/** the comment could not be read back or stored */
		FAILED
	}

	// Splices a value into a single-quoted SQL literal for the comment DDL, which takes no bind
	// parameters: doubles every quote, and every backslash on dialects where backslash is an
	// escape character inside literals. The scan of the escaped result is defence in depth: it
	// re-verifies that no quote (or live backslash) is left unpaired and able to terminate the
	// literal, so a regression in the escaping throws instead of reaching the database.
	private static String sqlLiteral(String value, boolean backslashIsEscape) {
		final String escaped=(backslashIsEscape?value.replace("\\","\\\\"):value).replace("'","''");
		for (int i=0;i<escaped.length();i++) {
			final char c=escaped.charAt(i);
			if (c=='\'' || (backslashIsEscape && c=='\\')) {
				if (i+1>=escaped.length() || escaped.charAt(i+1)!=c) {
					throw new IllegalArgumentException("unpaired "+c+" in SQL literal: "+escaped);
				}
				i++;
			}
		}
		return "'"+escaped+"'";
	}

	// Whether backslash is an escape character inside string literals on this mysql connection:
	// under the NO_BACKSLASH_ESCAPES sql mode it is an ordinary character, and doubling it there
	// would store a comment that never matches its tree name - re-stamping the table forever.
	// Asked of the very session that parses the literal: sql_mode is a session setting, and a
	// session opened at another moment can have been given another value of it.
	boolean isMysqlBackslashEscape(Connection con) throws SQLException {
		try (final PreparedStatement statement=con.prepareStatement("select @@sql_mode")) {
			final String sqlMode=executeResultSet(statement, rs -> rs.next() ? rs.getString(1) : null);
			return sqlMode==null || !sqlMode.toUpperCase().contains("NO_BACKSLASH_ESCAPES");
		}
	}

	// A connection of its own for the comment statements, outside the pool: they need session
	// settings (the lock timeout above) that a pooled connection would carry over to whoever
	// borrows it next, since CachedConnection.close() only rolls back.
	Connection newStampConnection(Dialect dialect) throws SQLException {
		final Properties properties=new Properties();
		properties.putAll(dialect.connectProperties);
		final Connection con=DriverManager.getConnection(config.getDBDirectory(), properties);
		try {
			con.setAutoCommit(false);
			executeSessionStatement(con, dialect.lockTimeoutSql); // give up instead of waiting for another session
			// postgres undoes a plain SET when the transaction that ran it is rolled back, and a
			// failed stamp is rolled back with the connection kept (StampSession.reset() below):
			// commit the setting, or the first failure of a sweep would leave every tree after it
			// stamped without the very bound this connection exists to carry. The session settings
			// of the other three dialects are not transactional - the commit costs them an empty
			// transaction.
			con.commit();
		}catch (SQLException e) { // nothing else holds this connection yet: it would leak
			try {
				con.close();
			}catch (SQLException e2) {}
			throw e;
		}
		return con;
	}

	/**
	 * A connection of its own for the catalog of a backend, outside the pool for the reason a stamp
	 * connection is: the caller of openTree() is inside a transaction and holding a pooled connection
	 * already, and a pool that cannot open a second one waits for a peer to return one - which here is
	 * the very thread that is waiting.
	 * <p>
	 * It is established the way a pooled connection is and not the way a stamp connection is: the
	 * bounds of {@link CachedConnection.ConnectDialect} rather than of {@link Dialect}, so that a
	 * login which never answers is bounded, a bound the administrator set in the connection string is
	 * left exactly as they set it, and the read bound of the login is lifted as soon as the login is
	 * through (#872). A stamp is a diagnostic aid and gives up rather than queue behind another
	 * session; a catalog row is the state a clear reads, and it waits for its lock rather than dying
	 * on a read bound. The isolation is the pool's for the same reason: this connection issues the
	 * ordinary DML of this class, and the repeatable read a mysql server defaults to gap-locks a
	 * catalog two transactions enrol into.
	 * <p>
	 * The bound of the connect is the one the pool bounds its own connects by, read from {@link
	 * CachedConnection#CONNECT_TIMEOUT_PROPERTY} where an operator set it: a login of this database
	 * takes what it takes whoever is asking, so a deployment which had to raise that property because
	 * its login is slower than the default must not meet a second, tighter bound here - the connect
	 * would fail in {@code 08001}, which is no conflict {@link #write} replays, and the backend would
	 * stop opening on an installation that opened before this connection existed. A property of 0 is
	 * the operator asking for no bound of the connect, and it is honoured here as it is by the pool.
	 * <p>
	 * With one difference at that value, and it is worth stating rather than glossing as parity: what
	 * still bounds a pooled attempt of a property of 0 is the deadline of the borrow ({@link
	 * CachedConnection#POOL_TIMEOUT_PROPERTY}, 60 s by default), and this connect has no borrow to
	 * take a deadline from - it waits for no peer to return a connection, so there is none to expire.
	 * A deployment that turns the connect bound off and meets a database which accepts the socket and
	 * never finishes the login therefore parks here, inside the lock {@code openCatalog()} holds, and
	 * with it every other transaction of this storage that goes on to open a tree. Both properties at
	 * 0 park a borrow of the pool in exactly the same way; what differs is that this one is reached
	 * with the first of them alone.
	 */
	Connection newCatalogConnection() throws SQLException {
		final String connectionString=config.getDBDirectory();
		final Properties properties=new Properties();
		final CachedConnection.ConnectDialect dialect=CachedConnection.ConnectDialect.of(connectionString);
		final long timeoutSeconds=CachedConnection.getConnectTimeoutSeconds();
		final boolean readBoundSet=dialect!=null && timeoutSeconds>0
			&& dialect.bound(connectionString, properties, timeoutSeconds);
		final Connection con;
		try {
			con=DriverManager.getConnection(connectionString, properties);
		}catch (SQLException e) {
			// redacted the way the pool redacts the failure of its own connects: a driver renders the
			// connection string it could not use into its message as readily as not, and the connection
			// string of this backend carries the password of the account it works as. This failure is
			// reported in full - ERR_OPEN_ENV_FAIL, or the log of a clear - so it must not carry one
			throw CachedConnection.reported(e, connectionString);
		}catch (RuntimeException e) {
			// a driver reporting a connect it will not make as an unchecked failure names the connection
			// string just as readily; reportedUnchecked() answers with the original where it holds no
			// credential, so nothing of a plain programming error is hidden by this
			final Exception reported=CachedConnection.reportedUnchecked(e, connectionString);
			if (reported instanceof SQLException) { // redacted, and reported as the connect failure it is
				throw (SQLException) reported;
			}
			throw (RuntimeException) reported; // the original: it holds no credential of this backend
		}
		try {
			con.setAutoCommit(false);
			con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
		}catch (SQLException | RuntimeException e) { // nothing else holds this connection yet: it would leak
			try {
				con.close();
			}catch (SQLException e2) {}
			throw e;
		}
		if (readBoundSet) {
			try {
				// only where this code set one: a read bound of the connection string is the
				// administrator's and is not lifted along with it, exactly as the pool leaves it
				con.setNetworkTimeout(Runnable::run, 0);
			}catch (SQLException | RuntimeException e) {
				// a driver that will not take the bound back leaves it in force: the pool stops pooling
				// such a connection, and this one - which is nobody's but this transaction's - says so
				logger.warn(LocalizableMessage.raw("jdbc: the catalog connection keeps the read bound its login was given, so a statement of the catalog slower than it fails on it: %s",
					stackTraceToSingleLineString(e)));
			}
		}
		return con;
	}

	/**
	 * The connection the catalog table of a backend is read and written on, and the transaction over
	 * it. No other connection touches that table: one physical connect per read-write open of the
	 * storage - the open reads what the catalog records once - and none at all for a read-only one or
	 * for a transaction that opens no tree.
	 * <p>
	 * Why the rows are not written on the caller's connection is in {@link
	 * WriteableTransactionTransactionImpl#enrolInCatalog}: they have to be committed, and that commit
	 * must not be the caller's. Why the read is not either is in {@link
	 * WriteableTransactionTransactionImpl#readEnrolledTrees}: a select of the caller's transaction
	 * would hold a lock on the catalog table for the whole life of that transaction, and the rows it
	 * decides are written from here.
	 */
	final class CatalogSession implements Closeable {
		private Connection con;
		private WriteableTransactionTransactionImpl txn;

		/** The connection, opened at the first read or write the catalog needs and shared by the rest. */
		Connection connection() throws SQLException {
			if (con==null) {
				con=newCatalogConnection();
			}
			return con;
		}

		/**
		 * A transaction over that connection, for its row statements alone: an upsert and a delete are
		 * per engine, and writing the catalog through the very ones every other tree is written through
		 * is what keeps its rows the same shape as theirs. It opens no tree and stamps no table, so the
		 * sessions it carries of its own are never opened.
		 */
		WriteableTransactionTransactionImpl transaction() throws SQLException {
			final Connection con=connection();
			if (txn==null) {
				txn=new WriteableTransactionTransactionImpl(con);
			}
			return txn;
		}

		void commit() throws SQLException {
			con.commit();
		}

		/**
		 * What a failed statement left behind must not poison the write of the next row: postgres
		 * refuses every further statement of a transaction whose statement failed (25P02) until it is
		 * rolled back, and this connection outlives the row that failed on it.
		 */
		void reset() {
			if (con!=null) {
				try {
					con.rollback();
				}catch (SQLException | RuntimeException e) {
					// the unchecked one as well: a driver is free to answer a rollback on a connection the
					// database dropped with one, and this runs from the catch of a failure it must not
					// replace - the caller goes on to report that failure, and in createCatalogTable() to
					// tolerate a table another session created while this one was creating it
					close();
				}
			}
		}

		@Override
		public void close() {
			if (con!=null) {
				try {
					con.close();
				}catch (SQLException e) {
					logger.trace(LocalizableMessage.raw("jdbc: unable to close the catalog connection: %s", stackTraceToSingleLineString(e)));
				}
				con=null;
				txn=null;
			}
		}
	}

	// The connection the comment statements of one sweep of openTree() calls share. Opening a
	// backend opens every tree it holds (about 25 for a stock suffix), so a connection per stamp
	// would mean that many physical connects on the first open after an upgrade - the one open
	// that stamps them all. Opened lazily: a sweep that finds every comment up to date, which is
	// every open after the first, opens nothing at all.
	final class StampSession implements Closeable {
		private Connection con;

		// Whether backslash escapes inside a literal on the connection above (mysql @@sql_mode).
		// It is a session setting of a connection the whole sweep shares, so the sweep asks once
		// instead of once per tree, and forgets it together with the session it describes.
		private Boolean mysqlBackslashEscape;

		// Set when a stamp failed for a reason no other tree of this sweep would escape either: a
		// connect that did not go through, a lock the statement gave up on. Each remaining tree
		// would pay that same bound - or that same connect attempt - again, which is a backend
		// open held for the bound times the number of its trees, all for a diagnostic aid. The
		// sweep gives up instead; nothing about the trees is remembered, so the next open retries.
		private boolean gaveUp;

		Connection connection(Dialect dialect) throws SQLException {
			if (con==null) {
				con=newStampConnection(dialect);
			}
			return con;
		}

		// Asked by the mysql statement only, and only once the connection above is open.
		boolean backslashIsEscape() throws SQLException {
			if (mysqlBackslashEscape==null) {
				mysqlBackslashEscape=isMysqlBackslashEscape(con);
			}
			return mysqlBackslashEscape;
		}

		void giveUp() {
			gaveUp=true;
		}

		boolean hasGivenUp() {
			return gaveUp;
		}

		// A statement that failed can leave the session unusable (postgres refuses every further
		// statement of the transaction with 25P02 until it is rolled back), so the stamp of the
		// next tree gets a clean one: rolled back, or replaced when even the rollback fails.
		void reset() {
			if (con!=null) {
				try {
					con.rollback();
				}catch (SQLException | RuntimeException e) {
					// the unchecked one as well: a driver is free to answer a rollback on a connection the
					// database dropped with one, and this runs from the catch of a failure it must not
					// replace - the caller goes on to report that failure, and in createCatalogTable() to
					// tolerate a table another session created while this one was creating it
					close();
				}
			}
		}

		@Override
		public void close() {
			if (con!=null) {
				try {
					con.close();
				}catch (SQLException e) {
					logger.trace(LocalizableMessage.raw("jdbc: unable to close the comment connection: %s", stackTraceToSingleLineString(e)));
				}
				con=null;
			}
			mysqlBackslashEscape=null; // it described the session that has just gone
		}
	}

	// A session setting must reach the server as a plain batch: the sql server driver runs a
	// prepared statement through sp_executesql, and a setting made there is reverted when that
	// call returns - before the statement it is meant to protect ever runs.
	//
	// Outside both layers of the bound, like the comment statement executeAny() runs, and for the
	// same reason: this is issued from newStampConnection() on a stamp connection, whose connect
	// properties carry a socket read timeout of their own (Dialect.connectProperties).
	private void executeSessionStatement(Connection con, String sql) throws SQLException {
		try (final Statement statement=con.createStatement()) {
			if (logger.isTraceEnabled()) {
				logger.trace(LocalizableMessage.raw("jdbc: %s",sql));
			}
			statement.execute(sql);
		}
	}

	// Table names are opaque SHA-224 hashes, so on the database side there is no way to tell
	// which tree a table holds. Stamp each table with its tree name (visible in "\dt+" and the
	// information schema) so database-level troubleshooting does not require recomputing hashes.
	// Runs on a dedicated connection, never on the transaction that opened the tree: comment
	// statements are DDL (an implicit commit on mysql and oracle), and a failing
	// sp_addextendedproperty rolls the whole transaction back on sql server - either would
	// corrupt work pending on the caller's connection (e.g. the trusted flag written by
	// DefaultIndex.afterOpen()). The comment is a diagnostic aid: a failed attempt only logs and
	// must not fail the backend.
	CommentResult commentTable(TreeName treeName, Dialect dialect) {
		try (final StampSession session=new StampSession()) { // a stamp of its own: no sweep to share a connection with
			return commentTable(treeName, dialect, session);
		}
	}

	CommentResult commentTable(TreeName treeName, Dialect dialect, StampSession session) {
		final String tableName=getTableName(treeName);
		if (dialect==null) { // no comment syntax and readback known for other engines: leave the table unstamped
			return CommentResult.UNSUPPORTED;
		}
		if (unstampableTrees.contains(treeName)) { // the database already rejected this one: do not ask again
			return CommentResult.FAILED;
		}
		if (session.hasGivenUp()) { // an earlier tree of this sweep lost the session every tree of it needs
			logger.debug(LocalizableMessage.raw("jdbc: table %s is left unstamped: the stamp of an earlier table of this open lost its connection", tableName));
			return CommentResult.FAILED;
		}
		final String treeComment=treeName.toString();
		try {
			// The readback runs on the stamp connection, not on one borrowed from the pool: the
			// caller of openTree() is inside a transaction and holding a pooled connection already,
			// and a pool that cannot open a second one waits for a peer to return one - which here
			// is the very thread that is waiting. The dialect comes from the caller's connection
			// for the same reason: finding it out must not cost a borrow either.
			final Connection con=session.connection(dialect);
			// comment statements are DDL (metadata lock on mysql, ddl lock on oracle) and openTree()
			// runs on every backend open: only stamp when the stored comment is absent or stale
			final String storedComment=readStoredComment(con, dialect, tableName);
			// end the read: this connection is shared by every tree of the sweep and must not hold
			// a transaction open across all of them
			con.commit();
			if (treeComment.equals(storedComment)) {
				return CommentResult.UP_TO_DATE;
			}
			final String sql;
			final String[] args;
			switch (dialect) {
			case MYSQL: // ALTER TABLE takes no binds; whether backslash escapes inside the literal depends on the sql mode of this session
				sql="alter table "+tableName+" comment "+sqlLiteral(treeComment,session.backslashIsEscape());
				args=NO_ARGS;
				break;
			case MICROSOFT: // no COMMENT ON in t-sql: MS_Description extended property (procedure arguments take binds)
				sql="declare @s sysname = schema_name()"
					+" if exists (select 1 from sys.extended_properties where class=1 and major_id=object_id(?) and minor_id=0 and name='MS_Description')"
					+" exec sys.sp_updateextendedproperty N'MS_Description', ?, N'SCHEMA', @s, N'TABLE', ?"
					+" else"
					+" exec sys.sp_addextendedproperty N'MS_Description', ?, N'SCHEMA', @s, N'TABLE', ?";
				args=new String[]{tableName, treeComment, tableName, treeComment, tableName};
				break;
			case POSTGRES: // no binds in ddl; the E'' form keeps backslash an escape character regardless of standard_conforming_strings
				sql="comment on table "+tableName+" is E"+sqlLiteral(treeComment,true);
				args=NO_ARGS;
				break;
			case ORACLE: // no binds in ddl; backslash is never an escape character in oracle literals
				sql="comment on table "+tableName+" is "+sqlLiteral(treeComment,false);
				args=NO_ARGS;
				break;
			default: // a dialect this switch was never told about must not inherit another one's ddl
				throw new IllegalStateException("no comment statement for dialect "+dialect);
			}
			try (final PreparedStatement statement=con.prepareStatement(sql)) {
				for (int i=0;i<args.length;i++) {
					statement.setString(i+1,args[i]);
				}
				executeAny(statement);
				con.commit();
			}
			return CommentResult.STAMPED;
		}catch (Exception e) {
			session.reset(); // what the failed statement left behind must not poison the stamp of the next tree
			final FailureScope scope=failureScope(e, dialect);
			if (scope==FailureScope.SESSION) {
				// the connection itself is gone, and every tree left in this sweep needs one: each
				// would pay the same connect attempt again, ~25 of them for a stock suffix
				session.giveUp();
			}else if (scope==FailureScope.TREE) {
				unstampableTrees.add(treeName);
			}
			logger.warn(LocalizableMessage.raw("jdbc: unable to comment table %s with tree name %s, it stays unstamped %s (the comment is a diagnostic aid: the backend is unaffected): %s",
				tableName, treeName, scope==FailureScope.TREE?"until this backend is closed":"for now", stackTraceToSingleLineString(e)));
			return CommentResult.FAILED;
		}
	}

	/** What a failed stamp says about stamping again - this tree, and the trees behind it in the same sweep. */
	enum FailureScope {
		/**
		 * The database rejected the statement: an account that may not comment its tables, say. It
		 * would be rejected again for this tree on every open, so the tree is remembered and not
		 * asked again while this backend is open. Says nothing about the other trees of the sweep,
		 * which are stamped as usual - the privilege may well be missing for this one table alone.
		 */
		TREE,
		/**
		 * Another session held the table locked and the stamp gave up on the bound above. Nothing
		 * is remembered - the next open tries again - and the sweep goes on: the lock belongs to
		 * this table, and the trees behind it are no more likely to be contended than usual.
		 */
		MOMENT,
		/**
		 * The connection the sweep runs on is gone, or was never established. Every tree left in
		 * the sweep would run into the same thing, one connect attempt each, so the sweep ends;
		 * nothing is remembered, since this says nothing about any of the tables.
		 */
		SESSION
	}

	// What a failed stamp says about trying again. Every chain of the failure is walked, by the walk
	// every other classifier of this class uses: a driver reports the vendor error of a rejected
	// statement as the next exception of a generic one at least as often as it reports it as the
	// cause, the statement of a try-with-resources carries what its close() saw as a suppressed
	// exception, and reading fewer of them than the others do would classify a connection that broke
	// as a rejection - which leaves the tree unstamped for the life of the backend. Walked to its end
	// rather than to MAX_CHAIN_LINKS: the seen set already terminates it, and the verdict weakens
	// under truncation rather than simply going unnoticed - a SESSION past the budget would come back
	// as TREE. The strongest verdict wins, so it is asked for in that order.
	static FailureScope failureScope(Throwable failure, Dialect dialect) {
		if (firstLinkMatching(failure, WITH_THE_RELEASE, EVERY_LINK,
				e -> scopeOf(e, dialect)==FailureScope.SESSION)!=null) {
			return FailureScope.SESSION;
		}
		if (firstLinkMatching(failure, WITH_THE_RELEASE, EVERY_LINK,
				e -> scopeOf(e, dialect)==FailureScope.MOMENT)!=null) {
			return FailureScope.MOMENT;
		}
		return FailureScope.TREE;
	}

	// What one exception of the chain says on its own.
	private static FailureScope scopeOf(SQLException e, Dialect dialect) {
		final String sqlState=e.getSQLState();
		if (e instanceof SQLTransientConnectionException || e instanceof SQLNonTransientConnectionException
				|| e instanceof SQLRecoverableException // what oracle throws for a connection that has gone
				|| (sqlState!=null && sqlState.startsWith("08"))) { // connection exception
			return FailureScope.SESSION;
		}
		if (e instanceof SQLTimeoutException || e instanceof SQLTransientException) {
			return FailureScope.MOMENT;
		}
		if (dialect==null) { // the failure came before the engine was known
			return FailureScope.TREE;
		}
		switch (dialect) {
		case POSTGRES: // 55P03 lock not available: lock_timeout expired
			return "55P03".equals(sqlState) ? FailureScope.MOMENT : FailureScope.TREE;
		case MYSQL: // 1205 lock wait timeout exceeded
			return e.getErrorCode()==1205 ? FailureScope.MOMENT : FailureScope.TREE;
		case ORACLE: // ORA-00054 resource busy, ORA-04021 timeout occurred while waiting to lock object
			return e.getErrorCode()==54 || e.getErrorCode()==4021 ? FailureScope.MOMENT : FailureScope.TREE;
		case MICROSOFT: // 1222 lock request time out period exceeded
			return e.getErrorCode()==1222 ? FailureScope.MOMENT : FailureScope.TREE;
		default: // a dialect with no lock timeout code of its own here: its failures are not treated as ones of the moment
			return FailureScope.TREE;
		}
	}

	// Returns the comment currently stored on the table, or null when there is none. The dialect is
	// passed in rather than read off the connection: the stamp sweep runs this on a connection of its
	// own, a clear runs it on the pooled one it did its work on, and both only for the dialects
	// commentTable() recognizes. It is a read and nothing else, and CachedConnection.close() rolls
	// back before the connection is handed on, so a clear leaves no transaction of its own behind.
	String readStoredComment(Connection con, Dialect dialect, String tableName) throws SQLException {
		final String sql;
		final String arg;
		switch (dialect) {
		case POSTGRES:
			sql="select obj_description(to_regclass(?), 'pg_class')";
			arg=tableName;
			break;
		case MYSQL:
			sql="select table_comment from information_schema.tables where table_schema=database() and table_name=?";
			arg=tableName;
			break;
		case ORACLE:
			sql="select comments from user_tab_comments where table_name=?";
			arg=tableName.toUpperCase();
			break;
		case MICROSOFT:
			sql="select cast(value as nvarchar(4000)) from sys.extended_properties where class=1 and major_id=object_id(?) and minor_id=0 and name='MS_Description'";
			arg=tableName;
			break;
		default: // a dialect this switch was never told about must not inherit another one's catalog
			throw new IllegalStateException("no table comment readback for dialect "+dialect);
		}
		try (final PreparedStatement statement=con.prepareStatement(sql)) {
			statement.setString(1,arg);
			return executeResultSet(statement, rs -> rs.next() ? rs.getString(1) : null);
		}
	}

	// Statistics upkeep after an import is bounded and can be turned off: gathering statistics of
	// a freshly loaded table is a full scan on oracle (dbms_stats defaults to AUTO_SAMPLE_SIZE,
	// and the entries themselves live in the blob column it reads), which a multi-million entry
	// backend would otherwise pay in full, with no way to cap or skip it, after import-ldif has
	// already reported its final status.
	static final String STATISTICS_PROPERTY="org.openidentityplatform.opendj.jdbc.statistics";
	static final String STATISTICS_TIMEOUT_PROPERTY=STATISTICS_PROPERTY+".timeout";
	private static final int STATISTICS_TIMEOUT_SECONDS_DEFAULT=600;

	// A bulk load leaves the optimizer statistics of freshly created tables stale (a table that
	// was never analyzed can make the planner badly misestimate the "where k>? order by k" cursor
	// batches - see OpenIdentityPlatform/OpenDJ#859), so refresh them once the data is in place.
	// Only the trees the import actually wrote are refreshed: rebuild-index imports a few index
	// trees, and gathering statistics of the whole backend on its behalf is a full scan per
	// table on oracle. Statistics upkeep is best-effort: a failure must not fail the import that
	// produced the data, so failures are only logged - the return value makes them observable to tests.
	boolean updateTableStatistics(Connection con, Collection<TreeName> trees) {
		if (!Boolean.parseBoolean(System.getProperty(STATISTICS_PROPERTY,"true"))) {
			logger.debug(LocalizableMessage.raw("jdbc: statistics refresh turned off by %s", STATISTICS_PROPERTY));
			return false; // nothing was refreshed
		}
		final Dialect dialect=dialectOf(con);
		if (dialect==null) { // no portable statistics refresh for other engines
			return false; // nothing was refreshed: reporting success here would make the assertion of the tests vacuous
		}
		final int timeoutSeconds=clampSeconds(Integer.getInteger(STATISTICS_TIMEOUT_PROPERTY,STATISTICS_TIMEOUT_SECONDS_DEFAULT));
		boolean allRefreshed=true;
		for (final TreeName treeName : trees) {
			final String tableName=getTableName(treeName);
			// The statement is chosen inside the try, so that the guard of the default branch
			// degrades to "this table was not refreshed" like every other failure here: the
			// contract above is that a refresh which failed never fails the import that produced
			// the data, and a throw escaping this loop would break it.
			try {
				final String sql;
				final String[] args;
				switch (dialect) {
				case POSTGRES:
					sql="analyze "+tableName;
					args=NO_ARGS;
					break;
				case MYSQL:
					sql="analyze table "+tableName;
					args=NO_ARGS;
					break;
				case ORACLE:
					sql="begin dbms_stats.gather_table_stats(user, ?); end;";
					args=new String[]{tableName.toUpperCase()};
					break;
				case MICROSOFT:
					sql="update statistics "+tableName;
					args=NO_ARGS;
					break;
				default: // a dialect this switch was never told about must not inherit another one's statement
					throw new IllegalStateException("no statistics refresh for dialect "+dialect);
				}
				try (final PreparedStatement statement=con.prepareStatement(sql)) {
					// 0: wait without limit - and false where the driver would not take the cancel, which
					// leaves the socket read timeout behind it as the only layer this refresh runs under
					final boolean cancelArmed=timeoutSeconds>0 && setQueryTimeout(statement, timeoutSeconds);
					for (int i=0;i<args.length;i++) {
						statement.setString(i+1,args[i]);
					}
					// Under the bound of the statistics refresh rather than under a class of
					// StatementBound, which would put its own value over one this statement has a
					// property for - but under both layers of it all the same: on oracle this is
					// dbms_stats.gather_table_stats, the engine whose session does not act on the
					// break its driver sends, and it runs at the very end of a successful import,
					// where a cancel that never arrives would park it with the data already
					// committed and nothing left to report.
					bounded(con, STATISTICS_TIMEOUT_PROPERTY, timeoutSeconds, cancelArmed, () -> {
						if (logger.isTraceEnabled()) {
							logger.trace(LocalizableMessage.raw("jdbc: %s",statement));
						}
						if (dialect==Dialect.MYSQL) { // mysql reports analyze problems as a result row, not an SQLException
							try (final ResultSet rs=statement.executeQuery()) {
								while (rs.next()) {
									if ("error".equalsIgnoreCase(rs.getString("Msg_type"))) {
										throw new SQLException(rs.getString("Msg_text"));
									}
								}
							}
						}else { // tolerates a statement that returns a result set, which execute() does not
							statement.execute();
						}
						return null;
					});
					con.commit();
				}
			}catch (Exception e) {
				try {
					con.rollback();
				} catch (SQLException e2) {}
				allRefreshed=false;
				logger.warn(LocalizableMessage.raw("jdbc: unable to refresh statistics of table %s (tree %s): %s",
					tableName, treeName, stackTraceToSingleLineString(e)));
			}
		}
		return allRefreshed;
	}

	/**
	 * Whether a table of this name is one the given connection reaches: in its database, and in one of
	 * the schemas an unqualified name of it resolves in - see {@link TableScope}, which is where the
	 * reason for each half of that question is. Asked of the catalog by name rather than by listing
	 * every table of the database: openTree(createOnDemand) asks it for every tree of the backend -
	 * about 25 of them for a stock suffix - on every open, on a database this backend may well be
	 * sharing with something else.
	 */
	boolean isExistsTable(Connection con, TableScope scope, String tableName) {
		// bounded as the operation it is, not as the bulk statement it guards and not as the class of
		// the transaction that happens to ask (#882): it reads a data dictionary rather than the data,
		// so a wait here is the metadata lock of another session
		try {
			return bounded(con, StatementBound.OPERATION, () -> {
				final DatabaseMetaData metaData = con.getMetaData();
				// asked with no schema pattern and read through the scope instead: what an unqualified
				// statement reaches is a path of schemas and not one of them, and a pattern is no way to
				// name a path - nor an exact way to name even one of it, "_" being a wildcard there
				try (final ResultSet rs = metaData.getTables(scope.catalog, null,
						storedIdentifier(metaData, tableName), new String[]{"TABLE"})) {
					while (rs.next()) {
						// the name still has to be compared: "_" is a single-character wildcard in a
						// metadata pattern, so "opendj_<hash>" also matches a table named "opendjX<hash>"
						if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME")) && scope.covers(rs)) {
							return true;
						}
					}
				}
				return false;
			});
		} catch (Exception e) {
			throw new StorageRuntimeException(e);
		}
	}

	@Override
	public void removeStorageFiles() throws StorageRuntimeException {
		final boolean isOpen=getStorageStatus().isWorking();
		if (!isOpen) {
			try {
				open(AccessMode.READ_WRITE);
			}catch (Exception e) {
				throw new StorageRuntimeException(e);
			}
		}
		try (final Connection con = getValidatedConnection()) {
			// where an unqualified name of this connection resolves, which every lookup below is
			// narrowed to: the skip in the loop decides between leaving a row where it is and dropping
			// the table it names, and a table of that name in another database of the server must not
			// be allowed to answer for this one - nor a table of this backend go unfound for living in
			// another schema of the search path than the one the connection works in
			final TableScope scope=TableScope.of(this, con);
			// the catalog names what this backend owns, and only that: listTrees() also names the
			// shared compressed schema trees, which another backend of this database may be the only
			// owner of and which a clear must therefore leave exactly where they lie (#881)
			final List<String> skippedRows=new ArrayList<>(); // rows the read could not act on: reported below
			final Map<TreeName,String> trees=catalogTables(con, scope, skippedRows);
			int dropped=0;
			int missing=0;
			try {
				for (final Map.Entry<TreeName,String> tree : trees.entrySet()) {
					final String tableName=tree.getValue();
					if (!isExistsTable(con, scope, tableName)) { // a row of the catalog outliving its table
						logger.warn(LocalizableMessage.raw(
							"jdbc: backend %s names tree %s, whose table %s is not there: nothing to drop for it",
							config.getBackendId(), tree.getKey(), tableName));
						missing++;
						continue;
					}
					dropTable(con, tableName);
					dropped++;
				}
				con.commit();
			} catch (Exception e) {
				// every failure of the loop and not the SQLException alone: the lookup deciding each
				// drop answers with a StorageRuntimeException of its own, and a drop left pending by one
				// of those has to go back here rather than wait for the connection to be handed back
				try {
					con.rollback();
				} catch (SQLException e2) {}
				throw e instanceof StorageRuntimeException ? (StorageRuntimeException) e : new StorageRuntimeException(e);
			}
			// all tables are gone: a table recreated later deserves a fresh stamp attempt, and the
			// memoized table name of a tree nothing holds any more is of no use to anyone
			for (final TreeName treeName : trees.keySet()) {
				tree2table.invalidate(treeName);
				unstampableTrees.remove(treeName);
			}
			try {
				reportClearOutcome(con, scope, dropped, missing, skippedRows);
			} catch (RuntimeException e) {
				// the clear itself is done and committed: an account of what it left standing must not be
				// the thing that reports it as failed, and a caller retrying it would find nothing to drop
				logger.trace(LocalizableMessage.raw("jdbc: unable to report what the clear left standing: %s",
					stackTraceToSingleLineString(e)));
			}
		} catch (StorageRuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new StorageRuntimeException(e);
		} finally {
			// the catalog went with the rest: the next tree enrolled creates its table again. The
			// online import needs exactly that - the storage which has just dropped its tables is the
			// one going on to open a root container and enrol every tree of it anew, which is what the
			// forgotten enrolments make it do rather than skip as already recorded.
			catalogTableOpened=false;
			enrolledTrees.clear();
			if (!isOpen) {
				close();
			}
		}
	}

	/**
	 * Drops one table of a clear. It is a method of its own so that the order {@link
	 * #removeStorageFiles()} drops in can be watched from a test: what names the trees has to outlive
	 * them, and that guarantee is the loop's - it holds because the loop walks the catalog's map in
	 * the order that map was built in, and a test asserting on the map instead would go on passing
	 * over a loop that had stopped doing so.
	 */
	void dropTable(Connection con, String tableName) throws SQLException {
		try (final PreparedStatement statement = con.prepareStatement("drop table " + tableName)) {
			// bulk, as #882 made every drop of this backend: nobody waits on a clear, and what it takes
			// follows the size of the table rather than the work of a caller
			execute(statement, StatementBound.BULK);
		}
	}

	/**
	 * Reports what a clear did not remove, once everything the catalog named is gone.
	 * <p>
	 * An "opendj" table still standing at that point is named by no catalog of this backend, and its
	 * name says nothing about whose it is - a table is named after the hash of its tree name. What
	 * does say so is the comment a table is stamped with as it is opened (#866): the tree name in
	 * plain text. A table whose stamp names a tree of a base DN this backend does not serve belongs to
	 * a backend sharing this database (#873) and is passed over in silence; one whose stamp names a
	 * tree of this backend is reported as its own, and so as removable by hand; one carrying no stamp
	 * at all - left by a version stamping no table, or by a database that refused the comment - can be
	 * attributed to nobody and is reported as exactly that. A stamp the database would not give up is
	 * reported apart from all of these: it says nothing either way, and counting it as a table without
	 * a stamp would turn a connection that died halfway into a confident line about tables this
	 * backend may well own.
	 * <p>
	 * The silence has a cost worth stating: a table stamped with a tree of a base DN that was taken
	 * out of the configuration while the backend was disabled reads exactly like a table of a backend
	 * sharing the database, the stamp naming the tree and never the backend it belonged to, so it is
	 * passed over too. What is left of such a base DN is found by its stamp and removed by hand.
	 * <p>
	 * The shared compressed schema pair is left out of all of it: it is kept on purpose (#881), so it
	 * is no leftover of anything, and naming it here would be asking for the removal of the one thing
	 * this code goes out of its way to spare.
	 * <p>
	 * A clear which dropped nothing at all is called out ahead of all of it: #888 was exactly such a
	 * clear, and it went by without a word in the log. A backend upgraded in place is the one case
	 * where a clear drops nothing while there is something to drop - nothing enrols a tree before
	 * {@link #removeStorageFiles()} runs, so the first offline clear of such a backend finds an empty
	 * catalog - and the line says so rather than leaving it to be found out.
	 * <p>
	 * A database which would not say what is standing gets a line of its own, whatever the clear
	 * dropped. What was left behind is exactly what could not be found out there, so it is no more a
	 * clear that left nothing than one that left something, and the count of what it did drop is the
	 * only thing that can still be stated: reporting it through the line above would say "the clear
	 * dropped no table at all" of a clear that dropped a dozen.
	 * <p>
	 * A row of the catalog the read passed over is reported wherever the clear got to, that line
	 * depending on nothing this database was asked afterwards: it is the one thing left standing that
	 * no other line here can name, since what such a row records is outside the namespace {@link
	 * #leftoverTables} scans, and the row itself is dropped by no clear either. Nothing this version
	 * writes makes such a row - {@link #getTableName} names every table {@code opendj_<hash>} - so it
	 * is the account of a database written into by something else, and it is no term of the "dropped
	 * nothing" line above: a catalog whose table is there always names itself, so a clear reading any
	 * row at all drops that one.
	 */
	void reportClearOutcome(Connection con, TableScope scope, int dropped, int missing, List<String> skippedRows) {
		final ClearLeftovers leftovers=leftoverTables(con, scope);
		if (leftovers==null) {
			// a line of its own and not a clause of the one below: this says nothing about whether
			// anything was left behind, so a clear that dropped its tables must not be reported here as
			// one that dropped none - and one that dropped none must still say so, that silence being
			// the whole of #888
			logger.warn(LocalizableMessage.raw("jdbc: backend %s: the clear dropped %d table(s) and %d of the trees its catalog names had lost their table already; what else is standing could not be read off this database, so this clear says nothing about it.%s",
				config.getBackendId(), dropped, missing, dropped==0 ? " "+CLEAR_DROPPED_NOTHING : ""));
			reportSkippedRows(skippedRows); // read off the catalog and not off this database: still worth stating
			return;
		}
		final int ours=leftovers.ours.size();
		final int unattributed=leftovers.unattributed.size();
		final int unreadable=leftovers.unreadable.size();
		// first of the lines, and not last: on a backend upgraded in place every table of it is
		// unstamped and lands in the list below, and the operator has to be told why before being
		// handed a list of tables their own backend is very probably still using.
		// The count of trees which had lost their table is all but always zero here, and is carried for
		// the case where it is not: a catalog which names anything at all names itself last, so its own
		// table is dropped and "dropped" is one - unless the catalog table went while this clear was
		// running, which is the one way a clear can name trees and still drop nothing. Each such tree is
		// logged as the loop skips it either way; this line only sums them up.
		if (dropped==0 && (missing>0 || ours>0 || unattributed>0 || unreadable>0)) {
			logger.warn(LocalizableMessage.raw("jdbc: backend %s: the clear dropped no table at all: %d of the trees its catalog names had lost their table already, and %d table(s) of this backend were named by no catalog, %d could not be attributed to anyone and %d could not be read. %s",
				config.getBackendId(), missing, ours, unattributed, unreadable, CLEAR_DROPPED_NOTHING));
		}
		reportSkippedRows(skippedRows); // after the reason above and among the lists, being a list itself
		if (ours>0) {
			logger.warn(LocalizableMessage.raw("jdbc: backend %s: %d table(s) of %s hold trees of this backend that its catalog does not name, and the clear left them where they are: %s. A tree is enrolled as it is opened read-write and by no other means, so such a table is one of a tree of a base DN this backend still serves that was taken out of the configuration while it was disabled - an attribute index, say - or one left by a version keeping no catalog: it is this backend's own and can be removed by hand, and re-adding the tree it belongs to adopts it with the rows it still holds",
				config.getBackendId(), ours, scope.name(), leftovers.ours));
		}
		if (unattributed>0) {
			logger.warn(LocalizableMessage.raw("jdbc: backend %s: %d opendj table(s) of %s are named by no catalog of this backend and carry no tree stamp, so nothing says whose they are: %s. They may hold the trees of a backend sharing this database, which nothing forbids, or be leftovers of a version stamping no table at all - a table is named after the hash of its tree name and can be attributed by no other means. They were left exactly where they are",
				config.getBackendId(), unattributed, scope.name(), leftovers.unattributed));
		}
		if (unreadable>0) {
			logger.warn(LocalizableMessage.raw("jdbc: backend %s: the stamp of %d opendj table(s) of %s could not be read, so this clear says nothing about whose they are: %s. They were left exactly where they are",
				config.getBackendId(), unreadable, scope.name(), leftovers.unreadable));
		}
	}

	/**
	 * Reports the rows of the catalog the clear could not act on; see {@link #readCatalogRows} for
	 * what makes a row one of these and {@link #reportClearOutcome} for why they are a line of their
	 * own. Silent where there are none, which is every clear of a catalog this backend wrote.
	 */
	private void reportSkippedRows(List<String> skippedRows) {
		if (skippedRows.isEmpty()) {
			return;
		}
		logger.warn(LocalizableMessage.raw("jdbc: backend %s: %d row(s) of its catalog name nothing this clear could drop and were passed over: %s. Neither the row nor whatever it records was removed, and no other line of this clear names them: the tables of this backend are named after the hash of a tree name, so what such a row records is outside the names a clear can account for. A catalog holding one was written into by something other than this backend",
			config.getBackendId(), skippedRows.size(), skippedRows));
	}

	/**
	 * Why a clear can drop nothing while there is something to drop, said wherever one did: it is the
	 * silence of #888, and the one thing an operator reading such a line has to be told.
	 */
	private static final String CLEAR_DROPPED_NOTHING="A backend upgraded from a version keeping no catalog has to be started once before its first offline \"import-ldif --clearBackend\": nothing enrols a tree before the clear runs, so that first clear finds a catalog that is not there and names nothing";

	/** What a clear left standing, told apart by the tree stamp of each table; see {@link #reportClearOutcome}. */
	static final class ClearLeftovers {
		/** Tables whose stamp names a tree of this backend: its own, and removable by hand. */
		final List<String> ours=new ArrayList<>();
		/** Tables carrying no stamp naming a tree: they can be attributed to nobody. */
		final List<String> unattributed=new ArrayList<>();
		/** Tables whose stamp the database would not give up: they are attributed neither way. */
		final List<String> unreadable=new ArrayList<>();
	}

	/**
	 * The "opendj" tables this connection reaches - see {@link TableScope} - that this backend can say
	 * something about, or {@code null} where the database would not list them. A table stamped with a
	 * tree this backend does not serve is in none of the lists: it is a backend sharing this database
	 * (#873) that it belongs to, and no part of this clear's outcome.
	 */
	ClearLeftovers leftoverTables(Connection con, TableScope scope) {
		// the shared compressed schema pair is left standing on purpose, so it is no leftover of
		// anything and reporting it would be pointing at the one thing this code goes out of its way
		// to keep. Taken out by name and not by stamp: an installation may hold the pair unstamped,
		// from a version that commented no table at all.
		final Set<String> leftOnPurpose=new HashSet<>();
		for (final TreeName treeName : SHARED_COMPRESSED_SCHEMA_TREES) {
			leftOnPurpose.add(readTableName(treeName).toLowerCase());
		}
		final ClearLeftovers leftovers=new ClearLeftovers();
		try {
			final List<String> standing=new ArrayList<>();
			final DatabaseMetaData metaData=con.getMetaData();
			try (final ResultSet rs=metaData.getTables(scope.catalog, null,
					storedIdentifier(metaData, "opendj%"), new String[]{"TABLE"})) {
				while (rs.next()) {
					final String tableName=rs.getString("TABLE_NAME");
					if (tableName==null) { // a row naming no table names nothing this clear can report
						continue;
					}
					if (!leftOnPurpose.contains(tableName.toLowerCase()) && scope.covers(rs)) {
						standing.add(tableName);
					}
				}
			}
			// the stamps are read once the metadata result set is closed: they are queries of this very
			// connection, and a driver may hold it for the whole of that result set
			final Dialect dialect=dialectOf(con);
			if (dialect==null) {
				// no comment readback is known for this engine, so no table of it can be attributed to
				// anyone at all. That is a different thing from a table which carries no stamp, and saying
				// the second would be telling an operator that every table of every backend of this
				// database is of unknown ownership when the truth is that nothing was ever asked
				leftovers.unreadable.addAll(standing);
				return leftovers;
			}
			for (final String tableName : standing) {
				final TreeName stamp;
				try {
					stamp=stampedTree(con, dialect, tableName);
				} catch (SQLException | RuntimeException e) {
					// this table alone is unaccounted for, and the ones after it need not be: postgres
					// refuses every further statement of a transaction whose statement failed (25P02), so
					// the read that failed is rolled back before the next table is asked about. There is
					// nothing pending to lose - the clear committed its drops before this ran
					logger.trace(LocalizableMessage.raw("jdbc: unable to read the stamp of table %s: %s",
						tableName, stackTraceToSingleLineString(e)));
					leftovers.unreadable.add(tableName);
					try {
						con.rollback();
					} catch (SQLException e2) {}
					continue;
				}
				if (stamp==null) {
					leftovers.unattributed.add(tableName);
				} else if (isOwnTree(stamp)) {
					leftovers.ours.add(tableName+" ("+stamp+")");
				}
			}
		} catch (SQLException e) {
			logger.trace(LocalizableMessage.raw("jdbc: unable to look for the tables a clear left behind: %s",
				stackTraceToSingleLineString(e)));
			return null;
		}
		return leftovers;
	}

	/**
	 * The tree named by the comment this table carries (#866), or {@code null} where it carries none
	 * or where what it carries is not the name of a tree. The stamp is the only thing that attributes
	 * a table to a backend at all - a table name is a bare hash - and stamping is best-effort, so the
	 * absence of one states nothing.
	 * <p>
	 * A read the database refused is passed to the caller rather than answered as an absent stamp: the
	 * two say different things, and the second would let a connection that died halfway be reported as
	 * a row of tables nothing can be said about. An engine with no readback of its own is the same
	 * distinction one step earlier, and is answered by the caller: it puts every table of such an
	 * engine where nothing was asked of it belongs, which is not where a table without a stamp goes.
	 */
	private TreeName stampedTree(Connection con, Dialect dialect, String tableName) throws SQLException {
		final String comment=readStoredComment(con, dialect, tableName);
		if (comment==null || comment.isEmpty()) {
			return null;
		}
		try {
			return TreeName.valueOf(comment);
		} catch (RuntimeException e) { // a comment of somebody else's making: no stamp of this backend's kind
			return null;
		}
	}

	/**
	 * The base DN the compressed schema trees of this backend are named under since #881, spelled out
	 * here for the reason {@link #SHARED_COMPRESSED_SCHEMA_TREES} is: the prefix is built by a private
	 * method of {@code PersistentCompressedSchema}, escapes and all. A table stamped with one of these
	 * carries this backend's id in plain text, so a clear that finds one standing can say whose it is.
	 */
	private String ownCompressedSchemaBaseDN() {
		return SHARED_COMPRESSED_SCHEMA_BASE_DN+"_"+escapedBackendId();
	}

	/**
	 * The backend id as one component of a tree name. A tree name is {@code /<base DN>/<id>} and is
	 * read back by splitting on its slashes ({@code TreeName.valueOf}), so an id carrying one of them
	 * would name a tree that parses into another tree than it was built from - and a table is stamped
	 * with that name (#866), so a clear reading the stamp of a table of this backend's own would then
	 * fail to recognize it and pass it over in silence. The escape is the one {@code
	 * PersistentCompressedSchema} spells its own prefix with, percent first so that the escape of the
	 * slash cannot be produced twice, and it leaves an id of the ordinary shape exactly as it is -
	 * which is what keeps the table names of an installation unchanged.
	 */
	private String escapedBackendId() {
		return config.getBackendId().replace("%", "%25").replace("/", "%2F");
	}

	/**
	 * Whether this tree is one of this backend's own: a tree of a base DN it serves, its own catalog,
	 * or its own pair of compressed schema trees. The catalog counts because a clear drops it last, so
	 * one still standing is a clear of this backend that did not get to the end, and never anything of
	 * anybody else's. The compressed schema pair counts because since #881 it is named after the
	 * backend id (#873) and so belongs to this backend as plainly as any tree of a base DN it serves -
	 * where the legacy pair, named from a literal, belongs to no backend in particular and is reported
	 * by nobody.
	 */
	private boolean isOwnTree(TreeName treeName) {
		if (getCatalogTree().equals(treeName) || ownCompressedSchemaBaseDN().equals(treeName.getBaseDN())) {
			return true;
		}
		final SortedSet<DN> baseDNs=config.getBaseDN();
		if (baseDNs==null) {
			return false;
		}
		for (final DN baseDN : baseDNs) {
			// every tree of an entry container is named after the normalized form of its base DN,
			// which is what EntryContainer builds its tree names from
			if (treeName.getBaseDN().equals(baseDN.toNormalizedUrlSafeString())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The database and the schemas a connection reaches with an unqualified name: what every table
	 * lookup of this backend is narrowed to.
	 * <p>
	 * The database is the half that has to narrow. Asked with a null catalog the question spans the
	 * whole server on some drivers - Connector/J reads a null catalog as "any database" since 8.0, and
	 * its databaseTerm being CATALOG it ignores the schema pattern besides - and every answer of such a
	 * lookup decides something a table of the same name in another database must have no say in. A
	 * clear skips the row of a table that is gone so that it can go on, and a foreign table answering
	 * for it turns that skip into an unqualified "drop table" of a table that is not in this database,
	 * failing the clear on this attempt and on every attempt after it. An open of a tree creates its
	 * table where there is none, and a foreign table answering for it skips the creation, leaving the
	 * catalog naming a tree whose table is not here. Two backends of the stock backend id in two
	 * databases of one server name their tables alike, so this is the ordinary layout and not a corner
	 * of one.
	 * <p>
	 * The schema is the half that must not narrow to one name. The statements this scope guards are
	 * unqualified, and an unqualified name resolves across a path of schemas: the whole
	 * {@code search_path} on postgresql, the default schema of the user and then {@code dbo} on sql
	 * server. A lookup narrowed to {@code current_schema()} alone would be the stricter question of the
	 * two - an installation whose tables were created in {@code public} while the connection now works
	 * in a schema of its own reads and writes them unqualified all the same, and asking only about that
	 * schema would report them absent: the clear would drop nothing, which is #888 over again, and the
	 * next open would create a second, empty set of tables shadowing the populated ones for every later
	 * unqualified reference. The path is asked of the connection, so that a lookup answers for exactly
	 * the tables the statements behind it reach - no more and no fewer.
	 */
	static final class TableScope {
		/** The database of the connection, or {@code null} where the driver names none - oracle has none. */
		final String catalog;
		/**
		 * The schemas an unqualified name of this connection resolves in, nearest first, or {@code null}
		 * where the schema is no dimension of this engine - mysql, whose schema is its database - or
		 * where the connection would not say. A null path narrows nothing, which is the question this
		 * class asked before there was anything to narrow it by.
		 */
		final List<String> schemas;
		/**
		 * Whether the connection answered both questions. One it would not answer leaves the lookup as
		 * wide as it ever was - fail-open, which is the safe direction for the schema and the weak one
		 * for the database - so the caller asks again rather than latching that answer for the life of a
		 * transaction; see {@link ReadableTransactionImpl#takeTableScope()}.
		 */
		final boolean answered;

		private TableScope(String catalog, List<String> schemas, boolean answered) {
			this.catalog=catalog;
			this.schemas=schemas;
			this.answered=answered;
		}

		/**
		 * What this connection says about where an unqualified name of it resolves. The storage is
		 * taken because one engine is asked with a statement rather than with a method of its driver,
		 * and a statement of this backend takes the bound of its class (#882).
		 */
		static TableScope of(JDBCStorage storage, Connection con) {
			return of(storage, con, true);
		}

		/**
		 * The same, told to keep quiet about a connection that will not answer. A transaction asks
		 * again for as long as it is refused - a lookup left as wide as the whole server decides a
		 * create and a drop - and one refusal per tree of the backend is one line per tree in the log,
		 * each with a stack trace, for a thing that was already said.
		 */
		static TableScope of(JDBCStorage storage, Connection con, boolean report) {
			String catalog=null;
			List<String> schemas=null;
			boolean answered=true;
			try {
				// an empty name is not the name of a database but a driver's way of saying it has none,
				// and passed to a metadata pattern it means "tables that belong to no catalog" - which is
				// not the same question and would answer nothing
				catalog=emptyToNull(con.getCatalog());
			} catch (Exception e) {
				// said out loud rather than swallowed: this decides a create and a drop, and a lookup
				// that silently reverts to the whole server is the one failure of the two that cannot be
				// seen from its outcome
				answered=false;
				log(report, "jdbc: this connection would not name the database it works in, so a table of another database of this server may answer for one of this backend's: %s", e);
			}
			try {
				schemas=schemaPathOf(storage, con);
			} catch (Exception e) {
				answered=false;
				log(report, "jdbc: this connection would not name the schemas an unqualified name of it resolves in, so a table of any schema may answer for one of this backend's: %s", e);
			}
			return new TableScope(catalog, schemas, answered);
		}

		/** Said once where it is worth saying, and kept for the trace where it would be said again. */
		private static void log(boolean report, String message, Exception e) {
			if (report) {
				logger.warn(LocalizableMessage.raw(message, stackTraceToSingleLineString(e)));
			} else {
				logger.trace(LocalizableMessage.raw(message, stackTraceToSingleLineString(e)));
			}
		}

		/** The schemas an unqualified name resolves in, in the order this engine resolves them. */
		private static List<String> schemaPathOf(JDBCStorage storage, Connection con) throws SQLException {
			final String driverName=driverNameOf(con);
			if (driverName.contains("mysql")) {
				// the schema of Connector/J is the database, and which of the two names it answers with is
				// the databaseTerm of the connection: with CATALOG - the default - getSchema() answers null
				// and the catalog above is the narrowing, and with SCHEMA it is the other way round. Asked
				// rather than assumed, so that neither setting leaves this lookup narrowed by nothing at all
				final String database=emptyToNull(con.getSchema());
				return database==null ? null : Collections.singletonList(database);
			}
			if (driverName.contains("postgres")) {
				// getSchema() is "select current_schema()" on pgjdbc - the first existing schema of the
				// search_path - while an unqualified reference resolves across the whole of it.
				// Behind a savepoint, because this runs on the caller's transaction and postgres refuses
				// every further statement of a transaction whose statement failed (25P02): a query this
				// engine turns out not to have - pgjdbc talks to more than one of them - would otherwise
				// surface as the next statement of the caller failing, with the cause nowhere near it
				final Savepoint before=savepoint(con);
				try (final PreparedStatement statement=con.prepareStatement("select unnest(current_schemas(true))")) {
					// bounded like every other statement of this backend (#882), and by the class the lookups
					// this scope narrows take: it reads a session setting rather than the data, and what the
					// savepoint and the fallback below answer for is a query this engine refuses - not one it
					// never answers at all, which is a wait holding the open of a tree with nothing to end it
					final List<String> path=storage.executeResultSet(statement, StatementBound.OPERATION, rs -> {
						final List<String> read=new ArrayList<>();
						while (rs.next()) {
							final String schema=emptyToNull(rs.getString(1));
							if (schema!=null) {
								read.add(schema);
							}
						}
						return read;
					});
					release(con, before);
					if (!path.isEmpty()) {
						return Collections.unmodifiableList(path);
					}
				} catch (Exception e) { // asked of getSchema() below instead, as well as it can say it
					undo(con, before);
					logger.debug(LocalizableMessage.raw("jdbc: unable to read the search path of this connection, which is asked for its current schema instead: %s",
						stackTraceToSingleLineString(e)));
				}
			}
			final String schema=emptyToNull(con.getSchema());
			if (schema==null) {
				return null;
			}
			if (driverName.contains("microsoft")) {
				// an unqualified name resolves in the default schema of the user and then in dbo
				return Collections.unmodifiableList(Arrays.asList(schema, "dbo"));
			}
			// oracle resolves in the current schema, and past it through synonyms this cannot enumerate:
			// a table reached through one is not found here, and an open creates it again in the schema
			return Collections.singletonList(schema);
		}

		/**
		 * A point to put a transaction back to, or {@code null} where this connection is in no
		 * transaction to speak of or would not take one. A read of the search path is answered by the
		 * connection of whoever asked for the scope, and a failed statement of it is theirs to be
		 * spared.
		 */
		private static Savepoint savepoint(Connection con) {
			try {
				return con.getAutoCommit() ? null : con.setSavepoint("opendj_search_path");
			} catch (SQLException | RuntimeException e) {
				return null;
			}
		}

		/** Puts the transaction back to where the probe found it, so that its failure stays the probe's. */
		private static void undo(Connection con, Savepoint savepoint) {
			if (savepoint!=null) {
				try {
					con.rollback(savepoint);
				} catch (SQLException | RuntimeException e) {}
			}
		}

		/** Gives up a savepoint nothing needs any more: a transaction keeps them all until it ends. */
		private static void release(Connection con, Savepoint savepoint) {
			if (savepoint!=null) {
				try {
					con.releaseSavepoint(savepoint);
				} catch (SQLException | RuntimeException e) {}
			}
		}

		/**
		 * Whether the table this row of a listing describes is one this connection reaches. The metadata
		 * pattern alone does not settle it: a schema reaches {@link DatabaseMetaData#getTables} as a
		 * pattern, where "_" is a single-character wildcard, so a listing narrowed to a schema named
		 * "app_data" is answered for by one named "appXdata" as well. The listings of this class are
		 * asked with no schema pattern at all - a path is more than one name anyway - and read through
		 * this instead.
		 */
		boolean covers(ResultSet rs) throws SQLException {
			return isSameCatalog(rs.getString("TABLE_CAT")) && isOnSchemaPath(rs.getString("TABLE_SCHEM"));
		}

		/**
		 * Whether the database of a listed table rules it out. A name neither side gives is no
		 * narrowing: a driver naming no catalog of its own - oracle has none - must not be read as
		 * naming another.
		 */
		private boolean isSameCatalog(String ofTable) {
			return catalog==null || ofTable==null || ofTable.isEmpty() || catalog.equalsIgnoreCase(ofTable);
		}

		/** Whether a listed table is in one of the schemas an unqualified name of this connection resolves in. */
		private boolean isOnSchemaPath(String ofTable) {
			if (schemas==null || schemas.isEmpty() || ofTable==null || ofTable.isEmpty()) {
				return true;
			}
			for (final String schema : schemas) {
				if (schema.equalsIgnoreCase(ofTable)) {
					return true;
				}
			}
			return false;
		}

		/** How the database and the schemas a table count was taken over are named in a log line. */
		String name() {
			final String where=schemas==null || schemas.isEmpty() ? null : String.join(", ", schemas);
			if (catalog!=null && where!=null) {
				return catalog+"."+where;
			}
			if (catalog!=null) {
				return catalog;
			}
			return where!=null ? where : "this connection";
		}

		/** An empty name is the name of nothing: see {@link #of}. */
		private static String emptyToNull(String name) {
			return name==null || name.isEmpty() ? null : name;
		}
	}

	//operation
	/**
	 * {@inheritDoc}
	 * <p>
	 * A rolled back read is <em>not</em> replayed, as
	 * {@link org.opends.server.backends.pluggable.spi.Storage#read(ReadOperation)} requires: two of the read
	 * operations of this server are not idempotent, and replaying them corrupts their result rather than repairing
	 * it. {@code ExportJob} runs the whole export inside a single read and its LDIF writer is opened once, so a
	 * replay appends the entries already written instead of truncating the file; {@code VerifyJob} accumulates its
	 * counters in instance fields that no attempt resets, so a replay reports twice the entry count of the backend.
	 * Both are reachable while the server is online, since an export holds no more than a shared backend lock.
	 * A conflict therefore fails the read here, exactly as it did before the retry of {@link #write} was added.
	 * <p>
	 * A connection the database dropped is not replayed either, for the same reason - but it is reported to the
	 * pool, which cannot notice one on its own: a borrow inside the alive window of
	 * {@link CachedConnection#ALIVE_BYPASS_PROPERTY} asks the database nothing, so the statement that broke is the
	 * only place the drop is ever seen.
	 */
	@Override
	public <T> T read(ReadOperation<T> readOperation) throws Exception {
		//borrowed outside the try: a connect the pool could not make says nothing about the connections it
		//holds - mysql reports a server at its connection limit as 08004, which is class 08 like a connection
		//that broke - and distrusting the pool over it would validate every borrow under the very load the
		//window exists for, against a server already refusing connections
		final Connection con=getConnection();
		boolean dropped=false;
		try (con) {
			try {
				return readOperation.run(new ReadableTransactionImpl(con));
			} catch (Exception e) {
				//asked while this read still owns the connection: once the release below has returned it to
				//the pool, another borrow may hold it and the driver would be answering about that one
				dropped=isConnectionFailure(e,con);
				if (dropped) {
					//told before the release rather than after it: a rollback that never reaches the server -
					//which is what pgjdbc does with a transaction it left IDLE - leaves the connection poolable,
					//so the release puts the dropped connection back at the head of the deque, and a borrow
					//racing the distrust would be handed it unvalidated
					distrustPool();
				}
				throw e;
			}
		} catch (Exception e) {
			//also the release of the connection: its rollback is the one round trip a read that found
			//nothing makes, so it can be the only place a drop is ever seen
			if (!dropped && isConnectionFailure(e)) {
				distrustPool();
			}
			throw e;
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * {@link org.opends.server.backends.pluggable.spi.Storage#write(WriteOperation)} requires an implementation to
	 * retry a rolled back operation until it succeeds, and {@link WriteOperation} is documented as idempotent for
	 * exactly that reason; {@link org.opends.server.backends.pdb.PDBStorage#write(WriteOperation)} already does so
	 * on the conflict exception of its own engine. The loop is bounded here, unlike PDBStorage: the database may be
	 * shared with writers outside this server, so a conflict is not guaranteed to clear and failing the operation is
	 * better than never returning. It is bounded twice - by {@link #MAX_RETRIES} attempts and by the
	 * {@link #MAX_RETRY_WINDOW_NANOS} wall-clock window - because an attempt is not guaranteed to be short: a
	 * conflict an engine reports only after its own lock wait timeout would otherwise multiply that wait by the
	 * attempt count. A conflict that slow consumes the whole window in one attempt and is not replayed, which is
	 * what master did with it.
	 * <p>
	 * Only the operation itself is replayed: a failure of {@link #getConnection()} or of the implicit
	 * {@link Connection#close()} - which returns the connection to the pool after a rollback - leaves the loop, so
	 * that a completed write is never replayed because releasing its connection failed.
	 * <p>
	 * A connection the database dropped is replayed as well, on a connection the next attempt borrows of its own.
	 * That is what makes the alive window of {@link CachedConnection#ALIVE_BYPASS_PROPERTY} safe to leave on: a
	 * connection handed out unvalidated and found dead costs an attempt rather than the operation, and a write of
	 * the replication replay - which records a failed operation as applied and advances the server state past it,
	 * see #889 - never sees it. Only while nothing of the attempt may have been committed yet, though: see
	 * {@link #replayReason(Throwable, String, boolean, boolean, boolean)}.
	 */
	@Override
	public void write(WriteOperation writeOperation) throws Exception {
		final long giveUpAt=System.nanoTime()+MAX_RETRY_WINDOW_NANOS;
		for (int attempt=1;;attempt++) {
			Exception failure=null;
			String driver=null;
			boolean committing=false;
			boolean dropped=false;
			boolean partlyCommitted=false;
			//borrowed outside the try, for the reason read() borrows outside it: a connect the pool could not
			//make is not a connection of this pool that broke, and it leaves the loop as it always did
			final Connection con=getConnection();
			try (con) {
				driver=driverNameOf(con);
				final WriteableTransactionTransactionImpl txn=new WriteableTransactionTransactionImpl(con);
				try {
					writeOperation.run(txn);
					committing=true;
					con.commit();
					return;
				} catch (Exception e) {
					try {
						con.rollback();
					} catch (SQLException ex) {
						//joined to the failure rather than dropped: a rollback issued on a connection the
						//database dropped is often the first place - and on a driver that reports a killed
						//session as a plain vendor error, the only place - the drop is stated outright, and
						//every classifier below reads the chains of this failure
						e.addSuppressed(ex);
					}
					//asked while this attempt still owns the connection: the release below returns it to the
					//pool, and the driver would then be answering about whichever borrow holds it next
					dropped=isConnectionFailure(e,con);
					if (dropped) {
						//told before the release rather than after it, for the reason read() tells it there: a
						//rollback that never reached the server leaves the connection poolable, so the release
						//returns the dropped connection to the head of the deque, where a borrow racing this
						//would be handed it unvalidated
						distrustPool();
					}
					//rethrown, so that a failure of the implicit close() is suppressed into the failure being
					//replayed rather than replacing it
					failure=e;
					throw e;
				} finally { // the comment connection lives no longer than the trees it stamped, and no longer
					// than the attempt that opened it: a replay stamps on a session of its own. The catalog
					// connection goes with it, having written every row this attempt had to enrol - and
					// committed each of them, so a replay finds them recorded and writes none again
					partlyCommitted=txn.partlyCommitted;
					try {
						try {
							txn.stampSession.close();
						} finally {
							txn.catalogSession.close();
						}
					} catch (RuntimeException e) {
						//the stamp is a diagnostic aid and must not become the outcome of the write: an unchecked
						//throw out of a driver's close() would otherwise replace the failure being unwound (JLS
						//14.20.2) - the very one the replay is decided on and the only one that says what went
						//wrong - or turn a transaction that has just committed into a failure of its own
						if (failure!=null) {
							failure.addSuppressed(e);
						} else {
							logger.trace(LocalizableMessage.raw("jdbc: unable to close the comment connection: %s",
									stackTraceToSingleLineString(e)));
						}
					}
				}
			} catch (Exception e) {
				//anything the operation did not throw comes from around it - the name of the driver, the
				//transaction, or the implicit close() that returns the connection to the pool: none of them
				//belongs to the replayed region
				if (e!=failure) {
					//a drop reported by the release of the connection still has to reach the pool, which has no
					//other way of hearing of it. Only the chains of the failure can be asked for it now: the
					//connection has been released, and whether it is closed is no longer this attempt's answer
					if (isConnectionFailure(e)) {
						distrustPool();
					}
					throw e;
				}
			}
			//a drop the release of the connection reported still has to reach the pool, which has no other way
			//of hearing of it. It is suppressed into the failure being unwound (JLS 14.20.3.1) rather than
			//replacing it, which is what leaves e==failure and skips the branch above - and it is the very
			//evidence replayReason() replays the attempt on, so the pool must not be told less than the loop
			//acts on. The drop of the operation itself was reported before the release, above
			if (!dropped && isConnectionFailure(failure)) {
				distrustPool();
			}
			final String reason=replayReason(failure,driver,committing,partlyCommitted,dropped);
			//System.nanoTime()-giveUpAt is the overflow safe form of the comparison
			if (reason==null || attempt>=MAX_RETRIES || System.nanoTime()-giveUpAt>=0) {
				throw failure;
			}
			//logged rather than silently absorbed, so that a deployment retrying most of its writes stays observable;
			//one line per replay, since an add can emit nine of them and a stack trace each time reads as a failure
			logger.warn(LocalizableMessage.raw("jdbc: replaying the transaction after %s, attempt %d of %d: %s",
					reason, attempt, MAX_RETRIES, conflictSummary(failure, driver)));
			if (logger.isTraceEnabled()) {
				logger.trace("jdbc: the failure being replayed was %s", stackTraceToSingleLineString(failure));
			}
			try {
				//randomized to spread the retries of the transactions that collided, growing to outlast contention
				Thread.sleep(retryDelayMillis(attempt));
			} catch (InterruptedException e) {
				//sleep cleared the interrupt flag: restore it, and report the failure being retried rather than the
				//interrupt, which would hide from the caller what actually went wrong
				Thread.currentThread().interrupt();
				failure.addSuppressed(e);
				throw failure;
			}
		}
	}

	/**
	 * Why the operation of a {@link #write} is worth replaying, as the noun phrase the message reporting the replay
	 * names - or null for a failure this loop must not repeat.
	 * <p>
	 * A transaction conflict is replayable whichever phase reported it: the engine rolled the transaction back
	 * before it answered. It is read from the failure of the operation only, never from the release of the
	 * connection - see {@link #isRetryableConflict} - since the release runs after the outcome was decided and
	 * cannot make that claim for it. A connection the database dropped is replayable only while the transaction
	 * had not been committed yet. A drop reported by {@code commit()} leaves the outcome unknown - the server may
	 * have committed and died before the answer reached us - and replaying a write that in fact committed applies
	 * it twice, which is the very reason 40003 is one of {@link #NON_REPLAYABLE_ROLLBACK_STATES}.
	 * <p>
	 * Nothing is replayable once the attempt has committed part of its own work, whatever the failure says. The DDL
	 * of {@link WriteableTransactionTransactionImpl#openTree} and {@link WriteableTransactionTransactionImpl#deleteTree}
	 * commits inside {@link WriteOperation#run}, and mysql and oracle commit before a DDL statement whether asked
	 * to or not, so the attempt no longer rolls back as a whole - and {@link WriteOperation} is only idempotent in
	 * the database. {@code RootContainer.open} opens and registers every entry container of every base DN in one
	 * write: replayed after the trees of the first base DN were created and committed, it registers that base DN a
	 * second time and fails with ERR_ENTRY_CONTAINER_ALREADY_REGISTERED, which masks the failure that caused the
	 * replay and leaves the indexes of the previous attempt behind with their configuration listeners.
	 *
	 * @param committing whether the failure was reported by {@code commit()}, which leaves the outcome unknown
	 * @param partlyCommitted whether the attempt committed part of its work before it failed
	 * @param connectionClosed whether the driver closed the connection under the failure - evidence no SQLState
	 * carries on mssql-jdbc, which reports a killed session as S0001 and closes the connection behind it
	 */
	static String replayReason(Throwable failure, String driver, boolean committing, boolean partlyCommitted,
			boolean connectionClosed) {
		if (partlyCommitted) {
			return null;
		}
		if (isRetryableConflict(failure, driver)) {
			return "a conflict";
		}
		if (!committing && (connectionClosed || isConnectionFailure(failure))) {
			return "a connection the database dropped";
		}
		return null;
	}

	/**
	 * Whether a failure says the connection is gone rather than the statement rejected, asked of the failure and of
	 * the connection it was raised on. A driver is not required to say so in a SQLState: mssql-jdbc reports a
	 * session killed by {@code KILL}, by the resource governor or by an availability group transition as error 596,
	 * 3980, 10054, 18456 or 4060, and {@code generateStateCode} maps none of them - with xopenStates off, which is
	 * its default, every one of them comes out as {@code "S"+errorState}, measured as S0001. What the driver does
	 * do is close the connection for any error of severity 20 and above, before it throws.
	 * <p>
	 * Asked only while the operation that failed still owns the connection: a released one is back in the pool and
	 * may already have been handed to another borrow, whose state it would then be answering about.
	 */
	static boolean isConnectionFailure(Throwable failure, Connection con) {
		return isConnectionFailure(failure) || isClosed(con);
	}

	/** Whether the driver reports the connection as closed; one that cannot answer is taken as closed. */
	private static boolean isClosed(Connection con) {
		try {
			return con.isClosed();
		} catch (SQLException e) {
			return true;
		}
	}

	/**
	 * Whether a failure says the connection is gone rather than the statement rejected: the database dropped it,
	 * restarted, failed over, or the network did.
	 * <p>
	 * Both chains of the failure are walked, for the reason {@link #failureScope} walks both: a driver reports the
	 * error that says what happened as the next exception of a generic one at least as often as it reports it as
	 * the cause, and mssql-jdbc chains every error of a message it received that way. The suppressed exceptions are
	 * walked with them, since the rollback and the release of a connection report a drop there - a write whose
	 * operation failed for its own reasons carries the drop of its {@code close()} as a suppressed exception (JLS
	 * 14.20.3.1) rather than as a cause. The walk starts at the failure this class was handed because it reaches it
	 * wrapped in a {@link StorageRuntimeException}, and a caller such as {@code EntryContainer.addEntry} may wrap
	 * it once more.
	 */
	static boolean isConnectionFailure(Throwable failure) {
		return firstLinkMatching(failure, WITH_THE_RELEASE, JDBCStorage::saysTheConnectionIsGone)!=null;
	}

	/**
	 * Whether {@link #firstLinkMatching} reads the suppressed exceptions along with the causes and the next
	 * exceptions. They are where the release of the connection reports what it saw - a rollback that failed as the
	 * attempt was unwound is suppressed into the failure being unwound (JLS 14.20.3.1) - so a question about the
	 * connection is asked of them, and a question about what the engine did with the transaction is not: the
	 * release runs after the outcome was decided, and cannot speak for it.
	 */
	private static final boolean WITH_THE_RELEASE=true;
	private static final boolean WITHOUT_THE_RELEASE=false;

	/**
	 * The first {@link SQLException} of the chains of a failure that answers the given question, or null where none
	 * does. Every classifier of this class walks the failure this way, so that none of them reads a chain the others
	 * act on: what makes a write replayable must also be what the pool is told about and what the replay logs.
	 */
	private static SQLException firstLinkMatching(Throwable failure, boolean withTheRelease,
			Predicate<SQLException> matches) {
		return firstLinkMatching(failure, withTheRelease, MAX_CHAIN_LINKS, matches);
	}

	/** The walk above, with the number of links it is allowed to look at. */
	private static SQLException firstLinkMatching(Throwable failure, boolean withTheRelease, int links,
			Predicate<SQLException> matches) {
		final Deque<Throwable> pending=new ArrayDeque<>();
		final Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
		if (failure!=null) {
			pending.push(failure);
		}
		while (!pending.isEmpty() && seen.size()<links) {
			final Throwable e=pending.pop();
			if (!seen.add(e)) { // a driver that chains an exception back to itself must not loop this walk
				continue;
			}
			if (e.getCause()!=null) {
				pending.push(e.getCause());
			}
			if (withTheRelease) {
				for (final Throwable suppressed : e.getSuppressed()) {
					pending.push(suppressed);
				}
			}
			if (!(e instanceof SQLException)) {
				continue;
			}
			final SQLException sqlException=(SQLException) e;
			if (sqlException.getNextException()!=null) {
				pending.push(sqlException.getNextException());
			}
			if (matches.test(sqlException)) {
				return sqlException;
			}
		}
		return null;
	}

	/**
	 * What one exception of the chain says on its own. The types are asked before the SQLState, the way
	 * {@link #scopeOf} asks them: they are what the JDBC contract gives a driver to say the connection is gone, and
	 * a driver that raises one of them has said so whatever state it filled in. Oracle reports ORA-03113, ORA-00028
	 * and ORA-01089 as {@link SQLRecoverableException} and happens to map them to 08006 as well; the type is what
	 * makes that robust rather than lucky.
	 */
	private static boolean saysTheConnectionIsGone(SQLException e) {
		if (e instanceof SQLRecoverableException || e instanceof SQLNonTransientConnectionException
				|| e instanceof SQLTransientConnectionException) {
			return true;
		}
		final String state=String.valueOf(e.getSQLState());
		return state.startsWith(CONNECTION_FAILURE_CLASS) || CONNECTION_FAILURE_STATES.contains(state);
	}

	/**
	 * Tells the pool of this backend that the database dropped a connection, so that the ones it still holds from
	 * before the drop are validated on their next borrow instead of being trusted for the rest of the alive window.
	 * A dropped connection is rarely alone: a restart, a failover or a network that went away takes every
	 * connection established before it, and the pool has no other way of hearing about any of them.
	 */
	private void distrustPool() {
		CachedConnection.distrustPool(config.getDBDirectory());
	}

	/** Returns the randomized delay before the given attempt is replayed, doubling with each attempt up to a cap. */
	static long retryDelayMillis(int attempt) {
		final double bound=Math.min(MAX_SLEEP_ON_RETRY_MS, BASE_SLEEP_ON_RETRY_MS * (1 << Math.min(attempt-1, 5)));
		return (long) (Math.random() * bound);
	}

	/**
	 * Returns whether the given failure carries a transaction conflict that replaying the operation can resolve.
	 * <p>
	 * The conflict is looked up along every chain of the failure, for the reason {@link #isConnectionFailure} walks
	 * them all: it reaches this class wrapped - a deadlock in {@code put} arrives as
	 * {@code StorageRuntimeException(SQLException)}, and a caller such as {@code EntryContainer.addEntry} may wrap it
	 * once more - and a driver reports the error that says what happened as the next exception of a generic one at
	 * least as often as it reports it as the cause.
	 * <p>
	 * The standard class 40 states carry the conflict of most engines - 40P01 for PostgreSQL, 40001 for SQL Server
	 * and for MySQL, whose driver replaces the server side HY000 of a deadlock and of a lock wait timeout with
	 * 40001 - but not of all of them, so the vendor error numbers are consulted as well, keyed by the driver in the
	 * same way {@code getTableDialect} keys the column types. They cannot be matched driver-independently: Oracle
	 * reports a deadlock as ORA-00060 with SQLState 61000, and gives 1205 to a fatal "not a data file" error that
	 * no replay can resolve, while 1205 is exactly the deadlock victim of SQL Server. The SQL Server number is
	 * matched beyond its class 40 state because a deployment may add {@code xopenStates=true} to its connection
	 * URL, which reports the same deadlock as 42000. MySQL needs no number of its own, since its driver has already
	 * mapped both conditions into class 40; see {@link #NON_REPLAYABLE_ROLLBACK_STATES} for the two class 40 states
	 * that are excluded from that match.
	 */
	static boolean isRetryableConflict(Throwable t, String driver) {
		// without the suppressed exceptions, unlike isConnectionFailure(): a conflict is replayed whichever phase
		// reported it, on the strength of the engine having rolled the transaction back before it answered - and
		// the release of the connection runs after the outcome was decided and cannot make that claim. A class 40
		// raised there would otherwise replay a transaction commit() left in doubt, which is what the committing
		// guard of replayReason() exists to prevent
		return firstLinkMatching(t, WITHOUT_THE_RELEASE, e -> isConflict(e, driver))!=null;
	}

	private static boolean isConflict(SQLException e, String driver) {
		final String state=String.valueOf(e.getSQLState());
		if (state.startsWith("40") && !NON_REPLAYABLE_ROLLBACK_STATES.contains(state)) {
			return true;
		}
		final String driverName=String.valueOf(driver);
		if (driverName.contains("oracle")) {
			return e.getErrorCode()==ORACLE_DEADLOCK_DETECTED;
		} else if (driverName.contains("microsoft")) {
			return e.getErrorCode()==MSSQL_DEADLOCK_VICTIM;
		}
		return false;
	}

	/**
	 * Returns the SQLState and vendor error number of the exception a replay was decided on, so that a replay can be
	 * logged without a stack trace on every attempt. That line is the only record a replay leaves, so it names the
	 * link the decision was taken on rather than the first {@link SQLException} of the failure: a write whose
	 * operation failed for its own reasons and whose release then reported a drop is replayed on the class 08
	 * suppressed into it, and naming the state of the rejected statement instead would describe a replay that did
	 * not happen. Falls back to the first SQLException of the failure, and to the failure itself where it carries
	 * none.
	 */
	static String conflictSummary(Throwable failure, String driver) {
		// asked in the order replayReason() asks it, and of the same chains, so that the line names the link the
		// decision was taken on rather than one that merely resembles it
		SQLException named=firstLinkMatching(failure, WITHOUT_THE_RELEASE, e -> isConflict(e, driver));
		if (named==null) {
			named=firstLinkMatching(failure, WITH_THE_RELEASE, JDBCStorage::saysTheConnectionIsGone);
		}
		if (named==null) {
			// without the release, so that the line names the statement that failed rather than the rollback
			// behind it: this is the fallback of a replay decided on isClosed(con) alone, where neither chain
			// carries a verdict, and the walk reaches the suppressed exceptions before the cause
			named=firstLinkMatching(failure, WITHOUT_THE_RELEASE, e -> true);
		}
		return named==null
			? String.valueOf(failure)
			: "SQLState "+named.getSQLState()+", error "+named.getErrorCode()+": "+named.getMessage();
	}

	static final byte[] NULL=new byte[]{(byte)0};

	static byte[] real2db(byte[] real) {
		return real.length==0?NULL:real;
	}
	static byte[] db2real(byte[] db) {
		return Arrays.equals(NULL,db)?new byte[0]:db;
	}

	final LoadingCache<ByteBuffer,String> key2hash = Caffeine.newBuilder()
		.softValues()
		.build(key -> {
			try {
				final MessageDigest md = MessageDigest.getInstance("SHA-512");
				final byte[] messageDigest = md.digest(key.array());
				final StringBuilder hashtext = new StringBuilder(128);
				for (byte b : messageDigest) {
					String hex = Integer.toHexString(0xff & b);
					if (hex.length() == 1) hashtext.append('0');
					hashtext.append(hex);
				}
				return hashtext.toString();
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		});

	/**
	 * Returns the placeholder to compare against the {@code h} column, casting it where the driver would
	 * otherwise bind a value of the wrong type.
	 * <p>
	 * The SQL Server driver sends {@link PreparedStatement#setString} parameters as NVARCHAR, and under a SQL
	 * collation comparing the {@code char(128)} column against an NVARCHAR value converts the column instead of
	 * the value: the primary key can no longer be sought, so every statement scans the whole table rather than
	 * reading one row. The upsert runs that scan under HOLDLOCK, which range-locks the entire table instead of
	 * the single key being written - the lock footprint that lets concurrent writers deadlock (error 1205).
	 * Casting the parameter back to char keeps the comparison seekable.
	 */
	static String hashParam(Connection con) {
		return driverNameOf(con).contains("microsoft") ? "cast(? as char(128))" : "?";
	}

	class ReadableTransactionImpl implements ReadableTransaction {
		final Connection con;
		/**
		 * The class the statements of this transaction take. It follows who runs them rather than
		 * what they look like: an import issues the same select and the same upsert a client
		 * operation does, but nobody is waiting on it - and on mssql it works the table unindexed,
		 * {@code k} being a {@code varbinary(max)} that cannot be an index key - so bounding an
		 * import as an entry read fails an import that ran to the end before this bound existed.
		 * The catalog lookups of {@code openTree()} keep the operation class whoever runs them: they
		 * read a data dictionary rather than the data, so a wait there is another session's metadata
		 * lock, which is one of the waits this bound exists to end.
		 */
		final StatementBound bound;
		boolean isReadOnly=true;

		public ReadableTransactionImpl(Connection con) {
			this(con, StatementBound.OPERATION);
		}

		ReadableTransactionImpl(Connection con, StatementBound bound) {
			this.con=con;
			this.bound=bound;
		}

		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			// the non-enrolling name: a read must not put a tree this backend does not own - the
			// shared compressed schema tree of #873 - up for removal
			final String tableName=readTableName(treeName);
			try (final PreparedStatement statement=con.prepareStatement("select v from "+tableName+" where h="+hashParam(con)+" and k=?")){
				statement.setString(1,key2hash.get(ByteBuffer.wrap(key.toByteArray())));
				statement.setBytes(2,real2db(key.toByteArray()));
				return executeResultSet(statement, bound, rc -> rc.next() ? valueOfRow(rc, tableName) : null);
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		@Override
		public Cursor<ByteString, ByteString> openCursor(TreeName treeName) {
			return new CursorImpl(isReadOnly,con,treeName,bound);
		}

		/**
		 * {@inheritDoc}
		 * <p>
		 * The batches of such a cursor are bulk statements however ordinary they look: nobody is
		 * waiting on the walk, and on mssql it is not even a walk along an index - {@code k} is a
		 * {@code varbinary(max)} there, which cannot be an index key, so every batch is a scan and
		 * a sort of the table. Bounding those as entry reads aborted an export or a rebuild that
		 * ran to the end before this bound existed.
		 */
		@Override
		public Cursor<ByteString, ByteString> openBulkCursor(TreeName treeName) {
			return new CursorImpl(isReadOnly,con,treeName,StatementBound.BULK);
		}

		/**
		 * {@inheritDoc}
		 * <p>
		 * Bulk whoever asks: {@code select count(*)} is a scan of the whole table on every engine
		 * here, so what it takes follows the size of the backend rather than the work of the caller
		 * that happens to ask. Its callers are administrative either way - {@code dbtest} through
		 * {@code BackendStat}, and the counts {@code verify-index} reports - so the override costs a
		 * client operation nothing. It is not the count behind {@code NOTE_BACKEND_STARTED}: that one
		 * is {@code BackendImpl.getEntryCount()} through {@code RootContainer.getEntryCount()}, which
		 * sums {@code id2childrenCount} and never reaches this method.
		 * <p>
		 * One of the places the class of the transaction is overridden downwards, the others being
		 * {@link #openBulkCursor(TreeName)}, {@link CursorImpl#positionToLastKey()} and the DDL a
		 * write transaction issues - the {@code create table} and the three {@code create index} of
		 * {@code openTree()}, the {@code delete from} of {@code clearTree()} and the {@code drop
		 * table} of {@code deleteTree()} - which is where an operation-class transaction, the one
		 * {@code write()} runs with, can take the shared backstop of its connection off. The count
		 * is deliberately not given here: whoever audits that list has to read it off the class
		 * rather than trust a number that a later hard-coded {@code BULK} would leave stale.
		 */
		@Override
		public long getRecordCount(TreeName treeName) {
			try (final PreparedStatement statement=con.prepareStatement("select count(*) from "+readTableName(treeName))){
				return executeResultSet(statement, StatementBound.BULK, rc -> rc.next() ? rc.getLong(1) : 0);
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		@Override
		public boolean treeExists(TreeName treeName) {
			return isExistsTable(treeName);
		}

		/**
		 * Where an unqualified name of this transaction's connection resolves, asked of it once. Every
		 * lookup of a table below is narrowed to it - the reason is in {@link TableScope} - and asking
		 * per lookup would cost a round trip per tree of the backend on every open: pgjdbc answers both
		 * halves of it with a select of its own. A transaction holds one connection for the whole of its
		 * life, so one answer serves it all.
		 */
		// not private: a private member is not inherited, and the writeable transaction below asks
		// for the scope of its own lookups through it
		TableScope tableScope;

		TableScope takeTableScope() {
			// a connection that would not answer is asked again rather than latched: what it says
			// decides a create and a drop, and one refused question would otherwise leave every lookup
			// of this transaction as wide as the whole server. Only the first refusal of a transaction
			// is reported: the ones behind it are the same connection saying the same thing, once per
			// tree of the backend
			if (tableScope==null || !tableScope.answered) {
				tableScope=TableScope.of(JDBCStorage.this, con, tableScope==null);
			}
			return tableScope;
		}

		// Readable, not writeable: the caller that asks about a tree this backend does not own is
		// the compressed schema migration (#873), which probes the shared tree from the writeable
		// transaction of RootContainer.open() but must not create or enrol it. Answering that from
		// the readable transaction keeps the probe available to every reader, and costs nothing:
		// the writeable one inherits it.
		// The name it asks about is the non-enrolling one for that same reason, and the question is
		// narrowed to where an unqualified name of this connection resolves, like every other table
		// lookup of this class: see isExistsTable(Connection, TableScope, String).
		boolean isExistsTable(TreeName treeName) {
			return JDBCStorage.this.isExistsTable(con, takeTableScope(), readTableName(treeName));
		}
	}
	/**
	 * A transaction able to write, unless the storage was opened read-only: then it may open an existing tree and
	 * read it, and every mutating operation throws {@link ReadOnlyStorageException} instead.
	 * <p>
	 * The mode is checked per operation rather than refused here, because {@code RootContainer.open(AccessMode)}
	 * asks for a write transaction even in read-only mode - that is where it opens the compressed schema and the
	 * entry containers - so refusing to hand one out failed the offline {@code export-ldif}, {@code verify-index}
	 * and {@code backendstat} before they read anything (#874). Both other storages of this server already have
	 * this shape: {@code PDBStorage.ReadOnlyStorageImpl} and {@code CASStorage.TransactionImpl.checkReadOnly()}.
	 */
	private final class WriteableTransactionTransactionImpl extends ReadableTransactionImpl implements WriteableTransaction {

		// Shared by every table this transaction stamps: opening a backend opens all its trees,
		// and each stamp of its own connection would be a physical connect of its own. Closed by
		// write() (and by ImporterImpl.close()) when the transaction is done with.
		final StampSession stampSession=new StampSession();

		// The connection the catalog rows of this transaction are written on, opened at the first
		// row there is to write and closed with the transaction, like the stamp session above.
		final CatalogSession catalogSession=new CatalogSession();

		/**
		 * Whether this transaction has committed part of its own work, which takes the attempt out of the
		 * replay of {@link JDBCStorage#write}: what it did no longer rolls back as a whole, and a
		 * {@link WriteOperation} is only idempotent in the database.
		 * <p>
		 * Raised by {@link #commitStatement} alone, which is what every statement of this transaction that
		 * commits goes through - never once for a method that may issue one: a catalog read deciding that the
		 * statement is not needed commits nothing, and a transaction the engine rolled back whole is still worth
		 * replaying. Which side of the statement the flag goes up on is the engine's answer, see there.
		 */
		boolean partlyCommitted;

		public WriteableTransactionTransactionImpl(Connection con) {
			this(con, StatementBound.OPERATION);
		}

		WriteableTransactionTransactionImpl(Connection con, StatementBound bound) {
			super(con, bound);
			//captured once rather than read per operation: the access mode of the storage is mutable state -
			//ImporterImpl reopens the storage READ_WRITE under its caller - and a transaction has to keep the mode
			//it was created with. It also drives isReadOnly, so that a cursor this transaction opens refuses
			//delete() as well.
			isReadOnly = !accessMode.isWriteable();
		}

		void checkReadOnly() {
			if (isReadOnly) {
				throw new ReadOnlyStorageException();
			}
		}

		/**
		 * Issues a statement that ends in a commit, raising {@link #partlyCommitted} at the moment the attempt
		 * stops rolling back as a whole.
		 * <p>
		 * mysql and oracle commit before a DDL statement whether asked to or not, so there the work behind it is
		 * committed by the statement itself and the flag has to be up before it is issued: the statement that
		 * fails has committed everything before it just as surely as the one that succeeds. postgresql and sql
		 * server run DDL inside the transaction, and a DML statement commits of its own accord nowhere - one that
		 * fails there has committed nothing, {@link JDBCStorage#write} rolls the attempt back whole, and a flag
		 * raised in front of it would take a conflict the engine itself undid out of the replay. On those the
		 * flag goes up in front of the commit instead, which is the call that leaves the outcome of the
		 * transaction unknown when it fails.
		 *
		 * @param ddl whether the statement is a DDL one, which two of the four engines commit before
		 */
		private void commitStatement(String sql, boolean ddl) throws SQLException {
			partlyCommitted|=ddl && commitsBeforeDdl();
			// Bulk, whatever class the transaction itself carries: every statement issued through here is
			// one of the ones #877 names as overridden downwards - the create table and the three create
			// index of openTree(), the delete from of clearTree() and the drop table of deleteTree() -
			// and nobody is waiting on any of them.
			try (final PreparedStatement statement=con.prepareStatement(sql)) {
				execute(statement, StatementBound.BULK);
				partlyCommitted=true; // a commit that fails leaves the outcome unknown, which is no more replayable
				con.commit();
			}
		}

		/** Whether this engine commits the transaction before a DDL statement whether asked to or not. */
		private boolean commitsBeforeDdl() {
			final String driverName=driverNameOf(con);
			return driverName.contains("mysql") || driverName.contains("oracle");
		}

		String getTableDialect() {
			if (driverNameOf(con).contains("oracle")) {
				return "h char(128),k raw(2000),v blob,primary key(h,k)";
			}else if (driverNameOf(con).contains("mysql")) {
				return "h char(128),k varbinary(255),v longblob,primary key(h,k)";
			}else if (driverNameOf(con).contains("microsoft")) {
				return "h char(128),k varbinary(max),v image,primary key(h)";
			}
			return "h char(128),k bytea,v bytea,primary key(h,k)";
		}

		@Override
		public void openTree(TreeName treeName, boolean createOnDemand) {
			if (createOnDemand) {
				checkReadOnly();
				// what makes this tree nameable by a process which has opened nothing: see
				// getCatalogTree(). Written before the table and not after it, on a connection of the
				// catalog's own and committed there, so that the table is never there without a row
				// naming it - on every engine, and not only on the ones whose DDL happens to carry the
				// row along - and so that none of it commits the work of this transaction. Of the two
				// ways a half-done open can end, a catalog naming a table that is not there is the one
				// the removal is ready for - it skips such a row and says so - while a table nothing
				// names is adopted with its stale rows by the next open of that tree and is dropped by no
				// clear ever after. deleteTree() takes the row out after the drop for that same reason,
				// which is why it is not the mirror of this. It writes, so it comes after the read-only
				// check and not before it (#874)
				enrolInCatalog(treeName);
				// Every statement below is a DDL that commits, and each raises partlyCommitted through
				// commitStatement() rather than once for the method: every one of them is guarded by a
				// catalog read, so on an existing backend this method issues nothing at all. Raising the
				// flag for a catalog read that commits nothing would make the whole attempt unreplayable -
				// the conflict replay of #867 as much as the drop replay, since replayReason() reads the
				// flag before it asks anything else - and RootContainer.open() opens every tree of every
				// base DN in a single write, whose first act is one of these.
				if (!isExistsTable(treeName)) {
					try {
						commitStatement("create table "+getTableName(treeName)+" ("+getTableDialect()+")", true);
					}catch (SQLException e) {
						throw new StorageRuntimeException(e);
					}
				}
				// CursorImpl iterates with "where k>? order by k" batches: primary key (h,k) cannot serve them
				final String driverName=driverNameOf(con);
				final String tableName=getTableName(treeName);
				if (driverName.contains("postgres")) {
					try {
						// asked although postgresql has "create index if not exists": that statement commits
						// whether it creates anything or not, and this is the engine of every default
						// deployment - unguarded, it would take every write that opens a tree out of the
						// conflict replay, RootContainer.open() and its ~25 trees per suffix included
						if (!isExistsIndex(tableName,"k_"+tableName.substring("opendj_".length()))) {
							commitStatement("create index if not exists k_"+tableName.substring("opendj_".length())+" on "+tableName+" (k)", true);
						}
					}catch (SQLException e) {
						throw new StorageRuntimeException(e);
					}
				}else if (driverName.contains("mysql")) {
					try {
						if (!isExistsIndex(tableName,"k_"+tableName.substring("opendj_".length()))) { // mysql has no "create index if not exists"
							commitStatement("create index k_"+tableName.substring("opendj_".length())+" on "+tableName+" (k)", true);
						}
					}catch (SQLException e) {
						throw new StorageRuntimeException(e);
					}
				}else if (driverName.contains("oracle")) {
					try {
						// oracle has no "create index if not exists"; unquoted identifiers are stored in uppercase
						if (!isExistsIndex(tableName.toUpperCase(),"k_"+tableName.substring("opendj_".length()))) {
							commitStatement("create index k_"+tableName.substring("opendj_".length())+" on "+tableName+" (k)", true);
						}
					}catch (SQLException e) {
						throw new StorageRuntimeException(e);
					}
				}
				// mssql: k is varbinary(max), which cannot be an index key column - cursor batches stay unindexed there
				// the dialect is taken off this transaction's own connection: finding it out must
				// not cost a borrow from a pool this thread is already holding a connection of
				commentTable(treeName, dialectOf(con), stampSession);
			}
		}

		/**
		 * Records the tree in the catalog of this backend, creating the catalog itself along the way
		 * when this is the first tree of the storage. Enrolling is the business of
		 * openTree(createOnDemand) alone: naming a tree in order to read it must never put it up for
		 * removal, since the tree read may belong to another backend of the same database - the
		 * unqualified compressed schema trees such a database may still hold, say (#873).
		 * <p>
		 * The row is written whenever the catalog does not already record this tree at this table -
		 * and not only when the table is created - so that a backend of an installation upgraded to a
		 * version keeping a catalog fills it in at its first read-write open instead of waiting for
		 * its trees to be created again. What the catalog already records is read once, when this
		 * storage first opens it; see {@link #enrolledTrees}.
		 * <p>
		 * The row is written on a connection of the catalog's own and committed there, never on the one
		 * this transaction runs on. It has to be committed: the open which fills the catalog of a
		 * backend upgraded from a version keeping none creates no table at all, so there is nothing
		 * else of {@link #openTree} to carry those rows, and a transaction failing after them would
		 * take every one back - leaving the tables named by nothing and the next clear dropping
		 * nothing, which is #888 over again. And that commit must not be this transaction's:
		 * {@code RootContainer.open()} opens every tree of every base DN in a single write, a commit
		 * anywhere inside it takes the whole write out of the replay - {@link #replayReason} reads
		 * {@link #partlyCommitted} before it asks anything else - and a deadlock at the twentieth tree
		 * would then fail the backend open where master replayed it. A connection of its own is what
		 * gives the row a commit that is not the caller's.
		 */
		void enrolInCatalog(TreeName treeName) {
			final TreeName catalog=getCatalogTree();
			if (catalog.equals(treeName)) {
				return; // the catalog holds no row of its own: catalogTables() adds it when its table is there
			}
			if (SHARED_COMPRESSED_SCHEMA_BASE_DN.equals(treeName.getBaseDN())) {
				return; // a tree this backend may not be the only owner of: see the constant
			}
			openCatalog(catalog);
			if (enrolledTrees.contains(treeName)) {
				return; // already recorded, at the table this open would record it at
			}
			try {
				catalogSession.transaction().upsert(catalog,
					ByteString.valueOfUtf8(treeName.toString()),
					ByteString.valueOfUtf8(getTableName(treeName)));
				// committed where it is written, so that the row is there before the table on every
				// engine and not only where the "create table" below happens to carry it - and on the
				// catalog's own connection, so that this commit is none of the caller's: see above
				catalogSession.commit();
				enrolledTrees.add(treeName);
			} catch (SQLException | RuntimeException e) {
				// the unchecked one as well, exactly as unenrolFromCatalog() takes it: upsert() answers a
				// failed statement with a StorageRuntimeException of its own, and what that statement left
				// behind has to be rolled back all the same. This connection outlives the row that failed
				// on it and carries every remaining tree of this open - postgres refuses every further
				// statement of a transaction whose statement failed (25P02), so a reset skipped here fails
				// the twenty-odd enrolments behind it with a cause nowhere near the one that started it
				catalogSession.reset();
				throw e instanceof StorageRuntimeException ? (StorageRuntimeException) e : new StorageRuntimeException(e);
			}
		}

		/**
		 * Makes the catalog of this backend usable, once per open of the storage: its table is created
		 * where there is none, and what it already records is read where there is one.
		 * <p>
		 * Serialized on the storage, so that two transactions opening trees at the same time cannot both
		 * find the table absent and both go on to create it. It serializes this storage and nothing
		 * else, which is why the create tolerates a table that turned up while it was being made: an
		 * offline tool beside a running server is a pair no lock of one process can order. The lock is
		 * taken by an {@code openTree} that finds the flag already up only to read it, and the stamp -
		 * the one thing under it that is nobody's dependency - is issued outside it.
		 */
		void openCatalog(TreeName catalog) {
			synchronized (catalogLock) {
				if (catalogTableOpened) {
					return;
				}
				if (isExistsTable(catalog)) {
					readEnrolledTrees(catalog);
				} else {
					createCatalogTable(catalog);
					// nothing to read from a table that has just been created, and nothing this open
					// enrols may be skipped as already recorded
				}
				catalogTableOpened=true;
			}
			// stamped with its tree name like any table of a tree (#866), and for a reason of its own: a
			// clear reports what it did not drop, and the catalog of a backend sharing this database
			// (#873) is the one table such a report could otherwise attribute to nobody. It costs one
			// stamp per open of the storage, not one per tree - the flag above is what keeps it to one -
			// and it is issued outside the lock: it is a diagnostic aid on a session and a bound of its
			// own, with no business holding up every openTree of this storage
			commentTable(catalog, dialectOf(con), stampSession);
		}

		/**
		 * Reads what the catalog already records, so that the trees it names are not enrolled again on
		 * an open which would write the rows that are already there; see {@link #enrolledTrees}. Run
		 * once per open of the storage, behind the very flag that keeps the catalog from being opened
		 * again, and it costs the one select a clear pays for anyway.
		 * <p>
		 * Read on the catalog's own connection and committed there, so that no transaction of a caller
		 * ever touches the catalog table. A select of the caller's transaction would hold a lock on it
		 * until that transaction ended - the whole of {@code RootContainer.open()} - and the rows this
		 * read decides are written on the catalog's connection: a clear of this backend queueing for the
		 * table in between would then be waiting for the caller while the caller waited for it, a pair
		 * of sessions no deadlock detector of the database can see, one of them being blocked inside
		 * this process rather than in the server. {@link #removeStorageFiles()} and {@link #listTrees()}
		 * read that table on a connection of their own, which is the same argument read the other way:
		 * neither is inside a transaction of a caller, and both are done with it when they commit.
		 */
		void readEnrolledTrees(TreeName catalog) {
			try {
				final Connection catalogCon=catalogSession.connection();
				for (final Map.Entry<TreeName,String> row : readCatalogRows(catalogCon, getTableName(catalog)).entrySet()) {
					// a row recording another table than this version would record is not the row this
					// open would leave behind: a removal drops the table the row records, so such a row is
					// rewritten - and committed - exactly like one that is not there at all. Asked through
					// the non-enrolling name of #881: reading what the catalog records is not taking an
					// interest in the tree it names, and a row this decides not to trust must not have put
					// its tree in the memo of the trees this backend names its tables for
					if (readTableName(row.getKey()).equals(row.getValue())) {
						enrolledTrees.add(row.getKey());
					}
				}
				catalogCon.commit(); // the read ends here and holds nothing of the catalog after it
			} catch (SQLException | RuntimeException e) {
				// the unchecked one as well, for the reason enrolInCatalog() takes it: this connection is
				// the one every enrolment of this open goes on to write its row on
				catalogSession.reset();
				throw e instanceof StorageRuntimeException ? (StorageRuntimeException) e : new StorageRuntimeException(e);
			}
		}

		/**
		 * Creates the table of the catalog, on the catalog's own connection for the reason its rows are
		 * written there - see {@link #enrolInCatalog}. It takes no index of the kind openTree() gives a
		 * tree: the catalog is read whole and written by key, never iterated by key range, so the index
		 * a cursor needs would serve nothing here. The stamp it does take is given by the caller, on
		 * every open rather than on creation alone.
		 * <p>
		 * A read-write open of a JDBC backend needs the privilege to create this table, where a version
		 * keeping no catalog issued no DDL at all on an installation whose tables were already there.
		 * An account that may write its rows but not create a table is a configuration this can meet,
		 * so the failure says which table it was and why the backend wanted it, rather than reaching
		 * the operator as a bare SQL error inside ERR_OPEN_ENV_FAIL.
		 */
		void createCatalogTable(TreeName catalog) {
			final String tableName=getTableName(catalog);
			try {
				final Connection catalogCon=catalogSession.connection();
				try (final PreparedStatement statement=catalogCon.prepareStatement("create table "+tableName+" ("+getTableDialect()+")")) {
					// bulk like every other create table of this backend (#882): it is DDL nobody waits on,
					// and the class of a client operation is not what a statement of this kind can be given
					execute(statement, StatementBound.BULK);
				}
				catalogCon.commit();
			} catch (SQLException | RuntimeException e) {
				// the unchecked one as well, for the reason enrolInCatalog() takes it: what the statement
				// left behind has to be rolled back whatever class the failure arrived in, this connection
				// being the one the rows of this open are written on
				catalogSession.reset();
				// a table that turned up between the lookup and this statement is what was wanted, whoever
				// made it: the lock this runs under orders the transactions of one storage, and an offline
				// tool beside a running server - the pair #888 is about - is ordered by nothing at all
				if (isExistsTable(catalog)) {
					logger.debug(LocalizableMessage.raw("jdbc: table %s was created by another session while this one was creating it: %s",
						tableName, stackTraceToSingleLineString(e)));
					return;
				}
				throw new StorageRuntimeException("jdbc: backend "+config.getBackendId()+" could not create table "
					+tableName+", which holds the catalog naming the trees it owns: a read-write open of a JDBC"
					+" backend needs the privilege to create it, and a clear of one names nothing without it", e);
			}
		}

		/**
		 * Takes the tree out of the catalog: a row is what puts a table up for removal, and this one is
		 * gone. Written and committed on the catalog's own connection, like the enrolment - see {@link
		 * #enrolInCatalog} - which is what keeps the caller's transaction from being able to roll it
		 * back over a table that is already dropped.
		 */
		void unenrolFromCatalog(TreeName treeName, boolean enrolled) {
			final TreeName catalog=getCatalogTree();
			if (catalog.equals(treeName)) {
				catalogTableOpened=false; // its own table is gone: the next enrolment creates it again
				enrolledTrees.clear(); // and records every tree anew, this one having recorded nothing
				return;
			}
			if (SHARED_COMPRESSED_SCHEMA_BASE_DN.equals(treeName.getBaseDN())) {
				// the symmetry of enrolInCatalog() and nothing more: no row of this pair was ever written,
				// so the delete would find none. What keeps the pair out of a clear is that a clear drops
				// what the catalog names and the catalog does not name them; see the constant
				return;
			}
			// taken out of what this storage knows the catalog records whether the delete below is
			// issued or not: a tree the catalog does not name is not one an enrolment may skip
			enrolledTrees.remove(treeName);
			if (!enrolled) {
				return;
			}
			try {
				// deleteRow() and not delete(): the read-only check belongs to the caller of deleteTree,
				// which made it, and the transaction this row is written through is one of this class's own
				catalogSession.transaction().deleteRow(catalog, ByteString.valueOfUtf8(treeName.toString()));
				catalogSession.commit();
			} catch (SQLException | RuntimeException e) {
				// the unchecked one as well: deleteRow() answers a failed statement with a
				// StorageRuntimeException, and what that statement left behind has to be rolled back all
				// the same - this connection outlives the row that failed on it
				catalogSession.reset();
				throw e instanceof StorageRuntimeException ? (StorageRuntimeException) e : new StorageRuntimeException(e);
			}
		}

		/** Whether a delete of this tree has a row of the catalog to take out; see {@link #unenrolFromCatalog}. */
		boolean isEnrolledTree(TreeName treeName) {
			if (getCatalogTree().equals(treeName) || SHARED_COMPRESSED_SCHEMA_BASE_DN.equals(treeName.getBaseDN())) {
				return false;
			}
			return catalogTableOpened || isExistsTable(getCatalogTree());
		}

		/**
		 * Whether the table already carries the index of this name, asked where the table itself is
		 * asked for - see {@link TableScope}. A table name carries no backend id and no database, so two
		 * databases of one server hold identical table <em>and</em> index names, and Connector/J 8 binds
		 * no schema predicate for a null catalog: a neighbouring database answering here would skip the
		 * create index of this one for good, leaving every "where k>? order by k" batch of every cursor
		 * a full scan behind it.
		 */
		boolean isExistsIndex(String tableName, String indexName) throws SQLException {
			final TableScope scope=takeTableScope();
			// the index lookup takes the operation bound of #882 like every other catalog read of this
			// class: it asks a data dictionary rather than the data, so a wait here is the metadata lock
			// of another session - and it is narrowed to the scope every table lookup here is narrowed to
			return bounded(con, StatementBound.OPERATION, () -> {
				// approximate=true: with false the oracle driver runs ANALYZE on every call
				try (final ResultSet rs = con.getMetaData().getIndexInfo(scope.catalog, null, tableName, false, true)) {
					while (rs.next()) {
						if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME")) && scope.covers(rs)) {
							return true;
						}
					}
				}
				return false;
			});
		}
		
		public void clearTree(TreeName treeName) {
			checkReadOnly();
			try { // the commit takes the attempt out of the replay: it commits the delete, and everything before it
				commitStatement("delete from "+getTableName(treeName), false);
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		@Override
		public void deleteTree(TreeName treeName) {
			checkReadOnly();
			// The row is taken out on the catalog's own connection rather than left to this transaction:
			// that transaction is the last thing the delete could still be rolled back by - write()
			// replays a class 40 conflict and rethrows everything else unreplayed - and the row would be
			// rolled back over a table that is already gone, with nothing ever to put it right: a deleted
			// tree is not opened again, so no enrolment and no unenrolment reaches it a second time. It
			// holds for the branch where there is no table to drop as much as for the one where the drop
			// commits of its own accord.
			// That connection is opened here, before anything is dropped: a connect this backend cannot
			// make costs nothing at this point, where one failing after the drop would leave exactly the
			// half-done state the sentence above is about.
			final boolean enrolled=isEnrolledTree(treeName);
			if (enrolled) {
				try {
					catalogSession.connection();
				} catch (SQLException e) {
					throw new StorageRuntimeException(e);
				}
			}
			// The table dropped is the one the tree names, where a clear drops the one its row records.
			// The two are the same table by the time anything is deleted: a row recording another one is
			// not taken as an enrolment - readEnrolledTrees() keeps it out of enrolledTrees - so the
			// openTree that every delete of a tree comes after has rewritten it to this name.
			// A row is written before its table is created and taken out after its table is dropped,
			// never the other way round: of the two ways a half-done change can end, a catalog naming a
			// table that is not there is the one the removal is ready for - it skips such a row and says
			// so - while a table nothing names is adopted with its stale rows by the next open of that
			// tree and is dropped by no clear ever after. So this is deliberately not the mirror of
			// openTree(): an unenrolment left pending before the drop would be committed by the drop
			// itself on mysql and oracle, where DDL commits the transaction it finds open before it
			// executes, and would then stand even where the drop goes on to fail - ORA-00054 on a tree
			// another session holds, say, which write() does not replay, it being neither a class 40
			// state nor ORA-00060.
			if (isExistsTable(treeName)) {
				try {
					commitStatement("drop table " + getTableName(treeName), true);
				} catch (SQLException e) {
					throw new StorageRuntimeException(e);
				}
			}
			unenrolFromCatalog(treeName, enrolled);
			// the memoized table name of a tree nothing holds any more is of no use to anyone
			tree2table.invalidate(treeName);
			unstampableTrees.remove(treeName); // a table recreated later deserves a fresh stamp attempt
		}

		@Override
		public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
			checkReadOnly();
			try {
				upsert(treeName, key, value);
			} catch (SQLException e) {
				//StorageRuntimeException, like read() and delete(): EntryContainer passes that type through unchanged,
				//while any other runtime exception is turned into an opaque ERR_UNCHECKED_EXCEPTION before it can be
				//classified as a conflict
				throw new StorageRuntimeException(e);
			}
		}

		boolean upsert(TreeName treeName, ByteSequence key, ByteSequence value) throws SQLException {
			final String driverName=driverNameOf(con);
			if (driverName.contains("postgres")) { //postgres upsert
				try (final PreparedStatement statement = con.prepareStatement("insert into " + getTableName(treeName) + " (h,k,v) values (?,?,?) ON CONFLICT (h, k) DO UPDATE set v=excluded.v")) {
					statement.setString(1, key2hash.get(ByteBuffer.wrap(key.toByteArray())));
					statement.setBytes(2, real2db(key.toByteArray()));
					statement.setBytes(3, value.toByteArray());
					return (execute(statement, bound) == 1 && statement.getUpdateCount() > 0);
				}
			}else if (driverName.contains("mysql")) { //mysql upsert
				try (final PreparedStatement statement = con.prepareStatement("insert into " + getTableName(treeName) + " (h,k,v) values (?,?,?) as new ON DUPLICATE KEY UPDATE v=new.v")) {
					statement.setString(1, key2hash.get(ByteBuffer.wrap(key.toByteArray())));
					statement.setBytes(2, real2db(key.toByteArray()));
					statement.setBytes(3, value.toByteArray());
					return (execute(statement, bound) == 1 && statement.getUpdateCount() > 0);
				}
			}else if (driverName.contains("oracle")) { //ANSI MERGE without ;
				try (final PreparedStatement statement = con.prepareStatement("merge into " + getTableName(treeName) + " old using (select ? h,? k,? v from dual) new on (old.h=new.h and old.k=new.k) WHEN MATCHED THEN UPDATE SET old.v=new.v WHEN NOT MATCHED THEN INSERT (h,k,v) VALUES (new.h,new.k,new.v)")) {
					statement.setString(1, key2hash.get(ByteBuffer.wrap(key.toByteArray())));
					statement.setBytes(2, real2db(key.toByteArray()));
					statement.setBytes(3, value.toByteArray());
					return (execute(statement, bound) == 1 && statement.getUpdateCount() > 0);
				}
			}else if (driverName.contains("microsoft")) { //ANSI MERGE with ; WITH (HOLDLOCK) makes the upsert atomic: without it SQL Server MERGE can race two concurrent NOT MATCHED inserts of the same key into a PRIMARY KEY violation. UPDLOCK is required on top of it: with HOLDLOCK alone the search phase takes a shared lock that the WHEN MATCHED update then has to convert to an exclusive one, so two concurrent upserts of the same key deadlock on the conversion; an update lock is taken right away and makes the second transaction wait instead. h is cast back to char so that the join can seek the primary key instead of scanning the whole table under those locks, see hashParam()
				try (final PreparedStatement statement = con.prepareStatement("merge into " + getTableName(treeName) + " WITH (HOLDLOCK, UPDLOCK) old using (select cast(? as char(128)) h,? k,? v) new on (old.h=new.h and old.k=new.k) WHEN MATCHED THEN UPDATE SET old.v=new.v WHEN NOT MATCHED THEN INSERT (h,k,v) VALUES (new.h,new.k,new.v);")) {
					statement.setString(1, key2hash.get(ByteBuffer.wrap(key.toByteArray())));
					statement.setBytes(2, real2db(key.toByteArray()));
					statement.setBytes(3, value.toByteArray());
					return (execute(statement, bound) == 1 && statement.getUpdateCount() > 0);
				}
			}else { //ANSI SQL: try update before insert with not exists
				return update(treeName,key,value) || insert(treeName,key,value);
			}
		}

		boolean insert(TreeName treeName, ByteSequence key, ByteSequence value) throws SQLException {
			try (final PreparedStatement statement = con.prepareStatement("insert into " + getTableName(treeName) + " (h,k,v) select ?,?,? where not exists (select 1 from "+getTableName(treeName)+" where  h=? and k=? )")) {
				statement.setString(1, key2hash.get(ByteBuffer.wrap(key.toByteArray())));
				statement.setBytes(2, real2db(key.toByteArray()));
				statement.setBytes(3, value.toByteArray());
				statement.setString(4, key2hash.get(ByteBuffer.wrap(key.toByteArray())));
				statement.setBytes(5, real2db(key.toByteArray()));
				return (execute(statement, bound)==1 && statement.getUpdateCount()>0);
			}
		}

		boolean update(TreeName treeName, ByteSequence key, ByteSequence value) throws SQLException {
			try (final PreparedStatement statement=con.prepareStatement("update "+getTableName(treeName)+" set v=? where h=? and k=?")){
				statement.setBytes(1,value.toByteArray());
				statement.setString(2,key2hash.get(ByteBuffer.wrap(key.toByteArray())));
				statement.setBytes(3,real2db(key.toByteArray()));
				return (execute(statement, bound)==1 && statement.getUpdateCount()>0);
			}
		}

		@Override
		public boolean update(TreeName treeName, ByteSequence key, UpdateFunction f) {
			//checked before the read, so that a read-only transaction reports the mode rather than the value it
			//computed being equal to the stored one
			checkReadOnly();
			final ByteString oldValue=read(treeName,key);
			final ByteSequence newValue=f.computeNewValue(oldValue);
			if (Objects.equals(newValue, oldValue))
			{
				return false;
			}
			if (newValue == null)
			{
				return delete(treeName, key);
			}
			put(treeName,key,newValue);
			return true;
		}

		@Override
		public boolean delete(TreeName treeName, ByteSequence key) {
			checkReadOnly();
			return deleteRow(treeName, key);
		}

		/**
		 * The statement of {@link #delete} without its read-only check, for the rows this class writes
		 * on a transaction of its own making: the catalog of a backend is written through a transaction
		 * over a connection of its own, whose access mode is read again as it is built, and the check
		 * that matters was made by the caller of {@code openTree} or {@code deleteTree}.
		 */
		boolean deleteRow(TreeName treeName, ByteSequence key) {
			try (final PreparedStatement statement=con.prepareStatement("delete from "+getTableName(treeName)+" where h="+hashParam(con)+" and k=?")){
				statement.setString(1,key2hash.get(ByteBuffer.wrap(key.toByteArray())));
				statement.setBytes(2,real2db(key.toByteArray()));
				return (execute(statement, bound)==1 && statement.getUpdateCount()>0);
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}
	}
	
	static int compareKeys(byte[] key1, byte[] key2) {
		return ByteString.wrap(key1).compareTo(key2, 0, key2.length);
	}

	// Iterates in batches via keyset pagination ("where k>? order by k limit n"):
	// scrollable ResultSet is not an option, the postgres/mysql drivers materialize it entirely in memory.
	// Batches start at "fetchsize.initial" and grow geometrically to "fetchsize" while the reads stay
	// sequential: most cursors read only a few rows, and eagerly fetching the maximum made every
	// repositioning transfer "fetchsize" rows over the network (#860).
	final class CursorImpl implements Cursor<ByteString, ByteString> {
		final Connection con;
		final TreeName treeName;
		final String tableName;
		// the enrolling name, resolved once and only if this cursor ever deletes
		String writeTableName;
		final boolean isReadOnly;
		final int batchSize=Math.max(1,Integer.getInteger("org.openidentityplatform.opendj.jdbc.fetchsize",1000));
		final int initialBatchSize=Math.min(batchSize,Math.max(1,Integer.getInteger("org.openidentityplatform.opendj.jdbc.fetchsize.initial",32)));
		int nextBatchSize=initialBatchSize;
		long fetchCount;
		final String limitClause;

		final ArrayDeque<byte[][]> buffer=new ArrayDeque<>();
		byte[] currentKeyDb;
		ByteString currentKey;
		ByteString currentValue;
		boolean defined;

		// The class of the statements this cursor issues, from whoever opened it: a search walks its
		// index and has a client waiting, while an import, an export or a rebuild walks a whole tree
		// with nobody waiting - and on mssql it walks it unindexed either way. It is not read off the
		// shape of the statement, because the opening batch of every cursor is the same
		// unconditioned "order by k" that positionToLastKey() issues, search or not.
		final StatementBound batchBound;

		public CursorImpl(boolean isReadOnly, Connection con, TreeName treeName, StatementBound batchBound) {
			this.isReadOnly=isReadOnly;
			this.con=con;
			this.treeName=treeName;
			// the read statements below take the non-enrolling name: a cursor is how the migration
			// of #873 reads the shared tree, and reading a tree must not put it up for removal
			this.tableName=readTableName(treeName);
			this.batchBound=batchBound;
			this.limitClause=((CachedConnection)con).parent.getClass().getName().contains("mysql")
				? " limit ?,?" : " offset ? rows fetch next ? rows only";
		}

		int adaptiveBatchSize() {
			final int size=nextBatchSize;
			nextBatchSize=Math.min(batchSize,size*4);
			return size;
		}

		/**
		 * Reads one batch of the cursor. The class of the bound is the caller's: a batch taken
		 * along the index of the tree for a client is an operation, while a batch that has to look
		 * at the whole table to answer - the one behind {@link #positionToLastKey()} - and every
		 * batch of a cursor an import or a rebuild walks ({@link #batchBound}) is bulk work, and
		 * the two cannot share a value.
		 */
		boolean fetchBatch(String condition, byte[] dbKey, long offset, boolean descending, int limit, StatementBound bound) {
			fetchCount++;
			buffer.clear();
			try (final PreparedStatement statement=con.prepareStatement("select k,v from "+tableName
					+(condition!=null?" where k"+condition+"?":"")
					+" order by k"+(descending?" desc":"")+limitClause)){
				int i=1;
				if (condition!=null) {
					statement.setBytes(i++,dbKey);
				}
				statement.setLong(i++,offset);
				statement.setLong(i,limit);
				return executeResultSet(statement, bound, rc -> {
					while (rc.next()) {
						buffer.add(new byte[][]{rc.getBytes(1),valueOfRow(rc.getBytes(2),tableName)});
					}
					return !buffer.isEmpty();
				});
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		void advanceFromBuffer() {
			final byte[][] row=buffer.poll();
			currentKeyDb=row[0];
			currentKey=ByteString.wrap(db2real(row[0]));
			currentValue=ByteString.wrap(row[1]);
			defined=true;
		}

		@Override
		public boolean next() {
			if (buffer.isEmpty() && !fetchBatch(currentKeyDb==null?null:">",currentKeyDb,0,false,adaptiveBatchSize(),batchBound)) {
				defined=false;
				return false;
			}
			advanceFromBuffer();
			return true;
		}

		@Override
		public boolean isDefined() {
			return defined;
		}

		@Override
		public ByteString getKey() throws NoSuchElementException {
			if (!defined) {
				throw new NoSuchElementException();
			}
			return currentKey;
		}

		@Override
		public ByteString getValue() throws NoSuchElementException {
			if (!defined) {
				throw new NoSuchElementException();
			}
			return currentValue;
		}

		@Override
		public void delete() throws NoSuchElementException, UnsupportedOperationException {
			if (!defined) {
				throw new NoSuchElementException();
			}
			if (isReadOnly) {
				throw new UnsupportedOperationException();
			}
			if (writeTableName==null) {
				// the enrolling name, unlike the read statements above: this writes to the tree, so it is
				// one this backend owns and its table belongs in the memo of the storage. What a clear
				// drops is what the catalog of the backend names (#888), and openTree(name, true) is the
				// one thing that writes there - a tree written through a cursor is one the backend opened
				// to get the cursor, which is where its row comes from
				writeTableName=getTableName(treeName);
			}
			try (final PreparedStatement statement=con.prepareStatement("delete from "+writeTableName+" where h="+hashParam(con)+" and k=?")){
				statement.setString(1,key2hash.get(ByteBuffer.wrap(db2real(currentKeyDb))));
				statement.setBytes(2,currentKeyDb);
				execute(statement, batchBound);
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		@Override
		public void close() {
			buffer.clear();
			defined=false;
		}

		@Override
		public boolean positionToKeyOrNext(ByteSequence key) {
			final byte[] target=real2db(key.toByteArray());
			// Forward repositioning within the already-fetched range is served from the buffer: buffered
			// rows are the contiguous sorted rows following the current one (byte order matches the
			// database binary collation), so the first row >= target is guaranteed to be among them.
			if (!buffer.isEmpty() && currentKeyDb!=null
					&& compareKeys(target,currentKeyDb)>0
					&& compareKeys(target,buffer.peekLast()[0])<=0) {
				while (compareKeys(buffer.peek()[0],target)<0) {
					buffer.poll();
				}
				advanceFromBuffer();
				return true;
			}
			if (!buffer.isEmpty()) { // jumped outside the buffered range: random access, back to small batches
				nextBatchSize=initialBatchSize;
			}
			if (fetchBatch(">=",target,0,false,adaptiveBatchSize(),batchBound)) {
				advanceFromBuffer();
				return true;
			}
			defined=false;
			return false;
		}

		@Override
		public boolean positionToKey(ByteSequence key) {
			final byte[] real=key.toByteArray();
			// The row is wrapped inside the handler rather than after it, so that null keeps meaning
			// "no such key" and only that: a row whose v is null - which the schema allows, however
			// this backend writes it - has to fail here as it fails in read(), rather than report a
			// key that exists as absent. Both go through valueOfRow(), which is where that failure
			// is named.
			final ByteString value;
			try (final PreparedStatement statement=con.prepareStatement("select v from "+tableName+" where h="+hashParam(con)+" and k=?")){
				statement.setString(1,key2hash.get(ByteBuffer.wrap(real)));
				statement.setBytes(2,real2db(real));
				value=executeResultSet(statement, batchBound, rc -> rc.next() ? valueOfRow(rc, tableName) : null);
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
			if (value!=null) {
				buffer.clear();
				nextBatchSize=initialBatchSize;
				currentKeyDb=real2db(real);
				currentKey=ByteString.wrap(real);
				currentValue=value;
				defined=true;
				return true;
			}
			defined=false;
			return false;
		}

		/**
		 * Bulk, not operation: with no condition to seek on, this is {@code order by k desc} over
		 * the whole table - and on mssql, where {@code k} is a {@code varbinary(max)} that cannot
		 * be an index key, a scan and a sort of it. It is also not on a search path: every open of
		 * a backend runs it once per base DN, through {@code EntryContainer.getHighestEntryID()},
		 * outside the try/catch of {@code BackendImpl.openBackend()} - a bound of two minutes here
		 * would turn a large backend that opens slowly into one that does not open at all.
		 */
		@Override
		public boolean positionToLastKey() {
			if (fetchBatch(null,null,0,true,1,StatementBound.BULK)) {
				advanceFromBuffer();
				return true;
			}
			defined=false;
			return false;
		}

		/**
		 * The class of the cursor, unlike {@link #positionToLastKey()}, which is bulk however it
		 * was opened: an offset comes from the VLV request of a client, so this runs on a search
		 * path and has to give the worker thread back - an import has no VLV position to seek to.
		 * That a deep offset is served by walking to it - the engines have no other way to answer
		 * an {@code offset ?} - is what makes the bound reachable here, and reaching it answers the
		 * request with an error rather than parking a thread of the server on it.
		 */
		@Override
		public boolean positionToIndex(int index) {
			if (!buffer.isEmpty()) { // absolute jump: random access, back to small batches
				nextBatchSize=initialBatchSize;
			}
			if (index>=0 && fetchBatch(null,null,index,false,adaptiveBatchSize(),batchBound)) {
				advanceFromBuffer();
				return true;
			}
			defined=false;
			return false;
		}
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * Answered from the catalog of the backend rather than from the trees this process happens to
	 * have touched: {@link #removeStorageFiles()} runs before anything has touched one (#888).
	 * <p>
	 * What a tool has to be shown is not what a clear may drop: the shared compressed schema trees
	 * are deliberately not enrolled - a backend must not offer a tree another one may own for removal
	 * - and would go unnamed by {@code dbtest} for it, so they are added here when their tables are
	 * there. {@link #catalogTables(Connection, TableScope)} is what the removal reads, and it
	 * names them not.
	 * <p>
	 * The catalog itself is among the names, being a tree of this backend like any other: {@code
	 * dbtest list-raw-dbs} counts it and {@code dump-raw-db} resolves its name, which is the one way
	 * of seeing from outside the server what a clear of this backend would drop.
	 */
	@Override
	public Set<TreeName> listTrees() {
		// validated, like the borrows of open() and removeStorageFiles(): since the catalog this reads
		// from, this borrow issues its statements far from itself and compensates a dropped connection
		// in no other way - a write is replayed and a read tells the pool, and this does neither, so a
		// connection dropped inside the alive window would surface out of a listing of tree names
		try (final Connection con=getValidatedConnection()) {
			return listTrees(con);
		} catch (StorageRuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new StorageRuntimeException(e);
		}
	}

	Set<TreeName> listTrees(Connection con) throws SQLException {
		final TableScope scope=TableScope.of(this, con);
		final Set<TreeName> trees=new HashSet<>(catalogTables(con, scope).keySet());
		for (final TreeName treeName : SHARED_COMPRESSED_SCHEMA_TREES) {
			// asked of the database, not assumed: the pair belongs to no backend in particular, and
			// once #881 gives each backend a pair of its own an installation may hold neither table.
			// Narrowed to this database: a pair of the same name in another database of the server
			// would otherwise have this backend name two trees it does not hold
			if (isExistsTable(con, scope, readTableName(treeName))) {
				trees.add(treeName);
			}
		}
		return trees;
	}

	/**
	 * The trees the catalog of this backend names, each with the table recorded as holding it, the
	 * catalog itself among them. Empty when the catalog table is not there - a backend which has
	 * never been opened read-write - which is what tells {@link #removeStorageFiles()} it has nothing
	 * it may drop.
	 * <p>
	 * The table name is taken from the row rather than recomputed from the tree name, so that a
	 * removal drops what was enrolled even if the naming of tables were ever to change.
	 * <p>
	 * The scope is the caller's rather than asked for here: it is not free of a round trip - pgjdbc
	 * answers both halves of it with a select of its own - and a clear and a listTrees() both narrow a
	 * lookup of their own by it, so they pass what they have instead of every reader asking twice over.
	 */
	Map<TreeName,String> catalogTables(Connection con, TableScope scope) throws SQLException {
		return catalogTables(con, scope, new ArrayList<>()); // nobody to tell: the descriptions go nowhere
	}

	/**
	 * The same, telling the caller what the read passed over: a clear accounts for every row of its
	 * catalog, and a row it could not act on is one nothing else in its report would name - the table
	 * such a row records is outside the namespace {@link #leftoverTables} scans. See {@link
	 * #reportClearOutcome}.
	 */
	Map<TreeName,String> catalogTables(Connection con, TableScope scope, List<String> skippedRows) throws SQLException {
		final TreeName catalogTree=getCatalogTree();
		final String catalogTable=getTableName(catalogTree);
		// narrowed to this database: a catalog of the same name in another database of the server
		// would send the select below at a table that is not here, failing the clear it answers
		if (!isExistsTable(con, scope, catalogTable)) {
			return Collections.emptyMap();
		}
		final Map<TreeName,String> trees=readCatalogRows(con, catalogTable, skippedRows);
		// The catalog names every tree of the backend but itself, and is put last on purpose: the
		// removal drops the trees in this order, and what names them has to outlive them. Dropping a
		// table is DDL, which mysql and oracle commit as they go, so a removal that fails halfway is
		// finished by the next attempt rather than leaving behind tables nothing names any more.
		trees.remove(catalogTree); // no row should name it; one that does must not hold back the order
		trees.put(catalogTree, catalogTable);
		return trees;
	}

	/**
	 * The rows of the catalog table as they stand, tree by tree: the caller has already established
	 * that the table is there - {@link #catalogTables} by a lookup of its own, an enrolment by having
	 * just created it or found it - so this asks the database nothing but the select.
	 * <p>
	 * A row this backend cannot have written is skipped and reported rather than trusted. What a clear
	 * drops is the table a row records, dropped by that name, so a row recording something outside the
	 * namespace this backend names its tables in points at a table that is nobody's business of this
	 * one's - and a row naming no tree at all, or naming one that is not a tree name, would otherwise
	 * fail every clear from here on rather than the one thing it describes.
	 * <p>
	 * Every row passed over is described into {@code skippedRows}, the warn above being addressed to
	 * whoever is reading the log at that moment and this to the account a clear gives of itself: such
	 * a row is a tree the clear cannot see, and neither the row nor what it records is dropped by
	 * anything. A reader with nobody to tell - a read of {@code dbtest}, or the one an enrolment makes
	 * - hands in a list of its own and lets it go, which is one allocation per read of a whole table
	 * and no convention to get wrong.
	 */
	Map<TreeName,String> readCatalogRows(Connection con, String catalogTable) throws SQLException {
		return readCatalogRows(con, catalogTable, new ArrayList<>());
	}

	Map<TreeName,String> readCatalogRows(Connection con, String catalogTable, List<String> skippedRows)
			throws SQLException {
		final Map<TreeName,String> trees=new LinkedHashMap<>();
		// the rows are read inside the bound rather than from a live ResultSet: #882 took the
		// executeResultSet() that returned one away, so a transfer cannot run with nothing bounding it
		try (final PreparedStatement statement=con.prepareStatement("select k,v from "+catalogTable)) {
			executeResultSet(statement, rs -> {
				while (rs.next()) {
					final byte[] key=rs.getBytes("k");
					if (key==null) { // no tree is named by a row with no key, and a clear must not fail over one
						logger.warn(LocalizableMessage.raw("jdbc: table %s holds a row naming no tree at all: skipped",
							catalogTable));
						skippedRows.add("a row naming no tree at all");
						continue;
					}
					final String name=new String(db2real(key), StandardCharsets.UTF_8);
					final TreeName treeName;
					try {
						treeName=TreeName.valueOf(name);
					} catch (RuntimeException e) { // reported rather than passed off as a backend with fewer trees
						logger.warn(LocalizableMessage.raw("jdbc: table %s holds \"%s\", which is not the name of a tree: skipped",
							catalogTable, name));
						skippedRows.add("\""+name+"\", which is not the name of a tree");
						continue;
					}
					final byte[] table=rs.getBytes("v");
					final String tableName=table==null || table.length==0
						? readTableName(treeName) // a row of a version which recorded the name and not the table
						: new String(table, StandardCharsets.UTF_8);
					// The prefix and not the whole of the name: the table recorded is taken from the row
					// rather than derived again so that a removal drops what was enrolled even if the naming
					// of tables were ever to change, and every naming this backend could take up is inside
					// the namespace it already scans for what a clear left standing. What the shape does have
					// to rule out is anything that is not a bare identifier: this value is read back from a
					// table and reaches a "drop table" that no driver will take a bind parameter for.
					if (!isOwnTableName(tableName)) {
						logger.warn(LocalizableMessage.raw("jdbc: table %s records tree %s at \"%s\", which is no table of this backend: skipped",
							catalogTable, treeName, tableName));
						skippedRows.add(treeName+" at \""+tableName+"\", which is no table of this backend");
						continue;
					}
					trees.put(treeName, tableName);
				}
				return null;
			});
		}
		return trees;
	}

	/**
	 * Whether a name read back from the catalog is one of this backend's tables: inside the namespace
	 * it names them in, and a bare identifier besides. A clear drops the table a row records, by that
	 * name, in a statement built by concatenation - the DDL of no engine here takes a bind parameter
	 * for it - so a row is trusted to name a table of this backend and nothing else. The existence
	 * lookup in front of the drop would answer no for most of what this rules out; it is not what
	 * makes it safe.
	 */
	static boolean isOwnTableName(String tableName) {
		if (!tableName.toLowerCase().startsWith("opendj")) {
			return false;
		}
		for (int i=0;i<tableName.length();i++) {
			final char c=tableName.charAt(i);
			if (!(c>='a' && c<='z') && !(c>='A' && c<='Z') && !(c>='0' && c<='9') && c!='_' && c!='$') {
				return false;
			}
		}
		return true;
	}

	final class ImporterImpl implements Importer {
		final Connection con;
		final ReadableTransactionImpl txr;
		final WriteableTransactionTransactionImpl txw;
		// The trees this import wrote: close() refreshes the statistics of these and only these,
		// so rebuilding a single index does not gather statistics for the whole backend. A full
		// import legitimately covers every tree - AbstractTwoPhaseImportStrategy.beforePhaseOne
		// clears them all before the first record is written - including when the import is
		// aborted, since close() runs from the try-with-resources of OnDiskMergeImporter.
		final Set<TreeName> writtenTrees = ConcurrentHashMap.newKeySet();

		// Set when the import failed or was cancelled. Its trees hold whatever the import got
		// through before it stopped - beforePhaseOne cleared them all, so that can be nothing at
		// all - and the operator is going to run it again, so there is nothing worth describing
		// to the optimizer here: on oracle gathering those statistics is a full scan per table
		// that would delay the report of a failure, or of a cancellation, by all of its duration.
		volatile boolean aborted = false;

		final Boolean isOpen;

		/**
		 * Both transactions of an import take the bulk class, and with them every statement it
		 * issues: phase one writes the trees through {@code put()}, phase two reads them back
		 * through {@code read()} and walks them through {@code openCursor()}, and none of that has
		 * a client waiting on it. Bounding those as entry reads is not merely strict, it fails work
		 * that ran to the end before this bound existed: {@code h} is the primary key on every
		 * dialect and the default lock wait is forever on mssql, postgres and oracle, so an upsert
		 * of an online import blocked by an LDAP write on the same table sat until the bound of an
		 * entry read and then failed the import.
		 */
		ImporterImpl(Connection con, boolean isOpen) {
			// An import writes by definition, so a storage that is not writeable refuses one where the
			// importer is built - which is where it was refused until the write transaction of a read-only
			// storage became one that is granted and checks per operation (#874). Left to that check, an
			// import of such a storage would take a connection out of the pool, begin its transaction and
			// fail at the first tree it clears rather than at its start.
			// What arrives here read-only is a storage that was already open: import-ldif and
			// rebuild-index both close it first, and startImport() opens a closed one READ_WRITE - an
			// import of any storage of this server reopens it that way - so those two arrive writeable.
			if (!accessMode.isWriteable()) {
				throw new ReadOnlyStorageException();
			}
			this.con=con;
			this.isOpen=isOpen;
			txr=new ReadableTransactionImpl(con, StatementBound.BULK);
			txw=new WriteableTransactionTransactionImpl(con, StatementBound.BULK);
		}
		
		@Override
		public void aborted() {
			aborted = true;
		}

		// The connection goes back whatever the commit does, and the storage this importer opened
		// is closed whatever the connection does: an importer is closed on the way out of a failed
		// import as readily as a finished one - a clearTree() that reaches the bulk bound is one
		// way there - and a commit that throws on the way would otherwise leave the connection
		// out of the pool for good, holding the transaction and the locks of that import.
		@Override
		public void close() {
			try {
				try {
					con.commit();
					if (aborted) {
						logger.debug(LocalizableMessage.raw("jdbc: import aborted: statistics of the trees it wrote are left alone"));
					}else {
						updateTableStatistics(con, writtenTrees);
					}
				} finally { // the pooled connection must be returned even when the commit or a statistics statement throws
					try {
						con.close();
					} finally {
						try {
							txw.stampSession.close();
						} finally {
							txw.catalogSession.close();
						}
					}
				}
			} catch (SQLException e) {
				throw new StorageRuntimeException(e);
			} finally {
				if (!isOpen) {
					JDBCStorage.this.close();
				}
			}
		}

		@Override
		public void clearTree(TreeName name) {
			txw.clearTree(name);
			writtenTrees.add(name);
		}

		@Override
		public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
			txw.put(treeName, key, value);
			writtenTrees.add(treeName);
		}
		
		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			return txr.read(treeName, key);
		}
		
		// Bulk like every other statement of an import, by the class of the transaction it comes
		// from: this walks a whole tree with no client waiting on it - phase one of a rebuild-index
		// reads every record of id2entry through this cursor (OnDiskMergeImporter.ID2EntrySource) -
		// and on mssql it walks it unindexed, so a batch of it is a scan and a sort of the table
		// rather than a step along an index.
		@Override
		public SequentialCursor<ByteString, ByteString> openCursor(TreeName treeName) {
			return txr.openCursor(treeName);
		}
	}
	
	//import
	@Override
	public Importer startImport() throws ConfigException, StorageRuntimeException {
		final boolean wasOpen=getStorageStatus().isWorking();
		if (!wasOpen) {
			try {
				open(AccessMode.READ_WRITE);
			}catch (Exception e) {
				throw new StorageRuntimeException(e);
			}
		}
		final Connection con;
		try {
			con=getValidatedConnection();
		}catch (Exception e){
			// and the storage this method opened goes back with it: ImporterImpl.close() is what closes
			// it again when an import opened it, and no importer is going to be built to reach that
			if (!wasOpen) {
				close();
			}
			throw new StorageRuntimeException(e);
		}
		// outside the catch: the importer of a read-only storage throws ReadOnlyStorageException,
		// which a caller tells apart from any other failure of an import
		boolean built=false;
		try {
			final Importer importer=new ImporterImpl(con, wasOpen);
			built=true;
			return importer;
		}finally {
			// and the connection borrowed above goes back on every path that does not build an
			// importer to hold it: it is the one an import keeps for its whole duration, so leaving it
			// here takes it out of the pool for good, with the transaction it had already begun. A
			// finally rather than a catch, so that it covers what a catch has to name - an Error
			// leaves the pool one connection short exactly as ReadOnlyStorageException did.
			if (!built) {
				try {
					con.close();
				}catch (SQLException ignored) {
					// the importer was never built; the failure to report is the one on its way out
				}
				// and the storage this method opened goes back with the connection, for the reason the
				// borrow above gives: ImporterImpl.close() is what closes it again when an import
				// opened it, and there is no importer here to reach that
				if (!wasOpen) {
					close();
				}
			}
		}
	}
	
	//backup
	@Override
	public boolean supportsBackupAndRestore() {
		return true;
	}

	@Override
	public void createBackup(BackupConfig backupConfig) throws DirectoryException
	{
		// TODO backup over snapshot or SQL export
		//new BackupManager(config.getBackendId()).createBackup(this, backupConfig);
	}

	@Override
	public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
	{
		new BackupManager(config.getBackendId()).removeBackup(backupDirectory, backupID);
	}

	@Override
	public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
	{
		// TODO restore over snapshot or SQL export
		//new BackupManager(config.getBackendId()).restoreBackup(this, restoreConfig);
	}

}
