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

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.responses.Responses.newGenericExtendedResult;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;


/**
 * Tests for {@linkConnectionState}.
 */
@SuppressWarnings("javadoc")
public class ConnectionStateTest extends LDAPTestCase {
    private static final LdapException ERROR = newLdapException(ResultCode.OTHER);
    private static final LdapException LATE_ERROR = newLdapException(ResultCode.BUSY);
    private static final ExtendedResult UNSOLICITED =
            newGenericExtendedResult(ResultCode.OPERATIONS_ERROR);

    @Test
    public void testCloseEventInClosedState() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener1);
        state.notifyConnectionClosed();
        state.notifyConnectionClosed();

        assertThat(state.isValid()).isFalse();
        assertThat(state.isClosed()).isTrue();
        assertThat(state.getConnectionError()).isNull();
        verify(listener1).handleConnectionClosed();
        verifyNoMoreInteractions(listener1);

        // Listeners registered after event should be notified immediately.
        final ConnectionEventListener listener2 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener2);
        verify(listener2).handleConnectionClosed();
        verifyNoMoreInteractions(listener2);
    }

    @Test
    public void testCloseEventInErrorState() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener1);
        state.notifyConnectionError(false, ERROR);
        state.notifyConnectionClosed();

        assertThat(state.isValid()).isFalse();
        assertThat(state.isClosed()).isTrue();
        assertThat(state.getConnectionError()).isSameAs(ERROR);
        verify(listener1).handleConnectionError(false, ERROR);
        verify(listener1).handleConnectionClosed();
        verifyNoMoreInteractions(listener1);

        // Listeners registered after event should be notified immediately.
        final ConnectionEventListener listener2 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener2);
        verify(listener2).handleConnectionError(false, ERROR);
        verify(listener2).handleConnectionClosed();
        verifyNoMoreInteractions(listener2);
    }

    @Test
    public void testCloseEventInValidState() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener1);
        state.notifyConnectionClosed();

        assertThat(state.isValid()).isFalse();
        assertThat(state.isClosed()).isTrue();
        assertThat(state.getConnectionError()).isNull();
        verify(listener1).handleConnectionClosed();
        verifyNoMoreInteractions(listener1);

        // Listeners registered after event should be notified immediately.
        final ConnectionEventListener listener2 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener2);
        verify(listener2).handleConnectionClosed();
        verifyNoMoreInteractions(listener2);
    }

    @Test
    public void testErrorEventInClosedState() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener1);
        state.notifyConnectionClosed();
        state.notifyConnectionError(false, ERROR);

        assertThat(state.isValid()).isFalse();
        assertThat(state.isClosed()).isTrue();
        assertThat(state.getConnectionError()).isNull();
        verify(listener1).handleConnectionClosed();
        verifyNoMoreInteractions(listener1);

        // Listeners registered after event should be notified immediately.
        final ConnectionEventListener listener2 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener2);
        verify(listener2).handleConnectionClosed();
        verifyNoMoreInteractions(listener2);
    }

    @Test
    public void testErrorEventInErrorState() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener1);
        state.notifyConnectionError(false, ERROR);
        state.notifyConnectionError(false, LATE_ERROR);

        assertThat(state.isValid()).isFalse();
        assertThat(state.isClosed()).isFalse();
        assertThat(state.getConnectionError()).isSameAs(ERROR);
        verify(listener1).handleConnectionError(false, ERROR);
        verifyNoMoreInteractions(listener1);

        // Listeners registered after event should be notified immediately.
        final ConnectionEventListener listener2 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener2);
        assertThat(state.getConnectionError()).isSameAs(ERROR);
        verify(listener2).handleConnectionError(false, ERROR);
        verifyNoMoreInteractions(listener2);
    }

    @Test
    public void testErrorEventInValidState() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener1);
        state.notifyConnectionError(false, ERROR);

        assertThat(state.isValid()).isFalse();
        assertThat(state.isClosed()).isFalse();
        assertThat(state.getConnectionError()).isSameAs(ERROR);
        verify(listener1).handleConnectionError(false, ERROR);
        verifyNoMoreInteractions(listener1);

        // Listeners registered after event should be notified immediately.
        final ConnectionEventListener listener2 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener2);
        verify(listener2).handleConnectionError(false, ERROR);
        verifyNoMoreInteractions(listener2);
    }

    @Test
    public void testRemoveEventListener() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener);
        state.notifyConnectionError(false, ERROR);
        verify(listener).handleConnectionError(false, ERROR);
        state.removeConnectionEventListener(listener);
        state.notifyConnectionClosed();
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void testUnsolicitedNotificationEventInClosedState() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener1);
        state.notifyConnectionClosed();
        state.notifyUnsolicitedNotification(UNSOLICITED);

        assertThat(state.isValid()).isFalse();
        assertThat(state.isClosed()).isTrue();
        assertThat(state.getConnectionError()).isNull();
        verify(listener1).handleConnectionClosed();
        verifyNoMoreInteractions(listener1);
    }

    @Test
    public void testUnsolicitedNotificationEventInErrorState() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener1);
        state.notifyConnectionError(false, ERROR);
        state.notifyUnsolicitedNotification(UNSOLICITED);

        assertThat(state.isValid()).isFalse();
        assertThat(state.isClosed()).isFalse();
        assertThat(state.getConnectionError()).isSameAs(ERROR);
        verify(listener1).handleConnectionError(false, ERROR);
        verifyNoMoreInteractions(listener1);
    }

    @Test
    public void testUnsolicitedNotificationEventInValidState() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener1);
        state.notifyUnsolicitedNotification(UNSOLICITED);

        assertThat(state.isValid()).isTrue();
        assertThat(state.isClosed()).isFalse();
        assertThat(state.getConnectionError()).isNull();
        verify(listener1).handleUnsolicitedNotification(same(UNSOLICITED));
        verifyNoMoreInteractions(listener1);

        /*
         * Listeners registered after event will not be notified (unsolicited
         * notifications are not cached).
         */
        final ConnectionEventListener listener2 = mock(ConnectionEventListener.class);
        state.addConnectionEventListener(listener2);
        verifyNoMoreInteractions(listener2);
    }

    @Test
    public void testValidState() {
        final ConnectionState state = new ConnectionState();
        assertThat(state.isValid()).isTrue();
        assertThat(state.isClosed()).isFalse();
        assertThat(state.getConnectionError()).isNull();
    }

    /**
     * Tests that reentrant close from error listener is handled.
     */
    @Test
    public void testReentrantClose() {
        final ConnectionState state = new ConnectionState();
        final ConnectionEventListener listener1 = mock(ConnectionEventListener.class);
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                state.notifyConnectionClosed();
                return null;
            }
        }).when(listener1).handleConnectionError(false, ERROR);

        state.addConnectionEventListener(listener1);
        state.notifyConnectionError(false, ERROR);

        assertThat(state.isValid()).isFalse();
        assertThat(state.isClosed()).isTrue();
        assertThat(state.getConnectionError()).isSameAs(ERROR);
        verify(listener1).handleConnectionError(false, ERROR);
        verify(listener1).handleConnectionClosed();
    }
}
