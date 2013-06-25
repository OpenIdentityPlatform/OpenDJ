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
 *      Portions copyright 2011 ForgeRock AS
 */
package org.opends.server.replication;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.config.ConfigException;
import org.opends.server.controls.ExternalChangelogRequestControl;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.controls.PersistentSearchControl;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConnectionHandler;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.LDAPStatistics;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.ExternalChangelogDomainFakeCfg;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.ECLUpdateMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyDnContext;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.StartECLSessionMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.DraftCNDbHandler;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Attributes;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Operation;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.externalchangelog.ECLSearchOperation;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for the replicationServer code.
 */

public class ExternalChangeLogTest extends ReplicationTestCase
{
  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  // The replicationServer that will be used in this test.
  private ReplicationServer replicationServer = null;

  // The port of the replicationServer.
  private int replicationServerPort;

  private static final String TEST_ROOT_DN_STRING2 = "o=test2";
  private static final String TEST_BACKEND_ID2 = "test2";

  private static final String TEST_ROOT_DN_STRING3 = "o=test3";
  private static final String TEST_BACKEND_ID3 = "test3";

  // The LDAPStatistics object associated with the LDAP connection handler.
  private LDAPStatistics ldapStatistics;

  private ChangeNumber gblCN;

  List<Control> NO_CONTROL = null;

  private int brokerSessionTimeout = 5000;

  private int maxWindow = 100;
  /**
   * Set up the environment for performing the tests in this Class.
   * Replication
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    // This test suite depends on having the schema available.
    configure();
  }

  /**
   * Utility : configure a replicationServer.
   */
  protected void configure() throws Exception
  {
    //  Find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replicationServerPort = socket.getLocalPort();
    socket.close();

    ReplServerFakeConfiguration conf1 =
      new ReplServerFakeConfiguration(
          replicationServerPort, "ExternalChangeLogTestDb",
          0, 71, 0, maxWindow, null);

    replicationServer = new ReplicationServer(conf1);
    debugInfo("configure", "ReplicationServer created"+replicationServer);

  }

  /**
   * Launcher.
   */
  @Test(enabled=true)
  public void ECLReplicationServerPreTest() throws Exception
  {
    // No RSDomain created yet => RS only case => ECL is not a supported
    ECLIsNotASupportedSuffix();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerPreTest"})
  public void ECLReplicationServerTest() throws Exception
  {
    // Following test does not create RSDomain (only broker) but want to test
    // ECL .. so let's enable ECl manually
    // Now that we tested that ECl is not available
    try
    {
      ECLWorkflowElement wfe = (ECLWorkflowElement)
      DirectoryServer.getWorkflowElement(
          ECLWorkflowElement.ECL_WORKFLOW_ELEMENT);
      if (wfe!=null)
        wfe.getReplicationServer().enableECL();
    }
    catch(DirectoryException de)
    {
      fail("Ending test " +  " with exception:"
          +  stackTraceToSingleLineString(de));
    }

    // Test all types of ops.
    ECLAllOps(); // Do not clean the db for the next test

    // First and last should be ok whenever a request has been done or not
    // in compat mode.
    ECLCompatTestLimits(1,4,true);
    replicationServer.clearDb();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest1()
  {

    // Test with a mix of domains, a mix of DSes
    ECLTwoDomains();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest2()
  {

    // Test ECL after changelog triming
    ECLAfterChangelogTrim();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest3()
  {
    // Write changes and read ECL from start
    int ts = ECLCompatWriteReadAllOps(1);

    ECLCompatNoControl(1);

    // Write additional changes and read ECL from a provided draft change number
    ts = ECLCompatWriteReadAllOps(5);
    replicationServer.clearDb();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest4()
  {
    ECLIncludeAttributes();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest5()
  {

    ChangeTimeHeartbeatTest();

  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerTest6()
  {
    // Test that ECL Operational, virtual attributes are not visible
    // outside rootDSE. Next test will test access in RootDSE.
    // This one checks in data.
    ECLOperationalAttributesFailTest();
  }

  @Test(enabled=true, dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest()
  {
    // ***********************************************
    // First set of test are in the cookie mode
    // ***********************************************

    // Test that private backend is excluded from ECL
    ECLOnPrivateBackend();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest1()
  {
    // Test remote API (ECL through replication protocol) with empty ECL
    ECLRemoteEmpty();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest2()
  {
    // Test with empty changelog
    ECLEmpty();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest3()
  {
    // Test all types of ops.
    ECLAllOps(); // Do not clean the db for the next test

    // Test that ECL Operational, virtual attributes are not visible
    // outside rootDSE. Next test will test access in RootDSE.
    // This one checks in data.
    ECLOperationalAttributesFailTest();

    // First and last should be ok whenever a request has been done or not
    // in compat mode.
    ECLCompatTestLimits(1,4, true);
    replicationServer.clearDb();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest4()
  {
    // Test remote API (ECL through replication protocol) with NON empty ECL
    ECLRemoteNonEmpty();
  }


  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest7()
  {
    // Persistent search with changesOnly request
    ECLPsearch(true, false);
    replicationServer.clearDb();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest8()
  {
    // Persistent search with init values request
    ECLPsearch(false, false);
    replicationServer.clearDb();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest9()
  {
    // Simultaneous psearches
    ECLSimultaneousPsearches();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest10()
  {
    // Test eligible count method.
    ECLGetEligibleCountTest();
    replicationServer.clearDb();
  }

  // TODO:ECL Test SEARCH abandon and check everything shutdown and cleaned
  // TODO:ECL Test PSEARCH abandon and check everything shutdown and cleaned
  // TODO:ECL Test invalid DN in cookie returns UNWILLING + message
  // TODO:ECL Test the attributes list and values returned in ECL entries
  // TODO:ECL Test search -s base, -s one

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest11()
  {
    // Test directly from the java obect that the changeTimeHeartbeatState
    // stored are ok.
    ChangeTimeHeartbeatTest();

  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest12()
  {
    // Test the different forms of filter that are parsed in order to
    // optimize the request.
    ECLFilterTest();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest13()
  {
    // ***********************************************
    // Second set of test are in the draft compat mode
    // ***********************************************
    // Empty replication changelog
    ECLCompatEmpty();

  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest14()
  {
    // Request from an invalid draft change number
    ECLCompatBadSeqnum();

  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest15()
  {
    // Write 4 changes and read ECL from start
    int ts = ECLCompatWriteReadAllOps(1);

    // Write 4 additional changes and read ECL from a provided draft change number
    ts = ECLCompatWriteReadAllOps(5);

    // Test request from a provided change number - read 6
    ECLCompatReadFrom(6);

    // Test request from a provided change number interval - read 5-7
    ECLCompatReadFromTo(5,7);

    // Test first and last draft changenumber
    ECLCompatTestLimits(1,8, true);

    // Test first and last draft changenumber, a dd a new change, do not
    // search again the ECL, but search for first and last
    ECLCompatTestLimitsAndAdd(1,8, ts);

    // Test DraftCNDb is purged when replication change log is purged
    ECLPurgeDraftCNDbAfterChangelogClear();

    // Test first and last are updated
    ECLCompatTestLimits(0,0, true);

    // Persistent search in changesOnly mode
    ECLPsearch(true, true);
    replicationServer.clearDb();
  }

  @Test(enabled=true, groups="slow", dependsOnMethods = { "ECLReplicationServerTest"})
  public void ECLReplicationServerFullTest16()
  {
    // Persistent search in init + changes mode
    ECLPsearch(false, true);

    // Test Filter on replication csn
    // TODO: test with optimization when code done.
    ECLFilterOnReplicationCsn();
    replicationServer.clearDb();
  }


  private void ECLIsNotASupportedSuffix()
  {
    ECLCompatTestLimits(0,0, false);
  }

  //=======================================================
  // Objectives
  //   - Test that everything id ok with no changes
  // Procedure
  //   - Does a SEARCH from 3 different remote ECL session,
  //   - Verify  DoneMsg is received on each session.
  private void ECLRemoteEmpty()
  {
    String tn = "ECLRemoteEmpty";
    debugInfo(tn, "Starting test\n\n");

    ReplicationBroker server1 = null;
    ReplicationBroker server2 = null;
    ReplicationBroker server3 = null;

    try
    {
      // Create 3 ECL broker
      server1 = openReplicationSession(
          DN.decode("cn=changelog"), 1111,
          100, replicationServerPort, brokerSessionTimeout, false);
      assertTrue(server1.isConnected());
      server2 = openReplicationSession(
          DN.decode("cn=changelog"), 2222,
          100, replicationServerPort,brokerSessionTimeout, false);
      assertTrue(server2.isConnected());
      server3 = openReplicationSession(
          DN.decode("cn=changelog"), 3333,
          100, replicationServerPort,brokerSessionTimeout, false);
      assertTrue(server3.isConnected());

      // Test broker1 receives only Done
      ReplicationMsg msg;
      int msgc=0;
      do
      {
        msg = server1.receive();
        msgc++;
      }
      while(!(msg instanceof DoneMsg));
      assertTrue(msgc==1,
          "Ending " + tn + " with incorrect message number :" +
          msg.getClass().getCanonicalName());
      assertTrue(msg instanceof DoneMsg,
      "Ending " + tn + " with incorrect message type :" +
      msg.getClass().getCanonicalName());

      // Test broker2 receives only Done
      msgc=0;
      do
      {
        msg = server2.receive();
        msgc++;
      }
      while(!(msg instanceof DoneMsg));
      assertTrue(msgc==1,
          "Ending " + tn + " with incorrect message number :" +
          msg.getClass().getCanonicalName());
      assertTrue(msg instanceof DoneMsg,
      "Ending " + tn + " with incorrect message type :" +
      msg.getClass().getCanonicalName());

      // Test broker3 receives only Done
      msgc=0;
      do
      {
        msg = server3.receive();
        msgc++;
      }
      while(!(msg instanceof DoneMsg));
      assertTrue(msgc==1,
          "Ending " + tn + " with incorrect message number :" +
          msg.getClass().getCanonicalName());
      assertTrue(msg instanceof DoneMsg,
      "Ending " + tn + " with incorrect message type :" +
      msg.getClass().getCanonicalName());
      debugInfo(tn, "Ending test successfully\n\n");
    }
    catch(Exception e)
    {
      fail("Ending test " + tn +  " with exception:"
          +  stackTraceToSingleLineString(e));
    }
    finally
    {
      if (server1 != null)
        server1.stop();
      if (server2 != null)
        server2.stop();
      if (server3 != null)
        server3.stop();
      replicationServer.clearDb();
    }
  }

  //=======================================================
  // Objectives
  //   - Test that everything id ok with changes on 2 suffixes
  // Procedure
  //   - From 1 remote ECL session,
  //   - Test simple update to be received from 2 suffixes
  private void ECLRemoteNonEmpty()
  {
    String tn = "ECLRemoteNonEmpty";
    debugInfo(tn, "Starting test\n\n");

    replicationServer.clearDb();

    // create a broker
    ReplicationBroker server01 = null;
    ReplicationBroker server02 = null;
    ReplicationBroker serverECL = null;

    try
    {
      // create 2 regular brokers on the 2 suffixes
      server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);

      server02 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2),  1202,
          100, replicationServerPort,
          brokerSessionTimeout, true, EMPTY_DN_GENID);

      // create and publish 1 change on each suffix
      long time = TimeThread.getTime();
      int ts = 1;
      ChangeNumber cn1 = new ChangeNumber(time, ts++, 1201);
      DeleteMsg delMsg1 =
        new DeleteMsg("o=" + tn + "1," + TEST_ROOT_DN_STRING, cn1, "ECLBasicMsg1uid");
      server01.publish(delMsg1);
      debugInfo(tn, "publishes:" + delMsg1);

      ChangeNumber cn2 = new ChangeNumber(time, ts++, 1202);
      DeleteMsg delMsg2 =
        new DeleteMsg("o=" + tn + "2," + TEST_ROOT_DN_STRING2, cn2, "ECLBasicMsg2uid");
      server02.publish(delMsg2);
      debugInfo(tn, "publishes:" + delMsg2);

      // wait for the server to take these changes into account
      sleep(500);

      // open ECL broker
      serverECL = openReplicationSession(
          DN.decode("cn=changelog"), 10,
          100, replicationServerPort, brokerSessionTimeout, false);
      assertTrue(serverECL.isConnected());

      // receive change 1 from suffix 1
      ReplicationMsg msg;
      msg = serverECL.receive();
      ECLUpdateMsg eclu = (ECLUpdateMsg)msg;
      UpdateMsg u = eclu.getUpdateMsg();
      debugInfo(tn, "RESULT:" + u.getChangeNumber() + " " + eclu.getCookie());
      assertTrue(u.getChangeNumber().equals(cn1), "RESULT:" + u.getChangeNumber());
      assertTrue(eclu.getCookie().equalsTo(new MultiDomainServerState(
          "o=test:"+delMsg1.getChangeNumber()+";o=test2:;")));

      // receive change 2 from suffix 2
      msg = serverECL.receive();
      eclu = (ECLUpdateMsg)msg;
      u = eclu.getUpdateMsg();
      debugInfo(tn, "RESULT:" + u.getChangeNumber());
      assertTrue(u.getChangeNumber().equals(cn2), "RESULT:" + u.getChangeNumber());
      assertTrue(eclu.getCookie().equalsTo(new MultiDomainServerState(
          "o=test2:"+delMsg2.getChangeNumber()+";"+
          "o=test:"+delMsg1.getChangeNumber()+";")));

      // receive Done
      msg = serverECL.receive();
      debugInfo(tn, "RESULT:" + msg);
      assertTrue(msg instanceof DoneMsg, "RESULT:" + msg);

      debugInfo(tn, "Ending test successfully");
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception:"
          +  stackTraceToSingleLineString(e));
    }
    finally
    {
       // clean
      if (serverECL != null)
        serverECL.stop();
      if (server01 != null)
        server01.stop();
      if (server02 != null)
        server02.stop();
      replicationServer.clearDb();
    }
  }

  /**
   * From embedded ECL (no remote session)
   * With empty RS, simple search should return only root entry.
   */
  private void ECLEmpty()
  {
    String tn = "ECLEmpty";
    debugInfo(tn, "Starting test\n\n");

    replicationServer.clearDb();

    try
    {
      // search on 'cn=changelog'
      InternalSearchOperation op2 = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          0, 0,
          false,
          LDAPFilter.decode("(objectclass=*)"),
          new LinkedHashSet<String>(0),
          createControls(""),
          null);

      // success
      waitOpResult(op2, ResultCode.SUCCESS);

      // root entry returned
      assertEquals(op2.getEntriesSent(), 1);
      debugInfo(tn, "Ending test successfully");
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception e="
          +  stackTraceToSingleLineString(e));
    }
  }

  /**
   * Build a list of controls including the cookie provided.
   * @param cookie The provided cookie.
   * @return The built list of controls.
   */
  private ArrayList<Control> createControls(String cookie)
          throws DirectoryException
  {
    ExternalChangelogRequestControl control =
      new ExternalChangelogRequestControl(true,
          new MultiDomainServerState(cookie));
    ArrayList<Control> controls = new ArrayList<Control>(1);
    controls.add(control);
    return controls;
  }

  /**
   * Utility - creates an LDIFWriter to dump result entries.
   */
  private static LDIFWriter getLDIFWriter()
  {
    LDIFWriter ldifWriter = null;
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    LDIFExportConfig exportConfig = new LDIFExportConfig(stream);
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      assert(e==null);
    }
    return ldifWriter;
  }

  // Add an entry in the database
  private void addEntry(Entry entry) throws Exception
  {
    AddOperationBasis addOp = new AddOperationBasis(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
        .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
        entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();
    waitOpResult(addOp, ResultCode.SUCCESS);
    assertNotNull(getEntry(entry.getDN(), 1000, true));
  }

  private void ECLOnPrivateBackend()
  {
    String tn = "ECLOnPrivateBackend";
    debugInfo(tn, "Starting test");

    replicationServer.clearDb();

    ReplicationBroker server01 = null;
    LDAPReplicationDomain domain2 = null;
    Backend backend2 = null;
    DN baseDn2 = null;
    try
    {
      baseDn2 = DN.decode(TEST_ROOT_DN_STRING2);

      server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);

      // create and publish 1 change on each suffix
      long time = TimeThread.getTime();
      int ts = 1;
      ChangeNumber cn1 = new ChangeNumber(time, ts++, 1201);
      DeleteMsg delMsg1 =
        new DeleteMsg("o=" + tn + "1," + TEST_ROOT_DN_STRING, cn1, "ECLBasicMsg1uid");
      server01.publish(delMsg1);
      debugInfo(tn, "publishes:" + delMsg1);

      // Initialize a second test backend o=test2, in addtion to o=test
      // Configure replication on this backend
      // Add the root entry in the backend
      backend2 = initializeTestBackend(false, TEST_ROOT_DN_STRING2,
          TEST_BACKEND_ID2);
      backend2.setPrivateBackend(true);
      SortedSet<String> replServers = new TreeSet<String>();
      replServers.add("localhost:"+replicationServerPort);

      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn2,  1602, replServers);
      ExternalChangelogDomainFakeCfg eclCfg =
        new ExternalChangelogDomainFakeCfg(true, null, null);
      domainConf.setExternalChangelogDomain(eclCfg);

      domain2 = MultimasterReplication.createNewDomain(domainConf);
      domain2.start();

      sleep(1000);
      Entry e = createEntry(baseDn2);
      addEntry(e);

      // Search on ECL from start on all suffixes
      String cookie = "";
      ExternalChangelogRequestControl control =
        new ExternalChangelogRequestControl(true,
            new MultiDomainServerState(cookie));
      ArrayList<Control> controls = new ArrayList<Control>(0);
      controls.add(control);
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      debugInfo(tn, "Search with cookie=" + cookie);
      sleep(2000);
      InternalSearchOperation searchOp = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*)"),
          attributes,
          controls,
          null);

      // Expect SUCCESS and root entry returned
      waitOpResult(searchOp, ResultCode.SUCCESS);

      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertEquals(entries.size(),2, "Entries number returned by search");
      assertTrue(entries != null);
      if (entries != null)
      {
        int i = 0;
        for (SearchResultEntry resultEntry : entries)
        {
          i++;
          // Expect
          debugInfo(tn, "Entry returned when test2 is public =" +
              resultEntry.toLDIFString());

          // Test entry attributes
          //if (i==2)
          //{
          //  checkPossibleValues(resultEntry,"targetobjectclass","top","organization");
          //}
        }
      }

      eclCfg =
        new ExternalChangelogDomainFakeCfg(false, null, null);
      domainConf.setExternalChangelogDomain(eclCfg);
      domain2.applyConfigurationChange(domainConf);

      debugInfo(tn, "Search with cookie=" + cookie);
      searchOp = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*)"),
          attributes,
          controls,
          null);

      // Expect success and only entry from o=test returned
      waitOpResult(searchOp, ResultCode.SUCCESS);

      entries = searchOp.getSearchEntries();
      assertTrue(entries != null, "Entries returned when test2 is ECL disabled.");
      assertTrue(entries.size()==1, "#Entry="+entries.size()+"when expected is 1");
      if (entries != null)
        for (SearchResultEntry resultEntry : entries)
        {
          // Expect
          debugInfo(tn, "Entry returned when test2 is private ="
              + resultEntry.toLDIFString());
        }

      //
      // Test lastExternalChangelogCookie attribute of the ECL
      // (does only refer to non private backend)
      MultiDomainServerState expectedLastCookie =
        new MultiDomainServerState("o=test:"+cn1+";");

      String lastCookie = readLastCookie(tn);

      assertTrue(expectedLastCookie.equalsTo(new MultiDomainServerState(lastCookie)),
          " Expected last cookie attribute value:" + expectedLastCookie +
          " Read from server: " + lastCookie + " are equal :");

    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception:"
          +  stackTraceToSingleLineString(e));
    }
    finally
    {
      // Cleaning
      if (domain2 != null && baseDn2 != null)
        MultimasterReplication.deleteDomain(baseDn2);
      if (backend2 != null)
      removeTestBackend2(backend2);

      if (server01 != null)
        server01.stop();
      replicationServer.clearDb();
    }
    debugInfo(tn, "Ending test successfully");
  }

  /**
   * From embebbded ECL
   * Search ECL with 4 messages on 2 suffixes from 2 brokers
   *
   */
  private void ECLTwoDomains()
  {
    String tn = "ECLTwoDomains";
    debugInfo(tn, "Starting test");

    ReplicationBroker s1test = null;
    ReplicationBroker s1test2 = null;
    ReplicationBroker s2test = null;
    ReplicationBroker s2test2 = null;

    try
    {
      // Initialize a second test backend
      Backend backend2 = initializeTestBackend(true,
          TEST_ROOT_DN_STRING2, TEST_BACKEND_ID2);

      //
      LDIFWriter ldifWriter = getLDIFWriter();

      // --
      s1test = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);

      s2test2 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2),  1202,
          100, replicationServerPort,
          brokerSessionTimeout, true, EMPTY_DN_GENID);
      sleep(500);

      // Produce updates
      long time = TimeThread.getTime();
      int ts = 1;
      ChangeNumber cn = new ChangeNumber(time, ts++, s1test.getServerId());
      DeleteMsg delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn, tn+"uuid1");
      s1test.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      cn = new ChangeNumber(time++, ts++, s2test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"2," + TEST_ROOT_DN_STRING2, cn, tn+"uuid2");
      s2test2.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      ChangeNumber cn3 = new ChangeNumber(time++, ts++, s2test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"3," + TEST_ROOT_DN_STRING2, cn3, tn+"uuid3");
      s2test2.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      cn = new ChangeNumber(time++, ts++, s1test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"4," + TEST_ROOT_DN_STRING, cn, tn+"uuid4");
      s1test.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());
      sleep(1500);

      // Changes are :
      //               s1          s2
      // o=test       msg1/msg4
      // o=test2                 msg2/msg2
      String cookie= "";

      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      debugInfo(tn, "Search with cookie=" + cookie + "\"");
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode("(targetDN=*"+tn+"*)"),
            attributes,
            createControls(cookie),
            null);

      waitOpResult(searchOp, ResultCode.SUCCESS);

      cookie="";
      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      if (entries != null)
      {
        int i=0;
        for (SearchResultEntry entry : entries)
        {
          debugInfo(tn, " RESULT entry returned:" + entry.toSingleLineString());
          ldifWriter.writeEntry(entry);
          if (i++==2)
          {
            // Store the cookie returned with the 3rd ECL entry returned to use
            // it in the test below.
            cookie =
              entry.getAttribute("changelogcookie").get(0).iterator().next().toString();
          }
        }
      }
      assertEquals(searchOp.getSearchEntries().size(), 4);

      // Now start from last cookie and expect to get ONLY the 4th change
      attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      ExternalChangelogRequestControl control =
        new ExternalChangelogRequestControl(true,
            new MultiDomainServerState(cookie));
      ArrayList<Control> controls = new ArrayList<Control>(0);
      controls.add(control);

      debugInfo(tn, "Search with cookie=" + cookie);
      searchOp = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*"+tn+"*)"),
          attributes,
          controls,
          null);

      // We expect SUCCESS and the 4th change
      waitOpResult(searchOp, ResultCode.SUCCESS);
      entries = searchOp.getSearchEntries();
      cookie="";
      if (entries != null)
      {
        for (SearchResultEntry entry : entries)
        {
          debugInfo(tn, "Result entry=\n" + entry.toLDIFString());
          ldifWriter.writeEntry(entry);
          try
          {
            // Store the cookie returned with the 4rd ECL entry returned to use
            // it in the test below.
            cookie =
              entry.getAttribute("changelogcookie").get(0).iterator().next().toString();
          }
          catch(NullPointerException e)
          {}
        }
      }
      assertEquals(searchOp.getSearchEntries().size(), 1);

      // Now publishes a new change and search from the previous cookie
      ChangeNumber cn5 = new ChangeNumber(time++, ts++, s1test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"5," + TEST_ROOT_DN_STRING, cn5, tn+"uuid5");
      s1test.publish(delMsg);
      sleep(500);

      // Changes are :
      //               s1         s2
      // o=test       msg1,msg5   msg4
      // o=test2      msg3        msg2

      control =
        new ExternalChangelogRequestControl(true,
            new MultiDomainServerState(cookie));
      controls = new ArrayList<Control>(0);
      controls.add(control);

      debugInfo(tn, "Search with cookie=" + cookie + "\"");
      searchOp = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*"+tn+"*)"),
          attributes,
          controls,
          null);

      waitOpResult(searchOp, ResultCode.SUCCESS);
      entries = searchOp.getSearchEntries();
      if (entries != null)
      {
        for (SearchResultEntry resultEntry : entries)
        {
          debugInfo(tn, "Result entry=\n" + resultEntry.toLDIFString());
          ldifWriter.writeEntry(resultEntry);
          try
          {
            cookie =
              resultEntry.getAttribute("changelogcookie").get(0).iterator().next().toString();
          }
          catch(NullPointerException e)
          {}
        }
      }
      assertEquals(searchOp.getSearchEntries().size(), 1);

      cookie="";

      control =
        new ExternalChangelogRequestControl(true,
            new MultiDomainServerState(cookie));
      controls = new ArrayList<Control>(0);
      controls.add(control);

      debugInfo(tn, "Search with cookie=" + cookie + "\" and filter on domain=" +
          "(targetDN=*direct*,o=test)");
      searchOp = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*"+tn+"*,o=test)"),
          attributes,
          controls,
          null);


      waitOpResult(searchOp, ResultCode.SUCCESS);

      entries = searchOp.getSearchEntries();
      if (entries != null)
      {
        for (SearchResultEntry resultEntry : entries)
        {
          debugInfo(tn, "Result entry=\n" + resultEntry.toLDIFString());
          ldifWriter.writeEntry(resultEntry);
          try
          {
            cookie =
              resultEntry.getAttribute("changelogcookie").get(0).iterator().next().toString();
          }
          catch(NullPointerException e)
          {}
        }
      }
      // we expect msg1 + msg4 + msg5
      assertEquals(searchOp.getSearchEntries().size(), 3);

      //
      // Test startState ("first cookie") of the ECL
      //
      // --
      s1test2 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2),  1203,
          100, replicationServerPort,
          brokerSessionTimeout, true, EMPTY_DN_GENID);

      s2test = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1204,
          100, replicationServerPort,
          brokerSessionTimeout, true);
      sleep(500);

      time = TimeThread.getTime();
      cn = new ChangeNumber(time++, ts++, s1test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"6," + TEST_ROOT_DN_STRING2, cn, tn+"uuid6");
      s1test2.publish(delMsg);

      cn = new ChangeNumber(time++, ts++, s2test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"7," + TEST_ROOT_DN_STRING, cn, tn+"uuid7");
      s2test.publish(delMsg);

      ChangeNumber cn8 = new ChangeNumber(time++, ts++, s1test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"8," + TEST_ROOT_DN_STRING2, cn8, tn+"uuid8");
      s1test2.publish(delMsg);

      ChangeNumber cn9 = new ChangeNumber(time++, ts++, s2test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"9," + TEST_ROOT_DN_STRING, cn9, tn+"uuid9");
      s2test.publish(delMsg);
      sleep(500);

      ReplicationServerDomain rsd =
        replicationServer.getReplicationServerDomain(TEST_ROOT_DN_STRING, false);
      ServerState startState = rsd.getStartState();
      assertTrue(startState.getMaxChangeNumber(s1test.getServerId()).getSeqnum()==1);
      assertTrue(startState.getMaxChangeNumber(s2test.getServerId()) != null);
      assertTrue(startState.getMaxChangeNumber(s2test.getServerId()).getSeqnum()==7);

      rsd =
        replicationServer.getReplicationServerDomain(TEST_ROOT_DN_STRING2, false);
      startState = rsd.getStartState();
      assertTrue(startState.getMaxChangeNumber(s2test2.getServerId()).getSeqnum()==2);
      assertTrue(startState.getMaxChangeNumber(s1test2.getServerId()).getSeqnum()==6);

      //
      // Test lastExternalChangelogCookie attribute of the ECL
      //
      MultiDomainServerState expectedLastCookie =
        new MultiDomainServerState("o=test:"+cn5+" "+cn9+";o=test2:"+cn3+" "+cn8+";");

      String lastCookie = readLastCookie(tn);

      assertTrue(expectedLastCookie.equalsTo(new MultiDomainServerState(lastCookie)),
          " Expected last cookie attribute value:" + expectedLastCookie +
          " Read from server: " + lastCookie + " are equal :");

      // Test invalid cookie
      cookie += ";o=test6:";

      control =
        new ExternalChangelogRequestControl(true,
            new MultiDomainServerState(cookie));
      controls = new ArrayList<Control>(0);
      controls.add(control);
      debugInfo(tn, "Search with bad domain in cookie=" + cookie);
      searchOp = connection.processSearch(
      ByteString.valueOf("cn=changelog"),
      SearchScope.WHOLE_SUBTREE,
      DereferencePolicy.NEVER_DEREF_ALIASES,
      0, // Size limit
      0, // Time limit
      false, // Types only
      LDAPFilter.decode("(targetDN=*"+tn+"*,o=test)"),
      attributes,
      controls,
      null);

      waitOpResult(searchOp, ResultCode.PROTOCOL_ERROR);
      assertEquals(searchOp.getSearchEntries().size(), 0);
      assertTrue(searchOp.getErrorMessage().toString().equals(
          ERR_INVALID_COOKIE_SYNTAX.get().toString()),
          searchOp.getErrorMessage().toString());

      // Test unknown domain in provided cookie
      // This case seems to be very hard to obtain in the real life
      // (how to remove a domain from a RS topology ?)
      // let's do a very quick test here.
      String newCookie = lastCookie + "o=test6:";

      control =
        new ExternalChangelogRequestControl(true,
            new MultiDomainServerState(newCookie));
      controls = new ArrayList<Control>(0);
      controls.add(control);
      debugInfo(tn, "Search with bad domain in cookie=" + cookie);
      searchOp = connection.processSearch(
      ByteString.valueOf("cn=changelog"),
      SearchScope.WHOLE_SUBTREE,
      DereferencePolicy.NEVER_DEREF_ALIASES,
      0, // Size limit
      0, // Time limit
      false, // Types only
      LDAPFilter.decode("(targetDN=*"+tn+"*,o=test)"),
      attributes,
      controls,
      null);

      waitOpResult(searchOp, ResultCode.UNWILLING_TO_PERFORM);
      assertEquals(searchOp.getSearchEntries().size(), 0);

      // Test missing domain in provided cookie
      newCookie = lastCookie.substring(lastCookie.indexOf(';')+1);
      control =
        new ExternalChangelogRequestControl(true,
            new MultiDomainServerState(newCookie));
      controls = new ArrayList<Control>(0);
      controls.add(control);
      debugInfo(tn, "Search with bad domain in cookie=" + cookie);
      searchOp = connection.processSearch(
      ByteString.valueOf("cn=changelog"),
      SearchScope.WHOLE_SUBTREE,
      DereferencePolicy.NEVER_DEREF_ALIASES,
      0, // Size limit
      0, // Time limit
      false, // Types only
      LDAPFilter.decode("(targetDN=*"+tn+"*,o=test)"),
      attributes,
      controls,
      null);

      waitOpResult(searchOp, ResultCode.UNWILLING_TO_PERFORM);
      assertEquals(searchOp.getSearchEntries().size(), 0);
      String expectedError = ERR_RESYNC_REQUIRED_MISSING_DOMAIN_IN_PROVIDED_COOKIE
          .get("o=test:;","<"+ newCookie + "o=test:;>").toString();
      assertTrue(searchOp.getErrorMessage().toString().equalsIgnoreCase(expectedError),
          "Expected: " + expectedError + "Server output:" +
          searchOp.getErrorMessage().toString());
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + "with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    finally
    {
      if (s1test != null)
        s1test.stop();
      if (s1test2 != null)
        s1test2.stop();
      if (s2test != null)
        s2test.stop();
      if (s2test2 != null)
        s2test2.stop();
      replicationServer.clearDb();
    }
    debugInfo(tn, "Ending test successfully");
  }

  //=======================================================
  // Test ECL content after replication changelogdb triming
  private void ECLAfterChangelogTrim()
  {
    String tn = "ECLAfterChangelogTrim";
    debugInfo(tn, "Starting test");

    ReplicationBroker server01 = null;
    ReplicationServerDomain d1 = null;
    ReplicationServerDomain d2 = null;

    try
    {
      // ---
      // 1. Populate the changelog and read the cookie

      // Creates broker on o=test
      server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);
      int ts = 1;


      ChangeNumber cn1 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      DeleteMsg delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn1, tn+"uuid1");
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      Thread.sleep(1000);

      // Test that last cookie has been updated
      String cookieNotEmpty = readLastCookie(tn);
      debugInfo(tn, "Store cookie not empty=\"" + cookieNotEmpty + "\"");

      cn1 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      delMsg =
        new DeleteMsg("uid="+tn+"2," + TEST_ROOT_DN_STRING, cn1, tn+"uuid2");
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      cn1 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      delMsg =
        new DeleteMsg("uid="+tn+"3," + TEST_ROOT_DN_STRING, cn1, tn+"uuid3");
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      // Sleep longer than this delay - the changelog will be trimmed
      Thread.sleep(1000);

      // ---
      // 2. Now set up a very short purge delay on the replication changelogs
      // so that this test can play with a trimmed changelog.
      d1 = replicationServer.getReplicationServerDomain("o=test", false);
      d2 = replicationServer.getReplicationServerDomain("o=test2", false);
      d1.setPurgeDelay(1);
      d2.setPurgeDelay(1);

      // Sleep longer than this delay - so that the changelog is trimmed
      Thread.sleep(1000);
      //
      LDIFWriter ldifWriter = getLDIFWriter();

      // ---
      // 3. Assert that a request with an empty cookie returns nothing
      String cookie= "";

      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      debugInfo(tn, "1. Search with cookie=" + cookie + "\"");
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode("(targetDN=*)"),
            attributes,
            createControls(cookie),
            null);

      waitOpResult(searchOp, ResultCode.SUCCESS);

      cookie="";
      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      if (entries != null)
      {
        for (SearchResultEntry entry : entries)
        {
          debugInfo(tn, " RESULT entry returned:" + entry.toSingleLineString());
          ldifWriter.writeEntry(entry);
        }
      }

      // Assert ECL is empty since replication changelog has been trimmed
      assertEquals(searchOp.getSearchEntries().size(), 0);

      // 4. Assert that a request with the current last cookie returns nothing
      cookie = readLastCookie(tn);
      debugInfo(tn, "2. Search with last cookie=" + cookie + "\"");
      searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode("(targetDN=*)"),
            attributes,
            createControls(cookie),
            null);

      waitOpResult(searchOp, ResultCode.SUCCESS);
      entries = searchOp.getSearchEntries();
      if (entries != null)
      {
        for (SearchResultEntry entry : entries)
        {
          debugInfo(tn, " RESULT entry returned:" + entry.toSingleLineString());
          ldifWriter.writeEntry(entry);
        }
      }

      // Assert ECL is empty since replication changelog has been trimmed
      assertEquals(searchOp.getSearchEntries().size(), 0);

      // ---
      // 5. Assert that a request with an "old" cookie - one that refers to
      //    changes that have been removed by the replication changelog trimming
      //    returns the appropriate error.

      cn1 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn1, tn+"uuid1");
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      debugInfo(tn, "3. Search with cookie=\"" + cookieNotEmpty + "\"");
      debugInfo(tn, "d1 trimdate" + d1.getStartState());
      debugInfo(tn, "d2 trimdate" + d2.getStartState());
      searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode("(targetDN=*)"),
            attributes,
            createControls(cookieNotEmpty),
            null);

      waitOpResult(searchOp, ResultCode.UNWILLING_TO_PERFORM);
      assertEquals(searchOp.getSearchEntries().size(), 0);
      assertTrue(searchOp.getErrorMessage().toString().startsWith(
          ERR_RESYNC_REQUIRED_TOO_OLD_DOMAIN_IN_PROVIDED_COOKIE.get("o=test")
	  .toString()),
          searchOp.getErrorMessage().toString());

    }
    catch(Exception e)
    {
      fail("Ending test " + tn + "with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    finally
    {
      if (server01 != null)
        server01.stop();
      // And reset changelog purge delay for the other tests.
      if (d1 != null)
        d1.setPurgeDelay(15 * 1000);
      if (d2 != null)
        d2.setPurgeDelay(15 * 1000);

      replicationServer.clearDb();
    }
    debugInfo(tn, "Ending test successfully");
  }


  private String readLastCookie(String tn)
  {
    String cookie = "";
    LDIFWriter ldifWriter = getLDIFWriter();

    //
    LinkedHashSet<String> lastcookieattribute = new LinkedHashSet<String>();
    lastcookieattribute.add("lastExternalChangelogCookie");

    try
    {
      // Root DSE
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf(""),
            SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode("(objectclass=*)"),
            lastcookieattribute,
            NO_CONTROL,
            null);

      waitOpResult(searchOp, ResultCode.SUCCESS);
      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      if (entries != null)
      {
        for (SearchResultEntry resultEntry : entries)
        {
          ldifWriter.writeEntry(resultEntry);
          try
          {
            List<Attribute> l = resultEntry.getAttribute("lastexternalchangelogcookie");
            cookie = l.get(0).iterator().next().toString();
          }
          catch(NullPointerException e)
          {}
        }

      }
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    return cookie;
  }

  // simple update to be received
  private void ECLAllOps()
  {
    String tn = "ECLAllOps";
    debugInfo(tn, "Starting test\n\n");

    try
    {
      LDIFWriter ldifWriter = getLDIFWriter();

      // Creates broker on o=test
      ReplicationBroker server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);
      int ts = 1;

      // Creates broker on o=test2
      ReplicationBroker server02 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2),  1202,
          100, replicationServerPort,
          brokerSessionTimeout, true);

      String user1entryUUID = "11111111-1111-1111-1111-111111111111";
      String baseUUID       = "22222222-2222-2222-2222-222222222222";


      // Publish DEL
      ChangeNumber cn1 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      DeleteMsg delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn1, tn+"uuid1");
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      // Publish ADD
      ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      String lentry = new String("dn: uid="+tn+"2," + TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n" + "objectClass: domain\n"
          + "entryUUID: "+user1entryUUID+"\n");
      Entry entry = TestCaseUtils.entryFromLdifString(lentry);
      AddMsg addMsg = new AddMsg(
          cn2,
          "uid="+tn+"2," + TEST_ROOT_DN_STRING,
          user1entryUUID,
          baseUUID,
          entry.getObjectClassAttribute(),
          entry.getAttributes(),
          new ArrayList<Attribute>());
      server01.publish(addMsg);
      debugInfo(tn, " publishes " + addMsg.getChangeNumber());

      // Publish DEL
      /*
      ChangeNumber cn12 = new ChangeNumber(TimeThread.getTime(), ts++, 1202);
      DeleteMsg delMsg2 =
        new DeleteMsg("uid="+tn+"12," + TEST_ROOT_DN_STRING2, cn12, tn+"uuid12");
      server02.publish(delMsg2);
      debugInfo(tn, " publishes " + delMsg2.getChangeNumber());
      */

      // Publish MOD
      ChangeNumber cn3 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      Attribute attr1 = Attributes.create("description", "new value");
      Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
      List<Modification> mods = new ArrayList<Modification>();
      mods.add(mod1);
      ModifyMsg modMsg = new ModifyMsg(cn3, DN
          .decode("uid="+tn+"3," + TEST_ROOT_DN_STRING), mods, tn+"uuid3");
      server01.publish(modMsg);
      debugInfo(tn, " publishes " + modMsg.getChangeNumber());

      // Publish modDN
      DN newSuperior = DN.decode(TEST_ROOT_DN_STRING2);
      ChangeNumber cn4 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      ModifyDNOperationBasis op = new ModifyDNOperationBasis(connection, 1, 1, null,
          DN.decode("uid="+tn+"4," + TEST_ROOT_DN_STRING), // entryDN
          RDN.decode("uid="+tn+"new4"), // new rdn
          true,  // deleteoldrdn
          newSuperior);
      op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(cn4, tn+"uuid4",
      "newparentId"));
      LocalBackendModifyDNOperation localOp = new LocalBackendModifyDNOperation(op);
      ModifyDNMsg modDNMsg = new ModifyDNMsg(localOp);
      server01.publish(modDNMsg);
      debugInfo(tn, " publishes " + modDNMsg.getChangeNumber());
      sleep(1000);

      String cookie= "";

      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      ExternalChangelogRequestControl control =
        new ExternalChangelogRequestControl(true,
            new MultiDomainServerState());
      ArrayList<Control> controls = new ArrayList<Control>(0);
      controls.add(control);

      debugInfo(tn, "Search with cookie=" + cookie + "\" filter=" +
          "(targetdn=*"+tn+"*,o=test)");
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode("(targetdn=*"+tn+"*,o=test)"),
            attributes,
            controls,
            null);

      // test success
      waitOpResult(searchOp, ResultCode.SUCCESS);
      // test 4 entries returned
      String cookie1 = "o=test:"+cn1.toString()+";";
      String cookie2 = "o=test:"+cn2.toString()+";";
      String cookie3 = "o=test:"+cn3.toString()+";";
      String cookie4 = "o=test:"+cn4.toString()+";";

      assertEquals(searchOp.getSearchEntries().size(), 4);
      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      if (entries != null)
      {
        int i=0;
        for (SearchResultEntry resultEntry : entries)
        {
          i++;
          debugInfo(tn, "Result entry returned:" + resultEntry.toLDIFString());
          ldifWriter.writeEntry(resultEntry);
          if (i==1)
          {
            // check the DEL entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "replicationcsn=" + cn1 + "," + TEST_ROOT_DN_STRING + ",cn=changelog"));
            checkValue(resultEntry,"replicationcsn",cn1.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"1," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","delete");
            checkValue(resultEntry,"changelogcookie",cookie1);
            checkValue(resultEntry,"targetentryuuid",tn+"uuid1");
            checkValue(resultEntry,"changenumber","0");
          } else if (i==2)
          {
            // check the ADD entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "replicationcsn=" + cn2 + "," + TEST_ROOT_DN_STRING + ",cn=changelog"));
            String expectedValue1 = "objectClass: domain\nobjectClass: top\n" +
            "entryUUID: 11111111-1111-1111-1111-111111111111\n";
            String expectedValue2 = "entryUUID: 11111111-1111-1111-1111-111111111111\n" +
            "objectClass: domain\nobjectClass: top\n";
            checkPossibleValues(resultEntry,"changes",expectedValue1, expectedValue2);
            checkValue(resultEntry,"replicationcsn",cn2.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"2," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","add");
            checkValue(resultEntry,"changelogcookie",cookie2);
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);
            checkValue(resultEntry,"changenumber","0");
          } else if (i==3)
          {
            // check the MOD entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "replicationcsn=" + cn3 + "," + TEST_ROOT_DN_STRING + ",cn=changelog"));
            String expectedValue = "replace: description\n" +
            "description: new value\n-\n";
            checkValue(resultEntry,"changes",expectedValue);
            checkValue(resultEntry,"replicationcsn",cn3.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"3," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","modify");
            checkValue(resultEntry,"changelogcookie",cookie3);
            checkValue(resultEntry,"targetentryuuid",tn+"uuid3");
            checkValue(resultEntry,"changenumber","0");
          } else if (i==4)
          {
            // check the MODDN entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "replicationcsn=" + cn4 + "," + TEST_ROOT_DN_STRING + ",cn=changelog"));
            checkValue(resultEntry,"replicationcsn",cn4.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"4," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","modrdn");
            checkValue(resultEntry,"changelogcookie",cookie4);
            checkValue(resultEntry,"targetentryuuid",tn+"uuid4");
            checkValue(resultEntry,"newrdn","uid=ECLAllOpsnew4");
            if (newSuperior != null)
              checkValue(resultEntry,"newsuperior",TEST_ROOT_DN_STRING2);
            checkValue(resultEntry,"deleteoldrdn","true");
            checkValue(resultEntry,"changenumber","0");
          }
        }
      }

      // Test the response control with ldapsearch tool
      String result = ldapsearch("cn=changelog");
      debugInfo(tn, "Entries:" + result);

      ArrayList<String> ctrlList = getControls(result);
      assertTrue(ctrlList.get(0).equals(cookie1));
      assertTrue(ctrlList.get(1).equals(cookie2));
      assertTrue(ctrlList.get(2).equals(cookie3));
      assertTrue(ctrlList.get(3).equals(cookie4));

      server01.stop();
      if (server02 != null)
        server02.stop();
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  protected ArrayList<String> getControls(String resultString)
  {
    StringReader r=new StringReader(resultString);
    BufferedReader br=new BufferedReader(r);
    ArrayList<String> ctrlList = new ArrayList<String>();
    try {
      while(true) {
        String s = br.readLine();
        if(s == null)
          break;
        if(!s.startsWith("#"))
          continue;
        String[] a=s.split(": ");
        if(a.length != 2)
          break;
        ctrlList.add(a[1]);
      }
    } catch (IOException e) {
      Assert.assertEquals(0, 1,  e.getMessage());
    }
    return ctrlList;
  }

  private static final ByteArrayOutputStream oStream = new ByteArrayOutputStream();
  private static final ByteArrayOutputStream eStream = new ByteArrayOutputStream();

  private String ldapsearch(String baseDN)
  {
    // test search as directory manager returns content
    String[] args3 =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-Z", "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", baseDN,
      "-s", "sub",
      "--control", "1.3.6.1.4.1.26027.1.5.4:false:;",
      "(objectclass=*)"
    };

    oStream.reset();
    eStream.reset();
    int retVal =
      LDAPSearch.mainSearch(args3, false, oStream, eStream);
    Assert.assertEquals(0, retVal,  "Returned error: " + eStream.toString());
    return oStream.toString();
  }

  private static void checkValue(Entry entry, String attrName, String expectedValue)
  {
    AttributeValue av = null;
    try
    {
      List<Attribute> attrs = entry.getAttribute(attrName);
      Attribute a = attrs.iterator().next();
      av = a.iterator().next();
      String encodedValue = av.toString();
      assertTrue(encodedValue.equalsIgnoreCase(expectedValue),
          "In entry " + entry + " attr <" + attrName + "> equals " +
          av + " instead of expected value " + expectedValue);
    }
    catch(Exception e)
    {
      assertTrue(false,
          "In entry " + entry + " attr <" + attrName + "> equals " +
          av + " instead of expected value " + expectedValue);
    }
  }


  private static String getAttributeValue(Entry entry, String attrName)
  {
    AttributeValue av = null;
    try
    {
      List<Attribute> attrs = entry.getAttribute(attrName);
      Attribute a = attrs.iterator().next();
      av = a.iterator().next();
      return av.toString();
    }
    catch(Exception e)
    {
    }
    return null;
  }

  private static void checkPossibleValues(Entry entry, String attrName,
      String expectedValue1, String expectedValue2)
  {
    AttributeValue av = null;
    try
    {
      List<Attribute> attrs = entry.getAttribute(attrName);
      Attribute a = attrs.iterator().next();
      av = a.iterator().next();
      String encodedValue = av.toString();
      assertTrue(
          (encodedValue.equalsIgnoreCase(expectedValue1) ||
              encodedValue.equalsIgnoreCase(expectedValue2)),
              "In entry " + entry + " attr <" + attrName + "> equals " +
              av + " instead of one of the expected values " + expectedValue1
              + " or " + expectedValue2);
    }
    catch(Exception e)
    {
      assertTrue(false,
          "In entry " + entry + " attr <" + attrName + "> equals " +
          av + " instead of one of the expected values " + expectedValue1
          + " or " + expectedValue2);
    }
  }

  private static void checkValues(Entry entry, String attrName,
      Set<String> expectedValues)
  {
    AttributeValue av = null;
    int i=0;
    try
    {
      List<Attribute> attrs = entry.getAttribute(attrName);
      Attribute a;
      Iterator<Attribute> iat = attrs.iterator();
      while ((a=iat.next())!=null)
      {
        Iterator<AttributeValue> iatv = a.iterator();
        while ((av = iatv.next())!=null)
        {
          String encodedValue = av.toString();
          assertTrue(
              expectedValues.contains(encodedValue),
              "In entry " + entry + " attr <" + attrName + "> equals " +
              av + " instead of one of the expected values " + expectedValues);
          i++;
        }
      }
    }
    catch(NoSuchElementException e)
    {
      assertTrue(i==expectedValues.size());
    }
  }

  /**
   * Test persistent search
   */
  private void ECLPsearch(boolean changesOnly, boolean compatMode)
  {
    String tn = "ECLPsearch_" + String.valueOf(changesOnly) + "_" +
      String.valueOf(compatMode);
    debugInfo(tn, "Starting test \n\n");
    Socket s =null;

    // create stats
    for (ConnectionHandler ch : DirectoryServer.getConnectionHandlers())
    {
      if (ch instanceof LDAPConnectionHandler)
      {
        LDAPConnectionHandler lch = (LDAPConnectionHandler) ch;
        if (!lch.useSSL())
        {
          ldapStatistics = lch.getStatTracker();
        }
      }
    }
    assertNotNull(ldapStatistics);

    try
    {
      // Create broker on suffix
      ReplicationBroker server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);
      int ts = 1;

      // Produce update on this suffix
      ChangeNumber cn = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      DeleteMsg delMsg =
        new DeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, cn,
            "11111111-1112-1113-1114-111111111114");
      debugInfo(tn, " publishing " + delMsg.getChangeNumber());
      server01.publish(delMsg);
      this.sleep(500); // let's be sure the message is in the RS

      // Creates cookie control
      String cookie = "";
      ArrayList<Control> controls = createControls(cookie);
      if (compatMode)
      {
        cookie = null;
        controls = new ArrayList<Control>(0);
      }

      // Creates psearch control
      HashSet<PersistentSearchChangeType> changeTypes =
        new HashSet<PersistentSearchChangeType>();
      changeTypes.add(PersistentSearchChangeType.ADD);
      changeTypes.add(PersistentSearchChangeType.DELETE);
      changeTypes.add(PersistentSearchChangeType.MODIFY);
      changeTypes.add(PersistentSearchChangeType.MODIFY_DN);
      boolean returnECs = true;
      PersistentSearchControl persSearchControl = new PersistentSearchControl(
          changeTypes, changesOnly, returnECs);
      controls.add(persSearchControl);

      // Creates request
      SearchRequestProtocolOp searchRequest =
        new SearchRequestProtocolOp(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            false,
            LDAPFilter.decode("(targetDN=*"+tn+"*,o=test)"),
            null);

      // Connects and bind
      debugInfo(tn, "Search with cookie=" + cookie + "\"");
      s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
      LDAPWriter w = new LDAPWriter(s);
      s.setSoTimeout(5000);
      bindAsManager(w, r);

      // Since we are going to be watching the post-response count, we need to
      // wait for the server to become idle before kicking off the next request
      // to ensure that any remaining post-response processing from the previous
      // operation has completed.
      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));

      InvocationCounterPlugin.resetAllCounters();

      long searchRequests   = ldapStatistics.getSearchRequests();
      long searchEntries    = ldapStatistics.getSearchResultEntries();
      long searchReferences = ldapStatistics.getSearchResultReferences();
      long searchesDone     = ldapStatistics.getSearchResultsDone();

      debugInfo(tn, "Search Persistent filter=(targetDN=*"+tn+"*,o=test)");
      LDAPMessage message;
      message = new LDAPMessage(2, searchRequest, controls);
      w.writeMessage(message);
      this.sleep(500);

      SearchResultEntryProtocolOp searchResultEntry = null;
      SearchResultDoneProtocolOp searchResultDone = null;

      if (changesOnly == false)
      {
        // Wait for change 1
        debugInfo(tn, "Waiting for init search expected to return change 1");
        searchEntries = 0;
        message = null;

        try
        {
          while ((searchEntries<1) && (message = r.readMessage()) != null)
          {
            debugInfo(tn, "Init search Result=" +
                message.getProtocolOpType() + message + " " + searchEntries);
            switch (message.getProtocolOpType())
            {
            case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
              searchResultEntry = message.getSearchResultEntryProtocolOp();
              searchEntries++;
              // FIXME:ECL Double check 1 is really the valid value here.
              checkValue(searchResultEntry.toSearchResultEntry(),"changenumber",
                  (compatMode?"1":"0"));
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              searchReferences++;
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              searchResultDone = message.getSearchResultDoneProtocolOp();
              assertEquals(
                  searchResultDone.getResultCode(), ResultCode.SUCCESS,
                  searchResultDone.getErrorMessage().toString());
              searchesDone++;
              break;
            }
          }
        }
        catch(Exception e)
        {
          fail("init search failed with e=" + stackTraceToSingleLineString(e));
        }
        debugInfo(tn, "INIT search done with success. searchEntries="
            + searchEntries + " #searchesDone="+ searchesDone);
      }

      // Produces change 2
      cn = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      String expectedDn = "uid=" + tn + "2," +  TEST_ROOT_DN_STRING;
      delMsg = new DeleteMsg(expectedDn, cn,
         "11111111-1112-1113-1114-111111111115");
      debugInfo(tn, " publishing " + delMsg.getChangeNumber());
      server01.publish(delMsg);
      this.gblCN = cn;
      this.sleep(1000);

      debugInfo(tn, delMsg.getChangeNumber() +
      " published , psearch will now wait for new entries");

      // wait for the 1 new entry
      searchEntries = 0;
      searchResultEntry = null;
      searchResultDone = null;
      message = null;
      while ((searchEntries<1) && (message = r.readMessage()) != null)
      {
        debugInfo(tn, "psearch search  Result=" +
            message.getProtocolOpType() + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchResultEntry = message.getSearchResultEntryProtocolOp();
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          searchReferences++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          searchResultDone = message.getSearchResultDoneProtocolOp();
          assertEquals(
              searchResultDone.getResultCode(), ResultCode.SUCCESS,
              searchResultDone.getErrorMessage().toString());
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      sleep(1000);

      // Check we received change 2
      for (LDAPAttribute a : searchResultEntry.getAttributes())
      {
        if (a.getAttributeType().equalsIgnoreCase("targetDN"))
        {
          for (ByteString av : a.getValues())
          {
            assertTrue(av.toString().equalsIgnoreCase(expectedDn),
                "Entry returned by psearch is " + av.toString() +
                " when expected is " + expectedDn);
          }
        }
      }
      debugInfo(tn, "Second search done successfully : " + searchResultEntry);
      server01.stop();
      try { s.close(); } catch (Exception e) {};
      while (!s.isClosed()) sleep(100);

      // TODO:  Testing ACI is disabled because it is currently failing when
      // ran in the precommit target while it works well when running alone.
      // anonymous search returns entries from cn=changelog whereas it
      // should not. Probably a previous test in the nightlytests suite is
      // removing/modifying some ACIs...
      // When problem found, we have to re-enable this test.
      if (false)
      {
        // ACI step
        debugInfo(tn, "Starting ACI step");
        s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
        r = new org.opends.server.tools.LDAPReader(s);
        w = new LDAPWriter(s);
        s.setSoTimeout(5000);
        bindAsWhoEver(w, r, "toto", "tutu", LDAPResultCode.OPERATIONS_ERROR);

        searchRequest =
          new SearchRequestProtocolOp(
              ByteString.valueOf("cn=changelog"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(targetDN=*directpsearch*,o=test)"),
              null);

        debugInfo(tn, "ACI test : sending search");
        message = new LDAPMessage(2, searchRequest, createControls(""));
        w.writeMessage(message);

        searchesDone=0;
        searchEntries = 0;
        searchResultEntry = null;
        searchResultDone = null;
        while ((searchesDone==0) && (message = r.readMessage()) != null)
        {
          debugInfo(tn, "ACI test : message returned " +
              message.getProtocolOpType() + message);
          switch (message.getProtocolOpType())
          {
          case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
            searchResultEntry = message.getSearchResultEntryProtocolOp();
            //assertTrue(false, "Unexpected entry returned in ACI test of " + tn + searchResultEntry);
            searchEntries++;
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
            searchReferences++;
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
            searchResultDone = message.getSearchResultDoneProtocolOp();
            assertEquals(searchResultDone.getResultCode(),
                ResultCode.SUCCESS.getIntValue());
//          assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
            searchesDone++;
            break;
          }
        }
        // search should end with success
        assertTrue(searchesDone==1);
        // but returning no entry
        assertEquals(searchEntries,0, "Bad search entry# in ACI test of " + tn);
      }

      try { s.close(); } catch (Exception e) {};
      while (!s.isClosed()) sleep(100);
    }
    catch(Exception e)
    {
      fail("Test " + tn + " fails with " +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ends test successfully");
  }

  /**
   * Test parallel simultaneous psearch with different filters.
   */
  private void ECLSimultaneousPsearches()
  {
    String tn = "ECLSimultaneousPsearches";
    debugInfo(tn, "Starting test \n\n");
    Socket s1 = null, s2 = null, s3 = null;
    ReplicationBroker server01 = null;
    ReplicationBroker server02 = null;
    boolean compatMode = false;
    boolean changesOnly = false;

    // create stats
    for (ConnectionHandler ch : DirectoryServer.getConnectionHandlers())
    {
      if (ch instanceof LDAPConnectionHandler)
      {
        LDAPConnectionHandler lch = (LDAPConnectionHandler) ch;
        if (!lch.useSSL())
        {
          ldapStatistics = lch.getStatTracker();
        }
      }
    }
    assertNotNull(ldapStatistics);

    try
    {
      // Create broker on o=test
      server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);
      server01.setChangeTimeHeartbeatInterval(100); //ms
      int ts = 1;

      // Create broker on o=test2
      server02 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2),  1202,
          100, replicationServerPort,
          brokerSessionTimeout, true, EMPTY_DN_GENID);
      server02.setChangeTimeHeartbeatInterval(100); //ms

      // Produce update 1
      ChangeNumber cn1 =
        new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      DeleteMsg delMsg1 =
        new DeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, cn1,
            "11111111-1111-1111-1111-111111111111");
      debugInfo(tn, " publishing " + delMsg1);
      server01.publish(delMsg1);
      this.sleep(500); // let's be sure the message is in the RS

      // Produce update 2
      ChangeNumber cn2 =
        new ChangeNumber(TimeThread.getTime(), ts++, 1202);
      DeleteMsg delMsg2 =
        new DeleteMsg("uid=" + tn + "2," + TEST_ROOT_DN_STRING2, cn2,
            "22222222-2222-2222-2222-222222222222");
      debugInfo(tn, " publishing " + delMsg2);
      server02.publish(delMsg2);
      this.sleep(500); // let's be sure the message is in the RS

      // Produce update 3
      ChangeNumber cn3 =
        new ChangeNumber(TimeThread.getTime(), ts++, 1202);
      DeleteMsg delMsg3 =
        new DeleteMsg("uid=" + tn + "3," + TEST_ROOT_DN_STRING2, cn3,
            "33333333-3333-3333-3333-333333333333");
      debugInfo(tn, " publishing " + delMsg3);
      server02.publish(delMsg3);
      this.sleep(500); // let's be sure the message is in the RS

      // Creates cookie control
      String cookie = "";
      ArrayList<Control> controls = createControls(cookie);
      if (compatMode)
      {
        cookie = null;
        controls = new ArrayList<Control>(0);
      }

      // Creates psearch control
      HashSet<PersistentSearchChangeType> changeTypes =
        new HashSet<PersistentSearchChangeType>();
      changeTypes.add(PersistentSearchChangeType.ADD);
      changeTypes.add(PersistentSearchChangeType.DELETE);
      changeTypes.add(PersistentSearchChangeType.MODIFY);
      changeTypes.add(PersistentSearchChangeType.MODIFY_DN);
      boolean returnECs = true;
      PersistentSearchControl persSearchControl = new PersistentSearchControl(
          changeTypes, changesOnly, returnECs);
      controls.add(persSearchControl);

      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      // Creates request 1
      SearchRequestProtocolOp searchRequest1 =
        new SearchRequestProtocolOp(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            false,
            LDAPFilter.decode("(targetDN=*"+tn+"*,o=test)"),
            attributes);

      // Creates request 2
      SearchRequestProtocolOp searchRequest2 =
        new SearchRequestProtocolOp(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            false,
            LDAPFilter.decode("(targetDN=*"+tn+"*,o=test2)"),
            attributes);

      // Creates request 3
      SearchRequestProtocolOp searchRequest3 =
        new SearchRequestProtocolOp(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            false,
            LDAPFilter.decode("objectclass=*"),
            attributes);

      // Connects and bind
      s1 = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      org.opends.server.tools.LDAPReader r1 = new org.opends.server.tools.LDAPReader(s1);
      LDAPWriter w1 = new LDAPWriter(s1);
      s1.setSoTimeout(15000);
      bindAsManager(w1, r1);

      // Connects and bind
      s2 = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      org.opends.server.tools.LDAPReader r2 = new org.opends.server.tools.LDAPReader(s2);
      LDAPWriter w2 = new LDAPWriter(s2);
      s2.setSoTimeout(30000);
      bindAsManager(w2, r2);

      // Connects and bind
      s3 = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      org.opends.server.tools.LDAPReader r3 = new org.opends.server.tools.LDAPReader(s3);
      LDAPWriter w3 = new LDAPWriter(s3);
      s3.setSoTimeout(15000);
      bindAsManager(w3, r3);

      // Since we are going to be watching the post-response count, we need to
      // wait for the server to become idle before kicking off the next request
      // to ensure that any remaining post-response processing from the previous
      // operation has completed.
      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));

      InvocationCounterPlugin.resetAllCounters();

      long searchRequests   = ldapStatistics.getSearchRequests();
      long searchEntries    = ldapStatistics.getSearchResultEntries();
      long searchReferences = ldapStatistics.getSearchResultReferences();
      long searchesDone     = ldapStatistics.getSearchResultsDone();

      LDAPMessage message;
      message = new LDAPMessage(2, searchRequest1, controls);
      w1.writeMessage(message);
      this.sleep(500);

      message = new LDAPMessage(2, searchRequest2, controls);
      w2.writeMessage(message);
      this.sleep(500);

      message = new LDAPMessage(2, searchRequest3, controls);
      w3.writeMessage(message);
      this.sleep(500);

      SearchResultEntryProtocolOp searchResultEntry = null;
      SearchResultDoneProtocolOp searchResultDone = null;

      if (changesOnly == false)
      {
        debugInfo(tn, "Search1  Persistent filter="+searchRequest1.getFilter().toString()
                  + " expected to return change " + cn1);
        searchEntries = 0;
        message = null;

        try
        {
          while ((searchEntries<1) && (message = r1.readMessage()) != null)
          {
            debugInfo(tn, "Search1 Result=" +
                message.getProtocolOpType() + " " + message);
            switch (message.getProtocolOpType())
            {
            case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
              searchResultEntry = message.getSearchResultEntryProtocolOp();
              searchEntries++;
              if (searchEntries==1)
              {
                checkValue(searchResultEntry.toSearchResultEntry(),"replicationcsn",cn1.toString());
                checkValue(searchResultEntry.toSearchResultEntry(),"changenumber",
                    (compatMode?"10":"0"));
              }
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              searchReferences++;
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              searchResultDone = message.getSearchResultDoneProtocolOp();
              assertEquals(
                  searchResultDone.getResultCode(), ResultCode.SUCCESS,
                  searchResultDone.getErrorMessage().toString());
              searchesDone++;
              break;
            }
          }
        }
        catch(Exception e)
        {
          fail("Search1 failed with e=" + stackTraceToSingleLineString(e));
        }
        debugInfo(tn, "Search1 done with success. searchEntries="
            + searchEntries + " #searchesDone="+ searchesDone);

        searchEntries = 0;
        message = null;
        try
        {
          debugInfo(tn, "Search 2  Persistent filter="+searchRequest2.getFilter().toString()
              + " expected to return change " + cn2 + " & " + cn3);
          while ((searchEntries<2) && (message = r2.readMessage()) != null)
          {
            debugInfo(tn, "Search 2 Result=" +
                message.getProtocolOpType() + message);
            switch (message.getProtocolOpType())
            {
            case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
              searchResultEntry = message.getSearchResultEntryProtocolOp();
              searchEntries++;
              checkValue(searchResultEntry.toSearchResultEntry(),"changenumber",
                  (compatMode?"10":"0"));
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              searchReferences++;
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              searchResultDone = message.getSearchResultDoneProtocolOp();
              assertEquals(
                  searchResultDone.getResultCode(), ResultCode.SUCCESS,
                  searchResultDone.getErrorMessage().toString());
              searchesDone++;
              break;
            }
          }
        }
        catch(Exception e)
        {
          fail("Search2 failed with e=" + stackTraceToSingleLineString(e));
        }
        debugInfo(tn, "Search2 done with success. searchEntries="
            + searchEntries + " #searchesDone="+ searchesDone);


        searchEntries = 0;
        message = null;
        try
        {
          debugInfo(tn, "Search3  Persistent filter="+searchRequest3.getFilter().toString()
              + " expected to return change top + " + cn1 + " & " + cn2 + " & " + cn3);
          while ((searchEntries<4) && (message = r3.readMessage()) != null)
          {
            debugInfo(tn, "Search3 Result=" +
                message.getProtocolOpType() + " " + message);

            switch (message.getProtocolOpType())
            {
            case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
              searchResultEntry = message.getSearchResultEntryProtocolOp();
              searchEntries++;
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              searchReferences++;
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
              searchResultDone = message.getSearchResultDoneProtocolOp();
              assertEquals(
                  searchResultDone.getResultCode(), ResultCode.SUCCESS,
                  searchResultDone.getErrorMessage().toString());
              searchesDone++;
              break;
            }
          }
        }
        catch(Exception e)
        {
          fail("Search3 failed with e=" + stackTraceToSingleLineString(e));
        }
        debugInfo(tn, "Search3 done with success. searchEntries="
            + searchEntries + " #searchesDone="+ searchesDone);

      }

      // Produces additional change
      ChangeNumber cn11 = new ChangeNumber(TimeThread.getTime(), 11, 1201);
      String expectedDn11 = "uid=" + tn + "11," +  TEST_ROOT_DN_STRING;
      DeleteMsg delMsg11 = new DeleteMsg(expectedDn11, cn11,
         "44444444-4444-4444-4444-444444444444");
      debugInfo(tn, " publishing " + delMsg11);
      server01.publish(delMsg11);
      this.sleep(500);
      debugInfo(tn, delMsg11.getChangeNumber() + " published additionally ");

      // Produces additional change
      ChangeNumber cn12 = new ChangeNumber(TimeThread.getTime(), 12, 1202);
      String expectedDn12 = "uid=" + tn + "12," +  TEST_ROOT_DN_STRING2;
      DeleteMsg delMsg12 = new DeleteMsg(expectedDn12, cn12,
         "55555555-5555-5555-5555-555555555555");
      debugInfo(tn, " publishing " + delMsg12 );
      server02.publish(delMsg12);
      this.sleep(500);
      debugInfo(tn, delMsg12.getChangeNumber()  + " published additionally ");

      // Produces additional change
      ChangeNumber cn13 = new ChangeNumber(TimeThread.getTime(), 13, 1202);
      String expectedDn13 = "uid=" + tn + "13," +  TEST_ROOT_DN_STRING2;
      DeleteMsg delMsg13 = new DeleteMsg(expectedDn13, cn13,
         "66666666-6666-6666-6666-666666666666");
      debugInfo(tn, " publishing " + delMsg13);
      server02.publish(delMsg13);
      this.sleep(500);
      debugInfo(tn, delMsg13.getChangeNumber()  + " published additionally ");

      // wait 11
      searchEntries = 0;
      searchResultEntry = null;
      searchResultDone = null;
      message = null;
      while ((searchEntries<1) && (message = r1.readMessage()) != null)
      {
        debugInfo(tn, "Search 11 Result=" +
            message.getProtocolOpType() + " " + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchResultEntry = message.getSearchResultEntryProtocolOp();
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          searchReferences++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          searchResultDone = message.getSearchResultDoneProtocolOp();
          assertEquals(
              searchResultDone.getResultCode(), ResultCode.SUCCESS,
              searchResultDone.getErrorMessage().toString());
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      sleep(1000);
      debugInfo(tn, "Search 1 successfully receives additional changes");

      // wait 12 & 13
      searchEntries = 0;
      searchResultEntry = null;
      searchResultDone = null;
      message = null;
      while ((searchEntries<2) && (message = r2.readMessage()) != null)
      {
        debugInfo(tn, "psearch search 12 Result=" +
            message.getProtocolOpType() + " " + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchResultEntry = message.getSearchResultEntryProtocolOp();
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          searchReferences++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          searchResultDone = message.getSearchResultDoneProtocolOp();
          assertEquals(
              searchResultDone.getResultCode(), ResultCode.SUCCESS,
              searchResultDone.getErrorMessage().toString());
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      sleep(1000);
      debugInfo(tn, "Search 2 successfully receives additional changes");

      // wait 11 & 12 & 13
      searchEntries = 0;
      searchResultEntry = null;
      searchResultDone = null;
      message = null;
      while ((searchEntries<3) && (message = r3.readMessage()) != null)
      {
        debugInfo(tn, "psearch search 13 Result=" +
            message.getProtocolOpType() + " " + message);
        switch (message.getProtocolOpType())
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          searchResultEntry = message.getSearchResultEntryProtocolOp();
          searchEntries++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          searchReferences++;
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          searchResultDone = message.getSearchResultDoneProtocolOp();
          assertEquals(
              searchResultDone.getResultCode(), ResultCode.SUCCESS,
              searchResultDone.getErrorMessage().toString());
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
          searchesDone++;
          break;
        }
      }
      sleep(1000);

      // Check we received change 13
      for (LDAPAttribute a : searchResultEntry.getAttributes())
      {
        if (a.getAttributeType().equalsIgnoreCase("targetDN"))
        {
          for (ByteString av : a.getValues())
          {
            assertTrue(av.toString().equalsIgnoreCase(expectedDn13),
                "Entry returned by psearch 13 is " + av.toString() +
                " when expected is " + expectedDn13);
          }
        }
      }
      debugInfo(tn, "Search 3 successfully receives additional changes");
   }
    catch(Exception e)
    {
      fail("Test " + tn + " fails with " +  stackTraceToSingleLineString(e));
    }
    finally
    {
      if (server01 != null)
        server01.stop();
      if (server02 != null)
        server02.stop();

      if (s1 != null)
      {
        try { s1.close(); } catch (Exception ignored) {};
        while (!s1.isClosed()) sleep(100);
      }
      if (s2 != null)
      {
        try { s2.close(); } catch (Exception e) {};
        while (!s2.isClosed()) sleep(100);
      }
      if (s3 != null)
      {
        try { s3.close(); } catch (Exception e) {};
        while (!s3.isClosed()) sleep(100);
      }
      replicationServer.clearDb();
    }
    debugInfo(tn, "Ends test successfully");
  }

  // utility - bind as required
  private void bindAsManager(LDAPWriter w, org.opends.server.tools.LDAPReader r)
  throws IOException, LDAPException, ASN1Exception, InterruptedException
  {
    bindAsWhoEver(w, r,
        "cn=Directory Manager", "password", LDAPResultCode.SUCCESS);
  }

  // utility - bind as required
  private void bindAsWhoEver(LDAPWriter w, org.opends.server.tools.LDAPReader r,
      String bindDN, String password,  int expected)
  throws IOException, LDAPException, ASN1Exception, InterruptedException
  {
//  Since we are going to be watching the post-response count, we need to
//  wait for the server to become idle before kicking off the next request to
//  ensure that any remaining post-response processing from the previous
//  operation has completed.
    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));


    InvocationCounterPlugin.resetAllCounters();
    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
          ByteString.valueOf(bindDN),
          3, ByteString.valueOf(password));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
//  assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
    assertEquals(bindResponse.getResultCode(), expected);
  }

  /**
   * Clean up the environment.
   *
   * @throws Exception If the environment could not be set up.
   */
  @Override
  @AfterClass
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    shutdown();

    paranoiaCheck();
  }

  /**
   * After the tests stop the replicationServer.
   */
  protected void shutdown() throws Exception
  {
    if (replicationServer != null) {
      replicationServer.remove();
      StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
            replicationServer.getDbDirName()));
    }
    replicationServer = null;
  }
  /**
   * Utility - sleeping as long as required
   */
  private void sleep(long time)
  {
    try
    {
      Thread.sleep(time);
    } catch (InterruptedException ex)
    {
      fail("Error sleeping " + ex.getMessage());
    }
  }

  /**
   * Utility - log debug message - highlight it is from the test and not
   * from the server code. Makes easier to observe the test steps.
   */
  private void debugInfo(String tn, String s)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST " + tn + " ** " + s);
    }
    // logError(Message.raw(Category.SYNC, Severity.NOTICE,
    // "** TEST " + tn + " ** " + s));
  }

  /**
   * Utility - create a second backend in order to test ECL with 2 suffixes.
   */
  private static Backend initializeTestBackend(
      boolean createBaseEntry,
      String rootDN,
      String backendId)
  throws IOException, InitializationException, ConfigException,
  DirectoryException
  {

    DN baseDN = DN.decode(rootDN);

    //  Retrieve backend. Warning: it is important to perform this each time,
    //  because a test may have disabled then enabled the backend (i.e a test
    //  performing an import task). As it is a memory backend, when the backend
    //  is re-enabled, a new backend object is in fact created and old reference
    //  to memory backend must be invalidated. So to prevent this problem, we
    //  retrieve the memory backend reference each time before cleaning it.
    MemoryBackend memoryBackend =
      (MemoryBackend)DirectoryServer.getBackend(backendId);

    if (memoryBackend == null)
    {
      memoryBackend = new MemoryBackend();
      memoryBackend.setBackendID(backendId);
      memoryBackend.setBaseDNs(new DN[] {baseDN});
      memoryBackend.initializeBackend();
      DirectoryServer.registerBackend(memoryBackend);
    }

    memoryBackend.clearMemoryBackend();

    if (createBaseEntry)
    {
      Entry e = createEntry(baseDN);
      memoryBackend.addEntry(e, null);
    }
    return memoryBackend;
  }

  private static void removeTestBackend2(Backend backend)
  {
    MemoryBackend memoryBackend = (MemoryBackend)backend;
    memoryBackend.clearMemoryBackend();
    memoryBackend.finalizeBackend();
    DirectoryServer.deregisterBackend(memoryBackend);
  }

  //=======================================================
  private void ChangeTimeHeartbeatTest()
  {
    String tn = "ChangeTimeHeartbeatTest";
    debugInfo(tn, "Starting test");
    ReplicationBroker s1test = null;
    ReplicationBroker s2test = null;
    ReplicationBroker s1test2 = null;
    ReplicationBroker s2test2 = null;

    // Initialize a second test backend
    Backend backend2 = null;

    try
    {
      backend2 = initializeTestBackend(true, TEST_ROOT_DN_STRING2,
          TEST_BACKEND_ID2);

      // --
      s1test = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);

      s2test2 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2),  1202,
          100, replicationServerPort,
          brokerSessionTimeout, true, EMPTY_DN_GENID);
      sleep(500);

      // Produce updates
      long time = TimeThread.getTime();
      int ts = 1;
      ChangeNumber cn = new ChangeNumber(time, ts++, s1test.getServerId());
      DeleteMsg delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn, tn+"uuid1");
      s1test.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      cn = new ChangeNumber(time++, ts++, s2test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"2," + TEST_ROOT_DN_STRING2, cn, tn+"uuid2");
      s2test2.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      ChangeNumber cn3 = new ChangeNumber(time++, ts++, s2test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"3," + TEST_ROOT_DN_STRING2, cn3, tn+"uuid3");
      s2test2.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      cn = new ChangeNumber(time++, ts++, s1test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"4," + TEST_ROOT_DN_STRING, cn, tn+"uuid4");
      s1test.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());
      sleep(500);

      // --
      s1test2 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2),  1203,
          100, replicationServerPort,
          brokerSessionTimeout, true, EMPTY_DN_GENID);

      s2test = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1204,
          100, replicationServerPort,
          brokerSessionTimeout, true);
      sleep(500);

      // Test startState ("first cookie") of the ECL
      time = TimeThread.getTime();
      cn = new ChangeNumber(time++, ts++, s1test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"6," + TEST_ROOT_DN_STRING2, cn, tn+"uuid6");
      s1test2.publish(delMsg);

      cn = new ChangeNumber(time++, ts++, s2test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"7," + TEST_ROOT_DN_STRING, cn, tn+"uuid7");
      s2test.publish(delMsg);

      ChangeNumber cn8 = new ChangeNumber(time++, ts++, s1test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"8," + TEST_ROOT_DN_STRING2, cn8, tn+"uuid8");
      s1test2.publish(delMsg);

      ChangeNumber cn9 = new ChangeNumber(time++, ts++, s2test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"9," + TEST_ROOT_DN_STRING, cn9, tn+"uuid9");
      s2test.publish(delMsg);
      sleep(500);

      ReplicationServerDomain rsd1 =
        replicationServer.getReplicationServerDomain(TEST_ROOT_DN_STRING, false);
      rsd1.getDbServerState();
      rsd1.getChangeTimeHeartbeatState();
      debugInfo(tn, rsd1.getBaseDn()
          + " DbServerState=" + rsd1.getDbServerState()
          + " ChangeTimeHeartBeatState=" + rsd1.getChangeTimeHeartbeatState()
          + " eligibleCN=" + rsd1.getEligibleCN()
          + " rs eligibleCN=" + replicationServer.getEligibleCN());
      // FIXME:ECL Enable this test by adding an assert on the right value

      ReplicationServerDomain rsd2 =
        replicationServer.getReplicationServerDomain(TEST_ROOT_DN_STRING2, false);
      rsd2.getDbServerState();
      rsd2.getChangeTimeHeartbeatState();
      debugInfo(tn, rsd2.getBaseDn()
          + " DbServerState=" + rsd2.getDbServerState()
          + " ChangeTimeHeartBeatState=" + rsd2.getChangeTimeHeartbeatState()
          + " eligibleCN=" + rsd2.getEligibleCN()
          + " rs eligibleCN=" + replicationServer.getEligibleCN());
      // FIXME:ECL Enable this test by adding an assert on the right value
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception:"
          +  stackTraceToSingleLineString(e));
    }
    finally
    {
      if (s1test2 != null)
        s1test2.stop();
      if (s2test2 != null)
        s2test2.stop();
      if (backend2 != null)
        removeTestBackend2(backend2);
      if (s1test != null)
        s1test.stop();
      if (s2test != null)
        s2test.stop();

      replicationServer.clearDb();
    }
    debugInfo(tn, "Ending test successfully");
  }

  /**
   * From embedded ECL (no remote session)
   * With empty RS, simple search should return only root entry.
   */
  private void ECLCompatEmpty()
  {
    String tn = "ECLCompatEmpty";
    debugInfo(tn, "Starting test\n\n");

    try
    {
      // search on 'cn=changelog'
      String filter = "(objectclass=*)";
      debugInfo(tn, " Search: " + filter);
      InternalSearchOperation op = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          LDAPFilter.decode(filter));

      // success
      assertEquals(
          op.getResultCode(), ResultCode.SUCCESS,
          op.getErrorMessage().toString());

      // root entry returned
      assertEquals(op.getEntriesSent(), 1);
      debugInfo(tn, "Ending test successfully");
    }
    catch(LDAPException e)
    {
      fail("Ending test " + tn + " with exception="
          +  stackTraceToSingleLineString(e));
    }
  }

  private int ECLCompatWriteReadAllOps(int firstDraftChangeNumber)
  {
    String tn = "ECLCompatWriteReadAllOps/" + String.valueOf(firstDraftChangeNumber);
    debugInfo(tn, "Starting test\n\n");
    int ts = 1;

    try
    {
      LDIFWriter ldifWriter = getLDIFWriter();

      // Creates broker on o=test
      ReplicationBroker server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);

      String user1entryUUID = "11111111-1112-1113-1114-111111111115";
      String baseUUID       = "22222222-2222-2222-2222-222222222222";


      // Publish DEL
      ChangeNumber cn1 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      DeleteMsg delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn1,
            user1entryUUID);
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      // Publish ADD
      gblCN = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      String lentry = new String(
          "dn: uid="+tn+"2," + TEST_ROOT_DN_STRING + "\n"
          + "objectClass: top\n"
          + "objectClass: domain\n"
          + "entryUUID: "+user1entryUUID+"\n");
      Entry entry = TestCaseUtils.entryFromLdifString(lentry);
      AddMsg addMsg = new AddMsg(
          gblCN,
          "uid="+tn+"2," + TEST_ROOT_DN_STRING,
          user1entryUUID,
          baseUUID,
          entry.getObjectClassAttribute(),
          entry.getAttributes(),
          new ArrayList<Attribute>());
      server01.publish(addMsg);
      debugInfo(tn, " publishes " + addMsg.getChangeNumber());

      // Publish MOD
      ChangeNumber cn3 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      Attribute attr1 = Attributes.create("description", "new value");
      Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
      List<Modification> mods = new ArrayList<Modification>();
      mods.add(mod1);
      ModifyMsg modMsg = new ModifyMsg(cn3, DN
          .decode("uid="+tn+"3," + TEST_ROOT_DN_STRING), mods, user1entryUUID);
      server01.publish(modMsg);
      debugInfo(tn, " publishes " + modMsg.getChangeNumber());

      // Publish modDN
      ChangeNumber cn4 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      ModifyDNOperationBasis op = new ModifyDNOperationBasis(connection, 1, 1, null,
          DN.decode("uid="+tn+"4," + TEST_ROOT_DN_STRING), // entryDN
          RDN.decode("uid="+tn+"new4"), // new rdn
          true,  // deleteoldrdn
          DN.decode(TEST_ROOT_DN_STRING2)); // new superior
      op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(cn4, user1entryUUID,
      "newparentId"));
      LocalBackendModifyDNOperation localOp = new LocalBackendModifyDNOperation(op);
      ModifyDNMsg modDNMsg = new ModifyDNMsg(localOp);
      server01.publish(modDNMsg);
      debugInfo(tn, " publishes " + modDNMsg.getChangeNumber());
      sleep(1000);

      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      String filter = "(targetdn=*"+tn.toLowerCase()+"*,o=test)";
      debugInfo(tn, " Search: " + filter);
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode(filter),
            attributes,
            NO_CONTROL,
            null);
      waitOpResult(searchOp, ResultCode.SUCCESS);

      // test 4 entries returned
      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      // 4 entries expected
      assertEquals(searchOp.getSearchEntries().size(), 4);
      if (entries != null)
      {
        int i=0;
        for (SearchResultEntry resultEntry : entries)
        {
          i++;
          debugInfo(tn, "Result entry returned:" + resultEntry.toLDIFString());
          ldifWriter.writeEntry(resultEntry);
          if (i==1)
          {
            // check the DEL entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "changenumber="+String.valueOf(firstDraftChangeNumber+0)+",cn=changelog"),
                "Result entry DN : actual=" + resultEntry.getDN().toNormalizedString() +
                " expected=" + "changenumber="+String.valueOf(firstDraftChangeNumber+0)+",cn=changelog");
            checkValue(resultEntry,"replicationcsn",cn1.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"1," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","delete");
            checkValue(resultEntry,"changelogcookie","o=test:"+cn1.toString()+";");
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);
            checkValue(resultEntry,"changenumber",String.valueOf(firstDraftChangeNumber+0));
            checkValue(resultEntry,"targetuniqueid",user1entryUUID);
          } else if (i==2)
          {
            // check the ADD entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "changenumber="+String.valueOf(firstDraftChangeNumber+1)+",cn=changelog"));
            String expectedValue1 = "objectClass: domain\nobjectClass: top\n" +
            "entryUUID: "+user1entryUUID+"\n";
            String expectedValue2 = "entryUUID: "+user1entryUUID+"\n" +
            "objectClass: domain\nobjectClass: top\n";
            checkPossibleValues(resultEntry,"changes",expectedValue1, expectedValue2);
            checkValue(resultEntry,"replicationcsn",gblCN.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"2," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","add");
            checkValue(resultEntry,"changelogcookie","o=test:"+gblCN.toString()+";");
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);
            checkValue(resultEntry,"changenumber",String.valueOf(firstDraftChangeNumber+1));
          } else if (i==3)
          {
            // check the MOD entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "changenumber="+String.valueOf(firstDraftChangeNumber+2)+",cn=changelog"));
            String expectedValue = "replace: description\n" +
            "description: new value\n-\n";
            checkValue(resultEntry,"changes",expectedValue);
            checkValue(resultEntry,"replicationcsn",cn3.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"3," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","modify");
            checkValue(resultEntry,"changelogcookie","o=test:"+cn3.toString()+";");
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);
            checkValue(resultEntry,"changenumber",String.valueOf(firstDraftChangeNumber+2));
          } else if (i==4)
          {
            // check the MODDN entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "changenumber="+String.valueOf(firstDraftChangeNumber+3)+",cn=changelog"));
            checkValue(resultEntry,"replicationcsn",cn4.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"4," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","modrdn");
            checkValue(resultEntry,"changelogcookie","o=test:"+cn4.toString()+";");
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);
            checkValue(resultEntry,"newrdn","uid="+tn+"new4");
            checkValue(resultEntry,"newsuperior",TEST_ROOT_DN_STRING2);
            checkValue(resultEntry,"deleteoldrdn","true");
            checkValue(resultEntry,"changenumber",String.valueOf(firstDraftChangeNumber+3));
          }
        }
      }
      server01.stop();

      // Test with filter on draft changenumber
      filter = "(&(targetdn=*"+tn.toLowerCase()+"*,o=test)(&(changenumber>="+
      firstDraftChangeNumber+")(changenumber<="+(firstDraftChangeNumber+3)+")))";
      debugInfo(tn, " Search: " + filter);
      searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode(filter),
            attributes,
            NO_CONTROL,
            null);
      waitOpResult(searchOp, ResultCode.SUCCESS);

      entries = searchOp.getSearchEntries();

      if (entries != null)
      {
        int i=0;
        for (SearchResultEntry resultEntry : entries)
        {
          i++;
          debugInfo(tn, "Result entry returned:" + resultEntry.toLDIFString());
          ldifWriter.writeEntry(resultEntry);
          if (i==1)
          {
            // check the DEL entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "changenumber="+String.valueOf(firstDraftChangeNumber+0)+",cn=changelog"));
            checkValue(resultEntry,"replicationcsn",cn1.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"1," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","delete");
            checkValue(resultEntry,"changelogcookie","o=test:"+cn1.toString()+";");
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);
            checkValue(resultEntry,"changenumber",String.valueOf(firstDraftChangeNumber+0));
            checkValue(resultEntry,"targetuniqueid",user1entryUUID);
          } else if (i==2)
          {
            // check the ADD entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "changenumber="+String.valueOf(firstDraftChangeNumber+1)+",cn=changelog"));
            String expectedValue1 = "objectClass: domain\nobjectClass: top\n" +
            "entryUUID: "+user1entryUUID+"\n";
            String expectedValue2 = "entryUUID: "+user1entryUUID+"\n" +
            "objectClass: domain\nobjectClass: top\n";
            checkPossibleValues(resultEntry,"changes",expectedValue1, expectedValue2);
            checkValue(resultEntry,"replicationcsn",gblCN.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"2," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","add");
            checkValue(resultEntry,"changelogcookie","o=test:"+gblCN.toString()+";");
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);
            checkValue(resultEntry,"changenumber",String.valueOf(firstDraftChangeNumber+1));
          } else if (i==3)
          {
            // check the MOD entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "changenumber="+String.valueOf(firstDraftChangeNumber+2)+",cn=changelog"));
            String expectedValue = "replace: description\n" +
            "description: new value\n-\n";
            checkValue(resultEntry,"changes",expectedValue);
            checkValue(resultEntry,"replicationcsn",cn3.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"3," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","modify");
            checkValue(resultEntry,"changelogcookie","o=test:"+cn3.toString()+";");
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);
            checkValue(resultEntry,"changenumber",String.valueOf(firstDraftChangeNumber+2));
          } else if (i==4)
          {
            // check the MODDN entry has the right content
            assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
                "changenumber="+String.valueOf(firstDraftChangeNumber+3)+",cn=changelog"));
            checkValue(resultEntry,"replicationcsn",cn4.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"4," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","modrdn");
            checkValue(resultEntry,"changelogcookie","o=test:"+cn4.toString()+";");
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);
            checkValue(resultEntry,"newrdn","uid="+tn+"new4");
            checkValue(resultEntry,"newsuperior",TEST_ROOT_DN_STRING2);
            checkValue(resultEntry,"deleteoldrdn","true");
            checkValue(resultEntry,"changenumber",String.valueOf(firstDraftChangeNumber+3));
          }
        }
      }
      assertEquals(searchOp.getSearchEntries().size(), 4);
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception:"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
    return ts;
  }

  private void ECLCompatReadFrom(int firstDraftChangeNumber)
  {
    String tn = "ECLCompatReadFrom/" + String.valueOf(firstDraftChangeNumber);
    debugInfo(tn, "Starting test\n\n");

    try
    {
      LDIFWriter ldifWriter = getLDIFWriter();

      // Creates broker on o=test
      ReplicationBroker server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);

      String user1entryUUID = "11111111-1112-1113-1114-111111111115";

      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      String filter = "(changenumber="+firstDraftChangeNumber+")";
      debugInfo(tn, " Search: " + filter);
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode(filter),
            attributes,
            NO_CONTROL,
            null);
      waitOpResult(searchOp, ResultCode.SUCCESS);

      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertEquals(searchOp.getSearchEntries().size(), 1);
      if (entries != null)
      {
        for (SearchResultEntry resultEntry : entries)
        {
          debugInfo(tn, "Result entry returned:" + resultEntry.toLDIFString());
          ldifWriter.writeEntry(resultEntry);
          // check the entry has the right content
          assertTrue(resultEntry.getDN().toNormalizedString().equalsIgnoreCase(
              "changenumber=6,cn=changelog"));
          checkValue(resultEntry,"replicationcsn",gblCN.toString());
          checkValue(resultEntry,"replicaidentifier","1201");
          checkValue(resultEntry,"changetype","add");
          checkValue(resultEntry,"changelogcookie","o=test:"+gblCN.toString()+";");
          checkValue(resultEntry,"targetentryuuid",user1entryUUID);
          checkValue(resultEntry,"changenumber","6");
        }
      }
      server01.stop();
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  // Process similar search as  but only check that there's no control
  // returned as part of the entry.
  private void ECLCompatNoControl(int firstDraftChangeNumber)
  {
    String tn = "ECLCompatNoControl/" + String.valueOf(firstDraftChangeNumber);
    debugInfo(tn, "Starting test\n\n");

    try
    {
      // Creates broker on o=test
      ReplicationBroker server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);

      String user1entryUUID = "11111111-1112-1113-1114-111111111115";

      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      String filter = "(changenumber="+firstDraftChangeNumber+")";
      debugInfo(tn, " Search: " + filter);
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode(filter),
            attributes,
            NO_CONTROL,
            null);
      waitOpResult(searchOp, ResultCode.SUCCESS);

      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertEquals(searchOp.getSearchEntries().size(), 1);
      if (entries != null)
      {
        for (SearchResultEntry resultEntry : entries)
        {
          // Just verify that no entry contains the ChangeLogCookie control
          List<Control> controls = resultEntry.getControls();
          assertTrue(controls.isEmpty());
        }
      }
      server01.stop();
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");

  }


  /**
   * Read the ECL in compat mode from firstDraftChangeNumber and to
   * lastDraftChangeNumber.
   * @param firstDraftChangeNumber
   * @param lastDraftChangeNumber
   */
  private void ECLCompatReadFromTo(int firstDraftChangeNumber,
      int lastDraftChangeNumber)
  {
    String tn = "ECLCompatReadFromTo/" +
    String.valueOf(firstDraftChangeNumber) + "/" +
    String.valueOf(lastDraftChangeNumber);

    debugInfo(tn, "Starting test\n\n");

    try
    {
      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      String filter = "(&(changenumber>="+firstDraftChangeNumber+")(changenumber<="+lastDraftChangeNumber+"))";
      debugInfo(tn, " Search: " + filter);
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode(filter),
            attributes,
            NO_CONTROL,
            null);
      waitOpResult(searchOp, ResultCode.SUCCESS);
      assertEquals(searchOp.getSearchEntries().size(),
          lastDraftChangeNumber-firstDraftChangeNumber+1);
      if (searchOp.getSearchEntries() != null)
      {
        for (SearchResultEntry resultEntry : searchOp.getSearchEntries())
        {
          debugInfo(tn, "Result entry returned:" + resultEntry.toLDIFString());
        }
      }
    }
    catch(Exception e)
    {
      fail("Ending test " + tn + " with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  /**
   * Read the ECL in compat mode providing an unknown draft changenumber.
   */
  private void ECLCompatBadSeqnum()
  {
    String tn = "ECLCompatBadSeqnum";
    debugInfo(tn, "Starting test\n\n");

    try
    {
      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      String filter = "(changenumber=1000)";
      debugInfo(tn, " Search: " + filter);
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode(filter),
            attributes,
            NO_CONTROL,
            null);
      waitOpResult(searchOp, ResultCode.SUCCESS);
      assertEquals(searchOp.getSearchEntries().size(), 0);
    }
    catch(Exception e)
    {
      fail("Ending test "+tn+" with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  /**
   * Read the ECL in compat mode providing an unknown draft changenumber.
   */
  private void ECLFilterOnReplicationCsn()
  {
    String tn = "ECLFilterOnReplicationCsn";
    debugInfo(tn, "Starting test\n\n");

    try
    {
      LDIFWriter ldifWriter = getLDIFWriter();

      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      String filter = "(replicationcsn="+this.gblCN+")";
      debugInfo(tn, " Search: " + filter);
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode(filter),
            attributes,
            NO_CONTROL,
            null);
      waitOpResult(searchOp, ResultCode.SUCCESS);
      assertEquals(searchOp.getSearchEntries().size(), 1);

      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertEquals(searchOp.getSearchEntries().size(), 1);
      if (entries != null)
      {
        for (SearchResultEntry resultEntry : entries)
        {
          debugInfo(tn, "Result entry returned:" + resultEntry.toLDIFString());
          ldifWriter.writeEntry(resultEntry);
          // check the DEL entry has the right content
          checkValue(resultEntry,"replicationcsn",gblCN.toString());
          // TODO:ECL check values of the other attributes
        }
      }

    }
    catch(Exception e)
    {
      fail("Ending test "+tn+" with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  /**
   * Test that different values of filter are correctly decoded
   * to find if the search op on the ECL can be optimized
   * regarding the Draft changenumbers.
   */
  private void ECLFilterTest()
  {
    String tn = "ECLFilterTest";
    debugInfo(tn, "Starting test\n\n");
    try
    {
      StartECLSessionMsg startCLmsg = new StartECLSessionMsg();

      DN baseDN = DN.decode("cn=changelog");

      //
      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(objectclass=*)"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),-1);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),-1);

      //
      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(changenumber>=2)"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),2);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),-1);

      //
      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(&(changenumber>=2)(changenumber<=5))"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),2);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),5);

      //
      try
      {
        ECLSearchOperation.evaluateSearchParameters(startCLmsg,
            baseDN, SearchFilter.createFilterFromString("(&(changenumber>=2)(changenumber<+5))"));
        assertTrue((startCLmsg.getFirstDraftChangeNumber()==1));
      }
      catch(DirectoryException de)
      {
        assertTrue(de != null);
      }

      //
      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(&(dc=x)(&(changenumber>=2)(changenumber<=5)))"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),2);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),5);

      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(&(&(changenumber>=3)(changenumber<=4))(&(|(dc=y)(dc=x))(&(changenumber>=2)(changenumber<=5))))"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),3);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),4);

      //
      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(|(objectclass=*)(&(changenumber>=2)(changenumber<=5)))"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),-1);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),-1);

      //
      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(changenumber=8)"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),8);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),8);

      //
      ChangeNumberGenerator gen = new ChangeNumberGenerator( 1, 0);
      ChangeNumber changeNumber1 = gen.newChangeNumber();
      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(replicationcsn="+changeNumber1+")"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),-1);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),-1);
      assertEquals(startCLmsg.getChangeNumber(), changeNumber1);

      // Use change number as base object.
      baseDN = DN.decode("changeNumber=8,cn=changelog");

      //
      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(objectclass=*)"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),8);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),8);

      // The base DN should take preference.
      ECLSearchOperation.evaluateSearchParameters(startCLmsg,
          baseDN, SearchFilter.createFilterFromString("(changenumber>=2)"));
      assertEquals(startCLmsg.getFirstDraftChangeNumber(),8);
      assertEquals(startCLmsg.getLastDraftChangeNumber(),8);
    }
    catch(Exception e)
    {
      fail("Ending "+tn+" test with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  /**
   * Put a short purge delay to the draftCNDB, clear the changelogDB,
   * expect the draftCNDb to be purged accordingly.
   */
  private void ECLPurgeDraftCNDbAfterChangelogClear()
  {
    String tn = "ECLPurgeDraftCNDbAfterChangelogClear";
    debugInfo(tn, "Starting test\n\n");
    try
    {
      DraftCNDbHandler draftdb = replicationServer.getDraftCNDbHandler();
      assertEquals(draftdb.count(), 8);
      draftdb.setPurgeDelay(1000);

      // Now Purge the changelog db
      this.replicationServer.clearDb();

      // Expect changes purged from the changelog db to be sometimes
      // also purged from the DraftCNDb.
      while(draftdb.count()>0)
      {
        debugInfo(tn, "draftdb.count="+draftdb.count());
        sleep(200);
      }
    }
    catch(Exception e)
    {
      fail("Ending "+tn+" test with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  private void ECLOperationalAttributesFailTest()
  {
    String tn = "ECLOperationalAttributesFailTest";
    // The goal is to verify that the Changelog attributes are not
    // available in other entries. We u
    debugInfo(tn, "Starting test \n\n");
    try
    {
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();

      attributes.add("firstchangenumber");
      attributes.add("lastchangenumber");
      attributes.add("changelog");
      attributes.add("lastExternalChangelogCookie");

      debugInfo(tn, " Search: "+ TEST_ROOT_DN_STRING);
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf(TEST_ROOT_DN_STRING),
            SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode("(objectclass=*)"),
            attributes,
            NO_CONTROL,
            null);
      waitOpResult(searchOp, ResultCode.SUCCESS);
      assertEquals(searchOp.getSearchEntries().size(), 1);

      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertEquals(entries.size(), 1);
      for (SearchResultEntry resultEntry : entries)
      {
          debugInfo(tn, "Result entry returned:" + resultEntry.toLDIFString());
          assertEquals(getAttributeValue(resultEntry, "firstchangenumber"),
                null);
          assertEquals(getAttributeValue(resultEntry, "lastchangenumber"),
                null);
          assertEquals(getAttributeValue(resultEntry, "changelog"),
                null);
          assertEquals(getAttributeValue(resultEntry, "lastExternalChangelogCookie"),
                null);
      }

      debugInfo(tn, "Ending test with success");
    }
    catch(Exception e)
    {
      fail("Ending "+tn+" test with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
  }

  private void ECLCompatTestLimits(int expectedFirst, int expectedLast,
      boolean eclEnabled)
  {
    String tn = "ECLCompatTestLimits";
    debugInfo(tn, "Starting test\n\n");
    try
    {
      LDIFWriter ldifWriter = getLDIFWriter();

      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      if (expectedFirst>0)
        attributes.add("firstchangenumber");
      attributes.add("lastchangenumber");
      attributes.add("changelog");
      attributes.add("lastExternalChangelogCookie");

      debugInfo(tn, " Search: rootDSE");
      InternalSearchOperation searchOp =
        connection.processSearch(
            ByteString.valueOf(""),
            SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            0, // Size limit
            0, // Time limit
            false, // Types only
            LDAPFilter.decode("(objectclass=*)"),
            attributes,
            NO_CONTROL,
            null);
      waitOpResult(searchOp, ResultCode.SUCCESS);
      assertEquals(searchOp.getSearchEntries().size(), 1);

      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertEquals(searchOp.getSearchEntries().size(), 1);
      if (entries != null)
      {
        for (SearchResultEntry resultEntry : entries)
        {
          debugInfo(tn, "Result entry returned:" + resultEntry.toLDIFString());
          ldifWriter.writeEntry(resultEntry);
          if (eclEnabled)
          {
            if (expectedFirst>0)
              checkValue(resultEntry,"firstchangenumber",
                String.valueOf(expectedFirst));
            checkValue(resultEntry,"lastchangenumber",
              String.valueOf(expectedLast));
            checkValue(resultEntry,"changelog",
              String.valueOf("cn=changelog"));
          }
          else
          {
            if (expectedFirst>0)
              assertEquals(getAttributeValue(resultEntry, "firstchangenumber"),
                null);
            assertEquals(getAttributeValue(resultEntry, "lastchangenumber"),
                null);
            assertEquals(getAttributeValue(resultEntry, "changelog"),
                null);
            assertEquals(getAttributeValue(resultEntry, "lastExternalChangelogCookie"),
                null);

          }
        }
      }
    }
    catch(Exception e)
    {
      fail("Ending "+tn+" test with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  private void ECLCompatTestLimitsAndAdd(int expectedFirst, int expectedLast,
      int ts)
  {
    String tn = "ECLCompatTestLimitsAndAdd";
    debugInfo(tn, "Starting test\n\n");
    try
    {
      ECLCompatTestLimits(expectedFirst, expectedLast, true);

      // Creates broker on o=test
      ReplicationBroker server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          100, replicationServerPort,
          brokerSessionTimeout, true);

      String user1entryUUID = "11111111-1112-1113-1114-111111111115";

      // Publish DEL
      ChangeNumber cn1 = new ChangeNumber(TimeThread.getTime(), ts++, 1201);
      DeleteMsg delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn1,
            user1entryUUID);
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());
      sleep(500);
      server01.stop();

      ECLCompatTestLimits(expectedFirst, expectedLast+1, true);

    }
    catch(Exception e)
    {
      fail("Ending "+tn+" test with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  private void ECLGetEligibleCountTest()
  {
    String tn = "ECLGetEligibleCountTest";
    debugInfo(tn, "Starting test\n\n");
    String user1entryUUID = "11111111-1112-1113-1114-111111111115";
    try
    {
      ReplicationServerDomain rsdtest =
        replicationServer.getReplicationServerDomain(TEST_ROOT_DN_STRING, false);

      // The replication changelog is empty
      long count = rsdtest.getEligibleCount(
          new ServerState(),
          new ChangeNumber(TimeThread.getTime(), 1, 1201));
      assertEquals(count, 0);

      // Creates broker on o=test
      ReplicationBroker server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING),  1201,
          1000, replicationServerPort,
          brokerSessionTimeout, true);

      // Publish one first message
      ChangeNumber cn1 = new ChangeNumber(TimeThread.getTime(), 1, 1201);
      DeleteMsg delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn1,
            user1entryUUID);
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());
      sleep(300);

      // From begin to now : 1 change
      count = rsdtest.getEligibleCount(
          new ServerState(),
          new ChangeNumber(TimeThread.getTime(), 1, 1201));
      assertEquals(count, 1);

      // Publish one second message
      ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(), 2, 1201);
      delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn2,
            user1entryUUID);
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());
      sleep(300);

      // From begin to now : 2 changes
      count = rsdtest.getEligibleCount(
          new ServerState(),
          new ChangeNumber(TimeThread.getTime(), 1, 1201));
      assertEquals(count, 2);

      // From begin to first change (inclusive) : 1 change = cn1
      count = rsdtest.getEligibleCount(
          new ServerState(),  cn1);
      assertEquals(count, 1);

      ServerState ss = new ServerState();
      ss.update(cn1);

      // From state/cn1(exclusive) to cn1 (inclusive) : 0 change
      count = rsdtest.getEligibleCount(ss, cn1);
      assertEquals(count, 0);

      // From state/cn1(exclusive) to cn2 (inclusive) : 1 change = cn2
      count = rsdtest.getEligibleCount(ss, cn2);
      assertEquals(count, 1);

      ss.update(cn2);

      // From state/cn2(exclusive) to now (inclusive) : 0 change
      count = rsdtest.getEligibleCount(ss,
          new ChangeNumber(TimeThread.getTime(), 4, 1201));
      assertEquals(count, 0);

      // Publish one third message
      ChangeNumber cn3 = new ChangeNumber(TimeThread.getTime(), 3, 1201);
      delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn3,
            user1entryUUID);
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());
      sleep(300);

      ss.update(cn2);

      // From state/cn2(exclusive) to now : 1 change = cn3
      count = rsdtest.getEligibleCount(ss,
          new ChangeNumber(TimeThread.getTime(), 4, 1201));
      assertEquals(count, 1);

      boolean perfs=false;
      if (perfs)
      {

      // number of msgs used by the test
      int maxMsg = 999999;

      // We need an RS configured with a window size bigger than the number
      // of msg used by the test.
      assertTrue(maxMsg<maxWindow);
      debugInfo(tn, "Perf test in compat mode - will generate " + maxMsg + " msgs.");
      for (int i=4; i<=maxMsg; i++)
      {
        ChangeNumber cnx = new ChangeNumber(TimeThread.getTime(), i, 1201);
        delMsg =
          new DeleteMsg("uid="+tn+i+"," + TEST_ROOT_DN_STRING, cnx,
              user1entryUUID);
        server01.publish(delMsg);
      }
      sleep(1000);
      debugInfo(tn, "Perfs test in compat - search lastChangeNumber");
      ArrayList<String> excludedDomains =
        MultimasterReplication.getECLDisabledDomains();
      if (!excludedDomains.contains(
          ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT))
        excludedDomains.add(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);

      ECLWorkflowElement eclwe = (ECLWorkflowElement)
      DirectoryServer.getWorkflowElement("EXTERNAL CHANGE LOG");
      ReplicationServer rs = eclwe.getReplicationServer();
      rs.disableEligibility(excludedDomains);
      long t1 = TimeThread.getTime();
      int[] limitss = replicationServer.getECLDraftCNLimits(
          replicationServer.getEligibleCN(), excludedDomains);
      assertEquals(limitss[1], maxMsg);
      long t2 = TimeThread.getTime();
      debugInfo(tn, "Perfs - " + maxMsg + " counted in (ms):" + (t2 - t1));

      try
      {
        // search on 'cn=changelog'
        LinkedHashSet<String> attributes = new LinkedHashSet<String>();
        attributes.add("+");
        attributes.add("*");

        String filter = "(changenumber>="+maxMsg+")";
        debugInfo(tn, " Search: " + filter);
        InternalSearchOperation searchOp =
          connection.processSearch(
              ByteString.valueOf("cn=changelog"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              0, // Size limit
              0, // Time limit
              false, // Types only
              LDAPFilter.decode(filter),
              attributes,
              NO_CONTROL,
              null);
        waitOpResult(searchOp, ResultCode.SUCCESS);
        long t3 = TimeThread.getTime();
        assertEquals(searchOp.getSearchEntries().size(), 1);
        debugInfo(tn, "Perfs - last change searched in (ms):" + (t3 - t2));

        filter = "(changenumber>="+maxMsg+")";
        debugInfo(tn, " Search: " + filter);
        searchOp =
          connection.processSearch(
              ByteString.valueOf("cn=changelog"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              0, // Size limit
              0, // Time limit
              false, // Types only
              LDAPFilter.decode(filter),
              attributes,
              NO_CONTROL,
              null);
        waitOpResult(searchOp, ResultCode.SUCCESS);
        long t4 = TimeThread.getTime();
        assertEquals(searchOp.getSearchEntries().size(), 1);
        debugInfo(tn, "Perfs - last change searched in (ms):" + (t4 - t3));

        filter = "(changenumber>="+(maxMsg-2)+")";
        debugInfo(tn, " Search: " + filter);
        searchOp =
          connection.processSearch(
              ByteString.valueOf("cn=changelog"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              0, // Size limit
              0, // Time limit
              false, // Types only
              LDAPFilter.decode(filter),
              attributes,
              NO_CONTROL,
              null);
        waitOpResult(searchOp, ResultCode.SUCCESS);
        long t5 = TimeThread.getTime();
        assertEquals(searchOp.getSearchEntries().size(), 3);
        debugInfo(tn, "Perfs - last 3 changes searched in (ms):" + (t5 - t4));
        if (searchOp.getSearchEntries() != null)
        {
          for (SearchResultEntry resultEntry : searchOp.getSearchEntries())
          {
            debugInfo(tn, "Result entry returned:" + resultEntry.toLDIFString());
          }
        }
      }
      catch(Exception e)
      {
        fail("Ending test "+tn+" with exception:\n"
            +  stackTraceToSingleLineString(e));
      }
      }
      server01.stop();

    }
    catch(Exception e)
    {
      fail("Ending "+tn+" test with exception:\n"
          +  stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ending test with success");
  }

  /**
   * Test ECl entry attributes, and there configuration.
   *
   */
  private void ECLIncludeAttributes()
  {
    String tn = "ECLIncludeAttributes";
    debugInfo(tn, "Starting test\n\n");
    Backend backend2 = null;
    Backend backend3 = null;
    DeleteOperationBasis delOp =null;
    LDAPReplicationDomain domain2 = null;
    LDAPReplicationDomain domain3 = null;
    LDAPReplicationDomain domain21 = null;
    DN baseDn2 = null;
    DN baseDn3 = null;
    try
    {
      // Initialize a second test backend o=test2, in addtion to o=test
      // Configure replication on this backend
      // Add the root entry in the backend
      backend2 = initializeTestBackend(false,
          TEST_ROOT_DN_STRING2, TEST_BACKEND_ID2);
      baseDn2 = DN.decode(TEST_ROOT_DN_STRING2);
      SortedSet<String> replServers = new TreeSet<String>();
      replServers.add("localhost:"+replicationServerPort);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn2, 1702, replServers);

      // on o=test2,sid=1702 include attrs set to : 'sn'
      SortedSet<String> eclInclude = new TreeSet<String>();
      eclInclude.add("sn");
      eclInclude.add("roomnumber");
      ExternalChangelogDomainFakeCfg eclCfg =
        new ExternalChangelogDomainFakeCfg(true, eclInclude, eclInclude);
      domainConf.setExternalChangelogDomain(eclCfg);
      // Set a Changetime heartbeat interval low enough (less than default
      // value that is 1000 ms) for the test to be sure to consider all changes
      // as eligible.
      domainConf.setChangetimeHeartbeatInterval(10);
      domain2 = MultimasterReplication.createNewDomain(domainConf);
      domain2.start();

      backend3 = initializeTestBackend(false,
          TEST_ROOT_DN_STRING3, TEST_BACKEND_ID3);
      baseDn3 = DN.decode(TEST_ROOT_DN_STRING3);
      domainConf =
        new DomainFakeCfg(baseDn3, 1703, replServers);

      // on o=test3,sid=1703 include attrs set to : 'objectclass'
      eclInclude = new TreeSet<String>();
      eclInclude.add("objectclass");

      TreeSet<String> eclIncludeForDeletes = new TreeSet<String>();
      eclIncludeForDeletes.add("*");

      eclCfg = new ExternalChangelogDomainFakeCfg(true, eclInclude, eclIncludeForDeletes);
      domainConf.setExternalChangelogDomain(eclCfg);
      // Set a Changetime heartbeat interval low enough (less than default
      // value that is 1000 ms) for the test to be sure to consider all changes
      // as eligible.
      domainConf.setChangetimeHeartbeatInterval(10);
      domain3 = MultimasterReplication.createNewDomain(domainConf);
      domain3.start();

      // on o=test2,sid=1704 include attrs set to : 'cn'
      domainConf =
        new DomainFakeCfg(baseDn2, 1704, replServers);
      eclInclude = new TreeSet<String>();
      eclInclude.add("cn");

      eclCfg =
        new ExternalChangelogDomainFakeCfg(true, eclInclude, eclInclude);
      domainConf.setExternalChangelogDomain(eclCfg);
      // Set a Changetime heartbeat interval low enough (less than default
      // value that is 1000 ms) for the test to be sure to consider all changes
      // as eligible.
      domainConf.setChangetimeHeartbeatInterval(10);
      domain21 = MultimasterReplication.createNewDomain(domainConf);
      domain21.start();

      sleep(1000);

      Entry e2 = createEntry(baseDn2);
      addEntry(e2);

      Entry e3 = createEntry(baseDn3);
      addEntry(e3);

      String lentry = new String(
          "dn: cn=Fiona Jensen," + TEST_ROOT_DN_STRING2 + "\n"
          + "objectclass: top\n"
          + "objectclass: person\n"
          + "objectclass: organizationalPerson\n"
          + "objectclass: inetOrgPerson\n"
          + "cn: Fiona Jensen\n"
          + "sn: Jensen\n"
          + "uid: fiona\n"
          + "telephonenumber: 12121212");

      Entry uentry1 = TestCaseUtils.entryFromLdifString(lentry);
      addEntry(uentry1); // add fiona in o=test2

      lentry = new String(
          "dn: cn=Robert Hue," + TEST_ROOT_DN_STRING3 + "\n"
          + "objectclass: top\n"
          + "objectclass: person\n"
          + "objectclass: organizationalPerson\n"
          + "objectclass: inetOrgPerson\n"
          + "cn: Robert Hue\n"
          + "sn: Robby\n"
          + "uid: robert\n"
          + "telephonenumber: 131313");
      Entry uentry2 = TestCaseUtils.entryFromLdifString(lentry);
      addEntry(uentry2); // add robert in o=test3

      // mod 'sn' of fiona (o=test2) with 'sn' configured as ecl-incl-att
      AttributeBuilder builder = new AttributeBuilder("sn");
      builder.add("newsn");
      Modification mod =
        new Modification(ModificationType.REPLACE, builder.toAttribute());
      List<Modification> mods = new ArrayList<Modification>();
      mods.add(mod);
      ModifyOperationBasis modOpBasis =
        new ModifyOperationBasis(connection, 1, 1, null, uentry1.getDN(), mods);
      modOpBasis.run();
      waitOpResult(modOpBasis, ResultCode.SUCCESS);

      // mod 'telephonenumber' of robert (o=test3)
      builder = new AttributeBuilder("telephonenumber");
      builder.add("555555");
      mod =
        new Modification(ModificationType.REPLACE, builder.toAttribute());
      mods = new ArrayList<Modification>();
      mods.add(mod);
      ModifyOperationBasis modOpBasis2 =
        new ModifyOperationBasis(connection, 1, 1, null, uentry2.getDN(), mods);
      modOpBasis2.run();
      waitOpResult(modOpBasis2, ResultCode.SUCCESS);

      // moddn robert (o=test3) to robert2 (o=test3)
      ModifyDNOperationBasis modDNOp = new ModifyDNOperationBasis(connection,
          InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(),
          null,
          DN.decode("cn=Robert Hue," + TEST_ROOT_DN_STRING3),
          RDN.decode("cn=Robert Hue2"), true,
          DN.decode(TEST_ROOT_DN_STRING3));
      modDNOp.run();
      waitOpResult(modDNOp, ResultCode.SUCCESS);

      // del robert (o=test3)
      delOp = new DeleteOperationBasis(connection,
          InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(), null,
          DN.decode("cn=Robert Hue2," + TEST_ROOT_DN_STRING3));
      delOp.run();
      waitOpResult(delOp, ResultCode.SUCCESS);

      getEntry(DN.decode("cn=Robert Hue2," + TEST_ROOT_DN_STRING3),5000,false);

      // Search on ECL from start on all suffixes
      String cookie = "";
      ExternalChangelogRequestControl control =
        new ExternalChangelogRequestControl(true,
            new MultiDomainServerState(cookie));
      ArrayList<Control> controls = new ArrayList<Control>(0);
      controls.add(control);
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      sleep(1000);
      debugInfo(tn, "Search with cookie=" + cookie);
      InternalSearchOperation searchOp = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*)"),
          attributes,
          controls,
          null);

      waitOpResult(searchOp, ResultCode.SUCCESS);
      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();

      sleep(2000);

      assertTrue(entries != null);
      String s = tn + " entries returned= ";
      if (entries != null)
      {
        for (SearchResultEntry resultEntry : entries)
        {
          // Expect
          debugInfo(tn, "Entry returned =" +  resultEntry.toLDIFString());
          s += "Entry:" + resultEntry.toLDIFString();

          String targetdn = getAttributeValue(resultEntry, "targetdn");

          if ((targetdn.endsWith("cn=robert hue,o=test3"))
            ||(targetdn.endsWith("cn=robert hue2,o=test3")))
          {
            Entry targetEntry = parseIncludedAttributes(resultEntry,
                targetdn);

            HashSet<String> eoc = new HashSet<String>();
            eoc.add("person");eoc.add("inetOrgPerson");eoc.add("organizationalPerson");eoc.add("top");
            checkValues(targetEntry,"objectclass",eoc);

            String changeType = getAttributeValue(resultEntry,
                "changetype");
            if (changeType.equals("delete"))
            {
              // We are using "*" for deletes so should get back 4 attributes.
              assertEquals(targetEntry.getAttributes().size(), 4);
              checkValue(targetEntry, "uid", "robert");
              checkValue(targetEntry, "cn", "Robert Hue2");
              checkValue(targetEntry, "telephonenumber", "555555");
              checkValue(targetEntry, "sn", "Robby");
            }
            else
            {
              assertEquals(targetEntry.getAttributes().size(), 0);
            }
          }
          if (targetdn.endsWith("cn=fiona jensen,o=test2"))
          {
            Entry targetEntry = parseIncludedAttributes(resultEntry,
                targetdn);

            assertEquals(targetEntry.getAttributes().size(), 2);
            checkValue(targetEntry,"sn","jensen");
            checkValue(targetEntry,"cn","Fiona Jensen");
          }
          checkValue(resultEntry,"changeinitiatorsname", "cn=Internal Client,cn=Root DNs,cn=config");
        }
      }
      assertEquals(entries.size(),8, "Entries number returned by search" + s);
    }
    catch(Exception e)
    {
      fail("End test "+tn+" with exception:\n" +
          stackTraceToSingleLineString(e));
    }
    finally
    {
      try
      {
        delOp = new DeleteOperationBasis(connection,
            InternalClientConnection.nextOperationID(),
            InternalClientConnection.nextMessageID(), null,
            DN.decode("cn=Fiona Jensen," + TEST_ROOT_DN_STRING2));
        delOp.run();
        waitOpResult(delOp, ResultCode.SUCCESS);
        delOp = new DeleteOperationBasis(connection,
            InternalClientConnection.nextOperationID(),
            InternalClientConnection.nextMessageID(), null,
            DN.decode(TEST_ROOT_DN_STRING2));
        delOp.run();
        waitOpResult(delOp, ResultCode.SUCCESS);
        delOp = new DeleteOperationBasis(connection,
            InternalClientConnection.nextOperationID(),
            InternalClientConnection.nextMessageID(), null,
            DN.decode(TEST_ROOT_DN_STRING3));
        delOp.run();
        waitOpResult(delOp, ResultCode.SUCCESS);

        // Cleaning
        if (domain21 != null)
          domain21.shutdown();

        if (domain2 != null)
          MultimasterReplication.deleteDomain(baseDn2);
        removeTestBackend2(backend2);

        if (domain3 != null)
          MultimasterReplication.deleteDomain(baseDn3);
        removeTestBackend2(backend3);

      }
      catch(Exception e) {
        fail("Ending test "+tn+" with exception in test cleanup:\n" +
            stackTraceToSingleLineString(e));
      }
      finally
      {
        replicationServer.clearDb();
      }
    }
    debugInfo(tn, "Ending test with success");
  }

  private Entry parseIncludedAttributes(
      SearchResultEntry resultEntry, String targetdn)
      throws Exception
  {
    // Parse includedAttributes as an entry.
    String includedAttributes = getAttributeValue(resultEntry, "includedattributes");
    String[] ldifAttributeLines = includedAttributes.split("\\n");
    String[] ldif = new String[ldifAttributeLines.length + 1];
    System.arraycopy(ldifAttributeLines, 0, ldif, 1, ldifAttributeLines.length);
    ldif[0] = "dn: " + targetdn;
    Entry targetEntry = TestCaseUtils.makeEntry(ldif);
    return targetEntry;
  }

  private void waitOpResult(Operation operation, ResultCode expectedResult)
  {
    int ii=0;
    while((operation.getResultCode()==ResultCode.UNDEFINED) ||
        (operation.getResultCode()!=expectedResult))
    {
      sleep(50);
      ii++;
      if (ii>10)
        assertEquals(operation.getResultCode(), expectedResult,
            operation.getErrorMessage().toString());
    }
  }
}
