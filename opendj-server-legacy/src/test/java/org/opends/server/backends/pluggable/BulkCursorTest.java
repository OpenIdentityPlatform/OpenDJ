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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.ServerContext;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.types.LDIFExportConfig;
import org.testng.annotations.Test;

/**
 * Which cursor the walks of a whole tree ask for (#877).
 * <p>
 * {@code ReadableTransaction.openBulkCursor()} is a {@code default} answering exactly as
 * {@code openCursor()} does, so every storage engine but the JDBC backend behaves the same either
 * way and a call site turned back into {@code openCursor()} would compile, run, and stay invisible
 * everywhere - while on the JDBC backend it would take the bound of a client operation, two
 * minutes by default, and abort the walk of a tree larger than that. Some of these walks have
 * nobody at a command line to see it happen: the load of the compressed schema and the read that
 * checks id2entry is there, both on the path of {@code start-ds}, and the generation ID a
 * replicated domain computes for itself the first time it starts, which is an export of the whole
 * of id2entry.
 * <p>
 * Each test below pins one such call site twice: that the bulk cursor is what it asks for, and
 * that it asks for no cursor of an operation at all. The second half only bites where the walk
 * really runs its body, so a walk whose fixture holds a record - {@code iterateDN2ID} - is given
 * one: with an empty tree the loop stops on its first step and every {@code never()} below it
 * passes on a run that reached nothing.
 * <p>
 * Six call sites are pinned that way - {@code ExportJob}, the id2entry, dn2id and VLV walks of
 * {@code VerifyJob}, and both trees of {@code PersistentCompressedSchema} - together with the
 * children count each row of the dn2id walk reads and the total the progress report of a verify
 * is sized with, the latter pinned one hop above its cursor. Three more are held by other means:
 * {@code ID2Entry.afterOpen()} has {@code ID2EntryTest}, the override that gives the class its
 * meaning has {@code JDBCStatementBoundTestCase}, and {@code VerifyJob.iterateID2ChildrenCount()}
 * cannot be reverted at all, since {@code ID2ChildrenCount} exposes no cursor but the bulk one and
 * the revert would not compile. The last three tests pin a delegation rather than its caller, which
 * is all that is available for them.
 * <p>
 * What none of this covers is the single-row {@code ReadableTransaction.read()} these same walks
 * make - {@code id2entry.get()} once per row of dn2id and of a VLV index - which has no bulk form
 * in the SPI at all and takes the class of the transaction it is made through. That is a gap of
 * the SPI rather than of a call site: a read by primary key is not the scan a cursor batch is, so
 * what it risks is a lock wait rather than a walk cut short.
 * <p>
 * Three call sites are left uncovered and are named here rather than passed over: the attribute
 * index of {@code verify-index} ({@code VerifyJob.iterateAttrIndex}), whose {@code MatchingRuleIndex}
 * is {@code final} and so cannot be handed to this suite, and the two of {@code BackendStat}. All
 * three walk a tree only on the command line of an operator.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class BulkCursorTest extends DirectoryServerTestCase
{
  private final TreeName id2entryName = new TreeName("dc=example,dc=com", "id2entry");

  @SuppressWarnings("unchecked")
  private static Cursor<ByteString, ByteString> emptyCursor()
  {
    return mock(Cursor.class); // next() answers false, so the walk stops on its first step
  }

  /** A cursor over a single record, so that the body of a walk really runs once. */
  @SuppressWarnings("unchecked")
  private static Cursor<ByteString, ByteString> cursorOver(ByteString key, ByteString value)
  {
    final Cursor<ByteString, ByteString> cursor = mock(Cursor.class);
    when(cursor.next()).thenReturn(true, false);
    when(cursor.getKey()).thenReturn(key);
    when(cursor.getValue()).thenReturn(value);
    return cursor;
  }

  /** A transaction whose every cursor is the empty one above, whichever kind is asked for. */
  private static ReadableTransaction transactionWithEmptyCursors()
  {
    final ReadableTransaction txn = mock(ReadableTransaction.class);
    when(txn.openBulkCursor(any(TreeName.class))).thenReturn(emptyCursor());
    when(txn.openCursor(any(TreeName.class))).thenReturn(emptyCursor());
    return txn;
  }

  /**
   * Gives a mock of a tree the name it would have been constructed with.
   * {@code AbstractTree.getName()} is {@code public final} over a private field, so mockito cannot
   * stub it and the instance it builds - whose constructor never runs - answers {@code null}. A
   * production call of {@code openBulkCursor(index.getName())} then passes {@code null}, which
   * {@code any(TreeName.class)} happily matches under the mockito pinned here: the assertion would
   * accept a walk of any tree at all, the wrong one included.
   */
  private static <T extends AbstractTree> T named(T tree, TreeName name) throws Exception
  {
    final Field field = AbstractTree.class.getDeclaredField("name");
    field.setAccessible(true);
    field.set(tree, name);
    return tree;
  }

  /**
   * An {@code export-ldif} walks the whole of id2entry, and so does the generation ID a replicated
   * domain computes for itself the first time it starts - {@code LDAPReplicationDomain
   * .computeGenerationId()} exports the backend to compute it, with no client operation waiting on
   * it and no operator watching it fail.
   */
  @Test
  public void testAnExportWalksId2entryWithABulkCursor() throws Exception
  {
    final ReadableTransaction txn = transactionWithEmptyCursors();
    final EntryContainer entryContainer = mock(EntryContainer.class);
    when(entryContainer.getID2Entry()).thenReturn(new ID2Entry(id2entryName, new DataConfig.Builder().build()));

    // LDIFExportConfig is final and nothing is written here, the walk stopping on its first step
    new ExportJob(new LDIFExportConfig(new ByteArrayOutputStream())).exportContainer(txn, entryContainer);

    verify(txn).openBulkCursor(id2entryName);
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /** {@code verify-index} walks id2entry whole, checking every entry against the indexes. */
  @Test
  public void testAVerifyWalksId2entryWithABulkCursor() throws Exception
  {
    final ReadableTransaction txn = transactionWithEmptyCursors();
    final VerifyJob job = new VerifyJob(mock(RootContainer.class), mock(VerifyConfig.class));
    job.id2entry = new ID2Entry(id2entryName, new DataConfig.Builder().build());

    job.iterateID2Entry(txn);

    verify(txn).openBulkCursor(id2entryName);
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /**
   * And it walks dn2id whole as well, rebuilding the children counts as it goes - which is a read
   * of the children count tree per DN, inside that walk and belonging to it. Read as a client
   * operation those would put the bound of an entry read over a verify nobody is waiting on, once
   * for every DN of the backend, so the tree here holds a record: with an empty one the walk stops
   * before its first row and the assertions below hold whatever the counters do.
   */
  @Test
  public void testAVerifyWalksDn2idAndItsChildrenCountsWithBulkCursors() throws Exception
  {
    final TreeName dn2idName = new TreeName("dc=example,dc=com", "dn2id");
    final TreeName id2childrenCountName = new TreeName("dc=example,dc=com", "id2childrencount");
    final ReadableTransaction txn = transactionWithEmptyCursors();
    // built before it is handed over: stubbing it inside the argument of thenReturn() would open a
    // stubbing while the one it belongs to is still unfinished, which mockito refuses
    final Cursor<ByteString, ByteString> dn2idRows =
        cursorOver(ByteString.valueOfUtf8("dc=example,dc=com"), ByteString.valueOfLong(1));
    when(txn.openBulkCursor(dn2idName)).thenReturn(dn2idRows);
    final VerifyJob job = new VerifyJob(mock(RootContainer.class), mock(VerifyConfig.class));
    job.dn2id = new DN2ID(dn2idName, DN.valueOf("dc=example,dc=com"));
    // read for the one row above, and answered with nothing: the entry it points at is not what
    // this suite is about, and a missing one is counted as an error rather than thrown
    job.id2entry = new ID2Entry(id2entryName, new DataConfig.Builder().build());
    job.id2childrenCount = new ID2ChildrenCount(id2childrenCountName);

    job.iterateDN2ID(txn);

    verify(txn).openBulkCursor(dn2idName);
    verify(txn).openBulkCursor(id2childrenCountName); // the count of the DN the walk just passed
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /** A VLV index of a verify is walked whole too, key by key. */
  @Test
  public void testAVerifyWalksAVlvIndexWithABulkCursor() throws Exception
  {
    final TreeName vlvIndexName = new TreeName("dc=example,dc=com", "vlv.people");
    final ReadableTransaction txn = transactionWithEmptyCursors();
    final VerifyJob job = new VerifyJob(mock(RootContainer.class), mock(VerifyConfig.class));

    job.iterateVLVIndex(txn, named(mock(VLVIndex.class), vlvIndexName), true);

    verify(txn).openBulkCursor(vlvIndexName);
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /**
   * The compressed schema is loaded by walking both of its trees whole, while the backend opens:
   * {@code RootContainer.open()} constructs it before a single client operation can run, and a
   * walk cut short there is a backend that does not open at all.
   */
  @Test
  public void testTheCompressedSchemaIsLoadedWithBulkCursors() throws Exception
  {
    final WriteableTransaction txn = mock(WriteableTransaction.class);
    // both trees are there and empty: loadTrees() walks only a tree that exists (#873), and the
    // record counts a mock answers with leave nothing to migrate from the legacy pair
    when(txn.treeExists(any(TreeName.class))).thenReturn(true);
    when(txn.openBulkCursor(any(TreeName.class))).thenReturn(emptyCursor());
    when(txn.openCursor(any(TreeName.class))).thenReturn(emptyCursor());

    new PersistentCompressedSchema(mock(ServerContext.class), "bulkCursorTest", mock(Storage.class), txn,
        AccessMode.READ_ONLY);

    verify(txn, times(2)).openBulkCursor(any(TreeName.class)); // the object classes and the attributes
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /**
   * An index walked whole - by {@code verify-index} or by {@code dbtest} - asks the transaction
   * for a bulk cursor, while {@code Index.openCursor()} stays what an operation evaluating a
   * filter takes. This pins the delegation rather than either of its call sites: the index a
   * verify walks is a {@code MatchingRuleIndex}, which is {@code final} and cannot be handed to a
   * mock, and the one {@code dbtest} walks is chosen inside {@code BackendStat}.
   */
  @Test
  public void testAnIndexWalkedWholeAsksForABulkCursor() throws Exception
  {
    final TreeName indexName = new TreeName("dc=example,dc=com", "cn.caseIgnoreMatch");
    final ReadableTransaction txn = transactionWithEmptyCursors();
    final CryptoSuite cryptoSuite = mock(CryptoSuite.class);
    when(cryptoSuite.isEncrypted()).thenReturn(false);
    final DefaultIndex index =
        new DefaultIndex(indexName, mock(State.class), 5, mock(EntryContainer.class), cryptoSuite);

    index.openBulkCursor(txn);

    verify(txn).openBulkCursor(indexName);
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /**
   * The children counts are walked whole by {@code verify-index} through {@code ShardedCounter}.
   * The delegation again, its one call site - {@code VerifyJob.iterateID2ChildrenCount()} - being
   * held by the compiler instead: {@code ID2ChildrenCount} exposes no cursor but this one, so a
   * revert to {@code openCursor} does not compile.
   */
  @Test
  public void testTheChildrenCountsAreWalkedWithABulkCursor() throws Exception
  {
    final TreeName id2childrenCountName = new TreeName("dc=example,dc=com", "id2childrencount");
    final ReadableTransaction txn = transactionWithEmptyCursors();

    new ID2ChildrenCount(id2childrenCountName).openBulkCursor(txn);

    verify(txn).openBulkCursor(id2childrenCountName);
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /**
   * The record check inside that walk is bulk as well: {@code VerifyJob.iterateID2ChildrenCount()}
   * asks it once per record of the children count tree, so a cursor of a client operation there is
   * the same hazard as one over the tree itself. The delegation again - that walk is private, and
   * {@code containsEntryID} has no other caller to keep an operation-class form for.
   */
  @Test
  public void testTheRecordCheckOfAWholeTreeWalkAsksForABulkCursor() throws Exception
  {
    final ReadableTransaction txn = transactionWithEmptyCursors();

    new ID2Entry(id2entryName, new DataConfig.Builder().build()).containsEntryID(txn, new EntryID(1));

    verify(txn).openBulkCursor(id2entryName);
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /**
   * And a count read outside such a walk is a client operation, which the bulk read above must not
   * quietly turn into: an LDAP search asking for {@code numSubordinates} reads one, and there a
   * bound of a client operation is exactly what it should take.
   */
  @Test
  public void testAChildrenCountOfAClientOperationStaysAnOperation() throws Exception
  {
    final TreeName id2childrenCountName = new TreeName("dc=example,dc=com", "id2childrencount");
    final ReadableTransaction txn = transactionWithEmptyCursors();

    new ID2ChildrenCount(id2childrenCountName).getCount(txn, new EntryID(1));

    verify(txn).openCursor(id2childrenCountName);
    verify(txn, never()).openBulkCursor(any(TreeName.class));
  }

  /**
   * The total is one such count read on one key, so it takes the class of whoever asks: the walk
   * that reads it to size its progress report, or the client operation that reads the same total.
   */
  @Test
  public void testTheTotalCountOfAWholeTreeWalkAsksForABulkCursor() throws Exception
  {
    final TreeName id2childrenCountName = new TreeName("dc=example,dc=com", "id2childrencount");
    final ReadableTransaction txn = transactionWithEmptyCursors();

    new ID2ChildrenCount(id2childrenCountName).getTotalCount(txn, true);

    verify(txn).openBulkCursor(id2childrenCountName);
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /** And {@code cn=monitor} reading the same total is a client operation. */
  @Test
  public void testTheTotalCountOfAClientOperationStaysAnOperation() throws Exception
  {
    final TreeName id2childrenCountName = new TreeName("dc=example,dc=com", "id2childrencount");
    final ReadableTransaction txn = transactionWithEmptyCursors();

    new ID2ChildrenCount(id2childrenCountName).getTotalCount(txn);

    verify(txn).openCursor(id2childrenCountName);
    verify(txn, never()).openBulkCursor(any(TreeName.class));
  }

  /**
   * The count a verify reads to size its progress report belongs to the walk it measures. Its
   * three siblings - the record counts of dn2id, of the children count tree and of a VLV index -
   * are bulk by the tree they count, and this one was the branch left reading as a client
   * operation: it is also the only one a plain {@code verify-index} reaches, the other three
   * being the {@code --clean} path.
   * <p>
   * Pinned on the container rather than on a cursor, that read being one hop further down:
   * {@code getNumberOfEntriesInBaseDN0} to {@code ID2ChildrenCount.getTotalCount} to the cursor
   * the two tests above pin.
   */
  @Test
  public void testTheProgressCountOfAVerifyIsReadAsPartOfItsWalk() throws Exception
  {
    final DN baseDN = DN.valueOf("dc=example,dc=com");
    final VerifyConfig verifyConfig = mock(VerifyConfig.class);
    when(verifyConfig.getBaseDN()).thenReturn(baseDN);
    final EntryContainer entryContainer = mock(EntryContainer.class);
    final RootContainer rootContainer = mock(RootContainer.class);
    when(rootContainer.getEntryContainer(baseDN)).thenReturn(entryContainer);
    final ReadableTransaction txn = transactionWithEmptyCursors();

    // false: the entry iterator, which is what a verify-index runs unless it was given --clean
    new VerifyJob(rootContainer, verifyConfig).new ProgressTask(false, txn);

    verify(entryContainer).getNumberOfEntriesInBaseDN0(txn, true);
    verify(entryContainer, never()).getNumberOfEntriesInBaseDN0(txn);
  }
}
