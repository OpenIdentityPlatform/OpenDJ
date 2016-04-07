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
 * Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;

/**
 * Unmodifiable search request implementation.
 */
final class UnmodifiableSearchRequestImpl extends AbstractUnmodifiableRequest<SearchRequest>
        implements SearchRequest {
    UnmodifiableSearchRequestImpl(final SearchRequest impl) {
        super(impl);
    }

    @Override
    public SearchRequest addAttribute(final String... attributeDescriptions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getAttributes() {
        return Collections.unmodifiableList(impl.getAttributes());
    }

    @Override
    public DereferenceAliasesPolicy getDereferenceAliasesPolicy() {
        return impl.getDereferenceAliasesPolicy();
    }

    @Override
    public Filter getFilter() {
        return impl.getFilter();
    }

    @Override
    public DN getName() {
        return impl.getName();
    }

    @Override
    public SearchScope getScope() {
        return impl.getScope();
    }

    @Override
    public int getSizeLimit() {
        return impl.getSizeLimit();
    }

    @Override
    public boolean isSingleEntrySearch() {
        return impl.isSingleEntrySearch();
    }

    @Override
    public int getTimeLimit() {
        return impl.getTimeLimit();
    }

    @Override
    public boolean isTypesOnly() {
        return impl.isTypesOnly();
    }

    @Override
    public SearchRequest setDereferenceAliasesPolicy(final DereferenceAliasesPolicy policy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchRequest setFilter(final Filter filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchRequest setFilter(final String filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchRequest setName(final DN dn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchRequest setName(final String dn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchRequest setScope(final SearchScope scope) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchRequest setSizeLimit(final int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchRequest setTimeLimit(final int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchRequest setTypesOnly(final boolean typesOnly) {
        throw new UnsupportedOperationException();
    }
}
