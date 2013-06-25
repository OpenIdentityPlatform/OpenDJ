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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.tools.dsreplication;

/**
 * This is an abstract class used for code refactorization.
 *
 */
abstract class MonoServerReplicationUserData extends ReplicationUserData
{
  private String hostName;
  private int port;
  private boolean useStartTLS;
  private boolean useSSL;

  /**
   * Returns the host name of the server.
   * @return the host name of the server.
   */
  public String getHostName()
  {
    return hostName;
  }

  /**
   * Sets the host name of the server.
   * @param hostName the host name of the server.
   */
  public void setHostName(String hostName)
  {
    this.hostName = hostName;
  }

  /**
   * Returns the port of the server.
   * @return the port of the server.
   */
  public int getPort()
  {
    return port;
  }

  /**
   * Sets the port of the server.
   * @param port the port of the server.
   */
  public void setPort(int port)
  {
    this.port = port;
  }
  /**
   * Returns <CODE>true</CODE> if we must use SSL to connect to the server and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must use SSL to connect to the server and
   * <CODE>false</CODE> otherwise.
   */
  boolean useSSL()
  {
    return useSSL;
  }

  /**
   * Sets whether we must use SSL to connect to the server or not.
   * @param useSSL whether we must use SSL to connect to the server or not.
   */
  void setUseSSL(boolean useSSL)
  {
    this.useSSL = useSSL;
  }

  /**
   * Returns <CODE>true</CODE> if we must use StartTLS to connect to the server
   * and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must use StartTLS to connect to the server
   * and <CODE>false</CODE> otherwise.
   */
  boolean useStartTLS()
  {
    return useStartTLS;
  }

  /**
   * Sets whether we must use StartTLS to connect to the server or not.
   * @param useStartTLS whether we must use SSL to connect to the server or not.
   */
  void setUseStartTLS(boolean useStartTLS)
  {
    this.useStartTLS = useStartTLS;
  }
}


