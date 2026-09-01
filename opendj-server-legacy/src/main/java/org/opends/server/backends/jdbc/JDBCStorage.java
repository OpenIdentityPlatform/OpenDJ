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

	/** Number of {@link Throwable#getCause()} hops walked when classifying a failure, also a guard against a cycle. */
	private static final int MAX_CAUSE_HOPS = 16;

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

	ResultSet executeResultSet(PreparedStatement statement) throws SQLException {
		if (logger.isTraceEnabled()) {
			logger.trace(LocalizableMessage.raw("jdbc: %s",statement));
		}
		return statement.executeQuery();
	}

	int execute(PreparedStatement statement) throws SQLException {
		if (logger.isTraceEnabled()) {
			logger.trace(LocalizableMessage.raw("jdbc: %s",statement));
		}
		return statement.executeUpdate();
	}

	// unlike execute(), tolerates statements that return a result set ("analyze table" on mysql)
	void executeAny(PreparedStatement statement) throws SQLException {
		if (logger.isTraceEnabled()) {
			logger.trace(LocalizableMessage.raw("jdbc: %s",statement));
		}
		statement.execute();
	}

	Connection getConnection() throws Exception {
		return CachedConnection.getConnection(config.getDBDirectory());
	}


	AccessMode accessMode=AccessMode.READ_ONLY;
	@Override
	public void open(AccessMode accessMode) throws Exception {
		try (final Connection con=getConnection()) {
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

	final LoadingCache<TreeName,String> tree2table = Caffeine.newBuilder()
		.build(treeName -> {
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
		});

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
	 * The base DN under which the compressed schema trees are named by versions naming them from a
	 * literal. It carries no backend qualifier, so on a database addressed by several backends -
	 * which nothing forbids (#873) - that pair of trees is the same pair for all of them, and a
	 * backend must not put a tree another one may be the owner of up for removal. The pair is left
	 * where it lies on purpose (#881): it may still be the only copy a backend has. Trees named from
	 * the backend id instead are enrolled like any other.
	 */
	static final String SHARED_COMPRESSED_SCHEMA_BASE_DN="compressed_schema";

	/**
	 * The pair named under {@link #SHARED_COMPRESSED_SCHEMA_BASE_DN}, spelled out here because the
	 * names are private to {@code PersistentCompressedSchema}. They are never enrolled, so nothing
	 * but this constant can name them - and a tool asking a backend what trees it holds has to be
	 * told about them all the same, which is what {@link #listTrees()} uses this for.
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
	 * forbids (#873) - never name each other's trees.
	 */
	TreeName getCatalogTree() {
		return new TreeName(CATALOG_BASE_DN, config.getBackendId());
	}

	/**
	 * Whether the table of the catalog was created, or found, by this storage. A tree is enrolled on
	 * every open - about 25 of them for a stock suffix - and asking the catalog whether the table is
	 * there would cost a metadata round trip per tree.
	 */
	private volatile boolean catalogTableOpened=false;

	/**
	 * The trees the catalog already records at the table this version would record them at, read
	 * from it when this storage first opens it and added to as it enrols. A tree named here needs no
	 * row written for it: the row would be the one that is already there, and writing it is neither
	 * free - it is committed where it is written, so that an open which creates no table still
	 * leaves an enrolment behind - nor without cost to the caller, whose own pending work that
	 * commit carries with it. A stock suffix has about 25 trees, and every open after the first
	 * enrols none of them.
	 * <p>
	 * A row recording another table than {@link #getTableName} would give is not in here: what a
	 * removal drops is the table the row records, so a row of a version naming its tables otherwise
	 * has to be rewritten rather than trusted. Held no longer than the open it was read in, like
	 * {@link #catalogTableOpened}, and given up whenever the catalog itself is.
	 */
	private final Set<TreeName> enrolledTrees=ConcurrentHashMap.newKeySet();

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
		try (final PreparedStatement statement=con.prepareStatement("select @@sql_mode");
			final ResultSet rs=executeResultSet(statement)) {
			final String sqlMode=rs.next() ? rs.getString(1) : null;
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
				}catch (SQLException e) {
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

	// What a failed stamp says about trying again. Both chains of the failure are walked: a driver
	// reports the vendor error of a rejected statement as the next exception of a generic one at
	// least as often as it reports it as the cause, and reading only one of the two would classify
	// a lock timeout as a rejection, which leaves the tree unstamped for the life of the backend
	// over a moment of contention.
	static FailureScope failureScope(Throwable failure, Dialect dialect) {
		FailureScope scope=FailureScope.TREE;
		final Deque<Throwable> pending=new ArrayDeque<>();
		final Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
		if (failure!=null) {
			pending.push(failure);
		}
		while (!pending.isEmpty()) {
			final Throwable e=pending.pop();
			if (!seen.add(e)) { // a driver that chains an exception back to itself must not loop this walk
				continue;
			}
			if (e.getCause()!=null) {
				pending.push(e.getCause());
			}
			if (!(e instanceof SQLException)) {
				continue;
			}
			final SQLException sqlException=(SQLException) e;
			if (sqlException.getNextException()!=null) {
				pending.push(sqlException.getNextException());
			}
			final FailureScope found=scopeOf(sqlException, dialect);
			if (found==FailureScope.SESSION) { // nothing further down either chain can weaken this one
				return FailureScope.SESSION;
			}
			if (found==FailureScope.MOMENT) {
				scope=FailureScope.MOMENT;
			}
		}
		return scope;
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
			try (final ResultSet rs=executeResultSet(statement)) {
				return rs.next() ? rs.getString(1) : null;
			}
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
		final int timeoutSeconds=Math.max(0,Integer.getInteger(STATISTICS_TIMEOUT_PROPERTY,STATISTICS_TIMEOUT_SECONDS_DEFAULT));
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
					statement.setQueryTimeout(timeoutSeconds); // 0: wait without limit
					for (int i=0;i<args.length;i++) {
						statement.setString(i+1,args[i]);
					}
					if (dialect==Dialect.MYSQL) { // mysql reports analyze problems as a result row, not an SQLException
						try (final ResultSet rs=executeResultSet(statement)) {
							while (rs.next()) {
								if ("error".equalsIgnoreCase(rs.getString("Msg_type"))) {
									throw new SQLException(rs.getString("Msg_text"));
								}
							}
						}
					}else {
						executeAny(statement);
					}
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
	 * Whether a table of this name is there in the catalog and the schema given, which every caller
	 * takes off the connection it works on. Asked of the catalog by name rather than by listing every
	 * table of the database: openTree(createOnDemand) asks it for every tree of the backend - about 25
	 * of them for a stock suffix - on every open, on a database this backend may well be sharing with
	 * something else.
	 * <p>
	 * Asked with a null catalog the question spans the whole server on some drivers - Connector/J
	 * reads a null catalog as "any database" since 8.0, and its databaseTerm being CATALOG it ignores
	 * the schema pattern besides - and every answer of this method decides something that a table of
	 * the same name in another database must have no say in. A clear skips the row of a table that is
	 * gone so that it can go on, and a foreign table answering for it turns that skip into an
	 * unqualified "drop table" of a table that is not in this database, failing the clear on this
	 * attempt and on every attempt after it. An open of a tree creates its table where there is none,
	 * and a foreign table answering for it skips the creation, leaving the catalog naming a tree whose
	 * table is not here - or, for the catalog table itself, leaving the very row that names it to be
	 * written into a table this database does not hold. Two backends of the stock backend id in two
	 * databases of one server name their catalogs alike, so this is the ordinary layout and not a
	 * corner of one.
	 */
	boolean isExistsTable(Connection con, String catalog, String schema, String tableName) {
		try {
			final DatabaseMetaData metaData = con.getMetaData();
			try (final ResultSet rs = metaData.getTables(catalog, schema,
					storedIdentifier(metaData, tableName), new String[]{"TABLE"})) {
				while (rs.next()) {
					// the name still has to be compared: "_" is a single-character wildcard in a
					// metadata pattern, so "opendj_<hash>" also matches a table named "opendjX<hash>"
					if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME")) && isInScope(rs, catalog, schema)) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			throw new StorageRuntimeException(e);
		}
		return false;
	}

	/**
	 * Whether the table this row of a listing describes is in the catalog and the schema asked for.
	 * The pattern alone does not settle it: a schema reaches {@link DatabaseMetaData#getTables} as a
	 * pattern, where "_" is a single-character wildcard, so a listing narrowed to a schema named
	 * "app_data" is answered for by a schema named "appXdata" as well.
	 */
	private static boolean isInScope(ResultSet rs, String catalog, String schema) throws SQLException {
		return isSameScope(catalog, rs.getString("TABLE_CAT")) && isSameScope(schema, rs.getString("TABLE_SCHEM"));
	}

	/**
	 * Whether a name of a listing rules a table out. A name neither side gives is no narrowing:
	 * a driver naming no catalog of its own - oracle has none - must not be read as naming another.
	 */
	private static boolean isSameScope(String asked, String ofTable) {
		return asked==null || ofTable==null || ofTable.isEmpty() || asked.equalsIgnoreCase(ofTable);
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
		try (final Connection con = getConnection()) {
			// the database and the schema this connection works in, which every lookup below is
			// narrowed to: the skip in the loop decides between leaving a row where it is and dropping
			// the table it names, and a table of that name in another database of the server must not
			// be allowed to answer for this one
			final String catalog=catalogOf(con);
			final String schema=schemaOf(con);
			// the catalog names what this backend owns, and only that: listTrees() also names the
			// shared compressed schema trees, which another backend of this database may be the only
			// owner of and which a clear must therefore leave exactly where they lie (#881)
			final Map<TreeName,String> trees=catalogTables(con, catalog, schema);
			int dropped=0;
			int missing=0;
			try {
				for (final Map.Entry<TreeName,String> tree : trees.entrySet()) {
					final String tableName=tree.getValue();
					if (!isExistsTable(con, catalog, schema, tableName)) { // a row of the catalog outliving its table
						logger.warn(LocalizableMessage.raw(
							"jdbc: backend %s names tree %s, whose table %s is not there: nothing to drop for it",
							config.getBackendId(), tree.getKey(), tableName));
						missing++;
						continue;
					}
					try (final PreparedStatement statement = con.prepareStatement("drop table " + tableName)) {
						execute(statement);
					}
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
				reportClearOutcome(con, catalog, schema, dropped, missing);
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
	 * catalog - and the line says so rather than leaving it to be found out. That line is decided by
	 * what the clear itself did, so a database which would not say what is standing silences the two
	 * lines above it and not this one: the silence of #888 is the very thing being reported.
	 */
	void reportClearOutcome(Connection con, String catalog, String schema, int dropped, int missing) {
		final ClearLeftovers leftovers=leftoverTables(con, catalog, schema);
		final int ours=leftovers==null ? 0 : leftovers.ours.size();
		final int unattributed=leftovers==null ? 0 : leftovers.unattributed.size();
		final int unreadable=leftovers==null ? 0 : leftovers.unreadable.size();
		// first of the lines, and not last: on a backend upgraded in place every table of it is
		// unstamped and lands in the list below, and the operator has to be told why before being
		// handed a list of tables their own backend is very probably still using.
		// The count of trees which had lost their table is all but always zero here, and is carried for
		// the case where it is not: a catalog which names anything at all names itself last, so its own
		// table is dropped and "dropped" is one - unless the catalog table went while this clear was
		// running, which is the one way a clear can name trees and still drop nothing. Each such tree is
		// logged as the loop skips it either way; this line only sums them up.
		if (dropped==0 && (missing>0 || ours>0 || unattributed>0 || unreadable>0 || leftovers==null)) {
			logger.warn(LocalizableMessage.raw("jdbc: backend %s: the clear dropped no table at all: %d of the trees its catalog names had lost their table already, and %s. A backend upgraded from a version keeping no catalog has to be started once before its first offline \"import-ldif --clearBackend\": nothing enrols a tree before the clear runs, so that first clear finds a catalog that is not there and names nothing",
				config.getBackendId(), missing, leftovers==null
					? "what else is standing could not be read off this database"
					: String.format("%d table(s) of this backend were named by no catalog, %d could not be attributed to anyone and %d could not be read",
						ours, unattributed, unreadable)));
		}
		if (ours>0) {
			logger.warn(LocalizableMessage.raw("jdbc: backend %s: %d table(s) of %s hold trees of this backend that its catalog does not name, and the clear left them where they are: %s. A tree is enrolled as it is opened read-write and by no other means, so such a table is one of a tree of a base DN this backend still serves that was taken out of the configuration while it was disabled - an attribute index, say - or one left by a version keeping no catalog: it is this backend's own and can be removed by hand, and re-adding the tree it belongs to adopts it with the rows it still holds",
				config.getBackendId(), ours, scopeName(catalog, schema), leftovers.ours));
		}
		if (unattributed>0) {
			logger.warn(LocalizableMessage.raw("jdbc: backend %s: %d opendj table(s) of %s are named by no catalog of this backend and carry no tree stamp, so nothing says whose they are: %s. They may hold the trees of a backend sharing this database, which nothing forbids, or be leftovers of a version stamping no table at all - a table is named after the hash of its tree name and can be attributed by no other means. They were left exactly where they are",
				config.getBackendId(), unattributed, scopeName(catalog, schema), leftovers.unattributed));
		}
		if (unreadable>0) {
			logger.warn(LocalizableMessage.raw("jdbc: backend %s: the stamp of %d opendj table(s) of %s could not be read, so this clear says nothing about whose they are: %s. They were left exactly where they are",
				config.getBackendId(), unreadable, scopeName(catalog, schema), leftovers.unreadable));
		}
	}

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
	 * The "opendj" tables of the given catalog and schema that this backend can say something about,
	 * or {@code null} where the database would not list them. A table stamped with a tree this backend
	 * does not serve is in none of the lists: it is a backend sharing this database (#873) that it
	 * belongs to, and no part of this clear's outcome.
	 */
	ClearLeftovers leftoverTables(Connection con, String catalog, String schema) {
		// the shared compressed schema pair is left standing on purpose, so it is no leftover of
		// anything and reporting it would be pointing at the one thing this code goes out of its way
		// to keep. Taken out by name and not by stamp: an installation may hold the pair unstamped,
		// from a version that commented no table at all.
		final Set<String> leftOnPurpose=new HashSet<>();
		for (final TreeName treeName : SHARED_COMPRESSED_SCHEMA_TREES) {
			leftOnPurpose.add(getTableName(treeName).toLowerCase());
		}
		final ClearLeftovers leftovers=new ClearLeftovers();
		try {
			final List<String> standing=new ArrayList<>();
			final DatabaseMetaData metaData=con.getMetaData();
			try (final ResultSet rs=metaData.getTables(catalog, schema,
					storedIdentifier(metaData, "opendj%"), new String[]{"TABLE"})) {
				while (rs.next()) {
					final String tableName=rs.getString("TABLE_NAME");
					if (tableName==null) { // a row naming no table names nothing this clear can report
						continue;
					}
					if (!leftOnPurpose.contains(tableName.toLowerCase()) && isInScope(rs, catalog, schema)) {
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
	 * Whether this tree is one of this backend's own: a tree of a base DN it serves, or its own
	 * catalog. The catalog counts because a clear drops it last, so one still standing is a clear of
	 * this backend that did not get to the end, and never anything of anybody else's.
	 */
	private boolean isOwnTree(TreeName treeName) {
		if (getCatalogTree().equals(treeName)) {
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

	/** How the catalog and the schema a table count was taken over are named in a log line. */
	private static String scopeName(String catalog, String schema) {
		if (catalog!=null && schema!=null) {
			return catalog+"."+schema;
		}
		if (catalog!=null) {
			return catalog;
		}
		return schema!=null ? schema : "this connection";
	}

	/**
	 * The catalog this connection works in, or {@code null} where the driver will not say. Every table
	 * lookup of this backend is narrowed by it, and a driver refusing to name it must not fail the
	 * lookup being narrowed: the unnarrowed question is the one this code asked before there was
	 * anything to narrow it by. It is a weaker question, and where it decides a drop or a create it is
	 * as weak as it ever was - a driver naming neither catalog nor schema is what {@link
	 * #isExistsTable(Connection, String, String, String)} describes, and nothing here can make it say
	 * more than it will.
	 * <p>
	 * A narrowed lookup is the narrower question in the other direction too: a table this backend
	 * created under a schema the connection no longer works in - a postgres installation whose
	 * search_path resolved to another schema when the tables were made, an oracle one reaching them
	 * through a synonym - was found by the unnarrowed lookup of the version before this one and is
	 * not found here, so an open recreates it empty in the schema this connection does work in. It is
	 * the same question the backend answers everywhere else: it creates its tables unqualified, reads
	 * them unqualified, and a table it cannot reach that way is not one it can be said to hold.
	 */
	static String catalogOf(Connection con) {
		try {
			// an empty name is not the name of a catalog but a driver's way of saying it has none, and
			// passed to a metadata pattern it means "tables that belong to no catalog" - which is not
			// the same question and would answer nothing
			return emptyToNull(con.getCatalog());
		} catch (Exception e) {
			return null;
		}
	}

	/** The schema this connection works in, or {@code null} where the driver will not say; see {@link #catalogOf}. */
	static String schemaOf(Connection con) {
		try {
			return emptyToNull(con.getSchema());
		} catch (Exception e) {
			return null;
		}
	}

	/** An empty name is the name of nothing: see {@link #catalogOf}. */
	private static String emptyToNull(String name) {
		return name==null || name.isEmpty() ? null : name;
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
	 */
	@Override
	public <T> T read(ReadOperation<T> readOperation) throws Exception {
		try(final Connection con=getConnection()) {
			return readOperation.run(new ReadableTransactionImpl(con));
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
	 */
	@Override
	public void write(WriteOperation writeOperation) throws Exception {
		final long giveUpAt=System.nanoTime()+MAX_RETRY_WINDOW_NANOS;
		for (int attempt=1;;attempt++) {
			Exception failure=null;
			String driver=null;
			try (final Connection con=getConnection()) {
				driver=driverNameOf(con);
				final WriteableTransactionTransactionImpl txn=new WriteableTransactionTransactionImpl(con);
				try {
					writeOperation.run(txn);
					con.commit();
					return;
				} catch (Exception e) {
					try {
						con.rollback();
					} catch (SQLException ex) {}
					//rethrown, so that a failure of the implicit close() is suppressed into the failure being
					//replayed rather than replacing it
					failure=e;
					throw e;
				} finally { // the comment connection lives no longer than the trees it stamped, and no longer
					// than the attempt that opened it: a replay stamps on a session of its own
					txn.stampSession.close();
				}
			} catch (Exception e) {
				//anything the operation did not throw comes from getConnection() or from the implicit close(),
				//which returns the connection to the pool: neither belongs to the replayed region
				if (e!=failure) {
					throw e;
				}
			}
			//System.nanoTime()-giveUpAt is the overflow safe form of the comparison
			if (attempt>=MAX_RETRIES || System.nanoTime()-giveUpAt>=0 || !isRetryableConflict(failure,driver)) {
				throw failure;
			}
			//logged rather than silently absorbed, so that a deployment retrying most of its writes stays observable;
			//one line per replay, since an add can emit nine of them and a stack trace each time reads as a failure
			logger.warn(LocalizableMessage.raw("jdbc: replaying the transaction after a conflict, attempt %d of %d: %s",
					attempt, MAX_RETRIES, conflictSummary(failure)));
			if (logger.isTraceEnabled()) {
				logger.trace("jdbc: the conflict being replayed was %s", stackTraceToSingleLineString(failure));
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

	/** Returns the randomized delay before the given attempt is replayed, doubling with each attempt up to a cap. */
	static long retryDelayMillis(int attempt) {
		final double bound=Math.min(MAX_SLEEP_ON_RETRY_MS, BASE_SLEEP_ON_RETRY_MS * (1 << Math.min(attempt-1, 5)));
		return (long) (Math.random() * bound);
	}

	/**
	 * Returns whether the given failure carries a transaction conflict that replaying the operation can resolve.
	 * <p>
	 * The conflict is looked up along the whole cause chain because it reaches this class wrapped: a deadlock in
	 * {@code put} arrives as {@code StorageRuntimeException(SQLException)}, and a caller such as
	 * {@code EntryContainer.addEntry} may wrap it once more.
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
		for (int hop=0; t!=null && hop<MAX_CAUSE_HOPS; t=t.getCause(), hop++) {
			if (t instanceof SQLException && isConflict((SQLException) t, driver)) {
				return true;
			}
		}
		return false;
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
	 * Returns the SQLState and vendor error number of the first {@link SQLException} of the given cause chain, which
	 * is what identifies a conflict, so that a replay can be logged without a stack trace on every attempt.
	 */
	static String conflictSummary(Throwable failure) {
		Throwable t=failure;
		for (int hop=0; t!=null && hop<MAX_CAUSE_HOPS; t=t.getCause(), hop++) {
			if (t instanceof SQLException) {
				final SQLException e=(SQLException) t;
				return "SQLState "+e.getSQLState()+", error "+e.getErrorCode()+": "+e.getMessage();
			}
		}
		return String.valueOf(failure);
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

	private class ReadableTransactionImpl implements ReadableTransaction {
		final Connection con;
		boolean isReadOnly=true;

		public ReadableTransactionImpl(Connection con) {
			this.con=con;
		}

		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			try (final PreparedStatement statement=con.prepareStatement("select v from "+getTableName(treeName)+" where h="+hashParam(con)+" and k=?")){
				statement.setString(1,key2hash.get(ByteBuffer.wrap(key.toByteArray())));
				statement.setBytes(2,real2db(key.toByteArray()));
				try(ResultSet rc=executeResultSet(statement)) {
					return rc.next() ? ByteString.wrap(rc.getBytes("v")) : null;
				}
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		@Override
		public Cursor<ByteString, ByteString> openCursor(TreeName treeName) {
			return new CursorImpl(isReadOnly,con,treeName);
		}

		@Override
		public long getRecordCount(TreeName treeName) {
			try (final PreparedStatement statement=con.prepareStatement("select count(*) from "+getTableName(treeName));
				 final ResultSet rc=executeResultSet(statement)){
				return rc.next() ? rc.getLong(1) : 0;
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
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

		public WriteableTransactionTransactionImpl(Connection con) {
			super(con);
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
		 * The database and the schema this transaction works in, asked of its connection once. Every
		 * lookup of a table below is narrowed to them - the reason is in {@link #isExistsTable(Connection,
		 * String, String, String)} - and asking per lookup would cost a round trip per tree of the
		 * backend on every open: pgjdbc answers getSchema() with a "select current_schema()" of its own.
		 * A transaction holds one connection for the whole of its life, so one answer serves it all.
		 */
		private String tableCatalog;
		private String tableSchema;
		private boolean tableScopeKnown;

		private void takeTableScope() {
			if (!tableScopeKnown) {
				tableCatalog=catalogOf(con);
				tableSchema=schemaOf(con);
				tableScopeKnown=true;
			}
		}

		boolean isExistsTable(TreeName treeName) {
			takeTableScope();
			return JDBCStorage.this.isExistsTable(con, tableCatalog, tableSchema, getTableName(treeName));
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
				// getCatalogTree(). Written before the table and not after it, and committed as it is
				// written, so that the table is never there without a row naming it - on every engine,
				// and not only on the ones whose DDL happens to carry the row along. Of the two ways a
				// half-done open can end, a catalog naming a table that is not there is the one the
				// removal is ready for - it skips such a row and says so - while a table nothing names is
				// adopted with its stale rows by the next open of that tree and is dropped by no clear
				// ever after. deleteTree() takes the row out after the drop for that same reason, which is
				// why it is not the mirror of this. It writes, so it comes after the read-only check and
				// not before it (#874)
				enrolInCatalog(treeName);
				if (!isExistsTable(treeName)) {
					try (final PreparedStatement statement=con.prepareStatement("create table "+getTableName(treeName)+" ("+getTableDialect()+")")){
						execute(statement);
						con.commit();
					}catch (SQLException e) {
						throw new StorageRuntimeException(e);
					}
				}
				// CursorImpl iterates with "where k>? order by k" batches: primary key (h,k) cannot serve them
				final String driverName=driverNameOf(con);
				final String tableName=getTableName(treeName);
				if (driverName.contains("postgres")) {
					try (final PreparedStatement statement=con.prepareStatement("create index if not exists k_"+tableName.substring("opendj_".length())+" on "+tableName+" (k)")){
						execute(statement);
						con.commit();
					}catch (SQLException e) {
						throw new StorageRuntimeException(e);
					}
				}else if (driverName.contains("mysql")) {
					try {
						if (!isExistsIndex(tableName,"k_"+tableName.substring("opendj_".length()))) { // mysql has no "create index if not exists"
							try (final PreparedStatement statement=con.prepareStatement("create index k_"+tableName.substring("opendj_".length())+" on "+tableName+" (k)")){
								execute(statement);
								con.commit();
							}
						}
					}catch (SQLException e) {
						throw new StorageRuntimeException(e);
					}
				}else if (driverName.contains("oracle")) {
					try {
						// oracle has no "create index if not exists"; unquoted identifiers are stored in uppercase
						if (!isExistsIndex(tableName.toUpperCase(),"k_"+tableName.substring("opendj_".length()))) {
							try (final PreparedStatement statement=con.prepareStatement("create index k_"+tableName.substring("opendj_".length())+" on "+tableName+" (k)")){
								execute(statement);
								con.commit();
							}
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
		 * its trees to be created again. It is committed where it is written and not left to the
		 * enclosing transaction, which is what makes that first open enough: it creates no table, so
		 * nothing else of {@link #openTree} would commit it. What the catalog already records is read
		 * once, when this storage first opens it; see {@link #enrolledTrees}.
		 */
		void enrolInCatalog(TreeName treeName) {
			final TreeName catalog=getCatalogTree();
			if (catalog.equals(treeName)) {
				return; // the catalog holds no row of its own: catalogTables() adds it when its table is there
			}
			if (SHARED_COMPRESSED_SCHEMA_BASE_DN.equals(treeName.getBaseDN())) {
				return; // a tree this backend may not be the only owner of: see the constant
			}
			if (!catalogTableOpened) {
				openCatalogTable(catalog);
				// stamped with its tree name like any table of a tree (#866), and for a reason of its
				// own: a clear reports what it did not drop, and the catalog of a backend sharing this
				// database (#873) is the one table such a report could otherwise attribute to nobody.
				// It costs one stamp per open of the storage, not one per tree: this runs behind the
				// very flag that keeps the catalog from being opened again
				commentTable(catalog, dialectOf(con), stampSession);
				readEnrolledTrees();
				catalogTableOpened=true;
			}
			if (enrolledTrees.contains(treeName)) {
				return; // already recorded, at the table this open would record it at
			}
			try {
				upsert(catalog, ByteString.valueOfUtf8(treeName.toString()),
					ByteString.valueOfUtf8(getTableName(treeName)));
				// The row carries a commit of its own, so that it is there before the table on every
				// engine and not only where the "create table" below happens to carry it: postgres and
				// sql server commit the row with the table they go on to create, and on mysql and oracle
				// the "create table" commits it as its DDL begins - but the open which fills the catalog
				// of a backend upgraded from a version keeping none creates no table at all, and that is
				// the one open whose enrolment has to survive. A transaction failing after it would take
				// every one of those rows back, leaving the tables named by nothing and the next clear
				// dropping nothing - which is #888 over again.
				// The commit is the transaction's and carries whatever else its caller has pending, so it
				// is not issued for a row that is already there: see enrolledTrees, which is why an open
				// of a backend whose catalog is complete - every open after the first - commits nothing
				// here at all.
				con.commit();
				enrolledTrees.add(treeName);
			} catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		/**
		 * Reads what the catalog already records, so that the trees it names are not enrolled again on
		 * an open which would write the rows that are already there; see {@link #enrolledTrees}. Run
		 * once per open of the storage, behind the very flag that keeps the catalog from being opened
		 * again, and it costs the one select a clear pays for anyway.
		 */
		void readEnrolledTrees() {
			takeTableScope();
			try {
				for (final Map.Entry<TreeName,String> row : catalogTables(con, tableCatalog, tableSchema).entrySet()) {
					// a row recording another table than this version would record is not the row this
					// open would leave behind: a removal drops the table the row records, so such a row is
					// rewritten - and committed - exactly like one that is not there at all
					if (getTableName(row.getKey()).equals(row.getValue())) {
						enrolledTrees.add(row.getKey());
					}
				}
			} catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		/**
		 * Creates the table of the catalog when it is not there yet. It takes no index of the kind
		 * openTree() gives a tree: the catalog is read whole and written by key, never iterated by key
		 * range, so the index a cursor needs would serve nothing here. The stamp it does take is given
		 * by the caller, on every open rather than on creation alone; see {@link #enrolInCatalog}.
		 */
		void openCatalogTable(TreeName catalog) {
			if (isExistsTable(catalog)) {
				return;
			}
			try (final PreparedStatement statement=con.prepareStatement("create table "+getTableName(catalog)+" ("+getTableDialect()+")")) {
				execute(statement);
				con.commit();
			} catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		/**
		 * Takes the tree out of the catalog: a row is what puts a table up for removal, and this one is
		 * gone. Returns whether the delete was issued, which is what {@link #deleteTree} commits.
		 */
		boolean unenrolFromCatalog(TreeName treeName) {
			final TreeName catalog=getCatalogTree();
			if (catalog.equals(treeName)) {
				catalogTableOpened=false; // its own table is gone: the next enrolment creates it again
				enrolledTrees.clear(); // and records every tree anew, this one having recorded nothing
				return false;
			}
			if (SHARED_COMPRESSED_SCHEMA_BASE_DN.equals(treeName.getBaseDN())) {
				// the symmetry of enrolInCatalog() and nothing more: no row of this pair was ever written,
				// so the delete would find none. What keeps the pair out of a clear is that a clear drops
				// what the catalog names and the catalog does not name them; see the constant
				return false;
			}
			// taken out of what this storage knows the catalog records whether the delete below is
			// issued or not: a tree the catalog does not name is not one an enrolment may skip
			enrolledTrees.remove(treeName);
			if (catalogTableOpened || isExistsTable(catalog)) {
				delete(catalog, ByteString.valueOfUtf8(treeName.toString()));
				return true;
			}
			return false;
		}

		boolean isExistsIndex(String tableName, String indexName) throws SQLException {
			// approximate=true: with false the oracle driver runs ANALYZE on every call
			try (final ResultSet rs = con.getMetaData().getIndexInfo(null, null, tableName, false, true)) {
				while (rs.next()) {
					if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
						return true;
					}
				}
			}
			return false;
		}
		
		public void clearTree(TreeName treeName) {
			checkReadOnly();
			try (final PreparedStatement statement=con.prepareStatement("delete from "+getTableName(treeName))){
				execute(statement);
				con.commit();
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		@Override
		public void deleteTree(TreeName treeName) {
			checkReadOnly();
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
				try (final PreparedStatement statement = con.prepareStatement("drop table " + getTableName(treeName))) {
					execute(statement);
					con.commit();
				} catch (SQLException e) {
					throw new StorageRuntimeException(e);
				}
			}
			// The row carries a commit of its own rather than being left to the enclosing transaction:
			// that transaction is the last thing this delete could still be rolled back by - write()
			// replays a class 40 conflict and rethrows everything else unreplayed - and the row would be
			// rolled back over a table that is already gone, with nothing ever to put it right: a deleted
			// tree is not opened again, so no enrolment and no unenrolment reaches it a second time.
			// The commit is the transaction's and not the row's, so it carries whatever else the caller
			// has pending. That is the shape of a deleteTree on every engine here anyway - the drop
			// above commits, and on mysql and oracle the DDL would commit even without being asked -
			// and this makes the branch where the table was already gone behave like the other one.
			if (unenrolFromCatalog(treeName)) {
				try {
					con.commit();
				} catch (SQLException e) {
					throw new StorageRuntimeException(e);
				}
			}
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
					return (execute(statement) == 1 && statement.getUpdateCount() > 0);
				}
			}else if (driverName.contains("mysql")) { //mysql upsert
				try (final PreparedStatement statement = con.prepareStatement("insert into " + getTableName(treeName) + " (h,k,v) values (?,?,?) as new ON DUPLICATE KEY UPDATE v=new.v")) {
					statement.setString(1, key2hash.get(ByteBuffer.wrap(key.toByteArray())));
					statement.setBytes(2, real2db(key.toByteArray()));
					statement.setBytes(3, value.toByteArray());
					return (execute(statement) == 1 && statement.getUpdateCount() > 0);
				}
			}else if (driverName.contains("oracle")) { //ANSI MERGE without ;
				try (final PreparedStatement statement = con.prepareStatement("merge into " + getTableName(treeName) + " old using (select ? h,? k,? v from dual) new on (old.h=new.h and old.k=new.k) WHEN MATCHED THEN UPDATE SET old.v=new.v WHEN NOT MATCHED THEN INSERT (h,k,v) VALUES (new.h,new.k,new.v)")) {
					statement.setString(1, key2hash.get(ByteBuffer.wrap(key.toByteArray())));
					statement.setBytes(2, real2db(key.toByteArray()));
					statement.setBytes(3, value.toByteArray());
					return (execute(statement) == 1 && statement.getUpdateCount() > 0);
				}
			}else if (driverName.contains("microsoft")) { //ANSI MERGE with ; WITH (HOLDLOCK) makes the upsert atomic: without it SQL Server MERGE can race two concurrent NOT MATCHED inserts of the same key into a PRIMARY KEY violation. UPDLOCK is required on top of it: with HOLDLOCK alone the search phase takes a shared lock that the WHEN MATCHED update then has to convert to an exclusive one, so two concurrent upserts of the same key deadlock on the conversion; an update lock is taken right away and makes the second transaction wait instead. h is cast back to char so that the join can seek the primary key instead of scanning the whole table under those locks, see hashParam()
				try (final PreparedStatement statement = con.prepareStatement("merge into " + getTableName(treeName) + " WITH (HOLDLOCK, UPDLOCK) old using (select cast(? as char(128)) h,? k,? v) new on (old.h=new.h and old.k=new.k) WHEN MATCHED THEN UPDATE SET old.v=new.v WHEN NOT MATCHED THEN INSERT (h,k,v) VALUES (new.h,new.k,new.v);")) {
					statement.setString(1, key2hash.get(ByteBuffer.wrap(key.toByteArray())));
					statement.setBytes(2, real2db(key.toByteArray()));
					statement.setBytes(3, value.toByteArray());
					return (execute(statement) == 1 && statement.getUpdateCount() > 0);
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
				return (execute(statement)==1 && statement.getUpdateCount()>0);
			}
		}

		boolean update(TreeName treeName, ByteSequence key, ByteSequence value) throws SQLException {
			try (final PreparedStatement statement=con.prepareStatement("update "+getTableName(treeName)+" set v=? where h=? and k=?")){
				statement.setBytes(1,value.toByteArray());
				statement.setString(2,key2hash.get(ByteBuffer.wrap(key.toByteArray())));
				statement.setBytes(3,real2db(key.toByteArray()));
				return (execute(statement)==1 && statement.getUpdateCount()>0);
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
			try (final PreparedStatement statement=con.prepareStatement("delete from "+getTableName(treeName)+" where h="+hashParam(con)+" and k=?")){
				statement.setString(1,key2hash.get(ByteBuffer.wrap(key.toByteArray())));
				statement.setBytes(2,real2db(key.toByteArray()));
				return (execute(statement)==1 && statement.getUpdateCount()>0);
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
		final String tableName;
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

		public CursorImpl(boolean isReadOnly, Connection con, TreeName treeName) {
			this.isReadOnly=isReadOnly;
			this.con=con;
			this.tableName=getTableName(treeName);
			this.limitClause=((CachedConnection)con).parent.getClass().getName().contains("mysql")
				? " limit ?,?" : " offset ? rows fetch next ? rows only";
		}

		int adaptiveBatchSize() {
			final int size=nextBatchSize;
			nextBatchSize=Math.min(batchSize,size*4);
			return size;
		}

		boolean fetchBatch(String condition, byte[] dbKey, long offset, boolean descending, int limit) {
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
				try(final ResultSet rc=executeResultSet(statement)) {
					while (rc.next()) {
						buffer.add(new byte[][]{rc.getBytes(1),rc.getBytes(2)});
					}
				}
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
			return !buffer.isEmpty();
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
			if (buffer.isEmpty() && !fetchBatch(currentKeyDb==null?null:">",currentKeyDb,0,false,adaptiveBatchSize())) {
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
			try (final PreparedStatement statement=con.prepareStatement("delete from "+tableName+" where h="+hashParam(con)+" and k=?")){
				statement.setString(1,key2hash.get(ByteBuffer.wrap(db2real(currentKeyDb))));
				statement.setBytes(2,currentKeyDb);
				execute(statement);
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
			if (fetchBatch(">=",target,0,false,adaptiveBatchSize())) {
				advanceFromBuffer();
				return true;
			}
			defined=false;
			return false;
		}

		@Override
		public boolean positionToKey(ByteSequence key) {
			final byte[] real=key.toByteArray();
			try (final PreparedStatement statement=con.prepareStatement("select v from "+tableName+" where h="+hashParam(con)+" and k=?")){
				statement.setString(1,key2hash.get(ByteBuffer.wrap(real)));
				statement.setBytes(2,real2db(real));
				try(final ResultSet rc=executeResultSet(statement)) {
					if (rc.next()) {
						buffer.clear();
						nextBatchSize=initialBatchSize;
						currentKeyDb=real2db(real);
						currentKey=ByteString.wrap(real);
						currentValue=ByteString.wrap(rc.getBytes("v"));
						defined=true;
						return true;
					}
				}
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
			defined=false;
			return false;
		}

		@Override
		public boolean positionToLastKey() {
			if (fetchBatch(null,null,0,true,1)) {
				advanceFromBuffer();
				return true;
			}
			defined=false;
			return false;
		}

		@Override
		public boolean positionToIndex(int index) {
			if (!buffer.isEmpty()) { // absolute jump: random access, back to small batches
				nextBatchSize=initialBatchSize;
			}
			if (index>=0 && fetchBatch(null,null,index,false,adaptiveBatchSize())) {
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
	 * there. {@link #catalogTables(Connection, String, String)} is what the removal reads, and it
	 * names them not.
	 * <p>
	 * The catalog itself is among the names, being a tree of this backend like any other: {@code
	 * dbtest list-raw-dbs} counts it and {@code dump-raw-db} resolves its name, which is the one way
	 * of seeing from outside the server what a clear of this backend would drop.
	 */
	@Override
	public Set<TreeName> listTrees() {
		try (final Connection con=getConnection()) {
			return listTrees(con);
		} catch (StorageRuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new StorageRuntimeException(e);
		}
	}

	Set<TreeName> listTrees(Connection con) throws SQLException {
		final String catalog=catalogOf(con);
		final String schema=schemaOf(con);
		final Set<TreeName> trees=new HashSet<>(catalogTables(con, catalog, schema).keySet());
		for (final TreeName treeName : SHARED_COMPRESSED_SCHEMA_TREES) {
			// asked of the database, not assumed: the pair belongs to no backend in particular, and
			// once #881 gives each backend a pair of its own an installation may hold neither table.
			// Narrowed to this database: a pair of the same name in another database of the server
			// would otherwise have this backend name two trees it does not hold
			if (isExistsTable(con, catalog, schema, getTableName(treeName))) {
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
	 * The catalog and the schema are the caller's rather than asked for here: neither is free of a
	 * round trip - pgjdbc answers getSchema() with a "select current_schema()" of its own - and a
	 * clear and a listTrees() both narrow a lookup of their own by them, so they pass what they have
	 * instead of every reader asking twice over.
	 */
	Map<TreeName,String> catalogTables(Connection con, String catalog, String schema) throws SQLException {
		final TreeName catalogTree=getCatalogTree();
		final String catalogTable=getTableName(catalogTree);
		// narrowed to this database: a catalog of the same name in another database of the server
		// would send the select below at a table that is not here, failing the clear it answers
		if (!isExistsTable(con, catalog, schema, catalogTable)) {
			return Collections.emptyMap();
		}
		final Map<TreeName,String> trees=new LinkedHashMap<>();
		try (final PreparedStatement statement=con.prepareStatement("select k,v from "+catalogTable);
			 final ResultSet rs=executeResultSet(statement)) {
			while (rs.next()) {
				final byte[] key=rs.getBytes("k");
				if (key==null) { // no tree is named by a row with no key, and a clear must not fail over one
					logger.warn(LocalizableMessage.raw("jdbc: table %s holds a row naming no tree at all: skipped",
						catalogTable));
					continue;
				}
				final String name=new String(db2real(key), StandardCharsets.UTF_8);
				final TreeName treeName;
				try {
					treeName=TreeName.valueOf(name);
				} catch (RuntimeException e) { // reported rather than passed off as a backend with fewer trees
					logger.warn(LocalizableMessage.raw("jdbc: table %s holds \"%s\", which is not the name of a tree: skipped",
						catalogTable, name));
					continue;
				}
				final byte[] table=rs.getBytes("v");
				trees.put(treeName, table==null || table.length==0
					? getTableName(treeName) // a row of a version which recorded the name and not the table
					: new String(table, StandardCharsets.UTF_8));
			}
		}
		// The catalog names every tree of the backend but itself, and is put last on purpose: the
		// removal drops the trees in this order, and what names them has to outlive them. Dropping a
		// table is DDL, which mysql and oracle commit as they go, so a removal that fails halfway is
		// finished by the next attempt rather than leaving behind tables nothing names any more.
		trees.remove(catalogTree); // no row should name it; one that does must not hold back the order
		trees.put(catalogTree, catalogTable);
		return trees;
	}

	private final class ImporterImpl implements Importer {
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

		public ImporterImpl() {
			isOpen=getStorageStatus().isWorking();
			if (!isOpen) {
				try {
					open(AccessMode.READ_WRITE);
				}catch (Exception e) {
					throw new StorageRuntimeException(e);
				}
			}
			try {
				con = getConnection();
			}catch (Exception e){
				throw new StorageRuntimeException(e);
			}
			txr =new ReadableTransactionImpl(con);
			txw =new WriteableTransactionTransactionImpl(con);
		}
		
		@Override
		public void aborted() {
			aborted = true;
		}

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
						txw.stampSession.close();
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
		
		@Override
		public SequentialCursor<ByteString, ByteString> openCursor(TreeName treeName) {
			return txr.openCursor(treeName);
		}
	}
	
	//import
	@Override
	public Importer startImport() throws ConfigException, StorageRuntimeException {
		return new ImporterImpl();
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
