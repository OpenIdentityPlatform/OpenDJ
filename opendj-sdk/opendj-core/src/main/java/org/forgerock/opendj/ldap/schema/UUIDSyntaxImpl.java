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

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_UUID_EXPECTED_DASH;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_UUID_EXPECTED_HEX;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_UUID_INVALID_LENGTH;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_UUID_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class implements the UUID syntax, which is defined in RFC 4530. Equality
 * and ordering matching will be allowed by default.
 */
final class UUIDSyntaxImpl extends AbstractSyntaxImpl {
    public String getName() {
        return SYNTAX_UUID_NAME;
    }

    public boolean isHumanReadable() {
        return true;
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
        // We will only accept values that look like valid UUIDs. This means
        // that all values must be in the form
        // HHHHHHHH-HHHH-HHHH-HHHH-HHHHHHHHHHHH, where "H" represents a
        // hexadecimal digit. First, make sure that the value is exactly 36
        // bytes long.
        final String valueString = value.toString();
        if (valueString.length() != 36) {
            invalidReason.append(WARN_ATTR_SYNTAX_UUID_INVALID_LENGTH.get(valueString, valueString
                    .length()));
            return false;
        }

        // Next, iterate through each character. Make sure that the 9th,
        // 14th, 19th, and 24th characters are dashes and the rest are hex
        // digits.
        for (int i = 0; i < 36; i++) {
            switch (i) {
            case 8:
            case 13:
            case 18:
            case 23:
                if (valueString.charAt(i) != '-') {
                    invalidReason.append(WARN_ATTR_SYNTAX_UUID_EXPECTED_DASH.get(valueString, i,
                            String.valueOf(valueString.charAt(i))));
                    return false;
                }
                break;
            default:
                switch (valueString.charAt(i)) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case 'a':
                case 'b':
                case 'c':
                case 'd':
                case 'e':
                case 'f':
                case 'A':
                case 'B':
                case 'C':
                case 'D':
                case 'E':
                case 'F':
                    break;
                default:

                    invalidReason.append(WARN_ATTR_SYNTAX_UUID_EXPECTED_HEX.get(valueString, i,
                            String.valueOf(valueString.charAt(i))));
                    return false;
                }
            }
        }

        // If we've gotten here, then the value is acceptable.
        return true;
    }
}
