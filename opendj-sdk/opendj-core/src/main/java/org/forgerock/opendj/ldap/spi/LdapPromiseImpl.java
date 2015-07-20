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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.spi;

import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.Promises;

/**
 * This class provides an implementation of the {@link LdapPromise}.
 *
 * @param <S>
 *            The type of result returned by this promise.
 * @see Promise
 * @see Promises
 * @see LdapPromise
 */
public class LdapPromiseImpl<S> extends LdapPromiseWrapper<S, PromiseImpl<S, LdapException>> implements
        LdapPromise<S>, LdapResultHandler<S> {

    /**
     * Creates a new {@link LdapPromiseImpl} from a wrapped existing {@link PromiseImpl}.
     *
     * @param wrappedPromise
     *      The {@link Promise} to wrap.
     * @param requestID
     *      Identifier of the request.
     */
    protected LdapPromiseImpl(PromiseImpl<S, LdapException> wrappedPromise, int requestID) {
        super(wrappedPromise, requestID);
    }

    /**
     * Creates a new {@link LdapPromiseImpl}.
     *
     * @param <S>
     *            The type of result of the promise.
     * @return a new {@link LdapPromiseImpl}
     */
    public static <S> LdapPromiseImpl<S> newLdapPromiseImpl() {
        return newLdapPromiseImpl(-1);
    }

    /**
     * Creates a new {@link LdapPromiseImpl} with a requestID.
     *
     * @param <S>
     *            The type of result of the promise.
     * @param requestID
     *            Identifier of the request.
     * @return a new {@link LdapPromiseImpl}
     */
    public static <S> LdapPromiseImpl<S> newLdapPromiseImpl(int requestID) {
        return new LdapPromiseImpl<>(PromiseImpl.<S, LdapException> create(), requestID);
    }

    @Override
    public void handleException(LdapException exception) {
        getWrappedPromise().handleException(exception);
    }

    @Override
    public void handleResult(S result) {
        getWrappedPromise().handleResult(result);
    }
}
