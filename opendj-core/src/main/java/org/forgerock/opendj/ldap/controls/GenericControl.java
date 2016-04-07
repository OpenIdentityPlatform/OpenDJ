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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import org.forgerock.opendj.ldap.ByteString;

import org.forgerock.util.Reject;

/**
 * A generic control which can be used to represent arbitrary raw request and
 * response controls.
 */
public final class GenericControl implements Control {

    /**
     * Creates a new control having the same OID, criticality, and value as the
     * provided control.
     *
     * @param control
     *            The control to be copied.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code control} was {@code null}.
     */
    public static GenericControl newControl(final Control control) {
        Reject.ifNull(control);

        if (control instanceof GenericControl) {
            return (GenericControl) control;
        }

        return new GenericControl(control.getOID(), control.isCritical(), control.getValue());
    }

    /**
     * Creates a new non-critical control having the provided OID and no value.
     *
     * @param oid
     *            The numeric OID associated with this control.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code oid} was {@code null}.
     */
    public static GenericControl newControl(final String oid) {
        return new GenericControl(oid, false, null);
    }

    /**
     * Creates a new control having the provided OID and criticality, but no
     * value.
     *
     * @param oid
     *            The numeric OID associated with this control.
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code oid} was {@code null}.
     */
    public static GenericControl newControl(final String oid, final boolean isCritical) {
        return new GenericControl(oid, isCritical, null);
    }

    /**
     * Creates a new control having the provided OID, criticality, and value.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param oid
     *            The numeric OID associated with this control.
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param value
     *            The value associated with this control, or {@code null} if
     *            there is no value. Its format is defined by the specification
     *            of this control.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code oid} was {@code null}.
     */
    public static GenericControl newControl(final String oid, final boolean isCritical,
            final Object value) {
        return new GenericControl(oid, isCritical, (value == null) ? null : ByteString
                .valueOfObject(value));
    }

    private final String oid;

    private final boolean isCritical;

    private final ByteString value;

    /** Prevent direct instantiation. */
    private GenericControl(final String oid, final boolean isCritical, final ByteString value) {
        Reject.ifNull(oid);
        this.oid = oid;
        this.isCritical = isCritical;
        this.value = value;
    }

    @Override
    public String getOID() {
        return oid;
    }

    @Override
    public ByteString getValue() {
        return value;
    }

    @Override
    public boolean hasValue() {
        return value != null;
    }

    @Override
    public boolean isCritical() {
        return isCritical;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Control(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        if (value != null) {
            builder.append(", value=");
            builder.append(value.toHexPlusAsciiString(4));
        }
        builder.append(")");
        return builder.toString();
    }

}
