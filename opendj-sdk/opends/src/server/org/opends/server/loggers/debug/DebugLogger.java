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
import org.opends.messages.Message;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import org.opends.server.api.DebugLogPublisher;
import org.opends.server.loggers.*;
import org.opends.server.types.*;
import org.opends.server.admin.std.server.DebugLogPublisherCfg;
import org.opends.server.admin.std.meta.DebugLogPublisherCfgDefn;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;

import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.StaticUtils.*;

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

  // The set of all DebugTracer instances.
  private static ConcurrentHashMap<String, DebugTracer> classTracers =
      new ConcurrentHashMap<String, DebugTracer>();

  // The set of debug loggers that have been registered with the server.  It
  // will initially be empty.
  private static CopyOnWriteArrayList<DebugLogPublisher> debugPublishers =
      new CopyOnWriteArrayList<DebugLogPublisher>();

  // Trace methods will use this static boolean to determine if debug is
  // enabled so to not incur the cost of calling debugPublishers.isEmtpty().
  static boolean enabled = false;

  // The singleton instance of this class for configuration purposes.
  static final DebugLogger instance = new DebugLogger();

  /**
   * Add an debug log publisher to the debug logger.
   *
   * @param publisher The error log publisher to add.
   */
  public synchronized static void addDebugLogPublisher(
      DebugLogPublisher publisher)
  {
    debugPublishers.add(publisher);

    updateTracerSettings();

    enabled = true;
  }

  /**
   * Remove an debug log publisher from the debug logger.
   *
   * @param publisher The debug log publisher to remove.
   * @return The publisher that was removed or null if it was not found.
   */
  public synchronized static boolean removeDebugLogPublisher(
      DebugLogPublisher publisher)
  {
    boolean removed = debugPublishers.remove(publisher);

    if(removed)
    {
      publisher.close();
    }

    updateTracerSettings();

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
    for(DebugLogPublisher publisher : debugPublishers)
    {
      publisher.close();
    }

    debugPublishers.clear();

    updateTracerSettings();

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

        addDebugLogPublisher(debugLogPublisher);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(DebugLogPublisherCfg config,
                                              List<Message> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(DebugLogPublisherCfg config,
                                              List<Message> unacceptableReasons)
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
    ArrayList<Message> messages = new ArrayList<Message>();

    config.addDebugChangeListener(this);

    if(config.isEnabled())
    {
      try
      {
        DebugLogPublisher debugLogPublisher =
            getDebugPublisher(config);

        addDebugLogPublisher(debugLogPublisher);
      }
      catch(ConfigException e)
      {
        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      catch (Exception e)
      {

        messages.add(ERR_CONFIG_LOGGER_CANNOT_CREATE_LOGGER.get(
                String.valueOf(config.dn().toString()),
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
    ArrayList<Message> messages = new ArrayList<Message>();

    DN dn = config.dn();

    DebugLogPublisher debugLogPublisher = null;
    for(DebugLogPublisher publisher : debugPublishers)
    {
      if(publisher.getDN().equals(dn))
      {
        debugLogPublisher = publisher;
      }
    }

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
        removeDebugLogPublisher(debugLogPublisher);
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(DebugLogPublisherCfg config,
                                             List<Message> unacceptableReasons)
  {
    DN dn = config.dn();

    DebugLogPublisher debugLogPublisher = null;
    for(DebugLogPublisher publisher : debugPublishers)
    {
      if(publisher.getDN().equals(dn))
      {
        debugLogPublisher = publisher;
      }
    }

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

    DebugLogPublisher debugLogPublisher = null;
    for(DebugLogPublisher publisher : debugPublishers)
    {
      if(publisher.getDN().equals(config.dn()))
      {
        debugLogPublisher = publisher;
      }
    }

    if(debugLogPublisher != null)
    {
      removeDebugLogPublisher(debugLogPublisher);
    }
    else
    {
      resultCode = ResultCode.NO_SUCH_OBJECT;
    }

    return new ConfigChangeResult(resultCode, adminActionRequired);
  }

  private boolean isJavaClassAcceptable(DebugLogPublisherCfg config,
                                        List<Message> unacceptableReasons)
  {
    String className = config.getJavaImplementationClass();
    DebugLogPublisherCfgDefn d = DebugLogPublisherCfgDefn.getInstance();
    ClassPropertyDefinition pd =
        d.getJavaImplementationClassPropertyDefinition();
    // Load the class and cast it to a DebugLogPublisher.
    DebugLogPublisher publisher = null;
    Class<? extends DebugLogPublisher> theClass;
    try {
      theClass = pd.loadClass(className, DebugLogPublisher.class);
      publisher = theClass.newInstance();
    } catch (Exception e) {
      Message message = ERR_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS.get(
              className,
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
      Method method = theClass.getMethod("isConfigurationAcceptable",
                                         DebugLogPublisherCfg.class,
                                         List.class);
      Boolean acceptable = (Boolean) method.invoke(publisher, config,
                                                   unacceptableReasons);

      if (! acceptable)
      {
        return false;
      }
    } catch (Exception e) {
      Message message = ERR_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS.get(
              className,
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
      Message message = ERR_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS.get(
          className, config.dn().toString(), stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS.get(
          className, config.dn().toString(), String.valueOf(e));
      throw new ConfigException(message, e);
    }

    // The debug publisher has been successfully initialized.
    return debugLogPublisher;
  }

  /**
   * Update all debug tracers with the settings in the registered
   * publishers.
   */
  static void updateTracerSettings()
  {
    DebugLogPublisher[] publishers =
        debugPublishers.toArray(new DebugLogPublisher[0]);

    for(DebugTracer tracer : classTracers.values())
    {
      tracer.updateSettings(publishers);
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
   * Creates a new Debug Tracer for the caller class and registers it
   * with the Debug Logger.
   *
   * @return The tracer created for the caller class.
   */
  public static DebugTracer getTracer()
  {
    DebugTracer tracer =
        new DebugTracer(debugPublishers.toArray(new DebugLogPublisher[0]));
    classTracers.put(tracer.getTracedClassName(), tracer);

    return tracer;
  }

  /**
   * Returns the registered Debug Tracer for a traced class.
   *
   * @param className The name of the class tracer to retrieve.
   * @return The tracer for the provided class or null if there are
   *         no tracers registered.
   */
  public static DebugTracer getTracer(String className)
  {
    return classTracers.get(className);
  }

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
