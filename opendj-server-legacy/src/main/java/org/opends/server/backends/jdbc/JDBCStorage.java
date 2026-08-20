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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.opends.server.backends.pluggable.spi.StorageUtils.addErrorMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

public class JDBCStorage implements org.opends.server.backends.pluggable.spi.Storage, ConfigurationChangeListener<JDBCBackendCfg>{
	
	private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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

	static String driverNameOf(Connection con) {
		return ((CachedConnection) con).parent.getClass().getName();
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
	// passed in rather than read off the connection: this runs on the stamp connection, which is
	// not a pooled one, and only for the dialects commentTable() recognizes.
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
		final Set<TreeName> trees=listTrees();
		if (!trees.isEmpty()) {
			try (final Connection con = getConnection()) {
				try {
					for (final TreeName treeName : trees) {
						try (final PreparedStatement statement = con.prepareStatement("drop table " + getTableName(treeName))) {
							execute(statement);
						}
					}
					con.commit();
				} catch (SQLException e) {
					try {
						con.rollback();
					} catch (SQLException e2) {}
					throw new StorageRuntimeException(e);
				}
			} catch (Exception e) {
				throw new StorageRuntimeException(e);
			}
			// all tables are gone: forget the mappings so listTrees() consumers skip the dropped trees
			for (final TreeName treeName : trees) {
				tree2table.invalidate(treeName);
				unstampableTrees.remove(treeName); // a table recreated later deserves a fresh stamp attempt
			}
		}
		if (!isOpen) {
			close();
		}
	}
	
	//operation
	@Override
	public <T> T read(ReadOperation<T> readOperation) throws Exception {
		try(final Connection con=getConnection()) {
			return readOperation.run(new ReadableTransactionImpl(con));
		}
	}

	@Override
	public void write(WriteOperation writeOperation) throws Exception {
		try (final Connection con=getConnection()) {
			final WriteableTransactionTransactionImpl txn=new WriteableTransactionTransactionImpl(con);
			try {
				writeOperation.run(txn);
				con.commit();
			} catch (Exception e) {
				try {
					con.rollback();
				} catch (SQLException ex) {}
				throw e;
			} finally { // the comment connection lives no longer than the trees it stamped
				txn.stampSession.close();
			}
		}
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

	private class ReadableTransactionImpl implements ReadableTransaction {
		final Connection con;
		boolean isReadOnly=true;

		public ReadableTransactionImpl(Connection con) {
			this.con=con;
		}

		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			try (final PreparedStatement statement=con.prepareStatement("select v from "+getTableName(treeName)+" where h=? and k=?")){
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
	private final class WriteableTransactionTransactionImpl extends ReadableTransactionImpl implements WriteableTransaction {

		// Shared by every table this transaction stamps: opening a backend opens all its trees,
		// and each stamp of its own connection would be a physical connect of its own. Closed by
		// write() (and by ImporterImpl.close()) when the transaction is done with.
		final StampSession stampSession=new StampSession();

		public WriteableTransactionTransactionImpl(Connection con) {
			super(con);
			if (!accessMode.isWriteable()) {
				throw new ReadOnlyStorageException();
			}
			isReadOnly = false;
		}

		boolean isExistsTable(TreeName treeName) {
			final String tableName = getTableName(treeName);
			try {
				final DatabaseMetaData metaData = con.getMetaData();
				// asked of the catalog by name: openTree(createOnDemand) calls this for every tree
				// of the backend - about 25 of them for a stock suffix, on every open - and listing
				// every table of the database each time costs the whole catalog once per tree, on a
				// database this backend may well be sharing with something else
				try (final ResultSet rs = metaData.getTables(null, null,
						storedIdentifier(metaData, tableName), new String[]{"TABLE"})) {
					while (rs.next()) {
						// the name still has to be compared: "_" is a single-character wildcard in a
						// metadata pattern, so "opendj_<hash>" also matches a table named "opendjX<hash>"
						if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
							return true;
						}
					}
				}
			} catch (Exception e) {
				throw new StorageRuntimeException(e);
			}
			return false;
		}

		String getTableDialect() {
			if (((CachedConnection) con).parent.getClass().getName().contains("oracle")) {
				return "h char(128),k raw(2000),v blob,primary key(h,k)";
			}else if (((CachedConnection) con).parent.getClass().getName().contains("mysql")) {
				return "h char(128),k varbinary(255),v longblob,primary key(h,k)";
			}else if (((CachedConnection) con).parent.getClass().getName().contains("microsoft")) {
				return "h char(128),k varbinary(max),v image,primary key(h)";
			}
			return "h char(128),k bytea,v bytea,primary key(h,k)";
		}

		@Override
		public void openTree(TreeName treeName, boolean createOnDemand) {
			if (createOnDemand) {
				if (!isExistsTable(treeName)) {
					try (final PreparedStatement statement=con.prepareStatement("create table "+getTableName(treeName)+" ("+getTableDialect()+")")){
						execute(statement);
						con.commit();
					}catch (SQLException e) {
						throw new StorageRuntimeException(e);
					}
				}
				// CursorImpl iterates with "where k>? order by k" batches: primary key (h,k) cannot serve them
				final String driverName=((CachedConnection) con).parent.getClass().getName();
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
			try (final PreparedStatement statement=con.prepareStatement("delete from "+getTableName(treeName))){
				execute(statement);
				con.commit();
			}catch (SQLException e) {
				throw new StorageRuntimeException(e);
			}
		}

		@Override
		public void deleteTree(TreeName treeName) {
			if (isExistsTable(treeName)) {
				try (final PreparedStatement statement = con.prepareStatement("drop table " + getTableName(treeName))) {
					execute(statement);
					con.commit();
				} catch (SQLException e) {
					throw new StorageRuntimeException(e);
				}
			}
			// forget the mapping so listTrees() consumers (updateTableStatistics) skip the dropped table
			tree2table.invalidate(treeName);
			unstampableTrees.remove(treeName); // a table recreated later deserves a fresh stamp attempt
		}

		@Override
		public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
			try {
				upsert(treeName, key, value);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		boolean upsert(TreeName treeName, ByteSequence key, ByteSequence value) throws SQLException {
			final String driverName=((CachedConnection) con).parent.getClass().getName();
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
			}else if (driverName.contains("microsoft")) { //ANSI MERGE with ; WITH (HOLDLOCK) makes the upsert atomic: without it SQL Server MERGE can race two concurrent NOT MATCHED inserts of the same key into a PRIMARY KEY violation. UPDLOCK is required on top of it: with HOLDLOCK alone the search phase takes a shared lock that the WHEN MATCHED update then has to convert to an exclusive one, so two concurrent upserts of the same key deadlock on the conversion; an update lock is taken right away and makes the second transaction wait instead
				try (final PreparedStatement statement = con.prepareStatement("merge into " + getTableName(treeName) + " WITH (HOLDLOCK, UPDLOCK) old using (select ? h,? k,? v) new on (old.h=new.h and old.k=new.k) WHEN MATCHED THEN UPDATE SET old.v=new.v WHEN NOT MATCHED THEN INSERT (h,k,v) VALUES (new.h,new.k,new.v);")) {
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
			try (final PreparedStatement statement=con.prepareStatement("delete from "+getTableName(treeName)+" where h=? and k=?")){
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
			try (final PreparedStatement statement=con.prepareStatement("delete from "+tableName+" where h=? and k=?")){
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
			try (final PreparedStatement statement=con.prepareStatement("select v from "+tableName+" where h=? and k=?")){
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
	
	@Override
	public Set<TreeName> listTrees() {
		return tree2table.asMap().keySet();
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
