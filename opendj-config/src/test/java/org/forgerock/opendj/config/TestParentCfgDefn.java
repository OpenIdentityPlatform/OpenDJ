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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import java.util.Collection;
import java.util.SortedSet;

import org.forgerock.opendj.config.client.ConcurrentModificationException;
import org.forgerock.opendj.config.client.IllegalManagedObjectNameException;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.MissingMandatoryPropertiesException;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.schema.AttributeType;

/**
 * An interface for querying the Test Parent managed object definition meta
 * information.
 * <p>
 * A configuration for testing components that have child components. It re-uses
 * the virtual-attribute configuration LDAP profile.
 */
public final class TestParentCfgDefn extends ManagedObjectDefinition<TestParentCfgClient, TestParentCfg> {

    /** The singleton configuration definition instance. */
    private static final TestParentCfgDefn INSTANCE = new TestParentCfgDefn();

    /** The "mandatory-boolean-property" property definition. */
    private static final BooleanPropertyDefinition PD_MANDATORY_BOOLEAN_PROPERTY;

    /** The "mandatory-class-property" property definition. */
    private static final ClassPropertyDefinition PD_MANDATORY_CLASS_PROPERTY;

    /** The "mandatory-read-only-attribute-type-property" property definition. */
    private static final AttributeTypePropertyDefinition PD_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY;

    /** The "optional-multi-valued-dn-property" property definition. */
    private static final DNPropertyDefinition PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY;

    /** The "test-children" relation definition. */
    private static final InstantiableRelationDefinition<TestChildCfgClient, TestChildCfg> RD_TEST_CHILDREN;

    /** The "optional-test-child" relation definition. */
    private static final OptionalRelationDefinition<TestChildCfgClient, TestChildCfg> RD_OPTIONAL_TEST_CHILD;

    /** Build the "mandatory-boolean-property" property definition. */
    static {
        BooleanPropertyDefinition.Builder builder = BooleanPropertyDefinition.createBuilder(INSTANCE,
                "mandatory-boolean-property");
        builder.setOption(PropertyOption.MANDATORY);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE,
                "mandatory-boolean-property"));
        builder.setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<Boolean>());
        PD_MANDATORY_BOOLEAN_PROPERTY = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PD_MANDATORY_BOOLEAN_PROPERTY);
    }

    /** Build the "mandatory-class-property" property definition. */
    static {
        ClassPropertyDefinition.Builder builder = ClassPropertyDefinition.createBuilder(INSTANCE,
                "mandatory-class-property");
        builder.setOption(PropertyOption.MANDATORY);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.COMPONENT_RESTART, INSTANCE,
                "mandatory-class-property"));
        DefaultBehaviorProvider<String> provider = new DefinedDefaultBehaviorProvider<>(
                "org.opends.server.extensions.SomeVirtualAttributeProvider");
        builder.setDefaultBehaviorProvider(provider);
        builder.addInstanceOf("org.opends.server.api.VirtualAttributeProvider");
        PD_MANDATORY_CLASS_PROPERTY = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PD_MANDATORY_CLASS_PROPERTY);
    }

    /** Build the "mandatory-read-only-attribute-type-property" property definition. */
    static {
        AttributeTypePropertyDefinition.Builder builder = AttributeTypePropertyDefinition.createBuilder(INSTANCE,
                "mandatory-read-only-attribute-type-property");
        builder.setOption(PropertyOption.READ_ONLY);
        builder.setOption(PropertyOption.MANDATORY);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE,
                "mandatory-read-only-attribute-type-property"));
        builder.setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<AttributeType>());
        PD_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PD_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY);
    }

    /** Build the "optional-multi-valued-dn-property" property definition. */
    static {
        DNPropertyDefinition.Builder builder = DNPropertyDefinition.createBuilder(INSTANCE,
                "optional-multi-valued-dn-property");
        builder.setOption(PropertyOption.MULTI_VALUED);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE,
                "optional-multi-valued-dn-property"));
        DefaultBehaviorProvider<DN> provider = new DefinedDefaultBehaviorProvider<>("dc=domain1,dc=com",
                "dc=domain2,dc=com", "dc=domain3,dc=com");
        builder.setDefaultBehaviorProvider(provider);
        PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY);
    }

    /** Build the "test-children" relation definition. */
    static {
        InstantiableRelationDefinition.Builder<TestChildCfgClient, TestChildCfg> builder =
            new InstantiableRelationDefinition.Builder<>(
                INSTANCE, "multiple-children", "test-children", TestChildCfgDefn.getInstance());
        RD_TEST_CHILDREN = builder.getInstance();
        INSTANCE.registerRelationDefinition(RD_TEST_CHILDREN);
    }

    /** Build the "optional-test-child" relation definition. */
    static {
        OptionalRelationDefinition.Builder<TestChildCfgClient, TestChildCfg> builder =
            new OptionalRelationDefinition.Builder<>(
                INSTANCE, "optional-test-child", TestChildCfgDefn.getInstance());
        RD_OPTIONAL_TEST_CHILD = builder.getInstance();
        INSTANCE.registerRelationDefinition(RD_OPTIONAL_TEST_CHILD);
    }

    /**
     * Get the Test Parent configuration definition singleton.
     *
     * @return Returns the Test Parent configuration definition singleton.
     */
    public static TestParentCfgDefn getInstance() {
        return INSTANCE;
    }

    /** Private constructor. */
    private TestParentCfgDefn() {
        super("test-parent", null);
    }

    @Override
    public TestParentCfgClient createClientConfiguration(ManagedObject<? extends TestParentCfgClient> impl) {
        return new TestParentCfgClientImpl(impl);
    }

    @Override
    public TestParentCfg createServerConfiguration(ServerManagedObject<? extends TestParentCfg> impl) {
        return new TestParentCfgServerImpl(impl);
    }

    @Override
    public Class<TestParentCfg> getServerConfigurationClass() {
        return TestParentCfg.class;
    }

    /**
     * Get the "mandatory-boolean-property" property definition.
     * <p>
     * A mandatory boolean property.
     *
     * @return Returns the "mandatory-boolean-property" property definition.
     */
    public BooleanPropertyDefinition getMandatoryBooleanPropertyPropertyDefinition() {
        return PD_MANDATORY_BOOLEAN_PROPERTY;
    }

    /**
     * Get the "mandatory-class-property" property definition.
     * <p>
     * A mandatory Java-class property requiring a component restart.
     *
     * @return Returns the "mandatory-class-property" property definition.
     */
    public ClassPropertyDefinition getMandatoryClassPropertyPropertyDefinition() {
        return PD_MANDATORY_CLASS_PROPERTY;
    }

    /**
     * Get the "mandatory-read-only-attribute-type-property" property
     * definition.
     * <p>
     * A mandatory read-only attribute type property.
     *
     * @return Returns the "mandatory-read-only-attribute-type-property"
     *         property definition.
     */
    public AttributeTypePropertyDefinition getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition() {
        return PD_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY;
    }

    /**
     * Get the "optional-multi-valued-dn-property" property definition.
     * <p>
     * An optional multi-valued DN property with a defined default behavior.
     *
     * @return Returns the "optional-multi-valued-dn-property" property
     *         definition.
     */
    public DNPropertyDefinition getOptionalMultiValuedDNPropertyPropertyDefinition() {
        return PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY;
    }

    /**
     * Get the "test-children" relation definition.
     *
     * @return Returns the "test-children" relation definition.
     */
    public InstantiableRelationDefinition<TestChildCfgClient, TestChildCfg> getTestChildrenRelationDefinition() {
        return RD_TEST_CHILDREN;
    }

    /**
     * Get the "optional-test-child" relation definition.
     *
     * @return Returns the "optional-test-child" relation definition.
     */
    public OptionalRelationDefinition<TestChildCfgClient, TestChildCfg> getOptionalTestChildRelationDefinition() {
        return RD_OPTIONAL_TEST_CHILD;
    }

    /** Managed object client implementation. */
    private static class TestParentCfgClientImpl implements TestParentCfgClient {

        /** Private implementation. */
        private ManagedObject<? extends TestParentCfgClient> impl;

        /** Private constructor. */
        private TestParentCfgClientImpl(ManagedObject<? extends TestParentCfgClient> impl) {
            this.impl = impl;
        }

        @Override
        public Boolean isMandatoryBooleanProperty() {
            return impl.getPropertyValue(INSTANCE.getMandatoryBooleanPropertyPropertyDefinition());
        }

        @Override
        public void setMandatoryBooleanProperty(boolean value) {
            impl.setPropertyValue(INSTANCE.getMandatoryBooleanPropertyPropertyDefinition(), value);
        }

        @Override
        public String getMandatoryClassProperty() {
            return impl.getPropertyValue(INSTANCE.getMandatoryClassPropertyPropertyDefinition());
        }

        @Override
        public void setMandatoryClassProperty(String value) {
            impl.setPropertyValue(INSTANCE.getMandatoryClassPropertyPropertyDefinition(), value);
        }

        @Override
        public AttributeType getMandatoryReadOnlyAttributeTypeProperty() {
            return impl.getPropertyValue(INSTANCE.getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition());
        }

        @Override
        public void setMandatoryReadOnlyAttributeTypeProperty(AttributeType value) throws PropertyException {
            impl.setPropertyValue(INSTANCE.getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition(), value);
        }

        @Override
        public SortedSet<DN> getOptionalMultiValuedDNProperty() {
            return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNPropertyPropertyDefinition());
        }

        @Override
        public void setOptionalMultiValuedDNProperty(Collection<DN> values) {
            impl.setPropertyValues(INSTANCE.getOptionalMultiValuedDNPropertyPropertyDefinition(), values);
        }

        @Override
        public String[] listTestChildren() throws ConcurrentModificationException, LdapException {
            return impl.listChildren(INSTANCE.getTestChildrenRelationDefinition());
        }

        @Override
        public TestChildCfgClient getTestChild(String name) throws DefinitionDecodingException,
                ManagedObjectDecodingException, ManagedObjectNotFoundException, ConcurrentModificationException,
                LdapException {
            return impl.getChild(INSTANCE.getTestChildrenRelationDefinition(), name).getConfiguration();
        }

        @Override
        public <M extends TestChildCfgClient> M createTestChild(ManagedObjectDefinition<M, ? extends TestChildCfg> d,
                String name, Collection<PropertyException> exceptions) throws IllegalManagedObjectNameException {
            return impl.createChild(INSTANCE.getTestChildrenRelationDefinition(), d, name, exceptions)
                    .getConfiguration();
        }

        @Override
        public void removeTestChild(String name) throws ManagedObjectNotFoundException,
                ConcurrentModificationException, OperationRejectedException, LdapException {
            impl.removeChild(INSTANCE.getTestChildrenRelationDefinition(), name);
        }

        @Override
        public boolean hasOptionalTestChild() throws ConcurrentModificationException, LdapException {
            return impl.hasChild(INSTANCE.getOptionalTestChildRelationDefinition());
        }

        @Override
        public TestChildCfgClient getOptionalChild() throws DefinitionDecodingException,
                ManagedObjectDecodingException, ManagedObjectNotFoundException, ConcurrentModificationException,
                LdapException {
            return impl.getChild(INSTANCE.getOptionalTestChildRelationDefinition()).getConfiguration();
        }

        @Override
        public <M extends TestChildCfgClient> M createOptionalTestChild(
                ManagedObjectDefinition<M, ? extends TestChildCfg> d, Collection<PropertyException> exceptions) {
            return impl.createChild(INSTANCE.getOptionalTestChildRelationDefinition(), d, exceptions)
                    .getConfiguration();
        }

        @Override
        public void removeOptionalTestChild() throws ManagedObjectNotFoundException, ConcurrentModificationException,
                OperationRejectedException, LdapException {
            impl.removeChild(INSTANCE.getOptionalTestChildRelationDefinition());
        }

        @Override
        public ManagedObjectDefinition<? extends TestParentCfgClient, ? extends TestParentCfg> definition() {
            return INSTANCE;
        }

        @Override
        public PropertyProvider properties() {
            return impl;
        }

        @Override
        public void commit() throws ManagedObjectAlreadyExistsException, MissingMandatoryPropertiesException,
                ConcurrentModificationException, OperationRejectedException, LdapException {
            impl.commit();
        }

    }

    /** Managed object server implementation. */
    private static class TestParentCfgServerImpl implements TestParentCfg {

        /** Private implementation. */
        private ServerManagedObject<? extends TestParentCfg> impl;

        /** Private constructor. */
        private TestParentCfgServerImpl(ServerManagedObject<? extends TestParentCfg> impl) {
            this.impl = impl;
        }

        @Override
        public void addChangeListener(ConfigurationChangeListener<TestParentCfg> listener) {
            impl.registerChangeListener(listener);
        }

        @Override
        public void removeChangeListener(ConfigurationChangeListener<TestParentCfg> listener) {
            impl.deregisterChangeListener(listener);
        }

        @Override
        public boolean isMandatoryBooleanProperty() {
            return impl.getPropertyValue(INSTANCE.getMandatoryBooleanPropertyPropertyDefinition());
        }

        @Override
        public String getMandatoryClassProperty() {
            return impl.getPropertyValue(INSTANCE.getMandatoryClassPropertyPropertyDefinition());
        }

        @Override
        public AttributeType getMandatoryReadOnlyAttributeTypeProperty() {
            return impl.getPropertyValue(INSTANCE.getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition());
        }

        @Override
        public SortedSet<DN> getOptionalMultiValuedDNProperty() {
            return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNPropertyPropertyDefinition());
        }

        @Override
        public String[] listTestChildren() {
            return impl.listChildren(INSTANCE.getTestChildrenRelationDefinition());
        }

        @Override
        public TestChildCfg getTestChild(String name) throws ConfigException {
            return impl.getChild(INSTANCE.getTestChildrenRelationDefinition(), name).getConfiguration();
        }

        @Override
        public void addTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener) throws ConfigException {
            impl.registerAddListener(INSTANCE.getTestChildrenRelationDefinition(), listener);
        }

        @Override
        public void removeTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener) {
            impl.deregisterAddListener(INSTANCE.getTestChildrenRelationDefinition(), listener);
        }

        @Override
        public void addTestChildDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener)
                throws ConfigException {
            impl.registerDeleteListener(INSTANCE.getTestChildrenRelationDefinition(), listener);
        }

        @Override
        public void removeTestChildDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener) {
            impl.deregisterDeleteListener(INSTANCE.getTestChildrenRelationDefinition(), listener);
        }

        @Override
        public boolean hasOptionalTestChild() {
            return impl.hasChild(INSTANCE.getOptionalTestChildRelationDefinition());
        }

        @Override
        public TestChildCfg getOptionalTestChild() throws ConfigException {
            return impl.getChild(INSTANCE.getOptionalTestChildRelationDefinition()).getConfiguration();
        }

        @Override
        public void addOptionalTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener)
                throws ConfigException {
            impl.registerAddListener(INSTANCE.getOptionalTestChildRelationDefinition(), listener);
        }

        @Override
        public void removeOptionalTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener) {
            impl.deregisterAddListener(INSTANCE.getOptionalTestChildRelationDefinition(), listener);
        }

        @Override
        public void addOptionalChildTestDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener)
                throws ConfigException {
            impl.registerDeleteListener(INSTANCE.getOptionalTestChildRelationDefinition(), listener);
        }

        @Override
        public void removeOptionalTestChildDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener) {
            impl.deregisterDeleteListener(INSTANCE.getOptionalTestChildRelationDefinition(), listener);
        }

        @Override
        public Class<? extends TestParentCfg> configurationClass() {
            return TestParentCfg.class;
        }

        @Override
        public DN dn() {
            return impl.getDN();
        }

    }
}
