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
 * by brackets "[]" replaced with your own identifying * information:
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.StartupPluginResult;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.config.ReadOnlyConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;
import static org.opends.server.util.ServerConstants.*;
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
       extends DirectoryServerPlugin
       implements ConfigurableComponent
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



  /**
   * The set of time units that will be used for expressing the task retention
   * time.
   */
  private static final LinkedHashMap<String,Double> timeUnits =
       new LinkedHashMap<String,Double>();



  // Indicates whether the profiler should be started automatically when the
  // Directory Server is started.
  private boolean autoStart;

  // The DN of the configuration entry for this plugin.
  private DN configEntryDN;

  // The set of profiler actions that a client may request.
  private LinkedHashSet<String> profilerActions;

  // The sample interval to use when capturing stack traces.
  private long sampleInterval;

  // The thread that is actually capturing the profile information.
  private ProfilerThread profilerThread;

  // The path to the directory into which the captured information should be
  // written.
  private String profileDirectory;



  static
  {
    timeUnits.put(TIME_UNIT_MILLISECONDS_ABBR, 1D);
    timeUnits.put(TIME_UNIT_MILLISECONDS_FULL, 1D);
    timeUnits.put(TIME_UNIT_SECONDS_ABBR, 1000D);
    timeUnits.put(TIME_UNIT_SECONDS_FULL, 1000D);
  }



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
                                     ConfigEntry configEntry)
         throws ConfigException
  {


    // Initialize the set of profiler actions.
    profilerActions = new LinkedHashSet<String>(4);
    profilerActions.add(PROFILE_ACTION_NONE);
    profilerActions.add(PROFILE_ACTION_START);
    profilerActions.add(PROFILE_ACTION_STOP);
    profilerActions.add(PROFILE_ACTION_CANCEL);


    // Get and store the DN of the associated configuration entry.
    configEntryDN = configEntry.getDN();


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


    // Get the name of the profile directory from the config entry.  We may
    // override this value when reading the stop request, but if that attribute
    // is missing or we get a request to finalize while the profiler is still
    // active, then we will want to know it ahead of time.  If there is no
    // value, then just use the current working directory.
    profileDirectory = System.getProperty("user.dir");

    int msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_PROFILE_DIR;
    StringConfigAttribute profileDirStub =
         new StringConfigAttribute(ATTR_PROFILE_DIR, getMessage(msgID), true,
                                   false, false);
    try
    {
      StringConfigAttribute profileDirAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(profileDirStub);
      if (profileDirAttr != null)
      {
        profileDirectory =
             getFileForPath(profileDirAttr.activeValue()).getAbsolutePath();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_PROFILE_DIR;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e),
                                  profileDirectory);
      logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }


    // Determine whether the plugin should start capturing data automatically
    // when the server is starting.
    autoStart = false;

    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_AUTOSTART;
    BooleanConfigAttribute autoStartStub =
         new BooleanConfigAttribute(ATTR_PROFILE_AUTOSTART, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute autoStartAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(autoStartStub);
      if (autoStartAttr != null)
      {
        autoStart = autoStartAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_AUTOSTART;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }


    // Determine the sample interval that should be used when capturing stack
    // traces.
    sampleInterval = DEFAULT_PROFILE_INTERVAL;

    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_INTERVAL;
    IntegerWithUnitConfigAttribute intervalStub =
         new IntegerWithUnitConfigAttribute(ATTR_PROFILE_INTERVAL,
                                            getMessage(msgID), false, timeUnits,
                                            true, 1, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute intervalAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(intervalStub);
      if (intervalAttr != null)
      {
        sampleInterval = intervalAttr.activeCalculatedValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_INTERVAL;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e),
                                  sampleInterval);
      logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }


    // Register with the Directory Server as a configurable component.
    DirectoryConfig.registerConfigurableComponent(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void finalizePlugin()
  {


    // If the profiler thread is still active, then cause it to dump the
    // information it has captured and exit.
    synchronized (this)
    {
      if (profilerThread != null)
      {
        profilerThread.stopProfiling();

        String filename = profileDirectory + File.separator + "profile." +
                          TimeThread.getUTCTime();
        try
        {
          profilerThread.writeCaptureData(filename);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
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


    DirectoryConfig.deregisterConfigurableComponent(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final StartupPluginResult doStartup()
  {

    // If the profiler should be started automatically, then do so now.
    if (autoStart)
    {
      profilerThread = new ProfilerThread(sampleInterval);
      profilerThread.start();
    }

    return StartupPluginResult.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  public final DN getConfigurableComponentEntryDN()
  {

    return configEntryDN;
  }



  /**
   * {@inheritDoc}
   */
  public final List<ConfigAttribute> getConfigurationAttributes()
  {

    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();


    int msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_INTERVAL;
    IntegerWithUnitConfigAttribute intervalAttr =
         new IntegerWithUnitConfigAttribute(ATTR_PROFILE_INTERVAL,
                                            getMessage(msgID), false, timeUnits,
                                            true, 1, false, 0, sampleInterval,
                                            TIME_UNIT_MILLISECONDS_FULL);
    attrList.add(intervalAttr);


    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_PROFILE_DIR;
    StringConfigAttribute profileDirAttr =
         new StringConfigAttribute(ATTR_PROFILE_DIR, getMessage(msgID), true,
                                   false, false, profileDirectory);
    attrList.add(profileDirAttr);


    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_AUTOSTART;
    BooleanConfigAttribute autoStartAttr =
         new BooleanConfigAttribute(ATTR_PROFILE_AUTOSTART, getMessage(msgID),
                                    false, autoStart);
    attrList.add(autoStartAttr);


    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_STATE;
    String stateStr = (profilerThread == null) ? "disabled" : "enabled";
    ReadOnlyConfigAttribute stateAttr =
         new ReadOnlyConfigAttribute(ATTR_PROFILE_STATE, getMessage(msgID),
                                     stateStr);
    attrList.add(stateAttr);


    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_ACTION;
    MultiChoiceConfigAttribute actionAttr =
         new MultiChoiceConfigAttribute(ATTR_PROFILE_ACTION, getMessage(msgID),
                                        true, false, false, profilerActions,
                                        PROFILE_ACTION_NONE);
    attrList.add(actionAttr);


    return attrList;
  }



  /**
   * {@inheritDoc}
   */
  public final boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                            List<String> unacceptableReasons)
  {


    // See if there is an acceptable value for the sample interval.
    int msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_INTERVAL;
    IntegerWithUnitConfigAttribute intervalStub =
         new IntegerWithUnitConfigAttribute(ATTR_PROFILE_INTERVAL,
                                            getMessage(msgID), false, timeUnits,
                                            true, 1, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute intervalAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(intervalStub);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_INTERVAL;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e),
                                         sampleInterval));
      return false;
    }


    // See if there is an acceptable value for the profiler directory.
    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_PROFILE_DIR;
    StringConfigAttribute profileDirStub =
         new StringConfigAttribute(ATTR_PROFILE_DIR, getMessage(msgID), true,
                                   false, false);
    try
    {
      StringConfigAttribute profileDirAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(profileDirStub);
      if (profileDirAttr != null)
      {
        File dirFile = getFileForPath(profileDirAttr.activeValue());
        if (! (dirFile.exists() && dirFile.isDirectory()))
        {
          msgID = MSGID_PLUGIN_PROFILER_INVALID_PROFILE_DIR;
          unacceptableReasons.add(getMessage(msgID,
                                             profileDirAttr.activeValue(),
                                             String.valueOf(configEntryDN)));
          return false;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_PROFILE_DIR;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e),
                                         sampleInterval));
      return false;
    }


    // See if there is an acceptable value for the autostart flag.
    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_AUTOSTART;
    BooleanConfigAttribute autoStartStub =
         new BooleanConfigAttribute(ATTR_PROFILE_AUTOSTART, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute autoStartAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(autoStartStub);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_AUTOSTART;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      return false;
    }


    // See if there is an acceptable value for the profiler action.
    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_ACTION;
    MultiChoiceConfigAttribute actionStub =
         new MultiChoiceConfigAttribute(ATTR_PROFILE_ACTION, getMessage(msgID),
                                        false, false, false, profilerActions);
    try
    {
      MultiChoiceConfigAttribute actionAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(actionStub);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_ACTION;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      return false;
    }


    // If we've gotten here, then everything looks OK.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public final ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                        boolean detailedResults)
  {


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Check to see if the sample interval needs to change and apply it as
    // necessary.
    int msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_INTERVAL;
    IntegerWithUnitConfigAttribute intervalStub =
         new IntegerWithUnitConfigAttribute(ATTR_PROFILE_INTERVAL,
                                            getMessage(msgID), false, timeUnits,
                                            true, 1, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute intervalAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(intervalStub);
      if (intervalAttr != null)
      {
        long newInterval = intervalAttr.pendingCalculatedValue();
        if (newInterval != sampleInterval)
        {
          sampleInterval = newInterval;

          if (detailedResults)
          {
            msgID = MSGID_PLUGIN_PROFILER_UPDATED_INTERVAL;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                    sampleInterval));
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_UPDATE_INTERVAL;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryConfig.getServerErrorResultCode();
    }



    // Check to see if the profile directory needs to change.
    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_PROFILE_DIR;
    StringConfigAttribute profileDirStub =
         new StringConfigAttribute(ATTR_PROFILE_DIR, getMessage(msgID), true,
                                   false, false);
    try
    {
      StringConfigAttribute profileDirAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(profileDirStub);
      if (profileDirAttr != null)
      {
        String dirString = profileDirAttr.pendingValue();
        if (! dirString.equals(profileDirectory))
        {
          File dirFile = getFileForPath(dirString);
          if (! (dirFile.exists() && dirFile.isDirectory()))
          {
            msgID = MSGID_PLUGIN_PROFILER_INVALID_PROFILE_DIR;
            messages.add(getMessage(msgID, dirString,
                                    String.valueOf(configEntryDN)));

            resultCode = DirectoryConfig.getServerErrorResultCode();
          }
          else
          {
            profileDirectory = dirFile.getAbsolutePath();

            if (detailedResults)
            {
              msgID = MSGID_PLUGIN_PROFILER_UPDATED_DIRECTORY;
              messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                      profileDirectory));
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_UPDATE_DIRECTORY;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      resultCode = DirectoryConfig.getServerErrorResultCode();
    }


    // Check to see if we need to invoke some profiler action.   We will only
    // try to take action if we haven't seen any problems with other parameters.
    msgID = MSGID_PLUGIN_PROFILER_DESCRIPTION_ACTION;
    MultiChoiceConfigAttribute actionStub =
         new MultiChoiceConfigAttribute(ATTR_PROFILE_ACTION, getMessage(msgID),
                                        false, false, false, profilerActions);
    try
    {
      MultiChoiceConfigAttribute actionAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(actionStub);

      if (actionAttr != null)
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          String action = actionAttr.pendingValue().toLowerCase();
          if (action.equals(PROFILE_ACTION_NONE))
          {
            // We don't need to do anything at all.
          }
          else if (action.equals(PROFILE_ACTION_START))
          {
            // See if the profiler thread is running.  If so, then don't do
            // anything.  Otherwise, start it.
            synchronized (this)
            {
              if (profilerThread == null)
              {
                profilerThread = new ProfilerThread(sampleInterval);
                profilerThread.start();

                if (detailedResults)
                {
                  msgID = MSGID_PLUGIN_PROFILER_STARTED_PROFILING;
                  messages.add(getMessage(msgID,
                                          String.valueOf(configEntryDN)));
                }
              }
              else
              {
                msgID = MSGID_PLUGIN_PROFILER_ALREADY_PROFILING;
                messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
              }
            }
          }
          else if (action.equals(PROFILE_ACTION_STOP))
          {
            // See if the profiler thread is running.  If so, then stop it and
            // write the information captured to disk.  Otherwise, don't do
            // anything.
            synchronized (this)
            {
              if (profilerThread == null)
              {
                msgID = MSGID_PLUGIN_PROFILER_NOT_RUNNING;
                messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
              }
              else
              {
                profilerThread.stopProfiling();

                if (detailedResults)
                {
                  msgID = MSGID_PLUGIN_PROFILER_STOPPED_PROFILING;
                  messages.add(getMessage(msgID,
                                          String.valueOf(configEntryDN)));
                }

                String filename = profileDirectory + File.separator +
                                  "profile." + TimeThread.getUTCTime();

                try
                {
                  profilerThread.writeCaptureData(filename);

                  if (detailedResults)
                  {
                    msgID = MSGID_PLUGIN_PROFILER_WROTE_PROFILE_DATA;
                    messages.add(getMessage(msgID,
                                            String.valueOf(configEntryDN),
                                            filename));
                  }
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    debugCought(DebugLogLevel.ERROR, e);
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
          }
          else if (action.equals(PROFILE_ACTION_CANCEL))
          {
            // See if the profiler thread is running.  If so, then stop it but
            // don't write anything to disk.  Otherwise, don't do anything.
            synchronized (this)
            {
              if (profilerThread == null)
              {
                msgID = MSGID_PLUGIN_PROFILER_NOT_RUNNING;
                messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
              }
              else
              {
                profilerThread.stopProfiling();

                if (detailedResults)
                {
                  msgID = MSGID_PLUGIN_PROFILER_STOPPED_PROFILING;
                  messages.add(getMessage(msgID,
                                          String.valueOf(configEntryDN)));
                }

                profilerThread = null;
              }
            }
          }
          else
          {
            // This was an unrecognized action.  We won't do anything.
            msgID = MSGID_PLUGIN_PROFILER_UNKNOWN_ACTION;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                    action));

            resultCode = DirectoryConfig.getServerErrorResultCode();
          }
        }
        else
        {
          msgID = MSGID_PLUGIN_PROFILER_SKIPPING_ACTION;
          messages.add(getMessage(msgID, actionAttr.pendingValue(),
                                  String.valueOf(configEntryDN)));
        }

        configEntry.removeConfigAttribute(ATTR_PROFILE_ACTION.toLowerCase());
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_PERFORM_ACTION;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      resultCode = DirectoryConfig.getServerErrorResultCode();
    }


    // Return the result of the processing.
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

