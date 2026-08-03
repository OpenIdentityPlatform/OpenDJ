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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;

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
  private static final long TIMEOUT_MS = 30000;

  private final String cipherTransformation = "AES/CBC/PKCS5Padding";
  private final int keyLength = 128;
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
      replicationServer = configureReplicationServer();
      testRoot = createCleanDir();
      changelogDB = new RaceableChangelogDB(replicationServer, testRoot.getPath(), createCryptoSuite());
      changelogDB.initializeDB();

      // the replica DB the drain will be held in, and which is the only one registered so far
      changelogDB.holdNextReplicaDBInItsShutdown();
      changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, DRAINED_SERVER_ID, replicationServer);
      // asserted, so that the test cannot pass by looking for a registration it cannot see
      assertThat(replicaDBMonitorNames(DRAINED_SERVER_ID))
          .as("the replica DB held by the drain is not registered")
          .hasSize(1);
      assertThat(replicaDBMonitorNames(RACING_SERVER_ID)).isEmpty();

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
      softly.assertThat(replicaDBMonitorNames(RACING_SERVER_ID))
          .as("monitor providers of the replica DBs created during the shutdown")
          .isEmpty();
      softly.assertAll();

      changelogDB.releaseDrain();
      shutdowner.join(TIMEOUT_MS);
      assertThat(shutdowner.isAlive()).as("the shutdown thread did not complete").isFalse();
      assertThat(shutdownFailure.get()).isNull();
      assertThat(replicaDBMonitorNames(DRAINED_SERVER_ID))
          .as("the drained replica DB is still registered")
          .isEmpty();
    }
    finally
    {
      if (changelogDB != null)
      {
        changelogDB.releaseCreator();
        changelogDB.releaseDrain();
        changelogDB.shutdownDB();
      }
      join(creator);
      join(shutdowner);
      // release what the unfixed code leaks, so that it does not outlive this test
      for (String monitorName : replicaDBMonitorNames(RACING_SERVER_ID))
      {
        DirectoryServer.getMonitorProviders().remove(monitorName);
      }
      remove(replicationServer);
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  private void join(final Thread thread) throws InterruptedException
  {
    if (thread != null)
    {
      thread.join(TIMEOUT_MS);
    }
  }

  /** Returns the names the replica DBs of the provided server id are registered under. */
  private List<String> replicaDBMonitorNames(final int serverId)
  {
    final String prefix = "changelog for ds(" + serverId + ")";
    final List<String> names = new ArrayList<>();
    for (String monitorName : DirectoryServer.getMonitorProviders().keySet())
    {
      if (monitorName.startsWith(prefix))
      {
        names.add(monitorName);
      }
    }
    return names;
  }

  private ReplicationServer configureReplicationServer() throws IOException, ConfigException
  {
    return new ReplicationServer(
        new ReplServerFakeConfiguration(findFreePort(), null, 0, 2, 5000, 100, null));
  }

  private CryptoSuite createCryptoSuite()
  {
    return getServerContext().getCryptoManager().newCryptoSuite(cipherTransformation, keyLength, false);
  }

  private File createCleanDir() throws IOException
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR, buildRoot
            + File.separator + "build");
    path = path + File.separator + "unit-tests" + File.separator + "FileChangelogDB";
    final File testRoot = new File(path);
    TestCaseUtils.deleteDirectory(testRoot);
    testRoot.mkdirs();
    return testRoot;
  }

  /**
   * A changelog DB which lets a test hold a thread creating a replica DB right after it has read
   * the shutdown flag, and hold the shutdown inside the drain of {@code domainToReplicaDBs}.
   */
  private static final class RaceableChangelogDB extends FileChangelogDB
  {
    private final AtomicBoolean holdNextCreation = new AtomicBoolean();
    private final AtomicBoolean holdNextReplicaDB = new AtomicBoolean();
    private final CountDownLatch creatorIsInWindow = new CountDownLatch(1);
    private final CountDownLatch creatorIsReleased = new CountDownLatch(1);
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
      if (holdNextReplicaDB.compareAndSet(true, false))
      {
        return new HeldOnShutdownReplicaDB(serverId, baseDN, server, cryptoSuite, replicationEnv);
      }
      return super.newReplicaDB(serverId, baseDN, server, cryptoSuite, replicationEnv);
    }

    void holdNextReplicaDBCreationBeforeItsDomainMapIsInserted()
    {
      holdNextCreation.set(true);
    }

    void holdNextReplicaDBInItsShutdown()
    {
      holdNextReplicaDB.set(true);
    }

    void awaitCreatorInWindow()
    {
      await(creatorIsInWindow);
    }

    void awaitDrainInReplicaDBShutdown()
    {
      await(drainIsInReplicaDBShutdown);
    }

    void releaseCreator()
    {
      creatorIsReleased.countDown();
    }

    void releaseDrain()
    {
      drainIsReleased.countDown();
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
}
