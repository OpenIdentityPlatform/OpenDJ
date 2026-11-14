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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.StaticServiceDiscoveryMechanismCfg;
import org.forgerock.util.Options;
import org.opends.server.core.ServerContext;
import org.opends.server.types.HostPort;

import java.util.*;
import javax.net.ssl.X509TrustManager;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.SSL_CONTEXT;

public class StaticServiceDiscoveryMechanism extends ServiceDiscoveryMechanism<StaticServiceDiscoveryMechanismCfg>
        implements ConfigurationChangeListener<StaticServiceDiscoveryMechanismCfg> {
    private ServerContext context;

    @Override
    public void initializeMechanism(StaticServiceDiscoveryMechanismCfg configuration, ServerContext serverContext) {
        super.initializeMechanism(configuration, serverContext);
        context = serverContext;
        partition = createPartition(configuration, serverContext);
        configuration.addStaticChangeListener(this);
    }

    private static Partition createPartition(StaticServiceDiscoveryMechanismCfg configuration, ServerContext context) {
        try {
            final X509TrustManager trust = configuration.isUseSSL()
                    ? ServiceDiscoveryTls.trustManager(configuration.getTrustManagerProviderDN(), context) : null;
            final Collection<Partition.Server> primary = servers(configuration.getPrimaryServer(), trust);
            final Collection<Partition.Server> secondary = servers(configuration.getSecondaryServer(), trust);
            return new Partition(configuration.name(), primary, secondary);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot configure TLS for static discovery: " + e.getMessage(), e);
        }
    }

    @Override
    public void finalizeMechanism() {
        cfg.removeStaticChangeListener(this);
    }

    @Override
    public boolean isConfigurationChangeAcceptable(StaticServiceDiscoveryMechanismCfg configuration,
                                                     List<LocalizableMessage> reasons) {
        return isConfigurationAcceptable(configuration, reasons, context);
    }

    @Override
    public synchronized ConfigChangeResult applyConfigurationChange(StaticServiceDiscoveryMechanismCfg configuration) {
        try {
            final Partition updated = createPartition(configuration, context);
            cfg = configuration;
            partition = updated;
            notifyChangeListeners();
            return new ConfigChangeResult();
        } catch (IllegalArgumentException e) {
            final ConfigChangeResult result = new ConfigChangeResult();
            result.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            result.addMessage(LocalizableMessage.raw(e.getMessage()));
            return result;
        }
    }

    private static Collection<Partition.Server> servers(Collection<String> addresses, X509TrustManager trust)
            throws Exception {
        final Collection<Partition.Server> servers = new LinkedHashSet<>();
        for (String value : addresses) {
            final HostPort address = HostPort.valueOf(value, 389);
            final Options options = Options.defaultOptions();
            if (trust != null) {
                options.set(SSL_CONTEXT, ServiceDiscoveryTls.context(address.getHost(), trust));
            }
            servers.add(new Partition.Server(address, options));
        }
        return servers;
    }

    @Override
    public boolean isConfigurationAcceptable(StaticServiceDiscoveryMechanismCfg configuration,
                                      List<LocalizableMessage> reasons, ServerContext context) {
        if ((configuration.getBindDN() == null) != (configuration.getBindPassword() == null)) {
            reasons.add(LocalizableMessage.raw("bind-dn and bind-password must be specified together"));
            return false;
        }
        if (configuration.getBindDN() != null
                && (configuration.getBindDN().isRootDN() || configuration.getBindPassword().isEmpty())) {
            reasons.add(LocalizableMessage.raw("The service bind DN and password must not be empty"));
            return false;
        }
        if (configuration.isUseSSL()) {
            try {
                ServiceDiscoveryTls.trustManager(configuration.getTrustManagerProviderDN(), context);
            } catch (Exception e) {
                reasons.add(LocalizableMessage.raw("Cannot configure TLS for static discovery: " + e.getMessage()));
                return false;
            }
        }
        return true;
    }
}
