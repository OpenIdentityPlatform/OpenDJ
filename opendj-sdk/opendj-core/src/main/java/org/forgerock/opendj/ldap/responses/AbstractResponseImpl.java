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

package org.forgerock.opendj.ldap.responses;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.util.Reject;

/**
 * Modifiable response implementation.
 *
 * @param <S>
 *            The type of response.
 */
abstract class AbstractResponseImpl<S extends Response> implements Response {
    /** Used by unmodifiable implementations as well. */
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

    AbstractResponseImpl() {
        // No implementation required.
    }

    AbstractResponseImpl(final Response response) {
        Reject.ifNull(response);
        for (final Control control : response.getControls()) {
            // Create defensive copy.
            controls.add(GenericControl.newControl(control));
        }
    }

    @Override
    public final S addControl(final Control control) {
        Reject.ifNull(control);
        controls.add(control);
        return getThis();
    }

    @Override
    public boolean containsControl(final String oid) {
        return getControl(controls, oid) != null;
    }

    @Override
    public final <C extends Control> C getControl(final ControlDecoder<C> decoder,
            final DecodeOptions options) throws DecodeException {
        Reject.ifNull(decoder, options);
        final Control control = getControl(controls, decoder.getOID());
        return control != null ? decoder.decodeControl(control, options) : null;
    }

    @Override
    public final List<Control> getControls() {
        return controls;
    }

    @Override
    public abstract String toString();

    abstract S getThis();
}
