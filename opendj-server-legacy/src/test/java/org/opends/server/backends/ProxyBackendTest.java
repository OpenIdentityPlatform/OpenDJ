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

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl;
import org.opends.server.protocols.ldap.LDAPControl;
import org.forgerock.opendj.server.config.server.ProxyBackendCfg;
import org.forgerock.opendj.server.config.server.StaticServiceDiscoveryMechanismCfg;
import org.forgerock.util.Options;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.discovery.ProxyBackendDiscoveryFixture;
import org.opends.server.discovery.ServiceDiscoveryMechanismConfigManager;
import org.opends.server.discovery.StaticServiceDiscoveryMechanism;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.*;
import org.opends.server.types.*;
import com.forgerock.opendj.ldap.controls.AffinityControl;
import com.forgerock.reactive.ServerConnectionFactoryAdapter;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test(sequential = true)
public class ProxyBackendTest extends DirectoryServerTestCase {
    LDAPConnectionFactory factory;

    @BeforeClass
    public void setUp() throws Exception {
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);
        assertEquals(getRootConnection().processModify(Requests.newModifyRequest("cn=Root DNs,cn=config")
                .addModification(ModificationType.ADD, "ds-cfg-default-root-privilege-name", "proxied-auth"))
                .getResultCode(), ResultCode.SUCCESS);

        factory =new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        try(Connection connection = getConnection()) {
            assertThat(connection.isValid()).isTrue();
        }
    }

    @AfterClass(alwaysRun = true)
    public void revokeProxyPrivilege() throws Exception {
        assertEquals(getRootConnection().processModify(Requests.newModifyRequest("cn=Root DNs,cn=config")
                .addModification(ModificationType.DELETE, "ds-cfg-default-root-privilege-name", "proxied-auth"))
                .getResultCode(), ResultCode.SUCCESS);
    }

    Connection getConnection() throws LdapException {
        final Connection connection = factory.getConnection();
        connection.bind("cn=Directory Manager", "password".toCharArray());
        return connection;
    }

    @Test
    public void sharesUserConnectionsOnlyAfterEachClientAuthenticates() throws Exception {
        final String remoteBase = "ou=user-pool-remote,o=test";
        final String proxyBase = "ou=user-pool-proxy,o=test";
        final String remoteUser = "uid=pool-user," + remoteBase;
        final String proxyUser = "uid=pool-user," + proxyBase;
        final String remoteOtherUser = "uid=pool-other," + remoteBase;
        final String proxyOtherUser = "uid=pool-other," + proxyBase;
        final String mechanism = "user-pool-integration";
        boolean mechanismCreated = false;
        boolean backendCreated = false;
        try {
            assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                    "dn: " + remoteBase, "objectClass: top", "objectClass: organizationalUnit",
                    "ou: user-pool-remote", "aci: (targetattr=\"*\")(version 3.0; acl \"pool user\"; "
                            + "allow (read,search,compare) userdn=\"ldap:///" + remoteUser + "\";)",
                    "aci: (targetattr=\"cn\")(version 3.0; acl \"deny other compare\"; "
                            + "deny (compare) userdn=\"ldap:///" + remoteOtherUser + "\";)"))
                    .getResultCode(), ResultCode.SUCCESS);
            assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                    "dn: " + remoteUser, "objectClass: top", "objectClass: person",
                    "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: pool-user",
                    "cn: Pool User", "sn: User", "userPassword: pool-password")).getResultCode(),
                    ResultCode.SUCCESS);
            assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                    "dn: " + remoteOtherUser, "objectClass: top", "objectClass: person",
                    "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: pool-other",
                    "cn: Other User", "sn: User", "userPassword: other-password")).getResultCode(),
                    ResultCode.SUCCESS);
            TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", mechanism,
                    "--type", "static", "--set", "primary-server:localhost:" + TestCaseUtils.getServerLdapPort(),
                    "--set", "use-ssl:false");
            mechanismCreated = true;
            TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", mechanism,
                    "--set", "base-dn:" + proxyBase, "--set", "remote-base-dn:" + remoteBase,
                    "--set", "enabled:true", "--set", "shard:" + mechanism);
            backendCreated = true;
            try (Connection first = factory.getConnection(); Connection second = factory.getConnection();
                 Connection rejected = factory.getConnection(); Connection other = factory.getConnection()) {
                assertEquals(first.bind(proxyUser, "pool-password".toCharArray()).getResultCode(), ResultCode.SUCCESS);
                assertEquals(second.bind(proxyUser, "pool-password".toCharArray()).getResultCode(), ResultCode.SUCCESS);
                try {
                    rejected.bind(proxyUser, "wrong-password".toCharArray());
                    Assert.fail("A client must authenticate even when a pool for its DN already exists");
                } catch (LdapException e) {
                    assertEquals(e.getResult().getResultCode(), ResultCode.INVALID_CREDENTIALS);
                }
                assertEquals(other.bind(proxyOtherUser, "other-password".toCharArray()).getResultCode(), ResultCode.SUCCESS);
                try (Connection direct = factory.getConnection()) {
                    direct.bind(remoteOtherUser, "other-password".toCharArray());
                    assertCompareDenied(direct, remoteUser);
                }
                assertCompareDenied(other, proxyUser);
                assertEquals(first.compare(proxyUser, "cn", "Pool User").getResultCode(), ResultCode.COMPARE_TRUE);
                final List<ClientConnection> remoteConnections = remoteConnections(DN.valueOf(remoteUser), 2);
                remoteConnections.get(0).disconnect(DisconnectReason.OTHER, false, null);
                assertEventuallyReadable(first, proxyUser);
                assertEventuallyReadable(second, proxyUser);
                first.close();
                assertEventuallyReadable(second, proxyUser);
                remoteConnections(DN.valueOf(remoteUser), 1).get(0).disconnect(DisconnectReason.OTHER, false, null);
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean unavailable = false;
                while (System.nanoTime() < deadline && !unavailable) {
                    try {
                        second.searchSingleEntry(proxyUser, SearchScope.BASE_OBJECT, "(objectClass=*)", "cn");
                    } catch (LdapException e) {
                        unavailable = e.getResult().getResultCode().equals(ResultCode.UNAVAILABLE);
                    }
                    if (!unavailable) Thread.sleep(25);
                }
                assertTrue(unavailable, "An exhausted pool must require another bind");
                assertEquals(second.bind(proxyUser, "pool-password".toCharArray()).getResultCode(), ResultCode.SUCCESS);
                assertEventuallyReadable(second, proxyUser);
                final InternalClientConnection internal = new InternalClientConnection(new AuthenticationInfo());
                assertEquals(internal.processSimpleBind(proxyUser, "pool-password").getResultCode(), ResultCode.SUCCESS);
                Assert.assertFalse(internal.getAuthenticationInfo().isAuthenticated(),
                        "An internal bind must not change the internal connection's identity");
                remoteConnections(DN.valueOf(remoteUser), 1);

                final ClientConnection serverClient = remoteConnections(DN.valueOf(proxyUser), 1).get(0);
                final Field listeners = ClientConnection.class.getDeclaredField("authenticationChangeListeners");
                listeners.setAccessible(true);
                synchronized (serverClient) {
                    assertEquals(((List<?>) listeners.get(serverClient)).size(), 1);
                }
                final ProxyBackend backend = (ProxyBackend) TestCaseUtils.getServerContext().getBackendConfigManager()
                        .findBackendForEntry(DN.valueOf(proxyUser));
                backend.finalizeBackend();
                synchronized (serverClient) {
                    assertEquals(((List<?>) listeners.get(serverClient)).size(), 0,
                            "A disabled backend must release its callbacks from live clients");
                }
            }
            remoteConnections(DN.valueOf(remoteUser), 0);
        } finally {
            if (mechanismCreated && !backendCreated) {
                TestCaseUtils.dsconfig("delete-service-discovery-mechanism", "--mechanism-name", mechanism);
            }
            getRootConnection().processDelete(DN.valueOf(remoteUser));
            getRootConnection().processDelete(DN.valueOf(remoteOtherUser));
            getRootConnection().processDelete(DN.valueOf(remoteBase));
        }
    }

    private static List<ClientConnection> remoteConnections(DN user, int count) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<ClientConnection> matches = Collections.emptyList();
        while (System.nanoTime() < deadline) {
            matches = new ArrayList<>();
            for (ConnectionHandler<?> handler : DirectoryServer.getConnectionHandlers()) {
                for (ClientConnection client : handler.getClientConnections()) {
                    if (user.equals(client.getAuthenticationInfo().getAuthenticationDN())) {
                        matches.add(client);
                    }
                }
            }
            if (matches.size() == count) return matches;
            Thread.sleep(25);
        }
        Assert.fail("Expected " + count + " remote user connections, found " + matches.size());
        return matches;
    }

    private static void assertEventuallyReadable(Connection client, String dn) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        LdapException last = null;
        while (System.nanoTime() < deadline) {
            try {
                Assert.assertNotNull(client.searchSingleEntry(dn, SearchScope.BASE_OBJECT, "(objectClass=*)", "cn"));
                return;
            } catch (LdapException e) {
                last = e;
                Thread.sleep(25);
            }
        }
        throw new AssertionError("User's remaining pooled connection was not available", last);
    }

    private static void assertCompareDenied(Connection client, String dn) throws Exception {
        try {
            Assert.assertNotEquals(client.compare(dn, "cn", "Pool User").getResultCode(), ResultCode.COMPARE_TRUE,
                    "The other user must not be permitted to compare the protected value");
        } catch (LdapException e) {
            assertEquals(e.getResult().getResultCode(), ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        }
    }

    @Test
    public void usesUserConnectionsWhenRemoteServerDoesNotAdvertiseProxyControl() throws Exception {
        final String proxyBase = "ou=no-proxy-control,o=test";
        final String proxyUser = "uid=user," + proxyBase;
        final String mechanism = "no-proxy-control";
        final org.forgerock.opendj.ldap.MemoryBackend remote = new org.forgerock.opendj.ldap.MemoryBackend(
                new LDIFEntryReader("dn:", "objectClass: top", "objectClass: extensibleObject", "",
                        "dn: dc=remote", "objectClass: top", "objectClass: domain", "dc: remote", "",
                        "dn: uid=user,dc=remote", "objectClass: top", "objectClass: person",
                        "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: user",
                        "cn: User", "sn: User", "userPassword: password", ""));
        final Options options = Options.defaultOptions();
        boolean mechanismCreated = false;
        boolean backendCreated = false;
        try (LDAPListener listener = new LDAPListener(Collections.singleton(new InetSocketAddress("127.0.0.1", 0)),
                new ServerConnectionFactoryAdapter(options.get(CommonLDAPOptions.LDAP_DECODE_OPTIONS),
                        Connections.<LDAPClientContext>newServerConnectionFactory(remote)), options)) {
            TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", mechanism,
                    "--type", "static", "--set", "bind-dn:cn=unused,dc=remote",
                    "--set", "bind-password:unused", "--set", "primary-server:127.0.0.1:"
                            + listener.firstSocketAddress().getPort(), "--set", "use-ssl:false");
            mechanismCreated = true;
            TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", mechanism,
                    "--set", "base-dn:" + proxyBase, "--set", "remote-base-dn:dc=remote",
                    "--set", "enabled:true", "--set", "shard:" + mechanism);
            backendCreated = true;
            try (Connection client = factory.getConnection()) {
                assertEquals(client.bind(proxyUser, "password".toCharArray()).getResultCode(), ResultCode.SUCCESS);
                Assert.assertNotNull(client.searchSingleEntry(proxyUser, SearchScope.BASE_OBJECT,
                        "(objectClass=*)", "cn"));
            }
        } finally {
            if (mechanismCreated && !backendCreated) {
                TestCaseUtils.dsconfig("delete-service-discovery-mechanism", "--mechanism-name", mechanism);
            }
        }
    }

    @Test
    public void authenticatesBinaryPasswordUsingUserConnections() throws Exception {
        final String name = "binary-password-proxy";
        final String proxyBase = "ou=binary-password,o=test";
        final org.forgerock.opendj.ldap.MemoryBackend remote = new org.forgerock.opendj.ldap.MemoryBackend(
                new LDIFEntryReader("dn:", "objectClass: top", "objectClass: extensibleObject", "",
                        "dn: dc=remote", "objectClass: top", "objectClass: domain", "dc: remote", "",
                        "dn: uid=user,dc=remote", "objectClass: top", "objectClass: inetOrgPerson",
                        "uid: user", "cn: User", "sn: User", "userPassword:: /w==", ""));
        final Options options = Options.defaultOptions();
        boolean mechanismCreated = false;
        boolean backendCreated = false;
        try (LDAPListener listener = new LDAPListener(Collections.singleton(new InetSocketAddress("127.0.0.1", 0)),
                new ServerConnectionFactoryAdapter(options.get(CommonLDAPOptions.LDAP_DECODE_OPTIONS),
                        Connections.<LDAPClientContext>newServerConnectionFactory(remote)), options)) {
            TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", name,
                    "--type", "static", "--set", "primary-server:127.0.0.1:"
                            + listener.firstSocketAddress().getPort());
            mechanismCreated = true;
            TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", name,
                    "--set", "base-dn:" + proxyBase, "--set", "remote-base-dn:dc=remote",
                    "--set", "enabled:true", "--set", "shard:" + name);
            backendCreated = true;
            try (Connection client = factory.getConnection()) {
                assertEquals(client.bind(Requests.newSimpleBindRequest("uid=user," + proxyBase,
                        new byte[] {(byte) 0xff})).getResultCode(), ResultCode.SUCCESS);
            }
        } finally {
            if (backendCreated) {
                TestCaseUtils.dsconfig("delete-backend", "--backend-name", name);
            }
            if (mechanismCreated) {
                TestCaseUtils.dsconfig("delete-service-discovery-mechanism", "--mechanism-name", name);
            }
        }
    }

    @Test
    public void rejectsIncompleteStaticDiscoveryCredentials() throws Exception {
        final StaticServiceDiscoveryMechanismCfg configuration = mockCfg(StaticServiceDiscoveryMechanismCfg.class);
        when(configuration.getBindDN()).thenReturn(DN.valueOf("cn=service"));
        when(configuration.getBindPassword()).thenReturn(null);
        final List<org.forgerock.i18n.LocalizableMessage> reasons = new ArrayList<>();
        Assert.assertFalse(new StaticServiceDiscoveryMechanism().isConfigurationChangeAcceptable(configuration, reasons));
        assertTrue(reasons.stream().anyMatch(reason -> reason.toString().contains("must be specified together")));
    }

    @Test
    public void switchesToUserConnectionsWhenServiceCredentialsAreRemoved() throws Exception {
        try (LDAPListener primary = testServer("primary")) {
            final ProxyBackend backend = priorityBackend(Collections.singletonList(primary.firstSocketAddress().getPort()),
                    Collections.emptyList());
            final Field field = ProxyBackend.class.getDeclaredField("discovery");
            field.setAccessible(true);
            final StaticServiceDiscoveryMechanism mechanism = (StaticServiceDiscoveryMechanism) field.get(backend);
            when(mechanism.cfg.getBindDN()).thenReturn(null);
            when(mechanism.cfg.getBindPassword()).thenReturn(null);
            backend.serviceChanged(mechanism);
            Assert.assertNull(backend.factory, "Removed credentials must not remain usable");
            Assert.assertNotNull(backend.bindFactory, "User binds must remain available without service credentials");
            final Field routeField = ProxyBackend.class.getDeclaredField("staticRoute");
            routeField.setAccessible(true);
            final Object route = routeField.get(backend);
            final java.lang.reflect.Method select = ProxyBackend.class.getDeclaredMethod("usesUserConnections", route.getClass());
            select.setAccessible(true);
            assertTrue((Boolean) select.invoke(null, route), "The route must use client-authenticated connections");
            backend.finalizeBackend();
        }
    }

    @Test
    public void retainsOneHealthyIdleConnectionForActiveSessions() throws Exception {
        final Class<?> type = Class.forName(ProxyBackend.class.getName() + "$UserPool");
        final java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object pool = constructor.newInstance();
        final java.lang.reflect.Method addSession = type.getDeclaredMethod("addSession", Connection.class);
        final java.lang.reflect.Method expire = type.getDeclaredMethod("expireIdle");
        final java.lang.reflect.Method close = type.getDeclaredMethod("close");
        addSession.setAccessible(true);
        expire.setAccessible(true);
        close.setAccessible(true);
        final Field idleField = type.getDeclaredField("idle");
        idleField.setAccessible(true);
        try (Connection first = factory.getConnection(); Connection second = factory.getConnection()) {
            addSession.invoke(pool, first);
            addSession.invoke(pool, second);
            for (Object idle : (Deque<?>) idleField.get(pool)) {
                final Field returnedAt = idle.getClass().getDeclaredField("returnedAt");
                returnedAt.setAccessible(true);
                returnedAt.setLong(idle, System.nanoTime() - TimeUnit.MINUTES.toNanos(6));
            }
            expire.invoke(pool);
            assertEquals(((Deque<?>) idleField.get(pool)).size(), 1,
                    "The last healthy connection must remain available to active clients");
            try (Connection disconnected = factory.getConnection()) {
                addSession.invoke(pool, disconnected);
                disconnected.close();
                for (Object idle : (Deque<?>) idleField.get(pool)) {
                    final Field returnedAt = idle.getClass().getDeclaredField("returnedAt");
                    returnedAt.setAccessible(true);
                    returnedAt.setLong(idle, System.nanoTime() - TimeUnit.MINUTES.toNanos(6));
                }
                expire.invoke(pool);
                assertTrue(second.isValid(), "A disconnected idle connection must not displace a healthy one");
                assertEquals(((Deque<?>) idleField.get(pool)).size(), 1);
            }
        } finally {
            close.invoke(pool);
        }
    }

    @Test
    public void replacesDisconnectedConnectionsWhenUserPoolIsFull() throws Exception {
        final Class<?> type = Class.forName(ProxyBackend.class.getName() + "$UserPool");
        final java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object pool = constructor.newInstance();
        final java.lang.reflect.Method addSession = type.getDeclaredMethod("addSession", Connection.class);
        final java.lang.reflect.Method borrow = type.getDeclaredMethod("borrow");
        final java.lang.reflect.Method close = type.getDeclaredMethod("close");
        addSession.setAccessible(true);
        borrow.setAccessible(true);
        close.setAccessible(true);
        try {
            for (int i = 0; i < 16; i++) {
                final Connection disconnected = factory.getConnection();
                addSession.invoke(pool, disconnected);
                disconnected.close();
            }
            try (Connection replacement = factory.getConnection()) {
                addSession.invoke(pool, replacement);
                assertTrue(replacement.isValid(), "A successful bind must replace invalid pooled connections");
                try (Connection acquired = (Connection) borrow.invoke(pool)) {
                    assertTrue(acquired.isValid());
                }
            }
        } finally {
            close.invoke(pool);
        }
    }

    @Test
    public void unreachableSecondaryDoesNotDisableProxyAuthorization() throws Exception {
        final org.forgerock.opendj.ldap.MemoryBackend memory = new org.forgerock.opendj.ldap.MemoryBackend(
                new LDIFEntryReader("dn:", "objectClass: top", "objectClass: extensibleObject",
                        "supportedControl: 2.16.840.1.113730.3.4.18", ""));
        final Options options = Options.defaultOptions();
        try (LDAPListener primary = new LDAPListener(Collections.singleton(new InetSocketAddress("127.0.0.1", 0)),
                new ServerConnectionFactoryAdapter(options.get(CommonLDAPOptions.LDAP_DECODE_OPTIONS),
                        Connections.<LDAPClientContext>newServerConnectionFactory(memory)), options);
             ServerSocket unused = new ServerSocket(0)) {
            final int unavailable = unused.getLocalPort();
            unused.close();
            final ProxyBackend backend = priorityBackend(Collections.singletonList(primary.firstSocketAddress().getPort()),
                    Collections.singletonList(unavailable));
            final Field routeField = ProxyBackend.class.getDeclaredField("staticRoute");
            routeField.setAccessible(true);
            final Object route = routeField.get(backend);
            final java.lang.reflect.Method select = ProxyBackend.class.getDeclaredMethod("usesUserConnections", route.getClass());
            select.setAccessible(true);
            try {
                Assert.assertFalse((Boolean) select.invoke(null, route),
                        "An unreachable secondary must not force user mode when a primary supports proxy authorization");
                final org.forgerock.opendj.ldap.MemoryBackend withoutProxy = new org.forgerock.opendj.ldap.MemoryBackend(
                        new LDIFEntryReader("dn:", "objectClass: top", "objectClass: extensibleObject", ""));
                final Field nextProbe = route.getClass().getDeclaredField("nextCapabilityProbe");
                nextProbe.setAccessible(true);
                try (LDAPListener recovered = new LDAPListener(
                        Collections.singleton(new InetSocketAddress("127.0.0.1", unavailable)),
                        new ServerConnectionFactoryAdapter(options.get(CommonLDAPOptions.LDAP_DECODE_OPTIONS),
                                Connections.<LDAPClientContext>newServerConnectionFactory(withoutProxy)), options)) {
                    nextProbe.setLong(route, 0);
                    assertTrue((Boolean) select.invoke(null, route),
                            "A recovered secondary without proxy support must select user mode");
                }
                nextProbe.setLong(route, 0);
                Assert.assertFalse((Boolean) select.invoke(null, route),
                        "The automatic mode must be re-evaluated after a server becomes unavailable");
            } finally {
                backend.factory.close();
                backend.bindFactory.close();
            }
        }
    }

    @Test(dependsOnMethods = "rewritesDnAndAttributesThroughLdap")
    public void distributesUserBindsAcrossPrimaryServersByDn() throws Exception {
        final String remoteBase = "dc=userpool";
        final String proxyBase = "ou=userpool-affinity,o=test";
        final String first = "user0";
        final int firstHash = DN.valueOf("uid=" + first + "," + remoteBase).hashCode();
        final int firstIndex = firstHash == Integer.MIN_VALUE ? 0 : Math.abs(firstHash) % 2;
        String other = null;
        for (int i = 1; i < 100; i++) {
            final String candidate = "user" + i;
            final int hash = DN.valueOf("uid=" + candidate + "," + remoteBase).hashCode();
            if ((hash == Integer.MIN_VALUE ? 0 : Math.abs(hash) % 2) != firstIndex) {
                other = candidate;
                break;
            }
        }
        Assert.assertNotNull(other, "A DN mapping to the other primary");
        try (LDAPListener firstServer = userAffinityServer("first", first, other, remoteBase);
             LDAPListener secondServer = userAffinityServer("second", first, other, remoteBase)) {
            TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", "user-affinity",
                    "--type", "static", "--set", "primary-server:127.0.0.1:" + firstServer.firstSocketAddress().getPort(),
                    "--set", "primary-server:127.0.0.1:" + secondServer.firstSocketAddress().getPort(),
                    "--set", "use-ssl:false");
            TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", "user-affinity",
                    "--set", "base-dn:" + proxyBase, "--set", "remote-base-dn:" + remoteBase,
                    "--set", "enabled:true", "--set", "shard:user-affinity");
            final List<String> servers = new ArrayList<>();
            for (String user : Arrays.asList(first, other)) {
                final String dn = "uid=" + user + "," + proxyBase;
                try (Connection client = factory.getConnection()) {
                    client.bind(dn, "password".toCharArray());
                    servers.add(client.searchSingleEntry(dn, SearchScope.BASE_OBJECT, "(objectClass=*)", "description")
                            .getAttribute("description").firstValueAsString());
                }
            }
            Assert.assertNotEquals(servers.get(0), servers.get(1),
                    "DN affinity should distribute different users across available primaries");
        }
    }

    private LDAPListener userAffinityServer(String label, String first, String second, String base) throws Exception {
        return userAffinityServer(label, first, second, base, "password");
    }

    private LDAPListener userAffinityServer(String label, String first, String second, String base, String password)
            throws Exception {
        final org.forgerock.opendj.ldap.MemoryBackend memory = new org.forgerock.opendj.ldap.MemoryBackend(
                new LDIFEntryReader("dn:", "objectClass: top", "objectClass: extensibleObject", "",
                        "dn: " + base, "objectClass: top", "objectClass: domain", "dc: userpool", "",
                        "dn: uid=" + first + "," + base, "objectClass: top", "objectClass: person",
                        "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: " + first,
                        "cn: " + first, "sn: User", "userPassword: " + password, "description: " + label, "",
                        "dn: uid=" + second + "," + base, "objectClass: top", "objectClass: person",
                        "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: " + second,
                        "cn: " + second, "sn: User", "userPassword: " + password, "description: " + label, ""));
        final Options options = Options.defaultOptions();
        return new LDAPListener(Collections.singleton(new InetSocketAddress("127.0.0.1", 0)),
                new ServerConnectionFactoryAdapter(options.get(CommonLDAPOptions.LDAP_DECODE_OPTIONS),
                        Connections.<LDAPClientContext>newServerConnectionFactory(memory)), options);
    }

    @Test(dependsOnMethods = "rewritesDnAndAttributesThroughLdap")
    public void userBindUsesSecondaryOnlyWhenPrimaryIsUnavailable() throws Exception {
        final String base = "dc=userpool";
        final String proxyBase = "ou=userpool-failover,o=test";
        try (LDAPListener secondary = userAffinityServer("secondary", "user0", "user1", base);
             ServerSocket unavailable = new ServerSocket(0)) {
            final int port = unavailable.getLocalPort();
            unavailable.close();
            TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", "user-failover",
                    "--type", "static", "--set", "primary-server:127.0.0.1:" + port,
                    "--set", "secondary-server:127.0.0.1:" + secondary.firstSocketAddress().getPort(),
                    "--set", "use-ssl:false");
            TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", "user-failover",
                    "--set", "base-dn:" + proxyBase, "--set", "remote-base-dn:" + base,
                    "--set", "enabled:true", "--set", "shard:user-failover");
            try (Connection client = factory.getConnection()) {
                final String dn = "uid=user0," + proxyBase;
                assertEquals(client.bind(dn, "password".toCharArray()).getResultCode(), ResultCode.SUCCESS);
                assertEquals(client.searchSingleEntry(dn, SearchScope.BASE_OBJECT, "(objectClass=*)", "description")
                        .getAttribute("description").firstValueAsString(), "secondary");
            }
        }
    }

    @Test(dependsOnMethods = "rewritesDnAndAttributesThroughLdap")
    public void userBindReturnsUnavailableWhenAllServersAreDown() throws Exception {
        final String proxyBase = "ou=userpool-offline,o=test";
        try (ServerSocket unused = new ServerSocket(0)) {
            final int port = unused.getLocalPort();
            unused.close();
            TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", "user-offline",
                    "--type", "static", "--set", "primary-server:127.0.0.1:" + port,
                    "--set", "use-ssl:false");
            TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", "user-offline",
                    "--set", "base-dn:" + proxyBase, "--set", "enabled:true", "--set", "shard:user-offline");
            try (Connection client = factory.getConnection()) {
                try {
                    client.bind("uid=user0," + proxyBase, "password".toCharArray());
                    Assert.fail("Bind must fail when every server is unavailable");
                } catch (LdapException e) {
                    assertEquals(e.getResult().getResultCode(), ResultCode.UNAVAILABLE);
                }
            }
        }
    }

    @Test(dependsOnMethods = "rewritesDnAndAttributesThroughLdap")
    public void userBindDoesNotRetryInvalidCredentialsOnSecondary() throws Exception {
        final String base = "dc=userpool";
        final String proxyBase = "ou=userpool-reject,o=test";
        try (LDAPListener primary = userAffinityServer("primary", "user0", "user1", base, "new-password");
             LDAPListener secondary = userAffinityServer("secondary", "user0", "user1", base, "old-password")) {
            final ResultCode expectedFailure;
            try (LDAPConnectionFactory directFactory = new LDAPConnectionFactory("127.0.0.1",
                    primary.firstSocketAddress().getPort()); Connection direct = directFactory.getConnection()) {
                try {
                    direct.bind("uid=user0," + base, "old-password".toCharArray());
                    Assert.fail("The primary fixture must reject the old password");
                    return;
                } catch (LdapException e) {
                    final ResultCode code = e.getResult().getResultCode();
                    assertTrue(code.equals(ResultCode.INVALID_CREDENTIALS) || code.equals(ResultCode.CLIENT_SIDE_SERVER_DOWN),
                            "Unexpected direct bind result: " + code);
                    expectedFailure = code.equals(ResultCode.INVALID_CREDENTIALS)
                            ? ResultCode.INVALID_CREDENTIALS : ResultCode.UNAVAILABLE;
                }
            }
            TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", "user-reject",
                    "--type", "static", "--set", "primary-server:127.0.0.1:" + primary.firstSocketAddress().getPort(),
                    "--set", "secondary-server:127.0.0.1:" + secondary.firstSocketAddress().getPort(),
                    "--set", "use-ssl:false");
            TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", "user-reject",
                    "--set", "base-dn:" + proxyBase, "--set", "remote-base-dn:" + base,
                    "--set", "enabled:true", "--set", "shard:user-reject");
            try (Connection client = factory.getConnection()) {
                try {
                    client.bind("uid=user0," + proxyBase, "old-password".toCharArray());
                    final String selected = client.searchSingleEntry("uid=user0," + proxyBase,
                            SearchScope.BASE_OBJECT, "(objectClass=*)", "description")
                            .getAttribute("description").firstValueAsString();
                    Assert.fail("A bad password on a reachable primary must not be tried on " + selected);
                } catch (LdapException e) {
                    assertEquals(e.getResult().getResultCode(), expectedFailure);
                }
            }
        }
    }

    @Test
    public void rewritesDnAndAttributesThroughLdap() throws Exception {
        final String remoteBase = "ou=rewrite-remote,o=test";
        final String proxyBase = "ou=rewrite-proxy,o=test";
        final String remoteUser = "uid=rewrite-user," + remoteBase;
        final String remoteManager = "uid=rewrite-manager," + remoteBase;
        final String mechanism = "rewrite-integration";
        final String backend = "rewrite-integration";
        boolean mechanismCreated = false;
        boolean backendCreated = false;
        boolean localBackendCreated = false;

        try {
            assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                    "dn: " + remoteBase, "objectClass: top", "objectClass: organizationalUnit",
                    "ou: rewrite-remote",
                    "aci: (targetattr=\"*\")(version 3.0; acl \"rewrite tests\"; allow (all) userdn=\"ldap:///"
                            + remoteUser + "\";)",
                    "aci: (version 3.0; acl \"rewrite moves\"; allow (import,export) userdn=\"ldap:///"
                            + remoteUser + "\";)")).getResultCode(), ResultCode.SUCCESS);
            assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                    "dn: ou=moved," + remoteBase, "objectClass: top", "objectClass: organizationalUnit",
                    "ou: moved")).getResultCode(), ResultCode.SUCCESS);
            assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                    "dn: " + remoteUser, "objectClass: top", "objectClass: person",
                    "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: rewrite-user",
                    "cn: Rewrite User", "sn: User", "displayName: Remote Value",
                    "manager: " + remoteManager, "userPassword: password")).getResultCode(), ResultCode.SUCCESS);
            TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", mechanism,
                    "--type", "static", "--set", "bind-dn:cn=Directory Manager",
                    "--set", "bind-password:password", "--set", "primary-server:localhost:" + TestCaseUtils.getServerLdapPort(),
                    "--set", "use-ssl:false");
            mechanismCreated = true;
            TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", backend,
                    "--set", "base-dn:" + proxyBase, "--set", "remote-base-dn:" + remoteBase,
                    "--set", "attribute-mapping:description=displayName", "--set", "enabled:true",
                    "--set", "shard:" + mechanism);
            backendCreated = true;

            final String childBase = "ou=local," + proxyBase;
            TestCaseUtils.dsconfig("create-backend", "--type", "memory", "--backend-name", "rewrite-local-child",
                    "--set", "base-dn:" + childBase, "--set", "enabled:true");
            localBackendCreated = true;
            final MemoryBackend childBackend = (MemoryBackend) TestCaseUtils.getServerContext()
                    .getBackendConfigManager().getLocalBackendById("rewrite-local-child");
            childBackend.addEntry(TestCaseUtils.makeEntry("dn: " + childBase,
                    "objectClass: top", "objectClass: organizationalUnit", "ou: local"), null);
            childBackend.addEntry(TestCaseUtils.makeEntry("dn: uid=local-child," + childBase,
                    "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                    "objectClass: inetOrgPerson", "uid: local-child", "cn: Local Child", "sn: Child"), null);
            assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry("dn: ou=local," + remoteBase,
                    "objectClass: top", "objectClass: organizationalUnit", "ou: local")).getResultCode(),
                    ResultCode.SUCCESS);
            assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry("dn: uid=local-child,ou=local," + remoteBase,
                    "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                    "objectClass: inetOrgPerson", "uid: local-child", "cn: Remote Child", "sn: Child"))
                    .getResultCode(), ResultCode.SUCCESS);

            try (Connection admin = getConnection()) {
                final Set<DN> entries = new HashSet<>();
                try (ConnectionEntryReader reader = admin.search(Requests.newSearchRequest("o=test",
                        SearchScope.WHOLE_SUBTREE, "(uid=rewrite-user)", "uid"))) {
                    while (reader.hasNext()) {
                        if (reader.isEntry()) {
                            entries.add(reader.readEntry().getName());
                        } else {
                            reader.readReference();
                        }
                    }
                    reader.readResult();
                }
                assertTrue(entries.contains(DN.valueOf(remoteUser)));
                assertTrue(entries.contains(DN.valueOf("uid=rewrite-user," + proxyBase)));
                final org.forgerock.opendj.ldap.Entry child = admin.searchSingleEntry(proxyBase,
                        SearchScope.WHOLE_SUBTREE, "(uid=local-child)", "uid", "cn");
                assertEquals(child.getName(), DN.valueOf("uid=local-child," + childBase));
                assertEquals(child.getAttribute("cn").firstValueAsString(), "Local Child");
                entries.clear();
                try (ConnectionEntryReader reader = admin.search(Requests.newSearchRequest("",
                        SearchScope.WHOLE_SUBTREE, "(uid=rewrite-user)", "uid"))) {
                    while (reader.hasNext()) {
                        if (reader.isEntry()) {
                            entries.add(reader.readEntry().getName());
                        } else {
                            reader.readReference();
                        }
                    }
                    reader.readResult();
                }
                assertTrue(entries.contains(DN.valueOf("uid=rewrite-user," + proxyBase)));
            }

            ProxyBackendLdapAssertions.assertRewriteOperations(factory, factory, proxyBase, remoteBase,
                    "cn=Directory Manager", "password");
        } finally {
            if (localBackendCreated) {
                TestCaseUtils.dsconfig("delete-backend", "--backend-name", "rewrite-local-child");
            }
            if (backendCreated) {
                TestCaseUtils.dsconfig("delete-backend", "--backend-name", mechanism);
            }
            if (mechanismCreated) {
                TestCaseUtils.dsconfig("delete-service-discovery-mechanism", "--mechanism-name", mechanism);
            }
            getRootConnection().processDelete(DN.valueOf("uid=created," + remoteBase));
            getRootConnection().processDelete(DN.valueOf("uid=renamed,ou=moved," + remoteBase));
            getRootConnection().processDelete(DN.valueOf(remoteUser));
            getRootConnection().processDelete(DN.valueOf("uid=local-child,ou=local," + remoteBase));
            getRootConnection().processDelete(DN.valueOf("ou=local," + remoteBase));
            getRootConnection().processDelete(DN.valueOf("ou=moved," + remoteBase));
            getRootConnection().processDelete(DN.valueOf(remoteBase));
        }
    }

    private LDAPListener testServer(String label) throws Exception {
        final org.forgerock.opendj.ldap.MemoryBackend memory =
                new org.forgerock.opendj.ldap.MemoryBackend(new LDIFEntryReader(
                "dn:", "objectClass: top", "objectClass: extensibleObject", "",
                "dn: cn=service", "objectClass: top", "objectClass: person", "cn: service", "sn: service",
                "userPassword: service-password", "",
                "dn: uid=user", "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson", "uid: user", "sn: user", "cn: user",
                "description: " + label, "userPassword: " + label + "-password", ""));
        final Options options = Options.defaultOptions();
        return new LDAPListener(Collections.singleton(new InetSocketAddress("127.0.0.1", 0)),
                new ServerConnectionFactoryAdapter(options.get(CommonLDAPOptions.LDAP_DECODE_OPTIONS),
                        Connections.<LDAPClientContext>newServerConnectionFactory(memory)), options);
    }

    private ProxyBackend priorityBackend(List<Integer> primaryPorts, List<Integer> secondaryPorts) throws Exception {
        final StaticServiceDiscoveryMechanism mechanism =
                ProxyBackendDiscoveryFixture.mechanism(primaryPorts, secondaryPorts);
        mechanism.cfg = mockCfg(StaticServiceDiscoveryMechanismCfg.class);
        when(mechanism.cfg.getBindDN()).thenReturn(DN.valueOf("cn=service"));
        when(mechanism.cfg.getBindPassword()).thenReturn("service-password");
        final ServiceDiscoveryMechanismConfigManager manager = Mockito.mock(ServiceDiscoveryMechanismConfigManager.class);
        Mockito.doReturn(mechanism).when(manager).getMechanism("priority-test");
        final ServerContext context = Mockito.mock(ServerContext.class);
        when(context.getServiceDiscoveryMechanismConfigManager()).thenReturn(manager);
        final ProxyBackendCfg configuration = mockCfg(ProxyBackendCfg.class);
        when(configuration.getShard()).thenReturn("priority-test");
        final ProxyBackend backend = new ProxyBackend();
        backend.configureBackend(configuration, context);
        return backend;
    }

    private ConnectionFactory backendFactory(ProxyBackend backend, String name) throws Exception {
        final Field field = ProxyBackend.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ConnectionFactory) field.get(backend);
    }

    private AffinityControl secondaryAffinity() {
        for (int i = 0; i < 100; i++) {
            final ByteString value = ByteString.valueOfUtf8(Integer.toString(i));
            if (value.hashCode() != Integer.MIN_VALUE && Math.abs(value.hashCode()) % 2 == 1) {
                return AffinityControl.newControl(value, false);
            }
        }
        throw new AssertionError("No affinity value for secondary group");
    }

    private void assertServer(ProxyBackend backend, String expected, boolean forceSecondaryAffinity) throws Exception {
        try (ConnectionFactory factory = backendFactory(backend, "factory");
             ConnectionFactory bindFactory = backendFactory(backend, "bindFactory")) {
            final SearchRequest search = Requests.newSearchRequest("uid=user", SearchScope.BASE_OBJECT,
                    "(objectClass=*)", "description");
            final org.forgerock.opendj.ldap.requests.BindRequest bind =
                    Requests.newSimpleBindRequest("uid=user", (expected + "-password").toCharArray());
            if (forceSecondaryAffinity) {
                search.addControl(secondaryAffinity());
                bind.addControl(secondaryAffinity());
            }
            try (Connection connection = factory.getConnection()) {
                assertTrue(connection.searchSingleEntry(search).getAttribute("description")
                        .contains(ByteString.valueOfUtf8(expected)));
            }
            try (Connection connection = bindFactory.getConnection()) {
                assertEquals(connection.bind(bind).getResultCode(), ResultCode.SUCCESS);
            }
        }
    }

    @Test
    public void prefersPrimaryAndUsesSecondaryOnlyWhenAllPrimariesAreUnavailable() throws Exception {
        try (LDAPListener primary = testServer("primary"); LDAPListener primary2 = testServer("primary2");
             LDAPListener secondary = testServer("secondary"); LDAPListener secondary2 = testServer("secondary2");
             ServerSocket unusedPort = new ServerSocket(0)) {
            final int primaryPort = primary.firstSocketAddress().getPort();
            final int primary2Port = primary2.firstSocketAddress().getPort();
            final int secondaryPort = secondary.firstSocketAddress().getPort();
            final int secondary2Port = secondary2.firstSocketAddress().getPort();
            final int unavailablePort = unusedPort.getLocalPort();
            // Release an unused port so connection attempts fail immediately.
            unusedPort.close();
            assertServer(priorityBackend(Collections.singletonList(primaryPort), Collections.singletonList(secondaryPort)),
                    "primary", true);
            final int hash = DN.valueOf("uid=user").hashCode();
            final int firstIndex = hash == Integer.MIN_VALUE ? 0 : Math.abs(hash) % 2;
            assertServer(priorityBackend(Arrays.asList(primaryPort, primary2Port), Collections.singletonList(secondaryPort)),
                    firstIndex == 0 ? "primary" : "primary2", true);
            final List<Integer> partiallyAvailable = firstIndex == 0
                    ? Arrays.asList(unavailablePort, primaryPort) : Arrays.asList(primaryPort, unavailablePort);
            assertServer(priorityBackend(partiallyAvailable, Collections.singletonList(secondaryPort)),
                    "primary", true);
            assertServer(priorityBackend(Collections.singletonList(unavailablePort), Collections.singletonList(secondaryPort)),
                    "secondary", false);
            assertServer(priorityBackend(Collections.singletonList(unavailablePort), Arrays.asList(secondaryPort, secondary2Port)),
                    firstIndex == 0 ? "secondary" : "secondary2", false);
        }
    }

    @Test
    public void forwardsSearchParametersToRemoteServer() throws Exception {
        final ProxyBackend backend = new ProxyBackend();
        final ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);
        final Connection connection = Mockito.mock(Connection.class);
        final ConnectionEntryReader reader = Mockito.mock(ConnectionEntryReader.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.search(Mockito.any(SearchRequest.class))).thenReturn(reader);
        when(reader.readResult()).thenReturn(Responses.newResult(ResultCode.SUCCESS));
        final Field factoryField = ProxyBackend.class.getDeclaredField("factory");
        factoryField.setAccessible(true);
        factoryField.set(backend, connectionFactory);

        final InternalClientConnection client = new InternalClientConnection(new AuthenticationInfo());
        final SearchOperationBasis operation = new SearchOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.emptyList(), DN.valueOf("o=test"), SearchScope.WHOLE_SUBTREE,
                DereferenceAliasesPolicy.ALWAYS, 7, 12, true,
                SearchFilter.createFilterFromString("(uid=*)"), new HashSet<>(Collections.singletonList("uid")));
        backend.execute(operation);

        final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        Mockito.verify(connection).search(captor.capture());
        final SearchRequest forwarded = captor.getValue();
        assertEquals(forwarded.getSizeLimit(), 7);
        assertEquals(forwarded.getTimeLimit(), 12);
        assertEquals(forwarded.getDereferenceAliasesPolicy(), DereferenceAliasesPolicy.ALWAYS);
        assertTrue(forwarded.isTypesOnly());
        assertEquals(operation.getResultCode(), ResultCode.SUCCESS);
    }

    @Test
    public void doesNotReuseAuthenticatedBindConnectionForAnonymousRequestsAfterModeChange() throws Exception {
        final org.forgerock.opendj.ldap.MemoryBackend memory = new org.forgerock.opendj.ldap.MemoryBackend(
                new LDIFEntryReader("dn:", "objectClass: top", "objectClass: extensibleObject", "",
                        "dn: uid=user", "objectClass: top", "objectClass: inetOrgPerson",
                        "uid: user", "cn: User", "sn: User", "userPassword: password", ""));
        final java.util.concurrent.atomic.AtomicReference<InetSocketAddress> bindPeer =
                new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<InetSocketAddress> searchPeer =
                new java.util.concurrent.atomic.AtomicReference<>();
        final RequestHandlerFactory<LDAPClientContext, RequestContext> handlers = client -> {
            @SuppressWarnings("unchecked")
            final RequestHandler<RequestContext> handler = (RequestHandler<RequestContext>)
                    java.lang.reflect.Proxy.newProxyInstance(RequestHandler.class.getClassLoader(),
                            new Class<?>[] {RequestHandler.class}, (proxy, method, args) -> {
                                if (method.getName().equals("handleBind")) {
                                    bindPeer.set(client.getPeerAddress());
                                } else if (method.getName().equals("handleSearch")
                                        && ((SearchRequest) args[1]).getName().equals(DN.valueOf("uid=user"))) {
                                    searchPeer.set(client.getPeerAddress());
                                }
                                try {
                                    return method.invoke(memory, args);
                                } catch (java.lang.reflect.InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
            return handler;
        };
        final Options options = Options.defaultOptions();
        try (LDAPListener listener = new LDAPListener(Collections.singleton(new InetSocketAddress("127.0.0.1", 0)),
                new ServerConnectionFactoryAdapter(options.get(CommonLDAPOptions.LDAP_DECODE_OPTIONS),
                        Connections.newServerConnectionFactory(handlers)), options)) {
            final ProxyBackend backend = priorityBackend(Collections.singletonList(listener.firstSocketAddress().getPort()),
                    Collections.emptyList());
            backend.serverContext = TestCaseUtils.getServerContext();
            try {
                final Field routeField = ProxyBackend.class.getDeclaredField("staticRoute");
                routeField.setAccessible(true);
                final Object route = routeField.get(backend);
                final Field mode = route.getClass().getDeclaredField("directUserMode");
                final Field probe = route.getClass().getDeclaredField("nextCapabilityProbe");
                mode.setAccessible(true);
                probe.setAccessible(true);
                probe.setLong(route, System.nanoTime() + TimeUnit.MINUTES.toNanos(1));
                mode.set(route, false);
                final BindOperationBasis bind = new BindOperationBasis(
                        new InternalClientConnection(new AuthenticationInfo()), nextOperationID(), nextMessageID(),
                        Collections.emptyList(), "3", ByteString.valueOfUtf8("uid=user"),
                        ByteString.valueOfUtf8("password"));
                backend.execute(bind);
                assertEquals(bind.getResultCode(), ResultCode.SUCCESS);
                mode.set(route, true);
                final org.opends.server.protocols.internal.InternalSearchOperation search =
                        new org.opends.server.protocols.internal.InternalSearchOperation(
                                new InternalClientConnection(new AuthenticationInfo()), nextOperationID(), nextMessageID(),
                                org.opends.server.protocols.internal.Requests.newSearchRequest(
                                        "uid=user", SearchScope.BASE_OBJECT, "(objectClass=*)"));
                backend.execute(search);
                assertEquals(search.getResultCode(), ResultCode.SUCCESS);
                Assert.assertNotNull(bindPeer.get());
                Assert.assertNotNull(searchPeer.get());
                Assert.assertNotEquals(searchPeer.get(), bindPeer.get(),
                        "Anonymous search must not use a connection authenticated by a client bind");
            } finally {
                backend.bindFactory.close();
                backend.factory.close();
            }
        }
    }

    @Test
    public void updatesAndRemovesProxyNamingContexts() throws Exception {
        final String name = "proxy-lifecycle-test";
        final DN oldBase = DN.valueOf("ou=proxy-old,o=test");
        final DN newBase = DN.valueOf("ou=proxy-new,o=test");
        final BackendConfigManager manager = TestCaseUtils.getServerContext().getBackendConfigManager();
        TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", name,
                "--type", "static", "--set", "primary-server:localhost:1389");
        boolean created = false;
        try {
            TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", name,
                    "--set", "base-dn:" + oldBase, "--set", "enabled:true", "--set", "shard:" + name);
            created = true;
            Assert.assertTrue(manager.findBackendForEntry(oldBase) instanceof ProxyBackend);
            final ProxyBackend backend = (ProxyBackend) manager.findBackendForEntry(oldBase);
            try {
                manager.replaceBackendBaseDNs(backend, Collections.singleton(oldBase),
                        Collections.singleton(DN.valueOf("o=test")),
                        () -> Assert.fail("A conflicting base DN must not be applied"));
                Assert.fail("The existing local naming context must not be replaced");
            } catch (DirectoryException expected) {
                Assert.assertSame(manager.findBackendForEntry(oldBase), backend);
                Assert.assertFalse(manager.findBackendForEntry(DN.valueOf("o=test")) instanceof ProxyBackend);
            }
            TestCaseUtils.dsconfig("set-backend-prop", "--backend-name", name,
                    "--set", "base-dn:" + newBase);
            Assert.assertFalse(manager.findBackendForEntry(oldBase) instanceof ProxyBackend);
            Assert.assertTrue(manager.findBackendForEntry(newBase) instanceof ProxyBackend);
            TestCaseUtils.dsconfig("set-backend-prop", "--backend-name", name, "--set", "enabled:false");
            Assert.assertFalse(manager.findBackendForEntry(newBase) instanceof ProxyBackend);
            TestCaseUtils.dsconfig("set-backend-prop", "--backend-name", name, "--set", "enabled:true");
            Assert.assertTrue(manager.findBackendForEntry(newBase) instanceof ProxyBackend);
            TestCaseUtils.dsconfig("delete-backend", "--backend-name", name);
            created = false;
            Assert.assertFalse(manager.findBackendForEntry(newBase) instanceof ProxyBackend);
        } finally {
            if (created) {
                TestCaseUtils.dsconfig("delete-backend", "--backend-name", name);
            }
            TestCaseUtils.dsconfig("delete-service-discovery-mechanism", "--mechanism-name", name);
        }
    }

    @Test
    public void bindPreservesBinaryPasswordAndSuppressesRemoteDiagnostic() throws Exception {
        final ProxyBackend backend = new ProxyBackend();
        final ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);
        final Connection connection = Mockito.mock(Connection.class);
        final byte[] observed = new byte[1];
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.bind(Mockito.any(org.forgerock.opendj.ldap.requests.BindRequest.class)))
                .thenAnswer(invocation -> {
                    final org.forgerock.opendj.ldap.requests.SimpleBindRequest request = invocation.getArgumentAt(0,
                            org.forgerock.opendj.ldap.requests.SimpleBindRequest.class);
                    byte[] password = request.getPassword();
                    assertEquals(password.length, 1);
                    observed[0] = password[0];
                    return Responses.newBindResult(ResultCode.INVALID_CREDENTIALS)
                            .setDiagnosticMessage("remote authentication detail");
                });
        backend.bindFactory = connectionFactory;
        final BindOperationBasis operation = new BindOperationBasis(
                new InternalClientConnection(new AuthenticationInfo()), nextOperationID(), nextMessageID(),
                Collections.emptyList(), "3", ByteString.valueOfUtf8("uid=test,o=test"),
                ByteString.wrap(new byte[] {(byte) 0xff}));
        backend.execute(operation);
        assertEquals(observed[0], (byte) 0xff);
        assertEquals(operation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
        Assert.assertFalse(operation.getErrorMessage().toString().contains("remote authentication detail"));
        assertEquals(operation.getAuthFailureReason().toString(), "remote authentication detail");
        TestCaseUtils.dsconfig("set-global-configuration-prop", "--set", "return-bind-error-messages:true");
        try {
            final BindOperationBasis detailed = new BindOperationBasis(
                    new InternalClientConnection(new AuthenticationInfo()), nextOperationID(), nextMessageID(),
                    Collections.emptyList(), "3", ByteString.valueOfUtf8("uid=test,o=test"),
                    ByteString.wrap(new byte[] {(byte) 0xff}));
            backend.execute(detailed);
            assertEquals(detailed.getErrorMessage().toString(), "remote authentication detail");
        } finally {
            TestCaseUtils.dsconfig("set-global-configuration-prop", "--set", "return-bind-error-messages:false");
        }
    }

    @Test
    public void rejectsUnauthenticatedBindWithoutAssigningTheRequestedDn() throws Exception {
        final ProxyBackend backend = new ProxyBackend();
        final ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);
        final Connection connection = Mockito.mock(Connection.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.bind(Mockito.any(org.forgerock.opendj.ldap.requests.BindRequest.class)))
                .thenReturn(Responses.newBindResult(ResultCode.SUCCESS));
        final Field field = ProxyBackend.class.getDeclaredField("bindFactory");
        field.setAccessible(true);
        field.set(backend, connectionFactory);
        final InternalClientConnection client = new InternalClientConnection(new AuthenticationInfo());
        final BindOperationBasis operation = new BindOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.emptyList(), "3", ByteString.valueOfUtf8("uid=test,o=test"), ByteString.empty());

        backend.execute(operation);

        assertEquals(operation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
        assertThat(client.getAuthenticationInfo().isAuthenticated()).isFalse();
        Assert.assertNull(client.getAuthenticationInfo().getAuthorizationDN());
        Mockito.verify(connectionFactory, Mockito.never()).getConnection();
    }

    @Test
    public void successfulAnonymousBindKeepsAnonymousIdentity() throws Exception {
        final ProxyBackend backend = new ProxyBackend();
        final ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);
        final Connection connection = Mockito.mock(Connection.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.bind(Mockito.any(org.forgerock.opendj.ldap.requests.BindRequest.class)))
                .thenReturn(Responses.newBindResult(ResultCode.SUCCESS));
        backend.bindFactory = connectionFactory;
        final InternalClientConnection client = new InternalClientConnection(new AuthenticationInfo());
        final BindOperationBasis operation = new BindOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.emptyList(), "3", ByteString.empty(), ByteString.empty());
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.SUCCESS);
        assertThat(client.getAuthenticationInfo().isAuthenticated()).isFalse();
        Assert.assertNull(client.getAuthenticationInfo().getAuthorizationDN());
    }

    @Test
    public void preservesRemoteReferralAndDiagnosticFields() throws Exception {
        final ProxyBackend backend = new ProxyBackend();
        final ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);
        final Connection connection = Mockito.mock(Connection.class);
        final org.forgerock.opendj.ldap.responses.CompareResult result = Responses.newCompareResult(ResultCode.REFERRAL);
        result.setMatchedDN("o=test");
        result.setDiagnosticMessage("Remote directory referral");
        result.addReferralURI("ldap://other.example/o=test");
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.compare(Mockito.any(org.forgerock.opendj.ldap.requests.CompareRequest.class))).thenReturn(result);
        final Field field = ProxyBackend.class.getDeclaredField("factory");
        field.setAccessible(true);
        field.set(backend, connectionFactory);
        final InternalClientConnection client = new InternalClientConnection(new AuthenticationInfo());
        final CompareOperationBasis operation = new CompareOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.emptyList(), DN.valueOf("uid=test,o=test"), org.forgerock.opendj.ldap.AttributeDescription.valueOf("uid"),
                ByteString.valueOfUtf8("test"));

        backend.execute(operation);

        assertEquals(operation.getResultCode(), ResultCode.REFERRAL);
        assertEquals(operation.getMatchedDN(), DN.valueOf("o=test"));
        assertEquals(operation.getErrorMessage().toString(), "Remote directory referral");
        assertEquals(operation.getReferralURLs(), Collections.singletonList("ldap://other.example/o=test"));
    }

    @Test
    public void appliesStaticDiscoveryChangesAndClosesOldFactories() throws Exception {
        try (LDAPListener first = testServer("first"); LDAPListener second = testServer("second")) {
            final String name = "static-route-update-test";
            TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", name, "--type", "static",
                    "--set", "bind-dn:cn=service", "--set", "bind-password:service-password",
                    "--set", "primary-server:127.0.0.1:" + first.firstSocketAddress().getPort());
            final ProxyBackendCfg configuration = mockCfg(ProxyBackendCfg.class);
            when(configuration.getShard()).thenReturn(name);
            final ProxyBackend backend = new ProxyBackend();
            backend.configureBackend(configuration, TestCaseUtils.getServerContext());
            backend.openBackend();
            try {
                final ConnectionFactory oldFactory = backendFactory(backend, "factory");
                assertEquals(readBackendLabel(oldFactory), "first");
                TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", name,
                        "--set", "primary-server:127.0.0.1:" + second.firstSocketAddress().getPort());
                assertEquals(readBackendLabel(backendFactory(backend, "factory")), "second");
                try {
                    readBackendLabel(oldFactory);
                    Assert.fail("The replaced connection factory must be closed");
                } catch (LdapException | IllegalStateException expected) {
                    // Its connection pools have been retired.
                }
                TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", name,
                        "--set", "bind-password:incorrect-password");
                try {
                    readBackendLabel(backendFactory(backend, "factory"));
                    Assert.fail("A changed invalid service password must take effect");
                } catch (LdapException expected) {
                    // The new service credentials are used for the connection.
                }
                TestCaseUtils.dsconfig("set-service-discovery-mechanism-prop", "--mechanism-name", name,
                        "--set", "bind-password:service-password");
                assertEquals(readBackendLabel(backendFactory(backend, "factory")), "second");
            } finally {
                backend.finalizeBackend();
            }
        }
    }

    private String readBackendLabel(ConnectionFactory factory) throws Exception {
        try (Connection connection = factory.getConnection()) {
            return connection.searchSingleEntry("uid=user", SearchScope.BASE_OBJECT,
                    "(objectClass=*)", "description").getAttribute("description").firstValueAsString();
        }
    }

    @Test
    public void waitsForInFlightOperationsBeforeClosingRetiredFactories() throws Exception {
        final ProxyBackend backend = new ProxyBackend();
        final ProxyBackendCfg configuration = mockCfg(ProxyBackendCfg.class);
        when(configuration.getBaseDN()).thenReturn(new TreeSet<DN>());
        backend.cfg = configuration;
        final ConnectionFactory authenticated = Mockito.mock(ConnectionFactory.class);
        final ConnectionFactory bind = Mockito.mock(ConnectionFactory.class);
        final Connection connection = Mockito.mock(Connection.class);
        final ConnectionEntryReader reader = Mockito.mock(ConnectionEntryReader.class);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        when(authenticated.getConnection()).thenReturn(connection);
        when(connection.search(Mockito.any(SearchRequest.class))).thenReturn(reader);
        when(reader.hasNext()).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("The test did not release the in-flight search");
            }
            return false;
        });
        when(reader.readResult()).thenReturn(Responses.newResult(ResultCode.SUCCESS));
        backend.factory = authenticated;
        backend.bindFactory = bind;
        final Class<?> routeClass = Class.forName(ProxyBackend.class.getName() + "$DynamicRoute");
        final java.lang.reflect.Constructor<?> constructor =
                routeClass.getDeclaredConstructor(ConnectionFactory.class, ConnectionFactory.class);
        constructor.setAccessible(true);
        final Field staticRoute = ProxyBackend.class.getDeclaredField("staticRoute");
        staticRoute.setAccessible(true);
        staticRoute.set(backend, constructor.newInstance(bind, authenticated));
        final InternalClientConnection client = new InternalClientConnection(new AuthenticationInfo());
        final SearchOperationBasis operation = new SearchOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.emptyList(), DN.valueOf("o=test"), SearchScope.WHOLE_SUBTREE,
                DereferenceAliasesPolicy.NEVER, 0, 0, false, SearchFilter.createFilterFromString("(uid=*)"),
                new HashSet<>(Collections.singletonList("uid")));
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<?> search = executor.submit(() -> {
                backend.execute(operation);
                return null;
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            backend.finalizeBackend();
            Mockito.verify(authenticated, Mockito.never()).close();
            Mockito.verify(bind, Mockito.never()).close();
            release.countDown();
            search.get(10, TimeUnit.SECONDS);
            assertEquals(operation.getResultCode(), ResultCode.SUCCESS);
            Mockito.verify(authenticated).close();
            Mockito.verify(bind).close();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void test_backend() throws Exception {
        final String shardName="Static Service Discovery Mechanism test_backend";
        TestCaseUtils.dsconfig(
                "create-service-discovery-mechanism",
                "--mechanism-name", shardName,
                "--type", "static",
                "--set", "bind-dn:cn=Directory Manager",
                "--set", "bind-password:password",
                "--set", "primary-server:unknown:636",
                "--set", "primary-server:localhost:"+TestCaseUtils.getServerLdapPort(),
                "--set", "secondary-server:unknown:389",
                "--set", "secondary-server:localhost:"+TestCaseUtils.getServerLdapPort(),
                "--set", "use-ssl:false"
        );
        Assert.assertNotNull(TestCaseUtils.getServerContext().getServiceDiscoveryMechanismConfigManager().getMechanism(shardName));
        Assert.assertNotNull(TestCaseUtils.getServerContext().getServiceDiscoveryMechanismConfigManager().getMechanism(shardName).getPartition());
        Assert.assertEquals(TestCaseUtils.getServerContext().getServiceDiscoveryMechanismConfigManager().getMechanism(shardName).getPartition().getPrimaryServers().size(), 2);
        Assert.assertEquals(TestCaseUtils.getServerContext().getServiceDiscoveryMechanismConfigManager().getMechanism(shardName).getPartition().getSecondaryServers().size(), 2);

        final ProxyBackendCfg backendCfg = mockCfg(ProxyBackendCfg.class);
        when(backendCfg.getShard()).thenReturn(shardName);

        final ProxyBackend backend=new ProxyBackend();
        backend.configureBackend(backendCfg,TestCaseUtils.getServerContext());
        backend.openBackend();

        //bind
        Operation operation;
        InternalClientConnection client = new InternalClientConnection(new AuthenticationInfo());

        operation = new BindOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.singletonList(new LDAPControl("1.3.6.1.4.1.99999.999", true)),
                "3", ByteString.valueOfUtf8("cn=Directory Manager"), ByteString.valueOfUtf8("password"));
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
        assertThat(client.getAuthenticationInfo().isAuthenticated()).isFalse();

        operation=new BindOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                "3",
                ByteString.valueOfUtf8("cn=Directory Manager"),
                ByteString.valueOfUtf8("bad")
        );
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.INVALID_CREDENTIALS);

        operation=new BindOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                "3",
                ByteString.valueOfUtf8("uid=test,o=test"),
                ByteString.valueOfUtf8("password")
        );
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.INVALID_CREDENTIALS);

        boolean authenticatedAfterFailedBind = client.getAuthenticationInfo().isAuthenticated();

        operation=new BindOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                "3",
                ByteString.valueOfUtf8("cn=Directory Manager"),
                ByteString.valueOfUtf8("password")
        );
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);

        //add
        operation=new AddOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                TestCaseUtils.makeEntry(
                        "dn: uid=test,o=test",
                        "objectClass: top",
                        "objectClass: person",
                        "objectClass: organizationalPerson",
                        "objectClass: inetOrgPerson",
                        "uid: test",
                        "givenName: test",
                        "sn: Test",
                        "cn: Test",
                        "userPassword: password"
                )
        );
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);

        operation=new AddOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                TestCaseUtils.makeEntry(
                        "dn: uid=test,o=test",
                        "objectClass: top",
                        "objectClass: person",
                        "objectClass: organizationalPerson",
                        "objectClass: inetOrgPerson",
                        "uid: test",
                        "givenName: test",
                        "sn: Test",
                        "cn: Test",
                        "userPassword: password"
                )
        );
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.ENTRY_ALREADY_EXISTS);

        // Modify DN must forward both the new parent and the old-RDN policy.
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                "dn: ou=destination,o=test",
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: destination"
        )).getResultCode(), ResultCode.SUCCESS);
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                "dn: uid=move-source,o=test",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: move-source",
                "sn: Move",
                "cn: Move"
        )).getResultCode(), ResultCode.SUCCESS);
        operation = new ModifyDNOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                DN.valueOf("uid=move-source,o=test"), RDN.valueOf("uid=move-target"), true,
                DN.valueOf("ou=destination,o=test"));
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.SUCCESS);
        try (Connection admin = getConnection()) {
            final org.forgerock.opendj.ldap.Entry moved = admin.searchSingleEntry(
                    "uid=move-target,ou=destination,o=test", SearchScope.BASE_OBJECT, "(objectClass=*)", "uid");
            assertTrue(moved.getAttribute("uid").contains(ByteString.valueOfUtf8("move-target")));
            assertThat(moved.getAttribute("uid").contains(ByteString.valueOfUtf8("move-source"))).isFalse();
        }

        operation = new ModifyDNOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                DN.valueOf("uid=move-target,ou=destination,o=test"), RDN.valueOf("uid=move-retained"), false, null);
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.SUCCESS);
        try (Connection admin = getConnection()) {
            final org.forgerock.opendj.ldap.Entry renamed = admin.searchSingleEntry(
                    "uid=move-retained,ou=destination,o=test", SearchScope.BASE_OBJECT, "(objectClass=*)", "uid");
            assertTrue(renamed.getAttribute("uid").contains(ByteString.valueOfUtf8("move-target")));
            assertTrue(renamed.getAttribute("uid").contains(ByteString.valueOfUtf8("move-retained")));
        }

        //bind as user
        operation=new BindOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                "3",
                ByteString.valueOfUtf8("uid=test,o=test"),
                ByteString.valueOfUtf8("password")
        );
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);

        // A false assertion must prevent the modify from reaching the backend.
        final AssertionRequestControl assertion = AssertionRequestControl.newControl(true,
                Filter.valueOf("(uid=not-the-user)"));
        operation = new ModifyOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.singletonList(new LDAPControl(assertion.getOID(), assertion.isCritical(), assertion.getValue())),
                ByteString.valueOfUtf8("uid=test,o=test"),
                Collections.singletonList(RawModification.create(ModificationType.REPLACE, "description", "must-not-appear")));
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.ASSERTION_FAILED);

        // Typed server controls must preserve their encoded values as well.
        operation = new ModifyOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.singletonList(new LDAPAssertionRequestControl(true, LDAPFilter.decode("(uid=not-the-user)"))),
                ByteString.valueOfUtf8("uid=test,o=test"),
                Collections.singletonList(RawModification.create(ModificationType.REPLACE, "description", "must-not-appear")));
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.ASSERTION_FAILED);

        // Unsupported critical controls must fail instead of silently changing data.
        operation = new ModifyOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.singletonList(new LDAPControl("1.3.6.1.4.1.99999.999", true)),
                ByteString.valueOfUtf8("uid=test,o=test"),
                Collections.singletonList(RawModification.create(ModificationType.REPLACE, "description", "must-not-appear")));
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

        // A client-supplied proxy authorization control must not impersonate the service account.
        operation = new ModifyOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.singletonList(new LDAPControl("2.16.840.1.113730.3.4.18", true,
                        ByteString.valueOfUtf8("dn:cn=Directory Manager"))),
                ByteString.valueOfUtf8("uid=test,o=test"),
                Collections.singletonList(RawModification.create(ModificationType.REPLACE, "description", "must-not-appear")));
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
        try (Connection admin = getConnection()) {
            assertThat(admin.searchSingleEntry("uid=test,o=test", SearchScope.BASE_OBJECT,
                    "(objectClass=*)", "description").getAttribute("description")).isNull();
        }

        // The response control from an accepted modify must also reach the client.
        final PostReadRequestControl postRead = PostReadRequestControl.newControl(true, "description");
        operation = new ModifyOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.singletonList(new LDAPControl(postRead.getOID(), postRead.isCritical(), postRead.getValue())),
                ByteString.valueOfUtf8("uid=test,o=test"),
                Collections.singletonList(RawModification.create(ModificationType.REPLACE, "description", "visible-result")));
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.SUCCESS);
        assertThat(operation.getResponseControls()).hasSize(1);
        final LDAPControl postReadResponse = (LDAPControl) operation.getResponseControls().get(0);
        assertEquals(postReadResponse.getOID(), postRead.getOID());
        final PostReadResponseControl decodedPostRead = PostReadResponseControl.DECODER.decodeControl(
                GenericControl.newControl(postReadResponse.getOID(), postReadResponse.isCritical(), postReadResponse.getValue()),
                new DecodeOptions());
        assertEquals(decodedPostRead.getEntry().getAttribute("description").firstValueAsString(), "visible-result");

        // A proxied search must not disclose another user's password, even though
        // the configured service account can read it.
        assertEquals(getRootConnection().processAdd(TestCaseUtils.makeEntry(
                "dn: uid=other,o=test",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: other",
                "sn: Other",
                "cn: Other",
                "userPassword: other-password"
        )).getResultCode(), ResultCode.SUCCESS);
        try (Connection admin = getConnection()) {
            assertThat(admin.searchSingleEntry("uid=other,o=test", SearchScope.BASE_OBJECT,
                    "(objectClass=*)", "userPassword").getAttribute("userPassword")).isNotNull();
        }
        final List<SearchResultEntry> otherEntries = new ArrayList<>();
        operation = new SearchOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                DN.valueOf("uid=other,o=test"), SearchScope.BASE_OBJECT, DereferenceAliasesPolicy.NEVER,
                1, 1, false, SearchFilter.createFilterFromString("(objectClass=*)"),
                new HashSet<>(Arrays.asList("uid", "userPassword"))) {
            @Override
            public void sendSearchEntry(SearchResultEntry searchEntry) throws DirectoryException {
                otherEntries.add(searchEntry);
            }
        };
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.SUCCESS);
        assertThat(otherEntries).hasSize(1);
        assertThat(otherEntries.get(0).getAllAttributes("userPassword").iterator().hasNext()).isFalse();

        // Search requests must honor the caller's limits and types-only flag.
        final SoftAssert searchChecks = new SoftAssert();
        final List<SearchResultEntry> typesOnlyEntries = new ArrayList<>();
        final SearchOperationBasis typesOnlySearch = new SearchOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.emptyList(), DN.valueOf("uid=test,o=test"), SearchScope.BASE_OBJECT,
                DereferenceAliasesPolicy.NEVER, 0, 0, true, SearchFilter.createFilterFromString("(uid=test)"),
                new HashSet<>(Arrays.asList("uid", "objectClass"))) {
            @Override
            public void sendSearchEntry(SearchResultEntry entry) throws DirectoryException {
                typesOnlyEntries.add(entry);
            }
        };
        backend.execute(typesOnlySearch);
        searchChecks.assertEquals(typesOnlySearch.getResultCode(), ResultCode.SUCCESS, "types-only result");
        searchChecks.assertEquals(typesOnlyEntries.size(), 1, "types-only entry count");
        if (typesOnlyEntries.size() == 1) {
            final Iterator<org.opends.server.types.Attribute> uidAttributes =
                    typesOnlyEntries.get(0).getAllAttributes("uid").iterator();
            searchChecks.assertTrue(uidAttributes.hasNext(), "types-only result includes the uid type");
            if (uidAttributes.hasNext()) {
                searchChecks.assertTrue(uidAttributes.next().isEmpty(), "types-only result excludes the uid value");
            }
        }
        if (typesOnlyEntries.size() == 1) {
            final Iterator<org.opends.server.types.Attribute> objectClasses =
                    typesOnlyEntries.get(0).getAllAttributes("objectClass").iterator();
            searchChecks.assertTrue(objectClasses.hasNext(), "types-only result includes the objectClass type");
            if (objectClasses.hasNext()) {
                searchChecks.assertTrue(objectClasses.next().isEmpty(), "types-only result excludes objectClass values");
            }
        }
        final List<SearchResultEntry> limitedEntries = new ArrayList<>();
        final SearchOperationBasis sizeLimitedSearch = new SearchOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.emptyList(), DN.valueOf("o=test"), SearchScope.WHOLE_SUBTREE,
                DereferenceAliasesPolicy.NEVER, 1, 0, false, SearchFilter.createFilterFromString("(uid=*)"),
                new HashSet<>(Collections.singletonList("uid"))) {
            @Override
            public void sendSearchEntry(SearchResultEntry entry) throws DirectoryException {
                limitedEntries.add(entry);
            }
        };
        backend.execute(sizeLimitedSearch);
        searchChecks.assertEquals(limitedEntries.size(), 1, "size-limited entry count");
        searchChecks.assertEquals(sizeLimitedSearch.getResultCode(), ResultCode.SIZE_LIMIT_EXCEEDED, "size-limited result");

        searchChecks.assertAll();

        // Anonymous searches must retain anonymous access rights at the remote server.
        final InternalClientConnection anonymousClient = new InternalClientConnection(new AuthenticationInfo());
        final List<SearchResultEntry> anonymousEntries = new ArrayList<>();
        operation = new SearchOperationBasis(anonymousClient, nextOperationID(), nextMessageID(), Collections.emptyList(),
                DN.valueOf("uid=other,o=test"), SearchScope.BASE_OBJECT, DereferenceAliasesPolicy.NEVER,
                1, 1, false, SearchFilter.createFilterFromString("(objectClass=*)"),
                new HashSet<>(Arrays.asList("uid", "userPassword"))) {
            @Override
            public void sendSearchEntry(SearchResultEntry searchEntry) throws DirectoryException {
                anonymousEntries.add(searchEntry);
            }
        };
        backend.execute(operation);
        assertEquals(operation.getResultCode(), ResultCode.SUCCESS);
        assertThat(anonymousEntries).hasSize(1);
        assertThat(anonymousEntries.get(0).getAllAttributes("uid").iterator().hasNext()).isTrue();
        assertThat(anonymousEntries.get(0).getAllAttributes("userPassword").iterator().hasNext()).isFalse();
        assertThat(authenticatedAfterFailedBind).isFalse();

        // This backend does not support paging: a critical control must be rejected.
        final SimplePagedResultsControl pageRequest = SimplePagedResultsControl.newControl(true, 1, ByteString.empty());
        final List<SearchResultEntry> pagedEntries = new ArrayList<>();
        final SearchOperationBasis pagedSearch = new SearchOperationBasis(client, nextOperationID(), nextMessageID(),
                Collections.singletonList(new LDAPControl(pageRequest.getOID(), pageRequest.isCritical(), pageRequest.getValue())),
                DN.valueOf("o=test"), SearchScope.WHOLE_SUBTREE, DereferenceAliasesPolicy.NEVER,
                0, 0, false, SearchFilter.createFilterFromString("(uid=*)"),
                new HashSet<>(Collections.singletonList("uid"))) {
            @Override
            public void sendSearchEntry(SearchResultEntry searchEntry) throws DirectoryException {
                pagedEntries.add(searchEntry);
            }
        };
        backend.execute(pagedSearch);
        assertEquals(pagedSearch.getResultCode(), ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
        assertThat(pagedEntries).isEmpty();

        //search
        final List<SearchResultEntry> sendSearchEntry=new ArrayList<>();
        operation=new SearchOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                DN.valueOf("uid=test,o=test"),
                SearchScope.BASE_OBJECT,
                DereferenceAliasesPolicy.NEVER,
                1,
                1,
                false,
                SearchFilter.createFilterFromString("(givenName=test)"),
                new HashSet<String>(Arrays.asList(new String[]{"uid"}))
                ){
            @Override
            public void sendSearchEntry(SearchResultEntry searchEntry) throws DirectoryException {
                sendSearchEntry.add(searchEntry);
            }
        };
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);
        assertThat(sendSearchEntry.size()).isEqualTo(1);
        assertEquals(sendSearchEntry.stream().findFirst().get().getName(),DN.valueOf("uid=test,o=test"));
        assertTrue(sendSearchEntry.stream().findFirst().get().getAllAttributes("uid").iterator().next().contains(ByteString.valueOfUtf8("test")));

        //edit
        operation=new ModifyOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                ByteString.valueOfUtf8("uid=test,o=test"),
                Arrays.asList(new RawModification[]{
                        RawModification.create(ModificationType.REPLACE,"givenName","test2"),
                        RawModification.create(ModificationType.REPLACE,"displayName","Test2"),
                        RawModification.create(ModificationType.REPLACE,"userPassword","password2")
                })

        );
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);

        sendSearchEntry.clear();
        operation=new SearchOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                DN.valueOf("uid=test,o=test"),
                SearchScope.BASE_OBJECT,
                DereferenceAliasesPolicy.NEVER,
                1,
                1,
                false,
                SearchFilter.createFilterFromString("(givenName=test2)"),
                new HashSet<String>(Arrays.asList(new String[]{"displayName"}))
        ){
            @Override
            public void sendSearchEntry(SearchResultEntry searchEntry) throws DirectoryException {
                sendSearchEntry.add(searchEntry);
            }
        };
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);
        assertThat(sendSearchEntry.size()).isEqualTo(1);
        assertEquals(sendSearchEntry.stream().findFirst().get().getName(),DN.valueOf("uid=test,o=test"));
        assertTrue(sendSearchEntry.stream().findFirst().get().getAllAttributes("displayName").iterator().next().contains(ByteString.valueOfUtf8("Test2")));

        //extended
        operation=new ExtendedOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                Requests.newWhoAmIExtendedRequest().getOID(), Requests.newWhoAmIExtendedRequest().getValue());
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);
        assertEquals(((ExtendedOperationBasis)operation).getResponseValue().toString(),"dn:uid=test,o=test");
    }

    @Test
    public void anonymousAuthenticationIsNotAuthenticated() {
        assertThat(AuthenticationInfo.anonymous().isAuthenticated()).isFalse();
        Assert.assertNull(AuthenticationInfo.anonymous().getAuthorizationDN());
    }

    @Test
    public void test_dsconfig() throws Exception {
        Assert.assertNotNull(TestCaseUtils.getServerContext().getBackendConfigManager().findBackendForEntry(DN.valueOf("cn=test,o=test")));

        String shardName="Static Service Discovery Mechanism";
        TestCaseUtils.dsconfig(
                "create-service-discovery-mechanism",
                "--mechanism-name", shardName,
                "--type", "static",
                "--set", "bind-dn:cn=Directory Manager",
                "--set", "bind-password:password",
                "--set", "primary-server:local1.example.com:636",
                "--set", "primary-server:local2.example.com:636",
                "--set", "secondary-server:remote1.example.com:636",
                "--set", "secondary-server:remote2.example.com:636",
                "--set", "use-ssl:true"
        );
        Assert.assertNotNull(TestCaseUtils.getServerContext().getServiceDiscoveryMechanismConfigManager().getMechanism(shardName));
        Assert.assertNotNull(TestCaseUtils.getServerContext().getServiceDiscoveryMechanismConfigManager().getMechanism(shardName).getPartition());
        Assert.assertEquals(TestCaseUtils.getServerContext().getServiceDiscoveryMechanismConfigManager().getMechanism(shardName).getPartition().getPrimaryServers().size(), 2);
        Assert.assertEquals(TestCaseUtils.getServerContext().getServiceDiscoveryMechanismConfigManager().getMechanism(shardName).getPartition().getSecondaryServers().size(), 2);

        shardName="Replication Service Discovery Mechanism";
        TestCaseUtils.dsconfig(
                "create-service-discovery-mechanism",
                "--mechanism-name", "Replication Service Discovery Mechanism",
                "--type", "replication",
                "--set", "bind-dn:cn=Directory Manager",
                "--set", "bind-password:password",
                "--set", "bootstrap-replication-server:local1.example.com:636",
                "--set", "use-ssl:true"
        );
        Assert.assertNotNull(TestCaseUtils.getServerContext().getServiceDiscoveryMechanismConfigManager().getMechanism(shardName));

        TestCaseUtils.dsconfig(
                "create-backend",
                "--type", "proxy",
                "--backend-name", "proxy-static",
                "--set", "base-dn: o=test1",
                "--set", "enabled:true",
                "--set", "shard:Static Service Discovery Mechanism"
        );
        Assert.assertNotNull(TestCaseUtils.getServerContext().getBackendConfigManager().findBackendForEntry(DN.valueOf("cn=test,o=test1")));

        TestCaseUtils.dsconfig(
                "create-backend",
                "--type", "proxy",
                "--backend-name", "proxy-replication",
                "--set", "base-dn: o=test2",
                "--set", "enabled:true",
                "--set", "shard:Replication Service Discovery Mechanism"
        );
        Assert.assertNotNull(TestCaseUtils.getServerContext().getBackendConfigManager().findBackendForEntry(DN.valueOf("cn=test,o=test2")));
    }
}
