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

import javax.security.auth.callback.CallbackHandler;

import org.opends.server.tools.ClientException;

/**
 * An upgrade task.
 */
public interface UpgradeTask
{

  /**
   * Notifies this task that the upgrade has completed. This method will be
   * invoked after all upgrade tasks have completed successfully. Most task
   * implementation will not need to do anything.
   *
   * @param context
   *          Context through which tasks can interact with the server
   *          installation.
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @throws ClientException
   *           If an error occurred while performing the task.
   */
  void end(UpgradeContext context, CallbackHandler handler)
      throws ClientException;

  /**
   * Performs this upgrade task.
   *
   * @param context
   *          Context through which tasks can interact with the server
   *          installation.
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @throws ClientException
   *           If an error occurred while performing the task.
   */
  void perform(UpgradeContext context, CallbackHandler handler)
      throws ClientException;

  /**
   * Notifies this task that the upgrade is about to start. This method will be
   * invoked before any upgrade tasks have been performed. Most task
   * implementation will not need to do anything.
   *
   * @param context
   *          Context through which tasks can interact with the server
   *          installation.
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @throws ClientException
   *           If an error occurred while starting the task.
   */
  void start(UpgradeContext context, CallbackHandler handler)
      throws ClientException;

  /**
   * Verifies that this upgrade task can be completed or not.
   *
   * @param context
   *          Context through which tasks can interact with the server
   *          installation.
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @throws ClientException
   *           If the upgrade cannot proceed.
   */
  void verify(UpgradeContext context, CallbackHandler handler)
      throws ClientException;

  /**
   * Interacts with the user where needed (e.g. in order to ask for
   * confirmation), and throw a {@code ClientException} if the upgrade cannot
   * proceed.
   *
   * @param context
   *          Context through which tasks can interact with the server
   *          installation.
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @throws ClientException
   *           If the upgrade cannot proceed.
   */
  void interact(UpgradeContext context, CallbackHandler handler)
      throws ClientException;
}
