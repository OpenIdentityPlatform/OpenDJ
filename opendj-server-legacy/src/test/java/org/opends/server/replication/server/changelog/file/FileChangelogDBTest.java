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

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

/**
 * Tests the {@link FileChangelogDB} class, and especially the window between
 * {@link FileChangelogDB#removeDomain(DN)}'s unlocked read of the domainMap and its
 * acquisition of the domainMap monitor, during which a concurrent remover
 * ({@code shutdownDB()}, {@code clearDB()} or another {@code removeDomain()}) may have
 * unmapped the domain.
 */
@SuppressWarnings("javadoc")
public class FileChangelogDBTest extends ReplicationTestCase
{
  private static final int SERVER_ID = 1;

  private DN TEST_ROOT_DN;

  @BeforeClass
  public void setup() throws Exception
  {
    TEST_ROOT_DN = DN.valueOf(TEST_ROOT_DN_STRING);
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
      replicationServer = newReplicationServer();
      final FileChangelogDB changelogDB = (FileChangelogDB) replicationServer.getChangelogDB();
      final FileReplicaDB replicaDB =
          changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, SERVER_ID, replicationServer).getFirst();

      final ConcurrentMap<DN, ConcurrentMap<Integer, FileReplicaDB>> domainToReplicaDBs =
          getDomainToReplicaDBs(changelogDB);
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
      remover.join(TimeUnit.SECONDS.toMillis(30));

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
      replicationServer = newReplicationServer();
      final FileChangelogDB changelogDB = (FileChangelogDB) replicationServer.getChangelogDB();
      final FileReplicaDB replicaDB =
          changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, SERVER_ID, replicationServer).getFirst();

      final ConcurrentMap<DN, ConcurrentMap<Integer, FileReplicaDB>> domainToReplicaDBs =
          getDomainToReplicaDBs(changelogDB);
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
      remover.join(TimeUnit.SECONDS.toMillis(30));

      assertFalse(remover.isAlive(), "removeDomain() did not complete");
      assertThat(thrown.get()).isNull();
      assertThat(domainToReplicaDBs.get(TEST_ROOT_DN)).isSameAs(recreatedDomainMap);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  private ReplicationServer newReplicationServer() throws Exception
  {
    final int changelogPort = findFreePort();
    return new ReplicationServer(
        new ReplServerFakeConfiguration(changelogPort, null, 0, 2, 100, 100, null));
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

  @SuppressWarnings("unchecked")
  private ConcurrentMap<DN, ConcurrentMap<Integer, FileReplicaDB>> getDomainToReplicaDBs(
      FileChangelogDB changelogDB) throws Exception
  {
    final Field field = FileChangelogDB.class.getDeclaredField("domainToReplicaDBs");
    field.setAccessible(true);
    return (ConcurrentMap<DN, ConcurrentMap<Integer, FileReplicaDB>>) field.get(changelogDB);
  }

  private void waitUntilBlockedOn(Thread thread, Object monitor) throws Exception
  {
    final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
    while (System.currentTimeMillis() < deadline)
    {
      final ThreadInfo threadInfo = threadMXBean.getThreadInfo(thread.getId());
      final LockInfo lockInfo = threadInfo != null ? threadInfo.getLockInfo() : null;
      if (lockInfo != null
          && threadInfo.getThreadState() == Thread.State.BLOCKED
          && lockInfo.getIdentityHashCode() == System.identityHashCode(monitor))
      {
        return;
      }
      Thread.sleep(1);
    }
    throw new AssertionError(
        "Timed out waiting for " + thread.getName() + " to block on the domainMap monitor");
  }
}
