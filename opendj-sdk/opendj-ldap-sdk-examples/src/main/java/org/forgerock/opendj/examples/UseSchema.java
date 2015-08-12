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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldif.LDIFEntryReader;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * This example command-line client application validates an entry
 * against the directory server schema before adding it.
 *
 * <br>
 *
 * This example takes the following command line parameters:
 *
 * <pre>
 *  {@code <host> <port> <bindDN> <bindPassword>}
 * </pre>
 *
 * Then it reads an entry to add from System.in.
 * If the entry is valid according to the directory schema,
 * it tries to add the entry to the directory.
 */
public final class UseSchema {
    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: host, port, bindDN, bindPassword.
     */
    public static void main(final String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: host port bindDN bindPassword");
            System.exit(1);
        }

        // Parse command line arguments.
        final String host         = args[0];
        final int    port         = Integer.parseInt(args[1]);
        final String bindDn       = args[2];
        final String bindPassword = args[3];

        // --- JCite ---
        // Connect and bind to the server.
        final LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port);
        Connection connection = null;

        try {
            connection = factory.getConnection();
            connection.bind(bindDn, bindPassword.toCharArray());

            // Read the schema from the directory server.
            // If that fails, use the default schema from the LDAP SDK.
            Schema schema = null;
            try {
                schema = Schema.readSchema(connection, DN.valueOf("cn=schema"));
            } catch (EntryNotFoundException e) {
                System.err.println(e.getMessage());
                schema = Schema.getDefaultSchema();
            } finally {
                if (schema == null) {
                    System.err.println("Failed to get schema.");
                    System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
                }
            }

            // Read an entry from System.in.
            final LDIFEntryReader reader = new LDIFEntryReader(System.in);
            final Entry entry = reader.readEntry();

            // If the entry is valid, try to add it. Otherwise display errors.
            final List<LocalizableMessage> schemaErrors = new LinkedList<>();
            boolean conformsToSchema = schema.validateEntry(
                    entry, SchemaValidationPolicy.defaultPolicy(), schemaErrors);
            final String entryDn = entry.getName().toString();
            Result result = null;
            if (conformsToSchema) {
                System.out.println("Processing ADD request for " + entryDn);
                result = connection.add(entry);
            } else {
                for (LocalizableMessage error : schemaErrors) {
                    System.err.println(error);
                }
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            }

            // Display the result. (A failed add results in an LdapException.)
            if (result != null) {
                System.out.println("ADD operation successful for DN " + entryDn);
            }
        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
        } catch (DecodeException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        // --- JCite ---
    }

    private UseSchema() {
        // Not used.
    }
}
