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
 *      Portions Copyright 2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.util.StaticUtils.toLowerCase;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_DELIVERY_METHOD_INVALID_ELEMENT;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_DELIVERY_METHOD_NO_ELEMENTS;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import java.util.HashSet;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class defines the delivery method attribute syntax. This contains one or
 * more of a fixed set of values. If there are multiple values, then they are
 * separated by spaces with a dollar sign between them. The allowed values
 * include:
 * <UL>
 * <LI>any</LI>
 * <LI>mhs</LI>
 * <LI>physical</LI>
 * <LI>telex</LI>
 * <LI>teletex</LI>
 * <LI>g3fax</LI>
 * <LI>g4fax</LI>
 * <LI>ia5</LI>
 * <LI>videotex</LI>
 * <LI>telephone</LI>
 * </UL>
 */
final class DeliveryMethodSyntaxImpl extends AbstractSyntaxImpl {
    /**
     * The set of values that may be used as delivery methods.
     */
    private static final HashSet<String> ALLOWED_VALUES = new HashSet<>();
    {
        ALLOWED_VALUES.add("any");
        ALLOWED_VALUES.add("mhs");
        ALLOWED_VALUES.add("physical");
        ALLOWED_VALUES.add("telex");
        ALLOWED_VALUES.add("teletex");
        ALLOWED_VALUES.add("g3fax");
        ALLOWED_VALUES.add("g4fax");
        ALLOWED_VALUES.add("ia5");
        ALLOWED_VALUES.add("videotex");
        ALLOWED_VALUES.add("telephone");
    }

    @Override
    public String getApproximateMatchingRule() {
        return AMR_DOUBLE_METAPHONE_OID;
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_OID;
    }

    public String getName() {
        return SYNTAX_DELIVERY_METHOD_NAME;
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
        final String stringValue = toLowerCase(value.toString());
        final StringTokenizer tokenizer = new StringTokenizer(stringValue, " $");
        if (!tokenizer.hasMoreTokens()) {
            invalidReason.append(ERR_ATTR_SYNTAX_DELIVERY_METHOD_NO_ELEMENTS.get(value.toString()));
            return false;
        }

        while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken();
            if (!ALLOWED_VALUES.contains(token)) {
                invalidReason.append(ERR_ATTR_SYNTAX_DELIVERY_METHOD_INVALID_ELEMENT.get(value
                        .toString(), token));
                return false;
            }
        }

        return true;
    }
}
