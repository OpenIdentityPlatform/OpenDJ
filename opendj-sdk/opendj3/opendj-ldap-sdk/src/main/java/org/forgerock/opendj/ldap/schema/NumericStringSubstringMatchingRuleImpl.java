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

import static com.forgerock.opendj.util.StringPrepProfile.NO_CASE_FOLD;
import static com.forgerock.opendj.util.StringPrepProfile.TRIM;
import static com.forgerock.opendj.util.StringPrepProfile.prepareUnicode;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

/**
 * This class implements the numericStringSubstringsMatch matching rule defined
 * in X.520 and referenced in RFC 2252.
 */
final class NumericStringSubstringMatchingRuleImpl extends AbstractSubstringMatchingRuleImpl {
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) {
        final StringBuilder buffer = new StringBuilder();
        prepareUnicode(buffer, value, TRIM, NO_CASE_FOLD);

        if (buffer.length() == 0) {
            return ByteString.empty();
        }
        return ByteString.valueOf(buffer.toString());
    }
}
