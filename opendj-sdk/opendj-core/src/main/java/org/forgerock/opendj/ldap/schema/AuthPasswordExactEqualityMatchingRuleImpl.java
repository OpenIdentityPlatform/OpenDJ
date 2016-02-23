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
 * Portions copyright 2014-2015 ForgeRock AS.
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
