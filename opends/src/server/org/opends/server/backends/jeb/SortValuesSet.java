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
import org.opends.server.protocols.asn1.ASN1Element;

import java.util.LinkedList;

import com.sleepycat.je.DatabaseException;

/**
 * This class representsa  partial sorted set of sorted entries in a VLV
 * index.
 */
public class SortValuesSet
{
  private long[] entryIDs;

  private int[] valuesBytesOffsets;

  private byte[] valuesBytes;

  private byte[] keyBytes;

  private VLVIndex vlvIndex;

  /**
   * Construct an empty sort values set with the given information.
   *
   * @param vlvIndex The VLV index using this set.
   */
  public SortValuesSet(VLVIndex vlvIndex)
  {
    this.keyBytes = new byte[0];
    this.entryIDs = null;
    this.valuesBytes = null;
    this.valuesBytesOffsets = null;
    this.vlvIndex = vlvIndex;
  }

  /**
   * Construct a sort values set from the database.
   *
   * @param keyBytes The database key used to locate this set.
   * @param dataBytes The bytes to decode and construct this set.
   * @param vlvIndex The VLV index using this set.
   */
  public SortValuesSet(byte[] keyBytes, byte[] dataBytes, VLVIndex vlvIndex)
  {
    this.keyBytes = keyBytes;
    this.vlvIndex = vlvIndex;
    if(dataBytes == null)
    {
      entryIDs = new long[0];
      return;
    }

    entryIDs = getEncodedIDs(dataBytes, 0);
    int valuesBytesOffset = entryIDs.length * 8 + 4;
    int valuesBytesLength = dataBytes.length - valuesBytesOffset;
    valuesBytes = new byte[valuesBytesLength];
    System.arraycopy(dataBytes, valuesBytesOffset, valuesBytes, 0,
                     valuesBytesLength);
    this.valuesBytesOffsets = null;
  }

  private SortValuesSet()
  {}

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
      if(valuesBytesOffsets != null)
      {
        valuesBytesOffsets = new int[1];
        valuesBytesOffsets[0] = 0;
      }
      return true;
    }
    if(vlvIndex.comparator.compare(this, entryIDs.length - 1, entryID,
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

      if(valuesBytesOffsets != null)
      {
        int[] updatedValuesBytesOffsets =
            new int[valuesBytesOffsets.length + 1];
        System.arraycopy(valuesBytesOffsets, 0, updatedValuesBytesOffsets,
            0, valuesBytesOffsets.length);
        updatedValuesBytesOffsets[valuesBytesOffsets.length] =
            updatedValuesBytes.length - newValuesBytes.length;
        valuesBytesOffsets = updatedValuesBytesOffsets;
      }

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
      int valuesPos = valuesBytesOffsets[pos];
      byte[] updatedValuesBytes = new byte[valuesBytes.length +
          newValuesBytes.length];
      System.arraycopy(valuesBytes, 0, updatedValuesBytes, 0, valuesPos);
      System.arraycopy(valuesBytes, valuesPos,  updatedValuesBytes,
                       valuesPos + newValuesBytes.length,
                       valuesBytes.length - valuesPos);
      System.arraycopy(newValuesBytes, 0, updatedValuesBytes, valuesPos,
                       newValuesBytes.length);

      if(valuesBytesOffsets != null)
      {
        int[] updatedValuesBytesOffsets =
            new int[valuesBytesOffsets.length + 1];
        System.arraycopy(valuesBytesOffsets, 0, updatedValuesBytesOffsets,
            0, pos);
        // Update the rest of the offsets one by one - Expensive!
        for(int i = pos; i < valuesBytesOffsets.length; i++)
        {
          updatedValuesBytesOffsets[i+1] =
              valuesBytesOffsets[i] + newValuesBytes.length;
        }
        updatedValuesBytesOffsets[pos] = valuesBytesOffsets[pos];
        valuesBytesOffsets = updatedValuesBytesOffsets;
      }

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

    if(valuesBytesOffsets == null)
    {
      updateValuesBytesOffsets();
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
      int valuesLength;
      int valuesPos = valuesBytesOffsets[pos];
      if(pos < valuesBytesOffsets.length - 1)
      {
        valuesLength = valuesBytesOffsets[pos+1] - valuesPos;
      }
      else
      {
        valuesLength = valuesBytes.length - valuesPos;
      }
      byte[] updatedValuesBytes = new byte[valuesBytes.length - valuesLength];
      System.arraycopy(valuesBytes, 0, updatedValuesBytes, 0, valuesPos);
      System.arraycopy(valuesBytes, valuesPos + valuesLength,
                       updatedValuesBytes, valuesPos,
                       valuesBytes.length - valuesPos - valuesLength);

      int[] updatedValuesBytesOffsets = new int[valuesBytesOffsets.length - 1];
      System.arraycopy(valuesBytesOffsets, 0, updatedValuesBytesOffsets,
          0, pos);
      // Update the rest of the offsets one by one - Expensive!
      for(int i = pos + 1; i < valuesBytesOffsets.length; i++)
      {
        updatedValuesBytesOffsets[i-1] =
            valuesBytesOffsets[i] - valuesLength;
      }

      entryIDs = updatedEntryIDs;
      valuesBytes = updatedValuesBytes;
      valuesBytesOffsets = updatedValuesBytesOffsets;
      return true;
    }
  }

  /**
   * Split portions of this set into another set. The values of the new set is
   * from the end of this set.
   *
   * @param splitLength The size of the new set.
   * @return The split set.
   */
  public SortValuesSet split(int splitLength)
  {
    if(valuesBytesOffsets == null)
    {
      updateValuesBytesOffsets();
    }

    long[] splitEntryIDs = new long[splitLength];
    byte[] splitValuesBytes = new byte[valuesBytes.length -
        valuesBytesOffsets[valuesBytesOffsets.length - splitLength]];
    int[] splitValuesBytesOffsets = new int[splitLength];

    long[] updatedEntryIDs = new long[entryIDs.length - splitEntryIDs.length];
    System.arraycopy(entryIDs, 0, updatedEntryIDs, 0, updatedEntryIDs.length);
    System.arraycopy(entryIDs, updatedEntryIDs.length, splitEntryIDs, 0,
                     splitEntryIDs.length);

    byte[] updatedValuesBytes =
        new byte[valuesBytesOffsets[valuesBytesOffsets.length - splitLength]];
    System.arraycopy(valuesBytes, 0, updatedValuesBytes, 0,
                     updatedValuesBytes.length);
    System.arraycopy(valuesBytes, updatedValuesBytes.length, splitValuesBytes,
                     0, splitValuesBytes.length);

    int[] updatedValuesBytesOffsets =
        new int[valuesBytesOffsets.length - splitValuesBytesOffsets.length];
    System.arraycopy(valuesBytesOffsets, 0, updatedValuesBytesOffsets,
        0, updatedValuesBytesOffsets.length);
    for(int i = updatedValuesBytesOffsets.length;
        i < valuesBytesOffsets.length; i++)
    {
      splitValuesBytesOffsets[i - updatedValuesBytesOffsets.length] =
          valuesBytesOffsets[i] -
              valuesBytesOffsets[updatedValuesBytesOffsets.length];
    }

    SortValuesSet splitValuesSet = new SortValuesSet();

    splitValuesSet.entryIDs = splitEntryIDs;
    splitValuesSet.keyBytes = this.keyBytes;
    splitValuesSet.valuesBytes = splitValuesBytes;
    splitValuesSet.valuesBytesOffsets = splitValuesBytesOffsets;
    splitValuesSet.vlvIndex = this.vlvIndex;

    entryIDs = updatedEntryIDs;
    valuesBytes = updatedValuesBytes;
    valuesBytesOffsets = updatedValuesBytesOffsets;
    keyBytes = null;

    return splitValuesSet;
  }

  /**
   * Encode this set to its database format.
   *
   * @return The encoded bytes representing this set or null if
   * this set is empty.
   */
  public byte[] toDatabase()
  {
    if(size() == 0)
    {
      return null;
    }

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
      int l = vlvIndex.comparator.compare(this, k, entryID, values);
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

  private byte[] attributeValuesToDatabase(AttributeValue[] values)
      throws DirectoryException
  {
    int totalValueBytes = 0;
    LinkedList<byte[]> valueBytes = new LinkedList<byte[]>();
    for (AttributeValue v : values)
    {
      byte[] vBytes;
      if(v == null)
      {
        vBytes = new byte[0];
      }
      else
      {
        vBytes = v.getNormalizedValueBytes();
      }
      byte[] vLength = ASN1Element.encodeLength(vBytes.length);
      valueBytes.add(vLength);
      valueBytes.add(vBytes);
      totalValueBytes += vLength.length + vBytes.length;
    }

    byte[] attrBytes = new byte[totalValueBytes];

    int pos = 0;
    for (byte[] b : valueBytes)
    {
      System.arraycopy(b, 0, attrBytes, pos, b.length);
      pos += b.length;
    }

    return attrBytes;
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

    if(valuesBytesOffsets == null)
    {
      updateValuesBytesOffsets();
    }

    int vBytesPos = valuesBytesOffsets[valuesBytesOffsets.length - 1];
    int vBytesLength = valuesBytes.length - vBytesPos;

    byte[] idBytes =
        JebFormat.entryIDToDatabase(entryIDs[entryIDs.length - 1]);
    keyBytes =
        new byte[vBytesLength + idBytes.length];

    System.arraycopy(valuesBytes, vBytesPos, keyBytes, 0, vBytesLength);
    System.arraycopy(idBytes, 0, keyBytes, vBytesLength, idBytes.length);

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

  private void updateValuesBytesOffsets()
  {
    valuesBytesOffsets = new int[entryIDs.length];
    int vBytesPos = 0;
    int numAttributes = vlvIndex.sortOrder.getSortKeys().length;

    for(int pos = 0; pos < entryIDs.length; pos++)
    {
      valuesBytesOffsets[pos] = vBytesPos;

      for(int i = 0; i < numAttributes; i++)
      {
        int valueLength = valuesBytes[vBytesPos] & 0x7F;
        if (valueLength != valuesBytes[vBytesPos++])
        {
          int valueLengthBytes = valueLength;
          valueLength = 0;
          for (int j=0; j < valueLengthBytes; j++, vBytesPos++)
          {
            valueLength = (valueLength << 8) | (valuesBytes[vBytesPos] & 0xFF);
          }
        }

        vBytesPos += valueLength;
      }
    }
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
    if(valuesBytesOffsets == null)
    {
      updateValuesBytesOffsets();
    }
    int numAttributes = vlvIndex.sortOrder.getSortKeys().length;
    int vIndex = index / numAttributes;
    int vOffset = index % numAttributes;
    int vBytesPos = valuesBytesOffsets[vIndex];

    // Find the desired value in the sort order set.
    for(int i = 0; i <= vOffset; i++)
    {
      int valueLength = valuesBytes[vBytesPos] & 0x7F;
      if (valueLength != valuesBytes[vBytesPos++])
      {
        int valueLengthBytes = valueLength;
        valueLength = 0;
        for (int j=0; j < valueLengthBytes; j++, vBytesPos++)
        {
          valueLength = (valueLength << 8) | (valuesBytes[vBytesPos] & 0xFF);
        }
      }

      if(i == vOffset)
      {
        if(valueLength == 0)
        {
          return null;
        }
        else
        {
          byte[] valueBytes = new byte[valueLength];
          System.arraycopy(valuesBytes, vBytesPos, valueBytes, 0, valueLength);
          return valueBytes;
        }
      }
      else
      {
        vBytesPos += valueLength;
      }
    }
    return new byte[0];
  }
}
