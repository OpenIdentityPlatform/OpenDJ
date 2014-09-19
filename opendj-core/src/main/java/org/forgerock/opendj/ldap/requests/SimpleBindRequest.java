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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import java.util.List;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * The simple authentication method of the Bind Operation provides three
 * authentication mechanisms:
 * <ul>
 * <li>An anonymous authentication mechanism, in which both the name and
 * password are zero length.
 * <li>An unauthenticated authentication mechanism using credentials consisting
 * of a name and a zero length password.
 * <li>A name/password authentication mechanism using credentials consisting of
 * a name and a password.
 * </ul>
 * {@link Requests} has methods to create a {@code SimpleBindRequest}.
 *
 * <pre>
 * String bindDN = ...;
 * char[] bindPassword = ...;
 *
 * SimpleBindRequest sbr = Requests.newSimpleBindRequest(bindDN, bindPassword);
 * </pre>
 *
 * Alternatively, use
 * {@link org.forgerock.opendj.ldap.Connection#bind(String, char[])
 * Connection.bind}.
 *
 * <pre>
 * Connection connection;
 * String bindDN = ...;
 * char[] bindPassword = ...;
 *
 * connection.bind(bindDN, bindPassword);
 * </pre>
 */
public interface SimpleBindRequest extends BindRequest {

    @Override
    SimpleBindRequest addControl(Control control);

    @Override
    BindClient createBindClient(String serverName) throws LdapException;

    /**
     * Returns the authentication mechanism identifier for this simple bind
     * request as defined by the LDAP protocol, which is always {@code 0x80}.
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

    @Override
    String getName();

    /**
     * Returns the password of the Directory object that the client wishes to
     * bind as. The password may be empty (but never {@code null}) when used for
     * of anonymous or unauthenticated binds.
     * <p>
     * Unless otherwise indicated, implementations will store a reference to the
     * returned password byte array, allowing applications to overwrite the
     * password after it has been used.
     *
     * @return The password of the Directory object that the client wishes to
     *         bind as.
     */
    byte[] getPassword();

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
    SimpleBindRequest setName(String name);

    /**
     * Sets the password of the Directory object that the client wishes to bind
     * as. The password may be empty (but never {@code null}) when used for of
     * anonymous or unauthenticated binds.
     * <p>
     * Unless otherwise indicated, implementations will store a reference to the
     * provided password byte array, allowing applications to overwrite the
     * password after it has been used.
     *
     * @param password
     *            The password of the Directory object that the client wishes to
     *            bind as, which may be empty.
     * @return This simple bind request.
     * @throws UnsupportedOperationException
     *             If this simple bind request does not permit the password to
     *             be set.
     * @throws NullPointerException
     *             If {@code password} was {@code null}.
     */
    SimpleBindRequest setPassword(byte[] password);

    /**
     * Sets the password of the Directory object that the client wishes to bind
     * as. The password will be converted to a UTF-8 octet string. The password
     * may be empty (but never {@code null}) when used for of anonymous or
     * unauthenticated binds. Subsequent modifications to the {@code password}
     * array will not alter this bind request.
     *
     * @param password
     *            The password of the Directory object that the client wishes to
     *            bind as, which may be empty.
     * @return This simple bind request.
     * @throws UnsupportedOperationException
     *             If this simple bind request does not permit the password to
     *             be set.
     * @throws NullPointerException
     *             If {@code password} was {@code null}.
     */
    SimpleBindRequest setPassword(char[] password);

}
