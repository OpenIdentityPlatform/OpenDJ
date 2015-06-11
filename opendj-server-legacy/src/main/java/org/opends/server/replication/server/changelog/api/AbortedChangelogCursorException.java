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
 *      Portions Copyright 2015 ForgeRock AS
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
