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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class implements the uniqueMemberMatch matching rule defined in X.520
 * and referenced in RFC 4517. It is based on the name and optional UID syntax,
 * and will compare values with a distinguished name and optional bit string (uid)
 * suffix.
 */
final class UniqueMemberEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    UniqueMemberEqualityMatchingRuleImpl() {
        super(EMR_UNIQUE_MEMBER_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) throws DecodeException {
        // Separate value into normalized DN and "optional uid" portion.
        final String stringValue = value.toString().trim();
        int dnEndPosition = stringValue.length();
        String optionalUid = "";
        int sharpPosition = -1;
        if (stringValue.endsWith("'B") || stringValue.endsWith("'b")) {
            sharpPosition = stringValue.lastIndexOf("#'");
            if (sharpPosition > 0) {
                dnEndPosition = sharpPosition;
                optionalUid = stringValue.substring(sharpPosition);
            }
        }
        try {
            DN dn = DN.valueOf(stringValue.substring(0, dnEndPosition), schema.asNonStrictSchema());
            return new ByteStringBuilder()
                .appendBytes(dn.toNormalizedByteString())
                .appendUtf8(optionalUid).toByteString();
        } catch (final LocalizedIllegalArgumentException e) {
            throw DecodeException.error(e.getMessageObject());
        }
    }
}
