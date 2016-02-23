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
 * Copyright 2014-2015 ForgeRock AS.
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
