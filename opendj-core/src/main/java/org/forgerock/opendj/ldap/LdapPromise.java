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
 *      Portions copyright 2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import org.forgerock.util.promise.AsyncFunction;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.SuccessHandler;

/**
 * A handle which can be used to retrieve the Result of an asynchronous Request.
 *
 * @param <S>
 *            The type of result returned by this promise.
 */
public interface LdapPromise<S> extends Promise<S, LdapException> {
    /**
     * Returns the request ID of the request if appropriate.
     *
     * @return The request ID, or {@code -1} if there is no request ID.
     */
    int getRequestID();

    @Override
    LdapPromise<S> onSuccess(SuccessHandler<? super S> onSuccess);

    @Override
    LdapPromise<S> onFailure(FailureHandler<? super LdapException> onFailure);

    @Override
    LdapPromise<S> onSuccessOrFailure(Runnable onSuccessOrFailure);

    @Override
    // @Checkstyle:ignore
    <VOUT> LdapPromise<VOUT> then(Function<? super S, VOUT, LdapException> onSuccess);

    @Override
    LdapPromise<S> then(SuccessHandler<? super S> onSuccess);

    @Override
    LdapPromise<S> then(SuccessHandler<? super S> onSuccess, FailureHandler<? super LdapException> onFailure);

    @Override
    LdapPromise<S> thenAlways(Runnable onSuccessOrFailure);

    @Override
    // @Checkstyle:ignore
    <VOUT> LdapPromise<VOUT> thenAsync(AsyncFunction<? super S, VOUT, LdapException> onSuccess);
}
