/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

import com.sleepycat.je.*;

import static com.sleepycat.je.EnvironmentConfig.*;
import static com.sleepycat.je.OperationStatus.*;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class represents a DB environment that acts as a factory for
 * ReplicationDBs.
 */
public class ReplicationDbEnv
{
  private Environment dbEnvironment;
  private Database changelogStateDb;
  /**
   * The current changelogState. This is in-memory version of what is inside the
   * on-disk changelogStateDB. It improves performances in case the
   * changelogState is read often.
   *
   * @GuardedBy("stateLock")
   */
  private final ChangelogState changelogState;
  /** Exclusive lock to synchronize updates to in-memory and on-disk changelogState. */
  private final Object stateLock = new Object();
  private final List<Database> allDbs = new CopyOnWriteArrayList<Database>();
  private ReplicationServer replicationServer;
  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
  private static final String GENERATION_ID_TAG = "GENID";
  private static final String OFFLINE_TAG = "OFFLINE";
  private static final String FIELD_SEPARATOR = " ";
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Initialize this class.
   * Creates Db environment that will be used to create databases.
   * It also reads the currently known databases from the "changelogstate"
   * database.
   * @param path Path where the backing files must be created.
   * @param replicationServer the ReplicationServer that creates this
   *                          ReplicationDbEnv.
   * @throws ChangelogException If an Exception occurred that prevented
   *                           the initialization to happen.
   */
  public ReplicationDbEnv(String path, ReplicationServer replicationServer)
      throws ChangelogException
  {
    this.replicationServer = replicationServer;

    try
    {
      dbEnvironment = openJEEnvironment(path);

      /*
       * One database is created to store the update from each LDAP server in
       * the topology. The database "changelogstate" is used to store the list
       * of all the servers that have been seen in the past.
       */
      changelogStateDb = openDatabase("changelogstate");
      changelogState = readOnDiskChangelogState();
    }
    catch (RuntimeException e)
    {
      throw new ChangelogException(e);
    }
  }

  /**
   * Open a JE environment.
   * <p>
   * protected so it can be overridden by tests.
   *
   * @param path
   *          the path to the JE environment in the filesystem
   * @return the opened JE environment
   */
  protected Environment openJEEnvironment(String path)
  {
    final EnvironmentConfig envConfig = new EnvironmentConfig();

    /*
     * Create the DB Environment that will be used for all the
     * ReplicationServer activities related to the db
     */
    envConfig.setAllowCreate(true);
    envConfig.setTransactional(true);
    envConfig.setConfigParam(STATS_COLLECT, "false");
    envConfig.setConfigParam(CLEANER_THREADS, "2");
    envConfig.setConfigParam(CHECKPOINTER_HIGH_PRIORITY, "true");
    /*
     * Tests have shown that since the parsing of the Replication log is
     * always done sequentially, it is not necessary to use a large DB cache.
     */
    if (Runtime.getRuntime().maxMemory() > 256 * 1024 * 1024)
    {
      /*
       * If the JVM is reasonably large then we can safely default to bigger
       * read buffers. This will result in more scalable checkpointer and
       * cleaner performance.
       */
      envConfig.setConfigParam(CLEANER_LOOK_AHEAD_CACHE_SIZE, mb(2));
      envConfig.setConfigParam(LOG_ITERATOR_READ_SIZE, mb(2));
      envConfig.setConfigParam(LOG_FAULT_READ_SIZE, kb(4));

      /*
       * The cache size must be bigger in order to accommodate the larger
       * buffers - see OPENDJ-943.
       */
      envConfig.setConfigParam(MAX_MEMORY, mb(16));
    }
    else
    {
      /*
       * Use 5M so that the replication can be used with 64M total for the
       * JVM.
       */
      envConfig.setConfigParam(MAX_MEMORY, mb(5));
    }

    // Since records are always added at the end of the Replication log and
    // deleted at the beginning of the Replication log, this should never
    // cause any deadlock.
    envConfig.setTxnTimeout(0, TimeUnit.SECONDS);
    envConfig.setLockTimeout(0, TimeUnit.SECONDS);

    // Since replication provides durability, we can reduce the DB durability
    // level so that we are immune to application / JVM crashes.
    envConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);

    return new Environment(new File(path), envConfig);
  }

  private String kb(int sizeInKb)
  {
    return String.valueOf(sizeInKb * 1024);
  }

  private String mb(int sizeInMb)
  {
    return String.valueOf(sizeInMb * 1024 * 1024);
  }

  /**
   * Open a JE database.
   * <p>
   * protected so it can be overridden by tests.
   *
   * @param databaseName
   *          the databaseName to open
   * @return the opened JE database
   * @throws ChangelogException
   *           if a problem happened opening the database
   * @throws RuntimeException
   *           if a problem happened with the JE database
   */
  protected Database openDatabase(String databaseName)
      throws ChangelogException, RuntimeException
  {
    if (isShuttingDown.get())
    {
      throw new ChangelogException(
          WARN_CANNOT_OPEN_DATABASE_BECAUSE_SHUTDOWN_WAS_REQUESTED.get(
              databaseName, replicationServer.getServerId()));
    }
    final DatabaseConfig dbConfig = new DatabaseConfig();
    dbConfig.setAllowCreate(true);
    dbConfig.setTransactional(true);
    final Database db =
        dbEnvironment.openDatabase(null, databaseName, dbConfig);
    if (isShuttingDown.get())
    {
      closeDB(db);
      throw new ChangelogException(
          WARN_CANNOT_OPEN_DATABASE_BECAUSE_SHUTDOWN_WAS_REQUESTED.get(
              databaseName, replicationServer.getServerId()));
    }
    allDbs.add(db);
    return db;
  }

  /**
   * Return the current changelog state.
   *
   * @return the current {@link ChangelogState}
   */
  public ChangelogState getChangelogState()
  {
    return changelogState;
  }

  /**
   * Read and return the changelog state from the database.
   *
   * @return the {@link ChangelogState} read from the changelogState DB
   * @throws ChangelogException
   *           if a database problem occurs
   */
  protected ChangelogState readOnDiskChangelogState() throws ChangelogException
  {
    return decodeChangelogState(readWholeState());
  }

  /**
   * Decode the whole changelog state DB.
   *
   * @param wholeState
   *          the whole changelog state DB as a Map.
   *          The Map is only used as a convenient collection of key => data objects
   * @return the decoded changelog state
   * @throws ChangelogException
   *           if a problem occurred while decoding
   */
  ChangelogState decodeChangelogState(Map<byte[], byte[]> wholeState)
      throws ChangelogException
  {
    try
    {
      final ChangelogState result = new ChangelogState();
      for (Entry<byte[], byte[]> entry : wholeState.entrySet())
      {
        final String stringKey = toString(entry.getKey());
        final String stringData = toString(entry.getValue());

        if (logger.isTraceEnabled())
        {
          debug("read (key, data)=(" + stringKey + ", " + stringData + ")");
        }

        final String prefix = stringKey.split(FIELD_SEPARATOR)[0];
        if (prefix.equals(GENERATION_ID_TAG))
        {
          final String[] str = stringData.split(FIELD_SEPARATOR, 3);
          final long generationId = toLong(str[1]);
          final DN baseDN = DN.valueOf(str[2]);

          if (logger.isTraceEnabled())
          {
            debug("has read generationId: baseDN=" + baseDN + " generationId="
                + generationId);
          }
          result.setDomainGenerationId(baseDN, generationId);
        }
        else if (prefix.equals(OFFLINE_TAG))
        {
          final String[] str = stringData.split(FIELD_SEPARATOR, 3);
          long timestamp = toLong(str[0]);
          final int serverId = toInt(str[1]);
          final DN baseDN = DN.valueOf(str[2]);
          if (logger.isTraceEnabled())
          {
            debug("has read replica offline: baseDN=" + baseDN + " serverId="
                + serverId);
          }
          result.addOfflineReplica(baseDN, new CSN(timestamp, 0, serverId));
        }
        else
        {
          final String[] str = stringData.split(FIELD_SEPARATOR, 2);
          final int serverId = toInt(str[0]);
          final DN baseDN = DN.valueOf(str[1]);

          if (logger.isTraceEnabled())
          {
            debug("has read replica: baseDN=" + baseDN + " serverId="
                + serverId);
          }
          result.addServerIdToDomain(serverId, baseDN);
        }
      }
      return result;
    }
    catch (DirectoryException e)
    {
      throw new ChangelogException(e.getMessageObject(), e);
    }
  }

  private Map<byte[], byte[]> readWholeState() throws ChangelogException
  {
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    Cursor cursor = changelogStateDb.openCursor(null, null);

    try
    {
      final Map<byte[], byte[]> results = new LinkedHashMap<byte[], byte[]>();

      OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
      while (status == OperationStatus.SUCCESS)
      {
        results.put(key.getData(), data.getData());
        status = cursor.getNext(key, data, LockMode.DEFAULT);
      }

      return results;
    }
    catch (RuntimeException e)
    {
      throw new ChangelogException(ERR_DATABASE_EXCEPTION.get(e.getMessage()), e);
    }
    finally
    {
      close(cursor);
    }
  }

  private int toInt(String data) throws ChangelogException
  {
    try
    {
      return Integer.parseInt(data);
    }
    catch (NumberFormatException e)
    {
      // should never happen
      // TODO: i18n
      throw new ChangelogException(LocalizableMessage.raw(
          "replicationServer state database has a wrong format: "
          + e.getLocalizedMessage() + "<" + data + ">"));
    }
  }

  private long toLong(String data) throws ChangelogException
  {
    try
    {
      return Long.parseLong(data);
    }
    catch (NumberFormatException e)
    {
      // should never happen
      // TODO: i18n
      throw new ChangelogException(LocalizableMessage.raw(
          "replicationServer state database has a wrong format: "
          + e.getLocalizedMessage() + "<" + data + ">"));
    }
  }

  private String toString(byte[] data) throws ChangelogException
  {
    try
    {
      return new String(data, "UTF-8");
    }
    catch (UnsupportedEncodingException e)
    {
      // should never happens
      // TODO: i18n
      throw new ChangelogException(LocalizableMessage.raw("need UTF-8 support"));
    }
  }

  /**
   * Converts the string to a UTF8-encoded byte array.
   *
   * @param s
   *          the string to convert
   * @return the byte array representation of the UTF8-encoded string
   */
  static byte[] toBytes(String s)
  {
    try
    {
      return s.getBytes("UTF-8");
    }
    catch (UnsupportedEncodingException e)
    {
      // can't happen
      return null;
    }
  }

  /**
   * Finds or creates the database used to store changes for a replica with the
   * given baseDN and serverId.
   *
   * @param serverId
   *          The server id that identifies the server.
   * @param baseDN
   *          The baseDN that identifies the domain.
   * @param generationId
   *          The generationId associated to this domain.
   * @return the Database.
   * @throws ChangelogException
   *           in case of underlying Exception.
   */
  public Database getOrAddReplicationDB(int serverId, DN baseDN, long generationId)
      throws ChangelogException
  {
    if (logger.isTraceEnabled())
    {
      debug("ReplicationDbEnv.getOrAddDb(" + serverId + ", " + baseDN + ", "
          + generationId + ")");
    }
    try
    {
      // JNR: redundant info is stored between the key and data down below.
      // It is probably ok since "changelogstate" DB does not receive a high
      // volume of inserts.
      Entry<String, String> replicaEntry = toReplicaEntry(baseDN, serverId);

      // Opens the DB for the changes received from this server on this domain.
      final Database replicaDB = openDatabase(replicaEntry.getKey());

      synchronized (stateLock)
      {
        putInChangelogStateDBIfNotExist(toByteArray(replicaEntry));
        changelogState.addServerIdToDomain(serverId, baseDN);
        putInChangelogStateDBIfNotExist(toGenIdEntry(baseDN, generationId));
        changelogState.setDomainGenerationId(baseDN, generationId);
      }
      return replicaDB;
    }
    catch (RuntimeException e)
    {
      throw new ChangelogException(e);
    }
  }

  /**
   * Return an entry to store in the changelog state database representing a
   * replica in the topology.
   *
   * @param baseDN
   *          the replica's baseDN
   * @param serverId
   *          the replica's serverId
   * @return a database entry for the replica
   */
  static Entry<String, String> toReplicaEntry(DN baseDN, int serverId)
  {
    final String key = serverId + FIELD_SEPARATOR + baseDN.toNormalizedUrlSafeString();
    final String value = serverId + FIELD_SEPARATOR + baseDN;
    return new SimpleImmutableEntry<String, String>(key, value);
  }

  /**
   * Return an entry to store in the changelog state database representing the
   * domain generation id.
   *
   * @param baseDN
   *          the domain's baseDN
   * @param generationId
   *          the domain's generationId
   * @return a database entry for the generationId
   */
  static Entry<byte[], byte[]> toGenIdEntry(DN baseDN, long generationId)
  {
    final String key = GENERATION_ID_TAG + FIELD_SEPARATOR + baseDN.toNormalizedUrlSafeString();
    final String data = GENERATION_ID_TAG + FIELD_SEPARATOR + generationId
        + FIELD_SEPARATOR + baseDN;
    return new SimpleImmutableEntry<byte[], byte[]>(toBytes(key), toBytes(data));
  }

  /**
   * Converts an Entry&lt;String, String&gt; to an Entry&lt;byte[], byte[]&gt;.
   *
   * @param entry
   *          the entry to convert
   * @return the converted entry
   */
  static Entry<byte[], byte[]> toByteArray(Entry<String, String> entry)
  {
    return new SimpleImmutableEntry<byte[], byte[]>(toBytes(entry.getKey()), toBytes(entry.getValue()));
  }

  /**
   * Return an entry to store in the changelog state database representing the
   * time a replica went offline.
   *
   * @param baseDN
   *          the replica's baseDN
   * @param offlineCSN
   *          the replica's serverId and offline timestamp
   * @return a database entry representing the time a replica went offline
   */
  static Entry<byte[], byte[]> toReplicaOfflineEntry(DN baseDN, CSN offlineCSN)
  {
    final int serverId = offlineCSN.getServerId();
    final byte[] key = toReplicaOfflineKey(baseDN, serverId);
    final byte[] data = toBytes(offlineCSN.getTime() + FIELD_SEPARATOR + serverId
        + FIELD_SEPARATOR + baseDN);
    return new SimpleImmutableEntry<byte[], byte[]>(key, data);
  }

  /**
   * Return the key for a replica offline entry in the changelog state database.
   *
   * @param baseDN
   *          the replica's baseDN
   * @param serverId
   *          the replica's serverId
   * @return the key used in the database to store offline time of the replica
   */
  private static byte[] toReplicaOfflineKey(DN baseDN, int serverId)
  {
    return toBytes(OFFLINE_TAG + FIELD_SEPARATOR + serverId + FIELD_SEPARATOR + baseDN.toNormalizedUrlSafeString());
  }

  /** Returns an entry with the provided key and a null value. */
  private SimpleImmutableEntry<byte[], byte[]> toEntryWithNullValue(byte[] key)
  {
    return new SimpleImmutableEntry<byte[], byte[]>(key, null);
  }

  private void putInChangelogStateDBIfNotExist(Entry<byte[], byte[]> entry)
      throws ChangelogException, RuntimeException
  {
    DatabaseEntry key = new DatabaseEntry(entry.getKey());
    DatabaseEntry data = new DatabaseEntry();
    if (changelogStateDb.get(null, key, data, LockMode.DEFAULT) == NOTFOUND)
    {
      Transaction txn = dbEnvironment.beginTransaction(null, null);
      try
      {
        data.setData(entry.getValue());
        if (logger.isTraceEnabled())
        {
          debug("putting record in the changelogstate Db key=["
              + toString(entry.getKey()) + "] value=["
              + toString(entry.getValue()) + "]");
        }
        changelogStateDb.put(txn, key, data);
        txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
      }
      catch (DatabaseException dbe)
      {
        // Abort the txn and propagate the Exception to the caller
        txn.abort();
        throw dbe;
      }
    }
  }

    /**
     * Creates a new transaction.
     *
     * @return the transaction.
     * @throws ChangelogException in case of underlying exception
     */
    public Transaction beginTransaction() throws ChangelogException
    {
      try
      {
        return dbEnvironment.beginTransaction(null, null);
      }
      catch (RuntimeException e)
      {
        throw new ChangelogException(e);
      }
    }

  /**
   * Shutdown the Db environment.
   */
  public void shutdown()
  {
    isShuttingDown.set(true);
    // CopyOnWriteArrayList iterator never throw ConcurrentModificationException
    // This code rely on openDatabase() to close databases opened concurrently
    // with this code
    final Database[] allDbsCopy = allDbs.toArray(new Database[0]);
    allDbs.clear();
    for (Database db : allDbsCopy)
    {
      closeDB(db);
    }

    try
    {
      dbEnvironment.close();
    }
    catch (DatabaseException e)
    {
      logger.error(closeDBErrorMessage(null, e));
    }
  }

  private void closeDB(Database db)
  {
    allDbs.remove(db);
    try
    {
      db.close();
    }
    catch (DatabaseException e)
    {
      logger.error(closeDBErrorMessage(db.getDatabaseName(), e));
    }
  }

  private LocalizableMessage closeDBErrorMessage(String dbName, DatabaseException e)
  {
    if (dbName != null)
    {
      return NOTE_EXCEPTION_CLOSING_DATABASE.get(dbName,
          stackTraceToSingleLineString(e));
    }
    return ERR_ERROR_CLOSING_CHANGELOG_ENV.get(stackTraceToSingleLineString(e));
  }

  /**
   * Clears the provided generationId associated to the provided baseDN from the
   * state Db.
   *
   * @param baseDN
   *          The baseDN for which the generationID must be cleared.
   * @throws ChangelogException
   *           If a database problem happened
   */
  public void clearGenerationId(DN baseDN) throws ChangelogException
  {
    synchronized (stateLock)
    {
      final int unusedGenId = 0;
      deleteFromChangelogStateDB(toGenIdEntry(baseDN, unusedGenId),
          "clearGenerationId(baseDN=" + baseDN + ")");
      changelogState.setDomainGenerationId(baseDN, unusedGenId);
    }
  }

  /**
   * Clears the provided serverId associated to the provided baseDN from the
   * state Db.
   *
   * @param baseDN
   *          The baseDN for which the serverId must be cleared.
   * @param serverId
   *          The serverId to remove from the Db.
   * @throws ChangelogException
   *           If a database problem happened
   */
  public void clearServerId(DN baseDN, int serverId) throws ChangelogException
  {
    synchronized (stateLock)
    {
      deleteFromChangelogStateDB(toByteArray(toReplicaEntry(baseDN, serverId)),
          "clearServerId(baseDN=" + baseDN + " , serverId=" + serverId + ")");
      changelogState.setDomainGenerationId(baseDN, -1);
    }
  }

  private void deleteFromChangelogStateDB(Entry<byte[], ?> entry,
      String methodInvocation) throws ChangelogException
  {
    if (logger.isTraceEnabled())
    {
      debug(methodInvocation + " starting");
    }

    try
    {
      final DatabaseEntry key = new DatabaseEntry(entry.getKey());
      final DatabaseEntry data = new DatabaseEntry();
      if (changelogStateDb.get(null, key, data, LockMode.DEFAULT) == SUCCESS)
      {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        try
        {
          changelogStateDb.delete(txn, key);
          txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
          if (logger.isTraceEnabled())
          {
            debug(methodInvocation + " succeeded");
          }
        }
        catch (RuntimeException dbe)
        {
          // Abort the txn and propagate the Exception to the caller
          txn.abort();
          throw dbe;
        }
      }
      else if (logger.isTraceEnabled())
      {
        debug(methodInvocation + " failed: key not found");
      }
    }
    catch (RuntimeException e)
    {
      if (logger.isTraceEnabled())
      {
        debug(methodInvocation + " error: " + stackTraceToSingleLineString(e));
      }
      throw new ChangelogException(e);
    }
  }

  /**
   * Notify that replica is offline.
   * <p>
   * This information is stored in the changelog state DB.
   *
   * @param baseDN
   *          the domain of the offline replica
   * @param offlineCSN
   *          the offline replica serverId and offline timestamp
   * @throws ChangelogException
   *           if a database problem occurred
   */
  public void notifyReplicaOffline(DN baseDN, CSN offlineCSN)
      throws ChangelogException
  {
    synchronized (stateLock)
    {
      // just overwrite any older entry as it is assumed a newly received offline
      // CSN is newer than the previous one
      putInChangelogStateDB(toReplicaOfflineEntry(baseDN, offlineCSN),
          "replicaOffline(baseDN=" + baseDN + ", offlineCSN=" + offlineCSN + ")");
      changelogState.addOfflineReplica(baseDN, offlineCSN);
    }
  }

  /**
   * Notify that replica is online.
   * <p>
   * Update the changelog state DB if necessary (ie, replica was known to be
   * offline).
   *
   * @param baseDN
   *          the domain of replica
   * @param serverId
   *          the serverId of replica
   * @throws ChangelogException
   *           if a database problem occurred
   */
  public void notifyReplicaOnline(DN baseDN, int serverId) throws ChangelogException
  {
    deleteFromChangelogStateDB(toEntryWithNullValue(toReplicaOfflineKey(baseDN, serverId)),
        "removeOfflineReplica(baseDN=" + baseDN + ", serverId=" + serverId + ")");
  }

  private void putInChangelogStateDB(Entry<byte[], byte[]> entry,
      String methodInvocation) throws ChangelogException
  {
    if (logger.isTraceEnabled())
    {
      debug(methodInvocation + " starting");
    }

    try
    {
      final DatabaseEntry key = new DatabaseEntry(entry.getKey());
      final DatabaseEntry data = new DatabaseEntry(entry.getValue());
      changelogStateDb.put(null, key, data);
      if (logger.isTraceEnabled())
      {
        debug(methodInvocation + " succeeded");
      }
    }
    catch (RuntimeException e)
    {
      if (logger.isTraceEnabled())
      {
        debug(methodInvocation + " error: " + stackTraceToSingleLineString(e));
      }
      throw new ChangelogException(e);
    }
  }

    /**
     * Clears the database.
     *
     * @param db
     *          The database to clear.
     */
    public final void clearDb(Database db)
    {
      String databaseName = db.getDatabaseName();

      // Closing is requested by Berkeley JE before truncate
      db.close();

      Transaction txn = null;
      try
      {
        txn = dbEnvironment.beginTransaction(null, null);
        dbEnvironment.truncateDatabase(txn, databaseName, false);
        txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
        txn = null;
      }
      catch (RuntimeException e)
      {
        logger.error(ERR_ERROR_CLEARING_DB, databaseName,
            e.getMessage() + " " + stackTraceToSingleLineString(e));
      }
      finally
      {
        try
        {
          if (txn != null)
          {
            txn.abort();
          }
        }
        catch(Exception e)
        { /* do nothing */ }
      }
    }

    /**
     * Get or create a db to manage integer change  number associated
     * to multidomain server state.
     * TODO:ECL how to manage compatibility of this db with  new domains
     * added or removed ?
     * @return the retrieved or created db.
     * @throws ChangelogException when a problem occurs.
     */
    public Database getOrCreateCNIndexDB() throws ChangelogException
    {
      try
      {
        // Opens the database for change number associated to this domain.
        // Create it if it does not already exist.
        return openDatabase("draftcndb");
      }
      catch (RuntimeException e)
      {
        throw new ChangelogException(e);
      }
    }

  /**
   * Shuts down replication when an unexpected database exception occurs. Note
   * that we do not expect lock timeouts or txn timeouts because the replication
   * databases are deadlock free, thus all operations should complete
   * eventually.
   *
   * @param e
   *          The unexpected database exception.
   */
  void shutdownOnException(DatabaseException e)
  {
    logger.error(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR, stackTraceToSingleLineString(e));
    replicationServer.shutdown();
  }

  private void debug(String message)
  {
    // replication server may be null in tests
    logger.trace("In %s, %s",
        (replicationServer != null ? replicationServer.getMonitorInstanceName() : "[test]"),
        message);
  }

}
