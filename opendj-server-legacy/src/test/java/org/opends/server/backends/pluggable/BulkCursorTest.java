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
 * Each test below therefore pins one such call site, and pins it twice: that the bulk cursor is
 * what it asks for, and that it asks for no cursor of an operation at all.
 * <p>
 * The remaining call sites are held by other means. {@code ID2Entry.afterOpen()} has
 * {@code ID2EntryTest}, the override that gives the class its meaning has
 * {@code JDBCStatementBoundTestCase}, and {@code VerifyJob.iterateID2ChildrenCount()} cannot be
 * reverted at all: {@code ID2ChildrenCount} exposes no cursor but the bulk one, so the revert
 * would not compile. Two are left uncovered - the attribute index of {@code verify-index}, whose
 * {@code MatchingRuleIndex} is {@code final} and so cannot be handed to this suite, and the two
 * sites of {@code dbtest} - and both walk a tree only on the command line of an operator.
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

  /** A transaction whose every cursor is the empty one above, whichever kind is asked for. */
  private static ReadableTransaction transactionWithEmptyCursors()
  {
    final ReadableTransaction txn = mock(ReadableTransaction.class);
    when(txn.openBulkCursor(any(TreeName.class))).thenReturn(emptyCursor());
    when(txn.openCursor(any(TreeName.class))).thenReturn(emptyCursor());
    return txn;
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

  /** And it walks dn2id whole as well, rebuilding the children counts as it goes. */
  @Test
  public void testAVerifyWalksDn2idWithABulkCursor() throws Exception
  {
    final TreeName dn2idName = new TreeName("dc=example,dc=com", "dn2id");
    final ReadableTransaction txn = transactionWithEmptyCursors();
    final VerifyJob job = new VerifyJob(mock(RootContainer.class), mock(VerifyConfig.class));
    job.dn2id = new DN2ID(dn2idName, DN.valueOf("dc=example,dc=com"));

    job.iterateDN2ID(txn);

    verify(txn).openBulkCursor(dn2idName);
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /** A VLV index of a verify is walked whole too, key by key. */
  @Test
  public void testAVerifyWalksAVlvIndexWithABulkCursor() throws Exception
  {
    final ReadableTransaction txn = transactionWithEmptyCursors();
    final VerifyJob job = new VerifyJob(mock(RootContainer.class), mock(VerifyConfig.class));

    job.iterateVLVIndex(txn, mock(VLVIndex.class), true);

    verify(txn).openBulkCursor(any(TreeName.class));
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
    when(txn.openBulkCursor(any(TreeName.class))).thenReturn(emptyCursor());
    when(txn.openCursor(any(TreeName.class))).thenReturn(emptyCursor());

    new PersistentCompressedSchema(mock(ServerContext.class), mock(Storage.class), txn, AccessMode.READ_ONLY);

    verify(txn, times(2)).openBulkCursor(any(TreeName.class)); // the object classes and the attributes
    verify(txn, never()).openCursor(any(TreeName.class));
  }

  /**
   * An index walked whole - by {@code verify-index} or by {@code dbtest} - asks the transaction
   * for a bulk cursor, while {@code Index.openCursor()} stays what an operation evaluating a
   * filter takes.
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
   * The children counts are walked whole by {@code verify-index} through {@code ShardedCounter},
   * which is the one tree of this backend no client operation opens a cursor over at all.
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
}
