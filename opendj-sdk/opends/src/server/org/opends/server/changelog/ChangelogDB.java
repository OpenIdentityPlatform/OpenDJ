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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.changelog;

import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.synchronization.SynchMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.List;
import java.io.File;
import java.io.UnsupportedEncodingException;

import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.core.DirectoryException;
import org.opends.server.synchronization.ChangeNumber;
import org.opends.server.synchronization.UpdateMessage;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

/**
 * This class implements the interface between the underlying database
 * and the dbHandler class.
 * This is the only class that should have code using the BDB interfaces.
 */
public class ChangelogDB
{
  private static Environment dbEnvironment = null;
  private Database db = null;
  private static Database stateDb = null;
  private String stringId = null;

  /**
   * Creates a new database or open existing database that will be used
   * to store and retrieve changes from an LDAP server.
   * @param serverId Identifier of the LDAP server.
   * @param baseDn baseDn of the LDAP server.
   * @throws DatabaseException if a database problem happened
   */
  public ChangelogDB(Short serverId, DN baseDn)
                     throws DatabaseException
  {
    try {
      stringId = serverId.toString() + " " + baseDn.toNormalizedString();
      byte[] byteId = stringId.getBytes("UTF-8");

      // Open the database. Create it if it does not already exist.
      DatabaseConfig dbConfig = new DatabaseConfig();
      dbConfig.setAllowCreate(true);
      dbConfig.setTransactional(true);

      db = dbEnvironment.openDatabase(null, stringId, dbConfig);

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
    }
    catch (UnsupportedEncodingException e)
    {
      // never happens
    }
  }

  /**
   * Initialize this class.
   * Creates Db environment that will be used to create databases.
   * It also reads the currently known databases from the "changelogstate"
   * database.
   * @param path Path where the backing files must be created.
   * @throws DatabaseException If a DatabaseException occured that prevented
   *                           the initialization to happen.
   * @throws ChangelogDBException If a changelog internal error caused
   *                              a failure of the changelog processing.
   */
  public static void initialize(String path) throws DatabaseException,
                                                    ChangelogDBException
  {
    EnvironmentConfig envConfig = new EnvironmentConfig();

    /* Create the DB Environment that will be used for all
     * the Changelog activities related to the db
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
          Changelog.getChangelogCache(baseDn).newDb(serverId, baseDn);
        } catch (NumberFormatException e)
        {
          // should never happen
          throw new ChangelogDBException(0,
              "changelog state database has a wrong format");
        } catch (UnsupportedEncodingException e)
        {
          // should never happens
          throw new ChangelogDBException(0, "need UTF-8 support");
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
   * add a list of changes to the underlying db.
   *
   * @param changes The list of changes to add to the underlying db.
   */
  public void addEntries(List<UpdateMessage> changes)
  {
    Transaction txn = null;

    try
    {
      txn = dbEnvironment.beginTransaction(null, null);

      for (UpdateMessage change : changes)
      {
        DatabaseEntry key = new ChangelogKey(change.getChangeNumber());
        DatabaseEntry data = new ChangelogData(change);

        try
        {
          db.put(txn, key, data);
        } catch (DatabaseException e)
        {
          int    msgID   = MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR;
          String message = getMessage(msgID) + stackTraceToSingleLineString(e);
          logError(ErrorLogCategory.SYNCHRONIZATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          Changelog.shutdown();
        }
      }

      txn.commitWriteNoSync();
      txn = null;
    }
    catch (DatabaseException e)
    {
      int    msgID   = MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR;
      String message = getMessage(msgID) + stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      Changelog.shutdown();
      if (txn != null)
      {
        try
        {
          txn.abort();
        } catch (DatabaseException e1)
        {
          // can't do much more. The Changelog server is shuting down.
        }
      }
    }
  }


  /**
   * Shutdown the database.
   */
  public void shutdown()
  {
    try
    {
      db.close();
    } catch (DatabaseException e)
    {
      int    msgID   = MSGID_EXCEPTION_CLOSING_DATABASE;
      String message = getMessage(msgID, stringId)  +
                                 stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.NOTICE,
               message, msgID);
    }
  }

  /**
   * Create a cursor that can be used to search or iterate on this Changelog DB.
   *
   * @param changeNumber The ChangeNumber from which the cursor must start.
   * @throws DatabaseException If a database error prevented the cursor
   *                           creation.
   * @throws Exception if the ChangelogCursor creation failed.
   * @return The ChangelogCursor.
   */
  public ChangelogCursor openReadCursor(ChangeNumber changeNumber)
                throws DatabaseException, Exception
  {
    if (changeNumber == null)
      changeNumber = readFirstChange();
    if (changeNumber == null)
      return null;
    return new ChangelogCursor(changeNumber);
  }

  /**
   * Create a cursor that can be used to delete some record from this
   * Changelog database.
   *
   * @throws DatabaseException If a database error prevented the cursor
   *                           creation.
   * @throws Exception if the ChangelogCursor creation failed.
   * @return The ChangelogCursor.
   */
  public ChangelogCursor openDeleteCursor()
                throws DatabaseException, Exception
  {
    return new ChangelogCursor();
  }

  /**
   * Read the first Change from the database.
   * @return the first ChangeNumber.
   */
  public ChangeNumber readFirstChange()
  {
    Cursor cursor;
    String str = null;

    try
    {
        cursor = db.openCursor(null, null);
    } catch (DatabaseException e1)
    {
        return null;
    }
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
      cursor.close();
      if (status != OperationStatus.SUCCESS)
      {
        /* database is empty */
        return null;
      }
      try
      {
       str = new String(key.getData(), "UTF-8");
      } catch (UnsupportedEncodingException e)
      {
        // never happens
      }
      return new ChangeNumber(str);
    } catch (DatabaseException e)
    {
      try {
      cursor.close();
      }
      catch (DatabaseException dbe)
      {
      }
      /* database is faulty : TODO : log better message */
      int    msgID   = MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR;
      String message = getMessage(msgID) + stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      Changelog.shutdown();
      return null;
    }
  }

  /**
   * Read the last Change from the database.
   * @return the last ChangeNumber.
   */
  public ChangeNumber readLastChange()
  {
    Cursor cursor;
    String str = null;

    try
    {
      cursor = db.openCursor(null, null);
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      OperationStatus status = cursor.getLast(key, data, LockMode.DEFAULT);
      cursor.close();
      if (status != OperationStatus.SUCCESS)
      {
        /* database is empty */
        return null;
      }
      try
      {
       str = new String(key.getData(), "UTF-8");
      } catch (UnsupportedEncodingException e)
      {
        // never happens
      }
      return new ChangeNumber(str);
    } catch (DatabaseException e)
    {
      int    msgID   = MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR;
      String message = getMessage(msgID) + stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      Changelog.shutdown();
      return null;
    }
  }

  /**
   * Shutdown the Db environment.
   */
  public static void shutdownDbEnvironment()
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

  /**
   * This Class implements a cursor that can be used to browse a changelog
   * database.
   */
  public class ChangelogCursor
  {
    private Cursor cursor = null;
    private Transaction txn = null;
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();

    /**
     * Creates a ChangelogCursor that can be used for browsing a changelog db.
     *
     * @param startingChangeNumber The ChangeNumber from which the cursor must
     *        start.
     * @throws Exception When the startingChangeNumber does not exist.
     */
    private ChangelogCursor(ChangeNumber startingChangeNumber)
            throws Exception
    {
      cursor = db.openCursor(txn, null);

      DatabaseEntry key = new ChangelogKey(startingChangeNumber);
      DatabaseEntry data = new DatabaseEntry();

      if (cursor.getSearchKey(key, data, LockMode.DEFAULT) !=
        OperationStatus.SUCCESS)
      {
        throw new Exception("ChangeNumber not available");
      }
    }

    private ChangelogCursor() throws DatabaseException
    {
      txn = dbEnvironment.beginTransaction(null, null);
      cursor = db.openCursor(txn, null);
    }

    /**
     * Close the Changelog Cursor.
     */
    public void close()
    {
      if (cursor == null)
        return;
      try
      {
        cursor.close();
        cursor = null;
      } catch (DatabaseException e)
      {
        int    msgID   = MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR;
        String message = getMessage(msgID) + stackTraceToSingleLineString(e);
        logError(ErrorLogCategory.SYNCHRONIZATION,
                 ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        Changelog.shutdown();
      }
      if (txn != null)
      {
        try
        {
          txn.commit();
        } catch (DatabaseException e)
        {
          int    msgID   = MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR;
          String message = getMessage(msgID) + stackTraceToSingleLineString(e);
          logError(ErrorLogCategory.SYNCHRONIZATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          Changelog.shutdown();
        }
      }
    }

    /**
     * Get the next ChangeNumber inthe database from this Cursor.
     *
     * @return The next ChangeNumber in the database from this cursor.
     * @throws DatabaseException In case of underlying database problem.
     */
    public ChangeNumber nextChangeNumber() throws DatabaseException
    {
      OperationStatus status = cursor.getNext(key, data, LockMode.DEFAULT);

      if (status != OperationStatus.SUCCESS)
      {
        return null;
      }
      try
      {
        String csnString = new String(key.getData(), "UTF-8");
        return new ChangeNumber(csnString);
      } catch (UnsupportedEncodingException e)
      {
        // can't happen
        return null;
      }
    }

    /**
     * Get the next UpdateMessage from this cursor.
     *
     * @return the next UpdateMessage.
     */
    public UpdateMessage next()
    {
      UpdateMessage currentChange = null;
      while (currentChange == null)
      {
        try
        {
          OperationStatus status = cursor.getNext(key, data, LockMode.DEFAULT);
          if (status != OperationStatus.SUCCESS)
          {
            return null;
          }
        } catch (DatabaseException e)
        {
          return null;
        }
        try {
          currentChange = ChangelogData.generateChange(data.getData());
        } catch (Exception e) {
          /*
           * An error happening trying to convert the data from the changelog
           * database to an Update Message.
           * This can only happen if the database is corrupted.
           * There is not much more that we can do at this point except trying
           * to continue with the next record.
           * In such case, it is therefore possible that we miss some changes.
           * TODO. log an error message.
           * TODO. Such problem should be handled by the repair functionality.
           */
        }
      }
      return currentChange;
    }

    /**
     * Delete the record at the current cursor position.
     *
     * @throws DatabaseException In case of database problem.
     */
    public void delete() throws DatabaseException
    {
      cursor.delete();
    }
  }
}
