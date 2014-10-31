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

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_BOOLEAN_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_BOOLEAN_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class defines the Boolean attribute syntax, which only allows values of
 * "TRUE" or "FALSE" (although this implementation is more flexible and will
 * also allow "YES", "ON", or "1" instead of "TRUE", or "NO", "OFF", or "0"
 * instead of "FALSE"). Only equality matching is allowed by default for this
 * syntax.
 */
final class BooleanSyntaxImpl extends AbstractSyntaxImpl {
    @Override
    public String getEqualityMatchingRule() {
        return EMR_BOOLEAN_OID;
    }

    public String getName() {
        return SYNTAX_BOOLEAN_NAME;
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

        if (!"TRUE".equals(valueString) && !"YES".equals(valueString)
                && !"ON".equals(valueString) && !"1".equals(valueString)
                && !"FALSE".equals(valueString) && !"NO".equals(valueString)
                && !"OFF".equals(valueString) && !"0".equals(valueString)) {
            invalidReason.append(WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN.get(value.toString()));
        }
        return true;
    }
}
