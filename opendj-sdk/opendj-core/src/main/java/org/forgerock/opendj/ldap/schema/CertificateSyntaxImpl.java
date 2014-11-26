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
 *      Portions Copyright 2014 Manuel Gaupp
 */

package org.forgerock.opendj.ldap.schema;

import java.io.IOException;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_SYNTAX_CERTIFICATE_INVALID_DER;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SYNTAX_CERTIFICATE_INVALID_VERSION;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SYNTAX_CERTIFICATE_NO_ELEMENT_EXPECTED;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SYNTAX_CERTIFICATE_NOTVALID;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SYNTAX_CERTIFICATE_ONLY_VALID_V23;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SYNTAX_CERTIFICATE_ONLY_VALID_V3;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CERTIFICATE_EXACT_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_OCTET_STRING_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_CERTIFICATE_NAME;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.forgerock.opendj.io.ASN1.*;

import com.forgerock.opendj.util.StaticUtils;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.DecodeException;



/**
 * This class implements the certificate attribute syntax. It is restricted to
 * accept only X.509 certificates.
 */
final class CertificateSyntaxImpl extends AbstractSyntaxImpl {
    /** {@inheritDoc} */
    @Override
    public String getEqualityMatchingRule() {
        return EMR_CERTIFICATE_EXACT_OID;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return SYNTAX_CERTIFICATE_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public String getOrderingMatchingRule() {
        return OMR_OCTET_STRING_OID;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isBEREncodingRequired() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isHumanReadable() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        // Skip validation if strict validation is disabled.
        if (schema.getOption(ALLOW_MALFORMED_CERTIFICATES)) {
            return true;
        }

        // Validate the ByteSequence against the definitions of X.509, clause 7
        ASN1Reader reader = ASN1.getReader(value);
        try {
            // Certificate SIGNED SEQUENCE
            reader.readStartSequence(UNIVERSAL_SEQUENCE_TYPE);

            // CertificateContent SEQUENCE
            reader.readStartSequence(UNIVERSAL_SEQUENCE_TYPE);

            // Optional Version
            long x509Version = 0;
            if (reader.hasNextElement() && reader.peekType() == (TYPE_MASK_CONTEXT | TYPE_MASK_CONSTRUCTED)) {
                reader.readStartExplicitTag((byte) (TYPE_MASK_CONTEXT | TYPE_MASK_CONSTRUCTED));

                x509Version = reader.readInteger(UNIVERSAL_INTEGER_TYPE);
                if (x509Version < 0 || x509Version > 2) {
                    // invalid Version specified
                    invalidReason.append(ERR_SYNTAX_CERTIFICATE_INVALID_VERSION.get(x509Version));
                    return false;
                }

                if (x509Version == 0) {
                    // DEFAULT values shall not be included in DER encoded
                    // SEQUENCE (X.690, 11.5)
                    invalidReason.append(ERR_SYNTAX_CERTIFICATE_INVALID_DER.get());
                    return false;
                }

                reader.readEndExplicitTag();
            }

            // serialNumber
            reader.skipElement(UNIVERSAL_INTEGER_TYPE);

            // signature AlgorithmIdentifier
            reader.skipElement(UNIVERSAL_SEQUENCE_TYPE);

            // issuer name (SEQUENCE as of X.501, 9.2)
            reader.skipElement(UNIVERSAL_SEQUENCE_TYPE);

            // validity (SEQUENCE)
            reader.skipElement(UNIVERSAL_SEQUENCE_TYPE);

            // subject name (SEQUENCE as of X.501, 9.2)
            reader.skipElement(UNIVERSAL_SEQUENCE_TYPE);

            // SubjectPublicKeyInfo (SEQUENCE)
            reader.skipElement(UNIVERSAL_SEQUENCE_TYPE);

            // OPTIONAL issuerUniqueIdentifier
            if (reader.hasNextElement() && reader.peekType() == (TYPE_MASK_CONTEXT + 1)) {
                if (x509Version < 1) {
                    // only valid in v2 and v3
                    invalidReason.append(ERR_SYNTAX_CERTIFICATE_ONLY_VALID_V23.get("issuerUniqueIdentifier"));
                    return false;
                }
                reader.skipElement();
            }

            // OPTIONAL subjectUniqueIdentifier
            if (reader.hasNextElement() && reader.peekType() == (TYPE_MASK_CONTEXT + 2)) {
                if (x509Version < 1) {
                    // only valid in v2 and v3
                    invalidReason.append(ERR_SYNTAX_CERTIFICATE_ONLY_VALID_V23.get("subjectUniqueIdentifier"));
                    return false;
                }
                reader.skipElement();
            }

            // OPTIONAL extensions
            if (reader.hasNextElement() && reader.peekType() == ((TYPE_MASK_CONTEXT | TYPE_MASK_CONSTRUCTED) + 3)) {
                if (x509Version < 2) {
                    // only valid in v3
                    invalidReason.append(ERR_SYNTAX_CERTIFICATE_ONLY_VALID_V3.get("extensions"));
                    return false;
                }

                reader.readStartExplicitTag((byte) ((TYPE_MASK_CONTEXT | TYPE_MASK_CONSTRUCTED) + 3));

                reader.skipElement(UNIVERSAL_SEQUENCE_TYPE);

                reader.readEndExplicitTag();
            }

            // There should not be any further ASN.1 elements within this SEQUENCE
            if (reader.hasNextElement()) {
                invalidReason.append(ERR_SYNTAX_CERTIFICATE_NO_ELEMENT_EXPECTED.get());
                return false;
            }
            reader.readEndSequence(); // End CertificateContent SEQUENCE

            // AlgorithmIdentifier SEQUENCE
            reader.skipElement(UNIVERSAL_SEQUENCE_TYPE);

            // ENCRYPTED HASH BIT STRING
            reader.skipElement(UNIVERSAL_BIT_STRING_TYPE);

            // There should not be any further ASN.1 elements within this SEQUENCE
            if (reader.hasNextElement()) {
                invalidReason.append(ERR_SYNTAX_CERTIFICATE_NO_ELEMENT_EXPECTED.get());
                return false;
            }
            reader.readEndSequence(); // End Certificate SEQUENCE

            // There should not be any further ASN.1 elements
            if (reader.hasNextElement()) {
                invalidReason.append(ERR_SYNTAX_CERTIFICATE_NO_ELEMENT_EXPECTED.get());
                return false;
            }
            // End of the certificate
        } catch (DecodeException de) {
            invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get(de));
            return false;
        } catch (IOException e) {
            invalidReason.append(StaticUtils.getExceptionMessage(e));
            return false;
        }

        // The basic structure of the value is an X.509 certificate
        return true;
    }
}
