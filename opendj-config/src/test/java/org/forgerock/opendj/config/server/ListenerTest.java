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
 * Copyright 2007-2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldif.LDIF.makeEntry;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.forgerock.opendj.config.AdminTestCase;
import org.forgerock.opendj.config.TestCfg;
import org.forgerock.opendj.config.TestParentCfg;
import org.forgerock.opendj.config.server.spi.ConfigAddListener;
import org.forgerock.opendj.config.server.spi.ConfigChangeListener;
import org.forgerock.opendj.config.server.spi.ConfigDeleteListener;
import org.forgerock.opendj.config.server.spi.ConfigurationRepository;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings({ "javadoc", "rawtypes", "unchecked" })
public class ListenerTest extends AdminTestCase {

    private static final DN ROOT_CONFIG_DN = DN.valueOf("cn=config");
    private static final DN TEST_PARENTS_DN = DN.valueOf("cn=test parents,cn=config");

    @BeforeClass
    public void setUp() throws Exception {
        TestCfg.setUp();
    }

    @AfterClass
    public void tearDown() throws Exception {
        TestCfg.cleanup();
    }

    private Entry getTestParentEntry() throws Exception {
        return makeEntry("dn: cn=test parents,cn=config", "objectclass: top", "objectclass: ds-cfg-branch",
            "cn: test parents");
    }

    /** Create a mock of ConfigurationRepository with provided DNs registered. */
    private ConfigurationRepository createConfigRepositoryWithDNs(DN... dns) throws ConfigException {
        ConfigurationRepository configRepository = mock(ConfigurationRepository.class);
        for (DN dn : dns) {
            when(configRepository.hasEntry(dn)).thenReturn(true);
        }
        return configRepository;
    }

    /** Register a listener for test parent entry and return the actual registered listener. */
    private ConfigAddListener registerAddListenerForTestParent(ConfigurationRepository configRepository,
        ServerManagedObject<RootCfg> root, ConfigurationAddListener<TestParentCfg> parentListener) throws Exception {
        root.registerAddListener(TestCfg.getTestOneToManyParentRelationDefinition(), parentListener);

        ArgumentCaptor<ConfigAddListener> registered = ArgumentCaptor.forClass(ConfigAddListener.class);
        verify(configRepository).registerAddListener(eq(TEST_PARENTS_DN), registered.capture());
        return registered.getValue();
    }

    /**
     * Register a listener for test parent entry in delayed scenario and return
     * the actual registered listener.
     */
    private DelayedConfigAddListener registerAddListenerForTestParentDelayed(
        ConfigurationRepository configRepository, ServerManagedObject<RootCfg> root,
        ConfigurationAddListener<TestParentCfg> parentListener) throws Exception {
        root.registerAddListener(TestCfg.getTestOneToManyParentRelationDefinition(), parentListener);

        ArgumentCaptor<DelayedConfigAddListener> registered = ArgumentCaptor.forClass(DelayedConfigAddListener.class);
        verify(configRepository).registerAddListener(eq(ROOT_CONFIG_DN), registered.capture());
        return registered.getValue();
    }

    @Test
    public void testRegisterAddListenerWithInstantiableRelationImmediate() throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN, TEST_PARENTS_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();

        root.registerAddListener(TestCfg.getTestOneToManyParentRelationDefinition(),
            mock(ConfigurationAddListener.class));

        verify(configRepository).registerAddListener(eq(TEST_PARENTS_DN), isA(ConfigAddListener.class));
    }

    @Test
    public void testRegisterAddListenerWithInstantiableRelationDelayed() throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();

        ConfigurationAddListener<TestParentCfg> parentListener = mock(ConfigurationAddListener.class);
        root.registerAddListener(TestCfg.getTestOneToManyParentRelationDefinition(), parentListener);

        ArgumentCaptor<DelayedConfigAddListener> registered = ArgumentCaptor.forClass(DelayedConfigAddListener.class);
        verify(configRepository).registerAddListener(eq(ROOT_CONFIG_DN), registered.capture());
        // check that actual listener is the one provided to the root
        ConfigurationAddListener<?> actualListener =
            ((ServerManagedObjectAddListenerAdaptor<?>)
                ((ConfigAddListenerAdaptor<?>) registered.getValue().getDelayedAddListener()).
                    getServerManagedObjectAddListener()).getConfigurationAddListener();
        assertThat(actualListener).isEqualTo(parentListener);
    }

    @Test
    public void testRegisterAddListenerWithInstantiableRelationDelayedThenActualized() throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();

        // register a listener to root
        ConfigurationAddListener<TestParentCfg> parentListener = mock(ConfigurationAddListener.class);
        root.registerAddListener(TestCfg.getTestOneToManyParentRelationDefinition(), parentListener);

        // get the delayed listener registered to configuration repository
        ArgumentCaptor<DelayedConfigAddListener> registered = ArgumentCaptor.forClass(DelayedConfigAddListener.class);
        verify(configRepository).registerAddListener(eq(ROOT_CONFIG_DN), registered.capture());

        // now simulate the add of target entry
        String parentDN = "cn=test parents,cn=config";
        when(configRepository.hasEntry(DN.valueOf(parentDN))).thenReturn(true);
        registered.getValue().applyConfigurationAdd(getTestParentEntry());

        // check that listener is added for target entry and deleted for its
        // parent entry
        ConfigAddListenerAdaptor listener =
            (ConfigAddListenerAdaptor<?>) registered.getValue().getDelayedAddListener();
        verify(configRepository).registerAddListener(DN.valueOf(parentDN), listener);
        verify(configRepository).deregisterAddListener(ROOT_CONFIG_DN, registered.getValue());
    }

    @Test
    public void testRegisterAddListenerWithOptionalRelation() throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();

        root.registerAddListener(TestCfg.getTestOneToZeroOrOneParentRelationDefinition(),
            mock(ConfigurationAddListener.class));

        verify(configRepository).registerAddListener(eq(ROOT_CONFIG_DN), isA(ConfigAddListener.class));
    }

    @Test
    public void testRegisterDeleteListenerWithInstantiableRelationImmediate() throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN, TEST_PARENTS_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();

        root.registerDeleteListener(TestCfg.getTestOneToManyParentRelationDefinition(),
            mock(ConfigurationDeleteListener.class));

        verify(configRepository).registerDeleteListener(eq(TEST_PARENTS_DN), isA(ConfigDeleteListener.class));
    }

    @Test
    public void testRegisterDeleteListenerWithInstantiableRelationDelayed() throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();

        ConfigurationDeleteListener<TestParentCfg> parentListener = mock(ConfigurationDeleteListener.class);
        root.registerDeleteListener(TestCfg.getTestOneToManyParentRelationDefinition(), parentListener);

        ArgumentCaptor<DelayedConfigAddListener> argument = ArgumentCaptor.forClass(DelayedConfigAddListener.class);
        verify(configRepository).registerAddListener(eq(ROOT_CONFIG_DN), argument.capture());
        // check that actual listener is the one provided to the root
        ConfigurationDeleteListener actualListener =
            ((ServerManagedObjectDeleteListenerAdaptor)
                ((ConfigDeleteListenerAdaptor) argument.getValue().getDelayedDeleteListener()).
                    getServerManagedObjectDeleteListener()).getConfigurationDeleteListener();
        assertThat(actualListener).isEqualTo(parentListener);
    }

    @Test
    public void testRegisterDeleteListenerWithOptionalRelation() throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();

        root.registerDeleteListener(TestCfg.getTestOneToZeroOrOneParentRelationDefinition(),
            mock(ConfigurationDeleteListener.class));

        verify(configRepository).registerDeleteListener(eq(ROOT_CONFIG_DN), isA(ConfigDeleteListener.class));
    }

    @Test
    public void testRegisterChangeListener() throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();
        root.setConfigDN(ROOT_CONFIG_DN);

        root.registerChangeListener(mock(ConfigurationChangeListener.class));

        verify(configRepository).registerChangeListener(eq(ROOT_CONFIG_DN), isA(ConfigChangeListener.class));
    }

    @Test
    public void testDeregisterAddListenerWithInstantiableRelationImmediate() throws Exception {
        // arrange
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN, TEST_PARENTS_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();

        ConfigurationAddListener<TestParentCfg> parentListener = mock(ConfigurationAddListener.class);
        ConfigAddListener registeredListener =
            registerAddListenerForTestParent(configRepository, root, parentListener);
        when(configRepository.getAddListeners(TEST_PARENTS_DN)).thenReturn(Arrays.asList(registeredListener));

        // act
        root.deregisterAddListener(TestCfg.getTestOneToManyParentRelationDefinition(), parentListener);

        // assert
        verify(configRepository).deregisterAddListener(eq(TEST_PARENTS_DN), same(registeredListener));
    }

    @Test
    public void testDeregisterAddListenerWithInstantiableRelationDelayed() throws Exception {
        // arrange
        ConfigurationRepository configRepository = createConfigRepositoryWithDNs(ROOT_CONFIG_DN);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        ServerManagedObject<RootCfg> root = context.getRootConfigurationManagedObject();

        ConfigurationAddListener<TestParentCfg> parentListener = mock(ConfigurationAddListener.class);
        ConfigAddListener registeredListener =
            registerAddListenerForTestParentDelayed(configRepository, root, parentListener);
        when(configRepository.getAddListeners(ROOT_CONFIG_DN)).thenReturn(Arrays.asList(registeredListener));

        // act
        root.deregisterAddListener(TestCfg.getTestOneToManyParentRelationDefinition(), parentListener);

        // assert
        verify(configRepository).deregisterAddListener(eq(ROOT_CONFIG_DN), same(registeredListener));
    }

}
