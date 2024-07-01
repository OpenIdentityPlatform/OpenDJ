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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools.upgrade;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static javax.security.auth.callback.TextOutputCallback.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.upgrade.FormattedNotificationCallback.*;
import static org.opends.server.tools.upgrade.Upgrade.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.messages.RuntimeMessages;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommandArgumentParser;

/**
 * This class provides the CLI used for upgrading the OpenDJ product.
 */
public final class UpgradeCli extends ConsoleApplication implements
    CallbackHandler
{
  /** Upgrade's logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The command-line argument parser. */
  private final SubCommandArgumentParser parser;

  /** The argument which should be used to specify the config file. */
  private StringArgument configFile;

  /** The argument which should be used to specify non interactive mode. */
  private BooleanArgument noPrompt;
  private BooleanArgument ignoreErrors;
  private BooleanArgument force;
  private BooleanArgument quietMode;
  private BooleanArgument verbose;
  private BooleanArgument acceptLicense;

  /** The argument which should be used to request usage information. */
  private BooleanArgument showUsageArgument;

  /** Flag indicating whether the global arguments have already been initialized. */
  private boolean globalArgumentsInitialized;

  private UpgradeCli(InputStream in, OutputStream out, OutputStream err)
  {
    super(new PrintStream(out), new PrintStream(err));
    this.parser =
        new SubCommandArgumentParser(getClass().getName(),
            INFO_UPGRADE_DESCRIPTION_CLI.get(), false);
    this.parser.setVersionHandler(new DirectoryServerVersionHandler());
    this.parser.setShortToolDescription(REF_SHORT_DESC_UPGRADE.get());
    this.parser.setDocToolDescriptionSupplement(SUPPLEMENT_DESCRIPTION_UPGRADE_CLI.get());
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
  private boolean isForceUpgrade()
  {
    return force.isPresent();
  }

  /**
   * Force to ignore the errors during the upgrade process.
   * Continues rather than fails.
   *
   * @return {@code true} if the errors are forced to be ignored.
   */
  private boolean isIgnoreErrors()
  {
    return ignoreErrors.isPresent();
  }

  /**
   * Automatically accepts the license if it's present.
   *
   * @return {@code true} if license is accepted by default.
   */
  private boolean isAcceptLicense()
  {
    return acceptLicense.isPresent();
  }

  /** Initialize arguments provided by the command line. */
  private void initializeGlobalArguments() throws ArgumentException
  {
    if (!globalArgumentsInitialized)
    {
      configFile = configFileArgument();
      noPrompt = noPromptArgument();
      verbose = verboseArgument();
      quietMode = quietArgument();
      ignoreErrors =
              BooleanArgument.builder(OPTION_LONG_IGNORE_ERRORS)
                      .description(INFO_UPGRADE_OPTION_IGNORE_ERRORS.get())
                      .buildArgument();
      force =
              BooleanArgument.builder(OPTION_LONG_FORCE_UPGRADE)
                      .description(INFO_UPGRADE_OPTION_FORCE.get(OPTION_LONG_NO_PROMPT))
                      .buildArgument();
      acceptLicense = acceptLicenseArgument();
      showUsageArgument = showUsageArgument();


      // Register the global arguments.
      parser.addGlobalArgument(showUsageArgument);
      parser.setUsageArgument(showUsageArgument, getOutputStream());
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
      final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(e.getMessage());
      getOutputStream().print(message);
      return EXIT_CODE_ERROR;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      parser.parseArguments(args);
      if (isInteractive() && isQuiet())
      {
        final LocalizableMessage message =
            ERR_UPGRADE_INCOMPATIBLE_ARGS.get(OPTION_LONG_QUIET,
                "interactive mode");
        getOutputStream().println(message);
        return EXIT_CODE_ERROR;
      }
      if (isInteractive() && isForceUpgrade())
      {
        final LocalizableMessage message =
            ERR_UPGRADE_INCOMPATIBLE_ARGS.get(OPTION_LONG_FORCE_UPGRADE,
                "interactive mode");
        getOutputStream().println(message);
        return EXIT_CODE_ERROR;
      }
      if (isQuiet() && isVerbose())
      {
        final LocalizableMessage message =
            ERR_UPGRADE_INCOMPATIBLE_ARGS.get(OPTION_LONG_QUIET,
                OPTION_LONG_VERBOSE);
        getOutputStream().println(message);
        return EXIT_CODE_ERROR;
      }
    }
    catch (ArgumentException ae)
    {
      parser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
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
      UpgradeLog.createLogFile();
      JDKLogging.enableLoggingForOpenDJTool(UpgradeLog.getPrintStream());
      logger.info(LocalizableMessage.raw("**** Upgrade of OpenDJ started ****"));
      logger.info(RuntimeMessages.NOTE_INSTALL_DIRECTORY.get(UpgradeUtils.getInstallationPath()));
      logger.info(RuntimeMessages.NOTE_INSTANCE_DIRECTORY.get(UpgradeUtils.getInstancePath()));

      // Upgrade's context.
      UpgradeContext context = new UpgradeContext(this)
          .setIgnoreErrorsMode(isIgnoreErrors())
          .setAcceptLicenseMode(isAcceptLicense())
          .setInteractiveMode(isInteractive())
          .setForceUpgradeMode(isForceUpgrade());

      // Starts upgrade.
      Upgrade.upgrade(context);
    }
    catch (ClientException ex)
    {
      return ex.getReturnCode();
    }
    catch (Exception ex)
    {
      println(Style.ERROR, ERR_UPGRADE_MAIN_UPGRADE_PROCESS.get(ex
          .getMessage()), 0);

      return EXIT_CODE_ERROR;
    }
    return Upgrade.isSuccess() ? EXIT_CODE_SUCCESS : EXIT_CODE_ERROR;
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
        printProgressBar(pnc.getMessage(), pnc.getProgress(), 2);
      }
      else if (c instanceof FormattedNotificationCallback)
      {
        // Displays formatted notifications.
        final FormattedNotificationCallback fnc =
            (FormattedNotificationCallback) c;
        switch (fnc.getMessageSubType())
        {
        case TITLE_CALLBACK:
          println(Style.TITLE, LocalizableMessage.raw(fnc.getMessage()), 0);
          logger.info(LocalizableMessage.raw(fnc.getMessage()));
          break;
        case SUBTITLE_CALLBACK:
          println(Style.SUBTITLE, LocalizableMessage.raw(fnc.getMessage()),
              4);
          logger.info(LocalizableMessage.raw(fnc.getMessage()));
          break;
        case NOTICE_CALLBACK:
          println(Style.NOTICE, LocalizableMessage.raw(fnc.getMessage()), 1);
          logger.info(LocalizableMessage.raw(fnc.getMessage()));
          break;
        case ERROR_CALLBACK:
          println(Style.ERROR, LocalizableMessage.raw(fnc.getMessage()), 1);
          logger.error(LocalizableMessage.raw(fnc.getMessage()));
          break;
        case WARNING:
          println(Style.WARNING, LocalizableMessage.raw(fnc.getMessage()), 2);
          logger.warn(LocalizableMessage.raw(fnc.getMessage()));
          break;
        default:
          logger.error(LocalizableMessage.raw("Unsupported message type: "
            + fnc.getMessage()));
          throw new IOException("Unsupported message type: ");
        }
      }
      else if (c instanceof TextOutputCallback)
      {
        // Usual output text.
        final TextOutputCallback toc = (TextOutputCallback) c;
        if(toc.getMessageType() == TextOutputCallback.INFORMATION) {
          logger.info(LocalizableMessage.raw(toc.getMessage()));
          println(LocalizableMessage.raw(toc.getMessage()));
        } else {
          logger.error(LocalizableMessage.raw("Unsupported message type: "
            + toc.getMessage()));
          throw new IOException("Unsupported message type: ");
        }
      }
      else if (c instanceof ConfirmationCallback)
      {
        final ConfirmationCallback cc = (ConfirmationCallback) c;
        List<String> choices = new ArrayList<>();

        final String defaultOption = getDefaultOption(cc.getDefaultOption());
        StringBuilder prompt =
            new StringBuilder(wrapText(cc.getPrompt(), MAX_LINE_WIDTH, 2));

        // Default answers.
        final List<String> yesNoDefaultResponses =
            StaticUtils.arrayToList(
              INFO_PROMPT_YES_COMPLETE_ANSWER.get().toString(),
              INFO_PROMPT_YES_FIRST_LETTER_ANSWER.get().toString(),
              INFO_PROMPT_NO_COMPLETE_ANSWER.get().toString(),
              INFO_PROMPT_NO_FIRST_LETTER_ANSWER.get().toString());

        // Generating prompt and possible answers list.
        prompt.append(" ").append("(");
        if (cc.getOptionType() == ConfirmationCallback.YES_NO_OPTION)
        {
          choices.addAll(yesNoDefaultResponses);
          prompt.append(INFO_PROMPT_YES_COMPLETE_ANSWER.get())
              .append("/")
              .append(INFO_PROMPT_NO_COMPLETE_ANSWER.get());
        }
        else if (cc.getOptionType()
            == ConfirmationCallback.YES_NO_CANCEL_OPTION)
        {
          choices.addAll(yesNoDefaultResponses);
          choices.addAll(StaticUtils.arrayToList(
              INFO_TASKINFO_CMD_CANCEL_CHAR.get().toString()));

          prompt.append(" ")
                .append("(").append(INFO_PROMPT_YES_COMPLETE_ANSWER.get())
                .append("/").append(INFO_PROMPT_NO_COMPLETE_ANSWER.get())
                .append("/").append(INFO_TASKINFO_CMD_CANCEL_CHAR.get());
        }
        prompt.append(")");

        logger.info(LocalizableMessage.raw(cc.getPrompt()));

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
                  readInput(LocalizableMessage.raw(prompt), defaultOption,
                      Style.SUBTITLE);
            }
            catch (ClientException e)
            {
              logger.error(LocalizableMessage.raw(e.getMessage()));
              break;
            }

            String valueLC = value.toLowerCase();
            if ((valueLC.equals(INFO_PROMPT_YES_FIRST_LETTER_ANSWER.get().toString())
                || valueLC.equals(INFO_PROMPT_YES_COMPLETE_ANSWER.get().toString()))
                && choices.contains(value))
            {
              cc.setSelectedIndex(ConfirmationCallback.YES);
              break;
            }
            else if ((valueLC.equals(INFO_PROMPT_NO_FIRST_LETTER_ANSWER.get().toString())
                || valueLC.equals(INFO_PROMPT_NO_COMPLETE_ANSWER.get().toString()))
                && choices.contains(value))
            {
              cc.setSelectedIndex(ConfirmationCallback.NO);
              break;
            }
            else if (valueLC.equals(INFO_TASKINFO_CMD_CANCEL_CHAR.get().toString())
                && choices.contains(value))
            {
              cc.setSelectedIndex(ConfirmationCallback.CANCEL);
              break;
            }
            logger.info(LocalizableMessage.raw(value));
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
          prompt.append(" ").append(getDefaultOption(cc.getSelectedIndex()));
          println(Style.SUBTITLE, LocalizableMessage.raw(prompt), 0);
          logger.info(LocalizableMessage.raw(getDefaultOption(cc.getSelectedIndex())));
        }
      }
      else
      {
        logger.error(LocalizableMessage.raw("Unrecognized Callback"));
        throw new UnsupportedCallbackException(c, "Unrecognized Callback");
      }
    }
  }



  private static String getDefaultOption(final int defaultOption)
  {
    if (defaultOption == ConfirmationCallback.YES)
    {
      return INFO_PROMPT_YES_COMPLETE_ANSWER.get().toString();
    }
    else if (defaultOption == ConfirmationCallback.NO)
    {
      return INFO_PROMPT_NO_COMPLETE_ANSWER.get().toString();
    }
    else if (defaultOption == ConfirmationCallback.CANCEL)
    {
      return INFO_TASKINFO_CMD_CANCEL_CHAR.get().toString();
    }
    return null;
  }
}
