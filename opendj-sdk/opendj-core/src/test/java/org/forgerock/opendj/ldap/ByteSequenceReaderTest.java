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
    public void testReadByte(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        for (byte b : ba) {
            Assert.assertEquals(reader.readByte(), b);
        }

        // The next read should result in IOB exception.
        reader.readByte();
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testReadBytes(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            byte[] readArray = new byte[length];
            reader.readBytes(readArray);
            byte[] subArray = new byte[length];
            System.arraycopy(ba, ba.length - remaining, subArray, 0, length);
            Assert.assertTrue(Arrays.equals(readArray, subArray));

            remaining -= length;
        }

        // Any more reads should result in IOB exception.
        reader.readBytes(new byte[1]);
    }

    @Test(dataProvider = "readerProvider")
    public void testReadBytesWithOffset(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;
        byte[] readArray = new byte[ba.length];

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            reader.readBytes(readArray, ba.length - remaining, length);

            remaining -= length;
        }

        Assert.assertTrue(Arrays.equals(readArray, ba));
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeOffsetReadBytes(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        byte[] array = new byte[ba.length];
        reader.readBytes(array, -1, ba.length);
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeLengthReadBytes(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        byte[] array = new byte[ba.length];
        reader.readBytes(array, 0, -1);
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testBadOffLenReadBytes(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        byte[] array = new byte[ba.length];
        reader.readBytes(array, 3, ba.length);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testReadBERLength() {
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
            Assert.assertEquals(reader.readBERLength(), length);
        }

        // Last one is incomplete and should throw error
        reader.readBERLength();
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testOversizedReadBERLength() {
        ByteSequenceReader reader = ByteString.wrap(new byte[]{
            b(0x85), b(0x10), b(0x00), b(0x00), b(0x00), b(0x00)
        }).asReader();

        // Shouldn't be able to reader over a 4 byte length.
        reader.readBERLength();
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testUndersizedReadBERLength() {
        ByteSequenceReader reader = ByteString.empty().asReader();

        // Shouldn't be able to reader over a 4 byte length.
        reader.readBERLength();
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testReadByteSequence(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            ByteSequence readSequence = reader.readByteSequence(length);
            byte[] subArray = new byte[length];
            System.arraycopy(ba, ba.length - remaining, subArray, 0, length);
            Assert.assertTrue(Arrays.equals(readSequence.toByteArray(), subArray));

            remaining -= length;
        }

        // Any more reads should result in IOB exception.
        reader.readByteSequence(1);
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testReadByteString(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            ByteString readSequence = reader.readByteString(length);
            byte[] subArray = new byte[length];
            System.arraycopy(ba, ba.length - remaining, subArray, 0, length);
            Assert.assertTrue(Arrays.equals(readSequence.toByteArray(), subArray));

            remaining -= length;
        }

        // Any more reads should result in IOB exception.
        reader.readByteString(1);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testReadShort() {
        ByteSequenceReader reader = ByteString.wrap(new byte[]{
            b(0x80), b(0x00), b(0x7F), b(0xFF), b(0xFF)
        }).asReader();

        Assert.assertEquals(reader.readShort(), Short.MIN_VALUE);
        Assert.assertEquals(reader.readShort(), Short.MAX_VALUE);

        // Any more reads should result in IOB exception.
        reader.readShort();
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testReadInt() {
        ByteSequenceReader reader = ByteString.wrap(new byte[]{
            b(0x80), b(0x00), b(0x00), b(0x00), b(0x7F),
            b(0xFF), b(0xFF), b(0xFF), b(0xFF) }).asReader();

        Assert.assertEquals(reader.readInt(), Integer.MIN_VALUE);
        Assert.assertEquals(reader.readInt(), Integer.MAX_VALUE);

        // Any more reads should result in IOB exception.
        reader.readInt();
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testReadLong() {
        ByteSequenceReader reader = ByteString.wrap(new byte[]{
            b(0x80), b(0x00), b(0x00), b(0x00), b(0x00),
            b(0x00), b(0x00), b(0x00), b(0x7F), b(0xFF),
            b(0xFF), b(0xFF), b(0xFF), b(0xFF), b(0xFF),
            b(0xFF), b(0xFF) }).asReader();

        Assert.assertEquals(reader.readLong(), Long.MIN_VALUE);
        Assert.assertEquals(reader.readLong(), Long.MAX_VALUE);

        // Any more reads should result in IOB exception.
        reader.readLong();
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testReadString(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();
        int remaining = ba.length;

        while (remaining > 0) {
            int length = remaining / 2;
            if (length == 0) {
                length = remaining % 2;
            }

            String readString = reader.readStringUtf8(length);
            String subString = new String(ba, ba.length - remaining, length);
            Assert.assertTrue(readString.equals(subString));

            remaining -= length;
        }

        // Any more reads should result in IOB exception.
        reader.readStringUtf8(1);
    }

    @Test(dataProvider = "readerProvider", expectedExceptions = IndexOutOfBoundsException.class)
    public void testSetPosition(ByteSequenceReader reader, byte[] ba) {
        reader.rewind();

        for (int i = 0; i < ba.length; i++) {
            reader.position(i);
            String readString = reader.readStringUtf8(ba.length - i);
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
