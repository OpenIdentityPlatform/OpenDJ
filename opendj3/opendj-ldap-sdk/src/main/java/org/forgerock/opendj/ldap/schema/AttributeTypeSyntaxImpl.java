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
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_OID_FIRST_COMPONENT_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_ATTRIBUTE_TYPE_NAME;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.SubstringReader;

/**
 * This class defines the attribute type description syntax, which is used to
 * hold attribute type definitions in the server schema. The format of this
 * syntax is defined in RFC 2252.
 */
final class AttributeTypeSyntaxImpl extends AbstractSyntaxImpl {

    @Override
    public String getEqualityMatchingRule() {
        return EMR_OID_FIRST_COMPONENT_OID;
    }

    public String getName() {
        return SYNTAX_ATTRIBUTE_TYPE_NAME;
    }

    public boolean isHumanReadable() {
        return true;
    }

    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        final String definition = value.toString();
        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // This means that the definition was empty or contained only
                // whitespace. That is illegal.
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_ATTRTYPE_EMPTY_VALUE1.get(definition);
                final DecodeException e = DecodeException.error(message);
                StaticUtils.DEBUG_LOG.throwing("AttributeTypeSyntax", "valueIsAcceptable", e);
                throw e;
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_ATTRTYPE_EXPECTED_OPEN_PARENTHESIS.get(definition, (reader
                                .pos() - 1), String.valueOf(c));
                final DecodeException e = DecodeException.error(message);
                StaticUtils.DEBUG_LOG.throwing("AttributeTypeSyntax", "valueIsAcceptable", e);
                throw e;
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            SchemaUtils.readOID(reader, schema.allowMalformedNamesAndOptions());

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
                } else if (tokenName.equalsIgnoreCase("name")) {
                    SchemaUtils.readNameDescriptors(reader, schema.allowMalformedNamesAndOptions());
                } else if (tokenName.equalsIgnoreCase("desc")) {
                    // This specifies the description for the attribute type. It
                    // is an arbitrary string of characters enclosed in single
                    // quotes.
                    SchemaUtils.readQuotedString(reader);
                } else if (tokenName.equalsIgnoreCase("obsolete")) {
                    // This indicates whether the attribute type should be
                    // considered obsolete. We do not need to do any more
                    // parsing
                    // for this token.
                } else if (tokenName.equalsIgnoreCase("sup")) {
                    // This specifies the name or OID of the superior attribute
                    // type from which this attribute type should inherit its
                    // properties.
                    SchemaUtils.readOID(reader, schema.allowMalformedNamesAndOptions());
                } else if (tokenName.equalsIgnoreCase("equality")) {
                    // This specifies the name or OID of the equality matching
                    // rule to use for this attribute type.
                    SchemaUtils.readOID(reader, schema.allowMalformedNamesAndOptions());
                } else if (tokenName.equalsIgnoreCase("ordering")) {
                    // This specifies the name or OID of the ordering matching
                    // rule to use for this attribute type.
                    SchemaUtils.readOID(reader, schema.allowMalformedNamesAndOptions());
                } else if (tokenName.equalsIgnoreCase("substr")) {
                    // This specifies the name or OID of the substring matching
                    // rule to use for this attribute type.
                    SchemaUtils.readOID(reader, schema.allowMalformedNamesAndOptions());
                } else if (tokenName.equalsIgnoreCase("syntax")) {
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
                    SchemaUtils.readOIDLen(reader, schema.allowMalformedNamesAndOptions());
                } else if (tokenName.equalsIgnoreCase("single-definition")) {
                    // This indicates that attributes of this type are allowed
                    // to
                    // have at most one definition. We do not need any more
                    // parsing for this token.
                } else if (tokenName.equalsIgnoreCase("single-value")) {
                    // This indicates that attributes of this type are allowed
                    // to
                    // have at most one value. We do not need any more parsing
                    // for
                    // this token.
                } else if (tokenName.equalsIgnoreCase("collective")) {
                    // This indicates that attributes of this type are
                    // collective
                    // (i.e., have their values generated dynamically in some
                    // way). We do not need any more parsing for this token.
                } else if (tokenName.equalsIgnoreCase("no-user-modification")) {
                    // This indicates that the values of attributes of this type
                    // are not to be modified by end users. We do not need any
                    // more parsing for this token.
                } else if (tokenName.equalsIgnoreCase("usage")) {
                    // This specifies the usage string for this attribute type.
                    // It
                    // should be followed by one of the strings
                    // "userApplications", "directoryOperation",
                    // "distributedOperation", or "dSAOperation".
                    int length = 0;

                    reader.skipWhitespaces();
                    reader.mark();

                    while (reader.read() != ' ') {
                        length++;
                    }

                    reader.reset();
                    final String usageStr = reader.read(length);
                    if (!usageStr.equalsIgnoreCase("userapplications")
                            && !usageStr.equalsIgnoreCase("directoryoperation")
                            && !usageStr.equalsIgnoreCase("distributedoperation")
                            && !usageStr.equalsIgnoreCase("dsaoperation")) {
                        final LocalizableMessage message =
                                WARN_ATTR_SYNTAX_ATTRTYPE_INVALID_ATTRIBUTE_USAGE1.get(definition,
                                        usageStr);
                        final DecodeException e = DecodeException.error(message);
                        StaticUtils.DEBUG_LOG.throwing("AttributeTypeSyntax", "valueIsAcceptable",
                                e);
                        throw e;
                    }
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or
                    // an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    SchemaUtils.readExtensions(reader);
                } else {
                    final LocalizableMessage message =
                            ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_TOKEN1.get(definition, tokenName);
                    final DecodeException e = DecodeException.error(message);
                    StaticUtils.DEBUG_LOG.throwing("AttributeTypeSyntax", "valueIsAcceptable", e);
                    throw e;
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
