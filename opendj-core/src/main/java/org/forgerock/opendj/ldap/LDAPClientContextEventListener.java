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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.EventListener;

import org.forgerock.opendj.ldap.requests.UnbindRequest;

/** A listener interface for handling LDAPClientContext state changes. */
public interface LDAPClientContextEventListener extends EventListener {
    /**
     * Invoked when the connection has been disconnected because of an error (e.g: message too big).
     *
     * @param context
     *            The {@link LDAPClientContext} which has failed
     * @param error
     *            The error
     */
    void handleConnectionError(LDAPClientContext context, Throwable error);

    /**
     * Invoked when the client closed the connection, possibly using an unbind request.
     *
     * @param context
     *            The {@link LDAPClientContext} which has been disconnected
     * @param unbindRequest
     *            The unbind request, which may be {@code null} if one was not sent before the connection was
     *            closed.
     */
    void handleConnectionClosed(LDAPClientContext context, UnbindRequest unbindRequest);

    /**
     * Invoked when the connection has been disconnected by the server.
     *
     * @param context
     *            The {@link LDAPClientContext} which has been disconnected
     * @param resultCode
     *            The result code which was included with the disconnect notification, or {@code null} if no
     *            disconnect notification was sent.
     * @param diagnosticMessage
     *            The diagnostic message, which may be empty or {@code null} indicating that none was provided.
     */
    void handleConnectionDisconnected(LDAPClientContext context, ResultCode resultCode, String diagnosticMessage);
}
