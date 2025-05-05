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

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResult;
import org.forgerock.util.Reject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/*
   An End Transaction Response is an LDAPMessage sent in response to a
   End Transaction Request.  Its response name is absent.  The
   responseValue when present contains a BER-encoded txnEndRes.

      txnEndRes ::= SEQUENCE {
           messageID MessageID OPTIONAL,
                -- msgid associated with non-success resultCode
           updatesControls SEQUENCE OF updateControls SEQUENCE {
                messageID MessageID,
                     -- msgid associated with controls
                controls  Controls
           } OPTIONAL
      }
      -- where MessageID and Controls are as specified in RFC 4511

   The txnEndRes.messageID provides the message id of the update request
   associated with a non-success response.  txnEndRes.messageID is
   absent when resultCode of the End Transaction Response is success

   The txnEndRes.updatesControls provides a facility for returning
   response controls that normally (i.e., in the absence of
   transactions) would be returned in an update response.  The
   updateControls.messageID provides the message id of the update
   request associated with the response controls provided in
   updateControls.controls.

   The txnEndRes.updatesControls is absent when there are no update
   response controls to return.

   If both txnEndRes.messageID and txnEndRes.updatesControl are absent,
   the responseValue of the End Transaction Response is absent.
 */
public class EndTransactionExtendedResult extends AbstractExtendedResult<EndTransactionExtendedResult> {
    @Override
    public String getOID() {
        return EndTransactionExtendedRequest.END_TRANSACTION_REQUEST_OID;
    }

    private EndTransactionExtendedResult(final ResultCode resultCode) {
        super(resultCode);
    }

    public static EndTransactionExtendedResult newResult(final ResultCode resultCode) {
        Reject.ifNull(resultCode);
        return new EndTransactionExtendedResult(resultCode);
    }

    // The message ID for the operation that failed, if applicable.
    Integer failedOpMessageID=null;

    public EndTransactionExtendedResult setFailedMessageID(final Integer failedOpMessageID) {
        Reject.ifNull(failedOpMessageID);
        this.failedOpMessageID = failedOpMessageID;
        return this;
    }

    // A mapping of the response controls for the operations performed as part of
    // the transaction.
    Map<Integer, List<Control>> opResponseControls= new TreeMap<>();

    public EndTransactionExtendedResult success(Integer messageID, List<Control> responses) {
        Reject.ifNull(messageID);
        Reject.ifNull(responses);
        opResponseControls.put(messageID,responses);
        return this;
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
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            if (failedOpMessageID!=null || (opResponseControls!=null && !opResponseControls.isEmpty()) ) {
                writer.writeStartSequence();
                if (failedOpMessageID != null) {
                    writer.writeInteger(failedOpMessageID);
                }
                if (opResponseControls != null && !opResponseControls.isEmpty()) {
                    writer.writeStartSequence();
                    for (Map.Entry<Integer, List<Control>> entry : opResponseControls.entrySet()) {
                        writer.writeStartSequence();
                            writer.writeInteger(entry.getKey());
                            writer.writeStartSequence();
                                for (Control control : entry.getValue()) {
                                   writer.writeOctetString(control.getValue());
                                }
                            writer.writeEndSequence();
                        writer.writeEndSequence();
                    }
                    writer.writeEndSequence();
                }
                writer.writeEndSequence();
            }
        } catch (final IOException ioe) {
            throw new RuntimeException(ioe);
        }
        return buffer.toByteString();
    }

    @Override
    public boolean hasValue() {
        return true;
    }

    @Override
    public String toString() {
        return "EndTransactionExtendedResult(resultCode=" +
                getResultCode() +
                ", matchedDN=" +
                getMatchedDN() +
                ", diagnosticMessage=" +
                getDiagnosticMessage() +
                ", referrals=" +
                getReferralURIs() +
                ", responseName=" +
                getOID() +
                ", failedOpMessageID=" +
                failedOpMessageID +
                ", opResponseControls=" +
                opResponseControls +
                ", controls=" +
                getControls() +
                ")";
    }
}