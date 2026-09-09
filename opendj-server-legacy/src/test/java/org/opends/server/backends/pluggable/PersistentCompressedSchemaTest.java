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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pluggable.DefaultIndexTest.DummyWriteableTransaction;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Attributes;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The compressed schema of a backend must not be shared with another backend that happens to
 * address the same database, and a backend upgraded from a version that did share it must keep
 * decoding the entries it wrote before the upgrade (issue #873).
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class PersistentCompressedSchemaTest extends DirectoryServerTestCase
{
  private static final String AD = "compressed_attributes";
  private static final String OC = "compressed_object_classes";

  /** The pair every backend wrote to before the definitions were separated. */
  private static final TreeName LEGACY_AD = new TreeName("compressed_schema", AD);
  private static final TreeName LEGACY_OC = new TreeName("compressed_schema", OC);

  private ServerContext serverContext;
  private SharedDatabase txn;
  private Storage storage;

  @BeforeClass
  public void startServer() throws Exception
  {
    // Needed for the schema: loading a definition resolves its attribute type against the server's.
    TestCaseUtils.startServer();
    serverContext = TestCaseUtils.getServerContext();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
    // One transaction shared by every backend of a test stands for one database addressed by all of
    // them - the JDBC deployment of the issue. On JE or PDB each backend would have its own.
    txn = new SharedDatabase();
    storage = mock(Storage.class);
    doAnswer(invocation -> {
      ((WriteOperation) invocation.getArguments()[0]).run(txn);
      return null;
    }).when(storage).write(any(WriteOperation.class));
  }

  /**
   * The heart of the issue: both backends allocate the first token of their own map for a different
   * attribute description, so if they shared a tree the second would overwrite the definition of
   * the first and the entries of the first would decode as the wrong attribute after a restart.
   */
  @Test
  public void backendsSharingOneDatabaseDoNotShareTheTokenSpace() throws Exception
  {
    // Both are opened before either writes, as they are at server start: each loads the same state
    // and so holds the same next token. Opening the second one after the first had written would
    // hide the issue, since it would have loaded the definition the first one just stored.
    final PersistentCompressedSchema backendA = open("backendA", AccessMode.READ_WRITE);
    final PersistentCompressedSchema backendB = open("backendB", AccessMode.READ_WRITE);
    final ByteString encodedByA = encode(backendA, "cn");
    final ByteString encodedByB = encode(backendB, "sn");

    // Re-opening is what a restart does: the definitions come back from the trees, not from memory.
    assertThat(decode(open("backendA", AccessMode.READ_WRITE), encodedByA)).isEqualTo("cn");
    assertThat(decode(open("backendB", AccessMode.READ_WRITE), encodedByB)).isEqualTo("sn");

    // and nothing was written under the prefix they used to share
    assertThat(txn.treeExists(LEGACY_AD)).isFalse();
    assertThat(txn.treeExists(LEGACY_OC)).isFalse();
  }

  /** A backend upgraded from a version that wrote under the shared prefix finds its definitions. */
  @Test
  public void definitionsWrittenBeforeTheUpgradeAreMigrated() throws Exception
  {
    final ByteString encoded = encode(open("backendA", AccessMode.READ_WRITE), "cn");
    makeStoreLookPreUpgrade("backendA");

    assertThat(decode(open("backendA", AccessMode.READ_WRITE), encoded)).isEqualTo("cn");
    assertThat(txn.getRecordCount(ownTree("backendA", AD))).isEqualTo(txn.getRecordCount(LEGACY_AD));
    assertThat(txn.getRecordCount(ownTree("backendA", OC))).isEqualTo(txn.getRecordCount(LEGACY_OC));
  }

  /**
   * The shared trees are read, never emptied: on a database addressed by several backends they may
   * still be the only copy another backend has, and leaving them is what makes a downgrade possible.
   */
  @Test
  public void theSharedTreesSurviveTheMigration() throws Exception
  {
    encode(open("backendA", AccessMode.READ_WRITE), "cn");
    makeStoreLookPreUpgrade("backendA");
    final long legacyRecords = txn.getRecordCount(LEGACY_AD);

    open("backendA", AccessMode.READ_WRITE);

    assertThat(txn.getRecordCount(LEGACY_AD)).isEqualTo(legacyRecords);
  }

  /**
   * A migration interrupted halfway - a storage without transactions can stop between two records -
   * is finished by the next open rather than leaving the backend with a partial token space.
   */
  @Test
  public void anInterruptedMigrationIsFinishedByTheNextOpen() throws Exception
  {
    final ByteString encoded = encode(open("backendA", AccessMode.READ_WRITE), "cn");
    makeStoreLookPreUpgrade("backendA");
    open("backendA", AccessMode.READ_WRITE);

    // undo one record of the completed migration, which is what an interrupted one would have left
    txn.delete(ownTree("backendA", AD), firstKeyOf(ownTree("backendA", AD)));

    assertThat(decode(open("backendA", AccessMode.READ_WRITE), encoded)).isEqualTo("cn");
    assertThat(txn.getRecordCount(ownTree("backendA", AD))).isEqualTo(txn.getRecordCount(LEGACY_AD));
  }

  /**
   * export-ldif and verify-index open the root container read-only, where the migration cannot run.
   * The definitions must still be found, and nothing may be written - not even the empty trees an
   * openTree() would leave behind, which is why {@link SharedDatabase} creates one whenever it is
   * asked for a tree at all.
   */
  @Test
  public void aReadOnlyOpenReadsTheSharedTreesWhereTheyLie() throws Exception
  {
    final ByteString encoded = encode(open("backendA", AccessMode.READ_WRITE), "cn");
    makeStoreLookPreUpgrade("backendA");

    assertThat(decode(open("backendA", AccessMode.READ_ONLY), encoded)).isEqualTo("cn");
    assertThat(txn.treeExists(ownTree("backendA", AD))).isFalse();
    assertThat(txn.treeExists(ownTree("backendA", OC))).isFalse();
  }

  /**
   * The migration fills the gaps of this backend and touches nothing else: where the shared trees
   * and this backend disagree about what a token stands for - which is the state issue #873 leaves
   * behind, two backends having allocated the same token for different attributes - the definition
   * this backend's own entries were encoded against is the one that survives.
   */
  @Test
  public void aDefinitionOfThisBackendIsNeverOverwrittenByTheSharedOne() throws Exception
  {
    final ByteString encodedByA = encode(open("backendA", AccessMode.READ_WRITE), "cn");

    // What another backend left under the shared prefix before the upgrade: the same first token,
    // standing for a different attribute, and one definition beyond what backendA holds, so that
    // the record counts send the migration on its way.
    final PersistentCompressedSchema backendB = open("backendB", AccessMode.READ_WRITE);
    encode(backendB, "sn");
    encode(backendB, "description");
    copyTree(ownTree("backendB", AD), LEGACY_AD);
    copyTree(ownTree("backendB", OC), LEGACY_OC);

    assertThat(decode(open("backendA", AccessMode.READ_WRITE), encodedByA)).isEqualTo("cn");
    // and the definition backendA did not have was still copied, so the migration did run
    assertThat(txn.getRecordCount(ownTree("backendA", AD))).isEqualTo(txn.getRecordCount(LEGACY_AD));
  }

  /**
   * A migration that cannot complete is fatal to the open: carrying on with the definitions half
   * copied would restart the token allocation part way through the map the entries were encoded
   * with, and report nothing.
   */
  @Test
  public void aMigrationThatCannotCompleteFailsTheOpen() throws Exception
  {
    encode(open("backendA", AccessMode.READ_WRITE), "cn");
    makeStoreLookPreUpgrade("backendA");
    final RuntimeException failure = new IllegalStateException("no space left on device");
    txn.failEveryWriteWith(failure);

    try
    {
      open("backendA", AccessMode.READ_WRITE);
      fail("the open must not succeed on a migration that failed");
    }
    catch (final InitializationException e)
    {
      assertThat(e.getCause()).isSameAs(failure);
      // and the message names the backend and both trees, since which of the two failed is the point
      assertThat(e.getMessage()).contains("backendA")
          .contains(LEGACY_AD.toString()).contains(ownTree("backendA", AD).toString());
    }
  }

  /**
   * A failure the storage reports as its own is left with the type it was given. The migration runs
   * inside the WriteOperation of {@link RootContainer#open}, and PDBStorage.write() decides by type
   * what to do with what leaves it: a transaction conflict it would have replayed must not come out
   * as something else, or the open fails where it used to be retried.
   */
  @Test
  public void aStorageFailureIsLeftForTheStorageToRecognize() throws Exception
  {
    encode(open("backendA", AccessMode.READ_WRITE), "cn");
    makeStoreLookPreUpgrade("backendA");
    final StorageRuntimeException conflict = new StorageRuntimeException("transaction rolled back");
    txn.failEveryWriteWith(conflict);

    try
    {
      open("backendA", AccessMode.READ_WRITE);
      fail("the open must not succeed on a migration that failed");
    }
    catch (final StorageRuntimeException e)
    {
      assertThat(e).isSameAs(conflict);
    }
  }

  /**
   * A record stored under a key no compressed schema hands out must cost the definition it carries
   * and nothing else: the backend still opens, and the definitions stored under the keys this
   * schema did write are still there (issue #897).
   * <p>
   * The load path folds whatever key a record is stored under into an id and hands it straight to
   * the decode map, so an all-zero key - what a truncated record leaves - reached it as the index
   * -1 and left the open as an IndexOutOfBoundsException, while a key folding to the largest id
   * four bytes address left it padding the map two billion slots, one at a time, with nothing in
   * the log to say what the open was waiting for.
   */
  @Test
  public void aDefinitionStoredUnderAnUnusableKeyDoesNotStopTheOpen() throws Exception
  {
    final ByteString encoded = encode(open("backendA", AccessMode.READ_WRITE), "cn");
    // Read before the definition of "cn", whose key is 0x01: a cursor walks the keys in order, so
    // the open used to end on this one before it reached anything else.
    storeDefinitionUnder(ownTree("backendA", AD), ByteString.wrap(new byte[] { 0x00 }), "description");
    storeDefinitionUnder(ownTree("backendA", AD),
        ByteString.wrap(new byte[] { 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }), "sn");

    final PersistentCompressedSchema reopened = open("backendA", AccessMode.READ_WRITE);

    assertThat(decode(reopened, encoded)).isEqualTo("cn");
    // and a definition that was skipped is registered afresh when it is next encoded, rather than
    // taken from the key it was stored under: it gets a token this schema hands out and stores.
    assertThat(decode(reopened, encode(reopened, "description"))).isEqualTo("description");
  }

  /** Writes an attribute description definition under a key of this test's choosing. */
  private void storeDefinitionUnder(TreeName treeName, ByteString key, String attributeName) throws Exception
  {
    final ByteStringBuilder definition = new ByteStringBuilder();
    final ASN1Writer writer = ASN1.getWriter(definition);
    writer.writeStartSequence();
    writer.writeOctetString(attributeName);
    writer.writeEndSequence();
    txn.put(treeName, key, definition);
  }

  private PersistentCompressedSchema open(String backendId, AccessMode accessMode) throws Exception
  {
    return new PersistentCompressedSchema(serverContext, backendId, storage, txn, accessMode);
  }

  private static TreeName ownTree(String backendId, String indexId)
  {
    return new TreeName("compressed_schema_" + backendId, indexId);
  }

  private ByteString encode(PersistentCompressedSchema schema, String attributeName) throws Exception
  {
    final ByteStringBuilder builder = new ByteStringBuilder();
    schema.encodeAttribute(builder, Attributes.create(attributeName, "a value"));
    return builder.toByteString();
  }

  private String decode(PersistentCompressedSchema schema, ByteString encoded) throws Exception
  {
    return schema.decodeAttribute(encoded.asReader()).getAttributeDescription().getAttributeType().getNameOrOID();
  }

  /** Moves the definitions of a backend back under the shared prefix, as an earlier version left them. */
  private void makeStoreLookPreUpgrade(String backendId) throws Exception
  {
    copyTree(ownTree(backendId, AD), LEGACY_AD);
    copyTree(ownTree(backendId, OC), LEGACY_OC);
    txn.deleteTree(ownTree(backendId, AD));
    txn.deleteTree(ownTree(backendId, OC));
  }

  private void copyTree(TreeName from, TreeName to)
  {
    txn.openTree(to, true);
    try (Cursor<ByteString, ByteString> cursor = txn.openCursor(from))
    {
      while (cursor.next())
      {
        txn.put(to, cursor.getKey(), cursor.getValue());
      }
    }
  }

  private ByteString firstKeyOf(TreeName treeName)
  {
    try (Cursor<ByteString, ByteString> cursor = txn.openCursor(treeName))
    {
      assertThat(cursor.next()).isTrue();
      return cursor.getKey();
    }
  }

  /**
   * The one database every backend of a test addresses. Two behaviours of a real storage are
   * modelled on purpose, so that the assertions above can fail:
   * <ul>
   * <li>a tree is materialized whenever one is asked for, whether or not creation was requested.
   * That is what JEStorage does - dbConfig() sets allowCreate, and openTree() reaches
   * env.openDatabase() with it, whatever createOnDemand says - so a read-only open that asks at all
   * leaves empty databases behind an offline tool run;</li>
   * <li>a write can be made to fail, which is the only way to reach the migration's failure path.</li>
   * </ul>
   */
  private static final class SharedDatabase extends DummyWriteableTransaction
  {
    private RuntimeException failure;

    void failEveryWriteWith(RuntimeException failure)
    {
      this.failure = failure;
    }

    @Override
    public void openTree(TreeName name, boolean createOnDemand)
    {
      super.openTree(name, true);
    }

    @Override
    public void put(TreeName treeName, ByteSequence key, ByteSequence value)
    {
      if (failure != null)
      {
        throw failure;
      }
      super.put(treeName, key, value);
    }
  }
}
