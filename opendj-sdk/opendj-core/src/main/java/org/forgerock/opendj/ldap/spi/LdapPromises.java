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

import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.responses.Responses.newCompareResult;
import static org.forgerock.opendj.ldap.responses.Responses.newResult;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.Promises;

/**
 * Utility methods for creating and composing {@link LdapPromise}s.
 */
public final class LdapPromises {
    private LdapPromises() {
    }

    /**
     * Converts a {@link Promise} to a {@link LdapPromise}.
     *
     * @param <R>
     *         The type of the task's result, or {@link Void} if the task does not return anything (i.e. it only has
     *         side-effects).
     * @param wrappedPromise
     *         The {@link Promise} to wrap.
     * @return A {@link LdapPromise} representing the same asynchronous task as the {@link Promise} provided.
     */
    public static <R> LdapPromise<R> asPromise(Promise<R, LdapException> wrappedPromise) {
        return wrap(wrappedPromise, -1);
    }

    /**
     * Creates a new bind {@link BindResultLdapPromiseImpl}.
     *
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The bind request sent to server.
     * @param bindClient
     *         Client that binds to the server.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link BindResultLdapPromiseImpl}.
     */
    public static BindResultLdapPromiseImpl newBindLdapPromise(
            final int requestID,
            final BindRequest request,
            final BindClient bindClient,
            final IntermediateResponseHandler intermediateResponseHandler,
            final Connection connection) {
        return new BindResultLdapPromiseImpl(LdapPromises.<BindResult>newInnerBindOrStartTLSPromise(),
                                             requestID,
                                             request,
                                             bindClient,
                                             intermediateResponseHandler);
    }

    /**
     * Creates a new bind {@link BindResultLdapPromiseImpl}.
     *
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The bind request sent to server.
     * @param bindClient
     *         Client that binds to the server.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link BindResultLdapPromiseImpl}.
     */
    public static BindResultLdapPromiseImpl newBindLdapPromise(
            final int requestID,
            final BindRequest request,
            final BindClient bindClient,
            final IntermediateResponseHandler intermediateResponseHandler,
            final LDAPConnectionImpl connection) {
        return new BindResultLdapPromiseImpl(LdapPromises.<BindResult>newInnerBindOrStartTLSPromise(),
                                             requestID,
                                             request,
                                             bindClient,
                                             intermediateResponseHandler);
    }

    /**
     * Creates a new compare {@link ResultLdapPromiseImpl}.
     *
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The compare request sent to the server.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link ResultLdapPromiseImpl}.
     */
    public static ResultLdapPromiseImpl<CompareRequest, CompareResult> newCompareLdapPromise(
            final int requestID,
            final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final Connection connection) {
        final PromiseImpl<CompareResult, LdapException> innerPromise = newInnerPromise(connection, requestID);
        return newCompareLdapPromise(innerPromise, requestID, request, intermediateResponseHandler);
    }

    /**
     * Creates a new compare {@link ResultLdapPromiseImpl}.
     *
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The compare request sent to the server.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link ResultLdapPromiseImpl}.
     */
    public static ResultLdapPromiseImpl<CompareRequest, CompareResult> newCompareLdapPromise(
            final int requestID,
            final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final LDAPConnectionImpl connection) {
        final PromiseImpl<CompareResult, LdapException> innerPromise = newInnerPromise(connection, requestID);
        return newCompareLdapPromise(innerPromise, requestID, request, intermediateResponseHandler);
    }

    private static ResultLdapPromiseImpl<CompareRequest, CompareResult> newCompareLdapPromise(
            final PromiseImpl<CompareResult, LdapException> innerPromise,
            final int requestID,
            final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        return new ResultLdapPromiseImpl<CompareRequest, CompareResult>(innerPromise,
                                                                        requestID,
                                                                        request,
                                                                        intermediateResponseHandler) {
            @Override
            protected CompareResult newErrorResult(ResultCode resultCode, String diagnosticMessage, Throwable cause) {
                return newCompareResult(resultCode).setDiagnosticMessage(diagnosticMessage).setCause(cause);
            }
        };
    }

    /**
     * Creates a new extended {@link ExtendedResultLdapPromiseImpl}.
     *
     * @param <S>
     *         The type of result returned by this promise.
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The extended request sent to the server.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link ExtendedResultLdapPromiseImpl}.
     */
    public static <S extends ExtendedResult> ExtendedResultLdapPromiseImpl<S> newExtendedLdapPromise(
            final int requestID,
            final ExtendedRequest<S> request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final Connection connection) {
        final PromiseImpl<S, LdapException> innerPromise;
        if (!StartTLSExtendedRequest.OID.equals(request.getOID())) {
            innerPromise = newInnerBindOrStartTLSPromise();
        } else {
            innerPromise = newInnerPromise(connection, requestID);
        }
        return new ExtendedResultLdapPromiseImpl<>(innerPromise, requestID, request, intermediateResponseHandler);
    }

    /**
     * Creates a new extended {@link ExtendedResultLdapPromiseImpl}.
     *
     * @param <S>
     *         The type of result returned by this promise.
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The extended request sent to the server.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link ExtendedResultLdapPromiseImpl}.
     */
    public static <S extends ExtendedResult> ExtendedResultLdapPromiseImpl<S> newExtendedLdapPromise(
            final int requestID,
            final ExtendedRequest<S> request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final LDAPConnectionImpl connection) {
        final PromiseImpl<S, LdapException> innerPromise;
        if (!StartTLSExtendedRequest.OID.equals(request.getOID())) {
            innerPromise = newInnerBindOrStartTLSPromise();
        } else {
            innerPromise = newInnerPromise(connection, requestID);
        }
        return new ExtendedResultLdapPromiseImpl<>(innerPromise, requestID, request, intermediateResponseHandler);
    }

    /**
     * Creates a new {@link ResultLdapPromiseImpl} to handle  a standard request (add, delete, modify and modidyDN).
     *
     * @param <R>
     *         The type of the task's request.
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The request sent to the server.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link ResultLdapPromiseImpl}.
     */
    public static <R extends Request> ResultLdapPromiseImpl<R, Result> newResultLdapPromise(
            final int requestID,
            final R request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final Connection connection) {
        final PromiseImpl<Result, LdapException> innerPromise = newInnerPromise(connection, requestID);
        return newResultLdapPromise(innerPromise, requestID, request, intermediateResponseHandler);
    }

    /**
     * Creates a new {@link ResultLdapPromiseImpl} to handle  a standard request (add, delete, modify and modidyDN).
     *
     * @param <R>
     *         The type of the task's request.
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The request sent to the server.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link ResultLdapPromiseImpl}.
     */
    public static <R extends Request> ResultLdapPromiseImpl<R, Result> newResultLdapPromise(
            final int requestID,
            final R request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final LDAPConnectionImpl connection) {
        final PromiseImpl<Result, LdapException> innerPromise = newInnerPromise(connection, requestID);
        return newResultLdapPromise(innerPromise, requestID, request, intermediateResponseHandler);
    }

    private static <R extends Request> ResultLdapPromiseImpl<R, Result> newResultLdapPromise(
            final PromiseImpl<Result, LdapException> innerPromise,
            final int requestID,
            final R request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        return new ResultLdapPromiseImpl<R, Result>(innerPromise, requestID, request, intermediateResponseHandler) {
            @Override
            protected Result newErrorResult(ResultCode resultCode, String diagnosticMessage, Throwable cause) {
                return newResult(resultCode).setDiagnosticMessage(diagnosticMessage).setCause(cause);
            }
        };
    }

    /**
     * Creates a new search {@link SearchResultLdapPromiseImpl}.
     *
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The search request sent to the server.
     * @param resultHandler
     *         Handler that consumes search result.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link SearchResultLdapPromiseImpl}.
     */
    public static SearchResultLdapPromiseImpl newSearchLdapPromise(
            final int requestID,
            final SearchRequest request,
            final SearchResultHandler resultHandler,
            final IntermediateResponseHandler intermediateResponseHandler,
            final Connection connection) {
        return new SearchResultLdapPromiseImpl(newInnerPromise(connection, requestID),
                                               requestID,
                                               request,
                                               resultHandler,
                                               intermediateResponseHandler);
    }

    /**
     * Creates a new search {@link SearchResultLdapPromiseImpl}.
     *
     * @param requestID
     *         Identifier of the request.
     * @param request
     *         The search request sent to the server.
     * @param resultHandler
     *         Handler that consumes search result.
     * @param intermediateResponseHandler
     *         Handler that consumes intermediate responses from extended operations.
     * @param connection
     *         The connection to directory server.
     * @return The new {@link SearchResultLdapPromiseImpl}.
     */
    public static SearchResultLdapPromiseImpl newSearchLdapPromise(
            final int requestID,
            final SearchRequest request,
            final SearchResultHandler resultHandler,
            final IntermediateResponseHandler intermediateResponseHandler,
            final LDAPConnectionImpl connection) {
        return new SearchResultLdapPromiseImpl(newInnerPromise(connection, requestID),
                                               requestID,
                                               request,
                                               resultHandler,
                                               intermediateResponseHandler);
    }

    /**
     * Returns a {@link LdapPromise} representing an asynchronous task which has already failed with the provided
     * error.
     *
     * @param <R>
     *         The type of the task's result, or {@link Void} if the task does not return anything (i.e. it only has
     *         side-effects).
     * @param <E>
     *         The type of the exception thrown by the task if it fails.
     * @param error
     *         The exception indicating why the asynchronous task has failed.
     * @return A {@link LdapPromise} representing an asynchronous task which has already failed with the provided error.
     */
    public static <R, E extends LdapException> LdapPromise<R> newFailedLdapPromise(final E error) {
        return wrap(Promises.<R, LdapException>newExceptionPromise(error), -1);
    }

    /**
     * Returns a {@link LdapPromise} representing an asynchronous task, identified by the provided requestID, which has
     * already failed with the provided error.
     *
     * @param <R>
     *         The type of the task's result, or {@link Void} if the task does not return anything (i.e. it only has
     *         side-effects).
     * @param <E>
     *         The type of the exception thrown by the task if it fails.
     * @param error
     *         The exception indicating why the asynchronous task has failed.
     * @param requestID
     *         The request ID of the failed task.
     * @return A {@link LdapPromise} representing an asynchronous task which has already failed with the provided error.
     */
    public static <R, E extends LdapException> LdapPromise<R> newFailedLdapPromise(final E error, int requestID) {
        return wrap(Promises.<R, LdapException>newExceptionPromise(error), requestID);
    }

    /**
     * Returns a {@link LdapPromise} representing an asynchronous task which has already succeeded with the provided
     * result. Attempts to get the result will immediately return the result.
     *
     * @param <R>
     *         The type of the task's result, or {@link Void} if the task does not return anything (i.e. it only has
     *         side-effects).
     * @param result
     *         The result of the asynchronous task.
     * @return A {@link LdapPromise} representing an asynchronous task which has already succeeded with the provided
     * result.
     */
    public static <R> LdapPromise<R> newSuccessfulLdapPromise(final R result) {
        return wrap(Promises.<R, LdapException>newResultPromise(result), -1);
    }

    /**
     * Returns a {@link LdapPromise} representing an asynchronous task, identified by the provided requestID, which has
     * already succeeded with the provided result. Attempts to get the result will immediately return the result.
     *
     * @param <R>
     *         The type of the task's result, or {@link Void} if the task does not return anything (i.e. it only has
     *         side-effects).
     * @param result
     *         The result of the asynchronous task.
     * @param requestID
     *         The request ID of the succeeded task.
     * @return A {@link LdapPromise} representing an asynchronous task which has already succeeded with the provided
     * result.
     */
    public static <R> LdapPromise<R> newSuccessfulLdapPromise(final R result, int requestID) {
        return wrap(Promises.<R, LdapException>newResultPromise(result), requestID);
    }

    private static <S extends Result> PromiseImpl<S, LdapException> newInnerBindOrStartTLSPromise() {
        return new PromiseImpl<S, LdapException>() {
            @Override
            protected LdapException tryCancel(boolean mayInterruptIfRunning) {
                /*
                 * No other operations can be performed while a bind or StartTLS is active. Therefore it is not
                 * possible to cancel bind or StartTLS requests, since doing so will leave the connection in a
                 * state which prevents other operations from being performed.
                 */
                return null;
            }
        };
    }

    private static <S extends Result> PromiseImpl<S, LdapException> newInnerPromise(
            final Connection connection, final int requestID) {
        return new PromiseImpl<S, LdapException>() {
            @Override
            protected final LdapException tryCancel(final boolean mayInterruptIfRunning) {
                /*
                 * This will abandon the request, but will also recursively cancel this future. There is no risk of
                 * an infinite loop because the state of this future has already been changed.
                 */
                connection.abandonAsync(Requests.newAbandonRequest(requestID));
                return newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED);
            }
        };
    }

    private static <S extends Result> PromiseImpl<S, LdapException> newInnerPromise(
            final LDAPConnectionImpl connection, final int requestID) {
        return new PromiseImpl<S, LdapException>() {
            @Override
            protected final LdapException tryCancel(final boolean mayInterruptIfRunning) {
                /*
                 * This will abandon the request, but will also recursively cancel this future. There is no risk of
                 * an infinite loop because the state of this future has already been changed.
                 */
                connection.abandonAsync(Requests.newAbandonRequest(requestID));
                return newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED);
            }
        };
    }

    static <R> LdapPromise<R> wrap(Promise<R, LdapException> wrappedPromise, int requestID) {
        return new LdapPromiseWrapper<>(wrappedPromise, requestID);
    }
}
