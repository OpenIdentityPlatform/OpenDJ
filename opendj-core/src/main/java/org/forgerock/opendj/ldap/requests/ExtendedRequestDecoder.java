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
