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

import static org.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class defines the booleanMatch matching rule defined in X.520 and
 * referenced in RFC 4519.
 */
final class BooleanEqualityMatchingRuleImpl extends AbstractMatchingRuleImpl {
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final String valueString = value.toString().toUpperCase();
        if (valueString.equals("TRUE") || valueString.equals("YES") || valueString.equals("ON")
                || valueString.equals("1")) {
            return SchemaConstants.TRUE_VALUE;
        } else if (valueString.equals("FALSE") || valueString.equals("NO")
                || valueString.equals("OFF") || valueString.equals("0")) {
            return SchemaConstants.FALSE_VALUE;
        }

        throw DecodeException.error(WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN.get(value.toString()));
    }
}
