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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;



import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

import org.opends.server.api.Backend;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.config.ConfigException;
import static org.opends.server.config.ConfigConstants.*;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.ThreadFilterTextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import static org.opends.server.loggers.ErrorLogger.*;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.LDAPConnectionArgumentParser;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.tasks.BackupTask;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;


/**
 * This program provides a utility that may be used to back up a Directory
 * Server backend in a binary form that may be quickly archived and restored.
 * The format of the backup may vary based on the backend type and does not need
 * to be something that can be handled by any other backend type.  This will be
 * a process that is intended to run separate from Directory Server and not
 * internally within the server process (e.g., via the tasks interface).
 */
public class BackUpDB extends TaskTool
{
  private static ErrorLogPublisher errorLogPublisher = null;
  /**
   * The main method for BackUpDB tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainBackUpDB(args, true, System.out, System.err);

    if(errorLogPublisher != null)
    {
      removeErrorLogPublisher(errorLogPublisher);
    }

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
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
    return mainBackUpDB(args, true, System.out, System.err);
  }

  /**
   * Processes the command-line arguments and invokes the backup process.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           {@code null} if standard output is not needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           {@code null} if standard error is not needed.
   *
   * @return The error code.
   */
  public static int mainBackUpDB(String[] args, boolean initializeServer,
                                 OutputStream outStream, OutputStream errStream)
  {
    BackUpDB tool = new BackUpDB();
    return tool.process(args, initializeServer, outStream, errStream);
  }

  // Define the command-line arguments that may be used with this program.
  private BooleanArgument backUpAll         = null;
  private BooleanArgument compress          = null;
  private BooleanArgument displayUsage      = null;
  private BooleanArgument encrypt           = null;
  private BooleanArgument hash              = null;
  private BooleanArgument incremental       = null;
  private BooleanArgument signHash          = null;
  private StringArgument  backendID         = null;
  private StringArgument  backupIDString    = null;
  private StringArgument  configClass       = null;
  private StringArgument  configFile        = null;
  private StringArgument  backupDirectory   = null;
  private StringArgument  incrementalBaseID = null;

  private int process(String[] args, boolean initializeServer,
                      OutputStream outStream, OutputStream errStream)
  {

    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser =
            createArgParser("org.opends.server.tools.BackUpDB",
                            INFO_BACKUPDB_TOOL_DESCRIPTION.get());


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument(
                   "configclass", OPTION_SHORT_CONFIG_CLASS,
                   OPTION_LONG_CONFIG_CLASS, true, false,
                   true, OPTION_VALUE_CONFIG_CLASS,
                   ConfigFileHandler.class.getName(), null,
                   INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument(
                   "configfile", 'f', "configFile", true, false,
                   true, "{configFile}", null, null,
                   INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      backendID =
           new StringArgument(
                   "backendid", 'n', "backendID", false, true, true,
                   "{backendID}", null, null,
                   INFO_BACKUPDB_DESCRIPTION_BACKEND_ID.get());
      argParser.addArgument(backendID);


      backUpAll = new BooleanArgument(
                  "backupall", 'a', "backUpAll",
                  INFO_BACKUPDB_DESCRIPTION_BACKUP_ALL.get());
      argParser.addArgument(backUpAll);


      backupIDString =
           new StringArgument(
                   "backupid", 'I', "backupID", false, false, true,
                   "{backupID}", null, null,
                   INFO_BACKUPDB_DESCRIPTION_BACKUP_ID.get());
      argParser.addArgument(backupIDString);


      backupDirectory =
           new StringArgument(
                   "backupdirectory", 'd', "backupDirectory", true,
                   false, true, "{backupDir}", null, null,
                   INFO_BACKUPDB_DESCRIPTION_BACKUP_DIR.get());
      argParser.addArgument(backupDirectory);


      incremental = new BooleanArgument(
                  "incremental", 'i', "incremental",
                  INFO_BACKUPDB_DESCRIPTION_INCREMENTAL.get());
      argParser.addArgument(incremental);


      incrementalBaseID =
           new StringArgument(
                   "incrementalbaseid", 'B', "incrementalBaseID",
                   false, false, true, "{backupID}", null, null,
                   INFO_BACKUPDB_DESCRIPTION_INCREMENTAL_BASE_ID.get());
      argParser.addArgument(incrementalBaseID);


      compress = new BooleanArgument(
                  "compress", OPTION_SHORT_COMPRESS,
                  OPTION_LONG_COMPRESS,
                  INFO_BACKUPDB_DESCRIPTION_COMPRESS.get());
      argParser.addArgument(compress);


      encrypt = new BooleanArgument(
                  "encrypt", 'y', "encrypt",
                  INFO_BACKUPDB_DESCRIPTION_ENCRYPT.get());
      argParser.addArgument(encrypt);


      hash = new BooleanArgument(
                  "hash", 'A', "hash",
                  INFO_BACKUPDB_DESCRIPTION_HASH.get());
      argParser.addArgument(hash);


      signHash =
           new BooleanArgument(
                   "signhash", 's', "signHash",
                   INFO_BACKUPDB_DESCRIPTION_SIGN_HASH.get());
      argParser.addArgument(signHash);


      displayUsage =
           new BooleanArgument(
                   "help", OPTION_SHORT_HELP,
                   OPTION_LONG_HELP,
                   INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
      validateTaskArgs();
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }


    // Make sure that either the backUpAll argument was provided or at least one
    // backend ID was given.  They are mutually exclusive.
    if (backUpAll.isPresent())
    {
      if (backendID.isPresent())
      {
        Message message = ERR_BACKUPDB_CANNOT_MIX_BACKUP_ALL_AND_BACKEND_ID.get(
                backUpAll.getLongIdentifier(),
                backendID.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return 1;
      }
    }
    else if (! backendID.isPresent())
    {
      Message message = ERR_BACKUPDB_NEED_BACKUP_ALL_OR_BACKEND_ID.get(
              backUpAll.getLongIdentifier(),
              backendID.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }
    else
    {
      // Check that the backendID has not been expressed twice.
      HashSet<String> backendIDLowerCase = new HashSet<String>();
      HashSet<String> repeatedBackendIds = new HashSet<String>();
      StringBuilder repeatedBackends = new StringBuilder();
      for (String id : backendID.getValues())
      {
        String lId = id.toLowerCase();
        if (backendIDLowerCase.contains(lId))
        {
          if (!repeatedBackendIds.contains(lId))
          {
            repeatedBackendIds.add(lId);
            if (repeatedBackends.length() > 0)
            {
              repeatedBackends.append(", ");
            }
            repeatedBackends.append(id);
          }
        }
        else
        {
          backendIDLowerCase.add(lId);
        }
      }
      if (repeatedBackends.length() > 0)
      {
        Message message = ERR_BACKUPDB_REPEATED_BACKEND_ID.get(
            repeatedBackends.toString());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return 1;
      }
    }

    // If the incremental base ID was specified, then make sure it is an
    // incremental backup.
    if (incrementalBaseID.isPresent())
    {
      if (! incremental.isPresent())
      {
        Message message =
                ERR_BACKUPDB_INCREMENTAL_BASE_REQUIRES_INCREMENTAL.get(
                        incrementalBaseID.getLongIdentifier(),
                        incremental.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return 1;
      }
    }

    // Encryption or signing requires the ADS backend be available for
    // CryptoManager access to secret key entries. If no connection arguments
    //  are present, infer an offline backup.
    if ((encrypt.isPresent() || signHash.isPresent())
            && ! argParser.connectionArgumentsPresent()) {
      Message message =
              ERR_BACKUPDB_ENCRYPT_OR_SIGN_REQUIRES_ONLINE.get(
                      encrypt.getLongIdentifier(),
                      signHash.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }

    // If the signHash option was provided, then make sure that the hash option
    // was given.
    if (signHash.isPresent() && (! hash.isPresent()))
    {
      Message message = ERR_BACKUPDB_SIGN_REQUIRES_HASH.get(
              signHash.getLongIdentifier(),
              hash.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }

    return process(argParser, initializeServer, out, err);
  }

  /**
   * {@inheritDoc}
   */
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    ArrayList<ASN1OctetString> values;
    if (backUpAll.getValue() != null &&
            !backUpAll.getValue().equals(
                    backUpAll.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(backUpAll.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_ALL, values));
    }

    if (compress.getValue() != null &&
            !compress.getValue().equals(
                    compress.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(compress.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_COMPRESS, values));
    }

    if (encrypt.getValue() != null &&
            !encrypt.getValue().equals(
                    encrypt.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(encrypt.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_ENCRYPT, values));
    }

    if (hash.getValue() != null &&
            !hash.getValue().equals(
                    hash.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(hash.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_HASH, values));
    }

    if (incremental.getValue() != null &&
            !incremental.getValue().equals(
                    incremental.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(incremental.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_INCREMENTAL, values));
    }

    if (signHash.getValue() != null &&
            !signHash.getValue().equals(
                    signHash.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(signHash.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_SIGN_HASH, values));
    }

    List<String> backendIDs = backendID.getValues();
    if (backendIDs != null && backendIDs.size() > 0) {
      values = new ArrayList<ASN1OctetString>(backendIDs.size());
      for (String s : backendIDs) {
        values.add(new ASN1OctetString(s));
      }
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_BACKEND_ID, values));
    }

    if (backupIDString.getValue() != null &&
            !backupIDString.getValue().equals(
                    backupIDString.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(backupIDString.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_BACKUP_ID, values));
    }

    if (backupDirectory.getValue() != null &&
            !backupDirectory.getValue().equals(
                    backupDirectory.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(backupDirectory.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_BACKUP_DIRECTORY_PATH, values));
    }

    if (incrementalBaseID.getValue() != null &&
            !incrementalBaseID.getValue().equals(
                    incrementalBaseID.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(incrementalBaseID.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_INCREMENTAL_BASE_ID, values));
    }

  }

  /**
   * {@inheritDoc}
   */
  public String getTaskObjectclass() {
    return "ds-task-backup";
  }

  /**
   * {@inheritDoc}
   */
  public Class getTaskClass() {
    return BackupTask.class;
  }

  /**
   * {@inheritDoc}
   */
  protected int processLocal(boolean initializeServer,
                           PrintStream out,
                           PrintStream err) {

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
        Message message = ERR_BACKUPDB_CANNOT_CREATE_BACKUP_DIR.get(
                backupDirectory.getValue(),
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }

    // If no backup ID was provided, then create one with the current timestamp.
    String backupID;
    if (backupIDString.isPresent())
    {
      backupID = backupIDString.getValue();
    }
    else
    {
      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      backupID = dateFormat.format(new Date());
    }

    // If the incremental base ID was specified, then make sure it is an
    // incremental backup.
    String incrementalBase;
    if (incrementalBaseID.isPresent())
    {
      incrementalBase = incrementalBaseID.getValue();
    }
    else
    {
      incrementalBase = null;
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
        Message message = ERR_SERVER_BOOTSTRAP_ERROR.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_LOAD_CONFIG.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_LOAD_CONFIG.get(getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
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
        Message message =
                ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message =
                ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // Initialize the Directory Server crypto manager.
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // FIXME -- Install a custom logger to capture information about the state
      // of the export.
      try
      {
        errorLogPublisher =
            new ThreadFilterTextErrorLogPublisher(Thread.currentThread(),
                                                  new TextWriter.STREAM(out));
        addErrorLogPublisher(errorLogPublisher);

      }
      catch(Exception e)
      {
        err.println("Error installing the custom error logger: " +
                    stackTraceToSingleLineString(e));
      }
    }


    // Get information about the backends defined in the server, and determine
    // whether we are backing up multiple backends or a single backend.
    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<BackendCfg>  entryList   = new ArrayList<BackendCfg>();
    ArrayList<List<DN>>    dnList      = new ArrayList<List<DN>>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);
    int numBackends = backendList.size();

    boolean multiple;
    ArrayList<Backend> backendsToArchive = new ArrayList<Backend>(numBackends);
    HashMap<String,BackendCfg> configEntries =
         new HashMap<String,BackendCfg>(numBackends);
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
            Message message =
                WARN_BACKUPDB_BACKUP_NOT_SUPPORTED.get(b.getBackendID());
            logError(message);
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
          Message message = ERR_BACKUPDB_NO_BACKENDS_FOR_ID.get(id);
          logError(message);
        }

        return 1;
      }


      // See if there are multiple backends to archive.
      multiple = (backendsToArchive.size() > 1);
    }


    // If there are no backends to archive, then print an error and exit.
    if (backendsToArchive.isEmpty())
    {
      Message message = WARN_BACKUPDB_NO_BACKENDS_TO_ARCHIVE.get();
      logError(message);
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
          Message message = ERR_BACKUPDB_CANNOT_LOCK_BACKEND.get(
              b.getBackendID(), String.valueOf(failureReason));
          logError(message);
          errorsEncountered = true;
          continue;
        }
      }
      catch (Exception e)
      {
        Message message = ERR_BACKUPDB_CANNOT_LOCK_BACKEND.get(
            b.getBackendID(), getExceptionMessage(e));
        logError(message);
        errorsEncountered = true;
        continue;
      }


      Message message = NOTE_BACKUPDB_STARTING_BACKUP.get(b.getBackendID());
      logError(message);


      // Get the config entry for this backend.
      BackendCfg configEntry = configEntries.get(b.getBackendID());


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
      BackupDirectory backupDir;
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
            message = ERR_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR.get(
                descriptorPath, ce.getMessage());
            logError(message);
            errorsEncountered = true;

            try
            {
              String lockFile = LockFileManager.getBackendLockFileName(b);
              StringBuilder failureReason = new StringBuilder();
              if (! LockFileManager.releaseLock(lockFile, failureReason))
              {
                message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
                    b.getBackendID(), String.valueOf(failureReason));
                logError(message);
              }
            }
            catch (Exception e)
            {
              message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
                  b.getBackendID(), getExceptionMessage(e));
              logError(message);
            }

            continue;
          }
          catch (Exception e)
          {
            message = ERR_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR.get(
                descriptorPath, getExceptionMessage(e));
            logError(message);
            errorsEncountered = true;

            try
            {
              String lockFile = LockFileManager.getBackendLockFileName(b);
              StringBuilder failureReason = new StringBuilder();
              if (! LockFileManager.releaseLock(lockFile, failureReason))
              {
                message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
                    b.getBackendID(), String.valueOf(failureReason));
                logError(message);
              }
            }
            catch (Exception e2)
            {
              message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
                  b.getBackendID(), getExceptionMessage(e2));
              logError(message);
            }

            continue;
          }
        }
        else
        {
          backupDir = new BackupDirectory(backupDirPath, configEntry.dn());
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
          message = ERR_BACKUPDB_CANNOT_CREATE_BACKUP_DIR.get(
              backupDirPath, getExceptionMessage(e));
          logError(message);
          errorsEncountered = true;

          try
          {
            String lockFile = LockFileManager.getBackendLockFileName(b);
            StringBuilder failureReason = new StringBuilder();
            if (! LockFileManager.releaseLock(lockFile, failureReason))
            {
              message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
                  b.getBackendID(), String.valueOf(failureReason));
              logError(message);
            }
          }
          catch (Exception e2)
          {
            message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
                b.getBackendID(), getExceptionMessage(e2));
            logError(message);
          }

          continue;
        }

        backupDir = new BackupDirectory(backupDirPath, configEntry.dn());
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
        message = ERR_BACKUPDB_CANNOT_BACKUP.get(
            b.getBackendID(), unsupportedReason.toString());
        logError(message);
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
                b.getBackendID(), String.valueOf(failureReason));
            logError(message);
          }
        }
        catch (Exception e2)
        {
          message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
              b.getBackendID(), getExceptionMessage(e2));
          logError(message);
        }

        continue;
      }


      // Perform the backup.
      try
      {
        b.createBackup(backupConfig);
      }
      catch (DirectoryException de)
      {
        message = ERR_BACKUPDB_ERROR_DURING_BACKUP.get(
            b.getBackendID(), de.getMessageObject());
        logError(message);
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
                b.getBackendID(), String.valueOf(failureReason));
            logError(message);
          }
        }
        catch (Exception e)
        {
          message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
              b.getBackendID(), getExceptionMessage(e));
          logError(message);
        }

        continue;
      }
      catch (Exception e)
      {
        message = ERR_BACKUPDB_ERROR_DURING_BACKUP.get(
            b.getBackendID(), getExceptionMessage(e));
        logError(message);
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
                b.getBackendID(), String.valueOf(failureReason));
            logError(message);
          }
        }
        catch (Exception e2)
        {
          message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
              b.getBackendID(), getExceptionMessage(e2));
          logError(message);
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
          message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
              b.getBackendID(), String.valueOf(failureReason));
          logError(message);
          errorsEncountered = true;
        }
      }
      catch (Exception e)
      {
        message = WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND.get(
            b.getBackendID(), getExceptionMessage(e));
        logError(message);
        errorsEncountered = true;
      }
    }


    // Print a final completed message, indicating whether there were any errors
    // in the process.
    int ret = 0;
    if (errorsEncountered)
    {
      Message message = NOTE_BACKUPDB_COMPLETED_WITH_ERRORS.get();
      logError(message);
      ret = 1;
    }
    else
    {
      Message message = NOTE_BACKUPDB_COMPLETED_SUCCESSFULLY.get();
      logError(message);
    }
    return ret;
  }
}

