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
 *      Portions copyright 2012-2013 ForgeRock AS.
 */
package org.forgerock.opendj.io;

import java.io.IOException;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.StaticUtils;

import static org.testng.Assert.*;

/**
 * An abstract base class for all ASN1Writer test cases.
 */
@Test(groups = { "precommit", "asn1", "sdk" })
@SuppressWarnings("javadoc")
public abstract class ASN1WriterTestCase extends ForgeRockTestCase {

    /**
     * Create an array with a selection of the valid single-byte types. We don't
     * support multi-byte types, so this should be a comprehensive data set.
     */
    private final byte[] testTypes = { 0x00, 0x7f, (byte) 0x80, (byte) 0xff };

    /**
     * Create byte arrays to use for element values.
     *
     * @return A list of byte arrays that can be used as element values.
     */
    @DataProvider(name = "binaryValues")
    public Object[][] getBinaryValues() {
        // NOTE -- Don't make these arrays too big since they consume memory.
        return new Object[][] { new Object[] { new byte[0x00] }, // The
                                                                 // zero-byte
            // value
            new Object[] { new byte[0x01] }, // The single-byte value
            new Object[] { new byte[0x7F] }, // The largest 1-byte length
                                             // encoding
            new Object[] { new byte[0x80] }, // The smallest 2-byte length
                                             // encoding
            new Object[] { new byte[0xFF] }, // The largest 2-byte length
                                             // encoding
            new Object[] { new byte[0x0100] }, // The smallest 3-byte length
            // encoding
            new Object[] { new byte[0xFFFF] }, // The largest 3-byte length
                                               // encoding
            new Object[] { new byte[0x010000] } // The smallest 4-byte length
        // encoding
        };
    }

    /**
     * Retrieves the set of boolean values that may be used for testing.
     *
     * @return The set of boolean values that may be used for testing.
     */
    @DataProvider(name = "booleanValues")
    public Object[][] getBooleanValues() {
        return new Object[][] { new Object[] { false }, new Object[] { true } };
    }

    /**
     * Retrieves the set of int values that should be used for testing.
     *
     * @return The set of int values that should be used for testing.
     */
    @DataProvider(name = "intValues")
    public Object[][] getIntValues() {
        return new Object[][] { new Object[] { 0x00000000, 1 }, new Object[] { 0x00000001, 1 },
            new Object[] { 0x0000000F, 1 }, new Object[] { 0x00000010, 1 },
            new Object[] { 0x0000007F, 1 }, new Object[] { 0x00000080, 2 },
            new Object[] { 0x000000FF, 2 }, new Object[] { 0x00000100, 2 },
            new Object[] { 0x00000FFF, 2 }, new Object[] { 0x00001000, 2 },
            new Object[] { 0x0000FFFF, 3 }, new Object[] { 0x00010000, 3 },
            new Object[] { 0x000FFFFF, 3 }, new Object[] { 0x00100000, 3 },
            new Object[] { 0x00FFFFFF, 4 }, new Object[] { 0x01000000, 4 },
            new Object[] { 0x0FFFFFFF, 4 }, new Object[] { 0x10000000, 4 },
            new Object[] { 0x7FFFFFFF, 4 }, new Object[] { -0x00000001, 1 },
            new Object[] { -0x0000000F, 1 }, new Object[] { -0x00000010, 1 },
            new Object[] { -0x0000007F, 1 }, new Object[] { -0x00000080, 1 },
            new Object[] { -0x000000FF, 2 }, new Object[] { -0x00000100, 2 },
            new Object[] { -0x00000FFF, 2 }, new Object[] { -0x00001000, 2 },
            new Object[] { -0x0000FFFF, 3 }, new Object[] { -0x00010000, 3 },
            new Object[] { -0x000FFFFF, 3 }, new Object[] { -0x00100000, 3 },
            new Object[] { -0x00FFFFFF, 4 }, new Object[] { -0x01000000, 4 },
            new Object[] { -0x0FFFFFFF, 4 }, new Object[] { -0x10000000, 4 },
            new Object[] { -0x7FFFFFFF, 4 }, new Object[] { 0x80000000, 4 } };
    }

    /**
     * Retrieves the set of long values that should be used for testing.
     *
     * @return The set of long values that should be used for testing.
     */
    @DataProvider(name = "longValues")
    public Object[][] getLongValues() {
        return new Object[][] { new Object[] { 0x0000000000000000L, 1 },
            new Object[] { 0x0000000000000001L, 1 }, new Object[] { 0x000000000000007FL, 1 },
            new Object[] { 0x0000000000000080L, 2 }, new Object[] { 0x00000000000000FFL, 2 },
            new Object[] { 0x0000000000000100L, 2 }, new Object[] { 0x000000000000FFFFL, 3 },
            new Object[] { 0x0000000000010000L, 3 }, new Object[] { 0x0000000000FFFFFFL, 4 },
            new Object[] { 0x0000000001000000L, 4 }, new Object[] { 0x00000000FFFFFFFFL, 5 },
            new Object[] { 0x0000000100000000L, 5 }, new Object[] { 0x000000FFFFFFFFFFL, 6 },
            new Object[] { 0x0000010000000000L, 6 }, new Object[] { 0x0000FFFFFFFFFFFFL, 7 },
            new Object[] { 0x0001000000000000L, 7 }, new Object[] { 0x00FFFFFFFFFFFFFFL, 8 },
            new Object[] { 0x0100000000000000L, 8 }, new Object[] { 0x7FFFFFFFFFFFFFFFL, 8 },
            new Object[] { -0x0000000000000001L, 1 }, new Object[] { -0x000000000000007FL, 1 },
            new Object[] { -0x0000000000000080L, 1 }, new Object[] { -0x00000000000000FFL, 2 },
            new Object[] { -0x0000000000000100L, 2 }, new Object[] { -0x000000000000FFFFL, 3 },
            new Object[] { -0x0000000000010000L, 3 }, new Object[] { -0x0000000000FFFFFFL, 4 },
            new Object[] { -0x0000000001000000L, 4 }, new Object[] { -0x00000000FFFFFFFFL, 5 },
            new Object[] { -0x0000000100000000L, 5 }, new Object[] { -0x000000FFFFFFFFFFL, 6 },
            new Object[] { -0x0000010000000000L, 6 }, new Object[] { -0x0000FFFFFFFFFFFFL, 7 },
            new Object[] { -0x0001000000000000L, 7 }, new Object[] { -0x00FFFFFFFFFFFFFFL, 8 },
            new Object[] { -0x0100000000000000L, 8 }, new Object[] { -0x7FFFFFFFFFFFFFFFL, 8 },
            new Object[] { 0x8000000000000000L, 8 } };
    }

    /**
     * Create strings to use for element values.
     *
     * @return A list of strings that can be used as element values.
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @DataProvider(name = "stringValues")
    public Object[][] getStringValues() throws Exception {
        return new Object[][] { new Object[] { null }, new Object[] { "" },
            new Object[] { "\u0000" }, new Object[] { "\t" }, new Object[] { "\n" },
            new Object[] { "\r\n" }, new Object[] { " " }, new Object[] { "a" },
            new Object[] { "Test1\tTest2\tTest3" }, new Object[] { "Test1\nTest2\nTest3" },
            new Object[] { "Test1\r\nTest2\r\nTest3" },
            new Object[] { "The Quick Brown Fox Jumps Over The Lazy Dog" },
            new Object[] { "\u00BFD\u00F3nde est\u00E1 el ba\u00F1o?" } };
    }

    /**
     * Tests the <CODE>write/readBoolean</CODE> methods.
     *
     * @param b
     *            The boolean value to use in the test.
     */
    @Test(dataProvider = "booleanValues")
    public void testEncodeDecodeBoolean(final boolean b) throws Exception {
        getWriter().writeBoolean(b);

        final ASN1Reader r = getReader(getEncodedBytes());
        assertEquals(r.peekLength(), 1);
        assertEquals(r.peekType(), ASN1.UNIVERSAL_BOOLEAN_TYPE);
        assertEquals(r.readBoolean(), b);
    }

    /**
     * Tests the <CODE>write/readBoolean</CODE> methods.
     *
     * @param b
     *            The boolean value to use in the test.
     */
    @Test(dataProvider = "booleanValues")
    public void testEncodeDecodeBooleanType(final boolean b) throws Exception {
        for (final byte type : testTypes) {
            getWriter().writeBoolean(type, b);

            final ASN1Reader r = getReader(getEncodedBytes());
            assertEquals(r.peekLength(), 1);
            assertEquals(r.peekType(), type);
            assertEquals(r.readBoolean(), b);
        }
    }

    /**
     * Tests the <CODE>write/readInteger</CODE> methods with Java ints.
     *
     * @param i
     *            The integer value to use for the test.
     */
    @Test(dataProvider = "intValues")
    public void testEncodeDecodeEnuerated(final int i, final int length) throws Exception {
        getWriter().writeEnumerated(i);

        final ASN1Reader r = getReader(getEncodedBytes());
        assertEquals(r.peekLength(), length);
        assertEquals(r.peekType(), ASN1.UNIVERSAL_ENUMERATED_TYPE);
        assertEquals(r.readInteger(), i);
    }

    /**
     * Tests the <CODE>write/readInteger</CODE> methods with Java ints.
     *
     * @param i
     *            The integer value to use for the test.
     */
    @Test(dataProvider = "intValues")
    public void testEncodeDecodeInteger(final int i, final int length) throws Exception {
        getWriter().writeInteger(i);

        final ASN1Reader r = getReader(getEncodedBytes());
        assertEquals(r.peekLength(), length);
        assertEquals(r.peekType(), ASN1.UNIVERSAL_INTEGER_TYPE);
        assertEquals(r.readInteger(), i);
    }

    /**
     * Tests the <CODE>write/readInteger</CODE> methods with Java longs.
     *
     * @param l
     *            The long value to use for the test.
     */
    @Test(dataProvider = "longValues")
    public void testEncodeDecodeInteger(final long l, final int length) throws Exception {
        getWriter().writeInteger(l);

        final ASN1Reader r = getReader(getEncodedBytes());
        assertEquals(r.peekLength(), length);
        assertEquals(r.peekType(), ASN1.UNIVERSAL_INTEGER_TYPE);
        assertEquals(r.readInteger(), l);
    }

    /**
     * Tests the <CODE>write/readInteger</CODE> methods with Java ints.
     *
     * @param i
     *            The integer value to use for the test.
     */
    @Test(dataProvider = "intValues")
    public void testEncodeDecodeIntegerType(final int i, final int length) throws Exception {
        for (final byte type : testTypes) {
            getWriter().writeInteger(type, i);

            final ASN1Reader r = getReader(getEncodedBytes());
            assertEquals(r.peekLength(), length);
            assertEquals(r.peekType(), type);
            assertEquals(r.readInteger(), i);
        }
    }

    /**
     * Tests the <CODE>write/readInteger</CODE> methods wiht JavaLongs.
     *
     * @param l
     *            The long value to use for the test.
     */
    @Test(dataProvider = "longValues")
    public void testEncodeDecodeIntegerType(final long l, final int length) throws Exception {
        for (final byte type : testTypes) {
            getWriter().writeInteger(type, l);

            final ASN1Reader r = getReader(getEncodedBytes());
            assertEquals(r.peekLength(), length);
            assertEquals(r.peekType(), type);
            assertEquals(r.readInteger(), l);
        }
    }

    /**
     * Tests the <CODE>write/readNull</CODE> methods.
     */
    @Test
    public void testEncodeDecodeNull() throws Exception {
        getWriter().writeNull();

        final ASN1Reader r = getReader(getEncodedBytes());
        assertEquals(r.peekLength(), 0);
        assertEquals(r.peekType(), ASN1.UNIVERSAL_NULL_TYPE);
        r.readNull();
    }

    /**
     * Tests the <CODE>write/readNull</CODE> methods.
     */
    @Test
    public void testEncodeDecodeNullType() throws Exception {
        for (final byte type : testTypes) {
            getWriter().writeNull(type);

            final ASN1Reader r = getReader(getEncodedBytes());
            assertEquals(r.peekLength(), 0);
            assertEquals(r.peekType(), type);
            r.readNull();
        }
    }

    /**
     * Tests the <CODE>write/readOctetString</CODE> methods.
     */
    @Test(dataProvider = "binaryValues")
    public void testEncodeDecodeOctetString(final byte[] b) throws Exception {
        final ByteString bs = ByteString.wrap(b);

        getWriter().writeOctetString(bs);

        ASN1Reader r = getReader(getEncodedBytes());
        assertEquals(r.peekLength(), b.length);
        assertEquals(r.peekType(), ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        assertTrue(bs.equals(r.readOctetString()));

        getWriter().writeOctetString(b, 0, b.length);

        r = getReader(getEncodedBytes());
        assertEquals(r.peekLength(), b.length);
        assertEquals(r.peekType(), ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        assertTrue(bs.equals(r.readOctetString()));
    }

    /**
     * Tests the <CODE>write/readOctetString</CODE> methods.
     */
    @Test(dataProvider = "stringValues")
    public void testEncodeDecodeOctetString(final String s) throws Exception {
        getWriter().writeOctetString(s);

        final String expected = s != null ? s : "";
        final ASN1Reader r = getReader(getEncodedBytes());
        assertEquals(r.peekLength(), StaticUtils.getBytes(expected).length);
        assertEquals(r.peekType(), ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        assertEquals(r.readOctetStringAsString(), expected);
    }

    /**
     * Tests the <CODE>write/readOctetString</CODE> methods.
     */
    @Test
    public void testEncodeDecodeOctetStringOffLen() throws Exception {
        final byte[] b = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 };

        for (int i = 0; i < 5; i += 2) {
            final byte[] bsb = new byte[3];
            System.arraycopy(b, i, bsb, 0, 3);
            final ByteString bs = ByteString.wrap(bsb);
            getWriter().writeOctetString(b, i, 3);

            final ASN1Reader r = getReader(getEncodedBytes());
            assertEquals(r.peekLength(), 3);
            assertEquals(r.peekType(), ASN1.UNIVERSAL_OCTET_STRING_TYPE);
            assertTrue(bs.equals(r.readOctetString()));
        }
    }

    /**
     * Tests the <CODE>write/readOctetString</CODE> methods.
     */
    @Test(dataProvider = "binaryValues")
    public void testEncodeDecodeOctetStringType(final byte[] b) throws Exception {
        final ByteString bs = ByteString.wrap(b);
        final ByteStringBuilder bsb = new ByteStringBuilder();

        for (final byte type : testTypes) {
            bsb.clear();
            getWriter().writeOctetString(type, bs);

            ASN1Reader r = getReader(getEncodedBytes());
            assertEquals(r.peekLength(), b.length);
            assertEquals(r.peekType(), type);
            r.readOctetString(bsb);
            assertTrue(bs.equals(bsb));

            bsb.clear();
            getWriter().writeOctetString(type, b, 0, b.length);

            r = getReader(getEncodedBytes());
            assertEquals(r.peekLength(), b.length);
            assertEquals(r.peekType(), type);
            r.readOctetString(bsb);
            assertTrue(bs.equals(bsb));
        }
    }

    /**
     * Tests the <CODE>write/readOctetString</CODE> methods.
     */
    @Test(dataProvider = "stringValues")
    public void testEncodeDecodeOctetStringType(final String s) throws Exception {
        for (final byte type : testTypes) {
            getWriter().writeOctetString(type, s);

            final String expected = s != null ? s : "";
            final ASN1Reader r = getReader(getEncodedBytes());
            assertEquals(r.peekLength(), StaticUtils.getBytes(expected).length);
            assertEquals(r.peekType(), type);
            assertEquals(r.readOctetStringAsString(), expected);
        }
    }

    @Test
    public void testEncodeDecodeSequence() throws Exception {
        final ASN1Writer writer = getWriter();

        writer.writeStartSequence();

        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeInteger(0);
        writer.writeInteger(10L);
        writer.writeNull();
        writer.writeOctetString("test value");
        writer.writeOctetString("skip value");

        writer.writeStartSequence();
        writer.writeOctetString("nested sequence");
        writer.writeEndSequence();

        writer.writeStartSet();
        writer.writeOctetString("nested set");
        writer.writeEndSet();

        writer.writeEndSequence();

        final ASN1Reader reader = getReader(getEncodedBytes());
        assertEquals(reader.peekType(), ASN1.UNIVERSAL_SEQUENCE_TYPE);
        assertEquals(reader.peekLength(), 71);

        assertTrue(reader.hasNextElement());
        reader.readStartSequence();
        assertTrue(reader.hasNextElement());

        assertEquals(true, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readInteger());
        assertEquals(10, reader.readInteger());
        reader.readNull();
        assertEquals("test value", reader.readOctetStringAsString());
        reader.skipElement();

        assertEquals(reader.peekLength(), 17);
        assertEquals(reader.peekType(), ASN1.UNIVERSAL_SEQUENCE_TYPE);
        reader.readStartSequence();
        assertEquals("nested sequence", reader.readOctetStringAsString());
        reader.readEndSequence();

        assertEquals(reader.peekLength(), 12);
        assertEquals(reader.peekType(), ASN1.UNIVERSAL_SET_TYPE);
        reader.readStartSequence();
        assertEquals("nested set", reader.readOctetStringAsString());
        reader.readEndSequence();

        assertFalse(reader.hasNextElement());
        reader.readEndSequence();
        assertFalse(reader.elementAvailable());
    }

    /**
     * Tests that negative integers are encoded according to ASN.1 BER
     * specification.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testNegativeIntEncoding() throws Exception {
        // Some negative integers of interest
        // to test specific ranges/boundaries.
        getWriter().writeInteger(-1);
        byte[] value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);

        getWriter().writeInteger(-2);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFE);

        getWriter().writeInteger(-127);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0x81);

        getWriter().writeInteger(-128);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0x80);

        getWriter().writeInteger(-255);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);
        assertEquals(value[3], (byte) 0x01);

        getWriter().writeInteger(-256);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);
        assertEquals(value[3], (byte) 0x00);

        getWriter().writeInteger(-65535);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);
        assertEquals(value[3], (byte) 0x00);
        assertEquals(value[4], (byte) 0x01);

        getWriter().writeInteger(-65536);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);
        assertEquals(value[3], (byte) 0x00);
        assertEquals(value[4], (byte) 0x00);

        getWriter().writeInteger(-2147483647);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0x80);
        assertEquals(value[3], (byte) 0x00);
        assertEquals(value[4], (byte) 0x00);
        assertEquals(value[5], (byte) 0x01);

        getWriter().writeInteger(-2147483648);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0x80);
        assertEquals(value[3], (byte) 0x00);
        assertEquals(value[4], (byte) 0x00);
        assertEquals(value[5], (byte) 0x00);
    }

    /**
     * Tests that negative integers are encoded according to ASN.1 BER
     * specification.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testNegativeLongEncoding() throws Exception {
        // Some negative integers of interest
        // to test specific ranges/boundaries.
        getWriter().writeInteger(-1L);
        byte[] value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);

        getWriter().writeInteger(-2L);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFE);

        getWriter().writeInteger(-127L);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0x81);

        getWriter().writeInteger(-128L);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0x80);

        getWriter().writeInteger(-255L);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);
        assertEquals(value[3], (byte) 0x01);

        getWriter().writeInteger(-256L);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);
        assertEquals(value[3], (byte) 0x00);

        getWriter().writeInteger(-65535L);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);
        assertEquals(value[3], (byte) 0x00);
        assertEquals(value[4], (byte) 0x01);

        getWriter().writeInteger(-65536L);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0xFF);
        assertEquals(value[3], (byte) 0x00);
        assertEquals(value[4], (byte) 0x00);

        getWriter().writeInteger(-2147483647L);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0x80);
        assertEquals(value[3], (byte) 0x00);
        assertEquals(value[4], (byte) 0x00);
        assertEquals(value[5], (byte) 0x01);

        getWriter().writeInteger(-2147483648L);
        value = getEncodedBytes();
        assertEquals(value[2], (byte) 0x80);
        assertEquals(value[3], (byte) 0x00);
        assertEquals(value[4], (byte) 0x00);
        assertEquals(value[5], (byte) 0x00);
    }

    protected abstract byte[] getEncodedBytes() throws IOException, DecodeException;

    protected abstract ASN1Reader getReader(byte[] encodedBytes) throws DecodeException,
            IOException;

    protected abstract ASN1Writer getWriter() throws IOException;
}
