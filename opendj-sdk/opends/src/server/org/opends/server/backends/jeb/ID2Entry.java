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
import org.opends.messages.Message;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.JebMessages.*;

import com.sleepycat.je.*;

import org.opends.server.types.*;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.api.CompressedSchema;

import java.io.IOException;
import java.util.zip.DataFormatException;

/**
 * Represents the database containing the LDAP entries. The database key is
 * the entry ID and the value is the entry contents.
 *
 */
public class ID2Entry extends DatabaseContainer
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Parameters for compression and encryption.
   */
  private DataConfig dataConfig;

  private static ThreadLocal<EntryCoder> entryCodingBuffers =
      new ThreadLocal<EntryCoder>();

  /**
   * A cached set of ByteStringBuilder buffers and ASN1Writer used to encode
   * entries.
   */
  private static class EntryCoder
  {
    ByteStringBuilder encodedBuffer;
    private ByteStringBuilder entryBuffer;
    private ByteStringBuilder compressedEntryBuffer;
    private ASN1Writer writer;

    private EntryCoder()
    {
      encodedBuffer = new ByteStringBuilder();
      entryBuffer = new ByteStringBuilder();
      compressedEntryBuffer = new ByteStringBuilder();
      writer = ASN1.getWriter(encodedBuffer);
    }

    private Entry decode(ByteString bytes, CompressedSchema compressedSchema)
        throws DirectoryException,ASN1Exception,LDAPException,
        DataFormatException, IOException
    {
      // Get the format version.
      byte formatVersion = bytes.byteAt(0);
      if(formatVersion != JebFormat.FORMAT_VERSION)
      {
        Message message =
            ERR_JEB_INCOMPATIBLE_ENTRY_VERSION.get(formatVersion);
        throw new ASN1Exception(message);
      }

      // Read the ASN1 sequence.
      ASN1Reader reader = ASN1.getReader(bytes.subSequence(1, bytes.length()));
      reader.readStartSequence();

      // See if it was compressed.
      int uncompressedSize = (int)reader.readInteger();

      if(uncompressedSize > 0)
      {
        // We will use the cached buffers to avoid allocations.
        // Reset the buffers;
        entryBuffer.clear();
        compressedEntryBuffer.clear();

        // It was compressed.
        reader.readOctetString(compressedEntryBuffer);
        CryptoManager cryptoManager = DirectoryServer.getCryptoManager();
        // TODO: Should handle the case where uncompress returns < 0
        compressedEntryBuffer.uncompress(entryBuffer, cryptoManager,
            uncompressedSize);

        // Since we are used the cached buffers (ByteStringBuilders),
        // the decoded attribute values will not refer back to the
        // original buffer.
        return Entry.decode(entryBuffer.asReader(), compressedSchema);
      }
      else
      {
        // Since we don't have to do any decompression, we can just decode
        // the entry off the
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

    private DatabaseEntry encodeInternal(Entry entry, DataConfig dataConfig)
        throws DirectoryException
    {
      encodeVolatile(entry, dataConfig);
      return new DatabaseEntry(encodedBuffer.getBackingArray(), 0,
          encodedBuffer.length());
    }

    private void encodeVolatile(Entry entry, DataConfig dataConfig)
        throws DirectoryException
    {
      // Reset the buffers;
      encodedBuffer.clear();
      entryBuffer.clear();
      compressedEntryBuffer.clear();

      // Encode the entry for later use.
      entry.encode(entryBuffer, dataConfig.getEntryEncodeConfig());

      // First write the DB format version byte.
      encodedBuffer.append(JebFormat.FORMAT_VERSION);

      try
      {
        // Then start the ASN1 sequence.
        writer.writeStartSequence(JebFormat.TAG_DATABASE_ENTRY);

        // Do optional compression.
        CryptoManager cryptoManager = DirectoryServer.getCryptoManager();
        if (dataConfig.isCompressed() && cryptoManager != null &&
            entryBuffer.compress(compressedEntryBuffer, cryptoManager))
        {
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
        if(debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ioe);
        }

      }
    }
  }

  /**
   * Create a new ID2Entry object.
   *
   * @param name The name of the entry database.
   * @param dataConfig The desired compression and encryption options for data
   * stored in the entry database.
   * @param env The JE Environment.
   * @param entryContainer The entryContainer of the entry database.
   * @throws DatabaseException If an error occurs in the JE database.
   *
   */
  ID2Entry(String name, DataConfig dataConfig,
           Environment env, EntryContainer entryContainer)
      throws DatabaseException
  {
    super(name, env, entryContainer);
    this.dataConfig = dataConfig;

    DatabaseConfig dbNodupsConfig = new DatabaseConfig();

    if(env.getConfig().getReadOnly())
    {
      dbNodupsConfig.setReadOnly(true);
      dbNodupsConfig.setAllowCreate(false);
      dbNodupsConfig.setTransactional(false);
    }
    else if(!env.getConfig().getTransactional())
    {
      dbNodupsConfig.setAllowCreate(true);
      dbNodupsConfig.setTransactional(false);
      dbNodupsConfig.setDeferredWrite(true);
    }
    else
    {
      dbNodupsConfig.setAllowCreate(true);
      dbNodupsConfig.setTransactional(true);
    }

    this.dbConfig = dbNodupsConfig;
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
   * @throws IOException if an error occurs while reading the ASN1 sequence.
   */
  static public Entry entryFromDatabase(ByteString bytes,
                                        CompressedSchema compressedSchema)
      throws DirectoryException,ASN1Exception,LDAPException,
      DataFormatException,IOException
  {
    EntryCoder coder = entryCodingBuffers.get();
    if(coder == null)
    {
      coder = new EntryCoder();
      entryCodingBuffers.set(coder);
    }
    return coder.decode(bytes, compressedSchema);
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
  static public ByteString entryToDatabase(Entry entry, DataConfig dataConfig)
      throws DirectoryException
  {
    EntryCoder coder = entryCodingBuffers.get();
    if(coder == null)
    {
      coder = new EntryCoder();
      entryCodingBuffers.set(coder);
    }

    return coder.encodeCopy(entry, dataConfig);
  }

  /**
   * Insert a record into the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @param entry The LDAP entry.
   * @return true if the entry was inserted, false if a record with that
   *         ID already existed.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  public boolean insert(Transaction txn, EntryID id, Entry entry)
       throws DatabaseException, DirectoryException
  {
    DatabaseEntry key = id.getDatabaseEntry();
    EntryCoder coder = entryCodingBuffers.get();
    if(coder == null)
    {
      coder = new EntryCoder();
      entryCodingBuffers.set(coder);
    }
    DatabaseEntry data = coder.encodeInternal(entry, dataConfig);

    OperationStatus status;
    status = insert(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Write a record in the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @param entry The LDAP entry.
   * @return true if the entry was written, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  public boolean put(Transaction txn, EntryID id, Entry entry)
       throws DatabaseException, DirectoryException
  {
    DatabaseEntry key = id.getDatabaseEntry();
    EntryCoder coder = entryCodingBuffers.get();
    if(coder == null)
    {
      coder = new EntryCoder();
      entryCodingBuffers.set(coder);
    }
    DatabaseEntry data = coder.encodeInternal(entry, dataConfig);

    OperationStatus status;
    status = put(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Write a pre-formatted record into the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param key The key containing a pre-formatted entry ID.
   * @param data The data value containing a pre-formatted LDAP entry.
   * @return true if the entry was written, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean putRaw(Transaction txn, DatabaseEntry key, DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status;
    status = put(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Remove a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @return true if the entry was removed, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean remove(Transaction txn, EntryID id)
       throws DatabaseException
  {
    DatabaseEntry key = id.getDatabaseEntry();

    OperationStatus status = delete(txn, key);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Fetch a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The desired entry ID which forms the key.
   * @param lockMode The JE locking mode to be used for the read.
   * @return The requested entry, or null if there is no such record.
   * @throws DirectoryException If a problem occurs while getting the entry.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public Entry get(Transaction txn, EntryID id, LockMode lockMode)
       throws DirectoryException, DatabaseException
  {
    DatabaseEntry key = id.getDatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();

    OperationStatus status;
    status = read(txn, key, data, lockMode);

    if (status != OperationStatus.SUCCESS)
    {
      return null;
    }

    try
    {
      Entry entry = entryFromDatabase(ByteString.wrap(data.getData()),
          entryContainer.getRootContainer().getCompressedSchema());
      entry.processVirtualAttributes();
      return entry;
    }
    catch (Exception e)
    {
      Message message = ERR_JEB_ENTRY_DATABASE_CORRUPT.get(id.toString());
      throw new DirectoryException(
          DirectoryServer.getServerErrorResultCode(), message);
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
