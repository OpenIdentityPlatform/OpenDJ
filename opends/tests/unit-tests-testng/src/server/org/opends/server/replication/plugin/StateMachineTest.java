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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.text.html.HTMLDocument.HTMLReader.IsindexAction;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.types.DirectoryException;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.admin.std.server.SynchronizationProviderCfg;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ResetGenerationIdMsg;
import org.opends.server.replication.protocol.RoutableMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Some tests to go through the DS state machine and validate we get the
 * expected status according to the actions we perform.
 */
public class StateMachineTest extends ReplicationTestCase
{

  private static final String EXAMPLE_DN = "dc=example,dc=com";  // Server id definitions

  private static final short DS1_ID = 1;
  private static final short DS2_ID = 2;
  private static final short DS3_ID = 3;
  private static final short RS1_ID = 41;
  private int rs1Port = -1;
  private LDAPReplicationDomain ds1 = null;
  private ReplicationBroker ds2 = null;
  private ReplicationBroker ds3 = null;
  private ReplicationServer rs1 = null;
  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  private void debugInfo(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST **" + s);
    }
  }

  private void debugInfo(String message, Exception e)
  {
    debugInfo(message + stackTraceToSingleLineString(e));
  }

  private void initTest()
  {
    rs1Port = -1;
    ds1 = null;
    ds2 = null;
    ds3 = null;
    findFreePorts();
  }

  private void endTest()
  {
    if (ds1 != null)
    {
      ds1.shutdown();
      ds1 = null;
    }

    try
    {
      // Clear any reference to a domain in synchro plugin
      MultimasterReplication.deleteDomain(DN.decode(EXAMPLE_DN));
    } catch (DirectoryException ex)
    {
      fail("Error deleting reference to domain: " + EXAMPLE_DN);
    }

    if (ds2 != null)
    {
      ds2.stop();
      ds2 = null;
    }

     if (ds3 != null)
    {
      ds3.stop();
      ds3 = null;
    }

    if (rs1 != null)
    {
      rs1.clearDb();
      rs1.remove();
      rs1 = null;
    }

    rs1Port = -1;
  }

  private void sleep(long time)
  {
    try
    {
      Thread.sleep(time);
    } catch (InterruptedException ex)
    {
      fail("Error sleeping " + stackTraceToSingleLineString(ex));
    }
  }

  /**
   * Check connection of the provided ds to the
   * replication server. Waits for connection to be ok up to secTimeout seconds
   * before failing.
   */
  private void checkConnection(int secTimeout, short dsId)
  {

    ReplicationBroker rb = null;
    LDAPReplicationDomain rd = null;
    switch (dsId)
    {
      case DS1_ID:
        rd = ds1;
        break;
      case DS2_ID:
        rb = ds2;
        break;
      case DS3_ID:
        rb = ds3;
        break;
      default:
        fail("Unknown ds server id.");
    }

    int nSec = 0;

    // Go out of the loop only if connection is verified or if timeout occurs
    while (true)
    {
      // Test connection
      boolean connected = false;
      if (rd != null)
        connected = rd.isConnected();
      else
        connected = rb.isConnected();

      if (connected)
      {
        // Connection verified
        debugInfo("checkConnection: connection of DS " + dsId +
          " to RS obtained after " + nSec + " seconds.");
        return;
      }

      // Sleep 1 second
      try
      {
        Thread.sleep(100);
      } catch (InterruptedException ex)
      {
        fail("Error sleeping " + stackTraceToSingleLineString(ex));
      }
      nSec++;

      if (nSec > secTimeout*10)
      {
        // Timeout reached, end with error
        fail("checkConnection: DS " + dsId + " is not connected to the RS after "
          + secTimeout + " seconds.");
      }
    }
  }

  /**
   * Find needed free TCP ports.
   */
  private void findFreePorts()
  {
    try
    {
      ServerSocket socket1 = TestCaseUtils.bindFreePort();
      rs1Port = socket1.getLocalPort();
      socket1.close();
    } catch (IOException e)
    {
      fail("Unable to determinate some free ports " +
        stackTraceToSingleLineString(e));
    }
  }

  /**
   * Creates a new ReplicationServer.
   */
  private ReplicationServer createReplicationServer(String testCase,
    int degradedStatusThreshold)
  {
    try
    {
      SortedSet<String> replServers = new TreeSet<String>();

      String dir = "stateMachineTest" + RS1_ID + testCase + "Db";
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(rs1Port, dir, 0, RS1_ID, 0, 100,
        replServers, 1, 1000, degradedStatusThreshold);
      ReplicationServer replicationServer = new ReplicationServer(conf);
      return replicationServer;

    } catch (Exception e)
    {
      fail("createReplicationServer " + stackTraceToSingleLineString(e));
    }
    return null;
  }

  /**
   * Creates and starts a new ReplicationDomain configured for the replication
   * server
   */
  @SuppressWarnings("unchecked")
  private LDAPReplicationDomain createReplicationDomain(short dsId)
  {
    try
    {
      SortedSet<String> replServers = new TreeSet<String>();
      replServers.add("localhost:" + rs1Port);

      DN baseDn = DN.decode(EXAMPLE_DN);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, dsId, replServers);
      LDAPReplicationDomain replicationDomain =
        MultimasterReplication.createNewDomain(domainConf);
      replicationDomain.start();
      SynchronizationProvider<SynchronizationProviderCfg> provider =
        DirectoryServer.getSynchronizationProviders().get(0);
      if (provider instanceof ConfigurationChangeListener)
      {
        ConfigurationChangeListener<MultimasterReplicationFakeConf> mmr =
          (ConfigurationChangeListener<MultimasterReplicationFakeConf>) provider;
        mmr.applyConfigurationChange(new MultimasterReplicationFakeConf());
      }

      return replicationDomain;

    } catch (Exception e)
    {
      fail("createReplicationDomain " + stackTraceToSingleLineString(e));
    }
    return null;
  }

  /**
   * Create and connect a replication broker to the replication server with
   * the given state and generation id (uses passed window for received changes)
   */
  private ReplicationBroker createReplicationBroker(short dsId,
    ServerState state, long generationId, int window)
    throws Exception, SocketException
  {
    ReplicationBroker broker = new ReplicationBroker(null,
      state, EXAMPLE_DN, dsId, 100, generationId, 0,
      new ReplSessionSecurity(null, null, null, true), (byte) 1);
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:" + rs1Port);
    broker.start(servers);

    return broker;
  }

  /**
   * Create and connect a replication broker to the replication server with
   * the given state and generation id (uses 100 as window for received changes)
   */
  private ReplicationBroker createReplicationBroker(short dsId,
    ServerState state, long generationId)
    throws Exception, SocketException
  {
    return createReplicationBroker(dsId, state, generationId, 100);
  }

  /**
   * Make simple state machine test.
   *
   * NC = Not connected status
   * N = Normal status
   * D = Degraded status
   * FU = Full update status
   * BG = Bad generation id status
   *
   * The test path should be:
   * ->NC->N->NC
   * @throws Exception If a problem occurred
   */
  @Test(enabled=true)
  public void testStateMachineBasic() throws Exception
  {
    String testCase = "testStateMachineBasic";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {

      /**
       * DS1 start, no RS available: DS1 should be in not connected status
       */
      ds1 = createReplicationDomain(DS1_ID);
      sleepAssertStatusEquals(30, ds1, ServerStatus.NOT_CONNECTED_STATUS);

      /**
       * RS1 starts , DS1 should connect to it and be in normal status
       */
      rs1 = createReplicationServer(testCase, 5000);
      sleepAssertStatusEquals(30, ds1, ServerStatus.NORMAL_STATUS);

      /**
       * RS1 stops, DS1 should go in not connected status
       */
      rs1.remove();
      sleepAssertStatusEquals(30, ds1, ServerStatus.NOT_CONNECTED_STATUS);

    } finally
    {
      endTest();
    }
  }

  // Returns various init values for test testStateMachineStatusAnalyzer
  @DataProvider(name="stateMachineStatusAnalyzerTestProvider")
  public Object [][] stateMachineStatusAnalyzerTestProvider() throws Exception
  {
    return new Object [][] { {1} , {10}, {50}, {120} };
  }

  /**
   * Test the status analyzer system that allows to go from normal to degraded
   * and vice versa, using the configured threshold value
   *
   * NC = Not connected status
   * N = Normal status
   * D = Degraded status
   * FU = Full update status
   * BG = Bad generation id status
   *
   * Expected path:
   * ->NC->N->D->N->NC
   * @throws Exception If a problem occurred
   */
  @Test(enabled=true, groups="slow", dataProvider="stateMachineStatusAnalyzerTestProvider")
  public void testStateMachineStatusAnalyzer(int thresholdValue) throws Throwable
  {
    String testCase = "testStateMachineStatusAnalyzer with threhold " + thresholdValue;

    debugInfo("Starting " + testCase + " with " + thresholdValue);

      initTest();

      BrokerReader br3 = null;
      BrokerReader br2 = null;
      BrokerWriter bw = null;

    try
    {
      /**
       * RS1 starts with specified threshold value
       */
      rs1 = createReplicationServer(testCase, thresholdValue);

      /**
       * DS2 starts and connects to RS1. No reader and low window value at the
       * beginning so writer for DS2 in RS should enqueue changes after first
       * changes sent to DS. (window value reached: a window msg needed by RS for
       * following sending changes to DS)
       */
      ds2 = createReplicationBroker(DS2_ID, new ServerState(), EMPTY_DN_GENID, 10);
      checkConnection(30, DS2_ID);

      /**
       * DS3 starts and connects to RS1
       */
      ds3 = createReplicationBroker(DS3_ID, new ServerState(), EMPTY_DN_GENID);
      br3 = new BrokerReader(ds3, DS3_ID);
      checkConnection(30, DS3_ID);

      // Send first changes to reach window and block DS2 writer queue. Writer will take them
      // from queue and block (no more changes removed from writer queue) after
      // having sent them to TCP receive queue of DS2.
      bw = new BrokerWriter(ds3, DS3_ID, false);
      bw.followAndPause(11);
      // sleep(1000);

      /**
       * DS3 sends changes (less than threshold): DS2 should still be in normal
       * status so no topo message should be sent (update topo message
       * for telling status of DS2 changed)
       */
      int nChangesSent = 0;
      if (thresholdValue > 1)
      {
        nChangesSent = thresholdValue - 1;
        bw.followAndPause(nChangesSent);
        sleep(1000); // Be sure status analyzer has time to test
        ReplicationMsg msg = br3.getLastMsg();
        debugInfo(testCase + " Step 1: last message from writer: " + msg);
        assertTrue(msg == null, (msg != null) ? msg.toString() : "null" );
      }

      /**
       * DS3 sends changes to reach the threshold value, DS3 should receive an
       * update topo message with status of DS2: degraded status
       */
      bw.followAndPause(thresholdValue - nChangesSent);
      // wait for a status MSG status analyzer to broker 3
      ReplicationMsg lastMsg = null;
      for (int count = 0; count< 50; count++)
      {
        List<DSInfo> dsList = ds3.getDsList();
        DSInfo ds3Info = null;
        if (dsList.size() > 0)
        {
          ds3Info = dsList.get(0);
        }
        if ((ds3Info != null) && (ds3Info.getDsId() == DS2_ID) &&
            (ds3Info.getStatus()== ServerStatus.DEGRADED_STATUS) )
        {
          break;
        }
        else
        {
          if (count < 50)
            sleep(200); // Be sure status analyzer has time to test
          else
            fail("DS2 did not get degraded : " + ds3Info);
        }
      }

      /**
       * DS3 sends 10 additional changes after threshold value, DS2 should still be
       * degraded so no topo message received.
       */
      bw.followAndPause(10);
      bw.shutdown();
      sleep(1000); // Be sure status analyzer has time to test
      lastMsg = br3.getLastMsg();
      ReplicationMsg msg = br3.getLastMsg();
      debugInfo(testCase + " Step 3: last message from writer: " + msg);
      assertTrue(lastMsg == null);

      /**
       * DS2 replays every changes and should go back to normal status
       * (create a reader to emulate replay of messages (messages read from queue))
       */
      br2 = new BrokerReader(ds2, DS2_ID);
      // wait for a status MSG status analyzer to broker 3
      for (int count = 0; count< 50; count++)
      {
        List<DSInfo> dsList = ds3.getDsList();
        DSInfo ds3Info = null;
        if (dsList.size() > 0)
        {
          ds3Info = dsList.get(0);
        }
        if ((ds3Info != null) && (ds3Info.getDsId() == DS2_ID) &&
            (ds3Info.getStatus()== ServerStatus.DEGRADED_STATUS) )
        {
          break;
        }
        else
        {
          if (count < 50)
            sleep(200); // Be sure status analyzer has time to test
          else
            fail("DS2 did not get degraded.");
        }
      }

    } finally
    {
      endTest();
      if (bw != null) bw.shutdown();
      if (br3 != null) br3.shutdown();
      if (br2 != null) br2.shutdown();
    }
  }

  /**
   * Go through the possible state machine transitions:
   *
   * NC = Not connected status
   * N = Normal status
   * D = Degraded status
   * FU = Full update status
   * BG = Bad generation id status
   *
   * The test path should be:
   * ->NC->D->N->NC->N->D->NC->D->N->BG->NC->N->D->BG->FU->NC->N->D->FU->NC->BG->NC->N->FU->NC->N->NC
   * @throws Exception If a problem occurred
   */
  @Test(enabled = false, groups = "slow")
  public void testStateMachineFull() throws Exception
  {
    String testCase = "testStateMachineFull";

    debugInfo("Starting " + testCase);

    initTest();
    BrokerReader br = null;
    BrokerWriter bw = null;

    try
    {

      int DEGRADED_STATUS_THRESHOLD = 1;

      /**
       * RS1 starts with 1 message as degraded status threshold value
       */
      rs1 = createReplicationServer(testCase, DEGRADED_STATUS_THRESHOLD);

      /**
       * DS2 starts and connects to RS1
       */
      ds2 = createReplicationBroker(DS2_ID, new ServerState(), EMPTY_DN_GENID);
      br = new BrokerReader(ds2, DS2_ID);
      checkConnection(30, DS2_ID);

      /**
       * DS2 starts sending a lot of changes
       */
      bw = new BrokerWriter(ds2, DS2_ID, false);
      bw.follow();
      sleep(1000); // Let some messages being queued in RS

      /**
       * DS1 starts and connects to RS1, server state exchange should lead to
       * start in degraded status as some changes should be in queued in the RS
       * and the threshold value is 1 change in queue.
       */
      ds1 = createReplicationDomain(DS1_ID);
      checkConnection(30, DS1_ID);
      sleepAssertStatusEquals(30, ds1, ServerStatus.DEGRADED_STATUS);

      /**
       * DS2 stops sending changes: DS1 should replay pending changes and should
       * enter the normal status
       */
      bw.pause();
      // Sleep enough so that replay can be done and analyzer has time
      // to see that the queue length is now under the threshold value.
      sleepAssertStatusEquals(30, ds1, ServerStatus.NORMAL_STATUS);

      /**
       * RS1 stops to make DS1 go to not connected status (from normal status)
       */
      rs1.remove();
      sleepAssertStatusEquals(30, ds1, ServerStatus.NOT_CONNECTED_STATUS);

      /**
       * DS2 restarts with up to date server state (this allows to have
       * restarting RS1 not sending him some updates he already sent)
       */
      ds2.stop();
      bw.shutdown();
      br.shutdown();
      ServerState curState = ds1.getServerState();
      ds2 = createReplicationBroker(DS2_ID, curState, EMPTY_DN_GENID);
      br = new BrokerReader(ds2, DS2_ID);

      /**
       * RS1 restarts, DS1 should get back to normal status
       */
      rs1 = createReplicationServer(testCase, DEGRADED_STATUS_THRESHOLD);
      checkConnection(30, DS2_ID);
      sleepAssertStatusEquals(30, ds1, ServerStatus.NORMAL_STATUS);

      /**
       * DS2 sends again a lot of changes to make DS1 degraded again
       */
      bw = new BrokerWriter(ds2, DS2_ID, false);
      bw.follow();
      sleep(8000); // Let some messages being queued in RS, and analyzer see the change
      sleepAssertStatusEquals(30, ds1, ServerStatus.DEGRADED_STATUS);

      /**
       * RS1 stops to make DS1 go to not connected status (from degraded status)
       */
      rs1.remove();
      bw.pause();
      sleepAssertStatusEquals(30, ds1, ServerStatus.NOT_CONNECTED_STATUS);


      /**
       * DS2 restarts with up to date server state (this allows to have
       * restarting RS1 not sending him some updates he already sent)
       */
      ds2.stop();
      bw.shutdown();
      br.shutdown();
      curState = ds1.getServerState();
      ds2 = createReplicationBroker(DS2_ID, curState, EMPTY_DN_GENID);
      br = new BrokerReader(ds2, DS2_ID);

      /**
       * RS1 restarts, DS1 should reconnect in degraded status (from not connected
       * this time, not from state machine entry)
       */
      rs1 = createReplicationServer(testCase, DEGRADED_STATUS_THRESHOLD);
      // It is too difficult to tune the right sleep so disabling this test:
      // Sometimes the status analyzer may be fast and quickly change the status
      // of DS1 to NORMAL_STATUS
      //sleep(2000);
      //sleepAssertStatusEquals(30, ds1, ServerStatus.DEGRADED_STATUS);
      checkConnection(30, DS2_ID);

      /**
       * DS1 should come back in normal status after a while
       */
      sleepAssertStatusEquals(30, ds1, ServerStatus.NORMAL_STATUS);

      /**
       * DS2 sends a reset gen id order with wrong gen id: DS1 should go into bad generation id status
       */
      long BAD_GEN_ID = 999999L;
      resetGenId(ds2, BAD_GEN_ID); // ds2 will also go bad gen
      sleepAssertStatusEquals(30, ds1, ServerStatus.BAD_GEN_ID_STATUS);

      /**
       * DS2 sends again a reset gen id order with right id: DS1 should be disconnected
       * by RS then reconnect and enter again in normal status. This goes through
       * not connected status but not possible to check as should reconnect immediately
       */
      resetGenId(ds2, EMPTY_DN_GENID); // ds2 will also be disconnected
      ds2.stop();
      br.shutdown(); // Reader could reconnect broker, but gen id would be bad: need to recreate a broker to send changex
      sleepAssertStatusEquals(30, ds1, ServerStatus.NORMAL_STATUS);

      /**
       * DS2 sends again a lot of changes to make DS1 degraded again
       */
      curState = ds1.getServerState();
      ds2 = createReplicationBroker(DS2_ID, curState, EMPTY_DN_GENID);
      checkConnection(30, DS2_ID);
      bw = new BrokerWriter(ds2, DS2_ID, false);
      br = new BrokerReader(ds2, DS2_ID);
      bw.follow();
      sleep(8000); // Let some messages being queued in RS, and analyzer see the change
      sleepAssertStatusEquals(30, ds1, ServerStatus.DEGRADED_STATUS);

      /**
       * DS2 sends reset gen id order with bad gen id: DS1 should go in bad gen id
       * status (from degraded status this time)
       */
      resetGenId(ds2, -1); // -1 to allow next step full update and flush RS db so that DS1 can reconnect after full update
      sleepAssertStatusEquals(30, ds1, ServerStatus.BAD_GEN_ID_STATUS);
      bw.pause();

      /**
       * DS2 engages full update (while DS1 in bad gen id status), DS1 should go
       * in full update status
       */
      BrokerInitializer bi = new BrokerInitializer(ds2, DS2_ID, false);
      bi.initFullUpdate(DS1_ID, 200);
      sleepAssertStatusEquals(30, ds1, ServerStatus.FULL_UPDATE_STATUS);

      /**
       * DS2 terminates full update to DS1: DS1 should reconnect (goes through not connected status)
       * and come back to normal status (RS genid was -1 so RS will adopt ne genb id)
       */
      bi.runFullUpdate();
      sleepAssertStatusEquals(30, ds1, ServerStatus.NORMAL_STATUS);

      /**
       * DS2 sends changes to DS1: DS1 should go in degraded status
       */
      ds2.stop(); // will need a new broker with another gen id restart it
      bw.shutdown();
      br.shutdown();
      long newGen = ds1.getGenerationID();
      curState = ds1.getServerState();
      ds2 = createReplicationBroker(DS2_ID, curState, newGen);
      checkConnection(30, DS2_ID);
      bw = new BrokerWriter(ds2, DS2_ID, false);
      br = new BrokerReader(ds2, DS2_ID);
      bw.follow();
      sleep(8000); // Let some messages being queued in RS, and analyzer see the change
      sleepAssertStatusEquals(30, ds1, ServerStatus.DEGRADED_STATUS);

      /**
       * DS2 engages full update (while DS1 in degraded status), DS1 should go
       * in full update status
       */
      bi = new BrokerInitializer(ds2, DS2_ID, false);
      bi.initFullUpdate(DS1_ID, 300);
      sleepAssertStatusEquals(30, ds1, ServerStatus.FULL_UPDATE_STATUS);
      bw.pause();

      /**
       * DS2 terminates full update to DS1: DS1 should reconnect (goes through not connected status)
       * and come back to bad gen id status (RS genid was another gen id (300 entries instead of 200))
       */
      bi.runFullUpdate();
      sleepAssertStatusEquals(30, ds1, ServerStatus.BAD_GEN_ID_STATUS);

      /**
       * DS2 sends reset gen id with gen id same as DS1: DS1 will be disconnected
       * by RS (not connected status) and come back to normal status
       */
      ds2.stop(); // will need a new broker with another gen id restart it
      bw.shutdown();
      br.shutdown();
      newGen = ds1.getGenerationID();
      curState = ds1.getServerState();
      ds2 = createReplicationBroker(DS2_ID, curState, newGen);
      checkConnection(30, DS2_ID);
      br = new BrokerReader(ds2, DS2_ID);
      resetGenId(ds2, newGen); // Make DS1 reconnect in normal status

      sleepAssertStatusEquals(30, ds1, ServerStatus.NORMAL_STATUS);

      /**
       * DS2 engages full update (while DS1 in normal status), DS1 should go
       * in full update status
       */
      bi = new BrokerInitializer(ds2, DS2_ID, false);
      bi.initFullUpdate(DS1_ID, 300); // 300 entries will compute same genid of the RS
      sleepAssertStatusEquals(30, ds1, ServerStatus.FULL_UPDATE_STATUS);

      /**
       * DS2 terminates full update to DS1: DS1 should reconnect (goes through not connected status)
       * and come back to normal status (process full update with same data as
       * before so RS already has right gen id: version with 300 entries)
       */
      bi.runFullUpdate();
      ds2.stop();
      br.shutdown();
      sleepAssertStatusEquals(30, ds1, ServerStatus.NORMAL_STATUS);

      /**
       * RS1 stops, DS1 should go to not connected status
       */
      rs1.remove();
      sleepAssertStatusEquals(30, ds1, ServerStatus.NOT_CONNECTED_STATUS);

    } finally
    {
      // Finalize test
      endTest();
      if (bw != null) bw.shutdown();
      if (br != null) br.shutdown();
    }
  }

  /**
   * Set up the environment.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    // Note: this test does not use the memory test backend as for having a DS
    // going into degraded status, we need to send a lot of updates. This makes
    // the memory test backend crash with OutOfMemoryError. So we prefer here
    // a backend backed up with a file

    // Clear the backend
    LDAPReplicationDomain.clearJEBackend(false, "userRoot", EXAMPLE_DN);

  }

  /**
   * Clean up the environment.
   *
   * @throws Exception If the environment could not be set up.
   */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    // Clear the backend
    LDAPReplicationDomain.clearJEBackend(false, "userRoot", EXAMPLE_DN);

    paranoiaCheck();
  }

  /**
   * Sends a reset genid message through the given replication broker, with the
   * given new generation id
   */
  private void resetGenId(ReplicationBroker rb, long newGenId)
  {
    ResetGenerationIdMsg resetMsg = new ResetGenerationIdMsg(newGenId);
    rb.publish(resetMsg);
  }

  /**
   * Utility class for making a full update through a broker. No separated thread
   * Usage:
   * BrokerInitializer bi = new BrokerInitializer(rb, sid, nEntries);
   * bi.initFullUpdate(); // Initializes a full update session by sending InitializeTargetMsg
   * bi.runFullUpdate(); // loops sending nEntries entries and finalizes the full update by sending the EntryDoneMsg
   */
  private class BrokerInitializer
  {

    private ReplicationBroker rb = null;
    private short serverId = -1;
    private long userId = 0;
    private short destId = -1; // Server id of server to initialize
    private long nEntries = -1; // Number of entries to send to dest
    private boolean createReader = false;

    /**
     * If the BrokerInitializer is to be used for a lot of entries to send
     * (which is often the case), the reader thread should be enabled to make
     * the window subsystem work and allow the broker to send as much entries as
     * he wants. If not enabled, the user is responsible to call the receive
     * method of the broker himself.
     */
    private BrokerReader reader = null;

    /**
     * Creates a broker initializer with a reader
     */
    public BrokerInitializer(ReplicationBroker rb, short serverId)
    {
      this(rb, serverId, true);
    }

    /**
     * Creates a broker initializer. Also creates a reader according to request
     */
    public BrokerInitializer(ReplicationBroker rb, short serverId,
      boolean createReader)
    {
      this.rb = rb;
      this.serverId = serverId;
      this.createReader = createReader;
    }

    /**
     * Initializes a full update session by sending InitializeTargetMsg
     */
    public void initFullUpdate(short destId, long nEntries)
    {
      // Also create reader ?
      if (createReader)
      {
        reader = new BrokerReader(rb, serverId);
      }

      debugInfo("Broker " + serverId + " initializer sending InitializeTargetMsg to server " + destId);

      this.destId = destId;
      this.nEntries = nEntries;

      // Send init msg to warn dest server it is going do be initialized
      RoutableMsg initTargetMsg = null;

      initTargetMsg =
          new InitializeTargetMsg(EXAMPLE_DN, serverId, destId,
          serverId, nEntries);

      rb.publish(initTargetMsg);

      // Send top entry for the domain
      String topEntry = "dn: " + EXAMPLE_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: domain\n"
        + "dc: example\n"
        + "entryUUID: 11111111-1111-1111-1111-111111111111\n\n";
      EntryMsg entryMsg = new EntryMsg(serverId, destId, topEntry.getBytes());
      rb.publish(entryMsg);
    }

    private EntryMsg createNextEntryMsg()
    {
      String userEntryUUID = "11111111-1111-1111-1111-111111111111";
      long curId = userId++;
      String userdn = "uid=full_update_user" + curId + "," + EXAMPLE_DN;
      String entryWithUUIDldif = "dn: " + userdn + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" +
        "uid: full_update_user" + curId + "\n" +
        "homePhone: 951-245-7634\n" +
        "description: This is the description for Aaccf Amar.\n" + "st: NC\n" +
        "mobile: 027-085-0537\n" +
        "postalAddress: Aaccf Amar$17984 Thirteenth Street" +
        "$Rockford, NC  85762\n" + "mail: user.1@example.com\n" +
        "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n" +
        "street: 17984 Thirteenth Street\n" + "telephoneNumber: 216-564-6748\n" +
        "employeeNumber: 1\n" + "sn: Amar\n" + "givenName: Aaccf\n" +
        "postalCode: 85762\n" + "userPassword: password\n" + "initials: AA\n" +
        "entryUUID: " + userEntryUUID + "\n\n";
      // -> WARNING: EntryMsg PDUs are concatenated before calling import on LDIF
      // file so need \n\n to separate LDIF entries to conform to LDIF file format

      // Create an entry message
      EntryMsg entryMsg = new EntryMsg(serverId, destId,
        entryWithUUIDldif.getBytes());

      return entryMsg;
    }

    /**
     * Loops sending entries for full update (EntryMsg messages). When
     * terminates, sends the EntryDoneMsg to finalize full update. Number of
     * sent entries is determined at initFullUpdate call time.
     */
    public void runFullUpdate()
    {
      debugInfo("Broker " + serverId + " initializer starting sending entries to server " + destId);

      for(long i = 0 ; i<nEntries ; i++) {
          EntryMsg entryMsg = createNextEntryMsg();
          rb.publish(entryMsg);
      }

      debugInfo("Broker " + serverId + " initializer stopping sending entries");

      debugInfo("Broker " + serverId + " initializer sending EntryDoneMsg");
      DoneMsg doneMsg = new DoneMsg(serverId, destId);
      rb.publish(doneMsg);

      if (createReader)
      {
        reader.shutdown();
      }

      debugInfo("Broker " + serverId + " initializer thread is dying");
    }
  }

  /**
   * Thread for sending a lot of changes through a broker.
   */
  private class BrokerWriter extends Thread
  {

    private ReplicationBroker rb = null;
    private short serverId = -1;
    private long userId = 0;
    private AtomicBoolean shutdown = new AtomicBoolean(false);
    // The writer starts suspended
    private AtomicBoolean suspended = new AtomicBoolean(true);
    // Tells a sending session is finished
    // A session is sending messages between the follow and the pause calls,
    // or the time a followAndPause method runs.
    private AtomicBoolean sessionDone = new AtomicBoolean(true);
    private boolean careAboutAmountOfChanges = false;
    private int nChangesSent = 0; // Number of sent changes
    private int nChangesSentLimit = 0;
    ChangeNumberGenerator gen = null;
    private Object sleeper = new Object();
    /**
     * If the BrokerWriter is to be used for a lot of changes to send (which is
     * often the case), the reader thread should be enabled to make the window
     * subsystem work and allow the broker to send as much changes as he wants.
     * If not enabled, the user is responsible to call the receive method of
     * the broker himself.
     */
    private BrokerReader reader = null;

    /* Creates a broker writer with a reader */
    public BrokerWriter(ReplicationBroker rb, short serverId)
    {
      this(rb, serverId, true);
    }

    /* Creates a broker writer. Also creates a reader according to request */
    public BrokerWriter(ReplicationBroker rb, short serverId,
      boolean createReader)
    {
      super("BrokerWriter for broker " + serverId);
      this.rb = rb;
      this.serverId = serverId;
      // Create a Change number generator to generate new change numbers
      // when we need to send changes
      gen = new ChangeNumberGenerator(serverId, 0);

      // Start thread (is paused by default so will have to call follow anyway)
      start();

      // Also create reader ?
      if (createReader)
      {
        reader = new BrokerReader(rb, serverId);
      }
    }

    /**
     * Loops sending changes: add operations creating users with different ids
     * This starts paused and has to be resumed calling a follow method.
     */
    public void run()
    {
      boolean dbg1Written = false, dbg2Written;
      // No stop msg when entering the loop (thread starts with paused writer)
      dbg2Written = true;
      while (!shutdown.get())
      {
        long startSessionTime = -1;
        boolean startedNewSession = false;
        // When not in pause, loop sending changes to RS
        while (!suspended.get())
        {
          startedNewSession = true;
          if (!dbg1Written)
          {
            startSessionTime = System.currentTimeMillis();
            debugInfo("Broker " + serverId +
              " writer starting sending changes session at: " + startSessionTime);
            dbg1Written = true;
            dbg2Written = false;
          }
          AddMsg addMsg = createNextAddMsg();
          rb.publish(addMsg);
          // End session if amount of changes sent has been requested
          if (careAboutAmountOfChanges)
          {
            nChangesSent++;
            if (nChangesSent == nChangesSentLimit)
            {
              // Requested number of changes to send sent, end session
              debugInfo("Broker " + serverId + " writer reached " +
                nChangesSent + " changes limit");
              suspended.set(true);
              break;
            }
          }
        }
        if (!dbg2Written)
        {
          long endSessionTime = System.currentTimeMillis();
          debugInfo("Broker " + serverId +
            " writer stopping sending changes session at: " + endSessionTime +
            " (duration: " + (endSessionTime - startSessionTime) + " ms)");
          dbg1Written = false;
          dbg2Written = true;
        }
        // Mark session is finished
        if (startedNewSession)
          sessionDone.set(true);
        try
        {
          // Writer in pause, sleep a while to let other threads work
          synchronized(sleeper)
          {
            sleeper.wait(1000);
          }
        } catch (InterruptedException ex)
        {
          /* Don't care */
        }
      }
      debugInfo("Broker " + serverId + " writer thread is dying");
    }

    /**
     * Stops the writer thread
     */
    public void shutdown()
    {
      suspended.set(true); // If were working
      shutdown.set(true);
      synchronized (sleeper)
      {
        sleeper.notify();
      }
      try
      {
        join();
      } catch (InterruptedException ex)
      {
        /* Don't care */
      }

      // Stop reader if any
      if (reader != null)
      {
        reader.shutdown();
      }
    }

    /**
     * Suspends the writer thread
     */
    public void pause()
    {
      if (isPaused())
        return; // Already suspended
      suspended.set(true);
      // Wait for all messages sent
      while (!sessionDone.get())
      {
        try
        {
          Thread.sleep(200);
        } catch (InterruptedException ex)
        {
          /* Don't care */
        }
      }
    }

    /**
     * Test if the writer is suspended
     */
    public boolean isPaused()
    {
      return (sessionDone.get());
    }

    /**
     * Resumes the writer thread until it is paused
     */
    public void follow()
    {
      sessionDone.set(false);
      suspended.set(false);
    }

    /**
     * Resumes the writer and suspends it after a given amount of ms
     * If the writer was working it will be paused anyway after the given amount
     * of time.
     * -> blocking call
     */
    public void followAndPause(long time)
    {
      debugInfo("Requested broker writer " + serverId + " to write for " + time + " ms.");
      pause(); // If however we were already working
      sessionDone.set(false);
      suspended.set(false);
      try
      {
        Thread.sleep(time);
      } catch (InterruptedException ex)
      {
        /* Don't care */
      }
      pause();
    }

    /**
     * Resumes the writer and suspends it after a given amount of changes has been
     * sent. If the writer was working it will be paused anyway after the given
     * amount of changes, starting from the current call time.
     * -> blocking call
     */
    public void followAndPause(int nChanges)
    {
      debugInfo("Requested broker writer " + serverId + " to write " + nChanges + " change(s).");
      pause(); // If however we were already working

      // Initialize counter system variables
      nChangesSent = 0;
      nChangesSentLimit = nChanges;
      careAboutAmountOfChanges = true;

      // Start session
      sessionDone.set(false);
      suspended.set(false);

      // Wait for all messages sent
      while (!sessionDone.get())
      {
        try
        {
          Thread.sleep(1000);
        } catch (InterruptedException ex)
        {
          /* Don't care */
        }
      }
      careAboutAmountOfChanges = false;
    }

    private AddMsg createNextAddMsg()
    {
      String userEntryUUID = "11111111-1111-1111-1111-111111111111";
      long curId =  userId++;
      String userdn = "uid=user" + curId + "," + EXAMPLE_DN;
      String entryWithUUIDldif = "dn: " + userdn + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" +
        "uid: user" + curId + "\n" +
        "homePhone: 951-245-7634\n" +
        "description: This is the description for Aaccf Amar.\n" + "st: NC\n" +
        "mobile: 027-085-0537\n" +
        "postalAddress: Aaccf Amar$17984 Thirteenth Street" +
        "$Rockford, NC  85762\n" + "mail: user.1@example.com\n" +
        "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n" +
        "street: 17984 Thirteenth Street\n" + "telephoneNumber: 216-564-6748\n" +
        "employeeNumber: 1\n" + "sn: Amar\n" + "givenName: Aaccf\n" +
        "postalCode: 85762\n" + "userPassword: password\n" + "initials: AA\n" +
        "entryUUID: " + userEntryUUID + "\n";

      Entry personWithUUIDEntry = null;
      try
      {
        personWithUUIDEntry = TestCaseUtils.entryFromLdifString(
          entryWithUUIDldif);
      } catch (Exception e)
      {
        fail(e.getMessage());
      }

      // Create an update message to add an entry.
      AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        personWithUUIDEntry.getDN().toString(),
        userEntryUUID,
        null,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());

      return addMsg;
    }
  }

  /**
   * This simple reader just throws away the received
   * messages. It is used on a breaker we want to be able to send or read from some message
   * with (changes, entries (full update)...). Calling the receive method of the
   * broker allows to unblock the window mechanism and to send the desired messages.
   * Calling the updateWindowAfterReplay method allows to send when necessary the
   * window message to the RS to allow him send other messages he may want to send us.
   */
  private class BrokerReader extends Thread
  {

    private ReplicationBroker rb = null;
    private short serverId = -1;
    private boolean shutdown = false;
    private ReplicationMsg lastMsg = null;

    public BrokerReader(ReplicationBroker rb, short serverId)
    {
      super("BrokerReader for broker " + serverId);
      this.rb = rb;
      this.serverId = serverId;
      start();
    }
    // Loop reading and throwing update messages
    public void run()
    {
      while (!shutdown)
      {
        try
        {
          ReplicationMsg msg = rb.receive(); // Allow more messages to be sent by broker writer
          rb.updateWindowAfterReplay();  // Allow RS to send more messages to broker
          if (msg != null)
            debugInfo("Broker " + serverId + " reader received: " + msg);
          lastMsg = msg;
        } catch (SocketTimeoutException ex)
        {
          if (shutdown)
            return;
        }
      }
      debugInfo("Broker " + serverId + " reader thread is dying");
    }

    // Returns last received message from reader
    // When read, last value is cleared
    public ReplicationMsg getLastMsg()
    {
      ReplicationMsg toReturn = lastMsg;
      lastMsg = null;
      return toReturn;
    }

    // Stops reader thread
    public void shutdown()
    {
      shutdown = true;

      try
      {
        join();
      } catch (InterruptedException ex)
      {
        /* Don't care */
      }
    }
  }

  /**
   * Waits for a long time for an equality condition to be true.
   * Every second, the equality check is performed. After the provided amount of
   * seconds, if the equality is false, an assertion error is raised.
   * This methods ends either because the equality is true or if the timeout
   * occurs after the provided number of seconds.
   * This method is convenient when the the equality can only occur after a
   * period of time which is difficult to establish, but we know it will occur
   * anyway. This has 2 advantages compared to a classical code like this:
   * - sleep(some time);
   * - assertEquals(testedValue, expectedValue);
   * 1. If the sleep value is too big, this will impact the total time of
   * running tests uselessly. It may also penalize a fast running machine where
   * the sleep time value may be unnecessarily to long.
   * 2. If the sleep value is too small, some slow machines may have the test
   * fail whereas some additional time would have made the test succeed.
   * @param secTimeout Number of seconds to wait before failing. The value for
   * this should be high. A timeout is needed anyway to have the test campaign
   * finish anyway.
   * @param testedValue The value we want to test
   * @param expectedValue The value the tested value should be equal to
   */
  private void sleepAssertStatusEquals(int secTimeout, LDAPReplicationDomain testedValue,
    ServerStatus expectedValue)
  {
    int nSec = 0;

    if ((testedValue == null) || (expectedValue == null))
      fail("sleepAssertStatusEquals: null parameters");

    // Go out of the loop only if equality is obtained or if timeout occurs
    while (true)
    {
      // Sleep 1 second
      try
      {
        Thread.sleep(1000);
      } catch (InterruptedException ex)
      {
        fail("Error sleeping " + stackTraceToSingleLineString(ex));
      }
      nSec++;

      // Test equality of values
      if (testedValue.getStatus().equals(expectedValue))
      {
        debugInfo("sleepAssertStatusEquals: equality obtained after "
          + nSec + " seconds (" + expectedValue + ").");
        return;
      }

      if (nSec == secTimeout)
      {
        // Timeout reached, end with error
        fail("sleepAssertStatusEquals: got <" +
          testedValue.getStatus().toString() + "> where expected <" +
          expectedValue.toString() + ">");
      }
    }
  }
}
