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
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaOptions;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.GlobalCfgDefn;
import org.opends.server.admin.std.meta.GlobalCfgDefn.DisabledPrivilege;
import org.opends.server.admin.std.meta.GlobalCfgDefn.InvalidAttributeSyntaxBehavior;
import org.opends.server.admin.std.meta.GlobalCfgDefn.SingleStructuralObjectclassBehavior;
import org.opends.server.admin.std.server.GlobalCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.schema.SchemaUpdater;
import org.opends.server.types.*;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines a utility that will be used to manage the set of core
 * configuration attributes defined in the Directory Server.  These
 * configuration attributes appear in the "cn=config" configuration entry.
 */
public class CoreConfigManager
       implements ConfigurationChangeListener<GlobalCfg>
{
  private final ServerContext serverContext;

  /**
   * Creates a new instance of this core config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public CoreConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Initializes the Directory Server's core configuration. This should only be
   * called at server startup.
   *
   * @throws ConfigException
   *           If a configuration problem causes the identity mapper
   *           initialization process to fail.
   * @throws InitializationException
   *           If a problem occurs while initializing the identity mappers that
   *           is not related to the server configuration.
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
        try
        {
          // validate provided string
          HostPort.valueOf(server);
        }
        catch (RuntimeException e)
        {
          LocalizableMessage message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
          throw new ConfigException(message, e);
        }
      }
    }


    // Apply the configuration to the server.
    applyGlobalConfiguration(globalConfig, serverContext);
  }



  /**
   * Applies the settings in the provided configuration to the Directory Server.
   *
   * @param  globalConfig  The configuration settings to be applied.
   */
  private static void applyGlobalConfiguration(GlobalCfg globalConfig, ServerContext serverContext)
  {
    setCheckSchema(globalConfig.isCheckSchema());
    setDefaultPasswordPolicyDN(globalConfig.getDefaultPasswordPolicyDN());
    setAddMissingRDNAttributes(globalConfig.isAddMissingRDNAttributes());
    setAllowAttributeNameExceptions(globalConfig.isAllowAttributeNameExceptions());
    setSyntaxEnforcementPolicy(convert(globalConfig.getInvalidAttributeSyntaxBehavior()));
    setServerErrorResultCode(ResultCode.valueOf(globalConfig.getServerErrorResultCode()));
    setSingleStructuralObjectClassPolicy(convert(globalConfig.getSingleStructuralObjectclassBehavior()));

    setNotifyAbandonedOperations(globalConfig.isNotifyAbandonedOperations());
    setSizeLimit(globalConfig.getSizeLimit());
    setTimeLimit((int) globalConfig.getTimeLimit());
    setProxiedAuthorizationIdentityMapperDN(globalConfig.getProxiedAuthorizationIdentityMapperDN());
    setWritabilityMode(convert(globalConfig.getWritabilityMode()));
    setRejectUnauthenticatedRequests(globalConfig.isRejectUnauthenticatedRequests());
    setBindWithDNRequiresPassword(globalConfig.isBindWithDNRequiresPassword());
    setLookthroughLimit(globalConfig.getLookthroughLimit());

    setMailServerPropertySets(getMailServerProperties(globalConfig.getSMTPServer()));
    setAllowedTasks(globalConfig.getAllowedTask());
    setDisabledPrivileges(convert(globalConfig.getDisabledPrivilege()));
    setReturnBindErrorMessages(globalConfig.isReturnBindErrorMessages());
    setIdleTimeLimit(globalConfig.getIdleTimeLimit());
    setSaveConfigOnSuccessfulStartup(globalConfig.isSaveConfigOnSuccessfulStartup());

    setUseNanoTime(globalConfig.getEtimeResolution() == GlobalCfgDefn.EtimeResolution.NANOSECONDS);
    setMaxAllowedConnections(globalConfig.getMaxAllowedClientConnections());
    setMaxPersistentSearchLimit(globalConfig.getMaxPsearches());
    setMaxInternalBufferSize((int) globalConfig.getMaxInternalBufferSize());

    // Update the "new" schema with configuration changes
    SchemaUpdater schemaUpdater = serverContext.getSchemaUpdater();
    SchemaBuilder schemaBuilder = schemaUpdater.getSchemaBuilder();
    boolean allowMalformedNames = globalConfig.isAllowAttributeNameExceptions();
    schemaBuilder.setOption(SchemaOptions.ALLOW_MALFORMED_NAMES_AND_OPTIONS, allowMalformedNames);
    Schema schema = schemaBuilder.toSchema();
    if (!globalConfig.isCheckSchema())
    {
      schema = schema.asNonStrictSchema();
    }
    schemaUpdater.updateSchema(schema);
  }

  private static AcceptRejectWarn convert(InvalidAttributeSyntaxBehavior invalidAttributeSyntaxBehavior)
  {
    switch (invalidAttributeSyntaxBehavior)
    {
    case ACCEPT:
      return AcceptRejectWarn.ACCEPT;
    case WARN:
      return AcceptRejectWarn.WARN;
    case REJECT:
    default:
      return AcceptRejectWarn.REJECT;
    }
  }

  private static AcceptRejectWarn convert(SingleStructuralObjectclassBehavior singleStructuralObjectclassBehavior)
  {
    switch (singleStructuralObjectclassBehavior)
    {
    case ACCEPT:
      return AcceptRejectWarn.ACCEPT;
    case WARN:
      return AcceptRejectWarn.WARN;
    case REJECT:
    default:
      return AcceptRejectWarn.REJECT;
    }
  }

  private static WritabilityMode convert(GlobalCfgDefn.WritabilityMode writabilityMode)
  {
    switch (writabilityMode)
    {
    case ENABLED:
      return WritabilityMode.ENABLED;
    case INTERNAL_ONLY:
      return WritabilityMode.INTERNAL_ONLY;
    case DISABLED:
    default:
      return WritabilityMode.DISABLED;
    }
  }

  private static List<Properties> getMailServerProperties(Set<String> smtpServers)
  {
    List<Properties> mailServerProperties = new ArrayList<Properties>();
    if (smtpServers != null && !smtpServers.isEmpty())
    {
      for (String smtpServer : smtpServers)
      {
        final Properties properties = new Properties();
        try
        {
          final HostPort hp = HostPort.valueOf(smtpServer);

          properties.setProperty(SMTP_PROPERTY_HOST, hp.getHost());
          properties.setProperty(SMTP_PROPERTY_PORT,
              String.valueOf(hp.getPort()));
          properties.setProperty(SMTP_PROPERTY_CONNECTION_TIMEOUT,
              SMTP_DEFAULT_TIMEOUT_VALUE);
          properties.setProperty(SMTP_PROPERTY_IO_TIMEOUT,
              SMTP_DEFAULT_TIMEOUT_VALUE);
        }
        catch (RuntimeException e)
        {
          // no valid port provided
          properties.setProperty(SMTP_PROPERTY_HOST, smtpServer);
        }
        mailServerProperties.add(properties);
      }
    }
    return mailServerProperties;
  }

  private static HashSet<Privilege> convert(Set<DisabledPrivilege> configuredDisabledPrivs)
  {
    HashSet<Privilege> disabledPrivileges = new HashSet<Privilege>();
    if (configuredDisabledPrivs != null)
    {
      for (DisabledPrivilege p : configuredDisabledPrivs)
      {
        final Privilege privilege = convert(p);
        if (privilege != null)
        {
          disabledPrivileges.add(privilege);
        }
      }
    }
    return disabledPrivileges;
  }

  private static Privilege convert(DisabledPrivilege privilege)
  {
    switch (privilege)
    {
      case BACKEND_BACKUP:
        return Privilege.BACKEND_BACKUP;
      case BACKEND_RESTORE:
        return Privilege.BACKEND_RESTORE;
      case BYPASS_ACL:
        return Privilege.BYPASS_ACL;
      case CANCEL_REQUEST:
        return Privilege.CANCEL_REQUEST;
      case CONFIG_READ:
        return Privilege.CONFIG_READ;
      case CONFIG_WRITE:
        return Privilege.CONFIG_WRITE;
      case DATA_SYNC:
        return Privilege.DATA_SYNC;
      case DISCONNECT_CLIENT:
        return Privilege.DISCONNECT_CLIENT;
      case JMX_NOTIFY:
        return Privilege.JMX_NOTIFY;
      case JMX_READ:
        return Privilege.JMX_READ;
      case JMX_WRITE:
        return Privilege.JMX_WRITE;
      case LDIF_EXPORT:
        return Privilege.LDIF_EXPORT;
      case LDIF_IMPORT:
        return Privilege.LDIF_IMPORT;
      case MODIFY_ACL:
        return Privilege.MODIFY_ACL;
      case PASSWORD_RESET:
        return Privilege.PASSWORD_RESET;
      case PRIVILEGE_CHANGE:
        return Privilege.PRIVILEGE_CHANGE;
      case PROXIED_AUTH:
        return Privilege.PROXIED_AUTH;
      case SERVER_RESTART:
        return Privilege.SERVER_RESTART;
      case SERVER_SHUTDOWN:
        return Privilege.SERVER_SHUTDOWN;
      case UNINDEXED_SEARCH:
        return Privilege.UNINDEXED_SEARCH;
      case UPDATE_SCHEMA:
        return Privilege.UPDATE_SCHEMA;
      case SUBENTRY_WRITE:
        return Privilege.SUBENTRY_WRITE;
      default:
        return null;
    }
  }


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(GlobalCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;

    Set<String> smtpServers = configuration.getSMTPServer();
    if (smtpServers != null)
    {
      for (String server : smtpServers)
      {
        try
        {
          // validate provided string
          HostPort.valueOf(server);
        }
        catch (RuntimeException e)
        {
          LocalizableMessage message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
          unacceptableReasons.add(message);
          configAcceptable = false;
        }
      }
    }

    // Ensure that the default password policy always points to a password
    // policy and not another type of authentication policy.
    DN defaultPasswordPolicyDN = configuration.getDefaultPasswordPolicyDN();
    AuthenticationPolicy policy = DirectoryServer
        .getAuthenticationPolicy(defaultPasswordPolicyDN);
    if (!policy.isPasswordPolicy())
    {
      LocalizableMessage message =
        ERR_CONFIG_PWPOLICY_CANNOT_CHANGE_DEFAULT_POLICY_WRONG_TYPE
          .get(configuration.getDefaultPasswordPolicy());
      unacceptableReasons.add(message);
      configAcceptable = false;
    }

    return configAcceptable;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(GlobalCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    applyGlobalConfiguration(configuration, serverContext);

    return ccr;
  }
}

