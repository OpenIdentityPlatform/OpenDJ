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
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.CoreMessages.*;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.SubstringReader;

/**
 * This class implements a default substring matching rule that matches
 * normalized substring assertion values in byte order.
 */
abstract class AbstractSubstringMatchingRuleImpl extends AbstractMatchingRuleImpl {
    static class DefaultSubstringAssertion implements Assertion {
        private final ByteString normInitial;
        private final ByteString[] normAnys;
        private final ByteString normFinal;

        protected DefaultSubstringAssertion(final ByteString normInitial,
                final ByteString[] normAnys, final ByteString normFinal) {
            this.normInitial = normInitial;
            this.normAnys = normAnys;
            this.normFinal = normFinal;
        }

        public ConditionResult matches(final ByteSequence attributeValue) {
            final int valueLength = attributeValue.length();

            int pos = 0;
            if (normInitial != null) {
                final int initialLength = normInitial.length();
                if (initialLength > valueLength) {
                    return ConditionResult.FALSE;
                }

                for (; pos < initialLength; pos++) {
                    if (normInitial.byteAt(pos) != attributeValue.byteAt(pos)) {
                        return ConditionResult.FALSE;
                    }
                }
            }

            if (normAnys != null && normAnys.length != 0) {
                for (final ByteSequence element : normAnys) {
                    final int anyLength = element.length();
                    if (anyLength == 0) {
                        continue;
                    }
                    final int end = valueLength - anyLength;
                    boolean match = false;
                    for (; pos <= end; pos++) {
                        if (element.byteAt(0) == attributeValue.byteAt(pos)) {
                            boolean subMatch = true;
                            for (int i = 1; i < anyLength; i++) {
                                if (element.byteAt(i) != attributeValue.byteAt(pos + i)) {
                                    subMatch = false;
                                    break;
                                }
                            }

                            if (subMatch) {
                                match = subMatch;
                                break;
                            }
                        }
                    }

                    if (match) {
                        pos += anyLength;
                    } else {
                        return ConditionResult.FALSE;
                    }
                }
            }

            if (normFinal != null) {
                final int finalLength = normFinal.length();

                if (valueLength - finalLength < pos) {
                    return ConditionResult.FALSE;
                }

                pos = valueLength - finalLength;
                for (int i = 0; i < finalLength; i++, pos++) {
                    if (normFinal.byteAt(i) != attributeValue.byteAt(pos)) {
                        return ConditionResult.FALSE;
                    }
                }
            }

            return ConditionResult.TRUE;
        }
    }

    AbstractSubstringMatchingRuleImpl() {
        // Nothing to do.
    }

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        if (value.length() == 0) {
            throw DecodeException.error(WARN_ATTR_SYNTAX_SUBSTRING_EMPTY.get());
        }

        ByteSequence initialString = null;
        ByteSequence finalString = null;
        List<ByteSequence> anyStrings = null;

        final String valueString = value.toString();

        if (valueString.length() == 1 && valueString.charAt(0) == '*') {
            return getAssertion(schema, initialString, anyStrings, finalString);
        }

        final char[] escapeChars = new char[] { '*' };
        final SubstringReader reader = new SubstringReader(valueString);

        ByteString bytes = evaluateEscapes(reader, escapeChars, false);
        if (bytes.length() > 0) {
            initialString = normalizeSubString(schema, bytes);
        }
        if (reader.remaining() == 0) {
            throw DecodeException.error(WARN_ATTR_SYNTAX_SUBSTRING_NO_WILDCARDS.get(value
                    .toString()));
        }
        while (true) {
            reader.read();
            bytes = evaluateEscapes(reader, escapeChars, false);
            if (reader.remaining() > 0) {
                if (bytes.length() == 0) {
                    throw DecodeException.error(WARN_ATTR_SYNTAX_SUBSTRING_CONSECUTIVE_WILDCARDS
                            .get(value.toString(), reader.pos()));
                }
                if (anyStrings == null) {
                    anyStrings = new LinkedList<ByteSequence>();
                }
                anyStrings.add(normalizeSubString(schema, bytes));
            } else {
                if (bytes.length() > 0) {
                    finalString = normalizeSubString(schema, bytes);
                }
                break;
            }
        }

        return getAssertion(schema, initialString, anyStrings, finalString);
    }

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence subInitial,
            final List<? extends ByteSequence> subAnyElements, final ByteSequence subFinal)
            throws DecodeException {
        final ByteString normInitial =
                subInitial == null ? null : normalizeSubString(schema, subInitial);

        ByteString[] normAnys = null;
        if (subAnyElements != null && !subAnyElements.isEmpty()) {
            normAnys = new ByteString[subAnyElements.size()];
            for (int i = 0; i < subAnyElements.size(); i++) {
                normAnys[i] = normalizeSubString(schema, subAnyElements.get(i));
            }
        }
        final ByteString normFinal = subFinal == null ? null : normalizeSubString(schema, subFinal);

        return new DefaultSubstringAssertion(normInitial, normAnys, normFinal);
    }

    ByteString normalizeSubString(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return normalizeAttributeValue(schema, value);
    }

    private char evaluateEscapedChar(final SubstringReader reader, final char[] escapeChars)
            throws DecodeException {
        final char c1 = reader.read();
        byte b;
        switch (c1) {
        case '0':
            b = 0x00;
            break;
        case '1':
            b = 0x10;
            break;
        case '2':
            b = 0x20;
            break;
        case '3':
            b = 0x30;
            break;
        case '4':
            b = 0x40;
            break;
        case '5':
            b = 0x50;
            break;
        case '6':
            b = 0x60;
            break;
        case '7':
            b = 0x70;
            break;
        case '8':
            b = (byte) 0x80;
            break;
        case '9':
            b = (byte) 0x90;
            break;
        case 'A':
        case 'a':
            b = (byte) 0xA0;
            break;
        case 'B':
        case 'b':
            b = (byte) 0xB0;
            break;
        case 'C':
        case 'c':
            b = (byte) 0xC0;
            break;
        case 'D':
        case 'd':
            b = (byte) 0xD0;
            break;
        case 'E':
        case 'e':
            b = (byte) 0xE0;
            break;
        case 'F':
        case 'f':
            b = (byte) 0xF0;
            break;
        default:
            if (c1 == 0x5C) {
                return c1;
            }
            if (escapeChars != null) {
                for (final char escapeChar : escapeChars) {
                    if (c1 == escapeChar) {
                        return c1;
                    }
                }
            }
            final LocalizableMessage message = ERR_INVALID_ESCAPE_CHAR.get(reader.getString(), c1);
            throw DecodeException.error(message);
        }

        // The two positions must be the hex characters that
        // comprise the escaped value.
        if (reader.remaining() == 0) {
            final LocalizableMessage message =
                    ERR_HEX_DECODE_INVALID_LENGTH.get(reader.getString());

            throw DecodeException.error(message);
        }

        final char c2 = reader.read();
        switch (c2) {
        case '0':
            // No action required.
            break;
        case '1':
            b |= 0x01;
            break;
        case '2':
            b |= 0x02;
            break;
        case '3':
            b |= 0x03;
            break;
        case '4':
            b |= 0x04;
            break;
        case '5':
            b |= 0x05;
            break;
        case '6':
            b |= 0x06;
            break;
        case '7':
            b |= 0x07;
            break;
        case '8':
            b |= 0x08;
            break;
        case '9':
            b |= 0x09;
            break;
        case 'A':
        case 'a':
            b |= 0x0A;
            break;
        case 'B':
        case 'b':
            b |= 0x0B;
            break;
        case 'C':
        case 'c':
            b |= 0x0C;
            break;
        case 'D':
        case 'd':
            b |= 0x0D;
            break;
        case 'E':
        case 'e':
            b |= 0x0E;
            break;
        case 'F':
        case 'f':
            b |= 0x0F;
            break;
        default:
            final LocalizableMessage message =
                    ERR_HEX_DECODE_INVALID_CHARACTER.get(new String(new char[] { c1, c2 }), c1);
            throw DecodeException.error(message);
        }
        return (char) b;
    }

    private ByteString evaluateEscapes(final SubstringReader reader, final char[] escapeChars,
            final boolean trim) throws DecodeException {
        return evaluateEscapes(reader, escapeChars, escapeChars, trim);
    }

    private ByteString evaluateEscapes(final SubstringReader reader, final char[] escapeChars,
            final char[] delimiterChars, final boolean trim) throws DecodeException {
        int length = 0;
        int lengthWithoutSpace = 0;
        char c;
        ByteStringBuilder valueBuffer = null;

        if (trim) {
            reader.skipWhitespaces();
        }

        reader.mark();
        while (reader.remaining() > 0) {
            c = reader.read();
            if (c == 0x5C /* The backslash character */) {
                if (valueBuffer == null) {
                    valueBuffer = new ByteStringBuilder();
                }
                valueBuffer.append(reader.read(length));
                valueBuffer.append(evaluateEscapedChar(reader, escapeChars));
                reader.mark();
                length = lengthWithoutSpace = 0;
            }
            if (delimiterChars != null) {
                for (final char delimiterChar : delimiterChars) {
                    if (c == delimiterChar) {
                        reader.reset();
                        if (valueBuffer != null) {
                            if (trim) {
                                valueBuffer.append(reader.read(lengthWithoutSpace));
                            } else {
                                valueBuffer.append(reader.read(length));
                            }
                            return valueBuffer.toByteString();
                        } else {
                            if (trim) {
                                if (lengthWithoutSpace > 0) {
                                    return ByteString.valueOf(reader.read(lengthWithoutSpace));
                                }
                                return ByteString.empty();
                            }
                            if (length > 0) {
                                return ByteString.valueOf(reader.read(length));
                            }
                            return ByteString.empty();
                        }
                    }
                }
            }
            length++;
            if (c != ' ') {
                lengthWithoutSpace = length;
            } else {
                lengthWithoutSpace++;
            }
        }

        reader.reset();
        if (valueBuffer != null) {
            if (trim) {
                valueBuffer.append(reader.read(lengthWithoutSpace));
            } else {
                valueBuffer.append(reader.read(length));
            }
            return valueBuffer.toByteString();
        } else {
            if (trim) {
                if (lengthWithoutSpace > 0) {
                    return ByteString.valueOf(reader.read(lengthWithoutSpace));
                }
                return ByteString.empty();
            }
            if (length > 0) {
                return ByteString.valueOf(reader.read(length));
            }
            return ByteString.empty();
        }
    }
}
