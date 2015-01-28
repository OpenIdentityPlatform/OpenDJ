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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.io;

import java.io.InputStream;
import java.io.OutputStream;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;

/**
 * This class contains various static factory methods for creating ASN.1 readers
 * and writers.
 *
 * @see ASN1Reader
 * @see ASN1Writer
 */
public final class ASN1 {

    /**
     * Maximum buffer size when reading ASN1. Buffers above this threshold will
     * be discarded for garbage collection to avoid OutOfMemoryErrors.
     */
    private static final int DEFAULT_MAX_BUFFER_SIZE = 32 * 1024;

    /**
     * The byte array containing the pre-encoded ASN.1 encoding for a boolean
     * value of "false".
     */
    public static final byte BOOLEAN_VALUE_FALSE = 0x00;

    /**
     * The byte array containing the pre-encoded ASN.1 encoding for a boolean
     * value of "false".
     */
    public static final byte BOOLEAN_VALUE_TRUE = (byte) 0xFF;

    /**
     * The BER type that is assigned to the universal Boolean element.
     */
    public static final byte UNIVERSAL_BOOLEAN_TYPE = 0x01;

    /**
     * The BER type that is assigned to the universal integer type.
     */
    public static final byte UNIVERSAL_INTEGER_TYPE = 0x02;
    /**
     * The BER type that is assigned to the universal bit string type.
     */
    public static final byte UNIVERSAL_BIT_STRING_TYPE = 0x03;

    /**
     * The BER type that is assigned to the universal octet string type.
     */
    public static final byte UNIVERSAL_OCTET_STRING_TYPE = 0x04;

    /**
     * The BER type that is assigned to the universal null type.
     */
    public static final byte UNIVERSAL_NULL_TYPE = 0x05;

    /**
     * The BER type that is assigned to the universal enumerated type.
     */
    public static final byte UNIVERSAL_ENUMERATED_TYPE = 0x0A;

    /**
     * The BER type that is assigned to the universal sequence type.
     */
    public static final byte UNIVERSAL_SEQUENCE_TYPE = 0x30;

    /**
     * The BER type that is assigned to the universal set type.
     */
    public static final byte UNIVERSAL_SET_TYPE = 0x31;

    /**
     * The ASN.1 element decoding state that indicates that the next byte read
     * should be additional bytes of a multi-byte length.
     */
    public static final int ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES = 2;

    /**
     * The ASN.1 element decoding state that indicates that the next byte read
     * should be the first byte for the element length.
     */
    public static final int ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE = 1;

    /**
     * The ASN.1 element decoding state that indicates that the next byte read
     * should be the BER type for a new element.
     */
    public static final int ELEMENT_READ_STATE_NEED_TYPE = 0;

    /**
     * The ASN.1 element decoding state that indicates that the next byte read
     * should be applied to the value of the element.
     */

    public static final int ELEMENT_READ_STATE_NEED_VALUE_BYTES = 3;
    /**
     * The byte array that will be used for ASN.1 elements with no value.
     */
    static final byte[] NO_VALUE = new byte[0];

    /**
     * The bitmask that can be ANDed with the BER type to zero out all bits
     * except those used in the class.
     */
    static final byte TYPE_MASK_ALL_BUT_CLASS = (byte) 0xC0;
    /**
     * The bitmask that can be ANDed with the BER type to zero out all bits
     * except the primitive/constructed bit.
     */
    static final byte TYPE_MASK_ALL_BUT_PC = 0x20;
    /**
     * The bitmask that can be ANDed with the BER type to determine if the
     * element is in the application-specific class.
     */
    static final byte TYPE_MASK_APPLICATION = 0x40;
    /**
     * The bitmask that can be ANDed with the BER type to determine if the
     * element is constructed.
     */
    public static final byte TYPE_MASK_CONSTRUCTED = 0x20;
    /**
     * The bitmask that can be ANDed with the BER type to determine if the
     * element is in the context-specific class.
     */
    public static final byte TYPE_MASK_CONTEXT = (byte) 0x80;
    /**
     * The bitmask that can be ANDed with the BER type to determine if the
     * element is a primitive.
     */
    static final byte TYPE_MASK_PRIMITIVE = 0x00;
    /**
     * The bitmask that can be ANDed with the BER type to determine if the
     * element is in the private class.
     */
    static final byte TYPE_MASK_PRIVATE = (byte) 0xC0;
    /**
     * The bitmask that can be ANDed with the BER type to determine if the
     * element is in the universal class.
     */
    static final byte TYPE_MASK_UNIVERSAL = 0x00;

    /**
     * Returns an ASN.1 reader whose source is the provided byte array and
     * having an unlimited maximum BER element size.
     *
     * @param array
     *            The byte array to use.
     * @return The new ASN.1 reader.
     */
    public static ASN1Reader getReader(final byte[] array) {
        return getReader(array, 0);
    }

    /**
     * Returns an ASN.1 reader whose source is the provided byte array and
     * having a user defined maximum BER element size.
     *
     * @param array
     *            The byte array to use.
     * @param maxElementSize
     *            The maximum BER element size, or {@code 0} to indicate that
     *            there is no limit.
     * @return The new ASN.1 reader.
     */
    public static ASN1Reader getReader(final byte[] array, final int maxElementSize) {
        return getReader(ByteString.wrap(array), maxElementSize);
    }

    /**
     * Returns an ASN.1 reader whose source is the provided byte sequence and
     * having an unlimited maximum BER element size.
     *
     * @param sequence
     *            The byte sequence to use.
     * @return The new ASN.1 reader.
     */
    public static ASN1Reader getReader(final ByteSequence sequence) {
        return getReader(sequence, 0);
    }

    /**
     * Returns an ASN.1 reader whose source is the provided byte sequence and
     * having a user defined maximum BER element size.
     *
     * @param sequence
     *            The byte sequence to use.
     * @param maxElementSize
     *            The maximum BER element size, or {@code 0} to indicate that
     *            there is no limit.
     * @return The new ASN.1 reader.
     */
    public static ASN1Reader getReader(final ByteSequence sequence, final int maxElementSize) {
        return new ASN1ByteSequenceReader(sequence.asReader(), maxElementSize);
    }

    /**
     * Returns an ASN.1 reader whose source is the provided byte sequence reader
     * and having an unlimited maximum BER element size.
     *
     * @param reader
     *            The byte sequence reader to use.
     * @return The new ASN.1 reader.
     */
    public static ASN1Reader getReader(final ByteSequenceReader reader) {
        return getReader(reader, 0);
    }

    /**
     * Returns an ASN.1 reader whose source is the provided byte sequence reader
     * and having a user defined maximum BER element size.
     *
     * @param reader
     *            The byte sequence reader to use.
     * @param maxElementSize
     *            The maximum BER element size, or {@code 0} to indicate that
     *            there is no limit.
     * @return The new ASN.1 reader.
     */
    public static ASN1Reader getReader(final ByteSequenceReader reader, final int maxElementSize) {
        return new ASN1ByteSequenceReader(reader, maxElementSize);
    }

    /**
     * Returns an ASN.1 reader whose source is the provided input stream and
     * having an unlimited maximum BER element size.
     *
     * @param stream
     *            The input stream to use.
     * @return The new ASN.1 reader.
     */
    public static ASN1Reader getReader(final InputStream stream) {
        return getReader(stream, 0);
    }

    /**
     * Returns an ASN.1 reader whose source is the provided input stream and
     * having a user defined maximum BER element size.
     *
     * @param stream
     *            The input stream to use.
     * @param maxElementSize
     *            The maximum BER element size, or {@code 0} to indicate that
     *            there is no limit.
     * @return The new ASN.1 reader.
     */
    public static ASN1Reader getReader(final InputStream stream, final int maxElementSize) {
        return new ASN1InputStreamReader(stream, maxElementSize);
    }

    /**
     * Returns an ASN.1 writer whose destination is the provided byte string
     * builder.
     *
     * @param builder
     *            The byte string builder to use.
     * @return The new ASN.1 writer.
     */
    public static ASN1Writer getWriter(final ByteStringBuilder builder) {
        return getWriter(builder.asOutputStream(), DEFAULT_MAX_BUFFER_SIZE);
    }

    /**
     * Returns an ASN.1 writer whose destination is the provided byte string
     * builder.
     *
     * @param builder
     *            The output stream to use.
     * @param maxBufferSize
     *          The threshold capacity beyond which internal cached buffers used
     *          for encoding and decoding ASN1 will be trimmed after use.
     * @return The new ASN.1 writer.
     */
    public static ASN1Writer getWriter(final ByteStringBuilder builder, final int maxBufferSize) {
        return getWriter(builder.asOutputStream(), maxBufferSize);
    }

    /**
     * Returns an ASN.1 writer whose destination is the provided output stream.
     *
     * @param stream
     *            The output stream to use.
     * @return The new ASN.1 writer.
     */
    public static ASN1Writer getWriter(final OutputStream stream) {
        return getWriter(stream, DEFAULT_MAX_BUFFER_SIZE);
    }

    /**
     * Returns an ASN.1 writer whose destination is the provided output stream.
     *
     * @param stream
     *            The output stream to use.
     * @param maxBufferSize
     *          The threshold capacity beyond which internal cached buffers used
     *          for encoding and decoding ASN1 will be trimmed after use.
     * @return The new ASN.1 writer.
     */
    public static ASN1Writer getWriter(final OutputStream stream, final int maxBufferSize) {
        return new ASN1OutputStreamWriter(stream, maxBufferSize);
    }

    /** Prevent instantiation. */
    private ASN1() {
        // Nothing to do.
    }
}
