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
 *      Copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;

import java.util.NoSuchElementException;

import org.fest.util.Collections;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.testng.annotations.Test;

/**
 * Test {@code AttributeParser}.
 */
@SuppressWarnings("javadoc")
public final class AttributeParserTestCase extends SdkTestCase {

    @Test
    public void testAsBooleanTrue() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "enabled: true");
        assertThat(e.parseAttribute("enabled").asBoolean()).isTrue();
    }

    @Test
    public void testAsBooleanFalse() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "enabled: false");
        assertThat(e.parseAttribute("enabled").asBoolean()).isFalse();
    }

    @Test
    public void testAsBooleanTrueDefaultFalse() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "enabled: true");
        assertThat(e.parseAttribute("enabled").asBoolean(false)).isTrue();
    }

    @Test
    public void testAsBooleanFalseDefaultTrue() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "enabled: false");
        assertThat(e.parseAttribute("enabled").asBoolean(true)).isFalse();
    }

    @Test
    public void testAsBooleanMissing() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("enabled").asBoolean()).isNull();
    }

    @Test
    public void testAsBooleanMissingDefaultTrue() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("enabled").asBoolean(true)).isTrue();
    }

    @Test
    public void testAsBooleanMissingDefaultFalse() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("enabled").asBoolean(false)).isFalse();
    }

    @Test(expectedExceptions = { NoSuchElementException.class })
    public void testAsBooleanMissingRequired() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        e.parseAttribute("enabled").requireValue().asBoolean();
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testAsBooleanInvalid() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "enabled: xxx");
        e.parseAttribute("enabled").asBoolean();
    }

    @Test
    public void testAsInteger99() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "age: 99");
        assertThat(e.parseAttribute("age").asInteger()).isEqualTo(99);
    }

    @Test
    public void testAsInteger99Default100() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "age: 99");
        assertThat(e.parseAttribute("age").asInteger(100)).isEqualTo(99);
    }

    @Test
    public void testAsIntegerMissing() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("age").asInteger()).isNull();
    }

    @Test
    public void testAsIntegerMissingDefault100() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("age").asInteger(100)).isEqualTo(100);
    }

    @Test(expectedExceptions = { NoSuchElementException.class })
    public void testAsIntegerMissingRequired() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        e.parseAttribute("age").requireValue().asInteger();
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testAsIntegerInvalid() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "age: xxx");
        e.parseAttribute("age").asInteger();
    }

    @Test
    public void testAsLong99() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "age: 99");
        assertThat(e.parseAttribute("age").asLong()).isEqualTo(99);
    }

    @Test
    public void testAsLong99Default100() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "age: 99");
        assertThat(e.parseAttribute("age").asLong(100)).isEqualTo(99);
    }

    @Test
    public void testAsLongMissing() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("age").asLong()).isNull();
    }

    @Test
    public void testAsLongMissingDefault100() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("age").asLong(100)).isEqualTo(100);
    }

    @Test(expectedExceptions = { NoSuchElementException.class })
    public void testAsLongMissingRequired() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        e.parseAttribute("age").requireValue().asLong();
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testAsLongInvalid() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "age: xxx");
        e.parseAttribute("age").asLong();
    }

    @Test
    public void testAsDN() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "manager: cn=manager");
        assertThat((Object) e.parseAttribute("manager").asDN()).isEqualTo(DN.valueOf("cn=manager"));
    }

    @Test
    public void testAsDNDefault() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "manager: cn=manager");
        assertThat((Object) e.parseAttribute("manager").asDN("cn=boss")).isEqualTo(
                DN.valueOf("cn=manager"));
    }

    @Test
    public void testAsDNMissing() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("manager").asDN()).isNull();
    }

    @Test
    public void testAsDNMissingDefault() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat((Object) e.parseAttribute("manager").asDN(DN.valueOf("cn=boss"))).isEqualTo(
                DN.valueOf("cn=boss"));
    }

    @Test(expectedExceptions = { NoSuchElementException.class })
    public void testAsDNMissingRequired() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        e.parseAttribute("manager").requireValue().asDN();
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testAsDNInvalid() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "manager: xxx");
        e.parseAttribute("manager").asDN();
    }

    @Test
    public void testAsAttributeDescription() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "type: cn");
        assertThat(e.parseAttribute("type").asAttributeDescription()).isEqualTo(
                AttributeDescription.valueOf("cn"));
    }

    @Test
    public void testAsAttributeDescriptionDefault() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "type: cn");
        assertThat(e.parseAttribute("type").asAttributeDescription("sn")).isEqualTo(
                AttributeDescription.valueOf("cn"));
    }

    @Test
    public void testAsAttributeDescriptionMissing() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("type").asAttributeDescription()).isNull();
    }

    @Test
    public void testAsAttributeDescriptionMissingDefault() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(
                e.parseAttribute("type").asAttributeDescription(AttributeDescription.valueOf("sn")))
                .isEqualTo(AttributeDescription.valueOf("sn"));
    }

    @Test(expectedExceptions = { NoSuchElementException.class })
    public void testAsAttributeDescriptionMissingRequired() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        e.parseAttribute("type").requireValue().asAttributeDescription();
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testAsAttributeDescriptionInvalid() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "type: ;x");
        e.parseAttribute("type").asAttributeDescription();
    }

    @Test
    public void testAsString() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "type: cn");
        assertThat(e.parseAttribute("type").asString()).isEqualTo(String.valueOf("cn"));
    }

    @Test
    public void testAsStringDefault() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "type: cn");
        assertThat(e.parseAttribute("type").asString("sn")).isEqualTo(String.valueOf("cn"));
    }

    @Test
    public void testAsStringMissing() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("type").asString()).isNull();
    }

    @Test
    public void testAsStringMissingDefault() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("type").asString(String.valueOf("sn"))).isEqualTo(
                String.valueOf("sn"));
    }

    @Test(expectedExceptions = { NoSuchElementException.class })
    public void testAsStringMissingRequired() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        e.parseAttribute("type").requireValue().asString();
    }

    @Test
    public void testAsByteString() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "type: cn");
        assertThat(e.parseAttribute("type").asByteString()).isEqualTo(ByteString.valueOfUtf8("cn"));
    }

    @Test
    public void testAsByteStringDefault() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test", "type: cn");
        assertThat(e.parseAttribute("type").asByteString(ByteString.valueOfUtf8("sn"))).isEqualTo(
                ByteString.valueOfUtf8("cn"));
    }

    @Test
    public void testAsByteStringMissing() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("type").asByteString()).isNull();
    }

    @Test
    public void testAsByteStringMissingDefault() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        assertThat(e.parseAttribute("type").asByteString(ByteString.valueOfUtf8("sn"))).isEqualTo(
                ByteString.valueOfUtf8("sn"));
    }

    @Test(expectedExceptions = { NoSuchElementException.class })
    public void testAsByteStringMissingRequired() {
        Entry e = new LinkedHashMapEntry("dn: cn=test", "objectClass: test");
        e.parseAttribute("type").requireValue().asByteString();
    }

    /**
     * Smoke test for set of methods: use one type only since the code is common
     * and we've already tested the parsing.
     */
    @Test
    public void testAsSetOfDN() {
        Entry e =
                new LinkedHashMapEntry("dn: cn=group", "objectClass: group", "member: cn=member1",
                        "member: cn=member2", "member: cn=member3");
        assertThat(e.parseAttribute("member").asSetOfDN()).isEqualTo(
                Collections.set(DN.valueOf("cn=member1"), DN.valueOf("cn=member2"), DN
                        .valueOf("cn=member3")));
    }

    @Test
    public void testAsSetOfDNDefault() {
        Entry e =
                new LinkedHashMapEntry("dn: cn=group", "objectClass: group", "member: cn=member1",
                        "member: cn=member2", "member: cn=member3");
        assertThat(e.parseAttribute("member").asSetOfDN("cn=dummy1", "cn=dummy2")).isEqualTo(
                Collections.set(DN.valueOf("cn=member1"), DN.valueOf("cn=member2"), DN
                        .valueOf("cn=member3")));
    }

    @Test
    public void testAsSetOfDNMissing() {
        Entry e = new LinkedHashMapEntry("dn: cn=group", "objectClass: group");
        assertThat(e.parseAttribute("member").asSetOfDN()).isEqualTo(
                java.util.Collections.emptySet());
    }

    @Test
    public void testAsSetOfDNMissingDefault() {
        Entry e = new LinkedHashMapEntry("dn: cn=group", "objectClass: group");
        assertThat(e.parseAttribute("member").asSetOfDN("cn=dummy1", "cn=dummy2")).isEqualTo(
                Collections.set(DN.valueOf("cn=dummy1"), DN.valueOf("cn=dummy2")));
    }

    @Test(expectedExceptions = { NoSuchElementException.class })
    public void testAsSetOfDNMissingRequired() {
        Entry e = new LinkedHashMapEntry("dn: cn=group", "objectClass: group");
        e.parseAttribute("member").requireValue().asSetOfDN();
    }

    @Test(expectedExceptions = { LocalizedIllegalArgumentException.class })
    public void testAsSetOfDNInvalid() {
        Entry e =
                new LinkedHashMapEntry("dn: cn=group", "objectClass: group", "member: cn=member1",
                        "member: xxxx");
        e.parseAttribute("member").asSetOfDN();
    }

}
