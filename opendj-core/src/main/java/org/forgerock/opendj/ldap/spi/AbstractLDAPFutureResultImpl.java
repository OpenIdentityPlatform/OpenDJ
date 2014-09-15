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
 *      Portions copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.spi;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResultImpl;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Result;


/**
 * Abstract future result implementation.
 *
 * @param <S>
 *            The type of result returned by this future.
 */
public abstract class AbstractLDAPFutureResultImpl<S extends Result> extends FutureResultImpl<S> implements
    IntermediateResponseHandler {
    private final Connection connection;
    private IntermediateResponseHandler intermediateResponseHandler;
    private volatile long timestamp;

    /**
     * Creates a future result.
     *
     * @param requestID
     *            identifier of the request
     * @param intermediateResponseHandler
     *            handler that consumes intermediate responses from extended
     *            operations
     * @param connection
     *            the connection to directory server
     */
    protected AbstractLDAPFutureResultImpl(final int requestID,
        final IntermediateResponseHandler intermediateResponseHandler, final Connection connection) {
        super(requestID);
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
            if (intermediateResponseHandler != null
                && !intermediateResponseHandler.handleIntermediateResponse(response)) {
                intermediateResponseHandler = null;
            }
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected final ErrorResultException tryCancel(final boolean mayInterruptIfRunning) {
        /*
         * No other operations can be performed while a bind or startTLS
         * operations is active. Therefore it is not possible to cancel bind or
         * startTLS requests, since doing so will leave the connection in a
         * state which prevents other operations from being performed.
         */
        if (isBindOrStartTLS()) {
            return null;
        }

        /*
         * This will abandon the request, but will also recursively cancel this
         * future. There is no risk of an infinite loop because the state of
         * this future has already been changed.
         */
        connection.abandonAsync(Requests.newAbandonRequest(getRequestID()));
        return ErrorResultException.newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED);
    }


    /**
     * Returns {@code true} if this future represents the result of a bind or
     * StartTLS request. The default implementation is to return {@code false}.
     *
     * @return {@code true} if this future represents the result of a bind or
     *         StartTLS request.
     */
    public boolean isBindOrStartTLS() {
        return false;
    }

    /**
     * Appends a string representation of this future's state to the provided
     * builder.
     *
     * @param sb
     *            The string builder.
     */
    protected void toString(final StringBuilder sb) {
        sb.append(" requestID = ");
        sb.append(getRequestID());
        sb.append(" timestamp = ");
        sb.append(timestamp);
    }

    /**
     * Sets the result associated to this future as an error result.
     *
     * @param result
     *            result of an operation
     */
    public final void adaptErrorResult(final Result result) {
        final S errorResult =
            newErrorResult(result.getResultCode(), result.getDiagnosticMessage(), result.getCause());
        setResultOrError(errorResult);
    }

    /**
     * Returns the creation time of this future.
     *
     * @return the timestamp indicating creation time of this future
     */
    public final long getTimestamp() {
        return timestamp;
    }

    /**
     * Create a new error result.
     *
     * @param resultCode
     *            operation result code
     * @param diagnosticMessage
     *            message associated to the error
     * @param cause
     *            cause of the error
     * @return the error result
     */
    protected abstract S newErrorResult(ResultCode resultCode, String diagnosticMessage, Throwable cause);

    /**
     * Sets the result associated to this future.
     *
     * @param result
     *            the result of operation
     */
    public final void setResultOrError(final S result) {
        if (result.getResultCode().isExceptional()) {
            handleError(ErrorResultException.newErrorResult(result));
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
    public boolean checkForTimeout() {
        return true;
    }
}
