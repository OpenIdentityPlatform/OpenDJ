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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.core.networkgroups;



import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.QOSPolicyCfg;
import org.opends.server.admin.std.server.ResourceLimitsQOSPolicyCfg;
import org.opends.server.types.DN;



/**
 * Stub configuration used in tests.
 */
public abstract class MockResourceLimitsQOSPolicyCfg implements
    ResourceLimitsQOSPolicyCfg
{

  /**
   * {@inheritDoc}
   */
  public final void addChangeListener(
      ConfigurationChangeListener<QOSPolicyCfg> listener)
  {
    // Stub.
  }



  /**
   * {@inheritDoc}
   */
  public final void removeChangeListener(
      ConfigurationChangeListener<QOSPolicyCfg> listener)
  {
    // Stub.
  }



  /**
   * {@inheritDoc}
   */
  public final DN dn()
  {
    // Stub.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public final void addResourceLimitsChangeListener(
      ConfigurationChangeListener<ResourceLimitsQOSPolicyCfg> listener)
  {
    // Stub.
  }



  /**
   * {@inheritDoc}
   */
  public final Class<? extends ResourceLimitsQOSPolicyCfg> configurationClass()
  {
    // Stub.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public final void removeResourceLimitsChangeListener(
      ConfigurationChangeListener<ResourceLimitsQOSPolicyCfg> listener)
  {
    // Stub.
  }



  /**
   * {@inheritDoc}
   */
  public String getJavaClass()
  {
    // Stub.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public int getMaxConcurrentOpsPerConnection()
  {
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public int getMaxConnections()
  {
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public int getMaxConnectionsFromSameIP()
  {
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public int getMaxOpsPerConnection()
  {
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public int getMinSubstringLength()
  {
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public Integer getSizeLimit()
  {
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public Long getTimeLimit()
  {
    return null;
  }

}
