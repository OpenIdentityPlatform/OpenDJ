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

import java.io.IOException;
import java.util.Calendar;
import java.util.Set;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/**
 * This command-line client demonstrates parsing entry attribute values to
 * objects. The client takes as arguments the host and port for the directory
 * server, and expects to find the entries and access control instructions as
 * defined in <a
 * href="http://opendj.forgerock.org/Example.ldif">Example.ldif</a>.
 */
public final class ParseAttributes {

    /**
     * Connect to the server, and then try to use some LDAP controls.
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

        // --- JCite ---
        final LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port);
        Connection connection = null;
        try {
            connection = factory.getConnection();

            // Use Kirsten Vaughan's credentials and her entry.
            String name = "uid=kvaughan,ou=People,dc=example,dc=com";
            char[] password = "bribery".toCharArray();
            connection.bind(name, password);

            // Make sure we have a timestamp to play with.
            updateEntry(connection, name, "description");

            // Read Kirsten's entry.
            final SearchResultEntry entry = connection.readEntry(name,
                    "cn", "objectClass", "hasSubordinates", "numSubordinates",
                    "isMemberOf", "modifyTimestamp");

            // Get the entry DN and some attribute values as objects.
            DN dn = entry.getName();

            Set<String> cn = entry.parseAttribute("cn").asSetOfString("");
            Set<AttributeDescription> objectClasses =
                    entry.parseAttribute("objectClass").asSetOfAttributeDescription();
            boolean hasChildren = entry.parseAttribute("hasSubordinates").asBoolean();
            int numChildren = entry.parseAttribute("numSubordinates").asInteger(0);
            Set<DN> groups = entry
                    .parseAttribute("isMemberOf")
                    .usingSchema(Schema.getDefaultSchema()).asSetOfDN();
            Calendar timestamp = entry
                    .parseAttribute("modifyTimestamp")
                    .asGeneralizedTime().toCalendar();

            // Do something with the objects.
            // ...

            // This example simply dumps what was obtained.
            entry.setName(dn);
            Entry newEntry = new LinkedHashMapEntry(name)
                .addAttribute("cn", cn.toArray())
                .addAttribute("objectClass", objectClasses.toArray())
                .addAttribute("hasChildren", hasChildren)
                .addAttribute("numChildren", numChildren)
                .addAttribute("groups", groups.toArray())
                .addAttribute("timestamp", timestamp.getTimeInMillis());

            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            writer.writeEntry(newEntry);
            writer.close();
        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
            return;
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

    /**
     * Update and entry to generate a time stamp.
     *
     * @param connection
     *            Connection to the directory server with rights to perform a
     *            modification on the entry.
     * @param name
     *            DN of the entry to modify.
     * @param attributeDescription
     *            Attribute to modify. Must take a String value.
     * @throws LdapException
     *             Modify failed.
     */
    private static void updateEntry(final Connection connection, final String name,
            final String attributeDescription) throws LdapException {
        ModifyRequest request = Requests.newModifyRequest(name)
                .addModification(ModificationType.REPLACE, attributeDescription, "This is a String.");
        connection.modify(request);
    }

    /**
     * Constructor not used.
     */
    private ParseAttributes() {
        // Not used.
    }
}
