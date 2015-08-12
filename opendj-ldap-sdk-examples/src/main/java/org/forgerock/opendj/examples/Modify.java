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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ConnectionChangeRecordWriter;
import org.forgerock.opendj.ldif.LDIFChangeRecordReader;

/**
 * An example client application which applies update operations to a Directory
 * Server. The update operations will be read from an LDIF file, or stdin if no
 * filename is provided. This example takes the following command line
 * parameters (it will read from stdin if no LDIF file is provided):
 *
 * <pre>
 *  {@code <host> <port> <username> <password> [<ldifFile>]}
 * </pre>
 */
public final class Modify {
    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: host, port, username, password,
     *            LDIF file name containing the update operations (will use
     *            stdin if not provided).
     */
    public static void main(final String[] args) {
        if (args.length < 4 || args.length > 5) {
            System.err.println("Usage: host port username password [ldifFileName]");
            System.exit(1);
        }

        // Parse command line arguments.
        final String hostName = args[0];
        final int port = Integer.parseInt(args[1]);
        final String userName = args[2];
        final String password = args[3];

        // Create the LDIF reader which will either used the named file, if
        // provided, or stdin.
        InputStream ldif;
        if (args.length > 4) {
            try {
                ldif = new FileInputStream(args[4]);
            } catch (final FileNotFoundException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
                return;
            }
        } else {
            ldif = System.in;
        }
        // --- JCite ---
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(ldif);

        // Connect and bind to the server.
        final LDAPConnectionFactory factory = new LDAPConnectionFactory(hostName, port);
        Connection connection = null;

        try {
            connection = factory.getConnection();
            connection.bind(userName, password.toCharArray());

            // Write the changes.
            final ConnectionChangeRecordWriter writer =
                    new ConnectionChangeRecordWriter(connection);
            while (reader.hasNext()) {
                ChangeRecord changeRecord = reader.readChangeRecord();
                writer.writeChangeRecord(changeRecord);
                System.err.println("Successfully modified entry " + changeRecord.getName());
            }
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

            try {
                reader.close();
            } catch (final IOException ignored) {
                // Ignore.
            }
        }
        // --- JCite ---
    }

    private Modify() {
        // Not used.
    }
}
