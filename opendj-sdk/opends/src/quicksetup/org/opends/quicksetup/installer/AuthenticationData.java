/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer;

/**
 * This class is used to provide a data model for the different parameters used
 * to connect to a server that we want to replicate contents with.
 *
 * @see DataReplicationOptions
 *
 */
public class AuthenticationData
{
  private String hostName;

  private int port;

  private String dn;

  private String pwd;

  private boolean useSecureConnection;

  /**
   * Sets the server LDAP port.
   * @param port the server LDAP port.
   */
  public void setPort(int port)
  {
    this.port = port;
  }

  /**
   * Returns the server LDAP port.
   * @return the server LDAP port.
   */
  public int getPort()
  {
    return port;
  }

  /**
   * Returns the Authentication DN.
   * @return the Authentication DN.
   */
  public String getDn()
  {
    return dn;
  }

  /**
   * Sets the Authentication DN.
   * @param dn the Authentication DN.
   */
  public void setDn(String dn)
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
   * Returns the host name to connect to.
   * @return the host name to connect to.
   */
  public String getHostName()
  {
    return hostName;
  }

  /**
   * Sets the host name to connect to.
   * @param hostName the host name to connect to.
   */
  public void setHostName(String hostName)
  {
    this.hostName = hostName;
  }

  /**
   * Returns whether to use a secure connection or not.
   * @return <CODE>true</CODE> if we must use a secure connection and
   * <CODE>false</CODE> otherwise.
   */
  public boolean useSecureConnection()
  {
    return useSecureConnection;
  }

  /**
   * Tells to use a secure connection or not.
   * @param useSecureConnection use a secure connection or not.
   */
  public void setUseSecureConnection(boolean useSecureConnection)
  {
    this.useSecureConnection = useSecureConnection;
  }
}
