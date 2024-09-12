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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2024 3A Systems, LLC.
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;
import static com.forgerock.opendj.util.StringPrepProfile.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.SubstringReader;

/**
 * Schema utility methods.
 */
final class SchemaUtils {
    /**
     * Reads the value for an "extra" parameter. It will handle a single
     * unquoted word (which is technically illegal, but we'll allow it), a
     * single quoted string, or an open parenthesis followed by a
     * space-delimited set of quoted strings or unquoted words followed by a
     * close parenthesis.
     *
     * @param reader
     *            The string representation of the definition.
     * @return The "extra" parameter value that was read.
     * @throws DecodeException
     *             If a problem occurs while attempting to read the value.
     */
    static List<String> readExtensions(final SubstringReader reader) throws DecodeException {
        int length = 0;
        List<String> values;

        // Skip over any leading spaces.
        reader.skipWhitespaces();
        reader.mark();

        try {
            // Look at the next character. If it is a quote, then parse until
            // the next quote and end. If it is an open parenthesis, then
            // parse individual values until the close parenthesis and end.
            // Otherwise, parse until the next space and end.
            char c = reader.read();
            if (c == '\'') {
                reader.mark();
                // Parse until the closing quote.
                while (reader.read() != '\'') {
                    length++;
                }

                reader.reset();
                values = Collections.singletonList(reader.read(length));
                reader.read();
            } else if (c == '(') {
                // Skip over any leading spaces
                reader.skipWhitespaces();
                reader.mark();

                c = reader.read();
                if (c == ')') {
                    values = Collections.emptyList();
                } else {
                    values = new ArrayList<>();
                    do {
                        reader.reset();
                        values.add(readQuotedString(reader));
                        reader.skipWhitespaces();
                        reader.mark();
                    } while (reader.read() != ')');
                    values = Collections.unmodifiableList(values);
                }
            } else {
                // Parse until the next space.
                do {
                    length++;
                } while (reader.read() != ' ');

                reader.reset();
                values = Collections.singletonList(reader.read(length));
            }

            return values;
        } catch (final StringIndexOutOfBoundsException e) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_TRUNCATED_VALUE1.get());
        }
    }

    static List<String> readNameDescriptors(final SubstringReader reader,
            final boolean allowCompatChars) throws DecodeException {
        List<String> values;

        // Skip over any spaces at the beginning of the value.
        reader.skipWhitespaces();

        try {
            reader.mark();
            char c = reader.read();
            if (c == '\'') {
                reader.reset();
                values = Collections.singletonList(readQuotedDescriptor(reader, allowCompatChars));
            } else if (c == '(') {
                // Skip over any leading spaces
                reader.skipWhitespaces();
                reader.mark();

                c = reader.read();
                if (c == ')') {
                    values = Collections.emptyList();
                } else {
                    values = new LinkedList<>();
                    do {
                        reader.reset();
                        values.add(readQuotedDescriptor(reader, allowCompatChars));
                        reader.skipWhitespaces();
                        reader.mark();
                    } while (reader.read() != ')');
                    values = Collections.unmodifiableList(values);
                }
            } else {
                throw DecodeException.error(
                        ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID1.get(c, reader.pos() - 1));
            }

            return values;
        } catch (final StringIndexOutOfBoundsException e) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_TRUNCATED_VALUE1.get());
        }
    }

    /**
     * Reads the attribute description or numeric OID, skipping over any leading
     * or trailing spaces.
     *
     * @param reader
     *            The string representation of the definition.
     * @param allowCompatChars
     *            {@code true} if certain illegal characters should be allowed
     *            for compatibility reasons.
     * @return The attribute description or numeric OID read from the
     *         definition.
     * @throws DecodeException
     *             If a problem is encountered while reading the name or OID.
     */
    static String readOID(final SubstringReader reader, final boolean allowCompatChars)
            throws DecodeException {
        int length = 0;
        boolean enclosingQuote = false;

        // Skip over any spaces at the beginning of the value.
        reader.skipWhitespaces();
        reader.mark();

        if (reader.remaining() > 0) {
            // The next character must be either numeric (for an OID) or
            // alphabetic (for an attribute description).
            if (reader.read() == '\'') {
                enclosingQuote = true;
                reader.mark();
            } else {
                reader.reset();
            }
        }

        if (reader.remaining() > 0) {
            char c = reader.read();
            length++;

            if (isDigit(c)) {
                // This must be a numeric OID. In that case, we will accept
                // only digits and periods, but not consecutive periods.
                boolean lastWasPeriod = false;

                while (reader.remaining() > 0
                        && (c = reader.read()) != ' '
                        && c != ')'
                        && (c != '\'' || !enclosingQuote)) {
                    if (c == '.') {
                        if (lastWasPeriod) {
                            throw DecodeException.error(
                                    ERR_ATTR_SYNTAX_OID_CONSECUTIVE_PERIODS1.get(reader.pos() - 1));
                        }
                        lastWasPeriod = true;
                    } else if (!isDigit(c)) {
                        throw DecodeException.error(
                                ERR_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER1.get(c, reader.pos() - 1));
                    } else {
                        lastWasPeriod = false;
                    }

                    length++;
                }

                if (lastWasPeriod) {
                    throw DecodeException.error(
                            ERR_ATTR_SYNTAX_OID_ENDS_WITH_PERIOD1.get(reader.pos() - 1));
                }
            } else if (isAlpha(c)) {
                // This must be an attribute description. In this case, we will
                // only accept alphabetic characters, numeric digits, and the hyphen.
                while (reader.remaining() > 0
                        && (c = reader.read()) != ' '
                        && c != ')'
                        && (c != '\'' || !enclosingQuote)) {
                    if (length == 0 && !isAlpha(c)) {
                        throw DecodeException.error(
                                ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID1.get(c, reader.pos() - 1));
                    }

                    if (!isKeyChar(c, allowCompatChars)) {
                        throw DecodeException.error(
                                ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID1.get(c, reader.pos() - 1));
                    }

                    length++;
                }
            } else {
                throw DecodeException.error(
                        ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID1.get(c, reader.pos() - 1));
            }

            if (enclosingQuote && c != '\'') {
                throw DecodeException.error(
                        ERR_ATTR_SYNTAX_EXPECTED_QUOTE_AT_POS1.get(reader.pos() - 1, c));
            }
        }

        if (length == 0) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_OID_NO_VALUE1.get(reader.pos() - 1));
        }

        reader.reset();
        final String oid = reader.read(length);
        if (enclosingQuote) {
            reader.read();
        }

        return oid;
    }

    /**
     * Reads the next OID from the definition, skipping over any leading spaces.
     * The OID may be followed by a integer length in brackets.
     *
     * @param reader
     *            The string representation of the definition.
     * @param allowCompatChars
     *            {@code true} if certain illegal characters should be allowed
     *            for compatibility reasons.
     * @return The OID read from the definition.
     * @throws DecodeException
     *             If a problem is encountered while reading the token name.
     */
    static String readOIDLen(final SubstringReader reader, final boolean allowCompatChars)
            throws DecodeException {
        int length = 1;
        boolean enclosingQuote = false;

        // Skip over any spaces at the beginning of the value.
        reader.skipWhitespaces();
        reader.mark();

        try {
            // The next character must be either numeric (for an OID) or
            // alphabetic (for an attribute description).
            char c = reader.read();
            if (c == '\'') {
                enclosingQuote = true;
                reader.mark();
                c = reader.read();
            }
            if (isDigit(c)) {
                boolean lastWasPeriod = false;
                while ((c = reader.read()) != ' '
                        && c != '{'
                        && (c != '\'' || !enclosingQuote)) {
                    if (c == '.') {
                        if (lastWasPeriod) {
                            throw DecodeException.error(
                                    ERR_ATTR_SYNTAX_OID_CONSECUTIVE_PERIODS1.get(reader.pos() - 1));
                        }
                        lastWasPeriod = true;
                    } else if (!isDigit(c)) {
                        /*
                         * Technically, this must be an illegal character.
                         * However, it is possible that someone just got sloppy
                         * and did not include a space between the name/OID and
                         * a closing parenthesis.
                         * In that case, we'll assume it's the end of the value.
                         */
                        if (c == ')') {
                            break;
                        }

                        throw DecodeException.error(
                                ERR_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER1.get(c, reader.pos() - 1));
                    } else {
                        lastWasPeriod = false;
                    }
                    length++;
                }

                if (length == 0) {
                    throw DecodeException.error(
                            ERR_ATTR_SYNTAX_OID_NO_VALUE1.get(reader.pos() - 1));
                }
            } else if (isAlpha(c)) {
                // This must be an attribute description. In this case, we will
                // only accept alphabetic characters, numeric digits, and the hyphen.
                while ((c = reader.read()) != ' '
                        && c != ')'
                        && c != '{'
                        && (c != '\'' || !enclosingQuote)) {
                    if (length == 0 && !isAlpha(c)) {
                        throw DecodeException.error(
                                ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID1.get(c, reader.pos() - 1));
                    }

                    if (!isKeyChar(c, allowCompatChars)) {
                        throw DecodeException.error(
                                ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID1.get(c, reader.pos() - 1));
                    }

                    length++;
                }
            } else {
                throw DecodeException.error(
                        ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID1.get(c, reader.pos() - 1));
            }

            reader.reset();

            // Return the position of the first non-space character after the
            // token.
            final String oid = reader.read(length);

            reader.mark();
            c = reader.read();
            if (c == '{') {
                reader.mark();
                // The only thing we'll allow here will be numeric digits and
                // the closing curly brace.
                while ((c = reader.read()) != '}') {
                    if (!isDigit(c)) {
                        throw DecodeException.error(
                                ERR_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER1.get(reader.getString(),
                                        reader.pos() - 1));
                    }
                }
            } else if (c == '\'') {
                reader.mark();
            } else {
                reader.reset();
            }

            return oid;
        } catch (final StringIndexOutOfBoundsException e) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_TRUNCATED_VALUE1.get());
        }
    }

    static Set<String> readOIDs(final SubstringReader reader, final boolean allowCompatChars)
            throws DecodeException {
        Set<String> values;

        // Skip over any spaces at the beginning of the value.
        reader.skipWhitespaces();
        reader.mark();

        try {
            final char c = reader.read();
            if (c == '(') {
                values = new LinkedHashSet<>();
                do {
                    values.add(readOID(reader, allowCompatChars));

                    // Skip over any trailing spaces
                    reader.skipWhitespaces();
                } while (reader.read() != ')');
                values = Collections.unmodifiableSet(values);
            } else {
                reader.reset();
                values = Collections.singleton(readOID(reader, allowCompatChars));
            }

            return values;
        } catch (final StringIndexOutOfBoundsException e) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_TRUNCATED_VALUE1.get());
        }
    }

    /**
     * Reads the value of a string enclosed in single quotes, skipping over the
     * quotes and any leading spaces.
     *
     * @param reader
     *            The string representation of the definition.
     * @return The string value read from the definition.
     * @throws DecodeException
     *             If a problem is encountered while reading the quoted string.
     */
    static String readQuotedString(final SubstringReader reader) throws DecodeException {
        int length = 0;

        // Skip over any spaces at the beginning of the value.
        reader.skipWhitespaces();

        try {
            // The next character must be a single quote.
            final char c = reader.read();
            if (c != '\'') {
                throw DecodeException.error(
                        ERR_ATTR_SYNTAX_EXPECTED_QUOTE_AT_POS1.get(reader.pos() - 1, c));
            }

            // Read until we find the closing quote.
            reader.mark();
            while (reader.read() != '\'') {
                length++;
            }

            reader.reset();

            final String str = reader.read(length);
            reader.read();
            return str;
        } catch (final StringIndexOutOfBoundsException e) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_TRUNCATED_VALUE1.get());
        }
    }

    /**
     * Reads the next ruleid from the definition, skipping over any leading
     * spaces.
     *
     * @param reader
     *            The string representation of the definition.
     * @return The ruleid read from the definition.
     * @throws DecodeException
     *             If a problem is encountered while reading the token name.
     */
    static Integer readRuleID(final SubstringReader reader) throws DecodeException {
        // This must be a ruleid. In that case, we will accept
        // only digits.
        int length = 0;

        // Skip over any spaces at the beginning of the value.
        reader.skipWhitespaces();
        reader.mark();

        try {
            while (reader.read() != ' ') {
                length++;
            }

            if (length == 0) {
                throw DecodeException.error(
                        ERR_ATTR_SYNTAX_RULE_ID_NO_VALUE1.get(reader.pos() - 1));
            }

            reader.reset();
            final String ruleID = reader.read(length);

            try {
                return Integer.valueOf(ruleID);
            } catch (final NumberFormatException e) {
                throw DecodeException.error(ERR_ATTR_SYNTAX_RULE_ID_INVALID1.get(ruleID));
            }
        } catch (final StringIndexOutOfBoundsException e) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_TRUNCATED_VALUE1.get());
        }
    }

    static Set<Integer> readRuleIDs(final SubstringReader reader) throws DecodeException {
        Set<Integer> values;

        // Skip over any spaces at the beginning of the value.
        reader.skipWhitespaces();
        reader.mark();

        try {
            final char c = reader.read();
            if (c == '(') {
                values = new LinkedHashSet<>();
                do {
                    if (!values.isEmpty()) {
                        reader.reset();
                    }
                    values.add(readRuleID(reader));
                    // Skip over any delims spaces
                    reader.skipDelims();
                    reader.mark();
                } while (reader.read() != ')');
                values = Collections.unmodifiableSet(values);
            } else {
                reader.reset();
                values = Collections.singleton(readRuleID(reader));
            }

            return values;
        } catch (final StringIndexOutOfBoundsException e) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_TRUNCATED_VALUE1.get());
        }
    }

    /**
     * Reads the next token name from the definition, skipping over any leading
     * or trailing spaces or <code>null</code> if there are no more tokens to
     * read.
     *
     * @param reader
     *            The string representation of the definition.
     * @return The token name read from the definition or <code>null</code> .
     * @throws DecodeException
     *             If a problem is encountered while reading the token name.
     */
    static String readTokenName(final SubstringReader reader) throws DecodeException {
        String token = null;
        int length = 0;
        // Skip over any spaces at the beginning of the value.
        reader.skipWhitespaces();
        reader.mark();

        try {
            // Read until we find the next space.
            char c;
            while ((c = reader.read()) != ' ' && c != ')') {
                length++;
            }

            if (length > 0) {
                reader.reset();
                token = reader.read(length);
            }

            // Skip over any trailing spaces after the value.
            reader.skipWhitespaces();

            if (token == null && reader.remaining() > 0) {
                reader.reset();
                throw DecodeException.error(
                        ERR_ATTR_SYNTAX_UNEXPECTED_CLOSE_PARENTHESIS1.get(length));
            }

            return token;
        } catch (final StringIndexOutOfBoundsException e) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_TRUNCATED_VALUE1.get());
        }
    }

    /**
     * Returns an unmodifiable copy of the provided schema element extra
     * properties.
     *
     * @param extraProperties
     *            The schema element extra properties.
     * @return An unmodifiable copy of the provided schema element extra
     *         properties.
     */
    static Map<String, List<String>> unmodifiableCopyOfExtraProperties(
            final Map<String, List<String>> extraProperties) {
        if (extraProperties == null || extraProperties.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<String, List<String>> tmp = new LinkedHashMap<>(extraProperties.size());
        for (final Map.Entry<String, List<String>> e : extraProperties.entrySet()) {
            tmp.put(e.getKey(), unmodifiableCopyOfList(e.getValue()));
        }
        return Collections.unmodifiableMap(tmp);
    }

    static <E> List<E> unmodifiableCopyOfList(final List<E> l) {
        if (l == null || l.isEmpty()) {
            return Collections.emptyList();
        } else if (l.size() == 1) {
            return Collections.singletonList(l.get(0));
        } else {
            final List<E> copy = new LinkedList<>(l);
            return Collections.unmodifiableList(copy);
        }
    }

    static <E> Set<E> unmodifiableCopyOfSet(final Set<E> s) {
        if (s == null || s.isEmpty()) {
            return Collections.emptySet();
        } else if (s.size() == 1) {
            return Collections.singleton(s.iterator().next());
        } else {
            final Set<E> copy = new LinkedHashSet<>(s);
            return Collections.unmodifiableSet(copy);
        }
    }

    /**
     * Reads the value of a string enclosed in single quotes, skipping over the
     * quotes and any leading spaces.
     *
     * @param reader
     *            The string representation of the definition.
     * @param allowCompatChars
     *            {@code true} if certain illegal characters should be allowed
     *            for compatibility reasons.
     * @return The string value read from the definition.
     * @throws DecodeException
     *             If a problem is encountered while reading the quoted string.
     */
    private static String readQuotedDescriptor(final SubstringReader reader,
            final boolean allowCompatChars) throws DecodeException {
        int length = 0;

        // Skip over any spaces at the beginning of the value.
        reader.skipWhitespaces();

        try {
            // The next character must be a single quote.
            char c = reader.read();
            if (c != '\'') {
                throw DecodeException.error(
                        ERR_ATTR_SYNTAX_EXPECTED_QUOTE_AT_POS1.get(reader.pos() - 1, c));
            }

            // Read until we find the closing quote.
            reader.mark();
            while ((c = reader.read()) != '\'') {
                if (length == 0 && !isAlpha(c)) {
                    throw DecodeException.error(
                            ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID1.get(c, reader.pos() - 1));
                }

                if (!isKeyChar(c, allowCompatChars)) {
                    throw DecodeException.error(
                            ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID1.get(c, reader.pos() - 1));
                }

                length++;
            }

            reader.reset();

            final String descr = reader.read(length);
            reader.read();
            return descr;
        } catch (final StringIndexOutOfBoundsException e) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_TRUNCATED_VALUE1.get());
        }
    }

    /** Prevent instantiation. */
    private SchemaUtils() {
        // Nothing to do.
    }

    private static ByteString singleSpaceOrEmpty(final ByteSequence value) {
        if (value.length() > 0) {
            // This should only happen if the value is composed entirely of
            // spaces. In that case, the normalized value is a single space.
            return SchemaConstants.SINGLE_SPACE_VALUE;
        }
        // The value is empty, so it is already normalized.
        return ByteString.empty();
    }

    static ByteString normalizeStringListAttributeValue(final ByteSequence value, boolean trim, boolean foldCase) {
        final StringBuilder buffer = new StringBuilder();
        prepareUnicode(buffer, value, trim, foldCase);

        if (buffer.length() == 0) {
            return singleSpaceOrEmpty(value);
        }
        trimUnnecessarySpacesInStringList(buffer);
        return ByteString.valueOfUtf8(buffer);
    }

    private static void trimUnnecessarySpacesInStringList(StringBuilder buffer) {
        // Replace any consecutive spaces with a single space. Any spaces
        // around a dollar sign will also be removed.
        for (int pos = buffer.length() - 1; pos > 0; pos--) {
            if (buffer.charAt(pos) == ' ') {
                if (buffer.charAt(pos - 1) == '$') {
                    if (pos <= 1 || buffer.charAt(pos - 2) != '\\') {
                        buffer.delete(pos, pos + 1);
                    }
                } else if (buffer.charAt(pos + 1) == '$') {
                    buffer.delete(pos, pos + 1);
                }
            }
        }
    }

    static ByteString normalizeStringAttributeValue(final ByteSequence value, final boolean trim,
            final boolean foldCase) {
        final StringBuilder buffer = new StringBuilder();
        prepareUnicode(buffer, value, trim, foldCase);

        if (buffer.length() == 0) {
            return singleSpaceOrEmpty(value);
        }
        return ByteString.valueOfUtf8(buffer);
    }

    static ByteString normalizeIA5StringAttributeValue(final ByteSequence value, boolean trim, boolean foldCase)
            throws DecodeException {
        final StringBuilder buffer = new StringBuilder();
        prepareUnicode(buffer, value, trim, foldCase);

        if (buffer.length() == 0) {
            return singleSpaceOrEmpty(value);
        }
        throwIfIA5IllegalCharacter(buffer, value);
        return ByteString.valueOfUtf8(buffer);
    }

    static void throwDecodeException(LocalizedLogger logger, LocalizableMessage message) throws DecodeException {
        final DecodeException e = DecodeException.error(message);
        logger.debug(LocalizableMessage.raw("%s", e));
        throw e;
    }

    private static void throwIfIA5IllegalCharacter(StringBuilder buffer, ByteSequence value) throws DecodeException {
        // Check the string for any non-IA5 characters
        final int bufferLength = buffer.length();
        for (int pos = 0; pos < bufferLength; pos++) {
            final char c = buffer.charAt(pos);
            if ((c & 0x7F) != c) {
                // This is not a valid character for an IA5 string. If strict
                // syntax enforcement is enabled, then we'll throw an exception.
                // Otherwise, we'll get rid of the character.
                throw DecodeException.error(
                        WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER.get(value, c));
            }
        }
    }

    static ByteString normalizeNumericStringAttributeValue(final ByteSequence value) {
        final StringBuilder buffer = new StringBuilder();
        prepareUnicode(buffer, value, TRIM, NO_CASE_FOLD);

        // Remove any space
        for (int pos = buffer.length() - 1; pos > 0; pos--) {
            char c = buffer.charAt(pos);
            if (c == ' ') {
                buffer.delete(pos, pos + 1);
            }
        }

        if (buffer.length() == 0) {
            return ByteString.empty();
        }
        return ByteString.valueOfUtf8(buffer);
    }

}
