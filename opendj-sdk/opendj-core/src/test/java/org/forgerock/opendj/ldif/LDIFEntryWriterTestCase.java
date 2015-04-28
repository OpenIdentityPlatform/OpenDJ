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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Matcher;
import org.testng.Assert;
import org.testng.annotations.Test;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * This class tests the LDIFEntryWriter functionality.
 */
public final class LDIFEntryWriterTestCase extends AbstractLDIFTestCase {

    /**
     * Standard entry used for the following tests.
     *
     * @return an Entry with pre-defined attributes
     */
    private static Entry getStandardEntry() {
        final Entry entry = new LinkedHashMapEntry("cn=John Doe,ou=people,dc=example,dc=com");
        entry.addAttribute("objectClass", "top", "person", "inetOrgPerson");
        entry.addAttribute("cn", "John Doe");
        entry.addAttribute("sn", "Doe");
        entry.addAttribute("age", "29");
        entry.addAttribute("givenName", "John");
        entry.addAttribute("description", "one two", "three four",
                "This is a very very long description, Neque porro quisquam est qui dolorem ipsum"
                + "quia dolor sit amet, consectetur, adipisci velit...");
        entry.addAttribute("typeOnly");
        entry.addAttribute("mail", "email@example.com");
        entry.addAttribute("localized;lang-fr", "\u00e7edilla");
        entry.addAttribute("entryUUID", "ad55a34a-763f-358f-93f9-da86f9ecd9e4");
        entry.addAttribute("entryDN", "uid=bjensen,ou=people,dc=example,dc=com");
        return entry;
    }

    /**
     * Test setExcludeAttribute method of LDIFEntryWriter Throws a
     * NullPointerException if the attributeDescription is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetExcludeAttributeDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        writer.setExcludeAttribute(null);
        writer.close();
    }

    /**
     * Test to write an entry with attribute exclusions.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAttributeWithMatch() throws Exception {
        final AttributeDescription attribute = AttributeDescription.valueOf("cn");
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        writer.setExcludeAttribute(attribute);

        writer.writeEntry(getStandardEntry());
        writer.close();

        assertThat(actual.size()).isGreaterThan(0);
        for (String line : actual) {
            // we have excluded this attribute especially
            assertThat(line).doesNotContain("cn: John Doe");
        }
    }

    /**
     * Test to write an entry with attribute exclusions. In this test, the
     * attribute description 'vip' doesn't exist then the entry must be written
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAttributeWithNoMatch() throws Exception {
        final AttributeDescription attribute = AttributeDescription.valueOf("vip");
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        writer.setExcludeAttribute(attribute);

        writer.writeEntry(getStandardEntry());
        writer.close();

        assertThat(actual.size()).isGreaterThan(0);
        for (String line : actual) {
            // we have excluded this attribute especially
            assertThat(line).doesNotContain("vip");
        }
        assertThat(actual.size()).isGreaterThan(getStandardEntry().getAttributeCount());
    }

    /**
     * Test SetExcludeBranch method of LDIFEntryWriter Throws a
     * NullPointerException if the excludeBranch is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetExcludeBranchDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        writer.setExcludeBranch(null);
        writer.close();
    }

    /**
     * Test SetExcludeBranch method of LDIFEntryWriter.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeBranchWrongDN() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        DN dn = DN.valueOf("dc=example.com");

        writer.setExcludeBranch(dn);
        writer.writeEntry(getStandardEntry());
        writer.flush();
        writer.close();
        // Even if DN is wrong then entry is expected
        assertThat(actual.size()).isGreaterThan(getStandardEntry().getAttributeCount());

    }

    /**
     * Test SetExcludeBranch method of LDIFEntryWriter.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeBranchWithNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        DN dn = DN.valueOf("dc=example,dc=com");

        writer.setExcludeBranch(dn);
        writer.writeEntry(getStandardEntry());
        writer.flush();
        writer.close();
        // No values expected - we have excluded the branch
        Assert.assertFalse(actual.size() > 0);
    }

    /**
     * Test SetExcludeBranch method of LDIFEntryWriter.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeBranchWithMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        DN dn = DN.valueOf("dc=example,dc=org");

        writer.setExcludeBranch(dn);
        writer.writeEntry(getStandardEntry());
        writer.flush();
        writer.close();
        // The entry must be written
        assertThat(actual.size()).isGreaterThan(getStandardEntry().getAttributeCount());
    }

    /**
     * Test SetExcludeFilter method of LDIFEntryWriter Throws a
     * NullPointerException if the excludeFilter is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testsetExcludeFilterDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        writer.setExcludeFilter(null);
        writer.close();
    }

    /**
     * Test testSetExcludeFilter method of LDIFEntryWriter. StandardEntry has an
     * objectclass : person
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeFilterWithMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        final Filter filter = Filter.equality("objectclass", "vip");
        final Matcher excludeFilter = filter.matcher();

        writer.setExcludeFilter(excludeFilter);
        writer.writeEntry(getStandardEntry());
        writer.close();

        // objectclass is 'person' in the example, result must be > 0
        assertThat(actual.get(0)).isEqualTo("dn: " + getStandardEntry().getName());
        assertThat(actual.size()).isGreaterThan(getStandardEntry().getAttributeCount());
    }

    /**
     * Test testSetExcludeFilter method of LDIFEntryWriter StandardEntry has an
     * objectclass : person
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeFilterWithNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        final Filter filter = Filter.equality("objectclass", "person");
        final Matcher excludeFilter = filter.matcher();

        writer.setExcludeFilter(excludeFilter);
        writer.writeEntry(getStandardEntry());
        writer.close();

        // the entry correspond to the filter - must be excluded
        assertThat(actual).isEmpty();
    }

    /**
     * Test SetIncludeAttribute method of LDIFEntryWriter Throws a
     * NullPointerException if the attributeDescription is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeAttributeDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        writer.setIncludeAttribute(null);
        writer.close();
    }

    /**
     * Test SetIncludeAttribute method of LDIFEntryWriter. Inserting attribute
     * cn (common name) & sn (surname)
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeAttributeWithMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        writer.setIncludeAttribute(AttributeDescription.valueOf("cn"));
        writer.setIncludeAttribute(AttributeDescription.valueOf("sn"));
        writer.writeEntry(getStandardEntry());
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: " + getStandardEntry().getName());
        assertThat(actual.get(1)).contains("cn: ");
        assertThat(actual.get(2)).contains("sn: ");
    }

    /**
     * Test SetIncludeAttribute method of LDIFEntryWriter in this example, the
     * field "manager" is not present in the StandardEntry. Then the entry must
     * only write the first line : dn: cn=John Doe,ou=people,dc=example,dc=com
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeAttributeWithNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        writer.setIncludeAttribute(AttributeDescription.valueOf("manager"));
        writer.writeEntry(getStandardEntry());
        writer.close();

        // 1st line is containing DN
        assertThat(actual.get(0)).isEqualTo("dn: " + getStandardEntry().getName());
        // empty second
        assertThat(actual.get(1)).isEmpty();
        // verifying no more than 2 lines written
        assertThat(actual.size()).isLessThanOrEqualTo(2);
    }

    /**
     * Test SetIncludeAttribute method of LDIFEntryWriter. Attempted insertions
     * repeating attributes. An attribute mustn't be written twice or +.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeAttributeWithRepeatedAttributes() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        writer.setIncludeAttribute(AttributeDescription.valueOf("cn"));
        writer.setIncludeAttribute(AttributeDescription.valueOf("sn"));
        writer.setIncludeAttribute(AttributeDescription.valueOf("cn"));
        writer.setIncludeAttribute(AttributeDescription.valueOf("cn"));
        writer.writeEntry(getStandardEntry());
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: " + getStandardEntry().getName());
        assertThat(actual.get(1)).contains("cn: ");
        assertThat(actual.get(2)).contains("sn: ");
        // 3 lines of result + 1 empty line
        assertThat(actual.size()).isLessThanOrEqualTo(4);
    }

    /**
     * Test to write an entry excluding all operational attributes
     * setExcludeAllOperationalAttributes to false (default case)
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAllOperationalAttributesFalse() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        int opAttributes = 0;

        writer.setExcludeAllOperationalAttributes(false);
        writer.writeEntry(getStandardEntry());
        writer.close();

        for (String line : actual) {
            if (line.contains("entryUUID") || line.contains("entryDN")) {
                opAttributes++;
            }
        }
        assertThat(actual.get(0)).isEqualTo("dn: " + getStandardEntry().getName());
        assertThat(actual.size()).isGreaterThan(getStandardEntry().getAttributeCount());

        assertThat(opAttributes).isEqualTo(2);

    }

    /**
     * Test to write an entry excluding all operational attributes
     * setExcludeAllOperationalAttributes is forced to true Result should be dn:
     * cn=John Doe,ou=people,dc=example,dc=com plus an empty line.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAllOperationalAttributesTrue() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        writer.setExcludeAllOperationalAttributes(true);
        writer.writeEntry(getStandardEntry());
        writer.close();

        for (String line : actual) {
            assertThat(line).doesNotContain("entryUUID");
            assertThat(line).doesNotContain("entryDN");
        }

        assertThat(actual.get(0)).isEqualTo("dn: " + getStandardEntry().getName());
        assertThat(actual.size()).isGreaterThan(getStandardEntry().getAttributeCount());
    }

    /**
     * Test to write an entry excluding user attributes Default case - full
     * entry must be written.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAllUserAttributesFalse() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        writer.setExcludeAllUserAttributes(false);
        writer.writeEntry(getStandardEntry());
        writer.flush();
        writer.close();

        assertThat(actual.get(0)).isEqualTo("dn: " + getStandardEntry().getName());
        assertThat(actual.size()).isGreaterThan(getStandardEntry().getAttributeCount());
    }

    /**
     * Test to write an entry excluding user attributes result should be dn:
     * cn=John Doe,ou=people,dc=example,dc=com plus an empty line.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetExcludeAllUserAttributesTrue() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        writer.setExcludeAllUserAttributes(true);
        writer.writeEntry(getStandardEntry());
        writer.flush();
        writer.close();

        for (String line : actual) {
            assertThat(line).doesNotContain("sn");
            assertThat(line).doesNotContain("mail");
        }
        assertThat(actual.get(0).contains("dn: cn=John Doe,ou=people,dc=example,dc=com"));
        assertThat(actual.size()).isLessThan(getStandardEntry().getAttributeCount());
    }

    /**
     * Test SetIncludeBranch method of LDIFEntryWriter Throws a
     * NullPointerException if the includeBranch is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeBranchDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        writer.setIncludeBranch(null);
        writer.close();
    }

    /**
     * Test SetIncludeBranch method of LDIFEntryWriter verifying right data are
     * present.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeBranchWithMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        DN dn = DN.valueOf("dc=example,dc=com");
        writer.setIncludeBranch(dn);

        writer.writeEntry(getStandardEntry());
        writer.close();

        // Must contains all the attributes
        assertThat(actual.get(0)).contains(getStandardEntry().getName().toString());
        assertThat(actual.size()).isGreaterThan(getStandardEntry().getAttributeCount());
    }

    /**
     * Test SetIncludeBranch method of LDIFEntryWriter DN included is
     * "dc=opendj,dc=org", which is not the one from the standard entry Entry
     * must not be written.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeBranchWithNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        DN dn = DN.valueOf("dc=opendj,dc=org");
        writer.setIncludeBranch(dn);

        writer.writeEntry(getStandardEntry());
        writer.close();

        // No result expected
        assertThat(actual.size()).isEqualTo(0);
    }

    /**
     * Test SetIncludeFilter method of LDIFEntryWriter. This example use
     * Filter.equality("objectclass", "vip"); which is not the one from the
     * standard entry.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeFilterWithNoMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        final Filter filter = Filter.equality("objectclass", "vip");
        final Matcher includeFilter = filter.matcher();

        writer.setIncludeFilter(includeFilter);
        writer.writeEntry(getStandardEntry());
        writer.close();

        assertThat(actual.size()).isEqualTo(0);
    }

    /**
     * Test SetIncludeFilter method of LDIFEntryWriter. This example use
     * Filter.equality("objectclass", "person"); which is the one from the
     * standard entry.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testSetIncludeFilterWithMatch() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        final Filter filter = Filter.equality("objectclass", "person");
        final Matcher includeFilter = filter.matcher();

        writer.setIncludeFilter(includeFilter);
        writer.writeEntry(getStandardEntry());
        writer.close();

        // Entry must be written
        assertThat(actual).isNotNull();
        assertThat(actual.size()).isGreaterThanOrEqualTo(getStandardEntry().getAttributeCount());
    }

    /**
     * Test SetIncludeFilter method of LDIFEntryWriter Throws a
     * NullPointerException if the schema is null.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeFilterDoesntAllowNull() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        writer.setIncludeFilter(null);
        writer.writeEntry(getStandardEntry());
        writer.close();
    }

    /**
     * Test WriteComment method of LDIFEntryWriter using the wrap function.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteCommentUsingTheWrapFunction() throws Exception {
        final CharSequence comment = "Lorem ipsum dolor sit amet, consectetur adipisicing elit";

        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

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
     * Test WriteComment method of LDIFEntryWriter using the wrap function. set
     * wrap to 0.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteCommentUsingTheWrapFunctionShortComment() throws Exception {
        final CharSequence comment = "Lorem ipsum dolor";

        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

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
     * Test WriteComment method of LDIFEntryWriter using the wrap function. The
     * comment doesn't contain any empty spaces.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteCommentUsingTheWrapFunctionNoEmptySpace() throws Exception {
        final CharSequence comment = "Lorem ipsumdolorsitamet,consecteturadipisicingelit";

        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

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
     * Test WriteComment method of LDIFEntryWriter.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteComment() throws Exception {
        final CharSequence comment1 = "This is a new comment";
        final CharSequence comment2 = "Another one";

        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        writer.writeComment(comment1);
        writer.writeComment(comment2);
        writer.close();

        // Verifying comments are well written in the LDIF Entry
        Assert.assertEquals(actual.size(), 2);
        assertThat(actual.get(0)).isEqualTo("# " + comment1);
        assertThat(actual.get(1)).isEqualTo("# " + comment2);
    }

    /**
     * Test to write an entry adding the user friendly Comment.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(enabled = false)
    public void testSetAddUserFriendlyComments() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);

        final CharSequence comment = "A simple comment";

        writer.setAddUserFriendlyComments(true);
        writer.writeComment0(comment);
        writer.close();
    }

    /**
     * Tests writeEntry method of LDIFEntryWriter class. Using the
     * getStandardEntry. Attribute description tested, containing a long text.
     * Wrap need to be used is this case.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteEntryUsingStandardEntry() throws Exception {
        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        final int wrapColumn = 15;
        writer.setWrapColumn(wrapColumn);
        writer.writeEntry(getStandardEntry());
        writer.close();

        int countDesc = 0;

        // Entry must be written - check wrap on description
        assertThat(actual).isNotNull();
        for (String line : actual) {
            if (line.contains("description")) {
                countDesc++;
            }
            assertThat(line.length()).isLessThanOrEqualTo(wrapColumn);
        }
        assertThat(countDesc).isEqualTo(getStandardEntry().getAttribute("description").size());
        assertThat(actual.size()).isGreaterThanOrEqualTo(getStandardEntry().getAttributeCount());
    }

    /**
     * Tests writeEntry method of LDIFEntryWriter class.See
     * https://opends.dev.java.net/issues/show_bug.cgi?id=4545 for more details.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteEntry() throws Exception {
        final Entry entry = new LinkedHashMapEntry("cn=John Doe,ou=people,dc=example,dc=com");
        entry.addAttribute("objectClass", "top", "person", "inetOrgPerson");
        entry.addAttribute("cn", "John Doe");
        entry.addAttribute("sn", "Doe");
        entry.addAttribute("givenName", "John");
        entry.addAttribute("description", "one two", "three four", "five six");
        entry.addAttribute("typeOnly");
        entry.addAttribute("localized;lang-fr", "\u00e7edilla");

        final List<String> actual = new ArrayList<>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        writer.writeEntry(entry);
        writer.close();

        final String[] expected =
                new String[] { "dn: cn=John Doe,ou=people,dc=example,dc=com", "objectClass: top",
                    "objectClass: person", "objectClass: inetOrgPerson", "cn: John Doe", "sn: Doe",
                    "givenName: John", "description: one two", "description: three four",
                    "description: five six", "typeOnly: ", "localized;lang-fr:: w6dlZGlsbGE=", "", };

        Assert.assertEquals(actual.size(), expected.length);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(actual.get(i), expected[i], "LDIF output was " + actual);
        }
    }

    /**
     * Testing the WriteEntry function using the mock for testing more
     * IOExceptions and verify if they are correctly handled.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = IOException.class)
    public void testWriteEntryUsingMockOutputThrowsIOException() throws Exception {

        OutputStream mockOutput = mock(OutputStream.class);
        doThrow(new IOException()).when(mockOutput).write(any(byte[].class));
        doThrow(new IOException()).when(mockOutput).write(any(byte[].class), anyInt(), anyInt());

        LDIFEntryWriter writer = new LDIFEntryWriter(mockOutput);
        try {
            CharSequence comment = "This is a new comment";
            writer.writeComment(comment);
        } finally {
            writer.close();
        }
    }

    /**
     * Verify flush/close are also forwarded to the stream.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteEntryUsingMockOutputForFlushAndClose() throws Exception {
        OutputStream mockOutput = mock(OutputStream.class);
        LDIFEntryWriter writer = new LDIFEntryWriter(mockOutput);
        try {
            writer.flush();
            writer.flush();
            verify(mockOutput, times(2)).flush();
        } finally {
            writer.close();
            verify(mockOutput, times(1)).close();
        }
    }

    /**
     * Test the WriteEntry using an output file verifying write is correctly
     * invoked.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testWriteEntryOutputStreamUsingMock() throws Exception {
        final OutputStream out = mock(OutputStream.class);
        final LDIFEntryWriter writer = new LDIFEntryWriter(out);

        writer.writeEntry(getStandardEntry());
        writer.close();

        verify(out, times(1)).write(any(byte[].class), anyInt(), anyInt());
    }
}
