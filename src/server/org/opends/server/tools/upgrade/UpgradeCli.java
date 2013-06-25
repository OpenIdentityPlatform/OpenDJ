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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.tools.upgrade;

import static org.opends.messages.ToolMessages.*;

import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.StaticUtils.filterExitCode;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static org.opends.server.tools.upgrade.FormattedNotificationCallback.*;
import static org.opends.server.tools.upgrade.Upgrade.EXIT_CODE_ERROR;
import static org.opends.server.tools.upgrade.Upgrade.EXIT_CODE_SUCCESS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.opends.messages.Message;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.tools.ClientException;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;

/**
 * This class provides the CLI used for upgrading the OpenDJ product.
 */
public final class UpgradeCli extends ConsoleApplication implements
    CallbackHandler
{
  /**
   * Upgrade's logger.
   */
  static private final Logger LOG = Logger
      .getLogger(UpgradeCli.class.getName());

  // The command-line argument parser.
  private final SubCommandArgumentParser parser;

  // The argument which should be used to specify the config class.
  private StringArgument configClass;

  // The argument which should be used to specify the config file.
  private StringArgument configFile;

  //The argument which should be used to specify non interactive mode.
  private BooleanArgument noPrompt;
  private BooleanArgument ignoreErrors;
  private BooleanArgument force;
  private BooleanArgument quietMode;
  private BooleanArgument verbose;
  private BooleanArgument acceptLicense;


  // The argument which should be used to request usage information.
  private BooleanArgument showUsageArgument;

  // Flag indicating whether or not the global arguments have
  // already been initialized.
  private boolean globalArgumentsInitialized = false;

  private UpgradeCli(InputStream in, OutputStream out, OutputStream err)
  {
    super(in, out, err);

    this.parser =
        new SubCommandArgumentParser(this.getClass().getName(),
            INFO_UPGRADE_DESCRIPTION_CLI.get(), false);

  }

  /**
   * Provides the command-line arguments to the main application for processing.
   *
   * @param args
   *          The set of command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    final int exitCode = main(args, true, System.out, System.err);
    if (exitCode != 0)
    {
      System.exit(filterExitCode(exitCode));
    }
  }

  /**
   * Provides the command-line arguments to the main application for processing
   * and returns the exit code as an integer.
   *
   * @param args
   *          The set of command-line arguments provided to this program.
   * @param initializeServer
   *          Indicates whether to perform basic initialization (which should
   *          not be done if the tool is running in the same JVM as the server).
   * @param outStream
   *          The output stream for standard output.
   * @param errStream
   *          The output stream for standard error.
   * @return Zero to indicate that the program completed successfully, or
   *         non-zero to indicate that an error occurred.
   */
  public static int main(String[] args, boolean initializeServer,
      OutputStream outStream, OutputStream errStream)
  {
    final UpgradeCli app = new UpgradeCli(System.in, outStream, errStream);

    // Run the application.
    return app.run(args, initializeServer);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAdvancedMode()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInteractive()
  {
    return !noPrompt.isPresent();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMenuDrivenMode()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isQuiet()
  {
    return quietMode.isPresent();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isScriptFriendly()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isVerbose()
  {
    return verbose.isPresent();
  }

  /**
   * Force the upgrade. All critical questions will be forced to 'yes'.
   *
   * @return {@code true} if the upgrade process is forced.
   */
  public boolean isForceUpgrade()
  {
    return force.isPresent();
  }

  /**
   * Force to ignore the errors during the upgrade process.
   * Continues rather than fails.
   *
   * @return {@code true} if the errors are forced to be ignored.
   */
  public boolean isIgnoreErrors()
  {
    return ignoreErrors.isPresent();
  }

  /**
   * Automatically accepts the license if it's present.
   *
   * @return {@code true} if license is accepted by default.
   */
  public boolean isAcceptLicense()
  {
    return acceptLicense.isPresent();
  }

  // Displays the provided message followed by a help usage reference.
  private void displayMessageAndUsageReference(final Message message)
  {
    println(message);
    println();
    println(parser.getHelpUsageReference());
  }

  // Initialize arguments provided by the command line.
  private void initializeGlobalArguments() throws ArgumentException
  {
    if (!globalArgumentsInitialized)
    {
      configClass =
          new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
              OPTION_LONG_CONFIG_CLASS, true, false, true,
              INFO_CONFIGCLASS_PLACEHOLDER.get(), ConfigFileHandler.class
                  .getName(), null, INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);

      configFile =
          new StringArgument("configfile", 'f', "configFile", true, false,
              true, INFO_CONFIGFILE_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);

      noPrompt =
          new BooleanArgument(OPTION_LONG_NO_PROMPT, OPTION_SHORT_NO_PROMPT,
              OPTION_LONG_NO_PROMPT, INFO_UPGRADE_DESCRIPTION_NO_PROMPT.get());

      verbose =
          new BooleanArgument(OPTION_LONG_VERBOSE, OPTION_SHORT_VERBOSE,
              OPTION_LONG_VERBOSE, INFO_DESCRIPTION_VERBOSE.get());

      quietMode =
          new BooleanArgument(OPTION_LONG_QUIET, OPTION_SHORT_QUIET,
              OPTION_LONG_QUIET, INFO_DESCRIPTION_QUIET.get());

      ignoreErrors =
          new BooleanArgument(OPTION_LONG_IGNORE_ERRORS, null,
              OPTION_LONG_IGNORE_ERRORS, INFO_UPGRADE_OPTION_IGNORE_ERRORS
                  .get());

      force = new BooleanArgument(OPTION_LONG_FORCE_UPGRADE, null,
          OPTION_LONG_FORCE_UPGRADE,
          INFO_UPGRADE_OPTION_FORCE.get(OPTION_LONG_NO_PROMPT));

      acceptLicense = new BooleanArgument(OPTION_LONG_ACCEPT_LICENSE, null,
          OPTION_LONG_ACCEPT_LICENSE, INFO_OPTION_ACCEPT_LICENSE.get());

      showUsageArgument =
          new BooleanArgument("help", OPTION_SHORT_HELP, OPTION_LONG_HELP,
              INFO_DESCRIPTION_USAGE.get());


      // Register the global arguments.
      parser.addGlobalArgument(showUsageArgument);
      parser.setUsageArgument(showUsageArgument, this.getOutputStream());
      parser.addGlobalArgument(configClass);
      parser.addGlobalArgument(configFile);
      parser.addGlobalArgument(noPrompt);
      parser.addGlobalArgument(verbose);
      parser.addGlobalArgument(quietMode);
      parser.addGlobalArgument(force);
      parser.addGlobalArgument(ignoreErrors);
      parser.addGlobalArgument(acceptLicense);

      globalArgumentsInitialized = true;
    }
  }

  private int run(String[] args, boolean initializeServer)
  {
    // Initialize the arguments
    try
    {
      initializeGlobalArguments();
    }
    catch (ArgumentException e)
    {
      final Message message = ERR_CANNOT_INITIALIZE_ARGS.get(e.getMessage());
      this.getOutputStream().print(message);
      return EXIT_CODE_ERROR;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      parser.parseArguments(args);
      if (isInteractive() && isQuiet())
      {
        final Message message =
            ERR_UPGRADE_INCOMPATIBLE_ARGS.get(OPTION_LONG_QUIET,
                "interactive mode");
        this.getOutputStream().println(message);
        return EXIT_CODE_ERROR;
      }
      if (isInteractive() && isForceUpgrade())
      {
        final Message message =
            ERR_UPGRADE_INCOMPATIBLE_ARGS.get(OPTION_LONG_FORCE_UPGRADE,
                "interactive mode");
        this.getOutputStream().println(message);
        return EXIT_CODE_ERROR;
      }
      if (isQuiet() && isVerbose())
      {
        final Message message =
            ERR_UPGRADE_INCOMPATIBLE_ARGS.get(OPTION_LONG_QUIET,
                OPTION_LONG_VERBOSE);
        this.getOutputStream().println(message);
        return EXIT_CODE_ERROR;
      }
    }
    catch (ArgumentException ae)
    {
      final Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      displayMessageAndUsageReference(message);
      return EXIT_CODE_ERROR;
    }

    // If the usage/version argument was provided, then we don't need
    // to do anything else.
    if (parser.usageOrVersionDisplayed())
    {
      return EXIT_CODE_SUCCESS;
    }

    // Main process
    try
    {
      // Creates the log file.
      UpgradeLog.initLogFileHandler();

      // Upgrade's context.
      UpgradeContext context =
          new UpgradeContext(BuildVersion.instanceVersion(), BuildVersion
              .binaryVersion(), this);

      context.setIgnoreErrorsMode(isIgnoreErrors());
      context.setAcceptLicenseMode(isAcceptLicense());
      context.setInteractiveMode(isInteractive());
      context.setForceUpgradeMode(isForceUpgrade());

      // Starts upgrade.
      Upgrade.upgrade(context);
    }
    catch (ClientException ex)
    {
      LOG.log(SEVERE, ex.getMessage());
      println(Style.ERROR, ex.getMessageObject(), 0);

      return ex.getExitCode();
    }
    catch (Exception ex)
    {
      LOG.log(SEVERE, ERR_UPGRADE_MAIN_UPGRADE_PROCESS.get(ex
          .getMessage()).toString());
      println(Style.ERROR, ERR_UPGRADE_MAIN_UPGRADE_PROCESS.get(ex
          .getMessage()), 0);

      return EXIT_CODE_ERROR;
    }
    return EXIT_CODE_SUCCESS;
  }

  /** {@inheritDoc} */
  @Override
  public void handle(Callback[] callbacks) throws IOException,
      UnsupportedCallbackException
  {
    for (final Callback c : callbacks)
    {
      // Displays progress eg. for a task.
      if (c instanceof ProgressNotificationCallback)
      {
        final ProgressNotificationCallback pnc =
            (ProgressNotificationCallback) c;
        final Message msg = Message.raw("  " + pnc.getMessage());
        printProgress(msg);
        printProgressBar(msg.length(), pnc.getProgress());
      }
      else if (c instanceof FormattedNotificationCallback)
      {
        // Displays formatted notifications.
        final FormattedNotificationCallback fnc =
            (FormattedNotificationCallback) c;
        LOG.log(INFO, fnc.getMessage());
        switch (fnc.getMessageSubType())
        {
        case TITLE_CALLBACK:
          println(Style.TITLE, Message.raw(fnc.getMessage()), 0);
          break;
        case SUBTITLE_CALLBACK:
          println(Style.SUBTITLE, Message.raw(fnc.getMessage()),
              4);
          break;
        case NOTICE_CALLBACK:
          println(Style.NOTICE, Message.raw(fnc.getMessage()), 1);
          break;
        case ERROR_CALLBACK:
          println(Style.ERROR, Message.raw(fnc.getMessage()), 1);
          break;
        case BREAKLINE:
          println(Style.BREAKLINE, Message.raw(fnc.getMessage()), 1);
          break;
        default:
          LOG.log(SEVERE, "Unsupported message type: "
            + fnc.getMessage());
          throw new IOException("Unsupported message type: ");
        }
      }
      else if (c instanceof TextOutputCallback)
      {
        // Usual output text.
        final TextOutputCallback toc = (TextOutputCallback) c;
        if(toc.getMessageType() == TextOutputCallback.INFORMATION) {
          LOG.log(INFO, toc.getMessage());
          printlnProgress(Message.raw(toc.getMessage()));
        } else {
          LOG.log(SEVERE, "Unsupported message type: "
            + toc.getMessage());
          throw new IOException("Unsupported message type: ");
        }
      }
      else if (c instanceof ConfirmationCallback)
      {
        final ConfirmationCallback cc = (ConfirmationCallback) c;
        List<String> choices = new ArrayList<String>();

        final String defaultOption =
            UpgradeContext.getDefaultOption(cc.getDefaultOption());

        StringBuilder prompt =
            new StringBuilder(StaticUtils.wrapText(cc.getPrompt(),
                ServerConstants.MAX_LINE_WIDTH, 2));

        // Default answers.
        final List<String> yesNoDefaultResponses =
            StaticUtils.arrayToList(new String[] {
              INFO_PROMPT_YES_COMPLETE_ANSWER.get().toString(),
              INFO_PROMPT_YES_FIRST_LETTER_ANSWER.get().toString(),
              INFO_PROMPT_NO_COMPLETE_ANSWER.get().toString(),
              INFO_PROMPT_NO_FIRST_LETTER_ANSWER.get().toString() });

        // Generating prompt and possible answers list.
        prompt.append(" ").append("(");
        if (cc.getOptionType() == ConfirmationCallback.YES_NO_OPTION)
        {
          choices.addAll(yesNoDefaultResponses);
          prompt.append(INFO_PROMPT_YES_COMPLETE_ANSWER.get().toString())
              .append("/")
              .append(INFO_PROMPT_NO_COMPLETE_ANSWER.get().toString());
        }
        else if (cc.getOptionType()
            == ConfirmationCallback.YES_NO_CANCEL_OPTION)
        {
          choices.addAll(yesNoDefaultResponses);
          choices.addAll(StaticUtils
              .arrayToList(new String[] { INFO_TASKINFO_CMD_CANCEL_CHAR.get()
                  .toString() }));

          prompt.append(" ").append("(").append(
              INFO_PROMPT_YES_COMPLETE_ANSWER.get().toString()).append("/")
              .append(INFO_PROMPT_NO_COMPLETE_ANSWER.get().toString())
              .append("/").append(
                  INFO_TASKINFO_CMD_CANCEL_CHAR.get().toString());
        }
        prompt.append(")");

        LOG.log(INFO, cc.getPrompt());

        // Displays the output and
        // while it hasn't a valid response, question is repeated.
        if (isInteractive())
        {
          while (true)
          {
            String value = null;
            try
            {
              value =
                  readInput(Message.raw(prompt), defaultOption,
                      Style.SUBTITLE);
            }
            catch (CLIException e)
            {
              LOG.log(SEVERE, e.getMessage());
              break;
            }

            if ((value.toLowerCase().equals(
                INFO_PROMPT_YES_FIRST_LETTER_ANSWER.get().toString()) || value
                .toLowerCase().equals(
                    INFO_PROMPT_YES_COMPLETE_ANSWER.get().toString()))
                && choices.contains(value))
            {
              cc.setSelectedIndex(ConfirmationCallback.YES);
              break;
            }
            else if ((value.toLowerCase().equals(
                INFO_PROMPT_NO_FIRST_LETTER_ANSWER.get().toString()) || value
                .toLowerCase().equals(
                    INFO_PROMPT_NO_COMPLETE_ANSWER.get().toString()))
                && choices.contains(value))
            {
              cc.setSelectedIndex(ConfirmationCallback.NO);
              break;
            }
            else if ((value.toLowerCase().equals(INFO_TASKINFO_CMD_CANCEL_CHAR
                .get().toString()))
                && choices.contains(value))
            {
              cc.setSelectedIndex(ConfirmationCallback.CANCEL);
              break;
            }
            LOG.log(INFO, value);
          }
        }
        else // Non interactive mode :
        {
          // Force mode.
          if (isForceUpgrade())
          {
            cc.setSelectedIndex(ConfirmationCallback.YES);
          }
          else // Default non interactive mode.
          {
            cc.setSelectedIndex(cc.getDefaultOption());
          }
          // Displays the prompt
          prompt.append(" ").append(
              UpgradeContext.getDefaultOption(cc.getSelectedIndex()));
          println(Style.SUBTITLE, Message.raw(prompt), 0);
          LOG.log(INFO, UpgradeContext.getDefaultOption(cc.getSelectedIndex()));
        }
      }
      else
      {
        LOG.log(SEVERE, "Unrecognized Callback");
        throw new UnsupportedCallbackException(c, "Unrecognized Callback");
      }
    }
  }
}
