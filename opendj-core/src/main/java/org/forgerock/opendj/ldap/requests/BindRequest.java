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
 *      Copyright 2009 Sun Microsystems, Inc.
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
 * The Bind operation allows authentication information to be exchanged between
 * the client and server. The Bind operation should be thought of as the
 * "authenticate" operation.
 */
public interface BindRequest extends Request {

    /**
     * The authentication type value (0x80) reserved for simple authentication.
     */
    byte AUTHENTICATION_TYPE_SIMPLE = (byte) 0x80;

    /**
     * The authentication type value (0xA3) reserved for SASL authentication.
     */
    byte AUTHENTICATION_TYPE_SASL = (byte) 0xA3;


    @Override
    BindRequest addControl(Control control);

    /**
     * Creates a new bind client which can be used to perform the authentication
     * process. This method is called by protocol implementations and is not
     * intended for use by applications.
     *
     * @param serverName
     *            The non-null fully-qualified host name of the server to
     *            authenticate to.
     * @return The new bind client.
     * @throws LdapException
     *             If an error occurred while creating the bind client context.
     */
    BindClient createBindClient(String serverName) throws LdapException;

    /**
     * Returns the authentication mechanism identifier for this generic bind
     * request as defined by the LDAP protocol. Note that the value
     * {@link #AUTHENTICATION_TYPE_SIMPLE} ({@code 0x80}) is reserved for simple
     * authentication and the value {@link #AUTHENTICATION_TYPE_SASL} (
     * {@code 0xA3}) is reserved for SASL authentication.
     *
     * @return The authentication mechanism identifier.
     */
    byte getAuthenticationType();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the name of the Directory object that the client wishes to bind
     * as. The name may be empty (but never {@code null}) when used for
     * anonymous binds, or when using SASL authentication. The server shall not
     * dereference any aliases in locating the named object.
     * <p>
     * The LDAP protocol defines the Bind name to be a distinguished name,
     * however some LDAP implementations have relaxed this constraint and allow
     * other identities to be used, such as the user's email address.
     *
     * @return The name of the Directory object that the client wishes to bind
     *         as.
     */
    String getName();

}
