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

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_TELEX_ILLEGAL_CHAR;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_TELEX_NOT_PRINTABLE;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_TELEX_TOO_SHORT;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_TELEX_TRUNCATED;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_TELEX_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class implements the telex number attribute syntax, which contains three
 * printable strings separated by dollar sign characters. Equality, ordering,
 * and substring matching will be allowed by default.
 */
final class TelexNumberSyntaxImpl extends AbstractSyntaxImpl {

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_OID;
    }

    public String getName() {
        return SYNTAX_TELEX_NAME;
    }

    @Override
    public String getOrderingMatchingRule() {
        return OMR_CASE_IGNORE_OID;
    }

    @Override
    public String getSubstringMatchingRule() {
        return SMR_CASE_IGNORE_OID;
    }

    public boolean isHumanReadable() {
        return false;
    }

    /**
     * Indicates whether the provided value is acceptable for use in an
     * attribute with this syntax. If it is not, then the reason may be appended
     * to the provided buffer.
     *
     * @param schema
     *            The schema in which this syntax is defined.
     * @param value
     *            The value for which to make the determination.
     * @param invalidReason
     *            The buffer to which the invalid reason should be appended.
     * @return <CODE>true</CODE> if the provided value is acceptable for use
     *         with this syntax, or <CODE>false</CODE> if not.
     */
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        // Get a string representation of the value and find its length.
        final String valueString = value.toString();
        final int valueLength = valueString.length();

        if (valueLength < 5) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELEX_TOO_SHORT.get(valueString));
            return false;
        }

        // The first character must be a printable string character.
        char c = valueString.charAt(0);
        if (!PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELEX_NOT_PRINTABLE.get(valueString, String
                    .valueOf(c), 0));
            return false;
        }

        // Continue reading until we find a dollar sign. Every intermediate
        // character must be a printable string character.
        int pos = 1;
        for (; pos < valueLength; pos++) {
            c = valueString.charAt(pos);
            if (c == '$') {
                pos++;
                break;
            } else if (!PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
                invalidReason.append(ERR_ATTR_SYNTAX_TELEX_ILLEGAL_CHAR.get(valueString, c, pos));
            }
        }

        if (pos >= valueLength) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELEX_TRUNCATED.get(valueString));
            return false;
        }

        // The next character must be a printable string character.
        c = valueString.charAt(pos++);
        if (!PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELEX_NOT_PRINTABLE.get(valueString, c, pos - 1));
            return false;
        }

        // Continue reading until we find another dollar sign. Every
        // intermediate character must be a printable string character.
        for (; pos < valueLength; pos++) {
            c = valueString.charAt(pos);
            if (c == '$') {
                pos++;
                break;
            } else if (!PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
                invalidReason.append(ERR_ATTR_SYNTAX_TELEX_ILLEGAL_CHAR.get(valueString, c, pos));
                return false;
            }
        }

        if (pos >= valueLength) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELEX_TRUNCATED.get(valueString));
            return false;
        }

        // The next character must be a printable string character.
        c = valueString.charAt(pos++);
        if (!PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELEX_NOT_PRINTABLE.get(valueString, String
                    .valueOf(c), pos - 1));
            return false;
        }

        // Continue reading until the end of the value. Every intermediate
        // character must be a printable string character.
        for (; pos < valueLength; pos++) {
            c = valueString.charAt(pos);
            if (!PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
                invalidReason.append(ERR_ATTR_SYNTAX_TELEX_ILLEGAL_CHAR.get(valueString, String
                        .valueOf(c), pos));
                return false;
            }
        }

        // If we've gotten here, then we're at the end of the value and it
        // is acceptable.
        return true;
    }
}
