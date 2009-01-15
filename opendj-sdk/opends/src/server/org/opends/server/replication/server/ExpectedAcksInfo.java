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

package org.opends.server.replication.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.AckMsg;

/**
 * This class is the mother class for sub-classes holding any information needed
 * about the acks that the replication server will wait for, when he receives an
 * update message with the assured flag on (assured replication acknowledgments
 * expected).
 * It also includes info/routines for constructing the final ack to be sent to
 * the sender of the update message.
 *
 * It is expected to have one sub-class per assured replication sub mode.
 */
public abstract class ExpectedAcksInfo
{
  // The server handler of the server that sent the assured update message and
  // to whow we want to return the final ack
  private ServerHandler requesterServerHandler = null;

  // The requested assured mode of matcching update message
  private AssuredMode assuredMode = null;

  /**
   * The change number of the assured update message we want acks for.
   */
  protected ChangeNumber changeNumber = null;

  /**
   * Is the treatment of the acks for the update message completed or not ?
   * This is used for concurrent access to this object by either the assured
   * timeout task or the code for processing an ack for the matching update
   * message. This should be set to true when the treatment of the expected
   * acks is completed or an ack timeout has occured and we are going to remove
   * this object from the map where it is stored.
   */
  private boolean completed = false;

  /**
   * This gives the list of servers we are willing to wait acks from and the
   * information about the ack from the servers.
   * key: the id of the server.
   * value: a boolean true if we received the ack from the server,
   * false otherwise.
   */
  protected Map<Short,Boolean> expectedServersAckStatus =
    new HashMap<Short,Boolean>();

  /**
   * Facility for monitoring:
   * If the timeout occurs for the original update, we call createAck(true)
   * in the timeout code for sending back an error ack to the original server.
   * We use this call to also save the list of server ids for server we did not
   * have time to receive an ack from. For updating its counters, the timeout
   * code can then call getTimeoutServers() method to now which servers did not
   * respond in time.
   */
  protected List<Short> serversInTimeout = null;

  /**
   * Creates a new ExpectedAcksInfo.
   * @param changeNumber The change number of the assured update message
   * @param requesterServerHandler The server handler of the server that sent
   * the assured update message
   * @param assuredMode The assured mode requested by the assured update message
   * @param expectedServers The list of servers we want an ack from
   */
  protected ExpectedAcksInfo(ChangeNumber changeNumber,
    ServerHandler requesterServerHandler, AssuredMode assuredMode,
    List<Short> expectedServers)
  {
    this.requesterServerHandler = requesterServerHandler;
    this.assuredMode = assuredMode;
    this.changeNumber = changeNumber;

    // Initialize list of servers we expect acks from
    for (Short serverId : expectedServers)
    {
      expectedServersAckStatus.put(serverId, false);
    }
  }

  /**
   * Gets the server handler of the server which requested the acknowledgments.
   * @return The server handler of the server which requested the
   * acknowledgments.
   */
  public ServerHandler getRequesterServer()
  {
    return requesterServerHandler;
  }

  /**
   * Gets the list of expected servers that did not respond in time.
   * @return The list of expected servers that did not respond in time.
   */
  public List<Short> getTimeoutServers()
  {
    return serversInTimeout;
  }

  /**
   * Gets the requested assured mode for the matching update message.
   * @return The requested assured mode for the matching update message.
   */
  public AssuredMode getAssuredMode()
  {
    return assuredMode;
  }

  /**
   * Process the received ack from a server we are waiting an ack from.
   * @param ackingServer The server handler of the server that sent the ack
   * @param ackMsg The ack message to process
   * @return True if the expected number of acks has just been reached
   */
  public abstract boolean processReceivedAck(ServerHandler ackingServer,
    AckMsg ackMsg);

  /**
   * Creates the ack message to be returned to the requester server, taking into
   * account the information in the received acks from every servers.
   * @param timeout True if we call this method when the timeout occurred, that
   * is we did not received every expected acks in time, and thus, the timeout
   * flag should also be enabled in the returned ack message.
   * @return The ack message ready to be sent to the requester server
   */
  public abstract AckMsg createAck(boolean timeout);

  /**
   * Has the treatment of this object been completed or not?
   * If true is returned, one must not modify this object (useless) nor remove
   * it from the map where it is stored (will be or has already been done by the
   * other code (ack timeout code, or ack processing code)).
   * @return True if treatment of this object has been completed.
   */
  public boolean isCompleted()
  {
    return completed;
  }

  /**
   * Signal that treatment of this object has been completed and that it is
   * going to be removed from the map where it is stored.
   */
  public void completed()
  {
    completed = true;
  }
}
