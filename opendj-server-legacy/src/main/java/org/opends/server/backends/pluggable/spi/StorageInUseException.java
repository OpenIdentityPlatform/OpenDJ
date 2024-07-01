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
 * Copyright 2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable.spi;

/**
 * Runtime exception for storage supporting single access only.
 */
@SuppressWarnings("serial")
public final class StorageInUseException extends StorageRuntimeException
{

  /**
   * Constructor with a message and a cause.
   *
   * @param message
   *          the exception message
   * @param cause
   *          the cause of the exception
   */
  public StorageInUseException(String message, Throwable cause)
  {
    super(message, cause);
  }

  /**
   * Constructor with a message.
   *
   * @param message
   *          the exception message
   */
  public StorageInUseException(String message)
  {
    super(message);
  }

  /**
   * Constructor with a cause.
   *
   * @param cause
   *          the cause of the exception
   */
  public StorageInUseException(Throwable cause)
  {
    super(cause);
  }

}
