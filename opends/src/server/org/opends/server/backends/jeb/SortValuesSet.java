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

import org.opends.server.types.*;
import org.opends.server.protocols.asn1.ASN1OctetString;

import java.util.HashMap;

import com.sleepycat.je.DatabaseException;

/**
 * This class representsa  partial sorted set of sorted entries in a VLV
 * index.
 */
public class SortValuesSet
{
  private static final int ENCODED_VALUE_SIZE = 16;
  private static final int ENCODED_VALUE_LENGTH_SIZE =
      encodedLengthSize(ENCODED_VALUE_SIZE);
  private static final int ENCODED_ATTRIBUTE_VALUE_SIZE =
      ENCODED_VALUE_LENGTH_SIZE + ENCODED_VALUE_SIZE;

  private ID2Entry id2entry;

  private long[] entryIDs;

  private byte[] valuesBytes;

  private byte[] keyBytes;

  private HashMap<EntryID, AttributeValue[]> cachedAttributeValues;

  private VLVIndex vlvIndex;

  /**
   * Construct an empty sort values set with the given information.
   *
   * @param vlvIndex The VLV index using this set.
   * @param id2entry The ID2Entry database.
   */
  public SortValuesSet(VLVIndex vlvIndex, ID2Entry id2entry)
  {
    this.keyBytes = new byte[0];
    this.entryIDs = null;
    this.valuesBytes = null;
    this.id2entry = id2entry;
    this.vlvIndex = vlvIndex;
    this.cachedAttributeValues = new HashMap<EntryID, AttributeValue[]>();
  }

  /**
   * Construct a sort values set from the database.
   *
   * @param keyBytes The database key used to locate this set.
   * @param dataBytes The bytes to decode and construct this set.
   * @param vlvIndex The VLV index using this set.
   * @param id2entry The ID2Entry database.
   */
  public SortValuesSet(byte[] keyBytes, byte[] dataBytes, VLVIndex vlvIndex,
                       ID2Entry id2entry)
  {
    this.keyBytes = keyBytes;
    this.id2entry = id2entry;
    this.vlvIndex = vlvIndex;
    this.cachedAttributeValues = new HashMap<EntryID, AttributeValue[]>();

    if(dataBytes == null)
    {
      entryIDs = new long[0];
      return;
    }

    entryIDs = getEncodedIDs(dataBytes, 0);
    valuesBytes = new byte[entryIDs.length * ENCODED_ATTRIBUTE_VALUE_SIZE *
        vlvIndex.sortOrder.getSortKeys().length];
    System.arraycopy(dataBytes, entryIDs.length * 8 + 4, valuesBytes, 0,
                     valuesBytes.length);
  }

  private SortValuesSet(long[] entryIDs, byte[] keyBytes, byte[] valuesBytes,
                        VLVIndex vlvIndex, ID2Entry id2entry)
  {
    this.keyBytes = keyBytes;
    this.id2entry = id2entry;
    this.entryIDs = entryIDs;
    this.valuesBytes = valuesBytes;
    this.vlvIndex = vlvIndex;
    this.cachedAttributeValues = new HashMap<EntryID, AttributeValue[]>();
  }

  /**
   * Add the given entryID and values from this VLV idnex.
   *
   * @param entryID The entry ID to add.
   * @param values The values to add.
   * @return True if the information was successfully added or False
   * otherwise.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE database.
   */
  public boolean add(long entryID, AttributeValue[] values)
      throws JebException, DatabaseException, DirectoryException
  {
    if(values == null)
    {
      return false;
    }

    if(entryIDs == null || entryIDs.length == 0)
    {
      entryIDs = new long[1];
      entryIDs[0] = entryID;
      valuesBytes = attributeValuesToDatabase(values);
      return true;
    }
    if(vlvIndex.comparator.compareValuesInSet(this,
                                              entryIDs.length - 1, entryID,
                                              values) < 0)
    {
      long[] updatedEntryIDs = new long[entryIDs.length + 1];
      System.arraycopy(entryIDs, 0, updatedEntryIDs, 0, entryIDs.length);
      updatedEntryIDs[entryIDs.length] = entryID;

      byte[] newValuesBytes = attributeValuesToDatabase(values);
      byte[] updatedValuesBytes = new byte[valuesBytes.length +
          newValuesBytes.length];
      System.arraycopy(valuesBytes, 0, updatedValuesBytes, 0,
                       valuesBytes.length);
      System.arraycopy(newValuesBytes, 0, updatedValuesBytes,
                       valuesBytes.length,
                       newValuesBytes.length);

      entryIDs = updatedEntryIDs;
      valuesBytes = updatedValuesBytes;
      return true;
    }
    else
    {
      int pos = binarySearch(entryID, values);
      if(pos >= 0)
      {
        if(entryIDs[pos] == entryID)
        {
          // The entry ID is alreadly present.
          return false;
        }
      }
      else
      {
        // For a negative return value r, the vlvIndex -(r+1) gives the array
        // ndex at which the specified value can be inserted to maintain
        // the sorted order of the array.
        pos = -(pos+1);
      }

      long[] updatedEntryIDs = new long[entryIDs.length + 1];
      System.arraycopy(entryIDs, 0, updatedEntryIDs, 0, pos);
      System.arraycopy(entryIDs, pos, updatedEntryIDs, pos+1,
                       entryIDs.length-pos);
      updatedEntryIDs[pos] = entryID;

      byte[] newValuesBytes = attributeValuesToDatabase(values);
      int valuesPos = pos * newValuesBytes.length;
      byte[] updatedValuesBytes = new byte[valuesBytes.length +
          newValuesBytes.length];
      System.arraycopy(valuesBytes, 0, updatedValuesBytes, 0, valuesPos);
      System.arraycopy(valuesBytes, valuesPos,  updatedValuesBytes,
                       valuesPos + newValuesBytes.length,
                       valuesBytes.length - valuesPos);
      System.arraycopy(newValuesBytes, 0, updatedValuesBytes, valuesPos,
                       newValuesBytes.length);

      entryIDs = updatedEntryIDs;
      valuesBytes = updatedValuesBytes;
    }

    return true;
  }

  /**
   * Remove the given entryID and values from this VLV idnex.
   *
   * @param entryID The entry ID to remove.
   * @param values The values to remove.
   * @return True if the information was successfully removed or False
   * otherwise.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE database.
   */
  public boolean remove(long entryID, AttributeValue[] values)
      throws JebException, DatabaseException, DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return false;
    }

    int pos = binarySearch(entryID, values);
    if(pos < 0)
    {
      // Not found.
      return false;
    }
    else
    {
      // Found it.
      long[] updatedEntryIDs = new long[entryIDs.length - 1];
      System.arraycopy(entryIDs, 0, updatedEntryIDs, 0, pos);
      System.arraycopy(entryIDs, pos+1, updatedEntryIDs, pos,
                       entryIDs.length-pos-1);

      int valuesLength = ENCODED_ATTRIBUTE_VALUE_SIZE *
          vlvIndex.sortOrder.getSortKeys().length;
      int valuesPos = pos * valuesLength;
      byte[] updatedValuesBytes = new byte[valuesBytes.length - valuesLength];
      System.arraycopy(valuesBytes, 0, updatedValuesBytes, 0, valuesPos);
      System.arraycopy(valuesBytes, valuesPos + valuesLength,
                       updatedValuesBytes, valuesPos,
                       valuesBytes.length - valuesPos - valuesLength);

      entryIDs = updatedEntryIDs;
      valuesBytes = updatedValuesBytes;
      return true;
    }
  }

  /**
   * Split portions of this set into another set. The values of the new set is
   * from the front of this set.
   *
   * @param splitLength The size of the new set.
   * @return The split set.
   */
  public SortValuesSet split(int splitLength)
  {
    long[] splitEntryIDs = new long[splitLength];
    byte[] splitValuesBytes = new byte[splitLength *
        ENCODED_ATTRIBUTE_VALUE_SIZE * vlvIndex.sortOrder.getSortKeys().length];

    long[] updatedEntryIDs = new long[entryIDs.length - splitEntryIDs.length];
    System.arraycopy(entryIDs, 0, updatedEntryIDs, 0, updatedEntryIDs.length);
    System.arraycopy(entryIDs, updatedEntryIDs.length, splitEntryIDs, 0,
                     splitEntryIDs.length);

    byte[] updatedValuesBytes = new byte[valuesBytes.length -
        splitValuesBytes.length];
    System.arraycopy(valuesBytes, 0, updatedValuesBytes, 0,
                     updatedValuesBytes.length);
    System.arraycopy(valuesBytes, updatedValuesBytes.length, splitValuesBytes,
                     0, splitValuesBytes.length);

    SortValuesSet splitValuesSet = new SortValuesSet(splitEntryIDs,
                                                     keyBytes,
                                                     splitValuesBytes,
                                                     vlvIndex, id2entry);

    entryIDs = updatedEntryIDs;
    valuesBytes = updatedValuesBytes;
    keyBytes = null;

    return splitValuesSet;
  }

  /**
   * Encode this set to its database format.
   *
   * @return The encoded bytes representing this set.
   */
  public byte[] toDatabase()
  {
    byte[] entryIDBytes = JebFormat.entryIDListToDatabase(entryIDs);
    byte[] concatBytes = new byte[entryIDBytes.length + valuesBytes.length + 4];
    int v = entryIDs.length;

    for (int j = 3; j >= 0; j--)
    {
      concatBytes[j] = (byte) (v & 0xFF);
      v >>>= 8;
    }

    System.arraycopy(entryIDBytes, 0, concatBytes, 4, entryIDBytes.length);
    System.arraycopy(valuesBytes, 0, concatBytes, entryIDBytes.length+4,
                     valuesBytes.length);

    return concatBytes;
  }

  /**
   * Get the size of the provided encoded set.
   *
   * @param bytes The encoded bytes of a SortValuesSet to decode the size from.
   * @param offset The byte offset to start decoding.
   * @return The size of the provided encoded set.
   */
  public static int getEncodedSize(byte[] bytes, int offset)
  {
    int v = 0;
    for (int i = offset; i < offset + 4; i++)
    {
      v <<= 8;
      v |= (bytes[i] & 0xFF);
    }
    return v;
  }

  /**
   * Get the IDs from the provided encoded set.
   *
   * @param bytes The encoded bytes of a SortValuesSet to decode the IDs from.
   * @param offset The byte offset to start decoding.
   * @return The decoded IDs in the provided encoded set.
   */
  public static long[] getEncodedIDs(byte[] bytes, int offset)
  {
    int length = getEncodedSize(bytes, offset);
    byte[] entryIDBytes = new byte[length * 8];
    System.arraycopy(bytes, offset+4, entryIDBytes, 0, entryIDBytes.length);
    return JebFormat.entryIDListFromDatabase(entryIDBytes);
  }

  /**
   * Searches this set for the specified values and entry ID using the binary
   * search algorithm.
   *
   * @param entryID The entry ID to match or -1 if not matching on entry ID.
   * @param values The values to match.
   * @return Index of the entry matching the values and optionally the entry ID
   * if it is found or a negative index if its not found.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE database.
   */
  int binarySearch(long entryID, AttributeValue[] values)
      throws JebException, DatabaseException, DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return -1;
    }

    int i = 0;
    for(int j = entryIDs.length - 1; i <= j;)
    {
      int k = i + j >> 1;
      int l = vlvIndex.comparator.compareValuesInSet(this, k, entryID, values);
      if(l < 0)
        i = k + 1;
      else
      if(l > 0)
        j = k - 1;
      else
        return k;
    }

    return -(i + 1);
  }

  /**
   * Retrieve the size of this set.
   *
   * @return The size of this set.
   */
  public int size()
  {
    if(entryIDs == null)
    {
      return 0;
    }

    return entryIDs.length;
  }

  /**
   * Retrieve the entry IDs in this set.
   *
   * @return The entry IDs in this set.
   */
  public long[] getEntryIDs()
  {
    return entryIDs;
  }

  private static int encodedLengthSize(int length)
  {
    if ((length & 0x000000FF) == length)
    {
      return 1;
    }
    else if ((length & 0x0000FFFF) == length)
    {
      return 2;
    }
    else if ((length & 0x00FFFFFF) == length)
    {
      return 3;
    }
    else
    {
      return 4;
    }
  }

  private byte[] attributeValuesToDatabase(AttributeValue[] values)
      throws DirectoryException
  {
    byte[] valuesBytes = new byte[values.length *
        (ENCODED_ATTRIBUTE_VALUE_SIZE)];
    for(int i = 0; i < values.length; i++)
    {
      AttributeValue value = values[i];
      int length;
      byte[] lengthBytes = new byte[ENCODED_VALUE_LENGTH_SIZE];
      if(value == null)
      {
        length = 0;
      }
      else
      {
        byte[] valueBytes = value.getNormalizedValueBytes();
        length = valueBytes.length;
        if(valueBytes.length > ENCODED_VALUE_SIZE)
        {
          System.arraycopy(valueBytes, 0, valuesBytes,
                           i * ENCODED_ATTRIBUTE_VALUE_SIZE +
                               ENCODED_VALUE_LENGTH_SIZE,
                           ENCODED_VALUE_SIZE);
        }
        else
        {
          System.arraycopy(valueBytes, 0, valuesBytes,
                           i * ENCODED_ATTRIBUTE_VALUE_SIZE +
                               ENCODED_VALUE_LENGTH_SIZE,
                           valueBytes.length);
        }
      }

      for (int j = ENCODED_VALUE_LENGTH_SIZE - 1; j >= 0; j--)
      {
        lengthBytes[j] = (byte) (length & 0xFF);
        length >>>= 8;
      }

      System.arraycopy(lengthBytes, 0, valuesBytes,
                       i * (ENCODED_ATTRIBUTE_VALUE_SIZE),
                       lengthBytes.length);
    }
    return valuesBytes;
  }

  /**
   * Returns the key to use for this set of sort values in the database.
   *
   * @return The key as an array of bytes that should be used for this set in
   * the database or NULL if this set is empty.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE database.
   */
  public byte[] getKeyBytes()
      throws JebException, DatabaseException, DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return null;
    }

    if(keyBytes != null)
    {
      return keyBytes;
    }

    SortKey[] sortKeys = vlvIndex.sortOrder.getSortKeys();
    int numValues = sortKeys.length;
    AttributeValue[] values =
        new AttributeValue[numValues];
    for (int i = (entryIDs.length - 1) * numValues, j = 0;
         i < entryIDs.length * numValues;
         i++, j++)
    {
      values[j] = new AttributeValue(sortKeys[j].getAttributeType(),
                                     new ASN1OctetString(getValue(i)));
    }
    keyBytes = vlvIndex.encodeKey(entryIDs[entryIDs.length - 1], values);
    return keyBytes;
  }

  /**
   * Returns the key to use for this set of sort values in the database.
   *
   * @return The key as a sort values object that should be used for this set in
   * the database or NULL if this set is empty or unbounded.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE database.
   */
  public SortValues getKeySortValues()
      throws JebException, DatabaseException, DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return null;
    }

    if(keyBytes != null && keyBytes.length == 0)
    {
      return null;
    }

    EntryID id = new EntryID(entryIDs[entryIDs.length - 1]);
    SortKey[] sortKeys = vlvIndex.sortOrder.getSortKeys();
    int numValues = sortKeys.length;
    AttributeValue[] values =
        new AttributeValue[numValues];
    for (int i = (entryIDs.length - 1) * numValues, j = 0;
         i < entryIDs.length * numValues;
         i++, j++)
    {
      values[j] = new AttributeValue(sortKeys[j].getAttributeType(),
                                     new ASN1OctetString(getValue(i)));
    }

    return new SortValues(id, values, vlvIndex.sortOrder);
  }

  /**
   * Returns the sort values at the index in this set.
   *
   * @param index The index of the sort values to get.
   * @return The sort values object at the specified index.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE database.
   **/
  public SortValues getSortValues(int index)
      throws JebException, DatabaseException, DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return null;
    }

    EntryID id = new EntryID(entryIDs[index]);
    SortKey[] sortKeys = vlvIndex.sortOrder.getSortKeys();
    int numValues = sortKeys.length;
    AttributeValue[] values =
        new AttributeValue[numValues];
    for (int i = index * numValues, j = 0;
         i < (index + 1) * numValues;
         i++, j++)
    {
      byte[] value = getValue(i);

      if(value != null)
      {
        values[j] = new AttributeValue(sortKeys[j].getAttributeType(),
                                       new ASN1OctetString(value));
      }
    }

    return new SortValues(id, values, vlvIndex.sortOrder);
  }

  /**
   * Retrieve an attribute value from this values set. The index is the
   * absolute index. (ie. for a sort on 3 attributes per entry, an vlvIndex of 6
   * will be the 1st attribute value of the 3rd entry).
   *
   * @param index The vlvIndex of the attribute value to retrieve.
   * @return The byte array representation of the attribute value.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE database.
   */
  public byte[] getValue(int index)
      throws JebException, DatabaseException, DirectoryException
  {
    // If values bytes is null, we have to get the value by getting the
    // entry by ID and getting the value.
    if(valuesBytes != null)
    {
      int pos = index * ENCODED_ATTRIBUTE_VALUE_SIZE;
      int length = 0;
      byte[] valueBytes;
      for (int k = 0; k < ENCODED_VALUE_LENGTH_SIZE; k++, pos++)
      {
        length <<= 8;
        length |= (valuesBytes[pos] & 0xFF);
      }

      if(length == 0)
      {
        return null;
      }
      // If the value has exceeded the max value size, we have to get the
      // value by getting the entry by ID.
      else if(length <= ENCODED_VALUE_SIZE && length > 0)
      {
        valueBytes = new byte[length];
        System.arraycopy(valuesBytes, pos, valueBytes, 0, length);

        return valueBytes;
      }
    }

    if(id2entry == null)
    {
      return new byte[0];
    }

    // Get the entry from id2entry and assign the values from the entry.
    // Once the values are assigned from the retrieved entry, it will
    // not be retrieve again from future compares.
    EntryID id = new EntryID(entryIDs[index /
        vlvIndex.sortOrder.getSortKeys().length]);
    AttributeValue[] values = cachedAttributeValues.get(id);
    if(values == null)
    {
      values = vlvIndex.getSortValues(id2entry.get(null, id));
      cachedAttributeValues.put(id, values);
    }
    int offset = index % values.length;
    return values[offset].getNormalizedValueBytes();
  }

}
