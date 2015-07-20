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

package org.forgerock.opendj.ldap.responses;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;

/**
 * A factory interface for decoding a generic extended result as an extended
 * result of specific type.
 *
 * @param <S>
 *            The type of result.
 */
public interface ExtendedResultDecoder<S extends ExtendedResult> {

    /**
     * Creates a new extended operation error result using the provided decoding
     * exception. This method should be used to adapt {@code DecodeException}
     * encountered while decoding an extended request or result. The returned
     * error result will have the result code {@link ResultCode#PROTOCOL_ERROR}.
     *
     * @param exception
     *            The decoding exception to be adapted.
     * @return An extended operation error result representing the decoding
     *         exception.
     * @throws NullPointerException
     *             If {@code exception} was {@code null}.
     */
    S adaptDecodeException(DecodeException exception);

    /**
     * Adapts the provided extended result handler into a result handler which
     * is compatible with this extended result decoder. Extended results handled
     * by the returned handler will be automatically converted and passed to the
     * provided result handler. Decoding errors encountered while decoding the
     * extended result will be converted into protocol errors.
     *
     * @param <R>
     *            The type of result handler to be adapted.
     * @param request
     *            The extended request whose result handler is to be adapted.
     * @param resultHandler
     *            The extended result handler which is to be adapted.
     * @param options
     *            The set of decode options which should be used when decoding
     *            the extended operation result.
     * @return A result handler which is compatible with this extended result
     *         decoder.
     */
    <R extends ExtendedResult> LdapResultHandler<S> adaptExtendedResultHandler(
            ExtendedRequest<R> request, LdapResultHandler<? super R> resultHandler, DecodeOptions options);

    /**
     * Decodes the provided extended operation result as a {@code Result} of
     * type {@code S}. This method is called when an extended result is received
     * from the server. The result may indicate success or failure of the
     * extended request.
     *
     * @param result
     *            The extended operation result to be decoded.
     * @param options
     *            The set of decode options which should be used when decoding
     *            the extended operation result.
     * @return The decoded extended operation result.
     * @throws DecodeException
     *             If the provided extended operation result could not be
     *             decoded. For example, if the request name was wrong, or if
     *             the request value was invalid.
     */
    S decodeExtendedResult(ExtendedResult result, DecodeOptions options) throws DecodeException;

    /**
     * Creates a new extended error result using the provided result code,
     * matched DN, and diagnostic message. This method is called when a generic
     * failure occurs, such as a connection failure, and the error result needs
     * to be converted to a {@code Result} of type {@code S}.
     *
     * @param resultCode
     *            The result code.
     * @param matchedDN
     *            The matched DN, which may be empty if none was provided.
     * @param diagnosticMessage
     *            The diagnostic message, which may be empty if none was
     *            provided.
     * @return The decoded extended operation error result.
     * @throws NullPointerException
     *             If {@code resultCode}, {@code matchedDN}, or
     *             {@code diagnosticMessage} were {@code null}.
     */
    S newExtendedErrorResult(ResultCode resultCode, String matchedDN, String diagnosticMessage);

}
