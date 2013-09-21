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

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class implements the IA5 string attribute syntax, which is simply a set
 * of ASCII characters. By default, they will be treated in a case-insensitive
 * manner, and equality, ordering, substring, and approximate matching will be
 * allowed.
 */
final class IA5StringSyntaxImpl extends AbstractSyntaxImpl {
    @Override
    public String getApproximateMatchingRule() {
        return AMR_DOUBLE_METAPHONE_OID;
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_OID;
    }

    public String getName() {
        return SYNTAX_IA5_STRING_NAME;
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
        // We will allow any value that does not contain any non-ASCII
        // characters. Empty values are acceptable as well.
        byte b;
        for (int i = 0; i < value.length(); i++) {
            b = value.byteAt(i);
            if ((b & 0x7F) != b) {

                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER.get(value.toString(), String
                                .valueOf(b));
                invalidReason.append(message);
                return false;
            }
        }

        return true;
    }
}
