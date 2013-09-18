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
 *      Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;
import static org.forgerock.opendj.ldap.SearchScope.BASE_OBJECT;
import static org.forgerock.opendj.ldap.TestCaseUtils.mockConnection;
import static org.forgerock.opendj.ldap.TestCaseUtils.mockConnectionFactory;
import static org.forgerock.opendj.ldap.TestCaseUtils.mockTimeSource;
import static org.forgerock.opendj.ldap.requests.Requests.newModifyRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.forgerock.opendj.ldap.responses.Responses.newResult;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.CompletedFutureResult;
import com.forgerock.opendj.util.StaticUtils;

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
    @BeforeClass()
    public void disableLogging() {
        StaticUtils.DEBUG_LOG.setLevel(Level.SEVERE);
    }

    /**
     * Re-enable logging after the tests.
     */
    @AfterClass()
    public void enableLogging() {
        StaticUtils.DEBUG_LOG.setLevel(Level.INFO);
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

    @SuppressWarnings("unchecked")
    @Test
    public void testBindWhileHeartBeatInProgress() throws Exception {
        // Mock connection with successful heartbeat.
        init(ResultCode.SUCCESS);

        // Get a connection.
        hbc = hbcf.getConnection();

        /*
         * Send a heartbeat, trapping the search call-back so that we can send
         * the response once we have attempted a bind.
         */
        when(
                connection.searchAsync(any(SearchRequest.class),
                        any(IntermediateResponseHandler.class), any(SearchResultHandler.class)))
                .thenReturn(null);
        when(hbcf.timeSource.currentTimeMillis()).thenReturn(11000L);
        scheduler.runAllTasks(); // Send the heartbeat.

        // Capture the heartbeat search result handler.
        final ArgumentCaptor<SearchResultHandler> arg =
                ArgumentCaptor.forClass(SearchResultHandler.class);
        verify(connection, times(2)).searchAsync(same(HEARTBEAT),
                any(IntermediateResponseHandler.class), arg.capture());
        assertThat(hbc.isValid()).isTrue(); // Not checked yet.

        /*
         * Now attempt a bind request, which should be held in a queue until the
         * heart beat completes.
         */
        hbc.bindAsync(newSimpleBindRequest(), null, null);
        verify(connection, times(0)).bindAsync(any(BindRequest.class),
                any(IntermediateResponseHandler.class), any(ResultHandler.class));

        // Send fake heartbeat response, releasing the bind request.
        arg.getValue().handleResult(newResult(ResultCode.SUCCESS));
        verify(connection, times(1)).bindAsync(any(BindRequest.class),
                any(IntermediateResponseHandler.class), any(ResultHandler.class));
    }

    @Test
    public void testGetConnection() throws Exception {
        // Mock connection with successful initial heartbeat.
        init(ResultCode.SUCCESS);
        hbc = hbcf.getConnection();
        assertThat(hbc).isNotNull();
        assertThat(hbc.isValid()).isTrue();
    }

    @Test
    public void testGetConnectionAsync() throws Exception {
        // Mock connection with successful initial heartbeat.
        init(ResultCode.SUCCESS);
        hbc = hbcf.getConnectionAsync(null).get();
        assertThat(hbc).isNotNull();
        assertThat(hbc.isValid()).isTrue();
    }

    @Test
    public void testGetConnectionAsyncWithInitialHeartBeatError() throws Exception {
        // Mock connection with failing initial heartbeat.
        init(ResultCode.BUSY);
        try {
            hbcf.getConnectionAsync(null).get();
            fail("Unexpectedly obtained a connection");
        } catch (final ErrorResultException e) {
            checkInitialHeartBeatFailure(e);
        }
    }

    @Test
    public void testGetConnectionWithInitialHeartBeatError() throws Exception {
        // Mock connection with failing initial heartbeat.
        init(ResultCode.BUSY);
        try {
            hbcf.getConnection();
            fail("Unexpectedly obtained a connection");
        } catch (final ErrorResultException e) {
            checkInitialHeartBeatFailure(e);
        }
    }

    @Test
    public void testHeartBeatSucceedsThenFails() throws Exception {
        // Mock connection with successful heartbeat.
        init(ResultCode.SUCCESS);

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
        when(hbcf.timeSource.currentTimeMillis()).thenReturn(6000L);
        scheduler.runAllTasks();
        verifyHeartBeatSent(connection, 2); // Heartbeat sent.
        assertThat(hbc.isValid()).isTrue();

        // Now force the heartbeat to fail.
        mockHeartBeatResponse(connection, listeners, ResultCode.CLIENT_SIDE_SERVER_DOWN);
        when(hbcf.timeSource.currentTimeMillis()).thenReturn(11000L);
        scheduler.runAllTasks();
        verifyHeartBeatSent(connection, 3);
        assertThat(hbc.isValid()).isFalse();

        // Flush redundant timeout tasks.
        scheduler.runAllTasks();

        // Attempt to send a new request: it should fail immediately.
        @SuppressWarnings("unchecked")
        final ResultHandler<Result> mockHandler = mock(ResultHandler.class);
        hbc.modifyAsync(newModifyRequest(DN.rootDN()), null, mockHandler);
        final ArgumentCaptor<ErrorResultException> arg =
                ArgumentCaptor.forClass(ErrorResultException.class);
        verify(mockHandler).handleErrorResult(arg.capture());
        assertThat(arg.getValue().getResult().getResultCode()).isEqualTo(
                ResultCode.CLIENT_SIDE_SERVER_DOWN);

        assertThat(hbc.isValid()).isFalse();
        assertThat(hbc.isClosed()).isFalse();
    }

    @Test
    public void testHeartBeatTimeout() throws Exception {
        // Mock connection with successful heartbeat.
        init(ResultCode.SUCCESS);

        // Get a connection and check that it was pinged.
        hbc = hbcf.getConnection();

        // Now force the heartbeat to fail due to timeout.
        mockHeartBeatResponse(connection, listeners, null /* no response */);
        when(hbcf.timeSource.currentTimeMillis()).thenReturn(11000L);
        scheduler.runAllTasks(); // Send the heartbeat.
        verifyHeartBeatSent(connection, 2);
        assertThat(hbc.isValid()).isTrue(); // Not checked yet.
        when(hbcf.timeSource.currentTimeMillis()).thenReturn(12000L);
        scheduler.runAllTasks(); // Check for heartbeat.
        assertThat(hbc.isValid()).isFalse(); // Now invalid.
        assertThat(hbc.isClosed()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testHeartBeatWhileBindInProgress() throws Exception {
        // Mock connection with successful heartbeat.
        init(ResultCode.SUCCESS);

        // Get a connection.
        hbc = hbcf.getConnection();

        /*
         * Send a bind request, trapping the bind call-back so that we can send
         * the response once we have attempted a heartbeat.
         */
        when(
                connection.bindAsync(any(BindRequest.class),
                        any(IntermediateResponseHandler.class), any(ResultHandler.class)))
                .thenReturn(null);
        hbc.bindAsync(newSimpleBindRequest(), null, null);

        // Capture the bind result handler.
        @SuppressWarnings("rawtypes")
        final ArgumentCaptor<ResultHandler> arg = ArgumentCaptor.forClass(ResultHandler.class);
        verify(connection, times(1)).bindAsync(any(BindRequest.class),
                any(IntermediateResponseHandler.class), arg.capture());

        /*
         * Now attempt the heartbeat which should not happen because there is a
         * bind in progress.
         */
        when(hbcf.timeSource.currentTimeMillis()).thenReturn(11000L);
        scheduler.runAllTasks(); // Attempt to send the heartbeat.
        verify(connection, times(1)).searchAsync(same(HEARTBEAT),
                any(IntermediateResponseHandler.class), any(SearchResultHandler.class));

        // Send fake bind response, releasing the heartbeat.
        arg.getValue().handleResult(newResult(ResultCode.SUCCESS));

        // Attempt to send a heartbeat again.
        when(hbcf.timeSource.currentTimeMillis()).thenReturn(16000L);
        scheduler.runAllTasks(); // Attempt to send the heartbeat.
        verify(connection, times(2)).searchAsync(same(HEARTBEAT),
                any(IntermediateResponseHandler.class), any(SearchResultHandler.class));
    }

    @Test
    public void testToString() {
        init(ResultCode.SUCCESS);
        assertThat(hbcf.toString()).isNotNull();
    }

    private void checkInitialHeartBeatFailure(final ErrorResultException e) {
        /*
         * Initial heartbeat failure should trigger connection exception with
         * heartbeat cause.
         */
        assertThat(e).isInstanceOf(ConnectionException.class);
        assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        assertThat(e.getCause()).isInstanceOf(ErrorResultException.class);
        assertThat(((ErrorResultException) e.getCause()).getResult().getResultCode()).isEqualTo(
                ResultCode.BUSY);
    }

    private void init(final ResultCode initialHeartBeatResult) {
        // Mock connection with successful heartbeat.
        listeners = new LinkedList<ConnectionEventListener>();
        connection = mockConnection(listeners);
        when(connection.isValid()).thenReturn(true);
        mockHeartBeatResponse(connection, listeners, initialHeartBeatResult);

        // Underlying connection factory.
        factory = mockConnectionFactory(connection);

        // Create heart beat connection factory.
        scheduler = new MockScheduler();
        hbcf =
                new HeartBeatConnectionFactory(factory, 10000, 100, TimeUnit.MILLISECONDS,
                        HEARTBEAT, scheduler);

        // Set initial time stamp.
        hbcf.timeSource = mockTimeSource(0);
    }

    private Connection mockHeartBeatResponse(final Connection mockConnection,
            final List<ConnectionEventListener> listeners, final ResultCode resultCode) {
        doAnswer(new Answer<FutureResult<Result>>() {
            @Override
            public FutureResult<Result> answer(final InvocationOnMock invocation) throws Throwable {
                if (resultCode == null) {
                    return null;
                }

                final SearchResultHandler handler =
                        (SearchResultHandler) invocation.getArguments()[2];
                if (resultCode.isExceptional()) {
                    final ErrorResultException error = newErrorResult(resultCode);
                    if (handler != null) {
                        handler.handleErrorResult(error);
                    }
                    if (error instanceof ConnectionException) {
                        for (final ConnectionEventListener listener : listeners) {
                            listener.handleConnectionError(false, error);
                        }
                    }
                    return new CompletedFutureResult<Result>(error);
                } else {
                    final Result result = newResult(resultCode);
                    if (handler != null) {
                        handler.handleResult(result);
                    }
                    return new CompletedFutureResult<Result>(result);
                }
            }
        }).when(mockConnection).searchAsync(any(SearchRequest.class),
                any(IntermediateResponseHandler.class), any(SearchResultHandler.class));
        return mockConnection;
    }

    private void verifyHeartBeatSent(final Connection connection, final int times) {
        verify(connection, times(times)).searchAsync(same(HEARTBEAT),
                any(IntermediateResponseHandler.class), any(SearchResultHandler.class));
    }
}
