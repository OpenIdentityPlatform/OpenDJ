/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.server.DebugLogPublisherCfg;

/**
 * This class defines the set of methods and structures that must be
 * implemented for a Directory Server debug log publisher.
 *
 * @param  <T>  The type of debug log publisher configuration handled
 *              by this log publisher implementation.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class DebugLogPublisher<T extends DebugLogPublisherCfg>
    implements LogPublisher<T>
{
  /** The default global settings key. */
  private static final String GLOBAL= "_global";

  /** The map of class names to their trace settings. */
  private Map<String,TraceSettings> classTraceSettings;

  /** The map of class names to their method trace settings. */
  private Map<String,Map<String,TraceSettings>> methodTraceSettings;



  /** Construct a default configuration where the global scope will only log at the ERROR level. */
  protected DebugLogPublisher()
  {
    classTraceSettings = null;
    methodTraceSettings = null;

    //Set the global settings so that nothing is logged.
    addTraceSettings(null, TraceSettings.DISABLED);
  }



  @Override
  public boolean isConfigurationAcceptable(T configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation. It should be overridden by debug log publisher
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Gets the method trace levels for a specified class.
   *
   * @param  className  The fully-qualified name of the class for
   *                    which to get the trace levels.
   *
   *@return  An unmodifiable map of trace levels keyed by method name,
   *         or {@code null} if no method-level tracing is configured
   *         for the scope.
   */
  final Map<String,TraceSettings> getMethodSettings(
                                              String className)
  {
    if(methodTraceSettings == null)
    {
      return null;
    }
    else
    {
      return methodTraceSettings.get(className);
    }
  }



  /**
   * Get the trace settings for a specified class.
   *
   * @param className
   *          The fully-qualified name of the class for which to get the trace
   *          levels.
   * @return The current trace settings for the class.
   */
  final TraceSettings getClassSettings(String className)
  {
    TraceSettings settings = null;
    if (classTraceSettings != null)
    {
      // Find most specific trace setting
      // which covers this fully qualified class name
      // Search up the hierarchy for a match.
      String searchName = className;
      settings = classTraceSettings.get(searchName);
      while (settings == null && searchName != null)
      {
        int clipPoint = searchName.lastIndexOf('$');
        if (clipPoint == -1)
        {
          clipPoint = searchName.lastIndexOf('.');
        }
        if (clipPoint != -1)
        {
          searchName = searchName.substring(0, clipPoint);
          settings = classTraceSettings.get(searchName);
        }
        else
        {
          searchName = null;
        }
      }
      // Try global settings
      // only if no specific target is defined
      if (settings == null && classTraceSettings.size()==1) {
        settings = classTraceSettings.get(GLOBAL);
      }
    }
    return settings == null ? TraceSettings.DISABLED : settings;
  }



  /**
   * Adds a trace settings to the current set for a specified scope.
   * If a scope is not specified, the settings will be set for the
   * global scope. The global scope settings are used when no other
   * scope matches.
   *
   * @param  scope     The scope for which to set the trace settings.
   *                   This should be a fully-qualified class name, or
   *                   {@code null} to set the trace settings for the
   *                   global scope.
   * @param  settings  The trace settings for the specified scope.
   */
  public final void addTraceSettings(String scope, TraceSettings settings)
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
   * Determine whether a trace setting is already defined for a particular
   * scope.
   *
   * @param scope
   *          The scope for which to make the determination. This should be a
   *          fully-qualified class name.
   * @return {@code true} if a trace settings is defined for the specified
   *         scope, {@code false} otherwise.
   */
  final boolean hasTraceSettings(String scope)
  {
    int methodPt = scope.lastIndexOf('#');
    if (methodPt != -1)
    {
      String methodName = scope.substring(methodPt + 1);
      scope = scope.substring(0, methodPt);
      if (methodTraceSettings != null)
      {
        Map<String, TraceSettings> methodLevels =
            methodTraceSettings.get(scope);
        if (methodLevels != null)
        {
          return methodLevels.containsKey(methodName);
        }
      }
    }
    else if (classTraceSettings != null)
    {
      return classTraceSettings.containsKey(scope);
    }
    return false;
  }



  /**
   * Remove a trace setting by scope.
   *
   * @param  scope  The scope for which to remove the trace setting.
   *                This should be a fully-qualified class name, or
   *                {@code null} to remove the trace setting for the
   *                global scope.
   *
   * @return  The trace settings for the specified scope, or
   *          {@code null} if no trace setting is defined for that
   *          scope.
   */
  final TraceSettings removeTraceSettings(String scope)
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
   * @param  className  The class name.
   * @param  settings   The trace settings for the class.
   */
  private final synchronized void setClassSettings(String className, TraceSettings settings)
  {
    if (classTraceSettings == null)
    {
      classTraceSettings = new HashMap<>();
    }
    classTraceSettings.put(className, settings);
  }



  /**
   * Set the method settings for a particular method in a class.
   *
   * @param  className   The class name.
   * @param  methodName  The method name.
   * @param  settings    The trace settings for the method.
   */
  private final synchronized void setMethodSettings(String className,
      String methodName, TraceSettings settings)
  {
    if (methodTraceSettings == null) {
      methodTraceSettings = new HashMap<>();
    }
    Map<String, TraceSettings> methodLevels = methodTraceSettings.get(className);
    if (methodLevels == null)
    {
      methodLevels = new TreeMap<>();
      methodTraceSettings.put(className, methodLevels);
    }
    methodLevels.put(methodName, settings);
  }



  /**
   * Log an arbitrary event in a method.
   * @param  settings        The current trace settings in effect.
   * @param  signature       The method signature.
   * @param  sourceLocation  The location of the method in the source.
   * @param  msg             The message to be logged.
   * @param  stackTrace      The stack trace at the time the message
   *                         is logged or null if its not available.
   */
  public abstract void trace(TraceSettings settings, String signature,
      String sourceLocation, String msg, StackTraceElement[] stackTrace);



  /**
   * Log a caught exception in a method.
   * @param  settings        The current trace settings in effect.
   * @param  signature       The method signature.
   * @param  sourceLocation  The location of the method in the source.
   * @param  msg             The message to be logged.
   * @param  ex              The exception that was caught.
   * @param  stackTrace      The stack trace at the time the exception
   *                         is caught or null if its not available.
   */
  public abstract void traceException(TraceSettings settings, String signature,
      String sourceLocation, String msg, Throwable ex,
      StackTraceElement[] stackTrace);

}
