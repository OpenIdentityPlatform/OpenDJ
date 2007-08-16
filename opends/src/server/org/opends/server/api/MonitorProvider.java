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
package org.opends.server.api;
import org.opends.messages.Message;



import java.util.List;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ObjectClass;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that can provide usage,
 * performance, availability, or other kinds of monitor information
 * to clients.
 *
 * @param  <T>  The type of configuration handled by this monitor
 *              provider.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class MonitorProvider<T extends MonitorProviderCfg>
       extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Indicates whether a request has been received to stop running.
  private boolean stopRequested;

  // The thread used to run this monitor provider.
  private Thread monitorThread;



  /**
   * Initializes this monitor provider.  Note that no initialization
   * should be done here, since it should be performed in the
   * {@code initializeMonitorProvider} class.
   *
   * @param  threadName  The name to use for this thread for debugging
   *                     purposes.
   */
  protected MonitorProvider(String threadName)
  {
    super(threadName);


    stopRequested = false;
    monitorThread = null;
  }



  /**
   * Initializes this monitor provider based on the information in the
   * provided configuration entry.
   *
   * @param  configuration  The configuration to use to initialize
   *                        this monitor provider.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeMonitorProvider(T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this monitor provider.  It should be possible to call this method
   * on an uninitialized monitor provider instance in order to
   * determine whether the monitor provider would be able to use the
   * provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The monitor provider configuration
   *                              for which to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this monitor provider, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      MonitorProviderCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by monitor provider
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Finalizes this monitor provider so that it may be unloaded and
   * taken out of service.  This method should be overridden by any
   * monitor provider that has resources that should be released when
   * the monitor is no longer needed.  Any monitor that does override
   * this method must first invoke this version by calling
   * {@code super.finalizeMonitorProvider}.
   */
  public void finalizeMonitorProvider()
  {
    // Signal the monitor thread that it should stop.
    stopRequested = true;

    try
    {
      if (monitorThread != null)
      {
        monitorThread.interrupt();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Retrieves the name of this monitor provider.  It should be unique
   * among all monitor providers, including all instances of the same
   * monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  public abstract String getMonitorInstanceName();



  /**
   * Retrieves the objectclass that should be included in the monitor
   * entry created from this monitor provider.  This may be overridden
   * by subclasses that wish to include their own custom objectclass
   * in the monitor entry (e.g., to make it easier to search for
   * monitor entries of that type).  The default implementation
   * returns the "extensibleObject" objectclass.
   *
   * @return  The objectclass that should be included in the monitor
   *          entry created from this monitor provider.
   */
  public ObjectClass getMonitorObjectClass()
  {
    return DirectoryConfig.getObjectClass(OC_EXTENSIBLE_OBJECT_LC,
                                          true);
  }



  /**
   * Retrieves the length of time in milliseconds that should elapse
   * between calls to the {@code updateMonitorData} method.  A
   * negative or zero return value indicates that the
   * {@code updateMonitorData} method should not be periodically
   * invoked.
   *
   * @return  The length of time in milliseconds that should elapse
   *          between calls to the {@code updateMonitorData()} method.
   */
  public abstract long getUpdateInterval();



  /**
   * Performs any processing periodic processing that may be desired
   * to update the information associated with this monitor.  Note
   * that best-effort attempts will be made to ensure that calls to
   * this method come {@code getUpdateInterval} milliseconds apart,
   * but no guarantees will be made.
   */
  public abstract void updateMonitorData();



  /**
   * Retrieves a set of attributes containing monitor data that should
   * be returned to the client if the corresponding monitor entry is
   * requested.
   *
   * @return  A set of attributes containing monitor data that should
   *          be returned to the client if the corresponding monitor
   *          entry is requested.
   */
  public abstract List<Attribute> getMonitorData();



  /**
   * Enters a loop, periodically invoking the
   * {@code getUpdateInterval} method to updates the data associated
   * with this monitor.
   */
  public final void run()
  {
    monitorThread = Thread.currentThread();


    // If this monitor should not perform any checks to periodically
    // update its information, then there is no need to run this
    // method.
    if (getUpdateInterval() <= 0)
    {
      return;
    }


    // Set the name of this thread for debugging purposes.
    setName(getMonitorInstanceName() + " Monitor Provider");


    // Operate in a loop until it is detected that the server is
    // shutting down.
    while (! stopRequested)
    {
      long stopSleepTime =
           System.currentTimeMillis() + getUpdateInterval();
      updateMonitorData();

      long remainingSleepTime =
           stopSleepTime - System.currentTimeMillis();
      while ((! stopRequested) && (remainingSleepTime > 0))
      {
        if (remainingSleepTime > 1000)
        {
          try
          {
            Thread.sleep(1000);
          } catch (Exception e) {}
        }
        else
        {
          try
          {
            Thread.sleep(remainingSleepTime);
          } catch (Exception e) {}
        }

        remainingSleepTime =
             stopSleepTime - System.currentTimeMillis();
      }
    }
  }
}

