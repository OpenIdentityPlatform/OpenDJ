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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import com.sleepycat.je.Transaction;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import org.opends.server.protocols.asn1.ASN1OctetString;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * A buffered index is used to buffer multiple reads or writes to the
 * same index key into a single read or write.
 * It can only be used to buffer multiple reads and writes under
 * the same transaction. The transaction may be null if it is known
 * that there are no other concurrent updates to the index.
 */
public class BufferedIndex
{
  /**
   * The index that is being buffered.
   */
  private Index index;

  /**
   * The database transaction used for all reads and writes, or null
   * if there is no transaction.
   */
  private Transaction txn;

  /**
   * The buffered records stored as a map from the record key to the
   * buffered value for that key.
   */
  private HashMap<ASN1OctetString, BufferedValue> values;

  /**
   * Inner class representing a buffered value.
   */
  private class BufferedValue
  {
    /**
     * The current value.
     */
    EntryIDSet value;

    /**
     * A flag to indicate whether the buffered value is different to the
     * value stored in the database, and therefore needs to be written
     * to the database.
     */
    boolean isDirty;
  }

  /**
   * Construct a buffered index.
   *
   * @param index The index to be buffered
   * @param txn The transaction to be used for all reads and writes
   *            through this buffered index, or null for no transaction.
   */
  public BufferedIndex(Index index, Transaction txn)
  {
    this.index = index;
    this.txn = txn;
    values = new LinkedHashMap<ASN1OctetString, BufferedValue>();
  }

  /**
   * Get the list of IDs for a key.
   * The value may be returned from a buffered value in memory.
   *
   * @param key The desired key
   * @return The list of IDs.
   */
  public EntryIDSet get(byte[] key)
  {
    return getCachedValue(key).value;
  }

  /**
   * Get the buffered value for a key, creating it if necessary.
   *
   * @param key The desired key.
   * @return The buffered value.
   */
  private BufferedValue getCachedValue(byte[] key)
  {
    BufferedValue bufferedValue = values.get(new ASN1OctetString(key));
    if (bufferedValue == null)
    {
      bufferedValue = new BufferedValue();
      bufferedValue.value = index.readKey(new DatabaseEntry(key), txn,
                                          LockMode.RMW);
      bufferedValue.isDirty = false;
      values.put(new ASN1OctetString(key), bufferedValue);
    }
    return bufferedValue;
  }

  /**
   * Add an ID to the given key.
   * The update may be buffered in memory.
   *
   * @param entryLimit The index entry limit
   * @param key The key to which the ID is to be inserted.
   * @param entryID The ID to be inserted.
   */
  public void insertID(int entryLimit, byte[] key, EntryID entryID)
  {
    BufferedValue bufferedValue = getCachedValue(key);
    EntryIDSet entryIDList = bufferedValue.value;
    if (entryIDList.isDefined())
    {
      if (entryLimit > 0 && entryIDList.size() >= entryLimit)
      {
        entryIDList = new EntryIDSet(entryIDList.size());
        entryIDList.add(entryID);
        bufferedValue.value = entryIDList;
        bufferedValue.isDirty = true;
        return;
      }

    }
    bufferedValue.isDirty = entryIDList.add(entryID);
  }

  /**
   * Remove an ID from the given key.
   * The update may be buffered in memory.
   *
   * @param key The key from which the ID is to be removed.
   * @param entryID The ID to be removed.
   */
  public void removeID(byte[] key, EntryID entryID)
  {
    BufferedValue bufferedValue = getCachedValue(key);
    bufferedValue.isDirty = bufferedValue.value.remove(entryID);
  }

  /**
   * Remove a key from the index.
   * The update may be buffered in memory.
   *
   * @param key The key to be removed.
   */
  public void remove(byte[] key)
  {
    BufferedValue bufferedValue = getCachedValue(key);
    bufferedValue.value = new EntryIDSet(key, null);
    bufferedValue.isDirty = true;
  }

  /**
   * Flush all the dirty data out to the index.
   *
   * @throws DatabaseException If an error occurs while writing out the
   * dirty data to the index.
   */
  public void flush() throws DatabaseException
  {
    for (Map.Entry<ASN1OctetString,BufferedValue> hashEntry : values.entrySet())
    {
      BufferedValue bufferedValue = hashEntry.getValue();
      if (bufferedValue.isDirty)
      {
        index.writeKey(txn, new DatabaseEntry(hashEntry.getKey().value()),
                       bufferedValue.value);
        bufferedValue.isDirty = false;
      }
    }
  }
}
