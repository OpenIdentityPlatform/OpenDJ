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
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static org.forgerock.util.Utils.*;

import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.AbstractConnectionWrapper;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
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
 * the connection attempt will fail and an {@link LdapException} will be thrown.
 */
public final class AuthenticatedConnectionFactory implements ConnectionFactory {

    /**
     * An authenticated connection supports all operations except Bind
     * operations.
     */
    public static final class AuthenticatedConnection extends AbstractConnectionWrapper<Connection> {

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
        /** {@inheritDoc} */
        @Override
        public BindResult bind(BindRequest request) throws LdapException {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        public BindResult bind(String name, char[] password) throws LdapException {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        public LdapPromise<BindResult> bindAsync(BindRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns an unmodifiable view of the Bind result which was returned
         * from the server after authentication.
         *
         * @return The Bind result which was returned from the server after
         *         authentication.
         */
        public BindResult getAuthenticatedBindResult() {
            return result;
        }

        /**
         * Re-authenticates to the Directory Server using the bind request
         * associated with this connection. If re-authentication fails for some
         * reason then this connection will be automatically closed.
         *
         * @return A promise representing the result of the operation.
         * @throws UnsupportedOperationException
         *             If this connection does not support rebind operations.
         * @throws IllegalStateException
         *             If this connection has already been closed, i.e. if
         *             {@code isClosed() == true}.
         */
        public LdapPromise<BindResult> rebindAsync() {
            if (request == null) {
                throw new UnsupportedOperationException();
            }

            return connection.bindAsync(request)
                      .thenOnResult(new ResultHandler<BindResult>() {
                          @Override
                          public void handleResult(final BindResult result) {
                              // Save the result.
                              AuthenticatedConnection.this.result = result;
                          }
                      }).thenOnException(new ExceptionHandler<LdapException>() {
                          @Override
                          public void handleException(final LdapException exception) {
                              /*
                               * This connection is now unauthenticated so prevent further use.
                               */
                              connection.close();
                          }
                      });
        }

        /**
         * Returns the string representation of this authenticated connection.
         *
         * @return The string representation of this authenticated connection factory.
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("AuthenticatedConnection(");
            builder.append(connection);
            builder.append(')');
            return builder.toString();
        }

    }

    private final BindRequest request;
    private final ConnectionFactory parentFactory;
    private boolean allowRebinds;

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
    public AuthenticatedConnectionFactory(final ConnectionFactory factory, final BindRequest request) {
        Reject.ifNull(factory, request);
        this.parentFactory = factory;

        // FIXME: should do a defensive copy.
        this.request = request;
    }

    @Override
    public void close() {
        parentFactory.close();
    }

    /** {@inheritDoc} */
    @Override
    public Connection getConnection() throws LdapException {
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

    /** {@inheritDoc} */
    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        return parentFactory.getConnectionAsync()
            .thenAsync(
                    new AsyncFunction<Connection, BindResult, LdapException>() {
                        @Override
                        public Promise<BindResult, LdapException> apply(final Connection connection)
                                throws LdapException {
                            connectionHolder.set(connection);
                            return connection.bindAsync(request);
                        }
                    }
            ).then(
                    new Function<BindResult, Connection, LdapException>() {
                        @Override
                        public Connection apply(BindResult result) throws LdapException {
                            // FIXME: should make the result unmodifiable.
                            return new AuthenticatedConnection(connectionHolder.get(), request, result);
                        }
                    },
                    new Function<LdapException, Connection, LdapException>() {
                        @Override
                        public Connection apply(LdapException errorResult) throws LdapException {
                            closeSilently(connectionHolder.get());
                            throw errorResult;
                        }
                    }
            );
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

    /**
     * Returns the string representation of this authenticated connection factory.
     *
     * @return The string representation of this authenticated connection factory.
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AuthenticatedConnectionFactory(");
        builder.append(parentFactory);
        builder.append(')');
        return builder.toString();
    }

}
