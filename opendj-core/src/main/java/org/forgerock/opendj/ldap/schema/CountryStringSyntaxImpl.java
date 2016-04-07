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
 * Portions Copyright 2012 Manuel Gaupp
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_COUNTRY_STRING_INVALID_LENGTH;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_COUNTRY_STRING_NO_VALID_ISO_CODE;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class defines the country string attribute syntax, which should be a
 * two-character ISO 3166 country code. However, for maintainability, it will
 * accept any value consisting entirely of two upper-case characters. In most
 * ways, it will behave like the directory string attribute syntax.
 */
final class CountryStringSyntaxImpl extends AbstractSyntaxImpl {

    @Override
    public String getApproximateMatchingRule() {
        return AMR_DOUBLE_METAPHONE_OID;
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_COUNTRY_STRING_NAME;
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
        final String stringValue = value.toString();
        if (stringValue.length() != 2) {
            invalidReason.append(ERR_ATTR_SYNTAX_COUNTRY_STRING_INVALID_LENGTH.get(stringValue));
            return false;
        }

        // Check for a string containing [A-Z][A-Z]
        if (stringValue.charAt(0) < 'A' || stringValue.charAt(0) > 'Z'
            || stringValue.charAt(1) < 'A' || stringValue.charAt(1) > 'Z') {
            invalidReason.append(ERR_ATTR_SYNTAX_COUNTRY_STRING_NO_VALID_ISO_CODE.get(stringValue));
            return false;
        }

        return true;
    }
}
