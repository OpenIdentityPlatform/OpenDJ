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
package org.opends.server.backends.jeb;

import java.util.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.DirectoryException;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;

/**
 * A buffered index is used to buffer multiple reads or writes to the
 * same index key into a single read or write.
 * It can only be used to buffer multiple reads and writes under
 * the same transaction. The transaction may be null if it is known
 * that there are no other concurrent updates to the index.
 */
public class IndexBuffer
{
  private final EntryContainer entryContainer;

  /**
   * The buffered records stored as a map from the record key to the
   * buffered value for that key for each index.
   */
  private final LinkedHashMap<Index, TreeMap<ByteString, BufferedIndexValues>> bufferedIndexes = new LinkedHashMap<>();
  /** The buffered records stored as a set of buffered VLV values for each index. */
  private final LinkedHashMap<VLVIndex, BufferedVLVValues> bufferedVLVIndexes = new LinkedHashMap<>();

  /** A simple class representing a pair of added and deleted indexed IDs. */
  static class BufferedIndexValues
  {
    private EntryIDSet addedIDs;
    private EntryIDSet deletedIDs;

    /**
     * Adds the provided entryID to this object associating it with the provided keyBytes.
     *
     * @param keyBytes the keyBytes mapping for this entryID
     * @param entryID the entryID to add
     */
    void addEntryID(ByteString keyBytes, EntryID entryID)
    {
      if (!remove(deletedIDs, entryID))
      {
        if (this.addedIDs == null)
        {
          this.addedIDs = new EntryIDSet(keyBytes, null);
        }
        this.addedIDs.add(entryID);
      }
    }

    /**
     * Deletes the provided entryID from this object.
     *
     * @param keyBytes the keyBytes mapping for this entryID
     * @param entryID the entryID to delete
     */
    void deleteEntryID(ByteString keyBytes, EntryID entryID)
    {
      if (!remove(addedIDs, entryID))
      {
        if (this.deletedIDs == null)
        {
          this.deletedIDs = new EntryIDSet(keyBytes, null);
        }
        this.deletedIDs.add(entryID);
      }
    }

    private boolean remove(EntryIDSet ids, EntryID entryID)
    {
      if (ids != null && ids.contains(entryID))
      {
        ids.remove(entryID);
        return true;
      }
      return false;
    }
  }

  /** A simple class representing a pair of added and deleted VLV values. */
  static class BufferedVLVValues
  {
    private TreeSet<SortValues> addedValues;
    private TreeSet<SortValues> deletedValues;

    /**
     * Adds the provided values to this object.
     *
     * @param sortValues the values to add
     */
    void addValues(SortValues sortValues)
    {
      if (!remove(deletedValues, sortValues))
      {
        if (this.addedValues == null)
        {
          this.addedValues = new TreeSet<>();
        }
        this.addedValues.add(sortValues);
      }
    }

    /**
     * Deletes the provided values from this object.
     *
     * @param sortValues the values to delete
     */
    void deleteValues(SortValues sortValues)
    {
      if (!remove(addedValues, sortValues))
      {
        if (this.deletedValues == null)
        {
          this.deletedValues = new TreeSet<>();
        }
        this.deletedValues.add(sortValues);
      }
    }

    private boolean remove(TreeSet<SortValues> values, SortValues sortValues)
    {
      if (values != null && values.contains(sortValues))
      {
        values.remove(sortValues);
        return true;
      }
      return false;
    }
  }

  /**
   * Construct a new empty index buffer object.
   *
   * @param entryContainer The database entryContainer using this
   * index buffer.
   */
  public IndexBuffer(EntryContainer entryContainer)
  {
    this.entryContainer = entryContainer;
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
    BufferedVLVValues bufferedValues = bufferedVLVIndexes.get(vlvIndex);
    if (bufferedValues == null)
    {
      bufferedValues = new BufferedVLVValues();
      bufferedVLVIndexes.put(vlvIndex, bufferedValues);
    }
    return bufferedValues;
  }

  /**
   * Get the buffered index values for the given index and keyBytes.
   *
   * @param index
   *          The index for which to retrieve the buffered index values
   * @param keyBytes
   *          The keyBytes for which to retrieve the buffered index values
   * @param bsComparator
   *          The byte sequence comparator to use when retrieving the
   *          BufferedIndexValues
   * @return The buffered index values, it can never be null
   */
  BufferedIndexValues getBufferedIndexValues(Index index, ByteString keyBytes, Comparator<ByteSequence> bsComparator)
  {
    BufferedIndexValues values = null;

    TreeMap<ByteString, BufferedIndexValues> bufferedOperations = bufferedIndexes.get(index);
    if (bufferedOperations == null)
    {
      bufferedOperations = new TreeMap<>(bsComparator);
      bufferedIndexes.put(index, bufferedOperations);
    }
    else
    {
      values = bufferedOperations.get(keyBytes);
    }

    if (values == null)
    {
      values = new BufferedIndexValues();
      bufferedOperations.put(keyBytes, values);
    }
    return values;
  }

  /**
   * Flush the buffered index changes until the given transaction to
   * the database.
   *
   * @param txn The database transaction to be used for the updates.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void flush(Transaction txn) throws DatabaseException, DirectoryException
  {
    DatabaseEntry key = new DatabaseEntry();

    for (AttributeIndex attributeIndex : entryContainer.getAttributeIndexes())
    {
      for (Index index : attributeIndex.getAllIndexes())
      {
        updateKeys(index, txn, key, bufferedIndexes.remove(index));
      }
    }

    for (VLVIndex vlvIndex : entryContainer.getVLVIndexes())
    {
      BufferedVLVValues bufferedVLVValues = bufferedVLVIndexes.remove(vlvIndex);
      if (bufferedVLVValues != null)
      {
        vlvIndex.updateIndex(txn, bufferedVLVValues.addedValues, bufferedVLVValues.deletedValues);
      }
    }

    final Index id2children = entryContainer.getID2Children();
    updateKeys(id2children, txn, key, bufferedIndexes.remove(id2children));

    final Index id2subtree = entryContainer.getID2Subtree();
    final TreeMap<ByteString, BufferedIndexValues> bufferedValues = bufferedIndexes.remove(id2subtree);
    if (bufferedValues != null)
    {
      /*
       * OPENDJ-1375: add keys in reverse order to be consistent with single
       * entry processing in add/delete processing. This is necessary in order
       * to avoid deadlocks.
       */
      updateKeys(id2subtree, txn, key, bufferedValues.descendingMap());
    }
  }

  private void updateKeys(Index index, Transaction txn, DatabaseEntry key,
      Map<ByteString, BufferedIndexValues> bufferedValues)
  {
    if (bufferedValues != null)
    {
      final Iterator<Map.Entry<ByteString, BufferedIndexValues>> it = bufferedValues.entrySet().iterator();
      while (it.hasNext())
      {
        final Map.Entry<ByteString, BufferedIndexValues> entry = it.next();
        final ByteString bufferedKey = entry.getKey();
        final BufferedIndexValues values = entry.getValue();

        key.setData(bufferedKey.toByteArray());
        index.updateKey(txn, key, values.deletedIDs, values.addedIDs);

        it.remove();
      }
    }
  }
}
