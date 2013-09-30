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
 *     Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.TestCaseUtils.findFreeSocketAddress;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.Test;

/**
 * Tests the {@link LDAPConnectionFactory} class.
 */
@SuppressWarnings("javadoc")
public class LDAPConnectionFactoryTestCase extends SdkTestCase {
    // Test timeout for tests which need to wait for network events.
    private static final long TEST_TIMEOUT = 30L;

    /**
     * This unit test exposes the bug raised in issue OPENDJ-1156: NPE in
     * ReferenceCountedObject after shutting down directory.
     */
    @Test
    public void testResourceManagement() throws Exception {
        final AtomicReference<LDAPClientContext> context = new AtomicReference<LDAPClientContext>();
        final Semaphore latch = new Semaphore(0);
        final LDAPListener server = createServer(latch, context);
        final ConnectionFactory factory = new LDAPConnectionFactory(server.getSocketAddress());
        try {
            for (int i = 0; i < 100; i++) {
                // Connect to the server.
                final Connection connection = factory.getConnection();
                try {
                    // Wait for the server to accept the connection.
                    assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

                    final MockConnectionEventListener listener = new MockConnectionEventListener();
                    connection.addConnectionEventListener(listener);

                    // Perform remote disconnect which will trigger a client side connection error.
                    context.get().disconnect();

                    // Wait for the error notification to reach the client.
                    listener.awaitError(TEST_TIMEOUT, TimeUnit.SECONDS);
                } finally {
                    connection.close();
                }
            }
        } finally {
            factory.close();
            server.close();
        }
    }

    private LDAPListener createServer(final Semaphore latch,
            final AtomicReference<LDAPClientContext> context) throws IOException {
        return new LDAPListener(findFreeSocketAddress(),
                new ServerConnectionFactory<LDAPClientContext, Integer>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public ServerConnection<Integer> handleAccept(
                            final LDAPClientContext clientContext) throws ErrorResultException {
                        context.set(clientContext);
                        latch.release();
                        return mock(ServerConnection.class);
                    }
                });
    }
}
