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
 */

package org.forgerock.opendj.ldap.controls;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;

/**
 * A factory interface for decoding a control as a control of specific type.
 *
 * @param <C>
 *            The type of control decoded by this control decoder.
 */
public interface ControlDecoder<C extends Control> {
    /**
     * Decodes the provided control as a {@code Control} of type {@code C}.
     *
     * @param control
     *            The control to be decoded.
     * @param options
     *            The set of decode options which should be used when decoding
     *            the control.
     * @return The decoded control.
     * @throws DecodeException
     *             If the control contained the wrong OID, it did not have a
     *             value, or if its value could not be decoded.
     */
    C decodeControl(Control control, DecodeOptions options) throws DecodeException;

    /**
     * Returns the numeric OID associated with this control decoder.
     *
     * @return The numeric OID associated with this control decoder.
     */
    String getOID();
}
