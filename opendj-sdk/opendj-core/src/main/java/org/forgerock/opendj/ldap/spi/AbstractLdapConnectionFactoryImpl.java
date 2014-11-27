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
 *      Copyright 2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap.spi;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.AsyncFunction;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.SuccessHandler;
import org.forgerock.util.time.TimeService;

import com.forgerock.opendj.util.ReferenceCountedObject;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.util.Utils.*;
import static org.forgerock.util.promise.Promises.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

/**
 * A abstract implementation of a LDAP connection factory.
 * This factory can be used to create connections that send a
 * periodic search request to a Directory Server, or to create
 * pre-authenticated secure connections to a Directory Server.
 * <p>
 * Before returning new connections to the application this factory will first
 * send an initial startTLS, bind or search (according to options) request in order to
 * determine that the remote server is responsive. If the request fails or times
 * out then the connection is closed immediately and an error returned to the client.
 * <p>
 * Once a connection has been established successfully (including the initial request)
 * and if heart beat is activated (this is done by specifying a interval
 * greater than 0 in the {@link LDAPOptions} constructor parameter), this
 * factory will periodically send heart beats on the connection based on the
 * configured heart beat interval. If the heart beat times out then the server
 * is assumed to be down and an appropriate {@link ConnectionException}
 * generated and published to any registered {@link ConnectionEventListener}s.
 * Note however, that heart beats will only be sent when the connection is
 * determined to be reasonably idle: there is no point in sending heart beats if
 * the connection has recently received a response. A connection is deemed to be
 * idle if no response has been received during a period equivalent to half the
 * heart beat interval.
 * <p>
 * The LDAP protocol specifically precludes clients from performing operations
 * while bind or startTLS requests are being performed. Likewise, a bind or
 * startTLS request will cause active operations to be aborted. This factory
 * coordinates heart beats with bind or startTLS requests, ensuring that they
 * are not performed concurrently. Specifically, bind and startTLS requests are
 * queued up while a heart beat is pending, and heart beats are not sent at all
 * while there are pending bind or startTLS requests. If one bind or startTLS
 * request has timed out, an error will be thrown after the interval time period.
 */
public abstract class AbstractLdapConnectionFactoryImpl implements LDAPConnectionFactoryImpl {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** This is package private in order to allow unit tests to inject fake timestamps. */
    TimeService timeService = TimeService.SYSTEM;

    // @Checkstyle:ignore
    private static <VOUT, VIN extends VOUT, E extends Exception> Function<VIN, VOUT, E> castFunction() {
        return new Function<VIN, VOUT, E>() {
            @Override
            public VOUT apply(VIN value) throws E {
                return value;
            }
        };
    }

    private final String host;

    private final int port;

    /** The LDAP connection options to use when creating connections. */
    protected final LDAPOptions options;

    /** Flag which indicates whether this factory has been closed. */
    private final AtomicBoolean isClosed = new AtomicBoolean();

    /**
     * Prevents the scheduler being released when there are remaining references (this factory or any connections).
     * It is initially set to 1 because this factory has a reference.
     */
    private final AtomicInteger referenceCount = new AtomicInteger(1);

    /** List of valid connections to which heartbeats will be sent. */
    private final List<AbstractLdapConnectionImpl<?>> validConnections =
        new LinkedList<AbstractLdapConnectionImpl<?>>();

    /** The heart beat scheduler. */
    private final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduler;

    /** The heart beat scheduled future - which may be null if heart beats are not being sent (no valid connections). */
    private ScheduledFuture<?> heartBeatFuture;

    /** Scheduled task which sends heart beats for all valid idle connections. */
    private final Runnable sendHeartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            boolean heartBeatSent = false;
            for (final AbstractLdapConnectionImpl<?> connection : getValidConnections()) {
                heartBeatSent |= connection.sendHeartBeat();
            }
            if (heartBeatSent) {
                scheduler.get().schedule(checkHeartBeatRunnable, options.getTimeout(MILLISECONDS), MILLISECONDS);
            }
        }
    };

    /** Scheduled task which checks that all heart beats have been received within the timeout period. */
    private final Runnable checkHeartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            for (final AbstractLdapConnectionImpl<?> connection : getValidConnections()) {
                connection.checkForHeartBeat();
            }
        }
    };

    /**
     * Initializes this {@link AbstractLdapConnectionFactoryImpl}.
     *
     * @param host
     *            The host name.
     * @param port
     *            The port number.
     * @param options
     *            The LDAP options to use when creating connections.
     */
    public AbstractLdapConnectionFactoryImpl(final String host, final int port, final LDAPOptions options) {
        Reject.ifNull(options);

        this.host = host;
        this.port = port;
        this.options = new LDAPOptions(options);
        this.scheduler = DEFAULT_SCHEDULER.acquireIfNull(options.getHeartBeatScheduler());
    }

    /**
     * Returns a promise which result is a {@link AbstractLdapConnectionImpl}.
     *
     * @return A promise which result is a {@link AbstractLdapConnectionImpl}.
     */
    protected abstract Promise<AbstractLdapConnectionImpl<?>, LdapException> getConnectionAsync0();

    /**
     * Install a secure layer on the given connection. Does nothing by default.
     * Factories which wants to use secure layer like SSL/TLS should override this method.
     *
     * @param connection
     *      The {@link Connection} to secure.
     * @return A promise which should be completed once the operation is done.
     */
    protected Promise<Void, LdapException> installSecureLayer(final Connection connection) {
        return newFailedPromise(newLdapException(ResultCode.OTHER,
            LocalizableMessage.raw("The connection should install a secure layer.")));
    }

    /**
     * Release all implementation resources.
     */
    protected void releaseImplResources() {
        //does nothing by default.
    }

    @Override
    public Connection getConnection() throws LdapException {
        try {
            return getConnectionAsync().getOrThrow();
        } catch (final InterruptedException e) {
            throw newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
        }
    }

    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        acquireResource(); // Protect resources.

        final AtomicReference<AbstractLdapConnectionImpl<?>> connectionHolder =
                new AtomicReference<AbstractLdapConnectionImpl<?>>();
        final PromiseImpl<Connection, LdapException> promise = PromiseImpl.create();

        getConnectionAsync0().thenAsync(new AsyncFunction<AbstractLdapConnectionImpl<?>, Void, LdapException>() {
            @Override
            public Promise<Void, LdapException> apply(AbstractLdapConnectionImpl<?> connection) throws LdapException {
                connectionHolder.set(connection);
                return startSchedulerAndSecureLayerIfNeeded(connection, promise);
            }
        }).thenAsync(new AsyncFunction<Void, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(Void v) throws LdapException {
                return sendInitialRequest(connectionHolder.get());
            }
        }).onSuccess(new SuccessHandler<Result>() {
            @Override
            public void handleResult(Result voidResult) {
                final AbstractLdapConnectionImpl<?> connectionImpl = connectionHolder.get();
                if (!promise.tryHandleResult(registerConnection(connectionImpl))) {
                    connectionImpl.close();
                }
            }
        }).onFailure(new FailureHandler<LdapException>() {
            @Override
            public void handleError(LdapException error) {
                if (promise.tryHandleError(error)) {
                    closeSilently(connectionHolder.get());
                    releaseResources();
                }
            }
        });

        return promise;
    }

    private Promise<Void, LdapException> startSchedulerAndSecureLayerIfNeeded(
        final AbstractLdapConnectionImpl<?> connection, final PromiseImpl<Connection, LdapException> promise) {
        // Start the scheduler to prevents initial request timed out.
        if (initialRequestEnabled()) {
            scheduler.get().schedule(new Runnable() {
                @Override
                public void run() {
                    if (promise.tryHandleError(newHeartBeatTimeoutError())) {
                        closeSilently(connection);
                        releaseResources();
                    }
                }
            }, options.getTimeout(MILLISECONDS), MILLISECONDS);
        }

        if (!options.useStartTLS() && options.getSSLContext() != null) {
            return installSecureLayer(connection);
        }

        return newSuccessfulPromise(null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Promise<Result, LdapException> sendInitialRequest(final AbstractLdapConnectionImpl connection) {
        // Sends the request which will acts as initial heartbeat.
        if (options.useStartTLS()) {
            // StartTLS extended request which will act as initial heartbeat.
            final StartTLSExtendedRequest startTLS = newStartTLSExtendedRequest(options.getSSLContext());
            startTLS.addEnabledCipherSuite(options.getEnabledCipherSuites().toArray(
                    new String[options.getEnabledCipherSuites().size()]));
            startTLS.addEnabledProtocol(options.getEnabledProtocols().toArray(
                    new String[options.getEnabledProtocols().size()]));

            return (Promise) connection.extendedRequestAsync(startTLS);
        } else if (options.getBindRequest() != null) {
            // FIXME: support multi-stage SASL binds?
            // The bind request will act as the initial hearbeat.
            return (Promise) connection.bindAsync(options.getBindRequest());
        } else if (isHeartBeatEnabled()) {
            return connection.searchAsync(options.getHeartBeatSearchRequest(), null, null).then(
                AbstractLdapConnectionFactoryImpl.<Result, Result, LdapException> castFunction(),
                new Function<LdapException, Result, LdapException>() {
                    @Override
                    public Result apply(LdapException error) throws LdapException {
                        throw adaptHeartBeatError(error);
                    }
                });
        }

        return newSuccessfulPromise(null);
    }

    private boolean initialRequestEnabled() {
        return isHeartBeatEnabled() || options.useStartTLS()
                || options.getSSLContext() != null || options.getBindRequest() != null;
    }

    private void acquireResource() {
        /*
         * If the factory is not closed then we need to prevent the scheduler
         * from being released while the connection attempt is in progress.
         */
        referenceCount.incrementAndGet();
        if (isClosed.get()) {
            releaseResources();
            throw new IllegalStateException("Attempted to get a connection after factory close");
        }
    }

    private Connection registerConnection(final AbstractLdapConnectionImpl<?> connection) {
        if (isHeartBeatEnabled()) {
            synchronized (validConnections) {
                if (validConnections.isEmpty()) {
                    /* This is the first active connection, so start the heart beat. */
                    heartBeatFuture = scheduler.get().scheduleWithFixedDelay(sendHeartBeatRunnable, 0,
                            options.getHeartBeatInterval(MILLISECONDS), MILLISECONDS);
                }
                validConnections.add(connection);
            }
        }

        return connection;
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            releaseResources();
            if (isHeartBeatEnabled()) {
                synchronized (validConnections) {
                    if (!validConnections.isEmpty()) {
                        logger.debug(LocalizableMessage.raw(
                                "HeartbeatConnectionFactory '%s' is closing while %d active connections remain", this,
                                validConnections.size()));
                    }
                }
            }
        }
    }

    void releaseResources() {
        if (referenceCount.decrementAndGet() == 0) {
            scheduler.release();
            releaseImplResources();
        }
    }

    /**
     * Returns {@link LDAPOptions} of this factory.
     *
     * @return {@link LDAPOptions} of this factory.
     */
    public LDAPOptions getLdapOptions() {
        return options;
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    @Override
    public String getHostName() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    boolean isHeartBeatEnabled() {
        return options.getHeartBeatInterval(MILLISECONDS) > 0L;
    }

    private AbstractLdapConnectionImpl<?>[] getValidConnections() {
        synchronized (validConnections) {
            return validConnections.toArray(new AbstractLdapConnectionImpl<?>[0]);
        }
    }

    void deregisterConnection(AbstractLdapConnectionImpl<?> connection) {
        if (isHeartBeatEnabled()) {
            synchronized (validConnections) {
                if (validConnections.remove(connection) && validConnections.isEmpty()) {
                    // This is the last active connection, so stop the heartbeat.
                    heartBeatFuture.cancel(false);
                }
            }
        }
    }

    private LdapException adaptHeartBeatError(final Exception error) {
        if (error instanceof ConnectionException) {
            return (LdapException) error;
        } else if (error instanceof TimeoutResultException || error instanceof TimeoutException) {
            return newHeartBeatTimeoutError();
        } else if (error instanceof InterruptedException) {
            return newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED, error);
        } else {
            return newLdapException(ResultCode.CLIENT_SIDE_SERVER_DOWN, HBCF_HEARTBEAT_FAILED.get(), error);
        }
    }

    LdapException newHeartBeatTimeoutError() {
        return newLdapException(ResultCode.CLIENT_SIDE_SERVER_DOWN,
                HBCF_HEARTBEAT_TIMEOUT.get(options.getTimeout(MILLISECONDS)));
    }

    TimeService getTimeService() {
        return timeService;
    }

    /**
     * Returns the minimum amount of time the connection should remain idle (no responses)
     * before starting to send heartbeats.
     *
     * @param unit
     *      The {@link TimeUnit} of the response
     * @return
     *      A long which represents the minimum amount of time the connection should remain idle (no responses)
     *      before starting to send heartbeats.
     */
    long getMinimumDelay(TimeUnit unit) {
        return MILLISECONDS.convert(options.getHeartBeatInterval(MILLISECONDS) / 2, unit);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("(").append(getClass().getSimpleName())
               .append("(").append(host).append(":").append(port).append("), ")
               .append("HeartBeat: ").append(isHeartBeatEnabled() ? "Enabled" : "Disabled").append(", ");

        if (isHeartBeatEnabled()) {
            builder.append("HeartBeatSearchRequest: ").append(options.getHeartBeatSearchRequest()).append(", ");
        }

        final boolean isSecured = options.getSSLContext() != null || options.useStartTLS();
        builder.append("IsSecured: ").append(isSecured ? "Yes" : "No").append(", ")
               .append("InitialRequest: ").append(initialRequestEnabled() ? "Enabled" : "Disabled");
        if (options.useStartTLS() || options.getBindRequest() != null || options.getHeartBeatSearchRequest() != null) {
            builder.append(", ");
            if (options.useStartTLS()) {
                builder.append("startTLS");
            } else if (options.getBindRequest() != null) {
                builder.append(options.getBindRequest());
            } else if (options.getHeartBeatSearchRequest() != null) {
                builder.append(options.getHeartBeatSearchRequest());
            }
        }
        builder.append(")");
        return builder.toString();
    }

}
