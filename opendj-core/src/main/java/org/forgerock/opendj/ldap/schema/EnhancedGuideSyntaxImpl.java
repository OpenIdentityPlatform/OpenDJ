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
 * Portions copyright 2011-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.util.StaticUtils.toLowerCase;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_OCTET_STRING_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_OCTET_STRING_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_ENHANCED_GUIDE_NAME;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.forgerock.opendj.ldap.schema.SchemaUtils.*;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.SubstringReader;

/**
 * This class implements the enhanced guide attribute syntax, which may be used
 * to provide criteria for generating search filters for entries of a given
 * objectclass.
 */
final class EnhancedGuideSyntaxImpl extends AbstractSyntaxImpl {
    @Override
    public String getEqualityMatchingRule() {
        return EMR_OCTET_STRING_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_ENHANCED_GUIDE_NAME;
    }

    @Override
    public String getOrderingMatchingRule() {
        return OMR_OCTET_STRING_OID;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
        final LocalizableMessageBuilder invalidReason) {
        // Get a lowercase string version of the provided value.
        final String valueStr = toLowerCase(value.toString());

        // Find the position of the first octothorpe. It should denote the
        // end of the objectclass.
        final int sharpPos = valueStr.indexOf('#');
        if (sharpPos < 0) {
            invalidReason.append(ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_SHARP1.get(valueStr));
            return false;
        }

        // Get the objectclass and see if it is a valid name or OID.
        final String ocName = valueStr.substring(0, sharpPos).trim();
        final int ocLength = ocName.length();
        if (ocLength == 0) {
            invalidReason.append(ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_OC1.get(valueStr));
            return false;
        }

        try {
            readOID(new SubstringReader(ocName.substring(ocLength)),
                schema.getOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS));
        } catch (final DecodeException de) {
            invalidReason.append(de.getMessageObject());
            return false;
        }

        // Find the last octothorpe and make sure it is followed by a valid
        // scope.
        final int lastSharpPos = valueStr.lastIndexOf('#');
        if (lastSharpPos == sharpPos) {
            invalidReason.append(ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_FINAL_SHARP.get(valueStr));
            return false;
        }

        final String scopeStr = valueStr.substring(lastSharpPos + 1).trim();
        if (!"baseobject".equals(scopeStr) && !"onelevel".equals(scopeStr)
            && !"wholesubtree".equals(scopeStr) && !"subordinatesubtree".equals(scopeStr)) {
            if (scopeStr.length() == 0) {
                invalidReason.append(ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_SCOPE.get(valueStr));
            } else {
                invalidReason.append(ERR_ATTR_SYNTAX_ENHANCEDGUIDE_INVALID_SCOPE.get(valueStr,
                    scopeStr));
            }
            return false;
        }

        // Everything between the two octothorpes must be the criteria. Make
        // sure it is valid.
        final String criteria = valueStr.substring(sharpPos + 1, lastSharpPos).trim();
        final int criteriaLength = criteria.length();
        if (criteriaLength == 0) {
            invalidReason.append(ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_CRITERIA.get(valueStr));
            return false;
        }

        return GuideSyntaxImpl.criteriaIsValid(schema, criteria, valueStr, invalidReason);
    }
}
