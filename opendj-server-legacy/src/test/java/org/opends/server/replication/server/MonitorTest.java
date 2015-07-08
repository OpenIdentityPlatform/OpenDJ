/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.ByteArrayOutputStream;
import java.net.SocketException;
import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.TestCaseUtils;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.server.changelog.file.ECLEnabledDomainPredicate;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

/**
 * Tests for the replicationServer code.
 */
@SuppressWarnings("javadoc")
public class MonitorTest extends ReplicationTestCase
{
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String baseDnStr = TEST_ROOT_DN_STRING;
  private static final String testName = "monitorTest";

  private static final int   WINDOW_SIZE = 10;
  private static final int server1ID = 1;
  private static final int server2ID = 2;
  private static final int server3ID = 3;
  private static final int server4ID = 4;
  private static final int changelog1ID = 21;
  private static final int changelog2ID = 22;
  private static final int changelog3ID = 23;

  private DN baseDN;
  private ReplicationBroker broker2;
  private ReplicationBroker broker3;
  private ReplicationBroker broker4;
  private ReplicationServer replServer1;
  private ReplicationServer replServer2;
  private ReplicationServer replServer3;
  private LDAPReplicationDomain replDomain;

  private static int[] replServerPort = new int[30];

  private void debugInfo(String s)
  {
    logger.error(LocalizableMessage.raw(s));
    if (logger.isTraceEnabled())
    {
      logger.trace("** TEST **" + s);
    }
  }

  /**
   * Set up the environment for performing the tests in this Class.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @Override
  @BeforeClass
  public void setUp() throws Exception
  {
    super.setUp();

    baseDN = DN.valueOf(baseDnStr);
  }

  /**
   * Creates entries necessary to the test.
   */
  private String[] newLDIFEntries()
  {
    return new String[]{
        "dn: " + baseDN + "\n"
            + "objectClass: top\n"
            + "objectClass: organization\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111111\n"
            + "\n",
        "dn: ou=People," + baseDN + "\n"
            + "objectClass: top\n"
            + "objectClass: organizationalUnit\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111112\n"
            + "\n",
        "dn: cn=Fiona Jensen,ou=people," + baseDN + "\n"
            + "objectclass: top\n"
            + "objectclass: person\n"
            + "objectclass: organizationalPerson\n"
            + "objectclass: inetOrgPerson\n"
            + "cn: Fiona Jensen\n"
            + "sn: Jensen\n"
            + "uid: fiona\n"
            + "telephonenumber: +1 408 555 1212\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111113\n"
            + "\n",
        "dn: cn=Robert Langman,ou=people," + baseDN + "\n"
            + "objectclass: top\n"
            + "objectclass: person\n"
            + "objectclass: organizationalPerson\n"
            + "objectclass: inetOrgPerson\n"
            + "cn: Robert Langman\n"
            + "sn: Langman\n"
            + "uid: robert\n"
            + "telephonenumber: +1 408 555 1213\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111114\n"
            + "\n"
    };
  }

  /**
   * Creates a new replicationServer.
   * @param changelogId The serverID of the replicationServer to create.
   * @param all         Specifies whether to connect the created replication
   *                    server to the other replication servers in the test.
   * @return The new created replication server.
   */
  private ReplicationServer createReplicationServer(int changelogId,
      boolean all, String suffix) throws Exception
  {
    SortedSet<String> servers = new TreeSet<>();
    if (all)
    {
      if (changelogId != changelog1ID)
      {
        servers.add("localhost:" + getChangelogPort(changelog1ID));
      }
      if (changelogId != changelog2ID)
      {
        servers.add("localhost:" + getChangelogPort(changelog2ID));
      }
    }
    int chPort = getChangelogPort(changelogId);
    String chDir = "monitorTest" + changelogId + suffix + "Db";
    ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(chPort, chDir, replicationDbImplementation, 0, changelogId, 0,
            100, servers);
    final DN testBaseDN = this.baseDN;
    ReplicationServer replicationServer = new ReplicationServer(conf, new DSRSShutdownSync(), new ECLEnabledDomainPredicate()
    {
      @Override
      public boolean isECLEnabledDomain(DN baseDN)
      {
        return testBaseDN.equals(baseDN);
      }
    });
    Thread.sleep(1000);

    return replicationServer;
  }

  /**
   * Create a synchronized suffix in the current server providing the
   * replication Server ID.
   * @param changelogID the replication server ID.
   */
  private void connectServer1ToChangelog(int changelogID) throws Exception
  {
    // Connect DS to the replicationServer
    {
      // suffix synchronized
      String synchroServerLdif =
        "dn: cn=" + testName + ", cn=domains," + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + testName + "\n"
        + "ds-cfg-base-dn: " + baseDnStr + "\n"
        + "ds-cfg-replication-server: localhost:"
        + getChangelogPort(changelogID)+"\n"
        + "ds-cfg-server-id: " + server1ID + "\n"
        + "ds-cfg-receive-status: true\n"
        + "ds-cfg-window-size: " + WINDOW_SIZE;

      addSynchroServerEntry(synchroServerLdif);

      replDomain = LDAPReplicationDomain.retrievesReplicationDomain(baseDN);
      if (replDomain != null)
      {
        debugInfo("ReplicationDomain: Import/Export is running ? " +
          replDomain.ieRunning());
      }
    }
  }

  /**
   * Disconnect DS from the replicationServer.
   */
  private void disconnectFromReplServer() throws Exception
  {
      // suffix synchronized
      String synchroServerStringDN = "cn=" + testName + ", cn=domains," +
      SYNCHRO_PLUGIN_DN;
      // Must have called connectServer1ToChangelog previously
      assertTrue(synchroServerEntry != null);
      DN synchroServerDN = DN.valueOf(synchroServerStringDN);
      deleteEntry(synchroServerDN);
      synchroServerEntry = null;
    configEntriesToCleanup.remove(synchroServerDN);
  }

  private int getChangelogPort(int changelogID) throws Exception
  {
    if (replServerPort[changelogID] == 0)
    {
      replServerPort[changelogID] = TestCaseUtils.findFreePort();
    }
    return replServerPort[changelogID];
  }

  private String createEntry(UUID uid)
  {
    String user2dn = "uid=user"+uid+",ou=People," + baseDnStr;
    return "dn: " + user2dn + "\n"
        + "objectClass: top\n" + "objectClass: person\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
        + "homePhone: 951-245-7634\n"
        + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
        + "mobile: 027-085-0537\n"
        + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
        + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
        + "cn: Aaccf Amar2\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
        + "street: 17984 Thirteenth Street\n"
        + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 2\n"
        + "sn: Amar2\n" + "givenName: Aaccf2\n" + "postalCode: 85762\n"
        + "userPassword: password\n" + "initials: AA\n";
  }

  private static ReplicationMsg createAddMsg(CSN csn) throws Exception
  {
    Entry personWithUUIDEntry = null;
    String user1entryUUID;
    String baseUUID = null;
    String user1dn;

    user1entryUUID = "33333333-3333-3333-3333-333333333333";
    user1dn = "uid=user1,ou=People," + baseDnStr;
    String entryWithUUIDldif = "dn: "+ user1dn + "\n"
    + "objectClass: top\n" + "objectClass: person\n"
    + "objectClass: organizationalPerson\n"
    + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
    + "homePhone: 951-245-7634\n"
    + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
    + "mobile: 027-085-0537\n"
    + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
    + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
    + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
    + "street: 17984 Thirteenth Street\n"
    + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
    + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
    + "userPassword: password\n" + "initials: AA\n"
    + "entryUUID: " + user1entryUUID + "\n";

    personWithUUIDEntry = TestCaseUtils.entryFromLdifString(entryWithUUIDldif);

    // Create and publish an update message to add an entry.
    return new AddMsg(csn,
        personWithUUIDEntry.getName(),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
  }

  @Test(enabled=true)
  public void testMultiRS() throws Exception
  {
    String testCase = "testMultiRS";
    debugInfo("Starting " + testCase);

    try
    {
      TestCaseUtils.initializeTestBackend(false);

      debugInfo("Creating 2 RS");
      replServer1 = createReplicationServer(changelog1ID, true, testCase);
      replServer2 = createReplicationServer(changelog2ID, true, testCase);
      replServer3 = createReplicationServer(changelog3ID, true, testCase);
      Thread.sleep(500);

      debugInfo("Connecting DS to replServer1");
      connectServer1ToChangelog(changelog1ID);

      try
      {
        debugInfo("Connecting broker2 to replServer1");
        broker2 = openReplicationSession(baseDN,
          server2ID, 100, getChangelogPort(changelog1ID), 1000);
        Thread.sleep(1000);
      } catch (SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

      try
      {
        debugInfo("Connecting broker3 to replServer2");
        broker3 = openReplicationSession(baseDN,
          server3ID, 100, getChangelogPort(changelog2ID), 1000);
        Thread.sleep(1000);
      } catch (SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

      try
      {
        debugInfo("Connecting broker4 to replServer2");
        broker4 = openReplicationSession(baseDN,
          server4ID, 100, getChangelogPort(changelog2ID), 1000);
        Thread.sleep(1000);
      } catch (SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

      // Do a bunch of change
      addTestEntriesToDB(newLDIFEntries());

      for (int i = 0; i < 200; i++)
      {
        addTestEntriesToDB(createEntry(UUID.randomUUID()));
      }

      /*
       * Create a CSN generator to generate new CSNs
       * when we need to send operation messages to the replicationServer.
       */
      CSNGenerator gen = new CSNGenerator(server3ID, 0);

      for (int i = 0; i < 10; i++)
      {
        broker3.publish(createAddMsg(gen.newCSN()));
      }

      searchMonitor();

      debugInfo("Disconnect DS from replServer1 (required in order to DEL entries).");
      disconnectFromReplServer();


      debugInfo("Successfully ending " + testCase);
    } finally
    {
      debugInfo("Cleaning entries");
      postTest();
    }
  }

  /**
   * Disconnect broker and remove entries from the local DB.
   */
  private void postTest() throws Exception
  {
    debugInfo("Post test cleaning.");

    stop(broker2, broker3, broker4);
    broker2 = broker3 = broker4 = null;
    remove(replServer1, replServer2, replServer3);
    replServer1 = replServer2 = replServer3 = null;

    super.cleanRealEntries();

    Arrays.fill(replServerPort, 0);
    TestCaseUtils.initializeTestBackend(false);
  }

  private static final ByteArrayOutputStream oStream =
    new ByteArrayOutputStream();
  private static final ByteArrayOutputStream eStream =
    new ByteArrayOutputStream();

  private void searchMonitor()
  {
    // test search as directory manager returns content
    String[] args3 =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-Z", "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "cn=monitor",
      "-s", "sub",
      "(domain-name=*)"
    };

    oStream.reset();
    eStream.reset();
    int retVal =
      LDAPSearch.mainSearch(args3, false, oStream, eStream);
    String entries = oStream.toString();
    debugInfo("Entries:" + entries);
    assertEquals(retVal, 0, "Returned error: " + eStream);
    assertTrue(!entries.equalsIgnoreCase(""), "Returned entries: " + entries);
  }
}
