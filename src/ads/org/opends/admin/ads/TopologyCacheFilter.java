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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.admin.ads;

import java.util.HashSet;
import java.util.Set;

/**
 * Class used to filter what we look for in the topology cache.
 * This is done in particular to avoid problems of performance when we
 * know what we are looking for.  It is particularly useful to avoid
 * searching for monitoring information.
 */
public class TopologyCacheFilter
{
  private Set<String> baseDNs = new HashSet<String>();
  private boolean searchMonitoringInformation = true;
  private boolean searchBaseDNInformation = true;

  /**
   * Returns whether we must search for base DN information or not.
   * @return <CODE>true</CODE> if we must search base DN information and
   * <CODE>false</CODE> otherwise.
   */
  public boolean searchBaseDNInformation()
  {
    return searchBaseDNInformation;
  }

  /**
   * Sets whether we must search for base DN information or not.
   * @param searchBaseDNInformation whether we must search for base DN
   * information or not.
   */
  public void setSearchBaseDNInformation(
      boolean searchBaseDNInformation)
  {
    this.searchBaseDNInformation = searchBaseDNInformation;
  }


  /**
   * Returns whether we must search for monitoring information or not.
   * @return <CODE>true</CODE> if we must search monitoring information and
   * <CODE>false</CODE> otherwise.
   */
  public boolean searchMonitoringInformation()
  {
    return searchMonitoringInformation;
  }

  /**
   * Sets whether we must search for monitoring information or not.
   * @param searchMonitoringInformation whether we must search for monitoring
   * information or not.
   */
  public void setSearchMonitoringInformation(
      boolean searchMonitoringInformation)
  {
    this.searchMonitoringInformation = searchMonitoringInformation;
  }

  /**
   * Adds one of the base DNs we must search for.  If at least one baseDN
   * is added using this method, only the added baseDNs are searched.  If no
   * base DN is added, all the base DNs will be retrieved.
   * @param dn the DN of the base DN to look for.
   */
  public void addBaseDNToSearch(String dn)
  {
    baseDNs.add(dn);
  }

  /**
   * Removes a base DN fom the list of baseDNs to search.
   * @param dn the DN of the base DN to be removed.
   */
  public void removeBaseDNToSearch(String dn)
  {
    baseDNs.remove(dn);
  }

  /**
   * Returns the list of base DNs that will be searched for.  If the list is
   * empty we will search for all the base DNs.
   * @return the list of base DNs we will search for.
   */
  public Set<String> getBaseDNsToSearch()
  {
    return new HashSet<String>(baseDNs);
  }

  /**
   * Tells whether this filter specifies to search for all the base DNs or not.
   * @return <CODE>true</CODE> if the filter specifies to search for all the
   * base DNs and <CODE>false</CODE> otherwise.
   */
  public boolean searchAllBaseDNs()
  {
    return baseDNs.isEmpty();
  }
}
