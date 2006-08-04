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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
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
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
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
public class ProfilerPlugin
       extends DirectoryServerPlugin
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.plugins.profiler.ProfilerPlugin";



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

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Performs any initialization necessary for this plugin.  This will be called
   * as soon as the plugin has been loaded and before it is registered with the
   * server.
   *
   * @param  directoryServer  The reference to the Directory Server instance in
   *                          which the plugin will be running.
   * @param  pluginTypes      The set of plugin types that indicate the ways in
   *                          which this plugin will be invoked.
   * @param  configEntry      The entry containing the configuration information
   *                          for this plugin.
   *
   * @throws  ConfigException  If the provided entry does not contain a valid
   *                           configuration for this plugin.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the plugin that is not related to the
   *                                   server configuration.
   */
  public void initializePlugin(DirectoryServer directoryServer,
                               Set<PluginType> pluginTypes,
                               ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializePlugin",
                      String.valueOf(directoryServer),
                      String.valueOf(pluginTypes), String.valueOf(configEntry));


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
        profileDirectory = profileDirAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePlugin", e);

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
      assert debugException(CLASS_NAME, "initializePlugin", e);

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
      assert debugException(CLASS_NAME, "initializePlugin", e);

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_INTERVAL;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e),
                                  sampleInterval);
      logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }


    // Register with the Directory Server as a configurable component.
    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * Performs any necessary finalization for this plugin.  This will be called
   * just after the plugin has been deregistered with the server but before it
   * has been unloaded.
   */
  public void finalizePlugin()
  {
    assert debugEnter(CLASS_NAME, "finalizePlugin");


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
          assert debugException(CLASS_NAME, "finalizePlugin", e);

          int    msgID   = MSGID_PLUGIN_PROFILER_CANNOT_WRITE_PROFILE_DATA;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      filename,
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
      }
    }


    DirectoryServer.deregisterConfigurableComponent(this);
  }



  /**
   * Performs any processing that should be done when the Directory Server is in
   * the process of starting.  This method will be called after virtually all
   * initialization has been performed but before other plugins have before the
   * connection handlers are started.
   *
   * @return  The result of the startup plugin processing.
   */
  public StartupPluginResult doStartup()
  {
    assert debugEnter(CLASS_NAME, "doStartup");

    // If the profiler should be started automatically, then do so now.
    if (autoStart)
    {
      profilerThread = new ProfilerThread(sampleInterval);
      profilerThread.start();
    }

    return new StartupPluginResult();
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

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
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.lang.List<String>");


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
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

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
        File dirFile = new File(profileDirAttr.activeValue());
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
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

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
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

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
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_ACTION;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      return false;
    }


    // If we've gotten here, then everything looks OK.
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


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
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_UPDATE_INTERVAL;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
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
          File dirFile = new File(dirString);
          if (! (dirFile.exists() && dirFile.isDirectory()))
          {
            msgID = MSGID_PLUGIN_PROFILER_INVALID_PROFILE_DIR;
            messages.add(getMessage(msgID, dirString,
                                    String.valueOf(configEntryDN)));

            resultCode = DirectoryServer.getServerErrorResultCode();
          }
          else
          {
            profileDirectory = dirString;

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
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_UPDATE_DIRECTORY;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      resultCode = DirectoryServer.getServerErrorResultCode();
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
                  assert debugException(CLASS_NAME, "applyNewConfiguration", e);

                  msgID = MSGID_PLUGIN_PROFILER_CANNOT_WRITE_PROFILE_DATA;
                  messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                          filename,
                                          stackTraceToSingleLineString(e)));

                  resultCode = DirectoryServer.getServerErrorResultCode();
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

            resultCode = DirectoryServer.getServerErrorResultCode();
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
      assert debugException(CLASS_NAME, "initializePlugin", e);

      msgID = MSGID_PLUGIN_PROFILER_CANNOT_PERFORM_ACTION;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    // Return the result of the processing.
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

