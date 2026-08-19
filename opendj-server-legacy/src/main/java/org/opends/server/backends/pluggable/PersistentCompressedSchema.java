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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

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
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
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

  /**
   * The tree prefix every backend shared before the definitions were separated per backend.
   * <p>
   * Every other tree of a backend is named from {@link EntryContainer#getTreePrefix()} and so
   * carries its base DN, but these two belong to the backend rather than to any one of its base
   * DNs and were named from a literal. That is harmless where a storage holds a single backend -
   * JE and PDB give each its own directory, and the Cassandra backend names its table after the
   * backend id - but the JDBC backend derives its table name from the tree name alone, so two
   * JDBC backends addressing one database met in this pair of tables. Each allocated tokens from
   * the size of its own in-memory map under its own lock, so both handed out the same token for
   * different attribute descriptions and overwrote each other's definitions; after a restart the
   * entries of the losing backend decoded as the wrong attributes, silently (issue #873).
   * <p>
   * Kept only to be read: {@link #load} migrates what it finds here into the backend's own trees
   * and never writes to it again.
   */
  private static final String LEGACY_TREE_PREFIX = "compressed_schema";
  private static final TreeName LEGACY_AD_TREE_NAME = new TreeName(LEGACY_TREE_PREFIX, DB_NAME_AD);
  private static final TreeName LEGACY_OC_TREE_NAME = new TreeName(LEGACY_TREE_PREFIX, DB_NAME_OC);

  /** The compressed attribute description schema tree of this backend. */
  private final TreeName adTreeName;
  /** The compressed object class set schema tree of this backend. */
  private final TreeName ocTreeName;

  /** The id of the backend these definitions belong to. */
  private final String backendId;

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
   * @param backendId
   *          The id of the backend whose definitions are held, which qualifies the trees so that
   *          two backends sharing one database do not share one token space.
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
  PersistentCompressedSchema(ServerContext serverContext, String backendId, final Storage storage,
      WriteableTransaction txn, AccessMode accessMode) throws StorageRuntimeException, InitializationException
  {
    super(serverContext);
    this.storage = storage;
    this.backendId = backendId;
    final String treePrefix = treePrefix(backendId);
    this.adTreeName = new TreeName(treePrefix, DB_NAME_AD);
    this.ocTreeName = new TreeName(treePrefix, DB_NAME_OC);
    load(txn, accessMode.isWriteable());
  }

  /**
   * Qualifies the legacy prefix with the backend id. {@link TreeName} splits its string form on
   * '/' and states that no component may contain one, and nothing rules a '/' out of a backend id
   * - ds-cfg-backend-id is a plain string - so it is escaped rather than passed through. The
   * escape is reversible, so two distinct backend ids can never produce one prefix.
   */
  private static String treePrefix(String backendId)
  {
    return LEGACY_TREE_PREFIX + "_" + backendId.replace("%", "%25").replace("/", "%2F");
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

    if (needsLegacyDefinitions(txn))
    {
      if (shouldCreate)
      {
        migrateLegacyDefinitions(txn);
      }
      else
      {
        // Read-only: nothing may be written, so the legacy definitions are read where they lie.
        // Loaded first, so that anything this backend has already migrated and since added under
        // its own prefix wins for the same token.
        loadTrees(txn, LEGACY_OC_TREE_NAME, LEGACY_AD_TREE_NAME);
      }
    }
    loadTrees(txn, ocTreeName, adTreeName);
  }

  /**
   * Tells whether the definitions under {@link #LEGACY_TREE_PREFIX} are still needed, either
   * because this backend has not been opened since the upgrade that separated them, or because a
   * previous migration did not run to completion - on a storage without transactions the copy can
   * stop halfway. Once migrated, this backend's trees only ever grow, so they can no longer hold
   * fewer records than the legacy ones and the question is settled without reading either tree.
   */
  private boolean needsLegacyDefinitions(ReadableTransaction txn)
  {
    final long legacyAdCount = recordCount(txn, LEGACY_AD_TREE_NAME);
    final long legacyOcCount = recordCount(txn, LEGACY_OC_TREE_NAME);
    return (legacyAdCount > 0 || legacyOcCount > 0)
        && (recordCount(txn, adTreeName) < legacyAdCount || recordCount(txn, ocTreeName) < legacyOcCount);
  }

  /** The number of records of a tree, without asking a storage to materialize one that is absent. */
  private long recordCount(ReadableTransaction txn, TreeName treeName)
  {
    return txn.treeExists(treeName) ? txn.getRecordCount(treeName) : 0;
  }

  private void migrateLegacyDefinitions(WriteableTransaction txn) throws InitializationException
  {
    try
    {
      final long copied = copyMissingRecords(txn, LEGACY_AD_TREE_NAME, adTreeName)
          + copyMissingRecords(txn, LEGACY_OC_TREE_NAME, ocTreeName);
      logger.info(NOTE_COMPSCHEMA_MIGRATED, copied, backendId, LEGACY_TREE_PREFIX, adTreeName.getBaseDN());
    }
    catch (final Exception e)
    {
      logger.traceException(e);
      // Deliberately fatal to the open. Loading no definitions at all would restart token
      // allocation from zero and decode every entry written so far as the wrong attributes,
      // without reporting anything.
      throw new InitializationException(ERR_COMPSCHEMA_CANNOT_MIGRATE.get(
          backendId, LEGACY_TREE_PREFIX, adTreeName.getBaseDN(), stackTraceToSingleLineString(e)), e);
    }
  }

  /**
   * Copies the records of {@code from} that {@code to} does not already hold, and returns how many
   * were copied. The legacy tree is left in place: on a shared database it may still be the only
   * copy another backend has, and leaving it is what makes a downgrade possible.
   * <p>
   * A key already present in {@code to} is never overwritten, which is what makes this safe to
   * re-run after an interrupted migration and safe against a legacy tree that a backend of an
   * earlier version is still writing to.
   */
  private long copyMissingRecords(WriteableTransaction txn, TreeName from, TreeName to)
  {
    if (!txn.treeExists(from))
    {
      return 0;
    }
    long copied = 0;
    try (Cursor<ByteString, ByteString> cursor = txn.openCursor(from))
    {
      while (cursor.next())
      {
        final ByteString key = cursor.getKey();
        if (txn.read(to, key) == null)
        {
          txn.put(to, key, cursor.getValue());
          copied++;
        }
      }
    }
    return copied;
  }

  /**
   * Loads the object class set definitions and then the attribute description definitions of the
   * provided pair of trees into the maps. A tree that does not exist contributes nothing: a
   * read-only open creates none, and a storage that keeps one object per tree fails outright when
   * asked to read one that was never written.
   */
  private void loadTrees(ReadableTransaction txn, TreeName ocTree, TreeName adTree) throws InitializationException
  {
    // Cursor through the object class database and load the object class set
    // definitions. At the same time, figure out the highest token value and
    // initialize the object class counter to one greater than that.
    if (txn.treeExists(ocTree))
    {
      try (Cursor<ByteString, ByteString> ocCursor = txn.openCursor(ocTree))
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
    }

    // Cursor through the attribute description database and load the attribute set definitions.
    if (txn.treeExists(adTree))
    {
      try (Cursor<ByteString, ByteString> adCursor = txn.openCursor(adTree))
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
