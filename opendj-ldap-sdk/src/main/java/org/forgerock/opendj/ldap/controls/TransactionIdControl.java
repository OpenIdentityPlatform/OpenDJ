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
 *      Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import static org.forgerock.opendj.ldap.CoreMessages.*;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;

import com.forgerock.opendj.util.Validator;

/**
 * Control to provide a transaction id.
 */
public final class TransactionIdControl implements Control {

    /** OID for this control. */
    public static final String OID = "1.3.6.1.4.1.36733.2.1.5.1";

    /**
     * A decoder which can be used for decoding the simple paged results control.
     */
    public static final ControlDecoder<TransactionIdControl> DECODER = new ControlDecoder<TransactionIdControl>() {

        @Override
        public TransactionIdControl decodeControl(final Control control, final DecodeOptions options)
                throws DecodeException {
            Validator.ensureNotNull(control);

            if (control instanceof TransactionIdControl) {
                return (TransactionIdControl) control;
            }

            if (!control.getOID().equals(OID)) {
                throw DecodeException.error(ERR_TRANSACTION_ID_CONTROL_BAD_OID.get(control.getOID(), OID));
            }

            if (!control.hasValue()) {
                // The control must always have a value.
                throw DecodeException.error(ERR_TRANSACTION_ID_CONTROL_DECODE_NULL.get());
            }

            return new TransactionIdControl(control.getValue().toString());
        }

        @Override
        public String getOID() {
            return OID;
        }
    };

    /**
     * Creates a new transactionId control.
     *
     * @param transactionId
     *            The transaction id to provide through this control.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code transactionId} was {@code null}.
     */
    public static TransactionIdControl newControl(final String transactionId) {
        Validator.ensureNotNull(transactionId);
        return new TransactionIdControl(transactionId);
    }

    /**
     * The control value transactionId element.
     */
    private final String transactionId;

    private TransactionIdControl(final String transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Returns the transaction id.
     *
     * @return The transaction id.
     */
    public String getTransactionId() {
        return transactionId;
    }

    /** {@inheritDoc} */
    @Override
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    @Override
    public ByteString getValue() {
        return ByteString.valueOf(transactionId);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasValue() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCritical() {
        // This control is never critical.
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("TransactionIdControl(oid=");
        builder.append(getOID());
        builder.append(", transactionId=");
        builder.append(transactionId);
        builder.append(")");
        return builder.toString();
    }
}
