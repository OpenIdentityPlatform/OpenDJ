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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;
import org.opends.messages.MessageBuilder;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.List;
import java.io.UnsupportedEncodingException;

import org.opends.server.types.DN;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMessage;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Database;
import com.sleepycat.je.DeadlockException;
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
  private Short serverId;
  private DN baseDn;

  // The maximum number of retries in case of DatabaseDeadlock Exception.
  private static final int DEADLOCK_RETRIES = 10;

  // The lock used to provide exclusive access to the thread that
  // close the db (shutdown or clear).
  private ReentrantReadWriteLock dbCloseLock;

 /**
   * Creates a new database or open existing database that will be used
   * to store and retrieve changes from an LDAP server.
   * @param serverId The identifier of the LDAP server.
   * @param baseDn The baseDn of the replication domain.
   * @param replicationServer The ReplicationServer that needs to be shutdown.
   * @param dbenv The Db environment to use to create the db.
   * @throws DatabaseException If a database problem happened.
   */
  public ReplicationDB(Short serverId, DN baseDn,
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

          for (UpdateMessage change : changes)
          {
            DatabaseEntry key = new ReplicationKey(change.getChangeNumber());
            DatabaseEntry data = new ReplicationData(change);
            db.put(txn, key, data);
          }

          txn.commitWriteNoSync();
          txn = null;
          done = true;
        }
        catch (DeadlockException e)
        {
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
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
      cursor.close();
      dbCloseLock.readLock().unlock();
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
      try
      {
        cursor.close();
        dbCloseLock.readLock().unlock();
      }
      catch (DatabaseException dbe)
      {
        // The db is dead - let's only log.
      }
      /* database is faulty */
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
      replicationServer.shutdown();
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
      dbCloseLock.readLock().lock();
      cursor = db.openCursor(null, null);
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      OperationStatus status = cursor.getLast(key, data, LockMode.DEFAULT);
      cursor.close();
      dbCloseLock.readLock().unlock();
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
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
      replicationServer.shutdown();
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
   * This Class implements a cursor that can be used to browse a
   * replicationServer database.
   */
  public class ReplServerDBCursor
  {
    private Cursor cursor = null;
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
              // Unlocking is required before throwing any exception
              dbCloseLock.readLock().unlock();
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
                try
                {
                  cursor.close();
                  cursor = db.openCursor(txn, null);
                }
                catch(Exception e)
                {
                  // Unlocking is required before throwing any exception
                  dbCloseLock.readLock().unlock();
                  throw(e);
                }
              }
            }
          }
        }
      }
      catch (Exception e)
      {
        // Unlocking is required before throwing any exception
        dbCloseLock.readLock().unlock();
        cursor.close();
        throw (e);
      }
    }

    private ReplServerDBCursor() throws DatabaseException
    {
      try
      {
        // We'll go on only if no close or no clear is running
        dbCloseLock.readLock().lock();
        txn = dbenv.beginTransaction();
        cursor = db.openCursor(txn, null);
      }
      catch(DatabaseException e)
      {
        dbCloseLock.readLock().unlock();
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
        if (cursor != null)
        {
          cursor.close();
          cursor = null;
        }
      }
      catch (DatabaseException e)
      {
        dbCloseLock.readLock().unlock();

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
      dbCloseLock.readLock().unlock();
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
        cursor.close();
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
        dbCloseLock.readLock().unlock();

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
      dbCloseLock.readLock().unlock();
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

      db = null;

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
}
