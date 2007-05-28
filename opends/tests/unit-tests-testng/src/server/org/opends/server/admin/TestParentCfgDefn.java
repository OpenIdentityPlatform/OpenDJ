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

import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.types.DN;



/**
 * A sample configuration definition class for testing.
 */
public final class TestParentCfgDefn extends
    ManagedObjectDefinition<TestParentCfgClient, TestParentCfg> {

  // The singleton configuration definition instance.
  private static final TestParentCfgDefn INSTANCE = new TestParentCfgDefn();

  /**
   * The relation between this definition and the root.
   */
  public static final InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg> RD_TEST_PARENT;

  // The "test-children" relation definition.
  private static final InstantiableRelationDefinition<TestChildCfgClient, TestChildCfg> RD_TEST_CHILDREN;

  // The "maximum-length" property definition.
  private static final IntegerPropertyDefinition PD_MAXIMUM_LENGTH;

  // The "minimum-length" property definition.
  private static final IntegerPropertyDefinition PD_MINIMUM_LENGTH;

  // Build the "maximum-length" property definition.
  static {
    IntegerPropertyDefinition.Builder builder = IntegerPropertyDefinition
        .createBuilder(INSTANCE, "maximum-length");
    DefaultBehaviorProvider<Integer> provider = new DefinedDefaultBehaviorProvider<Integer>(
        "456");
    builder.setDefaultBehaviorProvider(provider);
    builder.setLowerLimit(0);
    PD_MAXIMUM_LENGTH = builder.getInstance();
    INSTANCE.registerPropertyDefinition(PD_MAXIMUM_LENGTH);
  }

  // Build the "minimum-length" property definition.
  static {
    IntegerPropertyDefinition.Builder builder = IntegerPropertyDefinition
        .createBuilder(INSTANCE, "minimum-length");
    DefaultBehaviorProvider<Integer> provider = new DefinedDefaultBehaviorProvider<Integer>(
        "123");
    builder.setDefaultBehaviorProvider(provider);
    builder.setLowerLimit(0);
    PD_MINIMUM_LENGTH = builder.getInstance();
    INSTANCE.registerPropertyDefinition(PD_MINIMUM_LENGTH);
  }

  // Register this as a relation against the root configuration.
  static {
    RD_TEST_PARENT = new InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg>(
        INSTANCE, "test-parent", "test-parents", INSTANCE);
    RootCfgDefn.getInstance().registerRelationDefinition(RD_TEST_PARENT);
  }

  // Build the "test-children" relation definition.
  static {
    RD_TEST_CHILDREN = new InstantiableRelationDefinition<TestChildCfgClient, TestChildCfg>(
        INSTANCE, "test-child", "test-children", TestChildCfgDefn.getInstance());
    INSTANCE.registerRelationDefinition(RD_TEST_CHILDREN);
  }



  /**
   * Get the definition singleton.
   *
   * @return Returns the definition singleton.
   */
  public static TestParentCfgDefn getInstance() {
    return INSTANCE;
  }



  /**
   * Private constructor.
   */
  private TestParentCfgDefn() {
    super("test-parent", null);
  }



  /**
   * Get the "maximum-length" property definition.
   *
   * @return Returns the "maximum-length" property definition.
   */
  public IntegerPropertyDefinition getMaximumLengthPropertyDefinition() {
    return PD_MAXIMUM_LENGTH;
  }



  /**
   * Get the "minimum-length" property definition.
   *
   * @return Returns the "minimum-length" property definition.
   */
  public IntegerPropertyDefinition getMinimumLengthPropertyDefinition() {
    return PD_MINIMUM_LENGTH;
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
   * Managed object client implementation.
   */
  private static class TestParentCfgClientImpl implements TestParentCfgClient {

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
    public int getMaximumLength() {
      return impl.getPropertyValue(INSTANCE
          .getMaximumLengthPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public void setMaximumLength(Integer value) {
      impl.setPropertyValue(INSTANCE.getMaximumLengthPropertyDefinition(),
          value);
    }



    /**
     * {@inheritDoc}
     */
    public int getMinimumLength() {
      return impl.getPropertyValue(INSTANCE
          .getMinimumLengthPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public void setMinimumLength(Integer value) {
      impl.setPropertyValue(INSTANCE.getMinimumLengthPropertyDefinition(),
          value);
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
    public void commit() throws ConcurrentModificationException,
        OperationRejectedException, AuthorizationException,
        CommunicationException, ManagedObjectAlreadyExistsException,
        MissingMandatoryPropertiesException {
      impl.commit();
    }



    /**
     * {@inheritDoc}
     */
    public <C extends TestChildCfgClient> C createTestChild(
        ManagedObjectDefinition<C, ?> d, String name,
        Collection<DefaultBehaviorException> exceptions) {
      return impl.createChild(INSTANCE.getTestChildrenRelationDefinition(), d,
          name, exceptions).getConfiguration();
    }



    /**
     * {@inheritDoc}
     */
    public TestChildCfgClient getTestChild(String name)
        throws DefinitionDecodingException, ManagedObjectDecodingException,
        ManagedObjectNotFoundException, ConcurrentModificationException,
        AuthorizationException, CommunicationException {
      return impl.getChild(INSTANCE.getTestChildrenRelationDefinition(), name)
          .getConfiguration();
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
    public void removeTestChild(String name)
        throws ManagedObjectNotFoundException, OperationRejectedException,
        ConcurrentModificationException, AuthorizationException,
        CommunicationException {
      impl.removeChild(INSTANCE.getTestChildrenRelationDefinition(), name);
    }

  }



  /**
   * Managed object server implementation.
   */
  private static class TestParentCfgServerImpl implements TestParentCfg {

    // Private implementation.
    private ServerManagedObject<? extends TestParentCfg> impl;



    // Private constructor.
    private TestParentCfgServerImpl(
        ServerManagedObject<? extends TestParentCfg> impl) {
      this.impl = impl;
    }



    /**
     * {@inheritDoc}
     */
    public int getMaximumLength() {
      return impl.getPropertyValue(INSTANCE
          .getMaximumLengthPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public int getMinimumLength() {
      return impl.getPropertyValue(INSTANCE
          .getMinimumLengthPropertyDefinition());
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
