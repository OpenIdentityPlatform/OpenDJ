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
 *      Portions copyright 2011-2012 ForgeRock AS.
 */

package com.forgerock.opendj.ldap.tools;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.AbstractConnectionWrapper;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;

import com.forgerock.opendj.util.FutureResultTransformer;
import com.forgerock.opendj.util.RecursiveFutureResult;
import com.forgerock.opendj.util.Validator;

/**
 * An authenticated connection factory can be used to create pre-authenticated
 * connections to a Directory Server.
 * <p>
 * The connections returned by an authenticated connection factory support all
 * operations with the exception of Bind requests. Attempts to perform a Bind
 * will result in an {@code UnsupportedOperationException}.
 * <p>
 * In addition, the returned connections support retrieval of the
 * {@code BindResult} returned from the initial Bind request, or last rebind.
 * <p>
 * Support for connection re-authentication is provided through the
 * {@link #setRebindAllowed} method which, if set to {@code true}, causes
 * subsequent connections created using the factory to support the
 * {@code rebind} method.
 * <p>
 * If the Bind request fails for some reason (e.g. invalid credentials), then
 * the connection attempt will fail and an {@code ErrorResultException} will be
 * thrown.
 */
final class AuthenticatedConnectionFactory implements ConnectionFactory {

    /**
     * An authenticated connection supports all operations except Bind
     * operations.
     */
    static final class AuthenticatedConnection extends AbstractConnectionWrapper<Connection> {

        private final BindRequest request;
        private volatile BindResult result;

        private AuthenticatedConnection(final Connection connection, final BindRequest request,
                final BindResult result) {
            super(connection);
            this.request = request;
            this.result = result;
        }

        /*
         * Bind operations are not supported by pre-authenticated connections.
         * These methods will always throw {@code UnsupportedOperationException}.
         */

        public BindResult bind(BindRequest request) throws ErrorResultException {
            throw new UnsupportedOperationException();
        }

        public BindResult bind(String name, char[] password) throws ErrorResultException {
            throw new UnsupportedOperationException();
        }

        public FutureResult<BindResult> bindAsync(BindRequest request,
                IntermediateResponseHandler intermediateResponseHandler,
                ResultHandler<? super BindResult> resultHandler) {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns an unmodifiable view of the Bind result which was returned
         * from the server after authentication.
         *
         * @return The Bind result which was returned from the server after
         *         authentication.
         */
        BindResult getAuthenticatedBindResult() {
            return result;
        }

        /**
         * Re-authenticates to the Directory Server using the bind request
         * associated with this connection. If re-authentication fails for some
         * reason then this connection will be automatically closed.
         *
         * @param handler
         *            A result handler which can be used to asynchronously
         *            process the operation result when it is received, may be
         *            {@code null}.
         * @return A future representing the result of the operation.
         * @throws UnsupportedOperationException
         *             If this connection does not support rebind operations.
         * @throws IllegalStateException
         *             If this connection has already been closed, i.e. if
         *             {@code isClosed() == true}.
         */
        FutureResult<BindResult> rebindAsync(final ResultHandler<? super BindResult> handler) {
            if (request == null) {
                throw new UnsupportedOperationException();
            }

            /*
             * Wrap the client handler so that we can update the connection
             * state.
             */
            final ResultHandler<? super BindResult> clientHandler = handler;

            final ResultHandler<BindResult> handlerWrapper = new ResultHandler<BindResult>() {

                public void handleErrorResult(final ErrorResultException error) {
                    /*
                     * This connection is now unauthenticated so prevent further
                     * use.
                     */
                    connection.close();

                    if (clientHandler != null) {
                        clientHandler.handleErrorResult(error);
                    }
                }

                public void handleResult(final BindResult result) {
                    // Save the result.
                    AuthenticatedConnection.this.result = result;

                    if (clientHandler != null) {
                        clientHandler.handleResult(result);
                    }
                }

            };

            return connection.bindAsync(request, null, handlerWrapper);
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("AuthenticatedConnection(");
            builder.append(connection);
            builder.append(')');
            return builder.toString();
        }

    }

    private static final class FutureResultImpl {
        private final FutureResultTransformer<BindResult, Connection> futureBindResult;
        private final RecursiveFutureResult<Connection, BindResult> futureConnectionResult;
        private final BindRequest bindRequest;
        private Connection connection;

        private FutureResultImpl(final BindRequest request,
                final ResultHandler<? super Connection> handler) {
            this.bindRequest = request;
            this.futureBindResult = new FutureResultTransformer<BindResult, Connection>(handler) {

                @Override
                protected ErrorResultException transformErrorResult(
                        final ErrorResultException errorResult) {
                    // Ensure that the connection is closed.
                    if (connection != null) {
                        connection.close();
                        connection = null;
                    }
                    return errorResult;
                }

                @Override
                protected AuthenticatedConnection transformResult(final BindResult result)
                        throws ErrorResultException {
                    // FIXME: should make the result unmodifiable.
                    return new AuthenticatedConnection(connection, bindRequest, result);
                }

            };
            this.futureConnectionResult =
                    new RecursiveFutureResult<Connection, BindResult>(futureBindResult) {

                        @Override
                        protected FutureResult<? extends BindResult> chainResult(
                                final Connection innerResult,
                                final ResultHandler<? super BindResult> handler)
                                throws ErrorResultException {
                            connection = innerResult;
                            return connection.bindAsync(bindRequest, null, handler);
                        }
                    };
            futureBindResult.setFutureResult(futureConnectionResult);
        }

    }

    private final BindRequest request;
    private final ConnectionFactory parentFactory;
    private boolean allowRebinds = false;

    /**
     * Creates a new authenticated connection factory which will obtain
     * connections using the provided connection factory and immediately perform
     * the provided Bind request.
     *
     * @param factory
     *            The connection factory to use for connecting to the Directory
     *            Server.
     * @param request
     *            The Bind request to use for authentication.
     * @throws NullPointerException
     *             If {@code factory} or {@code request} was {@code null}.
     */
    AuthenticatedConnectionFactory(final ConnectionFactory factory, final BindRequest request) {
        Validator.ensureNotNull(factory, request);
        this.parentFactory = factory;

        // FIXME: should do a defensive copy.
        this.request = request;
    }

    @Override
    public void close() {
        parentFactory.close();
    }

    public Connection getConnection() throws ErrorResultException {
        final Connection connection = parentFactory.getConnection();
        BindResult bindResult = null;
        try {
            bindResult = connection.bind(request);
        } finally {
            if (bindResult == null) {
                connection.close();
            }
        }

        /*
         * If the bind didn't succeed then an exception will have been thrown
         * and this line will not be reached.
         */
        return new AuthenticatedConnection(connection, request, bindResult);
    }

    public FutureResult<Connection> getConnectionAsync(
            final ResultHandler<? super Connection> handler) {
        final FutureResultImpl future = new FutureResultImpl(request, handler);
        future.futureConnectionResult.setFutureResult(parentFactory
                .getConnectionAsync(future.futureConnectionResult));
        return future.futureBindResult;
    }

    /**
     * Indicates whether or not rebind requests are to be supported by
     * connections created by this authenticated connection factory.
     * <p>
     * Rebind requests are invoked using the connection's {@code rebind} method
     * which will throw an {@code UnsupportedOperationException} if rebinds are
     * not supported (the default).
     *
     * @return allowRebinds {@code true} if the {@code rebind} operation is to
     *         be supported, otherwise {@code false}.
     */
    boolean isRebindAllowed() {
        return allowRebinds;
    }

    /**
     * Specifies whether or not rebind requests are to be supported by
     * connections created by this authenticated connection factory.
     * <p>
     * Rebind requests are invoked using the connection's {@code rebind} method
     * which will throw an {@code UnsupportedOperationException} if rebinds are
     * not supported (the default).
     *
     * @param allowRebinds
     *            {@code true} if the {@code rebind} operation is to be
     *            supported, otherwise {@code false}.
     * @return A reference to this connection factory.
     */
    AuthenticatedConnectionFactory setRebindAllowed(final boolean allowRebinds) {
        this.allowRebinds = allowRebinds;
        return this;
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AuthenticatedConnectionFactory(");
        builder.append(String.valueOf(parentFactory));
        builder.append(')');
        return builder.toString();
    }

}
