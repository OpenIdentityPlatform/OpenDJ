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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This enumeration defines the set of possible reasons for the
 * closure of a connection between a client and the Directory Server.
 */
public enum DisconnectReason
{
  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the client unbind from the server.
   */
  UNBIND(MSGID_DISCONNECT_DUE_TO_UNBIND),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the client disconnected without unbinding.
   */
  CLIENT_DISCONNECT(MSGID_DISCONNECT_DUE_TO_CLIENT_CLOSURE),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the client connection was rejected.
   */
  CONNECTION_REJECTED(MSGID_DISCONNECT_DUE_TO_REJECTED_CLIENT),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of an I/O error.
   */
  IO_ERROR(MSGID_DISCONNECT_DUE_TO_IO_ERROR),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of a protocol error.
   */
  PROTOCOL_ERROR(MSGID_DISCONNECT_DUE_TO_PROTOCOL_ERROR),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the Directory Server shut down.
   */
  SERVER_SHUTDOWN(MSGID_DISCONNECT_DUE_TO_SERVER_SHUTDOWN),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because an administrator terminated the connection.
   */
  ADMIN_DISCONNECT(MSGID_DISCONNECT_BY_ADMINISTRATOR),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of a security problem.
   */
  SECURITY_PROBLEM(MSGID_DISCONNECT_DUE_TO_SECURITY_PROBLEM),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the maximum allowed request size was exceeded.
   */
  MAX_REQUEST_SIZE_EXCEEDED(MSGID_DISCONNECT_DUE_TO_MAX_REQUEST_SIZE),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because an administrative limit was exceeded.
   */
  ADMIN_LIMIT_EXCEEDED(MSGID_DISCONNECT_DUE_TO_ADMIN_LIMIT),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because the idle time limit was exceeded.
   */
  IDLE_TIME_LIMIT_EXCEEDED(MSGID_DISCONNECT_DUE_TO_IDLE_TIME_LIMIT),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of an I/O timeout.
   */
  IO_TIMEOUT(MSGID_DISCONNECT_DUE_TO_IO_TIMEOUT),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed because of an internal error within the server.
   */
  SERVER_ERROR(MSGID_DISCONNECT_DUE_TO_SERVER_ERROR),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed by a plugin.
   */
  CLOSED_BY_PLUGIN(MSGID_DISCONNECT_BY_PLUGIN),



  /**
   * The disconnect reason that indicates that the client connection
   * was closed for some other reason.
   */
  OTHER(MSGID_DISCONNECT_OTHER);



  // The unique ID for this disconnect reason.
  private int closureID;

  // The short human-readable disconnect reason.
  private String closureString;



  /**
   * Creates a new disconnect reason element with the provided closure
   * ID.
   *
   * @param  closureID  The unique ID for this disconnect reason.
   */
  private DisconnectReason(int closureID)
  {
    this.closureID     = closureID;
    this.closureString = null;
  }



  /**
   * Retrieves the unique ID for this disconnect reason.
   *
   * @return  The unique ID for this disconnect reason.
   */
  public int getClosureID()
  {
    return closureID;
  }



  /**
   * Retrieves the human-readable disconnect reason.
   *
   * @return  The human-readable disconnect reason.
   */
  public String getClosureString()
  {
    if (closureString == null)
    {
      closureString = getMessage(closureID);
    }

    return closureString;
  }



  /**
   * Retrieves a string representation of this disconnect reason.
   *
   * @return  A string representation of this disconnect reason.
   */
  public String toString()
  {
    return getClosureString();
  }
}

