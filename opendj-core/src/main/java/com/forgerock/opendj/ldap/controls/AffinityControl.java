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
 * Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.util.concurrent.ThreadLocalRandom;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.util.Reject;

/**
 * Control that provides a value for affinity.
 * <p>
 * As an example, this control can be used for connection affinity when using a load-balancer (
 * {@link org.forgerock.opendj.ldap.Connections Connections}).
 *
 * @see org.forgerock.opendj.ldap.Connections#newLeastRequestsLoadBalancer(java.util.Collection,
 *      org.forgerock.util.Options)
 */
public final class AffinityControl implements Control {

    /** OID for this control. */
    public static final String OID = "1.3.6.1.4.1.36733.2.1.5.2";

    /** A decoder which can be used for decoding the affinity control. */
    public static final ControlDecoder<AffinityControl> DECODER =
            new ControlDecoder<AffinityControl>() {

                @Override
                public AffinityControl decodeControl(
                        final Control control, final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof AffinityControl) {
                        return (AffinityControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        throw DecodeException.error(ERR_CONNECTION_AFFINITY_CONTROL_BAD_OID.get(control.getOID(), OID));
                    }

                    if (!control.hasValue()) {
                        // The control must always have a value.
                        throw DecodeException.error(ERR_CONNECTION_AFFINITY_CONTROL_DECODE_NULL.get());
                    }

                    return new AffinityControl(control.getValue(), control.isCritical());
                }

                @Override
                public String getOID() {
                    return OID;
                }
            };

    /** The affinity value is an arbitrary string. */
    private final ByteString affinityValue;

    /** Indicates if this control is critical. */
    private final boolean isCritical;

    /**
     * Creates a new affinity control with provided value.
     *
     * @param affinityValue
     *            The affinity value to use
     * @param isCritical
     *            Indicates if this control is critical
     * @return The new control.
     * @throws NullPointerException
     *             If {@code transactionId} was {@code null}.
     */
    public static AffinityControl newControl(final ByteString affinityValue, final boolean isCritical) {
        Reject.ifNull(affinityValue);
        return new AffinityControl(affinityValue, isCritical);
    }

    /**
     * Creates a new affinity control with a randomly generated affinity value.
     *
     * @param isCritical
     *          Indicates if this control is critical
     * @return The new control.
     * @throws NullPointerException
     *             If {@code transactionId} was {@code null}.
     */
    public static AffinityControl newControl(final boolean isCritical) {
        byte[] randomValue = new byte[5];
        ThreadLocalRandom.current().nextBytes(randomValue);
        return new AffinityControl(ByteString.valueOfBytes(randomValue), isCritical);
    }

    private AffinityControl(final ByteString affinityValue, final boolean isCritical) {
        this.affinityValue = affinityValue;
        this.isCritical = isCritical;
    }

    /**
     * Returns the affinity value.
     *
     * @return The affinity value.
     */
    public ByteString getAffinityValue() {
        return affinityValue;
    }

    @Override
    public String getOID() {
        return OID;
    }

    @Override
    public ByteString getValue() {
        return affinityValue;
    }

    @Override
    public boolean hasValue() {
        return true;
    }

    @Override
    public boolean isCritical() {
        return isCritical;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AffinityControl(oid=");
        builder.append(getOID());
        builder.append(", affinityValue(hex)=");
        builder.append(affinityValue.toHexString());
        builder.append(", isCritical=");
        builder.append(isCritical);
        builder.append(")");
        return builder.toString();
    }
}
