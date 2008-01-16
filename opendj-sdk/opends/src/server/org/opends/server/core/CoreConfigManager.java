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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.GlobalCfgDefn;
import org.opends.server.admin.std.meta.GlobalCfgDefn.WorkflowConfigurationMode;
import org.opends.server.admin.std.server.GlobalCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.config.ConfigException;
import org.opends.server.types.*;

import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a utility that will be used to manage the set of core
 * configuration attributes defined in the Directory Server.  These
 * configuration attributes appear in the "cn=config" configuration entry.
 */
public class CoreConfigManager
       implements ConfigurationChangeListener<GlobalCfg>
{
  /**
   * Creates a new instance of this core config manager.
   */
  public CoreConfigManager()
  {
    // No implementation is required.
  }



  /**
   * Initializes the Directory Server's core configuration.  This should only be
   * called at server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the identity
   *                           mapper initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the identity mappers that is not related
   *                                   to the server configuration.
   */
  public void initializeCoreConfig()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Get the global configuration and register with it as a change listener.
    GlobalCfg globalConfig = rootConfiguration.getGlobalConfiguration();
    globalConfig.addChangeListener(this);


    // If there are any STMP servers specified, then make sure that if the value
    // contains a colon that the portion after it is an integer between 1 and
    // 65535.
    Set<String> smtpServers = globalConfig.getSMTPServer();
    if (smtpServers != null)
    {
      for (String server : smtpServers)
      {
        int colonPos = server.indexOf(':');
        if ((colonPos == 0) || (colonPos == (server.length()-1)))
        {
          Message message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
          throw new ConfigException(message);
        }
        else if (colonPos > 0)
        {
          try
          {
            int port = Integer.parseInt(server.substring(colonPos+1));
            if ((port < 1) || (port > 65535))
            {
              Message message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
              throw new ConfigException(message);
            }
          }
          catch (Exception e)
          {
            Message message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
            throw new ConfigException(message, e);
          }
        }
      }
    }


    // Apply the configuration to the server.
    applyGlobalConfiguration(globalConfig);
  }



  /**
   * Applies the settings in the provided configuration to the Directory Server.
   *
   * @param  globalConfig  The configuration settings to be applied.
   */
  private static void applyGlobalConfiguration(GlobalCfg globalConfig)
  {
    DirectoryServer.setCheckSchema(globalConfig.isCheckSchema());

    DirectoryServer.setDefaultPasswordPolicyDN(
         globalConfig.getDefaultPasswordPolicyDN());

    DirectoryServer.setAddMissingRDNAttributes(
         globalConfig.isAddMissingRDNAttributes());

    DirectoryServer.setAllowAttributeNameExceptions(
         globalConfig.isAllowAttributeNameExceptions());

    switch (globalConfig.getInvalidAttributeSyntaxBehavior())
    {
      case ACCEPT:
        DirectoryServer.setSyntaxEnforcementPolicy(AcceptRejectWarn.ACCEPT);
        break;
      case WARN:
        DirectoryServer.setSyntaxEnforcementPolicy(AcceptRejectWarn.WARN);
        break;
      case REJECT:
      default:
        DirectoryServer.setSyntaxEnforcementPolicy(AcceptRejectWarn.REJECT);
        break;
    }

    DirectoryServer.setServerErrorResultCode(
         ResultCode.valueOf(globalConfig.getServerErrorResultCode()));

    switch (globalConfig.getSingleStructuralObjectclassBehavior())
    {
      case ACCEPT:
        DirectoryServer.setSingleStructuralObjectClassPolicy(
             AcceptRejectWarn.ACCEPT);
        break;
      case WARN:
        DirectoryServer.setSingleStructuralObjectClassPolicy(
             AcceptRejectWarn.WARN);
        break;
      case REJECT:
      default:
        DirectoryServer.setSingleStructuralObjectClassPolicy(
             AcceptRejectWarn.REJECT);
        break;
    }

    DirectoryServer.setNotifyAbandonedOperations(
         globalConfig.isNotifyAbandonedOperations());

    DirectoryServer.setSizeLimit(globalConfig.getSizeLimit());

    DirectoryServer.setTimeLimit((int) globalConfig.getTimeLimit());

    DirectoryServer.setProxiedAuthorizationIdentityMapperDN(
         globalConfig.getProxiedAuthorizationIdentityMapperDN());

    switch (globalConfig.getWritabilityMode())
    {
      case ENABLED:
        DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
        break;
      case INTERNAL_ONLY:
        DirectoryServer.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);
        break;
      case DISABLED:
      default:
        DirectoryServer.setWritabilityMode(WritabilityMode.DISABLED);
        break;
    }

    DirectoryServer.setRejectUnauthenticatedRequests(
         globalConfig.isRejectUnauthenticatedRequests());

    DirectoryServer.setBindWithDNRequiresPassword(
         globalConfig.isBindWithDNRequiresPassword());

    DirectoryServer.setLookthroughLimit(globalConfig.getLookthroughLimit());


    ArrayList<Properties> mailServerProperties = new ArrayList<Properties>();
    Set<String> smtpServers = globalConfig.getSMTPServer();
    if ((smtpServers != null) && (! smtpServers.isEmpty()))
    {
      for (String smtpServer : smtpServers)
      {
        int colonPos = smtpServer.indexOf(':');
        if (colonPos > 0)
        {
          String smtpHost = smtpServer.substring(0, colonPos);
          String smtpPort = smtpServer.substring(colonPos+1);

          Properties properties = new Properties();
          properties.setProperty(SMTP_PROPERTY_HOST, smtpHost);
          properties.setProperty(SMTP_PROPERTY_PORT, smtpPort);
          mailServerProperties.add(properties);
        }
        else
        {
          Properties properties = new Properties();
          properties.setProperty(SMTP_PROPERTY_HOST, smtpServer);
          mailServerProperties.add(properties);
        }
      }
    }
    DirectoryServer.setMailServerPropertySets(mailServerProperties);

    DirectoryServer.setAllowedTasks(globalConfig.getAllowedTask());


    HashSet<Privilege> disabledPrivileges = new HashSet<Privilege>();
    Set<GlobalCfgDefn.DisabledPrivilege> configuredDisabledPrivs =
         globalConfig.getDisabledPrivilege();
    if (configuredDisabledPrivs != null)
    {
      for (GlobalCfgDefn.DisabledPrivilege p : configuredDisabledPrivs)
      {
        switch (p)
        {
          case BACKEND_BACKUP:
            disabledPrivileges.add(Privilege.BACKEND_BACKUP);
            break;
          case BACKEND_RESTORE:
            disabledPrivileges.add(Privilege.BACKEND_RESTORE);
            break;
          case BYPASS_ACL:
            disabledPrivileges.add(Privilege.BYPASS_ACL);
            break;
          case CANCEL_REQUEST:
            disabledPrivileges.add(Privilege.CANCEL_REQUEST);
            break;
          case CONFIG_READ:
            disabledPrivileges.add(Privilege.CONFIG_READ);
            break;
          case CONFIG_WRITE:
            disabledPrivileges.add(Privilege.CONFIG_WRITE);
            break;
          case DATA_SYNC:
            disabledPrivileges.add(Privilege.DATA_SYNC);
            break;
          case DISCONNECT_CLIENT:
            disabledPrivileges.add(Privilege.DISCONNECT_CLIENT);
            break;
          case JMX_NOTIFY:
            disabledPrivileges.add(Privilege.JMX_NOTIFY);
            break;
          case JMX_READ:
            disabledPrivileges.add(Privilege.JMX_READ);
            break;
          case JMX_WRITE:
            disabledPrivileges.add(Privilege.JMX_WRITE);
            break;
          case LDIF_EXPORT:
            disabledPrivileges.add(Privilege.LDIF_EXPORT);
            break;
          case LDIF_IMPORT:
            disabledPrivileges.add(Privilege.LDIF_IMPORT);
            break;
          case MODIFY_ACL:
            disabledPrivileges.add(Privilege.MODIFY_ACL);
            break;
          case PASSWORD_RESET:
            disabledPrivileges.add(Privilege.PASSWORD_RESET);
            break;
          case PRIVILEGE_CHANGE:
            disabledPrivileges.add(Privilege.PRIVILEGE_CHANGE);
            break;
          case PROXIED_AUTH:
            disabledPrivileges.add(Privilege.PROXIED_AUTH);
            break;
          case SERVER_RESTART:
            disabledPrivileges.add(Privilege.SERVER_RESTART);
            break;
          case SERVER_SHUTDOWN:
            disabledPrivileges.add(Privilege.SERVER_SHUTDOWN);
            break;
          case UNINDEXED_SEARCH:
            disabledPrivileges.add(Privilege.UNINDEXED_SEARCH);
            break;
          case UPDATE_SCHEMA:
            disabledPrivileges.add(Privilege.UPDATE_SCHEMA);
            break;
        }
      }
    }
    DirectoryServer.setDisabledPrivileges(disabledPrivileges);

    DirectoryServer.setReturnBindErrorMessages(
         globalConfig.isReturnBindErrorMessages());

    DirectoryServer.setIdleTimeLimit(globalConfig.getIdleTimeLimit());

    DirectoryServer.setSaveConfigOnSuccessfulStartup(
         globalConfig.isSaveConfigOnSuccessfulStartup());

    // If the workflow configuration mode has changed then reconfigure
    // the workflows-only if the server is running. If the server is not
    // running (ie. the server is starting up) simply update the workflow
    // configuration mode as the workflow configuration is processed
    // elsewhere.
    WorkflowConfigurationMode oldMode =
      DirectoryServer.getWorkflowConfigurationMode();
    WorkflowConfigurationMode newMode =
      globalConfig.getWorkflowConfigurationMode();
    if (DirectoryServer.isRunning())
    {
      DirectoryServer.reconfigureWorkflows(oldMode, newMode);
    }
    else
    {
      DirectoryServer.setWorkflowConfigurationMode(newMode);
    }

    AbstractOperation.setUseNanoTime(globalConfig.getEtimeResolution() ==
      GlobalCfgDefn.EtimeResolution.NANO_SECONDS);
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(GlobalCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;

    Set<String> smtpServers = configuration.getSMTPServer();
    if (smtpServers != null)
    {
      for (String server : smtpServers)
      {
        int colonPos = server.indexOf(':');
        if ((colonPos == 0) || (colonPos == (server.length()-1)))
        {
          Message message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
          unacceptableReasons.add(message);
          configAcceptable = false;
        }
        else if (colonPos > 0)
        {
          try
          {
            int port = Integer.parseInt(server.substring(colonPos+1));
            if ((port < 1) || (port > 65535))
            {
              Message message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
              unacceptableReasons.add(message);
              configAcceptable = false;
            }
          }
          catch (Exception e)
          {
            Message message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
            unacceptableReasons.add(message);
            configAcceptable = false;
          }
        }
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(GlobalCfg configuration)
  {
    ResultCode         resultCode          = ResultCode.SUCCESS;
    boolean            adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    applyGlobalConfiguration(configuration);

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

