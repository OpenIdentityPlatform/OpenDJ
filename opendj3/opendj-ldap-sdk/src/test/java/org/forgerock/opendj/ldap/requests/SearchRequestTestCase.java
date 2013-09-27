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
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SearchRequestTestCase extends RequestTestCase {

    @DataProvider(name = "SearchRequests")
    public Object[][] getSearchRequests() throws Exception {
        return getTestRequests();
    }

    @Override
    protected SearchRequest[] createTestRequests() throws Exception {
        return new SearchRequest[] {
                Requests.newSearchRequest(
                        "uid=user.0,ou=people,o=test",
                        SearchScope.BASE_OBJECT, "(uid=user)", "uid", "ou"),
                Requests.newSearchRequest("uid=user.0,ou=people,o=test",
                        SearchScope.SINGLE_LEVEL, "(uid=user)", "uid", "ou") };
    }

    @Test
    public void createRequestForSingleEntrySearch() throws Exception {
        SearchRequest request = Requests.newSingleEntrySearchRequest(
                DN.valueOf("uid=user.0,ou=people,o=test"),
                SearchScope.BASE_OBJECT, Filter.equality("uid", "user"), "uid");

        assertThat(request.getSizeLimit()).isEqualTo(1);
        assertThat(request.isSingleEntrySearch()).isTrue();
    }

    @Test
    public void createRequestForSingleEntrySearchWithStrings() throws Exception {
        SearchRequest request = Requests.newSingleEntrySearchRequest(
                "uid=user.0,ou=people,o=test",
                SearchScope.BASE_OBJECT, "(uid=user)", "uid");

        assertThat(request.getSizeLimit()).isEqualTo(1);
        assertThat(request.isSingleEntrySearch()).isTrue();
    }

    @Test
    public void createRequestWithBaseObjectScope() throws Exception {
        SearchRequest request = Requests.newSearchRequest(
                DN.valueOf("uid=user.0,ou=people,o=test"),
                SearchScope.BASE_OBJECT, Filter.equality("uid", "user"), "uid");

        assertThat(request.isSingleEntrySearch()).isTrue();
    }
}
