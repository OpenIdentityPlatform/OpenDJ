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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.grizzly;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.util.Options;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

/**
 * Tests how the client filter handles errors on a connection with and without an LDAP connection
 * attached. None is attached after {@code handleClose} has removed it, or before
 * {@code registerConnection} has set it.
 */
@SuppressWarnings("javadoc")
public class LDAPClientFilterTestCase extends SdkTestCase {
    /** An UnbindRequest with message ID 1: a request is a decoding error on the client side. */
    private static final byte[] UNBIND_REQUEST = { 0x30, 0x05, 0x02, 0x01, 0x01, 0x42, 0x00 };

    private final LDAPClientFilter filter = new LDAPClientFilter(new DecodeOptions(), 0);

    @Test(expectedExceptions = DecodeException.class,
        expectedExceptionsMessageRegExp = "^Unexpected LDAP request: id=1, .*")
    public void handleReadWithoutLDAPConnectionThrowsTheDecodeError() throws Exception {
        filter.handleRead(newContext(newConnection(), UNBIND_REQUEST));
    }

    @Test
    public void exceptionOccurredWithoutLDAPConnectionReturns() throws Exception {
        filter.exceptionOccurred(newContext(newConnection(), null), new IOException("read failed"));
    }

    @Test
    public void handleReadClosesTheLDAPConnectionOnDecodeError() throws Exception {
        final Connection<?> connection = newConnection();
        final GrizzlyLDAPConnectionFactory factory = newFactory();
        try {
            final ConnectionEventListener listener = attachLDAPConnection(connection, factory);
            try {
                filter.handleRead(newContext(connection, UNBIND_REQUEST));
                fail("The client accepted an LDAP request");
            } catch (final DecodeException expected) {
                // The filter closes the LDAP connection and passes the error on to Grizzly.
            }
            assertConnectionError(listener, ResultCode.CLIENT_SIDE_DECODING_ERROR);
        } finally {
            factory.close();
        }
    }

    @Test
    public void exceptionOccurredClosesTheLDAPConnection() throws Exception {
        final Connection<?> connection = newConnection();
        final GrizzlyLDAPConnectionFactory factory = newFactory();
        try {
            final ConnectionEventListener listener = attachLDAPConnection(connection, factory);
            filter.exceptionOccurred(newContext(connection, null), new IOException("read failed"));
            assertConnectionError(listener, ResultCode.CLIENT_SIDE_LOCAL_ERROR);
        } finally {
            factory.close();
        }
    }

    /** Returns an open Grizzly connection with no LDAP connection attached. */
    private static Connection<?> newConnection() {
        final Connection<?> connection = mock(Connection.class);
        when(connection.getAttributes()).thenReturn(Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createSafeAttributeHolder());
        when(connection.isOpen()).thenReturn(true);
        return connection;
    }

    private static FilterChainContext newContext(final Connection<?> connection, final byte[] message) {
        final FilterChainContext ctx = FilterChainContext.create(connection);
        if (message != null) {
            ctx.setMessage(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, message));
        }
        return ctx;
    }

    private static GrizzlyLDAPConnectionFactory newFactory() {
        return new GrizzlyLDAPConnectionFactory("localhost", 0, Options.defaultOptions());
    }

    private ConnectionEventListener attachLDAPConnection(final Connection<?> connection,
            final GrizzlyLDAPConnectionFactory factory) {
        final GrizzlyLDAPConnection ldapConnection = new GrizzlyLDAPConnection(connection, factory);
        final ConnectionEventListener listener = mock(ConnectionEventListener.class);
        ldapConnection.addConnectionEventListener(listener);
        filter.registerConnection(connection, ldapConnection);
        return listener;
    }

    private static void assertConnectionError(final ConnectionEventListener listener, final ResultCode resultCode) {
        final ArgumentCaptor<LdapException> error = ArgumentCaptor.forClass(LdapException.class);
        verify(listener).handleConnectionError(eq(false), error.capture());
        assertThat(error.getValue().getResult().getResultCode()).isEqualTo(resultCode);
    }
}
