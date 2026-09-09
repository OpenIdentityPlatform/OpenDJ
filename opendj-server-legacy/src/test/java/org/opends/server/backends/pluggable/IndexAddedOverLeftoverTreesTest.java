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
import static org.opends.messages.BackendMessages.NOTE_INDEX_ADD_REQUIRES_REBUILD;
import static org.opends.server.backends.pluggable.State.IndexFlag.TRUSTED;
import static org.opends.server.backends.pluggable.SuffixContainer.STATE_INDEX_NAME;
import static org.opends.server.util.CollectionUtils.newTreeSet;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.mockito.ArgumentCaptor;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pdb.PDBStorage;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.AddOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
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
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class IndexAddedOverLeftoverTreesTest extends DirectoryServerTestCase
{
  private static final String BACKEND_ID = "IndexAddedOverLeftoverTreesTest";
  private static final DN BASE_DN = DN.valueOf("dc=b990,dc=com");
  private static final String VLV_INDEX_NAME = "b990vlv";

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
   * The index the add opens is not the index whose trees are still there: it has indexed none of
   * the entries, so it must be untrusted and the operator must be told to rebuild it, exactly as
   * for any other index added to a backend which already holds entries.
   */
  @Test
  public void anIndexAddedOverTheTreesLeftBehindIsNotTrusted() throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind();
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
      assertThat(ordinalsOf(ccr)).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
    }
    finally
    {
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
  @Test
  public void anIndexAddedOverTheTreesLeftBehindDoesNotAnswerWithTheirContent() throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final Storage storage = backend.getRootContainer().getStorage();
      final Map<TreeName, ByteString> keysHeld = keysHeldBy(storage, backend.leftoverIndexTrees);
      assertThat(keysHeld).as("the keys the trees left behind hold").isNotEmpty();

      indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      final AttributeIndex index = ec.getAttributeIndex(cnType);
      for (final MatchingRuleIndex opened : index.getNameToIndexes().values())
      {
        final ByteString keyHeld = keysHeld.get(opened.getName());
        if (keyHeld != null)
        {
          final Boolean answered = storage.read(txn -> opened.get(txn, keyHeld).isDefined());
          assertThat(answered)
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
   * TRUSTED out of those records - an empty index which every search believes.
   */
  @Test
  public void anIndexAddedOverAStateRecordWhoseTreesAreGoneIsNotTrusted() throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind();
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
      assertThat(ordinalsOf(ccr)).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /** A VLV index adopts what it left behind in the same way, in its tree and in its counter. */
  @Test
  public void aVlvIndexAddedOverTheTreesLeftBehindIsNotTrusted() throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind();
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      assertThat(backend.getRootContainer().getStorage().listTrees())
          .as("the VLV trees no configuration names any more")
          .contains(backend.leftoverVlvTree, backend.leftoverVlvCounterTree);

      final ConfigChangeResult ccr = vlvIndexAddListener(backend).applyConfigurationAdd(backend.vlvIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ec.getVLVIndex(VLV_INDEX_NAME).isTrusted())
          .as("a VLV index trusted over the content of trees it did not fill").isFalse();
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * An index type declared again for an attribute which is still indexed goes through
   * {@code AttributeIndex.applyConfigurationChange} rather than through the add listener, and opens
   * its tree the same way.
   */
  @Test
  public void anIndexTypeAddedOverTheTreeLeftBehindIsNotTrusted() throws Exception
  {
    final LeftoverBackend backend = leaveTreesBehind(indexCfg(newTreeSet(IndexType.EQUALITY)));
    try
    {
      final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
      final AttributeIndex index = ec.getAttributeIndex(cnType);
      assertThat(index.isIndexed(IndexType.SUBSTRING))
          .as("the index type this configuration no longer names").isFalse();

      final ConfigChangeResult ccr =
          index.applyConfigurationChange(indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING)));

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(index.isIndexed(IndexType.SUBSTRING)).isTrue();
      assertThat(index.isTrusted())
          .as("an index type trusted over the content of the tree it did not fill").isFalse();
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
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
  @Test
  public void anIndexAddedToAnEmptyBackendIsTrustedAndAsksForNothing() throws Exception
  {
    final LeftoverBackend backend = openBackend(true, null, null);
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
   * alone: this is the outcome an index added over leftover trees has to reach as well.
   */
  @Test
  public void anIndexAddedToANonEmptyBackendAsksForARebuild() throws Exception
  {
    final LeftoverBackend backend = openBackend(true, null, null);
    try
    {
      addEntry(backend, "dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: b990");

      final ConfigChangeResult ccr = indexAddListener(backend).applyConfigurationAdd(backend.cnIndexCfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(backend.getRootContainer().getEntryContainer(BASE_DN).getAttributeIndex(cnType).isTrusted())
          .as("an index which has indexed none of the entries").isFalse();
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(NOTE_INDEX_ADD_REQUIRES_REBUILD.ordinal());
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  private LeftoverBackend leaveTreesBehind() throws Exception
  {
    return leaveTreesBehind(null);
  }

  /**
   * Opens a backend whose configuration names a cn index and a VLV index, fills them with an entry,
   * and reopens it from a configuration which names {@code reopenedWith} instead - null for a
   * configuration which names no index at all. The trees of everything it no longer names are left
   * behind, and the entry added afterwards is in none of them.
   */
  private LeftoverBackend leaveTreesBehind(BackendIndexCfg reopenedWith) throws Exception
  {
    final LeftoverBackend indexed = openBackend(true, indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING)),
        vlvIndexCfg());
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

    final LeftoverBackend reopened = openBackend(false, reopenedWith, null);
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

  /** Reads back the flags an index tree carries, as they are stored. */
  private EnumSet<State.IndexFlag> persistedFlags(LeftoverBackend backend, TreeName index) throws Exception
  {
    final EntryContainer ec = backend.getRootContainer().getEntryContainer(BASE_DN);
    final State state = new State(new TreeName(ec.getTreePrefix(), STATE_INDEX_NAME));
    return backend.getRootContainer().getStorage().read(txn -> state.getIndexFlags(txn, index));
  }

  /** The messages a change result carries, by identity rather than by their formatted text. */
  private static Set<Integer> ordinalsOf(ConfigChangeResult ccr)
  {
    final Set<Integer> ordinals = new HashSet<>();
    for (final LocalizableMessage message : ccr.getMessages())
    {
      ordinals.add(message.ordinal());
    }
    return ordinals;
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
  private LeftoverBackend openBackend(boolean pristine, BackendIndexCfg cnIndexCfg, BackendVLVIndexCfg vlvIndexCfg)
      throws Exception
  {
    final LeftoverBackend backend = new LeftoverBackend();
    backend.setBackendID(BACKEND_ID);
    backend.cnIndexCfg = cnIndexCfg != null ? cnIndexCfg
        : indexCfg(newTreeSet(IndexType.EQUALITY, IndexType.SUBSTRING));
    backend.vlvIndexCfg = vlvIndexCfg != null ? vlvIndexCfg : vlvIndexCfg();
    backend.configuredWith = backendCfg(cnIndexCfg, vlvIndexCfg);
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

  /** A backend which keeps hold of the configuration its entry container registered with. */
  private static final class LeftoverBackend extends BackendImpl<PDBBackendCfg>
  {
    private PDBStorage storage;
    /** The configuration the entry container registers its listeners with. */
    private PDBBackendCfg configuredWith;
    private BackendIndexCfg cnIndexCfg;
    private BackendVLVIndexCfg vlvIndexCfg;
    /** The trees of the indexes an earlier configuration named, which nothing names now. */
    private Set<TreeName> leftoverIndexTrees;
    private TreeName leftoverVlvTree;
    private TreeName leftoverVlvCounterTree;

    @Override
    protected Storage configureStorage(PDBBackendCfg cfg, ServerContext serverContext) throws ConfigException
    {
      storage = new PDBStorage(cfg, serverContext);
      return storage;
    }
  }
}
