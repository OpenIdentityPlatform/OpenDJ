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
 *      Copyright 2012 ForgeRock AS
 *
 */

package org.forgerock.opendj.examples;

import java.io.IOException;
import java.util.Collection;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RootDSE;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/**
 * This command-line client demonstrates use of LDAP controls. The client takes
 * as arguments the host and port for the directory server, and expects to find
 * the entries and access control instructions as defined in <a
 * href="http://opendj.forgerock.org/Example.ldif">Example.ldif</a>.
 *
 * This client connects as <code>cn=Directory Manager</code> with password
 * <code>password</code>. Not a best practice; in real code use application
 * specific credentials to connect, and ensure that your application has access
 * to use the LDAP controls needed.
 */
public final class Controls {
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
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        final LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port);
        Connection connection = null;
        try {
            connection = factory.getConnection();
            checkSupportedControls(connection);

            String user = "cn=Directory Manager";
            char[] password = "password".toCharArray();
            connection.bind(user, password);

            useAssertionControl(connection);
            // TODO: The rest of the supported controls

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
     * Use the LDAP assertion control to perform a trivial modification.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     */
    static void useAssertionControl(Connection connection) throws ErrorResultException {
        if (isSupported(AssertionRequestControl.OID)) {
            // Modify Babs Jensen's description if her entry does not have
            // a description, yet.
            String dn = "uid=bjensen,ou=People,dc=example,dc=com";

            ModifyRequest request = Requests.newModifyRequest(dn);
            request.addControl(AssertionRequestControl.newControl(true,
                    Filter.valueOf("!(description=*)")));
            request.addModification(ModificationType.ADD, "description",
                    "Created with the help of the LDAP assertion control");

            connection.modify(request);

            LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                writer.writeEntry(connection.readEntry(dn, "description"));
                writer.close();
            } catch (final IOException e) {
                // Ignore.
            }
        }
    }

    /**
     * Controls supported by the LDAP server.
     */
    private static Collection<String> controls;

    /**
     * Populate the list of supported LDAP control OIDs.
     *
     * @param connection
     *            Active connection to the LDAP server.
     * @throws ErrorResultException
     */
    static void checkSupportedControls(Connection connection) throws ErrorResultException {
        RootDSE dse = RootDSE.readRootDSE(connection);
        controls = dse.getSupportedControls();
    }

    /**
     * Check whether a control is supported. Call {@code checkSupportedControls}
     * first.
     *
     * @param control
     *            Check support for this control, provided by OID.
     * @return True if the control is supported.
     */
    static boolean isSupported(String control) {
        if (controls != null && !controls.isEmpty()) {
            return controls.contains(control);
        }
        return false;
    }

    /**
     * Constructor not used.
     */
    private Controls() {
        // Not used.
    }
}
