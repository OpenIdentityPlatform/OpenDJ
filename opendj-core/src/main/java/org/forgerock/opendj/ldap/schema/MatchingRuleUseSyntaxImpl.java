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
 *      Portions copyright 2011-2014 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import java.util.Set;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.SubstringReader;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.forgerock.opendj.ldap.schema.SchemaUtils.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;

/**
 * This class implements the matching rule use description syntax, which is used
 * to hold matching rule use definitions in the server schema. The format of
 * this syntax is defined in RFC 2252.
 */
final class MatchingRuleUseSyntaxImpl extends AbstractSyntaxImpl {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    @Override
    public String getEqualityMatchingRule() {
        return EMR_OID_FIRST_COMPONENT_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_MATCHING_RULE_USE_NAME;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    /**
     * Indicates whether the provided value is acceptable for use in an
     * attribute with this syntax. If it is not, then the reason may be appended
     * to the provided buffer.
     *
     * @param schema
     *            The schema in which this syntax is defined.
     * @param value
     *            The value for which to make the determination.
     * @param invalidReason
     *            The buffer to which the invalid reason should be appended.
     * @return <CODE>true</CODE> if the provided value is acceptable for use
     *         with this syntax, or <CODE>false</CODE> if not.
     */
    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        // We'll use the decodeAttributeType method to determine if the
        // value is acceptable.
        final String definition = value.toString();
        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // Value was empty or contained only whitespace. This is illegal.
                throwDecodeException(logger, ERR_ATTR_SYNTAX_MRUSE_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                throwDecodeException(logger,
                    ERR_ATTR_SYNTAX_MRUSE_EXPECTED_OPEN_PARENTHESIS.get(definition, reader.pos() - 1, c));
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            final boolean allowMalformedNamesAndOptions = schema.getOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS);
            readOID(reader, allowMalformedNamesAndOptions);

            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the value. But before we start, set default values
            // for everything else we might need to know.
            Set<String> attributes = null;
            while (true) {
                final String tokenName = readTokenName(reader);

                if (tokenName == null) {
                    // No more tokens.
                    break;
                } else if ("name".equalsIgnoreCase(tokenName)) {
                    readNameDescriptors(reader, allowMalformedNamesAndOptions);
                } else if ("desc".equalsIgnoreCase(tokenName)) {
                    // This specifies the description for the attribute type. It
                    // is an arbitrary string of characters enclosed in single
                    // quotes.
                    readQuotedString(reader);
                } else if ("obsolete".equalsIgnoreCase(tokenName)) {
                    // This indicates whether the attribute type should be
                    // considered obsolete. We do not need to do any more
                    // parsing for this token.
                } else if ("applies".equalsIgnoreCase(tokenName)) {
                    attributes = readOIDs(reader, allowMalformedNamesAndOptions);
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    readExtensions(reader);
                } else {
                    throwDecodeException(logger, ERR_ATTR_SYNTAX_MRUSE_ILLEGAL_TOKEN1.get(definition, tokenName));
                }
            }

            // Make sure that the set of attributes was defined.
            if (attributes == null || attributes.size() == 0) {
                throwDecodeException(logger, ERR_ATTR_SYNTAX_MRUSE_NO_ATTR.get(definition));
            }
            return true;
        } catch (final DecodeException de) {
            invalidReason.append(ERR_ATTR_SYNTAX_MRUSE_INVALID1.get(definition, de.getMessageObject()));
            return false;
        }
    }
}
