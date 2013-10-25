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
 *      Copyright 2013 ForgeRock AS.
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
     *            time to use as current time for any check
     * @return the delay to wait before next timeout callback in milliseconds
     */
    long handleTimeout(final long currentTime);

    /**
     * Returns the timeout for this listener.
     *
     * @return the timeout in milliseconds
     */
    long getTimeout();

}
