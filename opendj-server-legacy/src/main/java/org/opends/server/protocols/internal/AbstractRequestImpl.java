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
 * Portions copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.protocols.internal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.util.Reject;
import org.opends.server.types.Control;

/**
 * Abstract request implementation.
 *
 * @see org.forgerock.opendj.ldap.requests.AbstractRequestImpl
 */
abstract class AbstractRequestImpl {

    /**
     * To be removed
     *
     * @param controls
     *          the controls
     * @param oid
     *          the oid
     * @return a new control object
     * @see org.forgerock.opendj.ldap.requests.AbstractRequestImpl#getControl(
     *      org.forgerock.opendj.ldap.controls.ControlDecoder, org.forgerock.opendj.ldap.DecodeOptions)
     */
    static Control getControl(final List<Control> controls, final String oid) {
        // Avoid creating an iterator if possible.
        if (!controls.isEmpty()) {
            for (final Control control : controls) {
                if (control.getOID().equals(oid)) {
                    return control;
                }
            }
        }
        return null;
    }

    private final List<Control> controls = new LinkedList<>();

    /** Default constructor. */
    AbstractRequestImpl() {
        // No implementation required.
    }

    /**
     * To be removed
     *
     * @param control
     *          the control
     * @return the current object
     * @see org.forgerock.opendj.ldap.requests.AbstractRequestImpl#addControl(
     *      org.forgerock.opendj.ldap.controls.Control)
     */
    public AbstractRequestImpl addControl(final Control control) {
        Reject.ifNull(control);
        controls.add(control);
        return this;
    }

    /**
     * To be removed
     *
     * @param controls
     *          the controls
     * @return the current object
     * @see org.forgerock.opendj.ldap.requests.AbstractRequestImpl#addControl(
     *      org.forgerock.opendj.ldap.controls.Control)
     */
    public AbstractRequestImpl addControl(final Collection<Control> controls) {
        if (controls != null) {
            for (Control c : controls) {
                addControl(c);
            }
        }
        return this;
    }

    /**
     * To be removed
     *
     * @param oid
     *          the oid
     * @return the current object
     * @see org.forgerock.opendj.ldap.requests.AbstractRequestImpl#containsControl(String)
     */
    public boolean containsControl(final String oid) {
        return getControl(controls, oid) != null;
    }

    /**
     * To be removed
     *
     * @return the controls
     * @see org.forgerock.opendj.ldap.requests.AbstractRequestImpl#getControls()
     */
    public final List<Control> getControls() {
        return controls;
    }

    /** {@inheritDoc} */
    @Override
    public abstract String toString();

}
