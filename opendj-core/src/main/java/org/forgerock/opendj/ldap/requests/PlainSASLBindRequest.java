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

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * The Plain SASL bind request as defined in RFC 4616. This SASL mechanism
 * allows a client to authenticate to the server with an authentication ID and
 * password. This mechanism does not provide a security layer.
 * <p>
 * The authentication and optional authorization identity is specified using an
 * authorization ID, or {@code authzId}, as defined in RFC 4513 section 5.2.1.8.
 *
 * <pre>
 * String authcid = ...;        // Authentication ID, e.g. dn:&lt;dn>, u:&lt;uid>
 * String authzid = ...;        // Authorization ID, e.g. dn:&lt;dn>, u:&lt;uid>
 * char[] password = ...;
 * Connection connection = ...; // Use StartTLS to protect the request
 *
 * PlainSASLBindRequest request =
 *         Requests.newPlainSASLBindRequest(authcid, password)
 *         .setAuthorizationID(authzid);
 *
 * connection.bind(request);
 * // Authenticated if the connection succeeds
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4616">RFC 4616 - The PLAIN Simple
 *      Authentication and Security Layer (SASL) Mechanism </a>
 * @see <a href="http://tools.ietf.org/html/rfc4513#section-5.2.1.8">RFC 4513 -
 *      SASL Authorization Identities (authzId) </a>
 */
public interface PlainSASLBindRequest extends SASLBindRequest {

    /**
     * The name of the SASL mechanism based on PLAIN authentication.
     */
    String SASL_MECHANISM_NAME = "PLAIN";

    @Override
    PlainSASLBindRequest addControl(Control control);

    @Override
    BindClient createBindClient(String serverName) throws LdapException;

    /**
     * Returns the authentication ID of the user. The authentication ID usually
     * has the form "dn:" immediately followed by the distinguished name of the
     * user, or "u:" followed by a user ID string, but other forms are
     * permitted.
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
     *
     * @return The password of the user that the client wishes to bind as.
     */
    byte[] getPassword();

    @Override
    String getSASLMechanism();

    /**
     * Sets the authentication ID of the user. The authentication ID usually has
     * the form "dn:" immediately followed by the distinguished name of the
     * user, or "u:" followed by a user ID string, but other forms are
     * permitted.
     *
     * @param authenticationID
     *            The authentication ID of the user.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the authentication ID to
     *             be set.
     * @throws LocalizedIllegalArgumentException
     *             If {@code authenticationID} was non-empty and did not contain
     *             a valid authorization ID type.
     * @throws NullPointerException
     *             If {@code authenticationID} was {@code null}.
     */
    PlainSASLBindRequest setAuthenticationID(String authenticationID);

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
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the authorization ID to
     *             be set.
     * @throws LocalizedIllegalArgumentException
     *             If {@code authorizationID} was non-empty and did not contain
     *             a valid authorization ID type.
     */
    PlainSASLBindRequest setAuthorizationID(String authorizationID);

    /**
     * Sets the password of the user that the client wishes to bind as.
     * <p>
     * Unless otherwise indicated, implementations will store a reference to the
     * provided password byte array, allowing applications to overwrite the
     * password after it has been used.
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
    PlainSASLBindRequest setPassword(byte[] password);

    /**
     * Sets the password of the user that the client wishes to bind as. The
     * password will be converted to a UTF-8 octet string.
     *
     * @param password
     *            The password of the user that the client wishes to bind as.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the password to be set.
     * @throws NullPointerException
     *             If {@code password} was {@code null}.
     */
    PlainSASLBindRequest setPassword(char[] password);
}
