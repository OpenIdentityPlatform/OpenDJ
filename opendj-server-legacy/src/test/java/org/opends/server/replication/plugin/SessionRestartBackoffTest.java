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
 * And that a request which stood while the domain was disabled is not run once it is
 * enabled back: the change it was made for is gone with the pending changes.
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
   * How long {@code shutdown()} or {@code disable()} may take when the backoff is woken:
   * either drains a replay which is not running, stops a session which is already stopped,
   * and saves the ServerState - {@code shutdown()} by waiting for the checkpointer to do it
   * once more. A checkpointer which sleeps the backoff through while it holds the monitor
   * the wake is given on holds either of them for the seconds the backoff has left instead.
   */
  private static final long WAKE_BOUND_IN_MS = 1500;

  /**
   * How long the checkpointer may take to save a change made once the domain is enabled
   * back: its next tick, at most a second away, plus the save. One which was left asleep
   * in the backoff {@code disable()} cut saves nothing until the backoff is out.
   */
  private static final long CHECKPOINT_BOUND_IN_MS = 2500;

  /**
   * How long a request left standing is given to be run by the checkpointer: two of its
   * ticks, where one which is standing when the domain is enabled back is run on the first.
   */
  private static final long LEFTOVER_REQUEST_BOUND_IN_MS = 2000;

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
      assertTrue(domain.isConnected(), "the domain did not connect to its replication server");
      broker = openReplicationSession(baseDN, BROKER_ID, 100, rsPort, 1000);

      leaveTheCheckpointerInTheBackoff(domain, broker, "user.925.shutdown");

      final long started = System.nanoTime();
      domain.shutdown();
      final long tookMs = NANOSECONDS.toMillis(System.nanoTime() - started);

      assertTrue(tookMs < WAKE_BOUND_IN_MS, "shutdown() waited " + tookMs
          + " ms: it sat out the backoff of the session restart the state checkpointer"
          + " was waiting through, for a session the domain was stopping anyway");
    }
    finally
    {
      release(domain, broker, replicationServer);
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
      assertTrue(domain.isConnected(), "the domain did not connect to its replication server");
      broker = openReplicationSession(baseDN, BROKER_ID, 100, rsPort, 1000);

      final DN entryDN = leaveTheCheckpointerInTheBackoff(domain, broker, "user.925.disable");

      /*
       * The data of this domain is about to be replaced, then has been. disable() is timed
       * as shutdown() is: its wake takes the monitor the checkpointer waits on, so a
       * checkpointer which sleeps the backoff through while holding that monitor holds
       * disable() for the rest of it - and everything below would then start late enough
       * for the save to land inside its bound all the same.
       */
      final long started = System.nanoTime();
      domain.disable();
      final long tookMs = NANOSECONDS.toMillis(System.nanoTime() - started);
      assertTrue(tookMs < WAKE_BOUND_IN_MS, "disable() waited " + tookMs
          + " ms: it sat out the backoff of the session restart the state checkpointer"
          + " was waiting through, for a session disable() was cutting anyway");
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
      release(domain, broker, replicationServer);
    }
  }

  /**
   * A request which stood while the domain was disabled was made for a change which is
   * gone with the pending changes, and the session {@code enable()} starts asks for
   * everything the ServerState it loads does not cover: run, the request would stop and
   * start that session once for a delivery which can not come. The request is made here by
   * hand, in the place of one made between a replay thread's read of the flag and the
   * clear {@code disable()} does after it.
   * <p>
   * The restart is the checkpointer's to run, within its first tick after the domain is
   * enabled back, so the pin is that the failure it would meet is never spent: a restart
   * which ran would have spent it, and would have left the session it stopped down.
   */
  @Test
  public void aRequestWhichStoodWhileTheDomainWasDisabledIsNotRunOnceItIsEnabledBack()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    LDAPReplicationDomain domain = null;
    try
    {
      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = createReplicationServer(rsPort, "sessionRestartBackoffTestEnableDb");
      domain = startDomain(baseDN, rsPort);
      assertTrue(domain.isConnected(), "the domain did not connect to its replication server");

      domain.disable();
      domain.requestSessionRestart();
      domain.failNextSessionRestarts(1);
      domain.enable();
      assertTrue(domain.isConnected(), "the domain did not come back up once enabled");

      Thread.sleep(LEFTOVER_REQUEST_BOUND_IN_MS);
      assertEquals(domain.getSessionRestartFailuresLeft(), 1, "the request which stood while"
          + " the domain was disabled was run against the session enable() started");
      assertTrue(domain.isConnected(),
          "the session enable() started was stopped for a request made before it");
    }
    finally
    {
      release(domain, null, replicationServer);
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

  /**
   * Lets go of what {@link #leaveTheCheckpointerInTheBackoff} set up, and of the domain and
   * the replication server of the case - each step whatever the one before it threw, or a
   * case which failed on its way up would leave its domain registered for the next one to
   * replace silently.
   */
  private void release(LDAPReplicationDomain domain, ReplicationBroker broker,
      ReplicationServer replicationServer) throws Exception
  {
    try
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
    finally
    {
      try
      {
        if (domain != null)
        {
          MultimasterReplication.deleteDomain(domain.getBaseDN());
        }
      }
      finally
      {
        remove(replicationServer);
      }
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

  /**
   * Creates and starts a domain on the provided base DN, and asserts nothing about it: the
   * caller holds the domain before it looks at it, so that a case which fails there still
   * has it to delete.
   */
  private LDAPReplicationDomain startDomain(DN baseDN, int rsPort) throws Exception
  {
    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + rsPort);
    final DomainFakeCfg domainCfg = new DomainFakeCfg(baseDN, DS_ID, replServers, GROUP_ID);
    domainCfg.setHeartbeatInterval(100000);
    final LDAPReplicationDomain domain = MultimasterReplication.createNewDomain(domainCfg);
    domain.start();
    return domain;
  }

  private ReplicationServer createReplicationServer(int rsPort, String dbDir) throws Exception
  {
    final ReplServerFakeConfiguration conf = new ReplServerFakeConfiguration(
        rsPort, dbDir, 0, RS_ID, 0, 100, new TreeSet<String>(), GROUP_ID, 1000, 5000);
    return new ReplicationServer(conf);
  }
}
