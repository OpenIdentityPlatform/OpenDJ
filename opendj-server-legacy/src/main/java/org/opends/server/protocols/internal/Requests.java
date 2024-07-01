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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 */

package org.opends.server.protocols.internal;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Reject;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.SearchFilter;

/**
 * This class contains various methods for creating and manipulating requests.
 * <p>
 * All copy constructors of the form {@code copyOfXXXRequest} perform deep
 * copies of their request parameter. More specifically, any controls,
 * modifications, and attributes contained within the response will be
 * duplicated.
 * <p>
 * Similarly, all unmodifiable views of request returned by methods of the form
 * {@code unmodifiableXXXRequest} return deep unmodifiable views of their
 * request parameter. More specifically, any controls, modifications, and
 * attributes contained within the returned request will be unmodifiable.
 *
 * @see org.forgerock.opendj.ldap.requests.Requests
 */
public final class Requests {

    // TODO: search request from LDAP URL.

    // TODO: update request from persistent search result.

    // TODO: synchronized requests?

    /**
     * Creates a new search request using the provided distinguished name,
     * scope, and filter.
     *
     * @param name
     *            The distinguished name of the base entry relative to which the
     *            search is to be performed.
     * @param scope
     *            The scope of the search.
     * @param filter
     *            The filter that defines the conditions that must be fulfilled
     *            in order for an entry to be returned.
     * @param attributeDescriptions
     *            The names of the attributes to be included with each entry.
     * @return The new search request.
     * @throws NullPointerException
     *             If the {@code name}, {@code scope}, or {@code filter} were
     *             {@code null}.
     */
    public static SearchRequest newSearchRequest(final DN name, final SearchScope scope,
            final SearchFilter filter, final String... attributeDescriptions)
            throws NullPointerException {
        Reject.ifNull(name, scope, filter);
        final SearchRequest request = new SearchRequest(name, scope, filter);
        for (final String attributeDescription : attributeDescriptions) {
            request.addAttribute(attributeDescription);
        }
        return request;
    }

    /**
     * Return a new search request object.
     *
     * @param name
     *          the dn
     * @param scope
     *          the search scope
     * @param filter
     *          the search filter
     * @return a new search request object
     * @throws DirectoryException
     *           if a problem occurs
     * @see #newSearchRequest(DN, SearchScope, SearchFilter, String...)
     */
    public static SearchRequest newSearchRequest(final String name, final SearchScope scope, final String filter)
            throws DirectoryException {
        return newSearchRequest(DN.valueOf(name), scope, SearchFilter.createFilterFromString(filter));
    }

    /**
     * Return a new search request object.
     *
     * @param name
     *          the dn
     * @param scope
     *          the search scope
     * @param filter
     *          the search filter
     * @return a new search request object
     * @throws DirectoryException
     *           if a problem occurs
     * @see #newSearchRequest(DN, SearchScope, SearchFilter, String...)
     */
    public static SearchRequest newSearchRequest(final DN name, final SearchScope scope, final String filter)
            throws DirectoryException {
        return newSearchRequest(name, scope, SearchFilter.createFilterFromString(filter));
    }

    /**
     * Return a new search request object.
     *
     * @param name
     *          the dn
     * @param scope
     *          the search scope
     * @return a new search request object
     * @see #newSearchRequest(DN, SearchScope, SearchFilter, String...)
     */
    public static SearchRequest newSearchRequest(final DN name, final SearchScope scope) {
        return newSearchRequest(name, scope, SearchFilter.objectClassPresent());
    }

    private Requests() {
        // Prevent instantiation.
    }
}
