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
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.protocols.internal;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Reject;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.SearchFilter;

/**
 * Search request implementation.
 *
 * @see org.forgerock.opendj.ldap.requests.SearchRequest
 */
public final class SearchRequest extends AbstractRequestImpl {
    /** Use a LinkedHashSet to return the attributes in the same order as requested by the user. */
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#addAttribute(String...)
     */
    public SearchRequest addAttribute(final String... attributeDescriptions) {
        for (final String attributeDescription : attributeDescriptions) {
            attributes.add(Reject.checkNotNull(attributeDescription));
        }
        return this;
    }

    /**
     * To be added to {@link org.forgerock.opendj.ldap.requests.SearchRequest}?
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#getAttributes()
     */
    public Set<String> getAttributes() {
        return attributes;
    }

    /**
     * To be removed.
     *
     * @return the dereference aliases policy
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#getDereferenceAliasesPolicy()
     */
    public DereferenceAliasesPolicy getDereferenceAliasesPolicy() {
        return dereferenceAliasesPolicy;
    }

    /**
     * To be removed.
     *
     * @return the search filter
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#getFilter()
     */
    public SearchFilter getFilter() {
        return filter;
    }

    /**
     * To be removed.
     *
     * @return the DN
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#getName()
     */
    public DN getName() {
        return name;
    }

    /**
     * To be removed.
     *
     * @return the search scope
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#getScope()
     */
    public SearchScope getScope() {
        return scope;
    }

    /**
     * To be removed.
     *
     * @return the size limit
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#getSizeLimit()
     */
    public int getSizeLimit() {
        return sizeLimit;
    }

    /**
     * To be removed.
     *
     * @return is single entry search
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#isSingleEntrySearch()
     */
    public boolean isSingleEntrySearch() {
        return sizeLimit == 1 || SearchScope.BASE_OBJECT.equals(scope);
    }

    /**
     * To be removed.
     *
     * @return the time limit
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#getTimeLimit()
     */
    public int getTimeLimit() {
        return timeLimit;
    }

    /**
     * To be removed.
     *
     * @return the types only
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#isTypesOnly()
     */
    public boolean isTypesOnly() {
        return typesOnly;
    }

    /**
     * To be removed.
     *
     * @param policy the dereference aliases policy
     * @return the current request
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#setDereferenceAliasesPolicy(DereferenceAliasesPolicy)
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#setFilter(org.forgerock.opendj.ldap.Filter)
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#setFilter(String)
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#setName(DN)
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#setName(String)
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#setScope(SearchScope)
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#setSizeLimit(int)
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#setTimeLimit(int)
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
     * @see org.forgerock.opendj.ldap.requests.SearchRequest#setTypesOnly(boolean)
     */
    public SearchRequest setTypesOnly(final boolean typesOnly) {
        this.typesOnly = typesOnly;
        return this;
    }

    @Override
    public SearchRequest addControl(Control control) {
        super.addControl(control);
        return this;
    }

    @Override
    public SearchRequest addControl(Collection<Control> controls) {
        super.addControl(controls);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("(name=").append(getName());
        sb.append(", scope=").append(getScope());
        sb.append(", filter=").append(getFilter());
        sb.append(", dereferenceAliasesPolicy=").append(getDereferenceAliasesPolicy());
        if (getSizeLimit()!=0) {
          sb.append(", sizeLimit=").append(getSizeLimit());
        }
        if (getTimeLimit()!=0) {
          sb.append(", timeLimit=").append(getTimeLimit());
        }
        sb.append(", typesOnly=").append(isTypesOnly());
        if (!getAttributes().isEmpty()) {
          sb.append(", attributes=").append(getAttributes());
        }
        if (!getControls().isEmpty()) {
          sb.append(", controls=").append(getControls());
        }
        sb.append(")");
        return sb.toString();
    }
}
