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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */

package org.opends.server.tools.dsreplication;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.OpenDsException;

/**
 * The exception that is thrown during the replication command-line execution.
 *
 */
public class ReplicationCliException extends OpenDsException {
  private static final long serialVersionUID = -8085682356609610678L;
  private ReplicationCliReturnCode errorCode;

  /**
   * The constructor for the exception.
   * @param message the localized message.
   * @param errorCode the error code associated with this exception.
   * @param cause the cause that generated this exception.
   */
  public ReplicationCliException(LocalizableMessage message,
      ReplicationCliReturnCode errorCode,
      Throwable cause)
  {
    super(message, cause);
    this.errorCode = errorCode;
  }

  /**
   * Returns the error code associated with this exception.
   * @return the error code associated with this exception.
   */
  public ReplicationCliReturnCode getErrorCode()
  {
    return errorCode;
  }
}

