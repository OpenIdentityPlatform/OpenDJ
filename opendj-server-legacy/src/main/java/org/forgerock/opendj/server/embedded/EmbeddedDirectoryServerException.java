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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.server.embedded;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.OpenDsException;

/**
 * Exception that may be thrown by an embedded directory server if a problem occurs while
 * performing an operation on the server.
 */
@SuppressWarnings("serial")
public final class EmbeddedDirectoryServerException extends OpenDsException
{

  /**
   * Creates a new exception with the provided message.
   *
   * @param message
   *          The message that explains the problem that occurred.
   */
  public EmbeddedDirectoryServerException(LocalizableMessage message)
  {
    super(message);
  }

  /**
   * Creates a new exception with the provided message and root cause.
   *
   * @param message
   *          The message that explains the problem that occurred.
   * @param cause
   *          The exception that was caught to trigger this exception.
   */
  public EmbeddedDirectoryServerException(LocalizableMessage message, Throwable cause)
  {
    super(message, cause);
  }
}
