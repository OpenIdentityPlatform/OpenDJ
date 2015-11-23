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

import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.Promises;

import static org.forgerock.opendj.ldap.LdapException.*;

/**
 * This class provides an implementation of the {@link LdapPromise}.
 *
 * @param <R>
 *            The type of the associated {@link Request}.
 * @param <S>
 *            The type of result returned by this promise.
 * @see Promise
 * @see Promises
 * @see LdapPromise
 */
public abstract class ResultLdapPromiseImpl<R extends Request, S extends Result> extends LdapPromiseImpl<S>
        implements LdapPromise<S>, IntermediateResponseHandler {
    private final R request;
    private IntermediateResponseHandler intermediateResponseHandler;
    private volatile long timestamp;

    ResultLdapPromiseImpl(final PromiseImpl<S, LdapException> impl, final int requestID, final R request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        super(impl, requestID);
        this.request = request;
        this.intermediateResponseHandler = intermediateResponseHandler;
        this.timestamp = System.currentTimeMillis();
    }

    /** {@inheritDoc} */
    @Override
    public final boolean handleIntermediateResponse(final IntermediateResponse response) {
        // FIXME: there's a potential race condition here - the promise could
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

    /**
     * Returns {@code true} if this promise represents the result of a bind or
     * StartTLS request. The default implementation is to return {@code false}.
     *
     * @return {@code true} if this promise represents the result of a bind or
     *         StartTLS request.
     */
    public boolean isBindOrStartTLS() {
        return false;
    }

    /**
     * Returns a string representation of this promise's state.
     *
     * @return String representation of this promise's state.
     */
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("( requestID = ");
        sb.append(getRequestID());
        sb.append(" timestamp = ");
        sb.append(timestamp);
        sb.append(" request = ");
        sb.append(getRequest());
        sb.append(" )");
        return sb.toString();
    }

    /**
     * Sets the result associated to this promise as an error result.
     *
     * @param result
     *            result of an operation
     */
    public final void adaptErrorResult(final Result result) {
        final S errorResult = newErrorResult(result.getResultCode(), result.getDiagnosticMessage(), result.getCause());
        setResultOrError(errorResult);
    }

    /**
     * Returns the creation time of this promise.
     *
     * @return the timestamp indicating creation time of this promise
     */
    public final long getTimestamp() {
        return timestamp;
    }

    abstract S newErrorResult(ResultCode resultCode, String diagnosticMessage, Throwable cause);

    /**
     * Sets the result associated to this promise.
     *
     * @param result
     *            the result of operation
     */
    public final void setResultOrError(final S result) {
        if (result.getResultCode().isExceptional()) {
            getWrappedPromise().handleException(newLdapException(result));
        } else {
            getWrappedPromise().handleResult(result);
        }
    }

    /**
     * Returns the attached request.
     *
     * @return The request.
     */
    public R getRequest() {
        return request;
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
