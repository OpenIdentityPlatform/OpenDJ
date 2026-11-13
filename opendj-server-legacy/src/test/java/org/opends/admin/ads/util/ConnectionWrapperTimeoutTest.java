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
package org.opends.admin.ads.util;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.types.HostPort;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(sequential = true)
public class ConnectionWrapperTimeoutTest extends DirectoryServerTestCase {
    @Test(timeOut = 10000)
    public void timesOutWhenServerAcceptsConnectionButDoesNotAnswerBind() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch accepted = new CountDownLatch(1);
        try (ServerSocket listener = new ServerSocket(0)) {
            final Thread server = new Thread(() -> {
                try (Socket ignored = listener.accept()) {
                    accepted.countDown();
                    release.await(8, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // The test closes the listener and connection during cleanup.
                }
            }, "unresponsive-ldap-test");
            server.setDaemon(true);
            server.start();
            final long start = System.nanoTime();
            try (ConnectionWrapper ignored = new ConnectionWrapper(new HostPort("localhost", listener.getLocalPort()),
                    PreferredConnection.Type.LDAP, DN.valueOf("cn=admin"), "password", 1000, null, null, 1000)) {
                fail("An unresponsive bind must time out");
            } catch (LdapException expected) {
                assertTrue(accepted.await(1, TimeUnit.SECONDS), "The test server must have accepted the connection");
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 500,
                        "The failure should occur after waiting for the unresponsive server");
                assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 6,
                        "The request timeout should expire before the server closes the connection");
            } finally {
                release.countDown();
                server.join(1000);
            }
        }
    }
}
