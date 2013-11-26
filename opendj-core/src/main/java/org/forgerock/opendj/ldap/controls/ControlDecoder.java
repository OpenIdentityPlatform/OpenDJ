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
