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
import org.forgerock.opendj.server.config.server.ProxyBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.ProxyBackend;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.core.*;
import org.opends.server.types.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

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

        operation=new BindOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                "3",
                ByteString.valueOfUtf8("cn=Directory Manager"),
                ByteString.valueOfUtf8("password")
        );
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);

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

        //bind as user
        operation=new BindOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                "3",
                ByteString.valueOfUtf8("uid=test,o=test"),
                ByteString.valueOfUtf8("password")
        );
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);

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
                        RawModification.create(ModificationType.REPLACE,"sn","Test2"),
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
                new HashSet<String>(Arrays.asList(new String[]{"sn"}))
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
        assertTrue(sendSearchEntry.stream().findFirst().get().getAllAttributes("sn").iterator().next().contains(ByteString.valueOfUtf8("Test2")));

        //extended
        operation=new ExtendedOperationBasis(client, nextOperationID(), nextMessageID(), Collections.emptyList(),
                Requests.newWhoAmIExtendedRequest().getOID(), Requests.newWhoAmIExtendedRequest().getValue());
        backend.execute(operation);
        assertEquals(operation.getResultCode(),ResultCode.SUCCESS);
        assertEquals(((ExtendedOperationBasis)operation).getResponseValue().toString(),"dn:uid=test,o=test");
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
