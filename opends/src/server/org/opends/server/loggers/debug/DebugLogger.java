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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.loggers.debug;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.nio.ByteBuffer;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import org.opends.server.api.ProtocolElement;
import org.opends.server.api.DebugLogPublisher;
import org.opends.server.loggers.*;
import org.opends.server.types.*;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.StaticUtils;
import org.opends.server.admin.std.server.DebugLogPublisherCfg;
import org.opends.server.admin.std.meta.DebugLogPublisherCfgDefn;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import com.sleepycat.je.*;

/**
 * A logger for debug and trace logging. DebugLogger provides a debugging
 * management access point. It is used to configure the Tracers, as well as
 * to register a per-class tracer.
 *
 * Various stub debug methods are provided to log different types of debug
 * messages. However, these methods do not contain any actual implementation.
 * Tracer aspects are later weaved to catch alls to these stub methods and
 * do the work of logging the message.
 *
 * DebugLogger is self-initializing.
 */
public class DebugLogger implements
    ConfigurationAddListener<DebugLogPublisherCfg>,
    ConfigurationDeleteListener<DebugLogPublisherCfg>,
    ConfigurationChangeListener<DebugLogPublisherCfg>
{
  //The default level to log constructor exectuions.
  static final LogLevel DEFAULT_CONSTRUCTOR_LEVEL =
      DebugLogLevel.VERBOSE;
  //The default level to log method entry and exit pointcuts.
  static final LogLevel DEFAULT_ENTRY_EXIT_LEVEL =
      DebugLogLevel.VERBOSE;
  //The default level to log method entry and exit pointcuts.
  static final LogLevel DEFAULT_THROWN_LEVEL =
      DebugLogLevel.ERROR;

  // The set of all DebugTracer aspect instances.
  static CopyOnWriteArraySet<DebugTracer> classTracers =
      new CopyOnWriteArraySet<DebugTracer>();

  // The set of debug loggers that have been registered with the server.  It
  // will initially be empty.
  static ConcurrentHashMap<DN,
      DebugLogPublisher> debugPublishers =
      new ConcurrentHashMap<DN,
          DebugLogPublisher>();

  // Trace methods will use this static boolean to determine if debug is
  // enabled so to not incur the cost of calling debugPublishers.isEmtpty().
  static boolean enabled = false;

  // The singleton instance of this class for configuration purposes.
  static final DebugLogger instance = new DebugLogger();

  /**
   * Add an debug log publisher to the debug logger.
   *
   * @param dn The DN of the configuration entry for the publisher.
   * @param publisher The error log publisher to add.
   */
  public synchronized static void addDebugLogPublisher(DN dn,
                                                 DebugLogPublisher publisher)
  {
    debugPublishers.put(dn, publisher);

    // Update all existing aspect instances
    addTracerSettings(publisher);

    enabled = DynamicConstants.WEAVE_ENABLED;
  }

  /**
   * Remove an debug log publisher from the debug logger.
   *
   * @param dn The DN of the publisher to remove.
   * @return The publisher that was removed or null if it was not found.
   */
  public synchronized static DebugLogPublisher removeDebugLogPublisher(DN dn)
  {
    DebugLogPublisher removed =  debugPublishers.remove(dn);

    if(removed != null)
    {
      removed.close();
      removeTracerSettings(removed);
    }

    if(debugPublishers.isEmpty())
    {
      enabled = false;
    }

    return removed;
  }

  /**
   * Removes all existing debug log publishers from the logger.
   */
  public synchronized static void removeAllDebugLogPublishers()
  {
    for(DebugLogPublisher publisher : debugPublishers.values())
    {
      publisher.close();
      removeTracerSettings(publisher);
    }

    debugPublishers.clear();

    enabled = false;
  }

  /**
   * Initializes all the debug log publishers.
   *
   * @param  configs The debug log publisher configurations.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public void initializeDebugLogger(List<DebugLogPublisherCfg> configs)
      throws ConfigException, InitializationException
  {
    for(DebugLogPublisherCfg config : configs)
    {
      config.addDebugChangeListener(this);

      if(config.isEnabled())
      {
        DebugLogPublisher debugLogPublisher = getDebugPublisher(config);

        addDebugLogPublisher(config.dn(), debugLogPublisher);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(DebugLogPublisherCfg config,
                                              List<String> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(DebugLogPublisherCfg config,
                                               List<String> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(DebugLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();

    config.addDebugChangeListener(this);

    if(config.isEnabled())
    {
      try
      {
        DebugLogPublisher debugLogPublisher =
            getDebugPublisher(config);

        addDebugLogPublisher(config.dn(), debugLogPublisher);
      }
      catch(ConfigException e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
        messages.add(e.getMessage());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
        int msgID = MSGID_CONFIG_LOGGER_CANNOT_CREATE_LOGGER;
        messages.add(getMessage(msgID, String.valueOf(config.dn().toString()),
                                stackTraceToSingleLineString(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      DebugLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();

    DN dn = config.dn();
    DebugLogPublisher debugLogPublisher = debugPublishers.get(dn);

    if(debugLogPublisher == null)
    {
      if(config.isEnabled())
      {
        // Needs to be added and enabled.
        return applyConfigurationAdd(config);
      }
    }
    else
    {
      if(config.isEnabled())
      {
        // The publisher is currently active, so we don't need to do anything.
        // Changes to the class name cannot be
        // applied dynamically, so if the class name did change then
        // indicate that administrative action is required for that
        // change to take effect.
        String className = config.getJavaImplementationClass();
        if(!className.equals(debugLogPublisher.getClass().getName()))
        {
          adminActionRequired = true;
        }
      }
      else
      {
        // The publisher is being disabled so shut down and remove.
        removeDebugLogPublisher(config.dn());
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(DebugLogPublisherCfg config,
                                               List<String> unacceptableReasons)
  {
    DN dn = config.dn();
    DebugLogPublisher debugLogPublisher = debugPublishers.get(dn);
    return debugLogPublisher != null;

  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult
         applyConfigurationDelete(DebugLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;

    DebugLogPublisher publisher = removeDebugLogPublisher(config.dn());

    return new ConfigChangeResult(resultCode, adminActionRequired);
  }

  private boolean isJavaClassAcceptable(DebugLogPublisherCfg config,
                                        List<String> unacceptableReasons)
  {
    String className = config.getJavaImplementationClass();
    DebugLogPublisherCfgDefn d = DebugLogPublisherCfgDefn.getInstance();
    ClassPropertyDefinition pd =
        d.getJavaImplementationClassPropertyDefinition();
    // Load the class and cast it to a DebugLogPublisher.
    Class<? extends DebugLogPublisher> theClass;
    try {
      theClass = pd.loadClass(className, DebugLogPublisher.class);
      theClass.newInstance();
    } catch (Exception e) {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS;
      String message = getMessage(msgID, className,
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
      theClass.getMethod("initializeDebugLogPublisher", config.definition()
          .getServerConfigurationClass());
    } catch (Exception e) {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS;
      String message = getMessage(msgID, className,
                                  config.dn().toString(),
                                  String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }
    // The class is valid as far as we can tell.
    return true;
  }

  private DebugLogPublisher getDebugPublisher(DebugLogPublisherCfg config)
      throws ConfigException {
    String className = config.getJavaImplementationClass();
    DebugLogPublisherCfgDefn d = DebugLogPublisherCfgDefn.getInstance();
    ClassPropertyDefinition pd =
        d.getJavaImplementationClassPropertyDefinition();
    // Load the class and cast it to a DebugLogPublisher.
    Class<? extends DebugLogPublisher> theClass;
    DebugLogPublisher debugLogPublisher;
    try {
      theClass = pd.loadClass(className, DebugLogPublisher.class);
      debugLogPublisher = theClass.newInstance();

      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod("initializeDebugLogPublisher",
                             config.definition().getServerConfigurationClass());
      method.invoke(debugLogPublisher, config);
    }
    catch (InvocationTargetException ite)
    {
      // Rethrow the exceptions thrown be the invoked method.
      Throwable e = ite.getTargetException();
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS;
      String message = getMessage(msgID, className,
                                  config.dn().toString(),
                                  stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS;
      String message = getMessage(msgID, className,
                                  config.dn().toString(),
                                  String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    // The debug publisher has been successfully initialized.
    return debugLogPublisher;
  }

  /**
   * Adds the settings for the provided publisher in all existing tracers.
   * If existing settings exist for the given publisher, it will be updated
   * with the new settings.
   *
   * @param publisher The debug log publisher with the new settings.
   */
  @SuppressWarnings("unchecked")
  public static void addTracerSettings(DebugLogPublisher publisher)
  {
    // Make sure this publisher is still registered with us. If not, don't
    // use its settings.
    if(debugPublishers.contains(publisher))
    {
      for(DebugTracer tracer : classTracers)
      {
        tracer.classSettings.put(publisher,
                                 publisher.getClassSettings(tracer.className));

        // For some reason, the compiler doesn't see that
        // debugLogPublihser.getMethodSettings returns a parameterized Map.
        // This problem goes away if a parameterized verson of
        // DebugLogPublisher is used. However, we can't not use reflection to
        // instantiate a generic
        // DebugLogPublisher<? extends DebugLogPublisherCfg> type. The only
        // thing we can do is to just suppress the compiler warnings.
        Map<String, TraceSettings> methodSettings =
            publisher.getMethodSettings(tracer.className);
        if(methodSettings != null)
        {
          tracer.methodSettings.put(publisher, methodSettings);
        }
      }
    }
  }

  /**
   * Removes the settings for the provided publisher in all existing tracers.
   *
   * @param publisher The debug log publisher to remove.
   */
  public static void removeTracerSettings(DebugLogPublisher publisher)
  {
    for(DebugTracer tracer : classTracers)
    {
      tracer.classSettings.remove(publisher);
      tracer.methodSettings.remove(publisher);
    }
  }


  /**
   * Indicates if debug logging is enabled.
   *
   * @return True if debug logging is enabled. False otherwise.
   */
  public static boolean debugEnabled()
  {
    return enabled;
  }

  /**
   * Retrieve the singleton instance of this class.
   *
   * @return The singleton instance of this logger.
   */
  public static DebugLogger getInstance()
  {
    return instance;
  }

  /**
   * Stub method for logging an arbitrary event in a method at the INFO level.
   * Implementation provided by AspectJ.
   *
   * @param msg the message to be logged.
   */
  public static void debugVerbose(String msg) {}


  /**
   * Stub method for logging an arbitrary event in a method at the INFO level.
   * Implementation provided by AspectJ.
   *
   * @param msg the message to be logged.
   */
  public static void debugInfo(String msg) {}


  /**
   * Stub method for logging an arbitrary event in a method at the WARNING
   * level. Implementation provided by AspectJ.
   *
   * @param msg the message to be logged.
   */
  public static void debugWarning(String msg) {}


  /**
   * Stub method for logging an arbitrary event in a method at the ERROR
   * level. Implementation provided by AspectJ.
   *
   * @param msg the message to be logged.
   */
  public static void debugError(String msg) {}


  /**
   * Stub method for logging an arbitrary event in a method at the INFO
   * level. Implementation provided by AspectJ.
   *
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugVerbose(String msg, Object... msgArgs) {}


  /**
   * Stub method for logging an arbitrary event in a method at the INFO
   * level. Implementation provided by AspectJ.
   *
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugInfo(String msg, Object... msgArgs) {}


  /**
   * Stub method for logging an arbitrary event in a method at the WARNING
   * level. Implementation provided by AspectJ.
   *
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugWarning(String msg, Object... msgArgs)
  {}


  /**
   * Stub method for logging an arbitrary event in a method at the ERROR
   * level. Implementation provided by AspectJ.
   *
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugError(String msg, Object... msgArgs)
  {}


  /**
   * Stub method for logging an arbitrary event in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param msg   The message to be logged.
   */
  public static void debugMessage(LogLevel level, String msg)
  {}


  /**
   * Stub method for logging an arbitrary event in a method.
   * Implementation provided by AspectJ.
   *
   * @param level   The level of the message being logged.
   * @param msg     The message to be formatted and logged.
   * @param msgArgs The set of arguments to use to replace tokens in the
   *                format string before it is returned.
   */
  public static void debugMessage(LogLevel level, String msg,
                                  Object... msgArgs) {}


  /**
   * Stub method for logging a caught exception in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param t     The exception caught.
   */
  public static void debugCaught(LogLevel level, Throwable t)
  {}

  /**
   * Stub method for logging a thrown exception in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param t     The exception being thrown.
   */
  public static void debugThrown(LogLevel level, Throwable t)
  {}


  /**
   * Stub method for logging an JE database access in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param status The JE return status code of the operation.
   * @param database The JE database handle operated on.
   * @param txn The JE transaction handle used in the operation.
   * @param key The database key operated on.
   * @param data The database value read or written.
   */
  public static void debugJEAccess(LogLevel level,
                                   OperationStatus status,
                                   Database database,
                                   Transaction txn,
                                   DatabaseEntry key, DatabaseEntry data) {}

  /**
   * Stub method for logging raw data in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param bytes The data to dump.
   */
  public static void debugData(LogLevel level, byte[] bytes) {}

  /**
   * Stub method for logging raw data in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param buffer The data to dump.
   */
  public static void debugData(LogLevel level, ByteBuffer buffer) {}

  /**
   * Stub method for logging a protocol element in a method.
   * Implementation provided by AspectJ.
   *
   * @param level The level of the message being logged.
   * @param element The protocol element to dump.
   */
  public static void debugProtocolElement(LogLevel level,
                                          ProtocolElement element) {}


  /**
   * Classes and methods annotated with @NoDebugTracing will not be weaved with
   * debug logging statements by AspectJ.
   */
  public @interface NoDebugTracing {}

  /**
   * Methods annotated with @NoEntryDebugTracing will not be weaved with
   * entry debug logging statements by AspectJ.
   */
  public @interface NoEntryDebugTracing {}

  /**
   * Methods annotated with @NoExitDebugTracing will not be weaved with
   * exit debug logging statements by AspectJ.
   */
  public @interface NoExitDebugTracing {}

  /**
   * Methods annotated with @TraceThrown will be weaved by AspectJ with
   * debug logging statements when an exception is thrown from the method.
   */
  public @interface TraceThrown {}

}
