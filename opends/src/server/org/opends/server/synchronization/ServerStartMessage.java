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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.opends.server.types.DN;
import org.opends.server.core.DirectoryException;

/**
 * This message is used by LDAP server when they first connect.
 * to a changelog server to let them know who they are and what is their state
 * (their RUV)
 */
public class ServerStartMessage extends SynchronizationMessage implements
    Serializable
{
  private static final long serialVersionUID = 8649393307038290287L;

  private short ServerId; // Id of the LDAP server that sent this message
  private String baseDn;
  private ServerState serverState = null;
  private int maxReceiveQueue;
  private int maxSendQueue;
  private int maxReceiveDelay;
  private int maxSendDelay;


  // TODO : should have a RUV here

  private String serverURL;

  /**
   * Create a new ServerStartMessage.
   *
   * @param serverId The serverId of the server for which the ServerStartMessage
   *                 is created.
   * @param baseDn   The base DN.
   * @param maxReceiveDelay The max receive delay for this server.
   * @param maxReceiveQueue The max receive Queue for this server.
   * @param maxSendDelay The max Send Delay from this server.
   * @param maxSendQueue The max send Queue from this server.
   * @param serverState  The state of this server.
   */
  public ServerStartMessage(short serverId, DN baseDn, int maxReceiveDelay,
                            int maxReceiveQueue, int maxSendDelay,
                            int maxSendQueue, ServerState serverState)
  {
    this.ServerId = serverId;
    this.baseDn = baseDn.toString();
    this.maxReceiveDelay = maxReceiveDelay;
    this.maxReceiveQueue = maxReceiveQueue;
    this.maxSendDelay = maxSendDelay;
    this.maxSendQueue = maxSendQueue;
    this.serverState = serverState;

    try
    {
      /* TODO : find a better way to get the server URL */
      this.serverURL = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e)
    {
      this.serverURL = "Unknown host";
    }
  }

  /**
   * Get the ServerID from the message.
   * @return the server ID
   */
  public short getServerId()
  {
    return ServerId;
  }

  /**
   * get the Server URL from the message.
   * @return the server URL
   */
  public String getServerURL()
  {
    return serverURL;
  }

  /**
   * Get the baseDn.
   * @return Returns the baseDn.
   */
  public DN getBaseDn()
  {
    try
    {
      return DN.decode(baseDn);
    } catch (DirectoryException e)
    {
      return null;
    }
  }

  /**
   * Get the maxReceiveDelay.
   * @return Returns the maxReceiveDelay.
   */
  public int getMaxReceiveDelay()
  {
    return maxReceiveDelay;
  }

  /**
   * Get the maxReceiveQueue.
   * @return Returns the maxReceiveQueue.
   */
  public int getMaxReceiveQueue()
  {
    return maxReceiveQueue;
  }

  /**
   * Get the maxSendDelay.
   * @return Returns the maxSendDelay.
   */
  public int getMaxSendDelay()
  {
    return maxSendDelay;
  }

  /**
   * Get the maxSendQueue.
   * @return Returns the maxSendQueue.
   */
  public int getMaxSendQueue()
  {
    return maxSendQueue;
  }

  /**
   * Get the ServerState.
   * @return The ServerState.
   */
  public ServerState getServerState()
  {
    return serverState;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UpdateMessage processReceive(SynchronizationDomain domain)
  {
    /*
     * This is currently not used.
     */
    return null;
  }

}
