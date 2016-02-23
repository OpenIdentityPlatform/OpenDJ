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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.types;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/** This class defines a base exception for OpenDS exceptions. */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public abstract class OpenDsException extends Exception implements LocalizableException
{
  /** Generated serialization ID. */
  private static final long serialVersionUID = 7310881401563732702L;

  /** LocalizableMessage that explains the problem. */
  private final LocalizableMessage message;

  /** Creates a new identified exception. */
  protected OpenDsException()
  {
    this(null, null);
  }

  /**
   * Constructs a new instance from another <code>OpenDsException</code>.
   * This constructor sets the message to be that of <code>cause</code>.
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
   * @param  message  The message that explains the problem that occurred.
   */
  protected OpenDsException(LocalizableMessage message)
  {
    this(message, null);
  }

  /**
   * Creates a new identified exception with the provided information.
   *
   * @param  cause  The underlying cause that triggered this exception.
   */
  protected OpenDsException(Throwable cause)
  {
    this(null, cause);
  }

  /**
   * Creates a new identified exception with the provided information.
   *
   * @param  message  The message that explains the problem that occurred.
   * @param  cause    The underlying cause that triggered this exception.
   */
  protected OpenDsException(LocalizableMessage message, Throwable cause)
  {
    super(message != null ? message.toString() :
            cause != null ? cause.getMessage() : null, cause);
    if (message != null) {
      this.message = message;
    } else if (cause instanceof LocalizableException) {
      this.message = ((LocalizableException) cause).getMessageObject();
    } else {
      this.message = null;
    }
  }

  /**
   * Returns the message that explains the problem that occurred.
   *
   * @return LocalizableMessage of the problem
   */
  @Override
  public LocalizableMessage getMessageObject() {
    return this.message;
  }
}
