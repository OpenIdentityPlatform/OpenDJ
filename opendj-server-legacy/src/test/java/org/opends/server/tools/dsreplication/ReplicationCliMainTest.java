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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCache;
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
