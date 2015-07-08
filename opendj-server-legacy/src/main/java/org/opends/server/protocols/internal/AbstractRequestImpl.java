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
