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
 *      Portions copyright 2011-2014 ForgeRock AS
 */

package org.opends.server.protocols.internal;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Reject;
import org.opends.server.types.DN;
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
     * Creates a new search request using the provided distinguished name,
     * scope, and filter, decoded using the default schema.
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
     * @throws  DirectoryException
     *             If a problem occurs while decoding the provided string as a
     *             search filter.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} could not be decoded using the default
     *             schema, or if {@code filter} is not a valid LDAP string
     *             representation of a filter.
     * @throws NullPointerException
     *             If the {@code name}, {@code scope}, or {@code filter} were
     *             {@code null}.
     */
    public static SearchRequest newSearchRequest(final String name, final SearchScope scope,
            final String filter, final String... attributeDescriptions)
            throws NullPointerException, LocalizedIllegalArgumentException, DirectoryException {
        Reject.ifNull(name, scope, filter);
        SearchFilter f = "(objectclass=*)".equals(filter.toLowerCase())
            ? SearchFilter.objectClassPresent()
            : SearchFilter.createFilterFromString(filter);
        final SearchRequest request = new SearchRequest(DN.valueOf(name), scope, f);
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
