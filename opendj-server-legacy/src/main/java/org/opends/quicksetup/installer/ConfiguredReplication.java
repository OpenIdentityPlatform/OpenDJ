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
 * Portions Copyright 2015 ForgeRock AS.
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
    this.newReplicationServers = new HashSet<>();
    this.newReplicationServers.addAll(newReplicationServers);
    this.domainsConf = new HashSet<>();
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
