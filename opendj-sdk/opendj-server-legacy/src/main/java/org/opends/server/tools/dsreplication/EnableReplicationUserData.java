/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.tools.dsreplication;

/**
 * This class is used to store the information provided by the user to enable
 * replication.  It is required because when we are in interactive mode the
 * ReplicationCliArgumentParser is not enough.
 */
public class EnableReplicationUserData extends ReplicationUserData
{
  /** Data for enabling replication on a server. */
  static final class EnableReplicationServerData
  {
    private String hostName;
    private int port;
    private String bindDn;
    private String pwd;
    private int replicationPort;
    private boolean secureReplication;
    private boolean configureReplicationServer = true;
    private boolean configureReplicationDomain = true;

    /**
     * Returns the host name of this server.
     *
     * @return the host name of this server.
     */
    String getHostName()
    {
      return hostName;
    }

    /**
     * Sets the host name of this server.
     *
     * @param hostName
     *          the host name of this server
     */
    void setHostName(String hostName)
    {
      this.hostName = hostName;
    }

    /**
     * Returns the port of this server.
     *
     * @return the port of this server
     */
    int getPort()
    {
      return port;
    }

    /**
     * Sets the port of this server.
     *
     * @param port
     *          the port of this server
     */
    void setPort(int port)
    {
      this.port = port;
    }

    /**
     * Returns the password for this server.
     *
     * @return the password for this server
     */
    String getPwd()
    {
      return pwd;
    }

    /**
     * Sets the password for this server.
     *
     * @param pwd
     *          the password for this server
     */
    void setPwd(String pwd)
    {
      this.pwd = pwd;
    }

    /**
     * Returns the dn to be used to bind to this server.
     *
     * @return the dn to be used to bind to this server
     */
    String getBindDn()
    {
      return bindDn;
    }

    /**
     * Sets the dn to be used to bind to this server.
     *
     * @param bindDn
     *          the dn to be used to bind to this server
     */
    void setBindDn(String bindDn)
    {
      this.bindDn = bindDn;
    }

    /**
     * Returns the replication port to be used on this server if it is not defined yet.
     *
     * @return the replication port to be used on this server if it is not defined yet
     */
    int getReplicationPort()
    {
      return replicationPort;
    }

    /**
     * Sets the replication port to be used on this server if it is not defined yet.
     *
     * @param replicationPort
     *          the replication port to be used on this server if it is not defined yet.
     */
    void setReplicationPort(int replicationPort)
    {
      this.replicationPort = replicationPort;
    }

    /**
     * Returns whether the user asked to have secure replication communication with this server.
     *
     * @return {@code true} if the user asked to have secure replication communication with the
     *         second server, {@code false} otherwise.
     */
    boolean isSecureReplication()
    {
      return secureReplication;
    }

    /**
     * Sets whether to use secure replication communication with this server.
     *
     * @param secureReplication
     *          whether to use secure replication communication with this server .
     */
    void setSecureReplication(boolean secureReplication)
    {
      this.secureReplication = secureReplication;
    }

    /**
     * Returns whether the user asked to configure the replication server on this server.
     *
     * @return whether the user asked to configure the replication server on this server
     */
    boolean configureReplicationServer()
    {
      return configureReplicationServer;
    }

    /**
     * Sets whether to configure the replication server on this server.
     *
     * @param configureReplicationServer
     *          whether to configure the replication server on this server
     */
    void setConfigureReplicationServer(boolean configureReplicationServer)
    {
      this.configureReplicationServer = configureReplicationServer;
    }

    /**
     * Returns whether the user asked to configure the replication domain on this server.
     *
     * @return whether the user asked to configure the replication domain on this server
     */
    boolean configureReplicationDomain()
    {
      return configureReplicationDomain;
    }

    /**
     * Sets whether to configure the replication domain on this server.
     *
     * @param configureReplicationDomain
     *          whether to configure the replication domain on this server
     */
    void setConfigureReplicationDomain(boolean configureReplicationDomain)
    {
      this.configureReplicationDomain = configureReplicationDomain;
    }
  }

  private EnableReplicationServerData server1 = new EnableReplicationServerData();
  private EnableReplicationServerData server2 = new EnableReplicationServerData();
  private boolean replicateSchema = true;

  /**
   * Returns <CODE>true</CODE> if the user asked to replicate schema and <CODE>
   * false</CODE> otherwise.
   * @return <CODE>true</CODE> if the user asked to replicate schema and <CODE>
   * false</CODE> otherwise.
   */
  public boolean replicateSchema()
  {
    return replicateSchema;
  }

  /**
   * Sets whether to replicate schema or not.
   * @param replicateSchema whether to replicate schema or not.
   */
  public void setReplicateSchema(boolean replicateSchema)
  {
    this.replicateSchema = replicateSchema;
  }

  /**
   * Returns the data for enabling replication on first server.
   *
   * @return the data for enabling replication on first server
   */
  EnableReplicationServerData getServer1()
  {
    return server1;
  }

  /**
   * Returns the data for enabling replication on second server.
   *
   * @return the data for enabling replication on second server
   */
  EnableReplicationServerData getServer2()
  {
    return server2;
  }
}
