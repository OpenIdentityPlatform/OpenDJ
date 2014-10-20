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
 *      Copyright 2013-2014 Manuel Gaupp
 */
package org.forgerock.opendj.ldap;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.util.Reject;
import org.forgerock.i18n.LocalizableMessage;

import static com.forgerock.opendj.ldap.CoreMessages.WARN_GSER_NO_VALID_IDENTIFIEDCHOICE;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_GSER_NO_VALID_IDENTIFIER;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_GSER_NO_VALID_INTEGER;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_GSER_NO_VALID_SEPARATOR;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_GSER_NO_VALID_STRING;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_GSER_PATTERN_NO_MATCH;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_GSER_SPACE_CHAR_EXPECTED;

/**
 * This class implements a parser for strings which are encoded using the
 * Generic String Encoding Rules (GSER) defined in RFC 3641.
 *
 * @see <a href="http://tools.ietf.org/html/rfc3641">RFC 3641 - Generic String
 * Encoding Rules (GSER) for ASN.1 Types</a>
 */
public final class GSERParser {

    private final String gserValue;

    private int pos;

    private final int length;

    /**
     * Pattern to match an identifier defined in RFC 3641, section 3.4.
     * <pre>
     * An &lt;identifier&gt; conforms to the definition of an identifier in ASN.1
     * notation (Clause 11.3 of X.680 [8]).  It begins with a lowercase
     * letter and is followed by zero or more letters, digits, and hyphens.
     * A hyphen is not permitted to be the last character, nor is it to be
     * followed by another hyphen.  The case of letters in an identifier is
     * always significant.
     *
     *    identifier    = lowercase *alphanumeric *(hyphen 1*alphanumeric)
     *    alphanumeric  = uppercase / lowercase / decimal-digit
     *    uppercase     = %x41-5A  ; "A" to "Z"
     *    lowercase     = %x61-7A  ; "a" to "z"
     *    decimal-digit = %x30-39  ; "0" to "9"
     *    hyphen        = "-"
     * </pre>
     */
    private static final Pattern GSER_IDENTIFIER = Pattern.compile("^([a-z]([A-Za-z0-9]|(-[A-Za-z0-9]))*)");

    /**
     * Pattern to match the identifier part (including the colon) of an
     * IdentifiedChoiceValue defined in RFC 3641, section 3.12.
     * <pre>
     *    IdentifiedChoiceValue = identifier ":" Value
     * </pre>
     */
    private static final Pattern GSER_CHOICE_IDENTIFIER = Pattern.compile("^([a-z]([A-Za-z0-9]|(-[A-Za-z0-9]))*:)");

    /**
     * Pattern to match "sp", containing zero, one or more space characters.
     * <pre>
     *    sp = *%x20  ; zero, one or more space characters
     * </pre>
     */
    private static final Pattern GSER_SP = Pattern.compile("^( *)");

    /**
     * Pattern to match "msp", containing at least one space character.
     * <pre>
     *    msp = 1*%x20  ; one or more space characters
     * </pre>
     */
    private static final Pattern GSER_MSP = Pattern.compile("^( +)");

    /**
     * Pattern to match an Integer value.
     */
    private static final Pattern GSER_INTEGER = Pattern.compile("^(\\d+)");

    /**
     * Pattern to match a GSER StringValue, defined in RFC 3641, section 3.2:
     * <pre>
     * Any embedded double quotes in the resulting UTF-8 character string
     * are escaped by repeating the double quote characters.
     *
     * [...]
     *
     *    StringValue       = dquote *SafeUTF8Character dquote
     *    dquote            = %x22 ; &quot; (double quote)
     * </pre>
     */
    private static final Pattern GSER_STRING = Pattern.compile("^(\"([^\"]|(\"\"))*\")");

    /**
     * Pattern to match the beginning of a GSER encoded Sequence.
     * <pre>
     *    SequenceValue = ComponentList
     *    ComponentList = "{" [ sp NamedValue *( "," sp NamedValue) ] sp "}"
     * </pre>
     */
    private static final Pattern GSER_SEQUENCE_START = Pattern.compile("^(\\{)");

    /**
     * Pattern to match the end of a GSER encoded Sequence.
     * <pre>
     *    SequenceValue = ComponentList
     *    ComponentList = "{" [ sp NamedValue *( "," sp NamedValue) ] sp "}"
     * </pre>
     */
    private static final Pattern GSER_SEQUENCE_END = Pattern.compile("^(\\})");

    /**
     * Pattern to match the separator used in GSER encoded sequences.
     */
    private static final Pattern GSER_SEP = Pattern.compile("^(,)");

    /**
     * Creates a new GSER Parser.
     *
     * @param value the GSER encoded String value
     */
    public GSERParser(CharSequence value) {
        Reject.checkNotNull(value);
        this.gserValue = value.toString();
        this.pos = 0;
        this.length = value.length();
    }

    /**
     * Determines if the GSER String contains at least one character to be read.
     *
     * @return <code>true</code> if there is at least one remaining character or
     * <code>false</code> otherwise.
     */
    public boolean hasNext() {
        return pos < length;
    }

    /**
     * Determines if the remaining GSER String matches the provided pattern.
     *
     * @param pattern the pattern to search for
     *
     * @return <code>true</code> if the remaining string matches the pattern or
     * <code>false</code> otherwise.
     */
    private boolean hasNext(Pattern pattern) {
        if (!hasNext()) {
            return false;
        }

        Matcher matcher = pattern.matcher(gserValue.substring(pos, length));

        return matcher.find();
    }

    /**
     * Returns the String matched by the first capturing group of the pattern.
     * The parser advances past the input matched by the first capturing group.
     *
     * @param pattern the pattern to search for
     *
     * @return the String matched by the first capturing group of the pattern
     *
     * @throws DecodeException If no match could be found
     */
    private String next(Pattern pattern) throws DecodeException {
        Matcher matcher = pattern.matcher(gserValue.substring(pos, length));
        if (matcher.find() && matcher.groupCount() >= 1) {
            pos += matcher.end(1);
            return matcher.group(1);
        } else {
            final LocalizableMessage msg =
                    WARN_GSER_PATTERN_NO_MATCH.get(pattern.pattern(),
                                                   gserValue.substring(pos, length));
            throw DecodeException.error(msg);
        }
    }

    /**
     * Skips the input matched by the first capturing group.
     *
     * @param pattern the pattern to search for
     *
     * @throws DecodeException If no match could be found
     */
    private void skip(Pattern pattern) throws DecodeException {
        Matcher matcher = pattern.matcher(gserValue.substring(pos, length));

        if (matcher.find() && matcher.groupCount() >= 1) {
            pos += matcher.end(1);
        } else {
            final LocalizableMessage msg =
                    WARN_GSER_PATTERN_NO_MATCH.get(pattern.pattern(),
                                                   gserValue.substring(pos, length));
            throw DecodeException.error(msg);
        }
    }

    /**
     * Skips the input matching zero, one or more space characters.
     *
     * @return reference to this GSERParser
     *
     * @throws DecodeException If no match could be found
     */
    public GSERParser skipSP() throws DecodeException {
        skip(GSER_SP);
        return this;
    }

    /**
     * Skips the input matching one or more space characters.
     *
     * @return reference to this GSERParser
     *
     * @throws DecodeException If no match could be found
     */
    public GSERParser skipMSP() throws DecodeException {
        skip(GSER_MSP);
        return this;
    }

    /**
     * Skips the input matching the start of a sequence and subsequent space
     * characters.
     *
     * @return reference to this GSERParser
     *
     * @throws DecodeException If the input does not match the start of a
     * sequence
     */
    public GSERParser readStartSequence() throws DecodeException {
        next(GSER_SEQUENCE_START);
        skip(GSER_SP);
        return this;
    }

    /**
     * Skips the input matching the end of a sequence and preceding space
     * characters.
     *
     * @return reference to this GSERParser
     *
     * @throws DecodeException If the input does not match the end of a sequence
     */
    public GSERParser readEndSequence() throws DecodeException {
        skip(GSER_SP);
        next(GSER_SEQUENCE_END);
        return this;
    }

    /**
     * Skips the input matching the separator pattern (",") and subsequenct
     * space characters.
     *
     * @return reference to this GSERParser
     *
     * @throws DecodeException If the input does not match the separator
     * pattern.
     */
    public GSERParser skipSeparator() throws DecodeException {
        if (!hasNext(GSER_SEP)) {
            final LocalizableMessage msg =
                    WARN_GSER_NO_VALID_SEPARATOR.get(gserValue.substring(pos, length));
            throw DecodeException.error(msg);
        }
        skip(GSER_SEP);
        skip(GSER_SP);
        return this;
    }

    /**
     * Returns the next element as a String.
     *
     * @return the input matching the String pattern
     *
     * @throws DecodeException If the input does not match the string pattern.
     */
    public String nextString() throws DecodeException {
        if (!hasNext(GSER_STRING)) {
            final LocalizableMessage msg =
                    WARN_GSER_NO_VALID_STRING.get(gserValue.substring(pos, length));
            throw DecodeException.error(msg);
        }

        String str = next(GSER_STRING);

        // Strip leading and trailing dquotes; unescape double dquotes
        return str.substring(1, str.length() - 1).replace("\"\"", "\"");
    }

    /**
     * Returns the next element as an Integer.
     *
     * @return the input matching the integer pattern
     *
     * @throws DecodeException If the input does not match the integer pattern
     */
    public int nextInteger() throws DecodeException {
        if (!hasNext(GSER_INTEGER)) {
            final LocalizableMessage msg =
                    WARN_GSER_NO_VALID_INTEGER.get(gserValue.substring(pos, length));
            throw DecodeException.error(msg);
        }
        return Integer.valueOf(next(GSER_INTEGER)).intValue();
    }

    /**
     * Returns the next element as a BigInteger.
     *
     * @return the input matching the integer pattern
     *
     * @throws DecodeException If the input does not match the integer pattern
     */
    public BigInteger nextBigInteger() throws DecodeException {
        if (!hasNext(GSER_INTEGER)) {
            final LocalizableMessage msg =
                    WARN_GSER_NO_VALID_INTEGER.get(gserValue.substring(pos, length));
            throw DecodeException.error(msg);
        }
        return new BigInteger(next(GSER_INTEGER));
    }

    /**
     * Returns the identifier of the next NamedValue element.
     *
     * @return the identifier of the NamedValue element
     *
     * @throws DecodeException If the input does not match the identifier
     * pattern of a NamedValue
     */
    public String nextNamedValueIdentifier() throws DecodeException {
        if (!hasNext(GSER_IDENTIFIER)) {
            final LocalizableMessage msg =
                    WARN_GSER_NO_VALID_IDENTIFIER.get(gserValue.substring(pos, length));
            throw DecodeException.error(msg);
        }
        String identifier = next(GSER_IDENTIFIER);
        if (!hasNext(GSER_MSP)) {
            final LocalizableMessage msg =
                    WARN_GSER_SPACE_CHAR_EXPECTED.get(gserValue.substring(pos, length));
            throw DecodeException.error(msg);
        }
        skipMSP();
        return identifier;
    }

    /**
     * Return the identifier of the next IdentifiedChoiceValue element.
     *
     * @return the identifier of the IdentifiedChoiceValue element
     *
     * @throws DecodeException If the input does not match the identifier
     * pattern of an IdentifiedChoiceValue
     */
    public String nextChoiceValueIdentifier() throws DecodeException {
        if (!hasNext(GSER_CHOICE_IDENTIFIER)) {
            final LocalizableMessage msg =
                    WARN_GSER_NO_VALID_IDENTIFIEDCHOICE.get(gserValue.substring(pos, length));
            throw DecodeException.error(msg);
        }
        String identifier = next(GSER_CHOICE_IDENTIFIER);

        // Remove the colon at the end of the identifier
        return identifier.substring(0, identifier.length() - 1);
    }

    /**
     * Returns the GSER encoded String value.
     *
     * @return The GSER encoded String value.
     */
    @Override
    public String toString() {
        return gserValue;
    }
}
