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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.loggers.RotationPolicy;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.LogRotationPolicyCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.meta.LogRotationPolicyCfgDefn;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a utility that will be used to manage the set of
 * log rotation policies used in the Directory Server.  It will perform the
 * initialization when the server is starting, and then will manage any
 * additions, and removals of policies while the server is running.
 */
public class LogRotationPolicyConfigManager implements
    ConfigurationAddListener<LogRotationPolicyCfg>,
    ConfigurationDeleteListener<LogRotationPolicyCfg>,
    ConfigurationChangeListener<LogRotationPolicyCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final ServerContext serverContext;

  /**
   * Creates this log rotation policy manager.
   *
   * @param serverContext
   *          The server context.
   */
  public LogRotationPolicyConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Initializes all the log rotation policies.
   *
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of performing
   *           the initialization as a result of the server configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not related to
   *           the server configuration.
   */
  public void initializeLogRotationPolicyConfig() throws ConfigException, InitializationException
  {
    ServerManagementContext context = ServerManagementContext.getInstance();
    RootCfg root = context.getRootConfiguration();

    root.addLogRotationPolicyAddListener(this);
    root.addLogRotationPolicyDeleteListener(this);

    for(String name : root.listLogRotationPolicies())
    {
      LogRotationPolicyCfg config = root.getLogRotationPolicy(name);

      RotationPolicy rotationPolicy = getRotationPolicy(config);

      DirectoryServer.registerRotationPolicy(config.dn(), rotationPolicy);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAddAcceptable(
      LogRotationPolicyCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationDeleteAcceptable(
      LogRotationPolicyCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // TODO: Make sure nothing is using this policy before deleting it.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationAdd(LogRotationPolicyCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    try
    {
      RotationPolicy rotationPolicy = getRotationPolicy(config);

      DirectoryServer.registerRotationPolicy(config.dn(), rotationPolicy);
    }
    catch (ConfigException e) {
      logger.traceException(e);
      ccr.addMessage(e.getMessageObject());
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    } catch (Exception e) {
      logger.traceException(e);

      ccr.addMessage(ERR_CONFIG_ROTATION_POLICY_CANNOT_CREATE_POLICY.get(config.dn(),
              stackTraceToSingleLineString(e)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    }

    return ccr;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationDelete(
      LogRotationPolicyCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    RotationPolicy policy = DirectoryServer.getRotationPolicy(config.dn());
    if(policy != null)
    {
      DirectoryServer.deregisterRotationPolicy(config.dn());
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
      LogRotationPolicyCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      LogRotationPolicyCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    RotationPolicy policy =
        DirectoryServer.getRotationPolicy(configuration.dn());
    String className = configuration.getJavaClass();
    if(!className.equals(policy.getClass().getName()))
    {
      ccr.setAdminActionRequired(true);
    }

    return ccr;
  }

  private boolean isJavaClassAcceptable(LogRotationPolicyCfg config,
                                        List<LocalizableMessage> unacceptableReasons)
  {
    String className = config.getJavaClass();
    LogRotationPolicyCfgDefn d = LogRotationPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();
    try {
      Class<? extends RotationPolicy> theClass =
          pd.loadClass(className, RotationPolicy.class);
      // Explicitly cast to check that implementation implements the correct interface.
      RotationPolicy retentionPolicy = theClass.newInstance();
      // next line is here to ensure that eclipse does not remove the cast in the line above
      retentionPolicy.hashCode();
      return true;
    } catch (Exception e) {
      unacceptableReasons.add(
          ERR_CONFIG_ROTATION_POLICY_INVALID_CLASS.get(className, config.dn(), e));
      return false;
    }
  }

  private RotationPolicy getRotationPolicy(LogRotationPolicyCfg config)
      throws ConfigException {
    String className = config.getJavaClass();
    LogRotationPolicyCfgDefn d = LogRotationPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();
    try {
      Class<? extends RotationPolicy> theClass =
          pd.loadClass(className, RotationPolicy.class);
      RotationPolicy rotationPolicy = theClass.newInstance();

      rotationPolicy.initializeLogRotationPolicy(config);

      return rotationPolicy;
    } catch (Exception e) {
      LocalizableMessage message = ERR_CONFIG_ROTATION_POLICY_INVALID_CLASS.get(
          className, config.dn(), e);
      throw new ConfigException(message, e);
    }
  }
}
