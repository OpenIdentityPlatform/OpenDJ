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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.opends.messages.MessageBuilder;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.DebugLogLevel;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

/**
 * This class implements the interface between the underlying database
 * and the dbHandler class.
 * This is the only class that should have code using the BDB interfaces.
 */
public class DraftCNDB
{
  private static final DebugTracer TRACER = getTracer();
  private Database db = null;
  private ReplicationDbEnv dbenv = null;
  private ReplicationServer replicationServer;

  // The maximum number of retries in case of DatabaseDeadlock Exception.
  private static final int DEADLOCK_RETRIES = 10;

  // The lock used to provide exclusive access to the thread that
  // close the db (shutdown or clear).
  private ReentrantReadWriteLock dbCloseLock;

  /**
   * Creates a new database or open existing database that will be used
   * to store and retrieve changes from an LDAP server.
   * @param replicationServer The ReplicationServer that needs to be shutdown.
   * @param dbenv The Db environment to use to create the db.
   * @throws DatabaseException If a database problem happened.
   */
  public DraftCNDB(
      ReplicationServer replicationServer,
      ReplicationDbEnv dbenv)
  throws DatabaseException
  {
    this.dbenv = dbenv;
    this.replicationServer = replicationServer;

    // Get or create the associated ReplicationServerDomain and Db.
    db = dbenv.getOrCreateDraftCNDb();

    dbCloseLock = new ReentrantReadWriteLock(true);
  }

  /**
   * Add an entry to the database.
   * @param draftCN      the provided draftCN.
   *
   * @param value        the provided value to be stored associated
   *                     with this draftCN.
   * @param domainBaseDN the provided domainBaseDn to be stored associated
   *                     with this draftCN.
   * @param changeNumber the provided replication change number to be
   *                     stored associated with this draftCN.
   */
  public void addEntry(int draftCN, String value, String domainBaseDN,
      ChangeNumber changeNumber)
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

          DatabaseEntry key = new ReplicationDraftCNKey(draftCN);
          DatabaseEntry data = new DraftCNData(draftCN,
              value, domainBaseDN, changeNumber);
          db.put(txn, key, data);
          txn.commitWriteNoSync();
          txn = null;
          done = true;
        }
        catch (DeadlockException e)
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
   * Create a cursor that can be used to search or iterate on this DB.
   *
   * @param draftCN The draftCN from which the cursor must start.
   * @throws DatabaseException If a database error prevented the cursor
   *                           creation.
   * @throws Exception if the ReplServerDBCursor creation failed.
   * @return The ReplServerDBCursor.
   */
  public DraftCNDBCursor openReadCursor(int draftCN)
  throws DatabaseException, Exception
  {
    return new DraftCNDBCursor(draftCN);
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
  public DraftCNDBCursor openDeleteCursor()
  throws DatabaseException, Exception
  {
    return new DraftCNDBCursor();
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
   * Read the first Change from the database, 0 when none.
   * @return the first ChangeNumber.
   */
  public int readFirstSeqnum()
  {
    Cursor cursor = null;
    String str = null;

    try
    {
      dbCloseLock.readLock().lock();
      cursor = db.openCursor(null, null);
    }
    catch (DatabaseException e1)
    {
      dbCloseLock.readLock().unlock();
      return 0;
    }
    try
    {
      try
      {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry entry = new DatabaseEntry();
        OperationStatus status = cursor.getFirst(key, entry, LockMode.DEFAULT);
        if (status != OperationStatus.SUCCESS)
        {
          /* database is empty */
          return 0;
        }
        try
        {
          str = new String(key.getData(), "UTF-8");
        } catch (UnsupportedEncodingException e)
        {
          // never happens
        }
        int sn = new Integer(str);
        return sn;
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
      return 0;
    }
    catch (Exception e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
      replicationServer.shutdown();
      return 0;
    }
  }

  /**
   * Return the record count.
   * @return the record count.
   */
  public long count()
  {
    try
    {
      return db.count();
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
    return 0L;
  }

  /**
   * Read the last draftCN from the database.
   * @return the last draftCN.
   */
  public int readLastDraftCN()
  {
    Cursor cursor = null;
    String str = null;

    try
    {
      dbCloseLock.readLock().lock();
      try
      {
        cursor = db.openCursor(null, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry entry = new DatabaseEntry();
        OperationStatus status = cursor.getLast(key, entry, LockMode.DEFAULT);
        if (status != OperationStatus.SUCCESS)
        {
          /* database is empty */
          return 0;
        }
        try
        {
          str = new String(key.getData(), "UTF-8");
        } catch (UnsupportedEncodingException e)
        {
          // never happens
        }
        int sn = new Integer(str);
        return sn;
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
      return 0;
    }
    catch (Exception e)
    {
      replicationServer.shutdown();
      return 0;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "DraftCNDB";
  }

  /**
   * This Class implements a cursor that can be used to browse the database.
   */
  public class DraftCNDBCursor
  {
    private Cursor cursor = null;

    // The transaction that will protect the actions done with the cursor
    // Will be let null for a read cursor
    // Will be set non null for a write cursor
    private Transaction txn = null;
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry entry = new DatabaseEntry();

    /**
     * Creates a cursor that can be used for browsing the db.
     *
     * @param startingDraftCN the draftCN from which the cursor must
     *                        start.
     * @throws Exception      when the startingDraftCN does not exist.
     */
    private DraftCNDBCursor(int startingDraftCN) throws Exception
    {
      try
      {
        // Take the lock. From now on, whatever error that happen in the life
        // of this cursor should end by unlocking that lock. We must also
        // unlock it when throwing an exception.
        dbCloseLock.readLock().lock();

        cursor = db.openCursor(txn, null);
        if (startingDraftCN >= 0)
        {
          key = new ReplicationDraftCNKey(startingDraftCN);
          entry = new DatabaseEntry();

          if (cursor.getSearchKey(key, entry, LockMode.DEFAULT) !=
            OperationStatus.SUCCESS)
          {
            // We could not move the cursor to the expected startingChangeNumber
            if (cursor.getSearchKeyRange(key, entry, LockMode.DEFAULT) !=
              OperationStatus.SUCCESS)
            {
              // We could not even move the cursor closed to it => failure
              throw new Exception("ChangeLog Draft Change Number " +
                  startingDraftCN + " is not available");
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
          else
          {
            // success : key has the right value
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

    private DraftCNDBCursor() throws DatabaseException
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);

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
      catch (DeadlockException e1)
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
     * Getter for the value field of the current cursor.
     * @return The current value field.
     * @throws DatabaseException When an error happens.
     */
    public String currentValue() throws DatabaseException
    {
      try
      {
        OperationStatus status =
          cursor.getCurrent(key, entry, LockMode.DEFAULT);

        if (status != OperationStatus.SUCCESS)
        {
          return null;
        }
        DraftCNData seqnumData = new DraftCNData(entry.getData());
        return seqnumData.getValue();
      }
      catch(Exception e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return null;
    }

    /**
     * Getter for the serviceID field of the current cursor.
     * @return The current serviceID.
     * @throws DatabaseException When an error happens.
     */
    public String currentServiceID() throws DatabaseException
    {
      try
      {
        OperationStatus status =
          cursor.getCurrent(key, entry, LockMode.DEFAULT);

        if (status != OperationStatus.SUCCESS)
        {
          return null;
        }
        DraftCNData seqnumData = new DraftCNData(entry.getData());
        return seqnumData.getServiceID();
      }
      catch(Exception e)
      {

      }
      return null;
    }

    /**
     * Returns the replication changeNumber associated with the current key.
     * @return the replication changeNumber
     * @throws DatabaseException when a problem occurs.
     */
    public ChangeNumber currentChangeNumber() throws DatabaseException
    {
      try
      {
        OperationStatus status =
          cursor.getCurrent(key, entry, LockMode.DEFAULT);

        if (status != OperationStatus.SUCCESS)
        {
          return null;
        }
        DraftCNData seqnumData =
          new DraftCNData(entry.getData());
        return seqnumData.getChangeNumber();
      }
      catch(Exception e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return null;
    }

    /**
     * Go to the next record on the cursor.
     * @return the next record on this cursor.
     * @throws DatabaseException a.
     */
    public boolean next() throws DatabaseException
    {
      OperationStatus status = cursor.getNext(key, entry, LockMode.DEFAULT);
      if (status != OperationStatus.SUCCESS)
      {
        return false;
      }
      return true;
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

      // Closing is requested by the Berkeley DB before truncate
      db.close();

      // Clears the changes
      dbenv.clearDb(dbName);

      // RE-create the db
      db = dbenv.getOrCreateDraftCNDb();
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
}
