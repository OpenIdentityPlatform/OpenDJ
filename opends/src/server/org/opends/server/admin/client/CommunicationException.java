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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client;



import static org.opends.messages.AdminMessages.*;

import org.opends.messages.Message;



/**
 * This exception is thrown when a communications related problem
 * occurs whilst interacting with the Directory Server. This may be
 * caused by problems such as network partitioning, the unavailability
 * of the Directory Server, or other failures on the client or server
 * side.
 */
public class CommunicationException extends AdminClientException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 9093195928501281027L;



  /**
   * Create a communication exception with a default message.
   */
  public CommunicationException() {
    super(ERR_COMMUNICATION_EXCEPTION_DEFAULT.get());
  }



  /**
   * Create a communication exception with a cause and a default
   * message.
   *
   * @param cause
   *          The cause.
   */
  public CommunicationException(Throwable cause) {
    super(ERR_COMMUNICATION_EXCEPTION_DEFAULT_CAUSE.get(cause.getMessage()),
        cause);
  }



  /**
   * Create a communication exception with a message and cause.
   *
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  public CommunicationException(Message message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create a communication exception with a message.
   *
   * @param message
   *          The message.
   */
  public CommunicationException(Message message) {
    super(message);
  }
}
