/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2015 ForgeRock AS.
 */
package org.opends.server.backends.jeb;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.DurationPropertyDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.std.meta.LocalDBBackendCfgDefn;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.MemoryQuota;
import org.forgerock.opendj.config.server.ConfigException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.dbi.MemoryBudget;

import static com.sleepycat.je.EnvironmentConfig.*;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.BackendMessages.*;

/**
 * This class maps JE properties to configuration attributes.
 */
public class ConfigurableEnvironment
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
   * The name of the attribute which configures the minimum number of threads
   * of the database cache evictor pool.
   */
  public static final String ATTR_EVICTOR_CORE_THREADS =
       ConfigConstants.NAME_PREFIX_CFG + "db-evictor-core-threads";
  /**
   * The name of the attribute which configures the maximum number of threads
   * of the database cache evictor pool.
   */
  public static final String ATTR_EVICTOR_MAX_THREADS =
       ConfigConstants.NAME_PREFIX_CFG + "db-evictor-max-threads";

  /**
   * The name of the attribute which configures the time excess threads
   * of the database cache evictor pool are kept alive.
   */
  public static final String ATTR_EVICTOR_KEEP_ALIVE =
       ConfigConstants.NAME_PREFIX_CFG + "db-evictor-keep-alive";

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
   * The name of the attribute which configures the size of the file
   * handle cache.
   */
  public static final String ATTR_LOG_FILECACHE_SIZE =
       ConfigConstants.NAME_PREFIX_CFG + "db-log-filecache-size";


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


  /** Pulled from resource/admin/ABBREVIATIONS.xsl.  db is mose common. */
  private static final List<String> ABBREVIATIONS = Arrays.asList(new String[]
          {"aci", "ip", "ssl", "dn", "rdn", "jmx", "smtp", "http",
           "https", "ldap", "ldaps", "ldif", "jdbc", "tcp", "tls",
           "pkcs11", "sasl", "gssapi", "md5", "je", "dse", "fifo",
           "vlv", "uuid", "md5", "sha1", "sha256", "sha384", "sha512",
           "tls", "db"});

  /** E.g. db-cache-percent -> DBCachePercent */
  private static String propNametoCamlCase(String hyphenated)
  {
    String[] components = hyphenated.split("\\-");
    StringBuilder buffer = new StringBuilder();
    for (String component: components) {
      if (ABBREVIATIONS.contains(component)) {
        buffer.append(component.toUpperCase());
      } else {
        buffer.append(component.substring(0, 1).toUpperCase()).append(component.substring(1));
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

        if (attrName.equals(ATTR_NUM_CLEANER_THREADS) && value == null)
        {
          // Automatically choose based on the number of processors. We will use
          // similar heuristics to those used to define the default number of
          // worker threads.
          int cpus = Runtime.getRuntime().availableProcessors();
          value = Integer.valueOf(Math.max(24, cpus * 2));

          logger.debug(INFO_ERGONOMIC_SIZING_OF_JE_CLEANER_THREADS,
              cfg.dn().rdn().getAttributeValue(0), (Number) value);
        }
        else if (attrName.equals(ATTR_NUM_LOCK_TABLES)
            && value == null)
        {
          // Automatically choose based on the number of processors.
          // We'll assume that the user has also allowed automatic
          // configuration of cleaners and workers.
          int cpus = Runtime.getRuntime().availableProcessors();
          int cleaners = Math.max(24, cpus * 2);
          int workers = Math.max(24, cpus * 2);
          BigInteger tmp = BigInteger.valueOf((cleaners + workers) * 2);
          value = tmp.nextProbablePrime();

          logger.debug(INFO_ERGONOMIC_SIZING_OF_JE_LOCK_TABLES, cfg.dn().rdn().getAttributeValue(0), (Number) value);
        }

        return String.valueOf(value);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
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
      registerProp("je.evictor.coreThreads", ATTR_EVICTOR_CORE_THREADS);
      registerProp("je.evictor.maxThreads", ATTR_EVICTOR_MAX_THREADS);
      registerProp("je.evictor.keepAlive", ATTR_EVICTOR_KEEP_ALIVE);
      registerProp("je.log.fileMax", ATTR_DATABASE_LOG_FILE_MAX);
      registerProp("je.checkpointer.bytesInterval",
                   ATTR_CHECKPOINTER_BYTES_INTERVAL);
      registerProp("je.checkpointer.wakeupInterval",
                   ATTR_CHECKPOINTER_WAKEUP_INTERVAL);
      registerProp("je.lock.nLockTables", ATTR_NUM_LOCK_TABLES);
      registerProp("je.cleaner.threads", ATTR_NUM_CLEANER_THREADS);
      registerProp("je.log.fileCacheSize", ATTR_LOG_FILECACHE_SIZE);
    }
    catch (Exception e)
    {
      logger.traceException(e);
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

    // "je.env.sharedLatches" is "true" by default since JE #12136 (3.3.62?)

    // This parameter was set to false while diagnosing a Berkeley DB JE bug.
    // Normally cleansed log files are deleted, but if this is set false
    // they are instead renamed from .jdb to .del.
    envConfig.setConfigParam(CLEANER_EXPUNGE, "true");

    // Under heavy write load the check point can fall behind causing
    // uncontrolled DB growth over time. This parameter makes the out of
    // the box configuration more robust at the cost of a slight
    // reduction in maximum write throughput. Experiments have shown
    // that response time predictability is not impacted negatively.
    envConfig.setConfigParam(CHECKPOINTER_HIGH_PRIORITY, "true");

    // If the JVM is reasonably large then we can safely default to
    // bigger read buffers. This will result in more scalable checkpointer
    // and cleaner performance.
    if (Runtime.getRuntime().maxMemory() > 256 * 1024 * 1024)
    {
      envConfig.setConfigParam(CLEANER_LOOK_AHEAD_CACHE_SIZE,
          String.valueOf(2 * 1024 * 1024));
      envConfig.setConfigParam(LOG_ITERATOR_READ_SIZE,
          String.valueOf(2 * 1024 * 1024));
      envConfig.setConfigParam(LOG_FAULT_READ_SIZE, String.valueOf(4 * 1024));
    }

    // Disable lock timeouts, meaning that no lock wait
    // timelimit is enforced and a deadlocked operation
    // will block indefinitely.
    envConfig.setLockTimeout(0, TimeUnit.MICROSECONDS);

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
    // See if the db cache size setting is valid.
    if(cfg.getDBCacheSize() != 0)
    {
      if (MemoryBudget.getRuntimeMaxMemory() < cfg.getDBCacheSize()) {
        throw new ConfigException(
            ERR_BACKEND_CONFIG_CACHE_SIZE_GREATER_THAN_JVM_HEAP.get(
                cfg.getDBCacheSize(), MemoryBudget.getRuntimeMaxMemory()));
      }
      if (cfg.getDBCacheSize() < MemoryBudget.MIN_MAX_MEMORY_SIZE) {
        throw new ConfigException(
            ERR_CONFIG_JEB_CACHE_SIZE_TOO_SMALL.get(
                cfg.getDBCacheSize(), MemoryBudget.MIN_MAX_MEMORY_SIZE));
      }
      MemoryQuota memoryQuota = DirectoryServer.getInstance().getServerContext().getMemoryQuota();
      if (!memoryQuota.acquireMemory(cfg.getDBCacheSize()))
      {
        logger.warn(ERR_BACKEND_CONFIG_CACHE_SIZE_GREATER_THAN_JVM_HEAP.get(
            cfg.getDBCacheSize(), memoryQuota.getMaxMemory()));
      }
    }

    EnvironmentConfig envConfig = defaultConfig();

    // Durability settings.
    if (cfg.isDBTxnNoSync() && cfg.isDBTxnWriteNoSync())
    {
      throw new ConfigException(
              ERR_CONFIG_JEB_DURABILITY_CONFLICT.get());
    }
    if (cfg.isDBTxnNoSync())
    {
      envConfig.setDurability(Durability.COMMIT_NO_SYNC);
    }
    if (cfg.isDBTxnWriteNoSync())
    {
      envConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);
    }

    // Iterate through the config attributes associated with a JE property.
    for (Map.Entry<String, String> mapEntry : attrMap.entrySet())
    {
      String jeProperty = mapEntry.getKey();
      String attrName = mapEntry.getValue();

      String value = getPropertyValue(cfg, attrName);
      envConfig.setConfigParam(jeProperty, value);
    }

    // Set logging and file handler levels.
    Logger parent = Logger.getLogger("com.sleepycat.je");
    try
    {
      parent.setLevel(Level.parse(cfg.getDBLoggingLevel()));
    }
    catch (Exception e)
    {
      throw new ConfigException(ERR_JEB_INVALID_LOGGING_LEVEL.get(cfg.getDBLoggingLevel(), cfg.dn()));
    }

    final Level level = cfg.isDBLoggingFileHandlerOn() ? Level.ALL : Level.OFF;
    envConfig.setConfigParam(FILE_LOGGING_LEVEL, level.getName());

    // See if there are any native JE properties specified in the config
    // and if so try to parse, evaluate and set them.
    return setJEProperties(envConfig, cfg.getJEProperty(), attrMap);
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
          LocalizableMessage message = ERR_CONFIG_JE_DUPLICATE_PROPERTY.get(
              jePropertyName);
            throw new ConfigException(message);
        }
        // Set JE property.
        try {
          envConfig.setConfigParam(jePropertyName, jePropertyValue);
          // If this property shadows an existing config attribute.
          if (configAttrMap.containsKey(jePropertyName)) {
            LocalizableMessage message = ERR_CONFIG_JE_PROPERTY_SHADOWS_CONFIG.get(
              jePropertyName, attrMap.get(jePropertyName));
            throw new ConfigException(message);
          }
          // Add this property to unique set.
          uniqueJEProperties.add(jePropertyName);
        } catch(IllegalArgumentException e) {
          logger.traceException(e);
          LocalizableMessage message =
            ERR_CONFIG_JE_PROPERTY_INVALID.get(
            jeEntry, e.getMessage());
          throw new ConfigException(message, e.getCause());
        }
      } else {
        LocalizableMessage message =
          ERR_CONFIG_JE_PROPERTY_INVALID_FORM.get(jeEntry);
        throw new ConfigException(message);
      }
    }

    return envConfig;
  }



}
