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
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

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
 * This class defines the attribute type description syntax, which is used to
 * hold attribute type definitions in the server schema. The format of this
 * syntax is defined in RFC 2252.
 */
final class AttributeTypeSyntaxImpl extends AbstractSyntaxImpl {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    @Override
    public String getEqualityMatchingRule() {
        return EMR_OID_FIRST_COMPONENT_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_ATTRIBUTE_TYPE_NAME;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        final String definition = value.toString();
        try {
            final SubstringReader reader = new SubstringReader(definition);
            final boolean allowMalformedNamesAndOptions = schema.getOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // Value was empty or contained only whitespace. This is illegal.
                throwDecodeException(logger, ERR_ATTR_SYNTAX_ATTRTYPE_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                throwDecodeException(logger, ERR_ATTR_SYNTAX_ATTRTYPE_EXPECTED_OPEN_PARENTHESIS.get(
                    definition, reader.pos() - 1, String.valueOf(c)));
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            readOID(reader, allowMalformedNamesAndOptions);

            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the definition. But before we start, set default
            // values for everything else we might need to know.
            while (true) {
                final String tokenName = SchemaUtils.readTokenName(reader);

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
                } else if ("sup".equalsIgnoreCase(tokenName)) {
                    // This specifies the name or OID of the superior attribute
                    // type from which this attribute type should inherit its
                    // properties.
                    readOID(reader, allowMalformedNamesAndOptions);
                } else if ("equality".equalsIgnoreCase(tokenName)) {
                    // This specifies the name or OID of the equality matching
                    // rule to use for this attribute type.
                    readOID(reader, allowMalformedNamesAndOptions);
                } else if ("ordering".equalsIgnoreCase(tokenName)) {
                    // This specifies the name or OID of the ordering matching
                    // rule to use for this attribute type.
                    readOID(reader, allowMalformedNamesAndOptions);
                } else if ("substr".equalsIgnoreCase(tokenName)) {
                    // This specifies the name or OID of the substring matching
                    // rule to use for this attribute type.
                    readOID(reader, allowMalformedNamesAndOptions);
                } else if ("syntax".equalsIgnoreCase(tokenName)) {
                    // This specifies the numeric OID of the syntax for this
                    // matching rule. It may optionally be immediately followed
                    // by
                    // an open curly brace, an integer definition, and a close
                    // curly brace to suggest the minimum number of characters
                    // that should be allowed in values of that type. This
                    // implementation will ignore any such length because it
                    // does
                    // not impose any practical limit on the length of attribute
                    // values.
                    readOIDLen(reader, allowMalformedNamesAndOptions);
                } else if ("single-definition".equalsIgnoreCase(tokenName)) {
                    // This indicates that attributes of this type are allowed to
                    // have at most one definition. We do not need any more
                    // parsing for this token.
                } else if ("single-value".equalsIgnoreCase(tokenName)) {
                    // This indicates that attributes of this type are allowed
                    // to have at most one value.
                    // We do not need any more parsing for this token.
                } else if ("collective".equalsIgnoreCase(tokenName)) {
                    // This indicates that attributes of this type are collective
                    // (i.e., have their values generated dynamically in some way).
                    // We do not need any more parsing for this token.
                } else if ("no-user-modification".equalsIgnoreCase(tokenName)) {
                    // This indicates that the values of attributes of this type
                    // are not to be modified by end users. We do not need any
                    // more parsing for this token.
                } else if ("usage".equalsIgnoreCase(tokenName)) {
                    // This specifies the usage string for this attribute type.
                    // It should be followed by one of the strings
                    // "userApplications", "directoryOperation",
                    // "distributedOperation", or "dSAOperation".
                    int length = 0;

                    reader.skipWhitespaces();
                    reader.mark();

                    while (" )".indexOf(reader.read()) == -1) {
                        length++;
                    }

                    reader.reset();
                    final String usageStr = reader.read(length);
                    if (!"userapplications".equalsIgnoreCase(usageStr)
                            && !"directoryoperation".equalsIgnoreCase(usageStr)
                            && !"distributedoperation".equalsIgnoreCase(usageStr)
                            && !"dsaoperation".equalsIgnoreCase(usageStr)) {
                        throwDecodeException(logger,
                            WARN_ATTR_SYNTAX_ATTRTYPE_INVALID_ATTRIBUTE_USAGE1.get(definition, usageStr));
                    }
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    SchemaUtils.readExtensions(reader);
                } else {
                    throwDecodeException(logger, ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_TOKEN1.get(definition, tokenName));

                }
            }
            return true;
        } catch (final DecodeException de) {
            invalidReason.append(ERR_ATTR_SYNTAX_ATTRTYPE_INVALID1.get(definition, de
                    .getMessageObject()));
            return false;
        }
    }
}
