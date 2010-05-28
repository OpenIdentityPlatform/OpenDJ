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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import java.util.List;

import javax.security.auth.Subject;

import org.opends.sdk.*;
import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.ControlDecoder;



/**
 * The GSSAPI SASL bind request as defined in RFC 2831. This SASL mechanism
 * allows a client to use the Generic Security Service Application Program
 * Interface (GSS-API) Kerberos V5 to authenticate to the server. This mechanism
 * can be used to negotiate integrity and/or privacy protection for the
 * underlying connection.
 * <p>
 * The optional authorization identity is specified using an authorization ID,
 * or {@code authzId}, as defined in RFC 4513 section 5.2.1.8.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4752">RFC 4752 - The Kerberos V5
 *      ("GSSAPI") Simple Authentication and Security Layer (SASL) Mechanism
 *      </a>
 * @see <a href="http://tools.ietf.org/html/rfc4513#section-5.2.1.8">RFC 4513 -
 *      SASL Authorization Identities (authzId) </a>
 */
public interface GSSAPISASLBindRequest extends SASLBindRequest
{

  /**
   * The name of the SASL mechanism based on GSS-API authentication.
   */
  public static final String SASL_MECHANISM_NAME = "GSSAPI";



  /**
   * {@inheritDoc}
   */
  GSSAPISASLBindRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  BindClient createBindClient(String serverName) throws ErrorResultException;



  /**
   * Returns the authentication ID of the user, which should be the user's
   * Kerberos principal. The authentication ID usually has the form "dn:"
   * immediately followed by the distinguished name of the user, or "u:"
   * followed by a user ID string, but other forms are permitted.
   * <p>
   * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
   *
   * @return The authentication ID of the user.
   */
  String getAuthenticationID();



  /**
   * Returns the authentication mechanism identifier for this SASL bind request
   * as defined by the LDAP protocol, which is always {@code 0xA3}.
   *
   * @return The authentication mechanism identifier.
   */
  byte getAuthenticationType();



  /**
   * Returns the optional authorization ID of the user which represents an
   * alternate authorization identity which should be used for subsequent
   * operations performed on the connection. The authorization ID usually has
   * the form "dn:" immediately followed by the distinguished name of the user,
   * or "u:" followed by a user ID string, but other forms are permitted.
   *
   * @return The authorization ID of the user, which may be {@code null}.
   */
  String getAuthorizationID();



  /**
   * {@inheritDoc}
   */
  <C extends Control> C getControl(ControlDecoder<C> decoder,
      DecodeOptions options) throws NullPointerException, DecodeException;



  /**
   * {@inheritDoc}
   */
  List<Control> getControls();



  /**
   * Returns the optional address of the Kerberos KDC (Key Distribution Center).
   * <p>
   * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
   *
   * @return The address of the Kerberos KDC (Key Distribution Center), which
   *         may be {@code null}.
   */
  String getKDCAddress();



  /**
   * Returns the name of the Directory object that the client wishes to bind as,
   * which is always the empty string for SASL authentication.
   *
   * @return The name of the Directory object that the client wishes to bind as.
   */
  String getName();



  /**
   * Returns the password of the user that the client wishes to bind as.
   * <p>
   * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
   *
   * @return The password of the user that the client wishes to bind as.
   */
  ByteString getPassword();



  /**
   * Returns the optional realm containing the user's account.
   * <p>
   * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
   *
   * @return The name of the realm containing the user's account, which may be
   *         {@code null}.
   */
  String getRealm();



  /**
   * {@inheritDoc}
   */
  String getSASLMechanism();



  /**
   * Returns the Kerberos subject of the user to be authenticated.
   * <p>
   * <b>NOTE</b>: if a {@code Subject} is specified then the authentication ID,
   * KDC address, password, and realm, will be ignored.
   *
   * @return The Kerberos subject of the user to be authenticated.
   */
  Subject getSubject();



  /**
   * Sets the authentication ID of the user, which should be the user's Kerberos
   * principal. The authentication ID usually has the form "dn:" immediately
   * followed by the distinguished name of the user, or "u:" followed by a user
   * ID string, but other forms are permitted.
   * <p>
   * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
   *
   * @param authenticationID
   *          The authentication ID of the user.
   * @return This bind request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code authenticationID} was non-empty and did not contain a
   *           valid authorization ID type.
   * @throws NullPointerException
   *           If {@code authenticationID} was {@code null}.
   */
  GSSAPISASLBindRequest setAuthenticationID(String authenticationID)
      throws LocalizedIllegalArgumentException, NullPointerException;



  /**
   * Sets the optional authorization ID of the user which represents an
   * alternate authorization identity which should be used for subsequent
   * operations performed on the connection. The authorization ID usually has
   * the form "dn:" immediately followed by the distinguished name of the user,
   * or "u:" followed by a user ID string, but other forms are permitted.
   *
   * @param authorizationID
   *          The authorization ID of the user, which may be {@code null}.
   * @return This bind request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code authorizationID} was non-empty and did not contain a
   *           valid authorization ID type.
   */
  GSSAPISASLBindRequest setAuthorizationID(String authorizationID)
      throws LocalizedIllegalArgumentException;



  /**
   * Sets the optional address of the Kerberos KDC (Key Distribution Center).
   * <p>
   * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
   *
   * @param address
   *          The address of the Kerberos KDC (Key Distribution Center), which
   *          may be {@code null}.
   * @return This bind request.
   * @throws UnsupportedOperationException
   *           If this bind request does not permit the KDC address to be set.
   * @throws NullPointerException
   *           If {@code address} was {@code null}.
   */
  GSSAPISASLBindRequest setKDCAddress(String address)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the password of the user that the client wishes to bind as.
   * <p>
   * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
   *
   * @param password
   *          The password of the user that the client wishes to bind as, which
   *          may be empty.
   * @return This bind request.
   * @throws UnsupportedOperationException
   *           If this bind request does not permit the password to be set.
   * @throws NullPointerException
   *           If {@code password} was {@code null}.
   */
  GSSAPISASLBindRequest setPassword(ByteString password)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the password of the user that the client wishes to bind as. The
   * password will be converted to a UTF-8 octet string.
   * <p>
   * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
   *
   * @param password
   *          The password of the user that the client wishes to bind as.
   * @return This bind request.
   * @throws UnsupportedOperationException
   *           If this bind request does not permit the password to be set.
   * @throws NullPointerException
   *           If {@code password} was {@code null}.
   */
  GSSAPISASLBindRequest setPassword(String password)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the optional realm containing the user's account.
   * <p>
   * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
   *
   * @param realm
   *          The name of the realm containing the user's account, which may be
   *          {@code null}.
   * @return This bind request.
   * @throws UnsupportedOperationException
   *           If this bind request does not permit the realm to be set.
   * @throws NullPointerException
   *           If {@code realm} was {@code null}.
   */
  GSSAPISASLBindRequest setRealm(String realm)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the Kerberos subject of the user to be authenticated.
   * <p>
   * <b>NOTE</b>: if a {@code Subject} is specified then the authentication ID,
   * KDC address, password, and realm, will be ignored.
   *
   * @param subject
   *          The Kerberos subject of the user to be authenticated.
   * @return This bind request.
   * @throws NullPointerException
   *           If {@code subject} was {@code null}.
   */
  GSSAPISASLBindRequest setSubject(Subject subject) throws NullPointerException;

}
