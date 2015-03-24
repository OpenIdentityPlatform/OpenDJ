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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.core;

import java.util.List;

import org.opends.server.admin.std.server.LogRetentionPolicyCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.meta.LogRetentionPolicyCfgDefn;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.loggers.RetentionPolicy;
import org.forgerock.opendj.config.server.ConfigException;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a utility that will be used to manage the set of
 * log retention policies used in the Directory Server.  It will perform the
 * initialization when the server is starting, and then will manage any
 * additions, and removals of policies while the server is running.
 */
public class LogRetentionPolicyConfigManager implements
    ConfigurationAddListener<LogRetentionPolicyCfg>,
    ConfigurationDeleteListener<LogRetentionPolicyCfg>,
    ConfigurationChangeListener<LogRetentionPolicyCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final ServerContext serverContext;

  /**
   * Creates this log retention policy manager.
   *
   * @param serverContext
   *          The server context.
   */
  public LogRetentionPolicyConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Initializes all the log retention policies.
   *
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of performing
   *           the initialization as a result of the server configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not related to
   *           the server configuration.
   */
  public void initializeLogRetentionPolicyConfig() throws ConfigException, InitializationException
  {
    ServerManagementContext context = ServerManagementContext.getInstance();
    RootCfg root = context.getRootConfiguration();

    root.addLogRetentionPolicyAddListener(this);
    root.addLogRetentionPolicyDeleteListener(this);

    for(String name : root.listLogRetentionPolicies())
    {
      LogRetentionPolicyCfg config = root.getLogRetentionPolicy(name);

      RetentionPolicy RetentionPolicy = getRetentionPolicy(config);

      DirectoryServer.registerRetentionPolicy(config.dn(), RetentionPolicy);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAddAcceptable(
      LogRetentionPolicyCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationDeleteAcceptable(
      LogRetentionPolicyCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // TODO: Make sure nothing is using this policy before deleting it.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationAdd(LogRetentionPolicyCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    try
    {
      RetentionPolicy RetentionPolicy = getRetentionPolicy(config);

      DirectoryServer.registerRetentionPolicy(config.dn(), RetentionPolicy);
    }
    catch (ConfigException e) {
      logger.traceException(e);
      ccr.addMessage(e.getMessageObject());
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    } catch (Exception e) {
      logger.traceException(e);

      ccr.addMessage(ERR_CONFIG_RETENTION_POLICY_CANNOT_CREATE_POLICY.get(
          config.dn(),stackTraceToSingleLineString(e)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    }

    return ccr;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationDelete(
      LogRetentionPolicyCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    RetentionPolicy policy = DirectoryServer.getRetentionPolicy(config.dn());
    if(policy != null)
    {
      DirectoryServer.deregisterRetentionPolicy(config.dn());
    }
    else
    {
      // TODO: Add message and check for usage
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    }

    return ccr;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      LogRetentionPolicyCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      LogRetentionPolicyCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    RetentionPolicy policy =
        DirectoryServer.getRetentionPolicy(configuration.dn());
    String className = configuration.getJavaClass();
    if(!className.equals(policy.getClass().getName()))
    {
      ccr.setAdminActionRequired(true);
    }

    return ccr;
  }

  private boolean isJavaClassAcceptable(LogRetentionPolicyCfg config,
                                        List<LocalizableMessage> unacceptableReasons)
  {
    String className = config.getJavaClass();
    LogRetentionPolicyCfgDefn d = LogRetentionPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();
    try {
      Class<? extends RetentionPolicy> theClass =
          pd.loadClass(className, RetentionPolicy.class);
      // Explicitly cast to check that implementation implements the correct interface.
      RetentionPolicy retentionPolicy = theClass.newInstance();
      // next line is here to ensure that eclipse does not remove the cast in the line above
      retentionPolicy.hashCode();
      return true;
    } catch (Exception e) {
      unacceptableReasons.add(
          ERR_CONFIG_RETENTION_POLICY_INVALID_CLASS.get(className, config.dn(), e));
      return false;
    }
  }

  private RetentionPolicy getRetentionPolicy(LogRetentionPolicyCfg config)
      throws ConfigException {
    String className = config.getJavaClass();
    LogRetentionPolicyCfgDefn d = LogRetentionPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();
    try {
      Class<? extends RetentionPolicy> theClass =
          pd.loadClass(className, RetentionPolicy.class);
      RetentionPolicy retentionPolicy = theClass.newInstance();

      retentionPolicy.initializeLogRetentionPolicy(config);

      return retentionPolicy;
    } catch (Exception e) {
      LocalizableMessage message = ERR_CONFIG_RETENTION_POLICY_INVALID_CLASS.get(
          className, config.dn(), e);
      throw new ConfigException(message, e);
    }
  }
}

