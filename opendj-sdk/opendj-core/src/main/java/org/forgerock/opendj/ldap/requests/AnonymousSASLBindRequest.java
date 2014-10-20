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

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * The anonymous SASL bind request as defined in RFC 4505. This SASL mechanism
 * allows a client to authenticate to the server without requiring the user to
 * establish or otherwise disclose their identity to the server. That is, this
 * mechanism provides an anonymous login method. This mechanism does not provide
 * a security layer.
 * <p>
 * Clients should provide trace information, which has no semantic value, and
 * can be used by administrators in order to identify the user. It should take
 * one of two forms: an Internet email address, or an opaque string that does
 * not contain the '@' (U+0040) character and that can be interpreted by the
 * system administrator of the client's domain. For privacy reasons, an Internet
 * email address or other information identifying the user should only be used
 * with permission from the user.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4505">RFC 4505 - Anonymous Simple
 *      Authentication and Security Layer (SASL) Mechanism </a>
 */
public interface AnonymousSASLBindRequest extends SASLBindRequest {

    /**
     * The name of the SASL mechanism that does not provide any authentication
     * but rather uses anonymous access.
     */
    String SASL_MECHANISM_NAME = "ANONYMOUS";

    @Override
    AnonymousSASLBindRequest addControl(Control control);

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
     * Returns the trace information, which has no semantic value, and can be
     * used by administrators in order to identify the user.
     *
     * @return The trace information, which has no semantic value, and can be
     *         used by administrators in order to identify the user.
     */
    String getTraceString();

    /**
     * Sets the trace information, which has no semantic value, and can be used
     * by administrators in order to identify the user.
     *
     * @param traceString
     *            The trace information, which has no semantic value, and can be
     *            used by administrators in order to identify the user.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this anonymous SASL request does not permit the trace
     *             information to be set.
     * @throws NullPointerException
     *             If {@code traceString} was {@code null}.
     */
    AnonymousSASLBindRequest setTraceString(String traceString);
}
