/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.server.core;

import java.net.InetSocketAddress;
import java.util.Collection;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.requests.BindRequest;

/**
 * The context in which a request is to be processed.
 * <p>
 * Implementations may query the context in order to:
 * <ul>
 * <li>query the schema associated with the request (attribute types, decode
 * DNs, etc)
 * <li>perform internal operations
 * <li>query information regarding client performing the request
 * </ul>
 * Context implementations take care of correctly routing internal requests.
 * <p>
 * In addition, the context acts as a transaction manager, coordinating any
 * resources accessed during the processing of a request and any subsequent
 * requests forming part of the same logical transaction.
 * <p>
 * FiXME: this interface should be split up into sub-components, such as network
 * information (protocol, addresses), client information (auth ID, SSF,
 * privileges).
 */
public interface Operation extends RequestContext, AttachmentHolder {
    /**
     * Retrieves the entry for the user that should be considered the
     * authorization identity for this operation. In many cases, it will be the
     * same as the authorization entry for the underlying client connection, or
     * {@code null} if no authentication has been performed on that connection.
     * However, it may be some other value if special processing has been
     * requested (e.g., the operation included a proxied authorization control).
     *
     * @return The entry for the user that should be considered the
     *         authorization identity for this operation, or {@code null} if the
     *         authorization identity should be the unauthenticated user.
     */
    Entry getAuthorizationEntry();

    /**
     * Returns a connection for performing internal operations.
     *
     * @return A connection for performing internal operations.
     */
    Connection getConnection();

    /**
     * Retrieves the operation ID for this operation.
     *
     * @return The operation ID for this operation.
     */
    long getOperationID();

    /**
     * Indicates whether the authenticate client has all of the specified
     * privileges.
     *
     * @param privileges
     *            The array of privileges for which to make the determination.
     * @return {@code true} if the authenticated client has all of the specified
     *         privileges, or {@code false} if not.
     */
    boolean hasAllPrivileges(Collection<Privilege> privileges);

    /**
     * Indicates whether the authenticated client has the specified privilege.
     *
     * @param privilege
     *            The privilege for which to make the determination.
     * @return {@code true} if the authenticated client has the specified
     *         privilege, or {@code false} if not.
     */
    boolean hasPrivilege(Privilege privilege);

    /**
     * Sets the entry for the user that should be considered the authorization
     * identity for this operation.
     *
     * @param authorizationEntry
     *            The entry for the user that should be considered the
     *            authorization identity for this operation, or {@code null} if
     *            it should be the unauthenticated user.
     */
    void setAuthorizationEntry(Entry authorizationEntry);

    /**
     * Retrieves the entry for the user as whom the client is authenticated.
     *
     * @return The entry for the user as whom the client is authenticated, or
     *         {@code null} if the client is unauthenticated.
     */
    Entry getAuthenticationEntry();

    /**
     * Retrieves the last successful bind request from the client.
     *
     * @return The last successful bind request or {@code null} if the client
     *         have not yet successfully bind.
     */
    BindRequest getBindRequest();

    /**
     * Retrieves the unique identifier that is assigned to the client connection
     * that submitted this operation.
     *
     * @return The unique identifier that is assigned to the client connection
     *         that submitted this operation.
     */
    long getConnectionID();

    /**
     * Returns the {@code InetSocketAddress} associated with the local system.
     *
     * @return The {@code InetSocketAddress} associated with the local system.
     */
    InetSocketAddress getLocalAddress();

    /**
     * Retrieves the default maximum number of entries that should checked for
     * matches during a search.
     *
     * @return The default maximum number of entries that should checked for
     *         matches during a search.
     */
    int getLookthroughLimit();

    /**
     * Returns the {@code InetSocketAddress} associated with the remote system.
     *
     * @return The {@code InetSocketAddress} associated with the remote system.
     */
    InetSocketAddress getPeerAddress();

    /**
     * Retrieves the protocol that the client is using to communicate with the
     * Directory Server.
     *
     * @return The protocol that the client is using to communicate with the
     *         Directory Server.
     */
    String getProtocol();

    /**
     * Returns the strongest cipher strength currently in use by the underlying
     * connection.
     *
     * @return The strongest cipher strength currently in use by the underlying
     *         connection.
     */
    int getSecurityStrengthFactor();

    /**
     * Retrieves the size limit that will be enforced for searches performed
     * using this client connection.
     *
     * @return The size limit that will be enforced for searches performed using
     *         this client connection.
     */
    int getSizeLimit();

    /**
     * Retrieves the time limit that will be enforced for searches performed
     * using this client connection.
     *
     * @return The time limit that will be enforced for searches performed using
     *         this client connection.
     */
    int getTimeLimit();
}
