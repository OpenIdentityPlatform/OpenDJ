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

    @Override
    public String getName() {
        return SYNTAX_OTHER_MAILBOX_NAME;
    }

    @Override
    public String getSubstringMatchingRule() {
        return SMR_CASE_IGNORE_LIST_OID;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
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
