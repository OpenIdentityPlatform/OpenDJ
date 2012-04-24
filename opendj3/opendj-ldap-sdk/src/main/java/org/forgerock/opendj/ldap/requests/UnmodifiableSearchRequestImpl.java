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
    UnmodifiableSearchRequestImpl(SearchRequest impl) {
        super(impl);
    }

    public SearchRequest addAttribute(String... attributeDescriptions) {
        throw new UnsupportedOperationException();
    }

    public List<String> getAttributes() {
        return Collections.unmodifiableList(impl.getAttributes());
    }

    public DereferenceAliasesPolicy getDereferenceAliasesPolicy() {
        return impl.getDereferenceAliasesPolicy();
    }

    public Filter getFilter() {
        return impl.getFilter();
    }

    public DN getName() {
        return impl.getName();
    }

    public SearchScope getScope() {
        return impl.getScope();
    }

    public int getSizeLimit() {
        return impl.getSizeLimit();
    }

    public int getTimeLimit() {
        return impl.getTimeLimit();
    }

    public boolean isTypesOnly() {
        return impl.isTypesOnly();
    }

    public SearchRequest setDereferenceAliasesPolicy(DereferenceAliasesPolicy policy) {
        throw new UnsupportedOperationException();
    }

    public SearchRequest setFilter(Filter filter) {
        throw new UnsupportedOperationException();
    }

    public SearchRequest setFilter(String filter) {
        throw new UnsupportedOperationException();
    }

    public SearchRequest setName(DN dn) {
        throw new UnsupportedOperationException();
    }

    public SearchRequest setName(String dn) {
        throw new UnsupportedOperationException();
    }

    public SearchRequest setScope(SearchScope scope) {
        throw new UnsupportedOperationException();
    }

    public SearchRequest setSizeLimit(int limit) {
        throw new UnsupportedOperationException();
    }

    public SearchRequest setTimeLimit(int limit) {
        throw new UnsupportedOperationException();
    }

    public SearchRequest setTypesOnly(boolean typesOnly) {
        throw new UnsupportedOperationException();
    }
}
