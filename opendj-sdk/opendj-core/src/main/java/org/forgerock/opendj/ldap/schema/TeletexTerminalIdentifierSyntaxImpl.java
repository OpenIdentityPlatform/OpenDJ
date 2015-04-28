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
 *      Portions Copyright 2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_TELETEX_TERM_ID_NAME;

import java.util.HashSet;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class implements the teletex terminal identifier attribute syntax, which
 * contains a printable string (the terminal identifier) followed by zero or
 * more parameters, which start with a dollar sign and are followed by a
 * parameter name, a colon, and a value. The parameter value should consist of
 * any string of bytes (the dollar sign and backslash must be escaped with a
 * preceding backslash), and the parameter name must be one of the following
 * strings:
 * <UL>
 * <LI>graphic</LI>
 * <LI>control</LI>
 * <LI>misc</LI>
 * <LI>page</LI>
 * <LI>private</LI>
 * </UL>
 */
final class TeletexTerminalIdentifierSyntaxImpl extends AbstractSyntaxImpl {
    /**
     * The set of allowed fax parameter values, formatted entirely in lowercase
     * characters.
     */
    private static final HashSet<String> ALLOWED_TTX_PARAMETERS = new HashSet<>(5);

    static {
        ALLOWED_TTX_PARAMETERS.add("graphic");
        ALLOWED_TTX_PARAMETERS.add("control");
        ALLOWED_TTX_PARAMETERS.add("misc");
        ALLOWED_TTX_PARAMETERS.add("page");
        ALLOWED_TTX_PARAMETERS.add("private");
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_OID;
    }

    public String getName() {
        return SYNTAX_TELETEX_TERM_ID_NAME;
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
        // Get a lowercase string representation of the value and find its
        // length.
        final String valueString = value.toString();
        final int valueLength = valueString.length();

        // The value must contain at least one character.
        if (valueLength == 0) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_EMPTY.get());
            return false;
        }

        // The first character must be a printable string character.
        char c = valueString.charAt(0);
        if (!PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_NOT_PRINTABLE.get(valueString, String
                    .valueOf(c), 0));
            return false;
        }

        // Continue reading until we find a dollar sign or the end of the
        // string. Every intermediate character must be a printable string
        // character.
        int pos = 1;
        for (; pos < valueLength; pos++) {
            c = valueString.charAt(pos);
            if (c == '$') {
                pos++;
                break;
            } else if (!PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
                invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_NOT_PRINTABLE.get(valueString, c, pos));
            }
        }

        if (pos >= valueLength) {
            // We're at the end of the value, so it must be valid unless the
            // last character was a dollar sign.
            if (c == '$') {
                invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_END_WITH_DOLLAR.get(valueString));
                return false;
            } else {
                return true;
            }
        }

        // Continue reading until we find the end of the string. Each
        // substring must be a valid teletex terminal identifier parameter
        // followed by a colon and the value. Dollar signs must be escaped
        int paramStartPos = pos;
        boolean escaped = false;
        while (pos < valueLength) {
            if (escaped) {
                pos++;
                continue;
            }

            c = valueString.charAt(pos++);
            if (c == '\\') {
                escaped = true;
                continue;
            } else if (c == '$') {
                final String paramStr = valueString.substring(paramStartPos, pos);

                final int colonPos = paramStr.indexOf(':');
                if (colonPos < 0) {
                    invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_PARAM_NO_COLON.get(valueString));
                    return false;
                }

                final String paramName = paramStr.substring(0, colonPos);
                if (!ALLOWED_TTX_PARAMETERS.contains(paramName)) {
                    invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_ILLEGAL_PARAMETER.get(
                            valueString, paramName));
                    return false;
                }

                paramStartPos = pos;
            }
        }

        // We must be at the end of the value. Read the last parameter and
        // make sure it is valid.
        final String paramStr = valueString.substring(paramStartPos);
        final int colonPos = paramStr.indexOf(':');
        if (colonPos < 0) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_PARAM_NO_COLON.get(valueString));
            return false;
        }

        final String paramName = paramStr.substring(0, colonPos);
        if (!ALLOWED_TTX_PARAMETERS.contains(paramName)) {
            invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_ILLEGAL_PARAMETER.get(valueString,
                    paramName));
            return false;
        }

        // If we've gotten here, then the value must be valid.
        return true;
    }
}
