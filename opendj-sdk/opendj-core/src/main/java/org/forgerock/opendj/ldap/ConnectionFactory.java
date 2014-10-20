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
 *      Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.io.Closeable;

import org.forgerock.util.promise.Promise;

/**
 * A connection factory provides an interface for obtaining a connection to a
 * Directory Server. Connection factories can be used to wrap other connection
 * factories in order to provide enhanced capabilities in a manner which is
 * transparent to the application. For example:
 * <ul>
 * <li>Connection pooling
 * <li>Load balancing
 * <li>Keep alive
 * <li>Transactional connections
 * <li>Connections to LDIF files
 * <li>Data transformations
 * <li>Logging connections
 * <li>Read-only connections
 * <li>Pre-authenticated connections
 * <li>Recording connections, with primitive roll-back functionality
 * </ul>
 * An application typically obtains a connection from a connection factory,
 * performs one or more operations, and then closes the connection. Applications
 * should aim to close connections as soon as possible in order to avoid
 * resource contention.
 */
public interface ConnectionFactory extends Closeable {

    /**
     * Releases any resources associated with this connection factory. Depending
     * on the implementation a factory may:
     * <ul>
     * <li>do nothing
     * <li>close underlying connection factories (e.g. load-balancers)
     * <li>close pooled connections (e.g. connection pools)
     * <li>shutdown IO event service and related thread pools (e.g. Grizzly).
     * </ul>
     * Calling {@code close} on a connection factory which is already closed has
     * no effect.
     * <p>
     * Applications should avoid closing connection factories while there are
     * remaining active connections in use or connection attempts in progress.
     *
     * @see Connections#uncloseable(ConnectionFactory)
     */
    @Override
    void close();

    /**
     * Asynchronously obtains a connection to the Directory Server associated
     * with this connection factory. The returned {@code Promise} can be used to
     * retrieve the completed connection.
     *
     * @return A promise which can be used to retrieve the connection.
     */
    Promise<Connection, LdapException> getConnectionAsync();

    /**
     * Returns a connection to the Directory Server associated with this
     * connection factory. The connection returned by this method can be used
     * immediately.
     * <p>
     * If the calling thread is interrupted while waiting for the connection
     * attempt to complete then the calling thread unblock and throw a
     * {@link CancelledResultException} whose cause is the underlying
     * {@link InterruptedException}.
     *
     * @return A connection to the Directory Server associated with this
     *         connection factory.
     * @throws LdapException
     *             If the connection request failed for some reason.
     */
    Connection getConnection() throws LdapException;
}
