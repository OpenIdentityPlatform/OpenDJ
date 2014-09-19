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

package org.forgerock.opendj.ldap;

/**
 * An interface for providing additional connection security to a connection.
 */
public interface ConnectionSecurityLayer {

    /**
     * Disposes of any system resources or security-sensitive information that
     * this connection security layer might be using. Invoking this method
     * invalidates this instance.
     */
    void dispose();

    /**
     * Unwraps a byte array received from the peer.
     *
     * @param incoming
     *            A non-{@code null} byte array containing the encoded bytes
     *            from the peer.
     * @param offset
     *            The starting position in {@code incoming} of the bytes to be
     *            unwrapped.
     * @param len
     *            The number of bytes from {@code incoming} to be unwrapped.
     * @return A non-{@code null} byte array containing the unwrapped bytes.
     * @throws LdapException
     *             If {@code incoming} cannot be successfully unwrapped.
     */
    byte[] unwrap(byte[] incoming, int offset, int len) throws LdapException;

    /**
     * Wraps a byte array to be sent to the peer.
     *
     * @param outgoing
     *            A non-{@code null} byte array containing the unencoded bytes
     *            to be sent to the peer.
     * @param offset
     *            The starting position in {@code outgoing} of the bytes to be
     *            wrapped.
     * @param len
     *            The number of bytes from {@code outgoing} to be wrapped.
     * @return A non-{@code null} byte array containing the wrapped bytes.
     * @throws LdapException
     *             If {@code outgoing} cannot be successfully wrapped.
     */
    byte[] wrap(byte[] outgoing, int offset, int len) throws LdapException;
}
