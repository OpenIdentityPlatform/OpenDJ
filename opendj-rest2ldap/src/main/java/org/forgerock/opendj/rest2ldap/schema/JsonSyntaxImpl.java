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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.schema;

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_JSON_EMPTY_CONTENT;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_JSON_TRAILING_CONTENT;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.EMR_CASE_IGNORE_JSON_QUERY_OID;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.VALIDATION_POLICY;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy.DISABLED;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.SYNTAX_JSON_DESCRIPTION;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.jsonParsingException;

import java.io.IOException;
import java.io.InputStream;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SyntaxImpl;
import org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/** This class implements the JSON attribute syntax. */
final class JsonSyntaxImpl implements SyntaxImpl {
    @Override
    public String getName() {
        return SYNTAX_JSON_DESCRIPTION;
    }

    @Override
    public String getApproximateMatchingRule() {
        return null;
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_JSON_QUERY_OID;
    }

    @Override
    public String getOrderingMatchingRule() {
        return null;
    }

    @Override
    public String getSubstringMatchingRule() {
        return null;
    }

    @Override
    public boolean isBEREncodingRequired() {
        return false;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
                                     final LocalizableMessageBuilder invalidReason) {
        final ValidationPolicy validationPolicy = schema.getOption(VALIDATION_POLICY);
        if (validationPolicy == DISABLED) {
            return true;
        }
        try (final InputStream inputStream = value.asReader().asInputStream();
             final JsonParser parser = validationPolicy.getJsonFactory().createParser(inputStream)) {
            JsonToken jsonToken = parser.nextToken();
            if (jsonToken == null) {
                invalidReason.append(ERR_JSON_EMPTY_CONTENT.get());
                return false;
            }
            int depth = 0;
            do {
                switch (jsonToken) {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                default:
                    // Skip.
                }
                jsonToken = parser.nextToken();
            } while (depth > 0);

            if (jsonToken != null) {
                invalidReason.append(ERR_JSON_TRAILING_CONTENT.get());
                return false;
            }
            return true;
        } catch (IOException e) {
            invalidReason.append(jsonParsingException(e));
            return false;
        }
    }
}
