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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.RootDNCfg;
import org.opends.server.admin.std.server.RootDNUserCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ConfigMessages.*;



/**
 * This class defines a utility that will be used to manage the set of root
 * users defined in the Directory Server.  It will handle both the
 * "cn=Root DNs,cn=config" entry itself (through the root privilege change
 * listener), and all of its children.
 */
public class RootDNConfigManager
       implements ConfigurationChangeListener<RootDNUserCfg>,
                  ConfigurationAddListener<RootDNUserCfg>,
                  ConfigurationDeleteListener<RootDNUserCfg>

{
  // A mapping between the actual root DNs and their alternate bind DNs.
  private ConcurrentHashMap<DN,HashSet<DN>> alternateBindDNs;

  // The root privilege change listener that will handle changes to the
  // "cn=Root DNs,cn=config" entry itself.
  private RootPrivilegeChangeListener rootPrivilegeChangeListener;



  /**
   * Creates a new instance of this root DN config manager.
   */
  public RootDNConfigManager()
  {
    alternateBindDNs = new ConcurrentHashMap<DN,HashSet<DN>>();
    rootPrivilegeChangeListener = new RootPrivilegeChangeListener();
  }



  /**
   * Initializes all of the root users currently defined in the Directory Server
   * configuration, as well as the set of privileges that root users will
   * inherit by default.
   *
   * @throws  ConfigException  If a configuration problem causes the identity
   *                           mapper initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the identity mappers that is not related
   *                                   to the server configuration.
   */
  public void initializeRootDNs()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Get the root DN configuration object, use it to set the default root
    // privileges, and register a change listener for it.
    RootDNCfg rootDNCfg = rootConfiguration.getRootDN();
    rootPrivilegeChangeListener.setDefaultRootPrivileges(rootDNCfg);
    rootDNCfg.addChangeListener(rootPrivilegeChangeListener);


    // Register as an add and delete listener for new root DN users.
    rootDNCfg.addRootDNUserAddListener(this);
    rootDNCfg.addRootDNUserDeleteListener(this);


    // Get the set of root users defined below "cn=Root DNs,cn=config".  For
    // each one, register as a change listener, and get the set of alternate
    // bind DNs.
    for (String name : rootDNCfg.listRootDNUsers())
    {
      RootDNUserCfg rootUserCfg = rootDNCfg.getRootDNUser(name);
      rootUserCfg.addChangeListener(this);
      DirectoryServer.registerRootDN(rootUserCfg.dn());

      HashSet<DN> altBindDNs = new HashSet<DN>();
      for (DN alternateBindDN : rootUserCfg.getAlternateBindDN())
      {
        try
        {
          altBindDNs.add(alternateBindDN);
          DirectoryServer.registerAlternateRootDN(rootUserCfg.dn(),
                                                  alternateBindDN);
        }
        catch (DirectoryException de)
        {
          throw new InitializationException(de.getMessageObject());
        }
      }

      alternateBindDNs.put(rootUserCfg.dn(), altBindDNs);
    }
  }



  /**
   * Retrieves the set of privileges that will be granted to root users by
   * default.
   *
   * @return  The set of privileges that will be granted to root users by
   *          default.
   */
  public Set<Privilege> getRootPrivileges()
  {
    return rootPrivilegeChangeListener.getDefaultRootPrivileges();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(RootDNUserCfg configuration,
                                              List<Message> unacceptableReasons)
  {
    // The new root user must not have an alternate bind DN that is already
    // in use.
    boolean configAcceptable = true;
    for (DN altBindDN : configuration.getAlternateBindDN())
    {
      DN existingRootDN = DirectoryServer.getActualRootBindDN(altBindDN);
      if (existingRootDN != null)
      {

        Message message = ERR_CONFIG_ROOTDN_CONFLICTING_MAPPING.get(
                String.valueOf(altBindDN),
                String.valueOf(configuration.dn()),
                String.valueOf(existingRootDN));
        unacceptableReasons.add(message);

        configAcceptable = false;
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(RootDNUserCfg configuration)
  {
    configuration.addChangeListener(this);

    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    HashSet<DN> altBindDNs = new HashSet<DN>();
    for (DN altBindDN : configuration.getAlternateBindDN())
    {
      try
      {
        DirectoryServer.registerAlternateRootDN(configuration.dn(), altBindDN);
        altBindDNs.add(altBindDN);
      }
      catch (DirectoryException de)
      {
        // This shouldn't happen, since the set of DNs should have already been
        // validated.
        resultCode = DirectoryServer.getServerErrorResultCode();
        messages.add(de.getMessageObject());

        for (DN dn : altBindDNs)
        {
          DirectoryServer.deregisterAlternateRootBindDN(dn);
        }
        break;
      }
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      DirectoryServer.registerRootDN(configuration.dn());
      alternateBindDNs.put(configuration.dn(), altBindDNs);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(RootDNUserCfg configuration,
                      List<Message> unacceptableReasons)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 RootDNUserCfg configuration)
  {
    DirectoryServer.deregisterRootDN(configuration.dn());
    configuration.removeChangeListener(this);

    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    HashSet<DN> altBindDNs = alternateBindDNs.remove(configuration.dn());
    if (altBindDNs != null)
    {
      for (DN dn : altBindDNs)
      {
        DirectoryServer.deregisterAlternateRootBindDN(dn);
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(RootDNUserCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // There must not be any new alternate bind DNs that are already in use by
    // other root users.
    for (DN altBindDN: configuration.getAlternateBindDN())
    {
      DN existingRootDN = DirectoryServer.getActualRootBindDN(altBindDN);
      if ((existingRootDN != null) &&
          (! existingRootDN.equals(configuration.dn())))
      {
        Message message = ERR_CONFIG_ROOTDN_CONFLICTING_MAPPING.get(
                String.valueOf(altBindDN),
                String.valueOf(configuration.dn()),
                String.valueOf(existingRootDN));
        unacceptableReasons.add(message);

        configAcceptable = false;
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 RootDNUserCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    HashSet<DN> setDNs = new HashSet<DN>();
    HashSet<DN> addDNs = new HashSet<DN>();
    HashSet<DN> delDNs =
         new HashSet<DN>(alternateBindDNs.get(configuration.dn()));

    for (DN altBindDN : configuration.getAlternateBindDN())
    {
      setDNs.add(altBindDN);

      if (! delDNs.remove(altBindDN))
      {
        addDNs.add(altBindDN);
      }
    }

    for (DN dn : delDNs)
    {
      DirectoryServer.deregisterAlternateRootBindDN(dn);
    }

    HashSet<DN> addedDNs = new HashSet<DN>(addDNs.size());
    for (DN dn : addDNs)
    {
      try
      {
        DirectoryServer.registerAlternateRootDN(configuration.dn(), dn);
        addedDNs.add(dn);
      }
      catch (DirectoryException de)
      {
        // This shouldn't happen, since the set of DNs should have already been
        // validated.
        resultCode = DirectoryServer.getServerErrorResultCode();
        messages.add(de.getMessageObject());

        for (DN addedDN : addedDNs)
        {
          DirectoryServer.deregisterAlternateRootBindDN(addedDN);
        }

        for (DN deletedDN : delDNs)
        {
          try
          {
            DirectoryServer.registerAlternateRootDN(configuration.dn(),
                                                    deletedDN);
          }
          catch (Exception e)
          {
            // This should also never happen.
            alternateBindDNs.get(configuration.dn()).remove(deletedDN);
          }
        }
      }
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      alternateBindDNs.put(configuration.dn(), setDNs);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

