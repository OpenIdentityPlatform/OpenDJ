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

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.StaticUtils;

/**
 * This class implements the userPasswordExactMatch matching rule, which will
 * simply compare encoded hashed password values to see if they are exactly
 * equal to each other.
 */
final class UserPasswordExactEqualityMatchingRuleImpl extends AbstractMatchingRuleImpl {
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        // The normalized form of this matching rule is exactly equal to the
        // non-normalized form, except that the scheme needs to be converted
        // to lowercase (if there is one).

        if (UserPasswordSyntaxImpl.isEncoded(value)) {
            final StringBuilder builder = new StringBuilder(value.length());
            int closingBracePos = -1;
            for (int i = 1; i < value.length(); i++) {
                if (value.byteAt(i) == '}') {
                    closingBracePos = i;
                    break;
                }
            }
            final ByteSequence seq1 = value.subSequence(0, closingBracePos + 1);
            final ByteSequence seq2 = value.subSequence(closingBracePos + 1, value.length());
            StaticUtils.toLowerCase(seq1, builder);
            builder.append(seq2);
            return ByteString.valueOf(builder.toString());
        } else {
            return value.toByteString();
        }
    }
}
