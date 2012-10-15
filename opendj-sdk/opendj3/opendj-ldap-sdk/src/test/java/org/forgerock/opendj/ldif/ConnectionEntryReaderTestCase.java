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
 *      Copyright 2011 ForgeRock AS
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

import com.forgerock.opendj.util.CompletedFutureResult;

/**
 * This class tests the ConnectionEntryReader functionality.
 */
@SuppressWarnings("javadoc")
public class ConnectionEntryReaderTestCase extends AbstractLDIFTestCase {

    /**
     * Test a ConnectionEntryReader. Searching and finding entry.
     *
     * @throws Exception
     */
    @Test()
    public final void testConnectionEntryReaderHandlesEntry() throws Exception {
        final Connection connection = mock(Connection.class);
        final SearchRequest sr =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");

        // @formatter:off
        when(
            connection.searchAsync(same(sr), (IntermediateResponseHandler) isNull(),
                any(SearchResultHandler.class))).thenAnswer(
                new Answer<FutureResult<Result>>() {
                        @Override
                        public FutureResult<Result> answer(final InvocationOnMock invocation)
                                throws Throwable {
                            // Execute handler and return future.
                            final SearchResultHandler handler =
                                    (SearchResultHandler) invocation.getArguments()[2];
                            if (handler != null) {
                                handler.handleEntry(Responses.newSearchResultEntry("cn=test"));
                                handler.handleResult(Responses.newResult(ResultCode.SUCCESS));
                            }
                            return new CompletedFutureResult<Result>(Responses
                                    .newResult(ResultCode.SUCCESS));
                        }
                    }
            );
        // @formatter:on

        ConnectionEntryReader reader = null;
        try {
            reader = new ConnectionEntryReader(connection, sr);
            assertThat(reader.hasNext()).isTrue();
            final SearchResultEntry entry = reader.readEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName().toString()).isEqualTo("cn=test");
            assertThat(reader.hasNext()).isFalse();
        } finally {
            reader.close();
        }
    }

    /**
     * Test a ConnectionEntryReader. Searching and finding reference.
     *
     * @throws Exception
     */
    @Test()
    public final void testConnectionEntryReaderHandlesReference() throws Exception {
        final Connection connection = mock(Connection.class);
        final SearchRequest sr =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");

        // @formatter:off
        when(
            connection.searchAsync(same(sr), (IntermediateResponseHandler) isNull(),
                any(SearchResultHandler.class))).thenAnswer(
                new Answer<FutureResult<Result>>() {
                            @Override
                            public FutureResult<Result> answer(final InvocationOnMock invocation)
                                    throws Throwable {
                                // Execute handler and return future.
                                final SearchResultHandler handler =
                                        (SearchResultHandler) invocation.getArguments()[2];
                                if (handler != null) {
                                    handler.handleReference(Responses
                                            .newSearchResultReference("http://www.forgerock.com/"));
                                    handler.handleResult(Responses.newResult(ResultCode.SUCCESS));
                                }
                                return new CompletedFutureResult<Result>(Responses
                                        .newResult(ResultCode.SUCCESS));
                            }
                        }
            );
        // @formatter:on

        ConnectionEntryReader reader = null;
        try {
            reader = new ConnectionEntryReader(connection, sr);
            assertThat(reader.hasNext()).isTrue();
            final SearchResultReference reference = reader.readReference();
            assertThat(reference).isNotNull();
            assertThat(reference.getURIs().get(0).toString())
                    .isEqualTo("http://www.forgerock.com/");
        } finally {
            reader.close();
        }
    }

    /**
     * The ConnectionEntryReader try to read an entry but it is a reference. The
     * readEntry provokes an SearchResultReferenceIOException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = SearchResultReferenceIOException.class)
    public final void testConnectionEntryReaderReadEntryThrowsSearchResultReferenceIOException()
            throws Exception {
        final Connection connection = mock(Connection.class);
        final SearchRequest sr =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");

        // @formatter:off
        when(
                connection.searchAsync(same(sr), (IntermediateResponseHandler) isNull(),
                    any(SearchResultHandler.class))).thenAnswer(
                    new Answer<FutureResult<Result>>() {
                                @Override
                                public FutureResult<Result> answer(final InvocationOnMock invocation)
                                        throws Throwable {
                                    // Execute handler and return future.
                                    final SearchResultHandler handler =
                                            (SearchResultHandler) invocation.getArguments()[2];

                                    if (handler != null) {
                                        handler.handleReference(Responses
                                                .newSearchResultReference("http://www.forgerock.com/"));
                                        handler.handleResult(Responses.newResult(ResultCode.SUCCESS));
                                    }
                                    return new CompletedFutureResult<Result>(Responses
                                            .newResult(ResultCode.SUCCESS));
                                }
                            }
            );
        // @formatter:on

        ConnectionEntryReader reader = null;
        try {
            reader = new ConnectionEntryReader(connection, sr);
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Try to read a reference instead of an entry. Do nothing.
     *
     * @throws Exception
     */
    @Test()
    public final void testConnectionEntryReaderReadReferenceInsteadOfEntry() throws Exception {
        final Connection connection = mock(Connection.class);
        final SearchRequest sr =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");

        // @formatter:off
        when(
                connection.searchAsync(same(sr), (IntermediateResponseHandler) isNull(),
                    any(SearchResultHandler.class))).thenAnswer(
                    new Answer<FutureResult<Result>>() {
                                @Override
                                public FutureResult<Result> answer(final InvocationOnMock invocation)
                                        throws Throwable {
                                    // Execute handler and return future.
                                    final SearchResultHandler handler =
                                            (SearchResultHandler) invocation.getArguments()[2];
                                    if (handler != null) {
                                        handler.handleEntry(Responses.newSearchResultEntry("cn=test"));
                                        handler.handleResult(Responses.newResult(ResultCode.SUCCESS));
                                    }
                                    return new CompletedFutureResult<Result>(Responses
                                            .newResult(ResultCode.SUCCESS));
                                }
                            }
            );
        // @formatter:on
        ConnectionEntryReader reader = null;
        try {
            reader = new ConnectionEntryReader(connection, sr);
            final SearchResultReference ref = reader.readReference();
            assertThat(ref).isNull();
        } finally {
            reader.close();
        }
    }

    /**
     * Connection Entry Reader is able to read multiple result from search.
     *
     * @throws Exception
     */
    @Test()
    public final void testConnectionEntryReaderMultipleResults() throws Exception {
        final Connection connection = mock(Connection.class);
        final SearchRequest sr =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");

        // @formatter:off
        when(
                connection.searchAsync(same(sr), (IntermediateResponseHandler) isNull(),
                    any(SearchResultHandler.class))).thenAnswer(
                    new Answer<FutureResult<Result>>() {
                                @Override
                                public FutureResult<Result> answer(final InvocationOnMock invocation)
                                        throws Throwable {
                                    // Execute handler and return future.
                                    final SearchResultHandler handler =
                                            (SearchResultHandler) invocation.getArguments()[2];
                                    if (handler != null) {
                                        handler.handleEntry(Responses.newSearchResultEntry("cn=Jensen"));
                                        handler.handleEntry(Responses.newSearchResultEntry("cn=Carter"));
                                        handler.handleEntry(Responses.newSearchResultEntry("cn=Aaccf Amar"));
                                        handler.handleReference(Responses
                                                .newSearchResultReference("http://www.forgerock.com/"));
                                        // ResultCode need to be present.
                                        handler.handleResult(Responses.newResult(ResultCode.SUCCESS));
                                    }
                                    return new CompletedFutureResult<Result>(Responses
                                            .newResult(ResultCode.SUCCESS));
                                }
                            }
            );
        // @formatter:on

        ConnectionEntryReader reader = null;
        try {
            reader = new ConnectionEntryReader(connection, sr);
            SearchResultEntry entry = null;
            SearchResultReference ref = null;
            entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo("cn=Jensen");
            assertThat(reader.hasNext()).isTrue();
            entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo("cn=Carter");
            assertThat(reader.hasNext()).isTrue();
            entry = reader.readEntry();
            assertThat(entry.getName().toString()).isEqualTo("cn=Aaccf Amar");
            assertThat(reader.hasNext()).isTrue();
            ref = reader.readReference();
            assertThat(ref.getURIs().get(0)).isEqualTo("http://www.forgerock.com/");
            assertThat(reader.hasNext()).isFalse();

        } finally {
            reader.close();
        }
    }

    /**
     * The SearchResultHandler contains no entry / no reference, only a negative
     * resultCode. ErrorResultIOException expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ErrorResultIOException.class)
    public final void testConnectionEntryReaderHandlerResultIsBusy() throws Exception {
        final Connection connection = mock(Connection.class);
        final SearchRequest sr =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");

        // @formatter:off
        when(
                connection.searchAsync(same(sr), (IntermediateResponseHandler) isNull(),
                    any(SearchResultHandler.class))).thenAnswer(
                    new Answer<FutureResult<Result>>() {
                                @Override
                                public FutureResult<Result> answer(final InvocationOnMock invocation)
                                        throws Throwable {
                                    // Execute handler and return future.
                                    final SearchResultHandler handler =
                                            (SearchResultHandler) invocation.getArguments()[2];
                                    if (handler != null) {
                                        handler.handleResult(Responses.newResult(ResultCode.BUSY));
                                    }
                                    return new CompletedFutureResult<Result>(Responses
                                            .newResult(ResultCode.SUCCESS));
                                }
                            }
            );
        // @formatter:on

        ConnectionEntryReader reader = null;
        try {
            reader = new ConnectionEntryReader(connection, sr);
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * Handler contains a successful code. NoSuchElementException expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public final void testConnectionEntryReaderHandlerResultIsSucess() throws Exception {
        final Connection connection = mock(Connection.class);
        final SearchRequest sr =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");

        // @formatter:off
        when(
                connection.searchAsync(same(sr), (IntermediateResponseHandler) isNull(),
                    any(SearchResultHandler.class))).thenAnswer(
                    new Answer<FutureResult<Result>>() {
                                @Override
                                public FutureResult<Result> answer(final InvocationOnMock invocation)
                                        throws Throwable {
                                    // Execute handler and return future.
                                    final SearchResultHandler handler =
                                            (SearchResultHandler) invocation.getArguments()[2];
                                    if (handler != null) {
                                        handler.handleResult(Responses.newResult(ResultCode.SUCCESS));
                                    }
                                    return new CompletedFutureResult<Result>(Responses
                                            .newResult(ResultCode.SUCCESS));
                                }
                            }
            );
        // @formatter:on

        ConnectionEntryReader reader = null;
        try {
            reader = new ConnectionEntryReader(connection, sr);
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * ConnectionEntryReader encounters an error. Handler handles an error
     * result.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ErrorResultIOException.class)
    public final void testConnectionEntryReaderHandlerErrorResult() throws Exception {
        final Connection connection = mock(Connection.class);
        final SearchRequest sr =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");

        // @formatter:off
        when(
                connection.searchAsync(same(sr), (IntermediateResponseHandler) isNull(),
                    any(SearchResultHandler.class))).thenAnswer(
                    new Answer<FutureResult<Result>>() {
                                @Override
                                public FutureResult<Result> answer(final InvocationOnMock invocation)
                                        throws Throwable {
                                    // Execute handler and return future.
                                    final SearchResultHandler handler =
                                            (SearchResultHandler) invocation.getArguments()[2];
                                    if (handler != null) {
                                        handler.handleErrorResult(ErrorResultException.newErrorResult(
                                                Responses.newResult(ResultCode.CLIENT_SIDE_PARAM_ERROR)));
                                    }
                                    return new CompletedFutureResult<Result>(Responses
                                            .newResult(ResultCode.SUCCESS));
                                }
                            }
            );
        // @formatter:on

        ConnectionEntryReader reader = null;
        try {
            reader = new ConnectionEntryReader(connection, sr);
            reader.readEntry();
        } finally {
            reader.close();
        }
    }

    /**
     * The ConnectionEntryReader doesn't allow a null connection.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testConnectionEntryReaderDoesntAllowNull() throws Exception {
        ConnectionEntryReader reader = null;
        try {
            reader =
                    new ConnectionEntryReader(null, Requests.newSearchRequest("dc=example,dc=com",
                            SearchScope.WHOLE_SUBTREE, "(sn=Jensen)", "cn"));
        } finally {
            reader.close();
        }

    }

}
