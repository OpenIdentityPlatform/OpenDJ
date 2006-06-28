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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines an exception that may be thrown if a problem occurs while
 * interacting with an LDAP protocol element.
 */
public class LDAPException
       extends Exception
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.ldap.LDAPException";



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = -7273984376022613884L;



  // The message ID for the message associated with this initialization
  // exception.
  private int messageID;

  // The LDAP result code associated with this exception.
  private int resultCode;



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

    assert debugConstructor(CLASS_NAME, String.valueOf(resultCode),
                            String.valueOf(messageID), String.valueOf(message));

    this.resultCode = resultCode;
    this.messageID  = messageID;
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

    assert debugConstructor(CLASS_NAME, String.valueOf(resultCode),
                            String.valueOf(messageID), String.valueOf(message),
                            String.valueOf(cause));

    this.resultCode = resultCode;
    this.messageID  = messageID;
  }



  /**
   * Retrieves the LDAP result code associated with this exception.
   *
   * @return  The LDAP result code associated with this exception.
   */
  public int getResultCode()
  {
    assert debugEnter(CLASS_NAME, "getResultCode");

    return resultCode;
  }



  /**
   * Retrieves the unique identifier for the associated message.
   *
   * @return  The unique identifier for the associated message.
   */
  public int getMessageID()
  {
    assert debugEnter(CLASS_NAME, "getMessageID");

    return messageID;
  }
}

