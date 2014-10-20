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
 * The CRAM-MD5 SASL bind request as defined in draft-ietf-sasl-crammd5. This
 * SASL mechanism allows a client to perform a simple challenge-response
 * authentication method, using a keyed MD5 digest. This mechanism does not
 * provide a security layer.
 * <p>
 * The CRAM-MD5 mechanism is intended to have limited use on the Internet. The
 * mechanism offers inadequate protection against common attacks against
 * application-level protocols and is prone to interoperability problems.
 * <p>
 * The authentication identity is specified using an authorization ID, or
 * {@code authzId}, as defined in RFC 4513 section 5.2.1.8.
 *
 * @see <a
 *      href="http://tools.ietf.org/html/draft-ietf-sasl-crammd5">draft-ietf-sasl-crammd5
 *      - The CRAM-MD5 SASL Mechanism </a>
 * @see <a href="http://tools.ietf.org/html/rfc4513#section-5.2.1.8">RFC 4513 -
 *      SASL Authorization Identities (authzId) </a>
 */
public interface CRAMMD5SASLBindRequest extends SASLBindRequest {

    /**
     * The name of the SASL mechanism based on CRAM-MD5 authentication.
     */
    String SASL_MECHANISM_NAME = "CRAM-MD5";

    @Override
    CRAMMD5SASLBindRequest addControl(Control control);

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
     * @return This bind request
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the authentication ID to
     *             be set..
     * @throws LocalizedIllegalArgumentException
     *             If {@code authenticationID} was non-empty and did not contain
     *             a valid authorization ID type.
     * @throws NullPointerException
     *             If {@code authenticationID} was {@code null}.
     */
    CRAMMD5SASLBindRequest setAuthenticationID(String authenticationID);

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
    CRAMMD5SASLBindRequest setPassword(byte[] password);

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
    CRAMMD5SASLBindRequest setPassword(char[] password);

}
