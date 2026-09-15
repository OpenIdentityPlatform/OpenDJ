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
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.replication.plugin.LDAPReplicationDomain.*;
import static org.testng.Assert.*;

import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Entry;
import org.opends.server.types.OperationType;
import org.testng.annotations.Test;

/**
 * Tests that a domain which is going down, or is being disabled, does not wait out the
 * backoff of the session restart its state checkpointer is sitting through: the session
 * that restart would start back is one the domain is stopping anyway, so the wait is on a
 * monitor which {@code shutdown()} and {@code disable()} wake, rather than slept through.
 * <p>
 * The checkpointer is the thread put through the wait here because it is the one whose
 * wait can be seen from a test: {@code shutdown()} waits for it, and it is what saves the
 * ServerState once the domain is enabled back. A replay thread sitting through the same
 * wait is one thread of a shared pool, and its absence shows nowhere.
 */
@SuppressWarnings("javadoc")
public class SessionRestartBackoffTest extends ReplicationTestCase
{
  private static final int RS_ID = 602;
  private static final int DS_ID = 1;
  private static final int BROKER_ID = 2;
  private static final int GROUP_ID = 1;

  /**
   * How long after the second restart has failed this test acts on the domain.
   * <p>
   * The checkpointer reports that failure and goes back to its wait of one second, takes
   * the request back once that second is out, and then holds the backoff of a third
   * restart in a row: three seconds. Acting a fifth of a second into those three lands
   * well inside them, and a wait which is not woken has most of them left to hold whoever
   * is waiting for the checkpointer. A machine slow enough to push the checkpointer's tick
   * past this delay has this test act before the backoff begins, which a wait that is
   * slept through survives - the test then proves less, but reports nothing false.
   */
  private static final long INTO_THE_BACKOFF_IN_MS = 1200;

  /**
   * How long {@code shutdown()} may take when the backoff is woken: it drains a replay
   * which is not running, stops a session which is already stopped, and waits for the
   * checkpointer to save the ServerState once more. A checkpointer which sleeps the
   * backoff through holds it for the seconds the backoff has left instead.
   */
  private static final long SHUTDOWN_BOUND_IN_MS = 1500;

  /**
   * How long the checkpointer may take to save a change made once the domain is enabled
   * back: its next tick, at most a second away, plus the save. One which was left asleep
   * in the backoff {@code disable()} cut saves nothing until the backoff is out.
   */
  private static final long CHECKPOINT_BOUND_IN_MS = 2500;

  @Test
  public void aDomainWhichIsShutDownDoesNotWaitOutTheBackoffOfItsSessionRestart()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    LDAPReplicationDomain domain = null;
    ReplicationBroker broker = null;
    try
    {
      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = createReplicationServer(rsPort, "sessionRestartBackoffTestShutdownDb");
      domain = startDomain(baseDN, rsPort);
      broker = openReplicationSession(baseDN, BROKER_ID, 100, rsPort, 1000);

      leaveTheCheckpointerInTheBackoff(domain, broker, "user.925.shutdown");

      final long started = System.nanoTime();
      domain.shutdown();
      final long tookMs = NANOSECONDS.toMillis(System.nanoTime() - started);

      assertTrue(tookMs < SHUTDOWN_BOUND_IN_MS, "shutdown() waited " + tookMs
          + " ms: it sat out the backoff of the session restart the state checkpointer"
          + " was waiting through, for a session the domain was stopping anyway");
    }
    finally
    {
      release(domain, broker);
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
      remove(replicationServer);
    }
  }

  @Test
  public void aDomainWhichIsDisabledDoesNotWaitOutTheBackoffOfItsSessionRestart()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    LDAPReplicationDomain domain = null;
    ReplicationBroker broker = null;
    try
    {
      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = createReplicationServer(rsPort, "sessionRestartBackoffTestDisableDb");
      domain = startDomain(baseDN, rsPort);
      broker = openReplicationSession(baseDN, BROKER_ID, 100, rsPort, 1000);

      final DN entryDN = leaveTheCheckpointerInTheBackoff(domain, broker, "user.925.disable");

      // The data of this domain is about to be replaced, then has been.
      domain.disable();
      domain.enable();
      assertTrue(domain.isConnected(), "the domain did not come back up once enabled");

      /*
       * A change of this replica's own, for the checkpointer to save: nothing but the
       * checkpointer writes the ServerState while the domain is up, so the change reaching
       * the saved state says that the checkpointer is ticking rather than still asleep in
       * the backoff disable() cut. It is read as the CSN the change was given, since the
       * change the restart was asked for is delivered again over the new session and is
       * recorded in that state as well.
       */
      final ModifyOperation modify = getRootConnection().processModify(
          modifyRequest(baseDN, REPLACE, "description", "the checkpointer is ticking"));
      assertEquals(modify.getResultCode(), ResultCode.SUCCESS,
          modify.getAdditionalLogItems().toString());
      final CSN csn = OperationContext.getCSN(modify);
      assertNotNull(csn, "the change of this replica's own was given no CSN");

      final long deadline = System.nanoTime() + MILLISECONDS.toNanos(CHECKPOINT_BOUND_IN_MS);
      while (!persistedServerState(baseDN).cover(csn))
      {
        assertTrue(System.nanoTime() < deadline, "the state checkpointer did not save the"
            + " ServerState within " + CHECKPOINT_BOUND_IN_MS + " ms of the domain being"
            + " enabled back: it was left asleep in the backoff of the session restart"
            + " disable() cut");
        Thread.sleep(50);
      }

      // The change the restart was asked for is delivered over the session enable() started.
      assertNull(getEntry(entryDN, 30000, false),
          "the change was not delivered again over the session the domain was enabled with");
    }
    finally
    {
      release(domain, broker);
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
      remove(replicationServer);
    }
  }

  /**
   * Leaves the state checkpointer of the provided domain sitting through the backoff of
   * a session restart, with the session stopped.
   * <p>
   * A change whose replay fails for longer than it is retried in place has the session
   * restarted for it to be delivered again, and the first two restarts fail where they
   * would start the session back: the one the replay thread runs, and the one the
   * checkpointer runs for the request it gave back. The checkpointer runs the third, and
   * it is the checkpointer whichever thread the second failure fell to, since the replay
   * thread leaves on the failure it meets and no delivery brings one back over a session
   * which is down. That third restart is owed the backoff of three restarts in a row, and
   * this returns once the checkpointer is inside it.
   *
   * @return the DN of the entry the change which is waiting to be delivered again deletes
   */
  private DN leaveTheCheckpointerInTheBackoff(
      LDAPReplicationDomain domain, ReplicationBroker broker, String uid) throws Exception
  {
    final Entry entry = TestCaseUtils.addEntry(
        "dn: uid=" + uid + "," + domain.getBaseDN(),
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: " + uid,
        "cn: Aaccf Amar",
        "sn: Amar");
    final String uuid = getEntry(entry.getName(), 1, true).parseAttribute("entryuuid").asString();

    /*
     * Unavailable for the attempts in place of the first delivery and two more, so that
     * the delivery a session enabled back brings ends in the change being applied.
     */
    ShortCircuitPlugin.registerShortCircuit(OperationType.DELETE, "PreParse",
        ResultCode.UNAVAILABLE.intValue(), IN_PLACE_REPLAY_ATTEMPTS + 2);
    domain.failNextSessionRestarts(2);

    final CSNGenerator gen = new CSNGenerator(BROKER_ID, 0);
    broker.publish(new DeleteMsg(entry.getName(), gen.newCSN(), uuid));

    final long deadline = System.nanoTime() + SECONDS.toNanos(30);
    while (domain.getSessionRestartFailuresLeft() > 0)
    {
      assertTrue(System.nanoTime() < deadline,
          "the two session restarts which were asked to fail did not both run within 30 s");
      Thread.sleep(50);
    }
    Thread.sleep(INTO_THE_BACKOFF_IN_MS);
    assertFalse(domain.isConnected(),
        "the session was started back before this test could act on the domain");
    return entry.getName();
  }

  /** Lets go of what {@link #leaveTheCheckpointerInTheBackoff} set up. */
  private void release(LDAPReplicationDomain domain, ReplicationBroker broker)
  {
    ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreParse");
    if (domain != null)
    {
      domain.failNextSessionRestarts(0);
    }
    if (broker != null)
    {
      broker.stop();
    }
  }

  private ServerState persistedServerState(DN baseDN) throws Exception
  {
    final ServerState persisted = new ServerState();
    for (String value : getEntry(baseDN, 1, true).parseAttribute("ds-sync-state").asSetOfString())
    {
      persisted.update(new CSN(value));
    }
    return persisted;
  }

  private LDAPReplicationDomain startDomain(DN baseDN, int rsPort) throws Exception
  {
    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + rsPort);
    final DomainFakeCfg domainCfg = new DomainFakeCfg(baseDN, DS_ID, replServers, GROUP_ID);
    domainCfg.setHeartbeatInterval(100000);
    final LDAPReplicationDomain domain = MultimasterReplication.createNewDomain(domainCfg);
    domain.start();
    assertTrue(domain.isConnected(), "the domain did not connect to its replication server");
    return domain;
  }

  private ReplicationServer createReplicationServer(int rsPort, String dbDir) throws Exception
  {
    final ReplServerFakeConfiguration conf = new ReplServerFakeConfiguration(
        rsPort, dbDir, 0, RS_ID, 0, 100, new TreeSet<String>(), GROUP_ID, 1000, 5000);
    return new ReplicationServer(conf);
  }
}
