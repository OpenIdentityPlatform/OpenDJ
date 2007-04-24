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
package org.opends.server.backends.jeb;

import com.sleepycat.je.EnvironmentConfig;

import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DebugLogLevel;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.admin.std.server.JEBackendCfg;
import org.opends.server.admin.std.meta.JEBackendCfgDefn;
import org.opends.server.admin.DurationPropertyDefinition;
import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.PropertyDefinition;

/**
 * This class maps JE properties to configuration attributes.
 */
public class ConfigurableEnvironment
{
  /**
   * The name of the attribute which configures the database cache size as a
   * percentage of Java VM heap size.
   */
  public static final String ATTR_DATABASE_CACHE_PERCENT =
       ConfigConstants.NAME_PREFIX_CFG + "database-cache-percent";

  /**
   * The name of the attribute which configures the database cache size as an
   * approximate number of bytes.
   */
  public static final String ATTR_DATABASE_CACHE_SIZE =
       ConfigConstants.NAME_PREFIX_CFG + "database-cache-size";

  /**
   * The name of the attribute which configures whether data updated by a
   * database transaction is forced to disk.
   */
  public static final String ATTR_DATABASE_TXN_NO_SYNC =
       ConfigConstants.NAME_PREFIX_CFG + "database-txn-no-sync";

  /**
   * The name of the attribute which configures whether data updated by a
   * database transaction is written from the Java VM to the O/S.
   */
  public static final String ATTR_DATABASE_TXN_WRITE_NO_SYNC =
       ConfigConstants.NAME_PREFIX_CFG + "database-txn-write-no-sync";

  /**
   * The name of the attribute which configures whether the database background
   * cleaner thread runs.
   */
  public static final String ATTR_DATABASE_RUN_CLEANER =
       ConfigConstants.NAME_PREFIX_CFG + "database-run-cleaner";

  /**
   * The name of the attribute which configures the minimum percentage of log
   * space that must be used in log files.
   */
  public static final String ATTR_CLEANER_MIN_UTILIZATION =
       ConfigConstants.NAME_PREFIX_CFG + "database-cleaner-min-utilization";

  /**
   * The name of the attribute which configures the maximum size of each
   * individual JE log file, in bytes.
   */
  public static final String ATTR_DATABASE_LOG_FILE_MAX =
       ConfigConstants.NAME_PREFIX_CFG + "database-log-file-max";

  /**
   * The name of the attribute which configures the database cache eviction
   * algorithm.
   */
  public static final String ATTR_EVICTOR_LRU_ONLY =
       ConfigConstants.NAME_PREFIX_CFG + "database-evictor-lru-only";

  /**
   * The name of the attribute which configures the number of nodes in one scan
   * of the database cache evictor.
   */
  public static final String ATTR_EVICTOR_NODES_PER_SCAN =
       ConfigConstants.NAME_PREFIX_CFG + "database-evictor-nodes-per-scan";


  /**
   * The name of the attribute which configures whether the logging file
   * handler will be on or off.
   */
  public static final String ATTR_LOGGING_FILE_HANDLER_ON =
       ConfigConstants.NAME_PREFIX_CFG + "database-logging-file-handler-on";


  /**
   * The name of the attribute which configures the trace logging message level.
   */
  public static final String ATTR_LOGGING_LEVEL =
       ConfigConstants.NAME_PREFIX_CFG + "database-logging-level";


  /**
   * The name of the attribute which configures how many bytes are written to
   * the log before the checkpointer runs.
   */
  public static final String ATTR_CHECKPOINTER_BYTES_INTERVAL =
       ConfigConstants.NAME_PREFIX_CFG + "database-checkpointer-bytes-interval";


  /**
   * The name of the attribute which configures the amount of time between
   * runs of the checkpointer.
   */
  public static final String ATTR_CHECKPOINTER_WAKEUP_INTERVAL =
       ConfigConstants.NAME_PREFIX_CFG +
       "database-checkpointer-wakeup-interval";


  /**
   * The name of the attribute which configures the number of lock tables.
   */
  public static final String ATTR_NUM_LOCK_TABLES =
       ConfigConstants.NAME_PREFIX_CFG + "database-lock-num-lock-tables";


  /**
   * The name of the attribute which configures the number threads
   * allocated by the cleaner for log file processing.
   */
  public static final String ATTR_NUM_CLEANER_THREADS =
       ConfigConstants.NAME_PREFIX_CFG + "database-cleaner-num-threads";


  /**
   * A map of JE property names to the corresponding configuration attribute.
   */
  private static HashMap<String, String> attrMap =
       new HashMap<String, String>();

  /**
   * A map of configuration attribute names to the corresponding configuration
   * object getter method.
   */
  private static HashMap<String,Method> methodMap =
       new HashMap<String, Method>();

  /**
   * A map of configuration attribute names to the corresponding configuration
   * PropertyDefinition.
   */
  private static HashMap<String,PropertyDefinition> defnMap =
       new HashMap<String, PropertyDefinition>();



  /**
   * Register a JE property and its corresponding configuration attribute.
   *
   * @param propertyName The name of the JE property to be registered.
   * @param attrName     The name of the configuration attribute associated
   *                     with the property.
   * @throws Exception   If there is an error in the attribute name.
   */
  private static void registerProp(String propertyName, String attrName)
       throws Exception
  {
    // Strip off NAME_PREFIX_CFG.
    String baseName = attrName.substring(7);


    // Convert hyphenated to camel case.
    StringBuilder builder = new StringBuilder();
    boolean capitalize = true;
    for (int i = 0; i < baseName.length(); i++)
    {
      char c = baseName.charAt(i);
      if (c == '-')
      {
        capitalize = true;
      }
      else
      {
        if (capitalize)
        {
          builder.append(Character.toUpperCase(c));
        }
        else
        {
          builder.append(c);
        }
        capitalize = false;
      }
    }
    String methodBaseName = builder.toString();

    Class<JEBackendCfg> configClass = JEBackendCfg.class;
    JEBackendCfgDefn defn = JEBackendCfgDefn.getInstance();
    Class<? extends JEBackendCfgDefn> defClass = defn.getClass();

    PropertyDefinition propDefn =
         (PropertyDefinition)defClass.getMethod("get" + methodBaseName +
         "PropertyDefinition").invoke(defn);

    String methodName;
    if (propDefn instanceof BooleanPropertyDefinition)
    {
      methodName = "is" + methodBaseName;
    }
    else
    {
      methodName = "get" + methodBaseName;
    }

    defnMap.put(attrName, propDefn);
    methodMap.put(attrName, configClass.getMethod(methodName));
    attrMap.put(propertyName, attrName);
  }


  /**
   * Get the name of the configuration attribute associated with a JE property.
   * @param jeProperty The name of the JE property.
   * @return The name of the associated configuration attribute.
   */
  public static String getAttributeForProperty(String jeProperty)
  {
    return attrMap.get(jeProperty);
  }

  /**
   * Get the value of a JE property that is mapped to a configuration attribute.
   * @param cfg The configuration containing the property values.
   * @param attrName The conriguration attribute type name.
   * @return The string value of the JE property.
   */
  private static String getPropertyValue(JEBackendCfg cfg, String attrName)
  {
    try
    {
      PropertyDefinition propDefn = defnMap.get(attrName);
      Method method = methodMap.get(attrName);

      if (propDefn instanceof DurationPropertyDefinition)
      {
        Long value = (Long)method.invoke(cfg);

        // JE durations are in microseconds so we must convert.
        DurationPropertyDefinition durationPropDefn =
             (DurationPropertyDefinition)propDefn;
        value = 1000*durationPropDefn.getBaseUnit().toMilliSeconds(value);

        return String.valueOf(value);
      }
      else
      {
        Object value = method.invoke(cfg);
        return String.valueOf(value);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      return "";
    }
  }



  static
  {
    // Register the parameters that have JE property names.
    try
    {
      registerProp("je.maxMemoryPercent", ATTR_DATABASE_CACHE_PERCENT);
      registerProp("je.maxMemory", ATTR_DATABASE_CACHE_SIZE);
      registerProp("je.cleaner.minUtilization", ATTR_CLEANER_MIN_UTILIZATION);
      registerProp("je.env.runCleaner", ATTR_DATABASE_RUN_CLEANER);
      registerProp("je.evictor.lruOnly", ATTR_EVICTOR_LRU_ONLY);
      registerProp("je.evictor.nodesPerScan", ATTR_EVICTOR_NODES_PER_SCAN);
      registerProp("je.log.fileMax", ATTR_DATABASE_LOG_FILE_MAX);
      registerProp("java.util.logging.FileHandler.on",
                   ATTR_LOGGING_FILE_HANDLER_ON);
      registerProp("java.util.logging.level", ATTR_LOGGING_LEVEL);
      registerProp("je.checkpointer.bytesInterval",
                   ATTR_CHECKPOINTER_BYTES_INTERVAL);
      registerProp("je.checkpointer.wakeupInterval",
                   ATTR_CHECKPOINTER_WAKEUP_INTERVAL);
      registerProp("je.lock.nLockTables", ATTR_NUM_LOCK_TABLES);
      registerProp("je.cleaner.threads", ATTR_NUM_CLEANER_THREADS);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Create a JE environment configuration with default values.
   *
   * @return A JE environment config containing default values.
   */
  public static EnvironmentConfig defaultConfig()
  {
    EnvironmentConfig envConfig = new EnvironmentConfig();

    envConfig.setTransactional(true);
    envConfig.setAllowCreate(true);

    // This property was introduced in JE 3.0.  Shared latches are now used on
    // all internal nodes of the b-tree, which increases concurrency for many
    // operations.
    envConfig.setConfigParam("je.env.sharedLatches", "true");

    // This parameter was set to false while diagnosing a Sleepycat bug.
    // Normally cleansed log files are deleted, but if this is set false
    // they are instead renamed from .jdb to .del.
    envConfig.setConfigParam("je.cleaner.expunge", "true");

    return envConfig;
  }



  /**
   * Parse a configuration associated with a JE environment and create an
   * environment config from it.
   *
   * @param cfg The configuration to be parsed.
   * @return An environment config instance corresponding to the config entry.
   * @throws ConfigException If there is an error in the provided configuration
   * entry.
   */
  public static EnvironmentConfig parseConfigEntry(JEBackendCfg cfg)
       throws ConfigException
  {
    EnvironmentConfig envConfig = defaultConfig();

    // Handle the attributes that do not have a JE property.
    envConfig.setTxnNoSync(cfg.isDatabaseTxnNoSync());
    envConfig.setTxnWriteNoSync(cfg.isDatabaseTxnWriteNoSync());

    // Iterate through the config attributes associated with a JE property.
    for (Map.Entry<String, String> mapEntry : attrMap.entrySet())
    {
      String jeProperty = mapEntry.getKey();
      String attrName = mapEntry.getValue();

      String value = getPropertyValue(cfg, attrName);
      envConfig.setConfigParam(jeProperty, value);
    }

    return envConfig;
  }



}
