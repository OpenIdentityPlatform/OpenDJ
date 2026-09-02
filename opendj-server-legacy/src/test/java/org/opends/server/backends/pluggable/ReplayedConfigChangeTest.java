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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opends.server.backends.pluggable.State.IndexFlag.TRUSTED;
import static org.opends.server.backends.pluggable.SuffixContainer.STATE_INDEX_NAME;
import static org.opends.server.util.CollectionUtils.newTreeSet;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pdb.PDBStorage;
import org.opends.server.backends.pluggable.State.IndexFlag;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
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
    for (DN baseDN : new DN[] { KEPT, REMOVED, ADDED })
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

  private static Set<TreeName> treesOf(EntryContainer ec)
  {
    final Set<TreeName> names = new HashSet<>();
    for (Tree tree : ec.listTrees())
    {
      names.add(tree.getName());
    }
    return names;
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
    final ReplayingBackend backend = new ReplayingBackend();
    backend.setBackendID(BACKEND_ID);
    backend.configuredWith = backendCfg(baseDNs);
    backend.configureBackend(backend.configuredWith, serverContext);
    // Start from a pristine on-disk state so that a previous run cannot mask the defect.
    backend.storage.removeStorageFiles();
    backend.openBackend();
    return backend;
  }

  private PDBBackendCfg backendCfg(SortedSet<DN> baseDNs) throws ConfigException
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
    when(cfg.listBackendVLVIndexes()).thenReturn(new String[0]);

    final BackendIndexCfg indexCfg = mock(BackendIndexCfg.class);
    when(indexCfg.getIndexType()).thenReturn(newTreeSet(IndexType.EQUALITY));
    when(indexCfg.getAttribute()).thenReturn(cnType);
    when(indexCfg.getIndexEntryLimit()).thenReturn(4000);
    when(indexCfg.getSubstringLength()).thenReturn(6);
    when(cfg.getBackendIndex("cn")).thenReturn(indexCfg);
    return cfg;
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
      NO_REPLAY
    }

    private final Storage delegate;
    private ConflictPoint conflictPoint;
    private int conflictsLeft;
    private int attempts;

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

    void failWithoutReplay()
    {
      arm(ConflictPoint.NO_REPLAY, 1);
    }

    private void arm(ConflictPoint where, int conflicts)
    {
      conflictPoint = where;
      conflictsLeft = conflicts;
      attempts = 0;
    }

    /** How many times the armed operation was run, the first attempt included. */
    int attempts()
    {
      return attempts;
    }

    @Override
    public void write(final WriteOperation writeOperation) throws Exception
    {
      final ConflictPoint armed = conflictPoint;
      if (armed == null)
      {
        delegate.write(writeOperation);
        return;
      }
      conflictPoint = null;
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
