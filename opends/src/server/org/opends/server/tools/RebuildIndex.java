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

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import org.opends.server.util.BuildVersion;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.LDAPConnectionArgumentParser;
import org.opends.server.util.args.StringArgument;
import org.opends.server.extensions.ConfigFileHandler;

import org.opends.server.config.ConfigException;

import org.opends.server.loggers.TextWriter;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.debug.TextDebugLogPublisher;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.protocols.ldap.LDAPAttribute;

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
 * This program provides a utility to rebuild the contents of the indexes of a
 * Directory Server backend. This will be a process that is intended to run
 * separate from Directory Server and not internally within the server process
 * (e.g., via the tasks interface).
 */
public class RebuildIndex extends TaskTool
{
  private StringArgument configClass = null;
  private StringArgument configFile = null;
  private StringArgument baseDNString = null;
  private StringArgument indexList = null;
  private StringArgument tmpDirectory = null;
  private BooleanArgument rebuildAll = null;
  private BooleanArgument rebuildDegraded = null;
  private BooleanArgument clearDegradedState = null;

  private final LDAPConnectionArgumentParser argParser = createArgParser(
      "org.opends.server.tools.RebuildIndex",
      INFO_REBUILDINDEX_TOOL_DESCRIPTION.get());

  private RebuildConfig rebuildConfig = new RebuildConfig();
  private Backend currentBackend = null;



  /**
   * Processes the command-line arguments and invokes the rebuild process.
   *
   * @param args
   *          The command-line arguments provided to this program.
   */
  public static void main(final String[] args)
  {
    final int retCode =
        mainRebuildIndex(args, true, System.out, System.err);

    if (retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Processes the command-line arguments and invokes the rebuild process.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @param initializeServer
   *          Indicates whether to initialize the server.
   * @param outStream
   *          The output stream to use for standard output, or {@code null} if
   *          standard output is not needed.
   * @param errStream
   *          The output stream to use for standard error, or {@code null} if
   *          standard error is not needed.
   * @return The error code.
   */
  public static int mainRebuildIndex(final String[] args,
      final boolean initializeServer, final OutputStream outStream,
      final OutputStream errStream)
  {
    final RebuildIndex tool = new RebuildIndex();
    return tool.process(args, initializeServer, outStream, errStream);
  }

  private int process(final String[] args, final boolean initializeServer,
      final OutputStream outStream, final OutputStream errStream )
  {
    final PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    final PrintStream err = NullOutputStream.wrapOrNullStream(errStream);

    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      initializeArguments(false);
    }
    catch (ArgumentException ae)
    {
      final Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

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
      final Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

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
    final int numArgs = args.length;
    if (numArgs == 0)
    {
      out.println(argParser.getUsage());
      return 1;
    }

    if (indexList.getValues().size() <= 0 && !rebuildAll.isPresent()
        && !rebuildDegraded.isPresent())
    {
      final Message message =
          ERR_REBUILDINDEX_REQUIRES_AT_LEAST_ONE_INDEX.get();

      err.println(wrapText(message, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if (rebuildAll.isPresent() && indexList.isPresent())
    {
      final Message msg = ERR_REBUILDINDEX_REBUILD_ALL_ERROR.get();
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if (rebuildDegraded.isPresent() && indexList.isPresent())
    {
      final Message msg = ERR_REBUILDINDEX_REBUILD_DEGRADED_ERROR.get("index");
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if (rebuildDegraded.isPresent() && clearDegradedState.isPresent())
    {
      final Message msg =
          ERR_REBUILDINDEX_REBUILD_DEGRADED_ERROR.get("clearDegradedState");
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if (rebuildAll.isPresent() && rebuildDegraded.isPresent())
    {
      final Message msg =
          ERR_REBUILDINDEX_REBUILD_ALL_DEGRADED_ERROR.get("rebuildDegraded");
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

    if (rebuildAll.isPresent() && clearDegradedState.isPresent())
    {
      final Message msg =
          ERR_REBUILDINDEX_REBUILD_ALL_DEGRADED_ERROR.get("clearDegradedState");
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
   * Initializes the arguments for the rebuild index tool.
   *
   * @param isMultipleBackends
   *          {@code true} if the tool is used as internal.
   * @throws ArgumentException
   *           If the initialization fails.
   */
  private void initializeArguments(final boolean isMultipleBackends)
      throws ArgumentException
  {
    configClass =
        new StringArgument("configclass", 'C', "configClass", true, false,
            true, INFO_CONFIGCLASS_PLACEHOLDER.get(), ConfigFileHandler.class
                .getName(), null, INFO_DESCRIPTION_CONFIG_CLASS.get());
    configClass.setHidden(true);
    argParser.addArgument(configClass);

    configFile =
        new StringArgument("configfile", 'f', "configFile", true, false, true,
            INFO_CONFIGFILE_PLACEHOLDER.get(), null, null,
            INFO_DESCRIPTION_CONFIG_FILE.get());
    configFile.setHidden(true);
    argParser.addArgument(configFile);


    baseDNString =
        new StringArgument("basedn", 'b', "baseDN", true, isMultipleBackends,
            true, INFO_BASEDN_PLACEHOLDER.get(), null, null,
            INFO_REBUILDINDEX_DESCRIPTION_BASE_DN.get());
    argParser.addArgument(baseDNString);


    indexList =
        new StringArgument("index", 'i', "index", false, true, true,
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
        new StringArgument("tmpdirectory", null, "tmpdirectory", false, false,
            true, INFO_REBUILDINDEX_TEMP_DIR_PLACEHOLDER.get(), "import-tmp",
            null, INFO_REBUILDINDEX_DESCRIPTION_TEMP_DIRECTORY.get());
    argParser.addArgument(tmpDirectory);

    BooleanArgument displayUsage =
        new BooleanArgument("help", 'H', "help", INFO_DESCRIPTION_USAGE.get());
    argParser.addArgument(displayUsage);
    argParser.setUsageArgument(displayUsage);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected int processLocal(final boolean initializeServer,
      final PrintStream out, final PrintStream err)
  {
    // Performs the initial bootstrap of the Directory Server and processes the
    // configuration.
    final DirectoryServer directoryServer = DirectoryServer.getInstance();
    if (initializeServer)
    {
      final int init = initializeServer(directoryServer, out, err);
      if (init != 0)
      {
        return init;
      }
      setErrorAndDebugLogPublisher(out, err);
    }

    if (!configureRebuildProcess(baseDNString.getValue()))
    {
      return 1;
    }

    return rebuildIndex(currentBackend, rebuildConfig);
  }

  /**
   * Configures the rebuild index process. i.e.: decodes the selected DN and
   * retrieves the backend which holds it. Finally, initializes and sets the
   * rebuild configuration.
   *
   * @param dn
   *          User selected base DN.
   * @return A boolean representing the result of the process.
   */
  private boolean configureRebuildProcess(final String dn) {
    // Decodes the base DN provided by the user.
    DN rebuildBaseDN = null;
    try
    {
      rebuildBaseDN = DN.decode(dn);
    }
    catch (Exception e)
    {
      final Message message =
          ERR_CANNOT_DECODE_BASE_DN.get(dn,
              getExceptionMessage(e));
      logError(message);
      return false;
    }

    // Retrieves the backend which holds the selected base DN.
    try
    {
      setCurrentBackend(retrieveBackend(rebuildBaseDN));
    }
    catch (Exception e)
    {
      logError(Message.raw(e.getMessage()));
      return false;
    }

    setRebuildConfig(initializeRebuildIndexConfiguration(rebuildBaseDN));
    return true;
  }

  /**
   * Defines the error and the debug log publisher used in this tool.
   *
   * @param out
   *          The output stream to use for standard output, or {@code null} if
   *          standard output is not needed.
   * @param err
   *          The output stream to use for standard error, or {@code null} if
   *          standard error is not needed.
   */
  private void setErrorAndDebugLogPublisher(final PrintStream out,
      final PrintStream err)
  {
    try
    {
      final ErrorLogPublisher<?> errorLogPublisher =
          TextErrorLogPublisher
              .getToolStartupTextErrorPublisher(new TextWriter.STREAM(out));
      final DebugLogPublisher<?> debugLogPublisher =
          TextDebugLogPublisher
              .getStartupTextDebugPublisher(new TextWriter.STREAM(out));
      ErrorLogger.addErrorLogPublisher(errorLogPublisher);
      DebugLogger.addDebugLogPublisher(debugLogPublisher);
    }
    catch (Exception e)
    {
      err.println("Error installing the custom error logger: "
          + stackTraceToSingleLineString(e));
    }
  }

  /**
   * Initializes the directory server.<br />
   * Processes to :
   * - bootstrapClient
   * - initializeJMX
   * - initializeConfiguration
   * - initializeSchema
   * - coreConfigManager.initializeCoreConfig()
   * - initializeCryptoManager
   *
   * @param directoryServer
   *          The current instance.
   * @param out
   *          The output stream to use for standard output, or {@code null} if
   *          standard output is not needed.
   * @param err
   *          The output stream to use for standard error, or {@code null} if
   *          standard error is not needed.
   * @return The result code.
   */
  private int initializeServer(final DirectoryServer directoryServer,
      final PrintStream out, final PrintStream err)
  {
    try
    {
      DirectoryServer.bootstrapClient();
      DirectoryServer.initializeJMX();
    }
    catch (Exception e)
    {
      final Message message =
          ERR_SERVER_BOOTSTRAP_ERROR.get(getExceptionMessage(e));
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      directoryServer.initializeConfiguration(configClass.getValue(),
          configFile.getValue());
    }
    catch (Exception ex)
    {
      Message message = null;
      if (ex instanceof InitializationException)
      {
        message = ERR_CANNOT_LOAD_CONFIG.get(ex.getMessage());
      }
      else
      {
        message = ERR_CANNOT_LOAD_CONFIG.get(getExceptionMessage(ex));
      }
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Initializes the Directory Server schema elements.
    try
    {
      directoryServer.initializeSchema();
    }
    catch (Exception e)
    {
      Message message = null;
      if (e instanceof ConfigException || e instanceof InitializationException)
      {
        message = ERR_CANNOT_LOAD_SCHEMA.get(e.getMessage());
      }
      else
      {
        message = ERR_CANNOT_LOAD_SCHEMA.get(getExceptionMessage(e));
      }
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Initializes the Directory Server core configuration.
    try
    {
      final CoreConfigManager coreConfigManager = new CoreConfigManager();
      coreConfigManager.initializeCoreConfig();
    }
    catch (Exception ex)
    {
      Message message = null;
      if (ex instanceof ConfigException
          || ex instanceof InitializationException)
      {
        message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(ex.getMessage());
      }
      else
      {
        message =
            ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(getExceptionMessage(ex));
      }
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Initializes the Directory Server crypto manager.
    try
    {
      directoryServer.initializeCryptoManager();
    }
    catch (Exception ex)
    {
      Message message = null;
      if (ex instanceof ConfigException
          || ex instanceof InitializationException)
      {
        message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(ex.getMessage());
      }
      else
      {
        message =
            ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(getExceptionMessage(ex));
      }
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    return 0;
  }

  /**
   * Initializes and sets the rebuild index configuration.
   *
   * @param rebuildBaseDN
   *          The selected base DN.
   * @return A rebuild configuration.
   */
  private RebuildConfig initializeRebuildIndexConfiguration(
      final DN rebuildBaseDN)
  {
    final RebuildConfig config = new RebuildConfig();
    config.setBaseDN(rebuildBaseDN);
    for (final String s : indexList.getValues())
    {
      config.addRebuildIndex(s);
    }

    if (rebuildAll.isPresent())
    {
      config.setRebuildMode(RebuildMode.ALL);
    }
    else if (rebuildDegraded.isPresent())
    {
      config.setRebuildMode(RebuildMode.DEGRADED);
    }
    else
    {
      if (clearDegradedState.isPresent())
      {
        config.isClearDegradedState(true);
      }
      config.setRebuildMode(RebuildMode.USER_DEFINED);
    }

    config.setTmpDirectory(tmpDirectory.getValue());
    return config;
  }

  /**
   * Launches the rebuild index process.
   *
   * @param backend
   *          The directory server backend.
   * @param rebuildConfig
   *          The configuration which is going to be used by the rebuild index
   *          process.
   * @return An integer representing the result of the process.
   */
  private int rebuildIndex(final Backend backend,
      final RebuildConfig rebuildConfig)
  {
    int returnCode = 0;

    // Acquire an exclusive lock for the backend.
    //TODO: Find a way to do this with the server online.
    try
    {
      final String lockFile = LockFileManager.getBackendLockFileName(backend);
      final StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        final Message message =
            ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND.get(backend
                .getBackendID(), String.valueOf(failureReason));
        logError(message);
        return 1;
      }
    }
    catch (Exception e)
    {
      final Message message =
          ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND.get(backend
              .getBackendID(), getExceptionMessage(e));
      logError(message);
      return 1;
    }

    try
    {
      final BackendImpl jebBackend = (BackendImpl) backend;
      jebBackend.rebuildBackend(rebuildConfig);
    }
    catch (InitializationException e)
    {
      logError(ERR_REBUILDINDEX_ERROR_DURING_REBUILD.get(e.getMessage()));
      returnCode = 1;
    }
    catch (Exception e)
    {
      logError(ERR_REBUILDINDEX_ERROR_DURING_REBUILD
          .get(getExceptionMessage(e)));
      returnCode = 1;
    }
    finally
    {
      // Release the shared lock on the backend.
      try
      {
        final String lockFile = LockFileManager.getBackendLockFileName(backend);
        final StringBuilder failureReason = new StringBuilder();
        if (!LockFileManager.releaseLock(lockFile, failureReason))
        {
          logError(WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND.get(backend
              .getBackendID(), String.valueOf(failureReason)));
        }
      }
      catch (Exception e)
      {
        logError(WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND.get(backend
            .getBackendID(), getExceptionMessage(e)));
      }
    }

    return returnCode;
  }

  /**
   * Gets information about the backends defined in the server. Iterates through
   * them, finding the one that holds the base DN.
   *
   * @param selectedDN
   *          The user selected DN.
   * @return The backend which holds the selected base DN.
   * @throws ConfigException
   *           If the backend is poorly configured.
   * @throws Exception
   *           If an exception occurred during the backend search.
   */
  private Backend retrieveBackend(final DN selectedDN) throws ConfigException,
      Exception
  {
    Backend backend = null;
    DN[] baseDNArray;

    final ArrayList<Backend> backendList = new ArrayList<Backend>();
    final ArrayList<BackendCfg> entryList = new ArrayList<BackendCfg>();
    final ArrayList<List<DN>> dnList = new ArrayList<List<DN>>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);

    final int numBackends = backendList.size();
    for (int i = 0; i < numBackends; i++)
    {
      final Backend b = backendList.get(i);
      final List<DN> baseDNs = dnList.get(i);

      for (final DN baseDN : baseDNs)
      {
        if (baseDN.equals(selectedDN))
        {
          if (backend == null)
          {
            backend = b;
            baseDNArray = new DN[baseDNs.size()];
            baseDNs.toArray(baseDNArray);
          }
          else
          {
            final Message message =
                ERR_MULTIPLE_BACKENDS_FOR_BASE.get(baseDNString.getValue());
            throw new ConfigException(message);
          }
          break;
        }
      }
    }

    if (backend == null)
    {
      final Message message =
          ERR_NO_BACKENDS_FOR_BASE.get(baseDNString.getValue());
      throw new ConfigException(message);
    }

    if (!(backend instanceof BackendImpl))
    {
      final Message message = ERR_BACKEND_NO_INDEXING_SUPPORT.get();
      throw new ConfigException(message);
    }
    return backend;
  }

  /**
   * This function allow internal use of the rebuild index tools. This function
   * rebuilds indexes shared by multiple backends.
   *
   * @param initializeServer
   *          Indicates whether to initialize the server.
   * @param out
   *          The print stream which is used to display errors/debug lines.
   *          Usually redirected into a logger if the tool is used as external.
   * @param args
   *          The arguments used to launch the rebuild index process.
   * @return An integer indicating the result of this action.
   */
  public int rebuildIndexesWithinMultipleBackends(
      final boolean initializeServer, final PrintStream out,
      final String... args)
  {
    try
    {
      setErrorAndDebugLogPublisher(out, out);

      try
      {
        initializeArguments(true);
      }
      catch (ArgumentException ae)
      {
        final Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
        out.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      try
      {
        argParser.parseArguments(args);
      }
      catch (ArgumentException ae)
      {
        final Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
        out.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      final DirectoryServer directoryServer = DirectoryServer.getInstance();
      if (initializeServer)
      {
        final int init = initializeServer(directoryServer, out, out);
        if (init != 0)
        {
          return init;
        }
      }

      for (final String dn : baseDNString.getValues())
      {
        if (!configureRebuildProcess(dn))
        {
          return 1;
        }

        final int result =
            rebuildIndex(getCurrentBackend(), getRebuildConfig());
        // If the rebuild index is going bad, process is stopped.
        if (result != 0)
        {
          out.println(String.format(
                  "An error occurs during the rebuild index process" +
                  " in %s, rebuild index(es) aborted.",
                  dn));
          return 1;
        }
      }
    }
    finally
    {
      StaticUtils.close(out);
    }
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  public String getTaskId()
  {
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

    final String baseDN = baseDNString.getValue();
    values = new ArrayList<ByteString>(1);
    values.add(ByteString.valueOf(baseDN));
    attributes.add(new LDAPAttribute(ATTR_REBUILD_BASE_DN, values));

    final List<String> indexes = indexList.getValues();
    values = new ArrayList<ByteString>(indexes.size());
    for (final String s : indexes)
    {
      values.add(ByteString.valueOf(s));
    }
    attributes.add(new LDAPAttribute(ATTR_REBUILD_INDEX, values));

    if (tmpDirectory.getValue() != null
        && !tmpDirectory.getValue().equals(tmpDirectory.getDefaultValue()))
    {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(tmpDirectory.getValue()));
      attributes.add(new LDAPAttribute(ATTR_REBUILD_TMP_DIRECTORY, values));
    }

    if (rebuildAll.getValue() != null
        && !rebuildAll.getValue().equals(rebuildAll.getDefaultValue()))
    {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(REBUILD_ALL));
      attributes.add(new LDAPAttribute(ATTR_REBUILD_INDEX, values));
    }

    if (rebuildDegraded.getValue() != null
        && !rebuildDegraded.getValue()
            .equals(rebuildDegraded.getDefaultValue()))
    {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(REBUILD_DEGRADED));
      attributes.add(new LDAPAttribute(ATTR_REBUILD_INDEX, values));
    }

    if (clearDegradedState.getValue() != null
        && !clearDegradedState.getValue().equals(
            clearDegradedState.getDefaultValue()))
    {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf("true"));
      attributes.add(new LDAPAttribute(ATTR_REBUILD_INDEX_CLEARDEGRADEDSTATE,
          values));
    }
  }

  /**
   * {@inheritDoc}
   */
  public String getTaskObjectclass()
  {
    return "ds-task-rebuild";
  }

  /**
   * {@inheritDoc}
   */
  public Class<?> getTaskClass()
  {
    return RebuildTask.class;
  }

  /**
   * Returns the rebuild configuration.
   *
   * @return The rebuild configuration.
   */
  public RebuildConfig getRebuildConfig()
  {
    return rebuildConfig;
  }

  /**
   * Sets the rebuild configuration.
   *
   * @param rebuildConfig
   *          The rebuild configuration to set.
   */
  public void setRebuildConfig(RebuildConfig rebuildConfig)
  {
    this.rebuildConfig = rebuildConfig;
  }

  /**
   * Returns the current backend.
   *
   * @return The current backend.
   */
  public Backend getCurrentBackend()
  {
    return currentBackend;
  }

  /**
   * Sets the current backend.
   *
   * @param currentBackend
   *          The current backend to set.
   */
  public void setCurrentBackend(Backend currentBackend)
  {
    this.currentBackend = currentBackend;
  }
}
