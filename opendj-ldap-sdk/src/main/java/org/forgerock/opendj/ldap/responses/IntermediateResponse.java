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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * An Intermediate response provides a general mechanism for defining
 * single-request/multiple-response operations. This response is intended to be
 * used in conjunction with the Extended operation to define new
 * single-request/multiple-response operations or in conjunction with a control
 * when extending existing operations in a way that requires them to return
 * Intermediate response information.
 * <p>
 * An Intermediate response may convey an optional response name and value.
 * These can be retrieved using the {@link #getOID} and {@link #getValue}
 * methods respectively.
 *
 * @see <a href="http://tools.ietf.org/html/rfc3771">RFC 3771 - The Lightweight
 *      Directory Access Protocol (LDAP) Intermediate Response Message</a>
 */
public interface IntermediateResponse extends Response {

    @Override
    IntermediateResponse addControl(Control control);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the numeric OID, if any, associated with this intermediate
     * response.
     *
     * @return The numeric OID associated with this intermediate response, or
     *         {@code null} if there is no OID.
     */
    String getOID();

    /**
     * Returns the value, if any, associated with this intermediate response.
     * Its format is defined by the specification of this intermediate response.
     *
     * @return The value associated with this intermediate response, or
     *         {@code null} if there is no value.
     */
    ByteString getValue();

    /**
     * Returns {@code true} if this intermediate response has a value. In some
     * circumstances it may be useful to determine if an intermediate response
     * has a value, without actually calculating the value and incurring any
     * performance costs.
     *
     * @return {@code true} if this intermediate response has a value, or
     *         {@code false} if there is no value.
     */
    boolean hasValue();

}
