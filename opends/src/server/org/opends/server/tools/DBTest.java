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

import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.util.args.*;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;
import org.opends.server.types.*;
import static org.opends.messages.ToolMessages.*;
import org.opends.messages.Message;
import static org.opends.server.tools.ToolConstants.OPTION_SHORT_CONFIG_CLASS;
import static org.opends.server.tools.ToolConstants.OPTION_LONG_CONFIG_CLASS;
import static org.opends.server.tools.ToolConstants.OPTION_SHORT_HELP;
import static org.opends.server.tools.ToolConstants.OPTION_LONG_HELP;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.config.ConfigException;
import org.opends.server.api.Backend;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.backends.jeb.*;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Element;

import java.io.*;
import java.util.*;

import com.sleepycat.je.*;

/**
 * This program provides a utility that may be used to debug a JE backend. This
 * tool provides the ability list various containers in the backend as well as
 * dump the contents of database containers. This will be
 * a process that is intended to run separate from Directory Server and not
 * internally within the server process (e.g., via the tasks interface).
 */
public class DBTest
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The error stream which this application should use.
  private final PrintStream err;

  // The output stream which this application should use.
  private final PrintStream out;

  // Flag indicating whether or not the global arguments have
  // already been initialized.
  private boolean globalArgumentsInitialized = false;

  // The command-line argument parser.
  private final SubCommandArgumentParser parser;

  // The argument which should be used to request usage information.
  private BooleanArgument showUsageArgument;

  // The argument which should be used to specify the config class.
  private StringArgument configClass;

  // THe argument which should be used to specify the config file.
  private StringArgument configFile;

  // Flag indicating whether or not the sub-commands have
  // already been initialized.
  private boolean subCommandsInitialized = false;



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
    if (out != null) {
      this.out = new PrintStream(out);
    } else {
      this.out = NullOutputStream.printStream();
    }

    if (err != null) {
      this.err = new PrintStream(err);
    } else {
      this.err = NullOutputStream.printStream();
    }

    Message toolDescription = INFO_DESCRIPTION_DBTEST_TOOL.get();
    this.parser = new SubCommandArgumentParser(this.getClass().getName(),
                                               toolDescription, false);
  }

  // Displays the provided message followed by a help usage reference.
  private void displayMessageAndUsageReference(Message message) {
    printMessage(message);
    printMessage(Message.EMPTY);
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


      showUsageArgument =
          new BooleanArgument("help", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                              INFO_DESCRIPTION_USAGE.get());

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
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(e.getMessage());
      printMessage(message);
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try {
      parser.parseArguments(args);
    } catch (ArgumentException ae) {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      displayMessageAndUsageReference(message);
      return 1;
    }

    // If the usage/version argument was provided, then we don't need
    // to do anything else.
    if (parser.usageOrVersionDisplayed()) {
      return 0;
    }

    // Only initialize the server when run as a standalone
    // application.
    if (initializeServer) {
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
        Message message = ERR_SERVER_BOOTSTRAP_ERROR.get(
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
        Message message = ERR_CANNOT_LOAD_CONFIG.get(
            ie.getMessage());
        printMessage(message);
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_LOAD_CONFIG.get(
            getExceptionMessage(e));
        printMessage(message);
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(
            ce.getMessage());
        printMessage(message);
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(
            ie.getMessage());
        printMessage(message);
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(
            getExceptionMessage(e));
        printMessage(message);
        return 1;
      }
    }

    // Make sure that we have a sub-command.
    if (parser.getSubCommand() == null)
    {
      Message message = ERR_DBTEST_MISSING_SUBCOMMAND.get();
      displayMessageAndUsageReference(message);
      return 1;
    }

    // Retrieve the sub-command implementation and run it.
    SubCommand subCommand = parser.getSubCommand();
    try {
      if(subCommand.getName().equals("list-root-containers"))
      {
        return listRootContainers();
      }
      else if(subCommand.getName().equals("list-entry-containers"))
      {
        return listEntryContainers(subCommand.getArgument("backendid"));
      }
      else if(subCommand.getName().equals("list-database-containers"))
      {
        return listDatabaseContainers(subCommand.getArgument("backendid"),
                                      subCommand.getArgument("basedn"));
      }
      else if(subCommand.getName().equals("dump-database-container"))
      {
        return dumpDatabaseContainer(subCommand.getArgument("backendid"),
                                     subCommand.getArgument("basedn"),
                                     subCommand.getArgument("databasename"),
                                     subCommand.getArgument("skipdecode"),
                                     subCommand.getArgument("maxkeyvalue"),
                                     subCommand.getArgument("minkeyvalue"),
                                     subCommand.getArgument("maxdatasize"),
                                     subCommand.getArgument("mindatasize"));
      }
      else if(subCommand.getName().equals("list-index-status"))
      {
        return listIndexStatus(subCommand.getArgument("backendid"),
                                      subCommand.getArgument("basedn"));
      }
      {
        return 0;
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      printMessage(Message.raw(StaticUtils.stackTraceToString(e)));
      return 1;
    }
  }

  private int listRootContainers()
  {
    TreeMap<LocalDBBackendCfg, BackendImpl> jeBackends = getJEBackends();
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
    TreeMap<LocalDBBackendCfg, BackendImpl> jeBackends = getJEBackends();
    BackendImpl backend = null;

    for(BackendImpl b : jeBackends.values())
    {
      if(b.getBackendID().equalsIgnoreCase(backendID.getValue()))
      {
        backend = b;
        break;
      }
    }

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
        Message message = ERR_DBTEST_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        printMessage(message);
        return 1;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_DBTEST_CANNOT_LOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      printMessage(message);
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
        builder.appendCell(ec.getBaseDN().toNormalizedString());
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
      try
      {
        // Close the root container
        rc.close();
      }
      catch(DatabaseException de)
      {
        // Ignore.
      }

      // Release the shared lock on the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
        Message message = WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
          printMessage(message);
        }
      }
      catch (Exception e)
      {
      Message message = WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
        printMessage(message);
      }
    }
  }

  private int listDatabaseContainers(Argument backendID,
                                     Argument baseDN)
  {
    TreeMap<LocalDBBackendCfg, BackendImpl> jeBackends = getJEBackends();
    BackendImpl backend = null;
    DN base = null;

    for(BackendImpl b : jeBackends.values())
    {
      if(b.getBackendID().equalsIgnoreCase(backendID.getValue()))
      {
        backend = b;
        break;
      }
    }

    if(backend == null)
    {
      printMessage(ERR_DBTEST_NO_BACKENDS_FOR_ID.get(backendID.getValue()));
      return 1;
    }

    if(baseDN.isPresent())
    {
      try
      {
        base = DN.decode(baseDN.getValue());
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
        Message message = ERR_DBTEST_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        printMessage(message);
        return 1;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_DBTEST_CANNOT_LOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      printMessage(message);
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
              base.toNormalizedString(), backend.getBackendID()));
          return 1;
        }

        ArrayList<DatabaseContainer> databaseContainers =
            new ArrayList<DatabaseContainer>();
        ec.listDatabases(databaseContainers);
        for(DatabaseContainer dc : databaseContainers)
        {
          builder.startRow();
          builder.appendCell(dc.getName().replace(ec.getDatabasePrefix()+"_",
                                                  ""));
          builder.appendCell(dc.getClass().getSimpleName());
          builder.appendCell(dc.getName());
          builder.appendCell(dc.getRecordCount());
          count++;
        }
      }
      else
      {
        for(EntryContainer ec : rc.getEntryContainers())
        {
          builder.startRow();
          ArrayList<DatabaseContainer> databaseContainers =
              new ArrayList<DatabaseContainer>();
          ec.listDatabases(databaseContainers);
          builder.appendCell("Base DN: " +
              ec.getBaseDN().toNormalizedString());
          for(DatabaseContainer dc : databaseContainers)
          {
            builder.startRow();
            builder.appendCell(dc.getName().replace(
                ec.getDatabasePrefix()+"_",""));
            builder.appendCell(dc.getClass().getSimpleName());
            builder.appendCell(dc.getName());
            builder.appendCell(dc.getRecordCount());
            count++;
          }
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
      try
      {
        // Close the root container
        rc.close();
      }
      catch(DatabaseException de)
      {
        // Ignore.
      }

      // Release the shared lock on the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          Message message = WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(
              backend.getBackendID(), String.valueOf(failureReason));
          printMessage(message);
        }
      }
      catch (Exception e)
      {
        Message message = WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(
            backend.getBackendID(), getExceptionMessage(e));
        printMessage(message);
      }
    }
  }

  private int listIndexStatus(Argument backendID,
                              Argument baseDN)
  {
    TreeMap<LocalDBBackendCfg, BackendImpl> jeBackends = getJEBackends();
    BackendImpl backend = null;
    DN base = null;

    for(BackendImpl b : jeBackends.values())
    {
      if(b.getBackendID().equalsIgnoreCase(backendID.getValue()))
      {
        backend = b;
        break;
      }
    }

    if(backend == null)
    {
      printMessage(ERR_DBTEST_NO_BACKENDS_FOR_ID.get(backendID.getValue()));
      return 1;
    }

    if(baseDN.isPresent())
    {
      try
      {
        base = DN.decode(baseDN.getValue());
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
        Message message = ERR_DBTEST_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        printMessage(message);
        return 1;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_DBTEST_CANNOT_LOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      printMessage(message);
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

      EntryContainer ec = rc.getEntryContainer(base);

      if(ec == null)
      {
        printMessage(ERR_DBTEST_NO_ENTRY_CONTAINERS_FOR_BASE_DN.get(
            base.toNormalizedString(), backend.getBackendID()));
        return 1;
      }

      ArrayList<DatabaseContainer> databaseContainers =
          new ArrayList<DatabaseContainer>();
      ec.listDatabases(databaseContainers);
      for(DatabaseContainer dc : databaseContainers)
      {
        if(dc instanceof Index || dc instanceof VLVIndex)
        {
          builder.startRow();
          builder.appendCell(dc.getName().replace(ec.getDatabasePrefix()+"_",
                                                  ""));
          builder.appendCell(dc.getClass().getSimpleName());
          builder.appendCell(dc.getName());
          if(dc instanceof Index)
          {
            builder.appendCell(ec.getState().getIndexTrustState(null,
                                                                ((Index)dc)));
          }
          else if(dc instanceof VLVIndex)
          {
            builder.appendCell(ec.getState().getIndexTrustState(null,
                                                               ((VLVIndex)dc)));
          }
          count++;
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
      try
      {
        // Close the root container
        rc.close();
      }
      catch(DatabaseException de)
      {
        // Ignore.
      }

      // Release the shared lock on the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
        Message message = WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
          printMessage(message);
        }
      }
      catch (Exception e)
      {
      Message message = WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
        printMessage(message);
      }
    }
  }

  private int dumpDatabaseContainer(Argument backendID, Argument baseDN,
                                    Argument databaseName, Argument skipDecode,
                                    Argument maxKeyValue, Argument minKeyValue,
                                    Argument maxDataSize,
                                    Argument minDataSize)
  {
    TreeMap<LocalDBBackendCfg, BackendImpl> jeBackends = getJEBackends();
    BackendImpl backend = null;
    DN base = null;

    for(BackendImpl b : jeBackends.values())
    {
      if(b.getBackendID().equalsIgnoreCase(backendID.getValue()))
      {
        backend = b;
        break;
      }
    }

    if(backend == null)
    {
      printMessage(ERR_DBTEST_NO_BACKENDS_FOR_ID.get(backendID.getValue()));
      return 1;
    }

    try
    {
      base = DN.decode(baseDN.getValue());
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
        Message message = ERR_DBTEST_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        printMessage(message);
        return 1;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_DBTEST_CANNOT_LOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      printMessage(message);
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
        printMessage(ERR_DBTEST_NO_ENTRY_CONTAINERS_FOR_BASE_DN.get(
            base.toNormalizedString(), backend.getBackendID()));
        return 1;
      }

      DatabaseContainer databaseContainer = null;
      ArrayList<DatabaseContainer> databaseContainers =
          new ArrayList<DatabaseContainer>();
      ec.listDatabases(databaseContainers);
      for(DatabaseContainer dc : databaseContainers)
      {
        if(dc.getName().replace(ec.getDatabasePrefix()+"_","").
            equalsIgnoreCase(databaseName.getValue()))
        {
          databaseContainer = dc;
          break;
        }
      }

      if(databaseContainer == null)
      {
        printMessage(ERR_DBTEST_NO_DATABASE_CONTAINERS_FOR_NAME.get(
            databaseName.getValue(), base.toNormalizedString(),
            backend.getBackendID()));
        return 1;
      }

      int count = 0;
      long totalKeySize = 0;
      long totalDataSize = 0;
      int indent = 4;

      Cursor cursor =
          databaseContainer.openCursor(null, CursorConfig.DEFAULT);

      try
      {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        LockMode lockMode = LockMode.DEFAULT;
        OperationStatus status;
        Comparator<byte[]> defaultComparator =
            new AttributeIndex.KeyComparator();
        Comparator<byte[]> dnComparator =
            new EntryContainer.KeyReverseComparator();
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
            if(minKeyValue.getValue().startsWith("0x"))
            {
              start =
                  StaticUtils.hexStringToByteArray(minKeyValue.getValue().
                      substring(2));
            }
            else
            {
              if(databaseContainer instanceof DN2ID ||
                  databaseContainer instanceof DN2URI)
              {
                // Encode the value as a DN
                start = StaticUtils.getBytes(
                    DN.decode(minKeyValue.getValue()).toNormalizedString());
              }
              else if(databaseContainer instanceof ID2Entry)
              {
                // Encode the value as an entryID
                start = JebFormat.entryIDToDatabase(
                    Long.parseLong(minKeyValue.getValue()));
              }
              else if(databaseContainer instanceof VLVIndex)
              {
                // Encode the value as a size/value pair
                byte[] vBytes =
                    new ASN1OctetString(minKeyValue.getValue()).value();
                byte[] vLength = ASN1Element.encodeLength(vBytes.length);
                start = new byte[vBytes.length + vLength.length];
                System.arraycopy(vLength, 0, start, 0, vLength.length);
                System.arraycopy(vBytes, 0, start, vLength.length,
                                 vBytes.length);
              }
              else
              {
                start = new ASN1OctetString(minKeyValue.getValue()).value();
              }
            }
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
            if(maxKeyValue.getValue().startsWith("0x"))
            {
              end =
                  StaticUtils.hexStringToByteArray(maxKeyValue.getValue().
                      substring(2));
            }
            else
            {
              if(databaseContainer instanceof DN2ID ||
                  databaseContainer instanceof DN2URI)
              {
                // Encode the value as a DN
                end = StaticUtils.getBytes(
                    DN.decode(maxKeyValue.getValue()).toNormalizedString());
              }
              else if(databaseContainer instanceof ID2Entry)
              {
                // Encode the value as an entryID
                end = JebFormat.entryIDToDatabase(
                    Long.parseLong(maxKeyValue.getValue()));
              }
              else if(databaseContainer instanceof VLVIndex)
              {
                // Encode the value as a size/value pair
                byte[] vBytes =
                    new ASN1OctetString(maxKeyValue.getValue()).value();
                byte[] vLength = ASN1Element.encodeLength(vBytes.length);
                end = new byte[vBytes.length + vLength.length];
                System.arraycopy(vLength, 0, end, 0, vLength.length);
                System.arraycopy(vBytes, 0, end, vLength.length,
                                 vBytes.length);
              }
              else
              {
                end = new ASN1OctetString(maxKeyValue.getValue()).value();
              }
            }
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
          if(end != null)
          {
            if(databaseContainer instanceof DN2ID)
            {
              if(dnComparator.compare(key.getData(), end) > 0)
              {
                break;
              }
            }
            else if(databaseContainer instanceof DN2URI)
            {
              if(dnComparator.compare(key.getData(), end) > 0)
              {
                break;
              }
            }
            else if(databaseContainer instanceof Index)
            {
              if(((Index)databaseContainer).indexer.getComparator().
                  compare(key.getData(), end) > 0)
              {
                break;
              }
            }
            else if(databaseContainer instanceof VLVIndex)
            {
              if(((VLVIndex)databaseContainer).comparator.
                  compare(key.getData(), end) > 0)
              {
                break;
              }
            }
            else
            {
              if(defaultComparator.compare(key.getData(),
                                           end) > 0)
              {
                break;
              }
            }
          }

          Message keyLabel = INFO_LABEL_DBTEST_KEY.get();
          Message dataLabel = INFO_LABEL_DBTEST_DATA.get();

          String formatedKey = null;
          String formatedData = null;

          if(!skipDecode.isPresent())
          {
            if(databaseContainer instanceof DN2ID)
            {
              try
              {
                formatedKey = DN.decode(new ASN1OctetString(
                    key.getData())).toNormalizedString();
                keyLabel = INFO_LABEL_DBTEST_ENTRY_DN.get();
              }
              catch(Exception e)
              {
                Message message =
                    ERR_DBTEST_DECODE_FAIL.get(getExceptionMessage(e));
                printMessage(message);
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
                formatedData = System.getProperty("line.separator") +
                    JebFormat.entryFromDatabase(data.getData(),
                         ec.getRootContainer().getCompressedSchema()).
                              toLDIFString();
                dataLabel = INFO_LABEL_DBTEST_ENTRY.get();
              }
              catch(Exception e)
              {
                Message message =
                    ERR_DBTEST_DECODE_FAIL.get(getExceptionMessage(e));
                printMessage(message);
              }
            }
            else if(databaseContainer instanceof DN2URI)
            {
              try
              {
                formatedKey = DN.decode(new ASN1OctetString(
                    key.getData())).toNormalizedString();
                keyLabel = INFO_LABEL_DBTEST_ENTRY_DN.get();
              }
              catch(Exception e)
              {
                Message message =
                    ERR_DBTEST_DECODE_FAIL.get(getExceptionMessage(e));
                printMessage(message);
              }
              formatedData = new ASN1OctetString(key.getData()).stringValue();
              dataLabel = INFO_LABEL_DBTEST_URI.get();
            }
            else if(databaseContainer instanceof Index)
            {
              formatedKey = new ASN1OctetString(key.getData()).stringValue();
              keyLabel = INFO_LABEL_DBTEST_INDEX_VALUE.get();

              EntryIDSet idSet = new EntryIDSet(key.getData(),
                                                data.getData());
              if(idSet.isDefined())
              {
                int lineCount = 0;
                StringBuilder builder = new StringBuilder();

                Iterator<EntryID> i = idSet.iterator();
                while(i.hasNext())
                {
                  builder.append(i.next());
                  if(lineCount == 10)
                  {
                    builder.append(System.getProperty("line.separator"));
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
                  System.arraycopy(keyBytes, pos, valueBytes, 0,
                                   valueLength);
                  builder.append(sortKey.getAttributeType().getNameOrOID());
                  builder.append(": ");
                  if(valueBytes.length == 0)
                  {
                    builder.append("NULL");
                  }
                  else
                  {
                    builder.append(
                        new ASN1OctetString(valueBytes).stringValue());
                  }
                  builder.append(" ");
                  pos += valueLength;
                }

                byte[] entryIDBytes = new byte[8];
                System.arraycopy(keyBytes, pos, entryIDBytes, 0,
                                 entryIDBytes.length);
                long entryID = JebFormat.entryIDFromDatabase(entryIDBytes);

                formatedKey = System.getProperty("line.separator") +
                    String.valueOf(entryID) + ": " + builder.toString();
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
                  builder.append(String.valueOf(entryIDs[i]));
                  builder.append(": ");
                  for(int j = 0; j < sortKeys.length; j++)
                  {
                    SortKey sortKey = index.sortOrder.getSortKeys()[j];
                    byte[] value = svs.getValue(i * sortKeys.length + j);
                    builder.append(sortKey.getAttributeType().getNameOrOID());
                    builder.append(": ");
                    if(value == null)
                    {
                      builder.append("NULL");
                    }
                    else if(value.length == 0)
                    {
                      builder.append("SIZE-EXCEEDED");
                    }
                    else
                    {
                      builder.append(
                          new ASN1OctetString(value).stringValue());
                    }
                    builder.append(" ");
                  }
                  builder.append(System.getProperty("line.separator"));
                }
                formatedData = System.getProperty("line.separator") +
                    builder.toString();
                dataLabel = INFO_LABEL_DBTEST_INDEX_ENTRY_ID_LIST.get();
              }
              catch(Exception e)
              {
                Message message =
                    ERR_DBTEST_DECODE_FAIL.get(getExceptionMessage(e));
                printMessage(message);
              }
            }
          }

          if(formatedKey == null)
          {
            StringBuilder keyBuilder = new StringBuilder();
            StaticUtils.byteArrayToHexPlusAscii(keyBuilder, key.getData(),
                                                indent);
            formatedKey = System.getProperty("line.separator") +
                keyBuilder.toString();
          }
          if(formatedData == null)
          {
            StringBuilder dataBuilder = new StringBuilder();
            StaticUtils.byteArrayToHexPlusAscii(dataBuilder, data.getData(),
                                                indent);
            formatedData = System.getProperty("line.separator") +
                dataBuilder.toString();
          }

          out.format("%s (%d bytes): %s%n", keyLabel,
                     key.getData().length, formatedKey);
          out.format("%s (%d bytes): %s%n%n", dataLabel,
                     data.getData().length, formatedData);

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
      try
      {
        // Close the root container
        rc.close();
      }
      catch(DatabaseException de)
      {
        // Ignore.
      }

      // Release the shared lock on the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          Message message = WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(
              backend.getBackendID(), String.valueOf(failureReason));
          printMessage(message);
        }
      }
      catch (Exception e)
      {
        Message message = WARN_DBTEST_CANNOT_UNLOCK_BACKEND.get(
            backend.getBackendID(), getExceptionMessage(e));
        printMessage(message);
      }
    }
  }

  private TreeMap<LocalDBBackendCfg, BackendImpl> getJEBackends()
  {
    ArrayList<Backend> backendList = new ArrayList<Backend>();
    ArrayList<BackendCfg>  entryList   = new ArrayList<BackendCfg>();
    ArrayList<List<DN>> dnList = new ArrayList<List<DN>>();
    int code = BackendToolUtils.getBackends(backendList, entryList, dnList);
    // TODO: Throw error if return code is not 0

    TreeMap<LocalDBBackendCfg, BackendImpl> jeBackends =
        new TreeMap<LocalDBBackendCfg, BackendImpl>();
    for(int i = 0; i < backendList.size(); i++)
    {
      Backend backend = backendList.get(i);
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
  public final void printMessage(Message msg) {
    err.println(wrapText(msg.toString(), MAX_LINE_WIDTH));
  }
}
