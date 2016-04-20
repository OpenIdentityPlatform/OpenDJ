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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.meta.RootDNCfgDefn;
import org.forgerock.opendj.server.config.server.RootDNCfg;
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

  @Override
  public boolean isConfigurationChangeAcceptable(RootDNCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // No special validation is required.
    return true;
  }

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
