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

/** Parameters to configure a directory server. */
public final class ConfigParameters
{
  private String serverRootDirectory;
  private String serverInstanceDirectory;
  private String configurationFile;
  private boolean disableConnectionHandlers;

  private ConfigParameters()
  {
    // prefer usage of static method for creation
  }

  /**
   * Creates configuration parameters.
   *
   * @return the parameters
   */
  public static ConfigParameters configParams()
  {
    return new ConfigParameters();
  }

  String getServerRootDirectory()
  {
    return serverRootDirectory;
  }

  /** This value may be {@code null}, it must always be checked. */
  String getServerInstanceDirectory()
  {
    return serverInstanceDirectory;
  }

  String getConfigurationFile()
  {
    return configurationFile;
  }

  boolean isDisableConnectionHandlers()
  {
    return disableConnectionHandlers;
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
  public ConfigParameters serverRootDirectory(String dir)
  {
    serverRootDirectory = dir;
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
  public ConfigParameters serverInstanceDirectory(String dir)
  {
    serverInstanceDirectory = dir;
    return this;
  }

  /**
   * Sets the path of the configuration file of the directory server.
   *
   * @param file
   *          the configuration file
   * @return this builder
   */
  public ConfigParameters configurationFile(String file)
  {
    configurationFile = file;
    return this;
  }

  /**
   * Sets the indicator allowing to disable the connection handlers.
   *
   * @param disable
   *          {@code true} to disable the connection handlers
   * @return this builder
   */
  public ConfigParameters disableConnectionHandlers(boolean disable)
  {
    disableConnectionHandlers = disable;
    return this;
  }
}