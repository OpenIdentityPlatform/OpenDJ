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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2025 3A Systems,LLC.
 */
package org.opends.server.replication.service;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.generateThreadDump;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.Task;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationDomain.ImportExportContext;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the Generic Replication Service.
 */
@SuppressWarnings("javadoc")
public class ReplicationDomainTest extends ReplicationTestCase
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private static final Task NO_INIT_TASK = null;

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
    DN testService = DN.valueOf("o=test");
    ReplicationServer replServer1 = null;
    ReplicationServer replServer2 = null;
    FakeReplicationDomain domain1 = null;
    FakeReplicationDomain domain2 = null;

    try
    {
      int[] ports = TestCaseUtils.findFreePorts(2);
      int replServerPort1 = ports[0];
      int replServerPort2 = ports[1];

      replServer1 = createReplicationServer(replServerID1, replServerPort1,
          "ReplicationDomainTestDb1", 100, "localhost:" + replServerPort2);
      replServer2 = createReplicationServer(replServerID2, replServerPort2,
          "ReplicationDomainTestDb2", 100, "localhost:" + replServerPort1);

      SortedSet<String> servers = newTreeSet("localhost:" + replServerPort1);
      BlockingQueue<UpdateMsg> rcvQueue1 = new LinkedBlockingQueue<>();
      domain1 = new FakeReplicationDomain(
          testService, domain1ServerId, servers, 100, 1000, rcvQueue1);

      SortedSet<String> servers2 = newTreeSet("localhost:" + replServerPort2);
      BlockingQueue<UpdateMsg> rcvQueue2 = new LinkedBlockingQueue<>();
      domain2 = new FakeReplicationDomain(
          testService, domain2ServerId, servers2, 100, 1000, rcvQueue2);

      Thread.sleep(500);

      /*
       * Publish a message from domain1,
       * Check that domain2 receives it shortly after.
       */
      byte[] test = {1, 2, 3 ,4, 0, 1, 2, 3, 4, 5};
      publish(domain1, test);

      UpdateMsg rcvdMsg = rcvQueue2.poll(20, TimeUnit.SECONDS);
      assertNotNull(rcvdMsg);
      assertEquals(test, rcvdMsg.getPayload());

      for (RSInfo replServerInfo : domain1.getRsInfos())
      {
        // The generation Id of the remote should be 1
        assertEquals(replServerInfo.getGenerationId(), 1,
            "Unexpected value of generationId in RSInfo for RS=" + replServerInfo);
      }

      for (DSInfo serverInfo : domain1.getReplicaInfos().values())
      {
        assertEquals(serverInfo.getStatus(), ServerStatus.NORMAL_STATUS);
      }

      domain1.setGenerationID(2);
      domain1.resetReplicationLog();
      Thread.sleep(500);

      for (RSInfo replServerInfo : domain1.getRsInfos())
      {
        // The generation Id of the remote should now be 2
        assertEquals(replServerInfo.getGenerationId(), 2,
            "Unexpected value of generationId in RSInfo for RS=" + replServerInfo);
      }

      int sleepTime = 50;
      while (true)
      {
        try
        {
          assertExpectedServerStatuses(domain1.getReplicaInfos(),
              domain1ServerId, domain2ServerId);
          assertExpectedServerStatuses(domain2.getReplicaInfos(),
              domain1ServerId, domain2ServerId);

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
          if (sleepTime >= 30000)
          {
            throw e;
          }
          Thread.sleep(sleepTime);
          sleepTime *= 2;
        }
      }
    }
    finally
    {
      disable(domain1, domain2);
      remove(replServer1, replServer2);
    }
  }

  /**
   * Publish information to the Replication Service (not assured mode).
   *
   * @param msg  The byte array containing the information that should
   *             be sent to the remote entities.
   */
  void publish(FakeReplicationDomain domain, byte[] msg)
  {
    UpdateMsg updateMsg;
    synchronized (this)
    {
      updateMsg = new UpdateMsg(domain.getGenerator().newCSN(), msg);
      // If assured replication is configured,
      // this will prepare blocking mechanism.
      // If assured replication is disabled, this returns immediately
      domain.prepareWaitForAckIfAssuredEnabled(updateMsg);
      domain.publish(updateMsg);
    }

    try
    {
      // If assured replication is enabled,
      // this will wait for the matching ack or time out.
      // If assured replication is disabled, this returns immediately
      domain.waitForAckIfAssuredEnabled(updateMsg);
    }
    catch (TimeoutException ex)
    {
      // This exception may only be raised if assured replication is enabled
      logger.info(NOTE_DS_ACK_TIMEOUT, domain.getBaseDN(), domain.getAssuredTimeout(), updateMsg);
    }
  }

  private void assertExpectedServerStatuses(Map<Integer, DSInfo> dsInfos,
      int domain1ServerId, int domain2ServerId)
  {
    for (DSInfo serverInfo : dsInfos.values())
    {
      if (serverInfo.getDsId() == domain2ServerId)
      {
        assertEquals(serverInfo.getStatus(), ServerStatus.BAD_GEN_ID_STATUS);
      }
      else
      {
        assertEquals(serverInfo.getDsId(), domain1ServerId);
        assertEquals(serverInfo.getStatus(), ServerStatus.NORMAL_STATUS);
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
    DN testService = DN.valueOf("o=test");
    ReplicationServer replServer1 = null;
    int replServerID1 = 10;
    FakeReplicationDomain domain1 = null;
    int domain1ServerId = 1;

    try
    {
      int replServerPort = TestCaseUtils.findFreePort();

      replServer1 = createReplicationServer(replServerID1, replServerPort,
          "ReplicationDomainTestDb", 100000, "localhost:" + replServerPort);

      SortedSet<String> servers = newTreeSet("localhost:" + replServerPort);
      BlockingQueue<UpdateMsg> rcvQueue1 = new LinkedBlockingQueue<>();
      domain1 = new FakeReplicationDomain(
          testService, domain1ServerId, servers, 1000, 100000, rcvQueue1);


      /*
       * Publish a message from domain1,
       * Check that domain2 receives it shortly after.
       */
      byte[] test = {1, 2, 3 ,4, 0, 1, 2, 3, 4, 5};

      long timeNow = System.nanoTime();
      timeNow = publishRepeatedly(domain1, test, timeNow);
      timeNow = publishRepeatedly(domain1, test, timeNow);
      timeNow = publishRepeatedly(domain1, test, timeNow);
      timeNow = publishRepeatedly(domain1, test, timeNow);
    }
    finally
    {
      disable(domain1);
      remove(replServer1);
    }
  }

  private long publishRepeatedly(FakeReplicationDomain domain1, byte[] test, long timeNow)
  {
    long timeStart = timeNow;
    for (int i = 0; i < 100000; i++)
    {
      publish(domain1, test);
    }
    timeNow = System.nanoTime();
    System.out.println(timeNow - timeStart);
    return timeNow;
  }

  private ReplicationServer createReplicationServer(int serverId,
      int replicationPort, String dirName, int windowSize,
      String... replServers) throws Exception
  {
    return createReplicationServer(serverId, replicationPort, dirName, windowSize, newTreeSet(replServers));
  }

  private ReplicationServer createReplicationServer(int serverId,
      int replicationPort, String dirName, int windowSize,
      SortedSet<String> replServers) throws Exception
  {
    return new ReplicationServer(
        new ReplServerFakeConfiguration(replicationPort, dirName, 0, serverId, 0, windowSize, replServers));
  }

  private void disable(ReplicationDomain... domains) throws InterruptedException {
    for (ReplicationDomain domain : domains)
    {
      if (domain != null)
      {
        domain.disableService();
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
    DN testService = DN.valueOf("o=test");
    ReplicationServer replServer = null;
    int replServerID = 11;
    FakeReplicationDomain domain1 = null;
    FakeReplicationDomain domain2 = null;

    try
    {
      int replServerPort = TestCaseUtils.findFreePort();

      replServer = createReplicationServer(replServerID, replServerPort,
          "exportAndImportData", 100);
      SortedSet<String> servers = newTreeSet("localhost:" + replServerPort);

      String exportedData = buildExportedData(ENTRYCOUNT);
      domain1 = new FakeReplicationDomain(
          testService, serverId1, servers, 0, exportedData, null, ENTRYCOUNT);

      StringBuffer importedData = new StringBuffer();
      domain2 = new FakeReplicationDomain(
          testService, serverId2, servers, 0, null, importedData, 0);

      /*
       * Trigger a total update from domain1 to domain2.
       * Check that the exported data is correctly received on domain2.
       */
      assertTrue(initializeFromRemote(domain2));
      waitEndExport(exportedData, importedData);
      assertExportSucessful(domain1, domain2, exportedData, importedData);
    }
    finally
    {
      disable(domain1, domain2);
      remove(replServer);
    }
  }

  private boolean initializeFromRemote(ReplicationDomain domain) throws DirectoryException
  {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    for (DSInfo remoteDS : domain.getReplicaInfos().values())
    {
      if (remoteDS.getDsId() != domain.getServerId())
      {
        domain.initializeFromRemote(remoteDS.getDsId(), NO_INIT_TASK);
        return true;
      }
    }
    return false;
  }

  /**
   * Test that a ReplicationDomain is able to export and import its database
   * across 2 replication servers.
   */
  @Test(enabled=true)
  public void exportAndImportAcross2ReplServers() throws Exception
  {
    final int ENTRYCOUNT=5000;
    DN testService = DN.valueOf("o=test");
    ReplicationServer replServer2 = null;
    ReplicationServer replServer1 = null;
    int replServerID = 11;
    int replServerID2 = 12;
    FakeReplicationDomain domain1 = null;
    FakeReplicationDomain domain2 = null;

    try
    {
      int[] ports = TestCaseUtils.findFreePorts(2);
      int replServerPort1 = ports[0];
      int replServerPort2 = ports[1];

      replServer1 = createReplicationServer(replServerID, replServerPort1,
          "exportAndImportservice1", 100);
      replServer2 = createReplicationServer(replServerID2, replServerPort2,
          "exportAndImportservice2", 100, "localhost:" + replServerPort1);

      SortedSet<String> servers1 = newTreeSet("localhost:" + replServerPort1);
      SortedSet<String> servers2 = newTreeSet("localhost:" + replServerPort2);

      String exportedData = buildExportedData(ENTRYCOUNT);
      domain1 = new FakeReplicationDomain(
          testService, 1, servers1, 0, exportedData, null, ENTRYCOUNT);

      StringBuffer importedData = new StringBuffer();
      domain2 = new FakeReplicationDomain(
          testService, 2, servers2, 0, null, importedData, 0);

      domain2.initializeFromRemote(1, NO_INIT_TASK);

      waitEndExport(exportedData, importedData);
      assertExportSucessful(domain1, domain2, exportedData, importedData);
    }
    finally
    {
      disable(domain1, domain2);
      remove(replServer1, replServer2);
    }
  }

  private String buildExportedData(final int ENTRYCOUNT)
  {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ENTRYCOUNT; i++)
    {
      sb.append("key : value").append(i).append("\n\n");
    }
    return sb.toString();
  }

  private void waitEndExport(String exportedData, StringBuffer importedData) throws Exception
  {
    int count = 0;
    while (importedData.length() < exportedData.length() && count < 500*5)
    {
      if(count % 100 == 0) { //capture thread dump on start and every 10 seconds
        logger.info(LocalizableMessage.raw("waitEndExport: thread dump on count=" + count));
        logger.info(LocalizableMessage.raw(generateThreadDump()));
      }
      count ++;
      Thread.sleep(100);
    }
  }

  private void assertExportSucessful(ReplicationDomain domain1,
      ReplicationDomain domain2, String exportedData, StringBuffer importedData)
  {
    assertEquals(getLeftEntryCount(domain2), 0, "Wrong LeftEntryCount for export");
    assertEquals(getLeftEntryCount(domain1), 0, "Wrong LeftEntryCount for import");
    assertEquals(importedData.length(), exportedData.length());
    assertEquals(importedData.toString(), exportedData);
  }

  private long getLeftEntryCount(ReplicationDomain domain)
  {
    final ImportExportContext ieContext = domain.getImportExportContext();
    if (ieContext != null)
    {
      return ieContext.getLeftEntryCount();
    }
    return 0; // import/export is finished
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
    DN testService = DN.valueOf("o=test");
    ReplicationServer replServer = null;
    int replServerID = 12;
    FakeStressReplicationDomain domain1 = null;

    try
    {
      SortedSet<String> servers =
          newTreeSet(HOST1 + SENDERPORT, HOST2 + RECEIVERPORT);

      replServer = createReplicationServer(replServerID, SENDERPORT,
          "ReplicationDomainTestDb", 100, servers);

      BlockingQueue<UpdateMsg> rcvQueue1 = new LinkedBlockingQueue<>();
      domain1 = new FakeStressReplicationDomain(
          testService, 2, servers, 1000, rcvQueue1);

      System.out.println("waiting");
      Thread.sleep(1000000000);
    }
    finally
    {
      disable(domain1);
      remove(replServer);
    }
  }

  /**
   * See comments in senderInitialize() above.
   */
  @Test(enabled=false)
  public void receiverInitialize() throws Exception
  {
    DN testService = DN.valueOf("o=test");
    ReplicationServer replServer = null;
    int replServerID = 11;
    FakeStressReplicationDomain domain1 = null;

    try
    {
      SortedSet<String> servers =
          newTreeSet(HOST1 + SENDERPORT, HOST2 + RECEIVERPORT);

      replServer = createReplicationServer(replServerID, RECEIVERPORT,
          "ReplicationDomainTestDb", 100, servers);

      BlockingQueue<UpdateMsg> rcvQueue1 = new LinkedBlockingQueue<>();
      domain1 = new FakeStressReplicationDomain(
          testService, 1, servers, 100000, rcvQueue1);
      /*
       * Trigger a total update from domain1 to domain2.
       * Check that the exported data is correctly received on domain2.
       */
      while (!initializeFromRemote(domain1))
      {
        System.out.println("trying...");
        Thread.sleep(1000);
      }
      System.out.println("waiting");
      Thread.sleep(10000000);
    }
    finally
    {
      disable(domain1);
      remove(replServer);
    }
  }

}
