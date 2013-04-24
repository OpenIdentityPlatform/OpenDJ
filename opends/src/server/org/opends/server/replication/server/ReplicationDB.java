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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.decodeUTF8;
import static org.opends.server.util.StaticUtils.getBytes;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.List;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMsg;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.sleepycat.je.*;

/**
 * This class implements the interface between the underlying database
 * and the dbHandler class.
 * This is the only class that should have code using the BDB interfaces.
 */
public class ReplicationDB
{
  private Database db = null;
  private ReplicationDbEnv dbenv = null;
  private ReplicationServer replicationServer;
  private int serverId;
  private String baseDn;

  // The lock used to provide exclusive access to the thread that
  // close the db (shutdown or clear).
  private ReentrantReadWriteLock dbCloseLock;

  // Change counter management
  // The Db itself does not allow to count records between a start and an end
  // change. And we cannot rely on the replication seqnum that is part of the
  // changenumber, since there can be holes (when an operation is canceled).
  // And traversing all the records from the start one to the end one works
  // fine but can be very long (ECL:lastChangeNumber).
  //
  // So we are storing special records in the DB (called counter records),
  // that contain the number of changes since the previous counter record.
  // One special record is :
  // - a special key : changetime , serverid=0  seqnum=0
  // - a counter value : count of changes since previous counter record.
  //
  // A counter record has to follow the order of the db, so it needs to have
  // a changenumber key that follow the order.
  // A counter record must have its own changenumber key since the Db does not
  // support duplicate key (it is a compatibility breaker character of the DB).
  //
  // We define 2 conditions to store a counter record :
  // 1/- at least 'counterWindowSize' changes have been stored in the Db
  //     since the previous counter record
  // 2/- the change to be stored has a new timestamp - so that the counter
  //     record is the first record for this timestamp.
  //


  private int  counterCurrValue = 1;
  // Current value of the counter.

  private long counterTsLimit = 0;
  // When not null,
  // the next change with a ts different from tsForNewCounterRecord will lead
  // to store a new counterRecord.

  private int  counterWindowSize = 1000;
  // The counter record will never be written to the db more often than each
  // counterWindowSize changes.

 /**
   * Creates a new database or open existing database that will be used
   * to store and retrieve changes from an LDAP server.
   * @param serverId The identifier of the LDAP server.
   * @param baseDn The baseDn of the replication domain.
   * @param replicationServer The ReplicationServer that needs to be shutdown.
   * @param dbenv The Db environment to use to create the db.
   * @throws DatabaseException If a database problem happened.
   */
  public ReplicationDB(int serverId, String baseDn,
                     ReplicationServer replicationServer,
                     ReplicationDbEnv dbenv)
                     throws DatabaseException
  {
    this.serverId = serverId;
    this.baseDn = baseDn;
    this.dbenv = dbenv;
    this.replicationServer = replicationServer;

    // Get or create the associated ReplicationServerDomain and Db.
    db = dbenv.getOrAddDb(serverId, baseDn,
        replicationServer.getReplicationServerDomain(baseDn,
        true).getGenerationId());

    dbCloseLock = new ReentrantReadWriteLock(true);

    //
    Cursor cursor;
    Transaction txn = null;
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    OperationStatus status;
    int distBackToCounterRecord = 0;

    // Initialize counter
    this.counterCurrValue = 1;
    cursor = db.openCursor(txn, null);
    try
    {
      status = cursor.getLast(key, data, LockMode.DEFAULT);
      while (status == OperationStatus.SUCCESS)
      {
        ChangeNumber cn = new ChangeNumber(decodeUTF8(key.getData()));
        if (!ReplicationDB.isaCounter(cn))
        {
          status = cursor.getPrev(key, data, LockMode.DEFAULT);
          distBackToCounterRecord++;
        }
        else
        {
          // counter record
          counterCurrValue = decodeCounterValue(data.getData()) + 1;
          counterTsLimit = cn.getTime();
          break;
        }
      }
      counterCurrValue += distBackToCounterRecord;
    }
    finally
    {
      cursor.close();
    }
  }



  /**
   * add a list of changes to the underlying db.
   *
   * @param changes The list of changes to add to the underlying db.
   */
  public void addEntries(List<UpdateMsg> changes)
  {
    try
    {
      dbCloseLock.readLock().lock();

      try
      {
        // If the DB has been closed then return immediately.
        if (isDBClosed())
        {
          return;
        }

        for (UpdateMsg change : changes)
        {
          DatabaseEntry key = new ReplicationKey(
              change.getChangeNumber());
          DatabaseEntry data = new ReplicationData(change);

          if ((counterCurrValue != 0)
              && (counterCurrValue % counterWindowSize == 0))
          {
            // enough changes to generate a counter record - wait for the next
            // change of time
            counterTsLimit = change.getChangeNumber().getTime();
          }
          if ((counterTsLimit != 0)
              && (change.getChangeNumber().getTime() != counterTsLimit))
          {
            // Write the counter record
            DatabaseEntry counterKey = new ReplicationKey(
                new ChangeNumber(change.getChangeNumber().getTime(),
                    0, 0));
            DatabaseEntry counterValue =
              encodeCounterValue(counterCurrValue - 1);
            db.put(null, counterKey, counterValue);
            counterTsLimit = 0;
          }
          db.put(null, key, data);
          counterCurrValue++;
        }
      }
      finally
      {
        dbCloseLock.readLock().unlock();
      }
    }
    catch (DatabaseException e)
    {
      replicationServer.handleUnexpectedDatabaseException(e);
    }
  }


  /**
   * Shutdown the database.
   */
  public void shutdown()
  {
    try
    {
      dbCloseLock.writeLock().lock();
      try
      {
        db.close();
        db = null;
      }
      finally
      {
        dbCloseLock.writeLock().unlock();
      }
    }
    catch (DatabaseException e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(NOTE_EXCEPTION_CLOSING_DATABASE.get(this.toString()));
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
    }
  }

  /**
   * Create a cursor that can be used to search or iterate on this
   * ReplicationServer DB.
   *
   * @param changeNumber The ChangeNumber from which the cursor must start.
   * @throws DatabaseException If a database error prevented the cursor
   *                           creation.
   * @throws Exception if the ReplServerDBCursor creation failed.
   * @return The ReplServerDBCursor.
   */
  public ReplServerDBCursor openReadCursor(ChangeNumber changeNumber)
                throws DatabaseException, Exception
  {
    return new ReplServerDBCursor(changeNumber);
  }

  /**
   * Create a cursor that can be used to delete some record from this
   * ReplicationServer database.
   *
   * @throws DatabaseException If a database error prevented the cursor
   *                           creation.
   * @throws Exception if the ReplServerDBCursor creation failed.
   *
   * @return The ReplServerDBCursor.
   */
  public ReplServerDBCursor openDeleteCursor()
                throws DatabaseException, Exception
  {
    return new ReplServerDBCursor();
  }



  private void closeLockedCursor(Cursor cursor)
      throws DatabaseException
  {
    try
    {
      if (cursor != null)
      {
        try
        {
          cursor.close();
        }
        catch (DatabaseException e)
        {
          // Ignore.
        }
      }
    }
    finally
    {
      dbCloseLock.readLock().unlock();
    }
  }

  /**
   * Read the first Change from the database.
   * @return the first ChangeNumber.
   */
  public ChangeNumber readFirstChange()
  {
    Cursor cursor = null;
    ChangeNumber cn = null;

    try
    {
      dbCloseLock.readLock().lock();
      try
      {
        // If the DB has been closed then return immediately.
        if (isDBClosed())
        {
          return null;
        }

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        cursor = db.openCursor(null, null);

        OperationStatus status = cursor.getFirst(key, data,
            LockMode.DEFAULT);

        if (status != OperationStatus.SUCCESS)
        {
          /* database is empty */
          return null;
        }

        String str = decodeUTF8(key.getData());
        cn = new ChangeNumber(str);
        if (ReplicationDB.isaCounter(cn))
        {
          // First record is a counter record .. go next
          status = cursor.getNext(key, data, LockMode.DEFAULT);
          if (status != OperationStatus.SUCCESS)
          {
            // DB contains only a counter record
            return null;
          }
          else
          {
            cn = new ChangeNumber(decodeUTF8(key.getData()));
          }
        }
      }
      finally
      {
        closeLockedCursor(cursor);
      }
    }
    catch (DatabaseException e)
    {
      /* database is faulty */
      replicationServer.handleUnexpectedDatabaseException(e);
      cn = null;
    }
    return cn;
  }



  /**
   * Read the last Change from the database.
   *
   * @return the last ChangeNumber.
   */
  public ChangeNumber readLastChange()
  {
    Cursor cursor = null;
    ChangeNumber cn = null;

    try
    {
      dbCloseLock.readLock().lock();
      try
      {
        // If the DB has been closed then return immediately.
        if (isDBClosed())
        {
          return null;
        }

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        cursor = db.openCursor(null, null);

        OperationStatus status = cursor.getLast(key, data,
            LockMode.DEFAULT);

        if (status != OperationStatus.SUCCESS)
        {
          /* database is empty */
          return null;
        }

        String str = decodeUTF8(key.getData());
        cn = new ChangeNumber(str);
        if (ReplicationDB.isaCounter(cn))
        {
          if (cursor.getPrev(key, data, LockMode.DEFAULT)
              != OperationStatus.SUCCESS)
          {
            /*
             * database only contain a counter record - don't know how much it
             * can be possible but ...
             */
            cn = null;
          }
          else
          {
            str = decodeUTF8(key.getData());
            cn = new ChangeNumber(str);
            // There can't be 2 counter record next to each other
          }
        }
      }
      finally
      {
        closeLockedCursor(cursor);
      }
    }
    catch (DatabaseException e)
    {
      replicationServer.handleUnexpectedDatabaseException(e);
      cn = null;
    }

    return cn;
  }

  /**
   * Try to find in the DB, the change number right before the one
   * passed as a parameter.
   *
   * @param changeNumber
   *          The changeNumber from which we start searching.
   * @return the changeNumber right before the one passed as a parameter.
   *         Can return null if there is none.
   */
  public ChangeNumber getPreviousChangeNumber(ChangeNumber changeNumber)
  {

    if (changeNumber == null)
      return null;

    Cursor cursor = null;
    ChangeNumber cn = null;

    DatabaseEntry key = new ReplicationKey(changeNumber);
    DatabaseEntry data = new DatabaseEntry();

    try
    {
      dbCloseLock.readLock().lock();
      try
      {
        // If the DB has been closed then return immediately.
        if (isDBClosed())
        {
          return null;
        }

        cursor = db.openCursor(null, null);
        if (cursor.getSearchKeyRange(key, data, LockMode.DEFAULT)
            == OperationStatus.SUCCESS)
        {
          // We can move close to the changeNumber.
          // Let's move to the previous change.
          if (cursor.getPrev(key, data, LockMode.DEFAULT)
              == OperationStatus.SUCCESS)
          {
            String str = decodeUTF8(key.getData());
            cn = new ChangeNumber(str);
            if (ReplicationDB.isaCounter(cn))
            {
              if (cursor.getPrev(key, data, LockMode.DEFAULT)
                  != OperationStatus.SUCCESS)
              {
                // database starts with a counter record.
                cn = null;
              }
              else
              {
                str = decodeUTF8(key.getData());
                cn = new ChangeNumber(str);
                // There can't be 2 counter record next to each other
              }
            }
          }
          // else, there was no change previous to our changeNumber.
        }
        else
        {
          // We could not move the cursor past to the changeNumber
          // Check if the last change is older than changeNumber
          if (cursor.getLast(key, data, LockMode.DEFAULT)
              == OperationStatus.SUCCESS)
          {
            String str = decodeUTF8(key.getData());
            cn = new ChangeNumber(str);
            if (ReplicationDB.isaCounter(cn))
            {
              if (cursor.getPrev(key, data, LockMode.DEFAULT)
                  != OperationStatus.SUCCESS)
              {
                /*
                 * database only contain a counter record, should not be
                 * possible, but Ok, let's just say no change Number
                 */
                cn = null;
              }
              else
              {
                str = decodeUTF8(key.getData());
                cn = new ChangeNumber(str);
                // There can't be 2 counter record next to each other
              }
            }
          }
        }
      }
      finally
      {
        closeLockedCursor(cursor);
      }
    }
    catch (DatabaseException e)
    {
      replicationServer.handleUnexpectedDatabaseException(e);
      cn = null;
    }
    return cn;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return serverId + baseDn;
  }

  /**
   * This Class implements a cursor that can be used to browse a
   * replicationServer database.
   */
  public class ReplServerDBCursor
  {
     // The transaction that will protect the actions done with the cursor
    // Will be let null for a read cursor
    // Will be set non null for a write cursor
    private final Transaction txn;
    private final Cursor cursor;
    private final DatabaseEntry key;
    private final DatabaseEntry data;

    private boolean isClosed = false;

    /**
     * Creates a ReplServerDBCursor that can be used for browsing a
     * replicationServer db.
     *
     * @param startingChangeNumber
     *          The ChangeNumber from which the cursor must start.
     * @throws Exception
     *           When the startingChangeNumber does not exist.
     */
    private ReplServerDBCursor(ChangeNumber startingChangeNumber)
        throws Exception
    {
      if (startingChangeNumber != null)
      {
        key = new ReplicationKey(startingChangeNumber);
      }
      else
      {
        key = new DatabaseEntry();
      }
      data = new DatabaseEntry();

      txn = null;

      // Take the lock. From now on, whatever error that happen in the life
      // of this cursor should end by unlocking that lock. We must also
      // unlock it when throwing an exception.
      dbCloseLock.readLock().lock();

      Cursor localCursor = null;
      try
      {
        // If the DB has been closed then create empty cursor.
        if (isDBClosed())
        {
          isClosed = true;
          cursor = null;
          return;
        }

        localCursor = db.openCursor(txn, null);
        if (startingChangeNumber != null)
        {
          if (localCursor.getSearchKey(key, data, LockMode.DEFAULT) !=
            OperationStatus.SUCCESS)
          {
            // We could not move the cursor to the expected startingChangeNumber
            if (localCursor.getSearchKeyRange(key, data, LockMode.DEFAULT) !=
              OperationStatus.SUCCESS)
            {
              // We could not even move the cursor closed to it => failure
              throw new Exception("ChangeNumber not available");
            }
            else
            {
              // We can move close to the startingChangeNumber.
              // Let's create a cursor from that point.
              DatabaseEntry aKey = new DatabaseEntry();
              DatabaseEntry aData = new DatabaseEntry();
              if (localCursor.getPrev(aKey, aData, LockMode.DEFAULT) !=
                OperationStatus.SUCCESS)
              {
                localCursor.close();
                localCursor = db.openCursor(txn, null);
              }
            }
          }
        }
        cursor = localCursor;
      }
      catch (Exception e)
      {
        // Unlocking is required before throwing any exception
        closeLockedCursor(localCursor);
        throw e;
      }
    }

    private ReplServerDBCursor() throws Exception
    {
      key = new DatabaseEntry();
      data = new DatabaseEntry();

      // We'll go on only if no close or no clear is running
      dbCloseLock.readLock().lock();

      Transaction localTxn = null;
      Cursor localCursor = null;
      try
      {
        // If the DB has been closed then create empty cursor.
        if (isDBClosed())
        {
          isClosed = true;
          txn = null;
          cursor = null;
          return;
        }

        // Create the transaction that will protect whatever done with this
        // write cursor.
        localTxn = dbenv.beginTransaction();
        localCursor = db.openCursor(localTxn, null);

        txn = localTxn;
        cursor = localCursor;
      }
      catch (Exception e)
      {
        closeLockedCursor(localCursor);

        if (localTxn != null)
        {
          try
          {
            localTxn.abort();
          }
          catch (DatabaseException ignore)
          {
            // Ignore.
          }
        }
        throw e;
      }
    }

    /**
     * Close the ReplicationServer Cursor.
     */
    public void close()
    {
      synchronized (this)
      {
        if (isClosed)
        {
          return;
        }
        isClosed = true;
      }

      closeLockedCursor(cursor);
      if (txn != null)
      {
        try
        {
          // No need for durability when purging.
          txn.commit(Durability.COMMIT_NO_SYNC);
        }
        catch (DatabaseException e)
        {
          replicationServer.handleUnexpectedDatabaseException(e);
        }
      }
    }

    /**
     * Abort the Cursor after a Deadlock Exception.
     * This method catch and ignore the DeadlockException because
     * this must be done when aborting a cursor after a DeadlockException
     * (per the Cursor documentation).
     * This should not be used in any other case.
     */
    public void abort()
    {
      synchronized (this)
      {
        if (isClosed)
        {
          return;
        }
        isClosed = true;
      }

      closeLockedCursor(cursor);

      if (txn != null)
      {
        try
        {
          txn.abort();
        }
        catch (DatabaseException e)
        {
          replicationServer.handleUnexpectedDatabaseException(e);
        }
      }
    }

    /**
     * Get the next ChangeNumber in the database from this Cursor.
     *
     * @return The next ChangeNumber in the database from this cursor.
     * @throws DatabaseException In case of underlying database problem.
     */
    public ChangeNumber nextChangeNumber() throws DatabaseException
    {
      if (isClosed)
      {
        return null;
      }

      OperationStatus status = cursor.getNext(key, data, LockMode.DEFAULT);

      if (status != OperationStatus.SUCCESS)
      {
        return null;
      }
      String csnString = decodeUTF8(key.getData());
      return new ChangeNumber(csnString);
    }

    /**
     * Get the next UpdateMsg from this cursor.
     *
     * @return the next UpdateMsg.
     */
    public UpdateMsg next()
    {
      if (isClosed)
      {
        return null;
      }

      UpdateMsg currentChange = null;
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

        ChangeNumber cn = null;
        try
        {
          cn = new ChangeNumber(
              decodeUTF8(key.getData()));
          if (ReplicationDB.isaCounter(cn))
          {
            // counter record
            continue;
          }
          currentChange = ReplicationData.generateChange(data
              .getData());
        }
        catch (Exception e)
        {
          /*
           * An error happening trying to convert the data from the
           * replicationServer database to an Update Message. This can only
           * happen if the database is corrupted. There is not much more that we
           * can do at this point except trying to continue with the next
           * record. In such case, it is therefore possible that we miss some
           * changes.
           * TODO : This should be handled by the repair functionality.
           */
          Message message = ERR_REPLICATIONDB_CANNOT_PROCESS_CHANGE_RECORD
              .get(replicationServer.getServerId(),
                  (cn == null ? "" : cn.toString()),
                  e.getMessage());
          logError(message);
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
      if (isClosed)
      {
        throw new IllegalStateException("ReplServerDBCursor already closed");
      }

      cursor.delete();
    }
  } // ReplServerDBCursor

  /**
   * Clears this change DB from the changes it contains.
   *
   * @throws Exception Throws an exception it occurs.
   * @throws DatabaseException Throws a DatabaseException when it occurs.
   */
  public void clear() throws Exception, DatabaseException
  {
    // The coming users will be blocked until the clear is done
    dbCloseLock.writeLock().lock();
    try
    {
      // If the DB has been closed then return immediately.
      if (isDBClosed())
      {
        return;
      }

      String dbName = db.getDatabaseName();

      // Clears the reference to this serverID
      dbenv.clearServerId(baseDn, serverId);

      // Closing is requested by the Berkeley DB before truncate
      db.close();
      db = null; // In case there's a failure between here and recreation.

      // Clears the changes
      dbenv.clearDb(dbName);

      // RE-create the db
      db = dbenv.getOrAddDb(serverId, baseDn, (long)-1);
    }
    catch(Exception e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_ERROR_CLEARING_DB.get(this.toString(),
          e.getMessage() + " " +
          stackTraceToSingleLineString(e)));
      logError(mb.toMessage());
    }
    finally
    {
      // Relax the waiting users
      dbCloseLock.writeLock().unlock();
    }
  }
  /**
   * Count the number of changes between 2 changes numbers (inclusive).
   * @param start The lower limit of the count.
   * @param stop The higher limit of the count.
   * @return The number of changes between provided start and stop changeNumber.
   * Returns 0 when an error occurs.
   */
  public int count(ChangeNumber start, ChangeNumber stop)
  {
    int counterRecord1 = 0;
    int counterRecord2 = 0;
    int distToCounterRecord1 = 0;
    int distBackToCounterRecord2 = 0;
    int count=0;
    OperationStatus status;

    try
    {
      dbCloseLock.readLock().lock();

      Cursor cursor = null;
      try
      {
        // If the DB has been closed then return immediately.
        if (isDBClosed())
        {
          return 0;
        }

        ChangeNumber cn ;

        if ((start==null)&&(stop==null))
          return (int)db.count();

        // Step 1 : from the start point, traverse db to the next counter record
        // or to the stop point.
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        cursor = db.openCursor(null, null);
        if (start != null)
        {
          key = new ReplicationKey(start);
          status = cursor.getSearchKey(key, data, LockMode.DEFAULT);
          if (status == OperationStatus.NOTFOUND)
            status = cursor.getSearchKeyRange(key, data, LockMode.DEFAULT);
        }
        else
        {
          status = cursor.getNext(key, data, LockMode.DEFAULT);
        }

        while (status == OperationStatus.SUCCESS)
        {
          // test whether the record is a regular change or a counter
          String csnString = decodeUTF8(key.getData());
          cn = new ChangeNumber(csnString);
          if (cn.getServerId() != 0)
          {
            // reached a regular change record
            // test whether we reached the 'stop' target
            if (!cn.newer(stop))
            {
              // let's loop
              distToCounterRecord1++;
              status = cursor.getNext(key, data, LockMode.DEFAULT);
            }
            else
            {
              // reached the end
              break;
            }
          }
          else
          {
            // counter record
            counterRecord1 = decodeCounterValue(data.getData());
            break;
          }
        }
        cursor.close();

        // cases
        //
        if (counterRecord1==0)
          return distToCounterRecord1;

        // Step 2 : from the stop point, traverse db to the next counter record
        // or to the start point.
        data = new DatabaseEntry();
        key = new ReplicationKey(stop);
        cursor = db.openCursor(null, null);
        status = cursor.getSearchKey(key, data, LockMode.DEFAULT);
        if (status == OperationStatus.SUCCESS)
        {
          cn = new ChangeNumber(decodeUTF8(key.getData()));
        }
        else
        {
          key = new DatabaseEntry();
          data = new DatabaseEntry();
          status = cursor.getLast(key, data, LockMode.DEFAULT);
          if (status != OperationStatus.SUCCESS)
          {
            /* database is empty */
            return 0;
          }
        }
        while (status == OperationStatus.SUCCESS)
        {
          cn = new ChangeNumber(decodeUTF8(key.getData()));
          if (!ReplicationDB.isaCounter(cn))
          {
            // regular change record
            if (!cn.older(start))
            {
              distBackToCounterRecord2++;
              status = cursor.getPrev(key, data, LockMode.DEFAULT);
            }
            else
              break;
          }
          else
          {
            // counter record
            counterRecord2 = decodeCounterValue(data.getData());
            break;
          }
        }
        cursor.close();

        // Step 3 : Now consolidates the result
        if (counterRecord1!=0)
        {
          if (counterRecord1 == counterRecord2)
          {
            // only one cp between from and to - no need to use it
            count = distToCounterRecord1 + distBackToCounterRecord2;
          }
          else
          {
            // 2 cp between from and to
            count = distToCounterRecord1 + (counterRecord2-counterRecord1)
            + distBackToCounterRecord2;
          }
        }
      }
      finally
      {
        closeLockedCursor(cursor);
      }
    }
    catch (DatabaseException e)
    {
      replicationServer.handleUnexpectedDatabaseException(e);
    }
    return count;
  }

  /**
   * Test if a provided changeNumber represents a counter record.
   * @param cn The provided changeNumber.
   * @return True if the provided changenumber is a counter.
   */
  static private boolean isaCounter(ChangeNumber cn)
  {
    return ((cn.getServerId()== 0) && (cn.getSeqnum()==0));
  }

  /**
   * Decode the provided database entry as a the value of a counter.
   * @param entry The provided entry.
   * @return The counter value.
   */
  private static int decodeCounterValue(byte[] entry)
  {
    String numAckStr = decodeUTF8(entry);
    return Integer.parseInt(numAckStr);
  }

  /**
   * Encode the provided counter value in a database entry.
   * @return The database entry with the counter value encoded inside.
   */
  static private DatabaseEntry encodeCounterValue(int value)
  {
    DatabaseEntry entry = new DatabaseEntry();
    entry.setData(getBytes(String.valueOf(value)));
    return entry;
  }

  /**
   * Set the counter writing window size (public method for unit tests only).
   * @param size Size in number of record.
   */
  public void setCounterWindowSize(int size)
  {
    this.counterWindowSize = size;
  }



  // Returns {@code true} if the DB is closed. This method assumes that either
  // the db read/write lock has been taken.
  private boolean isDBClosed()
  {
    return db == null;
  }

}
