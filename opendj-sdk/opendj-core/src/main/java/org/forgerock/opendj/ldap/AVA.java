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
 *      Portions copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.*;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;
import org.forgerock.util.Reject;

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.SubstringReader;

/**
 * An attribute value assertion (AVA) as defined in RFC 4512 section 2.3
 * consists of an attribute description with zero options and an attribute
 * value.
 * <p>
 * The following are examples of string representations of AVAs:
 *
 * <pre>
 * uid=12345
 * ou=Engineering
 * cn=Kurt Zeilenga
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4512#section-2.3">RFC 4512 -
 *      Lightweight Directory Access Protocol (LDAP): Directory Information
 *      Models </a>
 */
public final class AVA implements Comparable<AVA> {

    /**
     * Parses the provided LDAP string representation of an AVA using the
     * default schema.
     *
     * @param ava
     *            The LDAP string representation of an AVA.
     * @return The parsed RDN.
     * @throws LocalizedIllegalArgumentException
     *             If {@code ava} is not a valid LDAP string representation of a
     *             AVA.
     * @throws NullPointerException
     *             If {@code ava} was {@code null}.
     */
    public static AVA valueOf(final String ava) {
        return valueOf(ava, Schema.getDefaultSchema());
    }

    /**
     * Parses the provided LDAP string representation of an AVA using the
     * provided schema.
     *
     * @param ava
     *            The LDAP string representation of a AVA.
     * @param schema
     *            The schema to use when parsing the AVA.
     * @return The parsed AVA.
     * @throws LocalizedIllegalArgumentException
     *             If {@code ava} is not a valid LDAP string representation of a
     *             AVA.
     * @throws NullPointerException
     *             If {@code ava} or {@code schema} was {@code null}.
     */
    public static AVA valueOf(final String ava, final Schema schema) {
        final SubstringReader reader = new SubstringReader(ava);
        try {
            return decode(reader, schema);
        } catch (final UnknownSchemaElementException e) {
            final LocalizableMessage message =
                    ERR_RDN_TYPE_NOT_FOUND.get(ava, e.getMessageObject());
            throw new LocalizedIllegalArgumentException(message);
        }
    }

    static AVA decode(final SubstringReader reader, final Schema schema) {
        // Skip over any spaces at the beginning.
        reader.skipWhitespaces();

        if (reader.remaining() == 0) {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_DN_ATTR_NO_NAME.get(reader.getString());
            throw new LocalizedIllegalArgumentException(message);
        }

        final AttributeType attribute = readAttributeName(reader, schema);

        // Skip over any spaces if we have.
        reader.skipWhitespaces();

        // Make sure that we're not at the end of the DN string because
        // that would be invalid.
        if (reader.remaining() == 0) {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME.get(reader.getString(), attribute
                            .getNameOrOID());
            throw new LocalizedIllegalArgumentException(message);
        }

        // The next character must be an equal sign. If it is not, then
        // that's an error.
        final char c = reader.read();
        if (c != '=') {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_DN_NO_EQUAL
                            .get(reader.getString(), attribute.getNameOrOID(), c);
            throw new LocalizedIllegalArgumentException(message);
        }

        // Skip over any spaces after the equal sign.
        reader.skipWhitespaces();

        // Parse the value for this RDN component.
        final ByteString value = readAttributeValue(reader);

        return new AVA(attribute, value);
    }

    static void escapeAttributeValue(final String str, final StringBuilder builder) {
        if (str.length() > 0) {
            char c = str.charAt(0);
            int startPos = 0;
            if (c == ' ' || c == '#') {
                builder.append('\\');
                builder.append(c);
                startPos = 1;
            }
            final int length = str.length();
            for (int si = startPos; si < length; si++) {
                c = str.charAt(si);
                if (c < ' ') {
                    for (final byte b : getBytes(String.valueOf(c))) {
                        builder.append('\\');
                        builder.append(StaticUtils.byteToLowerHex(b));
                    }
                } else {
                    if ((c == ' ' && si == length - 1)
                            || (c == '"' || c == '+' || c == ',' || c == ';' || c == '<'
                            || c == '=' || c == '>' || c == '\\' || c == '\u0000')) {
                        builder.append('\\');
                    }
                    builder.append(c);
                }
            }
        }
    }

    private static void appendHexChars(final SubstringReader reader,
            final StringBuilder valueBuffer, final StringBuilder hexBuffer) throws DecodeException {
        final int length = hexBuffer.length();
        if (length == 0) {
            return;
        }

        if (length % 2 != 0) {
            final LocalizableMessage message = ERR_HEX_DECODE_INVALID_LENGTH.get(hexBuffer);
            throw DecodeException.error(message);
        }

        int pos = 0;
        final int arrayLength = length / 2;
        final byte[] hexArray = new byte[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            switch (hexBuffer.charAt(pos++)) {
            case '0':
                hexArray[i] = 0x00;
                break;
            case '1':
                hexArray[i] = 0x10;
                break;
            case '2':
                hexArray[i] = 0x20;
                break;
            case '3':
                hexArray[i] = 0x30;
                break;
            case '4':
                hexArray[i] = 0x40;
                break;
            case '5':
                hexArray[i] = 0x50;
                break;
            case '6':
                hexArray[i] = 0x60;
                break;
            case '7':
                hexArray[i] = 0x70;
                break;
            case '8':
                hexArray[i] = (byte) 0x80;
                break;
            case '9':
                hexArray[i] = (byte) 0x90;
                break;
            case 'A':
            case 'a':
                hexArray[i] = (byte) 0xA0;
                break;
            case 'B':
            case 'b':
                hexArray[i] = (byte) 0xB0;
                break;
            case 'C':
            case 'c':
                hexArray[i] = (byte) 0xC0;
                break;
            case 'D':
            case 'd':
                hexArray[i] = (byte) 0xD0;
                break;
            case 'E':
            case 'e':
                hexArray[i] = (byte) 0xE0;
                break;
            case 'F':
            case 'f':
                hexArray[i] = (byte) 0xF0;
                break;
            default:
                final LocalizableMessage message =
                        ERR_HEX_DECODE_INVALID_CHARACTER.get(hexBuffer, hexBuffer.charAt(pos - 1));
                throw DecodeException.error(message);
            }

            switch (hexBuffer.charAt(pos++)) {
            case '0':
                // No action required.
                break;
            case '1':
                hexArray[i] |= 0x01;
                break;
            case '2':
                hexArray[i] |= 0x02;
                break;
            case '3':
                hexArray[i] |= 0x03;
                break;
            case '4':
                hexArray[i] |= 0x04;
                break;
            case '5':
                hexArray[i] |= 0x05;
                break;
            case '6':
                hexArray[i] |= 0x06;
                break;
            case '7':
                hexArray[i] |= 0x07;
                break;
            case '8':
                hexArray[i] |= 0x08;
                break;
            case '9':
                hexArray[i] |= 0x09;
                break;
            case 'A':
            case 'a':
                hexArray[i] |= 0x0A;
                break;
            case 'B':
            case 'b':
                hexArray[i] |= 0x0B;
                break;
            case 'C':
            case 'c':
                hexArray[i] |= 0x0C;
                break;
            case 'D':
            case 'd':
                hexArray[i] |= 0x0D;
                break;
            case 'E':
            case 'e':
                hexArray[i] |= 0x0E;
                break;
            case 'F':
            case 'f':
                hexArray[i] |= 0x0F;
                break;
            default:
                final LocalizableMessage message =
                        ERR_HEX_DECODE_INVALID_CHARACTER.get(hexBuffer, hexBuffer.charAt(pos - 1));
                throw DecodeException.error(message);
            }
        }
        try {
            valueBuffer.append(new String(hexArray, "UTF-8"));
        } catch (final Exception e) {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE.get(reader.getString(), String
                            .valueOf(e));
            throw DecodeException.error(message);
        }
        // Clean up the hex buffer.
        hexBuffer.setLength(0);
    }

    private static ByteString delimitAndEvaluateEscape(final SubstringReader reader)
            throws DecodeException {
        char c = '\u0000';
        final StringBuilder valueBuffer = new StringBuilder();
        final StringBuilder hexBuffer = new StringBuilder();
        reader.skipWhitespaces();

        boolean escaped = false;
        while (reader.remaining() > 0) {
            c = reader.read();
            if (escaped) {
                // This character is escaped.
                if (isHexDigit(c)) {
                    // Unicode characters.
                    if (reader.remaining() <= 0) {
                        final LocalizableMessage msg =
                                ERR_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID
                                        .get(reader.getString());
                        throw DecodeException.error(msg);
                    }

                    // Check the next byte for hex.
                    final char c2 = reader.read();
                    if (isHexDigit(c2)) {
                        hexBuffer.append(c);
                        hexBuffer.append(c2);
                        // We may be at the end.
                        if (reader.remaining() == 0) {
                            appendHexChars(reader, valueBuffer, hexBuffer);
                        }
                    } else {
                        final LocalizableMessage message =
                                ERR_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID
                                        .get(reader.getString());
                        throw DecodeException.error(message);
                    }
                } else {
                    appendHexChars(reader, valueBuffer, hexBuffer);
                    valueBuffer.append(c);
                }
                escaped = false;
            } else if (c == 0x5C /* The backslash character */) {
                // We found an escape.
                escaped = true;
            } else {
                // Check for delimited chars.
                if (c == '+' || c == ',' || c == ';') {
                    reader.reset();
                    // Return what we have got here so far.
                    appendHexChars(reader, valueBuffer, hexBuffer);
                    return ByteString.valueOfUtf8(valueBuffer);
                }
                // It is definitely not a delimiter at this point.
                appendHexChars(reader, valueBuffer, hexBuffer);
                valueBuffer.append(c);
            }
            reader.mark();
        }

        reader.reset();
        return ByteString.valueOfUtf8(valueBuffer);
    }

    private static AttributeType readAttributeName(final SubstringReader reader, final Schema schema) {
        int length = 1;
        reader.mark();

        // The next character must be either numeric (for an OID) or
        // alphabetic (for an attribute description).
        char c = reader.read();
        if (isDigit(c)) {
            boolean lastWasPeriod = false;
            while (reader.remaining() > 0) {
                c = reader.read();

                if (c == '=' || c == ' ') {
                    // This signals the end of the OID.
                    break;
                } else if (c == '.') {
                    if (lastWasPeriod) {
                        final LocalizableMessage message =
                                ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(reader.getString(), c,
                                        reader.pos() - 1);
                        throw new LocalizedIllegalArgumentException(message);
                    } else {
                        lastWasPeriod = true;
                    }
                } else if (!isDigit(c)) {
                    // This must have been an illegal character.
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(reader.getString(), c, reader
                                    .pos() - 1);
                    throw new LocalizedIllegalArgumentException(message);
                } else {
                    lastWasPeriod = false;
                }
                length++;
            }
        } else if (isAlpha(c)) {
            // This must be an attribute description. In this case, we will
            // only accept alphabetic characters, numeric digits, and the
            // hyphen.
            while (reader.remaining() > 0) {
                c = reader.read();

                if (c == '=' || c == ' ') {
                    // This signals the end of the OID.
                    break;
                } else if (!isAlpha(c) && !isDigit(c) && c != '-') {
                    // This is an illegal character.
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(reader.getString(), c, reader
                                    .pos() - 1);
                    throw new LocalizedIllegalArgumentException(message);
                }

                length++;
            }
        } else {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(reader.getString(), c,
                            reader.pos() - 1);
            throw new LocalizedIllegalArgumentException(message);
        }

        reader.reset();

        // Return the position of the first non-space character after the
        // token.

        return schema.getAttributeType(reader.read(length));
    }

    private static ByteString readAttributeValue(final SubstringReader reader) {
        // All leading spaces have already been stripped so we can start
        // reading the value. However, it may be empty so check for that.
        if (reader.remaining() == 0) {
            return ByteString.empty();
        }

        reader.mark();

        // Look at the first character. If it is an octothorpe (#), then
        // that means that the value should be a hex string.
        char c = reader.read();
        int length = 0;
        if (c == '+') {
            // Value is empty and followed by another AVA
            reader.reset();
            return ByteString.empty();
        } else if (c == '#') {
            // The first two characters must be hex characters.
            reader.mark();
            if (reader.remaining() < 2) {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT.get(reader.getString());
                throw new LocalizedIllegalArgumentException(message);
            }

            for (int i = 0; i < 2; i++) {
                c = reader.read();
                if (isHexDigit(c)) {
                    length++;
                } else {
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(reader.getString(), c);
                    throw new LocalizedIllegalArgumentException(message);
                }
            }

            // The rest of the value must be a multiple of two hex
            // characters. The end of the value may be designated by the
            // end of the DN, a comma or semicolon, or a space.
            while (reader.remaining() > 0) {
                c = reader.read();
                if (isHexDigit(c)) {
                    length++;

                    if (reader.remaining() > 0) {
                        c = reader.read();
                        if (isHexDigit(c)) {
                            length++;
                        } else {
                            final LocalizableMessage message =
                                    ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(reader.getString(), c);
                            throw new LocalizedIllegalArgumentException(message);
                        }
                    } else {
                        final LocalizableMessage message =
                                ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT.get(reader.getString());
                        throw new LocalizedIllegalArgumentException(message);
                    }
                } else if (c == ' ' || c == ',' || c == ';') {
                    // This denotes the end of the value.
                    break;
                } else {
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(reader.getString(), c);
                    throw new LocalizedIllegalArgumentException(message);
                }
            }

            // At this point, we should have a valid hex string. Convert it
            // to a byte array and set that as the value of the provided
            // octet string.
            try {
                reader.reset();
                return ByteString.valueOfHex(reader.read(length));
            } catch (final LocalizedIllegalArgumentException e) {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE.get(reader.getString(), e
                                .getMessageObject());
                throw new LocalizedIllegalArgumentException(message);
            }
        } else if (c == '"') {
            // If the first character is a quotation mark, then the value
            // should continue until the corresponding closing quotation mark.
            reader.mark();
            while (true) {
                if (reader.remaining() <= 0) {
                    // We hit the end of the AVA before the closing quote.
                    // That's an error.
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_DN_UNMATCHED_QUOTE.get(reader.getString());
                    throw new LocalizedIllegalArgumentException(message);
                }

                if (reader.read() == '"') {
                    // This is the end of the value.
                    break;
                }
                length++;
            }
            reader.reset();
            final ByteString retString = ByteString.valueOfUtf8(reader.read(length));
            reader.read();
            return retString;
        } else {
            // Otherwise, use general parsing to find the end of the value.
            reader.reset();
            ByteString bytes;
            try {
                bytes = delimitAndEvaluateEscape(reader);
            } catch (final DecodeException e) {
                throw new LocalizedIllegalArgumentException(e.getMessageObject());
            }
            if (bytes.length() == 0) {
                // We don't allow an empty attribute value.
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_DN_INVALID_REQUIRES_ESCAPE_CHAR.get(reader.getString(),
                                reader.pos());
                throw new LocalizedIllegalArgumentException(message);
            }
            return bytes;
        }
    }

    private final AttributeType attributeType;

    private final ByteString attributeValue;

    /** Cached normalized value using equality matching rule. */
    private ByteString equalityNormalizedAttributeValue;

    /** Cached normalized value using ordering matching rule. */
    private ByteString orderingNormalizedAttributeValue;

    /**
     * Creates a new attribute value assertion (AVA) using the provided
     * attribute type and value.
     * <p>
     * If {@code attributeValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeType
     *            The attribute type.
     * @param attributeValue
     *            The attribute value.
     * @throws NullPointerException
     *             If {@code attributeType} or {@code attributeValue} was
     *             {@code null}.
     */
    public AVA(final AttributeType attributeType, final Object attributeValue) {
        Reject.ifNull(attributeType, attributeValue);

        this.attributeType = attributeType;
        this.attributeValue = ByteString.valueOfObject(attributeValue);
    }

    /**
     * Creates a new attribute value assertion (AVA) using the provided
     * attribute type and value decoded using the default schema.
     * <p>
     * If {@code attributeValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeType
     *            The attribute type.
     * @param attributeValue
     *            The attribute value.
     * @throws UnknownSchemaElementException
     *             If {@code attributeType} was not found in the default schema.
     * @throws NullPointerException
     *             If {@code attributeType} or {@code attributeValue} was
     *             {@code null}.
     */
    public AVA(final String attributeType, final Object attributeValue) {
        Reject.ifNull(attributeType, attributeValue);

        this.attributeType = Schema.getDefaultSchema().getAttributeType(attributeType);
        this.attributeValue = ByteString.valueOfObject(attributeValue);
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(final AVA ava) {
        final int result = attributeType.compareTo(ava.attributeType);
        if (result != 0) {
            return result > 0 ? 1 : -1;
        }

        final ByteString normalizedValue = getOrderingNormalizedValue();
        final ByteString otherNormalizedValue = ava.getOrderingNormalizedValue();
        return normalizedValue.compareTo(otherNormalizedValue);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof AVA) {
            final AVA ava = (AVA) obj;

            if (!attributeType.equals(ava.attributeType)) {
                return false;
            }

            final ByteString normalizedValue = getEqualityNormalizedValue();
            final ByteString otherNormalizedValue = ava.getEqualityNormalizedValue();
            return normalizedValue.equals(otherNormalizedValue);
        } else {
            return false;
        }
    }

    /**
     * Returns the attribute type associated with this AVA.
     *
     * @return The attribute type associated with this AVA.
     */
    public AttributeType getAttributeType() {
        return attributeType;
    }

    /**
     * Returns the attribute value associated with this AVA.
     *
     * @return The attribute value associated with this AVA.
     */
    public ByteString getAttributeValue() {
        return attributeValue;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return attributeType.hashCode() * 31 + getEqualityNormalizedValue().hashCode();
    }

    /**
     * Returns a single valued attribute having the same attribute type and
     * value as this AVA.
     *
     * @return A single valued attribute having the same attribute type and
     *         value as this AVA.
     */
    public Attribute toAttribute() {
        AttributeDescription ad = AttributeDescription.create(attributeType);
        return new LinkedAttribute(ad, attributeValue);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        return toString(builder).toString();
    }

    StringBuilder toString(final StringBuilder builder) {
        if (!attributeType.getNames().iterator().hasNext()) {
            builder.append(attributeType.getOID());
            builder.append("=#");
            builder.append(attributeValue.toHexString());
        } else {
            final String name = attributeType.getNameOrOID();
            builder.append(name);
            builder.append("=");

            final Syntax syntax = attributeType.getSyntax();
            if (!syntax.isHumanReadable()) {
                builder.append("#");
                builder.append(attributeValue.toHexString());
            } else {
                escapeAttributeValue(attributeValue.toString(), builder);
            }
        }
        return builder;
    }

    private ByteString getEqualityNormalizedValue() {
        final ByteString normalizedValue = equalityNormalizedAttributeValue;

        if (normalizedValue != null) {
            return normalizedValue;
        }

        final MatchingRule matchingRule = attributeType.getEqualityMatchingRule();
        if (matchingRule != null) {
            try {
                equalityNormalizedAttributeValue =
                        matchingRule.normalizeAttributeValue(attributeValue);
            } catch (final DecodeException de) {
                // Unable to normalize, so default to byte-wise comparison.
                equalityNormalizedAttributeValue = attributeValue;
            }
        } else {
            // No matching rule, so default to byte-wise comparison.
            equalityNormalizedAttributeValue = attributeValue;
        }

        return equalityNormalizedAttributeValue;
    }

    private ByteString getOrderingNormalizedValue() {
        final ByteString normalizedValue = orderingNormalizedAttributeValue;

        if (normalizedValue != null) {
            return normalizedValue;
        }

        final MatchingRule matchingRule = attributeType.getEqualityMatchingRule();
        if (matchingRule != null) {
            try {
                orderingNormalizedAttributeValue =
                        matchingRule.normalizeAttributeValue(attributeValue);
            } catch (final DecodeException de) {
                // Unable to normalize, so default to equality matching.
                orderingNormalizedAttributeValue = getEqualityNormalizedValue();
            }
        } else {
            // No matching rule, so default to equality matching.
            orderingNormalizedAttributeValue = getEqualityNormalizedValue();
        }

        return orderingNormalizedAttributeValue;
    }

    /**
     * Returns the normalized byte string representation of this AVA.
     * <p>
     * The representation is not a valid AVA.
     *
     * @param builder
     *            The builder to use to construct the normalized byte string.
     * @return The normalized byte string representation.
     * @see DN#toNormalizedByteString()
     */
    ByteStringBuilder toNormalizedByteString(final ByteStringBuilder builder) {
        builder.appendUtf8(toLowerCase(attributeType.getNameOrOID()));
        builder.appendUtf8("=");
        final ByteString value = getEqualityNormalizedValue();
        if (value.length() > 0) {
            builder.appendBytes(escapeBytes(value));
        }
        return builder;
    }

    /**
     * Returns the normalized readable string representation of this AVA.
     * <p>
     * The representation is not a valid AVA.
     *
     * @param builder
     *            The builder to use to construct the normalized string.
     * @return The normalized readable string representation.
     * @see DN#toNormalizedUrlSafeString()
     */
    StringBuilder toNormalizedUrlSafe(final StringBuilder builder) {
        builder.append(toLowerCase(attributeType.getNameOrOID()));
        builder.append('=');
        final ByteString value = getEqualityNormalizedValue();

        if (value.length() == 0) {
            return builder;
        }
        final boolean hasAttributeName = !attributeType.getNames().isEmpty();
        final boolean isHumanReadable = attributeType.getSyntax().isHumanReadable();
        if (!hasAttributeName || !isHumanReadable) {
            builder.append(value.toPercentHexString());
        } else {
            // try to decode value as UTF-8 string
            final CharBuffer buffer = CharBuffer.allocate(value.length());
            final CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            if (value.copyTo(buffer, decoder)) {
                buffer.flip();
                try {
                    // URL encoding encodes space char as '+' instead of using hex code
                    final String val = URLEncoder.encode(buffer.toString(), "UTF-8").replaceAll("\\+", "%20");
                    builder.append(val);
                } catch (UnsupportedEncodingException e) {
                    // should never happen
                    builder.append(value.toPercentHexString());
                }
            } else {
                builder.append(value.toPercentHexString());
            }
        }
        return builder;
    }

    /**
     * Return a new byte string with bytes 0x00, 0x01 and 0x02 escaped.
     * <p>
     * These bytes are reserved to represent respectively the RDN separator,
     * the AVA separator and the escape byte in a normalized byte string.
     */
    private ByteString escapeBytes(final ByteString value) {
        if (!needEscaping(value)) {
            return value;
        }

        final ByteStringBuilder builder = new ByteStringBuilder();
        for (int i = 0; i < value.length(); i++) {
            final byte b = value.byteAt(i);
            if (isByteToEscape(b)) {
                builder.appendByte(DN.NORMALIZED_ESC_BYTE);
            }
            builder.appendByte(b);
        }
        return builder.toByteString();
    }

    private boolean needEscaping(final ByteString value) {
        boolean needEscaping = false;
        for (int i = 0; i < value.length(); i++) {
            final byte b = value.byteAt(i);
            if (isByteToEscape(b)) {
                needEscaping = true;
                break;
            }
        }
        return needEscaping;
    }

    private boolean isByteToEscape(final byte b) {
        return b == DN.NORMALIZED_RDN_SEPARATOR || b == DN.NORMALIZED_AVA_SEPARATOR || b == DN.NORMALIZED_ESC_BYTE;
    }
}
