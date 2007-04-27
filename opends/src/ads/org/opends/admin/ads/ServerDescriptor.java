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

package org.opends.admin.ads;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The object of this class represent an OpenDS server.
 */
public class ServerDescriptor
{
  private Map<ADSContext.ServerProperty, Object> adsProperties =
    new HashMap<ADSContext.ServerProperty, Object>();
  private Set<ReplicaDescriptor> replicas = new HashSet<ReplicaDescriptor>();

  /**
   * Returns the replicas contained on the server.
   * @return the replicas contained on the server.
   */
  public Set<ReplicaDescriptor> getReplicas()
  {
    return replicas;
  }

  /**
   * Sets the replicas contained on the server.
   * @param replicas the replicas contained on the server.
   */
  public void setReplicas(Set<ReplicaDescriptor> replicas)
  {
    this.replicas = replicas;
  }

  /**
   * Returns a Map containing the properties of the server.
   * @return a Map containing the properties of the server.
   */
  public Map<ADSContext.ServerProperty, Object> getAdsProperties()
  {
    return adsProperties;
  }

  /**
   * Tells whether this server is registered in the ADS or not.
   * @return <CODE>true</CODE> if the server is registered in the ADS and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isRegistered()
  {
    return !adsProperties.isEmpty();
  }

  /**
   * Sets the properties of the server.
   * @param adsProperties a Map containing the properties of the server.
   */
  public void setAdsProperties(
      Map<ADSContext.ServerProperty, Object> adsProperties)
  {
    this.adsProperties = adsProperties;
  }
}
