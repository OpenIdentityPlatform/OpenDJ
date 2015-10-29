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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.forgerock.opendj.ldap.schema.SchemaUtils.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.SubstringReader;

/**
 * This class defines the objectIdentifierMatch matching rule defined in X.520
 * and referenced in RFC 2252. This expects to work on OIDs and will match
 * either an attribute/objectclass name or a numeric OID. NOTE: This matching
 * rule requires a schema to lookup object identifiers in the descriptor form.
 */
final class ObjectIdentifierEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    ObjectIdentifierEqualityMatchingRuleImpl() {
        super(EMR_OID_NAME);
    }

    static String resolveNames(final Schema schema, final String oidOrName) throws DecodeException {
        if (StaticUtils.isDigit(oidOrName.charAt(0))) {
            return oidOrName;
        }
        // Do a best effort attempt to normalize names to OIDs.
        final String lowerCaseName = StaticUtils.toLowerCase(oidOrName);
        try {
            final String oid = schema.getOIDForName(lowerCaseName);
            if (oid != null) {
                return oid;
            }
        } catch (UnknownSchemaElementException e) {
            throw DecodeException.error(e.getMessageObject(), e);
        }
        return lowerCaseName;
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) throws DecodeException {
        return normalizeAttributeValuePrivate(schema, value);
    }

    static ByteString normalizeAttributeValuePrivate(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final String definition = value.toString();
        final SubstringReader reader = new SubstringReader(definition);
        final String oid = readOID(reader, schema.getOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS));
        return ByteString.valueOfUtf8(resolveNames(schema, oid));
    }

    @Override
    String keyToHumanReadableString(ByteSequence key) {
        return key.toByteString().toString();
    }
}
