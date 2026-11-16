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

import java.util.Collections;
import java.util.Arrays;
import java.net.InetSocketAddress;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.CommonLDAPOptions;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.server.config.server.ReplicationServiceDiscoveryMechanismCfg;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.util.Options;
import com.forgerock.reactive.ServerConnectionFactoryAdapter;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.ProxyBackendLdapAssertions;
import org.opends.server.core.ServerContext;
import org.opends.server.types.HostPort;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/** Exercises topology changes through a real LDAP client and a proxy backend. */
@Test(sequential = true)
public class ReplicationDiscoveryRoutingTest extends DirectoryServerTestCase {
    private static final String REMOTE = "ou=replication-remote,o=test";
    private static final String PROXY = "ou=replication-proxy,o=test";
    private boolean privilegeAdded;

    /** Publishes deterministic snapshots while retaining the real backend and LDAP paths. */
    public static final class Fixture extends ReplicationServiceDiscoveryMechanism {
        static Fixture instance;
        private volatile ReplicationTopology snapshot = ReplicationTopology.create("test", null,
                Collections.emptyList());

        @Override
        public void initializeMechanism(ReplicationServiceDiscoveryMechanismCfg configuration, ServerContext context) {
            cfg = configuration;
            configuration.addReplicationChangeListener(this);
            instance = this;
        }

        @Override
        public ConfigChangeResult applyConfigurationChange(ReplicationServiceDiscoveryMechanismCfg configuration) {
            cfg = configuration;
            notifyChangeListeners();
            return new ConfigChangeResult();
        }

        @Override
        public void finalizeMechanism() {
            cfg.removeReplicationChangeListener(this);
        }

        @Override
        public Partition getPartition(DN target) {
            return snapshot.partitionFor(target);
        }

        void publish(boolean available) {
            snapshot = ReplicationTopology.create("test", null, available
                    ? Collections.singletonList(new ReplicationTopology.Replica(DN.valueOf(REMOTE),
                        new HostPort("localhost", TestCaseUtils.getServerLdapPort()), 1))
                    : Collections.emptyList());
            notifyChangeListeners();
        }

        void publish(int firstPort, int secondPort) {
            snapshot = ReplicationTopology.create("test", null, Arrays.asList(
                    new ReplicationTopology.Replica(DN.valueOf(REMOTE), new HostPort("127.0.0.1", firstPort), 1),
                    new ReplicationTopology.Replica(DN.valueOf(REMOTE), new HostPort("127.0.0.1", secondPort), 1)));
            notifyChangeListeners();
        }
    }

    @BeforeClass
    public void setUp() throws Exception {
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);
        assertEquals(getRootConnection().processModify(Requests.newModifyRequest("cn=Root DNs,cn=config")
                .addModification(ModificationType.ADD, "ds-cfg-default-root-privilege-name", "proxied-auth"))
                .getResultCode(), ResultCode.SUCCESS);
        privilegeAdded = true;
        final String user = "uid=rewrite-user," + REMOTE;
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                "dn: " + REMOTE, "objectClass: top", "objectClass: organizationalUnit", "ou: replication-remote",
                "aci: (targetattr=\"*\")(version 3.0; acl \"replication routing\"; allow (all) userdn=\"ldap:///"
                        + user + "\";)",
                "aci: (version 3.0; acl \"replication moves\"; allow (import,export) userdn=\"ldap:///"
                        + user + "\";)")).getResultCode(), ResultCode.SUCCESS);
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                "dn: ou=moved," + REMOTE, "objectClass: top", "objectClass: organizationalUnit", "ou: moved"))
                .getResultCode(), ResultCode.SUCCESS);
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                "dn: " + user, "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson", "uid: rewrite-user", "cn: Rewrite User", "sn: User",
                "displayName: Remote Value", "manager: uid=rewrite-manager," + REMOTE, "userPassword: password"))
                .getResultCode(), ResultCode.SUCCESS);
        TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", "replication-routing-test",
                "--type", "replication", "--set", "bind-dn:cn=Directory Manager", "--set", "bind-password:password",
                "--set", "bootstrap-replication-server:localhost:4444", "--set", "java-class:" + Fixture.class.getName());
        TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", "replication-routing-test",
                "--set", "base-dn:" + PROXY, "--set", "remote-base-dn:" + REMOTE,
                "--set", "attribute-mapping:description=displayName", "--set", "enabled:true",
                "--set", "shard:replication-routing-test");
    }

    @Test
    public void routesLdapRequestsAfterTopologyChanges() throws Exception {
        try (LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort())) {
            Fixture.instance.publish(true);
            ProxyBackendLdapAssertions.assertRewriteOperations(factory, factory, PROXY, REMOTE,
                    "cn=Directory Manager", "password");
            Fixture.instance.publish(false);
            try (Connection client = factory.getConnection()) {
                client.bind("uid=rewrite-user," + PROXY, "password".toCharArray());
                fail("A removed replica must not receive new requests");
            } catch (LdapException expected) {
                assertEquals(expected.getResult().getResultCode(), ResultCode.UNAVAILABLE);
            }
        }
    }

    @Test
    public void appliesPrioritiesToDiscoveredReplicasAndConfigurationChanges() throws Exception {
        try (LDAPListener first = replica("first"); LDAPListener second = replica("second");
             LDAPConnectionFactory proxy = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
             Connection client = proxy.getConnection()) {
            final int firstPort = first.firstSocketAddress().getPort();
            final int secondPort = second.firstSocketAddress().getPort();
            TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", "replication-routing-test",
                    "--set", "operation-identity-mode:specific", "--set", "primary-load-balancing-algorithm:failover",
                    "--set", "server-priority:127.0.0.1:" + firstPort + "=10",
                    "--set", "server-priority:[::1]:1389=20");
            Fixture.instance.publish(firstPort, secondPort);
            client.bind("cn=Directory Manager", "password".toCharArray());
            assertEquals(client.searchSingleEntry("uid=rewrite-user," + PROXY, SearchScope.BASE_OBJECT,
                    "(objectClass=*)", "description").getAttribute("description").firstValueAsString(), "second");
            TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", "replication-routing-test",
                    "--set", "server-priority:127.0.0.1:" + secondPort + "=10");
            assertEquals(client.searchSingleEntry("uid=rewrite-user," + PROXY, SearchScope.BASE_OBJECT,
                    "(objectClass=*)", "description").getAttribute("description").firstValueAsString(), "first");
        } finally {
            TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", "replication-routing-test",
                    "--reset", "server-priority", "--reset", "operation-identity-mode",
                    "--reset", "primary-load-balancing-algorithm");
            Fixture.instance.publish(false);
        }
    }

    @Test
    public void preservesCaseDistinctIpv6ZonePrioritiesInConfiguration() throws Exception {
        final String lower = "[fe80::1%eth0]:389=20";
        final String upper = "[fe80::1%ETH0]:389=20";
        try {
            TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", "replication-routing-test",
                    "--set", "server-priority:" + lower, "--set", "server-priority:" + upper);
            assertEquals(Fixture.instance.cfg.getServerPriority().size(), 2);
            try (LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
                 Connection client = factory.getConnection()) {
                client.bind("cn=Directory Manager", "password".toCharArray());
                final org.forgerock.opendj.ldap.Attribute values = client.searchSingleEntry(
                        "cn=replication-routing-test,cn=Service Discovery Mechanisms,cn=config",
                        SearchScope.BASE_OBJECT, "(objectClass=*)", "ds-cfg-server-priority")
                        .getAttribute("ds-cfg-server-priority");
                assertEquals(values.size(), 2);
            }
        } finally {
            TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", "replication-routing-test",
                    "--reset", "server-priority");
        }
    }

    private LDAPListener replica(String label) throws Exception {
        final MemoryBackend memory = new MemoryBackend(new LDIFEntryReader(
                "dn:", "objectClass: top", "objectClass: extensibleObject", "",
                "dn: cn=Directory Manager", "objectClass: top", "objectClass: person", "cn: Directory Manager",
                "sn: Manager", "userPassword: password", "",
                "dn: " + REMOTE, "objectClass: top", "objectClass: organizationalUnit", "ou: replication-remote", "",
                "dn: uid=rewrite-user," + REMOTE, "objectClass: top", "objectClass: person",
                "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: rewrite-user",
                "cn: Rewrite User", "sn: User", "displayName: " + label, ""));
        final Options options = Options.defaultOptions();
        return new LDAPListener(Collections.singleton(new InetSocketAddress("127.0.0.1", 0)),
                new ServerConnectionFactoryAdapter(options.get(CommonLDAPOptions.LDAP_DECODE_OPTIONS),
                        Connections.<LDAPClientContext>newServerConnectionFactory(memory)), options);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        if (privilegeAdded) {
            assertEquals(getRootConnection().processModify(Requests.newModifyRequest("cn=Root DNs,cn=config")
                    .addModification(ModificationType.DELETE, "ds-cfg-default-root-privilege-name", "proxied-auth"))
                    .getResultCode(), ResultCode.SUCCESS);
        }
    }
}
