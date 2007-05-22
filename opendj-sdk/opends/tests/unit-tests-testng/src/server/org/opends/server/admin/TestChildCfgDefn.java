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

  // The "heartbeat-interval" property definition.
  private static final DurationPropertyDefinition PD_HEARTBEAT_INTERVAL;

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
        CommunicationException {
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
