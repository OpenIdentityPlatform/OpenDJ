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
 * Parameters to establish connections to a directory server.
 */
public final class ConnectionParameters
{
  private String adminPassword;
  private Integer adminPort;
  private String adminUid;
  private String bindDn;
  private String bindPassword;
  private String hostname;
  private Integer ldapPort;

  private ConnectionParameters()
  {
    // private constructor to force usage of the associated Builder
  }

  /**
   * Creates a builder for the connection parameters.
   *
   * @return a builder
   */
  public static Builder connectionParams()
  {
    return new Builder();
  }

  String getAdminPassword()
  {
    return adminPassword;
  }

  Integer getAdminPort()
  {
    return adminPort;
  }

  String getAdminUid()
  {
    return adminUid;
  }

  String getBindDn()
  {
    return bindDn;
  }

  String getBindPassword()
  {
    return bindPassword;
  }

  String getHostname()
  {
    return hostname;
  }

  Integer getLdapPort()
  {
    return ldapPort;
  }

  /**
   * Builder for the ConnectionParameters class.
   */
  public static final class Builder
  {
    private ConnectionParameters params;

    private Builder()
    {
      params = new ConnectionParameters();
    }

    /**
     * Generates the parameters from this builder.
     * <p>
     * After this call, the builder is reset and can be used to generate other parameters.
     *
     * @return the replication parameters
     */
    public ConnectionParameters toParams()
    {
      ConnectionParameters p = params;
      this.params = new ConnectionParameters();
      return p;
    }

    /**
     * Sets the password of the Global Administrator to use to bind to the server.
     *
     * @param password
     *          the password
     * @return this builder
     */
    public Builder adminPassword(String password)
    {
      params.adminPassword = password;
      return this;
    }

    /**
     * Sets the port for directory server administration.
     *
     *  @param port
     *          the admin port
     * @return this builder
     */
    public Builder adminPort(int port)
    {
      params.adminPort = port;
      return this;
    }

    /**
     * Sets the user id of the Global Administrator to use to bind to the server.
     *
     * @param uid
     *          the user id
     * @return this builder
     */
    public Builder adminUid(String uid)
    {
      params.adminUid = uid;
      return this;
    }

    /**
     * Sets the Dn to use to bind to the directory server.
     *
     * @param dn
     *          the bind Dn
     * @return this builder
     */
    public Builder bindDn(String dn)
    {
      params.bindDn = dn;
      return this;
    }

    /**
     * Sets the password to use to bind to the directory server.
     *
     * @param password
     *          the bind password
     * @return this builder
     */
    public Builder bindPassword(String password)
    {
      params.bindPassword = password;
      return this;
    }

    /**
     * Sets the the fully-qualified directory server host name.
     *
     * @param hostname
     *          the hostname of the server
     * @return this builder
     */
    public Builder hostname(String hostname)
    {
      params.hostname = hostname;
      return this;
    }

    /**
     * Sets the port on which the directory server listen for LDAP communication.
     *
     * @param port
     *          the LDAP port
     * @return this builder
     */
    public Builder ldapPort(int port)
    {
      params.ldapPort = port;
      return this;
    }
  }
}
