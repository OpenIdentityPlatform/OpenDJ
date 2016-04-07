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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.spi;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.util.promise.PromiseImpl;

/**
 * Extended result promise implementation.
 *
 * @param <S>
 *         The type of result returned by this promise.
 */
public final class ExtendedResultLdapPromiseImpl<S extends ExtendedResult> extends
        ResultLdapPromiseImpl<ExtendedRequest<S>, S> {
    ExtendedResultLdapPromiseImpl(
            final PromiseImpl<S, LdapException> impl,
            final int requestID,
            final ExtendedRequest<S> request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        super(impl, requestID, request, intermediateResponseHandler);
    }

    /**
     * Decode an extended result.
     *
     * @param result
     *         Extended result to decode.
     * @param options
     *         Decoding options.
     * @return The decoded extended result.
     * @throws DecodeException
     *         If a problem occurs during decoding.
     */
    public S decodeResult(final ExtendedResult result, final DecodeOptions options) throws DecodeException {
        return getRequest().getResultDecoder().decodeExtendedResult(result, options);
    }

    @Override
    public boolean isBindOrStartTLS() {
        return StartTLSExtendedRequest.OID.equals(getRequest().getOID());
    }

    @Override
    S newErrorResult(final ResultCode resultCode, final String diagnosticMessage, final Throwable cause) {
        return getRequest().getResultDecoder().newExtendedErrorResult(resultCode, "", diagnosticMessage);
    }
}
