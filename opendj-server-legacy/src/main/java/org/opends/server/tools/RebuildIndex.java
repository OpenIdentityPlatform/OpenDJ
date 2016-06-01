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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.RebuildConfig.RebuildMode;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tasks.RebuildTask;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.cli.LDAPConnectionArgumentParser;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This program provides a utility to rebuild the contents of the indexes of a
 * Directory Server backend. This will be a process that is intended to run
 * separate from Directory Server and not internally within the server process
 * (e.g., via the tasks interface).
 */
public class RebuildIndex extends TaskTool
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private StringArgument configFile;
  private StringArgument baseDNString;
  private StringArgument indexList;
  private StringArgument tmpDirectory;
  private BooleanArgument rebuildAll;
  private BooleanArgument rebuildDegraded;
  private BooleanArgument clearDegradedState;

  private final LDAPConnectionArgumentParser argParser = createArgParser(
      "org.opends.server.tools.RebuildIndex",
      INFO_REBUILDINDEX_TOOL_DESCRIPTION.get());

  private RebuildConfig rebuildConfig = new RebuildConfig();
  private Backend<?> currentBackend;

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
    JDKLogging.enableConsoleLoggingForOpenDJTool();

    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      initializeArguments(false);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return 1;
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    if (indexList.getValues().isEmpty()
        && !rebuildAll.isPresent()
        && !rebuildDegraded.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_REBUILDINDEX_REQUIRES_AT_LEAST_ONE_INDEX.get());
      return 1;
    }

    if (rebuildAll.isPresent() && indexList.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_REBUILDINDEX_REBUILD_ALL_ERROR.get());
      return 1;
    }

    if (rebuildDegraded.isPresent() && indexList.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_REBUILDINDEX_REBUILD_DEGRADED_ERROR.get("index"));
      return 1;
    }

    if (rebuildDegraded.isPresent() && clearDegradedState.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_REBUILDINDEX_REBUILD_DEGRADED_ERROR.get("clearDegradedState"));
      return 1;
    }

    if (rebuildAll.isPresent() && rebuildDegraded.isPresent())
    {
      argParser.displayMessageAndUsageReference(err,
          ERR_REBUILDINDEX_REBUILD_ALL_DEGRADED_ERROR.get("rebuildDegraded"));
      return 1;
    }

    if (rebuildAll.isPresent() && clearDegradedState.isPresent())
    {
      argParser.displayMessageAndUsageReference(err,
          ERR_REBUILDINDEX_REBUILD_ALL_DEGRADED_ERROR.get("clearDegradedState"));
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
    argParser.setShortToolDescription(REF_SHORT_DESC_REBUILD_INDEX.get());

    configFile =
            StringArgument.builder("configFile")
                    .shortIdentifier('f')
                    .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                    .hidden()
                    .required()
                    .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                    .buildAndAddToParser(argParser);

    final StringArgument.Builder builder =
            StringArgument.builder("baseDN")
                    .shortIdentifier('b')
                    .description(INFO_REBUILDINDEX_DESCRIPTION_BASE_DN.get())
                    .required()
                    .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get());
    if (isMultipleBackends) {
      builder.multiValued();
    }
    baseDNString = builder.buildAndAddToParser(argParser);

    indexList =
            StringArgument.builder("index")
                    .shortIdentifier('i')
                    .description(INFO_REBUILDINDEX_DESCRIPTION_INDEX_NAME.get())
                    .multiValued()
                    .valuePlaceholder(INFO_INDEX_PLACEHOLDER.get())
                    .buildAndAddToParser(argParser);
    rebuildAll =
            BooleanArgument.builder("rebuildAll")
                    .description(INFO_REBUILDINDEX_DESCRIPTION_REBUILD_ALL.get())
                    .buildAndAddToParser(argParser);
    rebuildDegraded =
            BooleanArgument.builder("rebuildDegraded")
                    .description(INFO_REBUILDINDEX_DESCRIPTION_REBUILD_DEGRADED.get())
                    .buildAndAddToParser(argParser);
    clearDegradedState =
            BooleanArgument.builder("clearDegradedState")
                    .description(INFO_REBUILDINDEX_DESCRIPTION_CLEAR_DEGRADED_STATE.get())
                    .buildAndAddToParser(argParser);
    tmpDirectory =
            StringArgument.builder("tmpdirectory")
                    .description(INFO_REBUILDINDEX_DESCRIPTION_TEMP_DIRECTORY.get())
                    .defaultValue("import-tmp")
                    .valuePlaceholder(INFO_REBUILDINDEX_TEMP_DIR_PLACEHOLDER.get())
                    .buildAndAddToParser(argParser);

    final BooleanArgument displayUsage = showUsageArgument();
    argParser.addArgument(displayUsage);
    argParser.setUsageArgument(displayUsage);
  }

  @Override
  protected int processLocal(final boolean initializeServer, final PrintStream out, final PrintStream err)
  {
    if (initializeServer)
    {
      final int init = initializeServer(out, err);
      if (init != 0)
      {
        return init;
      }
    }

    if (!configureRebuildProcess(baseDNString.getValue()))
    {
      return 1;
    }

    return rebuildIndex(currentBackend, rebuildConfig);
  }

  @Override
  protected void cleanup()
  {
    DirectoryServer.shutdownBackends();
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
      rebuildBaseDN = DN.valueOf(dn);
    }
    catch (Exception e)
    {
      logger.error(ERR_CANNOT_DECODE_BASE_DN, dn,
              getExceptionMessage(e));
      return false;
    }

    // Retrieves the backend which holds the selected base DN.
    try
    {
      setCurrentBackend(retrieveBackend(rebuildBaseDN));
    }
    catch (Exception e)
    {
      logger.error(LocalizableMessage.raw(e.getMessage()));
      return false;
    }

    setRebuildConfig(initializeRebuildIndexConfiguration(rebuildBaseDN));
    return true;
  }

  /**
   * Initializes the directory server.
   *
   * @param out stream to write messages; may be null
   * @param err
   *          The output stream to use for standard error, or {@code null} if
   *          standard error is not needed.
   * @return The result code.
   */
  private int initializeServer(final PrintStream out, final PrintStream err)
  {
    try
    {
      new DirectoryServer.InitializationBuilder(configFile.getValue())
          .requireCryptoServices()
          .requireErrorAndDebugLogPublisher(out, err)
          .initialize();
      return 0;
    }
    catch (InitializationException ie)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(ie.getLocalizedMessage()));
      return 1;
    }
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
  private int rebuildIndex(final Backend<?> backend, final RebuildConfig rebuildConfig)
  {
    // Acquire an exclusive lock for the backend.
    //TODO: Find a way to do this with the server online.
    try
    {
      final String lockFile = LockFileManager.getBackendLockFileName(backend);
      final StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        logger.error(ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND, backend.getBackendID(), failureReason);
        return 1;
      }
    }
    catch (Exception e)
    {
      logger.error(ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND, backend
              .getBackendID(), getExceptionMessage(e));
      return 1;
    }

    int returnCode = 0;
    try
    {
      backend.rebuildBackend(rebuildConfig, DirectoryServer.getInstance().getServerContext());
    }
    catch (InitializationException e)
    {
      logger.error(ERR_REBUILDINDEX_ERROR_DURING_REBUILD, e.getMessage());
      returnCode = 1;
    }
    catch (Exception e)
    {
      logger.error(ERR_REBUILDINDEX_ERROR_DURING_REBUILD, getExceptionMessage(e));
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
          logger.warn(WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), failureReason);
        }
      }
      catch (Exception e)
      {
        logger.error(WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
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
  private Backend<?> retrieveBackend(final DN selectedDN) throws ConfigException, Exception
  {
    final List<Backend<?>> backendList = new ArrayList<>();
    final List<BackendCfg> entryList = new ArrayList<>();
    final List<List<DN>> dnList = new ArrayList<>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);

    Backend<?> backend = null;
    final int numBackends = backendList.size();
    for (int i = 0; i < numBackends; i++)
    {
      final Backend<?> b = backendList.get(i);
      final List<DN> baseDNs = dnList.get(i);
      if (baseDNs.contains(selectedDN))
      {
        if (backend != null)
        {
          throw new ConfigException(ERR_MULTIPLE_BACKENDS_FOR_BASE.get(baseDNString.getValue()));
        }
        backend = b;
      }
    }

    if (backend == null)
    {
      throw new ConfigException(ERR_NO_BACKENDS_FOR_BASE.get(baseDNString.getValue()));
    }
    if (!backend.supports(BackendOperation.INDEXING))
    {
      throw new ConfigException(ERR_BACKEND_NO_INDEXING_SUPPORT.get());
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
      final boolean initializeServer, final PrintStream out, final Collection<String> args)
  {
    try
    {
      try
      {
        initializeArguments(true);
      }
      catch (ArgumentException ae)
      {
        printWrappedText(out, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
        return 1;
      }

      try
      {
        argParser.parseArguments(args.toArray(new String[args.size()]));
      }
      catch (ArgumentException ae)
      {
        argParser.displayMessageAndUsageReference(out, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
        return 1;
      }

      if (initializeServer)
      {
        final int init = initializeServer(out, out);
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

  /** {@inheritDoc} */
  @Override
  public String getTaskId()
  {
    // NYI.
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    // Required attributes
    addLdapAttribute(attributes, ATTR_REBUILD_BASE_DN, baseDNString.getValue());

    attributes.add(new LDAPAttribute(ATTR_REBUILD_INDEX, indexList.getValues()));

    if (hasNonDefaultValue(tmpDirectory))
    {
      addLdapAttribute(attributes, ATTR_REBUILD_TMP_DIRECTORY, tmpDirectory.getValue());
    }

    if (hasNonDefaultValue(rebuildAll))
    {
      addLdapAttribute(attributes, ATTR_REBUILD_INDEX, REBUILD_ALL);
    }

    if (hasNonDefaultValue(rebuildDegraded))
    {
      addLdapAttribute(attributes, ATTR_REBUILD_INDEX, REBUILD_DEGRADED);
    }

    if (hasNonDefaultValue(clearDegradedState))
    {
      addLdapAttribute(attributes, ATTR_REBUILD_INDEX_CLEARDEGRADEDSTATE, "true");
    }
  }

  private void addLdapAttribute(List<RawAttribute> attributes, String attrType, String attrValue)
  {
    attributes.add(new LDAPAttribute(attrType, attrValue));
  }

  private boolean hasNonDefaultValue(BooleanArgument arg)
  {
    return arg.getValue() != null
        && !arg.getValue().equals(arg.getDefaultValue());
  }

  private boolean hasNonDefaultValue(StringArgument arg)
  {
    return arg.getValue() != null
        && !arg.getValue().equals(arg.getDefaultValue());
  }

  /** {@inheritDoc} */
  @Override
  public String getTaskObjectclass()
  {
    return "ds-task-rebuild";
  }

  /** {@inheritDoc} */
  @Override
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
  public Backend<?> getCurrentBackend()
  {
    return currentBackend;
  }

  /**
   * Sets the current backend.
   *
   * @param currentBackend
   *          The current backend to set.
   */
  public void setCurrentBackend(Backend<?> currentBackend)
  {
    this.currentBackend = currentBackend;
  }
}
