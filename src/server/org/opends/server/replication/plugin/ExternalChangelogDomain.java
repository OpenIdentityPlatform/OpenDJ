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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.ExternalChangelogDomainCfg;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;

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


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      ExternalChangelogDomainCfg configuration)
  {
    try
    {
      if (domain==null)
      {
        DN rdns = DN.decode(
            configuration.dn().getParent().getRDN().getAttributeValue(0).
            getNormalizedValue());
        domain = MultimasterReplication.findDomain(rdns, null);
      }
    }
    catch (Exception e)
    {
      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false);
    }

    this.isEnabled = configuration.isEnabled();
    domain.setEclIncludes(domain.getServerId(),
        configuration.getECLInclude(),
        configuration.getECLIncludeForDeletes());
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      ExternalChangelogDomainCfg configuration)
  {
    // How it works with dsconfig :
    // - after dsconfig set-external-changelog-domain-prop --set ecl-include:xx
    //   configuration contains only attribute xx
    // - after dsconfig set-external-changelog-domain-prop --add ecl-include:xx
    //   configuration contains attribute xx and the previous list
    // Hence in all cases, it is the complete list of attributes.
    try
    {
      if (domain==null)
      {
        DN rdns = DN.decode(
            configuration.dn().getParent().getRDN().getAttributeValue(0).
              getNormalizedValue());
        domain = MultimasterReplication.findDomain(rdns, null);
      }

      this.isEnabled = configuration.isEnabled();
      domain.changeConfig(configuration.getECLInclude(),
          configuration.getECLIncludeForDeletes());
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }
    catch (Exception e)
    {
      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false);
    }
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      ExternalChangelogDomainCfg configuration,
      List<Message> unacceptableReasons)
  {
    return true;
  }
  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      ExternalChangelogDomainCfg configuration,
      List<Message> unacceptableReasons)
  {
    return true;
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      ExternalChangelogDomainCfg configuration,
      List<Message> unacceptableReasons)
  {
    return true;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      ExternalChangelogDomainCfg configuration)
  {
    // nothing to do
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
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
