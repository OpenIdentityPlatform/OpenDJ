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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

import com.forgerock.opendj.util.StaticUtils;

/**
 * This class implements the telephoneNumberSubstringsMatch matching rule
 * defined in X.520 and referenced in RFC 2252. Note that although the
 * specification calls for a very rigorous format, this is widely ignored so
 * this matching will compare only numeric digits and strip out everything else.
 */
final class TelephoneNumberSubstringMatchingRuleImpl extends AbstractSubstringMatchingRuleImpl {

    TelephoneNumberSubstringMatchingRuleImpl() {
        super(SMR_TELEPHONE_NAME, EMR_TELEPHONE_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) {
        final String valueString = value.toString();
        final int valueLength = valueString.length();
        final StringBuilder buffer = new StringBuilder(valueLength);

        // Iterate through the characters in the value and filter out
        // everything that isn't a digit.
        for (int i = 0; i < valueLength; i++) {
            final char c = valueString.charAt(i);
            if (StaticUtils.isDigit(c)) {
                buffer.append(c);
            }
        }

        return ByteString.valueOfUtf8(buffer);
    }
}
