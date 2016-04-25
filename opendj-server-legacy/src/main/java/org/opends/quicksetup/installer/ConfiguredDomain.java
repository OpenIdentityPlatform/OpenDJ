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
package org.opends.quicksetup.installer;

import java.util.HashSet;
import java.util.Set;

/**
 * Class used to know what has been modified in the configuration of a
 * replication domain.
 * This class provides a read only view of what has been configured.
 */
class ConfiguredDomain
{
  private final String domainName;
  private final boolean isCreated;
  private final Set<String> addedReplicationServers;

  /**
   * Constructor of the ConfiguredDomain object.
   * @param domainName the name of the domain.
   * @param isCreated whether the domain has been created or not.
   * @param addedReplicationServers the set of replication servers added to
   * the replication server configuration.
   */
  ConfiguredDomain(String domainName, boolean isCreated,
      Set<String> addedReplicationServers)
  {
    this.domainName = domainName;
    this.isCreated = isCreated;
    this.addedReplicationServers = new HashSet<>();
    this.addedReplicationServers.addAll(addedReplicationServers);
  }

  /**
   * Returns a set of replication servers added to the replication domain
   * configuration.
   * @return a set of replication servers added to the replication domain
   * configuration.
   */
  Set<String> getAddedReplicationServers()
  {
    return addedReplicationServers;
  }

  /**
   * Returns the domain name.
   * @return the domain name.
   */
  String getDomainName()
  {
    return domainName;
  }

  /**
   * Returns <CODE>true</CODE> if the Replication domain was created and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the Replication domain was created and
   * <CODE>false</CODE> otherwise.
   */
  boolean isCreated()
  {
    return isCreated;
  }
}
