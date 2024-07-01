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

/** Parameters for replication operations on a directory server. */
public final class ReplicationParameters
{
  private String baseDn;
  private Integer replicationPortSource;
  private Integer replicationPortDestination;
  private ConnectionParameters connParamsForDestination;

  private ReplicationParameters()
  {
    // prefer usage of static method for creation
  }

  /**
   * Creates a builder for the replication parameters.
   *
   * @return a builder
   */
  public static ReplicationParameters replicationParams()
  {
    return new ReplicationParameters();
  }

  /**
   * Generates the command-line arguments for configuring replication, from the parameters.
   *
   * @return command-line arguments
   */
  String[] toCommandLineArgumentsConfiguration(String configurationFile, ConnectionParameters connParams)
  {
    return new String[] {
      "enable",
      "--no-prompt",
      "--configFile", configurationFile,
      "--host1", connParams.getHostName(),
      "--port1", s(connParams.getAdminPort()),
      "--bindDN1", connParams.getBindDn(),
      "--bindPassword1", connParams.getBindPassword(),
      "--replicationPort1", s(replicationPortSource),
      "--host2", connParamsForDestination.getHostName(),
      "--port2", s(connParamsForDestination.getAdminPort()),
      "--bindDN2", connParamsForDestination.getBindDn(),
      "--bindPassword2", connParamsForDestination.getBindPassword(),
      "--replicationPort2", s(replicationPortDestination),
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
      "--hostSource", connParams.getHostName(),
      "--portSource", s(connParams.getAdminPort()),
      "--hostDestination", connParamsForDestination.getHostName(),
      "--portDestination", s(connParamsForDestination.getAdminPort()),
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
      "--hostname", connParams.getHostName(),
      "--port", s(connParams.getAdminPort()),
      "--adminUID", connParams.getAdminUid(),
      "--adminPassword", connParams.getAdminPassword(),
      "--script-friendly",
      "--noPropertiesFile" };
  }

  @Override
  public String toString()
  {
    return "ReplicationParameters [baseDn=" + baseDn
        + ", source replication port=" + replicationPortSource
        + ", destination host name=" + getHostNameDestination()
        + ", destination replication port=" + replicationPortDestination
        + ", destination admin port=" + getAdminPortDestination()
        + "]";
  }

  int getReplicationPortSource()
  {
    return replicationPortSource;
  }

  int getReplicationPortDestination()
  {
    return replicationPortDestination;
  }

  String getHostNameDestination()
  {
    return connParamsForDestination.getHostName();
  }

  int getAdminPortDestination()
  {
    return connParamsForDestination.getAdminPort();
  }

  /** Convert an integer to a String. */
  private String s(Integer val)
  {
    return String.valueOf(val);
  }

  /**
   * Sets the base Dn of the data to be replicated.
   *
   * @param baseDn
   *          the base Dn
   * @return this builder
   */
  public ReplicationParameters baseDn(String baseDn)
  {
    this.baseDn = baseDn;
    return this;
  }

  /**
   * Sets the replication port of the first server (source) whose contents will be replicated.
   * <p>
   * The source server should correspond to the embedded server on which the replication operation is
   * applied.
   *
   * @param port
   *          the replication port
   * @return this builder
   */
  public ReplicationParameters replicationPortSource(int port)
  {
    this.replicationPortSource = port;
    return this;
  }

  /**
   * Sets the replication port of the second server (destination) whose contents will be replicated.
   *
   * @param port
   *          the replication port
   * @return this builder
   */
  public ReplicationParameters replicationPortDestination(int port)
  {
    this.replicationPortDestination = port;
    return this;
  }

  /**
   * Sets the connection parameters of the second server (destination) whose contents will be replicated.
   *
   * @param destinationParams
   *          The connection parameters for destination server
   * @return this builder
   */
  public ReplicationParameters connectionParamsForDestination(ConnectionParameters destinationParams)
  {
    this.connParamsForDestination = destinationParams;
    return this;
  }
}
