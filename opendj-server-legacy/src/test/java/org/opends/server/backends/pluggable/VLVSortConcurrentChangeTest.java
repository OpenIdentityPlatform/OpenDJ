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
import static org.mockito.Mockito.when;
import static org.opends.server.backends.pluggable.IndexFilter.CURSOR_ENTRY_LIMIT;
import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.opends.server.protocols.internal.Requests.newSearchRequest;
import static org.opends.server.protocols.ldap.LDAPResultCode.SUCCESS;
import static org.opends.server.util.CollectionUtils.newTreeSet;
import static org.opends.server.util.ServerConstants.OID_SERVER_SIDE_SORT_RESPONSE_CONTROL;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.meta.BackendVLVIndexCfgDefn.Scope;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.BackendVLVIndexCfg;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.backends.pdb.PDBStorage;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.ServerSideSortResponseControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Regression tests for GHSA-6f6q-q9gw-4jr4: a sorted search of a caller subject to access control
 * takes the entries of a VLV index, then reads and sorts them through the caller's access controls.
 * The JE backend reads without a snapshot, so what the storage holds may change between those reads:
 * an entry may be deleted, and the index may stop being applicable or grow. The search must then
 * leave the entry out, or go on as without an index no longer applicable, rather than fail or come
 * back empty. An index that counts more than the most entries read from an index at once is refused,
 * whether it did when checked or grew over it since, and so is one that grows over the caller's
 * lookthrough limit between its count and the read of its entries. An index that applies and holds
 * no entry gives none.
 * <p>
 * The tests run on PDB, whose reads see a snapshot: the storage below makes each change land right
 * after the read it must follow, and answers the reads of the search after it as the JE backend
 * would.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class VLVSortConcurrentChangeTest extends DirectoryServerTestCase
{
  private static final String BACKEND_ID = "VLVSortConcurrentChangeTest";
  private static final DN BASE_DN = DN.valueOf("dc=vlvsort,dc=com");
  private static final DN PEOPLE_DN = DN.valueOf("ou=people,dc=vlvsort,dc=com");
  /** Held in lower case, which is how an entry container keys the VLV indexes it holds. */
  private static final String VLV_INDEX_NAME = "people-by-sn";

  /** Added in this order, other than the order of their "sn" values: alice, bob, charlie. */
  private static final DN CHARLIE_DN = DN.valueOf("uid=charlie,ou=people,dc=vlvsort,dc=com");
  private static final DN ALICE_DN = DN.valueOf("uid=alice,ou=people,dc=vlvsort,dc=com");
  private static final DN BOB_DN = DN.valueOf("uid=bob,ou=people,dc=vlvsort,dc=com");
  /** Added only by the test that needs a fourth person. */
  private static final DN DAVE_DN = DN.valueOf("uid=dave,ou=people,dc=vlvsort,dc=com");

  private ServerContext serverContext;
  /** A user without the bypass-acl privilege, whom the global ACIs let read every entry and "sn". */
  private InternalClientConnection actorConn;
  private SteeringBackend backend;
  private EntryContainer entryContainer;
  private VLVIndex vlvIndex;

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    serverContext = TestCaseUtils.getServerContext();
    actorConn = new InternalClientConnection(new AuthenticationInfo(TestCaseUtils.makeEntry(
        "dn: uid=actor,dc=vlvsort,dc=com",
        "objectClass: top",
        "objectClass: person",
        "sn: Or",
        "cn: Act Or"), false));
  }

  @BeforeMethod
  public void openBackend() throws Exception
  {
    backend = new SteeringBackend();
    backend.setBackendID(BACKEND_ID);
    backend.configureBackend(backendCfg(), serverContext);
    // Start from a pristine on-disk state, whatever the previous test changed in it.
    backend.storage.removeStorageFiles();
    backend.openBackend();

    final List<Entry> entries = TestCaseUtils.makeEntries(
        "dn: " + BASE_DN,
        "objectClass: top",
        "objectClass: domain",
        "dc: vlvsort",
        "",
        "dn: " + PEOPLE_DN,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: people",
        "",
        "dn: " + CHARLIE_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: charlie",
        "sn: Xavier",
        "cn: Charlie Xavier",
        "",
        "dn: " + ALICE_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: alice",
        "sn: Baker",
        "cn: Alice Baker",
        "",
        "dn: " + BOB_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: bob",
        "sn: Monroe",
        "cn: Bob Monroe");
    for (Entry entry : entries)
    {
      backend.addEntry(entry, mock(AddOperation.class));
    }

    entryContainer = backend.getRootContainer().getEntryContainer(BASE_DN);
    vlvIndex = entryContainer.getVLVIndex(VLV_INDEX_NAME);
    // Created with the backend empty, so trusted: otherwise no search would take it.
    assertThat(vlvIndex.isTrusted()).as("the VLV index trusted").isTrue();
  }

  @AfterMethod(alwaysRun = true)
  public void closeBackend()
  {
    if (backend != null && backend.getRootContainer() != null)
    {
      backend.finalizeBackend();
    }
    backend = null;
    try
    {
      // What a test that failed while the backend opened may have left in the server wide registry.
      serverContext.getBackendConfigManager().deregisterBaseDN(BASE_DN);
    }
    catch (Exception alreadyGone)
    {
      // Which is what closing the backend does.
    }
  }

  /**
   * An entry deleted once the VLV index gave the candidates, and before the sort reads it, takes no
   * part in the sort: the search must not fail on it.
   */
  @Test
  public void sortOverVLVIndexLeavesOutEntryDeletedSinceItsCandidatesWereRead() throws Exception
  {
    backend.storage.deleteForTheNextReads(entryContainer.getID2Entry().getName(), entryIDKey(BOB_DN));

    assertThat(sortedPeople(actorConn, PEOPLE_DN, SearchScope.SINGLE_LEVEL)).containsExactly(ALICE_DN, CHARLIE_DN);
    assertThat(backend.storage.readsOfDeleted).as("reads of the deleted entry").isPositive();
  }

  /**
   * The same on the road of the attribute indexes, which every caller takes when no VLV index serves
   * the search.
   */
  @Test
  public void sortOverAttributeIndexLeavesOutEntryDeletedSinceItsCandidatesWereRead() throws Exception
  {
    backend.storage.deleteForTheNextReads(entryContainer.getID2Entry().getName(), entryIDKey(BOB_DN));

    assertThat(sortedPeople(getRootConnection(), BASE_DN, SearchScope.WHOLE_SUBTREE))
        .containsExactly(ALICE_DN, CHARLIE_DN);
    assertThat(backend.storage.readsOfDeleted).as("reads of the deleted entry").isPositive();
  }

  /**
   * A change of the definition of the VLV index between the check that it serves the search and the
   * read of its entries makes it no longer applicable: the search must go on as without it.
   */
  @Test
  public void sortGoesOnWithoutVLVIndexRedefinedSinceItWasChecked() throws Exception
  {
    backend.storage.afterFirstRead(counterTreeName(),
        () -> concurrently(() -> vlvIndex.applyConfigurationChange(vlvIndexCfg("-sn"))));

    assertThat(sortedPeople(actorConn, PEOPLE_DN, SearchScope.SINGLE_LEVEL))
        .containsExactly(ALICE_DN, BOB_DN, CHARLIE_DN);
    assertThat(backend.storage.changes).as("changes made during the search").isEqualTo(1);
    assertThat(vlvIndex.isTrusted()).as("the VLV index trusted after the change of its definition").isFalse();
  }

  /**
   * A rebuild of the VLV index starting between the check and the read of its entries leaves it
   * untrusted and, for a read of the JE backend, empty: the search must go on as without it rather
   * than take the empty index for an empty result.
   */
  @Test
  public void sortGoesOnWithoutVLVIndexWhoseRebuildStartedSinceItWasChecked() throws Exception
  {
    final TreeName deleted = emptyTree("vlv." + VLV_INDEX_NAME + ".deleted");
    backend.storage.afterFirstRead(counterTreeName(), () ->
    {
      concurrently(() ->
      {
        backend.getRootContainer().getStorage().write(txn -> vlvIndex.setTrusted(txn, false));
        return null;
      });
      backend.storage.answer(vlvIndex.getName(), deleted);
      backend.storage.answer(counterTreeName(), deleted);
    });

    assertThat(sortedPeople(actorConn, PEOPLE_DN, SearchScope.SINGLE_LEVEL))
        .containsExactly(ALICE_DN, BOB_DN, CHARLIE_DN);
    assertThat(backend.storage.changes).as("changes made during the search").isEqualTo(1);
    assertThat(vlvIndex.isTrusted()).as("the VLV index trusted once its rebuild started").isFalse();
  }

  /**
   * The VLV index growing over the most entries read from an index at once between its count and the
   * read of its entries makes it give none: the search must be refused, as it is when the index
   * already counts that many, rather than go on as without it and sort as many entries from the
   * attribute indexes.
   */
  @Test
  public void sortOverVLVIndexGrownOverTheEntryLimitSinceItWasCountedIsRefused() throws Exception
  {
    final TreeName grown = counterHolding("counter.vlv." + VLV_INDEX_NAME + ".grown", CURSOR_ENTRY_LIMIT + 1);
    backend.storage.afterFirstRead(counterTreeName(), () -> backend.storage.answer(counterTreeName(), grown));

    final InternalSearchOperation search = searchPeople(actorConn, PEOPLE_DN, SearchScope.SINGLE_LEVEL);
    assertThat(search.getResultCode()).isEqualTo(ResultCode.ADMIN_LIMIT_EXCEEDED);
    assertThat(search.getSearchEntries()).isEmpty();
    assertThat(backend.storage.changes).as("changes made during the search").isEqualTo(1);
  }

  /**
   * A VLV index that stops being applicable is left aside whatever it counts: redefined between the
   * check and the read of its entries while it also grows over the most entries read from an index at
   * once, the search must still go on as without it rather than be refused.
   */
  @Test
  public void sortGoesOnWithoutVLVIndexRedefinedSinceItWasCheckedThoughItGrewOverTheEntryLimit() throws Exception
  {
    final TreeName grown = counterHolding("counter.vlv." + VLV_INDEX_NAME + ".grown", CURSOR_ENTRY_LIMIT + 1);
    backend.storage.afterFirstRead(counterTreeName(), () ->
    {
      concurrently(() -> vlvIndex.applyConfigurationChange(vlvIndexCfg("-sn")));
      backend.storage.answer(counterTreeName(), grown);
    });

    assertThat(sortedPeople(actorConn, PEOPLE_DN, SearchScope.SINGLE_LEVEL))
        .containsExactly(ALICE_DN, BOB_DN, CHARLIE_DN);
    assertThat(backend.storage.changes).as("changes made during the search").isEqualTo(1);
  }

  /**
   * An applicable VLV index whose tree gives none of the entries its count still holds, a state no
   * read should see, is neither refused nor taken for empty: the search must go on as without it.
   */
  @Test
  public void sortGoesOnWithoutVLVIndexHoldingNoneOfTheEntriesItCounts() throws Exception
  {
    backend.storage.answer(vlvIndex.getName(), emptyTree("vlv." + VLV_INDEX_NAME + ".emptied"));

    assertThat(sortedPeople(actorConn, PEOPLE_DN, SearchScope.SINGLE_LEVEL))
        .containsExactly(ALICE_DN, BOB_DN, CHARLIE_DN);
  }

  /**
   * Sorting the entries of a VLV index through the caller's access controls reads every one of them:
   * a caller without a lookthrough limit, as an internal one, may still not read more than the most
   * entries read from an index at once, so an index counting more is refused.
   */
  @Test
  public void sortOverVLVIndexCountingOverTheEntryLimitIsRefused() throws Exception
  {
    backend.storage.answer(counterTreeName(),
        counterHolding("counter.vlv." + VLV_INDEX_NAME + ".over", CURSOR_ENTRY_LIMIT + 1));

    final InternalSearchOperation search = searchPeople(actorConn, PEOPLE_DN, SearchScope.SINGLE_LEVEL);
    assertThat(search.getResultCode()).isEqualTo(ResultCode.ADMIN_LIMIT_EXCEEDED);
    assertThat(search.getSearchEntries()).isEmpty();
  }

  /**
   * The VLV index growing between its count, checked against the caller's lookthrough limit, and the
   * read of its entries leaves it holding more entries than the caller may examine: the search must
   * be refused rather than sort them all, even though the VLV page it asks for is smaller.
   */
  @Test
  public void sortOverVLVIndexGrownOverTheLookthroughLimitSinceItWasCountedIsRefused() throws Exception
  {
    // The index holds a fourth person, as if added right after its count read three.
    backend.addEntry(TestCaseUtils.makeEntry(
        "dn: " + DAVE_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: dave",
        "sn: Delaney",
        "cn: Dave Delaney"), mock(AddOperation.class));
    backend.storage.answer(counterTreeName(), counterHolding("counter.vlv." + VLV_INDEX_NAME + ".before", 3));
    backend.storage.afterFirstRead(counterTreeName(),
        () -> backend.storage.answer(counterTreeName(), counterTreeName()));

    final InternalSearchOperation search = searchPeople(
        actorLimitedTo(3), PEOPLE_DN, SearchScope.SINGLE_LEVEL, new VLVRequestControl(0, 0, 1, 0));
    assertThat(search.getResultCode()).isEqualTo(ResultCode.ADMIN_LIMIT_EXCEEDED);
    assertThat(search.getSearchEntries()).isEmpty();
    assertThat(backend.storage.changes).as("changes made during the search").isEqualTo(1);
  }

  /**
   * An applicable VLV index holding no entry answers the search with none, sorted: the search must
   * neither go on as without it, through the attribute indexes, which here still hold the people, nor
   * be refused.
   */
  @Test
  public void sortOverEmptyVLVIndexGivesNoEntry() throws Exception
  {
    final TreeName empty = emptyTree("vlv." + VLV_INDEX_NAME + ".empty");
    backend.storage.answer(vlvIndex.getName(), empty);
    backend.storage.answer(counterTreeName(), empty);

    final InternalSearchOperation search = searchPeople(actorConn, PEOPLE_DN, SearchScope.SINGLE_LEVEL);
    assertThat(search.getSearchEntries()).isEmpty();
    assertThat(sortResultCode(search)).as("result code of the sort response control").isEqualTo(SUCCESS);
  }

  /** The actor, held to the provided lookthrough limit, which an internal connection never holds otherwise. */
  private InternalClientConnection actorLimitedTo(int lookthroughLimit) throws Exception
  {
    final InternalClientConnection conn = new InternalClientConnection(actorConn.getAuthenticationInfo());
    // An internal connection ignores the setter of the limit, which its connection keeps in a field of its own.
    final Field lookthroughLimitField = ClientConnection.class.getDeclaredField("lookthroughLimit");
    lookthroughLimitField.setAccessible(true);
    lookthroughLimitField.setInt(conn, lookthroughLimit);
    return conn;
  }

  /** Sorts the people on "sn", with a critical sort control and the provided controls, straight through the backend. */
  private InternalSearchOperation searchPeople(InternalClientConnection conn, DN baseDN, SearchScope scope,
      Control... controls) throws Exception
  {
    final SearchRequest request =
        newSearchRequest(baseDN, scope, "(sn=*)").addControl(new ServerSideSortRequestControl(true, "sn"));
    for (Control control : controls)
    {
      request.addControl(control);
    }
    final InternalSearchOperation search = new InternalSearchOperation(conn, -1, -1, request);
    backend.search(new LocalBackendSearchOperation(search));
    return search;
  }

  private List<DN> sortedPeople(InternalClientConnection conn, DN baseDN, SearchScope scope) throws Exception
  {
    final List<DN> dns = new ArrayList<>();
    for (SearchResultEntry entry : searchPeople(conn, baseDN, scope).getSearchEntries())
    {
      dns.add(entry.getName());
    }
    return dns;
  }

  /** The result code of the one sort response control of the search. */
  private static int sortResultCode(InternalSearchOperation search)
  {
    final List<Integer> resultCodes = new ArrayList<>();
    for (Control control : search.getResponseControls())
    {
      if (OID_SERVER_SIDE_SORT_RESPONSE_CONTROL.equals(control.getOID()))
      {
        resultCodes.add(((ServerSideSortResponseControl) control).getResultCode());
      }
    }
    assertThat(resultCodes).as("result codes of the sort response controls").hasSize(1);
    return resultCodes.get(0);
  }

  private ByteString entryIDKey(DN dn) throws Exception
  {
    return backend.getRootContainer().getStorage().read(txn -> entryContainer.getDN2ID().get(txn, dn)).toByteString();
  }

  /** The tree the VLV index counts its entries in, named as the VLV index names it. */
  private TreeName counterTreeName()
  {
    return new TreeName(entryContainer.getTreePrefix(), "counter.vlv." + VLV_INDEX_NAME);
  }

  private TreeName emptyTree(String name) throws Exception
  {
    final TreeName tree = new TreeName(entryContainer.getTreePrefix(), name);
    backend.getRootContainer().getStorage().write(txn -> txn.openTree(tree, true));
    return tree;
  }

  /** A tree counting entries as the VLV index counts its own, holding the provided count. */
  private TreeName counterHolding(String name, long count) throws Exception
  {
    final TreeName tree = emptyTree(name);
    // The key the VLV index keeps its count under is its own.
    final Field countKeyField = VLVIndex.class.getDeclaredField("COUNT_KEY");
    countKeyField.setAccessible(true);
    final ByteString countKey = (ByteString) countKeyField.get(null);
    final ShardedCounter counter = new ShardedCounter(tree);
    backend.getRootContainer().getStorage().write(txn -> counter.addCount(txn, countKey, count));
    return tree;
  }

  /** Makes a change from another thread, as a concurrent operation does, and waits until it is done. */
  private static void concurrently(Callable<?> change)
  {
    final FutureTask<?> task = new FutureTask<>(change);
    new Thread(task, "concurrent change").start();
    try
    {
      task.get(30, TimeUnit.SECONDS);
    }
    catch (Exception e)
    {
      throw new AssertionError("the concurrent change failed", e);
    }
  }

  private PDBBackendCfg backendCfg() throws ConfigException
  {
    final PDBBackendCfg cfg = mockCfg(PDBBackendCfg.class);
    when(cfg.dn()).thenReturn(DN.valueOf("ds-cfg-backend-id=" + BACKEND_ID + ",cn=Backends,cn=config"));
    when(cfg.getBackendId()).thenReturn(BACKEND_ID);
    when(cfg.getDBDirectory()).thenReturn(BACKEND_ID);
    when(cfg.getDBDirectoryPermissions()).thenReturn("755");
    when(cfg.getDBCacheSize()).thenReturn(0L);
    when(cfg.getDBCachePercent()).thenReturn(20);
    when(cfg.getBaseDN()).thenReturn(newTreeSet(BASE_DN));
    // Built before they are handed over: stubbing a mock from inside a when() of another mock leaves
    // that when() unfinished.
    final BackendIndexCfg snIndexCfg = snPresenceIndexCfg();
    final BackendVLVIndexCfg vlvIndexCfg = vlvIndexCfg("sn");
    // The index a search goes on with, once it goes on without the VLV index.
    when(cfg.listBackendIndexes()).thenReturn(new String[] { "sn" });
    when(cfg.getBackendIndex("sn")).thenReturn(snIndexCfg);
    when(cfg.listBackendVLVIndexes()).thenReturn(new String[] { VLV_INDEX_NAME });
    when(cfg.getBackendVLVIndex(VLV_INDEX_NAME)).thenReturn(vlvIndexCfg);
    return cfg;
  }

  private BackendIndexCfg snPresenceIndexCfg()
  {
    final BackendIndexCfg cfg = mock(BackendIndexCfg.class);
    when(cfg.getIndexType()).thenReturn(newTreeSet(IndexType.PRESENCE));
    when(cfg.getAttribute()).thenReturn(serverContext.getSchema().getAttributeType("sn"));
    when(cfg.getIndexEntryLimit()).thenReturn(4000);
    when(cfg.getSubstringLength()).thenReturn(6);
    return cfg;
  }

  /** The people sorted on the provided order, which a search of them sorted on "sn" matches as "sn". */
  private BackendVLVIndexCfg vlvIndexCfg(String sortOrder)
  {
    final BackendVLVIndexCfg cfg = mock(BackendVLVIndexCfg.class);
    when(cfg.getName()).thenReturn(VLV_INDEX_NAME);
    when(cfg.getBaseDN()).thenReturn(PEOPLE_DN);
    when(cfg.getScope()).thenReturn(Scope.SINGLE_LEVEL);
    when(cfg.getFilter()).thenReturn("(sn=*)");
    when(cfg.getSortOrder()).thenReturn(sortOrder);
    return cfg;
  }

  /** A PDB backend whose storage is a {@link SteeringStorage}. */
  private static final class SteeringBackend extends BackendImpl<PDBBackendCfg>
  {
    private SteeringStorage storage;

    @Override
    protected Storage configureStorage(PDBBackendCfg cfg, ServerContext serverContext) throws ConfigException
    {
      storage = new SteeringStorage(new PDBStorage(cfg, serverContext));
      return storage;
    }
  }

  /**
   * Decorates a {@link Storage} so that its reads see what a concurrent operation changed after the
   * read began, as reads of the JE backend do: a record deleted, a tree left in another state, which
   * the reads then get from another tree holding that state. A change may also be set to land right
   * after the first cursor a read opens on a given tree.
   */
  private static final class SteeringStorage implements Storage
  {
    private final Storage delegate;
    private final Map<TreeName, TreeName> answeredFrom = new HashMap<>();
    private TreeName deletedFrom;
    private ByteString deletedKey;
    private int readsOfDeleted;
    private TreeName changedAfter;
    private Runnable change;
    private int changes;

    SteeringStorage(Storage delegate)
    {
      this.delegate = delegate;
    }

    void deleteForTheNextReads(TreeName treeName, ByteString key)
    {
      deletedFrom = treeName;
      deletedKey = key;
    }

    /** Answers the reads of a tree from now on from another tree, which holds the state it was left in. */
    void answer(TreeName treeName, TreeName from)
    {
      answeredFrom.put(treeName, from);
    }

    void afterFirstRead(TreeName treeName, Runnable change)
    {
      this.changedAfter = treeName;
      this.change = change;
    }

    private TreeName answering(TreeName treeName)
    {
      final TreeName from = answeredFrom.get(treeName);
      return from != null ? from : treeName;
    }

    @Override
    public <T> T read(final ReadOperation<T> readOperation) throws Exception
    {
      return delegate.read(txn -> readOperation.run(new ReadableTransaction()
      {
        @Override
        public ByteString read(TreeName treeName, ByteSequence key)
        {
          if (treeName.equals(deletedFrom) && deletedKey.equals(key))
          {
            readsOfDeleted++;
            return null;
          }
          return txn.read(answering(treeName), key);
        }

        @Override
        public Cursor<ByteString, ByteString> openCursor(TreeName treeName)
        {
          final Cursor<ByteString, ByteString> cursor = txn.openCursor(answering(treeName));
          if (change != null && treeName.equals(changedAfter))
          {
            final Runnable landing = change;
            change = null;
            changes++;
            landing.run();
          }
          return cursor;
        }

        @Override
        public long getRecordCount(TreeName treeName)
        {
          return txn.getRecordCount(answering(treeName));
        }

        @Override
        public boolean treeExists(TreeName treeName)
        {
          return txn.treeExists(answering(treeName));
        }
      }));
    }

    @Override
    public void write(WriteOperation writeOperation) throws Exception
    {
      delegate.write(writeOperation);
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
