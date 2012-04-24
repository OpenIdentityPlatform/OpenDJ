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
 *      Copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

/**
 * The context associated with a request currently being processed by a request
 * handler. A request context can be used to query state information about the
 * request, such as whether or not it has been cancelled, as well as registering
 * to be notified if the request has been cancelled. Implementations may provide
 * additional information, such as the associated schema, time-stamp
 * information, etc.
 */
public interface RequestContext {

    /**
     * Registers the provided cancellation listener with this request context so
     * that it can be notified if a cancellation request is received and
     * processing of the request should be aborted if possible.
     * <p>
     * Requests may be cancelled as a result of an abandon request or a cancel
     * extended request sent from the client, or by the server itself (e.g.
     * during server shutdown).
     * <p>
     * This method provides a event notification mechanism which can be used by
     * asynchronous request handler implementations to detect cancellation of
     * requests.
     *
     * @param listener
     *            The listener which wants to be notified if a cancellation
     *            request is received and processing of the request should be
     *            aborted if possible.
     * @throws NullPointerException
     *             If the {@code listener} was {@code null}.
     * @see #checkIfCancelled
     */
    void addCancelRequestListener(CancelRequestListener listener);

    /**
     * Throws {@link CancelledResultException} if a cancellation request has
     * been received and processing of the request should be aborted if
     * possible.
     * <p>
     * Requests may be cancelled as a result of an abandon request or a cancel
     * extended request sent from the client, or by the server itself (e.g.
     * during server shutdown).
     * <p>
     * This method provides a polling mechanism which can be used by synchronous
     * request handler implementations to detect cancellation of requests.
     *
     * @param signalTooLate
     *            {@code true} to signal that, after this method returns,
     *            processing of the request will have proceeded too far for it
     *            to be aborted by subsequent cancellation requests.
     * @throws CancelledResultException
     *             If a cancellation request has been received and processing of
     *             the request should be aborted if possible.
     * @see #addCancelRequestListener
     */
    void checkIfCancelled(boolean signalTooLate) throws CancelledResultException;

    /**
     * Returns the message ID of the request, if available. Protocols, such as
     * LDAP and internal connections, include a unique message ID with each
     * request which may be useful for logging and auditing.
     *
     * @return The message ID of the request, or {@code -1} if there is no
     *         message ID associated with the request.
     */
    int getMessageID();

    /**
     * Removes the provided cancellation listener from this request context so
     * that it will not be notified if a cancellation request has been received.
     *
     * @param listener
     *            The listener which no longer wants to be notified if a
     *            cancellation request has been received.
     * @throws NullPointerException
     *             If the {@code listener} was {@code null}.
     */
    void removeCancelRequestListener(CancelRequestListener listener);
}
