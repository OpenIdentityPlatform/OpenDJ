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

package org.opends.guitools.replicationcli;

/**
 * This class is used to store the information provided by the user to enable
 * replication.  It is required because when we are in interactive mode the
 * ReplicationCliArgumentParser is not enough.
 *
 */
class EnableReplicationUserData extends ReplicationUserData
{
  private String hostName1;
  private int port1;
  private String bindDn1;
  private String pwd1;
  private boolean useStartTLS1;
  private boolean useSSL1;
  private int replicationPort1;
  private String hostName2;
  private int port2;
  private String pwd2;
  private String bindDn2;
  private boolean useStartTLS2;
  private boolean useSSL2;
  private int replicationPort2;

  /**
   * Returns the host name of the first server.
   * @return the host name of the first server.
   */
  String getHostName1()
  {
    return hostName1;
  }

  /**
   * Sets the host name of the first server.
   * @param hostName1 the host name of the first server.
   */
  void setHostName1(String hostName1)
  {
    this.hostName1 = hostName1;
  }

  /**
   * Returns the port of the first server.
   * @return the port of the first server.
   */
  int getPort1()
  {
    return port1;
  }

  /**
   * Sets the port of the first server.
   * @param port1 the port of the first server.
   */
  void setPort1(int port1)
  {
    this.port1 = port1;
  }

  /**
   * Returns the password for the first server.
   * @return the password for the first server.
   */
  String getPwd1()
  {
    return pwd1;
  }

  /**
   * Sets the password for the first server.
   * @param pwd1 the password for the first server.
   */
  void setPwd1(String pwd1)
  {
    this.pwd1 = pwd1;
  }

  /**
   * Returns <CODE>true</CODE> if we must use SSL to connect to the first
   * server and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must use SSL to connect to the first
   * server and <CODE>false</CODE> otherwise.
   */
  boolean useSSL1()
  {
    return useSSL1;
  }

  /**
   * Sets whether we must use SSL to connect to the first server or not.
   * @param useSSL1 whether we must use SSL to connect to the first server or
   * not.
   */
  void setUseSSL1(boolean useSSL1)
  {
    this.useSSL1 = useSSL1;
  }

  /**
   * Returns <CODE>true</CODE> if we must use StartTLS to connect to the first
   * server and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must use StartTLS to connect to the first
   * server and <CODE>false</CODE> otherwise.
   */
  boolean useStartTLS1()
  {
    return useStartTLS1;
  }

  /**
   * Sets whether we must use StartTLS to connect to the first server or not.
   * @param useStartTLS1 whether we must use SSL to connect to the first server
   * or not.
   */
  void setUseStartTLS1(boolean useStartTLS1)
  {
    this.useStartTLS1 = useStartTLS1;
  }


  /**
   * Returns the host name of the second server.
   * @return the host name of the second server.
   */
  String getHostName2()
  {
    return hostName2;
  }

  /**
   * Sets the host name of the second server.
   * @param host2Name the host name of the second server.
   */
  void setHostName2(String host2Name)
  {
    this.hostName2 = host2Name;
  }

  /**
   * Returns the port of the second server.
   * @return the port of the second server.
   */
  int getPort2()
  {
    return port2;
  }

  /**
   * Sets the port of the second server.
   * @param port2 the port of the second server.
   */
  void setPort2(int port2)
  {
    this.port2 = port2;
  }

  /**
   * Returns the password for the second server.
   * @return the password for the second server.
   */
  String getPwd2()
  {
    return pwd2;
  }

  /**
   * Sets the password for the second server.
   * @param pwd2 the password for the second server.
   */
  void setPwd2(String pwd2)
  {
    this.pwd2 = pwd2;
  }

  /**
   * Returns <CODE>true</CODE> if we must use SSL to connect to the second
   * server and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must use SSL to connect to the second
   * server and <CODE>false</CODE> otherwise.
   */
  boolean useSSL2()
  {
    return useSSL2;
  }

  /**
   * Sets whether we must use SSL to connect to the second server or not.
   * @param useSSL2 whether we must use SSL to connect to the second server or
   * not.
   */
  void setUseSSL2(boolean useSSL2)
  {
    this.useSSL2 = useSSL2;
  }

  /**
   * Returns <CODE>true</CODE> if we must use StartTLS to connect to the second
   * server and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must use StartTLS to connect to the second
   * server and <CODE>false</CODE> otherwise.
   */
  boolean useStartTLS2()
  {
    return useStartTLS2;
  }

  /**
   * Sets whether we must use StartTLS to connect to the second server or not.
   * @param useStartTLS2 whether we must use SSL to connect to the second server
   * or not.
   */
  void setUseStartTLS2(boolean useStartTLS2)
  {
    this.useStartTLS2 = useStartTLS2;
  }

  /**
   * Returns the dn to be used to bind to the first server.
   * @return the dn to be used to bind to the first server.
   */
  String getBindDn1()
  {
    return bindDn1;
  }

  /**
   * Sets the dn to be used to bind to the first server.
   * @param bindDn1 the dn to be used to bind to the first server.
   */
  void setBindDn1(String bindDn1)
  {
    this.bindDn1 = bindDn1;
  }

  /**
   * Returns the dn to be used to bind to the second server.
   * @return the dn to be used to bind to the second server.
   */
  String getBindDn2()
  {
    return bindDn2;
  }

  /**
   * Sets the dn to be used to bind to the second server.
   * @param bindDn2 the dn to be used to bind to the second server.
   */
  void setBindDn2(String bindDn2)
  {
    this.bindDn2 = bindDn2;
  }

  /**
   * Returns the replication port to be used on the first server if it is not
   * defined yet.
   * @return the replication port to be used on the first server if it is not
   * defined yet.
   */
  int getReplicationPort1()
  {
    return replicationPort1;
  }

  /**
   * Sets the replication port to be used on the first server if it is not
   * defined yet.
   * @param replicationPort1 the replication port to be used on the first server
   * if it is not defined yet.
   */
  void setReplicationPort1(int replicationPort1)
  {
    this.replicationPort1 = replicationPort1;
  }

  /**
   * Returns the replication port to be used on the second server if it is not
   * defined yet.
   * @return the replication port to be used on the second server if it is not
   * defined yet.
   */
  int getReplicationPort2()
  {
    return replicationPort2;
  }

  /**
   * Sets the replication port to be used on the second server if it is not
   * defined yet.
   * @param replicationPort2 the replication port to be used on the second
   * server if it is not defined yet.
   */
  void setReplicationPort2(int replicationPort2)
  {
    this.replicationPort2 = replicationPort2;
  }
}
