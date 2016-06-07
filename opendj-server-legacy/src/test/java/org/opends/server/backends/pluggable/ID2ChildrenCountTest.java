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
import static org.opends.server.util.CollectionUtils.*;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.PromiseImpl;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pdb.PDBStorage;
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
public class ID2ChildrenCountTest extends DirectoryServerTestCase
{
  private final TreeName id2CountTreeName = new TreeName("base-dn", "index-id");
  private ExecutorService parallelExecutor;
  private ID2ChildrenCount id2ChildrenCount;
  private PDBStorage storage;

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
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
        txn.openTree(id2CountTreeName, true);
      }
    });

    id2ChildrenCount = new ID2ChildrenCount(id2CountTreeName);

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
    updateCount(id(0), 1024);
    updateCount(id(1), 1024);
    updateCount(id(2), 1024);
    updateCount(id(3), 1024);
    assertThat(getTotalCounter()).isEqualTo(4096);

    assertThat(removeCount(id(0))).isEqualTo(1024);
    assertThat(getTotalCounter()).isEqualTo(3072);

    assertThat(removeCount(id(1))).isEqualTo(1024);
    assertThat(removeCount(id(2))).isEqualTo(1024);
    assertThat(removeCount(id(3))).isEqualTo(1024);
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
          updateCount(key, delta);
          return null;
        }
      });
    }
    return expected;
  }

  private long removeCount(final EntryID key) throws Exception {
    final PromiseImpl<Long, NeverThrowsException> l = PromiseImpl.create();
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        final long delta = id2ChildrenCount.removeCount(txn, key);
        id2ChildrenCount.updateTotalCount(txn, -delta);
        l.handleResult(delta);
      }
    });
    return l.get();
  }

  private void updateCount(final EntryID key, final long delta) throws Exception {
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        id2ChildrenCount.updateCount(txn, key, delta);
        id2ChildrenCount.updateTotalCount(txn, delta);
      }
    });
  }

  private long getCounter(final EntryID key) throws Exception {
    return storage.read(new ReadOperation<Long>()
    {
      @Override
      public Long run(ReadableTransaction txn) throws Exception
      {
        return id2ChildrenCount.getCount(txn, key);
      }
    });
  }

  private long getTotalCounter() throws Exception {
    return storage.read(new ReadOperation<Long>()
    {
      @Override
      public Long run(ReadableTransaction txn) throws Exception
      {
        return id2ChildrenCount.getTotalCount(txn);
      }
    });
  }

  public static EntryID id(long id) {
    return new EntryID(id);
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
}
