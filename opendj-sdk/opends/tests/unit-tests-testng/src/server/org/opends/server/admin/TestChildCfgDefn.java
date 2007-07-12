/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import java.util.Collection;
import java.util.SortedSet;
import org.opends.server.admin.AdministratorAction;
import org.opends.server.admin.AttributeTypePropertyDefinition;
import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.DefaultBehaviorProvider;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.DNPropertyDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;



/**
 * An interface for querying the Test Child managed object definition
 * meta information.
 * <p>
 * A configuration for testing components that are subordinate to a
 * parent component. It re-uses the virtual-attribute configuration
 * LDAP profile.
 */
public final class TestChildCfgDefn extends ManagedObjectDefinition<TestChildCfgClient, TestChildCfg> {

  // The singleton configuration definition instance.
  private static final TestChildCfgDefn INSTANCE = new TestChildCfgDefn();



  // The "mandatory-boolean-property" property definition.
  private static final BooleanPropertyDefinition PD_MANDATORY_BOOLEAN_PROPERTY;



  // The "mandatory-class-property" property definition.
  private static final ClassPropertyDefinition PD_MANDATORY_CLASS_PROPERTY;



  // The "mandatory-read-only-attribute-type-property" property definition.
  private static final AttributeTypePropertyDefinition PD_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY;



  // The "optional-multi-valued-dn-property1" property definition.
  private static final DNPropertyDefinition PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY1;



  // The "optional-multi-valued-dn-property2" property definition.
  private static final DNPropertyDefinition PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY2;



  // Build the "mandatory-boolean-property" property definition.
  static {
      BooleanPropertyDefinition.Builder builder = BooleanPropertyDefinition.createBuilder(INSTANCE, "mandatory-boolean-property");
      builder.setOption(PropertyOption.MANDATORY);
      builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE, "mandatory-boolean-property"));
      builder.setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<Boolean>());
      PD_MANDATORY_BOOLEAN_PROPERTY = builder.getInstance();
      INSTANCE.registerPropertyDefinition(PD_MANDATORY_BOOLEAN_PROPERTY);
  }



  // Build the "mandatory-class-property" property definition.
  static {
      ClassPropertyDefinition.Builder builder = ClassPropertyDefinition.createBuilder(INSTANCE, "mandatory-class-property");
      builder.setOption(PropertyOption.MANDATORY);
      builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.COMPONENT_RESTART, INSTANCE, "mandatory-class-property"));
      DefaultBehaviorProvider<String> provider = new DefinedDefaultBehaviorProvider<String>("org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
      builder.setDefaultBehaviorProvider(provider);
      builder.addInstanceOf("org.opends.server.api.VirtualAttributeProvider");
      PD_MANDATORY_CLASS_PROPERTY = builder.getInstance();
      INSTANCE.registerPropertyDefinition(PD_MANDATORY_CLASS_PROPERTY);
  }



  // Build the "mandatory-read-only-attribute-type-property" property definition.
  static {
      AttributeTypePropertyDefinition.Builder builder = AttributeTypePropertyDefinition.createBuilder(INSTANCE, "mandatory-read-only-attribute-type-property");
      builder.setOption(PropertyOption.READ_ONLY);
      builder.setOption(PropertyOption.MANDATORY);
      builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE, "mandatory-read-only-attribute-type-property"));
      builder.setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<AttributeType>());
      PD_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY = builder.getInstance();
      INSTANCE.registerPropertyDefinition(PD_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY);
  }



  // Build the "optional-multi-valued-dn-property1" property definition.
  static {
      DNPropertyDefinition.Builder builder = DNPropertyDefinition.createBuilder(INSTANCE, "optional-multi-valued-dn-property1");
      builder.setOption(PropertyOption.MULTI_VALUED);
      builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE, "optional-multi-valued-dn-property1"));
      DefaultBehaviorProvider<DN> provider = new RelativeInheritedDefaultBehaviorProvider<DN>(TestParentCfgDefn.getInstance(), "optional-multi-valued-dn-property", 1);
      builder.setDefaultBehaviorProvider(provider);
      PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY1 = builder.getInstance();
      INSTANCE.registerPropertyDefinition(PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY1);
  }



  // Build the "optional-multi-valued-dn-property2" property definition.
  static {
      DNPropertyDefinition.Builder builder = DNPropertyDefinition.createBuilder(INSTANCE, "optional-multi-valued-dn-property2");
      builder.setOption(PropertyOption.MULTI_VALUED);
      builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE, "optional-multi-valued-dn-property2"));
      DefaultBehaviorProvider<DN> provider = new RelativeInheritedDefaultBehaviorProvider<DN>(TestChildCfgDefn.getInstance(), "optional-multi-valued-dn-property1", 0);
      builder.setDefaultBehaviorProvider(provider);
      PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY2 = builder.getInstance();
      INSTANCE.registerPropertyDefinition(PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY2);
  }



  /**
   * Get the Test Child configuration definition singleton.
   *
   * @return Returns the Test Child configuration definition
   *         singleton.
   */
  public static TestChildCfgDefn getInstance() {
    return INSTANCE;
  }



  /**
   * Private constructor.
   */
  private TestChildCfgDefn() {
    super("test-child", null);
  }



  /**
   * {@inheritDoc}
   */
  public TestChildCfgClient createClientConfiguration(
      ManagedObject<? extends TestChildCfgClient> impl) {
    return new TestChildCfgClientImpl(impl);
  }



  /**
   * {@inheritDoc}
   */
  public TestChildCfg createServerConfiguration(
      ServerManagedObject<? extends TestChildCfg> impl) {
    return new TestChildCfgServerImpl(impl);
  }



  /**
   * {@inheritDoc}
   */
  public Class<TestChildCfg> getServerConfigurationClass() {
    return TestChildCfg.class;
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
   * Get the "mandatory-read-only-attribute-type-property" property definition.
   * <p>
   * A mandatory read-only attribute type property.
   *
   * @return Returns the "mandatory-read-only-attribute-type-property" property definition.
   */
  public AttributeTypePropertyDefinition getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition() {
    return PD_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY;
  }



  /**
   * Get the "optional-multi-valued-dn-property1" property definition.
   * <p>
   * An optional multi-valued DN property which inherits its values
   * from optional-multi-valued-dn-property in the parent.
   *
   * @return Returns the "optional-multi-valued-dn-property1" property definition.
   */
  public DNPropertyDefinition getOptionalMultiValuedDNProperty1PropertyDefinition() {
    return PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY1;
  }



  /**
   * Get the "optional-multi-valued-dn-property2" property definition.
   * <p>
   * An optional multi-valued DN property which inherits its values
   * from optional-multi-valued-dn-property1.
   *
   * @return Returns the "optional-multi-valued-dn-property2" property definition.
   */
  public DNPropertyDefinition getOptionalMultiValuedDNProperty2PropertyDefinition() {
    return PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY2;
  }



  /**
   * Managed object client implementation.
   */
  private static class TestChildCfgClientImpl implements
    TestChildCfgClient {

    // Private implementation.
    private ManagedObject<? extends TestChildCfgClient> impl;



    // Private constructor.
    private TestChildCfgClientImpl(
        ManagedObject<? extends TestChildCfgClient> impl) {
      this.impl = impl;
    }



    /**
     * {@inheritDoc}
     */
    public Boolean isMandatoryBooleanProperty() {
      return impl.getPropertyValue(INSTANCE.getMandatoryBooleanPropertyPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public void setMandatoryBooleanProperty(boolean value) {
      impl.setPropertyValue(INSTANCE.getMandatoryBooleanPropertyPropertyDefinition(), value);
    }



    /**
     * {@inheritDoc}
     */
    public String getMandatoryClassProperty() {
      return impl.getPropertyValue(INSTANCE.getMandatoryClassPropertyPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public void setMandatoryClassProperty(String value) {
      impl.setPropertyValue(INSTANCE.getMandatoryClassPropertyPropertyDefinition(), value);
    }



    /**
     * {@inheritDoc}
     */
    public AttributeType getMandatoryReadOnlyAttributeTypeProperty() {
      return impl.getPropertyValue(INSTANCE.getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public void setMandatoryReadOnlyAttributeTypeProperty(AttributeType value) throws PropertyIsReadOnlyException {
      impl.setPropertyValue(INSTANCE.getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition(), value);
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<DN> getOptionalMultiValuedDNProperty1() {
      return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty1PropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public void setOptionalMultiValuedDNProperty1(Collection<DN> values) {
      impl.setPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty1PropertyDefinition(), values);
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<DN> getOptionalMultiValuedDNProperty2() {
      return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty2PropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public void setOptionalMultiValuedDNProperty2(Collection<DN> values) {
      impl.setPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty2PropertyDefinition(), values);
    }



    /**
     * {@inheritDoc}
     */
    public ManagedObjectDefinition<? extends TestChildCfgClient, ? extends TestChildCfg> definition() {
      return INSTANCE;
    }



    /**
     * {@inheritDoc}
     */
    public PropertyProvider properties() {
      return impl;
    }



    /**
     * {@inheritDoc}
     */
    public void commit() throws ManagedObjectAlreadyExistsException,
        MissingMandatoryPropertiesException, ConcurrentModificationException,
        OperationRejectedException, AuthorizationException,
        CommunicationException {
      impl.commit();
    }

  }



  /**
   * Managed object server implementation.
   */
  private static class TestChildCfgServerImpl implements
    TestChildCfg {

    // Private implementation.
    private ServerManagedObject<? extends TestChildCfg> impl;



    // Private constructor.
    private TestChildCfgServerImpl(ServerManagedObject<? extends TestChildCfg> impl) {
      this.impl = impl;
    }



    /**
     * {@inheritDoc}
     */
    public void addChangeListener(
        ConfigurationChangeListener<TestChildCfg> listener) {
      impl.registerChangeListener(listener);
    }



    /**
     * {@inheritDoc}
     */
    public void removeChangeListener(
        ConfigurationChangeListener<TestChildCfg> listener) {
      impl.deregisterChangeListener(listener);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isMandatoryBooleanProperty() {
      return impl.getPropertyValue(INSTANCE.getMandatoryBooleanPropertyPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public String getMandatoryClassProperty() {
      return impl.getPropertyValue(INSTANCE.getMandatoryClassPropertyPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public AttributeType getMandatoryReadOnlyAttributeTypeProperty() {
      return impl.getPropertyValue(INSTANCE.getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<DN> getOptionalMultiValuedDNProperty1() {
      return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty1PropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public SortedSet<DN> getOptionalMultiValuedDNProperty2() {
      return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNProperty2PropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public ManagedObjectDefinition<? extends TestChildCfgClient, ? extends TestChildCfg> definition() {
      return INSTANCE;
    }



    /**
     * {@inheritDoc}
     */
    public PropertyProvider properties() {
      return impl;
    }



    /**
     * {@inheritDoc}
     */
    public DN dn() {
      return impl.getDN();
    }

  }
}
