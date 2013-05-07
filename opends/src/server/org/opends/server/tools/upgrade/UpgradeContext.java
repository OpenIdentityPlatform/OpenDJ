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

import static org.opends.messages.ToolMessages.
  ERR_UPGRADE_DISPLAY_NOTIFICATION_ERROR;
import static org.opends.messages.ToolMessages.
  ERR_UPGRADE_DISPLAY_CONFIRM_ERROR;
import static org.opends.messages.ToolMessages.ERR_UPGRADE_DISPLAY_CHECK_ERROR;
import static org.opends.messages.ToolMessages.INFO_PROMPT_NO_COMPLETE_ANSWER;
import static org.opends.messages.ToolMessages.INFO_PROMPT_YES_COMPLETE_ANSWER;
import static org.opends.messages.ToolMessages.INFO_TASKINFO_CMD_CANCEL_CHAR;
import static org.opends.server.tools.upgrade.Upgrade.EXIT_CODE_ERROR;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.TextOutputCallback;

import org.opends.messages.Message;
import org.opends.server.tools.ClientException;
import org.opends.server.util.BuildVersion;

/**
 * Context information which is passed to upgrade tasks. This might include
 * server configuration, etc.
 */
public final class UpgradeContext
{

  /**
   * The version we upgrade from.
   */
  private final BuildVersion fromVersion;

  /**
   * The version we want to upgrade to.
   */
  private final BuildVersion toVersion;

  /**
   * If ignore errors is enabled.
   */
  private final boolean isIgnoreErrorsMode;

  /**
   * Constructor for the upgrade context.
   *
   * @param fromVersion
   *          The version number from we upgrade from.
   * @param toVersion
   *          The version number we want to upgrade to.
   */
  UpgradeContext(final BuildVersion fromVersion, final BuildVersion toVersion)
  {
    this.fromVersion = fromVersion;
    this.toVersion = toVersion;
    this.isIgnoreErrorsMode = false;
  }

  /**
   * Constructor for the upgrade context.
   *
   * @param fromVersion
   *          The version number from we upgrade from.
   * @param toVersion
   *          The version number we want to upgrade to.
   * @param isIgnoreErrorsMode
   *          If ignore error mode is enabled.
   */
  UpgradeContext(final BuildVersion fromVersion, final BuildVersion toVersion,
      final boolean isIgnoreErrorsMode)
  {
    this.fromVersion = fromVersion;
    this.toVersion = toVersion;
    this.isIgnoreErrorsMode = isIgnoreErrorsMode;
  }

  /**
   * Returns the old version.
   *
   * @return The old version.
   */
  public BuildVersion getFromVersion()
  {
    return fromVersion;
  }

  /**
   * Returns the new version.
   *
   * @return The new version.
   */
  public BuildVersion getToVersion()
  {
    return toVersion;
  }

  /**
   * Returns the ignore error mode.
   *
   * @return {@true} if ignore error mode is activated.
   */
  public boolean isIgnoreErrorsMode()
  {
    return isIgnoreErrorsMode;
  }

  /**
   * Sends notification message to the application via the call-back handler.
   *
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @param message
   *          The message to be reported.
   * @throws ClientException
   *           If an error occurred while reporting the message.
   */
  public void notify(final CallbackHandler handler, final Message message)
      throws ClientException
  {
    try
    {
      handler.handle(new Callback[] { new TextOutputCallback(
          TextOutputCallback.INFORMATION, message.toString()) });
    }
    catch (final Exception e)
    {
      throw new ClientException(EXIT_CODE_ERROR,
          ERR_UPGRADE_DISPLAY_NOTIFICATION_ERROR.get(e.getMessage()));
    }
  }

  /**
   * Sends notification message to the application via the call-back handler
   * containing specific sub type message.
   *
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @param message
   *          The message to be reported.
   * @param msgType
   *          The sub type message. The message to be reported.
   * @throws ClientException
   *           If an error occurred while reporting the message.
   */
  public void notify(final CallbackHandler handler, final Message message,
      final int msgType) throws ClientException
  {
    try
    {
      handler.handle(new Callback[] { new FormattedNotificationCallback(
          message, msgType) });
    }
    catch (final Exception e)
    {
      throw new ClientException(EXIT_CODE_ERROR,
          ERR_UPGRADE_DISPLAY_NOTIFICATION_ERROR.get(e.getMessage()));
    }
  }

  /**
   * Displays a progress callback.
   *
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @param callback
   *          The callback to display.
   * @throws ClientException
   *           If an error occurred while reporting the message.
   */
  public void notifyProgress(final CallbackHandler handler,
      final ProgressNotificationCallback callback) throws ClientException
  {
    try
    {
      handler.handle(new Callback[] { callback });
    }
    catch (final Exception e)
    {
      throw new ClientException(EXIT_CODE_ERROR,
          ERR_UPGRADE_DISPLAY_NOTIFICATION_ERROR.get(e.getMessage()));
    }
  }

  /**
   * Asks a confirmation to the user. Answer is yes or no.
   *
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @param message
   *          The message to be reported.
   * @param defaultOption
   *          The default selected option for this callback.
   * @throws ClientException
   *           If an error occurred while reporting the message.
   * @return an integer corresponding to the user's answer.
   */
  public int confirmYN(final CallbackHandler handler, final Message message,
      final int defaultOption) throws ClientException
  {
    final ConfirmationCallback confirmYNCallback =
        new ConfirmationCallback(message.toString(),
            ConfirmationCallback.WARNING, ConfirmationCallback.YES_NO_OPTION,
            defaultOption);
    try
    {
      handler.handle(new Callback[] { confirmYNCallback });
    }
    catch (final Exception e)
    {
      throw new ClientException(EXIT_CODE_ERROR,
          ERR_UPGRADE_DISPLAY_CONFIRM_ERROR.get(e.getMessage()));
    }
    return confirmYNCallback.getSelectedIndex();
  }

  /**
   * Checks the user's options. If a required option is not present in the
   * user's options list, stops the process.
   *
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @param options
   *          The options which should be present in the user's upgrade options.
   * @throws ClientException
   *           If an error occurred while reporting the message.
   * @return An integer which represents the user selected index.
   */
  public int checkCLIUserOption(final CallbackHandler handler,
      final int... options) throws ClientException
  {
    final VerificationCallback checkCLICallback =
        new VerificationCallback(VerificationCallback.WARNING,
            ConfirmationCallback.OK_CANCEL_OPTION, ConfirmationCallback.OK,
            options);

    try
    {
      handler.handle(new Callback[] { checkCLICallback });
    }
    catch (final Exception e)
    {
      throw new ClientException(EXIT_CODE_ERROR,
          ERR_UPGRADE_DISPLAY_CHECK_ERROR.get(e.getMessage()));
    }
    return checkCLICallback.getSelectedIndex();

  }

  /**
   * Returns the default option string.
   *
   * @param defaultOption
   *          The default option int value.
   * @return The default option string.
   */
  public static String getDefaultOption(final int defaultOption)
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
