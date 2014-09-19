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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import org.forgerock.util.promise.PromiseImpl;

/**
 * This class provides an implementation of the {@code FutureResult}.
 *
 * @param <R>
 *            The type of result returned by this future.
 * @see Promise
 * @see Promises
 */
public class FutureResultImpl<R> extends PromiseImpl<R, LdapException>
    implements FutureResult<R>, ResultHandler<R> {
    private final int requestID;

    /**
     * Creates a new future result with a request ID of -1.
     */
    public FutureResultImpl() {
        this(-1);
    }

    /**
     * Creates a future result with the provided request ID.
     *
     * @param requestID
     *            The request ID which will be returned by the default
     *            implementation of {@link #getRequestID}.
     */
    public FutureResultImpl(int requestID) {
        this.requestID = requestID;
    }

    @Override
    public int getRequestID() {
        return requestID;
    }
}
