/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.ExternalChangelogDomainCfg;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.RDN;

/**
 * This class specifies the external changelog feature for a replication
 * domain.
 */
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
        DN rdns = DN.decode(rdn.getAttributeValue(0));
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
