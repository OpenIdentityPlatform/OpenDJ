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
 * Portions Copyright 2016 ForgeRock AS.
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

    @Override
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

    @Override
    public boolean isHumanReadable() {
        return false;
    }

    @Override
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
