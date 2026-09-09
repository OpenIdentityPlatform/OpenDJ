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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opends.messages.BackendMessages.ERR_CONFIG_BACKEND_DATA_CHANGE_FAILED;
import static org.opends.messages.BackendMessages.ERR_CONFIG_INDEX_CHANGE_FAILED;
import static org.opends.messages.BackendMessages.ERR_CONFIG_INDEX_DELETE_FAILED;
import static org.opends.messages.BackendMessages.ERR_CONFIG_VLV_INDEX_DELETE_FAILED;
import static org.opends.server.util.CollectionUtils.newTreeSet;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.meta.BackendVLVIndexCfgDefn.Scope;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.BackendVLVIndexCfg;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.mockito.ArgumentCaptor;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pdb.PDBStorage;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
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

/**
 * Tests what a pluggable backend tells the operator when a {@link Storage#write(WriteOperation)}
 * gives up on a configuration change. A bounded storage stops replaying a conflicting transaction
 * and reports the failure, while the configuration entry the change came from has already been
 * persisted; the in-memory state the four change paths keep around that write is then no longer
 * what the storage holds. Every one of them has to say so - see OpenDJ issue #962.
 * <p>
 * The bound is spent here by a storage decorator which runs the operation and then fails it, which
 * is what the last attempt of a bounded retry loop does: the transaction is rolled back and the
 * caller is handed the failure.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class ConfigChangeGivesUpTest extends DirectoryServerTestCase
{
  private static final String BACKEND_ID = "ConfigChangeGivesUpTest";
  private static final DN BASE_DN = DN.valueOf("dc=b962,dc=com");
  private static final String VLV_INDEX_NAME = "b962vlv";

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
   * The trees of an index whose deletion the storage gave up on are left behind while the
   * configuration entry naming them is gone, so the change has to ask for an administrative action
   * and name the index, rather than hand back a bare stack trace.
   */
  @Test
  public void anIndexDeletionWhichGivesUpAsksForAnAdministrativeAction() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Set<TreeName> indexTrees = treesOf(ec.getAttributeIndex(cnType));

      backend.storage.giveUpOnNextWrite();
      final ConfigChangeResult ccr = indexDeleteListener(backend).applyConfigurationDelete(backend.cnIndexCfg);

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(ERR_CONFIG_INDEX_DELETE_FAILED.ordinal());
      assertThat(ccr.getMessages().toString()).contains("cn").contains(BASE_DN.toString());
      assertThat(backend.getRootContainer().getStorage().listTrees())
          .as("the trees the failed deletion left behind").containsAll(indexTrees);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * The same for a VLV index, whose data is held by two trees: the index itself and the counter
   * which goes with it.
   */
  @Test
  public void aVlvIndexDeletionWhichGivesUpAsksForAnAdministrativeAction() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final TreeName vlvTree = new TreeName(ec.getTreePrefix(), "vlv." + VLV_INDEX_NAME);
      final TreeName vlvCounterTree = new TreeName(ec.getTreePrefix(), "counter.vlv." + VLV_INDEX_NAME);

      backend.storage.giveUpOnNextWrite();
      final ConfigChangeResult ccr = vlvIndexDeleteListener(backend).applyConfigurationDelete(backend.vlvIndexCfg);

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(ERR_CONFIG_VLV_INDEX_DELETE_FAILED.ordinal());
      assertThat(ccr.getMessages().toString()).contains(VLV_INDEX_NAME).contains(BASE_DN.toString());
      assertThat(backend.getRootContainer().getStorage().listTrees())
          .as("the trees the failed deletion left behind").contains(vlvTree, vlvCounterTree);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * An index change is applied by three writes, and the second one deletes the trees of the
   * indexes the new configuration no longer asks for. When it gives up, those trees are still
   * there, so the index has to go on naming them: an index taken out of the map while its trees
   * survive is an index nothing maintains and nothing deletes.
   */
  @Test
  public void anIndexChangeWhichGivesUpDeletingGoesOnNamingWhatItCouldNotDelete() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final AttributeIndex index = ec.getAttributeIndex(cnType);
      final Set<String> indexIdsBefore = new HashSet<>(index.getNameToIndexes().keySet());
      final Set<TreeName> treesBefore = treesOf(index);

      // Dropping the substring index is what gives the second write something to delete.
      backend.storage.giveUpOnWrite(2);
      final ConfigChangeResult ccr =
          index.applyConfigurationChange(indexCfg(newTreeSet(IndexType.EQUALITY), 4000));

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(ERR_CONFIG_INDEX_CHANGE_FAILED.ordinal());
      assertThat(ccr.getMessages().toString()).contains("cn").contains(BASE_DN.toString());
      assertThat(index.getNameToIndexes().keySet())
          .as("the indexes whose trees the failed deletion left behind").isEqualTo(indexIdsBefore);
      assertThat(backend.getRootContainer().getStorage().listTrees()).containsAll(treesBefore);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * The third write is what applies the entry limit and the confidentiality the new configuration
   * declares to the indexes which stay. When it gives up, the configuration must not be published
   * either, or the index claims settings which were never applied to it. What it declares is read
   * here through the index types it names, the only reader of that field this test can reach.
   */
  @Test
  public void anIndexChangeWhichGivesUpUpdatingDoesNotPublishWhatItCouldNotApply() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final AttributeIndex index = ec.getAttributeIndex(cnType);
      final Set<String> indexIdsBefore = new HashSet<>(index.getNameToIndexes().keySet());

      // A presence index is added and the entry limit of the ones which stay is lowered, so that
      // all three writes have work to do and the third is the one which gives up.
      backend.storage.giveUpOnWrite(3);
      final ConfigChangeResult ccr = index.applyConfigurationChange(
          indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING, IndexType.PRESENCE), 100));

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(ERR_CONFIG_INDEX_CHANGE_FAILED.ordinal());
      assertThat(index.getNameToIndexes().keySet())
          .as("the index the writes which did commit opened")
          .containsAll(indexIdsBefore).hasSize(indexIdsBefore.size() + 1);
      assertThat(index.isIndexed(IndexType.PRESENCE))
          .as("a configuration the write which gave up never applied").isFalse();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * The compression, encoding and encryption settings of a backend are applied to the entries and
   * to every index of that backend, and nothing else can apply half of them. A change which cannot
   * be applied has to ask for an administrative action and leave what it did not apply alone.
   */
  @Test
  public void aBackendDataChangeWhichCannotBeAppliedAsksForAnAdministrativeAction() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final PDBBackendCfg newCfg = backendCfg(backend.cnIndexCfg, backend.vlvIndexCfg);
      when(newCfg.isConfidentialityEnabled()).thenReturn(true);
      when(newCfg.isEntriesCompressed()).thenThrow(new IllegalStateException("no settings to be had"));

      final ConfigChangeResult ccr = ec.applyConfigurationChange(newCfg);

      assertThat(ccr.getResultCode()).isNotEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(ERR_CONFIG_BACKEND_DATA_CHANGE_FAILED.ordinal());
      assertThat(ccr.getMessages().toString()).contains(BASE_DN.toString());
      assertThat(ec.isConfidentialityEnabled())
          .as("a configuration none of which was applied").isFalse();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * Applying those settings writes nothing: it hands the entries and the indexes new parameters to
   * encode with from now on, and neither of those is a record in a tree. A transaction opened for
   * it is one a storage can give up on, and giving up half way is what leaves the entries and the
   * indexes encoded under settings which no longer agree.
   */
  @Test
  public void aBackendDataChangeOpensNoTransaction() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final PDBBackendCfg newCfg = backendCfg(backend.cnIndexCfg, backend.vlvIndexCfg);
      when(newCfg.isEntriesCompressed()).thenReturn(true);

      final int writesBefore = backend.storage.writes();
      final ConfigChangeResult ccr = ec.applyConfigurationChange(newCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.getMessages()).isEmpty();
      assertThat(ccr.adminActionRequired()).isFalse();
      assertThat(backend.storage.writes())
          .as("a transaction opened for work none of which is transactional").isEqualTo(writesBefore);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /** A deletion which does delete the trees has nothing to tell the operator. */
  @Test
  public void anIndexDeletionWhichSucceedsAsksForNothing() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Set<TreeName> indexTrees = treesOf(ec.getAttributeIndex(cnType));

      final ConfigChangeResult ccr = indexDeleteListener(backend).applyConfigurationDelete(backend.cnIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.getMessages()).isEmpty();
      assertThat(ccr.adminActionRequired()).isFalse();
      assertThat(backend.getRootContainer().getStorage().listTrees())
          .doesNotContain(indexTrees.toArray(new TreeName[0]));
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /** A VLV deletion which does delete the trees has nothing to tell the operator either. */
  @Test
  public void aVlvIndexDeletionWhichSucceedsAsksForNothing() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final TreeName vlvTree = new TreeName(ec.getTreePrefix(), "vlv." + VLV_INDEX_NAME);
      final TreeName vlvCounterTree = new TreeName(ec.getTreePrefix(), "counter.vlv." + VLV_INDEX_NAME);

      final ConfigChangeResult ccr = vlvIndexDeleteListener(backend).applyConfigurationDelete(backend.vlvIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.getMessages()).isEmpty();
      assertThat(ccr.adminActionRequired()).isFalse();
      assertThat(backend.getRootContainer().getStorage().listTrees())
          .as("the trees the deletion removed").doesNotContain(vlvTree, vlvCounterTree);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * A change which does apply publishes every part of it, each after the write which applied that
   * part: the map of indexes after the deletion, the indexing options with it, and the
   * configuration after the update. The administrative action this one asks for is the other kind -
   * an index which has just been added has yet to be rebuilt - and not a divergence.
   */
  @Test
  public void anIndexChangeWhichSucceedsPublishesEveryPartOfIt() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final AttributeIndex index = ec.getAttributeIndex(cnType);
      final Set<String> indexIdsBefore = new HashSet<>(index.getNameToIndexes().keySet());

      final ConfigChangeResult ccr = index.applyConfigurationChange(
          indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.PRESENCE), 4000, 3));

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ordinalsOf(ccr)).doesNotContain(ERR_CONFIG_INDEX_CHANGE_FAILED.ordinal());
      assertThat(index.isIndexed(IndexType.PRESENCE))
          .as("an index type this change declares").isTrue();
      assertThat(index.isIndexed(IndexType.SUBSTRING))
          .as("an index type this change no longer declares").isFalse();
      assertThat(index.getIndexingOptions().substringKeySize())
          .as("the indexing options this change declares").isEqualTo(3);
      assertThat(index.getNameToIndexes().keySet())
          .as("the indexes this change opened, and none of the ones it deleted")
          .isNotEqualTo(indexIdsBefore);
      assertThat(backend.getRootContainer().getStorage().listTrees())
          .as("every tree the published map names").containsAll(treesOf(index));
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * Pins what {@code ERR_CONFIG_INDEX_DELETE_FAILED} tells the operator: an index created again for
   * the same attribute adopts the trees a failed deletion left behind, and is trusted over their
   * stale content without a word about rebuilding it. This is not the behaviour being asked for
   * here - it is the behaviour that message describes (#990). When it is fixed, this test fails and
   * the message has to be rewritten, rather than quietly becoming untrue. Its VLV counterpart
   * says the same of {@code VLVIndex.afterOpen}, which nothing here holds.
   */
  @Test
  public void anIndexCreatedAgainAdoptsTheTreesAFailedDeletionLeftBehind() throws Exception
  {
    final GivingUpBackend backend = openBackend();
    try
    {
      // An index of an empty backend is trusted whatever its trees hold, so this needs an entry.
      backend.addEntry(
          TestCaseUtils.makeEntry("dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: b962"),
          mock(AddOperation.class));
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Set<TreeName> indexTrees = treesOf(ec.getAttributeIndex(cnType));

      backend.storage.giveUpOnNextWrite();
      indexDeleteListener(backend).applyConfigurationDelete(backend.cnIndexCfg);

      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(treesOf(ec.getAttributeIndex(cnType)))
          .as("the trees the index created again holds").isEqualTo(indexTrees);
      assertThat(ec.getAttributeIndex(cnType).isTrusted())
          .as("an index trusted over the content of the trees it adopted").isTrue();
      assertThat(ccr.getMessages())
          .as("nothing tells the operator this index has to be rebuilt").isEmpty();
    }
    finally
    {
      backend.finalizeBackend();
    }
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

  private static Set<TreeName> treesOf(AttributeIndex index)
  {
    final Set<TreeName> names = new HashSet<>();
    for (MatchingRuleIndex matchingRuleIndex : index.getNameToIndexes().values())
    {
      names.add(matchingRuleIndex.getName());
    }
    return names;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static ConfigurationDeleteListener<BackendIndexCfg> indexDeleteListener(GivingUpBackend backend)
      throws ConfigException
  {
    final ArgumentCaptor<ConfigurationDeleteListener> captor =
        ArgumentCaptor.forClass(ConfigurationDeleteListener.class);
    verify(backend.configuredWith).addBackendIndexDeleteListener(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static ConfigurationDeleteListener<BackendVLVIndexCfg> vlvIndexDeleteListener(GivingUpBackend backend)
      throws ConfigException
  {
    final ArgumentCaptor<ConfigurationDeleteListener> captor =
        ArgumentCaptor.forClass(ConfigurationDeleteListener.class);
    verify(backend.configuredWith).addBackendVLVIndexDeleteListener(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static ConfigurationAddListener<BackendIndexCfg> indexAddListener(GivingUpBackend backend)
      throws ConfigException
  {
    final ArgumentCaptor<ConfigurationAddListener> captor = ArgumentCaptor.forClass(ConfigurationAddListener.class);
    verify(backend.configuredWith).addBackendIndexAddListener(captor.capture());
    return captor.getValue();
  }

  private GivingUpBackend openBackend() throws Exception
  {
    final GivingUpBackend backend = new GivingUpBackend();
    backend.setBackendID(BACKEND_ID);
    backend.cnIndexCfg = indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING), 4000);
    backend.vlvIndexCfg = vlvIndexCfg();
    backend.configuredWith = backendCfg(backend.cnIndexCfg, backend.vlvIndexCfg);
    backend.configureBackend(backend.configuredWith, serverContext);
    // Start from a pristine on-disk state, so that a previous run cannot mask what this one leaves.
    backend.storage.removeStorageFiles();
    try
    {
      backend.openBackend();
    }
    catch (Exception e)
    {
      // openBackend() opens the root container before it registers the base DNs and the monitor, so
      // a failure in any of those leaves the volume open and every following test failing here too.
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
        e.addSuppressed(cleanupFailure);
      }
      throw e;
    }
    return backend;
  }

  private PDBBackendCfg backendCfg(BackendIndexCfg cnIndexCfg, BackendVLVIndexCfg vlvIndexCfg) throws ConfigException
  {
    final PDBBackendCfg cfg = mockCfg(PDBBackendCfg.class);
    when(cfg.dn()).thenReturn(DN.valueOf("ds-cfg-backend-id=" + BACKEND_ID + ",cn=Backends,cn=config"));
    when(cfg.getBackendId()).thenReturn(BACKEND_ID);
    when(cfg.getDBDirectory()).thenReturn(BACKEND_ID);
    when(cfg.getDBDirectoryPermissions()).thenReturn("755");
    when(cfg.getDBCacheSize()).thenReturn(0L);
    when(cfg.getDBCachePercent()).thenReturn(20);
    when(cfg.getBaseDN()).thenReturn(newTreeSet(BASE_DN));
    when(cfg.listBackendIndexes()).thenReturn(new String[] { "cn" });
    when(cfg.getBackendIndex("cn")).thenReturn(cnIndexCfg);
    when(cfg.listBackendVLVIndexes()).thenReturn(new String[] { VLV_INDEX_NAME });
    when(cfg.getBackendVLVIndex(VLV_INDEX_NAME)).thenReturn(vlvIndexCfg);
    return cfg;
  }

  private BackendIndexCfg indexCfg(SortedSet<IndexType> indexTypes, int indexEntryLimit)
  {
    return indexCfg(indexTypes, indexEntryLimit, 6);
  }

  private BackendIndexCfg indexCfg(SortedSet<IndexType> indexTypes, int indexEntryLimit, int substringLength)
  {
    final BackendIndexCfg cfg = mock(BackendIndexCfg.class);
    when(cfg.getIndexType()).thenReturn(indexTypes);
    when(cfg.getAttribute()).thenReturn(cnType);
    when(cfg.getIndexEntryLimit()).thenReturn(indexEntryLimit);
    when(cfg.getSubstringLength()).thenReturn(substringLength);
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

  /** A backend whose storage gives up on a write instead of replaying it until it succeeds. */
  private static final class GivingUpBackend extends BackendImpl<PDBBackendCfg>
  {
    private GivingUpStorage storage;
    /** The configuration the entry container registers its listeners with. */
    private PDBBackendCfg configuredWith;
    private BackendIndexCfg cnIndexCfg;
    private BackendVLVIndexCfg vlvIndexCfg;

    @Override
    protected Storage configureStorage(PDBBackendCfg cfg, ServerContext serverContext) throws ConfigException
    {
      storage = new GivingUpStorage(new PDBStorage(cfg, serverContext));
      return storage;
    }
  }

  /** What a storage which has spent its replay bound hands its caller. */
  private static final class StorageGaveUp extends Exception
  {
    private static final long serialVersionUID = 1L;
  }

  /**
   * Decorates a {@link Storage} so that a chosen write gives up rather than succeeding. The
   * operation is run inside the transaction the delegate opens and the failure is raised from
   * within it, so that the transaction is rolled back and the caller is handed the failure - which
   * is what the last attempt of a bounded retry loop leaves behind.
   */
  private static final class GivingUpStorage implements Storage
  {
    private final Storage delegate;
    /**
     * Counted over every write this storage is asked for, by whoever asks: the backend it belongs
     * to is private to one test at a time and this module runs its tests one at a time, so a write
     * armed here is the write the test is about. A backend which wrote on a thread of its own would
     * break that, and would have to arm the operation rather than the count.
     */
    private int writes;
    /** Which write, counted over the life of this storage, gives up; zero when none does. */
    private int givesUpAt;

    GivingUpStorage(Storage delegate)
    {
      this.delegate = delegate;
    }

    void giveUpOnNextWrite()
    {
      giveUpOnWrite(1);
    }

    /** Gives up on the {@code nth} write asked for from now on, the next one being the first. */
    void giveUpOnWrite(int nth)
    {
      givesUpAt = writes + nth;
    }

    /** How many write operations this storage was asked for. */
    int writes()
    {
      return writes;
    }

    @Override
    public void write(final WriteOperation writeOperation) throws Exception
    {
      writes++;
      if (writes != givesUpAt)
      {
        delegate.write(writeOperation);
        return;
      }
      givesUpAt = 0;
      delegate.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          writeOperation.run(txn);
          throw new StorageGaveUp();
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
