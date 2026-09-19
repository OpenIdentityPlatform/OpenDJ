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
import static org.opends.messages.BackendMessages.NOTE_CONFIG_DB_CACHE_REQUIRES_RESTART;
import static org.opends.server.util.StaticUtils.MB;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
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
  /** A cache size the quota of the test JVM grants several times over, in bytes. */
  private static final long SMALL_CACHE = 64L * MB;

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

  /**
   * A cache size changed while the storage is open is given back as it was taken: the close
   * releases what the open reserved, not what the configuration says by then. Read from the
   * configuration at both ends, a change in between drifts the quota by the difference for the
   * life of the JVM - the open which follows reserves the new size and pays nothing back.
   */
  @Test
  public void aCacheGrownWhileOpenIsGivenBackAsItWasTaken() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    final long availableBefore = quota.getAvailableMemory();
    storage = new JEStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore - SMALL_CACHE);

    storage.applyConfigurationChange(createBackendCfg(2 * SMALL_CACHE));
    storage.close();

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
  }

  /** The shrink is the same drift the other way: the difference stays reserved by nobody. */
  @Test
  public void aCacheShrunkWhileOpenIsGivenBackAsItWasTaken() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    final long availableBefore = quota.getAvailableMemory();
    storage = new JEStorage(createBackendCfg(2 * SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);

    storage.applyConfigurationChange(createBackendCfg(SMALL_CACHE));
    storage.close();

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
  }

  /**
   * The cache is sized when the environment opens and this storage never resizes it, so a change
   * of the cache size is applied by the next open of the backend - and the operator is told so,
   * rather than that the change applied.
   */
  @Test
  public void aCacheSizeChangedWhileOpenAsksForARestart() throws Exception
  {
    closeAndRemove(storage);
    storage = new JEStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);

    final ConfigChangeResult ccr = storage.applyConfigurationChange(createBackendCfg(2 * SMALL_CACHE));

    assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isTrue();
    assertThat(ccr.getMessages()).hasSize(1);
    assertThat(ccr.getMessages().get(0).ordinal()).isEqualTo(NOTE_CONFIG_DB_CACHE_REQUIRES_RESTART.ordinal());
    assertThat(ccr.getMessages().get(0).toString()).isEqualTo(
        NOTE_CONFIG_DB_CACHE_REQUIRES_RESTART.get(BACKEND_ID, SMALL_CACHE, 2 * SMALL_CACHE).toString());
  }

  /** A change which leaves the cache size alone asks for nothing, as before. */
  @Test
  public void aChangeWhichLeavesTheCacheSizeAloneAsksForNothing() throws Exception
  {
    closeAndRemove(storage);
    storage = new JEStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    final JEBackendCfg unchangedCache = createBackendCfg(SMALL_CACHE);
    when(unchangedCache.isDBTxnNoSync()).thenReturn(true);

    final ConfigChangeResult ccr = storage.applyConfigurationChange(unchangedCache);

    assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isFalse();
    assertThat(ccr.getMessages()).isEmpty();
  }

  /**
   * A change of the cache size is admitted against what the storage holds of the quota, which is
   * what the next open has to add to. Once a change has been admitted but not applied, the
   * configuration says the new size while the reservation is still the old one, and a check
   * against the configuration would admit a second change the server has no memory for.
   */
  @Test
  public void aCacheSizeChangeIsAdmittedAgainstWhatTheStorageHolds() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    storage = new JEStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    storage.applyConfigurationChange(createBackendCfg(2 * SMALL_CACHE));
    // Room for two caches and a bit: the difference to the configured size, not to the reserved one.
    assertThat(quota.acquireMemory(quota.getAvailableMemory() - 2 * SMALL_CACHE - MB)).isTrue();

    final List<LocalizableMessage> reasons = new ArrayList<>();
    assertThat(storage.isConfigurationChangeAcceptable(createBackendCfg(4 * SMALL_CACHE), reasons))
        .as("four caches, with one reserved and two and a bit free").isFalse();
    assertThat(storage.isConfigurationChangeAcceptable(createBackendCfg(3 * SMALL_CACHE), reasons))
        .as("three caches, with one reserved and two and a bit free").isTrue();
  }

  /**
   * A reservation the quota refused is not given back on close. The open goes ahead without it -
   * the quota is a budget, not a lock - but a close which released what was never taken would
   * hand the quota memory the server does not have.
   */
  @Test
  public void aReservationTheQuotaRefusedIsNotGivenBackOnClose() throws Exception
  {
    final MemoryQuota quota = serverContext.getMemoryQuota();
    closeAndRemove(storage);
    // Half a cache left in the quota: the reservation of a whole one is refused.
    assertThat(quota.acquireMemory(quota.getAvailableMemory() - SMALL_CACHE / 2)).isTrue();
    final long availableBefore = quota.getAvailableMemory();
    storage = new JEStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);

    storage.close();

    assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
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
    return createBackendCfg(0L);
  }

  /** A configuration whose cache is the given size in bytes, or a fifth of the quota when it is zero. */
  private static JEBackendCfg createBackendCfg(long cacheSize)
  {
    final JEBackendCfg backendCfg = mockCfg(JEBackendCfg.class);
    when(backendCfg.dn()).thenReturn(DN.valueOf("ds-cfg-backend-id=" + BACKEND_ID + ",cn=Backends,cn=config"));
    when(backendCfg.getBackendId()).thenReturn(BACKEND_ID);
    when(backendCfg.getDBDirectory()).thenReturn(BACKEND_ID);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(cacheSize);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getDBNumCleanerThreads()).thenReturn(2);
    when(backendCfg.getDBNumLockTables()).thenReturn(63);
    return backendCfg;
  }
}
