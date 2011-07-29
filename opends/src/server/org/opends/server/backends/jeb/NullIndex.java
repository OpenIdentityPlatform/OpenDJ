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
 *      Copyright 2011 ForgeRock AS
 */

package org.opends.server.backends.jeb;



import java.util.List;
import java.util.Set;

import org.opends.server.backends.jeb.importLDIF.ImportIDSet;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

import com.sleepycat.je.*;



/**
 * A null index which replaces id2children and id2subtree when they have been
 * disabled.
 */
final class NullIndex extends Index
{

  /**
   * Create a new null index object.
   *
   * @param name
   *          The name of the index database within the entryContainer.
   * @param indexer
   *          The indexer object to construct index keys from LDAP attribute
   *          values.
   * @param state
   *          The state database to persist index state info.
   * @param env
   *          The JE Environemnt
   * @param entryContainer
   *          The database entryContainer holding this index.
   * @throws DatabaseException
   *           If an error occurs in the JE database.
   */
  public NullIndex(String name, Indexer indexer, State state, Environment env,
      EntryContainer entryContainer) throws DatabaseException
  {
    super(name, indexer, state, 0, 0, false, env, entryContainer);
  }



  /**
   * {@inheritDoc}
   */
  public boolean insertID(IndexBuffer buffer, byte[] keyBytes, EntryID entryID)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean insertID(Transaction txn, DatabaseEntry key, EntryID entryID)
      throws DatabaseException
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void insert(DatabaseEntry key, ImportIDSet importIdSet,
      DatabaseEntry data) throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public void delete(DatabaseEntry key, ImportIDSet importIdSet,
      DatabaseEntry data) throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public synchronized boolean insert(ImportIDSet importIDSet,
      Set<byte[]> keySet, DatabaseEntry keyData, DatabaseEntry data)
      throws DatabaseException
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  void updateKey(Transaction txn, DatabaseEntry key, EntryIDSet deletedIDs,
      EntryIDSet addedIDs) throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public boolean removeID(IndexBuffer buffer, byte[] keyBytes, EntryID entryID)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void removeID(Transaction txn, DatabaseEntry key, EntryID entryID)
      throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public void delete(Transaction txn, Set<byte[]> keySet, EntryID entryID)
      throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public void delete(IndexBuffer buffer, byte[] keyBytes)
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public ConditionResult containsID(Transaction txn, DatabaseEntry key,
      EntryID entryID) throws DatabaseException
  {
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  public EntryIDSet readKey(DatabaseEntry key, Transaction txn,
      LockMode lockMode)
  {
    return new EntryIDSet();
  }



  /**
   * {@inheritDoc}
   */
  public void writeKey(Transaction txn, DatabaseEntry key,
      EntryIDSet entryIDList) throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public EntryIDSet readRange(byte[] lower, byte[] upper,
      boolean lowerIncluded, boolean upperIncluded)
  {
    return new EntryIDSet();
  }



  /**
   * {@inheritDoc}
   */
  public int getEntryLimitExceededCount()
  {
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public void closeCursor() throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public boolean addEntry(IndexBuffer buffer, EntryID entryID, Entry entry)
      throws DatabaseException, DirectoryException
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean addEntry(Transaction txn, EntryID entryID, Entry entry)
      throws DatabaseException, DirectoryException
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry)
      throws DatabaseException, DirectoryException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public void removeEntry(Transaction txn, EntryID entryID, Entry entry)
      throws DatabaseException, DirectoryException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public void modifyEntry(Transaction txn, EntryID entryID, Entry oldEntry,
      Entry newEntry, List<Modification> mods) throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public void modifyEntry(IndexBuffer buffer, EntryID entryID, Entry oldEntry,
      Entry newEntry, List<Modification> mods) throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public boolean setIndexEntryLimit(int indexEntryLimit)
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public int getIndexEntryLimit()
  {
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void setTrusted(Transaction txn, boolean trusted)
      throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public synchronized boolean isTrusted()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public synchronized boolean isRebuildRunning()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void setRebuildStatus(boolean rebuildRunning)
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  public boolean getMaintainCount()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void open() throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  synchronized void close() throws DatabaseException
  {
    // Do nothing.
  }



  /**
   * {@inheritDoc}
   */
  protected OperationStatus put(Transaction txn, DatabaseEntry key,
      DatabaseEntry data) throws DatabaseException
  {
    return OperationStatus.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  protected OperationStatus read(Transaction txn, DatabaseEntry key,
      DatabaseEntry data, LockMode lockMode) throws DatabaseException
  {
    return OperationStatus.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  protected OperationStatus insert(Transaction txn, DatabaseEntry key,
      DatabaseEntry data) throws DatabaseException
  {
    return OperationStatus.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  protected OperationStatus delete(Transaction txn, DatabaseEntry key)
      throws DatabaseException
  {
    return OperationStatus.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  public Cursor openCursor(Transaction txn, CursorConfig cursorConfig)
      throws DatabaseException
  {
    throw new IllegalStateException();
  }



  /**
   * {@inheritDoc}
   */
  public long getRecordCount() throws DatabaseException
  {
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public PreloadStats preload(PreloadConfig config) throws DatabaseException
  {
    return new PreloadStats();
  }

}
