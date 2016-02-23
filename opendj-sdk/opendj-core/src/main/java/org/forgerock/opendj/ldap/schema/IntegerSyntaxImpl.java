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

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_INTEGER_DASH_NEEDS_VALUE;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_INTEGER_EMPTY_VALUE;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_INTEGER_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_INTEGER_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_EXACT_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_INTEGER_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class defines the integer attribute syntax, which holds an
 * arbitrarily-long integer value. Equality, ordering, and substring matching
 * will be allowed by default.
 */
final class IntegerSyntaxImpl extends AbstractSyntaxImpl {
    @Override
    public String getEqualityMatchingRule() {
        return EMR_INTEGER_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_INTEGER_NAME;
    }

    @Override
    public String getOrderingMatchingRule() {
        return OMR_INTEGER_OID;
    }

    @Override
    public String getSubstringMatchingRule() {
        return SMR_CASE_EXACT_OID;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        final String valueString = value.toString();
        final int length = valueString.length();

        if (length == 0) {
            invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_EMPTY_VALUE.get(valueString));
            return false;
        } else if (length == 1) {
            switch (valueString.charAt(0)) {
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
                return true;
            case '-':
                invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_DASH_NEEDS_VALUE.get(valueString));
                return false;
            default:
                invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(valueString,
                        valueString.charAt(0), 0));
                return false;
            }
        } else {
            boolean negative = false;

            switch (valueString.charAt(0)) {
            case '0':
                invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO.get(valueString));
                return false;
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                // These are all fine.
                break;
            case '-':
                // This is fine too.
                negative = true;
                break;
            default:
                invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(valueString,
                        valueString.charAt(0), 0));
                return false;
            }

            switch (valueString.charAt(1)) {
            case '0':
                // This is fine as long as the value isn't negative.
                if (negative) {
                    invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO.get(valueString));
                    return false;
                }
                break;
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                // These are all fine.
                break;
            default:
                invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(valueString,
                        valueString.charAt(0), 0));
                return false;
            }

            for (int i = 2; i < length; i++) {
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
                    // These are all fine.
                    break;
                default:
                    invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(
                            valueString, valueString.charAt(0), 0));
                    return false;
                }
            }

            return true;
        }
    }
}
