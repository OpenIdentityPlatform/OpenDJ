/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.forgerock.opendj.ldap.RequestHandlerFactoryAdapter.adaptRequestHandler;
import static org.forgerock.util.time.Duration.duration;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.RequestLoadBalancer.PartitionedRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CRAMMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.DigestMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.GSSAPISASLBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.PasswordModifyExtendedRequest;
import org.forgerock.opendj.ldap.requests.PlainSASLBindRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.util.Function;
import org.forgerock.util.Option;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;

import com.forgerock.opendj.ldap.CoreMessages;
import com.forgerock.opendj.ldap.controls.AffinityControl;

/**
 * This class contains methods for creating and manipulating connection
 * factories and connections.
 */
public final class Connections {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * Specifies the interval between successive attempts to reconnect to offline load-balanced connection factories.
     * The default configuration is to attempt to reconnect every second.
     */
    public static final Option<Duration> LOAD_BALANCER_MONITORING_INTERVAL = Option.withDefault(duration("1 seconds"));

    /**
     * Specifies the event listener which should be notified whenever a load-balanced connection factory changes state
     * from online to offline or vice-versa. By default events will be logged to the {@code LoadBalancingAlgorithm}
     * logger using the {@link LoadBalancerEventListener#LOG_EVENTS} listener.
     */
    public static final Option<LoadBalancerEventListener> LOAD_BALANCER_EVENT_LISTENER =
            Option.of(LoadBalancerEventListener.class, LoadBalancerEventListener.LOG_EVENTS);

    /**
     * Specifies the scheduler which will be used for periodically reconnecting to offline connection factories. A
     * system-wide scheduler will be used by default.
     */
    public static final Option<ScheduledExecutorService> LOAD_BALANCER_SCHEDULER =
            Option.of(ScheduledExecutorService.class, null);

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
     * Creates a new "round-robin" load-balancer which will load-balance connections across the provided set of
     * connection factories. A round robin load balancing algorithm distributes connection requests across a list of
     * connection factories one at a time. When the end of the list is reached, the algorithm starts again from the
     * beginning.
     * <p/>
     * This algorithm is typically used for load-balancing <i>within</i> data centers, where load must be distributed
     * equally across multiple data sources. This algorithm contrasts with the
     * {@link #newFailoverLoadBalancer(Collection, Options)} which is used for load-balancing <i>between</i> data
     * centers.
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
     *         This configuration options for the load-balancer.
     * @return The new round-robin load balancer.
     * @see #newAffinityRequestLoadBalancer(Collection, Options)
     * @see #newFailoverLoadBalancer(Collection, Options)
     * @see #newLeastRequestsLoadBalancer(Collection, Options)
     * @see #LOAD_BALANCER_EVENT_LISTENER
     * @see #LOAD_BALANCER_MONITORING_INTERVAL
     * @see #LOAD_BALANCER_SCHEDULER
     */
    public static ConnectionFactory newRoundRobinLoadBalancer(
            final Collection<? extends ConnectionFactory> factories, final Options options) {
        return new ConnectionLoadBalancer("RoundRobinLoadBalancer", factories, options) {
            private final int maxIndex = factories.size();
            private final AtomicInteger nextIndex = new AtomicInteger(-1);

            @Override
            int getInitialConnectionFactoryIndex() {
                // A round robin pool of one connection factories is unlikely in
                // practice and requires special treatment.
                if (maxIndex == 1) {
                    return 0;
                }

                // Determine the next factory to use: avoid blocking algorithm.
                int oldNextIndex;
                int newNextIndex;
                do {
                    oldNextIndex = nextIndex.get();
                    newNextIndex = oldNextIndex + 1;
                    if (newNextIndex == maxIndex) {
                        newNextIndex = 0;
                    }
                } while (!nextIndex.compareAndSet(oldNextIndex, newNextIndex));

                // There's a potential, but benign, race condition here: other threads
                // could jump in and rotate through the list before we return the
                // connection factory.
                return newNextIndex;
            }
        };
    }

    /**
     * Creates a new "fail-over" load-balancer which will load-balance connections across the provided set of connection
     * factories. A fail-over load balancing algorithm provides fault tolerance across multiple underlying connection
     * factories.
     * <p/>
     * This algorithm is typically used for load-balancing <i>between</i> data centers, where there is preference to
     * always forward connection requests to the <i>closest available</i> data center. This algorithm contrasts with the
     * {@link #newRoundRobinLoadBalancer(Collection, Options)} which is used for load-balancing <i>within</i> a data
     * center.
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
     *         This configuration options for the load-balancer.
     * @return The new fail-over load balancer.
     * @see #newRoundRobinLoadBalancer(Collection, Options)
     * @see #newAffinityRequestLoadBalancer(Collection, Options)
     * @see #newLeastRequestsLoadBalancer(Collection, Options)
     * @see #LOAD_BALANCER_EVENT_LISTENER
     * @see #LOAD_BALANCER_MONITORING_INTERVAL
     * @see #LOAD_BALANCER_SCHEDULER
     */
    public static ConnectionFactory newFailoverLoadBalancer(
            final Collection<? extends ConnectionFactory> factories, final Options options) {
        return new ConnectionLoadBalancer("FailoverLoadBalancer", factories, options) {
            @Override
            int getInitialConnectionFactoryIndex() {
                // Always start with the first connection factory.
                return 0;
            }
        };
    }

    /**
     * Creates a new "affinity" load-balancer which will load-balance individual requests across the provided set of
     * connection factories, each typically representing a single replica, using an algorithm that ensures that requests
     * targeting a given DN will always be routed to the same replica. In other words, this load-balancer increases
     * consistency whilst maintaining read-scalability by simulating a "single master" replication topology, where each
     * replica is responsible for a subset of the entries. When a replica is unavailable the load-balancer "fails over"
     * by performing a linear probe in order to find the next available replica thus ensuring high-availability when a
     * network partition occurs while sacrificing consistency, since the unavailable replica may still be visible to
     * other clients.
     * <p/>
     * This load-balancer distributes requests based on the hash of their target DN and handles all core operations, as
     * well as any password modify extended requests and SASL bind requests which use authentication IDs having the
     * "dn:" form. Note that subtree operations (searches, subtree deletes, and modify DN) are likely to include entries
     * which are "mastered" on different replicas, so client applications should be more tolerant of inconsistencies.
     * Requests that are either unrecognized or that do not have a parameter that may be considered to be a target DN
     * will be routed randomly.
     * <p/>
     * <b>NOTE:</b> this connection factory returns fake connections, since real connections are obtained for each
     * request. Therefore, the returned fake connections have certain limitations: abandon requests will be ignored
     * since they cannot be routed; connection event listeners can be registered, but will only be notified when the
     * fake connection is closed or when all of the connection factories are unavailable.
     * <p/>
     * <b>NOTE:</b> in deployments where there are multiple client applications, care should be taken to ensure that
     * the factories are configured using the same ordering, otherwise requests will not be routed consistently
     * across the client applications.
     * <p/>
     * The implementation periodically attempts to connect to failed connection factories in order to determine if they
     * have become available again.
     *
     * @param factories
     *         The connection factories.
     * @param options
     *         This configuration options for the load-balancer.
     * @return The new affinity load balancer.
     * @see #newRoundRobinLoadBalancer(Collection, Options)
     * @see #newFailoverLoadBalancer(Collection, Options)
     * @see #newLeastRequestsLoadBalancer(Collection, Options)
     * @see #LOAD_BALANCER_EVENT_LISTENER
     * @see #LOAD_BALANCER_MONITORING_INTERVAL
     * @see #LOAD_BALANCER_SCHEDULER
     */
    public static ConnectionFactory newAffinityRequestLoadBalancer(
            final Collection<? extends ConnectionFactory> factories, final Options options) {
        return new RequestLoadBalancer("AffinityRequestLoadBalancer",
                                       factories,
                                       options,
                                       newAffinityRequestLoadBalancerNextFunction(factories),
                                       NOOP_END_OF_REQUEST_FUNCTION);
    }

    static Function<Request, PartitionedRequest, NeverThrowsException> newAffinityRequestLoadBalancerNextFunction(
            final Collection<? extends ConnectionFactory> factories) {
        return new Function<Request, PartitionedRequest, NeverThrowsException>() {
            private final int maxPartitionId = factories.size();

            @Override
            public PartitionedRequest apply(final Request request) {
                final int partitionId = computePartitionIdFromDN(dnOfRequest(request), maxPartitionId);
                return new PartitionedRequest(request, partitionId);
            }
        };
    }

    /**
     * Returns the partition ID which should be selected based on the provided DN and number of partitions, taking care
     * of negative hash values and especially Integer.MIN_VALUE (see doc for Math.abs()). If the provided DN is
     * {@code null} then a random partition ID will be returned.
     *
     * @param dn
     *         The DN whose partition ID is to be determined, which may be {@code null}.
     * @param numberOfPartitions
     *         The total number of partitions.
     * @return A partition ID in the range 0 <= partitionID < numberOfPartitions.
     */
    private static int computePartitionIdFromDN(final DN dn, final int numberOfPartitions) {
        final int partitionId = dn != null ? dn.hashCode() : ThreadLocalRandom.current().nextInt(0, numberOfPartitions);
        return partitionId == Integer.MIN_VALUE ? 0 : (Math.abs(partitionId) % numberOfPartitions);
    }

    /**
     * Returns the DN of the entry targeted by the provided request, or {@code null} if the target entry cannot be
     * determined. This method will return {@code null} for most extended operations and SASL bind requests which
     * specify a non-DN authorization ID.
     *
     * @param request
     *         The request whose target entry DN is to be determined.
     * @return The DN of the entry targeted by the provided request, or {@code null} if the target entry cannot be
     *         determined.
     */
    static DN dnOfRequest(final Request request) {
        // The following conditions are ordered such that the most common operations appear first in order to
        // reduce the average number of branches. A better solution would be to use a visitor, but a visitor
        // would only apply to the core operations, not extended operations or SASL binds.
        if (request instanceof SearchRequest) {
            return ((SearchRequest) request).getName();
        } else if (request instanceof ModifyRequest) {
            return ((ModifyRequest) request).getName();
        } else if (request instanceof SimpleBindRequest) {
            return dnOf(((SimpleBindRequest) request).getName());
        } else if (request instanceof AddRequest) {
            return ((AddRequest) request).getName();
        } else if (request instanceof DeleteRequest) {
            return ((DeleteRequest) request).getName();
        } else if (request instanceof CompareRequest) {
            return ((CompareRequest) request).getName();
        } else if (request instanceof ModifyDNRequest) {
            return ((ModifyDNRequest) request).getName();
        } else if (request instanceof PasswordModifyExtendedRequest) {
            return dnOfAuthzid(((PasswordModifyExtendedRequest) request).getUserIdentityAsString());
        } else if (request instanceof PlainSASLBindRequest) {
            return dnOfAuthzid(((PlainSASLBindRequest) request).getAuthenticationID());
        } else if (request instanceof DigestMD5SASLBindRequest) {
            return dnOfAuthzid(((DigestMD5SASLBindRequest) request).getAuthenticationID());
        } else if (request instanceof GSSAPISASLBindRequest) {
            return dnOfAuthzid(((GSSAPISASLBindRequest) request).getAuthenticationID());
        } else if (request instanceof CRAMMD5SASLBindRequest) {
            return dnOfAuthzid(((CRAMMD5SASLBindRequest) request).getAuthenticationID());
        } else {
            return null;
        }
    }

    private static DN dnOfAuthzid(final String authzid) {
        if (authzid != null && authzid.startsWith("dn:")) {
            return dnOf(authzid.substring(3));
        }
        return null;
    }

    private static DN dnOf(final String dnString) {
        try {
            return DN.valueOf(dnString);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Creates a distribution load balancer which uses consistent hashing to distributes requests across a set of
     * partitions based on a hash of each request's target DN. More precisely, a partition is selected as follows:
     * <ul>
     * <li>if the targeted entry lies beneath the partition base DN then the partition is selected based on a hash of
     * the DN which is superior to the target DN and immediately subordinate to the partition base DN</li>
     * <li>otherwise, if the request is not a search then the request is routed to a random partition</li>
     * <li>otherwise, if the search request targets the partition base DN, or a superior thereof, then the search is
     * routed to a random partition or broadcast to all partitions, depending on the search scope. When broadcasting,
     * care is taken to re-scope sub-requests in order to avoid returning duplicate entries</li>
     * <li>otherwise, the search is routed to a random partition because its scope lies outside of the partition
     * space.</li>
     * </ul>
     * This load balancer allows client applications to linearly scale their deployment for write throughput as well
     * as total number of entries. For example, if a single replicated topology can support 10000 updates/s and a
     * total of 100M entries, then a 4 way distributed topology could support up to 40000 updates/s and 400M entries.
     * <p/>
     * <b>NOTE:</b> there are a number of assumptions in the design of this load balancer as well as a number of
     * limitations:
     * <ul>
     * <li>simple paged results, server side sorting, and VLV request controls are not supported for searches which
     * traverse all partitions. </li>
     * <li>persistent searches which traverse all partitions are only supported if they request changes only. </li>
     * <li>requests which target an entry which is not below the partition base DN will be routed to a partition
     * selected based on the request's DN, thereby providing affinity. Note that this behavior assumes that entries
     * which are not below the partition base DN are replicated across all partitions.</li>
     * <li>searches that traverse multiple partitions as well as entries above the partition base DN may return
     * results in a non-hierarchical order. Specifically, entries from a partition (below the partition base DN)
     * may be returned before entries above the partition base DN. Although not required by the LDAP standard, some
     * legacy clients expect entries to be returned in hierarchical order.
     * </li>
     * </ul>
     *
     * @param partitionBaseDN
     *         The DN beneath which data is partitioned. All other data is assumed to be shared across all partitions.
     * @param partitions
     *         The consistent hash map containing the partitions to be distributed.
     * @param options
     *         The configuration options for the load-balancer (no options are supported currently).
     * @return The new distribution load balancer.
     */
    @SuppressWarnings("unused")
    public static ConnectionFactory newFixedSizeDistributionLoadBalancer(final DN partitionBaseDN,
            final ConsistentHashMap<? extends ConnectionFactory> partitions, final Options options) {
        return new ConsistentHashDistributionLoadBalancer(partitionBaseDN, partitions);
    }

    /**
     * Creates a new "least requests" load-balancer which will load-balance individual requests across the provided
     * set of connection factories, each typically representing a single replica, using an algorithm that ensures that
     * requests are routed to the replica which has the minimum number of active requests.
     * <p>
     * In other words, this load-balancer provides availability and partition tolerance, but sacrifices consistency.
     * When a replica is not available, its number of active requests will not decrease until the requests time out,
     * which will have the effect of directing requests to the other replicas. Consistency is low compared to the
     * "affinity" load-balancer, because there is no guarantee that requests for the same DN are directed to the same
     * replica.
     * <p/>
     * It is possible to increase consistency by providing a {@link AffinityControl} with a
     * request. The control value will then be used to compute a hash that will determine the connection to use. In that
     * case, the "least requests" behavior is completely overridden, i.e. the most saturated connection may be chosen
     * depending on the hash value.
     * <p/>
     * <b>NOTE:</b> this connection factory returns fake connections, since real connections are obtained for each
     * request. Therefore, the returned fake connections have certain limitations: abandon requests will be ignored
     * since they cannot be routed; connection event listeners can be registered, but will only be notified when the
     * fake connection is closed or when all of the connection factories are unavailable.
     * <p/>
     * <b>NOTE:</b>Server selection is only based on information which is local to the client application. If other
     * applications are accessing the same servers then their additional load is not taken into account. Therefore,
     * this load balancer is only effective if all client applications access the servers in a similar way.
     * <p/>
     * The implementation periodically attempts to connect to failed connection factories in order to determine if they
     * have become available again.
     *
     * @param factories
     *            The connection factories.
     * @param options
     *            This configuration options for the load-balancer.
     * @return The new least requests load balancer.
     * @see #newRoundRobinLoadBalancer(Collection, Options)
     * @see #newFailoverLoadBalancer(Collection, Options)
     * @see #newAffinityRequestLoadBalancer(Collection, Options)
     * @see #LOAD_BALANCER_EVENT_LISTENER
     * @see #LOAD_BALANCER_MONITORING_INTERVAL
     * @see #LOAD_BALANCER_SCHEDULER
     */
    public static ConnectionFactory newLeastRequestsLoadBalancer(
            final Collection<? extends ConnectionFactory> factories, final Options options) {
        final LeastRequestsDispatcher dispatcher = new LeastRequestsDispatcher(factories.size());
        return new RequestLoadBalancer("SaturationBasedRequestLoadBalancer", factories, options,
                newLeastRequestsLoadBalancerNextFunction(dispatcher),
                newLeastRequestsLoadBalancerEndOfRequestFunction(dispatcher));
    }

    private static final DecodeOptions CONTROL_DECODE_OPTIONS = new DecodeOptions();

    static Function<Request, PartitionedRequest, NeverThrowsException> newLeastRequestsLoadBalancerNextFunction(
            final LeastRequestsDispatcher dispatcher) {
        return new Function<Request, PartitionedRequest, NeverThrowsException>() {
            private final int maxIndex = dispatcher.size();

            @Override
            public PartitionedRequest apply(final Request request) {
                int affinityBasedIndex = parseAffinityRequestControl(request);
                int finalIndex = dispatcher.selectServer(affinityBasedIndex);
                Request cleanedRequest = (affinityBasedIndex == -1)
                        ? request : Requests.shallowCopyOfRequest(request, AffinityControl.OID);
                return new PartitionedRequest(cleanedRequest, finalIndex);
            }

            private int parseAffinityRequestControl(final Request request) {
                try {
                    AffinityControl control = request.getControl(AffinityControl.DECODER, CONTROL_DECODE_OPTIONS);
                    if (control != null) {
                        int index = control.getAffinityValue().hashCode();
                        return index == Integer.MIN_VALUE ? 0 : (Math.abs(index) % maxIndex);
                    }
                } catch (DecodeException e) {
                    logger.warn(CoreMessages.WARN_DECODING_AFFINITY_CONTROL.get(e.getMessage()));
                }
                return -1;
            }
        };
    }

    static Function<Integer, Void, NeverThrowsException> newLeastRequestsLoadBalancerEndOfRequestFunction(
            final LeastRequestsDispatcher dispatcher) {
        return new Function<Integer, Void, NeverThrowsException>() {
            @Override
            public Void apply(final Integer index) {
                dispatcher.terminatedRequest(index);
                return null;
            }
        };
    }

    /** No-op "end of request" function for the saturation-based request load balancer. */
    static final Function<Integer, Void, NeverThrowsException> NOOP_END_OF_REQUEST_FUNCTION =
            new Function<Integer, Void, NeverThrowsException>() {
                @Override
                public Void apply(Integer index) {
                    return null;
                }
            };

    /**
     * Dispatch requests to the server index which has the least active requests.
     * <p>
     * A server is actually represented only by its index. Provided an initial number of servers, the requests are
     * dispatched to the less saturated index, i.e. which corresponds to the server that has the lowest number of active
     * requests.
     */
    static class LeastRequestsDispatcher {
        /** Counter for each server. */
        private final AtomicLongArray serversCounters;

        LeastRequestsDispatcher(int numberOfServers) {
            serversCounters = new AtomicLongArray(numberOfServers);
        }

        int size() {
            return serversCounters.length();
        }

        /**
         * Returns the server index to use.
         *
         * @param forceIndex
         *            Forces a server index to use if different from -1. In that case, the default behavior of the
         *            dispatcher is overridden. If -1 is provided, then the default behavior of the dispatcher applies.
         * @return the server index
         */
        int selectServer(int forceIndex) {
            int index = forceIndex == -1 ? getLessSaturatedIndex() : forceIndex;
            serversCounters.incrementAndGet(index);
            return index;
        }

        /**
         * Signals to this dispatcher that a request has been finished for the provided server index.
         *
         * @param index
         *            The index of server that processed the request.
         */
        void terminatedRequest(Integer index) {
            serversCounters.decrementAndGet(index);
        }

        private int getLessSaturatedIndex() {
            long min = Long.MAX_VALUE;
            int minIndex = -1;
            // Modifications during this loop are ok, effects on result should not be dramatic
            for (int i = 0; i < serversCounters.length(); i++) {
                long count = serversCounters.get(i);
                if (count < min) {
                    min = count;
                    minIndex = i;
                }
            }
            return minIndex;
        }
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
     * for detecting whether the request should be aborted due to
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
     * for detecting whether the request should be aborted due to
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
