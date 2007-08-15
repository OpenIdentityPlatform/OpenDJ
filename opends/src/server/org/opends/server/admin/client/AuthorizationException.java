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




/**
 * This exception is thrown when an authorization error occurs while
 * interacting with the Directory Server. Authorization errors can
 * occur when a client attempts to perform an administrative operation
 * which they are not permitted to perform.
 */
public class AuthorizationException extends AdminSecurityException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 8414248362572933814L;



  /**
   * Create an authorization exception.
   */
  public AuthorizationException() {
    // No implementation required.
  }



  /**
   * Create an authorization exception with a cause.
   *
   * @param cause
   *          The cause.
   */
  public AuthorizationException(Throwable cause) {
    super(cause);
  }



  /**
   * Create an authorization exception with a message and cause.
   *
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  public AuthorizationException(Message message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create an authorization exception with a message.
   *
   * @param message
   *          The message.
   */
  public AuthorizationException(Message message) {
    super(message);
  }
}
