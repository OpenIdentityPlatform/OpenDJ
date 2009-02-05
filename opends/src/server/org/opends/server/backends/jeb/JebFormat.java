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

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

/**
 * Handles the disk representation of LDAP data.
 */
public class JebFormat
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


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
   */
  public static long entryIDFromDatabase(byte[] bytes)
  {
    long v = 0;
    for (int i = 0; i < 8; i++)
    {
      v <<= 8;
      v |= (bytes[i] & 0xFF);
    }
    return v;
  }

  /**
   * Decode an entry ID count from its database representation.
   *
   * @param bytes The database value of the entry ID count.
   * @return The entry ID count.
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
      v |= (bytes[0] & 0x7F);
      for (int i = 1; i < 8; i++)
      {
        v <<= 8;
        v |= (bytes[i] & 0xFF);
      }
      return v;
    }
    else
    {
      return Long.MAX_VALUE;
    }
  }

  /**
   * Decode an array of entry ID values from its database representation.
   *
   * @param bytes The raw database value, null if there is no value and
   *              hence no entry IDs. Note that this method will throw an
   *              ArrayIndexOutOfBoundsException if the bytes array length is
   *              not a multiple of 8.
   *
   * @return An array of entry ID values.
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
      v |= (decodedBytes[pos++] & 0xFFL);
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
      v |= (decodedBytes[pos++] & 0xFFL);
      entryIDList[i] = v;
    }

    return entryIDList;
  }

  /**
   * Encode an entry ID value to its database representation.
   * @param id The entry ID value to be encoded.
   * @return The encoded database value of the entry ID.
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
   * @param count The entry ID set count to be encoded.
   * @return The encoded database value of the entry ID.
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
   *
   * @return The encoded database value.
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
}
