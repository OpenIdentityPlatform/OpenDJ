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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import java.io.IOException;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Password modify extended result implementation.
 */
final class PasswordModifyExtendedResultImpl extends
        AbstractExtendedResult<PasswordModifyExtendedResult> implements
        PasswordModifyExtendedResult {
    /**
     * The ASN.1 element type that will be used to encode the genPasswd
     * component in a password modify extended response.
     */
    private static final byte TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD = (byte) 0x80;

    private byte[] password;

    PasswordModifyExtendedResultImpl(final PasswordModifyExtendedResult passwordModifyExtendedResult) {
        super(passwordModifyExtendedResult);
        this.password = passwordModifyExtendedResult.getGeneratedPassword();
    }

    /** Instantiation via factory. */
    PasswordModifyExtendedResultImpl(final ResultCode resultCode) {
        super(resultCode);
    }

    @Override
    public byte[] getGeneratedPassword() {
        return password;
    }

    @Override
    public String getOID() {
        // No response name defined.
        return null;
    }

    @Override
    public ByteString getValue() {
        if (password != null) {
            final ByteStringBuilder buffer = new ByteStringBuilder();
            final ASN1Writer writer = ASN1.getWriter(buffer);

            try {
                writer.writeStartSequence();
                writer.writeOctetString(TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD, password);
                writer.writeEndSequence();
            } catch (final IOException ioe) {
                // This should never happen unless there is a bug somewhere.
                throw new RuntimeException(ioe);
            }

            return buffer.toByteString();
        }
        return null;
    }

    @Override
    public boolean hasValue() {
        return password != null;
    }

    @Override
    public PasswordModifyExtendedResult setGeneratedPassword(final byte[] password) {
        this.password = password;
        return this;
    }

    @Override
    public PasswordModifyExtendedResult setGeneratedPassword(final char[] password) {
        this.password = (password != null) ? StaticUtils.getBytes(password) : null;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("PasswordModifyExtendedResponse(resultCode=");
        builder.append(getResultCode());
        builder.append(", matchedDN=");
        builder.append(getMatchedDN());
        builder.append(", diagnosticMessage=");
        builder.append(getDiagnosticMessage());
        builder.append(", referrals=");
        builder.append(getReferralURIs());
        if (password != null) {
            builder.append(", genPassword=");
            builder.append(ByteString.valueOfBytes(password));
        }
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
