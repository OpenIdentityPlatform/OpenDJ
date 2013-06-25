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
 * This class is used to store the information provided by the user to
 * initialize replication.  It is required because when we are in interactive
 * mode the ReplicationCliArgumentParser is not enough.
 *
 */
public class InitializeReplicationUserData extends ReplicationUserData
{
  private String hostNameSource;
  private int portSource;
  private String hostNameDestination;
  private int portDestination;

  /**
   * Returns the host name of the source server.
   * @return the host name of the source server.
   */
  public String getHostNameSource()
  {
    return hostNameSource;
  }

  /**
   * Sets the host name of the source server.
   * @param hostNameSource the host name of the source server.
   */
  public void setHostNameSource(String hostNameSource)
  {
    this.hostNameSource = hostNameSource;
  }

  /**
   * Returns the port of the source server.
   * @return the port of the source server.
   */
  public int getPortSource()
  {
    return portSource;
  }

  /**
   * Sets the port of the source server.
   * @param portSource the port of the source server.
   */
  public void setPortSource(int portSource)
  {
    this.portSource = portSource;
  }

  /**
   * Returns the host name of the destination server.
   * @return the host name of the destination server.
   */
  public String getHostNameDestination()
  {
    return hostNameDestination;
  }

  /**
   * Sets the host name of the destination server.
   * @param hostNameDestination the host name of the destination server.
   */
  public void setHostNameDestination(String hostNameDestination)
  {
    this.hostNameDestination = hostNameDestination;
  }

  /**
   * Returns the port of the destination server.
   * @return the port of the destination server.
   */
  public int getPortDestination()
  {
    return portDestination;
  }

  /**
   * Sets the port of the destination server.
   * @param portDestination the port of the destination server.
   */
  public void setPortDestination(int portDestination)
  {
    this.portDestination = portDestination;
  }

}
