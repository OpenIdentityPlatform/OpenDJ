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
 *      Portions Copyright 2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test {@code BasicAttribute}.
 */

@SuppressWarnings("javadoc")
public final class LinkedAttributeTestCase extends SdkTestCase {
    @Test
    public void smokeTest() throws Exception {
        // TODO: write a proper test suite.
        final AbstractAttribute attribute =
                new LinkedAttribute(AttributeDescription.valueOf("ALTSERVER", Schema
                        .getCoreSchema()));

        attribute.add(1);
        attribute.add("a value");
        attribute.add(ByteString.valueOfUtf8("another value"));

        Assert.assertTrue(attribute.contains(1));
        Assert.assertTrue(attribute.contains("a value"));
        Assert.assertTrue(attribute.contains(ByteString.valueOfUtf8("another value")));

        Assert.assertEquals(attribute.size(), 3);
        Assert.assertTrue(attribute.remove(1));
        Assert.assertEquals(attribute.size(), 2);
        Assert.assertFalse(attribute.remove("a missing value"));
        Assert.assertEquals(attribute.size(), 2);
        Assert.assertTrue(attribute.remove("a value"));
        Assert.assertEquals(attribute.size(), 1);
        Assert.assertTrue(attribute.remove(ByteString.valueOfUtf8("another value")));
        Assert.assertEquals(attribute.size(), 0);
    }

    @Test
    public void testAdd() {
        Attribute a = new LinkedAttribute("test");
        Assert.assertTrue(a.add(ByteString.valueOfUtf8("value1")));
        Assert.assertFalse(a.add(ByteString.valueOfUtf8("value1")));
        Assert.assertTrue(a.add(ByteString.valueOfUtf8("value2")));
        Assert.assertFalse(a.add(ByteString.valueOfUtf8("value2")));
        Assert.assertTrue(a.add(ByteString.valueOfUtf8("value3")));
        Assert.assertFalse(a.add(ByteString.valueOfUtf8("value3")));
        Assert.assertEquals(a.size(), 3);
        Iterator<ByteString> i = a.iterator();
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value3"));
        Assert.assertFalse(i.hasNext());
    }

    @Test
    public void testAddAll() {
        // addAll to an empty attribute.
        Attribute a = new LinkedAttribute("test");
        Assert.assertFalse(a.addAll(Collections.<ByteString> emptyList(), null));
        Iterator<ByteString> i = a.iterator();
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test");
        Assert.assertTrue(a.addAll(Arrays.asList(ByteString.valueOfUtf8("value1")), null));
        i = a.iterator();
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test");
        Assert.assertTrue(a.addAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2")), null));
        i = a.iterator();
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertFalse(i.hasNext());

        // addAll to a single-valued attribute.
        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"));
        Assert.assertFalse(a.addAll(Collections.<ByteString> emptyList(), null));
        i = a.iterator();
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"));
        Assert.assertTrue(a.addAll(Arrays.asList(ByteString.valueOfUtf8("value2")), null));
        i = a.iterator();
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"));
        Assert.assertTrue(a.addAll(Arrays.asList(ByteString.valueOfUtf8("value2"), ByteString
                .valueOfUtf8("value3")), null));
        i = a.iterator();
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value3"));
        Assert.assertFalse(i.hasNext());

        // addAll to a multi-valued attribute.
        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString.valueOfUtf8("value2"));
        Assert.assertFalse(a.addAll(Collections.<ByteString> emptyList(), null));
        i = a.iterator();
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString.valueOfUtf8("value2"));
        Assert.assertTrue(a.addAll(Arrays.asList(ByteString.valueOfUtf8("value3")), null));
        i = a.iterator();
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value3"));
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString.valueOfUtf8("value2"));
        Assert.assertTrue(a.addAll(Arrays.asList(ByteString.valueOfUtf8("value3"), ByteString
                .valueOfUtf8("value4")), null));
        i = a.iterator();
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value3"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value4"));
        Assert.assertFalse(i.hasNext());
    }

    @Test
    public void testClear() {
        Attribute a = new LinkedAttribute("test");
        Assert.assertTrue(a.isEmpty());
        Assert.assertEquals(a.size(), 0);
        a.clear();
        Assert.assertTrue(a.isEmpty());
        Assert.assertEquals(a.size(), 0);

        a.add(ByteString.valueOfUtf8("value1"));
        Assert.assertFalse(a.isEmpty());
        Assert.assertEquals(a.size(), 1);
        a.clear();
        Assert.assertTrue(a.isEmpty());
        Assert.assertEquals(a.size(), 0);

        a.add(ByteString.valueOfUtf8("value1"));
        a.add(ByteString.valueOfUtf8("value2"));
        Assert.assertFalse(a.isEmpty());
        Assert.assertEquals(a.size(), 2);
        a.clear();
        Assert.assertTrue(a.isEmpty());
        Assert.assertEquals(a.size(), 0);

        a.add(ByteString.valueOfUtf8("value1"));
        a.add(ByteString.valueOfUtf8("value2"));
        a.add(ByteString.valueOfUtf8("value3"));
        Assert.assertFalse(a.isEmpty());
        Assert.assertEquals(a.size(), 3);
        a.clear();
        Assert.assertTrue(a.isEmpty());
        Assert.assertEquals(a.size(), 0);
    }

    @Test
    public void testContains() {
        Attribute a = new LinkedAttribute("test");
        Assert.assertFalse(a.contains(ByteString.valueOfUtf8("value4")));

        a.add(ByteString.valueOfUtf8("value1"));
        Assert.assertTrue(a.contains(ByteString.valueOfUtf8("value1")));
        Assert.assertFalse(a.contains(ByteString.valueOfUtf8("value4")));

        a.add(ByteString.valueOfUtf8("value2"));
        Assert.assertTrue(a.contains(ByteString.valueOfUtf8("value1")));
        Assert.assertTrue(a.contains(ByteString.valueOfUtf8("value2")));
        Assert.assertFalse(a.contains(ByteString.valueOfUtf8("value4")));

        a.add(ByteString.valueOfUtf8("value3"));
        Assert.assertTrue(a.contains(ByteString.valueOfUtf8("value1")));
        Assert.assertTrue(a.contains(ByteString.valueOfUtf8("value2")));
        Assert.assertTrue(a.contains(ByteString.valueOfUtf8("value3")));
        Assert.assertFalse(a.contains(ByteString.valueOfUtf8("value4")));
    }

    @Test
    public void testContainsAll() {
        Attribute a = new LinkedAttribute("test");
        Assert.assertTrue(a.containsAll(Collections.<ByteString> emptyList()));
        Assert.assertFalse(a.containsAll(Arrays.asList(ByteString.valueOfUtf8("value1"))));
        Assert.assertFalse(a.containsAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"))));
        Assert.assertFalse(a.containsAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"))));

        a.add(ByteString.valueOfUtf8("value1"));
        Assert.assertTrue(a.containsAll(Collections.<ByteString> emptyList()));
        Assert.assertTrue(a.containsAll(Arrays.asList(ByteString.valueOfUtf8("value1"))));
        Assert.assertFalse(a.containsAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"))));
        Assert.assertFalse(a.containsAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"))));

        a.add(ByteString.valueOfUtf8("value2"));
        Assert.assertTrue(a.containsAll(Collections.<ByteString> emptyList()));
        Assert.assertTrue(a.containsAll(Arrays.asList(ByteString.valueOfUtf8("value1"))));
        Assert.assertTrue(a.containsAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"))));
        Assert.assertFalse(a.containsAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"))));
    }

    @Test
    public void testFirstValue() {
        Attribute a = new LinkedAttribute("test");
        try {
            a.firstValue();
            Assert.fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // Expected.
        }

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(a.firstValue(), ByteString.valueOfUtf8("value1"));

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString.valueOfUtf8("value2"));
        Assert.assertEquals(a.firstValue(), ByteString.valueOfUtf8("value1"));

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value2"), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(a.firstValue(), ByteString.valueOfUtf8("value2"));
    }

    @Test
    public void testGetAttributeDescription() {
        AttributeDescription ad = AttributeDescription.valueOf("test");
        Attribute a = new LinkedAttribute(ad);
        Assert.assertEquals(a.getAttributeDescription(), ad);
    }

    @Test
    public void testIterator() {
        Attribute a = new LinkedAttribute("test");
        Iterator<ByteString> i = a.iterator();
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString.valueOfUtf8("value2"));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertFalse(i.hasNext());
    }

    @Test
    public void testRemove() {
        Attribute a = new LinkedAttribute("test");
        Assert.assertFalse(a.remove(ByteString.valueOfUtf8("value1")));
        Iterator<ByteString> i = a.iterator();
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"));
        Assert.assertFalse(a.remove(ByteString.valueOfUtf8("value2")));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertFalse(i.hasNext());
        Assert.assertTrue(a.remove(ByteString.valueOfUtf8("value1")));
        i = a.iterator();
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString.valueOfUtf8("value2"));
        Assert.assertFalse(a.remove(ByteString.valueOfUtf8("value3")));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertFalse(i.hasNext());
        Assert.assertTrue(a.remove(ByteString.valueOfUtf8("value1")));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertFalse(i.hasNext());
        Assert.assertTrue(a.remove(ByteString.valueOfUtf8("value2")));
        i = a.iterator();
        Assert.assertFalse(i.hasNext());
    }

    @Test
    public void testRemoveAll() {
        // removeAll from an empty attribute.
        Attribute a = new LinkedAttribute("test");
        Assert.assertFalse(a.removeAll(Collections.<ByteString> emptyList(), null));
        Iterator<ByteString> i = a.iterator();
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test");
        Assert.assertFalse(a.removeAll(Arrays.asList(ByteString.valueOfUtf8("value1")), null));
        i = a.iterator();
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test");
        Assert.assertFalse(a.removeAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"))));
        i = a.iterator();
        Assert.assertFalse(i.hasNext());

        // removeAll from single-valued attribute.
        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"));
        Assert.assertFalse(a.removeAll(Collections.<ByteString> emptyList(), null));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"));
        Assert.assertTrue(a.removeAll(Arrays.asList(ByteString.valueOfUtf8("value1")), null));
        i = a.iterator();
        Assert.assertFalse(i.hasNext());

        a = new LinkedAttribute("test", ByteString.valueOfUtf8("value1"));
        Assert.assertTrue(a.removeAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"))));
        i = a.iterator();
        Assert.assertFalse(i.hasNext());

        // removeAll from multi-valued attribute.
        a =
                new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString
                        .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"), ByteString
                        .valueOfUtf8("value4"));
        Assert.assertFalse(a.removeAll(Collections.<ByteString> emptyList(), null));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value1"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value3"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value4"));
        Assert.assertFalse(i.hasNext());

        a =
                new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString
                        .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"), ByteString
                        .valueOfUtf8("value4"));
        Assert.assertTrue(a.removeAll(Arrays.asList(ByteString.valueOfUtf8("value1")), null));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value2"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value3"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value4"));
        Assert.assertFalse(i.hasNext());

        a =
                new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString
                        .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"), ByteString
                        .valueOfUtf8("value4"));
        Assert.assertTrue(a.removeAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2")), null));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value3"));
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value4"));
        Assert.assertFalse(i.hasNext());

        a =
                new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString
                        .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"), ByteString
                        .valueOfUtf8("value4"));
        Assert.assertTrue(a.removeAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3")), null));
        i = a.iterator();
        Assert.assertTrue(i.hasNext());
        Assert.assertEquals(i.next(), ByteString.valueOfUtf8("value4"));
        Assert.assertFalse(i.hasNext());

        a =
                new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString
                        .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"), ByteString
                        .valueOfUtf8("value4"));
        Assert.assertTrue(a.removeAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"), ByteString.valueOfUtf8("value4")),
                null));
        i = a.iterator();
        Assert.assertFalse(i.hasNext());

        a =
                new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString
                        .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"), ByteString
                        .valueOfUtf8("value4"));
        Assert.assertTrue(a.removeAll(Arrays.asList(ByteString.valueOfUtf8("value1"), ByteString
                .valueOfUtf8("value2"), ByteString.valueOfUtf8("value3"), ByteString.valueOfUtf8("value4"),
                ByteString.valueOfUtf8("value5")), null));
        i = a.iterator();
        Assert.assertFalse(i.hasNext());
    }
}
