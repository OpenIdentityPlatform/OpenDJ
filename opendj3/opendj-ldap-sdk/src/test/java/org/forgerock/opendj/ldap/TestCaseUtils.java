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
 */

package org.forgerock.opendj.ldap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * This class defines some utility functions which can be used by test cases.
 */
public final class TestCaseUtils {
    /**
     * The name of the system property that specifies the ldap port. Set this
     * property when running the server if you want to use a given port number,
     * otherwise a port is chosen randomly at test startup time.
     */
    public static final String PROPERTY_LDAP_PORT = "org.forgerock.opendj.test.LdapPort";

    /**
     * Port number that's used by the server. Need to be used by the test cases
     * to create connections.
     */
    public static final int PORT;

    static {
        final String ldapPort = System.getProperty(PROPERTY_LDAP_PORT);
        if (ldapPort != null) {
            PORT = Integer.valueOf(ldapPort);
        } else {
            PORT = findFreePort();
        }
    }

    /**
     * Creates a temporary text file with the specified contents. It will be
     * marked for automatic deletion when the JVM exits.
     *
     * @param lines
     *            The file contents.
     * @return The absolute path to the file that was created.
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    public static String createTempFile(final String... lines) throws Exception {
        final File f = File.createTempFile("LDIFBasedTestCase", ".txt");
        f.deleteOnExit();

        final FileWriter w = new FileWriter(f);
        for (final String s : lines) {
            w.write(s + System.getProperty("line.separator"));
        }

        w.close();

        return f.getAbsolutePath();
    }

    /**
     * Finds a free server socket port on the local host.
     *
     * @return The free port.
     */
    public static int findFreePort() {
        try {
            ServerSocket serverLdapSocket = new ServerSocket();
            serverLdapSocket.setReuseAddress(true);
            serverLdapSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            final int port = serverLdapSocket.getLocalPort();
            serverLdapSocket.close();
            return port;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns an internal client connection to the running ldap server.
     *
     * @return The internal client connection.
     * @throws Exception
     *             When an error occurs.
     */
    public static Connection getInternalConnection() throws Exception {
        startServer();
        final ConnectionFactory factory =
                Connections.newInternalConnectionFactory(LDAPServer.getInstance(), null);
        return factory.getConnection();
    }

    /**
     * Returns the port which the test server listens on.
     *
     * @return The LDAP port.
     */
    public static int getLdapPort() {
        return PORT;
    }

    /**
     * Starts the test ldap server.
     *
     * @throws Exception
     *             If an error occurs when starting the server.
     */
    public static void startServer() throws Exception {
        LDAPServer.getInstance().start(PORT);
    }

    /**
     * Stops the test ldap server.
     */
    public static void stopServer() {
        LDAPServer.getInstance().stop();
    }
}
