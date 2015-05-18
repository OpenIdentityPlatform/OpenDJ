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
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.jeb.DN2ID;
import org.opends.server.backends.jeb.DN2URI;
import org.opends.server.backends.jeb.DatabaseContainer;
import org.opends.server.backends.jeb.EntryContainer;
import org.opends.server.backends.jeb.EntryID;
import org.opends.server.backends.jeb.EntryIDSet;
import org.opends.server.backends.jeb.ID2Entry;
import org.opends.server.backends.jeb.Index;
import org.opends.server.backends.jeb.JebFormat;
import org.opends.server.backends.jeb.RootContainer;
import org.opends.server.backends.jeb.SortValuesSet;
import org.opends.server.backends.jeb.VLVIndex;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.SortKey;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TextTablePrinter;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

/**
 * This program provides a utility that may be used to debug a JE backend. This
 * tool provides the ability list various containers in the backend as well as
 * dump the contents of database containers. This will be
 * a process that is intended to run separate from Directory Server and not
 * internally within the server process (e.g., via the tasks interface).
 */
public class DBTest
{
  /** The error stream which this application should use. */
  private final PrintStream err;

  /** The output stream which this application should use. */
  private final PrintStream out;

  /**
   * Flag indicating whether or not the global arguments have already been
   * initialized.
   */
  private boolean globalArgumentsInitialized;

  /** The command-line argument parser. */
  private final SubCommandArgumentParser parser;

  /** The argument which should be used to request usage information. */
  private BooleanArgument showUsageArgument;

  /** The argument which should be used to specify the config class. */
  private StringArgument configClass;

  /** THe argument which should be used to specify the config file. */
  private StringArgument configFile;

  /**
   * Flag indicating whether or not the sub-commands have already been
   * initialized.
   */
  private boolean subCommandsInitialized;



  /**
   * Provides the command-line arguments to the main application for
   * processing.
   *
   * @param args
   *          The set of command-line arguments provided to this
   *          program.
   */
  public static void main(String[] args) {
    int exitCode = main(args, true, System.out, System.err);
    if (exitCode != 0) {
      System.exit(filterExitCode(exitCode));
    }
  }


  /**
   * Provides the command-line arguments to the main application for
   * processing and returns the exit code as an integer.
   *
   * @param args
   *          The set of command-line arguments provided to this
   *          program.
   * @param initializeServer
   *          Indicates whether to perform basic initialization (which
   *          should not be done if the tool is running in the same
   *          JVM as the server).
   * @param outStream
   *          The output stream for standard output.
   * @param errStream
   *          The output stream for standard error.
   * @return Zero to indicate that the program completed successfully,
   *         or non-zero to indicate that an error occurred.
   */
  public static int main(String[] args, boolean initializeServer,
                         OutputStream outStream, OutputStream errStream) {
    DBTest app = new DBTest(outStream, errStream);

    // Run the application.
    return app.run(args, initializeServer);
  }

  /**
   * Creates a new dsconfig application instance.
   *
   * @param out
   *          The application output stream.
   * @param err
   *          The application error stream.
   */
  public DBTest(OutputStream out, OutputStream err)
  {
    this.out = NullOutputStream.wrapOrNullStream(out);
    this.err = NullOutputStream.wrapOrNullStream(err);
    JDKLogging.disableLogging();

    LocalizableMessage toolDescription = INFO_DESCRIPTION_DBTEST_TOOL.get();
    this.parser = new SubCommandArgumentParser(getClass().getName(), toolDescription, false);
    this.parser.setShortToolDescription(REF_SHORT_DESC_DBTEST.get());
    this.parser.setVersionHandler(new DirectoryServerVersionHandler());
  }

  /** Displays the provided message followed by a help usage reference. */
  private void displayMessageAndUsageReference(LocalizableMessage message) {
    printMessage(message);
    printMessage(LocalizableMessage.EMPTY);
    printMessage(parser.getHelpUsageReference());
  }



  /**
   * Registers the global arguments with the argument parser.
   *
   * @throws ArgumentException
   *           If a global argument could not be registered.
   */
  private void initializeGlobalArguments() throws ArgumentException {
    if (!globalArgumentsInitialized) {
      configClass =
          new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                             OPTION_LONG_CONFIG_CLASS, true, false,
                             true, INFO_CONFIGCLASS_PLACEHOLDER.get(),
                             ConfigFileHandler.class.getName(), null,
                             INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);

      configFile =
          new StringArgument("configfile", 'f', "configFile", true, false,
                             true, INFO_CONFIGFILE_PLACEHOLDER.get(), null,
                             null,
                             INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);


      showUsageArgument = CommonArguments.getShowUsage();

      // Register the global arguments.
      parser.addGlobalArgument(showUsageArgument);
      parser.setUsageArgument(showUsageArgument, out);
      parser.addGlobalArgument(configClass);
      parser.addGlobalArgument(configFile);

      globalArgumentsInitialized = true;
    }
  }



  /**
   * Registers the sub-commands with the argument parser.
   *
   * @throws ArgumentException
   *           If a sub-command could not be created.
   */
  private void initializeSubCommands() throws ArgumentException {
    if (!subCommandsInitialized) {
      StringArgument backendID;
      StringArgument baseDN;
      StringArgument databaseName;
      BooleanArgument skipDecode;
      BooleanArgument statsOnly;
      StringArgument maxKeyValue;
      StringArgument minKeyValue;
      IntegerArgument maxDataSize;
      IntegerArgument minDataSize;
      SubCommand sub;

      sub = new SubCommand(parser, "list-root-containers",
                     INFO_DESCRIPTION_DBTEST_SUBCMD_LIST_ROOT_CONTAINERS.get());


      sub = new SubCommand(parser, "list-entry-containers",
                    INFO_DESCRIPTION_DBTEST_SUBCMD_LIST_ENTRY_CONTAINERS.get());
      backendID =
          new StringArgument("backendid", 'n', "backendID", true, false, true,
                             INFO_BACKENDNAME_PLACEHOLDER.get(), null, null,
                             INFO_DESCRIPTION_DBTEST_BACKEND_ID.get());
      sub.addArgument(backendID);


      sub = new SubCommand(parser, "list-database-containers",
                 INFO_DESCRIPTION_DBTEST_SUBCMD_LIST_DATABASE_CONTAINERS.get());
      backendID =
          new StringArgument("backendid", 'n', "backendID", true, false, true,
                             INFO_BACKENDNAME_PLACEHOLDER.get(), null, null,
                             INFO_DESCRIPTION_DBTEST_BACKEND_ID.get());
      sub.addArgument(backendID);
      baseDN =
          new StringArgument("basedn", 'b', "baseDN", false,
                             false, true, INFO_BASEDN_PLACEHOLDER.get(), null,
                             null,
                             INFO_DESCRIPTION_DBTEST_BASE_DN.get());
      sub.addArgument(baseDN);


      sub = new SubCommand(parser, "dump-database-container",
                  INFO_DESCRIPTION_DBTEST_SUBCMD_DUMP_DATABASE_CONTAINER.get());
      backendID =
          new StringArgument("backendid", 'n', "backendID", true, false, true,
                             INFO_BACKENDNAME_PLACEHOLDER.get(), null, null,
                             INFO_DESCRIPTION_DBTEST_BACKEND_ID.get());
      sub.addArgument(backendID);
      baseDN =
          new StringArgument("basedn", 'b', "baseDN", true,
                             false, true, INFO_BASEDN_PLACEHOLDER.get(), null,
                             null,
                             INFO_DESCRIPTION_DBTEST_BASE_DN.get());
      sub.addArgument(baseDN);
      databaseName =
          new StringArgument("databasename", 'd', "databaseName", true,
                             false, true, INFO_DATABASE_NAME_PLACEHOLDER.get(),
                             null, null,
                             INFO_DESCRIPTION_DBTEST_DATABASE_NAME.get());
      sub.addArgument(databaseName);
      skipDecode =
          new BooleanArgument("skipdecode", 'p', "skipDecode",
                              INFO_DESCRIPTION_DBTEST_SKIP_DECODE.get());
      sub.addArgument(skipDecode);
      statsOnly =
          new BooleanArgument("statsonly", 'q', "statsOnly",
                              INFO_DESCRIPTION_DBTEST_STATS_ONLY.get());
      sub.addArgument(statsOnly);
      maxKeyValue = new StringArgument("maxkeyvalue", 'K', "maxKeyValue", false,
                                       false, true,
                                       INFO_MAX_KEY_VALUE_PLACEHOLDER.get(),
                                       null, null,
                                   INFO_DESCRIPTION_DBTEST_MAX_KEY_VALUE.get());
      sub.addArgument(maxKeyValue);
      minKeyValue = new StringArgument("minkeyvalue", 'k', "minKeyValue", false,
                                       false, true,
                                       INFO_MIN_KEY_VALUE_PLACEHOLDER.get(),
                                       null,
                                       null,
                                   INFO_DESCRIPTION_DBTEST_MIN_KEY_VALUE.get());
      sub.addArgument(minKeyValue);
      maxDataSize = new IntegerArgument("maxdatasize", 'S', "maxDataSize",
                                        false, false, true,
                                        INFO_MAX_DATA_SIZE_PLACEHOLDER.get(),
                                        -1,
                                        null,
                                   INFO_DESCRIPTION_DBTEST_MAX_DATA_SIZE.get());
      sub.addArgument(maxDataSize);
      minDataSize = new IntegerArgument("mindatasize", 's', "minDataSize",
                                        false, false, true,
                                        INFO_MIN_DATA_SIZE_PLACEHOLDER.get(),
                                        -1,
                                        null,
                                   INFO_DESCRIPTION_DBTEST_MIN_DATA_SIZE.get());
      sub.addArgument(minDataSize);


      sub = new SubCommand(parser, "list-index-status",
                        INFO_DESCRIPTION_DBTEST_SUBCMD_LIST_INDEX_STATUS.get());
      sub.setDocDescriptionSupplement(
              SUPPLEMENT_DESCRIPTION_DBTEST_SUBCMD_LIST_INDEX_STATUS.get());
      backendID =
          new StringArgument("backendid", 'n', "backendID", true, false, true,
                             INFO_BACKENDNAME_PLACEHOLDER.get(), null, null,
                             INFO_DESCRIPTION_DBTEST_BACKEND_ID.get());
      sub.addArgument(backendID);
      baseDN =
          new StringArgument("basedn", 'b', "baseDN", true,
                             true, true, INFO_BASEDN_PLACEHOLDER.get(), null,
                             null,
                             INFO_DESCRIPTION_DBTEST_BASE_DN.get());
      sub.addArgument(baseDN);

      subCommandsInitialized = true;
    }
  }


  /**
   * Parses the provided command-line arguments and makes the
   * appropriate changes to the Directory Server configuration.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @param initializeServer
   *          Indicates whether to perform basic initialization (which
   *          should not be done if the tool is running in the same
   *          JVM as the server).
   * @return The exit code from the configuration processing. A
   *         nonzero value indicates that there was some kind of
   *         problem during the configuration processing.
   */
  private int run(String[] args, boolean initializeServer) {

    // Register global arguments and sub-commands.
    try {
      initializeGlobalArguments();
      initializeSubCommands();
    } catch (ArgumentException e) {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(e.getMessage());
      printMessage(message);
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try {
      parser.parseArguments(args);
    } catch (ArgumentException ae) {
      LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      displayMessageAndUsageReference(message);
      return 1;
    }

    // If the usage/version argument was provided, then we don't need
    // to do anything else.
    if (parser.usageOrVersionDisplayed()) {
      return 0;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      printMessage(e.getMessageObject());
      return 1;
    }

    // Only initialize the server when run as a standalone
    // application.
    if (initializeServer) {
      // Perform the initial bootstrap of the Directory Server and process the
      // configuration.
      DirectoryServer directoryServer = DirectoryServer.getInstance();
      try
      {
        DirectoryServer.bootstrapClient();
        DirectoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_SERVER_BOOTSTRAP_ERROR.get(
                getExceptionMessage(e));
        printMessage(message);
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        LocalizableMessage message = ERR_CANNOT_LOAD_CONFIG.get(
            ie.getMessage());
        printMessage(message);
        return 1;
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_LOAD_CONFIG.get(
            getExceptionMessage(e));
        printMessage(message);
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException | InitializationException e)
      {
        LocalizableMessage message = ERR_CANNOT_LOAD_SCHEMA.get(e.getMessage());
        printMessage(message);
        return 1;
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_LOAD_SCHEMA.get(getExceptionMessage(e));
        printMessage(message);
        return 1;
      }



      // Initialize the Directory Server core configuration.
      try
      {
        CoreConfigManager coreConfigManager = new CoreConfigManager(directoryServer.getServerContext());
        coreConfigManager.initializeCoreConfig();
      }
      catch (ConfigException | InitializationException e)
      {
        LocalizableMessage message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(e.getMessage());
        printMessage(message);
        return 1;
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(getExceptionMessage(e));
        printMessage(message);
        return 1;
      }


      // Initialize the Directory Server crypto manager.
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (ConfigException | InitializationException e)
      {
        LocalizableMessage message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(e.getMessage());
        printMessage(message);
        return 1;
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(getExceptionMessage(e));
        printMessage(message);
        return 1;
      }
    }

    // Make sure that we have a sub-command.
    if (parser.getSubCommand() == null)
    {
      LocalizableMessage message = ERR_DBTEST_MISSING_SUBCOMMAND.get();
      displayMessageAndUsageReference(message);
      return 1;
    }

    // Retrieve the sub-command implementation and run it.
    SubCommand subCommand = parser.getSubCommand();
    try {
      if("list-root-containers".equals(subCommand.getName()))
      {
        return listRootContainers();
      }
      else if("list-entry-containers".equals(subCommand.getName()))
      {
        return listEntryContainers(subCommand.getArgument("backendid"));
      }
      else if("list-database-containers".equals(subCommand.getName()))
      {
        return listDatabaseContainers(subCommand.getArgument("backendid"),
                                      subCommand.getArgument("basedn"));
      }
      else if("dump-database-container".equals(subCommand.getName()))
      {
        return dumpDatabaseContainer(subCommand.getArgument("backendid"),
                                     subCommand.getArgument("basedn"),
                                     subCommand.getArgument("databasename"),
                                     subCommand.getArgument("skipdecode"),
                                     subCommand.getArgument("statsonly"),
                                     subCommand.getArgument("maxkeyvalue"),
                                     subCommand.getArgument("minkeyvalue"),
                                     subCommand.getArgument("maxdatasize"),
                                     subCommand.getArgument("mindatasize"));
      }
      else if("list-index-status".equals(subCommand.getName()))
      {
        return listIndexStatus(subCommand.getArgument("backendid"),
                                      subCommand.getArgument("basedn"));
      }
      return 0;
    } catch (Exception e) {
      printMessage(LocalizableMessage.raw(StaticUtils.stackTraceToString(e)));
      return 1;
    }
  }

  private int listRootContainers()
  {
    Map<LocalDBBackendCfg, BackendImpl> jeBackends = getJEBackends();
    int count = 0;

    // Create a table of their properties.
    TableBuilder builder = new TableBuilder();

    builder.appendHeading(INFO_LABEL_DBTEST_BACKEND_ID.get());
    builder.appendHeading(INFO_LABEL_DBTEST_DB_DIRECTORY.get());

    for(Map.Entry<LocalDBBackendCfg, BackendImpl> backend :
        jeBackends.entrySet())
    {
      builder.startRow();
      builder.appendCell(backend.getValue().getBackendID());
      builder.appendCell(backend.getKey().getDBDirectory());
      count++;
    }

    TextTablePrinter printer = new TextTablePrinter(out);
    builder.print(printer);
    out.format("%nTotal: %d%n", count);

    return 0;
  }

  private int listEntryContainers(Argument backendID)
  {
    BackendImpl backend = getBackendById(backendID);
    if(backend == null)
    {
      printMessage(ERR_DBTEST_NO_BACKENDS_FOR_ID.get(backendID.getValue()));
      return 1;
    }

    // Acquire an shared lock for the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        printMessage(ERR_DBTEST_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), failureReason));
        return 1;
      }
    }
    catch (Exception e)
    {
      printMessage(ERR_DBTEST_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), getExceptionMessage(e)));
      return 1;
    }

    RootContainer rc;
    try
    {
      rc = backend.getReadOnlyRootContainer();
    }
    catch(Exception e)
    {
      printMessage(ERR_DBTEST_ERROR_INITIALIZING_BACKEND.get(
          backend.getBackendID(),
          StaticUtils.stackTraceToSingleLineString(e)));
      return 1;
    }

    try
    {
      // Create a table of their properties.
      TableBuilder builder = new TableBuilder();
      int count = 0;

      builder.appendHeading(INFO_LABEL_DBTEST_BASE_DN.get());
      builder.appendHeading(INFO_LABEL_DBTEST_JE_DATABASE_PREFIX.get());
      builder.appendHeading(INFO_LABEL_DBTEST_ENTRY_COUNT.get());

      for(EntryContainer ec : rc.getEntryContainers())
      {
        builder.startRow();
        builder.appendCell(ec.getBaseDN());
        builder.appendCell(ec.getDatabasePrefix());
        builder.appendCell(ec.getEntryCount());
        count++;
      }

      TextTablePrinter printer = new TextTablePrinter(out);
      builder.print(printer);
      out.format("%nTotal: %d%n", count);

      return 0;
    }
    catch(DatabaseException de)
    {
      printMessage(ERR_DBTEST_ERROR_READING_DATABASE.get(
          StaticUtils.stackTraceToSingleLineString(de)));
      return 1;
    }
    finally
    {
      close(rc);
      releaseSharedLock(backend);
    }
  }

  private int listDatabaseContainers(Argument backendID, Argument baseDN)
  {
    BackendImpl backend = getBackendById(backendID);
    if(backend == null)
    {
      printMessage(ERR_DBTEST_NO_BACKENDS_FOR_ID.get(backendID.getValue()));
      return 1;
    }

    DN base = null;
    if(baseDN.isPresent())
    {
      try
      {
        base = DN.valueOf(baseDN.getValue());
      }
      catch(DirectoryException de)
      {
        printMessage(ERR_DBTEST_DECODE_BASE_DN.get(baseDN.getValue(),
                                                   getExceptionMessage(de)));
        return 1;
      }
    }

    // Acquire an shared lock for the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        printMessage(ERR_DBTEST_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), failureReason));
        return 1;
      }
    }
    catch (Exception e)
    {
      printMessage(ERR_DBTEST_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), getExceptionMessage(e)));
      return 1;
    }

    RootContainer rc;
    try
    {
      rc = backend.getReadOnlyRootContainer();
    }
    catch(Exception e)
    {
      printMessage(ERR_DBTEST_ERROR_INITIALIZING_BACKEND.get(
          backend.getBackendID(),
          StaticUtils.stackTraceToSingleLineString(e)));
      return 1;
    }


    try
    {
      // Create a table of their properties.
      TableBuilder builder = new TableBuilder();
      int count = 0;

      builder.appendHeading(INFO_LABEL_DBTEST_DATABASE_NAME.get());
      builder.appendHeading(INFO_LABEL_DBTEST_DATABASE_TYPE.get());
      builder.appendHeading(INFO_LABEL_DBTEST_JE_DATABASE_NAME.get());
      builder.appendHeading(INFO_LABEL_DBTEST_ENTRY_COUNT.get());

      if(base != null)
      {
        EntryContainer ec = rc.getEntryContainer(base);
        if(ec == null)
        {
          printMessage(ERR_DBTEST_NO_ENTRY_CONTAINERS_FOR_BASE_DN.get(
              base, backend.getBackendID()));
          return 1;
        }

        count = appendDatabaseContainerRows(builder, ec, count);
      }
      else
      {
        for(EntryContainer ec : rc.getEntryContainers())
        {
          builder.startRow();
          builder.appendCell("Base DN: " + ec.getBaseDN());
          count = appendDatabaseContainerRows(builder, ec, count);
        }
      }

      TextTablePrinter printer = new TextTablePrinter(out);
      builder.print(printer);
      out.format("%nTotal: %d%n", count);

      return 0;
    }
    catch(DatabaseException de)
    {
      printMessage(ERR_DBTEST_ERROR_READING_DATABASE.get(
          StaticUtils.stackTraceToSingleLineString(de)));
      return 1;
    }
    finally
    {
      close(rc);
      releaseSharedLock(backend);
    }
  }

  private int appendDatabaseContainerRows(TableBuilder builder, EntryContainer ec, int count)
  {
    ArrayList<DatabaseContainer> databaseContainers = new ArrayList<DatabaseContainer>();
    ec.listDatabases(databaseContainers);
    String toReplace = ec.getDatabasePrefix() + "_";
    for(DatabaseContainer dc : databaseContainers)
    {
      builder.startRow();
      builder.appendCell(dc.getName().replace(toReplace, ""));
      builder.appendCell(dc.getClass().getSimpleName());
      builder.appendCell(dc.getName());
      builder.appendCell(dc.getRecordCount());
      count++;
    }
    return count;
  }

  private void close(RootContainer rc)
  {
    try
    {
      rc.close();
    }
    catch(DatabaseException ignored)
    {
      // Ignore.
    }
  }

  private void releaseSharedLock(BackendImpl backend)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.releaseLock(lockFile, failureReason))
      {
        printMessage(WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(backend.getBackendID(), failureReason));
      }
    }
    catch (Exception e)
    {
      printMessage(WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(backend.getBackendID(), getExceptionMessage(e)));
    }
  }

  private BackendImpl getBackendById(Argument backendId)
  {
    Collection<BackendImpl> backends = getJEBackends().values();
    String backendID = backendId.getValue();
    for (BackendImpl b : backends)
    {
      if (b.getBackendID().equalsIgnoreCase(backendID))
      {
        return b;
      }
    }
    return null;
  }

  private int listIndexStatus(Argument backendID, Argument baseDN)
  {
    BackendImpl backend = getBackendById(backendID);
    if(backend == null)
    {
      printMessage(ERR_DBTEST_NO_BACKENDS_FOR_ID.get(backendID.getValue()));
      return 1;
    }

    DN base = null;
    if(baseDN.isPresent())
    {
      try
      {
        base = DN.valueOf(baseDN.getValue());
      }
      catch(DirectoryException de)
      {
        printMessage(ERR_DBTEST_DECODE_BASE_DN.get(baseDN.getValue(),
                                                   getExceptionMessage(de)));
        return 1;
      }
    }

    // Acquire an shared lock for the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        printMessage(ERR_DBTEST_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), failureReason));
        return 1;
      }
    }
    catch (Exception e)
    {
      printMessage(ERR_DBTEST_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), getExceptionMessage(e)));
      return 1;
    }

    RootContainer rc;
    try
    {
      rc = backend.getReadOnlyRootContainer();
    }
    catch(Exception e)
    {
      printMessage(ERR_DBTEST_ERROR_INITIALIZING_BACKEND.get(
          backend.getBackendID(),
          StaticUtils.stackTraceToSingleLineString(e)));
      return 1;
    }


    try
    {
      // Create a table of their properties.
      TableBuilder builder = new TableBuilder();
      int count = 0;

      builder.appendHeading(INFO_LABEL_DBTEST_INDEX_NAME.get());
      builder.appendHeading(INFO_LABEL_DBTEST_INDEX_TYPE.get());
      builder.appendHeading(INFO_LABEL_DBTEST_JE_DATABASE_NAME.get());
      builder.appendHeading(INFO_LABEL_DBTEST_INDEX_STATUS.get());
      builder.appendHeading(INFO_LABEL_DBTEST_JE_RECORD_COUNT.get());
      builder.appendHeading(
          INFO_LABEL_DBTEST_INDEX_UNDEFINED_RECORD_COUNT.get());
      builder.appendHeading(LocalizableMessage.raw("95%"));
      builder.appendHeading(LocalizableMessage.raw("90%"));
      builder.appendHeading(LocalizableMessage.raw("85%"));


      EntryContainer ec = rc.getEntryContainer(base);
      if(ec == null)
      {
        printMessage(ERR_DBTEST_NO_ENTRY_CONTAINERS_FOR_BASE_DN.get(base, backend.getBackendID()));
        return 1;
      }

      ArrayList<DatabaseContainer> databaseContainers =
          new ArrayList<DatabaseContainer>();
      Map<Index, StringBuilder> undefinedKeys =
          new HashMap<Index, StringBuilder>();
      ec.listDatabases(databaseContainers);
      String toReplace = ec.getDatabasePrefix() + "_";
      for(DatabaseContainer dc : databaseContainers)
      {
        if(dc instanceof Index || dc instanceof VLVIndex)
        {
          builder.startRow();
          builder.appendCell(dc.getName().replace(toReplace, ""));
          builder.appendCell(dc.getClass().getSimpleName());
          builder.appendCell(dc.getName());
          builder.appendCell(ec.getState().getIndexTrustState(null, dc));
          builder.appendCell(dc.getRecordCount());

          if(dc instanceof Index)
          {
            Index index = (Index)dc;
            long undefined = 0, ninetyFive = 0, ninety = 0, eighty = 0;
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            LockMode lockMode = LockMode.DEFAULT;
            OperationStatus status;

            Cursor cursor = dc.openCursor(null, CursorConfig.DEFAULT);
            status = cursor.getFirst(key, data, lockMode);
            while(status == OperationStatus.SUCCESS)
            {
              byte[] bytes = data.getData();
              if (bytes.length == 0 || (bytes[0] & 0x80) == 0x80)
              {
                // Entry limit has exceeded and there is no encoded
                //  undefined set size.
                undefined ++;
                StringBuilder keyList = undefinedKeys.get(index);
                if(keyList == null)
                {
                  keyList = new StringBuilder();
                  undefinedKeys.put(index, keyList);
                }
                else
                {
                  keyList.append(" ");
                }
                if(index == ec.getID2Children() || index == ec.getID2Subtree())
                {
                  keyList.append("[").append(
                    JebFormat.entryIDFromDatabase(key.getData())).append("]");
                }
                else
                {
                  keyList.append("[").append(
                    new String(key.getData())).append("]");
                }
              }
              else
              {
                // Seems like entry limit has not been exceeded and the bytes
                // is a list of entry IDs.
                double percentFull =
                    (bytes.length / (double)8) / index.getIndexEntryLimit();
                if(percentFull >= .8)
                {
                  if(percentFull < .9)
                  {
                    eighty++;
                  }
                  else if(percentFull < .95)
                  {
                    ninety++;
                  }
                  else
                  {
                    ninetyFive++;
                  }
                }
              }
              status = cursor.getNext(key, data, lockMode);
            }
            builder.appendCell(undefined);
            builder.appendCell(ninetyFive);
            builder.appendCell(ninety);
            builder.appendCell(eighty);
            cursor.close();
          }
          else
          {
            builder.appendCell("-");
            builder.appendCell("-");
            builder.appendCell("-");
            builder.appendCell("-");
          }

          count++;
        }
      }

      TextTablePrinter printer = new TextTablePrinter(out);
      builder.print(printer);
      out.format("%nTotal: %d%n", count);
      for(Map.Entry<Index, StringBuilder> e : undefinedKeys.entrySet())
      {
        out.format("%nIndex: %s%n", e.getKey().getName().replace(toReplace, ""));
        out.format("Undefined keys: %s%n", e.getValue().toString());
      }
      return 0;
    }
    catch(DatabaseException de)
    {
      printMessage(ERR_DBTEST_ERROR_READING_DATABASE.get(
          StaticUtils.stackTraceToSingleLineString(de)));
      return 1;
    }
    finally
    {
      close(rc);
      releaseSharedLock(backend);
    }
  }

  private int dumpDatabaseContainer(Argument backendID, Argument baseDN,
                                    Argument databaseName, Argument skipDecode,
                                    Argument statsOnly,
                                    Argument maxKeyValue, Argument minKeyValue,
                                    Argument maxDataSize, Argument minDataSize)
  {
    BackendImpl backend = getBackendById(backendID);
    if(backend == null)
    {
      printMessage(ERR_DBTEST_NO_BACKENDS_FOR_ID.get(backendID.getValue()));
      return 1;
    }

    DN base = null;
    try
    {
      base = DN.valueOf(baseDN.getValue());
    }
    catch(DirectoryException de)
    {
      printMessage(ERR_DBTEST_DECODE_BASE_DN.get(baseDN.getValue(),
                                                 getExceptionMessage(de)));
      return 1;
    }

    // Acquire an shared lock for the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        printMessage(ERR_DBTEST_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), failureReason));
        return 1;
      }
    }
    catch (Exception e)
    {
      printMessage(ERR_DBTEST_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), getExceptionMessage(e)));
      return 1;
    }

    RootContainer rc;
    try
    {
      rc = backend.getReadOnlyRootContainer();
    }
    catch(Exception e)
    {
      printMessage(ERR_DBTEST_ERROR_INITIALIZING_BACKEND.get(
          backend.getBackendID(),
          StaticUtils.stackTraceToSingleLineString(e)));
      return 1;
    }

    try
    {
      EntryContainer ec = rc.getEntryContainer(base);
      if(ec == null)
      {
        printMessage(ERR_DBTEST_NO_ENTRY_CONTAINERS_FOR_BASE_DN.get(base, backend.getBackendID()));
        return 1;
      }

      DatabaseContainer databaseContainer = null;
      ArrayList<DatabaseContainer> databaseContainers =
          new ArrayList<DatabaseContainer>();
      ec.listDatabases(databaseContainers);
      String toReplace = ec.getDatabasePrefix() + "_";
      for(DatabaseContainer dc : databaseContainers)
      {
        if(dc.getName().replace(toReplace, "").equalsIgnoreCase(databaseName.getValue()))
        {
          databaseContainer = dc;
          break;
        }
      }

      if(databaseContainer == null)
      {
        printMessage(ERR_DBTEST_NO_DATABASE_CONTAINERS_FOR_NAME.get(
            databaseName.getValue(), base, backend.getBackendID()));
        return 1;
      }

      int count = 0;
      long totalKeySize = 0;
      long totalDataSize = 0;
      int indent = 4;

      Cursor cursor = databaseContainer.openCursor(null, CursorConfig.DEFAULT);
      try
      {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        LockMode lockMode = LockMode.DEFAULT;
        OperationStatus status;
        byte[] start = null;
        byte[] end = null;
        int minSize = -1;
        int maxSize = -1;

        if(maxDataSize.isPresent())
        {
          try
          {
            maxSize = maxDataSize.getIntValue();
          }
          catch(Exception e)
          {
            printMessage(ERR_DBTEST_CANNOT_DECODE_SIZE.get(
                maxDataSize.getValue(), getExceptionMessage(e)));
            return 1;
          }
        }

        if(minDataSize.isPresent())
        {
          try
          {
            minSize = minDataSize.getIntValue();
          }
          catch(Exception e)
          {
            printMessage(ERR_DBTEST_CANNOT_DECODE_SIZE.get(
                minDataSize.getValue(), getExceptionMessage(e)));
            return 1;
          }
        }

        // Parse the min value if given
        if(minKeyValue.isPresent())
        {
          try
          {
            start = parseKeyValue(minKeyValue.getValue(), databaseContainer);
          }
          catch(Exception e)
          {
            printMessage(ERR_DBTEST_CANNOT_DECODE_KEY.get(
                minKeyValue.getValue(), getExceptionMessage(e)));
            return 1;
          }
        }

        // Parse the max value if given
        if(maxKeyValue.isPresent())
        {
          try
          {
            end = parseKeyValue(maxKeyValue.getValue(), databaseContainer);
          }
          catch(Exception e)
          {
            printMessage(ERR_DBTEST_CANNOT_DECODE_KEY.get(
                maxKeyValue.getValue(), getExceptionMessage(e)));
            return 1;
          }
        }


        if(start != null)
        {
          key.setData(start);
          status = cursor.getSearchKey(key, data, lockMode);
        }
        else
        {
          status = cursor.getFirst(key, data, lockMode);
        }

        final String lineSep = System.getProperty("line.separator");
        while(status == OperationStatus.SUCCESS)
        {
          // Make sure this record is within the value size params
          if((minSize > 0 && data.getSize() < minSize) ||
              (maxSize > 0 && data.getSize() > maxSize))
          {
            status = cursor.getNext(key, data, lockMode);
            continue;
          }

          // Make sure we haven't gone pass the max value yet
          if(end != null
              && getComparator(databaseContainer).compare(key.getData(), end) > 0)
          {
            break;
          }

          if (!statsOnly.isPresent())
          {
            LocalizableMessage keyLabel = INFO_LABEL_DBTEST_KEY.get();
            LocalizableMessage dataLabel = INFO_LABEL_DBTEST_DATA.get();

            String formatedKey = null;
            String formatedData = null;

            if(!skipDecode.isPresent())
            {
              if(databaseContainer instanceof DN2ID)
              {
                try
                {
                  formatedKey = new String(key.getData()) + ec.getBaseDN();
                  keyLabel = INFO_LABEL_DBTEST_ENTRY_DN.get();
                }
                catch(Exception e)
                {
                  printMessage(ERR_DBTEST_DECODE_FAIL.get(getExceptionMessage(e)));
                }
                formatedData = String.valueOf(
                  JebFormat.entryIDFromDatabase(data.getData()));
                dataLabel = INFO_LABEL_DBTEST_ENTRY_ID.get();
              }
              else if(databaseContainer instanceof ID2Entry)
              {
                formatedKey = String.valueOf(
                    JebFormat.entryIDFromDatabase(key.getData()));
                keyLabel = INFO_LABEL_DBTEST_ENTRY_ID.get();
                try
                {
                  formatedData = lineSep +
                      ID2Entry.entryFromDatabase(
                        ByteString.wrap(data.getData()),
                        ec.getRootContainer().getCompressedSchema()).toLDIFString();
                  dataLabel = INFO_LABEL_DBTEST_ENTRY.get();
                }
                catch(Exception e)
                {
                  printMessage(ERR_DBTEST_DECODE_FAIL.get(getExceptionMessage(e)));
                }
              }
              else if(databaseContainer instanceof DN2URI)
              {
                try
                {
                  formatedKey = new String(key.getData());
                  keyLabel = INFO_LABEL_DBTEST_ENTRY_DN.get();
                }
                catch(Exception e)
                {
                  printMessage(ERR_DBTEST_DECODE_FAIL.get(getExceptionMessage(e)));
                }
                formatedData = new String(key.getData());
                dataLabel = INFO_LABEL_DBTEST_URI.get();
              }
              else if(databaseContainer instanceof Index)
              {
                formatedKey = new String(key.getData());
                keyLabel = INFO_LABEL_DBTEST_INDEX_VALUE.get();

                EntryIDSet idSet = new EntryIDSet(key.getData(), data.getData());
                if(idSet.isDefined())
                {
                  int lineCount = 0;
                  StringBuilder builder = new StringBuilder();

                  for (EntryID entryID : idSet)
                  {
                    builder.append(entryID);
                    if(lineCount == 10)
                    {
                      builder.append(lineSep);
                      lineCount = 0;
                    }
                    else
                    {
                      builder.append(" ");
                      lineCount++;
                    }
                  }
                  formatedData = builder.toString();
                }
                else
                {
                  formatedData = idSet.toString();
                }
                dataLabel = INFO_LABEL_DBTEST_INDEX_ENTRY_ID_LIST.get();
              }
              else if(databaseContainer instanceof VLVIndex)
              {
                VLVIndex index = (VLVIndex)databaseContainer;
                SortKey[] sortKeys = index.sortOrder.getSortKeys();

                int pos = 0;
                byte[] keyBytes = key.getData();
                if(keyBytes.length > 0)
                {
                  StringBuilder builder = new StringBuilder();

                  // Decode the attribute values
                  for(SortKey sortKey : sortKeys)
                  {
                    int valueLength = keyBytes[pos] & 0x7F;
                    if (keyBytes[pos++] != valueLength)
                    {
                      int numLengthBytes = valueLength;
                      valueLength = 0;
                      for (int k=0; k < numLengthBytes; k++, pos++)
                      {
                        valueLength = (valueLength << 8) |
                            (keyBytes[pos] & 0xFF);
                      }
                    }

                    byte[] valueBytes = new byte[valueLength];
                    System.arraycopy(keyBytes, pos, valueBytes, 0, valueLength);
                    builder.append(sortKey.getAttributeType().getNameOrOID());
                    builder.append(": ");
                    if(valueBytes.length == 0)
                    {
                      builder.append("NULL");
                    }
                    else
                    {
                      builder.append(new String(valueBytes));
                    }
                    builder.append(" ");
                    pos += valueLength;
                  }

                  byte[] entryIDBytes = new byte[8];
                  System.arraycopy(keyBytes, pos, entryIDBytes, 0,
                                   entryIDBytes.length);
                  long entryID = JebFormat.entryIDFromDatabase(entryIDBytes);

                  formatedKey = lineSep + entryID + ": " + builder;
                }
                else
                {
                  formatedKey = "UNBOUNDED";
                }
                keyLabel = INFO_LABEL_DBTEST_VLV_INDEX_LAST_SORT_KEYS.get();

                try
                {
                  StringBuilder builder = new StringBuilder();
                  SortValuesSet svs = new SortValuesSet(key.getData(),
                                                      data.getData(),
                                                      index);
                  long[] entryIDs = svs.getEntryIDs();
                  for(int i = 0; i < entryIDs.length; i++)
                  {
                    builder.append(entryIDs[i]);
                    builder.append(": ");
                    for(int j = 0; j < sortKeys.length; j++)
                    {
                      SortKey sortKey = index.sortOrder.getSortKeys()[j];
                      ByteString value = svs.getValue(i * sortKeys.length + j);
                      builder.append(sortKey.getAttributeType().getNameOrOID());
                      builder.append(": ");
                      if(value == null)
                      {
                        builder.append("NULL");
                      }
                      else if(value.length() == 0)
                      {
                        builder.append("SIZE-EXCEEDED");
                      }
                      else
                      {
                        builder.append(value);
                      }
                      builder.append(" ");
                    }
                    builder.append(lineSep);
                  }
                  formatedData = lineSep + builder;
                  dataLabel = INFO_LABEL_DBTEST_INDEX_ENTRY_ID_LIST.get();
                }
                catch(Exception e)
                {
                  printMessage(ERR_DBTEST_DECODE_FAIL.get(getExceptionMessage(e)));
                }
              }
            }

            if(formatedKey == null)
            {
              StringBuilder keyBuilder = new StringBuilder();
              StaticUtils.byteArrayToHexPlusAscii(keyBuilder, key.getData(), indent);
              formatedKey = lineSep + keyBuilder;
            }
            if(formatedData == null)
            {
              StringBuilder dataBuilder = new StringBuilder();
              StaticUtils.byteArrayToHexPlusAscii(dataBuilder, data.getData(), indent);
              formatedData = lineSep + dataBuilder;
            }

            out.format("%s (%d bytes): %s%n", keyLabel,
                       key.getData().length, formatedKey);
            out.format("%s (%d bytes): %s%n%n", dataLabel,
                       data.getData().length, formatedData);
          }
          status = cursor.getNext(key, data, lockMode);
          count++;
          totalKeySize += key.getData().length;
          totalDataSize += data.getData().length;
        }
      }
      finally
      {
        cursor.close();
      }
      out.format("%nTotal Records: %d%n", count);
      if(count > 0)
      {
        out.format("Total / Average Key Size: %d bytes / %d bytes%n",
                   totalKeySize, totalKeySize / count);
        out.format("Total / Average Data Size: %d bytes / %d bytes%n",
                   totalDataSize, totalDataSize / count);
      }
      return 0;
    }
    catch(DatabaseException de)
    {
      printMessage(ERR_DBTEST_ERROR_READING_DATABASE.get(
          StaticUtils.stackTraceToSingleLineString(de)));
      return 1;
    }
    finally
    {
      close(rc);
      releaseSharedLock(backend);
    }
  }

  private byte[] parseKeyValue(String value, DatabaseContainer databaseContainer)
      throws ParseException, DirectoryException
  {
    if(value.startsWith("0x"))
    {
      return hexStringToByteArray(value.substring(2));
    }
    else if(databaseContainer instanceof DN2ID
        || databaseContainer instanceof DN2URI)
    {
      // Encode the value as a DN
      return DN.valueOf(value).toNormalizedByteString().toByteArray();
    }
    else if(databaseContainer instanceof ID2Entry)
    {
      // Encode the value as an entryID
      return JebFormat.entryIDToDatabase(
          Long.parseLong(value));
    }
    else if(databaseContainer instanceof VLVIndex)
    {
      // Encode the value as a size/value pair
      byte[] vBytes = StaticUtils.getBytes(value);
      ByteStringBuilder builder = new ByteStringBuilder();
      builder.appendBERLength(vBytes.length);
      builder.append(vBytes);
      return builder.toByteArray();
    }
    else
    {
      return StaticUtils.getBytes(value);
    }
  }

  private Comparator<byte[]> getComparator(DatabaseContainer databaseContainer)
  {
    if(databaseContainer instanceof Index)
    {
      return ((Index) databaseContainer).getComparator();
    }
    else if(databaseContainer instanceof VLVIndex)
    {
      return ((VLVIndex)databaseContainer).comparator;
    }
    else
    { // default comparator
      return ByteSequence.BYTE_ARRAY_COMPARATOR;
    }
  }

  private Map<LocalDBBackendCfg, BackendImpl> getJEBackends()
  {
    ArrayList<Backend> backendList = new ArrayList<Backend>();
    ArrayList<BackendCfg> entryList = new ArrayList<BackendCfg>();
    ArrayList<List<DN>> dnList = new ArrayList<List<DN>>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);

    LinkedHashMap<LocalDBBackendCfg, BackendImpl> jeBackends =
        new LinkedHashMap<LocalDBBackendCfg, BackendImpl>();
    for(int i = 0; i < backendList.size(); i++)
    {
      Backend<?> backend = backendList.get(i);
      if(backend instanceof BackendImpl)
      {
        jeBackends.put((LocalDBBackendCfg)entryList.get(i),
                       (BackendImpl)backend);
      }
    }
    return jeBackends;
  }

  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  public final void printMessage(LocalizableMessage msg) {
    err.println(wrapText(msg.toString(), MAX_LINE_WIDTH));
  }
}
