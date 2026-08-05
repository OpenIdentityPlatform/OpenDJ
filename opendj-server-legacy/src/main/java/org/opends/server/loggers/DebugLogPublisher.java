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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.loggers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

  /**
   * The map of class names to their trace settings.
   * <p>
   * The getters below read this map without any lock, so it is concurrent and
   * created eagerly, which makes it safely published to every reader. Updates
   * rely on the atomic operations of the map rather than on locking.
   */
  private final Map<String,TraceSettings> classTraceSettings = new ConcurrentHashMap<>();

  /**
   * The map of class names to their method trace settings.
   * <p>
   * Concurrent for the same reason as {@link #classTraceSettings}. The nested maps
   * are concurrent as well: {@link DebugTracer} keeps the one returned by
   * {@link #getMethodSettings(String)} and iterates it while another thread may be
   * reconfiguring the publisher.
   */
  private final Map<String,Map<String,TraceSettings>> methodTraceSettings = new ConcurrentHashMap<>();



  /** Construct a default configuration where the global scope will only log at the ERROR level. */
  protected DebugLogPublisher()
  {
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
   *@return  A live, concurrent map of trace levels keyed by method
   *         name, or {@code null} if no method-level tracing is
   *         configured for the scope.
   */
  final Map<String, TraceSettings> getMethodSettings(String className)
  {
    return methodTraceSettings.get(className);
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
    // Find most specific trace setting
    // which covers this fully qualified class name
    // Search up the hierarchy for a match.
    String searchName = className;
    TraceSettings settings = classTraceSettings.get(searchName);
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
   *                   Must not be {@code null}.
   * @throws NullPointerException  If {@code settings} is {@code null}: the trace
   *                   settings are held in concurrent maps, which do not accept
   *                   null values.
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
      Map<String, TraceSettings> methodLevels = methodTraceSettings.get(scope);
      if (methodLevels != null)
      {
        return methodLevels.containsKey(methodName);
      }
      return false;
    }
    return classTraceSettings.containsKey(scope);
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
    if (scope == null) {
      return classTraceSettings.remove(GLOBAL);
    }
    int methodPt= scope.lastIndexOf('#');
    if (methodPt == -1) {
      return classTraceSettings.remove(scope);
    }
    final String methodName= scope.substring(methodPt+1);
    // Drop the enclosing map once its last method setting is gone. This must be
    // atomic with respect to setMethodSettings(), which computes on the same key,
    // otherwise a concurrently added setting could be discarded along with the map.
    final TraceSettings[] removedSettings = new TraceSettings[1];
    methodTraceSettings.compute(scope.substring(0, methodPt), (className, methodLevels) -> {
      if (methodLevels == null)
      {
        return null;
      }
      removedSettings[0] = methodLevels.remove(methodName);
      return methodLevels.isEmpty() ? null : methodLevels;
    });
    return removedSettings[0];
  }

  /**
   * Set the trace settings for a class.
   *
   * @param  className  The class name.
   * @param  settings   The trace settings for the class.
   */
  private void setClassSettings(String className, TraceSettings settings)
  {
    classTraceSettings.put(className, settings);
  }



  /**
   * Set the method settings for a particular method in a class.
   *
   * @param  className   The class name.
   * @param  methodName  The method name.
   * @param  settings    The trace settings for the method.
   */
  private void setMethodSettings(String className, String methodName, TraceSettings settings)
  {
    // Create the enclosing map and add the setting atomically, see removeTraceSettings().
    methodTraceSettings.compute(className, (name, methodLevels) -> {
      Map<String, TraceSettings> levels = methodLevels != null ? methodLevels : new ConcurrentHashMap<>();
      levels.put(methodName, settings);
      return levels;
    });
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
