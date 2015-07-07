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
package org.opends.server.backends.jeb;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.api.CompressedSchema;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.OperationStatus;

import static com.sleepycat.je.LockMode.*;
import static com.sleepycat.je.OperationStatus.*;

import static org.opends.messages.BackendMessages.*;

/**
 * This class provides a compressed schema implementation whose definitions are
 * stored in a Berkeley DB JE database.
 */
public final class JECompressedSchema extends CompressedSchema
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The name of the database used to store compressed attribute description definitions. */
  private static final String DB_NAME_AD = "compressed_attributes";
  /** The name of the database used to store compressed object class set definitions. */
  private static final String DB_NAME_OC = "compressed_object_classes";

  /** The compressed attribute description schema database. */
  private Database adDatabase;
  /** The environment in which the databases are held. */
  private Environment environment;
  /** The compressed object class set schema database. */
  private Database ocDatabase;

  private final ByteStringBuilder storeAttributeWriterBuffer = new ByteStringBuilder();
  private final ASN1Writer storeAttributeWriter = ASN1.getWriter(storeAttributeWriterBuffer);
  private final ByteStringBuilder storeObjectClassesWriterBuffer = new ByteStringBuilder();
  private final ASN1Writer storeObjectClassesWriter = ASN1.getWriter(storeObjectClassesWriterBuffer);



  /**
   * Creates a new instance of this JE compressed schema manager.
   *
   * @param environment
   *          A reference to the database environment in which the databases
   *          will be held.
   * @throws DatabaseException
   *           If a database problem occurs while loading the compressed schema
   *           definitions from the database.
   * @throws InitializationException
   *           If an error occurs while loading and processing the compressed
   *           schema definitions.
   */
  public JECompressedSchema(final Environment environment)
      throws DatabaseException, InitializationException
  {
    this.environment = environment;
    load();
  }



  /**
   * Closes the databases and releases any resources held by this compressed
   * schema manager.
   */
  public void close()
  {
    close0(adDatabase);
    close0(ocDatabase);

    adDatabase = null;
    ocDatabase = null;
    environment = null;
  }

  private void close0(Database database)
  {
    try
    {
      database.sync();
    }
    catch (final Exception e)
    {
      // Ignore.
    }
    StaticUtils.close(database);
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
      store(adDatabase, encodedAttribute, storeAttributeWriterBuffer);
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
      store(ocDatabase, encodedObjectClasses, storeObjectClassesWriterBuffer);
    }
    catch (final IOException e)
    {
      // TODO: Shouldn't happen but should log a message
    }
  }



  /**
   * Loads the compressed schema information from the database.
   *
   * @throws DatabaseException
   *           If a database error occurs while loading the definitions from the
   *           database.
   * @throws InitializationException
   *           If an error occurs while loading and processing the definitions.
   */
  private void load() throws DatabaseException, InitializationException
  {
    final DatabaseConfig dbConfig = JEBUtils.toDatabaseConfigNoDuplicates(environment);

    adDatabase = environment.openDatabase(null, DB_NAME_AD, dbConfig);
    ocDatabase = environment.openDatabase(null, DB_NAME_OC, dbConfig);

    // Cursor through the object class database and load the object class set
    // definitions. At the same time, figure out the highest token value and
    // initialize the object class counter to one greater than that.
    final Cursor ocCursor = ocDatabase.openCursor(null, null);
    try
    {
      final DatabaseEntry keyEntry = new DatabaseEntry();
      final DatabaseEntry valueEntry = new DatabaseEntry();
      OperationStatus status = ocCursor.getFirst(keyEntry, valueEntry, READ_UNCOMMITTED);
      while (status == SUCCESS)
      {
        final byte[] encodedObjectClasses = keyEntry.getData();
        final ASN1Reader reader = ASN1.getReader(valueEntry.getData());
        reader.readStartSequence();
        final List<String> objectClassNames = new LinkedList<>();
        while (reader.hasNextElement())
        {
          objectClassNames.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
        loadObjectClasses(encodedObjectClasses, objectClassNames);
        status = ocCursor.getNext(keyEntry, valueEntry, READ_UNCOMMITTED);
      }
    }
    catch (final IOException e)
    {
      logger.traceException(e);
      throw new InitializationException(
          ERR_COMPSCHEMA_CANNOT_DECODE_OC_TOKEN.get(e.getMessage()), e);
    }
    finally
    {
      ocCursor.close();
    }

    // Cursor through the attribute description database and load the attribute
    // set definitions.
    final Cursor adCursor = adDatabase.openCursor(null, null);
    try
    {
      final DatabaseEntry keyEntry = new DatabaseEntry();
      final DatabaseEntry valueEntry = new DatabaseEntry();
      OperationStatus status = adCursor.getFirst(keyEntry, valueEntry, READ_UNCOMMITTED);
      while (status == SUCCESS)
      {
        final byte[] encodedAttribute = keyEntry.getData();
        final ASN1Reader reader = ASN1.getReader(valueEntry.getData());
        reader.readStartSequence();
        final String attributeName = reader.readOctetStringAsString();
        final List<String> attributeOptions = new LinkedList<>();
        while (reader.hasNextElement())
        {
          attributeOptions.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
        loadAttribute(encodedAttribute, attributeName, attributeOptions);
        status = adCursor.getNext(keyEntry, valueEntry, READ_UNCOMMITTED);
      }
    }
    catch (final IOException e)
    {
      logger.traceException(e);
      throw new InitializationException(
          ERR_COMPSCHEMA_CANNOT_DECODE_AD_TOKEN.get(e.getMessage()), e);
    }
    finally
    {
      adCursor.close();
    }
  }



  private void store(final Database database, final byte[] key, final ByteStringBuilder value) throws DirectoryException
  {
    if (!putNoOverwrite(database, key, value))
    {
      final LocalizableMessage m = ERR_JEB_COMPSCHEMA_CANNOT_STORE_MULTIPLE_FAILURES.get();
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), m);
    }
  }

  private boolean putNoOverwrite(final Database database, final byte[] key, final ByteStringBuilder value)
      throws DirectoryException
  {
    final DatabaseEntry keyEntry = new DatabaseEntry(key);
    final DatabaseEntry valueEntry = new DatabaseEntry(value.getBackingArray(), 0, value.length());
    for (int i = 0; i < 3; i++)
    {
      try
      {
        final OperationStatus status = database.putNoOverwrite(null, keyEntry, valueEntry);
        if (status != SUCCESS)
        {
          final LocalizableMessage m = ERR_JEB_COMPSCHEMA_CANNOT_STORE_STATUS.get(status);
          throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), m);
        }
        return true;
      }
      catch (final LockConflictException ce)
      {
        continue;
      }
      catch (final DatabaseException de)
      {
        throw new DirectoryException(
            DirectoryServer.getServerErrorResultCode(), ERR_COMPSCHEMA_CANNOT_STORE_EX.get(de.getMessage()), de);
      }
    }
    return false;
  }

}
