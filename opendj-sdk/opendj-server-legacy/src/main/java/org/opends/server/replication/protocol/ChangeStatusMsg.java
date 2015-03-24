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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

import org.opends.server.replication.common.ServerStatus;

/**
 * This message is used by the DS to tell the RS he is changing his status
 * (new status field used), or by the RS to tell the DS he must change his
 * status (requested status field used).
 */
public class ChangeStatusMsg extends ReplicationMsg
{
  /** The status we want the DS to enter (used when from RS to DS). */
  private final ServerStatus requestedStatus;
  /** The new status the DS just entered (used when from DS to RS). */
  private ServerStatus newStatus;

  /**
   * Create a new ChangeStatusMsg.
   *
   * @param requestedStatus The requested status
   * @param newStatus The new status
   */
  public ChangeStatusMsg(ServerStatus requestedStatus, ServerStatus newStatus)
  {
    this.requestedStatus = requestedStatus;
    this.newStatus = newStatus;
  }

  /**
   * Creates a new ChangeStatusMsg from its encoded form.
   *
   * @param encodedMsg The byte array containing the encoded form of the
   *           ChangeStatusMsg.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the ChangeStatusMsg.
   */
  ChangeStatusMsg(byte[] encodedMsg) throws DataFormatException
  {
    /*
     * The message is stored in the form:
     * <message type><requested status><new status>
     */
    try
    {
      if (encodedMsg[0] != ReplicationMsg.MSG_TYPE_CHANGE_STATUS)
      {
        throw new DataFormatException("byte[] is not a valid msg");
      }
      requestedStatus = ServerStatus.valueOf(encodedMsg[1]);
      newStatus = ServerStatus.valueOf(encodedMsg[2]);
    } catch (IllegalArgumentException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    /*
     * The message is stored in the form:
     * <message type><requested status><new status>
     */
    return new byte[]
    {
      ReplicationMsg.MSG_TYPE_CHANGE_STATUS,
      requestedStatus.getValue(),
      newStatus.getValue()
    };
  }

  /**
   * Get the requested status.
   * @return The requested status
   */
  public ServerStatus getRequestedStatus()
  {
    return requestedStatus;
  }

  /**
   * Get the requested status.
   * @return The new status
   */
  public ServerStatus getNewStatus()
  {
    return newStatus;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "ChangeStatusMsg content:" +
      "\nnewStatus: " + newStatus +
      "\nrequestedStatus: " + requestedStatus;
  }
}
