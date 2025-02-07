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
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.*;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.forgerock.util.Reject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;

/*
 An End Transaction Request is an LDAPMessage of CHOICE extendedReq
   where the requestName is 1.3.6.1.1.21.3 and the requestValue is
   present and contains a BER-encoded txnEndReq.

      txnEndReq ::= SEQUENCE {
           commit         BOOLEAN DEFAULT TRUE,
           identifier     OCTET STRING }

   A commit value of TRUE indicates a request to commit the transaction
   identified by the identifier.  A commit value of FALSE indicates a
   request to abort the identified transaction.
 */
public class EndTransactionExtendedRequest extends AbstractExtendedRequest<EndTransactionExtendedRequest, EndTransactionExtendedResult> {

    public static final String END_TRANSACTION_REQUEST_OID ="1.3.6.1.1.21.3";

    @Override
    public String getOID() {
        return END_TRANSACTION_REQUEST_OID;
    }

    @Override
    public ExtendedResultDecoder<EndTransactionExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    Boolean commit=true;
    public EndTransactionExtendedRequest setCommit(final Boolean commit) {
        this.commit = commit;
        return this;
    }

    String transactionID;
    public EndTransactionExtendedRequest setTransactionID(final String transactionID) {
        Reject.ifNull(transactionID);
        this.transactionID = transactionID;
        return this;
    }
    public boolean isCommit() {
        return commit;
    }

    public String getTransactionID() {
        return transactionID;
    }

    @Override
    public ByteString getValue() {
        Reject.ifNull(transactionID);
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();
            if (commit!=null) {
                writer.writeBoolean(commit);
            }
            writer.writeOctetString(transactionID);
            writer.writeEndSequence();
        } catch (final IOException ioe) {
            throw new RuntimeException(ioe);
        }
        return buffer.toByteString();
    }

    @Override
    public boolean hasValue() {
        return true;
    }

    private static final EndTransactionExtendedRequest.ResultDecoder RESULT_DECODER = new EndTransactionExtendedRequest.ResultDecoder();

    private static final class ResultDecoder extends  AbstractExtendedResultDecoder<EndTransactionExtendedResult> {
        @Override
        public EndTransactionExtendedResult newExtendedErrorResult(final ResultCode resultCode,final String matchedDN, final String diagnosticMessage) {
            if (!resultCode.isExceptional()) {
                throw new IllegalArgumentException("No response name and value for result code "+ resultCode.intValue());
            }
            return EndTransactionExtendedResult.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(diagnosticMessage);
        }

        /*
        txnEndRes ::= SEQUENCE {
           messageID MessageID OPTIONAL,
                -- msgid associated with non-success resultCode
           updatesControls SEQUENCE OF updateControls SEQUENCE {
                messageID MessageID,
                     -- msgid associated with controls
                controls  Controls
           } OPTIONAL
      }
         */
        @Override
        public EndTransactionExtendedResult decodeExtendedResult(final ExtendedResult result,final DecodeOptions options) throws DecodeException {
            if (result instanceof EndTransactionExtendedResult) {
                return (EndTransactionExtendedResult) result;
            }

            final ResultCode resultCode = result.getResultCode();
            final EndTransactionExtendedResult newResult =
                    EndTransactionExtendedResult.newResult(resultCode)
                            .setMatchedDN(result.getMatchedDN())
                            .setDiagnosticMessage(result.getDiagnosticMessage());

            final ByteString responseValue = result.getValue();
            if (!resultCode.isExceptional() && responseValue == null) {
                throw DecodeException.error(LocalizableMessage.raw("Empty response value"));
            }
            if (responseValue != null) {
                try {
                    final ASN1Reader reader = ASN1.getReader(responseValue);
                    if (reader.hasNextElement()) {
                        reader.readStartSequence();
                        if (reader.hasNextElement() && reader.peekType() == ASN1.UNIVERSAL_INTEGER_TYPE) {
                            newResult.setFailedMessageID(Math.toIntExact(reader.readInteger()));
                        } else if (reader.hasNextElement() && reader.peekType() == ASN1.UNIVERSAL_SEQUENCE_TYPE) {
                            reader.readStartSequence();
                            while (reader.hasNextElement() && reader.peekType() == ASN1.UNIVERSAL_SEQUENCE_TYPE) {
                                reader.readStartSequence();
                                final long messageId = reader.readInteger();
                                final List<Control> controls = new ArrayList<>();
                                reader.readStartSequence();
                                while (reader.hasNextElement() && reader.peekType() == ASN1.UNIVERSAL_OCTET_STRING_TYPE) {
                                    final ByteString controlEncoded = reader.readOctetString();
                                    //TODO decode Control
                                }
                                reader.readEndSequence();
                                //newResult.success(messageId, controls.toArray(new Control[]{}));
                                reader.readEndSequence();
                            }
                            reader.readEndSequence();
                        }
                        reader.readEndSequence();
                    }
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

