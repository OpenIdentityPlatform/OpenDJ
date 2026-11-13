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

import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.reactive.LDAPClientConnection2;
import org.forgerock.opendj.reactive.LDAPConnectionHandler2;
import org.forgerock.opendj.server.config.meta.LDAPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.LDAPReader;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Tests trusted PROXY peers against a running LDAP connection handler. */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit" }, sequential = true)
public class LDAPProxyProtocolTestCase extends DirectoryServerTestCase {
    private static final LocalizableMessage STOP = LocalizableMessage.raw("Proxy test finished");
    private static final byte[] BIND = { 0x30, 0x0c, 0x02, 0x01, 0x01, 0x60, 0x07,
            0x02, 0x01, 0x03, 0x04, 0x00, (byte) 0x80, 0x00 };

    @BeforeClass
    public void setup() throws Exception {
        TestCaseUtils.startServer();
    }

    @Test
    public void trustedPeerForwardsClientAddress() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1"));
        try (Socket client = new Socket("127.0.0.1", port)) {
            client.setSoTimeout(5000);
            client.getOutputStream().write("PROXY TCP4 192.0.2.4 198.51.100.2 12345 389\r\n".getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().write(BIND);
            client.getOutputStream().flush();
            assertEquals(client.getInputStream().read(), 0x30, "anonymous bind response");
            ClientConnection connection = handler.getClientConnections().iterator().next();
            assertEquals(connection.getClientAddress(), "192.0.2.4");
            assertEquals(connection.getClientPort(), 12345);
            assertEquals(connection.getRemoteAddress().getHostAddress(), "192.0.2.4");
            assertTrue(connection.getMonitorSummary().contains("source=\"192.0.2.4:12345\""));
            assertTrue(TestCaseUtils.ACCESS_TEXT_WRITER.getMessages().stream()
                    .anyMatch(line -> line.contains("CONNECT conn=" + connection.getConnectionID() + " ")
                            && line.contains("from=192.0.2.4:12345")), "proxied CONNECT was not logged");
        } finally {
            stop(handler);
        }
    }

    @Test
    public void emptyTrustedPeerListRejectsConnection() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port));
        try (Socket client = new Socket("127.0.0.1", port)) {
            client.setSoTimeout(5000);
            client.getOutputStream().write("PROXY UNKNOWN\r\n".getBytes(StandardCharsets.US_ASCII));
            while (client.getInputStream().read() != -1) {
                // A notice may precede close on a plain LDAP connection.
            }
            assertTrue(handler.getClientConnections().isEmpty());
        } finally {
            stop(handler);
        }
    }

    @Test
    public void lockdownRejectsForwardedRemoteClientsButAllowsLoopback() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1", "ds-cfg-send-rejection-notice: false"));
        boolean original = DirectoryServer.lockdownMode();
        try {
            DirectoryServer.setLockdownMode(true);
            try (Socket remote = connect(port, "192.0.2.4")) {
                assertClosed(remote);
            }
            try (Socket loopback = connect(port, "127.0.0.1")) {
                assertEquals(loopback.getInputStream().read(), 0x30, "loopback must reach the LDAP handler");
            }
        } finally {
            DirectoryServer.setLockdownMode(original);
            stop(handler);
        }
    }

    @Test
    public void ipv4DeniedMaskRejectsMappedIpv6FromBothVersions() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1", "ds-cfg-denied-client: 192.0.2.4",
                "ds-cfg-send-rejection-notice: false"));
        try {
            for (boolean binary : new boolean[] { false, true }) {
                try (Socket client = connectMapped(port, binary)) {
                    assertClosed(client);
                }
            }
        } finally {
            stop(handler);
        }
    }

    @Test
    public void ipv4AndMappedIpv6ShareThePerAddressLimit() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1", "ds-cfg-max-client-connections-per-address: 1",
                "ds-cfg-send-rejection-notice: false"));
        try (Socket first = connect(port, "192.0.2.4")) {
            assertSuccessfulBind(first);
            for (boolean binary : new boolean[] { false, true }) {
                try (Socket duplicate = connectMapped(port, binary)) {
                    assertClosed(duplicate);
                }
            }
        } finally {
            stop(handler);
        }
    }

    @Test
    public void limitCountsTheForwardedClientAndReleasesItsSlot() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1",
                "ds-cfg-max-client-connections-per-address: 1",
                "ds-cfg-send-rejection-notice: false"));
        try {
            try (Socket first = connect(port, "192.0.2.4")) {
                assertEquals(first.getInputStream().read(), 0x30);
                try (Socket second = connect(port, "192.0.2.4")) {
                    assertClosed(second);
                }
                try (Socket different = connect(port, "192.0.2.5")) {
                    assertEquals(different.getInputStream().read(), 0x30);
                }
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!handler.getClientConnections().isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            assertTrue(handler.getClientConnections().isEmpty(), "closed clients were not released");
            try (Socket reconnected = connect(port, "192.0.2.4")) {
                assertEquals(reconnected.getInputStream().read(), 0x30, "the closed client's slot was not released");
            }
        } finally {
            stop(handler);
        }
    }

    @Test
    public void firstRequestTimerCoversSilentAndPartialHeaders() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1",
                "ds-cfg-first-request-time-limit: 1 seconds"));
        try (Socket silent = new Socket("127.0.0.1", port);
             Socket partial = new Socket("127.0.0.1", port)) {
            silent.setSoTimeout(5000);
            partial.setSoTimeout(5000);
            partial.getOutputStream().write("PROXY TCP4 ".getBytes(StandardCharsets.US_ASCII));
            partial.getOutputStream().flush();
            assertClosed(silent);
            assertClosed(partial);
        } finally {
            stop(handler);
        }
    }

    @Test
    public void trustedListChangesApplyToNewConnectionsOnly() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1"));
        try (Socket established = connect(port, "192.0.2.4")) {
            assertSuccessfulBind(established);
            assertEquals(handler.applyConfigurationChange(configuration(port)).getResultCode(), ResultCode.SUCCESS);
            try (Socket rejected = new Socket("127.0.0.1", port)) {
                rejected.setSoTimeout(5000);
                assertClosed(rejected);
            }
            established.getOutputStream().write(BIND);
            established.getOutputStream().flush();
            assertSuccessfulBind(established);
        } finally {
            stop(handler);
        }
    }

    @Test
    public void deniedClientMatchesForwardedAddressRatherThanLoadBalancer() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1",
                "ds-cfg-denied-client: 192.0.2.4",
                "ds-cfg-send-rejection-notice: false"));
        try (Socket denied = connect(port, "192.0.2.4"); Socket allowed = connect(port, "192.0.2.5")) {
            assertClosed(denied);
            assertEquals(allowed.getInputStream().read(), 0x30);
        } finally {
            stop(handler);
        }
    }

    @Test
    public void allowedClientMatchesForwardedAddressRatherThanLoadBalancer() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1",
                "ds-cfg-allowed-client: 192.0.2.4",
                "ds-cfg-send-rejection-notice: false"));
        try (Socket denied = connect(port, "192.0.2.5"); Socket allowed = connect(port, "192.0.2.4")) {
            assertClosed(denied);
            assertEquals(allowed.getInputStream().read(), 0x30);
        } finally {
            stop(handler);
        }
    }

    @Test
    public void deniedProxiedClientReceivesConfiguredRejectionNotice() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1",
                "ds-cfg-denied-client: 192.0.2.4",
                "ds-cfg-send-rejection-notice: true"));
        try (Socket denied = connect(port, "192.0.2.4")) {
            LDAPMessage message = new LDAPReader(denied).readMessage();
            assertNotNull(message, "the rejection notice was not sent");
            assertEquals(message.getExtendedResponseProtocolOp().getOID(), "1.3.6.1.4.1.1466.20036");
        } finally {
            stop(handler);
        }
    }

    @Test
    public void unknownHeaderUsesTheTcpPeerAddress() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1"));
        try (Socket client = new Socket("127.0.0.1", port)) {
            client.setSoTimeout(5000);
            client.getOutputStream().write("PROXY UNKNOWN\r\n".getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().write(BIND);
            client.getOutputStream().flush();
            assertEquals(client.getInputStream().read(), 0x30);
            assertEquals(handler.getClientConnections().iterator().next().getClientAddress(), "127.0.0.1");
        } finally {
            stop(handler);
        }
    }

    @Test
    public void proxyConfigurationSchemaUsesPingDsOids() {
        org.forgerock.opendj.ldap.schema.Schema schema = DirectoryServer.getInstance().getServerContext().getSchema();
        org.forgerock.opendj.ldap.schema.AttributeType enabled = schema.getAttributeType("ds-cfg-proxy-protocol-enabled");
        org.forgerock.opendj.ldap.schema.AttributeType allowed =
                schema.getAttributeType("ds-cfg-proxy-protocol-allowed-client");
        assertEquals(enabled.getOID(), "1.3.6.1.4.1.36733.2.1.1.662");
        assertEquals(enabled.getSyntax().getOID(), "1.3.6.1.4.1.36733.2.1.3.3.7");
        assertEquals(allowed.getOID(), "1.3.6.1.4.1.36733.2.1.1.663");
        assertEquals(allowed.getSyntax().getOID(), "1.3.6.1.4.1.36733.2.1.3.3.15");
    }

    @Test
    public void dsconfigCreatesAWorkingProxyHandlerAndUpdatesTrustedPeers() throws Exception {
        int port = TestCaseUtils.findFreePort();
        String name = "PROXY protocol dsconfig test";
        DN dn = DN.valueOf("cn=" + name + ",cn=Connection Handlers,cn=config");
        TestCaseUtils.dsconfig("create-connection-handler", "--type", "ldap", "--handler-name", name,
                "--set", "enabled:true", "--set", "listen-address:127.0.0.1", "--set", "listen-port:" + port,
                "--set", "proxy-protocol-enabled:true", "--set", "proxy-protocol-allowed-client:127.0.0.1");
        try {
            Entry entry = DirectoryServer.getEntry(dn);
            assertEquals(entry.parseAttribute("ds-cfg-proxy-protocol-enabled").asString(), "true");
            assertEquals(entry.parseAttribute("ds-cfg-proxy-protocol-allowed-client").asString(), "127.0.0.1");
            try (Socket established = connect(port, "192.0.2.4")) {
                assertSuccessfulBind(established);
                TestCaseUtils.dsconfig("set-connection-handler-prop", "--handler-name", name,
                        "--remove", "proxy-protocol-allowed-client:127.0.0.1");
                try (Socket rejected = new Socket("127.0.0.1", port)) {
                    rejected.setSoTimeout(5000);
                    assertClosed(rejected);
                }
                established.getOutputStream().write(BIND);
                established.getOutputStream().flush();
                assertSuccessfulBind(established);
            }
        } finally {
            TestCaseUtils.dsconfig("delete-connection-handler", "--handler-name", name);
        }
    }

    @Test
    public void dsconfigEnablesProxyAfterDisablingAndReenablingHandler() throws Exception {
        int port = TestCaseUtils.findFreePort();
        String name = "PROXY protocol restart test";
        TestCaseUtils.dsconfig("create-connection-handler", "--type", "ldap", "--handler-name", name,
                "--set", "enabled:true", "--set", "listen-address:127.0.0.1", "--set", "listen-port:" + port);
        try {
            TestCaseUtils.dsconfig("set-connection-handler-prop", "--handler-name", name,
                    "--set", "proxy-protocol-enabled:true", "--add", "proxy-protocol-allowed-client:127.0.0.1");
            try (Socket direct = new Socket("127.0.0.1", port)) {
                direct.setSoTimeout(5000);
                direct.getOutputStream().write(BIND);
                direct.getOutputStream().flush();
                assertSuccessfulBind(direct);
            }
            TestCaseUtils.dsconfig("set-connection-handler-prop", "--handler-name", name, "--set", "enabled:false");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (true) {
                try (java.net.ServerSocket available = new java.net.ServerSocket()) {
                    available.setReuseAddress(true);
                    available.bind(new java.net.InetSocketAddress("127.0.0.1", port));
                    break;
                } catch (java.net.BindException stillListening) {
                    assertTrue(System.nanoTime() < deadline, "handler did not stop listening");
                    Thread.sleep(25);
                }
            }
            TestCaseUtils.dsconfig("set-connection-handler-prop", "--handler-name", name, "--set", "enabled:true");
            try (Socket direct = new Socket("127.0.0.1", port)) {
                direct.setSoTimeout(5000);
                direct.getOutputStream().write(BIND);
                direct.getOutputStream().flush();
                assertClosed(direct);
            }
            try (Socket proxied = connect(port, "192.0.2.4")) {
                assertSuccessfulBind(proxied);
            }
        } finally {
            TestCaseUtils.dsconfig("delete-connection-handler", "--handler-name", name);
        }
    }

    @Test
    public void malformedHeaderClosesAndLogsAProtocolError() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1"));
        try (Socket client = new Socket("127.0.0.1", port)) {
            client.setSoTimeout(5000);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (handler.getClientConnections().isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            assertFalse(handler.getClientConnections().isEmpty(), "connection was not accepted");
            long id = handler.getClientConnections().iterator().next().getConnectionID();
            client.getOutputStream().write("PROXY TCP4 bad 198.51.100.2 123 389\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().flush();
            assertClosed(client);
            boolean logged = false;
            while (!logged && System.nanoTime() < deadline) {
                logged = TestCaseUtils.ACCESS_TEXT_WRITER.getMessages().stream()
                        .anyMatch(line -> line.contains("DISCONNECT conn=" + id + " ")
                                && line.contains("Invalid PROXY protocol header"));
                if (!logged) {
                    Thread.sleep(25);
                }
            }
            assertTrue(logged, "the PROXY protocol error was not logged");
        } finally {
            stop(handler);
        }
    }

    @Test
    public void aciIpRuleUsesTheForwardedAddress() throws Exception {
        TestCaseUtils.initializeTestBackend(true);
        TestCaseUtils.addEntry("dn: ou=proxy-test,o=test", "objectClass: top", "objectClass: organizationalUnit",
                "ou: proxy-test", "aci: (targetattr=\"*\")(version 3.0; acl \"deny forwarded address\"; "
                        + "deny (read,search) ip=\"192.0.2.5\";)");
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1"));
        try (Socket allowed = connect(port, "192.0.2.4"); Socket denied = connect(port, "192.0.2.5")) {
            assertSuccessfulBind(allowed);
            assertSuccessfulBind(denied);
            LDAPMessage firstAllowed = searchProxyTestEntry(allowed);
            assertEquals(firstAllowed.getProtocolOpType(), LDAP.OP_TYPE_SEARCH_RESULT_ENTRY);
            assertEquals(new LDAPReader(allowed).readMessage().getProtocolOpType(), LDAP.OP_TYPE_SEARCH_RESULT_DONE);
            LDAPMessage firstDenied = searchProxyTestEntry(denied);
            assertEquals(firstDenied.getProtocolOpType(), LDAP.OP_TYPE_SEARCH_RESULT_DONE,
                    "the denied address must not receive the entry");
        } finally {
            stop(handler);
            TestCaseUtils.initializeTestBackend(false);
        }
    }

    @Test
    public void aciSsfRuleUsesTheTrustedSslTlv() throws Exception {
        TestCaseUtils.initializeTestBackend(true);
        TestCaseUtils.addEntry("dn: ou=proxy-test,o=test", "objectClass: top", "objectClass: organizationalUnit",
                "ou: proxy-test", "aci: (targetattr=\"*\")(version 3.0; acl \"deny weak connection\"; "
                        + "deny (read,search) ssf != \"128\";)");
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1"));
        try (Socket secure = connectWithProxySsl(port, true); Socket plain = connectWithProxySsl(port, false)) {
            assertSuccessfulBind(secure);
            assertSuccessfulBind(plain);
            assertEquals(searchProxyTestEntry(secure).getProtocolOpType(), LDAP.OP_TYPE_SEARCH_RESULT_ENTRY);
            assertEquals(new LDAPReader(secure).readMessage().getProtocolOpType(), LDAP.OP_TYPE_SEARCH_RESULT_DONE);
            assertEquals(searchProxyTestEntry(plain).getProtocolOpType(), LDAP.OP_TYPE_SEARCH_RESULT_DONE,
                    "the client without a TLS assertion must not receive the entry");
        } finally {
            stop(handler);
            TestCaseUtils.initializeTestBackend(false);
        }
    }

    private static Socket connectWithProxySsl(int port, boolean ssl) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        socket.getOutputStream().write(proxySslHeader(ssl));
        socket.getOutputStream().write(BIND);
        socket.getOutputStream().flush();
        return socket;
    }

    private static LDAPMessage searchProxyTestEntry(Socket client) throws Exception {
        LDAPWriter<?> writer = LDAP.getWriter(ASN1.getWriter(client.getOutputStream()), 3);
        writer.writeSearchRequest(2, Requests.newSearchRequest("ou=proxy-test,o=test", SearchScope.BASE_OBJECT,
                "(objectClass=*)", "ou"));
        writer.getASN1Writer().flush();
        return new LDAPReader(client).readMessage();
    }

    @Test
    public void legacyHandlerRejectsProxyConfigurationDuringValidationAndInitialization() throws Exception {
        LDAPConnectionHandlerCfg config = configuration(TestCaseUtils.findFreePort());
        LDAPConnectionHandler legacy = new LDAPConnectionHandler();
        List<LocalizableMessage> reasons = new ArrayList<>();
        assertFalse(legacy.isConfigurationAcceptable(config, reasons));
        assertTrue(reasons.toString().contains("PROXY protocol"));
        boolean initialized = false;
        try {
            legacy.initializeConnectionHandler(DirectoryServer.getInstance().getServerContext(), config);
            initialized = true;
            fail("legacy handler accepted a persisted PROXY configuration");
        } catch (InitializationException e) {
            assertTrue(e.getMessage().contains("PROXY protocol"), e.getMessage());
        } finally {
            if (initialized) {
                legacy.finalizeConnectionHandler(STOP);
            }
        }
    }

    @Test
    public void postConnectPluginSeesClientAddressAndAnErrorReleasesItsSlot() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1",
                "ds-cfg-max-client-connections-per-address: 1"));
        AtomicBoolean failOnce = new AtomicBoolean(true);
        AtomicReference<String> seenAddress = new AtomicReference<>();
        InternalDirectoryServerPlugin plugin = new InternalDirectoryServerPlugin(
                DN.valueOf("cn=Proxy Protocol Test Plugin"), EnumSet.of(PluginType.POST_CONNECT), false) {
            @Override
            public PluginResult.PostConnect doPostConnect(ClientConnection connection) {
                if (connection.getConnectionHandler() == handler) {
                    seenAddress.set(connection.getClientAddress());
                    if (failOnce.getAndSet(false)) {
                        throw new NoClassDefFoundError("test post-connect failure");
                    }
                }
                return PluginResult.PostConnect.continueConnectProcessing();
            }
        };
        DirectoryServer.registerInternalPlugin(plugin);
        try {
            try (Socket failed = connect(port, "192.0.2.4")) {
                assertClosed(failed);
            }
            try (Socket next = connect(port, "192.0.2.4")) {
                assertEquals(next.getInputStream().read(), 0x30);
                assertEquals(seenAddress.get(), "192.0.2.4");
            }
        } finally {
            DirectoryServer.deregisterInternalPlugin(plugin);
            stop(handler);
        }
    }

    @Test
    public void sslTlvProvidesStrengthWithoutInventingAClientCertificate() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1"));
        try (Socket client = new Socket("127.0.0.1", port)) {
            client.setSoTimeout(5000);
            client.getOutputStream().write(proxySslHeader(true));
            client.getOutputStream().write(BIND);
            client.getOutputStream().flush();
            assertEquals(client.getInputStream().read(), 0x30);
            LDAPClientConnection2 connection = (LDAPClientConnection2) handler.getClientConnections().iterator().next();
            assertTrue(connection.isSecure());
            assertEquals(connection.getSSF(), 128);
            assertEquals(connection.getClientCertificateChain().length, 0);
        } finally {
            stop(handler);
        }
    }

    @Test
    public void unspecifiedFamilySslTlvUsesPeerAddressAndSecurityStrength() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port, "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1"));
        try (Socket client = new Socket("127.0.0.1", port)) {
            client.setSoTimeout(5000);
            client.getOutputStream().write(proxySslHeader(true, true));
            client.getOutputStream().write(BIND);
            client.getOutputStream().flush();
            assertSuccessfulBind(client);
            LDAPClientConnection2 connection = (LDAPClientConnection2) handler.getClientConnections().iterator().next();
            assertEquals(connection.getClientAddress(), "127.0.0.1");
            assertTrue(connection.isSecure());
            assertEquals(connection.getSSF(), 128);
            assertEquals(connection.getClientCertificateChain().length, 0);
        } finally {
            stop(handler);
        }
    }

    @Test
    public void secureAuthenticationPolicyUsesTheTrustedSslTlv() throws Exception {
        TestCaseUtils.initializeTestBackend(true);
        TestCaseUtils.addEntry("dn: uid=proxy-user,o=test", "objectClass: top", "objectClass: person",
                "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: proxy-user",
                "cn: Proxy User", "sn: User", "userPassword: test-password");
        String policyDn = "cn=Default Password Policy,cn=Password Policies,cn=config";
        boolean original = DirectoryServer.getDefaultPasswordPolicy().isRequireSecureAuthentication();
        LDAPConnectionHandler2 handler = null;
        try {
            assertEquals(setSecureAuthentication(policyDn, true), ResultCode.SUCCESS);
            int port = TestCaseUtils.findFreePort();
            handler = start(configuration(port, "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1"));
            assertEquals(bindWithProxySsl(port, false), ResultCode.INVALID_CREDENTIALS.intValue());
            assertEquals(bindWithProxySsl(port, true), ResultCode.SUCCESS.intValue());
        } finally {
            if (handler != null) {
                stop(handler);
            }
            assertEquals(setSecureAuthentication(policyDn, original), ResultCode.SUCCESS);
            TestCaseUtils.initializeTestBackend(false);
        }
    }

    private static ResultCode setSecureAuthentication(String policyDn, boolean required) {
        return InternalClientConnection.getRootConnection().processModify(Requests.newModifyRequest(policyDn)
                .addModification(ModificationType.REPLACE, "ds-cfg-require-secure-authentication",
                        Boolean.toString(required))).getResultCode();
    }

    private static int bindWithProxySsl(int port, boolean ssl) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(proxySslHeader(ssl));
            LDAPWriter<?> writer = LDAP.getWriter(ASN1.getWriter(socket.getOutputStream()), 3);
            writer.writeBindRequest(1, 3, Requests.newGenericBindRequest("uid=proxy-user,o=test", (byte) 0x80,
                    "test-password".getBytes(StandardCharsets.UTF_8)));
            writer.getASN1Writer().flush();
            return new LDAPReader(socket).readMessage().getBindResponseProtocolOp().getResultCode();
        }
    }

    private static byte[] proxySslHeader(boolean ssl) {
        return proxySslHeader(ssl, false);
    }

    private static byte[] proxySslHeader(boolean ssl, boolean unspecifiedFamily) {
        byte[] cipher = "ECDHE-RSA-AES128-GCM-SHA256".getBytes(StandardCharsets.US_ASCII);
        int addressLength = unspecifiedFamily ? 0 : 12;
        ByteBuffer frame = ByteBuffer.allocate(16 + addressLength + 3 + 5 + 3 + cipher.length);
        frame.put(new byte[] { 13, 10, 13, 10, 0, 13, 10, 81, 85, 73, 84, 10 });
        frame.put((byte) 0x21).put((byte) (unspecifiedFamily ? 0 : 0x11))
                .putShort((short) (addressLength + 3 + 5 + 3 + cipher.length));
        if (!unspecifiedFamily) {
            frame.put(new byte[] { (byte) 192, 0, 2, 4, (byte) 198, 51, 100, 2, 0x30, 0x39, 1, (byte) 0x85 });
        }
        frame.put((byte) 0x20).putShort((short) (5 + 3 + cipher.length)).put((byte) (ssl ? 1 : 0)).putInt(1);
        frame.put((byte) 0x23).putShort((short) cipher.length).put(cipher);
        return frame.array();
    }

    @Test
    public void disconnectDuringPostConnectReleasesTheReservedAddress() throws Exception {
        int port = TestCaseUtils.findFreePort();
        LDAPConnectionHandler2 handler = start(configuration(port,
                "ds-cfg-proxy-protocol-allowed-client: 127.0.0.1",
                "ds-cfg-max-client-connections-per-address: 1"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<ClientConnection> accepted = new AtomicReference<>();
        InternalDirectoryServerPlugin plugin = new InternalDirectoryServerPlugin(
                DN.valueOf("cn=Proxy Protocol Race Plugin"), EnumSet.of(PluginType.POST_CONNECT), false) {
            @Override
            public PluginResult.PostConnect doPostConnect(ClientConnection connection) {
                if (connection.getConnectionHandler() == handler && accepted.compareAndSet(null, connection)) {
                    entered.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test did not release the plugin");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
                return PluginResult.PostConnect.continueConnectProcessing();
            }
        };
        DirectoryServer.registerInternalPlugin(plugin);
        try (Socket first = connect(port, "192.0.2.4")) {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "post-connect plugin was not invoked");
            CountDownLatch disconnectStarted = new CountDownLatch(1);
            CompletableFuture<Void> close = CompletableFuture.runAsync(() -> {
                disconnectStarted.countDown();
                accepted.get().disconnect(DisconnectReason.CLOSED_BY_PLUGIN, false, null);
            });
            assertTrue(disconnectStarted.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (accepted.get().isConnectionValid() && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertFalse(accepted.get().isConnectionValid(), "disconnect did not overlap post-connect");
            release.countDown();
            close.get(5, TimeUnit.SECONDS);
            assertClosed(first);
            try (Socket next = connect(port, "192.0.2.4")) {
                assertEquals(next.getInputStream().read(), 0x30, "address slot leaked during disconnect");
            }
        } finally {
            release.countDown();
            DirectoryServer.deregisterInternalPlugin(plugin);
            stop(handler);
        }
    }

    private static Socket connect(int port, String ip) throws Exception {
        Socket client = new Socket("127.0.0.1", port);
        client.setSoTimeout(5000);
        client.getOutputStream().write(("PROXY TCP4 " + ip + " 198.51.100.2 12345 389\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().write(BIND);
        client.getOutputStream().flush();
        return client;
    }

    private static Socket connectMapped(int port, boolean binary) throws Exception {
        Socket client = new Socket("127.0.0.1", port);
        client.setSoTimeout(5000);
        if (binary) {
            ByteBuffer frame = ByteBuffer.allocate(52);
            frame.put(new byte[] { 13, 10, 13, 10, 0, 13, 10, 81, 85, 73, 84, 10 });
            frame.put((byte) 0x21).put((byte) 0x21).putShort((short) 36);
            frame.put(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff,
                    (byte) 192, 0, 2, 4 });
            frame.put(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });
            frame.putShort((short) 12345).putShort((short) 389);
            client.getOutputStream().write(frame.array());
        } else {
            client.getOutputStream().write("PROXY TCP6 ::ffff:192.0.2.4 ::1 12345 389\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
        }
        client.getOutputStream().write(BIND);
        client.getOutputStream().flush();
        return client;
    }

    private static void assertClosed(Socket client) throws Exception {
        try {
            assertEquals(client.getInputStream().read(), -1, "connection must close without an LDAP response");
        } catch (SocketException expected) {
            // A TCP reset also rejects the connection.
        }
    }

    private static void assertSuccessfulBind(Socket client) throws Exception {
        assertEquals(client.getInputStream().read(), 0x30);
        int length = client.getInputStream().read();
        assertTrue(length > 0 && length < 128, "unexpected bind response length");
        byte[] message = client.getInputStream().readNBytes(length);
        assertEquals(message.length, length);
        assertEquals(message[3], (byte) 0x61);
        assertEquals(message[5], (byte) 0x0a);
        assertEquals(message[7], (byte) 0x00);
    }

    private static LDAPConnectionHandlerCfg configuration(int port, String... values) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("dn: cn=Proxy Test Handler,cn=Connection Handlers,cn=config");
        lines.add("objectClass: top");
        lines.add("objectClass: ds-cfg-connection-handler");
        lines.add("objectClass: ds-cfg-ldap-connection-handler");
        lines.add("cn: Proxy Test Handler");
        lines.add("ds-cfg-java-class: " + LDAPConnectionHandler2.class.getName());
        lines.add("ds-cfg-enabled: true");
        lines.add("ds-cfg-listen-address: 127.0.0.1");
        lines.add("ds-cfg-listen-port: " + port);
        lines.add("ds-cfg-keep-stats: false");
        lines.add("ds-cfg-use-ssl: false");
        lines.add("ds-cfg-allow-start-tls: false");
        lines.add("ds-cfg-proxy-protocol-enabled: true");
        for (String value : values) {
            lines.add(value);
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
