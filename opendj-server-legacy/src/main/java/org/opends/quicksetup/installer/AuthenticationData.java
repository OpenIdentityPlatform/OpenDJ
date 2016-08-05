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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.quicksetup.installer;

import static org.opends.admin.ads.util.PreferredConnection.Type.*;

import org.forgerock.opendj.ldap.DN;
import org.opends.admin.ads.util.PreferredConnection.Type;
import org.opends.server.types.HostPort;

/**
 * This class is used to provide a data model for the different parameters used
 * to connect to a server that we want to replicate contents with.
 *
 * @see DataReplicationOptions
 */
public class AuthenticationData
{
  private HostPort hostPort = new HostPort(null, 0);
  private DN dn;
  private String pwd;
  private Type connectionType;

  /**
   * Sets the server LDAP port.
   * @param port the server LDAP port.
   */
  public void setPort(int port)
  {
    hostPort = new HostPort(hostPort.getHost(), port);
  }

  /**
   * Returns the server LDAP port.
   * @return the server LDAP port.
   */
  public int getPort()
  {
    return getHostPort().getPort();
  }

  /**
   * Returns the Authentication DN.
   * @return the Authentication DN.
   */
  public DN getDn()
  {
    return dn;
  }

  /**
   * Sets the Authentication DN.
   * @param dn the Authentication DN.
   */
  public void setDn(DN dn)
  {
    this.dn = dn;
  }

  /**
   * Returns the authentication password.
   * @return the authentication password.
   */
  public String getPwd()
  {
    return pwd;
  }

  /**
   * Sets the authentication password.
   * @param pwd the authentication password.
   */
  public void setPwd(String pwd)
  {
    this.pwd = pwd;
  }

  /**
   * Returns the host name and port to connect to.
   * @return the host name and port to connect to.
   */
  public HostPort getHostPort()
  {
    return hostPort;
  }

  /**
   * Sets the host name to connect to.
   * @param hostport the host name and port to connect to.
   */
  public void setHostPort(HostPort hostport)
  {
    this.hostPort = hostport;
  }

  /**
   * Returns whether to use a secure connection or not.
   * @return {@code true} if we must use a secure connection, {@code false} otherwise.
   */
  public boolean useSecureConnection()
  {
    return connectionType == LDAPS;
  }

  /**
   * Tells to use a secure connection or not.
   * @param useSecureConnection use a secure connection or not.
   */
  public void setUseSecureConnection(boolean useSecureConnection)
  {
    connectionType = useSecureConnection ? LDAPS : LDAP;
  }

  Type getConnectionType()
  {
    return connectionType;
  }
}
