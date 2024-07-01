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
import java.util.Arrays;
import java.util.List;

/** Parameters to setup a directory server. */
public final class SetupParameters
{
  private String baseDn;
  private int jmxPort;
  private String backendType;
  private List<String> ldifFiles = new ArrayList<>();

  private SetupParameters()
  {
    // prefer usage of static method for creation
  }

  /**
   * Creates a builder for the setup parameters.
   *
   * @return a builder
   */
  public static SetupParameters setupParams()
  {
    return new SetupParameters();
  }

  /**
   * Generates command-line arguments from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArguments(ConnectionParameters connParams)
  {
    String[] baseArgs = new String[] {
      "--cli",
      "--noPropertiesFile",
      "--no-prompt",
      "--doNotStart",
      "--skipPortCheck",
      "--baseDN", baseDn,
      "--hostname", connParams.getHostName(),
      "--rootUserDN", connParams.getBindDn(),
      "--rootUserPassword", connParams.getBindPassword(),
      "--ldapPort", s(connParams.getLdapPort()),
      "--adminConnectorPort", s(connParams.getAdminPort()),
      "--jmxPort", s(jmxPort),
      "--backendType", backendType
    };
    List<String> args = new ArrayList<>(Arrays.asList(baseArgs));
    if (connParams.getLdapSecurePort() != null)
    {
      args.add("--ldapsPort");
      args.add(s(connParams.getLdapSecurePort()));
      args.add("--generateSelfSignedCertificate");
    }
    for (final String ldif : ldifFiles)
    {
      args.add("--ldifFile");
      args.add(ldif);
    }
    return args.toArray(new String[args.size()]);
  }

  String getBaseDn()
  {
    return baseDn;
  }

  String getBackendType()
  {
    return backendType;
  }

  /** Convert an integer to a String. */
  private String s(Integer val)
  {
    return String.valueOf(val);
  }

  /**
   * Sets the base Dn for user information in the directory server.
   *
   * @param baseDn
   *          the base Dn
   * @return this builder
   */
  public SetupParameters baseDn(String baseDn)
  {
    this.baseDn = baseDn;
    return this;
  }

  /**
   * Sets the port on which the directory server should listen for JMX communication.
   *
   * @param jmxPort
   *          the JMX port
   * @return this builder
   */
  public SetupParameters jmxPort(int jmxPort)
  {
    this.jmxPort = jmxPort;
    return this;
  }

  /**
   * Sets the type of the backend containing user information.
   *
   * @param backendType
   *          the backend type (e.g. je, pdb)
   * @return this builder
   */
  public SetupParameters backendType(String backendType)
  {
    this.backendType = backendType;
    return this;
  }

  /**
   * Add an ldif file to import after setup.
   *
   * @param ldif
   *          the LDIF to import
   * @return this builder
   */
  public SetupParameters ldifFile(String ldif) {
    this.ldifFiles.add(ldif);
    return this;
  }
}