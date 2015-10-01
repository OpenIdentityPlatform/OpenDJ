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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.server.backends.pluggable.EntryIDSet.*;

import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Reject;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.types.DirectoryException;

/**
 * A buffered index is used to buffer multiple reads or writes to the
 * same index key into a single read or write.
 * <p>
 * It can only be used to buffer multiple reads and writes under
 * the same transaction. The transaction may be null if it is known
 * that there are no other concurrent updates to the index.
 */
@SuppressWarnings("javadoc")
class IndexBuffer
{
  /** Internal interface for IndexBuffer implementor. */
  private interface IndexBufferImplementor
  {
    void flush(WriteableTransaction txn) throws StorageRuntimeException, DirectoryException;

    void put(Index index, ByteString key, EntryID entryID);

    void put(VLVIndex index, ByteString sortKey);

    void remove(VLVIndex index, ByteString sortKey);

    void remove(Index index, ByteString key, EntryID entryID);
  }

  /**
   * A buffered index is used to buffer multiple reads or writes to the same index key into a single read or write.
   * <p>
   * It can only be used to buffer multiple reads and writes under the same transaction. The transaction may be null if
   * it is known that there are no other concurrent updates to the index.
   */
  private static final class DefaultIndexBuffer implements IndexBufferImplementor
  {
    /**
     * The buffered records stored as a map from the record key to the buffered value for that key for each index.
     * <p>
     * The map is sorted by {@link TreeName}s to establish a deterministic iteration order (see {@link AbstractTree}).
     * This prevents potential deadlock for db having pessimistic lock strategy (e.g.: JE).
     */
    private final SortedMap<Index, SortedMap<ByteString, BufferedIndexValues>> bufferedIndexes = new TreeMap<>();

    /**
     * The buffered records stored as a set of buffered VLV values for each index.
     * <p>
     * The map is sorted by {@link TreeName}s to establish a deterministic iteration order (see {@link AbstractTree}).
     * This prevents potential deadlock for db having pessimistic lock strategy (e.g.: JE).
     */
    private final SortedMap<VLVIndex, BufferedVLVIndexValues> bufferedVLVIndexes = new TreeMap<>();

    /**
     * A simple class representing a pair of added and deleted indexed IDs. Initially both addedIDs and deletedIDs are
     * {@code null} indicating that that the whole record should be deleted.
     */
    private static class BufferedIndexValues
    {
      private EntryIDSet addedEntryIDs;
      private EntryIDSet deletedEntryIDs;

      void addEntryID(EntryID entryID)
      {
        if (!remove(deletedEntryIDs, entryID))
        {
          if (this.addedEntryIDs == null)
          {
            this.addedEntryIDs = newDefinedSet();
          }
          this.addedEntryIDs.add(entryID);
        }
      }

      void deleteEntryID(EntryID entryID)
      {
        if (!remove(addedEntryIDs, entryID))
        {
          if (this.deletedEntryIDs == null)
          {
            this.deletedEntryIDs = newDefinedSet();
          }
          this.deletedEntryIDs.add(entryID);
        }
      }

      private static boolean remove(EntryIDSet entryIDs, EntryID entryID)
      {
        return entryIDs != null ? entryIDs.remove(entryID) : false;
      }
    }

    /** A simple class representing a pair of added and deleted VLV values. */
    private static class BufferedVLVIndexValues
    {
      private TreeSet<ByteString> addedSortKeys;
      private TreeSet<ByteString> deletedSortKeys;

      void addSortKey(ByteString sortKey)
      {
        if (!remove(deletedSortKeys, sortKey))
        {
          if (addedSortKeys == null)
          {
            addedSortKeys = new TreeSet<>();
          }
          addedSortKeys.add(sortKey);
        }
      }

      void deleteSortKey(ByteString sortKey)
      {
        if (!remove(addedSortKeys, sortKey))
        {
          if (deletedSortKeys == null)
          {
            deletedSortKeys = new TreeSet<>();
          }
          deletedSortKeys.add(sortKey);
        }
      }

      private static boolean remove(TreeSet<ByteString> sortKeys, ByteString sortKey)
      {
        return sortKeys != null ? sortKeys.remove(sortKey) : false;
      }
    }

    private BufferedVLVIndexValues createOrGetBufferedVLVIndexValues(VLVIndex vlvIndex)
    {
      BufferedVLVIndexValues bufferedValues = bufferedVLVIndexes.get(vlvIndex);
      if (bufferedValues == null)
      {
        bufferedValues = new BufferedVLVIndexValues();
        bufferedVLVIndexes.put(vlvIndex, bufferedValues);
      }
      return bufferedValues;
    }

    private BufferedIndexValues createOrGetBufferedIndexValues(Index index, ByteString keyBytes)
    {
      Map<ByteString, BufferedIndexValues> bufferedOperations = createOrGetBufferedOperations(index);

      BufferedIndexValues values = bufferedOperations.get(keyBytes);
      if (values == null)
      {
        values = new BufferedIndexValues();
        bufferedOperations.put(keyBytes, values);
      }
      return values;
    }

    private Map<ByteString, BufferedIndexValues> createOrGetBufferedOperations(Index index)
    {
      SortedMap<ByteString, BufferedIndexValues> bufferedOperations = bufferedIndexes.get(index);
      if (bufferedOperations == null)
      {
        bufferedOperations = new TreeMap<>();
        bufferedIndexes.put(index, bufferedOperations);
      }
      return bufferedOperations;
    }

    @Override
    public void flush(WriteableTransaction txn) throws StorageRuntimeException, DirectoryException
    {
      // Indexes are stored in sorted map to prevent deadlock during flush with DB using pessimistic lock strategies.
      for (Entry<Index, SortedMap<ByteString, BufferedIndexValues>> entry : bufferedIndexes.entrySet())
      {
        flushIndex(entry.getKey(), txn, entry.getValue());
      }

      for (Entry<VLVIndex, BufferedVLVIndexValues> entry : bufferedVLVIndexes.entrySet())
      {
        entry.getKey().updateIndex(txn, entry.getValue().addedSortKeys, entry.getValue().deletedSortKeys);
      }
    }

    @Override
    public void put(Index index, ByteString key, EntryID entryID)
    {
      createOrGetBufferedIndexValues(index, key).addEntryID(entryID);
    }

    @Override
    public void put(VLVIndex index, ByteString sortKey)
    {
      createOrGetBufferedVLVIndexValues(index).addSortKey(sortKey);
    }

    @Override
    public void remove(VLVIndex index, ByteString sortKey)
    {
      createOrGetBufferedVLVIndexValues(index).deleteSortKey(sortKey);
    }

    @Override
    public void remove(Index index, ByteString key, EntryID entryID)
    {
      createOrGetBufferedIndexValues(index, key).deleteEntryID(entryID);
    }

    private static void flushIndex(Index index, WriteableTransaction txn,
        Map<ByteString, BufferedIndexValues> bufferedValues)
    {
      for (Entry<ByteString, BufferedIndexValues> entry : bufferedValues.entrySet())
      {
        final BufferedIndexValues values = entry.getValue();
        index.update(txn, entry.getKey(), values.deletedEntryIDs, values.addedEntryIDs);
      }
    }
  }

  /**
   * IndexBuffer used during import which actually doesn't buffer modifications but forward those directly to the
   * supplied {@link WriteableTransaction}.
   */
  private static final class ImportIndexBuffer implements IndexBufferImplementor
  {
    private final WriteableTransaction txn;
    private final EntryID expectedEntryID;
    private final ByteString encodedEntryID;

    ImportIndexBuffer(WriteableTransaction txn, EntryID expectedEntryID)
    {
      this.txn = txn;
      this.expectedEntryID = expectedEntryID;
      this.encodedEntryID = CODEC_V2.encode(EntryIDSet.newDefinedSet(expectedEntryID.longValue()));
    }

    @Override
    public void put(Index index, ByteString key, EntryID entryID)
    {
      Reject.ifFalse(this.expectedEntryID.equals(entryID), "Unexpected entryID");
      txn.put(index.getName(), key, encodedEntryID);
    }

    @Override
    public void put(VLVIndex index, ByteString sortKey)
    {
      txn.put(index.getName(), sortKey, index.toValue());
    }

    @Override
    public void flush(WriteableTransaction txn) throws StorageRuntimeException, DirectoryException
    {
      // Nothing to do
    }

    @Override
    public void remove(VLVIndex index, ByteString sortKey)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void remove(Index index, ByteString key, EntryID entryID)
    {
      throw new UnsupportedOperationException();
    }
  }

  private final IndexBufferImplementor impl;

  static IndexBuffer newImportIndexBuffer(WriteableTransaction txn, EntryID entryID)
  {
    return new IndexBuffer(new ImportIndexBuffer(txn, entryID));
  }

  public IndexBuffer()
  {
    this(new DefaultIndexBuffer());
  }

  private IndexBuffer(IndexBufferImplementor impl)
  {
    this.impl = impl;
  }

  /**
   * Flush the buffered index changes to storage.
   *
   * @param txn
   *          a non null transaction
   * @throws StorageRuntimeException
   *           If an error occurs in the storage.
   * @throws DirectoryException
   *           If a Directory Server error occurs.
   */
  void flush(WriteableTransaction txn) throws StorageRuntimeException, DirectoryException
  {
    impl.flush(txn);
  }

  void put(Index index, ByteString key, EntryID entryID)
  {
    impl.put(index, key, entryID);
  }

  void put(VLVIndex index, ByteString sortKey)
  {
    impl.put(index, sortKey);
  }

  void remove(VLVIndex index, ByteString sortKey)
  {
    impl.remove(index, sortKey);
  }

  void remove(Index index, ByteString key, EntryID entryID)
  {
    impl.remove(index, key, entryID);
  }
}
