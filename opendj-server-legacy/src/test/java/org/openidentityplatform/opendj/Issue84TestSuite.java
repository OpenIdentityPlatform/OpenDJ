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
 * Copyright 2024 3A Systems, LLC.
 */
package org.openidentityplatform.opendj;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


import static org.assertj.core.api.Assertions.assertThat;

@Test(sequential = true)
public class Issue84TestSuite extends DirectoryServerTestCase {

    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);

        TestCaseUtils.addEntries(
                "dn: ou=People,o=test",
                "objectClass: organizationalUnit",
                "objectClass: top",
                "ou: People",
                "",
                "dn: uid=user.1,ou=People,o=test",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "objectClass: top",
                "cn: Aaren Atp",
                "sn: Atp",
                "uid: user.1",
                "userPassword: password",
                "",
                "dn: ou=Services,o=test",
                "objectClass: organizationalUnit",
                "objectClass: top",
                "ou: Services",
                "aci: (version 3.0; acl \"Test ACI\"; deny (all) userdn =\"ldap:///uid=user.1,ou=People,o=test\";)",
                "",
                "dn: uid=service.1,ou=Services,o=test",
                "objectClass: top",
                "objectClass: account",
                "objectClass: simpleSecurityObject",
                "uid: service.1",
                "userPassword: password",
                ""
        );
    }

    Connection getConnection(final String user,final String password) throws LdapException {
        final LDAPConnectionFactory factory =new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        final Connection connection = factory.getConnection();
        connection.bind(user, password.toCharArray());
        assertThat(connection.isValid()).isTrue();
        return connection;
    }

    @Test
    public void test_user() throws LdapException {
        try(Connection connection=getConnection("uid=user.1,ou=People,o=test","password")){
            final SearchRequest request =Requests.newSearchRequest("ou=Services,o=test", SearchScope.WHOLE_SUBTREE,"(&)");
            System.out.println("---------------------------------------------------------------------------------------");
            System.out.println(request);

            final ConnectionEntryReader reader = connection.search(request);
            assertThat(reader.hasNext()).isFalse();
        }
    }

    @Test
    public void test_service() throws LdapException, SearchResultReferenceIOException {
        try(Connection connection=getConnection("uid=service.1,ou=Services,o=test","password")){
            final SearchRequest request =Requests.newSearchRequest("ou=Services,o=test", SearchScope.WHOLE_SUBTREE,"(&)");
            System.out.println("---------------------------------------------------------------------------------------");
            System.out.println(request);

            final ConnectionEntryReader reader = connection.search(request);

            assertThat(reader.hasNext()).isTrue();
            SearchResultEntry entry=reader.readEntry();
            System.out.println(entry);
            assertThat(entry).isNotNull();
            assertThat(entry.getName().toString()).isEqualTo("ou=Services,o=test");

            assertThat(reader.hasNext()).isTrue();
            entry=reader.readEntry();
            System.out.println(entry);
            assertThat(entry).isNotNull();
            assertThat(entry.getName().toString()).isEqualTo("uid=service.1,ou=Services,o=test");
        }
    }
}
