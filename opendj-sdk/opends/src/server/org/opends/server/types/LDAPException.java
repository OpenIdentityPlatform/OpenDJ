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
package org.opends.server.types;
import org.opends.messages.Message;




/**
 * This class defines an exception that may be thrown if a problem
 * occurs while interacting with an LDAP protocol element.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class LDAPException
       extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class extends {@code java.lang.Exception}, which
   * implements the {@code java.io.Serializable} interface.  This
   * value was generated using the {@code serialver} command-line
   * utility included with the Java SDK.
   */
  private static final long serialVersionUID = -7273984376022613884L;



  // The matched DN associated with this LDAP exception.
  private final DN matchedDN;

  // The LDAP result code associated with this exception.
  private final int resultCode;

  // The server-provided error message for this LDAP exception.
  private final Message errorMessage;



  /**
   * Creates a new LDAP exception with the provided message.
   *
   * @param  resultCode  The LDAP result code associated with this
   *                     exception.
   * @param  message     The message that explains the problem that
   *                     occurred.
   */
  public LDAPException(int resultCode, Message message)
  {
    super(message);

    this.resultCode = resultCode;

    errorMessage = null;
    matchedDN    = null;
  }



  /**
   * Creates a new LDAP exception with the provided message.
   *
   * @param  resultCode    The LDAP result code associated with this
   *                       exception.
   * @param  errorMessage  The server-provided error message.
   * @param  message       The message that explains the problem that
   *                       occurred.
   */
  public LDAPException(int resultCode, Message errorMessage,
                       Message message)
  {
    super(message);

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;

    matchedDN    = null;
  }



  /**
   * Creates a new LDAP exception with the provided message and root
   * cause.
   *
   * @param  resultCode  The LDAP result code associated with this
   *                     exception.
   * @param  message     The message that explains the problem that
   *                     occurred.
   * @param  cause       The exception that was caught to trigger this
   *                     exception.
   */
  public LDAPException(int resultCode, Message message,
                       Throwable cause)
  {
    super(message, cause);

    this.resultCode = resultCode;

    errorMessage = null;
    matchedDN    = null;
  }



  /**
   * Creates a new LDAP exception with the provided message and root
   * cause.
   *
   * @param  resultCode    The LDAP result code associated with this
   *                       exception.
   * @param  errorMessage  The server-provided error message.
   * @param  message       The message that explains the problem that
   *                       occurred.
   * @param  cause         The exception that was caught to trigger
   *                       this exception.
   */
  public LDAPException(int resultCode, Message errorMessage,
                       Message message, Throwable cause)
  {
    super(message, cause);

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;

    matchedDN    = null;
  }



  /**
   * Creates a new LDAP exception with the provided message and root
   * cause.
   *
   * @param  resultCode    The LDAP result code associated with this
   *                       exception.
   * @param  errorMessage  The server-provided error message.
   * @param  message       The message that explains the problem that
   *                       occurred.
   * @param  matchedDN     The matched DN returned by the server.
   * @param  cause         The exception that was caught to trigger
   *                       this exception.
   */
  public LDAPException(int resultCode, Message errorMessage,
                       Message message, DN matchedDN,
                       Throwable cause)
  {
    super(message, cause);

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
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
  public Message getErrorMessage()
  {
    return errorMessage;
  }



  /**
   * Retrieves the matched DN for this exception.
   *
   * @return  The matched DN for this exception, or {@code null} if
   *          there is none.
   */
  public DN getMatchedDN()
  {
    return matchedDN;
  }
}

