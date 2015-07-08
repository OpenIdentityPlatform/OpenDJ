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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.replication.service;

import java.util.*;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.service.ReplicationBroker.ReplicationServerInfo;
import org.opends.server.replication.service.ReplicationBroker.Topology;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.Collections.*;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class ReplicationBrokerTest extends DirectoryServerTestCase
{

  private static enum TopologyCtorToUse
  {
    BUILD_WITH_TOPOLOGY_MSG, BUILD_WITH_DS_RS_LISTS;
  }

  private final int CURRENT_RS_ID = 91;
  private final int MISSING_RS_ID = 93;
  private final int ANOTHER_RS_ID = 94;
  private final DSInfo CURRENT_DS = dsInfo(11, CURRENT_RS_ID);
  private final DSInfo OTHER_DS = dsInfo(12, CURRENT_RS_ID);
  private final DSInfo MISSING_DS = dsInfo(13, CURRENT_RS_ID);
  private final ReplicationServerInfo CURRENT_RS = rsInfo(CURRENT_RS_ID,
      CURRENT_DS.getDsId(), OTHER_DS.getDsId());
  private final ReplicationServerInfo MISSING_RS = rsInfo(MISSING_RS_ID,
      MISSING_DS.getDsId());
  private final ReplicationServerInfo ANOTHER_RS = rsInfo(ANOTHER_RS_ID);

  @SuppressWarnings("unchecked")
  private DSInfo dsInfo(int dsServerId, int rsServerId)
  {
    byte z = 0;
    return new DSInfo(dsServerId, null, rsServerId, 0, null,
        false, null, z, z, EMPTY_LIST, EMPTY_LIST, EMPTY_LIST, z);
  }

  private ReplicationServerInfo rsInfo(int rsServerId, Integer... dsIds)
  {
    byte z = 0;
    final RSInfo info = new RSInfo(rsServerId, rsServerId + ":1389", 0, z, 0);
    return new ReplicationServerInfo(info, newSet(dsIds));
  }

  private Map<Integer, ReplicationServerInfo> newMap(ReplicationServerInfo... infos)
  {
    if (infos.length == 0)
    {
      return Collections.emptyMap();
    }
    final Map<Integer, ReplicationServerInfo> map = new HashMap<>();
    for (ReplicationServerInfo info : infos)
    {
      map.put(info.getServerId(), info);
    }
    return map;
  }

  private void assertInvariants(final Topology topo)
  {
    assertThat(topo.replicaInfos).doesNotContainKey(CURRENT_DS.getDsId());
  }

  private ReplicationServerInfo assertContainsRSWithDSs(
      Map<Integer, ReplicationServerInfo> rsInfos,
      ReplicationServerInfo rsInfo, Integer... connectedDSs)
  {
    return assertContainsRSWithDSs(rsInfos, rsInfo, newSet(connectedDSs));
  }

  private ReplicationServerInfo assertContainsRSWithDSs(
      Map<Integer, ReplicationServerInfo> rsInfos,
      ReplicationServerInfo rsInfo, Set<Integer> connectedDSs)
  {
    final ReplicationServerInfo info = find(rsInfos, rsInfo.toRSInfo());
    assertNotNull(info);
    assertThat(info.getConnectedDSs()).containsAll(connectedDSs);
    return info;
  }

  private ReplicationServerInfo find(Map<Integer, ReplicationServerInfo> rsInfos, RSInfo rsInfo)
  {
    for (ReplicationServerInfo info : rsInfos.values())
    {
      if (info.getServerId() == rsInfo.getId())
      {
        return info;
      }
    }
    return null;
  }

  private Topology newTopology(TopologyCtorToUse toUse,
      Map<Integer, DSInfo> replicaInfos, List<RSInfo> rsInfos, int dsServerId, int rsServerId,
      Set<String> rsUrls, Map<Integer, ReplicationServerInfo> previousRSs)
  {
    if (TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG == toUse)
    {
      final TopologyMsg topologyMsg = new TopologyMsg(replicaInfos.values(), rsInfos);
      return new Topology(topologyMsg, dsServerId, rsServerId, rsUrls, previousRSs);
    }
    else if (TopologyCtorToUse.BUILD_WITH_DS_RS_LISTS == toUse)
    {
      return new Topology(replicaInfos, rsInfos, dsServerId, rsServerId, rsUrls, previousRSs);
    }
    Assert.fail("Do not know which Topology constructor to use: " + toUse);
    return null;
  }

  private Map<Integer, DSInfo> newMap(DSInfo... dsInfos)
  {
    final Map<Integer, DSInfo> results = new HashMap<>();
    for (DSInfo dsInfo : dsInfos)
    {
      results.put(dsInfo.getDsId(), dsInfo);
    }
    return results;
  }

  @DataProvider
  public Object[][] topologyCtorProvider() {
    return new Object[][] { { TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG },
      { TopologyCtorToUse.BUILD_WITH_DS_RS_LISTS } };
  }

  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldContainNothing(TopologyCtorToUse toUse)
      throws Exception
  {
    final Topology topo = newTopology(toUse,
        EMPTY_MAP, EMPTY_LIST,
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), EMPTY_SET, EMPTY_MAP);
    assertInvariants(topo);
    assertThat(topo.rsInfos).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void topologyShouldFilterOutCurrentDS()
  {
    final Topology topo = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS, CURRENT_DS), EMPTY_LIST,
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), EMPTY_SET, EMPTY_MAP);
    assertInvariants(topo);
    assertThat(topo.rsInfos).isEmpty();
  }

  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldContainRSWithoutOtherDS(TopologyCtorToUse toUse)
  {
    final Topology topo = newTopology(toUse,
        newMap(OTHER_DS), newList(CURRENT_RS.toRSInfo()),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), EMPTY_SET, EMPTY_MAP);
    assertInvariants(topo);
    assertThat(topo.rsInfos).hasSize(1);
    assertContainsRSWithDSs(topo.rsInfos, CURRENT_RS, CURRENT_DS.getDsId());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void topologyShouldContainRSWithAllDSs_buildWithTopologyMsg()
  {
    final Topology topo = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(CURRENT_DS, OTHER_DS), newList(CURRENT_RS.toRSInfo()),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), EMPTY_SET, EMPTY_MAP);
    assertInvariants(topo);
    assertThat(topo.rsInfos).hasSize(1);
    assertContainsRSWithDSs(topo.rsInfos, CURRENT_RS, CURRENT_RS.getConnectedDSs());
  }

  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldStillContainRS(TopologyCtorToUse toUse) throws Exception
  {
    final Map<Integer, ReplicationServerInfo> previousRSs = newMap(CURRENT_RS);
    final Topology topo = newTopology(toUse,
        newMap(OTHER_DS), newList(CURRENT_RS.toRSInfo()),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), EMPTY_SET, previousRSs);
    assertInvariants(topo);
    assertThat(topo.rsInfos).hasSize(1);
    assertContainsRSWithDSs(topo.rsInfos, CURRENT_RS, CURRENT_RS.getConnectedDSs());
  }

  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldStillContainRSWithNewlyProvidedDSs(TopologyCtorToUse toUse)
  {
    final ReplicationServerInfo CURRENT_RS_WITHOUT_DS = rsInfo(CURRENT_RS_ID);
    final Map<Integer, ReplicationServerInfo> previousRSs = newMap(CURRENT_RS_WITHOUT_DS);
    final Topology topo = newTopology(toUse,
        newMap(OTHER_DS), newList(CURRENT_RS.toRSInfo()),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), EMPTY_SET, previousRSs);
    assertInvariants(topo);
    assertThat(topo.rsInfos).hasSize(1);
    assertContainsRSWithDSs(topo.rsInfos, CURRENT_RS, CURRENT_DS.getDsId(), OTHER_DS.getDsId());
  }

  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldHaveRemovedMissingRS(TopologyCtorToUse toUse)
  {
    final Map<Integer, ReplicationServerInfo> previousRSs = newMap(CURRENT_RS, MISSING_RS);
    final Topology topo = newTopology(toUse,
        newMap(OTHER_DS), newList(CURRENT_RS.toRSInfo()),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), EMPTY_SET, previousRSs);
    assertInvariants(topo);
    assertThat(topo.rsInfos).hasSize(1);
    assertContainsRSWithDSs(topo.rsInfos, CURRENT_RS, CURRENT_RS.getConnectedDSs());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void topologyShouldHaveStampedLocallyConfiguredRSs_buildWithDsRsLists()
  {
    final Set<String> locallyConfigured = newSet(CURRENT_RS.getServerURL());
    final Topology topo = newTopology(TopologyCtorToUse.BUILD_WITH_DS_RS_LISTS,
        newMap(OTHER_DS), newList(CURRENT_RS.toRSInfo(), ANOTHER_RS.toRSInfo()),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), locallyConfigured, EMPTY_MAP);
    assertInvariants(topo);
    assertThat(topo.rsInfos).hasSize(2);
    ReplicationServerInfo currentRS =
        assertContainsRSWithDSs(topo.rsInfos, CURRENT_RS, CURRENT_RS.getConnectedDSs());
    ReplicationServerInfo anotherRS =
        assertContainsRSWithDSs(topo.rsInfos, ANOTHER_RS);
    assertThat(currentRS.isLocallyConfigured()).isTrue();
    assertThat(anotherRS.isLocallyConfigured()).isFalse();
  }

}
