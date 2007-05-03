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

import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.debug.TraceSettings;
import org.opends.server.types.*;
import org.opends.server.admin.std.server.DebugLogPublisherCfg;
import org.opends.server.config.ConfigException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

import java.util.*;

/**
 * This class defines the set of methods and structures that must be
 * implemented for a Directory Server debug log publisher.
 *
 * @param <T> The type of debug log publisher configuration handled
 *            by this log publisher implementation.
 */
public abstract class DebugLogPublisher
    <T extends DebugLogPublisherCfg>
{
  //The default global settings key.
  private static final String GLOBAL= "_global";

  //The map of class names to their trace settings.
  private Map<String, TraceSettings> classTraceSettings;

  //The map of class names to their method trace settings.
  private Map<String, Map<String, TraceSettings>> methodTraceSettings;

  /**
   * Construct a default configuration where the global scope will
   * only log at the ERROR level.
   */
  protected DebugLogPublisher()
  {
    classTraceSettings = null;
    methodTraceSettings = null;

    //Set the global settings so that only errors are logged.
    addTraceSettings(null, new TraceSettings(DebugLogLevel.ERROR));
  }

  /**
   * Initializes this debug publisher provider based on the
   * information in the provided debug publisher configuration.
   *
   * @param config
   *          The debug publisher configuration that contains the
   *          information to use to initialize this debug publisher.
   * @throws org.opends.server.config.ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws org.opends.server.types.InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public abstract void initializeDebugLogPublisher(T config)
      throws ConfigException, InitializationException;

  /**
   * Gets the method trace levels for a specified class.
   * @param className - a fully qualified class name to get method
   * trace levels for
   * @return an unmodifiable map of trace levels keyed by method name.
   * If no method level tracing is configured for the scope,
   * <b>null</b> is returned.
   */
  public Map<String, TraceSettings> getMethodSettings(
      String className)
  {
    /*if (methodTraceSettings != null) {
      // Method levels are always at leaves in the
      // hierarchy, so don't bother searching up.
      return methodTraceSettings.get(className);
    }
    return null;*/
    return new HashMap<String, TraceSettings>();
  }

  /**
   * Get the trace settings for a specified class.
   * @param className - a fully qualified class name to get the
   * trace level for
   * @return the current trace settings for the class.
   */
  public TraceSettings getClassSettings(String className)
  {
    TraceSettings settings = TraceSettings.DISABLED;

    // If we're not enabled, trace level is DISABLED.
    if (classTraceSettings != null) {
      // Find most specific trace setting which covers this
      // fully qualified class name
      // Search up the hierarchy for a match.
      String searchName= className;
      Object value= null;
      value= classTraceSettings.get(searchName);
      while (value == null && searchName != null) {
        int clipPoint= searchName.lastIndexOf('$');
        if (clipPoint == -1) clipPoint= searchName.lastIndexOf('.');
        if (clipPoint != -1) {
          searchName= searchName.substring(0, clipPoint);
          value= classTraceSettings.get(searchName);
        }
        else {
          searchName= null;
        }
      }

      // Use global settings, if nothing more specific was found.
      if (value == null) value= classTraceSettings.get(GLOBAL);

      if (value != null) {
        settings= (TraceSettings)value;
      }
    }
    return settings;
  }

  /**
   * Adds a trace settings to the current set for a specified scope.
   * If a scope is not specified, the settings will be set for the
   * global scope. The global scope settings are used when no other
   * scope matches.
   *
   * @param scope - the scope to set trace settings for; this is a
   * fully qualified class name or null to set the trace settings for
   * the global scope.
   * @param settings - the trace settings for the scope
   */
  public void addTraceSettings(String scope, TraceSettings settings)
  {
    if (scope == null) {
      setClassSettings(GLOBAL, settings);
    }
    else {
      int methodPt= scope.lastIndexOf('#');
      if (methodPt != -1) {
        String methodName= scope.substring(methodPt+1);
        scope= scope.substring(0, methodPt);
        setMethodSettings(scope, methodName, settings);
      }
      else {
        setClassSettings(scope, settings);
      }
    }
  }

  /**
   * See if a trace setting is alreadly defined for a particular
   * scope.
   *
   * @param scope - the scope to test; this is a fully
   * qualified class name or null to set the trace settings for the
   * globalscope.
   * @return The trace settings for that scope or null if it doesn't
   *       exist.
   */
  public TraceSettings getTraceSettings(String scope)
  {
    if (scope == null) {
      if(classTraceSettings != null)
      {
        return classTraceSettings.get(GLOBAL);
      }
      return null;
    }
    else {
      int methodPt= scope.lastIndexOf('#');
      if (methodPt != -1) {
        String methodName= scope.substring(methodPt+1);
        scope= scope.substring(0, methodPt);
        if(methodTraceSettings != null)
        {
          Map<String, TraceSettings> methodLevels =
              methodTraceSettings.get(scope);
          if(methodLevels != null)
          {
            return methodLevels.get(methodName);
          }
          return null;
        }
        return null;
      }
      else {
        if(classTraceSettings != null)
        {
          return classTraceSettings.get(scope);
        }
        return null;
      }
    }
  }

  /**
   * Remove a trace setting by scope.
   *
   * @param scope - the scope to remove; this is a fully
   * qualified class name or null to set the trace settings for the
   * global scope.
   * @return The trace settings for that scope or null if it doesn't
   *       exist.
   */
  public TraceSettings removeTraceSettings(String scope)
  {
    TraceSettings removedSettings = null;
    if (scope == null) {
      if(classTraceSettings != null)
      {
        removedSettings =  classTraceSettings.remove(GLOBAL);
      }
    }
    else {
      int methodPt= scope.lastIndexOf('#');
      if (methodPt != -1) {
        String methodName= scope.substring(methodPt+1);
        scope= scope.substring(0, methodPt);
        if(methodTraceSettings != null)
        {
          Map<String, TraceSettings> methodLevels =
              methodTraceSettings.get(scope);
          if(methodLevels != null)
          {
            removedSettings = methodLevels.remove(methodName);
            if(methodLevels.isEmpty())
            {
              methodTraceSettings.remove(scope);
            }
          }
        }
      }
      else {
        if(classTraceSettings != null)
        {
          removedSettings =  classTraceSettings.remove(scope);
        }
      }
    }

    return removedSettings;
  }

  /**
   * Set the trace settings for a class.
   *
   * @param className The class name.
   * @param settings  The trace settings for the class.
   */
  private synchronized void setClassSettings(String className,
                                             TraceSettings settings)
  {
    if(classTraceSettings == null) classTraceSettings =
        new HashMap<String, TraceSettings>();

    classTraceSettings.put(className, settings);
  }

  /**
   * Set the method settings for a particular method in a class.
   *
   * @param className The class name.
   * @param methodName The method name.
   * @param settings The trace settings for the method.
   */
  private synchronized void setMethodSettings(String className,
                                              String methodName,
                                              TraceSettings settings)
  {
    if (methodTraceSettings == null) methodTraceSettings =
        new HashMap<String, Map<String, TraceSettings>>();
    Map<String, TraceSettings> methodLevels=
        methodTraceSettings.get(className);
    if (methodLevels == null) {
      methodLevels= new TreeMap<String, TraceSettings>();
      methodTraceSettings.put(className, methodLevels);
    }

    methodLevels.put(methodName, settings);
  }

  /**
   * Log a constructor entry.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param args The parameters provided to the constructor call.
   */
  public abstract void traceConstructor(LogLevel level,
                                        TraceSettings settings,
                                        String signature,
                                        String sourceLocation,
                                        Object[] args);

  /**
   * Log a non static method entry.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param obj the object this method operations on.
   * @param args The parameters provided to the constructor call
   */
  public abstract void traceNonStaticMethodEntry(LogLevel level,
                                               TraceSettings settings,
                                               String signature,
                                               String sourceLocation,
                                               Object obj,
                                               Object[] args);

  /**
   * Log a static method entry.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param args The parameters provided to the constructor call
   */
  public abstract void traceStaticMethodEntry(LogLevel level,
                                              TraceSettings settings,
                                              String signature,
                                              String sourceLocation,
                                              Object[] args);

  /**
   * Log a method return.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param ret the return value of the method.
   */
  public abstract void traceReturn(LogLevel level,
                                   TraceSettings settings,
                                   String signature,
                                   String sourceLocation,
                                   Object ret);

  /**
   * Log an arbitrary event in a method.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param msg message to format and log.
   */
  public abstract void traceMessage(LogLevel level,
                                    TraceSettings settings,
                                    String signature,
                                    String sourceLocation,
                                    String msg);

  /**
   * Log a thrown exception in a method.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param ex the exception thrown.
   */
  public abstract void traceThrown(LogLevel level,
                                   TraceSettings settings,
                                   String signature,
                                   String sourceLocation,
                                   Throwable ex);

  /**
   * Log a caught exception in a method.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param ex the exception caught.
   */
  public abstract void traceCaught(LogLevel level,
                                   TraceSettings settings,
                                   String signature,
                                   String sourceLocation,
                                   Throwable ex);

  /**
   * Log an JE database access in a method.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param status status of the JE operation.
   * @param database the database handle.
   * @param txn  transaction handle (may be null).
   * @param key  the key to dump.
   * @param data the data to dump.
   */
  public abstract void traceJEAccess(LogLevel level,
                                     TraceSettings settings,
                                     String signature,
                                     String sourceLocation,
                                     OperationStatus status,
                                     Database database,
                                     Transaction txn,
                                     DatabaseEntry key,
                                     DatabaseEntry data);

  /**
   * Log raw data in a method.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param data the data to dump.
   */
  public abstract void traceData(LogLevel level,
                                 TraceSettings settings,
                                 String signature,
                                 String sourceLocation,
                                 byte[] data);

  /**
   * Log a protocol element in a method.
   *
   * @param level The level of the log message.
   * @param settings The current trace settings in effect.
   * @param signature The constuctor method signature.
   * @param sourceLocation The location of the method in the source.
   * @param element the protocol element to dump.
   */
  public abstract void traceProtocolElement(LogLevel level,
                                            TraceSettings settings,
                                            String signature,
                                            String sourceLocation,
                                            ProtocolElement element);

  /**
   * Close this publisher.
   */
  public abstract void close();
}
