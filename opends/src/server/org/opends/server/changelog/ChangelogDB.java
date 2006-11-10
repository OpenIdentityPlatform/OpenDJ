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
import java.io.UnsupportedEncodingException;

import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.synchronization.ChangeNumber;
import org.opends.server.synchronization.UpdateMessage;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Database;
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
  private Database db = null;
  private ChangelogDbEnv dbenv = null;
  private Changelog changelog;
  private Short serverId;
  private DN baseDn;

  /**
   * Creates a new database or open existing database that will be used
   * to store and retrieve changes from an LDAP server.
   * @param serverId Identifier of the LDAP server.
   * @param baseDn baseDn of the LDAP server.
   * @param changelog the Changelog that needs to be shutdown
   * @param dbenv the Db encironemnet to use to create the db
   * @throws DatabaseException if a database problem happened
   */
  public ChangelogDB(Short serverId, DN baseDn, Changelog changelog,
                     ChangelogDbEnv dbenv)
                     throws DatabaseException
  {
    this.serverId = serverId;
    this.baseDn = baseDn;
    this.dbenv = dbenv;
    this.changelog = changelog;
    db = dbenv.getOrAddDb(serverId, baseDn);

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
      txn = dbenv.beginTransaction();

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
          changelog.shutdown();
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
      changelog.shutdown();
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
      String message = getMessage(msgID, this.toString())  +
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
      /* database is faulty */
      int    msgID   = MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR;
      String message = getMessage(msgID) + stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      changelog.shutdown();
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
      changelog.shutdown();
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return serverId.toString() + baseDn.toString();
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
      txn = dbenv.beginTransaction();
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
        changelog.shutdown();
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
          changelog.shutdown();
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
           * TODO : REPAIR : Such problem should be handled by the
           *        repair functionality.
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
