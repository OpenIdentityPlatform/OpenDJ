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
 *      Copyright 2013-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.spi;

import java.util.logging.Level;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.MockScheduler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.SuccessHandler;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.*;

import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.Fail.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.TestCaseUtils.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.ldap.responses.Responses.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the connection pool implementation..
 */
@SuppressWarnings("javadoc")
public class HeartBeatConnectionFactoryTestCase extends SdkTestCase {
    /** Test timeout in ms for tests which need to wait for simulated network events. */
    private static final long TEST_TIMEOUT_MS = 30L * 1000L;

    // @formatter:off

    /*
     * Key features which need testing:
     *
     * - lazy scheduler registration and deregistration
     * - scheduled task only sends heart-beat when connection is open
     * - connection remains valid when any response is received
     * - connection remains valid when a heart beat response is received
     * - connection becomes invalid if no response is received during timeout
     * - heart beat only sent when connection is idle
     * - slow bind / startTLS prevents heart beat
     * - slow heart beat prevents bind / start TLS
     * - support concurrent bind / start TLS
     */

    // @formatter:on

    private final class TestHeartBeatConnectionFactory extends AbstractLdapConnectionFactoryImpl {

        public TestHeartBeatConnectionFactory(String host, int port, LDAPOptions options) {
            super(host, port, options);
        }

        @Override
        protected Promise<AbstractLdapConnectionImpl<?>, LdapException> getConnectionAsync0() {
            PromiseImpl<AbstractLdapConnectionImpl<?>, LdapException> result = PromiseImpl.create();
            result.handleResult(connection);
            return result;
        }

    }

    /**
     * This class defines a test connection which simulate interactions with
     * LDAP server by returning promises for asynchronous bind and search
     * requests.
     * The promises state depends on the associated ResultCode object.
     * If a result code is not null, a completed promise is returned.
     * If a result code is null, the returned promise is pending and could be completed
     * by a call to the complete promise methods.
     */
    private final class MockHeartBeatConnectionImpl extends AbstractLdapConnectionImpl<TestHeartBeatConnectionFactory> {

        private ResultCode heartBeatRC;
        private ResultCode bindRC;
        private ResultLdapPromiseImpl<BindRequest, BindResult> bindPromise;
        private ResultLdapPromiseImpl<SearchRequest, Result> searchPromise;

        private int bindAsyncCalls;
        private int searchAsyncCalls;

        protected MockHeartBeatConnectionImpl(TestHeartBeatConnectionFactory attachedFactory,
                ResultCode initialHeartBeatResult) {
            super(attachedFactory);
            heartBeatRC = initialHeartBeatResult;
        }

        @Override
        protected LdapPromise<Void> abandonAsync0(int messageID, AbandonRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void bindAsync0(int messageID, BindRequest request, BindClient bindClient,
                IntermediateResponseHandler intermediateResponseHandler) throws LdapException {
            bindAsyncCalls++;
            if (bindRC == null) {
                bindPromise = (ResultLdapPromiseImpl<BindRequest, BindResult>) getPendingResult(messageID);
            } else {
                ResultLdapPromiseImpl<BindRequest, BindResult> promise = removePendingResult(messageID);
                if (bindRC.isExceptional()) {
                    final LdapException error = newLdapException(bindRC);
                    if (error instanceof ConnectionException) {
                        connectionErrorOccurred(false, error.getResult());
                    }
                }

                promise.setResultOrError(newBindResult(bindRC));
            }
        }

        private void completeBindPromise(ResultCode result) {
            ResultLdapPromiseImpl<BindRequest, BindResult> promise = removePendingResult(bindPromise.getRequestID());
            promise.setResultOrError(newBindResult(result));
            bindPromise = null;
        }

        @Override
        protected void close0(int messageID, UnbindRequest request, String reason) {
            //noting to do.
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <R extends Request> void writeRequest(int messageID, R request,
                IntermediateResponseHandler intermediateResponseHandler, RequestWriter<R> requestWriter)
                throws LdapException {
            if (request instanceof SearchRequest) {
                searchAsyncCalls++;
                if (heartBeatRC == null) {
                    searchPromise = (ResultLdapPromiseImpl<SearchRequest, Result>) getPendingResult(messageID);
                } else {
                    ResultLdapPromiseImpl<SearchRequest, Result> promise = removePendingResult(messageID);
                    if (heartBeatRC.isExceptional()) {
                        final LdapException error = newLdapException(heartBeatRC);
                        if (error instanceof ConnectionException) {
                            connectionErrorOccurred(false, error.getResult());
                        }
                    }

                    promise.setResultOrError(newResult(heartBeatRC));
                }
            }
        }

        public void completeSearchPromise(ResultCode resultCode) {
            ResultLdapPromiseImpl<SearchRequest, Result> promise = removePendingResult(searchPromise.getRequestID());
            promise.setResultOrError(newResult(resultCode));
            searchPromise = null;
        }

        @Override
        protected boolean isTLSEnabled() {
            return false;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }

    }

    private static final SearchRequest HEARTBEAT = newSearchRequest("dc=test", BASE_OBJECT, "(objectclass=*)", "1.1");
    private static final BindRequest BIND_REQUEST = newSimpleBindRequest();

    private MockHeartBeatConnectionImpl connection;
    private Connection hbc;
    private TestHeartBeatConnectionFactory hbcf;
    private MockScheduler scheduler;

    /**
     * Disables logging before the tests.
     */
    @BeforeClass
    public void disableLogging() {
        TestCaseUtils.setDefaultLogLevel(Level.SEVERE);
    }

    /**
     * Re-enable logging after the tests.
     */
    @AfterClass
    public void enableLogging() {
        TestCaseUtils.setDefaultLogLevel(Level.INFO);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            if (hbc != null) {
                hbc.close();
                assertThat(hbc.isValid()).isFalse();
                assertThat(hbc.isClosed()).isTrue();
            }
            scheduler.runAllTasks(); // Flush any remaining timeout tasks.
            assertThat(scheduler.isScheduled()).isFalse(); // No more connections to check.
            hbcf.close();
        } finally {
            connection = null;
            hbcf = null;
            scheduler = null;
            hbc = null;
        }
    }

    @Test
    public void testBindWhileHeartBeatInProgress() throws Exception {
        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS);
        hbc = hbcf.getConnection();

        connection.heartBeatRC = null;
        when(hbcf.timeService.now()).thenReturn(11000L);
        scheduler.runAllTasks(); // Send the heartbeat.

        // Check that connection was used for both initial request and first heart beat request.
        verifySearchAsyncCalled(2);
        assertThat(hbc.isValid()).isTrue(); // Not checked yet.

        // Now attempt a bind request, which should be held in a queue until the heart beat completes.
        hbc.bindAsync(newSimpleBindRequest());
        verifyBindAsyncCalled(0);

        // Send fake heartbeat response, releasing the bind request.
        connection.completeSearchPromise(ResultCode.SUCCESS);
        verifyBindAsyncCalled(1);
    }

    @Test
    public void testGetConnection() throws Exception {
        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS);
        hbc = hbcf.getConnection();
        assertThat(hbc).isNotNull();
        assertThat(hbc.isValid()).isTrue();
    }

    @Test
    public void testGetConnectionAsync() throws Exception {
        @SuppressWarnings("unchecked")
        final SuccessHandler<Connection> mockSuccessHandler = mock(SuccessHandler.class);

        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS);
        hbc = hbcf.getConnectionAsync().onSuccess(mockSuccessHandler).getOrThrow();
        assertThat(hbc).isNotNull();
        assertThat(hbc.isValid()).isTrue();

        verify(mockSuccessHandler).handleResult(any(Connection.class));
        verifyNoMoreInteractions(mockSuccessHandler);
    }

    @Test
    public void testGetConnectionAsyncWithInitialHeartBeatError() throws Exception {
        @SuppressWarnings("unchecked")
        final SuccessHandler<Connection> mockSuccessHandler = mock(SuccessHandler.class);
        final PromiseImpl<LdapException, NeverThrowsException> promisedError = PromiseImpl.create();

        mockConnectionWithInitialHeartBeatRC(ResultCode.BUSY);
        Promise<? extends Connection, LdapException> promise = hbcf.getConnectionAsync();
        promise.onSuccess(mockSuccessHandler).onFailure(new FailureHandler<LdapException>() {
            @Override
            public void handleError(LdapException error) {
                promisedError.handleResult(error);
            }
        });

        checkInitialHeartBeatFailure(promisedError.getOrThrow());

        try {
            promise.getOrThrow();
            fail("Unexpectedly obtained a connection");
        } catch (final LdapException e) {
            checkInitialHeartBeatFailure(e);
        }

        verifyNoMoreInteractions(mockSuccessHandler);
    }

    @Test
    public void testGetConnectionWithInitialHeartBeatError() throws Exception {
        mockConnectionWithInitialHeartBeatRC(ResultCode.BUSY);
        try {
            hbcf.getConnection();
            fail("Unexpectedly obtained a connection");
        } catch (final LdapException e) {
            checkInitialHeartBeatFailure(e);
        }
    }

    @Test
    public void testHeartBeatSucceedsThenFails() throws Exception {
        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS);

        // Get a connection and check that it was pinged.
        hbc = hbcf.getConnection();

        verifySearchAsyncCalled(1);
        assertThat(scheduler.isScheduled()).isTrue(); // heartbeater
        assertThat(hbc.isValid()).isTrue();

        // Invoke heartbeat before the connection is considered idle.
        scheduler.runAllTasks();
        verifySearchAsyncCalled(1); // No heartbeat sent - not idle yet.
        assertThat(hbc.isValid()).isTrue();

        // Invoke heartbeat after the connection is considered idle.
        when(hbcf.timeService.now()).thenReturn(6000L);
        scheduler.runAllTasks();
        verifySearchAsyncCalled(2); // Heartbeat sent.
        assertThat(hbc.isValid()).isTrue();

        // Now force the heartbeat to fail.
        connection.heartBeatRC = ResultCode.CLIENT_SIDE_SERVER_DOWN;
        when(hbcf.timeService.now()).thenReturn(11000L);
        scheduler.runAllTasks();
        verifySearchAsyncCalled(3);
        assertThat(hbc.isValid()).isFalse();

        // Flush redundant timeout tasks.
        scheduler.runAllTasks();

        // Attempt to send a new request: it should fail immediately.
        @SuppressWarnings("unchecked")
        final FailureHandler<LdapException> mockHandler = mock(FailureHandler.class);
        hbc.modifyAsync(newModifyRequest(DN.rootDN())).onFailure(mockHandler);
        final ArgumentCaptor<LdapException> arg = ArgumentCaptor.forClass(LdapException.class);
        verify(mockHandler).handleError(arg.capture());
        assertThat(arg.getValue().getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_SERVER_DOWN);

        assertThat(hbc.isValid()).isFalse();
        assertThat(hbc.isClosed()).isFalse();
    }

    @Test
    public void testHeartBeatTimeout() throws Exception {
        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS);

        // Get a connection and check that it was pinged.
        hbc = hbcf.getConnection();

        // Now force the heartbeat to fail due to timeout.
        connection.heartBeatRC = null; /* no response */
        when(hbcf.timeService.now()).thenReturn(11000L);
        scheduler.runAllTasks(); // Send the heartbeat.
        verifySearchAsyncCalled(2);
        assertThat(hbc.isValid()).isTrue(); // Not checked yet.
        when(hbcf.timeService.now()).thenReturn(12000L);
        scheduler.runAllTasks(); // Check for heartbeat.
        assertThat(hbc.isValid()).isFalse(); // Now invalid.
        assertThat(hbc.isClosed()).isFalse();
    }

    @Test(description = "OPENDJ-1348")
    public void testBindPreventsHeartBeatTimeout() throws Exception {
        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS);
        mockNoBindAsyncResponse();
        hbc = hbcf.getConnection();

        /*
         * Send a bind request, trapping the bind call-back so that we can send
         * the response once we have attempted a heartbeat.
         */
        when(hbcf.timeService.now()).thenReturn(11000L);
        hbc.bindAsync(newSimpleBindRequest());
        verifyBindAsyncCalled(1);

        // Verify no heartbeat is sent because there is a bind in progress.
        when(hbcf.timeService.now()).thenReturn(11001L);
        scheduler.runAllTasks(); // Invokes HBCF.ConnectionImpl.sendHeartBeat()
        verifySearchAsyncCalled(1);

        // Send fake bind response, releasing the heartbeat.
        when(hbcf.timeService.now()).thenReturn(11099L);
        connection.completeBindPromise(SUCCESS);

        // Check that bind response acts as heartbeat.
        assertThat(hbc.isValid()).isTrue();
        when(hbcf.timeService.now()).thenReturn(11100L);
        scheduler.runAllTasks(); // Invokes HBCF.ConnectionImpl.checkForHeartBeat()
        assertThat(hbc.isValid()).isTrue();
    }

    @Test(description = "OPENDJ-1348")
    public void testBindTriggersHeartBeatTimeoutWhenTooSlow() throws Exception {
        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS);
        mockNoBindAsyncResponse();
        hbc = hbcf.getConnection();

        // Send another bind request which will timeout.
        when(hbcf.timeService.now()).thenReturn(20000L);
        hbc.bindAsync(newSimpleBindRequest());
        verifyBindAsyncCalled(1);

        // Verify no heartbeat is sent because there is a bind in progress.
        when(hbcf.timeService.now()).thenReturn(20001L);
        scheduler.runAllTasks(); // Invokes HBCF.ConnectionImpl.sendHeartBeat()
        verifySearchAsyncCalled(1);

        // Check that lack of bind response acts as heartbeat timeout.
        assertThat(hbc.isValid()).isTrue();
        when(hbcf.timeService.now()).thenReturn(20100L);
        scheduler.runAllTasks(); // Invokes HBCF.ConnectionImpl.checkForHeartBeat()
        assertThat(hbc.isValid()).isFalse();
    }

    @Test
    public void testHeartBeatWhileBindInProgress() throws Exception {
        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS);
        //Trapping the callback of mockedConnection.bindAsync
        mockNoBindAsyncResponse();
        hbc = hbcf.getConnection();
        hbc.bindAsync(newSimpleBindRequest());

        verifyBindAsyncCalled(1);

        //Now attempt the heartbeat which should not happen because there is a bind in progress.
        when(hbcf.timeService.now()).thenReturn(11000L);
        // Attempt to send the heartbeat.
        scheduler.runAllTasks();
        verifySearchAsyncCalled(1);

        // Send fake bind response, releasing the heartbeat.
        connection.completeBindPromise(SUCCESS);

        // Attempt to send a heartbeat again.
        when(hbcf.timeService.now()).thenReturn(16000L);
        // Attempt to send the heartbeat.
        scheduler.runAllTasks();
        verifySearchAsyncCalled(2);
    }

    @Test(description = "OPENDJ-1607", timeOut = TEST_TIMEOUT_MS)
    public void testAuthenticatedTimeout() throws Exception {
        // Check that we detect timeout if server does not reply.
        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS,
                new LDAPOptions().setBindRequest(BIND_REQUEST));
        mockNoBindAsyncResponse();
        try {
            // Attempt to send a bind request.
            hbcf.getConnectionAsync().getOrThrow();
            fail("Unexpectedly obtained a connection");
        } catch (LdapException e) {
            assertThat(e).isInstanceOf(ConnectionException.class);
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        }
    }

    @Test(description = "OPENDJ-1607")
    public void testHearBeatAgainstSecureServer() throws Exception {
        // Check that the initial request is a bind request.
        mockConnectionWithInitialHeartBeatRC(ResultCode.AUTHORIZATION_DENIED,
                new LDAPOptions().setBindRequest(BIND_REQUEST));
        mockBindAsyncResponse(ResultCode.SUCCESS);
        hbc = hbcf.getConnection();
        verifyBindAsyncCalled(1);
    }

    @Test
    public void testToString() {
        mockConnectionWithInitialHeartBeatRC(ResultCode.SUCCESS);
        assertThat(hbcf.toString()).isNotNull();
    }

    private void checkInitialHeartBeatFailure(final LdapException e) {
        // Initial heartbeat failure should trigger connection exception with heartbeat cause.
        assertThat(e).isInstanceOf(ConnectionException.class);
        assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        assertThat(e.getCause()).isInstanceOf(LdapException.class);
        assertThat(((LdapException) e.getCause()).getResult().getResultCode()).isEqualTo(ResultCode.BUSY);
    }

    private void mockConnectionWithInitialHeartBeatRC(final ResultCode initialHeartBeatRC) {
        mockConnectionWithInitialHeartBeatRC(
                initialHeartBeatRC,
                new LDAPOptions().setHeartBeatInterval(10, SECONDS)
                    .setHeartBeatSearchRequest(HEARTBEAT)
                    .setTimeout(100, MILLISECONDS));
    }

    private void mockConnectionWithInitialHeartBeatRC(final ResultCode initialHeartBeatRC, LDAPOptions options) {
        // Create heart beat connection factory.
        scheduler = new MockScheduler();
        if (options.getBindRequest() == null) {
            options.setHeartBeatInterval(10000, MILLISECONDS).setHeartBeatSearchRequest(HEARTBEAT)
                    .setHeartBeatScheduler(scheduler);
        }

        options.setTimeout(100, MILLISECONDS);
        hbcf = new TestHeartBeatConnectionFactory("localhost", 1111, options);
        // Set initial time stamp.
        hbcf.timeService = mockTimeService(0);

        connection = new MockHeartBeatConnectionImpl(hbcf, initialHeartBeatRC);
    }

    private void mockNoBindAsyncResponse() {
        connection.bindRC = null;
    }

    private void mockBindAsyncResponse(final ResultCode resultCode) {
        connection.bindRC = resultCode;
    }

    private void verifyBindAsyncCalled(int times) {
        assertThat(connection.bindAsyncCalls).isEqualTo(times);
    }

    private void verifySearchAsyncCalled(final int times) {
        assertThat(connection.searchAsyncCalls).isEqualTo(times);
    }
}
