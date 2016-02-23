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

    @Override
    public String getName() {
        return SYNTAX_BIT_STRING_NAME;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
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
