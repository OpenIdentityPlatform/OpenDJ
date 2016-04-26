/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.forgerock.util.Utils;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tasks.BackupTask;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.cli.LDAPConnectionArgumentParser;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.StringArgument;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The main method for BackUpDB tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainBackUpDB(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
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

  /** Define the command-line arguments that may be used with this program. */
  private BooleanArgument backUpAll;
  private BooleanArgument compress;
  private BooleanArgument encrypt;
  private BooleanArgument hash;
  private BooleanArgument incremental;
  private BooleanArgument signHash;
  private StringArgument  backendID;
  private StringArgument  backupIDString;
  private StringArgument  configFile;
  private StringArgument  backupDirectory;
  private StringArgument  incrementalBaseID;

  private int process(String[] args, boolean initializeServer,
                      OutputStream outStream, OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser =
            createArgParser("org.opends.server.tools.BackUpDB",
                            INFO_BACKUPDB_TOOL_DESCRIPTION.get());
    argParser.setShortToolDescription(REF_SHORT_DESC_BACKUP.get());

    // Initialize all the command-line argument types and register them with the parser.
    try
    {
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('f')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      backendID =
              StringArgument.builder("backendID")
                      .shortIdentifier('n')
                      .description(INFO_BACKUPDB_DESCRIPTION_BACKEND_ID.get())
                      .multiValued()
                      .valuePlaceholder(INFO_BACKENDNAME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      backUpAll =
              BooleanArgument.builder("backUpAll")
                      .shortIdentifier('a')
                      .description(INFO_BACKUPDB_DESCRIPTION_BACKUP_ALL.get())
                      .buildAndAddToParser(argParser);
      backupIDString =
              StringArgument.builder("backupID")
                      .shortIdentifier('I')
                      .description(INFO_BACKUPDB_DESCRIPTION_BACKUP_ID.get())
                      .valuePlaceholder(INFO_BACKUPID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      backupDirectory =
              StringArgument.builder("backupDirectory")
                      .shortIdentifier('d')
                      .description(INFO_BACKUPDB_DESCRIPTION_BACKUP_DIR.get())
                      .required()
                      .valuePlaceholder(INFO_BACKUPDIR_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      incremental =
              BooleanArgument.builder("incremental")
                      .shortIdentifier('i')
                      .description(INFO_BACKUPDB_DESCRIPTION_INCREMENTAL.get())
                      .buildAndAddToParser(argParser);
      incrementalBaseID =
              StringArgument.builder("incrementalBaseID")
                      .shortIdentifier('B')
                      .description(INFO_BACKUPDB_DESCRIPTION_INCREMENTAL_BASE_ID.get())
                      .valuePlaceholder(INFO_BACKUPID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      compress =
              BooleanArgument.builder(OPTION_LONG_COMPRESS)
                      .shortIdentifier(OPTION_SHORT_COMPRESS)
                      .description(INFO_BACKUPDB_DESCRIPTION_COMPRESS.get())
                      .buildAndAddToParser(argParser);
      encrypt =
              BooleanArgument.builder("encrypt")
                      .shortIdentifier('y')
                      .description(INFO_BACKUPDB_DESCRIPTION_ENCRYPT.get())
                      .buildAndAddToParser(argParser);
      hash =
              BooleanArgument.builder("hash")
                      .shortIdentifier('A')
                      .description(INFO_BACKUPDB_DESCRIPTION_HASH.get())
                      .buildAndAddToParser(argParser);
      signHash =
              BooleanArgument.builder("signHash")
                      .shortIdentifier('s')
                      .description(INFO_BACKUPDB_DESCRIPTION_SIGN_HASH.get())
                      .buildAndAddToParser(argParser);

      final BooleanArgument displayUsage = showUsageArgument();
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
    }

    // Init the default values so that they can appear also on the usage.
    argParser.getArguments().initArgumentsWithConfiguration(argParser);

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
      validateTaskArgs();
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return 1;
    }
    catch (ClientException ce)
    {
      // No need to display the usage since the problem comes with a provided value.
      printWrappedText(err, ce.getMessageObject());
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
        argParser.displayMessageAndUsageReference(err, ERR_BACKUPDB_CANNOT_MIX_BACKUP_ALL_AND_BACKEND_ID.get(
            backUpAll.getLongIdentifier(), backendID.getLongIdentifier()));
        return 1;
      }
    }
    else if (! backendID.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_BACKUPDB_NEED_BACKUP_ALL_OR_BACKEND_ID.get(
          backUpAll.getLongIdentifier(), backendID.getLongIdentifier()));
      return 1;
    }
    else
    {
      // Check that the backendID has not been expressed twice.
      HashSet<String> backendIDLowerCase = new HashSet<>();
      HashSet<String> repeatedBackendIds = new HashSet<>();
      for (String id : backendID.getValues())
      {
        String lId = id.toLowerCase();
        if (!backendIDLowerCase.add(lId))
        {
          repeatedBackendIds.add(lId);
        }
      }
      if (!repeatedBackendIds.isEmpty())
      {
        argParser.displayMessageAndUsageReference(err,
            ERR_BACKUPDB_REPEATED_BACKEND_ID.get(Utils.joinAsString(", ", repeatedBackendIds)));
        return 1;
      }
    }

    // If the incremental base ID was specified, then make sure it is an
    // incremental backup.
    if (incrementalBaseID.isPresent() && ! incremental.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_BACKUPDB_INCREMENTAL_BASE_REQUIRES_INCREMENTAL.get(
              incrementalBaseID.getLongIdentifier(), incremental.getLongIdentifier()));
      return 1;
    }

    // Encryption or signing requires the ADS backend be available for
    // CryptoManager access to secret key entries. If no connection arguments
    //  are present, infer an offline backup.
    if ((encrypt.isPresent() || signHash.isPresent())
            && ! argParser.connectionArgumentsPresent()) {
      argParser.displayMessageAndUsageReference(err, ERR_BACKUPDB_ENCRYPT_OR_SIGN_REQUIRES_ONLINE.get(
          encrypt.getLongIdentifier(), signHash.getLongIdentifier()));
      return 1;
    }

    // If the signHash option was provided, then make sure that the hash option
    // was given.
    if (signHash.isPresent() && !hash.isPresent())
    {
      argParser.displayMessageAndUsageReference(err,
          ERR_BACKUPDB_SIGN_REQUIRES_HASH.get(signHash.getLongIdentifier(), hash.getLongIdentifier()));
      return 1;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      checkVersion();
    }
    catch (InitializationException e)
    {
      printWrappedText(err, e.getMessage());
      return 1;
    }

    return process(argParser, initializeServer, out, err);
  }

  @Override
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    addIfHasValue(attributes, ATTR_TASK_BACKUP_ALL, backUpAll);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_COMPRESS, compress);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_ENCRYPT, encrypt);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_HASH, hash);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_INCREMENTAL, incremental);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_SIGN_HASH, signHash);

    List<String> backendIDs = backendID.getValues();
    if (backendIDs != null && !backendIDs.isEmpty()) {
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_BACKEND_ID, backendIDs));
    }

    addIfHasValue(attributes, ATTR_BACKUP_ID, backupIDString);
    addIfHasValue(attributes, ATTR_BACKUP_DIRECTORY_PATH, backupDirectory);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_INCREMENTAL_BASE_ID, incrementalBaseID);
  }

  private void addIfHasValue(List<RawAttribute> attributes, String attrName, Argument arg)
  {
    if (hasValueDifferentThanDefaultValue(arg)) {
      attributes.add(new LDAPAttribute(attrName, arg.getValue()));
    }
  }

  private boolean hasValueDifferentThanDefaultValue(Argument arg)
  {
    return arg.getValue() != null
        && !arg.getValue().equals(arg.getDefaultValue());
  }

  @Override
  public String getTaskObjectclass() {
    return "ds-task-backup";
  }

  @Override
  public Class<?> getTaskClass() {
    return BackupTask.class;
  }

  @Override
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
        printWrappedText(
                err, ERR_BACKUPDB_CANNOT_CREATE_BACKUP_DIR.get(backupDirectory.getValue(), getExceptionMessage(e)));
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

    if (initializeServer)
    {
      try
      {
        new DirectoryServer.InitializationBuilder(configFile.getValue())
            .requireErrorAndDebugLogPublisher(out, err)
            .initialize();
      }
      catch (InitializationException ie)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(getExceptionMessage(ie)));
        return 1;
      }
    }


    // Get information about the backends defined in the server, and determine
    // whether we are backing up multiple backends or a single backend.
    List<Backend<?>> backendList = new ArrayList<>();
    List<BackendCfg> entryList = new ArrayList<>();
    List<List<DN>> dnList = new ArrayList<>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);
    int numBackends = backendList.size();

    boolean multiple;
    List<Backend<?>> backendsToArchive = new ArrayList<>(numBackends);
    Map<String, BackendCfg> configEntries = new HashMap<>(numBackends);
    if (backUpAll.isPresent())
    {
      for (int i=0; i < numBackends; i++)
      {
        Backend<?> b = backendList.get(i);
        if (b.supports(BackendOperation.BACKUP))
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
      // Iterate through the set of backends and pick out those that were requested.
      HashSet<String> requestedBackends = new HashSet<>(backendID.getValues());
      for (int i=0; i < numBackends; i++)
      {
        Backend<?> b = backendList.get(i);
        if (requestedBackends.contains(b.getBackendID()))
        {
          if (b.supports(BackendOperation.BACKUP))
          {
            backendsToArchive.add(b);
            configEntries.put(b.getBackendID(), entryList.get(i));
            requestedBackends.remove(b.getBackendID());
          }
          else
          {
            logger.warn(WARN_BACKUPDB_BACKUP_NOT_SUPPORTED, b.getBackendID());
          }
        }
      }

      if (! requestedBackends.isEmpty())
      {
        for (String id : requestedBackends)
        {
          logger.error(ERR_BACKUPDB_NO_BACKENDS_FOR_ID, id);
        }

        return 1;
      }

      // See if there are multiple backends to archive.
      multiple = backendsToArchive.size() > 1;
    }

    // If there are no backends to archive, then print an error and exit.
    if (backendsToArchive.isEmpty())
    {
      logger.warn(WARN_BACKUPDB_NO_BACKENDS_TO_ARCHIVE);
      return 1;
    }

    // Iterate through the backends to archive and back them up individually.
    boolean errorsEncountered = false;
    for (Backend<?> b : backendsToArchive)
    {
      if (!acquireSharedLock(b))
      {
        errorsEncountered = true;
        continue;
      }

      logger.info(NOTE_BACKUPDB_STARTING_BACKUP, b.getBackendID());

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
            logger.error(ERR_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR, descriptorPath, ce.getMessage());
            errorsEncountered = true;
            releaseSharedLock(b);
            continue;
          }
          catch (Exception e)
          {
            logger.error(ERR_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR, descriptorPath, getExceptionMessage(e));
            errorsEncountered = true;
            releaseSharedLock(b);
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
          logger.error(ERR_BACKUPDB_CANNOT_CREATE_BACKUP_DIR, backupDirPath, getExceptionMessage(e));
          errorsEncountered = true;
          releaseSharedLock(b);
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

      if (!b.supports(BackendOperation.BACKUP))
      {
        logger.error(ERR_BACKUPDB_CANNOT_BACKUP, b.getBackendID());
        errorsEncountered = true;
        unlockBackend(b);
        continue;
      }

      // Perform the backup.
      try
      {
        b.createBackup(backupConfig);
      }
      catch (DirectoryException de)
      {
        logger.error(ERR_BACKUPDB_ERROR_DURING_BACKUP, b.getBackendID(), de.getMessageObject());
        errorsEncountered = true;
        unlockBackend(b);
        continue;
      }
      catch (Exception e)
      {
        logger.error(ERR_BACKUPDB_ERROR_DURING_BACKUP, b.getBackendID(), getExceptionMessage(e));
        errorsEncountered = true;
        unlockBackend(b);
        continue;
      }

      if (!releaseSharedLock(b))
      {
        errorsEncountered = true;
      }
    }

    // Print a final completed message, indicating whether there were any errors
    // in the process.
    if (errorsEncountered)
    {
      logger.info(NOTE_BACKUPDB_COMPLETED_WITH_ERRORS);
      return 1;
    }
    logger.info(NOTE_BACKUPDB_COMPLETED_SUCCESSFULLY);
    return 0;
  }

  private boolean acquireSharedLock(Backend<?> b)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(b);
      StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        logger.error(ERR_BACKUPDB_CANNOT_LOCK_BACKEND, b.getBackendID(), failureReason);
        return false;
      }
      return true;
    }
    catch (Exception e)
    {
      logger.error(ERR_BACKUPDB_CANNOT_LOCK_BACKEND, b.getBackendID(), getExceptionMessage(e));
      return false;
    }
  }

  private boolean releaseSharedLock(Backend<?> b)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(b);
      StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.releaseLock(lockFile, failureReason))
      {
        logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
        return false;
      }
      return true;
    }
    catch (Exception e)
    {
      logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e));
      return false;
    }
  }

  private void unlockBackend(Backend<?> b)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(b);
      StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.releaseLock(lockFile, failureReason))
      {
        logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
      }
    }
    catch (Exception e)
    {
      logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e));
    }
  }

  @Override
  public String getTaskId() {
    return backupIDString != null ? backupIDString.getValue() : null;
  }
}
