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
package org.opends.server.protocols.http;

import org.forgerock.http.HttpApplicationException;
import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * Thrown to indicate that an {@link HttpApplication} was unable to start. A {@code LocalizedHttpApplicationException}
 * contains a localized error message which may be used to provide the user with detailed diagnosis information. The
 * localized message can be retrieved using the {@link #getMessageObject} method.
 */
public class LocalizedHttpApplicationException extends HttpApplicationException implements LocalizableException
{
  private static final long serialVersionUID = 2150656895248806504L;

  private final LocalizableMessage message;

  /**
   * Creates a new localized http application exception with the provided message.
   *
   * @param message
   *          The message that explains the problem that occurred.
   */
  public LocalizedHttpApplicationException(LocalizableMessage message)
  {
    super(message.toString());
    this.message = message;
  }

  /**
   * Creates a new localized http application exception with the provided message and cause.
   *
   * @param message
   *          The message that explains the problem that occurred.
   * @param cause
   *          The cause which may be later retrieved by the {@link #getCause} method. A {@code null} value is permitted,
   *          and indicates that the cause is nonexistent or unknown.
   */
  public LocalizedHttpApplicationException(LocalizableMessage message, Throwable cause)
  {
    super(message.toString(), cause);
    this.message = message;
  }

  @Override
  public final LocalizableMessage getMessageObject()
  {
    return message;
  }
}
