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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;

import static org.forgerock.opendj.ldap.LdapException.*;

/**
 * An abstract connection whose synchronous methods are implemented in terms of
 * asynchronous methods.
 */
public abstract class AbstractAsynchronousConnection extends AbstractConnection {

    /**
     * Creates a new abstract asynchronous connection.
     */
    protected AbstractAsynchronousConnection() {
        // No implementation required.
    }

    /** {@inheritDoc} */
    @Override
    public Result add(final AddRequest request) throws LdapException {
        return blockingGetOrThrow(addAsync(request));
    }

    /** {@inheritDoc} */
    @Override
    public BindResult bind(final BindRequest request) throws LdapException {
        return blockingGetOrThrow(bindAsync(request));
    }

    /** {@inheritDoc} */
    @Override
    public CompareResult compare(final CompareRequest request) throws LdapException {
        return blockingGetOrThrow(compareAsync(request));
    }

    /** {@inheritDoc} */
    @Override
    public Result delete(final DeleteRequest request) throws LdapException {
        return blockingGetOrThrow(deleteAsync(request));
    }

    /** {@inheritDoc} */
    @Override
    public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request,
            final IntermediateResponseHandler handler) throws LdapException {
        return blockingGetOrThrow(extendedRequestAsync(request, handler));
    }

    /** {@inheritDoc} */
    @Override
    public Result modify(final ModifyRequest request) throws LdapException {
        return blockingGetOrThrow(modifyAsync(request));
    }

    /** {@inheritDoc} */
    @Override
    public Result modifyDN(final ModifyDNRequest request) throws LdapException {
        return blockingGetOrThrow(modifyDNAsync(request));
    }

    /** {@inheritDoc} */
    @Override
    public Result search(final SearchRequest request, final SearchResultHandler handler) throws LdapException {
        return blockingGetOrThrow(searchAsync(request, handler));
    }

    private <T extends Result> T blockingGetOrThrow(LdapPromise<T> promise) throws LdapException {
        try {
            return promise.getOrThrow();
        } catch (InterruptedException e) {
            throw interrupted(e);
        }
    }

    /** Handle thread interruption. */
    private LdapException interrupted(InterruptedException e) {
        return newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
    }
}
