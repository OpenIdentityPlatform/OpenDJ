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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.config.EnvironmentParams;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.JebMessages.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class represents a JE environment handle that can be configured by the
 * Directory Server as a configurable component.
 */
public class ConfigurableEnvironment implements ConfigurableComponent
{
  /**
   * The DN of the configuration entry with which the environment is
   * associated.
   */
  private DN configDN;

  /**
   * The JE environment handle.
   */
  private Environment environment;

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
   * A map of JE property names to their associated configuration attribute.
   */
  private static HashMap<String, ConfigAttribute> configAttrMap =
       new HashMap<String, ConfigAttribute>();

  /**
   * A list of registered environment configuration attributes.
   */
  private static ArrayList<ConfigAttribute> configAttrList =
       new ArrayList<ConfigAttribute>();


  private static final ConfigAttribute CONFIG_ATTR_CACHE_PERCENT;
  private static final ConfigAttribute CONFIG_ATTR_CACHE_SIZE;
  private static final ConfigAttribute CONFIG_ATTR_TXN_NO_SYNC;
  private static final ConfigAttribute CONFIG_ATTR_TXN_WRITE_NO_SYNC;
  private static final ConfigAttribute CONFIG_ATTR_RUN_CLEANER;
  private static final ConfigAttribute CONFIG_ATTR_CLEANER_MIN_UTILIZATION;
  private static final ConfigAttribute CONFIG_ATTR_EVICTOR_LRU_ONLY;
  private static final ConfigAttribute CONFIG_ATTR_EVICTOR_NODES_PER_SCAN;
  private static final ConfigAttribute CONFIG_ATTR_LOG_FILE_MAX;
  private static final ConfigAttribute CONFIG_ATTR_LOGGING_FILE_HANDLER_ON;
  private static final ConfigAttribute CONFIG_ATTR_LOGGING_LEVEL;
  private static final ConfigAttribute CONFIG_ATTR_CHECKPOINTER_BYTES_INTERVAL;
  private static final ConfigAttribute CONFIG_ATTR_CHECKPOINTER_WAKEUP_INTERVAL;
  private static final ConfigAttribute CONFIG_ATTR_NUM_LOCK_TABLES;
  private static final ConfigAttribute CONFIG_ATTR_NUM_CLEANER_THREADS;



  /**
   * Register an environment property and its associated configuration
   * attribute.
   *
   * @param propertyName The name of the JE property to be registered.
   * @param configAttr   The configuration attribute associated with the
   *                     property.
   */
  private static void registerPropertyAttribute(String propertyName,
                                                ConfigAttribute configAttr)
  {
    configAttrMap.put(propertyName, configAttr);
    configAttrList.add(configAttr);
  }



  static
  {
    HashMap<String, Double> memoryUnits = new HashMap<String, Double>();
    memoryUnits.put(SIZE_UNIT_BYTES_ABBR, 1D);
    memoryUnits.put(SIZE_UNIT_BYTES_FULL, 1D);
    memoryUnits.put(SIZE_UNIT_KILOBYTES_ABBR, 1000D);
    memoryUnits.put(SIZE_UNIT_KILOBYTES_FULL, 1000D);
    memoryUnits.put(SIZE_UNIT_MEGABYTES_ABBR, 1000000D);
    memoryUnits.put(SIZE_UNIT_MEGABYTES_FULL, 1000000D);
    memoryUnits.put(SIZE_UNIT_GIGABYTES_ABBR, 1000000000D);
    memoryUnits.put(SIZE_UNIT_GIGABYTES_FULL, 1000000000D);
    memoryUnits.put(SIZE_UNIT_KIBIBYTES_ABBR, 1024D);
    memoryUnits.put(SIZE_UNIT_KIBIBYTES_FULL, 1024D);
    memoryUnits.put(SIZE_UNIT_MEBIBYTES_ABBR, (double) (1024 * 1024));
    memoryUnits.put(SIZE_UNIT_MEBIBYTES_FULL, (double) (1024 * 1024));
    memoryUnits.put(SIZE_UNIT_GIBIBYTES_ABBR, (double) (1024 * 1024 * 1024));
    memoryUnits.put(SIZE_UNIT_GIBIBYTES_FULL, (double) (1024 * 1024 * 1024));

    // JE time intervals are expressed in microseconds.
    HashMap<String, Double> timeUnits = new HashMap<String, Double>();
    timeUnits.put(TIME_UNIT_MICROSECONDS_ABBR, 1D);
    timeUnits.put(TIME_UNIT_MICROSECONDS_FULL, 1D);
    timeUnits.put(TIME_UNIT_MILLISECONDS_ABBR, 1000D);
    timeUnits.put(TIME_UNIT_MILLISECONDS_FULL, 1000D);
    timeUnits.put(TIME_UNIT_SECONDS_ABBR, 1000000D);
    timeUnits.put(TIME_UNIT_SECONDS_FULL, 1000000D);
    timeUnits.put(TIME_UNIT_MINUTES_ABBR, (double) (60 * 1000000));
    timeUnits.put(TIME_UNIT_MINUTES_FULL, (double) (60 * 1000000));

    String msg;

    // Create configuration attributes for the JE properties that
    // can be configured through the Directory Server interfaces.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_DATABASE_TXN_NO_SYNC);
    CONFIG_ATTR_TXN_NO_SYNC =
         new BooleanConfigAttribute(ATTR_DATABASE_TXN_NO_SYNC,
                                    msg, false);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_DATABASE_TXN_WRITE_NO_SYNC);
    CONFIG_ATTR_TXN_WRITE_NO_SYNC =
         new BooleanConfigAttribute(ATTR_DATABASE_TXN_WRITE_NO_SYNC,
                                    msg, false);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_DATABASE_CACHE_PERCENT);
    CONFIG_ATTR_CACHE_PERCENT =
         new IntegerConfigAttribute(ATTR_DATABASE_CACHE_PERCENT, msg, true,
                                    false, false, true, 1, true, 90);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_DATABASE_CACHE_SIZE);
    CONFIG_ATTR_CACHE_SIZE =
         new IntegerWithUnitConfigAttribute(ATTR_DATABASE_CACHE_SIZE, msg,
                                            false, memoryUnits,
                                            true, 0, false, 0);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_DATABASE_RUN_CLEANER);
    CONFIG_ATTR_RUN_CLEANER =
         new BooleanConfigAttribute(ATTR_DATABASE_RUN_CLEANER,
                                    msg, false);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_CLEANER_MIN_UTILIZATION);
    CONFIG_ATTR_CLEANER_MIN_UTILIZATION =
         new IntegerConfigAttribute(ATTR_CLEANER_MIN_UTILIZATION, msg, true,
                                    false, true, true, 0, true, 100);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_EVICTOR_LRU_ONLY);
    CONFIG_ATTR_EVICTOR_LRU_ONLY =
         new BooleanConfigAttribute(ATTR_EVICTOR_LRU_ONLY, msg, true);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_EVICTOR_NODES_PER_SCAN);
    CONFIG_ATTR_EVICTOR_NODES_PER_SCAN =
         new IntegerConfigAttribute(ATTR_EVICTOR_NODES_PER_SCAN, msg, false,
                                    false, true, true, 1, true, 1000);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_DATABASE_LOG_FILE_MAX);
    CONFIG_ATTR_LOG_FILE_MAX =
         new IntegerWithUnitConfigAttribute(ATTR_DATABASE_LOG_FILE_MAX, msg,
                                            false, memoryUnits,
                                            true, 1000000, true, 4294967296L);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_LOGGING_FILE_HANDLER_ON);
    CONFIG_ATTR_LOGGING_FILE_HANDLER_ON =
         new BooleanConfigAttribute(ATTR_LOGGING_FILE_HANDLER_ON, msg, true);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_LOGGING_LEVEL);
    CONFIG_ATTR_LOGGING_LEVEL =
         new StringConfigAttribute(ATTR_LOGGING_LEVEL, msg, false, false, true);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_CHECKPOINT_BYTES_INTERVAL);
    CONFIG_ATTR_CHECKPOINTER_BYTES_INTERVAL =
         new IntegerWithUnitConfigAttribute(ATTR_CHECKPOINTER_BYTES_INTERVAL,
                                            msg, true, memoryUnits,
                                            true, 0, false, 0);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_CHECKPOINT_WAKEUP_INTERVAL);
    CONFIG_ATTR_CHECKPOINTER_WAKEUP_INTERVAL =
         new IntegerWithUnitConfigAttribute(ATTR_CHECKPOINTER_WAKEUP_INTERVAL,
                                            msg, true, timeUnits,
                                            true, 1000000, false, 0);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_NUM_LOCK_TABLES);
    CONFIG_ATTR_NUM_LOCK_TABLES =
         new IntegerConfigAttribute(ATTR_NUM_LOCK_TABLES, msg, false,
                                    false, true, true, 1, true, 32767);

    msg = getMessage(MSGID_CONFIG_DESCRIPTION_NUM_CLEANER_THREADS);
    CONFIG_ATTR_NUM_CLEANER_THREADS =
         new IntegerConfigAttribute(ATTR_NUM_CLEANER_THREADS, msg, false,
                                    false, false, true, 1, false, 0);

    // Register the parameters that have JE property names.
    registerPropertyAttribute("je.maxMemoryPercent",
                              CONFIG_ATTR_CACHE_PERCENT);
    registerPropertyAttribute("je.maxMemory",
                              CONFIG_ATTR_CACHE_SIZE);
    registerPropertyAttribute("je.cleaner.minUtilization",
                              CONFIG_ATTR_CLEANER_MIN_UTILIZATION);
    registerPropertyAttribute("je.env.runCleaner",
                              CONFIG_ATTR_RUN_CLEANER);
    registerPropertyAttribute("je.evictor.lruOnly",
                              CONFIG_ATTR_EVICTOR_LRU_ONLY);
    registerPropertyAttribute("je.evictor.nodesPerScan",
                              CONFIG_ATTR_EVICTOR_NODES_PER_SCAN);
    registerPropertyAttribute("je.log.fileMax",
                              CONFIG_ATTR_LOG_FILE_MAX);
    registerPropertyAttribute("java.util.logging.FileHandler.on",
                              CONFIG_ATTR_LOGGING_FILE_HANDLER_ON);
    registerPropertyAttribute("java.util.logging.level",
                              CONFIG_ATTR_LOGGING_LEVEL);
    registerPropertyAttribute("je.checkpointer.bytesInterval",
                              CONFIG_ATTR_CHECKPOINTER_BYTES_INTERVAL);
    registerPropertyAttribute("je.checkpointer.wakeupInterval",
                              CONFIG_ATTR_CHECKPOINTER_WAKEUP_INTERVAL);
    registerPropertyAttribute("je.lock.nLockTables",
                              CONFIG_ATTR_NUM_LOCK_TABLES);
    registerPropertyAttribute("je.cleaner.threads",
                              CONFIG_ATTR_NUM_CLEANER_THREADS);

    // These parameters do not have JE property names.
    configAttrList.add(CONFIG_ATTR_TXN_NO_SYNC);
    configAttrList.add(CONFIG_ATTR_TXN_WRITE_NO_SYNC);
  }



  /**
   * Constructs a configurable environment.
   *
   * @param configDN    The DN of the configuration entry with which the
   *                    environment is associated.
   * @param environment The JE environment handle.
   */
  public ConfigurableEnvironment(DN configDN, Environment environment)
  {
    this.configDN = configDN;
    this.environment = environment;
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
   * Parse a configuration entry associated with a JE environment and create an
   * environment config from it.
   *
   * @param configEntry The configuration entry to be parsed.
   * @return An environment config instance corresponding to the config entry.
   * @throws ConfigException If there is an error in the provided configuration
   * entry.
   */
  public static EnvironmentConfig parseConfigEntry(ConfigEntry configEntry)
       throws ConfigException
  {
    EnvironmentConfig envConfig = defaultConfig();

    // Handle the attributes that do not have a JE property.
    BooleanConfigAttribute booleanAttr;
    booleanAttr = (BooleanConfigAttribute)
         configEntry.getConfigAttribute(CONFIG_ATTR_TXN_NO_SYNC);
    if (booleanAttr != null)
    {
      envConfig.setTxnNoSync(booleanAttr.activeValue());
    }

    booleanAttr = (BooleanConfigAttribute)
         configEntry.getConfigAttribute(CONFIG_ATTR_TXN_WRITE_NO_SYNC);
    if (booleanAttr != null)
    {
      envConfig.setTxnWriteNoSync(booleanAttr.activeValue());
    }

    // Iterate through the config attributes associated with a JE property.
    for (Map.Entry<String, ConfigAttribute> mapEntry : configAttrMap.entrySet())
    {
      String property = mapEntry.getKey();
      ConfigAttribute stub = mapEntry.getValue();

      // Check if the config entry contains this attribute.
      ConfigAttribute configAttr = configEntry.getConfigAttribute(stub);
      if (stub != null)
      {
        // Set the property.
        if (configAttr instanceof BooleanConfigAttribute)
        {
          BooleanConfigAttribute attr = (BooleanConfigAttribute) configAttr;
          boolean value = attr.activeValue();
          envConfig.setConfigParam(property, String.valueOf(value));
        }
        else if (configAttr instanceof IntegerConfigAttribute)
        {
          IntegerConfigAttribute attr = (IntegerConfigAttribute) configAttr;
          long value = attr.activeValue();
          envConfig.setConfigParam(property, String.valueOf(value));
        }
        else if (configAttr instanceof IntegerWithUnitConfigAttribute)
        {
          IntegerWithUnitConfigAttribute attr =
               (IntegerWithUnitConfigAttribute) configAttr;
          long value = attr.activeCalculatedValue();
          envConfig.setConfigParam(property, String.valueOf(value));
        }
        else if (configAttr instanceof StringConfigAttribute)
        {
          StringConfigAttribute attr = (StringConfigAttribute) configAttr;
          String value = attr.activeValue();
          envConfig.setConfigParam(property, value);
        }
      }
    }

    return envConfig;
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return The DN of the configuration entry with which this component is
   *         associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return The set of configuration attributes that are associated with this
   *         configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    return configAttrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param configEntry         The configuration entry for which to make the
   *                            determination.
   * @param unacceptableReasons A list that can be used to hold messages about
   *                            why the provided entry does not have an
   *                            acceptable configuration.
   * @return <CODE>true</CODE> if the provided entry has an acceptable
   *         configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param configEntry     The entry containing the new configuration to apply
   *                        for this component.
   * @param detailedResults Indicates whether detailed information about the
   *                        processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    ConfigChangeResult ccr;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();

    try
    {
      // Check if any JE non-mutable properties were changed.
      EnvironmentConfig oldEnvConfig = environment.getConfig();
      EnvironmentConfig newEnvConfig = parseConfigEntry(configEntry);
      Map paramsMap = EnvironmentParams.SUPPORTED_PARAMS;
      for (Object o : paramsMap.values())
      {
        ConfigParam param = (ConfigParam) o;
        if (!param.isMutable())
        {
          String oldValue = oldEnvConfig.getConfigParam(param.getName());
          String newValue = newEnvConfig.getConfigParam(param.getName());
          if (!oldValue.equalsIgnoreCase(newValue))
          {
            adminActionRequired = true;
            if (detailedResults)
            {
              ConfigAttribute configAttr = configAttrMap.get(param.getName());
              if (configAttr != null)
              {
                int msgID = MSGID_JEB_CONFIG_ATTR_REQUIRES_RESTART;
                messages.add(getMessage(msgID, configAttr.getName()));
              }
            }
          }
        }
      }

      // This takes care of changes to the JE environment for those
      // properties that are mutable at runtime.
      environment.setMutableConfig(newEnvConfig);
    }
    catch (Exception e)
    {
      messages.add(e.getMessage());
      ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                   adminActionRequired,
                                   messages);
      return ccr;
    }

    ccr = new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
                                 messages);
    return ccr;
  }
}
