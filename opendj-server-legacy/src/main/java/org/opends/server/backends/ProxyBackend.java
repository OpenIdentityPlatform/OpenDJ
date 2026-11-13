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
 * Copyright 2025-2026 3A Systems, LLC.
 */
package org.opends.server.backends;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.*;
import org.forgerock.opendj.ldap.responses.*;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.server.config.server.ProxyBackendCfg;
import org.forgerock.util.Options;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.*;
import org.opends.server.discovery.Partition;
import org.opends.server.discovery.ReplicationServiceDiscoveryMechanism;
import org.opends.server.discovery.ServiceDiscoveryChangeListener;
import org.opends.server.discovery.ServiceDiscoveryMechanism;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;
import org.opends.server.types.*;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.AUTHN_BIND_REQUEST;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.CONNECT_TIMEOUT;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.HEARTBEAT_ENABLED;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.REQUEST_TIMEOUT;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.forgerock.util.time.Duration.duration;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.ServerConstants.OID_PROXIED_AUTH_V1;
import static org.opends.server.util.ServerConstants.OID_PROXIED_AUTH_V2;

public class ProxyBackend extends Backend<ProxyBackendCfg>
        implements ConfigurationChangeListener<ProxyBackendCfg>, ServiceDiscoveryChangeListener {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    ProxyBackendCfg cfg;
    ServerContext serverContext;
    private final Object routingLock = new Object();
    private final Map<Partition, DynamicRoute> dynamicRoutes = new IdentityHashMap<>();
    private final Map<ClientConnection, UserSession> userSessions = new IdentityHashMap<>();
    private DynamicRoute staticRoute;
    private ServiceDiscoveryMechanism<?> discovery;
    private boolean listening;
    private ScheduledFuture<?> userPoolCleanup;
    private volatile ProxyRewriter rewriter = new ProxyRewriter(null, null, Collections.emptyList(),
            Schema.getDefaultSchema());

    @Override
    public void configureBackend(ProxyBackendCfg cfg, ServerContext serverContext) throws ConfigException {
        this.serverContext=serverContext;
        final ConfigChangeResult result = applyConfigurationChange(cfg);
        if (result.getResultCode() != ResultCode.SUCCESS) {
            throw new ConfigException(result.getMessages().get(0));
        }
    }

    ConnectionFactory bindFactory=null;
    ConnectionFactory factory=null;
    @Override
    public ConfigChangeResult applyConfigurationChange(ProxyBackendCfg configuration) {
        final ProxyRewriter newRewriter;
        try {
            newRewriter = createRewriter(configuration, serverContext);
        } catch (IllegalArgumentException e) {
            final ConfigChangeResult result = new ConfigChangeResult();
            result.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            result.addMessage(LocalizableMessage.raw(e.getMessage()));
            return result;
        }
        final ProxyBackendCfg previous;
        final boolean active;
        synchronized (routingLock) {
            previous = cfg;
            active = listening;
        }
        final ServiceDiscoveryMechanism<?> discoveryMechanism=serverContext.getServiceDiscoveryMechanismConfigManager()
                .getMechanism(configuration.getShard());

        final Partition partition=discoveryMechanism.getPartition();

        final List<ConnectionFactory> primaryBind = new LinkedList<>();
        final List<ConnectionFactory> secondaryBind = new LinkedList<>();
        final List<ConnectionFactory> primary = new LinkedList<>();
        final List<ConnectionFactory> secondary = new LinkedList<>();
        if (partition!=null) {
            for (Partition.Server server : partition.getPrimaryServers()) {
                primaryBind.add(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        Options.copyOf(server.getOptions())
                                .set(HEARTBEAT_ENABLED, true)
                ));
                if (hasServiceCredentials(discoveryMechanism)) primary.add(Connections.newCachedConnectionPool(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        Options.copyOf(server.getOptions())
                                .set(HEARTBEAT_ENABLED, true)
                                .set(AUTHN_BIND_REQUEST, newSimpleBindRequest(discoveryMechanism.cfg.getBindDN().toString(), discoveryMechanism.cfg.getBindPassword().toCharArray()))
                )));
            }
            for (Partition.Server server : partition.getSecondaryServers()) {
                secondaryBind.add(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        Options.copyOf(server.getOptions())
                                .set(HEARTBEAT_ENABLED, true)
                ));
                if (hasServiceCredentials(discoveryMechanism)) secondary.add(Connections.newCachedConnectionPool(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        Options.copyOf(server.getOptions())
                                .set(HEARTBEAT_ENABLED, true)
                                .set(AUTHN_BIND_REQUEST, newSimpleBindRequest(discoveryMechanism.cfg.getBindDN().toString(), discoveryMechanism.cfg.getBindPassword().toCharArray()))
                )));
            }
        }

        final ConnectionFactory newBindFactory = prioritizedAffinity(primaryBind, secondaryBind);
        final ConnectionFactory newFactory = prioritizedAffinity(primary, secondary);
        final Runnable applyRoute = () -> {
            synchronized (routingLock) {
                if (listening && discovery != discoveryMechanism) {
                    if (discovery != null) {
                        discovery.deregisterChangeListener(this);
                    }
                    discoveryMechanism.registerChangeListener(this);
                }
                retireDynamicRoutes();
                retireRoute(staticRoute);
                discovery = discoveryMechanism;
                cfg = configuration;
                bindFactory = newBindFactory;
                factory = newFactory;
                staticRoute = new DynamicRoute(newBindFactory, newFactory, partition,
                        hasServiceCredentials(discoveryMechanism));
                rewriter = newRewriter;
            }
        };
        if (active && configuration != previous && !configuration.getBaseDN().equals(previous.getBaseDN())) {
            try {
                // BackendConfigManager takes its write lock before routingLock during shutdown.
                // Apply the routing state under that same lock after replacing the suffixes.
                serverContext.getBackendConfigManager().replaceBackendBaseDNs(this,
                        previous.getBaseDN(), configuration.getBaseDN(), applyRoute);
            } catch (DirectoryException e) {
                if (newBindFactory != null) {
                    newBindFactory.close();
                }
                if (newFactory != null) {
                    newFactory.close();
                }
                final ConfigChangeResult result = new ConfigChangeResult();
                result.setResultCode(e.getResultCode());
                result.addMessage(e.getMessageObject());
                return result;
            }
        } else {
            applyRoute.run();
        }

        return new ConfigChangeResult();
    }

    private static boolean hasServiceCredentials(ServiceDiscoveryMechanism<?> mechanism) {
        return mechanism != null && mechanism.cfg.getBindDN() != null && mechanism.cfg.getBindPassword() != null;
    }

    private static ProxyRewriter createRewriter(ProxyBackendCfg configuration, ServerContext context) {
        final DN remoteBase = configuration.getRemoteBaseDN();
        final Set<DN> bases = configuration.getBaseDN();
        if (remoteBase != null && (bases == null || bases.size() != 1)) {
            throw new IllegalArgumentException("remote-base-dn requires exactly one proxy base-dn");
        }
        final DN clientBase = remoteBase == null ? null : bases.iterator().next();
        return new ProxyRewriter(clientBase, remoteBase, configuration.getAttributeMapping(), context.getSchema());
    }

    @Override
    public boolean isConfigurationAcceptable(ProxyBackendCfg configuration, List<LocalizableMessage> reasons,
                                             ServerContext context) {
        try {
            createRewriter(configuration, context);
            return true;
        } catch (IllegalArgumentException e) {
            reasons.add(LocalizableMessage.raw(e.getMessage()));
            return false;
        }
    }

    private static ConnectionFactory prioritizedAffinity(List<ConnectionFactory> primary,
                                                         List<ConnectionFactory> secondary) {
        if (primary.isEmpty() && secondary.isEmpty()) {
            return null;
        }
        if (primary.isEmpty() || secondary.isEmpty()) {
            return Connections.newAffinityRequestLoadBalancer(primary.isEmpty() ? secondary : primary,
                    Options.defaultOptions());
        }

        final ConnectionFactory secondaryAffinity =
                Connections.newAffinityRequestLoadBalancer(secondary, Options.defaultOptions());
        final List<ConnectionFactory> routes = new ArrayList<>(primary.size());
        for (int start = 0; start < primary.size(); start++) {
            final List<ConnectionFactory> candidates = new ArrayList<>(primary.size() + 1);
            for (int offset = 0; offset < primary.size(); offset++) {
                candidates.add(primary.get((start + offset) % primary.size()));
            }
            // The request-level secondary balancer must be last: it returns a
            // virtual connection before checking whether a server is reachable.
            candidates.add(secondaryAffinity);
            routes.add(Connections.newFailoverLoadBalancer(candidates, Options.defaultOptions()));
        }
        return Connections.newAffinityRequestLoadBalancer(routes, Options.defaultOptions());
    }

    private static final class DynamicRoute {
        final ConnectionFactory bind;
        final ConnectionFactory authenticated;
        final List<Partition.Server> servers;
        final int primaryServerCount;
        final Map<Partition.Server, LDAPConnectionFactory> physicalFactories = new LinkedHashMap<>();
        final Map<List<Object>, UserPool> userPools = new HashMap<>();
        final boolean hasServiceCredentials;
        final boolean fixedAuthorizationMode;
        Boolean directUserMode;
        long nextCapabilityProbe;
        int users;
        boolean retired;

        DynamicRoute(ConnectionFactory bind, ConnectionFactory authenticated) {
            this.bind = bind;
            this.authenticated = authenticated;
            this.servers = Collections.emptyList();
            this.primaryServerCount = 0;
            this.hasServiceCredentials = authenticated != null;
            this.directUserMode = false;
            this.fixedAuthorizationMode = true;
        }

        DynamicRoute(ConnectionFactory bind, ConnectionFactory authenticated, Partition partition,
                     boolean credentials) {
            this.bind = bind;
            this.authenticated = authenticated;
            this.servers = orderedServers(partition);
            this.primaryServerCount = partition == null ? 0 : partition.getPrimaryServers().size();
            this.hasServiceCredentials = credentials;
            directUserMode = credentials ? null : true;
            fixedAuthorizationMode = !credentials;
        }

        DynamicRoute(Partition partition, ServiceDiscoveryMechanism<?> mechanism) {
            final List<ConnectionFactory> primaryBind = new ArrayList<>();
            final List<ConnectionFactory> secondaryBind = new ArrayList<>();
            final List<ConnectionFactory> primary = new ArrayList<>();
            final List<ConnectionFactory> secondary = new ArrayList<>();
            addServers(partition.getPrimaryServers(), mechanism, primaryBind, primary);
            addServers(partition.getSecondaryServers(), mechanism, secondaryBind, secondary);
            bind = prioritizedAffinity(primaryBind, secondaryBind);
            authenticated = prioritizedAffinity(primary, secondary);
            servers = orderedServers(partition);
            primaryServerCount = partition.getPrimaryServers().size();
            hasServiceCredentials = hasServiceCredentials(mechanism);
            directUserMode = hasServiceCredentials ? null : true;
            fixedAuthorizationMode = !hasServiceCredentials;
        }

        private static List<Partition.Server> orderedServers(Partition partition) {
            if (partition == null) {
                return Collections.emptyList();
            }
            final List<Partition.Server> result = new ArrayList<>(partition.getPrimaryServers());
            result.addAll(partition.getSecondaryServers());
            return result;
        }

        private static void addServers(Collection<Partition.Server> servers, ServiceDiscoveryMechanism<?> mechanism,
                                       List<ConnectionFactory> bind, List<ConnectionFactory> authenticated) {
            for (Partition.Server server : servers) {
                final Options options = Options.copyOf(server.getOptions()).set(HEARTBEAT_ENABLED, true);
                // A successful client bind changes the physical connection's identity. Never
                // reuse it for anonymous requests after a capability-driven mode change.
                bind.add(new LDAPConnectionFactory(
                        server.getHostPort().getHost(), server.getHostPort().getPort(), options));
                if (!hasServiceCredentials(mechanism)) {
                    continue;
                }
                final Options authOptions = Options.copyOf(options).set(AUTHN_BIND_REQUEST,
                        newSimpleBindRequest(mechanism.cfg.getBindDN().toString(),
                                mechanism.cfg.getBindPassword().toCharArray()));
                authenticated.add(Connections.newCachedConnectionPool(new LDAPConnectionFactory(
                        server.getHostPort().getHost(), server.getHostPort().getPort(), authOptions)));
            }
        }

        void close() {
            for (UserPool pool : userPools.values()) {
                pool.close();
            }
            userPools.clear();
            for (LDAPConnectionFactory physicalFactory : physicalFactories.values()) {
                physicalFactory.close();
            }
            physicalFactories.clear();
            if (bind != null) {
                bind.close();
            }
            if (authenticated != null) {
                authenticated.close();
            }
        }
    }

    /** Connections in this pool have all been bound to the same identity on the same server. */
    private static final class UserPool {
        private static final int MAX_CONNECTIONS = 16;
        private static final long IDLE_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(5);
        private final Deque<IdleConnection> idle = new ArrayDeque<>();
        private int sessions;
        private int borrowed;
        private boolean closed;

        private static final class IdleConnection {
            final Connection connection;
            final long returnedAt = System.nanoTime();

            IdleConnection(Connection connection) {
                this.connection = connection;
            }
        }

        synchronized void addSession(Connection connection) {
            expireIdle();
            sessions++;
            if (closed || idle.size() + borrowed >= MAX_CONNECTIONS) {
                connection.close();
            } else {
                idle.addLast(new IdleConnection(connection));
            }
        }

        synchronized boolean removeSession() {
            if (--sessions == 0) {
                close();
                return true;
            }
            return false;
        }

        synchronized Connection borrow() throws LdapException {
            expireIdle();
            while (!idle.isEmpty()) {
                final Connection connection = idle.removeFirst().connection;
                if (!connection.isValid()) {
                    connection.close();
                    continue;
                }
                borrowed++;
                return new AbstractConnectionWrapper<Connection>(connection) {
                    private boolean returned;

                    @Override
                    public void close() {
                        synchronized (UserPool.this) {
                            if (returned) {
                                return;
                            }
                            returned = true;
                            borrowed--;
                            if (closed || !connection.isValid()) {
                                connection.close();
                            } else {
                                idle.addLast(new IdleConnection(connection));
                            }
                        }
                    }
                };
            }
            throw LdapException.newLdapException(borrowed > 0 ? ResultCode.BUSY : ResultCode.UNAVAILABLE,
                    borrowed > 0 ? "All user connections are in use" : "User must bind again to reconnect");
        }

        synchronized void close() {
            closed = true;
            while (!idle.isEmpty()) {
                idle.removeFirst().connection.close();
            }
        }

        synchronized void expireIdle() {
            final long now = System.nanoTime();
            for (Iterator<IdleConnection> iterator = idle.iterator(); iterator.hasNext();) {
                final IdleConnection entry = iterator.next();
                if (!entry.connection.isValid()) {
                    iterator.remove();
                    entry.connection.close();
                }
            }
            while (idle.size() > 1 && now - idle.peekFirst().returnedAt >= IDLE_TIMEOUT_NANOS) {
                idle.removeFirst().connection.close();
            }
        }
    }

    private static final class UserSession {
        final DynamicRoute route;
        final List<Object> key;
        final UserPool pool;
        Runnable cleanup;

        UserSession(DynamicRoute route, List<Object> key, UserPool pool) {
            this.route = route;
            this.key = key;
            this.pool = pool;
        }
    }

    private void removeUserSession(ClientConnection client, UserSession session) {
        synchronized (routingLock) {
            if (userSessions.remove(client, session) && session.pool.removeSession()) {
                session.route.userPools.remove(session.key, session.pool);
            }
        }
    }

    private static synchronized LDAPConnectionFactory physicalFactory(DynamicRoute route, Partition.Server server) {
        return route.physicalFactories.computeIfAbsent(server, key -> new LDAPConnectionFactory(
                key.getHostPort().getHost(), key.getHostPort().getPort(),
                Options.copyOf(key.getOptions()).set(HEARTBEAT_ENABLED, true)));
    }

    private static boolean usesUserConnections(DynamicRoute route) {
        synchronized (route) {
            if (route.directUserMode != null && (route.fixedAuthorizationMode
                    || route.nextCapabilityProbe - System.nanoTime() > 0)) {
                return route.directUserMode;
            }
            boolean supportedServer = false;
            boolean unsupportedServer = false;
            boolean unknownServer = false;
            for (Partition.Server server : route.servers) {
                final Options options = Options.copyOf(server.getOptions())
                        .set(CONNECT_TIMEOUT, duration(2, TimeUnit.SECONDS))
                        .set(REQUEST_TIMEOUT, duration(2, TimeUnit.SECONDS));
                try (LDAPConnectionFactory probe = new LDAPConnectionFactory(server.getHostPort().getHost(),
                        server.getHostPort().getPort(), options); Connection connection = probe.getConnection()) {
                    final org.forgerock.opendj.ldap.Entry root = connection.searchSingleEntry("",
                            SearchScope.BASE_OBJECT, "(objectClass=*)", "supportedControl");
                    final org.forgerock.opendj.ldap.Attribute supported = root == null ? null
                            : root.getAttribute("supportedControl");
                    if (supported == null || !supported.contains(ByteString.valueOfUtf8(OID_PROXIED_AUTH_V2))) {
                        unsupportedServer = true;
                        break;
                    }
                    supportedServer = true;
                } catch (LdapException e) {
                    unknownServer = true;
                }
            }
            final boolean direct = unsupportedServer || !supportedServer;
            route.directUserMode = direct;
            route.nextCapabilityProbe = System.nanoTime()
                    + (unknownServer ? TimeUnit.SECONDS.toNanos(30) : TimeUnit.MINUTES.toNanos(5));
            return direct;
        }
    }

    private Result bindUser(DynamicRoute route, BindOperation request, ProxyRewriter mapping,
                            List<org.forgerock.opendj.ldap.controls.Control> controls) {
        final DN remoteDN = mapping.dn(request.getBindDN(), true);
        final byte[] password = request.getSimplePassword().toByteArray();
        LdapException lastFailure = null;
        try {
            for (Partition.Server server : userBindServers(route, remoteDN)) {
                Connection connection = null;
                try {
                    try {
                        connection = physicalFactory(route, server).getConnection();
                    } catch (LdapException e) {
                        lastFailure = e;
                        final ResultCode code = e.getResult().getResultCode();
                        if (code.equals(ResultCode.CLIENT_SIDE_CONNECT_ERROR)
                                || code.equals(ResultCode.CLIENT_SIDE_SERVER_DOWN)
                                || code.equals(ResultCode.CLIENT_SIDE_TIMEOUT)
                                || code.equals(ResultCode.UNAVAILABLE)) {
                            continue;
                        }
                        return e.getResult();
                    }
                    final SimpleBindRequest bind = newSimpleBindRequest(remoteDN.toString(), password);
                    addRequestControls(bind, controls);
                    final Result result;
                    try {
                        result = connection.bind(bind);
                    } catch (LdapException e) {
                        final ResultCode code = e.getResult().getResultCode();
                        if (code.equals(ResultCode.CLIENT_SIDE_CONNECT_ERROR)
                                || code.equals(ResultCode.CLIENT_SIDE_SERVER_DOWN)
                                || code.equals(ResultCode.CLIENT_SIDE_TIMEOUT)) {
                            final Result failure = Responses.newResult(ResultCode.UNAVAILABLE);
                            failure.setDiagnosticMessage("Remote connection failed during bind; the client must bind again");
                            return failure;
                        }
                        return e.getResult();
                    } finally {
                        Arrays.fill(bind.getPassword(), (byte) 0);
                    }
                    if (result.getResultCode() != ResultCode.SUCCESS) {
                        return result;
                    }
                    if (request.isInternalOperation()) {
                        return result;
                    }
                    final ClientConnection client = request.getClientConnection();
                    synchronized (client) {
                        final AuthenticationInfo identity = AuthenticationInfo.internal(
                                new Entry(request.getBindDN(), null, null, null));
                        client.setAuthenticationInfo(identity);
                        final UserSession session;
                        synchronized (routingLock) {
                            if (route.retired) {
                                client.setAuthenticationInfo(AuthenticationInfo.anonymous());
                                return Responses.newResult(ResultCode.UNAVAILABLE);
                            }
                            final List<Object> key = Arrays.asList(remoteDN, server);
                            final UserPool pool = route.userPools.computeIfAbsent(key, ignored -> new UserPool());
                            pool.addSession(connection);
                            connection = null;
                            session = new UserSession(route, key, pool);
                            session.cleanup = () -> removeUserSession(client, session);
                            userSessions.put(client, session);
                        }
                        if (!client.addAuthenticationChangeListener(identity, session.cleanup)) {
                            removeUserSession(client, session);
                            client.setAuthenticationInfo(AuthenticationInfo.anonymous());
                            return Responses.newResult(ResultCode.UNAVAILABLE);
                        }
                    }
                    return result;
                } finally {
                    if (connection != null) {
                        connection.close();
                    }
                }
            }
            final Result unavailable = Responses.newResult(ResultCode.UNAVAILABLE);
            if (lastFailure != null) {
                unavailable.setDiagnosticMessage(lastFailure.getResult().getDiagnosticMessage());
            }
            return unavailable;
        } finally {
            Arrays.fill(password, (byte) 0);
        }
    }

    private static List<Partition.Server> userBindServers(DynamicRoute route, DN dn) {
        final List<Partition.Server> result = new ArrayList<>(route.servers.size());
        addAffinityServers(result, route.servers, 0, route.primaryServerCount, dn);
        addAffinityServers(result, route.servers, route.primaryServerCount,
                route.servers.size() - route.primaryServerCount, dn);
        return result;
    }

    private static void addAffinityServers(List<Partition.Server> result, List<Partition.Server> servers,
                                           int start, int count, DN dn) {
        if (count == 0) {
            return;
        }
        final int hash = dn.hashCode();
        final int first = hash == Integer.MIN_VALUE ? 0 : Math.abs(hash) % count;
        for (int offset = 0; offset < count; offset++) {
            result.add(servers.get(start + (first + offset) % count));
        }
    }

    private void retireDynamicRoutes() {
        for (DynamicRoute route : dynamicRoutes.values()) {
            retireRoute(route);
        }
        dynamicRoutes.clear();
    }

    private void retireRoute(DynamicRoute route) {
        if (route != null) {
            route.retired = true;
            if (route.users == 0) {
                route.close();
            }
        }
    }

    @Override
    public void serviceChanged(ServiceDiscoveryMechanism<?> changedService) {
        synchronized (routingLock) {
            if (discovery == changedService) {
                retireDynamicRoutes();
                if (!(changedService instanceof ReplicationServiceDiscoveryMechanism) && cfg != null) {
                    final ConfigChangeResult result = applyConfigurationChange(cfg);
                    if (result.getResultCode() != ResultCode.SUCCESS) {
                        retireRoute(staticRoute);
                        staticRoute = null;
                        bindFactory = null;
                        factory = null;
                        logger.warn(LocalizableMessage.raw("Proxy route is unavailable after discovery changed: "
                                + result.getMessages()));
                    }
                }
            }
        }
    }

    private static DN targetDN(Operation operation) {
        switch (operation.getOperationType()) {
            case BIND: return ((BindOperation) operation).getBindDN();
            case ADD: return ((AddOperation) operation).getEntryDN();
            case MODIFY: return ((ModifyOperation) operation).getEntryDN();
            case MODIFY_DN: return ((ModifyDNOperation) operation).getEntryDN();
            case COMPARE: return ((CompareOperation) operation).getEntryDN();
            case DELETE: return ((DeleteOperation) operation).getEntryDN();
            case SEARCH: return ((SearchOperation) operation).getBaseDN();
            default: return null;
        }
    }

    @Override
    public void openBackend() throws ConfigException, InitializationException {
        logger.info(LocalizableMessage.raw("openBackend remote backend %s", cfg.getBackendId()));
        for (DN dn : cfg.getBaseDN())
        {
            try
            {
                serverContext.getBackendConfigManager().registerBaseDN(dn, this, false);
            }
            catch (Exception e)
            {
                throw new InitializationException(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(dn, e), e);
            }
        }
        cfg.addProxyChangeListener(this);
        if (serverContext.getCronExecutorService() != null) {
            userPoolCleanup = serverContext.getCronExecutorService().scheduleAtFixedRate(() -> {
                synchronized (routingLock) {
                    if (staticRoute != null) {
                        staticRoute.userPools.values().forEach(UserPool::expireIdle);
                    }
                    for (DynamicRoute route : dynamicRoutes.values()) {
                        route.userPools.values().forEach(UserPool::expireIdle);
                    }
                }
            }, 1, 1, TimeUnit.MINUTES);
        }
        synchronized (routingLock) {
            listening = true;
            if (discovery != null) {
                discovery.registerChangeListener(this);
            }
        }
    }

    @Override
    public void finalizeBackend() {
        logger.info(LocalizableMessage.raw("finalizeBackend remote backend %s", cfg.getBackendId()));
        cfg.removeProxyChangeListener(this);
        if (userPoolCleanup != null) {
            userPoolCleanup.cancel(false);
            userPoolCleanup = null;
        }
        final Map<ClientConnection, Runnable> listenersToRemove = new IdentityHashMap<>();
        synchronized (routingLock) {
            listening = false;
            if (discovery != null) {
                discovery.deregisterChangeListener(this);
            }
            retireDynamicRoutes();
            retireRoute(staticRoute);
            staticRoute = null;
            userSessions.forEach((client, session) -> listenersToRemove.put(client, session.cleanup));
            userSessions.clear();
            bindFactory = null;
            factory = null;
        }
        listenersToRemove.forEach(ClientConnection::removeAuthenticationChangeListener);

        // Deregister our base DNs.
        for (DN dn : cfg.getBaseDN())
        {
            try
            {
                serverContext.getBackendConfigManager().deregisterBaseDN(dn);
            }
            catch (Exception e)
            {
                logger.traceException(e);
            }
        }
    }

    @Override
    public Set<DN> getBaseDNs() {
        return cfg.getBaseDN();
    }

    @Override
    public boolean isDefaultRoute() {
        return false;
    }

    @Override
    public Set<String> getSupportedControls() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedFeatures() {
        return Collections.emptySet();
    }

    @Override
    public boolean isPrivateBackend() {
        return false;
    }

    private List<org.forgerock.opendj.ldap.controls.Control> convertRequestControls(Operation operation, ProxyRewriter mapping)
            throws DirectoryException {
        final List<org.forgerock.opendj.ldap.controls.Control> controls = new ArrayList<>();
        for (org.opends.server.types.Control control : operation.getRequestControls()) {
            // Never let a client override the identity used by this proxy's service account.
            if (OID_PROXIED_AUTH_V1.equals(control.getOID()) || OID_PROXIED_AUTH_V2.equals(control.getOID())) {
                throw new DirectoryException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                        LocalizableMessage.raw("Client-supplied proxy authorization controls are not supported"));
            }
            try {
                final ByteStringBuilder encoded = new ByteStringBuilder();
                control.write(ASN1.getWriter(encoded));
                final ASN1Reader reader = ASN1.getReader(encoded.toByteString().toByteArray());
                reader.readStartSequence();
                reader.readOctetString(); // OID: use the value from the original control below.
                if (reader.hasNextElement() && reader.peekType() == ASN1.UNIVERSAL_BOOLEAN_TYPE) {
                    reader.readBoolean();
                }
                final ByteString value = reader.hasNextElement() ? reader.readOctetString() : null;
                reader.readEndSequence();
                final org.forgerock.opendj.ldap.controls.Control rewritten = mapping.requestControl(
                        GenericControl.newControl(control.getOID(), control.isCritical(), value));
                if (rewritten != null) {
                    controls.add(rewritten);
                }
            } catch (IOException e) {
                throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                        LocalizableMessage.raw("Could not encode an LDAP request control"), e);
            }
        }
        return controls;
    }

    private void addRequestControls(Request request, List<org.forgerock.opendj.ldap.controls.Control> controls) {
        for (org.forgerock.opendj.ldap.controls.Control control : controls) {
            request.addControl(control);
        }
    }

    private List<org.opends.server.types.Control> convertResponseControls(
            List<org.forgerock.opendj.ldap.controls.Control> controls, ProxyRewriter mapping) {
        final List<org.opends.server.types.Control> converted = new ArrayList<>(controls.size());
        for (org.forgerock.opendj.ldap.controls.Control control : controls) {
            final org.forgerock.opendj.ldap.controls.Control rewritten = mapping.responseControl(control);
            if (rewritten != null) {
                converted.add(new LDAPControl(rewritten.getOID(), rewritten.isCritical(),
                        rewritten.hasValue() ? rewritten.getValue() : null));
            }
        }
        return converted;
    }

    private void addResponseControls(Operation operation, Result result, ProxyRewriter mapping) {
        if (result.getMatchedDN() != null && !result.getMatchedDN().isEmpty()) {
            try {
                operation.setMatchedDN(mapping.dn(DN.valueOf(result.getMatchedDN()), false));
            } catch (IllegalArgumentException e) {
                throw new ProxyRewriter.RewriteException(ResultCode.PROTOCOL_ERROR,
                        "Remote server returned an invalid matched DN");
            }
        }
        if (operation.getOperationType() != OperationType.BIND
                && result.getDiagnosticMessage() != null && !result.getDiagnosticMessage().isEmpty()) {
            operation.appendErrorMessage(LocalizableMessage.raw(result.getDiagnosticMessage()));
        }
        if (result.getReferralURIs() != null && !result.getReferralURIs().isEmpty()) {
            operation.setReferralURLs(new ArrayList<>(result.getReferralURIs()));
        }
        for (org.opends.server.types.Control control : convertResponseControls(result.getControls(), mapping)) {
            operation.addResponseControl(control);
        }
    }

    private void addProxiedAuthControl(Operation operation, final Request request, ProxyRewriter mapping) {
        final DN authorizationDN = operation.getClientConnection().getAuthenticationInfo().getAuthorizationDN();
        final String authorizationID = authorizationDN == null || authorizationDN.isRootDN()
                ? "dn:"
                : "dn:" + mapping.dn(authorizationDN, true);
        request.addControl(ProxiedAuthV2RequestControl.newControl(authorizationID));
    }
    @Override
    public void execute(Operation operation) throws CanceledOperationException {
        if (operation.getOperationType() == OperationType.BIND) {
            if (!operation.isInternalOperation()) {
                operation.getClientConnection().setAuthenticationInfo(AuthenticationInfo.anonymous());
            }
            final BindOperation bind = (BindOperation) operation;
            if (bind.getAuthenticationType() == AuthenticationType.SIMPLE && bind.getBindDN() != null
                    && !bind.getBindDN().isRootDN() && bind.getSimplePassword().isEmpty()) {
                bind.setResultCode(ResultCode.INVALID_CREDENTIALS);
                return;
            }
        }
        final ProxyRewriter mapping;
        final ConnectionFactory operationFactory;
        final ConnectionFactory operationBindFactory;
        final DynamicRoute route;
        synchronized (routingLock) {
            mapping = rewriter;
            if (discovery instanceof ReplicationServiceDiscoveryMechanism) {
                final Partition partition = discovery.getPartition(mapping.dn(targetDN(operation), true));
                if (partition == null || partition.getPrimaryServers().isEmpty()
                        && partition.getSecondaryServers().isEmpty()) {
                    operation.setResponseData(new DirectoryException(ResultCode.UNAVAILABLE,
                            LocalizableMessage.raw("No proxy backend servers are available for the target DN")));
                    return;
                }
                route = dynamicRoutes.computeIfAbsent(partition,
                        key -> new DynamicRoute(key, discovery));
                route.users++;
                operationFactory = route.authenticated;
                operationBindFactory = route.bind;
            } else {
                route = staticRoute;
                if (route != null) {
                    route.users++;
                }
                operationFactory = factory;
                operationBindFactory = bindFactory;
            }
        }
        try {
        final UserSession existingSession;
        synchronized (routingLock) {
            existingSession = userSessions.get(operation.getClientConnection());
        }
        final boolean directUserMode = route != null && ((existingSession != null && existingSession.route == route)
                || usesUserConnections(route));
        final UserSession userSession;
        if (directUserMode && operation.getOperationType() != OperationType.BIND
                && operation.getClientConnection().getAuthenticationInfo().getAuthorizationDN() != null) {
            userSession = existingSession;
            if (userSession == null || userSession.route != route) {
                operation.setResponseData(new DirectoryException(ResultCode.UNAVAILABLE,
                        LocalizableMessage.raw("The client must bind again to access this proxy route")));
                return;
            }
        } else {
            userSession = null;
        }
        final ConnectionFactory requestFactory = directUserMode ? operationBindFactory : operationFactory;
        if (operation.getOperationType() == OperationType.BIND ? operationBindFactory == null : requestFactory == null) {
            operation.setResponseData(new DirectoryException(ResultCode.UNAVAILABLE,
                    LocalizableMessage.raw("No proxy backend servers are available for the target DN")));
            return;
        }
        final List<org.forgerock.opendj.ldap.controls.Control> controls;
        try {
            controls = convertRequestControls(operation, mapping);
        } catch (DirectoryException e) {
            operation.setResponseData(e);
            return;
        } catch (ProxyRewriter.RewriteException e) {
            operation.setResponseData(new DirectoryException(e.resultCode, LocalizableMessage.raw(e.getMessage())));
            return;
        }
        try {
        switch (operation.getOperationType()) {
            case BIND: {
                final BindOperation request = (BindOperation) operation;
                if (request.getAuthenticationType() != AuthenticationType.SIMPLE) {
                    // TODO: SASL authentication not implemented.
                    request.setResponseData(new DirectoryException(ResultCode.PROTOCOL_ERROR, LocalizableMessage.raw("non-SIMPLE authentication not supported: " + request.getAuthenticationType())));
                } else {
                    Result result;
                    if (directUserMode && !request.getBindDN().isRootDN()) {
                        result = bindUser(route, request, mapping, controls);
                    } else {
                        final byte[] password = request.getSimplePassword().toByteArray();
                        try {
                            final SimpleBindRequest bindRequest = newSimpleBindRequest(
                                    mapping.dn(request.getBindDN(), true).toString(), password);
                            addRequestControls(bindRequest, controls);
                            try (Connection connection = operationBindFactory.getConnection()) {
                                result = connection.bind(bindRequest);
                            } catch (LdapException e) {
                                result = e.getResult();
                            }
                        } finally {
                            Arrays.fill(password, (byte) 0);
                        }
                    }
                    request.setResultCode(result.getResultCode());
                    addResponseControls(operation, result, mapping);
                    request.setAuthFailureReason(LocalizableMessage.raw(result.getDiagnosticMessage()));
                    if (!directUserMode && !operation.isInternalOperation()
                            && result.getResultCode().equals(ResultCode.SUCCESS) && !request.getBindDN().isRootDN()) {
                        operation.getClientConnection().setAuthenticationInfo(AuthenticationInfo.internal(new Entry(request.getBindDN(),null,null,null)));
                    }
                }
                break;
            }
            case ADD: {
                final AddOperation request = (AddOperation) operation;
                final AddRequest originalAddRequest=Requests.newAddRequest(
                        new Entry(
                                request.getEntryDN(),
                                request.getObjectClasses(),
                                request.getUserAttributes(),
                                request.getOperationalAttributes()
                        ).toLDIFString().split(System.lineSeparator())
                );
                final AddRequest addRequest = Requests.newAddRequest(mapping.entry(originalAddRequest, true));
                addRequestControls(addRequest, controls);
                if (!directUserMode) addProxiedAuthControl(operation,addRequest,mapping);
                Result result;
                try (Connection connection = userSession == null ? requestFactory.getConnection() : userSession.pool.borrow()) {
                    result = connection.add(addRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result, mapping);
                break;
            }
            case MODIFY: {
                final ModifyOperation request = (ModifyOperation) operation;
                ModifyRequest modifyRequest=Requests.newModifyRequest(mapping.dn(request.getEntryDN(), true));
                addRequestControls(modifyRequest, controls);
                if (!directUserMode) addProxiedAuthControl(operation,modifyRequest,mapping);
                for(Modification  modify: request.getModifications()){
                    final String attributeName = modify.getAttribute().getAttributeDescription().toString();
                    modifyRequest=modifyRequest.addModification(
                            modify.getModificationType(),
                            mapping.attribute(attributeName, true),
                            StreamSupport.stream(modify.getAttribute().spliterator(),false)
                                    .map(value -> mapping.value(attributeName, value, true)).toArray(Object[]::new)
                    );
                }
                Result result;
                try (Connection connection = userSession == null ? requestFactory.getConnection() : userSession.pool.borrow()) {
                    result = connection.modify(modifyRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result, mapping);
                break;
            }
            case MODIFY_DN:{
                final ModifyDNOperation request = (ModifyDNOperation) operation;
                final ModifyDNRequest modifyDNRequest=Requests.newModifyDNRequest(
                                mapping.dn(request.getEntryDN(), true), mapping.rdn(request.getNewRDN(), true))
                        .setNewSuperior(mapping.dn(request.getNewSuperior(), true))
                        .setDeleteOldRDN(request.deleteOldRDN());
                addRequestControls(modifyDNRequest, controls);
                if (!directUserMode) addProxiedAuthControl(operation,modifyDNRequest,mapping);
                Result result;
                try (Connection connection = userSession == null ? requestFactory.getConnection() : userSession.pool.borrow()) {
                    result = connection.modifyDN(modifyDNRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result, mapping);
                break;
            }
            case COMPARE:{
                final CompareOperation request = (CompareOperation) operation;
                final String attributeName = request.getAttributeDescription().toString();
                final CompareRequest compareRequest=Requests.newCompareRequest(mapping.dn(request.getEntryDN(), true),
                        AttributeDescription.valueOf(mapping.attribute(attributeName, true)),
                        mapping.value(attributeName, request.getAssertionValue(), true));
                addRequestControls(compareRequest, controls);
                if (!directUserMode) addProxiedAuthControl(operation,compareRequest,mapping);
                Result result;
                try (Connection connection = userSession == null ? requestFactory.getConnection() : userSession.pool.borrow()) {
                    result = connection.compare(compareRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result, mapping);
                break;
            }
            case DELETE:{
                final DeleteOperation request = (DeleteOperation) operation;
                final DeleteRequest deleteRequest=Requests.newDeleteRequest(mapping.dn(request.getEntryDN(), true));
                addRequestControls(deleteRequest, controls);
                if (!directUserMode) addProxiedAuthControl(operation,deleteRequest,mapping);
                Result result;
                try (Connection connection = userSession == null ? requestFactory.getConnection() : userSession.pool.borrow()) {
                    result = connection.delete(deleteRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result, mapping);
                break;
            }
            case EXTENDED:{
                final ExtendedOperation request = (ExtendedOperation) operation;
                final ExtendedRequest extendedRequest=Requests.newGenericExtendedRequest(request.getRequestOID(),request.getRequestValue());
                addRequestControls(extendedRequest, controls);
                if (!directUserMode) addProxiedAuthControl(operation,extendedRequest,mapping);
                ExtendedResult result;
                try (Connection connection = userSession == null ? requestFactory.getConnection() : userSession.pool.borrow()) {
                    result = connection.extendedRequest(extendedRequest);
                    request.setResultCode(result.getResultCode());
                    request.setResponseValue(result.getValue());
                    addResponseControls(operation, result, mapping);
                } catch (LdapException e) {
                    request.setResultCode(e.getResult().getResultCode());
                    addResponseControls(operation, e.getResult(), mapping);
                }
                break;
            }
            case SEARCH:{
                final SearchOperation request = (SearchOperation) operation;
                final SearchRequest searchRequest=Requests.newSearchRequest(
                        mapping.dn(request.getBaseDN(), true),
                        request.getScope(),
                        mapping.filter(Filter.valueOf(request.getFilter().toString())),
                        request.getAttributes().stream().map(name -> mapping.attribute(name, true)).toArray(String[]::new)
                ).setDereferenceAliasesPolicy(request.getDerefPolicy())
                 .setSizeLimit(request.getSizeLimit())
                 .setTimeLimit(request.getTimeLimit())
                 .setTypesOnly(request.getTypesOnly());
                addRequestControls(searchRequest, controls);
                if (!directUserMode) addProxiedAuthControl(operation,searchRequest,mapping);
                try (Connection connection = userSession == null ? requestFactory.getConnection() : userSession.pool.borrow()) {
                    try (ConnectionEntryReader entryReader = connection.search(searchRequest)){
                        while (entryReader.hasNext())
                        {
                            if (entryReader.isEntry()) {
                                final SearchResultEntry sr = entryReader.readEntry();
                                final org.forgerock.opendj.ldap.Entry rewritten = mapping.entry(sr, false);
                                if (serverContext != null
                                        && serverContext.getBackendConfigManager().findBackendForEntry(request.getBaseDN()) == this
                                        && serverContext.getBackendConfigManager().findBackendForEntry(rewritten.getName()) != this) {
                                    continue;
                                }
                                final Map<AttributeType,List<Attribute>> userAttributes=new HashMap<>();
                                final Map<AttributeType,List<Attribute>> operationalAttributes=new HashMap<>();
                                for (org.forgerock.opendj.ldap.Attribute attr : rewritten.getAllAttributes()){
                                    final AttributeBuilder attrBuilder=new AttributeBuilder(attr.getAttributeDescription());
                                    for (final ByteString value: attr) {
                                        attrBuilder.add(value);
                                    }
                                    final AttributeType type = attr.getAttributeDescription().getAttributeType();
                                    final Map<AttributeType,List<Attribute>> attributes =
                                            type.isOperational() ? operationalAttributes : userAttributes;
                                    attributes.computeIfAbsent(type, ignored -> new ArrayList<>()).add(attrBuilder.toAttribute());
                                }
                                request.sendSearchEntry(new org.opends.server.types.SearchResultEntry(
                                        new Entry(rewritten.getName(),null,userAttributes,operationalAttributes),
                                        convertResponseControls(sr.getControls(), mapping)));
                            }else{
                                final SearchResultReference sr=entryReader.readReference();
                                request.sendSearchReference(new org.opends.server.types.SearchResultReference(
                                        sr.getURIs(), convertResponseControls(sr.getControls(), mapping)));
                            }
                        }
                        final Result result = entryReader.readResult();
                        request.setResultCode(result.getResultCode());
                        addResponseControls(operation, result, mapping);
                    }
                } catch (LdapException e) {
                   request.setResultCode(e.getResult().getResultCode());
                   addResponseControls(operation, e.getResult(), mapping);
                } catch (SearchResultReferenceIOException | DirectoryException e) {
                    throw new RuntimeException(e);
                }
                if (serverContext != null
                        && serverContext.getBackendConfigManager().findBackendForEntry(request.getBaseDN()) == this) {
                    LocalBackendWorkflowElement.executeSearchOnSubordinates(request);
                }
                break;
            }
            case ABANDON:
            case UNBIND:
            default:
                throw new IllegalStateException("Proxy backend should not receive " + operation.getOperationType() + " requests.");
        }
        } catch (ProxyRewriter.RewriteException e) {
            operation.setResponseData(new DirectoryException(e.resultCode, LocalizableMessage.raw(e.getMessage())));
        }
        } finally {
            if (route != null) {
                synchronized (routingLock) {
                    route.users--;
                    if (route.retired && route.users == 0) {
                        route.close();
                    }
                }
            }
        }
    }

    @Override
    public boolean isConfigurationChangeAcceptable(ProxyBackendCfg configuration, List<LocalizableMessage> unacceptableReasons) {
        return isConfigurationAcceptable(configuration, unacceptableReasons, serverContext);
    }
}
