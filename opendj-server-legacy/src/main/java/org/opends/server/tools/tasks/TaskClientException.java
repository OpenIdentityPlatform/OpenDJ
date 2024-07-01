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

package org.opends.server.tools.tasks;

import org.forgerock.i18n.LocalizableMessage;

import org.opends.server.types.OpenDsException;

/**
 * Exception for problems related to interacting with the task backend.
 */
public class TaskClientException extends OpenDsException {

  private static final long serialVersionUID = 3800881643050096416L;

  /**
   * Constructs a default instance.
   */
  public TaskClientException() {
  }

  /**
   * Constructs a parameterized instance.
   *
   * @param cause of this exception
   */
  public TaskClientException(OpenDsException cause) {
    super(cause);
  }

  /**
   * Constructs a parameterized instance.
   *
   * @param message for this exception
   */
  public TaskClientException(LocalizableMessage message) {
    super(message);
  }

  /**
   * Constructs a parameterized instance.
   *
   * @param cause of this exception
   */
  public TaskClientException(Throwable cause) {
    super(cause);
  }

  /**
   * Constructs a parameterized instance.
   *
   * @param message for this exception
   * @param cause of this exception
   */
  public TaskClientException(LocalizableMessage message, Throwable cause) {
    super(message, cause);
  }

}
