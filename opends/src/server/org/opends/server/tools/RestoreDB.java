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
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.StartupErrorLogger;
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

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This program provides a utility that may be used to restore a binary backup
 * of a Directory Server backend generated using the BackUpDB tool.  This will
 * be a process that is intended to run separate from Directory Server and not
 * internally within the server process (e.g., via the tasks interface).
 */
public class RestoreDB
{
  /**
   * The main method for RestoreDB tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainRestoreDB(args);

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
           new StringArgument("configclass", 'C', "configClass", true, false,
                              true, "{configClass}",
                              ConfigFileHandler.class.getName(), null,
                              MSGID_RESTOREDB_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, "{configFile}", null, null,
                              MSGID_RESTOREDB_DESCRIPTION_CONFIG_FILE);
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


      verifyOnly = new BooleanArgument("verifyonly", 'V', "verifyOnly",
                                       MSGID_RESTOREDB_DESCRIPTION_VERIFY_ONLY);
      argParser.addArgument(verifyOnly);


      displayUsage =
           new BooleanArgument("help", 'H', "help",
                               MSGID_RESTOREDB_DESCRIPTION_USAGE);
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_INITIALIZE_ARGS;
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
      int    msgID   = MSGID_RESTOREDB_ERROR_PARSING_ARGS;
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
        directoryServer.bootstrapClient();
        directoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_RESTOREDB_SERVER_BOOTSTRAP_ERROR;
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
        int    msgID   = MSGID_RESTOREDB_CANNOT_LOAD_CONFIG;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_LOAD_CONFIG;
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
        int    msgID   = MSGID_RESTOREDB_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_LOAD_SCHEMA;
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
        int    msgID   = MSGID_RESTOREDB_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_INITIALIZE_CORE_CONFIG;
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
        int    msgID   = MSGID_RESTOREDB_CANNOT_INITIALIZE_CRYPTO_MANAGER;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_INITIALIZE_CRYPTO_MANAGER;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_INITIALIZE_CRYPTO_MANAGER;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // FIXME -- Install a custom logger to capture information about the state
      // of the export.
      StartupErrorLogger startupLogger = new StartupErrorLogger();
      startupLogger.initializeErrorLogger(null);
      addErrorLogger(startupLogger);
    }


    // Open the backup directory and make sure it is valid.
    BackupDirectory backupDir = null;
    try
    {
      backupDir = BackupDirectory.readBackupDirectoryDescriptor(
                       backupDirectory.getValue());
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_READ_BACKUP_DIRECTORY;
      String message = getMessage(msgID, backupDirectory.getValue(),
                                  stackTraceToSingleLineString(e));
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
    String backupID = null;
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
    ArrayList<ConfigEntry> entryList   = new ArrayList<ConfigEntry>();
    ArrayList<List<DN>>    dnList      = new ArrayList<List<DN>>();
    getBackends(backendList, entryList, dnList);


    Backend     backend     = null;
    ConfigEntry configEntry = null;
    int         numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend     b = backendList.get(i);
      ConfigEntry e = entryList.get(i);
      if (e.getDN().equals(configEntryDN))
      {
        backend     = b;
        configEntry = e;
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
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return 0;
    }


    // Perform the restore.
    try
    {
      backend.restoreBackup(configEntry, restoreConfig);
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
                                  stackTraceToSingleLineString(e));
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
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
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
      int    msgID   = MSGID_RESTOREDB_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE, de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_DECODE_BACKEND_BASE_DN;
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
      int    msgID   = MSGID_RESTOREDB_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE, ce.getMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
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
        int    msgID   = MSGID_RESTOREDB_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    ce.getMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_DETERMINE_BACKEND_ID;
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
        int    msgID   = MSGID_RESTOREDB_CANNOT_DETERMINE_BACKEND_CLASS;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    ce.getMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_DETERMINE_BACKEND_CLASS;
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
        int    msgID   = MSGID_RESTOREDB_CANNOT_LOAD_BACKEND_CLASS;
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
        int    msgID   = MSGID_RESTOREDB_CANNOT_INSTANTIATE_BACKEND_CLASS;
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
          msgID = MSGID_RESTOREDB_NO_BASES_FOR_BACKEND;
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
        int    msgID   = MSGID_RESTOREDB_CANNOT_DETERMINE_BASES_FOR_BACKEND;
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

