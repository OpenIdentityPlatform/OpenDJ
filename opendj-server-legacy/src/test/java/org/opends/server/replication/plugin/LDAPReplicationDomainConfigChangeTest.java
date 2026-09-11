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
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.AssuredType;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.forgerock.opendj.server.config.server.ExternalChangelogDomainCfg;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.testng.annotations.Test;

/**
 * Tests what a configuration change does, and does not, publish to a running
 * {@link LDAPReplicationDomain}.
 */
@SuppressWarnings("javadoc")
public class LDAPReplicationDomainConfigChangeTest extends ReplicationTestCase
{
  private static final int SERVER_ID = 1;
  private static final long HEARTBEAT_INTERVAL_IN_MS = 100000;
  private static final long ASSURED_TIMEOUT_IN_MS = 3000;
  private static final long NEW_ASSURED_TIMEOUT_IN_MS = 7000;
  /** The reason the external changelog configuration of the tests below cannot be read. */
  private static final String UNDECODABLE_ECL_REASON =
      "the external changelog configuration cannot be decoded";
  private static final String DOMAIN_CONFIG_NAME = "config change test";

  /**
   * A configuration whose {@code cn=external changelog} child entry cannot be decoded:
   * the one thing {@code applyConfigurationChange()} does which can fail.
   */
  private static final class UndecodableEclDomainFakeCfg extends DomainFakeCfg
  {
    private final DN configEntryDN;

    UndecodableEclDomainFakeCfg(DN baseDN, int serverId, SortedSet<String> replServers)
    {
      this(baseDN, serverId, replServers, null);
    }

    /**
     * @param configEntryDN
     *          The entry this configuration is stored in, or {@code null} to keep the DN
     *          of {@link DomainFakeCfg}, which the server configuration does not carry:
     *          what an unreadable external changelog configuration does depends on
     *          whether the entry which would carry it is there.
     */
    UndecodableEclDomainFakeCfg(DN baseDN, int serverId, SortedSet<String> replServers,
        DN configEntryDN)
    {
      super(baseDN, serverId, replServers);
      this.configEntryDN = configEntryDN;
    }

    @Override
    public DN dn()
    {
      return configEntryDN != null ? configEntryDN : super.dn();
    }

    @Override
    public ExternalChangelogDomainCfg getExternalChangelogDomain() throws ConfigException
    {
      throw new ConfigException(LocalizableMessage.raw(UNDECODABLE_ECL_REASON));
    }
  }

  /** An ECL domain which refuses every change it is handed. */
  private static final class RejectingExternalChangelogDomain extends ExternalChangelogDomain
  {
    RejectingExternalChangelogDomain(LDAPReplicationDomain domain, ExternalChangelogDomainCfg cfg)
    {
      super(domain, cfg);
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(ExternalChangelogDomainCfg configuration)
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
      ccr.addMessage(LocalizableMessage.raw("the ECL domain refused the change"));
      return ccr;
    }
  }

  @Test
  public void changeWhichCouldNotBeAppliedLeavesThePreviousConfigurationRunning() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    try
    {
      final SortedSet<String> replServers = unstartedReplicationServer();
      final LDAPReplicationDomain domain = startDomain(new DomainFakeCfg(baseDN, SERVER_ID, replServers));

      // Connected to no replication server, the default isolation policy rejects the updates.
      assertEquals(modifyBaseEntry(baseDN).getResultCode(), ResultCode.UNWILLING_TO_PERFORM);

      final DomainFakeCfg refused = new UndecodableEclDomainFakeCfg(baseDN, SERVER_ID, replServers);
      refused.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);
      domain.applyConfigurationChange(refused);

      assertEquals(modifyBaseEntry(baseDN).getResultCode(), ResultCode.UNWILLING_TO_PERFORM,
          "the domain went on running the isolation policy of a change it reported as failed");
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
    }
  }

  @Test
  public void changeWhichCouldNotBeAppliedSaysWhy() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DN configEntryDN = addDomainConfigurationEntry(baseDN);
    try
    {
      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      assertNotNull(domain, "the domain was not created from its configuration entry");

      final ConfigChangeResult ccr = domain.applyConfigurationChange(new UndecodableEclDomainFakeCfg(
          baseDN, SERVER_ID, unstartedReplicationServer(), configEntryDN));

      assertEquals(ccr.getResultCode(), ResultCode.OTHER);
      assertTrue(ccr.getMessages().toString().contains(UNDECODABLE_ECL_REASON),
          "the administrator was told the change failed, but not what failed: "
              + ccr.getMessages());
    }
    finally
    {
      removeDomainConfigurationEntry(configEntryDN);
    }
  }

  @Test
  public void changeWhoseExternalChangelogConfigurationCannotBeReadIsRefusedBeforeItIsWritten()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final DN configEntryDN = addDomainConfigurationEntry(baseDN);
    try
    {
      final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
      assertNotNull(domain, "the domain was not created from its configuration entry");

      final List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
      final boolean acceptable = domain.isConfigurationChangeAcceptable(
          new UndecodableEclDomainFakeCfg(
              baseDN, SERVER_ID, unstartedReplicationServer(), configEntryDN),
          unacceptableReasons);

      assertFalse(acceptable,
          "the change was accepted, so the modified entry is written to the server "
              + "configuration before the domain finds out it cannot be applied");
      assertTrue(unacceptableReasons.toString().contains(UNDECODABLE_ECL_REASON),
          "the administrator was not told what could not be read: " + unacceptableReasons);
    }
    finally
    {
      removeDomainConfigurationEntry(configEntryDN);
    }
  }

  @Test
  public void assuredConfigurationIsAppliedToADomainWhichOwnsItsSession() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    try
    {
      final SortedSet<String> replServers = unstartedReplicationServer();
      final LDAPReplicationDomain domain =
          startDomain(assuredCfg(baseDN, replServers, ASSURED_TIMEOUT_IN_MS));
      assertEquals(domain.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);

      // Disabled is what an online import leaves the domain: it owns its session, so this
      // change is applied to it without a session being restarted for it.
      domain.disable();
      waitForListenerThread(baseDN, false);

      final DomainFakeCfg safeRead = new DomainFakeCfg(baseDN, SERVER_ID, replServers,
          AssuredType.SAFE_READ, 1, -1, NEW_ASSURED_TIMEOUT_IN_MS, null);
      safeRead.setHeartbeatInterval(HEARTBEAT_INTERVAL_IN_MS);
      final ConfigChangeResult ccr = domain.applyConfigurationChange(safeRead);

      assertEquals(ccr.getResultCode(), ResultCode.SUCCESS, ccr.getMessages().toString());
      assertFalse(hasListenerThread(baseDN),
          "the change started a session on a domain which was disabled for a total update");
      assertEquals(domain.getAssuredMode(), AssuredMode.SAFE_READ_MODE,
          "the assured configuration was dropped although the change reported success");
      assertEquals(domain.getAssuredTimeout(), NEW_ASSURED_TIMEOUT_IN_MS,
          "the assured timeout was dropped although the change reported success");
      assertTrue(ccr.adminActionRequired(),
          "the assured configuration is negotiated as a session comes up, and this domain was"
              + " given no session to negotiate it over");

      // Left as a total update leaves it: enabled back, on the configuration it was given.
      domain.enable();
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
    }
  }

  @Test
  public void changeIsRefusedWhenTheExternalChangelogDomainRejectsIt() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    try
    {
      final SortedSet<String> replServers = unstartedReplicationServer();
      final DomainFakeCfg cfg = new DomainFakeCfg(baseDN, SERVER_ID, replServers);
      final LDAPReplicationDomain domain = startDomain(cfg);
      replaceEclDomain(domain,
          new RejectingExternalChangelogDomain(domain, cfg.getExternalChangelogDomain()));

      final ConfigChangeResult ccr =
          domain.applyConfigurationChange(new DomainFakeCfg(baseDN, SERVER_ID, replServers));

      assertNotEquals(ccr.getResultCode(), ResultCode.SUCCESS,
          "the ECL domain refused the change and the domain reported it as applied");
      assertFalse(ccr.getMessages().isEmpty(), "the refusal of the ECL domain was not passed on");
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
    }
  }

  @Test
  public void assuredTimeoutIsAppliedAlthoughItNeedsNoReconnection() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    try
    {
      final SortedSet<String> replServers = unstartedReplicationServer();
      final LDAPReplicationDomain domain = startDomain(
          assuredCfg(baseDN, replServers, ASSURED_TIMEOUT_IN_MS));
      assertEquals(domain.getAssuredTimeout(), ASSURED_TIMEOUT_IN_MS);

      // Only the timeout changes, which is the one assured property a session does not
      // have to be restarted for.
      final ConfigChangeResult ccr =
          domain.applyConfigurationChange(assuredCfg(baseDN, replServers, NEW_ASSURED_TIMEOUT_IN_MS));

      assertEquals(domain.getAssuredTimeout(), NEW_ASSURED_TIMEOUT_IN_MS,
          "the new assured timeout was dropped although the change reported success");
      assertFalse(ccr.adminActionRequired(),
          "a change which is live asked the administrator to act: " + ccr.getMessages());
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
    }
  }

  @Test
  public void changeNoSessionCouldBeRestartedForSaysItIsNotLiveYet() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    try
    {
      final LDAPReplicationDomain domain =
          startDomain(new DomainFakeCfg(baseDN, SERVER_ID, unstartedReplicationServer()));

      // Disabled is what an online import leaves the domain: it owns its session, and the
      // replication servers a domain talks to are negotiated as that session comes up.
      domain.disable();
      waitForListenerThread(baseDN, false);

      final ConfigChangeResult ccr = domain.applyConfigurationChange(
          new DomainFakeCfg(baseDN, SERVER_ID, unstartedReplicationServer()));

      assertEquals(ccr.getResultCode(), ResultCode.SUCCESS, ccr.getMessages().toString());
      assertTrue(ccr.adminActionRequired(),
          "the change was reported as fully applied although the session it needs was never"
              + " restarted for it, and a domain left disabled never restarts one");
      assertTrue(ccr.getMessages().toString()
              .contains(NOTE_REPLICATION_DOMAIN_SESSION_NOT_RESTARTED.get(baseDN).toString()),
          "the administrator was not told which domain is waiting for a session: " + ccr.getMessages());
      assertFalse(hasListenerThread(baseDN),
          "the change started a session on a domain which was disabled for a total update");

      // Left as a total update leaves it: enabled back, on the configuration it was given.
      domain.enable();
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
    }
  }

  @Test
  public void externalChangelogConfigurationChangesTheSessionUnderTheServiceStateLock()
      throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    Thread eclChange = null;
    try
    {
      final SortedSet<String> replServers = unstartedReplicationServer();
      final LDAPReplicationDomain domain = startDomain(new DomainFakeCfg(baseDN, SERVER_ID, replServers));

      final SortedSet<String> eclIncludes = new TreeSet<>();
      eclIncludes.add("cn");
      final CountDownLatch applied = new CountDownLatch(1);
      eclChange = new Thread(() -> {
        domain.changeConfig(eclIncludes, new TreeSet<String>());
        applied.countDown();
      }, "ECL configuration change");

      /*
       * Asserted outside the block: a failure inside it would leave the thread running on
       * a domain the cleanup below is about to delete. What is waited for is the thread
       * blocking on the monitor rather than merely being slow, or the assertion would
       * hold for a change which simply took its time.
       */
      final Object serviceStateLock = serviceStateLockOf(domain);
      final boolean blockedOnTheLock;
      final Set<String> eclIncludesWhileLocked;
      synchronized (serviceStateLock)
      {
        eclChange.start();
        blockedOnTheLock = waitForBlockedOn(eclChange, serviceStateLock);
        eclIncludesWhileLocked = new TreeSet<>(domain.getEclIncludes());
      }

      assertTrue(blockedOnTheLock,
          "the ECL configuration changed the session of the domain without holding serviceStateLock");
      /*
       * What the assertion above alone does not tell apart: restartService(), at the end
       * of changeConfig(), takes this lock as well, so a change which applied the
       * attributes first and blocked on the lock afterwards would look just the same. The
       * whole of changeConfig() runs under the lock exactly when the attributes are still
       * unapplied while this thread holds it.
       */
      assertFalse(eclIncludesWhileLocked.contains("cn"),
          "the ECL attributes were applied outside serviceStateLock, and only the session"
              + " restart which follows them was taken under it: " + eclIncludesWhileLocked);
      assertTrue(applied.await(30, SECONDS), "the ECL configuration change never completed");
      assertTrue(domain.getEclIncludes().contains("cn"),
          "the ECL configuration change was reported as done and applied nothing");
    }
    finally
    {
      if (eclChange != null)
      {
        eclChange.join(SECONDS.toMillis(30));
      }
      MultimasterReplication.deleteDomain(baseDN);
    }
  }

  @Test
  public void externalChangelogConfigurationGivesNoSessionBackToADisabledDomain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    try
    {
      final SortedSet<String> replServers = unstartedReplicationServer();
      final LDAPReplicationDomain domain = startDomain(new DomainFakeCfg(baseDN, SERVER_ID, replServers));

      // Disabled is what the domain is for the length of a total update: it owns its session.
      domain.disable();
      waitForListenerThread(baseDN, false);

      final SortedSet<String> eclIncludes = new TreeSet<>();
      eclIncludes.add("cn");
      domain.changeConfig(eclIncludes, new TreeSet<String>());

      assertFalse(hasListenerThread(baseDN),
          "the external changelog configuration started a session on a disabled domain");
      assertTrue(domain.getEclIncludes().contains("cn"),
          "the attributes published to the external changelog were dropped");

      // Left as a total update leaves it: enabled back, with its ServerState loaded again.
      domain.enable();
    }
    finally
    {
      MultimasterReplication.deleteDomain(baseDN);
    }
  }

  /**
   * The listener thread of a domain is the one which says a session is running. Named
   * after the server id as well, or a domain another test class left behind on the same
   * base DN would answer for this one.
   */
  private static boolean hasListenerThread(DN baseDN)
  {
    final String listenerName =
        "Replica DS(" + SERVER_ID + ") listener for domain \"" + baseDN + "\"";
    for (Thread thread : Thread.getAllStackTraces().keySet())
    {
      if (thread.getName().contains(listenerName) && thread.isAlive())
      {
        return true;
      }
    }
    return false;
  }

  private static void waitForListenerThread(DN baseDN, boolean running) throws Exception
  {
    final long deadline = System.currentTimeMillis() + SECONDS.toMillis(30);
    while (hasListenerThread(baseDN) != running && System.currentTimeMillis() < deadline)
    {
      Thread.sleep(100);
    }
    assertEquals(hasListenerThread(baseDN), running,
        "the listener thread of " + baseDN + " never " + (running ? "started" : "stopped"));
  }

  private DomainFakeCfg assuredCfg(DN baseDN, SortedSet<String> replServers, long assuredTimeout)
  {
    final DomainFakeCfg cfg = new DomainFakeCfg(baseDN, SERVER_ID, replServers,
        AssuredType.SAFE_DATA, 1, -1, assuredTimeout, null);
    // The same as the configuration in place, or the broker would restart the session for
    // the heartbeat interval and the change would no longer be the timeout alone.
    cfg.setHeartbeatInterval(HEARTBEAT_INTERVAL_IN_MS);
    return cfg;
  }

  /** A replication server which is not started, so that the domain never connects. */
  private SortedSet<String> unstartedReplicationServer() throws Exception
  {
    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + TestCaseUtils.findFreePort());
    return replServers;
  }

  /**
   * Configures a domain the way the server does, through its configuration entries: what
   * an unreadable external changelog configuration does depends on whether the entry
   * which carries it is there, and the fake configurations of the other tests are stored
   * in no entry at all.
   *
   * @return the DN of the entry the configuration of the domain is stored in
   */
  private DN addDomainConfigurationEntry(DN baseDN) throws Exception
  {
    addSynchroServerEntry(
        "dn: cn=" + DOMAIN_CONFIG_NAME + ",cn=domains," + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + DOMAIN_CONFIG_NAME + "\n"
        + "ds-cfg-base-dn: " + baseDN + "\n"
        + "ds-cfg-replication-server: localhost:" + TestCaseUtils.findFreePort() + "\n"
        + "ds-cfg-server-id: " + SERVER_ID + "\n");
    final DN configEntryDN = synchroServerEntry.getName();
    assertTrue(getServerContext().getConfigurationHandler()
            .hasEntry(DN.valueOf("cn=external changelog," + configEntryDN)),
        "the domain was configured without the external changelog entry these tests need");
    return configEntryDN;
  }

  private void removeDomainConfigurationEntry(DN configEntryDN) throws Exception
  {
    // Deletes the "cn=external changelog" entry below it as well.
    deleteEntry(configEntryDN);
    configEntriesToCleanup.remove(configEntryDN);
    synchroServerEntry = null;
  }

  private LDAPReplicationDomain startDomain(DomainFakeCfg cfg) throws Exception
  {
    cfg.setHeartbeatInterval(HEARTBEAT_INTERVAL_IN_MS);
    final LDAPReplicationDomain domain = MultimasterReplication.createNewDomain(cfg);
    domain.start();
    return domain;
  }

  private ModifyOperation modifyBaseEntry(DN baseDN)
  {
    return getRootConnection().processModify(modifyRequest(baseDN, REPLACE, "description", "test"));
  }

  /**
   * Whether the thread ends up waiting for this very monitor, rather than running to
   * completion or blocking on an unrelated one.
   */
  private static boolean waitForBlockedOn(Thread thread, Object monitor) throws Exception
  {
    final long deadline = System.currentTimeMillis() + SECONDS.toMillis(10);
    while (System.currentTimeMillis() < deadline)
    {
      if (thread.getState() == Thread.State.TERMINATED)
      {
        return false;
      }
      final ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());
      final LockInfo blockedOn = info != null ? info.getLockInfo() : null;
      if (blockedOn != null
          && blockedOn.getIdentityHashCode() == System.identityHashCode(monitor))
      {
        return true;
      }
      Thread.sleep(20);
    }
    return false;
  }

  private static Object serviceStateLockOf(LDAPReplicationDomain domain) throws Exception
  {
    final Field serviceStateLock = LDAPReplicationDomain.class.getDeclaredField("serviceStateLock");
    serviceStateLock.setAccessible(true);
    return serviceStateLock.get(domain);
  }

  private static void replaceEclDomain(LDAPReplicationDomain domain, ExternalChangelogDomain eclDomain)
      throws Exception
  {
    final Field field = LDAPReplicationDomain.class.getDeclaredField("eclDomain");
    field.setAccessible(true);
    field.set(domain, eclDomain);
  }
}
