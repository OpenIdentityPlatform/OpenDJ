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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * The Search operation is used to request a server to return, subject to access
 * controls and other restrictions, a set of entries matching a complex search
 * criterion. This can be used to read attributes from a single entry, from
 * entries immediately subordinate to a particular entry, or from a whole
 * subtree of entries.
 * <p>
 * Use {@link Requests#newSearchRequest(DN, SearchScope, Filter, String...)} or
 * {@link Requests#newSearchRequest(String, SearchScope, String, String...)} to
 * create a new search request.
 *
 * <pre>
 * SearchRequest request = Requests.newSearchRequest(&quot;dc=example,dc=com&quot;, SearchScope.WHOLE_SUBTREE,
 *         &quot;(sn=Jensen)&quot;, &quot;cn&quot;);
 * </pre>
 *
 * Alternatively, use the
 * {@link org.forgerock.opendj.ldap.Connection#search(String, SearchScope, String, String...)
 * Connection.search()} method to specify the arguments directly.
 *
 * <pre>
 * Connection connection = ...;
 * ConnectionEntryReader reader = connection.search(
 *          "dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(sn=Jensen)", "cn");
 * </pre>
 */
public interface SearchRequest extends Request {
    /**
     * Adds the provided attribute name(s) to the list of attributes to be
     * included with each entry that matches the search criteria. Attributes
     * that are sub-types of listed attributes are implicitly included.
     *
     * @param attributeDescriptions
     *            The name(s) of the attribute to be included with each entry.
     * @return This search request.
     * @throws UnsupportedOperationException
     *             If this search request does not permit attribute names to be
     *             added.
     * @throws NullPointerException
     *             If {@code attributeDescriptions} was {@code null}.
     */
    SearchRequest addAttribute(String... attributeDescriptions);

    @Override
    SearchRequest addControl(Control control);

    /**
     * Returns a {@code List} containing the list of attributes to be included
     * with each entry that matches the search criteria. Attributes that are
     * sub-types of listed attributes are implicitly included. The returned
     * {@code List} may be modified if permitted by this search request.
     *
     * @return A {@code List} containing the list of attributes.
     */
    List<String> getAttributes();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns an indication as to whether or not alias entries are to be
     * dereferenced during the search.
     *
     * @return The alias dereferencing policy.
     */
    DereferenceAliasesPolicy getDereferenceAliasesPolicy();

    /**
     * Returns the filter that defines the conditions that must be fulfilled in
     * order for an entry to be returned.
     *
     * @return The search filter.
     */
    Filter getFilter();

    /**
     * Returns the distinguished name of the base entry relative to which the
     * search is to be performed.
     *
     * @return The distinguished name of the base entry.
     */
    DN getName();

    /**
     * Returns the scope of the search.
     *
     * @return The search scope.
     */
    SearchScope getScope();

    /**
     * Returns the size limit that should be used in order to restrict the
     * maximum number of entries returned by the search.
     * <p>
     * A value of zero (the default) in this field indicates that no
     * client-requested size limit restrictions are in effect. Servers may also
     * enforce a maximum number of entries to return.
     *
     * @return The size limit that should be used in order to restrict the
     *         maximum number of entries returned by the search.
     */
    int getSizeLimit();

    /**
     * Indicates whether search result is expected to be limited to a single entry.
     * <p>
     * It is the case if size limit is equal to 1 or if scope is equal to <code>SearchScope.BASE_OBJECT</code>.
     * <p>
     * If search results contain more than one entry, the search operation will throw
     * a <code>MultipleEntriesFoundException</code>.
     *
     * @return {@code true} if the search is limited to a single entry result,
     *         or {@code false} (the default) otherwise.
     */
    boolean isSingleEntrySearch();

    /**
     * Returns the time limit that should be used in order to restrict the
     * maximum time (in seconds) allowed for the search.
     * <p>
     * A value of zero (the default) in this field indicates that no
     * client-requested time limit restrictions are in effect for the search.
     * Servers may also enforce a maximum time limit for the search.
     *
     * @return The time limit that should be used in order to restrict the
     *         maximum time (in seconds) allowed for the search.
     */
    int getTimeLimit();

    /**
     * Indicates whether search results are to contain both attribute
     * descriptions and values, or just attribute descriptions.
     *
     * @return {@code true} if only attribute descriptions (and not values) are
     *         to be returned, or {@code false} (the default) if both attribute
     *         descriptions and values are to be returned.
     */
    boolean isTypesOnly();

    /**
     * Sets the alias dereferencing policy to be used during the search.
     *
     * @param policy
     *            The alias dereferencing policy to be used during the search.
     * @return This search request.
     * @throws UnsupportedOperationException
     *             If this search request does not permit the alias
     *             dereferencing policy to be set.
     * @throws NullPointerException
     *             If {@code policy} was {@code null}.
     */
    SearchRequest setDereferenceAliasesPolicy(DereferenceAliasesPolicy policy);

    /**
     * Sets the filter that defines the conditions that must be fulfilled in
     * order for an entry to be returned.
     *
     * @param filter
     *            The filter that defines the conditions that must be fulfilled
     *            in order for an entry to be returned.
     * @return This search request.
     * @throws UnsupportedOperationException
     *             If this search request does not permit the filter to be set.
     * @throws NullPointerException
     *             If {@code filter} was {@code null}.
     */
    SearchRequest setFilter(Filter filter);

    /**
     * Sets the filter that defines the conditions that must be fulfilled in
     * order for an entry to be returned.
     *
     * @param filter
     *            The filter that defines the conditions that must be fulfilled
     *            in order for an entry to be returned.
     * @return This search request.
     * @throws UnsupportedOperationException
     *             If this search request does not permit the filter to be set.
     * @throws LocalizedIllegalArgumentException
     *             If {@code filter} is not a valid LDAP string representation
     *             of a filter.
     * @throws NullPointerException
     *             If {@code filter} was {@code null}.
     */
    SearchRequest setFilter(String filter);

    /**
     * Sets the distinguished name of the base entry relative to which the
     * search is to be performed.
     *
     * @param dn
     *            The distinguished name of the base entry relative to which the
     *            search is to be performed.
     * @return This search request.
     * @throws UnsupportedOperationException
     *             If this search request does not permit the distinguished name
     *             to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    SearchRequest setName(DN dn);

    /**
     * Sets the distinguished name of the base entry relative to which the
     * search is to be performed.
     *
     * @param dn
     *            The distinguished name of the base entry relative to which the
     *            search is to be performed.
     * @return This search request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code dn} could not be decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this search request does not permit the distinguished name
     *             to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    SearchRequest setName(String dn);

    /**
     * Sets the scope of the search.
     *
     * @param scope
     *            The scope of the search.
     * @return This search request.
     * @throws UnsupportedOperationException
     *             If this search request does not permit the scope to be set.
     * @throws NullPointerException
     *             If {@code scope} was {@code null}.
     */
    SearchRequest setScope(SearchScope scope);

    /**
     * Sets the size limit that should be used in order to restrict the maximum
     * number of entries returned by the search.
     * <p>
     * A value of zero (the default) in this field indicates that no
     * client-requested size limit restrictions are in effect. Servers may also
     * enforce a maximum number of entries to return.
     *
     * @param limit
     *            The size limit that should be used in order to restrict the
     *            maximum number of entries returned by the search.
     * @return This search request.
     * @throws UnsupportedOperationException
     *             If this search request does not permit the size limit to be
     *             set.
     * @throws LocalizedIllegalArgumentException
     *             If {@code limit} was negative.
     */
    SearchRequest setSizeLimit(int limit);

    /**
     * Sets the time limit that should be used in order to restrict the maximum
     * time (in seconds) allowed for the search.
     * <p>
     * A value of zero (the default) in this field indicates that no
     * client-requested time limit restrictions are in effect for the search.
     * Servers may also enforce a maximum time limit for the search.
     *
     * @param limit
     *            The time limit that should be used in order to restrict the
     *            maximum time (in seconds) allowed for the search.
     * @return This search request.
     * @throws UnsupportedOperationException
     *             If this search request does not permit the time limit to be
     *             set.
     * @throws LocalizedIllegalArgumentException
     *             If {@code limit} was negative.
     */
    SearchRequest setTimeLimit(int limit);

    /**
     * Specifies whether search results are to contain both attribute
     * descriptions and values, or just attribute descriptions.
     *
     * @param typesOnly
     *            {@code true} if only attribute descriptions (and not values)
     *            are to be returned, or {@code false} (the default) if both
     *            attribute descriptions and values are to be returned.
     * @return This search request.
     * @throws UnsupportedOperationException
     *             If this search request does not permit the types-only
     *             parameter to be set.
     */
    SearchRequest setTypesOnly(boolean typesOnly);

}
