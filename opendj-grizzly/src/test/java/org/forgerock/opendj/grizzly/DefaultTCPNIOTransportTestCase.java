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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.grizzly;

import static org.forgerock.opendj.grizzly.DefaultTCPNIOTransport.DEFAULT_TRANSPORT;
import static org.forgerock.opendj.ldap.TestCaseUtils.findFreeSocketAddress;
import static org.testng.Assert.assertTrue;

import java.net.Socket;
import java.net.SocketAddress;

import org.forgerock.opendj.ldap.SdkTestCase;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.ReferenceCountedObject;

/**
 * Tests DefaultTCPNIOTransport class.
 */
public class DefaultTCPNIOTransportTestCase extends SdkTestCase {
    /**
     * Tests the default transport.
     * <p>
     * FIXME: this test is disable because it does not clean up any of the
     * connections it creates.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test(enabled = false)
    public void testGetInstance() throws Exception {
        // Create a transport.
        final ReferenceCountedObject<TCPNIOTransport>.Reference transport =
                DEFAULT_TRANSPORT.acquire();
        SocketAddress socketAddress = findFreeSocketAddress();
        transport.get().bind(socketAddress);

        // Establish a socket connection to see if the transport factory works.
        final Socket socket = new Socket();
        try {
            socket.connect(socketAddress);

            // Successfully connected if there is no exception.
            assertTrue(socket.isConnected());
            // Don't stop the transport because it is shared with the ldap server.
        } finally {
            socket.close();
            transport.release();
        }
    }
}
