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
package org.opends.server.protocols.ldap;

import static org.testng.Assert.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.naming.ldap.LdapName;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.reactive.LDAPConnectionHandler2;
import org.forgerock.opendj.server.config.meta.LDAPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.types.Entry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Exercises the real nginx stream module and distinct client addresses. */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit" }, sequential = true)
public class LDAPProxyProtocolNginxTestCase extends DirectoryServerTestCase {
    private static final LocalizableMessage STOP = LocalizableMessage.raw("nginx test finished");

    @BeforeClass
    public void setup() throws Exception {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new SkipException("Docker is required for the nginx integration test");
        }
        TestCaseUtils.startServer();
    }

    @Test(timeOut = 180000)
    public void nginxForwardsClientAddressWithBothProtocolVersions() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port));
        String config = "worker_processes 1; error_log /dev/stderr info; events { worker_connections 128; }\n"
                + "stream { server { listen 1389; proxy_pass host.testcontainers.internal:" + port
                + "; proxy_protocol on; } server { listen 1390; proxy_pass host.testcontainers.internal:"
                + port + "; proxy_protocol v2; } server { listen 1391; proxy_pass host.testcontainers.internal:"
                + port + "; } }\n";
        try (Network network = Network.newNetwork();
             GenericContainer<?> nginx = new GenericContainer<>(DockerImageName.parse("nginx:1.31.4"))
                     .withNetwork(network).withNetworkAliases("proxy")
                     .withAccessToHost(true).withExposedPorts(1389, 1390, 1391)
                     .withCopyToContainer(Transferable.of(config), "/etc/nginx/nginx.conf")
                     .waitingFor(Wait.forListeningPort());
             GenericContainer<?> client = new GenericContainer<>(DockerImageName.parse("python:3.13-alpine"))
                     .withNetwork(network).withCommand("sh", "-c", "sleep 300")
                     .withCopyToContainer(clientProgram(), "/tmp/ldap_client.py")) {
            Testcontainers.exposeHostPorts(port);
            nginx.start();
            Container.ExecResult configTest = nginx.execInContainer("nginx", "-t");
            assertEquals(configTest.getExitCode(), 0, configTest.getStderr());
            Container.ExecResult version = nginx.execInContainer("nginx", "-v");
            assertEquals(version.getExitCode(), 0, version.getStderr());
            assertTrue((version.getStdout() + version.getStderr()).contains("nginx/1.31.4"));
            client.start();
            String clientAddress = addressOn(client, network);
            String proxyAddress = addressOn(nginx, network);
            assertNotEquals(clientAddress, proxyAddress);
            assertEquals(handler.applyConfigurationChange(configuration(port, "127.0.0.1", clientAddress))
                    .getResultCode(), ResultCode.SUCCESS);
            for (int nginxPort : new int[] { 1389, 1390 }) {
                CompletableFuture<Container.ExecResult> request = CompletableFuture.supplyAsync(() -> {
                    try {
                        return client.execInContainer("python", "/tmp/ldap_client.py", "plain", "proxy",
                                String.valueOf(nginxPort));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean found = false;
                while (System.nanoTime() < deadline && !found) {
                    for (ClientConnection connection : handler.getClientConnections()) {
                        if (clientAddress.equals(connection.getClientAddress())) {
                            assertEquals(connection.getRemoteAddress().getHostAddress(), clientAddress);
                            found = TestCaseUtils.ACCESS_TEXT_WRITER.getMessages().stream()
                                    .anyMatch(line -> line.contains("CONNECT conn=" + connection.getConnectionID() + " ")
                                            && line.contains("from=" + clientAddress + ":"));
                        }
                    }
                    if (!found) {
                        Thread.sleep(25);
                    }
                }
                assertTrue(found, "OpenDJ did not expose and log client " + clientAddress + " through nginx " + nginxPort);
                Container.ExecResult result = request.get(10, TimeUnit.SECONDS);
                assertEquals(result.getExitCode(), 0, result.getStderr());
                assertTrue(result.getStdout().contains("bind=success search=success"), result.getStdout());
            }
            assertNginxRejectsRequest(client, 1391);
            assertEquals(handler.applyConfigurationChange(configuration(port, "127.0.0.1", "192.0.2.201"))
                    .getResultCode(), ResultCode.SUCCESS);
            assertNginxRejectsRequest(client, 1390);
            assertEquals(handler.applyConfigurationChange(configuration(port, "192.0.2.200")).getResultCode(),
                    ResultCode.SUCCESS);
            assertNginxRejectsRequest(client, 1389);
        } finally {
            stop(handler);
        }
    }

    private static void assertNginxRejectsRequest(GenericContainer<?> client, int port) throws Exception {
        Container.ExecResult result = client.execInContainer("python", "/tmp/ldap_client.py", "reject", "proxy",
                String.valueOf(port));
        assertEquals(result.getExitCode(), 0, result.getStderr());
        assertEquals(result.getStdout().trim(), "rejected");
    }

    @Test(timeOut = 180000)
    public void nginxForwardsLdapsAndStartTlsWithCertificateVerification() throws Exception {
        KeyManager[] managers = DirectoryServer.getKeyManagerProvider(
                DN.valueOf("cn=Administration,cn=Key Manager Providers,cn=config")).getKeyManagers();
        X509Certificate certificate = null;
        for (KeyManager manager : managers) {
            if (manager instanceof X509KeyManager) {
                X509Certificate[] chain = ((X509KeyManager) manager).getCertificateChain("admin-cert");
                if (chain != null && chain.length > 0) {
                    certificate = chain[0];
                    break;
                }
            }
        }
        assertNotNull(certificate, "admin-cert is required for the TLS fixture");
        String hostname = null;
        if (certificate.getSubjectAlternativeNames() != null) {
            for (java.util.List<?> name : certificate.getSubjectAlternativeNames()) {
                if (Integer.valueOf(2).equals(name.get(0))) {
                    hostname = String.valueOf(name.get(1));
                    break;
                }
            }
        }
        if (hostname == null) {
            for (javax.naming.ldap.Rdn rdn : new LdapName(certificate.getSubjectX500Principal().getName()).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    hostname = String.valueOf(rdn.getValue());
                    break;
                }
            }
        }
        assertNotNull(hostname, "certificate has no DNS identity");
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
        int ldapsPort = TestCaseUtils.findFreePort();
        int startTlsPort = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 ldaps = start(tlsConfiguration(ldapsPort, true));
        LDAPConnectionHandler2 startTls;
        try {
            startTls = start(tlsConfiguration(startTlsPort, false));
        } catch (Exception e) {
            stop(ldaps);
            throw e;
        }
        String config = "worker_processes 1; error_log /dev/stderr info; events { worker_connections 128; }\n"
                + "stream { server { listen 1636; proxy_pass host.testcontainers.internal:" + ldapsPort
                + "; proxy_protocol v2; } server { listen 1388; proxy_pass host.testcontainers.internal:"
                + startTlsPort + "; proxy_protocol v2; } }\n";
        try (Network network = Network.newNetwork();
             GenericContainer<?> nginx = new GenericContainer<>(DockerImageName.parse("nginx:1.31.4"))
                     .withNetwork(network).withNetworkAliases("proxy").withAccessToHost(true)
                     .withExposedPorts(1636, 1388)
                     .withCopyToContainer(Transferable.of(config), "/etc/nginx/nginx.conf")
                     .waitingFor(Wait.forListeningPort());
             GenericContainer<?> client = new GenericContainer<>(DockerImageName.parse("python:3.13-alpine"))
                     .withNetwork(network).withAccessToHost(true).withCommand("sh", "-c", "sleep 300")
                     .withCopyToContainer(Transferable.of(pem), "/tmp/server-cert.pem")
                     .withCopyToContainer(clientProgram(), "/tmp/ldap_client.py")) {
            Testcontainers.exposeHostPorts(ldapsPort, startTlsPort);
            nginx.start();
            Container.ExecResult configTest = nginx.execInContainer("nginx", "-t");
            assertEquals(configTest.getExitCode(), 0, configTest.getStderr());
            client.start();
            String clientAddress = addressOn(client, network);
            assertTlsBind(client, clientAddress, hostname, ldaps, 1636, false);
            assertTlsBind(client, clientAddress, hostname, startTls, 1388, true);
            Container.ExecResult coalesced = client.execInContainer("python", "/tmp/ldap_client.py", "coalesced",
                    "host.testcontainers.internal", String.valueOf(ldapsPort), hostname, clientAddress);
            assertEquals(coalesced.getExitCode(), 0, coalesced.getStderr());
            assertTrue(coalesced.getStdout().contains("tls=success"), coalesced.getStdout());
        } finally {
            stop(startTls);
            stop(ldaps);
        }
    }

    private static void assertTlsBind(GenericContainer<?> client, String clientAddress, String hostname,
            LDAPConnectionHandler2 handler, int port, boolean startTls) throws Exception {
        CompletableFuture<Container.ExecResult> request = CompletableFuture.supplyAsync(() -> {
            try {
                return client.execInContainer("python", "/tmp/ldap_client.py", startTls ? "starttls" : "ldaps",
                        "proxy", String.valueOf(port), hostname);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean found = false;
        while (System.nanoTime() < deadline && !found) {
            for (ClientConnection connection : handler.getClientConnections()) {
                if (clientAddress.equals(connection.getClientAddress()) && connection.isSecure()) {
                    found = true;
                }
            }
            if (!found) {
                Thread.sleep(25);
            }
        }
        Container.ExecResult result = request.get(20, TimeUnit.SECONDS);
        assertTrue(found, "OpenDJ did not observe the secured client " + clientAddress + ": " + result.getStderr());
        assertEquals(result.getExitCode(), 0, result.getStderr());
        assertTrue(result.getStdout().contains("bind=success search=success"), result.getStdout());
    }

    private static Transferable clientProgram() throws Exception {
        return Transferable.of(Files.readAllBytes(Paths.get(System.getProperty("org.opends.server.BuildRoot"),
                "src/test/resources/proxy/ldap_client.py")));
    }

    private static String addressOn(GenericContainer<?> container, Network network) {
        return container.getContainerInfo().getNetworkSettings().getNetworks().values().stream()
                .filter(info -> network.getId().equals(info.getNetworkID()))
                .findFirst().orElseThrow(() -> new AssertionError("container is not on the test network"))
                .getIpAddress();
    }

    private static LDAPConnectionHandlerCfg tlsConfiguration(int port, boolean ssl) throws Exception {
        Entry entry = TestCaseUtils.makeEntry(
                "dn: cn=nginx TLS " + port + ",cn=Connection Handlers,cn=config",
                "objectClass: top", "objectClass: ds-cfg-connection-handler",
                "objectClass: ds-cfg-ldap-connection-handler", "cn: nginx TLS " + port,
                "ds-cfg-java-class: " + LDAPConnectionHandler2.class.getName(), "ds-cfg-enabled: true",
                "ds-cfg-listen-address: 127.0.0.1", "ds-cfg-listen-port: " + port,
                "ds-cfg-keep-stats: false", "ds-cfg-use-ssl: " + ssl, "ds-cfg-allow-start-tls: " + !ssl,
                "ds-cfg-ssl-cert-nickname: admin-cert",
                "ds-cfg-key-manager-provider: cn=Administration,cn=Key Manager Providers,cn=config",
                "ds-cfg-trust-manager-provider: cn=Administration,cn=Trust Manager Providers,cn=config",
                "ds-cfg-proxy-protocol-enabled: true", "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1");
        return InitializationUtils.getConfiguration(LDAPConnectionHandlerCfgDefn.getInstance(), entry);
    }

    private static LDAPConnectionHandlerCfg configuration(int port) throws Exception {
        return configuration(port, "127.0.0.1");
    }

    private static LDAPConnectionHandlerCfg configuration(int port, String trustedPeer) throws Exception {
        return configuration(port, trustedPeer, null);
    }

    private static LDAPConnectionHandlerCfg configuration(int port, String trustedPeer, String allowedClient)
            throws Exception {
        List<String> lines = new ArrayList<>(Arrays.asList(
                "dn: cn=nginx Test Handler,cn=Connection Handlers,cn=config",
                "objectClass: top", "objectClass: ds-cfg-connection-handler",
                "objectClass: ds-cfg-ldap-connection-handler", "cn: nginx Test Handler",
                "ds-cfg-java-class: " + LDAPConnectionHandler2.class.getName(), "ds-cfg-enabled: true",
                "ds-cfg-listen-address: 127.0.0.1", "ds-cfg-listen-port: " + port,
                "ds-cfg-keep-stats: false", "ds-cfg-use-ssl: false", "ds-cfg-allow-start-tls: false",
                "ds-cfg-send-rejection-notice: false", "ds-cfg-proxy-protocol-enabled: true",
                "ds-cfg-proxy-protocol-allowed-client: " + trustedPeer));
        if (allowedClient != null) {
            lines.add("ds-cfg-allowed-client: " + allowedClient);
        }
        Entry entry = TestCaseUtils.makeEntry(lines.toArray(new String[0]));
        return InitializationUtils.getConfiguration(LDAPConnectionHandlerCfgDefn.getInstance(), entry);
    }

    private static LDAPConnectionHandler2 start(LDAPConnectionHandlerCfg config) throws Exception {
        LDAPConnectionHandler2 handler = new LDAPConnectionHandler2();
        handler.initializeConnectionHandler(DirectoryServer.getInstance().getServerContext(), config);
        handler.start();
        return handler;
    }

    private static void stop(LDAPConnectionHandler2 handler) throws Exception {
        handler.processServerShutdown(STOP);
        handler.finalizeConnectionHandler(STOP);
        handler.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(handler.isAlive());
    }
}
