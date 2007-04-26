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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.File;
import java.io.UnsupportedEncodingException;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

/**
 * This class is used to represent a Db environement that can be used
 * to create ReplicationDB.
 */
public class ReplicationDbEnv
{
  private Environment dbEnvironment = null;
  private Database stateDb = null;
  private ReplicationServer replicationServer = null;

  /**
   * Initialize this class.
   * Creates Db environment that will be used to create databases.
   * It also reads the currently known databases from the "changelogstate"
   * database.
   * @param path Path where the backing files must be created.
   * @param replicationServer the ReplicationServer that creates this
   *                          ReplicationDbEnv.
   * @throws DatabaseException If a DatabaseException occured that prevented
   *                           the initialization to happen.
   * @throws ReplicationDBException If a replicationServer internal error caused
   *                              a failure of the replicationServer processing.
   */
  public ReplicationDbEnv(String path, ReplicationServer replicationServer)
         throws DatabaseException, ReplicationDBException
  {
    this.replicationServer = replicationServer;
    EnvironmentConfig envConfig = new EnvironmentConfig();

    /* Create the DB Environment that will be used for all
     * the ReplicationServer activities related to the db
     */
    envConfig.setAllowCreate(true);
    envConfig.setTransactional(true);
    envConfig.setConfigParam("je.cleaner.expunge", "true");
    // TODO : the DB cache size should be configurable
    // For now set 5M is OK for being efficient in 64M total for the JVM
    envConfig.setConfigParam("je.maxMemory", "5000000");
    dbEnvironment = new Environment(new File(path), envConfig);

    /*
     * One database is created to store the update from each LDAP
     * server in the topology.
     * The database "changelogstate" is used to store the list of all
     * the servers that have been seen in the past.
     */
    DatabaseConfig dbConfig = new DatabaseConfig();
    dbConfig.setAllowCreate(true);
    dbConfig.setTransactional(true);

    stateDb = dbEnvironment.openDatabase(null, "changelogstate", dbConfig);
    start();

  }

  /**
   * Read the list of known servers from the database and start dbHandler
   * for each of them.
   *
   * @throws DatabaseException in case of underlying DatabaseException
   * @throws ReplicationDBException when the information from the database
   *                              cannot be decoded correctly.
   */
  private void start() throws DatabaseException, ReplicationDBException
  {
    Cursor cursor = stateDb.openCursor(null, null);
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    try
    {
      OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
      while (status == OperationStatus.SUCCESS)
      {
        try
        {
          String stringData = new String(data.getData(), "UTF-8");
          String[] str = stringData.split(" ", 2);
          short serverId = new Short(str[0]);
          DN baseDn = null;
          try
          {
            baseDn = DN.decode(str[1]);
          } catch (DirectoryException e)
          {
            int    msgID   = MSGID_IGNORE_BAD_DN_IN_DATABASE_IDENTIFIER;
            String message = getMessage(msgID, str[1]);
            logError(ErrorLogCategory.SYNCHRONIZATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
          }
          DbHandler dbHandler =
            new DbHandler(serverId, baseDn, replicationServer, this);
          replicationServer.getReplicationCache(baseDn).newDb(serverId,
                                                            dbHandler);
        } catch (NumberFormatException e)
        {
          // should never happen
          throw new ReplicationDBException(0,
              "replicationServer state database has a wrong format");
        } catch (UnsupportedEncodingException e)
        {
          // should never happens
          throw new ReplicationDBException(0, "need UTF-8 support");
        }
        status = cursor.getNext(key, data, LockMode.DEFAULT);
      }
      cursor.close();

    } catch (DatabaseException dbe) {
      cursor.close();
      throw dbe;
    }
  }

  /**
   * Find or create the database used to store changes from the server
   * with the given serverId and the given baseDn.
   * @param serverId The server id that identifies the server.
   * @param baseDn The baseDn that identifies the server.
   * @return the Database.
   * @throws DatabaseException in case of underlying Exception.
   */
  public Database getOrAddDb(Short serverId, DN baseDn)
                  throws DatabaseException
  {
    try
    {
      String stringId = serverId.toString() + " " + baseDn.toNormalizedString();
      byte[] byteId;

      byteId = stringId.getBytes("UTF-8");

      // Open the database. Create it if it does not already exist.
      DatabaseConfig dbConfig = new DatabaseConfig();
      dbConfig.setAllowCreate(true);
      dbConfig.setTransactional(true);
      Database db = dbEnvironment.openDatabase(null, stringId, dbConfig);

      DatabaseEntry key = new DatabaseEntry();
      key.setData(byteId);
      DatabaseEntry data = new DatabaseEntry();
      OperationStatus status = stateDb.get(null, key, data, LockMode.DEFAULT);
      if (status == OperationStatus.NOTFOUND)
      {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        try {
          data.setData(byteId);
          stateDb.put(txn, key, data);
          txn.commitWriteNoSync();
        } catch (DatabaseException dbe)
        {
          // Abort the txn and propagate the Exception to the caller
          txn.abort();
          throw dbe;
        }
      }
      return db;
    } catch (UnsupportedEncodingException e)
    {
      // can't happen
      return null;
    }
  }

  /**
   * Creates a new transaction.
   *
   * @return the transaction.
   * @throws DatabaseException in case of underlying database Exception.
   */
  public Transaction beginTransaction() throws DatabaseException
  {
    return dbEnvironment.beginTransaction(null, null);
  }

  /**
   * Shutdown the Db environment.
   */
  public void shutdown()
  {
    try
    {
      stateDb.close();
      dbEnvironment.close();
    } catch (DatabaseException e)
    {
      int    msgID   = MSGID_ERROR_CLOSING_CHANGELOG_ENV;
      String message = getMessage(msgID) + stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    }
  }

}
