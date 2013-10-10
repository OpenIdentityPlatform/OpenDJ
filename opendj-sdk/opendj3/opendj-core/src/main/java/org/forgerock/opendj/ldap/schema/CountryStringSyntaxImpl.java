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
 *      Portions Copyright 2012 Manuel Gaupp
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
