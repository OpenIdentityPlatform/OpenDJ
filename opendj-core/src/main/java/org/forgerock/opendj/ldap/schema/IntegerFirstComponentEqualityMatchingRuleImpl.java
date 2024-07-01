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
 * Portions copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import com.forgerock.opendj.util.SubstringReader;

/**
 * This class implements the integerFirstComponentMatch matching rule defined in
 * X.520 and referenced in RFC 2252. This rule is intended for use with
 * attributes whose values contain a set of parentheses enclosing a
 * space-delimited set of names and/or name-value pairs (like attribute type or
 * objectclass descriptions) in which the "first component" is the first item
 * after the opening parenthesis.
 */
final class IntegerFirstComponentEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    IntegerFirstComponentEqualityMatchingRuleImpl() {
        super(EMR_INTEGER_FIRST_COMPONENT_NAME);
    }

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue) throws DecodeException {
        try {
            final String definition = assertionValue.toString();
            return defaultAssertion(normalizeRuleID(new SubstringReader(definition)));
        } catch (final Exception e) {
            logger.debug(LocalizableMessage.raw("%s", e));
            throw DecodeException.error(ERR_EMR_INTFIRSTCOMP_FIRST_COMPONENT_NOT_INT.get(assertionValue));
        }
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) throws DecodeException {
        final String definition = value.toString();
        final SubstringReader reader = new SubstringReader(definition);

        // We'll do this a character at a time. First, skip over any leading whitespace.
        reader.skipWhitespaces();

        if (reader.remaining() <= 0) {
            // This means that the value was empty or contained only whitespace.
            // That is illegal.
            throw DecodeException.error(ERR_ATTR_SYNTAX_EMPTY_VALUE.get());
        }

        // The next character must be an open parenthesis.
        // If it is not, then that is an error.
        final char c = reader.read();
        if (c != '(') {
            throw DecodeException.error(ERR_ATTR_SYNTAX_EXPECTED_OPEN_PARENTHESIS.get(
                    definition, reader.pos() - 1, c));
        }

        // Skip over any spaces immediately following the opening parenthesis.
        reader.skipWhitespaces();

        // The next set of characters must be the OID.
        return normalizeRuleID(reader);
    }

    private ByteString normalizeRuleID(final SubstringReader reader) throws DecodeException {
        return ByteString.valueOfInt(SchemaUtils.readRuleID(reader));
    }
}
