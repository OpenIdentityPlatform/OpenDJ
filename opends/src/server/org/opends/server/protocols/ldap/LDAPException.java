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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import org.opends.server.types.DN;



/**
 * This class defines an exception that may be thrown if a problem occurs while
 * interacting with an LDAP protocol element.
 */
public class LDAPException
       extends Exception
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = -7273984376022613884L;



  // The matched DN associated with this LDAP exception.
  private final DN matchedDN;

  // The message ID for the message associated with this LDAP exception.
  private final int messageID;

  // The LDAP result code associated with this exception.
  private final int resultCode;

  // The server-provided error message for this LDAP exception.
  private final String errorMessage;



  /**
   * Creates a new LDAP exception with the provided message.
   *
   * @param  resultCode  The LDAP result code associated with this exception.
   * @param  messageID   The unique identifier for the associated message.
   * @param  message     The message that explains the problem that occurred.
   */
  public LDAPException(int resultCode, int messageID, String message)
  {
    super(message);

    this.resultCode = resultCode;
    this.messageID  = messageID;

    errorMessage = null;
    matchedDN    = null;
  }



  /**
   * Creates a new LDAP exception with the provided message.
   *
   * @param  resultCode    The LDAP result code associated with this exception.
   * @param  errorMessage  The server-provided error message.
   * @param  messageID     The unique identifier for the associated message.
   * @param  message       The message that explains the problem that occurred.
   */
  public LDAPException(int resultCode, String errorMessage, int messageID,
                       String message)
  {
    super(message);

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
    this.messageID    = messageID;

    matchedDN    = null;
  }



  /**
   * Creates a new LDAP exception with the provided message and root cause.
   *
   * @param  resultCode  The LDAP result code associated with this exception.
   * @param  messageID   The unique identifier for the associated message.
   * @param  message     The message that explains the problem that occurred.
   * @param  cause       The exception that was caught to trigger this
   *                     exception.
   */
  public LDAPException(int resultCode, int messageID, String message,
                       Throwable cause)
  {
    super(message, cause);

    this.resultCode = resultCode;
    this.messageID  = messageID;

    errorMessage = null;
    matchedDN    = null;
  }



  /**
   * Creates a new LDAP exception with the provided message and root cause.
   *
   * @param  resultCode    The LDAP result code associated with this exception.
   * @param  errorMessage  The server-provided error message.
   * @param  messageID     The unique identifier for the associated message.
   * @param  message       The message that explains the problem that occurred.
   * @param  cause         The exception that was caught to trigger this
   *                       exception.
   */
  public LDAPException(int resultCode, String errorMessage, int messageID,
                       String message, Throwable cause)
  {
    super(message, cause);

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
    this.messageID    = messageID;

    matchedDN    = null;
  }



  /**
   * Creates a new LDAP exception with the provided message and root cause.
   *
   * @param  resultCode    The LDAP result code associated with this exception.
   * @param  errorMessage  The server-provided error message.
   * @param  messageID     The unique identifier for the associated message.
   * @param  message       The message that explains the problem that occurred.
   * @param  matchedDN     The matched DN returned by the server.
   * @param  cause         The exception that was caught to trigger this
   *                       exception.
   */
  public LDAPException(int resultCode, String errorMessage, int messageID,
                       String message, DN matchedDN, Throwable cause)
  {
    super(message, cause);

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
    this.messageID    = messageID;
    this.matchedDN    = matchedDN;
  }



  /**
   * Retrieves the LDAP result code associated with this exception.
   *
   * @return  The LDAP result code associated with this exception.
   */
  public int getResultCode()
  {
    return resultCode;
  }



  /**
   * Retrieves the server-provided error message for this exception.
   *
   * @return  The server-provided error message for this exception, or
   *          {@code null} if none was given.
   */
  public String getErrorMessage()
  {
    return errorMessage;
  }



  /**
   * Retrieves the unique identifier for the associated message.
   *
   * @return  The unique identifier for the associated message.
   */
  public int getMessageID()
  {
    return messageID;
  }



  /**
   * Retrieves the matched DN for this exception.
   *
   * @return  The matched DN for this exception, or {@code null} if there is
   *          none.
   */
  public DN getMatchedDN()
  {
    return matchedDN;
  }
}

