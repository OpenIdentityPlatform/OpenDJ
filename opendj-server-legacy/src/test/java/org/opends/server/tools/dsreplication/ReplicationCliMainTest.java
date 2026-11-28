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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.tools.dsreplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ReplicationCliMainTest extends DirectoryServerTestCase
{
  /**
   * The server ids that enable takes the ids of new replication servers and domains outside of are
   * the ones of both topologies and of both servers. The replication server of the second topology
   * was left out before (issue #1207): enable read the first topology twice.
   */
  @Test
  public void usedServerIdsAreTheIdsOfBothTopologiesAndOfBothServers()
  {
    final TopologyCache topology1 = topology(server(null, replica(1)));
    final TopologyCache topology2 = topology(server(4242));

    assertThat(ReplicationCliMain.getUsedServerIds(topology1, topology2, server(4243), server(null, replica(2))))
        .containsOnly(1, 4242, 4243, 2);
  }

  /** A server that is not registered in any administration data has no topology. */
  @Test
  public void usedServerIdsWithoutTopologiesAreTheIdsOfBothServers()
  {
    assertThat(ReplicationCliMain.getUsedServerIds(null, null, server(4243), server(null, replica(2))))
        .containsOnly(4243, 2);
  }

  @Test
  public void statusDisplaysMissingChangesAboveTheIntegerRange() throws Exception
  {
    final ReplicaDescriptor replica = replica(1);
    final DN suffixDN = DN.valueOf("dc=example,dc=com");
    replica.setSuffix(new SuffixDescriptor(suffixDN, replica));
    final ServerDescriptor server = server(null, replica);
    server.getServerProperties().put(ServerDescriptor.ServerProperty.HOST_NAME, "localhost");
    server.getServerProperties().put(ServerDescriptor.ServerProperty.ADMIN_ENABLED, List.of(true));
    server.getServerProperties().put(ServerDescriptor.ServerProperty.ADMIN_PORT, List.of(4444));
    replica.setServer(server);

    final SearchResultEntry monitor = Responses.newSearchResultEntry("cn=replica,cn=monitor");
    monitor.addAttribute("missing-changes", "6442450929");
    final TopologyCache cache = new TopologyCache(new ADSContext(mock(ConnectionWrapper.class)), null, 0);
    final Method readCount = TopologyCache.class.getDeclaredMethod(
        "setMissingChanges", ReplicaDescriptor.class, SearchResultEntry.class);
    readCount.setAccessible(true);
    readCount.invoke(cache, replica, monitor);

    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final ReplicationCliMain cli = new ReplicationCliMain(new PrintStream(output, true, StandardCharsets.UTF_8),
        System.err);
    final Method display = ReplicationCliMain.class.getDeclaredMethod("displayReplicaStatuses",
        List.class, boolean.class, Set.class, Set.class, Set.class, Set.class);
    display.setAccessible(true);
    display.invoke(cli, List.of(Set.of(replica)), true, Set.of(), Set.of(server), new HashSet<>(), new HashSet<>());

    assertThat(output.toString(StandardCharsets.UTF_8).strip().split("\t", -1))
        .hasSize(8).endsWith("6442450929");
  }

  private static TopologyCache topology(ServerDescriptor... servers)
  {
    final TopologyCache topology = mock(TopologyCache.class);
    when(topology.getServers()).thenReturn(new HashSet<>(Arrays.asList(servers)));
    return topology;
  }

  private static ServerDescriptor server(Integer replicationServerId, ReplicaDescriptor... replicas)
  {
    final ServerDescriptor server =
        ServerDescriptor.createStandalone(new HashMap<ADSContext.ServerProperty, Object>());
    if (replicationServerId != null)
    {
      server.getServerProperties().put(ServerDescriptor.ServerProperty.IS_REPLICATION_SERVER, Boolean.TRUE);
      server.getServerProperties().put(ServerDescriptor.ServerProperty.REPLICATION_SERVER_ID, replicationServerId);
    }
    server.setReplicas(new HashSet<>(Arrays.asList(replicas)));
    return server;
  }

  private static ReplicaDescriptor replica(int serverId)
  {
    final ReplicaDescriptor replica = new ReplicaDescriptor();
    replica.setServerId(serverId);
    return replica;
  }
}
