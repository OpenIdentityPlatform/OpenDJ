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
 * Copyright 2013-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

/**
 * Listener on timeout events.
 * <p>
 * The listener must register itself with a {@code TimeoutChecker} using
 * {@code TimeoutChecker#addListener()} method to be called back with
 * {@code #handleTimeout} method.
 * <p>
 * The listener must deregister itself using
 * {@code TimeoutChecker#removeListener()} to stop being called back.
 */
public interface TimeoutEventListener {

    /**
     * Handle a timeout event.
     *
     * @param currentTime
     *            Time to use as current time for any check.
     * @return The delay to wait before next timeout callback in milliseconds,
     *         or zero if this listener should no longer be notified.
     */
    long handleTimeout(long currentTime);

    /**
     * Returns the timeout for this listener.
     *
     * @return The timeout in milliseconds.
     */
    long getTimeout();

}
