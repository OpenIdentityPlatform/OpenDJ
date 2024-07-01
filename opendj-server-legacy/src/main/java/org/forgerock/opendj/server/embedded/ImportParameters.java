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

/** Parameters to import LDIF data to a directory server. */
public final class ImportParameters
{
  private String backendID;
  private String ldifFile;

  private ImportParameters()
  {
    // prefer usage of static method for creation
  }

  /**
   * Creates the import parameters.
   *
   * @return parameters
   */
  public static ImportParameters importParams()
  {
    return new ImportParameters();
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
   * Sets the backend id of the backend to import.
   *
   * @param id
   *          the backend id
   * @return this builder
   */
  public ImportParameters backendId(String id)
  {
    backendID = id;
    return this;
  }

  /**
   * Sets the path to the LDIF file to be imported.
   *
   * @param ldifFile
   *          The path to the LDIF file
   * @return this builder
   */
  public ImportParameters ldifFile(String ldifFile)
  {
    this.ldifFile = ldifFile;
    return this;
  }
}
