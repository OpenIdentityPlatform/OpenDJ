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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.RootDNCfgDefn;
import org.opends.server.admin.std.server.RootDNCfg;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.Privilege;

/**
 * This class defines a data structure that is used to handle changes to the set
 * of default root privileges.
 */
public class RootPrivilegeChangeListener
       implements ConfigurationChangeListener<RootDNCfg>
{
  /** The set of privileges that will be given to root users by default. */
  private Set<Privilege> defaultRootPrivileges;

  /** Creates a new instance of this root privilege change listener. */
  public RootPrivilegeChangeListener()
  {
    defaultRootPrivileges = Privilege.getDefaultRootPrivileges();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(RootDNCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // No special validation is required.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(RootDNCfg configuration)
  {
    setDefaultRootPrivileges(configuration);
    return new ConfigChangeResult();
  }

  /**
   * Retrieves the set of privileges that will be automatically granted to root
   * users.
   *
   * @return  The set of privileges that will be automatically granted to root
   *          users.
   */
  public Set<Privilege> getDefaultRootPrivileges()
  {
    return defaultRootPrivileges;
  }



  /**
   * Specifies the set of privileges that will be automatically granted to root
   * users.
   *
   * @param  configuration  The configuration object that specifies the set of
   *                        privileges that will be automatically granted to
   *                        root users.
   */
  void setDefaultRootPrivileges(RootDNCfg configuration)
  {
    Set<RootDNCfgDefn.DefaultRootPrivilegeName> configPrivSet =
         configuration.getDefaultRootPrivilegeName();

    HashSet<Privilege> privSet = new HashSet<>(configPrivSet.size());
    for (RootDNCfgDefn.DefaultRootPrivilegeName p : configPrivSet)
    {
        privSet.add(Privilege.privilegeForName(p.toString()));
    }

    defaultRootPrivileges = privSet;
  }
}
