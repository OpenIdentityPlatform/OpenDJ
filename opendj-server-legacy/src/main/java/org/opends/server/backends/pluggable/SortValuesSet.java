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

import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.SortKey;

/**
 * This class represents a partial sorted set of sorted entries in a VLV index.
 */
class SortValuesSet
{
  private long[] entryIDs;
  private int[] valuesBytesOffsets;
  private byte[] valuesBytes;
  private ByteString key;
  private final VLVIndex vlvIndex;

  /**
   * Construct an empty sort values set with the given information.
   *
   * @param vlvIndex The VLV index using this set.
   */
  SortValuesSet(VLVIndex vlvIndex)
  {
    this(vlvIndex, ByteString.empty(), null, null, null);
  }

  /**
   * Construct a sort values set from the database.
   *
   * @param key The database key used to locate this set.
   * @param value The bytes to decode and construct this set.
   * @param vlvIndex The VLV index using this set.
   */
  SortValuesSet(ByteString key, ByteString value, VLVIndex vlvIndex)
  {
    this.key = key;
    this.vlvIndex = vlvIndex;
    if(value == null)
    {
      entryIDs = new long[0];
      return;
    }

    entryIDs = getEncodedIDs(value);
    int valuesBytesOffset = entryIDs.length * 8 + 4;
    int valuesBytesLength = value.length() - valuesBytesOffset;
    valuesBytes = new byte[valuesBytesLength];
    System.arraycopy(value, valuesBytesOffset, valuesBytes, 0,
                     valuesBytesLength);
    this.valuesBytesOffsets = null;
  }

  private SortValuesSet(VLVIndex vlvIndex, ByteString key, long[] entryIDs,
      byte[] valuesBytes, int[] valuesBytesOffsets)
  {
    this.vlvIndex = vlvIndex;
    this.key = key;
    this.entryIDs = entryIDs;
    this.valuesBytes = valuesBytes;
    this.valuesBytesOffsets = valuesBytesOffsets;
  }

  /**
   * Add the given entryID and values from these sort values.
   *
   * @param sv The sort values to add.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  void add(SortValues sv) throws StorageRuntimeException, DirectoryException
  {
    add(sv.getEntryID(), sv.getValues(), sv.getTypes());
  }

  /**
   * Add the given entryID and values from this VLV index.
   *
   * @param entryID The entry ID to add.
   * @param values The values to add.
   * @param types The types of the values to add.
   * @return True if the information was successfully added or False
   * otherwise.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  private boolean add(long entryID, ByteString[] values, AttributeType[] types)
      throws StorageRuntimeException, DirectoryException
  {
    if(values == null)
    {
      return false;
    }

    if(entryIDs == null || entryIDs.length == 0)
    {
      entryIDs = new long[] { entryID };
      valuesBytes = attributeValuesToDatabase(values, types);
      if(valuesBytesOffsets != null)
      {
        valuesBytesOffsets = new int[] { 0 };
      }
      return true;
    }
    if (vlvIndex.compare(
        this, entryIDs.length - 1, entryID, values) < 0)
    {
      long[] updatedEntryIDs = new long[entryIDs.length + 1];
      System.arraycopy(entryIDs, 0, updatedEntryIDs, 0, entryIDs.length);
      updatedEntryIDs[entryIDs.length] = entryID;

      byte[] newValuesBytes = attributeValuesToDatabase(values, types);
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

      byte[] newValuesBytes = attributeValuesToDatabase(values, types);
      // BUG valuesBytesOffsets might be null ? If not why testing below ?
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
   * Remove the given entryID and values from these sort values.
   *
   * @param sv The sort values to remove.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  void remove(SortValues sv) throws DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return;
    }

    if(valuesBytesOffsets == null)
    {
      updateValuesBytesOffsets();
    }

    int pos = binarySearch(sv.getEntryID(), sv.getValues());
    if(pos < 0)
    {
      // Not found.
      return;
    }

    // Found it.
    long[] updatedEntryIDs = new long[entryIDs.length - 1];
    System.arraycopy(entryIDs, 0, updatedEntryIDs, 0, pos);
    System.arraycopy(entryIDs, pos + 1, updatedEntryIDs, pos,
                     entryIDs.length - pos - 1);
    int valuesLength;
    int valuesPos = valuesBytesOffsets[pos];
    if (pos < valuesBytesOffsets.length - 1)
    {
      valuesLength = valuesBytesOffsets[pos + 1] - valuesPos;
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
    System.arraycopy(valuesBytesOffsets, 0, updatedValuesBytesOffsets, 0, pos);
    // Update the rest of the offsets one by one - Expensive!
    for (int i = pos + 1; i < valuesBytesOffsets.length; i++)
    {
      updatedValuesBytesOffsets[i - 1] = valuesBytesOffsets[i] - valuesLength;
    }

    entryIDs = updatedEntryIDs;
    valuesBytes = updatedValuesBytes;
    valuesBytesOffsets = updatedValuesBytesOffsets;
  }

  /**
   * Split portions of this set into another set. The values of the new set is
   * from the end of this set.
   *
   * @param splitLength The size of the new set.
   * @return The split set.
   */
  SortValuesSet split(int splitLength)
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

    SortValuesSet splitValuesSet = new SortValuesSet(vlvIndex, key,
        splitEntryIDs, splitValuesBytes, splitValuesBytesOffsets);
    entryIDs = updatedEntryIDs;
    valuesBytes = updatedValuesBytes;
    valuesBytesOffsets = updatedValuesBytesOffsets;
    key = null;
    return splitValuesSet;
  }

  /**
   * Encode this set to its database format.
   *
   * @return The encoded bytes representing this set or null if
   * this set is empty.
   */
  ByteString toByteString()
  {
    if(size() == 0)
    {
      return null;
    }
    final ByteStringBuilder builder = new ByteStringBuilder(4 + entryIDs.length
        * 8 + valuesBytes.length);
    builder.append(entryIDs.length);
    for (long entryID : entryIDs)
    {
      builder.append(entryID);
    }
    builder.append(valuesBytes);
    return builder.toByteString();
  }

  /**
   * Get the IDs from the provided encoded set.
   *
   * @param bytes The encoded bytes of a SortValuesSet to decode the IDs from.
   * @return The decoded IDs in the provided encoded set.
   */
  static long[] getEncodedIDs(ByteString bytes)
  {
    final ByteSequenceReader reader = bytes.asReader();
    final int length = reader.getInt();
    final long[] entryIDSet = new long[length];
    for (int i = 0; i < length; i++)
    {
      entryIDSet[i] = reader.getLong();
    }
    return entryIDSet;
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
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  int binarySearch(long entryID, ByteString... values)
      throws StorageRuntimeException, DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return -1;
    }

    int i = 0;
    for(int j = entryIDs.length - 1; i <= j;)
    {
      int k = i + j >> 1;
      int l = vlvIndex.compare(this, k, entryID, values);
      if (l < 0)
      {
        i = k + 1;
      }
      else if (l > 0)
      {
        j = k - 1;
      }
      else
      {
        return k;
      }
    }

    return -(i + 1);
  }

  /**
   * Retrieve the size of this set.
   *
   * @return The size of this set.
   */
  int size()
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
  long[] getEntryIDs()
  {
    return entryIDs;
  }

  private byte[] attributeValuesToDatabase(ByteString[] values,
      AttributeType[] types) throws DirectoryException
  {
    try
    {
      final ByteStringBuilder builder = new ByteStringBuilder();

      for (int i = 0; i < values.length; i++)
      {
        final ByteString v = values[i];
        if (v == null)
        {
          builder.appendBERLength(0);
        }
        else
        {
          final MatchingRule eqRule = types[i].getEqualityMatchingRule();
          final ByteString nv = eqRule.normalizeAttributeValue(v);
          builder.appendBERLength(nv.length());
          builder.append(nv);
        }
      }
      builder.trimToSize();

      return builder.getBackingArray();
    }
    catch (DecodeException e)
    {
      throw new DirectoryException(
          ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
  }

  /**
   * Returns the key to use for this set of sort values in the database.
   *
   * @return The key as an array of bytes that should be used for this set in
   * the database or NULL if this set is empty.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  ByteString getKeyBytes()
      throws StorageRuntimeException, DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return null;
    }

    if(key != null)
    {
      return key;
    }

    if(valuesBytesOffsets == null)
    {
      updateValuesBytesOffsets();
    }

    int vBytesPos = valuesBytesOffsets[valuesBytesOffsets.length - 1];
    int vBytesLength = valuesBytes.length - vBytesPos;

    ByteString idBytes = ByteString.valueOf(entryIDs[entryIDs.length - 1]);
    ByteStringBuilder keyBytes = new ByteStringBuilder(vBytesLength + idBytes.length());
    keyBytes.append(valuesBytes, vBytesPos, vBytesLength);
    keyBytes.append(idBytes);

    key = keyBytes.toByteString();
    return key;
  }

  /**
   * Returns the key to use for this set of sort values in the database.
   *
   * @return The key as a sort values object that should be used for this set in
   * the database or NULL if this set is empty or unbounded.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  SortValues getKeySortValues()
      throws StorageRuntimeException, DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return null;
    }

    if(key != null && key.length() == 0)
    {
      return null;
    }

    EntryID id = new EntryID(entryIDs[entryIDs.length - 1]);
    SortKey[] sortKeys = vlvIndex.getSortOrder().getSortKeys();
    int numValues = sortKeys.length;
    ByteString[] values = new ByteString[numValues];
    for (int i = (entryIDs.length - 1) * numValues, j = 0;
         i < entryIDs.length * numValues;
         i++, j++)
    {
      values[j] = getValue(i);
    }

    return new SortValues(id, values, vlvIndex.getSortOrder());
  }

  /**
   * Returns the sort values at the index in this set.
   *
   * @param index The index of the sort values to get.
   * @return The sort values object at the specified index.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  SortValues getSortValues(int index) throws StorageRuntimeException, DirectoryException
  {
    if(entryIDs == null || entryIDs.length == 0)
    {
      return null;
    }

    EntryID id = new EntryID(entryIDs[index]);
    SortKey[] sortKeys = vlvIndex.getSortOrder().getSortKeys();
    int numValues = sortKeys.length;
    ByteString[] values = new ByteString[numValues];
    for (int i = index * numValues, j = 0;
         i < (index + 1) * numValues;
         i++, j++)
    {
      values[j] = getValue(i);
    }

    return new SortValues(id, values, vlvIndex.getSortOrder());
  }

  private void updateValuesBytesOffsets()
  {
    valuesBytesOffsets = new int[entryIDs.length];
    int vBytesPos = 0;
    int numAttributes = vlvIndex.getSortOrder().getSortKeys().length;

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
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  ByteString getValue(int index)
      throws StorageRuntimeException, DirectoryException
  {
    if(valuesBytesOffsets == null)
    {
      updateValuesBytesOffsets();
    }
    int numAttributes = vlvIndex.getSortOrder().getSortKeys().length;
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
          return ByteString.wrap(valueBytes);
        }
      }
      else
      {
        vBytesPos += valueLength;
      }
    }
    return ByteString.empty();
  }
}
