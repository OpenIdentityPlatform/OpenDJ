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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.GeneralizedTime;

/**
 * This class defines the generalizedTimeMatch matching rule defined in X.520
 * and referenced in RFC 2252.
 */
final class GeneralizedTimeEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    GeneralizedTimeEqualityMatchingRuleImpl() {
        super(EMR_GENERALIZED_TIME_NAME);
    }

    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) throws DecodeException {
        return normalizeAttributeValue(value);
    }

    static ByteString normalizeAttributeValue(final ByteSequence value) throws DecodeException {
        try {
            final GeneralizedTime time = GeneralizedTime.valueOf(value.toString());
            return createNormalizedAttributeValue(time.getTimeInMillis());
        } catch (LocalizedIllegalArgumentException e) {
            throw DecodeException.error(e.getMessageObject());
        }
    }

    static ByteString createNormalizedAttributeValue(final long timeInMillis) {
        /* Dates older than 1970 will be negative and will sort after dates more recent than 1970 due to twos
         * complement encoding. Therefore mangle the time in order to ensure that it is positive for all valid values
         * of a generalized time.
         */
        return ByteString.valueOfLong(timeInMillis - GeneralizedTime.MIN_GENERALIZED_TIME_MS);
    }
}
