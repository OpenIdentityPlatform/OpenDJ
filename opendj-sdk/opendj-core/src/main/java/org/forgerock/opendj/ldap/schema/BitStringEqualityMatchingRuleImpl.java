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
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_BIT_STRING_INVALID_BIT;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_BIT_STRING_NOT_QUOTED;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_BIT_STRING_TOO_SHORT;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class defines the bitStringMatch matching rule defined in X.520 and
 * referenced in RFC 2252.
 */
final class BitStringEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    BitStringEqualityMatchingRuleImpl() {
        super(EMR_BIT_STRING_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final String valueString = value.toString().toUpperCase();
        final int length = valueString.length();
        if (length < 3) {
            throw DecodeException.error(WARN_ATTR_SYNTAX_BIT_STRING_TOO_SHORT.get(value));
        }

        if (valueString.charAt(0) != '\''
                || valueString.charAt(length - 1) != 'B'
                || valueString.charAt(length - 2) != '\'') {
            throw DecodeException.error(WARN_ATTR_SYNTAX_BIT_STRING_NOT_QUOTED.get(value));
        }

        for (int i = 1; i < length - 2; i++) {
            switch (valueString.charAt(i)) {
            case '0':
            case '1':
                // These characters are fine.
                break;
            default:
                throw DecodeException.error(WARN_ATTR_SYNTAX_BIT_STRING_INVALID_BIT.get(value, valueString.charAt(i)));
            }
        }

        return ByteString.valueOfUtf8(valueString);
    }
}
