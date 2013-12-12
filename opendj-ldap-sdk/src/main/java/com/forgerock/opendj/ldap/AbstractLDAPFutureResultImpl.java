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
 *      Portions copyright 2011-2013 ForgeRock AS.
 */

package com.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Result;

import com.forgerock.opendj.util.AsynchronousFutureResult;

/**
 * Abstract future result implementation.
 *
 * @param <S>
 *            The type of result returned by this future.
 */
abstract class AbstractLDAPFutureResultImpl<S extends Result>
        extends AsynchronousFutureResult<S, ResultHandler<? super S>>
        implements IntermediateResponseHandler {
    private final Connection connection;
    private IntermediateResponseHandler intermediateResponseHandler;
    private volatile long timestamp;

    AbstractLDAPFutureResultImpl(final int requestID,
        final ResultHandler<? super S> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler,
        final Connection connection) {
        super(resultHandler, requestID);
        this.connection = connection;
        this.intermediateResponseHandler = intermediateResponseHandler;
        this.timestamp = System.currentTimeMillis();
    }

    /** {@inheritDoc} */
    @Override
    public final boolean handleIntermediateResponse(final IntermediateResponse response) {
        // FIXME: there's a potential race condition here - the future could
        // get cancelled between the isDone() call and the handler
        // invocation. We'd need to add support for intermediate handlers in
        // the synchronizer.
        if (!isDone()) {
            updateTimestamp();
            if (intermediateResponseHandler != null) {
                if (!intermediateResponseHandler.handleIntermediateResponse(response)) {
                    intermediateResponseHandler = null;
                }
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final ErrorResultException handleCancelRequest(final boolean mayInterruptIfRunning) {
        /*
         * This will abandon the request, but will also recursively cancel this
         * future. There is no risk of an infinite loop because the state of
         * this future has already been changed.
         */
        connection.abandonAsync(Requests.newAbandonRequest(getRequestID()));
        return null;
    }

    @Override
    protected final boolean isCancelable() {
        /*
         * No other operations can be performed while a bind or startTLS
         * operations is active. Therefore it is not possible to cancel bind or
         * startTLS requests, since doing so will leave the connection in a
         * state which prevents other operations from being performed.
         */
        return !isBindOrStartTLS();
    }

    /**
     * Returns {@code false} by default.
     */
    boolean isBindOrStartTLS() {
        return false;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        sb.append(" requestID = ");
        sb.append(getRequestID());
        sb.append(" timestamp = ");
        sb.append(timestamp);
        super.toString(sb);
    }

    final void adaptErrorResult(final Result result) {
        final S errorResult =
                newErrorResult(result.getResultCode(), result.getDiagnosticMessage(), result
                        .getCause());
        setResultOrError(errorResult);
    }

    final long getTimestamp() {
        return timestamp;
    }

    abstract S newErrorResult(ResultCode resultCode, String diagnosticMessage, Throwable cause);

    final void setResultOrError(final S result) {
        if (result.getResultCode().isExceptional()) {
            handleErrorResult(ErrorResultException.newErrorResult(result));
        } else {
            handleResult(result);
        }
    }

    final void updateTimestamp() {
        timestamp = System.currentTimeMillis();
    }

    /**
     * Returns {@code true} if this request should be canceled once the timeout
     * period expires. The default implementation is to return {@code true}
     * which will be appropriate for nearly all requests, the one obvious
     * exception being persistent searches.
     *
     * @return {@code true} if this request should be canceled once the timeout
     *         period expires.
     */
    boolean checkForTimeout() {
        return true;
    }
}
