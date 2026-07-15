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
 * Copyright 2024-2026 3A Systems, LLC.
 */
package org.openidentityplatform.opendj;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.PersistentSearchChangeType;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;

import org.opends.server.backends.MemoryBackend;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Test(sequential = true)
public class AliasTestCase extends DirectoryServerTestCase {
    /** Backend holding a naming context other than o=test, to check aliases crossing a backend boundary. */
    static final String OTHER_BACKEND_ID = "test2";

    Connection connection;

    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);
        TestCaseUtils.initializeMemoryBackend(OTHER_BACKEND_ID, "o=test2", true);

        TestCaseUtils.addEntries(
                 "dn: o=MyCompany, o=test",
                        "o: MyCompany",
                        "objectclass:organization",
                        "",
                        "dn: ou=Area1, o=test",
                        "objectclass: alias",
                        "objectclass: extensibleobject",
                        "ou: Area1",
                        "aliasedObjectName: o=MyCompany, o=test",
                        "",
                        "dn: cn=John Doe, o=MyCompany, o=test",
                        "cn: John Doe",
                        "sn: Doe",
                        "objectclass: person",
                        "",
                        "dn: cn=President, o=MyCompany, o=test",
                        "objectclass: alias",
                        "objectclass: extensibleobject",
                        "cn: President",
                        "aliasedobjectname: cn=John Doe, o=MyCompany, o=test",
                        "",

                        "dn: ou=employees,o=test",
                        "objectClass: top",
                        "objectClass: organizationalUnit",
                        "ou: employees",
                        "description: All employees",
                        "",
                        "dn: uid=jdoe,ou=employees,o=test",
                        "objectClass: alias",
                        "objectClass: top",
                        "objectClass: extensibleObject",
                        "aliasedObjectName: uid=jdoe,ou=researchers,o=test",
                        "uid: jdoe",
                        "",
                        "dn: ou=researchers,o=test",
                        "objectClass: top",
                        "objectClass: organizationalUnit",
                        "ou: researchers",
                        "description: All reasearchers",
                        "",
                        "dn: uid=jdoe,ou=researchers,o=test",
                        "objectClass: alias",
                        "objectClass: top",
                        "objectClass: extensibleObject",
                        "aliasedObjectName: uid=jdoe,ou=employees,o=test",
                        "uid: jdoe",

                        "",
                        "dn: ou=students,o=test",
                        "objectClass: top",
                        "objectClass: organizationalUnit",
                        "ou: students",
                        "description: All students",
                        "",
                        "dn: uid=janedoe,ou=students,o=test",
                        "objectClass: alias",
                        "objectClass: top",
                        "objectClass: extensibleObject",
                        "aliasedObjectName: uid=janedoe,ou=researchers,o=test",
                        "uid: janedoe",
                        "",
                        "dn: uid=janedoe,ou=researchers,o=test",
                        "objectClass: alias",
                        "objectClass: top",
                        "objectClass: extensibleObject",
                        "aliasedObjectName: uid=janedoe,ou=employees,o=test",
                        "uid: janedoe",
                        "",
                        "dn: uid=janedoe,ou=employees,o=test",
                        "objectClass: alias",
                        "objectClass: top",
                        "objectClass: extensibleObject",
                        "aliasedObjectName: uid=janedoe,ou=students,o=test",
                        "uid: janedoe",
                        "",

                        //an alias pointing into the o=test2 backend, and one pointing nowhere at all
                        "dn: ou=people,o=test2",
                        "objectClass: top",
                        "objectClass: organizationalUnit",
                        "ou: people",
                        "",
                        "dn: cn=Jane Roe,ou=people,o=test2",
                        "cn: Jane Roe",
                        "sn: Roe",
                        "objectclass: person",
                        "",
                        "dn: ou=external,o=test",
                        "objectClass: alias",
                        "objectClass: top",
                        "objectClass: extensibleObject",
                        "ou: external",
                        "aliasedObjectName: ou=people,o=test2",
                        "",
                        "dn: ou=nowhere,o=test",
                        "objectClass: alias",
                        "objectClass: top",
                        "objectClass: extensibleObject",
                        "ou: nowhere",
                        "aliasedObjectName: ou=people,o=missing",
                        ""
        );

        final LDAPConnectionFactory factory =new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        connection = factory.getConnection();
        connection.bind("cn=Directory Manager", "password".toCharArray());
        assertThat(connection.isValid()).isTrue();
    }

    @AfterClass
    public void stopServer() throws Exception {
        if (connection != null) {
            connection.close();
        }
        final MemoryBackend otherBackend = (MemoryBackend) TestCaseUtils.getServerContext()
                .getBackendConfigManager().getLocalBackendById(OTHER_BACKEND_ID);
        if (otherBackend != null) {
            otherBackend.clearMemoryBackend();
            otherBackend.finalizeBackend();
            TestCaseUtils.getServerContext().getBackendConfigManager().deregisterLocalBackend(otherBackend);
        }
    }

    public HashMap<String,SearchResultEntry> search(SearchScope scope,DereferenceAliasesPolicy policy) throws SearchResultReferenceIOException, LdapException {
        return search("ou=Area1,o=test", scope, policy);
    }

    public HashMap<String,SearchResultEntry> search(String dn, SearchScope scope,DereferenceAliasesPolicy policy) throws SearchResultReferenceIOException, LdapException {
        final SearchRequest request =Requests.newSearchRequest(dn, scope,"(objectclass=*)")
                .setDereferenceAliasesPolicy(policy);
        System.out.println("---------------------------------------------------------------------------------------");
        System.out.println(request);

        HashMap<String,SearchResultEntry> res=new HashMap<>();
        final ConnectionEntryReader reader = connection.search(request);
        while (reader.hasNext()) {
            final SearchResultEntry srEntry = reader.readEntry();
            System.out.println(srEntry);
            assertThat(res.put(srEntry.getName().toString(),srEntry)).isNull();
        }
        return res;
    }

    //https://docs.oracle.com/cd/E21043_01/oid.1111/e10029/oid_alias_entries.htm
    //
    //    16.3.1 Searching the Base with Alias Entries
    //
    //    A base search finds the top level of the alias entry you specify.
    @Test
    public void test_base_never() throws LdapException, SearchResultReferenceIOException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.BASE_OBJECT,DereferenceAliasesPolicy.NEVER);

        assertThat(res.containsKey("ou=Area1,o=test")).isTrue();
        assertThat(res.containsKey("o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isFalse();
    }
    //    Base Search with the Dereferencing Flag -a find
    //
    //    This example shows a base search of ou=Area1,o=test with a filter of "objectclass=*" with the dereferencing flag set to -a find.
    //
    //    ldapsearch -p port -h host -b "ou=Area1,o=test" -a find -s base "objectclass=*"
    //    The directory server, during the base search, looks up the base specified in the search request and returns it to the user. However, if the base is an alias entry and, as in the example, -a find is specified in the search request, then the directory server automatically dereferences the alias entry and returns the entry it points to.
    //    In this example, the search dereferences ou=Area1,o=test, which is an alias entry, and returns o=MyCompany,o=test.
    @Test
    public void test_base_find() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.BASE_OBJECT,DereferenceAliasesPolicy.FINDING_BASE);

        assertThat(res.containsKey("ou=Area1,o=test")).isFalse();
        assertThat(res.containsKey("o=MyCompany,o=test")).isTrue();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isFalse();
    }

    //    Base Search with the Dereferencing Flag -a search
    //
    //    This example shows a base search of ou=Area1,o=test with a filter of "objectclass=*" with the dereferencing flag set to -a search.
    //
    //    ldapsearch -p port -h host -b "ou=Area1,o=test" -a search -s base "objectclass=*"
    //    The directory server, during the base search, looks up the base specified in the search request and returns it to the user without dereferencing it.
    //    It returns ou=Area1,o=test.
    @Test
    public void test_base_search() throws SearchResultReferenceIOException, LdapException  {
        HashMap<String,SearchResultEntry> res=search(SearchScope.BASE_OBJECT, DereferenceAliasesPolicy.IN_SEARCHING);

        assertThat(res.containsKey("ou=Area1,o=test")).isTrue();
        assertThat(res.containsKey("o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isFalse();
    }

    //    Base Search with the Dereferencing Flag -a always
    //
    //    This example shows a base search of ou=Area1,o=test with a filter of "objectclass=*" with the dereferencing flag set to -a always.
    //
    //    ldapsearch -p port -h host -b "ou=Area1,o=test" -a always -s base "objectclass=*"
    //    The directory server, during the base search, looks up the base specified in the search request.
    //    If it is an alias entry, the directory server automatically dereferences the alias entry and returns the entry it points to.
    //    In this example, the search dereferences ou=Area1,o=test, which is an alias entry, and returns o=MyCompany,o=test.
    @Test
    public void test_base_always() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.BASE_OBJECT,DereferenceAliasesPolicy.ALWAYS);

        assertThat(res.containsKey("ou=Area1,o=test")).isFalse();
        assertThat(res.containsKey("o=MyCompany,o=test")).isTrue();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isFalse();
    }

    //16.3.2 Searching One-Level with Alias Entries
    //
    //    A one-level search finds only the children of the base level you specify.
    @Test
    public void test_one_never() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.SINGLE_LEVEL,DereferenceAliasesPolicy.NEVER);

        assertThat(res.containsKey("ou=Area1,o=test")).isFalse();
        assertThat(res.containsKey("o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isFalse();
    }

    //    One-Level Search with the Dereferencing Flag -a find
    //
    //    This example shows a one-level search of "ou=Area1,o=test" with a filter of "objectclass=*" with the dereferencing flag set to -a find.
    //
    //    ldapsearch -p port -h host -b "ou=Area1,o=test" -a find -s one "objectclass=*"
    //    The directory server returns one-level entries under the base that match the filter criteria. In the example, -a find is specified in the search request, and thus the directory server automatically dereferences while looking up the base (the first step), but does not dereference alias entries that are one level under the base. Therefore, the search dereferences ou=Area1,o=test, which is an alias entry, and then looks up one-level entries under o=MyCompany,o=test. One of the one-level entries is cn=President,o=MyCompany,o=test that is not dereferenced and is returned as is.
    //
    //            Thus, the search returns cn=President,o=MyCompany,o=test and cn=John Doe,o=MyCompany,o=test.
    @Test
    public void test_one_find() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.SINGLE_LEVEL,DereferenceAliasesPolicy.FINDING_BASE);

        assertThat(res.containsKey("ou=Area1,o=test")).isFalse();
        assertThat(res.containsKey("o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isTrue();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isTrue();
    }
    
    //            One-Level Search with the Dereferencing Flag -a search
    //
    //    This example shows a one-level search of "ou=Area1,o=test" with a filter of "objectclass=*" with the dereferencing flag set to -a search.
    //    ldapsearch -p port -h host -b "ou=Area1,o=test" -a search -s one "objectclass=*"
    //    The directory server searches for the base that is specified in the search request.
    //    If the base entry is an alias entry, it returns nothing. (Alias entries cannot have children.)
    //    Otherwise, it returns the base entry's immediate children after dereferencing them.
    //    In this example, the base entry is "ou=Area1,o=test", which is an alias entry, so the search returns nothing

    @Test
    public void test_one_search() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.SINGLE_LEVEL,DereferenceAliasesPolicy.IN_SEARCHING);

        assertThat(res.containsKey("ou=Area1,o=test")).isFalse();
        assertThat(res.containsKey("o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isFalse();
    }

    //    One-Level Search with the Dereferencing Flag -a always
    //
    //    This example shows a one-level search of "ou=Area1,o=test" with a filter of "objectclass=*" with the dereferencing flag set to -a always.
    //    ldapsearch -p port -h host -b "ou=Area1,o=test" -a always -s one "objectclass=*"
    //    In the example, -a always is specified in the search request, and thus the directory server automatically dereferences while looking up the base (the first step),
    //    then dereference alias entries that are one level under the base.
    //    Therefore, the search dereferences ou=Area1,o=test, which is an alias entry, and then looks up one-level entries under o=MyCompany,o=test.
    //    One of the one-level entries is cn=President,o=MyCompany,o=test. That is dereferenced and is returned as cn=John Doe,o=MyCompany,o=test.
    //    The other one-level entry is cn=John Doe,o=MyCompany,o=test, which has already been returned.
    //
    //            Thus, the search returns cn=John Doe,o=MyCompany,o=test.
    @Test
    public void test_one_always() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.SINGLE_LEVEL,DereferenceAliasesPolicy.ALWAYS);

        assertThat(res.containsKey("ou=Area1,o=test")).isFalse();
        assertThat(res.containsKey("o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isTrue();
    }

    //16.3.3 Searching a Subtree with Alias Entries
    //
    //    A subtree search finds the base, children, and grand children.
    @Test
    public void test_sub_never() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.WHOLE_SUBTREE,DereferenceAliasesPolicy.NEVER);

        assertThat(res.containsKey("ou=Area1,o=test")).isTrue();
        assertThat(res.containsKey("o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isFalse();
    }

    //    Subtree Search with the Dereferencing Flag -a find
    //
    //    This example shows a subtree search of "ou=Area1,o=test" with a filter of "objectclass=*" with the dereferencing flag set to -a find.
    //
    //    ldapsearch -p port -h host -b "ou=Area1,o=test" -a find -s sub "objectclass=*"
    //    The directory server returns all entries under the base that match the filter criteria.
    //    In the example, -a find is specified in the search request, and thus the directory server automatically dereferences while looking up the base (the first step),
    //    but does not dereference alias entries that are under the base.
    //    Therefore, the search dereferences ou=Area1,o=test, which is an alias entry, and then looks up entries under o=MyCompany,o=test.
    //    One of the entries is cn=President,o=MyCompany,o=test that is not dereferenced and is returned as is.
    //
    //    Thus, the search returns:
    //
    //    o=MyCompany,o=test
    //            cn=John doe,o=MyCompany,o=test
    //            cn=President,o=MyCompany,o=test
    @Test
    public void test_sub_find() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.WHOLE_SUBTREE,DereferenceAliasesPolicy.FINDING_BASE);

        assertThat(res.containsKey("ou=Area1,o=test")).isFalse();
        assertThat(res.containsKey("o=MyCompany,o=test")).isTrue();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isTrue();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isTrue();
    }

    //    Subtree Search with the Dereferencing Flag -a search
    //
    //    This example shows a subtree search of "ou=Area1,o=test" with a filter of "objectclass=*" with the dereferencing flag set to -a search.
    //
    //    ldapsearch -p port -h host -b "ou=Area1,o=test" -a search -s sub "objectclass=*"
    //    The directory searches for the base that is specified in the search request.
    //    If the base is an alias entry, then it returns the base entry without dereferencing it. (Alias entries cannot have children.)
    //    Otherwise it returns all entries under the base. If any alias entries are found, it dereferences them and returns all entries under them as well.
    //
    //    In this example, the base entry is an alias entry, ou=Area1,o=test, so the directory returns ou=Area1,o=test.
    //    In this example, the base entry is ou=Area1,o=test, which is dereferenced to o=MyCompany,o=test, which is returned.
    //    There are two entries under o=MyCompany,o=test.
    //    One is cn=President,o=MyCompany,o=test, which is returned and also dereferenced to cn=John Doe,o=MyCompany,o=test, which is returned.
    //    The other entry under o=MyCompany,o=test, which has already been returned.
    //    So the result is o=MyCompany,o=test and cn=John Doe,o=MyCompany,o=test.
    @Test
    public void test_sub_search() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.WHOLE_SUBTREE,DereferenceAliasesPolicy.IN_SEARCHING);

        assertThat(res.containsKey("ou=Area1,o=test")).isFalse();
        assertThat(res.containsKey("o=MyCompany,o=test")).isTrue();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isTrue();
    }

    //    Subtree Search with the Dereferencing Flag -a always
    //
    //    This example shows a subtree search of "ou=Area1,o=test" with a filter of "objectclass=*" with the dereferencing flag set to -a always.
    //
    //    ldapsearch -p port -h host -b "ou=Area1,o=test" -a always -s sub "objectclass=*"
    //    The directory server dereferences the base entry and returns it. It also returns all entries under the dereferenced base. If any alias entries are found, it dereferences them and returns all entries under them as well.
    //
    //    In this example, the base entry is ou=Area1,o=test, which is dereferenced to o=MyCompany,o=test, which is returned.
    //    There are two entries under o=MyCompany,o=test.
    //    One is cn=President,o=MyCompany,o=test, which is returned and also dereferenced to cn=John Doe,o=MyCompany,o=test, which is returned.
    //    The other entry under o=MyCompany,o=test, which has already been returned.
    //    So the result is o=MyCompany,o=test and cn=John Doe,o=MyCompany,o=test.
    @Test
    public void test_sub_always() throws SearchResultReferenceIOException, LdapException {
        HashMap<String,SearchResultEntry> res=search(SearchScope.WHOLE_SUBTREE,DereferenceAliasesPolicy.ALWAYS);

        assertThat(res.containsKey("ou=Area1,o=test")).isFalse();
        assertThat(res.containsKey("o=MyCompany,o=test")).isTrue();
        assertThat(res.containsKey("cn=President,o=MyCompany,o=test")).isFalse();
        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isTrue();
    }

    // Dereferencing recursion avoidance test.
    @Test
    public void test_alias_recursive() throws LdapException, SearchResultReferenceIOException {
        HashMap<String, SearchResultEntry> res = search("uid=jdoe,ou=employees,o=test", SearchScope.WHOLE_SUBTREE, DereferenceAliasesPolicy.ALWAYS);

        assertThat(res.containsKey("uid=jdoe,ou=employees,o=test")).isTrue();
        assertThat(res.containsKey("uid=jdoe,ou=researchers,o=test")).isFalse();
    }

    @Test
    public void test_alias_recursive_loop() throws LdapException, SearchResultReferenceIOException {
        HashMap<String, SearchResultEntry> res = search("uid=janedoe,ou=students,o=test", SearchScope.WHOLE_SUBTREE, DereferenceAliasesPolicy.ALWAYS);

        assertThat(res.containsKey("uid=janedoe,ou=students,o=test")).isTrue();
        assertThat(res.containsKey("uid=janedoe,ou=researches,o=test")).isFalse();
        assertThat(res.containsKey("uid=janedoe,ou=employees,o=test")).isFalse();
    }

    //    The alias target does not have to live in the backend holding the alias:
    //    the search must be handed over to the backend holding the target.
    @Test
    public void test_cross_backend_alias_base_always() throws LdapException, SearchResultReferenceIOException {
        HashMap<String, SearchResultEntry> res = search("ou=external,o=test", SearchScope.BASE_OBJECT, DereferenceAliasesPolicy.ALWAYS);

        assertThat(res.containsKey("ou=external,o=test")).isFalse();
        assertThat(res.containsKey("ou=people,o=test2")).isTrue();
        assertThat(res.containsKey("cn=Jane Roe,ou=people,o=test2")).isFalse();
    }

    @Test
    public void test_cross_backend_alias_sub_always() throws LdapException, SearchResultReferenceIOException {
        HashMap<String, SearchResultEntry> res = search("ou=external,o=test", SearchScope.WHOLE_SUBTREE, DereferenceAliasesPolicy.ALWAYS);

        assertThat(res.containsKey("ou=external,o=test")).isFalse();
        assertThat(res.containsKey("ou=people,o=test2")).isTrue();
        assertThat(res.containsKey("cn=Jane Roe,ou=people,o=test2")).isTrue();
    }

    //    No backend at all holds the alias target: this is a base DN that does not exist.
    @Test
    public void test_alias_target_without_backend() throws SearchResultReferenceIOException {
        try {
            search("ou=nowhere,o=test", SearchScope.BASE_OBJECT, DereferenceAliasesPolicy.ALWAYS);
            fail("dereferencing an alias whose target has no backend must fail");
        } catch (LdapException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.NO_SUCH_OBJECT);
        }
    }

    // An alias whose target lies outside the search scope: the target is only
    // reachable by dereferencing, so it must be returned by the search.
    @Test
    public void test_deref_target_outside_scope() throws Exception {
        TestCaseUtils.addEntries(
                "dn: ou=pointers,o=test",
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: pointers",
                "",
                "dn: cn=ptr,ou=pointers,o=test",
                "objectClass: alias",
                "objectClass: top",
                "objectClass: extensibleObject",
                "cn: ptr",
                "aliasedObjectName: cn=John Doe,o=MyCompany,o=test",
                ""
        );
        HashMap<String, SearchResultEntry> res =
                search("ou=pointers,o=test", SearchScope.WHOLE_SUBTREE, DereferenceAliasesPolicy.ALWAYS);

        assertThat(res.containsKey("cn=John Doe,o=MyCompany,o=test")).isTrue();
    }

    // An alias pointing at an entry which does not exist is skipped, and the entries next to it are
    // still returned: aliases are not checked when they are written, so a dangling one is expected.
    @Test
    public void test_dangling_alias() throws Exception {
        TestCaseUtils.addEntries(
                "dn: ou=dangling,o=test",
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: dangling",
                "",
                "dn: cn=broken,ou=dangling,o=test",
                "objectClass: alias",
                "objectClass: top",
                "objectClass: extensibleObject",
                "cn: broken",
                "aliasedObjectName: cn=does-not-exist,ou=dangling,o=test",
                "",
                "dn: cn=intact,ou=dangling,o=test",
                "cn: intact",
                "sn: intact",
                "objectClass: person",
                ""
        );
        HashMap<String, SearchResultEntry> res =
                search("ou=dangling,o=test", SearchScope.WHOLE_SUBTREE, DereferenceAliasesPolicy.ALWAYS);

        assertThat(res.containsKey("cn=does-not-exist,ou=dangling,o=test")).isFalse();
        assertThat(res.containsKey("ou=dangling,o=test")).isTrue();
        assertThat(res.containsKey("cn=intact,ou=dangling,o=test")).isTrue();
    }

    // A persistent search reports every change it is notified of. Dereferencing aliases must not
    // make it drop a change because the entry was notified before.
    @Test
    public void test_persistent_search_reports_repeated_changes() throws Exception {
        TestCaseUtils.addEntries(
                "dn: ou=psearch,o=test",
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: psearch",
                "",
                "dn: cn=changing,ou=psearch,o=test",
                "cn: changing",
                "sn: changing",
                "objectClass: person",
                ""
        );

        final BlockingQueue<String> notified = new LinkedBlockingQueue<>();
        final SearchRequest request =
                Requests.newSearchRequest("ou=psearch,o=test", SearchScope.WHOLE_SUBTREE, "(objectclass=*)")
                        .setDereferenceAliasesPolicy(DereferenceAliasesPolicy.ALWAYS)
                        .addControl(PersistentSearchRequestControl.newControl(
                                true, true, false, PersistentSearchChangeType.MODIFY));

        final LDAPConnectionFactory factory =
                new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        try (Connection psearch = factory.getConnection()) {
            psearch.bind("cn=Directory Manager", "password".toCharArray());
            psearch.searchAsync(request, new SearchResultHandler() {
                @Override
                public boolean handleEntry(SearchResultEntry entry) {
                    notified.add(entry.getName().toString());
                    return true;
                }

                @Override
                public boolean handleReference(SearchResultReference reference) {
                    return true;
                }
            });

            // The same entry is modified repeatedly: each change must reach the persistent search.
            for (int i = 1; i <= 3; i++) {
                connection.modify(Requests.newModifyRequest("cn=changing,ou=psearch,o=test")
                        .addModification(ModificationType.REPLACE, "description", "change " + i));
                assertThat(notified.poll(30, TimeUnit.SECONDS))
                        .as("notification for change " + i)
                        .isEqualTo("cn=changing,ou=psearch,o=test");
            }
        }
    }

    @Test(expectedExceptions = LdapException.class)
    public void test_stackoverflow() throws Exception {

        String entryTemplate = "dn: uid={uid},ou=employees,o=test\n" +
                "objectClass: alias\n" +
                "objectClass: top\n" +
                "objectClass: extensibleObject\n" +
                "aliasedObjectName: uid={alias},ou=employees,o=test \n" +
                "uid: {uid}\n";
        final String firstDn = "uid=jdoe0,ou=employees,o=test";
        for(int i = 0; i < 10000; i++) {
            String entryStr = entryTemplate.replace("{uid}", "jdoe" + i).replace("{alias}", "jdoe" + (i + 1));
            Entry entry = TestCaseUtils.makeEntry(entryStr);
            TestCaseUtils.addEntry(entry);
        }
        search(firstDn, SearchScope.WHOLE_SUBTREE, DereferenceAliasesPolicy.ALWAYS);
    }
}
