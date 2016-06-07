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
package org.opends.server.core;

import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn.DisabledPrivilege;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn.InvalidAttributeSyntaxBehavior;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn.SingleStructuralObjectclassBehavior;
import org.forgerock.opendj.server.config.server.GlobalCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.loggers.CommonAudit;
import org.opends.server.types.*;

import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
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
    GlobalCfg globalConfig = serverContext.getRootConfig().getGlobalConfiguration();
    globalConfig.addChangeListener(this);


    // Validate any specified SMTP servers
    Set<String> smtpServers = globalConfig.getSMTPServer();
    if (smtpServers != null)
    {
      for (String server : smtpServers)
      {
        try
        {
          HostPort.valueOf(server, SMTP_DEFAULT_PORT);
        }
        catch (RuntimeException e)
        {
          LocalizableMessage message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
          throw new ConfigException(message, e);
        }
      }
    }

    applyGlobalConfiguration(globalConfig, serverContext);
  }

  /**
   * Applies the settings in the provided configuration to the Directory Server.
   *
   * @param  globalConfig  The configuration settings to be applied.
   */
  private static void applyGlobalConfiguration(final GlobalCfg globalConfig, final ServerContext serverContext)
      throws ConfigException
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

    // For tools, common audit may not be available
    CommonAudit commonAudit = serverContext.getCommonAudit();
    if (commonAudit != null)
    {
      commonAudit.setTrustTransactionIds(globalConfig.isTrustTransactionIds());
    }

    // Update the "new" schema with configuration changes if necessary
    try
    {
      final boolean allowMalformedNames = globalConfig.isAllowAttributeNameExceptions();
      serverContext.getSchema().updateSchemaOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS, allowMalformedNames);
    }
    catch (DirectoryException e)
    {
      throw new ConfigException(e.getMessageObject(), e);
    }
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
    List<Properties> mailServerProperties = new ArrayList<>();
    if (smtpServers != null && !smtpServers.isEmpty())
    {
      for (String smtpServer : smtpServers)
      {
        final Properties properties = new Properties();
        try
        {
          final HostPort hp = HostPort.valueOf(smtpServer, SMTP_DEFAULT_PORT);

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
    HashSet<Privilege> disabledPrivileges = new HashSet<>();
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
          HostPort.valueOf(server, SMTP_DEFAULT_PORT);
        }
        catch (RuntimeException e)
        {
          unacceptableReasons.add(ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server));
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

  @Override
  public ConfigChangeResult applyConfigurationChange(GlobalCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      applyGlobalConfiguration(configuration, serverContext);
    }
    catch (ConfigException e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
    }
    return ccr;
  }
}
