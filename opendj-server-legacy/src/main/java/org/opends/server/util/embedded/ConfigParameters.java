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
 * Parameters to configure a directory server.
 */
public final class ConfigParameters
{
  private String serverRootDirectory;
  private String serverInstanceDirectory;
  private String configurationFile;

  private ConfigParameters()
  {
    // private constructor to force usage of the associated Builder
  }

  String getServerRootDirectory()
  {
    return serverRootDirectory;
  }

  String getServerInstanceDirectory()
  {
    // provides the expected default value if not set
    return serverInstanceDirectory != null ? serverInstanceDirectory : serverRootDirectory;
  }

  String getConfigurationFile()
  {
    return configurationFile;
  }

  /**
   * Builder for this class.
   */
  public static final class Builder
  {
    private ConfigParameters params;

    private Builder()
    {
      params = new ConfigParameters();
    }

    /**
     * Creates a builder for the configuration parameters.
     *
     * @return a builder
     */
    public static Builder configParams()
    {
      return new Builder();
    }

    /**
     * Generates the parameters from this builder.
     * <p>
     * After this call, the builder is reset and can be used to generate other parameters.
     *
     * @return the replication parameters
     */
    public ConfigParameters toParams()
    {
      ConfigParameters p = params;
      this.params = new ConfigParameters();
      return p;
    }

    /**
     * Sets the server root directory of the directory server.
     * <p>
     * The server root is the location where the binaries and default configuration is stored.
     *
     * @param dir
     *          the server root directory
     * @return this builder
     */
    public Builder serverRootDirectory(String dir)
    {
      params.serverRootDirectory = dir;
      return this;
    }

    /**
     * Sets the install root directory of the directory server.
     * <p>
     * The install root is the location where the data and live configuration is stored.
     *
     * @param dir
     *          the install root directory
     * @return this builder
     */
    public Builder serverInstanceDirectory(String dir)
    {
      params.serverInstanceDirectory = dir;
      return this;
    }

    /**
     * Sets the path of the configuration file of the directory server.
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
  }
}