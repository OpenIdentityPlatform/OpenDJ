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



import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.types.DN;



/**
 * A sample configuration definition class for testing.
 */
public final class TestChildCfgDefn extends
    ManagedObjectDefinition<TestChildCfgClient, TestChildCfg> {

  // The singleton configuration definition instance.
  private static final TestChildCfgDefn INSTANCE = new TestChildCfgDefn();

  // The "maximum-length" property definition.
  private static final IntegerPropertyDefinition PD_MAXIMUM_LENGTH;

  // The "minimum-length" property definition.
  private static final IntegerPropertyDefinition PD_MINIMUM_LENGTH;

  // The "heartbeat-interval" property definition.
  private static final DurationPropertyDefinition PD_HEARTBEAT_INTERVAL;

  // Build the "maximum-length" property definition.
  static {
    IntegerPropertyDefinition.Builder builder = IntegerPropertyDefinition
        .createBuilder(INSTANCE, "maximum-length");
    DefaultBehaviorProvider<Integer> provider = new RelativeInheritedDefaultBehaviorProvider<Integer>(
        TestParentCfgDefn.getInstance(), "maximum-length", 1);
    builder.setDefaultBehaviorProvider(provider);
    builder.setLowerLimit(0);
    PD_MAXIMUM_LENGTH = builder.getInstance();
    INSTANCE.registerPropertyDefinition(PD_MAXIMUM_LENGTH);
  }

  // Build the "minimum-length" property definition.
  static {
    IntegerPropertyDefinition.Builder builder = IntegerPropertyDefinition
        .createBuilder(INSTANCE, "minimum-length");
    DefaultBehaviorProvider<Integer> provider = new AbsoluteInheritedDefaultBehaviorProvider<Integer>(
        ManagedObjectPath.valueOf("/relation=test-parent+name=test parent 2"),
        "minimum-length");
    builder.setDefaultBehaviorProvider(provider);
    builder.setLowerLimit(0);
    PD_MINIMUM_LENGTH = builder.getInstance();
    INSTANCE.registerPropertyDefinition(PD_MINIMUM_LENGTH);
  }

  // Build the "heartbeat-interval" property definition.
  static {
    DurationPropertyDefinition.Builder builder = DurationPropertyDefinition
        .createBuilder(INSTANCE, "heartbeat-interval");
    DefaultBehaviorProvider<Long> provider = new DefinedDefaultBehaviorProvider<Long>(
        "1000ms");
    builder.setDefaultBehaviorProvider(provider);
    builder.setAllowUnlimited(false);
    builder.setBaseUnit("ms");
    builder.setLowerLimit("100");
    PD_HEARTBEAT_INTERVAL = builder.getInstance();
    INSTANCE.registerPropertyDefinition(PD_HEARTBEAT_INTERVAL);
  }



  /**
   * Get the definition singleton.
   *
   * @return Returns the definition singleton.
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
   * Get the "heartbeat-interval" property definition.
   *
   * @return Returns the "heartbeat-interval" property definition.
   */
  public DurationPropertyDefinition getHeartbeatIntervalPropertyDefinition() {
    return PD_HEARTBEAT_INTERVAL;
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
   * Managed object client implementation.
   */
  private static class TestChildCfgClientImpl implements TestChildCfgClient {

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
    public long getHeartbeatInterval() {
      return impl.getPropertyValue(INSTANCE
          .getHeartbeatIntervalPropertyDefinition());
    }



    /**
     * {@inheritDoc}
     */
    public void setHeartbeatInterval(Long value) {
      impl.setPropertyValue(INSTANCE.getHeartbeatIntervalPropertyDefinition(),
          value);
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
    public void commit() throws ConcurrentModificationException,
        OperationRejectedException, AuthorizationException,
        CommunicationException, ManagedObjectAlreadyExistsException,
        MissingMandatoryPropertiesException {
      impl.commit();
    }
  }



  /**
   * Managed object server implementation.
   */
  private static class TestChildCfgServerImpl implements TestChildCfg {

    // Private implementation.
    private ServerManagedObject<? extends TestChildCfg> impl;



    // Private constructor.
    private TestChildCfgServerImpl(
        ServerManagedObject<? extends TestChildCfg> impl) {
      this.impl = impl;
    }



    /**
     * {@inheritDoc}
     */
    public long getHeartbeatInterval() {
      return impl.getPropertyValue(INSTANCE
          .getHeartbeatIntervalPropertyDefinition());
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
