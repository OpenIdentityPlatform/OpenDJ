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
 * Copyright 2011-2016 ForgeRock AS.
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
        try (ConnectionEntryReader reader = newReader(ERROR)) {
            reader.hasNext();
            fail();
        } catch (final LdapException e) {
            assertThat(e.getResult()).isSameAs(ERROR);
        }
    }

    @Test
    public final void testReadEntry() throws Exception {
        try (ConnectionEntryReader reader = newReader(ENTRY1, SUCCESS)) {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.isEntry()).isTrue();
            assertThat(reader.isReference()).isFalse();
            assertThat(reader.readEntry()).isSameAs(ENTRY1);
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        }
    }

    @Test
    public final void testReadEntryWhenError() throws Exception {
        try (ConnectionEntryReader reader = newReader(ERROR)) {
            reader.readEntry();
            fail();
        } catch (final LdapException e) {
            assertThat(e.getResult()).isSameAs(ERROR);
        }
    }

    @Test(expectedExceptions = NoSuchElementException.class)
    public final void testReadEntryWhenNoMore() throws Exception {
        try (ConnectionEntryReader reader = newReader(SUCCESS)) {
            assertThat(reader.hasNext()).isFalse();
            reader.readEntry();
        }
    }

    @Test
    public final void testReadEntryWhenReference() throws Exception {
        try (ConnectionEntryReader reader = newReader(REF, SUCCESS)) {
            assertThat(reader.hasNext()).isTrue();
            try {
                reader.readEntry();
                fail();
            } catch (final SearchResultReferenceIOException e) {
                assertThat(e.getReference()).isSameAs(REF);
            }
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        }
    }

    @Test
    public final void testReadMultipleResults() throws Exception {
        try (ConnectionEntryReader reader = newReader(ENTRY1, ENTRY2, REF, ENTRY3, SUCCESS)) {
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
        }
    }

    @Test
    public final void testReadReference() throws Exception {
        try (ConnectionEntryReader reader = newReader(REF, SUCCESS)) {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.isEntry()).isFalse();
            assertThat(reader.isReference()).isTrue();
            assertThat(reader.readReference()).isSameAs(REF);
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        }
    }

    @Test
    public final void testReadReferenceWhenEntry() throws Exception {
        try (ConnectionEntryReader reader = newReader(ENTRY1, SUCCESS)) {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.readReference()).isNull();
            assertThat(reader.readEntry()).isSameAs(ENTRY1);
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        }
    }

    @Test
    public final void testReadReferenceWhenError() throws Exception {
        try (ConnectionEntryReader reader = newReader(ERROR)) {
            reader.readReference();
            fail();
        } catch (final LdapException e) {
            assertThat(e.getResult()).isSameAs(ERROR);
        }
    }

    @Test(expectedExceptions = NoSuchElementException.class)
    public final void testReadReferenceWhenNoMore() throws Exception {
        try (ConnectionEntryReader reader = newReader(SUCCESS)) {
            assertThat(reader.hasNext()).isFalse();
            reader.readReference();
        }
    }

    @Test
    public final void testReadResult() throws Exception {
        try (ConnectionEntryReader reader = newReader(SUCCESS)) {
            assertThat(reader.readResult()).isSameAs(SUCCESS);
        }
    }

    @Test
    public final void testReadResultWhenError() throws Exception {
        try (ConnectionEntryReader reader = newReader(ERROR)) {
            reader.readResult();
            fail();
        } catch (final LdapException e) {
            assertThat(e.getResult()).isSameAs(ERROR);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public final void testReadResultWhenEntry() throws Exception {
        try (ConnectionEntryReader reader = newReader(ENTRY1, SUCCESS)) {
            reader.readResult();
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
                        for (final Object response : responses) {
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
