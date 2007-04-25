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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import org.opends.quicksetup.util.Utils;

/**
 * Represents user specified input data to an application.
 */
public class UserData {

  private String serverLocation;
  private int serverPort;
  private String directoryManagerDn;
  private String directoryManagerPwd;
  private DataOptions dataOptions;
  private SecurityOptions securityOptions;
  private int serverJMXPort;
  private boolean startServer;
  private boolean stopServer;

  /**
   * Creates a user data object with default values.
   */
  public UserData() {
    startServer = true;

    DataOptions defaultDataOptions = new DefaultDataOptions();

    setServerLocation(Utils.getDefaultServerLocation());
    // See what we can propose as port
    int defaultPort = getDefaultPort();
    if (defaultPort != -1)
    {
      setServerPort(defaultPort);
    }

    setDirectoryManagerDn("cn=Directory Manager");

    setDataOptions(defaultDataOptions);
    SecurityOptions sec = SecurityOptions.createNoCertificateOptions();
    sec.setSslPort(getDefaultSslPort());
    sec.setCertificateUserName(getDefaultSelfSignedName());
    setSecurityOptions(sec);
  }

  /**
   * Sets the location of the server (installation path).
   * @param serverLocation the new server location (installation path).
   */
  public void setServerLocation(String serverLocation)
  {
    this.serverLocation = serverLocation;
  }

  /**
   * Returns the location of the server (installation path).
   * @return the location of the server (installation path).
   */
  public String getServerLocation()
  {
    return serverLocation;
  }

  /**
   * Sets the server LDAP port.
   * @param serverPort the new server LDAP port.
   */
  public void setServerPort(int serverPort)
  {
    this.serverPort = serverPort;
  }

  /**
   * Returns the server LDAP port.
   * @return the server LDAP port.
   */
  public int getServerPort()
  {
    return serverPort;
  }

  /**
   * Returns the Directory Manager DN.
   * @return the Directory Manager DN.
   */
  public String getDirectoryManagerDn()
  {
    return directoryManagerDn;
  }

  /**
   * Sets the new Directory Manager DN.
   * @param directoryManagerDn the new Directory Manager DN.
   */
  public void setDirectoryManagerDn(String directoryManagerDn)
  {
    this.directoryManagerDn = directoryManagerDn;
  }

  /**
   * Returns the Directory Manager password.
   * @return the Directory Manager password.
   */
  public String getDirectoryManagerPwd()
  {
    return directoryManagerPwd;
  }

  /**
   * Sets the new Directory Manager password.
   * @param directoryManagerPwd the new Directory Manager password.
   */
  public void setDirectoryManagerPwd(String directoryManagerPwd)
  {
    this.directoryManagerPwd = directoryManagerPwd;
  }

  /**
   * Returns the DataOptions object representing the data in the Data Options
   * panel.
   * @return the DataOptions object representing the data in the Data Options
   * panel.
   */
  public DataOptions getDataOptions()
  {
    return dataOptions;
  }

  /**
   * Sets the DataOptions object representing the data in the Data Options
   * panel.
   * @param dataOptions the DataOptions object representing the data in the Data
   * Options panel.
   */
  public void setDataOptions(DataOptions dataOptions)
  {
    this.dataOptions = dataOptions;
  }

  /**
   * Sets the server JMX port.
   * @param serverJMXPort the new server JMX port.
   */
  public void setServerJMXPort(int serverJMXPort)
  {
    this.serverJMXPort = serverJMXPort;
  }

  /**
   * Returns the server JMX port.
   * @return the server JMX port.
   */
  public int getServerJMXPort()
  {
    return serverJMXPort;
  }

  /**
   * Returns <CODE>true</CODE> if the server must be started once the
   * installation is finished, <CODE>false</CODE> if not.
   * @return <CODE>true</CODE> if the server must be started once the
   * installation is finished, <CODE>false</CODE> if not.
   */
  public boolean getStartServer()
  {
    return startServer;
  }

  /**
   * Sets whether we want to start the server once the installation is finished
   * or not.
   * @param startServer the boolean indicating whether to start the server or
   * not.
   */
  public void setStartServer(boolean startServer)
  {
    this.startServer = startServer;
  }

  /**
   * Sets whether to stop the server or not.
   * @param stopServer stop the server or not.
   */
  public void setStopServer(boolean stopServer)
  {
    this.stopServer = stopServer;
  }

  /**
   * Returns whether the user wants to stop the server or not.
   * @return <CODE>true</CODE> if the user wants to stop the server and <CODE>\
   * false</CODE> otherwise.
   */
  public boolean getStopServer()
  {
    return stopServer;
  }

  /**
   * Returns the SecurityOptions representing the SSL/StartTLS configuration
   * chosen by the user.
   * @return the SecurityOptions representing the SSL/StartTLS configuration
   * chosen by the user.
   */
  public SecurityOptions getSecurityOptions()
  {
    return securityOptions;
  }

  /**
   * Sets the SecurityOptions representing the SSL/StartTLS configuration
   * chosen by the user.
   * @param securityOptions the SecurityOptions representing the SSL/StartTLS
   * configuration chosen by the user.
   */
  public void setSecurityOptions(SecurityOptions securityOptions)
  {
    this.securityOptions = securityOptions;
  }

  /**
   * Provides the port that will be proposed to the user in the second page of
   * the installation wizard. It will check whether we can use ports of type
   * X389 and if not it will return -1.
   *
   * @return the free port of type x389 if it is available and we can use and -1
   * if not.
   */
  static public int getDefaultPort()
  {
    int defaultPort = -1;

    for (int i=0;i<10000 && (defaultPort == -1);i+=1000)
    {
      int port = i + 389;
      if (Utils.canUseAsPort(port))
      {
        defaultPort = port;
      }
    }
    return defaultPort;
  }

  /**
   * Provides the port that will be proposed to the user in the security dialog
   *  of the installation wizard. It will check whether we can use ports of type
   * X636 and if not it will return -1.
   *
   * @return the free port of type X636 if it is available and we can use and -1
   * if not.
   */
  static int getDefaultSslPort()
  {
    int defaultPort = -1;

    for (int i=0;i<10000 && (defaultPort == -1);i+=1000)
    {
      int port = i + 636;
      if (Utils.canUseAsPort(port))
      {
        defaultPort = port;
      }
    }
    return defaultPort;
  }

  /**
   * Provides the port that will be used by default for JMX.
   *
   * @param forbiddenPorts an array of ports that we cannot use.
   * @return the port X689 if it is available and we can use and -1 if not.
   */
  static public int getDefaultJMXPort(int[] forbiddenPorts)
  {
    int defaultJMXPort = -1;

    for (int i=0;i<65000 && (defaultJMXPort == -1);i+=1000)
    {
      int port = i + org.opends.server.util.SetupUtils.getDefaultJMXPort();
      boolean isForbidden = false;
      if (forbiddenPorts != null)
      {
        for (int j=0; j<forbiddenPorts.length && !isForbidden; j++)
        {
          isForbidden = forbiddenPorts[j] == port;
        }
      }
      if (!isForbidden && Utils.canUseAsPort(port))
      {
        defaultJMXPort = port;
      }
    }
    return defaultJMXPort;
  }

  /**
   * Provides the default name for the self signed certificate that will be
   * created.
   */
  private String getDefaultSelfSignedName()
  {
    String name = "";
    try
    {
      name = java.net.InetAddress.getLocalHost().getHostName();
    }
    catch (Throwable t)
    {
    }
    return name;
  }
}
