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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ChangeStatusMsg;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.SocketSession;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationBackend;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.tasks.LdifFileWriter;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.opends.server.TestCaseUtils.*;

/**
 * Tests contained here:
 *
 * - testSingleRS : test generation ID setting with different servers and one
 *   Replication server.
 *
 * - testMultiRS : tests generation ID propagation with more than one
 *   Replication server.
 *
 */

public class GenerationIdTest extends ReplicationTestCase
{
  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  private static final String baseDnStr = TEST_ROOT_DN_STRING;
  private static final String testName = "generationIdTest";

  private static final int   WINDOW_SIZE = 10;
  private static final short server1ID = 1;
  private static final short server2ID = 2;
  private static final short server3ID = 3;
  private static final short changelog1ID = 11;
  private static final short changelog2ID = 12;
  private static final short changelog3ID = 13;

  private DN baseDn;
  private ReplicationBroker broker2 = null;
  private ReplicationBroker broker3 = null;
  private ReplicationServer replServer1 = null;
  private ReplicationServer replServer2 = null;
  private ReplicationServer replServer3 = null;
  private boolean emptyOldChanges = true;
  LDAPReplicationDomain replDomain = null;
  private Entry taskInitRemoteS2;
  SocketSession ssSession = null;
  boolean ssShutdownRequested = false;
  private String[] updatedEntries;

  private static int[] replServerPort = new int[20];

  /**
   * A temporary LDIF file containing some test entries.
   */
  private File ldifFile;

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
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
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

    baseDn = DN.decode(baseDnStr);

    updatedEntries = newLDIFEntries();

    // Synchro suffix
    synchroServerEntry = null;

    taskInitRemoteS2 = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.replication.plugin.InitializeTargetTask",
        "ds-task-initialize-domain-dn: " + baseDn,
        "ds-task-initialize-replica-server-id: " + server2ID);
  }

  // Tests that entries have been written in the db
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

  /*
   * Creates entries necessary to the test.
   */
  private String[] newLDIFEntries()
  {
    String[] entries =
    {
        "dn: " + baseDn + "\n"
        + "objectClass: top\n"
        + "objectClass: organization\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111111\n"
        + "\n",
        "dn: ou=People," + baseDn + "\n"
        + "objectClass: top\n"
        + "objectClass: organizationalUnit\n"
        + "entryUUID: 21111111-1111-1111-1111-111111111112\n"
        + "\n",
        "dn: cn=Fiona Jensen,ou=people," + baseDn + "\n"
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
        "dn: cn=Robert Langman,ou=people," + baseDn + "\n"
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

    return entries;
  }

  private int receiveImport(ReplicationBroker broker, short serverID,
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
          break;

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
          debugInfo("Broker " + serverID + " receives ERROR "
              + em.toString());
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
  private ReplicationServer createReplicationServer(short changelogId,
      boolean all, String testCase)
  {
    SortedSet<String> servers = null;
    servers = new TreeSet<String>();
    try
    {
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
      String chDir = "generationIdTest"+changelogId+testCase+"Db";
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(chPort, chDir, 0, changelogId, 0, 100,
            servers);
      ReplicationServer replicationServer = new ReplicationServer(conf);
      Thread.sleep(1000);

      return replicationServer;

    }
    catch (Exception e)
    {
      fail("createChangelog" + stackTraceToSingleLineString(e));
    }
    return null;
  }

  /**
   * Create a synchronized suffix in the current server providing the
   * replication Server ID.
   * @param changelogID
   */
  private void connectServer1ToChangelog(short changelogID)
  {
    // Connect DS to the replicationServer
    try
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

      // Must be no connection already done or disconnectFromReplServer should
      // have been called
      assertTrue(synchroServerEntry == null);

      synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);
      DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the synchronized server");
      configEntryList.add(synchroServerEntry.getDN());

      replDomain = LDAPReplicationDomain.retrievesReplicationDomain(baseDn);


      if (replDomain != null)
      {
        debugInfo("ReplicationDomain: Import/Export is running ? " + replDomain.ieRunning());
      }
    }
    catch(Exception e)
    {
      debugInfo("connectToReplServer", e);
      fail("connectToReplServer", e);
    }
  }

  /*
   * Disconnect DS from the replicationServer
   */
  private void disconnectFromReplServer(short changelogID)
  {
    try
    {
      // suffix synchronized
      String synchroServerStringDN = "cn=" + testName + ", cn=domains," +
      SYNCHRO_PLUGIN_DN;
      // Must have called connectServer1ToChangelog previously
      assertTrue(synchroServerEntry != null);

      DN synchroServerDN = DN.decode(synchroServerStringDN);
      DirectoryServer.getConfigHandler().deleteEntry(synchroServerDN, null);
      assertTrue(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()) ==
        null,
        "Unable to delete the synchronized domain");
      synchroServerEntry = null;

      configEntryList.remove(configEntryList.indexOf(synchroServerDN));

    }
    catch(Exception e)
    {
      fail("disconnectFromReplServer", e);
    }
  }

  private int getChangelogPort(short changelogID)
  {
    if (replServerPort[changelogID] == 0)
    {
      try
      {
        // Find  a free port for the replicationServer
        ServerSocket socket = TestCaseUtils.bindFreePort();
        replServerPort[changelogID] = socket.getLocalPort();
        socket.close();
      }
      catch(Exception e)
      {
        fail("Cannot retrieve a free port for replication server."
            + e.getMessage());
      }
    }
    return replServerPort[changelogID];
  }

  protected static final String REPLICATION_GENERATION_ID =
    "ds-sync-generation-id";

  private long readGenIdFromSuffixRootEntry()
  {
    long genId=-1;
    try
    {
      Entry resultEntry = getEntry(baseDn, 1000, true);
      if (resultEntry==null)
      {
        debugInfo("Entry not found <" + baseDn + ">");
      }
      else
      {
        debugInfo("Entry found <" + baseDn + ">");

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
    catch(Exception e)
    {
      fail("Exception raised in readGenId", e);
    }
    return genId;
  }

  private void performLdifImport()
  {
    try
    {
      // Create a temporary test LDIF file.
      ldifFile = File.createTempFile("import-test", ".ldif");
      String resourcePath = DirectoryServer.getInstanceRoot() + File.separator +
      "config" + File.separator + "MakeLDIF";
      LdifFileWriter.makeLdif(ldifFile.getPath(), resourcePath, template);

      // Launch import of the Ldif file on the memory test backend
      // Note: we do not use a task here as import task does not work on memory
      // backend: it disables then re-enables backend which leads to backend
      // object instance lost and this is not accepttable for a backend with
      // non persistent data
      LDIFImportConfig importConfig =
        new LDIFImportConfig(ldifFile.getAbsolutePath());

      MemoryBackend memoryBackend =
        (MemoryBackend) DirectoryServer.getBackend(TEST_BACKEND_ID);
      memoryBackend.importLDIF(importConfig);

    }
    catch(Exception e)
    {
     fail("Could not perform ldif import on memory test backend: "
       + e.getMessage());
    }
  }

  private String createEntry(UUID uid)
  {
    String user2dn = "uid=user"+uid+",ou=People," + baseDnStr;
    return new String(
        "dn: "+ user2dn + "\n"
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
        + "userPassword: password\n" + "initials: AA\n");
  }

  static protected ReplicationMsg createAddMsg()
  {
    Entry personWithUUIDEntry = null;
    String user1entryUUID;
    String baseUUID = null;
    String user1dn;

    /*
     * Create a Change number generator to generate new changenumbers
     * when we need to send operation messages to the replicationServer.
     */
    ChangeNumberGenerator gen = new ChangeNumberGenerator((short) 2, 0);

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

    try
    {
      personWithUUIDEntry = TestCaseUtils.entryFromLdifString(entryWithUUIDldif);
    }
    catch(Exception e)
    {
      fail(e.getMessage());
    }

    // Create and publish an update message to add an entry.
    AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());

    return addMsg;
  }

  /*
   * Check that the expected number of changes are in the replication
   * server database.
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
      Thread.sleep(500);
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
      rgenId = replServer1.getGenerationId(baseDn.toNormalizedString());
      assertEquals(rgenId, EMPTY_DN_GENID);

      // Clean for next test
      debugInfo(testCase + " Unconfiguring DS1 to replicate to RS1(" + changelog1ID + ")");
      disconnectFromReplServer(changelog1ID);


      //===========================================================
      debugInfo(testCase + " ** TEST ** Non empty backend");

      debugInfo(testCase + " Adding test entries to DS");
      this.addTestEntriesToDB(updatedEntries);

      debugInfo(testCase + " Configuring DS1 to replicate to RS1(" + changelog1ID + ") on a non empty backend");
      connectServer1ToChangelog(changelog1ID);

      debugInfo(testCase + " Test that the generationId is written in the DB in the root entry on DS1");
      genId = readGenIdFromSuffixRootEntry();
      assertTrue(genId != -1);
      assertTrue(genId != EMPTY_DN_GENID);

      debugInfo(testCase + " Test that the generationId is set on RS1");
      rgenId = replServer1.getGenerationId(baseDn.toNormalizedString());
      assertEquals(genId, rgenId);

      //===========================================================
      debugInfo(testCase + " ** TEST ** DS2 connection to RS1 with bad genID");

      try
      {
        broker2 = openReplicationSession(baseDn,
            server2ID, 100, getChangelogPort(changelog1ID),
            1000, !emptyOldChanges, genId+1);
      }
      catch(SocketException se)
      {
        fail("DS2 with bad genID failed to connect to RS1.");
      }

      //===========================================================
      debugInfo(testCase + " ** TEST ** DS3 connection to RS1 with good genID");
      try
      {
        broker3 = openReplicationSession(baseDn,
            server3ID, 100, getChangelogPort(changelog1ID), 1000, !emptyOldChanges, genId);
      }
      catch(SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

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
      }

      //===========================================================
      debugInfo(testCase + " ** TEST ** The part of the topology with the right gen ID should work well");

      // Now create a change that must be replicated
      String ent1[] = { createEntry(UUID.randomUUID()) };
      this.addTestEntriesToDB(ent1);

      // Verify that RS1 does contain the change related to this ADD.
      Thread.sleep(500);
      checkChangelogSize(1);

      // Verify that DS3 receives this change
      try
      {
        ReplicationMsg msg = broker3.receive();
        debugInfo("Broker 3 received expected update msg" + msg);
      }
      catch(SocketTimeoutException e)
      {
        fail("Update message is supposed to be received.");
      }

      //===========================================================
      debugInfo(testCase + " ** TEST ** Persistence of the generation ID in RS1");

      long genIdBeforeShut =
        replServer1.getGenerationId(baseDn.toNormalizedString());

      debugInfo("Shutdown replServer1");
      broker2.stop();
      broker2 = null;
      broker3.stop();
      broker3 = null;
      replServer1.remove();
      replServer1 = null;

      debugInfo("Create again replServer1");
      replServer1 = createReplicationServer(changelog1ID, false, testCase);

      // To search the replication server db later in these tests, we need
      // to attach the search backend to the replication server just created.
      Thread.sleep(500);
      b = (ReplicationBackend)DirectoryServer.getBackend("replicationChanges");
      b.setServer(replServer1);

      debugInfo("Delay to allow DS to reconnect to replServer1");

      long genIdAfterRestart =
        replServer1.getGenerationId(baseDn.toNormalizedString());
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
      try
      {
        debugInfo("Create again broker2");
        broker2 = openReplicationSession(baseDn,
            server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges, genId);
        assertTrue(broker2.isConnected(), "Broker2 failed to connect to replication server");

        debugInfo("Create again broker3");
        broker3 = openReplicationSession(baseDn,
            server3ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges, genId);
        assertTrue(broker3.isConnected(), "Broker3 failed to connect to replication server");
      }
      catch(SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }


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
          "ds-task-class-name: org.opends.server.replication.plugin.SetGenerationIdTask",
          "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr);
      addTask(taskReset, ResultCode.SUCCESS, null);
      waitTaskState(taskReset, TaskState.COMPLETED_SUCCESSFULLY, null);

      // Broker 2 and 3 should receive 1 change status message to order them
      // to enter the bad gen id status
      try
      {
        ReplicationMsg msg = broker2.receive();
        if (!(msg instanceof ChangeStatusMsg))
        {
          fail("Broker 2 connection is expected to receive 1 ChangeStatusMsg" +
            " to enter the bad gen id status"
              + msg);
        }
        ChangeStatusMsg csMsg = (ChangeStatusMsg)msg;
        if (csMsg.getRequestedStatus() != ServerStatus.BAD_GEN_ID_STATUS)
        {
          fail("Broker 2 connection is expected to receive 1 ChangeStatusMsg" +
            " to enter the bad gen id status"
              + msg);
        }
      }
      catch(SocketTimeoutException se)
      {
        fail("DS2 is expected to receive 1 ChangeStatusMsg to enter the " +
          "bad gen id status.");
      }
      try
      {
        ReplicationMsg msg = broker3.receive();
        if (!(msg instanceof ChangeStatusMsg))
        {
          fail("Broker 3 connection is expected to receive 1 ChangeStatusMsg" +
            " to enter the bad gen id status"
              + msg);
        }
        ChangeStatusMsg csMsg = (ChangeStatusMsg)msg;
        if (csMsg.getRequestedStatus() != ServerStatus.BAD_GEN_ID_STATUS)
        {
          fail("Broker 3 connection is expected to receive 1 ChangeStatusMsg" +
            " to enter the bad gen id status"
              + msg);
        }
      }
      catch(SocketTimeoutException se)
      {
        fail("DS3 is expected to receive 1 ChangeStatusMsg to enter the " +
          "bad gen id status.");
      }

      debugInfo("DS1 root entry must contain the new gen ID");
      genId = readGenIdFromSuffixRootEntry();
      assertTrue(genId != -1, "DS is expected to have a new genID computed " +
          " after on-line import but genId=" + genId);
      assertTrue(genId != oldGenId, "The new genID after import and reset of genID "
        + "is expected to be diffrent from previous one");

      debugInfo("RS1 must have the new gen ID");
      rgenId = replServer1.getGenerationId(baseDn.toNormalizedString());
      assertEquals(genId, rgenId, "DS and replServer are expected to have same genId.");

      debugInfo("RS1 must have been cleared since it has not the proper generation ID");
      checkChangelogSize(0);

      assertTrue(!replServer1.getReplicationServerDomain(
          baseDn.toNormalizedString(), false).
          isDegradedDueToGenerationId(server1ID),
      "Expecting that DS1 status in RS1 is : not in bad gen id.");

      //===============================================================
      debugInfo(testCase + " ** TEST ** Previous test set a new gen ID on the "+
          "topology, verify degradation of DS2 and DS3");

      assertTrue(replServer1.getReplicationServerDomain(
          baseDn.toNormalizedString(), false).
          isDegradedDueToGenerationId(server2ID),
      "Expecting that DS2 with old gen ID is in bad gen id from RS1");
      assertTrue(replServer1.getReplicationServerDomain(
          baseDn.toNormalizedString(), false).
          isDegradedDueToGenerationId(server3ID),
      "Expecting that DS3 with old gen ID is in bad gen id from RS1");

      debugInfo("Add entries to DS1, update should not be sent to DS2 and DS3 that are in bad gen id");
      String[] ent3 = { createEntry(UUID.randomUUID()) };
      this.addTestEntriesToDB(ent3);

      debugInfo("RS1 must have stored that update.");
      Thread.sleep(500);
      checkChangelogSize(1);

      try
      {
        ReplicationMsg msg = broker2.receive();
        fail("No update message is supposed to be received by broker2 in bad gen id. " + msg);
      } catch(SocketTimeoutException e) { /* expected */ }

      try
      {
        ReplicationMsg msg = broker3.receive();
        fail("No update message is supposed to be received by broker3 in bad gen id. " + msg);
      } catch(SocketTimeoutException e) { /* expected */ }


      debugInfo("DS2 is publishing a change and RS1 must ignore this change, DS3 must not receive it.");
      broker2.publish(createAddMsg());

      // Updates count in RS1 must stay unchanged = to 1
      Thread.sleep(500);
      checkChangelogSize(1);

      try
      {
        ReplicationMsg msg = broker3.receive();
        fail("No update message is supposed to be received by broker3 in bad gen id. "+ msg);
      } catch(SocketTimeoutException e) { /* expected */ }


      //===============================================================
      debugInfo(testCase + " ** TEST ** Previous test put DS2 and DS3 in bad gen id, "+
          " now simulates \"dsreplication initialize \"by doing a TU+reset " +
          " from DS1 to DS2 and DS3, verify NON degradation of DS2 and DS3");

      // In S1 launch the total update to initialize S2
      addTask(taskInitRemoteS2, ResultCode.SUCCESS, null);

      // S2 should be re-initialized and have a new valid genId
      int receivedEntriesNb = this.receiveImport(broker2, server2ID, null);
      debugInfo("broker2 has been initialized from DS with #entries=" + receivedEntriesNb);

      broker2.stop();

      // Simulates the broker restart at the end of the import
      broker2 = openReplicationSession(baseDn,
          server2ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges, genId);

      broker3.stop();

      // Simulates the broker restart at the end of the import
      broker3 = openReplicationSession(baseDn,
          server3ID, 100, getChangelogPort(changelog1ID), 1000, emptyOldChanges, genId);

      debugInfo("Adding reset task to DS1");
      taskReset = TestCaseUtils.makeEntry(
          "dn: ds-task-id=resetgenid"+ UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-reset-generation-id",
          "ds-task-class-name: org.opends.server.replication.plugin.SetGenerationIdTask",
          "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr);

      addTask(taskReset, ResultCode.SUCCESS, null);
      waitTaskState(taskReset, TaskState.COMPLETED_SUCCESSFULLY, null);
      Thread.sleep(200);

      debugInfo("Verify that RS1 has still the right genID");
      assertEquals(replServer1.getGenerationId(baseDn.toNormalizedString()), rgenId);

      // Updates count in RS1 must stay unchanged = to 1
      Thread.sleep(500);
      checkChangelogSize(1);

      debugInfo("Verifying that DS2 is not in bad gen id any more");

      assertTrue(!replServer1.getReplicationServerDomain(
          baseDn.toNormalizedString(), false).
          isDegradedDueToGenerationId(server2ID),
      "Expecting that DS2 is not in bad gen id from RS1");

      debugInfo("Verifying that DS3 is not in bad gen id any more");

      assertTrue(!replServer1.getReplicationServerDomain(
          baseDn.toNormalizedString(), false).
          isDegradedDueToGenerationId(server3ID),
      "Expecting that DS3 is not in bad gen id from RS1");

      debugInfo("DS2 is publishing a change and RS1 must store this change, DS3 must receive it.");
      AddMsg emsg = (AddMsg)createAddMsg();
      broker2.publish(emsg);

      Thread.sleep(500);
      checkChangelogSize(2);

      try
      {
        ReplicationMsg msg = broker3.receive();

        /* expected */
        AddMsg rcvmsg = (AddMsg)msg;
        assertEquals(rcvmsg.getChangeNumber(), emsg.getChangeNumber());
      }
      catch(SocketTimeoutException e)
      {
        fail("The msg send by DS2 is expected to be received by DS3)");
      }

      //===============================================================
      debugInfo(testCase + " ** TEST ** General cleaning");

      debugInfo("Disconnect DS from replServer1 (required in order to DEL entries).");
      disconnectFromReplServer(changelog1ID);

      debugInfo("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " +
          stackTraceToSingleLineString(e));
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
   * - genId reset   propagation from one RS to the others
   */
  @Test(enabled=false)
  public void testMultiRS() throws Exception
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
      Thread.sleep(500);

      debugInfo("Connecting DS to replServer1");
      connectServer1ToChangelog(changelog1ID);
      Thread.sleep(1500);

      debugInfo("Expect genId are set in all replServers.");
      assertEquals(replServer1.getGenerationId(baseDn.toNormalizedString()), EMPTY_DN_GENID,
        " in replServer1");
      assertEquals(replServer2.getGenerationId(baseDn.toNormalizedString()), EMPTY_DN_GENID,
        " in replServer2");
      assertEquals(replServer3.getGenerationId(baseDn.toNormalizedString()), EMPTY_DN_GENID,
        " in replServer3");

      debugInfo("Disconnect DS from replServer1.");
      disconnectFromReplServer(changelog1ID);
      Thread.sleep(3000);

      debugInfo(
        "Expect genIds to be resetted in all servers to -1 as no more DS in topo");
      assertEquals(replServer1.getGenerationId(baseDn.toNormalizedString()), -1);
      assertEquals(replServer2.getGenerationId(baseDn.toNormalizedString()), -1);
      assertEquals(replServer3.getGenerationId(baseDn.toNormalizedString()), -1);

      debugInfo("Add entries to DS");
      this.addTestEntriesToDB(updatedEntries);

      debugInfo("Connecting DS to replServer2");
      connectServer1ToChangelog(changelog2ID);
      Thread.sleep(3000);

      debugInfo(
        "Expect genIds to be set in all servers based on the added entries.");
      genId = readGenIdFromSuffixRootEntry();
      assertTrue(genId != -1);
      assertEquals(replServer1.getGenerationId(baseDn.toNormalizedString()), genId);
      assertEquals(replServer2.getGenerationId(baseDn.toNormalizedString()), genId);
      assertEquals(replServer3.getGenerationId(baseDn.toNormalizedString()), genId);

      debugInfo("Connecting broker2 to replServer3 with a good genId");
      try
      {
        broker2 = openReplicationSession(baseDn,
          server2ID, 100, getChangelogPort(changelog3ID),
          1000, !emptyOldChanges, genId);
        Thread.sleep(1000);
      } catch (SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

      debugInfo(
        "Expecting that broker2 is not in bad gen id since it has a correct genId");
      assertTrue(!replServer1.getReplicationServerDomain(baseDn.toNormalizedString(), false).
        isDegradedDueToGenerationId(server2ID));

      debugInfo("Disconnecting DS from replServer1");
      disconnectFromReplServer(changelog1ID);

      debugInfo(
        "Expect all genIds to keep their value since broker2 is still connected.");
      assertEquals(replServer1.getGenerationId(baseDn.toNormalizedString()), genId);
      assertEquals(replServer2.getGenerationId(baseDn.toNormalizedString()), genId);
      assertEquals(replServer3.getGenerationId(baseDn.toNormalizedString()), genId);

      debugInfo("Connecting broker2 to replServer1 with a bad genId");
      try
      {
        long badgenId = 1;
        broker3 = openReplicationSession(baseDn,
          server3ID, 100, getChangelogPort(changelog1ID),
          1000, !emptyOldChanges, badgenId);
        Thread.sleep(1000);
      } catch (SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

      debugInfo(
        "Expecting that broker3 is in bad gen id since it has a bad genId");
      assertTrue(replServer1.getReplicationServerDomain(baseDn.toNormalizedString(), false).
        isDegradedDueToGenerationId(server3ID));

      int found = testEntriesInDb();
      assertEquals(found, updatedEntries.length,
        " Entries present in DB :" + found +
        " Expected entries :" + updatedEntries.length);

      debugInfo("Connecting DS to replServer1.");
      connectServer1ToChangelog(changelog1ID);
      Thread.sleep(1000);


      debugInfo("Adding reset task to DS.");
      Entry taskReset = TestCaseUtils.makeEntry(
        "dn: ds-task-id=resetgenid" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-reset-generation-id",
        "ds-task-class-name: org.opends.server.replication.plugin.SetGenerationIdTask",
        "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr);
      addTask(taskReset, ResultCode.SUCCESS, null);
      waitTaskState(taskReset, TaskState.COMPLETED_SUCCESSFULLY, null);
      Thread.sleep(500);

      debugInfo("Verifying that all replservers genIds have been reset.");
      genId = readGenIdFromSuffixRootEntry();
      assertEquals(replServer1.getGenerationId(baseDn.toNormalizedString()), genId);
      assertEquals(replServer2.getGenerationId(baseDn.toNormalizedString()), genId);
      assertEquals(replServer3.getGenerationId(baseDn.toNormalizedString()), genId);

      debugInfo("Adding reset task to DS.");
      taskReset = TestCaseUtils.makeEntry(
        "dn: ds-task-id=resetgenid" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-reset-generation-id",
        "ds-task-class-name: org.opends.server.replication.plugin.SetGenerationIdTask",
        "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr,
        "ds-task-reset-generation-id-new-value: -1");
      addTask(taskReset, ResultCode.SUCCESS, null);
      waitTaskState(taskReset, TaskState.COMPLETED_SUCCESSFULLY, null);
      Thread.sleep(500);

      debugInfo("Verifying that all replservers genIds have been reset.");
      genId = readGenIdFromSuffixRootEntry();
      assertEquals(replServer1.getGenerationId(baseDn.toNormalizedString()), -1);
      assertEquals(replServer2.getGenerationId(baseDn.toNormalizedString()), -1);
      assertEquals(replServer3.getGenerationId(baseDn.toNormalizedString()), -1);

      debugInfo(
        "Disconnect DS from replServer1 (required in order to DEL entries).");
      disconnectFromReplServer(changelog1ID);

      debugInfo("Successfully ending " + testCase);
    } finally
    {
      postTest();
    }
  }

  /**
   * Disconnect broker and remove entries from the local DB
   * @throws Exception
   */
  protected void postTest()
  {
    debugInfo("Post test cleaning.");

    // Clean brokers
    if (broker2 != null)
      broker2.stop();
    broker2 = null;
    if (broker3 != null)
      broker3.stop();
    broker3 = null;

    if (replServer1 != null)
    {
      replServer1.clearDb();
      replServer1.remove();
      replServer1 = null;
    }
    if (replServer2 != null)
    {
      replServer2.clearDb();
      replServer2.remove();
      replServer2 = null;
    }
    if (replServer3 != null)
    {
      replServer3.clearDb();
      replServer3.remove();
      replServer3 = null;
    }

    super.cleanRealEntries();

    // Clean replication server ports
    for (int i = 0; i < replServerPort.length; i++)
    {
      replServerPort[i] = 0;
    }

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
      Thread.sleep(1000);

      debugInfo(testCase + " Expect genId attribute to be not retrievable");
      genId = readGenIdFromSuffixRootEntry();
      assertEquals(genId,-1);

      this.addTestEntriesToDB(updatedEntries);

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
   * to check that it handle correctly deconnection and reconnection.
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
        broker = openReplicationSession(baseDn,
            server2ID, 100, getChangelogPort(changelog1ID),
            1000, !emptyOldChanges, generationId);
        debugInfo(testCase + " Expect genId to be set in memory on the replication " +
          " server side even if not wrote on disk/db since no change occurred.");
        rgenId = replServer1.getGenerationId(baseDn.toNormalizedString());
        assertEquals(rgenId, generationId);
        broker.stop();
        broker = null;
        Thread.sleep(2000); // Let time to RS to clear info about previous connection
      }
    } finally
    {
      if (broker != null)
        broker.stop();
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
    testMultiRS();
    testServerStop();
    testLoop();
  }
}
