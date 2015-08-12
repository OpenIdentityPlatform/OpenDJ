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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.Syntax;

/**
 * An example client application which prints a summary of the schema on the
 * named server as well as any warnings encountered while parsing the schema.
 * This example takes the following command line parameters:
 *
 * <pre>
 *  {@code <host> <port> <username> <password>}
 * </pre>
 */
public final class ReadSchema {
    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: host, port, username, password.
     */
    public static void main(final String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: host port username password");
            System.exit(1);
        }

        // Parse command line arguments.
        final String hostName = args[0];
        final int port = Integer.parseInt(args[1]);
        final String userName = args[2];
        final String password = args[3];

        // --- JCite ---
        // Connect and bind to the server.
        final LDAPConnectionFactory factory = new LDAPConnectionFactory(hostName, port);
        Connection connection = null;

        try {
            connection = factory.getConnection();
            connection.bind(userName, password.toCharArray());

            // Read the schema.
            Schema schema = Schema.readSchemaForEntry(connection, DN.rootDN());

            System.out.println("Attribute types");
            for (AttributeType at : schema.getAttributeTypes()) {
                System.out.println("  " + at.getNameOrOID());
            }
            System.out.println();

            System.out.println("Object classes");
            for (ObjectClass oc : schema.getObjectClasses()) {
                System.out.println("  " + oc.getNameOrOID());
            }
            System.out.println();

            System.out.println("Matching rules");
            for (MatchingRule mr : schema.getMatchingRules()) {
                System.out.println("  " + mr.getNameOrOID());
            }
            System.out.println();

            System.out.println("Syntaxes");
            for (Syntax s : schema.getSyntaxes()) {
                System.out.println("  " + s.getDescription());
            }
            System.out.println();

            // Etc...

            System.out.println("WARNINGS");
            for (LocalizableMessage m : schema.getWarnings()) {
                System.out.println("  " + m);
            }
            System.out.println();
        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
            return;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        // --- JCite ---
    }

    private ReadSchema() {
        // Not used.
    }
}
