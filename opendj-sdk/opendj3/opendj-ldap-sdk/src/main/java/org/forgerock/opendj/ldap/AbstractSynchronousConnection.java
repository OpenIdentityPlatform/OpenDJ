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
 *      Copyright 2012 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.requests.AbandonRequest;
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

import com.forgerock.opendj.util.CompletedFutureResult;

/**
 * An abstract connection whose asynchronous methods are implemented in terms of
 * synchronous methods.
 * <p>
 * <b>NOTE:</b> this implementation does not support intermediate response
 * handlers except for extended operations, because they are not supported by
 * the equivalent synchronous methods.
 */
public abstract class AbstractSynchronousConnection extends AbstractConnection {

    /**
     * Creates a new abstract synchronous connection.
     */
    protected AbstractSynchronousConnection() {
        // No implementation required.
    }

    /**
     * Abandon operations are not supported because operations are performed
     * synchronously and the ID of the request to be abandoned cannot be
     * determined. Thread interruption must be used in order to cancel a blocked
     * request.
     *
     * @param request
     *            {@inheritDoc}
     * @return {@inheritDoc}
     * @throws UnsupportedOperationException
     *             Always thrown: abandon requests are not supported for
     *             synchronous connections.
     */
    @Override
    public FutureResult<Void> abandonAsync(final AbandonRequest request) {
        throw new UnsupportedOperationException(
                "Abandon requests are not supported for synchronous connections");
    }

    @Override
    public FutureResult<Result> addAsync(final AddRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        try {
            return onSuccess(add(request), resultHandler);
        } catch (final ErrorResultException e) {
            return onFailure(e, resultHandler);
        }
    }

    @Override
    public FutureResult<BindResult> bindAsync(final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super BindResult> resultHandler) {
        try {
            return onSuccess(bind(request), resultHandler);
        } catch (final ErrorResultException e) {
            return onFailure(e, resultHandler);
        }
    }

    @Override
    public FutureResult<CompareResult> compareAsync(final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super CompareResult> resultHandler) {
        try {
            return onSuccess(compare(request), resultHandler);
        } catch (final ErrorResultException e) {
            return onFailure(e, resultHandler);
        }
    }

    @Override
    public FutureResult<Result> deleteAsync(final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        try {
            return onSuccess(delete(request), resultHandler);
        } catch (final ErrorResultException e) {
            return onFailure(e, resultHandler);
        }
    }

    @Override
    public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
            final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super R> resultHandler) {
        try {
            return onSuccess(extendedRequest(request, intermediateResponseHandler), resultHandler);
        } catch (final ErrorResultException e) {
            return onFailure(e, resultHandler);
        }
    }

    @Override
    public FutureResult<Result> modifyAsync(final ModifyRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        try {
            return onSuccess(modify(request), resultHandler);
        } catch (final ErrorResultException e) {
            return onFailure(e, resultHandler);
        }
    }

    @Override
    public FutureResult<Result> modifyDNAsync(final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<? super Result> resultHandler) {
        try {
            return onSuccess(modifyDN(request), resultHandler);
        } catch (final ErrorResultException e) {
            return onFailure(e, resultHandler);
        }
    }

    @Override
    public FutureResult<Result> searchAsync(final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final SearchResultHandler resultHandler) {
        try {
            return onSuccess(search(request, resultHandler), resultHandler);
        } catch (final ErrorResultException e) {
            return onFailure(e, resultHandler);
        }
    }

    private <R extends Result> FutureResult<R> onFailure(final ErrorResultException e,
            final ResultHandler<? super R> resultHandler) {
        if (resultHandler != null) {
            resultHandler.handleErrorResult(e);
        }
        return new CompletedFutureResult<R>(e);
    }

    private <R extends Result> FutureResult<R> onSuccess(final R result,
            final ResultHandler<? super R> resultHandler) {
        if (resultHandler != null) {
            resultHandler.handleResult(result);
        }
        return new CompletedFutureResult<R>(result);
    }

}
