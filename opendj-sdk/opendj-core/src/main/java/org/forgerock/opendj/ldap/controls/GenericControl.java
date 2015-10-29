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

    /** {@inheritDoc} */
    public String getOID() {
        return oid;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        return value;
    }

    /** {@inheritDoc} */
    public boolean hasValue() {
        return value != null;
    }

    /** {@inheritDoc} */
    public boolean isCritical() {
        return isCritical;
    }

    /** {@inheritDoc} */
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
