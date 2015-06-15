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
import static org.opends.server.ConfigurationMock.legacyMockCfg;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.PromiseImpl;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.PDBBackendCfg;
import org.opends.server.backends.pdb.PDBStorage;
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
public class ID2CountTest extends DirectoryServerTestCase
{
  private final TreeName id2CountTreeName = new TreeName("base-dn", "index-id");
  private ExecutorService parallelExecutor;
  private ID2Count id2Count;
  private PDBStorage storage;

  @BeforeClass
  public void startFakeServer() throws Exception {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void stopFakeServer() throws Exception {
    TestCaseUtils.shutdownFakeServer();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
    ServerContext serverContext = mock(ServerContext.class);
    when(serverContext.getMemoryQuota()).thenReturn(new MemoryQuota());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));

    storage = new PDBStorage(createBackendCfg(), serverContext);
    try(final org.opends.server.backends.pluggable.spi.Importer importer = storage.startImport()) {
      importer.createTree(id2CountTreeName);
    }

    storage.open(AccessMode.READ_WRITE);

    id2Count = new ID2Count(id2CountTreeName);

    parallelExecutor = Executors.newFixedThreadPool(32);
  }

  @AfterMethod
  public void tearDown() {
    storage.close();
    storage.removeStorageFiles();
  }

  @Test
  public void testConcurrentAddDelta() throws Exception
  {
    final long expected = stressCounter(8192, id(1), parallelExecutor);
    waitExecutorTermination();

    assertThat(getCounter(id(1))).isEqualTo(expected);
    assertThat(getTotalCounter()).isEqualTo(expected);
  }

  @Test
  public void testConcurrentTotalCounter() throws Exception
  {
    long totalExpected = 0;
    for(int i = 0 ; i < 64 ; i++) {
      totalExpected += stressCounter(128, id(i), parallelExecutor);
    }
    waitExecutorTermination();

    assertThat(getTotalCounter()).isEqualTo(totalExpected);
  }

  @Test
  public void testDeleteCounterDecrementTotalCounter() throws Exception
  {
    addDelta(id(0), 1024);
    addDelta(id(1), 1024);
    addDelta(id(2), 1024);
    addDelta(id(3), 1024);
    assertThat(getTotalCounter()).isEqualTo(4096);

    assertThat(deleteCount(id(0))).isEqualTo(1024);
    assertThat(getTotalCounter()).isEqualTo(3072);

    assertThat(deleteCount(id(1))).isEqualTo(1024);
    assertThat(deleteCount(id(2))).isEqualTo(1024);
    assertThat(deleteCount(id(3))).isEqualTo(1024);
    assertThat(getTotalCounter()).isEqualTo(0);
  }

  @Test
  public void testGetCounterNonExistingKey() throws Exception
  {
      assertThat(getCounter(id(987654))).isEqualTo(0);
  }

  private void waitExecutorTermination() throws InterruptedException
  {
    parallelExecutor.shutdown();
    parallelExecutor.awaitTermination(30, TimeUnit.SECONDS);
  }

  private long stressCounter(final int numIterations, final EntryID key, final ExecutorService exec)
  {
    final Random r = new Random();
    long expected = 0;
    for(int i = 0 ; i < numIterations ; i++) {
      final long delta = r.nextLong();
      expected += delta;

      exec.submit(new Callable<Void>()
      {
        @Override
        public Void call() throws Exception
        {
          addDelta(key, delta);
          return null;
        }
      });
    }
    return expected;
  }

  private long deleteCount(final EntryID key) throws Exception {
    final PromiseImpl<Long, NeverThrowsException> l = PromiseImpl.create();
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        l.handleResult(id2Count.deleteCount(txn, key));
      }
    });
    return l.get();
  }

  private void addDelta(final EntryID key, final long delta) throws Exception {
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        id2Count.addDelta(txn, key, delta);
      }
    });
  }

  private long getCounter(final EntryID key) throws Exception {
    return storage.read(new ReadOperation<Long>()
    {
      @Override
      public Long run(ReadableTransaction txn) throws Exception
      {
        return id2Count.getCount(txn, key);
      }
    });
  }

  private long getTotalCounter() throws Exception {
    return storage.read(new ReadOperation<Long>()
    {
      @Override
      public Long run(ReadableTransaction txn) throws Exception
      {
        return id2Count.getTotalCount(txn);
      }
    });
  }

  public static EntryID id(long id) {
    return new EntryID(id);
  }

  private PDBBackendCfg createBackendCfg() throws ConfigException, DirectoryException
  {
    String homeDirName = "pdb_test";
    PDBBackendCfg backendCfg = legacyMockCfg(PDBBackendCfg.class);

    when(backendCfg.getBackendId()).thenReturn("persTest" + homeDirName);
    when(backendCfg.getDBDirectory()).thenReturn(homeDirName);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getBaseDN()).thenReturn(TestCaseUtils.newSortedSet(DN.valueOf("dc=test,dc=com")));
    when(backendCfg.dn()).thenReturn(DN.valueOf("dc=test,dc=com"));
    when(backendCfg.listBackendIndexes()).thenReturn(new String[] { "sn" });
    when(backendCfg.listBackendVLVIndexes()).thenReturn(new String[0]);

    BackendIndexCfg indexCfg = legacyMockCfg(BackendIndexCfg.class);
    when(indexCfg.getIndexType()).thenReturn(TestCaseUtils.newSortedSet(IndexType.PRESENCE, IndexType.EQUALITY));
    when(indexCfg.getAttribute()).thenReturn(DirectoryServer.getAttributeType("sn"));
    when(backendCfg.getBackendIndex("sn")).thenReturn(indexCfg);

    return backendCfg;
  }

}
