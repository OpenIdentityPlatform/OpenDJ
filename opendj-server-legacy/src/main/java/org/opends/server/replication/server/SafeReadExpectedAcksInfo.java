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
 *      Portions Copyright 2013-2015 ForgeRock AS
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
 * update message requesting assured replication with Safe Read sub-mode.
 * It also includes info/routines for constructing the final ack to be sent to
 * the sender of the update message.
 */
public class SafeReadExpectedAcksInfo extends ExpectedAcksInfo
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Did some servers go in timeout when the matching update was sent ?. */
  private boolean hasTimeout;

  /** Were some servers in wrong status when the matching update was sent ?. */
  private boolean hasWrongStatus;

  /** Did some servers make an error replaying the sent matching update ?. */
  private boolean hasReplayError;

  /**
   * The list of server ids that had errors for the sent matching update Each
   * server id of the list had one of the 3 possible errors (timeout, wrong
   * status or replay error).
   */
  private List<Integer> failedServers = new ArrayList<>();

  /**
   * Number of servers we want an ack from and from which we received the ack.
   * Said differently: the number of servers in expectedServersAckStatus whose
   * value is true. When this value reaches the size of expectedServersAckStatus
   * we can compute an ack message (based on info in this object), to be
   * returned to the (requester) server that sent us an assured update message.
   */
  private int numKnownAckStatus;

  /**
   * Creates a new SafeReadExpectedAcksInfo.
   * @param csn The CSN of the assured update message
   * @param requesterServerHandler The server that sent the assured update
   * message
   * @param expectedServers The list of servers we want an ack from (they are
   * in normal status and have the same group id as us)
   * @param wrongStatusServers The list of all servers already detected in
   * wrongStatus (degraded status) to keep trace of the error for the future
   * returning ack we gonna compute
   */
  public SafeReadExpectedAcksInfo(CSN csn,
    ServerHandler requesterServerHandler, List<Integer> expectedServers,
    List<Integer> wrongStatusServers)
  {
    super(csn, requesterServerHandler, AssuredMode.SAFE_READ_MODE,
      expectedServers);

    // Keep track of potential servers detected in wrong status
    if (wrongStatusServers.size() > 0)
    {
      hasWrongStatus = true;
      failedServers = wrongStatusServers;
    }
  }

  /**
   * Sets the timeout marker for the future update ack.
   * @param hasTimeout True if some timeout occurred
   */
  public void setHasTimeout(boolean hasTimeout)
  {
    this.hasTimeout = hasTimeout;
  }

  /**
   * Sets the wrong status marker for the future update ack.
   * @param hasWrongStatus True if some servers were in wrong status
   */
  public void setHasWrongStatus(boolean hasWrongStatus)
  {
    this.hasWrongStatus = hasWrongStatus;
  }

  /**
   * Sets the replay error marker for the future update ack.
   * @param hasReplayError True if some servers had errors replaying the change
   */
  public void setHasReplayError(boolean hasReplayError)
  {
    this.hasReplayError = hasReplayError;
  }

  /**
   * Gets the timeout marker for the future update ack.
   * @return The timeout marker for the future update ack.
   */
  public boolean hasTimeout()
  {
    return hasTimeout;
  }

  /**
   * Gets the wrong status marker for the future update ack.
   * @return hasWrongStatus The wrong status marker for the future update ack.
   */
  public boolean hasWrongStatus()
  {
    return hasWrongStatus;
  }

  /**
   * Gets the replay error marker for the future update ack.
   * @return hasReplayError The replay error marker for the future update ack.
   */
  public boolean hasReplayError()
  {
    return hasReplayError;
  }

  /** {@inheritDoc} */
  @Override
  public boolean processReceivedAck(ServerHandler ackingServer, AckMsg ackMsg)
  {
    // Get the ack status for the matching server
    int ackingServerId = ackingServer.getServerId();
    boolean ackReceived = expectedServersAckStatus.get(ackingServerId);
    if (ackReceived)
    {
      // Sanity check: this should never happen
      if (logger.isTraceEnabled())
        logger.trace("Received unexpected ack from server id: "
          + ackingServerId + " ack message: " + ackMsg);
        return false;
    } else
    {
      // Analyze received ack and update info for the ack to be later computed
      // accordingly
      boolean someErrors = false;
      if (ackMsg.hasTimeout())
      {
        hasTimeout = true;
        someErrors = true;
      }
      if (ackMsg.hasWrongStatus())
      {
        hasWrongStatus = true;
        someErrors = true;
      }
      if (ackMsg.hasReplayError())
      {
        hasReplayError = true;
        someErrors = true;
      }
      if (someErrors)
      {
        failedServers.addAll(ackMsg.getFailedServers());
      }

      // Mark this ack received for the server
      expectedServersAckStatus.put(ackingServerId, true);
      numKnownAckStatus++;
    }

    return (numKnownAckStatus == expectedServersAckStatus.size());
  }

  /** {@inheritDoc} */
  @Override
  public AckMsg createAck(boolean timeout)
  {
    AckMsg ack = new AckMsg(csn);

    // Fill collected errors info
    ack.setHasTimeout(hasTimeout);
    ack.setHasWrongStatus(hasWrongStatus);
    ack.setHasReplayError(hasReplayError);

    if (timeout)
    {
      // Force anyway timeout flag if requested
      ack.setHasTimeout(true);

      // Add servers that did not respond in time
      Set<Integer> serverIds = expectedServersAckStatus.keySet();
      serversInTimeout = new ArrayList<>(); // Use next loop to fill it
      for (int serverId : serverIds)
      {
        boolean ackReceived = expectedServersAckStatus.get(serverId);
        if (!ackReceived && !failedServers.contains(serverId))
        {
          failedServers.add(serverId);
          serversInTimeout.add(serverId);
        }
      }
    }

    ack.setFailedServers(failedServers);

    return ack;
  }
}
