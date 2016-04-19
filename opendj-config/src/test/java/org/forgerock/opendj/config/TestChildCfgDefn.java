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
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.MissingMandatoryPropertiesException;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.client.ConnectionHandlerCfgClient;
import org.forgerock.opendj.server.config.server.ConnectionHandlerCfg;

/**
 * An interface for querying the Test Child managed object definition meta
 * information.
 * <p>
 * A configuration for testing components that are subordinate to a parent
 * component. It re-uses the virtual-attribute configuration LDAP profile.
 */
public final class TestChildCfgDefn extends ManagedObjectDefinition<TestChildCfgClient, TestChildCfg> {

    /** The singleton configuration definition instance. */
    private static final TestChildCfgDefn INSTANCE = new TestChildCfgDefn();

    // @Checkstyle:off
    /** The "aggregation-property" property definition. */
    private static final AggregationPropertyDefinition<ConnectionHandlerCfgClient, ConnectionHandlerCfg>
        PROPDEF_AGGREGATION_PROPERTY;
    // @Checkstyle:on
    /** The "mandatory-boolean-property" property definition. */
    private static final BooleanPropertyDefinition PROPDEF_MANDATORY_BOOLEAN_PROPERTY;

    /** The "mandatory-class-property" property definition. */
    private static final ClassPropertyDefinition PROPDEF_MANDATORY_CLASS_PROPERTY;

    /** The "mandatory-read-only-attribute-type-property" property definition. */
    private static final AttributeTypePropertyDefinition PROPDEF_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY;

    /** The "optional-multi-valued-dn-property1" property definition. */
    private static final DNPropertyDefinition PROPDEF_OPTIONAL_MULTI_VALUED_DN_PROPERTY1;

    /** The "optional-multi-valued-dn-property2" property definition. */
    private static final DNPropertyDefinition PROPDEF_OPTIONAL_MULTI_VALUED_DN_PROPERTY2;

    /** Build the "aggregation-property" property definition. */
    static {
        AggregationPropertyDefinition.Builder<ConnectionHandlerCfgClient, ConnectionHandlerCfg> builder =
                AggregationPropertyDefinition.createBuilder(INSTANCE, "aggregation-property");
        builder.setOption(PropertyOption.MULTI_VALUED);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE,
                "aggregation-property"));
        builder.setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<String>());
        builder.setParentPath("/");
        builder.setRelationDefinition("connection-handler");
        PROPDEF_AGGREGATION_PROPERTY = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PROPDEF_AGGREGATION_PROPERTY);
        INSTANCE.registerConstraint(PROPDEF_AGGREGATION_PROPERTY.getSourceConstraint());
    }

    /** Build the "mandatory-boolean-property" property definition. */
    static {
        BooleanPropertyDefinition.Builder builder = BooleanPropertyDefinition.createBuilder(INSTANCE,
                "mandatory-boolean-property");
        builder.setOption(PropertyOption.MANDATORY);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE,
                "mandatory-boolean-property"));
        builder.setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<Boolean>());
        PROPDEF_MANDATORY_BOOLEAN_PROPERTY = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PROPDEF_MANDATORY_BOOLEAN_PROPERTY);
    }

    /** Build the "mandatory-class-property" property definition. */
    static {
        ClassPropertyDefinition.Builder builder = ClassPropertyDefinition.createBuilder(INSTANCE,
                "mandatory-class-property");
        builder.setOption(PropertyOption.MANDATORY);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.COMPONENT_RESTART, INSTANCE,
                "mandatory-class-property"));
        DefaultBehaviorProvider<String> provider = new DefinedDefaultBehaviorProvider<>(
                "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        builder.setDefaultBehaviorProvider(provider);
        builder.addInstanceOf("org.opends.server.api.VirtualAttributeProvider");
        PROPDEF_MANDATORY_CLASS_PROPERTY = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PROPDEF_MANDATORY_CLASS_PROPERTY);
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
        PROPDEF_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PROPDEF_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY);
    }

    /** Build the "optional-multi-valued-dn-property1" property definition. */
    static {
        DNPropertyDefinition.Builder builder = DNPropertyDefinition.createBuilder(INSTANCE,
                "optional-multi-valued-dn-property1");
        builder.setOption(PropertyOption.MULTI_VALUED);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE,
                "optional-multi-valued-dn-property1"));
        DefaultBehaviorProvider<DN> provider = new RelativeInheritedDefaultBehaviorProvider<>(
                TestParentCfgDefn.getInstance(), "optional-multi-valued-dn-property", 1);
        builder.setDefaultBehaviorProvider(provider);
        PROPDEF_OPTIONAL_MULTI_VALUED_DN_PROPERTY1 = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PROPDEF_OPTIONAL_MULTI_VALUED_DN_PROPERTY1);
    }

    /** Build the "optional-multi-valued-dn-property2" property definition. */
    static {
        DNPropertyDefinition.Builder builder = DNPropertyDefinition.createBuilder(INSTANCE,
                "optional-multi-valued-dn-property2");
        builder.setOption(PropertyOption.MULTI_VALUED);
        builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE,
                "optional-multi-valued-dn-property2"));
        DefaultBehaviorProvider<DN> provider = new RelativeInheritedDefaultBehaviorProvider<>(
                TestChildCfgDefn.getInstance(), "optional-multi-valued-dn-property1", 0);
        builder.setDefaultBehaviorProvider(provider);
        PROPDEF_OPTIONAL_MULTI_VALUED_DN_PROPERTY2 = builder.getInstance();
        INSTANCE.registerPropertyDefinition(PROPDEF_OPTIONAL_MULTI_VALUED_DN_PROPERTY2);
    }

    /**
     * Get the Test Child configuration definition singleton.
     *
     * @return Returns the Test Child configuration definition singleton.
     */
    public static TestChildCfgDefn getInstance() {
        return INSTANCE;
    }

    /** Private constructor. */
    private TestChildCfgDefn() {
        super("test-child", null);
    }

    @Override
    public TestChildCfgClient createClientConfiguration(ManagedObject<? extends TestChildCfgClient> impl) {
        return new TestChildCfgClientImpl(impl);
    }

    @Override
    public TestChildCfg createServerConfiguration(ServerManagedObject<? extends TestChildCfg> impl) {
        return new TestChildCfgServerImpl(impl);
    }

    @Override
    public Class<TestChildCfg> getServerConfigurationClass() {
        return TestChildCfg.class;
    }

    /**
     * Get the "aggregation-property" property definition.
     * <p>
     * An aggregation property which references connection handlers.
     *
     * @return Returns the "aggregation-property" property definition.
     */
    // @Checkstyle:off
    public AggregationPropertyDefinition<ConnectionHandlerCfgClient, ConnectionHandlerCfg>
        getAggregationPropertyPropertyDefinition() {
        return PROPDEF_AGGREGATION_PROPERTY;
    }
    // @Checkstyle:on

    /**
     * Get the "mandatory-boolean-property" property definition.
     * <p>
     * A mandatory boolean property.
     *
     * @return Returns the "mandatory-boolean-property" property definition.
     */
    public BooleanPropertyDefinition getMandatoryBooleanPropertyPropertyDefinition() {
        return PROPDEF_MANDATORY_BOOLEAN_PROPERTY;
    }

    /**
     * Get the "mandatory-class-property" property definition.
     * <p>
     * A mandatory Java-class property requiring a component restart.
     *
     * @return Returns the "mandatory-class-property" property definition.
     */
    public ClassPropertyDefinition getMandatoryClassPropertyPropertyDefinition() {
        return PROPDEF_MANDATORY_CLASS_PROPERTY;
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
        return PROPDEF_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY;
    }

    /**
     * Get the "optional-multi-valued-dn-property1" property definition.
     * <p>
     * An optional multi-valued DN property which inherits its values from
     * optional-multi-valued-dn-property in the parent.
     *
     * @return Returns the "optional-multi-valued-dn-property1" property
     *         definition.
     */
    public DNPropertyDefinition getOptionalMultiValuedDNProperty1PropertyDefinition() {
        return PROPDEF_OPTIONAL_MULTI_VALUED_DN_PROPERTY1;
    }

    /**
     * Get the "optional-multi-valued-dn-property2" property definition.
     * <p>
     * An optional multi-valued DN property which inherits its values from
     * optional-multi-valued-dn-property1.
     *
     * @return Returns the "optional-multi-valued-dn-property2" property
     *         definition.
     */
    public DNPropertyDefinition getOptionalMultiValuedDNProperty2PropertyDefinition() {
        return PROPDEF_OPTIONAL_MULTI_VALUED_DN_PROPERTY2;
    }

    /** Managed object client implementation. */
    private static class TestChildCfgClientImpl implements TestChildCfgClient {

        /** Private implementation. */
        private ManagedObject<? extends TestChildCfgClient> impl;

        /** Private constructor. */
        private TestChildCfgClientImpl(ManagedObject<? extends TestChildCfgClient> impl) {
            this.impl = impl;
        }

        @Override
        public SortedSet<String> getAggregationProperty() {
            return impl.getPropertyValues(INSTANCE.getAggregationPropertyPropertyDefinition());
        }

        @Override
        public void setAggregationProperty(Collection<String> values) {
            impl.setPropertyValues(INSTANCE.getAggregationPropertyPropertyDefinition(), values);
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
        public SortedSet<DN> getOptionalMultiValuedDNProperty1() {
            return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty1PropertyDefinition());
        }

        @Override
        public void setOptionalMultiValuedDNProperty1(Collection<DN> values) {
            impl.setPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty1PropertyDefinition(), values);
        }

        @Override
        public SortedSet<DN> getOptionalMultiValuedDNProperty2() {
            return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty2PropertyDefinition());
        }

        @Override
        public void setOptionalMultiValuedDNProperty2(Collection<DN> values) {
            impl.setPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty2PropertyDefinition(), values);
        }

        @Override
        public ManagedObjectDefinition<? extends TestChildCfgClient, ? extends TestChildCfg> definition() {
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
    private static class TestChildCfgServerImpl implements TestChildCfg {

        /** Private implementation. */
        private ServerManagedObject<? extends TestChildCfg> impl;

        /** Private constructor. */
        private TestChildCfgServerImpl(ServerManagedObject<? extends TestChildCfg> impl) {
            this.impl = impl;
        }

        @Override
        public void addChangeListener(ConfigurationChangeListener<TestChildCfg> listener) {
            impl.registerChangeListener(listener);
        }

        @Override
        public void removeChangeListener(ConfigurationChangeListener<TestChildCfg> listener) {
            impl.deregisterChangeListener(listener);
        }

        @Override
        public SortedSet<String> getAggregationProperty() {
            return impl.getPropertyValues(INSTANCE.getAggregationPropertyPropertyDefinition());
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
        public SortedSet<DN> getOptionalMultiValuedDNProperty1() {
            return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty1PropertyDefinition());
        }

        @Override
        public SortedSet<DN> getOptionalMultiValuedDNProperty2() {
            return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty2PropertyDefinition());
        }

        @Override
        public Class<? extends TestChildCfg> configurationClass() {
            return TestChildCfg.class;
        }

        @Override
        public DN dn() {
            return impl.getDN();
        }

    }
}
