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
 * Copyright 2026 3A Systems, LLC.
 */
package legacy.discovery;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.ServiceDiscoveryMechanismCfg;
import org.opends.server.core.ServerContext;
import org.opends.server.discovery.Partition;
import org.opends.server.discovery.ServiceDiscoveryChangeListener;
import org.opends.server.discovery.ServiceDiscoveryMechanism;

/** A provider compiled against the original interface, then loaded unchanged against the current SPI. */
public class LegacyDiscoveryProvider implements ServiceDiscoveryMechanism<ServiceDiscoveryMechanismCfg> {
    @Override
    public String getName() {
        return "legacy-provider";
    }

    @Override
    public boolean isConfigurationAcceptable(ServiceDiscoveryMechanismCfg configuration,
            List<LocalizableMessage> reasons, ServerContext context) {
        return true;
    }

    @Override
    public void initializeMechanism(ServiceDiscoveryMechanismCfg configuration, ServerContext context) {
    }

    @Override
    public void finalizeMechanism() {
    }

    @Override
    public boolean registerChangeListener(ServiceDiscoveryChangeListener listener) {
        return true;
    }

    @Override
    public void deregisterChangeListener(ServiceDiscoveryChangeListener listener) {
    }

    @Override
    public Set<Partition> getPartitions(Collection<DN> baseDNs) {
        return Collections.emptySet();
    }
}
