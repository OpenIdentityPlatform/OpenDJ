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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.EventListener;

import org.forgerock.opendj.ldap.responses.ExtendedResult;

/**
 * An object that registers to be notified when a connection is closed by the
 * application, receives an unsolicited notification, or experiences a fatal
 * error.
 */
public interface ConnectionEventListener extends EventListener {
    /**
     * Notifies this connection event listener that the application has called
     * {@code close} on the connection. The connection event listener will be
     * notified immediately after the application calls the {@code close} method
     * on the associated connection.
     */
    void handleConnectionClosed();

    /**
     * Notifies this connection event listener that a fatal error has occurred
     * and the connection can no longer be used - the server has crashed, for
     * example. The connection implementation makes this notification just
     * before it throws the provided {@link LdapException} to the
     * application.
     * <p>
     * <b>Note:</b> disconnect notifications are treated as fatal connection
     * errors and are handled by this method. In this case
     * {@code isDisconnectNotification} will be {@code true} and {@code error}
     * will contain the result code and any diagnostic information contained in
     * the notification message.
     *
     * @param isDisconnectNotification
     *            {@code true} if the error was triggered by a disconnect
     *            notification sent by the server, otherwise {@code false}.
     * @param error
     *            The exception that is about to be thrown to the application.
     */
    void handleConnectionError(boolean isDisconnectNotification, LdapException error);

    /**
     * Notifies this connection event listener that the connection has just
     * received the provided unsolicited notification from the server.
     * <p>
     * <b>Note:</b> disconnect notifications are treated as fatal connection
     * errors and are handled by the {@link #handleConnectionError} method.
     *
     * @param notification
     *            The unsolicited notification.
     */
    void handleUnsolicitedNotification(ExtendedResult notification);
}
