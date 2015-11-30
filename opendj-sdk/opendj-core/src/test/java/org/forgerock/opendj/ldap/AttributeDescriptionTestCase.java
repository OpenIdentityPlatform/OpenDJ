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

package org.forgerock.opendj.ldap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Iterator;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test {@code AttributeDescription}.
 */
@SuppressWarnings("javadoc")
public final class AttributeDescriptionTestCase extends SdkTestCase {
    @DataProvider(name = "dataForCompareCoreSchema")
    public Object[][] dataForCompareCoreSchema() {
        // AD1, AD2, compare result, isSubtype, isSuperType
        return new Object[][] { { "cn", "cn", 0, true, true },
            { "cn", "commonName", 0, true, true }, { " cn", "commonName ", 0, true, true },
            { "commonName", "cn", 0, true, true }, { "commonName", "commonName", 0, true, true },
            { "cn", "objectClass", 1, false, false }, { "objectClass", "cn", -1, false, false },
            { "name", "cn", 1, false, true }, { "cn", "name", -1, true, false },
            { "name;foo", "cn", 1, false, false }, { "cn;foo", "name", -1, true, false },
            { "name", "cn;foo", 1, false, true }, { "cn", "name;foo", -1, false, false }, };
    }

    @DataProvider(name = "dataForCompareNoSchema")
    public Object[][] dataForCompareNoSchema() {
        // AD1, AD2, compare result, isSubtype, isSuperType
        return new Object[][] { { "cn", "cn", 0, true, true }, { "cn", "CN", 0, true, true },
            { "CN", "cn", 0, true, true }, { "CN", "CN", 0, true, true },
            { "cn", "commonName", -1, false, false }, { "commonName", "cn", 1, false, false },
            { "commonName", "commonName", 0, true, true }, { "cn", "cn;foo", -1, false, true },
            { "cn;foo", "cn", 1, true, false }, { "cn;foo", "cn;foo", 0, true, true },
            { "CN;FOO", "cn;foo", 0, true, true }, { "cn;foo", "CN;FOO", 0, true, true },
            { "CN;FOO", "CN;FOO", 0, true, true }, { "cn;foo", "cn;bar", 1, false, false },
            { "cn;bar", "cn;foo", -1, false, false },

            { "cn;xxx;yyy", "cn", 1, true, false }, { "cn;xxx;yyy", "cn;yyy", 1, true, false },
            { "cn;xxx;yyy", "cn;xxx", 1, true, false },
            { "cn;xxx;yyy", "cn;xxx;yyy", 0, true, true },
            { "cn;xxx;yyy", "cn;yyy;xxx", 0, true, true },

            { "cn", "cn;xxx;yyy", -1, false, true }, { "cn;yyy", "cn;xxx;yyy", -1, false, true },
            { "cn;xxx", "cn;xxx;yyy", -1, false, true },
            { "cn;xxx;yyy", "cn;xxx;yyy", 0, true, true },
            { "cn;yyy;xxx", "cn;xxx;yyy", 0, true, true }, };
    }

    @DataProvider(name = "dataForValueOfCoreSchema")
    public Object[][] dataForValueOfCoreSchema() {
        // Value, type, isObjectClass
        return new Object[][] { { "cn", "cn", false }, { "CN", "cn", false },
            { "commonName", "cn", false }, { "objectclass", "objectClass", true }, };
    }

    @DataProvider(name = "dataForValueOfInvalidAttributeDescriptions")
    public Object[][] dataForValueOfInvalidAttributeDescriptions() {
        return new Object[][] { { "" }, { " " }, { ";" }, { " ; " }, { "0cn" }, { "cn+" },
            { "cn;foo+bar" }, { "cn;foo;foo+bar" }, { ";foo" }, { "cn;" }, { "cn;;foo" },
            { "cn; ;foo" }, { "cn;foo;" }, { "cn;foo; " }, { "cn;foo;;bar" }, { "cn;foo; ;bar" },
            { "cn;foo;bar;;" }, { "1a" }, { "1.a" }, { "1-" }, { "1.1a" }, { "1.1.a" }, };
    }

    @DataProvider(name = "dataForValueOfNoSchema")
    public Object[][] dataForValueOfNoSchema() {
        // Value, type, options, containsOptions("foo")
        return new Object[][] { { "cn", "cn", new String[0], false },
            { " cn ", "cn", new String[0], false }, { "  cn  ", "cn", new String[0], false },
            { "CN", "CN", new String[0], false }, { "1", "1", new String[0], false },
            { "1.2", "1.2", new String[0], false }, { "1.2.3", "1.2.3", new String[0], false },
            { "111.222.333", "111.222.333", new String[0], false },
            { "objectClass", "objectClass", new String[0], false },
            { "cn;foo", "cn", new String[] { "foo" }, true },
            { "cn;FOO", "cn", new String[] { "FOO" }, true },
            { "cn;bar", "cn", new String[] { "bar" }, false },
            { "cn;BAR", "cn", new String[] { "BAR" }, false },
            { "cn;foo;bar", "cn", new String[] { "foo", "bar" }, true },
            { "cn;FOO;bar", "cn", new String[] { "FOO", "bar" }, true },
            { "cn;foo;BAR", "cn", new String[] { "foo", "BAR" }, true },
            { "cn;FOO;BAR", "cn", new String[] { "FOO", "BAR" }, true },
            { "cn;bar;FOO", "cn", new String[] { "bar", "FOO" }, true },
            { "cn;BAR;foo", "cn", new String[] { "BAR", "foo" }, true },
            { "cn;bar;FOO", "cn", new String[] { "bar", "FOO" }, true },
            { "cn;BAR;FOO", "cn", new String[] { "BAR", "FOO" }, true },
            { " cn;BAR;FOO ", "cn", new String[] { "BAR", "FOO" }, true },
            { "  cn;BAR;FOO  ", "cn", new String[] { "BAR", "FOO" }, true },
            { "cn;xxx;yyy;zzz", "cn", new String[] { "xxx", "yyy", "zzz" }, false },
            { "cn;zzz;YYY;xxx", "cn", new String[] { "zzz", "YYY", "xxx" }, false }, };
    }

    @Test(dataProvider = "dataForCompareCoreSchema")
    public void testCompareCoreSchema(final String ad1, final String ad2, final int compare,
            final boolean isSubType, final boolean isSuperType) {
        final AttributeDescription attributeDescription1 =
                AttributeDescription.valueOf(ad1, Schema.getCoreSchema());

        final AttributeDescription attributeDescription2 =
                AttributeDescription.valueOf(ad2, Schema.getCoreSchema());

        // Identity.
        assertTrue(attributeDescription1.equals(attributeDescription1));
        assertTrue(attributeDescription1.compareTo(attributeDescription1) == 0);
        assertTrue(attributeDescription1.isSubTypeOf(attributeDescription1));
        assertTrue(attributeDescription1.isSuperTypeOf(attributeDescription1));

        if (compare == 0) {
            assertTrue(attributeDescription1.equals(attributeDescription2));
            assertTrue(attributeDescription2.equals(attributeDescription1));
            assertTrue(attributeDescription1.compareTo(attributeDescription2) == 0);
            assertTrue(attributeDescription2.compareTo(attributeDescription1) == 0);

            assertTrue(attributeDescription1.isSubTypeOf(attributeDescription2));
            assertTrue(attributeDescription1.isSuperTypeOf(attributeDescription2));
            assertTrue(attributeDescription2.isSubTypeOf(attributeDescription1));
            assertTrue(attributeDescription2.isSuperTypeOf(attributeDescription1));
        } else {
            assertFalse(attributeDescription1.equals(attributeDescription2));
            assertFalse(attributeDescription2.equals(attributeDescription1));

            if (compare < 0) {
                assertTrue(attributeDescription1.compareTo(attributeDescription2) < 0);
                assertTrue(attributeDescription2.compareTo(attributeDescription1) > 0);
            } else {
                assertTrue(attributeDescription1.compareTo(attributeDescription2) > 0);
                assertTrue(attributeDescription2.compareTo(attributeDescription1) < 0);
            }

            assertEquals(attributeDescription1.isSubTypeOf(attributeDescription2), isSubType);

            assertEquals(attributeDescription1.isSuperTypeOf(attributeDescription2), isSuperType);
        }
    }

    @Test(dataProvider = "dataForCompareNoSchema")
    public void testCompareNoSchema(final String ad1, final String ad2, final int compare,
            final boolean isSubType, final boolean isSuperType) {
        final AttributeDescription attributeDescription1 =
                AttributeDescription.valueOf(ad1, Schema.getEmptySchema());

        final AttributeDescription attributeDescription2 =
                AttributeDescription.valueOf(ad2, Schema.getEmptySchema());

        // Identity.
        assertTrue(attributeDescription1.equals(attributeDescription1));
        assertTrue(attributeDescription1.compareTo(attributeDescription1) == 0);
        assertTrue(attributeDescription1.isSubTypeOf(attributeDescription1));
        assertTrue(attributeDescription1.isSuperTypeOf(attributeDescription1));

        if (compare == 0) {
            assertTrue(attributeDescription1.equals(attributeDescription2));
            assertTrue(attributeDescription2.equals(attributeDescription1));
            assertTrue(attributeDescription1.compareTo(attributeDescription2) == 0);
            assertTrue(attributeDescription2.compareTo(attributeDescription1) == 0);

            assertTrue(attributeDescription1.isSubTypeOf(attributeDescription2));
            assertTrue(attributeDescription1.isSuperTypeOf(attributeDescription2));
            assertTrue(attributeDescription2.isSubTypeOf(attributeDescription1));
            assertTrue(attributeDescription2.isSuperTypeOf(attributeDescription1));
        } else {
            assertFalse(attributeDescription1.equals(attributeDescription2));
            assertFalse(attributeDescription2.equals(attributeDescription1));

            if (compare < 0) {
                assertTrue(attributeDescription1.compareTo(attributeDescription2) < 0);
                assertTrue(attributeDescription2.compareTo(attributeDescription1) > 0);
            } else {
                assertTrue(attributeDescription1.compareTo(attributeDescription2) > 0);
                assertTrue(attributeDescription2.compareTo(attributeDescription1) < 0);
            }

            assertEquals(attributeDescription1.isSubTypeOf(attributeDescription2), isSubType);

            assertEquals(attributeDescription1.isSuperTypeOf(attributeDescription2), isSuperType);
        }
    }

    @Test(dataProvider = "dataForValueOfCoreSchema")
    public void testValueOfCoreSchema(final String ad, final String at, final boolean isObjectClass) {
        final AttributeDescription attributeDescription =
                AttributeDescription.valueOf(ad, Schema.getCoreSchema());

        assertEquals(attributeDescription.toString(), ad);

        assertEquals(attributeDescription.getAttributeType().getNameOrOID(), at);

        assertEquals(attributeDescription.isObjectClass(), isObjectClass);

        assertFalse(attributeDescription.hasOptions());
        assertFalse(attributeDescription.hasOption("dummy"));

        final Iterator<String> iterator = attributeDescription.getOptions().iterator();
        assertFalse(iterator.hasNext());
    }

    /** FIXME: none of these pass! The valueOf method is far to lenient. */
    @Test(dataProvider = "dataForValueOfInvalidAttributeDescriptions",
            expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testValueOfInvalidAttributeDescriptions(final String ad) {
        AttributeDescription.valueOf(ad, Schema.getEmptySchema());
    }

    @Test(dataProvider = "dataForValueOfNoSchema")
    public void testValueOfNoSchema(final String ad, final String at, final String[] options,
            final boolean containsFoo) {
        final AttributeDescription attributeDescription =
                AttributeDescription.valueOf(ad, Schema.getEmptySchema());

        assertEquals(attributeDescription.toString(), ad);

        assertEquals(attributeDescription.getAttributeType().getNameOrOID(), at);

        assertFalse(attributeDescription.isObjectClass());

        if (options.length == 0) {
            assertFalse(attributeDescription.hasOptions());
        } else {
            assertTrue(attributeDescription.hasOptions());
        }

        assertFalse(attributeDescription.hasOption("dummy"));
        if (containsFoo) {
            assertTrue(attributeDescription.hasOption("foo"));
            assertTrue(attributeDescription.hasOption("FOO"));
            assertTrue(attributeDescription.hasOption("FoO"));
        } else {
            assertFalse(attributeDescription.hasOption("foo"));
            assertFalse(attributeDescription.hasOption("FOO"));
            assertFalse(attributeDescription.hasOption("FoO"));
        }

        for (final String option : options) {
            assertTrue(attributeDescription.hasOption(option));
        }

        final Iterator<String> iterator = attributeDescription.getOptions().iterator();
        for (final String option : options) {
            assertTrue(iterator.hasNext());
            assertEquals(iterator.next(), option);
        }
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testWithOptionAddFirstOption() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn");
        AttributeDescription ad2 = ad1.withOption("test");
        assertTrue(ad2.hasOptions());
        assertTrue(ad2.hasOption("test"));
        assertFalse(ad2.hasOption("dummy"));
        assertEquals(ad2.toString(), "cn;test");
        assertEquals(ad2.getOptions().iterator().next(), "test");
    }

    @Test
    public void testWithOptionAddExistingFirstOption() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test");
        AttributeDescription ad2 = ad1.withOption("test");
        assertSame(ad1, ad2);
    }

    @Test
    public void testWithOptionAddSecondOption() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test1");
        AttributeDescription ad2 = ad1.withOption("test2");
        assertTrue(ad2.hasOptions());
        assertTrue(ad2.hasOption("test1"));
        assertTrue(ad2.hasOption("test2"));
        assertFalse(ad2.hasOption("dummy"));
        assertEquals(ad2.toString(), "cn;test1;test2");
        Iterator<String> i = ad2.getOptions().iterator();
        assertEquals(i.next(), "test1");
        assertEquals(i.next(), "test2");
    }

    @Test
    public void testWithOptionAddExistingSecondOption() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test1;test2");
        AttributeDescription ad2 = ad1.withOption("test1");
        AttributeDescription ad3 = ad1.withOption("test2");
        assertSame(ad1, ad2);
        assertSame(ad1, ad3);
    }

    @Test
    public void testWithoutOptionEmpty() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn");
        AttributeDescription ad2 = ad1.withoutOption("test");
        assertSame(ad1, ad2);
    }

    @Test
    public void testWithoutOptionFirstOption() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test");
        AttributeDescription ad2 = ad1.withoutOption("test");
        assertFalse(ad2.hasOptions());
        assertFalse(ad2.hasOption("test"));
        assertEquals(ad2.toString(), "cn");
        assertFalse(ad2.getOptions().iterator().hasNext());
    }

    @Test
    public void testWithoutOptionFirstOptionMissing() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test");
        AttributeDescription ad2 = ad1.withoutOption("dummy");
        assertSame(ad1, ad2);
    }

    @Test
    public void testWithoutOptionSecondOption1() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test1;test2");
        AttributeDescription ad2 = ad1.withoutOption("test1");
        assertTrue(ad2.hasOptions());
        assertFalse(ad2.hasOption("test1"));
        assertTrue(ad2.hasOption("test2"));
        assertEquals(ad2.toString(), "cn;test2");
        assertEquals(ad2.getOptions().iterator().next(), "test2");
    }

    @Test
    public void testWithoutOptionSecondOption2() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test1;test2");
        AttributeDescription ad2 = ad1.withoutOption("test2");
        assertTrue(ad2.hasOptions());
        assertTrue(ad2.hasOption("test1"));
        assertFalse(ad2.hasOption("test2"));
        assertEquals(ad2.toString(), "cn;test1");
        assertEquals(ad2.getOptions().iterator().next(), "test1");
    }

    @Test
    public void testWithoutOptionSecondOptionMissing() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test1;test2");
        AttributeDescription ad2 = ad1.withoutOption("dummy");
        assertSame(ad1, ad2);
    }

    @Test
    public void testWithoutOptionThirdOption1() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test1;test2;test3");
        AttributeDescription ad2 = ad1.withoutOption("test1");
        assertTrue(ad2.hasOptions());
        assertFalse(ad2.hasOption("test1"));
        assertTrue(ad2.hasOption("test2"));
        assertTrue(ad2.hasOption("test3"));
        assertEquals(ad2.toString(), "cn;test2;test3");
        Iterator<String> i = ad2.getOptions().iterator();
        assertEquals(i.next(), "test2");
        assertEquals(i.next(), "test3");
    }

    @Test
    public void testWithoutOptionThirdOption2() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test1;test2;test3");
        AttributeDescription ad2 = ad1.withoutOption("test2");
        assertTrue(ad2.hasOptions());
        assertTrue(ad2.hasOption("test1"));
        assertFalse(ad2.hasOption("test2"));
        assertTrue(ad2.hasOption("test3"));
        assertEquals(ad2.toString(), "cn;test1;test3");
        Iterator<String> i = ad2.getOptions().iterator();
        assertEquals(i.next(), "test1");
        assertEquals(i.next(), "test3");
    }

    @Test
    public void testWithoutOptionThirdOption3() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test1;test2;test3");
        AttributeDescription ad2 = ad1.withoutOption("test3");
        assertTrue(ad2.hasOptions());
        assertTrue(ad2.hasOption("test1"));
        assertTrue(ad2.hasOption("test2"));
        assertFalse(ad2.hasOption("test3"));
        assertEquals(ad2.toString(), "cn;test1;test2");
        Iterator<String> i = ad2.getOptions().iterator();
        assertEquals(i.next(), "test1");
        assertEquals(i.next(), "test2");
    }

    @Test
    public void testWithoutOptionThirdOptionMissing() {
        AttributeDescription ad1 = AttributeDescription.valueOf("cn;test1;test2;test3");
        AttributeDescription ad2 = ad1.withoutOption("dummy");
        assertSame(ad1, ad2);
    }

}
