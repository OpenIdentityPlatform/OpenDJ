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


import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Set;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.zip.DataFormatException;

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
   * @return The decoded entry.
   * @throws ASN1Exception If the data is not in the expected ASN.1 encoding
   * format.
   * @throws LDAPException If the data is not in the expected ASN.1 encoding
   * format.
   * @throws DataFormatException If an error occurs while trying to decompress
   * compressed data.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  static public Entry entryFromDatabase(byte[] bytes)
       throws DirectoryException,ASN1Exception,LDAPException,DataFormatException
  {
    byte[] uncompressedBytes = decodeDatabaseEntry(bytes);
    return decodeDirectoryServerEntry(uncompressedBytes);
  }

  /**
   * Decode an entry from a ASN1 encoded DirectoryServerEntry.
   *
   * @param bytes A byte array containing the encoding of DirectoryServerEntry.
   * @return The decoded entry.
   * @throws ASN1Exception If the data is not in the expected ASN.1 encoding
   * format.
   * @throws LDAPException If the data is not in the expected ASN.1 encoding
   * format.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  static private Entry decodeDirectoryServerEntry(byte[] bytes)
       throws DirectoryException,ASN1Exception,LDAPException
  {
    HashMap<ObjectClass, String> objectClasses;
    HashMap<AttributeType, List<Attribute>> userAttributes =
         new HashMap<AttributeType, List<Attribute>>();
    HashMap<AttributeType, List<Attribute>> operationalAttributes =
         new HashMap<AttributeType, List<Attribute>>();

    // Decode the sequence.
    List<ASN1Element> elements;
    elements = ASN1Sequence.decodeAsSequence(bytes).elements();

    // Decode the dn (LDAPDN).
    DN dn;
    dn = DN.decode(elements.get(0).decodeAsOctetString().stringValue());

    // Decode the object classes (SET OF LDAPString).
    List<ASN1Element> attrElements =
         elements.get(1).decodeAsSet().elements();
    objectClasses =
    new HashMap<ObjectClass, String>(attrElements.size() * 4 / 3);
    for (ASN1Element e : attrElements)
    {
      String ocName = e.decodeAsOctetString().stringValue();
      String lowerOCName = ocName.toLowerCase();

      ObjectClass objectClass = DirectoryServer.getObjectClass(lowerOCName);
      if (objectClass == null)
      {
        objectClass = DirectoryServer.getDefaultObjectClass(ocName);
      }

      objectClasses.put(objectClass, ocName);
    }

    // Decode the user attributes (AttributeList).
    attrElements = elements.get(2).decodeAsSequence().elements();
    for (ASN1Element e : attrElements)
    {
      Attribute a = LDAPAttribute.decode(e).toAttribute();
      List<Attribute> attrList;
      attrList = userAttributes.get(a.getAttributeType());
      if (attrList == null)
      {
        attrList = new ArrayList<Attribute>(1);
        attrList.add(a);
        userAttributes.put(a.getAttributeType(), attrList);
      }
      else
      {
        attrList.add(a);
      }
    }

    // Decode the operational attributes (AttributeList).
    attrElements = elements.get(3).decodeAsSequence().elements();
    for (ASN1Element e : attrElements)
    {
      Attribute a = LDAPAttribute.decode(e).toAttribute();
      List<Attribute> attrList;
      attrList = operationalAttributes.get(a.getAttributeType());
      if (attrList == null)
      {
        attrList = new ArrayList<Attribute>(1);
        attrList.add(a);
        operationalAttributes.put(a.getAttributeType(), attrList);
      }
      else
      {
        attrList.add(a);
      }
    }

    return new Entry(dn, objectClasses, userAttributes, operationalAttributes);
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
          debugInfo("Compression %d/%d%n",
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
   */
  static public byte[] entryToDatabase(Entry entry, DataConfig dataConfig)
  {
    byte[] uncompressedBytes = encodeDirectoryServerEntry(entry);
    return encodeDatabaseEntry(uncompressedBytes, dataConfig);
  }

  /**
   * Encodes an entry to the raw database format, without compression.
   *
   * @param entry The entry to encode.
   * @return A byte array containing the encoded database value.
   */
  static public byte[] entryToDatabase(Entry entry)
  {
    return entryToDatabase(entry, new DataConfig());
  }

  /**
   * Encode a ASN1 DirectoryServerEntry.
   *
   * @param entry The entry to encode.
   * @return A byte array containing the encoded DirectoryServerEntry.
   */
  static private byte[] encodeDirectoryServerEntry(Entry entry)
  {
    // Encode the DN (LDAPDN).
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(4);
    elements.add(new ASN1OctetString(entry.getDN().toString()));

    // Encode the object classes (SET OF LDAPString).
    Collection<String> objectClasses = entry.getObjectClasses().values();
    ArrayList<ASN1Element> objectClassElements;
    objectClassElements = new ArrayList<ASN1Element>(objectClasses.size());
    for (String s : objectClasses)
    {
      objectClassElements.add(new ASN1OctetString(s));
    }
    elements.add(new ASN1Set(objectClassElements));

    // Encode the user attributes (AttributeList).
    ArrayList<ASN1Element> userAttrElements = new ArrayList<ASN1Element>();
    for (List<Attribute> list : entry.getUserAttributes().values())
    {
      for (Attribute a : list)
      {
        if (a.isVirtual())
        {
          continue;
        }
        userAttrElements.add(new LDAPAttribute(a).encode());
      }
    }
    elements.add(new ASN1Sequence(userAttrElements));

    // Encode the operational attributes (AttributeList).
    ArrayList<ASN1Element> opAttrElements = new ArrayList<ASN1Element>();
    for (List<Attribute> list : entry.getOperationalAttributes().values())
    {
      for (Attribute a : list)
      {
        if (a.isVirtual())
        {
          continue;
        }
        opAttrElements.add(new LDAPAttribute(a).encode());
      }
    }
    elements.add(new ASN1Sequence(opAttrElements));

    // Encode the sequence.
    return new ASN1Sequence(TAG_DIRECTORY_SERVER_ENTRY, elements).encode();
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
   * Decode an array of entry ID values from its database representation.
   *
   * @param bytes The raw database value, null if there is no value and
   *              hence no entry IDs.  Zero length means the index entry
   *              limit has been exceeded.
   *
   * @return An array of entry ID values.
   */
  public static long[] entryIDListFromDatabase(byte[] bytes)
  {
    if (bytes == null)
    {
      return new long[0];
    }

    if (bytes.length == 0)
    {
      return null;
    }

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
   * Encode an array of entry ID values to its database representation.
   *
   * @param entryIDArray An array of entry ID values. A null value indicates
   * that the entry limit is exceeded, and a zero length array indicates no
   * values.
   * @return The encoded database value.
   */
  public static byte[] entryIDListToDatabase(long[] entryIDArray)
  {
    if (entryIDArray == null)
    {
      // index entry limit exceeded
      return new byte[0];
    }

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
