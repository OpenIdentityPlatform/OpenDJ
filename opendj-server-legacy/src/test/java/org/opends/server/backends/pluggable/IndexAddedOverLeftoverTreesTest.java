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
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opends.messages.BackendMessages.ERR_CONFIG_INDEX_ATTRIBUTE_ALREADY_INDEXED;
import static org.opends.messages.BackendMessages.NOTE_INDEX_ADD_REQUIRES_REBUILD;
import static org.opends.messages.BackendMessages.WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES;
import static org.opends.server.backends.pluggable.State.IndexFlag.TRUSTED;
import static org.opends.server.backends.pluggable.SuffixContainer.STATE_INDEX_NAME;
import static org.opends.server.util.CollectionUtils.newTreeSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.meta.BackendVLVIndexCfgDefn.Scope;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.BackendVLVIndexCfg;
import org.forgerock.opendj.server.config.server.ErrorLogPublisherCfg;
import org.forgerock.opendj.server.config.server.JEBackendCfg;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.forgerock.opendj.server.config.server.PluggableBackendCfg;
import org.mockito.ArgumentCaptor;
import org.opends.messages.Severity;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.jeb.JEStorage;
import org.opends.server.backends.pdb.PDBStorage;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.AddOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.loggers.ErrorLogPublisher;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.RestoreConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests what a pluggable backend does with the trees of an index which is declared again after the
 * configuration naming them is gone. The name of an index tree is a pure function of the base DN,
 * the attribute and the index id, and opening an index creates its trees only if they are not
 * there, so an index added for an attribute a previous index of the same name served reopens
 * exactly the trees that one left behind - with their content and with the TRUSTED flag their
 * {@code state} records still carry. Neither belongs to the index being added: what those trees
 * hold is whatever the backend was told before the configuration stopped naming them, and every
 * entry written in between is missing from it - see OpenDJ issue #990.
 * <p>
 * The trees are left behind here by declaring them, filling them and then reopening the backend
 * from a configuration which no longer names them, which is what an index deleted while the
 * backend is disabled - or through an offline {@code dsconfig} - leaves behind: the entry
 * container registers the listener which would have deleted the trees only while it is open
 * ({@code EntryContainer}, its constructor and {@code close()}). A deletion the storage gives up on
 * and a stop between the deletion of the trees and the commit of the configuration change leave the
 * same state; #962 covers the first of those.
 * <p>
 * Every case runs over PDB and over JE. The two lock differently: JE write-locks the name of a
 * deleted tree until the transaction commits, and opening a tree of that name from the same
 * transaction - which JE does under a transaction of its own - waits for it forever, so a drop and
 * an open sharing one write never return there. The time limit is what makes that a failure rather
 * than a hang; under Maven {@code TestListener} replaces it with {@code org.opends.test.timeout}.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class IndexAddedOverLeftoverTreesTest extends DirectoryServerTestCase
{
  private static final String BACKEND_ID = "IndexAddedOverLeftoverTreesTest";
  private static final DN BASE_DN = DN.valueOf("dc=b990,dc=com");
  private static final String VLV_INDEX_NAME = "b990vlv";
  private static final long HANG_TIMEOUT_MS = 60_000;

  /** The storages the cases run over, each in a directory of its own. */
  enum StorageKind
  {
    PDB
    {
      @Override
      PluggableBackendCfg newBackendCfg(String dbDirectory)
      {
        final PDBBackendCfg cfg = mockCfg(PDBBackendCfg.class);
        when(cfg.getDBDirectory()).thenReturn(dbDirectory);
        when(cfg.getDBDirectoryPermissions()).thenReturn("755");
        when(cfg.getDBCacheSize()).thenReturn(0L);
        when(cfg.getDBCachePercent()).thenReturn(20);
        return cfg;
      }

      @Override
      Storage newStorage(PluggableBackendCfg cfg, ServerContext serverContext) throws ConfigException
      {
        return new PDBStorage((PDBBackendCfg) cfg, serverContext);
      }
    },
    JE
    {
      @Override
      PluggableBackendCfg newBackendCfg(String dbDirectory)
      {
        final JEBackendCfg cfg = mockCfg(JEBackendCfg.class);
        when(cfg.getDBDirectory()).thenReturn(dbDirectory);
        when(cfg.getDBDirectoryPermissions()).thenReturn("755");
        when(cfg.getDBCacheSize()).thenReturn(0L);
        when(cfg.getDBCachePercent()).thenReturn(20);
        when(cfg.getDBNumCleanerThreads()).thenReturn(2);
        when(cfg.getDBNumLockTables()).thenReturn(63);
        return cfg;
      }

      @Override
      Storage newStorage(PluggableBackendCfg cfg, ServerContext serverContext) throws ConfigException
      {
        return new JEStorage((JEBackendCfg) cfg, serverContext);
      }
    };

    /** A configuration of this storage, with its files under the given directory. */
    abstract PluggableBackendCfg newBackendCfg(String dbDirectory);

    abstract Storage newStorage(PluggableBackendCfg cfg, ServerContext serverContext) throws ConfigException;
  }

  private ServerContext serverContext;
  private AttributeType cnType;

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    serverContext = TestCaseUtils.getServerContext();
    cnType = serverContext.getSchema().getAttributeType("cn");
  }

  @DataProvider(name = "storages")
  public Object[][] storages()
  {
    return new Object[][] { { StorageKind.PDB }, { StorageKind.JE } };
  }

  /**
   * A test which fails before it finalizes its backend leaves the base DN behind in the server wide
   * registry, where it would outlive the test and break the next one to use it.
   */
  @AfterMethod
  public void deregisterLeftoverBaseDN()
  {
    try
    {
      serverContext.getBackendConfigManager().deregisterBaseDN(BASE_DN);
    }
    catch (Exception alreadyGone)
    {
      // Which is what a test that finalized its backend leaves behind.
    }
  }

  /**
   * The index the add opens is not the index whose trees are still there: it has indexed none of
   * the entries, so it must be untrusted and the operator must be told to rebuild it, exactly as
   * for any other index added to a backend which already holds entries - and told, in the change
   * result and in the error log, that content was discarded to get there.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void anIndexAddedOverTheTreesLeftBehindIsNotTrusted(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    final ErrorLogPublisher<ErrorLogPublisherCfg> errorLog = registerErrorLogCapture();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      assertThat(backend.getRootContainer().getStorage().listTrees())
          .as("the trees no configuration names any more").containsAll(backend.leftoverIndexTrees);

      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ec.getAttributeIndex(cnType).isTrusted())
          .as("an index trusted over the content of trees it did not fill").isFalse();
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr.getMessages())).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal(),
          WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      assertThat(renderedOf(ccr.getMessages())).as("the index and the base DN the report names")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.get("cn", BASE_DN).toString());
      assertThat(warningOrdinalsLoggedTo(errorLog))
          .as("the discard reported in the error log, not only in the change result")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
    }
    finally
    {
      ErrorLogger.getInstance().removeLogPublisher(errorLog);
      backend.finalizeBackend();
    }
  }

  /**
   * The flag alone does not make the answers right again: {@code DefaultIndex.get} hands back what
   * a key holds whenever it holds anything, whatever the flag says, and only a key with nothing
   * behind it answers "undefined" and sends the search to the entries. So the content of the
   * adopted trees has to go, or every key they hold goes on answering with the entries of another
   * index and misses everything written since.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void anIndexAddedOverTheTreesLeftBehindDoesNotAnswerWithTheirContent(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Storage storage = backend.getRootContainer().getStorage();
      final Map<TreeName, ByteString> keysHeld = keysHeldBy(storage, backend.leftoverIndexTrees);
      assertThat(keysHeld).as("the keys the trees left behind hold").isNotEmpty();

      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(ordinalsOf(ccr.getMessages())).contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      final AttributeIndex index = ec.getAttributeIndex(cnType);
      for (final MatchingRuleIndex opened : index.getNameToIndexes().values())
      {
        final ByteString keyHeld = keysHeld.get(opened.getName());
        if (keyHeld != null)
        {
          assertThat(answers(storage, opened, keyHeld))
              .as("a key of " + opened.getName() + " answered out of the tree left behind").isFalse();
        }
      }
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * The trees can be gone while their {@code state} records are not: on JDBC the drop of a table is
   * DDL which commits of its own accord, while the {@code state.deleteRecord} of the same
   * {@code closeAndDelete} belongs to the transaction, so a rollback after the drop restores the
   * records over tables which are already gone. The index added next creates empty trees and reads
   * TRUSTED out of those records - an empty index which every search believes. Nothing is
   * discarded on this road, so nothing says it was: the record goes, and the rebuild is asked for.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void anIndexAddedOverAStateRecordWhoseTreesAreGoneIsNotTrusted(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Storage storage = backend.getRootContainer().getStorage();
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          for (final TreeName leftover : backend.leftoverIndexTrees)
          {
            txn.deleteTree(leftover);
          }
        }
      });
      for (final TreeName leftover : backend.leftoverIndexTrees)
      {
        assertThat(persistedFlags(backend, leftover))
            .as("the state record left over the tree which is gone").contains(TRUSTED);
      }

      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ec.getAttributeIndex(cnType).isTrusted())
          .as("an empty index trusted over a state record which outlived its trees").isFalse();
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr.getMessages())).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
      assertThat(ordinalsOf(ccr.getMessages())).as("content reported as discarded where no tree was")
          .doesNotContain(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * {@code dropLeftovers} folds each id's result with {@code dropped |= ...} rather than the last one
   * assigned; the two only disagree when the ids themselves do, which every other case avoids by giving
   * all of them a leftover or none. Deleting one of the two trees {@code leaveTreesBehind} left - the
   * cn index has EQUALITY and SUBSTRING - ahead of the add leaves the other one real, so the fold and
   * the assignment can differ on whether the change discarded anything.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void anIndexAddedOverOnlyOneOfTwoLeftoverTreesIsReportedDiscarded(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    try
    {
      final Storage storage = backend.getRootContainer().getStorage();
      final TreeName oneOfTwo = backend.leftoverIndexTrees.iterator().next();
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          txn.deleteTree(oneOfTwo);
        }
      });

      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr.getMessages()))
          .as("the other of the two ids still had a real tree to discard")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A VLV index adopts what it left behind in the same way, in its tree and in its counter. Both are
   * dropped, and so is the {@code state} record the next open of the backend would read the flag
   * out of.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void aVlvIndexAddedOverTheTreesLeftBehindIsNotTrusted(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    final ErrorLogPublisher<ErrorLogPublisherCfg> errorLog = registerErrorLogCapture();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Storage storage = backend.getRootContainer().getStorage();
      assertThat(storage.listTrees())
          .as("the VLV trees no configuration names any more")
          .contains(backend.leftoverVlvTree, backend.leftoverVlvCounterTree);
      assertThat(holdsAnything(storage, backend.leftoverVlvTree)).as("the VLV tree left behind").isTrue();
      assertThat(holdsAnything(storage, backend.leftoverVlvCounterTree)).as("the VLV counter left behind").isTrue();
      assertThat(persistedFlags(backend, backend.leftoverVlvTree)).contains(TRUSTED);

      final ConfigChangeResult ccr = vlvIndexAddListener(backend).applyConfigurationAdd(backend.vlvIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ec.getVLVIndex(VLV_INDEX_NAME).isTrusted())
          .as("a VLV index trusted over the content of trees it did not fill").isFalse();
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr.getMessages())).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal(),
          WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      assertThat(renderedOf(ccr.getMessages())).as("the index and the base DN the report names")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.get(VLV_INDEX_NAME, BASE_DN).toString());
      assertThat(warningOrdinalsLoggedTo(errorLog))
          .as("the discard reported in the error log, not only in the change result")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      assertThat(holdsAnything(storage, backend.leftoverVlvTree))
          .as("the VLV tree left behind still holds its content").isFalse();
      assertThat(holdsAnything(storage, backend.leftoverVlvCounterTree))
          .as("the VLV counter left behind still holds its count").isFalse();
      assertThat(persistedFlags(backend, backend.leftoverVlvTree))
          .as("the TRUSTED record outlived the add").doesNotContain(TRUSTED);
    }
    finally
    {
      ErrorLogger.getInstance().removeLogPublisher(errorLog);
      backend.finalizeBackend();
    }
  }

  /**
   * The state record can outlive the VLV tree and its counter the same way it outlives an
   * attribute index's tree
   * ({@link #anIndexAddedOverAStateRecordWhoseTreesAreGoneIsNotTrusted(StorageKind)}): deleting
   * them by hand exercises the two {@code treeExists} guards in
   * {@code VLVIndex.dropLeftovers} on their false arm, which the fixture of the case above never
   * reaches. On JE, {@code deleteTree} of a tree which is not there is silent, so a guard removed
   * or inverted would say content was discarded where none was.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void aVlvIndexAddedOverAStateRecordWhoseTreesAreGoneIsNotTrusted(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Storage storage = backend.getRootContainer().getStorage();
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          txn.deleteTree(backend.leftoverVlvTree);
          txn.deleteTree(backend.leftoverVlvCounterTree);
        }
      });
      assertThat(persistedFlags(backend, backend.leftoverVlvTree))
          .as("the state record left over the VLV tree which is gone").contains(TRUSTED);

      final ConfigChangeResult ccr = vlvIndexAddListener(backend).applyConfigurationAdd(backend.vlvIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ec.getVLVIndex(VLV_INDEX_NAME).isTrusted())
          .as("an empty VLV index trusted over a state record which outlived its trees").isFalse();
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr.getMessages())).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
      assertThat(ordinalsOf(ccr.getMessages())).as("content reported as discarded where no tree was")
          .doesNotContain(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * An index type declared again for an attribute which is still indexed goes through
   * {@code AttributeIndex.applyConfigurationChange} rather than through the add listener, and opens
   * its tree the same way. Only the tree of the id declared again goes: the tree of the id the index
   * was serving all along keeps what it holds.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void anIndexTypeAddedOverTheTreeLeftBehindIsNotTrusted(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind, indexCfg(newTreeSet(IndexType.EQUALITY)));
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Storage storage = backend.getRootContainer().getStorage();
      final AttributeIndex index = ec.getAttributeIndex(cnType);
      assertThat(index.isIndexed(IndexType.SUBSTRING))
          .as("the index type this configuration no longer names").isFalse();
      final Map<TreeName, ByteString> keysHeld = keysHeldBy(storage, backend.leftoverIndexTrees);
      // The index ids are the matching rules' - the one the index serves now is the equality one.
      final Map<String, MatchingRuleIndex> servedBefore = new HashMap<>(index.getNameToIndexes());
      assertThat(servedBefore).hasSize(1);
      final MatchingRuleIndex equality = servedBefore.values().iterator().next();
      final ByteString equalityKey = keysHeld.get(equality.getName());
      assertThat(equalityKey).as("a key the live equality tree holds").isNotNull();

      final ConfigChangeResult ccr =
          index.applyConfigurationChange(indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING)));

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(index.isIndexed(IndexType.SUBSTRING)).isTrue();
      assertThat(index.isTrusted())
          .as("an index type trusted over the content of the tree it did not fill").isFalse();
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr.getMessages())).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal(),
          WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      final Map<String, MatchingRuleIndex> added = new HashMap<>(index.getNameToIndexes());
      added.keySet().removeAll(servedBefore.keySet());
      assertThat(added).hasSize(1);
      final MatchingRuleIndex substring = added.values().iterator().next();
      // Named by its tree on this road, where the add listener names the index by its attribute.
      assertThat(renderedOf(ccr.getMessages())).as("the tree and the base DN the report names")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.get(substring.getName(), BASE_DN).toString());
      assertThat(keysHeld).as("the substring tree left behind held a key").containsKey(substring.getName());
      assertThat(answers(storage, substring, keysHeld.get(substring.getName())))
          .as("a substring key answered out of the tree left behind").isFalse();
      assertThat(answers(storage, equality, equalityKey))
          .as("the live equality tree went with the leftover").isTrue();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * What the drop write found is reported from a {@code finally}, so a throw from the write which
   * opens the index - after the drop has committed and the content is gone for good - does not
   * swallow it: the change fails, and the change result and the error log still say what was
   * discarded. Neither storage engine can be made to throw on its own, so the failure is injected
   * through {@link RefusingStorage}: the drop write goes through, the open write is refused before
   * it runs.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void aDiscardIsStillReportedWhenTheWriteWhichOpensTheIndexFails(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    final ErrorLogPublisher<ErrorLogPublisherCfg> errorLog = registerErrorLogCapture();
    try
    {
      final Storage storage = backend.getRootContainer().getStorage();
      final int writesBefore = backend.storage.writes();
      backend.storage.refuseWrite(2);

      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(backend.storage.writes()).as("the refused write was the second, and the last one made")
          .isEqualTo(writesBefore + 2);
      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr.getMessages())).as("the discard the failed change swallowed")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      assertThat(warningOrdinalsLoggedTo(errorLog)).contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      for (final TreeName leftover : backend.leftoverIndexTrees)
      {
        assertThat(storage.<Boolean> read(txn -> txn.treeExists(leftover)))
            .as("the tree the report says was discarded: " + leftover).isFalse();
      }
    }
    finally
    {
      ErrorLogger.getInstance().removeLogPublisher(errorLog);
      backend.finalizeBackend();
    }
  }

  /** The VLV road reports from a {@code finally} the same way. */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void aVlvDiscardIsStillReportedWhenTheWriteWhichBuildsTheIndexFails(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    final ErrorLogPublisher<ErrorLogPublisherCfg> errorLog = registerErrorLogCapture();
    try
    {
      final Storage storage = backend.getRootContainer().getStorage();
      final int writesBefore = backend.storage.writes();
      backend.storage.refuseWrite(2);

      final ConfigChangeResult ccr = vlvIndexAddListener(backend).applyConfigurationAdd(backend.vlvIndexCfg);

      assertThat(backend.storage.writes()).as("the refused write was the second, and the last one made")
          .isEqualTo(writesBefore + 2);
      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr.getMessages())).as("the discard the failed change swallowed")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      assertThat(warningOrdinalsLoggedTo(errorLog)).contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      assertThat(storage.<Boolean> read(txn -> txn.treeExists(backend.leftoverVlvTree)))
          .as("the VLV tree the report says was discarded").isFalse();
      assertThat(storage.<Boolean> read(txn -> txn.treeExists(backend.leftoverVlvCounterTree)))
          .as("the VLV counter the report says was discarded").isFalse();
    }
    finally
    {
      ErrorLogger.getInstance().removeLogPublisher(errorLog);
      backend.finalizeBackend();
    }
  }

  /**
   * And so does {@code AttributeIndex.applyConfigurationChange}, whose drop write is the first of
   * the writes a change which adds an index type makes, and whose open write is the second.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void anIndexTypeDiscardIsStillReportedWhenTheWriteWhichOpensItFails(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind, indexCfg(newTreeSet(IndexType.EQUALITY)));
    final ErrorLogPublisher<ErrorLogPublisherCfg> errorLog = registerErrorLogCapture();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Storage storage = backend.getRootContainer().getStorage();
      final AttributeIndex index = ec.getAttributeIndex(cnType);
      final Map<TreeName, ByteString> keysHeld = keysHeldBy(storage, backend.leftoverIndexTrees);
      final MatchingRuleIndex equality = index.getNameToIndexes().values().iterator().next();
      final Set<TreeName> substringTrees = new HashSet<>(backend.leftoverIndexTrees);
      substringTrees.remove(equality.getName());
      assertThat(substringTrees).hasSize(1);
      final int writesBefore = backend.storage.writes();
      backend.storage.refuseWrite(2);

      final ConfigChangeResult ccr =
          index.applyConfigurationChange(indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING)));

      assertThat(backend.storage.writes()).as("the refused write was the second, and the last one made")
          .isEqualTo(writesBefore + 2);
      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr.getMessages())).as("the discard the failed change swallowed")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      assertThat(warningOrdinalsLoggedTo(errorLog)).contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      final TreeName substringTree = substringTrees.iterator().next();
      assertThat(storage.<Boolean> read(txn -> txn.treeExists(substringTree)))
          .as("the tree the report says was discarded").isFalse();
      assertThat(index.isIndexed(IndexType.SUBSTRING)).as("an index type the failed change declared").isFalse();
      assertThat(answers(storage, equality, keysHeld.get(equality.getName())))
          .as("the live equality tree went with the leftover").isTrue();
    }
    finally
    {
      ErrorLogger.getInstance().removeLogPublisher(errorLog);
      backend.finalizeBackend();
    }
  }

  /**
   * The drop write can fail at its own commit, once it has run and answered - a disk which is full
   * when the engine writes its log. JE and PDB roll the drop back with it: the trees are still
   * there, and so is the TRUSTED record the next open of the backend reads. The report stands all
   * the same, and says more than happened. It is kept that way on purpose: the configuration entry
   * is already written when the listener runs, so what comes next is not another add but that
   * open, which adopts the trees - and the rebuild the report asks for is what puts it right. On
   * JDBC the same failure comes after a DROP which committed on its own, and the report is the only
   * trace of it; a report made to wait for the commit would be silent there, in the one case the
   * drop exists for. Pinned so that moving it behind the commit is red here rather than silent
   * there.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void aDiscardIsStillReportedWhenTheDropWriteFailsAtItsCommit(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    try
    {
      final Storage storage = backend.getRootContainer().getStorage();
      final int writesBefore = backend.storage.writes();
      backend.storage.failWriteAfterItRan(1);

      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(backend.storage.writes()).as("the failed write was the first, and the last one made")
          .isEqualTo(writesBefore + 1);
      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr.getMessages())).as("the report of a drop the engine rolled back")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      for (final TreeName leftover : backend.leftoverIndexTrees)
      {
        assertThat(holdsAnything(storage, leftover)).as("the drop of " + leftover + " rolled back with the write")
            .isTrue();
        assertThat(persistedFlags(backend, leftover)).as("the record of " + leftover).contains(TRUSTED);
      }
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * The VLV road keeps its report when the drop write fails at its commit, on the terms the case
   * above gives: the tree and its counter are back, and so is the TRUSTED record.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void aVlvDiscardIsStillReportedWhenTheDropWriteFailsAtItsCommit(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind);
    try
    {
      final Storage storage = backend.getRootContainer().getStorage();
      final int writesBefore = backend.storage.writes();
      backend.storage.failWriteAfterItRan(1);

      final ConfigChangeResult ccr = vlvIndexAddListener(backend).applyConfigurationAdd(backend.vlvIndexCfg);

      assertThat(backend.storage.writes()).as("the failed write was the first, and the last one made")
          .isEqualTo(writesBefore + 1);
      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr.getMessages())).as("the report of a drop the engine rolled back")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      assertThat(holdsAnything(storage, backend.leftoverVlvTree)).as("the VLV tree rolled back with the write")
          .isTrue();
      assertThat(holdsAnything(storage, backend.leftoverVlvCounterTree))
          .as("the VLV counter rolled back with the write").isTrue();
      assertThat(persistedFlags(backend, backend.leftoverVlvTree)).as("the record of the VLV tree").contains(TRUSTED);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * And so does {@code AttributeIndex.applyConfigurationChange}: the tree of the index type
   * declared again is back with its TRUSTED record, the change has not been applied, and the
   * report stands.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void anIndexTypeDiscardIsStillReportedWhenTheDropWriteFailsAtItsCommit(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(kind, indexCfg(newTreeSet(IndexType.EQUALITY)));
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Storage storage = backend.getRootContainer().getStorage();
      final AttributeIndex index = ec.getAttributeIndex(cnType);
      final Map<TreeName, ByteString> keysHeld = keysHeldBy(storage, backend.leftoverIndexTrees);
      final MatchingRuleIndex equality = index.getNameToIndexes().values().iterator().next();
      final Set<TreeName> substringTrees = new HashSet<>(backend.leftoverIndexTrees);
      substringTrees.remove(equality.getName());
      assertThat(substringTrees).hasSize(1);
      final TreeName substringTree = substringTrees.iterator().next();
      final int writesBefore = backend.storage.writes();
      backend.storage.failWriteAfterItRan(1);

      final ConfigChangeResult ccr =
          index.applyConfigurationChange(indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING)));

      assertThat(backend.storage.writes()).as("the failed write was the first, and the last one made")
          .isEqualTo(writesBefore + 1);
      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr.getMessages())).as("the report of a drop the engine rolled back")
          .contains(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
      assertThat(holdsAnything(storage, substringTree)).as("the substring tree rolled back with the write").isTrue();
      assertThat(persistedFlags(backend, substringTree)).as("the record of the substring tree").contains(TRUSTED);
      assertThat(index.isIndexed(IndexType.SUBSTRING)).as("an index type the failed change declared").isFalse();
      assertThat(answers(storage, equality, keysHeld.get(equality.getName())))
          .as("the live equality tree went with the leftover").isTrue();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * An index added to a backend which holds no entry has nothing to index and nothing to adopt, so
   * it stays trusted and asks for nothing. Pins the upgrade {@code DefaultIndex.afterOpen} makes.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void anIndexAddedToAnEmptyBackendIsTrustedAndAsksForNothing(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = openBackend(kind, true, null, null);
    try
    {
      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(backend.getRootContainer().getEntryContainer(BASE_DN).getAttributeIndex(cnType).isTrusted())
          .as("an index of a backend with nothing to index").isTrue();
      assertThat(ccr.adminActionRequired()).isFalse();
      assertThat(ccr.getMessages()).isEmpty();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * An index added to a backend which holds entries, with nothing left behind for it to adopt, is
   * untrusted over trees it created empty and asks to be rebuilt. Pins what the fix has to leave
   * alone: this is the outcome an index added over leftover trees has to reach as well, and nothing
   * was discarded to reach it here.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void anIndexAddedToANonEmptyBackendAsksForARebuild(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend = openBackend(kind, true, null, null);
    try
    {
      addEntry(backend, "dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: b990");

      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(backend.getRootContainer().getEntryContainer(BASE_DN).getAttributeIndex(cnType).isTrusted())
          .as("an index which has indexed none of the entries").isFalse();
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr.getMessages())).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
      assertThat(ordinalsOf(ccr.getMessages())).as("content reported as discarded where nothing was left behind")
          .doesNotContain(WARN_INDEX_ADD_DISCARDED_LEFTOVER_TREES.ordinal());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * The entry container keys its indexes by the attribute type, which every name of the attribute
   * and its OID resolve to, while a configuration entry is named by whichever of them was typed. An
   * index declared for cn as commonName or as 2.5.4.3 is therefore a second index for an attribute
   * which is already indexed, naming the very trees the live index serves - and those must not go
   * the way of trees left behind. The add is refused instead, before the configuration is written
   * and again where it would have dropped them.
   */
  @Test(dataProvider = "storages", timeOut = HANG_TIMEOUT_MS)
  public void aSecondIndexForAnAttributeAlreadyIndexedIsRefused(StorageKind kind) throws Exception
  {
    final LeftoverBackend backend =
        openBackend(kind, true, indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING)), null);
    try
    {
      addEntry(backend, "dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: b990");
      addEntry(backend, "dn: cn=live," + BASE_DN, "objectClass: top", "objectClass: organizationalRole", "cn: live");
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Storage storage = backend.getRootContainer().getStorage();
      final AttributeIndex live = ec.getAttributeIndex(cnType);
      assertThat(live.isTrusted()).isTrue();
      final Map<TreeName, ByteString> keysHeld = keysHeldBy(storage, treesOf(live));
      assertThat(keysHeld).as("the keys the live index holds").isNotEmpty();

      // What was typed names the configuration entry; the attribute type it resolves to is the one already indexed.
      final DN typedAs = DN.valueOf("ds-cfg-attribute=commonName,cn=Index," + backend.configuredWith.dn());
      final BackendIndexCfg declaredAgain = indexCfg(newTreeSet(IndexType.EQUALITY));
      when(declaredAgain.dn()).thenReturn(typedAs);
      final ConfigurationAddListener<BackendIndexCfg> listener = indexAddListener(backend);

      // An index for an attribute which is not indexed yet is still accepted - pins the refusing arm above
      // against a mutant which refuses every add.
      final BackendIndexCfg fresh = indexCfg(newTreeSet(IndexType.EQUALITY));
      when(fresh.getAttribute()).thenReturn(serverContext.getSchema().getAttributeType("sn"));
      final List<LocalizableMessage> reasons = new ArrayList<>();
      assertThat(listener.isConfigurationAddAcceptable(fresh, reasons)).isTrue();
      assertThat(reasons).isEmpty();

      assertThat(listener.isConfigurationAddAcceptable(declaredAgain, reasons)).isFalse();
      assertThat(ordinalsOf(reasons)).contains(ERR_CONFIG_INDEX_ATTRIBUTE_ALREADY_INDEXED.ordinal());

      final ConfigChangeResult ccr = listener.applyConfigurationAdd(declaredAgain);
      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr.getMessages())).contains(ERR_CONFIG_INDEX_ATTRIBUTE_ALREADY_INDEXED.ordinal());

      assertThat(ec.getAttributeIndex(cnType)).as("the live index was replaced").isSameAs(live);
      assertThat(live.isTrusted()).isTrue();
      for (final MatchingRuleIndex opened : live.getNameToIndexes().values())
      {
        assertThat(answers(storage, opened, keysHeld.get(opened.getName())))
            .as("a key of " + opened.getName() + " the live index held before").isTrue();
      }
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  private LeftoverBackend leaveTreesBehind(StorageKind kind) throws Exception
  {
    return leaveTreesBehind(kind, null);
  }

  /**
   * Opens a backend whose configuration names a cn index and a VLV index, fills them with an entry,
   * and reopens it from a configuration which names {@code reopenedWith} instead - null for a
   * configuration which names no index at all. The trees of everything it no longer names are left
   * behind, and the entry added afterwards is in none of them.
   */
  private LeftoverBackend leaveTreesBehind(StorageKind kind, BackendIndexCfg reopenedWith) throws Exception
  {
    final LeftoverBackend indexed =
        openBackend(kind, true, indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING)), vlvIndexCfg());
    final Set<TreeName> indexTrees;
    final TreeName vlvTree;
    final TreeName vlvCounterTree;
    try
    {
      addEntry(indexed, "dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: b990");
      addEntry(indexed, "dn: cn=stale," + BASE_DN, "objectClass: top", "objectClass: organizationalRole",
          "cn: stale");
      final EntryContainer ec = indexed.getRootContainer().getEntryContainer(BASE_DN);
      indexTrees = treesOf(ec.getAttributeIndex(cnType));
      vlvTree = new TreeName(ec.getTreePrefix(), "vlv." + VLV_INDEX_NAME);
      vlvCounterTree = new TreeName(ec.getTreePrefix(), "counter.vlv." + VLV_INDEX_NAME);
    }
    finally
    {
      indexed.finalizeBackend();
    }

    final LeftoverBackend reopened = openBackend(kind, false, reopenedWith, null);
    try
    {
      addEntry(reopened, "dn: cn=fresh," + BASE_DN, "objectClass: top", "objectClass: organizationalRole",
          "cn: fresh");
    }
    catch (Exception e)
    {
      reopened.finalizeBackend();
      throw e;
    }
    reopened.leftoverIndexTrees = indexTrees;
    reopened.leftoverVlvTree = vlvTree;
    reopened.leftoverVlvCounterTree = vlvCounterTree;
    return reopened;
  }

  /** The first key each of the given trees holds, for the trees which hold any. */
  private static Map<TreeName, ByteString> keysHeldBy(Storage storage, Set<TreeName> trees) throws Exception
  {
    final Map<TreeName, ByteString> keys = new HashMap<>();
    for (final TreeName tree : trees)
    {
      final ByteString key = storage.read(txn -> {
        try (Cursor<ByteString, ByteString> cursor = txn.openCursor(tree))
        {
          return cursor.next() ? cursor.getKey() : null;
        }
      });
      if (key != null)
      {
        keys.put(tree, key);
      }
    }
    return keys;
  }

  private static boolean holdsAnything(Storage storage, TreeName tree) throws Exception
  {
    return storage.read(txn -> {
      try (Cursor<ByteString, ByteString> cursor = txn.openCursor(tree))
      {
        return cursor.next();
      }
    });
  }

  /**
   * Whether the index answers the key with a non-empty entry ID set, as a search reading it would get one.
   * {@code DefaultIndex.get} answers {@code newDefinedSet()} for a missing key while the index is trusted, so
   * {@code isDefined()} alone would pin the trusted flag rather than the content a leftover tree still holds.
   */
  private static boolean answers(Storage storage, MatchingRuleIndex index, ByteString key) throws Exception
  {
    return storage.read(txn -> {
      final EntryIDSet ids = index.get(txn, key);
      return ids.isDefined() && ids.size() > 0;
    });
  }

  /** Reads back the flags an index tree carries, as they are stored. */
  private EnumSet<State.IndexFlag> persistedFlags(LeftoverBackend backend, TreeName index) throws Exception
  {
    final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
    final State state = new State(new TreeName(ec.getTreePrefix(), STATE_INDEX_NAME));
    return backend.getRootContainer().getStorage().read(txn -> state.getIndexFlags(txn, index));
  }

  /** The messages a change carries, as the operator reads them: identity and arguments both. */
  private static List<String> renderedOf(Collection<LocalizableMessage> messages)
  {
    final List<String> rendered = new ArrayList<>();
    for (final LocalizableMessage message : messages)
    {
      rendered.add(message.toString());
    }
    return rendered;
  }

  /** The messages a change carries, by identity rather than by their formatted text. */
  private static Set<Integer> ordinalsOf(Collection<LocalizableMessage> messages)
  {
    final Set<Integer> ordinals = new HashSet<>();
    for (final LocalizableMessage message : messages)
    {
      ordinals.add(message.ordinal());
    }
    return ordinals;
  }

  /**
   * Registers a mock {@code ErrorLogPublisher}, enabled for every category and severity, so that
   * {@link #warningOrdinalsLoggedTo} can read back what {@code AttributeIndex.reportDiscardedLeftovers}
   * put in the error log - the half of it the change result alone does not exercise. The caller removes
   * it once done, from a {@code finally}: it would otherwise go on capturing messages other tests log.
   */
  @SuppressWarnings("unchecked")
  private static ErrorLogPublisher<ErrorLogPublisherCfg> registerErrorLogCapture()
  {
    final ErrorLogPublisher<ErrorLogPublisherCfg> errorLog = mock(ErrorLogPublisher.class);
    when(errorLog.isEnabledFor(any(), any())).thenReturn(true);
    ErrorLogger.getInstance().addLogPublisher(errorLog);
    return errorLog;
  }

  /** The ordinals of the messages logged at {@link Severity#WARNING} through the given capture. */
  private static Set<Integer> warningOrdinalsLoggedTo(ErrorLogPublisher<ErrorLogPublisherCfg> errorLog)
  {
    final ArgumentCaptor<LocalizableMessage> logged = ArgumentCaptor.forClass(LocalizableMessage.class);
    verify(errorLog, atLeastOnce()).log(any(), eq(Severity.WARNING), logged.capture(), any());
    return ordinalsOf(logged.getAllValues());
  }

  private static Set<TreeName> treesOf(AttributeIndex index)
  {
    final Set<TreeName> names = new HashSet<>();
    for (final MatchingRuleIndex matchingRuleIndex : index.getNameToIndexes().values())
    {
      names.add(matchingRuleIndex.getName());
    }
    return names;
  }

  private static void addEntry(LeftoverBackend backend, String... ldifLines) throws Exception
  {
    final Entry entry = TestCaseUtils.makeEntry(ldifLines);
    backend.addEntry(entry, mock(AddOperation.class));
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static ConfigurationAddListener<BackendIndexCfg> indexAddListener(LeftoverBackend backend)
      throws ConfigException
  {
    final ArgumentCaptor<ConfigurationAddListener> captor = ArgumentCaptor.forClass(ConfigurationAddListener.class);
    verify(backend.configuredWith).addBackendIndexAddListener(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static ConfigurationAddListener<BackendVLVIndexCfg> vlvIndexAddListener(LeftoverBackend backend)
      throws ConfigException
  {
    final ArgumentCaptor<ConfigurationAddListener> captor = ArgumentCaptor.forClass(ConfigurationAddListener.class);
    verify(backend.configuredWith).addBackendVLVIndexAddListener(captor.capture());
    return captor.getValue();
  }

  /**
   * Opens a backend whose configuration names the given index and VLV index - null for one it does
   * not name, whose trees are then left to whatever is already in the storage.
   */
  private LeftoverBackend openBackend(StorageKind kind, boolean pristine, BackendIndexCfg cnIndexCfg,
      BackendVLVIndexCfg vlvIndexCfg) throws Exception
  {
    final LeftoverBackend backend = new LeftoverBackend(kind);
    backend.setBackendID(BACKEND_ID);
    backend.cnIndexCfg = cnIndexCfg != null ? cnIndexCfg
        : indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING));
    backend.vlvIndexCfg = vlvIndexCfg != null ? vlvIndexCfg : vlvIndexCfg();
    backend.configuredWith = backendCfg(kind, cnIndexCfg, vlvIndexCfg);
    backend.configureBackend(backend.configuredWith, serverContext);
    if (pristine)
    {
      // Start from a pristine on-disk state, so that a previous run cannot mask what this one leaves.
      backend.storage.removeStorageFiles();
    }
    try
    {
      backend.openBackend();
    }
    catch (Exception e)
    {
      // openBackend() opens the root container before it registers the base DNs and the monitor, so a failure in
      // any of those leaves the volume open and every following test failing here too. finalizeBackend() is no
      // use for it: closeBackend() deregisters the monitor before it closes the root container, and NPEs on the
      // monitor which was never registered.
      try
      {
        final RootContainer root = backend.getRootContainer();
        if (root != null)
        {
          root.close();
        }
        else
        {
          backend.storage.close();
        }
      }
      catch (Exception cleanupFailure)
      {
        e.addSuppressed(cleanupFailure);
      }
      throw e;
    }
    return backend;
  }

  private PluggableBackendCfg backendCfg(StorageKind kind, BackendIndexCfg cnIndexCfg, BackendVLVIndexCfg vlvIndexCfg)
      throws ConfigException
  {
    final PluggableBackendCfg cfg = kind.newBackendCfg(BACKEND_ID + "." + kind);
    when(cfg.dn()).thenReturn(DN.valueOf("ds-cfg-backend-id=" + BACKEND_ID + ",cn=Backends,cn=config"));
    when(cfg.getBackendId()).thenReturn(BACKEND_ID);
    when(cfg.getBaseDN()).thenReturn(newTreeSet(BASE_DN));
    when(cfg.listBackendIndexes()).thenReturn(cnIndexCfg != null ? new String[] { "cn" } : new String[0]);
    when(cfg.getBackendIndex("cn")).thenReturn(cnIndexCfg);
    when(cfg.listBackendVLVIndexes())
        .thenReturn(vlvIndexCfg != null ? new String[] { VLV_INDEX_NAME } : new String[0]);
    when(cfg.getBackendVLVIndex(VLV_INDEX_NAME)).thenReturn(vlvIndexCfg);
    return cfg;
  }

  private BackendIndexCfg indexCfg(SortedSet<IndexType> indexTypes)
  {
    final BackendIndexCfg cfg = mock(BackendIndexCfg.class);
    when(cfg.dn()).thenReturn(DN.valueOf("ds-cfg-attribute=cn,cn=Index,ds-cfg-backend-id=" + BACKEND_ID
        + ",cn=Backends,cn=config"));
    when(cfg.getIndexType()).thenReturn(indexTypes);
    when(cfg.getAttribute()).thenReturn(cnType);
    when(cfg.getIndexEntryLimit()).thenReturn(4000);
    when(cfg.getSubstringLength()).thenReturn(6);
    return cfg;
  }

  private BackendVLVIndexCfg vlvIndexCfg()
  {
    final BackendVLVIndexCfg cfg = mock(BackendVLVIndexCfg.class);
    when(cfg.getName()).thenReturn(VLV_INDEX_NAME);
    when(cfg.getBaseDN()).thenReturn(BASE_DN);
    when(cfg.getScope()).thenReturn(Scope.WHOLE_SUBTREE);
    when(cfg.getFilter()).thenReturn("(objectClass=*)");
    when(cfg.getSortOrder()).thenReturn("+cn");
    return cfg;
  }

  /** A backend over the storage of the given kind, which keeps hold of the configuration its entry container registered with. */
  private static final class LeftoverBackend extends BackendImpl<PluggableBackendCfg>
  {
    private final StorageKind kind;
    private RefusingStorage storage;
    /** The configuration the entry container registers its listeners with. */
    private PluggableBackendCfg configuredWith;
    private BackendIndexCfg cnIndexCfg;
    private BackendVLVIndexCfg vlvIndexCfg;
    /** The trees of the indexes an earlier configuration named, which nothing names now. */
    private Set<TreeName> leftoverIndexTrees;
    private TreeName leftoverVlvTree;
    private TreeName leftoverVlvCounterTree;

    private LeftoverBackend(StorageKind kind)
    {
      this.kind = kind;
    }

    @Override
    protected Storage configureStorage(PluggableBackendCfg cfg, ServerContext serverContext) throws ConfigException
    {
      storage = new RefusingStorage(kind.newStorage(cfg, serverContext));
      return storage;
    }
  }

  /** A failure no storage engine replays, unlike a conflict. */
  private static final class RefusedWrite extends Exception
  {
    private static final long serialVersionUID = 1L;

    RefusedWrite()
    {
      super("write refused by the test");
    }
  }

  /**
   * Delegates to the storage of the engine, and refuses the one write a case arms: neither engine
   * can be made to fail a write on its own, and both are final. The refusal comes at one of two
   * points - before the operation runs, which is what a write the engine cannot begin looks like
   * to the caller, or once the operation has run and before it is committed, which is what a
   * failure at commit looks like: the engine rolls the operation back, and whatever the operation
   * noted on the way stands. Unarmed, it is the engine's storage.
   */
  private static final class RefusingStorage implements Storage
  {
    private final Storage delegate;
    /** Which write, counted over the life of this storage, is armed; zero for none. */
    private int armedWrite;
    private boolean afterItRan;
    private int writes;

    RefusingStorage(Storage delegate)
    {
      this.delegate = delegate;
    }

    /** Refuses the {@code nth} write asked for from now on, the next one being the first, before it runs. */
    void refuseWrite(int nth)
    {
      armedWrite = writes + nth;
      afterItRan = false;
    }

    /** Fails the {@code nth} write asked for from now on once its operation has run, before it is committed. */
    void failWriteAfterItRan(int nth)
    {
      armedWrite = writes + nth;
      afterItRan = true;
    }

    /** How many write operations this storage was asked for, refused or not. */
    int writes()
    {
      return writes;
    }

    @Override
    public void write(final WriteOperation operation) throws Exception
    {
      writes++;
      if (writes != armedWrite)
      {
        delegate.write(operation);
        return;
      }
      armedWrite = 0;
      if (!afterItRan)
      {
        throw new RefusedWrite();
      }
      delegate.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          operation.run(txn);
          throw new RefusedWrite();
        }
      });
    }

    @Override
    public Importer startImport() throws ConfigException
    {
      return delegate.startImport();
    }

    @Override
    public void open(AccessMode accessMode) throws Exception
    {
      delegate.open(accessMode);
    }

    @Override
    public <T> T read(ReadOperation<T> readOperation) throws Exception
    {
      return delegate.read(readOperation);
    }

    @Override
    public void removeStorageFiles()
    {
      delegate.removeStorageFiles();
    }

    @Override
    public StorageStatus getStorageStatus()
    {
      return delegate.getStorageStatus();
    }

    @Override
    public boolean supportsBackupAndRestore()
    {
      return delegate.supportsBackupAndRestore();
    }

    @Override
    public void createBackup(BackupConfig backupConfig) throws DirectoryException
    {
      delegate.createBackup(backupConfig);
    }

    @Override
    public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
    {
      delegate.removeBackup(backupDirectory, backupID);
    }

    @Override
    public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
    {
      delegate.restoreBackup(restoreConfig);
    }

    @Override
    public Set<TreeName> listTrees()
    {
      return delegate.listTrees();
    }

    @Override
    public void close()
    {
      delegate.close();
    }
  }
}
