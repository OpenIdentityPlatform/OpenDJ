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
import java.util.HashSet;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.List;
import java.util.Arrays;

import org.opends.messages.Message;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.std.meta.LocalDBBackendCfgDefn;
import org.opends.server.admin.DurationPropertyDefinition;
import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.PropertyDefinition;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.ConfigMessages.*;

/**
 * This class maps JE properties to configuration attributes.
 */
public class ConfigurableEnvironment
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The name of the attribute which configures the database cache size as a
   * percentage of Java VM heap size.
   */
  public static final String ATTR_DATABASE_CACHE_PERCENT =
       ConfigConstants.NAME_PREFIX_CFG + "db-cache-percent";

  /**
   * The name of the attribute which configures the database cache size as an
   * approximate number of bytes.
   */
  public static final String ATTR_DATABASE_CACHE_SIZE =
       ConfigConstants.NAME_PREFIX_CFG + "db-cache-size";

  /**
   * The name of the attribute which configures whether data updated by a
   * database transaction is forced to disk.
   */
  public static final String ATTR_DATABASE_TXN_NO_SYNC =
       ConfigConstants.NAME_PREFIX_CFG + "db-txn-no-sync";

  /**
   * The name of the attribute which configures whether data updated by a
   * database transaction is written from the Java VM to the O/S.
   */
  public static final String ATTR_DATABASE_TXN_WRITE_NO_SYNC =
       ConfigConstants.NAME_PREFIX_CFG + "db-txn-write-no-sync";

  /**
   * The name of the attribute which configures whether the database background
   * cleaner thread runs.
   */
  public static final String ATTR_DATABASE_RUN_CLEANER =
       ConfigConstants.NAME_PREFIX_CFG + "db-run-cleaner";

  /**
   * The name of the attribute which configures the minimum percentage of log
   * space that must be used in log files.
   */
  public static final String ATTR_CLEANER_MIN_UTILIZATION =
       ConfigConstants.NAME_PREFIX_CFG + "db-cleaner-min-utilization";

  /**
   * The name of the attribute which configures the maximum size of each
   * individual JE log file, in bytes.
   */
  public static final String ATTR_DATABASE_LOG_FILE_MAX =
       ConfigConstants.NAME_PREFIX_CFG + "db-log-file-max";

  /**
   * The name of the attribute which configures the database cache eviction
   * algorithm.
   */
  public static final String ATTR_EVICTOR_LRU_ONLY =
       ConfigConstants.NAME_PREFIX_CFG + "db-evictor-lru-only";

  /**
   * The name of the attribute which configures the number of nodes in one scan
   * of the database cache evictor.
   */
  public static final String ATTR_EVICTOR_NODES_PER_SCAN =
       ConfigConstants.NAME_PREFIX_CFG + "db-evictor-nodes-per-scan";


  /**
   * The name of the attribute which configures whether the logging file
   * handler will be on or off.
   */
  public static final String ATTR_LOGGING_FILE_HANDLER_ON =
       ConfigConstants.NAME_PREFIX_CFG + "db-logging-file-handler-on";


  /**
   * The name of the attribute which configures the trace logging message level.
   */
  public static final String ATTR_LOGGING_LEVEL =
       ConfigConstants.NAME_PREFIX_CFG + "db-logging-level";


  /**
   * The name of the attribute which configures how many bytes are written to
   * the log before the checkpointer runs.
   */
  public static final String ATTR_CHECKPOINTER_BYTES_INTERVAL =
       ConfigConstants.NAME_PREFIX_CFG + "db-checkpointer-bytes-interval";


  /**
   * The name of the attribute which configures the amount of time between
   * runs of the checkpointer.
   */
  public static final String ATTR_CHECKPOINTER_WAKEUP_INTERVAL =
       ConfigConstants.NAME_PREFIX_CFG +
       "db-checkpointer-wakeup-interval";


  /**
   * The name of the attribute which configures the number of lock tables.
   */
  public static final String ATTR_NUM_LOCK_TABLES =
       ConfigConstants.NAME_PREFIX_CFG + "db-num-lock-tables";


  /**
   * The name of the attribute which configures the number threads
   * allocated by the cleaner for log file processing.
   */
  public static final String ATTR_NUM_CLEANER_THREADS =
       ConfigConstants.NAME_PREFIX_CFG + "db-num-cleaner-threads";


  /**
   * The name of the attribute which may specify any native JE properties.
   */
  public static final String ATTR_JE_PROPERTY =
       ConfigConstants.NAME_PREFIX_CFG + "je-property";


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


  // Pulled from resource/admin/ABBREVIATIONS.xsl.  db is mose common.
  private static final List<String> ABBREVIATIONS = Arrays.asList(new String[]
          {"aci", "ip", "ssl", "dn", "rdn", "jmx", "smtp", "http",
           "https", "ldap", "ldaps", "ldif", "jdbc", "tcp", "tls",
           "pkcs11", "sasl", "gssapi", "md5", "je", "dse", "fifo",
           "vlv", "uuid", "md5", "sha1", "sha256", "sha384", "sha512",
           "tls", "db"});

  /*
   * e.g. db-cache-percent -> DBCachePercent
   */
  private static String propNametoCamlCase(String hyphenated)
  {
    String[] components = hyphenated.split("\\-");
    StringBuilder buffer = new StringBuilder();
    for (String component: components) {
      if (ABBREVIATIONS.contains(component)) {
        buffer.append(component.toUpperCase());
      } else {
        buffer.append(component.substring(0, 1).toUpperCase() +
                component.substring(1));
      }
    }
    return buffer.toString();
  }


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

    String methodBaseName = propNametoCamlCase(baseName);

    Class<LocalDBBackendCfg> configClass = LocalDBBackendCfg.class;
    LocalDBBackendCfgDefn defn = LocalDBBackendCfgDefn.getInstance();
    Class<? extends LocalDBBackendCfgDefn> defClass = defn.getClass();

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
  private static String getPropertyValue(LocalDBBackendCfg cfg, String attrName)
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
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

    // This parameter was set to false while diagnosing a Berkeley DB JE bug.
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
  public static EnvironmentConfig parseConfigEntry(LocalDBBackendCfg cfg)
       throws ConfigException
  {
    EnvironmentConfig envConfig = defaultConfig();

    // Handle the attributes that do not have a JE property.
    envConfig.setTxnNoSync(cfg.isDBTxnNoSync());
    envConfig.setTxnWriteNoSync(cfg.isDBTxnWriteNoSync());

    // Iterate through the config attributes associated with a JE property.
    for (Map.Entry<String, String> mapEntry : attrMap.entrySet())
    {
      String jeProperty = mapEntry.getKey();
      String attrName = mapEntry.getValue();

      String value = getPropertyValue(cfg, attrName);
      envConfig.setConfigParam(jeProperty, value);
    }

    // See if there are any native JE properties specified in the config
    // and if so try to parse, evaluate and set them.
    SortedSet<String> jeProperties = cfg.getJEProperty();
    try {
      envConfig = setJEProperties(envConfig, jeProperties, attrMap);
    } catch (ConfigException e) {
      throw e;
    }

    return envConfig;
  }



  /**
   * Parse, validate and set native JE environment properties for
   * a given environment config.
   *
   * @param  envConfig The JE environment config for which to set
   *                   the properties.
   * @param  jeProperties The JE environment properties to parse,
   *                      validate and set.
   * @param  configAttrMap Component supported JE properties to
   *                       their configuration attributes map.
   * @return An environment config instance with given properties
   *         set.
   * @throws ConfigException If there is an error while parsing,
   *         validating and setting any of the properties provided.
   */
  public static EnvironmentConfig setJEProperties(EnvironmentConfig envConfig,
    SortedSet<String> jeProperties, HashMap<String, String> configAttrMap)
    throws ConfigException
  {
    if (jeProperties.isEmpty()) {
      // return default config.
      return envConfig;
    }

    // Set to catch duplicate properties.
    HashSet<String> uniqueJEProperties = new HashSet<String>();

    // Iterate through the config values associated with a JE property.
    for (String jeEntry : jeProperties)
    {
      StringTokenizer st = new StringTokenizer(jeEntry, "=");
      if (st.countTokens() == 2) {
        String jePropertyName = st.nextToken();
        String jePropertyValue = st.nextToken();
        // Check if it is a duplicate.
        if (uniqueJEProperties.contains(jePropertyName)) {
          Message message = ERR_CONFIG_JE_DUPLICATE_PROPERTY.get(
              jePropertyName);
            throw new ConfigException(message);
        }
        // Set JE property.
        try {
          envConfig.setConfigParam(jePropertyName, jePropertyValue);
          // This is a special case that JE cannot validate before
          // actually setting it. Validate it before it gets to JE.
          if (jePropertyName.equals("java.util.logging.level")) {
            java.util.logging.Level.parse(jePropertyValue);
          }
          // If this property shadows an existing config attribute.
          if (configAttrMap.containsKey(jePropertyName)) {
            Message message = ERR_CONFIG_JE_PROPERTY_SHADOWS_CONFIG.get(
              jePropertyName, attrMap.get(jePropertyName));
            throw new ConfigException(message);
          }
          // Add this property to unique set.
          uniqueJEProperties.add(jePropertyName);
        } catch(IllegalArgumentException e) {
          if (debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
          Message message =
            ERR_CONFIG_JE_PROPERTY_INVALID.get(
            jeEntry, e.getMessage());
          throw new ConfigException(message, e.getCause());
        }
      } else {
        Message message =
          ERR_CONFIG_JE_PROPERTY_INVALID_FORM.get(jeEntry);
        throw new ConfigException(message);
      }
    }

    return envConfig;
  }



}
