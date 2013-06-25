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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.tools;
import org.opends.messages.Message;

import static org.opends.server.util.StaticUtils.wrapText;

import org.opends.server.util.BuildVersion;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.LDAPConnectionArgumentParser;
import org.opends.server.util.args.StringArgument;
import org.opends.server.extensions.ConfigFileHandler;


import static org.opends.messages.ToolMessages.*;
import org.opends.server.config.ConfigException;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.logError;

import org.opends.server.loggers.TextWriter;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.debug.TextDebugLogPublisher;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.protocols.ldap.LDAPAttribute;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.LockFileManager;
import org.opends.server.tasks.RebuildTask;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.*;
import org.opends.server.api.Backend;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.api.DebugLogPublisher;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.jeb.RebuildConfig;
import org.opends.server.backends.jeb.RebuildConfig.RebuildMode;
import org.opends.server.admin.std.server.BackendCfg;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;


/**
 * This program provides a utility to rebuild the contents of the indexes
 * of a Directory Server backend.  This will be a process that is
 * intended to run separate from Directory Server and not internally within the
 * server process (e.g., via the tasks interface).
 */
public class RebuildIndex extends TaskTool
{
  private StringArgument  configClass             = null;
  private StringArgument  configFile              = null;
  private StringArgument  baseDNString            = null;
  private StringArgument  indexList               = null;
  private StringArgument  tmpDirectory            = null;
  private BooleanArgument rebuildAll              = null;
  private BooleanArgument rebuildDegraded         = null;
  private BooleanArgument clearDegradedState      = null;

  /**
   * Processes the command-line arguments and invokes the rebuild process.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainRebuildIndex(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Processes the command-line arguments and invokes the rebuild process.
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
  public static int mainRebuildIndex(String[] args, boolean initializeServer,
                                     OutputStream outStream,
                                     OutputStream errStream)
  {
    RebuildIndex tool = new RebuildIndex();
    return tool.process(args, initializeServer, outStream, errStream);
  }

  private int process(String[] args, boolean initializeServer,
      OutputStream outStream, OutputStream errStream) {
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

    // Define the command-line arguments that may be used with this program.
    BooleanArgument displayUsage ;


    // Create the command-line argument parser for use with this program.
    Message toolDescription = INFO_REBUILDINDEX_TOOL_DESCRIPTION.get();
    LDAPConnectionArgumentParser argParser =
      createArgParser("org.opends.server.tools.RebuildIndex",
                            toolDescription);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument("configclass", 'C', "configClass", true, false,
                              true, INFO_CONFIGCLASS_PLACEHOLDER.get(),
                              ConfigFileHandler.class.getName(), null,
                              INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, INFO_CONFIGFILE_PLACEHOLDER.get(), null,
                              null,
                              INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      baseDNString =
           new StringArgument("basedn", 'b', "baseDN", true, false, true,
                              INFO_BASEDN_PLACEHOLDER.get(), null, null,
                              INFO_REBUILDINDEX_DESCRIPTION_BASE_DN.get());
      argParser.addArgument(baseDNString);


      indexList =
           new StringArgument("index", 'i', "index",
                              false, true, true,
                              INFO_INDEX_PLACEHOLDER.get(), null, null,
                              INFO_REBUILDINDEX_DESCRIPTION_INDEX_NAME.get());
      argParser.addArgument(indexList);


      rebuildAll =
           new BooleanArgument("rebuildAll", null, "rebuildAll",
                    INFO_REBUILDINDEX_DESCRIPTION_REBUILD_ALL.get());
      argParser.addArgument(rebuildAll);


      rebuildDegraded =
           new BooleanArgument("rebuildDegraded", null, "rebuildDegraded",
                    INFO_REBUILDINDEX_DESCRIPTION_REBUILD_DEGRADED.get());
      argParser.addArgument(rebuildDegraded);

      clearDegradedState =
          new BooleanArgument("clearDegradedState", null, "clearDegradedState",
                   INFO_REBUILDINDEX_DESCRIPTION_CLEAR_DEGRADED_STATE.get());
      argParser.addArgument(clearDegradedState);

      tmpDirectory =
           new StringArgument("tmpdirectory", null, "tmpdirectory", false,
                   false, true, INFO_REBUILDINDEX_TEMP_DIR_PLACEHOLDER.get(),
                   "import-tmp",
                    null, INFO_REBUILDINDEX_DESCRIPTION_TEMP_DIRECTORY.get());
      argParser.addArgument(tmpDirectory);

      displayUsage =
           new BooleanArgument("help", 'H', "help",
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

    // If no arguments were provided, then display usage information and exit.
    int numArgs = args.length;
    if (numArgs == 0)
    {
      out.println(argParser.getUsage());
      return 1;
    }

    if (indexList.getValues().size() <= 0 && !rebuildAll.isPresent()
        && !rebuildDegraded.isPresent())
    {
      Message message = ERR_REBUILDINDEX_REQUIRES_AT_LEAST_ONE_INDEX.get();

      err.println(wrapText(message, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if(rebuildAll.isPresent() && indexList.isPresent())
    {
      Message msg = ERR_REBUILDINDEX_REBUILD_ALL_ERROR.get();
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if(rebuildDegraded.isPresent() && indexList.isPresent())
    {
      Message msg = ERR_REBUILDINDEX_REBUILD_DEGRADED_ERROR.get(
          "index");
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if(rebuildDegraded.isPresent() && clearDegradedState.isPresent())
    {
      Message msg = ERR_REBUILDINDEX_REBUILD_DEGRADED_ERROR.get(
          "clearDegradedState");
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if(rebuildAll.isPresent() && rebuildDegraded.isPresent())
    {
      Message msg = ERR_REBUILDINDEX_REBUILD_ALL_DEGRADED_ERROR.get(
          "rebuildDegraded");
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if(rebuildAll.isPresent() && clearDegradedState.isPresent())
    {
      Message msg = ERR_REBUILDINDEX_REBUILD_ALL_DEGRADED_ERROR.get(
          "clearDegradedState");
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    return process(argParser, initializeServer, out, err);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  protected int processLocal(boolean initializeServer,
                           PrintStream out,
                           PrintStream err) {
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
        Message message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(
                ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(
                ie.getMessage());
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



      try
      {
        ErrorLogPublisher<?> errorLogPublisher =
            TextErrorLogPublisher.getToolStartupTextErrorPublisher(
            new TextWriter.STREAM(out));
        DebugLogPublisher<?> debugLogPublisher =
            TextDebugLogPublisher.getStartupTextDebugPublisher(
            new TextWriter.STREAM(out));
        ErrorLogger.addErrorLogPublisher(errorLogPublisher);
        DebugLogger.addDebugLogPublisher(debugLogPublisher);
      }
      catch(Exception e)
      {
        err.println("Error installing the custom error logger: " +
                    stackTraceToSingleLineString(e));
      }
    }

    // Decode the base DN provided by the user.
    DN rebuildBaseDN;
    try
    {
      rebuildBaseDN = DN.decode(baseDNString.getValue());
    }
    catch (DirectoryException de)
    {
      Message message = ERR_CANNOT_DECODE_BASE_DN.get(
          baseDNString.getValue(), de.getMessageObject());
      logError(message);
      return 1;
    }
    catch (Exception e)
    {
      Message message = ERR_CANNOT_DECODE_BASE_DN.get(
          baseDNString.getValue(), getExceptionMessage(e));
      logError(message);
      return 1;
    }

    // Get information about the backends defined in the server.
    Backend backend = null;
    DN[]          baseDNArray;

    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<BackendCfg>  entryList   = new ArrayList<BackendCfg>();
    ArrayList<List<DN>> dnList = new ArrayList<List<DN>>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);

    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend     b       = backendList.get(i);
      List<DN>    baseDNs = dnList.get(i);

      for (DN baseDN : baseDNs)
      {
        if (baseDN.equals(rebuildBaseDN))
        {
          if (backend == null)
          {
            backend         = b;
            baseDNArray     = new DN[baseDNs.size()];
            baseDNs.toArray(baseDNArray);
          }
          else
          {
            Message message =
                ERR_MULTIPLE_BACKENDS_FOR_BASE.get(baseDNString.getValue());
            logError(message);
            return 1;
          }
          break;
        }
      }
    }

    if (backend == null)
    {
      Message message = ERR_NO_BACKENDS_FOR_BASE.get(baseDNString.getValue());
      logError(message);
      return 1;
    }

    if (!(backend instanceof BackendImpl))
    {
      Message message = ERR_BACKEND_NO_INDEXING_SUPPORT.get();
      logError(message);
      return 1;
    }

    // Initialize the rebuild configuration.
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(rebuildBaseDN);
    for (String s : indexList.getValues())
    {
      rebuildConfig.addRebuildIndex(s);
    }

    // Acquire an exclusive lock for the backend.
    //TODO: Find a way to do this with the server online.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        Message message = ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        logError(message);
        return 1;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      logError(message);
      return 1;
    }

    if (rebuildAll.isPresent())
    {
      rebuildConfig.setRebuildMode(RebuildMode.ALL);
    }
    else if (rebuildDegraded.isPresent())
    {
      rebuildConfig.setRebuildMode(RebuildMode.DEGRADED);
    }
    else
    {
      if(clearDegradedState.isPresent()) {
        rebuildConfig.isClearDegradedState(true);
      }
      rebuildConfig.setRebuildMode(RebuildMode.USER_DEFINED);
    }

    rebuildConfig.setTmpDirectory(tmpDirectory.getValue());

    // Launch the rebuild process.
    int returnCode = 0;
    try
    {
      BackendImpl jebBackend = (BackendImpl)backend;
      jebBackend.rebuildBackend(rebuildConfig);
    }
    catch (InitializationException e)
    {
      Message message =
          ERR_REBUILDINDEX_ERROR_DURING_REBUILD.get(e.getMessage());
      logError(message);
      returnCode = 1;
    }
    catch (Exception e)
    {
      Message message =
          ERR_REBUILDINDEX_ERROR_DURING_REBUILD.get(getExceptionMessage(e));
      logError(message);
      returnCode = 1;
    }
    finally
    {
      // Release the shared lock on the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          Message message = WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND.get(
              backend.getBackendID(), String.valueOf(failureReason));
          logError(message);
        }
      }
      catch (Exception e)
      {
        Message message = WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND.get(
            backend.getBackendID(), getExceptionMessage(e));
        logError(message);
      }
    }

    return returnCode;
  }

  /**
   * {@inheritDoc}
   */
  public String getTaskId() {
    // NYI.
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    //
    // Required attributes
    //
    ArrayList<ByteString> values;

    String baseDN = baseDNString.getValue();
    values = new ArrayList<ByteString>(1);
    values.add(ByteString.valueOf(baseDN));
    attributes.add(new LDAPAttribute(ATTR_REBUILD_BASE_DN, values));

    List<String> indexes = indexList.getValues();
    values = new ArrayList<ByteString>(indexes.size());
    for (String s : indexes)
    {
      values.add(ByteString.valueOf(s));
    }
    attributes.add(new LDAPAttribute(ATTR_REBUILD_INDEX, values));


    if (tmpDirectory.getValue() != null &&
            !tmpDirectory.getValue().equals(
                    tmpDirectory.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(tmpDirectory.getValue()));
      attributes.add(new LDAPAttribute(ATTR_REBUILD_TMP_DIRECTORY, values));
    }


    if (rebuildAll.getValue() != null &&
            !rebuildAll.getValue().equals(
                    rebuildAll.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(REBUILD_ALL));
      attributes.add(
              new LDAPAttribute(ATTR_REBUILD_INDEX, values));
    }


    if (rebuildDegraded.getValue() != null &&
            !rebuildDegraded.getValue().equals(
                rebuildDegraded.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(REBUILD_DEGRADED));
      attributes.add(
              new LDAPAttribute(ATTR_REBUILD_INDEX, values));
    }

    if (clearDegradedState.getValue() != null &&
        !clearDegradedState.getValue().equals(
            clearDegradedState.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf("true"));
      attributes.add(
            new LDAPAttribute(ATTR_REBUILD_INDEX_CLEARDEGRADEDSTATE, values));
    }
  }

  /**
   * {@inheritDoc}
   */
  public String getTaskObjectclass() {
    return "ds-task-rebuild";
  }

  /**
   * {@inheritDoc}
   */
  public Class<?> getTaskClass() {
    return RebuildTask.class;
  }
}
