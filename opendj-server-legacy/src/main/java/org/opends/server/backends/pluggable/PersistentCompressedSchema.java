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

  /** How many definitions the migration copied, reported by {@link #reportMigration()} once committed. */
  private long migratedDefinitions;

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
   * Qualifies the legacy prefix with the backend id. {@link TreeName} documents that it assumes no
   * name component contains a '/', and {@code TreeName.valueOf} splits the string form at the last
   * one, so a prefix carrying a '/' would come back as a different name than it went in as.
   * Nothing rules a '/' out of a backend id - ds-cfg-backend-id is a plain string - so it is
   * escaped rather than passed through. The escape is reversible, so two distinct backend ids can
   * never produce one prefix.
   * <p>
   * The qualifier is the backend id rather than a base DN because this object is built once per
   * {@link RootContainer}, before any entry container exists, and a backend holding two base DNs
   * has no single prefix to borrow. The price is that these two trees, alone among the trees of a
   * backend, do not follow the entries when a JDBC backend is deleted and re-created under another
   * id over the same database: every other tree is named from its base DN and is found again,
   * while these are not, leaving the token allocation to restart at zero over entries encoded with
   * the definitions of the old id. Changing a backend id over populated storage needs an export
   * and a re-import - as it always has on Cassandra, where the one table of a backend is named
   * after the id and the entries do not survive the rename either.
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
    if (shouldCreate)
    {
      // Asked for only where the trees may be created. A read-only open must leave the storage as
      // it found it, and passing the flag on would not: JEStorage.openTree ignores createOnDemand
      // and reaches env.openDatabase() with setAllowCreate(true), so an offline export-ldif or
      // verify-index of a backend that has not migrated yet would leave two empty databases behind.
      // Nothing below needs the trees open - every read is guarded by treeExists().
      txn.openTree(adTreeName, true);
      txn.openTree(ocTreeName, true);
    }

    if (needsLegacyDefinitions(txn))
    {
      if (shouldCreate)
      {
        migrateLegacyDefinitions(txn);
      }
      else
      {
        // Read-only: nothing may be written, so the legacy definitions are read where they lie.
        // Loaded first, so that a token this backend has already migrated, and since re-used under
        // its own prefix, decodes to its own definition. That settles the decode map only:
        // CompressedSchema.loadAttributeToMaps keys the encode map by attribute description, so a
        // legacy description displaced from a token stays in it and would encode to a token that
        // now decodes to another attribute. Harmless only because nothing encodes during a
        // read-only open - export-ldif and verify-index decode.
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
   * <p>
   * That invariant holds in one direction only. A version from before the separation resumes
   * writing to the legacy pair, so after a downgrade and a second upgrade the counts can agree
   * while the definitions behind them have diverged, and nothing is migrated. Downgrading across
   * the separation is not a supported path - the legacy trees are left in place so that a backend
   * still running an earlier version can go on reading them, not so that this one can go back.
   * <p>
   * The question is settled again on every open, nothing recording that it has been answered
   * before: two existence probes, and the record counts only where the legacy trees are still
   * there. That is what not writing a marker of this backend's own into a database it may be
   * sharing with another one costs.
   * <p>
   * A backend created after the upgrade on a database that already holds legacy definitions copies
   * them although it has no entries of its own. Deliberate: what it inherits is a consistent
   * token-to-definition mapping and costs one copy, whereas asking instead whether its own trees
   * are absent would answer "nothing to migrate" for the half-copied trees an interrupted
   * migration leaves behind - the one case this method exists to catch.
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
    migratedDefinitions = migrateTree(txn, LEGACY_AD_TREE_NAME, adTreeName)
        + migrateTree(txn, LEGACY_OC_TREE_NAME, ocTreeName);
  }

  private long migrateTree(WriteableTransaction txn, TreeName from, TreeName to) throws InitializationException
  {
    try
    {
      return copyMissingRecords(txn, from, to);
    }
    catch (final StorageRuntimeException e)
    {
      // Left with the type the storage gave it. The migration runs inside the WriteOperation of
      // RootContainer.open(), and PDBStorage.write() decides by type what to do with what leaves
      // it: a transaction conflict reaches its retry as a RollbackException only, so wrapping it
      // here would turn a conflict that used to be replayed into a permanent failure of the open.
      // Nothing is lost - a storage failure that is not a conflict still fails the open, through
      // ERR_OPEN_ENV_FAIL, and leaves the trees as they were, since the transaction rolls back.
      throw e;
    }
    catch (final Exception e)
    {
      logger.traceException(e);
      // Deliberately fatal to the open. Loading no definitions at all would restart token
      // allocation from zero and decode every entry written so far as the wrong attributes,
      // without reporting anything.
      throw new InitializationException(
          ERR_COMPSCHEMA_CANNOT_MIGRATE.get(backendId, from, to, stackTraceToSingleLineString(e)), e);
    }
  }

  /**
   * Reports a migration that has been committed, and reports nothing where none was needed.
   * <p>
   * Called by {@link RootContainer#open} once the transaction the migration ran in has committed,
   * and only then: a copy is undone by a rollback - a later failure of the same transaction, or a
   * conflict PersistIt replays - and this line is the only evidence the migration path emits, so
   * one standing for a copy that was rolled back, or repeated once per replay, would be worse than
   * none at all.
   */
  void reportMigration()
  {
    if (migratedDefinitions > 0)
    {
      logger.info(NOTE_COMPSCHEMA_MIGRATED, migratedDefinitions, backendId,
          LEGACY_AD_TREE_NAME, LEGACY_OC_TREE_NAME, adTreeName, ocTreeName);
    }
  }

  /**
   * Copies the records of {@code from} that {@code to} does not already hold, and returns how many
   * were copied. The legacy tree is left in place: on a shared database it may still be the only
   * copy a backend that has not been upgraded yet has.
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
