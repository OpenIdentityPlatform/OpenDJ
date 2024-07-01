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
 * Portions copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

/**
 * A completion handler for consuming the results of a Search operation.
 * <p>
 * {@link Connection} and {@link Connection} objects allow a search result
 * completion handler to be specified when sending Search operation requests to
 * a Directory Server. The {@link #handleEntry} method is invoked each time a
 * Search Result Entry is returned from the Directory Server. The
 * {@link #handleReference} method is invoked for each Search Result Reference
 * returned from the Directory Server.
 * <p>
 * Implementations of these methods should complete in a timely manner so as to
 * avoid keeping the invoking thread from dispatching to other completion
 * handlers.
 */
public interface SearchResultHandler {
    /**
     * Invoked each time a search result entry is returned from an asynchronous
     * search operation.
     *
     * @param entry
     *            The search result entry.
     * @return {@code true} if this handler should continue to be notified of
     *         any remaining entries and references, or {@code false} if the
     *         remaining entries and references should be skipped for some
     *         reason (e.g. a client side size limit has been reached).
     */
    boolean handleEntry(SearchResultEntry entry);

    /**
     * Invoked each time a search result reference is returned from an
     * asynchronous search operation.
     *
     * @param reference
     *            The search result reference.
     * @return {@code true} if this handler should continue to be notified of
     *         any remaining entries and references, or {@code false} if the
     *         remaining entries and references should be skipped for some
     *         reason (e.g. a client side size limit has been reached).
     */
    boolean handleReference(SearchResultReference reference);
}
