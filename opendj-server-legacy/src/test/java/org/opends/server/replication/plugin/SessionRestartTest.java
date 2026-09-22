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
package org.opends.server.replication.plugin;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.plugin.LDAPReplicationDomain.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.server.DataServerHandler;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.Entry;
import org.opends.server.types.OperationType;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TestTimer.CallableVoid;
import org.testng.annotations.Test;

/**
 * Tests which sessions a configuration change restarts. A domain which stopped its own
 * session - one which is shutting down, or whose data is being replaced - owns it and is the
 * one which brings it back; one which is running its session is given a new one, and the
 * replication server hears the change over it.
 */
@SuppressWarnings("javadoc")
public class SessionRestartTest extends ReplicationTestCase
{
  private static final int RS_ID = 601;
  private static final int DS_ID = 1;
  /** The replica whose changes the domain under test replays. */
  private static final int PUBLISHER_ID = 2;
  private static final int GROUP_ID = 1;

  @Test
  public void aConfigurationChangeDoesNotStartTheSessionOfADisabledDomain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    LDAPReplicationDomain domain = null;
    try
    {
      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = createReplicationServer(rsPort, "sessionRestartTestDisabledDb");

      final DomainFakeCfg domainCfg = newDomainCfg(baseDN, rsPort);
      domain = MultimasterReplication.createNewDomain(domainCfg);
      domain.start();
      assertTrue(domain.isConnected());

      // The data this domain replicates is about to be replaced by an import or a restore.
      domain.disable();
      assertFalse(domain.isConnected());

      final ConfigChangeResult ccr = changeEclIncludes(domain, domainCfg);

      assertFalse(domain.isConnected(),
          "a configuration change started the session of a disabled domain");
      // the restart the change asked for was refused, and said so rather than reported as applied
      assertTrue(ccr.adminActionRequired(), "the refused restart was reported as fully applied");
    }
    finally
    {
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
      remove(replicationServer);
    }
  }

  @Test
  public void aConfigurationChangeDoesNotStartTheSessionOfAShutDownDomain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    LDAPReplicationDomain domain = null;
    try
    {
      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = createReplicationServer(rsPort, "sessionRestartTestShutdownDb");

      final DomainFakeCfg domainCfg = newDomainCfg(baseDN, rsPort);
      domain = MultimasterReplication.createNewDomain(domainCfg);
      domain.start();
      assertTrue(domain.isConnected());

      domain.shutdown();
      assertFalse(domain.isConnected());

      final ConfigChangeResult ccr = changeEclIncludes(domain, domainCfg);

      assertFalse(domain.isConnected(),
          "a configuration change started the session of a domain which has shut down");
      // the restart the change asked for was refused, and said so rather than reported as applied
      assertTrue(ccr.adminActionRequired(), "the refused restart was reported as fully applied");
    }
    finally
    {
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
      remove(replicationServer);
    }
  }

  /**
   * The twin of the two above: a domain which is running its session is given a new one by
   * the change, and the replication server is what tells. The attributes the external
   * changelog includes are stored in the domain before the session is restarted for them,
   * so the domain says the change is applied whether or not the restart ran; the
   * replication server hears the list once, in the {@code StartSessionMsg} of a session, and
   * a {@code DataServerHandler} which carries the new list is a session started after the
   * change.
   */
  @Test
  public void aConfigurationChangeRestartsTheSessionOfALiveDomain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    LDAPReplicationDomain domain = null;
    try
    {
      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = createReplicationServer(rsPort, "sessionRestartTestLiveDb");

      final DomainFakeCfg domainCfg = newDomainCfg(baseDN, rsPort);
      domain = MultimasterReplication.createNewDomain(domainCfg);
      domain.start();
      assertTrue(domain.isConnected());
      // What the session which is running told the replication server: no attribute at all.
      assertThat(eclIncludesHeardBy(replicationServer, baseDN)).doesNotContain("cn");

      final ConfigChangeResult ccr = changeEclIncludes(domain, domainCfg);

      // the restart was run, so there is nothing to tell the administrator to wait for
      assertFalse(ccr.adminActionRequired(),
          "a restart which was run was reported as refused: " + ccr.getMessages());
      final ReplicationServer rs = replicationServer;
      new TestTimer.Builder().maxSleep(5, SECONDS).sleepTimes(100, MILLISECONDS).toTimer()
          .repeatUntilSuccess(new CallableVoid()
      {
        @Override
        public void call() throws Exception
        {
          assertThat(eclIncludesHeardBy(rs, baseDN))
              .as("the replication server was never told the new list, so no session was"
                  + " started after the change")
              .contains("cn");
        }
      });
    }
    finally
    {
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
      remove(replicationServer);
    }
  }

  /**
   * A replay thread which could not apply a change stops the session so that the change
   * is delivered again, and waits out a backoff before it starts the session back. The
   * session it stopped is its claim; a configuration change which stops and starts the
   * session while it waits leaves that claim stale, and the thread which comes back from
   * its wait leaves the session which replaced the one it stopped alone.
   * <p>
   * What a stale claim which is not declined does to the session is nothing today: the
   * broker and the listener thread are up already, and starting them again is a no-op. The
   * one thing which tells a declined claim from one which was acted on is the generation of
   * the session, which counts every start: the thread which acted on its stale claim leaves
   * it one past where the restart which replaced its session left it.
   */
  @Test
  public void aReplayThreadLeavesAloneTheSessionWhichReplacedTheOneItStopped() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    LDAPReplicationDomain domain = null;
    ReplicationBroker publisher = null;
    try
    {
      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = createReplicationServer(rsPort, "sessionRestartTestStaleClaimDb");
      domain = MultimasterReplication.createNewDomain(newDomainCfg(baseDN, rsPort));
      domain.start();
      assertTrue(domain.isConnected());

      final Entry entry = TestCaseUtils.addEntry(
          "dn: cn=stale claim," + baseDN,
          "objectClass: top",
          "objectClass: person",
          "cn: stale claim",
          "sn: claim");
      final String uuid = getEntry(entry.getName(), 1, true).parseAttribute("entryuuid").asString();
      publisher = openReplicationSession(baseDN, PUBLISHER_ID, 100, rsPort, 1000);

      final LDAPReplicationDomain replica = domain;
      final Object serviceStateLock = serviceStateLockOf(replica);
      try
      {
        /*
         * The backend refuses the delete for longer than the replay is retried in place,
         * so the replay thread stops the session for the change to be delivered again -
         * and serves it once the session was restarted for it, so that the change is
         * applied over the session started next and asks for no restart of its own.
         */
        ShortCircuitPlugin.registerShortCircuit(OperationType.DELETE, "PreParse",
            ResultCode.UNAVAILABLE.intValue(), IN_PLACE_REPLAY_ATTEMPTS + 2);
        publisher.publish(new DeleteMsg(entry.getName(), new CSNGenerator(PUBLISHER_ID, 0).newCSN(), uuid));

        /*
         * The lock is taken and let go until it is taken while the session is stopped: the
         * replay thread stopped it under the lock and let the lock go for its wait, so the
         * claim it holds is standing, and the lock is held for the rest of that wait. The
         * restart below runs while the claim is standing however short the wait is.
         */
        final long claim;
        final long replaced;
        final long deadline = System.currentTimeMillis() + SECONDS.toMillis(30);
        while (true)
        {
          synchronized (serviceStateLock)
          {
            if (!replica.isConnected())
            {
              claim = sessionGenerationOf(replica);
              // Something else restarts the session while the replay thread waits: the
              // attributes the external changelog includes are changed.
              replica.changeConfig(newTreeSet("cn"), new TreeSet<String>());
              replaced = sessionGenerationOf(replica);
              break;
            }
          }
          assertTrue(System.currentTimeMillis() < deadline, "the failed replay never stopped the session");
          Thread.sleep(5);
        }
        assertNotEquals(replaced, claim, "the restart left the generation of the session where it was");
        assertTrue(replica.isConnected(), "the restart left the domain without a session");

        // The change is delivered again over the session which replaced the stopped one,
        // and applied; the replay thread comes back from its wait and finds its claim stale.
        assertNull(getEntry(entry.getName(), 30000, false),
            "the change the session was stopped for was not delivered again over the session which replaced it");
        new TestTimer.Builder().maxSleep(30, SECONDS).sleepTimes(100, MILLISECONDS).toTimer()
            .repeatUntilSuccess(new CallableVoid()
        {
          @Override
          public void call() throws Exception
          {
            assertFalse(isRecoveringFromAReplayFailure(replica),
                "the replay thread never came back from the restart it asked for");
          }
        });

        synchronized (serviceStateLock)
        {
          assertEquals(sessionGenerationOf(replica), replaced,
              "the replay thread started the session which replaced the one it stopped,"
                  + " as if its claim on the stopped one were still current");
        }
      }
      finally
      {
        ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreParse");
      }
    }
    finally
    {
      if (publisher != null)
      {
        publisher.stop();
      }
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
      remove(replicationServer);
    }
  }

  /**
   * Applies a configuration change which changes the attributes the external changelog
   * includes. The domain hands it to {@code ExternalChangelogDomain}, which asks for the
   * session to be restarted so that the replication server hears the new list.
   *
   * @return the result of the change, so that each test says whether the restart it asked
   *         for was to be run or refused
   */
  private ConfigChangeResult changeEclIncludes(LDAPReplicationDomain domain, DomainFakeCfg domainCfg)
      throws Exception
  {
    final SortedSet<String> eclIncludes = new TreeSet<>();
    eclIncludes.add("cn");
    domainCfg.setExternalChangelogDomain(
        new ExternalChangelogDomainFakeCfg(true, eclIncludes, new TreeSet<String>()));

    final ConfigChangeResult ccr = domain.applyConfigurationChange(domainCfg);
    assertEquals(ccr.getResultCode(), ResultCode.SUCCESS, ccr.getMessages().toString());
    // the change did reach the external changelog configuration of the domain
    assertThat(domain.getEclIncludes()).contains("cn");
    return ccr;
  }

  /**
   * The attributes the replication server was told the domain includes in the external
   * changelog, by the session it holds for the domain right now.
   */
  private static Set<String> eclIncludesHeardBy(ReplicationServer replicationServer, DN baseDN)
  {
    final DataServerHandler ds =
        replicationServer.getReplicationServerDomain(baseDN).getConnectedDSs().get(DS_ID);
    assertNotNull(ds, "the replication server holds no session of the domain");
    return ds.toDSInfo().getEclIncludes();
  }

  private static Object serviceStateLockOf(LDAPReplicationDomain domain) throws Exception
  {
    // Declared where the session lives, next to disableService()/enableService()
    final Field serviceStateLock = ReplicationDomain.class.getDeclaredField("serviceStateLock");
    serviceStateLock.setAccessible(true);
    return serviceStateLock.get(domain);
  }

  /** Read under {@code serviceStateLock}, as {@code getSessionGeneration()} asks. */
  private static long sessionGenerationOf(LDAPReplicationDomain domain) throws Exception
  {
    final Method getSessionGeneration = ReplicationDomain.class.getDeclaredMethod("getSessionGeneration");
    getSessionGeneration.setAccessible(true);
    return (Long) getSessionGeneration.invoke(domain);
  }

  /**
   * Whether a replay thread of the domain is restarting the session for a change it could
   * not apply: set by the thread which took that recovery on, and cleared once the restart
   * it asked for is done with - run, or declined on a stale claim.
   */
  private static boolean isRecoveringFromAReplayFailure(LDAPReplicationDomain domain) throws Exception
  {
    final Field replayFailureRecovery = LDAPReplicationDomain.class.getDeclaredField("replayFailureRecovery");
    replayFailureRecovery.setAccessible(true);
    return ((AtomicBoolean) replayFailureRecovery.get(domain)).get();
  }

  private DomainFakeCfg newDomainCfg(DN baseDN, int rsPort)
  {
    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + rsPort);
    return new DomainFakeCfg(baseDN, DS_ID, replServers, GROUP_ID);
  }

  private ReplicationServer createReplicationServer(int rsPort, String dbDir) throws Exception
  {
    final ReplServerFakeConfiguration conf = new ReplServerFakeConfiguration(
        rsPort, dbDir, 0, RS_ID, 0, 100, new TreeSet<String>(), GROUP_ID, 1000, 5000);
    return new ReplicationServer(conf);
  }
}
