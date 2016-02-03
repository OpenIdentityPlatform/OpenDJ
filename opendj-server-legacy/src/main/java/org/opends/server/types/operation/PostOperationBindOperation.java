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
package org.opends.server.types.operation;
import org.forgerock.i18n.LocalizableMessage;



import org.opends.server.types.AuthenticationType;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;



/**
 * This class defines a set of methods that are available for use by
 * post-operation plugins for bind operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PostOperationBindOperation
       extends PostOperationOperation
{
  /**
   * Retrieves the authentication type for this bind operation.
   *
   * @return  The authentication type for this bind operation.
   */
  AuthenticationType getAuthenticationType();



  /**
   * Retrieves a string representation of the protocol version
   * associated with this bind request.
   *
   * @return  A string representation of the protocol version
   *          associated with this bind request.
   */
  String getProtocolVersion();



  /**
   * Retrieves the raw, unprocessed bind DN for this bind operation as
   * contained in the client request.  The value may not actually
   * contain a valid DN, as no validation will have been performed.
   *
   * @return  The raw, unprocessed bind DN for this bind operation as
   *          contained in the client request.
   */
  ByteString getRawBindDN();



  /**
   * Retrieves the bind DN for this bind operation.
   *
   * @return  The bind DN for this bind operation.
   */
  DN getBindDN();



  /**
   * Retrieves the simple authentication password for this bind operation.
   *
   * @return  The simple authentication password for this bind
   *          operation.
   */
  ByteString getSimplePassword();



  /**
   * Retrieves the SASL mechanism for this bind operation.
   *
   * @return  The SASL mechanism for this bind operation, or
   *          <CODE>null</CODE> if the bind does not use SASL
   *          authentication.
   */
  String getSASLMechanism();



  /**
   * Retrieves the SASL credentials for this bind operation.
   *
   * @return  The SASL credentials for this bind operation, or
   *          <CODE>null</CODE> if there are none or if the bind does
   *          not use SASL authentication.
   */
  ByteString getSASLCredentials();



  /**
   * Retrieves the set of server SASL credentials to include in the
   * bind response.
   *
   * @return  The set of server SASL credentials to include in the
   *          bind response, or <CODE>null</CODE> if there are none.
   */
  ByteString getServerSASLCredentials();



  /**
   * Specifies the set of server SASL credentials to include in the
   * bind response.
   *
   * @param  serverSASLCredentials  The set of server SASL credentials
   *                                to include in the bind response.
   */
  void setServerSASLCredentials(ByteString serverSASLCredentials);



  /**
   * Retrieves the user entry associated with the SASL authentication
   * attempt.  This should be set by any SASL mechanism in which the
   * processing was able to get far enough to make this determination,
   * regardless of whether the authentication was ultimately
   * successful.
   *
   * @return  The user entry associated with the SASL authentication
   *          attempt, or <CODE>null</CODE> if it was not a SASL
   *          authentication or the SASL processing was not able to
   *          map the request to a user.
   */
  Entry getSASLAuthUserEntry();



  /**
   * Retrieves a human-readable message providing the reason that the
   * authentication failed, if available.
   *
   * @return  A human-readable message providing the reason that the
   *          authentication failed, or <CODE>null</CODE> if none is
   *          available.
   */
  LocalizableMessage getAuthFailureReason();



  /**
   * Specifies the reason that the authentication failed.
   *
   * @param  reason  A human-readable message providing the reason
   *                 that the authentication failed.
   */
  void setAuthFailureReason(LocalizableMessage reason);



  /**
   * Retrieves the user entry DN for this bind operation.  It will
   * only be available if the bind processing has proceeded far enough
   * to identify the user attempting to authenticate.
   *
   * @return  The user entry DN for this bind operation, or
   *          <CODE>null</CODE> if the bind processing has not
   *          progressed far enough to identify the user or if the
   *          user DN could not be determined.
   */
  DN getUserEntryDN();
}

