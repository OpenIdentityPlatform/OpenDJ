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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.examples;

import static org.forgerock.opendj.ldap.LDAPListener.CONNECT_MAX_BACKLOG;

import java.io.FileInputStream;
import java.io.IOException;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.KeyManagers;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.util.Options;

/**
 * An LDAP directory server which exposes data contained in an LDIF file. This
 * is implementation is very simple and is only intended as an example:
 * <ul>
 * <li>It does not support StartTLS
 * <li>It does not support Abandon or Cancel requests
 * <li>Very basic authentication and authorization support.
 * </ul>
 * This example takes the following command line parameters:
 *
 * <pre>
 *  {@code <listenAddress> <listenPort> <ldifFile> [<keyStoreFile> <keyStorePassword> <certNickname>]}
 * </pre>
 */
public final class Server {

    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: listen address, listen port, ldifFile,
     *            and optionally: key store, key store password and certificate nick name
     */
    public static void main(final String[] args) {
        if (args.length != 3 && args.length != 6) {
            System.err.println("Usage: listenAddress listenPort ldifFile "
                    + "[keyStoreFile keyStorePassword certNickname]");
            System.exit(1);
        }

        // Parse command line arguments.
        final String localAddress = args[0];
        final int localPort = Integer.parseInt(args[1]);
        final String ldifFileName = args[2];
        final String keyStoreFileName = (args.length == 6) ? args[3] : null;
        final String keyStorePassword = (args.length == 6) ? args[4] : null;
        final String certNickname = (args.length == 6) ? args[5] : null;

        // Create the memory backend.
        final MemoryBackend backend;
        try {
            backend = new MemoryBackend(new LDIFEntryReader(new FileInputStream(ldifFileName)));
        } catch (final IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
            return; // Keep compiler quiet.
        }

        // Create a server connection adapter.
        final ServerConnectionFactory<LDAPClientContext, Integer> connectionHandler =
                Connections.newServerConnectionFactory(backend);

        // Create listener.
        LDAPListener listener = null;
        try {
            final Options options = Options.defaultOptions().set(CONNECT_MAX_BACKLOG, 4096);

            if (keyStoreFileName != null) {
                // Configure SSL/TLS and enable it when connections are
                // accepted.
                final SSLContext sslContext =
                        new SSLContextBuilder().setKeyManager(
                                KeyManagers.useSingleCertificate(certNickname, KeyManagers
                                        .useKeyStoreFile(keyStoreFileName, keyStorePassword
                                                .toCharArray(), null))).setTrustManager(
                                TrustManagers.trustAll()).getSSLContext();

                final ServerConnectionFactory<LDAPClientContext, Integer> sslWrapper =
                        new ServerConnectionFactory<LDAPClientContext, Integer>() {

                            @Override
                            public ServerConnection<Integer> handleAccept(final LDAPClientContext clientContext)
                                    throws LdapException {
                                clientContext.enableTLS(sslContext, null, null, false, false);
                                return connectionHandler.handleAccept(clientContext);
                            }
                        };

                listener = new LDAPListener(localAddress, localPort, sslWrapper, options);
            } else {
                // No SSL.
                listener = new LDAPListener(localAddress, localPort, connectionHandler, options);
            }
            System.out.println("Press any key to stop the server...");
            System.in.read();
        } catch (final Exception e) {
            System.out.println("Error listening on " + localAddress + ":" + localPort);
            e.printStackTrace();
        } finally {
            if (listener != null) {
                listener.close();
            }
        }
    }

    private Server() {
        // Not used.
    }
}
