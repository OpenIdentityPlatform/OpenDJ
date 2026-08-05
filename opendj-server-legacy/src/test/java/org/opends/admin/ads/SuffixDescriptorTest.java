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
package org.opends.admin.ads;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/** Tests the id-based {@code equals()}/{@code hashCode()} contract of {@link SuffixDescriptor}. */
@Test(groups = { "precommit", "admin" }, sequential = true)
@SuppressWarnings("javadoc")
public class SuffixDescriptorTest extends DirectoryServerTestCase
{
  private static final DN SUFFIX_DN = DN.valueOf("dc=example,dc=com");
  private static final DN OTHER_SUFFIX_DN = DN.valueOf("dc=example,dc=org");

  @Test
  public void suffixesWithTheSameIdAreEqual()
  {
    ServerDescriptor server1 = server("server1.example.com");
    ServerDescriptor server2 = server("server2.example.com");

    SuffixDescriptor suffix1 = suffix(SUFFIX_DN, server1, server2);
    SuffixDescriptor suffix2 = suffix(SUFFIX_DN, server1, server2);

    assertThat(suffix1.getId()).isEqualTo(suffix2.getId());
    assertThat(suffix1).isEqualTo(suffix2);
    assertThat(suffix1.hashCode()).isEqualTo(suffix2.hashCode());
    assertThat(suffix1.compareTo(suffix2)).isZero();
  }

  @Test
  public void suffixesWithTheSameIdCollapseInASet()
  {
    ServerDescriptor server = server("server1.example.com");

    Set<SuffixDescriptor> suffixes = new HashSet<>();
    suffixes.add(suffix(SUFFIX_DN, server));
    suffixes.add(suffix(SUFFIX_DN, server));

    assertThat(suffixes).hasSize(1);
  }

  @Test
  public void suffixesOnDifferentDnsAreNotEqual()
  {
    ServerDescriptor server = server("server1.example.com");

    assertThat(suffix(SUFFIX_DN, server)).isNotEqualTo(suffix(OTHER_SUFFIX_DN, server));
  }

  @Test
  public void suffixesOnDifferentServersAreNotEqual()
  {
    SuffixDescriptor suffix1 = suffix(SUFFIX_DN, server("server1.example.com"));
    SuffixDescriptor suffix2 = suffix(SUFFIX_DN, server("server2.example.com"));

    assertThat(suffix1).isNotEqualTo(suffix2);
  }

  @Test
  public void suffixIsNotEqualToItsId()
  {
    SuffixDescriptor suffix = suffix(SUFFIX_DN, server("server1.example.com"));

    assertThat(suffix.equals(suffix.getId())).isFalse();
    assertThat(suffix.equals(null)).isFalse();
  }

  /**
   * The id must not depend on the iteration order of the replica set: {@code getReplicas()}
   * hands out a {@code HashSet} of {@code ReplicaDescriptor}s, which do not override
   * {@code hashCode()}.
   */
  @Test
  public void idIsIndependentOfTheReplicaOrder()
  {
    ServerDescriptor server1 = server("server1.example.com");
    ServerDescriptor server2 = server("server2.example.com");
    assertThat(server1.getId()).isLessThan(server2.getId());

    String expectedId = SUFFIX_DN + "-" + server1.getId() + "-" + server2.getId();

    assertThat(suffix(SUFFIX_DN, server1, server2).getId()).isEqualTo(expectedId);
    assertThat(suffix(SUFFIX_DN, server2, server1).getId()).isEqualTo(expectedId);
  }

  private ServerDescriptor server(String hostName)
  {
    Map<ADSContext.ServerProperty, Object> adsProperties = new HashMap<>();
    adsProperties.put(ADSContext.ServerProperty.HOST_NAME, hostName);
    adsProperties.put(ADSContext.ServerProperty.LDAP_PORT, 1389);
    return ServerDescriptor.createStandalone(adsProperties);
  }

  private SuffixDescriptor suffix(DN suffixDN, ServerDescriptor... servers)
  {
    SuffixDescriptor suffix = new SuffixDescriptor(suffixDN, replica(servers[0]));
    for (int i = 1; i < servers.length; i++)
    {
      suffix.addReplica(replica(servers[i]));
    }
    return suffix;
  }

  private ReplicaDescriptor replica(ServerDescriptor server)
  {
    ReplicaDescriptor replica = new ReplicaDescriptor();
    replica.setServer(server);
    return replica;
  }
}
