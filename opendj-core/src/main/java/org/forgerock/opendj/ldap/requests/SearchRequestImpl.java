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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Reject;

/**
 * Search request implementation.
 */
final class SearchRequestImpl extends AbstractRequestImpl<SearchRequest> implements SearchRequest {
    private final List<String> attributes = new LinkedList<>();
    private DereferenceAliasesPolicy dereferenceAliasesPolicy = DereferenceAliasesPolicy.NEVER;
    private Filter filter;
    private DN name;
    private SearchScope scope;
    private int sizeLimit;
    private int timeLimit;
    private boolean typesOnly;

    SearchRequestImpl(final DN name, final SearchScope scope, final Filter filter) {
        this.name = name;
        this.scope = scope;
        this.filter = filter;
    }

    SearchRequestImpl(final SearchRequest searchRequest) {
        super(searchRequest);
        this.attributes.addAll(searchRequest.getAttributes());
        this.name = searchRequest.getName();
        this.dereferenceAliasesPolicy = searchRequest.getDereferenceAliasesPolicy();
        this.filter = searchRequest.getFilter();
        this.scope = searchRequest.getScope();
        this.sizeLimit = searchRequest.getSizeLimit();
        this.timeLimit = searchRequest.getTimeLimit();
        this.typesOnly = searchRequest.isTypesOnly();
    }

    @Override
    public SearchRequest addAttribute(final String... attributeDescriptions) {
        for (final String attributeDescription : attributeDescriptions) {
            attributes.add(Reject.checkNotNull(attributeDescription));
        }
        return this;
    }

    @Override
    public List<String> getAttributes() {
        return attributes;
    }

    @Override
    public DereferenceAliasesPolicy getDereferenceAliasesPolicy() {
        return dereferenceAliasesPolicy;
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    @Override
    public DN getName() {
        return name;
    }

    @Override
    public SearchScope getScope() {
        return scope;
    }

    @Override
    public int getSizeLimit() {
        return sizeLimit;
    }

    @Override
    public boolean isSingleEntrySearch() {
        return sizeLimit == 1 || SearchScope.BASE_OBJECT.equals(scope);
    }

    @Override
    public int getTimeLimit() {
        return timeLimit;
    }

    @Override
    public boolean isTypesOnly() {
        return typesOnly;
    }

    @Override
    public SearchRequest setDereferenceAliasesPolicy(final DereferenceAliasesPolicy policy) {
        Reject.ifNull(policy);

        this.dereferenceAliasesPolicy = policy;
        return this;
    }

    @Override
    public SearchRequest setFilter(final Filter filter) {
        Reject.ifNull(filter);

        this.filter = filter;
        return this;
    }

    @Override
    public SearchRequest setFilter(final String filter) {
        this.filter = Filter.valueOf(filter);
        return this;
    }

    @Override
    public SearchRequest setName(final DN dn) {
        Reject.ifNull(dn);

        this.name = dn;
        return this;
    }

    @Override
    public SearchRequest setName(final String dn) {
        Reject.ifNull(dn);

        this.name = DN.valueOf(dn);
        return this;
    }

    @Override
    public SearchRequest setScope(final SearchScope scope) {
        Reject.ifNull(scope);

        this.scope = scope;
        return this;
    }

    @Override
    public SearchRequest setSizeLimit(final int limit) {
        Reject.ifFalse(limit >= 0, "negative size limit");

        this.sizeLimit = limit;
        return this;
    }

    @Override
    public SearchRequest setTimeLimit(final int limit) {
        Reject.ifFalse(limit >= 0, "negative time limit");

        this.timeLimit = limit;
        return this;
    }

    @Override
    public SearchRequest setTypesOnly(final boolean typesOnly) {
        this.typesOnly = typesOnly;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("SearchRequest(name=");
        builder.append(getName());
        builder.append(", scope=");
        builder.append(getScope());
        builder.append(", dereferenceAliasesPolicy=");
        builder.append(getDereferenceAliasesPolicy());
        builder.append(", sizeLimit=");
        builder.append(getSizeLimit());
        builder.append(", timeLimit=");
        builder.append(getTimeLimit());
        builder.append(", typesOnly=");
        builder.append(isTypesOnly());
        builder.append(", filter=");
        builder.append(getFilter());
        builder.append(", attributes=");
        builder.append(getAttributes());
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

    @Override
    SearchRequest getThis() {
        return this;
    }
}
