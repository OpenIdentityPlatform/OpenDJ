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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.backends.pdb;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.forgerock.opendj.config.ConfigurationMock.*;
import static org.opends.server.util.StaticUtils.*;
import static org.forgerock.opendj.ldap.ByteString.*;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.MemoryQuota;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.persistit.Exchange;

public class PDBStorageTest extends DirectoryServerTestCase
{
  private final TreeName treeName = new TreeName("dc=test", "test");
  private PDBStorage storage;

  @BeforeClass
  public static void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @BeforeMethod
  public void setUp() throws ConfigException
  {
    ServerContext serverContext = mock(ServerContext.class);
    when(serverContext.getMemoryQuota()).thenReturn(new MemoryQuota());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));

    storage = new PDBStorage(createBackendCfg(), serverContext);
    storage.open(AccessMode.READ_WRITE);
  }

  @AfterMethod
  public void tearDown()
  {
    storage.close();
  }

  @Test
  public void testCanAddLargeValues() throws Exception
  {
    storage.write(new WriteOperation()
    {
      private final TreeName treeName = new TreeName("dc=test", "test");

      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.openTree(treeName, true);
        txn.put(treeName, valueOfUtf8("4mb"), valueOfBytes(new byte[4 * MB]));
        txn.put(treeName, valueOfUtf8("32mb"), valueOfBytes(new byte[32 * MB]));
        // 64Mb is the maximum allowed for value size. But Persistit has header reducing the payload.
        txn.put(treeName, valueOfUtf8("64mb"), valueOfBytes(new byte[63 * MB]));
      }
    });
  }

  @Test
  public void testExchangeWithSmallValuesAreReleasedToPool() throws Exception
  {
    final Exchange initial = storage.getNewExchange(treeName, true);
    storage.releaseExchange(initial);

    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.put(treeName, valueOfUtf8("small"), valueOfBytes(new byte[512 * KB]));
      }
    });

    assertThat(storage.getNewExchange(treeName, true)).isSameAs(initial);
  }

  @Test
  public void testExchangeWithLargeValuesAreNotReleasedToPool() throws Exception
  {
    final Exchange initial = storage.getNewExchange(treeName, true);
    storage.releaseExchange(initial);

    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.put(treeName, valueOfUtf8("small"), valueOfBytes(new byte[16 * MB]));
      }
    });

    assertThat(storage.getNewExchange(treeName, true)).isNotSameAs(initial);
  }

  protected PDBBackendCfg createBackendCfg()
  {
    PDBBackendCfg backendCfg = mockCfg(PDBBackendCfg.class);
    when(backendCfg.getBackendId()).thenReturn("PDBStorageTest");
    when(backendCfg.getDBDirectory()).thenReturn("PDBStorageTest");
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    return backendCfg;
  }

}
