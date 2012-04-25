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
 *      Copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.examples;

import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;

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
 * The host, port, bind-dn, and bind-password are required. The use-starttls and
 * use-ssl parameters are optional and mutually exclusive.
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
        } catch (final ErrorResultException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
            return;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

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
    private static LDAPOptions getTrustAllOptions() throws GeneralSecurityException {
        LDAPOptions lo = new LDAPOptions();
        SSLContext sslContext =
                new SSLContextBuilder().setTrustManager(TrustManagers.trustAll()).getSSLContext();
        lo.setSSLContext(sslContext);
        lo.setUseStartTLS(useStartTLS);
        return lo;
    }

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
        } catch (final ErrorResultException e) {
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

    /**
     * Authenticate using StartTLS.
     */
    private static void connectStartTLS() {
        trustAllConnect();
    }

    /**
     * Authenticate over LDAPS.
     */
    private static void connectSSL() {
        trustAllConnect();
    }

    private static String host;
    private static int port;
    private static String bindDN;
    private static String bindPassword;
    private static boolean useStartTLS = false;
    private static boolean useSSL = false;

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
            if (args[4].toLowerCase().equals("use-starttls")) {
                useStartTLS = true;
                useSSL = false;
            } else if (args[4].toLowerCase().equals("use-ssl")) {
                useStartTLS = false;
                useSSL = true;
            } else {
                giveUp();
            }
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
    }

    private SimpleAuth() {
        // Not used.
    }
}
