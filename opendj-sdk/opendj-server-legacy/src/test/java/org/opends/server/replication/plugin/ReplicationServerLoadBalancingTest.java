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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.*;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.types.DN;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

/**
 * Test in real situations the algorithm for load balancing the DSs connections
 * to the RSs. This uses the weights of the RSs. We concentrate the tests on
 * weight only: all servers have the same group id, gen id an states.
 */
public class ReplicationServerLoadBalancingTest extends ReplicationTestCase
{
  /** Number of DSs. */
  private static final int NDS = 20;
  /** Number of RSs. */
  private static final int NRS = 4;
  private final LDAPReplicationDomain rd[] = new LDAPReplicationDomain[NDS];
  private final ReplicationServer rs[] = new ReplicationServer[NRS];
  private int[] rsPort;

  private static final int RS1_ID = 501;
  private static final int RS2_ID = 502;
  private static final int RS3_ID = 503;

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private void debugInfo(String s)
  {
    logger.error(LocalizableMessage.raw(s));
    if (logger.isTraceEnabled())
    {
      logger.trace("** TEST **" + s);
    }
  }

  private void initTest() throws Exception
  {
    Arrays.fill(rd, null);
    Arrays.fill(rs, null);

    rsPort = TestCaseUtils.findFreePorts(NRS);
  }

  private void endTest() throws Exception
  {
    debugInfo("endTest");
    for (int i = 0 ; i < NDS; i++)
    {
      if (rd[i] != null)
      {
        rd[i].shutdown();
        rd[i] = null;
      }
    }

    // Clear any reference to a domain in synchro plugin
    MultimasterReplication.deleteDomain(DN.valueOf(TEST_ROOT_DN_STRING));
    remove(rs);
    Arrays.fill(rs, null);
    Arrays.fill(rsPort, -1);
    debugInfo("endTest done");
  }

  /**
   * Creates the list of servers to represent the RS topology matching the
   * passed test case.
   */
  private SortedSet<String> createRSListForTestCase(String testCase)
  {
    SortedSet<String> replServers = new TreeSet<>();

    final int nbRSs = getNbRSs(testCase);
    for (int i = 0; i < nbRSs; i++)
    {
      replServers.add("localhost:" + rsPort[i]);
    }
    return replServers;
  }

  private int getNbRSs(String testCase)
  {
    if ("testFailoversAndWeightChanges".equals(testCase)
        || "testSpreadLoad".equals(testCase))
    {
      return NRS;
    }
    else if ("testNoYoyo1".equals(testCase))
    {
      return 2;
    }
    else if ("testNoYoyo2".equals(testCase) || "testNoYoyo3".equals(testCase))
    {
      return 3;
    }
    fail("Unknown test case: " + testCase);
    return 0; // dead code, but java does not know about it
  }

  /**
   * Creates a new ReplicationServer.
   */
  private ReplicationServer createReplicationServer(int rsIndex,
    int weight, String testCase) throws Exception
  {
    SortedSet<String> replServers = new TreeSet<>();

    int nbRSs = getNbRSs(testCase);
    for (int i = 0; i < nbRSs; i++)
    {
      if (i != rsIndex){
        replServers.add("localhost:" + rsPort[i]);
      }
    }

    String dir = "replicationServerLoadBalancingTest" + rsIndex + testCase + "Db";
    ReplServerFakeConfiguration conf =
      new ReplServerFakeConfiguration(rsPort[rsIndex], dir, replicationDbImplementation, 0, rsIndex+501, 0,
      100, replServers, 1, 1000, 5000, weight);
    return new ReplicationServer(conf);
  }

  /**
   * Returns a suitable RS configuration with the passed new weight.
   */
  private ReplicationServerCfg createReplicationServerConfigWithNewWeight
    (int rsIndex, int weight, String testCase)
  {
    SortedSet<String> replServers = new TreeSet<>();

    if ("testFailoversAndWeightChanges".equals(testCase)
        || "testSpreadLoad".equals(testCase))
    {
      // 4 servers used for this test case.
      for (int i = 0; i < NRS; i++)
      {
        if (i != rsIndex)
        {
          replServers.add("localhost:" + rsPort[i]);
        }
      }
    }
    else
    {
      fail("Unknown test case: " + testCase);
    }

    String dir = "replicationServerLoadBalancingTest" + rsIndex + testCase + "Db";
    return new ReplServerFakeConfiguration(rsPort[rsIndex], dir, replicationDbImplementation,
        0, rsIndex + 501, 0, 100, replServers, 1, 1000, 5000, weight);
  }

  /**
   * Creates a new ReplicationDomain.
   */
  private LDAPReplicationDomain createReplicationDomain(int serverId,
      String testCase) throws Exception
  {
    final SortedSet<String> replServers = createRSListForTestCase(testCase);
    final DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
    final DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId + 1, replServers, 1);
    final LDAPReplicationDomain rd =
        MultimasterReplication.createNewDomain(domainConf);
    rd.start();
    assertTrue(rd.isConnected());
    return rd;
  }

  /**
   * Basic weight test: starts some RSs with different weights, start some DSs
   * and check the DSs are correctly spread across the RSs
   * @throws Exception If a problem occurred
   */
  @Test (enabled=true)
  public void testSpreadLoad() throws Exception
  {
    String testCase = "testSpreadLoad";
    debugInfo("Starting " + testCase);
    initTest();

    try
    {
      /**
       * Start RS1 weigth=1, RS2 weigth=2, RS3 weigth=3, RS4 weigth=4
       */
      // Create and start RSs
      for (int i = 0; i < rs.length; i++)
      {
        rs[i] = createReplicationServer(i, i + 1, testCase);
      }

      // Start a first DS to make every RSs inter connect
      rd[0] = createReplicationDomain(0, testCase);

      // Wait for RSs inter-connections
      checkRSConnectionsAndGenId(new int[] {0, 1, 2, 3},
        "Waiting for RSs inter-connections");

      /**
       * Start the 19 other DSs. One should end up with:
       * - RS1 has 2 DSs
       * - RS2 has 4 DSs
       * - RS3 has 6 DSs
       * - RS4 has 8 DSs
       */
      createReplicationDomains(testCase, 1, NDS);

     // Now check the number of connected DSs for each RS
      assertEquals(getNbDSsConnectedToRS(0), 2,
       "Wrong expected number of DSs connected to RS1");
      assertEquals(getNbDSsConnectedToRS(1), 4,
       "Wrong expected number of DSs connected to RS2");
      assertEquals(getNbDSsConnectedToRS(2), 6,
       "Wrong expected number of DSs connected to RS3");
      assertEquals(getNbDSsConnectedToRS(3), 8,
       "Wrong expected number of DSs connected to RS4");
    } finally
    {
      endTest();
    }
  }

  /**
   * Return the number of DSs currently connected to the RS with the passed
   * index.
   */
  private int getNbDSsConnectedToRS(int rsIndex)
  {
    Iterator<ReplicationServerDomain> rsdIt = rs[rsIndex].getDomainIterator();
    if (rsdIt.hasNext())
    {
      return rsdIt.next().getConnectedDSs().keySet().size();
    }
    return 0;
  }

  /**
   * Waits for secTimeout seconds (before failing) that all RSs are connected
   * together and that they have the same generation id.
   * @param rsIndexes List of the indexes of the RSs that should all be
   *        connected together at the end
   * @param msg The message to display if the condition is not met before
   *        timeout
   */
  private void checkRSConnectionsAndGenId(int[] rsIndexes, String msg)
      throws Exception
  {
    debugInfo("checkRSConnectionsAndGenId for <" + msg + ">");
    // Number of seconds to wait for condition before failing
    int secTimeout = 30;
    // Number of seconds already passed
    int nSec = 0;
    // Number of RSs to take into account
    int nRSs = rsIndexes.length;

    // Go out of the loop only if connection is verified or if timeout occurs
    while (true)
    {
      // Test connection
      boolean connected = false;
      boolean sameGenId = false;

      // Connected together ?
      int nOk = 0;
      for (int i = 0; i < nRSs; i++)
      {
        int rsIndex = rsIndexes[i];
        ReplicationServer repServer = rs[rsIndex];
        int curRsId = repServer.getServerId();

        Iterator<ReplicationServerDomain> iter = repServer.getDomainIterator();
        if (!iter.hasNext())
        {
          // No domain yet, RS is not yet connected to others
          debugInfo("RS " + curRsId + " has no domain yet");
          break;
        }
        Set<Integer> connectedRSsId = iter.next().getConnectedRSs().keySet();

        // Does this RS see all other RSs
        int nPeer = 0;
        debugInfo("Checking RSs connected to RS " + curRsId);
        for (int j = 0; j < nRSs; j++)
        {
          int otherRsIndex = rsIndexes[j];
          if (otherRsIndex != rsIndex) // Treat only other RSs
          {
            int otherRsId = otherRsIndex+501;
            if (connectedRSsId.contains(otherRsId))
            {
              debugInfo("\tRS " + curRsId + " sees RS " + otherRsId);
              nPeer++;
            } else
            {
              debugInfo("\tRS " + curRsId + " does not see RS " + otherRsId);
            }
          }
        }
        if (nPeer == nRSs-1)
        {
          nOk++;
        }
      }

      if (nOk == nRSs)
      {
        debugInfo("Connections are ok");
        connected = true;
      } else
      {
        debugInfo("Connections are not ok");
      }

      // Same gen id ?
      long refGenId = -1L;
      boolean refGenIdInitialized = false;
      nOk = 0;
      for (int i = 0; i < nRSs; i++)
      {
        ReplicationServer repServer = rs[i];
        int curRsId = repServer.getServerId();

        Iterator<ReplicationServerDomain> iter = repServer.getDomainIterator();
        if (!iter.hasNext())
        {
          // No domain yet, RS is not yet connected to others
          debugInfo("RS " + curRsId + " has no domain yet");
          break;
        }
        Long rsGenId = iter.next().getGenerationId();

        // Expecting all RSs to have gen id equal and not -1
        if (rsGenId == -1L)
        {
          debugInfo("\tRS " + curRsId + " gen id is -1 which is not expected");
          break;
        }

        if (!refGenIdInitialized)
        {
          // Store reference gen id all RSs must have
          refGenId = rsGenId;
          refGenIdInitialized = true;
        }
        if (rsGenId == refGenId)
        {
          debugInfo("\tRS " + curRsId + " gen id is " + rsGenId + " as expected");
          nOk++;
        } else
        {
          debugInfo("\tRS " + curRsId + " gen id is " + rsGenId
            + " but expected " + refGenId);
        }
      }

      if (nOk == nRSs)
      {
        debugInfo("Gen ids are ok");
        sameGenId = true;
      } else
      {
        debugInfo("Gen ids are not ok");
      }

      if (connected && sameGenId)
      {
        // Connection verified
        debugInfo("checkRSConnections: all RSs connected and with same gen id obtained after "
          + nSec + " seconds.");
        return;
      }

      // Sleep 1 second
      Thread.sleep(1000);
      nSec++;

      // Timeout reached, end with error
      assertTrue(
          nSec <= secTimeout,
          "checkRSConnections: could not obtain that RSs are connected and have the same gen id after "
              + (nSec - 1) + " seconds. [" + msg + "]");
    }
  }

  /**
   * Execute a full scenario with some RSs failovers and dynamic weight changes.
   * @throws Exception If a problem occurred
   */
  @Test (groups = "slow")
  public void testFailoversAndWeightChanges() throws Exception
  {
    String testCase = "testFailoversAndWeightChanges";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {
      /**
       * RS1 (weight=1) starts
       */
      rs[0] = createReplicationServer(0, 1, testCase);

      /**
       * DS1 starts and connects to RS1
       */
      rd[0] = createReplicationDomain(0, testCase);
      assertEquals(rd[0].getRsServerId(), RS1_ID);

      /**
       * RS2 (weight=1) starts
       */
      rs[1] = createReplicationServer(1, 1, testCase);
      checkRSConnectionsAndGenId(new int[] {0, 1},
        "Waiting for RS2 connected to peers");

      /**
       * DS2 starts and connects to RS2
       */
      rd[1] = createReplicationDomain(1, testCase);
      assertEquals(rd[1].getRsServerId(), RS2_ID);

      /**
       * RS3 (weight=1) starts
       */
      rs[2] = createReplicationServer(2, 1, testCase);
      checkRSConnectionsAndGenId(new int[] {0, 1, 2},
        "Waiting for RS3 connected to peers");

      /**
       * DS3 starts and connects to RS3
       */
      rd[2] = createReplicationDomain(2, testCase);
      assertEquals(rd[2].getRsServerId(), RS3_ID);

      /**
       * DS4 starts and connects to RS1, RS2 or RS3
       */
      rd[3] = createReplicationDomain(3, testCase);
      int ds4ConnectedRsId = rd[3].getRsServerId();
      assertTrue(ds4ConnectedRsId == RS1_ID || ds4ConnectedRsId == RS2_ID ||
        ds4ConnectedRsId == RS3_ID,
        "DS4 should be connected to either RS1, RS2 or RS3 but it is " +
        "connected to RS id " + ds4ConnectedRsId);

      /**
       * DS5 starts and connects to one of the 2 other RSs
       */
      rd[4] = createReplicationDomain(4, testCase);
      int ds5ConnectedRsId = rd[4].getRsServerId();
      assertTrue(ds5ConnectedRsId != ds4ConnectedRsId,
        "DS5 should be connected to a RS which is not the same as the one of " +
        "DS4 (" + ds4ConnectedRsId + ")");

      /**
       * DS6 starts and connects to the RS with one DS
       */
      rd[5] = createReplicationDomain(5, testCase);
      int ds6ConnectedRsId = rd[5].getRsServerId();
      assertTrue(ds6ConnectedRsId != ds4ConnectedRsId &&
        ds6ConnectedRsId != ds5ConnectedRsId,
        "DS6 should be connected to a RS which is not the same as the one of " +
        "DS4 (" + ds4ConnectedRsId + ") or DS5 (" + ds5ConnectedRsId + ") : " +
        ds6ConnectedRsId);

      /**
       * DS7 to DS12 start, we must end up with RS1, RS2 and RS3 each with 4 DSs
       */
      createReplicationDomains(testCase, 6, 12);
      // Now check the number of connected DSs for each RS
      assertEquals(getNbDSsConnectedToRS(0), 4,
        "Wrong expected number of DSs connected to RS1");
      assertEquals(getNbDSsConnectedToRS(1), 4,
        "Wrong expected number of DSs connected to RS2");
      assertEquals(getNbDSsConnectedToRS(2), 4,
        "Wrong expected number of DSs connected to RS3");

      /**
       * RS4 (weight=1) starts, we must end up with RS1, RS2, RS3 and RS4 each
       * with 3 DSs
       */

      rs[3] = createReplicationServer(3, 1, testCase);
      checkRSConnectionsAndGenId(new int[] {0, 1, 2, 3},
        "Waiting for RS4 connected to peers");

      checkForCorrectNumbersOfConnectedDSs(new int[][]{new int[] {3, 3, 3, 3}},
        "RS4 started, each RS should have 3 DSs connected to it");

      /**
       * Change RS3 weight from 1 to 3, we must end up with RS1, RS2 and RS4
       * each with 2 DSs and RS3 with 6 DSs
       */

      // Change RS3 weight to 3
      ReplicationServerCfg newRSConfig =
        createReplicationServerConfigWithNewWeight(2, 3, testCase);
      rs[2].applyConfigurationChange(newRSConfig);

      checkForCorrectNumbersOfConnectedDSs(new int[][]{new int[] {2, 2, 6, 2}},
        "RS3 changed weight from 1 to 3");

      /**
       * DS13 to DS20 start, we must end up with RS1, RS2 and RS4 each with 3
       * or 4 DSs (1 with 4 and the 2 others with 3) and RS3 with 10 DSs
       */
      createReplicationDomains(testCase, 12, 20);
      int rsWith4DsIndex = -1; // The RS (index) that has 4 DSs
      // Now check the number of connected DSs for each RS
      int nbDSsRS1 = getNbDSsConnectedToRS(0);
      int nbDSsRS2 = getNbDSsConnectedToRS(1);
      int nbDSsRS3 = getNbDSsConnectedToRS(3);
      Assertions.assertThat(nbDSsRS1).isIn(3, 4);
      Assertions.assertThat(nbDSsRS2).isIn(3, 4);
      Assertions.assertThat(nbDSsRS3).isIn(3, 4);
      if (nbDSsRS1 == 4)
      {
        rsWith4DsIndex = 0;
      }
      if (nbDSsRS2 == 4)
      {
        rsWith4DsIndex = 1;
      }
      if (nbDSsRS3 == 4)
      {
        rsWith4DsIndex = 3;
      }
      assertEquals(nbDSsRS1 + nbDSsRS2 + nbDSsRS3, 10,
          "Expected 10 DSs connected to RS1, RS2 and RS4");
      assertEquals(getNbDSsConnectedToRS(2), 10,
        "Wrong expected number of DSs connected to RS3");

      /**
       * Stop 2 DSs from RS3, one should end up with RS1 has 3 DSs, RS2 has 3
       * DSs, RS3 has 9 DSs and RS4 has 3 DSs (with DS (with the lowest server
       * id) from the RS that had 4 DSs that went to RS3)
       */

      // Determine the lowest id of DSs connected to the RS with 4 DSs
      Set<Integer> fourDsList = getConnectedDSIdsForReplServer(rsWith4DsIndex);
      assertEquals(fourDsList.size(), 4);
      int lowestDsId = Integer.MAX_VALUE;
      for (int id : fourDsList)
      {
        if (id < lowestDsId)
        {
          lowestDsId = id;
        }
      }

      // Get 2 DS ids of 2 DSs connected to RS3 and stop matching DSs
      Iterator<Integer> dsIdIt = getConnectedDSIdsForReplServer(2).iterator();
      int aFirstDsOnRs3Id = dsIdIt.next() - 1;
      rd[aFirstDsOnRs3Id].shutdown();
      int aSecondDsOnRs3Id = dsIdIt.next() - 1;
      rd[aSecondDsOnRs3Id].shutdown();

      // Check connections
      checkForCorrectNumbersOfConnectedDSs(new int[][]{new int[] {3, 3, 9, 3}},
        "2 DSs ("+ aFirstDsOnRs3Id + "," + aSecondDsOnRs3Id +
        ") have been stopped from RS3, DS with lowest id (" + lowestDsId +
        ") should have moved from the RS with 4 DS (RS " +
        (rsWith4DsIndex+501) + ") to RS3");

      // Check that the right DS moved away from the RS with 4 DSs and went to
      // RS3 and that the 3 others did not move
      Set<Integer> dsOnRs3List = getConnectedDSIdsForReplServer(2);
      assertTrue(dsOnRs3List.contains(lowestDsId), "DS with the lowest id (" +
        lowestDsId + " should have come to RS3");
      Set<Integer> threeDsList = getConnectedDSIdsForReplServer(rsWith4DsIndex);
      // All 3 DSs should still be connected to the RS with 4 DSs
      Assertions.assertThat(fourDsList).containsAll(threeDsList);

      /**
       * Start the 2 stopped DSs again, we must end up with RS1, RS2 and RS4
       * each with 3 or 4 DSs (1 with 4 and the 2 others with 3) and RS3 with
       * 10 DSs
       */

      // Restart the 2 stopped DSs
      rd[aFirstDsOnRs3Id] = createReplicationDomain(aFirstDsOnRs3Id, testCase);
      rd[aSecondDsOnRs3Id] = createReplicationDomain(aSecondDsOnRs3Id, testCase);
      // Now check the number of connected DSs for each RS
      nbDSsRS1 = getNbDSsConnectedToRS(0);
      nbDSsRS2 = getNbDSsConnectedToRS(1);
      nbDSsRS3 = getNbDSsConnectedToRS(3);
      Assertions.assertThat(nbDSsRS1).isIn(3, 4);
      Assertions.assertThat(nbDSsRS2).isIn(3, 4);
      Assertions.assertThat(nbDSsRS3).isIn(3, 4);
      assertEquals(nbDSsRS1 + nbDSsRS2 + nbDSsRS3, 10,
          "Expected 10 DSs connected to RS1, RS2 and RS4");
      assertEquals(getNbDSsConnectedToRS(2), 10,
          "Wrong expected number of DSs connected to RS3");

      /**
       * Change RS2 weight to 2, RS3 weight to 4, RS4 weight to 3, we must end
       * up with RS1 has 2 DSs, RS2 has 4 DSs, RS3 has 8 DSs and RS4 has 6 DSs
       */

      // Change RS2 weight to 2
      newRSConfig = createReplicationServerConfigWithNewWeight(1, 2, testCase);
      rs[1].applyConfigurationChange(newRSConfig);
      // Change RS3 weight to 4
      newRSConfig = createReplicationServerConfigWithNewWeight(2, 4, testCase);
      rs[2].applyConfigurationChange(newRSConfig);
      // Change RS4 weight to 3
      newRSConfig = createReplicationServerConfigWithNewWeight(3, 3, testCase);
      rs[3].applyConfigurationChange(newRSConfig);

      checkForCorrectNumbersOfConnectedDSs(new int[][]{new int[] {2, 4, 8, 6}},
        "Changed RS2, RS3 and RS4 weights");

      /**
       * Stop RS2 and RS4, we must end up with RS1 has 4 DSs, and RS3 has 16 DSs
       */
      stopRs(1, 3);

      checkForCorrectNumbersOfConnectedDSs(new int[][]{new int[] {4, -1, 16, -1}},
        "Stopped RS2 and RS4");

      /**
       * Restart RS2 and RS4 with same weights (2 and 3), we must end up with
       * RS1 has 2 DSs, RS2 has 4 DSs, RS3 has 8 DSs and RS4 has 6 DSs
       */

      // Restart RS2 and RS4
      rs[1] = createReplicationServer(1, 2, testCase);
      rs[3] = createReplicationServer(3, 3, testCase);

      checkForCorrectNumbersOfConnectedDSs(new int[][]{new int[] {2, 4, 8, 6}},
        "Restarted RS2 and RS4");

      /**
       * Stop RS3, we must end up with RS1 has 3 DSs, and RS2 has 7 DSs and
       * RS4 has 10 DSs
       */
      stopRs(2);

      checkForCorrectNumbersOfConnectedDSs(new int[][]{
        new int[] {2, 8, -1, 10},
        new int[] {3, 7, -1, 10},
        new int[] {3, 8, -1, 9},
        new int[] {4, 6, -1, 10},
        new int[] {4, 7, -1, 9},
        new int[] {4, 8, -1, 8},
        new int[] {5, 6, -1, 9}},
        "Stopped RS3");

      /**
       * Restart RS3 with same weight (4), we must end up with RS1 has 2 DSs,
       * RS2 has 4 DSs, RS3 has 8 DSs and RS4 has 6 DSs
       */

      // Restart RS3
      rs[2] = createReplicationServer(2, 4, testCase);

      checkForCorrectNumbersOfConnectedDSs(new int[][]{new int[] {2, 4, 8, 6}},
        "Restarted RS2 and RS4");

      /**
       * Stop RS1, RS2 and RS3, all DSs should be connected to RS4
       */
      stopRs(0, 1, 2);

      checkForCorrectNumbersOfConnectedDSs(new int[][]{new int[] {-1, -1, -1, 20}},
        "Stopped RS1, RS2 and RS3");

    } finally
    {
      endTest();
    }
  }

  private Set<Integer> getConnectedDSIdsForReplServer(int rsIndex)
  {
    return rs[rsIndex].getDomainIterator().next().getConnectedDSs().keySet();
  }

  private void stopRs(int... rsIndexes) throws Exception
  {
    for (int rsIndex : rsIndexes)
    {
      if (rs[rsIndex] != null)
      {
        clearChangelogDB(rs[rsIndex]);
        rs[rsIndex].remove();
      }
    }
  }

  /** Translate an int array into a human readable string. */
  private static String toString(int[] ints)
  {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < ints.length; i++)
    {
      if (i != 0)
      {
        sb.append(",");
      }
      sb.append(ints[i]);
    }
    sb.append("]");
    return sb.toString();
  }

  /** Translate an int[][] array into a human readable string. */
  private static String toString(int[][] ints)
  {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < ints.length; i++)
    {
      if (i != 0)
      {
        sb.append(",");
      }
      sb.append(toString(ints[i]));
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Wait for the correct number of connected DSs for each RS. Fails if timeout
   * before condition met.
   * @param possibleExpectedDSsNumbers The expected number of connected DSs for each
   * RS. -1 if the matching RS should not be taken into account. This is a list of
   * possible expected situation
   * @param msg The message to display if the condition is not met before
   *        timeout
   */
  private void checkForCorrectNumbersOfConnectedDSs(int[][] possibleExpectedDSsNumbers,
    String msg) throws Exception
  {
    // Time to wait before condition met: warning, this should let enough
    // time to the topology to auto-balance. Currently  this must at least let
    // enough time to a topo message being received and to monitoring messages
    // being received after (2 monitoring publisher period)
    int secTimeout = 30;
    int nSec = 0;
    // To display what has been seen
    int[] finalDSsNumbers = new int[possibleExpectedDSsNumbers[0].length];

    // Go out of the loop only if connection is verified or if timeout occurs
    while (true)
    {
      for (int[] expectedDSsNumbers : possibleExpectedDSsNumbers)
      {
        // Examine connections
        int nOk = 0; // Number of RSs ok
        int nRSs = 0; // Number of RSs to examine
        for (int j = 0; j < finalDSsNumbers.length; j++)
        {
          int expectedDSNumber = expectedDSsNumbers[j];

          if (expectedDSNumber != -1)
          {
            nRSs++;
            // Check for number of DSs connected to this RS
            int connectedDSs = getNbDSsConnectedToRS(j);
            if (connectedDSs == expectedDSNumber)
            {
              nOk++;
            }
            // Store result for this RS
            finalDSsNumbers[j] = connectedDSs;
          }
          else
          {
            // Store result for this RS
            finalDSsNumbers[j] = -1;
          }
        }

        if (nOk == nRSs)
        {
          // Connection verified
          debugInfo("checkForCorrectNumbersOfConnectedDSs: got expected " +
            "connections " + toString(expectedDSsNumbers) + " after " + nSec +
            " seconds.");
          return;
        }
      }

      // Sleep 1 second
      Thread.sleep(1000);
      nSec++;

      // Timeout reached, end with error
      assertTrue(nSec <= secTimeout,
          "checkForCorrectNumbersOfConnectedDSs: could not get expected "
              + "connections " + toString(possibleExpectedDSsNumbers)
              + " after " + (nSec - 1) + " seconds. Got this result : "
              + toString(finalDSsNumbers) + " [" + msg + "]");
    }
  }

  /**
   * In a topology where the balance cannot be exactly reached according to the
   * weights, this is testing that the DS is not doing yoyo. The yoyo effect
   * would be a DS keeping going between RSs (going to/back from other RS for
   * ever).
   *
   * RS1 weight=1 ;  RS2 weight=1 ; 3DSs.
   * We expect two DSs on one RS and the last one on the other RS and no
   * disconnections/reconnections after the very first connections.
   * @throws Exception If a problem occurred
   */
  @Test (enabled=true,groups = "slow")
  public void testNoYoyo1() throws Exception
  {
    String testCase = "testNoYoyo1";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {

      /**
       * RS1 (weight=1) starts
       */

      rs[0] = createReplicationServer(0, 1, testCase);

      /**
       * DS1 starts and connects to RS1
       */

      rd[0] = createReplicationDomain(0, testCase);
      assertEquals(rd[0].getRsServerId(), RS1_ID);

      /**
       * RS2 (weight=1) starts
       */

      rs[1] = createReplicationServer(1, 1, testCase);
      checkRSConnectionsAndGenId(new int[] {0, 1},
        "Waiting for RS2 connected to peers");

      /**
       * DS2 starts and connects to RS2
       */

      rd[1] = createReplicationDomain(1, testCase);
      assertEquals(rd[1].getRsServerId(), RS2_ID);

      /**
       * DS3 starts and connects to either RS1 or RS2 but should stay on it
       */

      int dsIsIndex = 2;
      rd[dsIsIndex] = createReplicationDomain(dsIsIndex, testCase);
      int rsId = rd[dsIsIndex].getRsServerId();
      int rsIndex = rsId - 501;
      int nDSs = getNbDSsConnectedToRS(rsIndex);
      assertEquals(getNbDSsConnectedToRS(rsIndex), 2, " Expected 2 DSs on RS " + rsId);
      debugInfo(testCase + ": DS3 connected to RS " + rsId + ", with " + nDSs + " DSs");

      // Be sure that DS3 stays connected to the same RS during some long time
      // check every second
      int waitTime = 10;
      int elapsedTime = 0;
      while (elapsedTime < waitTime)
      {
        Thread.sleep(1000);
        // Still connected to the right RS ?
        assertEquals(rd[dsIsIndex].getRsServerId(), rsId, "DS3 should still be " +
          "connected to RS " + rsId);
        assertEquals(getNbDSsConnectedToRS(rsIndex), 2, " Expected 2 DSs on RS " + rsId);
        elapsedTime++;
      }

    } finally
    {
      endTest();
    }
  }

  /**
   * In a topology where the balance cannot be exactly reached according to the
   * weights, this is testing that the DS is not doing yoyo. The yoyo effect
   * would be a DS keeping going between RSs (going to/back from other RS for
   * ever).
   *
   * RS1 weight=1 ;  RS2 weight=1 ; RS3 weight=1 ; 4DSs.
   * We expect 1 RS with 2 DSs and the 2 other RSs with 1 DS each and no
   * disconnections/reconnections after the very first connections.
   * @throws Exception If a problem occurred
   */
  @Test (enabled=true, groups = "slow")
  public void testNoYoyo2() throws Exception
  {
    String testCase = "testNoYoyo2";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {

      /**
       * RS1 (weight=1) starts
       */

      rs[0] = createReplicationServer(0, 1, testCase);

      /**
       * DS1 starts and connects to RS1
       */

      rd[0] = createReplicationDomain(0, testCase);
      assertEquals(rd[0].getRsServerId(), RS1_ID);

      /**
       * RS2 (weight=1) and R3 (weight=1) start
       */

      rs[1] = createReplicationServer(1, 1, testCase);
      rs[2] = createReplicationServer(2, 1, testCase);
      checkRSConnectionsAndGenId(new int[] {0, 1, 2},
        "Waiting for RSs being connected to peers");

      /**
       * DS2 to DS3 start and connects to RSs
       */
      createReplicationDomains(testCase, 1, 3);

      /**
       * DS4 starts and connects to either RS1 RS2 or RS3 but should stay on it
       */

      int dsIsIndex = 3;
      rd[dsIsIndex] = createReplicationDomain(dsIsIndex, testCase);
      int rsId = rd[dsIsIndex].getRsServerId();
      int rsIndex = rsId - 501;
      int nDSs = getNbDSsConnectedToRS(rsIndex);
      assertEquals(getNbDSsConnectedToRS(rsIndex), 2, " Expected 2 DSs on RS " + rsId);
      debugInfo(testCase + ": DS4 connected to RS " + rsId + ", with " + nDSs + " DSs");

      // Be sure that DS3 stays connected to the same RS during some long time
      // check every second
      int waitTime = 10;
      int elapsedTime = 0;
      while (elapsedTime < waitTime)
      {
        Thread.sleep(1000);
        // Still connected to the right RS ?
        assertEquals(rd[dsIsIndex].getRsServerId(), rsId, "DS4 should still be " +
          "connected to RS " + rsId);
        assertEquals(getNbDSsConnectedToRS(rsIndex), 2, " Expected 2 DSs on RS " + rsId);
        elapsedTime++;
      }

    } finally
    {
      endTest();
    }
  }

  /**
   * In a topology where the balance cannot be exactly reached according to the
   * weights, this is testing that the DS is not doing yoyo. The yoyo effect
   * would be a DS keeping going between RSs (going to/back from other RS for
   * ever).
   *
   * RS1 weight=1 ;  RS2 weight=1 ; RS3 weight=1 ; 7DSs.
   * We expect 1 RS with 3 DSs and the 2 other RSs with 2 DS each and no
   * disconnections/reconnections after the very first connections.
   * @throws Exception If a problem occurred
   */
  @Test (enabled=true, groups = "slow")
  public void testNoYoyo3() throws Exception
  {
    String testCase = "testNoYoyo3";

    debugInfo("Starting " + testCase);

    initTest();

    try
    {

      /**
       * RS1 (weight=1) starts
       */

      rs[0] = createReplicationServer(0, 1, testCase);

      /**
       * DS1 starts and connects to RS1
       */

      rd[0] = createReplicationDomain(0, testCase);
      assertEquals(rd[0].getRsServerId(), RS1_ID);

      /**
       * RS2 (weight=1) and R3 (weight=1) start
       */

      rs[1] = createReplicationServer(1, 1, testCase);
      rs[2] = createReplicationServer(2, 1, testCase);
      checkRSConnectionsAndGenId(new int[] {0, 1, 2},
        "Waiting for RSs being connected to peers");

      /**
       * DS2 to DS6 start and connects to RSs
       */
      createReplicationDomains(testCase, 1, 6);

      /**
       * DS7 starts and connects to either RS1 RS2 or RS3 but should stay on it
       */

      int dsIsIndex = 6;
      rd[dsIsIndex] = createReplicationDomain(dsIsIndex, testCase);
      int rsId = rd[dsIsIndex].getRsServerId();
      int rsIndex = rsId - 501;
      int nDSs = getNbDSsConnectedToRS(rsIndex);
      assertEquals(getNbDSsConnectedToRS(rsIndex), 3, " Expected 2 DSs on RS " + rsId);
      debugInfo(testCase + ": DS7 connected to RS " + rsId + ", with " + nDSs + " DSs");

      // Be sure that DS3 stays connected to the same RS during some long time
      // check every second
      int waitTime = 10;
      int elapsedTime = 0;
      while (elapsedTime < waitTime)
      {
        Thread.sleep(1000);
        // Still connected to the right RS ?
        assertEquals(rd[dsIsIndex].getRsServerId(), rsId, "DS7 should still be " +
          "connected to RS " + rsId);
        assertEquals(getNbDSsConnectedToRS(rsIndex), 3, " Expected 2 DSs on RS " + rsId);
        elapsedTime++;
      }

    } finally
    {
      endTest();
    }
  }

  private void createReplicationDomains(String testCase, int start, int end) throws Exception
  {
    for (int i = start; i < end; i++)
    {
      rd[i] = createReplicationDomain(i, testCase);
    }
  }
}
