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
 * Portions Copyright 2014 Manuel Gaupp
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_CERTIFICATE_EXACT_ASSERTION_NAME;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This class defines the Certificate Exact Assertion attribute syntax, which
 * contains components for matching X.509 certificates.
 */
final class CertificateExactAssertionSyntaxImpl extends AbstractSyntaxImpl {

    @Override
    public String getName() {
        return SYNTAX_CERTIFICATE_EXACT_ASSERTION_NAME;
    }

    @Override
    public boolean isBEREncodingRequired() {
        return false;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        // This method will never be called because this syntax is only used
        // within assertions.
        return true;
    }
}
