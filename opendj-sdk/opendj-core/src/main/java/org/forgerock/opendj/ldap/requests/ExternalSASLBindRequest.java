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
 *      Portions Copyright 2012-2014 ForgeRock AS.
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
 * The External SASL bind request as defined in RFC 4422. This SASL mechanism
 * allows a client to request the server to use credentials established by means
 * external to the mechanism to authenticate the client. The external means may
 * be, for instance, SSL or TLS.
 * <p>
 * A client may either request that its authorization identity be automatically
 * derived from its authentication credentials exchanged at a lower security
 * layer, or it may explicitly provide a desired authorization identity.
 * <p>
 * The optional authorization identity is specified using an authorization ID,
 * or {@code authzId}, as defined in RFC 4513 section 5.2.1.8.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4422">RFC 4422 - Simple
 *      Authentication and Security Layer (SASL) </a>
 * @see <a href="http://tools.ietf.org/html/rfc4513#section-5.2.1.8">RFC 4513 -
 *      SASL Authorization Identities (authzId) </a>
 */
public interface ExternalSASLBindRequest extends SASLBindRequest {

    /**
     * The name of the SASL mechanism based on external authentication.
     */
    String SASL_MECHANISM_NAME = "EXTERNAL";

    @Override
    ExternalSASLBindRequest addControl(Control control);

    @Override
    BindClient createBindClient(String serverName) throws LdapException;

    /**
     * Returns the authentication mechanism identifier for this SASL bind
     * request as defined by the LDAP protocol, which is always {@code 0xA3}.
     *
     * @return The authentication mechanism identifier.
     */
    @Override
    byte getAuthenticationType();

    /**
     * Returns the optional desired authorization ID of the user, or
     * {@code null} if the authorization ID should derived from authentication
     * credentials exchanged at a lower security layer. The authorization ID
     * usually has the form "dn:" immediately followed by the distinguished name
     * of the user, or "u:" followed by a user ID string, but other forms are
     * permitted.
     *
     * @return The desired authorization ID of the user, which may be
     *         {@code null} .
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

    @Override
    String getSASLMechanism();

    /**
     * Sets the optional desired authorization ID of the user, or {@code null}
     * if the authorization ID should derived from authentication credentials
     * exchanged at a lower security layer. The authorization ID usually has the
     * form "dn:" immediately followed by the distinguished name of the user, or
     * "u:" followed by a user ID string, but other forms are permitted.
     *
     * @param authorizationID
     *            The desired authorization ID of the user, which may be
     *            {@code null}.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this external SASL request does not permit the
     *             authorization ID to be set.
     * @throws LocalizedIllegalArgumentException
     *             If {@code authorizationID} was non-empty and did not contain
     *             a valid authorization ID type.
     */
    ExternalSASLBindRequest setAuthorizationID(String authorizationID);
}
