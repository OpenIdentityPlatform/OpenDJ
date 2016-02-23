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

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class implements the IA5 string attribute syntax, which is simply a set
 * of ASCII characters. By default, they will be treated in a case-insensitive
 * manner, and equality, ordering, substring, and approximate matching will be
 * allowed.
 */
final class IA5StringSyntaxImpl extends AbstractSyntaxImpl {
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
        return SYNTAX_IA5_STRING_NAME;
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
        // We will allow any value that does not contain any non-ASCII
        // characters. Empty values are acceptable as well.
        byte b;
        for (int i = 0; i < value.length(); i++) {
            b = value.byteAt(i);
            if ((b & 0x7F) != b) {

                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER.get(value.toString(), String
                                .valueOf(b));
                invalidReason.append(message);
                return false;
            }
        }

        return true;
    }
}
