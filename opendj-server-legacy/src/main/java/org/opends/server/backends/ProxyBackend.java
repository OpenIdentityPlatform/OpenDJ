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
import org.forgerock.opendj.server.config.meta.ServiceDiscoveryMechanismCfgDefn.OperationIdentityMode;
import org.forgerock.opendj.server.config.meta.ServiceDiscoveryMechanismCfgDefn.PrimaryLoadBalancingAlgorithm;
import org.forgerock.opendj.server.config.meta.ServiceDiscoveryMechanismCfgDefn.SecondaryLoadBalancingAlgorithm;
import org.forgerock.opendj.server.config.server.ProxyBackendCfg;
import org.forgerock.util.Options;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.*;
import org.opends.server.discovery.Partition;
import org.opends.server.discovery.ReplicationServiceDiscoveryMechanism;
import org.opends.server.discovery.ServerPriorities;
import org.opends.server.discovery.ServiceDiscoveryChangeListener;
import org.opends.server.discovery.ServiceDiscoveryMechanism;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.loggers.AccessLogger;
import org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;
import org.opends.server.types.*;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.StreamSupport;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.AUTHN_BIND_REQUEST;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.CONNECT_TIMEOUT;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.HEARTBEAT_ENABLED;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.HEARTBEAT_INTERVAL;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.HEARTBEAT_TIMEOUT;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.HEARTBEAT_SEARCH_REQUEST;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.REQUEST_TIMEOUT;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.forgerock.util.time.Duration.duration;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.ServerConstants.OID_PROXIED_AUTH_V1;
import static org.opends.server.util.ServerConstants.OID_PROXIED_AUTH_V2;

public class ProxyBackend extends Backend<ProxyBackendCfg>
        implements ConfigurationChangeListener<ProxyBackendCfg>, ServiceDiscoveryChangeListener {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    private static final java.util.concurrent.atomic.AtomicLong PROXY_IDS = new java.util.concurrent.atomic.AtomicLong();

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
            Collections.emptyList(), Schema.getDefaultSchema());

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
            validateConnectionSettings(configuration);
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
        final ProxyLoadBalancingAlgorithm primaryAlgorithm = primaryLoadBalancingAlgorithm(discoveryMechanism);
        final ProxyLoadBalancingAlgorithm secondaryAlgorithm = secondaryLoadBalancingAlgorithm(discoveryMechanism);

        final List<ConnectionFactory> primaryBind = new LinkedList<>();
        final List<ConnectionFactory> secondaryBind = new LinkedList<>();
        final List<ConnectionFactory> primary = new LinkedList<>();
        final List<ConnectionFactory> secondary = new LinkedList<>();
        if (partition!=null) {
            for (Partition.Server server : orderedTier(partition.getPrimaryServers(), discoveryMechanism, primaryAlgorithm)) {
                primaryBind.add(routedFactory(server, new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        connectionOptions(server, configuration)
                )));
                if (hasServiceCredentials(discoveryMechanism)) primary.add(routedFactory(server, newServicePool(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        connectionOptions(server, configuration)
                                .set(AUTHN_BIND_REQUEST, newSimpleBindRequest(discoveryMechanism.cfg.getBindDN().toString(), discoveryMechanism.cfg.getBindPassword().toCharArray()))
                ), configuration)));
            }
            for (Partition.Server server : orderedTier(partition.getSecondaryServers(), discoveryMechanism, secondaryAlgorithm)) {
                secondaryBind.add(routedFactory(server, new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        connectionOptions(server, configuration)
                )));
                if (hasServiceCredentials(discoveryMechanism)) secondary.add(routedFactory(server, newServicePool(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        connectionOptions(server, configuration)
                                .set(AUTHN_BIND_REQUEST, newSimpleBindRequest(discoveryMechanism.cfg.getBindDN().toString(), discoveryMechanism.cfg.getBindPassword().toCharArray()))
                ), configuration)));
            }
        }

        final ConnectionFactory newBindFactory = prioritizedLoadBalancer(primaryBind, secondaryBind,
                primaryAlgorithm, secondaryAlgorithm, configuration);
        final ConnectionFactory newFactory = prioritizedLoadBalancer(primary, secondary,
                primaryAlgorithm, secondaryAlgorithm, configuration);
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
                staticRoute = new DynamicRoute(newBindFactory, newFactory, partition, discoveryMechanism, configuration);
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
        return new ProxyRewriter(clientBase, remoteBase, configuration.getAttributeMapping(),
                configuration.getAttributeAlias(), context.getSchema());
    }

    @Override
    public boolean isConfigurationAcceptable(ProxyBackendCfg configuration, List<LocalizableMessage> reasons,
                                             ServerContext context) {
        try {
            validateConnectionSettings(configuration);
            createRewriter(configuration, context);
            return true;
        } catch (IllegalArgumentException e) {
            reasons.add(LocalizableMessage.raw(e.getMessage()));
            return false;
        }
    }

    /**
     * Builds a strict primary-before-secondary route. The primary policy only chooses among the
     * primary candidate routes; each candidate route attempts every primary before accepting the
     * secondary request-level balancer as its final candidate.
     */
    private static ConnectionFactory prioritizedLoadBalancer(List<ConnectionFactory> primary,
                                                             List<ConnectionFactory> secondary,
                                                             ProxyLoadBalancingAlgorithm primaryAlgorithm,
                                                             ProxyLoadBalancingAlgorithm secondaryAlgorithm,
                                                             ProxyBackendCfg configuration) {
        if (primary.isEmpty() && secondary.isEmpty()) {
            return null;
        }
        if (primary.isEmpty() || secondary.isEmpty()) {
            return newLoadBalancer(primary.isEmpty() ? secondary : primary,
                    primary.isEmpty() ? secondaryAlgorithm : primaryAlgorithm, configuration);
        }

        final ConnectionFactory secondaryLoadBalancer = newLoadBalancer(secondary, secondaryAlgorithm, configuration);
        final List<ConnectionFactory> routes = new ArrayList<>(primary.size());
        for (int start = 0; start < primary.size(); start++) {
            final List<ConnectionFactory> candidates = new ArrayList<>(primary.size() + 1);
            for (int offset = 0; offset < primary.size(); offset++) {
                candidates.add(primary.get((start + offset) % primary.size()));
            }
            // The request-level secondary balancer must be last: it returns a
            // virtual connection before checking whether a server is reachable.
            candidates.add(secondaryLoadBalancer);
            routes.add(Connections.newFailoverLoadBalancer(candidates, loadBalancerOptions(configuration)));
        }
        return newLoadBalancer(routes, primaryAlgorithm, configuration);
    }

    private static ConnectionFactory newLoadBalancer(Collection<ConnectionFactory> factories,
                                                     ProxyLoadBalancingAlgorithm algorithm,
                                                     ProxyBackendCfg configuration) {
        final Options options = loadBalancerOptions(configuration);
        switch (algorithm) {
            case AFFINITY:
                return Connections.newAffinityRequestLoadBalancer(factories, options);
            case ROUND_ROBIN:
                return Connections.newRoundRobinLoadBalancer(factories, options);
            case LEAST_REQUESTS:
                return Connections.newLeastRequestsLoadBalancer(factories, options);
            case FAILOVER:
                return Connections.newFailoverLoadBalancer(factories, options);
            case DISTRIBUTION:
                return Connections.newConsistentHashRequestLoadBalancer(factories, options);
            default:
                throw new AssertionError("Unsupported load-balancing algorithm: " + algorithm);
        }
    }

    private enum ProxyLoadBalancingAlgorithm {
        AFFINITY,
        ROUND_ROBIN,
        LEAST_REQUESTS,
        FAILOVER,
        DISTRIBUTION
    }

    private static Collection<Partition.Server> orderedTier(Collection<Partition.Server> servers,
            ServiceDiscoveryMechanism<?> mechanism, ProxyLoadBalancingAlgorithm algorithm) {
        if (algorithm == ProxyLoadBalancingAlgorithm.FAILOVER || algorithm == ProxyLoadBalancingAlgorithm.LEAST_REQUESTS) {
            return ServerPriorities.parse(mechanism.cfg.getServerPriority()).order(servers);
        }
        return servers;
    }

    private static ProxyLoadBalancingAlgorithm primaryLoadBalancingAlgorithm(
            ServiceDiscoveryMechanism<?> mechanism) {
        final PrimaryLoadBalancingAlgorithm configured = mechanism.cfg.getPrimaryLoadBalancingAlgorithm();
        return configured == null ? ProxyLoadBalancingAlgorithm.AFFINITY
                : ProxyLoadBalancingAlgorithm.valueOf(configured.name());
    }

    private static ProxyLoadBalancingAlgorithm secondaryLoadBalancingAlgorithm(
            ServiceDiscoveryMechanism<?> mechanism) {
        final SecondaryLoadBalancingAlgorithm configured = mechanism.cfg.getSecondaryLoadBalancingAlgorithm();
        return configured == null ? ProxyLoadBalancingAlgorithm.AFFINITY
                : ProxyLoadBalancingAlgorithm.valueOf(configured.name());
    }

    private static final class DynamicRoute {
        final ConnectionFactory bind;
        final ConnectionFactory authenticated;
        final ProxyBackendCfg configuration;
        final List<Partition.Server> servers;
        final UserBindTier primaryUserBindTier;
        final UserBindTier secondaryUserBindTier;
        final Map<Partition.Server, UserBindTier> userBindTiers;
        final Map<Partition.Server, LDAPConnectionFactory> physicalFactories = new LinkedHashMap<>();
        final Map<List<Object>, UserPool> userPools = new HashMap<>();
        final boolean hasServiceCredentials;
        final String serviceIdentity;
        final OperationIdentityMode configuredIdentityMode;
        OperationIdentity operationIdentity;
        long nextCapabilityProbe;
        int users;
        boolean retired;

        DynamicRoute(ConnectionFactory bind, ConnectionFactory authenticated) {
            this.configuration = null;
            this.bind = bind;
            this.authenticated = authenticated;
            this.servers = Collections.emptyList();
            this.primaryUserBindTier = new UserBindTier(Collections.emptyList(), ProxyLoadBalancingAlgorithm.AFFINITY);
            this.secondaryUserBindTier = new UserBindTier(Collections.emptyList(), ProxyLoadBalancingAlgorithm.AFFINITY);
            this.userBindTiers = Collections.emptyMap();
            this.hasServiceCredentials = authenticated != null;
            this.serviceIdentity = null;
            this.configuredIdentityMode = OperationIdentityMode.PROXY_AUTH;
            this.operationIdentity = OperationIdentity.PROXY_AUTH;
            this.nextCapabilityProbe = Long.MAX_VALUE;
        }

        DynamicRoute(ConnectionFactory bind, ConnectionFactory authenticated, Partition partition,
                     ServiceDiscoveryMechanism<?> mechanism, ProxyBackendCfg configuration) {
            this.configuration = configuration;
            this.bind = bind;
            this.authenticated = authenticated;
            this.servers = orderedServers(partition);
            this.primaryUserBindTier = new UserBindTier(orderedTier(primaryServers(partition), mechanism,
                    primaryLoadBalancingAlgorithm(mechanism)),
                    primaryLoadBalancingAlgorithm(mechanism));
            this.secondaryUserBindTier = new UserBindTier(orderedTier(secondaryServers(partition), mechanism,
                    secondaryLoadBalancingAlgorithm(mechanism)),
                    secondaryLoadBalancingAlgorithm(mechanism));
            this.userBindTiers = userBindTiers(primaryUserBindTier, secondaryUserBindTier);
            this.hasServiceCredentials = hasServiceCredentials(mechanism);
            this.serviceIdentity = this.hasServiceCredentials ? mechanism.cfg.getBindDN().toString() : null;
            this.configuredIdentityMode = configuredIdentityMode(mechanism);
        }

        DynamicRoute(Partition partition, ServiceDiscoveryMechanism<?> mechanism, ProxyBackendCfg configuration) {
            this.configuration = configuration;
            final List<ConnectionFactory> primaryBind = new ArrayList<>();
            final List<ConnectionFactory> secondaryBind = new ArrayList<>();
            final List<ConnectionFactory> primary = new ArrayList<>();
            final List<ConnectionFactory> secondary = new ArrayList<>();
            final ProxyLoadBalancingAlgorithm primaryAlgorithm = primaryLoadBalancingAlgorithm(mechanism);
            final ProxyLoadBalancingAlgorithm secondaryAlgorithm = secondaryLoadBalancingAlgorithm(mechanism);
            final Collection<Partition.Server> orderedPrimary = orderedTier(partition.getPrimaryServers(), mechanism, primaryAlgorithm);
            final Collection<Partition.Server> orderedSecondary = orderedTier(partition.getSecondaryServers(), mechanism, secondaryAlgorithm);
            addServers(orderedPrimary, mechanism, configuration, primaryBind, primary);
            addServers(orderedSecondary, mechanism, configuration, secondaryBind, secondary);
            bind = prioritizedLoadBalancer(primaryBind, secondaryBind, primaryAlgorithm, secondaryAlgorithm, configuration);
            authenticated = prioritizedLoadBalancer(primary, secondary, primaryAlgorithm, secondaryAlgorithm, configuration);
            servers = orderedServers(partition);
            primaryUserBindTier = new UserBindTier(orderedPrimary, primaryAlgorithm);
            secondaryUserBindTier = new UserBindTier(orderedSecondary, secondaryAlgorithm);
            userBindTiers = userBindTiers(primaryUserBindTier, secondaryUserBindTier);
            hasServiceCredentials = hasServiceCredentials(mechanism);
            serviceIdentity = hasServiceCredentials ? mechanism.cfg.getBindDN().toString() : null;
            configuredIdentityMode = configuredIdentityMode(mechanism);
        }

        private static Collection<Partition.Server> primaryServers(Partition partition) {
            return partition == null ? Collections.emptyList() : partition.getPrimaryServers();
        }

        private static Collection<Partition.Server> secondaryServers(Partition partition) {
            return partition == null ? Collections.emptyList() : partition.getSecondaryServers();
        }

        private static Map<Partition.Server, UserBindTier> userBindTiers(UserBindTier primary,
                                                                           UserBindTier secondary) {
            final Map<Partition.Server, UserBindTier> result = new IdentityHashMap<>();
            primary.addTo(result);
            secondary.addTo(result);
            return result;
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
                                       ProxyBackendCfg configuration, List<ConnectionFactory> bind,
                                       List<ConnectionFactory> authenticated) {
            for (Partition.Server server : servers) {
                final Options options = connectionOptions(server, configuration);
                // A successful client bind changes the physical connection's identity. Never
                // reuse it for anonymous requests after a capability-driven mode change.
                bind.add(routedFactory(server, new LDAPConnectionFactory(
                        server.getHostPort().getHost(), server.getHostPort().getPort(), options)));
                if (!hasServiceCredentials(mechanism)) {
                    continue;
                }
                final Options authOptions = Options.copyOf(options).set(AUTHN_BIND_REQUEST,
                        newSimpleBindRequest(mechanism.cfg.getBindDN().toString(),
                                mechanism.cfg.getBindPassword().toCharArray()));
                authenticated.add(routedFactory(server, newServicePool(new LDAPConnectionFactory(
                        server.getHostPort().getHost(), server.getHostPort().getPort(), authOptions), configuration)));
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

    /** Selects physical servers for client binds while retaining successful-session pinning. */
    private static final class UserBindTier {
        private final List<Partition.Server> servers;
        private final ProxyLoadBalancingAlgorithm algorithm;
        private final AtomicInteger nextServer = new AtomicInteger();
        private final AtomicLongArray activeBinds;
        private final Map<Partition.Server, Integer> indexes = new IdentityHashMap<>();
        private final ConsistentHashMap<Partition.Server> distribution = new ConsistentHashMap<>();

        UserBindTier(Collection<Partition.Server> servers, ProxyLoadBalancingAlgorithm algorithm) {
            this.servers = new ArrayList<>(servers);
            this.algorithm = algorithm;
            this.activeBinds = new AtomicLongArray(this.servers.size());
            for (int i = 0; i < this.servers.size(); i++) {
                final Partition.Server server = this.servers.get(i);
                indexes.put(server, i);
                distribution.put("server-" + i, server);
            }
        }

        void addTo(Map<Partition.Server, UserBindTier> tiers) {
            for (Partition.Server server : servers) {
                tiers.put(server, this);
            }
        }

        void addServers(List<Partition.Server> result, DN dn) {
            if (servers.isEmpty()) {
                return;
            }
            switch (algorithm) {
                case AFFINITY:
                    addFrom(result, affinityIndex(dn), servers.size());
                    return;
                case ROUND_ROBIN:
                    addFrom(result, Math.floorMod(nextServer.getAndIncrement(), servers.size()), servers.size());
                    return;
                case LEAST_REQUESTS:
                    addFrom(result, leastRequestsIndex(), servers.size());
                    return;
                case FAILOVER:
                    result.addAll(servers);
                    return;
                case DISTRIBUTION:
                    final Partition.Server selected = distribution.get(dn.toNormalizedUrlSafeString());
                    addFrom(result, indexes.get(selected), servers.size());
                    return;
                default:
                    throw new AssertionError("Unsupported load-balancing algorithm: " + algorithm);
            }
        }

        void requestStarted(Partition.Server server) {
            if (algorithm == ProxyLoadBalancingAlgorithm.LEAST_REQUESTS) {
                activeBinds.incrementAndGet(indexes.get(server));
            }
        }

        void requestCompleted(Partition.Server server) {
            if (algorithm == ProxyLoadBalancingAlgorithm.LEAST_REQUESTS) {
                activeBinds.decrementAndGet(indexes.get(server));
            }
        }

        private int affinityIndex(DN dn) {
            final int hash = dn.hashCode();
            return hash == Integer.MIN_VALUE ? 0 : Math.abs(hash) % servers.size();
        }

        private int leastRequestsIndex() {
            int selected = 0;
            long minimum = activeBinds.get(0);
            for (int i = 1; i < activeBinds.length(); i++) {
                final long value = activeBinds.get(i);
                if (value < minimum) {
                    minimum = value;
                    selected = i;
                }
            }
            return selected;
        }

        private void addFrom(List<Partition.Server> result, int start, int count) {
            for (int offset = 0; offset < count; offset++) {
                result.add(servers.get((start + offset) % servers.size()));
            }
        }
    }

    private enum OperationIdentity {
        CLIENT,
        SPECIFIC,
        PROXY_AUTH,
        UNAVAILABLE
    }

    private static OperationIdentityMode configuredIdentityMode(ServiceDiscoveryMechanism<?> mechanism) {
        if (mechanism == null || mechanism.cfg.getOperationIdentityMode() == null) {
            return OperationIdentityMode.AUTO;
        }
        return mechanism.cfg.getOperationIdentityMode();
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

        synchronized ConnectionPoolStatistics getStatistics() {
            return new ConnectionPoolStatistics(idle.size() + borrowed, idle.size(), borrowed,
                    0, 0, MAX_CONNECTIONS, closed);
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

    private static void logProxyRequest(Operation operation, String identityMode, String shard,
                                        ConnectionRoutingContext context, long proxyId, String target, String filter, String scope) {
        context.onRequestIssued();
        AccessLogger.logProxyRequest(operation, proxyId, identityMode, shard, target, filter, scope);
    }

    private static Connection borrowUserConnection(UserSession session, ConnectionRoutingContext context)
            throws LdapException {
        context.onAcquisitionStarted();
        String server = ((Partition.Server) session.key.get(1)).getHostPort().toString();
        context.onPoolAttempt(server, session.pool::getStatistics);
        try {
            Connection connection = session.pool.borrow();
            context.onServerSelected(server);
            return new RoutedConnection(connection, context);
        } catch (LdapException | RuntimeException e) {
            context.onPoolAcquisitionFailure();
            throw e;
        }
    }

    private void removeUserSession(ClientConnection client, UserSession session) {
        synchronized (routingLock) {
            if (userSessions.remove(client, session) && session.pool.removeSession()) {
                session.route.userPools.remove(session.key, session.pool);
            }
        }
    }

    private static void validateConnectionSettings(ProxyBackendCfg configuration) {
        if (configuration.getConnectionPoolMaxSize() != -1
                && configuration.getConnectionPoolMinSize() > configuration.getConnectionPoolMaxSize()) {
            throw new IllegalArgumentException("connection-pool-min-size must not exceed connection-pool-max-size");
        }
        Filter.valueOf(configuration.getKeepAliveSearchRequestFilter());
    }

    private static Options loadBalancerOptions(ProxyBackendCfg configuration) {
        return Options.defaultOptions().set(Connections.LOAD_BALANCER_MONITORING_INTERVAL,
                duration(configuration.getReconnectInterval(), TimeUnit.MILLISECONDS));
    }

    private static final class RoutedConnection extends AbstractConnectionWrapper<Connection> {
        private final ConnectionRoutingContext context;

        RoutedConnection(Connection connection, ConnectionRoutingContext context) {
            super(connection);
            this.context = context;
        }

        private <T extends Result> LdapPromise<T> observe(LdapPromise<T> promise) {
            return org.forgerock.opendj.ldap.spi.LdapPromises.mapWithCancellation(promise, promise.getRequestID(),
                    result -> {
                        context.onComplete(result);
                        return result;
                    }, failure -> {
                        context.onComplete(failure.getResult());
                    });
        }

        private interface LdapCall<T extends Result> { T run() throws LdapException; }

        private <T extends Result> T observe(LdapCall<T> call) throws LdapException {
            try {
                T result = call.run();
                context.onComplete(result);
                return result;
            } catch (LdapException e) {
                context.onComplete(e.getResult());
                throw e;
            }
        }

        @Override
        public Result add(AddRequest request) throws LdapException {
            return observe(() -> connection.add(request));
        }

        @Override
        public LdapPromise<Result> addAsync(AddRequest request, IntermediateResponseHandler handler) {
            return observe(connection.addAsync(request, handler));
        }

        @Override
        public BindResult bind(BindRequest request) throws LdapException {
            return observe(() -> connection.bind(request));
        }

        @Override
        public LdapPromise<BindResult> bindAsync(BindRequest request, IntermediateResponseHandler handler) {
            return observe(connection.bindAsync(request, handler));
        }

        @Override
        public Result modify(ModifyRequest request) throws LdapException {
            return observe(() -> connection.modify(request));
        }

        @Override
        public LdapPromise<Result> modifyAsync(ModifyRequest request, IntermediateResponseHandler handler) {
            return observe(connection.modifyAsync(request, handler));
        }

        @Override
        public Result modifyDN(ModifyDNRequest request) throws LdapException {
            return observe(() -> connection.modifyDN(request));
        }

        @Override
        public LdapPromise<Result> modifyDNAsync(ModifyDNRequest request, IntermediateResponseHandler handler) {
            return observe(connection.modifyDNAsync(request, handler));
        }

        @Override
        public CompareResult compare(CompareRequest request) throws LdapException {
            return observe(() -> connection.compare(request));
        }

        @Override
        public LdapPromise<CompareResult> compareAsync(CompareRequest request, IntermediateResponseHandler handler) {
            return observe(connection.compareAsync(request, handler));
        }

        @Override
        public Result delete(DeleteRequest request) throws LdapException {
            return observe(() -> connection.delete(request));
        }

        @Override
        public LdapPromise<Result> deleteAsync(DeleteRequest request, IntermediateResponseHandler handler) {
            return observe(connection.deleteAsync(request, handler));
        }

        @Override
        public ConnectionEntryReader search(SearchRequest request) {
            return new ConnectionEntryReader(this, request);
        }

        @Override
        public LdapPromise<Result> searchAsync(SearchRequest request, SearchResultHandler handler) {
            return searchAsync(request, null, handler);
        }

        @Override
        public LdapPromise<Result> searchAsync(SearchRequest request, IntermediateResponseHandler intermediate,
                                               SearchResultHandler handler) {
            SearchResultHandler counted = new SearchResultHandler() {
                @Override
                public boolean handleEntry(SearchResultEntry entry) {
                    context.onSearchEntry();
                    return handler.handleEntry(entry);
                }

                @Override
                public boolean handleReference(SearchResultReference reference) {
                    return handler.handleReference(reference);
                }
            };
            return observe(connection.searchAsync(request, intermediate, counted));
        }

        @Override
        public <R extends ExtendedResult> R extendedRequest(ExtendedRequest<R> request) throws LdapException {
            return observe(() -> connection.extendedRequest(request));
        }

        @Override
        public <R extends ExtendedResult> R extendedRequest(ExtendedRequest<R> request,
                IntermediateResponseHandler handler) throws LdapException {
            return observe(() -> connection.extendedRequest(request, handler));
        }

        @Override
        public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(ExtendedRequest<R> request,
                IntermediateResponseHandler handler) {
            return observe(connection.extendedRequestAsync(request, handler));
        }
    }

    private static final class RoutedFactory implements ConnectionFactory {
        private final Partition.Server server;
        private final ConnectionFactory delegate;

        RoutedFactory(Partition.Server server, ConnectionFactory delegate) {
            this.server = server;
            this.delegate = delegate;
        }

        @Override
        public void close() { delegate.close(); }

        @Override
        public Connection getConnection() throws LdapException { return delegate.getConnection(); }

        @Override
        public org.forgerock.util.promise.Promise<Connection, LdapException> getConnectionAsync() {
            return delegate.getConnectionAsync();
        }

        @Override
        public Connection getConnectionWithContext(ConnectionRoutingContext context) throws LdapException {
            preparePool(context);
            try {
                Connection connection = delegate.getConnection();
                return route(connection, context);
            } catch (LdapException | RuntimeException e) {
                if (context != null) context.onPoolAcquisitionFailure();
                throw e;
            }
        }

        @Override
        public org.forgerock.util.promise.Promise<Connection, LdapException> getConnectionAsyncWithContext(
                ConnectionRoutingContext context) {
            preparePool(context);
            try {
                return org.forgerock.opendj.ldap.spi.LdapPromises.mapWithCancellation(delegate.getConnectionAsync(), -1,
                        connection -> route(connection, context), failure -> {
                            if (context != null) context.onPoolAcquisitionFailure();
                        });
            } catch (RuntimeException e) {
                if (context != null) context.onPoolAcquisitionFailure();
                throw e;
            }
        }

        private void preparePool(ConnectionRoutingContext context) {
            if (context == null) return;
            context.onAcquisitionStarted();
            if (delegate instanceof ConnectionPool) {
                context.onPoolAttempt(server.getHostPort().toString(), ((ConnectionPool) delegate)::getStatistics);
            }
        }

        @Override
        public String toString() {
            return "RoutedFactory(" + server.getHostPort() + ")";
        }

        private Connection route(Connection connection, ConnectionRoutingContext context) {
            if (context == null) return connection;
            context.onServerSelected(server.getHostPort().toString());
            return new RoutedConnection(connection, context);
        }
    }

    private static ConnectionFactory routedFactory(Partition.Server server, ConnectionFactory delegate) {
        return new RoutedFactory(server, delegate);
    }

    private static ConnectionFactory newServicePool(ConnectionFactory factory, ProxyBackendCfg configuration) {
        int max = configuration.getConnectionPoolMaxSize();
        if (max == -1) {
            max = Integer.MAX_VALUE;
        }
        return Connections.newCachedConnectionPool(factory, configuration.getConnectionPoolMinSize(), max,
                configuration.getConnectionPoolIdleTimeout(), TimeUnit.MILLISECONDS,
                configuration.getConnectionPoolWaitTimeout());
    }

    private static Options connectionOptions(Partition.Server server, ProxyBackendCfg configuration) {
        final Options options = Options.copyOf(server.getOptions()).set(HEARTBEAT_ENABLED, true);
        if (configuration != null) {
            if (configuration.getConnectionTimeout() != null) {
                options.set(CONNECT_TIMEOUT, duration(configuration.getConnectionTimeout(), TimeUnit.MILLISECONDS));
            }
            options.set(REQUEST_TIMEOUT, duration(configuration.getRequestTimeout(), TimeUnit.MILLISECONDS));
            options.set(HEARTBEAT_ENABLED, configuration.isKeepAliveEnabled());
            options.set(HEARTBEAT_INTERVAL, duration(configuration.getKeepAliveInterval(), TimeUnit.MILLISECONDS));
            options.set(HEARTBEAT_TIMEOUT, duration(configuration.getKeepAliveTimeout(), TimeUnit.MILLISECONDS));
            final DN base = configuration.getKeepAliveSearchRequestBaseDN();
            options.set(HEARTBEAT_SEARCH_REQUEST, Requests.newSearchRequest(
                    base == null ? "" : base.toString(), SearchScope.BASE_OBJECT,
                    configuration.getKeepAliveSearchRequestFilter(), "1.1"));
        }
        return options;
    }

    private static synchronized LDAPConnectionFactory physicalFactory(DynamicRoute route, Partition.Server server) {
        return route.physicalFactories.computeIfAbsent(server, key -> new LDAPConnectionFactory(
                key.getHostPort().getHost(), key.getHostPort().getPort(),
                connectionOptions(key, route.configuration)));
    }

    private static boolean usesUserConnections(DynamicRoute route) {
        return selectOperationIdentity(route) == OperationIdentity.CLIENT;
    }

    private static OperationIdentity selectOperationIdentity(DynamicRoute route) {
        synchronized (route) {
            switch (route.configuredIdentityMode) {
                case CLIENT:
                    return OperationIdentity.CLIENT;
                case SPECIFIC:
                    return route.hasServiceCredentials ? OperationIdentity.SPECIFIC : OperationIdentity.UNAVAILABLE;
                case AUTO:
                    if (!route.hasServiceCredentials) {
                        return OperationIdentity.CLIENT;
                    }
                    break;
                case PROXY_AUTH:
                    if (!route.hasServiceCredentials) {
                        return OperationIdentity.UNAVAILABLE;
                    }
                    break;
                default:
                    return OperationIdentity.UNAVAILABLE;
            }
            if (route.operationIdentity != null && route.nextCapabilityProbe - System.nanoTime() > 0) {
                return route.operationIdentity;
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
                        continue;
                    }
                    supportedServer = true;
                } catch (LdapException e) {
                    unknownServer = true;
                }
            }
            if (route.configuredIdentityMode == OperationIdentityMode.PROXY_AUTH) {
                route.operationIdentity = supportedServer && !unsupportedServer && !unknownServer
                        ? OperationIdentity.PROXY_AUTH : OperationIdentity.UNAVAILABLE;
            } else if (unknownServer) {
                route.operationIdentity = OperationIdentity.UNAVAILABLE;
            } else if (unsupportedServer) {
                route.operationIdentity = OperationIdentity.SPECIFIC;
            } else if (supportedServer && !unknownServer) {
                route.operationIdentity = OperationIdentity.PROXY_AUTH;
            } else {
                route.operationIdentity = OperationIdentity.UNAVAILABLE;
            }
            route.nextCapabilityProbe = System.nanoTime()
                    + (unknownServer ? TimeUnit.SECONDS.toNanos(30) : TimeUnit.MINUTES.toNanos(5));
            return route.operationIdentity;
        }
    }

    private Result bindUser(DynamicRoute route, BindOperation request, ProxyRewriter mapping,
                            List<org.forgerock.opendj.ldap.controls.Control> controls,
                            ConnectionRoutingContext routingContext, String identityMode, String shard, long proxyId) {
        final DN remoteDN = mapping.dn(request.getBindDN(), true);
        logProxyRequest(request, identityMode, shard, routingContext, proxyId, remoteDN.toString(), null, null);
        final byte[] password = request.getSimplePassword().toByteArray();
        LdapException lastFailure = null;
        routingContext.onAcquisitionStarted();
        try {
            for (Partition.Server server : userBindServers(route, remoteDN)) {
                Connection connection = null;
                try {
                    try {
                        connection = physicalFactory(route, server).getConnection();
                        routingContext.onServerSelected(server.getHostPort().toString());
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
                    final UserBindTier tier = route.userBindTiers.get(server);
                    final Result result;
                    tier.requestStarted(server);
                    try {
                        result = new RoutedConnection(connection, routingContext).bind(bind);
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
                        tier.requestCompleted(server);
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
        route.primaryUserBindTier.addServers(result, dn);
        route.secondaryUserBindTier.addServers(result, dn);
        return result;
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

    /** Maps SDK-only remote transport failures to LDAP result codes valid on the client connection. */
    private static ResultCode clientResultCode(Result result) {
        final ResultCode resultCode = result.getResultCode();
        if (resultCode.equals(ResultCode.CLIENT_SIDE_CONNECT_ERROR)
                || resultCode.equals(ResultCode.CLIENT_SIDE_SERVER_DOWN)
                || resultCode.equals(ResultCode.CLIENT_SIDE_TIMEOUT)) {
            return ResultCode.UNAVAILABLE;
        }
        return resultCode;
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
                final Partition partition;
                try {
                    partition = discovery.getPartition(mapping.dn(targetDN(operation), true));
                } catch (ProxyRewriter.RewriteException e) {
                    operation.setResponseData(new DirectoryException(e.resultCode, LocalizableMessage.raw(e.getMessage())));
                    return;
                }
                if (partition == null || partition.getPrimaryServers().isEmpty()
                        && partition.getSecondaryServers().isEmpty()) {
                    operation.setResponseData(new DirectoryException(ResultCode.UNAVAILABLE,
                            LocalizableMessage.raw("No proxy backend servers are available for the target DN")));
                    return;
                }
                route = dynamicRoutes.computeIfAbsent(partition,
                        key -> new DynamicRoute(key, discovery, cfg));
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
        final OperationIdentity operationIdentity = route == null ? OperationIdentity.PROXY_AUTH
                : existingSession != null && existingSession.route == route ? OperationIdentity.CLIENT
                : selectOperationIdentity(route);
        if (operationIdentity == OperationIdentity.UNAVAILABLE && operation.getOperationType() != OperationType.BIND) {
            operation.setResponseData(new DirectoryException(ResultCode.UNAVAILABLE,
                    LocalizableMessage.raw("The proxy operation identity is unavailable for the target route")));
            return;
        }
        final boolean directUserMode = operationIdentity == OperationIdentity.CLIENT;
        final boolean useProxiedAuth = operationIdentity == OperationIdentity.PROXY_AUTH;
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
        final ConnectionFactory requestFactory = operation.getOperationType() == OperationType.BIND || directUserMode
                ? operationBindFactory : operationFactory;
        if (requestFactory == null) {
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
        final ConnectionRoutingContext routingContext = new ConnectionRoutingContext();
        final long proxyId = PROXY_IDS.incrementAndGet();
        final String identityMode = operation.getOperationType() == OperationType.BIND
                ? "client" : operationIdentity.toString().toLowerCase(Locale.ROOT).replace('_', '-');
        final String shard = route != null && route.configuration != null ? route.configuration.getShard() : null;
        boolean searchSubordinates = false;
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
                        result = bindUser(route, request, mapping, controls, routingContext, identityMode, shard, proxyId);
                    } else {
                        final byte[] password = request.getSimplePassword().toByteArray();
                        try {
                            final SimpleBindRequest bindRequest = newSimpleBindRequest(
                                    mapping.dn(request.getBindDN(), true).toString(), password);
                            addRequestControls(bindRequest, controls);
                            logProxyRequest(operation, identityMode, shard, routingContext, proxyId,
                                    bindRequest.getName(), null, null);
                            try (Connection connection = requestFactory.getConnectionWithContext(routingContext)) {
                                result = connection.bind(bindRequest);
                            } catch (LdapException e) {
                                routingContext.onComplete(null);
                                result = e.getResult();
                            }
                        } finally {
                            Arrays.fill(password, (byte) 0);
                        }
                    }
                    routingContext.onComplete(null);
                    request.setResultCode(clientResultCode(result));
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
                if (useProxiedAuth) addProxiedAuthControl(operation,addRequest,mapping);
                Result result;
                logProxyRequest(operation, identityMode, shard, routingContext, proxyId, addRequest.getName().toString(), null, null);
                try (Connection connection = userSession == null ? requestFactory.getConnectionWithContext(routingContext) : borrowUserConnection(userSession, routingContext)) {
                    result = connection.add(addRequest);
                } catch (LdapException e) {
                    routingContext.onComplete(null);
                    result =e.getResult();
                }
                request.setResultCode(clientResultCode(result));
                addResponseControls(operation, result, mapping);
                break;
            }
            case MODIFY: {
                final ModifyOperation request = (ModifyOperation) operation;
                ModifyRequest modifyRequest=Requests.newModifyRequest(mapping.dn(request.getEntryDN(), true));
                addRequestControls(modifyRequest, controls);
                if (useProxiedAuth) addProxiedAuthControl(operation,modifyRequest,mapping);
                for(Modification  modify: request.getModifications()){
                    final String attributeName = modify.getAttribute().getAttributeDescription().toString();
                    modifyRequest=modifyRequest.addModification(
                            modify.getModificationType(),
                            mapping.attributeForWrite(attributeName),
                            StreamSupport.stream(modify.getAttribute().spliterator(),false)
                                    .map(value -> mapping.value(attributeName, value, true)).toArray(Object[]::new)
                    );
                }
                Result result;
                logProxyRequest(operation, identityMode, shard, routingContext, proxyId, modifyRequest.getName().toString(), null, null);
                try (Connection connection = userSession == null ? requestFactory.getConnectionWithContext(routingContext) : borrowUserConnection(userSession, routingContext)) {
                    result = connection.modify(modifyRequest);
                } catch (LdapException e) {
                    routingContext.onComplete(null);
                    result =e.getResult();
                }
                request.setResultCode(clientResultCode(result));
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
                if (useProxiedAuth) addProxiedAuthControl(operation,modifyDNRequest,mapping);
                Result result;
                logProxyRequest(operation, identityMode, shard, routingContext, proxyId, modifyDNRequest.getName().toString(), null, null);
                try (Connection connection = userSession == null ? requestFactory.getConnectionWithContext(routingContext) : borrowUserConnection(userSession, routingContext)) {
                    result = connection.modifyDN(modifyDNRequest);
                } catch (LdapException e) {
                    routingContext.onComplete(null);
                    result =e.getResult();
                }
                request.setResultCode(clientResultCode(result));
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
                if (useProxiedAuth) addProxiedAuthControl(operation,compareRequest,mapping);
                Result result;
                logProxyRequest(operation, identityMode, shard, routingContext, proxyId, compareRequest.getName().toString(), null, null);
                try (Connection connection = userSession == null ? requestFactory.getConnectionWithContext(routingContext) : borrowUserConnection(userSession, routingContext)) {
                    result = connection.compare(compareRequest);
                } catch (LdapException e) {
                    routingContext.onComplete(null);
                    result =e.getResult();
                }
                request.setResultCode(clientResultCode(result));
                addResponseControls(operation, result, mapping);
                break;
            }
            case DELETE:{
                final DeleteOperation request = (DeleteOperation) operation;
                final DeleteRequest deleteRequest=Requests.newDeleteRequest(mapping.dn(request.getEntryDN(), true));
                addRequestControls(deleteRequest, controls);
                if (useProxiedAuth) addProxiedAuthControl(operation,deleteRequest,mapping);
                Result result;
                logProxyRequest(operation, identityMode, shard, routingContext, proxyId, deleteRequest.getName().toString(), null, null);
                try (Connection connection = userSession == null ? requestFactory.getConnectionWithContext(routingContext) : borrowUserConnection(userSession, routingContext)) {
                    result = connection.delete(deleteRequest);
                } catch (LdapException e) {
                    routingContext.onComplete(null);
                    result =e.getResult();
                }
                request.setResultCode(clientResultCode(result));
                addResponseControls(operation, result, mapping);
                break;
            }
            case EXTENDED:{
                final ExtendedOperation request = (ExtendedOperation) operation;
                final ExtendedRequest extendedRequest=Requests.newGenericExtendedRequest(request.getRequestOID(),request.getRequestValue());
                addRequestControls(extendedRequest, controls);
                if (useProxiedAuth) addProxiedAuthControl(operation,extendedRequest,mapping);
                ExtendedResult result;
                logProxyRequest(operation, identityMode, shard, routingContext, proxyId, null, null, null);
                try (Connection connection = userSession == null ? requestFactory.getConnectionWithContext(routingContext) : borrowUserConnection(userSession, routingContext)) {
                    result = connection.extendedRequest(extendedRequest);
                    request.setResultCode(clientResultCode(result));
                    request.setResponseValue(result.getValue());
                    addResponseControls(operation, result, mapping);
                } catch (LdapException e) {
                    routingContext.onComplete(null);
                    request.setResultCode(clientResultCode(e.getResult()));
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
                if (useProxiedAuth) addProxiedAuthControl(operation,searchRequest,mapping);
                logProxyRequest(operation, identityMode, shard, routingContext, proxyId, searchRequest.getName().toString(), searchRequest.getFilter().toString(),
                        searchRequest.getScope().toString());
                try (Connection connection = userSession == null ? requestFactory.getConnectionWithContext(routingContext) : borrowUserConnection(userSession, routingContext)) {
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
                                final Map<org.forgerock.opendj.ldap.schema.ObjectClass, String> objectClasses =
                                        new LinkedHashMap<>();
                                final Schema localSchema = (serverContext != null
                                        ? serverContext.getSchema() : Schema.getDefaultSchema()).asNonStrictSchema();
                                for (org.forgerock.opendj.ldap.Attribute attr : rewritten.getAllAttributes()){
                                    final AttributeType type = attr.getAttributeDescription().getAttributeType();
                                    if (type.isObjectClass() && !attr.isEmpty()) {
                                        for (ByteString value : attr) {
                                            final String name = value.toString().trim();
                                            objectClasses.put(localSchema.getObjectClass(name), name);
                                        }
                                    }
                                    // Direct delivery bypasses returnEntry(), which normally adds objectClass
                                    // to the attribute maps for LDAP encoding. Retain it here, even if empty
                                    // for a types-only search, as well as in the internal object-class map.
                                    final AttributeBuilder attrBuilder=new AttributeBuilder(attr.getAttributeDescription());
                                    for (final ByteString value: attr) {
                                        attrBuilder.add(value);
                                    }
                                    final Map<AttributeType,List<Attribute>> attributes =
                                            type.isOperational() ? operationalAttributes : userAttributes;
                                    attributes.computeIfAbsent(type, ignored -> new ArrayList<>()).add(attrBuilder.toAttribute());
                                }
                                request.sendSearchEntry(new org.opends.server.types.SearchResultEntry(
                                        new Entry(rewritten.getName(),objectClasses,userAttributes,operationalAttributes),
                                        convertResponseControls(sr.getControls(), mapping)));
                            }else{
                                final SearchResultReference sr=entryReader.readReference();
                                request.sendSearchReference(new org.opends.server.types.SearchResultReference(
                                        sr.getURIs(), convertResponseControls(sr.getControls(), mapping)));
                            }
                        }
                        final Result result = entryReader.readResult();
                        request.setResultCode(clientResultCode(result));
                        addResponseControls(operation, result, mapping);
                    }
                } catch (LdapException e) {
                    routingContext.onComplete(null);
                   request.setResultCode(clientResultCode(e.getResult()));
                   addResponseControls(operation, e.getResult(), mapping);
                } catch (SearchResultReferenceIOException | DirectoryException e) {
                    throw new RuntimeException(e);
                }
                searchSubordinates = true;
                break;
            }
            case ABANDON:
            case UNBIND:
            default:
                throw new IllegalStateException("Proxy backend should not receive " + operation.getOperationType() + " requests.");
        }
        } catch (ProxyRewriter.RewriteException e) {
            operation.setResponseData(new DirectoryException(e.resultCode, LocalizableMessage.raw(e.getMessage())));
        } finally {
            String remoteAuthDN = null;
            String authorizationDN = null;
            if (routingContext.getRespondingServer() != null) {
                if (operation.getOperationType() == OperationType.BIND) {
                    if (routingContext.getResponseCode() == ResultCode.SUCCESS) {
                        try {
                            remoteAuthDN = mapping.dn(((BindOperation) operation).getBindDN(), true).toString();
                        } catch (ProxyRewriter.RewriteException ignored) {
                            // The response remains useful when the identity cannot be rewritten.
                        }
                    }
                } else if (directUserMode) {
                    remoteAuthDN = userSession == null ? "" : userSession.key.get(0).toString();
                } else if (route != null) {
                    remoteAuthDN = route.serviceIdentity;
                }
                if (useProxiedAuth && operation.getOperationType() != OperationType.BIND) {
                    final DN dn = operation.getClientConnection().getAuthenticationInfo().getAuthorizationDN();
                    authorizationDN = dn == null ? "" : dn.toString();
                }
            }
            if (routingContext.wasRequestIssued()) {
                AccessLogger.deferProxyResponse(operation, proxyId, routingContext.getRespondingServer(),
                        routingContext.getSelectedServer(), routingContext.getResponseCode(), remoteAuthDN,
                        authorizationDN, operation.getOperationType() == OperationType.SEARCH
                                ? routingContext.getSearchEntries() : -1, routingContext.getPoolSnapshot(),
                        routingContext.getElapsedMillis());
            }
        }
        if (searchSubordinates) {
            final SearchOperation request = (SearchOperation) operation;
            if (serverContext != null
                    && serverContext.getBackendConfigManager().findBackendForEntry(request.getBaseDN()) == this) {
                LocalBackendWorkflowElement.executeSearchOnSubordinates(request);
            }
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
