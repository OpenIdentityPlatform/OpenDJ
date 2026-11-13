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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.server.ServiceDiscoveryMechanismCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.core.ServerContext;
import org.opends.server.discovery.ServiceDiscoveryMechanism;
import org.opends.server.discovery.ServiceDiscoveryMechanismConfigManager;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

@Test(sequential = true)
public class ExternalDiscoveryProviderTest extends DirectoryServerTestCase {
    private static final String LEGACY_PROVIDER = "legacy.discovery.LegacyDiscoveryProvider";

    @BeforeClass
    public void compileLegacyProvider() throws Exception {
        final Path fixture = Paths.get(System.getProperty("basedir"), "target", "legacy-discovery-provider");
        Files.createDirectories(fixture);
        for (String name : new String[] {"ServiceDiscoveryMechanism.java", "LegacyDiscoveryProvider.java"}) {
            try (InputStream source = getClass().getResourceAsStream("/legacy-discovery/" + name)) {
                assertNotNull(source, name);
                Files.copy(source, fixture.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The legacy provider fixture requires a JDK");
        final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        assertEquals(compiler.run(null, diagnostics, diagnostics, "--release", "11", "-classpath",
                System.getProperty("java.class.path"), "-d", fixture.toString(),
                fixture.resolve("ServiceDiscoveryMechanism.java").toString(),
                fixture.resolve("LegacyDiscoveryProvider.java").toString()), 0,
                diagnostics.toString(StandardCharsets.UTF_8.name()));
        // Publish only the old provider binary. Its original interface must not shadow the current SPI.
        final Path testClasses = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        final Path binary = Paths.get("legacy", "discovery", "LegacyDiscoveryProvider.class");
        Files.createDirectories(testClasses.resolve(binary).getParent());
        Files.copy(fixture.resolve(binary), testClasses.resolve(binary), StandardCopyOption.REPLACE_EXISTING);
    }

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
    public void rejectsLegacyProviderWithMigrationDiagnostic() {
        final ServiceDiscoveryMechanismCfg config = legacyConfiguration();
        final ServiceDiscoveryMechanismConfigManager manager =
                new ServiceDiscoveryMechanismConfigManager(mock(ServerContext.class));
        final List<LocalizableMessage> reasons = new ArrayList<>();
        assertFalse(manager.isConfigurationAddAcceptable(config, reasons));
        assertTrue(reasons.toString().contains(LEGACY_PROVIDER), reasons.toString());
        assertTrue(reasons.toString().contains("rebuild"), reasons.toString());
        final ConfigChangeResult result = manager.applyConfigurationAdd(config);
        assertEquals(result.getResultCode(), ResultCode.OTHER);
        assertTrue(result.getMessages().toString().contains("rebuild"), result.getMessages().toString());
        assertNull(manager.getMechanism("legacy-provider"));
        assertEquals(manager.applyConfigurationDelete(config).getResultCode(), ResultCode.SUCCESS,
                "A rejected provider must not prevent its configuration from being deleted");
    }

    @Test
    public void continuesStartupAfterRejectingLegacyProvider() throws Exception {
        final ServiceDiscoveryMechanismCfg current = mock(ServiceDiscoveryMechanismCfg.class);
        when(current.getJavaClass()).thenReturn(Provider.class.getName());
        when(current.name()).thenReturn("current-provider");
        final RootCfg root = mock(RootCfg.class);
        final ServiceDiscoveryMechanismCfg legacy = legacyConfiguration();
        when(root.listServiceDiscoveryMechanisms()).thenReturn(new String[] {"legacy-provider", "current-provider"});
        when(root.getServiceDiscoveryMechanism("legacy-provider")).thenReturn(legacy);
        when(root.getServiceDiscoveryMechanism("current-provider")).thenReturn(current);
        final ServerContext context = mock(ServerContext.class);
        when(context.getRootConfig()).thenReturn(root);
        final ServiceDiscoveryMechanismConfigManager manager = new ServiceDiscoveryMechanismConfigManager(context);
        manager.initializeServiceDiscoveryMechanismConfigManager();
        assertNull(manager.getMechanism("legacy-provider"));
        assertNotNull(manager.getMechanism("current-provider"));
        assertEquals(manager.getMechanism("current-provider").getName(), "current-provider");
        manager.finalize();
    }

    private ServiceDiscoveryMechanismCfg legacyConfiguration() {
        final ServiceDiscoveryMechanismCfg config = mock(ServiceDiscoveryMechanismCfg.class);
        when(config.getJavaClass()).thenReturn(LEGACY_PROVIDER);
        when(config.name()).thenReturn("legacy-provider");
        return config;
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
