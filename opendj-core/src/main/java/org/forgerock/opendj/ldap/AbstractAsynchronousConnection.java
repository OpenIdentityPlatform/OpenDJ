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
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

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

    /**
     * {@inheritDoc}
     */
    @Override
    public Result add(final AddRequest request) throws ErrorResultException {
        final FutureResult<Result> future = addAsync(request, null, null);
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BindResult bind(final BindRequest request) throws ErrorResultException {
        final FutureResult<BindResult> future = bindAsync(request, null, null);
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompareResult compare(final CompareRequest request) throws ErrorResultException {
        final FutureResult<CompareResult> future = compareAsync(request, null, null);
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result delete(final DeleteRequest request) throws ErrorResultException {
        final FutureResult<Result> future = deleteAsync(request, null, null);
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request,
            final IntermediateResponseHandler handler) throws ErrorResultException {
        final FutureResult<R> future = extendedRequestAsync(request, handler, null);
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result modify(final ModifyRequest request) throws ErrorResultException {
        final FutureResult<Result> future = modifyAsync(request, null, null);
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result modifyDN(final ModifyDNRequest request) throws ErrorResultException {
        final FutureResult<Result> future = modifyDNAsync(request, null, null);
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result search(final SearchRequest request, final SearchResultHandler handler)
            throws ErrorResultException {
        final FutureResult<Result> future = searchAsync(request, null, handler);
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            // Cancel the request if it hasn't completed.
            future.cancel(false);
        }
    }

    // Handle thread interruption.
    private ErrorResultException interrupted(InterruptedException e) {
        return newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
    }
}
