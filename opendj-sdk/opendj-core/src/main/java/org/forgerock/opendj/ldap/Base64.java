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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import org.forgerock.util.Reject;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_BASE64_DECODE_INVALID_CHARACTER;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_BASE64_DECODE_INVALID_LENGTH;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;

/**
 * This class provides methods for performing base64 encoding and decoding.
 * Base64 is a mechanism for encoding binary data in ASCII form by converting
 * sets of three bytes with eight significant bits each to sets of four bytes
 * with six significant bits each.
 * <p>
 * <b>NOTE:</b> the JDK class {@link javax.xml.bind.DatatypeConverter} provides
 * similar functionality, however the methods are better suited to the LDAP SDK.
 * For example, the JDK encoder does not handle array/offset/len parameters, and
 * the decoder ignores invalid Base64 data.
 */
final class Base64 {
    /**
     * The set of characters that may be used in base64-encoded values.
     */
    private static final char[] BASE64_ALPHABET = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            + "abcdefghijklmnopqrstuvwxyz" + "0123456789+/").toCharArray();

    /**
     * Decodes the provided base64 encoded data.
     *
     * @param base64
     *            The base64 encoded data.
     * @return The decoded data.
     * @throws LocalizedIllegalArgumentException
     *             If a problem occurs while attempting to decode {@code base64}
     *             .
     * @throws NullPointerException
     *             If {@code base64} was {@code null}.
     */
    static ByteString decode(final String base64) {
        Reject.ifNull(base64);

        // The encoded value must have length that is a multiple of four
        // bytes.
        final int length = base64.length();
        if (length % 4 != 0) {
            final LocalizableMessage message = ERR_BASE64_DECODE_INVALID_LENGTH.get(base64);
            throw new LocalizedIllegalArgumentException(message);
        }

        final ByteStringBuilder builder = new ByteStringBuilder(length);
        for (int i = 0; i < length; i += 4) {
            boolean append = true;
            int value = 0;

            for (int j = 0; j < 4; j++) {
                switch (base64.charAt(i + j)) {
                case 'A':
                    value <<= 6;
                    break;
                case 'B':
                    value = (value << 6) | 0x01;
                    break;
                case 'C':
                    value = (value << 6) | 0x02;
                    break;
                case 'D':
                    value = (value << 6) | 0x03;
                    break;
                case 'E':
                    value = (value << 6) | 0x04;
                    break;
                case 'F':
                    value = (value << 6) | 0x05;
                    break;
                case 'G':
                    value = (value << 6) | 0x06;
                    break;
                case 'H':
                    value = (value << 6) | 0x07;
                    break;
                case 'I':
                    value = (value << 6) | 0x08;
                    break;
                case 'J':
                    value = (value << 6) | 0x09;
                    break;
                case 'K':
                    value = (value << 6) | 0x0A;
                    break;
                case 'L':
                    value = (value << 6) | 0x0B;
                    break;
                case 'M':
                    value = (value << 6) | 0x0C;
                    break;
                case 'N':
                    value = (value << 6) | 0x0D;
                    break;
                case 'O':
                    value = (value << 6) | 0x0E;
                    break;
                case 'P':
                    value = (value << 6) | 0x0F;
                    break;
                case 'Q':
                    value = (value << 6) | 0x10;
                    break;
                case 'R':
                    value = (value << 6) | 0x11;
                    break;
                case 'S':
                    value = (value << 6) | 0x12;
                    break;
                case 'T':
                    value = (value << 6) | 0x13;
                    break;
                case 'U':
                    value = (value << 6) | 0x14;
                    break;
                case 'V':
                    value = (value << 6) | 0x15;
                    break;
                case 'W':
                    value = (value << 6) | 0x16;
                    break;
                case 'X':
                    value = (value << 6) | 0x17;
                    break;
                case 'Y':
                    value = (value << 6) | 0x18;
                    break;
                case 'Z':
                    value = (value << 6) | 0x19;
                    break;
                case 'a':
                    value = (value << 6) | 0x1A;
                    break;
                case 'b':
                    value = (value << 6) | 0x1B;
                    break;
                case 'c':
                    value = (value << 6) | 0x1C;
                    break;
                case 'd':
                    value = (value << 6) | 0x1D;
                    break;
                case 'e':
                    value = (value << 6) | 0x1E;
                    break;
                case 'f':
                    value = (value << 6) | 0x1F;
                    break;
                case 'g':
                    value = (value << 6) | 0x20;
                    break;
                case 'h':
                    value = (value << 6) | 0x21;
                    break;
                case 'i':
                    value = (value << 6) | 0x22;
                    break;
                case 'j':
                    value = (value << 6) | 0x23;
                    break;
                case 'k':
                    value = (value << 6) | 0x24;
                    break;
                case 'l':
                    value = (value << 6) | 0x25;
                    break;
                case 'm':
                    value = (value << 6) | 0x26;
                    break;
                case 'n':
                    value = (value << 6) | 0x27;
                    break;
                case 'o':
                    value = (value << 6) | 0x28;
                    break;
                case 'p':
                    value = (value << 6) | 0x29;
                    break;
                case 'q':
                    value = (value << 6) | 0x2A;
                    break;
                case 'r':
                    value = (value << 6) | 0x2B;
                    break;
                case 's':
                    value = (value << 6) | 0x2C;
                    break;
                case 't':
                    value = (value << 6) | 0x2D;
                    break;
                case 'u':
                    value = (value << 6) | 0x2E;
                    break;
                case 'v':
                    value = (value << 6) | 0x2F;
                    break;
                case 'w':
                    value = (value << 6) | 0x30;
                    break;
                case 'x':
                    value = (value << 6) | 0x31;
                    break;
                case 'y':
                    value = (value << 6) | 0x32;
                    break;
                case 'z':
                    value = (value << 6) | 0x33;
                    break;
                case '0':
                    value = (value << 6) | 0x34;
                    break;
                case '1':
                    value = (value << 6) | 0x35;
                    break;
                case '2':
                    value = (value << 6) | 0x36;
                    break;
                case '3':
                    value = (value << 6) | 0x37;
                    break;
                case '4':
                    value = (value << 6) | 0x38;
                    break;
                case '5':
                    value = (value << 6) | 0x39;
                    break;
                case '6':
                    value = (value << 6) | 0x3A;
                    break;
                case '7':
                    value = (value << 6) | 0x3B;
                    break;
                case '8':
                    value = (value << 6) | 0x3C;
                    break;
                case '9':
                    value = (value << 6) | 0x3D;
                    break;
                case '+':
                    value = (value << 6) | 0x3E;
                    break;
                case '/':
                    value = (value << 6) | 0x3F;
                    break;
                case '=':
                    append = false;
                    switch (j) {
                    case 2:
                        builder.appendByte(value >>> 4);
                        break;
                    case 3:
                        builder.appendByte(value >>> 10);
                        builder.appendByte(value >>> 2);
                        break;
                    }
                    break;
                default:
                    final LocalizableMessage message =
                            ERR_BASE64_DECODE_INVALID_CHARACTER.get(base64, base64.charAt(i + j));
                    throw new LocalizedIllegalArgumentException(message);
                }

                if (!append) {
                    break;
                }
            }

            if (append) {
                builder.appendByte(value >>> 16);
                builder.appendByte(value >>> 8);
                builder.appendByte(value);
            } else {
                break;
            }
        }

        return builder.toByteString();
    }

    /**
     * Encodes the provided data as a base64 string.
     *
     * @param bytes
     *            The data to be encoded.
     * @return The base64 encoded representation of {@code bytes}.
     * @throws NullPointerException
     *             If {@code bytes} was {@code null}.
     */
    static String encode(final ByteSequence bytes) {
        Reject.ifNull(bytes);

        if (bytes.isEmpty()) {
            return "";
        }

        final StringBuilder buffer = new StringBuilder(4 * bytes.length() / 3);

        int pos = 0;
        final int iterations = bytes.length() / 3;
        for (int i = 0; i < iterations; i++) {
            final int value =
                    ((bytes.byteAt(pos++) & 0xFF) << 16) | ((bytes.byteAt(pos++) & 0xFF) << 8)
                            | (bytes.byteAt(pos++) & 0xFF);

            buffer.append(BASE64_ALPHABET[(value >>> 18) & 0x3F]);
            buffer.append(BASE64_ALPHABET[(value >>> 12) & 0x3F]);
            buffer.append(BASE64_ALPHABET[(value >>> 6) & 0x3F]);
            buffer.append(BASE64_ALPHABET[value & 0x3F]);
        }

        switch (bytes.length() % 3) {
        case 1:
            buffer.append(BASE64_ALPHABET[(bytes.byteAt(pos) >>> 2) & 0x3F]);
            buffer.append(BASE64_ALPHABET[(bytes.byteAt(pos) << 4) & 0x3F]);
            buffer.append("==");
            break;
        case 2:
            final int value = ((bytes.byteAt(pos++) & 0xFF) << 8) | (bytes.byteAt(pos) & 0xFF);
            buffer.append(BASE64_ALPHABET[(value >>> 10) & 0x3F]);
            buffer.append(BASE64_ALPHABET[(value >>> 4) & 0x3F]);
            buffer.append(BASE64_ALPHABET[(value << 2) & 0x3F]);
            buffer.append("=");
            break;
        }

        return buffer.toString();
    }

    /**
     * Prevent instance creation.
     */
    private Base64() {
        // No implementation required.
    }
}
