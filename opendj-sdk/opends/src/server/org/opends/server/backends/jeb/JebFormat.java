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


import org.opends.server.api.CompressedSchema;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

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
   * Decode a DatabaseEntry.  The encoded bytes may be compressed and/or
   * encrypted.
   *
   * @param bytes The encoded bytes of a DatabaseEntry.
   * @return The decoded bytes.
   * @throws ASN1Exception If the data is not in the expected ASN.1 encoding
   * format.
   * @throws DataFormatException If an error occurs while trying to decompress
   * compressed data.
   */
  static public byte[] decodeDatabaseEntry(byte[] bytes)
       throws ASN1Exception,DataFormatException
  {
    // FIXME: This array copy could be very costly on performance. We need to
    // FIXME: find a faster way to implement this versioning feature.
    // Remove version number from the encoded bytes
    byte[] encodedBytes = new byte[bytes.length - 1];
    System.arraycopy(bytes, 1, encodedBytes, 0, encodedBytes.length);

    // Decode the sequence.
    List<ASN1Element> elements;
    elements = ASN1Sequence.decodeAsSequence(encodedBytes).elements();

    // Decode the uncompressed size.
    int uncompressedSize;
    uncompressedSize = elements.get(0).decodeAsInteger().intValue();

    // Decode the data bytes.
    byte[] dataBytes;
    dataBytes = elements.get(1).decodeAsOctetString().value();

    byte[] uncompressedBytes;
    if (uncompressedSize == 0)
    {
      // The bytes are not compressed.
      uncompressedBytes = dataBytes;
    }
    else
    {
      // The bytes are compressed.
      CryptoManager cryptoManager = DirectoryServer.getCryptoManager();
      uncompressedBytes = new byte[uncompressedSize];
      /* int len = */ cryptoManager.uncompress(dataBytes, uncompressedBytes);
    }

    return uncompressedBytes;
  }

  /**
   * Decodes an entry from its database representation.
   * <p>
   * An entry on disk is ASN1 encoded in this format:
   *
   * <pre>
   * DatabaseEntry ::= [APPLICATION 0] IMPLICIT SEQUENCE {
   *  uncompressedSize      INTEGER,      -- A zero value means not compressed.
   *  dataBytes             OCTET STRING  -- Optionally compressed encoding of
   *                                         the data bytes.
   * }
   *
   * ID2EntryValue ::= DatabaseEntry
   *  -- Where dataBytes contains an encoding of DirectoryServerEntry.
   *
   * DirectoryServerEntry ::= [APPLICATION 1] IMPLICIT SEQUENCE {
   *  dn                      LDAPDN,
   *  objectClasses           SET OF LDAPString,
   *  userAttributes          AttributeList,
   *  operationalAttributes   AttributeList
   * }
   * </pre>
   *
   * @param bytes A byte array containing the encoded database value.
   * @param compressedSchema The compressed schema manager to use when decoding.
   * @return The decoded entry.
   * @throws ASN1Exception If the data is not in the expected ASN.1 encoding
   * format.
   * @throws LDAPException If the data is not in the expected ASN.1 encoding
   * format.
   * @throws DataFormatException If an error occurs while trying to decompress
   * compressed data.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  static public Entry entryFromDatabase(byte[] bytes,
                                        CompressedSchema compressedSchema)
       throws DirectoryException,ASN1Exception,LDAPException,DataFormatException
  {
    byte[] uncompressedBytes = decodeDatabaseEntry(bytes);
    return decodeDirectoryServerEntry(uncompressedBytes, compressedSchema);
  }

  /**
   * Decode an entry from a ASN1 encoded DirectoryServerEntry.
   *
   * @param bytes A byte array containing the encoding of DirectoryServerEntry.
   * @param compressedSchema The compressed schema manager to use when decoding.
   * @return The decoded entry.
   * @throws ASN1Exception If the data is not in the expected ASN.1 encoding
   * format.
   * @throws LDAPException If the data is not in the expected ASN.1 encoding
   * format.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  static private Entry decodeDirectoryServerEntry(byte[] bytes,
                            CompressedSchema compressedSchema)
       throws DirectoryException,ASN1Exception,LDAPException
  {
    return Entry.decode(bytes, compressedSchema);
  }

  /**
   * Encodes a DatabaseEntry.  The encoded bytes may be compressed and/or
   * encrypted.
   *
   * @param bytes The bytes to encode.
   * @param dataConfig Compression and cryptographic options.
   * @return A byte array containing the encoded DatabaseEntry.
   */
  static public byte[] encodeDatabaseEntry(byte[] bytes, DataConfig dataConfig)
  {
    int uncompressedSize = 0;

    // Do optional compression.
    CryptoManager cryptoManager = DirectoryServer.getCryptoManager();
    if (dataConfig.isCompressed() && cryptoManager != null)
    {
      byte[] compressedBuffer = new byte[bytes.length];
      int compressedSize = cryptoManager.compress(bytes,
                                                  compressedBuffer);
      if (compressedSize != -1)
      {
        // Compression was successful.
        uncompressedSize = bytes.length;
        bytes = new byte[compressedSize];
        System.arraycopy(compressedBuffer, 0, bytes, 0, compressedSize);

        if(debugEnabled())
        {
          TRACER.debugInfo("Compression %d/%d%n",
                    compressedSize, uncompressedSize);
        }

      }

    }

    // Encode the DatabaseEntry.
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Integer(uncompressedSize));
    elements.add(new ASN1OctetString(bytes));
    byte[] asn1Sequence =
        new ASN1Sequence(TAG_DATABASE_ENTRY, elements).encode();

    // FIXME: This array copy could be very costly on performance. We need to
    // FIXME: find a faster way to implement this versioning feature.
    // Prefix version number to the encoded bytes
    byte[] encodedBytes = new byte[asn1Sequence.length + 1];
    encodedBytes[0] = FORMAT_VERSION;
    System.arraycopy(asn1Sequence, 0, encodedBytes, 1, asn1Sequence.length);

    return encodedBytes;
  }

  /**
   * Encodes an entry to the raw database format, with optional compression.
   *
   * @param entry The entry to encode.
   * @param dataConfig Compression and cryptographic options.
   * @return A byte array containing the encoded database value.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  static public byte[] entryToDatabase(Entry entry, DataConfig dataConfig)
         throws DirectoryException
  {
    byte[] uncompressedBytes = encodeDirectoryServerEntry(entry,
                                             dataConfig.getEntryEncodeConfig());
    return encodeDatabaseEntry(uncompressedBytes, dataConfig);
  }

  /**
   * Encodes an entry to the raw database format, without compression.
   *
   * @param entry The entry to encode.
   * @return A byte array containing the encoded database value.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  static public byte[] entryToDatabase(Entry entry)
         throws DirectoryException
  {
    return entryToDatabase(entry, new DataConfig(false, false, null));
  }

  /**
   * Encode a ASN1 DirectoryServerEntry.
   *
   * @param entry The entry to encode.
   * @encodeConfig The configuration to use when encoding the entry.
   * @return A byte array containing the encoded DirectoryServerEntry.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  static private byte[] encodeDirectoryServerEntry(Entry entry,
                                                 EntryEncodeConfig encodeConfig)
         throws DirectoryException
  {
    return entry.encode(encodeConfig);
  }

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

   /**
   * Get the version number of the DatabaseEntry.
   *
   * @param bytes The encoded bytes of a DatabaseEntry.
   * @return The version number.
   */
  public static byte getEntryVersion(byte[] bytes)
  {
    return bytes[0];
  }

}
