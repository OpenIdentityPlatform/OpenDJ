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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.tools.dsconfig;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.Utils.SHELL_COMMENT_SEPARATOR;
import static com.forgerock.opendj.cli.Utils.canWrite;
import static com.forgerock.opendj.cli.Utils.filterExitCode;
import static com.forgerock.opendj.cli.Utils.formatDateTimeStringForEquivalentCommand;
import static com.forgerock.opendj.cli.Utils.getCurrentOperationDateMessage;
import static com.forgerock.opendj.util.StaticUtils.stackTraceToSingleLineString;
import static org.forgerock.util.Utils.closeSilently;
import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;
import static org.opends.server.tools.dsconfig.ArgumentExceptionFactory.displayManagedObjectDecodingException;
import static org.opends.server.tools.dsconfig.ArgumentExceptionFactory.displayMissingMandatoryPropertyException;
import static org.opends.server.tools.dsconfig.ArgumentExceptionFactory.displayOperationRejectedException;
import static org.opends.server.util.ServerConstants.PROPERTY_SCRIPT_NAME;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.Tag;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.MissingMandatoryPropertiesException;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.InitializationException;
import org.opends.server.util.BuildVersion;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentGroup;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommandBuilder;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.Menu;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuCallback;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;



/**
 * This class provides a command-line tool which enables
 * administrators to configure the Directory Server.
 */
public final class DSConfig extends ConsoleApplication {

  /**
   * The name of this tool.
   */
  final static String DSCONFIGTOOLNAME = "dsconfig";

  /**
   * A menu call-back which runs a sub-command interactively.
   */
  private class SubCommandHandlerMenuCallback implements MenuCallback<Integer> {

    /** The sub-command handler. */
    private final SubCommandHandler handler;



    /**
     * Creates a new sub-command handler call-back.
     *
     * @param handler
     *          The sub-command handler.
     */
    public SubCommandHandlerMenuCallback(SubCommandHandler handler) {
      this.handler = handler;
    }



    /** {@inheritDoc} */
    @Override
    public MenuResult<Integer> invoke(ConsoleApplication app)
    throws ClientException {
      try {
        MenuResult<Integer> result = handler.run(app, factory);

        if (result.isQuit()) {
          return result;
        } else {
          if (result.isSuccess() && isInteractive() &&
              handler.isCommandBuilderUseful())
          {
            printCommandBuilder(getCommandBuilder(handler));
          }
          // Success or cancel.
          app.println();
          app.pressReturnToContinue();
          return MenuResult.again();
        }
      } catch (ArgumentException e) {
        app.println(e.getMessageObject());
        return MenuResult.success(1);
      } catch (ClientException e) {
        app.println(e.getMessageObject());
        return MenuResult.success(e.getReturnCode());
      }
    }
  }



  /**
   * The interactive mode sub-menu implementation.
   */
  private class SubMenuCallback implements MenuCallback<Integer> {

    /** The menu. */
    private final Menu<Integer> menu;



    /**
     * Creates a new sub-menu implementation.
     *
     * @param app
     *          The console application.
     * @param rd
     *          The relation definition.
     * @param ch
     *          The optional create sub-command.
     * @param dh
     *          The optional delete sub-command.
     * @param lh
     *          The optional list sub-command.
     * @param sh
     *          The option set-prop sub-command.
     */
    public SubMenuCallback(ConsoleApplication app, RelationDefinition<?, ?> rd,
        CreateSubCommandHandler<?, ?> ch, DeleteSubCommandHandler dh,
        ListSubCommandHandler lh, SetPropSubCommandHandler sh) {
      LocalizableMessage ufn = rd.getUserFriendlyName();

      LocalizableMessage ufpn = null;
      if (rd instanceof InstantiableRelationDefinition<?,?>) {
        InstantiableRelationDefinition<?, ?> ir =
          (InstantiableRelationDefinition<?, ?>) rd;
        ufpn = ir.getUserFriendlyPluralName();
      } else if (rd instanceof SetRelationDefinition<?,?>) {
        SetRelationDefinition<?, ?> sr =
          (SetRelationDefinition<?, ?>) rd;
        ufpn = sr.getUserFriendlyPluralName();
      }

      MenuBuilder<Integer> builder = new MenuBuilder<Integer>(app);

      builder.setTitle(INFO_DSCFG_HEADING_COMPONENT_MENU_TITLE.get(ufn));
      builder.setPrompt(INFO_DSCFG_HEADING_COMPONENT_MENU_PROMPT.get());

      if (lh != null) {
        SubCommandHandlerMenuCallback callback =
          new SubCommandHandlerMenuCallback(lh);
        if (ufpn != null) {
          builder.addNumberedOption(
              INFO_DSCFG_OPTION_COMPONENT_MENU_LIST_PLURAL.get(ufpn), callback);
        } else {
          builder
          .addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_LIST_SINGULAR
              .get(ufn), callback);
        }
      }

      if (ch != null) {
        SubCommandHandlerMenuCallback callback =
          new SubCommandHandlerMenuCallback(ch);
        builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_CREATE
            .get(ufn), callback);
      }

      if (sh != null) {
        SubCommandHandlerMenuCallback callback =
          new SubCommandHandlerMenuCallback(sh);
        if (ufpn != null) {
          builder
          .addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_MODIFY_PLURAL
              .get(ufn), callback);
        } else {
          builder.addNumberedOption(
              INFO_DSCFG_OPTION_COMPONENT_MENU_MODIFY_SINGULAR.get(ufn),
              callback);
        }
      }

      if (dh != null) {
        SubCommandHandlerMenuCallback callback =
          new SubCommandHandlerMenuCallback(dh);
        builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_DELETE
            .get(ufn), callback);
      }

      builder.addBackOption(true);
      builder.addQuitOption();

      this.menu = builder.toMenu();
    }



    /** {@inheritDoc} */
    @Override
    public final MenuResult<Integer> invoke(ConsoleApplication app)
    throws ClientException {
      try {
        app.println();
        app.println();

        MenuResult<Integer> result = menu.run();

        if (result.isCancel()) {
          return MenuResult.again();
        }

        return result;
      } catch (ClientException e) {
        app.println(e.getMessageObject());
        return MenuResult.success(1);
      }
    }

  }

  /**
   * The type name which will be used for the most generic managed
   * object types when they are instantiable and intended for
   * customization only.
   */
  public static final String CUSTOM_TYPE = "custom";

  /**
   * The type name which will be used for the most generic managed
   * object types when they are instantiable and not intended for
   * customization.
   */
  public static final String GENERIC_TYPE = "generic";

  private long sessionStartTime;
  private boolean sessionStartTimePrinted = false;
  private int sessionEquivalentOperationNumber = 0;

  /**
   * Provides the command-line arguments to the main application for
   * processing.
   *
   * @param args
   *          The set of command-line arguments provided to this
   *          program.
   */
  public static void main(String[] args) {
    int exitCode = main(args, System.out, System.err);
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
   * @param outStream
   *          The output stream for standard output.
   * @param errStream
   *          The output stream for standard error.
   * @return Zero to indicate that the program completed successfully,
   *         or non-zero to indicate that an error occurred.
   */
  public static int main(String[] args, OutputStream outStream,
      OutputStream errStream)
  {
    JDKLogging.disableLogging();
    DSConfig app =
        new DSConfig(System.in, outStream, errStream,
            new LDAPManagementContextFactory());
    app.sessionStartTime = System.currentTimeMillis();
    /*
     * FIXME: obtain path info from system properties.
     */
    if (!ConfigurationFramework.getInstance().isInitialized())
    {
      try
      {
        ConfigurationFramework.getInstance().initialize(
            DirectoryServer.getServerRoot(), DirectoryServer.getInstanceRoot());
      }
      catch (ConfigException e)
      {
        app.println(e.getMessageObject());
        return 1;
      }
    }

    // Run the application.
    return app.run(args);
  }

  /** The argument which should be used to request advanced mode. */
  private BooleanArgument advancedModeArgument;

  /**
   * The factory which the application should use to retrieve its management
   * context.
   */
  private final ManagementContextFactory factory;

  /**
   * Flag indicating whether or not the global arguments have already been
   * initialized.
   */
  private boolean globalArgumentsInitialized = false;

  /** The sub-command handler factory. */
  private SubCommandHandlerFactory handlerFactory = null;

  /** Mapping of sub-commands to their implementations. */
  private final Map<SubCommand, SubCommandHandler> handlers =
    new HashMap<SubCommand, SubCommandHandler>();

  /** Indicates whether or not a sub-command was provided. */
  private boolean hasSubCommand = true;

  /** The argument which should be used to read dsconfig commands from a file. */
  private StringArgument batchFileArgument;

  /**
   * The argument which should be used to request non interactive behavior.
   */
  private BooleanArgument noPromptArgument;

  /**
   * The argument that the user must set to display the equivalent
   * non-interactive mode argument.
   */
  private BooleanArgument displayEquivalentArgument;

  /**
   * The argument that allows the user to dump the equivalent non-interactive
   * command to a file.
   */
  private StringArgument equivalentCommandFileArgument;

  /** The command-line argument parser. */
  private final SubCommandArgumentParser parser;

  /** The argument which should be used to request quiet output. */
  private BooleanArgument quietArgument;

  /**
   * The argument which should be used to request script-friendly output.
   */
  private BooleanArgument scriptFriendlyArgument;

  /** The argument which should be used to request usage information. */
  private BooleanArgument showUsageArgument;

  /** The argument which should be used to request verbose output. */
  private BooleanArgument verboseArgument;

  /** The argument which should be used to indicate the properties file. */
  private StringArgument propertiesFileArgument;

  /**
   * The argument which should be used to indicate that we will not look for
   * properties file.
   */
  private BooleanArgument noPropertiesFileArgument;

  /**
   * Creates a new dsconfig application instance.
   *
   * @param in
   *          The application input stream.
   * @param out
   *          The application output stream.
   * @param err
   *          The application error stream.
   * @param factory
   *          The factory which this application instance should use
   *          for obtaining management contexts.
   */
  private DSConfig(InputStream in, OutputStream out, OutputStream err,
      ManagementContextFactory factory) {
    super(new PrintStream(out), new PrintStream(err));

    this.parser = new SubCommandArgumentParser(getClass().getName(),
        INFO_DSCFG_TOOL_DESCRIPTION.get(), false);

    this.factory = factory;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isAdvancedMode() {
    return advancedModeArgument.isPresent();
  }



  /** {@inheritDoc} */
  @Override
  public boolean isInteractive() {
    return !noPromptArgument.isPresent();
  }



  /** {@inheritDoc} */
  @Override
  public boolean isMenuDrivenMode() {
    return !hasSubCommand;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isQuiet() {
    return quietArgument.isPresent();
  }



  /** {@inheritDoc} */
  @Override
  public boolean isScriptFriendly() {
    return scriptFriendlyArgument.isPresent();
  }



  /** {@inheritDoc} */
  @Override
  public boolean isVerbose() {
    return verboseArgument.isPresent();
  }



  /** Displays the provided message followed by a help usage reference. */
  private void displayMessageAndUsageReference(LocalizableMessage message) {
    println(message);
    println();
    println(parser.getHelpUsageReference());
  }



  /**
   * Registers the global arguments with the argument parser.
   *
   * @throws ArgumentException
   *           If a global argument could not be registered.
   */
  private void initializeGlobalArguments(String[] args)
  throws ArgumentException {
    if (!globalArgumentsInitialized) {

      verboseArgument = CommonArguments.getVerbose();
      quietArgument = CommonArguments.getQuiet();
      scriptFriendlyArgument = CommonArguments.getScriptFriendly();
      noPromptArgument = CommonArguments.getNoPrompt();
      advancedModeArgument = CommonArguments.getAdvancedMode();
      showUsageArgument = CommonArguments.getShowUsage();

      batchFileArgument = new StringArgument(OPTION_LONG_BATCH_FILE_PATH,
          OPTION_SHORT_BATCH_FILE_PATH, OPTION_LONG_BATCH_FILE_PATH,
          false, false, true, INFO_BATCH_FILE_PATH_PLACEHOLDER.get(),
          null, null,
          INFO_DESCRIPTION_BATCH_FILE_PATH.get());

      displayEquivalentArgument = new BooleanArgument(
          OPTION_LONG_DISPLAY_EQUIVALENT,
          null, OPTION_LONG_DISPLAY_EQUIVALENT,
          INFO_DSCFG_DESCRIPTION_DISPLAY_EQUIVALENT.get());

      equivalentCommandFileArgument = new StringArgument(
          OPTION_LONG_EQUIVALENT_COMMAND_FILE_PATH, null,
          OPTION_LONG_EQUIVALENT_COMMAND_FILE_PATH, false, false, true,
          INFO_PATH_PLACEHOLDER.get(), null, null,
          INFO_DSCFG_DESCRIPTION_EQUIVALENT_COMMAND_FILE_PATH.get());

      propertiesFileArgument = new StringArgument("propertiesFilePath",
          null, OPTION_LONG_PROP_FILE_PATH,
          false, false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PROP_FILE_PATH.get());

      noPropertiesFileArgument = new BooleanArgument(
          "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
          INFO_DESCRIPTION_NO_PROP_FILE.get());

      // Register the global arguments.

      ArgumentGroup toolOptionsGroup = new ArgumentGroup(
          INFO_DSCFG_DESCRIPTION_OPTIONS_ARGS.get(), 2);
      parser.addGlobalArgument(advancedModeArgument, toolOptionsGroup);

      parser.addGlobalArgument(showUsageArgument);
      parser.setUsageArgument(showUsageArgument, getOutputStream());
      parser.addGlobalArgument(verboseArgument);
      parser.addGlobalArgument(quietArgument);
      parser.addGlobalArgument(scriptFriendlyArgument);
      parser.addGlobalArgument(noPromptArgument);
      parser.addGlobalArgument(batchFileArgument);
      parser.addGlobalArgument(displayEquivalentArgument);
      parser.addGlobalArgument(equivalentCommandFileArgument);
      parser.addGlobalArgument(propertiesFileArgument);
      parser.setFilePropertiesArgument(propertiesFileArgument);
      parser.addGlobalArgument(noPropertiesFileArgument);
      parser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      // Register any global arguments required by the management
      // context factory.
      factory.setRawArguments(args);
      factory.registerGlobalArguments(parser);

      globalArgumentsInitialized = true;
    }
  }



  /**
   * Registers the sub-commands with the argument parser. This method
   * uses the administration framework introspection APIs to determine
   * the overall structure of the command-line.
   *
   * @throws ArgumentException
   *           If a sub-command could not be created.
   */
  private void initializeSubCommands() throws ArgumentException {
    if (handlerFactory == null) {
      handlerFactory = new SubCommandHandlerFactory(parser);

      final Comparator<SubCommand> c = new Comparator<SubCommand>() {

        @Override
        public int compare(SubCommand o1, SubCommand o2) {
          return o1.getName().compareTo(o2.getName());
        }
      };

      Map<Tag, SortedSet<SubCommand>> groups =
        new TreeMap<Tag, SortedSet<SubCommand>>();
      SortedSet<SubCommand> allSubCommands = new TreeSet<SubCommand>(c);
      for (SubCommandHandler handler : handlerFactory
          .getAllSubCommandHandlers()) {
        SubCommand sc = handler.getSubCommand();

        handlers.put(sc, handler);
        allSubCommands.add(sc);

        // Add the sub-command to its groups.
        for (Tag tag : handler.getTags()) {
          SortedSet<SubCommand> group = groups.get(tag);
          if (group == null) {
            group = new TreeSet<SubCommand>(c);
            groups.put(tag, group);
          }
          group.add(sc);
        }
      }

      // Register the usage arguments.
      for (Map.Entry<Tag, SortedSet<SubCommand>> group : groups.entrySet()) {
        Tag tag = group.getKey();
        SortedSet<SubCommand> subCommands = group.getValue();

        String option = OPTION_LONG_HELP + "-" + tag.getName();
        String synopsis = tag.getSynopsis().toString().toLowerCase();
        BooleanArgument arg = new BooleanArgument(option, null, option,
            INFO_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE.get(synopsis));

        parser.addGlobalArgument(arg);
        parser.setUsageGroupArgument(arg, subCommands);
      }

      // Register the --help-all argument.
      String option = OPTION_LONG_HELP + "-all";
      BooleanArgument arg = new BooleanArgument(option, null, option,
          INFO_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE_ALL.get());

      parser.addGlobalArgument(arg);
      parser.setUsageGroupArgument(arg, allSubCommands);
    }
  }



  /**
   * Parses the provided command-line arguments and makes the
   * appropriate changes to the Directory Server configuration.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @return The exit code from the configuration processing. A
   *         nonzero value indicates that there was some kind of
   *         problem during the configuration processing.
   */
  private int run(String[] args) {

    // Register global arguments and sub-commands.
    try {
      initializeGlobalArguments(args);
      initializeSubCommands();
    } catch (ArgumentException e) {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(e.getMessage());
      println(message);
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

    // Check for conflicting arguments.
    if (quietArgument.isPresent() && verboseArgument.isPresent()) {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(quietArgument
          .getLongIdentifier(), verboseArgument.getLongIdentifier());
      displayMessageAndUsageReference(message);
      return 1;
    }

    if (batchFileArgument.isPresent() && !noPromptArgument.isPresent()) {
      LocalizableMessage message =
          ERR_DSCFG_ERROR_QUIET_AND_INTERACTIVE_INCOMPATIBLE.get(
              batchFileArgument.getLongIdentifier(), noPromptArgument
                  .getLongIdentifier());
      displayMessageAndUsageReference(message);
      return 1;
    }

    if (quietArgument.isPresent() && !noPromptArgument.isPresent()) {
      LocalizableMessage message = ERR_DSCFG_ERROR_QUIET_AND_INTERACTIVE_INCOMPATIBLE.get(
          quietArgument.getLongIdentifier(), noPromptArgument
          .getLongIdentifier());
      displayMessageAndUsageReference(message);
      return 1;
    }

    if (scriptFriendlyArgument.isPresent() && verboseArgument.isPresent()) {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(scriptFriendlyArgument
          .getLongIdentifier(), verboseArgument.getLongIdentifier());
      displayMessageAndUsageReference(message);
      return 1;
    }

    if (noPropertiesFileArgument.isPresent()
        && propertiesFileArgument.isPresent())
    {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          noPropertiesFileArgument.getLongIdentifier(),
          propertiesFileArgument.getLongIdentifier());
      displayMessageAndUsageReference(message);
      return 1;
    }


    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      println(e.getMessageObject());
      return 1;
    }

    // Check that we can write on the provided path where we write the
    // equivalent non-interactive commands.
    if (equivalentCommandFileArgument.isPresent())
    {
      final String file = equivalentCommandFileArgument.getValue();
      if (!canWrite(file))
      {
        println(ERR_DSCFG_CANNOT_WRITE_EQUIVALENT_COMMAND_LINE_FILE.get(file));
        return 1;
      }
      else
      {
        if (new File(file).isDirectory())
        {
          println(ERR_DSCFG_EQUIVALENT_COMMAND_LINE_FILE_DIRECTORY.get(file));
          return 1;
        }
      }
    }

    // Make sure that management context's arguments are valid.
    try {
      factory.validateGlobalArguments();
    } catch (ArgumentException e) {
      println(e.getMessageObject());
      return 1;
    }

    // Handle batch file if any
    if (batchFileArgument.isPresent()) {
      handleBatchFile(args);
      // don't need to do anything else
      return 0;
    }

    int retCode = 0;
    if (parser.getSubCommand() == null) {
      hasSubCommand = false;

      if (isInteractive()) {
        // Top-level interactive mode.
        retCode = runInteractiveMode();
      } else {
        LocalizableMessage message = ERR_ERROR_PARSING_ARGS
        .get(ERR_DSCFG_ERROR_MISSING_SUBCOMMAND.get());
        displayMessageAndUsageReference(message);
        retCode = 1;
      }
    } else {
      hasSubCommand = true;

      // Retrieve the sub-command implementation and run it.
      SubCommandHandler handler = handlers.get(parser.getSubCommand());
      retCode = runSubCommand(handler);
    }

    try {
      // Close the Management context ==> an LDAP UNBIND is sent
      factory.close();
    } catch (Exception e) {
      // Nothing to report in this case
    }

    return retCode;
  }



  /** Run the top-level interactive console. */
  private int runInteractiveMode() {

    ConsoleApplication app = this;

    // Build menu structure.
    Comparator<RelationDefinition<?, ?>> c =
      new Comparator<RelationDefinition<?, ?>>() {

      @Override
      public int compare(RelationDefinition<?, ?> rd1,
          RelationDefinition<?, ?> rd2) {
        String s1 = rd1.getUserFriendlyName().toString();
        String s2 = rd2.getUserFriendlyName().toString();

        return s1.compareToIgnoreCase(s2);
      }
    };

    final Set<RelationDefinition<?, ?>> relations =
        new TreeSet<RelationDefinition<?, ?>>(c);
    final Map<RelationDefinition<?, ?>, CreateSubCommandHandler<?, ?>> createHandlers =
        new HashMap<RelationDefinition<?, ?>, CreateSubCommandHandler<?, ?>>();
    final Map<RelationDefinition<?, ?>, DeleteSubCommandHandler> deleteHandlers =
        new HashMap<RelationDefinition<?, ?>, DeleteSubCommandHandler>();
    final Map<RelationDefinition<?, ?>, ListSubCommandHandler> listHandlers =
        new HashMap<RelationDefinition<?, ?>, ListSubCommandHandler>();
    final Map<RelationDefinition<?, ?>, GetPropSubCommandHandler> getPropHandlers =
        new HashMap<RelationDefinition<?, ?>, GetPropSubCommandHandler>();
    final Map<RelationDefinition<?, ?>, SetPropSubCommandHandler> setPropHandlers =
        new HashMap<RelationDefinition<?, ?>, SetPropSubCommandHandler>();


    for (CreateSubCommandHandler<?, ?> ch : handlerFactory
        .getCreateSubCommandHandlers()) {
      relations.add(ch.getRelationDefinition());
      createHandlers.put(ch.getRelationDefinition(), ch);
    }

    for (DeleteSubCommandHandler dh : handlerFactory
        .getDeleteSubCommandHandlers()) {
      relations.add(dh.getRelationDefinition());
      deleteHandlers.put(dh.getRelationDefinition(), dh);
    }

    for (ListSubCommandHandler lh :
      handlerFactory.getListSubCommandHandlers()) {
      relations.add(lh.getRelationDefinition());
      listHandlers.put(lh.getRelationDefinition(), lh);
    }

    for (GetPropSubCommandHandler gh : handlerFactory
        .getGetPropSubCommandHandlers()) {
      relations.add(gh.getRelationDefinition());
      getPropHandlers.put(gh.getRelationDefinition(), gh);
    }

    for (SetPropSubCommandHandler sh : handlerFactory
        .getSetPropSubCommandHandlers()) {
      relations.add(sh.getRelationDefinition());
      setPropHandlers.put(sh.getRelationDefinition(), sh);
    }

    // Main menu.
    MenuBuilder<Integer> builder = new MenuBuilder<Integer>(app);

    builder.setTitle(INFO_DSCFG_HEADING_MAIN_MENU_TITLE.get());
    builder.setPrompt(INFO_DSCFG_HEADING_MAIN_MENU_PROMPT.get());
    builder.setMultipleColumnThreshold(0);

    for (RelationDefinition<?, ?> rd : relations) {
      MenuCallback<Integer> callback = new SubMenuCallback(app, rd,
          createHandlers.get(rd), deleteHandlers.get(rd), listHandlers.get(rd),
          setPropHandlers.get(rd));
      builder.addNumberedOption(rd.getUserFriendlyName(), callback);
    }

    builder.addQuitOption();

    Menu<Integer> menu = builder.toMenu();

    try {
      // Force retrieval of management context.
      factory.getManagementContext(app);
    } catch (ArgumentException e) {
      app.println(e.getMessageObject());
      return 1;
    } catch (ClientException e) {
      app.println(e.getMessageObject());
      return 1;
    }

    try {
      app.println();
      app.println();

      MenuResult<Integer> result = menu.run();

      if (result.isQuit()) {
        return 0;
      } else {
        return result.getValue();
      }
    } catch (ClientException e) {
      app.println(e.getMessageObject());
      return 1;
    }
  }



  /** Run the provided sub-command handler. */
  private int runSubCommand(SubCommandHandler handler) {
    try {
      MenuResult<Integer> result = handler.run(this, factory);

      if (result.isSuccess()) {
        if (isInteractive() &&
            handler.isCommandBuilderUseful())
        {
          printCommandBuilder(getCommandBuilder(handler));
        }
        return result.getValue();
      } else {
        // User must have quit.
        return 1;
      }
    } catch (ArgumentException e) {
      println(e.getMessageObject());
      return 1;
    } catch (ClientException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ManagedObjectDecodingException) {
        ManagedObjectDecodingException de =
          (ManagedObjectDecodingException) cause;
        println();
        displayManagedObjectDecodingException(this, de);
        println();
      } else if (cause instanceof MissingMandatoryPropertiesException) {
        MissingMandatoryPropertiesException mmpe =
          (MissingMandatoryPropertiesException) cause;
        println();
        displayMissingMandatoryPropertyException(this, mmpe);
        println();
      } else if (cause instanceof OperationRejectedException) {
        OperationRejectedException ore = (OperationRejectedException) cause;
        println();
        displayOperationRejectedException(this, ore);
        println();
      } else {
        // Just display the default message.
        println(e.getMessageObject());
      }

      return 1;
    } catch (Exception e) {
      println(LocalizableMessage.raw(stackTraceToSingleLineString(e, true)));
      return 1;
    }
  }

  /**
   * Updates the command builder with the global options: script friendly,
   * verbose, etc. for a given sub command. It also adds systematically the
   * no-prompt option.
   *
   * @param <T>
   *          SubCommand type.
   * @param subCommand
   *          The sub command handler or common.
   * @return <T> The builded command.
   */
  <T> CommandBuilder getCommandBuilder(final T subCommand)
  {
    String commandName = System.getProperty(PROPERTY_SCRIPT_NAME);
    if (commandName == null)
    {
      commandName = DSCONFIGTOOLNAME;
    }
    CommandBuilder commandBuilder = null;
    if (subCommand instanceof SubCommandHandler)
    {
      commandBuilder =
          new CommandBuilder(commandName, ((SubCommandHandler) subCommand)
              .getSubCommand().getName());
    }
    else
    {
      commandBuilder = new CommandBuilder(commandName, (String) subCommand);
    }
    if (factory != null && factory.getContextCommandBuilder() != null)
    {
      commandBuilder.append(factory.getContextCommandBuilder());
    }

    if (verboseArgument.isPresent())
    {
      commandBuilder.addArgument(verboseArgument);
    }

    if (scriptFriendlyArgument.isPresent())
    {
      commandBuilder.addArgument(scriptFriendlyArgument);
    }

    commandBuilder.addArgument(noPromptArgument);

    if (propertiesFileArgument.isPresent())
    {
      commandBuilder.addArgument(propertiesFileArgument);
    }

    if (noPropertiesFileArgument.isPresent())
    {
      commandBuilder.addArgument(noPropertiesFileArgument);
    }

    return commandBuilder;
  }

  /**
   * Prints the contents of a command builder.  This method has been created
   * since SetPropSubCommandHandler calls it.  All the logic of DSConfig is on
   * this method.  It writes the content of the CommandBuilder to the standard
   * output, or to a file depending on the options provided by the user.
   * @param commandBuilder the command builder to be printed.
   */
  void printCommandBuilder(CommandBuilder commandBuilder)
  {
    if (displayEquivalentArgument.isPresent())
    {
      println();
      // We assume that the app we are running is this one.
      println(INFO_DSCFG_NON_INTERACTIVE.get(commandBuilder));
    }
    if (equivalentCommandFileArgument.isPresent())
    {
      String file = equivalentCommandFileArgument.getValue();
      BufferedWriter writer = null;
      try
      {
        writer = new BufferedWriter(new FileWriter(file, true));

        if (!sessionStartTimePrinted)
        {
          writer.write(SHELL_COMMENT_SEPARATOR+getSessionStartTimeMessage());
          writer.newLine();
          sessionStartTimePrinted = true;
        }

        sessionEquivalentOperationNumber++;
        writer.newLine();
        writer.write(SHELL_COMMENT_SEPARATOR+
            INFO_DSCFG_EQUIVALENT_COMMAND_LINE_SESSION_OPERATION_NUMBER.get(
                sessionEquivalentOperationNumber));
        writer.newLine();

        writer.write(SHELL_COMMENT_SEPARATOR+getCurrentOperationDateMessage());
        writer.newLine();

        writer.write(commandBuilder.toString());
        writer.newLine();
        writer.newLine();

        writer.flush();
      }
      catch (IOException ioe)
      {
        println(ERR_DSCFG_ERROR_WRITING_EQUIVALENT_COMMAND_LINE.get(file, ioe));
      }
      finally
      {
        closeSilently(writer);
      }
    }
  }

  /**
   * Returns the message to be displayed in the file with the equivalent
   * command-line with information about when the session started.
   * @return  the message to be displayed in the file with the equivalent
   * command-line with information about when the session started.
   */
  private String getSessionStartTimeMessage()
  {
    String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    if (scriptName == null || scriptName.length() == 0)
    {
      scriptName = DSCONFIGTOOLNAME;
    }
    final String date = formatDateTimeStringForEquivalentCommand(
        new Date(sessionStartTime));
    return INFO_DSCFG_SESSION_START_TIME_MESSAGE.get(scriptName, date).
    toString();
  }

  private void handleBatchFile(String[] args) {

    BufferedReader bReader = null;
    try {
      // Build a list of initial arguments,
      // removing the batch file option + its value
      List<String> initialArgs = new ArrayList<String>();
      Collections.addAll(initialArgs, args);
      int batchFileArgIndex = -1;
      for (String elem : initialArgs) {
        if (elem.startsWith("-" + OPTION_SHORT_BATCH_FILE_PATH)
                || elem.contains(OPTION_LONG_BATCH_FILE_PATH)) {
          batchFileArgIndex = initialArgs.indexOf(elem);
          break;
        }
      }
      if (batchFileArgIndex != -1) {
        // Remove both the batch file arg and its value
        initialArgs.remove(batchFileArgIndex);
        initialArgs.remove(batchFileArgIndex);
      }
      String batchFilePath = batchFileArgument.getValue().trim();
      bReader =
              new BufferedReader(new FileReader(batchFilePath));
      String line;
      String command = "";
      // Split the CLI string into arguments array
      while ((line = bReader.readLine()) != null) {
         if ("".equals(line) || line.startsWith("#")) {
          // Empty line or comment
          continue;
        }
       // command split in several line support
        if (line.endsWith("\\")) {
          // command is split into several lines
          command += line.substring(0, line.length() - 1);
          continue;
        } else {
          command += line;
        }
        command = command.trim();
        // string between quotes support
        command = replaceSpacesInQuotes(command);
        String displayCommand = new String(command);

        // "\ " support
        command = command.replace("\\ ", "##");
        displayCommand = displayCommand.replace("\\ ", " ");

        String[] fileArguments = command.split("\\s+");
        // reset command
        command = "";
        for (int ii = 0; ii < fileArguments.length; ii++) {
          fileArguments[ii] = fileArguments[ii].replace("##", " ");
        }

        errPrintln(LocalizableMessage.raw(displayCommand));

        // Append initial arguments to the file line
        List<String> allArguments = new ArrayList<String>();
        Collections.addAll(allArguments, fileArguments);
        allArguments.addAll(initialArgs);
        String[] allArgsArray = allArguments.toArray(new String[]{});

        int exitCode = main(allArgsArray, getOutputStream(), getErrorStream());
        if (exitCode != 0)
        {
          System.exit(filterExitCode(exitCode));
        }
        errPrintln();
      }
    } catch (IOException ex) {
      println(ERR_DSCFG_ERROR_READING_BATCH_FILE.get(ex));
    } finally {
      closeSilently(bReader);
    }
  }

  /** Replace spaces in quotes by "\ ". */
  private String replaceSpacesInQuotes(String line) {
    String newLine = "";
    boolean inQuotes = false;
    for (int ii = 0; ii < line.length(); ii++) {
      char ch = line.charAt(ii);
      if (ch == '\"' || ch == '\'') {
        inQuotes = !inQuotes;
        continue;
      }
      if (inQuotes && ch == ' ') {
        newLine += "\\ ";
      } else {
        newLine += ch;
      }
    }
    return newLine;
  }
}
