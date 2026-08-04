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
package org.opends.server.replication.server.changelog.file;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.forgerock.util.Pair;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.server.changelog.file.FileChangelogTestFixtures.*;
import static org.opends.server.util.StaticUtils.toLowerCase;

/**
 * Test the FileChangelogDB class.
 */
@SuppressWarnings("javadoc")
public class FileChangelogDBTest extends ReplicationTestCase
{
  /** Server id of the replica DB which is shut down by the drain of the changelog. */
  private static final int DRAINED_SERVER_ID = 814;
  /** Server id of the replica DB whose creation races that drain. */
  private static final int RACING_SERVER_ID = 813;
  /** Server id of a replica DB created before the failing one, in the same domain. */
  private static final int EXISTING_SERVER_ID = 817;
  /** Server id of the replica DB whose creation is made to fail. */
  private static final int FAILING_SERVER_ID = 818;
  private static final long TIMEOUT_MS = 30000;

  private static final LocalizableMessage CREATION_FAILURE =
      LocalizableMessage.raw("FileChangelogDBTest replica DB creation failure");

  private DN TEST_ROOT_DN;

  @BeforeClass
  public void setup() throws Exception
  {
    TEST_ROOT_DN = DN.valueOf(TEST_ROOT_DN_STRING);
  }

  /**
   * A replica DB whose creation loses the race against {@code shutdownDB()} must not be created at
   * all: it would be held by a domain map the shutdown has already drained, so nothing would ever
   * shut it down, and its monitor provider would stay registered for the lifetime of the process.
   * <p>
   * The interleaving is driven step by step:
   * <ol>
   * <li>the creator thread reads the shutdown flag, sees {@code false}, and is held there, before
   * it inserts the domain map it needs;</li>
   * <li>the shutdown flips the flag and drains {@code domainToReplicaDBs}, and is held inside the
   * shutdown of the replica DB it found, i.e. once that domain map has been removed and while the
   * replication environment is still open;</li>
   * <li>the creator is released into that window.</li>
   * </ol>
   */
  @Test
  public void replicaDBLosingTheRaceAgainstShutdownIsNotCreated() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    RaceableChangelogDB changelogDB = null;
    File testRoot = null;
    Thread creator = null;
    Thread shutdowner = null;
    final AtomicReference<Throwable> creationFailure = new AtomicReference<>();
    final AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();
    try
    {
      replicationServer = configureReplicationServer(100, 5000);
      testRoot = createCleanDir("FileChangelogDB");
      changelogDB = new RaceableChangelogDB(replicationServer, testRoot.getPath(), createCryptoSuite(false));
      changelogDB.initializeDB();

      // the replica DB the drain will be held in, and which is the only one registered so far
      changelogDB.holdNextReplicaDBInItsShutdown();
      changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, DRAINED_SERVER_ID, replicationServer);
      // asserted, so that the test cannot pass by looking for a registration it cannot see
      assertThat(DirectoryServer.getMonitorProviders().keySet())
          .as("the replica DB held by the drain is not registered")
          .contains(replicaDBMonitorName(replicationServer, DRAINED_SERVER_ID));
      assertThat(DirectoryServer.getMonitorProviders().keySet())
          .doesNotContain(replicaDBMonitorName(replicationServer, RACING_SERVER_ID));

      final FileChangelogDB racedChangelogDB = changelogDB;
      final ReplicationServer racedReplicationServer = replicationServer;
      changelogDB.holdNextReplicaDBCreationBeforeItsDomainMapIsInserted();
      creator = new Thread("FileChangelogDBTest replica DB creator")
      {
        @Override
        public void run()
        {
          try
          {
            racedChangelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, RACING_SERVER_ID, racedReplicationServer);
          }
          catch (Throwable t)
          {
            creationFailure.set(t);
          }
        }
      };
      creator.start();
      changelogDB.awaitCreatorInWindow();

      shutdowner = new Thread("FileChangelogDBTest changelog shutdown")
      {
        @Override
        public void run()
        {
          try
          {
            racedChangelogDB.shutdownDB();
          }
          catch (Throwable t)
          {
            shutdownFailure.set(t);
          }
        }
      };
      shutdowner.start();
      changelogDB.awaitDrainInReplicaDBShutdown();

      changelogDB.releaseCreator();
      creator.join(TIMEOUT_MS);

      assertThat(creator.isAlive()).as("the creator thread did not complete").isFalse();
      final SoftAssertions softly = new SoftAssertions();
      softly.assertThat(creationFailure.get())
          .as("a replica DB created while the changelog is being drained is released by nobody")
          .isInstanceOf(ChangelogException.class)
          .hasMessage(ERR_CANNOT_CREATE_REPLICA_DB_BECAUSE_CHANGELOG_DB_SHUTDOWN.get().toString());
      softly.assertThat(DirectoryServer.getMonitorProviders().keySet())
          .as("monitor providers of the replica DBs created during the shutdown")
          .doesNotContain(replicaDBMonitorName(replicationServer, RACING_SERVER_ID));
      softly.assertAll();

      changelogDB.releaseDrain();
      shutdowner.join(TIMEOUT_MS);
      assertThat(shutdowner.isAlive()).as("the shutdown thread did not complete").isFalse();
      assertThat(shutdownFailure.get()).isNull();
      assertThat(DirectoryServer.getMonitorProviders().keySet())
          .as("the drained replica DB is still registered")
          .doesNotContain(replicaDBMonitorName(replicationServer, DRAINED_SERVER_ID));
    }
    finally
    {
      if (changelogDB != null)
      {
        changelogDB.releaseAllHeldThreads();
        changelogDB.shutdownDB();
      }
      join(creator);
      join(shutdowner);
      deregisterLeakedReplicaDBMonitors(replicationServer);
      remove(replicationServer);
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  /**
   * A replica DB whose creation wins the race against {@code shutdownDB()} - i.e. reads the
   * shutdown flag as {@code false} under the domain map monitor - must be shut down by the drain:
   * the drain builds its iterator over {@code domainToReplicaDBs} after the flag is flipped, so it
   * sees the domain map inserted before that monitor was taken, and blocks on the monitor until
   * the creation has published the new replica DB.
   * <p>
   * This is the branch the fix in {@code getExistingOrNewReplicaDB()} relies on: a drain rewritten
   * to no longer traverse the map as it existed when the flag was flipped - snapshotting the keys
   * beforehand, shutting the replication environment down first - would silently reintroduce the
   * leak this test guards against.
   * <p>
   * The interleaving is driven step by step:
   * <ol>
   * <li>the creator thread creates its replica DB - the monitor provider is now registered - and
   * is held before the DB is published into the domain map, still under the domain map
   * monitor;</li>
   * <li>the shutdown starts, flips the flag, and blocks on the domain map monitor the creator
   * holds;</li>
   * <li>the creator is released: it publishes the replica DB and exits the monitor, and the drain
   * must then shut that replica DB down.</li>
   * </ol>
   */
  @Test
  public void replicaDBWinningTheRaceAgainstShutdownIsShutDownByTheDrain() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    RaceableChangelogDB changelogDB = null;
    File testRoot = null;
    Thread creator = null;
    Thread shutdowner = null;
    final AtomicReference<Throwable> creationFailure = new AtomicReference<>();
    final AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();
    final AtomicReference<FileReplicaDB> createdReplicaDB = new AtomicReference<>();
    try
    {
      replicationServer = configureReplicationServer(100, 5000);
      testRoot = createCleanDir("FileChangelogDB");
      changelogDB = new RaceableChangelogDB(replicationServer, testRoot.getPath(), createCryptoSuite(false));
      changelogDB.initializeDB();

      final FileChangelogDB racedChangelogDB = changelogDB;
      final ReplicationServer racedReplicationServer = replicationServer;
      changelogDB.holdNextReplicaDBOnceCreated();
      creator = new Thread("FileChangelogDBTest replica DB creator")
      {
        @Override
        public void run()
        {
          try
          {
            createdReplicaDB.set(racedChangelogDB
                .getOrCreateReplicaDB(TEST_ROOT_DN, RACING_SERVER_ID, racedReplicationServer).getFirst());
          }
          catch (Throwable t)
          {
            creationFailure.set(t);
          }
        }
      };
      creator.start();
      changelogDB.awaitCreatorHoldingItsCreatedReplicaDB();
      // asserted, so that the deregistration below cannot pass by never having seen a registration
      assertThat(DirectoryServer.getMonitorProviders().keySet())
          .as("the racing replica DB is not registered")
          .contains(replicaDBMonitorName(replicationServer, RACING_SERVER_ID));

      shutdowner = new Thread("FileChangelogDBTest changelog shutdown")
      {
        @Override
        public void run()
        {
          try
          {
            racedChangelogDB.shutdownDB();
          }
          catch (Throwable t)
          {
            shutdownFailure.set(t);
          }
        }
      };
      shutdowner.start();
      awaitBlockedOnAMonitor(shutdowner);

      changelogDB.releaseCreatedReplicaDB();
      creator.join(TIMEOUT_MS);
      shutdowner.join(TIMEOUT_MS);
      assertThat(creator.isAlive()).as("the creator thread did not complete").isFalse();
      assertThat(shutdowner.isAlive()).as("the shutdown thread did not complete").isFalse();
      assertThat(creationFailure.get()).as("a creation which won the race must succeed").isNull();
      assertThat(createdReplicaDB.get()).as("the replica DB which won the race").isNotNull();
      assertThat(shutdownFailure.get()).isNull();
      assertThat(DirectoryServer.getMonitorProviders().keySet())
          .as("the replica DB which won the race is not shut down by the drain")
          .doesNotContain(replicaDBMonitorName(replicationServer, RACING_SERVER_ID));
    }
    finally
    {
      if (changelogDB != null)
      {
        changelogDB.releaseAllHeldThreads();
        changelogDB.shutdownDB();
      }
      join(creator);
      join(shutdowner);
      deregisterLeakedReplicaDBMonitors(replicationServer);
      remove(replicationServer);
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  /**
   * A replica DB creation which fails must not leave behind the empty domain map it inserted:
   * nothing would ever remove it from {@code domainToReplicaDBs}, and every multi domain cursor
   * created afterwards would walk a domain holding no replica DB at all.
   */
  @Test
  public void failedReplicaDBCreationDropsTheDomainMapItInserted() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    FailingChangelogDB changelogDB = null;
    File testRoot = null;
    try
    {
      replicationServer = configureReplicationServer(100, 5000);
      testRoot = createCleanDir("FileChangelogDB");
      changelogDB = new FailingChangelogDB(replicationServer, testRoot.getPath(), createCryptoSuite(false));
      changelogDB.initializeDB();

      changelogDB.failNextReplicaDBCreation();
      try
      {
        changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, FAILING_SERVER_ID, replicationServer);
        failBecauseExceptionWasNotThrown(ChangelogException.class);
      }
      catch (ChangelogException expected)
      {
        assertThat(expected).hasMessage(CREATION_FAILURE.toString());
      }
      assertThat(domainToReplicaDBs(changelogDB))
          .as("the empty domain map inserted for the creation which failed")
          .doesNotContainKey(TEST_ROOT_DN);

      // the next creation starts from scratch and repopulates the domain
      final Pair<FileReplicaDB, Boolean> result =
          changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, FAILING_SERVER_ID, replicationServer);
      assertThat(result.getSecond()).as("the replica DB was created anew").isTrue();
      assertThat(domainToReplicaDBs(changelogDB).get(TEST_ROOT_DN)).containsOnlyKeys(FAILING_SERVER_ID);
    }
    finally
    {
      if (changelogDB != null)
      {
        changelogDB.shutdownDB();
      }
      remove(replicationServer);
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  /**
   * A replica DB creation which fails must only drop an <b>empty</b> domain map: a populated one
   * must stay mapped, so that the drain of {@code shutdownDB()} finds the replica DBs it holds and
   * shuts them down.
   */
  @Test
  public void failedReplicaDBCreationKeepsAPopulatedDomainMap() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    FailingChangelogDB changelogDB = null;
    File testRoot = null;
    try
    {
      replicationServer = configureReplicationServer(100, 5000);
      testRoot = createCleanDir("FileChangelogDB");
      changelogDB = new FailingChangelogDB(replicationServer, testRoot.getPath(), createCryptoSuite(false));
      changelogDB.initializeDB();

      changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, EXISTING_SERVER_ID, replicationServer);
      final ConcurrentMap<Integer, FileReplicaDB> domainMap = domainToReplicaDBs(changelogDB).get(TEST_ROOT_DN);

      changelogDB.failNextReplicaDBCreation();
      try
      {
        changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, FAILING_SERVER_ID, replicationServer);
        failBecauseExceptionWasNotThrown(ChangelogException.class);
      }
      catch (ChangelogException expected)
      {
        assertThat(expected).hasMessage(CREATION_FAILURE.toString());
      }
      assertThat(domainToReplicaDBs(changelogDB).get(TEST_ROOT_DN))
          .as("the domain map holding the replica DB created before the failure")
          .isSameAs(domainMap)
          .containsOnlyKeys(EXISTING_SERVER_ID);
    }
    finally
    {
      if (changelogDB != null)
      {
        changelogDB.shutdownDB();
      }
      remove(replicationServer);
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMap<DN, ConcurrentMap<Integer, FileReplicaDB>> domainToReplicaDBs(
      final FileChangelogDB changelogDB) throws Exception
  {
    final Field field = FileChangelogDB.class.getDeclaredField("domainToReplicaDBs");
    field.setAccessible(true);
    return (ConcurrentMap<DN, ConcurrentMap<Integer, FileReplicaDB>>) field.get(changelogDB);
  }

  /** Joins the provided thread, leaving a signal behind when it did not die within the timeout. */
  private void join(final Thread thread) throws InterruptedException
  {
    if (thread != null)
    {
      thread.join(TIMEOUT_MS);
      if (thread.isAlive())
      {
        final IllegalStateException hung = new IllegalStateException("Test thread " + thread.getName()
            + " is still alive after " + TIMEOUT_MS + " ms: it may leak a live changelog into later tests");
        hung.setStackTrace(thread.getStackTrace());
        hung.printStackTrace();
        thread.interrupt();
      }
    }
  }

  /**
   * Waits until the provided thread is blocked acquiring a monitor: the domain map monitor held by
   * the creator is the only one it can stay blocked on - the other locks on its way to the drain
   * are only transiently contended, hence the two consecutive observations.
   */
  private static void awaitBlockedOnAMonitor(final Thread thread) throws InterruptedException
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    int blockedObservations = 0;
    while (blockedObservations < 2)
    {
      if (!thread.isAlive())
      {
        throw new IllegalStateException(thread.getName() + " completed without blocking on the domain map monitor");
      }
      if (System.currentTimeMillis() > deadline)
      {
        throw new IllegalStateException(
            "timed out waiting for " + thread.getName() + " to block on the domain map monitor");
      }
      blockedObservations = thread.getState() == Thread.State.BLOCKED ? blockedObservations + 1 : 0;
      Thread.sleep(1);
    }
  }

  /**
   * Returns the name the monitor provider of the provided replica DB is registered under, i.e. the
   * name built by {@code FileReplicaDB.DbMonitorProvider.getMonitorInstanceName()}, lower-cased
   * the way {@code DirectoryServer.registerMonitorProvider()} stores it.
   */
  private String replicaDBMonitorName(final ReplicationServer replicationServer, final int serverId)
  {
    final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(TEST_ROOT_DN);
    assertThat(domain).as("the domain scoping the monitor name of DS(" + serverId + ")").isNotNull();
    return toLowerCase("Changelog for DS(" + serverId + "),cn=" + domain.getMonitorInstanceName());
  }

  /** Releases the monitor providers a regression leaks, so that they do not outlive this test. */
  private void deregisterLeakedReplicaDBMonitors(final ReplicationServer replicationServer)
  {
    if (replicationServer == null || replicationServer.getReplicationServerDomain(TEST_ROOT_DN) == null)
    {
      return; // no replica DB was ever created, hence no monitor provider was ever registered
    }
    for (final int serverId : new int[] { RACING_SERVER_ID, DRAINED_SERVER_ID })
    {
      // deregister the provider instead of removing the map entry, so that the JMX MBean
      // registered alongside it is released as well
      final MonitorProvider<? extends MonitorProviderCfg> provider =
          DirectoryServer.getMonitorProviders().get(replicaDBMonitorName(replicationServer, serverId));
      if (provider != null)
      {
        DirectoryServer.deregisterMonitorProvider(provider);
      }
    }
  }

  /**
   * A changelog DB which lets a test hold a thread creating a replica DB right after it has read
   * the shutdown flag, hold it again once the replica DB is created but not yet published into the
   * domain map, and hold the shutdown inside the drain of {@code domainToReplicaDBs}.
   */
  private static final class RaceableChangelogDB extends FileChangelogDB
  {
    private final AtomicBoolean holdNextCreation = new AtomicBoolean();
    private final AtomicBoolean holdNextReplicaDBShutdown = new AtomicBoolean();
    private final AtomicBoolean holdNextCreatedReplicaDB = new AtomicBoolean();
    private final CountDownLatch creatorIsInWindow = new CountDownLatch(1);
    private final CountDownLatch creatorIsReleased = new CountDownLatch(1);
    private final CountDownLatch creatorHoldsItsCreatedReplicaDB = new CountDownLatch(1);
    private final CountDownLatch createdReplicaDBIsReleased = new CountDownLatch(1);
    private final CountDownLatch drainIsInReplicaDBShutdown = new CountDownLatch(1);
    private final CountDownLatch drainIsReleased = new CountDownLatch(1);

    RaceableChangelogDB(final ReplicationServer replicationServer, final String dbDirectoryPath,
        final CryptoSuite cryptoSuite) throws ConfigException
    {
      super(replicationServer, dbDirectoryPath, cryptoSuite);
    }

    @Override
    ConcurrentMap<Integer, FileReplicaDB> getExistingOrNewDomainMap(final DN baseDN)
    {
      if (holdNextCreation.compareAndSet(true, false))
      {
        creatorIsInWindow.countDown();
        await(creatorIsReleased);
      }
      return super.getExistingOrNewDomainMap(baseDN);
    }

    @Override
    FileReplicaDB newReplicaDB(final int serverId, final DN baseDN, final ReplicationServer server,
        final CryptoSuite cryptoSuite, final ReplicationEnvironment replicationEnv) throws ChangelogException
    {
      if (holdNextReplicaDBShutdown.compareAndSet(true, false))
      {
        return new HeldOnShutdownReplicaDB(serverId, baseDN, server, cryptoSuite, replicationEnv);
      }
      final FileReplicaDB replicaDB = super.newReplicaDB(serverId, baseDN, server, cryptoSuite, replicationEnv);
      if (holdNextCreatedReplicaDB.compareAndSet(true, false))
      {
        // the replica DB exists and its monitor provider is registered, but it is not published
        // into the domain map yet: hold the creator there, under the domain map monitor
        creatorHoldsItsCreatedReplicaDB.countDown();
        await(createdReplicaDBIsReleased);
      }
      return replicaDB;
    }

    void holdNextReplicaDBCreationBeforeItsDomainMapIsInserted()
    {
      holdNextCreation.set(true);
    }

    void holdNextReplicaDBInItsShutdown()
    {
      holdNextReplicaDBShutdown.set(true);
    }

    void holdNextReplicaDBOnceCreated()
    {
      holdNextCreatedReplicaDB.set(true);
    }

    void awaitCreatorInWindow()
    {
      await(creatorIsInWindow);
    }

    void awaitCreatorHoldingItsCreatedReplicaDB()
    {
      await(creatorHoldsItsCreatedReplicaDB);
    }

    void awaitDrainInReplicaDBShutdown()
    {
      await(drainIsInReplicaDBShutdown);
    }

    void releaseCreator()
    {
      creatorIsReleased.countDown();
    }

    void releaseCreatedReplicaDB()
    {
      createdReplicaDBIsReleased.countDown();
    }

    void releaseDrain()
    {
      drainIsReleased.countDown();
    }

    void releaseAllHeldThreads()
    {
      releaseCreator();
      releaseCreatedReplicaDB();
      releaseDrain();
    }

    /** A replica DB which holds the thread shutting it down until the test releases it. */
    private final class HeldOnShutdownReplicaDB extends FileReplicaDB
    {
      HeldOnShutdownReplicaDB(final int serverId, final DN baseDN, final ReplicationServer server,
          final CryptoSuite cryptoSuite, final ReplicationEnvironment replicationEnv) throws ChangelogException
      {
        super(serverId, baseDN, server, cryptoSuite, replicationEnv);
      }

      @Override
      void shutdown()
      {
        drainIsInReplicaDBShutdown.countDown();
        await(drainIsReleased);
        super.shutdown();
      }
    }

    private static void await(final CountDownLatch latch)
    {
      try
      {
        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        {
          throw new IllegalStateException("timed out waiting for the replica DB creation race");
        }
      }
      catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
  }

  /** A changelog DB which lets a test make the next replica DB creation fail. */
  private static final class FailingChangelogDB extends FileChangelogDB
  {
    private final AtomicBoolean failNextCreation = new AtomicBoolean();

    FailingChangelogDB(final ReplicationServer replicationServer, final String dbDirectoryPath,
        final CryptoSuite cryptoSuite) throws ConfigException
    {
      super(replicationServer, dbDirectoryPath, cryptoSuite);
    }

    @Override
    FileReplicaDB newReplicaDB(final int serverId, final DN baseDN, final ReplicationServer server,
        final CryptoSuite cryptoSuite, final ReplicationEnvironment replicationEnv) throws ChangelogException
    {
      if (failNextCreation.compareAndSet(true, false))
      {
        throw new ChangelogException(CREATION_FAILURE);
      }
      return super.newReplicaDB(serverId, baseDN, server, cryptoSuite, replicationEnv);
    }

    void failNextReplicaDBCreation()
    {
      failNextCreation.set(true);
    }
  }
}
