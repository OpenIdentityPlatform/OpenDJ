/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.opends.server.backends.pluggable.State.IndexFlag.*;

import java.util.UUID;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.PDBBackendCfg;
import org.opends.server.backends.pdb.PDBStorage;
import org.opends.server.backends.pluggable.State.IndexFlag;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage.AccessMode;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.MemoryQuota;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class StateTest extends DirectoryServerTestCase
{
  private static final IndexFlag DEFAULT_FLAG = COMPACTED;

  private final TreeName stateTreeName = new TreeName("base-dn", "index-id");
  private TreeName indexTreeName;
  private PDBStorage storage;
  private State state;

  @BeforeClass
  public void startServer() throws Exception {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void stopServer() throws Exception {
    TestCaseUtils.shutdownFakeServer();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
    indexTreeName = new TreeName("index-base-dn", "index-index-id-" + UUID.randomUUID().toString());

    ServerContext serverContext = mock(ServerContext.class);
    when(serverContext.getMemoryQuota()).thenReturn(new MemoryQuota());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));

    storage = new PDBStorage(createBackendCfg(), serverContext);
    try(final org.opends.server.backends.pluggable.spi.Importer importer = storage.startImport()) {
      importer.createTree(stateTreeName);
    }

    storage.open(AccessMode.READ_WRITE);

    state = new State(stateTreeName);
  }

  @AfterMethod
  public void tearDown() {
    storage.close();
    storage.removeStorageFiles();
  }

  @Test
  public void testDefaultValuesForNotExistingEntries() throws Exception
  {
    assertThat(getFlags()).containsExactly(DEFAULT_FLAG);
  }

  @Test
  public void testCreateNewFlagHasDefaultValue() throws Exception
  {
    addFlags();
    assertThat(getFlags()).containsExactly(DEFAULT_FLAG);
  }

  @Test
  public void testCreateStateTrustedIsAlsoCompacted() throws Exception
  {
    addFlags(TRUSTED);
    assertThat(getFlags()).containsExactly(TRUSTED, DEFAULT_FLAG);
  }

  @Test
  public void testCreateWithTrustedAndCompacted() throws Exception
  {
    addFlags(TRUSTED, COMPACTED);
    assertThat(getFlags()).containsExactly(TRUSTED, COMPACTED);
  }

  @Test
  public void testUpdateNotSetDefault() throws Exception
  {
    createFlagWith();

    addFlags(TRUSTED);
    assertThat(getFlags()).containsExactly(TRUSTED);
  }

  @Test
  public void testAddFlags() throws Exception
  {
    createFlagWith(TRUSTED);

    addFlags(COMPACTED);
    assertThat(getFlags()).containsExactly(TRUSTED, COMPACTED);
  }

  @Test
  public void testRemoveFlags() throws Exception
  {
    addFlags(COMPACTED, TRUSTED);
    assertThat(getFlags()).containsExactly(TRUSTED, COMPACTED);

    removeFlags(TRUSTED);
    assertThat(getFlags()).containsExactly(COMPACTED);

    removeFlags(COMPACTED);
    assertThat(getFlags()).containsExactly();
  }

  @Test
  public void testDeleteRecord() throws Exception
  {
    addFlags(COMPACTED, TRUSTED);

    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        state.deleteRecord(txn, indexTreeName);
      }
    });

    assertThat(getFlags()).containsExactly(COMPACTED);
  }

  private PDBBackendCfg createBackendCfg() throws ConfigException, DirectoryException
  {
    String homeDirName = "pdb_test";
    PDBBackendCfg backendCfg = mock(PDBBackendCfg.class);

    when(backendCfg.getBackendId()).thenReturn("persTest" + homeDirName);
    when(backendCfg.getDBDirectory()).thenReturn(homeDirName);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getBaseDN()).thenReturn(TestCaseUtils.newSortedSet(DN.valueOf("dc=test,dc=com")));
    when(backendCfg.dn()).thenReturn(DN.valueOf("dc=test,dc=com"));
    when(backendCfg.listBackendIndexes()).thenReturn(new String[] { "sn" });
    when(backendCfg.listBackendVLVIndexes()).thenReturn(new String[0]);

    BackendIndexCfg indexCfg = mock(BackendIndexCfg.class);
    when(indexCfg.getIndexType()).thenReturn(TestCaseUtils.newSortedSet(IndexType.PRESENCE, IndexType.EQUALITY));
    when(indexCfg.getAttribute()).thenReturn(DirectoryServer.getAttributeType("sn"));
    when(backendCfg.getBackendIndex("sn")).thenReturn(indexCfg);

    return backendCfg;
  }

  private void createFlagWith(IndexFlag... flags) throws Exception
  {
    createEmptyFlag();
    addFlags(flags);
  }

  private void createEmptyFlag() throws Exception {
    removeFlags(DEFAULT_FLAG);
  }

  private void addFlags(final IndexFlag... flags) throws Exception
  {
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        state.addFlagsToIndex(txn, indexTreeName, flags);
      }
    });
  }

  private void removeFlags(final IndexFlag... flags) throws Exception
  {
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        state.removeFlagsFromIndex(txn, indexTreeName, flags);
      }
    });
  }

  private IndexFlag[] getFlags() throws Exception
  {
    return storage.read(new ReadOperation<IndexFlag[]>()
    {
      @Override
      public IndexFlag[] run(ReadableTransaction txn) throws Exception
      {
        return state.getIndexFlags(txn, indexTreeName).toArray(new IndexFlag[0]);
      }
    });
  }
}
