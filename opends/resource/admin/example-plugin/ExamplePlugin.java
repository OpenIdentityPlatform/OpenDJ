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
package com.example.opends;

import static org.opends.server.loggers.ErrorLogger.logError;

import java.util.List;
import java.util.Set;

import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.StartupPluginResult;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;

import org.opends.server.types.ResultCode;
import org.opends.server.types.InitializationException;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.messages.Message;
import org.opends.messages.Category;
import org.opends.messages.Severity;

import com.example.opends.server.ExamplePluginCfg;

/**
 * The example plugin implementation class. This plugin will output
 * the configured message to the error log during server start up.
 */
public class ExamplePlugin extends
  DirectoryServerPlugin<ExamplePluginCfg> implements
  ConfigurationChangeListener<ExamplePluginCfg> {

  // The current configuration.
  private ExamplePluginCfg config;



  /**
   * Default constructor.
   */
  public ExamplePlugin() {
    super();
  }


  /**
   * Performs any initialization necessary for this plugin.  This will
   * be called as soon as the plugin has been loaded and before it is
   * registered with the server.
   *
   * @param  pluginTypes    The set of plugin types that indicate the
   *                        ways in which this plugin will be invoked.
   * @param  configuration  The configuration for this plugin.
   *
   * @throws  ConfigException  If the provided entry does not contain
   *                           a valid configuration for this plugin.
   *
   * @throws  InitializationException  If a problem occurs while
   *                                   initializing the plugin that is
   *                                   not related to the server
   *                                   configuration.
   */
  @Override()
  public void initializePlugin(Set<PluginType> pluginTypes,
      ExamplePluginCfg configuration)
      throws ConfigException, InitializationException {
    // This plugin may only be used as a server startup plugin.
    for (PluginType t : pluginTypes) {
      switch (t) {
      case STARTUP:
        // This is fine.
        break;
      default:
        throw new ConfigException(Message.raw("Invalid plugin type " + t
            + " for the example plugin."));
      }
    }

    // Register change listeners. These are not really necessary for
    // this plugin since it is only used during server start-up.
    configuration.addExampleChangeListener(this);

    // Save the configuration.
    this.config = configuration;
  }



  /**
   * Performs any processing that should be done when the Directory
   * Server is in the process of starting.  This method will be called
   * after virtually all other initialization has been performed but
   * before the connection handlers are started.
   *
   * @return  The result of the startup plugin processing.
   */
  @Override
  public StartupPluginResult doStartup() {
    // Log the provided message.
    logError(Message.raw(Category.CONFIG, Severity.NOTICE,
        "Example plugin message '" + config.getMessage() + "'."));
    return StartupPluginResult.SUCCESS;
  }



  /**
   * Applies the configuration changes to this change listener.
   *
   * @param config
   *          The new configuration containing the changes.
   * @return Returns information about the result of changing the
   *         configuration.
   */
  public ConfigChangeResult applyConfigurationChange(
      ExamplePluginCfg config) {
    // The new configuration has already been validated.

    // Log a message to say that the configuration has changed. This
    // isn't necessary, but we'll do it just to show that the change
    // has taken effect.
    logError(Message.raw(Category.CONFIG, Severity.NOTICE,
        "Example plugin message has been changed from '"
            + this.config.getMessage() + "' to '"
            + config.getMessage() + "'"));

    // Update the configuration.
    this.config = config;

    // Update was successfull, no restart required.
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * Indicates whether the proposed change to the configuration is
   * acceptable to this change listener.
   *
   * @param config
   *          The new configuration containing the changes.
   * @param messages
   *          A list that can be used to hold messages about why the
   *          provided configuration is not acceptable.
   * @return Returns <code>true</code> if the proposed change is
   *         acceptable, or <code>false</code> if it is not.
   */
  public boolean isConfigurationChangeAcceptable(
      ExamplePluginCfg config, List<Message> messages) {
    // The only thing that can be validated here is the plugin's
    // message. However, it is always going to be valid, so let's
    // always return true.
    return true;
  }
}
