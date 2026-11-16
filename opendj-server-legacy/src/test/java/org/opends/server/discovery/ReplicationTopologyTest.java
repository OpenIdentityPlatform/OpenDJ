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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.discovery;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.types.HostPort;
import org.forgerock.opendj.server.config.server.ReplicationServiceDiscoveryMechanismCfg;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;

@Test(sequential = true)
public class ReplicationTopologyTest extends DirectoryServerTestCase {
    @Test
    public void rejectsMalformedPriorityAddressesBeforeTheyAreDiscovered() {
        for (String value : Arrays.asList("bad host:389=1", "999.999.999.999:389=1", "...:389=1",
                "[not::ipv6]:389=1", "[2001:::1]:389=1")) {
            try {
                ServerPriorities.parse(Collections.singleton(value));
                fail("Invalid address accepted: " + value);
            } catch (IllegalArgumentException expected) {
                // An unresolvable but syntactically valid host remains allowed.
            }
        }
        ServerPriorities.parse(Arrays.asList("future.example:389=1", "ldap_server.example:389=1",
                "[2001:db8::1]:636=2"));
    }

    @Test
    public void acceptsConfiguredAddressFormsAndPreservesIpv6ZoneCase() {
        ServerPriorities.parse(Arrays.asList("127.1:389=10", "[fe80::1%eth0]:389=20",
                "[fe80::1%ETH0]:389=30"))
                .validateStaticServers(Arrays.asList("127.1:389", "[fe80::1%eth0]:389",
                        "[fe80::1%ETH0]:389", "-other-configured-host-:389"), Collections.emptyList());
    }

    @Test
    public void reportsInvalidPortInConfiguredStaticServer() {
        final String invalidAddress = "ldap.example:2147483648";
        try {
            ServerPriorities.parse(Collections.singleton("ldap.example:389=1"))
                    .validateStaticServers(Collections.singleton(invalidAddress), Collections.emptyList());
            fail("An overflowing server port must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Invalid server port"));
            assertTrue(expected.getMessage().contains(invalidAddress));
        }
    }

    @Test
    public void priorityChangesPreserveTheDiscoveredTopology() throws Exception {
        final DN base = DN.valueOf("dc=example,dc=com");
        final HostPort address = HostPort.valueOf("localhost:1389");
        final ReplicationTopology topology = ReplicationTopology.create("test", null, Collections.singletonList(
                new ReplicationTopology.Replica(base, address, 1)));
        final ReplicationServiceDiscoveryMechanism mechanism = new ReplicationServiceDiscoveryMechanism();
        final ReplicationServiceDiscoveryMechanismCfg previous = mockCfg(ReplicationServiceDiscoveryMechanismCfg.class);
        final ReplicationServiceDiscoveryMechanismCfg updated = mockCfg(ReplicationServiceDiscoveryMechanismCfg.class);
        when(updated.getServerPriority()).thenReturn(new TreeSet<>(Collections.singleton("localhost:1389=10")));
        mechanism.cfg = previous;
        final Field field = ReplicationServiceDiscoveryMechanism.class.getDeclaredField("topology");
        field.setAccessible(true);
        field.set(mechanism, topology);
        try {
            mechanism.applyConfigurationChange(updated);
            assertEquals(mechanism.getPartition(base).getPrimaryServers().size(), 1,
                    "A priority-only change must not discard discovered replicas");
        } finally {
            mechanism.finalizeMechanism();
        }
    }
    @Test
    public void resolvesReplicationLoopbackAddressesAgainstTheOwningServer() throws Exception {
        final Method method = ReplicationServiceDiscoveryMechanism.class.getDeclaredMethod(
                "serverAddresses", Attribute.class, String.class);
        method.setAccessible(true);
        final Attribute configured = new LinkedAttribute("ds-cfg-replication-server",
                "localhost:8989", "127.0.0.2:8990", "[::1]:8991", "other.example:8992");
        @SuppressWarnings("unchecked")
        final Set<HostPort> addresses = (Set<HostPort>) method.invoke(null, configured, "192.0.2.20");
        assertTrue(addresses.contains(HostPort.valueOf("192.0.2.20:8989")));
        assertTrue(addresses.contains(HostPort.valueOf("192.0.2.20:8990")));
        assertTrue(addresses.contains(HostPort.valueOf("192.0.2.20:8991")));
        assertTrue(addresses.contains(HostPort.valueOf("other.example:8992")));
    }

    @Test
    public void selectsOnlyReplicasOfTheRequestedNamingContextAndPrioritizesTheGroup() {
        final DN people = DN.valueOf("dc=example,dc=com");
        final DN other = DN.valueOf("dc=other,dc=com");
        final HostPort primary = HostPort.valueOf("primary.example:1389");
        final HostPort secondary = HostPort.valueOf("secondary.example:1389");
        final HostPort unrelated = HostPort.valueOf("other.example:1389");
        final ReplicationTopology topology = ReplicationTopology.create("test", 1, Arrays.asList(
                new ReplicationTopology.Replica(people, primary, 1),
                new ReplicationTopology.Replica(people, secondary, 2),
                new ReplicationTopology.Replica(other, unrelated, 1)));

        final Partition partition = topology.partitionFor(DN.valueOf("uid=user,dc=example,dc=com"));
        assertEquals(partition.getPrimaryServers().iterator().next().getHostPort(), primary);
        assertEquals(partition.getSecondaryServers().iterator().next().getHostPort(), secondary);
        assertEquals(partition.getPrimaryServers().size(), 1);
        assertEquals(partition.getSecondaryServers().size(), 1);
        assertTrue(topology.partitionFor(DN.valueOf("dc=missing,dc=com")).getPrimaryServers().isEmpty());
    }

    @Test
    public void selectsOnlyReplicationServersConnectedToTheBootstrapServer() {
        final HostPort bootstrap = HostPort.valueOf("bootstrap.example:8989");
        final HostPort peer = HostPort.valueOf("peer.example:8989");
        final HostPort transitive = HostPort.valueOf("transitive.example:8989");
        final HostPort unrelated = HostPort.valueOf("unrelated.example:8989");
        final Map<HostPort, Set<HostPort>> graph = new HashMap<>();
        graph.put(peer, Collections.singleton(bootstrap));
        graph.put(transitive, Collections.singleton(peer));
        graph.put(unrelated, Collections.emptySet());
        final Set<HostPort> result = ReplicationTopology.connectedServers(Collections.singleton(bootstrap), graph);
        assertEquals(result.size(), 3);
        assertTrue(result.containsAll(Arrays.asList(bootstrap, peer, transitive)));
    }

    @Test
    public void withoutPreferredGroupUsesAllReplicasAndTheMostSpecificBase() {
        final HostPort parent = HostPort.valueOf("parent.example:1389");
        final HostPort child1 = HostPort.valueOf("child1.example:1389");
        final HostPort child2 = HostPort.valueOf("child2.example:1389");
        final DN parentBase = DN.valueOf("dc=example,dc=com");
        final DN childBase = DN.valueOf("ou=people,dc=example,dc=com");
        final ReplicationTopology topology = ReplicationTopology.create("test", null, Arrays.asList(
                new ReplicationTopology.Replica(parentBase, parent, 1),
                new ReplicationTopology.Replica(childBase, child1, 1),
                new ReplicationTopology.Replica(childBase, child2, 2)));
        final Partition partition = topology.partitionFor(DN.valueOf("uid=user,ou=people,dc=example,dc=com"));
        assertEquals(partition.getPrimaryServers().size(), 2);
        assertTrue(partition.getSecondaryServers().isEmpty());
        for (Partition.Server server : partition.getPrimaryServers()) {
            assertTrue(server.getHostPort().equals(child1) || server.getHostPort().equals(child2));
        }
    }
}
