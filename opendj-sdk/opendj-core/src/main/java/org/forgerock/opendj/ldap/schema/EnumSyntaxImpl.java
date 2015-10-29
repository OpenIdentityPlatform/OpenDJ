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
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.util.StringPrepProfile.CASE_FOLD;
import static com.forgerock.opendj.util.StringPrepProfile.TRIM;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_INVALID_VALUE;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.AMR_DOUBLE_METAPHONE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_OID_GENERIC_ENUM;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_IGNORE_OID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Reject;

/**
 * This class provides an enumeration-based mechanism where a new syntax and its
 * corresponding matching rules can be created on-the-fly. An enum syntax is an
 * LDAPSyntaxDescriptionSyntax with X-ENUM extension.
 */
final class EnumSyntaxImpl extends AbstractSyntaxImpl {
    private final String oid;
    /** Set of read-only enum entries. */
    private final List<String> entries;

    EnumSyntaxImpl(final String oid, final List<String> entries) {
        Reject.ifNull(oid, entries);
        this.oid = oid;
        final List<String> entryStrings = new ArrayList<>(entries.size());

        for (final String entry : entries) {
            final String normalized = normalize(ByteString.valueOfUtf8(entry));
            if (!entryStrings.contains(normalized)) {
                entryStrings.add(normalized);
            }
        }
        this.entries = Collections.unmodifiableList(entryStrings);
    }

    @Override
    public String getApproximateMatchingRule() {
        return AMR_DOUBLE_METAPHONE_OID;
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_OID;
    }

    public String getName() {
        return oid;
    }

    @Override
    public String getOrderingMatchingRule() {
        return OMR_OID_GENERIC_ENUM + "." + oid;
    }

    @Override
    public String getSubstringMatchingRule() {
        return SMR_CASE_IGNORE_OID;
    }

    public int indexOf(final ByteSequence value) {
        return entries.indexOf(normalize(value));
    }

    public boolean isHumanReadable() {
        return true;
    }

    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        // The value is acceptable if it belongs to the set.
        final boolean isAllowed = entries.contains(normalize(value));

        if (!isAllowed) {
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_INVALID_VALUE.get(value.toString(), oid);
            invalidReason.append(message);
        }

        return isAllowed;
    }

    private String normalize(final ByteSequence value) {
        return SchemaUtils.normalizeStringAttributeValue(value, TRIM, CASE_FOLD).toString();
    }
}
