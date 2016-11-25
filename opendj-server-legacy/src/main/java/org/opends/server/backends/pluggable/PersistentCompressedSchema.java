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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.api.CompressedSchema;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class provides a compressed schema implementation whose definitions are
 * persisted in a tree.
 */
final class PersistentCompressedSchema extends CompressedSchema
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The name of the tree used to store compressed attribute description definitions. */
  private static final String DB_NAME_AD = "compressed_attributes";
  /** The name of the tree used to store compressed object class set definitions. */
  private static final String DB_NAME_OC = "compressed_object_classes";

  /** The compressed attribute description schema tree. */
  private static final TreeName adTreeName = new TreeName("compressed_schema", DB_NAME_AD);
  /** The compressed object class set schema tree. */
  private static final TreeName ocTreeName = new TreeName("compressed_schema", DB_NAME_OC);

  /** The storage in which the trees are held. */
  private final Storage storage;

  private final ByteStringBuilder storeAttributeWriterBuffer = new ByteStringBuilder();
  private final ASN1Writer storeAttributeWriter = ASN1.getWriter(storeAttributeWriterBuffer);
  private final ByteStringBuilder storeObjectClassesWriterBuffer = new ByteStringBuilder();
  private final ASN1Writer storeObjectClassesWriter = ASN1.getWriter(storeObjectClassesWriterBuffer);

  /**
   * Creates a new instance of this compressed schema manager.
   *
   * @param serverContext
   *          The server context.
   * @param storage
   *          A reference to the storage in which the trees will be held.
   * @param txn a non null transaction
   * @param accessMode specifies how the storage has been opened (read only or read/write)
   *
   * @throws StorageRuntimeException
   *           If a problem occurs while loading the compressed schema
   *           definitions from the tree.
   * @throws InitializationException
   *           If an error occurs while loading and processing the compressed
   *           schema definitions.
   */
  PersistentCompressedSchema(ServerContext serverContext, final Storage storage, WriteableTransaction txn,
      AccessMode accessMode) throws StorageRuntimeException, InitializationException
  {
    super(serverContext);
    this.storage = storage;
    load(txn, accessMode.isWriteable());
  }

  @Override
  protected void storeAttribute(final byte[] encodedAttribute,
      final String attributeName, final Iterable<String> attributeOptions)
      throws DirectoryException
  {
    try
    {
      storeAttributeWriterBuffer.clear();
      storeAttributeWriter.writeStartSequence();
      storeAttributeWriter.writeOctetString(attributeName);
      for (final String option : attributeOptions)
      {
        storeAttributeWriter.writeOctetString(option);
      }
      storeAttributeWriter.writeEndSequence();
      store(adTreeName, encodedAttribute, storeAttributeWriterBuffer);
    }
    catch (final IOException e)
    {
      // TODO: Shouldn't happen but should log a message
    }
  }

  @Override
  protected void storeObjectClasses(final byte[] encodedObjectClasses,
      final Collection<String> objectClassNames) throws DirectoryException
  {
    try
    {
      storeObjectClassesWriterBuffer.clear();
      storeObjectClassesWriter.writeStartSequence();
      for (final String ocName : objectClassNames)
      {
        storeObjectClassesWriter.writeOctetString(ocName);
      }
      storeObjectClassesWriter.writeEndSequence();
      store(ocTreeName, encodedObjectClasses, storeObjectClassesWriterBuffer);
    }
    catch (final IOException e)
    {
      // TODO: Shouldn't happen but should log a message
    }
  }

  private void load(WriteableTransaction txn, boolean shouldCreate)
      throws StorageRuntimeException, InitializationException
  {
    txn.openTree(adTreeName, shouldCreate);
    txn.openTree(ocTreeName, shouldCreate);

    // Cursor through the object class database and load the object class set
    // definitions. At the same time, figure out the highest token value and
    // initialize the object class counter to one greater than that.
    try (Cursor<ByteString, ByteString> ocCursor = txn.openCursor(ocTreeName))
    {
      while (ocCursor.next())
      {
        final byte[] encodedObjectClasses = ocCursor.getKey().toByteArray();
        final ASN1Reader reader = ASN1.getReader(ocCursor.getValue());
        reader.readStartSequence();
        final List<String> objectClassNames = new LinkedList<>();
        while (reader.hasNextElement())
        {
          objectClassNames.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
        loadObjectClasses(encodedObjectClasses, objectClassNames);
      }
    }
    catch (final IOException e)
    {
      logger.traceException(e);
      throw new InitializationException(ERR_COMPSCHEMA_CANNOT_DECODE_OC_TOKEN.get(e.getMessage()), e);
    }

    // Cursor through the attribute description database and load the attribute set definitions.
    try (Cursor<ByteString, ByteString> adCursor = txn.openCursor(adTreeName))
    {
      while (adCursor.next())
      {
        final byte[] encodedAttribute = adCursor.getKey().toByteArray();
        final ASN1Reader reader = ASN1.getReader(adCursor.getValue());
        reader.readStartSequence();
        final String attributeName = reader.readOctetStringAsString();
        final List<String> attributeOptions = new LinkedList<>();
        while (reader.hasNextElement())
        {
          attributeOptions.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
        loadAttribute(encodedAttribute, attributeName, attributeOptions);
      }
    }
    catch (final IOException e)
    {
      logger.traceException(e);
      throw new InitializationException(ERR_COMPSCHEMA_CANNOT_DECODE_AD_TOKEN.get(e.getMessage()), e);
    }
  }

  private boolean store(final TreeName treeName, final byte[] key, final ByteStringBuilder value)
      throws DirectoryException
  {
    final ByteString keyEntry = ByteString.wrap(key);
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          txn.put(treeName, keyEntry, value);
        }
      });
      return true;
    }
    catch (final Exception e)
    {
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
          ERR_COMPSCHEMA_CANNOT_STORE_EX.get(e.getMessage()), e);
    }
  }
}
