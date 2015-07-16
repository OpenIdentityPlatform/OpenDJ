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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.types.DN;

/**
 * Handles the disk representation of LDAP data.
 */
public class JebFormat
{

  /**
   * The format version used by this class to encode and decode a DatabaseEntry.
   */
  public static final byte FORMAT_VERSION = 0x01;

  /**
   * The ASN1 tag for the DatabaseEntry type.
   */
  public static final byte TAG_DATABASE_ENTRY = 0x60;

  /**
   * The ASN1 tag for the DirectoryServerEntry type.
   */
  public static final byte TAG_DIRECTORY_SERVER_ENTRY = 0x61;

  /**
   * Decode an entry ID value from its database representation. Note that
   * this method will throw an ArrayIndexOutOfBoundsException if the bytes
   * array length is less than 8.
   *
   * @param bytes The database value of the entry ID.
   * @return The entry ID value.
   * @see #entryIDToDatabase(long)
   */
  public static long entryIDFromDatabase(byte[] bytes)
  {
    return toLong(bytes, 0, 8);
  }

  /**
   * Decode a long from a byte array, starting at start index and ending at end
   * index.
   *
   * @param bytes
   *          The bytes value of the long.
   * @param start
   *          the array index where to start computing the long
   * @param end
   *          the array index exclusive where to end computing the long
   * @return the long representation of the read bytes.
   * @throws ArrayIndexOutOfBoundsException
   *           if the bytes array length is less than end.
   */
  public static long toLong(byte[] bytes, int start, int end)
      throws ArrayIndexOutOfBoundsException
  {
    long v = 0;
    for (int i = start; i < end; i++)
    {
      v <<= 8;
      v |= bytes[i] & 0xFF;
    }
    return v;
  }

  /**
   * Decode an entry ID count from its database representation.
   *
   * @param bytes The database value of the entry ID count.
   * @return The entry ID count.
   *  Cannot be negative if encoded with #entryIDUndefinedSizeToDatabase(long)
   * @see #entryIDUndefinedSizeToDatabase(long)
   */
  public static long entryIDUndefinedSizeFromDatabase(byte[] bytes)
  {
    if(bytes == null)
    {
      return 0;
    }

    if(bytes.length == 8)
    {
      long v = 0;
      v |= bytes[0] & 0x7F;
      for (int i = 1; i < 8; i++)
      {
        v <<= 8;
        v |= bytes[i] & 0xFF;
      }
      return v;
    }
    return Long.MAX_VALUE;
  }

  /**
   * Decode an array of entry ID values from its database representation.
   *
   * @param bytes The raw database value, null if there is no value and
   *              hence no entry IDs. Note that this method will throw an
   *              ArrayIndexOutOfBoundsException if the bytes array length is
   *              not a multiple of 8.
   * @return An array of entry ID values.
   * @see #entryIDListToDatabase(long[])
   */
  public static long[] entryIDListFromDatabase(byte[] bytes)
  {
    byte[] decodedBytes = bytes;

    int count = decodedBytes.length / 8;
    long[] entryIDList = new long[count];
    for (int pos = 0, i = 0; i < count; i++)
    {
      long v = 0;
      v |= (decodedBytes[pos++] & 0xFFL) << 56;
      v |= (decodedBytes[pos++] & 0xFFL) << 48;
      v |= (decodedBytes[pos++] & 0xFFL) << 40;
      v |= (decodedBytes[pos++] & 0xFFL) << 32;
      v |= (decodedBytes[pos++] & 0xFFL) << 24;
      v |= (decodedBytes[pos++] & 0xFFL) << 16;
      v |= (decodedBytes[pos++] & 0xFFL) << 8;
      v |= decodedBytes[pos++] & 0xFFL;
      entryIDList[i] = v;
    }

    return entryIDList;
  }

  /**
   * Decode a integer array using the specified byte array read from DB.
   *
   * @param bytes The byte array.
   * @return An integer array.
   */
  public static int[] intArrayFromDatabaseBytes(byte[] bytes) {
    byte[] decodedBytes = bytes;

    int count = decodedBytes.length / 8;
    int[] entryIDList = new int[count];
    for (int pos = 0, i = 0; i < count; i++) {
      int v = 0;
      pos +=4;
      v |= (decodedBytes[pos++] & 0xFFL) << 24;
      v |= (decodedBytes[pos++] & 0xFFL) << 16;
      v |= (decodedBytes[pos++] & 0xFFL) << 8;
      v |= decodedBytes[pos++] & 0xFFL;
      entryIDList[i] = v;
    }

    return entryIDList;
  }

  /**
   * Encode an entry ID value to its database representation.
   *
   * @param id The entry ID value to be encoded.
   * @return The encoded database value of the entry ID.
   * @see #entryIDFromDatabase(byte[])
   */
  public static byte[] entryIDToDatabase(long id)
  {
    byte[] bytes = new byte[8];
    long v = id;
    for (int i = 7; i >= 0; i--)
    {
      bytes[i] = (byte) (v & 0xFF);
      v >>>= 8;
    }
    return bytes;
  }

  /**
   * Encode an entry ID set count to its database representation.
   *
   * @param count The entry ID set count to be encoded.
   * @return The encoded database value of the entry ID set count.
   * @see #entryIDUndefinedSizeFromDatabase(byte[])
   */
  public static byte[] entryIDUndefinedSizeToDatabase(long count)
  {
    byte[] bytes = new byte[8];
    long v = count;
    for (int i = 7; i >= 1; i--)
    {
      bytes[i] = (byte) (v & 0xFF);
      v >>>= 8;
    }
    bytes[0] = (byte) ((v | 0x80) & 0xFF);
    return bytes;
  }

  /**
   * Encode an array of entry ID values to its database representation.
   *
   * @param entryIDArray An array of entry ID values.
   * @return The encoded database value.
   * @see #entryIDListFromDatabase(byte[])
   */
  public static byte[] entryIDListToDatabase(long[] entryIDArray)
  {
    if (entryIDArray.length == 0)
    {
      // Zero values
      return null;
    }

    byte[] bytes = new byte[8*entryIDArray.length];
    for (int pos = 0, i = 0; i < entryIDArray.length; i++)
    {
      long v = entryIDArray[i];
      bytes[pos++] = (byte) ((v >>> 56) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 48) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 40) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 32) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 24) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 16) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 8) & 0xFF);
      bytes[pos++] = (byte) (v & 0xFF);
    }

    return bytes;
  }

  /**
   * Find the length of bytes that represents the superior DN of the given
   * DN key. The superior DN is represented by the initial bytes of the DN key.
   *
   * @param dnKey The database key value of the DN.
   * @return The length of the superior DN or -1 if the given dn is the
   *         root DN or 0 if the superior DN is removed.
   */
  public static int findDNKeyParent(byte[] dnKey)
  {
    return findDNKeyParent(dnKey, 0, dnKey.length);
  }

  /**
   * Find the length of bytes that represents the superior DN of the given
   * DN key. The superior DN is represented by the initial bytes of the DN key.
   *
   * @param dnKey The database key value of the DN.
   * @param offset Starting position in the database key data.
   * @param length The length of the database key data.
   * @return The length of the superior DN or -1 if the given dn is the
   *         root DN or 0 if the superior DN is removed.
   */
  public static int findDNKeyParent(byte[] dnKey, int offset, int length)
  {
    if (length == 0)
    {
      // This is the root or base DN
      return -1;
    }

    // We will walk backwards through the buffer and
    // find the first unescaped NORMALIZED_RDN_SEPARATOR
    for (int i = offset+length - 1; i >= offset; i--)
    {
      if (dnKey[i] == DN.NORMALIZED_RDN_SEPARATOR && i-1 >= offset && dnKey[i-1] != DN.NORMALIZED_ESC_BYTE)
      {
        return i;
      }
    }
    return offset;
  }

  /**
   * Create a DN database key from an entry DN.
   *
   * @param dn The entry DN.
   * @param prefixRDNs The number of prefix RDNs to remove from the encoded
   *                   representation.
   * @return A DatabaseEntry containing the key.
   */
  public static byte[] dnToDNKey(DN dn, int prefixRDNs)
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    int startSize = dn.size() - prefixRDNs - 1;
    for (int i = startSize; i >= 0; i--)
    {
        builder.append(DN.NORMALIZED_RDN_SEPARATOR);
        dn.getRDN(i).toNormalizedByteString(builder);
    }

    return builder.toByteArray();
  }


}
