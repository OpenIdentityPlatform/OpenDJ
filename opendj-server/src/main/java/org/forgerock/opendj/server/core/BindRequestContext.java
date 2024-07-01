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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.server.core;

import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.requests.BindRequest;

/**
 * The bind request context allows for updating the authentication state of the
 * connection.
 */
public interface BindRequestContext extends Operation {

    /**
     * Specifies information about the authentication that has been performed
     * for this connection.
     *
     * @param bindRequest
     *            The bind request the client used for authentication.
     * @param authenticationEntry
     *            The entry for the user as whom the client is authenticated, or
     *            {@code null} if the client is unauthenticated.
     * @param authorizationEntry
     *            The entry for the user that should be considered the
     *            authorization identity for this client, or {@code null} if it
     *            should be the unauthenticated user.
     */
    void setAuthenticationInfo(BindRequest bindRequest, Entry authenticationEntry,
            Entry authorizationEntry);
}
