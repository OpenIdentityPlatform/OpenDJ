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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools.upgrade;

import com.forgerock.opendj.cli.ClientException;

/** An upgrade task. */
interface UpgradeTask
{
  /**
   * Performs any preparation work required before performing the upgrade task, including
   * interacting with the user where needed (e.g. in order to ask for confirmation), and throw a
   * {@code ClientException} if the upgrade cannot proceed.
   *
   * @param context
   *          Context through which tasks can interact with the server installation.
   * @throws ClientException
   *           If the upgrade cannot proceed.
   */
  void prepare(UpgradeContext context) throws ClientException;

  /**
   * Performs this upgrade task.
   *
   * @param context
   *          Context through which tasks can interact with the server installation.
   * @throws ClientException
   *           If an error occurred while performing the task.
   */
  void perform(UpgradeContext context) throws ClientException;

  /**
   * This method will be invoked after all upgrade tasks have completed
   * successfully The post upgrade tasks are processes which should be launched
   * after a successful upgrade.
   *
   * @param context
   *          Context through which tasks can interact with the server
   *          installation.
   * @throws ClientException
   *           If the task cannot proceed.
   */
  void postUpgrade(UpgradeContext context) throws ClientException;

  /**
   * This method will be invoked only if one of the previous post upgrade task
   * has failed.
   *
   * @param context
   *          Context through which tasks can interact with the server
   *          installation.
   * @throws ClientException
   *           If the task cannot proceed.
   */
  void postponePostUpgrade(UpgradeContext context) throws ClientException;
}
