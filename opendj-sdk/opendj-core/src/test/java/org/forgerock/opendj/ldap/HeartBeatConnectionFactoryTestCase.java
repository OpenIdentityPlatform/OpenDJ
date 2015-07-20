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
 *      Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.spi.BindResultLdapPromiseImpl;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.Fail.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.TestCaseUtils.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.ldap.responses.Responses.*;
import static org.forgerock.opendj.ldap.spi.LdapPromiseImpl.*;
import static org.forgerock.opendj.ldap.spi.LdapPromises.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the connection pool implementation..
 */
@SuppressWarnings("javadoc")
public class HeartBeatConnectionFactoryTestCase extends SdkTestCase {

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

    private static final SearchRequest HEARTBEAT = newSearchRequest("dc=test", BASE_OBJECT,
            "(objectclass=*)", "1.1");

    private Connection connection;
    private ConnectionFactory factory;
    private Connection hbc;
    private HeartBeatConnectionFactory hbcf;
    private List<ConnectionEventListener> listeners;
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
            factory = null;
            hbcf = null;
            listeners = null;
            scheduler = null;
            hbc = null;
        }
    }

    @Test
    public void testBindWhileHeartBeatInProgress() throws Exception {
        mockConnectionWithInitialHeartbeatResult(ResultCode.SUCCESS);
        mockBindAsyncResponse();
        hbc = hbcf.getConnection();

        /*
         * Send a heartbeat, trapping the search call-back so that we can send
         * the response once we have attempted a bind.
         */
        when(connection.searchAsync(any(SearchRequest.class), any(SearchResultHandler.class))).thenReturn(
            newSuccessfulLdapPromise(newResult(SUCCESS)));
        when(hbcf.timeService.now()).thenReturn(11000L);
        scheduler.runAllTasks(); // Send the heartbeat.

        // Capture the heartbeat search result handler.
        verify(connection, times(2)).searchAsync(same(HEARTBEAT), any(SearchResultHandler.class));
        assertThat(hbc.isValid()).isTrue(); // Not checked yet.

        /*
         * Now attempt a bind request, which should be held in a queue until the
         * heart beat completes.
         */
        hbc.bindAsync(newSimpleBindRequest());
        verify(connection, times(0)).bindAsync(any(BindRequest.class));

        // Send fake heartbeat response, releasing the bind request.
        verify(connection, times(1)).bindAsync(any(BindRequest.class), any(IntermediateResponseHandler.class));
    }

    @Test
    public void testGetConnection() throws Exception {
        mockConnectionWithInitialHeartbeatResult(ResultCode.SUCCESS);
        hbc = hbcf.getConnection();
        assertThat(hbc).isNotNull();
        assertThat(hbc.isValid()).isTrue();
    }

    @Test
    public void testGetConnectionAsync() throws Exception {
        @SuppressWarnings("unchecked")
        final ResultHandler<Connection> mockResultHandler = mock(ResultHandler.class);

        mockConnectionWithInitialHeartbeatResult(ResultCode.SUCCESS);
        hbc = hbcf.getConnectionAsync().thenOnResult(mockResultHandler).getOrThrow();
        assertThat(hbc).isNotNull();
        assertThat(hbc.isValid()).isTrue();

        verify(mockResultHandler).handleResult(any(Connection.class));
        verifyNoMoreInteractions(mockResultHandler);
    }

    @Test
    public void testGetConnectionAsyncWithInitialHeartBeatError() throws Exception {
        @SuppressWarnings("unchecked")
        final ResultHandler<Connection> mockResultHandler = mock(ResultHandler.class);
        final PromiseImpl<LdapException, NeverThrowsException> promisedError = PromiseImpl.create();

        mockConnectionWithInitialHeartbeatResult(ResultCode.BUSY);
        Promise<? extends Connection, LdapException> promise = hbcf.getConnectionAsync();
        promise.thenOnResult(mockResultHandler).thenOnException(new ExceptionHandler<LdapException>() {
            @Override
            public void handleException(LdapException exception) {
                promisedError.handleResult(exception);
            }
        });

        checkInitialHeartBeatFailure(promisedError.getOrThrow());

        try {
            promise.getOrThrow();
            fail("Unexpectedly obtained a connection");
        } catch (final LdapException e) {
            checkInitialHeartBeatFailure(e);
        }

        verifyNoMoreInteractions(mockResultHandler);
    }

    @Test
    public void testGetConnectionWithInitialHeartBeatError() throws Exception {
        mockConnectionWithInitialHeartbeatResult(ResultCode.BUSY);
        try {
            hbcf.getConnection();
            fail("Unexpectedly obtained a connection");
        } catch (final LdapException e) {
            checkInitialHeartBeatFailure(e);
        }
    }

    @Test
    public void testHeartBeatSucceedsThenFails() throws Exception {
        mockConnectionWithInitialHeartbeatResult(ResultCode.SUCCESS);

        // Get a connection and check that it was pinged.
        hbc = hbcf.getConnection();

        verifyHeartBeatSent(connection, 1);
        assertThat(scheduler.isScheduled()).isTrue(); // heartbeater
        assertThat(listeners).hasSize(1);
        assertThat(hbc.isValid()).isTrue();

        // Invoke heartbeat before the connection is considered idle.
        scheduler.runAllTasks();
        verifyHeartBeatSent(connection, 1); // No heartbeat sent - not idle yet.
        assertThat(hbc.isValid()).isTrue();

        // Invoke heartbeat after the connection is considered idle.
        when(hbcf.timeService.now()).thenReturn(6000L);
        scheduler.runAllTasks();
        verifyHeartBeatSent(connection, 2); // Heartbeat sent.
        assertThat(hbc.isValid()).isTrue();

        // Now force the heartbeat to fail.
        mockHeartBeatResponse(connection, listeners, ResultCode.CLIENT_SIDE_SERVER_DOWN);
        when(hbcf.timeService.now()).thenReturn(11000L);
        scheduler.runAllTasks();
        verifyHeartBeatSent(connection, 3);
        assertThat(hbc.isValid()).isFalse();

        // Flush redundant timeout tasks.
        scheduler.runAllTasks();

        // Attempt to send a new request: it should fail immediately.
        @SuppressWarnings("unchecked")
        final ExceptionHandler<LdapException> mockHandler = mock(ExceptionHandler.class);
        hbc.modifyAsync(newModifyRequest(DN.rootDN())).thenOnException(mockHandler);
        final ArgumentCaptor<LdapException> arg = ArgumentCaptor.forClass(LdapException.class);
        verify(mockHandler).handleException(arg.capture());
        assertThat(arg.getValue().getResult().getResultCode()).isEqualTo(
                ResultCode.CLIENT_SIDE_SERVER_DOWN);

        assertThat(hbc.isValid()).isFalse();
        assertThat(hbc.isClosed()).isFalse();
    }

    @Test
    public void testHeartBeatTimeout() throws Exception {
        mockConnectionWithInitialHeartbeatResult(ResultCode.SUCCESS);

        // Get a connection and check that it was pinged.
        hbc = hbcf.getConnection();

        // Now force the heartbeat to fail due to timeout.
        mockHeartBeatResponse(connection, listeners, null /* no response */);
        when(hbcf.timeService.now()).thenReturn(11000L);
        scheduler.runAllTasks(); // Send the heartbeat.
        verifyHeartBeatSent(connection, 2);
        assertThat(hbc.isValid()).isTrue(); // Not checked yet.
        when(hbcf.timeService.now()).thenReturn(12000L);
        scheduler.runAllTasks(); // Check for heartbeat.
        assertThat(hbc.isValid()).isFalse(); // Now invalid.
        assertThat(hbc.isClosed()).isFalse();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test(description = "OPENDJ-1348")
    public void testBindPreventsHeartBeatTimeout() throws Exception {
        mockConnectionWithInitialHeartbeatResult(ResultCode.SUCCESS);
        BindResultLdapPromiseImpl promise = mockBindAsyncResponse();
        hbc = hbcf.getConnection();

        /*
         * Send a bind request, trapping the bind call-back so that we can send
         * the response once we have attempted a heartbeat.
         */
        when(hbcf.timeService.now()).thenReturn(11000L);
        hbc.bindAsync(newSimpleBindRequest());

        verify(connection, times(1)).bindAsync(any(BindRequest.class), any(IntermediateResponseHandler.class));

        // Verify no heartbeat is sent because there is a bind in progress.
        when(hbcf.timeService.now()).thenReturn(11001L);
        scheduler.runAllTasks(); // Invokes HBCF.ConnectionImpl.sendHeartBeat()
        verify(connection, times(1)).searchAsync(same(HEARTBEAT), any(SearchResultHandler.class));

        // Send fake bind response, releasing the heartbeat.
        when(hbcf.timeService.now()).thenReturn(11099L);
        ((PromiseImpl) promise.getWrappedPromise()).handleResult(newResult(SUCCESS));

        // Check that bind response acts as heartbeat.
        assertThat(hbc.isValid()).isTrue();
        when(hbcf.timeService.now()).thenReturn(11100L);
        scheduler.runAllTasks(); // Invokes HBCF.ConnectionImpl.checkForHeartBeat()
        assertThat(hbc.isValid()).isTrue();
    }

    @Test(description = "OPENDJ-1348")
    public void testBindTriggersHeartBeatTimeoutWhenTooSlow() throws Exception {
        mockConnectionWithInitialHeartbeatResult(ResultCode.SUCCESS);
        mockBindAsyncResponse();
        hbc = hbcf.getConnection();

        // Send another bind request which will timeout.
        when(hbcf.timeService.now()).thenReturn(20000L);
        hbc.bindAsync(newSimpleBindRequest());
        verify(connection, times(1)).bindAsync(any(BindRequest.class), any(IntermediateResponseHandler.class));

        // Verify no heartbeat is sent because there is a bind in progress.
        when(hbcf.timeService.now()).thenReturn(20001L);
        scheduler.runAllTasks(); // Invokes HBCF.ConnectionImpl.sendHeartBeat()
        verify(connection, times(1)).searchAsync(same(HEARTBEAT), any(SearchResultHandler.class));

        // Check that lack of bind response acts as heartbeat timeout.
        assertThat(hbc.isValid()).isTrue();
        when(hbcf.timeService.now()).thenReturn(20100L);
        scheduler.runAllTasks(); // Invokes HBCF.ConnectionImpl.checkForHeartBeat()
        assertThat(hbc.isValid()).isFalse();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testHeartBeatWhileBindInProgress() throws Exception {
        mockConnectionWithInitialHeartbeatResult(ResultCode.SUCCESS);
        //Trapping the callback of mockedConnection.bindAsync
        BindResultLdapPromiseImpl promise = mockBindAsyncResponse();
        hbc = hbcf.getConnection();
        hbc.bindAsync(newSimpleBindRequest());

        verify(connection, times(1)).bindAsync(any(BindRequest.class), any(IntermediateResponseHandler.class));

        /*
         * Now attempt the heartbeat which should not happen because there is a
         * bind in progress.
         */
        when(hbcf.timeService.now()).thenReturn(11000L);
        // Attempt to send the heartbeat.
        scheduler.runAllTasks();
        verify(connection, times(1)).searchAsync(same(HEARTBEAT), any(SearchResultHandler.class));

        // Send fake bind response, releasing the heartbeat.
        ((PromiseImpl) promise.getWrappedPromise()).handleResult(newResult(SUCCESS));

        // Attempt to send a heartbeat again.
        when(hbcf.timeService.now()).thenReturn(16000L);
        // Attempt to send the heartbeat.
        scheduler.runAllTasks();
        verify(connection, times(2)).searchAsync(same(HEARTBEAT), any(SearchResultHandler.class));
    }

    @Test
    public void testToString() {
        mockConnectionWithInitialHeartbeatResult(ResultCode.SUCCESS);
        assertThat(hbcf.toString()).isNotNull();
    }

    private void checkInitialHeartBeatFailure(final LdapException e) {
        /*
         * Initial heartbeat failure should trigger connection exception with
         * heartbeat cause.
         */
        assertThat(e).isInstanceOf(ConnectionException.class);
        assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        assertThat(e.getCause()).isInstanceOf(LdapException.class);
        assertThat(((LdapException) e.getCause()).getResult().getResultCode()).isEqualTo(ResultCode.BUSY);
    }

    private void mockConnectionWithInitialHeartbeatResult(final ResultCode initialHeartBeatResult) {
        listeners = new LinkedList<>();
        connection = mockConnection(listeners);
        when(connection.isValid()).thenReturn(true);
        mockHeartBeatResponse(connection, listeners, initialHeartBeatResult);

        // Underlying connection factory.
        factory = mockConnectionFactory(connection);

        // Create heart beat connection factory.
        scheduler = new MockScheduler();
        hbcf = new HeartBeatConnectionFactory(factory, 10000, 100, TimeUnit.MILLISECONDS, HEARTBEAT, scheduler);

        // Set initial time stamp.
        hbcf.timeService = mockTimeService(0);
    }

    private BindResultLdapPromiseImpl mockBindAsyncResponse() {
        final BindResultLdapPromiseImpl bindPromise = newBindLdapPromise(-1, null, null, null, connection);
        doAnswer(new Answer<LdapPromise<BindResult>>() {
            @Override
            public LdapPromise<BindResult> answer(final InvocationOnMock invocation) throws Throwable {
                return bindPromise;
            }
        }).when(connection).bindAsync(any(BindRequest.class), any(IntermediateResponseHandler.class));

        return bindPromise;
    }

    private Connection mockHeartBeatResponse(final Connection mockConnection,
            final List<ConnectionEventListener> listeners, final ResultCode resultCode) {
        Answer<LdapPromise<Result>> answer = new Answer<LdapPromise<Result>>() {
            @Override
            public LdapPromise<Result> answer(final InvocationOnMock invocation) throws Throwable {
                if (resultCode == null) {
                    return newLdapPromiseImpl();
                }

                if (resultCode.isExceptional()) {
                    final LdapException error = newLdapException(resultCode);
                    if (error instanceof ConnectionException) {
                        for (final ConnectionEventListener listener : listeners) {
                            listener.handleConnectionError(false, error);
                        }
                    }
                    return newFailedLdapPromise(error);
                } else {
                    return newSuccessfulLdapPromise(newResult(resultCode));
                }
            }
        };

        doAnswer(answer).when(mockConnection).searchAsync(any(SearchRequest.class), any(SearchResultHandler.class));
        doAnswer(answer).when(mockConnection).searchAsync(any(SearchRequest.class),
            any(IntermediateResponseHandler.class), any(SearchResultHandler.class));

        return mockConnection;
    }

    private void verifyHeartBeatSent(final Connection connection, final int times) {
        verify(connection, times(times)).searchAsync(same(HEARTBEAT), any(SearchResultHandler.class));
    }
}
