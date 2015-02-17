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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.core;
import org.forgerock.i18n.LocalizableMessage;

import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;


/**
 * This interface defines an operation that may be used to authenticate a user
 * to the Directory Server.  Note that for security restrictions, response
 * messages that may be returned to the client must be carefully cleaned to
 * ensure that they do not provide a malicious client with information that may
 * be useful in an attack.  This does impact the debugability of the server,
 * but that can be addressed by calling the <CODE>setAuthFailureReason</CODE>
 * method, which can provide a reason for a failure in a form that will not be
 * returned to the client but may be written to a log file.
 */
public interface BindOperation extends Operation
{

  /**
   * Retrieves the authentication type for this bind operation.
   *
   * @return  The authentication type for this bind operation.
   */
  AuthenticationType getAuthenticationType();

  /**
   * Retrieves the raw, unprocessed bind DN for this bind operation as contained
   * in the client request.  The value may not actually contain a valid DN, as
   * no validation will have been performed.
   *
   * @return  The raw, unprocessed bind DN for this bind operation as contained
   *          in the client request.
   */
  ByteString getRawBindDN();

  /**
   * Specifies the raw, unprocessed bind DN for this bind operation.  This
   * should only be called by pre-parse plugins.
   *
   * @param  rawBindDN  The raw, unprocessed bind DN for this bind operation.
   */
  void setRawBindDN(ByteString rawBindDN);

  /**
   * Retrieves a string representation of the protocol version associated with
   * this bind request.
   *
   * @return  A string representation of the protocol version associated with
   *          this bind request.
   */
  String getProtocolVersion();

  /**
   * Specifies the string representation of the protocol version associated with
   * this bind request.
   *
   * @param  protocolVersion  The string representation of the protocol version
   *                          associated with this bind request.
   */
  void setProtocolVersion(String protocolVersion);

  /**
   * Retrieves the bind DN for this bind operation.  This method should not be
   * called by pre-parse plugins, as the raw value will not have been processed
   * by that time.  Instead, pre-parse plugins should call the
   * <CODE>getRawBindDN</CODE> method.
   *
   * @return  The bind DN for this bind operation, or <CODE>null</CODE> if the
   *          raw DN has not yet been processed.
   */
  DN getBindDN();

  /**
   * Retrieves the simple authentication password for this bind operation.
   *
   * @return  The simple authentication password for this bind operation.
   */
  ByteString getSimplePassword();

  /**
   * Specifies the simple authentication password for this bind operation.
   *
   * @param  simplePassword  The simple authentication password for this bind
   *                         operation.
   */
  void setSimplePassword(ByteString simplePassword);

  /**
   * Retrieves the SASL mechanism for this bind operation.
   *
   * @return  The SASL mechanism for this bind operation, or <CODE>null</CODE>
   *          if the bind does not use SASL authentication.
   */
  String getSASLMechanism();

  /**
   * Retrieves the SASL credentials for this bind operation.
   *
   * @return  The SASL credentials for this bind operation, or <CODE>null</CODE>
   *          if there are none or if the bind does not use SASL authentication.
   */
  ByteString getSASLCredentials();

  /**
   * Specifies the SASL credentials for this bind operation.
   *
   * @param  saslMechanism    The SASL mechanism for this bind operation.
   * @param  saslCredentials  The SASL credentials for this bind operation, or
   *                          <CODE>null</CODE> if there are none.
   */
  void setSASLCredentials(String saslMechanism, ByteString saslCredentials);

  /**
   * Retrieves the set of server SASL credentials to include in the bind
   * response.
   *
   * @return  The set of server SASL credentials to include in the bind
   *          response, or <CODE>null</CODE> if there are none.
   */
  ByteString getServerSASLCredentials();

  /**
   * Specifies the set of server SASL credentials to include in the bind
   * response.
   *
   * @param  serverSASLCredentials  The set of server SASL credentials to
   *                                include in the bind response.
   */
  void setServerSASLCredentials(ByteString serverSASLCredentials);

  /**
   * Retrieves the user entry associated with the SASL authentication attempt.
   * This should be set by any SASL mechanism in which the processing was able
   * to get far enough to make this determination, regardless of whether the
   * authentication was ultimately successful.
   *
   * @return  The user entry associated with the SASL authentication attempt, or
   *          <CODE>null</CODE> if it was not a SASL authentication or the SASL
   *          processing was not able to map the request to a user.
   */
  Entry getSASLAuthUserEntry();

  /**
   * Specifies the user entry associated with the SASL authentication attempt.
   * This should be set by any SASL mechanism in which the processing was able
   * to get far enough to make this determination, regardless of whether the
   * authentication was ultimately successful.
   *
   * @param  saslAuthUserEntry  The user entry associated with the SASL
   *                            authentication attempt.
   */
  void setSASLAuthUserEntry(Entry saslAuthUserEntry);

  /**
   * Retrieves a human-readable message providing the reason that the
   * authentication failed, if available.
   *
   * @return  A human-readable message providing the reason that the
   *          authentication failed, or <CODE>null</CODE> if none is available.
   */
  LocalizableMessage getAuthFailureReason();

  /**
   * Specifies the reason that the authentication failed.
   *
   * @param  message providing the reason that the
   *                 authentication failed.
   */
  void setAuthFailureReason(LocalizableMessage message);

  /**
   * Retrieves the user entry DN for this bind operation.  It will only be
   * available if the bind processing has proceeded far enough to identify the
   * user attempting to authenticate.
   *
   * @return  The user entry DN for this bind operation, or <CODE>null</CODE> if
   *          the bind processing has not progressed far enough to identify the
   *          user or if the user DN could not be determined.
   */
  DN getUserEntryDN();

  /**
   * Retrieves the authentication info that resulted from processing this bind
   * operation.  It will only be valid if the bind processing was successful.
   *
   * @return  The authentication info that resulted from processing this bind
   *          operation.
   */
  AuthenticationInfo getAuthenticationInfo();

  /**
   * Specifies the authentication info that resulted from processing this bind
   * operation.  This method must only be called by SASL mechanism handlers
   * during the course of processing the {@code processSASLBind} method.
   *
   * @param  authInfo  The authentication info that resulted from processing
   *                   this bind operation.
   */
  void setAuthenticationInfo(AuthenticationInfo authInfo);

  /**
   * Set the user entry DN for this bind operation.
   *
   * @param  userEntryDN  The user entry DN for this bind operation, or
   *                      <CODE>null</CODE> if the bind processing has not
   *                      progressed far enough to identify the user or if
   *                      the user DN could not be determined.
   */
  void setUserEntryDN(DN userEntryDN);


}
