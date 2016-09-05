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
 * Parameters to import LDIF data to a directory server.
 */
public final class ImportParameters
{
  private String backendID;
  private String ldifFile;

  private ImportParameters()
  {
    // private constructor to force usage of the associated Builder
  }

  /**
   * Creates a builder for the import parameters.
   *
   * @return a builder
   */
  public static Builder importParams()
  {
    return new Builder();
  }

  /**
   * Generates command-line arguments from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArguments(String configurationFile, ConnectionParameters connParams)
  {
    return new String[] {
      "--configFile", configurationFile,
      "--backendID", backendID,
      "--bindDN", connParams.getBindDn(),
      "--bindPassword", connParams.getBindPassword(),
      "--ldifFile", ldifFile,
      "--noPropertiesFile",
      "--trustAll"
    };
  }

  String getLdifFile()
  {
    return ldifFile;
  }

  /**
   * Builder for this class.
   */
  public static final class Builder
  {
    private ImportParameters params;

    private Builder()
    {
      params = new ImportParameters();
    }

    /**
     * Generates the parameters from this builder.
     * <p>
     * After this call, the builder is reset and can be used to generate other parameters.
     *
     * @return the replication parameters
     */
    public ImportParameters toParams()
    {
      ImportParameters p = params;
      this.params = new ImportParameters();
      return p;
    }

    /**
     * Sets the backend id of the backend to import.
     *
     * @param id
     *          the backend id
     * @return this builder
     */
    public Builder backendId(String id)
    {
      params.backendID = id;
      return this;
    }

    /**
     * Sets the path to the LDIF file to be imported.
     *
     * @param ldifFile
     *          The path to the LDIF file
     * @return this builder
     */
    public Builder ldifFile(String ldifFile)
    {
      params.ldifFile = ldifFile;
      return this;
    }
  }
}
