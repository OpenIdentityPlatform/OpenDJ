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
  private Environment dbEnvironment = null;
  private Database stateDb = null;
  private ReplicationServer replicationServer = null;
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
        envConfig.setConfigParam("je.cleaner.lookAheadCacheSize",
            String.valueOf(2 * 1024 * 1024));
        envConfig.setConfigParam("je.log.iteratorReadSize",
            String.valueOf(2 * 1024 * 1024));
        envConfig.setConfigParam("je.log.faultReadSize",
            String.valueOf(4 * 1024));

        /*
         * The cache size must be bigger in order to accommodate the larger
         * buffers - see OPENDJ-943.
         */
        envConfig.setConfigParam("je.maxMemory",
            String.valueOf(16 * 1024 * 1024));
      }
      else
      {
        /*
         * Use 5M so that the replication can be used with 64M total for the
         * JVM.
         */
        envConfig.setConfigParam("je.maxMemory",
            String.valueOf(5 * 1024 * 1024));
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
      DatabaseConfig dbConfig = new DatabaseConfig();
      dbConfig.setAllowCreate(true);
      dbConfig.setTransactional(true);

      stateDb = dbEnvironment.openDatabase(null, "changelogstate", dbConfig);
      start();
    }
    catch (RuntimeException e)
    {
      throw new ChangelogException(e);
    }
  }

  /**
   * Read the list of known servers from the database and start dbHandler
   * for each of them.
   *
   * @throws ChangelogException in case of underlying Exception
   */
  private void start() throws ChangelogException, DatabaseException
  {
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    Cursor cursor = stateDb.openCursor(null, null);

    try
    {
      readDomainBaseDNGenerationIDRecords(key, data, cursor);
      readServerIdDomainBaseDNRecords(key, data, cursor);
    }
    finally
    {
      close(cursor);
    }
  }

  private void readDomainBaseDNGenerationIDRecords(DatabaseEntry key,
      DatabaseEntry data, Cursor cursor) throws ChangelogException,
      DatabaseException
  {
    // Get the domain base DN/ generationIDs records
    OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
    while (status == OperationStatus.SUCCESS)
    {
      String stringData = toString(data.getData());

      if (debugEnabled())
        TRACER.debugInfo("In "
            + this.replicationServer.getMonitorInstanceName()
            + " Read tag baseDn generationId=" + stringData);

      String[] str = stringData.split(FIELD_SEPARATOR, 3);
      if (str[0].equals(GENERATION_ID_TAG))
      {
        long generationId = toLong(str[1]);
        String baseDn = str[2];

        if (debugEnabled())
          TRACER.debugInfo("In "
              + this.replicationServer.getMonitorInstanceName()
              + " Has read baseDn=" + baseDn + " generationId=" + generationId);

        replicationServer.getReplicationServerDomain(baseDn, true)
            .initGenerationID(generationId);
      }
      status = cursor.getNext(key, data, LockMode.DEFAULT);
    }
  }

  private void readServerIdDomainBaseDNRecords(DatabaseEntry key,
      DatabaseEntry data, Cursor cursor) throws ChangelogException,
      DatabaseException
  {
    // Get the server Id / domain base DN records
    OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
    while (status == OperationStatus.SUCCESS)
    {
      String stringData = toString(data.getData());

      if (debugEnabled())
        TRACER.debugInfo("In "
            + this.replicationServer.getMonitorInstanceName()
            + " Read serverId BaseDN=" + stringData);

      String[] str = stringData.split(FIELD_SEPARATOR, 2);
      if (!str[0].equals(GENERATION_ID_TAG))
      {
        int serverId = toInt(str[0]);
        String baseDn = str[1];

        if (debugEnabled())
          TRACER.debugInfo("In "
              + this.replicationServer.getMonitorInstanceName()
              + " Has read: baseDn=" + baseDn + " serverId=" + serverId);

        DbHandler dbHandler =
            new DbHandler(serverId, baseDn, replicationServer, this,
                replicationServer.getQueueSize());

        replicationServer.getReplicationServerDomain(baseDn, true)
            .setDbHandler(serverId, dbHandler);
      }

      status = cursor.getNext(key, data, LockMode.DEFAULT);
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

    /**
     * Finds or creates the database used to store changes from the server
     * with the given serverId and the given baseDn.
     *
     * @param serverId     The server id that identifies the server.
     * @param baseDn       The baseDn that identifies the domain.
     * @param generationId The generationId associated to this domain.
     * @return the Database.
     * @throws ChangelogException in case of underlying Exception.
     */
    public Database getOrAddDb(int serverId, String baseDn, long generationId)
        throws ChangelogException
    {
      if (debugEnabled())
        TRACER.debugInfo("ReplicationDbEnv.getOrAddDb() " +
          serverId + " " + baseDn + " " + generationId);
      try
      {
        final String serverIdKey = serverId + FIELD_SEPARATOR + baseDn;

        // Opens the database for the changes received from this server
        // on this domain. Create it if it does not already exist.
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        Database db = dbEnvironment.openDatabase(null, serverIdKey, dbConfig);

        // Creates the record serverId/domain base Dn in the stateDb
        // if it does not already exist.
        putInStateDBIfNotExist(serverIdKey, serverIdKey);

        // Creates the record domain base Dn/ generationId in the stateDb
        // if it does not already exist.
        final String genIdKey = GENERATION_ID_TAG + FIELD_SEPARATOR + baseDn;
        final String genIdData = GENERATION_ID_TAG
            + FIELD_SEPARATOR + generationId
            + FIELD_SEPARATOR + baseDn;
        putInStateDBIfNotExist(genIdKey, genIdData);
        return db;
      }
      catch (RuntimeException e)
      {
        throw new ChangelogException(e);
      }
      catch (UnsupportedEncodingException e)
      {
        // can't happen
        return null;
      }
    }

  private void putInStateDBIfNotExist(String keyString, String dataString)
      throws UnsupportedEncodingException, RuntimeException
  {
    byte[] byteId = keyString.getBytes("UTF-8");
    byte[] dataByteId = dataString.getBytes("UTF-8");
    DatabaseEntry key = new DatabaseEntry();
    key.setData(byteId);
    DatabaseEntry data = new DatabaseEntry();
    OperationStatus status = stateDb.get(null, key, data, LockMode.DEFAULT);
    if (status == OperationStatus.NOTFOUND)
    {
      Transaction txn = dbEnvironment.beginTransaction(null, null);
      try
      {
        data.setData(dataByteId);
        if (debugEnabled())
          TRACER.debugInfo("Created in the state Db record key=[" + keyString
              + "] value=[" + dataString + "]");
        stateDb.put(txn, key, data);
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
        stateDb.close();
      } catch (DatabaseException e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_ERROR_CLOSING_CHANGELOG_ENV.get());
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
    String methodInvocation = "clearGenerationId(baseDN=" + baseDn + ")";

    String key = GENERATION_ID_TAG + FIELD_SEPARATOR + baseDn;
    OperationStatus status = deleteFromStateDB(key, methodInvocation);
    if (status == OperationStatus.SUCCESS || status == OperationStatus.KEYEXIST)
    {
      // TODO : should have a better error logging
      if (debugEnabled())
        TRACER.debugInfo("In "
            + this.replicationServer.getMonitorInstanceName()
            + methodInvocation + " failed " + status);
    }
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
    String key = serverId + FIELD_SEPARATOR + baseDn;
    deleteFromStateDB(key, "clearServerId(baseDN=" + baseDn + " , serverId="
        + serverId + ")");
  }

  private OperationStatus deleteFromStateDB(String keyString,
      String methodInvocation)
  {
    if (debugEnabled())
      TRACER.debugInfo("In " + this.replicationServer.getMonitorInstanceName()
          + " " + methodInvocation);

    try
    {
      final byte[] byteId = keyString.getBytes("UTF-8");
      final DatabaseEntry key = new DatabaseEntry();
      key.setData(byteId);
      final DatabaseEntry data = new DatabaseEntry();
      OperationStatus status = stateDb.get(null, key, data, LockMode.DEFAULT);
      if (status != OperationStatus.NOTFOUND)
      {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        try
        {
          stateDb.delete(txn, key);
          txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
          if (debugEnabled())
            TRACER.debugInfo(" In "
                + this.replicationServer.getMonitorInstanceName() + " "
                + methodInvocation + " succeeded " + status);
        }
        catch (RuntimeException dbe)
        {
          // Abort the txn and propagate the Exception to the caller
          txn.abort();
          throw dbe;
        }
      }
      return status;
    }
    catch (UnsupportedEncodingException e)
    {
      // can't happen
    }
    catch (RuntimeException dbe)
    {
      // FIXME can actually happen (see catch above)
      // what should we do about it?
    }
    return null;
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
      String stringId = "draftcndb";

      // Opens the database for seqnum associated to this domain.
      // Create it if it does not already exist.
      DatabaseConfig dbConfig = new DatabaseConfig();
      dbConfig.setAllowCreate(true);
      dbConfig.setTransactional(true);

      try
      {
        return dbEnvironment.openDatabase(null, stringId, dbConfig);
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

}
