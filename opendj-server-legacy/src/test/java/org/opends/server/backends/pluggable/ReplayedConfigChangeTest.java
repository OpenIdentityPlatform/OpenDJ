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
import static org.opends.messages.BackendMessages.ERR_BACKEND_BASEDN_NO_LONGER_HELD;
import static org.opends.messages.BackendMessages.ERR_BACKEND_CANNOT_LIST_TREES_AFTER_BASEDN_CHANGE;
import static org.opends.messages.BackendMessages.ERR_BACKEND_CANNOT_REGISTER_BASEDN;
import static org.opends.messages.BackendMessages.NOTE_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD;
import static org.opends.messages.BackendMessages.NOTE_INDEX_ADD_REQUIRES_REBUILD;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opends.server.backends.pluggable.State.IndexFlag.TRUSTED;
import static org.opends.server.backends.pluggable.SuffixContainer.STATE_INDEX_NAME;
import static org.opends.server.util.CollectionUtils.newTreeSet;
import static org.forgerock.util.Utils.closeSilently;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.meta.BackendVLVIndexCfgDefn.Scope;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.BackendVLVIndexCfg;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pdb.PDBStorage;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.State.IndexFlag;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.AddOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RestoreConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.persistit.exception.RollbackException;

/**
 * Tests that {@link BackendImpl#applyConfigurationChange} survives a replay of its
 * {@link WriteOperation}. {@link Storage#write(WriteOperation)} may replay the operation after a
 * transaction conflict, so every side effect it performs must either be transactional or be
 * idempotent - see OpenDJ issue #907.
 * <p>
 * The conflict is raised from inside the operation as the {@link RollbackException} PersistIt
 * itself raises, so that the replay is driven by {@code PDBStorage.write}'s own retry loop rather
 * than by a second call to it. That loop keeps one storage implementation - and with it its cache
 * of PersistIt exchanges - across every attempt, which a second call would not.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class ReplayedConfigChangeTest extends DirectoryServerTestCase
{
  private static final String BACKEND_ID = "ReplayedConfigChangeTest";
  private static final DN KEPT = DN.valueOf("dc=b907a,dc=com");
  private static final DN REMOVED = DN.valueOf("dc=b907b,dc=com");
  private static final DN ADDED = DN.valueOf("dc=b907c,dc=com");
  /** Hierarchically related to {@link #KEPT}, which one backend is not allowed to serve as well. */
  private static final DN UNREGISTRABLE = DN.valueOf("dc=b907d,dc=b907a,dc=com");
  /** Held in lower case, which is how an entry container keys the vlvIndexes it holds. */
  private static final String VLV_INDEX_NAME = "b907vlv";

  private ServerContext serverContext;
  private AttributeType cnType;

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    serverContext = TestCaseUtils.getServerContext();
    cnType = serverContext.getSchema().getAttributeType("cn");
  }

  /**
   * These tests are designed to fail, and a failing one can leave a base DN behind in the server
   * wide registry, where it would outlive the test and break the next one to use that DN.
   */
  @AfterMethod
  public void deregisterLeftoverBaseDNs()
  {
    for (DN baseDN : new DN[] { KEPT, REMOVED, ADDED, UNREGISTRABLE })
    {
      try
      {
        serverContext.getBackendConfigManager().deregisterBaseDN(baseDN);
      }
      catch (Exception alreadyGone)
      {
        // Which is what the test should have left behind.
      }
    }
  }

  /**
   * A base DN removal whose transaction conflicts before it touches the storage must be replayed
   * without reporting a failure against the base DN it has already deregistered.
   */
  @Test
  public void removalIsReplayableWhenTheTransactionConflictsBeforeAnyStorageAccess() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT, REMOVED));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      assertThat(rootContainer.getBaseDNs()).contains(REMOVED);

      backend.storage.conflictAtFirstStorageAccess(1);
      final ConfigChangeResult ccr = backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT)));

      assertThat(backend.storage.attempts()).isEqualTo(2);
      assertThat(ccr.getMessages()).isEmpty();
      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(rootContainer.getBaseDNs()).doesNotContain(REMOVED);
      assertThat(backend.getBaseDNs()).doesNotContain(REMOVED);
      assertThat(serverContext.getBackendConfigManager().getLocalBackendWithBaseDN(REMOVED)).isNull();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A base DN addition whose transaction conflicts at commit time must be replayed, so that what
   * the entry container it opens writes ends up committed rather than discarded by the rollback.
   */
  @Test
  public void additionIsReplayableWhenTheTransactionConflictsAtCommitTime() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      assertThat(rootContainer.getBaseDNs()).doesNotContain(ADDED);

      backend.storage.conflictAtCommit(1);
      final ConfigChangeResult ccr = backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT, ADDED)));

      assertThat(backend.storage.attempts()).isEqualTo(2);
      assertThat(ccr.getMessages()).isEmpty();
      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(rootContainer.getBaseDNs()).contains(ADDED);
      assertThat(backend.getBaseDNs()).contains(ADDED);
      assertThat(serverContext.getBackendConfigManager().getLocalBackendWithBaseDN(ADDED)).isSameAs(backend);
      // Everything the newly opened entry container wrote belongs to the rolled back transaction,
      // so the storage has to be asked, not the entry container which remembers writing it.
      final EntryContainer ec = rootContainer.getEntryContainer(ADDED);
      final TreeName cnIndex = ec.getAttributeIndex(cnType).getNameToIndexes().values().iterator().next().getName();
      assertThat(rootContainer.getStorage().listTrees()).contains(cnIndex);
      assertThat(persistedFlags(rootContainer, ec, cnIndex)).contains(TRUSTED);
      // The entry container the rolled back attempt opened registered five configuration listeners,
      // which only its close() takes back, so the replay has to give it up before opening another.
      verify(backend.configuredWith, times(1)).removePluggableChangeListener(any());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * The trees of a removed base DN are deleted by the operation itself, so a replay deletes trees a
   * rolled back attempt had already deleted. This is the case which reaches the storage, and it
   * removes and adds a base DN at once because that is what an operator editing the configuration
   * does.
   */
  @Test
  public void aRemovalAndAnAdditionInOneChangeSurviveRepeatedReplay() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT, REMOVED));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      final Set<TreeName> removedTrees = treesOf(rootContainer.getEntryContainer(REMOVED));
      assertThat(rootContainer.getStorage().listTrees()).containsAll(removedTrees);

      // More than one conflict, because the contract is that the operation is replayed until it
      // succeeds rather than that it survives a single replay.
      backend.storage.conflictAtCommit(2);
      final ConfigChangeResult ccr = backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT, ADDED)));

      assertThat(backend.storage.attempts()).isEqualTo(3);
      assertThat(ccr.getMessages()).isEmpty();
      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(rootContainer.getBaseDNs()).contains(KEPT, ADDED).doesNotContain(REMOVED);
      assertThat(backend.getBaseDNs()).contains(KEPT, ADDED).doesNotContain(REMOVED);

      final Set<TreeName> storedTrees = rootContainer.getStorage().listTrees();
      assertThat(storedTrees).doesNotContainAnyElementsOf(removedTrees);
      assertThat(storedTrees).containsAll(treesOf(rootContainer.getEntryContainer(ADDED)));
      assertThat(storedTrees).containsAll(treesOf(rootContainer.getEntryContainer(KEPT)));
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A failure the storage engine does not replay must leave the backend as it was and say which
   * base DNs the change was about, since the failure itself never names them.
   */
  @Test
  public void aFailureWhichIsNotReplayedAppliesNothingAndNamesTheBaseDNs() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT, REMOVED));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      final Set<TreeName> removedTrees = treesOf(rootContainer.getEntryContainer(REMOVED));

      backend.storage.failWithoutReplay();
      final ConfigChangeResult ccr = backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT, ADDED)));

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.getMessages().toString()).contains(REMOVED.toString()).contains(ADDED.toString());

      // Nothing was registered, nothing was deregistered, and the rollback put the trees back.
      assertThat(rootContainer.getBaseDNs()).contains(REMOVED).doesNotContain(ADDED);
      assertThat(backend.getBaseDNs()).contains(REMOVED).doesNotContain(ADDED);
      assertThat(serverContext.getBackendConfigManager().getLocalBackendWithBaseDN(REMOVED)).isSameAs(backend);
      assertThat(serverContext.getBackendConfigManager().getLocalBackendWithBaseDN(ADDED)).isNull();
      assertThat(rootContainer.getStorage().listTrees()).containsAll(removedTrees);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A failure which the storage engine neither replays nor rolls back - the DDL of mysql and oracle
   * commits of its own accord, and cassandra has no transaction at all - leaves the trees of a
   * removed base DN gone. That base DN has to stop being reachable, or every operation against it
   * meets a storage error rather than the "no such entry" its removal was meant to leave.
   */
  @Test
  public void aFailureWhichIsNotRolledBackGivesUpTheBaseDNsWhoseTreesAreGone() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT, REMOVED));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      final Set<TreeName> removedTrees = treesOf(rootContainer.getEntryContainer(REMOVED));

      backend.storage.failAfterCommit();
      final ConfigChangeResult ccr = backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT, ADDED)));

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ccr.getMessages().toString()).contains(REMOVED.toString()).contains(ADDED.toString());

      // The trees are gone, so the base DN is given up rather than left routed at them.
      assertThat(rootContainer.getStorage().listTrees()).doesNotContainAnyElementsOf(removedTrees);
      assertThat(rootContainer.getBaseDNs()).doesNotContain(REMOVED);
      assertThat(backend.getBaseDNs()).doesNotContain(REMOVED);
      assertThat(serverContext.getBackendConfigManager().getLocalBackendWithBaseDN(REMOVED)).isNull();

      // The added base DN is not registered, since the change it belongs to failed.
      assertThat(rootContainer.getBaseDNs()).doesNotContain(ADDED);
      assertThat(backend.getBaseDNs()).doesNotContain(ADDED);
      assertThat(serverContext.getBackendConfigManager().getLocalBackendWithBaseDN(ADDED)).isNull();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * The same failure leaves the trees it created for a base DN which is not being added after all
   * exactly where they are. The configuration which names that base DN was stored before this
   * listener was called - {@code ConfigurationHandler.replaceEntry} writes the entry, and only then
   * notifies - and the failure does not take it back, so the next open of this backend opens that
   * base DN again from it, adopting the trees which survived and creating the ones which did not.
   * Deleting them here would take away the trees of a base DN the stored configuration still asks
   * this backend to serve, and would buy nothing: that open re-creates them empty.
   */
  @Test
  public void aFailureWhichIsNotRolledBackLeavesTheTreesItCreated() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT, REMOVED));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      final Set<TreeName> storedBefore = new HashSet<>(rootContainer.getStorage().listTrees());

      backend.storage.failAfterCommit();
      final ConfigChangeResult ccr = backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT, ADDED)));

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(rootContainer.getBaseDNs()).doesNotContain(ADDED);
      // Named by the failure, since nothing in the running server names them any more.
      assertThat(ccr.getMessages().toString()).contains(ADDED.toString());

      final Set<TreeName> left = new HashSet<>(rootContainer.getStorage().listTrees());
      left.removeAll(storedBefore);
      assertThat(left).as("the trees created for the base DN the stored configuration still names")
          .isNotEmpty();
      for (TreeName tree : left)
      {
        assertThat(tree.getBaseDN()).isEqualTo(ADDED.toNormalizedUrlSafeString());
      }
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * Whether anything survived a failure is read from the trees the storage still holds, so a backend
   * which cannot be asked for them reconciles nothing at all. The operator has to be told that,
   * since it is the case where the failure alone says least about what the backend is left serving.
   */
  @Test
  public void aFailureWhoseSurvivingTreesCannotBeListedSaysSo() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT, REMOVED));
    try
    {
      backend.storage.onListTrees(new Runnable()
      {
        @Override
        public void run()
        {
          throw new StorageRuntimeException("the trees cannot be listed");
        }
      });

      backend.storage.failAfterCommit();
      final ConfigChangeResult ccr = backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT)));
      backend.storage.onListTrees(null);

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(ERR_BACKEND_CANNOT_LIST_TREES_AFTER_BASEDN_CHANGE.ordinal());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A base DN this backend no longer holds must fail the change rather than the method: an entry
   * container unregistered while the change was working out what to do leaves the root container
   * with nothing to answer for that base DN, and the administration framework is owed a result
   * whatever happens.
   */
  @Test
  public void aBaseDNTheBackendNoLongerHoldsFailsTheChangeRatherThanTheMethod() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT, REMOVED));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      final PDBBackendCfg newCfg = backendCfg(newTreeSet(KEPT));
      when(newCfg.getBaseDN()).thenReturn(new UnregisteringWhenAsked(rootContainer, REMOVED, newTreeSet(KEPT)));

      final ConfigChangeResult ccr = backend.applyConfigurationChange(newCfg);

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr)).contains(ERR_BACKEND_BASEDN_NO_LONGER_HELD.ordinal());
      assertThat(ccr.getMessages().toString()).contains(REMOVED.toString());
      // Nothing was applied, so the base DNs this backend serves are the ones it served before.
      assertThat(backend.getBaseDNs()).contains(KEPT);
      assertThat(rootContainer.getStorage().listTrees()).containsAll(treesOf(rootContainer.getEntryContainer(KEPT)));
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A base DN whose entry container is gone must not be answered with an ancestor's.
   * {@link RootContainer#getEntryContainer} walks up the DN until it finds a container, which is how
   * an entry is routed to the base DN above it; asked for a base DN the root container no longer
   * holds, it hands back the container of the one it does. Deleting the trees of that container is
   * deleting the trees of a base DN this backend is still serving.
   * <p>
   * Two base DNs of one backend are hierarchically related only after a registration the registry
   * refused, which leaves the entry container behind in the root container - see
   * {@link #aBaseDNWhichCannotBeRegisteredReportsWhereItFailed}.
   */
  @Test
  public void anEntryContainerWhichIsGoneIsNotAnsweredWithItsParent() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      // Refused by the registry, and so left in the root container underneath KEPT.
      backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT, UNREGISTRABLE)));
      assertThat(rootContainer.getBaseDNs()).contains(KEPT, UNREGISTRABLE);
      final Set<TreeName> keptTrees = treesOf(rootContainer.getEntryContainer(KEPT));

      final PDBBackendCfg newCfg = backendCfg(newTreeSet(KEPT));
      when(newCfg.getBaseDN()).thenReturn(new UnregisteringWhenAsked(rootContainer, UNREGISTRABLE, newTreeSet(KEPT)));
      final ConfigChangeResult ccr = backend.applyConfigurationChange(newCfg);

      // The base DN above the one which was gone is left alone, trees and routing both.
      assertThat(rootContainer.getStorage().listTrees())
          .as("the trees of the base DN above the one which was gone").containsAll(keptTrees);
      assertThat(rootContainer.getBaseDNs()).contains(KEPT);
      assertThat(serverContext.getBackendConfigManager().getLocalBackendWithBaseDN(KEPT)).isSameAs(backend);
      // And the change says which base DN stopped it.
      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr)).contains(ERR_BACKEND_BASEDN_NO_LONGER_HELD.ordinal());
      assertThat(ccr.getMessages().toString()).contains(UNREGISTRABLE.toString());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A base DN the registry refuses is reported with the whole of what refused it. The registry
   * raises the same message for several reasons and from more than one place, so the exception's own
   * text does not say which of them happened; the frames it was raised on do.
   */
  @Test
  public void aBaseDNWhichCannotBeRegisteredReportsWhereItFailed() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT));
    try
    {
      final ConfigChangeResult ccr = backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT, UNREGISTRABLE)));

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr)).contains(ERR_BACKEND_CANNOT_REGISTER_BASEDN.ordinal());
      assertThat(ccr.getMessages().toString())
          .as("the reported cause never says where it was raised")
          .contains("BackendConfigManager.java:");
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * The entry container locks are held for the write which deletes the trees, and no longer:
   * everything below the write reaches {@code BackendConfigManager}, whose single registry lock the
   * server already takes in the opposite order - {@code shutdownLocalBackends} and a backend being
   * disabled both hold it while closing a root container, which locks every entry container in turn.
   * Holding both in this order would deadlock a base DN change against a shutdown, with no timeout
   * on either side.
   */
  @Test
  public void theRegistryIsNotTouchedWhileAnEntryContainerLockIsHeld() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT, REMOVED));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      final EntryContainer removed = rootContainer.getEntryContainer(REMOVED);
      // Listing the surviving trees is the last thing the failure path does before it deregisters,
      // so it is asked on the very thread, and at the very moment, the deadlock would be reached.
      final boolean[] lockHeld = new boolean[] { false };
      final boolean[] asked = new boolean[] { false };
      backend.storage.onListTrees(new Runnable()
      {
        @Override
        public void run()
        {
          asked[0] = true;
          lockHeld[0] |= ((ReentrantReadWriteLock.WriteLock) removed.exclusiveLock).isHeldByCurrentThread();
        }
      });

      backend.storage.failAfterCommit();
      backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT)));
      backend.storage.onListTrees(null);

      assertThat(asked).as("the failure path never listed the surviving trees").containsExactly(true);
      assertThat(lockHeld).as("the entry container lock was still held").containsExactly(false);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A configuration change which leaves the base DNs alone - every change to index-entry-limit,
   * db-cache-percent and the rest - has no storage work to do, so it opens no transaction to
   * commit nothing.
   */
  @Test
  public void aChangeWhichLeavesTheBaseDNsAloneOpensNoTransaction() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT, REMOVED));
    try
    {
      final int writesBefore = backend.storage.writes();
      final ConfigChangeResult ccr = backend.applyConfigurationChange(backendCfg(newTreeSet(KEPT, REMOVED)));

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.getMessages()).isEmpty();
      assertThat(backend.storage.writes()).isEqualTo(writesBefore);
      assertThat(backend.getBaseDNs()).contains(KEPT, REMOVED);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  private static Set<TreeName> treesOf(EntryContainer ec)
  {
    final Set<TreeName> names = new HashSet<>();
    for (Tree tree : ec.listTrees())
    {
      names.add(tree.getName());
    }
    return names;
  }

  /** The messages a change result carries, by identity rather than by their formatted text. */
  private static Set<Integer> ordinalsOf(ConfigChangeResult ccr)
  {
    final Set<Integer> ordinals = new HashSet<>();
    for (LocalizableMessage message : ccr.getMessages())
    {
      ordinals.add(message.ordinal());
    }
    return ordinals;
  }

  /**
   * An index a change adds is opened untrusted while the backend holds entries, and the operator is
   * told to rebuild it. That message belongs to the attempt which commits: added to the result from
   * inside the operation, a replay repeats it once per attempt.
   */
  @Test
  public void anIndexAddedByAChangeIsReportedOnceWhenTheTransactionIsReplayed() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT));
    try
    {
      addBaseEntry(backend, KEPT, "b907a");
      final AttributeIndex index = backend.getRootContainer().getEntryContainer(KEPT).getAttributeIndex(cnType);

      // The first of the three writes a change makes is the one which opens the indexes it adds.
      backend.storage.conflictAtCommit(1);
      final ConfigChangeResult ccr =
          index.applyConfigurationChange(indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.PRESENCE), 4000));

      assertThat(backend.storage.attempts()).isEqualTo(2);
      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ccr.getMessages()).as("the rebuild the added index needs, asked for once").hasSize(1);
      assertThat(ordinalsOf(ccr)).containsOnly(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
      assertThat(index.isIndexed(IndexType.PRESENCE)).as("the index type the change added").isTrue();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * Raising the entry limit of an index leaves it holding the keys it gave up under the lower limit,
   * so it has to be rebuilt and may not be trusted until it is. The limit an index holds is not
   * rolled back with the transaction while the removal of its persisted TRUSTED flag is, so an
   * attempt which rolls back must not be what decides that the limit went up.
   */
  @Test
  public void aRaisedEntryLimitUntrustsTheIndexWhenTheTransactionIsReplayed() throws Exception
  {
    final ReplayingBackend backend = openBackend(newTreeSet(KEPT));
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      final EntryContainer ec = rootContainer.getEntryContainer(KEPT);
      final AttributeIndex index = ec.getAttributeIndex(cnType);
      final MatchingRuleIndex cnIndex = index.getNameToIndexes().values().iterator().next();
      assertThat(persistedFlags(rootContainer, ec, cnIndex.getName())).contains(TRUSTED);

      // The third write is the one which applies the entry limit: the first two open the indexes
      // the change adds and delete the ones it removes, and it neither adds nor removes any.
      backend.storage.conflictAtCommitOnWrite(3, 1);
      final ConfigChangeResult ccr = index.applyConfigurationChange(indexCfg(newTreeSet(IndexType.EQUALITY), 8000));

      assertThat(backend.storage.attempts()).isEqualTo(2);
      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ccr.getMessages()).as("the rebuild the raised limit needs, asked for once").hasSize(1);
      assertThat(ordinalsOf(ccr)).containsOnly(NOTE_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.ordinal());
      assertThat(cnIndex.getIndexEntryLimit()).as("the limit the change applied").isEqualTo(8000);
      assertThat(cnIndex.isTrusted()).isFalse();
      assertThat(persistedFlags(rootContainer, ec, cnIndex.getName()))
          .as("the flag the attempt which committed had to remove").doesNotContain(TRUSTED);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A vlvIndex whose sort order changes holds a tree sorted under the order it no longer has, so it
   * may not be trusted until it is rebuilt. Every question it asks is asked of the configuration it
   * holds, and no rollback takes that configuration back, so the replay of an attempt which rolled
   * back finds nothing changed: the rebuild it goes on asking for is the administrative action the
   * rolled back attempt left in the result, told to the operator once per attempt.
   */
  @Test
  public void aChangedSortOrderUntrustsTheVlvIndexWhenTheTransactionIsReplayed() throws Exception
  {
    final ReplayingBackend backend = openBackendWithVlvIndex();
    try
    {
      final RootContainer rootContainer = backend.getRootContainer();
      final EntryContainer ec = rootContainer.getEntryContainer(KEPT);
      final VLVIndex vlvIndex = ec.getVLVIndex(VLV_INDEX_NAME);
      assertThat(persistedFlags(rootContainer, ec, vlvIndex.getName())).contains(TRUSTED);

      backend.storage.conflictAtCommit(1);
      final ConfigChangeResult ccr = vlvIndex.applyConfigurationChange(vlvIndexCfg("+sn"));

      assertThat(backend.storage.attempts()).isEqualTo(2);
      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ccr.getMessages()).as("the rebuild the new sort order needs, asked for once").hasSize(1);
      assertThat(ordinalsOf(ccr)).containsOnly(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
      assertThat(vlvIndex.isTrusted()).isFalse();
      assertThat(persistedFlags(rootContainer, ec, vlvIndex.getName()))
          .as("the flag the attempt which committed had to remove").doesNotContain(TRUSTED);

      // The definition the change applied is the one this vlvIndex holds from now on, so asking for
      // it a second time asks for nothing.
      final ConfigChangeResult again = vlvIndex.applyConfigurationChange(vlvIndexCfg("+sn"));
      assertThat(again.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(again.adminActionRequired()).as("a change which changes nothing").isFalse();
      assertThat(again.getMessages()).isEmpty();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /** Reads back the flags an index was given when it was opened, as they are stored. */
  private static EnumSet<IndexFlag> persistedFlags(RootContainer rootContainer, EntryContainer ec, TreeName index)
      throws Exception
  {
    final State state = new State(new TreeName(ec.getTreePrefix(), STATE_INDEX_NAME));
    return rootContainer.getStorage().read(txn -> state.getIndexFlags(txn, index));
  }

  private ReplayingBackend openBackend(SortedSet<DN> baseDNs) throws Exception
  {
    return openBackend(baseDNs, false);
  }

  /** The vlvIndex is opened only where a test is about one, since every base DN gets a copy of it. */
  private ReplayingBackend openBackendWithVlvIndex() throws Exception
  {
    return openBackend(newTreeSet(KEPT), true);
  }

  private ReplayingBackend openBackend(SortedSet<DN> baseDNs, boolean withVlvIndex) throws Exception
  {
    final ReplayingBackend backend = new ReplayingBackend();
    backend.setBackendID(BACKEND_ID);
    backend.configuredWith = backendCfg(baseDNs, withVlvIndex);
    backend.configureBackend(backend.configuredWith, serverContext);
    // Start from a pristine on-disk state so that a previous run cannot mask the defect.
    backend.storage.removeStorageFiles();
    try
    {
      backend.openBackend();
    }
    catch (Exception e)
    {
      // openBackend() opens the root container before it preloads, counts the entries, registers
      // the base DNs and registers the monitor, so a failure in any of those leaves the volume open
      // and the monitor registered. Every following test would then fail in openBackend() too, and
      // the one which actually broke would be lost among them.
      try
      {
        if (backend.getRootContainer() != null)
        {
          backend.finalizeBackend();
        }
        else
        {
          backend.storage.close();
        }
      }
      catch (Exception cleanupFailure)
      {
        // openBackend() registers the root container monitor last of all, and closeBackend()
        // deregisters it without a null check, so cleaning up after a failure before that throws a
        // NullPointerException of its own. The failure being cleaned up after is the one worth
        // reading.
        e.addSuppressed(cleanupFailure);
      }
      throw e;
    }
    return backend;
  }

  private PDBBackendCfg backendCfg(SortedSet<DN> baseDNs) throws ConfigException
  {
    return backendCfg(baseDNs, false);
  }

  private PDBBackendCfg backendCfg(SortedSet<DN> baseDNs, boolean withVlvIndex) throws ConfigException
  {
    final PDBBackendCfg cfg = mockCfg(PDBBackendCfg.class);
    when(cfg.dn()).thenReturn(DN.valueOf("ds-cfg-backend-id=" + BACKEND_ID + ",cn=Backends,cn=config"));
    when(cfg.getBackendId()).thenReturn(BACKEND_ID);
    when(cfg.getDBDirectory()).thenReturn(BACKEND_ID);
    when(cfg.getDBDirectoryPermissions()).thenReturn("755");
    when(cfg.getDBCacheSize()).thenReturn(0L);
    when(cfg.getDBCachePercent()).thenReturn(20);
    when(cfg.getBaseDN()).thenReturn(baseDNs);
    when(cfg.listBackendIndexes()).thenReturn(new String[] { "cn" });
    // Built before it is handed over: stubbing a mock from inside a when() of another mock leaves
    // that when() unfinished, and Mockito fails the next test to touch either of them.
    final BackendIndexCfg cnIndexCfg = indexCfg(newTreeSet(IndexType.EQUALITY), 4000);
    when(cfg.getBackendIndex("cn")).thenReturn(cnIndexCfg);
    if (withVlvIndex)
    {
      final BackendVLVIndexCfg vlvCfg = vlvIndexCfg("+cn");
      when(cfg.listBackendVLVIndexes()).thenReturn(new String[] { VLV_INDEX_NAME });
      when(cfg.getBackendVLVIndex(VLV_INDEX_NAME)).thenReturn(vlvCfg);
    }
    else
    {
      when(cfg.listBackendVLVIndexes()).thenReturn(new String[0]);
    }
    return cfg;
  }

  private BackendIndexCfg indexCfg(SortedSet<IndexType> indexTypes, int indexEntryLimit)
  {
    final BackendIndexCfg cfg = mock(BackendIndexCfg.class);
    when(cfg.getIndexType()).thenReturn(indexTypes);
    when(cfg.getAttribute()).thenReturn(cnType);
    when(cfg.getIndexEntryLimit()).thenReturn(indexEntryLimit);
    when(cfg.getSubstringLength()).thenReturn(6);
    return cfg;
  }

  private BackendVLVIndexCfg vlvIndexCfg(String sortOrder)
  {
    final BackendVLVIndexCfg cfg = mock(BackendVLVIndexCfg.class);
    when(cfg.getName()).thenReturn(VLV_INDEX_NAME);
    when(cfg.getBaseDN()).thenReturn(KEPT);
    when(cfg.getScope()).thenReturn(Scope.WHOLE_SUBTREE);
    when(cfg.getFilter()).thenReturn("(objectClass=*)");
    when(cfg.getSortOrder()).thenReturn(sortOrder);
    return cfg;
  }

  /** An index of an empty backend is trusted when it is opened, whatever its tree holds. */
  private static void addBaseEntry(ReplayingBackend backend, DN baseDN, String domainComponent) throws Exception
  {
    backend.addEntry(
        TestCaseUtils.makeEntry("dn: " + baseDN, "objectClass: top", "objectClass: domain", "dc: " + domainComponent),
        mock(AddOperation.class));
  }

  /** A backend whose storage makes the next write operation conflict, and so be replayed. */
  private static final class ReplayingBackend extends BackendImpl<PDBBackendCfg>
  {
    private ReplayingStorage storage;
    /** The configuration the entry containers register their listeners with. */
    private PDBBackendCfg configuredWith;

    @Override
    protected Storage configureStorage(PDBBackendCfg cfg, ServerContext serverContext) throws ConfigException
    {
      storage = new ReplayingStorage(new PDBStorage(cfg, serverContext));
      return storage;
    }
  }

  /**
   * The base DNs a change asks for, which unregisters an entry container the first time it is asked
   * whether it holds one. {@link BackendImpl#applyConfigurationChange} copies the base DNs the root
   * container holds and then looks each of their entry containers up, and asks this set in between;
   * an importLDIF, a rebuildBackend, an exportLDIF or the backend being disabled closes the root
   * container in that window and unregisters every one of them. Done here rather than raced for, so
   * that the window is closed on the same thread every time.
   */
  private static final class UnregisteringWhenAsked extends TreeSet<DN>
  {
    private static final long serialVersionUID = 1L;

    private final transient RootContainer rootContainer;
    private final DN toUnregister;
    private boolean unregistered;

    UnregisteringWhenAsked(RootContainer rootContainer, DN toUnregister, SortedSet<DN> baseDNs)
    {
      super(baseDNs);
      this.rootContainer = rootContainer;
      this.toUnregister = toUnregister;
    }

    @Override
    public boolean contains(Object baseDN)
    {
      if (!unregistered)
      {
        unregistered = true;
        // Closed here because nothing else will: the root container closes the containers it
        // holds, and this one has just been taken out of it, with its configuration listeners
        // still registered.
        closeSilently(rootContainer.unregisterEntryContainer(toUnregister));
      }
      return super.contains(baseDN);
    }
  }

  /** A failure which no storage engine replays, unlike {@link RollbackException}. */
  private static final class UnreplayableFailure extends Exception
  {
    private static final long serialVersionUID = 1L;
  }

  /**
   * Decorates a {@link Storage} so that the next {@link Storage#write(WriteOperation)} conflicts a
   * given number of times before it is let through. The conflict is raised from within the single
   * {@code write} the delegate is asked for, so the delegate's own retry loop performs the replay.
   */
  private static final class ReplayingStorage implements Storage
  {
    /** Where the conflict is raised, which decides how much of the operation has run. */
    private enum ConflictPoint
    {
      /** As soon as the operation first touches the transaction, before it has changed anything. */
      FIRST_STORAGE_ACCESS,
      /** Once the operation has run to completion, as a conflict reported by {@code commit()}. */
      COMMIT,
      /** Once the operation has run to completion, as a failure which is not replayed at all. */
      NO_REPLAY,
      /**
       * Once the operation has committed, as a failure which is not replayed either: what an engine
       * whose tree deletions do not belong to the transaction leaves behind.
       */
      NO_REPLAY_AFTER_COMMIT
    }

    private final Storage delegate;
    private Runnable onListTrees;
    private ConflictPoint conflictPoint;
    /** Which write, counted over the life of this storage, is armed; zero for the next one. */
    private int armedWrite;
    private int conflictsLeft;
    private int attempts;
    private int writes;

    ReplayingStorage(Storage delegate)
    {
      this.delegate = delegate;
    }

    void conflictAtFirstStorageAccess(int conflicts)
    {
      arm(ConflictPoint.FIRST_STORAGE_ACCESS, conflicts);
    }

    void conflictAtCommit(int conflicts)
    {
      arm(ConflictPoint.COMMIT, conflicts);
    }

    /**
     * Conflicts at commit time on the {@code nth} write asked for from now on, the next one being
     * the first: a configuration change which makes several writes has to arm the one it is about.
     */
    void conflictAtCommitOnWrite(int nth, int conflicts)
    {
      arm(ConflictPoint.COMMIT, conflicts);
      armedWrite = writes + nth;
    }

    void failWithoutReplay()
    {
      arm(ConflictPoint.NO_REPLAY, 1);
    }

    void failAfterCommit()
    {
      arm(ConflictPoint.NO_REPLAY_AFTER_COMMIT, 1);
    }

    private void arm(ConflictPoint where, int conflicts)
    {
      conflictPoint = where;
      conflictsLeft = conflicts;
      armedWrite = 0;
      attempts = 0;
    }

    /** How many times the armed operation was run, the first attempt included. */
    int attempts()
    {
      return attempts;
    }

    /** How many write operations this storage was asked for, armed or not. */
    int writes()
    {
      return writes;
    }

    @Override
    public void write(final WriteOperation writeOperation) throws Exception
    {
      writes++;
      final ConflictPoint armed = conflictPoint;
      if (armed == null || (armedWrite != 0 && writes != armedWrite))
      {
        delegate.write(writeOperation);
        return;
      }
      conflictPoint = null;
      armedWrite = 0;
      if (armed == ConflictPoint.NO_REPLAY_AFTER_COMMIT)
      {
        // Committed, then reported as a failure: the operation's work outlives the failure, as it
        // does where the storage engine does not roll a tree deletion back.
        delegate.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            attempts++;
            writeOperation.run(txn);
          }
        });
        throw new UnreplayableFailure();
      }
      // A single call, so that the replay is the delegate's own and keeps whatever the delegate
      // holds for the duration of a write, rather than starting afresh as a second call would.
      delegate.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          attempts++;
          if (conflictsLeft-- <= 0)
          {
            writeOperation.run(txn);
            return;
          }
          if (armed == ConflictPoint.FIRST_STORAGE_ACCESS)
          {
            writeOperation.run(new ConflictingTransaction());
            return;
          }
          writeOperation.run(txn);
          if (armed == ConflictPoint.NO_REPLAY)
          {
            throw new UnreplayableFailure();
          }
          throw new RollbackException();
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

    /** Run whenever the trees which survived a failure are listed, and only then. */
    void onListTrees(Runnable probe)
    {
      this.onListTrees = probe;
    }

    @Override
    public Set<TreeName> listTrees()
    {
      if (onListTrees != null)
      {
        onListTrees.run();
      }
      return delegate.listTrees();
    }

    @Override
    public void close()
    {
      delegate.close();
    }
  }

  /** A transaction which conflicts as soon as it is used, without ever reaching the storage. */
  private static final class ConflictingTransaction implements WriteableTransaction
  {
    private static RollbackException conflict()
    {
      return new RollbackException();
    }

    @Override
    public void openTree(TreeName name, boolean createOnDemand)
    {
      throw conflict();
    }

    @Override
    public void deleteTree(TreeName name)
    {
      throw conflict();
    }

    @Override
    public void put(TreeName treeName, ByteSequence key, ByteSequence value)
    {
      throw conflict();
    }

    @Override
    public boolean update(TreeName treeName, ByteSequence key, UpdateFunction f)
    {
      throw conflict();
    }

    @Override
    public boolean delete(TreeName treeName, ByteSequence key)
    {
      throw conflict();
    }

    @Override
    public ByteString read(TreeName treeName, ByteSequence key)
    {
      throw conflict();
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      throw conflict();
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      throw conflict();
    }

    @Override
    public boolean treeExists(TreeName treeName)
    {
      throw conflict();
    }
  }
}
