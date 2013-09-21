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

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_BIT_STRING_INVALID_BIT;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_BIT_STRING_NOT_QUOTED;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_BIT_STRING_TOO_SHORT;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_BIT_STRING_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_BIT_STRING_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class defines the bit string attribute syntax, which is comprised of a
 * string of binary digits surrounded by single quotes and followed by a capital
 * letter "B" (e.g., '101001'B).
 */
final class BitStringSyntaxImpl extends AbstractSyntaxImpl {
    @Override
    public String getEqualityMatchingRule() {
        return EMR_BIT_STRING_OID;
    }

    public String getName() {
        return SYNTAX_BIT_STRING_NAME;
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
        final String valueString = value.toString().toUpperCase();

        final int length = valueString.length();
        if (length < 3) {
            invalidReason.append(WARN_ATTR_SYNTAX_BIT_STRING_TOO_SHORT.get(value.toString()));
            return false;
        }

        if (valueString.charAt(0) != '\'' || valueString.charAt(length - 2) != '\''
                || valueString.charAt(length - 1) != 'B') {
            invalidReason.append(WARN_ATTR_SYNTAX_BIT_STRING_NOT_QUOTED.get(value.toString()));
            return false;
        }

        for (int i = 1; i < length - 2; i++) {
            switch (valueString.charAt(i)) {
            case '0':
            case '1':
                // These characters are fine.
                break;
            default:
                invalidReason.append(WARN_ATTR_SYNTAX_BIT_STRING_INVALID_BIT.get(value.toString(),
                        String.valueOf(valueString.charAt(i))));
                return false;
            }
        }

        // If we've gotten here, then everything is fine.
        return true;
    }
}
