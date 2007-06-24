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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.RootDNCfgDefn;
import org.opends.server.admin.std.server.RootDNCfg;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;



/**
 * This class defines a data structure that is used to handle changes to the set
 * of default root privileges.
 */
public class RootPrivilegeChangeListener
       implements ConfigurationChangeListener<RootDNCfg>
{
  // The set of privileges that will be given to root users by default.
  private Set<Privilege> defaultRootPrivileges;



  /**
   * Creates a new instance of this root privilege change listener.
   */
  public RootPrivilegeChangeListener()
  {
    defaultRootPrivileges = Privilege.getDefaultRootPrivileges();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(RootDNCfg configuration,
                      List<String> unacceptableReasons)
  {
    // No special validation is required.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(RootDNCfg configuration)
  {
    setDefaultRootPrivileges(configuration);
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
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

    HashSet<Privilege> privSet = new HashSet<Privilege>(configPrivSet.size());
    for (RootDNCfgDefn.DefaultRootPrivilegeName p : configPrivSet)
    {
      switch (p)
      {
        case BYPASS_ACL:
          privSet.add(Privilege.BYPASS_ACL);
          break;
        case MODIFY_ACL:
          privSet.add(Privilege.MODIFY_ACL);
          break;
        case CONFIG_READ:
          privSet.add(Privilege.CONFIG_READ);
          break;
        case CONFIG_WRITE:
          privSet.add(Privilege.CONFIG_WRITE);
          break;
        case JMX_READ:
          privSet.add(Privilege.JMX_READ);
          break;
        case JMX_WRITE:
          privSet.add(Privilege.JMX_WRITE);
          break;
        case JMX_NOTIFY:
          privSet.add(Privilege.JMX_NOTIFY);
          break;
        case LDIF_IMPORT:
          privSet.add(Privilege.LDIF_IMPORT);
          break;
        case LDIF_EXPORT:
          privSet.add(Privilege.LDIF_EXPORT);
          break;
        case BACKEND_BACKUP:
          privSet.add(Privilege.BACKEND_BACKUP);
          break;
        case BACKEND_RESTORE:
          privSet.add(Privilege.BACKEND_RESTORE);
          break;
        case SERVER_SHUTDOWN:
          privSet.add(Privilege.SERVER_SHUTDOWN);
          break;
        case SERVER_RESTART:
          privSet.add(Privilege.SERVER_RESTART);
          break;
        case PROXIED_AUTH:
          privSet.add(Privilege.PROXIED_AUTH);
          break;
        case DISCONNECT_CLIENT:
          privSet.add(Privilege.DISCONNECT_CLIENT);
          break;
        case CANCEL_REQUEST:
          privSet.add(Privilege.CANCEL_REQUEST);
          break;
        case PASSWORD_RESET:
          privSet.add(Privilege.PASSWORD_RESET);
          break;
        case DATA_SYNC:
          privSet.add(Privilege.DATA_SYNC);
          break;
        case UPDATE_SCHEMA:
          privSet.add(Privilege.UPDATE_SCHEMA);
          break;
        case PRIVILEGE_CHANGE:
          privSet.add(Privilege.PRIVILEGE_CHANGE);
          break;
        case UNINDEXED_SEARCH:
          privSet.add(Privilege.UNINDEXED_SEARCH);
          break;
      }
    }

    defaultRootPrivileges = privSet;
  }
}

