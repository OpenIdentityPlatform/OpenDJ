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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.sdk.tools;




import com.sun.opends.sdk.messages.Messages;
import com.sun.opends.sdk.util.LocalizableException;
import com.sun.opends.sdk.util.Message;



/**
 * Thrown to indicate that a problem occurred when interacting with the
 * client. For example, if input provided by the client was invalid.
 */
@SuppressWarnings("serial")
final class CLIException extends Exception implements
    LocalizableException
{

  /**
   * Adapts any exception that may have occurred whilst reading input
   * from the console.
   *
   * @param cause
   *          The exception that occurred whilst reading input from the
   *          console.
   * @return Returns a new CLI exception describing a problem that
   *         occurred whilst reading input from the console.
   */
  static CLIException adaptInputException(Throwable cause)
  {
    return new CLIException(Messages.ERR_CONSOLE_INPUT_ERROR.get(cause
        .getMessage()), cause);
  }



  /**
   * Creates a new CLI exception with the provided message.
   *
   * @param message
   *          The message explaining the problem that occurred.
   */
  CLIException(Message message)
  {
    super(message.toString());
    this.message = message;
  }



  /**
   * Creates a new CLI exception with the provided message and cause.
   *
   * @param message
   *          The message explaining the problem that occurred.
   * @param cause
   *          The cause of this exception.
   */
  CLIException(Message message, Throwable cause)
  {
    super(message.toString(), cause);
    this.message = message;
  }



  private final Message message;



  public Message getMessageObject()
  {
    return message;
  }

}
