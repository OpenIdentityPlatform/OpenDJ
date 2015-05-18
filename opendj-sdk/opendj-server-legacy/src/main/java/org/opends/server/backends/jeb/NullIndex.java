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
 *      Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
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
   *          The JE Environment
   * @param entryContainer
   *          The database entryContainer holding this index.
   * @throws DatabaseException
   *           If an error occurs in the JE database.
   */
  public NullIndex(String name, Indexer indexer, State state, Environment env, EntryContainer entryContainer)
      throws DatabaseException
  {
    super(name, indexer, state, 0, 0, false, env, entryContainer);
  }

  /** {@inheritDoc} */
  @Override
  public void insert(DatabaseEntry key, ImportIDSet importIdSet, DatabaseEntry data) throws DatabaseException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public void delete(DatabaseEntry key, ImportIDSet importIdSet, DatabaseEntry data) throws DatabaseException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  void updateKey(Transaction txn, DatabaseEntry key, EntryIDSet deletedIDs, EntryIDSet addedIDs)
      throws DatabaseException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public void delete(IndexBuffer buffer, ByteString keyBytes)
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult containsID(Transaction txn, DatabaseEntry key, EntryID entryID) throws DatabaseException
  {
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override
  public EntryIDSet readKey(DatabaseEntry key, Transaction txn, LockMode lockMode)
  {
    return new EntryIDSet();
  }

  /** {@inheritDoc} */
  @Override
  public void writeKey(Transaction txn, DatabaseEntry key, EntryIDSet entryIDList) throws DatabaseException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public EntryIDSet readRange(byte[] lower, byte[] upper, boolean lowerIncluded, boolean upperIncluded)
  {
    return new EntryIDSet();
  }

  /** {@inheritDoc} */
  @Override
  public int getEntryLimitExceededCount()
  {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void addEntry(IndexBuffer buffer, EntryID entryID, Entry entry)
      throws DatabaseException, DirectoryException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public void removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry)
      throws DatabaseException, DirectoryException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public void modifyEntry(IndexBuffer buffer, EntryID entryID, Entry oldEntry, Entry newEntry, List<Modification> mods)
      throws DatabaseException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public boolean setIndexEntryLimit(int indexEntryLimit)
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public int getIndexEntryLimit()
  {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void setTrusted(Transaction txn, boolean trusted) throws DatabaseException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTrusted()
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRebuildRunning()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean getMaintainCount()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void open() throws DatabaseException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws DatabaseException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  OperationStatus put(Transaction txn, DatabaseEntry key, DatabaseEntry data) throws DatabaseException
  {
    return OperationStatus.SUCCESS;
  }

  /** {@inheritDoc} */
  @Override
  OperationStatus read(Transaction txn, DatabaseEntry key, DatabaseEntry data, LockMode lockMode)
      throws DatabaseException
  {
    return OperationStatus.SUCCESS;
  }

  /** {@inheritDoc} */
  @Override
  OperationStatus insert(Transaction txn, DatabaseEntry key, DatabaseEntry data) throws DatabaseException
  {
    return OperationStatus.SUCCESS;
  }

  /** {@inheritDoc} */
  @Override
  OperationStatus delete(Transaction txn, DatabaseEntry key) throws DatabaseException
  {
    return OperationStatus.SUCCESS;
  }

  /** {@inheritDoc} */
  @Override
  public Cursor openCursor(Transaction txn, CursorConfig cursorConfig) throws DatabaseException
  {
    throw new IllegalStateException();
  }

  /** {@inheritDoc} */
  @Override
  public long getRecordCount() throws DatabaseException
  {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public PreloadStats preload(PreloadConfig config) throws DatabaseException
  {
    return new PreloadStats();
  }
}
