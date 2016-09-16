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

/** Parameters to rebuild the indexes of a directory server. */
public final class RebuildIndexParameters
{
  private String baseDN;

  private RebuildIndexParameters()
  {
    // prefer usage of static method for creation
  }

  /**
   * Creates a builder for the rebuild index parameters.
   *
   * @return a builder
   */
  public static RebuildIndexParameters rebuildIndexParams()
  {
    return new RebuildIndexParameters();
  }

  /**
   * Generates command-line arguments from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArguments(String configurationFile)
  {
    return new String[] {
      "--configFile", configurationFile,
      "--baseDN", baseDN,
      "--rebuildAll",
      "--noPropertiesFile"
    };
  }

  /**
   * Sets the base Dn for user information in the directory server.
   *
   * @param baseDN
   *          the baseDN
   * @return this builder
   */
  public RebuildIndexParameters baseDN(String baseDN)
  {
    this.baseDN = baseDN;
    return this;
  }
}
