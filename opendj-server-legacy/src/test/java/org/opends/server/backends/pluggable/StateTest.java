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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.config.ConfigurationMock.*;
import static org.mockito.Mockito.*;
import static org.opends.server.backends.pluggable.State.IndexFlag.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.UUID;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pdb.PDBStorage;
import org.opends.server.backends.pluggable.State.IndexFlag;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.MemoryQuota;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
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
    TestCaseUtils.startServer();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
    indexTreeName = new TreeName("index-base-dn", "index-index-id-" + UUID.randomUUID());

    ServerContext serverContext = mock(ServerContext.class);
    when(serverContext.getMemoryQuota()).thenReturn(new MemoryQuota());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));

    storage = new PDBStorage(createBackendCfg(), serverContext);
    storage.open(AccessMode.READ_WRITE);
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.openTree(stateTreeName, true);
      }
    });

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
    PDBBackendCfg backendCfg = mockCfg(PDBBackendCfg.class);

    when(backendCfg.getBackendId()).thenReturn("persTest" + homeDirName);
    when(backendCfg.getDBDirectory()).thenReturn(homeDirName);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getBaseDN()).thenReturn(newTreeSet(DN.valueOf("dc=test,dc=com")));
    when(backendCfg.dn()).thenReturn(DN.valueOf("dc=test,dc=com"));
    when(backendCfg.listBackendIndexes()).thenReturn(new String[] { "sn" });
    when(backendCfg.listBackendVLVIndexes()).thenReturn(new String[0]);

    BackendIndexCfg indexCfg = mockCfg(BackendIndexCfg.class);
    when(indexCfg.getIndexType()).thenReturn(newTreeSet(IndexType.PRESENCE, IndexType.EQUALITY));
    when(indexCfg.getAttribute()).thenReturn(CoreSchema.getSNAttributeType());
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
