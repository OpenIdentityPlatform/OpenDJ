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
package org.opends.server.backends.jeb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.forgerock.opendj.ldap.ByteString.valueOfUtf8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.JEBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
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

/**
 * Tests what a {@link JEStorage} takes as it opens and gives back when the open fails - the twin
 * of the same cases on {@code PDBStorageTest}.
 */
@SuppressWarnings("javadoc")
public class JEStorageTest extends DirectoryServerTestCase
{
  private static final String BACKEND_ID = "JEStorageTest";
  /**
   * A parent directory under which the storage's own directory is a regular file, so that the open
   * fails once its configuration is built - the memory reserved - and before the environment is:
   * what a backend whose directory the server cannot use meets.
   */
  private static final String BLOCKED_DB_DIRECTORY = BACKEND_ID + "-blocked";

  private final TreeName treeName = new TreeName("dc=test", "test");
  private ServerContext serverContext;
  private JEStorage storage;

  @BeforeClass
  public static void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
    serverContext = mock(ServerContext.class);
    when(serverContext.getMemoryQuota()).thenReturn(new MemoryQuota());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));

    storage = new JEStorage(createBackendCfg(), serverContext);
    // the environment is removed on the way in as well as on the way out: a build whose JVM died never ran
    // tearDown(), and this class shares a fixed db-directory across methods and across builds
    storage.removeStorageFiles();
    storage.open(AccessMode.READ_WRITE);
  }

  @AfterMethod
  public void tearDown()
  {
    closeAndRemove(storage);
  }

  /**
   * Closes the storage and removes its environment, keeping whichever of the two failed first. Removing it
   * from a finally would let a removal failure replace the close() failure (JLS 14.20.2) - and a close() that
   * throws is exactly the case the removal is here for.
   */
  private static void closeAndRemove(JEStorage storage)
  {
    RuntimeException failure = null;
    try
    {
      storage.close();
    }
    catch (RuntimeException e)
    {
      failure = e;
    }
    try
    {
      storage.removeStorageFiles();
    }
    catch (RuntimeException e)
    {
      if (failure == null)
      {
        failure = e;
      }
      else
      {
        failure.addSuppressed(e);
      }
    }
    if (failure != null)
    {
      throw failure;
    }
  }

  /**
   * An open which fails gives back what it took before it failed: the memory it reserved for the
   * cache, and the listener the constructor registered on the backend configuration. Nothing else
   * will - a root container does not close a storage which did not open - and a backend whose
   * directory the server cannot use is enabled again and again, each attempt draining one cache
   * size.
   */
  @Test
  public void aStorageWhoseOpenFailedGivesBackWhatItTook() throws Exception
  {
    final JEBackendCfg cfg = createBackendCfg();
    final JEStorage second = blockedStorage(cfg);
    final MemoryQuota quota = serverContext.getMemoryQuota();
    final long availableBefore = quota.getAvailableMemory();
    try
    {
      second.open(AccessMode.READ_WRITE);
      fail("the storage was expected not to open over a directory which is a file");
    }
    catch (ConfigException expected)
    {
      // What a backend directory the server cannot use does.
    }
    finally
    {
      unblock(second);
    }

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
    verify(cfg).removeJEChangeListener(second);
  }

  /**
   * A storage whose open failed has given everything back already, so closing it afterwards takes
   * nothing more - {@code BackendImpl.importLDIF} closes the storage of its root container however
   * the import ended - and does not fail on what the open never got to.
   */
  @Test
  public void closingAStorageWhoseOpenFailedTakesNothingMore() throws Exception
  {
    final JEStorage second = blockedStorage(createBackendCfg());
    final MemoryQuota quota = serverContext.getMemoryQuota();
    final long availableBefore = quota.getAvailableMemory();
    try
    {
      second.open(AccessMode.READ_WRITE);
      fail("the storage was expected not to open over a directory which is a file");
    }
    catch (ConfigException expected)
    {
      // What a backend directory the server cannot use does.
    }
    finally
    {
      unblock(second);
    }

    second.close();

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
  }

  /**
   * A storage which is open refuses to open again before it takes anything, and what it holds is
   * left as it is: the refusal is a guard against a programming error, not a failed open with
   * something to give back.
   */
  @Test
  public void openingAnOpenStorageIsRefusedAndTakesNothing() throws Exception
  {
    createTree();
    final MemoryQuota quota = serverContext.getMemoryQuota();
    final long availableBefore = quota.getAvailableMemory();
    try
    {
      storage.open(AccessMode.READ_WRITE);
      fail("a storage which is open was expected to refuse to open again");
    }
    catch (IllegalStateException expected)
    {
      // The guard against a double open.
    }

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
    // Still open: a read reaches the environment.
    assertThat(read("missing")).isNull();
  }

  /** A storage whose directory is a regular file, which no open of it can use. */
  private JEStorage blockedStorage(JEBackendCfg cfg) throws Exception
  {
    when(cfg.getDBDirectory()).thenReturn(BLOCKED_DB_DIRECTORY);
    final JEStorage blocked = new JEStorage(cfg, serverContext);
    final File directory = blocked.getDirectory();
    directory.getParentFile().mkdirs();
    if (!directory.isFile())
    {
      assertThat(directory.createNewFile()).as("the file in the way of %s", directory).isTrue();
    }
    return blocked;
  }

  /** Removes the file in the way of the given storage's directory, and the directory it was made in. */
  private static void unblock(JEStorage blocked)
  {
    final File directory = blocked.getDirectory();
    directory.delete();
    directory.getParentFile().delete();
  }

  private void createTree() throws Exception
  {
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.openTree(treeName, true);
      }
    });
  }

  private ByteString read(final String key) throws Exception
  {
    return storage.read(new ReadOperation<ByteString>()
    {
      @Override
      public ByteString run(ReadableTransaction txn) throws Exception
      {
        return txn.read(treeName, valueOfUtf8(key));
      }
    });
  }

  private static JEBackendCfg createBackendCfg()
  {
    final JEBackendCfg backendCfg = mockCfg(JEBackendCfg.class);
    when(backendCfg.dn()).thenReturn(DN.valueOf("ds-cfg-backend-id=" + BACKEND_ID + ",cn=Backends,cn=config"));
    when(backendCfg.getBackendId()).thenReturn(BACKEND_ID);
    when(backendCfg.getDBDirectory()).thenReturn(BACKEND_ID);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getDBNumCleanerThreads()).thenReturn(2);
    when(backendCfg.getDBNumLockTables()).thenReturn(63);
    return backendCfg;
  }
}
