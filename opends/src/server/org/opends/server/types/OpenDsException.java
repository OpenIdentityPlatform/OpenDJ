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

package org.opends.server.types;
import org.opends.messages.Message;


/**
 * This class defines a base exception for OpenDS exceptions.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public abstract class OpenDsException
        extends Exception
{

  /** Message that explains the problem. */
  Message message;

  /**
   * Creates a new identified exception.
   */
  protected OpenDsException()
  {
    super();
  }

  /**
   * Constructs a new instance from another
   * <code>OpenDsException</code>.
   * This constructor sets the message to be that of
   * <code>cause</code>.
   *
   * @param cause exception whose message will be used for
   *        this exception's message.
   */
  protected OpenDsException(OpenDsException cause) {
    this(null, cause);
  }

  /**
   * Creates a new identified exception with the provided information.
   *
   * @param  message  The message that explains the problem that
   *                  occurred.
   */
  protected OpenDsException(Message message)
  {
    this(message, null);
  }



  /**
   * Creates a new identified exception with the provided information.
   *
   * @param  cause  The underlying cause that triggered this
   *                exception.
   */
  protected OpenDsException(Throwable cause)
  {
    this(null, cause);
  }



  /**
   * Creates a new identified exception with the provided information.
   *
   * @param  message  The message that explains the problem that
   *                  occurred.
   * @param  cause    The underlying cause that triggered this
   *                  exception.
   */
  protected OpenDsException(Message message, Throwable cause)
  {
    super(message != null ? message.toString() :
            cause != null ? cause.getMessage() : null, cause);
    if (message != null) {
      this.message = message;
    } else if (cause instanceof OpenDsException) {
      this.message = ((OpenDsException)cause).getMessageObject();
    }
  }



  /**
   * Returns the message that explains the problem that occurred.
   *
   * @return Message of the problem
   */
  public Message getMessageObject() {
    return this.message;
  }
}
