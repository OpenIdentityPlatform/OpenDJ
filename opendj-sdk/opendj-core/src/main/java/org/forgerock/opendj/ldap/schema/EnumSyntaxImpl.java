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
 * Portions copyright 2014-2016 ForgeRock AS.
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

    @Override
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

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
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
