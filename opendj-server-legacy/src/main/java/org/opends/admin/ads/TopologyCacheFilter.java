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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.admin.ads;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;

/**
 * Class used to filter what we look for in the topology cache.
 * This is done in particular to avoid problems of performance when we
 * know what we are looking for.  It is particularly useful to avoid
 * searching for monitoring information.
 */
public class TopologyCacheFilter
{
  private final Set<DN> baseDNs = new HashSet<>();
  private boolean searchMonitoringInformation = true;
  private boolean searchBaseDNInformation = true;

  /**
   * Returns whether we must search for base DN information or not.
   * @return {@code true} if we must search base DN information and
   * {@code false} otherwise.
   */
  boolean searchBaseDNInformation()
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
   * @return {@code true} if we must search monitoring information and
   * {@code false} otherwise.
   */
  boolean searchMonitoringInformation()
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
   * @param dn the base DN to look for.
   */
  public void addBaseDNToSearch(DN dn)
  {
    baseDNs.add(dn);
  }

  /**
   * Adds all the base DNs we must search for.  If at least one baseDN
   * is added using this method, only the added baseDNs are searched.
   * If no base DN is added, all the base DNs will be retrieved.
   * @param dns the base DNs to look for.
   */
  public void addBaseDNsToSearch(Collection<DN> dns)
  {
    baseDNs.addAll(dns);
  }

  /**
   * Returns the list of base DNs that will be searched for.  If the list is
   * empty we will search for all the base DNs.
   * @return the list of base DNs we will search for.
   */
  public Set<DN> getBaseDNsToSearch()
  {
    return new HashSet<>(baseDNs);
  }

  /**
   * Tells whether this filter specifies to search for all the base DNs or not.
   * @return {@code true} if the filter specifies to search for all the
   * base DNs and {@code false} otherwise.
   */
  boolean searchAllBaseDNs()
  {
    return baseDNs.isEmpty();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "("
        + "baseDNs=" + baseDNs
        + ", searchMonitoringInformation=" + searchMonitoringInformation
        + ", searchBaseDNInformation=" + searchBaseDNInformation
        + ")";
  }
}
