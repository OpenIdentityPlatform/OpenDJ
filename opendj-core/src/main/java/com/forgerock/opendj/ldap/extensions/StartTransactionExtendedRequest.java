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
 * Copyright 2025 3A Systems, LLC
 */
package com.forgerock.opendj.ldap.extensions;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AbstractExtendedRequest;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;

import java.io.IOException;

/*
 A Start Transaction Request is an LDAPMessage of CHOICE extendedReq
   where the requestName is 1.3.6.1.1.21.1 and the requestValue is
   absent.
 */
public class StartTransactionExtendedRequest extends AbstractExtendedRequest<StartTransactionExtendedRequest, StartTransactionExtendedResult> {
    public static final String START_TRANSACTION_REQUEST_OID ="1.3.6.1.1.21.1";

    @Override
    public String getOID() {
        return START_TRANSACTION_REQUEST_OID;
    }
    @Override
    public ExtendedResultDecoder<StartTransactionExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    @Override
    public ByteString getValue() {
        return null;
    }

    @Override
    public boolean hasValue() {
        return false;
    }

    private static final StartTransactionExtendedRequest.ResultDecoder RESULT_DECODER = new StartTransactionExtendedRequest.ResultDecoder();

    private static final class ResultDecoder extends  AbstractExtendedResultDecoder<StartTransactionExtendedResult> {
        @Override
        public StartTransactionExtendedResult newExtendedErrorResult(final ResultCode resultCode,final String matchedDN, final String diagnosticMessage) {
            if (!resultCode.isExceptional()) {
                throw new IllegalArgumentException("No response name and value for result code "+ resultCode.intValue());
            }
            return StartTransactionExtendedResult.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(diagnosticMessage);
        }

        @Override
        public StartTransactionExtendedResult decodeExtendedResult(final ExtendedResult result,final DecodeOptions options) throws DecodeException {
            if (result instanceof StartTransactionExtendedResult) {
                return (StartTransactionExtendedResult) result;
            }

            final ResultCode resultCode = result.getResultCode();
            final StartTransactionExtendedResult newResult =
                    StartTransactionExtendedResult.newResult(resultCode)
                            .setMatchedDN(result.getMatchedDN())
                            .setDiagnosticMessage(result.getDiagnosticMessage());

            final ByteString responseValue = result.getValue();
            if (!resultCode.isExceptional() && responseValue == null) {
                throw DecodeException.error(LocalizableMessage.raw("Empty response value"));
            }
            if (responseValue != null) {
                try {
                    final ASN1Reader reader = ASN1.getReader(responseValue);
                    newResult.setTransactionID(reader.readOctetStringAsString());
                } catch (final IOException e) {
                    throw DecodeException.error(LocalizableMessage.raw("Error decoding response value"), e);
                }
            }
            for (final Control control : result.getControls()) {
                newResult.addControl(control);
            }
            return newResult;
        }
    }
}
