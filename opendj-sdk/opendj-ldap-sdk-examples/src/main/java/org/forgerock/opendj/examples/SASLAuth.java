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
 *      Copyright 2011-2015 ForgeRock AS.
 */

/**
 * An example client application which performs SASL authentication to a
 * directory server, displays a result, and closes the connection.
 *
 * Set up StartTLS before using this example.
 */
package org.forgerock.opendj.examples;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.SSL_USE_STARTTLS;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.SSL_CONTEXT;


import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.requests.PlainSASLBindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.util.Options;

/**
 * An example client application which performs SASL PLAIN authentication to a
 * directory server over LDAP with StartTLS. This example takes the following
 * command line parameters:
 * <ul>
 * <li>host - host name of the directory server</li>
 * <li>port - port number of the directory server for StartTLS</li>
 * <li>authzid - (Optional) Authorization identity</li>
 * <li>authcid - Authentication identity</li>
 * <li>passwd - Password of the user to authenticate</li>
 * </ul>
 * The host, port, authcid, and passwd are required. SASL PLAIN is described in
 * <a href="http://www.ietf.org/rfc/rfc4616.txt">RFC 4616</a>.
 * <p>
 * The authzid and authcid are prefixed as described in <a
 * href="http://tools.ietf.org/html/rfc4513#section-5.2.1.8">RFC 4513, section
 * 5.2.1.8</a>, with "dn:" if you pass in a distinguished name, or with "u:" if
 * you pass in a user ID.
 * <p>
 * By default, OpenDJ is set up for SASL PLAIN to use the Exact Match Identity
 * Mapper to find entries by searching uid values for the user ID. In other
 * words, the following examples are equivalent.
 *
 * <pre>
 * dn:uid=bjensen,ou=people,dc=example,dc=com
 * u:bjensen
 * </pre>
 */
public final class SASLAuth {
    /**
     * Authenticate to the directory using SASL PLAIN.
     *
     * @param args
     *            The command line arguments
     */
    public static void main(String[] args) {
        parseArgs(args);
        Connection connection = null;

        // --- JCite ---
        try {
            final LDAPConnectionFactory factory =
                    new LDAPConnectionFactory(host, port, getTrustAllOptions());
            connection = factory.getConnection();
            PlainSASLBindRequest request =
                    Requests.newPlainSASLBindRequest(authcid, passwd.toCharArray())
                    .setAuthorizationID(authzid);
            connection.bind(request);
            System.out.println("Authenticated as " + authcid + ".");
        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
            return;
        } catch (final GeneralSecurityException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_CONNECT_ERROR.intValue());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        // --- JCite ---
    }

    /**
     * For StartTLS the connection factory needs SSL context options. In the
     * general case, a trust manager in the SSL context serves to check server
     * certificates, and a key manager handles client keys when the server
     * checks certificates from our client.
     *
     * OpenDJ directory server lets you install by default with a self-signed
     * certificate that is not in the system trust store. To simplify this
     * implementation trusts all server certificates.
     */
    private static Options getTrustAllOptions() throws GeneralSecurityException {
        Options options = Options.defaultOptions();
        SSLContext sslContext =
                new SSLContextBuilder().setTrustManager(TrustManagers.trustAll()).getSSLContext();
        options.set(SSL_CONTEXT, sslContext);
        options.set(SSL_USE_STARTTLS, true);
        return options;
    }

    private static String host;
    private static int port;
    private static String authzid;
    private static String authcid;
    private static String passwd;

    /**
     * Parse command line arguments.
     *
     * @param args
     *            host port [authzid] authcid passwd
     */
    private static void parseArgs(String[] args) {
        if (args.length < 4 || args.length > 5) {
            giveUp();
        }

        host = args[0];
        port = Integer.parseInt(args[1]);

        if (args.length == 5) {
            authzid = args[2];
            authcid = args[3];
            passwd = args[4];
        } else {
            authzid = null;
            authcid = args[2];
            passwd = args[3];
        }
    }

    private static void giveUp() {
        printUsage();
        System.exit(1);
    }

    private static void printUsage() {
        System.err.println("Usage: host port [authzid] authcid passwd");
        System.err.println("\tThe port must be able to handle LDAP with StartTLS.");
        System.err.println("\tSee http://www.ietf.org/rfc/rfc4616.txt for more on SASL PLAIN.");
    }

    private SASLAuth() {
        // Not used.
    }
}
