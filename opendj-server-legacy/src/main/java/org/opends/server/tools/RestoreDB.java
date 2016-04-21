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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.forgerock.util.Utils;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tasks.RestoreTask;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.cli.LDAPConnectionArgumentParser;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This program provides a utility that may be used to restore a binary backup
 * of a Directory Server backend generated using the BackUpDB tool.  This will
 * be a process that is intended to run separate from Directory Server and not
 * internally within the server process (e.g., via the tasks interface).
 */
public class RestoreDB extends TaskTool {

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The main method for RestoreDB tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainRestoreDB(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
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
    return mainRestoreDB(args, true, System.out, System.err);
  }

  /**
   * Processes the command-line arguments and invokes the restore process.
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
  public static int mainRestoreDB(String[] args, boolean initializeServer,
                                  OutputStream outStream,
                                  OutputStream errStream)
  {
    RestoreDB tool = new RestoreDB();
    return tool.process(args, initializeServer, outStream, errStream);
  }


  /** Define the command-line arguments that may be used with this program. */
  private BooleanArgument displayUsage;
  private BooleanArgument listBackups;
  private BooleanArgument verifyOnly;
  private StringArgument  backupIDString;
  private StringArgument  configFile;
  private StringArgument  backupDirectory;


  private int process(String[] args, boolean initializeServer,
                      OutputStream outStream, OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser =
            createArgParser("org.opends.server.tools.RestoreDB",
                            INFO_RESTOREDB_TOOL_DESCRIPTION.get());


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      argParser.setShortToolDescription(REF_SHORT_DESC_RESTORE.get());

      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('f')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      backupIDString =
              StringArgument.builder("backupID")
                      .shortIdentifier('I')
                      .description(INFO_RESTOREDB_DESCRIPTION_BACKUP_ID.get())
                      .valuePlaceholder(INFO_BACKUPID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      backupDirectory =
              StringArgument.builder("backupDirectory")
                      .shortIdentifier('d')
                      .description(INFO_RESTOREDB_DESCRIPTION_BACKUP_DIR.get())
                      .required()
                      .valuePlaceholder(INFO_BACKUPDIR_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      listBackups =
              BooleanArgument.builder("listBackups")
                      .shortIdentifier('l')
                      .description(INFO_RESTOREDB_DESCRIPTION_LIST_BACKUPS.get())
                      .buildAndAddToParser(argParser);
      verifyOnly =
              BooleanArgument.builder(OPTION_LONG_DRYRUN)
                      .shortIdentifier(OPTION_SHORT_DRYRUN)
                      .description(INFO_RESTOREDB_DESCRIPTION_VERIFY_ONLY.get())
                      .buildAndAddToParser(argParser);

      displayUsage = showUsageArgument();
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


    if (listBackups.isPresent() && argParser.connectionArgumentsPresent()) {
      printWrappedText(err, ERR_LDAP_CONN_INCOMPATIBLE_ARGS.get(listBackups.getLongIdentifier()));
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


  /** {@inheritDoc} */
  @Override
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    addAttribute(attributes, ATTR_BACKUP_DIRECTORY_PATH, backupDirectory);
    addAttribute(attributes, ATTR_BACKUP_ID, backupIDString);
    addAttribute(attributes, ATTR_TASK_RESTORE_VERIFY_ONLY, verifyOnly);
  }

  private void addAttribute(List<RawAttribute> attributes, String attrName, Argument arg)
  {
    if (arg.getValue() != null && !arg.getValue().equals(arg.getDefaultValue()))
    {
      attributes.add(new LDAPAttribute(attrName, arg.getValue()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getTaskObjectclass() {
    return "ds-task-restore";
  }

  /** {@inheritDoc} */
  @Override
  public Class<?> getTaskClass() {
    return RestoreTask.class;
  }

  /** {@inheritDoc} */
  @Override
  protected int processLocal(boolean initializeServer,
                           PrintStream out,
                           PrintStream err) {


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
        printWrappedText(err, ERR_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(ie.getLocalizedMessage()));
        return 1;
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
      logger.error(ERR_RESTOREDB_CANNOT_READ_BACKUP_DIRECTORY, backupDirectory.getValue(), getExceptionMessage(e));
      return 1;
    }


    // If we're just going to be listing backups, then do that now.
    DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_LOCAL_TIME);
    if (listBackups.isPresent())
    {
      for (BackupInfo backupInfo : backupDir.getBackups().values())
      {
        LocalizableMessage message = INFO_RESTOREDB_LIST_BACKUP_ID.get(
                backupInfo.getBackupID());
        out.println(message);

        message = INFO_RESTOREDB_LIST_BACKUP_DATE.get(
                dateFormat.format(backupInfo.getBackupDate()));
        out.println(message);

        message = INFO_RESTOREDB_LIST_INCREMENTAL.get(backupInfo.isIncremental());
        out.println(message);

        message = INFO_RESTOREDB_LIST_COMPRESSED.get(backupInfo.isCompressed());
        out.println(message);

        message = INFO_RESTOREDB_LIST_ENCRYPTED.get(backupInfo.isEncrypted());
        out.println(message);

        byte[] hash = backupInfo.getUnsignedHash();
        message = INFO_RESTOREDB_LIST_HASHED.get(hash != null);
        out.println(message);

        byte[] signature = backupInfo.getSignedHash();
        message = INFO_RESTOREDB_LIST_SIGNED.get(signature != null);
        out.println(message);

        StringBuilder dependencyList = new StringBuilder();
        Set<String> dependencyIDs = backupInfo.getDependencies();
        if (! dependencyIDs.isEmpty())
        {
          Utils.joinAsString(dependencyList, ", ", dependencyIDs);
        }
        else
        {
          dependencyList.append("none");
        }


        message = INFO_RESTOREDB_LIST_DEPENDENCIES.get(dependencyList);
        out.println(message);
        out.println();
      }

      return 0;
    }


    // If a backup ID was specified, then make sure it is valid.  If none was
    // provided, then choose the latest backup from the archive.  Encrypted
    // or signed backups cannot be restored to a local (offline) server
    // instance.
    String backupID;
    {
      BackupInfo backupInfo = backupDir.getLatestBackup();
      if (backupInfo == null)
      {
        logger.error(ERR_RESTOREDB_NO_BACKUPS_IN_DIRECTORY, backupDirectory.getValue());
        return 1;
      }
      backupID = backupInfo.getBackupID();
      if (backupIDString.isPresent())
      {
        backupID = backupIDString.getValue();
        backupInfo = backupDir.getBackupInfo(backupID);
        if (backupInfo == null)
        {
          logger.error(ERR_RESTOREDB_INVALID_BACKUP_ID, backupID, backupDirectory.getValue());
          return 1;
        }
      }
      if (backupInfo.isEncrypted() || null != backupInfo.getSignedHash()) {
        logger.error(ERR_RESTOREDB_ENCRYPT_OR_SIGN_REQUIRES_ONLINE);
        return 1;
      }
    }


    // Get the DN of the backend configuration entry from the backup and load
    // the associated backend from the configuration.
    DN configEntryDN = backupDir.getConfigEntryDN();


    // Get information about the backends defined in the server and determine
    // which to use for the restore.
    List<Backend<?>> backendList = new ArrayList<>();
    List<BackendCfg> entryList = new ArrayList<>();
    List<List<DN>> dnList = new ArrayList<>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);


    Backend<?> backend = null;
    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend<?> b = backendList.get(i);
      BackendCfg e = entryList.get(i);
      if (e.dn().equals(configEntryDN))
      {
        backend     = b;
        break;
      }
    }

    if (backend == null)
    {
      logger.error(ERR_RESTOREDB_NO_BACKENDS_FOR_DN, backupDirectory.getValue(), configEntryDN);
      return 1;
    }
    else if (!backend.supports(BackendOperation.RESTORE))
    {
      logger.error(ERR_RESTOREDB_CANNOT_RESTORE, backend.getBackendID());
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
        logger.error(ERR_RESTOREDB_CANNOT_LOCK_BACKEND, backend.getBackendID(), failureReason);
        return 1;
      }
    }
    catch (Exception e)
    {
      logger.error(ERR_RESTOREDB_CANNOT_LOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
      return 1;
    }


    // Perform the restore.
    try
    {
      backend.restoreBackup(restoreConfig);
    }
    catch (DirectoryException de)
    {
      logger.error(ERR_RESTOREDB_ERROR_DURING_BACKUP, backupID, backupDir.getPath(), de.getMessageObject());
    }
    catch (Exception e)
    {
      logger.error(ERR_RESTOREDB_ERROR_DURING_BACKUP, backupID, backupDir.getPath(), getExceptionMessage(e));
    }


    // Release the exclusive lock on the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        logger.warn(WARN_RESTOREDB_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), failureReason);
      }
    }
    catch (Exception e)
    {
      logger.warn(WARN_RESTOREDB_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public String getTaskId() {
    return backupIDString != null? backupIDString.getValue() : null;
  }
}
