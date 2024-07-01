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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.server.ExternalChangelogDomainCfg;

/** This class specifies the external changelog feature for a replication domain. */
public class ExternalChangelogDomain
  implements ConfigurationAddListener<ExternalChangelogDomainCfg>,
             ConfigurationDeleteListener<ExternalChangelogDomainCfg>,
             ConfigurationChangeListener<ExternalChangelogDomainCfg>
{

  private LDAPReplicationDomain domain;
  private boolean isEnabled;

  /**
   * Constructor from a provided LDAPReplicationDomain.
   * @param domain The provided domain.
   * @param configuration The external changelog configuration.
   */
  public ExternalChangelogDomain(LDAPReplicationDomain domain,
      ExternalChangelogDomainCfg configuration)
  {
    this.domain = domain;
    this.isEnabled = configuration.isEnabled();
    configuration.addChangeListener(this);
    domain.setEclIncludes(domain.getServerId(),
        configuration.getECLInclude(),
        configuration.getECLIncludeForDeletes());
  }


  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationAdd(
      ExternalChangelogDomainCfg configuration)
  {
    final ConfigChangeResult ccr = setDomain(configuration);
    if (ccr != null)
    {
      return ccr;
    }

    this.isEnabled = configuration.isEnabled();
    domain.setEclIncludes(domain.getServerId(),
        configuration.getECLInclude(),
        configuration.getECLIncludeForDeletes());
    return new ConfigChangeResult();
  }


  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      ExternalChangelogDomainCfg configuration)
  {
    // How it works with dsconfig :
    // - after dsconfig set-external-changelog-domain-prop --set ecl-include:xx
    //   configuration contains only attribute xx
    // - after dsconfig set-external-changelog-domain-prop --add ecl-include:xx
    //   configuration contains attribute xx and the previous list
    // Hence in all cases, it is the complete list of attributes.
    final ConfigChangeResult ccr = setDomain(configuration);
    if (ccr != null)
    {
      return ccr;
    }

    this.isEnabled = configuration.isEnabled();
    domain.changeConfig(configuration.getECLInclude(),
        configuration.getECLIncludeForDeletes());
    return new ConfigChangeResult();
  }

  private ConfigChangeResult setDomain(ExternalChangelogDomainCfg configuration)
  {
    try
    {
      if (domain==null)
      {
        RDN rdn = configuration.dn().parent().rdn();
        DN rdns = DN.valueOf(rdn.getFirstAVA().getAttributeValue());
        domain = MultimasterReplication.findDomain(rdns, null);
      }
      return null;
    }
    catch (Exception e)
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
      return ccr;
    }
  }


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAddAcceptable(
      ExternalChangelogDomainCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }
  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      ExternalChangelogDomainCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationDeleteAcceptable(
      ExternalChangelogDomainCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }


  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationDelete(
      ExternalChangelogDomainCfg configuration)
  {
    return new ConfigChangeResult();
  }

  /**
   * Specifies whether this domain is enabled/disabled regarding the ECL.
   * @return enabled/disabled for the ECL.
   */
  boolean isEnabled()
  {
    return this.isEnabled;
  }
}
