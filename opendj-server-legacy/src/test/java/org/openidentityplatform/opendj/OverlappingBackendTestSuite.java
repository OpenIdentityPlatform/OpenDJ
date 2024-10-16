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


import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;

@Test(sequential = true)
public class OverlappingBackendTestSuite extends DirectoryServerTestCase {

    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();

        TestCaseUtils.initializeTestBackend(true);

        TestCaseUtils.addEntries(
                "dn: ou=es,o=test",
                "objectClass: organizationalUnit",
                "objectClass: top",
                "ou: es",
                "",
                "dn: o=test,ou=es,o=test",
                "objectClass: organization",
                "objectClass: top",
                "o: test",
                "",
                "dn: uid=user.1,o=test,ou=es,o=test",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "objectClass: top",
                "cn: Aaren Atp",
                "sn: Atp",
                "uid: user.1",
                "userPassword: password",
                ""
        );
    }

    Set<String> search(final String base) throws LdapException, SearchResultReferenceIOException {
        final LDAPConnectionFactory factory =new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        final Connection connection = factory.getConnection();
        connection.bind("cn=Directory Manager", "password".toCharArray());
        assertThat(connection.isValid()).isTrue();

        Set<String> res=new HashSet<>();
        final SearchRequest request =Requests.newSearchRequest(base, SearchScope.WHOLE_SUBTREE,"(&)");
        System.out.println("---------------------------------------------------------------------------------------");
        System.out.println(request);
        final ConnectionEntryReader reader = connection.search(request);
        while(reader.hasNext()) {
            final SearchResultEntry entry=reader.readEntry();
            System.out.println(entry);
            assertThat(entry).isNotNull();
            res.add(entry.getName().toString());
        }
        connection.close();
        factory.close();
        return res;
    }

    void hasUserRoot(Set<String> res) {
        assertThat(res.contains("o=test")).isTrue();
        assertThat(res.contains("ou=es,o=test")).isTrue();
        assertThat(res.contains("o=test,ou=es,o=test")).isTrue();
        assertThat(res.contains("uid=user.1,o=test,ou=es,o=test")).isTrue();
    }

    void hasUserRoot2base(Set<String> res) {
        assertThat(res.contains("ou=eus,o=test")).isTrue();
    }

    void hasUserRoot2(Set<String> res) {
        hasUserRoot2base(res);
        assertThat(res.contains("o=test,ou=eus,o=test")).isTrue();
        assertThat(res.contains("uid=user.2,o=test,ou=eus,o=test")).isTrue();
    }
    @Test
    public void test_userRoot2() throws Exception {
        hasUserRoot(search("o=test"));
        TestCaseUtils.initializeMemoryBackend("userRoot2","ou=eus,o=test",true);
        hasUserRoot(search("o=test"));
        hasUserRoot2base(search("o=test"));
        TestCaseUtils.addEntries(
              "dn: o=test,ou=eus,o=test",
              "objectClass: organization",
              "objectClass: top",
              "o: test",
              "",
              "dn: uid=user.2,o=test,ou=eus,o=test",
              "objectClass: top",
              "objectClass: account",
              "objectClass: simpleSecurityObject",
              "uid: user.2",
              "userPassword: password",
              ""
        );
        hasUserRoot(search("o=test"));
        hasUserRoot2(search("o=test"));
        hasUserRoot2(search("ou=eus,o=test"));

        int resultCode = TestCaseUtils.applyModifications(true,
                "dn: uid=user.1,o=test,ou=es,o=test",
                "changetype: modify",
                "add: description",
                "description: user.1");
        assertEquals(resultCode, 0);

        resultCode = TestCaseUtils.applyModifications(true,
                "dn: uid=user.2,o=test,ou=eus,o=test",
                "changetype: modify",
                "add: description",
                "description: user.2");
        assertEquals(resultCode, 0);

        hasUserRoot(search("o=test"));
        hasUserRoot2(search("o=test"));
        hasUserRoot2(search("ou=eus,o=test"));

        TestCaseUtils.deleteEntry(DN.valueOf("uid=user.1,o=test,ou=es,o=test"));
        TestCaseUtils.deleteEntry(DN.valueOf("uid=user.2,o=test,ou=eus,o=test"));
      }
}
