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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.util.cli;



import org.opends.messages.Message;
import org.opends.messages.UtilityMessages;
import org.opends.server.types.IdentifiedException;



/**
 * Thrown to indicate that a problem occurred when interacting with
 * the client. For example, if input provided by the client was
 * invalid.
 */
public class CLIException extends IdentifiedException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -8182075627986981748L;



  /**
   * Adapts any exception that may have occurred whilst reading input
   * from the console.
   *
   * @param cause
   *          The exception that occurred whilst reading input from
   *          the console.
   * @return Returns a new CLI exception describing a problem that
   *         occurred whilst reading input from the console.
   */
  public static CLIException adaptInputException(Throwable cause) {
    return new CLIException(UtilityMessages.ERR_CONSOLE_INPUT_ERROR.get(cause
        .getMessage()), cause);
  }



  /**
   * Creates a new CLI exception with the provided message.
   *
   * @param message
   *          The message explaining the problem that occurred.
   */
  public CLIException(Message message) {
    super(message);
  }



  /**
   * Creates a new CLI exception with the provided message and cause.
   *
   * @param message
   *          The message explaining the problem that occurred.
   * @param cause
   *          The cause of this exception.
   */
  public CLIException(Message message, Throwable cause) {
    super(message, cause);
  }

}
