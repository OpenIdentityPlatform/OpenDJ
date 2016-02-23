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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */

package org.opends.server.types;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This class defines an exception that is thrown in the case of
 * problems with encryption key management, and is a wrapper for a
 * variety of other cipher related exceptions.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public class CryptoManagerException extends OpenDsException {
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class extends <CODE>java.lang.Exception</CODE>,
   * which implements the <CODE>java.io.Serializable</CODE>
   * interface. This value was generated using the
   * <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  static final long serialVersionUID = -5890763923778143774L;

  /**
   * Creates an exception with the given message.
   * @param message the message message.
   */
  public CryptoManagerException(LocalizableMessage message) {
    super(message);
   }

  /**
   * Creates an exception with the given message and underlying
   * cause.
   * @param message The message message.
   * @param cause  The underlying cause.
   */
  public CryptoManagerException(LocalizableMessage message, Exception cause) {
    super(message, cause);
  }
}
