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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_SUBSTRING_ASSERTION_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;
import com.forgerock.opendj.ldap.CoreMessages;

/**
 * This class defines the substring assertion attribute syntax, which contains
 * one or more substring components, as used in a substring search filter. For
 * the purposes of matching, it will be treated like a Directory String syntax
 * except that approximate matching will not be allowed.
 */
final class SubstringAssertionSyntaxImpl extends AbstractSyntaxImpl {

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_SUBSTRING_ASSERTION_NAME;
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
        return true;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        // Get the string representation of the value and check its length.
        // A zero-length value is acceptable. A one-length value is
        // acceptable as long as it is not an asterisk. For all other
        // lengths, just ensure that there are no consecutive wildcards.
        final String valueString = value.toString();
        final int valueLength = valueString.length();
        if (valueLength == 0) {
            return true;
        } else if (valueLength == 1) {
            if (valueString.charAt(0) == '*') {
                invalidReason.append(CoreMessages.WARN_ATTR_SYNTAX_SUBSTRING_ONLY_WILDCARD.get());

                return false;
            } else {
                return true;
            }
        } else {
            for (int i = 1; i < valueLength; i++) {
                if (valueString.charAt(i) == '*' && valueString.charAt(i - 1) == '*') {
                    invalidReason
                            .append(CoreMessages.WARN_ATTR_SYNTAX_SUBSTRING_CONSECUTIVE_WILDCARDS
                                    .get(valueString, i));
                    return false;
                }
            }

            return true;
        }
    }
}
