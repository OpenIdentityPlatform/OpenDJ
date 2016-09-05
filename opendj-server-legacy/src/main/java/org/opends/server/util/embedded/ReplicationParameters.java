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
 * Parameters for replication operations on a directory server.
 */
public final class ReplicationParameters
{
  private String baseDn;
  private Integer replicationPort1;
  private Integer replicationPort2;
  private ConnectionParameters connParamsForHost2;

  private ReplicationParameters()
  {
    // private constructor to force usage of the associated Builder
  }

  /**
   * Creates a builder for the replication parameters.
   *
   * @return a builder
   */
  public static Builder replicationParams()
  {
    return new Builder();
  }

  /**
   * Generates the command-line arguments for enabling replication, from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArgumentsEnable(String configurationFile, ConnectionParameters connParams)
  {
    return new String[] {
      "enable",
      "--no-prompt",
      "--configFile", configurationFile,
      "--host1", connParams.getHostname(),
      "--port1", s(connParams.getAdminPort()),
      "--bindDN1", connParams.getBindDn(),
      "--bindPassword1", connParams.getBindPassword(),
      "--replicationPort1", s(replicationPort1),
      "--host2", connParamsForHost2.getHostname(),
      "--port2", s(connParamsForHost2.getAdminPort()),
      "--bindDN2", connParamsForHost2.getBindDn(),
      "--bindPassword2", connParamsForHost2.getBindPassword(),
      "--replicationPort2", s(replicationPort2),
      "--adminUID", connParams.getAdminUid(),
      "--adminPassword", connParams.getAdminPassword(),
      "--baseDN", baseDn,
      "--trustAll",
      "--noPropertiesFile" };
  }

  /**
   * Generates the command-line arguments for initializing replication, from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArgumentsInitialize(String configurationFile, ConnectionParameters connParams)
  {
    return new String[] {
      "initialize",
      "--no-prompt",
      "--configFile", configurationFile,
      "--hostSource", connParams.getHostname(),
      "--portSource", s(connParams.getAdminPort()),
      "--hostDestination", connParamsForHost2.getHostname(),
      "--portDestination", s(connParamsForHost2.getAdminPort()),
      "--adminUID", connParams.getAdminUid(),
      "--adminPassword", connParams.getAdminPassword(),
      "--baseDN", baseDn,
      "--trustAll",
      "--noPropertiesFile" };
  }

  /**
   * Generates the command-line arguments for output of the replication status, from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArgumentsStatus(String configurationFile, ConnectionParameters connParams)
  {
    return new String[] {
      "status",
      "--no-prompt",
      "--configFile", configurationFile,
      "--hostname", connParams.getHostname(),
      "--port", s(connParams.getAdminPort()),
      "--adminUID", connParams.getAdminUid(),
      "--adminPassword", connParams.getAdminPassword(),
      "--script-friendly",
      "--noPropertiesFile" };
  }

  int getReplicationPort1()
  {
    return replicationPort1;
  }

  int getReplicationPort2()
  {
    return replicationPort2;
  }

  String getHostname2()
  {
    return connParamsForHost2.getHostname();
  }

  int getAdminPort2()
  {
    return connParamsForHost2.getAdminPort();
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
    private ReplicationParameters params;

    private Builder()
    {
      params = new ReplicationParameters();
    }

    /**
     * Generates the parameters from this builder.
     * <p>
     * After this call, the builder is reset and can be used to generate other parameters.
     *
     * @return the replication parameters
     */
    public ReplicationParameters toParams()
    {
      ReplicationParameters p = params;
      this.params = new ReplicationParameters();
      return p;
    }

    /**
     * Sets the base Dn of the data to be replicated.
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
     * Sets the replication port of the first server whose contents will be replicated.
     * <p>
     * The first server should correspond to the embedded server on which the replication
     * operation is applied.
     *
     * @param port
     *          the replication port
     * @return this builder
     */
    public Builder replicationPort1(int port)
    {
      params.replicationPort1 = port;
      return this;
    }

    /**
     * Sets the replication port of the second server whose contents will be replicated.
     *
     * @param port
     *          the replication port
     * @return this builder
     */
    public Builder replicationPort2(int port)
    {
      params.replicationPort2 = port;
      return this;
    }

    /**
     * Sets the connection parameters of the second server whose contents will be replicated.
     *
     * @param host2Params
     *          The connection parameters
     * @return this builder
     */
    public Builder connectionParamsForHost2(ConnectionParameters.Builder host2Params)
    {
      params.connParamsForHost2 = host2Params.toParams();
      return this;
    }
  }
}
