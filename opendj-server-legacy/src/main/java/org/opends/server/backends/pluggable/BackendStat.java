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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.config.SizeUnit;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.forgerock.opendj.server.config.server.PluggableBackendCfg;
import org.forgerock.util.Option;
import org.forgerock.util.Options;
import org.opends.server.api.Backend;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.core.LockFileManager;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.tools.BackendToolUtils;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TextTablePrinter;

/**
 * This program provides a utility that may be used to debug a Pluggable Backend.
 * This tool provides the ability to:
 * <ul>
 * <li>list root containers</li>
 * <li>list entry containers</li>
 * <li>list Trees in a Backend or Storage</li>
 * <li>gather information about Backend indexes</li>
 * <li>dump the contents of a Tree either at the Backend or the Storage layer.</li>
 * </ul>
 * This will be a process that is intended to run outside of Directory Server and not
 * internally within the server process (e.g., via the tasks interface); it still
 * requires configuration information and access to Directory Server instance data.
 */
public class BackendStat
{
  /**
   * Collects all necessary interaction interfaces with either a Backend using TreeNames
   * or a storage using Trees.
   */
  private interface TreeKeyValue
  {
    /**
     * Returns a key given a string representation of it.
     *
     * @param data a string representation of the key.
     *             Prefixing with "0x" will interpret the rest of the string as an hex dump
     *             of the intended value.
     * @return a key given a string representation of it
     */
    ByteString getTreeKey(String data);

    /**
     * Returns a printable string for the given key.
     *
     * @param key a key from the Tree
     * @return a printable string for the given key
     */
    String keyDecoder(ByteString key);

    /**
     * Returns a printable string for the given value.
     *
     * @param value a value from the tree
     * @return a printable string for the given value
     */
    String valueDecoder(ByteString value);

    /**
     * Returns the TreeName for this storage Tree.
     *
     * @return the TreeName for this storage Tree
     */
    TreeName getTreeName();
  }

  /** Stays at the storage level when cursoring Trees. */
  private static class StorageTreeKeyValue implements TreeKeyValue
  {
    private final TreeName treeName;

    private StorageTreeKeyValue(TreeName treeName)
    {
      this.treeName = treeName;
    }

    @Override
    public TreeName getTreeName()
    {
      return treeName;
    }

    @Override
    public ByteString getTreeKey(String data)
    {
      return ByteString.valueOfUtf8(data);
    }

    @Override
    public String keyDecoder(ByteString key)
    {
      throw new UnsupportedOperationException(ERR_BACKEND_TOOL_DECODER_NOT_AVAILABLE.get().toString());
    }

    @Override
    public String valueDecoder(ByteString value)
    {
      throw new UnsupportedOperationException(ERR_BACKEND_TOOL_DECODER_NOT_AVAILABLE.get().toString());
    }
  }

  /** Delegate key semantics to the backend. */
  private static class BackendTreeKeyValue implements TreeKeyValue
  {
    private final TreeName name;
    private final Tree tree;

    private BackendTreeKeyValue(Tree tree)
    {
      this.tree = tree;
      this.name = tree.getName();
    }

    @Override
    public ByteString getTreeKey(String data)
    {
      if (data.length() == 0)
      {
        return ByteString.empty();
      }
      return tree.generateKey(data);
    }

    @Override
    public String keyDecoder(ByteString key)
    {
      return tree.keyToString(key);
    }

    @Override
    public String valueDecoder(ByteString value)
    {
      return tree.valueToString(value);
    }

    @Override
    public TreeName getTreeName()
    {
      return name;
    }
  }

  /** Statistics collector. */
  private class TreeStats
  {
    private final long count;
    private final long totalKeySize;
    private final long totalDataSize;

    private TreeStats(long count, long tks, long tds)
    {
      this.count = count;
      this.totalKeySize = tks;
      this.totalDataSize = tds;
    }
  }

  private static final Option<Boolean> DUMP_DECODE_VALUE = Option.withDefault(true);
  private static final Option<Boolean> DUMP_STATS_ONLY = Option.withDefault(false);
  private static final Option<Boolean> DUMP_SINGLE_LINE = Option.withDefault(false);
  private static final Option<Argument> DUMP_MIN_KEY_VALUE = Option.of(Argument.class, null);
  private static final Option<Argument> DUMP_MAX_KEY_VALUE = Option.of(Argument.class, null);
  private static final Option<Boolean> DUMP_MIN_KEY_VALUE_IS_HEX = Option.withDefault(false);
  private static final Option<Boolean> DUMP_MAX_KEY_VALUE_IS_HEX = Option.withDefault(false);
  private static final Option<Integer> DUMP_MIN_DATA_SIZE = Option.of(Integer.class, 0);
  private static final Option<Integer> DUMP_MAX_DATA_SIZE = Option.of(Integer.class, Integer.MAX_VALUE);
  private static final Option<Integer> DUMP_INDENT = Option.of(Integer.class, 4);

  // Sub-command names.
  private static final String LIST_BACKENDS = "list-backends";
  private static final String LIST_BASE_DNS = "list-base-dns";
  private static final String LIST_INDEXES = "list-indexes";
  private static final String SHOW_INDEX_STATUS = "show-index-status";
  private static final String DUMP_INDEX = "dump-index";
  private static final String LIST_RAW_DBS = "list-raw-dbs";
  private static final String DUMP_RAW_DB = "dump-raw-db";

  private static final String BACKENDID_NAME = "backendid";
  private static final String BACKENDID = "backendID";
  private static final String BASEDN_NAME = "basedn";
  private static final String BASEDN = "baseDN";
  private static final String USESIUNITS_NAME = "usesiunits";
  private static final String USESIUNITS = "useSIUnits";
  private static final String MAXDATASIZE_NAME = "maxdatasize";
  private static final String MAXDATASIZE = "maxDataSize";
  private static final String MAXKEYVALUE_NAME = "maxkeyvalue";
  private static final String MAXKEYVALUE = "maxKeyValue";
  private static final String MAXHEXKEYVALUE_NAME = "maxhexkeyvalue";
  private static final String MAXHEXKEYVALUE = "maxHexKeyValue";
  private static final String MINDATASIZE_NAME = "mindatasize";
  private static final String MINDATASIZE = "minDataSize";
  private static final String MINKEYVALUE_NAME = "minkeyvalue";
  private static final String MINKEYVALUE = "minKeyValue";
  private static final String MINHEXKEYVALUE_NAME = "minhexkeyvalue";
  private static final String MINHEXKEYVALUE = "minHexKeyValue";
  private static final String SKIPDECODE_NAME = "skipdecode";
  private static final String SKIPDECODE = "skipDecode";
  private static final String STATSONLY_NAME = "statsonly";
  private static final String STATSONLY = "statsOnly";
  private static final String INDEXNAME_NAME = "indexname";
  private static final String INDEXNAME = "indexName";
  private static final String DBNAME_NAME = "dbname";
  private static final String DBNAME = "dbName";
  private static final String SINGLELINE_NAME = "singleline";
  private static final String SINGLELINE = "singleLine";

  private static final String HEXDUMP_LINE_FORMAT = "%s%s %s%n";

  /** The error stream which this application should use. */
  private final PrintStream err;
  /** The output stream which this application should use. */
  private final PrintStream out;

  /** The command-line argument parser. */
  private final SubCommandArgumentParser parser;
  /** The argument which should be used to request usage information. */
  private BooleanArgument showUsageArgument;
  /** The argument which should be used to specify the config file. */
  private StringArgument configFile;

  /** Flag indicating whether the sub-commands have already been initialized. */
  private boolean subCommandsInitialized;
  /** Flag indicating whether the global arguments have already been initialized. */
  private boolean globalArgumentsInitialized;

  /**
   * Provides the command-line arguments to the main application for
   * processing.
   *
   * @param args The set of command-line arguments provided to this
   *             program.
   */
  public static void main(String[] args)
  {
    int exitCode = main(args, System.out, System.err);
    if (exitCode != 0)
    {
      System.exit(filterExitCode(exitCode));
    }
  }

  /**
   * Provides the command-line arguments to the main application for
   * processing and returns the exit code as an integer.
   *
   * @param args      The set of command-line arguments provided to this
   *                  program.
   * @param outStream The output stream for standard output.
   * @param errStream The output stream for standard error.
   * @return Zero to indicate that the program completed successfully,
   * or non-zero to indicate that an error occurred.
   */
  public static int main(String[] args, OutputStream outStream, OutputStream errStream)
  {
    BackendStat app = new BackendStat(outStream, errStream);
    return app.run(args);
  }

  /**
   * Creates a new dsconfig application instance.
   *
   * @param out The application output stream.
   * @param err The application error stream.
   */
  public BackendStat(OutputStream out, OutputStream err)
  {
    this.out = NullOutputStream.wrapOrNullStream(out);
    this.err = NullOutputStream.wrapOrNullStream(err);
    JDKLogging.disableLogging();

    LocalizableMessage toolDescription = INFO_DESCRIPTION_BACKEND_TOOL.get();
    this.parser = new SubCommandArgumentParser(getClass().getName(), toolDescription, false);
    this.parser.setShortToolDescription(REF_SHORT_DESC_BACKEND_TOOL.get());
    this.parser.setVersionHandler(new DirectoryServerVersionHandler());
  }

  /**
   * Registers the global arguments with the argument parser.
   *
   * @throws ArgumentException If a global argument could not be registered.
   */
  private void initializeGlobalArguments() throws ArgumentException
  {
    if (!globalArgumentsInitialized)
    {
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('f')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildArgument();

      showUsageArgument = showUsageArgument();

      // Register the global arguments.
      parser.addGlobalArgument(showUsageArgument);
      parser.setUsageArgument(showUsageArgument, out);
      parser.addGlobalArgument(configFile);

      globalArgumentsInitialized = true;
    }
  }

  /**
   * Registers the sub-commands with the argument parser.
   *
   * @throws ArgumentException If a sub-command could not be created.
   */
  private void initializeSubCommands() throws ArgumentException
  {
    if (!subCommandsInitialized)
    {
      // list-backends
      new SubCommand(parser, LIST_BACKENDS,
                           INFO_DESCRIPTION_BACKEND_TOOL_SUBCMD_LIST_BACKENDS.get());

      // list-base-dns
      addBackendArgument(new SubCommand(
              parser, LIST_BASE_DNS, INFO_DESCRIPTION_BACKEND_DEBUG_SUBCMD_LIST_ENTRY_CONTAINERS.get()));

      // list-indexes
      final SubCommand listIndexes = new SubCommand(
              parser, LIST_INDEXES, INFO_DESCRIPTION_BACKEND_TOOL_SUBCMD_LIST_INDEXES.get());
      addBackendBaseDNArguments(listIndexes, false, false);

      // show-index-status
      final SubCommand showIndexStatus = new SubCommand(
              parser, SHOW_INDEX_STATUS, INFO_DESCRIPTION_BACKEND_DEBUG_SUBCMD_LIST_INDEX_STATUS.get());
      showIndexStatus.setDocDescriptionSupplement(SUPPLEMENT_DESCRIPTION_BACKEND_TOOL_SUBCMD_LIST_INDEX_STATUS.get());
      addBackendBaseDNArguments(showIndexStatus, true, true);

      // dump-index
      final SubCommand dumpIndex = new SubCommand(
              parser, DUMP_INDEX, INFO_DESCRIPTION_BACKEND_TOOL_SUBCMD_DUMP_INDEX.get());
      addBackendBaseDNArguments(dumpIndex, true, false);
      dumpIndex.addArgument(StringArgument.builder(INDEXNAME)
              .shortIdentifier('i')
              .description(INFO_DESCRIPTION_BACKEND_DEBUG_INDEX_NAME.get())
              .required()
              .valuePlaceholder(INFO_INDEX_NAME_PLACEHOLDER.get())
              .buildArgument());
      addDumpSubCommandArguments(dumpIndex);
      dumpIndex.addArgument(BooleanArgument.builder(SKIPDECODE)
              .shortIdentifier('p')
              .description(INFO_DESCRIPTION_BACKEND_DEBUG_SKIP_DECODE.get())
              .buildArgument());

      // list-raw-dbs
      final SubCommand listRawDBs = new SubCommand(
              parser, LIST_RAW_DBS, INFO_DESCRIPTION_BACKEND_TOOL_SUBCMD_LIST_RAW_DBS.get());
      addBackendArgument(listRawDBs);
      listRawDBs.addArgument(BooleanArgument.builder(USESIUNITS)
              .shortIdentifier('u')
              .description(INFO_DESCRIPTION_BACKEND_TOOL_USE_SI_UNITS.get())
              .buildArgument());

      // dump-raw-db
      final SubCommand dumbRawDB = new SubCommand(
              parser, DUMP_RAW_DB, INFO_DESCRIPTION_BACKEND_TOOL_SUBCMD_DUMP_RAW_DB.get());
      addBackendArgument(dumbRawDB);
      dumbRawDB.addArgument(StringArgument.builder(DBNAME)
              .shortIdentifier('d')
              .description(INFO_DESCRIPTION_BACKEND_DEBUG_RAW_DB_NAME.get())
              .required()
              .valuePlaceholder(INFO_DATABASE_NAME_PLACEHOLDER.get())
              .buildArgument());
      addDumpSubCommandArguments(dumbRawDB);
      dumbRawDB.addArgument(BooleanArgument.builder(SINGLELINE)
              .shortIdentifier('l')
              .description(INFO_DESCRIPTION_BACKEND_TOOL_SUBCMD_SINGLE_LINE.get())
              .buildArgument());

      subCommandsInitialized = true;
    }
  }

  private void addBackendArgument(SubCommand sub) throws ArgumentException
  {
    sub.addArgument(
            StringArgument.builder(BACKENDID)
                    .shortIdentifier('n')
                    .description(INFO_DESCRIPTION_BACKEND_DEBUG_BACKEND_ID.get())
                    .required()
                    .valuePlaceholder(INFO_BACKENDNAME_PLACEHOLDER.get())
                    .buildArgument());
  }

  private void addBackendBaseDNArguments(SubCommand sub, boolean isRequired, boolean isMultiValued)
      throws ArgumentException
  {
    addBackendArgument(sub);
    final StringArgument.Builder builder = StringArgument.builder(BASEDN)
            .shortIdentifier('b')
            .description(INFO_DESCRIPTION_BACKEND_DEBUG_BASE_DN.get())
            .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get());
    if (isMultiValued)
    {
      builder.multiValued();
    }
    if (isRequired) {
      builder.required();
    }
    sub.addArgument(builder.buildArgument());
  }

  private void addDumpSubCommandArguments(SubCommand sub) throws ArgumentException
  {
    sub.addArgument(BooleanArgument.builder(STATSONLY)
            .shortIdentifier('q')
            .description(INFO_DESCRIPTION_BACKEND_DEBUG_STATS_ONLY.get())
            .buildArgument());

    sub.addArgument(newMaxKeyValueArg());
    sub.addArgument(newMinKeyValueArg());
    sub.addArgument(StringArgument.builder(MAXHEXKEYVALUE)
            .shortIdentifier('X')
            .description(INFO_DESCRIPTION_BACKEND_DEBUG_MAX_KEY_VALUE.get())
            .valuePlaceholder(INFO_MAX_KEY_VALUE_PLACEHOLDER.get())
            .buildArgument());

    sub.addArgument(StringArgument.builder(MINHEXKEYVALUE)
            .shortIdentifier('x')
            .description(INFO_DESCRIPTION_BACKEND_DEBUG_MIN_KEY_VALUE.get())
            .valuePlaceholder(INFO_MIN_KEY_VALUE_PLACEHOLDER.get())
            .buildArgument());

    sub.addArgument(IntegerArgument.builder(MAXDATASIZE)
            .shortIdentifier('S')
            .description(INFO_DESCRIPTION_BACKEND_DEBUG_MAX_DATA_SIZE.get())
            .defaultValue(-1)
            .valuePlaceholder(INFO_MAX_DATA_SIZE_PLACEHOLDER.get())
            .buildArgument());

    sub.addArgument(IntegerArgument.builder(MINDATASIZE)
            .shortIdentifier('s')
            .description(INFO_DESCRIPTION_BACKEND_DEBUG_MIN_DATA_SIZE.get())
            .defaultValue(-1)
            .valuePlaceholder(INFO_MIN_DATA_SIZE_PLACEHOLDER.get())
            .buildArgument());
  }

  private StringArgument newMinKeyValueArg() throws ArgumentException
  {
    return StringArgument.builder(MINKEYVALUE)
            .shortIdentifier('k')
            .description(INFO_DESCRIPTION_BACKEND_DEBUG_MIN_KEY_VALUE.get())
            .valuePlaceholder(INFO_MIN_KEY_VALUE_PLACEHOLDER.get())
            .buildArgument();
  }

  private StringArgument newMaxKeyValueArg() throws ArgumentException
  {
    return StringArgument.builder(MAXKEYVALUE)
            .shortIdentifier('K')
            .description(INFO_DESCRIPTION_BACKEND_DEBUG_MAX_KEY_VALUE.get())
            .valuePlaceholder(INFO_MAX_KEY_VALUE_PLACEHOLDER.get())
            .buildArgument();
  }

  /**
   * Parses the provided command-line arguments and makes the
   * appropriate changes to the Directory Server configuration.
   *
   * @param args The command-line arguments provided to this program.
   * @return The exit code from the configuration processing. A
   * nonzero value indicates that there was some kind of
   * problem during the configuration processing.
   */
  private int run(String[] args)
  {
    // Register global arguments and sub-commands.
    try
    {
      initializeGlobalArguments();
      initializeSubCommands();
    }
    catch (ArgumentException e)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(e.getMessage()));
      return 1;
    }

    try
    {
      parser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      parser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return 1;
    }

    if (parser.usageOrVersionDisplayed())
    {
      return 0;
    }

    if (parser.getSubCommand() == null)
    {
      parser.displayMessageAndUsageReference(err, ERR_BACKEND_DEBUG_MISSING_SUBCOMMAND.get());
      return 1;
    }

    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      printWrappedText(err, e.getMessageObject());
      return 1;
    }

    // Perform the initial bootstrap of the Directory Server and process the configuration.
    SubCommand subCommand = parser.getSubCommand();
    final String subCommandName = subCommand.getName();
    try
    {
      new DirectoryServer.InitializationBuilder(configFile.getValue())
          .requireCryptoServices()
          .initialize();
    }
    catch (InitializationException e)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(e.getLocalizedMessage()));
      return 1;
    }
    if (LIST_BACKENDS.equals(subCommandName))
    {
      return listRootContainers();
    }
    BackendImpl<?> backend = getBackendById(subCommand.getArgument(BACKENDID_NAME));
    if (backend == null)
    {
      return 1;
    }
    RootContainer rootContainer = getAndLockRootContainer(backend);
    if (rootContainer == null)
    {
      return 1;
    }
    try
    {
      switch (subCommandName)
      {
      case LIST_BASE_DNS:
        return listBaseDNs(rootContainer);
      case LIST_RAW_DBS:
        return listRawDBs(rootContainer, subCommand.getArgument(USESIUNITS_NAME));
      case LIST_INDEXES:
        return listIndexes(rootContainer, backend, subCommand.getArgument(BASEDN_NAME));
      case DUMP_RAW_DB:
        return dumpTree(rootContainer, backend, subCommand, false);
      case DUMP_INDEX:
        return dumpTree(rootContainer, backend, subCommand, true);
      case SHOW_INDEX_STATUS:
        return showIndexStatus(rootContainer, backend, subCommand.getArgument(BASEDN_NAME));
      default:
        return 1;
      }
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_EXECUTING_COMMAND.get(subCommandName,
          StaticUtils.stackTraceToString(e)));
      return 1;
    }
    finally
    {
      close(rootContainer);
      releaseExclusiveLock(backend);
    }
  }

  private int dumpTree(RootContainer rc, BackendImpl<?> backend, SubCommand subCommand, boolean isBackendTree)
      throws ArgumentException, DirectoryException
  {
    Options options = Options.defaultOptions();
    if (!setDumpTreeOptionArguments(subCommand, options))
    {
      return 1;
    }
    if (isBackendTree)
    {
      return dumpBackendTree(rc, backend, subCommand.getArgument(BASEDN_NAME), subCommand.getArgument(INDEXNAME_NAME),
          options);
    }
    return dumpStorageTree(rc, backend, subCommand.getArgument(DBNAME_NAME), options);
  }

  private boolean setDumpTreeOptionArguments(SubCommand subCommand, Options options) throws ArgumentException
  {
    try
    {
      Argument arg = subCommand.getArgument(SINGLELINE_NAME);
      if (arg != null && arg.isPresent())
      {
        options.set(DUMP_SINGLE_LINE, true);
      }
      if (subCommand.getArgument(STATSONLY_NAME).isPresent())
      {
        options.set(DUMP_STATS_ONLY, true);
      }
      arg = subCommand.getArgument(SKIPDECODE_NAME);
      if (arg == null || arg.isPresent())
      {
        options.set(DUMP_DECODE_VALUE, false);
      }
      if (subCommand.getArgument(MINDATASIZE_NAME).isPresent())
      {
        options.set(DUMP_MIN_DATA_SIZE, subCommand.getArgument(MINDATASIZE_NAME).getIntValue());
      }
      if (subCommand.getArgument(MAXDATASIZE_NAME).isPresent())
      {
        options.set(DUMP_MAX_DATA_SIZE, subCommand.getArgument(MAXDATASIZE_NAME).getIntValue());
      }

      options.set(DUMP_MIN_KEY_VALUE, subCommand.getArgument(MINKEYVALUE_NAME));
      if (subCommand.getArgument(MINHEXKEYVALUE_NAME).isPresent())
      {
        if (subCommand.getArgument(MINKEYVALUE_NAME).isPresent())
        {
          printWrappedText(err, ERR_BACKEND_TOOL_ONLY_ONE_MIN_KEY.get());
          return false;
        }
        options.set(DUMP_MIN_KEY_VALUE_IS_HEX, true);
        options.set(DUMP_MIN_KEY_VALUE, subCommand.getArgument(MINHEXKEYVALUE_NAME));
      }

      options.set(DUMP_MAX_KEY_VALUE, subCommand.getArgument(MAXKEYVALUE_NAME));
      if (subCommand.getArgument(MAXHEXKEYVALUE_NAME).isPresent())
      {
        if (subCommand.getArgument(MAXKEYVALUE_NAME).isPresent())
        {
          printWrappedText(err, ERR_BACKEND_TOOL_ONLY_ONE_MAX_KEY.get());
          return false;
        }
        options.set(DUMP_MAX_KEY_VALUE_IS_HEX, true);
        options.set(DUMP_MAX_KEY_VALUE, subCommand.getArgument(MAXHEXKEYVALUE_NAME));
      }
      return true;
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_PROCESSING_ARGUMENT.get(StaticUtils.stackTraceToString(ae)));
      throw ae;
    }
  }

  private int listRootContainers()
  {
    TableBuilder builder = new TableBuilder();

    builder.appendHeading(INFO_LABEL_BACKEND_DEBUG_BACKEND_ID.get());
    builder.appendHeading(INFO_LABEL_BACKEND_TOOL_STORAGE.get());

    final Map<PluggableBackendCfg, BackendImpl<?>> pluggableBackends = getPluggableBackends();
    for (Map.Entry<PluggableBackendCfg, BackendImpl<?>> backend : pluggableBackends.entrySet())
    {
      builder.startRow();
      builder.appendCell(backend.getValue().getBackendID());
      builder.appendCell(backend.getKey().getJavaClass());
    }

    builder.print(new TextTablePrinter(out));
    out.format(INFO_LABEL_BACKEND_TOOL_TOTAL.get(pluggableBackends.size()).toString());

    return 0;
  }

  private int listBaseDNs(RootContainer rc)
  {
    try
    {
      TableBuilder builder = new TableBuilder();
      builder.appendHeading(INFO_LABEL_BACKEND_DEBUG_BASE_DN.get());
      Collection<EntryContainer> entryContainers = rc.getEntryContainers();
      for (EntryContainer ec : entryContainers)
      {
        builder.startRow();
        builder.appendCell(ec.getBaseDN());
      }

      builder.print(new TextTablePrinter(out));
      out.format(INFO_LABEL_BACKEND_TOOL_TOTAL.get(entryContainers.size()).toString());

      return 0;
    }
    catch (StorageRuntimeException de)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_ERROR_LISTING_BASE_DNS.get(stackTraceToSingleLineString(de)));
      return 1;
    }
  }

  private int listRawDBs(RootContainer rc, Argument useSIUnits)
  {
    try
    {
      TableBuilder builder = new TableBuilder();

      builder.appendHeading(INFO_LABEL_BACKEND_TOOL_RAW_DB_NAME.get());
      builder.appendHeading(INFO_LABEL_BACKEND_TOOL_TOTAL_KEYS.get());
      builder.appendHeading(INFO_LABEL_BACKEND_TOOL_KEYS_SIZE.get());
      builder.appendHeading(INFO_LABEL_BACKEND_TOOL_VALUES_SIZE.get());
      builder.appendHeading(INFO_LABEL_BACKEND_TOOL_TOTAL_SIZES.get());

      SortedSet<TreeName> treeNames = new TreeSet<>(rc.getStorage().listTrees());
      for (TreeName tree: treeNames)
      {
        builder.startRow();
        builder.appendCell(tree);
        appendStorageTreeStats(builder, rc, new StorageTreeKeyValue(tree), useSIUnits.isPresent());
      }

      builder.print(new TextTablePrinter(out));
      out.format(INFO_LABEL_BACKEND_TOOL_TOTAL.get(treeNames.size()).toString());

      return 0;
    }
    catch (StorageRuntimeException de)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_ERROR_LISTING_TREES.get(stackTraceToSingleLineString(de)));
      return 1;
    }
  }

  private void appendStorageTreeStats(TableBuilder builder, RootContainer rc, TreeKeyValue targetTree,
      boolean useSIUnit)
  {
    Options options = Options.defaultOptions();
    options.set(DUMP_STATS_ONLY, true);
    try
    {
      options.set(DUMP_MIN_KEY_VALUE, newMinKeyValueArg());
      options.set(DUMP_MAX_KEY_VALUE, newMaxKeyValueArg());
      TreeStats treeStats = cursorTreeToDump(rc, targetTree, options);
      builder.appendCell(treeStats.count);
      builder.appendCell(appendKeyValueSize(treeStats.totalKeySize, useSIUnit));
      builder.appendCell(appendKeyValueSize(treeStats.totalDataSize, useSIUnit));
      builder.appendCell(appendKeyValueSize(treeStats.totalKeySize + treeStats.totalDataSize, useSIUnit));
    }
    catch (Exception e)
    {
      appendStatsNoData(builder, 3);
    }
  }

  private String appendKeyValueSize(long size, boolean useSIUnit)
  {
    if (useSIUnit && size > SizeUnit.KILO_BYTES.getSize())
    {
      NumberFormat format = NumberFormat.getNumberInstance();
      format.setMaximumFractionDigits(2);
      SizeUnit unit = SizeUnit.getBestFitUnit(size);
      return format.format(unit.fromBytes(size)) + " " + unit;
    }
    else
    {
      return String.valueOf(size);
    }
  }

  private int listIndexes(RootContainer rc, BackendImpl<?> backend, Argument baseDNArg) throws DirectoryException
  {
    DN base = null;
    if (baseDNArg.isPresent())
    {
      base = getBaseDNFromArg(baseDNArg);
    }

    try
    {
      TableBuilder builder = new TableBuilder();
      int count = 0;

      builder.appendHeading(INFO_LABEL_BACKEND_DEBUG_INDEX_NAME.get());
      builder.appendHeading(INFO_LABEL_BACKEND_TOOL_RAW_DB_NAME.get());
      builder.appendHeading(INFO_LABEL_BACKEND_DEBUG_INDEX_TYPE.get());
      builder.appendHeading(INFO_LABEL_BACKEND_DEBUG_RECORD_COUNT.get());

      if (base != null)
      {
        EntryContainer ec = rc.getEntryContainer(base);
        if (ec == null)
        {
          return printEntryContainerError(backend, base);
        }
        count = appendTreeRows(builder, ec);
      }
      else
      {
        for (EntryContainer ec : rc.getEntryContainers())
        {
          builder.startRow();
          builder.appendCell("Base DN: " + ec.getBaseDN());
          count += appendTreeRows(builder, ec);
        }
      }

      builder.print(new TextTablePrinter(out));
      out.format(INFO_LABEL_BACKEND_TOOL_TOTAL.get(count).toString());

      return 0;
    }
    catch (StorageRuntimeException de)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_ERROR_LISTING_TREES.get(stackTraceToSingleLineString(de)));
      return 1;
    }
  }

  private int printEntryContainerError(BackendImpl<?> backend, DN base)
  {
    printWrappedText(err, ERR_BACKEND_DEBUG_NO_ENTRY_CONTAINERS_FOR_BASE_DN.get(base, backend.getBackendID()));
    return 1;
  }

  private DN getBaseDNFromArg(Argument baseDNArg) throws DirectoryException
  {
    try
    {
      return DN.valueOf(baseDNArg.getValue());
    }
    catch (LocalizedIllegalArgumentException e)
    {
      printWrappedText(err, ERR_BACKEND_DEBUG_DECODE_BASE_DN.get(baseDNArg.getValue(), getExceptionMessage(e)));
      throw e;
    }
  }

  private RootContainer getAndLockRootContainer(BackendImpl<?> backend)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        printWrappedText(err, ERR_BACKEND_DEBUG_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), failureReason));
        return null;
      }
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_BACKEND_DEBUG_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), StaticUtils
          .getExceptionMessage(e)));
      return null;
    }

    try
    {
      return backend.getReadOnlyRootContainer();
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_ERROR_INITIALIZING_BACKEND.get(backend.getBackendID(),
          stackTraceToSingleLineString(e)));
      return null;
    }
  }

  private int appendTreeRows(TableBuilder builder, EntryContainer ec)
  {
    int count = 0;
    for (final Tree tree : ec.listTrees())
    {
      builder.startRow();
      builder.appendCell(tree.getName().getIndexId());
      builder.appendCell(tree.getName());
      builder.appendCell(tree.getClass().getSimpleName());
      builder.appendCell(getTreeRecordCount(ec, tree));
      count++;
    }
    return count;
  }

  private long getTreeRecordCount(EntryContainer ec, final Tree tree)
  {
    try
    {
      return ec.getRootContainer().getStorage().read(new ReadOperation<Long>()
      {
        @Override
        public Long run(ReadableTransaction txn) throws Exception
        {
          return tree.getRecordCount(txn);
        }
      });
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_ERROR_READING_TREE.get(stackTraceToSingleLineString(e)));
      return -1;
    }
  }

  private void close(RootContainer rc)
  {
    try
    {
      rc.close();
    }
    catch (StorageRuntimeException ignored)
    {
      // Ignore.
    }
  }

  private void releaseExclusiveLock(BackendImpl<?> backend)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.releaseLock(lockFile, failureReason))
      {
        printWrappedText(err, WARN_BACKEND_DEBUG_CANNOT_UNLOCK_BACKEND.get(backend.getBackendID(), failureReason));
      }
    }
    catch (Exception e)
    {
      printWrappedText(err, WARN_BACKEND_DEBUG_CANNOT_UNLOCK_BACKEND.get(backend.getBackendID(),
          StaticUtils.getExceptionMessage(e)));
    }
  }

  private BackendImpl<?> getBackendById(Argument backendIdArg)
  {
    final String backendID = backendIdArg.getValue();
    final Map<PluggableBackendCfg, BackendImpl<?>> pluggableBackends = getPluggableBackends();

    for (Map.Entry<PluggableBackendCfg, BackendImpl<?>> backend : pluggableBackends.entrySet())
    {
      final BackendImpl b = backend.getValue();
      if (b.getBackendID().equalsIgnoreCase(backendID))
      {
        try
        {
          b.configureBackend(backend.getKey(), DirectoryServer.getInstance().getServerContext());
          return b;
        }
        catch (ConfigException ce)
        {
          printWrappedText(err, ERR_BACKEND_TOOL_CANNOT_CONFIGURE_BACKEND.get(backendID, ce));
          return null;
        }
      }
    }

    printWrappedText(err, ERR_BACKEND_DEBUG_NO_BACKENDS_FOR_ID.get(backendID));
    return null;
  }

  private int showIndexStatus(RootContainer rc, BackendImpl<?> backend, Argument baseDNArg) throws DirectoryException
  {
    DN base = getBaseDNFromArg(baseDNArg);

    try
    {
      // Create a table of their properties.
      TableBuilder builder = new TableBuilder();
      int count = 0;

      builder.appendHeading(INFO_LABEL_BACKEND_DEBUG_INDEX_NAME.get());
      builder.appendHeading(INFO_LABEL_BACKEND_TOOL_RAW_DB_NAME.get());
      builder.appendHeading(INFO_LABEL_BACKEND_DEBUG_INDEX_STATUS.get());
      builder.appendHeading(INFO_LABEL_BACKEND_DEBUG_INDEX_CONFIDENTIAL.get());
      builder.appendHeading(INFO_LABEL_BACKEND_DEBUG_RECORD_COUNT.get());
      builder.appendHeading(INFO_LABEL_BACKEND_TOOL_INDEX_UNDEFINED_RECORD_COUNT.get());
      builder.appendHeading(LocalizableMessage.raw("95%"));
      builder.appendHeading(LocalizableMessage.raw("90%"));
      builder.appendHeading(LocalizableMessage.raw("85%"));

      EntryContainer ec = rc.getEntryContainer(base);
      if (ec == null)
      {
        return printEntryContainerError(backend, base);
      }

      Map<Index, StringBuilder> undefinedKeys = new HashMap<>();
      for (AttributeIndex attrIndex : ec.getAttributeIndexes())
      {
        for (AttributeIndex.MatchingRuleIndex index : attrIndex.getNameToIndexes().values())
        {
          builder.startRow();
          builder.appendCell(index.getName().getIndexId());
          builder.appendCell(index.getName());
          builder.appendCell(index.isTrusted());
          builder.appendCell(index.isEncrypted());
          if (index.isTrusted())
          {
            appendIndexStats(builder, ec, index, undefinedKeys);
          }
          else
          {
            appendStatsNoData(builder, 5);
          }
          count++;
        }
      }

      for (VLVIndex vlvIndex : ec.getVLVIndexes())
      {
        builder.startRow();
        builder.appendCell(vlvIndex.getName().getIndexId());
        builder.appendCell(vlvIndex.getName());
        builder.appendCell(vlvIndex.isTrusted());
        builder.appendCell(getTreeRecordCount(ec, vlvIndex));
        appendStatsNoData(builder, 4);
        count++;
      }

      builder.print(new TextTablePrinter(out));
      out.format(INFO_LABEL_BACKEND_TOOL_TOTAL.get(count).toString());
      for (Map.Entry<Index, StringBuilder> e : undefinedKeys.entrySet())
      {
        out.format(INFO_LABEL_BACKEND_TOOL_INDEX.get(e.getKey().getName()).toString());
        out.format(INFO_LABEL_BACKEND_TOOL_OVER_INDEX_LIMIT_KEYS.get(e.getValue()).toString());
      }
      return 0;
    }
    catch (StorageRuntimeException de)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_ERROR_READING_TREE.get(stackTraceToSingleLineString(de)));
      return 1;
    }
  }

  private void appendStatsNoData(TableBuilder builder, int columns)
  {
    while (columns > 0)
    {
      builder.appendCell("-");
      columns--;
    }
  }

  private void appendIndexStats(final TableBuilder builder, EntryContainer ec, final Index index,
      final Map<Index, StringBuilder> undefinedKeys)
  {
    final long entryLimit = index.getIndexEntryLimit();

    try
    {
      ec.getRootContainer().getStorage().read(new ReadOperation<Void>()
      {
        @Override
        public Void run(ReadableTransaction txn) throws Exception
        {
          long eighty = 0;
          long ninety = 0;
          long ninetyFive = 0;
          long undefined = 0;
          long count = 0;
          BackendTreeKeyValue keyDecoder = new BackendTreeKeyValue(index);
          try (Cursor<ByteString, EntryIDSet> cursor = index.openCursor(txn))
          {
            while (cursor.next())
            {
              count++;
              EntryIDSet entryIDSet;
              try
              {
                entryIDSet = cursor.getValue();
              }
              catch (Exception e)
              {
                continue;
              }

              if (entryIDSet.isDefined())
              {
                if (entryIDSet.size() >= entryLimit * 0.8)
                {
                  if (entryIDSet.size() >= entryLimit * 0.95)
                  {
                    ninetyFive++;
                  }
                  else if (entryIDSet.size() >= entryLimit * 0.9)
                  {
                    ninety++;
                  }
                  else
                  {
                    eighty++;
                  }
                }
              }
              else
              {
                undefined++;
                StringBuilder keyList = undefinedKeys.get(index);
                if (keyList == null)
                {
                  keyList = new StringBuilder();
                  undefinedKeys.put(index, keyList);
                }
                else
                {
                  keyList.append(" ");
                }
                keyList.append("[").append(keyDecoder.keyDecoder(cursor.getKey())).append("]");
              }
            }
          }
          builder.appendCell(count);
          builder.appendCell(undefined);
          builder.appendCell(ninetyFive);
          builder.appendCell(ninety);
          builder.appendCell(eighty);
          return null;
        }
      });
    }
    catch (Exception e)
    {
      appendStatsNoData(builder, 5);
      printWrappedText(err, ERR_BACKEND_TOOL_ERROR_READING_TREE.get(index.getName()));
    }
  }

  private int dumpStorageTree(RootContainer rc, BackendImpl<?> backend, Argument treeNameArg, Options options)
  {
    TreeName targetTree = getStorageTreeName(treeNameArg, rc);
    if (targetTree == null)
    {
      printWrappedText(err,
          ERR_BACKEND_TOOL_NO_TREE_FOR_NAME_IN_STORAGE.get(treeNameArg.getValue(), backend.getBackendID()));
      return 1;
    }

    try
    {
      dumpActualTree(rc, new StorageTreeKeyValue(targetTree), options);
      return 0;
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_ERROR_READING_TREE.get(stackTraceToSingleLineString(e)));
      return 1;
    }
  }

  private TreeName getStorageTreeName(Argument treeNameArg, RootContainer rc)
  {
    for (TreeName tree : rc.getStorage().listTrees())
    {
      if (treeNameArg.getValue().equals(tree.toString()))
      {
        return tree;
      }
    }
    return null;
  }

  private int dumpBackendTree(RootContainer rc, BackendImpl<?> backend, Argument baseDNArg, Argument treeNameArg,
      Options options) throws DirectoryException
  {
    DN base = getBaseDNFromArg(baseDNArg);

    EntryContainer ec = rc.getEntryContainer(base);
    if (ec == null)
    {
      return printEntryContainerError(backend, base);
    }

    Tree targetTree = getBackendTree(treeNameArg, ec);
    if (targetTree == null)
    {
      printWrappedText(err,
          ERR_BACKEND_TOOL_NO_TREE_FOR_NAME.get(treeNameArg.getValue(), base, backend.getBackendID()));
      return 1;
    }

    try
    {
      dumpActualTree(rc, new BackendTreeKeyValue(targetTree), options);
      return 0;
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_BACKEND_TOOL_ERROR_READING_TREE.get(stackTraceToSingleLineString(e)));
      return 1;
    }
  }

  private Tree getBackendTree(Argument treeNameArg, EntryContainer ec)
  {
    for (Tree tree : ec.listTrees())
    {
      if (treeNameArg.getValue().contains(tree.getName().getIndexId())
          || treeNameArg.getValue().equals(tree.getName().toString()))
      {
        return tree;
      }
    }
    return null;
  }

  private void dumpActualTree(RootContainer rc, final TreeKeyValue target, final Options options) throws Exception
  {
    TreeStats treeStats =  cursorTreeToDump(rc, target, options);
    out.format(INFO_LABEL_BACKEND_TOOL_TOTAL_RECORDS.get(treeStats.count).toString());
    if (treeStats.count > 0)
    {
      out.format(INFO_LABEL_BACKEND_TOOL_TOTAL_KEY_SIZE_AND_AVG.get(
          treeStats.totalKeySize, treeStats.totalKeySize / treeStats.count).toString());
      out.format(INFO_LABEL_BACKEND_TOOL_TOTAL_DATA_SIZE_AND_AVG.get(
          treeStats.totalDataSize, treeStats.totalDataSize / treeStats.count).toString());
    }
  }

  private TreeStats cursorTreeToDump(RootContainer rc, final TreeKeyValue target, final Options options)
      throws Exception
  {
    return rc.getStorage().read(new ReadOperation<TreeStats>()
      {
        @Override
        public TreeStats run(ReadableTransaction txn) throws Exception
        {
          long count = 0;
          long totalKeySize = 0;
          long totalDataSize = 0;
          try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(target.getTreeName()))
          {
            ByteString key;
            ByteString maxKey = null;
            ByteString value;

            if (options.get(DUMP_MIN_KEY_VALUE).isPresent())
            {
              key = getMinOrMaxKey(options, DUMP_MIN_KEY_VALUE, DUMP_MIN_KEY_VALUE_IS_HEX);
              if (!cursor.positionToKeyOrNext(key))
              {
                return new TreeStats(0, 0, 0);
              }
            }
            else
            {
              if (!cursor.next())
              {
                return new TreeStats(0, 0, 0);
              }
            }

            if (options.get(DUMP_MAX_KEY_VALUE).isPresent())
            {
              maxKey = getMinOrMaxKey(options, DUMP_MAX_KEY_VALUE, DUMP_MAX_KEY_VALUE_IS_HEX);
            }

            do
            {
              key = cursor.getKey();
              if (maxKey != null && key.compareTo(maxKey) > 0)
              {
                break;
              }
              value = cursor.getValue();
              long valueLen = value.length();
              if (options.get(DUMP_MIN_DATA_SIZE) <= valueLen && valueLen <= options.get(DUMP_MAX_DATA_SIZE))
              {
                count++;
                int keyLen = key.length();
                totalKeySize += keyLen;
                totalDataSize += valueLen;
                if (!options.get(DUMP_STATS_ONLY))
                {
                  if (options.get(DUMP_DECODE_VALUE))
                  {
                    String k = target.keyDecoder(key);
                    String v = target.valueDecoder(value);
                    out.format(INFO_LABEL_BACKEND_TOOL_KEY_FORMAT.get(keyLen) + " %s%n"
                        + INFO_LABEL_BACKEND_TOOL_VALUE_FORMAT.get(valueLen) + " %s%n", k, v);
                  }
                  else
                  {
                    hexDumpRecord(key, value, out, options);
                  }
                }
              }
            }
            while (cursor.next());
          }
          catch (Exception e)
          {
            out.format(ERR_BACKEND_TOOL_CURSOR_AT_KEY_NUMBER.get(count, e.getCause()).toString());
            e.printStackTrace(out);
            out.format("%n");
            throw e;
          }
          return new TreeStats(count, totalKeySize, totalDataSize);
        }

      private ByteString getMinOrMaxKey(Options options, Option<Argument> keyOpt, Option<Boolean> isHexKey)
      {
        ByteString key;
        if (options.get(isHexKey))
        {
          key = ByteString.valueOfHex(options.get(keyOpt).getValue());
        }
        else
        {
          key = target.getTreeKey(options.get(keyOpt).getValue());
        }
        return key;
      }
    });
  }

  final void hexDumpRecord(ByteString key, ByteString value, PrintStream out, Options options)
  {
    if (options.get(DUMP_SINGLE_LINE))
    {
      out.format(INFO_LABEL_BACKEND_TOOL_KEY_FORMAT.get(key.length()) + " ");
      toHexDumpSingleLine(out, key);
      out.format(INFO_LABEL_BACKEND_TOOL_VALUE_FORMAT.get(value.length()) + " ");
      toHexDumpSingleLine(out, value);
    }
    else
    {
      out.format(INFO_LABEL_BACKEND_TOOL_KEY_FORMAT.get(key.length()) + "%n");
      toHexDumpWithAsciiCompact(key, options.get(DUMP_INDENT), out);
      out.format(INFO_LABEL_BACKEND_TOOL_VALUE_FORMAT.get(value.length()) + "%n");
      toHexDumpWithAsciiCompact(value, options.get(DUMP_INDENT), out);
    }
  }

  final void toHexDumpSingleLine(PrintStream out, ByteString data)
  {
    for (int i = 0; i < data.length(); i++)
    {
      out.format("%s", StaticUtils.byteToHex(data.byteAt(i)));
    }
    out.format("%n");
  }

  final void toHexDumpWithAsciiCompact(ByteString data, int indent, PrintStream out)
  {
    StringBuilder hexDump = new StringBuilder();
    StringBuilder indentBuilder = new StringBuilder();
    StringBuilder asciiDump = new StringBuilder();
    for (int i = 0; i < indent; i++)
    {
      indentBuilder.append(' ');
    }
    int pos = 0;
    while (pos < data.length())
    {
      byte val = data.byteAt(pos);
      hexDump.append(StaticUtils.byteToHex(val));
      hexDump.append(' ');
      asciiDump.append(val >= ' ' ? (char)val : ".");
      pos++;
      if (pos % 16 == 0)
      {
        out.format(HEXDUMP_LINE_FORMAT, indentBuilder.toString(), hexDump.toString(), asciiDump.toString());
        hexDump.setLength(0);
        asciiDump.setLength(0);
      }
    }
    while (pos % 16 != 0)
    {
      hexDump.append("   ");
      pos++;
    }
    out.format(HEXDUMP_LINE_FORMAT, indentBuilder.toString(), hexDump.toString(), asciiDump.toString());
  }

  private static Map<PluggableBackendCfg, BackendImpl<?>> getPluggableBackends()
  {
    List<Backend<?>> backendList = new ArrayList<>();
    List<BackendCfg> entryList = new ArrayList<>();
    List<List<DN>> dnList = new ArrayList<>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);

    final Map<PluggableBackendCfg, BackendImpl<?>> pluggableBackends = new LinkedHashMap<>();
    for (int i = 0; i < backendList.size(); i++)
    {
      Backend<?> backend = backendList.get(i);
      if (backend instanceof BackendImpl)
      {
        pluggableBackends.put((PluggableBackendCfg) entryList.get(i), (BackendImpl<?>) backend);
      }
    }
    return pluggableBackends;
  }
}
