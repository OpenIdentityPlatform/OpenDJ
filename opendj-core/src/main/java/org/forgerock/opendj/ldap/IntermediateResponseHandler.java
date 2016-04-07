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
 * Portions copyright 2011 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.responses.IntermediateResponse;

/**
 * A completion handler for consuming intermediate responses returned from
 * extended operations, or other operations for which an appropriate control was
 * sent.
 * <p>
 * Intermediate responses are rarely used in practice and are therefore only
 * supported in a few specialized cases where they are most likely to be
 * encountered:
 * <ul>
 * <li>when performing extended requests using the
 * {@link Connection#extendedRequest} methods
 * <li>when using the asynchronous operation methods, such as
 * {@link Connection#addAsync}
 * </ul>
 * When no handler is provided any intermediate responses will be discarded.
 * <p>
 * The {@link #handleIntermediateResponse} method is invoked each time a
 * Intermediate Response is returned from the Directory Server.
 * <p>
 * Implementations of these methods should complete in a timely manner so as to
 * avoid keeping the invoking thread from dispatching to other completion
 * handlers.
 */
public interface IntermediateResponseHandler {
    /**
     * Invoked each time an intermediate response is returned from the Directory
     * Server.
     *
     * @param response
     *            The intermediate response.
     * @return {@code true} if this handler should continue to be notified of
     *         any remaining intermediate responses, or {@code false} if the
     *         remaining responses should be skipped for some reason (e.g. a
     *         client side size limit has been reached).
     */
    boolean handleIntermediateResponse(IntermediateResponse response);
}
