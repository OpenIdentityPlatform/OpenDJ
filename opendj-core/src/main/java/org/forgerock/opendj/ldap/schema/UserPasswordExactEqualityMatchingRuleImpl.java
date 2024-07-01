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
 * Portions copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.StaticUtils;

/**
 * This class implements the userPasswordExactMatch matching rule, which will
 * simply compare encoded hashed password values to see if they are exactly
 * equal to each other.
 */
final class UserPasswordExactEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    UserPasswordExactEqualityMatchingRuleImpl() {
        super(EMR_USER_PASSWORD_EXACT_NAME);
    }

    @Override
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
            return ByteString.valueOfUtf8(builder);
        } else {
            return value.toByteString();
        }
    }

    @Override
    public String keyToHumanReadableString(ByteSequence key) {
        return key.toString();
    }
}
