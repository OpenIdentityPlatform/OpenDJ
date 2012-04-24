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

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.responses.ExtendedResult;

/**
 * A factory interface for decoding a generic extended request as an extended
 * request of specific type.
 *
 * @param <R>
 *            The type of extended request.
 * @param <S>
 *            The type of result.
 */
public interface ExtendedRequestDecoder<R extends ExtendedRequest<S>, S extends ExtendedResult> {
    /**
     * Decodes the provided extended operation request as an
     * {@code ExtendedRequest} of type {@code R}.
     *
     * @param request
     *            The extended operation request to be decoded.
     * @param options
     *            The set of decode options which should be used when decoding
     *            the extended operation request.
     * @return The decoded extended operation request.
     * @throws DecodeException
     *             If the provided extended operation request could not be
     *             decoded. For example, if the request name was wrong, or if
     *             the request value was invalid.
     */
    R decodeExtendedRequest(ExtendedRequest<?> request, DecodeOptions options)
            throws DecodeException;

}
