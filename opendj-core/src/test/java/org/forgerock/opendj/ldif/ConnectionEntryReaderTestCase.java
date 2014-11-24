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
 *      Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.ldif;

import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.Fail.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.responses.Responses.*;
import static org.forgerock.opendj.ldap.spi.LdapPromises.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * This class tests the ConnectionEntryReader functionality.
 */
@SuppressWarnings("javadoc")
public class ConnectionEntryReaderTestCase extends AbstractLDIFTestCase {

    private static final SearchResultEntry ENTRY1 = newSearchResultEntry("cn=entry1");
    private static final SearchResultEntry ENTRY2 = newSearchResultEntry("cn=entry2");
    private static final SearchResultEntry ENTRY3 = newSearchResultEntry("cn=entry3");
    private static final Result ERROR = newResult(ResultCode.BUSY);
    private static final SearchResultReference REF =
            newSearchResultReference("http://www.forgerock.com/");
    private static final SearchRequest SEARCH = Requests.newSearchRequest("",
            SearchScope.WHOLE_SUBTREE, "(objectClass=*)");
    private static final Result SUCCESS = newResult(ResultCode.SUCCESS);

    @Test
    public final void testHasNextWhenError() throws Exception {
        final ConnectionEntryReader reader = newReader(ERROR);
        try {
            reader.hasNext();
            fail();
        } catch (final LdapException e) {
            assertThat(e.getResult()).isSameAs(ERROR);
        } finally {
            reader.close();
        }
    }

    @Test
    public final void testReadEntry() throws Exception {
        final ConnectionEntryReader reader = newReader(ENTRY1, SUCCESS);
        try {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.isEntry()).isTrue();
            assertThat(reader.isReference()).isFalse();
            assertThat(reader.readEntry()).isSameAs(ENTRY1);
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        } finally {
            reader.close();
        }
    }

    @Test
    public final void testReadEntryWhenError() throws Exception {
        final ConnectionEntryReader reader = newReader(ERROR);
        try {
            reader.readEntry();
            fail();
        } catch (final LdapException e) {
            assertThat(e.getResult()).isSameAs(ERROR);
        } finally {
            reader.close();
        }
    }

    @Test(expectedExceptions = NoSuchElementException.class)
    public final void testReadEntryWhenNoMore() throws Exception {
        final ConnectionEntryReader reader = newReader(SUCCESS);
        try {
            assertThat(reader.hasNext()).isFalse();
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    @Test
    public final void testReadEntryWhenReference() throws Exception {
        final ConnectionEntryReader reader = newReader(REF, SUCCESS);
        try {
            assertThat(reader.hasNext()).isTrue();
            try {
                reader.readEntry();
                fail();
            } catch (final SearchResultReferenceIOException e) {
                assertThat(e.getReference()).isSameAs(REF);
            }
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        } finally {
            reader.close();
        }
    }

    @Test
    public final void testReadMultipleResults() throws Exception {
        final ConnectionEntryReader reader = newReader(ENTRY1, ENTRY2, REF, ENTRY3, SUCCESS);
        try {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.readEntry()).isSameAs(ENTRY1);
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.readEntry()).isSameAs(ENTRY2);
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.readReference()).isSameAs(REF);
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.readEntry()).isSameAs(ENTRY3);
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        } finally {
            reader.close();
        }
    }

    @Test
    public final void testReadReference() throws Exception {
        final ConnectionEntryReader reader = newReader(REF, SUCCESS);
        try {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.isEntry()).isFalse();
            assertThat(reader.isReference()).isTrue();
            assertThat(reader.readReference()).isSameAs(REF);
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        } finally {
            reader.close();
        }
    }

    @Test
    public final void testReadReferenceWhenEntry() throws Exception {
        final ConnectionEntryReader reader = newReader(ENTRY1, SUCCESS);
        try {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.readReference()).isNull();
            assertThat(reader.readEntry()).isSameAs(ENTRY1);
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        } finally {
            reader.close();
        }
    }

    @Test
    public final void testReadReferenceWhenError() throws Exception {
        final ConnectionEntryReader reader = newReader(ERROR);
        try {
            reader.readReference();
            fail();
        } catch (final LdapException e) {
            assertThat(e.getResult()).isSameAs(ERROR);
        } finally {
            reader.close();
        }
    }

    @Test(expectedExceptions = NoSuchElementException.class)
    public final void testReadReferenceWhenNoMore() throws Exception {
        final ConnectionEntryReader reader = newReader(SUCCESS);
        try {
            assertThat(reader.hasNext()).isFalse();
            reader.readReference();
        } finally {
            reader.close();
        }
    }

    @Test
    public final void testReadResult() throws Exception {
        final ConnectionEntryReader reader = newReader(SUCCESS);
        try {
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        } finally {
            reader.close();
        }
    }

    @Test
    public final void testReadResultWhenError() throws Exception {
        final ConnectionEntryReader reader = newReader(ERROR);
        try {
            reader.readResult();
            fail();
        } catch (final LdapException e) {
            assertThat(e.getResult()).isSameAs(ERROR);
        } finally {
            reader.close();
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public final void testReadResultWhenEntry() throws Exception {
        final ConnectionEntryReader reader = newReader(ENTRY1, SUCCESS);
        try {
            reader.readResult();
        } finally {
            reader.close();
        }
    }

    private ConnectionEntryReader newReader(final Object... responses) {
        final Connection connection = mock(Connection.class);
        // @formatter:off
        when(connection.searchAsync(same(SEARCH), any(SearchResultHandler.class))).thenAnswer(
            new Answer<LdapPromise<Result>>() {
                @Override
                public LdapPromise<Result> answer(final InvocationOnMock invocation) throws Throwable {
                    // Execute handler and return future.
                    final SearchResultHandler handler = (SearchResultHandler) invocation.getArguments()[1];
                    if (handler != null) {
                        for (int i = 0; i < responses.length; i++) {
                            final Object response = responses[i];
                            if (response instanceof SearchResultEntry) {
                                handler.handleEntry((SearchResultEntry) response);
                            } else if (response instanceof SearchResultReference) {
                                handler.handleReference((SearchResultReference) response);
                            }
                        }
                    }
                    final Result result = (Result) responses[responses.length - 1];
                    if (result.isSuccess()) {
                        return newSuccessfulLdapPromise(result);
                    } else {
                        return newFailedLdapPromise(newLdapException(result));
                    }
                }
            });
        // @formatter:on
        return new ConnectionEntryReader(connection, SEARCH);
    }

}
