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

import static org.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_EMPTY_VALUE;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_EXPECTED_OPEN_PARENTHESIS;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_EMR_INTFIRSTCOMP_FIRST_COMPONENT_NOT_INT;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.SubstringReader;

/**
 * This class implements the integerFirstComponentMatch matching rule defined in
 * X.520 and referenced in RFC 2252. This rule is intended for use with
 * attributes whose values contain a set of parentheses enclosing a
 * space-delimited set of names and/or name-value pairs (like attribute type or
 * objectclass descriptions) in which the "first component" is the first item
 * after the opening parenthesis.
 */
final class IntegerFirstComponentEqualityMatchingRuleImpl extends AbstractMatchingRuleImpl {

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        try {
            final String definition = value.toString();
            final SubstringReader reader = new SubstringReader(definition);
            final int intValue = SchemaUtils.readRuleID(reader);

            return new Assertion() {
                public ConditionResult matches(final ByteSequence attributeValue) {
                    final int actualIntValue = attributeValue.toByteString().toInt();
                    return intValue == actualIntValue ? ConditionResult.TRUE
                            : ConditionResult.FALSE;
                }
            };
        } catch (final Exception e) {
            StaticUtils.DEBUG_LOG.throwing("IntegerFirstComponentEqualityMatchingRule",
                    "getAssertion", e);

            final LocalizableMessage message =
                    ERR_EMR_INTFIRSTCOMP_FIRST_COMPONENT_NOT_INT.get(value.toString());
            throw DecodeException.error(message);
        }

    }

    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final String definition = value.toString();
        final SubstringReader reader = new SubstringReader(definition);

        // We'll do this a character at a time. First, skip over any leading
        // whitespace.
        reader.skipWhitespaces();

        if (reader.remaining() <= 0) {
            // This means that the value was empty or contained only
            // whitespace. That is illegal.
            final LocalizableMessage message = ERR_ATTR_SYNTAX_EMPTY_VALUE.get();
            throw DecodeException.error(message);
        }

        // The next character must be an open parenthesis. If it is not,
        // then that is an error.
        final char c = reader.read();
        if (c != '(') {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_EXPECTED_OPEN_PARENTHESIS.get(definition, (reader.pos() - 1),
                            String.valueOf(c));
            throw DecodeException.error(message);
        }

        // Skip over any spaces immediately following the opening
        // parenthesis.
        reader.skipWhitespaces();

        // The next set of characters must be the OID.
        return ByteString.valueOf(SchemaUtils.readRuleID(reader));
    }
}
