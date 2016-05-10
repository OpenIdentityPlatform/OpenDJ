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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.adapter.server3x;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.AUTHN_BIND_REQUEST;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;

import java.security.GeneralSecurityException;
import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ConstraintViolationException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.MatchedValuesRequestControl;
import org.forgerock.opendj.ldap.controls.PermissiveModifyRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.controls.SubentriesRequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.PlainSASLBindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.WhoAmIExtendedRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.WhoAmIExtendedResult;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.util.Options;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** This class defines a set of tests for the Adapters.class. */
@SuppressWarnings("javadoc")
@Test(sequential=true)
public class AdaptersTestCase extends DirectoryServerTestCase {
    private static final String USER_0_DN_STRING = "uid=user.0,o=test";

    private Integer getServerLdapPort() {
        return TestCaseUtils.getServerLdapPort();
    }

    @DataProvider
    public Object[][] rootConnectionFactories() throws DirectoryException {
        return new Object[][] {
            { new LDAPConnectionFactory("localhost",
                                        getServerLdapPort(),
                                        Options.defaultOptions()
                                               .set(AUTHN_BIND_REQUEST,
                                                    newSimpleBindRequest("cn=directory manager",
                                                                                  "password".toCharArray()))) },
            { Adapters.newConnectionFactory(new InternalClientConnection(DN.valueOf("cn=directory manager"))) },
            { Adapters.newRootConnectionFactory() } };
    }

    /**
     * Launched before the tests, this function starts the server and adds data.
     *
     * @throws Exception
     *         If the server could not be initialized.
     */
    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();

        // Creates a root connection to add data
        final Connection connection = Adapters.newRootConnectionFactory().getConnection();
        // @formatter:off
        connection.add(
                "dn: uid=user.0, o=test",
                "objectClass: top",
                "objectClass: person",
                "objectClass: inetOrgPerson",
                "objectClass: organizationalPerson",
                "cn: Aaccf Amar",
                "sn:user.0",
                "uid:user.0",
                "description: This is the description for Aaccf Amar.",
                "userPassword:: cGFzc3dvcmQ=",
                "postalAddress: Aaccf Amar$01251 Chestnut Street$Panama City, DE  50369",
                "postalCode: 50369");

        connection.add(
                "dn: uid=user.1, o=test",
                "objectClass: top",
                "objectClass: person",
                "objectClass: inetOrgPerson",
                "objectClass: organizationalPerson",
                "cn: Aaren Atp",
                "sn:user.1",
                "uid:user.1",
                "description: This is the description for Aaren Atp.",
                "postalAddress: Aaren Atp$70110 Fourth Street$New Haven, OH  93694",
                "postalCode: 93694");

        connection.add(
                "dn: uid=user.2, o=test",
                "objectClass: top",
                "objectClass: person",
                "objectClass: inetOrgPerson",
                "objectClass: organizationalPerson",
                "cn: Aarika Atpco",
                "sn: user.2",
                "uid:user.2",
                "description: This is the description for Aarika Atpco.",
                "userPassword:: cGFzc3dvcmQ=",
                "postalAddress: Aarika Atpco$00900 Maple Street$New Orleans, KS  10857",
                "postalCode: 10857");

        connection.add(
                "dn: uid=user.3, o=test",
                "objectClass: top",
                "objectClass: person",
                "objectClass: inetOrgPerson",
                "objectClass: organizationalPerson",
                "cn: Aaron Atrc",
                "sn:user.3",
                "uid:user.3",
                "description: This is the description for Aaron Atrc.",
                "postalAddress: Aaron Atrc$59748 Willow Street$Green Bay, TN  66239",
                "postalCode: 66239");

        connection.add(
                "dn: uid=user.4, o=test",
                "objectClass: top",
                "objectClass: person",
                "objectClass: inetOrgPerson",
                "objectClass: organizationalPerson",
                "cn: Samantha Carter",
                "sn: Carter",
                "uid:user.4",
                "description: This is the description for Samantha Carter.",
                "postalAddress: 59748 Willow Street$Green Bay, TN  66000",
                "postalCode: 66000");
        // @formatter:on

        connection.close();
    }

    /** Clean up data at the end of tests. */
    @AfterClass
    public void shutDownEmbeddedServerServer() throws Exception {
        // Delete all added entries but user.3 which is already removed in one test
        for (int i = 0; i < 5; i++) {
            if (i != 3) {
                TestCaseUtils.deleteEntry(DN.valueOf("uid=user." + i + ", o=test"));
            }
        }
    }

    /**
     * A simple LDAP connection.
     *
     * @throws LdapException
     */
    @Test
    public void testSimpleLDAPConnectionFactorySimpleBind() throws LdapException {
        try (final LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", getServerLdapPort());
             final Connection connection = factory.getConnection()) {
            connection.bind("cn=Directory Manager", "password".toCharArray());
            assertThat(connection.isValid()).isTrue();
            assertThat(connection.isClosed()).isFalse();
        }
    }

    /**
     * Tests a SASL Bind with an LDAP connection.
     *
     * @throws NumberFormatException
     * @throws GeneralSecurityException
     * @throws LdapException
     */
    @Test
    public void testLDAPSASLBind() throws NumberFormatException, GeneralSecurityException, LdapException {
        final PlainSASLBindRequest request = Requests.newPlainSASLBindRequest("u:user.0", "password".toCharArray());
        try (final LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", getServerLdapPort());
             final Connection connection = factory.getConnection()) {
            connection.bind(request);
        }
    }

    /**
     * Tests an SASL connection with the adapter.
     *
     * @throws LdapException
     */
    @Test
    public void testAdapterConnectionSASLBindRequest() throws LdapException {
        PlainSASLBindRequest request =
                Requests.newPlainSASLBindRequest("u:user.0", "password".toCharArray());
        try (final Connection connection = Adapters.newRootConnectionFactory().getConnection()) {
            connection.bind(request);
        }
    }

    /**
     * Binds as a root.
     *
     * @throws Exception
     */
    @Test
    public void testAdapterConnectionSimpleBindAsRoot() throws Exception {
        try (final Connection connection = Adapters.newRootConnectionFactory().getConnection()) {
            final BindResult result = connection.bind("cn=Directory Manager", "password".toCharArray());
            assertThat(connection.isValid()).isTrue();
            assertThat(result.getResultCode()).isEqualTo(ResultCode.SUCCESS);
        }
    }

    /**
     * Binds as a known user.
     *
     * @throws Exception
     */
    @Test
    public void testAdapterConnectionSimpleBindAsAUser() throws Exception {
        try (final Connection connection = Adapters.newConnectionFactory(
                new InternalClientConnection(DN.valueOf(USER_0_DN_STRING))).getConnection()) {
            final BindResult result = connection.bind(USER_0_DN_STRING, "password".toCharArray());
            assertThat(result.getResultCode()).isEqualTo(ResultCode.SUCCESS);
        }
    }

    /**
     * Binds as a known user but using the wrong password.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = AuthenticationException.class)
    public void testAdapterConnectionSimpleBindAsAUserWrongPassword() throws Exception {
        try (final Connection connection =  Adapters.newConnectionFactory(
                new InternalClientConnection(DN.valueOf(USER_0_DN_STRING))).getConnection()) {
            // Invalid credentials
            connection.bind(USER_0_DN_STRING, "pass".toCharArray());
        }
    }

    /**
     * Testing the adapters with a simple add request.
     *
     * @throws Exception
     */
    @Test
    public void testAdapterAddRequest() throws Exception {
        final Connection connection = Adapters.newRootConnectionFactory().getConnection();
        // @formatter:off
        final AddRequest addRequest = Requests.newAddRequest(
                "dn: sn=carter,o=test",
                "objectClass: top",
                "objectClass: person",
                "cn: scarter");
        // @formatter:on

        final Result r = connection.add(addRequest);
        assertThat(r.getDiagnosticMessage()).isEmpty();
        assertThat(r.isSuccess()).isTrue();

        // We find the added entry :
        final SearchResultEntry srEntry =
                connection.searchSingleEntry(Requests.newSearchRequest(
                        "sn=carter,o=test", SearchScope.BASE_OBJECT, "(cn=scarter)"));
        assertThat(srEntry.getName().toString()).isEqualTo("sn=carter,o=test");

        connection.close();
    }

    /**
     * Tries an add request but the entry already exists.
     *
     * @throws Exception
     */
    @Test(dataProvider = "rootConnectionFactories",
            expectedExceptions = ConstraintViolationException.class)
    public void testAdapterAddRequestFails(final ConnectionFactory factory) throws Exception {
        // @formatter:off
        final AddRequest addRequest = Requests.newAddRequest(
                "dn: sn=bjensen,o=test",
                "objectClass: top",
                "objectClass: person",
                "cn: bjensen");
        // @formatter:on

        try (final Connection connection = factory.getConnection()) {

            // First add :
            Result r = connection.add(addRequest);
            assertThat(r.getDiagnosticMessage()).isEmpty();
            assertThat(r.isSuccess()).isTrue();
            // Second :
            r = connection.add(addRequest);
        }
    }

    /**
     * Uses the adapter to perform a search request.
     *
     * @throws Exception
     */
    @Test
    public void testAdapterSearchRequest() throws Exception {
        final Connection connection = Adapters.newRootConnectionFactory().getConnection();

        final SearchRequest request =
                Requests.newSearchRequest("o=test", SearchScope.WHOLE_SUBTREE,
                        "(sn=user.1)");

        // Performs the search :
        final ConnectionEntryReader reader = connection.search(request);

        assertThat(reader.isEntry()).isTrue();
        final SearchResultEntry srEntry = reader.readEntry();
        assertThat(srEntry).isNotNull();

        Entry expectedEntry =
                new LinkedHashMapEntry("dn: uid=user.1,o=test", "objectClass: top",
                        "objectClass: person", "objectClass: inetOrgPerson",
                        "objectClass: organizationalPerson", "sn:user.1", "uid:user.1",
                        "cn: Aaren Atp", "description: This is the description for Aaren Atp.",
                        "postalAddress: Aaren Atp$70110 Fourth Street$New Haven, OH  93694",
                        "postalCode: 93694");
        Entry original = new LinkedHashMapEntry(srEntry);

        // No differences expected.
        assertThat(Entries.diffEntries(original, expectedEntry).getModifications()).isEmpty();

        assertThat(srEntry.getName().toString()).isEqualTo("uid=user.1,o=test");
        assertThat(srEntry.getAttributeCount()).isEqualTo(7);
        assertThat(srEntry.getAttribute("description").firstValueAsString()).isEqualTo(
                "This is the description for Aaren Atp.");
        assertThat(srEntry.getAttribute("postalAddress").firstValueAsString()).isEqualTo(
                "Aaren Atp$70110 Fourth Street$New Haven, OH  93694");
        assertThat(srEntry.getAttribute("postalCode").firstValueAsString()).isEqualTo("93694");
        // top - person - inetOrgPerson - organizationalPerson.
        assertThat(srEntry.getAttribute("objectClass").toArray().length).isEqualTo(4);
        assertThat(reader.hasNext()).isFalse();

        connection.close();
    }

    /**
     * Tries to perform a search with the adapter. No result expected.
     *
     * @throws Exception
     */
    @Test(dataProvider = "rootConnectionFactories")
    public void testAdapterSearchRequestWithNoResults(final ConnectionFactory factory)
            throws Exception {
        final SearchRequest request =
                Requests.newSearchRequest("o=test", SearchScope.WHOLE_SUBTREE,
                        "(uid=unknown)").addControl(
                        MatchedValuesRequestControl.newControl(true, "(uid=user.1)"));

        final Connection connection = factory.getConnection();
        ConnectionEntryReader reader = connection.search(request);

        assertThat(reader.hasNext()).isFalse();

        connection.close();
    }

    /**
     * Tries to perform a search single entry. Exception expected.
     *
     * @param factory
     *            The connection factory.
     * @throws Exception
     */
    @Test(dataProvider = "rootConnectionFactories",
            expectedExceptions = EntryNotFoundException.class)
    public void testAdapterSearchSingleEntryWithNoResults(final ConnectionFactory factory)
            throws Exception {
        try (final Connection connection = factory.getConnection()) {
            connection.searchSingleEntry(Requests.newSearchRequest("o=test",
                    SearchScope.WHOLE_SUBTREE, "(uid=unknown)"));
        }
    }

    /**
     * Performs a search with a sub entries request control. Sub-entries are
     * included and the normal entries are excluded. No result expected.
     *
     * @throws LdapException
     * @throws SearchResultReferenceIOException
     */
    @Test(dataProvider = "rootConnectionFactories",
            expectedExceptions = NoSuchElementException.class)
    public void testAdapterSearchRequestSubEntriesWithNoResult(final ConnectionFactory factory)
            throws LdapException, SearchResultReferenceIOException {
        try (final Connection connection = factory.getConnection()) {
            final SearchRequest request =
                    Requests.newSearchRequest("o=test", SearchScope.WHOLE_SUBTREE,
                            "cn=*", "cn", "subtreeSpecification")
                    // sub-entries included, normal entries excluded.
                            .addControl(SubentriesRequestControl.newControl(true, true));

            final ConnectionEntryReader reader = connection.search(request);
            assertThat(reader.isEntry()).isFalse();
            assertThat(reader.isReference()).isFalse();
            reader.readEntry();
        }
    }

    /**
     * Performs a search with a sub entries request control. Sub-entries are
     * excluded this time and the normal entries are included.
     *
     * @throws LdapException
     * @throws SearchResultReferenceIOException
     */
    @Test(dataProvider = "rootConnectionFactories")
    public void testAdapterSearchRequestSubEntries(final ConnectionFactory factory)
            throws LdapException, SearchResultReferenceIOException {
        final Connection connection = factory.getConnection();

        final SearchRequest request =
                Requests.newSearchRequest("o=test", SearchScope.WHOLE_SUBTREE, "cn=*",
                        "cn", "subtreeSpecification")
                // sub-entries excluded, normal entries included.
                        .addControl(SubentriesRequestControl.newControl(true, false));

        final ConnectionEntryReader reader = connection.search(request);
        assertThat(reader.isEntry()).isTrue();
        // All the entries are present.
        int nbEntries = 0;
        while (reader.hasNext()) {
            final SearchResultEntry entry = reader.readEntry();
            assertThat(entry).isNotNull();
            nbEntries++;
        }
        // All the entries must appear (except those which were deleted by another operation/test).
        assertThat(nbEntries >= 3);

        connection.close();
    }

    /**
     * Deletes an inexistent entry.
     *
     * @throws LdapException
     */
    @Test(dataProvider = "rootConnectionFactories",
            expectedExceptions = EntryNotFoundException.class)
    public void testAdapterDeleteRequestNoSuchEntry(final ConnectionFactory factory) throws LdapException {
        final DeleteRequest deleteRequest = Requests.newDeleteRequest("cn=test");
        try (final Connection connection = factory.getConnection()) {
            connection.delete(deleteRequest);
        }
    }

    /**
     * Deletes an existing entry with the 'no-op' control.
     *
     * @throws LdapException
     */
    @Test(dataProvider = "rootConnectionFactories")
    public void testAdapterDeleteRequestNoOpControl(final ConnectionFactory factory) throws LdapException {
        final DeleteRequest deleteRequest =
                Requests.newDeleteRequest("uid=user.1, o=test")
                // The no-op control is specified with his OID.
                        .addControl(GenericControl.newControl("1.3.6.1.4.1.4203.1.10.2"));

        final Connection connection = factory.getConnection();
        final Result result = connection.delete(deleteRequest);
        assertThat(result.getResultCode()).isEqualTo(ResultCode.NO_OPERATION);
        // Verifies that the delete has no impact in this case (due to no_operation control)
        final SearchResultEntry srEntry =
                connection.searchSingleEntry(Requests.newSearchRequest(
                        "uid=user.1, o=test", SearchScope.BASE_OBJECT, "(uid=user.1)"));
        assertThat(srEntry).isNotNull();
        assertThat(srEntry.getName().toString()).isEqualTo("uid=user.1,o=test");

        connection.close();
    }

    /**
     * Deletes an existing entry.
     *
     * @throws LdapException
     */
    @Test
    public void testAdapterDeleteRequest() throws LdapException {
        try (final Connection connection = Adapters.newRootConnectionFactory().getConnection()) {
            // Checks if the entry exists.
            SearchResultEntry sre =
                    connection.searchSingleEntry(Requests.newSearchRequest(
                            "uid=user.3, o=test", SearchScope.BASE_OBJECT, "(uid=user.3)"));
            assertThat(sre).isNotNull();

            final DeleteRequest deleteRequest =
                    Requests.newDeleteRequest("uid=user.3, o=test");

            connection.delete(deleteRequest);

            // Verifies if the entry was correctly deleted.
            connection.searchSingleEntry(Requests.newSearchRequest("uid=user.3, o=test",
                    SearchScope.BASE_OBJECT, "(uid=user.3)"));
            fail("Expected EntryNotFoundException to be thrown");
        } catch (EntryNotFoundException ex) {
            // Expected - no result.
        }
    }

    /**
     * Modifies an existing entry.
     *
     * @throws LdapException
     * @throws DecodeException
     */
    @Test
    public void testAdapterModifyRequest() throws LdapException, DecodeException {
        final ModifyRequest changeRequest =
                Requests.newModifyRequest("uid=user.2, o=test").addControl(
                        PreReadRequestControl.newControl(true, "mail")).addModification(
                        ModificationType.ADD, "mail", "modified@example.com");

        final Connection connection = Adapters.newRootConnectionFactory().getConnection();
        final Result result = connection.modify(changeRequest);
        assertThat(result.getDiagnosticMessage()).isEmpty();
        assertThat(result.getControls()).isNotEmpty();
        assertThat(result.getControls().get(0).getOID()).isEqualTo("1.3.6.1.1.13.1"); // pre read control OID.
        assertThat(result.getControls().get(0).isCritical()).isFalse(); // pre read response control.
        assertThat(result.getMatchedDN()).isEmpty();

        PreReadResponseControl control =
                result.getControl(PreReadResponseControl.DECODER, new DecodeOptions());
        // Pre read control sends a copy of the target entry exactly as it was
        // immediately before the processing for that operation - mail attribute doesn't exist.
        Entry unmodifiedEntry = control.getEntry();
        assertThat(unmodifiedEntry.getAttribute("mail")).isNull();

        //Verifies that entry has been correctly modified.
        final SearchResultEntry srEntry =
                connection.searchSingleEntry(Requests.newSearchRequest(
                        "uid=user.2, o=test", SearchScope.BASE_OBJECT, "(uid=user.2)"));
        assertThat(srEntry.getAttribute("mail").firstValueAsString()).isEqualTo(
                "modified@example.com");
    }

    /**
     * Tries to modify the existing entry with the same values but using the
     * permissive modify control.
     *
     * @throws LdapException
     */
    @Test(dataProvider = "rootConnectionFactories")
    public void testAdapterUsePermissiveModifyRequest(final ConnectionFactory factory) throws LdapException {
        final ModifyRequest changeRequest =
                Requests.newModifyRequest("uid=user.2, o=test").addControl(
                        PermissiveModifyRequestControl.newControl(true)).addModification(
                        ModificationType.ADD, "uid", "user.2");

        final Connection connection = factory.getConnection();
        connection.modify(changeRequest);

        // Verifies that entry has been correctly modified.
        final SearchResultEntry srEntry =
                connection.searchSingleEntry(Requests.newSearchRequest(
                        "uid=user.2, o=test", SearchScope.BASE_OBJECT, "(uid=user.2)"));
        assertThat(srEntry.getAttribute("uid").firstValueAsString()).isEqualTo("user.2");
    }

    /**
     * Tries to modify the existing entry with the same values.
     *
     * @throws LdapException
     */
    @Test(dataProvider = "rootConnectionFactories",
            expectedExceptions = ConstraintViolationException.class)
    public void testAdapterModifyRequestFails(final ConnectionFactory factory) throws LdapException {
        final ModifyRequest changeRequest =
                Requests.newModifyRequest("uid=user.2, o=test").addModification(
                        ModificationType.ADD, "uid", "user.2");
        final Connection connection = factory.getConnection();
        connection.modify(changeRequest);
    }

    /**
     * Modifies the DN.
     *
     * @throws LdapException
     */
    @Test(dataProvider = "rootConnectionFactories")
    public void testAdapterModifyDNRequest(final ConnectionFactory factory) throws LdapException {
        final Connection connection = factory.getConnection();

        // Verifies that entry has been correctly modified.
        final SearchResultEntry srEntryExists =
                connection.searchSingleEntry(Requests.newSearchRequest(
                        "uid=user.4, o=test", SearchScope.BASE_OBJECT, "(uid=user.4)"));
        assertThat(srEntryExists).isNotNull();

        // Modifying the DN.
        ModifyDNRequest changeRequest =
                Requests.newModifyDNRequest("uid=user.4,o=test", "uid=user.test")
                        .setDeleteOldRDN(true);

        connection.modifyDN(changeRequest);

        // Checks previous mod.
        final SearchResultEntry srEntry =
                connection.searchSingleEntry(Requests.newSearchRequest(
                        "uid=user.test, o=test", SearchScope.BASE_OBJECT,
                        "(uid=user.test)"));
        assertThat(srEntry).isNotNull();

        // Modify again the DN as previously.
        changeRequest =
                Requests.newModifyDNRequest("uid=user.test,o=test", "uid=user.4")
                        .setDeleteOldRDN(true);
        connection.modifyDN(changeRequest);
    }

    /**
     * Compare request. The comparison returns true.
     *
     * @throws LdapException
     */
    @Test(dataProvider = "rootConnectionFactories")
    public void testAdapterCompareRequestTrue(final ConnectionFactory factory) throws LdapException {
        final CompareRequest compareRequest =
                Requests.newCompareRequest(USER_0_DN_STRING, "uid", "user.0");

        final Connection connection = factory.getConnection();
        final CompareResult result = connection.compare(compareRequest);
        assertThat(result.getResultCode()).isEqualTo(ResultCode.COMPARE_TRUE);
        assertThat(result.getDiagnosticMessage()).isEmpty();
        assertThat(result.getControls()).isEmpty();
        assertThat(result.getMatchedDN()).isEmpty();
    }

    /**
     * Compare request. The comparison returns false.
     *
     * @throws LdapException
     */
    @Test(dataProvider = "rootConnectionFactories")
    public void testAdapterCompareRequestFalse(final ConnectionFactory factory) throws LdapException {
        final CompareRequest compareRequest =
                Requests.newCompareRequest(USER_0_DN_STRING, "uid", "scarter");

        final Connection connection = factory.getConnection();
        final CompareResult result = connection.compare(compareRequest);
        assertThat(result.getResultCode()).isEqualTo(ResultCode.COMPARE_FALSE);
        assertThat(result.getDiagnosticMessage()).isEmpty();
        assertThat(result.getControls()).isEmpty();
        assertThat(result.getMatchedDN()).isEmpty();
    }

    /**
     * Use the Who Am I? extended request.
     *
     * @throws LdapException
     */
    @Test(dataProvider = "rootConnectionFactories")
    public void testAdapterExtendedOperation(final ConnectionFactory factory) throws LdapException {
        final WhoAmIExtendedRequest request = Requests.newWhoAmIExtendedRequest();
        try (final Connection connection = factory.getConnection()) {
            final WhoAmIExtendedResult extResult = connection.extendedRequest(request);
            assertThat(extResult.getAuthorizationID()).isNotEmpty();
        }
    }

    /**
     * Creates an LDAP Connection and performs some basic calls like
     * add/delete/search and compare results with an SDK adapter connection
     * doing the same.
     *
     * @throws LdapException
     * @throws SearchResultReferenceIOException
     */
    @Test
    public void testLDAPConnectionAndAdapterComparison() throws LdapException, SearchResultReferenceIOException {
        // @formatter:off
        final AddRequest addRequest = Requests.newAddRequest(
                "dn: sn=babs,o=test",
                "objectClass: top",
                "objectClass: person",
                "cn: bjensen");
        // @formatter:on

        final SearchRequest searchRequest =
                Requests.newSearchRequest("o=test", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.*)").addControl(
                        MatchedValuesRequestControl.newControl(true, "(uid=user.1)"));

        final DeleteRequest deleteRequest = Requests.newDeleteRequest("sn=babs,o=test");

        // LDAP Connection
        final LDAPConnectionFactory factory =
                new LDAPConnectionFactory("localhost", getServerLdapPort());
        Connection connection = null;
        connection = factory.getConnection();
        connection.bind("cn=Directory Manager", "password".toCharArray());
        assertThat(connection.isValid()).isTrue();

        final Result addResult = connection.add(addRequest);
        assertThat(addResult.getResultCode()).isEqualTo(ResultCode.SUCCESS);

        final ConnectionEntryReader reader = connection.search(searchRequest);

        final Result deleteResult = connection.delete(deleteRequest);
        assertThat(deleteResult.getResultCode()).isEqualTo(ResultCode.SUCCESS);

        // SDK Adapter connection
        final Connection adapterConnection = Adapters.newRootConnectionFactory().getConnection();
        final Result sdkAddResult = adapterConnection.add(addRequest);
        final ConnectionEntryReader sdkReader = adapterConnection.search(searchRequest);
        final Result sdkDeleteResult = adapterConnection.delete(deleteRequest);

        // Compare the results :
        // AddRequest
        assertThat(addResult.getMatchedDN()).isEqualTo(sdkAddResult.getMatchedDN());
        assertThat(addResult.getControls()).isEqualTo(sdkAddResult.getControls());
        assertThat(addResult.getDiagnosticMessage()).isEqualTo(sdkAddResult.getDiagnosticMessage());
        assertThat(addResult.getReferralURIs()).isEqualTo(sdkAddResult.getReferralURIs());

        // DeleteRequest
        assertThat(deleteResult.getMatchedDN()).isEqualTo(sdkDeleteResult.getMatchedDN());
        assertThat(deleteResult.getControls()).isEqualTo(sdkDeleteResult.getControls());
        assertThat(deleteResult.getDiagnosticMessage()).isEqualTo(
                sdkDeleteResult.getDiagnosticMessage());
        assertThat(deleteResult.getReferralURIs()).isEqualTo(sdkDeleteResult.getReferralURIs());

        // ConnectionEntryReader
        while (reader.hasNext()) {
            SearchResultEntry entry = reader.readEntry();
            SearchResultEntry sdkEntry = sdkReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(sdkEntry.getName().toString());
            assertThat(entry.getAttributeCount()).isEqualTo(sdkEntry.getAttributeCount());
            assertThat(entry.getAllAttributes().iterator().next().getAttributeDescription())
                    .isEqualTo(sdkEntry.getAllAttributes().iterator().next().getAttributeDescription());
            assertThat(entry.getControls().size()).isEqualTo(sdkEntry.getControls().size());
        }
    }
}
