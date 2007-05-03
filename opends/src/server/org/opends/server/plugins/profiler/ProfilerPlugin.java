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
package org.opends.server.plugins.profiler;



import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ProfilerPluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.StartupPluginResult;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;
import static org.opends.server.util.StaticUtils.*;



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



  // The DN of the configuration entry for this plugin.
  private DN configEntryDN;

  // The current configuration for this plugin.
  private ProfilerPluginCfg currentConfig;

  // The thread that is actually capturing the profile information.
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



  /**
   * {@inheritDoc}
   */
  @Override()
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
      int    msgID   = MSGID_PLUGIN_PROFILER_NO_PLUGIN_TYPES;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }
    else
    {
      for (PluginType t : pluginTypes)
      {
        if (t != PluginType.STARTUP)
        {
          int    msgID   = MSGID_PLUGIN_PROFILER_INVALID_PLUGIN_TYPE;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      String.valueOf(t));
          throw new ConfigException(msgID, message);
        }
      }
    }


    // Make sure that the profile directory exists.
    File profileDirectory = getFileForPath(configuration.getProfileDirectory());
    if (! (profileDirectory.exists() && profileDirectory.isDirectory()))
    {
      int    msgID   = MSGID_PLUGIN_PROFILER_INVALID_PROFILE_DIR;
      String message = getMessage(msgID, profileDirectory.getAbsolutePath(),
                                  String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_PLUGIN_PROFILER_CANNOT_WRITE_PROFILE_DATA;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      filename,
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final StartupPluginResult doStartup()
  {
    ProfilerPluginCfg config = currentConfig;

    // If the profiler should be started automatically, then do so now.
    if (config.isEnableProfilingOnStartup())
    {
      profilerThread = new ProfilerThread(config.getProfileSampleInterval());
      profilerThread.start();
    }

    return StartupPluginResult.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      ProfilerPluginCfg configuration,
                      List<String> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Make sure that the profile directory exists.
    File profileDirectory = getFileForPath(configuration.getProfileDirectory());
    if (! (profileDirectory.exists() && profileDirectory.isDirectory()))
    {
      int msgID = MSGID_PLUGIN_PROFILER_INVALID_PROFILE_DIR;
      unacceptableReasons.add(getMessage(msgID,
                                         profileDirectory.getAbsolutePath(),
                                         String.valueOf(configEntryDN)));
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
  public ConfigChangeResult applyConfigurationChange(
                                 ProfilerPluginCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

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

            int msgID = MSGID_PLUGIN_PROFILER_STARTED_PROFILING;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
          }
          else
          {
            int msgID = MSGID_PLUGIN_PROFILER_ALREADY_PROFILING;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
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
            int msgID = MSGID_PLUGIN_PROFILER_NOT_RUNNING;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
          }
          else
          {
            profilerThread.stopProfiling();

            int msgID = MSGID_PLUGIN_PROFILER_STOPPED_PROFILING;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

            String filename =
                 getFileForPath(
                      configuration.getProfileDirectory()).getAbsolutePath() +
                 File.separator + "profile." + TimeThread.getGMTTime();

            try
            {
              profilerThread.writeCaptureData(filename);

              msgID = MSGID_PLUGIN_PROFILER_WROTE_PROFILE_DATA;
              messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                      filename));
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }

              msgID = MSGID_PLUGIN_PROFILER_CANNOT_WRITE_PROFILE_DATA;
              messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                      filename,
                                      stackTraceToSingleLineString(e)));

              resultCode = DirectoryConfig.getServerErrorResultCode();
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
            int msgID = MSGID_PLUGIN_PROFILER_NOT_RUNNING;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
          }
          else
          {
            profilerThread.stopProfiling();

            int msgID = MSGID_PLUGIN_PROFILER_STOPPED_PROFILING;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

            profilerThread = null;
          }
        }
        break;
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}
