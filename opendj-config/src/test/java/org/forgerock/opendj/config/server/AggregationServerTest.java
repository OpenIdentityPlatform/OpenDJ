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
 * Portions copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldif.LDIF.makeEntry;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.AdminTestCase;
import org.forgerock.opendj.config.AdministratorAction;
import org.forgerock.opendj.config.AggregationPropertyDefinition;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.TestCfg;
import org.forgerock.opendj.config.TestChildCfg;
import org.forgerock.opendj.config.TestChildCfgDefn;
import org.forgerock.opendj.config.TestParentCfg;
import org.forgerock.opendj.config.UndefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.conditions.Conditions;
import org.forgerock.opendj.config.server.spi.ConfigChangeListener;
import org.forgerock.opendj.config.server.spi.ConfigDeleteListener;
import org.forgerock.opendj.config.server.spi.ConfigurationRepository;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.server.config.client.ConnectionHandlerCfgClient;
import org.forgerock.opendj.server.config.server.ConnectionHandlerCfg;
import org.mockito.ArgumentCaptor;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Test cases for aggregations on the server-side. */
@Test(singleThreaded = true)
@SuppressWarnings("javadoc")
public final class AggregationServerTest extends AdminTestCase {

    /** Dummy change listener for triggering change constraint call-backs. */
    private static final class DummyChangeListener implements ConfigurationChangeListener<TestChildCfg> {

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

    /** Dummy delete listener for triggering delete constraint call-backs. */
    private static final class DummyDeleteListener implements ConfigurationDeleteListener<TestChildCfg> {

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

    // @Checkstyle:off
    private static final Entry TEST_CHILD_1 = makeEntry(
        "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real");

    private static final Entry TEST_CHILD_2 = makeEntry(
        "dn: cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 2",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real",
        "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config");

    /** Has an invalid handler reference. */
    private static final Entry TEST_CHILD_3 = makeEntry(
        "dn: cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 3", "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real",
        "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=bad rdn, cn=config");

    private static final Entry TEST_CHILD_4 = makeEntry(
        "dn: cn=test child 4,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 4", "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real",
        "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config",
        "ds-cfg-rotation-policy: cn=LDAPS Connection Handler, cn=connection handlers, cn=config");

    private static final Entry TEST_CHILD_5 = makeEntry(
        "dn: cn=test child 5,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 5", "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real",
        "ds-cfg-rotation-policy: cn=BAD Connection Handler 1, cn=connection handlers, cn=config",
        "ds-cfg-rotation-policy: cn=BAD Connection Handler 2, cn=connection handlers, cn=config",
        "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config");

    private static final Entry TEST_CHILD_6 = makeEntry(
        "dn: cn=test child 6,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 6", "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real",
        "ds-cfg-rotation-policy: cn=Test Connection Handler, cn=connection handlers, cn=config");

    private static final Entry TEST_CHILD_7 = makeEntry(
        "dn: cn=test child 7,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 7", "ds-cfg-enabled: false",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real",
        "ds-cfg-rotation-policy: cn=Test Connection Handler, cn=connection handlers, cn=config");

    static final Entry TEST_PARENTS = makeEntry(
        "dn: cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test parents");

    static final Entry TEST_PARENT_1 = makeEntry(
        "dn: cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-parent-dummy",
        "cn: test parent 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real");

    private static final Entry TEST_BASE_CHILD = LDIF.makeEntry(
        "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test children");

    /** This handler is disabled - see ds-cfg-enabled property. */
    protected static final Entry TEST_CONNECTION_HANDLER_ENTRY_DISABLED = LDIF.makeEntry(
        "dn: cn=" + "Test Connection Handler" + ",cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-ldap-connection-handler",
        "cn: LDAP Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
        "ds-cfg-enabled: false",
        "ds-cfg-listen-address: 0.0.0.0", "ds-cfg-listen-port: 389");

    /** This handler is enabled - see ds-cfg-enabled property. */
    protected static final Entry TEST_CONNECTION_HANDLER_ENTRY_ENABLED = LDIF.makeEntry(
        "dn: cn=" + "Test Connection Handler" + ",cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-ldap-connection-handler",
        "cn: LDAP Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: 0.0.0.0", "ds-cfg-listen-port: 389");
    // @Checkstyle:on

    // @Checkstyle:off
    /** The default test child configuration "aggregation-property" property definition. */
    private AggregationPropertyDefinition<ConnectionHandlerCfgClient, ConnectionHandlerCfg>
        aggregationPropertyDefinitionDefault;

    /** An aggregation where the target must be enabled if the source is enabled. */
    private AggregationPropertyDefinition<ConnectionHandlerCfgClient, ConnectionHandlerCfg>
        aggregationPropertyDefinitionTargetAndSourceMustBeEnabled;

    /** An aggregation where the target must be enabled. */
    private AggregationPropertyDefinition<ConnectionHandlerCfgClient, ConnectionHandlerCfg>
        aggregationPropertyDefinitionTargetMustBeEnabled;
    // @Checkstyle:on

    @BeforeClass
    public void setUp() throws Exception {
        TestCfg.setUp();

        // Save the aggregation property definition so that it can be
        // replaced and restored later.
        aggregationPropertyDefinitionDefault =
            TestChildCfgDefn.getInstance().getAggregationPropertyPropertyDefinition();

        // Create the two test aggregation properties.
        AggregationPropertyDefinition.Builder<ConnectionHandlerCfgClient, ConnectionHandlerCfg> builder;
        TestChildCfgDefn d = TestChildCfgDefn.getInstance();
        builder = AggregationPropertyDefinition.createBuilder(d, "aggregation-property");
        builder.setOption(PropertyOption.MULTI_VALUED);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, d,
            "aggregation-property"));
        builder.setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<String>());
        builder.setParentPath("/");
        builder.setRelationDefinition("connection-handler");
        builder.setTargetIsEnabledCondition(Conditions.contains("enabled", "true"));
        aggregationPropertyDefinitionTargetMustBeEnabled = builder.getInstance();
        TestCfg.initializePropertyDefinition(aggregationPropertyDefinitionTargetMustBeEnabled);

        builder = AggregationPropertyDefinition.createBuilder(d, "aggregation-property");
        builder.setOption(PropertyOption.MULTI_VALUED);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, d,
            "aggregation-property"));
        builder.setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<String>());
        builder.setParentPath("/");
        builder.setRelationDefinition("connection-handler");
        builder.setTargetIsEnabledCondition(Conditions.contains("enabled", "true"));
        builder.setTargetNeedsEnablingCondition(Conditions.contains("mandatory-boolean-property", "true"));
        aggregationPropertyDefinitionTargetAndSourceMustBeEnabled = builder.getInstance();
        TestCfg.initializePropertyDefinition(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled);
    }

    @AfterClass
    public void tearDown() throws Exception {
        TestCfg.cleanup();

        // Restore the test child aggregation definition.
        TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);
    }

    /**
     * Tests that aggregation is rejected when the LDAP DN contains a valid RDN
     * but an invalid parent DN.
     */
    @Test
    public void testAggregationBadBaseDN() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_3, LDAP_CONN_HANDLER_ENTRY);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);
        try {
            parentCfg.getTestChild(entryName(TEST_CHILD_3));
            fail("Unexpectedly added test child 3 when it had a bad aggregation value");
        } catch (ConfigException e) {
            assertThat(e.getCause()).isNotNull().isInstanceOf(ServerManagedObjectDecodingException.class);
            ServerManagedObjectDecodingException de = (ServerManagedObjectDecodingException) e.getCause();
            assertThat(de.getCauses()).hasSize(1);
            PropertyException propertyException = de.getCauses().iterator().next();
            assertThat(propertyException).isInstanceOf(PropertyException.class);
            assertEquals(propertyException.getPropertyDefinition(), TestChildCfgDefn.getInstance()
                .getAggregationPropertyPropertyDefinition());
        }
    }

    /**
     * Tests that aggregation is rejected by a constraint violation when the DN
     * values are dangling.
     */
    @Test
    public void testAggregationDanglingReference() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_5, LDAP_CONN_HANDLER_ENTRY);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);

        try {
            parentCfg.getTestChild(entryName(TEST_CHILD_5));
            fail("Unexpectedly added test child 5 when it had a dangling reference");
        } catch (ConfigException e) {
            assertThat(e.getCause()).isNotNull().isInstanceOf(ConstraintViolationException.class);
            ConstraintViolationException cve = (ConstraintViolationException) e.getCause();
            assertThat(cve.getMessages()).isNotNull().hasSize(2);
        }
    }

    /**
     * Tests that aggregation is REJECTED by a constraint violation when an
     * enabled component references a disabled component and the referenced
     * component must always be enabled.
     */
    @Test
    public void testAggregationDisabledReference1() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_6, TEST_CONNECTION_HANDLER_ENTRY_DISABLED);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);

        registerAggregationDefinitionWithTargetEnabled();

        try {
            TestParentCfg parent = getParentCfg(TEST_PARENT_1, context);
            parent.getTestChild(entryName(TEST_CHILD_6));
            fail("Unexpectedly added test child 6 when it had a disabled reference");
        } catch (ConfigException e) {
            assertThat(e.getCause()).isNotNull().isInstanceOf(ConstraintViolationException.class);
            ConstraintViolationException cve = (ConstraintViolationException) e.getCause();
            assertThat(cve.getMessages()).isNotNull().hasSize(1);
        } finally {
            putBackDefaultAggregationDefinitionFromTargetEnabled();
        }
    }

    /**
     * Tests that aggregation is REJECTED by a constraint violation when a
     * disabled component references a disabled component and the referenced
     * component must always be enabled.
     */
    @Test
    public void testAggregationDisabledReference2() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_7, TEST_CONNECTION_HANDLER_ENTRY_DISABLED);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);

        registerAggregationDefinitionWithTargetEnabled();

        try {
            TestParentCfg parent = getParentCfg(TEST_PARENT_1, context);
            parent.getTestChild(entryName(TEST_CHILD_7));
            fail("Unexpectedly added test child 7 when it had a disabled reference");
        } catch (ConfigException e) {
            assertThat(e.getCause()).isNotNull().isInstanceOf(ConstraintViolationException.class);
            ConstraintViolationException cve = (ConstraintViolationException) e.getCause();
            assertThat(cve.getMessages()).isNotNull().hasSize(1);
        } finally {
            putBackDefaultAggregationDefinitionFromTargetEnabled();
        }
    }

    /**
     * Tests that aggregation is REJECTED by a constraint violation when an
     * enabled component references a disabled component and the referenced
     * component must always be enabled when the referencing component is
     * enabled.
     */
    @Test
    public void testAggregationDisabledReference3() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_6, TEST_CONNECTION_HANDLER_ENTRY_DISABLED);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);

        registerAggregationDefinitionWithTargetAndSourceEnabled();

        try {
            TestParentCfg parent = getParentCfg(TEST_PARENT_1, context);
            parent.getTestChild(entryName(TEST_CHILD_6));
            fail("Unexpectedly added test child 6 when it had a disabled reference");
        } catch (ConfigException e) {
            assertThat(e.getCause()).isNotNull().isInstanceOf(ConstraintViolationException.class);
            ConstraintViolationException cve = (ConstraintViolationException) e.getCause();
            assertThat(cve.getMessages()).isNotNull().hasSize(1);
        } finally {
            putBackDefaultAggregationDefinitionFromTargetAndSourceEnabled();
        }
    }

    /**
     * Tests that aggregation is ALLOWED when a disabled component references a
     * disabled component and the referenced component must always be enabled
     * when the referencing component is enabled.
     */
    @Test
    public void testAggregationDisabledReference4() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_7, TEST_CONNECTION_HANDLER_ENTRY_DISABLED);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);

        registerAggregationDefinitionWithTargetAndSourceEnabled();

        try {
            TestParentCfg parent = getParentCfg(TEST_PARENT_1, context);
            parent.getTestChild(entryName(TEST_CHILD_7));
        } finally {
            putBackDefaultAggregationDefinitionFromTargetAndSourceEnabled();
        }
    }

    /**
     * Tests that aggregation contains no values when it contains does not
     * contain any DN attribute values.
     */
    @Test
    public void testAggregationEmpty() throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_1);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);
        TestChildCfg testChildCfg = parentCfg.getTestChild(entryName(TEST_CHILD_1));

        assertEquals(testChildCfg.getMandatoryClassProperty(),
            "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        assertEquals(testChildCfg.getMandatoryReadOnlyAttributeTypeProperty(), Schema.getDefaultSchema()
            .getAttributeType("description"));
        assertSetEquals(testChildCfg.getAggregationProperty(), new String[0]);
    }

    /**
     * Tests that aggregation contains multiple valid values when it contains a
     * multiple valid DN attribute values.
     */
    @Test
    public void testAggregationMultipleValues() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_4, LDAP_CONN_HANDLER_ENTRY,
                LDAPS_CONN_HANDLER_ENTRY);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);
        TestChildCfg testChildCfg = parentCfg.getTestChild(entryName(TEST_CHILD_4));

        assertEquals(testChildCfg.getMandatoryClassProperty(),
            "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        assertEquals(testChildCfg.getMandatoryReadOnlyAttributeTypeProperty(), Schema.getDefaultSchema()
            .getAttributeType("description"));
        assertSetEquals(testChildCfg.getAggregationProperty(), "LDAPS Connection Handler", "LDAP Connection Handler");
    }

    /**
     * Tests that aggregation contains single valid value when it contains a
     * single valid DN attribute values.
     */
    @Test
    public void testAggregationSingle() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_2, LDAP_CONN_HANDLER_ENTRY);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);
        TestChildCfg testChildCfg = parentCfg.getTestChild(entryName(TEST_CHILD_2));

        assertEquals(testChildCfg.getMandatoryClassProperty(),
            "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        assertEquals(testChildCfg.getMandatoryReadOnlyAttributeTypeProperty(), Schema.getDefaultSchema()
            .getAttributeType("description"));

        // Test normalization.
        assertSetEquals(testChildCfg.getAggregationProperty(), "LDAP Connection Handler");
        assertSetEquals(testChildCfg.getAggregationProperty(), "  LDAP   Connection  Handler ");
        assertSetEquals(testChildCfg.getAggregationProperty(), "  ldap connection HANDLER ");
    }

    /**
     * Tests that it is impossible to delete a referenced component when the
     * referenced component must always exist regardless of whether the
     * referencing component is enabled or not.
     */
    @Test
    public void testCannotDeleteReferencedComponent() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENTS, TEST_PARENT_1, TEST_BASE_CHILD, TEST_CHILD_7,
                CONN_HANDLER_ENTRY, TEST_CONNECTION_HANDLER_ENTRY_ENABLED);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);

        registerAggregationDefinitionWithTargetEnabled();

        ConfigurationDeleteListener<TestChildCfg> deleteListener = new DummyDeleteListener();
        try {
            // Retrieve the parent and child managed objects and register
            // delete and change listeners respectively in order to trigger
            // the constraint call-backs.
            TestParentCfg parentCfg = getParentCfg(entryName(TEST_PARENT_1), context);
            parentCfg.addTestChildDeleteListener(deleteListener);

            ArgumentCaptor<ConfigDeleteListener> registeredListener =
                ArgumentCaptor.forClass(ConfigDeleteListener.class);
            verify(configRepository).registerDeleteListener(eq(TEST_BASE_CHILD.getName()),
                registeredListener.capture());

            // Now simulate the delete ofthe referenced connection handler.
            assertThat(
                registeredListener.getValue().configDeleteIsAcceptable(TEST_CONNECTION_HANDLER_ENTRY_ENABLED,
                    new LocalizableMessageBuilder())).isFalse();

        } finally {
            putBackDefaultAggregationDefinitionFromTargetEnabled();
        }
    }

    /**
     * Tests that it is impossible to disable a referenced component when the
     * referenced component must always be enabled regardless of whether the
     * referencing component is enabled or not.
     */
    @Test
    public void testCannotDisableReferencedComponent() throws Exception {
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENTS, TEST_PARENT_1, TEST_BASE_CHILD, TEST_CHILD_7,
                CONN_HANDLER_ENTRY, TEST_CONNECTION_HANDLER_ENTRY_ENABLED);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);

        registerAggregationDefinitionWithTargetEnabled();

        ConfigurationChangeListener<TestChildCfg> changeListener = new DummyChangeListener();
        try {
            // Retrieve the parent and child managed objects and register
            // delete and change listeners respectively in order to trigger
            // the constraint call-backs.
            TestParentCfg parentCfg = getParentCfg(entryName(TEST_PARENT_1), context);
            TestChildCfg testChildCfg = parentCfg.getTestChild(entryName(TEST_CHILD_7));
            testChildCfg.addChangeListener(changeListener);

            ArgumentCaptor<ConfigChangeListener> registeredListener =
                ArgumentCaptor.forClass(ConfigChangeListener.class);
            verify(configRepository).registerChangeListener(eq(TEST_CHILD_7.getName()), registeredListener.capture());

            // Now simulate the disabling ofthe referenced connection handler.
            assertThat(
                registeredListener.getValue().configChangeIsAcceptable(TEST_CONNECTION_HANDLER_ENTRY_DISABLED,
                    new LocalizableMessageBuilder())).isFalse();

        } finally {
            putBackDefaultAggregationDefinitionFromTargetEnabled();
        }
    }

    /**
     * Register the temporary aggregation definition to be used in test. You
     * must call
     * {@code putBackDefaultAggregationDefinitionFromTargetAndSourceEnabled}
     * method at end of test.
     */
    private void registerAggregationDefinitionWithTargetAndSourceEnabled() {
        TestCfg.removeConstraint(aggregationPropertyDefinitionDefault.getSourceConstraint());
        TestCfg.addPropertyDefinition(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled);
        TestCfg.addConstraint(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled.getSourceConstraint());
    }

    /** Put back the default aggregation definition. */
    private void putBackDefaultAggregationDefinitionFromTargetAndSourceEnabled() {
        TestCfg.removeConstraint(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled.getSourceConstraint());
        TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);
        TestCfg.addConstraint(aggregationPropertyDefinitionDefault.getSourceConstraint());
    }

    /**
     * Register the temporary aggregation definition to be used in test. You
     * must call {@code putBackDefaultAggregationDefinitionFromTargetEnabled}
     * method at end of test.
     */
    private void registerAggregationDefinitionWithTargetEnabled() {
        TestCfg.removeConstraint(aggregationPropertyDefinitionDefault.getSourceConstraint());
        TestCfg.addPropertyDefinition(aggregationPropertyDefinitionTargetMustBeEnabled);
        TestCfg.addConstraint(aggregationPropertyDefinitionTargetMustBeEnabled.getSourceConstraint());
    }

    /** Put back the default aggregation definition. */
    private void putBackDefaultAggregationDefinitionFromTargetEnabled() {
        TestCfg.removeConstraint(aggregationPropertyDefinitionTargetMustBeEnabled.getSourceConstraint());
        TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);
        TestCfg.addConstraint(aggregationPropertyDefinitionDefault.getSourceConstraint());
    }

    /** Asserts that the actual set of DNs contains the expected values. */
    private void assertSetEquals(SortedSet<String> actual, String... expected) {
        SortedSet<String> values =
            new TreeSet<>(TestChildCfgDefn.getInstance().getAggregationPropertyPropertyDefinition());
        if (expected != null) {
            for (String value : expected) {
                values.add(value);
            }
        }
        Assert.assertEquals((Object) actual, (Object) values);
    }

}
