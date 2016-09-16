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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.server.embedded;

import java.util.ArrayList;
import java.util.List;

/** Parameters to upgrade a Directory Server. */
public final class UpgradeParameters
{
  private boolean ignoreErrors;

  private UpgradeParameters()
  {
    // prefer usage of static method for creation
  }

  /**
   * Starts construction of the upgrade parameters.
   *
   * @return this builder
   */
  public static UpgradeParameters upgradeParams()
  {
    return new UpgradeParameters();
  }

  /**
   * Generates command-line arguments from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArguments(String configurationFile)
  {
    List<String> args = new ArrayList<>(6);
    args.add("--acceptLicense");
    args.add("--no-prompt");
    args.add("--force");
    args.add("--configFile");
    args.add(configurationFile);
    if (ignoreErrors) {
      args.add("--ignoreErrors");
    }
    return args.toArray(new String[args.size()]);
  }

  /**
   * Indicates whether errors should be ignored during the upgrade.
   * <p>
   * This option should be used with caution and may be useful in automated deployments where
   * potential errors are known in advance and resolved after the upgrade has completed
   *
   * @param ignore
   *          indicates whether errors should be ignored
   * @return this builder
   */
  public UpgradeParameters isIgnoreErrors(boolean ignore)
  {
    ignoreErrors = ignore;
    return this;
  }
}
