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
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test case for ByteStringBuilder.
 */
@SuppressWarnings("javadoc")
public class ByteStringBuilderTestCase extends ByteSequenceTestCase {
    private static final byte[] EIGHT_BYTES = new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
        (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 };

    /**
     * ByteSequence data provider.
     *
     * @return The array of ByteStrings and the bytes it should contain.
     */
    @DataProvider(name = "byteSequenceProvider")
    public Object[][] byteSequenceProvider() throws Exception {
        final Object[][] builders = byteStringBuilderProvider();
        final Object[][] addlSequences = new Object[builders.length + 1][];
        System.arraycopy(builders, 0, addlSequences, 0, builders.length);
        addlSequences[builders.length] =
                new Object[] { new ByteStringBuilder().append(EIGHT_BYTES).subSequence(2, 6),
                    new byte[] { (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06 } };

        return addlSequences;
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testAppendBadByteBufferLength1() {
        new ByteStringBuilder().append(ByteBuffer.wrap(new byte[5]), -1);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testAppendBadByteBufferLength2() {
        new ByteStringBuilder().append(ByteBuffer.wrap(new byte[5]), 6);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testAppendBadByteSequenceReaderLength1() {
        new ByteStringBuilder().append(ByteString.wrap(new byte[5]).asReader(), -1);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testAppendBadByteSequenceReaderLength2() {
        new ByteStringBuilder().append(ByteString.wrap(new byte[5]).asReader(), 6);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testAppendBadInputStreamLength() throws Exception {
        final ByteArrayInputStream stream = new ByteArrayInputStream(new byte[5]);
        new ByteStringBuilder().append(stream, -1);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testAppendBadLength1() {
        new ByteStringBuilder().append(new byte[5], 0, 6);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testAppendBadLength2() {
        new ByteStringBuilder().append(new byte[5], 0, -1);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testAppendBadOffset1() {
        new ByteStringBuilder().append(new byte[5], -1, 3);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testAppendBadOffset2() {
        new ByteStringBuilder().append(new byte[5], 6, 0);
    }

    @Test
    public void testAppendInputStream() throws Exception {
        final ByteStringBuilder bsb = new ByteStringBuilder();
        final ByteArrayInputStream stream = new ByteArrayInputStream(new byte[5]);
        Assert.assertEquals(bsb.append(stream, 10), 5);
    }

    @Test(dataProvider = "builderProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testClear(final ByteStringBuilder bs, final byte[] ba) {
        bs.clear();
        Assert.assertEquals(bs.length(), 0);
        bs.byteAt(0);
    }

    @Test
    public void testEnsureAdditionalCapacity() {
        final ByteStringBuilder bsb = new ByteStringBuilder(8);
        Assert.assertEquals(bsb.getBackingArray().length, 8);
        bsb.ensureAdditionalCapacity(43);
        bsb.ensureAdditionalCapacity(2);
        Assert.assertTrue(bsb.getBackingArray().length >= 43);
    }

    @Test(dataProvider = "builderProvider")
    public void testGetBackingArray(final ByteStringBuilder bs, final byte[] ba) {
        final byte[] trimmedArray = new byte[bs.length()];
        System.arraycopy(bs.getBackingArray(), 0, trimmedArray, 0, bs.length());
        Assert.assertTrue(Arrays.equals(trimmedArray, ba));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidCapacity() {
        new ByteStringBuilder(-1);
    }

    @Test
    public void testTrimToSize() {
        final ByteStringBuilder bsb = new ByteStringBuilder();
        bsb.append(EIGHT_BYTES);
        Assert.assertTrue(bsb.getBackingArray().length > 8);
        bsb.trimToSize();
        Assert.assertEquals(bsb.getBackingArray().length, 8);
    }

    @DataProvider(name = "builderProvider")
    private Object[][] byteStringBuilderProvider() throws Exception {
        final ByteBuffer testBuffer = ByteBuffer.wrap(EIGHT_BYTES);
        final ByteString testByteString = ByteString.wrap(EIGHT_BYTES);
        final ByteSequenceReader testByteReader = testByteString.asReader();
        final InputStream testStream = new ByteArrayInputStream(EIGHT_BYTES);
        final ByteStringBuilder testBuilderFromStream = new ByteStringBuilder(8);
        testBuilderFromStream.append(testStream, 8);

        return new Object[][] {
            { new ByteStringBuilder().append((byte) 0x00).append((byte) 0x01),
                new byte[] { (byte) 0x00, (byte) 0x01 } },
            {
                new ByteStringBuilder(5).append(
                        new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04 }).append(
                        new byte[] { (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 }),
                EIGHT_BYTES },
            { new ByteStringBuilder(3).append(EIGHT_BYTES, 0, 3).append(EIGHT_BYTES, 3, 5),
                EIGHT_BYTES },
            { new ByteStringBuilder().append(testBuffer, 3).append(testBuffer, 5), EIGHT_BYTES },
            { new ByteStringBuilder(2).append(testByteString), EIGHT_BYTES },
            { new ByteStringBuilder().append(testByteReader, 5).append(testByteReader, 3),
                EIGHT_BYTES },
            { testBuilderFromStream, EIGHT_BYTES },
            { new ByteStringBuilder().append(Short.MIN_VALUE).append(Short.MAX_VALUE),
                new byte[] { (byte) 0x80, (byte) 0x00, (byte) 0x7F, (byte) 0xFF } },
            {
                new ByteStringBuilder(5).append(Integer.MIN_VALUE).append(Integer.MAX_VALUE),
                new byte[] { (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x7F,
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF } },
            {
                new ByteStringBuilder().append(Long.MIN_VALUE).append(Long.MAX_VALUE),
                new byte[] { (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x7F, (byte) 0xFF, (byte) 0xFF,
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF } },
            { new ByteStringBuilder(11).append("this is a").append(" test"),
                "this is a test".getBytes("UTF-8") },
            { new ByteStringBuilder().append((Object) "this is a").append((Object) " test"),
                "this is a test".getBytes("UTF-8") },
            {
                new ByteStringBuilder().append("this is a".toCharArray()).append(
                        " test".toCharArray()), "this is a test".getBytes("UTF-8") },
            {
                new ByteStringBuilder().append((Object) "this is a".toCharArray()).append(
                        (Object) " test".toCharArray()), "this is a test".getBytes("UTF-8") },
            {
                new ByteStringBuilder().append((Object) EIGHT_BYTES).append((Object) EIGHT_BYTES),
                new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                    (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x01, (byte) 0x02, (byte) 0x03,
                    (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 } },
            {
                new ByteStringBuilder().appendBERLength(0x00000000).appendBERLength(0x00000001)
                        .appendBERLength(0x0000000F).appendBERLength(0x00000010).appendBERLength(
                                0x0000007F).appendBERLength(0x000000FF).appendBERLength(0x00000100)
                        .appendBERLength(0x00000FFF).appendBERLength(0x00001000).appendBERLength(
                                0x0000FFFF).appendBERLength(0x00010000).appendBERLength(0x000FFFFF)
                        .appendBERLength(0x00100000).appendBERLength(0x00FFFFFF).appendBERLength(
                                0x01000000).appendBERLength(0x0FFFFFFF).appendBERLength(0x10000000)
                        .appendBERLength(0xFFFFFFFF),
                new byte[] { (byte) 0x00, (byte) 0x01, (byte) 0x0F, (byte) 0x10, (byte) 0x7F,
                    (byte) 0x81, (byte) 0xFF, (byte) 0x82, (byte) 0x01, (byte) 0x00, (byte) 0x82,
                    (byte) 0x0F, (byte) 0xFF, (byte) 0x82, (byte) 0x10, (byte) 0x00, (byte) 0x82,
                    (byte) 0xFF, (byte) 0xFF, (byte) 0x83, (byte) 0x01, (byte) 0x00, (byte) 0x00,
                    (byte) 0x83, (byte) 0x0F, (byte) 0xFF, (byte) 0xFF, (byte) 0x83, (byte) 0x10,
                    (byte) 0x00, (byte) 0x00, (byte) 0x83, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                    (byte) 0x84, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x84,
                    (byte) 0x0F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x84, (byte) 0x10,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x84, (byte) 0xFF, (byte) 0xFF,
                    (byte) 0xFF, (byte) 0xFF } }, };
    }
}
