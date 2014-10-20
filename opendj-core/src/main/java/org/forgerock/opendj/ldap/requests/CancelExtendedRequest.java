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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;

/**
 * The cancel extended request as defined in RFC 3909. This operation is similar
 * to the abandon operation, except that it has a response and also requires the
 * abandoned operation to return a response indicating it was canceled. This
 * operation should be used instead of the abandon operation when the client
 * needs an indication of the outcome. This operation may be used to cancel both
 * interrogation and update operations.
 *
 * @see <a href="http://tools.ietf.org/html/rfc3909">RFC 3909 - Lightweight
 *      Directory Access Protocol (LDAP) Cancel Operation </a>
 */
public interface CancelExtendedRequest extends ExtendedRequest<ExtendedResult> {

    /**
     * A decoder which can be used to decode cancel extended operation requests.
     */
    ExtendedRequestDecoder<CancelExtendedRequest, ExtendedResult> DECODER =
            new CancelExtendedRequestImpl.RequestDecoder();

    /**
     * The OID for the cancel extended operation request.
     */
    String OID = "1.3.6.1.1.8";

    @Override
    CancelExtendedRequest addControl(Control control);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    @Override
    String getOID();

    /**
     * Returns the request ID of the request to be abandoned.
     *
     * @return The request ID of the request to be abandoned.
     */
    int getRequestID();

    @Override
    ExtendedResultDecoder<ExtendedResult> getResultDecoder();

    @Override
    ByteString getValue();

    @Override
    boolean hasValue();

    /**
     * Sets the request ID of the request to be abandoned.
     *
     * @param id
     *            The request ID of the request to be abandoned.
     * @return This abandon request.
     * @throws UnsupportedOperationException
     *             If this abandon request does not permit the request ID to be
     *             set.
     */
    CancelExtendedRequest setRequestID(int id);
}
