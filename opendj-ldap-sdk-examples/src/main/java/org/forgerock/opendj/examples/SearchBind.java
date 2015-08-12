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
 *      Copyright 2012-2015 ForgeRock AS.
 *
 */

package org.forgerock.opendj.examples;

import java.io.Console;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;

/**
 * An interactive command-line client that performs a search and subsequent
 * simple bind. The client prompts for email address and for a password, and
 * then searches based on the email address, to bind as the user with the
 * password. If successful, the client displays the common name from the user's
 * entry.
 * <ul>
 * <li>host - host name of the directory server</li>
 * <li>port - port number of the directory server</li>
 * <li>base-dn - base DN for the search, e.g. dc=example,dc=com</li>
 * </ul>
 * All arguments are required.
 */
public final class SearchBind {
    /**
     * Prompt for email and password, search and bind, then display message.
     *
     * @param args
     *            The command line arguments: host, port, base-dn.
     */
    public static void main(final String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: host port base-dn");
            System.err.println("For example: localhost 1389 dc=example,dc=com");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String baseDN = args[2];

        // --- JCite ---
        // Prompt for mail and password.
        Console c = System.console();
        if (c == null) {
            System.err.println("No console.");
            System.exit(1);
        }

        String mail = c.readLine("Email address: ");
        char[] password = c.readPassword("Password: ");

        // Search using mail address, and then bind with the DN and password.
        final LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port);
        Connection connection = null;
        try {
            connection = factory.getConnection();
            SearchResultEntry entry =
                    connection.searchSingleEntry(baseDN,
                            SearchScope.WHOLE_SUBTREE,
                            Filter.equality("mail", mail).toString(),
                            "cn");
            DN bindDN = entry.getName();
            connection.bind(bindDN.toString(), password);

            String cn = entry.getAttribute("cn").firstValueAsString();
            System.out.println("Hello, " + cn + "!");
        } catch (final LdapException e) {
            System.err.println("Failed to bind.");
            System.exit(e.getResult().getResultCode().intValue());
            return;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        // --- JCite ---
    }

    /**
     * Constructor not used.
     */
    private SearchBind() {
        // Not used
    }
}
