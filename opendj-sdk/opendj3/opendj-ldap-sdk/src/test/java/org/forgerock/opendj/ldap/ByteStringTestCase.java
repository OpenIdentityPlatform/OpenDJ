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
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import java.util.Arrays;

import javax.xml.bind.DatatypeConverter;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the ByteString class.
 */
@SuppressWarnings("javadoc")
public class ByteStringTestCase extends ByteSequenceTestCase {
    /**
     * ByteString data provider.
     *
     * @return The array of ByteStrings and the bytes it should contain.
     */
    @DataProvider(name = "byteSequenceProvider")
    public Object[][] byteSequenceProvider() throws Exception {
        byte[] testBytes =
                new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                    (byte) 0x06, (byte) 0x07, (byte) 0x08 };

        return new Object[][] {
            { ByteString.empty(), new byte[0] },
            { ByteString.valueOfBase64("AAA="), new byte[] { 0x00, 0x00 }},
            { ByteString.valueOfBase64("AAAA"), new byte[] { 0x00, 0x00, 0x00 }},
            { ByteString.valueOfBase64("AAAAAA=="), new byte[] { 0x00, 0x00, 0x00, 0x00 }},
            { ByteString.valueOf(1),
                new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01 } },
            { ByteString.valueOf(Integer.MAX_VALUE),
                new byte[] { (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF } },
            { ByteString.valueOf(Integer.MIN_VALUE),
                new byte[] { (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00 } },
            {
                ByteString.valueOf(Long.MAX_VALUE),
                new byte[] { (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF } },
            {
                ByteString.valueOf(Long.MIN_VALUE),
                new byte[] { (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00 } },
            { ByteString.valueOf("cn=testvalue"), "cn=testvalue".getBytes("UTF-8") },
            { ByteString.valueOf((Object) "cn=testvalue"), "cn=testvalue".getBytes("UTF-8") },
            { ByteString.valueOf("cn=testvalue".toCharArray()), "cn=testvalue".getBytes("UTF-8") },
            { ByteString.valueOf((Object) "cn=testvalue".toCharArray()),
                "cn=testvalue".getBytes("UTF-8") },
            { ByteString.valueOf(testBytes), testBytes },
            { ByteString.valueOf((Object) testBytes), testBytes },
            { ByteString.valueOf(ByteString.valueOf("cn=testvalue")),
                "cn=testvalue".getBytes("UTF-8") },
            { ByteString.wrap(new byte[0]), new byte[0] },
            {
                ByteString.wrap(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                    (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 }),
                new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                    (byte) 0x06, (byte) 0x07, (byte) 0x08 } },
            {
                ByteString.wrap(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                    (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x10 },
                        0, 8),
                new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                    (byte) 0x06, (byte) 0x07, (byte) 0x08 } },
            {
                ByteString.wrap(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                    (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x10 },
                        1, 8),
                new byte[] { (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06,
                    (byte) 0x07, (byte) 0x08, (byte) 0x09 } },
            {
                ByteString.wrap(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                    (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x10 },
                        2, 8),
                new byte[] { (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                    (byte) 0x08, (byte) 0x09, (byte) 0x10 } },
            {
                ByteString.wrap(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                    (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 }, 3, 0), new byte[0] }, };
    }

    @DataProvider(name = "byteStringIntegerProvider")
    public Object[][] byteStringIntegerProvider() {
        return new Object[][] { { ByteString.valueOf(0), 0 }, { ByteString.valueOf(1), 1 },
            { ByteString.valueOf(Integer.MAX_VALUE), Integer.MAX_VALUE },
            { ByteString.valueOf(Integer.MIN_VALUE), Integer.MIN_VALUE }, };
    }

    @DataProvider(name = "byteStringLongProvider")
    public Object[][] byteStringLongProvider() {
        return new Object[][] { { ByteString.valueOf(0L), 0L }, { ByteString.valueOf(1L), 1L },
            { ByteString.valueOf(Long.MAX_VALUE), Long.MAX_VALUE },
            { ByteString.valueOf(Long.MIN_VALUE), Long.MIN_VALUE } };
    }

    @DataProvider(name = "byteStringCharArrayProvider")
    public Object[][] byteStringCharArrayProvider() {
        return new Object[][] { { "" }, { "1" }, { "1234567890" } };
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testInvalidWrapLength() {
        ByteString.wrap(new byte[] { (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03 }, 2, 8);
    }

    @Test(dataProvider = "byteStringIntegerProvider")
    public void testToInteger(final ByteString bs, final int i) {
        Assert.assertEquals(bs.toInt(), i);
    }

    @Test(dataProvider = "byteStringLongProvider")
    public void testToLong(final ByteString bs, final long l) {
        Assert.assertEquals(bs.toLong(), l);
    }

    @Test(dataProvider = "byteStringCharArrayProvider")
    public void testFromStringToCharArray(final String s) {
        ByteString bs = ByteString.valueOf(s);
        Assert.assertTrue(Arrays.equals(bs.toCharArray(), s.toCharArray()));
    }

    @Test(dataProvider = "byteStringCharArrayProvider")
    public void testFromCharArrayToCharArray(final String s) {
        final char[] chars = s.toCharArray();
        ByteString bs = ByteString.valueOf(chars);
        Assert.assertTrue(Arrays.equals(bs.toCharArray(), chars));
    }

    @Test(dataProvider = "byteStringCharArrayProvider")
    public void testValueOfCharArray(final String s) {
        ByteString bs = ByteString.valueOf(s.toCharArray());
        Assert.assertEquals(bs.toString(), s);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testUndersizedToInteger() {
        ByteString.wrap(new byte[] { (byte) 0x00, (byte) 0x01 }).toInt();
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testUndersizedToLong() {
        ByteString.wrap(new byte[] { (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03 }).toLong();
    }

    /**
     * Base 64 invalid test data provider.
     *
     * @return Returns an array of invalid encoded base64 data.
     */
    @DataProvider(name = "invalidBase64Data")
    public Object[][] createInvalidBase64Data() {
        // FIXME: fix cases ==== and ==x=

        return new Object[][] { { "=" }, { "==" }, { "===" }, { "A" }, { "AA" }, { "AAA" },
            { "AA`=" }, { "AA~=" }, { "AA!=" }, { "AA@=" }, { "AA#=" }, { "AA$=" }, { "AA%=" },
            { "AA^=" }, { "AA*=" }, { "AA(=" }, { "AA)=" }, { "AA_=" }, { "AA-=" }, { "AA{=" },
            { "AA}=" }, { "AA|=" }, { "AA[=" }, { "AA]=" }, { "AA\\=" }, { "AA;=" }, { "AA'=" },
            { "AA\"=" }, { "AA:=" }, { "AA,=" }, { "AA.=" }, { "AA<=" }, { "AA>=" }, { "AA?=" },
            { "AA;=" } };
    }

    /**
     * Base 64 valid test data provider.
     *
     * @return Returns an array of decoded and valid encoded base64 data.
     */
    @DataProvider(name = "validBase64Data")
    public Object[][] createValidBase64Data() {
        return new Object[][] {
            { "", "" },
            { "00", "AA==" },
            { "01", "AQ==" },
            { "02", "Ag==" },
            { "03", "Aw==" },
            { "04", "BA==" },
            { "05", "BQ==" },
            { "06", "Bg==" },
            { "07", "Bw==" },
            { "0000", "AAA=" },
            { "000000", "AAAA" },
            { "00000000", "AAAAAA==" },
            {
                "000102030405060708090a0b0c0d0e0f" + "101112131415161718191a1b1c1d1e1f"
                        + "202122232425262728292a2b2c2d2e2f" + "303132333435363738393a3b3c3d3e3f"
                        + "404142434445464748494a4b4c4d4e4f" + "505152535455565758595a5b5c5d5e5f"
                        + "606162636465666768696a6b6c6d6e6f" + "707172737475767778797a7b7c7d7e7f"
                        + "808182838485868788898a8b8c8d8e8f" + "909192939495969798999a9b9c9d9e9f"
                        + "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf" + "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf"
                        + "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf" + "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf"
                        + "e0e1e2e3e4e5e6e7e8e9eaebecedeeef" + "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4v"
                        + "MDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5f"
                        + "YGFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6P"
                        + "kJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6ytrq+wsbKztLW2t7i5uru8vb6/"
                        + "wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX2Nna29zd3t/g4eLj5OXm5+jp6uvs7e7v"
                        + "8PHy8/T19vf4+fr7/P3+/w==" }, };
    }

    @Test(dataProvider = "invalidBase64Data",
            expectedExceptions = { LocalizedIllegalArgumentException.class })
    public void testValueOfBase64ThrowsLIAE(final String invalidBase64) throws Exception {
        Assert.fail("Expected exception but got result: "
                + Arrays.toString(new ByteString[] { ByteString.valueOfBase64(invalidBase64) }));
    }

    @Test(dataProvider = "validBase64Data")
    public void testValueOfBase64(final String hexData, final String encodedData) throws Exception {
        final byte[] data = DatatypeConverter.parseHexBinary(hexData);
        final byte[] decodedData = ByteString.valueOfBase64(encodedData).toByteArray();
        Assert.assertEquals(decodedData, data);
    }
}
