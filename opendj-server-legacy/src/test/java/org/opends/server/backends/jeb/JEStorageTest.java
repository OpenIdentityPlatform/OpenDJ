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

import static com.sleepycat.je.EnvironmentConfig.CLEANER_MIN_AGE;
import static com.sleepycat.je.EnvironmentConfig.CLEANER_MIN_UTILIZATION;
import static com.sleepycat.je.EnvironmentConfig.CLEANER_THREADS;
import static com.sleepycat.je.EnvironmentConfig.ENV_RUN_CLEANER;
import static com.sleepycat.je.EnvironmentConfig.EVICTOR_CORE_THREADS;
import static com.sleepycat.je.EnvironmentConfig.EVICTOR_KEEP_ALIVE;
import static com.sleepycat.je.EnvironmentConfig.EVICTOR_MAX_THREADS;
import static com.sleepycat.je.EnvironmentConfig.LOG_FILE_MAX;
import static com.sleepycat.je.EnvironmentConfig.LOG_ITERATOR_READ_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.forgerock.opendj.ldap.ByteString.valueOfUtf8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opends.messages.BackendMessages.ERR_CONFIG_JEB_DURABILITY_CONFLICT;
import static org.opends.messages.BackendMessages.NOTE_CONFIG_DB_CACHE_REQUIRES_RESTART;
import static org.opends.messages.BackendMessages.NOTE_CONFIG_DB_PROPERTY_REQUIRES_RESTART;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_JE_PROPERTY_INVALID;
import static org.opends.server.util.StaticUtils.MB;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

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
import org.opends.server.backends.pluggable.spi.Importer;
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

import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentMutableConfig;

/**
 * Tests what a {@link JEStorage} takes as it opens and gives back when the open fails - the twin
 * of the same cases on {@code PDBStorageTest} - and what a configuration change reaches of the
 * environment it runs.
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
    when(unchangedCache.isDBTxnWriteNoSync()).thenReturn(false);

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

  /**
   * What JE takes while it runs - the cleaner, the evictor pool and the durability - is applied
   * to the running environment by a configuration change, and the operator is asked for nothing.
   * Built at the open and never touched again, the environment ran on unchanged until the next
   * open while the change was reported as applied.
   */
  @Test
  public void aChangeOfWhatJETakesWhileItRunsReachesTheEnvironment() throws Exception
  {
    final Environment env = environmentOf(storage);
    assertThat(env.getMutableConfig().getConfigParam(CLEANER_MIN_UTILIZATION)).isEqualTo("50");

    final JEBackendCfg cfg = createBackendCfg();
    when(cfg.getDBCleanerMinUtilization()).thenReturn(60);
    when(cfg.isDBRunCleaner()).thenReturn(false);
    when(cfg.getDBEvictorCoreThreads()).thenReturn(2);
    when(cfg.getDBEvictorMaxThreads()).thenReturn(4);
    when(cfg.getDBEvictorKeepAlive()).thenReturn(120L);
    when(cfg.getDBNumCleanerThreads()).thenReturn(3);
    final ConfigChangeResult ccr = storage.applyConfigurationChange(cfg);

    assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isFalse();
    assertThat(ccr.getMessages()).isEmpty();
    final EnvironmentMutableConfig running = env.getMutableConfig();
    assertThat(running.getConfigParam(CLEANER_MIN_UTILIZATION)).isEqualTo("60");
    assertThat(running.getConfigParam(ENV_RUN_CLEANER)).isEqualTo("false");
    assertThat(running.getConfigParam(EVICTOR_CORE_THREADS)).isEqualTo("2");
    assertThat(running.getConfigParam(EVICTOR_MAX_THREADS)).isEqualTo("4");
    // a JE duration, in microseconds
    assertThat(running.getConfigParam(EVICTOR_KEEP_ALIVE)).isEqualTo("120000000");
    assertThat(running.getConfigParam(CLEANER_THREADS)).isEqualTo("3");
  }

  /**
   * The durability follows the change every way. It is what the transactions of this storage
   * commit with, taken from the environment handle: a change set on the handle reaches the next
   * transaction - a change which sets neither flag included, which commits synchronously, set as
   * such rather than left to what JE falls back on, since JE leaves a durability it has in place
   * when it is handed none.
   */
  @Test
  public void aChangeOfTheDurabilityReachesTheEnvironmentEveryWay() throws Exception
  {
    final Environment env = environmentOf(storage);
    // db-txn-write-no-sync is on by default
    assertThat(env.getConfig().getDurability()).isEqualTo(Durability.COMMIT_WRITE_NO_SYNC);

    final JEBackendCfg noSync = createBackendCfg();
    when(noSync.isDBTxnNoSync()).thenReturn(true);
    when(noSync.isDBTxnWriteNoSync()).thenReturn(false);
    assertThat(storage.applyConfigurationChange(noSync).getMessages()).isEmpty();
    assertThat(env.getConfig().getDurability()).isEqualTo(Durability.COMMIT_NO_SYNC);

    final JEBackendCfg sync = createBackendCfg();
    when(sync.isDBTxnWriteNoSync()).thenReturn(false);
    assertThat(storage.applyConfigurationChange(sync).getMessages()).isEmpty();
    assertThat(env.getConfig().getDurability()).isEqualTo(Durability.COMMIT_SYNC);

    assertThat(storage.applyConfigurationChange(createBackendCfg()).getMessages()).isEmpty();
    assertThat(env.getConfig().getDurability()).isEqualTo(Durability.COMMIT_WRITE_NO_SYNC);
  }

  /**
   * A native property set through je-property is applied when JE takes it while it runs, and
   * asks for a restart when JE takes it at the open alone: the change result names the property,
   * what the environment runs with and what is now configured, and the environment keeps the
   * former.
   */
  @Test
  public void aNativePropertyIsAppliedOrAsksForARestartAsJETakesIt() throws Exception
  {
    final Environment env = environmentOf(storage);
    final String readSizeAtOpen = env.getConfig().getConfigParam(LOG_ITERATOR_READ_SIZE);
    assertThat(readSizeAtOpen).isNotEqualTo("16384");

    final JEBackendCfg cfg = createBackendCfg();
    when(cfg.getJEProperty()).thenReturn(
        new TreeSet<>(Arrays.asList(CLEANER_MIN_AGE + "=5", LOG_ITERATOR_READ_SIZE + "=16384")));
    final ConfigChangeResult ccr = storage.applyConfigurationChange(cfg);

    assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isTrue();
    assertThat(ccr.getMessages()).hasSize(1);
    assertThat(ccr.getMessages().get(0).toString()).isEqualTo(NOTE_CONFIG_DB_PROPERTY_REQUIRES_RESTART
        .get(LOG_ITERATOR_READ_SIZE, BACKEND_ID, readSizeAtOpen, "16384").toString());
    assertThat(env.getMutableConfig().getConfigParam(CLEANER_MIN_AGE)).isEqualTo("5");
    assertThat(env.getConfig().getConfigParam(LOG_ITERATOR_READ_SIZE)).isEqualTo(readSizeAtOpen);
  }

  /**
   * A property JE takes at the open alone asks for a restart in the change result as well, not
   * only in the property's definition: the definition reaches the reference documentation, the
   * change result reaches the error log of the server which took the change.
   */
  @Test
  public void aChangeOfWhatJETakesAtTheOpenAloneAsksForARestart() throws Exception
  {
    final Environment env = environmentOf(storage);
    final String fileMaxAtOpen = env.getConfig().getConfigParam(LOG_FILE_MAX);

    final JEBackendCfg cfg = createBackendCfg();
    when(cfg.getDBLogFileMax()).thenReturn(2 * Long.parseLong(fileMaxAtOpen));
    final ConfigChangeResult ccr = storage.applyConfigurationChange(cfg);

    assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isTrue();
    assertThat(ccr.getMessages()).hasSize(1);
    assertThat(ccr.getMessages().get(0).toString()).isEqualTo(NOTE_CONFIG_DB_PROPERTY_REQUIRES_RESTART
        .get("db-log-file-max", BACKEND_ID, fileMaxAtOpen, String.valueOf(2 * Long.parseLong(fileMaxAtOpen)))
        .toString());
    assertThat(env.getConfig().getConfigParam(LOG_FILE_MAX)).isEqualTo(fileMaxAtOpen);
  }

  /**
   * The cache is the one thing JE takes while it runs which a change leaves alone: it stays at
   * the size the open reserved, with the reservation, until the next open - the restart the
   * change result asks for. What is applied to the environment is applied around it.
   */
  @Test
  public void aChangeWhileOpenLeavesTheCacheWhereTheOpenReservedIt() throws Exception
  {
    closeAndRemove(storage);
    storage = new JEStorage(createBackendCfg(SMALL_CACHE), serverContext);
    storage.open(AccessMode.READ_WRITE);
    final Environment env = environmentOf(storage);
    assertThat(env.getMutableConfig().getCacheSize()).isEqualTo(SMALL_CACHE);

    final JEBackendCfg cfg = createBackendCfg(2 * SMALL_CACHE);
    when(cfg.getDBCleanerMinUtilization()).thenReturn(60);
    final ConfigChangeResult ccr = storage.applyConfigurationChange(cfg);

    assertThat(ccr.adminActionRequired()).isTrue();
    assertThat(ccr.getMessages()).hasSize(1);
    assertThat(ccr.getMessages().get(0).ordinal()).isEqualTo(NOTE_CONFIG_DB_CACHE_REQUIRES_RESTART.ordinal());
    final EnvironmentMutableConfig running = env.getMutableConfig();
    assertThat(running.getCacheSize()).isEqualTo(SMALL_CACHE);
    assertThat(running.getConfigParam(CLEANER_MIN_UTILIZATION)).isEqualTo("60");
  }

  /**
   * An import runs the environment on a configuration of its own, which is thrown away with it:
   * the backend opens again on the configuration as changed once the import is over, so a change
   * which lands during one is neither applied to the import's environment nor reported against
   * it - held to the import's configuration, every property the import sets differently would
   * ask for a restart.
   */
  @Test
  public void aChangeDuringAnImportLeavesTheImportsEnvironmentAlone() throws Exception
  {
    closeAndRemove(storage);
    storage = new JEStorage(createBackendCfg(), serverContext);
    final Importer importer = storage.startImport();
    try
    {
      final Environment env = environmentOf(storage);
      final JEBackendCfg cfg = createBackendCfg();
      when(cfg.getDBCleanerMinUtilization()).thenReturn(60);
      final ConfigChangeResult ccr = storage.applyConfigurationChange(cfg);

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isFalse();
      assertThat(ccr.getMessages()).isEmpty();
      assertThat(env.getMutableConfig().getConfigParam(CLEANER_MIN_UTILIZATION)).isEqualTo("50");
    }
    finally
    {
      importer.close();
    }
  }

  /**
   * A configuration no environment can be built from is refused before it is written: a
   * durability which sets both flags - db-txn-write-no-sync is on by default, so setting
   * db-txn-no-sync alone is one - and a native property JE does not know. Nothing checked either
   * before, and the backend failed to open on them at its next restart.
   */
  @Test
  public void aConfigurationNoEnvironmentCanBeBuiltFromIsRefused() throws Exception
  {
    final JEBackendCfg bothFlags = createBackendCfg();
    when(bothFlags.isDBTxnNoSync()).thenReturn(true);
    final List<LocalizableMessage> reasons = new ArrayList<>();
    assertThat(storage.isConfigurationChangeAcceptable(bothFlags, reasons)).isFalse();
    assertThat(reasons).hasSize(1);
    assertThat(reasons.get(0).toString()).isEqualTo(ERR_CONFIG_JEB_DURABILITY_CONFLICT.get().toString());

    final JEBackendCfg unknownProperty = createBackendCfg();
    when(unknownProperty.getJEProperty()).thenReturn(new TreeSet<>(Arrays.asList("je.no.such.property=1")));
    reasons.clear();
    assertThat(storage.isConfigurationChangeAcceptable(unknownProperty, reasons)).isFalse();
    assertThat(reasons).hasSize(1);
    assertThat(reasons.get(0).ordinal()).isEqualTo(ERR_CONFIG_JE_PROPERTY_INVALID.get("", "").ordinal());

    reasons.clear();
    assertThat(JEStorage.isConfigurationAcceptable(bothFlags, reasons, serverContext)).isFalse();
    assertThat(reasons).hasSize(1);
    assertThat(storage.isConfigurationChangeAcceptable(createBackendCfg(), new ArrayList<LocalizableMessage>())).isTrue();
  }

  /** A storage which is closed has no environment to apply a change to: the next open takes it. */
  @Test
  public void aChangeWhileClosedTouchesNoEnvironment() throws Exception
  {
    storage.close();
    final JEBackendCfg cfg = createBackendCfg();
    when(cfg.getDBCleanerMinUtilization()).thenReturn(60);

    final ConfigChangeResult ccr = storage.applyConfigurationChange(cfg);

    assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isFalse();
    assertThat(ccr.getMessages()).isEmpty();
  }

  /** The environment of the given storage, which it keeps to itself. */
  private static Environment environmentOf(JEStorage storage) throws Exception
  {
    final Field env = JEStorage.class.getDeclaredField("env");
    env.setAccessible(true);
    return (Environment) env.get(storage);
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
