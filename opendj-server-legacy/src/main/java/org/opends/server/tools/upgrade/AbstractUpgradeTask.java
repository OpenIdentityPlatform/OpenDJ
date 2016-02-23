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
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.tools.upgrade;

import com.forgerock.opendj.cli.ClientException;

/**
 * Abstract upgrade task implementation.
 */
abstract class AbstractUpgradeTask implements UpgradeTask
{
  /**
   * Creates a new abstract upgrade task.
   */
  AbstractUpgradeTask()
  {
    // No implementation required.
  }

  /** {@inheritDoc} */
  @Override
  public void prepare(UpgradeContext context)
      throws ClientException
  {
    // Nothing to do.
  }

  /** {@inheritDoc} */
  @Override
  public void perform(UpgradeContext context) throws ClientException
  {
    // Must be implemented.
  }

  /** {@inheritDoc} */
  @Override
  public void postUpgrade(UpgradeContext context)
      throws ClientException
  {
    // Nothing to do.
  }

  /** {@inheritDoc} */
  @Override
  public void postponePostUpgrade(UpgradeContext context)
      throws ClientException
  {
    // Nothing to do.
  }
}
