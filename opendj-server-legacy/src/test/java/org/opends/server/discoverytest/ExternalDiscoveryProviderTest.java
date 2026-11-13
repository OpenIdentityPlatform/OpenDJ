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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.discoverytest;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.ServiceDiscoveryMechanismCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.core.ServerContext;
import org.opends.server.discovery.ServiceDiscoveryMechanism;
import org.opends.server.discovery.ServiceDiscoveryMechanismConfigManager;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test(sequential = true)
public class ExternalDiscoveryProviderTest extends DirectoryServerTestCase {
    public static class Provider extends ServiceDiscoveryMechanism<ServiceDiscoveryMechanismCfg> {
        static boolean acceptable;
        static boolean initialized;
        static boolean finalized;

        @Override
        public boolean isConfigurationAcceptable(ServiceDiscoveryMechanismCfg config,
                List<LocalizableMessage> reasons, ServerContext context) {
            return acceptable;
        }

        @Override
        public void initializeMechanism(ServiceDiscoveryMechanismCfg config, ServerContext context) {
            super.initializeMechanism(config, context);
            initialized = true;
        }

        @Override
        public void finalizeMechanism() {
            finalized = true;
        }
    }

    @Test
    public void managerCallsExternalProviderLifecycle() {
        Provider.acceptable = false;
        Provider.initialized = false;
        Provider.finalized = false;
        ServiceDiscoveryMechanismCfg config = mock(ServiceDiscoveryMechanismCfg.class);
        when(config.getJavaClass()).thenReturn(Provider.class.getName());
        when(config.name()).thenReturn("external-provider");
        ServiceDiscoveryMechanismConfigManager manager = new ServiceDiscoveryMechanismConfigManager(mock(ServerContext.class));
        assertFalse(manager.isConfigurationAddAcceptable(config, new ArrayList<LocalizableMessage>()));
        Provider.acceptable = true;
        assertTrue(manager.isConfigurationAddAcceptable(config, new ArrayList<LocalizableMessage>()));
        assertEquals(manager.applyConfigurationAdd(config).getResultCode(), ResultCode.SUCCESS);
        assertTrue(Provider.initialized);
        assertEquals(manager.getMechanism("external-provider").getName(), "external-provider");
        assertEquals(manager.applyConfigurationDelete(config).getResultCode(), ResultCode.SUCCESS);
        assertTrue(Provider.finalized);
    }
}
