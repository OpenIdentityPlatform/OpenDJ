/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangelogException;

import com.sleepycat.je.*;

import static com.sleepycat.je.LockMode.*;
import static com.sleepycat.je.OperationStatus.*;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class is used to represent a Db environment that can be used
 * to create ReplicationDB.
 */
public class ReplicationDbEnv
{
  private Environment dbEnvironment;
  private Database changelogStateDb;
  private ReplicationServer replicationServer;
  private static final String GENERATION_ID_TAG = "GENID";
  private static final String FIELD_SEPARATOR = " ";
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

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
      EnvironmentConfig envConfig = new EnvironmentConfig();

      /*
       * Create the DB Environment that will be used for all the
       * ReplicationServer activities related to the db
       */
      envConfig.setAllowCreate(true);
      envConfig.setTransactional(true);
      envConfig.setConfigParam("je.cleaner.threads", "2");
      envConfig.setConfigParam("je.checkpointer.highPriority", "true");

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
        envConfig.setConfigParam("je.cleaner.lookAheadCacheSize", mb(2));
        envConfig.setConfigParam("je.log.iteratorReadSize", mb(2));
        envConfig.setConfigParam("je.log.faultReadSize", kb(4));

        /*
         * The cache size must be bigger in order to accommodate the larger
         * buffers - see OPENDJ-943.
         */
        envConfig.setConfigParam("je.maxMemory", mb(16));
      }
      else
      {
        /*
         * Use 5M so that the replication can be used with 64M total for the
         * JVM.
         */
        envConfig.setConfigParam("je.maxMemory", mb(5));
      }

      // Since records are always added at the end of the Replication log and
      // deleted at the beginning of the Replication log, this should never
      // cause any deadlock.
      envConfig.setTxnTimeout(0, TimeUnit.SECONDS);
      envConfig.setLockTimeout(0, TimeUnit.SECONDS);

      // Since replication provides durability, we can reduce the DB durability
      // level so that we are immune to application / JVM crashes.
      envConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);

      dbEnvironment = new Environment(new File(path), envConfig);

      /*
       * One database is created to store the update from each LDAP server in
       * the topology. The database "changelogstate" is used to store the list
       * of all the servers that have been seen in the past.
       */
      changelogStateDb = openDatabase("changelogstate");
    }
    catch (RuntimeException e)
    {
      throw new ChangelogException(e);
    }
  }

  private String kb(int sizeInKb)
  {
    return String.valueOf(sizeInKb * 1024);
  }

  private String mb(int sizeInMb)
  {
    return String.valueOf(sizeInMb * 1024 * 1024);
  }

  private Database openDatabase(String databaseName) throws RuntimeException
  {
    final DatabaseConfig dbConfig = new DatabaseConfig();
    dbConfig.setAllowCreate(true);
    dbConfig.setTransactional(true);
    return dbEnvironment.openDatabase(null, databaseName, dbConfig);
  }

  /**
   * Read the list of known servers from the database and start dbHandler
   * for each of them.
   *
   * @throws ChangelogException in case of underlying Exception
   */
  public void initializeFromChangelogStateDB() throws ChangelogException
  {
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    Cursor cursor = changelogStateDb.openCursor(null, null);

    try
    {
      OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
      while (status == OperationStatus.SUCCESS)
      {
        final String stringData = toString(data.getData());

        if (debugEnabled())
          debug("read (" + GENERATION_ID_TAG + " generationId baseDn) OR "
              + "(serverId baseDN): " + stringData);

        final String[] str = stringData.split(FIELD_SEPARATOR, 3);
        if (str[0].equals(GENERATION_ID_TAG))
        {
          long generationId = toLong(str[1]);
          String baseDn = str[2];

          if (debugEnabled())
            debug("has read baseDn=" + baseDn + " generationId=" +generationId);

          replicationServer.initDomainGenerationID(baseDn, generationId);
        }
        else
        {
          int serverId = toInt(str[0]);
          String baseDn = str[1];

          if (debugEnabled())
            debug("has read: baseDn=" + baseDn + " serverId=" + serverId);

          replicationServer.addServerIdToDomain(serverId, baseDn);
        }

        status = cursor.getNext(key, data, LockMode.DEFAULT);
      }
    }
    catch (RuntimeException e)
    {
      throw new ChangelogException(e);
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
    } catch (NumberFormatException e)
    {
      // should never happen
      // TODO: i18n
      throw new ChangelogException(Message.raw(
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
      throw new ChangelogException(Message.raw(
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
      throw new ChangelogException(Message.raw("need UTF-8 support"));
    }
  }

  private byte[] toBytes(String s)
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
   * Finds or creates the database used to store changes from the server with
   * the given serverId and the given baseDn.
   *
   * @param serverId
   *          The server id that identifies the server.
   * @param baseDn
   *          The baseDn that identifies the domain.
   * @param generationId
   *          The generationId associated to this domain.
   * @return the Database.
   * @throws ChangelogException
   *           in case of underlying Exception.
   */
  public Database getOrAddDb(int serverId, String baseDn, long generationId)
      throws ChangelogException
  {
    if (debugEnabled())
      debug("ReplicationDbEnv.getOrAddDb(" + serverId + ", " + baseDn + ", "
          + generationId + ")");
    try
    {
      // JNR: redundant info is stored between the key and data down below.
      // It is probably ok since "changelogstate" DB does not receive a high
      // volume of inserts.
      final String serverIdToBaseDn = buildServerIdKey(baseDn, serverId);

      // Opens the DB for the changes received from this server on this domain.
      Database db = openDatabase(serverIdToBaseDn);

      putInChangelogStateDBIfNotExist(serverIdToBaseDn, serverIdToBaseDn);
      putInChangelogStateDBIfNotExist(buildGenIdKey(baseDn),
                                      buildGenIdData(baseDn, generationId));
      return db;
    }
    catch (RuntimeException e)
    {
      throw new ChangelogException(e);
    }
  }

  private String buildGenIdKey(String baseDn)
  {
    return GENERATION_ID_TAG + FIELD_SEPARATOR + baseDn;
  }

  private String buildServerIdKey(String baseDn, int serverId)
  {
    return serverId + FIELD_SEPARATOR + baseDn;
  }

  private String buildGenIdData(String baseDn, long generationId)
  {
    return GENERATION_ID_TAG + FIELD_SEPARATOR + generationId + FIELD_SEPARATOR
        + baseDn;
  }

  private void putInChangelogStateDBIfNotExist(String keyString,
      String dataString) throws RuntimeException
  {
    DatabaseEntry key = new DatabaseEntry(toBytes(keyString));
    DatabaseEntry data = new DatabaseEntry();
    if (changelogStateDb.get(null, key, data, DEFAULT) == NOTFOUND)
    {
      Transaction txn = dbEnvironment.beginTransaction(null, null);
      try
      {
        data.setData(toBytes(dataString));
        if (debugEnabled())
          debug("putting record in the changelogstate Db key=[" + keyString
              + "] value=[" + dataString + "]");
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
      try
      {
        changelogStateDb.close();
      } catch (DatabaseException e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_ERROR_CLOSING_CHANGELOG_ENV.get());
        mb.append(" ");
        mb.append(stackTraceToSingleLineString(e));
        logError(mb.toMessage());
      }

      try
      {
        dbEnvironment.close();
      } catch (DatabaseException e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_ERROR_CLOSING_CHANGELOG_ENV.get());
        mb.append(" ");
        mb.append(stackTraceToSingleLineString(e));
        logError(mb.toMessage());
      }
    }

  /**
   * Clears the provided generationId associated to the provided baseDn from the
   * state Db.
   *
   * @param baseDn
   *          The baseDn for which the generationID must be cleared.
   */
  public void clearGenerationId(String baseDn)
  {
    deleteFromChangelogStateDB(buildGenIdKey(baseDn),
        "clearGenerationId(baseDN=" + baseDn + ")");
  }

  /**
   * Clears the provided serverId associated to the provided baseDn from the
   * state Db.
   *
   * @param baseDn
   *          The baseDn for which the generationID must be cleared.
   * @param serverId
   *          The serverId to remove from the Db.
   */
  public void clearServerId(String baseDn, int serverId)
  {
    deleteFromChangelogStateDB(buildServerIdKey(baseDn, serverId),
        "clearServerId(baseDN=" + baseDn + " , serverId=" + serverId + ")");
  }

  private void deleteFromChangelogStateDB(String keyString,
      String methodInvocation)
  {
    if (debugEnabled())
      debug(methodInvocation + " starting");

    try
    {
      final DatabaseEntry key = new DatabaseEntry(toBytes(keyString));
      final DatabaseEntry data = new DatabaseEntry();
      OperationStatus status = changelogStateDb.get(null, key, data, DEFAULT);
      if (status == SUCCESS)
      {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        try
        {
          changelogStateDb.delete(txn, key);
          txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
          if (debugEnabled())
            debug(methodInvocation + " succeeded");
        }
        catch (RuntimeException dbe)
        {
          // Abort the txn and propagate the Exception to the caller
          txn.abort();
          throw dbe;
        }
      }
      else
      {
        if (debugEnabled())
          debug(methodInvocation + " failed: key=[ " + keyString
              + "] not found");
      }
    }
    catch (RuntimeException dbe)
    {
      // FIXME can actually happen (see catch above)
      // what should we do about it?
    }
  }

    /**
     * Clears the database.
     *
     * @param databaseName The name of the database to clear.
     */
    public final void clearDb(String databaseName)
    {
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
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_ERROR_CLEARING_DB.get(databaseName,
            e.getMessage() + " " +
            stackTraceToSingleLineString(e)));
        logError(mb.toMessage());
      }
      finally
      {
        try
        {
          if (txn != null)
            txn.abort();
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
    public Database getOrCreateDraftCNDb() throws ChangelogException
    {
      try
      {
        // Opens the database for seqnum associated to this domain.
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
    innerShutdownOnException(e);
  }

  /**
   * Shuts down replication when an unexpected changelog exception occurs. Note
   * that we do not expect lock timeouts or txn timeouts because the replication
   * databases are deadlock free, thus all operations should complete
   * eventually.
   *
   * @param e
   *          The unexpected changelog exception.
   */
  void shutdownOnException(ChangelogException e)
  {
    innerShutdownOnException(e);
  }

  private void innerShutdownOnException(Exception e)
  {
    MessageBuilder mb = new MessageBuilder();
    mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
    mb.append(".   ");
    mb.append(stackTraceToSingleLineString(e));
    logError(mb.toMessage());
    replicationServer.shutdown();
  }

  private void debug(String message)
  {
    TRACER.debugInfo("In " + replicationServer.getMonitorInstanceName() + ", "
        + message);
  }

}
