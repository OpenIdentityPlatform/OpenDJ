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
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_INVALID_VALUE;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

import org.forgerock.util.Reject;

/**
 * This class is the ordering matching rule implementation for an enum syntax
 * implementation. The ordering is determined by the order of the entries in the
 * X-ENUM extension value.
 */
final class EnumOrderingMatchingRule extends AbstractOrderingMatchingRuleImpl {
    private final EnumSyntaxImpl syntax;

    EnumOrderingMatchingRule(final EnumSyntaxImpl syntax) {
        super(OMR_GENERIC_ENUM_NAME);
        Reject.ifNull(syntax);
        this.syntax = syntax;
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final int index = syntax.indexOf(value);
        if (index < 0) {
            throw DecodeException.error(WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_INVALID_VALUE.get(value
                    .toString(), syntax.getName()));
        }
        return ByteString.valueOfInt(index);
    }

}
