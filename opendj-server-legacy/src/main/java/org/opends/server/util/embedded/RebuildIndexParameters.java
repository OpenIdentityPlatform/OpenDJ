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
package org.opends.server.util.embedded;

/**
 * Parameters to rebuild the indexes of a directory server.
 */
public final class RebuildIndexParameters
{
  private String configurationFile;
  private String baseDN;

  private RebuildIndexParameters()
  {
    // private constructor to force usage of the associated Builder
  }

  /**
   * Generates command-line arguments from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArguments()
  {
    return new String[] {
      "--configFile", configurationFile,
      "--baseDN", baseDN,
      "--rebuildAll",
      "--noPropertiesFile"
    };
  }

  /**
   * Builder for this class.
   */
  public static final class Builder
  {
    private RebuildIndexParameters params;

    private Builder()
    {
      params = new RebuildIndexParameters();
    }

    /**
     * Creates a builder for the rebuild index parameters.
     *
     * @return a builder
     */
    public static Builder rebuildIndexParams()
    {
      return new Builder();
    }

    /**
     * Generates the parameters from this builder.
     * <p>
     * After this call, the builder is reset and can be used to generate other parameters.
     *
     * @return the rebuild index parameters
     */
    public RebuildIndexParameters toParams()
    {
      RebuildIndexParameters p = params;
      this.params = new RebuildIndexParameters();
      return p;
    }

    /**
     * Sets the configuration file of the server.
     *
     * @param file
     *          the configuration file
     * @return this builder
     */
    public Builder configurationFile(String file)
    {
      params.configurationFile = file;
      return this;
    }

    /**
     * Sets the base Dn for user information in the directory server.
     *
     * @param baseDN
     *          the baseDN
     * @return this builder
     */
    public Builder baseDN(String baseDN)
    {
      params.baseDN = baseDN;
      return this;
    }
  }
}
