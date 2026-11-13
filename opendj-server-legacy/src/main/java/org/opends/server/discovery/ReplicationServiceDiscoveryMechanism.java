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
package org.opends.server.discovery;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.SSL_CONTEXT;
import static org.forgerock.opendj.ldap.SearchScope.WHOLE_SUBTREE;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.opends.admin.ads.util.PreferredConnection.Type.LDAPS;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.X509TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.server.config.server.ReplicationServiceDiscoveryMechanismCfg;
import org.forgerock.util.Options;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.server.core.ServerContext;
import org.opends.server.types.HostPort;

/** Discovers replicated LDAP servers through the OpenDJ administration registry. */
public class ReplicationServiceDiscoveryMechanism extends ServiceDiscoveryMechanism<ReplicationServiceDiscoveryMechanismCfg>
        implements ConfigurationChangeListener<ReplicationServiceDiscoveryMechanismCfg> {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    private static final String REPLICATION_CONFIG =
            "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config";
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private volatile ReplicationTopology topology = ReplicationTopology.create("replication", null,
            Collections.emptyList());
    private ServerContext context;
    private ScheduledExecutorService executor;
    private volatile boolean refreshRequired;
    private Map<String, NodeSnapshot> cachedNodes = Collections.emptyMap();

    private static final class NodeSnapshot {
        final List<Object> registryIdentity;
        final List<DiscoveredReplica> replicas = new ArrayList<>();
        final Map<HostPort, Set<HostPort>> servers = new HashMap<>();

        NodeSnapshot(Map<ServerProperty, Object> server) {
            registryIdentity = java.util.Arrays.asList(server.get(ServerProperty.HOST_NAME),
                    server.get(ServerProperty.ADMIN_PORT), server.get(ServerProperty.LDAP_PORT),
                    server.get(ServerProperty.LDAP_ENABLED), server.get(ServerProperty.LDAPS_PORT),
                    server.get(ServerProperty.LDAPS_ENABLED));
        }
    }

    private static final class DiscoveredReplica {
        final ReplicationTopology.Replica replica;
        final Set<HostPort> replicationServers;

        DiscoveredReplica(ReplicationTopology.Replica replica, Set<HostPort> replicationServers) {
            this.replica = replica;
            this.replicationServers = replicationServers;
        }
    }

    @Override
    synchronized void initializeMechanism(ReplicationServiceDiscoveryMechanismCfg configuration, ServerContext context) {
        super.initializeMechanism(configuration, context);
        this.context = context;
        configuration.addReplicationChangeListener(this);
        restartDiscovery();
    }

    @Override
    synchronized void finalizeMechanism() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (cfg != null) {
            cfg.removeReplicationChangeListener(this);
        }
    }

    @Override
    public boolean isConfigurationChangeAcceptable(ReplicationServiceDiscoveryMechanismCfg configuration,
                                                     List<LocalizableMessage> reasons) {
        return isConfigurationAcceptable(configuration, reasons, context);
    }

    @Override
    public synchronized ConfigChangeResult applyConfigurationChange(ReplicationServiceDiscoveryMechanismCfg configuration) {
        cfg = configuration;
        cachedNodes = Collections.emptyMap();
        topology = ReplicationTopology.create(configuration.name(), configuration.getPrimaryGroupId(),
                Collections.emptyList());
        refreshRequired = true;
        notifyChangeListeners();
        restartDiscovery();
        return new ConfigChangeResult();
    }

    @Override
    boolean isConfigurationAcceptable(ReplicationServiceDiscoveryMechanismCfg configuration,
                                      List<LocalizableMessage> reasons, ServerContext context) {
        if (configuration.getBindDN() == null || configuration.getBindDN().isRootDN()
                || configuration.getBindPassword() == null || configuration.getBindPassword().isEmpty()) {
            reasons.add(LocalizableMessage.raw("Replication discovery requires bind-dn and bind-password"));
            return false;
        }
        try {
            for (String server : configuration.getBootstrapReplicationServer()) {
                HostPort.valueOf(server);
            }
            return true;
        } catch (IllegalArgumentException e) {
            reasons.add(LocalizableMessage.raw("Invalid bootstrap replication server address: " + e.getMessage()));
            return false;
        }
    }

    private synchronized void restartDiscovery() {
        if (executor != null) {
            executor.shutdownNow();
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            final Thread thread = new Thread(task, "replication-discovery-" + cfg.name());
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::refresh, 0, Math.max(1, cfg.getDiscoveryInterval()), TimeUnit.SECONDS);
    }

    private void refresh() {
        final ReplicationServiceDiscoveryMechanismCfg configuration = cfg;
        try {
            final X509TrustManager adminTrust = ServiceDiscoveryTls.trustManager(
                    configuration.getTrustManagerProviderDN(), context);
            Exception failure = null;
            for (String bootstrap : configuration.getBootstrapReplicationServer()) {
                try (ConnectionWrapper connection = connect(HostPort.valueOf(bootstrap), configuration, adminTrust)) {
                    final Set<Map<ServerProperty, Object>> registered = new ADSContext(connection).readServerRegistry();
                    final Map<HostPort, Set<HostPort>> servers = new HashMap<>();
                    final HostPort bootstrapAddress = HostPort.valueOf(bootstrap);
                    final String registeredHost = registeredBootstrapHost(bootstrapAddress, registered);
                    final Set<HostPort> roots = readReplicationServers(connection,
                            registeredHost, servers);
                    if (roots.isEmpty()) {
                        throw new IllegalStateException("The bootstrap server has no configured replication server");
                    }
                    final List<DiscoveredReplica> discovered = new ArrayList<>();
                    final Map<String, NodeSnapshot> previous;
                    synchronized (this) {
                        previous = cachedNodes;
                    }
                    final Map<String, NodeSnapshot> updatedNodes = new HashMap<>();
                    for (Map<ServerProperty, Object> server : registered) {
                        final Object id = server.get(ServerProperty.ID);
                        final String nodeId = id != null ? id.toString()
                                : String.valueOf(server.get(ServerProperty.HOST_NAME)) + ":"
                                        + server.get(ServerProperty.ADMIN_PORT);
                        final NodeSnapshot node = new NodeSnapshot(server);
                        try {
                            readReplicas(server, configuration, adminTrust, node.replicas, node.servers);
                            updatedNodes.put(nodeId, node);
                        } catch (Exception e) {
                            final NodeSnapshot cached = previous.get(nodeId);
                            if (cached != null && cached.registryIdentity.equals(node.registryIdentity)) {
                                updatedNodes.put(nodeId, cached);
                            }
                            logger.warn(LocalizableMessage.raw("Unable to read registered replication server "
                                    + server.get(ServerProperty.HOST_NAME) + ": " + e));
                        }
                    }
                    for (NodeSnapshot node : updatedNodes.values()) {
                        discovered.addAll(node.replicas);
                        node.servers.forEach(servers::putIfAbsent);
                    }
                    final Set<HostPort> reachable = ReplicationTopology.connectedServers(roots, servers);
                    final List<ReplicationTopology.Replica> replicas = new ArrayList<>();
                    for (DiscoveredReplica replica : discovered) {
                        if (!Collections.disjoint(replica.replicationServers, reachable)) {
                            replicas.add(replica.replica);
                        }
                    }
                    final ReplicationTopology updated = ReplicationTopology.create(configuration.name(),
                            configuration.getPrimaryGroupId(), replicas);
                    final boolean changed;
                    synchronized (this) {
                        if (configuration != cfg || executor == null || Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        cachedNodes = updatedNodes;
                        changed = refreshRequired || !topology.hasSameServers(updated);
                        if (changed) {
                            topology = updated;
                            refreshRequired = false;
                        }
                    }
                    if (changed) {
                        notifyChangeListeners();
                    }
                    return;
                } catch (Exception e) {
                    failure = e;
                }
            }
            logger.warn(LocalizableMessage.raw("Replication topology discovery failed: " + failure));
        } catch (Exception e) {
            logger.warn(LocalizableMessage.raw("Replication topology discovery failed: " + e));
        }
    }

    private static String registeredBootstrapHost(HostPort bootstrap, Set<Map<ServerProperty, Object>> registered) {
        for (Map<ServerProperty, Object> server : registered) {
            final Object host = server.get(ServerProperty.HOST_NAME);
            final Object port = server.get(ServerProperty.ADMIN_PORT);
            if (host == null || port == null) {
                continue;
            }
            try {
                if (bootstrap.getPort() == Integer.parseInt(String.valueOf(port))
                        && sameHost(bootstrap.getHost(), String.valueOf(host))) {
                    return String.valueOf(host);
                }
            } catch (IllegalArgumentException e) {
                // Ignore invalid registry entries.
            }
        }
        return bootstrap.getHost();
    }

    private static boolean sameHost(String first, String second) {
        if (first.equalsIgnoreCase(second)) {
            return true;
        }
        try {
            for (InetAddress left : InetAddress.getAllByName(first)) {
                for (InetAddress right : InetAddress.getAllByName(second)) {
                    if (left.equals(right)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private ConnectionWrapper connect(HostPort address, ReplicationServiceDiscoveryMechanismCfg configuration,
                                      X509TrustManager trust) throws Exception {
        return new ConnectionWrapper(address, LDAPS, configuration.getBindDN(), configuration.getBindPassword(),
                CONNECT_TIMEOUT_MS, TrustManagers.checkHostName(address.getHost(), trust), null, CONNECT_TIMEOUT_MS);
    }

    private void readReplicas(Map<ServerProperty, Object> server, ReplicationServiceDiscoveryMechanismCfg configuration,
                              X509TrustManager trust, List<DiscoveredReplica> replicas,
                              Map<HostPort, Set<HostPort>> servers)
            throws Exception {
        final Object hostValue = server.get(ServerProperty.HOST_NAME);
        if (hostValue == null) {
            return;
        }
        final String host = String.valueOf(hostValue);
        final Object adminPort = server.get(ServerProperty.ADMIN_PORT);
        final Object ldapPort = server.get(configuration.isUseSSL() ? ServerProperty.LDAPS_PORT : ServerProperty.LDAP_PORT);
        final Object ldapEnabled = server.get(configuration.isUseSSL() ? ServerProperty.LDAPS_ENABLED : ServerProperty.LDAP_ENABLED);
        if (adminPort == null) {
            return;
        }
        final HostPort admin = new HostPort(host, Integer.parseInt(String.valueOf(adminPort)));
        final HostPort ldap = ldapPort == null || !Boolean.parseBoolean(String.valueOf(ldapEnabled)) ? null
                : new HostPort(host, Integer.parseInt(String.valueOf(ldapPort)));
        final Options options = Options.defaultOptions();
        if (configuration.isUseSSL()) {
            options.set(SSL_CONTEXT, ServiceDiscoveryTls.context(host, trust));
        }
        try (ConnectionWrapper connection = connect(admin, configuration, trust)) {
            readReplicationServers(connection, host, servers);
            if (ldap == null) {
                return;
            }
            try (ConnectionEntryReader entries = connection.getConnection().search(newSearchRequest(
                    REPLICATION_CONFIG, WHOLE_SUBTREE, "(objectclass=ds-cfg-replication-domain)",
                    "ds-cfg-base-dn", "ds-cfg-group-id", "ds-cfg-replication-server"))) {
                while (entries.hasNext()) {
                    final SearchResultEntry entry = entries.readEntry();
                    final Attribute base = entry.getAttribute("ds-cfg-base-dn");
                    final Attribute group = entry.getAttribute("ds-cfg-group-id");
                    if (base != null) {
                        replicas.add(new DiscoveredReplica(new ReplicationTopology.Replica(
                                DN.valueOf(base.firstValueAsString()), ldap,
                                group == null ? 1 : Integer.parseInt(group.firstValueAsString()), options),
                                serverAddresses(entry.getAttribute("ds-cfg-replication-server"), host)));
                    }
                }
                entries.readResult();
            }
        }
    }

    private Set<HostPort> readReplicationServers(ConnectionWrapper connection, String host,
                                                  Map<HostPort, Set<HostPort>> graph) throws Exception {
        final Set<HostPort> local = new HashSet<>();
        try (ConnectionEntryReader entries = connection.getConnection().search(newSearchRequest(
                REPLICATION_CONFIG, WHOLE_SUBTREE, "(objectclass=ds-cfg-replication-server)",
                "ds-cfg-replication-port", "ds-cfg-replication-server"))) {
            while (entries.hasNext()) {
                final SearchResultEntry entry = entries.readEntry();
                final Attribute port = entry.getAttribute("ds-cfg-replication-port");
                if (port != null) {
                    final HostPort address = new HostPort(host, Integer.parseInt(port.firstValueAsString()));
                    local.add(address);
                    graph.put(address, serverAddresses(entry.getAttribute("ds-cfg-replication-server"), host));
                }
            }
            entries.readResult();
        }
        return local;
    }

    private static Set<HostPort> serverAddresses(Attribute attribute, String ownerHost) {
        final Set<HostPort> addresses = new HashSet<>();
        if (attribute != null) {
            for (ByteString value : attribute) {
                final HostPort address = HostPort.valueOf(value.toString());
                final String host = address.getHost();
                boolean loopback = host.equalsIgnoreCase("localhost") || host.equalsIgnoreCase("localhost.");
                if (!loopback && (host.indexOf(':') >= 0 || host.matches("[0-9.]+"))) {
                    try {
                        loopback = InetAddress.getByName(host).isLoopbackAddress();
                    } catch (java.net.UnknownHostException e) {
                        // Keep invalid or unresolvable addresses unchanged for normal discovery handling.
                    }
                }
                addresses.add(loopback ? new HostPort(ownerHost, address.getPort()) : address);
            }
        }
        return addresses;
    }

    @Override
    public Partition getPartition(DN target) {
        return topology.partitionFor(target);
    }
}
