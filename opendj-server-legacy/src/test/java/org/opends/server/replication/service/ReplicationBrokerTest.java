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
 * Copyright 2014-2015 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.service.ReplicationBroker.RSEvaluations;
import org.opends.server.replication.service.ReplicationBroker.ReplicationServerInfo;
import org.opends.server.replication.service.ReplicationBroker.Topology;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.Collections.*;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.replication.service.ReplicationBroker.computeBestReplicationServer;
import static org.opends.server.util.CollectionUtils.*;
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
    return new ReplicationServerInfo(info, newHashSet(dsIds));
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
    return assertContainsRSWithDSs(rsInfos, rsInfo, newHashSet(connectedDSs));
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
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo()),
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
        newMap(CURRENT_DS, OTHER_DS), newArrayList(CURRENT_RS.toRSInfo()),
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
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo()),
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
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo()),
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
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo()),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), EMPTY_SET, previousRSs);
    assertInvariants(topo);
    assertThat(topo.rsInfos).hasSize(1);
    assertContainsRSWithDSs(topo.rsInfos, CURRENT_RS, CURRENT_RS.getConnectedDSs());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void topologyShouldHaveStampedLocallyConfiguredRSs_buildWithDsRsLists()
  {
    final Set<String> locallyConfigured = newHashSet(CURRENT_RS.getServerURL());
    final Topology topo = newTopology(TopologyCtorToUse.BUILD_WITH_DS_RS_LISTS,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), ANOTHER_RS.toRSInfo()),
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

  /**
   * A topology names a replication server before the configuration of the domain lists it: the next
   * topology, built once the configuration lists it, must stamp it as locally configured.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldStampRSAddedToTheConfigurationAfterThePreviousTopology(TopologyCtorToUse toUse)
  {
    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "127.0.0.1:1389", 0, (byte) 0, 0);
    final Topology before = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), newHashSet(CURRENT_RS.getServerURL()), EMPTY_MAP);
    assertThat(find(before.rsInfos, announcedRS).isLocallyConfigured()).isFalse();

    final RSInfo configuredRS = new RSInfo(ANOTHER_RS_ID, "localhost:1389", 0, (byte) 0, 0);
    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), configuredRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(),
        newHashSet(CURRENT_RS.getServerURL(), "localhost:1389"), before.rsInfos);
    assertInvariants(after);
    final ReplicationServerInfo anotherRS = find(after.rsInfos, configuredRS);
    assertThat(anotherRS.isLocallyConfigured()).isTrue();
    assertThat(anotherRS.getServerURL()).isEqualTo("localhost:1389");
    assertThat(find(after.rsInfos, CURRENT_RS.toRSInfo()).isLocallyConfigured()).isTrue();
  }

  /**
   * A configured replication server announces in the topology messages a URL other than the
   * configured one (a port mapped by NAT or by Docker), and the broker hears of it first that way.
   * Once a phase-one handshake has reached it at the configured URL, it is locally configured, and
   * stays so when the next topology message names it with the announced URL again.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void topologyShouldStampRSReachedAtAConfiguredURLItDoesNotAnnounce()
  {
    final Set<String> configured = newHashSet(CURRENT_RS.getServerURL(), "localhost:28989");
    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "localhost:8989", 0, (byte) 0, 0);
    final Topology heard = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), configured, EMPTY_MAP);
    assertThat(find(heard.rsInfos, announcedRS).isLocallyConfigured()).isFalse();

    final RSInfo reachedRS = new RSInfo(ANOTHER_RS_ID, "localhost:28989", 0, (byte) 0, 0);
    final Topology connected = newTopology(TopologyCtorToUse.BUILD_WITH_DS_RS_LISTS,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), reachedRS),
        CURRENT_DS.getDsId(), ANOTHER_RS_ID, configured, heard.rsInfos);
    ReplicationServerInfo anotherRS = find(connected.rsInfos, reachedRS);
    assertThat(anotherRS.isLocallyConfigured()).isTrue();
    assertThat(anotherRS.getServerURL()).isEqualTo("localhost:28989");

    final Topology next = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), ANOTHER_RS_ID, configured, connected.rsInfos);
    anotherRS = find(next.rsInfos, announcedRS);
    assertThat(anotherRS.isLocallyConfigured()).isTrue();
    assertThat(anotherRS.getServerURL()).isEqualTo("localhost:28989");
  }

  /**
   * A replication server the previous topology stamped as locally configured stays so as long as the
   * configuration lists the URL it is known with, even when the topology names it with another URL (a port
   * mapped by NAT or by Docker) and its host name cannot be resolved at that moment: losing the flag
   * would make the broker leave the server it is connected to.
   * <p>
   * The case shows that the host is not resolved only where resolving it fails: a resolver which
   * answers for any name (the fake addresses of a proxy or VPN DNS) skips it.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldKeepStampedAConfiguredRSWithoutResolvingItsHost(TopologyCtorToUse toUse)
  {
    try
    {
      final InetAddress[] answer = InetAddress.getAllByName("rs.invalid");
      throw new SkipException("The resolver answers for rs.invalid with " + Arrays.toString(answer)
          + ": this case could not tell a resolved host from one that is not");
    }
    catch (UnknownHostException expected)
    {
      // The premise of this case: resolving the host fails
    }
    final String unresolvableURL = "rs.invalid:1389";
    final RSInfo connectedRS = new RSInfo(ANOTHER_RS_ID, unresolvableURL, 0, (byte) 0, 0);
    final Map<Integer, ReplicationServerInfo> previousRSs =
        newMap(new ReplicationServerInfo(connectedRS, newHashSet(CURRENT_DS.getDsId())));

    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "rs.invalid:8989", 0, (byte) 0, 0);
    final Topology topo = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(announcedRS),
        CURRENT_DS.getDsId(), ANOTHER_RS_ID, newHashSet(unresolvableURL), previousRSs);
    assertInvariants(topo);
    final ReplicationServerInfo anotherRS = find(topo.rsInfos, announcedRS);
    assertThat(anotherRS.isLocallyConfigured()).isTrue();
    assertThat(anotherRS.getServerURL()).isEqualTo(unresolvableURL);
  }

  /**
   * A replication server is removed from the configuration of the domain: the next topology must no
   * longer stamp it as locally configured.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldNotStampRSRemovedFromTheConfigurationAfterThePreviousTopology(TopologyCtorToUse toUse)
  {
    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "localhost:1389", 0, (byte) 0, 0);
    final Topology before = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(),
        newHashSet(CURRENT_RS.getServerURL(), "localhost:1389"), EMPTY_MAP);
    assertThat(find(before.rsInfos, announcedRS).isLocallyConfigured()).isTrue();

    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), newHashSet(CURRENT_RS.getServerURL()), before.rsInfos);
    assertInvariants(after);
    assertThat(find(after.rsInfos, announcedRS).isLocallyConfigured()).isFalse();
  }

  /**
   * A topology names a replication server before the configuration of the domain lists it, with the URL
   * the configuration then lists it with: the next topology must stamp it as locally configured.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldStampRSAddedToTheConfigurationWithTheURLItAnnounces(TopologyCtorToUse toUse)
  {
    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "localhost:1389", 0, (byte) 0, 0);
    final Topology before = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), newHashSet(CURRENT_RS.getServerURL()), EMPTY_MAP);
    assertThat(find(before.rsInfos, announcedRS).isLocallyConfigured()).isFalse();

    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(),
        newHashSet(CURRENT_RS.getServerURL(), "localhost:1389"), before.rsInfos);
    assertInvariants(after);
    assertThat(find(after.rsInfos, announcedRS).isLocallyConfigured()).isTrue();
  }

  /**
   * The broker has reconnected to a replication server a topology named before the configuration of
   * the domain listed it, and the topology of the new session has the infos of the phase-one handshakes:
   * the periodic check of the best replication server must not reject it as a server which is not
   * locally configured.
   * <p>
   * The other server is not on this host, so the same-host filter keeps only this one, and the check
   * returns it before it reads which server the broker is connected to: the case pins the filter of the
   * locally configured servers only.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void bestServerCheckShouldNotRejectTheRSAddedToTheConfiguration()
  {
    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "localhost:1389", 0, (byte) 0, 0);
    final Topology before = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), newHashSet(CURRENT_RS.getServerURL()), EMPTY_MAP);

    final Topology reconnected = newTopology(TopologyCtorToUse.BUILD_WITH_DS_RS_LISTS,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), ANOTHER_RS_ID,
        newHashSet(CURRENT_RS.getServerURL(), "localhost:1389"), before.rsInfos);
    final RSEvaluations evals = computeBestReplicationServer(false, ANOTHER_RS_ID, new ServerState(),
        reconnected.rsInfos, CURRENT_DS.getDsId(), (byte) 0, 0);
    assertThat(evals.getBestRS()).isNotNull();
    assertThat(evals.getBestRS().getServerId()).as(evals.toString()).isEqualTo(ANOTHER_RS_ID);
  }

  /**
   * The entry of the configuration which lists a replication server stamped as locally configured is
   * written another way, with an equivalent URL: the server must keep the flag, with the configured URL,
   * even when the restart of the session this asks for is refused (while a total update runs).
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldKeepStampedRSWhoseConfiguredURLIsWrittenAnotherWay(TopologyCtorToUse toUse)
  {
    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "localhost:8989", 0, (byte) 0, 0);
    final Topology before = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(),
        newHashSet(CURRENT_RS.getServerURL(), "localhost:8989"), EMPTY_MAP);
    assertThat(find(before.rsInfos, announcedRS).isLocallyConfigured()).isTrue();

    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(),
        newHashSet(CURRENT_RS.getServerURL(), "127.0.0.1:8989"), before.rsInfos);
    assertInvariants(after);
    final ReplicationServerInfo anotherRS = find(after.rsInfos, announcedRS);
    assertThat(anotherRS.isLocallyConfigured()).isTrue();
    assertThat(anotherRS.getServerURL()).isEqualTo("127.0.0.1:8989");
  }

  /**
   * A known replication server which is not locally configured comes with a URL which is only an
   * equivalent of a configured one: the topology message must not resolve it, so the server gets the flag
   * at the next connection only.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldNotResolveTheURLAKnownRSComesWith(TopologyCtorToUse toUse)
  {
    final Set<String> configured = newHashSet(CURRENT_RS.getServerURL(), "localhost:28989");
    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "localhost:8989", 0, (byte) 0, 0);
    final Topology before = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), configured, EMPTY_MAP);
    assertThat(find(before.rsInfos, announcedRS).isLocallyConfigured()).isFalse();

    final RSInfo equivalentRS = new RSInfo(ANOTHER_RS_ID, "127.0.0.1:28989", 0, (byte) 0, 0);
    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), equivalentRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), configured, before.rsInfos);
    assertInvariants(after);
    assertThat(find(after.rsInfos, equivalentRS).isLocallyConfigured()).isFalse();
  }

  /**
   * A known replication server which is not locally configured gets an equivalent of its URL in the
   * configuration of the domain, written another way: the next topology must not resolve it either, so
   * the server gets the flag at the next connection only.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldNotResolveTheURLAKnownRSIsKnownWith(TopologyCtorToUse toUse)
  {
    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "127.0.0.1:28989", 0, (byte) 0, 0);
    final Topology before = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), newHashSet(CURRENT_RS.getServerURL()), EMPTY_MAP);
    assertThat(find(before.rsInfos, announcedRS).isLocallyConfigured()).isFalse();

    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(),
        newHashSet(CURRENT_RS.getServerURL(), "localhost:28989"), before.rsInfos);
    assertInvariants(after);
    assertThat(find(after.rsInfos, announcedRS).isLocallyConfigured()).isFalse();
  }

  /**
   * A known replication server comes with another URL in the next topology (a mapped port) while the
   * configuration of the domain now lists the URL it is known with: it is locally configured, without
   * resolving any host name.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldStampRSAddedToTheConfigurationWithTheURLItIsKnownWith(TopologyCtorToUse toUse)
  {
    final RSInfo knownRS = new RSInfo(ANOTHER_RS_ID, "127.0.0.2:28989", 0, (byte) 0, 0);
    final Topology before = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), knownRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), newHashSet(CURRENT_RS.getServerURL()), EMPTY_MAP);
    assertThat(find(before.rsInfos, knownRS).isLocallyConfigured()).isFalse();

    final RSInfo mappedRS = new RSInfo(ANOTHER_RS_ID, "127.0.0.3:28989", 0, (byte) 0, 0);
    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), mappedRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(),
        newHashSet(CURRENT_RS.getServerURL(), "127.0.0.2:28989"), before.rsInfos);
    assertInvariants(after);
    final ReplicationServerInfo rs = find(after.rsInfos, mappedRS);
    assertThat(rs.isLocallyConfigured()).isTrue();
    assertThat(rs.getServerURL()).isEqualTo("127.0.0.2:28989");
  }

  /**
   * A known replication server with no URL (protocol version below 4) is not locally configured, and the
   * check must not ask the configured URLs for a null one: they are a sorted set, as in the configuration,
   * whose contains(null) throws.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldNotStampKnownRSWithoutURL(TopologyCtorToUse toUse)
  {
    // sorted, as the configuration is: contains(null) throws
    final Set<String> configured = new TreeSet<>(Arrays.asList(CURRENT_RS.getServerURL(), "localhost:8989"));
    final RSInfo noUrlRS = new RSInfo(ANOTHER_RS_ID, null, 0, (byte) 0, 0);
    final Topology before = newTopology(TopologyCtorToUse.BUILD_WITH_TOPOLOGY_MSG,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), noUrlRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), configured, EMPTY_MAP);
    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), noUrlRS),
        CURRENT_DS.getDsId(), CURRENT_RS.getServerId(), configured, before.rsInfos);
    assertThat(find(after.rsInfos, noUrlRS).isLocallyConfigured()).isFalse();
  }

  /**
   * A locally configured replication server, known with the configured URL a phase-one handshake reached
   * it at, comes with another URL (a port mapped by NAT or by Docker), and the entry of the configuration
   * is written another way, as an equivalent of the URL it comes with: the server must keep the flag,
   * with the configured URL.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldKeepStampedRSWhoseConfiguredURLIsWrittenAsTheURLItComesWith(TopologyCtorToUse toUse)
  {
    final RSInfo reachedRS = new RSInfo(ANOTHER_RS_ID, "localhost:28989", 0, (byte) 0, 0);
    final Topology connected = newTopology(TopologyCtorToUse.BUILD_WITH_DS_RS_LISTS,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), reachedRS),
        CURRENT_DS.getDsId(), ANOTHER_RS_ID, newHashSet(CURRENT_RS.getServerURL(), "localhost:28989"), EMPTY_MAP);
    assertThat(find(connected.rsInfos, reachedRS).isLocallyConfigured()).isTrue();

    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "localhost:8989", 0, (byte) 0, 0);
    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), ANOTHER_RS_ID,
        newHashSet(CURRENT_RS.getServerURL(), "127.0.0.1:8989"), connected.rsInfos);
    assertInvariants(after);
    final ReplicationServerInfo rs = find(after.rsInfos, announcedRS);
    assertThat(rs.isLocallyConfigured()).isTrue();
    assertThat(rs.getServerURL()).isEqualTo("127.0.0.1:8989");
  }

  /**
   * A locally configured replication server, known with the configured URL a phase-one handshake reached
   * it at, comes with another URL, and the entry of the configuration is replaced with a URL equivalent to
   * neither: the server loses the flag, and keeps the URL it is known with.
   */
  @Test(dataProvider = "topologyCtorProvider")
  @SuppressWarnings("unchecked")
  public void topologyShouldNotStampRSWhoseConfiguredURLNamesNeitherOfItsURLs(TopologyCtorToUse toUse)
  {
    final RSInfo reachedRS = new RSInfo(ANOTHER_RS_ID, "localhost:28989", 0, (byte) 0, 0);
    final Topology connected = newTopology(TopologyCtorToUse.BUILD_WITH_DS_RS_LISTS,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), reachedRS),
        CURRENT_DS.getDsId(), ANOTHER_RS_ID, newHashSet(CURRENT_RS.getServerURL(), "localhost:28989"), EMPTY_MAP);
    assertThat(find(connected.rsInfos, reachedRS).isLocallyConfigured()).isTrue();

    final RSInfo announcedRS = new RSInfo(ANOTHER_RS_ID, "localhost:8989", 0, (byte) 0, 0);
    final Topology after = newTopology(toUse,
        newMap(OTHER_DS), newArrayList(CURRENT_RS.toRSInfo(), announcedRS),
        CURRENT_DS.getDsId(), ANOTHER_RS_ID,
        newHashSet(CURRENT_RS.getServerURL(), "127.0.0.1:18989"), connected.rsInfos);
    assertInvariants(after);
    final ReplicationServerInfo rs = find(after.rsInfos, announcedRS);
    assertThat(rs.isLocallyConfigured()).isFalse();
    assertThat(rs.getServerURL()).isEqualTo("localhost:28989");
  }
}
