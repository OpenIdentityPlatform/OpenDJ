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
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

/** This class implements the authPasswordMatch matching rule defined in RFC 3112. */
final class AuthPasswordExactEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {
    AuthPasswordExactEqualityMatchingRuleImpl() {
        super(EMR_AUTH_PASSWORD_EXACT_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final String[] authPWComponents = AuthPasswordSyntaxImpl.decodeAuthPassword(value.toString());

        final StringBuilder normalizedValue = new StringBuilder(
            2 + authPWComponents[0].length() + authPWComponents[1].length() + authPWComponents[2].length());
        normalizedValue.append(authPWComponents[0]);
        normalizedValue.append('$');
        normalizedValue.append(authPWComponents[1]);
        normalizedValue.append('$');
        normalizedValue.append(authPWComponents[2]);

        return ByteString.valueOfUtf8(normalizedValue);
    }
}
