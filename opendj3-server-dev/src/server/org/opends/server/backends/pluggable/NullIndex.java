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
package org.opends.server.backends.pluggable;

import java.util.List;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

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
   *          The indexer object to construct index keys from LDAP attribute values.
   * @param state
   *          The state database to persist index state info.
   * @param storage
   *          The JE Storage
   * @param txn
   *          The transaction to use when creating this object
   * @param entryContainer
   *          The database entryContainer holding this index.
   * @throws StorageRuntimeException
   *           If an error occurs in the JE database.
   */
  public NullIndex(TreeName name, Indexer indexer, State state, Storage storage, WriteableStorage txn,
      EntryContainer entryContainer) throws StorageRuntimeException
  {
    super(name, indexer, state, 0, 0, false, storage, txn, entryContainer);
  }

  /** {@inheritDoc} */
  @Override
  void updateKey(WriteableStorage txn, ByteString key, EntryIDSet deletedIDs, EntryIDSet addedIDs)
      throws StorageRuntimeException
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
  public ConditionResult containsID(ReadableStorage txn, ByteString key, EntryID entryID)
      throws StorageRuntimeException
  {
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override
  public EntryIDSet readKey(ByteSequence key, ReadableStorage txn)
  {
    return new EntryIDSet();
  }

  /** {@inheritDoc} */
  @Override
  public void writeKey(WriteableStorage txn, ByteString key, EntryIDSet entryIDList) throws StorageRuntimeException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public EntryIDSet readRange(ReadableStorage txn, ByteSequence lower, ByteSequence upper, boolean lowerIncluded,
      boolean upperIncluded)
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
  public void addEntry(IndexBuffer buffer, EntryID entryID, Entry entry, IndexingOptions options)
      throws StorageRuntimeException, DirectoryException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public void removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry, IndexingOptions options)
      throws StorageRuntimeException, DirectoryException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public void modifyEntry(IndexBuffer buffer, EntryID entryID, Entry oldEntry, Entry newEntry, List<Modification> mods,
      IndexingOptions options) throws StorageRuntimeException
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
  public void setTrusted(WriteableStorage txn, boolean trusted) throws StorageRuntimeException
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
  public void setRebuildStatus(boolean rebuildRunning)
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public boolean getMaintainCount()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void open(WriteableStorage txn) throws StorageRuntimeException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws StorageRuntimeException
  {
    // Do nothing.
  }

  /** {@inheritDoc} */
  @Override
  void put(WriteableStorage txn, ByteSequence key, ByteSequence value) throws StorageRuntimeException
  {
  }

  /** {@inheritDoc} */
  @Override
  ByteString read(ReadableStorage txn, ByteSequence key, boolean isRMW) throws StorageRuntimeException
  {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  boolean insert(WriteableStorage txn, ByteString key, ByteString value) throws StorageRuntimeException
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  boolean delete(WriteableStorage txn, ByteSequence key) throws StorageRuntimeException
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public long getRecordCount(ReadableStorage txn) throws StorageRuntimeException
  {
    return 0;
  }
}
