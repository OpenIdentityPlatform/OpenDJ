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

import java.io.IOException;
import java.util.Arrays;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/**
 * An example client application which searches a Directory Server. This example
 * takes the following command line parameters:
 *
 * <pre>
 *  {@code <host> <port> <username> <password>
 *      <baseDN> <scope> <filter> [<attribute> <attribute> ...]}
 * </pre>
 */
public final class Search {
    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: host, port, username, password,
     *            base DN, scope, filter, and zero or more attributes to be
     *            retrieved.
     */
    public static void main(final String[] args) {
        if (args.length < 7) {
            System.err.println("Usage: host port username password baseDN scope "
                    + "filter [attribute ...]");
            System.exit(1);
        }

        // Parse command line arguments.
        final String hostName = args[0];
        final int port = Integer.parseInt(args[1]);
        final String userName = args[2];
        final String password = args[3];
        final String baseDN = args[4];
        final String scopeString = args[5];
        final String filter = args[6];
        String[] attributes;
        if (args.length > 7) {
            attributes = Arrays.copyOfRange(args, 7, args.length);
        } else {
            attributes = new String[0];
        }

        final SearchScope scope = SearchScope.valueOf(scopeString);
        if (scope == null) {
            System.err.println("Unknown scope: " + scopeString);
            System.exit(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
            return;
        }

        // --- JCite ---
        // Create an LDIF writer which will write the search results to stdout.
        final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);

        // Connect and bind to the server.
        final LDAPConnectionFactory factory = new LDAPConnectionFactory(hostName, port);
        Connection connection = null;

        try {
            connection = factory.getConnection();
            connection.bind(userName, password.toCharArray());

            // Read the entries and output them as LDIF.
            final ConnectionEntryReader reader =
                    connection.search(baseDN, scope, filter, attributes);
            while (reader.hasNext()) {
                if (!reader.isReference()) {
                    final SearchResultEntry entry = reader.readEntry();
                    writer.writeComment("Search result entry: " + entry.getName());
                    writer.writeEntry(entry);
                } else {
                    final SearchResultReference ref = reader.readReference();

                    // Got a continuation reference.
                    writer.writeComment("Search result reference: " + ref.getURIs());
                }
            }
            writer.flush();
        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
        } catch (final IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        // --- JCite ---
    }

    private Search() {
        // Not used.
    }
}
