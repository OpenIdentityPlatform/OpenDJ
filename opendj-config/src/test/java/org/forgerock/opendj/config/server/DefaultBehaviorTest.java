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
import static org.forgerock.opendj.ldif.LDIF.makeEntry;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.AdminTestCase;
import org.forgerock.opendj.config.TestCfg;
import org.forgerock.opendj.config.TestChildCfg;
import org.forgerock.opendj.config.TestParentCfg;
import org.forgerock.opendj.config.server.spi.ConfigAddListener;
import org.forgerock.opendj.config.server.spi.ConfigChangeListener;
import org.forgerock.opendj.config.server.spi.ConfigurationRepository;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.mockito.ArgumentCaptor;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
/** Test default behavior on the server side, by checking values of configuration objects. */
public final class DefaultBehaviorTest extends AdminTestCase {

    private static class TestConfigurationAddListener implements ConfigurationAddListener<TestChildCfg> {

        private TestChildCfg childCfg;

        @Override
        public ConfigChangeResult applyConfigurationAdd(TestChildCfg configuration) {
            return new ConfigChangeResult();
        }

        /** Gets the child configuration checking that it has the expected name. */
        public TestChildCfg getChildCfg(String expectedName) {
            Assert.assertNotNull(childCfg);
            Assert.assertEquals(childCfg.dn().rdn().getFirstAVA().getAttributeValue().toString(), expectedName);
            return childCfg;
        }

        @Override
        public boolean isConfigurationAddAcceptable(TestChildCfg configuration,
            List<LocalizableMessage> unacceptableReasons) {
            childCfg = configuration;
            return true;
        }
    }

    private static class TestConfigurationChangeListener implements ConfigurationChangeListener<TestChildCfg> {

        private TestChildCfg childCfg;

        @Override
        public ConfigChangeResult applyConfigurationChange(TestChildCfg configuration) {
            return new ConfigChangeResult();
        }

        /** Gets the child configuration checking that it has the expected name. */
        public TestChildCfg getChildCfg(String expectedName) {
            Assert.assertNotNull(childCfg);
            Assert.assertEquals(childCfg.dn().rdn().getFirstAVA().getAttributeValue().toString(), expectedName);
            return childCfg;
        }

        @Override
        public boolean isConfigurationChangeAcceptable(TestChildCfg configuration,
            List<LocalizableMessage> unacceptableReasons) {
            childCfg = configuration;
            return true;
        }
    }

    // @Checkstyle:off
    static final Entry CONFIG = makeEntry(
        "dn: cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-root-config",
        "cn: config");

    static final Entry TEST_PARENTS = makeEntry(
        "dn: cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test parents");

    /** Parent 1 - uses default values for optional-multi-valued-dn-property. */
    static final List<String> LDIF_TEST_PARENT_1 = Arrays.asList(
        "dn: cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-parent-dummy",
        "cn: test parent 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real");

    static final Entry TEST_PARENT_1 = makeEntry(LDIF_TEST_PARENT_1);

    /** Parent 2 - overrides default values for optional-multi-valued-dn-property. */
    static final Entry TEST_PARENT_2 = makeEntry(
        "dn: cn=test parent 2,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-parent-dummy",
        "cn: test parent 2",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real",
        "ds-cfg-base-dn: dc=default value p2v1,dc=com",
        "ds-cfg-base-dn: dc=default value p2v2,dc=com");

    static final Entry TEST_CHILD_BASE_1 = makeEntry(
        "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test children");

    static final Entry TEST_CHILD_BASE_2 = makeEntry(
        "dn:cn=test children,cn=test parent 2,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test children");

    private static final List<String> LDIF_TEST_CHILD_1 = Arrays.asList(
        "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real");

    private static final Entry TEST_CHILD_1 = makeEntry(LDIF_TEST_CHILD_1);

    private static final List<String> NEW_ATTRS_1 = Arrays.asList(
        "ds-cfg-base-dn: dc=new value 1,dc=com",
        "ds-cfg-base-dn: dc=new value 2,dc=com",
        "ds-cfg-group-dn: dc=new value 3,dc=com",
        "ds-cfg-group-dn: dc=new value 4,dc=com");

    private static final List<String> NEW_ATTRS_2 = Arrays.asList(
        "ds-cfg-base-dn: dc=new value 1,dc=com",
        "ds-cfg-base-dn: dc=new value 2,dc=com");

    private static final List<String> NEW_ATTRS_3 = Arrays.asList(
        "ds-cfg-group-dn: dc=new value 1,dc=com",
        "ds-cfg-group-dn: dc=new value 2,dc=com");

    private static final Entry TEST_CHILD_2 = makeEntry(
        "dn: cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 2",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real",
        "ds-cfg-base-dn: dc=default value c2v1,dc=com",
        "ds-cfg-base-dn: dc=default value c2v2,dc=com");

    private static final Entry TEST_CHILD_3 = makeEntry(
        "dn: cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 3",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-conflict-behavior: virtual-overrides-real",
        "ds-cfg-base-dn: dc=default value c3v1,dc=com",
        "ds-cfg-base-dn: dc=default value c3v2,dc=com",
        "ds-cfg-group-dn: dc=default value c3v3,dc=com",
        "ds-cfg-group-dn: dc=default value c3v4,dc=com");

    private static final Entry TEST_CHILD_4 = makeEntry(
        "dn: cn=test child 4,cn=test children,cn=test parent 2,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 4",
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

    @DataProvider
    Object[][] childConfigurationsValues() {
        return new Object[][] {
            // parent entry, child base entry, child entry,
            // expected first dn property values,
            // expected second dn property values
            { TEST_PARENT_1, TEST_CHILD_BASE_1, TEST_CHILD_1,
                Arrays.asList("dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com"),
                Arrays.asList("dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com") },

            { TEST_PARENT_1, TEST_CHILD_BASE_1, TEST_CHILD_2,
                Arrays.asList("dc=default value c2v1,dc=com", "dc=default value c2v2,dc=com"),
                Arrays.asList("dc=default value c2v1,dc=com", "dc=default value c2v2,dc=com") },

            { TEST_PARENT_1, TEST_CHILD_BASE_1, TEST_CHILD_3,
                Arrays.asList("dc=default value c3v1,dc=com", "dc=default value c3v2,dc=com"),
                Arrays.asList("dc=default value c3v3,dc=com", "dc=default value c3v4,dc=com") },

            { TEST_PARENT_2, TEST_CHILD_BASE_2, TEST_CHILD_4,
                Arrays.asList("dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com"),
                Arrays.asList("dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com") } };
    }

    /** Test that a child config have correct values when accessed from its parent config. */
    @Test(dataProvider = "childConfigurationsValues")
    public void testChildValues(Entry testParent, Entry testBaseChild, Entry testChild,
        List<String> valuesForOptionalDNProperty1, List<String> valuesForOptionalDNProperty2) throws Exception {
        // arrange
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(testParent, testBaseChild, testChild);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(testParent, context);

        // assert
        assertChildHasCorrectValues(parentCfg.getTestChild(entryName(testChild)), valuesForOptionalDNProperty1,
            valuesForOptionalDNProperty2);
    }

    /** Test that a child config have correct values when accessed through an add listener. */
    @Test(dataProvider = "childConfigurationsValues")
    public void testAddListenerChildValues(Entry testParent, Entry testBaseChild, Entry testChild,
        List<String> valuesForOptionalDNProperty1, List<String> valuesForOptionalDNProperty2) throws Exception {
        // arrange
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(testParent, testBaseChild, testChild);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(testParent, context);
        TestConfigurationAddListener addListener = new TestConfigurationAddListener();
        parentCfg.addTestChildAddListener(addListener);

        // act
        simulateEntryAdd(testChild, configRepository);

        // assert
        assertChildHasCorrectValues(addListener.getChildCfg(entryName(testChild)), valuesForOptionalDNProperty1,
            valuesForOptionalDNProperty2);
    }

    @DataProvider
    Object[][] childConfigurationsValuesForChangeListener() {
        return new Object[][] {
            // new entry after change, expected first dn property values,
            // expected second dn property values
            { makeEntryFrom(LDIF_TEST_CHILD_1, NEW_ATTRS_1),
                Arrays.asList("dc=new value 1,dc=com", "dc=new value 2,dc=com"),
                Arrays.asList("dc=new value 3,dc=com", "dc=new value 4,dc=com") },

            { makeEntryFrom(LDIF_TEST_CHILD_1, NEW_ATTRS_2),
                Arrays.asList("dc=new value 1,dc=com", "dc=new value 2,dc=com"),
                Arrays.asList("dc=new value 1,dc=com", "dc=new value 2,dc=com") },

            { makeEntryFrom(LDIF_TEST_CHILD_1, NEW_ATTRS_3),
                Arrays.asList("dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com"),
                Arrays.asList("dc=new value 1,dc=com", "dc=new value 2,dc=com") },

            { makeEntryFrom(LDIF_TEST_PARENT_1, NEW_ATTRS_2),
                Arrays.asList("dc=new value 1,dc=com", "dc=new value 2,dc=com"),
                Arrays.asList("dc=new value 1,dc=com", "dc=new value 2,dc=com") } };
    }

    /**
     * Tests that a child config have correct values when accessed through an
     * change listener. The defaulted properties are replaced with some real
     * values.
     */
    @Test(dataProvider = "childConfigurationsValuesForChangeListener")
    public void testChangeListenerChildValues(Entry newEntry, List<String> valuesForOptionalDNProperty1,
        List<String> valuesForOptionalDNProperty2) throws Exception {
        // arrange
        ConfigurationRepository configRepository =
            createConfigRepositoryWithEntries(TEST_PARENT_1, TEST_CHILD_BASE_1, TEST_CHILD_1);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parentCfg = getParentCfg(TEST_PARENT_1, context);
        TestChildCfg childCfg = parentCfg.getTestChild(entryName(TEST_CHILD_1));
        TestConfigurationChangeListener changeListener = new TestConfigurationChangeListener();
        childCfg.addChangeListener(changeListener);

        // act
        simulateEntryChange(newEntry, configRepository);

        // assert
        assertChildHasCorrectValues(changeListener.getChildCfg(entryName(TEST_CHILD_1)),
            valuesForOptionalDNProperty1, valuesForOptionalDNProperty2);
    }

    @DataProvider
    Object[][] parentConfigurationsValues() {
        return new Object[][] {
            // parent entry, expected first dn property values, expected second
            // dn property values
            { TEST_PARENT_1, Arrays.asList("dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com") },
            { TEST_PARENT_2, Arrays.asList("dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com") } };
    }

    /** Tests that parent configuration has correct values. */
    @Test(dataProvider = "parentConfigurationsValues")
    public void testParentValues(Entry parentEntry, List<String> valuesForOptionalDNProperty) throws Exception {
        ConfigurationRepository configRepository = createConfigRepositoryWithEntries(parentEntry);
        ServerManagementContext context =
            new ServerManagementContext(configRepository);
        TestParentCfg parent = getParentCfg(parentEntry, context);

        assertThat(parent.getMandatoryClassProperty()).isEqualTo(
            "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        assertThat(parent.getMandatoryReadOnlyAttributeTypeProperty()).isEqualTo(
            Schema.getDefaultSchema().getAttributeType("description"));
        assertDNSetEquals(parent.getOptionalMultiValuedDNProperty(), valuesForOptionalDNProperty);
    }

    /**
     * Simulate an entry add by triggering configAddIsAcceptable method of last
     * registered add listener.
     */
    private void simulateEntryAdd(Entry entry, ConfigurationRepository configRepository) throws IOException {
        // use argument capture to retrieve the actual listener
        ArgumentCaptor<ConfigAddListener> registeredListener = ArgumentCaptor.forClass(ConfigAddListener.class);
        verify(configRepository).registerAddListener(eq(entry.getName().parent()), registeredListener.capture());

        registeredListener.getValue().configAddIsAcceptable(entry, new LocalizableMessageBuilder());
    }

    /**
     * Simulate an entry change by triggering configChangeIsAcceptable method on
     * last registered change listener.
     */
    private void simulateEntryChange(Entry newEntry, ConfigurationRepository configRepository) {
        // use argument capture to retrieve the actual listener
        ArgumentCaptor<ConfigChangeListener> registeredListener = ArgumentCaptor.forClass(ConfigChangeListener.class);
        verify(configRepository).registerChangeListener(eq(newEntry.getName()), registeredListener.capture());

        registeredListener.getValue().configChangeIsAcceptable(newEntry, new LocalizableMessageBuilder());
    }

    private void assertChildHasCorrectValues(TestChildCfg child, List<String> dnProperty1, List<String> dnProperty2) {
        assertThat(child.getMandatoryClassProperty()).isEqualTo(
            "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        assertThat(child.getMandatoryReadOnlyAttributeTypeProperty()).isEqualTo(
            Schema.getDefaultSchema().getAttributeType("description"));
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(), dnProperty1);
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(), dnProperty2);
    }

    /** Asserts that the actual set of DNs contains the expected values. */
    private void assertDNSetEquals(SortedSet<DN> actualDNs, List<String> expectedDNs) {
        String[] actualStrings = new String[actualDNs.size()];
        int i = 0;
        for (DN dn : actualDNs) {
            actualStrings[i] = dn.toString();
            i++;
        }
        assertThat(actualStrings).containsOnly(expectedDNs.toArray(new Object[expectedDNs.size()]));
    }

    /** Make an entry by combining two lists. */
    static Entry makeEntryFrom(List<String> base, List<String> attrs) {
        List<String> ldif = new ArrayList<>(base);
        ldif.addAll(attrs);
        return makeEntry(ldif.toArray(new String[0]));
    }
}
