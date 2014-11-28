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
 *      Copyright 2012-2014 ForgeRock AS
 *
 */

package org.forgerock.opendj.examples;

import java.util.Collection;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.RootDSE;
import org.forgerock.opendj.ldap.requests.PasswordModifyExtendedRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.WhoAmIExtendedRequest;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.WhoAmIExtendedResult;

/**
 * This command-line client demonstrates use of LDAP extended operations. The
 * client takes as arguments the host and port for the directory server, and
 * expects to find the entries and access control instructions as defined in <a
 * href="http://opendj.forgerock.org/Example.ldif">Example.ldif</a>.
 *
 * This client connects as <code>cn=Directory Manager</code> with password
 * <code>password</code>. Not a best practice; in real code use application
 * specific credentials to connect, and ensure that your application has access
 * to use the LDAP extended operations needed.
 */
public final class ExtendedOperations {

    /**
     * Connect to the server, and then try to use some LDAP extended operations.
     *
     * @param args
     *            The command line arguments: host, port
     */
    public static void main(final String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: host port");
            System.err.println("For example: localhost 1389");
            System.exit(1);
        }
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);

        final LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port);
        Connection connection = null;

        try {
            connection = factory.getConnection();
            checkSupportedExtendedOperations(connection);

            final String user = "cn=Directory Manager";
            final char[] password = "password".toCharArray();
            connection.bind(user, password);

            // Uncomment a method to run one of the examples.

            // For a Cancel Extended request, see the SearchAsync example.
            //usePasswordModifyExtendedRequest(connection);
            // For StartTLS, see the authentication examples.
            useWhoAmIExtendedRequest(connection);

        } catch (LdapException e) {
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
     * Use the password modify extended request.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void usePasswordModifyExtendedRequest(Connection connection) throws LdapException {
        // --- JCite password modify ---
        if (isSupported(PasswordModifyExtendedRequest.OID)) {
            final String userIdentity = "u:scarter";
            final char[] oldPassword = "sprain".toCharArray();
            final char[] newPassword = "secret12".toCharArray();

            final PasswordModifyExtendedRequest request =
                    Requests.newPasswordModifyExtendedRequest()
                        .setUserIdentity(userIdentity)
                        .setOldPassword(oldPassword)
                        .setNewPassword(newPassword);

            final PasswordModifyExtendedResult result =
                    connection.extendedRequest(request);
            if (result.isSuccess()) {
                System.out.println("Changed password for " + userIdentity);
            } else {
                System.err.println(result.getDiagnosticMessage());
            }
        } else {
            System.err.println("PasswordModifyExtendedRequest not supported");
        }
        // --- JCite password modify ---
    }

    /**
     * Use the Who Am I? extended request.
     *
     * @param connection Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useWhoAmIExtendedRequest(Connection connection) throws LdapException {
        // --- JCite who am I ---
        if (isSupported(WhoAmIExtendedRequest.OID)) {

            final String name = "uid=bjensen,ou=People,dc=example,dc=com";
            final char[] password = "hifalutin".toCharArray();

            final Result result = connection.bind(name, password);
            if (result.isSuccess()) {

                final WhoAmIExtendedRequest request =
                        Requests.newWhoAmIExtendedRequest();
                final WhoAmIExtendedResult extResult =
                        connection.extendedRequest(request);

                if (extResult.isSuccess()) {
                    System.out.println("Authz ID: "  + extResult.getAuthorizationID());
                }
            }
        } else {
            System.err.println("WhoAmIExtendedRequest not supported");
        }
        // --- JCite who am I ---
    }

    // --- JCite check support ---
    /**
     * Controls supported by the LDAP server.
     */
    private static Collection<String> extendedOperations;

    /**
     * Populate the list of supported LDAP extended operation OIDs.
     *
     * @param connection
     *            Active connection to the LDAP server.
     * @throws LdapException
     *             Failed to get list of extended operations.
     */
    static void checkSupportedExtendedOperations(Connection connection) throws LdapException {
        extendedOperations = RootDSE.readRootDSE(connection)
                .getSupportedExtendedOperations();
    }

    /**
     * Check whether an extended operation is supported. Call
     * {@code checkSupportedExtendedOperations} first.
     *
     * @param extendedOperation
     *            Check support for this extended operation, provided by OID.
     * @return True if the control is supported.
     */
    static boolean isSupported(final String extendedOperation) {
        return extendedOperations != null && extendedOperations.contains(extendedOperation);
    }
    // --- JCite check support ---

    /**
     * Constructor not used.
     */
    private ExtendedOperations() {
        // Not used.
    }
}
