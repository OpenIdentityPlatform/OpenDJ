/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.replication.common;

/**
 * The various status a DS can take.
 */
public enum ServerStatus
{

  /**
   * Invalid status: special status used in state machine implementation to
   * return an error (impossible status). See class StatusMachine class for
   * further details.
   */
  INVALID_STATUS((byte) -1),
  /**
   * Not connected status: special status used as initial status of the state
   * machine in the DS context only as already connected while state machine
   * considered in RS context.
   */
  NOT_CONNECTED_STATUS((byte) 0),
  /**
   * DS in normal status.
   * When:
   * - everything is fine
   * Properties:
   * - no referrals
   * - updates received from RS
   * - if assured mode, RS asks for ack
   */
  NORMAL_STATUS((byte) 1),
  /**
   * DS in degraded status.
   * When:
   * - DS is too late compared to number of updates RS has to send
   * Properties:
   * - referrals returned
   * - updates received from RS
   * - if assured mode, RS does not asks for ack
   */
  DEGRADED_STATUS((byte) 2),
  /**
   * DS in full update (local DS is initialized from another DS).
   * (if local DS initializes another, it is not in this status)
   * When:
   * - A full update is being performed to our local DS
   * Properties:
   * - referrals returned
   * - no updates received from RS
   * - no ack requested as no updates received
   */
  FULL_UPDATE_STATUS((byte) 3),
  /**
   * DS in bad generation id status.
   * When:
   * - A reset generation id order has been sent to topology
   * Properties:
   * - no referrals returned
   * - no updates received from RS
   * - no ack requested as no updates received
   */
  BAD_GEN_ID_STATUS((byte) 4);

  /** The status value. */
  private byte value = -1;

  private ServerStatus(byte value)
  {
    this.value = value;
  }

  /**
   * Returns the ServerStatus matching the passed status numeric representation.
   * @param value The numeric value for the status to return
   * @return The matching ServerStatus
   * @throws java.lang.IllegalArgumentException If provided status value is
   * wrong
   */
  public static ServerStatus valueOf(byte value) throws IllegalArgumentException
  {
    switch (value)
    {
      case -1:
        return INVALID_STATUS;
      case 0:
        return NOT_CONNECTED_STATUS;
      case 1:
        return NORMAL_STATUS;
      case 2:
        return DEGRADED_STATUS;
      case 3:
        return FULL_UPDATE_STATUS;
      case 4:
        return BAD_GEN_ID_STATUS;
      default:
        throw new IllegalArgumentException("Wrong status numeric value: " +
          value);
    }
  }

  /**
   * Get a numeric representation of the status.
   * @return The numeric representation of the status
   */
  public byte getValue()
  {
    return value;
  }

  /**
   * Get a user readable string representing this status (User friendly string
   * for monitoring purpose).
   * @return A user readable string representing this status.
   */
  @Override
  public String toString()
  {
    switch (value)
    {
      case -1:
        return "Invalid";
      case 0:
        return "Not connected";
      case 1:
        return "Normal";
      case 2:
        return "Degraded";
      case 3:
        return "Full update";
      case 4:
        return "Bad generation id";
      default:
        throw new IllegalArgumentException("Wrong status numeric value: " +
          value);
    }
  }
}
