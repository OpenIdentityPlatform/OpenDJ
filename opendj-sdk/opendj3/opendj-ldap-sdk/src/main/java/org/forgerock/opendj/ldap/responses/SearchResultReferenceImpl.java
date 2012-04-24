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

package org.forgerock.opendj.ldap.responses;

import java.util.LinkedList;
import java.util.List;

import com.forgerock.opendj.util.Validator;

/**
 * Search result reference implementation.
 */
final class SearchResultReferenceImpl extends AbstractResponseImpl<SearchResultReference> implements
        SearchResultReference {

    private final List<String> uris = new LinkedList<String>();

    /**
     * Creates a new search result reference using the provided continuation
     * reference URI.
     *
     * @param uri
     *            The first continuation reference URI to be added to this
     *            search result reference.
     * @throws NullPointerException
     *             If {@code uri} was {@code null}.
     */
    SearchResultReferenceImpl(final String uri) {
        addURI(uri);
    }

    /**
     * Creates a new search result reference that is an exact copy of the
     * provided result.
     *
     * @param searchResultReference
     *            The search result reference to be copied.
     * @throws NullPointerException
     *             If {@code searchResultReference} was {@code null} .
     */
    SearchResultReferenceImpl(final SearchResultReference searchResultReference) {
        super(searchResultReference);
        this.uris.addAll(searchResultReference.getURIs());
    }

    /**
     * {@inheritDoc}
     */
    public SearchResultReference addURI(final String uri) {
        Validator.ensureNotNull(uri);
        uris.add(uri);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getURIs() {
        return uris;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("SearchResultReference(uris=");
        builder.append(getURIs());
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

    @Override
    SearchResultReference getThis() {
        return this;
    }

}
