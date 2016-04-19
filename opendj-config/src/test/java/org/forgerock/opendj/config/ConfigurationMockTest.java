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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.*;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.server.config.meta.PluginCfgDefn.PluginType;
import org.forgerock.opendj.server.config.server.AttributeCleanupPluginCfg;
import org.forgerock.opendj.server.config.server.CollectiveAttributeSubentriesVirtualAttributeCfg;
import org.forgerock.opendj.server.config.server.CoreSchemaCfg;
import org.forgerock.opendj.server.config.server.GoverningStructureRuleVirtualAttributeCfg;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.testng.annotations.Test;

/** Test case to ensure that ConfigurationMock class is correct. */
@SuppressWarnings("javadoc")
public class ConfigurationMockTest extends ConfigTestCase {

    @Test
    public void testPropertyWithStringReturnValue() {
        GoverningStructureRuleVirtualAttributeCfg mock = mockCfg(GoverningStructureRuleVirtualAttributeCfg.class);
        assertThat(mock.getJavaClass()).
            isEqualTo("org.opends.server.extensions.GoverningSturctureRuleVirtualAttributeProvider");
    }

    @Test
    public void testPropertyWithLongReturnValue() throws Exception {
        LDAPConnectionHandlerCfg mock = mockCfg(LDAPConnectionHandlerCfg.class);
        assertThat(mock.getMaxRequestSize()).isEqualTo(5 * 1000 * 1000);
    }

    @Test
    public void testPropertyWithEnumReturnValue() throws Exception {
        AttributeCleanupPluginCfg mock = mockCfg(AttributeCleanupPluginCfg.class);
        assertThat(mock.getPluginType()).containsOnly(PluginType.PREPARSEADD, PluginType.PREPARSEMODIFY);
    }

    @Test
    public void testPropertyWithAttributeTypeReturnValue() throws Exception {
        CollectiveAttributeSubentriesVirtualAttributeCfg mock =
                mockCfg(CollectiveAttributeSubentriesVirtualAttributeCfg.class);
        assertThat(mock.getAttributeType()).isEqualTo(
                Schema.getDefaultSchema().getAttributeType("collectiveAttributeSubentries"));
    }

    @Test
    public void testPropertyWithNoDefaultBooleanReturnValue() throws Exception {
        CollectiveAttributeSubentriesVirtualAttributeCfg mock =
            mockCfg(CollectiveAttributeSubentriesVirtualAttributeCfg.class);
        // should use default mockito behavior
        assertThat(mock.isEnabled()).isEqualTo(false);
    }

    @Test
    public void testPropertyWithDefaultBooleanReturnValue() throws Exception {
        CoreSchemaCfg mock = mockCfg(CoreSchemaCfg.class);
        assertThat(mock.isStrictFormatCountryString()).isEqualTo(true);
    }

    @Test
    public void testNonPropertyMethod() throws Exception {
        CoreSchemaCfg mock = mockCfg(CoreSchemaCfg.class);
        assertThat(mock.dn()).isNull();

        // Ensure we can add behavior to the mock
        when(mock.dn()).thenReturn(DN.rootDN());
        assertThat(mock.dn()).isNotNull();
        assertThat(mock.dn().toString()).isEqualTo(DN.rootDN().toString());
    }

}
