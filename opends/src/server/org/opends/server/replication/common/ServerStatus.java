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

  // The status value
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
}
