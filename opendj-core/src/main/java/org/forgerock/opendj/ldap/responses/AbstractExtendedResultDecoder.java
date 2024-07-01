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
 * Portions Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import static org.forgerock.opendj.ldap.LdapException.newLdapException;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;

/**
 * This class provides a skeletal implementation of the
 * {@code ExtendedResultDecoder} interface, to minimize the effort required to
 * implement this interface.
 *
 * @param <S>
 *            The type of result.
 */
public abstract class AbstractExtendedResultDecoder<S extends ExtendedResult> implements
        ExtendedResultDecoder<S> {
    /**
     * Creates a new abstract extended result decoder.
     */
    protected AbstractExtendedResultDecoder() {
        // Nothing to do.
    }

    @Override
    public S adaptDecodeException(final DecodeException exception) {
        final S adaptedResult =
                newExtendedErrorResult(ResultCode.PROTOCOL_ERROR, "", exception.getMessage());
        adaptedResult.setCause(exception.getCause());
        return adaptedResult;
    }

    @Override
    public <R extends ExtendedResult> LdapResultHandler<S> adaptExtendedResultHandler(
            final ExtendedRequest<R> request, final LdapResultHandler<? super R> resultHandler,
            final DecodeOptions options) {
        return new LdapResultHandler<S>() {

            @Override
            public void handleException(final LdapException error) {
                final Result result = error.getResult();
                final R adaptedResult =
                        request.getResultDecoder().newExtendedErrorResult(result.getResultCode(),
                                result.getMatchedDN(), result.getDiagnosticMessage());
                adaptedResult.setCause(result.getCause());
                resultHandler.handleException(newLdapException(adaptedResult));
            }

            @Override
            public void handleResult(final S result) {
                try {
                    final R adaptedResult =
                            request.getResultDecoder().decodeExtendedResult(result, options);
                    resultHandler.handleResult(adaptedResult);
                } catch (final DecodeException e) {
                    final R adaptedResult = request.getResultDecoder().adaptDecodeException(e);
                    resultHandler.handleException(newLdapException(adaptedResult));
                }
            }

        };
    }

    @Override
    public abstract S decodeExtendedResult(ExtendedResult result, DecodeOptions options)
            throws DecodeException;

    @Override
    public abstract S newExtendedErrorResult(ResultCode resultCode, String matchedDN,
            String diagnosticMessage);

}
