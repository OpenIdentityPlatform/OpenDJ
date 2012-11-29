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
 *      Portions copyright 2011 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.CompletedFutureResult;

/**
 * Unit test for AbstractAsynchronousConnection. The tests verify that all
 * synchronous operation methods delegate to the equivalent asynchronous method.
 */
@SuppressWarnings({ "javadoc", "resource" })
public class AbstractAsynchronousConnectionTestCase extends SdkTestCase {

    private final class MockConnection extends AbstractAsynchronousConnection {
        private final ResultCode resultCode;
        private final SearchResultEntry entry;

        private MockConnection(ResultCode resultCode) {
            this(resultCode, null);
        }

        private MockConnection(ResultCode resultCode, SearchResultEntry entry) {
            this.resultCode = resultCode;
            this.entry = entry;
        }

        /**
         * {@inheritDoc}
         */
        public FutureResult<Void> abandonAsync(AbandonRequest request) {
            if (!resultCode.isExceptional()) {
                return new CompletedFutureResult<Void>((Void) null);
            } else {
                return new CompletedFutureResult<Void>(newErrorResult(resultCode));
            }
        }

        /**
         * {@inheritDoc}
         */
        public FutureResult<Result> addAsync(AddRequest request,
                IntermediateResponseHandler intermediateResponseHandler,
                ResultHandler<? super Result> resultHandler) {
            if (!resultCode.isExceptional()) {
                return new CompletedFutureResult<Result>(Responses.newResult(resultCode));
            } else {
                return new CompletedFutureResult<Result>(newErrorResult(resultCode));
            }
        }

        /**
         * {@inheritDoc}
         */
        public void addConnectionEventListener(ConnectionEventListener listener) {
            // Do nothing.
        }

        /**
         * {@inheritDoc}
         */
        public FutureResult<BindResult> bindAsync(BindRequest request,
                IntermediateResponseHandler intermediateResponseHandler,
                ResultHandler<? super BindResult> resultHandler) {
            if (!resultCode.isExceptional()) {
                return new CompletedFutureResult<BindResult>(Responses.newBindResult(resultCode));
            } else {
                return new CompletedFutureResult<BindResult>(newErrorResult(resultCode));
            }
        }

        /**
         * {@inheritDoc}
         */
        public void close(UnbindRequest request, String reason) {
            // Do nothing.
        }

        /**
         * {@inheritDoc}
         */
        public FutureResult<CompareResult> compareAsync(CompareRequest request,
                IntermediateResponseHandler intermediateResponseHandler,
                ResultHandler<? super CompareResult> resultHandler) {
            if (!resultCode.isExceptional()) {
                return new CompletedFutureResult<CompareResult>(Responses
                        .newCompareResult(resultCode));
            } else {
                return new CompletedFutureResult<CompareResult>(newErrorResult(resultCode));
            }
        }

        /**
         * {@inheritDoc}
         */
        public FutureResult<Result> deleteAsync(DeleteRequest request,
                IntermediateResponseHandler intermediateResponseHandler,
                ResultHandler<? super Result> resultHandler) {
            if (!resultCode.isExceptional()) {
                return new CompletedFutureResult<Result>(Responses.newResult(resultCode));
            } else {
                return new CompletedFutureResult<Result>(newErrorResult(resultCode));
            }
        }

        /**
         * {@inheritDoc}
         */
        public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
                ExtendedRequest<R> request,
                IntermediateResponseHandler intermediateResponseHandler,
                ResultHandler<? super R> resultHandler) {
            if (!resultCode.isExceptional()) {
                return new CompletedFutureResult<R>(request.getResultDecoder()
                        .newExtendedErrorResult(resultCode, "", ""));
            } else {
                return new CompletedFutureResult<R>(newErrorResult(resultCode));
            }
        }

        /**
         * {@inheritDoc}
         */
        public boolean isClosed() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isValid() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        public FutureResult<Result> modifyAsync(ModifyRequest request,
                IntermediateResponseHandler intermediateResponseHandler,
                ResultHandler<? super Result> resultHandler) {
            if (!resultCode.isExceptional()) {
                return new CompletedFutureResult<Result>(Responses.newResult(resultCode));
            } else {
                return new CompletedFutureResult<Result>(newErrorResult(resultCode));
            }
        }

        /**
         * {@inheritDoc}
         */
        public FutureResult<Result> modifyDNAsync(ModifyDNRequest request,
                IntermediateResponseHandler intermediateResponseHandler,
                ResultHandler<? super Result> resultHandler) {
            if (!resultCode.isExceptional()) {
                return new CompletedFutureResult<Result>(Responses.newResult(resultCode));
            } else {
                return new CompletedFutureResult<Result>(newErrorResult(resultCode));
            }
        }

        /**
         * {@inheritDoc}
         */
        public void removeConnectionEventListener(ConnectionEventListener listener) {
            // Do nothing.
        }

        /**
         * {@inheritDoc}
         */
        public FutureResult<Result> searchAsync(SearchRequest request,
                IntermediateResponseHandler intermediateResponseHandler,
                SearchResultHandler resultHandler) {
            if (entry != null) {
                resultHandler.handleEntry(entry);
            }

            if (!resultCode.isExceptional()) {
                return new CompletedFutureResult<Result>(Responses.newResult(resultCode));
            } else {
                return new CompletedFutureResult<Result>(newErrorResult(resultCode));
            }
        }

        /**
         * {@inheritDoc}
         */
        public String toString() {
            return "MockConnection";
        }

    }

    @Test()
    public void testAddRequestSuccess() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.SUCCESS);
        final AddRequest addRequest = Requests.newAddRequest("cn=test");
        assertThat(mockConnection.add(addRequest).getResultCode()).isEqualTo(ResultCode.SUCCESS);
    }

    @Test()
    public void testAddRequestFail() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.UNWILLING_TO_PERFORM);
        final AddRequest addRequest = Requests.newAddRequest("cn=test");
        try {
            mockConnection.add(addRequest);
            fail();
        } catch (ErrorResultException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.UNWILLING_TO_PERFORM);
        }
    }

    @Test()
    public void testBindRequestSuccess() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.SUCCESS);
        final BindRequest bindRequest = Requests.newSimpleBindRequest();
        assertThat(mockConnection.bind(bindRequest).getResultCode()).isEqualTo(ResultCode.SUCCESS);
    }

    @Test()
    public void testBindRequestFail() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.UNWILLING_TO_PERFORM);
        final BindRequest bindRequest = Requests.newSimpleBindRequest();
        try {
            mockConnection.bind(bindRequest);
            fail();
        } catch (ErrorResultException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.UNWILLING_TO_PERFORM);
        }
    }

    @Test()
    public void testCompareRequestSuccess() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.SUCCESS);
        final CompareRequest compareRequest = Requests.newCompareRequest("cn=test", "cn", "test");
        assertThat(mockConnection.compare(compareRequest).getResultCode()).isEqualTo(
                ResultCode.SUCCESS);
    }

    @Test()
    public void testCompareRequestFail() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.UNWILLING_TO_PERFORM);
        final CompareRequest compareRequest = Requests.newCompareRequest("cn=test", "cn", "test");
        try {
            mockConnection.compare(compareRequest);
            fail();
        } catch (ErrorResultException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.UNWILLING_TO_PERFORM);
        }
    }

    @Test()
    public void testDeleteRequestSuccess() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.SUCCESS);
        final DeleteRequest deleteRequest = Requests.newDeleteRequest("cn=test");
        assertThat(mockConnection.delete(deleteRequest).getResultCode()).isEqualTo(
                ResultCode.SUCCESS);
    }

    @Test()
    public void testDeleteRequestFail() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.UNWILLING_TO_PERFORM);
        final DeleteRequest deleteRequest = Requests.newDeleteRequest("cn=test");
        try {
            mockConnection.delete(deleteRequest);
            fail();
        } catch (ErrorResultException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.UNWILLING_TO_PERFORM);
        }
    }

    @Test()
    public void testExtendedRequestSuccess() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.SUCCESS);
        final GenericExtendedRequest extendedRequest = Requests.newGenericExtendedRequest("test");
        assertThat(mockConnection.extendedRequest(extendedRequest).getResultCode()).isEqualTo(
                ResultCode.SUCCESS);
    }

    @Test()
    public void testExtendedRequestFail() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.UNWILLING_TO_PERFORM);
        final GenericExtendedRequest extendedRequest = Requests.newGenericExtendedRequest("test");
        try {
            mockConnection.extendedRequest(extendedRequest);
            fail();
        } catch (ErrorResultException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.UNWILLING_TO_PERFORM);
        }
    }

    @Test()
    public void testModifyRequestSuccess() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.SUCCESS);
        final ModifyRequest modifyRequest = Requests.newModifyRequest("cn=test");
        assertThat(mockConnection.modify(modifyRequest).getResultCode()).isEqualTo(
                ResultCode.SUCCESS);
    }

    @Test()
    public void testModifyRequestFail() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.UNWILLING_TO_PERFORM);
        final ModifyRequest modifyRequest = Requests.newModifyRequest("cn=test");
        try {
            mockConnection.modify(modifyRequest);
            fail();
        } catch (ErrorResultException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.UNWILLING_TO_PERFORM);
        }
    }

    @Test()
    public void testModifyDNRequestSuccess() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.SUCCESS);
        final ModifyDNRequest modifyDNRequest = Requests.newModifyDNRequest("cn=test", "cn=newrdn");
        assertThat(mockConnection.modifyDN(modifyDNRequest).getResultCode()).isEqualTo(
                ResultCode.SUCCESS);
    }

    @Test()
    public void testModifyDNRequestFail() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.UNWILLING_TO_PERFORM);
        final ModifyDNRequest modifyDNRequest = Requests.newModifyDNRequest("cn=test", "cn=newrdn");
        try {
            mockConnection.modifyDN(modifyDNRequest);
            fail();
        } catch (ErrorResultException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.UNWILLING_TO_PERFORM);
        }
    }

    @Test()
    public void testSearchRequestSuccess() throws Exception {
        final SearchResultEntry entry = Responses.newSearchResultEntry("cn=test");
        final Connection mockConnection = new MockConnection(ResultCode.SUCCESS, entry);
        final SearchRequest searchRequest =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");
        List<SearchResultEntry> entries = new LinkedList<SearchResultEntry>();
        assertThat(mockConnection.search(searchRequest, entries).getResultCode()).isEqualTo(
                ResultCode.SUCCESS);
        assertThat(entries.size()).isEqualTo(1);
        assertThat(entries.iterator().next()).isSameAs(entry);
    }

    @Test()
    public void testSearchRequestFail() throws Exception {
        final Connection mockConnection = new MockConnection(ResultCode.UNWILLING_TO_PERFORM);
        final SearchRequest searchRequest =
                Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(objectClass=*)");
        List<SearchResultEntry> entries = new LinkedList<SearchResultEntry>();
        try {
            mockConnection.search(searchRequest, entries);
            fail();
        } catch (ErrorResultException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.UNWILLING_TO_PERFORM);
            assertThat(entries.isEmpty());
        }
    }
}
