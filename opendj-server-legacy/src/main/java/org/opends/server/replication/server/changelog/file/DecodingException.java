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
 * Copyright 2014 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.replication.server.changelog.api.ChangelogException;

/**
 * Exception thrown when a record can't be decoded properly.
 */
public class DecodingException extends ChangelogException
{
  private static final long serialVersionUID = 5629692522662643737L;

  /**
   * Creates a new decoding exception with the provided information.
   *
   * @param message
   *          The message that explains the problem that occurred.
   */
  public DecodingException(LocalizableMessage message)
  {
    super(message);
  }

  /**
   * Creates a new decoding exception with the provided information.
   *
   * @param cause
   *          The underlying cause that triggered this exception.
   */
  public DecodingException(Throwable cause)
  {
    super(cause);
  }

  /**
   * Creates a new decoding exception with the provided information.
   *
   * @param message
   *          The message that explains the problem that occurred.
   * @param cause
   *          The underlying cause that triggered this exception.
   */
  public DecodingException(LocalizableMessage message, Throwable cause)
  {
    super(message, cause);
  }
}
