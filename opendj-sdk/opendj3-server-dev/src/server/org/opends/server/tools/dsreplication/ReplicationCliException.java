/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
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

