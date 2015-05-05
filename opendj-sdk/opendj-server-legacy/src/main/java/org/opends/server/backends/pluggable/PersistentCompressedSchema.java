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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

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
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import static org.opends.messages.BackendMessages.*;

/**
 * This class provides a compressed schema implementation whose definitions are
 * persisted in a database.
 */
final class PersistentCompressedSchema extends CompressedSchema
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The name of the database used to store compressed attribute description definitions. */
  private static final String DB_NAME_AD = "compressed_attributes";
  /** The name of the database used to store compressed object class set definitions. */
  private static final String DB_NAME_OC = "compressed_object_classes";

  /** The compressed attribute description schema tree. */
  private static final TreeName adTreeName = new TreeName("compressed_schema", DB_NAME_AD);
  /** The compressed object class set schema tree. */
  private static final TreeName ocTreeName = new TreeName("compressed_schema", DB_NAME_OC);

  /** The storage in which the databases are held. */
  private Storage storage;

  private final ByteStringBuilder storeAttributeWriterBuffer = new ByteStringBuilder();
  private final ASN1Writer storeAttributeWriter = ASN1.getWriter(storeAttributeWriterBuffer);
  private final ByteStringBuilder storeObjectClassesWriterBuffer = new ByteStringBuilder();
  private final ASN1Writer storeObjectClassesWriter = ASN1.getWriter(storeObjectClassesWriterBuffer);



  /**
   * Creates a new instance of this JE compressed schema manager.
   *
   * @param storage
   *          A reference to the database environment in which the databases
   *          will be held.
   * @param txn a non null database transaction
   * @throws StorageRuntimeException
   *           If a database problem occurs while loading the compressed schema
   *           definitions from the database.
   * @throws InitializationException
   *           If an error occurs while loading and processing the compressed
   *           schema definitions.
   */
  PersistentCompressedSchema(final Storage storage, WriteableTransaction txn)
      throws StorageRuntimeException, InitializationException
  {
    this.storage = storage;
    load(txn);
  }

  /** {@inheritDoc} */
  @Override
  protected void storeAttribute(final byte[] encodedAttribute,
      final String attributeName, final Collection<String> attributeOptions)
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



  /** {@inheritDoc} */
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



  /**
   * Loads the compressed schema information from the database.
   *
   * @throws StorageRuntimeException
   *           If a database error occurs while loading the definitions from the
   *           database.
   * @throws InitializationException
   *           If an error occurs while loading and processing the definitions.
   */
  private void load(WriteableTransaction txn) throws StorageRuntimeException, InitializationException
  {
    txn.openTree(adTreeName);
    txn.openTree(ocTreeName);

    // Cursor through the object class database and load the object class set
    // definitions. At the same time, figure out the highest token value and
    // initialize the object class counter to one greater than that.
    final Cursor<ByteString, ByteString> ocCursor = txn.openCursor(ocTreeName);
    try
    {
      while (ocCursor.next())
      {
        final byte[] encodedObjectClasses = ocCursor.getKey().toByteArray();
        final ASN1Reader reader = ASN1.getReader(ocCursor.getValue());
        reader.readStartSequence();
        final List<String> objectClassNames = new LinkedList<String>();
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
    finally
    {
      ocCursor.close();
    }

    // Cursor through the attribute description database and load the attribute
    // set definitions.
    final Cursor<ByteString, ByteString> adCursor = txn.openCursor(adTreeName);
    try
    {
      while (adCursor.next())
      {
        final byte[] encodedAttribute = adCursor.getKey().toByteArray();
        final ASN1Reader reader = ASN1.getReader(adCursor.getValue());
        reader.readStartSequence();
        final String attributeName = reader.readOctetStringAsString();
        final List<String> attributeOptions = new LinkedList<String>();
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
    finally
    {
      adCursor.close();
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
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_COMPSCHEMA_CANNOT_STORE_EX.get(e.getMessage()), e);
    }
  }

}
