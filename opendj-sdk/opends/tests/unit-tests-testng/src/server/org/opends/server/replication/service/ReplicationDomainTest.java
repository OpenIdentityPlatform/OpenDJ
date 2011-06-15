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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.service;

import java.io.File;
import static org.testng.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import java.util.concurrent.LinkedBlockingQueue;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the Generic Replication Service.
 */
public class ReplicationDomainTest extends ReplicationTestCase
{
  /**
   * Create ChangeNumber Data
   */
  @DataProvider(name = "publishAndReceiveData")
  public Object[][] createpublishAndReceiveData()
  {
    return new Object[][] {
       {1, 2, 3, 4},
       {1, 2, 1, 2},
       {1, 2, 45891, 45672},
       {45610, 45720, 1, 2},
       {45610, 45720, 45891, 45672}
    };
  }

  /**
   * Test that a ReplicationDomain is able to publish and receive UpdateMsg.
   * Also test the ReplicationDomain.resetReplicationLog() method.
   */
  @Test(dataProvider = "publishAndReceiveData", enabled=true)
  public void publishAndReceive(
      int replServerID1, int replServerID2,
      int domain1ServerId, int domain2ServerId)
      throws Exception
  {
    String testService = "test";
    ReplicationServer replServer1 = null;
    ReplicationServer replServer2 = null;
    FakeReplicationDomain domain1 = null;
    FakeReplicationDomain domain2 = null;

    try
    {
      // find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int replServerPort1 = socket.getLocalPort();
      socket.close();

      socket = TestCaseUtils.bindFreePort();
      int replServerPort2 = socket.getLocalPort();
      socket.close();

      TreeSet<String> replserver1 = new TreeSet<String>();
      replserver1.add("localhost:" + replServerPort1);

      TreeSet<String> replserver2 = new TreeSet<String>();
      replserver2.add("localhost:" + replServerPort2);

      ReplServerFakeConfiguration conf1 =
        new ReplServerFakeConfiguration(
            replServerPort1, "ReplicationDomainTestDb1",
            0, replServerID1, 0, 100, replserver2);

      ReplServerFakeConfiguration conf2 =
        new ReplServerFakeConfiguration(
            replServerPort2, "ReplicationDomainTestDb2",
            0, replServerID2, 0, 100, replserver1);

      replServer1 = new ReplicationServer(conf1);;
      replServer2 = new ReplicationServer(conf2);
      ArrayList<String> servers = new ArrayList<String>(1);
      servers.add("localhost:" + replServerPort1);

      BlockingQueue<UpdateMsg> rcvQueue1 = new LinkedBlockingQueue<UpdateMsg>();
      domain1 = new FakeReplicationDomain(
          testService, domain1ServerId, servers, 100, 1000, rcvQueue1);

      ArrayList<String> servers2 = new ArrayList<String>(1);
      servers2.add("localhost:" + replServerPort2);

      BlockingQueue<UpdateMsg> rcvQueue2 = new LinkedBlockingQueue<UpdateMsg>();
      domain2 = new FakeReplicationDomain(
          testService, domain2ServerId, servers2, 100, 1000, rcvQueue2);

      Thread.sleep(500);

      /*
       * Publish a message from domain1,
       * Check that domain2 receives it shortly after.
       */
      byte[] test = {1, 2, 3 ,4, 0, 1, 2, 3, 4, 5};
      domain1.publish(test);

      UpdateMsg rcvdMsg = rcvQueue2.poll(20, TimeUnit.SECONDS);
      assertNotNull(rcvdMsg);
      assertEquals(test, rcvdMsg.getPayload());

      /*
       * Now test the resetReplicationLog() method.
       */
      List<RSInfo> replServers = domain1.getRsList();

      for (RSInfo replServerInfo : replServers)
      {
        // The generation Id of the remote should be 1
        assertEquals(replServerInfo.getGenerationId(), 1,
            "Unexpected value of generationId in RSInfo for RS="
            + replServerInfo.toString());
      }

      for (DSInfo serverInfo : domain1.getReplicasList())
      {
        assertEquals(serverInfo.getStatus(), ServerStatus.NORMAL_STATUS);
      }

      domain1.setGenerationID(2);
      domain1.resetReplicationLog();
      Thread.sleep(500);
      replServers = domain1.getRsList();

      for (RSInfo replServerInfo : replServers)
      {
        // The generation Id of the remote should now be 2
        assertEquals(replServerInfo.getGenerationId(), 2,
            "Unexpected value of generationId in RSInfo for RS="
            + replServerInfo.toString());
      }

      int sleepTime = 50;
      while (true)
      {
        try
        {
          for (DSInfo serverInfo : domain1.getReplicasList())
          {
            if (serverInfo.getDsId() == domain2ServerId)
              assertEquals(serverInfo.getStatus(), ServerStatus.BAD_GEN_ID_STATUS);
            else
            {
              assertTrue(serverInfo.getDsId() == domain1ServerId);
              assertTrue(serverInfo.getStatus() == ServerStatus.NORMAL_STATUS);
            }
          }

          for (DSInfo serverInfo : domain2.getReplicasList())
          {
            if (serverInfo.getDsId() == domain2ServerId)
              assertTrue(serverInfo.getStatus() == ServerStatus.BAD_GEN_ID_STATUS);
            else
            {
              assertTrue(serverInfo.getDsId() == domain1ServerId);
              assertTrue(serverInfo.getStatus() == ServerStatus.NORMAL_STATUS);
            }
          }

          Map<Integer, ServerState> states1 = domain1.getReplicaStates();
          ServerState state2 = states1.get(domain2ServerId);
          assertNotNull(state2, "getReplicaStates is not showing DS2");

          Map<Integer, ServerState> states2 = domain2.getReplicaStates();
          ServerState state1 = states2.get(domain1ServerId);
          assertNotNull(state1, "getReplicaStates is not showing DS1");

          // if we reach this point all tests are OK
          break;
        }
        catch (AssertionError e)
        {
          if (sleepTime < 30000)
          {
            Thread.sleep(sleepTime);
            sleepTime *=2;
          }
          else
            throw e;
        }
      }

    }
    finally
    {
      if (domain1 != null)
        domain1.stopDomain();

      if (domain2 != null)
        domain2.stopDomain();

      if (replServer1 != null)
      {
        replServer1.remove();
        StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer1.getDbDirName()));
      }
      if (replServer2 != null)
      {
        replServer2.remove();
        StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer2.getDbDirName()));
      }
    }
  }

  /**
   * Publish performance test.
   * The test loops calling the publish methods of the ReplicationDomain.
   * It should not be enabled by default as it will use a lot of time.
   * Its call is only to investigate performance issues with the replication.
   */
  @Test(enabled=false)
  public void publishPerf() throws Exception
  {
    String testService = "test";
    ReplicationServer replServer1 = null;
    int replServerID1 = 10;
    FakeReplicationDomain domain1 = null;
    int domain1ServerId = 1;

    try
    {
      // find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int replServerPort = socket.getLocalPort();
      socket.close();


      TreeSet<String> replserver = new TreeSet<String>();
      replserver.add("localhost:" + replServerPort);

      ReplServerFakeConfiguration conf1 =
        new ReplServerFakeConfiguration(
            replServerPort, "ReplicationDomainTestDb",
            0, replServerID1, 0, 100000, replserver);

      replServer1 = new ReplicationServer(conf1);;
      ArrayList<String> servers = new ArrayList<String>(1);
      servers.add("localhost:" + replServerPort);

      BlockingQueue<UpdateMsg> rcvQueue1 = new LinkedBlockingQueue<UpdateMsg>();
      domain1 = new FakeReplicationDomain(
          testService, domain1ServerId, servers, 1000, 100000, rcvQueue1);


      /*
       * Publish a message from domain1,
       * Check that domain2 receives it shortly after.
       */
      byte[] test = {1, 2, 3 ,4, 0, 1, 2, 3, 4, 5};

      long timeStart = System.nanoTime();
      for (int i=0; i< 100000; i++)
        domain1.publish(test);
      long timeNow = System.nanoTime();
      System.out.println(timeNow - timeStart);

      timeStart = timeNow;
      for (int i=0; i< 100000; i++)
        domain1.publish(test);
      timeNow = System.nanoTime();
      System.out.println(timeNow - timeStart);

      timeStart = timeNow;
      for (int i=0; i< 100000; i++)
        domain1.publish(test);
      timeNow = System.nanoTime();
      System.out.println(timeNow - timeStart);

      timeStart = timeNow;
      for (int i=0; i< 100000; i++)
        domain1.publish(test);
      timeNow = System.nanoTime();
      System.out.println(timeNow - timeStart);

    }
    finally
    {
      if (domain1 != null)
        domain1.disableService();

      if (replServer1 != null)
      {
        replServer1.remove();
        StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer1.getDbDirName()));
      }
    }
  }

  @DataProvider(name = "exportAndImportData")
  public Object[][] createExportAndimportData()
  {
    return new Object[][] {
       {1, 2},
       {45610, 45720}
    };
  }

  /**
   * Test that a ReplicationDomain is able to export and import its database
   * When there is only one replication server.
   */
  @Test(dataProvider = "exportAndImportData", enabled=true)
  public void exportAndImport(int serverId1, int serverId2) throws Exception
  {
    final int ENTRYCOUNT=5000;
    String testService = "test";
    ReplicationServer replServer = null;
    int replServerID = 11;
    FakeReplicationDomain domain1 = null;
    FakeReplicationDomain domain2 = null;

    try
    {
      // find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int replServerPort = socket.getLocalPort();
      socket.close();

      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(
            replServerPort, "exportAndImportData",
            0, replServerID, 0, 100, null);

      replServer = new ReplicationServer(conf);
      ArrayList<String> servers = new ArrayList<String>(1);
      servers.add("localhost:" + replServerPort);

      StringBuilder exportedDataBuilder = new StringBuilder();
      for (int i =0; i<ENTRYCOUNT; i++)
      {
        exportedDataBuilder.append("key : value"+i+"\n\n");
      }
      String exportedData=exportedDataBuilder.toString();
      domain1 = new FakeReplicationDomain(
          testService, serverId1, servers,
          100, 0, exportedData, null, ENTRYCOUNT);

      StringBuilder importedData = new StringBuilder();
      domain2 = new FakeReplicationDomain(
          testService, serverId2, servers, 100, 0,
          null, importedData, 0);

      /*
       * Trigger a total update from domain1 to domain2.
       * Check that the exported data is correctly received on domain2.
       */
      for (DSInfo remoteDS : domain2.getReplicasList())
      {
        if (remoteDS.getDsId() != domain2.getServerId())
        {
          domain2.initializeFromRemote(remoteDS.getDsId());
          break;
        }
      }

      int count = 0;
      while ((importedData.length() < exportedData.length()) && (count < 500))
      {
        count ++;
        Thread.sleep(100);
      }
      assertTrue(domain2.getLeftEntryCount() == 0,
          "LeftEntryCount for export is " + domain2.getLeftEntryCount());
      assertTrue(domain1.getLeftEntryCount() == 0,
          "LeftEntryCount for import is " + domain1.getLeftEntryCount());
      assertEquals(importedData.length(), exportedData.length());
      assertEquals(importedData.toString(), exportedData);
    }
    finally
    {
      if (domain1 != null)
        domain1.disableService();

      if (domain2 != null)
        domain2.disableService();

      if (replServer != null)
      {
        replServer.remove();
        StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer.getDbDirName()));
      }
    }
  }

  /**
   * Test that a ReplicationDomain is able to export and import its database
   * across 2 replication servers.
   */
  @Test(enabled=true)
  public void exportAndImportAcross2ReplServers() throws Exception
  {
    final int ENTRYCOUNT=5000;
    String testService = "test";
    ReplicationServer replServer2 = null;
    ReplicationServer replServer1 = null;
    int replServerID = 11;
    int replServerID2 = 12;
    FakeReplicationDomain domain1 = null;
    FakeReplicationDomain domain2 = null;

    try
    {
      // find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int replServerPort1 = socket.getLocalPort();
      socket.close();

      socket = TestCaseUtils.bindFreePort();
      int replServerPort2 = socket.getLocalPort();
      socket.close();

      TreeSet<String> replserver1 = new TreeSet<String>();
      replserver1.add("localhost:" + replServerPort1);

      TreeSet<String> replserver2 = new TreeSet<String>();
      replserver2.add("localhost:" + replServerPort2);

      ReplServerFakeConfiguration conf1 =
        new ReplServerFakeConfiguration(
            replServerPort1, "exportAndImportservice1",
            0, replServerID, 0, 100, null);

      ReplServerFakeConfiguration conf2 =
        new ReplServerFakeConfiguration(
            replServerPort2, "exportAndImportservice2",
            0, replServerID2, 0, 100, replserver1);

      replServer1 = new ReplicationServer(conf1);
      replServer2 = new ReplicationServer(conf2);

      ArrayList<String> servers1 = new ArrayList<String>(1);
      servers1.add("localhost:" + replServerPort1);

      ArrayList<String> servers2 = new ArrayList<String>(1);
      servers2.add("localhost:" + replServerPort2);

      StringBuilder exportedDataBuilder = new StringBuilder();
      for (int i =0; i<ENTRYCOUNT; i++)
      {
        exportedDataBuilder.append("key : value"+i+"\n\n");
      }
      String exportedData=exportedDataBuilder.toString();
      domain1 = new FakeReplicationDomain(
          testService, 1, servers1,
          100, 0, exportedData, null, ENTRYCOUNT);

      StringBuilder importedData = new StringBuilder();
      domain2 = new FakeReplicationDomain(
          testService, 2, servers2, 100, 0,
          null, importedData, 0);

      domain2.initializeFromRemote(1);

      int count = 0;
      while ((importedData.length() < exportedData.length()) && (count < 500))
      {
        count ++;
        Thread.sleep(100);
      }
      assertTrue(domain2.getLeftEntryCount() == 0,
          "LeftEntryCount for export is " + domain2.getLeftEntryCount());
      assertTrue(domain1.getLeftEntryCount() == 0,
          "LeftEntryCount for import is " + domain1.getLeftEntryCount());
      assertEquals(importedData.length(), exportedData.length());
      assertEquals(importedData.toString(), exportedData);
    }
    finally
    {
      if (domain1 != null)
        domain1.disableService();

      if (domain2 != null)
        domain2.disableService();

      if (replServer1 != null)
      {
        replServer1.remove();
        StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer1.getDbDirName()));
      }
      if (replServer2 != null)
      {
        replServer2.remove();
        StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer2.getDbDirName()));
      }
    }
  }

  /**
   * Sender side of the Total Update Perf test.
   * The goal of this test is to measure the performance
   * of the total update code.
   * It is not intended to be run as part of the daily unit test but
   * should only be used manually by developer in need of testing the
   * performance improvement or non-regression of the total update code.
   * Use this test in combination with the receiverInitialize() :
   *   - enable the test
   *   - start the senderInitialize first using
   *     ./build.sh \
   *        -Dorg.opends.test.suppressOutput=false \
   *        -Dtest.methods=org.opends.server.replication.service.ReplicationDomainTest.senderInitialize test
   *   - start the receiverInitialize second.
   *   - you may want to change  HOST1 and HOST2 to use 2 different hosts
   *     if you don't want to do a loopback test.
   *   - don't forget to disable again the tests after running them
   */
  final String HOST1 = "localhost:";
  final String HOST2 = "localhost:";
  final int SENDERPORT = 10102;
  final int RECEIVERPORT = 10101;

  @Test(enabled=false)
  public void senderInitialize() throws Exception
  {
    String testService = "test";
    ReplicationServer replServer = null;
    int replServerID = 12;
    FakeStressReplicationDomain domain1 = null;

    try
    {
      TreeSet<String> servers = new TreeSet<String>();
      servers.add(HOST1 + SENDERPORT);
      servers.add(HOST2 + RECEIVERPORT);

      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(
            SENDERPORT, "ReplicationDomainTestDb",
            0, replServerID, 0, 100, servers);

      replServer = new ReplicationServer(conf);

      BlockingQueue<UpdateMsg> rcvQueue1 = new LinkedBlockingQueue<UpdateMsg>();
      domain1 = new FakeStressReplicationDomain(
          testService, 2, servers, 100, 1000, rcvQueue1);

      System.out.println("waiting");
      Thread.sleep(1000000000);
    }
    finally
    {
      if (domain1 != null)
        domain1.disableService();

      if (replServer != null)
      {
        replServer.remove();
        StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer.getDbDirName()));
      }
    }
  }

  /**
   * See comments in senderInitialize() above
   */
  @Test(enabled=false)
  public void receiverInitialize() throws Exception
  {
    String testService = "test";
    ReplicationServer replServer = null;
    int replServerID = 11;
    FakeStressReplicationDomain domain1 = null;

    try
    {
      TreeSet<String> servers = new TreeSet<String>();
      servers.add(HOST1 + SENDERPORT);
      servers.add(HOST2 + RECEIVERPORT);

      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(
            RECEIVERPORT, "ReplicationDomainTestDb",
            0, replServerID, 0, 100, servers);

      replServer = new ReplicationServer(conf);

      BlockingQueue<UpdateMsg> rcvQueue1 = new LinkedBlockingQueue<UpdateMsg>();
      domain1 = new FakeStressReplicationDomain(
          testService, 1, servers, 100, 100000, rcvQueue1);
      /*
       * Trigger a total update from domain1 to domain2.
       * Check that the exported data is correctly received on domain2.
       */
      boolean alone = true;
      while (alone)
      {
        for (DSInfo remoteDS : domain1.getReplicasList())
        {
          if (remoteDS.getDsId() != domain1.getServerId())
          {
            alone = false;
            domain1.initializeFromRemote(remoteDS.getDsId() , null);
            break;
          }
        }
        if (alone)
        {
          System.out.println("trying...");
          Thread.sleep(1000);
        }
      }
      System.out.println("waiting");
      Thread.sleep(10000000);
    }
    finally
    {
      if (domain1 != null)
        domain1.disableService();

      if (replServer != null)
      {
        replServer.remove();
        StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer.getDbDirName()));
      }
    }
  }
}
