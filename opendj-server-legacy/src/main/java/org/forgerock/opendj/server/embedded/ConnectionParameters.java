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

/** Parameters to establish connections to a directory server. */
public final class ConnectionParameters
{
  private String adminPassword;
  private Integer adminPort;
  private String adminUid;
  private String bindDn;
  private String bindPassword;
  private String hostName;
  private Integer ldapPort;
  private Integer ldapsPort;

  private ConnectionParameters()
  {
    // prefer usage of static method for creation
  }

  /**
   * Creates connection parameters.
   *
   * @return the parameters
   */
  public static ConnectionParameters connectionParams()
  {
    return new ConnectionParameters();
  }

  @Override
  public String toString()
  {
    return "ConnectionParameters ["
        + "host name=" + hostName
        + ", ldap port=" + ldapPort
        + ", ldaps port=" + ldapsPort
        + ", admin port=" + adminPort
        + ", bind DN=" + bindDn
        + ", admin uid=" + adminUid
        + "]";

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

  String getHostName()
  {
    return hostName;
  }

  Integer getLdapPort()
  {
    return ldapPort;
  }

  Integer getLdapSecurePort()
  {
    return ldapsPort;
  }

  /**
   * Sets the password of the Global Administrator to use to bind to the server.
   *
   * @param password
   *          the password
   * @return this builder
   */
  public ConnectionParameters adminPassword(String password)
  {
    adminPassword = password;
    return this;
  }

  /**
   * Sets the port for directory server administration.
   *
   * @param port
   *          the admin port
   * @return this builder
   */
  public ConnectionParameters adminPort(int port)
  {
    adminPort = port;
    return this;
  }

  /**
   * Sets the user id of the Global Administrator to use to bind to the server.
   *
   * @param uid
   *          the user id
   * @return this builder
   */
  public ConnectionParameters adminUid(String uid)
  {
    adminUid = uid;
    return this;
  }

  /**
   * Sets the Dn to use to bind to the directory server.
   *
   * @param dn
   *          the bind Dn
   * @return this builder
   */
  public ConnectionParameters bindDn(String dn)
  {
    bindDn = dn;
    return this;
  }

  /**
   * Sets the password to use to bind to the directory server.
   *
   * @param password
   *          the bind password
   * @return this builder
   */
  public ConnectionParameters bindPassword(String password)
  {
    bindPassword = password;
    return this;
  }

  /**
   * Sets the the fully-qualified directory server host name.
   *
   * @param hostName
   *          the hostName of the server
   * @return this builder
   */
  public ConnectionParameters hostName(String hostName)
  {
    this.hostName = hostName;
    return this;
  }

  /**
   * Sets the port on which the directory server listens for LDAP communication.
   *
   * @param port
   *          the LDAP port
   * @return this builder
   */
  public ConnectionParameters ldapPort(int port)
  {
    ldapPort = port;
    return this;
  }

  /**
   * Sets the port on which the directory server listens for LDAPS (secure) communication.
   *
   * @param port
   *          the LDAPS port
   * @return this builder
   */
  public ConnectionParameters ldapSecurePort(int port)
  {
    ldapsPort = port;
    return this;
  }
}
