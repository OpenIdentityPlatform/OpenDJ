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
package org.opends.server.backends.jeb;

import com.sleepycat.je.Transaction;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseEntry;

import java.util.*;

import org.opends.server.types.DirectoryException;

/**
 * A buffered index is used to buffer multiple reads or writes to the
 * same index key into a single read or write.
 * It can only be used to buffer multiple reads and writes under
 * the same transaction. The transaction may be null if it is known
 * that there are no other concurrent updates to the index.
 */
public class IndexBuffer
{
  private EntryContainer entryContainer;

  /**
   * The buffered records stored as a map from the record key to the
   * buffered value for that key for each index.
   */
  private LinkedHashMap<Index,
      TreeMap<byte[], BufferedIndexValues>> bufferedIndexes;

  /**
   * The buffered records stored as a set of buffered VLV values
   * for each index.
   */
  private LinkedHashMap<VLVIndex, BufferedVLVValues> bufferedVLVIndexes;

  /**
   * A simple class representing a pair of added and deleted indexed IDs.
   */
  public static class BufferedIndexValues {
    EntryIDSet addedIDs;
    EntryIDSet deletedIDs;
  }

  /**
   * A simple class representing a pair of added and deleted VLV values.
   */
  public static class BufferedVLVValues {
    TreeSet<SortValues> addedValues;
    TreeSet<SortValues> deletedValues;
  }

  /**
   * Construct a new empty index buffer object.
   *
   * @param entryContainer The database entryContainer using this
   * index buffer.
   */
  public IndexBuffer(EntryContainer entryContainer)
  {
    bufferedIndexes =
        new LinkedHashMap<Index, TreeMap<byte[], BufferedIndexValues>>();
    bufferedVLVIndexes = new LinkedHashMap<VLVIndex, BufferedVLVValues>();
    this.entryContainer = entryContainer;
  }

  /**
   * Get the buffered values for the given index.
   *
   * @param index The index with the buffered values to retrieve.
   * @return The buffered values or <code>null</code> if there are
   * no buffered values for the specified index.
   */
  public TreeMap<byte[], BufferedIndexValues> getBufferedIndex(Index index)
  {
    return bufferedIndexes.get(index);
  }

  /**
   * Put the specified buffered index values for the given index.
   *
   * @param index The index affected by the buffered values.
   * @param bufferedValues The buffered values for the index.
   */
  public void putBufferedIndex(Index index, TreeMap<byte[],
      BufferedIndexValues> bufferedValues)
  {
    bufferedIndexes.put(index, bufferedValues);
  }

  /**
   * Get the buffered VLV values for the given VLV index.
   *
   * @param vlvIndex The VLV index with the buffered values to retrieve.
   * @return The buffered VLV values or <code>null</code> if there are
   * no buffered VLV values for the specified VLV index.
   */
  public BufferedVLVValues getVLVIndex(VLVIndex vlvIndex)
  {
    return bufferedVLVIndexes.get(vlvIndex);
  }

  /**
   * Put the specified buffered VLV values for the given VLV index.
   *
   * @param vlvIndex The VLV index affected by the buffered values.
   * @param bufferedVLVValues The buffered values for the VLV index.
   */
  public void putBufferedVLVIndex(VLVIndex vlvIndex,
                          BufferedVLVValues bufferedVLVValues)
  {
    bufferedVLVIndexes.put(vlvIndex, bufferedVLVValues);
  }

  /**
   * Flush the buffered index changes until the given transaction to
   * the database.
   *
   * @param txn The database transaction to be used for the updates.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void flush(Transaction txn)
      throws DatabaseException, DirectoryException
  {
    TreeMap<byte[], BufferedIndexValues> bufferedValues;
    BufferedVLVValues bufferedVLVValues;
    byte[] keyBytes;
    DatabaseEntry key = new DatabaseEntry();

    for(AttributeIndex attributeIndex :
        entryContainer.getAttributeIndexes())
    {
      for(Index index : attributeIndex.getAllIndexes())
      {
        bufferedValues = bufferedIndexes.remove(index);

        if(bufferedValues != null)
        {
          Iterator<Map.Entry<byte[], BufferedIndexValues>> keyIterator =
              bufferedValues.entrySet().iterator();
          while(keyIterator.hasNext())
          {
            Map.Entry<byte[], BufferedIndexValues> bufferedKey =
                keyIterator.next();
            keyBytes = bufferedKey.getKey();
            key.setData(keyBytes);

            index.updateKey(txn, key, bufferedKey.getValue().deletedIDs,
                bufferedKey.getValue().addedIDs);

            keyIterator.remove();
          }
        }
      }
    }

    for(VLVIndex vlvIndex : entryContainer.getVLVIndexes())
    {
      bufferedVLVValues = bufferedVLVIndexes.remove(vlvIndex);

      if(bufferedVLVValues != null)
      {
        vlvIndex.updateIndex(txn, bufferedVLVValues.addedValues,
            bufferedVLVValues.deletedValues);
      }
    }

    Index id2children = entryContainer.getID2Children();
    bufferedValues = bufferedIndexes.remove(id2children);

    if(bufferedValues != null)
    {
      Iterator<Map.Entry<byte[], BufferedIndexValues>> keyIterator =
          bufferedValues.entrySet().iterator();
      while(keyIterator.hasNext())
      {
        Map.Entry<byte[], BufferedIndexValues> bufferedKey =
            keyIterator.next();
        keyBytes = bufferedKey.getKey();
        key.setData(keyBytes);

        id2children.updateKey(txn, key, bufferedKey.getValue().deletedIDs,
            bufferedKey.getValue().addedIDs);

        keyIterator.remove();
      }
    }

    Index id2subtree = entryContainer.getID2Subtree();
    bufferedValues = bufferedIndexes.remove(id2subtree);

    if(bufferedValues != null)
    {
      Iterator<Map.Entry<byte[], BufferedIndexValues>> keyIterator =
          bufferedValues.entrySet().iterator();
      while(keyIterator.hasNext())
      {
        Map.Entry<byte[], BufferedIndexValues> bufferedKey =
            keyIterator.next();
        keyBytes = bufferedKey.getKey();
        key.setData(keyBytes);

        id2subtree.updateKey(txn, key, bufferedKey.getValue().deletedIDs,
            bufferedKey.getValue().addedIDs);

        keyIterator.remove();
      }
    }
  }
}
