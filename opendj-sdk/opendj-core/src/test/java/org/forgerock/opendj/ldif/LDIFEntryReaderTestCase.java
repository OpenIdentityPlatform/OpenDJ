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

import static org.testng.Assert.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.NoSuchElementException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Matcher;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy.Action;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * This class tests the LDIFEntryReader functionality.
 */
@SuppressWarnings("javadoc")
public final class LDIFEntryReaderTestCase extends AbstractLDIFTestCase {
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
     * Test SetExcludeBranch method of LDIFEntryReader. Excluding the
     * "dc=example,dc=com" Entry is not read and function return a
     * NoSuchElementException exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testSetExcludeBranchWithNoMatch() throws Exception {

        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setExcludeBranch(DN.valueOf("dc=example,dc=com"));

        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Test SetExcludeBranch method of LDIFEntryReader. Excluding the
     * "dc=example,dc=org", which is not in the standard ldif entry. Entry must
     * be fully read.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeBranchWithMatch() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setExcludeBranch(DN.valueOf("dc=example,dc=org"));
        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
    }

    /**
     * Test the setExcludeBranch with a null parameter.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetExcludeBranchDoesntAllowNull() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setExcludeBranch(null);
        reader.close();
    }

    /**
     * Test to read an entry excluding user attributes. Default case - all the
     * lines must be read.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeAllUserAttributesFalse() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setExcludeAllUserAttributes(false);
        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
        assertThat(entry.getAttribute("entryDN")).isNotNull();
        assertThat(entry.getAttribute("description")).isNotNull();
    }

    /**
     * Test to read an entry excluding user attributes Only the operational
     * attributes must be read (entryDN, entryUUID, modifyTimestamp,
     * modifiersName...) (e.g : 4 in the standard entry)
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeAllUserAttributesTrue() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setExcludeAllUserAttributes(true);

        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getAttribute("dn")).isNull();
        assertThat(entry.getAttribute("sn")).isNull();
        assertThat(entry.getAttribute("uid")).isNull();
        assertThat(entry.getAttribute("description")).isNull();

        assertThat(entry.getAttribute("entryDN")).isNotEmpty();
        assertThat(entry.getAttribute("entryUUID")).isNotEmpty();
        assertThat(entry.getAttribute("modifyTimestamp")).isNotNull();
        assertThat(entry.getAttributeCount()).isEqualTo(4);
    }

    /**
     * Test to read an entry with attribute exclusions. In this test, the
     * attribute description 'vip' doesn't exist... the entry must be fully
     * read.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeAttributeWithNoMatch() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setExcludeAttribute(AttributeDescription.valueOf("vip"));

        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
        // no attribute 'vip'
        assertThat(entry.getAttribute("vip")).isNull();
    }

    /**
     * Test to read an entry with attribute exclusions. Three attributes
     * excluded, entry must contain the others.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeAttributeWithMatch() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());

        reader.setExcludeAttribute(AttributeDescription.valueOf("cn"));
        reader.setExcludeAttribute(AttributeDescription.valueOf("cn"));
        reader.setExcludeAttribute(AttributeDescription.valueOf("sn"));
        reader.setExcludeAttribute(AttributeDescription.valueOf("entryDN"));

        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getAttribute("entryDN")).isNull();
        assertThat(entry.getAttribute("sn")).isNull();
        assertThat(entry.getAttribute("cn")).isNull();

        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes - 3);
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
    }

    /**
     * {@link LDIFEntryReader#setExcludeAttribute(AttributeDescription)}
     * does not allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetExcludeAttributeDoesntAllowNull() throws Exception {

        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setExcludeAttribute(null);
        reader.close();
    }

    /**
     * Test to read an entry excluding all operational attributes
     * setExcludeAllOperationalAttributes to false (default case) All attributes
     * must be read.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeAllOperationalAttributesFalse() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());

        reader.setExcludeAllOperationalAttributes(false);
        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
        assertThat(entry.getAttribute("entryDN")).isNotNull();
        assertThat(entry.getAttribute("entryUUID")).isNotNull();
        assertThat(entry.getAttribute("modifyTimestamp")).isNotNull();
    }

    /**
     * Test to read an entry excluding all operational attributes
     * setExcludeAllOperationalAttributes is forced to true.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeAllOperationalAttributesTrue() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());

        reader.setExcludeAllOperationalAttributes(true);
        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getAttribute("entryDN")).isNull();
        assertThat(entry.getAttribute("entryUUID")).isNull();
        assertThat(entry.getAttribute("modifyTimestamp")).isNull();

        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isLessThan(nbStandardEntryAttributes);
    }

    /**
     * Test SetExcludeFilter method of LDIFEntryReader. Throws a
     * NullPointerException if the excludeFilter is null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testsetExcludeFilterDoesntAllowNull() throws Exception {

        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());

        reader.setExcludeFilter(null);
        reader.close();
    }

    /**
     * Test testSetExcludeFilter method of LDIFEntryReader. StandardEntry has an
     * objectclass : person, not vip. The filter must exclude all entries with
     * an objectclass = vip. In this case, entry must be fully read.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeFilterWithMatch() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        final Filter filter = Filter.equality("objectclass", "vip");
        final Matcher excludeFilter = filter.matcher();

        reader.setExcludeFilter(excludeFilter);
        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttribute("objectclass").toString()).isNotEqualTo("vip");
    }

    /**
     * Test testSetExcludeFilter method of LDIFEntryReader. StandardEntry has an
     * objectclass : person. The filter must exclude all entries with an
     * objectclass = person. Entry musn't be read.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testSetExcludeFilterWithNoMatch() throws Exception {

        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        final Filter filter = Filter.equality("objectclass", "person");
        final Matcher excludeFilter = filter.matcher();

        reader.setExcludeFilter(excludeFilter);
        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Test the setIncludeAttribute Attributes included must be the only ones
     * present in the entry. First line dn must be present.
     *
     * @throws Exception
     */
    @Test
    public void testSetIncludeAttributeWithMatch() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setIncludeAttribute(AttributeDescription.valueOf("cn"));
        reader.setIncludeAttribute(AttributeDescription.valueOf("sn"));
        reader.setIncludeAttribute(AttributeDescription.valueOf("sn"));
        final Entry entry = reader.readEntry();

        assertThat(entry).isNotNull();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(2);
        assertThat(entry.getAttribute("cn")).isNotNull();
        assertThat(entry.getAttribute("sn")).isNotNull();
        assertThat(entry.getAttribute("description")).isNull();

        reader.close();
    }

    /**
     * Test the setIncludeAttribute Attributes included must be the only ones
     * present in the entry. In this case, the attribute "manager" doesn't
     * exist. Only dn line must be read.
     *
     * @throws Exception
     */
    @Test
    public void testSetIncludeAttributeWithNoMatch() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setIncludeAttribute(AttributeDescription.valueOf("manager"));
        final Entry entry = reader.readEntry();

        assertThat(entry).isNotNull();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(0);
        assertThat(entry.getAttribute("description")).isNull();

        reader.close();
    }

    /**
     * Test SetIncludeAttribute method of LDIFEntryReader Throws a
     * NullPointerException if the includeAttribute is null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeAttributeDoesntAllowNull() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setIncludeAttribute(null);
        reader.close();
    }

    /**
     * Test SetIncludeBranch method of LDIFEntryReader. "dc=example,dc=org" not
     * existing in the standard ldif entry. Entry must not be read.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testSetIncludeBranchWithNoMatch() throws Exception {

        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setIncludeBranch(DN.valueOf("dc=example,dc=org"));

        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Test SetIncludeBranch method of LDIFEntryReader. "dc=example,dc=com" is
     * the branch of the standard entry. Entry must be fully read.
     *
     * @throws Exception
     */
    @Test
    public void testSetIncludeBranchWithMatch() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setIncludeBranch(DN.valueOf("dc=example,dc=com"));
        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry).isNotNull();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
    }

    /**
     * Test SetIncludeBranch method of LDIFEntryReader. Throws a
     * NullPointerException if the includeBranch is null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeBranchDoesntAllowNull() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setIncludeBranch(null);
        reader.close();
    }

    /**
     * LDIFEntryReader setIncludeFilter with an equality filter on the
     * objectclass: vip, Entry musn't be read.
     *
     * @throws Exception
     *             NoSuchElementException launched if entry is not read
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testSetIncludeFilterWithNoMatch() throws Exception {

        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        final Filter filter = Filter.equality("objectclass", "vip");
        final Matcher includeFilter = filter.matcher();
        reader.setIncludeFilter(includeFilter);
        Entry entry = null;
        try {
            entry = reader.readEntry();
        } finally {
            reader.close();
        }
        assertThat(entry).isNull();

    }

    /**
     * LDIFEntryReader setIncludeFilter with an equality filter on the
     * objectclass: person, Entry must be read.
     *
     * @throws Exception
     */
    @Test
    public void testSetIncludeFilterWithMatch() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        final Filter filter = Filter.equality("objectclass", "person");
        final Matcher includeFilter = filter.matcher();
        reader.setIncludeFilter(includeFilter);
        Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttribute("cn")).isNotNull();
        assertThat(entry.getAttribute("sn")).isNotNull();

    }

    /**
     * LDIFEntryReader setIncludeFilter doesn't allow null.
     *
     * @throws Exception
     *             NullPointerException expected
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeFilterDoesntAllowNull() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setIncludeFilter(null);
        reader.close();
    }

    /**
     * Tests reading a malformed record invokes the rejected record listener.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test
    public void testRejectedLDIFListenerMalformedFirstRecord() throws Exception {
        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        LDIFEntryReader reader =
                new LDIFEntryReader("dn: baddn", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example");

        reader.setRejectedLDIFListener(listener);

        assertThat(reader.hasNext()).isFalse();

        verify(listener).handleMalformedRecord(
                eq(1L),
                eq(Arrays.asList("dn: baddn", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example")),
                any(LocalizableMessage.class));
        reader.close();
    }

    /**
     * Tests reading a malformed LDIF invokes the rejected LDIF listener.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test
    public void testRejectedLDIFListenerMalformedSecondRecord() throws Exception {
        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        // @formatter:off
        LDIFEntryReader reader = new LDIFEntryReader(
                "dn: dc=example,dc=com",
                "changetype: add",
                "objectClass: top",
                "objectClass: domainComponent",
                "dc: example",
                "",
                "dn: baddn",
                "changetype: add",
                "objectClass: top",
                "objectClass: domainComponent",
                "dc: example"
        );
        // @formatter:on

        reader.setRejectedLDIFListener(listener);

        reader.readEntry(); // Skip good record.
        assertThat(reader.hasNext()).isFalse();

        verify(listener).handleMalformedRecord(
                eq(7L),
                eq(Arrays.asList("dn: baddn", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example")),
                any(LocalizableMessage.class));
        reader.close();
    }

    /**
     * Tests reading a LDIF which does not conform to the schema invokes the
     * rejected LDIF listener.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test
    public void testRejectedRecordListenerRejectsBadSchemaRecord() throws Exception {
        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        // @formatter:off
        LDIFEntryReader reader = new LDIFEntryReader(
            "dn: dc=example,dc=com",
            "changetype: add",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example",
            "xxx: unknown attribute");
        reader.setRejectedLDIFListener(listener)
             .setSchemaValidationPolicy(
                 SchemaValidationPolicy.ignoreAll()
                     .checkAttributesAndObjectClasses(Action.REJECT));
        // @formatter:on

        assertThat(reader.hasNext()).isFalse();

        verify(listener).handleSchemaValidationFailure(
                eq(1L),
                eq(Arrays.asList("dn: dc=example,dc=com", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example", "xxx: unknown attribute")),
                anyListOf(LocalizableMessage.class));
        reader.close();
    }

    /**
     * Tests reading a LDIF which does not conform to the schema invokes the
     * rejected LDIF listener.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test
    public void testRejectedLDIFListenerWarnsBadSchemaRecord() throws Exception {
        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        LDIFEntryReader reader =
                new LDIFEntryReader("dn: dc=example,dc=com", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example", "xxx: unknown attribute");
        reader.setRejectedLDIFListener(listener).setSchemaValidationPolicy(
                SchemaValidationPolicy.ignoreAll().checkAttributesAndObjectClasses(Action.WARN));

        assertThat(reader.hasNext()).isTrue();

        Entry entry = reader.readEntry();

        assertThat(entry.getName().toString()).isEqualTo("dc=example,dc=com");
        assertThat(entry.containsAttribute("objectClass", "top", "domainComponent")).isTrue();
        assertThat(entry.containsAttribute("dc", "example")).isTrue();
        assertThat(entry.getAttributeCount()).isEqualTo(2);

        verify(listener).handleSchemaValidationWarning(
                eq(1L),
                eq(Arrays.asList("dn: dc=example,dc=com", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example", "xxx: unknown attribute")),
                anyListOf(LocalizableMessage.class));
        reader.close();
    }

    /**
     * LDIFEntryReader setRejectedLDIFListener skips the record.
     *
     * @throws Exception
     */
    @Test
    public void testRejectedLDIFListenerSkipsRecord() throws Exception {
        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setRejectedLDIFListener(listener).setExcludeBranch(DN.valueOf("dc=com"));

        assertThat(reader.hasNext()).isFalse();

        verify(listener).handleSkippedRecord(eq(1L), eq(Arrays.asList(getStandardEntry())),
                any(LocalizableMessage.class));
        reader.close();
    }

    /**
     * LDIFEntryReader setIncludeFilter allows null.
     *
     * @throws Exception
     */
    @Test
    public void testSetRejectedLDIFListenerDoesAllowNull() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setRejectedLDIFListener(null);
        Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");

    }

    /**
     * LDIFEntryReader setSchemaValidationPolicy. Validate the entry depending
     * of the selected policy. Entry is here NOT allowed because it contains a
     * uid attribute which is not allowed by the SchemaValidationPolicy.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testSetSchemaValidationPolicyDefaultRejectsEntry() throws Exception {
        // @formatter:off
        String[] strEntry = {
            "dn: uid=user.0,ou=People,dc=example,dc=com", "objectClass: person",
            "objectClass: top", "cn: Aaccf Amar", "sn: Amar", "uid: user.0"
        };
        // @formatter:on
        final LDIFEntryReader reader = new LDIFEntryReader(strEntry);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFEntryReader setSchemaValidationPolicy. Validate the entry depending
     * of the selected policy. Entry is here allowed because it fills the case
     * of the validation.
     *
     * @throws Exception
     */
    @Test
    public void testSetSchemaValidationPolicyDefaultAllowsEntry() throws Exception {
        // @formatter:off
        String[] strEntry = {
            "dn: uid=user.0,ou=People,dc=example,dc=com", "objectClass: person",
            "objectClass: top", "cn: Aaccf Amar", "sn: Amar"
        };
        // @formatter:on

        final LDIFEntryReader reader = new LDIFEntryReader(strEntry);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());
        final Entry entry = reader.readEntry();
        reader.close();

        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(3);
    }

    /**
     * LDIFEntryReader SetValidationPolicy doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetSchemaValidationPolicyDoesntAllowNull() throws Exception {

        // @formatter:off
        String[] strEntry = {
            "dn: uid=user.0,ou=People,dc=example,dc=com", "objectClass: person",
            "objectClass: top", "cn: Aaccf Amar", "sn: Amar", "uid: user.0"
        };
        // @formatter:on

        final LDIFEntryReader reader = new LDIFEntryReader(strEntry);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(null);
        reader.close();
    }

    /**
     * Test the setSchemaSetValidationPolicy. Adding a new schema and insuring
     * the validationPolicy allows the new attribute/class Adding a new schema
     * explained in the admin-guide (chapter 15. Managing Schema). The new
     * attribute is accepted by the policy schema. Entry must be read.
     *
     * @throws Exception
     */
    @Test
    public void testSetSchemaSetSchemaValidationPolicyDefaultAllowsEntryWithNewAttribute()
            throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: top", "cn: Aaccf Amar",
            "sn: Amar",
            "objectClass: myCustomObjClass",
            "myCustomAttribute: Testing..."
        };
        // @formatter:on

        final SchemaBuilder scBuild = new SchemaBuilder();
        // Adding the new schema containing the customclass
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType("( temporary-fake-attr-id NAME 'myCustomAttribute' EQUALITY case"
                + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )", false);
        // Adding default core schema
        scBuild.addSchema(Schema.getCoreSchema(), false);
        Schema schema = scBuild.toSchema();
        final LDIFEntryReader reader = new LDIFEntryReader(strEntry);
        reader.setSchema(schema);
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        Entry entry = null;
        try {
            entry = reader.readEntry();
            // cn + sn + myCustomAttribute + objectClass
            assertThat(entry.getAttributeCount()).isEqualTo(4);
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=user.0,ou=People,dc=example,dc=com");
            assertThat(entry.getAttribute("sn").firstValue().toString()).isEqualTo("Amar");
            assertThat(entry.getAttribute("cn").firstValueAsString()).isEqualTo("Aaccf Amar");
            // entry.getAttribute("new attribute") : access by that way doesn't work...
            // TODO BUG jira/browse/OPENDJ-157
            assertThat(
                    entry.getAttribute(AttributeDescription.valueOf("myCustomAttribute", schema))
                            .firstValueAsString()).isEqualTo("Testing...");
        } finally {
            reader.close();
        }
    }

    /**
     * Test the setSchemaSetValidationPolicy : throw an exception if
     * unrecognized attributes are found. ex. Entry
     * "uid=user.0,ou=People,dc=example,dc=com" doesn't respect the schema
     * because it contains an unrecognized object class "myCustomObjClass".
     * Entry musn't be read.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testSetSchemaSetSchemaValidationPolicyDefaultDoesntAllowEntryWithNewAttribute()
            throws Exception {

        // @formatter:off
        String[] strEntry = {
            "dn: uid=user.0,ou=People,dc=example,dc=com", "objectClass: person",
            "objectClass: organizationalperson", "objectClass: top", "cn: Aaccf Amar",
            "sn: Amar", "objectClass: myCustomObjClass", "myCustomAttribute: Testing..."
        };
        // @formatter:on

        final LDIFEntryReader reader = new LDIFEntryReader(strEntry);

        final SchemaBuilder scBuild = new SchemaBuilder();
        scBuild.addSchema(Schema.getCoreSchema(), false);

        reader.setSchema(scBuild.toSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFEntryReader setSchema doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetSchemaDoesntAllowNull() throws Exception {

        final LDIFEntryReader reader = new LDIFEntryReader(getStandardEntry());
        reader.setSchema(null); // must throw a NullPointerException
        reader.close();
    }

    /**
     * Tests readEntry method of LDIFEntryReader class.See
     * https://opends.dev.java.net/issues/show_bug.cgi?id=4545 for more details.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testEmpty() throws Exception {
        final String path = TestCaseUtils.createTempFile("");
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        try {
            Assert.assertFalse(reader.hasNext());
            Assert.assertFalse(reader.hasNext());
            reader.readEntry();
        } finally {
            Assert.assertFalse(reader.hasNext());
            reader.close();
        }
    }

    /**
     * Test to read an entry with no empty spaces.
     *
     * @throws Exception
     */
    @Test
    public void testReadEntryWithNoSpaces() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "# Entry of SCarter",
            "dn:uid=scarter,ou=People,dc=example,dc=com",
            "objectClass:person",
            "objectClass:inetorgperson",
            "objectClass:organizationalperson",
            "objectClass:top",
            "postalAddress:Aaccf Amar$01251 Chestnut Street$Panama City, DE  50369",
            "postalCode:50369",
            "uid:scarter",
            "description::U2hvcnQgZGVzY3JpcHRpb24gb2YgU2NhcnRlcg=="
        };
        // @formatter:on
        final String path = TestCaseUtils.createTempFile(strEntry);
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        Entry entry = null;
        try {
            assertThat(reader.hasNext());
            entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=scarter,ou=People,dc=example,dc=com");
            assertThat(entry.getAttribute("uid").firstValueAsString()).isEqualTo("scarter");
            assertThat(entry.getAttribute("description").firstValueAsString()).isEqualTo(
                    "Short description of Scarter");
        } finally {
            reader.close();
        }

    }

    /**
     * Test to read an entry containing spaces before the attribute.
     */
    @Test
    public void testReadEntryWithAttributesSpacesAtStart() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "#   Entry of SCarter",
            "dn:   uid=scarter,ou=People,dc=example,dc=com",
            "objectClass:   person",
            "objectClass:   inetorgperson",
            "objectClass:   organizationalperson",
            "objectClass:   top",
            "postalAddress:   Aaccf Amar$01251 Chestnut Street$Panama City, DE  50369",
            "postalCode:   50369",
            "uid:    scarter",
            "description::    U2hvcnQgZGVzY3JpcHRpb24gb2YgU2NhcnRlcg==",
        };
        // @formatter:on
        final String path = TestCaseUtils.createTempFile(strEntry);
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        Entry entry = null;
        try {
            assertThat(reader.hasNext());
            entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=scarter,ou=People,dc=example,dc=com");
            assertThat(entry.getAttribute("uid").firstValueAsString()).isEqualTo("scarter");
            assertThat(entry.getAttribute("description").firstValueAsString()).isEqualTo(
                    "Short description of Scarter");
        } finally {
            reader.close();
        }
    }

    /**
     * Test to read an entry containing spaces at the end of the attribute. ldif
     * do not admit spaces at end ;)
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testReadEntryWithAttributesSpacesAtEnd() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "#   Entry of SCarter   ",
            "dn:   uid=scarter,ou=People,dc=example,dc=com    ",
            "objectClass:   person    ",
            "objectClass:   inetorgperson    ",
            "objectClass:   organizationalperson    ",
            "objectClass:   top  ",
            "postalAddress:   Aaccf Amar$01251 Chestnut Street$Panama City, DE  50369  ",
            "postalCode:   50369 ",
            "uid:    scarter  ",
            "description::    U2hvcnQgZGVzY3JpcHRpb24gb2YgU2NhcnRlcg==   ",
        };
        // @formatter:on
        final String path = TestCaseUtils.createTempFile(strEntry);
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        Entry entry = null;
        try {
            assertThat(reader.hasNext());
            entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "uid=scarter,ou=People,dc=example,dc=com");
            assertThat(entry.getAttribute("uid").firstValueAsString()).isEqualTo("scarter");
            assertThat(entry.getAttribute("description").firstValueAsString()).isEqualTo(
                    "Short description of Scarter");
        } finally {
            reader.close();
        }
    }

    /**
     * Tests readEntry method of LDIFEntryReader class.See
     * https://opends.dev.java.net/issues/show_bug.cgi?id=4545 for more details.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testReadEntry() throws Exception {
        final String path = TestCaseUtils.createTempFile(getStandardEntry());
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        try {
            Assert.assertTrue(reader.hasNext());
            final Entry entry = reader.readEntry();
            assertNotNull(entry);
            Assert.assertEquals(entry.getName(), DN
                    .valueOf("uid=user.0,ou=People,dc=example,dc=com"));
            Assert.assertFalse(reader.hasNext());
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Test to read an entry containing duplicates values
     * ERR_LDIF_MULTI_VALUED_SINGLE_VALUED_ATTRIBUTE &&
     * WARN_LDIF_DUPLICATE_ATTRIBUTE_VALUE.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFEntryReaderEntryWithDuplicateAttributes() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=user.0,ou=People,dc=example,dc=com",
            "objectClass: organizationalperson",
            "objectClass: top",
            "postalAddress: Aaccf Amar$01251 Chestnut Street$Panama City, DE  50369",
            "postalCode: 50369",
            "description: This is the description for Aaccf Amar.",
            "userPassword: ;", // empty value allowed
            "telephoneNumber: +1 685 622 6202", "sn: Amar",
            // entryUUID : ERR_LDIF_MULTI_VALUED_SINGLE_VALUED_ATTRIBUTE
            "entryUUID: ad55a34a-763f-358f-93f9-da86f9ecd9e4",
            "entryUUID: ad55a34a-763f-358f-93f9-da45f9ecd9e4",
            // WARN_LDIF_DUPLICATE_ATTRIBUTE_VALUE :
            "objectClass: person",
            "objectClass: person"
        };
        // @formatter:on
        final String path = TestCaseUtils.createTempFile(strEntry);
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFEntryReader - Try to read a full example of entry.
     *
     * @throws Exception
     */
    @Test
    public void testLDIFEntryReaderFullEntry() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "version: 1",
            "dn: cn=Barbara Jensen, ou=Product Development, dc=airius, dc=com",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Barbara Jensen",
            "cn: Barbara J Jensen",
            "cn: Babs Jensen",
            "sn: Jensen",
            "uid: bjensen",
            "telephonenumber: +1 408 555 1212",
            "description: A big sailing fan.",
            "", // if a space here, second entry is not read
            "dn: cn=Bjorn Jensen, ou=Accounting, dc=airius, dc=com",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Bjorn Jensen",
            "sn: Jensen",
            "telephonenumber: +1 408 555 1212"
        };
        // @formatter:on
        final String path = TestCaseUtils.createTempFile(strEntry);
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);

        Entry entry = null;
        try {
            assertThat(reader.hasNext());
            // 1st entry
            entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "cn=Barbara Jensen, ou=Product Development, dc=airius, dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(6);
            // 2nd
            entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "cn=Bjorn Jensen, ou=Accounting, dc=airius, dc=com");
            assertThat(entry.getAttributeCount()).isEqualTo(4);

            assertThat(reader.hasNext()).isFalse();
        } finally {
            reader.close();
        }
    }


    /**
     * Tries to read an entry composed by multi-valued attributes. The
     * multi-valued attributes contains an interesting case where two of them
     * represents the same value, one in uppercase and the other in lower case.
     *
     * @throws Exception
     */
    @Test
    public void testLDIFEntryReaderMultiplesAttributeValuesDifferentLetterCase() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=Character Set,cn=Password Validators,cn=config",
            "objectClass: ds-cfg-character-set-password-validator",
            "objectClass: ds-cfg-password-validator",
            "objectClass: top",
            "ds-cfg-enabled: true",
            "ds-cfg-java-class: org.opends.server.extensions.CharacterSetPasswordValidator",
            "ds-cfg-allow-unclassified-characters: true",
            "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
            "ds-cfg-character-set: 1:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "ds-cfg-character-set: 1:0123456789",
            "ds-cfg-character-set: 1:~!@#$%^&*()-_=+[]{}|;:,.<>/?",
            "cn: Character Set"
        };
        // @formatter:on
        final String path = TestCaseUtils.createTempFile(strEntry);
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        try {
            assertThat(reader.hasNext());
            final Entry entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo(
                    "cn=Character Set,cn=Password Validators,cn=config");
            // List the attributes : objectClass ds-cfg-enabled ds-cfg-java-class
            // ds-cfg-allow-unclassified-characters ds-cfg-character-set cn
            assertThat(entry.getAttributeCount()).isEqualTo(6);
            assertThat(entry.getAttribute("ds-cfg-character-set")).isNotEmpty();
            assertThat(entry.getAttribute("ds-cfg-character-set").toArray().length).isEqualTo(4);
            assertThat(reader.hasNext()).isFalse();
        } finally {
            reader.close();
        }
    }

    /**
     * Testing to read an entry which containing empty required attributes.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testValueOfLDIFEntryReadStandardEntryMissingValues() throws Exception {

        // @formatter:off
        final String[] strEntry = {
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: organizationalperson",
            "objectClass: top",
            "cn: Aaccf Amar",
            "sn:"
        };
        // @formatter:on
        final LDIFEntryReader reader = new LDIFEntryReader(strEntry);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());
        try {
            reader.readEntry();
        } finally {
            reader.close();
        }

    }

    /**
     * Testing to read an entry containing BER value
     * schemaValidationPolicy.checkAttributeValues().needsChecking() &&
     * attributeDescription.containsOption("binary") reply 'because it has an
     * unexpected binary option for attribute sn : 'sn;binary'.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testValueOfLDIFEntryBERUnexpectedBinaryOption() throws Exception {

        // @formatter:off
        final String[] strEntry = {
            "version: 1",
            "dn:: b3U95Za25qWt6YOoLG89QWlyaXVz",
            "# dn:: ou=<JapaneseOU>,o=Airius",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Horatio Jensen",
            "cn: Horatio N Jensen",
            "sn: Jensen",
            "uid: hjensen",
            "sn;binary:: 5bCP56yg5Y6f"
        };
        // @formatter:on

        final LDIFEntryReader reader = new LDIFEntryReader(strEntry);
        Schema schema = Schema.getCoreSchema();
        reader.setSchema(schema);
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        try {
            reader.readEntry();
        } finally {
            reader.close();
        }

    }

    /**
     * Testing to read an entry containing a fatal continuation line at start.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testValueOfLDIFEntryFatalContinuationLineAtStart() throws Exception {

        // @formatter:off
        final String[] strEntry = {
            " This is a fatal continuation line at start",
            "dn:: b3U95Za25qWt6YOoLG89QWlyaXVz",
            "# dn:: ou=<JapaneseOU>,o=Airius",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Horatio Jensen",
            "cn: Horatio N Jensen",
            "sn: Jensen",
            "uid: hjensen"
        };
        // @formatter:on

        final LDIFEntryReader reader = new LDIFEntryReader(strEntry);
        try {
            reader.readEntry();
        } finally {
            reader.close();
        }

    }

    /**
     * LDIFEntryReader entry containing a reference to an external file.
     *
     * @throws Exception
     */
    @Test
    public void testValueOfLDIFEntryReadEntryContainingURL() throws Exception {
        final File file = File.createTempFile("sdk", ".jpeg");
        final String url = file.toURI().toURL().toString();

        // @formatter:off
        final LDIFEntryReader reader = new LDIFEntryReader(
                "#A single comment",
                " continued in the second line",
                "version: 1",
                "dn:: b3U95Za25qWt6YOoLG89QWlyaXVz",
                "# dn:: ou=<JapaneseOU>,o=Airius",
                "objectclass: top",
                "objectclass: person",
                "objectclass: organizationalPerson",
                "cn: Horatio Jensen",
                "cn: Horatio N Jensen",
                "sn: Jensen",
                "uid: hjensen",
                "telephonenumber: +1 408 555 1212",
                "jpegphoto:< " + url,
                "#This is a end line comment", "# Followed by another"
        );
        // @formatter:on

        try {
            Entry entry = reader.readEntry();
            assertThat(entry.getName().toString()).isNotEqualTo("b3U95Za25qWt6YOoLG89QWlyaXVz");
            assertThat(entry.getAttributeCount()).isEqualTo(6);
            assertThat(entry.getAttribute("jpegphoto")).isNotEmpty();
            assertThat(entry.getAttribute("cn").firstValueAsString()).isEqualTo("Horatio Jensen");
        } finally {
            file.delete();
            reader.close();
        }
    }

    /**
     * LDIFEntryReader entry containing a malformed URL.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testValueOfLDIFEntryReadEntryContainingMalformedURL() throws Exception {

        // @formatter:off
        final LDIFEntryReader reader = new LDIFEntryReader(
                "version: 1",
                "dn:: b3U95Za25qWt6YOoLG89QWlyaXVz",
                "# dn:: ou=<JapaneseOU>,o=Airius",
                "objectclass: top",
                "objectclass: person",
                "objectclass: organizationalPerson",
                "cn: Horatio Jensen",
                "cn: Horatio N Jensen",
                "sn: Jensen",
                "uid: hjensen",
                "telephonenumber: +1 408 555 1212",
                "jpegphoto:< invalidProtocol",
                " ",
                " ");
        // @formatter:on

        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Test to read an entry missing key value.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testReadEntryParseColonPositionThrowException() throws Exception {

        // @formatter:off
        final String path = TestCaseUtils.createTempFile(
                "#Entry made for testing",
                ": cn=Gern Jensen, ou=Product Testing, dc=airius, dc=com",
                "objectclass: top",
                "objectclass: person",
                "objectclass: organizationalPerson"
        );
        // @formatter:on

        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Test to read an entry containing base64 encoded attribute.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testReadEntryBase64EncodedMalformedBase64Attribute() throws Exception {

        // @formatter:off
        final LDIFEntryReader reader = new LDIFEntryReader(Arrays.asList(
            "version: 1",
            "dn: cn=Gern Jensen, ou=Product Testing, dc=airius, dc=com",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Gern Jensen",
            "cn: Gern O Jensen",
            "sn: Jensen",
            "uid: gernj",
            "telephonenumber: +1 408 555 1212",
            "description:: V2hhdCBhIGNhcmVmdWwgcmVhZGVyIHlvdSBhcmUhICBUaGlzIHZhbHVl"
            + "IGlzIGJhc2UtNjQtZW5aaaaaaaaaaajb2RlZCBiZWNhdXNlIGl0IGhhcyBhIGNvbnRyb2wgY2hhcmFjdG"
            + "VyIGluIGl0IChhIENSKS4NICBCeSB0aGUgd2F5LCB5b3Ugc2hvdWxkIHJlYWxseSBnZXQg"
            + "b3V0IG1vcmUu"
        ));
        // @formatter:on
        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Test to read an entry containing base64 encoded attribute.
     *
     * @throws Exception
     */
    @Test
    public void testReadEntryBase64Encoded() throws Exception {
        // @formatter:off
        final LDIFEntryReader reader = new LDIFEntryReader(Arrays.asList(
            "version: 1",
            "dn: cn=Gern Jensen, ou=Product Testing, dc=airius, dc=com",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Gern Jensen",
            "cn: Gern O Jensen",
            "sn: Jensen",
            "uid: gernj",
            "telephonenumber: +1 408 555 1212",
            "description:: V2hhdCBhIGNhcmVmdWwgcmVhZGVyIHlvdSBhcmUhICBUaGlzIHZhbHVl"
            + "IGlzIGJhc2UtNjQtZW5jb2RlZCBiZWNhdXNlIGl0IGhhcyBhIGNvbnRyb2wgY2hhcmFjdG"
            + "VyIGluIGl0IChhIENSKS4NICBCeSB0aGUgd2F5LCB5b3Ugc2hvdWxkIHJlYWxseSBnZXQg"
            + "b3V0IG1vcmUu")
        );
        // @formatter:on

        try {
            assertThat(reader.hasNext());
            final Entry entry = reader.readEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getAttributeCount()).isEqualTo(6);
            // Verifying second occurrence of is not taken into account
            assertThat(entry.getAttribute("cn").firstValueAsString()).isEqualTo("Gern Jensen");
            // Verifying decoding is enabled on description attribute
            assertThat(entry.getAttribute("description").firstValueAsString())
                    .isNotSameAs(
                            "V2hhdCBhIGNhcmVmdWwgcmVhZGVyIHlvdSBhcmUhICBUaGlzIHZhbHVl"
                                    + "IGlzIGJhc2UtNjQtZW5jb2RlZCBiZWNhdXNlIGl0IGhhcyBhIGNvbnRyb2wgY2hhcmFjdG"
                                    + "VyIGluIGl0IChhIENSKS4NICBCeSB0aGUgd2F5LCB5b3Ugc2hvdWxkIHJlYWxseSBnZXQg"
                                    + "b3V0IG1vcmUu");
            assertThat(entry.getAttribute("description").firstValueAsString()).contains(
                    "What a careful reader you are!");
        } finally {
            reader.close();
        }
    }

    /**
     * Test to read an entry containing base64 encoded attribute.
     *
     * @throws Exception
     */
    @Test
    public void testReadEntryBase64EncodedDN() throws Exception {
        // @formatter:off
        final LDIFEntryReader reader = new LDIFEntryReader(Arrays.asList(
            "dn::  dWlkPXJvZ2FzYXdhcmEsb3U95Za25qWt6YOoLG89QWlyaXVz", // adding space before ok, after : ko
            "# dn:: uid=<uid>,ou=<JapaneseOU>,o=Airius",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Gern Jensen",
            "cn: Gern O Jensen",
            "sn: Jensen",
            "uid: gernj"
        ));
        // @formatter:on
        try {
            assertThat(reader.hasNext());
            final Entry entry = reader.readEntry();
            assertThat(reader.hasNext()).isFalse();
            assertThat(entry.getName().toString()).isEqualTo("uid=rogasawara,ou=営業部,o=Airius");
        } finally {
            reader.close();
        }
    }

    /**
     * Test to read an entry containing base64 encoded DN. DN base64 encoded is
     * malformed. Must throw an error.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testReadEntryBase64EncodedDNMalformedThrowsError() throws Exception {

        // @formatter:off
        final LDIFEntryReader reader = new LDIFEntryReader(Arrays.asList(
            "dn:: dWlkPXJvZ2FzYXdh!!!OOOpppps!!!25qWt6YOoLG89QWlyaXVz",
            "# dn:: uid=<uid>,ou=<JapaneseOU>,o=Airius",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Gern Jensen",
            "cn: Gern O Jensen",
            "sn: Jensen",
            "uid: gernj"
        ));
        // @formatter:on

        try {
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Test LDIFEntryReader reading a LDIF entry via EntryAsArray.
     *
     * @throws Exception
     */
    @Test
    public void testLDIFEntryReaderEntryAsArray() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader(Arrays.asList(getStandardEntry()));

        try {
            assertThat(reader.hasNext());
            assertThat(reader.readEntry().getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFEntryReader cause NullPointerException when InputStream is null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testLDIFEntryReaderInpuStreamDoesntAllowNull() throws Exception {
        final LDIFEntryReader reader = new LDIFEntryReader((InputStream) null);
        reader.close();
    }

    /**
     * LDIFEntryReader read cause IOException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = IOException.class)
    public void testReadEntryThrowsIOException() throws Exception {

        final FileInputStream mockIn = mock(FileInputStream.class);
        final LDIFEntryReader reader = new LDIFEntryReader(mockIn);

        doThrow(new IOException()).when(mockIn).read();
        try {
            reader.readEntry();
        } finally {
            reader.close();
            verify(mockIn, times(1)).close();
        }
    }

    /**
     * LDIFEntryReader ValueOfLDIFEntry - Multiple change records found.
     * Exception LocalizedIllegalArgumentException expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testValueOfLDIFEntryMultipleChangeRecordFound() throws Exception {

        // @formatter:off
        LDIFEntryReader.valueOfLDIFEntry(
            "#This is an example test",
            "dn: CN=John Smith,OU=Legal,DC=example,DC=com",
            "changetype: modify",
            "replace:employeeID",
            "employeeID: 1234",
            "",
            "dn: CN=Jane Smith,OU=Accounting,DC=example,DC=com",
            "changetype: modify",
            "replace:employeeID",
            "employeeID: 5678"
        );
        // @formatter:on
    }

    /**
     * LDIFEntryReader ValueOfLDIFEntry throws exception when a single comment
     * inserted.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testValueOfLDIFEntryThrowsExceptionIfOnlyAComment() throws Exception {
        LDIFEntryReader.valueOfLDIFEntry("#This is an example test");
    }

    /**
     * Test of valueOfLDIFEntry using malformed LDIF. Must return an
     * LocalizedIllegalArgumentException In this case, dn is missing.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testValueOfLDIFEntryMalformedEntry() throws Exception {

        // @formatter:off
        LDIFEntryReader.valueOfLDIFEntry(
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson"
        );
        // @formatter:on
    }

    /**
     * Test of valueOfLDIFEntry using well formed entry.
     *
     * @throws Exception
     */
    @Test
    public void testValueOfLDIFEntryWellFormedEntry() throws Exception {
        // @formatter:off
        final Entry entry = LDIFEntryReader.valueOfLDIFEntry(
                "dn: uid=user.0,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalperson",
                "objectClass: inetorgperson"
        );
        // @formatter:on

        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttributeCount()).isEqualTo(1);
    }

    /**
     * Test LDIFEntryReader valueOfLDIFEntry on the standard entry and verify if
     * all the attributes are well read.
     *
     * @throws Exception
     */
    @Test
    public void testValueOfLDIFEntryReadStandardEntry() throws Exception {
        final Entry entry = LDIFEntryReader.valueOfLDIFEntry(getStandardEntry());

        assertThat(entry).isNotNull();
        assertThat(entry.getName()).isNotNull();
        assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(entry.getAttribute("sn").firstValue().toString()).isEqualTo("Amar");
        assertThat(entry.getAttributeCount()).isEqualTo(nbStandardEntryAttributes);
    }

    /**
     * LDIFReader valueOfLDIFEntry doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testValueOfLDIFEntryDoesntAllowNull() throws Exception {
        LDIFEntryReader.valueOfLDIFEntry((String[]) null);
    }
}
