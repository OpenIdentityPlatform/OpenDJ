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
package org.opends.server.tools;



import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.StartupErrorLogger;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This program provides a utility that may be used to back up a Directory
 * Server backend in a binary form that may be quickly archived and restored.
 * The format of the backup may vary based on the backend type and does not need
 * to be something that can be handled by any other backend type.  This will be
 * a process that is intended to run separate from Directory Server and not
 * internally within the server process (e.g., via the tasks interface).
 */
public class BackUpDB
{
  /**
   * The main method for BackUpDB tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainBackUpDB(args);

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }

  /**
   * Processes the command-line arguments and invokes the backup process.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */
  public static int mainBackUpDB(String[] args)
  {
    // Define the command-line arguments that may be used with this program.
    BooleanArgument backUpAll         = null;
    BooleanArgument compress          = null;
    BooleanArgument displayUsage      = null;
    BooleanArgument encrypt           = null;
    BooleanArgument hash              = null;
    BooleanArgument incremental       = null;
    BooleanArgument signHash          = null;
    StringArgument  backendID         = null;
    StringArgument  backupIDString    = null;
    StringArgument  configClass       = null;
    StringArgument  configFile        = null;
    StringArgument  backupDirectory   = null;
    StringArgument  incrementalBaseID = null;


    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_BACKUPDB_TOOL_DESCRIPTION);
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.BackUpDB", toolDescription,
                            false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument("configclass", 'C', "configClass", true, false,
                              true, "{configClass}",
                              ConfigFileHandler.class.getName(), null,
                              MSGID_BACKUPDB_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, "{configFile}", null, null,
                              MSGID_BACKUPDB_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      backendID =
           new StringArgument("backendid", 'n', "backendID", false, true, true,
                              "{backendID}", null, null,
                              MSGID_BACKUPDB_DESCRIPTION_BACKEND_ID);
      argParser.addArgument(backendID);


      backUpAll = new BooleanArgument("backupall", 'a', "backUpAll",
                                      MSGID_BACKUPDB_DESCRIPTION_BACKUP_ALL);
      argParser.addArgument(backUpAll);


      backupIDString =
           new StringArgument("backupid", 'I', "backupID", false, false, true,
                              "{backupID}", null, null,
                              MSGID_BACKUPDB_DESCRIPTION_BACKUP_ID);
      argParser.addArgument(backupIDString);


      backupDirectory =
           new StringArgument("backupdirectory", 'd', "backupDirectory", true,
                              false, true, "{backupDir}", null, null,
                              MSGID_BACKUPDB_DESCRIPTION_BACKUP_DIR);
      argParser.addArgument(backupDirectory);


      incremental = new BooleanArgument("incremental", 'i', "incremental",
                                        MSGID_BACKUPDB_DESCRIPTION_INCREMENTAL);
      argParser.addArgument(incremental);


      incrementalBaseID =
           new StringArgument("incrementalbaseid", 'B', "incrementalBaseID",
                              false, false, true, "{backupID}", null, null,
                              MSGID_BACKUPDB_DESCRIPTION_INCREMENTAL_BASE_ID);
      argParser.addArgument(incrementalBaseID);


      compress = new BooleanArgument("compress", 'c', "compress",
                                     MSGID_BACKUPDB_DESCRIPTION_COMPRESS);
      argParser.addArgument(compress);


      encrypt = new BooleanArgument("encrypt", 'y', "encrypt",
                                    MSGID_BACKUPDB_DESCRIPTION_ENCRYPT);
      argParser.addArgument(encrypt);


      hash = new BooleanArgument("hash", 'h', "hash",
                                 MSGID_BACKUPDB_DESCRIPTION_HASH);
      argParser.addArgument(hash);


      signHash =
           new BooleanArgument("signhash", 's', "signHash",
                               MSGID_BACKUPDB_DESCRIPTION_SIGN_HASH);
      argParser.addArgument(signHash);


      displayUsage =
           new BooleanArgument("help", 'H', "help",
                               MSGID_BACKUPDB_DESCRIPTION_USAGE);
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_BACKUPDB_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return 1;
    }


    // If we should just display usage information, then print it and exit.
    if (displayUsage.isPresent())
    {
      return 0;
    }


    // Make sure that either the backUpAll argument was provided or at least one
    // backend ID was given.  They are mutually exclusive.
    if (backUpAll.isPresent())
    {
      if (backendID.isPresent())
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_MIX_BACKUP_ALL_AND_BACKEND_ID;
        String message = getMessage(msgID, backUpAll.getLongIdentifier(),
                                    backendID.getLongIdentifier());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }
    else if (! backendID.isPresent())
    {
      int    msgID   = MSGID_BACKUPDB_NEED_BACKUP_ALL_OR_BACKEND_ID;
      String message = getMessage(msgID, backUpAll.getLongIdentifier(),
                                  backendID.getLongIdentifier());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // If no backup ID was provided, then create one with the current timestamp.
    String backupID;
    if (backupIDString.isPresent())
    {
      backupID = backupIDString.getValue();
    }
    else
    {
      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_UTC_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      backupID = dateFormat.format(new Date());
    }


    // If the incremental base ID was specified, then make sure it is an
    // incremental backup.
    String incrementalBase;
    if (incrementalBaseID.isPresent())
    {
      if (! incremental.isPresent())
      {
        int    msgID   = MSGID_BACKUPDB_INCREMENTAL_BASE_REQUIRES_INCREMENTAL;
        String message = getMessage(msgID,
                                    incrementalBaseID.getLongIdentifier(),
                                    incremental.getLongIdentifier());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      incrementalBase = incrementalBaseID.getValue();
    }
    else
    {
      incrementalBase = null;
    }


    // If the signHash option was provided, then make sure that the hash option
    // was given.
    if (signHash.isPresent() && (! hash.isPresent()))
    {
      int    msgID   = MSGID_BACKUPDB_SIGN_REQUIRES_HASH;
      String message = getMessage(msgID, signHash.getLongIdentifier(),
                                  hash.getLongIdentifier());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Make sure that the backup directory exists.  If not, then create it.
    File backupDirFile = new File(backupDirectory.getValue());
    if (! backupDirFile.exists())
    {
      try
      {
        backupDirFile.mkdirs();
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_CREATE_BACKUP_DIR;
        String message = getMessage(msgID, backupDirectory.getValue(),
                                    stackTraceToSingleLineString(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }


    // Perform the initial bootstrap of the Directory Server and process the
    // configuration.
    DirectoryServer directoryServer = DirectoryServer.getInstance();

    try
    {
      directoryServer.bootstrapClient();
      directoryServer.initializeJMX();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_BACKUPDB_SERVER_BOOTSTRAP_ERROR;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      directoryServer.initializeConfiguration(configClass.getValue(),
                                              configFile.getValue());
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_LOAD_CONFIG;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_LOAD_CONFIG;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }



    // Initialize the Directory Server schema elements.
    try
    {
      directoryServer.initializeSchema();
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Initialize the Directory Server core configuration.
    try
    {
      CoreConfigManager coreConfigManager = new CoreConfigManager();
      coreConfigManager.initializeCoreConfig();
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Initialize the Directory Server crypto manager.
    try
    {
      directoryServer.initializeCryptoManager();
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // FIXME -- Install a custom logger to capture information about the state
    // of the export.
    StartupErrorLogger startupLogger = new StartupErrorLogger();
    startupLogger.initializeErrorLogger(null);
    addErrorLogger(startupLogger);


    // Get information about the backends defined in the server, and determine
    // whether we are backing up multiple backends or a single backend.
    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<ConfigEntry> entryList   = new ArrayList<ConfigEntry>();
    ArrayList<List<DN>>    dnList      = new ArrayList<List<DN>>();
    getBackends(backendList, entryList, dnList);
    int numBackends = backendList.size();

    boolean multiple;
    ArrayList<Backend> backendsToArchive = new ArrayList<Backend>(numBackends);
    HashMap<String,ConfigEntry> configEntries =
         new HashMap<String,ConfigEntry>(numBackends);
    if (backUpAll.isPresent())
    {
      for (int i=0; i < numBackends; i++)
      {
        Backend b = backendList.get(i);
        if (b.supportsBackup())
        {
          backendsToArchive.add(b);
          configEntries.put(b.getBackendID(), entryList.get(i));
        }
      }

      // We'll proceed as if we're backing up multiple backends in this case
      // even if there's just one.
      multiple = true;
    }
    else
    {
      // Iterate through the set of backends and pick out those that were
      // requested.
      HashSet<String> requestedBackends =
           new HashSet<String>(backendList.size());
      requestedBackends.addAll(backendID.getValues());

      for (int i=0; i < numBackends; i++)
      {
        Backend b = backendList.get(i);
        if (requestedBackends.contains(b.getBackendID()))
        {
          if (! b.supportsBackup())
          {
            int    msgID   = MSGID_BACKUPDB_BACKUP_NOT_SUPPORTED;
            String message = getMessage(msgID, b.getBackendID());
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                     msgID);
          }
          else
          {
            backendsToArchive.add(b);
            configEntries.put(b.getBackendID(), entryList.get(i));
            requestedBackends.remove(b.getBackendID());
          }
        }
      }

      if (! requestedBackends.isEmpty())
      {
        for (String id : requestedBackends)
        {
          int    msgID   = MSGID_BACKUPDB_NO_BACKENDS_FOR_ID;
          String message = getMessage(msgID, id);
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }

        return 1;
      }


      // See if there are multiple backends to archive.
      multiple = (backendsToArchive.size() > 1);
    }


    // If there are no backends to archive, then print an error and exit.
    if (backendsToArchive.isEmpty())
    {
      int    msgID   = MSGID_BACKUPDB_NO_BACKENDS_TO_ARCHIVE;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return 1;
    }


    // Iterate through the backends to archive and back them up individually.
    boolean errorsEncountered = false;
    for (Backend b : backendsToArchive)
    {
      // Acquire a shared lock for this backend.
      try
      {
        String        lockFile      = LockFileManager.getBackendLockFileName(b);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
        {
          int    msgID   = MSGID_BACKUPDB_CANNOT_LOCK_BACKEND;
          String message = getMessage(msgID, b.getBackendID(),
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          errorsEncountered = true;
          continue;
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_LOCK_BACKEND;
        String message = getMessage(msgID, b.getBackendID(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        errorsEncountered = true;
        continue;
      }


      int    msgID = MSGID_BACKUPDB_STARTING_BACKUP;
      String message = getMessage(msgID, b.getBackendID());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
               msgID);


      // Get the config entry for this backend.
      ConfigEntry configEntry = configEntries.get(b.getBackendID());


      // Get the path to the directory to use for this backup.  If we will be
      // backing up multiple backends (or if we are backing up all backends,
      // even if there's only one of them), then create a subdirectory for each
      // backend.
      String backupDirPath;
      if (multiple)
      {
        backupDirPath = backupDirectory.getValue() + File.separator +
                        b.getBackendID();
      }
      else
      {
        backupDirPath = backupDirectory.getValue();
      }


      // If the directory doesn't exist, then create it.  If it does exist, then
      // see if it has a backup descriptor file.
      BackupDirectory backupDir = null;
      backupDirFile = new File(backupDirPath);
      if (backupDirFile.exists())
      {
        String descriptorPath = backupDirPath + File.separator +
                                BACKUP_DIRECTORY_DESCRIPTOR_FILE;
        File descriptorFile = new File(descriptorPath);
        if (descriptorFile.exists())
        {
          try
          {
            backupDir =
                 BackupDirectory.readBackupDirectoryDescriptor(backupDirPath);
          }
          catch (ConfigException ce)
          {
            msgID   = MSGID_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR;
            message = getMessage(msgID, descriptorPath, ce.getMessage());
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
            errorsEncountered = true;

            try
            {
              String lockFile = LockFileManager.getBackendLockFileName(b);
              StringBuilder failureReason = new StringBuilder();
              if (! LockFileManager.releaseLock(lockFile, failureReason))
              {
                msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
                message = getMessage(msgID, b.getBackendID(),
                                     String.valueOf(failureReason));
                logError(ErrorLogCategory.BACKEND,
                         ErrorLogSeverity.SEVERE_WARNING, message, msgID);
              }
            }
            catch (Exception e)
            {
              msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
              message = getMessage(msgID, b.getBackendID(),
                                   stackTraceToSingleLineString(e));
              logError(ErrorLogCategory.BACKEND,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            }

            continue;
          }
          catch (Exception e)
          {
            msgID   = MSGID_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR;
            message = getMessage(msgID, descriptorPath,
                                 stackTraceToSingleLineString(e));
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
            errorsEncountered = true;

            try
            {
              String lockFile = LockFileManager.getBackendLockFileName(b);
              StringBuilder failureReason = new StringBuilder();
              if (! LockFileManager.releaseLock(lockFile, failureReason))
              {
                msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
                message = getMessage(msgID, b.getBackendID(),
                                     String.valueOf(failureReason));
                logError(ErrorLogCategory.BACKEND,
                         ErrorLogSeverity.SEVERE_WARNING, message, msgID);
              }
            }
            catch (Exception e2)
            {
              msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
              message = getMessage(msgID, b.getBackendID(),
                                   stackTraceToSingleLineString(e2));
              logError(ErrorLogCategory.BACKEND,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            }

            continue;
          }
        }
        else
        {
          backupDir = new BackupDirectory(backupDirPath, configEntry.getDN());
        }
      }
      else
      {
        try
        {
          backupDirFile.mkdirs();
        }
        catch (Exception e)
        {
          msgID   = MSGID_BACKUPDB_CANNOT_CREATE_BACKUP_DIR;
          message = getMessage(msgID, backupDirPath,
                               stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          errorsEncountered = true;

          try
          {
            String lockFile = LockFileManager.getBackendLockFileName(b);
            StringBuilder failureReason = new StringBuilder();
            if (! LockFileManager.releaseLock(lockFile, failureReason))
            {
              msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
              message = getMessage(msgID, b.getBackendID(),
                                   String.valueOf(failureReason));
              logError(ErrorLogCategory.BACKEND,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            }
          }
          catch (Exception e2)
          {
            msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
            message = getMessage(msgID, b.getBackendID(),
                                 stackTraceToSingleLineString(e2));
            logError(ErrorLogCategory.BACKEND,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }

          continue;
        }

        backupDir = new BackupDirectory(backupDirPath, configEntry.getDN());
      }


      // Create a backup configuration and determine whether the requested
      // backup can be performed using the selected backend.
      BackupConfig backupConfig = new BackupConfig(backupDir, backupID,
                                                   incremental.isPresent());
      backupConfig.setCompressData(compress.isPresent());
      backupConfig.setEncryptData(encrypt.isPresent());
      backupConfig.setHashData(hash.isPresent());
      backupConfig.setSignHash(signHash.isPresent());
      backupConfig.setIncrementalBaseID(incrementalBase);

      StringBuilder unsupportedReason = new StringBuilder();
      if (! b.supportsBackup(backupConfig, unsupportedReason))
      {
        msgID   = MSGID_BACKUPDB_CANNOT_BACKUP;
        message = getMessage(msgID, b.getBackendID(),
                             unsupportedReason.toString());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
            message = getMessage(msgID, b.getBackendID(),
                                 String.valueOf(failureReason));
            logError(ErrorLogCategory.BACKEND,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }
        }
        catch (Exception e2)
        {
          msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
          message = getMessage(msgID, b.getBackendID(),
                               stackTraceToSingleLineString(e2));
          logError(ErrorLogCategory.BACKEND,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        }

        continue;
      }


      // Perform the backup.
      try
      {
        b.createBackup(configEntry, backupConfig);
      }
      catch (DirectoryException de)
      {
        msgID   = MSGID_BACKUPDB_ERROR_DURING_BACKUP;
        message = getMessage(msgID, b.getBackendID(),
                                    de.getErrorMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
            message = getMessage(msgID, b.getBackendID(),
                                 String.valueOf(failureReason));
            logError(ErrorLogCategory.BACKEND,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }
        }
        catch (Exception e)
        {
          msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
          message = getMessage(msgID, b.getBackendID(),
                               stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        }

        continue;
      }
      catch (Exception e)
      {
        msgID   = MSGID_BACKUPDB_ERROR_DURING_BACKUP;
        message = getMessage(msgID, b.getBackendID(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
            message = getMessage(msgID, b.getBackendID(),
                                 String.valueOf(failureReason));
            logError(ErrorLogCategory.BACKEND,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }
        }
        catch (Exception e2)
        {
          msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
          message = getMessage(msgID, b.getBackendID(),
                               stackTraceToSingleLineString(e2));
          logError(ErrorLogCategory.BACKEND,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        }

        continue;
      }


      // Release the shared lock for the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(b);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
          message = getMessage(msgID, b.getBackendID(),
                               String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          errorsEncountered = true;
        }
      }
      catch (Exception e)
      {
        msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
        message = getMessage(msgID, b.getBackendID(),
                             stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        errorsEncountered = true;
      }
    }


    // Print a final completed message, indicating whether there were any errors
    // in the process.
    if (errorsEncountered)
    {
      int    msgID = MSGID_BACKUPDB_COMPLETED_WITH_ERRORS;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
               msgID);
    }
    else
    {
      int    msgID = MSGID_BACKUPDB_COMPLETED_SUCCESSFULLY;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
               msgID);
    }
    return 0;
  }



  /**
   * Retrieves information about the backends defined in the Directory Server
   * configuration.
   *
   * @param  backendList  A list into which instantiated (but not initialized)
   *                      backend instances will be placed.
   * @param  entryList    A list into which the config entries associated with
   *                      the backends will be placed.
   * @param  dnList       A list into which the set of base DNs for each backend
   *                      will be placed.
   */
  private static void getBackends(ArrayList<Backend> backendList,
                                  ArrayList<ConfigEntry> entryList,
                                  ArrayList<List<DN>> dnList)
  {
    // Get the base entry for all backend configuration.
    DN backendBaseDN = null;
    try
    {
      backendBaseDN = DN.decode(DN_BACKEND_BASE);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE, de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE,
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);

      System.exit(1);
    }

    ConfigEntry baseEntry = null;
    try
    {
      baseEntry = DirectoryServer.getConfigEntry(backendBaseDN);
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE, ce.getMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_BACKUPDB_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE,
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }


    // Iterate through the immediate children, attempting to parse them as
    // backends.
    for (ConfigEntry configEntry : baseEntry.getChildren().values())
    {
      // Get the backend ID attribute from the entry.  If there isn't one, then
      // skip the entry.
      String backendID = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
        StringConfigAttribute idStub =
             new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID),
                                       true, false, true);
        StringConfigAttribute idAttr =
             (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
        if (idAttr == null)
        {
          continue;
        }
        else
        {
          backendID = idAttr.activeValue();
        }
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    ce.getMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }


      // Get the backend class name attribute from the entry.  If there isn't
      // one, then just skip the entry.
      String backendClassName = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS;
        StringConfigAttribute classStub =
             new StringConfigAttribute(ATTR_BACKEND_CLASS, getMessage(msgID),
                                       true, false, false);
        StringConfigAttribute classAttr =
             (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          continue;
        }
        else
        {
          backendClassName = classAttr.activeValue();
        }
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_DETERMINE_BACKEND_CLASS;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    ce.getMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_DETERMINE_BACKEND_CLASS;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }

      Class backendClass = null;
      try
      {
        backendClass = Class.forName(backendClassName);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_LOAD_BACKEND_CLASS;
        String message = getMessage(msgID, backendClassName,
                                    String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }

      Backend backend = null;
      try
      {
        backend = (Backend) backendClass.newInstance();
        backend.setBackendID(backendID);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_INSTANTIATE_BACKEND_CLASS;
        String message = getMessage(msgID, backendClassName,
                                    String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }


      // Get the base DN attribute from the entry.  If there isn't one, then
      // just skip this entry.
      List<DN> baseDNs = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
        DNConfigAttribute baseDNStub =
             new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID),
                                   true, true, true);
        DNConfigAttribute baseDNAttr =
             (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
        if (baseDNAttr == null)
        {
          msgID = MSGID_BACKUPDB_NO_BASES_FOR_BACKEND;
          String message = getMessage(msgID,
                                      String.valueOf(configEntry.getDN()));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
        else
        {
          baseDNs = baseDNAttr.activeValues();
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_DETERMINE_BASES_FOR_BACKEND;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }


      backendList.add(backend);
      entryList.add(configEntry);
      dnList.add(baseDNs);
    }
  }
}

