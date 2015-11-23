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
 *      Copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.examples;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.util.Options;

/**
 * An example client application which performs simple authentication to a
 * directory server. This example takes the following command line parameters:
 * <ul>
 * <li>host - host name of the directory server</li>
 * <li>port - port number of the directory server, e.g. 1389, 1636</li>
 * <li>bind-dn - DN of the user to authenticate</li>
 * <li>bind-password - Password of the user to authenticate</li>
 * <li>use-starttls - (Optional) connect with StartTLS</li>
 * <li>use-ssl - (Optional) connect over SSL</li>
 * </ul>
 * The host, port, bind-dn, and bind-password arguments are required.
 * The use-starttls and use-ssl arguments are optional and mutually exclusive.
 * <p>
 * If the server certificate is self-signed,
 * or otherwise not trusted out-of-the-box,
 * then set the trust store by using the JSSE system property
 * {@code -Djavax.net.ssl.trustStore=/path/to/opendj/config/keystore}
 * and the trust store password if necessary by using the JSSE system property
 * {@code -Djavax.net.ssl.trustStorePassword=`cat /path/to/opendj/config/keystore.pin`}.
 */
public final class SimpleAuth {

    /**
     * Authenticate to the directory either over LDAP, over LDAPS, or using
     * StartTLS.
     *
     * @param args
     *            The command line arguments
     */
    public static void main(final String[] args) {
        parseArgs(args);
        // Connect and bind to the server, then close the connection.
        if (useStartTLS) {
            connectStartTLS();
        } else if (useSSL) {
            connectSSL();
        } else {
            connect();
        }
    }

    // --- JCite basic auth ---
    /**
     * Authenticate over LDAP.
     */
    private static void connect() {
        final LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port);
        Connection connection = null;

        try {
            connection = factory.getConnection();
            connection.bind(bindDN, bindPassword.toCharArray());
            System.out.println("Authenticated as " + bindDN + ".");
        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
            return;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
    // --- JCite basic auth ---

    // --- JCite trust options ---
    /**
     * For StartTLS and SSL the connection factory needs SSL context options.
     * In the general case, a trust manager in the SSL context serves
     * to check server certificates, and a key manager handles client keys
     * when the server checks certificates from our client.
     * <p>
     * This sample expects a directory server
     * that allows use of Start TLS on the LDAP port.
     * This sample checks the server certificate,
     * verifying that the certificate is currently valid,
     * and that the host name of the server matches that of the certificate,
     * based on a Java Key Store-format trust store.
     * This sample does not present a client certificate.
     *
     * @param hostname Host name expected in the server certificate
     * @param truststore Path to trust store file for the trust manager
     * @param storepass Password for the trust store
     * @return SSL context options
     * @throws GeneralSecurityException Could not load the trust store
     */
    private static Options getTrustOptions(final String hostname,
                                           final String truststore,
                                           final String storepass)
            throws GeneralSecurityException {
        Options options = Options.defaultOptions();

        TrustManager trustManager = null;
        try {
            trustManager = TrustManagers.checkValidityDates(
                    TrustManagers.checkHostName(hostname,
                            TrustManagers.checkUsingTrustStore(
                                    truststore, storepass.toCharArray(), null)));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        if (trustManager != null) {
            SSLContext sslContext = new SSLContextBuilder()
                    .setTrustManager(trustManager).getSSLContext();
            options.set(SSL_CONTEXT, sslContext);
        }

        options.set(SSL_USE_STARTTLS, useStartTLS);

        return options;
    }
    // --- JCite trust options ---

    // --- JCite secure connect ---
    /**
     * Perform authentication over a secure connection.
     */
    private static void secureConnect() {
        Connection connection = null;

        try {

            final LDAPConnectionFactory factory =
                    new LDAPConnectionFactory(host, port,
                            getTrustOptions(host, keystore, storepass));
            connection = factory.getConnection();
            connection.bind(bindDN, bindPassword.toCharArray());

            System.out.println("Authenticated as " + bindDN + ".");

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
    }
    // --- JCite secure connect ---

    // --- JCite trust all ---
    /**
     * For StartTLS and SSL the connection factory needs SSL context options. In
     * the general case, a trust manager in the SSL context serves to check
     * server certificates, and a key manager handles client keys when the
     * server checks certificates from our client.
     *
     * OpenDJ directory server lets you install by default with a self-signed
     * certificate that is not in the system trust store. To simplify this
     * implementation trusts all server certificates.
     */
    private static Options getTrustAllOptions() throws GeneralSecurityException {
        Options options = Options.defaultOptions();
        SSLContext sslContext =
                new SSLContextBuilder().setTrustManager(TrustManagers.trustAll())
                        .getSSLContext();
        options.set(SSL_CONTEXT, sslContext);
        options.set(SSL_USE_STARTTLS, useStartTLS);
        return options;
    }
    // --- JCite trust all ---

    // --- JCite trust all connect ---
    /**
     * Perform authentication over a secure connection, trusting all server
     * certificates.
     */
    private static void trustAllConnect() {
        Connection connection = null;

        try {
            final LDAPConnectionFactory factory =
                    new LDAPConnectionFactory(host, port, getTrustAllOptions());
            connection = factory.getConnection();
            connection.bind(bindDN, bindPassword.toCharArray());
            System.out.println("Authenticated as " + bindDN + ".");
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
    }
    // --- JCite trust all connect ---

    /**
     * Authenticate using StartTLS.
     */
    private static void connectStartTLS() {
        secureConnect();
        // trustAllConnect();
    }

    /**
     * Authenticate over LDAPS.
     */
    private static void connectSSL() {
        secureConnect();
        // trustAllConnect();
    }

    private static String host;
    private static int port;
    private static String bindDN;
    private static String bindPassword;
    private static boolean useStartTLS;
    private static boolean useSSL;
    private static String keystore;
    private static String storepass;

    /**
     * Parse command line arguments.
     *
     * @param args
     *            host port bind-dn bind-password [ use-starttls | use-ssl ]
     */
    private static void parseArgs(String[] args) {
        if (args.length < 4 || args.length > 5) {
            giveUp();
        }

        host = args[0];
        port = Integer.parseInt(args[1]);
        bindDN = args[2];
        bindPassword = args[3];

        if (args.length == 5) {
            if ("use-starttls".equals(args[4].toLowerCase())) {
                useStartTLS = true;
                useSSL = false;
            } else if ("use-ssl".equals(args[4].toLowerCase())) {
                useStartTLS = false;
                useSSL = true;
            } else {
                giveUp();
            }
        }

        keystore = System.getProperty("javax.net.ssl.trustStore");
        storepass = System.getProperty("javax.net.ssl.trustStorePassword");
        if (keystore == null) { // Try to use Java's cacerts trust store.
            keystore = System.getProperty("java.home") + File.separator
                    + "lib" + File.separator
                    + "security" + File.separator
                    + "cacerts";
            storepass = "changeit"; // Default password
        }
    }

    private static void giveUp() {
        printUsage();
        System.exit(1);
    }

    private static void printUsage() {
        System.err.println("Usage: host port bind-dn bind-password [ use-starttls | use-ssl ]");
        System.err.println("\thost, port, bind-dn, and bind-password arguments are required.");
        System.err.println("\tuse-starttls and use-ssl are optional and mutually exclusive.");
        System.err.println("\tOptionally set javax.net.ssl.trustStore and javax.net.ssl.trustStorePassword.");
    }

    private SimpleAuth() {
        // Not used.
    }
}
