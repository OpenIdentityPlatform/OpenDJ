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
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

/**
 * A null index which replaces id2children and id2subtree when they have been
 * disabled.
 */
final class NullIndex extends Index
{

  NullIndex(TreeName name, Indexer indexer, State state, Storage storage, WriteableStorage txn,
      EntryContainer entryContainer) throws StorageRuntimeException
  {
    super(name, indexer, state, 0, 0, false, storage, txn, entryContainer);
  }

  @Override
  void updateKey(WriteableStorage txn, ByteString key, EntryIDSet deletedIDs, EntryIDSet addedIDs)
      throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  void delete(IndexBuffer buffer, ByteString keyBytes)
  {
    // Do nothing.
  }

  @Override
  ConditionResult containsID(ReadableStorage txn, ByteString key, EntryID entryID)
      throws StorageRuntimeException
  {
    return ConditionResult.UNDEFINED;
  }

  @Override
  EntryIDSet read(ReadableStorage txn, ByteSequence key)
  {
    return new EntryIDSet();
  }

  @Override
  EntryIDSet readRange(ReadableStorage txn, ByteSequence lower, ByteSequence upper, boolean lowerIncluded,
      boolean upperIncluded)
  {
    return new EntryIDSet();
  }

  @Override
  int getEntryLimitExceededCount()
  {
    return 0;
  }

  @Override
  void addEntry(IndexBuffer buffer, EntryID entryID, Entry entry, IndexingOptions options)
      throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  void removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry, IndexingOptions options)
      throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  void modifyEntry(IndexBuffer buffer, EntryID entryID, Entry oldEntry, Entry newEntry, List<Modification> mods,
      IndexingOptions options) throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  boolean setIndexEntryLimit(int indexEntryLimit)
  {
    return false;
  }

  @Override
  int getIndexEntryLimit()
  {
    return 0;
  }

  @Override
  void setTrusted(WriteableStorage txn, boolean trusted) throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  boolean isTrusted()
  {
    return true;
  }

  @Override
  boolean isRebuildRunning()
  {
    return false;
  }

  @Override
  void setRebuildStatus(boolean rebuildRunning)
  {
    // Do nothing.
  }

  @Override
  boolean getMaintainCount()
  {
    return false;
  }

  @Override
  void open(WriteableStorage txn) throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  public void close() throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  long getRecordCount(ReadableStorage txn) throws StorageRuntimeException
  {
    return 0;
  }
}
