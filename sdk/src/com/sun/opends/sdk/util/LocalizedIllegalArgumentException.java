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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.util;






/**
 * Thrown to indicate that a method has been passed an illegal or
 * inappropriate argument.
 * <p>
 * A {@code LocalizedIllegalArgumentException} contains a localized
 * error message which maybe used to provide the user with detailed
 * diagnosis information. The localized message can be retrieved using
 * the {@link #getMessageObject} method.
 * <p>
 * A {@code LocalizedIllegalArgumentException} is typically used to
 * indicate problems parsing values such as distinguished names and
 * filters.
 */
@SuppressWarnings("serial")
public class LocalizedIllegalArgumentException extends
    IllegalArgumentException implements LocalizableException
{
  // The I18N message associated with this exception.
  private final Message message;



  /**
   * Creates a new localized illegal argument exception with the
   * provided message.
   *
   * @param message
   *          The message that explains the problem that occurred.
   */
  public LocalizedIllegalArgumentException(Message message)
  {
    super(String.valueOf(message));
    this.message = message;
  }



  /**
   * Creates a new localized illegal argument exception with the
   * provided message and cause.
   *
   * @param message
   *          The message that explains the problem that occurred.
   * @param cause
   *          The cause which may be later retrieved by the
   *          {@link #getCause} method. A {@code null} value is
   *          permitted, and indicates that the cause is nonexistent or
   *          unknown.
   */
  public LocalizedIllegalArgumentException(Message message,
      Throwable cause)
  {
    super(String.valueOf(message), cause);
    this.message = message;
  }



  /**
   * {@inheritDoc}
   */
  public Message getMessageObject()
  {
    return this.message;
  }
}
