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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.TestCaseUtils.failWasExpected;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.AdminTestCase;
import org.forgerock.opendj.config.TestCfg;
import org.forgerock.opendj.config.TestChildCfg;
import org.forgerock.opendj.config.TestChildCfgDefn;
import org.forgerock.opendj.config.TestParentCfg;
import org.forgerock.opendj.config.server.spi.ConfigAddListener;
import org.forgerock.opendj.config.server.spi.ConfigChangeListener;
import org.forgerock.opendj.config.server.spi.ConfigDeleteListener;
import org.forgerock.opendj.config.server.spi.ConfigurationRepository;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldif.LDIF;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Test cases for constraints on the server-side. */
@SuppressWarnings("javadoc")
public final class ConstraintTest extends AdminTestCase {

    private static class AddListener implements ConfigurationAddListener<TestChildCfg> {

        @Override
        public ConfigChangeResult applyConfigurationAdd(TestChildCfg configuration) {
            return new ConfigChangeResult();
        }

        @Override
        public boolean isConfigurationAddAcceptable(TestChildCfg configuration,
            List<LocalizableMessage> unacceptableReasons) {
            return true;
        }
    }

    private static class DeleteListener implements ConfigurationDeleteListener<TestChildCfg> {

        @Override
        public ConfigChangeResult applyConfigurationDelete(TestChildCfg configuration) {
            return new ConfigChangeResult();
        }

        @Override
        public boolean isConfigurationDeleteAcceptable(TestChildCfg configuration,
            List<LocalizableMessage> unacceptableReasons) {
            return true;
        }

    }

    private static class ChangeListener implements ConfigurationChangeListener<TestChildCfg> {

        @Override
        public ConfigChangeResult applyConfigurationChange(TestChildCfg configuration) {
            return new ConfigChangeResult();
        }

        @Override
        public boolean isConfigurationChangeAcceptable(TestChildCfg configuration,
            List<LocalizableMessage> unacceptableReasons) {
            return true;
        }

    }

    // @Checkstyle:off
    private static final Entry TEST_CHILD_1 = LDIF.makeEntry(
        "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real");

    private static final Entry TEST_BASE_CHILD = LDIF.makeEntry(
        "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test children");

    /** Parent 1 - uses default values for optional-multi-valued-dn-property. */
    private static final Entry TEST_PARENT_1 = LDIF.makeEntry(
        "dn: cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-parent-dummy",
        "cn: test parent 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real");
    // @Checkstyle:on

    @BeforeClass
    public void setUp() throws Exception {
        TestCfg.setUp();
    }

    @AfterClass
    public void tearDown() throws Exception {
        TestCfg.cleanup();
    }

    /** Success just ensure there is no exception raised. */
    @Test
    public void testGetManagedObjectSuccess() throws Exception {
        // arrange
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_BASE_CHILD, TEST_CHILD_1);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        MockConstraint constraint = new MockConstraint(true, false, configRepository);
        try {
            TestCfg.addConstraint(constraint);
            TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);

            // act
            parentCfg.getTestChild(entryName(TEST_CHILD_1));
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    @Test
    public void testGetManagedObjectFail() throws Exception {
        // arrange
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_BASE_CHILD, TEST_CHILD_1);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        MockConstraint constraint = new MockConstraint(false, true, configRepository);
        try {
            TestCfg.addConstraint(constraint);
            TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);

            // act
            parentCfg.getTestChild(entryName(TEST_CHILD_1));

            failWasExpected(ConfigException.class);
        } catch (ConfigException e) {
            // assert
            Throwable cause = e.getCause();
            assertThat(e.getCause()).isNotNull().isInstanceOf(ConstraintViolationException.class);
            ConstraintViolationException cve = (ConstraintViolationException) cause;
            assertThat(cve.getMessages().size()).isEqualTo(1);
            assertThat(cve.getManagedObject().getManagedObjectDefinition()).isSameAs(TestChildCfgDefn.getInstance());
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    @DataProvider
    Object[][] constraintValues() {
        return new Object[][] {
            // value of constraint used
            { true }, // success
            { false } // failure
        };
    }

    @Test(dataProvider = "constraintValues")
    public void testAddConstraint(boolean isUsableConstraint) throws Exception {
        // arrange
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_BASE_CHILD, TEST_CHILD_1);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);
        parentCfg.addTestChildAddListener(new AddListener());
        MockConstraint constraint = new MockConstraint(isUsableConstraint, false, configRepository);
        try {
            TestCfg.addConstraint(constraint);

            // act
            boolean isAcceptable = simulateEntryAdd(TEST_CHILD_1, configRepository);

            // assert : success depends on constraint used
            assertThat(isAcceptable).isEqualTo(isUsableConstraint);
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    @Test(dataProvider = "constraintValues")
    public void testDeleteConstraint(boolean isDeleteAllowedConstraint) throws Exception {
        // arrange
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_BASE_CHILD, TEST_CHILD_1);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);
        parentCfg.addTestChildDeleteListener(new DeleteListener());
        MockConstraint constraint = new MockConstraint(false, isDeleteAllowedConstraint, configRepository);
        try {
            TestCfg.addConstraint(constraint);

            // act
            boolean isAcceptable = simulateEntryDelete(TEST_CHILD_1, configRepository);

            // assert : success depends on constraint used
            assertThat(isAcceptable).isEqualTo(isDeleteAllowedConstraint);
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    @Test(dataProvider = "constraintValues")
    public void testChangeConstraint(boolean isUsableConstraint) throws Exception {
        // arrange
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_BASE_CHILD, TEST_CHILD_1);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        MockConstraint constraint = new MockConstraint(isUsableConstraint, false, configRepository);
        TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);
        TestChildCfg childCfg = parentCfg.getTestChild(entryName(TEST_CHILD_1));

        try {
            TestCfg.addConstraint(constraint);
            childCfg.addChangeListener(new ChangeListener());

            // act
            // It is not an issue to use the same child entry here
            // as we're only interested in constraint checking
            boolean isAcceptable = simulateEntryChange(TEST_CHILD_1, configRepository);

            // assert : success depends on constraint used
            assertThat(isAcceptable).isEqualTo(isUsableConstraint);
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    /**
     * Simulate an entry add by triggering configAddIsAcceptable method of last
     * registered add listener.
     *
     * @return true if add is acceptable, false otherwise.
     */
    private boolean simulateEntryAdd(Entry entry, ConfigurationRepository configRepository) throws IOException {
        // use argument capture to retrieve the actual listener
        ArgumentCaptor<ConfigAddListener> registeredListener = ArgumentCaptor.forClass(ConfigAddListener.class);
        verify(configRepository).registerAddListener(eq(entry.getName().parent()), registeredListener.capture());

        return registeredListener.getValue().configAddIsAcceptable(entry, new LocalizableMessageBuilder());
    }

    /**
     * Simulate an entry delete by triggering configDeleteIsAcceptable method of
     * last registered add listener.
     *
     * @return true if delete is acceptable, false otherwise.
     */
    private boolean simulateEntryDelete(Entry entry, ConfigurationRepository configRepository) throws IOException {
        // use argument capture to retrieve the actual listener
        ArgumentCaptor<ConfigDeleteListener> registeredListener = ArgumentCaptor.forClass(ConfigDeleteListener.class);
        verify(configRepository).registerDeleteListener(eq(entry.getName().parent()), registeredListener.capture());

        return registeredListener.getValue().configDeleteIsAcceptable(entry, new LocalizableMessageBuilder());
    }

    /**
     * Simulate an entry change by triggering configChangeIsAcceptable method on
     * last registered change listener.
     *
     * @return true if change is acceptable, false otherwise.
     */
    private boolean simulateEntryChange(Entry newEntry, ConfigurationRepository configRepository) {
        // use argument capture to retrieve the actual listener
        ArgumentCaptor<ConfigChangeListener> registeredListener = ArgumentCaptor.forClass(ConfigChangeListener.class);
        verify(configRepository).registerChangeListener(eq(newEntry.getName()), registeredListener.capture());

        return registeredListener.getValue().configChangeIsAcceptable(newEntry, new LocalizableMessageBuilder());
    }

}
