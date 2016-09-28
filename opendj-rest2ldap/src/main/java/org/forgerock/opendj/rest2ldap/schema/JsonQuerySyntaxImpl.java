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

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_JSON_QUERY_PARSE_ERROR;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.SYNTAX_JSON_QUERY_DESCRIPTION;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.json.resource.QueryFilters;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SyntaxImpl;

/** This class implements the JSON query attribute syntax. */
final class JsonQuerySyntaxImpl implements SyntaxImpl {
    @Override
    public String getName() {
        return SYNTAX_JSON_QUERY_DESCRIPTION;
    }

    @Override
    public String getApproximateMatchingRule() {
        return null;
    }

    @Override
    public String getEqualityMatchingRule() {
        return null;
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
        try {
            QueryFilters.parse(value.toString());
            return true;
        } catch (Exception e) {
            invalidReason.append(ERR_JSON_QUERY_PARSE_ERROR.get(value));
            return false;
        }
    }
}
