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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import org.fest.assertions.Assertions;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.SubstringReader;

/**
 * Test schema utilities.
 */
@SuppressWarnings("javadoc")
public class SchemaUtilsTest extends AbstractSchemaTestCase {

    @DataProvider(name = "invalidOIDs")
    public Object[][] createInvalidOIDs() {
        return new Object[][] { { "" }, { ".0" }, { "0." }, { "100." }, { ".999" }, { "1one" },
            { "one+two+three" }, { "one.two.three" },
            // AD puts quotes around OIDs - test mismatched quotes.
            { "'0" }, { "'10" }, { "999'" }, { "0.0'" }, };
    }

    @DataProvider(name = "validOIDs")
    public Object[][] createValidOIDs() {
        return new Object[][] {
            // Compliant NOIDs
            { "0.0" }, { "1.0" }, { "2.0" }, { "3.0" }, { "4.0" }, { "5.0" }, { "6.0" }, { "7.0" },
            { "8.0" }, { "9.0" }, { "0.1" }, { "0.2" }, { "0.3" }, { "0.4" }, { "0.5" }, { "0.6" },
            { "0.7" }, { "0.8" }, { "0.9" }, { "10.0" }, { "100.0" }, { "999.0" }, { "0.100" },
            { "0.999" }, { "100.100" }, { "999.999" }, { "111.22.333.44.55555.66.777.88.999" },
            { "a" },
            { "a2" },
            { "a-" },
            { "one" },
            { "one1" },
            { "one-two" },
            { "one1-two2-three3" },
            // AD puts quotes around OIDs - not compliant but we need to
            // handle them.
            { "'0.0'" }, { "'10.0'" }, { "'999.0'" }, { "'111.22.333.44.55555.66.777.88.999'" },
            { "'a'" }, { "'a2'" }, { "'a-'" }, { "'one'" }, { "'one1'" }, { "'one-two'" },
            { "'one1-two2-three3'" },
            // Not strictly legal, but we'll be lenient with what we accept.
            { "0" }, { "1" }, { "2" }, { "3" }, { "4" }, { "5" }, { "6" }, { "7" }, { "8" },
            { "9" }, { "00" }, { "01" }, { "01.0" }, { "0.01" }, };
    }

    @Test(dataProvider = "invalidOIDs", expectedExceptions = DecodeException.class)
    public void testReadOIDInvalid(final String oid) throws DecodeException {
        final SubstringReader reader = new SubstringReader(oid);
        SchemaUtils.readOID(reader, false);
    }

    @Test(dataProvider = "validOIDs")
    public void testReadOIDValid(final String oid) throws DecodeException {
        String expected = oid;
        if (oid.startsWith("'")) {
            expected = oid.substring(1, oid.length() - 1);
        }

        final SubstringReader reader = new SubstringReader(oid);
        Assert.assertEquals(SchemaUtils.readOID(reader, false), expected);
    }

    @DataProvider
    public Object[][] nonAsciiStringProvider() throws Exception {
        final String nonAsciiChars = "ëéèêœ";
        final String nonAsciiCharsReplacement = new String(
                new byte[] { b(0x65), b(0xcc), b(0x88), b(0x65), b(0xcc),
                    b(0x81), b(0x65), b(0xcc), b(0x80), b(0x65), b(0xcc),
                    b(0x82), b(0xc5), b(0x93), }, "UTF8");
        return new Object[][] {
            { nonAsciiChars, false, false, nonAsciiCharsReplacement },
            { nonAsciiChars, false, true,  nonAsciiCharsReplacement },
            { nonAsciiChars, true,  false, nonAsciiCharsReplacement },
            { nonAsciiChars, true,  true,  nonAsciiCharsReplacement },
        };
    }

    @DataProvider
    public Object[][] stringProvider() throws Exception {
        final String allSpaceChars = "\u0009\n\u000b\u000c\r\u000e";
        final String mappedToNothingChars = "\u007F"
            + "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008"
            + "\u000E\u000F"
            + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019"
            + "\u001A\u001B\u001C\u001D\u001E\u001F";
        return new Object[][] {
            // empty always remains empty
            { "", false, false, "" },
            { "", false, true,  "" },
            { "", true,  false, "" },
            { "", true,  true,  "" },
            // double space chars are always converted to single char
            { "  ", false, false, " " },
            { "  ", false, true,  " " },
            { "  ", true,  false, " " },
            { "  ", true,  true,  " " },
            // trim all space chars to a single space
            { allSpaceChars, false, false, " " },
            { allSpaceChars, false, true,  " " },
            { allSpaceChars, true,  false, " " },
            { allSpaceChars, true,  true,  " " },
            // remove chars that are not mapped to anything
            { mappedToNothingChars, false, false, " " },
            { mappedToNothingChars, false, true,  " " },
            { mappedToNothingChars, true,  false, " " },
            { mappedToNothingChars, true,  true,  " " },
        };
    }

    /** Mixes trimming and case folding tests. */
    @DataProvider
    public Object[][] stringWithSpacesProvider() {
        return new Object[][] {
            { " this is a string ", false, false, " this is a string " },
            { " this is a string ", false, true,  " this is a string " },
            { " this is a string ", true,  false, "this is a string" },
            { " this is a string ", true,  true,  "this is a string" },
            { "   this  is    a   string  ", false, false, " this is a string " },
            { "   this  is    a   string  ", false, true,  " this is a string " },
            { "   this  is    a   string  ", true,  false, "this is a string" },
            { "   this  is    a   string  ", true,  true,  "this is a string" },
            { " THIS IS A STRING ", false, false, " THIS IS A STRING " },
            { " THIS IS A STRING ", false, true,  " this is a string " },
            { " THIS IS A STRING ", true,  false, "THIS IS A STRING" },
            { " THIS IS A STRING ", true,  true,  "this is a string" },
        };
    }

    private byte b(int i) {
        return (byte) i;
    }

    @Test(dataProvider = "stringProvider")
    public void testNormalizeStringProvider(String value, boolean trim, boolean foldCase, String expected)
            throws Exception {
        ByteString val = ByteString.valueOfUtf8(value);
        ByteString normValue = SchemaUtils.normalizeStringAttributeValue(val, trim, foldCase);
        Assertions.assertThat(normValue.toString()).isEqualTo(expected);
    }

    @Test(dataProvider = "nonAsciiStringProvider")
    public void testNormalizeStringWithNonAscii(String value, boolean trim, boolean foldCase, String expected)
            throws Exception {
        testNormalizeStringProvider(value, trim, foldCase, expected);
    }

    @Test(dataProvider = "stringWithSpacesProvider")
    public void testNormalizeStringWithSpaces(String value, boolean trim, boolean foldCase, String expected)
            throws Exception {
        testNormalizeStringProvider(value, trim, foldCase, expected);
    }

    @Test(dataProvider = "stringProvider")
    public void testNormalizeIA5String(String value, boolean trim, boolean foldCase, String expected)
            throws Exception {
        ByteString val = ByteString.valueOfUtf8(value);
        ByteString normValue = SchemaUtils.normalizeIA5StringAttributeValue(val, trim, foldCase);
        Assertions.assertThat(normValue.toString()).isEqualTo(expected);
    }

    @Test(dataProvider = "nonAsciiStringProvider", expectedExceptions = { DecodeException.class })
    public void testNormalizeIA5StringShouldThrowForNonAscii(
            String value, boolean trim, boolean foldCase, String expected) throws Exception {
        testNormalizeIA5String(value, trim, foldCase, expected);
    }

    @Test(dataProvider = "stringWithSpacesProvider")
    public void testNormalizeIA5StringWithSpaces(String value, boolean trim, boolean foldCase, String expected)
            throws Exception {
        testNormalizeIA5String(value, trim, foldCase, expected);
    }

    @Test(dataProvider = "stringProvider")
    public void testNormalizeStringList(String value, boolean trim, boolean foldCase, String expected)
            throws Exception {
        ByteString val = ByteString.valueOfUtf8(value);
        ByteString normValue = SchemaUtils.normalizeStringListAttributeValue(val, trim, foldCase);
        Assertions.assertThat(normValue.toString()).isEqualTo(expected);
    }

    @Test(dataProvider = "nonAsciiStringProvider")
    public void testNormalizeStringListWithNonAscii(String value, boolean trim, boolean foldCase, String expected)
            throws Exception {
        testNormalizeStringList(value, trim, foldCase, expected);
    }

    @DataProvider
    public Object[][] stringListProvider() throws Exception {
        return new Object[][] {
            { "this$is$a$list", false, false, "this$is$a$list" },
            { "this$is$a$list", false, true,  "this$is$a$list"},
            { "this$is$a$list", true,  false, "this$is$a$list" },
            { "this$is$a$list", true,  true,  "this$is$a$list" },
            { "this $ is $ a $ list", false, false, "this$is$a$list" },
            { "this $ is $ a $ list", false, true,  "this$is$a$list" },
            { "this $ is $ a $ list", true,  false, "this$is$a$list" },
            { "this $ is $ a $ list", true,  true,  "this$is$a$list" },
            { "this $ is \\\\ $ a $ list", false, false, "this$is \\\\$a$list" },
            { "this $ is \\\\ $ a $ list", false, true,  "this$is \\\\$a$list" },
            { "this $ is \\\\ $ a $ list", true,  false, "this$is \\\\$a$list" },
            { "this $ is \\\\ $ a $ list", true,  true,  "this$is \\\\$a$list" },
            { "$ this $ is $ a $ list", false, false, "$this$is$a$list" },
            { "$ this $ is $ a $ list", false, true,  "$this$is$a$list" },
            { "$ this $ is $ a $ list", true,  false, "$this$is$a$list" },
            { "$ this $ is $ a $ list", true,  true,  "$this$is$a$list" },
        };
    }

    @Test(dataProvider = "stringListProvider")
    public void testNormalizeStringListWithList(String value, boolean trim, boolean foldCase, String expected)
            throws Exception {
        testNormalizeStringList(value, trim, foldCase, expected);
    }

    @DataProvider
    public Object[][] numericStringProvider() throws Exception {
        return new Object[][] {
            { "", "" },
            { "   ", "" },
            { " 123  ", "123" },
            { " 123  456  ", "123456" },
        };
    }

    @Test(dataProvider = "numericStringProvider")
    public void testNormalizeNumericString(String value, String expected) throws Exception {
        ByteString val = ByteString.valueOfUtf8(value);
        ByteString normValue = SchemaUtils.normalizeNumericStringAttributeValue(val);
        Assertions.assertThat(normValue.toString()).isEqualTo(expected);
    }
}
