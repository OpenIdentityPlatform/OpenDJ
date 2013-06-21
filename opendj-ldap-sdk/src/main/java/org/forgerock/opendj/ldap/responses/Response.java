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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import java.util.List;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * The base class of all Responses provides methods for querying and
 * manipulating the set of Controls included with a Response.
 */
public interface Response {

    /**
     * Adds the provided control to this response.
     *
     * @param control
     *            The control to be added.
     * @return This response.
     * @throws UnsupportedOperationException
     *             If this response does not permit controls to be added.
     * @throws NullPointerException
     *             If {@code control} was {@code null}.
     */
    Response addControl(Control control);

    /**
     * Returns {@code true} if this response contains the specified response
     * control.
     *
     * @param oid
     *            The numeric OID of the response control.
     * @return {@code true} if this response contains the specified response
     *         control.
     */
    boolean containsControl(String oid);

    /**
     * Decodes and returns the first control in this response having an OID
     * corresponding to the provided control decoder.
     *
     * @param <C>
     *            The type of control to be decoded and returned.
     * @param decoder
     *            The control decoder.
     * @param options
     *            The set of decode options which should be used when decoding
     *            the control.
     * @return The decoded control, or {@code null} if the control is not
     *         included with this response.
     * @throws DecodeException
     *             If the control could not be decoded because it was malformed
     *             in some way (e.g. the control value was missing, or its
     *             content could not be decoded).
     * @throws NullPointerException
     *             If {@code decoder} or {@code options} was {@code null}.
     */
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    /**
     * Returns a {@code List} containing the controls included with this
     * response. The returned {@code List} may be modified if permitted by this
     * response.
     *
     * @return A {@code List} containing the controls.
     */
    List<Control> getControls();

}
