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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.CSN;

/**
 * AckMsg messages are used for acknowledging an update that has been sent
 * requesting an ack: update sent in Assured Mode, either safe data or safe
 * read sub mode.
 * The CSN refers to the update CSN that was requested to be acknowledged.
 * If some errors occurred during attempt to acknowledge the update in the path
 * to final servers, errors are marked with the following fields:
 * - hasTimeout:
 * Some servers went in timeout when the matching update was sent.
 * - hasWrongStatus:
 * Some servers were in a status where we cannot ask for an ack when the
 * matching update was sent.
 * - hasReplayError:
 * Some servers made an error replaying the sent matching update.
 * - failedServers:
 * The list of server ids that had errors for the sent matching update. Each
 * server id of the list had one of the 3 possible errors (timeout/wrong status
 * /replay error)
 *
 * AckMsg messages are sent all along the reverse path of the path followed
 * an update message.
 */
public class AckMsg extends ReplicationMsg
{
  /** CSN of the update that was acked. */
  private final CSN csn;

  /**
   * Did some servers go in timeout when the matching update (corresponding to
   * CSN) was sent?.
   */
  private boolean hasTimeout;

  /**
   * Were some servers in wrong status when the matching update (corresponding
   * to CSN) was sent?.
   */
  private boolean hasWrongStatus;

  /**
   * Did some servers make an error replaying the sent matching update
   * (corresponding to CSN)?.
   */
  private boolean hasReplayError;

  /**
   * The list of server ids that had errors for the sent matching update
   * (corresponding to CSN). Each server id of the list had one of the 3
   * possible errors (timeout/degraded or admin/replay error).
   */
  private List<Integer> failedServers = new ArrayList<>();

  /**
   * Creates a new AckMsg from a CSN (no errors).
   *
   * @param csn The CSN used to build the AckMsg.
   */
  public AckMsg(CSN csn)
  {
    this.csn = csn;
  }

  /**
   * Creates a new AckMsg from a CSN (with specified error info).
   *
   * @param csn The CSN used to build the AckMsg.
   * @param hasTimeout The hasTimeout info
   * @param hasWrongStatus The hasWrongStatus info
   * @param hasReplayError The hasReplayError info
   * @param failedServers The list of failed servers
   */
  public AckMsg(CSN csn, boolean hasTimeout, boolean hasWrongStatus,
      boolean hasReplayError, List<Integer> failedServers)
  {
    this.csn = csn;
    this.hasTimeout = hasTimeout;
    this.hasWrongStatus = hasWrongStatus;
    this.hasReplayError = hasReplayError;
    this.failedServers = failedServers;
  }

  /**
   * Sets the timeout marker for this message.
   * @param hasTimeout True if some timeout occurred
   */
  public void setHasTimeout(boolean hasTimeout)
  {
    this.hasTimeout = hasTimeout;
  }

  /**
   * Sets the wrong status marker for this message.
   * @param hasWrongStatus True if some servers were in wrong status
   */
  public void setHasWrongStatus(boolean hasWrongStatus)
  {
    this.hasWrongStatus = hasWrongStatus;
  }

  /**
   * Sets the replay error marker for this message.
   * @param hasReplayError True if some servers had errors replaying the change
   */
  public void setHasReplayError(boolean hasReplayError)
  {
    this.hasReplayError = hasReplayError;
  }

  /**
   * Sets the list of failing servers for this message.
   * @param idList The list of failing servers for this message.
   */
  public void setFailedServers(List<Integer> idList)
  {
    this.failedServers = idList;
  }

  /**
   * Creates a new AckMsg by decoding the provided byte array.
   *
   * @param in The byte array containing the encoded form of the AckMsg.
   * @throws DataFormatException If in does not contain a properly encoded
   *                             AckMsg.
   */
  AckMsg(byte[] in) throws DataFormatException
  {
    /*
     * The message is stored in the form:
     * <operation type><CSN><has timeout><has degraded><has replay
     * error><failed server ids>
     */
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_ACK)
    {
      throw new DataFormatException("byte[] is not a valid modify msg");
    }

    csn = scanner.nextCSNUTF8();
    hasTimeout = scanner.nextBoolean();
    hasWrongStatus = scanner.nextBoolean();
    hasReplayError = scanner.nextBoolean();

    while (!scanner.isEmpty())
    {
      failedServers.add(scanner.nextIntUTF8());
    }
  }

  /**
   * Get the CSN from the message.
   *
   * @return the CSN
   */
  public CSN getCSN()
  {
    return csn;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    /*
     * The message is stored in the form:
     * <operation type><CSN><has timeout><has degraded><has replay
     * error><failed server ids>
     */
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_ACK);
    builder.appendCSNUTF8(csn);
    builder.appendBoolean(hasTimeout);
    builder.appendBoolean(hasWrongStatus);
    builder.appendBoolean(hasReplayError);
    for (int serverId : failedServers)
    {
      builder.appendIntUTF8(serverId);
    }
    return builder.toByteArray();
  }

  /**
   * Tells if the matching update had timeout.
   * @return true if the matching update had timeout
   */
  public boolean hasTimeout()
  {
    return hasTimeout;
  }

  /**
   * Tells if the matching update had wrong status error.
   * @return true if the matching update had wrong status error
   */
  public boolean hasWrongStatus()
  {
    return hasWrongStatus;
  }

  /**
   * Tells if the matching update had replay error.
   * @return true if the matching update had replay error
   */
  public boolean hasReplayError()
  {
    return hasReplayError;
  }

  /**
   * Get the list of failed servers.
   * @return the list of failed servers
   */
  public List<Integer> getFailedServers()
  {
    return failedServers;
  }

  /**
   * Transforms the errors information of the ack into human readable string.
   * @return A human readable string for errors embedded in the message.
   */
  public String errorsToString()
  {
    final String idList =
        !failedServers.isEmpty() ? failedServers.toString() : "none";

    return "hasTimeout: " + (hasTimeout ? "yes" : "no")  + ", " +
      "hasWrongStatus: " + (hasWrongStatus ? "yes" : "no")  + ", " +
      "hasReplayError: " + (hasReplayError ? "yes" : "no")  + ", " +
      "concerned server ids: " + idList;
  }

}

