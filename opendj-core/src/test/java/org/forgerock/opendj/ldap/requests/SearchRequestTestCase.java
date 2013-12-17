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
 *      Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import static org.fest.assertions.Assertions.*;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SearchRequestTestCase extends RequestsTestCase {

    private static final SearchRequest NEW_SEARCH_REQUEST = Requests.newSearchRequest("uid=user.0,ou=people,o=test",
            SearchScope.BASE_OBJECT, "(uid=user)", "uid", "ou");

    private static final SearchRequest NEW_SEARCH_REQUEST2 = Requests.newSearchRequest("uid=user.0,ou=people,o=test",
            SearchScope.SINGLE_LEVEL, "(uid=user)", "uid", "ou");

    @DataProvider(name = "SearchRequests")
    private Object[][] getSearchRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected SearchRequest[] newInstance() {
        return new SearchRequest[] {
            NEW_SEARCH_REQUEST,
            NEW_SEARCH_REQUEST2 };
    }

    @Test
    public void createRequestForSingleEntrySearch() throws Exception {
        SearchRequest request = Requests.newSingleEntrySearchRequest(DN.valueOf("uid=user.0,ou=people,o=test"),
                SearchScope.BASE_OBJECT, Filter.equality("uid", "user"), "uid");

        assertThat(request.getSizeLimit()).isEqualTo(1);
        assertThat(request.isSingleEntrySearch()).isTrue();
    }

    @Test
    public void createRequestForSingleEntrySearchWithStrings() throws Exception {
        SearchRequest request = Requests.newSingleEntrySearchRequest("uid=user.0,ou=people,o=test",
                SearchScope.BASE_OBJECT, "(uid=user)", "uid");

        assertThat(request.getSizeLimit()).isEqualTo(1);
        assertThat(request.isSingleEntrySearch()).isTrue();
    }

    @Test
    public void createRequestWithBaseObjectScope() throws Exception {
        SearchRequest request = Requests.newSearchRequest(DN.valueOf("uid=user.0,ou=people,o=test"),
                SearchScope.BASE_OBJECT, Filter.equality("uid", "user"), "uid");

        assertThat(request.isSingleEntrySearch()).isTrue();
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfSearchRequest((SearchRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableSearchRequest((SearchRequest) original);
    }

    @Test(dataProvider = "SearchRequests")
    public void testModifiableRequest(final SearchRequest original) {
        final String name = "uid=bjensen";
        final Filter filter = Filter.format("(&(uid=%s)(age>=%s))", "bjensen", 21);
        final SearchScope scope = SearchScope.BASE_OBJECT;
        final DereferenceAliasesPolicy policy = DereferenceAliasesPolicy.ALWAYS;
        final int timeLimit = 10;
        final int sizeLimit = 15;

        final SearchRequest copy = (SearchRequest) copyOf(original);
        copy.setName(name);
        copy.setFilter(filter);
        copy.setScope(scope);
        copy.setDereferenceAliasesPolicy(policy);
        copy.setSizeLimit(sizeLimit);
        copy.setTimeLimit(timeLimit);
        copy.setTypesOnly(true);

        assertThat(copy.getName().toString()).isEqualTo(name);
        assertThat(copy.getFilter()).isEqualTo(filter);
        assertThat(copy.getScope()).isEqualTo(scope);
        assertThat(copy.getDereferenceAliasesPolicy()).isEqualTo(policy);
        assertThat(copy.getTimeLimit()).isEqualTo(timeLimit);
        assertThat(copy.getSizeLimit()).isEqualTo(sizeLimit);
        assertThat(copy.isTypesOnly()).isTrue();
    }

    @Test(dataProvider = "SearchRequests")
    public void testUnmodifiableRequest(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getName().toString()).isEqualTo(original.getName().toString());
        assertThat(unmodifiable.getFilter()).isEqualTo(original.getFilter());
        assertThat(unmodifiable.getScope()).isEqualTo(original.getScope());
        assertThat(unmodifiable.getDereferenceAliasesPolicy()).isEqualTo(original.getDereferenceAliasesPolicy());
        assertThat(unmodifiable.getTimeLimit()).isEqualTo(original.getTimeLimit());
        assertThat(unmodifiable.getSizeLimit()).isEqualTo(original.getSizeLimit());
    }

    @Test(dataProvider = "SearchRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetDereferenceAliasesPolicy(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        unmodifiable.setDereferenceAliasesPolicy(DereferenceAliasesPolicy.ALWAYS);
    }

    @Test(dataProvider = "SearchRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetFilter(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        unmodifiable.setFilter(Filter.format("(&(cn=%s)(age>=%s))", "bjensen", 21));
    }

    @Test(dataProvider = "SearchRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetFilter2(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        unmodifiable.setFilter("(&(cn=bjensen)(age>=21))");
    }

    @Test(dataProvider = "SearchRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        unmodifiable.setName("uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "SearchRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName2(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        unmodifiable.setName(DN.valueOf("uid=scarter,ou=people,dc=example,dc=com"));
    }

    @Test(dataProvider = "SearchRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetScope(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        unmodifiable.setScope(SearchScope.BASE_OBJECT);
    }

    @Test(dataProvider = "SearchRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetSizeLimit(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        unmodifiable.setSizeLimit(10);
    }

    @Test(dataProvider = "SearchRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetTimeLimit(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        unmodifiable.setTimeLimit(200);
    }

    @Test(dataProvider = "SearchRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetTypesOnly(final SearchRequest original) {
        final SearchRequest unmodifiable = (SearchRequest) unmodifiableOf(original);
        unmodifiable.setTypesOnly(false);
    }
}
