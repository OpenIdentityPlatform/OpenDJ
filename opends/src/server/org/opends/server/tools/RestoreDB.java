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



import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.Backend;
import org.opends.server.config.ConfigException;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.ThreadFilterTextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.util.StaticUtils;
import static org.opends.server.tools.ToolConstants.*;
import org.opends.server.admin.std.server.BackendCfg;


/**
 * This program provides a utility that may be used to restore a binary backup
 * of a Directory Server backend generated using the BackUpDB tool.  This will
 * be a process that is intended to run separate from Directory Server and not
 * internally within the server process (e.g., via the tasks interface).
 */
public class RestoreDB
{
  private static DN publisherDN = null;
  /**
   * The main method for RestoreDB tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainRestoreDB(args);

    if(publisherDN != null)
    {
      ErrorLogger.removeErrorLogPublisher(publisherDN);
    }

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }

  /**
   * Processes the command-line arguments and invokes the restore process.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */
  public static int mainRestoreDB(String[] args)
  {
    return mainRestoreDB(args, true);
  }

  /**
   * Processes the command-line arguments and invokes the restore process.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   *
   * @return The error code.
   */
  public static int mainRestoreDB(String[] args, boolean initializeServer)
  {
    // Define the command-line arguments that may be used with this program.
    BooleanArgument displayUsage      = null;
    BooleanArgument listBackups          = null;
    BooleanArgument verifyOnly        = null;
    StringArgument  backupIDString    = null;
    StringArgument  configClass       = null;
    StringArgument  configFile        = null;
    StringArgument  backupDirectory   = null;


    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_RESTOREDB_TOOL_DESCRIPTION);
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.RestoreDB",
                            toolDescription, false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                              OPTION_LONG_CONFIG_CLASS, true, false,
                              true, OPTION_VALUE_CONFIG_CLASS,
                              ConfigFileHandler.class.getName(), null,
                              MSGID_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, "{configFile}", null, null,
                              MSGID_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      backupIDString =
           new StringArgument("backupid", 'I', "backupID", false, false, true,
                              "{backupID}", null, null,
                              MSGID_RESTOREDB_DESCRIPTION_BACKUP_ID);
      argParser.addArgument(backupIDString);


      backupDirectory =
           new StringArgument("backupdirectory", 'd', "backupDirectory", true,
                              false, true, "{backupDir}", null, null,
                              MSGID_RESTOREDB_DESCRIPTION_BACKUP_DIR);
      argParser.addArgument(backupDirectory);


      listBackups = new BooleanArgument("listbackups", 'l', "listBackups",
                                     MSGID_RESTOREDB_DESCRIPTION_LIST_BACKUPS);
      argParser.addArgument(listBackups);


      verifyOnly = new BooleanArgument("verifyonly", OPTION_SHORT_DRYRUN,
                                       OPTION_LONG_DRYRUN,
                                       MSGID_RESTOREDB_DESCRIPTION_VERIFY_ONLY);
      argParser.addArgument(verifyOnly);


      displayUsage =
           new BooleanArgument("help", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                               MSGID_DESCRIPTION_USAGE);
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_ARGS;
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
      int    msgID   = MSGID_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return 1;
    }


    // If we should just display usage information, then print it and exit.
    if (argParser.usageDisplayed())
    {
      return 0;
    }


    // Perform the initial bootstrap of the Directory Server and process the
    // configuration.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    if (initializeServer)
    {
      try
      {
        DirectoryServer.bootstrapClient();
        DirectoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_SERVER_BOOTSTRAP_ERROR;
        String message = getMessage(msgID, getExceptionMessage(e));
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
        int    msgID   = MSGID_CANNOT_LOAD_CONFIG;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_LOAD_CONFIG;
        String message = getMessage(msgID, getExceptionMessage(e));
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
        int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, getExceptionMessage(e));
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
        int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, getExceptionMessage(e));
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
        int    msgID   = MSGID_CANNOT_INITIALIZE_CRYPTO_MANAGER;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_CANNOT_INITIALIZE_CRYPTO_MANAGER;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_INITIALIZE_CRYPTO_MANAGER;
        String message = getMessage(msgID, getExceptionMessage(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // FIXME -- Install a custom logger to capture information about the state
      // of the export.
      try
      {
        publisherDN = DN.decode("cn=Custom Logger for RestoreDB");
        ThreadFilterTextErrorLogPublisher publisher =
            new ThreadFilterTextErrorLogPublisher(Thread.currentThread(),
                                                  new TextWriter.STDOUT());
        ErrorLogger.addErrorLogPublisher(publisherDN, publisher);

      }
      catch(Exception e)
      {
        System.err.println("Error installing the custom error logger: " +
            StaticUtils.stackTraceToSingleLineString(e));
      }
    }


    // Open the backup directory and make sure it is valid.
    BackupDirectory backupDir;
    try
    {
      backupDir = BackupDirectory.readBackupDirectoryDescriptor(
                       backupDirectory.getValue());
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_READ_BACKUP_DIRECTORY;
      String message = getMessage(msgID, backupDirectory.getValue(),
                                  getExceptionMessage(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return 1;
    }


    // If we're just going to be listing backups, then do that now.
    DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_LOCAL_TIME);
    if (listBackups.isPresent())
    {
      for (BackupInfo backupInfo : backupDir.getBackups().values())
      {
        int    msgID   = MSGID_RESTOREDB_LIST_BACKUP_ID;
        String message = getMessage(msgID, backupInfo.getBackupID());
        System.out.println(message);

        msgID   = MSGID_RESTOREDB_LIST_BACKUP_DATE;
        message = getMessage(msgID,
                             dateFormat.format(backupInfo.getBackupDate()));
        System.out.println(message);

        msgID   = MSGID_RESTOREDB_LIST_INCREMENTAL;
        message = getMessage(msgID, String.valueOf(backupInfo.isIncremental()));
        System.out.println(message);

        msgID   = MSGID_RESTOREDB_LIST_COMPRESSED;
        message = getMessage(msgID, String.valueOf(backupInfo.isCompressed()));
        System.out.println(message);

        msgID   = MSGID_RESTOREDB_LIST_ENCRYPTED;
        message = getMessage(msgID, String.valueOf(backupInfo.isEncrypted()));
        System.out.println(message);

        byte[] hash = backupInfo.getUnsignedHash();
        msgID   = MSGID_RESTOREDB_LIST_HASHED;
        message = getMessage(msgID, String.valueOf(hash != null));
        System.out.println(message);

        byte[] signature = backupInfo.getSignedHash();
        msgID   = MSGID_RESTOREDB_LIST_SIGNED;
        message = getMessage(msgID, String.valueOf(signature != null));
        System.out.println(message);

        StringBuilder dependencyList = new StringBuilder();
        HashSet<String> dependencyIDs = backupInfo.getDependencies();
        if (! dependencyIDs.isEmpty())
        {
          Iterator<String> iterator = dependencyIDs.iterator();
          dependencyList.append(iterator.next());

          while (iterator.hasNext())
          {
            dependencyList.append(", ");
            dependencyList.append(iterator.next());
          }
        }
        else
        {
          dependencyList.append("none");
        }

        msgID   = MSGID_RESTOREDB_LIST_DEPENDENCIES;
        message = getMessage(msgID, dependencyList.toString());
        System.out.println(message);

        System.out.println();
      }

      return 1;
    }


    // If a backup ID was specified, then make sure it is valid.  If none was
    // provided, then choose the latest backup from the archive.
    String backupID;
    if (backupIDString.isPresent())
    {
      backupID = backupIDString.getValue();
      BackupInfo backupInfo = backupDir.getBackupInfo(backupID);
      if (backupInfo == null)
      {
        int    msgID   = MSGID_RESTOREDB_INVALID_BACKUP_ID;
        String message = getMessage(msgID, backupID,
                                    backupDirectory.getValue());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return 1;
      }
    }
    else
    {
      BackupInfo latestBackup = backupDir.getLatestBackup();
      if (latestBackup == null)
      {
        int    msgID   = MSGID_RESTOREDB_NO_BACKUPS_IN_DIRECTORY;
        String message = getMessage(msgID, backupDirectory.getValue());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return 1;
      }
      else
      {
        backupID = latestBackup.getBackupID();
      }
    }


    // Get the DN of the backend configuration entry from the backup and load
    // the associated backend from the configuration.
    DN configEntryDN = backupDir.getConfigEntryDN();


    // Get information about the backends defined in the server and determine
    // which to use for the restore.
    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<BackendCfg> entryList   = new ArrayList<BackendCfg>();
    ArrayList<List<DN>>    dnList      = new ArrayList<List<DN>>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);


    Backend     backend     = null;
    int         numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend     b = backendList.get(i);
      BackendCfg e = entryList.get(i);
      if (e.dn().equals(configEntryDN))
      {
        backend     = b;
        break;
      }
    }

    if (backend == null)
    {
      int    msgID   = MSGID_RESTOREDB_NO_BACKENDS_FOR_DN;
      String message = getMessage(msgID, backupDirectory.getValue(),
                                  configEntryDN.toString());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return 1;
    }
    else if (! backend.supportsRestore())
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_RESTORE;
      String message = getMessage(msgID, backend.getBackendID());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return 1;
    }


    // Create the restore config object from the information available.
    RestoreConfig restoreConfig = new RestoreConfig(backupDir, backupID,
                                                    verifyOnly.isPresent());


    // Acquire an exclusive lock for the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_LOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return 0;
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_LOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
                                  getExceptionMessage(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return 0;
    }


    // Perform the restore.
    try
    {
      backend.restoreBackup(restoreConfig);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_RESTOREDB_ERROR_DURING_BACKUP;
      String message = getMessage(msgID, backupID, backupDir.getPath(),
                                  de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_RESTOREDB_ERROR_DURING_BACKUP;
      String message = getMessage(msgID, backupID, backupDir.getPath(),
                                  getExceptionMessage(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    }


    // Release the exclusive lock on the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_UNLOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                 message, msgID);
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_UNLOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
                                  getExceptionMessage(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }
    return 0;
  }
}

