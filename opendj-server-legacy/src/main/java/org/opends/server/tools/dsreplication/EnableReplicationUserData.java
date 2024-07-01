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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.tools.dsreplication;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.HostPort;

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
    private HostPort hostPort = new HostPort(null, 0);
    private DN bindDn;
    private String pwd;
    private int replicationPort;
    private boolean secureReplication;
    private boolean configureReplicationServer = true;
    private boolean configureReplicationDomain = true;

    /**
     * Returns the host name and port of this server.
     *
     * @return the host name and port of this server.
     */
    HostPort getHostPort()
    {
      return hostPort;
    }

    /**
     * Sets the host name and port of this server.
     *
     * @param hostPort
     *          the host name and port of this server
     */
    void setHostPort(HostPort hostPort)
    {
      this.hostPort = hostPort;
    }

    /**
     * Returns the host name of this server.
     *
     * @return the host name of this server.
     */
    String getHostName()
    {
      return hostPort.getHost();
    }

    /**
     * Returns the port of this server.
     *
     * @return the port of this server
     */
    int getPort()
    {
      return hostPort.getPort();
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
    DN getBindDn()
    {
      return bindDn;
    }

    /**
     * Sets the dn to be used to bind to this server.
     *
     * @param bindDn
     *          the dn to be used to bind to this server
     */
    void setBindDn(DN bindDn)
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

    @Override
    public String toString()
    {
      // do not add password to avoid accidental logging
      return getClass().getSimpleName()
          + "(hostPort=" + hostPort
          + ", bindDn=" + bindDn
          + ", replicationPort=" + replicationPort
          + ", secureReplication=" + secureReplication
          + ", configureReplicationServer=" + configureReplicationServer
          + ", configureReplicationDomain=" + configureReplicationDomain
          + ")";
    }
  }

  private final EnableReplicationServerData server1 = new EnableReplicationServerData();
  private final EnableReplicationServerData server2 = new EnableReplicationServerData();
  private boolean replicateSchema = true;

  /**
   * Returns whether the user asked to replicate schema.
   * @return {@code true} if the user asked to replicate schema, {@code false} otherwise.
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

  @Override
  public String toString()
  {
    return getClass().getSimpleName()
        + "("
        + fieldsToString()
        + ", replicateSchema=" + replicateSchema
        + ", server1=" + server1
        + ", server2=" + server2
        + ")";
  }
}
