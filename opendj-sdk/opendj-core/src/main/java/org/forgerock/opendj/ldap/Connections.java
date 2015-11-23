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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import static org.forgerock.opendj.ldap.RequestHandlerFactoryAdapter.adaptRequestHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.Promise;

/**
 * This class contains methods for creating and manipulating connection
 * factories and connections.
 */
public final class Connections {
    /**
     * Creates a new connection pool which creates new connections as needed
     * using the provided connection factory, but will reuse previously
     * allocated connections when they are available.
     * <p>
     * Connections which have not been used for sixty seconds are closed and
     * removed from the pool. Thus, a pool that remains idle for long enough
     * will not contain any cached connections.
     * <p>
     * Connections obtained from the connection pool are guaranteed to be valid
     * immediately before being returned to the calling application. More
     * specifically, connections which have remained idle in the connection pool
     * for a long time and which have been remotely closed due to a time out
     * will never be returned. However, once a pooled connection has been
     * obtained it is the responsibility of the calling application to handle
     * subsequent connection failures, these being signaled via a
     * {@link ConnectionException}.
     *
     * @param factory
     *            The connection factory to use for creating new connections.
     * @return The new connection pool.
     * @throws NullPointerException
     *             If {@code factory} was {@code null}.
     */
    public static ConnectionPool newCachedConnectionPool(final ConnectionFactory factory) {
        return new CachedConnectionPool(factory, 0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, null);
    }

    /**
     * Creates a new connection pool which creates new connections as needed
     * using the provided connection factory, but will reuse previously
     * allocated connections when they are available.
     * <p>
     * Attempts to use more than {@code maximumPoolSize} connections at once
     * will block until a connection is released back to the pool. In other
     * words, this pool will prevent applications from using more than
     * {@code maximumPoolSize} connections at the same time.
     * <p>
     * Connections which have not been used for the provided {@code idleTimeout}
     * period are closed and removed from the pool, until there are only
     * {@code corePoolSize} connections remaining.
     * <p>
     * Connections obtained from the connection pool are guaranteed to be valid
     * immediately before being returned to the calling application. More
     * specifically, connections which have remained idle in the connection pool
     * for a long time and which have been remotely closed due to a time out
     * will never be returned. However, once a pooled connection has been
     * obtained it is the responsibility of the calling application to handle
     * subsequent connection failures, these being signaled via a
     * {@link ConnectionException}.
     *
     * @param factory
     *            The connection factory to use for creating new connections.
     * @param corePoolSize
     *            The minimum number of connections to keep in the pool, even if
     *            they are idle.
     * @param maximumPoolSize
     *            The maximum number of connections to allow in the pool.
     * @param idleTimeout
     *            The time out period, after which unused non-core connections
     *            will be closed.
     * @param unit
     *            The time unit for the {@code keepAliveTime} argument.
     * @return The new connection pool.
     * @throws IllegalArgumentException
     *             If {@code corePoolSize}, {@code maximumPoolSize} are less
     *             than or equal to zero, or if {@code idleTimeout} is negative,
     *             or if {@code corePoolSize} is greater than
     *             {@code maximumPoolSize}, or if {@code idleTimeout} is
     *             non-zero and {@code unit} is {@code null}.
     * @throws NullPointerException
     *             If {@code factory} was {@code null}.
     */
    public static ConnectionPool newCachedConnectionPool(final ConnectionFactory factory,
            final int corePoolSize, final int maximumPoolSize, final long idleTimeout,
            final TimeUnit unit) {
        return new CachedConnectionPool(factory, corePoolSize, maximumPoolSize, idleTimeout, unit,
                null);
    }

    /**
     * Creates a new connection pool which creates new connections as needed
     * using the provided connection factory, but will reuse previously
     * allocated connections when they are available.
     * <p>
     * Attempts to use more than {@code maximumPoolSize} connections at once
     * will block until a connection is released back to the pool. In other
     * words, this pool will prevent applications from using more than
     * {@code maximumPoolSize} connections at the same time.
     * <p>
     * Connections which have not been used for the provided {@code idleTimeout}
     * period are closed and removed from the pool, until there are only
     * {@code corePoolSize} connections remaining.
     * <p>
     * Connections obtained from the connection pool are guaranteed to be valid
     * immediately before being returned to the calling application. More
     * specifically, connections which have remained idle in the connection pool
     * for a long time and which have been remotely closed due to a time out
     * will never be returned. However, once a pooled connection has been
     * obtained it is the responsibility of the calling application to handle
     * subsequent connection failures, these being signaled via a
     * {@link ConnectionException}.
     *
     * @param factory
     *            The connection factory to use for creating new connections.
     * @param corePoolSize
     *            The minimum number of connections to keep in the pool, even if
     *            they are idle.
     * @param maximumPoolSize
     *            The maximum number of connections to allow in the pool.
     * @param idleTimeout
     *            The time out period, after which unused non-core connections
     *            will be closed.
     * @param unit
     *            The time unit for the {@code keepAliveTime} argument.
     * @param scheduler
     *            The scheduler which should be used for periodically checking
     *            for idle connections, or {@code null} if the default scheduler
     *            should be used.
     * @return The new connection pool.
     * @throws IllegalArgumentException
     *             If {@code corePoolSize}, {@code maximumPoolSize} are less
     *             than or equal to zero, or if {@code idleTimeout} is negative,
     *             or if {@code corePoolSize} is greater than
     *             {@code maximumPoolSize}, or if {@code idleTimeout} is
     *             non-zero and {@code unit} is {@code null}.
     * @throws NullPointerException
     *             If {@code factory} was {@code null}.
     */
    public static ConnectionPool newCachedConnectionPool(final ConnectionFactory factory,
            final int corePoolSize, final int maximumPoolSize, final long idleTimeout,
            final TimeUnit unit, final ScheduledExecutorService scheduler) {
        return new CachedConnectionPool(factory, corePoolSize, maximumPoolSize, idleTimeout, unit,
                scheduler);
    }

    /**
     * Creates a new connection pool which will maintain {@code poolSize}
     * connections created using the provided connection factory.
     * <p>
     * Attempts to use more than {@code poolSize} connections at once will block
     * until a connection is released back to the pool. In other words, this
     * pool will prevent applications from using more than {@code poolSize}
     * connections at the same time.
     * <p>
     * Connections obtained from the connection pool are guaranteed to be valid
     * immediately before being returned to the calling application. More
     * specifically, connections which have remained idle in the connection pool
     * for a long time and which have been remotely closed due to a time out
     * will never be returned. However, once a pooled connection has been
     * obtained it is the responsibility of the calling application to handle
     * subsequent connection failures, these being signaled via a
     * {@link ConnectionException}.
     *
     * @param factory
     *            The connection factory to use for creating new connections.
     * @param poolSize
     *            The maximum size of the connection pool.
     * @return The new connection pool.
     * @throws IllegalArgumentException
     *             If {@code poolSize} is negative.
     * @throws NullPointerException
     *             If {@code factory} was {@code null}.
     */
    public static ConnectionPool newFixedConnectionPool(final ConnectionFactory factory,
            final int poolSize) {
        return new CachedConnectionPool(factory, poolSize, poolSize, 0L, null, null);
    }

    /**
     * Creates a new internal client connection which will route requests to the
     * provided {@code RequestHandler}.
     * <p>
     * When processing requests, {@code RequestHandler} implementations are
     * passed a {@code RequestContext} having a pseudo {@code requestID} which
     * is incremented for each successive internal request on a per client
     * connection basis. The request ID may be useful for logging purposes.
     * <p>
     * An internal connection does not require {@code RequestHandler}
     * implementations to return a result when processing requests. However, it
     * is recommended that implementations do always return results even for
     * abandoned requests. This is because application client threads may block
     * indefinitely waiting for results.
     *
     * @param requestHandler
     *            The request handler which will be used for all client
     *            connections.
     * @return The new internal connection.
     * @throws NullPointerException
     *             If {@code requestHandler} was {@code null}.
     */
    public static Connection newInternalConnection(
            final RequestHandler<RequestContext> requestHandler) {
        Reject.ifNull(requestHandler);
        return newInternalConnection(adaptRequestHandler(requestHandler));
    }

    /**
     * Creates a new internal client connection which will route requests to the
     * provided {@code ServerConnection}.
     * <p>
     * When processing requests, {@code ServerConnection} implementations are
     * passed an integer as the first parameter. This integer represents a
     * pseudo {@code requestID} which is incremented for each successive
     * internal request on a per client connection basis. The request ID may be
     * useful for logging purposes.
     * <p>
     * An internal connection does not require {@code ServerConnection}
     * implementations to return a result when processing requests. However, it
     * is recommended that implementations do always return results even for
     * abandoned requests. This is because application client threads may block
     * indefinitely waiting for results.
     *
     * @param serverConnection
     *            The server connection.
     * @return The new internal connection.
     * @throws NullPointerException
     *             If {@code serverConnection} was {@code null}.
     */
    public static Connection newInternalConnection(final ServerConnection<Integer> serverConnection) {
        Reject.ifNull(serverConnection);
        return new InternalConnection(serverConnection);
    }

    /**
     * Creates a new connection factory which binds internal client connections
     * to the provided {@link RequestHandler}s.
     * <p>
     * When processing requests, {@code RequestHandler} implementations are
     * passed an integer as the first parameter. This integer represents a
     * pseudo {@code requestID} which is incremented for each successive
     * internal request on a per client connection basis. The request ID may be
     * useful for logging purposes.
     * <p>
     * An internal connection factory does not require {@code RequestHandler}
     * implementations to return a result when processing requests. However, it
     * is recommended that implementations do always return results even for
     * abandoned requests. This is because application client threads may block
     * indefinitely waiting for results.
     *
     * @param requestHandler
     *            The request handler which will be used for all client
     *            connections.
     * @return The new internal connection factory.
     * @throws NullPointerException
     *             If {@code requestHandler} was {@code null}.
     */
    public static ConnectionFactory newInternalConnectionFactory(
            final RequestHandler<RequestContext> requestHandler) {
        Reject.ifNull(requestHandler);
        return new InternalConnectionFactory<>(
            Connections.<Void> newServerConnectionFactory(requestHandler), null);
    }

    /**
     * Creates a new connection factory which binds internal client connections
     * to {@link RequestHandler}s created using the provided
     * {@link RequestHandlerFactory}.
     * <p>
     * When processing requests, {@code RequestHandler} implementations are
     * passed an integer as the first parameter. This integer represents a
     * pseudo {@code requestID} which is incremented for each successive
     * internal request on a per client connection basis. The request ID may be
     * useful for logging purposes.
     * <p>
     * An internal connection factory does not require {@code RequestHandler}
     * implementations to return a result when processing requests. However, it
     * is recommended that implementations do always return results even for
     * abandoned requests. This is because application client threads may block
     * indefinitely waiting for results.
     *
     * @param <C>
     *            The type of client context.
     * @param factory
     *            The request handler factory to use for creating connections.
     * @param clientContext
     *            The client context.
     * @return The new internal connection factory.
     * @throws NullPointerException
     *             If {@code factory} was {@code null}.
     */
    public static <C> ConnectionFactory newInternalConnectionFactory(
            final RequestHandlerFactory<C, RequestContext> factory, final C clientContext) {
        Reject.ifNull(factory);
        return new InternalConnectionFactory<>(newServerConnectionFactory(factory), clientContext);
    }

    /**
     * Creates a new connection factory which binds internal client connections
     * to {@link ServerConnection}s created using the provided
     * {@link ServerConnectionFactory}.
     * <p>
     * When processing requests, {@code ServerConnection} implementations are
     * passed an integer as the first parameter. This integer represents a
     * pseudo {@code requestID} which is incremented for each successive
     * internal request on a per client connection basis. The request ID may be
     * useful for logging purposes.
     * <p>
     * An internal connection factory does not require {@code ServerConnection}
     * implementations to return a result when processing requests. However, it
     * is recommended that implementations do always return results even for
     * abandoned requests. This is because application client threads may block
     * indefinitely waiting for results.
     *
     * @param <C>
     *            The type of client context.
     * @param factory
     *            The server connection factory to use for creating connections.
     * @param clientContext
     *            The client context.
     * @return The new internal connection factory.
     * @throws NullPointerException
     *             If {@code factory} was {@code null}.
     */
    public static <C> ConnectionFactory newInternalConnectionFactory(
            final ServerConnectionFactory<C, Integer> factory, final C clientContext) {
        Reject.ifNull(factory);
        return new InternalConnectionFactory<>(factory, clientContext);
    }

    /**
     * Creates a new "round-robin" load-balance which will load-balance connections across the provided set of
     * connection factories. A round robin load balancing algorithm distributes connection requests across a list of
     * connection factories one at a time. When the end of the list is reached, the algorithm starts again from the
     * beginning.
     * <p/>
     * This algorithm is typically used for load-balancing <i>within</i> data centers, where load must be distributed
     * equally across multiple data sources. This algorithm contrasts with the {@link FailoverLoadBalancingAlgorithm}
     * which is used for load-balancing <i>between</i> data centers.
     * <p/>
     * If a problem occurs that temporarily prevents connections from being obtained for one of the connection
     * factories, then this algorithm automatically "fails over" to the next operational connection factory in the list.
     * If none of the connection factories are operational then a {@code ConnectionException} is returned to the
     * client.
     * <p/>
     * The implementation periodically attempts to connect to failed connection factories in order to determine if they
     * have become available again.
     *
     * @param factories
     *         The connection factories.
     * @param options
     *         This configuration options for the load-balancer. See {@link LoadBalancingAlgorithm} for common options.
     * @return The new round-robin load balancer.
     * @see #newFailoverLoadBalancer(Collection, Options)
     * @see LoadBalancingAlgorithm
     */
    public static ConnectionFactory newRoundRobinLoadBalancer(
            final Collection<? extends ConnectionFactory> factories, final Options options) {
        return new LoadBalancer(new RoundRobinLoadBalancingAlgorithm(factories, options));
    }

    /**
     * Creates a new "fail-over" load-balance which will load-balance connections across the provided set of connection
     * factories. A fail-over load balancing algorithm provides fault tolerance across multiple underlying connection
     * factories.
     * <p/>
     * This algorithm is typically used for load-balancing <i>between</i> data centers, where there is preference to
     * always forward connection requests to the <i>closest available</i> data center. This algorithm contrasts with the
     * {@link RoundRobinLoadBalancingAlgorithm} which is used for load-balancing <i>within</i> a data center.
     * <p/>
     * This algorithm selects connection factories based on the order in which they were provided during construction.
     * More specifically, an attempt to obtain a connection factory will always return the <i>first operational</i>
     * connection factory in the list. Applications should, therefore, organize the connection factories such that the
     * <i>preferred</i> (usually the closest) connection factories appear before those which are less preferred.
     * <p/>
     * If a problem occurs that temporarily prevents connections from being obtained for one of the connection
     * factories, then this algorithm automatically "fails over" to the next operational connection factory in the list.
     * If none of the connection factories are operational then a {@code ConnectionException} is returned to the
     * client.
     * <p/>
     * The implementation periodically attempts to connect to failed connection factories in order to determine if they
     * have become available again.
     *
     * @param factories
     *         The connection factories.
     * @param options
     *         This configuration options for the load-balancer. See {@link LoadBalancingAlgorithm} for common options.
     * @return The new fail-over load balancer.
     * @see #newRoundRobinLoadBalancer(Collection, Options)
     * @see LoadBalancingAlgorithm
     */
    public static ConnectionFactory newFailoverLoadBalancer(
            final Collection<? extends ConnectionFactory> factories, final Options options) {
        return new LoadBalancer(new FailoverLoadBalancingAlgorithm(factories, options));
    }

    /**
     * Creates a new load balancer which will obtain connections using the provided load balancing algorithm.
     *
     * @param algorithm
     *         The load balancing algorithm which will be used to obtain the next
     * @return The new load balancer.
     * @throws NullPointerException
     *         If {@code algorithm} was {@code null}.
     */
    public static ConnectionFactory newLoadBalancer(final LoadBalancingAlgorithm algorithm) {
        return new LoadBalancer(algorithm);
    }

    /**
     * Creates a new connection factory which forwards connection requests to
     * the provided factory, but whose {@code toString} method will always
     * return {@code name}.
     * <p>
     * This method may be useful for debugging purposes in order to more easily
     * identity connection factories.
     *
     * @param factory
     *            The connection factory to be named.
     * @param name
     *            The name of the connection factory.
     * @return The named connection factory.
     * @throws NullPointerException
     *             If {@code factory} or {@code name} was {@code null}.
     */
    public static ConnectionFactory newNamedConnectionFactory(final ConnectionFactory factory,
            final String name) {
        Reject.ifNull(factory, name);

        return new ConnectionFactory() {

            @Override
            public void close() {
                factory.close();
            }

            @Override
            public Connection getConnection() throws LdapException {
                return factory.getConnection();
            }

            @Override
            public Promise<Connection, LdapException> getConnectionAsync() {
                return factory.getConnectionAsync();
            }

            @Override
            public String toString() {
                return name;
            }

        };
    }

    /**
     * Creates a new server connection factory using the provided
     * {@link RequestHandler}. The returned factory will manage connection and
     * request life-cycle, including request cancellation.
     * <p>
     * When processing requests, {@link RequestHandler} implementations are
     * passed a {@link RequestContext} as the first parameter which may be used
     * for detecting whether or not the request should be aborted due to
     * cancellation requests or other events, such as connection failure.
     * <p>
     * The returned factory maintains state information which includes a table
     * of active requests. Therefore, {@code RequestHandler} implementations are
     * required to always return results in order to avoid potential memory
     * leaks.
     *
     * @param <C>
     *            The type of client context.
     * @param requestHandler
     *            The request handler which will be used for all client
     *            connections.
     * @return The new server connection factory.
     * @throws NullPointerException
     *             If {@code requestHandler} was {@code null}.
     */
    public static <C> ServerConnectionFactory<C, Integer> newServerConnectionFactory(
            final RequestHandler<RequestContext> requestHandler) {
        Reject.ifNull(requestHandler);
        return new RequestHandlerFactoryAdapter<>(new RequestHandlerFactory<C, RequestContext>() {
            @Override
            public RequestHandler<RequestContext> handleAccept(final C clientContext) {
                return requestHandler;
            }
        });
    }

    /**
     * Creates a new server connection factory using the provided
     * {@link RequestHandlerFactory}. The returned factory will manage
     * connection and request life-cycle, including request cancellation.
     * <p>
     * When processing requests, {@link RequestHandler} implementations are
     * passed a {@link RequestContext} as the first parameter which may be used
     * for detecting whether or not the request should be aborted due to
     * cancellation requests or other events, such as connection failure.
     * <p>
     * The returned factory maintains state information which includes a table
     * of active requests. Therefore, {@code RequestHandler} implementations are
     * required to always return results in order to avoid potential memory
     * leaks.
     *
     * @param <C>
     *            The type of client context.
     * @param factory
     *            The request handler factory to use for associating request
     *            handlers with client connections.
     * @return The new server connection factory.
     * @throws NullPointerException
     *             If {@code factory} was {@code null}.
     */
    public static <C> ServerConnectionFactory<C, Integer> newServerConnectionFactory(
            final RequestHandlerFactory<C, RequestContext> factory) {
        Reject.ifNull(factory);
        return new RequestHandlerFactoryAdapter<>(factory);
    }

    /**
     * Returns an uncloseable view of the provided connection. Attempts to call
     * {@link Connection#close()} or
     * {@link Connection#close(org.forgerock.opendj.ldap.requests.UnbindRequest, String)}
     * will be ignored.
     *
     * @param connection
     *            The connection whose {@code close} methods are to be disabled.
     * @return An uncloseable view of the provided connection.
     */
    public static Connection uncloseable(Connection connection) {
        return new AbstractConnectionWrapper<Connection>(connection) {
            @Override
            public void close() {
                // Do nothing.
            }

            @Override
            public void close(org.forgerock.opendj.ldap.requests.UnbindRequest request,
                    String reason) {
                // Do nothing.
            }
        };
    }

    /**
     * Returns an uncloseable view of the provided connection factory. Attempts
     * to call {@link ConnectionFactory#close()} will be ignored.
     *
     * @param factory
     *            The connection factory whose {@code close} method is to be
     *            disabled.
     * @return An uncloseable view of the provided connection factory.
     */
    public static ConnectionFactory uncloseable(final ConnectionFactory factory) {
        return new ConnectionFactory() {

            @Override
            public Promise<Connection, LdapException> getConnectionAsync() {
                return factory.getConnectionAsync();
            }

            @Override
            public Connection getConnection() throws LdapException {
                return factory.getConnection();
            }

            @Override
            public void close() {
                // Do nothing.
            }
        };
    }

    /**
     * Returns the host name associated with the provided
     * {@code InetSocketAddress}, without performing a DNS lookup. This method
     * attempts to provide functionality which is compatible with
     * {@code InetSocketAddress.getHostString()} in JDK7. It can be removed once
     * we drop support for JDK6.
     *
     * @param socketAddress
     *            The socket address which is expected to be an instance of
     *            {@code InetSocketAddress}.
     * @return The host name associated with the provided {@code SocketAddress},
     *         or {@code null} if it is unknown.
     */
    public static String getHostString(final InetSocketAddress socketAddress) {
        /*
         * See OPENDJ-1270 for more information about slow DNS queries.
         *
         * We must avoid calling getHostName() in the case where it is likely to
         * perform a blocking DNS query. Ideally we would call getHostString(),
         * but the method was only added in JDK7.
         */
        if (socketAddress.isUnresolved()) {
            /*
             * Usually socket addresses are resolved on creation. If the address
             * is unresolved then there must be a user provided hostname instead
             * and getHostName will not perform a reverse lookup.
             */
            return socketAddress.getHostName();
        } else {
            /*
             * Simulate getHostString() by parsing the toString()
             * representation. This assumes that the toString() representation
             * is stable, which I assume it is because it is documented.
             */
            final InetAddress address = socketAddress.getAddress();
            final String hostSlashIp = address.toString();
            final int slashPos = hostSlashIp.indexOf('/');
            if (slashPos == 0) {
                return hostSlashIp.substring(1);
            } else {
                return hostSlashIp.substring(0, slashPos);
            }
        }
    }

    /** Prevent instantiation. */
    private Connections() {
        // Do nothing.
    }
}
