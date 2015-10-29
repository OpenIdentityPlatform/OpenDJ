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

package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.Attributes.emptyAttribute;
import static org.forgerock.opendj.ldap.Attributes.singletonAttribute;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test {@code Entry}.
 */
@SuppressWarnings("javadoc")
public final class EntryTestCase extends SdkTestCase {

    private static interface EntryFactory {
        Entry newEntry(String... ldifLines) throws Exception;
    }

    private static final class LinkedHashMapEntryFactory implements EntryFactory {
        @Override
        public Entry newEntry(final String... ldifLines) throws Exception {
            final LDIFEntryReader reader = new LDIFEntryReader(ldifLines).setSchema(SCHEMA);
            final Entry entry = reader.readEntry();
            assertThat(reader.hasNext()).isFalse();
            return new LinkedHashMapEntry(entry);
        }
    }

    private static final class TreeMapEntryFactory implements EntryFactory {
        @Override
        public Entry newEntry(final String... ldifLines) throws Exception {
            final LDIFEntryReader reader = new LDIFEntryReader(ldifLines).setSchema(SCHEMA);
            final Entry entry = reader.readEntry();
            assertThat(reader.hasNext()).isFalse();
            return new TreeMapEntry(entry);
        }
    }

    private static final AttributeDescription AD_CN;
    private static final AttributeDescription AD_CUSTOM1;
    private static final AttributeDescription AD_CUSTOM2;
    private static final AttributeDescription AD_NAME;

    private static final AttributeDescription AD_SN;

    private static final Schema SCHEMA;

    static {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addAttributeType("( 9.9.9.1 NAME 'custom1' SUP name )", false);
        builder.addAttributeType("( 9.9.9.2 NAME 'custom2' SUP name )", false);
        SCHEMA = builder.toSchema();
        AD_CUSTOM1 = AttributeDescription.valueOf("custom1", SCHEMA);
        AD_CUSTOM2 = AttributeDescription.valueOf("custom2", SCHEMA);
        AD_CN = AttributeDescription.valueOf("cn");
        AD_SN = AttributeDescription.valueOf("sn");
        AD_NAME = AttributeDescription.valueOf("name");
    }

    @DataProvider(name = "EntryFactory")
    Object[][] entryFactory() {
        // Value, type, options, containsOptions("foo")
        return new Object[][] { { new TreeMapEntryFactory() }, { new LinkedHashMapEntryFactory() } };
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeAttribute(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.addAttribute(new LinkedAttribute("sn", "sn"))).isTrue();
        assertThat(entry.getAttribute(AD_SN)).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeAttributeCollection(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> duplicateValues = new LinkedList<>();
        assertThat(entry.addAttribute(new LinkedAttribute("sn", "sn"), duplicateValues)).isTrue();
        assertThat(entry.getAttribute(AD_SN)).hasSize(1);
        assertThat(duplicateValues).hasSize(0);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeAttributeCollectionValueMissing(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> duplicateValues = new LinkedList<>();
        assertThat(entry.addAttribute(new LinkedAttribute("cn", "newcn"), duplicateValues))
                .isTrue();
        assertThat(entry.getAttribute(AD_CN)).hasSize(2);
        assertThat(duplicateValues).hasSize(0);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeAttributeCollectionValuePresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> duplicateValues = new LinkedList<>();
        assertThat(entry.addAttribute(new LinkedAttribute("cn", "test"), duplicateValues))
                .isFalse();
        assertThat(entry.getAttribute(AD_CN)).hasSize(1);
        assertThat(duplicateValues).hasSize(1);
        assertThat(duplicateValues).contains(ByteString.valueOfUtf8("test"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeAttributeValueMissing(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.addAttribute(new LinkedAttribute("cn", "newcn"))).isTrue();
        assertThat(entry.getAttribute(AD_CN)).hasSize(2);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeAttributeValuePresent(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.addAttribute(new LinkedAttribute("cn", "test"))).isFalse();
        assertThat(entry.getAttribute(AD_CN)).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeString(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.addAttribute("sn", "sn")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_SN)).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeStringCustom(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.addAttribute("custom2", "custom2")).isSameAs(entry);
        // This is expected to be null since the type was decoded using the
        // default schema and a temporary oid was allocated.
        assertThat(entry.getAttribute(AD_CUSTOM2)).isNull();
        assertThat(entry.getAttribute("custom2")).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeStringCustomValueMissing(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.addAttribute("custom1", "xxxx")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CUSTOM1)).hasSize(2);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeStringCustomValuePresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.addAttribute("custom1", "custom1")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CUSTOM1)).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeStringValueMissing(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.addAttribute("cn", "newcn")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CN)).hasSize(2);
    }

    @Test(dataProvider = "EntryFactory")
    public void testAddAttributeStringValuePresent(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.addAttribute("cn", "test")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CN)).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testClearAttributes(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.clearAttributes()).isSameAs(entry);
        assertThat(entry.getAttributeCount()).isEqualTo(0);
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeCustomMissing(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.containsAttribute(emptyAttribute(AD_CUSTOM2), missingValues)).isFalse();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeCustomPresent1(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.containsAttribute(emptyAttribute(AD_CUSTOM1), missingValues)).isTrue();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeCustomPresent2(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.containsAttribute(emptyAttribute("custom1"), missingValues)).isTrue();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeCustomValueMissing1(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(
                entry.containsAttribute(singletonAttribute(AD_CUSTOM2, "missing"), missingValues))
                .isFalse();
        assertThat(missingValues).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeMissing(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.containsAttribute(emptyAttribute(AD_SN), missingValues)).isFalse();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributePresent1(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.containsAttribute(emptyAttribute(AD_CN), missingValues)).isTrue();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributePresent2(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.containsAttribute(emptyAttribute("cn"), missingValues)).isTrue();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeValueCustomMissing2(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(
                entry.containsAttribute(singletonAttribute(AD_CUSTOM1, "missing"), missingValues))
                .isFalse();
        assertThat(missingValues).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeValueCustomPresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(
                entry.containsAttribute(singletonAttribute(AD_CUSTOM1, "custom1"), missingValues))
                .isTrue();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeValueMissing1(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.containsAttribute(singletonAttribute(AD_SN, "missing"), missingValues))
                .isFalse();
        assertThat(missingValues).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeValueMissing2(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.containsAttribute(singletonAttribute(AD_CN, "missing"), missingValues))
                .isFalse();
        assertThat(missingValues).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeAttributeValuePresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.containsAttribute(singletonAttribute(AD_CN, "test"), missingValues))
                .isTrue();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeStringCustomMissing(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.containsAttribute("custom2")).isFalse();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeStringCustomPresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.containsAttribute("custom1")).isTrue();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeStringMissing(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.containsAttribute("sn")).isFalse();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeStringPresent(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.containsAttribute("cn")).isTrue();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeStringValueCustom(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.containsAttribute("custom1", "custom1")).isTrue();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeStringValueMissing1(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.containsAttribute("cn", "missing")).isFalse();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeStringValueMissing2(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.containsAttribute("sn", "missing")).isFalse();
    }

    @Test(dataProvider = "EntryFactory")
    public void testContainsAttributeStringValuePresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.containsAttribute("cn", "test")).isTrue();
    }

    @Test
    public void testEqualsHashCodeDifferentContentDifferentTypes1() throws Exception {
        final Entry e1 = createTestEntry(new TreeMapEntryFactory());
        // Extra attributes.
        final Entry e2 = createTestEntry(new LinkedHashMapEntryFactory()).addAttribute("sn", "sn");
        assertThat(e1).isNotEqualTo(e2);
        assertThat(e2).isNotEqualTo(e1);
        assertThat(e1.hashCode()).isNotEqualTo(e2.hashCode());
    }

    @Test
    public void testEqualsHashCodeDifferentContentDifferentTypes2() throws Exception {
        final Entry e1 = createTestEntry(new TreeMapEntryFactory());
        // Same attributes, extra values.
        final Entry e2 =
                createTestEntry(new LinkedHashMapEntryFactory()).addAttribute("cn", "newcn");
        assertThat(e1).isNotEqualTo(e2);
        assertThat(e2).isNotEqualTo(e1);
        assertThat(e1.hashCode()).isNotEqualTo(e2.hashCode());
    }

    @Test(dataProvider = "EntryFactory")
    public void testEqualsHashCodeDifferentContentSameTypes1(final EntryFactory factory)
            throws Exception {
        final Entry e1 = createTestEntry(factory);
        // Extra attributes.
        final Entry e2 = createTestEntry(factory).addAttribute("sn", "sn");
        assertThat(e1).isNotEqualTo(e2);
        assertThat(e2).isNotEqualTo(e1);
        assertThat(e1.hashCode()).isNotEqualTo(e2.hashCode());
    }

    @Test(dataProvider = "EntryFactory")
    public void testEqualsHashCodeDifferentContentSameTypes2(final EntryFactory factory)
            throws Exception {
        final Entry e1 = createTestEntry(factory);
        // Same attributes, extra values.
        final Entry e2 = createTestEntry(factory).addAttribute("cn", "newcn");
        assertThat(e1).isNotEqualTo(e2);
        assertThat(e2).isNotEqualTo(e1);
        assertThat(e1.hashCode()).isNotEqualTo(e2.hashCode());
    }

    @Test(dataProvider = "EntryFactory")
    public void testEqualsHashCodeDifferentDN(final EntryFactory factory) throws Exception {
        final Entry e1 = createTestEntry(factory);
        final Entry e2 = createTestEntry(factory).setName("cn=foobar");
        assertThat(e1).isNotEqualTo(e2);
        assertThat(e1.hashCode()).isNotEqualTo(e2.hashCode());
    }

    @Test(dataProvider = "EntryFactory")
    public void testEqualsHashCodeMutates(final EntryFactory factory) throws Exception {
        final Entry e = createTestEntry(factory);
        final int hc1 = e.hashCode();
        e.addAttribute("sn", "sn");
        final int hc2 = e.hashCode();
        assertThat(hc1).isNotEqualTo(hc2);
    }

    @Test
    public void testEqualsHashCodeSameContentDifferentTypes() throws Exception {
        final Entry e1 = createTestEntry(new TreeMapEntryFactory());
        final Entry e2 = createTestEntry(new LinkedHashMapEntryFactory());
        assertThat(e1).isEqualTo(e2);
        assertThat(e2).isEqualTo(e1);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test(dataProvider = "EntryFactory")
    public void testEqualsHashCodeSameContentSameTypes(final EntryFactory factory) throws Exception {
        final Entry e1 = createTestEntry(factory);
        final Entry e2 = createTestEntry(factory);
        assertThat(e1).isEqualTo(e1);
        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributes(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAllAttributes().iterator()).hasSize(3);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributesAttributeDescriptionMissing(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAllAttributes(AD_SN)).hasSize(0);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributesAttributeDescriptionPresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAllAttributes(AD_CN)).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributesAttributeDescriptionPresentOptions(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        entry.addAttribute(singletonAttribute(AD_CN.withOption("lang-fr"), "xxxx"));
        assertThat(entry.getAllAttributes(AD_CN)).hasSize(2);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributesAttributeDescriptionSupertype(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAllAttributes(AD_NAME)).hasSize(2);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributesStringCustom(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        entry.addAttribute(singletonAttribute(AD_CUSTOM1.withOption("lang-fr"), "xxxx"));
        assertThat(entry.getAllAttributes("custom1")).hasSize(2);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributesStringCustomOptions(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        entry.addAttribute("custom2", "value1");
        entry.addAttribute("custom2;lang-fr", "value2");
        assertThat(entry.getAllAttributes("custom2")).hasSize(2);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributesStringMissing(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAllAttributes("sn")).hasSize(0);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributesStringPresent(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAllAttributes("cn")).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAllAttributesStringSupertype(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAllAttributes("name")).hasSize(2);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAttributeAttributeDescriptionMissing(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAttribute(AD_SN)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAttributeAttributeDescriptionPresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAttribute(AD_CN)).isNotNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAttributeCount(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAttributeCount()).isEqualTo(3);
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAttributeStringCustom(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAttribute("custom1")).isNotNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAttributeStringMissing(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAttribute("sn")).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetAttributeStringPresent(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.getAttribute("cn")).isNotNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testGetName(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat((Object) entry.getName()).isEqualTo(DN.valueOf("cn=test"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testParseAttributeAttributeDescriptionCustom(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.parseAttribute(AD_CUSTOM1).asString()).isEqualTo("custom1");
    }

    @Test(dataProvider = "EntryFactory")
    public void testParseAttributeAttributeDescriptionMissing(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.parseAttribute(AD_SN).asString()).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testParseAttributeAttributeDescriptionPresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.parseAttribute(AD_CN).asString()).isEqualTo("test");
    }

    @Test(dataProvider = "EntryFactory")
    public void testParseAttributeStringCustom(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.parseAttribute("custom1").asString()).isEqualTo("custom1");
    }

    @Test(dataProvider = "EntryFactory")
    public void testParseAttributeStringMissing(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.parseAttribute("sn").asString()).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testParseAttributeStringPresent(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.parseAttribute("cn").asString()).isEqualTo("test");
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeAttributeDescriptionMissing(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.removeAttribute(AD_SN)).isFalse();
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeAttributeDescriptionPresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.removeAttribute(AD_CN)).isTrue();
        assertThat(entry.getAttribute(AD_CN)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeAttributeMissing(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.removeAttribute(emptyAttribute(AD_SN), missingValues)).isFalse();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeAttributePresent(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.removeAttribute(emptyAttribute(AD_CN), missingValues)).isTrue();
        assertThat(entry.getAttribute(AD_CN)).isNull();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeAttributeValueMissing1(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.removeAttribute(singletonAttribute(AD_CN, "missing"), missingValues))
                .isFalse();
        assertThat(entry.getAttribute(AD_CN)).isNotNull();
        assertThat(missingValues).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeAttributeValueMissing2(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.removeAttribute(singletonAttribute(AD_SN, "missing"), missingValues))
                .isFalse();
        assertThat(missingValues).hasSize(1);
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeAttributeValuePresent(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        final List<ByteString> missingValues = new LinkedList<>();
        assertThat(entry.removeAttribute(singletonAttribute(AD_CN, "test"), missingValues))
                .isTrue();
        assertThat(entry.getAttribute(AD_CN)).isNull();
        assertThat(missingValues).isEmpty();
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeStringCustom(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.removeAttribute("custom1")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CUSTOM1)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeStringMissing(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.removeAttribute("sn")).isSameAs(entry);
        assertThat(entry.getAttributeCount()).isEqualTo(3);
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeStringPresent(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.removeAttribute("cn")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CN)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeStringValueMissing1(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.removeAttribute("cn", "missing")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CN)).isNotNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeStringValueMissing2(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.removeAttribute("sn", "missing")).isSameAs(entry);
        assertThat(entry.getAttributeCount()).isEqualTo(3);
    }

    @Test(dataProvider = "EntryFactory")
    public void testRemoveAttributeStringValuePresent(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.removeAttribute("cn", "test")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CN)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeAttributeMissingEmpty(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute(emptyAttribute(AD_SN))).isFalse();
        assertThat(entry.getAttribute(AD_SN)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeAttributeMissingValue(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute(singletonAttribute(AD_SN, "sn"))).isTrue();
        assertThat(entry.getAttribute(AD_SN)).isEqualTo(singletonAttribute(AD_SN, "sn"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeAttributePresentEmpty(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute(emptyAttribute(AD_CN))).isTrue();
        assertThat(entry.getAttribute(AD_CN)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeAttributePresentValue(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute(singletonAttribute(AD_CN, "newcn"))).isTrue();
        assertThat(entry.getAttribute(AD_CN)).isEqualTo(singletonAttribute(AD_CN, "newcn"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeStringCustomEmpty(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute("custom1")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CUSTOM1)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeStringCustomMissingValue(final EntryFactory factory)
            throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute("custom2", "xxxx")).isSameAs(entry);
        // This is expected to be null since the type was decoded using the
        // default schema and a temporary oid was allocated.
        assertThat(entry.getAttribute(AD_CUSTOM2)).isNull();
        assertThat(entry.getAttribute("custom2")).isEqualTo(singletonAttribute("custom2", "xxxx"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeStringCustomValue(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute("custom1", "xxxx")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CUSTOM1))
                .isEqualTo(singletonAttribute(AD_CUSTOM1, "xxxx"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeStringMissingEmpty(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute("sn")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_SN)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeStringMissingValue(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute("sn", "sn")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_SN)).isEqualTo(singletonAttribute(AD_SN, "sn"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeStringPresentEmpty(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute("cn")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CN)).isNull();
    }

    @Test(dataProvider = "EntryFactory")
    public void testReplaceAttributeStringPresentValue(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.replaceAttribute("cn", "newcn")).isSameAs(entry);
        assertThat(entry.getAttribute(AD_CN)).isEqualTo(singletonAttribute(AD_CN, "newcn"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testSetNameDN(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.setName(DN.valueOf("cn=foobar"))).isSameAs(entry);
        assertThat((Object) entry.getName()).isEqualTo(DN.valueOf("cn=foobar"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testSetNameString(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        assertThat(entry.setName("cn=foobar")).isSameAs(entry);
        assertThat((Object) entry.getName()).isEqualTo(DN.valueOf("cn=foobar"));
    }

    @Test(dataProvider = "EntryFactory")
    public void testToString(final EntryFactory factory) throws Exception {
        final Entry entry = createTestEntry(factory);
        // The String representation is unspecified but we should at least
        // expect the DN to be present.
        assertThat(entry.toString()).contains("cn=test");
    }

    private Entry createTestEntry(final EntryFactory factory) throws Exception {
        return factory.newEntry("dn: cn=test", "objectClass: top", "objectClass: extensibleObject",
                "cn: test", "custom1: custom1");
    }
}
