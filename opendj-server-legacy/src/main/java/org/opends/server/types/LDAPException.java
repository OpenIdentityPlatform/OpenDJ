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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.types;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;




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



  /** The matched DN associated with this LDAP exception. */
  private final DN matchedDN;

  /** The LDAP result code associated with this exception. */
  private final int resultCode;

  /** The server-provided error message for this LDAP exception. */
  private final LocalizableMessage errorMessage;



  /**
   * Creates a new LDAP exception with the provided message.
   *
   * @param  resultCode  The LDAP result code associated with this
   *                     exception.
   * @param  message     The message that explains the problem that
   *                     occurred.
   */
  public LDAPException(int resultCode, LocalizableMessage message)
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
  public LDAPException(int resultCode, LocalizableMessage errorMessage,
                       LocalizableMessage message)
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
  public LDAPException(int resultCode, LocalizableMessage message,
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
  public LDAPException(int resultCode, LocalizableMessage errorMessage,
                       LocalizableMessage message, Throwable cause)
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
  public LDAPException(int resultCode, LocalizableMessage errorMessage,
                       LocalizableMessage message, DN matchedDN,
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
  public LocalizableMessage getErrorMessage()
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

