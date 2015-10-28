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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test class for ByteSequenceReader.
 */
@SuppressWarnings("javadoc")
public class ByteSequenceReaderTest extends SdkTestCase {

    private static final byte[] EIGHT_BYTES =
        new byte[]{ b(0x01), b(0x02), b(0x03), b(0x04),
                    b(0x05), b(0x06), b(0x07), b(0x08) };

    private static byte b(int i) {
        return (byte) i;
    }

    @DataProvider(name = "readerProvider")
    public Object[][] byteSequenceReaderProvider() {
        return new Object[][] {
            { ByteString.wrap(EIGHT_BYTES).asReader(), EIGHT_BYTES },
            { new ByteStringBuilder().appendBytes(EIGHT_BYTES).asReader(), EIGHT_BYTES },
            { new ByteStringBuilder().appendBytes(EIGHT_BYTES).subSequence(0, 8).asReader(), EIGHT_BYTES }
        };
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testGet(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        for (byte b : ba) {
            Assert.assertEquals(reader.get(), b);
        }

        // The next get should result in IOB exception.
        reader.get();
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testBulkGet(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            byte[] readArray = new byte[length];
            reader.get(readArray);
            byte[] subArray = new byte[length];
            System.arraycopy(ba, ba.length - remaining, subArray, 0, length);
            Assert.assertTrue(Arrays.equals(readArray, subArray));

            remaining -= length;
        }

        // Any more gets should result in IOB exception.
        reader.get(new byte[1]);
    }

    @Test(dataProvider = "readerProvider")
    public void testBulkGetWithOffset(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;
        byte[] readArray = new byte[ba.length];

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            reader.get(readArray, ba.length - remaining, length);

            remaining -= length;
        }

        Assert.assertTrue(Arrays.equals(readArray, ba));
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeOffsetBulkGet(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        byte[] array = new byte[ba.length];
        reader.get(array, -1, ba.length);
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeLengthBulkGet(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        byte[] array = new byte[ba.length];
        reader.get(array, 0, -1);
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testBadOffLenBulkGet(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        byte[] array = new byte[ba.length];
        reader.get(array, 3, ba.length);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testGetBERLength() {
        ByteSequenceReader reader = ByteString.wrap(new byte[]{
            b(0x00), b(0x01), b(0x0F), b(0x10),
            b(0x7F),

            b(0x81), b(0xFF),

            b(0x82), b(0x01), b(0x00),
            b(0x82), b(0x0F), b(0xFF), b(0x82), b(0x10),
            b(0x00), b(0x82), b(0xFF), b(0xFF),

            b(0x83), b(0x01), b(0x00), b(0x00),
            b(0x83), b(0x0F), b(0xFF), b(0xFF),
            b(0x83), b(0x10), b(0x00), b(0x00),
            b(0x83), b(0xFF), b(0xFF), b(0xFF),

            b(0x84), b(0x01), b(0x00), b(0x00), b(0x00),
            b(0x84), b(0x0F), b(0xFF), b(0xFF), b(0xFF),
            b(0x84), b(0x10), b(0x00), b(0x00), b(0x00),
            b(0x84), b(0xFF), b(0xFF), b(0xFF), b(0xFF),

            b(0x84), b(0x10), b(0x00)
        }).asReader();

        int[] expectedLength = new int[]{
            0x00000000, 0x00000001, 0x0000000F, 0x00000010,
            0x0000007F,

            0x000000FF,

            0x00000100, 0x00000FFF, 0x00001000, 0x0000FFFF,

            0x00010000, 0x000FFFFF, 0x00100000, 0x00FFFFFF,

            0x01000000, 0x0FFFFFFF, 0x10000000, 0xFFFFFFFF
        };

        for (int length : expectedLength) {
            Assert.assertEquals(reader.getBERLength(), length);
        }

        // Last one is incomplete and should throw error
        reader.getBERLength();
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testOversizedGetBERLength() {
        ByteSequenceReader reader = ByteString.wrap(new byte[]{
            b(0x85), b(0x10), b(0x00), b(0x00), b(0x00), b(0x00)
        }).asReader();

        // Shouldn't be able to reader over a 4 byte length.
        reader.getBERLength();
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testUndersizedGetBERLength() {
        ByteSequenceReader reader = ByteString.empty().asReader();

        // Shouldn't be able to reader over a 4 byte length.
        reader.getBERLength();
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testGetByteSequence(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            ByteSequence readSequence = reader.getByteSequence(length);
            byte[] subArray = new byte[length];
            System.arraycopy(ba, ba.length - remaining, subArray, 0, length);
            Assert.assertTrue(Arrays.equals(readSequence.toByteArray(), subArray));

            remaining -= length;
        }

        // Any more gets should result in IOB exception.
        reader.getByteSequence(1);
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testGetByteString(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            ByteString readSequence = reader.getByteString(length);
            byte[] subArray = new byte[length];
            System.arraycopy(ba, ba.length - remaining, subArray, 0, length);
            Assert.assertTrue(Arrays.equals(readSequence.toByteArray(), subArray));

            remaining -= length;
        }

        // Any more gets should result in IOB exception.
        reader.getByteString(1);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testGetShort() {
        ByteSequenceReader reader = ByteString.wrap(new byte[]{
            b(0x80), b(0x00), b(0x7F), b(0xFF), b(0xFF)
        }).asReader();

        Assert.assertEquals(reader.getShort(), Short.MIN_VALUE);
        Assert.assertEquals(reader.getShort(), Short.MAX_VALUE);

        // Any more gets should result in IOB exception.
        reader.getShort();
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testGetInt() {
        ByteSequenceReader reader = ByteString.wrap(new byte[]{
            b(0x80), b(0x00), b(0x00), b(0x00), b(0x7F),
            b(0xFF), b(0xFF), b(0xFF), b(0xFF) }).asReader();

        Assert.assertEquals(reader.getInt(), Integer.MIN_VALUE);
        Assert.assertEquals(reader.getInt(), Integer.MAX_VALUE);

        // Any more gets should result in IOB exception.
        reader.getInt();
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testGetLong() {
        ByteSequenceReader reader = ByteString.wrap(new byte[]{
            b(0x80), b(0x00), b(0x00), b(0x00), b(0x00),
            b(0x00), b(0x00), b(0x00), b(0x7F), b(0xFF),
            b(0xFF), b(0xFF), b(0xFF), b(0xFF), b(0xFF),
            b(0xFF), b(0xFF) }).asReader();

        Assert.assertEquals(reader.getLong(), Long.MIN_VALUE);
        Assert.assertEquals(reader.getLong(), Long.MAX_VALUE);

        // Any more gets should result in IOB exception.
        reader.getLong();
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testGetString(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            String readString = reader.getString(length);
            String subString = new String(ba, ba.length - remaining, length);
            Assert.assertTrue(readString.equals(subString));

            remaining -= length;
        }

        // Any more gets should result in IOB exception.
        reader.getString(1);
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testSetPosition(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();

        for (int i = 0; i < ba.length; i++) {
            reader.position(i);
            String readString = reader.getString(ba.length - i);
            String subString = new String(ba, i, ba.length - i);
            Assert.assertTrue(readString.equals(subString));
        }

        // Setting an invalid position should result in IOB exception.
        reader.position(ba.length + 1);
    }

    @Test(dataProvider = "readerProvider")
    public void testRemaining(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();

        for (int i = 0; i < ba.length; i++) {
            reader.position(i);
            Assert.assertEquals(reader.remaining(), ba.length - i);
        }
    }

    @Test(dataProvider = "readerProvider")
    public void testRewind(ByteSequenceReader reader, byte[] ba) {
        reader.position(ba.length - 1);
        reader.rewind();
        Assert.assertEquals(reader.position(), 0);
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testSkip(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            reader.skip(length);
            remaining -= length;

            Assert.assertEquals(reader.position(), ba.length - remaining);
        }

        // Any more skips should result in IOB exception.
        reader.skip(1);
    }

    @Test(dataProvider = "readerProvider")
    public void testPeek(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();

        int length = ba.length;
        int pos = 0;
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length - i; j++) {
                if (j == 0) {
                    Assert.assertEquals(reader.peek(), ba[pos]);
                }
                Assert.assertEquals(reader.peek(j), ba[pos + j]);
            }
            pos++;
            if (pos < length) {
                reader.skip(1);
            }
        }
    }

}
