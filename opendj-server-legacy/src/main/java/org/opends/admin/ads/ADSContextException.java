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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.admin.ads;

import static org.opends.messages.QuickSetupMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.OpenDsException;

/**
 * This is the exception that is thrown in ADSContext.
 * @see org.opends.admin.ads.ADSContext
 */
public class ADSContextException extends OpenDsException {
  private static final long serialVersionUID = 1984039711031042813L;

  /** The enumeration containing the different error types. */
  public enum ErrorType
  {
    /** The host name is missing. */
    MISSING_HOSTNAME,
    /** The host name is not valid. */
    NOVALID_HOSTNAME,
    /** The installation path is missing. */
    MISSING_IPATH,
    /** The installation path is not valid. */
    NOVALID_IPATH,
    /** An access permission error. */
    ACCESS_PERMISSION,
    /** The entity is already registered. */
    ALREADY_REGISTERED,
    /** The installation is broken. */
    BROKEN_INSTALL,
    /** The entity is not yet registered. */
    NOT_YET_REGISTERED,
    /** The port is missing. */
    MISSING_PORT,
    /** The port is not valid. */
    NOVALID_PORT,
    /** The name is missing. */
    MISSING_NAME,
    /** The administration UID is missing. */
    MISSING_ADMIN_UID,
    /** The administrator password is missing. */
    MISSING_ADMIN_PASSWORD,
    /** There is already a backend with the name of the ADS backend but not of the expected type. */
    UNEXPECTED_ADS_BACKEND_TYPE,
    /** Error merging with another ADSContext. */
    ERROR_MERGING,
    /** Unexpected error (potential bug). */
    ERROR_UNEXPECTED;
  }

  private final ErrorType error;
  private final String toString;

  /**
   * Creates an ADSContextException of the given error type.
   * @param error the error type.
   */
  ADSContextException(ErrorType error)
  {
    this(error, null);
  }

  /**
   * Creates an ADSContextException of the given error type with the provided
   * error cause.
   * @param error the error type.
   * @param x the throwable that generated this exception.
   */
  ADSContextException(ErrorType error, Throwable x)
  {
    this(error, getMessage(error, x), x);
  }

  /**
   * Creates an ADSContextException of the given error type with the provided error cause and
   * message.
   *
   * @param error
   *          the error type.
   * @param msg
   *          the message describing the error.
   * @param cause
   *          the throwable that generated this exception.
   */
  ADSContextException(ErrorType error, LocalizableMessage msg, Throwable cause)
  {
    super(msg, cause);
    this.error = error;
    toString = "ADSContextException: error type " + error + "." + (cause != null ? "  Root cause: " + cause : "");
  }

  /**
   * Returns the error type of this exception.
   * @return the error type of this exception.
   */
  public ErrorType getError()
  {
    return error;
  }

  /** {@inheritDoc} */
  @Override
  public void printStackTrace()
  {
    super.printStackTrace();
    if (getCause() != null)
    {
      System.out.println("embeddedException = {");
      getCause().printStackTrace();
      System.out.println("}");
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return toString;
  }

  private static LocalizableMessage getMessage(ErrorType error, Throwable x)
  {
    if (x instanceof OpenDsException)
    {
      return INFO_ADS_CONTEXT_EXCEPTION_WITH_DETAILS_MSG.get(error,
          ((OpenDsException)x).getMessageObject());
    } else if (x != null)
    {
      return INFO_ADS_CONTEXT_EXCEPTION_WITH_DETAILS_MSG.get(error, x);
    }
    else
    {
      return INFO_ADS_CONTEXT_EXCEPTION_MSG.get(error);
    }
  }
}
