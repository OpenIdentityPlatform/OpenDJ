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
package org.opends.server.backends;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.HostPort;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

/** LDAP proxy integration tests using a real LDAPS backend. */
@Test(sequential = true)
public class ProxyBackendTlsTest extends DirectoryServerTestCase {
    private static final String REMOTE = "ou=tls-remote,o=test";
    private static final String PROXY = "ou=tls-proxy,o=test";
    private static final String BAD_HOST_PROXY = "ou=tls-bad-host,o=test";
    private static final String BAD_TRUST_PROXY = "ou=tls-bad-trust,o=test";
    private static final String PLAIN_PROXY = "ou=tls-plain-port,o=test";
    private boolean privilegeAdded;

    @BeforeClass
    public void setUp() throws Exception {
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);
        final String host = certificateHost();
        final int ldapsPort = TestCaseUtils.findFreePort();
        TestCaseUtils.dsconfig("set-connection-handler-prop", "--handler-name", "LDAPS Connection Handler",
                "--set", "enabled:false");
        TestCaseUtils.dsconfig("set-connection-handler-prop", "--handler-name", "LDAPS Connection Handler",
                "--set", "ssl-cert-nickname:ads-certificate", "--set", "listen-port:" + ldapsPort,
                "--set", "enabled:true");
        TestCaseUtils.dsconfig("create-trust-manager-provider", "--provider-name", "backend-tls-test",
                "--type", "file-based", "--set", "trust-store-file:config/server.keystore",
                "--set", "trust-store-type:JKS", "--set", "trust-store-pin:password", "--set", "enabled:true");
        TestCaseUtils.dsconfig("create-trust-manager-provider", "--provider-name", "backend-tls-untrusted",
                "--type", "file-based", "--set", "trust-store-file:config/client.truststore",
                "--set", "trust-store-type:JKS", "--set", "trust-store-pin:password", "--set", "enabled:true");
        assertEquals(getRootConnection().processModify(Requests.newModifyRequest("cn=Root DNs,cn=config")
                .addModification(ModificationType.ADD, "ds-cfg-default-root-privilege-name", "proxied-auth"))
                .getResultCode(), ResultCode.SUCCESS);
        privilegeAdded = true;
        final String user = "uid=rewrite-user," + REMOTE;
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                "dn: " + REMOTE, "objectClass: top", "objectClass: organizationalUnit", "ou: tls-remote",
                "aci: (targetattr=\"*\")(version 3.0; acl \"tls proxy\"; allow (all) userdn=\"ldap:///" + user + "\";)",
                "aci: (version 3.0; acl \"tls proxy moves\"; allow (import,export) userdn=\"ldap:///" + user + "\";)"))
                .getResultCode(), ResultCode.SUCCESS);
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry("dn: ou=moved," + REMOTE,
                "objectClass: top", "objectClass: organizationalUnit", "ou: moved")).getResultCode(), ResultCode.SUCCESS);
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry("dn: " + user, "objectClass: top",
                "objectClass: person", "objectClass: organizationalPerson", "objectClass: inetOrgPerson",
                "uid: rewrite-user", "cn: Rewrite User", "sn: User", "displayName: Remote Value",
                "manager: uid=rewrite-manager," + REMOTE, "userPassword: password")).getResultCode(), ResultCode.SUCCESS);
        createProxy("tls-good", PROXY, new HostPort(host, ldapsPort));
        final String mismatchedHost = "127.0.0.1".equals(host) ? "localhost" : "127.0.0.1";
        createProxy("tls-bad-host", BAD_HOST_PROXY, new HostPort(mismatchedHost, ldapsPort));
        createProxy("tls-bad-trust", BAD_TRUST_PROXY, new HostPort(host, ldapsPort), "backend-tls-untrusted");
        createProxy("tls-plain-port", PLAIN_PROXY, new HostPort(host, TestCaseUtils.getServerLdapPort()));
    }

    private void createProxy(String name, String proxyBase, HostPort address) throws Exception {
        createProxy(name, proxyBase, address, "backend-tls-test");
    }

    private void createProxy(String name, String proxyBase, HostPort address, String trustProvider) throws Exception {
        TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", name, "--type", "static",
                "--set", "bind-dn:cn=Directory Manager", "--set", "bind-password:password",
                "--set", "primary-server:" + address, "--set", "use-ssl:true",
                "--set", "trust-manager-provider:" + trustProvider);
        TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", name,
                "--set", "base-dn:" + proxyBase, "--set", "remote-base-dn:" + REMOTE,
                "--set", "attribute-mapping:description=displayName", "--set", "enabled:true", "--set", "shard:" + name);
    }

    @Test
    public void forwardsLdapOperationsOverLdaps() throws Exception {
        try (LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort())) {
            ProxyBackendLdapAssertions.assertRewriteOperations(factory, factory, PROXY, REMOTE,
                    "cn=Directory Manager", "password");
        }
    }

    @Test
    public void rejectsUntrustedCertificatesMismatchedHostnamesAndPlainLdapPorts() throws Exception {
        assertBindFails(BAD_HOST_PROXY);
        assertBindFails(BAD_TRUST_PROXY);
        assertBindFails(PLAIN_PROXY);
    }

    private void assertBindFails(String proxyBase) throws Exception {
        try (LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
             Connection client = factory.getConnection()) {
            try {
                client.bind("uid=rewrite-user," + proxyBase, "password".toCharArray());
                fail("TLS must reject the incompatible backend: " + proxyBase);
            } catch (LdapException expected) {
                assertNotNull(expected.getResult());
            }
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        if (privilegeAdded) {
            assertEquals(getRootConnection().processModify(Requests.newModifyRequest("cn=Root DNs,cn=config")
                    .addModification(ModificationType.DELETE, "ds-cfg-default-root-privilege-name", "proxied-auth"))
                    .getResultCode(), ResultCode.SUCCESS);
        }
    }

    private static String certificateHost() throws Exception {
        final Path file = Paths.get(DirectoryServer.getInstanceRoot(), "config", "server.keystore");
        final KeyStore store = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(file)) {
            store.load(input, "password".toCharArray());
        }
        final X509Certificate certificate = (X509Certificate) store.getCertificate("ads-certificate");
        for (Rdn rdn : new LdapName(certificate.getSubjectX500Principal().getName()).getRdns()) {
            if ("CN".equalsIgnoreCase(rdn.getType())) {
                return String.valueOf(rdn.getValue());
            }
        }
        throw new IllegalStateException("The test certificate has no common name");
    }
}
