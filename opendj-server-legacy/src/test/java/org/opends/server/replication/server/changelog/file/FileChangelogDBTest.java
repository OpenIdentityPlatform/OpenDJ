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
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.CursorOptions;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.replication.server.changelog.file.FileChangelogTestFixtures.*;
import static org.opends.server.util.StaticUtils.toLowerCase;
import static org.testng.Assert.*;

/**
 * Test the FileChangelogDB class: the races between a replica DB creation and
 * {@link FileChangelogDB#shutdownDB()}; the window between
 * {@link FileChangelogDB#removeDomain(DN)}'s unlocked read of the domainMap and its
 * acquisition of the domainMap monitor, during which a concurrent remover
 * ({@code shutdownDB()}, {@code clearDB()} or another {@code removeDomain()}) may have
 * unmapped the domain; and the cleanup of a creation which bails out without having created a
 * replica DB - the empty domainMap it inserted must be dropped, without unmapping the fresh
 * domainMap of a concurrent creation and without announcing the domain a second time to the
 * multi domain cursors that were live at the time.
 */
@SuppressWarnings("javadoc")
public class FileChangelogDBTest extends ReplicationTestCase
{
  /** Server id of the replica DB which is shut down by the drain of the changelog. */
  private static final int DRAINED_SERVER_ID = 814;
  /** Server id of the replica DB whose creation races that drain. */
  private static final int RACING_SERVER_ID = 813;
  /** Server id of the replica DB whose domain removal races a concurrent remover. */
  private static final int SERVER_ID = 1;
  /** Server id of the replica DB whose creation bails out on the identity check. */
  private static final int STALE_SERVER_ID = 815;
  /** Server id of the replica DB created in the fresh domain map the bail-out must not unmap. */
  private static final int FRESH_SERVER_ID = 816;
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
      deregisterLeakedReplicaDBMonitors(replicationServer, RACING_SERVER_ID, DRAINED_SERVER_ID);
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
      waitUntilBlockedOn(shutdowner, changelogDB.getDomainToReplicaDBs().get(TEST_ROOT_DN));

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
      deregisterLeakedReplicaDBMonitors(replicationServer, RACING_SERVER_ID);
      remove(replicationServer);
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  /**
   * A replica DB creation which fails must not leave behind the empty domain map it inserted:
   * nothing would ever remove it from {@code domainToReplicaDBs}, and every multi domain cursor
   * created afterwards would walk a domain holding no replica DB at all - the symptom this test
   * also asserts, through the domains a new multi domain cursor asks the changelog to open.
   */
  @Test
  public void failedReplicaDBCreationDropsTheDomainMapItInserted() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    RaceableChangelogDB changelogDB = null;
    File testRoot = null;
    try
    {
      replicationServer = configureReplicationServer(100, 5000);
      testRoot = createCleanDir("FileChangelogDB");
      changelogDB = new RaceableChangelogDB(replicationServer, testRoot.getPath(), createCryptoSuite(false));
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
      assertThat(changelogDB.getDomainToReplicaDBs())
          .as("the empty domain map inserted for the creation which failed")
          .doesNotContainKey(TEST_ROOT_DN);

      // the symptom of the leftover map: a multi domain cursor created after the failure must not
      // walk the phantom domain
      changelogDB.walkedDomains.clear();
      final MultiDomainDBCursor cursor = changelogDB.getCursorFrom(
          new MultiDomainServerState(), new CursorOptions(GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY));
      try
      {
        cursor.next();
        assertThat(changelogDB.walkedDomains)
            .as("the domains walked by a multi domain cursor created after the failed creation")
            .doesNotContain(TEST_ROOT_DN);
      }
      finally
      {
        cursor.close();
      }

      // the next creation starts from scratch and repopulates the domain
      final Pair<FileReplicaDB, Boolean> result =
          changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, FAILING_SERVER_ID, replicationServer);
      assertThat(result.getSecond()).as("the replica DB was created anew").isTrue();
      assertThat(changelogDB.getDomainToReplicaDBs().get(TEST_ROOT_DN)).containsOnlyKeys(FAILING_SERVER_ID);
    }
    finally
    {
      try
      {
        if (changelogDB != null)
        {
          changelogDB.shutdownDB();
        }
      }
      finally
      {
        remove(replicationServer);
        TestCaseUtils.deleteDirectory(testRoot);
      }
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
    RaceableChangelogDB changelogDB = null;
    File testRoot = null;
    try
    {
      replicationServer = configureReplicationServer(100, 5000);
      testRoot = createCleanDir("FileChangelogDB");
      changelogDB = new RaceableChangelogDB(replicationServer, testRoot.getPath(), createCryptoSuite(false));
      changelogDB.initializeDB();

      changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, EXISTING_SERVER_ID, replicationServer);
      final ConcurrentMap<Integer, FileReplicaDB> domainMap =
          changelogDB.getDomainToReplicaDBs().get(TEST_ROOT_DN);

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
      assertThat(changelogDB.getDomainToReplicaDBs().get(TEST_ROOT_DN))
          .as("the domain map holding the replica DB created before the failure")
          .isSameAs(domainMap)
          .containsOnlyKeys(EXISTING_SERVER_ID);
    }
    finally
    {
      try
      {
        if (changelogDB != null)
        {
          changelogDB.shutdownDB();
        }
      }
      finally
      {
        remove(replicationServer);
        TestCaseUtils.deleteDirectory(testRoot);
      }
    }
  }

  /**
   * The cleanup of a failed creation leaves the domain announced to every multi domain cursor
   * which was live at the time, and the next successful creation of the domain announces it to
   * them again: the second announcement must not open a second cursor over the same domain. Such
   * a cursor would either leak unclosed - the cursor tree of {@code CompositeDBCursor} collapses
   * cursors comparing equal - or deliver every change twice, which kills the
   * {@code ChangeNumberIndexer} thread with the {@code IllegalStateException} its cookie update
   * throws on a replayed change.
   * <p>
   * This covers the drop path only: the {@code removeDomain()} path never re-announces a domain
   * to a cursor which still holds it - the cursor drops the domain, through
   * {@code indexer.clear()}, before the domain is unmapped.
   */
  @Test
  public void announcingADomainTwiceToALiveCursorMustNotOpenASecondDomainCursor() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    RaceableChangelogDB changelogDB = null;
    File testRoot = null;
    try
    {
      replicationServer = configureReplicationServer(100, 5000);
      testRoot = createCleanDir("FileChangelogDB");
      changelogDB = new RaceableChangelogDB(replicationServer, testRoot.getPath(), createCryptoSuite(false));
      changelogDB.initializeDB();

      // the live cursor both announcements reach
      final MultiDomainDBCursor cursor = changelogDB.getCursorFrom(
          new MultiDomainServerState(), new CursorOptions(GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY));
      try
      {
        // first announcement: the failed creation announces the domain before dropping its map
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
        cursor.next();
        assertThat(changelogDB.walkedDomains)
            .as("the domains the live cursor iterates over after the first announcement")
            .containsExactly(TEST_ROOT_DN);

        // second announcement: the next creation of the domain announces it to the cursor again
        final FileReplicaDB replicaDB =
            changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, FAILING_SERVER_ID, replicationServer).getFirst();
        final CSN csn = new CSN(System.currentTimeMillis(), 1, FAILING_SERVER_ID);
        replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csn, "uid"));
        waitChangesArePersisted(replicaDB, 1);

        assertThat(cursor.next()).as("the change published after the second announcement").isTrue();
        assertThat(cursor.getRecord().getCSN()).isEqualTo(csn);
        assertThat(changelogDB.walkedDomains)
            .as("announcing an already incorporated domain again must not open a second cursor over it")
            .containsExactly(TEST_ROOT_DN);
        assertThat(cursor.next()).as("the single published change is delivered more than once").isFalse();
      }
      finally
      {
        cursor.close();
      }
    }
    finally
    {
      try
      {
        if (changelogDB != null)
        {
          changelogDB.shutdownDB();
        }
      }
      finally
      {
        remove(replicationServer);
        TestCaseUtils.deleteDirectory(testRoot);
      }
    }
  }

  /**
   * A creation which bails out on the identity check must not drop the domain map another creation
   * has freshly inserted: the drop is equality based and two empty maps are equal, so an identity
   * unaware cleanup would unmap the fresh map, and the replica DB about to be published into it
   * would no longer be reachable from {@code domainToReplicaDBs} - nothing would ever shut it
   * down, which is the leak of #813 all over again.
   * <p>
   * The interleaving is driven step by step:
   * <ol>
   * <li>the stale creator obtains its domain map and is held before entering its monitor;</li>
   * <li>{@code removeDomain()} unmaps that domain map;</li>
   * <li>a fresh creator inserts a new, still empty domain map and is held inside
   * {@code newReplicaDB()}, under the fresh map's monitor;</li>
   * <li>the stale creator is released: its identity check fails and it must bail out without
   * touching the fresh map, then retry and block on the fresh map's monitor;</li>
   * <li>the fresh creator is released: both creations complete into that same map.</li>
   * </ol>
   */
  @Test
  public void bailOutMustNotUnmapAnotherThreadsFreshDomainMap() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    RaceableChangelogDB changelogDB = null;
    File testRoot = null;
    Thread staleCreator = null;
    Thread freshCreator = null;
    final AtomicReference<Throwable> staleCreationFailure = new AtomicReference<>();
    final AtomicReference<Throwable> freshCreationFailure = new AtomicReference<>();
    try
    {
      replicationServer = configureReplicationServer(100, 5000);
      testRoot = createCleanDir("FileChangelogDB");
      changelogDB = new RaceableChangelogDB(replicationServer, testRoot.getPath(), createCryptoSuite(false));
      changelogDB.initializeDB();

      final RaceableChangelogDB racedChangelogDB = changelogDB;
      final ReplicationServer racedReplicationServer = replicationServer;

      // 1- the stale creator obtains the domain map about to be unmapped, and is parked there
      changelogDB.holdNextCreationAfterItsDomainMapIsObtained();
      staleCreator = new Thread("FileChangelogDBTest stale replica DB creator")
      {
        @Override
        public void run()
        {
          try
          {
            racedChangelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, STALE_SERVER_ID, racedReplicationServer);
          }
          catch (Throwable t)
          {
            staleCreationFailure.set(t);
          }
        }
      };
      staleCreator.start();
      changelogDB.awaitCreatorHoldingItsDomainMap();

      // 2- the domain map the stale creator holds is unmapped
      changelogDB.removeDomain(TEST_ROOT_DN);

      // 3- the fresh creator inserts a new, still empty domain map, and is parked inside
      // newReplicaDB(), under the monitor of that fresh map
      changelogDB.holdNextReplicaDBOnceCreated();
      freshCreator = new Thread("FileChangelogDBTest fresh replica DB creator")
      {
        @Override
        public void run()
        {
          try
          {
            racedChangelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, FRESH_SERVER_ID, racedReplicationServer);
          }
          catch (Throwable t)
          {
            freshCreationFailure.set(t);
          }
        }
      };
      freshCreator.start();
      changelogDB.awaitCreatorHoldingItsCreatedReplicaDB();
      final ConcurrentMap<Integer, FileReplicaDB> freshDomainMap =
          changelogDB.getDomainToReplicaDBs().get(TEST_ROOT_DN);
      assertThat(freshDomainMap).as("the fresh domain map, not published into yet").isNotNull().isEmpty();

      // 4- the stale creator bails out on its identity check, retries, and blocks on the monitor
      // of the fresh domain map - without the identity check its cleanup would have unmapped the
      // fresh map, and it would have completed into a third map instead of blocking
      changelogDB.releaseCreatorHoldingItsDomainMap();
      waitUntilBlockedOnOrCompleted(staleCreator, freshDomainMap);
      assertThat(changelogDB.getDomainToReplicaDBs().get(TEST_ROOT_DN))
          .as("the domain map the fresh creator is about to publish its replica DB into")
          .isSameAs(freshDomainMap);

      // 5- both creations complete into that same map
      changelogDB.releaseCreatedReplicaDB();
      staleCreator.join(TIMEOUT_MS);
      freshCreator.join(TIMEOUT_MS);
      assertThat(staleCreator.isAlive()).as("the stale creator thread did not complete").isFalse();
      assertThat(freshCreator.isAlive()).as("the fresh creator thread did not complete").isFalse();
      assertThat(staleCreationFailure.get()).isNull();
      assertThat(freshCreationFailure.get()).isNull();
      assertThat(changelogDB.getDomainToReplicaDBs().get(TEST_ROOT_DN))
          .isSameAs(freshDomainMap)
          .containsOnlyKeys(STALE_SERVER_ID, FRESH_SERVER_ID);
    }
    finally
    {
      try
      {
        if (changelogDB != null)
        {
          changelogDB.releaseAllHeldThreads();
          changelogDB.shutdownDB();
        }
      }
      finally
      {
        join(staleCreator);
        join(freshCreator);
        deregisterLeakedReplicaDBMonitors(replicationServer, STALE_SERVER_ID, FRESH_SERVER_ID);
        remove(replicationServer);
        TestCaseUtils.deleteDirectory(testRoot);
      }
    }
  }

  /**
   * The concurrent remover unmapped the domain and shut its replica DBs down, exactly like
   * the {@code shutdownDB()} drain does: {@code removeDomain()} must complete without
   * throwing a {@link NullPointerException}.
   */
  @Test
  public void removeDomainRacingConcurrentRemovalMustNotThrowNPE() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100, 100);
      final FileChangelogDB changelogDB = (FileChangelogDB) replicationServer.getChangelogDB();
      final FileReplicaDB replicaDB =
          changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, SERVER_ID, replicationServer).getFirst();

      final ConcurrentMap<DN, ConcurrentMap<Integer, FileReplicaDB>> domainToReplicaDBs =
          changelogDB.getDomainToReplicaDBs();
      final ConcurrentMap<Integer, FileReplicaDB> domainMap = domainToReplicaDBs.get(TEST_ROOT_DN);
      assertThat(domainMap).isNotNull();

      final AtomicReference<Throwable> thrown = new AtomicReference<>();
      final Thread remover = newRemoverThread(changelogDB, thrown);
      synchronized (domainMap)
      {
        remover.start();
        // removeDomain() read the domain entry and is now blocked on the monitor held here
        waitUntilBlockedOn(remover, domainMap);

        domainToReplicaDBs.remove(TEST_ROOT_DN);
        replicaDB.shutdown();
      }
      remover.join(TIMEOUT_MS);

      assertFalse(remover.isAlive(), "removeDomain() did not complete");
      assertThat(thrown.get()).isNull();
    }
    finally
    {
      remove(replicationServer);
    }
  }

  /**
   * The concurrent remover unmapped the domain and {@code getOrCreateReplicaDB()} then
   * recreated it: {@code removeDomain()} must only unmap the domainMap instance it holds the
   * monitor on, never the recreated one.
   */
  @Test
  public void removeDomainMustNotUnmapConcurrentlyRecreatedDomain() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100, 100);
      final FileChangelogDB changelogDB = (FileChangelogDB) replicationServer.getChangelogDB();
      final FileReplicaDB replicaDB =
          changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, SERVER_ID, replicationServer).getFirst();

      final ConcurrentMap<DN, ConcurrentMap<Integer, FileReplicaDB>> domainToReplicaDBs =
          changelogDB.getDomainToReplicaDBs();
      final ConcurrentMap<Integer, FileReplicaDB> domainMap = domainToReplicaDBs.get(TEST_ROOT_DN);
      assertThat(domainMap).isNotNull();

      final ConcurrentMap<Integer, FileReplicaDB> recreatedDomainMap = new ConcurrentHashMap<>();
      final AtomicReference<Throwable> thrown = new AtomicReference<>();
      final Thread remover = newRemoverThread(changelogDB, thrown);
      synchronized (domainMap)
      {
        remover.start();
        waitUntilBlockedOn(remover, domainMap);

        domainToReplicaDBs.remove(TEST_ROOT_DN);
        replicaDB.shutdown();
        domainToReplicaDBs.put(TEST_ROOT_DN, recreatedDomainMap);
      }
      remover.join(TIMEOUT_MS);

      assertFalse(remover.isAlive(), "removeDomain() did not complete");
      assertThat(thrown.get()).isNull();
      assertThat(domainToReplicaDBs.get(TEST_ROOT_DN)).isSameAs(recreatedDomainMap);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  private Thread newRemoverThread(final FileChangelogDB changelogDB, final AtomicReference<Throwable> thrown)
  {
    return new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          changelogDB.removeDomain(TEST_ROOT_DN);
        }
        catch (Throwable t)
        {
          thrown.set(t);
        }
      }
    }, "removeDomain() under test");
  }

  /** Waits until the provided replica DB has persisted the provided number of records. */
  private void waitChangesArePersisted(FileReplicaDB replicaDB, int recordCount) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (replicaDB.getNumberRecords() < recordCount)
    {
      if (System.currentTimeMillis() > deadline)
      {
        throw new AssertionError("Timed out waiting for " + recordCount + " records to be persisted");
      }
      Thread.sleep(10);
    }
  }

  /** Waits until the provided thread is blocked acquiring the monitor of the provided object. */
  private void waitUntilBlockedOn(Thread thread, Object monitor) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline)
    {
      if (isBlockedOn(thread, monitor))
      {
        return;
      }
      Thread.sleep(1);
    }
    throw new AssertionError(
        "Timed out waiting for " + thread.getName() + " to block on the domainMap monitor");
  }

  /**
   * Waits until the provided thread is blocked acquiring the monitor of the provided object, or
   * has completed: completion is left for the caller's assertions to diagnose.
   */
  private void waitUntilBlockedOnOrCompleted(Thread thread, Object monitor) throws Exception
  {
    final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline)
    {
      if (!thread.isAlive() || isBlockedOn(thread, monitor))
      {
        return;
      }
      Thread.sleep(1);
    }
    throw new AssertionError("Timed out waiting for " + thread.getName()
        + " to block on the domainMap monitor or complete");
  }

  private static boolean isBlockedOn(Thread thread, Object monitor)
  {
    final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    final ThreadInfo threadInfo = threadMXBean.getThreadInfo(thread.getId());
    final LockInfo lockInfo = threadInfo != null ? threadInfo.getLockInfo() : null;
    return lockInfo != null
        && threadInfo.getThreadState() == Thread.State.BLOCKED
        && lockInfo.getIdentityHashCode() == System.identityHashCode(monitor);
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
  private void deregisterLeakedReplicaDBMonitors(final ReplicationServer replicationServer, final int... serverIds)
  {
    if (replicationServer == null || replicationServer.getReplicationServerDomain(TEST_ROOT_DN) == null)
    {
      return; // no replica DB was ever created, hence no monitor provider was ever registered
    }
    for (final int serverId : serverIds)
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
   * the shutdown flag, hold it after it has obtained its domain map but before it enters the
   * monitor, hold it again once the replica DB is created but not yet published into the domain
   * map, hold the shutdown inside the drain of {@code domainToReplicaDBs}, make the next replica
   * DB creation fail, and record the domains cursors are opened for.
   */
  private static final class RaceableChangelogDB extends FileChangelogDB
  {
    private final AtomicBoolean holdNextCreation = new AtomicBoolean();
    private final AtomicBoolean holdNextDomainMapObtained = new AtomicBoolean();
    private final AtomicBoolean holdNextReplicaDBShutdown = new AtomicBoolean();
    private final AtomicBoolean holdNextCreatedReplicaDB = new AtomicBoolean();
    private final AtomicBoolean failNextCreation = new AtomicBoolean();
    private final CountDownLatch creatorIsInWindow = new CountDownLatch(1);
    private final CountDownLatch creatorIsReleased = new CountDownLatch(1);
    private final CountDownLatch creatorHoldsItsDomainMap = new CountDownLatch(1);
    private final CountDownLatch domainMapIsReleased = new CountDownLatch(1);
    private final CountDownLatch creatorHoldsItsCreatedReplicaDB = new CountDownLatch(1);
    private final CountDownLatch createdReplicaDBIsReleased = new CountDownLatch(1);
    private final CountDownLatch drainIsInReplicaDBShutdown = new CountDownLatch(1);
    private final CountDownLatch drainIsReleased = new CountDownLatch(1);

    /** The baseDNs of the domains any cursor was opened for, one element per opening. */
    private final List<DN> walkedDomains = new CopyOnWriteArrayList<>();

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
      final ConcurrentMap<Integer, FileReplicaDB> domainMap = super.getExistingOrNewDomainMap(baseDN);
      if (holdNextDomainMapObtained.compareAndSet(true, false))
      {
        creatorHoldsItsDomainMap.countDown();
        await(domainMapIsReleased);
      }
      return domainMap;
    }

    @Override
    FileReplicaDB newReplicaDB(final int serverId, final DN baseDN, final ReplicationServer server,
        final CryptoSuite cryptoSuite, final ReplicationEnvironment replicationEnv) throws ChangelogException
    {
      if (failNextCreation.compareAndSet(true, false))
      {
        throw new ChangelogException(CREATION_FAILURE);
      }
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

    @Override
    public DBCursor<UpdateMsg> getCursorFrom(final DN baseDN, final ServerState startState,
        final CursorOptions options) throws ChangelogException
    {
      walkedDomains.add(baseDN);
      return super.getCursorFrom(baseDN, startState, options);
    }

    void holdNextReplicaDBCreationBeforeItsDomainMapIsInserted()
    {
      holdNextCreation.set(true);
    }

    void holdNextCreationAfterItsDomainMapIsObtained()
    {
      holdNextDomainMapObtained.set(true);
    }

    void holdNextReplicaDBInItsShutdown()
    {
      holdNextReplicaDBShutdown.set(true);
    }

    void holdNextReplicaDBOnceCreated()
    {
      holdNextCreatedReplicaDB.set(true);
    }

    void failNextReplicaDBCreation()
    {
      failNextCreation.set(true);
    }

    void awaitCreatorInWindow()
    {
      await(creatorIsInWindow);
    }

    void awaitCreatorHoldingItsDomainMap()
    {
      await(creatorHoldsItsDomainMap);
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

    void releaseCreatorHoldingItsDomainMap()
    {
      domainMapIsReleased.countDown();
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
      releaseCreatorHoldingItsDomainMap();
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
}
