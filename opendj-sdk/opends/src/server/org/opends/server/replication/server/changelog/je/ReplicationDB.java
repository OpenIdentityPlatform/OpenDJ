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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.*;

import static com.sleepycat.je.LockMode.*;
import static com.sleepycat.je.OperationStatus.*;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements the interface between the underlying database
 * and the JEReplicaDB class.
 * <p>
 * This is the only class that should have code using the BDB interfaces.
 */
class ReplicationDB
{

  private Database db;
  private final ReplicationDbEnv dbEnv;
  private final ReplicationServer replicationServer;
  private final int serverId;
  private final DN baseDN;

  /**
   * The lock used to provide exclusive access to the thread that close the db
   * (shutdown or clear).
   */
  private final ReadWriteLock dbCloseLock = new ReentrantReadWriteLock(true);

  // Change counter management
  // The Db itself does not allow to count records between a start and an end
  // change. And we cannot rely on the replication seqnum that is part of the
  // CSN, since there can be holes (when an operation is canceled).
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
  // a CSN key that follows the order.
  // A counter record must have its own CSN key since the Db does not support
  // duplicate keys (it is a compatibility breaker character of the DB).
  //
  // We define 2 conditions to store a counter record :
  // 1/- at least 'counterWindowSize' changes have been stored in the Db
  //     since the previous counter record
  // 2/- the change to be stored has a new timestamp - so that the counter
  //     record is the first record for this timestamp.


  /** Current value of the counter. */
  private int counterCurrValue = 1;

  /**
   * When not null, the next change with a ts different from
   * tsForNewCounterRecord will lead to store a new counterRecord.
   */
  private long counterTsLimit = 0;

  /**
   * The counter record will never be written to the db more often than each
   * counterWindowSize changes.
   */
  private int counterWindowSize = 1000;

 /**
   * Creates a new database or open existing database that will be used
   * to store and retrieve changes from an LDAP server.
   * @param serverId The identifier of the LDAP server.
   * @param baseDN The baseDN of the replication domain.
   * @param replicationServer The ReplicationServer that needs to be shutdown.
   * @param dbEnv The Db environment to use to create the db.
   * @throws ChangelogException If a database problem happened
   */
  ReplicationDB(int serverId, DN baseDN,
      ReplicationServer replicationServer, ReplicationDbEnv dbEnv)
      throws ChangelogException
  {
    this.serverId = serverId;
    this.baseDN = baseDN;
    this.dbEnv = dbEnv;
    this.replicationServer = replicationServer;

    // Get or create the associated ReplicationServerDomain and Db.
    final ReplicationServerDomain domain =
        replicationServer.getReplicationServerDomain(baseDN, true);
    db = dbEnv.getOrAddReplicationDB(serverId, baseDN, domain.getGenerationId());

    intializeCounters();
  }

  private void intializeCounters() throws ChangelogException
  {
    this.counterCurrValue = 1;

    Cursor cursor = null;
    try
    {
      cursor = db.openCursor(null, null);

      int distBackToCounterRecord = 0;
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      OperationStatus status = cursor.getLast(key, data, LockMode.DEFAULT);
      while (status == OperationStatus.SUCCESS)
      {
        CSN csn = toCSN(key.getData());
        if (isACounterRecord(csn))
        {
          counterCurrValue = decodeCounterValue(data.getData()) + 1;
          counterTsLimit = csn.getTime();
          break;
        }

        status = cursor.getPrev(key, data, LockMode.DEFAULT);
        distBackToCounterRecord++;
      }
      counterCurrValue += distBackToCounterRecord;
    }
    catch (DatabaseException e)
    {
      throw new ChangelogException(e);
    }
    finally
    {
      close(cursor);
    }
  }

  private static CSN toCSN(byte[] data)
  {
    return new CSN(decodeUTF8(data));
  }

  /**
   * add one change to the underlying db.
   *
   * @param change
   *          The change to add to the underlying db.
   * @throws ChangelogException
   *           If a database problem happened
   */
  void addEntry(UpdateMsg change) throws ChangelogException
  {
    dbCloseLock.readLock().lock();
    try
    {
      // If the DB has been closed then return immediately.
      if (isDBClosed())
      {
        return;
      }

      final DatabaseEntry key = createReplicationKey(change.getCSN());
      // Always keep messages in the replication DB with the current protocol
      // version
      final DatabaseEntry data = new DatabaseEntry(change.getBytes());

      insertCounterRecordIfNeeded(change.getCSN());
      db.put(null, key, data);
      counterCurrValue++;
    }
    catch (DatabaseException e)
    {
      throw new ChangelogException(
          ERR_EXCEPTION_COULD_NOT_ADD_CHANGE_TO_REPLICA_DB.get(
              change.toString(), String.valueOf(baseDN),
              String.valueOf(serverId), stackTraceToSingleLineString(e)));
    }
    finally
    {
      dbCloseLock.readLock().unlock();
    }
  }

  private void insertCounterRecordIfNeeded(CSN csn) throws DatabaseException
  {
    if (counterCurrValue != 0 && (counterCurrValue % counterWindowSize == 0))
    {
      // enough changes to generate a counter record
      // wait for the next change of time
      counterTsLimit = csn.getTime();
    }
    if (counterTsLimit != 0 && csn.getTime() != counterTsLimit)
    {
      // Write the counter record
      final CSN counterRecord = newCounterRecord(csn);
      DatabaseEntry counterKey = createReplicationKey(counterRecord);
      DatabaseEntry counterValue = encodeCounterValue(counterCurrValue - 1);
      db.put(null, counterKey, counterValue);
      counterTsLimit = 0;
    }
  }

  private DatabaseEntry createReplicationKey(CSN csn)
  {
    DatabaseEntry key = new DatabaseEntry();
    try
    {
      key.setData(csn.toString().getBytes("UTF-8"));
    }
    catch (UnsupportedEncodingException e)
    {
      // Should never happens, UTF-8 is always supported
      // TODO : add better logging
    }
    return key;
  }

  /**
   * Shutdown the database.
   */
  void shutdown()
  {
    dbCloseLock.writeLock().lock();
    try
    {
      db.close();
      db = null;
    }
    catch (DatabaseException e)
    {
      logError(NOTE_EXCEPTION_CLOSING_DATABASE.get(toString(),
          stackTraceToSingleLineString(e)));
    }
    finally
    {
      dbCloseLock.writeLock().unlock();
    }
  }

  /**
   * Create a cursor that can be used to search or iterate on this
   * ReplicationServer DB.
   *
   * @param startCSN
   *          The CSN from which the cursor must start.If null, start from the
   *          oldest CSN
   * @return The ReplServerDBCursor.
   * @throws ChangelogException
   *           If a database problem happened
   */
  ReplServerDBCursor openReadCursor(CSN startCSN) throws ChangelogException
  {
    return new ReplServerDBCursor(startCSN);
  }

  /**
   * Create a cursor that can be used to delete some record from this
   * ReplicationServer database.
   *
   * @throws ChangelogException If a database error prevented the cursor
   *                           creation.
   *
   * @return The ReplServerDBCursor.
   */
  ReplServerDBCursor openDeleteCursor() throws ChangelogException
  {
    return new ReplServerDBCursor();
  }

  private void closeAndReleaseReadLock(Cursor cursor)
  {
    try
    {
      StaticUtils.close(cursor);
    }
    finally
    {
      dbCloseLock.readLock().unlock();
    }
  }

  /**
   * Read the oldest CSN present in the database.
   *
   * @return the oldest CSN in the DB, null if the DB is empty or closed
   * @throws ChangelogException
   *           If a database problem happened
   */
  CSN readOldestCSN() throws ChangelogException
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

      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      if (cursor.getFirst(key, data, LockMode.DEFAULT) != SUCCESS)
      {
        // database is empty
        return null;
      }

      final CSN csn = toCSN(key.getData());
      if (!isACounterRecord(csn))
      {
        return csn;
      }

      // First record is a counter record .. go next
      if (cursor.getNext(key, data, LockMode.DEFAULT) != SUCCESS)
      {
        // DB contains only a counter record
        return null;
      }
      // There cannot be 2 counter record next to each other,
      // it is safe to return this record
      return toCSN(key.getData());
    }
    catch (DatabaseException e)
    {
      throw new ChangelogException(e);
    }
    finally
    {
      closeAndReleaseReadLock(cursor);
    }
  }

  /**
   * Read the newest CSN present in the database.
   *
   * @return the newest CSN in the DB, null if the DB is empty or closed
   * @throws ChangelogException
   *           If a database problem happened
   */
  CSN readNewestCSN() throws ChangelogException
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

      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      if (cursor.getLast(key, data, LockMode.DEFAULT) != SUCCESS)
      {
        // database is empty
        return null;
      }

      final CSN csn = toCSN(key.getData());
      if (!isACounterRecord(csn))
      {
        return csn;
      }

      if (cursor.getPrev(key, data, LockMode.DEFAULT) != SUCCESS)
      {
        /*
         * database only contain a counter record - don't know how much it can
         * be possible but ...
         */
        return null;
      }
      // There cannot be 2 counter record next to each other,
      // it is safe to return this record
      return toCSN(key.getData());
    }
    catch (DatabaseException e)
    {
      throw new ChangelogException(e);
    }
    finally
    {
      closeAndReleaseReadLock(cursor);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return serverId + " " + baseDN.toNormalizedString();
  }

  /**
   * This Class implements a cursor that can be used to browse a
   * replicationServer database.
   */
  class ReplServerDBCursor implements Closeable
  {
    /**
     * The transaction that will protect the actions done with the cursor.
     * <p>
     * Will be let null for a read cursor
     * <p>
     * Will be set non null for a write cursor
     */
    private final Transaction txn;
    private final Cursor cursor;
    private final DatabaseEntry key;
    private final DatabaseEntry data;

    private boolean isClosed = false;

    /**
     * Creates a ReplServerDBCursor that can be used for browsing a
     * replicationServer db.
     *
     * @param startCSN
     *          The CSN from which the cursor must start.
     * @throws ChangelogException
     *           When the startCSN does not exist.
     */
    private ReplServerDBCursor(CSN startCSN) throws ChangelogException
    {
      if (startCSN != null)
      {
        key = createReplicationKey(startCSN);
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

      boolean cursorHeld = false;
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
        if (startCSN != null
            && localCursor.getSearchKey(key, data, LockMode.DEFAULT) != SUCCESS)
        {
          // We could not move the cursor to the expected startCSN
          if (localCursor.getSearchKeyRange(key, data, DEFAULT) != SUCCESS)
          {
            // We could not even move the cursor close to it
            // => return empty cursor
            isClosed = true;
            cursor = null;
            return;
          }

          // We can move close to the startCSN.
          // Let's create a cursor from that point.
          DatabaseEntry aKey = new DatabaseEntry();
          DatabaseEntry aData = new DatabaseEntry();
          if (localCursor.getPrev(aKey, aData, LockMode.DEFAULT) != SUCCESS)
          {
            localCursor.close();
            localCursor = db.openCursor(txn, null);
          }
        }
        cursor = localCursor;
        cursorHeld = cursor != null;
      }
      catch (DatabaseException e)
      {
        throw new ChangelogException(e);
      }
      finally
      {
        if (!cursorHeld)
        {
          closeAndReleaseReadLock(localCursor);
        }
      }
    }

    private ReplServerDBCursor() throws ChangelogException
    {
      key = new DatabaseEntry();
      data = new DatabaseEntry();

      // We'll go on only if no close or no clear is running
      dbCloseLock.readLock().lock();

      boolean cursorHeld = false;
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
        localTxn = dbEnv.beginTransaction();
        localCursor = db.openCursor(localTxn, null);

        txn = localTxn;
        cursor = localCursor;
        cursorHeld = cursor != null;
      }
      catch (ChangelogException e)
      {
        JEUtils.abort(localTxn);
        throw e;
      }
      catch (Exception e)
      {
        JEUtils.abort(localTxn);
        throw new ChangelogException(e);
      }
      finally
      {
        if (!cursorHeld)
        {
          closeAndReleaseReadLock(localCursor);
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

      closeAndReleaseReadLock(cursor);

      if (txn != null)
      {
        try
        {
          // No need for durability when purging.
          txn.commit(Durability.COMMIT_NO_SYNC);
        }
        catch (DatabaseException e)
        {
          dbEnv.shutdownOnException(e);
        }
      }
    }

    /**
     * Abort the cursor after an Exception.
     * This method catch and ignore the DatabaseException because
     * this must be done when aborting a cursor after a DatabaseException
     * (per the Cursor documentation).
     * This should not be used in any other case.
     */
    void abort()
    {
      synchronized (this)
      {
        if (isClosed)
        {
          return;
        }
        isClosed = true;
      }

      closeAndReleaseReadLock(cursor);
      JEUtils.abort(txn);
    }

    /**
     * Get the next CSN in the database from this Cursor.
     *
     * @return The next CSN in the database from this cursor.
     * @throws ChangelogException
     *           In case of underlying database problem.
     */
    CSN nextCSN() throws ChangelogException
    {
      if (isClosed)
      {
        return null;
      }

      try
      {
        if (cursor.getNext(key, data, LockMode.DEFAULT) != SUCCESS)
        {
          return null;
        }
        return toCSN(key.getData());
      }
      catch (DatabaseException e)
      {
        throw new ChangelogException(e);
      }
    }

    /**
     * Get the next UpdateMsg from this cursor.
     *
     * @return the next UpdateMsg.
     */
    UpdateMsg next()
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
          if (cursor.getNext(key, data, LockMode.DEFAULT) != SUCCESS)
          {
            return null;
          }
        }
        catch (DatabaseException e)
        {
          return null;
        }

        CSN csn = null;
        try
        {
          csn = toCSN(key.getData());
          if (isACounterRecord(csn))
          {
            continue;
          }
          currentChange = (UpdateMsg) ReplicationMsg.generateMsg(
              data.getData(), ProtocolVersion.getCurrentVersion());
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
                  (csn != null ? csn.toString() : ""),
                  e.getMessage());
          logError(message);
        }
      }
      return currentChange;
    }

    /**
     * Delete the record at the current cursor position.
     *
     * @throws ChangelogException In case of database problem.
     */
    void delete() throws ChangelogException
    {
      if (isClosed)
      {
        throw new IllegalStateException("ReplServerDBCursor already closed");
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
  }

  /**
   * Clears this change DB from the changes it contains.
   *
   * @throws ChangelogException In case of database problem.
   */
  void clear() throws ChangelogException
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

      // Clears the reference to this serverID
      dbEnv.clearServerId(baseDN, serverId);

      final Database oldDb = db;
      db = null; // In case there's a failure between here and recreation.
      dbEnv.clearDb(oldDb);

      // RE-create the db
      db = dbEnv.getOrAddReplicationDB(serverId, baseDN, -1);
    }
    catch (Exception e)
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
   * Whether a provided CSN represents a counter record. A counter record is
   * used to store the time.
   *
   * @param csn
   *          The CSN to test
   * @return true if the provided CSN is a counter record, false if the change
   *         is a regular/normal change that was performed on the replica.
   */
  private static boolean isACounterRecord(CSN csn)
  {
    return csn.getServerId() == 0 && csn.getSeqnum() == 0;
  }

  private static CSN newCounterRecord(CSN csn)
  {
    return new CSN(csn.getTime(), 0, 0);
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
  private static DatabaseEntry encodeCounterValue(int value)
  {
    DatabaseEntry entry = new DatabaseEntry();
    entry.setData(getBytes(String.valueOf(value)));
    return entry;
  }

  /**
   * Set the counter writing window size (public method for unit tests only).
   * @param size Size in number of record.
   */
  public void setCounterRecordWindowSize(int size)
  {
    this.counterWindowSize = size;
  }

  /**
   * Returns {@code true} if the DB is closed. This method assumes that either
   * the db read/write lock has been taken.
   */
  private boolean isDBClosed()
  {
    return db == null || !db.getEnvironment().isValid();
  }

  /**
   * Returns the number of records in this DB.
   *
   * @return the number of records in this DB.
   */
  long getNumberRecords()
  {
    return db.count();
  }
}
