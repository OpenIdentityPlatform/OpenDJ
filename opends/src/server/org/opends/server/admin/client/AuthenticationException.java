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

package org.opends.server.admin.client;



import static org.opends.messages.AdminMessages.*;

import org.opends.messages.Message;



/**
 * This exception is thrown when an authentication error occurs while
 * connecting to the Directory Server. An authentication error can
 * happen, for example, when the client credentials are invalid.
 */
public class AuthenticationException extends AdminSecurityException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 3544797197747686958L;



  /**
   * Creates an authentication exception with a default message.
   */
  public AuthenticationException() {
    super(ERR_AUTHENTICATION_EXCEPTION_DEFAULT.get());
  }



  /**
   * Create an authentication exception with a cause and a default
   * message.
   *
   * @param cause
   *          The cause.
   */
  public AuthenticationException(Throwable cause) {
    super(ERR_AUTHENTICATION_EXCEPTION_DEFAULT.get(), cause);
  }



  /**
   * Create an authentication exception with a message and cause.
   *
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  public AuthenticationException(Message message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create an authentication exception with a message.
   *
   * @param message
   *          The message.
   */
  public AuthenticationException(Message message) {
    super(message);
  }
}
