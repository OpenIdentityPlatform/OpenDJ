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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.ResultHandler;

/**
 * A completion handler for consuming the result of an asynchronous operation or
 * connection attempts.
 * <p>
 * A result completion handler may be specified when performing asynchronous
 * operations using a {@link Connection} object or when connecting
 * asynchronously to a remote Directory Server using an
 * {@link ConnectionFactory}. The {@link #handleResult} method is invoked when
 * the operation or connection attempt completes successfully. The
 * {@link #handleException(LdapException)} method is invoked if the operation or connection
 * attempt fails.
 * <p>
 * Implementations of these methods should complete in a timely manner so as to
 * avoid keeping the invoking thread from dispatching to other completion
 * handlers.
 *
 * @param <S>
 *            The type of result handled by this result handler.
 */
public interface LdapResultHandler<S> extends ResultHandler<S>, ExceptionHandler<LdapException> {
    /**
     * Invoked when the asynchronous operation has failed.
     *
     * @param exception
     *            The error result exception indicating why the asynchronous
     *            operation has failed.
     */
    @Override
    void handleException(LdapException exception);

    /**
     * Invoked when the asynchronous operation has completed successfully.
     *
     * @param result
     *            The result of the asynchronous operation.
     */
    @Override
    void handleResult(S result);
}
