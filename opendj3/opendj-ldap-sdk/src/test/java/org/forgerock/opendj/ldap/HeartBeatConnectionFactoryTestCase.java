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
import static org.forgerock.opendj.ldap.Connections.newHeartBeatConnectionFactory;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;
import static org.forgerock.opendj.ldap.SearchScope.BASE_OBJECT;
import static org.forgerock.opendj.ldap.TestCaseUtils.mockConnection;
import static org.forgerock.opendj.ldap.TestCaseUtils.mockConnectionFactory;
import static org.forgerock.opendj.ldap.requests.Requests.newModifyRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
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

import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

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

    @Test(enabled = false)
    public void testHeartBeatTimeout() throws Exception {
        // Mock connection which never responds to heartbeat.
        final List<ConnectionEventListener> listeners = new LinkedList<ConnectionEventListener>();
        final Connection connection = mockConnection(listeners);
        when(connection.isValid()).thenReturn(true);

        // Underlying connection factory.
        final ConnectionFactory factory = mockConnectionFactory(connection);

        // Create heart beat connection factory.
        final SearchRequest hb = newSearchRequest("dc=test", BASE_OBJECT, "(objectclass=*)", "1.1");
        final MockScheduler scheduler = new MockScheduler();
        final ConnectionFactory hbcf =
                newHeartBeatConnectionFactory(factory, 0, TimeUnit.MILLISECONDS, hb, scheduler);

        // First connection should cause heart beat to be scheduled.
        final Connection hbc = hbcf.getConnection();
        assertThat(scheduler.isScheduled()).isTrue();
        assertThat(scheduler.getCommand()).isNotNull();
        assertThat(scheduler.getDelay()).isEqualTo(0);
        assertThat(scheduler.getUnit()).isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(listeners).hasSize(1);

        // The connection should be immediately invalid due to 0 timeout.
        assertThat(connection.isValid()).isTrue();
        assertThat(hbc.isValid()).isFalse();

        /*
         * Attempt to send heartbeat. This should trigger the connection to be
         * closed and all subsequent request attempts to fail.
         */
        scheduler.getCommand().run();

        // The underlying connection should have been closed.
        verify(connection).close();

        /*
         * ...and the scheduled heart beat stopped because there are no
         * remaining connections.
         */
        assertThat(scheduler.isScheduled()).isFalse();

        // Attempt to send a new request: it should fail immediately.
        @SuppressWarnings("unchecked")
        final ResultHandler<Result> mockHandler = mock(ResultHandler.class);
        hbc.modifyAsync(newModifyRequest(DN.rootDN()), null, mockHandler);
        final ArgumentCaptor<ErrorResultException> arg =
                ArgumentCaptor.forClass(ErrorResultException.class);
        verify(mockHandler).handleErrorResult(arg.capture());
        assertThat(arg.getValue().getResult().getResultCode()).isEqualTo(
                ResultCode.CLIENT_SIDE_SERVER_DOWN);
    }

    @Test
    public void testSchedulerRegistration() throws Exception {
        // Three mock connections.
        final List<ConnectionEventListener> listeners1 = new LinkedList<ConnectionEventListener>();
        final Connection connection1 = heartBeat(mockConnection(listeners1), ResultCode.SUCCESS);

        final List<ConnectionEventListener> listeners2 = new LinkedList<ConnectionEventListener>();
        final Connection connection2 = heartBeat(mockConnection(listeners2), ResultCode.SUCCESS);

        final List<ConnectionEventListener> listeners3 = new LinkedList<ConnectionEventListener>();
        final Connection connection3 = heartBeat(mockConnection(listeners3), ResultCode.SUCCESS);

        // Underlying connection factory.
        final ConnectionFactory factory =
                mockConnectionFactory(connection1, connection2, connection3);

        // Create heart beat connection factory.
        final SearchRequest hb = newSearchRequest("dc=test", BASE_OBJECT, "(objectclass=*)", "1.1");
        final MockScheduler scheduler = new MockScheduler();
        final ConnectionFactory hbcf =
                newHeartBeatConnectionFactory(factory, 0, TimeUnit.MILLISECONDS, hb, scheduler);

        // Heart beat should not be scheduled yet.
        assertThat(scheduler.isScheduled()).isFalse();

        // First connection should cause heart beat to be scheduled.
        hbcf.getConnection();
        assertThat(scheduler.isScheduled()).isTrue();
        assertThat(scheduler.getCommand()).isNotNull();
        assertThat(scheduler.getDelay()).isEqualTo(0);
        assertThat(scheduler.getUnit()).isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(listeners1).hasSize(1);

        // Second connection should not change anything.
        hbcf.getConnection();
        assertThat(scheduler.isScheduled()).isTrue();
        assertThat(listeners2).hasSize(1);

        // Check heart-beat sent to both connections.
        scheduler.getCommand().run();
        verify(connection1, times(1)).searchAsync(same(hb), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));
        verify(connection2, times(1)).searchAsync(same(hb), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));
        verify(connection3, times(0)).searchAsync(same(hb), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));

        // Close first connection: heart beat should still be scheduled.
        listeners1.get(0).handleConnectionClosed();
        assertThat(scheduler.isScheduled()).isTrue();
        assertThat(listeners1).isEmpty();

        // Check heart-beat only sent to second connection.
        scheduler.getCommand().run();
        verify(connection1, times(1)).searchAsync(same(hb), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));
        verify(connection2, times(2)).searchAsync(same(hb), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));
        verify(connection3, times(0)).searchAsync(same(hb), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));

        // Close second connection: heart beat should now be stopped.
        listeners2.get(0).handleConnectionClosed();
        assertThat(scheduler.isScheduled()).isFalse();
        assertThat(listeners2).isEmpty();

        // Opening another connection should restart the heart beat.
        hbcf.getConnection();
        assertThat(scheduler.isScheduled()).isTrue();
        assertThat(scheduler.getCommand()).isNotNull();
        assertThat(scheduler.getDelay()).isEqualTo(0);
        assertThat(scheduler.getUnit()).isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(listeners3).hasSize(1);

        // Check heart-beat only sent to third connection.
        scheduler.getCommand().run();
        verify(connection1, times(1)).searchAsync(same(hb), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));
        verify(connection2, times(2)).searchAsync(same(hb), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));
        verify(connection3, times(1)).searchAsync(same(hb), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));
    }

    private Connection heartBeat(final Connection mockConnection, final ResultCode resultCode) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                final SearchResultHandler handler =
                        (SearchResultHandler) invocation.getArguments()[2];
                if (resultCode.isExceptional()) {
                    handler.handleErrorResult(newErrorResult(resultCode));
                } else {
                    handler.handleResult(newResult(resultCode));
                }
                return null;
            }
        }).when(mockConnection).searchAsync(any(SearchRequest.class),
                any(IntermediateResponseHandler.class), any(SearchResultHandler.class));
        return mockConnection;
    }

    //    private void sleepFor(final long ms) {
    //        // Avoid premature wake-ups.
    //        final long until = System.currentTimeMillis() + ms;
    //        do {
    //            try {
    //                Thread.sleep(until - System.currentTimeMillis());
    //            } catch (final InterruptedException e) {
    //                Thread.currentThread().interrupt();
    //                return;
    //            }
    //        } while (System.currentTimeMillis() < until);
    //    }
}
