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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_OCTET_STRING_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_OCTET_STRING_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_CERTLIST_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class implements the certificate list attribute syntax. This should be
 * restricted to holding only X.509 certificate lists, but we will accept any
 * set of bytes. It will be treated much like the octet string attribute syntax.
 */
final class CertificateListSyntaxImpl extends AbstractSyntaxImpl {

    @Override
    public String getEqualityMatchingRule() {
        return EMR_OCTET_STRING_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_CERTLIST_NAME;
    }

    @Override
    public String getOrderingMatchingRule() {
        return OMR_OCTET_STRING_OID;
    }

    @Override
    public boolean isBEREncodingRequired() {
        return true;
    }

    @Override
    public boolean isHumanReadable() {
        return false;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        // All values will be acceptable for the certificate list syntax.
        return true;
    }
}
