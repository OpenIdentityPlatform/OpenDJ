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

import static java.nio.charset.StandardCharsets.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationBroker;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests a configuration change while this replica is the target of a total update.
 * <p>
 * The import of a total update streams over the session of the domain, on its listener
 * thread, and a configuration change which restarts that session for what it carries stops
 * the broker the import is reading (issue #1040). Through the server configuration the
 * change holds the lock of the configuration while it runs, and the restart waits for the
 * listener thread to end - which needs that lock to enable the backend back once the
 * stream ends: the change never returns. Reached below the configuration listeners, as the
 * change of an entry which was accepted before the import started reaches it, the restart
 * ends the import on the entries which had arrived.
 * <p>
 * The exporter is a broker of this test, so that the test says when the entries arrive:
 * the change is made while the import is waiting for them.
 */
@SuppressWarnings("javadoc")
public class ConfigChangeDuringImportTest extends ReplicationTestCase
{
  /**
   * The memory backend of {@code o=test} loses its data when it is disabled and enabled
   * back, which is what an import does to the backend it replaces: a total update needs a
   * backend which keeps what was imported into it.
   */
  private static final String EXAMPLE_DN = "dc=example,dc=com";
  private static final int RS_ID = 612;
  private static final int DS_ID = 1;
  private static final int EXPORTER_ID = 2;
  private static final int INIT_WINDOW = 100;
  private static final String DOMAIN_CONFIG_NAME = "config change during import test";
  private static final String IMPORTED_ENTRY_DN = "cn=imported,ou=People," + EXAMPLE_DN;
  /** How long a configuration change is given to return before it is read as hung. */
  private static final long CHANGE_TIMEOUT_IN_MS = 30_000;

  private DN baseDN;
  private int rsPort;
  private ReplicationServer replicationServer;
  private LDAPReplicationDomain domain;
  /** The entry the domain is configured in, when it is configured through the server. */
  private DN domainConfigDN;
  private ReplicationBroker exporter;

  @BeforeMethod
  public void setUpLocal() throws Exception
  {
    baseDN = DN.valueOf(EXAMPLE_DN);
    TestCaseUtils.clearBackend("userRoot", EXAMPLE_DN);

    rsPort = TestCaseUtils.findFreePort();
    replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
        rsPort, "configChangeDuringImportTestDb", 0, RS_ID, 0, 100, new TreeSet<String>()));
  }

  @AfterMethod(timeOut = 120_000)
  public void tearDown() throws Exception
  {
    try
    {
      stop(exporter);
      if (domainConfigDN != null)
      {
        // Deletes the "cn=external changelog" entry below it, and the domain, as well.
        deleteEntry(domainConfigDN);
        configEntriesToCleanup.remove(domainConfigDN);
        synchroServerEntry = null;
      }
      else if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
    }
    finally
    {
      exporter = null;
      domainConfigDN = null;
      domain = null;
      remove(replicationServer);
    }
  }

  /**
   * A change of the external changelog entry while the import streams must be refused, as
   * a change of the domain entry is.
   * <p>
   * The change is made the way {@code dsconfig} makes it, through the server configuration,
   * which holds the lock of the configuration for the length of it. Without the refusal the
   * attributes are applied and the session restarted for them: the restart stops the broker
   * the import is reading and waits for the listener thread, which ends the import on what
   * had arrived and then waits for the lock of the configuration to enable the backend back
   * - the change never returns, the backend stays deregistered, and every configuration
   * change of the server after it waits on the same lock.
   */
  @Test(timeOut = 180_000)
  public void aChangeOfTheExternalChangelogEntryIsRefusedWhileATotalUpdateRuns() throws Exception
  {
    configureDomainThroughTheServer();
    final String[] exported = exportedEntries();
    startImportInto(exported.length);

    final ModifyOperation change = changeConfigurationEntry(
        DN.valueOf("cn=external changelog," + domainConfigDN), "ds-cfg-ecl-include", "cn");

    assertEquals(change.getResultCode(), ResultCode.UNWILLING_TO_PERFORM,
        "a change of the external changelog entry was accepted while a total update ran: "
            + change.getErrorMessage());
    assertThat(change.getErrorMessage().toString())
        .as("the refusal does not say a total update is the reason")
        .contains(NOTE_ERR_CANNOT_CHANGE_CONFIG_DURING_TOTAL_UPDATE.get().toString());

    finishImport(exported);
    assertImported(exported);
    assertFalse(domain.getEclIncludes().contains("cn"),
        "the refused change of the attributes published to the external changelog was applied");
  }

  /**
   * A change of the domain entry while the import streams is refused, as it was before
   * this fix: the twin of the case above, pinned so that the two entries keep answering the
   * same thing.
   */
  @Test(timeOut = 180_000)
  public void aChangeOfTheDomainEntryIsRefusedWhileATotalUpdateRuns() throws Exception
  {
    configureDomainThroughTheServer();
    final String[] exported = exportedEntries();
    startImportInto(exported.length);

    final ModifyOperation change =
        changeConfigurationEntry(domainConfigDN, "ds-cfg-assured-type", "safe-read");

    assertEquals(change.getResultCode(), ResultCode.UNWILLING_TO_PERFORM,
        "a change of the domain entry was accepted while a total update ran: "
            + change.getErrorMessage());
    assertThat(change.getErrorMessage().toString())
        .as("the refusal does not say a total update is the reason")
        .contains(NOTE_ERR_CANNOT_CHANGE_CONFIG_DURING_TOTAL_UPDATE.get().toString());

    finishImport(exported);
    assertImported(exported);
    assertEquals(domain.getAssuredMode(), AssuredMode.SAFE_DATA_MODE,
        "the refused change of the assured configuration was applied");
  }

  /**
   * A change of the domain configuration which reaches the domain while the import streams
   * - one accepted before the import started - must leave the session to the import.
   * <p>
   * The assured configuration is negotiated as the session comes up, so the change asks for
   * a restart. The restart is refused and reported, the way it is for a domain disabled for
   * a total update: the configuration is stored, the import ends on the session it started
   * on, and the session the import brings up next negotiates what was stored.
   */
  @Test(timeOut = 180_000)
  public void aChangeOfTheDomainConfigurationLeavesTheSessionToTheImport() throws Exception
  {
    startDomain(domainCfg(AssuredType.NOT_ASSURED));
    final String[] exported = exportedEntries();
    startImportInto(exported.length);
    final Thread listener = listenerThread();
    assertNotNull(listener, "the import is running on no listener thread");

    final ConfigChangeResult ccr = domain.applyConfigurationChange(domainCfg(AssuredType.SAFE_READ));

    assertEquals(ccr.getResultCode(), ResultCode.SUCCESS, ccr.getMessages().toString());
    assertTrue(ccr.adminActionRequired(),
        "the change was reported as live although the session was not restarted for it");
    assertThat(ccr.getMessages().toString())
        .contains(NOTE_REPLICATION_DOMAIN_SESSION_NOT_RESTARTED.get(baseDN).toString());
    assertSame(listenerThread(), listener,
        "the session the import streams over was restarted for the change");

    finishImport(exported);
    assertImported(exported);
    assertEquals(domain.getAssuredMode(), AssuredMode.SAFE_READ_MODE,
        "the assured configuration was dropped although the change reported success");
  }

  /**
   * A change of the attributes published to the external changelog which reaches the domain
   * while the import streams must leave the session to the import: the attributes are
   * stored, and the session the import brings up next publishes them.
   */
  @Test(timeOut = 180_000)
  public void aChangeOfTheExternalChangelogAttributesLeavesTheSessionToTheImport() throws Exception
  {
    startDomain(domainCfg(AssuredType.NOT_ASSURED));
    final String[] exported = exportedEntries();
    startImportInto(exported.length);
    final Thread listener = listenerThread();
    assertNotNull(listener, "the import is running on no listener thread");

    final SortedSet<String> eclIncludes = new TreeSet<>();
    eclIncludes.add("cn");
    domain.changeConfig(eclIncludes, new TreeSet<String>());

    assertSame(listenerThread(), listener,
        "the session the import streams over was restarted for the change");

    finishImport(exported);
    assertImported(exported);
    assertTrue(domain.getEclIncludes().contains("cn"),
        "the attributes published to the external changelog were dropped");
  }

  /**
   * Configures the domain the way the server does, through its configuration entry: the
   * change listeners of that entry and of the "cn=external changelog" entry below it are
   * registered, and a change of either goes through the lock of the configuration.
   */
  private void configureDomainThroughTheServer() throws Exception
  {
    addSynchroServerEntry(
        "dn: cn=" + DOMAIN_CONFIG_NAME + ",cn=domains," + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + DOMAIN_CONFIG_NAME + "\n"
        + "ds-cfg-base-dn: " + EXAMPLE_DN + "\n"
        + "ds-cfg-replication-server: localhost:" + rsPort + "\n"
        + "ds-cfg-server-id: " + DS_ID + "\n"
        + "ds-cfg-assured-type: safe-data\n"
        + "ds-cfg-assured-sd-level: 1\n");
    domainConfigDN = synchroServerEntry.getName();
    assertTrue(getServerContext().getConfigurationHandler()
            .hasEntry(DN.valueOf("cn=external changelog," + domainConfigDN)),
        "the domain was configured without the external changelog entry this test changes");
    domain = MultimasterReplication.findDomain(baseDN, null);
    assertNotNull(domain, "the configuration entry created no domain");
    assertTrue(domain.isConnected(), "the domain did not connect to the replication server");
    exporter = openReplicationSession(baseDN, EXPORTER_ID, 100, rsPort, 10000);
  }

  private void startDomain(DomainFakeCfg cfg) throws Exception
  {
    domain = MultimasterReplication.createNewDomain(cfg);
    domain.start();
    assertTrue(domain.isConnected(), "the domain did not connect to the replication server");
    exporter = openReplicationSession(baseDN, EXPORTER_ID, 100, rsPort, 10000);
  }

  /**
   * A configuration which differs from the one the domain was started on by its assured
   * type alone: the broker properties are the same, so the change asks for no restart of
   * its own and what is left is the one the assured configuration asks for.
   */
  private DomainFakeCfg domainCfg(AssuredType assuredType)
  {
    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + rsPort);
    return new DomainFakeCfg(baseDN, DS_ID, replServers, assuredType, 1, -1, 1000, null);
  }

  /**
   * Changes a configuration entry the way {@code dsconfig} does, through the server
   * configuration, and fails when the change does not return: the restart it asks for
   * waits for the listener thread to end, which waits for the lock of the configuration the
   * change holds. The thread of the change is interrupted then, which gives up that wait
   * and lets the domain be taken down.
   */
  private ModifyOperation changeConfigurationEntry(DN entryDN, String attribute, String value)
      throws Exception
  {
    final AtomicReference<ModifyOperation> result = new AtomicReference<>();
    final Thread change = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        result.set(getRootConnection().processModify(
            modifyRequest(entryDN, REPLACE, attribute, value)));
      }
    }, "configuration change during the import");
    change.start();
    change.join(CHANGE_TIMEOUT_IN_MS);
    if (change.isAlive())
    {
      final String stacks = "\n" + stackOf(change) + "\n" + stackOf(listenerThread());
      change.interrupt();
      change.join(CHANGE_TIMEOUT_IN_MS);
      org.testng.Assert.fail("the change of " + entryDN + " did not return while the import ran:" + stacks);
    }
    return result.get();
  }

  private static String stackOf(Thread thread)
  {
    if (thread == null)
    {
      return "<no thread>";
    }
    final StringBuilder sb = new StringBuilder(thread.getName()).append(" [").append(thread.getState()).append("]\n");
    for (StackTraceElement frame : thread.getStackTrace())
    {
      sb.append("    at ").append(frame).append('\n');
    }
    return sb.toString();
  }

  /**
   * The listener thread of the domain is the one which says which session is running: the
   * import runs on it, and a restart of the session replaces it.
   */
  private Thread listenerThread()
  {
    final String name = "Replica DS(" + DS_ID + ") listener for domain \"" + baseDN + "\"";
    for (Thread thread : Thread.getAllStackTraces().keySet())
    {
      if (thread.getName().contains(name) && thread.isAlive())
      {
        return thread;
      }
    }
    return null;
  }

  /**
   * Has the exporter start a total update into this replica, and returns once the backend
   * of the domain is deregistered for it: from then on the import is reading the session.
   */
  private void startImportInto(int entryCount) throws Exception
  {
    exporter.publish(new InitializeTargetMsg(
        baseDN, EXPORTER_ID, DS_ID, EXPORTER_ID, entryCount, INIT_WINDOW));
    final long deadline = System.currentTimeMillis() + 30_000;
    while (getServerContext().getBackendConfigManager().findLocalBackendForEntry(baseDN) != null)
    {
      assertTrue(System.currentTimeMillis() < deadline,
          "the import did not deregister the backend of the domain");
      Thread.sleep(20);
    }
  }

  /** Has the exporter send the entries of the total update, and waits for the import to end. */
  private void finishImport(String... ldifEntries) throws Exception
  {
    int msgId = 0;
    for (String ldif : ldifEntries)
    {
      exporter.publish(new EntryMsg(EXPORTER_ID, DS_ID, ldif.getBytes(UTF_8), ++msgId));
    }
    exporter.publish(new DoneMsg(EXPORTER_ID, DS_ID));
    final long deadline = System.currentTimeMillis() + 60_000;
    while (domain.ieRunning())
    {
      assertTrue(System.currentTimeMillis() < deadline, "the import did not end");
      Thread.sleep(50);
    }
  }

  private static void assertImported(String... ldifEntries) throws Exception
  {
    for (String ldif : ldifEntries)
    {
      final DN dn = dnOf(ldif);
      assertTrue(entryExists(dn), "the import ended before " + dn
          + " arrived: the session it streams over was stopped from under it");
    }
  }

  /** The data of the exporter: the base entry and two entries below it. */
  private static String[] exportedEntries()
  {
    return new String[] {
      "dn: " + EXAMPLE_DN + "\n"
          + "objectClass: top\n"
          + "objectClass: domain\n"
          + "dc: example\n"
          + "entryUUID: 31111111-1111-1111-1111-111111111111\n"
          + "\n",
      "dn: ou=People," + EXAMPLE_DN + "\n"
          + "objectClass: top\n"
          + "objectClass: organizationalUnit\n"
          + "ou: People\n"
          + "entryUUID: 31111111-1111-1111-1111-111111111112\n"
          + "\n",
      "dn: " + IMPORTED_ENTRY_DN + "\n"
          + "objectClass: top\n"
          + "objectClass: person\n"
          + "cn: imported\n"
          + "sn: imported\n"
          + "entryUUID: 31111111-1111-1111-1111-111111111113\n"
          + "\n",
    };
  }

  private static DN dnOf(String ldif)
  {
    return DN.valueOf(ldif.substring("dn: ".length(), ldif.indexOf('\n')));
  }
}
