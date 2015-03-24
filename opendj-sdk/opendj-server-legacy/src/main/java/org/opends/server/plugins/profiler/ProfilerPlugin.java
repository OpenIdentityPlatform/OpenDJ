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
package org.opends.server.plugins.profiler;
import static org.opends.messages.PluginMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.admin.std.server.ProfilerPluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.util.TimeThread;

/**
 * This class defines a Directory Server startup plugin that will register
 * itself as a configurable component that can allow for a simple sample-based
 * profiling mechanism within the Directory Server.  When profiling is enabled,
 * the server will periodically (e.g., every few milliseconds) retrieve all the
 * stack traces for all threads in the server and aggregates them so that they
 * can be analyzed to see where the server is spending all of its processing
 * time.
 */
public final class ProfilerPlugin
       extends DirectoryServerPlugin<ProfilerPluginCfg>
       implements ConfigurationChangeListener<ProfilerPluginCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The value to use for the profiler action when no action is necessary.
   */
  public static final String PROFILE_ACTION_NONE = "none";



  /**
   * The value to use for the profiler action when it should start capturing
   * information.
   */
  public static final String PROFILE_ACTION_START = "start";



  /**
   * The value to use for the profiler action when it should stop capturing
   * data and write the information it has collected to disk.
   */
  public static final String PROFILE_ACTION_STOP = "stop";



  /**
   * The value to use for the profiler action when it should stop capturing
   * data and discard any information that has been collected.
   */
  public static final String PROFILE_ACTION_CANCEL = "cancel";



  /** The DN of the configuration entry for this plugin. */
  private DN configEntryDN;

  /** The current configuration for this plugin. */
  private ProfilerPluginCfg currentConfig;

  /** The thread that is actually capturing the profile information. */
  private ProfilerThread profilerThread;



  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public ProfilerPlugin()
  {
    super();

  }



  /** {@inheritDoc} */
  @Override
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     ProfilerPluginCfg configuration)
         throws ConfigException
  {
    configuration.addProfilerChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();


    // Make sure that this plugin is only registered as a startup plugin.
    if (pluginTypes.isEmpty())
    {
      throw new ConfigException(ERR_PLUGIN_PROFILER_NO_PLUGIN_TYPES.get(configEntryDN));
    }
    else
    {
      for (PluginType t : pluginTypes)
      {
        if (t != PluginType.STARTUP)
        {
          throw new ConfigException(ERR_PLUGIN_PROFILER_INVALID_PLUGIN_TYPE.get(configEntryDN, t));
        }
      }
    }


    // Make sure that the profile directory exists.
    File profileDirectory = getFileForPath(configuration.getProfileDirectory());
    if (!profileDirectory.exists() || !profileDirectory.isDirectory())
    {
      LocalizableMessage message = WARN_PLUGIN_PROFILER_INVALID_PROFILE_DIR.get(
          profileDirectory.getAbsolutePath(), configEntryDN);
      throw new ConfigException(message);
    }
  }



  /** {@inheritDoc} */
  @Override
  public final void finalizePlugin()
  {
    currentConfig.removeProfilerChangeListener(this);

    // If the profiler thread is still active, then cause it to dump the
    // information it has captured and exit.
    synchronized (this)
    {
      if (profilerThread != null)
      {
        profilerThread.stopProfiling();

        String filename = currentConfig.getProfileDirectory() + File.separator +
                          "profile." + TimeThread.getGMTTime();
        try
        {
          profilerThread.writeCaptureData(filename);
        }
        catch (Exception e)
        {
          logger.traceException(e);

          logger.error(ERR_PLUGIN_PROFILER_CANNOT_WRITE_PROFILE_DATA, configEntryDN, filename,
                  stackTraceToSingleLineString(e));
        }
      }
    }
  }



  /** {@inheritDoc} */
  @Override
  public final PluginResult.Startup doStartup()
  {
    ProfilerPluginCfg config = currentConfig;

    // If the profiler should be started automatically, then do so now.
    if (config.isEnableProfilingOnStartup())
    {
      profilerThread = new ProfilerThread(config.getProfileSampleInterval());
      profilerThread.start();
    }

    return PluginResult.Startup.continueStartup();
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    ProfilerPluginCfg config = (ProfilerPluginCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      ProfilerPluginCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN cfgEntryDN = configuration.dn();

    // Make sure that the plugin is only registered as a startup plugin.
    if (configuration.getPluginType().isEmpty())
    {
      unacceptableReasons.add(ERR_PLUGIN_PROFILER_NO_PLUGIN_TYPES.get(cfgEntryDN));
      configAcceptable = false;
    }
    else
    {
      for (PluginCfgDefn.PluginType t : configuration.getPluginType())
      {
        if (t != PluginCfgDefn.PluginType.STARTUP)
        {
          unacceptableReasons.add(ERR_PLUGIN_PROFILER_INVALID_PLUGIN_TYPE.get(cfgEntryDN, t));
          configAcceptable = false;
          break;
        }
      }
    }


    // Make sure that the profile directory exists.
    File profileDirectory = getFileForPath(configuration.getProfileDirectory());
    if (!profileDirectory.exists() || !profileDirectory.isDirectory())
    {
      unacceptableReasons.add(WARN_PLUGIN_PROFILER_INVALID_PROFILE_DIR.get(
          profileDirectory.getAbsolutePath(), cfgEntryDN));
      configAcceptable = false;
    }

    return configAcceptable;
  }



  /**
   * Applies the configuration changes to this change listener.
   *
   * @param configuration
   *          The new configuration containing the changes.
   * @return Returns information about the result of changing the
   *         configuration.
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 ProfilerPluginCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    currentConfig = configuration;

    // See if we need to perform any action.
    switch (configuration.getProfileAction())
    {
      case START:
        // See if the profiler thread is running.  If so, then don't do
        // anything.  Otherwise, start it.
        synchronized (this)
        {
          if (profilerThread == null)
          {
            profilerThread =
                 new ProfilerThread(configuration.getProfileSampleInterval());
            profilerThread.start();

            ccr.addMessage(INFO_PLUGIN_PROFILER_STARTED_PROFILING.get(configEntryDN));
          }
          else
          {
            ccr.addMessage(INFO_PLUGIN_PROFILER_ALREADY_PROFILING.get(configEntryDN));
          }
        }
        break;

      case STOP:
        // See if the profiler thread is running.  If so, then stop it and write
        // the information captured to disk.  Otherwise, don't do anything.
        synchronized (this)
        {
          if (profilerThread == null)
          {
            ccr.addMessage(INFO_PLUGIN_PROFILER_NOT_RUNNING.get(configEntryDN));
          }
          else
          {
            profilerThread.stopProfiling();

            ccr.addMessage(INFO_PLUGIN_PROFILER_STOPPED_PROFILING.get(configEntryDN));

            String filename =
                 getFileForPath(
                      configuration.getProfileDirectory()).getAbsolutePath() +
                 File.separator + "profile." + TimeThread.getGMTTime();

            try
            {
              profilerThread.writeCaptureData(filename);

              ccr.addMessage(INFO_PLUGIN_PROFILER_WROTE_PROFILE_DATA.get(configEntryDN, filename));
            }
            catch (Exception e)
            {
              logger.traceException(e);

              ccr.addMessage(ERR_PLUGIN_PROFILER_CANNOT_WRITE_PROFILE_DATA.get(
                  configEntryDN, filename, stackTraceToSingleLineString(e)));
              ccr.setResultCode(DirectoryConfig.getServerErrorResultCode());
            }

            profilerThread = null;
          }
        }
        break;

      case CANCEL:
        // See if the profiler thread is running.  If so, then stop it but don't
        // write anything to disk.  Otherwise, don't do anything.
        synchronized (this)
        {
          if (profilerThread == null)
          {
            ccr.addMessage(INFO_PLUGIN_PROFILER_NOT_RUNNING.get(configEntryDN));
          }
          else
          {
            profilerThread.stopProfiling();

            ccr.addMessage(INFO_PLUGIN_PROFILER_STOPPED_PROFILING.get(configEntryDN));

            profilerThread = null;
          }
        }
        break;
    }

    return ccr;
  }
}
