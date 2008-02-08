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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import org.opends.messages.Message;
import org.opends.server.api.CompressedSchema;
import org.opends.server.core.DirectoryServer;

import static org.opends.messages.CoreMessages.*;



/**
 * This class defines a data structure that contains configuration
 * information about how an entry should be encoded.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class EntryEncodeConfig
{
  /**
   * The encode mask value that can be used to indicate that the
   * encoded entry should not contain a DN.
   */
  private static final byte ENCODE_FLAG_EXCLUDE_DN = 0x01;



  /**
   * The encode mask value that can be used that the encoded
   * representation should compress the set of object classes.
   */
  private static final byte ENCODE_FLAG_COMPRESS_OCS = 0x02;



  /**
   * The encode mask value that can be used that the encoded
   * representation should compress the set of attribute descriptions
   * to conserve space and improve performance.
   */
  private static final byte ENCODE_FLAG_COMPRESS_ADS = 0x04;



  /**
   * A reference to an entry encode configuration with all the default
   * settings.
   */
  public static final EntryEncodeConfig
       DEFAULT_CONFIG = new EntryEncodeConfig();



  // Indicates whether to compress the attribute descriptions.
  private final boolean compressAttrDescriptions;

  // Indicates whether to compress the object class sets.
  private final boolean compressObjectClassSets;

  // Indicates whether to exclude the DN.
  private final boolean excludeDN;

  // The encoded representation of this encode configuration.
  private final byte[] encodedRepresentation;

  // The compressed schema handler for this encode configuration.
  private final CompressedSchema compressedSchema;



  /**
   * Creates a new encoded entry configuration wtih the default
   * settings.
   */
  public EntryEncodeConfig()
  {
    excludeDN                = false;
    compressAttrDescriptions = false;
    compressObjectClassSets  = false;

    compressedSchema = DirectoryServer.getDefaultCompressedSchema();

    encodedRepresentation = new byte[] { 0x00 };
  }



  /**
   * Creates a new encoded entry configuration wtih the specified
   * settings.
   *
   * @param  excludeDN                 Indicates whether to exclude
   *                                   the DN from the encoded entry.
   * @param  compressAttrDescriptions  Indicates whether to compress
   *                                   attribute descriptions.
   * @param  compressObjectClassSets   Indicates whether to compress
   *                                   object class sets.
   */
  public EntryEncodeConfig(boolean excludeDN,
                           boolean compressAttrDescriptions,
                           boolean compressObjectClassSets)
  {
    this.excludeDN                = excludeDN;
    this.compressAttrDescriptions = compressAttrDescriptions;
    this.compressObjectClassSets  = compressObjectClassSets;

    compressedSchema = DirectoryServer.getDefaultCompressedSchema();

    byte flagByte = 0x00;
    if (excludeDN)
    {
      flagByte |= ENCODE_FLAG_EXCLUDE_DN;
    }

    if (compressAttrDescriptions)
    {
      flagByte |= ENCODE_FLAG_COMPRESS_ADS;
    }

    if (compressObjectClassSets)
    {
      flagByte |= ENCODE_FLAG_COMPRESS_OCS;
    }

    encodedRepresentation = new byte[] { flagByte };
  }



  /**
   * Creates a new encoded entry configuration wtih the specified
   * settings.
   *
   * @param  excludeDN                 Indicates whether to exclude
   *                                   the DN from the encoded entry.
   * @param  compressAttrDescriptions  Indicates whether to compress
   *                                   attribute descriptions.
   * @param  compressObjectClassSets   Indicates whether to compress
   *                                   object class sets.
   * @param  compressedSchema          The compressed schema manager
   *                                   for this encode config.
   */
  public EntryEncodeConfig(boolean excludeDN,
                           boolean compressAttrDescriptions,
                           boolean compressObjectClassSets,
                           CompressedSchema compressedSchema)
  {
    this.excludeDN                = excludeDN;
    this.compressAttrDescriptions = compressAttrDescriptions;
    this.compressObjectClassSets  = compressObjectClassSets;
    this.compressedSchema         = compressedSchema;

    byte flagByte = 0x00;
    if (excludeDN)
    {
      flagByte |= ENCODE_FLAG_EXCLUDE_DN;
    }

    if (compressAttrDescriptions)
    {
      flagByte |= ENCODE_FLAG_COMPRESS_ADS;
    }

    if (compressObjectClassSets)
    {
      flagByte |= ENCODE_FLAG_COMPRESS_OCS;
    }

    encodedRepresentation = new byte[] { flagByte };
  }



  /**
   * Indicates whether the encoded entry should exclude the DN.
   *
   * @return  {@code true} if the encoded entry should exclude the DN,
   *          or {@code false} if not.
   */
  public boolean excludeDN()
  {
    return excludeDN;
  }



  /**
   * Indicates whether the encoded entry should use compressed
   * attribute descriptions.
   *
   * @return  {@code true} if the encoded entry should use compressed
   *          attribute descriptions, or {@code false} if not.
   */
  public boolean compressAttributeDescriptions()
  {
    return compressAttrDescriptions;
  }



  /**
   * Indicates whether the encoded entry should use compressed object
   * class sets.
   *
   * @return  {@code true} if the encoded entry should use compressed
   *          object class sets, or {@code false} if not.
   */
  public boolean compressObjectClassSets()
  {
    return compressObjectClassSets;
  }



  /**
   * Retrieves the compressed schema manager that may be used to
   * generate compact schema encodings with this entry encode
   * configuration.
   *
   * @return  The compressed schema manager that may be used to
   *          generate compact schema encodings with this entry encode
   *          configuration.
   */
  public CompressedSchema getCompressedSchema()
  {
    return compressedSchema;
  }



  /**
   * Encodes this entry encode configuration into a byte array
   * suitable for inclusion in the encoded entry.
   *
   * @return  A byte array containing the encoded configuration.
   */
  public byte[] encode()
  {
    return encodedRepresentation;
  }



  /**
   * Decodes the entry encode configuration from the specified portion
   * of the given byte array.
   *
   * @param  encodedEntry      The byte array containing the encoded
   *                           entry.
   * @param  startPos          The position at which to start decoding
   *                           the encode configuration.
   * @param  length            The number of bytes contained in the
   *                           encode configuration.
   * @param  compressedSchema  The compressed schema manager to use
   *                           when decoding.
   *
   * @return  The decoded configuration.
   *
   * @throws  DirectoryException  If the configuration cannot be
   *                              properly decoded.
   */
  public static EntryEncodeConfig
                     decode(byte[] encodedEntry, int startPos,
                     int length, CompressedSchema compressedSchema)
         throws DirectoryException
  {
    if (length != 1)
    {
      Message message = ERR_ENTRYENCODECFG_INVALID_LENGTH.get();
      throw new DirectoryException(
                     DirectoryServer.getServerErrorResultCode(),
                     message);
    }

    boolean excludeDN = false;
    if ((encodedEntry[startPos] & ENCODE_FLAG_EXCLUDE_DN) ==
        ENCODE_FLAG_EXCLUDE_DN)
    {
      excludeDN = true;
    }

    boolean compressAttrDescriptions = false;
    if ((encodedEntry[startPos] & ENCODE_FLAG_COMPRESS_ADS) ==
        ENCODE_FLAG_COMPRESS_ADS)
    {
      compressAttrDescriptions = true;
    }

    boolean compressObjectClassSets = false;
    if ((encodedEntry[startPos] & ENCODE_FLAG_COMPRESS_OCS) ==
        ENCODE_FLAG_COMPRESS_OCS)
    {
      compressObjectClassSets = true;
    }

    return new EntryEncodeConfig(excludeDN, compressAttrDescriptions,
                                 compressObjectClassSets,
                                 compressedSchema);
  }



  /**
   * Retrieves a string representation of this entry encode
   * configuration.
   *
   * @return  A string representation of this entry encode
   *          configuration.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this entry encode
   * configuration to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("EntryEncodeConfig(excludeDN=");
    buffer.append(excludeDN);
    buffer.append(", compressAttrDescriptions=");
    buffer.append(compressAttrDescriptions);
    buffer.append(", compressObjectClassSets=");
    buffer.append(compressObjectClassSets);
    buffer.append(")");
  }
}

