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
 * The possible events for the status state machine of a DS. See StateMachine
 * class for further details.
 */
public enum StatusMachineEvent
{

  /**
   * Invalid event: special event used to be returned by some methods to signal
   * an error.
   */
  INVALID_EVENT((byte) -1),
  /**
   * Event used when one wants the DS to enter the NOT_CONNECTED_STATUS.
   */
  TO_NOT_CONNECTED_STATUS_EVENT((byte) 0),
  /**
   * Event used when one wants the DS to enter the NORMAL_STATUS.
   */
  TO_NORMAL_STATUS_EVENT((byte) 1),
  /**
   * Event used when one wants the DS to enter the DEGRADED_STATUS.
   */
  TO_DEGRADED_STATUS_EVENT((byte) 2),
  /**
   * Event used when one wants the DS to enter the FULL_UPDATE_STATUS.
   */
  TO_FULL_UPDATE_STATUS_EVENT((byte) 3),
  /**
   * Event used when one wants the DS to enter the BAD_GEN_ID_STATUS.
   */
  TO_BAD_GEN_ID_STATUS_EVENT((byte) 4);
  // The status value
  private byte value = -1;

  private StatusMachineEvent(byte value)
  {
    this.value = value;
  }

  /**
   * Returns the StatusMachineEvent matching the passed event numeric
   * representation.
   * @param value The numeric value for the event to return
   * @return The matching StatusMachineEvent
   * @throws java.lang.IllegalArgumentException If provided event value is
   * wrong
   */
  public static StatusMachineEvent valueOf(byte value)
    throws IllegalArgumentException
  {
    switch (value)
    {
      case 0:
        return TO_NOT_CONNECTED_STATUS_EVENT;
      case 1:
        return TO_NORMAL_STATUS_EVENT;
      case 2:
        return TO_DEGRADED_STATUS_EVENT;
      case 3:
        return TO_FULL_UPDATE_STATUS_EVENT;
      case 4:
        return TO_BAD_GEN_ID_STATUS_EVENT;
      default:
        throw new IllegalArgumentException("Wrong event numeric value: "
          + value);
    }
  }

  /**
   * Returns the event matching the passed requested status.
   * When an entity receives a request to enter a particular status, this
   * order is translated into a state machine event according to what is
   * requested. Then, according to the current status and the computed event,
   * the state machine retruns the matching new status
   * (StateMachine.computeNewStatus).
   * @param reqStatus The status to translate to an event.
   * @return The matching event.
   */
  public static StatusMachineEvent statusToEvent(ServerStatus reqStatus)
  {
   switch (reqStatus)
    {
      case NOT_CONNECTED_STATUS:
        return TO_NOT_CONNECTED_STATUS_EVENT;
      case NORMAL_STATUS:
        return TO_NORMAL_STATUS_EVENT;
      case DEGRADED_STATUS:
        return TO_DEGRADED_STATUS_EVENT;
      case FULL_UPDATE_STATUS:
        return TO_FULL_UPDATE_STATUS_EVENT;
      case BAD_GEN_ID_STATUS:
        return TO_BAD_GEN_ID_STATUS_EVENT;
      default:
        return INVALID_EVENT;
    }
  }

  /**
   * Get a numeric representation of the event.
   * @return The numeric representation of the event
   */
  public byte getValue()
  {
    return value;
  }
}
