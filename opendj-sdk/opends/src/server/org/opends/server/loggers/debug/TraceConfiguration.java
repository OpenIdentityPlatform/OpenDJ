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
package org.opends.server.loggers.debug;

import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Collections;

/**
 * A LoggingConfiguration for the debug logging system.
 */
public class TraceConfiguration
{
  private static final String GLOBAL= "_global";

  private Map<String, TraceSettings> classTraceSettings;
  private Map<String, Map<String, TraceSettings>> methodTraceSettings;


  /**
   * Construct a default configuration where all settings are disabled.
   */
  public TraceConfiguration()
  {
    classTraceSettings = null;
    methodTraceSettings = null;
  }

  /**
   * Gets the method trace levels for a specified class.
   * @param className - a fully qualified class name to get method trace
   * levels for
   * @return an unmodifiable map of trace levels keyed by method name.  If
   * no method level tracing is configured for the scope, <b>null</b> is
   * returned.
   */
  public Map<String, TraceSettings> getMethodSettings(String className)
  {
    Map<String, TraceSettings> levels = null;

    if (DebugLogger.enabled && methodTraceSettings != null) {
      // Method levels are always at leaves in the
      // hierarchy, so don't bother searching up.
      Map<String, TraceSettings> value= methodTraceSettings.get(className);
      if (value != null ) {
        levels= value;
      }
    }
    return levels != null ? Collections.unmodifiableMap(levels) : null;
  }

  /**
   * Get the trace settings for a specified class.
   * @param className - a fully qualified class name to get the
   * trace level for
   * @return the current trace settings for the class.
   */
  public TraceSettings getTraceSettings(String className)
  {
    TraceSettings settings = TraceSettings.DISABLED;

    // If we're not enabled, trace level is DISABLED.
    if (DebugLogger.enabled  && classTraceSettings != null) {
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
   * @param scope - the scope to set trace settings for; this is a fully
   * qualified class name.
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

  private synchronized void setClassSettings(String className,
                                             TraceSettings settings)
  {
    if(classTraceSettings == null) classTraceSettings =
        new HashMap<String, TraceSettings>();

    classTraceSettings.put(className, settings);
  }

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
}
