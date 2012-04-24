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
 *      Copyright 2011 ForgeRock AS
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
