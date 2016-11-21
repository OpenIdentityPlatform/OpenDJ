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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.loggers.RotationPolicy;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.LogRotationPolicyCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.meta.LogRotationPolicyCfgDefn;
import org.forgerock.opendj.config.ClassPropertyDefinition;
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
    RootCfg root = serverContext.getRootConfig();
    root.addLogRotationPolicyAddListener(this);
    root.addLogRotationPolicyDeleteListener(this);

    for(String name : root.listLogRotationPolicies())
    {
      LogRotationPolicyCfg config = root.getLogRotationPolicy(name);
      RotationPolicy<LogRotationPolicyCfg> rotationPolicy = getRotationPolicy(config);
      DirectoryServer.registerRotationPolicy(config.dn(), rotationPolicy);
    }
  }

  @Override
  public boolean isConfigurationAddAcceptable(
      LogRotationPolicyCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
      LogRotationPolicyCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // TODO: Make sure nothing is using this policy before deleting it.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(LogRotationPolicyCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    try
    {
      RotationPolicy<LogRotationPolicyCfg> rotationPolicy = getRotationPolicy(config);
      DirectoryServer.registerRotationPolicy(config.dn(), rotationPolicy);
    }
    catch (ConfigException e) {
      logger.traceException(e);
      ccr.addMessage(e.getMessageObject());
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
    } catch (Exception e) {
      logger.traceException(e);

      ccr.addMessage(ERR_CONFIG_ROTATION_POLICY_CANNOT_CREATE_POLICY.get(config.dn(),
              stackTraceToSingleLineString(e)));
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
    }

    return ccr;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
      LogRotationPolicyCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    RotationPolicy<?> policy = DirectoryServer.getRotationPolicy(config.dn());
    if(policy != null)
    {
      DirectoryServer.deregisterRotationPolicy(config.dn());
    }
    else
    {
      // TODO: Add message and check for usage
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      LogRotationPolicyCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      LogRotationPolicyCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    RotationPolicy<?> policy = DirectoryServer.getRotationPolicy(configuration.dn());
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
      RotationPolicy<?> retentionPolicy = theClass.newInstance();
      // next line is here to ensure that eclipse does not remove the cast in the line above
      retentionPolicy.hashCode();
      return true;
    } catch (Exception e) {
      unacceptableReasons.add(
          ERR_CONFIG_ROTATION_POLICY_INVALID_CLASS.get(className, config.dn(), e));
      return false;
    }
  }

  private RotationPolicy<LogRotationPolicyCfg> getRotationPolicy(LogRotationPolicyCfg config)
      throws ConfigException {
    String className = config.getJavaClass();
    LogRotationPolicyCfgDefn d = LogRotationPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();
    try {
      Class<? extends RotationPolicy> theClass =
          pd.loadClass(className, RotationPolicy.class);
      RotationPolicy<LogRotationPolicyCfg> rotationPolicy = theClass.newInstance();
      rotationPolicy.initializeLogRotationPolicy(config);
      return rotationPolicy;
    } catch (Exception e) {
      LocalizableMessage message = ERR_CONFIG_ROTATION_POLICY_INVALID_CLASS.get(
          className, config.dn(), e);
      throw new ConfigException(message, e);
    }
  }
}
