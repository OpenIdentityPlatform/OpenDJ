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
import static org.opends.server.tools.upgrade.Upgrade.EXIT_CODE_ERROR;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.TextOutputCallback;

import org.opends.messages.Message;
import org.opends.server.tools.ClientException;
import org.opends.server.types.InitializationException;
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
   * The call-back handler for interacting with the upgrade application.
   */
  private final CallbackHandler handler;

  /**
   * If ignore errors is enabled.
   */
  private boolean isIgnoreErrorsMode;

  /**
   * If accept license is enabled.
   */
  private boolean isAcceptLicenseMode;

  /**
   * If interactive mode is enabled.
   */
  private boolean isInteractiveMode;

  /**
   * If force upgrade is enabled.
   */
  private boolean isForceUpgradeMode;



  /**
   * Creates a new upgrade context for upgrading from the instance version (as
   * obtained from config/buildinfo) to the binary version.
   *
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @throws InitializationException
   *           If an error occurred while reading or parsing the version.
   */
  public UpgradeContext(CallbackHandler handler) throws InitializationException
  {
    this(BuildVersion.instanceVersion(), BuildVersion.binaryVersion(), handler);
  }



  /**
   * Constructor for the upgrade context.
   *
   * @param fromVersion
   *          The version number from we upgrade from.
   * @param toVersion
   *          The version number we want to upgrade to.
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   */
  public UpgradeContext(final BuildVersion fromVersion,
      final BuildVersion toVersion, CallbackHandler handler)
  {
    this.fromVersion = fromVersion;
    this.toVersion = toVersion;
    this.handler = handler;
  }



  /**
   * Returns the old version.
   *
   * @return The old version.
   */
  BuildVersion getFromVersion()
  {
    return fromVersion;
  }



  /**
   * Returns the new version.
   *
   * @return The new version.
   */
  BuildVersion getToVersion()
  {
    return toVersion;
  }



  /**
   * Returns the ignore error mode.
   *
   * @return {code true} if ignore error mode is activated.
   */
  boolean isIgnoreErrorsMode()
  {
    return isIgnoreErrorsMode;
  }



  /**
   * Sets the ignore errors mode.
   *
   * @param isIgnoreErrorsMode
   *          {@code true} if ignore error mode is activated.
   * @return This upgrade context.
   */
  public UpgradeContext setIgnoreErrorsMode(boolean isIgnoreErrorsMode)
  {
    this.isIgnoreErrorsMode = isIgnoreErrorsMode;
    return this;
  }



  /**
   * Returns the accept license mode.
   *
   * @return {@code true} if accept license mode is activated.
   */
  boolean isAcceptLicenseMode()
  {
    return isAcceptLicenseMode;
  }



  /**
   * Sets the accept license mode.
   *
   * @param isAcceptLicenseMode
   *          {@code true} if the accept license mode is activated.
   * @return This upgrade context.
   */
  public UpgradeContext setAcceptLicenseMode(boolean isAcceptLicenseMode)
  {
    this.isAcceptLicenseMode = isAcceptLicenseMode;
    return this;
  }



  /**
   * Returns the callback handler.
   *
   * @return The actual callback handler.
   */
  CallbackHandler getHandler()
  {
    return handler;
  }



  /**
   * Returns the status of the interactive mode.
   *
   * @return {@code true} if interactive mode is activated.
   */
  boolean isInteractiveMode()
  {
    return isInteractiveMode;
  }



  /**
   * Sets the interactive mode.
   *
   * @param isInteractiveMode
   *          {@code true} if the interactive mode is activated.
   * @return This upgrade context.
   */
  public UpgradeContext setInteractiveMode(boolean isInteractiveMode)
  {
    this.isInteractiveMode = isInteractiveMode;
    return this;
  }



  /**
   * Returns the status of the force upgrade mode.
   *
   * @return {@code true} if the force upgrade mode is activated.
   */
  boolean isForceUpgradeMode()
  {
    return isForceUpgradeMode;
  }



  /**
   * Sets the force upgrade mode.
   *
   * @param isForceUpgradeMode
   *          {@code true} if the force upgrade mode is activated.
   * @return This upgrade context.
   */
  public UpgradeContext setForceUpgradeMode(boolean isForceUpgradeMode)
  {
    this.isForceUpgradeMode = isForceUpgradeMode;
    return this;
  }



  /**
   * Sends notification message to the application via the call-back handler.
   *
   * @param message
   *          The message to be reported.
   * @throws ClientException
   *           If an error occurred while reporting the message.
   */
  void notify(final Message message) throws ClientException
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
   * @param message
   *          The message to be reported.
   * @param msgType
   *          The sub type message. The message to be reported.
   * @throws ClientException
   *           If an error occurred while reporting the message.
   */
  void notify(final Message message, final int msgType) throws ClientException
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
   * @param callback
   *          The callback to display.
   * @throws ClientException
   *           If an error occurred while reporting the message.
   */
  void notifyProgress(final ProgressNotificationCallback callback)
      throws ClientException
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
   * @param message
   *          The message to be reported.
   * @param defaultOption
   *          The default selected option for this callback.
   * @throws ClientException
   *           If an error occurred while reporting the message.
   * @return an integer corresponding to the user's answer.
   */
  int confirmYN(final Message message, final int defaultOption)
      throws ClientException
  {
    final ConfirmationCallback confirmYNCallback = new ConfirmationCallback(
        message.toString(), ConfirmationCallback.WARNING,
        ConfirmationCallback.YES_NO_OPTION, defaultOption);
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
}
