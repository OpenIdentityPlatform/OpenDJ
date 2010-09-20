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

import org.opends.sdk.*;
import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.ControlDecoder;



/**
 * The DIGEST-MD5 SASL bind request as defined in RFC 2831. This SASL mechanism
 * allows a client to perform a challenge-response authentication method,
 * similar to HTTP Digest Access Authentication. This mechanism can be used to
 * negotiate integrity and/or privacy protection for the underlying connection.
 * <p>
 * Compared to CRAM-MD5, DIGEST-MD5 prevents chosen plain-text attacks, and
 * permits the use of third party authentication servers, mutual authentication,
 * and optimized re-authentication if a client has recently authenticated to a
 * server.
 * <p>
 * The authentication and optional authorization identity is specified using an
 * authorization ID, or {@code authzId}, as defined in RFC 4513 section 5.2.1.8.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2831">RFC 2831 - Using Digest
 *      Authentication as a SASL Mechanism </a>
 * @see <a href="http://tools.ietf.org/html/rfc4513#section-5.2.1.8">RFC 4513 -
 *      SASL Authorization Identities (authzId) </a>
 */
public interface DigestMD5SASLBindRequest extends SASLBindRequest
{

  /**
   * The name of the SASL mechanism based on DIGEST-MD5 authentication.
   */
  public static final String SASL_MECHANISM_NAME = "DIGEST-MD5";



  /**
   * Supported quality-of-protection options.
   */
  public static enum QOPOption
  {
    /**
     * Authentication only.
     */
    AUTH,

    /**
     * Authentication plus integrity protection.
     */
    AUTH_INT,

    /**
     * Authentication plus integrity and confidentiality protection.
     */
    AUTH_CONF
  }



  /**
   * Cipher options for use with the security layer.
   */
  public static enum CipherOption
  {
    /**
     * Triple DES
     *   The "triple DES" cipher in CBC mode with EDE with the
     *   same key for each E stage (aka "two keys mode") for a
     *   total key length of 112 bits.
     * <p>
     * RC4 128 bits
     *   The RC4 cipher with a 128 bit key.
     */
    TRIPLE_DES_RC4,

    /**
     * DES
     *   The Data Encryption Standard (DES) cipher [FIPS] in
     *   cipher block chaining (CBC) mode with a 56 bit key.
     * <p>
     * RC4 56 bits
     *   The RC4 cipher with a 56 bit key.
     */
    DES_RC4_56,

    /**
     * RC4 40 bits
     *   The RC4 cipher with a 40 bit key.
     */
    RC4_40
  }

  /**
   * {@inheritDoc}
   */
  DigestMD5SASLBindRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  BindClient createBindClient(String serverName) throws ErrorResultException;



  /**
   * Returns the authentication ID of the user. The authentication ID usually
   * has the form "dn:" immediately followed by the distinguished name of the
   * user, or "u:" followed by a user ID string, but other forms are permitted.
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
   * Returns the name of the Directory object that the client wishes to bind as,
   * which is always the empty string for SASL authentication.
   *
   * @return The name of the Directory object that the client wishes to bind as.
   */
  String getName();



  /**
   * Returns the password of the user that the client wishes to bind as.
   *
   * @return The password of the user that the client wishes to bind as.
   */
  ByteString getPassword();



  /**
   * Returns the optional realm containing the user's account.
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
   * Returns the quality-of-protection options to use.
   * The order of the list specifies the preference order.
   *
   * @return The list of quality-of-protection options to use.
   */
  QOPOption[] getQOP();



  /**
   * Returns the ciphers to use with the optional security layer
   * offered by the {@code AUTH_CONF} quality-of-protection. The order
   * of the list specifies the preference order. When there is
   * more than one choice for a particular option, the cipher
   * selected depends on the availability of the ciphers in the
   * underlying platform.
   *
   * @return The list of cipher options to use.
   */
  CipherOption[] getCipher();



  /**
   * Returns whether the server must authenticate to the client.
   *
   * @return {@code true} if the server must authenticate
   *         to the client or {@code false} otherwise.
   */
  boolean getServerAuth();



  /**
   * Returns the maximum size of the receive buffer in bytes.
   * The actual maximum number of bytes will
   * be the minimum of this number and the peer's maximum send
   * buffer size.
   *
   * @return The maximum size of the receive buffer in bytes.
   */
  int getMaxReceiveBufferSize();



  /**
   * Returns the maximum size of the send buffer in bytes.
   * The actual maximum number of bytes will
   * be the minimum of this number and the peer's maximum receive
   * buffer size.
   *
   * @return The maximum size of the send buffer in bytes.
   */
  int getMaxSendBufferSize();



  /**
   * Sets the authentication ID of the user. The authentication ID usually has
   * the form "dn:" immediately followed by the distinguished name of the user,
   * or "u:" followed by a user ID string, but other forms are permitted.
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
  DigestMD5SASLBindRequest setAuthenticationID(String authenticationID)
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
  DigestMD5SASLBindRequest setAuthorizationID(String authorizationID)
      throws LocalizedIllegalArgumentException;



  /**
   * Sets the password of the user that the client wishes to bind as.
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
  DigestMD5SASLBindRequest setPassword(ByteString password)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the password of the user that the client wishes to bind as. The
   * password will be converted to a UTF-8 octet string.
   *
   * @param password
   *          The password of the user that the client wishes to bind as.
   * @return This bind request.
   * @throws UnsupportedOperationException
   *           If this bind request does not permit the password to be set.
   * @throws NullPointerException
   *           If {@code password} was {@code null}.
   */
  DigestMD5SASLBindRequest setPassword(String password)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the optional realm containing the user's account.
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
  DigestMD5SASLBindRequest setRealm(String realm)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Specifies the quality-of-protection options to use.
   * The order of the list specifies the preference order.
   *
   * @param qopOptions The list of quality-of-protection options to
   *                   use.
   * @return This bind request.
   */
  DigestMD5SASLBindRequest setQOP(QOPOption... qopOptions);



  /**
   * Specifies the ciphers to use with the optional security layer
   * offered by the {@code AUTH_CONF} quality-of-protection. The order
   * of the list specifies the preference order. When there is
   * more than one choice for a particular option, the cipher
   * selected depends on the availability of the ciphers in the
   * underlying platform.
   *
   * @param cipherOptions The list of cipher options to use.
   * @return his bind request.
   */
  DigestMD5SASLBindRequest setCipher(CipherOption... cipherOptions);



  /**
   * Specifies whether the server must authenticate to the client.
   *
   * @param serverAuth {@code true} if the server must authenticate
   *                   to the client or {@code false} otherwise.
   * @return This bind request.
   */
  DigestMD5SASLBindRequest setServerAuth(boolean serverAuth);



  /**
   * Specifies the maximum size of the receive buffer in bytes.
   * The actual maximum number of bytes will
   * be the minimum of this number and the peer's maximum send
   * buffer size.
   *
   * @param maxBuffer The maximum size of the receive buffer in bytes.
   * @return This bind request.
   */
  DigestMD5SASLBindRequest setMaxReceiveBufferSize(int maxBuffer);



  /**
   * Specifies the maximum size of the send buffer in bytes.
   * The actual maximum number of bytes will
   * be the minimum of this number and the peer's maximum receive
   * buffer size.
   *
   * @param maxBuffer The maximum size of the send buffer in bytes.
   * @return This bind request.
   */
  DigestMD5SASLBindRequest setMaxSendBufferSize(int maxBuffer);
}
