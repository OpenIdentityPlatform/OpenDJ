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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;


import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;



/**
 * This class defines an exception that may be thrown during the course of
 * creating an LDAP connection to the server.
 */
public class LDAPConnectionException extends OpenDsException {

  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = 3135563348838654570L;


  /**
   * The LDAP result code associated with the exception.
   */
  private final int resultCode;


  /**
   * The matched DN associated with the exception.
   */
  private final DN matchedDN;


  /**
   * The server-provided error message for this exception.
   */
  private final Message errorMessage;


  /**
   * Creates a new exception with the provided message.
   *
   * @param  message    The message to use for this exception.
   */
  public LDAPConnectionException(Message message)
  {
    super(message);

    resultCode   = -1;
    matchedDN    = null;
    errorMessage = null;
  }


  /**
   * Creates a new exception with the provided message.
   *
   * @param  message       The message to use for this exception.
   * @param  resultCode    The result code for this exception.
   * @param  errorMessage  The server-provided error message for this exception.
   */
  public LDAPConnectionException(Message message, int resultCode,
                                 Message errorMessage)
  {
    super(message);

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;

    matchedDN = null;
  }


  /**
   * Creates a new exception with the provided message and
   * underlying cause.
   *
   * @param  message    The message to use for this exception.
   * @param  cause      The underlying cause that triggered this
   *                    exception.
   */
  public LDAPConnectionException(Message message, Throwable cause)
  {
    super(message, cause);

    resultCode   = -1;
    matchedDN    = null;
    errorMessage = null;
  }


  /**
   * Creates a new exception with the provided message and
   * underlying cause.
   *
   * @param  message       The message to use for this exception.
   * @param  resultCode    The result code for this exception.
   * @param  errorMessage  The server-provided error message for this exception.
   * @param  cause         The underlying cause that triggered this
   *                       exception.
   */
  public LDAPConnectionException(Message message, int resultCode,
                                 Message errorMessage, Throwable cause)
  {
    super(message, cause);

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;

    matchedDN = null;
  }


  /**
   * Creates a new exception with the provided message and
   * underlying cause.
   *
   * @param  message       The explanation to use for this exception.
   * @param  resultCode    The result code for this exception.
   * @param  errorMessage  The server-provided error message for this
   *                       exception.
   * @param  matchedDN     The matched DN string for this exception.
   * @param  cause         The underlying cause that triggered this
   *                       exception.
   */
  public LDAPConnectionException(Message message, int resultCode,
                                 Message errorMessage, DN matchedDN,
                                 Throwable cause)
  {
    super(message, cause);

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
    this.matchedDN    = matchedDN;
  }


  /**
   * Return the result code associated with this exception.
   *
   * @return  The result code associated with this exception, or -1 if none was
   *          provided.
   */
  public int getResultCode()
  {
    return this.resultCode;
  }


  /**
   * Retrieves the server-provided error message associated with this exception.
   *
   * @return  The server-provided error message associated with this exception.
   */
  public Message getErrorMessage()
  {
    return this.errorMessage;
  }


  /**
   * Return the matched DN associated with this exception.
   *
   * @return  The matched DN associated with this exception, or {@code null} if
   *          none was provided.
   */
  public DN getMatchedDN()
  {
    return this.matchedDN;
  }
}

