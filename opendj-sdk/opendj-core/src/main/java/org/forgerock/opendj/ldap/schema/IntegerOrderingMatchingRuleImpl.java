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
 *      Portions copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_ILLEGAL_INTEGER;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import java.math.BigInteger;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class defines the integerOrderingMatch matching rule defined in X.520
 * and referenced in RFC 4519. The implementation of this matching rule is
 * intentionally aligned with the equality matching rule so that they could
 * potentially share the same index.
 */
final class IntegerOrderingMatchingRuleImpl extends AbstractOrderingMatchingRuleImpl {
    /** Sign mask to be used when encoding zero or positive integers. */
    static final byte SIGN_MASK_POSITIVE = (byte) 0x00;

    /** Sign mask to be used when encoding negative integers. */
    static final byte SIGN_MASK_NEGATIVE = (byte) 0xff;

    /**
     * Encodes an integer using a format which is suitable for comparisons using
     * {@link ByteSequence#compareTo(ByteSequence)}. The integer is encoded as
     * follows:
     * <ul>
     * <li>bit 0: sign bit, 0 = negative, 1 = positive
     * <li>bits 1-3: length of the encoded length in bytes (0 when length is <
     *     2^4, 4 when length is < 2^32)
     * <li>bits 4-7: encoded length when length is < 2^4
     * <li>bits 4-15: encoded length when length is < 2^12
     * <li>bits 4-23: encoded length when length is < 2^20
     * <li>bits 4-31: encoded length when length is < 2^28
     * <li>bits 4-35: encoded length when length is < 2^31 (bits 35-39 are
     *     always zero, because an int is 32 bits)
     * <li>remaining: byte encoding of the absolute value of the integer.
     * </ul>
     * When the value is negative all bits from bit 1 onwards are inverted.
     */
    static ByteString normalizeValueAndEncode(final ByteSequence value) throws DecodeException {
        final BigInteger bi;
        try {
            bi = new BigInteger(value.toString());
        } catch (final Exception e) {
            throw DecodeException.error(WARN_ATTR_SYNTAX_ILLEGAL_INTEGER.get(value));
        }

        /*
         * BigInteger.toByteArray() always includes a sign bit, which means that
         * we gain an extra zero byte for numbers that require a multiple of 8
         * bits, e.g. 128-255, 32768-65535, because the sign bit overflows into
         * an additional byte. We'll strip it out in that case because we encode
         * the sign bit in the header.
         */
        final byte[] absBytes = bi.abs().toByteArray();
        final int length = absBytes.length;
        final boolean removeLeadingByte = length > 1 && absBytes[0] == 0;
        final int trimmedLength = removeLeadingByte ? length - 1 : length;
        final int startIndex = removeLeadingByte ? 1 : 0;
        final byte signMask = bi.signum() < 0 ? SIGN_MASK_NEGATIVE : SIGN_MASK_POSITIVE;

        // Encode the sign, length of the length, and the length.
        final ByteStringBuilder builder = new ByteStringBuilder(trimmedLength + 5);
        encodeHeader(builder, trimmedLength, signMask);

        // Encode the absolute value of the integer..
        for (int i = startIndex; i < length; i++) {
            builder.appendByte(absBytes[i] ^ signMask);
        }

        return builder.toByteString();
    }

    // Package private for unit testing.
    static void encodeHeader(final ByteStringBuilder builder, final int length,
            final byte signMask) {
        if ((length & 0x0000000F) == length) {
            // 0000xxxx
            final byte b0 = (byte) (0x80 | length & 0x0F);
            builder.appendByte(b0 ^ signMask);
        } else if ((length & 0x00000FFF) == length) {
            // 0001xxxx xxxxxxxx
            final byte b0 = (byte) (0x90 | length >> 8 & 0x0F);
            builder.appendByte(b0 ^ signMask);
            builder.appendByte(length & 0xFF ^ signMask);
        } else if ((length & 0x000FFFFF) == length) {
            // 0010xxxx xxxxxxxx xxxxxxxx
            final byte b0 = (byte) (0xA0 | length >> 16 & 0x0F);
            builder.appendByte(b0 ^ signMask);
            builder.appendByte(length >> 8 & 0xFF ^ signMask);
            builder.appendByte(length & 0xFF ^ signMask);
        } else if ((length & 0x0FFFFFFF) == length) {
            // 0011xxxx xxxxxxxx xxxxxxxx xxxxxxxx
            final byte b0 = (byte) (0xB0 | length >> 24 & 0x0F);
            builder.appendByte(b0 ^ signMask);
            builder.appendByte(length >> 16 & 0xFF ^ signMask);
            builder.appendByte(length >> 8 & 0xFF ^ signMask);
            builder.appendByte(length & 0xFF ^ signMask);
        } else {
            // 0100xxxx xxxxxxxx xxxxxxxx xxxxxxxx xxxx0000
            final byte b0 = (byte) (0xC0 | length >> 28 & 0x0F);
            builder.appendByte(b0 ^ signMask);
            builder.appendByte(length >> 20 & 0xFF ^ signMask);
            builder.appendByte(length >> 12 & 0xFF ^ signMask);
            builder.appendByte(length >> 4 & 0xFF ^ signMask);
            builder.appendByte(length << 4 & 0xFF ^ signMask);
        }
    }

    public IntegerOrderingMatchingRuleImpl() {
        // Reusing equality index since OPENDJ-1864
        super(EMR_INTEGER_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return normalizeValueAndEncode(value);
    }
}
