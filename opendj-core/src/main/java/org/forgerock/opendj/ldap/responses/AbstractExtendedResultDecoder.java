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
 *      Portions Copyright 2012-2014 ForgeRock AS.
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
