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
package com.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.DEBUG_LOG;

import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;

/**
 * Common utility methods.
 */
final class GrizzlyUtils {
    static void configureConnection(final Connection<?> connection, final boolean tcpNoDelay,
            final boolean keepAlive, final boolean reuseAddress, final int linger) {
        /*
         * Test shows that its much faster with non block writes but risk
         * running out of memory if the server is slow.
         */
        connection.configureBlocking(true);

        // Configure socket options.
        final SocketChannel channel = (SocketChannel) ((TCPNIOConnection) connection).getChannel();
        final Socket socket = channel.socket();
        try {
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (final SocketException e) {
            DEBUG_LOG.log(Level.FINE, "Unable to set TCP_NODELAY to " + tcpNoDelay
                    + " on client connection", e);
        }
        try {
            socket.setKeepAlive(keepAlive);
        } catch (final SocketException e) {
            DEBUG_LOG.log(Level.FINE, "Unable to set SO_KEEPALIVE to " + keepAlive
                    + " on client connection", e);
        }
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (final SocketException e) {
            DEBUG_LOG.log(Level.FINE, "Unable to set SO_REUSEADDR to " + reuseAddress
                    + " on client connection", e);
        }
        try {
            if (linger < 0) {
                socket.setSoLinger(false, 0);
            } else {
                socket.setSoLinger(true, linger);
            }
        } catch (final SocketException e) {
            DEBUG_LOG.log(Level.FINE, "Unable to set SO_LINGER to " + linger
                    + " on client connection", e);
        }
    }

    // Prevent instantiation.
    private GrizzlyUtils() {
        // No implementation required.
    }

}
