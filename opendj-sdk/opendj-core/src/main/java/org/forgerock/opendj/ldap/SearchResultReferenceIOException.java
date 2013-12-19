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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.io.IOException;

import org.forgerock.opendj.ldap.responses.SearchResultReference;

import org.forgerock.util.Reject;

/**
 * Thrown when an iteration over a set of search results using a
 * {@code ConnectionEntryReader} encounters a {@code SearchResultReference}.
 */
@SuppressWarnings("serial")
public final class SearchResultReferenceIOException extends IOException {
    private final SearchResultReference reference;

    /**
     * Creates a new referral result IO exception with the provided
     * {@code SearchResultReference}.
     *
     * @param reference
     *            The {@code SearchResultReference} which may be later retrieved
     *            by the {@link #getReference} method.
     * @throws NullPointerException
     *             If {@code reference} was {@code null}.
     */
    public SearchResultReferenceIOException(final SearchResultReference reference) {
        super(Reject.checkNotNull(reference).toString());
        this.reference = reference;
    }

    /**
     * Returns the {@code SearchResultReference} which was encountered while
     * processing the search results.
     *
     * @return The {@code SearchResultReference} which was encountered while
     *         processing the search results.
     */
    public SearchResultReference getReference() {
        return reference;
    }
}
