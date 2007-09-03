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
 * This exception is thrown when the particular flavor of
 * authentication requested is not supported by the Directory Server.
 */
public class AuthenticationNotSupportedException
    extends AdminSecurityException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 7387834052676291793L;



  /**
   * Creates an authentication not supported exception with a default
   * message.
   */
  public AuthenticationNotSupportedException() {
    super(ERR_AUTHENTICATION_NOT_SUPPORTED_EXCEPTION_DEFAULT.get());
  }



  /**
   * Creates an authentication not supported exception with a cause
   * and a default message.
   *
   * @param cause
   *          The cause.
   */
  public AuthenticationNotSupportedException(Throwable cause) {
    super(ERR_AUTHENTICATION_NOT_SUPPORTED_EXCEPTION_DEFAULT.get(), cause);
  }



  /**
   * Create an authentication not supported exception with a message
   * and cause.
   *
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  public AuthenticationNotSupportedException(Message message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create an authentication not supported exception with a message.
   *
   * @param message
   *          The message.
   */
  public AuthenticationNotSupportedException(Message message) {
    super(message);
  }
}
