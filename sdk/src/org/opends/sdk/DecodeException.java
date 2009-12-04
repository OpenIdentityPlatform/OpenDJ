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

package org.opends.sdk;



import java.io.IOException;

import com.sun.opends.sdk.util.LocalizableException;
import com.sun.opends.sdk.util.Message;



/**
 * Thrown when data from an input source cannot be decoded, perhaps due
 * to the data being malformed in some way. By default decoding
 * exceptions are fatal, indicating that the associated input source is
 * no longer usable.
 */
@SuppressWarnings("serial")
public final class DecodeException extends IOException implements
    LocalizableException
{
  private final Message message;

  private final boolean isFatal;



  /**
   * Creates a new fatal decode exception with the provided message. The
   * associated input source can no longer be used.
   *
   * @param message
   *          The message that explains the problem that occurred.
   * @return The new fatal decode exception.
   */
  public static DecodeException fatalError(Message message)
  {
    return new DecodeException(message, true, null);
  }



  /**
   * Creates a new fatal decode exception with the provided message and
   * root cause. The associated input source can no longer be used.
   *
   * @param message
   *          The message that explains the problem that occurred.
   * @param cause
   *          The underlying cause of this exception.
   * @return The new fatal decode exception.
   */
  public static DecodeException fatalError(Message message,
      Throwable cause)
  {
    return new DecodeException(message, true, cause);
  }



  /**
   * Creates a new non-fatal decode exception with the provided message.
   *
   * @param message
   *          The message that explains the problem that occurred.
   * @return The new non-fatal decode exception.
   */
  public static DecodeException error(Message message)
  {
    return new DecodeException(message, false, null);
  }



  /**
   * Creates a new non-fatal decode exception with the provided message
   * and root cause.
   *
   * @param message
   *          The message that explains the problem that occurred.
   * @param cause
   *          The underlying cause of this exception.
   * @return The new non-fatal decode exception.
   */
  public static DecodeException error(Message message, Throwable cause)
  {
    return new DecodeException(message, false, cause);
  }



  // Construction is provided via factory methods.
  private DecodeException(Message message, boolean isFatal,
      Throwable cause)
  {
    super(message.toString(), cause);
    this.message = message;
    this.isFatal = isFatal;
  }



  /**
   * Returns the message that explains the problem that occurred.
   *
   * @return Message of the problem
   */
  public Message getMessageObject()
  {
    return message;
  }



  /**
   * Indicates whether or not the error was fatal and the associated
   * input source can no longer be used.
   *
   * @return {@code true} if the error was fatal and the associated
   *         input source can no longer be used, otherwise {@code false}
   *         .
   */
  public boolean isFatal()
  {
    return isFatal;
  }
}
