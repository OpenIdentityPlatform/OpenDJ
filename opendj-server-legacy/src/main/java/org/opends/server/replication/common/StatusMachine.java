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
 */
package org.opends.server.replication.common;

/**
 * This class contains static methods to implement the DS status state machine.
 * They are used to validate the transitions of the state machine according to
 * the current status and event, and to compute the new status.
 * - Status (states of the state machine) are defined in ServerStatus enum
 * - Events are defined in StateMachineEvent enum
 */
public class StatusMachine
{

  /**
   * Checks if a given status is valid as an entering status for the state
   * machine.
   * @param initStatus Initial status to check.
   * @return True if the passed status is a valid initial status.
   */
  public static boolean isValidInitialStatus(ServerStatus initStatus)
  {
    switch (initStatus)
    {
      case NORMAL_STATUS:
      case DEGRADED_STATUS:
      case BAD_GEN_ID_STATUS:
        return true;
    }

    return false;
  }

  /**
   * Computes the new status of the state machine according to the current
   * status and the new generated event.
   * @param curStatus The current status we start from.
   * @param event The event that must make the current status evolve.
   * @return The newly computed status. If the state transition is impossible
   * according to state machine, special INVALID_STATUS is returned.
   */
  public static ServerStatus computeNewStatus(ServerStatus curStatus,
    StatusMachineEvent event)
  {
    switch (curStatus)
    {
      // From NOT_CONNECTED_STATUS
      case NOT_CONNECTED_STATUS:
        switch (event)
        {
          case TO_NOT_CONNECTED_STATUS_EVENT:
            return ServerStatus.NOT_CONNECTED_STATUS;
          case TO_NORMAL_STATUS_EVENT:
            return ServerStatus.NORMAL_STATUS;
          case TO_DEGRADED_STATUS_EVENT:
            return ServerStatus.DEGRADED_STATUS;
          case TO_BAD_GEN_ID_STATUS_EVENT:
            return ServerStatus.BAD_GEN_ID_STATUS;
          default:
            return ServerStatus.INVALID_STATUS;
        }
      // From NORMAL_STATUS
      case NORMAL_STATUS:
        switch (event)
        {
          case TO_NOT_CONNECTED_STATUS_EVENT:
            return ServerStatus.NOT_CONNECTED_STATUS;
          case TO_NORMAL_STATUS_EVENT:
            return ServerStatus.NORMAL_STATUS;
          case TO_DEGRADED_STATUS_EVENT:
            return ServerStatus.DEGRADED_STATUS;
          case TO_FULL_UPDATE_STATUS_EVENT:
            return ServerStatus.FULL_UPDATE_STATUS;
          case TO_BAD_GEN_ID_STATUS_EVENT:
            return ServerStatus.BAD_GEN_ID_STATUS;
          default:
            return ServerStatus.INVALID_STATUS;
        }
      // From DEGRADED_STATUS
      case DEGRADED_STATUS:
        switch (event)
        {
          case TO_NOT_CONNECTED_STATUS_EVENT:
            return ServerStatus.NOT_CONNECTED_STATUS;
          case TO_NORMAL_STATUS_EVENT:
            return ServerStatus.NORMAL_STATUS;
          case TO_DEGRADED_STATUS_EVENT:
            return ServerStatus.DEGRADED_STATUS;
          case TO_FULL_UPDATE_STATUS_EVENT:
            return ServerStatus.FULL_UPDATE_STATUS;
          case TO_BAD_GEN_ID_STATUS_EVENT:
            return ServerStatus.BAD_GEN_ID_STATUS;
          default:
            return ServerStatus.INVALID_STATUS;
        }
      // From FULL_UPDATE_STATUS
      case FULL_UPDATE_STATUS:
        switch (event)
        {
          case TO_NOT_CONNECTED_STATUS_EVENT:
            return ServerStatus.NOT_CONNECTED_STATUS;
          case TO_FULL_UPDATE_STATUS_EVENT:
            return ServerStatus.FULL_UPDATE_STATUS;
          default:
            return ServerStatus.INVALID_STATUS;
        }
      // From BAD_GEN_ID_STATUS
      case BAD_GEN_ID_STATUS:
        switch (event)
        {
          case TO_NOT_CONNECTED_STATUS_EVENT:
            return ServerStatus.NOT_CONNECTED_STATUS;
          case TO_FULL_UPDATE_STATUS_EVENT:
            return ServerStatus.FULL_UPDATE_STATUS;
          case TO_BAD_GEN_ID_STATUS_EVENT:
            return ServerStatus.BAD_GEN_ID_STATUS;
          default:
            return ServerStatus.INVALID_STATUS;
        }
      default:
        return ServerStatus.INVALID_STATUS;
    }
  }
}
