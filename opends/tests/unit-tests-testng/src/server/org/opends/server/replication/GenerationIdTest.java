/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.*;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationBackend;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.tasks.LdifFileWriter;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

/**
 * Tests contained here:
 *
 * - testSingleRS : test generation ID setting with different servers and one
 *   Replication server.
 *
 * - testMultiRS : tests generation ID propagation with more than one
 *   Replication server.
 */
@SuppressWarnings("javadoc")
public class GenerationIdTest extends ReplicationTestCase
{
  /** The tracer object for the debug logger */
  private static final DebugTracer TRACER = getTracer();

  private static final String baseDnStr = TEST_ROOT_DN_STRING;
  private static final String testName = "generationIdTest";

  private static final int   WINDOW_SIZE = 10;
  private static final int server1ID = 1;
  private static final int server2ID = 2;
  private static final int server3ID = 3;
  private static final int changelog1ID = 11;
  private static final int changelog2ID = 12;
  private static final int changelog3ID = 13;

  private DN baseDN;
  private ReplicationBroker broker2 = null;
  private ReplicationBroker broker3 = null;
  private ReplicationServer replServer1 = null;
  private ReplicationServer replServer2 = null;
  private ReplicationServer replServer3 = null;
  private boolean emptyOldChanges = true;
  private Entry taskInitRemoteS2;
  private String[] updatedEntries;

  private static int[] replServerPort = new int[20];

  /**
   * A makeldif template used to create some test entries.
   */
  private static String diff = "";
  private static String[] template = new String[] {
    "define suffix=" + baseDnStr,
    "define maildomain=example.com",
    "define numusers=11",
    "",
    "branch: [suffix]",
    "",
    "branch: ou=People,[suffix]",
    "subordinateTemplate: person:[numusers]",
    "",
    "template: person",
    "rdnAttr: uid",
    "objectClass: top",
    "objectClass: person",
    "objectClass: organizationalPerson",
    "objectClass: inetOrgPerson",
    "givenName: <first>",
    "sn: <last>",
    "cn: {givenName} {sn}",
    "initials: {givenName:1}<random:chars:" +
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}",
    "employeeNumber: <sequential:0>",
    "uid: user.{employeeNumber}",
    "mail: {uid}@[maildomain]",
    "userPassword: password",
    "telephoneNumber: <random:telephone>",
    "homePhone: <random:telephone>",
    "pager: <random:telephone>",
    "mobile: <random:telephone>",
    "street: <random:numeric:5> <file:streets> Street",
    "l: <file:cities>",
    "st: <file:states>",
    "postalCode: <random:numeric:5>",
    "postalAddress: {cn}${street}${l}, {st}  {postalCode}",
    "description: This is the description for {cn} " + diff,
  ""};


  private void debugInfo(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, "** TEST **" + s));
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST **" + s);
    }
  }
  protected void debugInfo(String message, Exception e)
  {
    debugInfo(message + stackTraceToSingleLineString(e));
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

    baseDN = DN.decode(baseDnStr);

    updatedEntries = newLDIFEntries();

    // Synchro suffix
    synchroServerEntry = null;

    taskInitRemoteS2 = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-domain-dn: " + baseDN,
        "ds-task-initialize-replica-server-id: " + server2ID);
  }

  /** Tests that entries have been written in the db */
  private int testEntriesInDb()
  {
    debugInfo("TestEntriesInDb");
    short found = 0;

    for (String entry : updatedEntries)
    {

      int dns = entry.indexOf("dn: ");
      int dne = entry.indexOf(TestCaseUtils.TEST_ROOT_DN_STRING);
      String dn = entry.substring(dns + 4,
        dne + TestCaseUtils.TEST_ROOT_DN_STRING.length());

      debugInfo("Search Entry: " + dn);

      DN entryDN = null;
      try
      {
        entryDN = DN.decode(dn);
      }
      catch(Exception e)
      {
        debugInfo("TestEntriesInDb/" + e);
      }

      try
      {
        Entry resultEntry = getEntry(entryDN, 1000, true);
        if (resultEntry==null)
        {
          debugInfo("Entry not found <" + dn + ">");
        }
        else
        {
          debugInfo("Entry found <" + dn + ">");
          found++;
        }
      }
      catch(Exception e)
      {
        debugInfo("TestEntriesInDb/", e);
      }
    }
    return found;
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

  private int receiveImport(ReplicationBroker broker, int serverID,
      String[] updatedEntries)
  {
    // Expect the broker to receive the entries
    ReplicationMsg msg;
    short entriesReceived = -100;
    while (true)
    {
      try
      {
        debugInfo("Broker " + serverID + " Wait for entry or done msg");
        msg = broker.receive();

        if (msg == null)
        {
          break;
        }

        if (msg instanceof InitializeTargetMsg)
        {
          debugInfo("Broker " + serverID + " receives InitializeTargetMessage ");
          entriesReceived = 0;
        }
        else if (msg instanceof EntryMsg)
        {
          EntryMsg em = (EntryMsg)msg;
          debugInfo("Broker " + serverID + " receives entry " + new String(em.getEntryBytes()));
          entriesReceived++;
        }
        else if (msg instanceof DoneMsg)
        {
          debugInfo("Broker " + serverID + " receives done ");
          break;
        }
        else if (msg instanceof ErrorMsg)
        {
          ErrorMsg em = (ErrorMsg)msg;
          debugInfo("Broker " + serverID + " receives ERROR " + em);
          break;
        }
        else
        {
          debugInfo("Broker " + serverID + " receives and trashes " + msg);
        }
      }
      catch(Exception e)
      {
        debugInfo("receiveUpdatedEntries" + stackTraceToSingleLineString(e));
      }
    }

    if (updatedEntries != null)
    {
      assertTrue(entriesReceived == updatedEntries.length,
          " Received entries("+entriesReceived +
          ") == Expected entries("+updatedEntries.length+")");
    }

    return entriesReceived;
  }

  /**
   * Creates a new replicationServer.
   * @param changelogId The serverID of the replicationServer to create.
   * @param all         Specifies whether to connect the created replication
   *                    server to the other replication servers in the test.
   * @return The new created replication server.
   */
  private ReplicationServer createReplicationServer(int changelogId,
      boolean all, String testCase) throws Exception
  {
    SortedSet<String> servers = new TreeSet<String>();
    if (all)
    {
      if (changelogId != changelog1ID)
        servers.add("localhost:" + getChangelogPort(changelog1ID));
      if (changelogId != changelog2ID)
        servers.add("localhost:" + getChangelogPort(changelog2ID));
      if (changelogId != changelog3ID)
        servers.add("localhost:" + getChangelogPort(changelog3ID));
    }
    int chPort = getChangelogPort(changelogId);
    String chDir = "generationIdTest" + changelogId + testCase + "Db";
    ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(chPort, chDir, 0, changelogId, 0, 100, servers);
    ReplicationServer replicationServer = new ReplicationServer(conf);
    Thread.sleep(1000);

    return replicationServer;
  }

  /**
   * Create a synchronized suffix in the current server providing the
   * replication Server ID.
   * @param changeLogID replication Server ID
   */
  private void connectServer1ToChangelog(int changeLogID) throws Exception
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
        + getChangelogPort(changeLogID)+"\n"
        + "ds-cfg-server-id: " + server1ID + "\n"
        + "ds-cfg-receive-status: true\n"
        + "ds-cfg-window-size: " + WINDOW_SIZE;

      // Must be no connection already done or disconnectFromReplServer should
      // have been called
      assertTrue(synchroServerEntry == null);

      synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);
      DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the synchronized server");
      configEntryList.add(synchroServerEntry.getDN());

      int waitCo=0;
      LDAPReplicationDomain doToco=null;
      while(waitCo<50)
      {
        doToco = LDAPReplicationDomain.retrievesReplicationDomain(baseDN);
        if (doToco != null && doToco.isConnected())
        {
          break;
        }
        Thread.sleep(waitCo * 200);
        waitCo++;
      }
      assertNotNull(doToco);
      assertTrue(doToco.isConnected(), "not connected after #attempt="+waitCo);
      debugInfo("ReplicationDomain: Import/Export is running ? " + doToco.ieRunning());
    }
  }

  /**
   * Disconnect DS from the replicationServer
   */
  private void disconnectFromReplServer(int changelogID) throws Exception
  {
    {
      // suffix synchronized
      String synchroServerStringDN = "cn=" + testName + ", cn=domains," +
      SYNCHRO_PLUGIN_DN;
      // Must have called connectServer1ToChangelog previously
      assertTrue(synchroServerEntry != null);

      DN synchroServerDN = DN.decode(synchroServerStringDN);

      Entry ecle = DirectoryServer.getConfigHandler().getEntry(
          DN.decode("cn=external changelog," + synchroServerStringDN));
      if (ecle!=null)
      {
        DirectoryServer.getConfigHandler().deleteEntry(ecle.getDN(), null);
      }
      DirectoryServer.getConfigHandler().deleteEntry(synchroServerDN, null);
      assertTrue(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()) ==
        null,
        "Unable to delete the synchronized domain");
      synchroServerEntry = null;

      configEntryList.remove(configEntryList.indexOf(synchroServerDN));

      LDAPReplicationDomain replDomainToDis = null;
      try
      {
        int waitCo=0;
        while(waitCo<30)
        {
          replDomainToDis = LDAPReplicationDomain.retrievesReplicationDomain(baseDN);
          Thread.sleep(200);
          waitCo++;
        }
        assert(replDomainToDis==null);
      }
      catch (DirectoryException e)
      {
        // success
        debugInfo("disconnectFromReplServer:" + changelogID, e);
      }
    }
  }

  private int getChangelogPort(int changelogID) throws Exception
  {
    if (replServerPort[changelogID] == 0)
    {
      replServerPort[changelogID] = TestCaseUtils.findFreePort();
    }
    return replServerPort[changelogID];
  }

  protected static final String REPLICATION_GENERATION_ID =
    "ds-sync-generation-id";

  private long readGenIdFromSuffixRootEntry() throws Exception
  {
    long genId=-1;
    {
      Entry resultEntry = getEntry(baseDN, 1000, true);
      if (resultEntry==null)
      {
        debugInfo("Entry not found <" + baseDN + ">");
      }
      else
      {
        debugInfo("Entry found <" + baseDN + ">");

        AttributeType synchronizationGenIDType =
          DirectoryServer.getAttributeType(REPLICATION_GENERATION_ID);
        List<Attribute> attrs =
          resultEntry.getAttribute(synchronizationGenIDType);
        if (attrs != null)
        {
          Attribute attr = attrs.get(0);
          if (attr.size() == 1)
          {
            genId =
                Long.decode(attr.iterator().next().getValue().toString());
          }
        }

      }
    }
    return genId;
  }

  private void performLdifImport() throws Exception
  {
    // Create a temporary test LDIF file.
    // A temporary LDIF file containing some test entries.
    File ldifFile = File.createTempFile("import-test", ".ldif");
    String resourcePath =
        DirectoryServer.getInstanceRoot() + File.separator + "config"
            + File.separator + "MakeLDIF";
    LdifFileWriter.makeLdif(ldifFile.getPath(), resourcePath, template);

    // Launch import of the Ldif file on the memory test backend
    // Note: we do not use a task here as import task does not work on memory
    // backend: it disables then re-enables backend which leads to backend
    // object instance lost and this is not acceptable for a backend with
    // non persistent data
    LDIFImportConfig importConfig = new LDIFImportConfig(ldifFile.getAbsolutePath());

    MemoryBackend memoryBackend = (MemoryBackend) DirectoryServer.getBackend(TEST_BACKEND_ID);
    memoryBackend.importLDIF(importConfig);
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

  static protected ReplicationMsg createAddMsg() throws Exception
  {
    Entry personWithUUIDEntry = null;
    String user1entryUUID;
    String baseUUID = null;
    String user1dn;

    /*
     * Create a CSN generator to generate new CSNs when we need to send
     * operation messages to the replicationServer.
     */
    CSNGenerator gen = new CSNGenerator(2, 0);

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
    return new AddMsg(gen.newCSN(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
  }

  /**
   * Check that the expected number of changes are in the replication server
   * database.
   */
  private void checkChangelogSize(int expectedCount)
  {
    try
    {
      SearchFilter filter =
        SearchFilter.createFilterFromString("(objectclass=*)");
      InternalSearchOperation searchOperation =
        connection.processSearch(DN.decode("dc=replicationchanges"),
            SearchScope.SUBORDINATE_SUBTREE,
            filter);
      if (debugEnabled())
      {
        if (searchOperation.getSearchEntries().size() != expectedCount)
        {
          for (SearchResultEntry sre : searchOperation.getSearchEntries())
          {
            debugInfo("Entry found: " + sre.toLDIFString());
          }
        }
      }
      assertEquals(searchOperation.getSearchEntries().size(), expectedCount);
    }
    catch(Exception e)
    {
    }
  }

  /**
   * SingleRS tests basic features of generationID
   * with one single Replication Server.
   *
   * @throws Exception
   */
  @Test(enabled=false)
  public void testSingleRS() throws Exception
  {
    String testCase = "testSingleRS";
    debugInfo("Starting "+ testCase + " debugEnabled:" + debugEnabled());

    debugInfo(testCase + " Clearing DS1 backend");
    // Special test were we want to test with an empty backend. So free it
    // for this special test.
    TestCaseUtils.initializeTestBackend(false);

    try
    {
      long rgenId;
      long genId;

      replServer1 = createReplicationServer(changelog1ID, false, testCase);

      // To search the replication server db later in these tests, we need
      // to attach the search backend to the replication server just created.
      ReplicationBackend b =
        (ReplicationBackend)DirectoryServer.getBackend("replicationChanges");
      b.setServer(replServer1);

      //===========================================================
      debugInfo(testCase + " ** TEST ** Empty backend");

      debugInfo(testCase + " Configuring DS1 to replicate to RS1(" + changelog1ID + ") on an empty backend");
      connectServer1ToChangelog(changelog1ID);

      debugInfo(testCase + " Expect genId to be not retrievable from suffix root entry");
      genId = readGenIdFromSuffixRootEntry();
      assertEquals(genId,-1);

      debugInfo(testCase + " Expect genId to be set in memory on the replication " +
      " server side (not wrote on disk/db since no change occurred).");
      rgenId = replServer1.getGenerationId(baseDN);
      assertEquals(rgenId, EMPTY_DN_GENID);

      // Clean for next test
      debugInfo(testCase + " Unconfiguring DS1 to replicate to RS1(" + changelog1ID + ")");
      disconnectFromReplServer(changelog1ID);


      //===========================================================
      debugInfo(testCase + " ** TEST ** Non empty backend");

      debugInfo(testCase + " Adding test entries to DS");
      addTestEntriesToDB(updatedEntries);

      debugInfo(testCase + " Configuring DS1 to replicate to RS1(" + changelog1ID + ") on a non empty backend");
      connectServer1ToChangelog(changelog1ID);

      debugInfo(testCase + " Test that the generationId is written in the DB in the root entry on DS1");
      genId = readGenIdFromSuffixRootEntry();
      assertTrue(genId != -1);
      assertTrue(genId != EMPTY_DN_GENID);

      debugInfo(testCase + " Test that the generationId is set on RS1");
      rgenId = replServer1.getGenerationId(baseDN);
      assertEquals(genId, rgenId);

      //===========================================================
      debugInfo(testCase + " ** TEST ** DS2 connection to RS1 with bad genID");

      broker2 = openReplicationSession(baseDN, server2ID, 100,
          getChangelogPort(changelog1ID), 1000, !emptyOldChanges, genId+1);

      //===========================================================
      debugInfo(testCase + " ** TEST ** DS3 connection to RS1 with good genID");
      broker3 = openReplicationSession(baseDN, server3ID, 100,
          getChangelogPort(changelog1ID), 1000, !emptyOldChanges, genId);

      //===========================================================
      debugInfo(testCase + " ** TEST ** DS2 (bad genID) changes must be ignored.");

      broker2.publish(createAddMsg());
      try
      {
        broker3.receive();
        fail("No update message is supposed to be received here.");
      }
      catch(SocketTimeoutException e)
      {
        // This is the expected result
        // Note that timeout should be lower than RS montoring publisher period
        // so that timeout occurs
      }

      //===========================================================
      debugInfo(testCase + " ** TEST ** The part of the topology with the right gen ID should work well");

      // Now create a change that must be replicated
      String ent1[] = { createEntry(UUID.randomUUID()) };
      addTestEntriesToDB(ent1);

      // Verify that RS1 does contain the change related to this ADD.
      Thread.sleep(500);
      checkChangelogSize(1);

      // Verify that DS3 receives this change
      ReplicationMsg msg = broker3.receive();
      debugInfo("Broker 3 received expected update msg" + msg);

      //===========================================================
      debugInfo(testCase + " ** TEST ** Persistence of the generation ID in RS1");

      long genIdBeforeShut = replServer1.getGenerationId(baseDN);

      debugInfo("Shutdown replServer1");
      stop(broker2, broker3);
      broker2 = broker3 = null;
      remove(replServer1);
      replServer1 = null;

      debugInfo("Create again replServer1");
      replServer1 = createReplicationServer(changelog1ID, false, testCase);

      // To search the replication server db later in these tests, we need
      // to attach the search backend to the replication server just created.
      b = (ReplicationBackend)DirectoryServer.getBackend("replicationChanges");
      b.setServer(replServer1);

      debugInfo("Delay to allow DS to reconnect to replServer1");

      long genIdAfterRestart = replServer1.getGenerationId(baseDN);
      debugInfo("Aft restart / replServer.genId=" + genIdAfterRestart);
      assertTrue(replServer1!=null, "Replication server creation failed.");
      assertTrue(genIdBeforeShut == genIdAfterRestart,
        "generationId is expected to have the same value" +
        " after replServer1 restart. Before : " + genIdBeforeShut +
        " after : " + genIdAfterRestart);

      // By the way also verify that no change occurred on the replication server db
      // and still contain the ADD submitted initially.
      Thread.sleep(500);
      checkChangelogSize(1);

      //===============================================================
      debugInfo(testCase + " ** TEST ** Import with new data set + reset will"+
          " spread a new gen ID on the topology, verify DS1 and RS1");
      debugInfo("Create again broker2");
      broker2 = openReplicationSession(baseDN,
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges, genId);
      assertTrue(broker2.isConnected(), "Broker2 failed to connect to replication server");

      debugInfo("Create again broker3");
      broker3 = openReplicationSession(baseDN,
          server3ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges, genId);
      assertTrue(broker3.isConnected(), "Broker3 failed to connect to replication server");


      debugInfo("Launch on-line import on DS1");
      long oldGenId = genId;
      genId=-1;
      disconnectFromReplServer(changelog1ID);
      performLdifImport();
      connectServer1ToChangelog(changelog1ID);

      debugInfo("Create Reset task on DS1 to propagate the new gen ID as the reference");
      Entry taskReset = TestCaseUtils.makeEntry(
          "dn: ds-task-id=resetgenid"+genId+ UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-reset-generation-id",
          "ds-task-class-name: org.opends.server.tasks.SetGenerationIdTask",
          "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr);
      addTask(taskReset, ResultCode.SUCCESS, null);
      waitTaskState(taskReset, TaskState.COMPLETED_SUCCESSFULLY, null);

      // Broker 2 and 3 should receive 1 change status message to order them
      // to enter the bad gen id status
      ChangeStatusMsg csMsg = (ChangeStatusMsg)waitForSpecificMsg(broker2,
        ChangeStatusMsg.class.getName());
      if (csMsg.getRequestedStatus() != ServerStatus.BAD_GEN_ID_STATUS)
      {
        fail("Broker 2 connection is expected to receive 1 ChangeStatusMsg" +
          " to enter the bad gen id status"
            + csMsg);
      }
      csMsg = (ChangeStatusMsg)waitForSpecificMsg(broker3,
        ChangeStatusMsg.class.getName());
      if (csMsg.getRequestedStatus() != ServerStatus.BAD_GEN_ID_STATUS)
      {
        fail("Broker 2 connection is expected to receive 1 ChangeStatusMsg" +
          " to enter the bad gen id status"
            + csMsg);
      }

      debugInfo("DS1 root entry must contain the new gen ID");
      genId = readGenIdFromSuffixRootEntry();
      assertTrue(genId != -1, "DS is expected to have a new genID computed " +
          " after on-line import but genId=" + genId);
      assertTrue(genId != oldGenId, "The new genID after import and reset of genID "
        + "is expected to be diffrent from previous one");

      debugInfo("RS1 must have the new gen ID");
      rgenId = replServer1.getGenerationId(baseDN);
      assertEquals(genId, rgenId, "DS and replServer are expected to have same genId.");

      debugInfo("RS1 must have been cleared since it has not the proper generation ID");
      checkChangelogSize(0);

      assertFalse(isDegradedDueToGenerationId(replServer1, server1ID),
          "Expecting that DS1 status in RS1 is : not in bad gen id.");

      //===============================================================
      debugInfo(testCase + " ** TEST ** Previous test set a new gen ID on the "+
          "topology, verify degradation of DS2 and DS3");

      assertTrue(isDegradedDueToGenerationId(replServer1, server2ID),
          "Expecting that DS2 with old gen ID is in bad gen id from RS1");
      assertTrue(isDegradedDueToGenerationId(replServer1, server3ID),
          "Expecting that DS3 with old gen ID is in bad gen id from RS1");

      debugInfo("Add entries to DS1, update should not be sent to DS2 and DS3 that are in bad gen id");
      String[] ent3 = { createEntry(UUID.randomUUID()) };
      addTestEntriesToDB(ent3);

      debugInfo("RS1 must have stored that update.");
      Thread.sleep(500);
      checkChangelogSize(1);

      try
      {
        ReplicationMsg msg2 = broker2.receive();
        fail("No update message is supposed to be received by broker2 in bad gen id. " + msg2);
      } catch(SocketTimeoutException e) { /* expected */ }

      try
      {
        ReplicationMsg msg2 = broker3.receive();
        fail("No update message is supposed to be received by broker3 in bad gen id. " + msg2);
      } catch(SocketTimeoutException e) { /* expected */ }


      debugInfo("DS2 is publishing a change and RS1 must ignore this change, DS3 must not receive it.");
      AddMsg emsg = (AddMsg)createAddMsg();
      broker2.publish(emsg);

      // Updates count in RS1 must stay unchanged = to 1
      Thread.sleep(500);
      checkChangelogSize(1);

      try
      {
        ReplicationMsg msg2 = broker3.receive();
        fail("No update message is supposed to be received by broker3 in bad gen id. "+ msg2);
      } catch(SocketTimeoutException e) { /* expected */ }


      //===============================================================
      debugInfo(testCase + " ** TEST ** Previous test put DS2 and DS3 in bad gen id, "+
          " now simulates \"dsreplication initialize \"by doing a TU+reset " +
          " from DS1 to DS2 and DS3, verify NON degradation of DS2 and DS3");

      // In S1 launch the total update to initialize S2
      addTask(taskInitRemoteS2, ResultCode.SUCCESS, null);

      // S2 should be re-initialized and have a new valid genId

      // Signal that we just entered the full update status
      broker2.signalStatusChange(ServerStatus.FULL_UPDATE_STATUS);

      int receivedEntriesNb = receiveImport(broker2, server2ID, null);
      debugInfo("broker2 has been initialized from DS with #entries=" + receivedEntriesNb);

      broker2.stop();

      // Simulates the broker restart at the end of the import
      broker2 = openReplicationSession(baseDN,
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges, genId);

      broker3.stop();

      // Simulates the broker restart at the end of the import
      broker3 = openReplicationSession(baseDN,
          server3ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges, genId);

      debugInfo("Adding reset task to DS1");
      taskReset = TestCaseUtils.makeEntry(
          "dn: ds-task-id=resetgenid"+ UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-reset-generation-id",
          "ds-task-class-name: org.opends.server.tasks.SetGenerationIdTask",
          "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr);

      addTask(taskReset, ResultCode.SUCCESS, null);
      waitTaskState(taskReset, TaskState.COMPLETED_SUCCESSFULLY, null);

      debugInfo("Verify that RS1 has still the right genID");
      assertEquals(replServer1.getGenerationId(baseDN), rgenId);

      // Updates count in RS1 must stay unchanged = to 1
      Thread.sleep(500);
      checkChangelogSize(1);

      debugInfo("Verifying that DS2 is not in bad gen id any more");
      assertFalse(isDegradedDueToGenerationId(replServer1, server2ID),
          "Expecting that DS2 is not in bad gen id from RS1");

      debugInfo("Verifying that DS3 is not in bad gen id any more");
      assertFalse(isDegradedDueToGenerationId(replServer1, server3ID),
          "Expecting that DS3 is not in bad gen id from RS1");

      debugInfo("Verify that DS2 receives the add message stored in RS1 DB");
      msg = broker2.receive();
      assertTrue(msg instanceof AddMsg, "Expected to receive an AddMsg but received: " + msg);

      debugInfo("Verify that DS3 receives the add message stored in RS1 DB");
      msg = broker3.receive();
      assertTrue(msg instanceof AddMsg, "Expected to receive an AddMsg but received: " + msg);

      debugInfo("DS2 is publishing a change and RS1 must store this change, DS3 must receive it.");
      emsg = (AddMsg)createAddMsg();
      broker2.publish(emsg);

      Thread.sleep(500);
      checkChangelogSize(2);

      /* expected */
      msg = broker3.receive();
      AddMsg rcvmsg = (AddMsg)msg;
      assertEquals(rcvmsg.getCSN(), emsg.getCSN());

      //===============================================================
      debugInfo(testCase + " ** TEST ** General cleaning");

      debugInfo("Disconnect DS from replServer1 (required in order to DEL entries).");
      disconnectFromReplServer(changelog1ID);

      debugInfo("Successfully ending " + testCase);
    } finally
    {
      postTest();
    }
  }

  /**
  /**
   * testMultiRS tests basic features of generationID
   * with more than one Replication Server.
   * The following test focus on:
   * - genId checking across multiple starting RS (replication servers)
   * - genId setting propagation from one RS to the others
   * - genId reset propagation from one RS to the others
   */
  @Test(enabled=false)
  public void testMultiRS(int i) throws Exception
  {
    String testCase = "testMultiRS";
    long genId;

    debugInfo("Starting " + testCase);

    try
    {
      // Special test were we want to test with an empty backend. So free it
      // for this special test.
      TestCaseUtils.initializeTestBackend(false);

      debugInfo("Creating 3 RS");
      replServer1 = createReplicationServer(changelog1ID, true, testCase);
      replServer2 = createReplicationServer(changelog2ID, true, testCase);
      replServer3 = createReplicationServer(changelog3ID, true, testCase);

      debugInfo("Connecting DS to replServer1");
      connectServer1ToChangelog(changelog1ID);

      debugInfo("Expect genId are set in all replServers.");
      int waitRes=0;
      while(waitRes<100)
      {
        if (replServer1.getGenerationId(baseDN) == EMPTY_DN_GENID
            && replServer2.getGenerationId(baseDN) == EMPTY_DN_GENID
            && replServer3.getGenerationId(baseDN) == EMPTY_DN_GENID)
          break;
        waitRes++;
        Thread.sleep(100);
      }
      assertEquals(replServer1.getGenerationId(baseDN), EMPTY_DN_GENID, " in replServer1");
      assertEquals(replServer2.getGenerationId(baseDN), EMPTY_DN_GENID, " in replServer2");
      assertEquals(replServer3.getGenerationId(baseDN), EMPTY_DN_GENID, " in replServer3");

      debugInfo("Disconnect DS from replServer1.");
      disconnectFromReplServer(changelog1ID);

      waitRes=0;
      while(waitRes<100)
      {
        if (replServer1.getGenerationId(baseDN) == -1
            && replServer2.getGenerationId(baseDN) == -1
            && replServer3.getGenerationId(baseDN) == -1)
          break;
        waitRes++;
        Thread.sleep(100);
      }
      debugInfo(
        "Expect genIds to be resetted in all servers to -1 as no more DS in topo - after 10 sec");
      assertEquals(replServer1.getGenerationId(baseDN), -1);
      assertEquals(replServer2.getGenerationId(baseDN), -1);
      assertEquals(replServer3.getGenerationId(baseDN), -1);

      debugInfo("Add entries to DS");
      addTestEntriesToDB(updatedEntries);

      debugInfo("Connecting DS to replServer2");
      connectServer1ToChangelog(changelog2ID);

      debugInfo(
        "Expect genIds to be set in all servers based on the added entries.");
      genId = readGenIdFromSuffixRootEntry();
      assertTrue(genId != -1);
      waitRes=0;
      while(waitRes<100)
      {
        if (replServer1.getGenerationId(baseDN) == genId
            && replServer2.getGenerationId(baseDN) == genId
            && replServer3.getGenerationId(baseDN) == genId)
          break;
        waitRes++;
        Thread.sleep(100);
      }
      assertEquals(replServer1.getGenerationId(baseDN), genId);
      assertEquals(replServer2.getGenerationId(baseDN), genId);
      assertEquals(replServer3.getGenerationId(baseDN), genId);

      debugInfo("Connecting broker2 to replServer3 with a good genId");
      broker2 = openReplicationSession(baseDN, server2ID, 100,
          getChangelogPort(changelog3ID), 1000, !emptyOldChanges, genId);
      Thread.sleep(1000);

      debugInfo("Expecting that broker2 is not in bad gen id since it has a correct genId");
      assertFalse(isDegradedDueToGenerationId(replServer1, server2ID));

      debugInfo("Disconnecting DS from replServer1");
      disconnectFromReplServer(changelog1ID);

      debugInfo("Verifying that all replservers genIds have been reset.");

      debugInfo(
      "Expect all genIds to keep their value since broker2 is still connected.");
      waitRes=0;
      while(waitRes<100)
      {
        if (replServer1.getGenerationId(baseDN) == genId
            && replServer2.getGenerationId(baseDN) == genId
            && replServer3.getGenerationId(baseDN) == genId)
          break;
        waitRes++;
        Thread.sleep(100);
      }
      assertEquals(replServer1.getGenerationId(baseDN), genId);
      assertEquals(replServer2.getGenerationId(baseDN), genId);
      assertEquals(replServer3.getGenerationId(baseDN), genId);

      debugInfo("Connecting broker3 to replServer1 with a bad genId");
      long badgenId = 1;
      broker3 = openReplicationSession(baseDN, server3ID, 100,
          getChangelogPort(changelog1ID), 1000, !emptyOldChanges, badgenId);
      Thread.sleep(1000);

      debugInfo("Expecting that broker3 is in bad gen id since it has a bad genId");
      assertTrue(isDegradedDueToGenerationId(replServer1, server3ID));

      int found = testEntriesInDb();
      assertEquals(found, updatedEntries.length,
        " Entries present in DB :" + found +
        " Expected entries :" + updatedEntries.length);

      debugInfo("Connecting DS to replServer1.");
      connectServer1ToChangelog(changelog1ID);


      debugInfo("Adding reset task to DS.");
      Entry taskReset = TestCaseUtils.makeEntry(
        "dn: ds-task-id=resetgenid" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-reset-generation-id",
        "ds-task-class-name: org.opends.server.tasks.SetGenerationIdTask",
        "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr);
      addTask(taskReset, ResultCode.SUCCESS, null);
      waitTaskState(taskReset, TaskState.COMPLETED_SUCCESSFULLY, null);
      Thread.sleep(500);

      debugInfo("Verifying that all replservers genIds have been reset.");
      genId = readGenIdFromSuffixRootEntry();
      assertEquals(replServer1.getGenerationId(baseDN), genId);
      assertEquals(replServer2.getGenerationId(baseDN), genId);
      assertEquals(replServer3.getGenerationId(baseDN), genId);

      debugInfo("Adding reset task to DS." + genId);
      taskReset = TestCaseUtils.makeEntry(
        "dn: ds-task-id=resetgenid" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-reset-generation-id",
        "ds-task-class-name: org.opends.server.tasks.SetGenerationIdTask",
        "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr,
        "ds-task-reset-generation-id-new-value: -1");
      addTask(taskReset, ResultCode.SUCCESS, null);
      waitTaskState(taskReset, TaskState.COMPLETED_SUCCESSFULLY, null);

      debugInfo("Verifying that all replservers genIds have been reset.");
      waitRes=0;
      while(waitRes<100)
      {
        readGenIdFromSuffixRootEntry();
        if (replServer1.getGenerationId(baseDN) == -1
            && replServer2.getGenerationId(baseDN) == -1
            && replServer3.getGenerationId(baseDN) == -1)
          break;
        waitRes++;
        Thread.sleep(100);
      }
      assertEquals(replServer1.getGenerationId(baseDN), -1, "test" + i);
      assertEquals(replServer2.getGenerationId(baseDN), -1, "test" + i);
      assertEquals(replServer3.getGenerationId(baseDN), -1, "test" + i);

      debugInfo(
        "Disconnect DS from replServer1 (required in order to DEL entries).");
      disconnectFromReplServer(changelog1ID);

      debugInfo("Successfully ending " + testCase);
    } finally
    {
      postTest();
    }
  }

  private boolean isDegradedDueToGenerationId(ReplicationServer rs, int serverId)
  {
    ReplicationServerDomain domain = rs.getReplicationServerDomain(baseDN);
    return domain.isDegradedDueToGenerationId(serverId);
  }

  /**
   * Disconnect broker and remove entries from the local DB
   */
  protected void postTest()
  {
    debugInfo("Post test cleaning.");

    stop(broker2, broker3);
    broker2 = broker3 = null;
    remove(replServer1, replServer2, replServer3);
    replServer1 = replServer2 = replServer3 = null;

    super.cleanRealEntries();

    Arrays.fill(replServerPort, 0);

    debugInfo("Clearing DS backend");
    try
    {
      TestCaseUtils.initializeTestBackend(false);
    } catch (Exception ex)
    {debugInfo("postTest(): error cleaning memory backend: " + ex);}
  }

  /**
   * Test generationID saving when the root entry does not exist
   * at the moment when the replication is enabled.
   * @throws Exception
   */
  @Test(enabled=false, groups="slow")
  public void testServerStop() throws Exception
  {
    String testCase = "testServerStop";
    debugInfo("Starting "+ testCase + " debugEnabled:" + debugEnabled());

    debugInfo(testCase + " Clearing DS1 backend");
    // Special test were we want to test with an empty backend. So free it
    // for this special test.
    TestCaseUtils.initializeTestBackend(false);

    try
    {
      long genId;

      replServer1 = createReplicationServer(changelog1ID, false, testCase);

      /*
       * Test  : empty replicated backend
       * Check : nothing is broken - no generationId generated
       */

      // Connect DS to RS with no data
      // Read generationId - should be not retrievable since no entry
      debugInfo(testCase + " Connecting DS1 to replServer1(" + changelog1ID + ")");
      connectServer1ToChangelog(changelog1ID);

      debugInfo(testCase + " Expect genId attribute to be not retrievable");
      genId = readGenIdFromSuffixRootEntry();
      assertEquals(genId,-1);

      addTestEntriesToDB(updatedEntries);

      debugInfo(testCase + " Expect genId attribute to be retrievable");
      genId = readGenIdFromSuffixRootEntry();
      assertEquals(genId, EMPTY_DN_GENID);

      disconnectFromReplServer(changelog1ID);
    }
    finally
    {
      postTest();
      debugInfo("Successfully ending " + testCase);
    }
  }

  /**
   * Loop opening sessions to the Replication Server
   * to check that it handle correctly disconnection and reconnection.
   */
  @Test(enabled=false, groups="slow")
  public void testLoop() throws Exception
  {
    String testCase = "testLoop";
    debugInfo("Starting "+ testCase + " debugEnabled:" + debugEnabled());
    long rgenId;

    // Special test were we want to test with an empty backend. So free it
    // for this special test.
    TestCaseUtils.initializeTestBackend(false);

    replServer1 = createReplicationServer(changelog1ID, false, testCase);
    replServer1.clearDb();

    ReplicationBroker broker = null;
    try
    {
      for (int i=0; i< 5; i++)
      {
        long generationId = 1000+i;
        broker = openReplicationSession(baseDN, server2ID, 100,
            getChangelogPort(changelog1ID), 1000, !emptyOldChanges, generationId);
        debugInfo(testCase + " Expect genId to be set in memory on the replication " +
          " server side even if not wrote on disk/db since no change occurred.");
        rgenId = replServer1.getGenerationId(baseDN);
        assertEquals(rgenId, generationId);
        broker.stop();
        broker = null;
        Thread.sleep(2000); // Let time to RS to clear info about previous connection
      }
    } finally
    {
      stop(broker);
      postTest();
    }
  }

  /**
   * This is used to make sure that the 3 tests are run in the
   * specified order since this is necessary.
   */
  @Test(enabled=true, groups="slow")
  public void generationIdTest() throws Exception
  {
    testSingleRS();
    testMultiRS(0);
    testServerStop();
    testLoop();
  }
}
