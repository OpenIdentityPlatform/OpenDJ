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

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Password modify extended request implementation.
 */
final class PasswordModifyExtendedRequestImpl extends
        AbstractExtendedRequest<PasswordModifyExtendedRequest, PasswordModifyExtendedResult>
        implements PasswordModifyExtendedRequest {
    static final class RequestDecoder implements
            ExtendedRequestDecoder<PasswordModifyExtendedRequest, PasswordModifyExtendedResult> {
        @Override
        public PasswordModifyExtendedRequest decodeExtendedRequest(
                final ExtendedRequest<?> request, final DecodeOptions options)
                throws DecodeException {
            final PasswordModifyExtendedRequest newRequest =
                    new PasswordModifyExtendedRequestImpl();
            if (request.getValue() != null) {
                try {
                    final ASN1Reader reader = ASN1.getReader(request.getValue());
                    reader.readStartSequence();
                    if (reader.hasNextElement()
                            && (reader.peekType() == TYPE_PASSWORD_MODIFY_USER_ID)) {
                        newRequest.setUserIdentity(reader.readOctetStringAsString());
                    }
                    if (reader.hasNextElement()
                            && (reader.peekType() == TYPE_PASSWORD_MODIFY_OLD_PASSWORD)) {
                        newRequest.setOldPassword(reader.readOctetString().toByteArray());
                    }
                    if (reader.hasNextElement()
                            && (reader.peekType() == TYPE_PASSWORD_MODIFY_NEW_PASSWORD)) {
                        newRequest.setNewPassword(reader.readOctetString().toByteArray());
                    }
                    reader.readEndSequence();
                } catch (final IOException e) {
                    final LocalizableMessage message =
                            ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST.get(getExceptionMessage(e));
                    throw DecodeException.error(message, e);
                }
            }

            for (final Control control : request.getControls()) {
                newRequest.addControl(control);
            }

            return newRequest;
        }
    }

    private static final class ResultDecoder extends
            AbstractExtendedResultDecoder<PasswordModifyExtendedResult> {
        @Override
        public PasswordModifyExtendedResult decodeExtendedResult(final ExtendedResult result,
                final DecodeOptions options) throws DecodeException {
            if (result instanceof PasswordModifyExtendedResult) {
                return (PasswordModifyExtendedResult) result;
            } else {
                final ResultCode resultCode = result.getResultCode();

                final PasswordModifyExtendedResult newResult =
                        Responses.newPasswordModifyExtendedResult(resultCode).setMatchedDN(
                                result.getMatchedDN()).setDiagnosticMessage(
                                result.getDiagnosticMessage());

                // TODO: Should we check to make sure OID is null?
                final ByteString responseValue = result.getValue();
                if (resultCode == ResultCode.SUCCESS && responseValue != null) {
                    try {
                        final ASN1Reader asn1Reader = ASN1.getReader(responseValue);
                        asn1Reader.readStartSequence();
                        if (asn1Reader.peekType() == TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD) {
                            newResult.setGeneratedPassword(asn1Reader.readOctetString()
                                    .toByteArray());
                        }
                        asn1Reader.readEndSequence();
                    } catch (final IOException e) {
                        final LocalizableMessage message =
                                ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST.get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }
                }

                for (final Control control : result.getControls()) {
                    newResult.addControl(control);
                }

                return newResult;
            }
        }

        @Override
        public PasswordModifyExtendedResult newExtendedErrorResult(final ResultCode resultCode,
                final String matchedDN, final String diagnosticMessage) {
            return Responses.newPasswordModifyExtendedResult(resultCode).setMatchedDN(matchedDN)
                    .setDiagnosticMessage(diagnosticMessage);
        }
    }

    private static final ExtendedResultDecoder<PasswordModifyExtendedResult> RESULT_DECODER =
            new ResultDecoder();

    /**
     * The ASN.1 element type that will be used to encode the genPasswd
     * component in a password modify extended response.
     */
    private static final byte TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD = (byte) 0x80;

    /**
     * The ASN.1 element type that will be used to encode the newPasswd
     * component in a password modify extended request.
     */
    private static final byte TYPE_PASSWORD_MODIFY_NEW_PASSWORD = (byte) 0x82;

    /**
     * The ASN.1 element type that will be used to encode the oldPasswd
     * component in a password modify extended request.
     */
    private static final byte TYPE_PASSWORD_MODIFY_OLD_PASSWORD = (byte) 0x81;

    /**
     * The ASN.1 element type that will be used to encode the userIdentity
     * component in a password modify extended request.
     */
    private static final byte TYPE_PASSWORD_MODIFY_USER_ID = (byte) 0x80;
    private byte[] newPassword;
    private byte[] oldPassword;

    private ByteString userIdentity;

    /** Instantiation via factory. */
    PasswordModifyExtendedRequestImpl() {

    }

    PasswordModifyExtendedRequestImpl(
            final PasswordModifyExtendedRequest passwordModifyExtendedRequest) {
        super(passwordModifyExtendedRequest);
        this.userIdentity = passwordModifyExtendedRequest.getUserIdentity();
        this.oldPassword = passwordModifyExtendedRequest.getOldPassword();
        this.newPassword = passwordModifyExtendedRequest.getNewPassword();
    }

    @Override
    public byte[] getNewPassword() {
        return newPassword;
    }

    @Override
    public String getOID() {
        return OID;
    }

    @Override
    public byte[] getOldPassword() {
        return oldPassword;
    }

    @Override
    public ExtendedResultDecoder<PasswordModifyExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    @Override
    public ByteString getUserIdentity() {
        return userIdentity;
    }

    @Override
    public String getUserIdentityAsString() {
        return userIdentity != null ? userIdentity.toString() : null;
    }

    @Override
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);

        try {
            writer.writeStartSequence();
            if (userIdentity != null) {
                writer.writeOctetString(TYPE_PASSWORD_MODIFY_USER_ID, userIdentity);
            }
            if (oldPassword != null) {
                writer.writeOctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD, oldPassword);
            }
            if (newPassword != null) {
                writer.writeOctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD, newPassword);
            }
            writer.writeEndSequence();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }

        return buffer.toByteString();
    }

    @Override
    public boolean hasValue() {
        return true;
    }

    @Override
    public PasswordModifyExtendedRequest setNewPassword(final byte[] newPassword) {
        this.newPassword = newPassword;
        return this;
    }

    @Override
    public PasswordModifyExtendedRequest setNewPassword(final char[] newPassword) {
        this.newPassword = (newPassword != null) ? StaticUtils.getBytes(newPassword) : null;
        return this;
    }

    @Override
    public PasswordModifyExtendedRequest setOldPassword(final byte[] oldPassword) {
        this.oldPassword = oldPassword;
        return this;
    }

    @Override
    public PasswordModifyExtendedRequest setOldPassword(final char[] oldPassword) {
        this.oldPassword = (oldPassword != null) ? StaticUtils.getBytes(oldPassword) : null;
        return this;
    }

    @Override
    public PasswordModifyExtendedRequest setUserIdentity(final Object userIdentity) {
        this.userIdentity = (userIdentity != null) ? ByteString.valueOfObject(userIdentity) : null;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("PasswordModifyExtendedRequest(requestName=");
        builder.append(getOID());
        builder.append(", userIdentity=");
        builder.append(userIdentity);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
