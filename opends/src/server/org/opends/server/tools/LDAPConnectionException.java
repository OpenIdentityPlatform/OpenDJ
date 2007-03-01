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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;


/**
 * This class defines an exception that may be thrown during the course of
 * creating an LDAP connection to the server.
 */
public class LDAPConnectionException extends Exception
{



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = 3135563348838654570L;


  /**
   * The error code associated with the exception.
   */
  private int errorCode = 1;

  /**
   * Creates a new exception with the provided message.
   *
   * @param  message    The message to use for this exception.
   */
  public LDAPConnectionException(String message)
  {
    super(message);

  }


  /**
   * Creates a new exception with the provided message.
   *
   * @param  message    The message to use for this exception.
   * @param  errorCode  The error code for this exception.
   */
  public LDAPConnectionException(String message, int errorCode)
  {
    super(message);

    this.errorCode = errorCode;
  }


  /**
   * Creates a new exception with the provided message and
   * underlying cause.
   *
   * @param  message    The message to use for this exception.
   * @param  cause      The underlying cause that triggered this
   *                    exception.
   */
  public LDAPConnectionException(String message, Throwable cause)
  {
    super(message, cause);


  }


  /**
   * Creates a new exception with the provided message and
   * underlying cause.
   *
   * @param  message    The message to use for this exception.
   * @param  errorCode  The error code for this exception.
   * @param  cause      The underlying cause that triggered this
   *                    exception.
   */
  public LDAPConnectionException(String message, int errorCode, Throwable cause)
  {
    super(message, cause);

    this.errorCode = errorCode;

  }


  /**
   * Return the error code associated with this exception.
   *
   * @return  The error code associated with this exception.
   */
  public int getErrorCode()
  {
    return this.errorCode;
  }
}

