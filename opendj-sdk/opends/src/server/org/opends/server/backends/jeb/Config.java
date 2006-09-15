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

import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ConfigMessages.
     MSGID_CONFIG_DESCRIPTION_BACKEND_DIRECTORY;
import static org.opends.server.messages.ConfigMessages.
     MSGID_CONFIG_DESCRIPTION_BACKEND_MODE;
import static org.opends.server.messages.ConfigMessages.
    MSGID_CONFIG_BACKEND_MODE_INVALID;
import static org.opends.server.messages.ConfigMessages.
    MSGID_CONFIG_BACKEND_INSANE_MODE;
import static org.opends.server.config.ConfigConstants.ATTR_BACKEND_DIRECTORY;
import static org.opends.server.config.ConfigConstants.ATTR_BACKEND_MODE;
import static org.opends.server.messages.ConfigMessages.
     MSGID_CONFIG_BACKEND_NO_DIRECTORY;
import static org.opends.server.messages.JebMessages.*;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getFileForPath;

import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.RDN;
import org.opends.server.types.FilePermission;
import com.sleepycat.je.EnvironmentConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

import static org.opends.server.loggers.Debug.debugException;

/**
 * This class represents the configuration of a JE backend.
 */
public class Config
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.jeb.Config";

  /**
   * The name of the object class which configures
   * an attribute index.
   */
  public static final String OBJECT_CLASS_CONFIG_ATTR_INDEX =
       ConfigConstants.NAME_PREFIX_CFG + "je-index";

  /**
   * The name of the object class which configures
   * the database.
   */
  public static final String OBJECT_CLASS_CONFIG_DATABASE =
       ConfigConstants.NAME_PREFIX_CFG + "je-database";

  /**
   * The name of the attribute which configures
   * the attribute type of an attribute index.
   */
  public static final String ATTR_INDEX_ATTRIBUTE =
       ConfigConstants.NAME_PREFIX_CFG + "index-attribute";

  /**
   * The name of the attribute which configures
   * the type of indexing for an attribute index.
   */
  public static final String ATTR_INDEX_TYPE =
       ConfigConstants.NAME_PREFIX_CFG + "index-type";

  /**
   * The name of the attribute which configures
   * the entry limit for an attribute index.
   */
  public static final String ATTR_INDEX_ENTRY_LIMIT =
       ConfigConstants.NAME_PREFIX_CFG + "index-entry-limit";

  /**
   * The name of the attribute which configures
   * the substring length for an attribute index.
   */
  public static final String ATTR_INDEX_SUBSTRING_LENGTH =
       ConfigConstants.NAME_PREFIX_CFG + "index-substring-length";

  /**
   * The name of the attribute which configures
   * the backend index entry limit.
   */
  public static final String ATTR_BACKEND_INDEX_ENTRY_LIMIT =
       ConfigConstants.NAME_PREFIX_CFG + "backend-index-entry-limit";


  /**
   * The name of the attribute which configures
   * the subtree delete size limit.
   */
  public static final String ATTR_SUBTREE_DELETE_SIZE_LIMIT =
       ConfigConstants.NAME_PREFIX_CFG + "backend-subtree-delete-size-limit";

  /**
   * The name of the attribute which configures
   * the memory available for import buffering.
   */
  public static final String ATTR_IMPORT_BUFFER_SIZE =
       ConfigConstants.NAME_PREFIX_CFG + "backend-import-buffer-size";

  /**
   * The name of the attribute which configures
   * the pathname of a directory for import temporary files.
   */
  public static final String ATTR_IMPORT_TEMP_DIRECTORY =
       ConfigConstants.NAME_PREFIX_CFG + "backend-import-temp-directory";

  /**
   * The name of the attribute which configures
   * the import queue size.
   */
  public static final String ATTR_IMPORT_QUEUE_SIZE =
       ConfigConstants.NAME_PREFIX_CFG + "backend-import-queue-size";

  /**
   * The name of the attribute which configures
   * the import pass size.
   */
  public static final String ATTR_IMPORT_PASS_SIZE =
       ConfigConstants.NAME_PREFIX_CFG + "backend-import-pass-size";

  /**
   * The name of the attribute which configures
   * the number of import worker threads.
   */
  public static final String ATTR_IMPORT_THREAD_COUNT =
       ConfigConstants.NAME_PREFIX_CFG + "backend-import-thread-count";

  /**
   * The name of the attribute which configures
   * the maximum time to spend preloading the database cache.
   */
  public static final String ATTR_PRELOAD_TIME_LIMIT =
       ConfigConstants.NAME_PREFIX_CFG + "backend-preload-time-limit";

  /**
   * The name of the attribute which configures
   * whether entries should be compressed in the database.
   */
  public static final String ATTR_ENTRIES_COMPRESSED =
       ConfigConstants.NAME_PREFIX_CFG + "backend-entries-compressed";

  /**
   * The name of the attribute which configures the number of times a
   * database transaction will be retried after it is aborted due to deadlock
   * with another thread.
   */
  public static final String ATTR_DEADLOCK_RETRY_LIMIT =
       ConfigConstants.NAME_PREFIX_CFG + "backend-deadlock-retry-limit";



  /**
   * A set of units and their multipliers for configuration attributes
   * representing a number of bytes.
   */
  private static HashMap<String, Double> memoryUnits;

  /**
   * A set of units and their multipliers for configuration attributes
   * representing a period of time in milliseconds.
   */
  private static HashMap<String, Double> timeUnits;

  static
  {
    memoryUnits = new HashMap<String, Double>();
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

    timeUnits = new HashMap<String, Double>();
    timeUnits.put(TIME_UNIT_MILLISECONDS_ABBR, 1D);
    timeUnits.put(TIME_UNIT_MILLISECONDS_FULL, 1D);
    timeUnits.put(TIME_UNIT_SECONDS_ABBR, 1000D);
    timeUnits.put(TIME_UNIT_SECONDS_FULL, 1000D);
    timeUnits.put(TIME_UNIT_MINUTES_ABBR, (double) (60 * 1000));
    timeUnits.put(TIME_UNIT_MINUTES_FULL, (double) (60 * 1000));
  }

  /**
   * The set of base DNs.
   */
  private DN[] baseDNs = null;

  /**
   * The backend directory (file system pathname).
   */
  private File backendDirectory = null;

  /**
   * The backend directory permission mode. By default, owner has read, write
   * and execute permissions on the database directory.
   */
  private FilePermission backendPermission = new FilePermission(0700);

  /**
   * Number of times we should retry database transactions that get aborted
   * due to deadlock.
   */
  private int deadlockRetryLimit = 0;

  /**
   * The backend index entry limit, zero means unlimited.
   */
  private int backendIndexEntryLimit = 0;

  /**
   * The maximum number of entries to process in a single pass through the LDIF
   * file.
   */
  private int importPassSize = Integer.MAX_VALUE;

  /**
   * The subtree delete size limit, zero means unlimited.
   */
  private int subtreeDeleteSizeLimit = 0;

  /**
   * The memory available for import buffering.
   */
  private long importBufferSize = 100*1024*1024;

  /**
   * The pathname of the directory for import temporary files.
   */
  private String importTempDirectory = "importTmp";

  /**
   * The set of configured attribute indexes.
   */
  private Map<AttributeType, IndexConfig> indexConfigMap = null;



  /**
   * The DN of the JE database environment configuration entry, if any.
   */
  private DN envConfigDN = null;

  /**
   * The JE environment config.
   */
  private EnvironmentConfig envConfig = null;

  /**
   * The import queue size.
   */
  private int importQueueSize = 100;

  /**
   * The number of import threads.
   */
  private int importThreadCount = 8;

  /**
   * The database cache preload time limit in milliseconds.
   */
  private long preloadTimeLimit = 0;

  /**
   * Whether entries should be compressed in the database.
   */
  private boolean entriesCompressed = false;


  /**
   * Initialize this JE backend configuration from a configuration entry.
   *
   * @param configEntry The backend configuration entry.
   * @param  baseDNs      The set of base DNs that have been configured for this
   *                      backend.
   * @throws ConfigException If there is an error in the configuration entry.
   */
  public void initializeConfig(ConfigEntry configEntry, DN[] baseDNs)
       throws ConfigException
  {
    StringConfigAttribute stub;
    IntegerConfigAttribute intStub;
    IntegerWithUnitConfigAttribute intWithUnitStub;

    // Set the base DNs.
    this.baseDNs = baseDNs;

    // Determine the backend database directory.
    // Required, single-valued config attribute requiring admin action on change
    String msg = getMessage(MSGID_CONFIG_DESCRIPTION_BACKEND_DIRECTORY);
    stub =
         new StringConfigAttribute(ATTR_BACKEND_DIRECTORY,
                                   msg, true, false, true);
    StringConfigAttribute backendDirectoryAttr = (StringConfigAttribute)
         configEntry.getConfigAttribute(stub);
    if (backendDirectoryAttr == null)
    {
      int msgID = MSGID_CONFIG_BACKEND_NO_DIRECTORY;
      String message = getMessage(msgID, configEntry.getDN().toString());
      throw new ConfigException(msgID, message);
    }
    backendDirectory = getFileForPath(backendDirectoryAttr.activeValue());

    // ds-cfg-backend-mode
    // Optional, single-valued config attribute requiring admin action on change
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_BACKEND_MODE);
    stub =
        new StringConfigAttribute(ATTR_BACKEND_MODE, msg, false,false, true);
    StringConfigAttribute backendModeAttr = (StringConfigAttribute)
         configEntry.getConfigAttribute(stub);
    if (backendModeAttr != null)
    {
      FilePermission newBackendPermission;
      try
      {
        newBackendPermission = FilePermission.decodeUNIXMode(
            backendModeAttr.activeValue());
      }
      catch(Exception e)
      {
        int msgID = MSGID_CONFIG_BACKEND_MODE_INVALID;
        String message = getMessage(msgID, configEntry.getDN().toString());
        throw new ConfigException(msgID, message);
      }

      //Make sure the mode will allow the server itself access to
      //the database
      if(!newBackendPermission.isOwnerWritable() ||
           !newBackendPermission.isOwnerReadable() ||
           !newBackendPermission.isOwnerExecutable())
      {
        int msgID = MSGID_CONFIG_BACKEND_INSANE_MODE;
        String message = getMessage(msgID);
        throw new ConfigException(msgID, message);
      }
      else
      {
        backendPermission = newBackendPermission;
      }
    }

    // ds-cfg-backendIndexEntryLimit
    // Optional, single-valued config attribute requiring admin action on change
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_BACKEND_INDEX_ENTRY_LIMIT);
    intStub =
         new IntegerConfigAttribute(ATTR_BACKEND_INDEX_ENTRY_LIMIT, msg, false,
                                    false, true, true, 0, false, 0);
    IntegerConfigAttribute entryLimitAttr = (IntegerConfigAttribute)
         configEntry.getConfigAttribute(intStub);
    if (entryLimitAttr != null)
    {
      backendIndexEntryLimit = entryLimitAttr.activeIntValue();
    }

    // ds-cfg-backendSubtreeDeleteSizeLimit
    // Optional, single-valued config attribute
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_SUBTREE_DELETE_SIZE_LIMIT);
    intStub =
         new IntegerConfigAttribute(ATTR_SUBTREE_DELETE_SIZE_LIMIT, msg, false,
                                    false, false, true, 0, false, 0);
    IntegerConfigAttribute sdslAttr = (IntegerConfigAttribute)
         configEntry.getConfigAttribute(intStub);
    if (sdslAttr != null)
    {
      subtreeDeleteSizeLimit = sdslAttr.activeIntValue();
    }

    // Determine the directory for import temporary files.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_IMPORT_TEMP_DIRECTORY);
    stub =
         new StringConfigAttribute(ATTR_IMPORT_TEMP_DIRECTORY,
                                   msg, false, false, false);
    StringConfigAttribute importTempDirAttr = (StringConfigAttribute)
         configEntry.getConfigAttribute(stub);
    if (importTempDirAttr != null)
    {
      this.importTempDirectory = importTempDirAttr.activeValue();
    }

    // Determine the memory available for import buffering.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_IMPORT_BUFFER_SIZE);
    intWithUnitStub =
         new IntegerWithUnitConfigAttribute(ATTR_IMPORT_BUFFER_SIZE, msg,
                                            false, memoryUnits,
                                            true, 10*1024*1024, false,
                                            0);
    IntegerWithUnitConfigAttribute importBufferSizeAttr =
         (IntegerWithUnitConfigAttribute)
         configEntry.getConfigAttribute(intWithUnitStub);
    if (importBufferSizeAttr != null)
    {
      importBufferSize = importBufferSizeAttr.activeCalculatedValue();
    }

    // Determine the import queue size.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_IMPORT_QUEUE_SIZE);
    intStub =
         new IntegerConfigAttribute(ATTR_IMPORT_QUEUE_SIZE, msg, false,
                                    false, false, true, 1, false, 0);
    IntegerConfigAttribute a = (IntegerConfigAttribute)
         configEntry.getConfigAttribute(intStub);
    if (a != null)
    {
      importQueueSize = a.activeIntValue();
    }


    // Determine the number of import threads.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_IMPORT_THREAD_COUNT);
    intStub =
         new IntegerConfigAttribute(ATTR_IMPORT_THREAD_COUNT, msg, false,
                                    false, false, true, 1, false, 0);
    a = (IntegerConfigAttribute)
         configEntry.getConfigAttribute(intStub);
    if (a != null)
    {
      importThreadCount = a.activeIntValue();
    }


    // Determine the import pass size.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_IMPORT_PASS_SIZE);
    intStub = new IntegerConfigAttribute(ATTR_IMPORT_PASS_SIZE, msg, true,
                                         false, false, true, 0, true,
                                         Integer.MAX_VALUE);
    a = (IntegerConfigAttribute)
         configEntry.getConfigAttribute(intStub);
    if (a != null)
    {
      importPassSize = a.activeIntValue();
    }


    // Determine the database cache preload time limit.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_PRELOAD_TIME_LIMIT);
    intWithUnitStub =
         new IntegerWithUnitConfigAttribute(ATTR_PRELOAD_TIME_LIMIT, msg,
                                            false, timeUnits,
                                            true, 0, false, 0);
    IntegerWithUnitConfigAttribute preloadTimeLimitAttr =
         (IntegerWithUnitConfigAttribute)
         configEntry.getConfigAttribute(intWithUnitStub);
    if (preloadTimeLimitAttr != null)
    {
      preloadTimeLimit = preloadTimeLimitAttr.activeCalculatedValue();
    }


    // Determine whether entries should be compressed in the database.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_ENTRIES_COMPRESSED);
    BooleanConfigAttribute booleanStub =
         new BooleanConfigAttribute(ATTR_ENTRIES_COMPRESSED, msg, false);
    BooleanConfigAttribute entriesCompressedAttr =
         (BooleanConfigAttribute)
         configEntry.getConfigAttribute(booleanStub);
    if (entriesCompressedAttr != null)
    {
      entriesCompressed = entriesCompressedAttr.activeValue();
    }


    // Determine the deadlock retry limit.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_DEADLOCK_RETRY_LIMIT);
    intStub =
         new IntegerConfigAttribute(ATTR_DEADLOCK_RETRY_LIMIT, msg, false,
                                    false, false, true, 0, false, 0);
    a = (IntegerConfigAttribute)
         configEntry.getConfigAttribute(intStub);
    if (a != null)
    {
      deadlockRetryLimit = a.activeIntValue();
    }


    indexConfigMap = new HashMap<AttributeType, IndexConfig>();

    // Create an RDN for cn=Index.
    RDN indexRDN = null;
    try
    {
      indexRDN = RDN.decode("cn=Index");
    }
    catch (DirectoryException e)
    {
      assert debugException(CLASS_NAME, "initializeConfig", e);
    }

    ConcurrentHashMap<DN, ConfigEntry> children = configEntry.getChildren();
    for (ConfigEntry childConfigEntry : children.values())
    {
      if (childConfigEntry.hasObjectClass(OBJECT_CLASS_CONFIG_DATABASE))
      {
        // This is a database config entry.
        if (envConfig != null)
        {
          int    msgID   = MSGID_JEB_DUPLICATE_CONFIG_ENTRY;
          String message = getMessage(msgID,
                                      childConfigEntry.getDN().toString(),
                                      OBJECT_CLASS_CONFIG_DATABASE);
          logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
                   message, msgID);
        }
        else
        {
          envConfig =
               ConfigurableEnvironment.parseConfigEntry(childConfigEntry);
          envConfigDN = childConfigEntry.getDN();
        }
      }
      else if (childConfigEntry.getDN().getRDN().equals(indexRDN))
      {
        // This is the cn=Index branch entry.

        // Determine the index configuration.
        configureIndexEntries(indexConfigMap, backendIndexEntryLimit,
                              childConfigEntry.getChildren().values());
      }
      else
      {
        // Entry not recognized.
        int    msgID   = MSGID_JEB_CONFIG_ENTRY_NOT_RECOGNIZED;
        String message = getMessage(msgID, childConfigEntry.getDN().toString());
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
                 message, msgID);
      }
    }

    // Create a database configuration with defaults if we don't have one.
    if (envConfig == null)
    {
      envConfig = ConfigurableEnvironment.defaultConfig();
    }
  }

  /**
   * Takes a bunch of index configuration entries and constructs the
   * equivalent index configuration objects.
   *
   * @param indexConfigMap  The output index configuration objects are inserted
   *                        into this map.
   * @param backendIndexEntryLimit The index entry limit for all indexes where
   *                        a value is not specified.
   * @param entries         The input index configuration entries.
   * @throws ConfigException If an error occurs reading a configuration entry.
   */
  private void configureIndexEntries(
       Map<AttributeType, IndexConfig> indexConfigMap,
       int backendIndexEntryLimit,
       Collection<ConfigEntry> entries)
       throws ConfigException
  {
    String msg;
    StringConfigAttribute attributeStub;
    MultiChoiceConfigAttribute typeStub;
    IntegerConfigAttribute entryLimitStub;
    IntegerConfigAttribute substringLengthStub;

    // ds-cfg-indexAttribute
    // Required, single-valued config attributes requiring admin action on
    // change.
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_INDEX_ATTRIBUTE);
    attributeStub =
         new StringConfigAttribute(ATTR_INDEX_ATTRIBUTE, msg, true,
                                   false, true);

    // ds-cfg-indexType
    // Optional, multi-valued config attributes requiring admin action on change
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_INDEX_TYPE);
    HashSet<String> indexTypeSet = new HashSet<String>();
    indexTypeSet.add("presence");
    indexTypeSet.add("equality");
    indexTypeSet.add("substring");
    indexTypeSet.add("ordering");
    typeStub =
         new MultiChoiceConfigAttribute(ATTR_INDEX_TYPE, msg, false,
                                   true, true, indexTypeSet);

    // ds-cfg-indexEntryLimit
    // Optional, single-valued config attributes requiring admin action on
    // change.
    int indexEntryLimit = backendIndexEntryLimit;
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_INDEX_ENTRY_LIMIT);
    entryLimitStub =
         new IntegerConfigAttribute(ATTR_INDEX_ENTRY_LIMIT, msg, false,
                                    false, true, true, 0, false, 0);

    // ds-cfg-indexSubstringLength
    // Optional, single-valued config attributes requiring admin action on
    // change.
    int substringLength = 6;
    msg = getMessage(MSGID_CONFIG_DESCRIPTION_INDEX_SUBSTRING_LENGTH);
    substringLengthStub =
         new IntegerConfigAttribute(ATTR_INDEX_SUBSTRING_LENGTH, msg, false,
                                    false, true, true, 3, false, 0);

    // Iterate through the configuration entries and process those that
    // are index configuration entries.
    for (ConfigEntry configEntry : entries)
    {

      // Skip this entry if it is not an index configuration entry.
      if (!configEntry.hasObjectClass(OBJECT_CLASS_CONFIG_ATTR_INDEX))
      {
        int    msgID   = MSGID_JEB_CONFIG_ENTRY_NOT_RECOGNIZED;
        String message = getMessage(msgID, configEntry.getDN().toString());
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
                 message, msgID);
        continue;
      }

      // Get the attribute type of the index.
      StringConfigAttribute attributeConfigAttr = (StringConfigAttribute)
           configEntry.getConfigAttribute(attributeStub);

      AttributeType attrType = DirectoryServer.getAttributeType(
           attributeConfigAttr.activeValue().trim().toLowerCase());
      if (attrType == null)
      {
        int    msgID   = MSGID_JEB_INDEX_ATTRIBUTE_TYPE_NOT_FOUND;
        String message = getMessage(msgID, configEntry.getDN().toString(),
                                    attributeConfigAttr.activeValue());
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
                 message, msgID);
        continue;
      }

      // Get the index entry limit if set.
      IntegerConfigAttribute entryLimitAttr = (IntegerConfigAttribute)
           configEntry.getConfigAttribute(entryLimitStub);
      if (entryLimitAttr != null)
      {
        indexEntryLimit = entryLimitAttr.activeIntValue();
      }

      // Get the substring length if set.
      IntegerConfigAttribute substringLengthConfigAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(substringLengthStub);
      if (substringLengthConfigAttr != null)
      {
        substringLength = substringLengthConfigAttr.activeIntValue();
      }

      // Create an index configuration object.
      IndexConfig indexConfig = indexConfigMap.get(attrType);
      if (indexConfig == null)
      {
        indexConfig = new IndexConfig(attrType);
        indexConfig.setPresenceEntryLimit(indexEntryLimit);
        indexConfig.setEqualityEntryLimit(indexEntryLimit);
        indexConfig.setSubstringEntryLimit(indexEntryLimit);
        indexConfig.setSubstringLength(substringLength);
        indexConfigMap.put(attrType, indexConfig);
      }
      else
      {
        int    msgID   = MSGID_JEB_DUPLICATE_INDEX_CONFIG;
        String message = getMessage(msgID,
                                    configEntry.getDN().toString(),
                                    attrType.getNameOrOID());
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
                 message, msgID);
      }

      // Get the type of indexing.
      MultiChoiceConfigAttribute typeConfigAttr = (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(typeStub);
      if (typeConfigAttr != null)
      {
        for (String indexType : typeConfigAttr.activeValues())
        {
          if (indexType.equalsIgnoreCase("presence"))
          {
            indexConfig.setPresenceIndex(true);
          }
          else if (indexType.equalsIgnoreCase("equality"))
          {
            indexConfig.setEqualityIndex(true);
            if (attrType.getEqualityMatchingRule() == null)
            {
              int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
              String message = getMessage(messageID, attrType, indexType);
              throw new ConfigException(messageID, message);
            }
          }
          else if (indexType.equalsIgnoreCase("substring"))
          {
            indexConfig.setSubstringIndex(true);
            if (attrType.getSubstringMatchingRule() == null)
            {
              int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
              String message = getMessage(messageID, attrType, indexType);
              throw new ConfigException(messageID, message);
            }
          }
          else if (indexType.equalsIgnoreCase("ordering"))
          {
            indexConfig.setOrderingIndex(true);
            if (attrType.getOrderingMatchingRule() == null)
            {
              int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
              String message = getMessage(messageID, attrType, indexType);
              throw new ConfigException(messageID, message);
            }
          }
        }
      }
    }
  }

  /**
   * Get the backend directory.
   *
   * @return A file representing the backend directory
   */
  public File getBackendDirectory()
  {
    return backendDirectory;
  }

  /**
   * Get the backend directory file permission mode.
   *
   * @return An FilePermission representing the directory permissions
   */
  public FilePermission getBackendPermission()
  {
     return backendPermission;
  }

  /**
   * Get the set of base DNs.
   *
   * @return An array of base DNs.
   */
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }

  /**
   * Get the JE environment configuration.
   * @return A JE environment config object.
   */
  public EnvironmentConfig getEnvironmentConfig()
  {
    return envConfig;
  }

  /**
   * Get the transaction deadlock retry limit.
   *
   * @return The transaction deadlock retry limit.
   */
  public int getDeadlockRetryLimit()
  {
    return deadlockRetryLimit;
  }

  /**
   * Get the value of the backend index entry limit.
   *
   * @return The backend index entry limit value or 0 if no limit.
   */
  public int getBackendIndexEntryLimit()
  {
    return backendIndexEntryLimit;
  }

  /**
   * Get the value of the subtree delete size limit.
   *
   * @return The subtree delete size limit, or zero if there is no limit.
   */
  public int getSubtreeDeleteSizeLimit()
  {
    return subtreeDeleteSizeLimit;
  }

  /**
   * Get the attribute index configurations.
   *
   * @return A map of attribute types to index configurations
   */
  public Map<AttributeType, IndexConfig> getIndexConfigMap()
  {
    return indexConfigMap;
  }

  /**
   * Get the pathname of the directory for import temporary files.
   *
   * @return The pathname of the directory for import temporary files.
   */
  public String getImportTempDirectory()
  {
    return importTempDirectory;
  }

  /**
   * Get the value of the import buffer size.
   * @return The import buffer size.
   */
   public long getImportBufferSize()
  {
    return importBufferSize;
  }

  /**
   * Get the import queue size.
   * @return The import queue size.
   */
  public int getImportQueueSize()
  {
    return importQueueSize;
  }

  /**
   * Get the number of import threads.
   * @return The number of import threads.
   */
  public int getImportThreadCount()
  {
    return importThreadCount;
  }

  /**
   * Get the maximum number of entries to process in a single pass of an LDIF
   * import.
   *
   * @return  The maximum number of entries to process in a single pass of an
   *          LDIF import.
   */
  public int getImportPassSize()
  {
    return importPassSize;
  }



  /**
   * Determine whether entries are to be written to the entry database in
   * compressed form.
   * @return true if entries are to be written in compressed form.
   */
  public boolean isEntriesCompressed()
  {
    return entriesCompressed;
  }



  /**
   * Get the database cache preload time limit in milliseconds.
   * @return The database cache preload time limit in milliseconds.
   */
  public long getPreloadTimeLimit()
  {
    return preloadTimeLimit;
  }



  /**
   * Get the DN of the configuration entry for the JE environment, if any.
   *
   * @return The configuration entry DN, or null if there is none.
   */
  public DN getEnvConfigDN()
  {
    return envConfigDN;
  }



}
