/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.AckMsg;

/**
 * This class holds every info needed about the expected acks for a received
 * update message requesting assured replication with Safe Data sub-mode.
 * It also includes info/routines for constructing the final ack to be sent to
 * the sender of the update message.
 */
public class SafeDataExpectedAcksInfo extends ExpectedAcksInfo
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Requested level of safe data when the update message was received. */
  private byte safeDataLevel = -1;

  /**
   * Number of received acks for the matching update message, up to now Already
   * set to 1 as the local RS receiving the message from a DS counts.
   */
  private byte numReceivedAcks = 1;

  /**
   * Creates a new SafeDataExpectedAcksInfo.
   * @param csn The CSN of the assured update message
   * @param requesterServerHandler The server that sent the assured update
   * message
   * @param safeDataLevel The Safe Data level requested for the assured
   * update message
   * @param expectedServers The list of servers we want an ack from
   */
  public SafeDataExpectedAcksInfo(CSN csn,
    ServerHandler requesterServerHandler, byte safeDataLevel,
    List<Integer> expectedServers)
  {
    super(csn, requesterServerHandler, AssuredMode.SAFE_DATA_MODE,
      expectedServers);
    this.safeDataLevel = safeDataLevel;
  }

  /** {@inheritDoc} */
  @Override
  public boolean processReceivedAck(ServerHandler ackingServer, AckMsg ackMsg)
  {
    /*
     * Security: although a DS should not respond to an update message sent to
     * him with assured safe data mode, we double check here that the ack sender
     * is a RS to take the ack into account.
     */
     if (ackingServer.isDataServer())
     {
       // Sanity check: this should never happen
        if (logger.isTraceEnabled())
        {
          logger.trace("Received unexpected SD ack from DS id: "
          + ackingServer.getServerId() + " ack message: " + ackMsg);
        }
        return false;
     }

    // Get the ack status for the matching server
    int ackingServerId = ackingServer.getServerId();
    boolean ackReceived = expectedServersAckStatus.get(ackingServerId);
    if (ackReceived)
    {
      // Sanity check: this should never happen
      if (logger.isTraceEnabled())
      {
        logger.trace("Received unexpected ack from server id: " +
          ackingServerId + " ack message: " + ackMsg);
      }
      return false;
    } else
    {
      // Mark this ack received for the server
      expectedServersAckStatus.put(ackingServerId, true);
      numReceivedAcks++;
      return numReceivedAcks == safeDataLevel;
    }
  }

  /** {@inheritDoc} */
  @Override
  public AckMsg createAck(boolean timeout)
  {
    AckMsg ack = new AckMsg(csn);

    if (timeout)
    {
      // Fill collected errors info
      ack.setHasTimeout(true);
      // Tell which servers did not send an ack in time
      List<Integer> failedServers = new ArrayList<>();
      Set<Integer> serverIds = expectedServersAckStatus.keySet();
      serversInTimeout = new ArrayList<>(); // Use next loop to fill it
      for (Integer serverId : serverIds)
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
