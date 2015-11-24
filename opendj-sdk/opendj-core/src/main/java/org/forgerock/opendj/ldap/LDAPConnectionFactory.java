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

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.HBCF_CONNECTION_CLOSED_BY_CLIENT;
import static com.forgerock.opendj.ldap.CoreMessages.HBCF_HEARTBEAT_FAILED;
import static com.forgerock.opendj.ldap.CoreMessages.HBCF_HEARTBEAT_TIMEOUT;
import static com.forgerock.opendj.ldap.CoreMessages.LDAP_CONNECTION_CONNECT_TIMEOUT;
import static com.forgerock.opendj.util.StaticUtils.DEFAULT_SCHEDULER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newStartTLSExtendedRequest;
import static org.forgerock.opendj.ldap.requests.Requests.unmodifiableSearchRequest;
import static org.forgerock.opendj.ldap.responses.Responses.newBindResult;
import static org.forgerock.opendj.ldap.responses.Responses.newGenericExtendedResult;
import static org.forgerock.opendj.ldap.responses.Responses.newResult;
import static org.forgerock.opendj.ldap.spi.LdapPromiseImpl.newLdapPromiseImpl;
import static org.forgerock.opendj.ldap.spi.LdapPromises.newFailedLdapPromise;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import javax.net.ssl.SSLContext;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.spi.ConnectionState;
import org.forgerock.opendj.ldap.spi.LDAPConnectionFactoryImpl;
import org.forgerock.opendj.ldap.spi.LDAPConnectionImpl;
import org.forgerock.opendj.ldap.spi.LdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.TransportProvider;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.Option;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

import com.forgerock.opendj.util.ReferenceCountedObject;

/**
 * A factory class which can be used to obtain connections to an LDAP Directory Server. A connection attempt comprises
 * of the following steps:
 * <ul>
 * <li>first of all a TCP connection to the remote LDAP server is obtained. The attempt will fail if a connection is
 *     not obtained within the configured {@link #CONNECT_TIMEOUT connect timeout}
 * <li>if LDAPS (not StartTLS) is requested then an SSL handshake is performed. LDAPS is enabled by specifying the
 *     {@link #SSL_CONTEXT} option along with {@link #SSL_USE_STARTTLS} set to {@code false}
 * <li>if StartTLS is requested then a StartTLS request is sent and then an SSL handshake performed once the response
 *     has been received. StartTLS is enabled by specifying the {@link #SSL_CONTEXT} option along with
 *     {@link #SSL_USE_STARTTLS} set to {@code true}
 * <li>an initial authentication request is sent if the {@link #AUTHN_BIND_REQUEST} option is specified
 * <li>if heart-beat support is enabled via the {@link #HEARTBEAT_ENABLED} option, and none of steps 2-4 were performed,
 *     then an initial heart-beat is sent in order to determine whether the directory service is available.
 * <li>the connect attempt will fail if it does not complete within the configured connection timeout. If the SSL
 *     handshake, StartTLS request, initial bind request, or initial heart-beat fail for any reason then the connection
 *     attempt will be deemed to have failed and an appropriate error returned.
 * </ul>
 * Once a connection has been established heart-beats will be sent periodically on the connection based on the
 * configured heart-beat interval. If the heart-beat times out then the server is assumed to be down and an appropriate
 * {@link ConnectionException} generated and published to any registered {@link ConnectionEventListener}s. Note
 * however, that heart-beats will only be sent when the connection is determined to be reasonably idle: there is no
 * point in sending heart-beats if the connection has recently received a response. A connection is deemed to be idle
 * if no response has been received during a period equivalent to half the heart-beat interval.
 * <p>
 * The LDAP protocol specifically precludes clients from performing operations while bind or startTLS requests are being
 * performed. Likewise, a bind or startTLS request will cause active operations to be aborted. This factory coordinates
 * heart-beats with bind or startTLS requests, ensuring that they are not performed concurrently. Specifically, bind and
 * startTLS requests are queued up while a heart-beat is pending, and heart-beats are not sent at all while there are
 * pending bind or startTLS requests.
 */
public final class LDAPConnectionFactory extends CommonLDAPOptions implements ConnectionFactory {
    /**
     * Configures the connection factory to return pre-authenticated connections using the specified {@link
     * BindRequest}. The connections returned by the connection factory will support all operations with the exception
     * of Bind requests. Attempts to perform a Bind will result in an {@code UnsupportedOperationException}.
     * <p>
     * If the Bind request fails for some reason (e.g. invalid credentials), then the connection attempt will fail and
     * an {@link LdapException} will be thrown.
     */
    public static final Option<BindRequest> AUTHN_BIND_REQUEST = Option.of(BindRequest.class, null);

    /**
     * Specifies the connect timeout spcified. If a connection is not established within the timeout period (incl. SSL
     * negotiation, initial bind request, and/or heart-beat), then a {@link TimeoutResultException} error result will be
     * returned.
     * <p>
     * The default operation timeout is 10 seconds and may be configured using the {@code
     * org.forgerock.opendj.io.connectTimeout} property. A timeout setting of 0 causes the OS connect timeout to be
     * used.
     */
    public static final Option<Duration> CONNECT_TIMEOUT =
            Option.withDefault(new Duration((long) getIntProperty("org.forgerock.opendj.io.connectTimeout", 10000),
                                            TimeUnit.MILLISECONDS));

    /**
     * Configures the connection factory to periodically send "heart-beat" or "keep-alive" requests to the Directory
     * Server. This feature allows client applications to proactively detect network problems or unresponsive
     * servers. In addition, frequent heartbeat requests may also prevent load-balancers or Directory Servers from
     * closing otherwise idle connections.
     * <p>
     * Before returning new connections to the application the factory will first send an initial heart-beat request in
     * order to determine that the remote server is responsive. If the heart-beat request fails or is too slow to
     * respond then the connection is closed immediately and an error returned to the client.
     * <p>
     * Once a connection has been established successfully (including the initial heart-beat request), the connection
     * factory will periodically send heart-beat requests on the connection based on the configured heart-beat interval.
     * If the Directory Server is too slow to respond to the heart-beat then the server is assumed to be down and an
     * appropriate {@link ConnectionException} generated and published to any registered
     * {@link ConnectionEventListener}s. Note however, that heart-beat requests will only be sent when the connection
     * is determined to be reasonably idle: there is no point in sending heart-beats if the connection has recently
     * received a response. A connection is deemed to be idle if no response has been received during a period
     * equivalent to half the heart-beat interval.
     * <p>
     * The LDAP protocol specifically precludes clients from performing operations while bind or startTLS requests are
     * being performed. Likewise, a bind or startTLS request will cause active operations to be aborted. The LDAP
     * connection factory coordinates heart-beats with bind or startTLS requests, ensuring that they are not performed
     * concurrently. Specifically, bind and startTLS requests are queued up while a heart-beat is pending, and
     * heart-beats are not sent at all while there are pending bind or startTLS requests.
     */
    public static final Option<Boolean> HEARTBEAT_ENABLED = Option.withDefault(false);

    /**
     * Specifies the time between successive heart-beat requests (default interval is 10 seconds). Heart-beats will only
     * be sent if {@link #HEARTBEAT_ENABLED} is set to {@code true}.
     *
     * @see #HEARTBEAT_ENABLED
     */
    public static final Option<Duration> HEARTBEAT_INTERVAL = Option.withDefault(new Duration(10L, SECONDS));

    /**
     * Specifies the scheduler which will be used for periodically sending heart-beat requests. A system-wide scheduler
     * will be used by default. Heart-beats will only be sent if {@link #HEARTBEAT_ENABLED} is set to {@code true}.
     *
     * @see #HEARTBEAT_ENABLED
     */
    public static final Option<ScheduledExecutorService> HEARTBEAT_SCHEDULER =
            Option.of(ScheduledExecutorService.class, null);

    /**
     * Specifies the timeout for heart-beat requests, after which the remote Directory Server will be deemed to be
     * unavailable (default timeout is 3 seconds). Heart-beats will only be sent if {@link #HEARTBEAT_ENABLED} is set to
     * {@code true}. If a {@link #REQUEST_TIMEOUT request timeout} is also set then the lower of the two will be used
     * for sending heart-beats.
     *
     * @see #HEARTBEAT_ENABLED
     */
    public static final Option<Duration> HEARTBEAT_TIMEOUT = Option.withDefault(new Duration(3L, SECONDS));

    /**
     * Specifies the operation timeout. If a response is not received from the Directory Server within the timeout
     * period, then the operation will be abandoned and a {@link TimeoutResultException} error result returned. A
     * timeout setting of 0 disables operation timeout limits.
     * <p>
     * The default operation timeout is 0 (no timeout) and may be configured using the {@code
     * org.forgerock.opendj.io.requestTimeout} property or the deprecated {@code org.forgerock.opendj.io.timeout}
     * property.
     */
    public static final Option<Duration> REQUEST_TIMEOUT =
            Option.withDefault(new Duration((long) getIntProperty("org.forgerock.opendj.io.requestTimeout",
                                                                  getIntProperty("org.forgerock.opendj.io.timeout", 0)),
                                            TimeUnit.MILLISECONDS));

    /**
     * Specifies the SSL context which will be used when initiating connections with the Directory Server.
     * <p>
     * By default no SSL context will be used, indicating that connections will not be secured. If an SSL context is set
     * then connections will be secured using either SSL or StartTLS depending on {@link #SSL_USE_STARTTLS}.
     */
    public static final Option<SSLContext> SSL_CONTEXT = Option.of(SSLContext.class, null);

    /**
     * Specifies the cipher suites enabled for secure connections with the Directory Server.
     * <p>
     * The suites must be supported by the SSLContext specified by option {@link #SSL_CONTEXT}. Only the suites listed
     * in the parameter are enabled for use.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static final Option<List<String>> SSL_ENABLED_CIPHER_SUITES =
            (Option) Option.of(List.class, Collections.<String>emptyList());

    /**
     * Specifies the protocol versions enabled for secure connections with the Directory Server.
     * <p>
     * The protocols must be supported by the SSLContext specified by option {@link #SSL_CONTEXT}. Only the protocols
     * listed in the parameter are enabled for use.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static final Option<List<String>> SSL_ENABLED_PROTOCOLS =
            (Option) Option.of(List.class, Collections.<String>emptyList());

    /**
     * Specifies whether SSL or StartTLS should be used for securing connections when an SSL context is specified.
     * <p>
     * By default SSL will be used in preference to StartTLS.
     */
    public static final Option<Boolean> SSL_USE_STARTTLS = Option.withDefault(false);

    /** Default heart-beat which will target the root DSE but not return any results. */
    private static final SearchRequest DEFAULT_HEARTBEAT =
            unmodifiableSearchRequest(newSearchRequest("", SearchScope.BASE_OBJECT, "(objectClass=*)", "1.1"));

    /**
     * Specifies the parameters of the search request that will be used for heart-beats. The default heart-beat search
     * request is a base object search against the root DSE requesting no attributes. Heart-beats will only be sent if
     * {@link #HEARTBEAT_ENABLED} is set to {@code true}.
     *
     * @see #HEARTBEAT_ENABLED
     */
    public static final Option<SearchRequest> HEARTBEAT_SEARCH_REQUEST =
            Option.of(SearchRequest.class, DEFAULT_HEARTBEAT);

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** The overall timeout to use when establishing connections, including SSL, bind, and heart-beat. */
    private final long connectTimeoutMS;

    /**
     * The minimum amount of time the connection should remain idle (no responses) before starting to send heartbeats.
     */
    private final long heartBeatDelayMS;

    /** Indicates whether heartbeats should be performed. */
    private final Boolean heartBeatEnabled;

    /** The heartbeat search request. */
    private final SearchRequest heartBeatRequest;

    /**
     * The heartbeat timeout in milli-seconds. The connection will be marked as failed if no heartbeat response is
     * received within the timeout.
     */
    private final long heartBeatTimeoutMS;

    /** The interval between successive heartbeats. */
    private final long heartBeatintervalMS;

    /** The factory responsible for handling the low-level network communication with the Directory Server. */
    private final LDAPConnectionFactoryImpl impl;

    /** The optional bind request which will be used as the initial heartbeat if specified. */
    private final BindRequest initialBindRequest;

    /** Flag which indicates whether this factory has been closed. */
    private final AtomicBoolean isClosed = new AtomicBoolean();

    /** A copy of the original options. This is only useful for debugging. */
    private final Options options;

    /** Transport provider that provides the implementation of this factory. */
    private final TransportProvider provider;

    /**
     * Prevents the scheduler being released when there are remaining references (this factory or any connections). It
     * is initially set to 1 because this factory has a reference.
     */
    private final AtomicInteger referenceCount = new AtomicInteger(1);

    /** The heartbeat scheduler. */
    private final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduler;

    /** Non-null if SSL or StartTLS should be used when creating new connections. */
    private final SSLContext sslContext;

    /** The list of permitted SSL ciphers for SSL negotiation. */
    private final List<String> sslEnabledCipherSuites;

    /** The list of permitted SSL protocols for SSL negotiation. */
    private final List<String> sslEnabledProtocols;

    /** Indicates whether a StartTLS request should be sent immediately after connecting. */
    private final boolean sslUseStartTLS;

    /** List of valid connections to which heartbeats will be sent. */
    private final List<ConnectionImpl> validConnections = new LinkedList<>();

    /** This is package private in order to allow unit tests to inject fake time stamps. */
    TimeService timeService = TimeService.SYSTEM;

    /** Scheduled task which sends heart beats for all valid idle connections. */
    private final Runnable sendHeartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            boolean heartBeatSent = false;
            for (final ConnectionImpl connection : getValidConnections()) {
                heartBeatSent |= connection.sendHeartBeat();
            }
            if (heartBeatSent) {
                scheduler.get().schedule(checkHeartBeatRunnable, heartBeatTimeoutMS, TimeUnit.MILLISECONDS);
            }
        }
    };

    /** Scheduled task which checks that all heart beats have been received within the timeout period. */
    private final Runnable checkHeartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            for (final ConnectionImpl connection : getValidConnections()) {
                connection.checkForHeartBeat();
            }
        }
    };

    /** The heartbeat scheduled future - which may be null if heartbeats are not being sent (no valid connections). */
    private ScheduledFuture<?> heartBeatFuture;

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP connections to the Directory Server at the
     * provided host and port number.
     *
     * @param host
     *         The host name.
     * @param port
     *         The port number.
     * @throws NullPointerException
     *         If {@code host} was {@code null}.
     * @throws ProviderNotFoundException
     *         if no provider is available or if the provider requested using options is not found.
     */
    public LDAPConnectionFactory(final String host, final int port) {
        this(host, port, Options.defaultOptions());
    }

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP connections to the Directory Server at the
     * provided host and port number.
     *
     * @param host
     *         The host name.
     * @param port
     *         The port number.
     * @param options
     *         The LDAP options to use when creating connections.
     * @throws NullPointerException
     *         If {@code host} or {@code options} was {@code null}.
     * @throws ProviderNotFoundException
     *         if no provider is available or if the provider requested using options is not found.
     */
    public LDAPConnectionFactory(final String host, final int port, final Options options) {
        Reject.ifNull(host, options);

        this.connectTimeoutMS = options.get(CONNECT_TIMEOUT).to(TimeUnit.MILLISECONDS);
        Reject.ifTrue(connectTimeoutMS < 0, "connect timeout must be >= 0");
        Reject.ifTrue(options.get(REQUEST_TIMEOUT).getValue() < 0, "request timeout must be >= 0");

        this.heartBeatEnabled = options.get(HEARTBEAT_ENABLED);
        this.heartBeatintervalMS = options.get(HEARTBEAT_INTERVAL).to(TimeUnit.MILLISECONDS);
        this.heartBeatTimeoutMS = options.get(HEARTBEAT_TIMEOUT).to(TimeUnit.MILLISECONDS);
        this.heartBeatDelayMS = heartBeatintervalMS / 2;
        this.heartBeatRequest = options.get(HEARTBEAT_SEARCH_REQUEST);
        if (heartBeatEnabled) {
            Reject.ifTrue(heartBeatintervalMS <= 0, "heart-beat interval must be positive");
            Reject.ifTrue(heartBeatTimeoutMS <= 0, "heart-beat timeout must be positive");
        }

        this.provider = getTransportProvider(options);
        this.scheduler = DEFAULT_SCHEDULER.acquireIfNull(options.get(HEARTBEAT_SCHEDULER));
        this.impl = provider.getLDAPConnectionFactory(host, port, options);
        this.initialBindRequest = options.get(AUTHN_BIND_REQUEST);
        this.sslContext = options.get(SSL_CONTEXT);
        this.sslUseStartTLS = options.get(SSL_USE_STARTTLS);
        this.sslEnabledProtocols = options.get(SSL_ENABLED_PROTOCOLS);
        this.sslEnabledCipherSuites = options.get(SSL_ENABLED_CIPHER_SUITES);

        this.options = Options.copyOf(options);
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            synchronized (validConnections) {
                if (!validConnections.isEmpty()) {
                    logger.debug(LocalizableMessage.raw(
                            "HeartbeatConnectionFactory '%s' is closing while %d active connections remain",
                            this,
                            validConnections.size()));
                }
            }
            releaseScheduler();
            impl.close();
        }
    }

    @Override
    public Connection getConnection() throws LdapException {
        return getConnectionAsync().getOrThrowUninterruptibly();
    }

    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        acquireScheduler(); // Protect scheduler.

        // Register the connect timeout timer.
        final PromiseImpl<Connection, LdapException> promise = PromiseImpl.create();
        final AtomicReference<LDAPConnectionImpl> connectionHolder = new AtomicReference<>();
        final ScheduledFuture<?> timeoutFuture;
        if (connectTimeoutMS > 0) {
            timeoutFuture = scheduler.get().schedule(new Runnable() {
                @Override
                public void run() {
                    if (promise.tryHandleException(newConnectTimeoutError())) {
                        closeSilently(connectionHolder.get());
                        releaseScheduler();
                    }
                }
            }, connectTimeoutMS, TimeUnit.MILLISECONDS);
        } else {
            timeoutFuture = null;
        }

        // Now connect, negotiate SSL, etc.
        impl.getConnectionAsync()
            // Save the connection.
            .then(new Function<LDAPConnectionImpl, LDAPConnectionImpl, LdapException>() {
                @Override
                public LDAPConnectionImpl apply(final LDAPConnectionImpl connection) throws LdapException {
                    connectionHolder.set(connection);
                    return connection;
                }
            })
            .thenAsync(performStartTLSIfNeeded())
            .thenAsync(performSSLHandShakeIfNeeded(connectionHolder))
            .thenAsync(performInitialBindIfNeeded(connectionHolder))
            .thenAsync(performInitialHeartBeatIfNeeded(connectionHolder))
            .thenOnResult(new ResultHandler<Result>() {
                @Override
                public void handleResult(Result result) {
                    if (timeoutFuture != null) {
                        timeoutFuture.cancel(false);
                    }
                    final LDAPConnectionImpl connection = connectionHolder.get();
                    final ConnectionImpl connectionImpl = new ConnectionImpl(connection);
                    if (!promise.tryHandleResult(registerConnection(connectionImpl))) {
                        connectionImpl.close();
                    }
                }
            })
            .thenOnException(new ExceptionHandler<LdapException>() {
                @Override
                public void handleException(final LdapException e) {
                    if (timeoutFuture != null) {
                        timeoutFuture.cancel(false);
                    }
                    final LdapException connectException;
                    if (e instanceof ConnectionException || e instanceof AuthenticationException) {
                        connectException = e;
                    } else if (e instanceof TimeoutResultException) {
                        connectException = newHeartBeatTimeoutError();
                    } else {
                        connectException = newLdapException(ResultCode.CLIENT_SIDE_SERVER_DOWN,
                                                            HBCF_HEARTBEAT_FAILED.get(),
                                                            e);
                    }
                    if (promise.tryHandleException(connectException)) {
                        closeSilently(connectionHolder.get());
                        releaseScheduler();
                    }
                }
            });

        return promise;
    }

    /**
     * Returns the host name of the Directory Server. The returned host name is the same host name that was provided
     * during construction and may be an IP address. More specifically, this method will not perform a reverse DNS
     * lookup.
     *
     * @return The host name of the Directory Server.
     */
    public String getHostName() {
        return impl.getHostName();
    }

    /**
     * Returns the port of the Directory Server.
     *
     * @return The port of the Directory Server.
     */
    public int getPort() {
        return impl.getPort();
    }

    /**
     * Returns the name of the transport provider, which provides the implementation of this factory.
     *
     * @return The name of actual transport provider.
     */
    public String getProviderName() {
        return provider.getName();
    }

    @Override
    public String toString() {
        return "LDAPConnectionFactory(provider=`" + getProviderName() + ", host='" + getHostName() + "', port="
                + getPort() + ", options=" + options + ")";
    }

    private void acquireScheduler() {
        /*
         * If the factory is not closed then we need to prevent the scheduler from being released while the
         * connection attempt is in progress.
         */
        referenceCount.incrementAndGet();
        if (isClosed.get()) {
            releaseScheduler();
            throw new IllegalStateException("Attempted to get a connection on closed factory");
        }
    }

    private ConnectionImpl[] getValidConnections() {
        synchronized (validConnections) {
            return validConnections.toArray(new ConnectionImpl[validConnections.size()]);
        }
    }

    private LdapException newConnectTimeoutError() {
        final LocalizableMessage msg = LDAP_CONNECTION_CONNECT_TIMEOUT.get(impl.getSocketAddress(), connectTimeoutMS);
        return newLdapException(ResultCode.CLIENT_SIDE_CONNECT_ERROR, msg.toString());
    }

    private LdapException newHeartBeatTimeoutError() {
        return newLdapException(ResultCode.CLIENT_SIDE_SERVER_DOWN, HBCF_HEARTBEAT_TIMEOUT.get(heartBeatTimeoutMS));
    }

    private AsyncFunction<Void, BindResult, LdapException> performInitialBindIfNeeded(
            final AtomicReference<LDAPConnectionImpl> connectionHolder) {
        return new AsyncFunction<Void, BindResult, LdapException>() {
            @Override
            public Promise<BindResult, LdapException> apply(final Void ignored) throws LdapException {
                if (initialBindRequest != null) {
                    return connectionHolder.get().bindAsync(initialBindRequest, null);
                } else {
                    return newResultPromise(newBindResult(ResultCode.SUCCESS));
                }
            }
        };
    }

    private AsyncFunction<BindResult, Result, LdapException> performInitialHeartBeatIfNeeded(
            final AtomicReference<LDAPConnectionImpl> connectionHolder) {
        return new AsyncFunction<BindResult, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(final BindResult ignored) throws LdapException {
                // Only send an initial heartbeat if we haven't already interacted with the server.
                if (heartBeatEnabled && sslContext == null && initialBindRequest == null) {
                    return connectionHolder.get().searchAsync(heartBeatRequest, null, null);
                } else {
                    return newResultPromise(newResult(ResultCode.SUCCESS));
                }
            }
        };
    }

    private AsyncFunction<ExtendedResult, Void, LdapException> performSSLHandShakeIfNeeded(
            final AtomicReference<LDAPConnectionImpl> connectionHolder) {
        return new AsyncFunction<ExtendedResult, Void, LdapException>() {
            @Override
            public Promise<Void, LdapException> apply(final ExtendedResult extendedResult) throws LdapException {
                if (sslContext != null && !sslUseStartTLS) {
                    return connectionHolder.get().enableTLS(sslContext, sslEnabledProtocols, sslEnabledCipherSuites);
                } else {
                    return newResultPromise(null);
                }
            }
        };
    }

    private AsyncFunction<LDAPConnectionImpl, ExtendedResult, LdapException> performStartTLSIfNeeded() {
        return new AsyncFunction<LDAPConnectionImpl, ExtendedResult, LdapException>() {
            @Override
            public Promise<ExtendedResult, LdapException> apply(final LDAPConnectionImpl connection)
                    throws LdapException {
                if (sslContext != null && sslUseStartTLS) {
                    final StartTLSExtendedRequest startTLS = newStartTLSExtendedRequest(sslContext)
                            .addEnabledCipherSuite(sslEnabledCipherSuites)
                            .addEnabledProtocol(sslEnabledProtocols);
                    return connection.extendedRequestAsync(startTLS, null);
                } else {
                    return newResultPromise((ExtendedResult) newGenericExtendedResult(ResultCode.SUCCESS));
                }
            }
        };
    }

    private Connection registerConnection(final ConnectionImpl heartBeatConnection) {
        synchronized (validConnections) {
            if (heartBeatEnabled && validConnections.isEmpty()) {
                // This is the first active connection, so start the heart beat.
                heartBeatFuture = scheduler.get()
                                           .scheduleWithFixedDelay(sendHeartBeatRunnable,
                                                                   0,
                                                                   heartBeatintervalMS,
                                                                   TimeUnit.MILLISECONDS);
            }
            validConnections.add(heartBeatConnection);
        }
        return heartBeatConnection;
    }

    private void releaseScheduler() {
        if (referenceCount.decrementAndGet() == 0) {
            scheduler.release();
        }
    }

    /**
     * This synchronizer prevents Bind or StartTLS operations from being processed concurrently with heart-beats. This
     * is required because the LDAP protocol specifically states that servers receiving a Bind operation should either
     * wait for existing operations to complete or abandon them. The same presumably applies to StartTLS operations.
     * Note that concurrent bind/StartTLS operations are not permitted.
     * <p>
     * This connection factory only coordinates Bind and StartTLS requests with heart-beats. It does not attempt to
     * prevent or control attempts to send multiple concurrent Bind or StartTLS operations, etc.
     * <p>
     * This synchronizer can be thought of as cross between a read-write lock and a semaphore. Unlike a read-write lock
     * there is no requirement that a thread releasing a lock must hold it. In addition, this synchronizer does not
     * support reentrancy. A thread attempting to acquire exclusively more than once will deadlock, and a thread
     * attempting to acquire shared more than once will succeed and be required to release an equivalent number of
     * times.
     * <p>
     * The synchronizer has three states:
     * <ul>
     *     <li> UNLOCKED(0) - the synchronizer may be acquired shared or exclusively
     *     <li> LOCKED_EXCLUSIVELY(-1) - the synchronizer is held exclusively and cannot be acquired shared or
     *          exclusively. An exclusive lock is held while a heart beat is in progress
     *     <li> LOCKED_SHARED(>0) - the synchronizer is held shared and cannot be acquired exclusively. N shared locks
     *          are held while N Bind or StartTLS operations are in progress.
     * </ul>
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        /** Lock states. Positive values indicate that the shared lock is taken. */
        private static final int LOCKED_EXCLUSIVELY = -1;
        private static final int UNLOCKED = 0; // initial state

        /** Keep compiler quiet. */
        private static final long serialVersionUID = -3590428415442668336L;

        boolean isHeld() {
            return getState() != 0;
        }

        void lockShared() {
            acquireShared(1);
        }

        boolean tryLockExclusively() {
            return tryAcquire(0 /* unused */);
        }

        boolean tryLockShared() {
            return tryAcquireShared(1) > 0;
        }

        void unlockExclusively() {
            release(0 /* unused */);
        }

        void unlockShared() {
            releaseShared(0 /* unused */);
        }

        @Override
        protected boolean isHeldExclusively() {
            return getState() == LOCKED_EXCLUSIVELY;
        }

        @Override
        protected boolean tryAcquire(final int ignored) {
            if (compareAndSetState(UNLOCKED, LOCKED_EXCLUSIVELY)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        @Override
        protected int tryAcquireShared(final int readers) {
            for (;;) {
                final int state = getState();
                if (state == LOCKED_EXCLUSIVELY) {
                    return LOCKED_EXCLUSIVELY; // failed
                }
                final int newState = state + readers;
                if (compareAndSetState(state, newState)) {
                    return newState; // succeeded + more readers allowed
                }
            }
        }

        @Override
        protected boolean tryRelease(final int ignored) {
            if (getState() != LOCKED_EXCLUSIVELY) {
                throw new IllegalMonitorStateException();
            }
            setExclusiveOwnerThread(null);
            setState(UNLOCKED);
            return true;
        }

        @Override
        protected boolean tryReleaseShared(final int ignored) {
            for (;;) {
                final int state = getState();
                if (state == UNLOCKED || state == LOCKED_EXCLUSIVELY) {
                    throw new IllegalMonitorStateException();
                }
                final int newState = state - 1;
                if (compareAndSetState(state, newState)) {
                    /*
                     * We could always return true here, but since there cannot be waiting readers we can specialize
                     * for waiting writers.
                     */
                    return newState == UNLOCKED;
                }
            }
        }

    }

    /** A connection that sends heart beats and supports all operations. */
    private final class ConnectionImpl extends AbstractAsynchronousConnection implements ConnectionEventListener {
        /** The wrapped connection. */
        private final LDAPConnectionImpl connectionImpl;

        /** List of pending Bind or StartTLS requests which must be invoked once the current heart beat completes. */
        private final Queue<Runnable> pendingBindOrStartTLSRequests = new ConcurrentLinkedQueue<>();

        /**
         * List of pending responses for all active operations. These will be signaled if no heart beat is detected
         * within the permitted timeout period.
         */
        private final Queue<LdapResultHandler<?>> pendingResults = new ConcurrentLinkedQueue<>();

        /** Internal connection state. */
        private final ConnectionState state = new ConnectionState();

        /** Coordinates heart-beats with Bind and StartTLS requests. */
        private final Sync sync = new Sync();

        /** Timestamp of last response received (any response, not just heart beats). */
        private volatile long lastResponseTimestamp = timeService.now();

        private ConnectionImpl(final LDAPConnectionImpl connectionImpl) {
            this.connectionImpl = connectionImpl;
            connectionImpl.addConnectionEventListener(this);
        }

        @Override
        public LdapPromise<Void> abandonAsync(final AbandonRequest request) {
            return connectionImpl.abandonAsync(request);
        }

        @Override
        public LdapPromise<Result> addAsync(
                final AddRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            if (hasConnectionErrorOccurred()) {
                return newConnectionErrorPromise();
            }
            return timestampPromise(connectionImpl.addAsync(request, intermediateResponseHandler));
        }

        @Override
        public void addConnectionEventListener(final ConnectionEventListener listener) {
            state.addConnectionEventListener(listener);
        }

        @Override
        public LdapPromise<BindResult> bindAsync(
                final BindRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            if (hasConnectionErrorOccurred()) {
                return newConnectionErrorPromise();
            }
            if (sync.tryLockShared()) {
                // Fast path
                return timestampBindOrStartTLSPromise(connectionImpl.bindAsync(request, intermediateResponseHandler));
            }
            return enqueueBindOrStartTLSPromise(new AsyncFunction<Void, BindResult, LdapException>() {
                @Override
                public Promise<BindResult, LdapException> apply(Void value) throws LdapException {
                    return timestampBindOrStartTLSPromise(connectionImpl.bindAsync(request,
                                                                                   intermediateResponseHandler));
                }
            });
        }

        @Override
        public void close() {
            handleConnectionClosed();
            connectionImpl.close();
        }

        @Override
        public void close(final UnbindRequest request, final String reason) {
            handleConnectionClosed();
            connectionImpl.close(request, reason);
        }

        @Override
        public LdapPromise<CompareResult> compareAsync(
                final CompareRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            if (hasConnectionErrorOccurred()) {
                return newConnectionErrorPromise();
            }
            return timestampPromise(connectionImpl.compareAsync(request, intermediateResponseHandler));
        }

        @Override
        public LdapPromise<Result> deleteAsync(
                final DeleteRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            if (hasConnectionErrorOccurred()) {
                return newConnectionErrorPromise();
            }
            return timestampPromise(connectionImpl.deleteAsync(request, intermediateResponseHandler));
        }

        @Override
        public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(
                final ExtendedRequest<R> request, final IntermediateResponseHandler intermediateResponseHandler) {
            if (hasConnectionErrorOccurred()) {
                return newConnectionErrorPromise();
            }
            if (!isStartTLSRequest(request)) {
                return timestampPromise(connectionImpl.extendedRequestAsync(request, intermediateResponseHandler));
            }
            if (sync.tryLockShared()) {
                // Fast path
                return timestampBindOrStartTLSPromise(
                        connectionImpl.extendedRequestAsync(request, intermediateResponseHandler));
            }
            return enqueueBindOrStartTLSPromise(new AsyncFunction<Void, R, LdapException>() {
                @Override
                public Promise<R, LdapException> apply(Void value) throws LdapException {
                    return timestampBindOrStartTLSPromise(
                            connectionImpl.extendedRequestAsync(request, intermediateResponseHandler));
                }
            });
        }

        @Override
        public void handleConnectionClosed() {
            if (state.notifyConnectionClosed()) {
                failPendingResults(newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED,
                                                    HBCF_CONNECTION_CLOSED_BY_CLIENT.get()));
                synchronized (validConnections) {
                    connectionImpl.removeConnectionEventListener(this);
                    validConnections.remove(this);
                    if (heartBeatEnabled && validConnections.isEmpty()) {
                        // This is the last active connection, so stop the heartbeat.
                        heartBeatFuture.cancel(false);
                    }
                }
                releaseScheduler();
            }
        }

        @Override
        public void handleConnectionError(final boolean isDisconnectNotification, final LdapException error) {
            if (state.notifyConnectionError(isDisconnectNotification, error)) {
                failPendingResults(error);
            }
        }

        @Override
        public void handleUnsolicitedNotification(final ExtendedResult notification) {
            timestamp(notification);
            state.notifyUnsolicitedNotification(notification);
        }

        @Override
        public boolean isClosed() {
            return state.isClosed();
        }

        @Override
        public boolean isValid() {
            return state.isValid() && connectionImpl.isValid();
        }

        @Override
        public LdapPromise<Result> modifyAsync(
                final ModifyRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            if (hasConnectionErrorOccurred()) {
                return newConnectionErrorPromise();
            }
            return timestampPromise(connectionImpl.modifyAsync(request, intermediateResponseHandler));
        }

        @Override
        public LdapPromise<Result> modifyDNAsync(
                final ModifyDNRequest request, final IntermediateResponseHandler intermediateResponseHandler) {
            if (hasConnectionErrorOccurred()) {
                return newConnectionErrorPromise();
            }
            return timestampPromise(connectionImpl.modifyDNAsync(request, intermediateResponseHandler));
        }

        @Override
        public void removeConnectionEventListener(final ConnectionEventListener listener) {
            state.removeConnectionEventListener(listener);
        }

        @Override
        public LdapPromise<Result> searchAsync(
                final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler searchHandler) {
            if (hasConnectionErrorOccurred()) {
                return newConnectionErrorPromise();
            }

            final AtomicBoolean searchDone = new AtomicBoolean();
            final SearchResultHandler entryHandler = new SearchResultHandler() {
                @Override
                public synchronized boolean handleEntry(SearchResultEntry entry) {
                    if (!searchDone.get()) {
                        timestamp(entry);
                        if (searchHandler != null) {
                            searchHandler.handleEntry(entry);
                        }
                    }
                    return true;
                }

                @Override
                public synchronized boolean handleReference(SearchResultReference reference) {
                    if (!searchDone.get()) {
                        timestamp(reference);
                        if (searchHandler != null) {
                            searchHandler.handleReference(reference);
                        }
                    }
                    return true;
                }
            };
            return timestampPromise(connectionImpl.searchAsync(request, intermediateResponseHandler, entryHandler)
                                                  .thenOnResultOrException(new Runnable() {
                                                      @Override
                                                      public void run() {
                                                          searchDone.getAndSet(true);
                                                      }
                                                  }));
        }

        @Override
        public String toString() {
            return connectionImpl.toString();
        }

        private void checkForHeartBeat() {
            if (sync.isHeld()) {
                /*
                 * A heart beat or bind/startTLS is still in progress, but it should have completed by now. Let's
                 * avoid aggressively terminating the connection, because the heart beat may simply have been delayed
                 * by a sudden surge of activity. Therefore, only flag the connection as failed if no activity has been
                 * seen on the connection since the heart beat was sent.
                 */
                final long currentTimeMillis = timeService.now();
                if (lastResponseTimestamp < (currentTimeMillis - heartBeatTimeoutMS)) {
                    logger.warn(LocalizableMessage.raw("No heartbeat detected for connection '%s'", connectionImpl));
                    handleConnectionError(false, newHeartBeatTimeoutError());
                }
            }
        }

        private boolean hasConnectionErrorOccurred() {
            return state.getConnectionError() != null;
        }

        private <R extends Result> LdapPromise<R> enqueueBindOrStartTLSPromise(
                AsyncFunction<Void, R, LdapException> doRequest) {
            /*
             * A heart beat must be in progress so create a runnable task which will be executed when the heart beat
             * completes.
             */
            final LdapPromiseImpl<Void> promise = newLdapPromiseImpl();
            final LdapPromise<R> result = promise.thenAsync(doRequest);

            // Enqueue and flush if the heart beat has completed in the mean time.
            pendingBindOrStartTLSRequests.offer(new Runnable() {
                @Override
                public void run() {
                    // FIXME: Handle cancel chaining.
                    if (!result.isCancelled()) {
                        sync.lockShared(); // Will not block.
                        promise.handleResult(null);
                    }
                }
            });
            flushPendingBindOrStartTLSRequests();
            return result;
        }

        private void failPendingResults(final LdapException error) {
            // Peek instead of pool because notification is responsible for removing the element from the queue.
            LdapResultHandler<?> pendingResult;
            while ((pendingResult = pendingResults.peek()) != null) {
                pendingResult.handleException(error);
            }
        }

        private void flushPendingBindOrStartTLSRequests() {
            if (!pendingBindOrStartTLSRequests.isEmpty()) {
                /*
                 * The pending requests will acquire the shared lock, but we take it here anyway to ensure that
                 * pending requests do not get blocked.
                 */
                if (sync.tryLockShared()) {
                    try {
                        Runnable pendingRequest;
                        while ((pendingRequest = pendingBindOrStartTLSRequests.poll()) != null) {
                            // Dispatch the waiting request. This will not block.
                            pendingRequest.run();
                        }
                    } finally {
                        sync.unlockShared();
                    }
                }
            }
        }

        private boolean isStartTLSRequest(final ExtendedRequest<?> request) {
            return request.getOID().equals(StartTLSExtendedRequest.OID);
        }

        private <R> LdapPromise<R> newConnectionErrorPromise() {
            return newFailedLdapPromise(state.getConnectionError());
        }

        private void releaseBindOrStartTLSLock() {
            sync.unlockShared();
        }

        private void releaseHeartBeatLock() {
            sync.unlockExclusively();
            flushPendingBindOrStartTLSRequests();
        }

        /**
         * Sends a heart beat on this connection if required to do so.
         *
         * @return {@code true} if a heart beat was sent, otherwise {@code false}.
         */
        private boolean sendHeartBeat() {
            // Don't attempt to send a heart beat if the connection has already failed.
            if (!state.isValid()) {
                return false;
            }

            // Only send the heart beat if the connection has been idle for some time.
            final long currentTimeMillis = timeService.now();
            if (currentTimeMillis < (lastResponseTimestamp + heartBeatDelayMS)) {
                return false;
            }

            /* Don't send a heart beat if there is already a heart beat, bind, or startTLS in progress. Note that the
             * bind/startTLS response will update the lastResponseTimestamp as if it were a heart beat.
             */
            if (sync.tryLockExclusively()) {
                try {
                    connectionImpl.searchAsync(heartBeatRequest, null, new SearchResultHandler() {
                        @Override
                        public boolean handleEntry(final SearchResultEntry entry) {
                            timestamp(entry);
                            return true;
                        }

                        @Override
                        public boolean handleReference(final SearchResultReference reference) {
                            timestamp(reference);
                            return true;
                        }
                    }).thenOnResult(new org.forgerock.util.promise.ResultHandler<Result>() {
                        @Override
                        public void handleResult(Result result) {
                            timestamp(result);
                            releaseHeartBeatLock();
                        }
                    }).thenOnException(new ExceptionHandler<LdapException>() {
                        @Override
                        public void handleException(LdapException exception) {
                            /*
                             * Connection failure will be handled by connection event listener. Ignore cancellation
                             * errors since these indicate that the heart beat was aborted by a client-side close.
                             */
                            if (!(exception instanceof CancelledResultException)) {
                                /*
                                 * Log at debug level to avoid polluting the logs with benign password policy related
                                 * errors. See OPENDJ-1168 and OPENDJ-1167.
                                 */
                                logger.debug(LocalizableMessage.raw("Heartbeat failed for connection factory '%s'",
                                                                    LDAPConnectionFactory.this,
                                                                    exception));
                                timestamp(exception);
                            }
                            releaseHeartBeatLock();
                        }
                    });
                } catch (final IllegalStateException e) {
                    /*
                     * This may happen when we attempt to send the heart beat just after the connection is closed but
                     * before we are notified. Release the lock because we're never going to get a response.
                     */
                    releaseHeartBeatLock();
                }
            }
            /*
             * Indicate that a the heartbeat should be checked even if a bind/startTLS is in progress, since these
             * operations will effectively act as the heartbeat.
             */
            return true;
        }

        private <R> R timestamp(final R response) {
            if (!(response instanceof ConnectionException)) {
                lastResponseTimestamp = timeService.now();
            }
            return response;
        }

        private <R extends Result> LdapPromise<R> timestampBindOrStartTLSPromise(LdapPromise<R> wrappedPromise) {
            return timestampPromise(wrappedPromise).thenOnResultOrException(new Runnable() {
                @Override
                public void run() {
                    releaseBindOrStartTLSLock();
                }
            });
        }

        private <R extends Result> LdapPromise<R> timestampPromise(LdapPromise<R> wrappedPromise) {
            final LdapPromiseImpl<R> outerPromise = new LdapPromiseImplWrapper<>(wrappedPromise);
            pendingResults.add(outerPromise);
            wrappedPromise.thenOnResult(new ResultHandler<R>() {
                @Override
                public void handleResult(R result) {
                    outerPromise.handleResult(result);
                    timestamp(result);
                }
            }).thenOnException(new ExceptionHandler<LdapException>() {
                @Override
                public void handleException(LdapException exception) {
                    outerPromise.handleException(exception);
                    timestamp(exception);
                }
            });
            outerPromise.thenOnResultOrException(new Runnable() {
                @Override
                public void run() {
                    pendingResults.remove(outerPromise);
                }
            });
            if (hasConnectionErrorOccurred()) {
                outerPromise.handleException(state.getConnectionError());
            }
            return outerPromise;
        }

        private class LdapPromiseImplWrapper<R> extends LdapPromiseImpl<R> {
            protected LdapPromiseImplWrapper(final LdapPromise<R> wrappedPromise) {
                super(new PromiseImpl<R, LdapException>() {
                    @Override
                    protected LdapException tryCancel(boolean mayInterruptIfRunning) {
                        /*
                         * FIXME: if the inner cancel succeeds then this promise will be completed and we can never
                         * indicate that this cancel request has succeeded.
                         */
                        wrappedPromise.cancel(mayInterruptIfRunning);
                        return null;
                    }
                }, wrappedPromise.getRequestID());
            }
        }
    }
}
