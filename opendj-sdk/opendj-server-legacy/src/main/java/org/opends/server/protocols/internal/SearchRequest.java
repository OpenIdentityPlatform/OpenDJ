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
package org.opends.server.protocols.internal;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Reject;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.SearchFilter;

/**
 * Search request implementation.
 *
 * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl
 */
public final class SearchRequest extends AbstractRequestImpl {
    /**
     * Use a LinkedHashSet to return the attributes in the same order as requested by the user.
     */
    private final Set<String> attributes = new LinkedHashSet<>();
    private DereferenceAliasesPolicy dereferenceAliasesPolicy = DereferenceAliasesPolicy.NEVER;
    private SearchFilter filter;
    private DN name;
    private SearchScope scope;
    private int sizeLimit;
    private int timeLimit;
    private boolean typesOnly;

    /**
     * To be removed.
     *
     * @param name
     *          the dn
     * @param scope
     *          the search scope
     * @param filter
     *          the search filter
     */
    SearchRequest(final DN name, final SearchScope scope, final SearchFilter filter) {
        this.name = name;
        this.scope = scope;
        this.filter = filter;
    }

    /**
     * To be removed.
     *
     * @param attributeDescriptions
     *          the attribute descriptions
     * @return the current object
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#addAttribute(String...)
     */
    public SearchRequest addAttribute(final String... attributeDescriptions) {
        for (final String attributeDescription : attributeDescriptions) {
            attributes.add(Reject.checkNotNull(attributeDescription));
        }
        return this;
    }

    /**
     * To be added to {@link org.forgerock.opendj.ldap.requests.SearchRequestImpl}?
     *
     * @param attributeDescriptions
     *          the attribute descriptions
     * @return the current object
     */
    public SearchRequest addAttribute(final Collection<String> attributeDescriptions) {
        for (final String attributeDescription : attributeDescriptions) {
            attributes.add(Reject.checkNotNull(attributeDescription));
        }
        return this;
    }

    /**
     * To be removed.
     *
     * @return the attributes
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#getAttributes()
     */
    public Set<String> getAttributes() {
        return attributes;
    }

    /**
     * To be removed.
     *
     * @return the dereference aliases policy
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#getDereferenceAliasesPolicy()
     */
    public DereferenceAliasesPolicy getDereferenceAliasesPolicy() {
        return dereferenceAliasesPolicy;
    }

    /**
     * To be removed.
     *
     * @return the search filter
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#getFilter()
     */
    public SearchFilter getFilter() {
        return filter;
    }

    /**
     * To be removed.
     *
     * @return the DN
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#getName()
     */
    public DN getName() {
        return name;
    }

    /**
     * To be removed.
     *
     * @return the search scope
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#getScope()
     */
    public SearchScope getScope() {
        return scope;
    }

    /**
     * To be removed.
     *
     * @return the size limit
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#getSizeLimit()
     */
    public int getSizeLimit() {
        return sizeLimit;
    }

    /**
     * To be removed.
     *
     * @return is single entry search
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#isSingleEntrySearch()
     */
    public boolean isSingleEntrySearch() {
        return sizeLimit == 1 || SearchScope.BASE_OBJECT.equals(scope);
    }

    /**
     * To be removed.
     *
     * @return the time limit
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#getTimeLimit()
     */
    public int getTimeLimit() {
        return timeLimit;
    }

    /**
     * To be removed.
     *
     * @return the types only
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#isTypesOnly()
     */
    public boolean isTypesOnly() {
        return typesOnly;
    }

    /**
     * To be removed.
     *
     * @param policy the dereference aliases policy
     * @return the current request
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#setDereferenceAliasesPolicy(DereferenceAliasesPolicy)
     */
    public SearchRequest setDereferenceAliasesPolicy(final DereferenceAliasesPolicy policy) {
        Reject.ifNull(policy);

        this.dereferenceAliasesPolicy = policy;
        return this;
    }

    /**
     * To be removed.
     *
     * @param filter the search filter
     * @return the current request
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#setFilter(org.forgerock.opendj.ldap.Filter)
     */
    public SearchRequest setFilter(final SearchFilter filter) {
        Reject.ifNull(filter);

        this.filter = filter;
        return this;
    }

    /**
     * To be removed.
     *
     * @param filter the search filter
     * @return the current request
     * @throws DirectoryException if problem occurs
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#setFilter(String)
     */
    public SearchRequest setFilter(final String filter) throws DirectoryException {
        this.filter = SearchFilter.createFilterFromString(filter);
        return this;
    }

    /**
     * To be removed.
     *
     * @param dn the dn
     * @return the current request
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#setName(org.forgerock.opendj.ldap.DN)
     */
    public SearchRequest setName(final DN dn) {
        Reject.ifNull(dn);

        this.name = dn;
        return this;
    }

    /**
     * To be removed.
     *
     * @param dn the dn
     * @return the current request
     * @throws DirectoryException if problem occurs
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#setName(String)
     */
    public SearchRequest setName(final String dn) throws DirectoryException {
        Reject.ifNull(dn);

        this.name = DN.valueOf(dn);
        return this;
    }

    /**
     * To be removed.
     *
     * @param scope the search scope
     * @return the current request
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#setScope(SearchScope)
     */
    public SearchRequest setScope(final SearchScope scope) {
        Reject.ifNull(scope);

        this.scope = scope;
        return this;
    }

    /**
     * To be removed.
     *
     * @param limit the size limit
     * @return the current request
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#setSizeLimit(int)
     */
    public SearchRequest setSizeLimit(final int limit) {
        Reject.ifFalse(limit >= 0, "negative size limit");

        this.sizeLimit = limit;
        return this;
    }

    /**
     * To be removed.
     *
     * @param limit the time limit
     * @return the current request
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#setTimeLimit(int)
     */
    public SearchRequest setTimeLimit(final int limit) {
        Reject.ifFalse(limit >= 0, "negative time limit");

        this.timeLimit = limit;
        return this;
    }

    /**
     * To be removed.
     *
     * @param typesOnly the types only
     * @return the current request
     * @see org.forgerock.opendj.ldap.requests.SearchRequestImpl#setTypesOnly(boolean)
     */
    public SearchRequest setTypesOnly(final boolean typesOnly) {
        this.typesOnly = typesOnly;
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public SearchRequest addControl(Control control) {
        super.addControl(control);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public SearchRequest addControl(Collection<Control> controls) {
        super.addControl(controls);
        return this;
    }

    /** {@inheritDoc} */
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

}
