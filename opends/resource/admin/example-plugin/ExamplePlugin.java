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



import static org.opends.server.loggers.Error.logError;

import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.StartupPluginResult;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

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
   * {@inheritDoc}
   */
  @Override()
  public void initializePlugin(Set<PluginType> pluginTypes,
      ExamplePluginCfg configuration)
      throws ConfigException {
    // This plugin may only be used as a server startup plugin.
    for (PluginType t : pluginTypes) {
      switch (t) {
      case STARTUP:
        // This is fine.
        break;
      default:
        throw new ConfigException(-1, "Invalid plugin type " + t
            + " for the example plugin.");
      }
    }

    // Register change listeners. These are not really necessary for
    // this plugin since it is only used during server start-up.
    configuration.addExampleChangeListener(this);

    // Save the configuration.
    this.config = configuration;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public StartupPluginResult doStartup() {
    // Log the provided message.
    logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.NOTICE,
        "Example plugin message '" + config.getMessage() + "'.", 9999);
    return StartupPluginResult.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      ExamplePluginCfg config) {
    // The new configuration has already been validated.

    // Log a message to say that the configuration has changed. This
    // isn't necessary, but we'll do it just to show that the change
    // has taken effect.
    logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.NOTICE,
        "Example plugin message has been changed from '"
            + this.config.getMessage() + "' to '"
            + config.getMessage() + "'.", 9999);

    // Update the configuration.
    this.config = config;

    // Update was successfull, no restart required.
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      ExamplePluginCfg config, List<String> messages) {
    // The only thing that can be validated here is the plugin's
    // message. However, it is always going to be valid, so let's
    // always return true.
    return true;
  }
}
