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
import org.opends.messages.Message;



import org.opends.server.admin.OperationsException;



/**
 * This exception is thrown when a critical concurrent modification is
 * detected by the client. This may be caused by another client
 * application removing a managed object whilst it is being managed.
 */
public class ConcurrentModificationException extends OperationsException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -1467024486347612820L;



  /**
   * Create a concurrent modification exception.
   */
  public ConcurrentModificationException() {
    // No implementation required.
  }



  /**
   * Create a concurrent modification exception with a cause.
   *
   * @param cause
   *          The cause.
   */
  public ConcurrentModificationException(Throwable cause) {
    super(cause);
  }



  /**
   * Create a concurrent modification exception with a message and
   * cause.
   *
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  public ConcurrentModificationException(Message message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create a concurrent modification exception with a message.
   *
   * @param message
   *          The message.
   */
  public ConcurrentModificationException(Message message) {
    super(message);
  }
}
