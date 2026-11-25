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
package org.forgerock.opendj.config;

import org.forgerock.opendj.server.config.meta.ReplicationServiceDiscoveryMechanismCfgDefn;
import org.forgerock.opendj.server.config.meta.ServiceDiscoveryMechanismCfgDefn;
import org.forgerock.opendj.server.config.meta.StaticServiceDiscoveryMechanismCfgDefn;
import org.forgerock.opendj.server.config.server.ServiceDiscoveryMechanismCfg;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/** Checks the common trust-provider contract and its administrative actions. */
@Test(singleThreaded = true)
public class ServiceDiscoveryTrustConfigurationTest extends ForgeRockTestCase {
    @Test
    public void inheritsTrustProviderAndPreservesAdministrativeActions() throws Exception {
        assertNotNull(ServiceDiscoveryMechanismCfg.class.getDeclaredMethod("getTrustManagerProviderDN"));
        assertNotNull(ServiceDiscoveryMechanismCfgDefn.getInstance().getPropertyDefinition("trust-manager-provider"));
        assertEquals(StaticServiceDiscoveryMechanismCfgDefn.getInstance()
                .getPropertyDefinition("trust-manager-provider").getAdministratorAction().getType(),
                AdministratorAction.Type.COMPONENT_RESTART);
        assertEquals(ReplicationServiceDiscoveryMechanismCfgDefn.getInstance()
                .getPropertyDefinition("trust-manager-provider").getAdministratorAction().getType(),
                AdministratorAction.Type.NONE);
    }
}
