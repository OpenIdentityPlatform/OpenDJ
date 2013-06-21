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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_GENERALIZED_TIME_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_GENERALIZED_TIME_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_GENERALIZED_TIME_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.GeneralizedTime;

/**
 * This class implements the fax attribute syntax. This should be restricted to
 * holding only fax message contents, but we will accept any set of bytes. It
 * will be treated much like the octet string attribute syntax.
 */
final class GeneralizedTimeSyntaxImpl extends AbstractSyntaxImpl {

    @Override
    public String getEqualityMatchingRule() {
        return EMR_GENERALIZED_TIME_OID;
    }

    public String getName() {
        return SYNTAX_GENERALIZED_TIME_NAME;
    }

    @Override
    public String getOrderingMatchingRule() {
        return OMR_GENERALIZED_TIME_OID;
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
        try {
            GeneralizedTime.valueOf(value.toString());
            return true;
        } catch (final LocalizedIllegalArgumentException e) {
            invalidReason.append(e.getMessageObject());
            return false;
        }
    }
}
