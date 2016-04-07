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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;

/**
 * A generic Extended request which should be used for unsupported extended
 * operations. Servers list the names of Extended requests they recognize in the
 * {@code supportedExtension} attribute in the root DSE. Where the name is not
 * recognized, the server returns
 * {@link org.forgerock.opendj.ldap.ResultCode#PROTOCOL_ERROR} (the server may
 * return this error in other cases).
 */
public interface GenericExtendedRequest extends ExtendedRequest<GenericExtendedResult> {
    /**
     * A decoder which can be used to decode generic extended operation
     * requests.
     */
    ExtendedRequestDecoder<GenericExtendedRequest, GenericExtendedResult> DECODER =
            new GenericExtendedRequestImpl.RequestDecoder();

    @Override
    GenericExtendedRequest addControl(Control control);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    @Override
    String getOID();

    @Override
    ExtendedResultDecoder<GenericExtendedResult> getResultDecoder();

    @Override
    ByteString getValue();

    @Override
    boolean hasValue();

    /**
     * Sets the numeric OID associated with this extended request.
     *
     * @param oid
     *            The numeric OID associated with this extended request.
     * @return This generic extended request.
     * @throws UnsupportedOperationException
     *             If this generic extended request does not permit the request
     *             name to be set.
     * @throws NullPointerException
     *             If {@code oid} was {@code null}.
     */
    GenericExtendedRequest setOID(String oid);

    /**
     * Sets the value, if any, associated with this extended request. Its format
     * is defined by the specification of this extended request.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param value
     *            TThe value associated with this extended request, or
     *            {@code null} if there is no value. Its format is defined by
     *            the specification of this control.
     * @return This generic extended request.
     * @throws UnsupportedOperationException
     *             If this generic extended request does not permit the request
     *             value to be set.
     */
    GenericExtendedRequest setValue(Object value);
}
