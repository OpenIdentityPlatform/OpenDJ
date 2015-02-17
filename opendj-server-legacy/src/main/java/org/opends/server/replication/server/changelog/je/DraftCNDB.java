/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import static com.sleepycat.je.LockMode.*;
import static com.sleepycat.je.OperationStatus.*;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Closeable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogException;

import com.sleepycat.je.*;

/**
 * This class implements the interface between the underlying database
 * and the {@link JEChangeNumberIndexDB} class.
 * This is the only class that should have code using the BDB interfaces.
 */
public class DraftCNDB
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
   * @throws ChangelogException If a database problem happened
   */
  public DraftCNDB(ReplicationDbEnv dbenv) throws ChangelogException
  {
    this.dbenv = dbenv;

    // Get or create the associated ReplicationServerDomain and Db.
    db = dbenv.getOrCreateCNIndexDB();
  }

  /**
   * Add a record to the database.
   *
   * @param record
   *          the provided {@link ChangeNumberIndexRecord} to be stored.
   * @throws ChangelogException
   *           If a database problem happened
   */
  public void addRecord(ChangeNumberIndexRecord record)
      throws ChangelogException
  {
    try
    {
      final long changeNumber = record.getChangeNumber();
      DatabaseEntry key = new ReplicationDraftCNKey(changeNumber);
      DatabaseEntry data = new DraftCNData(changeNumber, record.getBaseDN(), record.getCSN());

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
        JEUtils.abort(txn);
        dbCloseLock.readLock().unlock();
      }
    }
    catch (DatabaseException e)
    {
      throw new ChangelogException(e);
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
      logger.info(NOTE_EXCEPTION_CLOSING_DATABASE, this, stackTraceToSingleLineString(e));
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
   * @return The ReplServerDBCursor
   * @throws ChangelogException If a database error prevented the cursor
   *                           creation.
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
   * @return The ReplServerDBCursor
   * @throws ChangelogException If a database error prevented the cursor
   *                           creation.
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
   *
   * @return the first change number.
   * @throws ChangelogException
   *           if a database problem occurred
   */
  public ChangeNumberIndexRecord readFirstRecord() throws ChangelogException
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
          return null;
        }

        cursor = db.openCursor(null, null);
        ReplicationDraftCNKey key = new ReplicationDraftCNKey();
        DatabaseEntry entry = new DatabaseEntry();
        if (cursor.getFirst(key, entry, LockMode.DEFAULT) != SUCCESS)
        {
          return null;
        }

        return newCNIndexRecord(key, entry);
      }
      finally
      {
        closeLockedCursor(cursor);
      }
    }
    catch (DatabaseException e)
    {
      throw new ChangelogException(e);
    }
  }

  private ChangeNumberIndexRecord newCNIndexRecord(ReplicationDraftCNKey key,
      DatabaseEntry data) throws ChangelogException
  {
    return new DraftCNData(key.getChangeNumber(), data.getData()).getRecord();
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
        return 0;
      }

      return db.count();
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
    }
    finally
    {
      dbCloseLock.readLock().unlock();
    }
    return 0;
  }

  /**
   * Read the last change number from the database.
   *
   * @return the last change number.
   * @throws ChangelogException
   *           if a database problem occurred
   */
  public ChangeNumberIndexRecord readLastRecord() throws ChangelogException
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
          return null;
        }

        cursor = db.openCursor(null, null);
        ReplicationDraftCNKey key = new ReplicationDraftCNKey();
        DatabaseEntry entry = new DatabaseEntry();
        if (cursor.getLast(key, entry, LockMode.DEFAULT) != SUCCESS)
        {
          return null;
        }

        return newCNIndexRecord(key, entry);
      }
      finally
      {
        closeLockedCursor(cursor);
      }
    }
    catch (DatabaseException e)
    {
      throw new ChangelogException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName();
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
    private final ReplicationDraftCNKey key;
    private final DatabaseEntry entry = new DatabaseEntry();
    private ChangeNumberIndexRecord record;
    private boolean isClosed;


    /**
     * Creates a cursor that can be used for browsing the db.
     *
     * @param startChangeNumber
     *          the change number from which the cursor must start.
     * @throws ChangelogException
     *           If a database problem happened
     */
    private DraftCNDBCursor(long startChangeNumber) throws ChangelogException
    {
      this.key = new ReplicationDraftCNKey(startChangeNumber);

      // Take the lock. From now on, whatever error that happen in the life
      // of this cursor should end by unlocking that lock. We must also
      // unlock it when throwing an exception.
      dbCloseLock.readLock().lock();

      boolean cursorHeld = false;
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
              // We could not even move the cursor close to it
              // => return an empty cursor
              isClosed = true;
              txn = null;
              cursor = null;
              return;
            }

            if (localCursor.getPrev(key, entry, LockMode.DEFAULT) != SUCCESS)
            {
              localCursor.close();
              localCursor = db.openCursor(null, null);
            }
            else
            {
              record = newCNIndexRecord(this.key, entry);
            }
          }
          else
          {
            record = newCNIndexRecord(this.key, entry);
          }
        }

        this.txn = null;
        this.cursor = localCursor;
        cursorHeld = true;
      }
      catch (DatabaseException e)
      {
        throw new ChangelogException(e);
      }
      finally
      {
        if (!cursorHeld)
        {
          // Do not keep a readLock on the DB when this class does not hold a DB
          // cursor. Either an exception was thrown or no cursor could be opened
          // for some reason.
          closeLockedCursor(localCursor);
        }
      }
    }

    private DraftCNDBCursor() throws ChangelogException
    {
      Transaction localTxn = null;
      Cursor localCursor = null;

      this.key = new ReplicationDraftCNKey();

      // We'll go on only if no close or no clear is running
      boolean cursorHeld = false;
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
        cursorHeld = true;
      }
      catch (DatabaseException e)
      {
        logger.traceException(e);
        JEUtils.abort(localTxn);
        throw new ChangelogException(e);
      }
      catch (ChangelogException e)
      {
        logger.traceException(e);
        JEUtils.abort(localTxn);
        throw e;
      }
      finally
      {
        if (!cursorHeld)
        {
          // Do not keep a readLock on the DB when this class does not hold a DB
          // cursor. Either an exception was thrown or no cursor could be opened
          // for some reason.
          closeLockedCursor(localCursor);
        }
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
     * Abort the Cursor after a DatabaseException.
     * This method catch and ignore the DatabaseException because
     * this must be done when aborting a cursor after a DatabaseException
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
      JEUtils.abort(txn);
    }

    /**
     * Returns the {@link ChangeNumberIndexRecord} at the current position of
     * the cursor.
     *
     * @return The current {@link ChangeNumberIndexRecord}.
     */
    public ChangeNumberIndexRecord currentRecord()
    {
      if (isClosed)
      {
        return null;
      }
      return record;
    }

    /**
     * Go to the next record on the cursor.
     *
     * @return the next record on this cursor.
     * @throws ChangelogException
     *           If a database problem happened
     */
    public boolean next() throws ChangelogException
    {
      // first wipe old entry
      record = null;
      if (isClosed)
      {
        return false;
      }

      try {
        OperationStatus status = cursor.getNext(key, entry, LockMode.DEFAULT);
        if (status == OperationStatus.SUCCESS)
        {
          record = newCNIndexRecord(this.key, entry);
          return true;
        }
        return false;
      }
      catch (DatabaseException e)
      {
        throw new ChangelogException(e);
      }
    }

    /**
     * Delete the record at the current cursor position.
     *
     * @throws ChangelogException
     *           If a database problem happened
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

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + " currentRecord=" + record;
    }
  }

  /**
   * Clears this change DB from the changes it contains.
   *
   * @throws ChangelogException
   *           If a database problem happened
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

      final Database oldDb = db;
      db = null; // In case there's a failure between here and recreation.
      dbenv.clearDb(oldDb);

      // RE-create the db
      db = dbenv.getOrCreateCNIndexDB();
    }
    catch(Exception e)
    {
      logger.error(ERR_ERROR_CLEARING_DB, this, e.getMessage() + " " + stackTraceToSingleLineString(e));
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
    return db == null || !db.getEnvironment().isValid();
  }
}
