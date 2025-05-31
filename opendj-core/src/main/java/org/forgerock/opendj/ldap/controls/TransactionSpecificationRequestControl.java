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
 * Copyright 2025 3A Systems,LLC.
 */
package org.forgerock.opendj.ldap.controls;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.util.Reject;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_TRANSACTION_ID_CONTROL_BAD_OID;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_TRANSACTION_ID_CONTROL_DECODE_NULL;

public class TransactionSpecificationRequestControl implements Control{

    public final static String OID="1.3.6.1.1.21.2";

    final String transactionId;
    public TransactionSpecificationRequestControl(String transactionId) {
        Reject.ifNull(transactionId);
        this.transactionId = transactionId;
    }

    @Override
    public String getOID() {
        return OID;
    }

    @Override
    public ByteString getValue() {
        return ByteString.valueOfUtf8(transactionId);
    }

    @Override
    public boolean hasValue() {
        return true;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    public static final ControlDecoder<TransactionSpecificationRequestControl> DECODER = new ControlDecoder<TransactionSpecificationRequestControl>() {
        @Override
        public TransactionSpecificationRequestControl decodeControl(final Control control, final DecodeOptions options)
                throws DecodeException {
            Reject.ifNull(control);

            if (control instanceof TransactionSpecificationRequestControl) {
                return (TransactionSpecificationRequestControl) control;
            }

            if (!control.getOID().equals(OID)) {
                throw DecodeException.error(ERR_TRANSACTION_ID_CONTROL_BAD_OID.get(control.getOID(), OID));
            }

            if (!control.hasValue()) {
                throw DecodeException.error(ERR_TRANSACTION_ID_CONTROL_DECODE_NULL.get());
            }

            return new TransactionSpecificationRequestControl(control.getValue().toString());
        }

        @Override
        public String getOID() {
            return OID;
        }
    };
}
