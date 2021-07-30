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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.forgerock.opendj.reactive;

import static java.util.Collections.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.AccessLogger.logConnect;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.util.StaticUtils.isFips;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.AddressMask;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPClientContextEventListener;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRequestEnvelope;
import org.forgerock.opendj.server.config.server.ConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.forgerock.util.Function;
import org.forgerock.util.Options;
import org.glassfish.grizzly.utils.ArrayUtils;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.QueueingStrategy;
import org.opends.server.core.ServerContext;
import org.opends.server.core.WorkQueueStrategy;
import org.opends.server.extensions.NullKeyManagerProvider;
import org.opends.server.monitors.ClientConnectionMonitorProvider;
import org.opends.server.protocols.ldap.LDAPStatistics;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;
import org.opends.server.types.SSLClientAuthPolicy;
import org.opends.server.util.SelectableCertificateKeyManager;
import org.opends.server.util.StaticUtils;

import com.forgerock.reactive.ReactiveHandler;
import com.forgerock.reactive.Stream;
import java.security.Provider;
import java.security.Security;

/**
 * This class defines a connection handler that will be used for communicating with clients over LDAP. It is actually
 * implemented in two parts: as a connection handler and one or more request handlers. The connection handler is
 * responsible for accepting new connections and registering each of them with a request handler. The request handlers
 * then are responsible for reading requests from the clients and parsing them as operations. A single request handler
 * may be used, but having multiple handlers might provide better performance in a multi-CPU system.
 */
public final class LDAPConnectionHandler2 extends ConnectionHandler<LDAPConnectionHandlerCfg> implements
        ConfigurationChangeListener<LDAPConnectionHandlerCfg>, ServerShutdownListener, AlertGenerator {
    /** Task run periodically by the connection finalizer. */
    private final class ConnectionFinalizerRunnable implements Runnable {
        @Override
        public void run() {
            if (!connectionFinalizerActiveJobQueue.isEmpty()) {
                for (Runnable r : connectionFinalizerActiveJobQueue) {
                    r.run();
                }
                connectionFinalizerActiveJobQueue.clear();
            }

            // Switch the lists.
            synchronized (connectionFinalizerLock) {
                List<Runnable> tmp = connectionFinalizerActiveJobQueue;
                connectionFinalizerActiveJobQueue = connectionFinalizerPendingJobQueue;
                connectionFinalizerPendingJobQueue = tmp;
            }
        }
    }

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** Default friendly name for the LDAP connection handler. */
    private static final String DEFAULT_FRIENDLY_NAME = "LDAP Connection Handler";

    /** SSL instance name used in context creation. */
    private static final String SSL_CONTEXT_INSTANCE_NAME = "TLS";

    private LDAPListener listener;

    /** The current configuration state. */
    private LDAPConnectionHandlerCfg currentConfig;

    /* Properties that cannot be modified dynamically */

    /** The set of addresses on which to listen for new connections. */
    private Set<InetSocketAddress> listenAddresses;

    /** The SSL client auth policy used by this connection handler. */
    private SSLClientAuthPolicy sslClientAuthPolicy;

    /** The backlog that will be used for the accept queue. */
    private int backlog;

    /** Indicates whether to allow the reuse address socket option. */
    private boolean allowReuseAddress;

    /** Indicates whether the Directory Server is in the process of shutting down. */
    private volatile boolean shutdownRequested;

    /* Internal LDAP connection handler state */

    /** Indicates whether this connection handler is enabled. */
    private boolean enabled;

    /** The set of clients that are explicitly allowed access to the server. */
    private Collection<AddressMask> allowedClients;

    /** The set of clients that have been explicitly denied access to the server. */
    private Collection<AddressMask> deniedClients;

    /** The set of listeners for this connection handler. */
    private List<HostPort> listeners;

    /** The set of statistics collected for this connection handler. */
    private LDAPStatistics statTracker;

    /** The client connection monitor provider associated with this connection handler. */
    private ClientConnectionMonitorProvider connMonitor;

    /** The unique name assigned to this connection handler. */
    private String handlerName;

    /** The protocol used by this connection handler. */
    private String protocol;

    /** Queueing strategy. */
    private final QueueingStrategy queueingStrategy;

    /**
     * The condition variable that will be used by the start method to wait for the socket port to be opened and ready
     * to process requests before returning.
     */
    private final Object waitListen = new Object();

    /** The friendly name of this connection handler. */
    private String friendlyName;

    /**
     * SSL context.
     *
     * @see LDAPConnectionHandler2#sslEngine
     */
    private SSLContext sslContext;

    /** The SSL engine is used for obtaining default SSL parameters. */
    private SSLEngine sslEngine;

    /**
     * Connection finalizer thread.
     * <p>
     * This thread is defers closing clients for approximately 100ms. This gives the client a chance to close the
     * connection themselves before the server thus avoiding leaving the server side in the TIME WAIT state.
     */
    private final Object connectionFinalizerLock = new Object();
    private ScheduledExecutorService connectionFinalizer;
    private List<Runnable> connectionFinalizerActiveJobQueue;
    private List<Runnable> connectionFinalizerPendingJobQueue;

    /**
     * Maintains the list of active client connections. Backed by a {@link ConcurrentHashMap} to have a thread-safe 0(1)
     * on add (connection) and remove (disconnection).
     */
    private final Collection<ClientConnection> clientConnections =
            newSetFromMap(new ConcurrentHashMap<ClientConnection, Boolean>());

    /**
     * Creates a new instance of this LDAP connection handler. It must be initialized before it may be used.
     */
    public LDAPConnectionHandler2() {
        this(new WorkQueueStrategy(), null); // Use name from configuration.
    }

    /**
     * Creates a new instance of this LDAP connection handler, using a queueing strategy. It must be initialized before
     * it may be used.
     *
     * @param strategy
     *            Request handling strategy.
     * @param friendlyName
     *            The name of of this connection handler, or {@code null} if the name should be taken from the
     *            configuration.
     */
    public LDAPConnectionHandler2(QueueingStrategy strategy, String friendlyName) {
        super(friendlyName != null ? friendlyName : DEFAULT_FRIENDLY_NAME + " Thread");

        this.friendlyName = friendlyName;
        this.queueingStrategy = strategy;
    }

    /**
     * Indicates whether this connection handler should allow interaction with LDAPv2 clients.
     *
     * @return <CODE>true</CODE> if LDAPv2 is allowed, or <CODE>false</CODE> if not.
     */
    public boolean allowLDAPv2() {
        return currentConfig.isAllowLDAPV2();
    }

    /**
     * Indicates whether this connection handler should allow the use of the StartTLS extended operation.
     *
     * @return <CODE>true</CODE> if StartTLS is allowed, or <CODE>false</CODE> if not.
     */
    public boolean allowStartTLS() {
        return currentConfig.isAllowStartTLS() && !currentConfig.isUseSSL();
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(LDAPConnectionHandlerCfg config) {
        final ConfigChangeResult ccr = new ConfigChangeResult();

        // Note that the following properties cannot be modified:
        // * listen port and addresses
        // * use ssl
        // * ssl policy
        // * ssl cert nickname
        // * accept backlog
        // * tcp reuse address
        // * num request handler

        // Clear the stat tracker if LDAPv2 is being enabled.
        if (currentConfig.isAllowLDAPV2() != config.isAllowLDAPV2() && config.isAllowLDAPV2()) {
            statTracker.clearStatistics();
        }

        // Apply the changes.
        currentConfig = config;
        enabled = config.isEnabled();
        allowedClients = config.getAllowedClient();
        deniedClients = config.getDeniedClient();

        // Reconfigure SSL if needed.
        try {
            configureSSL(config);
        } catch (DirectoryException e) {
            logger.traceException(e);
            ccr.setResultCode(e.getResultCode());
            ccr.addMessage(e.getMessageObject());
            return ccr;
        }

        if (config.isAllowLDAPV2()) {
            DirectoryServer.registerSupportedLDAPVersion(2, this);
        } else {
            DirectoryServer.deregisterSupportedLDAPVersion(2, this);
        }

        return ccr;
    }

    private void configureSSL(LDAPConnectionHandlerCfg config) throws DirectoryException {
        protocol = config.isUseSSL() ? "LDAPS" : "LDAP";
        if (config.isUseSSL() || config.isAllowStartTLS()) {
            sslContext = createSSLContext(config);
            sslEngine = createSSLEngine(config, sslContext);
        } else {
            sslContext = null;
            sslEngine = null;
        }
    }

    @Override
    public void finalizeConnectionHandler(LocalizableMessage finalizeReason) {
        shutdownRequested = true;
        currentConfig.removeLDAPChangeListener(this);

        if (connMonitor != null) {
            DirectoryServer.deregisterMonitorProvider(connMonitor);
        }

        if (statTracker != null) {
            DirectoryServer.deregisterMonitorProvider(statTracker);
        }

        DirectoryServer.deregisterSupportedLDAPVersion(2, this);
        DirectoryServer.deregisterSupportedLDAPVersion(3, this);

        // Shutdown the connection finalizer and ensure that any pending
        // unclosed connections are closed.
        synchronized (connectionFinalizerLock) {
            connectionFinalizer.shutdown();
            connectionFinalizer = null;

            Runnable r = new ConnectionFinalizerRunnable();
            r.run(); // Flush active queue.
            r.run(); // Flush pending queue.
        }
    }

    /**
     * Retrieves information about the set of alerts that this generator may produce. The map returned should be between
     * the notification type for a particular notification and the human-readable description for that notification.
     * This alert generator must not generate any alerts with types that are not contained in this list.
     *
     * @return Information about the set of alerts that this generator may produce.
     */
    @Override
    public Map<String, String> getAlerts() {
        Map<String, String> alerts = new LinkedHashMap<>();

        alerts.put(ALERT_TYPE_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES,
                ALERT_DESCRIPTION_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES);
        alerts.put(ALERT_TYPE_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR,
                ALERT_DESCRIPTION_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR);

        return alerts;
    }

    /**
     * Retrieves the fully-qualified name of the Java class for this alert generator implementation.
     *
     * @return The fully-qualified name of the Java class for this alert generator implementation.
     */
    @Override
    public String getClassName() {
        return LDAPConnectionHandler2.class.getName();
    }

    /**
     * Retrieves the set of active client connections that have been established through this connection handler.
     *
     * @return The set of active client connections that have been established through this connection handler.
     */
    @Override
    public Collection<ClientConnection> getClientConnections() {
        return unmodifiableCollection(clientConnections);
    }

    /**
     * Retrieves the DN of the configuration entry with which this alert generator is associated.
     *
     * @return The DN of the configuration entry with which this alert generator is associated.
     */
    @Override
    public DN getComponentEntryDN() {
        return currentConfig.dn();
    }

    @Override
    public String getConnectionHandlerName() {
        return handlerName;
    }

    @Override
    public Collection<String> getEnabledSSLCipherSuites() {
        final SSLEngine engine = sslEngine;
        if (engine != null) {
            return Arrays.asList(engine.getEnabledCipherSuites());
        }
        return super.getEnabledSSLCipherSuites();
    }

    @Override
    public Collection<String> getEnabledSSLProtocols() {
        final SSLEngine engine = sslEngine;
        if (engine != null) {
            return Arrays.asList(engine.getEnabledProtocols());
        }
        return super.getEnabledSSLProtocols();
    }

    @Override
    public Collection<HostPort> getListeners() {
        return listeners;
    }

    /**
     * Retrieves the maximum length of time in milliseconds that attempts to write to LDAP client connections should be
     * allowed to block.
     *
     * @return The maximum length of time in milliseconds that attempts to write to LDAP client connections should be
     *         allowed to block, or zero if there should not be any limit imposed.
     */
    public long getMaxBlockedWriteTimeLimit() {
        return currentConfig.getMaxBlockedWriteTimeLimit();
    }

    /**
     * Retrieves the maximum ASN.1 element value length that will be allowed by this connection handler.
     *
     * @return The maximum ASN.1 element value length that will be allowed by this connection handler.
     */
    public int getMaxRequestSize() {
        return (int) currentConfig.getMaxRequestSize();
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getShutdownListenerName() {
        return handlerName;
    }

    /**
     * Retrieves the SSL client authentication policy for this connection handler.
     *
     * @return The SSL client authentication policy for this connection handler.
     */
    public SSLClientAuthPolicy getSSLClientAuthPolicy() {
        return sslClientAuthPolicy;
    }

    /**
     * Retrieves the set of statistics maintained by this connection handler.
     *
     * @return The set of statistics maintained by this connection handler.
     */
    public LDAPStatistics getStatTracker() {
        return statTracker;
    }

    @Override
    public void initializeConnectionHandler(ServerContext serverContext, LDAPConnectionHandlerCfg config)
            throws ConfigException, InitializationException {
        if (friendlyName == null) {
            friendlyName = config.name();
        }

        // Save this configuration for future reference.
        currentConfig = config;
        enabled = config.isEnabled();
        allowedClients = config.getAllowedClient();
        deniedClients = config.getDeniedClient();

        // Configure SSL if needed.
        try {
            // This call may disable the connector if wrong SSL settings
            configureSSL(config);
        } catch (DirectoryException e) {
            logger.traceException(e);
            throw new InitializationException(e.getMessageObject());
        }

        // Save properties that cannot be dynamically modified.
        allowReuseAddress = config.isAllowTCPReuseAddress();
        backlog = config.getAcceptBacklog();
        listenAddresses = new HashSet<>();
        for (InetAddress addr : config.getListenAddress()) {
            listenAddresses.add(new InetSocketAddress(addr, config.getListenPort()));
        }

        // Construct a unique name for this connection handler, and put
        // together the set of listeners.
        listeners = new LinkedList<>();
        StringBuilder nameBuffer = new StringBuilder();
        nameBuffer.append(friendlyName);
        for (InetSocketAddress a : listenAddresses) {
            listeners.add(new HostPort(a.getHostName(), a.getPort()));
            nameBuffer.append(" ");
            nameBuffer.append(a.getHostName());
        }
        nameBuffer.append(" port ");
        nameBuffer.append(config.getListenPort());
        handlerName = nameBuffer.toString();

        // Attempt to bind to the listen port on all configured addresses to
        // verify whether the connection handler will be able to start.
        LocalizableMessage errorMessage = checkAnyListenAddressInUse(config.getListenAddress(), config.getListenPort(),
                allowReuseAddress, config.dn());
        if (errorMessage != null) {
            logger.error(errorMessage);
            throw new InitializationException(errorMessage);
        }

        // Create a system property to store the LDAP(S) port the server is
        // listening to. This information can be displayed with jinfo.
        System.setProperty(protocol + "_port", String.valueOf(config.getListenPort()));

        // Create and start a connection finalizer thread for this
        // connection handler.
        connectionFinalizer = Executors.newSingleThreadScheduledExecutor(new DirectoryThread.Factory(
                "LDAP Connection Finalizer for connection handler " + toString()));

        connectionFinalizerActiveJobQueue = new ArrayList<>();
        connectionFinalizerPendingJobQueue = new ArrayList<>();

        connectionFinalizer.scheduleWithFixedDelay(new ConnectionFinalizerRunnable(), 100, 100, TimeUnit.MILLISECONDS);

        // Register the set of supported LDAP versions.
        DirectoryServer.registerSupportedLDAPVersion(3, this);
        if (config.isAllowLDAPV2()) {
            DirectoryServer.registerSupportedLDAPVersion(2, this);
        }

        // Create and register monitors.
        statTracker = new LDAPStatistics(handlerName + " Statistics");
        DirectoryServer.registerMonitorProvider(statTracker);

        connMonitor = new ClientConnectionMonitorProvider(this);
        DirectoryServer.registerMonitorProvider(connMonitor);

        // Register this as a change listener.
        config.addLDAPChangeListener(this);
    }

    @Override
    public boolean isConfigurationAcceptable(ConnectionHandlerCfg configuration,
            List<LocalizableMessage> unacceptableReasons) {
        LDAPConnectionHandlerCfg config = (LDAPConnectionHandlerCfg) configuration;

        if (currentConfig == null || (!currentConfig.isEnabled() && config.isEnabled())) {
            // Attempt to bind to the listen port on all configured addresses to
            // verify whether the connection handler will be able to start.
            LocalizableMessage errorMessage = checkAnyListenAddressInUse(config.getListenAddress(),
                    config.getListenPort(), config.isAllowTCPReuseAddress(), config.dn());
            if (errorMessage != null) {
                unacceptableReasons.add(errorMessage);
                return false;
            }
        }

        if (config.isEnabled()
        // Check that the SSL configuration is valid.
                && (config.isUseSSL() || config.isAllowStartTLS())) {
            try {
                createSSLEngine(config, createSSLContext(config));
            } catch (DirectoryException e) {
                logger.traceException(e);

                unacceptableReasons.add(e.getMessageObject());
                return false;
            }
        }

        return true;
    }

    /**
     * Checks whether any listen address is in use for the given port. The check is performed by binding to each address
     * and port.
     *
     * @param listenAddresses
     *            the listen {@link InetAddress} to test
     * @param listenPort
     *            the listen port to test
     * @param allowReuseAddress
     *            whether addresses can be reused
     * @param configEntryDN
     *            the configuration entry DN
     * @return an error message if at least one of the address is already in use, null otherwise.
     */
    private LocalizableMessage checkAnyListenAddressInUse(Collection<InetAddress> listenAddresses, int listenPort,
            boolean allowReuseAddress, DN configEntryDN) {
        for (InetAddress a : listenAddresses) {
            try {
                if (StaticUtils.isAddressInUse(a, listenPort, allowReuseAddress)) {
                    throw new IOException(ERR_CONNHANDLER_ADDRESS_INUSE.get().toString());
                }
            } catch (IOException e) {
                logger.traceException(e);
                return ERR_CONNHANDLER_CANNOT_BIND.get("LDAP", configEntryDN, a.getHostAddress(), listenPort,
                        getExceptionMessage(e));
            }
        }
        return null;
    }

    @Override
    public boolean isConfigurationChangeAcceptable(LDAPConnectionHandlerCfg config,
            List<LocalizableMessage> unacceptableReasons) {
        return isConfigurationAcceptable(config, unacceptableReasons);
    }

    @Override
    public void processServerShutdown(LocalizableMessage reason) {
        shutdownRequested = true;
    }

    void stopListener() {
        if (listener != null) {
            listener.close();
            listener = null;
            logger.info(NOTE_CONNHANDLER_STOPPED_LISTENING, handlerName);
        }
    }

    private void startListener() throws IOException {
        listener = new LDAPListener(
                listenAddresses,
                new Function<LDAPClientContext,
                             ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                             LdapException>() {
                    @Override
                    public ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>> apply(
                            LDAPClientContext clientContext) throws LdapException {
                        final LDAPClientConnection2 conn = canAccept(clientContext);
                        clientConnections.add(conn);
                        logConnect(conn);
                        clientContext.addListener(new LDAPClientContextEventListener() {
                            @Override
                            public void handleConnectionError(final LDAPClientContext context, final Throwable error) {
                                clientConnections.remove(conn);
                            }

                            @Override
                            public void handleConnectionDisconnected(final LDAPClientContext context,
                                    final ResultCode resultCode, String diagnosticMessage) {
                                clientConnections.remove(conn);
                            }

                            @Override
                            public void handleConnectionClosed(final LDAPClientContext context,
                                    final UnbindRequest unbindRequest) {
                                clientConnections.remove(conn);
                            }
                        });
                        return new ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>() {
                            @Override
                            public Stream<Response> handle(final LDAPClientContext context,
                                    final LdapRequestEnvelope request) throws Exception {
                                return conn.handle(queueingStrategy, request);
                            }
                        };
                    }
                }, Options.defaultOptions()
                          .set(LDAPListener.CONNECT_MAX_BACKLOG, backlog)
                          .set(LDAPListener.REQUEST_MAX_SIZE_IN_BYTES, (int) currentConfig.getMaxRequestSize()));
        logger.info(NOTE_CONNHANDLER_STARTED_LISTENING, handlerName);
    }

    /**
     * Operates in a loop, accepting new connections and ensuring that requests on those connections are handled
     * properly.
     */
    @Override
    public void run() {
        setName(handlerName);
        boolean starting = true;
        boolean lastIterationFailed = false;

        while (!shutdownRequested) {
            // If this connection handler is not enabled, then just sleep for a bit and check again.
            if (!this.enabled) {
                if (listener != null) {
                    stopListener();
                }

                if (starting) {
                    // This may happen if there was an initialisation error which led to disable the connector.
                    // The main thread is waiting for the connector to listen on its port, which will not occur yet,
                    // so notify here to allow the server startup to complete.
                    synchronized (waitListen) {
                        starting = false;
                        waitListen.notify();
                    }
                }

                StaticUtils.sleep(1000);
                continue;
            }

            if (listener != null) {
                // If already listening, then sleep for a bit and check again.
                StaticUtils.sleep(1000);
                continue;
            }

            try {
                // At this point, the connection Handler either started correctly or failed
                // to start but the start process should be notified and resume its work in any cases.
                synchronized (waitListen) {
                    waitListen.notify();
                }

                // If we have gotten here, then we are about to start listening
                // for the first time since startup or since we were previously disabled.
                startListener();
                lastIterationFailed = false;
            } catch (Exception e) {
                // Clean up the messed up HTTP server
                stopListener();

                // Error + alert about the horked config
                logger.traceException(e);
                logger.error(ERR_CONNHANDLER_CANNOT_ACCEPT_CONNECTION, friendlyName, currentConfig.dn(),
                        getExceptionMessage(e));

                if (lastIterationFailed) {
                    // The last time through the accept loop we also encountered a failure.
                    // Rather than enter a potential infinite loop of failures,
                    // disable this acceptor and log an error.
                    LocalizableMessage message = ERR_CONNHANDLER_CONSECUTIVE_ACCEPT_FAILURES.get(friendlyName,
                            currentConfig.dn(), stackTraceToSingleLineString(e));
                    logger.error(message);

                    DirectoryServer.sendAlertNotification(this,
                            ALERT_TYPE_HTTP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES, message);
                    this.enabled = false;
                } else {
                    lastIterationFailed = true;
                }
            }
        }

        // Initiate shutdown
        stopListener();
    }

    private LDAPClientConnection2 canAccept(LDAPClientContext clientContext) throws LdapException {
        // Check to see if the core server rejected the
        // connection (e.g., already too many connections
        // established).
        final LDAPClientConnection2 clientConnection = new LDAPClientConnection2(this, clientContext, getProtocol(),
                currentConfig.isKeepStats());
        if (clientConnection.getConnectionID() < 0) {
            clientConnection.disconnect(DisconnectReason.ADMIN_LIMIT_EXCEEDED, true,
                    ERR_CONNHANDLER_REJECTED_BY_SERVER.get());
            throw LdapException.newLdapException(ResultCode.ADMIN_LIMIT_EXCEEDED);
        }

        InetAddress clientAddr = clientConnection.getRemoteAddress();
        // Check to see if the client is on the denied list.
        // If so, then reject it immediately.
        if (!deniedClients.isEmpty() && AddressMask.matchesAny(deniedClients, clientAddr)) {
            clientConnection.disconnect(
                    DisconnectReason.CONNECTION_REJECTED,
                    currentConfig.isSendRejectionNotice(),
                    ERR_CONNHANDLER_DENIED_CLIENT.get(clientConnection.getClientHostPort(),
                            clientConnection.getServerHostPort()));
            throw LdapException.newLdapException(ResultCode.CONSTRAINT_VIOLATION);
        }
        // Check to see if there is an allowed list and if
        // there is whether the client is on that list. If
        // not, then reject the connection.
        if (!allowedClients.isEmpty() && !AddressMask.matchesAny(allowedClients, clientAddr)) {
            clientConnection.disconnect(
                    DisconnectReason.CONNECTION_REJECTED,
                    currentConfig.isSendRejectionNotice(),
                    ERR_CONNHANDLER_DISALLOWED_CLIENT.get(clientConnection.getClientHostPort(),
                            clientConnection.getServerHostPort()));
            throw LdapException.newLdapException(ResultCode.CONSTRAINT_VIOLATION);
        }

        // If we've gotten here, then we'll take the
        // connection so invoke the post-connect plugins and
        // register the client connection with a request
        // handler.
        try {
            PluginConfigManager pluginManager = DirectoryServer.getPluginConfigManager();
            PluginResult.PostConnect pluginResult = pluginManager.invokePostConnectPlugins(clientConnection);
            if (!pluginResult.continueProcessing()) {
                clientConnection.disconnect(pluginResult.getDisconnectReason(),
                        pluginResult.sendDisconnectNotification(), pluginResult.getErrorMessage());
                throw LdapException.newLdapException(ResultCode.CONSTRAINT_VIOLATION);
            }
        } catch (Exception e) {
            logger.traceException(e);

            LocalizableMessage message = INFO_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT.get(
                    clientConnection.getClientHostPort(), clientConnection.getServerHostPort(), getExceptionMessage(e));
            logger.debug(message);

            clientConnection.disconnect(DisconnectReason.SERVER_ERROR, currentConfig.isSendRejectionNotice(), message);
            throw LdapException.newLdapException(ResultCode.OPERATIONS_ERROR);
        }

        if (useSSL()) {
            try {
                clientContext.enableTLS(createSSLEngine(), false);
            } catch (DirectoryException e) {
                throw LdapException.newLdapException(e.getResultCode(), e);
            }
        }

        return clientConnection;
    }

    /**
     * Appends a string representation of this connection handler to the provided buffer.
     *
     * @param buffer
     *            The buffer to which the information should be appended.
     */
    @Override
    public void toString(StringBuilder buffer) {
        buffer.append(handlerName);
    }

    /**
     * Indicates whether this connection handler should use SSL to communicate with clients.
     *
     * @return {@code true} if this connection handler should use SSL to communicate with clients, or {@code false} if
     *         not.
     */
    public boolean useSSL() {
        return currentConfig.isUseSSL();
    }

    SSLEngine createSSLEngine() throws DirectoryException {
        return createSSLEngine(currentConfig, sslContext);
    }

    private SSLEngine createSSLEngine(LDAPConnectionHandlerCfg config, SSLContext sslContext)
            throws DirectoryException {
        try {
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);

            final Set<String> protocols = config.getSSLProtocol();
            if (!protocols.isEmpty()) {
                sslEngine.setEnabledProtocols(protocols.toArray(new String[0]));
            } else { //enforce enable TLSv1.3 to avoid jdk 11 TLSv1.3 problem
            	String[] enabledProtocols = sslEngine.getEnabledProtocols();
            	String[] enabledProtocolsNoTLSv13 = ArrayUtils.remove(enabledProtocols, "TLSv1.3");
            	sslEngine.setEnabledProtocols(enabledProtocolsNoTLSv13);
            }

            final Set<String> ciphers = config.getSSLCipherSuite();
            if (!ciphers.isEmpty()) {
                sslEngine.setEnabledCipherSuites(ciphers.toArray(new String[0]));
            }

            switch (config.getSSLClientAuthPolicy()) {
            case DISABLED:
                sslEngine.setNeedClientAuth(false);
                sslEngine.setWantClientAuth(false);
                break;
            case REQUIRED:
                sslEngine.setWantClientAuth(true);
                sslEngine.setNeedClientAuth(true);
                break;
            case OPTIONAL:
            default:
                sslEngine.setNeedClientAuth(false);
                sslEngine.setWantClientAuth(true);
                break;
            }

            return sslEngine;
        } catch (Exception e) {
            logger.traceException(e);
            ResultCode resCode = DirectoryServer.getCoreConfigManager().getServerErrorResultCode();
            LocalizableMessage message = ERR_CONNHANDLER_SSL_CANNOT_INITIALIZE.get(getExceptionMessage(e));
            throw new DirectoryException(resCode, message, e);
        }
    }

    private void disableAndWarnIfUseSSL(LDAPConnectionHandlerCfg config) {
        if (config.isUseSSL()) {
            logger.warn(INFO_DISABLE_CONNECTION, friendlyName);
            enabled = false;
        }
    }

    private SSLContext createSSLContext(LDAPConnectionHandlerCfg config) throws DirectoryException {
        try {
            DN keyMgrDN = config.getKeyManagerProviderDN();
            final ServerContext serverContext = DirectoryServer.getInstance().getServerContext();
            KeyManagerProvider<?> keyManagerProvider = serverContext.getKeyManagerProvider(keyMgrDN);
            if (keyManagerProvider == null) {
                logger.error(ERR_NULL_KEY_PROVIDER_MANAGER, keyMgrDN, friendlyName);
                disableAndWarnIfUseSSL(config);
                keyManagerProvider = new NullKeyManagerProvider();
                // The SSL connection is unusable without a key manager provider
            } else if (!keyManagerProvider.containsAtLeastOneKey()) {
                logger.error(ERR_INVALID_KEYSTORE, friendlyName);
                disableAndWarnIfUseSSL(config);
            }

            final SortedSet<String> aliases = new TreeSet<>(config.getSSLCertNickname());
            final KeyManager[] keyManagers;
            if (aliases.isEmpty()) {
                keyManagers = keyManagerProvider.getKeyManagers();
            } else {
                final Iterator<String> it = aliases.iterator();
                while (it.hasNext()) {
                    if (!keyManagerProvider.containsKeyWithAlias(it.next())) {
                        logger.error(ERR_KEYSTORE_DOES_NOT_CONTAIN_ALIAS, aliases, friendlyName);
                        it.remove();
                    }
                }

                if (aliases.isEmpty()) {
                    disableAndWarnIfUseSSL(config);
                }
                keyManagers = SelectableCertificateKeyManager.wrap(keyManagerProvider.getKeyManagers(), aliases,
                        friendlyName);
            }

            final DN trustMgrDN = config.getTrustManagerProviderDN();
            final TrustManager[] trustManagers =
                    trustMgrDN == null ? null : serverContext.getTrustManagerProvider(trustMgrDN).getTrustManagers();
            SSLContext sslContext = SSLContext.getInstance(SSL_CONTEXT_INSTANCE_NAME);
            if (isFips()) {
            	sslContext.init(keyManagerProvider.getKeyManagers(), trustManagers, null);
            } else {
            	sslContext.init(keyManagers, trustManagers, null);
            }
            return sslContext;
        } catch (Exception e) {
            logger.traceException(e);
            ResultCode resCode = DirectoryServer.getCoreConfigManager().getServerErrorResultCode();
            LocalizableMessage message = ERR_CONNHANDLER_SSL_CANNOT_INITIALIZE.get(getExceptionMessage(e));
            throw new DirectoryException(resCode, message, e);
        }
    }

    /**
     * Enqueue a connection finalizer which will be invoked after a short delay.
     *
     * @param r
     *            The connection finalizer runnable.
     */
    void registerConnectionFinalizer(Runnable r) {
        synchronized (connectionFinalizerLock) {
            if (connectionFinalizer != null) {
                connectionFinalizerPendingJobQueue.add(r);
            } else {
                // Already finalized - invoked immediately.
                r.run();
            }
        }
    }
}
