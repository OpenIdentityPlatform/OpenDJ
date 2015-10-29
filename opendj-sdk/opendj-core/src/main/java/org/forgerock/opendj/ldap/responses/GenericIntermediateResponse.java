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

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * A Generic Intermediate response provides a mechanism for communicating
 * unrecognized or unsupported Intermediate responses to the client.
 */
public interface GenericIntermediateResponse extends IntermediateResponse {

    @Override
    GenericIntermediateResponse addControl(Control control);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    @Override
    String getOID();

    @Override
    ByteString getValue();

    @Override
    boolean hasValue();

    /**
     * Sets the numeric OID, if any, associated with this intermediate response.
     *
     * @param oid
     *            The numeric OID associated with this intermediate response, or
     *            {@code null} if there is no value.
     * @return This generic intermediate response.
     * @throws UnsupportedOperationException
     *             If this intermediate response does not permit the response
     *             name to be set.
     */
    GenericIntermediateResponse setOID(String oid);

    /**
     * Sets the value, if any, associated with this intermediate response. Its
     * format is defined by the specification of this intermediate response.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param value
     *            The value associated with this intermediate response, or
     *            {@code null} if there is no value.
     * @return This generic intermediate response.
     * @throws UnsupportedOperationException
     *             If this intermediate response does not permit the response
     *             value to be set.
     */
    GenericIntermediateResponse setValue(Object value);

}
