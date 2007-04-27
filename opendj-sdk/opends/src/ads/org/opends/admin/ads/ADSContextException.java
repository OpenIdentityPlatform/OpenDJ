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

package org.opends.admin.ads;

/**
 * This is the exception that is thrown in ADSContext.
 * @see ADSContext.
 *
 */
public class ADSContextException extends Exception {

  private static final long serialVersionUID = 1984039711031042813L;

  private String toString;

  /**
   * The enumeration containing the different error types.
   *
   */
  public enum ErrorType
  {
    /**
     * The host name is missing.
     */
    MISSING_HOSTNAME,
    /**
     * The host name is not valid.
     */
    NOVALID_HOSTNAME,
    /**
     * The installation path is missing.
     */
    MISSING_IPATH,
    /**
     * The installation path is not valid.
     */
    NOVALID_IPATH,
    /**
     * An access permission error.
     */
    ACCESS_PERMISSION,
    /**
     * The entity is already registered.
     */
    ALREADY_REGISTERED,
    /**
     * The installation is broken.
     */
    BROKEN_INSTALL,
    /**
     * The entity is not yet registered.
     */
    NOT_YET_REGISTERED,
    /**
     * The port is missing.
     */
    MISSING_PORT,
    /**
     * The port is not valid.
     */
    NOVALID_PORT,
    /**
     * The name is missing.
     */
    MISSING_NAME,
    /**
     * The administration UID is missing.
     */
    MISSING_ADMIN_UID,
    /**
     * The administratior password is missing.
     */
    MISSING_ADMIN_PASSWORD,
    /**
     * Unexpected error (potential bug).
     */
    ERROR_UNEXPECTED
  };

  ErrorType error;
  Throwable embeddedException;

  /**
   * Creates an ADSContextException of the given error type.
   * @param error the error type.
   */
  public ADSContextException(ErrorType error)
  {
    this.error = error;
  }

  /**
   * Creates an ADSContextException of the given error type with the provided
   * error cause.
   * @param error the error type.
   * @param x the throwable that generated this exception.
   */
  public ADSContextException(ErrorType error, Throwable x)
  {
    this.error = error;
    this.embeddedException = x;
  }

  /**
   * Returns the error type of this exception.
   * @return the error type of this exception.
   */
  public ErrorType getError()
  {
    return error;
  }

  /**
   * Returns the throwable that caused this exception.  It might be null.
   * @return the throwable that caused this exception.
   */
  public Throwable getCause()
  {
    return embeddedException;
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    if (toString == null)
    {
      toString = "ADSContextException: error type"+error+".";
      if (getCause() != null)
      {
        toString += "  Root cause: "+getCause().toString();
      }
    }
    return toString;
  }

  /**
   * {@inheritDoc}
   */
  public void printStackTrace()
  {
    super.printStackTrace();
    if (embeddedException != null)
    {
      System.out.println("embeddedException = {");
      embeddedException.printStackTrace();
      System.out.println("}");
    }
  }
}