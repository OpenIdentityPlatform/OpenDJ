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
import org.opends.server.backends.ProxyBackend;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.internal.InternalClientConnection;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test(sequential = true)
public class StaticServiceDiscoveryMechanismTest extends DirectoryServerTestCase {
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

    private LDAPListener testServer(String label) throws Exception {
        final MemoryBackend memory = new MemoryBackend(new LDIFEntryReader(
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
        final List<Partition.Server> primaries = new ArrayList<>();
        final List<Partition.Server> secondaries = new ArrayList<>();
        for (int port : primaryPorts) {
            primaries.add(new Partition.Server(HostPort.valueOf("127.0.0.1:" + port, 389), Options.defaultOptions()));
        }
        for (int port : secondaryPorts) {
            secondaries.add(new Partition.Server(HostPort.valueOf("127.0.0.1:" + port, 389), Options.defaultOptions()));
        }
        final StaticServiceDiscoveryMechanism mechanism = new StaticServiceDiscoveryMechanism();
        mechanism.partition = new Partition("priority-test", primaries, secondaries);
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
                "--set", "use-ssl:true"
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
                "--set", "replication-server:local1.example.com:636",
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
