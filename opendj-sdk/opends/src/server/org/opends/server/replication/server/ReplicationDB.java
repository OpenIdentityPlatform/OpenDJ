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
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.server;
import org.opends.messages.MessageBuilder;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.List;
import java.io.UnsupportedEncodingException;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMsg;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.DataFormatException;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Database;
import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

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

  // The maximum number of retries in case of DatabaseDeadlock Exception.
  private static final int DEADLOCK_RETRIES = 10;

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
  // A counter record must have its own chagenumber key since the Db does not
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
    Cursor cursor = null;
    Transaction txn = null;
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    OperationStatus status;
    int distBackToCounterRecord = 0;

    // Initialize counter
    this.counterCurrValue = 1;
    cursor = db.openCursor(txn, null);
    status = cursor.getLast(key, data, LockMode.DEFAULT);
    while (status == OperationStatus.SUCCESS)
    {
      try
      {
        ChangeNumber cn =new ChangeNumber(new String(key.getData(), "UTF-8"));
        if (!ReplicationDB.isaCounter(cn))
        {
          status = cursor.getPrev(key, data, LockMode.DEFAULT);
          distBackToCounterRecord++;
        }
        else
        {
          // counter record
          counterCurrValue = decodeCounterValue(data.getData())+1;
          counterTsLimit = cn.getTime();
          break;
        }
      }
      catch (UnsupportedEncodingException e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_CHANGELOG_UNSUPPORTED_UTF8_ENCODING.get());
        mb.append(stackTraceToSingleLineString(e));
        logError(mb.toMessage());
        replicationServer.shutdown();
        if (txn != null)
        {
          try
          {
            txn.abort();
          } catch (DatabaseException e1)
          {
            // can't do much more. The ReplicationServer is shuting down.
          }
        }
        replicationServer.shutdown();
      }
      catch (DataFormatException e)
      {
        // Should never happen
      }
    }
    counterCurrValue += distBackToCounterRecord;
    cursor.close();

  }

  /**
   * add a list of changes to the underlying db.
   *
   * @param changes The list of changes to add to the underlying db.
   */
  public void addEntries(List<UpdateMsg> changes)
  {
    Transaction txn = null;

    try
    {
      int tries = 0;
      boolean done = false;

      // The database can return a Deadlock Exception if several threads are
      // accessing the database at the same time. This Exception is a
      // transient state, when it happens the transaction is aborted and
      // the operation is attempted again up to DEADLOCK_RETRIES times.
      while ((tries++ < DEADLOCK_RETRIES) && (!done))
      {
        dbCloseLock.readLock().lock();
        try
        {
          txn = dbenv.beginTransaction();

          for (UpdateMsg change : changes)
          {
            DatabaseEntry key = new ReplicationKey(change.getChangeNumber());
            DatabaseEntry data = new ReplicationData(change);

            if ((counterCurrValue!=0) &&
                (counterCurrValue%counterWindowSize == 0))
            {
              // enough changes to generate a counter record - wait for the next
              // change fo time
              counterTsLimit = change.getChangeNumber().getTime();
            }
            if ((counterTsLimit!=0)
                && (change.getChangeNumber().getTime() != counterTsLimit))
            {
              // Write the counter record
              DatabaseEntry counterKey = new ReplicationKey(
                  new ChangeNumber(
                  change.getChangeNumber().getTime(),
                  0, 0));
              DatabaseEntry counterValue =
                encodeCounterValue(counterCurrValue-1);
              db.put(txn, counterKey, counterValue);
              counterTsLimit=0;
            }
            db.put(txn, key, data);
            counterCurrValue++;

          }
          txn.commitWriteNoSync();
          txn = null;
          done = true;
        }
        catch (LockConflictException e)
        {
          if (txn != null)
            txn.abort();
          txn = null;
        }
        finally
        {
          dbCloseLock.readLock().unlock();
        }
      }
      if (!done)
      {
        // Could not write to the DB after DEADLOCK_RETRIES tries.
        // This ReplicationServer is not reliable and will be shutdown.
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
        logError(mb.toMessage());
        if (txn != null)
        {
           txn.abort();
        }
        replicationServer.shutdown();
      }
    }
    catch (DatabaseException e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
      if (txn != null)
      {
        try
        {
          txn.abort();
        } catch (DatabaseException e1)
        {
          // can't do much more. The ReplicationServer is shuting down.
        }
      }
      replicationServer.shutdown();
    }
    catch (UnsupportedEncodingException e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CHANGELOG_UNSUPPORTED_UTF8_ENCODING.get());
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
      replicationServer.shutdown();
      if (txn != null)
      {
        try
        {
          txn.abort();
        } catch (DatabaseException e1)
        {
          // can't do much more. The ReplicationServer is shuting down.
        }
      }
      replicationServer.shutdown();
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
        cursor.close();
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
    String str = null;
    ChangeNumber cn = null;

    try
    {
      dbCloseLock.readLock().lock();
      cursor = db.openCursor(null, null);
    }
    catch (DatabaseException e1)
    {
      dbCloseLock.readLock().unlock();
      return null;
    }
    try
    {
      try
      {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
        if (status != OperationStatus.SUCCESS)
        {
          /* database is empty */
          return null;
        }
        try
        {
          str = new String(key.getData(), "UTF-8");
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
              cn = new ChangeNumber(new String(key.getData(), "UTF-8"));
            }
          }
        } catch (UnsupportedEncodingException e)
        {
          // never happens
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
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
      replicationServer.shutdown();
      cn = null;
    }
    return cn;
  }

  /**
   * Read the last Change from the database.
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
        cursor = db.openCursor(null, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status = cursor.getLast(key, data, LockMode.DEFAULT);
        if (status != OperationStatus.SUCCESS)
        {
          /* database is empty */
          return null;
        }
        try
        {
          String str = new String(key.getData(), "UTF-8");
          cn = new ChangeNumber(str);
          if (ReplicationDB.isaCounter(cn))
          {
            if (cursor.getPrev(key, data, LockMode.DEFAULT) !=
              OperationStatus.SUCCESS)
            {
              /* database only contain a counter record - don't know
               * how much it can be possible but ... */
              cn = null;
            }
          }
        }
        catch (UnsupportedEncodingException e)
        {
          // never happens
        }
      }
      finally
      {
        closeLockedCursor(cursor);
      }
    }
    catch (DatabaseException e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
      replicationServer.shutdown();
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
    return serverId + baseDn.toString();
  }

  /**
   * This Class implements a cursor that can be used to browse a
   * replicationServer database.
   */
  public class ReplServerDBCursor
  {
    private Cursor cursor = null;

    // The transaction that will protect the actions done with the cursor
    // Will be let null for a read cursor
    // Will be set non null for a write cursor
    private Transaction txn = null;
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();

    /**
     * Creates a ReplServerDBCursor that can be used for browsing a
     * replicationServer db.
     *
     * @param startingChangeNumber The ChangeNumber from which the cursor must
     *        start.
     * @throws Exception When the startingChangeNumber does not exist.
     */
    private ReplServerDBCursor(ChangeNumber startingChangeNumber)
            throws Exception
    {
      try
      {
        // Take the lock. From now on, whatever error that happen in the life
        // of this cursor should end by unlocking that lock. We must also
        // unlock it when throwing an exception.
        dbCloseLock.readLock().lock();

        cursor = db.openCursor(txn, null);
        if (startingChangeNumber != null)
        {
          key = new ReplicationKey(startingChangeNumber);
          data = new DatabaseEntry();

          if (cursor.getSearchKey(key, data, LockMode.DEFAULT) !=
            OperationStatus.SUCCESS)
          {
            // We could not move the cursor to the expected startingChangeNumber
            if (cursor.getSearchKeyRange(key, data, LockMode.DEFAULT) !=
              OperationStatus.SUCCESS)
            {
              // We could not even move the cursor closed to it => failure
              throw new Exception("ChangeNumber not available");
            }
            else
            {
              // We can move close to the startingChangeNumber.
              // Let's create a cursor from that point.
              DatabaseEntry key = new DatabaseEntry();
              DatabaseEntry data = new DatabaseEntry();
              if (cursor.getPrev(key, data, LockMode.DEFAULT) !=
                OperationStatus.SUCCESS)
              {
                closeLockedCursor(cursor);
                dbCloseLock.readLock().lock();
                cursor = db.openCursor(txn, null);
              }
            }
          }
        }
      }
      catch (Exception e)
      {
       // Unlocking is required before throwing any exception
        closeLockedCursor(cursor);
        throw (e);
      }
    }

    private ReplServerDBCursor() throws DatabaseException
    {
      try
      {
        // We'll go on only if no close or no clear is running
        dbCloseLock.readLock().lock();

        // Create the transaction that will protect whatever done with this
        // write cursor.
        txn = dbenv.beginTransaction();

        cursor = db.openCursor(txn, null);
      }
      catch(DatabaseException e)
      {
        if (txn != null)
        {
          try
          {
            txn.abort();
          }
          catch (DatabaseException dbe)
          {}
        }
        closeLockedCursor(cursor);
        throw (e);
      }
    }

    /**
     * Close the ReplicationServer Cursor.
     */
    public void close()
    {
      try
      {
        closeLockedCursor(cursor);
        cursor = null;
      }
      catch (DatabaseException e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
        mb.append(stackTraceToSingleLineString(e));
        logError(mb.toMessage());
        replicationServer.shutdown();
      }
      if (txn != null)
      {
        try
        {
          txn.commit();
        } catch (DatabaseException e)
        {
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
          mb.append(stackTraceToSingleLineString(e));
          logError(mb.toMessage());
          replicationServer.shutdown();
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
      if (cursor == null)
        return;
      try
      {
        closeLockedCursor(cursor);
        cursor = null;
      }
      catch (LockConflictException e1)
      {
        // The DB documentation states that a DeadlockException
        // on the close method of a cursor that is aborting should
        // be ignored.
      }
      catch (DatabaseException e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
        mb.append(stackTraceToSingleLineString(e));
        logError(mb.toMessage());
        replicationServer.shutdown();
      }
      if (txn != null)
      {
        try
        {
          txn.abort();
        } catch (DatabaseException e)
        {
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
          mb.append(stackTraceToSingleLineString(e));
          logError(mb.toMessage());
          replicationServer.shutdown();
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
     * Get the next UpdateMsg from this cursor.
     *
     * @return the next UpdateMsg.
     */
    public UpdateMsg next()
    {
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
        try
        {
          ChangeNumber cn=new ChangeNumber(new String(key.getData(), "UTF-8"));
          if(ReplicationDB.isaCounter(cn))
          {
            // counter record
            continue;
          }
          currentChange = ReplicationData.generateChange(data.getData());
        } catch (Exception e) {
          /*
           * An error happening trying to convert the data from the
           * replicationServer database to an Update Message.
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
      String dbName = db.getDatabaseName();

      // Clears the reference to this serverID
      dbenv.clearServerId(baseDn, serverId);

      // Closing is requested by the Berkeley DB before truncate
      db.close();

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
   * Returns -1 when an error occurs.
   */
  public int count(ChangeNumber start, ChangeNumber stop)
  {
    int counterRecord1 = 0;
    int counterRecord2 = 0;
    int distToCounterRecord1 = 0;
    int distBackToCounterRecord2 = 0;
    int count=0;
    Cursor cursor = null;
    Transaction txn = null;
    OperationStatus status;
    try
    {
      ChangeNumber cn ;

      if ((start==null)&&(stop==null))
        return (int)db.count();

      // Step 1 : from the start point, traverse db to the next counter record
      // or to the stop point.
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      cursor = db.openCursor(txn, null);
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
        String csnString = new String(key.getData(), "UTF-8");
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
      txn = null;
      data = new DatabaseEntry();
      key = new ReplicationKey(stop);
      cursor = db.openCursor(txn, null);
      status = cursor.getSearchKey(key, data, LockMode.DEFAULT);
      if (status == OperationStatus.SUCCESS)
      {
        cn = new ChangeNumber(new String(key.getData(), "UTF-8"));
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
        cn = new ChangeNumber(new String(key.getData(), "UTF-8"));
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
    catch (UnsupportedEncodingException e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CHANGELOG_UNSUPPORTED_UTF8_ENCODING.get());
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
      replicationServer.shutdown();
    }
    catch (DataFormatException e)
    {
      // Should never happen
    }
    finally
    {
      if (cursor != null)
        cursor.close();
      if (txn != null)
      {
        try
        {
          txn.abort();
        } catch (DatabaseException e1)
        {
          // can't do much more. The ReplicationServer is shuting down.
        }
      }
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
   * @throws DataFormatException
   */
  private static int decodeCounterValue(byte[] entry)
  throws DataFormatException
  {
    try
    {
      String numAckStr = new String(entry, 0, entry.length, "UTF-8");
      return Integer.parseInt(numAckStr);

    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Encode the provided counter value in a database entry.
   * @param entry The provided entry.
   * @return The databse entry with the counter value encoded inside..
   * @throws UnsupportedEncodingException
   */
  static private DatabaseEntry encodeCounterValue(int value)
  throws UnsupportedEncodingException
  {
    DatabaseEntry entry = new DatabaseEntry();
    entry.setData(String.valueOf(value).getBytes("UTF-8"));
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

}
