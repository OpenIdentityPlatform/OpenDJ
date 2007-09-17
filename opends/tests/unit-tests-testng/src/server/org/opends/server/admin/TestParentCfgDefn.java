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
import org.opends.server.admin.client.IllegalManagedObjectNameException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefaultBehaviorProvider;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DNPropertyDefinition;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;



/**
 * An interface for querying the Test Parent managed object definition
 * meta information.
 * <p>
 * A configuration for testing components that have child components.
 * It re-uses the virtual-attribute configuration LDAP profile.
 */
public final class TestParentCfgDefn extends ManagedObjectDefinition<TestParentCfgClient, TestParentCfg> {

  // The singleton configuration definition instance.
  private static final TestParentCfgDefn INSTANCE = new TestParentCfgDefn();



  // The "mandatory-boolean-property" property definition.
  private static final BooleanPropertyDefinition PD_MANDATORY_BOOLEAN_PROPERTY;



  // The "mandatory-class-property" property definition.
  private static final ClassPropertyDefinition PD_MANDATORY_CLASS_PROPERTY;



  // The "mandatory-read-only-attribute-type-property" property definition.
  private static final AttributeTypePropertyDefinition PD_MANDATORY_READ_ONLY_ATTRIBUTE_TYPE_PROPERTY;



  // The "optional-multi-valued-dn-property" property definition.
  private static final DNPropertyDefinition PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY;



  // The "test-children" relation definition.
  private static final InstantiableRelationDefinition<TestChildCfgClient, TestChildCfg> RD_TEST_CHILDREN;



  // The "optional-test-child" relation definition.
  private static final OptionalRelationDefinition<TestChildCfgClient, TestChildCfg> RD_OPTIONAL_TEST_CHILD;



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



  // Build the "optional-multi-valued-dn-property" property definition.
  static {
      DNPropertyDefinition.Builder builder = DNPropertyDefinition.createBuilder(INSTANCE, "optional-multi-valued-dn-property");
      builder.setOption(PropertyOption.MULTI_VALUED);
      builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.NONE, INSTANCE, "optional-multi-valued-dn-property"));
      DefaultBehaviorProvider<DN> provider = new DefinedDefaultBehaviorProvider<DN>("dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");
      builder.setDefaultBehaviorProvider(provider);
      PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY = builder.getInstance();
      INSTANCE.registerPropertyDefinition(PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY);
  }



  // Build the "test-children" relation definition.
  static {
    InstantiableRelationDefinition.Builder<TestChildCfgClient, TestChildCfg> builder =
      new InstantiableRelationDefinition.Builder<TestChildCfgClient, TestChildCfg>(
        INSTANCE, "multiple-children", "test-children", TestChildCfgDefn
            .getInstance());
    RD_TEST_CHILDREN = builder.getInstance();
    INSTANCE.registerRelationDefinition(RD_TEST_CHILDREN);
  }



  // Build the "optional-test-child" relation definition.
  static {
    OptionalRelationDefinition.Builder<TestChildCfgClient, TestChildCfg> builder =
      new OptionalRelationDefinition.Builder<TestChildCfgClient, TestChildCfg>(
        INSTANCE, "optional-test-child", TestChildCfgDefn.getInstance());
    RD_OPTIONAL_TEST_CHILD = builder.getInstance();
    INSTANCE.registerRelationDefinition(RD_OPTIONAL_TEST_CHILD);
  }



  /**
   * Get the Test Parent configuration definition singleton.
   *
   * @return Returns the Test Parent configuration definition
   *         singleton.
   */
  public static TestParentCfgDefn getInstance() {
    return INSTANCE;
  }



  /**
   * Private constructor.
   */
  private TestParentCfgDefn() {
    super("test-parent", TopCfgDefn.getInstance());
  }



  /**
   * {@inheritDoc}
   */
  public TestParentCfgClient createClientConfiguration(
      ManagedObject<? extends TestParentCfgClient> impl) {
    return new TestParentCfgClientImpl(impl);
  }



  /**
   * {@inheritDoc}
   */
  public TestParentCfg createServerConfiguration(
      ServerManagedObject<? extends TestParentCfg> impl) {
    return new TestParentCfgServerImpl(impl);
  }



  /**
   * {@inheritDoc}
   */
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
   * Get the "optional-multi-valued-dn-property" property definition.
   * <p>
   * An optional multi-valued DN property with a defined default
   * behavior.
   *
   * @return Returns the "optional-multi-valued-dn-property" property definition.
   */
  public DNPropertyDefinition getOptionalMultiValuedDNPropertyPropertyDefinition() {
    return PD_OPTIONAL_MULTI_VALUED_DN_PROPERTY;
  }



  /**
   * Get the "test-children" relation definition.
   *
   * @return Returns the "test-children" relation definition.
   */
  public InstantiableRelationDefinition<TestChildCfgClient,TestChildCfg> getTestChildrenRelationDefinition() {
    return RD_TEST_CHILDREN;
  }



  /**
   * Get the "optional-test-child" relation definition.
   *
   * @return Returns the "optional-test-child" relation definition.
   */
  public OptionalRelationDefinition<TestChildCfgClient,TestChildCfg> getOptionalTestChildRelationDefinition() {
    return RD_OPTIONAL_TEST_CHILD;
  }



  /**
   * Managed object client implementation.
   */
  private static class TestParentCfgClientImpl implements
    TestParentCfgClient {

    // Private implementation.
    private ManagedObject<? extends TestParentCfgClient> impl;



    // Private constructor.
    private TestParentCfgClientImpl(
        ManagedObject<? extends TestParentCfgClient> impl) {
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
    public SortedSet<DN> getOptionalMultiValuedDNProperty() {
      return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNPropertyPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public void setOptionalMultiValuedDNProperty(Collection<DN> values) {
      impl.setPropertyValues(INSTANCE.getOptionalMultiValuedDNPropertyPropertyDefinition(), values);
    }



    /**
     * {@inheritDoc}
     */
    public String[] listTestChildren() throws ConcurrentModificationException,
        AuthorizationException, CommunicationException {
      return impl.listChildren(INSTANCE.getTestChildrenRelationDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public TestChildCfgClient getTestChild(String name)
        throws DefinitionDecodingException, ManagedObjectDecodingException,
        ManagedObjectNotFoundException, ConcurrentModificationException,
        AuthorizationException, CommunicationException {
      return impl.getChild(INSTANCE.getTestChildrenRelationDefinition(), name).getConfiguration();
    }



    /**
     * {@inheritDoc}
     */
    public <M extends TestChildCfgClient> M createTestChild(
        ManagedObjectDefinition<M, ? extends TestChildCfg> d, String name, Collection<DefaultBehaviorException> exceptions) throws IllegalManagedObjectNameException {
      return impl.createChild(INSTANCE.getTestChildrenRelationDefinition(), d, name, exceptions).getConfiguration();
    }



    /**
     * {@inheritDoc}
     */
    public void removeTestChild(String name)
        throws ManagedObjectNotFoundException, ConcurrentModificationException,
        OperationRejectedException, AuthorizationException, CommunicationException {
      impl.removeChild(INSTANCE.getTestChildrenRelationDefinition(), name);
    }



    /**
     * {@inheritDoc}
     */
    public boolean hasOptionalTestChild() throws ConcurrentModificationException,
        AuthorizationException, CommunicationException {
      return impl.hasChild(INSTANCE.getOptionalTestChildRelationDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public TestChildCfgClient getOptionalChild()
        throws DefinitionDecodingException, ManagedObjectDecodingException,
        ManagedObjectNotFoundException, ConcurrentModificationException,
        AuthorizationException, CommunicationException {
      return impl.getChild(INSTANCE.getOptionalTestChildRelationDefinition()).getConfiguration();
    }



    /**
     * {@inheritDoc}
     */
    public <M extends TestChildCfgClient> M createOptionalTestChild(
        ManagedObjectDefinition<M, ? extends TestChildCfg> d, Collection<DefaultBehaviorException> exceptions) {
      return impl.createChild(INSTANCE.getOptionalTestChildRelationDefinition(), d, exceptions).getConfiguration();
    }



    /**
     * {@inheritDoc}
     */
    public void removeOptionalTestChild()
        throws ManagedObjectNotFoundException, ConcurrentModificationException,
        OperationRejectedException, AuthorizationException, CommunicationException {
      impl.removeChild(INSTANCE.getOptionalTestChildRelationDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public ManagedObjectDefinition<? extends TestParentCfgClient, ? extends TestParentCfg> definition() {
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
  private static class TestParentCfgServerImpl implements
    TestParentCfg {

    // Private implementation.
    private ServerManagedObject<? extends TestParentCfg> impl;



    // Private constructor.
    private TestParentCfgServerImpl(ServerManagedObject<? extends TestParentCfg> impl) {
      this.impl = impl;
    }



    /**
     * {@inheritDoc}
     */
    public void addChangeListener(
        ConfigurationChangeListener<TestParentCfg> listener) {
      impl.registerChangeListener(listener);
    }



    /**
     * {@inheritDoc}
     */
    public void removeChangeListener(
        ConfigurationChangeListener<TestParentCfg> listener) {
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
    public SortedSet<DN> getOptionalMultiValuedDNProperty() {
      return impl.getPropertyValues(INSTANCE.getOptionalMultiValuedDNPropertyPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public String[] listTestChildren() {
      return impl.listChildren(INSTANCE.getTestChildrenRelationDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public TestChildCfg getTestChild(String name) throws ConfigException {
      return impl.getChild(INSTANCE.getTestChildrenRelationDefinition(), name).getConfiguration();
    }



    /**
     * {@inheritDoc}
     */
    public void addTestChildAddListener(
        ConfigurationAddListener<TestChildCfg> listener) throws ConfigException {
      impl.registerAddListener(INSTANCE.getTestChildrenRelationDefinition(), listener);
    }



    /**
     * {@inheritDoc}
     */
    public void removeTestChildAddListener(
        ConfigurationAddListener<TestChildCfg> listener) {
      impl.deregisterAddListener(INSTANCE.getTestChildrenRelationDefinition(), listener);
    }



    /**
     * {@inheritDoc}
     */
    public void addTestChildDeleteListener(
        ConfigurationDeleteListener<TestChildCfg> listener) throws ConfigException {
      impl.registerDeleteListener(INSTANCE.getTestChildrenRelationDefinition(), listener);
    }



    /**
     * {@inheritDoc}
     */
    public void removeTestChildDeleteListener(
        ConfigurationDeleteListener<TestChildCfg> listener) {
      impl.deregisterDeleteListener(INSTANCE.getTestChildrenRelationDefinition(), listener);
    }



    /**
     * {@inheritDoc}
     */
    public boolean hasOptionalTestChild() {
      return impl.hasChild(INSTANCE.getOptionalTestChildRelationDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public TestChildCfg getOptionalTestChild() throws ConfigException {
      return impl.getChild(INSTANCE.getOptionalTestChildRelationDefinition()).getConfiguration();
    }



    /**
     * {@inheritDoc}
     */
    public void addOptionalTestChildAddListener(
        ConfigurationAddListener<TestChildCfg> listener) throws ConfigException {
      impl.registerAddListener(INSTANCE.getOptionalTestChildRelationDefinition(), listener);
    }



    /**
     * {@inheritDoc}
     */
    public void removeOptionalTestChildAddListener(
        ConfigurationAddListener<TestChildCfg> listener) {
      impl.deregisterAddListener(INSTANCE.getOptionalTestChildRelationDefinition(), listener);
    }



    /**
     * {@inheritDoc}
     */
    public void addOptionalChildTestDeleteListener(
        ConfigurationDeleteListener<TestChildCfg> listener) throws ConfigException {
      impl.registerDeleteListener(INSTANCE.getOptionalTestChildRelationDefinition(), listener);
    }



    /**
     * {@inheritDoc}
     */
    public void removeOptionalTestChildDeleteListener(
        ConfigurationDeleteListener<TestChildCfg> listener) {
      impl.deregisterDeleteListener(INSTANCE.getOptionalTestChildRelationDefinition(), listener);
    }



    /**
     * {@inheritDoc}
     */
    public ManagedObjectDefinition<? extends TestParentCfgClient, ? extends TestParentCfg> definition() {
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
