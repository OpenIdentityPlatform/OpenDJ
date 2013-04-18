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

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;

import com.forgerock.opendj.util.FutureResultTransformer;
import com.forgerock.opendj.util.RecursiveFutureResult;

/**
 * An authenticated connection factory can be used to create pre-authenticated
 * connections to a Directory Server.
 * <p>
 * The connections returned by an authenticated connection factory support all
 * operations with the exception of Bind requests. Attempts to perform a Bind
 * will result in an {@code UnsupportedOperationException}.
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
    public static final class AuthenticatedConnection extends AbstractConnectionWrapper<Connection> {

        private AuthenticatedConnection(final Connection connection) {
            super(connection);
        }

        /*
         * Bind operations are not supported by pre-authenticated connections.
         * These methods will always throw {@code UnsupportedOperationException}.
         */

        public FutureResult<BindResult> bindAsync(final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super BindResult> resultHandler) {
            throw new UnsupportedOperationException();
        }


        public BindResult bind(BindRequest request) throws ErrorResultException {
            throw new UnsupportedOperationException();
        }


        public BindResult bind(String name, char[] password) throws ErrorResultException {
            throw new UnsupportedOperationException();
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
                protected Connection transformResult(final BindResult result)
                        throws ErrorResultException {
                    return new AuthenticatedConnection(connection);
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
     */
    AuthenticatedConnectionFactory(final ConnectionFactory factory, final BindRequest request) {
        this.parentFactory = factory;

        // FIXME: should do a defensive copy.
        this.request = request;
    }

    @Override
    public void close() {
        // Delegate.
        parentFactory.close();
    }

    public Connection getConnection() throws ErrorResultException {
        final Connection connection = parentFactory.getConnection();
        boolean bindSucceeded = false;
        try {
            connection.bind(request);
            bindSucceeded = true;
        } finally {
            if (!bindSucceeded) {
                connection.close();
            }
        }

        /*
         * If the bind didn't succeed then an exception will have been thrown
         * and this line will not be reached.
         */
        return new AuthenticatedConnection(connection);
    }


    @Override
    public FutureResult<Connection> getConnectionAsync(
            final ResultHandler<? super Connection> handler) {
        final FutureResultImpl future = new FutureResultImpl(request, handler);
        future.futureConnectionResult.setFutureResult(parentFactory
                .getConnectionAsync(future.futureConnectionResult));
        return future.futureBindResult;
    }


    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AuthenticatedConnectionFactory(");
        builder.append(String.valueOf(parentFactory));
        builder.append(", ");
        builder.append(request);
        builder.append(')');
        return builder.toString();
    }

}
