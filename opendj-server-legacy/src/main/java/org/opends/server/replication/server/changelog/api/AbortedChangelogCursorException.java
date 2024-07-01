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
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.api;

import javax.annotation.Generated;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This exception is thrown when a cursor that has been aborted is used.
 * <p>
 * A cursor can be aborted when it is open on a log file that
 * must be purged or cleared.
 */
public class AbortedChangelogCursorException extends ChangelogException
{

  @Generated("Eclipse")
  private static final long serialVersionUID = -2123770048083474999L;

  /**
   * Creates a new exception with the provided message.
   *
   * @param message
   *          The message that explains the problem that occurred.
   */
  public AbortedChangelogCursorException(LocalizableMessage message)
  {
    super(message);
  }

  /**
   * Creates a new exception with the provided cause.
   *
   * @param cause
   *          The underlying cause that triggered this exception.
   */
  public AbortedChangelogCursorException(Throwable cause)
  {
    super(cause);
  }

  /**
   * Creates a new exception with the provided message and cause.
   *
   * @param message
   *          The message that explains the problem that occurred.
   * @param cause
   *          The underlying cause that triggered this exception.
   */
  public AbortedChangelogCursorException(LocalizableMessage message, Throwable cause)
  {
    super(message, cause);
  }
}
