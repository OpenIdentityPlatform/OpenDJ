/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import static org.forgerock.util.Reject.*;

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
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;

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
 * Note: The name <em>AVA</em> is historical, coming from X500/LDAPv2.
 * However, in LDAP context, this class actually represents an
 * <code>AttributeTypeAndValue</code>.
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

        final String nameOrOid = readAttributeName(reader);
        final AttributeType attribute = schema.getAttributeType(nameOrOid);

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
        return new AVA(attribute, nameOrOid, value);
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

    private static ByteString delimitAndEvaluateEscape(final SubstringReader reader) {
        final StringBuilder valueBuffer = new StringBuilder();
        StringBuilder hexBuffer = null;
        reader.skipWhitespaces();

        boolean escaped = false;
        while (reader.remaining() > 0) {
            final char c = reader.read();
            if (escaped) {
                // This character is escaped.
                if (isHexDigit(c)) {
                    // Unicode characters.
                    if (reader.remaining() <= 0) {
                        throw new LocalizedIllegalArgumentException(
                                ERR_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID.get(reader.getString()));
                    }

                    // Check the next byte for hex.
                    final char c2 = reader.read();
                    if (isHexDigit(c2)) {
                        if (hexBuffer == null) {
                            hexBuffer = new StringBuilder();
                        }
                        hexBuffer.append(c);
                        hexBuffer.append(c2);
                        // We may be at the end.
                        if (reader.remaining() == 0) {
                            appendHexChars(reader, valueBuffer, hexBuffer);
                        }
                    } else {
                        throw new LocalizedIllegalArgumentException(
                                ERR_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID.get(reader.getString()));
                    }
                } else {
                    appendHexChars(reader, valueBuffer, hexBuffer);
                    valueBuffer.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                // Check for delimited chars.
                if (c == '+' || c == ',' || c == ';') {
                    reader.reset();
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

    private static void appendHexChars(final SubstringReader reader,
                                       final StringBuilder valueBuffer,
                                       final StringBuilder hexBuffer) {
        if (hexBuffer == null) {
            return;
        }
        final ByteString bytes = ByteString.valueOfHex(hexBuffer.toString());
        try {
            valueBuffer.append(new String(bytes.toByteArray(), "UTF-8"));
        } catch (final Exception e) {
            throw new LocalizedIllegalArgumentException(
                    ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE.get(reader.getString(), String.valueOf(e)));
        }
        // Clean up the hex buffer.
        hexBuffer.setLength(0);
    }

    private static String readAttributeName(final SubstringReader reader) {
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
                        throw illegalCharacter(reader, c);
                    }
                    lastWasPeriod = true;
                } else if (!isDigit(c)) {
                    throw illegalCharacter(reader, c);
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
                    throw illegalCharacter(reader, c);
                }

                length++;
            }
        } else {
            throw illegalCharacter(reader, c);
        }

        reader.reset();

        // Return the position of the first non-space character after the token
        return reader.read(length);
    }

    private static LocalizedIllegalArgumentException illegalCharacter(
            final SubstringReader reader, final char c) {
        return new LocalizedIllegalArgumentException(
                ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(reader.getString(), c, reader.pos() - 1));
    }

    private static ByteString readAttributeValue(final SubstringReader reader) {
        // All leading spaces have already been stripped so we can start
        // reading the value. However, it may be empty so check for that.
        if (reader.remaining() == 0) {
            return ByteString.empty();
        }

        // Decide how to parse based on the first character.
        reader.mark();
        char c = reader.read();
        if (c == '+') {
            // Value is empty and followed by another AVA.
            reader.reset();
            return ByteString.empty();
        } else if (c == '#') {
            // Value is HEX encoded BER.
            return readAttributeValueAsBER(reader);
        } else if (c == '"') {
            // The value should continue until the corresponding closing quotation mark.
            return readAttributeValueWithinQuotes(reader);
        } else {
            // Otherwise, use general parsing to find the end of the value.
            return readAttributeValueUnescaped(reader);
        }
    }

    private static ByteString readAttributeValueUnescaped(final SubstringReader reader) {
        reader.reset();
        final ByteString bytes = delimitAndEvaluateEscape(reader);
        if (bytes.length() == 0) {
            // We don't allow an empty attribute value.
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_DN_INVALID_REQUIRES_ESCAPE_CHAR.get(reader.getString(), reader.pos());
            throw new LocalizedIllegalArgumentException(message);
        }
        return bytes;
    }

    private static ByteString readAttributeValueWithinQuotes(final SubstringReader reader) {
        int length = 0;
        reader.mark();
        while (true) {
            if (reader.remaining() <= 0) {
                // We hit the end of the AVA before the closing quote. That's an error.
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_DN_UNMATCHED_QUOTE.get(reader.getString()));
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
    }

    private static ByteString readAttributeValueAsBER(final SubstringReader reader) {
        // The first two characters must be hex characters.
        reader.mark();
        if (reader.remaining() < 2) {
            throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT.get(reader.getString()));
        }

        int length = 0;
        for (int i = 0; i < 2; i++) {
            final char c = reader.read();
            if (isHexDigit(c)) {
                length++;
            } else {
                throw new LocalizedIllegalArgumentException(
                        ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(reader.getString(), c));
            }
        }

        // The rest of the value must be a multiple of two hex
        // characters. The end of the value may be designated by the
        // end of the DN, a comma or semicolon, or a space.
        while (reader.remaining() > 0) {
            char c = reader.read();
            if (isHexDigit(c)) {
                length++;

                if (reader.remaining() > 0) {
                    c = reader.read();
                    if (isHexDigit(c)) {
                        length++;
                    } else {
                        throw new LocalizedIllegalArgumentException(
                                ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(reader.getString(), c));
                    }
                } else {
                    throw new LocalizedIllegalArgumentException(
                            ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT.get(reader.getString()));
                }
            } else if (c == ' ' || c == ',' || c == ';') {
                // This denotes the end of the value.
                break;
            } else {
                throw new LocalizedIllegalArgumentException(
                        ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(reader.getString(), c));
            }
        }

        // At this point, we should have a valid hex string. Convert it
        // to a byte array and set that as the value of the provided
        // octet string.
        try {
            reader.reset();
            return ByteString.valueOfHex(reader.read(length));
        } catch (final LocalizedIllegalArgumentException e) {
            throw new LocalizedIllegalArgumentException(
                    ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE.get(reader.getString(), e.getMessageObject()));
        }
    }

    private final AttributeType attributeType;
    private final String attributeName;
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
        this(attributeType, null, attributeValue);
    }

    /**
     * Creates a new attribute value assertion (AVA) using the provided
     * attribute type, name and value.
     * <p>
     * If {@code attributeValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeType
     *            The attribute type.
     * @param attributeName
     *            The user provided attribute name.
     * @param attributeValue
     *            The attribute value.
     * @throws NullPointerException
     *             If {@code attributeType}, {@code attributeName} or {@code attributeValue} was {@code null}.
     */
    public AVA(final AttributeType attributeType, final String attributeName, final Object attributeValue) {
        this.attributeType = checkNotNull(attributeType);
        this.attributeName = computeAttributeName(attributeName, attributeType);
        this.attributeValue = ByteString.valueOfObject(checkNotNull(attributeValue));
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
        this.attributeName = checkNotNull(attributeType);
        this.attributeType = Schema.getDefaultSchema().getAttributeType(attributeType);
        this.attributeValue = ByteString.valueOfObject(checkNotNull(attributeValue));
    }

    private String computeAttributeName(final String attributeName, final AttributeType attributeType) {
        return attributeName != null ? attributeName : attributeType.getNameOrOID();
    }

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
     * Returns the attribute name associated with this AVA.
     *
     * @return The attribute name associated with this AVA.
     */
    public String getAttributeName() {
        return attributeName;
    }

    /**
     * Returns the attribute value associated with this AVA.
     *
     * @return The attribute value associated with this AVA.
     */
    public ByteString getAttributeValue() {
        return attributeValue;
    }

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

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        return toString(builder).toString();
    }

    StringBuilder toString(final StringBuilder builder) {
        if (attributeName.equals(attributeType.getOID())) {
            builder.append(attributeType.getOID());
            builder.append("=#");
            builder.append(attributeValue.toHexString());
        } else {
            builder.append(attributeName);
            builder.append("=");

            if (!attributeType.getSyntax().isHumanReadable()) {
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
        for (int i = 0; i < value.length(); i++) {
            if (isByteToEscape(value.byteAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isByteToEscape(final byte b) {
        return b == DN.NORMALIZED_RDN_SEPARATOR || b == DN.NORMALIZED_AVA_SEPARATOR || b == DN.NORMALIZED_ESC_BYTE;
    }
}
