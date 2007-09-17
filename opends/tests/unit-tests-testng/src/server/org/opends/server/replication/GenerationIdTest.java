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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication;

import static org.opends.server.config.ConfigConstants.ATTR_TASK_LOG_MESSAGES;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_STATE;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.net.SocketTimeoutException;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.plugin.ReplicationDomain;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DoneMessage;
import org.opends.server.replication.protocol.EntryMessage;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.InitializeTargetMessage;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.replication.protocol.SocketSession;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.tasks.LdifFileWriter;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests contained here:
 *
 * - testSingleRS : test generation ID setting with different servers and one
 *   Replication server.
 *
 * - testMultiRS : tests generation ID propagatoion with more than one 
 *   Replication server.
 *
 */

public class GenerationIdTest extends ReplicationTestCase
{
  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  private static final String baseDnStr = "dc=example,dc=com";
  private static final String baseSnStr = "genidcom";
  
  private static final int   WINDOW_SIZE = 10;
  private static final int   CHANGELOG_QUEUE_SIZE = 100;
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
  ReplicationDomain replDomain = null;
  private Entry taskInitRemoteS2;
  SocketSession ssSession = null;
  boolean ssShutdownRequested = false;
  protected String[] updatedEntries;

  private static int[] replServerPort = new int[20];

  /**
   * A temporary LDIF file containing some test entries.
   */
  private File ldifFile;

  /**
   * A temporary file to contain rejected entries.
   */
  private File rejectFile;

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
  @BeforeClass
  public void setUp() throws Exception
  {
    //log("Starting generationIdTest setup: debugEnabled:" + debugEnabled());

    // This test suite depends on having the schema available.
    TestCaseUtils.startServer();

    baseDn = DN.decode(baseDnStr);

    updatedEntries = newLDIFEntries();

    // Create an internal connection in order to provide operations
    // to DS to populate the db -
    connection = InternalClientConnection.getRootConnection();

    // Synchro provider
    String synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Synchro multi-master
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
      + synchroStringDN;

    // Synchro suffix
    synchroServerEntry = null;

    // Add config entries to the current DS server based on :
    // Add the replication plugin: synchroPluginEntry & synchroPluginStringDN
    // Add synchroServerEntry
    // Add replServerEntry
    configureReplication();

    taskInitRemoteS2 = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-initialize-remote-replica",
        "ds-task-class-name: org.opends.server.tasks.InitializeTargetTask",
        "ds-task-initialize-domain-dn: " + baseDn,
        "ds-task-initialize-replica-server-id: " + server2ID);

    // Change log
    String changeLogStringDN = "cn=Changelog Server, " + synchroPluginStringDN;
    String changeLogLdif = "dn: " + changeLogStringDN + "\n"
    + "objectClass: top\n"
    + "objectClass: ds-cfg-synchronization-changelog-server-config\n"
    + "cn: Changelog Server\n" + "ds-cfg-changelog-port: 8990\n"
    + "ds-cfg-changelog-server-id: 1\n"
    + "ds-cfg-window-size: " + WINDOW_SIZE + "\n"
    + "ds-cfg-changelog-max-queue-size: " + CHANGELOG_QUEUE_SIZE;
    replServerEntry = TestCaseUtils.entryFromLdifString(changeLogLdif);
    replServerEntry = null;

  }

  // Tests that entries have been written in the db
  private int testEntriesInDb()
  {
    debugInfo("TestEntriesInDb");
    short found = 0;

    for (String entry : updatedEntries)
    {

      int dns = entry.indexOf("dn: ");
      int dne = entry.indexOf("dc=com");
      String dn = entry.substring(dns+4,dne+6);

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
        + "objectClass: domain\n"
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
    ReplicationMessage msg;
    short entriesReceived = 0;
    while (true)
    {
      try
      {
        debugInfo("Broker " + serverID + " Wait for entry or done msg");
        msg = broker.receive();

        if (msg == null)
          break;

        if (msg instanceof InitializeTargetMessage)
        {
          debugInfo("Broker " + serverID + " receives InitializeTargetMessage ");
          entriesReceived = 0;
        }
        else if (msg instanceof EntryMessage)
        {
          EntryMessage em = (EntryMessage)msg;
          debugInfo("Broker " + serverID + " receives entry " + new String(em.getEntryBytes()));
          entriesReceived++;
        }
        else if (msg instanceof DoneMessage)
        {
          debugInfo("Broker " + serverID + "  receives done ");
          break;
        }
        else if (msg instanceof ErrorMessage)
        {
          ErrorMessage em = (ErrorMessage)msg;
          debugInfo("Broker " + serverID + "  receives ERROR "
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
   * @param all         Specifies whether to coonect the created replication 
   *                    server to the other replication servers in the test.
   * @return The new created replication server.
   */
  private ReplicationServer createReplicationServer(short changelogId, 
      boolean all, String suffix)
  {
    SortedSet<String> servers = null;
    servers = new TreeSet<String>();
    try
    {
      if (changelogId==changelog1ID)
      {
        if (replServer1!=null)
          return replServer1;
      }
      else if (changelogId==changelog2ID)
      {
        if (replServer2!=null)
          return replServer2;
      }
      else if (changelogId==changelog3ID)
      {
        if (replServer3!=null)
          return replServer3;
      }
      if (all)
      {
        servers.add("localhost:" + getChangelogPort(changelog1ID));
        servers.add("localhost:" + getChangelogPort(changelog2ID));
        servers.add("localhost:" + getChangelogPort(changelog3ID));
      }
      int chPort = getChangelogPort(changelogId);
      String chDir = "genid"+changelogId+suffix+"Db";
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
  private void connectToReplServer(short changelogID)
  {
    // Connect DS to the replicationServer
    try
    {
      // suffix synchronized
      String synchroServerStringDN = synchroPluginStringDN;
      String synchroServerLdif =
        "dn: cn=" + baseSnStr + ", cn=domains," + synchroServerStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider-config\n"
        + "cn: " + baseSnStr + "\n"
        + "ds-cfg-synchronization-dn: " + baseDnStr + "\n"
        + "ds-cfg-changelog-server: localhost:"
        + getChangelogPort(changelogID)+"\n"
        + "ds-cfg-directory-server-id: " + server1ID + "\n"
        + "ds-cfg-receive-status: true\n"
        + "ds-cfg-window-size: " + WINDOW_SIZE;

      if (synchroServerEntry == null)
      {
        synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);
        DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
        assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the synchronized server");
        configEntryList.add(synchroServerEntry.getDN());

        replDomain = ReplicationDomain.retrievesReplicationDomain(baseDn);

      }
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
      String synchroServerStringDN = "cn=" + baseSnStr + ", cn=domains," + 
      synchroPluginStringDN;
      if (synchroServerEntry != null)
      {
        DN synchroServerDN = DN.decode(synchroServerStringDN);
        DirectoryServer.getConfigHandler().deleteEntry(synchroServerDN,null);
        assertTrue(DirectoryServer.getConfigEntry(synchroServerEntry.getDN())==null,
        "Unable to delete the synchronized domain");
        synchroServerEntry = null;

        configEntryList.remove(configEntryList.indexOf(synchroServerDN));
      }
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

  private long readGenId()
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
          LinkedHashSet<AttributeValue> values = attr.getValues();
          if (values.size() == 1)
          {
            genId = Long.decode(values.iterator().next().getStringValue());
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

  private Entry getTaskImport()
  {
    Entry task = null;

    try
    {
      // Create a temporary test LDIF file.
      ldifFile = File.createTempFile("import-test", ".ldif");
      String resourcePath = DirectoryServer.getServerRoot() + File.separator +
      "config" + File.separator + "MakeLDIF";
      LdifFileWriter.makeLdif(ldifFile.getPath(), resourcePath, template);
      // Create a temporary rejects file.
      rejectFile = File.createTempFile("import-test-rejects", ".ldif");

      task = TestCaseUtils.makeEntry(
          "dn: ds-task-id=" + UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-import",
          "ds-task-class-name: org.opends.server.tasks.ImportTask",
          "ds-task-import-backend-id: userRoot",
          "ds-task-import-ldif-file: " + ldifFile.getPath(),
          "ds-task-import-reject-file: " + rejectFile.getPath(),
          "ds-task-import-overwrite-rejects: TRUE",
          "ds-task-import-exclude-attribute: description"
      );
    }
    catch(Exception e)
    {
    }
    return task;
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

  static protected ReplicationMessage createAddMsg()
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
    ReplicationDomain.clearJEBackend(false,
        "userRoot",
        baseDn.toNormalizedString());

    try
    {
      long rgenId;
      long genId;

      replServer1 = createReplicationServer(changelog1ID, false, testCase);

      /*
       * Test  : empty replicated backend
       * Check : nothing is broken - no generationId generated
       */

      // Connect DS to RS with no data
      // Read generationId - should be not retrievable since no entry
      debugInfo(testCase + " Connecting DS1 to replServer1(" + changelog1ID + ")");
      connectToReplServer(changelog1ID);
      Thread.sleep(1000);

      debugInfo(testCase + " Expect genId attribute to be not retrievable");
      genId = readGenId();
      assertEquals(genId,-1);

      debugInfo(testCase + " Expect genId to be set in memory on the replication " +
      " server side even if not wrote on disk/db since no change occured.");
      rgenId = replServer1.getGenerationId(baseDn);
      assertEquals(rgenId, 3211313L);

      debugInfo(testCase + " Disconnecting DS1 from replServer1(" + changelog1ID + ")");
      disconnectFromReplServer(changelog1ID);

      /*
       * Test  : non empty replicated backend
       * Check : generationId correctly generated
       */

      // Now disconnect - create entries and reconnect
      // Test that generation has been added to the data.
      debugInfo(testCase + " add test entries to DS");
      this.addTestEntriesToDB(updatedEntries);
      connectToReplServer(changelog1ID);

      // Test that the generationId is written in the DB in the 
      // root entry on the replica side
      genId = readGenId();
      assertTrue(genId != -1);
      assertTrue(genId != 3211313L);

      // Test that the generationId is set on the replication server side
      rgenId = replServer1.getGenerationId(baseDn);
      assertEquals(genId, rgenId);

      /*
       * Test : Connection from 2nd broker with a different generationId
       * Check: We should receive an error message
       */

      try
      {
        broker2 = openReplicationSession(baseDn,
            server2ID, 100, getChangelogPort(changelog1ID), 
            1000, !emptyOldChanges, genId+1);
      }
      catch(SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }
      try
      {
        ReplicationMessage msg = broker2.receive();
        if (!(msg instanceof ErrorMessage))
        {
          fail("Broker connection is expected to receive an ErrorMessage."
              + msg);
        }
        ErrorMessage emsg = (ErrorMessage)msg;
        debugInfo(testCase + " " + emsg.getMsgID() + " " + emsg.getDetails());
      }
      catch(SocketTimeoutException se)
      {
        fail("Broker is expected to receive an ErrorMessage.");
      }

      /*
       * Test  : Connect with same generationId
       * Check : Must be accepted.
       */
      try
      {
        broker3 = openReplicationSession(baseDn,
            server3ID, 100, getChangelogPort(changelog1ID), 1000, !emptyOldChanges, genId);
      }
      catch(SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

      /*
       * Test  : generationID persistence in Replication server
       *         Shutdown/Restart Replication Server and redo connections
       *         with valid and invalid generationId
       * Check : same expected connections results 
       */

      // The changes from broker2 should be ignored
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

      // Now create a change that must be replicated
      String ent1[] = { createEntry(UUID.randomUUID()) };
      this.addTestEntriesToDB(ent1);

      try
      {
        ReplicationMessage msg = broker3.receive();
        debugInfo("Broker 3 received expected update msg" + msg);
      }
      catch(SocketTimeoutException e)
      {
        fail("Update message is supposed to be received.");
      }

      long genIdBeforeShut = replServer1.getGenerationId(baseDn);

      debugInfo("Shutdown replServer1");
      broker2.stop();
      broker2 = null;
      broker3.stop();
      broker3 = null;
      replServer1.shutdown();
      replServer1 = null;

      debugInfo("Create again replServer1");
      replServer1 = createReplicationServer(changelog1ID, false, testCase);
      debugInfo("Delay to allow DS to reconnect to replServer1");
      Thread.sleep(200);

      long genIdAfterRestart = replServer1.getGenerationId(baseDn);
      debugInfo("Aft restart / replServer.genId=" + genIdAfterRestart);
      assertTrue(replServer1!=null, "Replication server creation failed.");
      assertTrue(genIdBeforeShut == genIdAfterRestart,
      "generationId is expected to have the same value after replServer1 restart");

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

      /*
       * 
       * FIXME Should clearJEBackend() regenerate generationId and do a start
       *       against ReplicationServer ?
       */

      /*
       * Test: Reset the replication server in order to allow new data set.
       */

      debugInfo("Launch an on-line import on DS.");
      genId=-1;
      Entry importTask = getTaskImport();
      addTask(importTask, ResultCode.SUCCESS, null);
      waitTaskState(importTask, TaskState.COMPLETED_SUCCESSFULLY, null);
      Thread.sleep(500);

      Entry taskReset = TestCaseUtils.makeEntry(
          "dn: ds-task-id=resetgenid"+genId+ UUID.randomUUID() +
          ",cn=Scheduled Tasks,cn=Tasks",
          "objectclass: top",
          "objectclass: ds-task",
          "objectclass: ds-task-reset-generation-id",
          "ds-task-class-name: org.opends.server.tasks.SetGenerationIdTask",
          "ds-task-reset-generation-id-domain-base-dn: " + baseDnStr);

      debugInfo("Reset generationId");
      addTask(taskReset, ResultCode.SUCCESS, null);
      waitTaskState(taskReset, TaskState.COMPLETED_SUCCESSFULLY, null);
      Thread.sleep(200);

      // TODO: Test that replication server db has been cleared

      debugInfo("Expect new genId to be computed on DS and sent to all replServers after on-line import.");
      genId = readGenId();
      assertTrue(genId != -1, "DS is expected to have a new genID computed " +
          " after on-line import but genId=" + genId);

      rgenId = replServer1.getGenerationId(baseDn);     
      assertEquals(genId, rgenId, "DS and replServer are expected to have same genId.");

      assertTrue(!replServer1.getReplicationCache(baseDn, false).
          isDegradedDueToGenerationId(server1ID),
      "Expecting that DS is not degraded since domain genId has been reset");

      assertTrue(replServer1.getReplicationCache(baseDn, false).
          isDegradedDueToGenerationId(server2ID),
      "Expecting that broker2 is degraded since domain genId has been reset");
      assertTrue(replServer1.getReplicationCache(baseDn, false).
          isDegradedDueToGenerationId(server3ID),
      "Expecting that broker3 is degraded since domain genId has been reset");


      // Now create a change that normally would be replicated
      // but will not be replicated here since DS and brokers are degraded
      String[] ent3 = { createEntry(UUID.randomUUID()) };
      this.addTestEntriesToDB(ent3);

      try
      {
        ReplicationMessage msg = broker2.receive();
        if (!(msg instanceof ErrorMessage))
        {
          fail("Broker 2 connection is expected to receive an ErrorMessage."
              + msg);
        }
        ErrorMessage emsg = (ErrorMessage)msg;
        debugInfo(testCase + " " + emsg.getMsgID() + " " + emsg.getDetails());
      }
      catch(SocketTimeoutException se)
      {
        fail("Broker 2 is expected to receive an ErrorMessage.");
      }
      try
      {
        ReplicationMessage msg = broker3.receive();
        if (!(msg instanceof ErrorMessage))
        {
          fail("Broker 3 connection is expected to receive an ErrorMessage."
              + msg);
        }
        ErrorMessage emsg = (ErrorMessage)msg;
        debugInfo(testCase + " " + emsg.getMsgID() + " " + emsg.getDetails());
      }
      catch(SocketTimeoutException se)
      {
        fail("Broker 3 is expected to receive an ErrorMessage.");
      }

      try
      {
        ReplicationMessage msg = broker2.receive();
        fail("No update message is supposed to be received by degraded broker2" + msg);
      } catch(SocketTimeoutException e) { /* expected */ }

      try
      {
        ReplicationMessage msg = broker3.receive();
        fail("No update message is supposed to be received by degraded broker3"+ msg);
      } catch(SocketTimeoutException e) { /* expected */ }

      debugInfo("broker2 is publishing a change, " + 
      "replServer1 expected to ignore this change.");
      broker2.publish(createAddMsg());
      try
      {
        ReplicationMessage msg = broker3.receive();
        fail("No update message is supposed to be received by degraded broker3"+ msg);
      } catch(SocketTimeoutException e) { /* expected */ }
    
      // In S1 launch the total update to initialize S2
      addTask(taskInitRemoteS2, ResultCode.SUCCESS, null);

      // S2 should be re-initialized and have a new valid genId
      int receivedEntriesNb = this.receiveImport(broker2, server2ID, null);
      debugInfo("broker2 has been initialized from DS with #entries=" + receivedEntriesNb);

      debugInfo("Adding reset task to DS.");
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
      Thread.sleep(200);

      debugInfo("Verifying that replServer1 has been reset.");
      assertEquals(replServer1.getGenerationId(baseDn), rgenId);

      debugInfo("Disconnect DS from replServer1 (required in order to DEL entries).");
      disconnectFromReplServer(changelog1ID);

      postTest();

      debugInfo(testCase + " Clearing DS backend");
      ReplicationDomain.clearJEBackend(false,
          replDomain.getBackend().getBackendID(),
          baseDn.toNormalizedString());

      // At this moment, root entry of the domain has been removed so
      // genId is no more in the database ... but it has still the old
      // value in memory.
      int found = testEntriesInDb();
      replDomain.loadGenerationId();

      debugInfo("Successfully ending " + testCase);
    }
    catch(Exception e)
    {
      fail(testCase + " Exception:"+ e.getMessage() + " " + 
          stackTraceToSingleLineString(e));
    }
  }

  /**
  /**
   * SingleRS tests basic features of generationID 
   * with more than one Replication Server.
   * The following test focus on:
   * - genId checking accross multiple starting RS (replication servers)
   * - genId setting propagation from one RS to the others
   * - genId reset   propagation from one RS to the others
   */
  @Test(enabled=false)
  public void testMultiRS() throws Exception
  {
    String testCase = "testMultiRS";
    long genId;

    debugInfo("Starting " + testCase);

    ReplicationDomain.clearJEBackend(false,
        "userRoot",
        baseDn.toNormalizedString());

    debugInfo ("Creating 3 RS");
    replServer1 = createReplicationServer(changelog1ID, true, testCase);
    replServer2 = createReplicationServer(changelog2ID, true, testCase);
    replServer3 = createReplicationServer(changelog3ID, true, testCase);
    Thread.sleep(500);

    debugInfo("Connecting DS to replServer1");
    connectToReplServer(changelog1ID);
    Thread.sleep(1500);

    debugInfo("Expect genId are set in all replServers.");
    assertEquals(replServer1.getGenerationId(baseDn), 3211313L, " in replServer1");
    assertEquals(replServer2.getGenerationId(baseDn), 3211313L, " in replServer2");
    assertEquals(replServer3.getGenerationId(baseDn), 3211313L, " in replServer3");

    debugInfo("Disconnect DS from replServer1.");
    disconnectFromReplServer(changelog1ID);
    Thread.sleep(1000);

    debugInfo("Expect genId to be unset(-1) in all servers since no server is " +
        " connected and no change ever occured");
    assertEquals(replServer1.getGenerationId(baseDn), -1, " in replServer1");
    assertEquals(replServer2.getGenerationId(baseDn), -1, " in replServer2");
    assertEquals(replServer3.getGenerationId(baseDn), -1, " in replServer3");

    debugInfo("Add entries to DS");
    this.addTestEntriesToDB(updatedEntries);

    debugInfo("Connecting DS to replServer2");
    connectToReplServer(changelog2ID);
    Thread.sleep(1000);

    debugInfo("Expect genIds to be set in all servers based on the added entries.");
    genId = readGenId();
    assertTrue(genId != -1);    
    assertEquals(replServer1.getGenerationId(baseDn), genId);
    assertEquals(replServer2.getGenerationId(baseDn), genId);
    assertEquals(replServer3.getGenerationId(baseDn), genId);

    debugInfo("Connecting broker2 to replServer3 with a good genId");
    try
    {
      broker2 = openReplicationSession(baseDn,
          server2ID, 100, getChangelogPort(changelog3ID), 
          1000, !emptyOldChanges, genId);
      Thread.sleep(1000);
    }
    catch(SocketException se)
    {
      fail("Broker connection is expected to be accepted.");
    }

    debugInfo("Expecting that broker2 is not degraded since it has a correct genId");
    assertTrue(!replServer1.getReplicationCache(baseDn, false).
        isDegradedDueToGenerationId(server2ID));

    debugInfo("Disconnecting DS from replServer1");
    disconnectFromReplServer(changelog1ID);

    debugInfo("Expect all genIds to keep their value since broker2 is still connected.");
    assertEquals(replServer1.getGenerationId(baseDn), genId);
    assertEquals(replServer2.getGenerationId(baseDn), genId);
    assertEquals(replServer3.getGenerationId(baseDn), genId);

    debugInfo("Connecting broker2 to replServer1 with a bad genId");
    try
    {
      long badgenId=1;
      broker3 = openReplicationSession(baseDn,
          server3ID, 100, getChangelogPort(changelog1ID), 
          1000, !emptyOldChanges, badgenId);
      Thread.sleep(1000);
    }
    catch(SocketException se)
    {
      fail("Broker connection is expected to be accepted.");
    }

    debugInfo("Expecting that broker3 is degraded since it has a bad genId");
    assertTrue(replServer1.getReplicationCache(baseDn, false).
        isDegradedDueToGenerationId(server3ID));

    int found = testEntriesInDb();
    assertEquals(found, updatedEntries.length,
        " Entries present in DB :" + found +
        " Expected entries :" + updatedEntries.length);

    debugInfo("Connecting DS to replServer1.");
    connectToReplServer(changelog1ID);
    Thread.sleep(1000);


    debugInfo("Adding reset task to DS.");
    Entry taskReset = TestCaseUtils.makeEntry(
        "dn: ds-task-id=resetgenid"+ UUID.randomUUID() +
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
    genId = readGenId();
    assertEquals(replServer2.getGenerationId(baseDn), genId);
    assertEquals(replServer2.getGenerationId(baseDn), genId);
    assertEquals(replServer3.getGenerationId(baseDn), genId);

    debugInfo("Disconnect DS from replServer1 (required in order to DEL entries).");
    disconnectFromReplServer(changelog1ID);

    debugInfo("Cleaning entries");
    postTest();

    debugInfo("Successfully ending " + testCase);
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
      replServer1.remove();
    if (replServer2 != null)
      replServer2.remove();
    if (replServer3 != null)
      replServer3.remove();
    replServer1 = null;
    replServer2 = null;
    replServer3 = null;

    super.cleanRealEntries();

    try
    {
      ReplicationDomain.clearJEBackend(false,
        replDomain.getBackend().getBackendID(),
        baseDn.toNormalizedString());

      // At this moment, root entry of the domain has been removed so
      // genId is no more in the database ... but it has still the old
      // value in memory.
      testEntriesInDb();
      replDomain.loadGenerationId();
    }
    catch (Exception e) {}
  }
}
