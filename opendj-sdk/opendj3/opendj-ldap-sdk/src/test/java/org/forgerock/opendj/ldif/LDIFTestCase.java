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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import static org.fest.assertions.Assertions.assertThat;

import java.util.ArrayList;
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
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.annotations.Test;

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
        final String[] entry = {
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
        return entry;
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
    @Test()
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
    @Test()
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
     * LDIF Search with null reader is allowed but throws null pointer
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
     * Test the search request using a schema and no specifying attribute
     * description.
     *
     * @throws Exception
     */
    @Test()
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
        if (resultReader.hasNext()) {
            Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(2);
            assertThat(entry.getAttribute("uid")).isNotNull();
            assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
            assertThat(entry.getAttribute("uid").firstValueAsString()).isNotNull();
        }
        resultReader.close();
    }

    /**
     * Test the search request using a schema and no specifying attribute
     * description. TypesOnly = true.
     *
     * @throws Exception
     */
    @Test()
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
        if (resultReader.hasNext()) {
            Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(2);
            assertThat(entry.getAttribute("uid")).isNotNull();
            assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
            assertThat(entry.getAttribute("uid")).isEmpty();
        }
        resultReader.close();
    }

    /**
     * LDIF Search with Schema use : all attributes.
     *
     * @throws Exception
     */
    @Test()
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
        if (resultReader.hasNext()) {
            Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(2);
            assertThat(entry.getAttribute("uid")).isNotNull();
            assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
            assertThat(entry.getAttribute("uid").firstValueAsString()).isEqualTo("user.0");
        }
        resultReader.close();
    }

    /**
     * LDIF Search with Schema use : all attributes. Types only.
     *
     * @throws Exception
     */
    @Test()
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

        if (resultReader.hasNext()) {
            Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(2);
            assertThat(entry.getAttribute("objectClass")).isNotNull();
            assertThat(entry.getAttribute("uid")).isNotNull();
            assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
            assertThat(entry.getAttribute("uid")).isEmpty();

            try {
                assertThat(entry.getAttribute("uid").firstValueAsString()).isNull();
            } catch (NoSuchElementException ex) {
                // No values, only type.
                // Expected exception on entry.getAttribute("uid").firstValueAsString()
            }
        }
        resultReader.close();
    }

    /**
     * Testing the new search request with the + operator == operational
     * attributes only.
     *
     * @throws Exception
     */
    @Test()
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
        if (resultReader.hasNext()) {
            Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(4);
            assertThat(entry.getAttribute("entryDN").firstValueAsString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttribute("entryUUID")).isNotEmpty();
            assertThat(entry.getAttribute("modifyTimestamp")).isNotEmpty();
            assertThat(entry.getAttribute("modifiersName")).isNotEmpty();
        }
        resultReader.close();
    }

    /**
     * Testing the new search request with the + operator == operational
     * attributes only. Combined here with the TypeOnly = true.
     *
     * @throws Exception
     */
    @Test()
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
        if (resultReader.hasNext()) {
            Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(4);
            assertThat(entry.getAttribute("entryDN")).isEmpty();
            assertThat(entry.getAttribute("entryUUID")).isEmpty();
            assertThat(entry.getAttribute("modifyTimestamp")).isEmpty();
            assertThat(entry.getAttribute("modifiersName")).isEmpty();
        }
        resultReader.close();
    }

    /**
     * LDIF Search with Schema use filter on uid attribute.
     *
     * @throws Exception
     */
    @Test()
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
        if (resultReader.hasNext()) {
            Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(1);
        }
        resultReader.close();
    }

    /**
     * LDIF Search with Schema use filter on uid attribute.
     *
     * @throws Exception
     */
    @Test()
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
        if (resultReader.hasNext()) {
            Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(1);
            assertThat(entry.getAttribute("uid").getAttributeDescription()).isNotNull();
            assertThat(entry.getAttribute("uid")).isEmpty();
            try {
                assertThat(entry.getAttribute("uid").firstValueAsString()).isNull();
            } catch (NoSuchElementException ex) {
                // No values, only type.
                // Expected exception on entry.getAttribute("uid").firstValueAsString()
            }
        }
        resultReader.close();
    }

    /**
     * LDIF Search with Schema use with filter not contained in this entry.
     *
     * @throws Exception
     */
    @Test()
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
        if (resultReader.hasNext()) {
            Entry entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(0);
        }
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
        if (resultReader.hasNext()) {
            resultReader.readEntry();
        }
        resultReader.close();
    }

    /**
     * Verifying LDIF Collection reader.
     *
     * @throws Exception
     */
    @Test()
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

        Collection<Entry> collection = new ArrayList<Entry>();
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
     * The LDIF newEntryCollectionReader allows a null parameter.
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
     * LDIF newEntryIteratorReader.
     *
     * @throws Exception
     */
    @Test()
    public final void testLdifNewEntryIteratorReader() throws Exception {

        // @formatter:off
        final Entry e = new LinkedHashMapEntry(
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson"
        );
        final Entry e1 = new LinkedHashMapEntry("dn: uid=user.1,ou=People,dc=example,dc=com", "objectClass: person");
        // @formatter:on

        final SortedMap<DN, Entry> sourceEntries = new TreeMap<DN, Entry>();
        sourceEntries.put(DN.valueOf("uid=user.0,ou=People,dc=example,dc=com"), e);
        sourceEntries.put(DN.valueOf("uid=user.1,ou=People,dc=example,dc=com"), e1);
        final Iterator<Entry> sourceIterator = sourceEntries.values().iterator();

        final EntryReader resultReader = LDIF.newEntryIteratorReader(sourceIterator);
        Entry entry = null;
        int cCount = 0;
        while (resultReader.hasNext()) {
            entry = resultReader.readEntry();
            assertThat(entry.getName().toString()).isNotNull();
            assertThat(entry.getName().toString()).contains("ou=People,dc=example,dc=com");
            assertThat(entry.getAttributeCount()).isGreaterThanOrEqualTo(1);
            cCount++;
        }
        assertThat(cCount).isEqualTo(2);
        resultReader.close();
    }

    /**
     * LDIF newEntryIteratorReader doesn't allow null.
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
     * Verify LDIF CopyToChangeRecord is working.
     *
     * @throws Exception
     */
    @Test()
    public final void testLdifCopyToChangeRecord() throws Exception {

        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "# Entry to delete",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on
        final java.util.List<String> actual = new ArrayList<String>();
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
     * LDIF copyTo - ChangeRecord doesn't allow null parameters.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifCopyToChangeRecordDoesntAllowNull() throws Exception {

        LDIF.copyTo((ChangeRecordReader) null, (ChangeRecordWriter) null);
    }

    /**
     * LDIF copyTo - ChangeRecord doesn't allow a null writer.
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
     * LDIF copyTo - ChangeRecord doesn't allow a null reader.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifCopyToChangeRecordDoesntAllowNullReader() throws Exception {

        final java.util.List<String> actual = new ArrayList<String>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        LDIF.copyTo(null, writer);
    }

    /**
     * LDIF copyTo - EntryWriter doesn't allow null.
     *
     * @throws Exception
     */
    @Test()
    public final void testLdifCopyToEntryWriter() throws Exception {

        // @formatter:off
        final LDIFEntryReader reader = new LDIFEntryReader(
            "# Entry to delete",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on

        final List<String> actual = new ArrayList<String>();
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

        final List<String> actual = new ArrayList<String>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        LDIF.copyTo(null, writer);
    }

    /**
     * Testing the diff function. Following example from the admin guide.
     *
     * @see <a
     *      href=http://opendj.forgerock.org/doc/admin-guide/index.html#ldif-diff
     *      -1 result”>Admin Guide</a>
     * @throws Exception
     */
    @Test()
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
        /* Expected : 2 add / 1 delete.
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
            if (mod.getModificationType() == ModificationType.ADD) {
                assertThat((mod.getAttribute().getAttributeDescription().toString())
                        .equals("description")
                        || (mod.getAttribute().getAttributeDescription().toString())
                                .equals("userPassword"));

                assertThat((mod.getAttribute().firstValueAsString()).equals("A new description.")
                        || (mod.getAttribute().firstValueAsString()).equals("secret12"));
            }
            if (mod.getModificationType() == ModificationType.DELETE) {
                assertThat(mod.getAttribute().getAttributeDescription().toString()).isEqualTo(
                        "userPassword");
                assertThat(mod.getAttribute().firstValueAsString()).isEqualTo("changeme");
            }
        }
        reader.close();
        target.close();
    }

    /**
     * Testing the diff : entry in source but not in target.
     *
     * @throws Exception
     */
    @Test()
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
     * Testing the diff : entry in target not in source. The rdn of the
     * following example is completed by ou=People.
     *
     * @throws Exception
     */
    @Test()
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
     * Diff between two same entries : no modifications expected.
     *
     * @throws Exception
     */
    @Test()
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
     * Diff between two same entries : no modifications expected.
     *
     * @throws Exception
     */
    @Test()
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
     * Test the patch function. Apply simple patch to replace/add data to the
     * input.
     *
     * @throws Exception
     */
    @Test()
    public final void testLdifPatch() throws Exception {
        // @formatter:off
        final LDIFEntryReader input = new LDIFEntryReader(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "sn: new user",
            "mail: mail@mailme.org"
        );

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

        // @formatter:on
        final EntryReader reader = LDIF.patch(input, patch);
        Entry entry = reader.readEntry();
        assertThat(entry.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(4); // objectclass - sn - mail - manager
        assertThat(entry.getAttribute("manager").firstValueAsString()).isEqualTo(
                "uid=joneill,ou=People,dc=example,dc=com");
        reader.close();
    }

    /**
     * LDIF patch - EntryReader/ChangeRecordReader doesn't allow null. Exception
     * expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testLdifPatchDoesntAllowNull() throws Exception {
        LDIF.patch(null, null);
    }

}
