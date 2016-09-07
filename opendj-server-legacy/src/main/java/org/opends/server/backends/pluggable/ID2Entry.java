/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.forgerock.opendj.ldap.ResultCode.UNWILLING_TO_PERFORM;
import static org.forgerock.util.Reject.*;
import static org.forgerock.util.Utils.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.CursorTransformer.transformKeysAndValues;
import static org.opends.server.core.DirectoryServer.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.InflaterOutputStream;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.util.Function;
import org.forgerock.util.Reject;
import org.opends.server.api.CompressedSchema;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.CryptoManagerException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;

/**
 * Represents the tree containing the LDAP entries.
 * The key is the entry ID and the value is the entry contents.
 */
class ID2Entry extends AbstractTree
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Transforms cursor keys into EntryIDs. */
  private static final Function<ByteString, EntryID, Exception> TO_ENTRY_ID =
          new Function<ByteString, EntryID, Exception>() {
    @Override
    public EntryID apply(ByteString value) throws Exception {
      return new EntryID(value);
    }
  };

  /** Transforms cursor values into Entry objects. */
  private final CursorTransformer.ValueTransformer<ByteString, ByteString, Entry, Exception> TO_ENTRY =
          new CursorTransformer.ValueTransformer<ByteString, ByteString, Entry, Exception>() {
    @Override
    public Entry transform(ByteString key, ByteString value) throws Exception {
      return get0(value);
    }
  };

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

  /** A cached set of ByteStringBuilder buffers and ASN1Writer used to encode entries. */
  private static final class EntryCodec
  {
    /**
     * The format version used encode and decode entries in previous versions.
     * Not used anymore, kept for compatibility during upgrade.
     */
    static final byte FORMAT_VERSION = 0x01;

    /** The ASN1 tag for the ByteString type. */
    private static final byte TAG_TREE_ENTRY = 0x60;
    private static final int BUFFER_INIT_SIZE = 512;
    private static final byte PLAIN_ENTRY = 0x00;
    private static final byte COMPRESS_ENTRY = 0x01;
    private static final byte ENCRYPT_ENTRY = 0x02;

    /** The format version for entry encoding. */
    static final byte FORMAT_VERSION_V2 = 0x02;

    private final ByteStringBuilder encodedBuffer = new ByteStringBuilder();
    private final ByteStringBuilder entryBuffer = new ByteStringBuilder();
    private final ByteStringBuilder compressedEntryBuffer = new ByteStringBuilder();
    private final int maxBufferSize;

    private EntryCodec()
    {
      this.maxBufferSize = getMaxInternalBufferSize();
    }

    private void release()
    {
      encodedBuffer.clearAndTruncate(maxBufferSize, BUFFER_INIT_SIZE);
      entryBuffer.clearAndTruncate(maxBufferSize, BUFFER_INIT_SIZE);
      compressedEntryBuffer.clearAndTruncate(maxBufferSize, BUFFER_INIT_SIZE);
    }

    private Entry decode(ByteString bytes, CompressedSchema compressedSchema)
        throws DirectoryException, DecodeException, IOException
    {
      final byte formatVersion = bytes.byteAt(0);
      switch(formatVersion)
      {
      case FORMAT_VERSION:
        return decodeV1(bytes, compressedSchema);
      case FORMAT_VERSION_V2:
        return decodeV2(bytes, compressedSchema);
      default:
        throw DecodeException.error(ERR_INCOMPATIBLE_ENTRY_VERSION.get(formatVersion));
      }
    }

    /**
     * Decodes an entry from the old format.
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
     * @param bytes A byte array containing the encoded tree value.
     * @param compressedSchema The compressed schema manager to use when decoding.
     * @return The decoded entry.
     * @throws DecodeException If the data is not in the expected ASN.1 encoding
     * format.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws IOException if an error occurs while reading the ASN1 sequence.
     */
    private Entry decodeV1(ByteString bytes, CompressedSchema compressedSchema)
        throws DirectoryException, DecodeException, IOException
    {
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

    /**
     * Decodes an entry in the new extensible format.
     * Enties are encoded according to the sequence
     *   {VERSION_BYTE, FLAG_BYTE, COMPACT_INTEGER_LENGTH, ID2ENTRY_VALUE}
     * where
     *
     * ID2ENTRY_VALUE = encoding of Entry as in decodeV1()
     * VERSION_BYTE = 0x2
     * FLAG_BYTE = bit field of OR'ed values indicating post-encoding processing.
     *     possible meaningful flags are COMPRESS_ENTRY and ENCRYPT_ENTRY.
     * COMPACT_INTEGER_LENGTH = length of ID2ENTRY_VALUE
     *
     * @param bytes A byte array containing the encoded tree value.
     * @param compressedSchema The compressed schema manager to use when decoding.
     * @return The decoded entry.
     * @throws DecodeException If the data is not in the expected ASN.1 encoding
     * format or a decryption error occurs.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws IOException if an error occurs while reading the ASN1 sequence.
     */
    private Entry decodeV2(ByteString bytes, CompressedSchema compressedSchema)
        throws DirectoryException, DecodeException, IOException
    {
      ByteSequenceReader reader = bytes.asReader();
      // skip version byte
      reader.position(1);
      int format = reader.readByte();
      int encodedEntryLen = reader.readCompactUnsignedInt();
      try
      {
        if (format == PLAIN_ENTRY)
        {
          return Entry.decode(reader, compressedSchema);
        }
        InputStream is = reader.asInputStream();
        if ((format & ENCRYPT_ENTRY) == ENCRYPT_ENTRY)
        {
          is = getCryptoManager().getCipherInputStream(is);
        }
        if ((format & COMPRESS_ENTRY) == COMPRESS_ENTRY)
        {
          is = new InflaterInputStream(is);
        }
        byte[] data = new byte[encodedEntryLen];
        int readBytes;
        int position = 0;
        int leftToRead = encodedEntryLen;
        // CipherInputStream does not read more than block size...
        do
        {
          if ((readBytes = is.read(data, position, leftToRead)) == -1 )
          {
            throw DecodeException.error(ERR_CANNOT_DECODE_ENTRY.get());
          }
          position += readBytes;
          leftToRead -= readBytes;
        } while (leftToRead > 0 && readBytes > 0);
        return Entry.decode(ByteString.wrap(data).asReader(), compressedSchema);
      }
      catch (CryptoManagerException cme)
      {
        logger.traceException(cme);
        throw DecodeException.error(cme.getMessageObject());
      }
    }

    private ByteString encode(Entry entry, DataConfig dataConfig) throws DirectoryException
    {
      encodeVolatile(entry, dataConfig);
      return encodedBuffer.toByteString();
    }

    private void encodeVolatile(Entry entry, DataConfig dataConfig) throws DirectoryException
    {
      entry.encode(entryBuffer, dataConfig.getEntryEncodeConfig());

      OutputStream os = encodedBuffer.asOutputStream();
      try
      {
        byte[] formatFlags = { FORMAT_VERSION_V2, 0};
        os.write(formatFlags);
        encodedBuffer.appendCompactUnsigned(entryBuffer.length());
        if (dataConfig.isCompressed())
        {
          os = new DeflaterOutputStream(os);
          formatFlags[1] = COMPRESS_ENTRY;
        }
        if (dataConfig.isEncrypted())
        {
          os = dataConfig.getCryptoSuite().getCipherOutputStream(os);
          formatFlags[1] |= ENCRYPT_ENTRY;
        }
        encodedBuffer.setByte(1, formatFlags[1]);

        entryBuffer.copyTo(os);
        os.flush();
      }
      catch(CryptoManagerException | IOException e)
      {
        logger.traceException(e);
        throw new DirectoryException(UNWILLING_TO_PERFORM, ERR_CANNOT_ENCODE_ENTRY.get(e.getLocalizedMessage()));
      }
      finally
      {
        try
        {
          os.close();
        }
        catch (IOException ioe)
        {
          throw new DirectoryException(UNWILLING_TO_PERFORM, ERR_CANNOT_ENCODE_ENTRY.get(ioe.getLocalizedMessage()));
        }
      }
    }
  }

  /**
   * Create a new ID2Entry object.
   *
   * @param name The name of the entry tree.
   * @param dataConfig The desired compression and encryption options for data
   * stored in the entry tree.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  ID2Entry(TreeName name, DataConfig dataConfig) throws StorageRuntimeException
  {
    super(name);
    this.dataConfig = dataConfig;
  }

  @Override
  void afterOpen(WriteableTransaction txn, boolean createOnDemand) throws StorageRuntimeException
  {
    // Make sure the tree is there and readable, even if the storage is READ_ONLY.
    // Would be nice if there were a better way...
    try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(getName()))
    {
      cursor.next();
    }
  }

  /**
   * Decodes an entry from its tree representation.
   *
   * @param bytes A byte array containing the encoded tree value.
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
  Entry entryFromDatabase(ByteString bytes,
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
   * Encodes an entry to the raw tree format, with optional compression.
   *
   * @param entry The entry to encode.
   * @param dataConfig Compression and cryptographic options.
   * @return A ByteSTring containing the encoded tree value.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  ByteString entryToDatabase(Entry entry, DataConfig dataConfig) throws DirectoryException
  {
    EntryCodec codec = acquireEntryCodec();
    try
    {
      return codec.encode(entry, dataConfig);
    }
    finally
    {
      codec.release();
    }
  }

  ByteString encode(Entry entry) throws DirectoryException {
    return entryToDatabase(entry, dataConfig);
  }

  /**
   * Write a record in the entry tree.
   *
   * @param txn a non null transaction
   * @param entryID The entry ID which forms the key.
   * @param entry The LDAP entry.
   * @throws StorageRuntimeException If an error occurs in the storage.
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  public void put(WriteableTransaction txn, EntryID entryID, Entry entry)
       throws StorageRuntimeException, DirectoryException
  {
    put(txn, entryID, encode(entry));
  }

  public void put(WriteableTransaction txn, EntryID entryID, ByteSequence encodedEntry)
      throws StorageRuntimeException, DirectoryException
  {
    Reject.ifNull(txn, "txn must not be null.");
    txn.put(getName(), entryID.toByteString(), encodedEntry);
  }

  /**
   * Fetch a record from the entry tree.
   *
   * @param txn a non null transaction
   * @param entryID The desired entry ID which forms the key.
   * @return The requested entry, or null if there is no such record.
   * @throws DirectoryException If a problem occurs while getting the entry.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  public Entry get(ReadableTransaction txn, EntryID entryID)
       throws DirectoryException, StorageRuntimeException
  {
    try
    {
      return get0(txn.read(getName(), entryID.toByteString()));
    }
    catch (Exception e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), ERR_ENTRY_DATABASE_CORRUPT.get(entryID));
    }
  }

  Cursor<EntryID, Entry> openCursor(ReadableTransaction txn)
  {
    return transformKeysAndValues(txn.openCursor(getName()), TO_ENTRY_ID, TO_ENTRY);
  }

  /**
   * Check that a record entry exists in the entry tree.
   *
   * @param txn a non null transaction
   * @param entryID The entry ID which forms the key.
   * @return True if an entry with entryID exists
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  public boolean containsEntryID(ReadableTransaction txn, EntryID entryID)
  {
    checkNotNull(txn, "txn must not be null");
    checkNotNull(entryID, "entryID must not be null");
    try(final Cursor<ByteString, ByteString> cursor = txn.openCursor(getName())) {
      return cursor.positionToKey(entryID.toByteString());
    }
  }

  private Entry get0(ByteString value) throws Exception
  {
    if (value == null)
    {
      return null;
    }
    final Entry entry = entryFromDatabase(value, dataConfig.getEntryEncodeConfig().getCompressedSchema());
    entry.processVirtualAttributes();
    return entry;
  }

  /**
   * Set the desired compression and encryption options for data
   * stored in the entry tree.
   *
   * @param dataConfig The desired compression and encryption options for data
   * stored in the entry tree.
   */
  void setDataConfig(DataConfig dataConfig)
  {
    this.dataConfig = dataConfig;
  }

  @Override
  public String keyToString(ByteString key)
  {
    return new EntryID(key).toString();
  }

  @Override
  public String valueToString(ByteString value)
  {
    try
    {
      return "\n" + get0(value).toString();
    }
    catch (Exception e)
    {
      return e.getMessage();
    }
  }

  @Override
  public ByteString generateKey(String data)
  {
    EntryID entryID = new EntryID(Long.parseLong(data));
    return entryID.toByteString();
  }
}
