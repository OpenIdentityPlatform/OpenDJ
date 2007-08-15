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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools.dsconfig;
import org.opends.messages.Message;



import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AttributeTypePropertyDefinition;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.Tag;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.tools.ClientException;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;



/**
 * This class provides a command-line tool which enables
 * administrators to configure the Directory Server.
 */
public final class DSConfig extends ConsoleApplication {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



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
    DSConfig app = new DSConfig(System.in, outStream, errStream,
        new LDAPManagementContextFactory());
    // Only initialize the client environment when run as a standalone
    // application.
    if (initializeServer) {
      try {
        app.initializeClientEnvironment();
      } catch (InitializationException e) {
        // TODO: is this ok as an error message?
        app.printMessage(e.getMessageObject());
        return 1;
      }
    }

    // Run the application.
    return app.run(args);
  }

  // Flag indicating whether or not the application environment has
  // already been initialized.
  private boolean environmentInitialized = false;

  // The factory which the application should use to retrieve its
  // management context.
  private final ManagementContextFactory factory;

  // Flag indicating whether or not the global arguments have
  // already been initialized.
  private boolean globalArgumentsInitialized = false;

  // Mapping of sub-commands to their implementations;
  private final Map<SubCommand, SubCommandHandler> handlers =
    new HashMap<SubCommand, SubCommandHandler>();

  // The argument which should be used to request interactive
  // behavior.
  private BooleanArgument interactiveArgument;

  // The command-line argument parser.
  private final SubCommandArgumentParser parser;

  // The argument which should be used to request quiet output.
  private BooleanArgument quietArgument;

  // The argument which should be used to request script-friendly
  // output.
  private BooleanArgument scriptFriendlyArgument;

  // The argument which should be used to request usage information.
  private BooleanArgument showUsageArgument;

  // Flag indicating whether or not the sub-commands have
  // already been initialized.
  private boolean subCommandsInitialized = false;

  // The argument which should be used to request verbose output.
  private BooleanArgument verboseArgument;



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
  public DSConfig(InputStream in, OutputStream out, OutputStream err,
      ManagementContextFactory factory) {
    super(in, out, err);

    this.parser = new SubCommandArgumentParser(this.getClass().getName(),
        INFO_CONFIGDS_TOOL_DESCRIPTION.get(), false);

    this.factory = factory;
  }



  /**
   * Initializes core APIs for use when dsconfig will be run as a
   * standalone application.
   *
   * @throws InitializationException
   *           If the core APIs could not be initialized.
   */
  public void initializeClientEnvironment() throws InitializationException {
    if (environmentInitialized == false) {
      // TODO: do we need to do this?
      DirectoryServer.bootstrapClient();

      // Bootstrap definition classes.
      ClassLoaderProvider.getInstance().enable();

      // Switch off class name validation in client.
      ClassPropertyDefinition.setAllowClassValidation(false);

      // Switch off attribute type name validation in client.
      AttributeTypePropertyDefinition.setCheckSchema(false);

      environmentInitialized = true;
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isInteractive() {
    return interactiveArgument.isPresent();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isQuiet() {
    return quietArgument.isPresent();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isScriptFriendly() {
    return scriptFriendlyArgument.isPresent();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isVerbose() {
    return verboseArgument.isPresent();
  }



  /**
   * {@inheritDoc}
   */
  public ManagementContext getManagementContext() throws ArgumentException,
      ClientException {
    return factory.getManagementContext(this);
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
    if (globalArgumentsInitialized == false) {
      verboseArgument = new BooleanArgument("verbose", 'v', "verbose",
          INFO_DESCRIPTION_VERBOSE.get());

      quietArgument = new BooleanArgument("quiet", 'q', "quiet",
          INFO_DESCRIPTION_QUIET.get());

      scriptFriendlyArgument = new BooleanArgument("script-friendly", 's',
          "script-friendly", INFO_DESCRIPTION_SCRIPT_FRIENDLY.get());

      interactiveArgument = new BooleanArgument("interactive", 'i',
          "interactive", INFO_DESCRIPTION_INTERACTIVE.get());

      showUsageArgument = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
              OPTION_LONG_HELP,
              INFO_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE_SUMMARY.get());

      // Register the global arguments.
      parser.addGlobalArgument(showUsageArgument);
      parser.setUsageArgument(showUsageArgument, getOutputStream());
      parser.addGlobalArgument(verboseArgument);
      parser.addGlobalArgument(quietArgument);
      parser.addGlobalArgument(scriptFriendlyArgument);
      parser.addGlobalArgument(interactiveArgument);

      // Register any global arguments required by the management
      // context factory.
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
    if (subCommandsInitialized == false) {
      Comparator<SubCommand> c = new Comparator<SubCommand>() {

        public int compare(SubCommand o1, SubCommand o2) {
          return o1.getName().compareTo(o2.getName());
        }
      };

      SubCommandBuilder builder = new SubCommandBuilder();
      Map<Tag, SortedSet<SubCommand>> groups =
        new TreeMap<Tag, SortedSet<SubCommand>>();
      SortedSet<SubCommand> allSubCommands = new TreeSet<SubCommand>(c);
      for (SubCommandHandler handler : builder.getSubCommandHandlers(this,
          parser)) {
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

      subCommandsInitialized = true;
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

    // Make sure that we have a sub-command.
    if (parser.getSubCommand() == null) {
      Message message = ERR_ERROR_PARSING_ARGS.get(
              ERR_DSCFG_ERROR_MISSING_SUBCOMMAND.get());
      displayMessageAndUsageReference(message);
      return 1;
    }

    if (quietArgument.isPresent() && verboseArgument.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              quietArgument.getLongIdentifier(),
          verboseArgument.getLongIdentifier());
      displayMessageAndUsageReference(message);
      return 1;
    }

    if (quietArgument.isPresent() && interactiveArgument.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              quietArgument.getLongIdentifier(),
          interactiveArgument.getLongIdentifier());
      displayMessageAndUsageReference(message);
      return 1;
    }

    if (scriptFriendlyArgument.isPresent() && verboseArgument.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(scriptFriendlyArgument
          .getLongIdentifier(), verboseArgument.getLongIdentifier());
      displayMessageAndUsageReference(message);
      return 1;
    }

    // Make sure that management context's arguments are valid.
    try {
      factory.validateGlobalArguments();
    } catch (ArgumentException e) {
      printMessage(e.getMessageObject());
      return 1;
    }

    // Retrieve the sub-command implementation and run it.
    SubCommandHandler handler = handlers.get(parser.getSubCommand());
    try {
      return handler.run();
    } catch (ArgumentException e) {
      printMessage(e.getMessageObject());
      return 1;
    } catch (ClientException e) {
      // If the client exception was caused by a decoding exception
      // then we should display the causes.
      printMessage(e.getMessageObject());

      Throwable cause = e.getCause();
      if (cause instanceof ManagedObjectDecodingException) {
        ManagedObjectDecodingException de =
          (ManagedObjectDecodingException) cause;

        printMessage(Message.EMPTY);
        TableBuilder builder = new TableBuilder();
        for (PropertyException pe : de.getCauses()) {
          AbstractManagedObjectDefinition<?, ?> d = de
              .getPartialManagedObject().getManagedObjectDefinition();
          ArgumentException ae = ArgumentExceptionFactory
              .adaptPropertyException(pe, d);
          builder.startRow();
          builder.appendCell("*");
          builder.appendCell(ae.getMessage());
        }

        TextTablePrinter printer = new TextTablePrinter(getErrorStream());
        printer.setDisplayHeadings(false);
        printer.setColumnWidth(1, 0);
        builder.print(printer);
        printMessage(Message.EMPTY);
      }

      return 1;
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      printMessage(Message.raw(StaticUtils.stackTraceToString(e)));
      return 1;
    }
  }
}
