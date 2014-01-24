/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.opends.server.admin.client;



import static org.opends.messages.AdminMessages.*;

import org.forgerock.i18n.LocalizableMessage;



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
  public AuthenticationException(LocalizableMessage message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create an authentication exception with a message.
   *
   * @param message
   *          The message.
   */
  public AuthenticationException(LocalizableMessage message) {
    super(message);
  }
}
