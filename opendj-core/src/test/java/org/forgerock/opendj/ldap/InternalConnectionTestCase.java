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
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.testng.annotations.Test;

/** Tests the connection life cycle of the pseudo-connection returned by {@link Connections#newInternalConnection}. */
@SuppressWarnings("javadoc")
public class InternalConnectionTestCase extends SdkTestCase {

    @SuppressWarnings("unchecked")
    private static ServerConnection<Integer> mockServerConnection() {
        return mock(ServerConnection.class);
    }

    private static void verifyClosedOnce(final ServerConnection<Integer> serverConnection) {
        verify(serverConnection, times(1)).handleConnectionClosed(anyInt(), any(UnbindRequest.class));
    }

    @Test
    public void testCloseNotifiesListeners() throws Exception {
        final ServerConnection<Integer> serverConnection = mockServerConnection();
        final Connection connection = Connections.newInternalConnection(serverConnection);
        final MockConnectionEventListener listener = new MockConnectionEventListener();
        connection.addConnectionEventListener(listener);

        connection.close();

        assertThat(listener.getInvocationCount()).isEqualTo(1);
        verifyClosedOnce(serverConnection);
    }

    @Test
    public void testCloseIsIdempotent() throws Exception {
        final ServerConnection<Integer> serverConnection = mockServerConnection();
        final Connection connection = Connections.newInternalConnection(serverConnection);
        final MockConnectionEventListener listener = new MockConnectionEventListener();
        connection.addConnectionEventListener(listener);

        connection.close();
        connection.close();

        assertThat(listener.getInvocationCount()).isEqualTo(1);
        verifyClosedOnce(serverConnection);
    }

    @Test
    public void testRemovedListenerIsNotNotified() throws Exception {
        final Connection connection = Connections.newInternalConnection(mockServerConnection());
        final MockConnectionEventListener listener = new MockConnectionEventListener();
        connection.addConnectionEventListener(listener);
        connection.removeConnectionEventListener(listener);

        connection.close();

        assertThat(listener.getInvocationCount()).isEqualTo(0);
    }

    /** A listener registered after the connection was closed is told about it straight away. */
    @Test
    public void testListenerAddedAfterCloseIsNotifiedImmediately() throws Exception {
        final Connection connection = Connections.newInternalConnection(mockServerConnection());
        connection.close();

        final MockConnectionEventListener listener = new MockConnectionEventListener();
        connection.addConnectionEventListener(listener);

        assertThat(listener.getInvocationCount()).isEqualTo(1);
    }

    @Test
    public void testConnectionStateReflectsClose() throws Exception {
        final Connection connection = Connections.newInternalConnection(mockServerConnection());
        assertThat(connection.isClosed()).isFalse();
        assertThat(connection.isValid()).isTrue();

        connection.close();

        assertThat(connection.isClosed()).isTrue();
        assertThat(connection.isValid()).isFalse();
    }
}
