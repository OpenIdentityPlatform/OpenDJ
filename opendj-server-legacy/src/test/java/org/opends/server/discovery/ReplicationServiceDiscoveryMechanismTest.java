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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.ProxyBackendLdapAssertions;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.HostPort;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;

/** Reads an actual ADS registry and replication configuration over the administration connector. */
@Test(sequential = true)
public class ReplicationServiceDiscoveryMechanismTest extends DirectoryServerTestCase {
    @Test
    public void discoversARegisteredReplicaOverTls() throws Exception {
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);
        final String host = administrationCertificateHost();
        final int ldapsPort = TestCaseUtils.findFreePort();
        final int replicationPort = TestCaseUtils.findFreePort();
        final String rsAddress = new HostPort(host, replicationPort).toString();
        TestCaseUtils.dsconfig("set-connection-handler-prop", "--handler-name", "LDAPS Connection Handler",
                "--set", "enabled:false");
        TestCaseUtils.dsconfig("set-connection-handler-prop", "--handler-name", "LDAPS Connection Handler",
                "--set", "ssl-cert-nickname:ads-certificate", "--set", "listen-port:" + ldapsPort,
                "--set", "enabled:true");
        writeTrustStore();
        TestCaseUtils.dsconfig("create-trust-manager-provider", "--provider-name", "discovery-test-trust",
                "--type", "file-based", "--set", "trust-store-file:config/discovery-test.truststore",
                "--set", "trust-store-type:JKS", "--set", "trust-store-pin:password",
                "--set", "enabled:true");
        TestCaseUtils.dsconfig("create-replication-server", "--provider-name", "Multimaster Synchronization",
                "--set", "replication-db-directory:discovery-test-db", "--set", "replication-port:" + replicationPort,
                "--set", "replication-server-id:121");
        TestCaseUtils.dsconfig("create-replication-domain", "--provider-name", "Multimaster Synchronization",
                "--domain-name", "discovery-test", "--set", "base-dn:o=test", "--set", "server-id:122",
                "--set", "replication-server:" + rsAddress, "--set", "group-id:2");

        final Map<ServerProperty, Object> server = new EnumMap<>(ServerProperty.class);
        try (ConnectionWrapper connection = new ConnectionWrapper(new HostPort("localhost", TestCaseUtils.getServerLdapPort()),
                PreferredConnection.Type.LDAP, DN.valueOf("cn=Directory Manager"), "password", 5000, null)) {
            final ADSContext ads = new ADSContext(connection);
            server.put(ServerProperty.ID, "discovery-test");
            server.put(ServerProperty.HOST_NAME, host);
            server.put(ServerProperty.ADMIN_PORT, TestCaseUtils.getServerAdminPort());
            server.put(ServerProperty.ADMIN_ENABLED, true);
            server.put(ServerProperty.LDAP_PORT, TestCaseUtils.getServerLdapPort());
            server.put(ServerProperty.LDAP_ENABLED, true);
            server.put(ServerProperty.LDAPS_PORT, ldapsPort);
            server.put(ServerProperty.LDAPS_ENABLED, true);
            ads.registerServer(server);
            final Map<ServerProperty, Object> unavailable = new EnumMap<>(ServerProperty.class);
            unavailable.put(ServerProperty.ID, "unavailable-discovery-test");
            unavailable.put(ServerProperty.HOST_NAME, host);
            unavailable.put(ServerProperty.ADMIN_PORT, TestCaseUtils.findFreePort());
            unavailable.put(ServerProperty.ADMIN_ENABLED, true);
            unavailable.put(ServerProperty.LDAPS_PORT, TestCaseUtils.findFreePort());
            unavailable.put(ServerProperty.LDAPS_ENABLED, true);
            ads.registerServer(unavailable);
        }

        TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", "real-replication-discovery",
                "--type", "replication", "--set", "bind-dn:cn=Directory Manager", "--set", "bind-password:password",
                "--set", "bootstrap-replication-server:" + new HostPort(host, TestCaseUtils.getServerAdminPort()),
                "--set", "trust-manager-provider:discovery-test-trust", "--set", "primary-group-id:2",
                "--set", "discovery-interval:1s", "--set", "use-ssl:true");
        final ServiceDiscoveryMechanism<?> mechanism = TestCaseUtils.getServerContext()
                .getServiceDiscoveryMechanismConfigManager().getMechanism("real-replication-discovery");
        assertNotNull(mechanism);
        final DN user = DN.valueOf("uid=someone,o=test");
        final CountDownLatch discovered = new CountDownLatch(1);
        mechanism.registerChangeListener(ignored -> discovered.countDown());
        if (mechanism.getPartition(user).getPrimaryServers().isEmpty()) {
            assertTrue(discovered.await(30, TimeUnit.SECONDS), "ADS discovery did not publish a replica");
        }
        final Partition partition = mechanism.getPartition(user);
        assertEquals(partition.getPrimaryServers().size(), 1);
        assertEquals(partition.getPrimaryServers().iterator().next().getHostPort(),
                new HostPort(host, ldapsPort));
        assertTrue(partition.getSecondaryServers().isEmpty());
        assertFalse(mechanism.getPartition(DN.valueOf("dc=unrelated")).getPrimaryServers().iterator().hasNext());

        final String remoteUser = "uid=rewrite-user,o=test";
        assertEquals(getRootConnection().processModify(Requests.newModifyRequest("cn=Root DNs,cn=config")
                .addModification(ModificationType.ADD, "ds-cfg-default-root-privilege-name", "proxied-auth"))
                .getResultCode(), ResultCode.SUCCESS);
        assertEquals(getRootConnection().processModify(Requests.newModifyRequest("o=test")
                .addModification(ModificationType.ADD, "aci",
                        "(targetattr=\"*\")(version 3.0; acl \"discovered proxy\"; allow (all) userdn=\"ldap:///"
                                + remoteUser + "\";)",
                        "(version 3.0; acl \"discovered proxy moves\"; allow (import,export) userdn=\"ldap:///"
                                + remoteUser + "\";)")).getResultCode(), ResultCode.SUCCESS);
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                "dn: ou=moved,o=test", "objectClass: top", "objectClass: organizationalUnit", "ou: moved"))
                .getResultCode(), ResultCode.SUCCESS);
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                "dn: " + remoteUser, "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson", "uid: rewrite-user", "cn: Rewrite User", "sn: User",
                "displayName: Remote Value", "manager: uid=rewrite-manager,o=test", "userPassword: password"))
                .getResultCode(), ResultCode.SUCCESS);
        TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", "discovered-proxy",
                "--set", "base-dn:ou=discovered-proxy,o=test", "--set", "remote-base-dn:o=test",
                "--set", "attribute-mapping:description=displayName", "--set", "enabled:true",
                "--set", "shard:real-replication-discovery");
        try (LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort())) {
            ProxyBackendLdapAssertions.assertRewriteOperations(factory, factory, "ou=discovered-proxy,o=test", "o=test",
                    "cn=Directory Manager", "password");
        } finally {
            getRootConnection().processModify(Requests.newModifyRequest("cn=Root DNs,cn=config")
                    .addModification(ModificationType.DELETE, "ds-cfg-default-root-privilege-name", "proxied-auth"));
        }

        final CountDownLatch removed = new CountDownLatch(1);
        final ServiceDiscoveryChangeListener removedListener = ignored -> {
            if (mechanism.getPartition(user).getPrimaryServers().isEmpty()) {
                removed.countDown();
            }
        };
        mechanism.registerChangeListener(removedListener);
        try (ConnectionWrapper connection = new ConnectionWrapper(new HostPort("localhost", TestCaseUtils.getServerLdapPort()),
                PreferredConnection.Type.LDAP, DN.valueOf("cn=Directory Manager"), "password", 5000, null)) {
            new ADSContext(connection).unregisterServer(server);
        }
        assertTrue(removed.await(30, TimeUnit.SECONDS), "ADS removal was not applied while another node was down");
        mechanism.deregisterChangeListener(removedListener);

        final CountDownLatch restored = new CountDownLatch(1);
        final ServiceDiscoveryChangeListener restoredListener = ignored -> {
            if (!mechanism.getPartition(user).getPrimaryServers().isEmpty()) {
                restored.countDown();
            }
        };
        mechanism.registerChangeListener(restoredListener);
        try (ConnectionWrapper connection = new ConnectionWrapper(new HostPort("localhost", TestCaseUtils.getServerLdapPort()),
                PreferredConnection.Type.LDAP, DN.valueOf("cn=Directory Manager"), "password", 5000, null)) {
            new ADSContext(connection).registerServer(server);
        }
        assertTrue(restored.await(30, TimeUnit.SECONDS), "ADS addition was not applied while another node was down");
        mechanism.deregisterChangeListener(restoredListener);

        final CountDownLatch ldapDiscovered = new CountDownLatch(1);
        final ServiceDiscoveryChangeListener ldapListener = ignored -> {
            final Partition current = mechanism.getPartition(user);
            if (current.getPrimaryServers().stream()
                    .anyMatch(serverInfo -> serverInfo.getHostPort().equals(new HostPort(host, TestCaseUtils.getServerLdapPort())))) {
                ldapDiscovered.countDown();
            }
        };
        mechanism.registerChangeListener(ldapListener);
        TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", "real-replication-discovery",
                "--set", "use-ssl:false");
        assertTrue(ldapDiscovered.await(30, TimeUnit.SECONDS),
                "Administration TLS must use the trust provider even when LDAP TLS is disabled");
        mechanism.deregisterChangeListener(ldapListener);

        TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", "real-replication-discovery",
                "--set", "bootstrap-replication-server:" + new HostPort(host, TestCaseUtils.findFreePort()));
        assertTrue(mechanism.getPartition(user).getPrimaryServers().isEmpty(),
                "Changing the bootstrap server must discard the old topology");
        try (LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
             Connection client = factory.getConnection()) {
            client.bind("uid=rewrite-user,ou=discovered-proxy,o=test", "password".toCharArray());
            fail("Requests must not use the old topology after reconfiguration");
        } catch (LdapException expected) {
            // The new bootstrap server is unavailable, so no replica has been discovered.
        }
    }

    private static String administrationCertificateHost() throws Exception {
        final Path config = Paths.get(DirectoryServer.getInstanceRoot(), "config");
        final char[] password = new String(Files.readAllBytes(config.resolve("admin-keystore.pin")),
                StandardCharsets.UTF_8).trim().toCharArray();
        final KeyStore store = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(config.resolve("admin-keystore"))) {
            store.load(input, password);
        }
        final X509Certificate certificate = (X509Certificate) store.getCertificate("admin-cert");
        for (Rdn rdn : new LdapName(certificate.getSubjectX500Principal().getName()).getRdns()) {
            if ("CN".equalsIgnoreCase(rdn.getType())) {
                return String.valueOf(rdn.getValue());
            }
        }
        throw new IllegalStateException("The test administration certificate has no common name");
    }

    private static void writeTrustStore() throws Exception {
        final Path config = Paths.get(DirectoryServer.getInstanceRoot(), "config");
        final char[] adminPassword = new String(Files.readAllBytes(config.resolve("admin-keystore.pin")),
                StandardCharsets.UTF_8).trim().toCharArray();
        final KeyStore admin = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(config.resolve("admin-keystore"))) {
            admin.load(input, adminPassword);
        }
        final KeyStore ldap = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(config.resolve("server.keystore"))) {
            ldap.load(input, "password".toCharArray());
        }
        final KeyStore trust = KeyStore.getInstance("JKS");
        trust.load(null, null);
        trust.setCertificateEntry("admin", admin.getCertificate("admin-cert"));
        trust.setCertificateEntry("ldap", ldap.getCertificate("ads-certificate"));
        try (OutputStream output = Files.newOutputStream(config.resolve("discovery-test.truststore"))) {
            trust.store(output, "password".toCharArray());
        }
    }
}
