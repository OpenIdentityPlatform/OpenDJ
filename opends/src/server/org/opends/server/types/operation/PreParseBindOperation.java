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
package org.opends.server.types.operation;
import org.opends.messages.Message;



import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.ByteString;



/**
 * This class defines a set of methods that are available for use by
 * pre-parse plugins for bind operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PreParseBindOperation
       extends PreParseOperation
{
  /**
   * Retrieves the authentication type for this bind operation.
   *
   * @return  The authentication type for this bind operation.
   */
  public AuthenticationType getAuthenticationType();



  /**
   * Retrieves a string representation of the protocol version
   * associated with this bind request.
   *
   * @return  A string representation of the protocol version
   *          associated with this bind request.
   */
  public String getProtocolVersion();



  /**
   * Specifies the string representation of the protocol version
   * associated with this bind request.
   *
   * @param  protocolVersion  The string representation of the
   *                          protocol version associated with this
   *                          bind request.
   */
  public void setProtocolVersion(String protocolVersion);



  /**
   * Retrieves the raw, unprocessed bind DN for this bind operation as
   * contained in the client request.  The value may not actually
   * contain a valid DN, as no validation will have been performed.
   *
   * @return  The raw, unprocessed bind DN for this bind operation as
   *          contained in the client request.
   */
  public ByteString getRawBindDN();



  /**
   * Specifies the raw, unprocessed bind DN for this bind operation.
   *
   * @param  rawBindDN  The raw, unprocessed bind DN for this bind
   *                    operation.
   */
  public void setRawBindDN(ByteString rawBindDN);



  /**
   * Retrieves the simple authentication password for this bind
   * operation.
   *
   * @return  The simple authentication password for this bind
   *          operation.
   */
  public ByteString getSimplePassword();



  /**
   * Specifies the simple authentication password for this bind
   * operation.
   *
   * @param  simplePassword  The simple authentication password for
   *                         this bind operation.
   */
  public void setSimplePassword(ByteString simplePassword);



  /**
   * Retrieves the SASL mechanism for this bind operation.
   *
   * @return  The SASL mechanism for this bind operation, or
   *          <CODE>null</CODE> if the bind does not use SASL
   *          authentication.
   */
  public String getSASLMechanism();



  /**
   * Retrieves the SASL credentials for this bind operation.
   *
   * @return  The SASL credentials for this bind operation, or
   *          <CODE>null</CODE> if there are none or if the bind does
   *          not use SASL authentication.
   */
  public ASN1OctetString getSASLCredentials();



  /**
   * Specifies the SASL credentials for this bind operation.
   *
   * @param  saslMechanism    The SASL mechanism for this bind
   *                          operation.
   * @param  saslCredentials  The SASL credentials for this bind
   *                          operation, or <CODE>null</CODE> if there
   *                          are none.
   */
  public void setSASLCredentials(String saslMechanism,
                                 ASN1OctetString saslCredentials);



  /**
   * Specifies the set of server SASL credentials to include in the
   * bind response.
   *
   * @param  serverSASLCredentials  The set of server SASL credentials
   *                                to include in the bind response.
   */
  public void setServerSASLCredentials(ASN1OctetString
                                            serverSASLCredentials);



  /**
   * Specifies the reason that the authentication failed.
   *
   * @param  reason  A human-readable message providing the reason
   *                 that the authentication failed.
   */
  public void setAuthFailureReason(Message reason);
}

