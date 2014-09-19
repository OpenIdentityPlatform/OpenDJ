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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import java.util.List;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * A generic Bind request which should be used for unsupported authentication
 * methods. Servers that do not support a choice supplied by a client return a
 * Bind response with the result code set to
 * {@link ResultCode#AUTH_METHOD_NOT_SUPPORTED}.
 */
public interface GenericBindRequest extends BindRequest {

    @Override
    GenericBindRequest addControl(Control control);

    @Override
    BindClient createBindClient(String serverName) throws LdapException;

    @Override
    byte getAuthenticationType();

    /**
     * Returns the authentication information for this bind request. The content
     * is defined by the authentication mechanism.
     * <p>
     * Unless otherwise indicated, implementations will store a reference to the
     * returned byte array, allowing applications to overwrite any sensitive
     * data such as passwords after it has been used.
     *
     * @return The authentication information.
     */
    byte[] getAuthenticationValue();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    @Override
    String getName();

    /**
     * Sets the authentication mechanism identifier for this generic bind
     * request. Note that value {@code 0} is reserved for simple authentication,
     * {@code 1} and {@code 2} are reserved but unused, and {@code 3} is
     * reserved for SASL authentication.
     *
     * @param type
     *            The authentication mechanism identifier for this generic bind
     *            request.
     * @return This generic bind request.
     * @throws UnsupportedOperationException
     *             If this generic bind request does not permit the
     *             authentication type to be set.
     */
    GenericBindRequest setAuthenticationType(byte type);

    /**
     * Sets the authentication information for this generic bind request in a
     * form defined by the authentication mechanism.
     * <p>
     * Unless otherwise indicated, implementations will store a reference to the
     * returned byte array, allowing applications to overwrite any sensitive
     * data such as passwords after it has been used.
     *
     * @param bytes
     *            The authentication information for this generic bind request
     *            in a form defined by the authentication mechanism.
     * @return This generic bind request.
     * @throws UnsupportedOperationException
     *             If this generic bind request does not permit the
     *             authentication bytes to be set.
     * @throws NullPointerException
     *             If {@code bytes} was {@code null}.
     */
    GenericBindRequest setAuthenticationValue(byte[] bytes);

    /**
     * Sets the name of the Directory object that the client wishes to bind as.
     * The name may be empty (but never {@code null} when used for of anonymous
     * binds, or when using SASL authentication. The server shall not
     * dereference any aliases in locating the named object.
     * <p>
     * The LDAP protocol defines the Bind name to be a distinguished name,
     * however some LDAP implementations have relaxed this constraint and allow
     * other identities to be used, such as the user's email address.
     *
     * @param name
     *            The name of the Directory object that the client wishes to
     *            bind as.
     * @return This bind request.
     * @throws UnsupportedOperationException
     *             If this bind request does not permit the distinguished name
     *             to be set.
     * @throws NullPointerException
     *             If {@code name} was {@code null}.
     */
    GenericBindRequest setName(String name);

}
