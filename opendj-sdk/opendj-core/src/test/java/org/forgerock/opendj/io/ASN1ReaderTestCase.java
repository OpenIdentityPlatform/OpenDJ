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
 *      Portions copyright 2012-2015 ForgeRock AS.
 *      Portions Copyright 2014 Manuel Gaupp
 */
package org.forgerock.opendj.io;

import static org.testng.Assert.*;

import java.io.IOException;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * An abstract base class for all ASN1Reader test cases.
 */
@Test(groups = { "precommit", "asn1", "sdk" })
@SuppressWarnings("javadoc")
public abstract class ASN1ReaderTestCase extends ForgeRockTestCase {

    /**
     * Retrieves the set of byte array values that may be used for testing.
     *
     * @return The set of byte array values that may be used for testing.
     */
    @DataProvider(name = "byteValues")
    public Object[][] getByteValues() {
        final Object[][] array = new Object[256][1];
        for (int i = 0; i < 256; i++) {
            array[i] = new Object[] { new byte[] { (byte) (i & 0xFF) } };
        }

        return array;
    }

    /**
     * Create byte arrays with encoded ASN.1 elements to test decoding them as
     * octet strings.
     *
     * @return A list of byte arrays with encoded ASN.1 elements that can be
     *         decoded as octet strings.
     */
    @DataProvider(name = "elementArrays")
    public Object[][] getElementArrays() {
        return new Object[][] { new Object[] { new byte[] { 0x04, 0x00 } },
            new Object[] { new byte[] { (byte) 0x50, 0x00 } },
            new Object[] { new byte[] { 0x04, 0x05, 0x48, 0x65, 0x6C, 0x6C, 0x6F } },
            new Object[] { new byte[] { 0x01, 0x01, 0x00 } },
            new Object[] { new byte[] { 0x01, 0x01, (byte) 0xFF } },
            new Object[] { new byte[] { 0x0A, 0x01, 0x00 } },
            new Object[] { new byte[] { 0x0A, 0x01, 0x01 } },
            new Object[] { new byte[] { 0x0A, 0x01, 0x7F } },
            new Object[] { new byte[] { 0x0A, 0x01, (byte) 0x80 } },
            new Object[] { new byte[] { 0x0A, 0x01, (byte) 0xFF } },
            new Object[] { new byte[] { 0x0A, 0x02, 0x01, 0x00 } },
            new Object[] { new byte[] { 0x02, 0x01, 0x00 } },
            new Object[] { new byte[] { 0x02, 0x01, 0x01 } },
            new Object[] { new byte[] { 0x02, 0x01, 0x7F } },
            new Object[] { new byte[] { 0x02, 0x02, 0x00, (byte) 0x80 } },
            new Object[] { new byte[] { 0x02, 0x02, 0x00, (byte) 0xFF } },
            new Object[] { new byte[] { 0x02, 0x02, 0x01, 0x00 } },
            new Object[] { new byte[] { 0x05, 0x00 } }, new Object[] { new byte[] { 0x30, 0x00 } },
            new Object[] { new byte[] { 0x31, 0x00 } },
            new Object[] { new byte[] { 0x05, (byte) 0x81, 0x00 } },
            new Object[] { new byte[] { 0x05, (byte) 0x82, 0x00, 0x00 } },
            new Object[] { new byte[] { 0x05, (byte) 0x83, 0x00, 0x00, 0x00 } },
            new Object[] { new byte[] { 0x05, (byte) 0x84, 0x00, 0x00, 0x00, 0x00 } }, };
    }

    /**
     * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array
     * argument with an array with a zero length that takes multiple bytes to
     * encode.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testDecodeExtendedZeroLengthArrayAsNull() throws Exception {
        final byte[] b = new byte[] { 0x05, (byte) 0x81, 0x00 };
        getReader(b, 0).readNull();
    }

    /**
     * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
     * argument with an array that has less bytes than indicated by the length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLengthMismatchArrayAsBoolean() throws Exception {
        final byte[] b = { 0x01, 0x01 };
        getReader(b, 0).readBoolean();
    }

    /**
     * Tests the <CODE>readEnumerated</CODE> method that takes a byte array with
     * a length mismatch.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLengthMismatchArrayAsEnumerated() throws Exception {
        final byte[] b = { 0x02, (byte) 0x81, 0x01 };
        getReader(b, 0).readEnumerated();
    }

    /**
     * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array
     * with a length mismatch.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLengthMismatchArrayAsInteger() throws Exception {
        final byte[] b = { 0x02, (byte) 0x81, 0x01 };
        getReader(b, 0).readInteger();
    }

    /**
     * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
     * using an array whose actual length doesn't match with the decoded length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLengthMismatchArrayAsOctetString() throws Exception {
        final byte[] b = { 0x04, 0x02, 0x00 };
        getReader(b, 0).readOctetString();
    }

    /**
     * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
     * argument with an array that takes too many bytes to expressthe length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLongLengthArrayAsBoolean() throws Exception {
        final byte[] b = { 0x01, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00 };
        getReader(b, 0).readBoolean();
    }

    /**
     * Tests the <CODE>readEnumerated</CODE> method that takes a byte array with
     * a long length array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLongLengthArrayAsEnumerated() throws Exception {
        final byte[] b = { 0x02, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00 };
        getReader(b, 0).readEnumerated();
    }

    /**
     * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array
     * with a long length array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLongLengthArrayAsInteger() throws Exception {
        final byte[] b = { 0x02, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00 };
        getReader(b, 0).readInteger();
    }

    /**
     * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array
     * argument with an array with a long length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLongLengthArrayAsNull() throws Exception {
        final byte[] b = new byte[] { 0x05, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x00 };
        getReader(b, 0).readNull();
    }

    /**
     * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
     * using an array that indicates it takes more than four bytes to encode the
     * length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLongLengthArrayAsOctetString() throws Exception {
        final byte[] b = { 0x04, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x00 };
        getReader(b, 0).readOctetString();
    }

    /**
     * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
     * argument with an array that takes too many bytes to encode the length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLongLengthArrayAsSequence() throws Exception {
        final byte[] b = { 0x30, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x00 };
        getReader(b, 0).readStartSequence();
    }

    /**
     * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
     * argument with an array that has an invalid number of bytes in the value.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeLongValueArrayAsBoolean() throws Exception {
        final byte[] b = { 0x01, 0x02, 0x00, 0x00 };
        getReader(b, 0).readBoolean();
    }

    /**
     * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array
     * argument with an array with a nonzero length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeNonZeroLengthArrayAsNull() throws Exception {
        final byte[] b = new byte[] { 0x05, 0x01, 0x00 };
        getReader(b, 0).readNull();
    }

    /**
     * Tests the <CODE>readOctetString</CODE> method when the max element size
     * is exceeded.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeOctetStringExceedMaxSize() throws Exception {
        final byte[] b = new byte[] { 0x04, 0x05, 0x48, 0x65, 0x6C, 0x6C, 0x6F };
        getReader(b, 3).readOctetString();
    }

    /**
     * Tests the <CODE>readOctetString</CODE> method when the max element size
     * is exceeded.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeSequenceExceedMaxSize() throws Exception {
        final byte[] b = new byte[] { 0x30, 0x07, 0x04, 0x05, 0x48, 0x65, 0x6C, 0x6C, 0x6F };
        getReader(b, 3).readOctetString();
    }

    /**
     * Tests to make sure trailing components are ignored if not used.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testDecodeSequenceIncompleteRead() throws Exception {
        // An ASN.1 sequence of booleans missing one boolean element at the end
        final byte[] b =
                new byte[] { 0x30, 0x06, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00 };
        final ASN1Reader reader = getReader(b, 0);
        reader.readStartSequence();
        reader.readEndSequence();
        assertFalse(reader.readBoolean());
    }

    /**
     * Tests to make sure a premature EOF while reading a sub sequence can be
     * detected.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeSequencePrematureEof() throws Exception {
        // An ASN.1 sequence of booleans missing one boolean element at the end
        final byte[] b = new byte[] { 0x30, 0x09, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00 };
        final ASN1Reader reader = getReader(b, 0);
        reader.readStartSequence();
        while (reader.hasNextElement()) {
            reader.readBoolean();
        }
        reader.readEndSequence();
    }

    /**
     * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
     * argument with a short array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeShortArrayAsBoolean() throws Exception {
        final byte[] b = new byte[1];
        getReader(b, 0).readBoolean();
    }

    /**
     * Tests the <CODE>readEnumerated</CODE> method that takes a byte array with
     * a short array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeShortArrayAsEnumerated() throws Exception {
        final byte[] b = new byte[0];
        getReader(b, 0).readEnumerated();
    }

    /**
     * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array
     * with a short array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeShortArrayAsInteger() throws Exception {
        final byte[] b = new byte[0];
        getReader(b, 0).readInteger();
    }

    /**
     * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array
     * argument with a short array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeShortArrayAsNull() throws Exception {
        final byte[] b = new byte[1];
        getReader(b, 0).readNull();
    }

    /**
     * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
     * using a short array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeShortArrayAsOctetString() throws Exception {
        final byte[] b = new byte[1];
        getReader(b, 0).readOctetString();
    }

    /**
     * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
     * argument with a short array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeShortArrayAsSequence() throws Exception {
        final byte[] b = new byte[1];
        getReader(b, 0).readStartSequence();
    }

    /**
     * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
     * argument with an array that has an invalid number of bytes in the value.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeShortValueArrayAsBoolean() throws Exception {
        final byte[] b = { 0x01, 0x00, 0x00, 0x00 };
        getReader(b, 0).readBoolean();
    }

    /**
     * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
     * argument with an array that doesn't contain a full length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeTruncatedLengthArrayAsBoolean() throws Exception {
        final byte[] b = { 0x01, (byte) 0x82, 0x00 };
        getReader(b, 0).readBoolean();
    }

    /**
     * Tests the <CODE>readEnumerated</CODE> method that takes a byte array with
     * a truncated length array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeTruncatedLengthArrayAsEnumerated() throws Exception {
        final byte[] b = { 0x02, (byte) 0x82, 0x00 };
        getReader(b, 0).readEnumerated();
    }

    /**
     * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array
     * with a truncated length array.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeTruncatedLengthArrayAsInteger() throws Exception {
        final byte[] b = { 0x02, (byte) 0x82, 0x00 };
        getReader(b, 0).readInteger();
    }

    /**
     * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array
     * argument with an array with a truncated length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeTruncatedLengthArrayAsNull() throws Exception {
        final byte[] b = new byte[] { 0x05, (byte) 0x82, 0x00 };
        getReader(b, 0).readNull();
    }

    /**
     * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
     * using an array that doesn't fully contain the length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeTruncatedLengthArrayAsOctetString() throws Exception {
        final byte[] b = { 0x04, (byte) 0x82, 0x00 };
        getReader(b, 0).readOctetString();
    }

    /**
     * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
     * argument with an array that doesn't fully describe the length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testDecodeTruncatedLengthArrayAsSequence() throws Exception {
        final byte[] b = { 0x30, (byte) 0x82, 0x00 };
        getReader(b, 0).readStartSequence();
    }

    /**
     * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
     * argument with valid arrays.
     *
     * @param b
     *            The byte array to use for the element values.
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(dataProvider = "byteValues")
    public void testDecodeValidArrayAsBoolean(final byte[] b) throws Exception {
        // First, test with the standard Boolean type.
        final byte[] elementArray = new byte[] { 0x01, 0x01, b[0] };
        assertEquals(getReader(elementArray, 0).readBoolean(), (b[0] != 0x00));

        // Next, test with a nonstandard Boolean type.
        elementArray[0] = 0x50;
        assertEquals(getReader(elementArray, 0).readBoolean(), (b[0] != 0x00));
    }

    /**
     * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
     * using a valid array.
     *
     * @param b
     *            The byte array to decode.
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(dataProvider = "elementArrays")
    public void testDecodeValidArrayAsOctetString(final byte[] b) throws Exception {
        final ByteStringBuilder bsb = new ByteStringBuilder();
        bsb.appendByte(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        bsb.appendBERLength(b.length);
        bsb.appendBytes(b);

        assertEquals(getReader(bsb.toByteArray(), 0).readOctetString(), ByteString.wrap(b));
    }

    /**
     * Tests the <CODE>decodeAsOctetStringAsString</CODE> method that takes a
     * byte array using a valid array.
     *
     * @param b
     *            The byte array to decode.
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(dataProvider = "elementArrays")
    public void testDecodeValidArrayAsOctetStringAsString(final byte[] b) throws Exception {
        final ByteStringBuilder bsb = new ByteStringBuilder();
        bsb.appendByte(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        bsb.appendBERLength(b.length);
        bsb.appendBytes(b);

        assertEquals(getReader(bsb.toByteArray(), 0).readOctetStringAsString(), new String(b,
                "UTF-8"));
    }

    /**
     * Tests the <CODE>decodeAsOctetStringAsString</CODE> method that takes a
     * byte array using a valid array.
     *
     * @param b
     *            The byte array to decode.
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(dataProvider = "elementArrays")
    public void testDecodeValidArrayAsOctetStringAsStringCharSet(final byte[] b) throws Exception {
        final ByteStringBuilder bsb = new ByteStringBuilder();
        bsb.appendByte(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        bsb.appendBERLength(b.length);
        bsb.appendBytes(b);

        assertEquals(getReader(bsb.toByteArray(), 0).readOctetStringAsString(), new String(b,
                "UTF-8"));
    }

    /**
     * Tests the <CODE>decodeAsOctetStringBuilder</CODE> method that takes a
     * byte array using a valid array.
     *
     * @param b
     *            The byte array to decode.
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(dataProvider = "elementArrays")
    public void testDecodeValidArrayAsOctetStringBuilder(final byte[] b) throws Exception {
        final ByteStringBuilder bsb = new ByteStringBuilder();
        bsb.appendByte(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        bsb.appendBERLength(b.length);
        bsb.appendBytes(b);

        final ByteStringBuilder bsb2 = new ByteStringBuilder();
        getReader(bsb.toByteArray(), 0).readOctetString(bsb2);
        assertEquals(bsb2.toByteString(), ByteString.wrap(b));
    }

    /**
     * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
     * argument with valid arrays.
     *
     * @param encodedElements
     *            Byte arrays that may be used as valid values for encoded
     *            elements.
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(dataProvider = "elementArrays")
    public void testDecodeValidArrayAsSequence(final byte[] encodedElements) throws Exception {
        final ByteStringBuilder bsb = new ByteStringBuilder();
        bsb.appendByte(ASN1.UNIVERSAL_SEQUENCE_TYPE);
        bsb.appendBERLength(encodedElements.length + 2);
        bsb.appendByte(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        bsb.appendBERLength(encodedElements.length);
        bsb.appendBytes(encodedElements);

        final ASN1Reader reader = getReader(bsb.toByteArray(), 0);
        assertEquals(reader.peekLength(), encodedElements.length + 2);
        reader.readStartSequence();
        assertEquals(reader.peekType(), ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        assertEquals(reader.peekLength(), encodedElements.length);
        reader.readOctetString().equals(ByteString.wrap(encodedElements));
        reader.readEndSequence();
    }

    /**
     * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
     * argument with valid arrays using extended lengths.
     *
     * @param b
     *            The byte array to use for the element values.
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(dataProvider = "byteValues")
    public void testDecodeValidExtendedArrayAsBoolean(final byte[] b) throws Exception {
        // First, test with the standard Boolean type.
        final byte[] elementArray = new byte[] { 0x01, (byte) 0x81, 0x01, b[0] };
        assertEquals(getReader(elementArray, 0).readBoolean(), (b[0] != 0x00));

        // Next, test with a nonstandard Boolean type.
        elementArray[0] = 0x50;
        assertEquals(getReader(elementArray, 0).readBoolean(), (b[0] != 0x00));
    }

    /**
     * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array
     * argument with an array with a zero length.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testDecodeZeroLengthArrayAsNull() throws Exception {
        final byte[] b = new byte[] { 0x05, 0x00 };
        getReader(b, 0).readNull();
    }

    /**
     * Tests the <CODE>elementAvailable</CODE> method.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testElementAvailable() throws Exception {
        // An ASN.1 sequence of booleans missing one boolean element at the end
        byte[] b = new byte[] { 0x30, 0x06, 0x02, 0x01, 0x00, 0x02 };
        ASN1Reader reader = getReader(b, 0);
        assertFalse(reader.elementAvailable());

        b = new byte[] { 0x30, 0x03, 0x02, 0x01, 0x00 };
        reader = getReader(b, 0);
        assertTrue(reader.elementAvailable());
        reader.readStartSequence();
        assertTrue(reader.elementAvailable());
        reader.readInteger();
        assertFalse(reader.elementAvailable());
    }

    /**
     * Tests the <CODE>hasNextElement</CODE> method.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testHasNextElement() throws Exception {
        // An ASN.1 sequence of booleans missing one boolean element at the end
        byte[] b = new byte[] { 0x30, 0x06, 0x02, 0x01, 0x00, 0x02, 0x00, 0x03 };
        ASN1Reader reader = getReader(b, 0);
        assertTrue(reader.hasNextElement());
        reader.readStartSequence();
        assertTrue(reader.hasNextElement());
        reader.readInteger();
        assertTrue(reader.hasNextElement());

        b = new byte[] { 0x30, 0x03, 0x02, 0x01, 0x00 };
        reader = getReader(b, 0);
        assertTrue(reader.hasNextElement());
        reader.readStartSequence();
        assertTrue(reader.hasNextElement());
        reader.readInteger();
        assertFalse(reader.hasNextElement());
    }

    /**
     * Tests the <CODE>readEndSequence</CODE> method without first calling
     * readStartSequence.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { IllegalStateException.class, IOException.class })
    public void testReadEndSequenceNoStartSequence() throws Exception {
        final byte[] b = { 0x30, 0x01, 0x00 };
        getReader(b, 0).readEndSequence();
    }

    /**
     * Tests the <CODE>skipElement</CODE> method.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testSkipElement() throws Exception {
        // An ASN.1 sequence of booleans missing one boolean element at the end
        final byte[] b =
                new byte[] { 0x30, 0x09, 0x02, 0x01, 0x00, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02 };
        final ASN1Reader reader = getReader(b, 0);
        reader.readStartSequence();
        reader.readInteger();
        reader.skipElement();
        assertEquals(reader.readInteger(), 2);
        reader.readEndSequence();
    }

    /**
     * Tests the <CODE>skipElement</CODE> method.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testSkipElementIncompleteRead() throws Exception {
        // An ASN.1 sequence of booleans missing one boolean element at the end
        final byte[] b = new byte[] { 0x30, 0x09, 0x01, 0x01, 0x00, 0x01, 0x02 };
        final ASN1Reader reader = getReader(b, 0);
        reader.readStartSequence();
        reader.readBoolean();
        reader.skipElement();
        reader.readEndSequence();
    }

    /**
     * Tests the <CODE>skipElement</CODE> method providing a specific type.
     */
    @Test
    public void testSkipElementWithType() throws Exception {
        final byte[] b =
                new byte[] { 0x30, 0x09, 0x02, 0x01, 0x00, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02 };
        final ASN1Reader reader = getReader(b, 0);
        reader.readStartSequence();
        reader.skipElement(ASN1.UNIVERSAL_INTEGER_TYPE);
        reader.skipElement(ASN1.UNIVERSAL_INTEGER_TYPE);
        assertEquals(reader.readInteger(), 2);
        reader.readEndSequence();
    }

    /**
     * Tests the <CODE>skipElement</CODE> method providing a wrong type.
     */
    @Test(expectedExceptions = { DecodeException.class, IOException.class })
    public void testSkipElementWithWrongType() throws Exception {
        final byte[] b =
                new byte[] { 0x30, 0x09, 0x02, 0x01, 0x00, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02 };
        final ASN1Reader reader = getReader(b, 0);
        reader.readStartSequence();
        reader.readInteger();
        reader.skipElement(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
        assertEquals(reader.readInteger(), 2);
        reader.readEndSequence();
    }

    /**
     * Gets the reader to be use for the unit tests.
     *
     * @param b
     *            The array of bytes to be read.
     * @param maxElementSize
     *            The max element size.
     * @return The reader to be use for the unit tests.
     * @throws IOException
     *             In an unexpected IO exception occurred.
     */
    protected abstract ASN1Reader getReader(byte[] b, int maxElementSize) throws IOException;
}
