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
 * Copyright 2007-2009 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.ExternalChangelogDomainCfg;

/**
 * This class implement a configuration object for the ExternalChangelog domain
 * that can be used in unit tests to instantiate ExternalChangelogDomain.
 */
public class ExternalChangelogDomainFakeCfg
  implements ExternalChangelogDomainCfg
{
  /** The value of the "ecl-include" property. */
  private SortedSet<String> pECLInclude;

  /** The value of the "ecl-include-for-deletes" property. */
  private SortedSet<String> pECLIncludeForDeletes;

  /** The value of the "enabled" property. */
  private boolean pEnabled;

  private DN pDN;

  /**
   * Creates a new Domain with the provided information
   * (assured mode disabled, default group id).
   */
  public ExternalChangelogDomainFakeCfg(boolean isEnabled,
      SortedSet<String> eclInclude,
      SortedSet<String> eclIncludeForDeletes)
  {
    this.pEnabled = isEnabled;
    this.pECLInclude = eclInclude != null ? eclInclude : new TreeSet<String>();
    this.pECLIncludeForDeletes = eclIncludeForDeletes != null ? eclIncludeForDeletes : new TreeSet<String>();
  }

  @Override
  public Class<? extends ExternalChangelogDomainCfg> configurationClass()
  {
    return null;
  }

  /**
   * Register to be notified when this External Changelog Domain is changed.
   *
   * @param listener
   *          The External Changelog Domain configuration change listener.
   */
  @Override
  public void addChangeListener(
      ConfigurationChangeListener<ExternalChangelogDomainCfg> listener)
  {}

  /**
   * Deregister an existing External Changelog Domain configuration change listener.
   *
   * @param listener
   *          The External Changelog Domain configuration change listener.
   */
  @Override
  public void removeChangeListener(
      ConfigurationChangeListener<ExternalChangelogDomainCfg> listener)
  {}

  @Override
  public SortedSet<String> getECLInclude()
  {
    return this.pECLInclude;
  }

  @Override
  public SortedSet<String> getECLIncludeForDeletes()
  {
    return this.pECLIncludeForDeletes;
  }

  /**
   * Set enabled.
   * @param enabled a.
   */
  public void setEnable(boolean enabled)
  {
    this.pEnabled = enabled;
  }

  /**
   * Gets the "enabled" property.
   * <p>
   * Indicates whether the External Changelog Domain is enabled for
   * use.
   *
   * @return Returns the value of the "enabled" property.
   */
  @Override
  public boolean isEnabled()
  {
    return this.pEnabled;
  }

  @Override
  public DN dn()
  {
    return pDN;
  }

  public void setDN(DN dn)
  {
    this.pDN = dn;
  }
}
