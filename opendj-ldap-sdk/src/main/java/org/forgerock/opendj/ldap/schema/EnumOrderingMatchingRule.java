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

import static org.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_INVALID_VALUE;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.Validator;

/**
 * This class is the ordering matching rule implementation for an enum syntax
 * implmentation. The ordering is determined by the order of the entries in the
 * X-ENUM extension value.
 */
final class EnumOrderingMatchingRule extends AbstractOrderingMatchingRuleImpl {
    private final EnumSyntaxImpl syntax;

    EnumOrderingMatchingRule(final EnumSyntaxImpl syntax) {
        Validator.ensureNotNull(syntax);
        this.syntax = syntax;
    }

    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final int index = syntax.indexOf(value);
        if (index < 0) {
            throw DecodeException.error(WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_INVALID_VALUE.get(value
                    .toString(), syntax.getName()));
        }
        return ByteString.valueOf(index);
    }

}
