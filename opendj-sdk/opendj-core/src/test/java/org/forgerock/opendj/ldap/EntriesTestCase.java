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
 *      Portions copyright 2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.Entries.diffEntries;
import static org.forgerock.opendj.ldap.Entries.diffOptions;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.Iterator;

import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.annotations.Test;

/**
 * Test {@code Entries}.
 */
@SuppressWarnings("javadoc")
public final class EntriesTestCase extends SdkTestCase {

    @Test
    public void testContainsObjectClass() throws Exception {
        Entry entry =
                new LinkedHashMapEntry("dn: cn=test", "objectClass: top", "objectClass: person");
        Schema schema = Schema.getDefaultSchema();

        assertTrue("should contain top", Entries.containsObjectClass(entry, schema
                .getObjectClass("top")));
        assertTrue("should contain person", Entries.containsObjectClass(entry, schema
                .getObjectClass("person")));
        assertFalse("should not contain country", Entries.containsObjectClass(entry, schema
                .getObjectClass("country")));
    }

    @Test
    public void testDiffEntriesAddDeleteAddIntermediateAttribute() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "sn: ignore");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value",
            "sn: ignore");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "add: description",
            "description: value");
        // @formatter:on
        assertEquals(diffEntries(from, to), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteAddTrailingAttributes() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: ignore");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: ignore",
            "description: value",
            "sn: value");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "add: description",
            "description: value",
            "-",
            "add: sn",
            "sn: value");
        // @formatter:on
        assertEquals(diffEntries(from, to), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteDeleteIntermediateAttribute() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value",
            "sn: ignore");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "sn: ignore");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "delete: description",
            "description: value");
        // @formatter:on
        assertEquals(diffEntries(from, to), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteDeleteTrailingAttributes() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: ignore",
            "description: value",
            "sn: value");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: ignore");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "delete: description",
            "description: value",
            "-",
            "delete: sn",
            "sn: value");
        // @formatter:on
        assertEquals(diffEntries(from, to), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteMultiValueAddSingleValue() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1",
            "description: value2");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "add: description",
            "description: value2");
        // @formatter:on
        assertEquals(diffEntries(from, to), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteMultiValueDeleteSingleValue() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1",
            "description: value2");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "delete: description",
            "description: value2");
        // @formatter:on
        assertEquals(diffEntries(from, to), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteMultiValueSameSizeDifferentValues() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1",
            "description: value2");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: VALUE2",
            "description: VALUE3");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "delete: description",
            "description: value1",
            "-",
            "add: description",
            "description: VALUE3");
        // @formatter:on
        assertEquals(diffEntries(from, to), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteMultiValueSameSizeDifferentValuesExact() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1",
            "description: value2");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: VALUE2",
            "description: VALUE3");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "delete: description",
            "description: value1",
            "description: value2",

            "-",
            "add: description",
            "description: VALUE2",
            "description: VALUE3");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().useExactMatching()), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteSingleValue() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: from");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: to");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "delete: description",
            "description: from",
            "-",
            "add: description",
            "description: to");
        // @formatter:on
        assertEquals(diffEntries(from, to), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteSingleValueExactMatch() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: VALUE");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "delete: description",
            "description: value",
            "-",
            "add: description",
            "description: VALUE");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().useExactMatching()), expected);
    }

    @Test
    public void testDiffEntriesAddDeleteSingleValueNoChange() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: VALUE");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify");
        // @formatter:on
        assertEquals(diffEntries(from, to), expected);
    }

    @Test
    public void testDiffEntriesReplaceAddTrailingAttributes() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: ignore");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: ignore",
            "description: value",
            "sn: value");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "replace: description",
            "description: value",
            "-",
            "replace: sn",
            "sn: value");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().alwaysReplaceAttributes()), expected);
    }

    @Test
    public void testDiffEntriesReplaceDeleteTrailingAttributes() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: ignore",
            "description: value",
            "sn: value");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: ignore");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "replace: description",
            "-",
            "replace: sn");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().alwaysReplaceAttributes()), expected);
    }

    @Test
    public void testDiffEntriesReplaceFilteredAttributes() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: from");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "cn: to",
            "description: value",
            "sn: value");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "replace: cn",
            "cn: to",
            "-",
            "replace: sn",
            "sn: value");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().alwaysReplaceAttributes().attributes("cn", "sn")),
                expected);
    }

    @Test
    public void testDiffEntriesReplaceMultiValueChangeSize() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1",
            "description: value2");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "replace: description",
            "description: value1",
            "description: value2");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().alwaysReplaceAttributes()), expected);
    }

    @Test
    public void testDiffEntriesReplaceMultiValueSameSize() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1",
            "description: value2");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: VALUE2",
            "description: VALUE3");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "replace: description",
            "description: VALUE2",
            "description: VALUE3");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().alwaysReplaceAttributes()), expected);
    }

    @Test
    public void testDiffEntriesReplaceMultiValueSameSizeExact() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value1",
            "description: value2");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value2",
            "description: value3");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "replace: description",
            "description: value2",
            "description: value3");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().alwaysReplaceAttributes().useExactMatching()), expected);
    }

    @Test
    public void testDiffEntriesReplaceSingleValue() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: from");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: to");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "replace: description",
            "description: to");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().alwaysReplaceAttributes()), expected);
    }

    @Test
    public void testDiffEntriesReplaceSingleValueExactMatch() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: VALUE");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "replace: description",
            "description: VALUE");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().alwaysReplaceAttributes().useExactMatching()), expected);
    }

    @Test
    public void testDiffEntriesReplaceSingleValueNoChange() {
        // @formatter:off
        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: value");
        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "description: VALUE");
        ModifyRequest expected = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify");
        // @formatter:on
        assertEquals(diffEntries(from, to, diffOptions().alwaysReplaceAttributes()), expected);
    }

    private void assertEquals(ModifyRequest actual, ModifyRequest expected) {
        assertThat((Object) actual.getName()).isEqualTo(expected.getName());
        assertThat(actual.getModifications()).hasSize(expected.getModifications().size());
        Iterator<Modification> i1 = actual.getModifications().iterator();
        Iterator<Modification> i2 = expected.getModifications().iterator();
        while (i1.hasNext()) {
            Modification m1 = i1.next();
            Modification m2 = i2.next();
            assertThat(m1.getModificationType()).isEqualTo(m2.getModificationType());
            assertThat(m1.getAttribute()).isEqualTo(m2.getAttribute());
        }
    }
}
