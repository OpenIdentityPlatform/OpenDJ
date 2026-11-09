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
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.StaticServiceDiscoveryMechanismCfg;
import org.forgerock.util.Options;
import org.opends.server.core.ServerContext;
import org.opends.server.types.HostPort;

import java.util.*;

public class StaticServiceDiscoveryMechanism extends ServiceDiscoveryMechanism<StaticServiceDiscoveryMechanismCfg>{

    @Override
    void initializeMechanism(StaticServiceDiscoveryMechanismCfg configuration, ServerContext serverContext) {
        super.initializeMechanism(configuration, serverContext);

        final Collection<Partition.Server> primary=new HashSet<>();
        final Collection<Partition.Server> secondary=new HashSet<>();
        for(String server: cfg.getPrimaryServer()) {
            primary.add(new Partition.Server(HostPort.valueOf(server,389), Options.defaultOptions()));
        }
        for(String server: cfg.getSecondaryServer()) {
            secondary.add(new Partition.Server(HostPort.valueOf(server,389), Options.defaultOptions()));
        }
        partition=new Partition(cfg.name(),primary,secondary);
    }
}
