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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.opends.server.loggers.debug.DebugLogger.*;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.AckMsg;

/**
 * This class holds every info needed about the expected acks for a received
 * update message requesting assured replication with Safe Data sub-mode.
 * It also includes info/routines for constructing the final ack to be sent to
 * the sender of the update message.
 */
public class SafeDataExpectedAcksInfo extends ExpectedAcksInfo
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Requested level of safe data when the update message was received.
  private byte safeDataLevel = (byte)-1;

  // Number of received acks for the matching update message, up to now
  // Already set to 1 as the local RS receiving the message from a DS counts.
  private byte numReceivedAcks = (byte)1;

  /**
   * Creates a new SafeDataExpectedAcksInfo.
   * @param changeNumber The change number of the assured update message
   * @param requesterServerHandler The server that sent the assured update
   * message
   * @param safeDataLevel The Safe Data level requested for the assured
   * update message
   * @param expectedServers The list of servers we want an ack from
   */
  public SafeDataExpectedAcksInfo(ChangeNumber changeNumber,
    ServerHandler requesterServerHandler, byte safeDataLevel,
    List<Short> expectedServers)
  {
    super(changeNumber, requesterServerHandler, AssuredMode.SAFE_DATA_MODE,
      expectedServers);
    this.safeDataLevel = safeDataLevel;
  }

  /**
   * {@inheritDoc}
   */
   public boolean processReceivedAck(ServerHandler ackingServer, AckMsg ackMsg)
  {
    /*
     * Security: although a DS should not respond to an update message sent to
     * him with assured safe data mode, we double check here that the ack sender
     * is a RS to take the ack into account.
     */
     if (ackingServer.isLDAPserver())
     {
       // Sanity check: this should never happen
        if (debugEnabled())
          TRACER.debugInfo("Received unexpected SD ack from DS id: "
          + ackingServer.getServerId() + " ack message: " + ackMsg);
        return false;
     }

    // Get the ack status for the matching server
    short ackingServerId = ackingServer.getServerId();
    boolean ackReceived = expectedServersAckStatus.get(ackingServerId);
    if (ackReceived)
    {
      // Sanity check: this should never happen
      if (debugEnabled())
        TRACER.debugInfo("Received unexpected ack from server id: " +
          ackingServerId + " ack message: " + ackMsg);
      return false;
    } else
    {

      // Mark this ack received for the server
      expectedServersAckStatus.put(ackingServerId, true);
      numReceivedAcks++;
      if (numReceivedAcks == safeDataLevel)
        return true;
      else
        return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  public AckMsg createAck(boolean timeout)
  {
    AckMsg ack = new AckMsg(changeNumber);

    if (timeout)
    {
      // Fill collected errors info
      ack.setHasTimeout(true);
      // Tell wich servers did not send an ack in time
      List<Short> failedServers = new ArrayList<Short>();
      Set<Short> serverIds = expectedServersAckStatus.keySet();
      serversInTimeout = new ArrayList<Short>(); // Use next loop to fill it
      for (Short serverId : serverIds)
      {
        boolean ackReceived = expectedServersAckStatus.get(serverId);
        if (!ackReceived)
        {
          failedServers.add(serverId);
          serversInTimeout.add(serverId);
        }
      }
      ack.setFailedServers(failedServers);
    }

    return ack;
  }
}
