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
import org.forgerock.opendj.ldap.responses.AbstractExtendedResult;
import org.forgerock.util.Reject;

import java.io.IOException;

/*
    A Start Transaction Response is an LDAPMessage of CHOICE extendedRes
   sent in response to a Start Transaction Request.  Its responseName is
   absent.  When the resultCode is success (0), responseValue is present
   and contains a transaction identifier.  Otherwise, the responseValue
   is absent.
 */
public class StartTransactionExtendedResult extends AbstractExtendedResult<StartTransactionExtendedResult> {
    @Override
    public String getOID() {
        return StartTransactionExtendedRequest.START_TRANSACTION_REQUEST_OID;
    }

    private StartTransactionExtendedResult(final ResultCode resultCode) {
        super(resultCode);
    }

    public static StartTransactionExtendedResult newResult(final ResultCode resultCode) {
        Reject.ifNull(resultCode);
        return new StartTransactionExtendedResult(resultCode);
    }

    private String transactionID = null;

    public StartTransactionExtendedResult setTransactionID(final String transactionID) {
        this.transactionID = transactionID;
        return this;
    }

    public String getTransactionID() {
        return transactionID;
    }

    @Override
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeOctetString(transactionID);
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
        return "StartTransactionExtendedResult(resultCode=" +
                getResultCode() +
                ", matchedDN=" +
                getMatchedDN() +
                ", diagnosticMessage=" +
                getDiagnosticMessage() +
                ", referrals=" +
                getReferralURIs() +
                ", responseName=" +
                getOID() +
                ", transactionID=" +
                transactionID +
                ", controls=" +
                getControls() +
                ")";
    }
}
