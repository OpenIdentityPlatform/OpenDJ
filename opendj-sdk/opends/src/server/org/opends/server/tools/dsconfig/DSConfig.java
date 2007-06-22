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



import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
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
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;



/**
 * This class provides a command-line tool which enables
 * administrators to configure the Directory Server.
 */
public final class DSConfig {

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
    DSConfig app = new DSConfig(System.in, System.out, System.err,
        new LDAPManagementContextFactory());
    // Only initialize the client environment when run as a standalone
    // application.
    try {
      app.initializeClientEnvironment();
    } catch (InitializationException e) {
      // TODO: is this ok as an error message?
      System.err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      System.exit(1);
    }

    // Run the application.
    int exitCode = app.run(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  // Flag indicating whether or not the application environment has
  // already been initialized.
  private boolean environmentInitialized = false;

  // The error stream which this application should use.
  private final PrintStream err;

  // The factory which the application should use to retrieve its
  // management context.
  private final ManagementContextFactory factory;

  // Flag indicating whether or not the global arguments have
  // already been initialized.
  private boolean globalArgumentsInitialized = false;

  // Mapping of sub-commands to their implementations;
  private final Map<SubCommand, SubCommandHandler> handlers =
    new HashMap<SubCommand, SubCommandHandler>();

  // The input stream reader which this application should use.
  private final BufferedReader in;

  // The argument which should be used to request interactive
  // behavior.
  private BooleanArgument interactiveArgument;

  // The output stream which this application should use.
  private final PrintStream out;

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
    this.parser = new SubCommandArgumentParser(this.getClass().getName(),
        getMessage(MSGID_CONFIGDS_TOOL_DESCRIPTION), false);

    if (in != null) {
      this.in = new BufferedReader(new InputStreamReader(in));
    } else {
      this.in = new BufferedReader(new Reader() {

        @Override
        public void close() throws IOException {
          // Do nothing.
        }



        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
          return -1;
        }

      });
    }

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

    this.factory = factory;
  }



  /**
   * Interactively confirms whether a user wishes to perform an
   * action. If the application is non-interactive, then the action is
   * granted by default.
   *
   * @param prompt
   *          The prompt describing the action.
   * @return Returns <code>true</code> if the user wishes the action
   *         to be performed, or <code>false</code> if they refused,
   *         or if an exception occurred.
   */
  public boolean confirmAction(String prompt) {
    if (!isInteractive()) {
      return true;
    }

    String yes = Messages.getString("general.yes");
    String no = Messages.getString("general.no");
    String errMsg = Messages.getString("general.confirm.error");
    String error = String.format(errMsg, yes, no);
    prompt = prompt + String.format(" (%s / %s): ", yes, no);

    while (true) {
      String response;
      try {
        response = readLineOfInput(prompt);
      } catch (Exception e) {
        return false;
      }

      if (response == null) {
        // End of input.
        return false;
      }

      response = response.toLowerCase().trim();
      if (response.length() == 0) {
        // Empty input.
        err.println(wrapText(error, MAX_LINE_WIDTH));
      } else if (no.startsWith(response)) {
        return false;
      } else if (yes.startsWith(response)) {
        return true;
      } else {
        // Try again...
        err.println(wrapText(error, MAX_LINE_WIDTH));
      }
    }
  }



  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  public void displayMessage(String msg) {
    err.println(wrapText(msg, MAX_LINE_WIDTH));
  }



  /**
   * Displays a message to the error stream if verbose mode is
   * enabled.
   *
   * @param msg
   *          The verbose message.
   */
  public void displayVerboseMessage(String msg) {
    if (isVerbose()) {
      err.println(wrapText(msg, MAX_LINE_WIDTH));
    }
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
   * Indicates whether or not the user has requested interactive
   * behavior.
   *
   * @return Returns <code>true</code> if the user has requested
   *         interactive behavior.
   */
  public boolean isInteractive() {
    return interactiveArgument.isPresent();
  }



  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         quiet output.
   */
  public boolean isQuiet() {
    return quietArgument.isPresent();
  }



  /**
   * Indicates whether or not the user has requested script-friendly
   * output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         script-friendly output.
   */
  public boolean isScriptFriendly() {
    return scriptFriendlyArgument.isPresent();
  }



  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         verbose output.
   */
  public boolean isVerbose() {
    return verboseArgument.isPresent();
  }



  /**
   * Interactively retrieves a line of input from the console.
   *
   * @param prompt
   *          The prompt.
   * @return Returns the line of input, or <code>null</code> if the
   *         end of input has been reached.
   * @throws Exception
   *           If the line of input could not be retrieved for some
   *           reason.
   */
  public String readLineOfInput(String prompt) throws Exception {
    err.print(wrapText(prompt, MAX_LINE_WIDTH));
    return in.readLine();
  }



  /**
   * Interactively retrieves a password from the console.
   *
   * @param prompt
   *          The password prompt.
   * @return Returns the password.
   * @throws Exception
   *           If the password could not be retrieved for some reason.
   */
  public String readPassword(String prompt) throws Exception {
    err.print(wrapText(prompt, MAX_LINE_WIDTH));
    char[] pwChars = PasswordReader.readPassword();
    return new String(pwChars);
  }



  /**
   * Gets the management context which sub-commands should use in
   * order to manage the directory server.
   *
   * @return Returns the management context which sub-commands should
   *         use in order to manage the directory server.
   * @throws ArgumentException
   *           If a management context related argument could not be
   *           parsed successfully.
   * @throws ClientException
   *           If the management context could not be created.
   */
  ManagementContext getManagementContext() throws ArgumentException,
      ClientException {
    return factory.getManagementContext(this);
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
          MSGID_DESCRIPTION_VERBOSE);

      quietArgument = new BooleanArgument("quiet", 'q', "quiet",
          MSGID_DESCRIPTION_QUIET);

      scriptFriendlyArgument = new BooleanArgument("script-friendly", 's',
          "script-friendly", MSGID_DESCRIPTION_SCRIPT_FRIENDLY);

      interactiveArgument = new BooleanArgument("interactive", 'i',
          "interactive", MSGID_DESCRIPTION_INTERACTIVE);

      showUsageArgument = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
          OPTION_LONG_HELP, MSGID_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE_SUMMARY);

      // Register the global arguments.
      parser.addGlobalArgument(showUsageArgument);
      parser.setUsageArgument(showUsageArgument, out);
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
      for (SubCommandHandler handler : builder.getSubCommandHandlers(parser)) {
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
        String synopsis = tag.getSynopsis().toLowerCase();
        BooleanArgument arg = new BooleanArgument(option, null, option,
            MSGID_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE, synopsis);

        parser.addGlobalArgument(arg);
        parser.setUsageGroupArgument(arg, subCommands);
      }

      // Register the --help-all argument.
      String option = OPTION_LONG_HELP + "-all";
      BooleanArgument arg = new BooleanArgument(option, null, option,
          MSGID_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE_ALL);

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
      int msgID = MSGID_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, e.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try {
      parser.parseArguments(args);
    } catch (ArgumentException ae) {
      int msgID = MSGID_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println();
      err.println(parser.getHelpUsageReference());
      return 1;
    }

    // If the usage/version argument was provided, then we don't need
    // to do anything else.
    if (parser.usageOrVersionDisplayed()) {
      return 0;
    }

    // Make sure that we have a sub-command.
    if (parser.getSubCommand() == null) {
      int msgID = MSGID_ERROR_PARSING_ARGS;
      String message = getMessage(msgID,
          getMessage(MSGID_DSCFG_ERROR_MISSING_SUBCOMMAND));
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println();
      err.println(parser.getHelpUsageReference());
      return 1;
    }

    if (quietArgument.isPresent() && verboseArgument.isPresent()) {
      int msgID = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, quietArgument.getLongIdentifier(),
          verboseArgument.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println();
      err.println(parser.getHelpUsageReference());
      return 1;
    }

    if (quietArgument.isPresent() && interactiveArgument.isPresent()) {
      int msgID = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, quietArgument.getLongIdentifier(),
          interactiveArgument.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println();
      err.println(parser.getHelpUsageReference());
      return 1;
    }

    if (scriptFriendlyArgument.isPresent() && verboseArgument.isPresent()) {
      int msgID = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, scriptFriendlyArgument
          .getLongIdentifier(), verboseArgument.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println();
      err.println(parser.getHelpUsageReference());
      return 1;
    }

    // Make sure that management context's arguments are valid.
    try {
      factory.validateGlobalArguments();
    } catch (ArgumentException e) {
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    // Retrieve the sub-command implementation and run it.
    SubCommandHandler handler = handlers.get(parser.getSubCommand());
    try {
      return handler.run(this, out, err);
    } catch (ArgumentException e) {
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    } catch (ClientException e) {
      // If the client exception was caused by a decoding exception
      // then we should display the causes.
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));

      Throwable cause = e.getCause();
      if (cause instanceof ManagedObjectDecodingException) {
        // FIXME: use a table.
        ManagedObjectDecodingException de =
          (ManagedObjectDecodingException) cause;

        err.println();
        for (PropertyException pe : de.getCauses()) {
          AbstractManagedObjectDefinition<?, ?> d = de
              .getPartialManagedObject().getManagedObjectDefinition();
          ArgumentException ae = ArgumentExceptionFactory
              .adaptPropertyException(pe, d);
          err.println(wrapText(" * " + ae.getMessage(), MAX_LINE_WIDTH));
        }
        err.println();
      }

      return 1;
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      err.println(wrapText(StaticUtils.stackTraceToString(e), MAX_LINE_WIDTH));
      return 1;
    }
  }
}
