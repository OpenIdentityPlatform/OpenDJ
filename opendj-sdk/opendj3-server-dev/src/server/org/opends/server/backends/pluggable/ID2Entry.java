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
 *      Portions Copyright 2012-2014 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.api.CompressedSchema;
import org.opends.server.backends.pluggable.BackendImpl.ReadableStorage;
import org.opends.server.backends.pluggable.BackendImpl.Storage;
import org.opends.server.backends.pluggable.BackendImpl.StorageRuntimeException;
import org.opends.server.backends.pluggable.BackendImpl.TreeName;
import org.opends.server.backends.pluggable.BackendImpl.WriteableStorage;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.JebMessages.*;
import static org.opends.server.core.DirectoryServer.*;

/**
 * Represents the database containing the LDAP entries. The database key is
 * the entry ID and the value is the entry contents.
 */
public class ID2Entry extends DatabaseContainer
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Parameters for compression and encryption. */
  private DataConfig dataConfig;

  /** Cached encoding buffers. */
  private static final ThreadLocal<EntryCodec> ENTRY_CODEC_CACHE = new ThreadLocal<EntryCodec>()
  {
    @Override
    protected EntryCodec initialValue()
    {
      return new EntryCodec();
    }
  };

  private static EntryCodec acquireEntryCodec()
  {
    EntryCodec codec = ENTRY_CODEC_CACHE.get();
    if (codec.maxBufferSize != getMaxInternalBufferSize())
    {
      // Setting has changed, so recreate the codec.
      codec = new EntryCodec();
      ENTRY_CODEC_CACHE.set(codec);
    }
    return codec;
  }

  /**
   * A cached set of ByteStringBuilder buffers and ASN1Writer used to encode
   * entries.
   */
  private static class EntryCodec
  {
    private static final int BUFFER_INIT_SIZE = 512;

    private final ByteStringBuilder encodedBuffer = new ByteStringBuilder();
    private final ByteStringBuilder entryBuffer = new ByteStringBuilder();
    private final ByteStringBuilder compressedEntryBuffer = new ByteStringBuilder();
    private final ASN1Writer writer;
    private final int maxBufferSize;

    private EntryCodec()
    {
      this.maxBufferSize = getMaxInternalBufferSize();
      this.writer = ASN1.getWriter(encodedBuffer, maxBufferSize);
    }

    private void release()
    {
      closeSilently(writer);
      encodedBuffer.clearAndTruncate(maxBufferSize, BUFFER_INIT_SIZE);
      entryBuffer.clearAndTruncate(maxBufferSize, BUFFER_INIT_SIZE);
      compressedEntryBuffer.clearAndTruncate(maxBufferSize, BUFFER_INIT_SIZE);
    }

    private Entry decode(ByteString bytes, CompressedSchema compressedSchema)
        throws DirectoryException, DecodeException, LDAPException,
        DataFormatException, IOException
    {
      // Get the format version.
      byte formatVersion = bytes.byteAt(0);
      if(formatVersion != JebFormat.FORMAT_VERSION)
      {
        throw DecodeException.error(ERR_JEB_INCOMPATIBLE_ENTRY_VERSION.get(formatVersion));
      }

      // Read the ASN1 sequence.
      ASN1Reader reader = ASN1.getReader(bytes.subSequence(1, bytes.length()));
      reader.readStartSequence();

      // See if it was compressed.
      int uncompressedSize = (int)reader.readInteger();
      if(uncompressedSize > 0)
      {
        // It was compressed.
        reader.readOctetString(compressedEntryBuffer);

        OutputStream decompressor = null;
        try
        {
          // TODO: Should handle the case where uncompress fails
          decompressor = new InflaterOutputStream(entryBuffer.asOutputStream());
          compressedEntryBuffer.copyTo(decompressor);
        }
        finally {
          closeSilently(decompressor);
        }

        // Since we are used the cached buffers (ByteStringBuilders),
        // the decoded attribute values will not refer back to the
        // original buffer.
        return Entry.decode(entryBuffer.asReader(), compressedSchema);
      }
      else
      {
        // Since we don't have to do any decompression, we can just decode
        // the entry directly.
        ByteString encodedEntry = reader.readOctetString();
        return Entry.decode(encodedEntry.asReader(), compressedSchema);
      }
    }

    private ByteString encodeCopy(Entry entry, DataConfig dataConfig)
        throws DirectoryException
    {
      encodeVolatile(entry, dataConfig);
      return encodedBuffer.toByteString();
    }

    private ByteString encodeInternal(Entry entry, DataConfig dataConfig)
        throws DirectoryException
    {
      encodeVolatile(entry, dataConfig);
      return encodedBuffer.toByteString();
    }

    private void encodeVolatile(Entry entry, DataConfig dataConfig) throws DirectoryException
    {
      // Encode the entry for later use.
      entry.encode(entryBuffer, dataConfig.getEntryEncodeConfig());

      // First write the DB format version byte.
      encodedBuffer.append(JebFormat.FORMAT_VERSION);

      try
      {
        // Then start the ASN1 sequence.
        writer.writeStartSequence(JebFormat.TAG_DATABASE_ENTRY);

        if (dataConfig.isCompressed())
        {
          OutputStream compressor = null;
          try {
            compressor = new DeflaterOutputStream(compressedEntryBuffer.asOutputStream());
            entryBuffer.copyTo(compressor);
          }
          finally {
            closeSilently(compressor);
          }

          // Compression needed and successful.
          writer.writeInteger(entryBuffer.length());
          writer.writeOctetString(compressedEntryBuffer);
        }
        else
        {
          writer.writeInteger(0);
          writer.writeOctetString(entryBuffer);
        }

        writer.writeEndSequence();
      }
      catch(IOException ioe)
      {
        // TODO: This should never happen with byte buffer.
        logger.traceException(ioe);
      }
    }
  }

  /**
   * Create a new ID2Entry object.
   *
   * @param name The name of the entry database.
   * @param dataConfig The desired compression and encryption options for data
   * stored in the entry database.
   * @param storage The JE Storage.
   * @param entryContainer The entryContainer of the entry database.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   *
   */
  ID2Entry(TreeName name, DataConfig dataConfig, Storage storage, EntryContainer entryContainer)
      throws StorageRuntimeException
  {
    super(name, storage, entryContainer);
    this.dataConfig = dataConfig;
  }

  /**
   * Decodes an entry from its database representation.
   * <p>
   * An entry on disk is ASN1 encoded in this format:
   *
   * <pre>
   * ByteString ::= [APPLICATION 0] IMPLICIT SEQUENCE {
   *  uncompressedSize      INTEGER,      -- A zero value means not compressed.
   *  dataBytes             OCTET STRING  -- Optionally compressed encoding of
   *                                         the data bytes.
   * }
   *
   * ID2EntryValue ::= ByteString
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
   * @throws DecodeException If the data is not in the expected ASN.1 encoding
   * format.
   * @throws LDAPException If the data is not in the expected ASN.1 encoding
   * format.
   * @throws DataFormatException If an error occurs while trying to decompress
   * compressed data.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws IOException if an error occurs while reading the ASN1 sequence.
   */
  public static Entry entryFromDatabase(ByteString bytes,
      CompressedSchema compressedSchema) throws DirectoryException,
      DecodeException, LDAPException, DataFormatException, IOException
  {
    EntryCodec codec = acquireEntryCodec();
    try
    {
      return codec.decode(bytes, compressedSchema);
    }
    finally
    {
      codec.release();
    }
  }

  /**
   * Encodes an entry to the raw database format, with optional compression.
   *
   * @param entry The entry to encode.
   * @param dataConfig Compression and cryptographic options.
   * @return A ByteSTring containing the encoded database value.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  public static ByteString entryToDatabase(Entry entry, DataConfig dataConfig)
      throws DirectoryException
  {
    EntryCodec codec = acquireEntryCodec();
    try
    {
      return codec.encodeCopy(entry, dataConfig);
    }
    finally
    {
      codec.release();
    }
  }



  /**
   * Insert a record into the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @param entry The LDAP entry.
   * @return true if the entry was inserted, false if a record with that
   *         ID already existed.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  public boolean insert(WriteableStorage txn, EntryID id, Entry entry)
       throws StorageRuntimeException, DirectoryException
  {
    ByteString key = id.toByteString();
    EntryCodec codec = acquireEntryCodec();
    try
    {
      ByteString value = codec.encodeInternal(entry, dataConfig);
      return insert(txn, key, value);
    }
    finally
    {
      codec.release();
    }
  }

  /**
   * Write a record in the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @param entry The LDAP entry.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  public void put(WriteableStorage txn, EntryID id, Entry entry)
       throws StorageRuntimeException, DirectoryException
  {
    ByteString key = id.toByteString();
    EntryCodec codec = acquireEntryCodec();
    try
    {
      ByteString value = codec.encodeInternal(entry, dataConfig);
      put(txn, key, value);
    }
    finally
    {
      codec.release();
    }
  }

  /**
   * Write a pre-formatted record into the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param key The key containing a pre-formatted entry ID.
   * @param value The data value containing a pre-formatted LDAP entry.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  @Override
  public void put(WriteableStorage txn, ByteSequence key, ByteSequence value) throws StorageRuntimeException
  {
    super.put(txn, key, value);
  }

  /**
   * Remove a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @return true if the entry was removed, false if it was not.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public boolean remove(WriteableStorage txn, EntryID id) throws StorageRuntimeException
  {
    return delete(txn, id.toByteString());
  }

  /**
   * Fetch a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The desired entry ID which forms the key.
   * @return The requested entry, or null if there is no such record.
   * @throws DirectoryException If a problem occurs while getting the entry.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public Entry get(ReadableStorage txn, EntryID id, boolean isRMW)
       throws DirectoryException, StorageRuntimeException
  {
    ByteString value = read(txn, id.toByteString(), isRMW);
    if (value == null)
    {
      return null;
    }

    try
    {
      Entry entry = entryFromDatabase(value,
          entryContainer.getRootContainer().getCompressedSchema());
      entry.processVirtualAttributes();
      return entry;
    }
    catch (Exception e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_JEB_ENTRY_DATABASE_CORRUPT.get(id));
    }
  }

  /**
   * Set the desired compression and encryption options for data
   * stored in the entry database.
   *
   * @param dataConfig The desired compression and encryption options for data
   * stored in the entry database.
   */
  public void setDataConfig(DataConfig dataConfig)
  {
    this.dataConfig = dataConfig;
  }
}
