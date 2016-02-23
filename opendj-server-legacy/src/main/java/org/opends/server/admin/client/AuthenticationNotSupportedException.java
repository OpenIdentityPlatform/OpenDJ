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
 * Portions Copyright 2014 ForgeRock AS.
 */

package org.opends.server.admin.client;



import static org.opends.messages.AdminMessages.*;

import org.forgerock.i18n.LocalizableMessage;



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
  public AuthenticationNotSupportedException(LocalizableMessage message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create an authentication not supported exception with a message.
   *
   * @param message
   *          The message.
   */
  public AuthenticationNotSupportedException(LocalizableMessage message) {
    super(message);
  }
}
