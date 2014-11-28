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
 *      Copyright 2012-2014 ForgeRock AS
 */
package org.forgerock.opendj.examples;

import java.io.IOException;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/**
 * Demonstrates accessing server information about capabilities and schema.
 */
public final class GetInfo {
    /** Connection information. */
    private static String host;
    private static int port;
    /** The kind of server information to request (all, controls, extops). */
    private static String infoType;

    /**
     * Access the directory over LDAP to request information about capabilities
     * and schema.
     *
     * @param args
     *            The command line arguments
     */
    public static void main(final String[] args) {
        parseArgs(args);
        connect();
    }

    /**
     * Authenticate over LDAP.
     */
    private static void connect() {
        // --- JCite ---
        final LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port);
        Connection connection = null;

        try {
            connection = factory.getConnection();
            connection.bind("", "".toCharArray()); // Anonymous bind

            final String attributeList;
            if ("controls".equals(infoType.toLowerCase())) {
                attributeList = "supportedControl";
            } else if ("extops".equals(infoType.toLowerCase())) {
                attributeList = "supportedExtension";
            } else {
                attributeList = "+"; // All operational attributes
            }

            final SearchResultEntry entry = connection.searchSingleEntry(
                    "", // DN is "" for root DSE.
                    SearchScope.BASE_OBJECT, // Read only the root DSE.
                    "(objectclass=*)", // Every object matches this filter.
                    attributeList); // Return these requested attributes.

            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            writer.writeComment("Root DSE for LDAP server at " + host + ":" + port);
            if (entry != null) {
                writer.writeEntry(entry);
            }
            writer.flush();
        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
            return;
        } catch (final IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            return;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        // --- JCite ---
    }

    private static void giveUp() {
        printUsage();
        System.exit(1);
    }

    /**
     * Parse command line arguments.
     *
     * @param args
     *            host port bind-dn bind-password info-type
     */
    private static void parseArgs(final String[] args) {
        if (args.length != 3) {
            giveUp();
        }

        host = args[0];
        port = Integer.parseInt(args[1]);
        infoType = args[2];
        final String infoTypeLc = infoType.toLowerCase();
        if (!"all".equals(infoTypeLc)
                && !"controls".equals(infoTypeLc)
                && !"extops".equals(infoTypeLc)) {
            giveUp();
        }
    }

    private static void printUsage() {
        System.err.println("Usage: host port info-type");
        System.err.println("\tAll arguments are required.");
        System.err.println("\tinfo-type to get can be either all, controls, or extops.");
    }

    private GetInfo() {
        // Not used.
    }
}
