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

package org.opends.guitools.controlpanel.task;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.OpenDsException;

/**
 * Exception throw when there is an error updating the configuration online
 * (in general is used as a wrapper when we get a NamingException).
 *
 */
public class OnlineUpdateException extends OpenDsException
{

  private static final long serialVersionUID = 2594845362087209988L;

  /**
   * Creates an exception with a message.
   * @param msg the message.
   */
  public OnlineUpdateException(LocalizableMessage msg)
  {
    super(msg);
  }

  /**
   * Creates an exception with a message and a root cause.
   * @param msg the message.
   * @param rootCause the root cause.
   */
  public OnlineUpdateException(LocalizableMessage msg, Throwable rootCause)
  {
    super(msg, rootCause);
  }
}
