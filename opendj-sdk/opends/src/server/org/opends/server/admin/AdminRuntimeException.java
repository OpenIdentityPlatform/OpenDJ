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

package org.opends.server.admin;



import org.opends.messages.Message;



/**
 * Exceptions thrown when interacting with administration framework
 * that applications are not expected to catch.
 */
public abstract class AdminRuntimeException extends RuntimeException {

  // Message that explains the problem.
  private final Message message;



  /**
   * Create an admin runtime exception with a message and cause.
   *
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  protected AdminRuntimeException(Message message, Throwable cause) {
    super(message.toString(), cause);
    this.message = message;
  }



  /**
   * Create an admin runtime exception with a message.
   *
   * @param message
   *          The message.
   */
  protected AdminRuntimeException(Message message) {
    super(message.toString());
    this.message = message;
  }



  /**
   * Returns the message that explains the problem that occurred.
   *
   * @return Returns the message describing the problem that occurred
   *         (never <code>null</code>).
   */
  public Message getMessageObject() {
    return this.message;
  }
}
