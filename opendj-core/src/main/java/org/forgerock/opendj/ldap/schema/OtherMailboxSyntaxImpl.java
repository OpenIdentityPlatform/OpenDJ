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

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CASE_IGNORE_LIST_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_IGNORE_LIST_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_OTHER_MAILBOX_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class implements the other mailbox attribute syntax, which consists of a
 * printable string component (the mailbox type) followed by a dollar sign and
 * an IA5 string component (the mailbox). Equality and substring matching will
 * be allowed by default.
 */
final class OtherMailboxSyntaxImpl extends AbstractSyntaxImpl {

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_LIST_OID;
    }

    public String getName() {
        return SYNTAX_OTHER_MAILBOX_NAME;
    }

    @Override
    public String getSubstringMatchingRule() {
        return SMR_CASE_IGNORE_LIST_OID;
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
        // Check to see if the provided value was null. If so, then that's
        // not acceptable.
        if (value == null) {
            invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_EMPTY_VALUE.get());
            return false;
        }

        // Get the value as a string and determine its length. If it is
        // empty, then that's not acceptable.
        final String valueString = value.toString();
        final int valueLength = valueString.length();
        if (valueLength == 0) {
            invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_EMPTY_VALUE.get());
            return false;
        }

        // Iterate through the characters in the vale until we find a dollar
        // sign. Every character up to that point must be a printable string
        // character.
        int pos = 0;
        for (; pos < valueLength; pos++) {
            final char c = valueString.charAt(pos);
            if (c == '$') {
                if (pos == 0) {
                    invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_NO_MBTYPE.get(valueString));
                    return false;
                }

                pos++;
                break;
            } else if (!PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
                invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_ILLEGAL_MBTYPE_CHAR.get(
                        valueString, String.valueOf(c), pos));
                return false;
            }
        }

        // Make sure there is at least one character left for the mailbox.
        if (pos >= valueLength) {
            invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_NO_MAILBOX.get(valueString));
            return false;
        }

        // The remaining characters in the value must be IA5 (ASCII)
        // characters.
        for (; pos < valueLength; pos++) {
            final char c = valueString.charAt(pos);
            if (c != (c & 0x7F)) {
                invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_ILLEGAL_MB_CHAR.get(valueString,
                        String.valueOf(c), pos));
                return false;
            }
        }

        // If we've gotten here, then the value is OK.
        return true;
    }
}
