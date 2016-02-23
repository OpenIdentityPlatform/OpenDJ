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
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_LDAPSYNTAX_REGEX_INVALID_VALUE;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.AMR_DOUBLE_METAPHONE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_IGNORE_OID;

import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

import org.forgerock.util.Reject;

/**
 * This class provides a regex mechanism where a new syntax and its
 * corresponding matching rules can be created on-the-fly. A regex syntax is an
 * LDAPSyntaxDescriptionSyntax with X-PATTERN extension.
 */
final class RegexSyntaxImpl extends AbstractSyntaxImpl {
    /** The Pattern associated with the regex. */
    private final Pattern pattern;

    RegexSyntaxImpl(final Pattern pattern) {
        Reject.ifNull(pattern);
        this.pattern = pattern;
    }

    @Override
    public String getApproximateMatchingRule() {
        return AMR_DOUBLE_METAPHONE_OID;
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_OID;
    }

    @Override
    public String getName() {
        return "Regex(" + pattern + ")";
    }

    @Override
    public String getOrderingMatchingRule() {
        return OMR_CASE_IGNORE_OID;
    }

    @Override
    public String getSubstringMatchingRule() {
        return SMR_CASE_IGNORE_OID;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        final String strValue = value.toString();
        final boolean matches = pattern.matcher(strValue).matches();
        if (!matches) {
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_LDAPSYNTAX_REGEX_INVALID_VALUE
                            .get(strValue, pattern.pattern());
            invalidReason.append(message);
        }
        return matches;
    }
}
