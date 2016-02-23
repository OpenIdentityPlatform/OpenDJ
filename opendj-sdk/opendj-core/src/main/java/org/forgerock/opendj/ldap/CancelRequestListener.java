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
 * Copyright 2011 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.EventListener;

import org.forgerock.i18n.LocalizableMessage;

/**
 * An object that registers to be notified when a cancellation request has been
 * received and processing of the request should be aborted if possible.
 * <p>
 * Requests may be cancelled as a result of an abandon request or a cancel
 * extended request sent from the client, or by the server itself (e.g. during
 * server shutdown).
 */
public interface CancelRequestListener extends EventListener {
    /**
     * Invoked when a cancellation request has been received and processing of
     * the request should be aborted if possible.
     * <p>
     * Requests may be cancelled as a result of an abandon request or a cancel
     * extended request sent from the client, or by the server itself (e.g.
     * during server shutdown).
     * <p>
     * Implementations should, if possible, abort further processing of the
     * request and return an appropriate cancellation result.
     *
     * @param cancellationReason
     *            A message describing the reason why the request is being
     *            cancelled.
     */
    void handleCancelRequest(LocalizableMessage cancellationReason);
}
