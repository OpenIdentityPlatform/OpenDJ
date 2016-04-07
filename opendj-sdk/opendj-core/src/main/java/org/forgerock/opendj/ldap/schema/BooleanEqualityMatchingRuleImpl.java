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

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class defines the booleanMatch matching rule defined in X.520 and
 * referenced in RFC 4519.
 */
final class BooleanEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    BooleanEqualityMatchingRuleImpl() {
        super(EMR_BOOLEAN_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final String valueString = value.toString().toUpperCase();
        if ("TRUE".equals(valueString) || "YES".equals(valueString)
                || "ON".equals(valueString) || "1".equals(valueString)) {
            return SchemaConstants.TRUE_VALUE;
        } else if ("FALSE".equals(valueString) || "NO".equals(valueString)
                || "OFF".equals(valueString) || "0".equals(valueString)) {
            return SchemaConstants.FALSE_VALUE;
        }
        throw DecodeException.error(WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN.get(value.toString()));
    }
}
