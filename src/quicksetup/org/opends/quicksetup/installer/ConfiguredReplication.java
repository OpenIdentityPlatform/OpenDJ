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

package org.opends.quicksetup.installer;

import java.util.HashSet;
import java.util.Set;

/**
 * Class used to know what has been configured in terms of replication on
 * a server.  This class provides a read only view of what has been configured.
 *
 */
class ConfiguredReplication
{
  private boolean synchProviderCreated;
  private boolean synchProviderEnabled;
  private boolean secureReplicationEnabled;
  private boolean replicationServerCreated;
  private Set<String> newReplicationServers;
  private Set<ConfiguredDomain> domainsConf;

  /**
   * Constructor of the ConfiguredReplication object.
   * @param synchProviderCreated whether the synchronization provider was
   * created or not.
   * @param synchProviderEnabled whether the synchronization provider was
   * enabled or not.
   * @param secureReplicationEnabled whether we enabled security for
   * replication.
   * @param replicationServerCreated whether the replication server was
   * created or not.
   * @param newReplicationServers the set of replication servers added to
   * the replication server configuration.
   * @param domainsConf the set of ConfiguredDomain objects representing the
   * replication domains that were modified.
   */
  ConfiguredReplication(boolean synchProviderCreated,
      boolean synchProviderEnabled, boolean replicationServerCreated,
      boolean secureReplicationEnabled, Set<String> newReplicationServers,
      Set<ConfiguredDomain> domainsConf)
  {
    this.synchProviderCreated = synchProviderCreated;
    this.synchProviderEnabled = synchProviderEnabled;
    this.replicationServerCreated = replicationServerCreated;
    this.secureReplicationEnabled = secureReplicationEnabled;
    this.newReplicationServers = new HashSet<String>();
    this.newReplicationServers.addAll(newReplicationServers);
    this.domainsConf = new HashSet<ConfiguredDomain>();
    this.domainsConf.addAll(domainsConf);
  }

  /**
   * Returns a set of ConfiguredDomain objects representing the replication
   * domains that were modified.
   * @return a set of ConfiguredDomain objects representing the replication
   * domains that were modified.
   */
  public Set<ConfiguredDomain> getDomainsConf()
  {
    return domainsConf;
  }


  /**
   * Returns a set of replication servers added to the replication server
   * configuration.
   * @return a set of replication servers added to the replication server
   * configuration.
   */
  Set<String> getNewReplicationServers()
  {
    return newReplicationServers;
  }

  /**
   * Returns <CODE>true</CODE> if the Replication Server was created and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the Replication Server was created and
   * <CODE>false</CODE> otherwise.
   */
  boolean isReplicationServerCreated()
  {
    return replicationServerCreated;
  }

  /**
   * Returns <CODE>true</CODE> if the Security was enabled for replication and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the Security was enabled for replication and
   * <CODE>false</CODE> otherwise.
   */
  boolean isSecureReplicationEnabled()
  {
    return secureReplicationEnabled;
  }

  /**
   * Returns <CODE>true</CODE> if the Synchronization Provider was created and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the Synchronization Provider was created and
   * <CODE>false</CODE> otherwise.
   */
  boolean isSynchProviderCreated()
  {
    return synchProviderCreated;
  }

  /**
   * Returns <CODE>true</CODE> if the Synchronization Provider was enabled and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the Synchronization Provider was enabled and
   * <CODE>false</CODE> otherwise.
   */
  boolean isSynchProviderEnabled()
  {
    return synchProviderEnabled;
  }
}
