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
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.Closeable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.DebugLogLevel;

import com.sleepycat.je.*;

import static com.sleepycat.je.LockMode.*;
import static com.sleepycat.je.OperationStatus.*;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements the interface between the underlying database
 * and the dbHandler class.
 * This is the only class that should have code using the BDB interfaces.
 */
public class DraftCNDB
{
  private static final DebugTracer TRACER = getTracer();
  private static final int DATABASE_EMPTY = 0;

  private Database db;
  private ReplicationDbEnv dbenv;

  /**
   * The lock used to provide exclusive access to the thread that close the db
   * (shutdown or clear).
   */
  private final ReadWriteLock dbCloseLock = new ReentrantReadWriteLock(true);

  /**
   * Creates a new database or open existing database that will be used
   * to store and retrieve changes from an LDAP server.
   * @param dbenv The Db environment to use to create the db.
   * @throws ChangelogException If a database problem happened.
   */
  public DraftCNDB(ReplicationDbEnv dbenv) throws ChangelogException
  {
    this.dbenv = dbenv;

    // Get or create the associated ReplicationServerDomain and Db.
    db = dbenv.getOrCreateDraftCNDb();
  }

  /**
   * Add an entry to the database.
   * @param changeNumber the provided change number.
   *
   * @param value        the provided value to be stored associated
   *                     with this change number.
   * @param domainBaseDN the provided domainBaseDn to be stored associated
   *                     with this change number.
   * @param csn the provided replication CSN to be
   *                     stored associated with this change number.
   */
  public void addEntry(long changeNumber, String value, String domainBaseDN,
      CSN csn)
  {
    try
    {
      DatabaseEntry key = new ReplicationDraftCNKey(changeNumber);
      DatabaseEntry data = new DraftCNData(value, domainBaseDN, csn);

      // Use a transaction so that we can override durability.
      Transaction txn = null;
      dbCloseLock.readLock().lock();
      try
      {
        // If the DB has been closed then return immediately.
        if (isDBClosed())
        {
          return;
        }

        txn = dbenv.beginTransaction();
        db.put(txn, key, data);
        txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
      }
      finally
      {
        abort(txn);
        dbCloseLock.readLock().unlock();
      }
    }
    catch (DatabaseException e)
    {
      dbenv.shutdownOnException(e);
    }
    catch (ChangelogException e)
    {
      dbenv.shutdownOnException(e);
    }
  }

  /**
   * Aborts the current transaction. It has no effect if the transaction has
   * committed.
   *
   * @param txn
   *          the transaction to abort
   */
  private static void abort(Transaction txn)
  {
    if (txn != null)
    {
      try
      {
        txn.abort();
      }
      catch (DatabaseException ignored)
      {
        // Ignore.
        TRACER.debugCaught(DebugLogLevel.ERROR, ignored);
      }
    }
  }

  /**
   * Shutdown the database.
   */
  public void shutdown()
  {
    dbCloseLock.writeLock().lock();
    try
    {
      db.close();
      db = null;
    }
    catch (DatabaseException e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(NOTE_EXCEPTION_CLOSING_DATABASE.get(toString()));
      mb.append(" ");
      mb.append(stackTraceToSingleLineString(e));
      logError(mb.toMessage());
    }
    finally
    {
      dbCloseLock.writeLock().unlock();
    }
  }

  /**
   * Create a cursor that can be used to search or iterate on this DB.
   *
   * @param changeNumber The change number from which the cursor must start.
   * @throws ChangelogException If a database error prevented the cursor
   *                           creation.
   * @return The ReplServerDBCursor.
   */
  public DraftCNDBCursor openReadCursor(long changeNumber)
      throws ChangelogException
  {
    return new DraftCNDBCursor(changeNumber);
  }

  /**
   * Create a cursor that can be used to delete some record from this
   * ReplicationServer database.
   *
   * @throws ChangelogException If a database error prevented the cursor
   *                           creation.
   * @return The ReplServerDBCursor.
   */
  public DraftCNDBCursor openDeleteCursor() throws ChangelogException
  {
    return new DraftCNDBCursor();
  }

  private void closeLockedCursor(Cursor cursor)
  {
    try
    {
      close(cursor);
    }
    finally
    {
      dbCloseLock.readLock().unlock();
    }
  }

  /**
   * Read the first Change from the database, 0 when none.
   * @return the first change number.
   */
  public int readFirstChangeNumber()
  {
    try
    {
      dbCloseLock.readLock().lock();
      Cursor cursor = null;
      try
      {
        // If the DB has been closed then return immediately.
        if (isDBClosed())
        {
          return DATABASE_EMPTY;
        }

        cursor = db.openCursor(null, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry entry = new DatabaseEntry();
        if (cursor.getFirst(key, entry, LockMode.DEFAULT) != SUCCESS)
        {
          return DATABASE_EMPTY;
        }

        return Integer.parseInt(decodeUTF8(key.getData()));
      }
      finally
      {
        closeLockedCursor(cursor);
      }
    }
    catch (DatabaseException e)
    {
      dbenv.shutdownOnException(e);
      return DATABASE_EMPTY;
    }
  }

  /**
   * Return the record count.
   * @return the record count.
   */
  public long count()
  {
    dbCloseLock.readLock().lock();
    try
    {
      // If the DB has been closed then return immediately.
      if (isDBClosed())
      {
        return DATABASE_EMPTY;
      }

      return db.count();
    }
    catch (Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
    finally
    {
      dbCloseLock.readLock().unlock();
    }
    return DATABASE_EMPTY;
  }

  /**
   * Read the last change number from the database.
   * @return the last change number.
   */
  public int readLastChangeNumber()
  {
    try
    {
      dbCloseLock.readLock().lock();
      Cursor cursor = null;
      try
      {
        // If the DB has been closed then return immediately.
        if (isDBClosed())
        {
          return DATABASE_EMPTY;
        }

        cursor = db.openCursor(null, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry entry = new DatabaseEntry();
        if (cursor.getLast(key, entry, LockMode.DEFAULT) != SUCCESS)
        {
          return DATABASE_EMPTY;
        }

        return Integer.parseInt(decodeUTF8(key.getData()));
      }
      finally
      {
        closeLockedCursor(cursor);
      }
    }
    catch (DatabaseException e)
    {
      dbenv.shutdownOnException(e);
      return DATABASE_EMPTY;
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
  public class DraftCNDBCursor implements Closeable
  {
    private final Cursor cursor;

    /**
     * The transaction that will protect the actions done with the cursor.
     * Will be let null for a read cursor.
     * Will be set non null for a write cursor.
     */
    private final Transaction txn;
    private final DatabaseEntry key;
    private final DatabaseEntry entry;
    private DraftCNData cnData;
    private boolean isClosed = false;


    /**
     * Creates a cursor that can be used for browsing the db.
     *
     * @param startChangeNumber
     *          the change number from which the cursor must start.
     * @throws ChangelogException
     *           when the startChangeNumber does not exist.
     */
    private DraftCNDBCursor(long startChangeNumber) throws ChangelogException
    {
      this.key = new ReplicationDraftCNKey(startChangeNumber);
      this.entry = new DatabaseEntry();

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
          txn = null;
          cursor = null;
          return;
        }

        localCursor = db.openCursor(null, null);
        if (startChangeNumber >= 0)
        {
          if (localCursor.getSearchKey(key, entry, LockMode.DEFAULT) != SUCCESS)
          {
            // We could not move the cursor to the expected startChangeNumber
            if (localCursor.getSearchKeyRange(key, entry, DEFAULT) != SUCCESS)
            {
              // We could not even move the cursor close to it => failure
              throw new ChangelogException(
                  Message.raw("ChangeLog Change Number " + startChangeNumber
                      + " is not available"));
            }

            if (localCursor.getPrev(key, entry, LockMode.DEFAULT) != SUCCESS)
            {
              localCursor.close();
              localCursor = db.openCursor(null, null);
            }
            else
            {
              cnData = new DraftCNData(entry.getData());
            }
          }
          else
          {
            cnData = new DraftCNData(entry.getData());
          }
        }

        this.txn = null;
        this.cursor = localCursor;
      }
      catch (DatabaseException e)
      {
        // Unlocking is required before throwing any exception
        closeLockedCursor(localCursor);
        throw new ChangelogException(e);
      }
      catch (ChangelogException e)
      {
        // Unlocking is required before throwing any exception
        closeLockedCursor(localCursor);
        throw e;
      }
    }



    private DraftCNDBCursor() throws ChangelogException
    {
      Transaction localTxn = null;
      Cursor localCursor = null;

      this.key = new DatabaseEntry();
      this.entry = new DatabaseEntry();

      // We'll go on only if no close or no clear is running
      dbCloseLock.readLock().lock();
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

        this.txn = localTxn;
        this.cursor = localCursor;
      }
      catch (DatabaseException e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);

        closeLockedCursor(localCursor);
        DraftCNDB.abort(localTxn);
        throw new ChangelogException(e);
      }
      catch (ChangelogException e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);

        closeLockedCursor(localCursor);
        DraftCNDB.abort(localTxn);
        throw e;
      }
    }

    /**
     * Close the ReplicationServer Cursor.
     */
    @Override
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
          txn.commit();
        }
        catch (DatabaseException e)
        {
          dbenv.shutdownOnException(e);
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
          dbenv.shutdownOnException(e);
        }
      }
    }

    /**
     * Getter for the value field of the current cursor.
     * @return The current value field.
     */
    public String currentValue()
    {
      if (isClosed)
      {
        return null;
      }

      try
      {
        if (cnData != null)
        {
          return cnData.getValue();
        }
      }
      catch(Exception e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return null;
    }

    /**
     * Getter for the baseDN field of the current cursor.
     * @return The current baseDN.
     */
    public String currentBaseDN()
    {
      if (isClosed)
      {
        return null;
      }

      try
      {
        if (cnData != null)
        {
          return cnData.getBaseDN();
        }
      }
      catch(Exception e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return null;
    }

    /**
     * Getter for the integer value of the current cursor, representing
     * the current change number being processed.
     *
     * @return the current change number as an integer.
     */
    public int currentKey()
    {
      if (isClosed)
      {
        return -1;
      }

      try
      {
        String str = decodeUTF8(key.getData());
        return Integer.parseInt(str);
      }
      catch (Exception e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return -1;
    }

    /**
     * Returns the replication CSN associated with the current key.
     * @return the replication CSN
     */
    public CSN currentCSN()
    {
      if (isClosed)
      {
        return null;
      }

      try
      {
        if (cnData != null)
        {
          return cnData.getCSN();
        }
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
     * @throws ChangelogException a.
     */
    public boolean next() throws ChangelogException
    {
      if (isClosed)
      {
        return false;
      }

      try {
        OperationStatus status = cursor.getNext(key, entry, LockMode.DEFAULT);
        if (status != OperationStatus.SUCCESS)
        {
          cnData = null;
          return false;
        }
        cnData = new DraftCNData(entry.getData());
      }
      catch(Exception e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return true;
    }

    /**
     * Delete the record at the current cursor position.
     *
     * @throws ChangelogException In case of database problem.
     */
    public void delete() throws ChangelogException
    {
      if (isClosed)
      {
        throw new IllegalStateException("DraftCNDB already closed");
      }

      try
      {
        cursor.delete();
      }
      catch (DatabaseException e)
      {
        throw new ChangelogException(e);
      }
    }

    /**
     * Returns the current key associated with this cursor.
     *
     * @return The current key associated with this cursor.
     */
    public DatabaseEntry getKey()
    {
      if (isClosed)
      {
        return null;
      }

      return key;
    }
  }

  /**
   * Clears this change DB from the changes it contains.
   *
   * @throws ChangelogException Throws a DatabaseException when it occurs.
   */
  public void clear() throws ChangelogException
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

      // Closing is requested by the Berkeley DB before truncate
      db.close();
      db = null; // In case there's a failure between here and recreation.

      // Clears the changes
      dbenv.clearDb(dbName);

      // RE-create the db
      db = dbenv.getOrCreateDraftCNDb();
    }
    catch(Exception e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_ERROR_CLEARING_DB.get(toString(),
          e.getMessage() + " " + stackTraceToSingleLineString(e)));
      logError(mb.toMessage());
    }
    finally
    {
      // Relax the waiting users
      dbCloseLock.writeLock().unlock();
    }
  }

  /**
   * Returns {@code true} if the DB is closed. This method assumes that either
   * the db read/write lock has been taken.
   *
   * @return {@code true} if the DB is closed.
   */
  private boolean isDBClosed()
  {
    return db == null;
  }
}
