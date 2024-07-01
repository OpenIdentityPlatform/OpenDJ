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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import org.opends.server.replication.plugin.MultimasterReplication;
import org.forgerock.opendj.ldap.DN;

/**
 * Returns whether a domain is enabled for the external changelog.
 *
 * @FunctionalInterface
 */
public class ECLEnabledDomainPredicate
{

  /**
   * Returns whether the provided baseDN represents a replication domain enabled
   * for the external changelog.
   * <p>
   * This method is a test seam that break the dependency on a static method.
   *
   * @param baseDN
   *          the replication domain to check
   * @return true if the provided baseDN is enabled for the external changelog,
   *         false if the provided baseDN is disabled for the external changelog
   *         or unknown to multimaster replication.
   * @see MultimasterReplication#isECLEnabledDomain(DN)
   */
  public boolean isECLEnabledDomain(DN baseDN)
  {
    return MultimasterReplication.isECLEnabledDomain(baseDN);
  }
}
