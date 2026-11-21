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
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.forgerock.opendj.ldap.DN;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
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
  public void localDisableAllRemovesDomainBelowBackendBaseDN() throws Exception
  {
    TestCaseUtils.startServer();
    final String baseDN = "o=disable-all";
    final DN backend = DN.valueOf("ds-cfg-backend-id=disable-all,cn=Backends,cn=config");
    final DN nestedDN = DN.valueOf("ou=nested," + baseDN);
    final String domains = ",cn=domains,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config";
    final DN rootDomain = DN.valueOf("cn=disable-all-root" + domains);
    final DN nestedDomain = DN.valueOf("cn=disable-all-nested" + domains);
    final String replicationServer = "127.0.0.1:" + TestCaseUtils.findFreePort();
    TestCaseUtils.addEntries(
        "dn: " + backend,
        "objectClass: top",
        "objectClass: ds-cfg-backend",
        "objectClass: ds-cfg-local-backend",
        "objectClass: ds-cfg-memory-backend",
        "ds-cfg-backend-id: disable-all",
        "ds-cfg-java-class: org.opends.server.backends.MemoryBackend",
        "ds-cfg-base-dn: " + baseDN,
        "ds-cfg-enabled: true",
        "ds-cfg-writability-mode: enabled");
    try
    {
      TestCaseUtils.addEntries(
          "dn: " + baseDN,
          "objectClass: top",
          "objectClass: organization",
          "o: disable-all",
          "",
          "dn: " + nestedDN,
          "objectClass: top",
          "objectClass: organizationalUnit",
          "ou: nested");
      addReplicationDomain(rootDomain, baseDN, replicationServer, 1);
      addReplicationDomain(nestedDomain, nestedDN.toString(), replicationServer, 2);
      assertThat(DirectoryServer.getEntry(rootDomain)).isNotNull();
      assertThat(DirectoryServer.getEntry(nestedDomain)).isNotNull();

      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      final int status = ReplicationCliMain.mainCLI(new String[] {
          "disable", "--configFile", new File(TestCaseUtils.paths.testInstanceRoot, "config/config.ldif").getPath(),
          "--hostname", "127.0.0.1", "--port", String.valueOf(TestCaseUtils.getServerAdminPort()),
          "--bindDN", "cn=Directory Manager", "--adminPassword", "password", "--disableAll", "-X", "-n"
      }, false, output, output);

      assertThat(status).as(output.toString()).isZero();
      assertThat(DirectoryServer.getEntry(rootDomain)).isNull();
      assertThat(DirectoryServer.getEntry(nestedDomain)).isNull();
    }
    finally
    {
      for (DN domain : Arrays.asList(nestedDomain, rootDomain))
      {
        if (DirectoryServer.getEntry(domain) != null)
        {
          final DN externalChangelog = DN.valueOf("cn=external changelog," + domain);
          if (DirectoryServer.getEntry(externalChangelog) != null)
          {
            TestCaseUtils.deleteEntry(externalChangelog);
          }
          TestCaseUtils.deleteEntry(domain);
        }
      }
      TestCaseUtils.deleteEntry(backend);
    }
  }

  private static void addReplicationDomain(DN configDN, String baseDN, String replicationServer, int serverID)
      throws Exception
  {
    TestCaseUtils.addEntries(
        "dn: " + configDN,
        "objectClass: top",
        "objectClass: ds-cfg-replication-domain",
        "cn: " + configDN.rdn().getFirstAVA().getAttributeValue(),
        "ds-cfg-base-dn: " + baseDN,
        "ds-cfg-replication-server: " + replicationServer,
        "ds-cfg-server-id: " + serverID);
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
