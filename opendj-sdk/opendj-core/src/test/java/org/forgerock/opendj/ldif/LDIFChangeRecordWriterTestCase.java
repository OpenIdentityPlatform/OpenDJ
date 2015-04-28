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
 *      Copyright 2011-2015 ForgeRock AS
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.controls.PersistentSearchChangeType;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * This class tests the LDIFChangeRecordWriter functionality.
 */
@SuppressWarnings("javadoc")
public class LDIFChangeRecordWriterTestCase extends AbstractLDIFTestCase {

    /**
     * Provide a standard LDIF Change Record, valid, for tests below. 1 dn + 1
     * changetype + 11 attributes.
     *
     * @return a string containing a standard LDIF Change Record.
     */
    public final String[] getAddLDIFChangeRecord() {
        // @formatter:off
        return new String[] {
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: add",
            "sn: Carter",
            "cn: Samantha Carter",
            "givenName: Sam",
            "objectClass: inetOrgPerson",
            "telephoneNumber: 555 555-5555",
            "mail: scarter@mail.org",
            "entryDN: uid=scarter,ou=people,dc=example,dc=org",
            "entryUUID: ad55a34a-763f-358f-93f9-da86f9ecd9e4",
            "modifyTimestamp: 20120903142126Z",
            "modifiersName: cn=Internal Client,cn=Root DNs,cn=config",
            "description::V2hhdCBhIGNhcmVmdWwgcmVhZGVyIHlvdSBhcmUgIQ=="
        };
        // @formatter:on
    }

    /**
     * Test to write a record excluding all operational attributes
     * setExcludeAllOperationalAttributes is forced to true.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAllOperationalAttributesTrue() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setExcludeAllOperationalAttributes(true);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        for (String line : actual) {
            assertThat(line).doesNotContain("entryUUID");
            assertThat(line).doesNotContain("entryDN");
        }
        assertThat(actual.size()).isEqualTo(10);
    }

    /**
     * Test to write a record excluding all operational attributes
     * setExcludeAllOperationalAttributes is false. All lines must be written
     * plus an empty line.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAllOperationalAttributesFalse() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setExcludeAllOperationalAttributes(false);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isEqualTo(14);
        assertThat(actual.get(13)).isEqualTo("");
    }

    /**
     * Test to write a record excluding user attributes true. dn, changetype,
     * operational attributes and empty line must be written.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeAllUserAttributesTrue() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setExcludeAllUserAttributes(true);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isEqualTo(7);
        assertThat(actual.get(6)).isEqualTo("");
    }

    /**
     * Test to write a record excluding user attributes Default case - the
     * record must be written.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAllUserAttributesFalse() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setExcludeAllUserAttributes(false);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isEqualTo(14);
        assertThat(actual.get(13)).isEqualTo("");
    }

    /**
     * Test setExcludeAttribute method of LDIFChangeRecordWriter Throws a
     * NullPointerException if the attributeDescription is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetExcludeAttributeDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        try {
            writer.setExcludeAttribute(null);
        } finally {
            writer.close();
        }
    }

    /**
     * Test SetExcludeBranch method of LDIFChangeRecordWriter.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeBranchWrongDN() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final DN dn = DN.valueOf("dc=example.com");

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        // Even if DN is wrong then record must be write.
        assertThat(actual.size()).isEqualTo(14);
        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");

    }

    /**
     * Test SetExcludeBranch method of LDIFChangeRecordWriter.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeBranchWithNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final DN dn = DN.valueOf("dc=example,dc=com");

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        // No values expected - we have excluded the branch.
        Assert.assertFalse(actual.size() > 0);
    }

    /**
     * Test SetExcludeBranch method of LDIFChangeRecordWriter.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeBranchWithMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final DN dn = DN.valueOf("dc=example,dc=org");

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        // The record must be written
        assertThat(actual.size()).isEqualTo(14);
        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
    }

    /**
     * Test SetExcludeBranch method of LDIFChangeRecordWriter Throws a
     * NullPointerException if the excludeBranch is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetExcludeBranchDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        try {
            writer.setExcludeBranch(null);
        } finally {
            writer.close();
        }
    }

    /**
     * Test to write an LDIFChangeRecordWriter with attribute exclusions.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAttributeWithMatch() throws Exception {
        final AttributeDescription attribute = AttributeDescription.valueOf("cn");
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setExcludeAttribute(attribute);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isEqualTo(13);
        for (String line : actual) {
            // we have excluded this attribute especially.
            assertThat(line).doesNotContain("cn: Samantha Carter");
        }
    }

    /**
     * Test to write an LDIFChangeRecordWriter with attribute exclusions. In
     * this case, vip attribute is not present in the example. All lines must be
     * written.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAttributeWithNoMatch() throws Exception {
        final AttributeDescription attribute = AttributeDescription.valueOf("vip");
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setExcludeAttribute(attribute);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isEqualTo(14);
        for (String line : actual) {
            // we have excluded this attribute especially.
            assertThat(line).doesNotContain("vip");
        }
    }

    /**
     * Test SetIncludeAttribute method of LDIFChangeRecordWriter. Inserting
     * attribute cn (common name) & sn (surname).
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeAttributeWithMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());

        writer.setIncludeAttribute(AttributeDescription.valueOf("cn"));
        writer.setIncludeAttribute(AttributeDescription.valueOf("sn"));
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: add");
        assertThat(actual.get(2)).contains("sn: Carter");
        assertThat(actual.get(3)).contains("cn: ");
        assertThat(actual.get(4)).contains("");
    }

    /**
     * Test SetIncludeAttribute method of LDIFChangeRecordWriter. Inserting
     * attribute cn (common name) & sn (surname)
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeAttributeWithNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());

        writer.setIncludeAttribute(AttributeDescription.valueOf("vip"));
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: add");
        assertThat(actual.get(2)).contains("");
    }

    /**
     * Test SetIncludeAttribute method of LDIFChangeRecordWriter Throws a
     * NullPointerException if the attributeDescription is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeAttributeDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        try {
            writer.setIncludeAttribute(null);
        } finally {
            writer.close();
        }
    }

    /**
     * Test SetIncludeBranch method of LDIFChangeRecordWriter DN included is
     * "dc=example,dc=com", which is not the one from the record. Record must
     * not be written.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */    @Test
    public void testSetIncludeBranchWithNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final DN dn = DN.valueOf("dc=example,dc=org");

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setIncludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        // No result expected
        assertThat(actual.size()).isEqualTo(0);
    }

    /**
     * Test SetIncludeBranch method of LDIFChangeRecordWriter verifying right
     * data are present.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeBranchWithMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final DN dn = DN.valueOf("dc=example,dc=com");

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.setIncludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        // Must contains all the attributes
        assertThat(actual.get(0)).contains("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isEqualTo(14);
    }

    /**
     * Test SetIncludeBranch method of LDIFChangeRecordWriter Throws a
     * NullPointerException if the includeBranch is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeBranchDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        try {
            writer.setIncludeBranch(null);
        } finally {
            writer.close();
        }
    }

    /**
     * Test to write a record adding the user friendly Comment.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(enabled = false)
    public void testSetAddUserFriendlyComments() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final CharSequence comment = "A simple comment";

        writer.setAddUserFriendlyComments(true);
        writer.writeComment0(comment);
        writer.close();
    }

    /**
     * Test WriteComment method of LDIFChangeRecordWriter using the wrap
     * function.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteCommentUsingTheWrapFunction() throws Exception {
        final CharSequence comment = "Lorem ipsum dolor sit amet, consectetur adipisicing elit";

        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        int wrapColumn = 15;
        writer.setWrapColumn(wrapColumn);
        writer.writeComment(comment);
        writer.close();

        for (String line : actual) {
            // The line length <= writer.wrapColumn
            assertThat(line.length()).isLessThanOrEqualTo(wrapColumn);
            // Each line started with #
            assertThat(line.startsWith("#")).isTrue();
        }
    }

    /**
     * Test WriteComment method of LDIFChangeRecordWriter using the wrap
     * function. set wrap to 0.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteCommentUsingTheWrapFunctionShortComment() throws Exception {
        final CharSequence comment = "Lorem ipsum dolor";

        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        int wrapColumn = 30;
        writer.setWrapColumn(wrapColumn);
        writer.writeComment(comment);
        writer.close();

        for (String line : actual) {
            // Each line started with #
            assertThat(line.startsWith("#")).isTrue();
            assertThat(line.length()).isLessThanOrEqualTo(wrapColumn);
        }
    }

    /**
     * Test WriteComment method of LDIFChangeRecordWriter using the wrap
     * function. The comment doesn't contain any empty spaces.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteCommentUsingTheWrapFunctionNoEmptySpace() throws Exception {
        final CharSequence comment = "Lorem ipsumdolorsitamet,consecteturadipisicingelit";

        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        int wrapColumn = 15;
        writer.setWrapColumn(wrapColumn);
        writer.writeComment(comment);
        writer.close();

        for (String line : actual) {
            // The line length <= writer.wrapColumn
            assertThat(line.length()).isLessThanOrEqualTo(wrapColumn);
            // Each line started with #
            assertThat(line.startsWith("#")).isTrue();
        }
    }

    /**
     * Write an ChangeRecord add type LDIF.
     *
     * @throws Exception
     */
    @Test
    public void testWriteChangeRecord() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ChangeRecord changeRequest = Requests.newChangeRecord(
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: add",
            "sn: Carter"
        );
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: add");
        assertThat(actual.get(2)).isEqualTo("sn: Carter");
        assertThat(actual.get(3)).isEqualTo("");
    }

    /**
     * Write an AddRequestChange LDIF.
     *
     * @throws Exception
     */
    @Test
    public void testWriteAddRequest() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final AddRequest changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isEqualTo(14);
    }

    /**
     * Write an ChangeRecord LDIF.
     *
     * @throws Exception
     */
    @Test
    public void testWriteAddRequestNoBranchExcluded() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final DN dnAdd = DN.valueOf("uid=scarter,ou=People,dc=example,dc=com");

        // @formatter:off
        final ChangeRecord changeRequest = Requests.newAddRequest(dnAdd)
                .addAttribute("sn", "Carter");
        // @formatter:on

        final DN dn = DN.valueOf("dc=example,dc=org");
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(4);
        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: add");
        assertThat(actual.get(2)).isEqualTo("sn: Carter");
    }

    /**
     * Write an ChangeRecord LDIF.
     *
     * @throws Exception
     */
    @Test
    public void testWriteAddRequestBranchExcluded() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        final DN dnAdd = DN.valueOf("uid=scarter,ou=People,dc=example,dc=com");

        // @formatter:off
        final ChangeRecord changeRequest = Requests.newAddRequest(dnAdd)
                .addAttribute("sn", "Carter");
        // @formatter:on

        final DN dn = DN.valueOf("dc=example,dc=com");
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(0);
    }

    /**
     * Test to write a change record containing an URL.
     *
     * @throws Exception
     */
    @Test
    public void testWriteAddRequestJpegAttributeOk() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        final File file = File.createTempFile("sdk", ".jpeg");
        final String url = file.toURI().toURL().toString();

        final DN dnAdd = DN.valueOf("uid=scarter,ou=People,dc=example,dc=com");

        // @formatter:off
        final ChangeRecord changeRequest = Requests.newAddRequest(dnAdd)
                .addAttribute("sn", "Carter")
                .addAttribute("jpegphoto", url);
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        file.delete();
        writer.close();

        assertThat(actual.size()).isEqualTo(5);
        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: add");
        assertThat(actual.get(2)).isEqualTo("sn: Carter");
        assertThat(actual.get(3)).contains("jpegphoto: file:/");

    }

    /**
     * Write an AddRequestChange LDIF. The dn/sn is base64 encoded, and contain
     * ascii chars. If they aren't containing ascii, they will not be
     * translated.
     *
     * @throws Exception
     */
    @Test
    public void testWriteAddBinaryRequest() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final AddRequest changeRequest = Requests.newAddRequest(
            "dn:: dWlkPXJvZ2FzYXdhcmE=",
            "changetype: add",
            "sn::cm9nYXNhd2FyYQ=="
        );
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=rogasawara");
        assertThat(actual.get(2)).isEqualTo("sn: rogasawara");
        assertThat(actual.get(3)).isEqualTo("");
        assertThat(actual.size()).isEqualTo(4);
    }

    /**
     * Write an AddRequestChange LDIF. The dn/sn is base64 encoded, and contain
     * ascii chars. If they aren't containing ascii, they will not be
     * translated. In this case dn: uid=rogasawara,ou=å–¶æ¥­éƒ¨,o=Airius
     *
     * @throws Exception
     */
    @Test
    public void testWriteAddBinaryNonAsciiRequest() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final AddRequest changeRequest = Requests.newAddRequest(
            "dn:: dWlkPXJvZ2FzYXdhcmEsb3U95Za25qWt6YOoLG89QWlyaXVz",
            "changetype: add",
            "sn::cm9nYXNhd2FyYQ=="
        );
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0))
                .isEqualTo("dn:: dWlkPXJvZ2FzYXdhcmEsb3U95Za25qWt6YOoLG89QWlyaXVz");
        assertThat(actual.get(2)).isEqualTo("sn: rogasawara");
        assertThat(actual.get(3)).isEqualTo("");
        assertThat(actual.size()).isEqualTo(4);
    }

    /**
     * Write an DeleteRequest LDIF. Branch is excluded. The record musn't be
     * written.
     *
     * @throws Exception
     */
    @Test
    public void testWriteDeleteRequestBranchExcluded() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        // @formatter:off
        final DeleteRequest changeRequest = (DeleteRequest) Requests.newChangeRecord(
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on
        final DN dn = DN.valueOf("dc=example,dc=com");
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(0);
    }

    /**
     * Write an DeleteRequest LDIF. Branch is not excluded.
     *
     * @throws Exception
     */
    @Test
    public void testWriteDeleteRequestBranchNotExcluded() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        // @formatter:off
        final DeleteRequest changeRequest = (DeleteRequest) Requests.newChangeRecord(
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on
        final DN dn = DN.valueOf("dc=example,dc=org");
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(3);
        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: delete");
        assertThat(actual.get(2)).isEqualTo("");

    }

    /**
     * Write a delete request.
     *
     * @throws Exception
     */
    @Test
    public void testWriteDeleteRequest() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final DeleteRequest changeRequest = (DeleteRequest) Requests.newChangeRecord(
            "# Delete an existing entry",
            "dn: cn=Robert Jensen, ou=Marketing, dc=airius, dc=com",
            "changetype: delete"
        );
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0))
                .isEqualTo("dn: cn=Robert Jensen, ou=Marketing, dc=airius, dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: delete");
    }

    /**
     * A delete Record with a control.
     *
     * @throws Exception
     */
    @Test
    public void testWriteDeleteRequestContainingControl() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        final DN dn = DN.valueOf("uid=scarter,ou=People,dc=example,dc=com");

        // @formatter:off
        final DeleteRequest changeRequest = Requests.newDeleteRequest(dn)
            .addControl(PersistentSearchRequestControl.newControl(
                true, true, true, // isCritical, changesOnly, returnECs
                PersistentSearchChangeType.ADD,
                PersistentSearchChangeType.DELETE,
                PersistentSearchChangeType.MODIFY,
                PersistentSearchChangeType.MODIFY_DN
            )
            );
        // @formatter:on

        writer.writeComment("This record contains a control");
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("# This record contains a control");
        assertThat(actual.get(1)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.get(2)).contains("control: 2.16.840.1.113730.3.4.3 true");
        assertThat(actual.get(3)).isEqualTo("changetype: delete");
        assertThat(actual.get(4)).isEqualTo("");
    }

    /**
     * Write a delete request with illegal argu;ent : the following example is
     * containing additional lines after the changetype when none were expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testWriteDeleteRequestIllegalArguments() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final DeleteRequest changeRequest = (DeleteRequest) Requests.newChangeRecord(
            "# Delete an existing entry",
            "dn: cn=Robert Jensen, ou=Marketing, dc=airius, dc=com",
            "changetype: delete",
            "dn: cn=Robert , ou=Marketing, dc=airius, dc=com",
            "changetype: delete"
        );
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0))
                .isEqualTo("dn: cn=Robert Jensen, ou=Marketing, dc=airius, dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: delete");
    }

    /**
     * Write an ChangeRecord Moddn LDIF.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModdnRequest() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyDNRequest changeRequest =
            Requests.newModifyDNRequest("uid=scarter,ou=People,dc=example,dc=com", "cn=carter");
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(5);
        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: modrdn");
        assertThat(actual.get(2)).isEqualTo("newrdn: cn=carter");
        assertThat(actual.get(3)).isEqualTo("deleteoldrdn: 0");

    }

    /**
     * Write an ChangeRecord Moddn LDIF.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModdnRequestNewSuperior() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        // @formatter:off
        final ModifyDNRequest changeRequest = (ModifyDNRequest) Requests.newChangeRecord(
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: moddn",
            "newrdn: cn=carter",
            "deleteoldrdn: true",
            "newsuperior:   ou=People,dc=example,dc=org"
        );
        // @formatter:on
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isEqualTo(6);
        assertThat(actual.get(3)).isEqualTo("deleteoldrdn: 1");
        assertThat(actual.get(4)).isEqualTo("newsuperior: ou=People,dc=example,dc=org");
    }

    /**
     * Write a Moddn request.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModdnRequestDeleterdnFalse() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        // @formatter:off
        final ModifyDNRequest changeRequest = (ModifyDNRequest) Requests.newChangeRecord(
            "version: 1",
            "",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: moddn",
            "newrdn: cn=carter",
            "deleteoldrdn: false"
        );
        // @formatter:on
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isEqualTo(5);
        assertThat(actual.get(3)).isEqualTo("deleteoldrdn: 0");
    }

    /**
     * Write a modify request.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModifyRequest() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyRequest changeRequest = Requests.newModifyRequest(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modify",
            "add: work-phone",
            "work-phone: 650/506-7000"
        );

        // @formatter:on
        writer.writeChangeRecord(changeRequest);
        writer.close();

        // version number is skipped.
        assertThat(actual.get(0)).isEqualTo("dn: cn=scarter,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: modify");
        assertThat(actual.get(2)).isEqualTo("add: work-phone");
        assertThat(actual.get(3)).isEqualTo("work-phone: 650/506-7000");
        assertThat(actual.get(4)).isEqualTo("-");
    }

    /**
     * Write a modify request containing a control.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModifyRequestUsingControl() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyRequest changeRequest = Requests.newModifyRequest("cn=scarter,dc=example,dc=com")
                .addControl(PreReadRequestControl.newControl(true, "mail"))
                .addModification(
                        ModificationType.REPLACE, "mail", "modified@example.com");
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(7);
        assertThat(actual.get(0)).isEqualTo("dn: cn=scarter,dc=example,dc=com");
        assertThat(actual.get(1)).contains("control: 1.3.6.1.1.13.1 true:");
        assertThat(actual.get(2)).isEqualTo("changetype: modify");
        assertThat(actual.get(3)).isEqualTo("replace: mail");
        assertThat(actual.get(4)).isEqualTo("mail: modified@example.com");
        assertThat(actual.get(5)).isEqualTo("-");
    }

    /**
     * Write an ModifyRequest LDIF.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModifyRequestNoModifications() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyRequest changeRequest = Requests.newModifyRequest(
            "version: 1",
            "",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modify"
        );
        // @formatter:on
        writer.writeChangeRecord(changeRequest);
        writer.close();

        // No changes, nothing to do, the record is not written.
        assertThat(actual.size()).isEqualTo(0);
    }

    /**
     * Write a modify request using an exclusion filter attribute.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModifyRequestFilterAttributesExcluded() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyRequest changeRequest = (ModifyRequest) Requests.newChangeRecord(
            "version: 1",
            "",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modify",
            "replace: work-phone",
            "work-phone: 555-555-1155"
        );
        // @formatter:on

        writer.setExcludeAttribute(AttributeDescription.valueOf("work-phone"));
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(3);
        assertThat(actual.get(0)).isEqualTo("dn: cn=scarter,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: modify");
        assertThat(actual.get(2)).isEqualTo("");
    }

    /**
     * Write a modify request using branch exclusion.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModifyRequestBranchExcludedNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyRequest changeRequest = (ModifyRequest) Requests.newChangeRecord(
            "version: 1",
            "",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modify",
            "replace: work-phone",
            "work-phone: 555-555-1155"
        );
        // @formatter:on

        final DN dn = DN.valueOf("dc=example,dc=org");
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(6); // all line plus a ""
        assertThat(actual.get(0)).isEqualTo("dn: cn=scarter,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: modify");
        assertThat(actual.get(5)).isEqualTo("");
    }

    /**
     * Write a modify request using branch exclusion.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModifyRequestBranchExcludedMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyRequest changeRequest = (ModifyRequest) Requests.newChangeRecord(
            "version: 1",
            "",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modify",
            "replace: work-phone",
            "work-phone: 555-555-1155"
        );
        // @formatter:on

        final DN dn = DN.valueOf("dc=example,dc=com");
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(0);
    }

    /**
     * Write a modifyDN request with a branch exclusion.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModifyDNRequestBranchExcludedNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyDNRequest changeRequest = (ModifyDNRequest) Requests.newChangeRecord(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: cn=Susan Jacobs",
            "deleteoldrdn: no"
        );
        // @formatter:on
        final DN dn = DN.valueOf("dc=example,dc=org");
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(5);
        assertThat(actual.get(0)).isEqualTo("dn: cn=scarter,dc=example,dc=com");
        assertThat(actual.get(3)).isEqualTo("deleteoldrdn: 0");
    }

    /**
     * Write a modifyDN request with a branch exclusion.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModifyDNRequestBranchExcludedMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyDNRequest changeRequest = (ModifyDNRequest) Requests.newChangeRecord(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: cn=Susan Jacobs",
            "deleteoldrdn: no"
        );
        // @formatter:on

        final DN dn = DN.valueOf("dc=example,dc=com");
        writer.setExcludeBranch(dn);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(0);
    }

    /**
     * Write a modifyDN request.
     *
     * @throws Exception
     */
    @Test
    public void testWriteModifyDNRequest() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        // @formatter:off
        final ModifyDNRequest changeRequest =
                Requests.newModifyDNRequest("cn=scarter,dc=example,dc=com", "cn=Susan Jacobs")
                .setDeleteOldRDN(true);
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.size()).isEqualTo(5);
        assertThat(actual.get(0)).isEqualTo("dn: cn=scarter,dc=example,dc=com");
        assertThat(actual.get(1)).isEqualTo("changetype: modrdn");
        assertThat(actual.get(2)).isEqualTo("newrdn: cn=Susan Jacobs");
        assertThat(actual.get(3)).isEqualTo("deleteoldrdn: 1");
    }

    /**
     * Write a full example containing multiple change records.
     *
     * @throws Exception
     */
    @Test
    public void testWriteMultipleChangeRecords() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        final ChangeRecord changeRequest = Requests.newAddRequest(getAddLDIFChangeRecord());

        // @formatter:off
        final ModifyDNRequest changeRequest2 =
                Requests.newModifyDNRequest("cn=scarter,dc=example,dc=com", "cn=Susan Jacobs")
                .setDeleteOldRDN(false);
        // @formatter:on

        // @formatter:off
        final ModifyRequest changeRequest3 = Requests.newModifyRequest(
            "version: 1",
            "",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modify",
            "replace: work-phone",
            "work-phone: 555-555-1155"
        );
        // @formatter:on

        // @formatter:off
        final AddRequest changeRequest4 = Requests.newAddRequest(
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: add",
            "sn: Carter"
        );
        // @formatter:on

        final File file = File.createTempFile("sdk", null);
        final String url = file.toURI().toURL().toString();

        // @formatter:off
        final AddRequest changeRequest5 = Requests.newAddRequest(
            "version: 1",
            "# Add a new record",
            "dn: cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com",
            "changetype: add",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Fiona Jensen",
            "sn: Jensen",
            "uid: fiona",
            "telephonenumber: +1 408 555 1212",
            "jpegphoto:< " + url
        );
        // @formatter:on

        writer.writeChangeRecord(changeRequest);
        writer.writeChangeRecord(changeRequest2);
        writer.writeChangeRecord(changeRequest3);
        writer.writeChangeRecord(changeRequest4);
        writer.writeComment("A comment...");
        writer.writeChangeRecord(changeRequest5);
        writer.close();

        assertThat(actual.size()).isGreaterThan(10);
        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.get(actual.size() - 1)).isEqualTo("");

        file.delete();
    }

    /**
     * Write a record containing multiple changes.
     *
     * @throws Exception
     */
    @Test
    public void testWriteMultipleChangesRecord() throws Exception {
        // @formatter:off
        final ChangeRecord changeRequest = Requests.newChangeRecord(
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "add: work-phone",
            "work-phone: 650/506-7000",
            "work-phone: 650/506-7001",
            "-",
            "delete: home-fax",
            "-",
            "replace: home-phone",
            "home-phone: 415/697-8899"
        );
        // @formatter:on
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: uid=scarter,ou=People,dc=example,dc=com");
        assertThat(actual.size()).isGreaterThan(10);
    }

    /**
     * Test to write a simple comment with the LDFChangeRecordWriter.
     *
     * @throws Exception
     */
    @Test
    public void testWriteComment() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(actual);

        writer.writeComment("TLDIFChangeRecordWriter, this is a comment.");
        writer.close();

        assertThat(actual.get(0)).isEqualTo("# TLDIFChangeRecordWriter, this is a comment.");
    }

    /**
     * Verify the LDIFWriteChangeRecord write and correctly flush and close.
     *
     * @throws Exception
     */
    @Test
    public void testWriteChangeRecordFlushClose() throws Exception {
        final OutputStream mockOutput = mock(OutputStream.class);
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(mockOutput);
        try {
            writer.writeComment("TLDIFChangeRecordWriter, this is a comment.");
            writer.flush();
            writer.flush();
            verify(mockOutput, times(2)).flush();
        } finally {
            writer.close();
            verify(mockOutput, times(1)).close();
        }
    }

    /**
     * Test the LDIFWriteChangeRecord using an output file verifying write is
     * correctly invoked.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteEntryOutputStreamUsingByteArrayOutputStream() throws Exception {
        final OutputStream out = new ByteArrayOutputStream();
        final LDIFChangeRecordWriter writer = new LDIFChangeRecordWriter(out);

        // @formatter:off
        final String[] lines = {
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: add",
            "sn: Carter"
        };
        // @formatter:on

        final AddRequest changeRequest = Requests.newAddRequest(lines);
        writer.writeChangeRecord(changeRequest);
        writer.close();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new ByteArrayInputStream(out.toString().getBytes())));

        assertThat(reader.readLine()).isEqualTo(lines[0]);
        assertThat(reader.readLine()).isEqualTo(lines[1]);
        assertThat(reader.readLine()).isEqualTo(lines[2]);
        assertThat(reader.readLine()).isEqualTo("");
        assertThat(reader.readLine()).isNull();
    }
}
