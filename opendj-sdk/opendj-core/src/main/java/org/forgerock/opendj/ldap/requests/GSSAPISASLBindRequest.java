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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

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
public interface GSSAPISASLBindRequest extends SASLBindRequest {

    /**
     * Indicates that the client will accept authentication only. More
     * specifically, the underlying connection will not be protected using
     * integrity protection or encryption, unless previously established using
     * SSL/TLS. This is the default if no QOP option is present in the bind
     * request.
     */
    String QOP_AUTH = "auth";

    /**
     * Indicates that the client will accept authentication with connection
     * integrity protection and encryption.
     */
    String QOP_AUTH_CONF = "auth-conf";

    /**
     * Indicates that the client will accept authentication with connection
     * integrity protection. More specifically, the underlying connection will
     * not be encrypted, unless previously established using SSL/TLS.
     */
    String QOP_AUTH_INT = "auth-int";

    /**
     * The name of the SASL mechanism based on GSS-API authentication.
     */
    String SASL_MECHANISM_NAME = "GSSAPI";

    /**
     * Adds the provided additional authentication parameter to the list of
     * parameters to be passed to the underlying mechanism implementation. This
     * method is provided in order to allow for future extensions.
     *
     * @param name
     *            The name of the additional authentication parameter.
     * @param value
     *            The value of the additional authentication parameter.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit additional
     *             authentication parameters to be added.
     * @throws NullPointerException
     *             If {@code name} or {@code value} was {@code null}.
     */
    GSSAPISASLBindRequest addAdditionalAuthParam(String name, String value);

    @Override
    GSSAPISASLBindRequest addControl(Control control);

    /**
     * Adds the provided quality of protection (QOP) values to the ordered list
     * of QOP values that the client is willing to accept. The order of the list
     * specifies the preference order, high to low. Authentication will fail if
     * no QOP values are recognized or accepted by the server.
     * <p>
     * By default the client will accept {@link #QOP_AUTH AUTH}.
     *
     * @param qopValues
     *            The quality of protection values that the client is willing to
     *            accept.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit QOP values to be added.
     * @throws NullPointerException
     *             If {@code qopValues} was {@code null}.
     * @see #QOP_AUTH
     * @see #QOP_AUTH_INT
     * @see #QOP_AUTH_CONF
     */
    GSSAPISASLBindRequest addQOP(String... qopValues);

    @Override
    BindClient createBindClient(String serverName) throws LdapException;

    /**
     * Returns a map containing the provided additional authentication
     * parameters to be passed to the underlying mechanism implementation. This
     * method is provided in order to allow for future extensions.
     *
     * @return A map containing the provided additional authentication
     *         parameters to be passed to the underlying mechanism
     *         implementation.
     */
    Map<String, String> getAdditionalAuthParams();

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
     * Returns the authentication mechanism identifier for this SASL bind
     * request as defined by the LDAP protocol, which is always {@code 0xA3}.
     *
     * @return The authentication mechanism identifier.
     */
    @Override
    byte getAuthenticationType();

    /**
     * Returns the optional authorization ID of the user which represents an
     * alternate authorization identity which should be used for subsequent
     * operations performed on the connection. The authorization ID usually has
     * the form "dn:" immediately followed by the distinguished name of the
     * user, or "u:" followed by a user ID string, but other forms are
     * permitted.
     *
     * @return The authorization ID of the user, which may be {@code null}.
     */
    String getAuthorizationID();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the optional address of the Kerberos KDC (Key Distribution
     * Center).
     * <p>
     * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
     *
     * @return The address of the Kerberos KDC (Key Distribution Center), which
     *         may be {@code null}.
     */
    String getKDCAddress();

    /**
     * Returns the maximum size of the receive buffer in bytes. The actual
     * maximum number of bytes will be the minimum of this number and the peer's
     * maximum send buffer size. The default size is 65536.
     *
     * @return The maximum size of the receive buffer in bytes.
     */
    int getMaxReceiveBufferSize();

    /**
     * Returns the maximum size of the send buffer in bytes. The actual maximum
     * number of bytes will be the minimum of this number and the peer's maximum
     * receive buffer size. The default size is 65536.
     *
     * @return The maximum size of the send buffer in bytes.
     */
    int getMaxSendBufferSize();

    /**
     * Returns the name of the Directory object that the client wishes to bind
     * as, which is always the empty string for SASL authentication.
     *
     * @return The name of the Directory object that the client wishes to bind
     *         as.
     */
    @Override
    String getName();

    /**
     * Returns the password of the user that the client wishes to bind as.
     * <p>
     * Unless otherwise indicated, implementations will store a reference to the
     * returned password byte array, allowing applications to overwrite the
     * password after it has been used.
     * <p>
     * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
     *
     * @return The password of the user that the client wishes to bind as.
     */
    byte[] getPassword();

    /**
     * Returns the ordered list of quality of protection (QOP) values that the
     * client is willing to accept. The order of the list specifies the
     * preference order, high to low. Authentication will fail if no QOP values
     * are recognized or accepted by the server.
     * <p>
     * By default the client will accept {@link #QOP_AUTH AUTH}.
     *
     * @return The list of quality of protection values that the client is
     *         willing to accept. The returned list may be empty indicating that
     *         the default QOP will be accepted.
     */
    List<String> getQOPs();

    /**
     * Returns the optional realm containing the user's account.
     * <p>
     * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
     *
     * @return The name of the realm containing the user's account, which may be
     *         {@code null}.
     */
    String getRealm();

    @Override
    String getSASLMechanism();

    /**
     * Returns the Kerberos subject of the user to be authenticated.
     * <p>
     * <b>NOTE</b>: if a {@code Subject} is specified then the authentication
     * ID, KDC address, password, and realm, will be ignored.
     *
     * @return The Kerberos subject of the user to be authenticated.
     */
    Subject getSubject();

    /**
     * Returns {@code true} if the server must authenticate to the client. The
     * default is {@code false}.
     *
     * @return {@code true} if the server must authenticate to the client.
     */
    boolean isServerAuth();

    /**
     * Sets the authentication ID of the user, which should be the user's
     * Kerberos principal. The authentication ID usually has the form "dn:"
     * immediately followed by the distinguished name of the user, or "u:"
     * followed by a user ID string, but other forms are permitted.
     * <p>
     * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
     *
     * @param authenticationID
     *            The authentication ID of the user.
     * @return This bind request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code authenticationID} was non-empty and did not contain
     *             a valid authorization ID type.
     * @throws NullPointerException
     *             If {@code authenticationID} was {@code null}.
     */
    GSSAPISASLBindRequest setAuthenticationID(String authenticationID);

    /**
     * Sets the optional authorization ID of the user which represents an
     * alternate authorization identity which should be used for subsequent
     * operations performed on the connection. The authorization ID usually has
     * the form "dn:" immediately followed by the distinguished name of the
     * user, or "u:" followed by a user ID string, but other forms are
     * permitted.
     *
     * @param authorizationID
     *            The authorization ID of the user, which may be {@code null}.
     * @return This bind request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code authorizationID} was non-empty and did not contain
     *             a valid authorization ID type.
     */
    GSSAPISASLBindRequest setAuthorizationID(String authorizationID);

    /**
     * Sets the optional address of the Kerberos KDC (Key Distribution Center).
     * <p>
     * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
     *
     * @param address
     *            The address of the Kerberos KDC (Key Distribution Center),
     *            which may be {@code null}.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the KDC address to be
     *             set.
     * @throws NullPointerException
     *             If {@code address} was {@code null}.
     */
    GSSAPISASLBindRequest setKDCAddress(String address);

    /**
     * Sets the maximum size of the receive buffer in bytes. The actual maximum
     * number of bytes will be the minimum of this number and the peer's maximum
     * send buffer size. The default size is 65536.
     *
     * @param size
     *            The maximum size of the receive buffer in bytes.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the buffer size to be
     *             set.
     */
    GSSAPISASLBindRequest setMaxReceiveBufferSize(int size);

    /**
     * Sets the maximum size of the send buffer in bytes. The actual maximum
     * number of bytes will be the minimum of this number and the peer's maximum
     * receive buffer size. The default size is 65536.
     *
     * @param size
     *            The maximum size of the send buffer in bytes.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the buffer size to be
     *             set.
     */
    GSSAPISASLBindRequest setMaxSendBufferSize(int size);

    /**
     * Sets the password of the user that the client wishes to bind as.
     * <p>
     * Unless otherwise indicated, implementations will store a reference to the
     * provided password byte array, allowing applications to overwrite the
     * password after it has been used.
     * <p>
     * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
     *
     * @param password
     *            The password of the user that the client wishes to bind as,
     *            which may be empty.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the password to be set.
     * @throws NullPointerException
     *             If {@code password} was {@code null}.
     */
    GSSAPISASLBindRequest setPassword(byte[] password);

    /**
     * Sets the password of the user that the client wishes to bind as. The
     * password will be converted to a UTF-8 octet string.
     * <p>
     * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
     *
     * @param password
     *            The password of the user that the client wishes to bind as.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the password to be set.
     * @throws NullPointerException
     *             If {@code password} was {@code null}.
     */
    GSSAPISASLBindRequest setPassword(char[] password);

    /**
     * Sets the optional realm containing the user's account.
     * <p>
     * <b>NOTE</b>: this will not be used if a {@code Subject} is specified.
     *
     * @param realm
     *            The name of the realm containing the user's account, which may
     *            be {@code null}.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the realm to be set.
     * @throws NullPointerException
     *             If {@code realm} was {@code null}.
     */
    GSSAPISASLBindRequest setRealm(String realm);

    /**
     * Specifies whether or not the server must authenticate to the client. The
     * default is {@code false}.
     *
     * @param serverAuth
     *            {@code true} if the server must authenticate to the client or
     *            {@code false} otherwise.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit server auth to be set.
     */
    GSSAPISASLBindRequest setServerAuth(boolean serverAuth);

    /**
     * Sets the Kerberos subject of the user to be authenticated.
     * <p>
     * <b>NOTE</b>: if a {@code Subject} is specified then the authentication
     * ID, KDC address, password, and realm, will be ignored.
     *
     * @param subject
     *            The Kerberos subject of the user to be authenticated.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the Kerberos subject to
     *             be set.
     * @throws NullPointerException
     *             If {@code subject} was {@code null}.
     */
    GSSAPISASLBindRequest setSubject(Subject subject);
}
