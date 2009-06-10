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
package org.opends.server.replication.common;

import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.replication.protocol.OperationContext.SYNCHROCONTEXT;
import static org.opends.server.util.StaticUtils.createEntry;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.config.ConfigException;
import org.opends.server.controls.ExternalChangelogRequestControl;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.controls.PersistentSearchControl;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
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
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.plugin.DomainFakeCfg;
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
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.Attribute;
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
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
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

  public static final String TEST_ROOT_DN_STRING2 = "o=test2";
  public static final String TEST_BACKEND_ID2 = "test2";

  // The LDAPStatistics object associated with the LDAP connection handler.
  protected LDAPStatistics ldapStatistics;
  
  /**
   * Set up the environment for performing the tests in this Class.
   * Replication
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
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

    /*
    // Add the replication server to the configuration
    TestCaseUtils.dsconfig(
        "create-replication-server",
        "--provider-name", "Multimaster Synchronization",
        "--set", "replication-db-directory:" + "externalChangeLogTestConfigureDb",
        "--set", "replication-port:" + replicationServerPort,
        "--set", "replication-server-id:71");

    // Retrieves the replicationServer object
    DirectoryServer.getSynchronizationProviders();
    for (SynchronizationProvider<?> provider : DirectoryServer
        .getSynchronizationProviders()) {
      if (provider instanceof MultimasterReplication) {
        MultimasterReplication mmp = (MultimasterReplication) provider;
        ReplicationServerListener list = mmp.getReplicationServerListener();
        if (list != null) {
          replicationServer = list.getReplicationServer();
          if (replicationServer != null) {
            break;
          }
        }
      }
    }
    */
    ReplServerFakeConfiguration conf1 =
      new ReplServerFakeConfiguration(
          replicationServerPort, "ExternalChangeLogTestDb",
          0, 71, 0, 100, null);

    replicationServer = new ReplicationServer(conf1);;
    debugInfo("configure", "ReplicationServer created"+replicationServer);
    
  }

  /**
   * Launcher.
   */
  @Test(enabled=true)
  public void ECLReplicationServerTest()
  {
    ECLRemoteEmpty();replicationServer.clearDb();
    ECLDirectEmpty();replicationServer.clearDb();
    ECLDirectAllOps();replicationServer.clearDb();
    ECLRemoteNonEmpty();replicationServer.clearDb();
    ECLDirectMsg();replicationServer.clearDb();
    ECLDirectPsearch(true);replicationServer.clearDb();
    ECLDirectPsearch(false);replicationServer.clearDb();
    ECLPrivateDirectMsg();replicationServer.clearDb();
    // TODO:ECL Test SEARCH abandon and check everything shutdown and cleaned
    // TODO:ECL Test PSEARCH abandon and check everything shutdown and cleaned
    // TODO:ECL Test invalid DN in cookie returns UNWILLING + message
    // TODO:ECL Test notif control returned contains the cookie
    // TODO:ECL Test the attributes list and values returned in ECL entries
    // TODO:ECL Test search -s base, -s one
  }

  //=======================================================
  // From 3 remote ECL session,
  // Test DoneMsg is received from 1 suffix on each session.
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
          DN.decode("cn=changelog"), (short)1111,
          100, replicationServerPort, 1000, false);
      assertTrue(server1.isConnected());
      server2 = openReplicationSession(
          DN.decode("cn=changelog"), (short)2222,
          100, replicationServerPort,1000, false);
      assertTrue(server2.isConnected());
      server3 = openReplicationSession(
          DN.decode("cn=changelog"), (short)3333,
          100, replicationServerPort,1000, false);
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
          "Ending " + tn + " with incorrect message type :" +
          msg.getClass().getCanonicalName());
      assertTrue(msg instanceof DoneMsg);        

      // Test broker2 receives only Done
      msgc=0;
      do 
      {
        msg = server2.receive();
        msgc++;
      }
      while(!(msg instanceof DoneMsg));
      assertTrue(msgc==1,
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
          "Ending " + tn + " with incorrect message type :" +
          msg.getClass().getCanonicalName());

      server1.stop();
      server2.stop();
      server3.stop();
      sleep(500);
      debugInfo(tn, "Ending test successfully\n\n");      
    }
    catch(Exception e)
    {
      debugInfo(tn, "Ending test with exception:"
          +  stackTraceToSingleLineString(e));      
      assertTrue(e == null);
    }
  }
  
  //=======================================================
  // From 1 remote ECL session,
  // test simple update to be received from 2 suffixes
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
      // create 2 reguler brokers on the 2 suffixes
      server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING), (short) 1201, 
          100, replicationServerPort,
          1000, true);

      server02 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2), (short) 1202, 
          100, replicationServerPort,
          1000, true, EMPTY_DN_GENID);

      // create and publish 1 change on each suffix
      long time = TimeThread.getTime();
      int ts = 1;
      ChangeNumber cn1 = new ChangeNumber(time, ts++, (short)1201);
      DeleteMsg delMsg1 =
        new DeleteMsg("o=" + tn + "1," + TEST_ROOT_DN_STRING, cn1, "ECLBasicMsg1uid");
      server01.publish(delMsg1);
      debugInfo(tn, "publishes:" + delMsg1);

      ChangeNumber cn2 = new ChangeNumber(time, ts++, (short)1202);
      DeleteMsg delMsg2 =
        new DeleteMsg("o=" + tn + "2," + TEST_ROOT_DN_STRING2, cn2, "ECLBasicMsg2uid");
      server02.publish(delMsg2);
      debugInfo(tn, "publishes:" + delMsg2);

      // wait for the server to take these changes into account
      sleep(500);
      
      // open ECL broker
      serverECL = openReplicationSession(
          DN.decode("cn=changelog"), (short)10,
          100, replicationServerPort, 1000, false);
      assertTrue(serverECL.isConnected());

      // receive change 1 from suffix 1
      ReplicationMsg msg;
      msg = serverECL.receive();
      ECLUpdateMsg eclu = (ECLUpdateMsg)msg;
      UpdateMsg u = eclu.getUpdateMsg();
      debugInfo(tn, "RESULT:" + u.getChangeNumber());
      assertTrue(u.getChangeNumber().equals(cn1), "RESULT:" + u.getChangeNumber());
      assertTrue(eclu.getCookie().equalsTo(new MultiDomainServerState(
          "o=test:"+delMsg1.getChangeNumber()+";")));
      
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

      // clean
      serverECL.stop();
      server01.stop();
      server02.stop();      
      sleep(2000);
      debugInfo(tn, "Ending test successfully");
    }
    catch(Exception e)
    {
      debugInfo(tn, "Ending test with exception:"
          +  stackTraceToSingleLineString(e));      
      assertTrue(e == null);
    }
  }
  
  /**
   * From embedded ECL (no remote session)
   * With empty RS, simple search should return only root entry.
   */
  private void ECLDirectEmpty()
  {
    String tn = "ECLDirectEmpty";
    debugInfo(tn, "Starting test\n\n");

    try
    {
      // search on 'cn=changelog'
      InternalSearchOperation op = connection.processSearch(
        ByteString.valueOf("cn=changelog"),
        SearchScope.WHOLE_SUBTREE,
        LDAPFilter.decode("(objectclass=*)"));

      debugInfo(tn, "Res code=" + op.getResultCode());
      debugInfo(tn, "Res OE=" + ResultCode.OPERATIONS_ERROR);
      // Error because no cookie
      assertEquals(
          op.getResultCode(), ResultCode.OPERATIONS_ERROR,
          op.getErrorMessage().toString());

      // search on 'cn=changelog'
      InternalSearchOperation op2 = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          0, 0,
          false,
          LDAPFilter.decode("(objectclass=*)"),
          new LinkedHashSet<String>(0),
          getControls(""),
          null);

      // success
      assertEquals(
          op2.getResultCode(), ResultCode.SUCCESS,
          op2.getErrorMessage().toString());

      // root entry returned
      assertEquals(op2.getEntriesSent(), 1);
      debugInfo(tn, "Ending test successfully");
    }
    catch(LDAPException e)
    {
      debugInfo(tn, "Ending test with exception e="
          +  stackTraceToSingleLineString(e));      
      assertTrue(e == null);
    }
  }

  private ArrayList<Control> getControls(String cookie)
  {
    ExternalChangelogRequestControl control =
      new ExternalChangelogRequestControl(true, 
          new MultiDomainServerState(cookie));
    ArrayList<Control> controls = new ArrayList<Control>(0);
    controls.add(control);
    return controls;
  }

  // Utility - creates an LDIFWriter to dump result entries
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
    assertEquals(addOp.getResultCode(), ResultCode.SUCCESS,
        addOp.getErrorMessage().toString() + addOp.getAdditionalLogMessage());
    assertNotNull(getEntry(entry.getDN(), 1000, true));
  }

  //=======================================================
  // From embebbded ECL
  // Search ECL with 1 domain, public then private backend
  private void ECLPrivateDirectMsg()
  {
    ReplicationServer replServer = null;
    String tn = "ECLPrivateDirectMsg";
    debugInfo(tn, "Starting test");

    try
    {
      // Initialize a second test backend
      Backend backend2 = initializeTestBackend2(false);

      DN baseDn = DN.decode(TEST_ROOT_DN_STRING2);

      // configure and start replication of TEST_ROOT_DN_STRING on the server
      SortedSet<String> replServers = new TreeSet<String>();
      replServers.add("localhost:"+replicationServerPort);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, (short) 1602, replServers);
      LDAPReplicationDomain domain = MultimasterReplication.createNewDomain(domainConf);
      SynchronizationProvider replicationPlugin = new MultimasterReplication();
      replicationPlugin.completeSynchronizationProvider();
      sleep(1000);

      Entry e = createEntry(baseDn);
      addEntry(e);
      sleep(1000);

      ExternalChangelogRequestControl control =
        new ExternalChangelogRequestControl(true, 
            new MultiDomainServerState(""));
      ArrayList<Control> controls = new ArrayList<Control>(0);
      controls.add(control);

      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");
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
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS,
          searchOp.getErrorMessage().toString() + searchOp.getAdditionalLogMessage());

      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      assertTrue(entries != null);
      if (entries != null)
      {
        for (SearchResultEntry resultEntry : entries)
        {
          debugInfo(tn, "Result private entry=" + resultEntry.toLDIFString());
        }
      }
      assertEquals(entries.size(),1, "Entries number returned by search");
      
      // Same with private backend
      domain.getBackend().setPrivateBackend(true);

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
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS,
          searchOp.getErrorMessage().toString() + searchOp.getAdditionalLogMessage());

      entries = searchOp.getSearchEntries();
      assertTrue(entries != null);
      assertTrue(entries.size()==0);
      
      if (replServer != null)
        replServer.remove();

      if (domain != null)
        MultimasterReplication.deleteDomain(baseDn);

      if (replicationPlugin != null)
        DirectoryServer.deregisterSynchronizationProvider(replicationPlugin);

      removeTestBackend2(backend2);
    }
    catch(Exception e)
    {
      debugInfo(tn, "Ending test ECLDirectMsg with exception:\n"
          +  stackTraceToSingleLineString(e));      
      assertTrue(e == null);
    }
    debugInfo(tn, "Ending test successfully");
  }
  
  //=======================================================
  // From embebbded ECL
  // Search ECL with 4 messages on 2 suffixes from 2 brokers
  private void ECLDirectMsg()
  {
    String tn = "ECLDirectMsg";
    debugInfo(tn, "Starting test");
 
    try
    {
      // Initialize a second test backend
      Backend backend2 = initializeTestBackend2(true);

      //
      LDIFWriter ldifWriter = getLDIFWriter();

      // --
      ReplicationBroker s1test = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING), (short) 1201, 
          100, replicationServerPort,
          1000, true);

      ReplicationBroker s2test2 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2), (short) 1202, 
          100, replicationServerPort,
          1000, true, EMPTY_DN_GENID);
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

      cn = new ChangeNumber(time++, ts++, s2test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"3," + TEST_ROOT_DN_STRING2, cn, tn+"uuid3");
      s2test2.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      cn = new ChangeNumber(time++, ts++, s1test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"4," + TEST_ROOT_DN_STRING, cn, tn+"uuid4");
      s1test.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());
      sleep(500);

      // Changes are :
      //               s1          s2
      // o=test       msg1/msg4   
      // o=test2                 msg2/msg2
      String cookie= "";

      debugInfo(tn, "STEP 1 - from empty cookie("+cookie+")");

      // search on 'cn=changelog'
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");
      InternalSearchOperation searchOp = 
        connection.processSearch(
            ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES, 
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*direct*)"),
          attributes,
          getControls(cookie),
          null);

      // We expect SUCCESS and the 4 changes
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS,
          searchOp.getErrorMessage().toString());
      
      LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
      if (entries != null)
      {
        int i=0;
        for (SearchResultEntry entry : entries)
        {
          debugInfo(tn, " RESULT entry returned:" + entry.toSingleLineString());
          ldifWriter.writeEntry(entry);
          try
          {
            if (i++==2)
              cookie =
                entry.getAttribute("cookie").get(0).iterator().next().toString();
          }
          catch(NullPointerException e)
          {}
        }
      }
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS);
      
      // We expect the 4 changes
      assertEquals(searchOp.getSearchEntries().size(), 4);

      // Now start from last cookie and expect to get the last change
      // search on 'cn=changelog'
      attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      debugInfo(tn, "STEP 2 - from cookie" + cookie);

      ExternalChangelogRequestControl control =
        new ExternalChangelogRequestControl(true, 
            new MultiDomainServerState(cookie));
      ArrayList<Control> controls = new ArrayList<Control>(0);
      controls.add(control);

      searchOp = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES, 
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*direct*)"),
          attributes,
          controls,
          null);
      
      
      // We expect SUCCESS and the 4th change
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS,
          searchOp.getErrorMessage().toString() + searchOp.getAdditionalLogMessage());
      
      cookie= "";
      entries = searchOp.getSearchEntries();
      if (entries != null)
      {
        for (SearchResultEntry entry : entries)
        {
          debugInfo(tn, "Result entry=\n" + entry.toLDIFString());
          ldifWriter.writeEntry(entry);
          try
          {
          cookie =
            entry.getAttribute("cookie").get(0).iterator().next().toString();
          }
          catch(NullPointerException e)
          {}
        }
      }

      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS);
      
      // we expect msg4
      assertEquals(searchOp.getSearchEntries().size(), 1);
      
      debugInfo(tn, "STEP 3 - from cookie" + cookie);

      cn = new ChangeNumber(time++, ts++, s1test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"5," + TEST_ROOT_DN_STRING, cn, tn+"uuid5");
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

      searchOp = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES, 
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*direct*)"),
          attributes,
          controls,
          null);

      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS,
          searchOp.getErrorMessage().toString() + searchOp.getAdditionalLogMessage());
      
      cookie= "";
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
            resultEntry.getAttribute("cookie").get(0).iterator().next().toString();
          }
          catch(NullPointerException e)
          {}
        }
      }
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS);

      // we expect 1 entries : msg5
      assertEquals(searchOp.getSearchEntries().size(), 1);

      cookie="";
      debugInfo(tn, "STEP 4 - [filter:o=test cookie:" + cookie + "]");

      control =
        new ExternalChangelogRequestControl(true, 
            new MultiDomainServerState(cookie));
      controls = new ArrayList<Control>(0);
      controls.add(control);

      searchOp = connection.processSearch(
          ByteString.valueOf("cn=changelog"),
          SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES, 
          0, // Size limit
          0, // Time limit
          false, // Types only
          LDAPFilter.decode("(targetDN=*direct*,o=test)"),
          attributes,
          controls,
          null);
      
      
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS,
          searchOp.getErrorMessage().toString() + searchOp.getAdditionalLogMessage());
      
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
            resultEntry.getAttribute("cookie").get(0).iterator().next().toString();
          }
          catch(NullPointerException e)
          {}
        }
      }
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS);

      // we expect msg1 + msg4 + msg5
      assertEquals(searchOp.getSearchEntries().size(), 3);

      // --
      ReplicationBroker s1test2 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING2), (short) 1203, 
          100, replicationServerPort,
          1000, true, EMPTY_DN_GENID);

      ReplicationBroker s2test = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING), (short) 1204, 
          100, replicationServerPort,
          1000, true);
      sleep(500);

      // Test startState of the domain for the first cookie feature
      time = TimeThread.getTime();
      cn = new ChangeNumber(time++, ts++, s1test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"6," + TEST_ROOT_DN_STRING2, cn, tn+"uuid6");
      s1test2.publish(delMsg);

      cn = new ChangeNumber(time++, ts++, s2test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"7," + TEST_ROOT_DN_STRING, cn, tn+"uuid7");
      s2test.publish(delMsg);

      cn = new ChangeNumber(time++, ts++, s1test2.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"8," + TEST_ROOT_DN_STRING2, cn, tn+"uuid8");
      s1test2.publish(delMsg);

      cn = new ChangeNumber(time++, ts++, s2test.getServerId());
      delMsg =
        new DeleteMsg("uid="+tn+"9," + TEST_ROOT_DN_STRING, cn, tn+"uuid9");
      s2test.publish(delMsg);
      sleep(500);

      ReplicationServerDomain rsd =
        replicationServer.getReplicationServerDomain(TEST_ROOT_DN_STRING, false);
      ServerState startState = rsd.getStartState();
      assertTrue(startState.getMaxChangeNumber(s1test.getServerId()).getSeqnum()==1);
      assertTrue(startState.getMaxChangeNumber(s2test.getServerId()).getSeqnum()==7);

      rsd =
        replicationServer.getReplicationServerDomain(TEST_ROOT_DN_STRING2, false);
      startState = rsd.getStartState();
      assertTrue(startState.getMaxChangeNumber(s2test2.getServerId()).getSeqnum()==2);
      assertTrue(startState.getMaxChangeNumber(s1test2.getServerId()).getSeqnum()==6);
      
      s1test.stop();
      s1test2.stop();
      s2test.stop();
      s2test2.stop();
      
      removeTestBackend2(backend2);
    }
    catch(Exception e)
    {
      debugInfo(tn, "Ending test ECLDirectMsg with exception:\n"
          +  stackTraceToSingleLineString(e));      
      assertTrue(e == null);
    }
    debugInfo(tn, "Ending test successfully");
  }

  // simple update to be received
  private void ECLDirectAllOps()
  {
    String tn = "ECLDirectAllOps";
    debugInfo(tn, "Starting test\n\n");
 
    try
    {
      LDIFWriter ldifWriter = getLDIFWriter();

      // Creates broker on o=test
      ReplicationBroker server01 = openReplicationSession(
          DN.decode(TEST_ROOT_DN_STRING), (short) 1201, 
          100, replicationServerPort,
          1000, true);
      int ts = 1;

      String user1entryUUID = "11111111-1111-1111-1111-111111111111";
      String baseUUID       = "22222222-2222-2222-2222-222222222222";


      // Publish DEL
      ChangeNumber cn1 = new ChangeNumber(TimeThread.getTime(), ts++, (short)1201);
      DeleteMsg delMsg =
        new DeleteMsg("uid="+tn+"1," + TEST_ROOT_DN_STRING, cn1, tn+"uuid1");
      server01.publish(delMsg);
      debugInfo(tn, " publishes " + delMsg.getChangeNumber());

      // Publish ADD
      ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(), ts++, (short)1201);
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

      // Publish MOD
      ChangeNumber cn3 = new ChangeNumber(TimeThread.getTime(), ts++, (short)1201);
      Attribute attr1 = Attributes.create("description", "new value");
      Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
      List<Modification> mods = new ArrayList<Modification>();
      mods.add(mod1);
      ModifyMsg modMsg = new ModifyMsg(cn3, DN
          .decode("uid="+tn+"3," + TEST_ROOT_DN_STRING), mods, tn+"uuid3");
      server01.publish(modMsg);
      debugInfo(tn, " publishes " + modMsg.getChangeNumber());

      // Publish modDN
      ChangeNumber cn4 = new ChangeNumber(TimeThread.getTime(), ts++, (short)1201);
      ModifyDNOperationBasis op = new ModifyDNOperationBasis(connection, 1, 1, null,
          DN.decode("uid="+tn+"4," + TEST_ROOT_DN_STRING), // entryDN
          RDN.decode("uid="+tn+"new4"), // new rdn
          true,  // deleteoldrdn
          DN.decode(TEST_ROOT_DN_STRING2)); // new superior
      op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(cn4, tn+"uuid4",
      "newparentId"));
      LocalBackendModifyDNOperation localOp = new LocalBackendModifyDNOperation(op);
      ModifyDNMsg modDNMsg = new ModifyDNMsg(localOp);
      server01.publish(modDNMsg);
      debugInfo(tn, " publishes " + modDNMsg.getChangeNumber());
      sleep(600);

      String cookie= "";

      // search on 'cn=changelog'
      debugInfo(tn, "STEP 1 - from empty cookie("+cookie+")");
      LinkedHashSet<String> attributes = new LinkedHashSet<String>();
      attributes.add("+");
      attributes.add("*");

      ExternalChangelogRequestControl control =
        new ExternalChangelogRequestControl(true, 
            new MultiDomainServerState());
      ArrayList<Control> controls = new ArrayList<Control>(0);
      controls.add(control);

      InternalSearchOperation searchOp = 
        connection.processSearch(
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
      sleep(500);

      // test success
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS,
          searchOp.getErrorMessage().toString());
      
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
            checkValue(resultEntry,"replicationcsn",cn1.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"1," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","delete");
            checkValue(resultEntry,"cookie","o=test:"+cn1.toString()+";");
            checkValue(resultEntry,"targetentryuuid",tn+"uuid1");
          } else if (i==2)
          {
            // check the ADD entry has the right content
            String expectedValue1 = "objectClass: domain\nobjectClass: top\n" +
            "entryUUID: 11111111-1111-1111-1111-111111111111\n\n";
            String expectedValue2 = "entryUUID: 11111111-1111-1111-1111-111111111111\n" +
            "objectClass: domain\nobjectClass: top\n\n";
            checkPossibleValues(resultEntry,"changes",expectedValue1, expectedValue2);
            checkValue(resultEntry,"replicationcsn",cn2.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"2," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","add");
            checkValue(resultEntry,"cookie","o=test:"+cn2.toString()+";");
            checkValue(resultEntry,"targetentryuuid",user1entryUUID);

          } else if (i==3)
          {
            // check the MOD entry has the right content
            String expectedValue = "replace: description\n" +
            "description: new value\n-\n";
            checkValue(resultEntry,"changes",expectedValue);
            checkValue(resultEntry,"replicationcsn",cn3.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"3," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","modify");
            checkValue(resultEntry,"cookie","o=test:"+cn3.toString()+";");
            checkValue(resultEntry,"targetentryuuid",tn+"uuid3");
          } else if (i==4)
          {
            // check the MODDN entry has the right content
            checkValue(resultEntry,"replicationcsn",cn4.toString());
            checkValue(resultEntry,"replicaidentifier","1201");
            checkValue(resultEntry,"targetdn","uid="+tn+"4," + TEST_ROOT_DN_STRING);
            checkValue(resultEntry,"changetype","modrdn");
            checkValue(resultEntry,"cookie","o=test:"+cn4.toString()+";");
            checkValue(resultEntry,"targetentryuuid",tn+"uuid4");
            checkValue(resultEntry,"newrdn","uid=ECLDirectAllOpsnew4");            
            checkValue(resultEntry,"newsuperior",TEST_ROOT_DN_STRING2);
            checkValue(resultEntry,"deleteoldrdn","true");
          }
        }
      }
      server01.stop();
    }
    catch(Exception e)
    {
      debugInfo(tn, "Ending test with exception:\n"
          +  stackTraceToSingleLineString(e));      
      assertTrue(e == null);
    }
    debugInfo(tn, "Ending test with success");
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
  
  /**
   * Test persistent search
   */
  private void ECLDirectPsearch(boolean changesOnly)
  {
    String tn = "ECLDirectPsearch_" + String.valueOf(changesOnly);
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
          DN.decode(TEST_ROOT_DN_STRING), (short) 1201, 
          100, replicationServerPort,
          1000, true);
      int ts = 1;

      // Produce update on this suffix
      ChangeNumber cn = new ChangeNumber(TimeThread.getTime(), ts++, (short)1201);
      DeleteMsg delMsg =
        new DeleteMsg("uid=" + tn + "1," + TEST_ROOT_DN_STRING, cn, tn+"uuid1");
      debugInfo(tn, " publishing " + delMsg.getChangeNumber());
      server01.publish(delMsg);
      this.sleep(500); // let's be sure the message is in the RS

      // Creates cookie control
      ArrayList<Control> controls = getControls("");

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
      s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
      LDAPWriter w = new LDAPWriter(s);
      s.setSoTimeout(1500000);
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

      debugInfo(tn, "Sending the PSearch request filter=(targetDN=*"+tn+"*,o=test)");
      LDAPMessage message;
      message = new LDAPMessage(2, searchRequest, controls);
      w.writeMessage(message);
      this.sleep(500);

      SearchResultEntryProtocolOp searchResultEntry = null;
      SearchResultDoneProtocolOp searchResultDone = null;

      if (changesOnly == false)
      {
        // Wait for change 1
        debugInfo(tn, "Waiting for : INIT search expected to return change 1");
        searchEntries = 0;
        message = null;
        
        try
        {
          while ((searchEntries<1) && (message = r.readMessage()) != null)
          {
            debugInfo(tn, "First search returns " + 
                message.getProtocolOpType() + message + " " + searchEntries);
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
//            assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
              searchesDone++;
              break;
            }
          }
        }
        catch(Exception e)
        {
          debugInfo(tn, "INIT search failed with e=" +
              stackTraceToSingleLineString(e));        
        }
        debugInfo(tn, "INIT search done with success. searchEntries="
            + searchEntries + " searchesDone="+ searchesDone);
      }

      // Produces change 2
      cn = new ChangeNumber(TimeThread.getTime(), ts++, (short)1201);
      String expectedDn = "uid=" + tn + "2," +  TEST_ROOT_DN_STRING;
      delMsg = new DeleteMsg(expectedDn, cn, tn + "uuid2");
      debugInfo(tn, " publishing " + delMsg.getChangeNumber());
      server01.publish(delMsg);
      this.sleep(1000);

      debugInfo(tn, delMsg.getChangeNumber() +
          " published , will wait for new entries (Persist)");

      // wait for the 1 new entry
      searchEntries = 0;
      searchResultEntry = null;
      searchResultDone = null;
      message = null;
      while ((searchEntries<1) && (message = r.readMessage()) != null)
      {
        debugInfo(tn, "2nd search returns " + 
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
      sleep(1000);

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
      s.setSoTimeout(1500000);
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
      message = new LDAPMessage(2, searchRequest, getControls(""));
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
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
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
      sleep(1000);
    }
    catch(Exception e)
    {
      assertTrue(e==null, stackTraceToSingleLineString(e));
    }
    debugInfo(tn, "Ends test successfuly");
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
    if (replicationServer != null)
      replicationServer.remove();
    /*
    TestCaseUtils.dsconfig(
        "delete-replication-server",
        "--provider-name", "Multimaster Synchronization");
        */
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
    //if (debugEnabled())
    {
      logError(Message.raw(Category.SYNC, Severity.NOTICE,
          "** TEST " + tn + " ** " + s));
      TRACER.debugInfo("** TEST " + tn + " ** " + s);
    }
  }

  /**
   * Utility - create a second backend in order to test ECL with 2 suffixes.
   */
  private static Backend initializeTestBackend2(boolean createBaseEntry)
  throws IOException, InitializationException, ConfigException,
         DirectoryException
  {

    DN baseDN = DN.decode(TEST_ROOT_DN_STRING2);

    //  Retrieve backend. Warning: it is important to perform this each time,
    //  because a test may have disabled then enabled the backend (i.e a test
    //  performing an import task). As it is a memory backend, when the backend
    //  is re-enabled, a new backend object is in fact created and old reference
    //  to memory backend must be invalidated. So to prevent this problem, we
    //  retrieve the memory backend reference each time before cleaning it.
    MemoryBackend memoryBackend =
      (MemoryBackend)DirectoryServer.getBackend(TEST_BACKEND_ID2);

    if (memoryBackend == null)
    {
      memoryBackend = new MemoryBackend();
      memoryBackend.setBackendID(TEST_BACKEND_ID2);
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
    memoryBackend.finalizeBackend();
    DirectoryServer.deregisterBackend(memoryBackend);
  }
}