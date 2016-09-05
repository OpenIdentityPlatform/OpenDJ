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
 * Parameters to setup a directory server.
 */
public final class SetupParameters
{
  private String baseDn;
  private int jmxPort;
  private String backendType;

  private SetupParameters()
  {
    // private constructor to force usage of the associated Builder
  }

  /**
   * Creates a builder for the setup parameters.
   *
   * @return a builder
   */
  public static Builder setupParams()
  {
    return new Builder();
  }

  /**
   * Generates command-line arguments from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArguments(ConnectionParameters connParams)
  {
    return new String[] {
      "--cli",
      "--noPropertiesFile",
      "--no-prompt",
      "--doNotStart",
      "--skipPortCheck",
      "--baseDN", baseDn,
      "--hostname", connParams.getHostname(),
      "--rootUserDN", connParams.getBindDn(),
      "--rootUserPassword", connParams.getBindPassword(),
      "--ldapPort", s(connParams.getLdapPort()),
      "--adminConnectorPort", s(connParams.getAdminPort()),
      "--jmxPort", s(jmxPort),
      "--backendType", backendType
    };
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
   * Builder for this class.
   */
  public static final class Builder
  {
    private SetupParameters params;

    private Builder()
    {
      params = new SetupParameters();
    }

    /**
     * Generates the parameters from this builder.
     * <p>
     * After this call, the builder is reset and can be used to generate other parameters.
     *
     * @return the replication parameters
     */
    public SetupParameters toParams()
    {
      SetupParameters p = params;
      this.params = new SetupParameters();
      return p;
    }

    /**
     * Sets the base Dn for user information in the directory server.
     *
     * @param baseDn
     *          the base Dn
     * @return this builder
     */
    public Builder baseDn(String baseDn)
    {
      params.baseDn = baseDn;
      return this;
    }

    /**
     * Sets the port on which the directory server should listen for JMX communication.
     *
     * @param jmxPort
     *          the JMX port
     * @return this builder
     */
    public Builder jmxPort(int jmxPort)
    {
      params.jmxPort = jmxPort;
      return this;
    }

    /**
     * Sets the type of the backend containing user information.
     *
     * @param backendType
     *          the backend type (e.g. je, pdb)
     * @return this builder
     */
    public Builder backendType(String backendType)
    {
      params.backendType = backendType;
      return this;
    }
  }
}