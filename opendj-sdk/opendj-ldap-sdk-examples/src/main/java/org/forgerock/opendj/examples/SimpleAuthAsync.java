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
 *      Copyright 2015 ForgeRock AS.
 */

package org.forgerock.opendj.examples;

import static org.forgerock.opendj.ldap.TrustManagers.checkHostName;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.SSL_USE_STARTTLS;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.SSL_CONTEXT;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Options;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.security.GeneralSecurityException;
import java.util.concurrent.CountDownLatch;

/**
 * An example client application which performs simple authentication to a
 * directory server using the asynchronous APIs.
 * <br>
 * This example takes the following command line parameters:
 * <ul>
 * <li>host - host name of the directory server</li>
 * <li>port - port number of the directory server</li>
 * <li>bind-dn - DN of the user to authenticate</li>
 * <li>bind-password - Password of the user to authenticate</li>
 * <li>use-starttls - (Optional) connect with StartTLS</li>
 * <li>use-ssl - (Optional) connect over SSL</li>
 * </ul>
 * The host, port, bind-dn, and bind-password arguments are required.
 * The use-starttls and use-ssl arguments are optional and mutually exclusive.
 * <br>
 * If the server certificate is self-signed,
 * or otherwise not trusted out-of-the-box,
 * then set the trust store by using the JSSE system property
 * {@code -Djavax.net.ssl.trustStore=/path/to/opendj/config/keystore}
 * and the trust store password if necessary by using the JSSE system property
 * {@code -Djavax.net.ssl.trustStorePassword=`cat /path/to/opendj/config/keystore.pin`}.
 */
public final class SimpleAuthAsync {
    /** Connection to the LDAP server. */
    private static Connection connection;
    /** Result for the modify operation. */
    private static int resultCode;
    /** Count down latch to wait for modify operation to complete. */
    private static final CountDownLatch COMPLETION_LATCH = new CountDownLatch(1);

    /**
     * Authenticate to the directory either over LDAP, over LDAPS, or using
     * StartTLS.
     *
     * @param args
     *            The command line arguments
     */
    public static void main(final String[] args) {
        parseArgs(args);

        // Connect and bind.
        // Pass getTrustAllOptions() instead of getTrustOptions()
        // to the connection factory constructor
        // if you want to trust all certificates blindly.
        new LDAPConnectionFactory(host, port, getTrustOptions(host, keystore, storepass))
                .getConnectionAsync()
                .thenAsync(new AsyncFunction<Connection, BindResult, LdapException>() {
                    @Override
                    public Promise<BindResult, LdapException> apply(Connection connection)
                            throws LdapException {
                        SimpleAuthAsync.connection = connection;
                        return connection.bindAsync(
                                Requests.newSimpleBindRequest(bindDN, bindPassword.toCharArray()));
                    }
                })
                .thenOnResult(new ResultHandler<Result>() {
                    @Override
                    public void handleResult(Result result) {
                        resultCode = result.getResultCode().intValue();
                        System.out.println("Authenticated as " + bindDN + ".");
                        COMPLETION_LATCH.countDown();
                    }
                })
                .thenOnException(new ExceptionHandler<LdapException>() {
                    @Override
                    public void handleException(LdapException e) {
                        System.err.println(e.getMessage());
                        resultCode = e.getResult().getResultCode().intValue();
                        COMPLETION_LATCH.countDown();
                    }
                });

        try {
            COMPLETION_LATCH.await();
        }  catch (InterruptedException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_USER_CANCELLED.intValue());
            return;
        }

        closeSilently(connection);
        System.exit(resultCode);
    }

    /**
     * For StartTLS and SSL the connection factory needs SSL context options.
     * In the general case, a trust manager in the SSL context serves
     * to check server certificates, and a key manager handles client keys
     * when the server checks certificates from our client.
     * <br>
     * This sample checks the server certificate,
     * verifying that the certificate is currently valid,
     * and that the host name of the server matches that of the certificate,
     * based on a Java Key Store-format trust store.
     * This sample does not present a client certificate.
     *
     * @param hostname      Host name expected in the server certificate
     * @param truststore    Path to trust store file for the trust manager
     * @param storepass     Password for the trust store
     * @return SSL context options if SSL or StartTLS is used.
     */
    private static Options getTrustOptions(final String hostname,
                                           final String truststore,
                                           final String storepass) {
        Options options = Options.defaultOptions();
        if (useSSL || useStartTLS) {
            try {
                TrustManager trustManager = TrustManagers.checkValidityDates(
                        checkHostName(hostname,
                                TrustManagers.checkUsingTrustStore(
                                        truststore, storepass.toCharArray(), null)));
                if (trustManager != null) {
                    SSLContext sslContext = new SSLContextBuilder()
                            .setTrustManager(trustManager).getSSLContext();
                    options.set(SSL_CONTEXT, sslContext);
                }
                options.set(SSL_USE_STARTTLS, useStartTLS);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_CONNECT_ERROR.intValue());
                return null;
            }
        }
        return options;
    }

    /**
     * For StartTLS and SSL the connection factory needs SSL context options. In
     * the general case, a trust manager in the SSL context serves to check
     * server certificates, and a key manager handles client keys when the
     * server checks certificates from our client.
     * <br>
     * OpenDJ directory server lets you install by default with a self-signed
     * certificate that is not in the system trust store. To simplify this
     * implementation trusts all server certificates.
     *
     * @return SSL context options to trust all certificates without checking.
     */
    private static Options getTrustAllOptions() {
        try {
            Options options = Options.defaultOptions();
            SSLContext sslContext =
                    new SSLContextBuilder().setTrustManager(TrustManagers.trustAll())
                            .getSSLContext();
            options.set(SSL_CONTEXT, sslContext);
            options.set(SSL_USE_STARTTLS, useStartTLS);
            return options;
        } catch (GeneralSecurityException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_CONNECT_ERROR.intValue());
            return null;
        }
    }

    private static String  host;
    private static int     port;
    private static String  bindDN;
    private static String  bindPassword;
    private static boolean useStartTLS;
    private static boolean useSSL;
    private static String  keystore;
    private static String  storepass;

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

    private SimpleAuthAsync() {
        // Not used.
    }
}
