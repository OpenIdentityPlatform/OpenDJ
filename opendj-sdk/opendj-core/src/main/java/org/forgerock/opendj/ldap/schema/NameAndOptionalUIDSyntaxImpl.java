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

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAMEANDUID_ILLEGAL_BINARY_DIGIT;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_NAMEANDUID_INVALID_DN;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_UNIQUE_MEMBER_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SMR_CASE_IGNORE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_NAME_AND_OPTIONAL_UID_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.DN;

/**
 * This class implements the name and optional UID attribute syntax, which holds
 * values consisting of a DN, optionally followed by an octothorpe (#) and a bit
 * string value.
 */
final class NameAndOptionalUIDSyntaxImpl extends AbstractSyntaxImpl {

    @Override
    public String getEqualityMatchingRule() {
        return EMR_UNIQUE_MEMBER_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_NAME_AND_OPTIONAL_UID_NAME;
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
        final String valueString = value.toString().trim();
        final int valueLength = valueString.length();

        // See if the value contains the "optional uid" portion. If we think
        // it does, then mark its location.
        int dnEndPos = valueLength;
        int sharpPos = -1;
        if (valueString.endsWith("'B") || valueString.endsWith("'b")) {
            sharpPos = valueString.lastIndexOf("#'");
            if (sharpPos > 0) {
                dnEndPos = sharpPos;
            }
        }

        // Take the DN portion of the string and try to normalize it.
        try {
            DN.valueOf(valueString.substring(0, dnEndPos), schema);
        } catch (final LocalizedIllegalArgumentException e) {
            // We couldn't normalize the DN for some reason. The value cannot
            // be acceptable.
            invalidReason.append(ERR_ATTR_SYNTAX_NAMEANDUID_INVALID_DN.get(valueString, e
                    .getMessageObject()));
            return false;
        }

        // If there is an "optional uid", then normalize it and make sure it
        // only contains valid binary digits.
        if (sharpPos > 0) {
            final int endPos = valueLength - 2;
            for (int i = sharpPos + 2; i < endPos; i++) {
                final char c = valueString.charAt(i);
                if (c != '0' && c != '1') {
                    invalidReason.append(ERR_ATTR_SYNTAX_NAMEANDUID_ILLEGAL_BINARY_DIGIT.get(
                            valueString, String.valueOf(c), i));
                    return false;
                }
            }
        }

        // If we've gotten here, then the value is acceptable.
        return true;
    }
}
