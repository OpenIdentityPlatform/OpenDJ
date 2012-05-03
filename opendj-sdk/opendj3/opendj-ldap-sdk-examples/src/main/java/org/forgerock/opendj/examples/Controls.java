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
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RootDSE;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityRequestControl;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityResponseControl;
import org.forgerock.opendj.ldap.controls.EntryChangeNotificationResponseControl;
import org.forgerock.opendj.ldap.controls.GetEffectiveRightsRequestControl;
import org.forgerock.opendj.ldap.controls.ManageDsaITRequestControl;
import org.forgerock.opendj.ldap.controls.PersistentSearchChangeType;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
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
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);

        final LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port);
        Connection connection = null;
        try {
            connection = factory.getConnection();
            checkSupportedControls(connection);

            final String user = "cn=Directory Manager";
            final char[] password = "password".toCharArray();
            connection.bind(user, password);

            // Uncomment one of the methods:

            //useAssertionControl(connection);
            //useAuthorizationIdentityRequestControl(connection);
            // For the EntryChangeNotificationResponseControl see
            // usePersistentSearchRequestControl()
            //useGetEffectiveRightsRequestControl(connection);
            //usePersistentSearchRequestControl(connection);
            useManageDsaITRequestControl(connection);
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
     * @throws ErrorResultException
     *             Operation failed.
     */
    static void useAssertionControl(Connection connection) throws ErrorResultException {
        if (isSupported(AssertionRequestControl.OID)) {
            // Modify Babs Jensen's description if her entry does not have
            // a description, yet.
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";

            ModifyRequest request = Requests.newModifyRequest(dn);
            request.addControl(AssertionRequestControl.newControl(true, Filter
                    .valueOf("!(description=*)")));
            request.addModification(ModificationType.ADD, "description",
                    "Created with the help of the LDAP assertion control");

            connection.modify(request);

            LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                writer.writeEntry(connection.readEntry(dn, "description"));
                writer.close();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Use the LDAP Authorization Identity Controls to get the authorization ID.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws ErrorResultException
     *             Operation failed.
     */
    static void useAuthorizationIdentityRequestControl(Connection connection) throws ErrorResultException {
        if (isSupported(AuthorizationIdentityRequestControl.OID)) {
            final String name = "uid=bjensen,ou=People,dc=example,dc=com";
            final char[] password = "hifalutin".toCharArray();

            System.out.println("Binding as " + name);
            BindRequest request = Requests.newSimpleBindRequest(name, password);
            request.addControl(AuthorizationIdentityRequestControl.newControl(true));

            final BindResult result = connection.bind(request);
            try {
                final AuthorizationIdentityResponseControl control =
                        result.getControl(AuthorizationIdentityResponseControl.DECODER,
                                new DecodeOptions());
                System.out.println("Authorization ID returned: "
                                + control.getAuthorizationID());
            } catch (final DecodeException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Use the GetEffectiveRights Request Control to determine what sort of
     * access a user has to particular attributes on an entry.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws ErrorResultException
     *             Operation failed.
     */
    static void useGetEffectiveRightsRequestControl(Connection connection)
            throws ErrorResultException {
        if (isSupported(GetEffectiveRightsRequestControl.OID)) {
            final String authDN = "uid=kvaughan,ou=People,dc=example,dc=com";

            SearchRequest request =
                    Requests.newSearchRequest(
                            "dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                            "(uid=bjensen)", "cn", "aclRights", "aclRightsInfo");
            request.addControl(
                    GetEffectiveRightsRequestControl.newControl(true, authDN, "cn"));

            final ConnectionEntryReader reader = connection.search(request);
            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                while (reader.hasNext()) {
                    if (!reader.isReference()) {
                        final SearchResultEntry entry = reader.readEntry();
                        writer.writeEntry(entry);
                    }
                }
                writer.close();
            } catch (final ErrorResultIOException e) {
                e.printStackTrace();
            } catch (final SearchResultReferenceIOException e) {
                e.printStackTrace();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Use the ManageDsaIT Request Control to show the difference between a
     * referral accessed with and without use of the control.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws ErrorResultException
     *             Operation failed.
     */
    static void useManageDsaITRequestControl(Connection connection) throws ErrorResultException {
        if (isSupported(ManageDsaITRequestControl.OID)) {
            // This entry is a referral object:
            final String dn = "dc=references,dc=example,dc=com";

            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                System.out.println("Referral without the ManageDsaIT control.");
                SearchRequest request = Requests.newSearchRequest(dn,
                        SearchScope.BASE_OBJECT, "(objectclass=*)", "");
                final ConnectionEntryReader reader = connection.search(request);
                while (reader.hasNext()) {
                    if (reader.isReference()) {
                        final SearchResultReference ref = reader.readReference();
                        System.out.println("Reference: " + ref.getURIs().toString());
                    }
                }

                System.out.println("Referral with the ManageDsaIT control.");
                request.addControl(ManageDsaITRequestControl.newControl(true));
                final SearchResultEntry entry = connection.searchSingleEntry(request);
                writer.writeEntry(entry);
                writer.close();
            } catch (final ErrorResultIOException e) {
                e.printStackTrace();
            } catch (final SearchResultReferenceIOException e) {
                e.printStackTrace();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * Use the LDAP PersistentSearchRequestControl to set up a persistent
     * search. Also use the Entry Change Notification Response Control to get
     * details about why an entry was returned for a persistent search.
     *
     * After you set this up, use another application to make changes to user
     * entries under dc=example,dc=com.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws ErrorResultException
     *             Operation failed.
     */
    static void usePersistentSearchRequestControl(Connection connection) throws ErrorResultException {
        if (isSupported(PersistentSearchRequestControl.OID)) {
            SearchRequest request =
                    Requests.newSearchRequest(
                            "dc=example,dc=com",
                            SearchScope.WHOLE_SUBTREE,
                            "(objectclass=inetOrgPerson)",
                            "cn");
            request.addControl(PersistentSearchRequestControl.newControl(
                    true, true, true, // isCritical, changesOnly, returnECs
                    PersistentSearchChangeType.ADD,
                    PersistentSearchChangeType.DELETE,
                    PersistentSearchChangeType.MODIFY,
                    PersistentSearchChangeType.MODIFY_DN));

            final ConnectionEntryReader reader = connection.search(request);

            try {
                while (reader.hasNext()) {
                    if (!reader.isReference()) {
                        final SearchResultEntry entry = reader.readEntry();
                        System.out.println("Entry changed: " + entry.getName().toString());

                        final EntryChangeNotificationResponseControl control =
                                entry.getControl(
                                        EntryChangeNotificationResponseControl.DECODER,
                                        new DecodeOptions());

                        final PersistentSearchChangeType type = control.getChangeType();
                        System.out.println("Change type: " + type.toString());
                        if (type.equals(PersistentSearchChangeType.MODIFY_DN)) {
                            System.out.println("Previous DN: "
                                    + control.getPreviousName().toString());
                        }
                        System.out.println("Change number: " + control.getChangeNumber());
                        System.out.println(); // Add a blank line.
                    }
                }
            } catch (final DecodeException e) {
                e.printStackTrace();
            } catch (final ErrorResultIOException e) {
                e.printStackTrace();
            } catch (final SearchResultReferenceIOException e) {
                e.printStackTrace();
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
     *             Failed to get list of controls.
     */
    static void checkSupportedControls(Connection connection) throws ErrorResultException {
        controls = RootDSE.readRootDSE(connection).getSupportedControls();
    }

    /**
     * Check whether a control is supported. Call {@code checkSupportedControls}
     * first.
     *
     * @param control
     *            Check support for this control, provided by OID.
     * @return True if the control is supported.
     */
    static boolean isSupported(final String control) {
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
