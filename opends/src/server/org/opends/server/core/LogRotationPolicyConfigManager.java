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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;

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
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.config.ConfigException;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.StaticUtils.*;

import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * Initializes all the log rotation policies.
   *
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public void initializeLogRotationPolicyConfig()
      throws ConfigException, InitializationException
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

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      LogRotationPolicyCfg configuration,
      List<Message> unacceptableReasons)
  {
    return isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      LogRotationPolicyCfg configuration,
      List<Message> unacceptableReasons)
  {
    // TODO: Make sure nothing is using this policy before deleting it.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(LogRotationPolicyCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    try
    {
      RotationPolicy rotationPolicy = getRotationPolicy(config);

      DirectoryServer.registerRotationPolicy(config.dn(), rotationPolicy);
    }
    catch (ConfigException e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      messages.add(e.getMessageObject());
      resultCode = DirectoryServer.getServerErrorResultCode();
    } catch (Exception e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      messages.add(ERR_CONFIG_ROTATION_POLICY_CANNOT_CREATE_POLICY.get(
              String.valueOf(config.dn().toString()),
              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      LogRotationPolicyCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    RotationPolicy policy = DirectoryServer.getRotationPolicy(config.dn());
    if(policy != null)
    {
      DirectoryServer.deregisterRotationPolicy(config.dn());
    }
    else
    {
      // TODO: Add message and check for usage
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      LogRotationPolicyCfg configuration,
      List<Message> unacceptableReasons)
  {
    return isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      LogRotationPolicyCfg configuration)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    RotationPolicy policy =
        DirectoryServer.getRotationPolicy(configuration.dn());
    String className = configuration.getJavaClass();
    if(!className.equals(policy.getClass().getName()))
    {
      adminActionRequired = true;
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  private boolean isJavaClassAcceptable(LogRotationPolicyCfg config,
                                        List<Message> unacceptableReasons)
  {
    String className = config.getJavaClass();
    LogRotationPolicyCfgDefn d = LogRotationPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd =
        d.getJavaClassPropertyDefinition();
    // Load the class and cast it to a RotationPolicy.
    Class<? extends RotationPolicy> theClass;
    try {
      theClass = pd.loadClass(className, RotationPolicy.class);
      theClass.newInstance();
    } catch (Exception e) {
      Message message = ERR_CONFIG_ROTATION_POLICY_INVALID_CLASS.get(className,
                                  config.dn().toString(),
                                  String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }
    // Check that the implementation class implements the correct interface.
    try {
      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      theClass.getMethod("initializeLogRotationPolicy", config.definition()
          .getServerConfigurationClass());
    } catch (Exception e) {
      Message message = ERR_CONFIG_ROTATION_POLICY_INVALID_CLASS.get(className,
                                  config.dn().toString(),
                                  String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }
    // The class is valid as far as we can tell.
    return true;
  }

  private RotationPolicy getRotationPolicy(LogRotationPolicyCfg config)
      throws ConfigException {
    String className = config.getJavaClass();
    LogRotationPolicyCfgDefn d = LogRotationPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd =
        d.getJavaClassPropertyDefinition();
    // Load the class and cast it to a RotationPolicy.
    Class<? extends RotationPolicy> theClass;
    RotationPolicy rotationPolicy;
    try {
      theClass = pd.loadClass(className, RotationPolicy.class);
      rotationPolicy = theClass.newInstance();

      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod("initializeLogRotationPolicy",
                             config.definition().getServerConfigurationClass());
      method.invoke(rotationPolicy, config);
    }
    catch (InvocationTargetException ite)
    {
      // Rethrow the exceptions thrown be the invoked method.
      Throwable e = ite.getTargetException();
      Message message = ERR_CONFIG_ROTATION_POLICY_INVALID_CLASS.get(
          className, config.dn().toString(), stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    } catch (Exception e) {
      Message message = ERR_CONFIG_ROTATION_POLICY_INVALID_CLASS.get(
          className, config.dn().toString(), String.valueOf(e));
      throw new ConfigException(message, e);
    }

    // The connection handler has been successfully initialized.
    return rotationPolicy;
  }
}
