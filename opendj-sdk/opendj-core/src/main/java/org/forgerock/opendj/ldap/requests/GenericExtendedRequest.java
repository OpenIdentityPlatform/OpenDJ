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
 *      Portions copyright 2012-2015 ForgeRock AS.
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
