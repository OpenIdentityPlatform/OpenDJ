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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;
import org.opends.messages.Message;



import static org.opends.messages.CoreMessages.*;



/**
 * This enumeration defines the set of possible reasons for the
 * closure of a connection between a client and the Directory Server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum DisconnectReason
{
  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the client unbind from the server.
   */
  UNBIND(
          INFO_DISCONNECT_DUE_TO_UNBIND.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the client disconnected without unbinding.
   */
  CLIENT_DISCONNECT(
          INFO_DISCONNECT_DUE_TO_CLIENT_CLOSURE.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the client connection was rejected.
   */
  CONNECTION_REJECTED(
          INFO_DISCONNECT_DUE_TO_REJECTED_CLIENT.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of an I/O error.
   */
  IO_ERROR(
          INFO_DISCONNECT_DUE_TO_IO_ERROR.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of a protocol error.
   */
  PROTOCOL_ERROR(
          INFO_DISCONNECT_DUE_TO_PROTOCOL_ERROR.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the Directory Server shut down.
   */
  SERVER_SHUTDOWN(
          INFO_DISCONNECT_DUE_TO_SERVER_SHUTDOWN.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because an administrator terminated the connection.
   */
  ADMIN_DISCONNECT(
          INFO_DISCONNECT_BY_ADMINISTRATOR.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of a security problem.
   */
  SECURITY_PROBLEM(
          INFO_DISCONNECT_DUE_TO_SECURITY_PROBLEM.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the maximum allowed request size was exceeded.
   */
  MAX_REQUEST_SIZE_EXCEEDED(
          INFO_DISCONNECT_DUE_TO_MAX_REQUEST_SIZE.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because an administrative limit was exceeded.
   */
  ADMIN_LIMIT_EXCEEDED(
          INFO_DISCONNECT_DUE_TO_ADMIN_LIMIT.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the idle time limit was exceeded.
   */
  IDLE_TIME_LIMIT_EXCEEDED(
          INFO_DISCONNECT_DUE_TO_IDLE_TIME_LIMIT.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of an I/O timeout.
   */
  IO_TIMEOUT(
          INFO_DISCONNECT_DUE_TO_IO_TIMEOUT.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of an internal error within the server.
   */
  SERVER_ERROR(
          INFO_DISCONNECT_DUE_TO_SERVER_ERROR.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed by a plugin.
   */
  CLOSED_BY_PLUGIN(
          INFO_DISCONNECT_BY_PLUGIN.get()),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed for some other reason.
   */
  OTHER(
          INFO_DISCONNECT_OTHER.get());



  // The disconnect reason.
  private Message message;


  /**
   * Creates a new disconnect reason element with the provided closure
   * message.
   *
   * @param  message  The message for this disconnect reason.
   */
  private DisconnectReason(Message message)
  {
    this.message = message;
  }



  /**
   * Retrieves the human-readable disconnect reason.
   *
   * @return  The human-readable disconnect reason.
   */
  public Message getClosureMessage()
  {
    return message;
  }



  /**
   * Retrieves a string representation of this disconnect reason.
   *
   * @return  A string representation of this disconnect reason.
   */
  public String toString()
  {
    return message.toString();
  }
}

