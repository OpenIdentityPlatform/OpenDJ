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
 * Portions Copyright 2014 ForgeRock AS.
 */
package org.opends.server.types;

import org.forgerock.i18n.LocalizableMessage;



/**
 * This class defines a base exception that should be extended by any
 * exception that exposes a unique identifier for the associated
 * message.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public abstract class IdentifiedException
       extends OpenDsException
{
  /**
   * Generated serialization ID.
   */
  private static final long serialVersionUID = 7071843225564003122L;



  /**
   * Creates a new identified exception.
   */
  protected IdentifiedException()
  {
    super();
  }



  /**
   * Creates a new identified exception with the provided information.
   *
   * @param  message  The message that explains the problem that
   *                  occurred.
   */
  protected IdentifiedException(LocalizableMessage message)
  {
    super(message);
  }



  /**
   * Creates a new identified exception with the provided information.
   *
   * @param  cause  The underlying cause that triggered this
   *                exception.
   */
  protected IdentifiedException(Throwable cause)
  {
    super(cause);
  }



  /**
   * Creates a new identified exception with the provided information.
   *
   * @param  message  The message that explains the problem that
   *                  occurred.
   * @param  cause    The underlying cause that triggered this
   *                  exception.
   */
  protected IdentifiedException(LocalizableMessage message, Throwable cause)
  {
    super(message, cause);
  }

}

