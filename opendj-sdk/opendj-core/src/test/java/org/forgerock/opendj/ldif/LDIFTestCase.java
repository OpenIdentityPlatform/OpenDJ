/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.testng.annotations.Test;

import com.forgerock.opendj.ldap.CoreMessages;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;

/**
 * This class tests the LDIF functionality.
 */
@SuppressWarnings("javadoc")
public class LDIFTestCase extends AbstractLDIFTestCase {

    /**
     * Provide a standard entry for the tests below.
     *
     * @return well formed LDIF entry
     */
    public final String[] getStandardEntry() {
        // @formatter:off
        return new String[] {
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "postalAddress: Aaccf Amar$01251 Chestnut Street$Panama City, DE  50369",
            "postalCode: 50369",
            "uid: user.0",
            "description: This is the description for Aaccf Amar.",
            "userPassword: {SSHA}hpbT8dLi8xgYy2kl4aP6QKGzsFdhESWpPmDTEw==",
            "employeeNumber: 0",
            "initials: ASA",
            "givenName: Aaccf",
            "pager: +1 779 041 6341",
            "mobile: +1 010 154 3228",
            "cn: Aaccf Amar",
            "telephoneNumber: +1 685 622 6202",
            "sn: Amar",
            "street: 01251 Chestnut Street",
            "homePhone: +1 225 216 5900",
            "mail: user.0@maildomain.net",
            "l: Panama City", "st: DE",
            "pwdChangedTime: 20120903142126.219Z",
            "entryDN: uid=user.0,ou=people,dc=example,dc=org",
            "entryUUID: ad55a34a-763f-358f-93f9-da86f9ecd9e4",
            "modifyTimestamp: 20120903142126Z",
            "modifiersName: cn=Internal Client,cn=Root DNs,cn=config"
        };
        // @formatter:on
    }

    /**
     * Number of attributes of the standard entry.
     */
    public final int nbStandardEntryAttributes = new LinkedHashMapEntry(getStandardEntry())
            .getAttributeCount();

    /**
     * Testing LDIF Search with match.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifSearchWithMatch() throws Exception {
        final EntryReader reader = new LDIFEntryReader(getStandardEntry());
        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)");

        final EntryReader resultReader = LDIF.search(reader, sr);
        final Entry entry = resultReader.readEntry();

        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);

        reader.close();
        resultReader.close();
    }

    /**
     * Testing LDIF Search with no match.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifSearchWithNoMatch() throws Exception {
        final EntryReader reader = new LDIFEntryReader(getStandardEntry());
        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=org", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)");
        final EntryReader resultReader = LDIF.search(reader, sr);
        // No result found in the reader.
        assertThat(resultReader.hasNext()).isFalse();
        resultReader.close();
    }

    /**
     * LDIF Search with null parameters throw a NullPointerException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifSearchDoesntAllowNull() throws Exception {

        LDIF.search(null, null);
    }

    /**
     * LDIF Search doesn't allow a null search request.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifSearchDoesntAllowNullSearchRequest() throws Exception {

        final EntryReader reader = new LDIFEntryReader(getStandardEntry());
        try {
            LDIF.search(reader, null);
        } finally {
            reader.close();
        }
    }

    /**
     * LDIF Search with null reader is allowed but throws a null pointer
     * exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifSearchAllowsNullReader() throws Exception {

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)");

        final EntryReader resultReader = LDIF.search(null, sr);
        resultReader.readEntry();
        resultReader.close();
    }

    /**
     * Tests the search request using a schema and no specifying attribute
     * description.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifSearchWithSchemaMatchNoAttributeDescription() throws Exception {
        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "uid=*");

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getEmptySchema());
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(2);
        assertThat(entry.getAttribute("uid")).isNotNull();
        assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
        assertThat(entry.getAttribute("uid").firstValueAsString()).isNotNull();

        resultReader.close();
    }

    /**
     * Tests the search request using a schema and no specifying attribute
     * description. TypesOnly = true.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifSearchWithSchemaMatchNoAttributeDescriptionTypeOnly()
            throws Exception {
        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "uid=*")
                        .setTypesOnly(true);

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getEmptySchema());
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(2);
        assertThat(entry.getAttribute("uid")).isNotNull();
        assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
        assertThat(entry.getAttribute("uid")).isEmpty();

        resultReader.close();
    }

    /**
     * LDIF search with schema use : all attributes.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifSearchWithSchemaMatchFullAttributes() throws Exception {

        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)", "*");

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getEmptySchema());
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(2);
        assertThat(entry.getAttribute("uid")).isNotNull();
        assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
        assertThat(entry.getAttribute("uid").firstValueAsString()).isEqualTo("user.0");

        resultReader.close();
    }

    /**
     * LDIF Search with schema use : all attributes. Types only.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public final void testLdifSearchWithSchemaMatchFullAttributesTypeOnly() throws Exception {

        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)", "*").setTypesOnly(true);

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getEmptySchema());
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(2);
        assertThat(entry.getAttribute("objectClass")).isNotNull();
        assertThat(entry.getAttribute("uid")).isNotNull();
        assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
        assertThat(entry.getAttribute("uid")).isEmpty();
        // The following assert throws an exception because it contains only
        // attribute descriptions (and not values) are to be returned.
        assertThat(entry.getAttribute("uid").firstValueAsString()).isNull();

        resultReader.close();
    }

    /**
     * Testing the new search request with the + operator == operational
     * attributes only.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifSearchWithSchemaMatchOnlyOperationalAttributes() throws Exception {

        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0",
            "entryDN: uid=user.0,ou=People,dc=example,dc=com",
            "entryUUID: ad55a34a-763f-358f-93f9-da86f9ecd9e4",
            "modifyTimestamp: 20120903142126Z",
            "modifiersName: cn=Internal Client,cn=Root DNs,cn=config"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)", "+");

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getCoreSchema());
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(entry.getAttribute("entryDN").firstValueAsString()).isEqualTo(
                "uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttribute("entryUUID")).isNotEmpty();
        assertThat(entry.getAttribute("modifyTimestamp")).isNotEmpty();
        assertThat(entry.getAttribute("modifiersName")).isNotEmpty();

        resultReader.close();
    }

    /**
     * Testing the new search request with the + operator == operational
     * attributes only. Combined here with the TypeOnly = true.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifSearchWithSchemaMatchOnlyOperationalAttributesTypeOnly()
            throws Exception {

        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0",
            "entryDN: uid=user.0,ou=People,dc=example,dc=com",
            "entryUUID: ad55a34a-763f-358f-93f9-da86f9ecd9e4",
            "modifyTimestamp: 20120903142126Z",
            "modifiersName: cn=Internal Client,cn=Root DNs,cn=config"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)", "+").setTypesOnly(true);

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getCoreSchema());
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(entry.getAttribute("entryDN")).isEmpty();
        assertThat(entry.getAttribute("entryUUID")).isEmpty();
        assertThat(entry.getAttribute("modifyTimestamp")).isEmpty();
        assertThat(entry.getAttribute("modifiersName")).isEmpty();

        resultReader.close();
    }

    /**
     * LDIF search with schema use filter on uid attribute.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifSearchWithSchemaMatchSpecifiedAttribute() throws Exception {

        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)", "uid");

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getEmptySchema());
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(1);

        resultReader.close();
    }

    /**
     * LDIF search with schema use filter on uid attribute.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public final void testLdifSearchWithSchemaMatchSpecifiedAttributeTypeOnly() throws Exception {

        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)", "uid").setTypesOnly(true);

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getEmptySchema());
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(1);
        assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
        assertThat(entry.getAttribute("uid")).isEmpty();
        // The following assert throws an exception because no values contained in, only type.
        assertThat(entry.getAttribute("uid").firstValueAsString()).isNull();
        resultReader.close();
    }

    /**
     * LDIF search with schema use with filter not contained in this entry.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifSearchWithSchemaNoMatchSpecifiedAttribute() throws Exception {

        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)", "email");

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getEmptySchema());
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(0);

        resultReader.close();
    }

    /**
     * The attribute description contains an internal white space : must throw
     * an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public final void testLdifSearchWithSchemaThrowsException() throws Exception {

        // @formatter:off
        final EntryReader reader = new LDIFEntryReader(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "uid: user.0"
        );
        // @formatter:on

        final SearchRequest sr =
                Requests.newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                        "(uid=user.0)", "wro ng"); // wrong syntax filter

        final EntryReader resultReader = LDIF.search(reader, sr, Schema.getEmptySchema());
        resultReader.readEntry();
        resultReader.close();
    }

    /**
     * Tries to search for an entry in ldif using ignore case exact matching rule and lower case search filter.
     *
     * @throws IOException
     */
    @Test
    public final void testSearchForEntryInLDIFUsingIgnoreMatchingRuleSucceedWithLowerCaseFilter() throws IOException {
        Schema schema = newSchemaBuilder(getCaseIgnoreMatchingRule(), getDirectoryStringSyntax());

        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
                "dn: cn=JPEG,cn=Syntaxes,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-attribute-syntax",
                "cn: JPEG",
                "ds-cfg-java-class: org.opends.server.schema.JPEGSyntax",
                "ds-cfg-enabled: true"
        ).setSchema(schema);
        // @formatter:on

        final SearchRequest sr = Requests.newSearchRequest("cn=config", SearchScope.WHOLE_SUBTREE,
                "(ds-cfg-java-class=org.opends.server.schema.jpegsyntax)", "*");
        final EntryReader resultReader = LDIF.search(input, sr, schema);
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("cn=JPEG,cn=Syntaxes,cn=config");
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(entry.getAttribute("ds-cfg-java-class").firstValueAsString()).isEqualTo(
                "org.opends.server.schema.JPEGSyntax");
        input.close();
    }

    /**
     * Tries to search for an entry in ldif using case exact matching rule and lower case search filter.
     *
     * @throws IOException
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public final void testSearchForEntryInLDIFUsingExactMatchingRuleFailsWithLowerCaseFilter() throws IOException {
        Schema schema = newSchemaBuilder(getCaseExactMatchingRule(), getDirectoryStringSyntax());

        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
                "dn: cn=JPEG,cn=Syntaxes,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-attribute-syntax",
                "cn: JPEG",
                "ds-cfg-java-class: org.opends.server.schema.JPEGSyntax",
                "ds-cfg-enabled: true"
        ).setSchema(schema);
        // @formatter:on

        final SearchRequest sr = Requests.newSearchRequest("cn=config", SearchScope.WHOLE_SUBTREE,
                "(ds-cfg-java-class=org.opends.server.schema.jpegsyntax)", "*");
        final EntryReader resultReader = LDIF.search(input, sr, schema);
        resultReader.readEntry();
    }

    /**
     * Tries to search for an entry in ldif using case exact matching rule and the right search filter.
     *
     * @throws IOException
     */
    @Test
    public final void testSearchForEntryInLDIFUsingExactMatchingRuleSucceedWithRightFilter() throws IOException {
        Schema schema = newSchemaBuilder(getCaseExactMatchingRule(), getDirectoryStringSyntax());

        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
                "dn: cn=JPEG,cn=Syntaxes,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-attribute-syntax",
                "cn: JPEG",
                "ds-cfg-java-class: org.opends.server.schema.JPEGSyntax",
                "ds-cfg-enabled: true"
        ).setSchema(schema);
        // @formatter:on

        final SearchRequest sr = Requests.newSearchRequest("cn=config", SearchScope.WHOLE_SUBTREE,
                "(ds-cfg-java-class=org.opends.server.schema.JPEGSyntax)", "*");
        final EntryReader resultReader = LDIF.search(input, sr, schema);
        final Entry entry = resultReader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("cn=JPEG,cn=Syntaxes,cn=config");
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(entry.getAttribute("ds-cfg-java-class").firstValueAsString()).isEqualTo(
                "org.opends.server.schema.JPEGSyntax");
        input.close();
    }

    private Schema newSchemaBuilder(MatchingRule defaultMatchingRule, Syntax defaultSyntax) {
        return new SchemaBuilder(Schema.getCoreSchema())
            .setOption(DEFAULT_MATCHING_RULE_OID, defaultMatchingRule.getOID())
            .setOption(DEFAULT_SYNTAX_OID, defaultSyntax.getOID())
            .toSchema().asNonStrictSchema();
    }

    /**
     * Verifying LDIF collection reader.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifNewEntryCollectionReader() throws Exception {

        // @formatter:off
        Entry e = new LinkedHashMapEntry(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson"
        );
        Entry e1 = new LinkedHashMapEntry("dn: uid=user.1,ou=People,dc=example,dc=com", "objectClass: person");
        Entry e2 = new LinkedHashMapEntry("dn: uid=user.2,ou=People,dc=example,dc=com", "objectClass: person");
        // @formatter:on

        Collection<Entry> collection = new ArrayList<>();
        collection.add(e);
        collection.add(e1);
        collection.add(e2);

        final EntryReader resultReader = LDIF.newEntryCollectionReader(collection);
        Entry entry = null;
        int cCount = 0;
        while (resultReader.hasNext()) {
            entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isNotNull();
            assertThat(entry.getAttributeCount()).isGreaterThanOrEqualTo(1);
            cCount++;
        }
        assertThat(cCount).isEqualTo(3);
        resultReader.close();
    }

    /**
     * The LDIF entry collection reader allows a null parameter.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifNewEntryCollectionDoesntAllowNull() throws Exception {

        EntryReader resultReader = null;
        try {
            resultReader = LDIF.newEntryCollectionReader(null);
            resultReader.readEntry();
        } finally {
            resultReader.close();
        }
    }

    /**
     * Tests the LDIF entry iterator reader.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifNewEntryIteratorReader() throws Exception {

        // @formatter:off
        final Entry e = new LinkedHashMapEntry(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson"
        );
        final Entry e1 = new LinkedHashMapEntry(
            "dn: uid=user.1,ou=People,dc=example,dc=com",
            "objectClass: person"
        );
        // @formatter:on

        final SortedMap<DN, Entry> sourceEntries = new TreeMap<>();
        sourceEntries.put(DN.valueOf("uid=user.0,ou=People,dc=example,dc=com"), e);
        sourceEntries.put(DN.valueOf("uid=user.1,ou=People,dc=example,dc=com"), e1);
        final Iterator<Entry> sourceIterator = sourceEntries.values().iterator();

        final EntryReader resultReader = LDIF.newEntryIteratorReader(sourceIterator);
        int cCount = 0;
        while (resultReader.hasNext()) {
            final Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isNotNull();
            assertThat(entry.getName().toString()).contains("ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isGreaterThanOrEqualTo(1);
            cCount++;
        }
        assertThat(cCount).isEqualTo(2);
        resultReader.close();
    }

    /**
     * LDIF entry iterator reader doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifNewEntryIteratorReaderDoesntAllowsNull() throws Exception {

        EntryReader resultReader = null;
        try {
            resultReader = LDIF.newEntryIteratorReader(null);
            resultReader.readEntry();
        } finally {
            resultReader.close();
        }
    }

    /**
     * Tests the LDIF copy over a change record writer from an LDIF change record reader.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifCopyToChangeRecord() throws Exception {

        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "# Entry to delete",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on
        final java.util.List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        try {
            LDIF.copyTo(reader, writer);
            // Comment is skipped.
            assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
            assertThat(actual.get(1)).isEqualTo("changetype: delete");
            assertThat(actual.get(2)).isEqualTo("");
            assertThat(actual.size()).isEqualTo(3); // 2 lines + 1 empty
        } finally {
            reader.close();
            writer.close();
        }
    }

    /**
     * The LDIF copy doesn't allow null parameters.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifCopyToChangeRecordDoesntAllowNull() throws Exception {

        LDIF.copyTo((ChangeRecordReader) null, (ChangeRecordWriter) null);
    }

    /**
     * LDIF copy doesn't allow a null writer.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifCopyToChangeRecordDoesntAllowNullWriter() throws Exception {

        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "# Entry to delete",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on
        LDIF.copyTo(reader, null);
    }

    /**
     * LDIF copy doesn't allow a null reader.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifCopyToChangeRecordDoesntAllowNullReader() throws Exception {
        final java.util.List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        LDIF.copyTo(null, writer);
    }

    /**
     * Tests the LDIF copy from an entry reader to a entry writer.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifCopyToEntryWriter() throws Exception {

        // @formatter:off
        final LDIFEntryReader reader = new LDIFEntryReader(
            "# Entry to delete",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on

        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        try {
            LDIF.copyTo(reader, writer);
            // Comment is skipped.
            assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
            assertThat(actual.get(1)).isEqualTo("changetype: delete");
            assertThat(actual.get(2)).isEqualTo("");
            assertThat(actual.size()).isEqualTo(3); // 2 lines + 1 empty
        } finally {
            reader.close();
            writer.close();
        }
    }

    /**
     * LDIF copyTo - EntryWriter doesn't allow a null writer.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifCopyToEntryWriterDoesntAllowNullWriter() throws Exception {

        // @formatter:off
        final LDIFEntryReader reader = new LDIFEntryReader(
            "# Entry to delete",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on
        LDIF.copyTo(reader, null);
    }

    /**
     * LDIF copyTo - EntryWriter doesn't allow a null reader.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifCopyToEntryWriterDoesntAllowNullReader() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        LDIF.copyTo(null, writer);
    }

    /**
     * Testing the diff function. The following example is extracted from the admin guide.
     *
     * @see <a
     *      href=http://opendj.forgerock.org/doc/admin-guide/index.html#ldif-diff
     *      -1 result”>Admin Guide</a>
     * @throws Exception
     */
    @Test
    public final void testLdifDiffEntriesModsOnSameDN() throws Exception {

        // @formatter:off
        final LDIFEntryReader source = new LDIFEntryReader(
            "dn: uid=newuser,ou=People,dc=example,dc=com",
            "uid: newuser",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "objectClass: top",
            "cn: New User",
            "sn: User",
            "ou: People",
            "mail: newuser@example.com",
            "userPassword: changeme" // diff here
        );

        final LDIFEntryReader target = new LDIFEntryReader(
            "dn: uid=newuser,ou=People,dc=example,dc=com",
            "uid: newuser",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "objectClass: top",
            "cn: New User",
            "sn: User",
            "ou: People",
            "mail: newuser@example.com",
            "userPassword: secret12", // diff here
            "description: A new description." // diff here
        );
        // @formatter:on

        final ChangeRecordReader reader = LDIF.diff(source, target);
        final ChangeRecord cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=newuser,ou=People,dc=example,dc=com");
        assertThat(cr).isInstanceOf(ModifyRequest.class);

        // @formatter:off
        /* Expected : 2 add / 1 delete - output :
         * dn: uid=newuser,ou=People,dc=example,dc=com
         * changetype: modify
         * add: userPassword
         * userPassword: secret12
         * -
         * delete: userPassword
         * userPassword: changeme
         * -
         * add: description
         * description: A new description.
         */
         // @formatter:on

        assertThat(((ModifyRequest) cr).getModifications().size()).isEqualTo(3);
        for (Modification mod : ((ModifyRequest) cr).getModifications()) {
            final String attrDescription = mod.getAttribute().getAttributeDescription().toString();
            final String firstValueAsString = mod.getAttribute().firstValueAsString();
            if (mod.getModificationType() == ModificationType.ADD) {
                assertThat(attrDescription).isIn("description", "userPassword");
                assertThat(firstValueAsString).isIn("A new description.", "secret12");
            } else if (mod.getModificationType() == ModificationType.DELETE) {
                assertThat(attrDescription).isEqualTo("userPassword");
                assertThat(firstValueAsString).isEqualTo("changeme");
            }
        }
        reader.close();
        target.close();
    }

    /**
     * Testing the diff : the entry is present in source but not in the target.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifDiffEntriesEntryInSourceNotInTarget() throws Exception {

        // @formatter:off
        final LDIFEntryReader source = new LDIFEntryReader(
            "dn: uid=newuser,ou=People,dc=example,dc=com",
            "cn: New User"
        );

        final LDIFEntryReader target = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "cn: Samantha Carter"
        );
        // @formatter:on

        final ChangeRecordReader reader = LDIF.diff(source, target);

        // @formatter:off
        /* output is :
          dn: uid=newuser,ou=People,dc=example,dc=com
          changetype: delete

          dn: uid=scarter,ou=People,dc=example,dc=com
          changetype: add
          cn: Samantha Carter
         */
        // @formatter:on

        ChangeRecord cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=newuser,ou=People,dc=example,dc=com");
        assertThat(cr).isInstanceOf(DeleteRequest.class);

        cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(cr).isInstanceOf(AddRequest.class);
        assertThat(((AddRequest) cr).getAttributeCount()).isEqualTo(1);
        assertThat(((AddRequest) cr).getAttribute("cn").firstValueAsString()).isEqualTo(
                "Samantha Carter");
        reader.close();
        target.close();
    }

    /**
     * Testing the diff : entry is in the target not in the source. The rdn of the
     * following example is completed by ou=People.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifDiffEntriesEntryInTargetNotInSource() throws Exception {

        // @formatter:off
        final LDIFEntryReader source = new LDIFEntryReader(
            "dn: uid=scarter,dc=example,dc=com",
            "cn: Samantha Carter"
        );

        final LDIFEntryReader target = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "cn: Samantha Carter"
        );
        // @formatter:on

        final ChangeRecordReader reader = LDIF.diff(source, target);

        // @formatter:off
        /* output is :
        dn: uid=scarter,ou=People,dc=example,dc=com
        changetype: add
        cn: Samantha Carter

        dn: uid=scarter,dc=example,dc=com
        changetype: delete
         */
        // @formatter:on

        ChangeRecord cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(cr).isInstanceOf(AddRequest.class);
        assertThat(((AddRequest) cr).getAttributeCount()).isEqualTo(1);
        assertThat(((AddRequest) cr).getAttribute("cn").firstValueAsString()).isEqualTo(
                "Samantha Carter");

        cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,dc=example,dc=com");
        assertThat(cr).isInstanceOf(DeleteRequest.class);

        reader.close();
        source.close();
        target.close();
    }

    /**
     * Differences between two same entries : no modifications expected.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifDiffEntriesNoDiff() throws Exception {

        // @formatter:off
        final LDIFEntryReader source = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "cn: Samantha Carter"
        );

        final LDIFEntryReader target = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "cn: Samantha Carter"
        );
        // @formatter:on

        final ChangeRecordReader reader = LDIF.diff(source, target);
        assertThat(reader.hasNext()).isTrue();
        final ChangeRecord cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(cr instanceof ModifyRequest);
        assertThat(((ModifyRequest) cr).getModifications()).isEmpty();
        reader.close();
        target.close();
    }

    /**
     * Differences between two short LDIF examples.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifDiffEntriesShortExamples() throws Exception {

        // @formatter:off
        final LDIFEntryReader source = new LDIFEntryReader(
                "dn: uid=user.0,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Aaccf",
                "sn: Amar",
                "cn: Aaccf Amar",
                "initials: APA",
                "employeeNumber: 0",
                "uid: user.0",
                "mail: user.0@example.com",
                "description: This is the description for Aaccf Amar.",
                "",
                "dn: uid=user.1,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Aaren",
                "sn: Atp",
                "cn: Aaren Atp",
                "initials: AFA",
                "employeeNumber: 1",
                "uid: user.1",
                "mail: user.1@example.com",
                "description: This is the description for Aaren Atp.",
                "",
                "dn: uid=user.2,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Aarika",
                "sn: Atpco",
                "cn: Aarika Atpco",
                "initials: AVA",
                "employeeNumber: 2",
                "uid: user.2",
                "mail: user.2@example.com",
                "description: This is the description for Aarika Atpco.",
                "",
                "dn: uid=user.3,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Aaron",
                "sn: Atrc",
                "cn: Aaron Atrc",
                "initials: ATA",
                "employeeNumber: 3",
                "uid: user.3",
                "mail: user.3@example.com",
                "description: This is the description for Aaron Atrc.",
                "",
                "dn: uid=user.4,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Aartjan",
                "sn: Aalders",
                "cn: Aartjan Aalders",
                "initials: AAA",
                "employeeNumber: 4",
                "uid: user.4",
                "mail: user.4@example.com",
                "description: This is the description for Aartjan Aalders."
        );

        final LDIFEntryReader target = new LDIFEntryReader(
                "dn: uid=user.0,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Amar", // diff
                "sn: Amar",
                "cn: Aaccf Amar",
                "initials: APA",
                "employeeNumber: 55", // diff
                "uid: user.0",
                "mail: user.0@example.com",
                "description: This is the description for Aaccf Amar.",
                "work-phone: 650/506-0666", // diff
                "",
                "dn: uid=user.1,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Aaren",
                "sn: Atp",
                "cn: Aaren Atp",
                "initials: AFA",
                "employeeNumber: 1",
                "uid: user.1",
                "mail: aaren@example.com", // diff
                "description: This is the description for Aaren Atp.",
                "",
                "dn: uid=user.2,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Aarika",
                "sn: Atpco",
                "cn: Aarika Atpco",
                "initials: AVA",
                "employeeNumber: 2",
                "uid: user.2",
                "mail: user.2@example.com", // diff (delete description)
                "",
                "dn: uid=user.3,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Aaron",
                "sn: Atrc",
                "cn: Aaron Atrc",
                "initials: ATA",
                "employeeNumber: 3",
                "uid: user.999", // diff
                "mail: user.999@example.com", // diff
                "description: This is the description for Aaron Atrc.",
                "",
                "dn: uid=user.4,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson",
                "givenName: Aartjan",
                "sn: Aalders",
                "cn: Aartjan Aalders",
                "initials: AAA",
                "employeeNumber: 4",
                "uid: user.4",
                "mail: user.4@example.com",
                "description: This is the description for Aartjan Aalders."
        );
        // @formatter:on

        final ChangeRecordReader reader = LDIF.diff(source, target);
        assertThat(reader.hasNext()).isTrue();
        ChangeRecord cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(cr instanceof ModifyRequest);
        assertThat(((ModifyRequest) cr).getModifications()).isNotEmpty();
        // 1st entry : 2 add/delete + 1 add(work-phone)
        assertThat(((ModifyRequest) cr).getModifications().size()).isEqualTo(5);
        // 2nd entry : 1 add/delete
        cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=user.1,ou=People,dc=example,dc=com");
        assertThat(cr instanceof ModifyRequest);
        assertThat(((ModifyRequest) cr).getModifications().size()).isEqualTo(2);
        // 3rd entry : 1 delete
        cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=user.2,ou=People,dc=example,dc=com");
        assertThat(cr instanceof ModifyRequest);
        assertThat(((ModifyRequest) cr).getModifications().size()).isEqualTo(1);
        assertThat(((ModifyRequest) cr).getModifications().get(0).getModificationType().toString())
                .isEqualTo("delete");
        assertThat(
                ((ModifyRequest) cr).getModifications().get(0).getAttribute()
                        .getAttributeDescriptionAsString()).isEqualTo("description");
        // 4th entry : 2 add/delete
        cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=user.3,ou=People,dc=example,dc=com");
        assertThat(cr instanceof ModifyRequest);
        assertThat(((ModifyRequest) cr).getModifications().size()).isEqualTo(4);
        // 5th entry : 0 modifications
        cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=user.4,ou=People,dc=example,dc=com");
        assertThat(cr instanceof ModifyRequest);
        assertThat(((ModifyRequest) cr).getModifications().size()).isEqualTo(0);
        assertThat(reader.hasNext()).isFalse();

        reader.close();
        target.close();
    }

    /**
     * Differences between two same entries : no modifications expected.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifDiffEntriesNoDiffBase64() throws Exception {

        // @formatter:off
        final LDIFEntryReader source = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "cn: Samantha Carter"
        );

        final LDIFEntryReader target = new LDIFEntryReader(
            "dn:: dWlkPXNjYXJ0ZXIsb3U9UGVvcGxlLGRjPWV4YW1wbGUsZGM9Y29t",
            "cn: Samantha Carter"
        );
        // @formatter:on

        final ChangeRecordReader reader = LDIF.diff(source, target);
        assertThat(reader.hasNext()).isTrue();
        final ChangeRecord cr = reader.readChangeRecord();
        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(cr instanceof ModifyRequest);
        assertThat(((ModifyRequest) cr).getModifications()).isEmpty();
        reader.close();
        target.close();
    }

    /**
     * The diff function doesn't allow malformed ldif. Exception expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public final void testLdifDiffEntriesNoDiffMalformedTarget() throws Exception {

        final LDIFEntryReader source =
                new LDIFEntryReader("dn: uid=scarter,ou=People,dc=example,dc=com");

        final LDIFEntryReader target = new LDIFEntryReader("dn: wrongRDN");

        LDIF.diff(source, target);
    }

    /**
     * LDIF diff - EntryReader/Writer doesn't allow null. Exception expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifDiffEntriesDoesntAllowNull() throws Exception {
        LDIF.diff(null, null);
    }

    /**
     * Create a patch without any differences with the original.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchAddNoDiff() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: add",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(3); // objectclass - sn - mail
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * The patch adds successfully an attribute 'manager' to the entry.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchAddDiff() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: add",
            "add: manager",
            "manager: uid=joneill,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5); // objectclass - sn - mail
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * The patch adds two new entries to the original.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchAddDiffNewEntry() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=joneill,ou=People,dc=example,dc=com",
            "changetype: add",
            "add: manager",
            "manager: uid=hamond,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "",
            "dn: uid=djackson,ou=People,dc=example,dc=com",
            "changetype: add",
            "add: manager",
            "manager: uid=joneill,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString())
                .isEqualTo("uid=djackson,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=joneill,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(3);
        reader.close();
    }

    /**
     * Tries to modify a nonexistent entry. The patch throws an error via the
     * listener which is in RejectedChangeRecordListener.OVERWRITE.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchAddModifyNonExistantEntryDoNothing() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: cn=Lisa Jangles,ou=People,dc=example,dc=com",
            "changetype: modify",
            "add: telephonenumber",
            "telephonenumber: (408) 555-2468"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);
        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(3);
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Testing to delete an entry.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchDeleteEntry() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Testing to delete in entry.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchDeleteEntryAmongSeveral() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=user.1,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aarika",
            "sn: Atpco",
            "cn: Aarika Atpco",
            "uid: user.1",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Eniko",
            "sn: Eniko",
            "cn: Eniko Atpco",
            "uid: user.2",
            "",
            "dn: uid=user.3,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aaron",
            "sn: Atrc",
            "cn: Aaron Atrc",
            "uid: user.3"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.1,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.3,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Testing to delete attributes in a selected entry.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchDeleteAttributesEntry() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Carter",
            "sn: Sam",
            "cn: Sam Carter",
            "uid: scarter",
            "mail: user.1@mail.com",
            "postalAdress: 42 Shepherd Street",
            "work-phone: 650/506-7000",
            "work-phone: 650/506-0666",
            "home-fax: 650-7001"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "delete: work-phone",
            "work-phone: 650/506-0666",
            "-",
            "delete: home-fax"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);
        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(8);
        assertThat(entry.getAttribute("work-phone").firstValueAsString()).isEqualTo("650/506-7000");
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Modifying an entry : add
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchModifyEntry() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "add: work-phone",
            "work-phone: 650/506-7000"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * The patch attempts to modify the dn adding uppercase.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchModifyDNEntryUppercaseUid() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "uid: scarter",
            "",
            "dn: uid=djackson,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "uid: djackson"

        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: uid=Scarter",
            "deleteoldrdn: 1"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        // does not work with a single entry && ...
        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString())
                .isEqualTo("uid=djackson,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=Scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4); //The patch create uid attribute on the selected entry.
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Attempts to modify the entry adding upper case in cn.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchModifyDNEntryUpperCaseDnNameSurname() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: cn=sam carter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "uid: scarter",
            "",
            "dn: uid=djackson,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "uid: djackson"

        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: cn=sam carter,ou=People,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: cn=Sam Carter",
            "deleteoldrdn: 1"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        // does not work with a single entry && ...
        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo(
                "cn=Sam Carter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString())
                .isEqualTo("uid=djackson,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4); //The patch create uid attribute on the selected entry.
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * The patch attempts to modify a rdn of a specific entry.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchModifyDNEntry() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "uid: scarter",
            "",
            "dn: uid=djackson,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "uid: djackson"

        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: uid=Susan Jacobs",
            "deleteoldrdn: 1"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        // does not work with a single entry && ...
        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString())
                .isEqualTo("uid=djackson,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo(
                "uid=Susan Jacobs,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4); //The patch create uid attribute on the selected entry.
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    @Test
    public final void testLdifPatchModifyDnEntry2() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=user.1,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aarika",
            "sn: Atpco",
            "cn: Aarika Atpco",
            "uid: user.1",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Eniko",
            "sn: Eniko",
            "cn: Eniko Atpco",
            "uid: user.2",
            "",
            "dn: uid=user.3,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aaron",
            "sn: Atrc",
            "cn: Aaron Atrc",
            "uid: user.3"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: uid=user.22",
            "deleteoldrdn: 1"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.1,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.22,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.3,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Renames a branch : from ou=People,dc=example,dc=com to ou=Human
     * Resources,dc=example,dc=com.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchModifyDnEntryBranch() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: organizationalunit",
            "ou: People",
            "",
            "dn: uid=user.1,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aarika",
            "sn: Atpco",
            "cn: Aarika Atpco",
            "uid: user.1",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Eniko",
            "sn: Eniko",
            "cn: Eniko Atpco",
            "uid: user.2",
            "",
            "dn: uid=user.3,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aaron",
            "sn: Atrc",
            "cn: Aaron Atrc",
            "uid: user.3",
            "",
            "dn: uid=user.4,ou=People,dc=example,dc=org",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Allan",
            "sn: Zorg",
            "cn: Allan Zorg",
            "uid: user.4"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: ou=People,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: ou=Human Resources,dc=example,dc=com",
            "deleteoldrdn: 1"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("ou=Human Resources,dc=example,dc=com");
        assertThat(entry.getAllAttributes("ou").iterator().next().firstValueAsString()).isEqualTo(
                "Human Resources");
        assertThat(entry.getAttributeCount()).isEqualTo(2);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo(
                "uid=user.1,ou=Human Resources,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo(
                "uid=user.2,ou=Human Resources,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo(
                "uid=user.3,ou=Human Resources,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.4,ou=People,dc=example,dc=org");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Renames a branch : from ou=People,dc=example,dc=com to ou=Human
     * Resources,dc=example,dc=com. In this example deleteoldrdn is set to 0.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchModifyDnEntryBranchKeepsOldRdn() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: organizationalunit",
            "ou: People",
            "",
            "dn: uid=user.1,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aarika",
            "sn: Atpco",
            "cn: Aarika Atpco",
            "uid: user.1",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Eniko",
            "sn: Eniko",
            "cn: Eniko Atpco",
            "uid: user.2",
            "",
            "dn: uid=user.3,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aaron",
            "sn: Atrc",
            "cn: Aaron Atrc",
            "uid: user.3",
            "",
            "dn: uid=user.4,ou=People,dc=example,dc=org",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Allan",
            "sn: Zorg",
            "cn: Allan Zorg",
            "uid: user.4"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: ou=People,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: ou=Human Resources,dc=example,dc=com",
            "deleteoldrdn: 0"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("ou=Human Resources,dc=example,dc=com");
        assertThat(entry.getAllAttributes("ou").iterator().next().firstValueAsString()).isEqualTo(
                "People");
        assertThat(entry.getAttributeCount()).isEqualTo(2);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo(
                "uid=user.1,ou=Human Resources,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo(
                "uid=user.2,ou=Human Resources,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo(
                "uid=user.3,ou=Human Resources,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.4,ou=People,dc=example,dc=org");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Moves an entry.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchModifyDnEntryNewSuperior() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=user.1,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aarika",
            "sn: Atpco",
            "cn: Aarika Atpco",
            "uid: user.1",
            "mail: user.1@mail.com",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Eniko",
            "sn: Eniko",
            "cn: Eniko Atpco",
            "uid: user.2"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "# moves the entry from ou=People, dc=example,dc=com to Marketing",
            "dn: uid=user.1,ou=People,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: uid=user.1",
            "deleteoldrdn: 1",
            "newsuperior: ou=Marketing,dc=example,dc=com"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo(
                "uid=user.1,ou=Marketing,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(6);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.2,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Applies simple patch to replace/add data to the input.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchAddReplace() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "replace:sn",
            "sn: scarter",
            "-",
            "add: manager",
            "manager: uid=joneill,ou=People,dc=example,dc=com"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4); // objectclass - sn - mail - manager
        assertThat(entry.getAttribute("manager").firstValueAsString()).isEqualTo(
                "uid=joneill,ou=People,dc=example,dc=com");
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Replaces / adds postalAdress.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchAddReplaceLanguageTagExample() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "postalAdress;lang-en: Shepherd Street"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "replace: postalAdress;lang-fr",
            "postalAdress;lang-fr: 355 avenue Leon Blum",
            "-",
            "replace: postalAdress;lang-en",
            "postalAdress;lang-en: 42 Shepherd Street"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(5); // objectclass - sn - mail - manager - postalAdress
        assertThat(entry.getAttribute("postalAdress;lang-fr").firstValueAsString()).isEqualTo(
                "355 avenue Leon Blum");
        assertThat(entry.getAttribute("postalAdress;lang-en").firstValueAsString()).isEqualTo(
                "42 Shepherd Street");
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Tests some changes : add/replace/delete...
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchVariousChanges() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "",
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aaccf",
            "sn: Amar",
            "cn: Aaccf Amar",
            "",
            "dn: uid=user.1,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aaren",
            "sn: Atp",
            "cn: Aaren Atp",
            "mail: AarenAtp@mail.org",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aarika",
            "sn: Atpco",
            "cn: Aarika Atpco",
            "description:: ZnVubnkgZGVzY3JpcHRpb24gISA6RA==",
            "mail:: QWFyaWthQXRwY29AbWFpbC5vcmc=",
            "",
            "dn: uid=user.3,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Kadja",
            "sn: Atpcol",
            "cn: Kadja Atpcol"
        );
        // @formatter:on

        final File file = File.createTempFile("sdk", ".png");
        final String url = file.toURI().toURL().toString();

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "replace:sn",
            "sn: scarter",
            "-",
            "add: manager",
            "manager: uid=joneill,ou=People,dc=example,dc=com",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "changetype: modify",
            "replace:description",
            "description:: QWFyaWthIEF0cGNvIGRlc2NyaXB0aW9uIDogbG9yZW0gaXBzdW0uLi4=",
            "-",
            "add: jpegphoto",
            "jpegphoto:< " + url,
            "",
            "dn: uid=user.3,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(entry.getAttribute("manager").firstValueAsString()).isEqualTo(
                "uid=joneill,ou=People,dc=example,dc=com");
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getAttributeCount()).isEqualTo(5);
        assertThat(entry.getName().toString()).isEqualTo("uid=user.1,ou=People,dc=example,dc=com");
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getAttributeCount()).isEqualTo(7);
        assertThat(entry.getName().toString()).isEqualTo("uid=user.2,ou=People,dc=example,dc=com");
        assertThat(entry.getAttribute("mail").firstValueAsString()).isEqualTo(
                "AarikaAtpco@mail.org");
        assertThat(entry.getAttribute("description").firstValueAsString()).isEqualTo(
                "Aarika Atpco description : lorem ipsum...");
        assertThat(entry.getAttribute("jpegphoto")).isNotEmpty();
        assertThat(reader.hasNext()).isFalse();

        file.delete();
        reader.close();
    }

    /**
     * An example to illustrate an LDIFChangeRecordReader containing changes on
     * previous ldif.
     *
     * @throws Exception
     */
    @Test
    public final void testLdifPatchContainingChanges() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "",
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aaccf",
            "sn: Amar",
            "cn: Aaccf Amar",
            "",
            "dn: uid=user.1,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aaren",
            "sn: Atp",
            "cn: Aaren Atp",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Aarika",
            "sn: Atpco",
            "cn: Aarika Atpco"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
                "dn: uid=scarter,ou=People,dc=example,dc=com",
                "changetype: modify",
                "replace:sn",
                "sn: scarter",
                "-",
                "add: manager",
                "manager: uid=joneill,ou=People,dc=example,dc=com",
                "",
                "dn: uid=user.0,ou=People,dc=example,dc=com",
                "changetype: modify",
                "replace:sn",
                "sn: Amarr",
                "-",
                "delete: givenName",
                "",
                "dn: uid=user.1,ou=People,dc=example,dc=com",
                "changetype: modify",
                "replace:givenName",
                "givenName: Aarwen",
                "-",
                "add: manager",
                "manager: uid=joneill,ou=People,dc=example,dc=com",
                "-",
                "add: mail",
                "mail: Aarwen@mail.com",
                "-",
                "add: fax",
                "fax: 555 555-5555",
                "-",
                "add: description",
                "description:: QWFyd2VuIGRlc2NyaXB0aW9uLg=="
        );
        // @formatter:on

        final EntryReader reader = LDIF.patch(input, patch);

        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        // Attr. list : objectclass - sn - mail - manager
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(entry.getAttribute("manager").firstValueAsString()).isEqualTo(
                "uid=joneill,ou=People,dc=example,dc=com");
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        // Attr. list : objectclass - sn - cn
        assertThat(entry.getAttributeCount()).isEqualTo(3);
        assertThat(reader.hasNext()).isTrue();
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.1,ou=People,dc=example,dc=com");
        // Attr. list : objectclass - sn - cn - givenName - manager - mail - fax - description
        assertThat(entry.getAttributeCount()).isEqualTo(8);
        assertThat(entry.getAttribute("description").firstValueAsString()).isEqualTo(
                "Aarwen description.");
        assertThat(reader.hasNext()).isTrue();
        // Last entry, no modification on it.
        entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.2,ou=People,dc=example,dc=com");
        // Attr. list : objectClass - givenname - sn - cn
        assertThat(entry.getAttributeCount()).isEqualTo(4);
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Tries to apply a patch which data are not valid. Exception expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public final void testLdifPatchInvalidChangeRecord() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
                "dn: uid=scarter,ou=People,dc=example,dc=com",
                "changetype: modif\u0000",
                "replace:sn",
                "sn: scarter",
                "-",
                "add: manager\u0000",
                "manager: uid=joneill,ou=People,dc=example,dc=com"
        );
        // @formatter:on
        EntryReader reader = new LDIFEntryReader();
        try {
            reader = LDIF.patch(input, patch);
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * The listener is in FAIL_FAST Mode. On this example,
     * attaching the listener to the patch should throw a decode exception as a
     * wrong DN is used in the modify request. (The entry does not exist).
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public final void testFailFastPatchOnModifyRequestFailsDueToWrongDN() throws Exception {

        final RejectedChangeRecordListener listener = RejectedChangeRecordListener.FAIL_FAST;
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=WRONGUID,ou=People,dc=example,dc=com",
            "changetype: modify",
            "add: work-phone",
            "work-phone: 650/506-7000"
        );
        // @formatter:on
        EntryReader reader = new LDIFEntryReader();
        try {
            reader = LDIF.patch(input, patch, listener);
        } finally {
            reader.close();
        }
    }

    /**
     * The listener is in OVERWRITE Mode. On this example,
     * attaching the listener to the patch should throw a decode exception even
     * if a wrong DN is used in the modify request. (The entry does not exist).
     * No data impacted by the patch, no warning sent.
     *
     * @throws Exception
     */
    @Test
    public final void testOverwritePatchOnModifyRequestSucceedsEvenIfWrongDN() throws Exception {
        final RejectedChangeRecordListener listener = RejectedChangeRecordListener.OVERWRITE;
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=WRONGUID,ou=People,dc=example,dc=com",
            "changetype: modify",
            "add: work-phone",
            "work-phone: 650/506-7000"
        );
        // @formatter:on
        EntryReader reader = new LDIFEntryReader();
        try {
            reader = LDIF.patch(input, patch, listener);
            Entry entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(3);
            assertThat(entry.getAttribute("objectClass").firstValueAsString()).isEqualTo(
                    "person");
            assertThat(entry.getAttribute("sn").firstValueAsString()).isEqualTo(
                    "new user");
            assertThat(entry.getAttribute("mail").firstValueAsString()).isEqualTo(
                    "mail@mailme.org");
            assertThat(reader.hasNext()).isFalse();
            assertThat(entry.getAttribute("work-phone")).isNull();
        } finally {
            reader.close();
        }
    }

    /**
     * The listener is in FAIL_FAST Mode. On this example,
     * attaching the listener to the patch should throw a decode exception as a
     * wrong DN is used in the modify DN request. (The entry does not exist).
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public final void testFailFastPatchOnModifyDNRequestFailsDueToWrongDN() throws Exception {
        final RejectedChangeRecordListener listener = RejectedChangeRecordListener.FAIL_FAST;
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: ou=WRONGscarter, dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: ou=Human Resources,dc=example,dc=com",
            "deleteoldrdn: 1"
        );
        // @formatter:on
        EntryReader reader = new LDIFEntryReader();
        try {
            reader = LDIF.patch(input, patch, listener);
        } finally {
            reader.close();
        }
    }

    /**
     * The listener is in OVERWRITE Mode. On this example,
     * attaching the listener to the patch should not throw a decode exception
     * even if a wrong DN is used in the modify DN request. (Even if the entry
     * does not exist).
     *
     * @throws Exception
     */
    @Test
    public final void testOverwritePatchOnModifyDNRequestSucceedsEvenWithWrongDN() throws Exception {
        final RejectedChangeRecordListener listener = RejectedChangeRecordListener.OVERWRITE;
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: ou=WRONGscarter, dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: ou=Human Resources,dc=example,dc=com",
            "deleteoldrdn: 1"
        );
        // @formatter:on
        EntryReader reader = new LDIFEntryReader();
        try {
            reader = LDIF.patch(input, patch, listener);
            Entry entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
            assertThat(entry.getName().toString()).isNotEqualTo("uid=scarter,ou=Human Resources,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(3);
            assertThat(entry.getAttribute("objectClass").firstValueAsString()).isEqualTo(
                    "person");
            assertThat(entry.getAttribute("sn").firstValueAsString()).isEqualTo(
                    "new user");
            assertThat(entry.getAttribute("mail").firstValueAsString()).isEqualTo(
                    "mail@mailme.org");
            assertThat(reader.hasNext()).isFalse();
        } finally {
            reader.close();
        }
    }

    /**
     * The listener is in FAIL_FAST Mode. On this example,
     * attaching the listener to the patch should throw a decode exeption as
     * another entry containing the requested change DN already exists.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public final void testFailFastPatchOnModifyDNRequestFailsDueToDuplicateEntry() throws Exception {
        final RejectedChangeRecordListener listener = RejectedChangeRecordListener.FAIL_FAST;
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Eniko",
            "sn: Eniko",
            "cn: Eniko Atpco",
            "uid: user.2"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: moddn",
            "newrdn: uid=user.2,ou=People,dc=example,dc=com",
            "deleteoldrdn: 0"
        );
        // @formatter:on
        EntryReader reader = new LDIFEntryReader();
        try {
            reader = LDIF.patch(input, patch, listener);
        } finally {
            reader.close();
        }
    }

    /**
     * The listener is in OVERWRITE Mode. On this example,
     * attaching the listener to the patch should not throw a decode exeption
     * even if another entry containing the requested change DN already exists.
     * The DN is overwrited.
     *
     * @throws Exception
     */
    @Test
    public final void testOverwritePatchOnModifyDNRequestSucceedsEvenWithDuplicateEntry() throws Exception {
        final RejectedChangeRecordListener listener = RejectedChangeRecordListener.OVERWRITE;
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org",
            "",
            "dn: uid=user.2,ou=People,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: inetorgperson",
            "givenName: Eniko",
            "sn: Eniko",
            "cn: Eniko Atpco",
            "uid: user.2"
        );
        // @formatter:on

        // @formatter:off
        final  LDIFChangeRecordReader patch = new LDIFChangeRecordReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: moddn",
            "newrdn: uid=user.2,ou=People,dc=example,dc=com",
            "deleteoldrdn: 0"
        );
        // @formatter:on
        EntryReader reader = new LDIFEntryReader();
        try {
            reader = LDIF.patch(input, patch, listener);
            Entry entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo("uid=user.2,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(4);
            assertThat(entry.getAttribute("objectClass").firstValueAsString()).isEqualTo(
                    "person");
            assertThat(entry.getAttribute("sn").firstValueAsString()).isEqualTo(
                    "new user");
            assertThat(entry.getAttribute("mail").firstValueAsString()).isEqualTo(
                    "mail@mailme.org");
            assertThat(reader.hasNext()).isFalse();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIF patch doesn't allow null. Exception expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifPatchDoesntAllowNull() throws Exception {
        LDIF.patch(null, null);
    }

    // @formatter:off
    private static final List<String> LDIF_ONE_ENTRY = Arrays.asList(
        "dn: uid=user.1,ou=People,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalperson",
        "objectClass: inetorgperson",
        "givenName: Eniko",
        "sn: Atpco",
        "cn: Eniko Atpco",
        "uid: user.1");
    // @formatter:on

    // @formatter:off
    private static final List<String> LDIF_TWO_ENTRIES = Arrays.asList(
        "dn: uid=user.1,ou=People,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalperson",
        "objectClass: inetorgperson",
        "givenName: Eniko",
        "sn: Atpco",
        "uid: user.1",
        "",
        "dn: uid=user.2,ou=People,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalperson",
        "objectClass: inetorgperson",
        "givenName: Aaaron",
        "sn: Atp",
        "uid: user.2");

    @Test
    public void testMakeEntry() throws Exception {
        final Entry entry = LDIF.makeEntry(LDIF_ONE_ENTRY);
        final Entry entry2 = LDIF.makeEntry(LDIF_ONE_ENTRY.toArray(new String[0]));

        assertThat(entry.getName().toString()).isEqualTo("uid=user.1,ou=People,dc=example,dc=com");
        assertThat(entry.getAttribute("objectClass").firstValueAsString()).isEqualTo("top");
        assertThat(entry.getAttribute("uid").firstValueAsString()).isEqualTo("user.1");
        assertThat(entry.getAttribute("givenName").firstValueAsString()).isEqualTo("Eniko");
        assertThat(entry.getAttribute("sn").firstValueAsString()).isEqualTo("Atpco");
        assertThat(entry2).isEqualTo(entry);
    }

    @Test
    public void testMakeEntries() throws Exception {
        // @formatter:off
        final List<Entry> entries = LDIF.makeEntries(LDIF_TWO_ENTRIES);
        final List<Entry> entries2 = LDIF.makeEntries(LDIF_TWO_ENTRIES.toArray(new String[0]));

        // @formatter:on
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getName().toString()).isEqualTo("uid=user.1,ou=People,dc=example,dc=com");
        assertThat(entries.get(1).getName().toString()).isEqualTo("uid=user.2,ou=People,dc=example,dc=com");
        assertThat(entries2).isEqualTo(entries);
    }

    @Test
    public void testMakeEntryEmpty() throws Exception {
        try {
            LDIF.makeEntry();
            TestCaseUtils.failWasExpected(LocalizedIllegalArgumentException.class);
        } catch (LocalizedIllegalArgumentException e) {
            assertThat(e.getMessageObject()).isEqualTo(CoreMessages.WARN_READ_LDIF_ENTRY_NO_ENTRY_FOUND.get());
        }
    }

    @Test
    public void testMakeEntryWithMultipleEntries() throws Exception {
        try {
            LDIF.makeEntry(LDIF_TWO_ENTRIES);
            TestCaseUtils.failWasExpected(LocalizedIllegalArgumentException.class);
        } catch (LocalizedIllegalArgumentException e) {
            assertThat(e.getMessageObject()).isEqualTo(
                CoreMessages.WARN_READ_LDIF_ENTRY_MULTIPLE_ENTRIES_FOUND.get(2));
        }
    }

    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testMakeEntryBadLDif() throws Exception {
        LDIF.makeEntry("dummy: uid=user.1,ou=People,dc=example,dc=com");
    }

    @Test
    public void testMakeEntriesEmpty() throws Exception {
        try {
            LDIF.makeEntries();
            TestCaseUtils.failWasExpected(LocalizedIllegalArgumentException.class);
        } catch (LocalizedIllegalArgumentException e) {
            assertThat(e.getMessageObject()).isEqualTo(CoreMessages.WARN_READ_LDIF_ENTRY_NO_ENTRY_FOUND.get());
        }
    }

    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testMakeEntriesBadLDif() throws Exception {
        LDIF.makeEntries("dummy: uid=user.1,ou=People,dc=example,dc=com");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testMakeEntriesNull() throws Exception {
        LDIF.makeEntries((String[]) null);
    }
}
