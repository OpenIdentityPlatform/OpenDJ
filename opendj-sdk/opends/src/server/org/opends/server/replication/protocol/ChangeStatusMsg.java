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
 *      Copyright 2008 Sun Microsystems, Inc.
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
  // The status we want the DS to enter (used when from RS to DS)
  private ServerStatus requestedStatus = ServerStatus.INVALID_STATUS;
  // The new status the DS just entered (used when from DS to RS)
  private ServerStatus newStatus = ServerStatus.INVALID_STATUS;

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
  public ChangeStatusMsg(byte[] encodedMsg) throws DataFormatException
  {
    /*
     * The message is stored in the form:
     * <message type><requested status><new status>
     */

    /* First byte is the type */
    if (encodedMsg[0] != ReplicationMsg.MSG_TYPE_CHANGE_STATUS)
    {
      throw new DataFormatException("byte[] is not a valid msg");
    }

    try
    {
      /* Then the requested status */
      requestedStatus = ServerStatus.valueOf(encodedMsg[1]);

      /* Then the new status */
      newStatus = ServerStatus.valueOf(encodedMsg[2]);
    } catch (IllegalArgumentException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    /*
     * The message is stored in the form:
     * <message type><requested status><new status>
     */
    byte[] encodedMsg = new byte[3];

    /* Put the type of the operation */
    encodedMsg[0] = ReplicationMsg.MSG_TYPE_CHANGE_STATUS;

    /* Put the requested status */
    encodedMsg[1] = requestedStatus.getValue();

    /* Put the requested status */
    encodedMsg[2] = newStatus.getValue();

    return encodedMsg;
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

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "ChangeStatusMsg content:" +
      "\nnewStatus: " + newStatus +
      "\nrequestedStatus: " + requestedStatus;
  }
}