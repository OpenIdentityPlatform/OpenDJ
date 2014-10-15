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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.ldap;

import static org.testng.Assert.assertTrue;

import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the Attributes class.
 */
@SuppressWarnings("javadoc")
public class AttributesTestCase extends SdkTestCase {
    /**
     * Data provider for attribute descriptions.
     *
     * @return
     */
    @DataProvider(name = "dataForAttributeDescriptions")
    public Object[][] dataForAttributeDescriptions() {
        // Value, type, options, containsOptions("foo")
        return new Object[][] { { "cn" }, { "CN" }, { "objectClass" }, { "cn;foo" }, { "cn;FOO" },
            { "cn;bar" }, { "cn;BAR" }, { "cn;foo;bar" }, { "cn;FOO;bar" }, };
    }

    /** Data provider for old and new attributes. */
    @DataProvider(name = "dataForAttributeRename")
    public Object[][] dataForAttributeRename() {
        return new Object[][] { { "cn", "cn", true }, { "CN", "cn", true },
            { "objectClass", "cn", false }, { "cn;foo", "cn", true } };
    }

    /**
     * Tests the attribute renaming method.
     *
     * @throws Exception
     */
    @Test(dataProvider = "dataForAttributeRename")
    public void testAttributeRename(final String attr, final String desc, final boolean valid)
            throws Exception {
        final AttributeDescription desc1 =
                AttributeDescription.valueOf(attr, Schema.getCoreSchema());
        final AttributeDescription desc2 =
                AttributeDescription.valueOf(desc, Schema.getCoreSchema());
        final Attribute attr1 = Attributes.emptyAttribute(desc1);
        try {
            Attributes.renameAttribute(attr1, desc2);
        } catch (final Exception e) {
            if (valid) {
                // shouldn't have come here.
                throw e;
            }
        }
    }

    /**
     * Tests the empty attribute method.
     *
     * @throws Exception
     */
    @Test(dataProvider = "dataForAttributeDescriptions")
    public void testEmptyAttribute(final String attrDesc) throws Exception {
        final AttributeDescription desc =
                AttributeDescription.valueOf(attrDesc, Schema.getCoreSchema());
        final Attribute attr = Attributes.emptyAttribute(desc);
        assertTrue(attr.isEmpty());
    }

    /**
     * Tests the unmodifiable attribute method.
     *
     * @throws Exception
     */
    @Test(dataProvider = "dataForAttributeDescriptions",
            expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAttribute(final String attrDesc) throws Exception {
        final AttributeDescription desc =
                AttributeDescription.valueOf(attrDesc, Schema.getCoreSchema());
        final Attribute attr = Attributes.emptyAttribute(desc);
        attr.add("test"); // should go through.
        // Make it unmodifiable.
        final Attribute attr1 = Attributes.unmodifiableAttribute(attr);
        attr1.add("test");
    }

    /**
     * Tests the unmodifiable entry method.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableEntry() throws Exception {
        final Entry entry = new LinkedHashMapEntry("cn=test");
        // add a value.
        entry.clearAttributes();
        final Entry entry1 = Entries.unmodifiableEntry(entry);
        entry1.clearAttributes();
    }
}
