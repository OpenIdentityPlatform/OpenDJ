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
 *      Copyright 2012-2015 ForgeRock AS
 *
 */

package org.forgerock.opendj.examples;

import java.io.IOException;
import java.util.Collection;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.RootDSE;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.SortKey;
import org.forgerock.opendj.ldap.controls.ADNotificationRequestControl;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityRequestControl;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityResponseControl;
import org.forgerock.opendj.ldap.controls.EntryChangeNotificationResponseControl;
import org.forgerock.opendj.ldap.controls.GetEffectiveRightsRequestControl;
import org.forgerock.opendj.ldap.controls.ManageDsaITRequestControl;
import org.forgerock.opendj.ldap.controls.MatchedValuesRequestControl;
import org.forgerock.opendj.ldap.controls.PasswordExpiredResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordExpiringResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyRequestControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyResponseControl;
import org.forgerock.opendj.ldap.controls.PermissiveModifyRequestControl;
import org.forgerock.opendj.ldap.controls.PersistentSearchChangeType;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.controls.ServerSideSortRequestControl;
import org.forgerock.opendj.ldap.controls.ServerSideSortResponseControl;
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl;
import org.forgerock.opendj.ldap.controls.SubentriesRequestControl;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.controls.VirtualListViewRequestControl;
import org.forgerock.opendj.ldap.controls.VirtualListViewResponseControl;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Result;
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

            // Uncomment a method to run one of the examples.

            //useADNotificationRequestControl(connection);
            //useAssertionControl(connection);
            useAuthorizationIdentityRequestControl(connection);
            // For the EntryChangeNotificationResponseControl see
            // usePersistentSearchRequestControl()
            //useGetEffectiveRightsRequestControl(connection);
            //useManageDsaITRequestControl(connection);
            //useMatchedValuesRequestControl(connection);
            //usePasswordExpiredResponseControl(connection);
            //usePasswordExpiringResponseControl(connection);
            //usePasswordPolicyRequestControl(connection);
            //usePermissiveModifyRequestControl(connection);
            //usePersistentSearchRequestControl(connection);
            //usePostReadRequestControl(connection);
            //usePreReadRequestControl(connection);
            //useProxiedAuthV2RequestControl(connection);
            //useServerSideSortRequestControl(connection);
            //useSimplePagedResultsControl(connection);
            //useSubentriesRequestControl(connection);
            //useSubtreeDeleteRequestControl(connection);
            //useVirtualListViewRequestControl(connection);

        } catch (final LdapException e) {
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
     * Use the <a
     * href="http://msdn.microsoft.com/en-us/library/windows/desktop/ms676877(v=vs.85).aspx"
     * >Microsoft LDAP Notification control</a>
     * to register a change notification request for a search
     * on Microsoft Active Directory.
     * <p/>
     * This client binds to Active Directory as
     * {@code cn=Administrator,cn=users,dc=example,dc=com}
     * with password {@code password},
     * and expects entries under {@code dc=example,dc=com}.
     *
     * @param connection Active connection to Active Directory server.
     * @throws LdapException Operation failed.
     */
    static void useADNotificationRequestControl(Connection connection) throws LdapException {

        // --- JCite ADNotification ---
        final String user = "cn=Administrator,cn=users,dc=example,dc=com";
        final char[] password = "password".toCharArray();
        connection.bind(user, password);

        final String[] attributes = {"cn",
            ADNotificationRequestControl.IS_DELETED_ATTR,
            ADNotificationRequestControl.WHEN_CHANGED_ATTR,
            ADNotificationRequestControl.WHEN_CREATED_ATTR};

        SearchRequest request =
                Requests.newSearchRequest("dc=example,dc=com",
                        SearchScope.WHOLE_SUBTREE, "(objectclass=*)", attributes)
                        .addControl(ADNotificationRequestControl.newControl(true));

        ConnectionEntryReader reader = connection.search(request);

        try {
            while (reader.hasNext()) {
                if (!reader.isReference()) {
                    SearchResultEntry entry = reader.readEntry(); // Updated entry
                    final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);

                    Boolean isDeleted = entry.parseAttribute(
                            ADNotificationRequestControl.IS_DELETED_ATTR
                    ).asBoolean();
                    if (isDeleted != null && isDeleted) {
                        // Handle entry deletion
                        writer.writeComment("Deleted entry: " + entry.getName());
                        writer.writeEntry(entry);
                        writer.flush();
                    }
                    String whenCreated = entry.parseAttribute(
                            ADNotificationRequestControl.WHEN_CREATED_ATTR)
                            .asString();
                    String whenChanged = entry.parseAttribute(
                            ADNotificationRequestControl.WHEN_CHANGED_ATTR)
                            .asString();
                    if (whenCreated != null && whenChanged != null) {
                        if (whenCreated.equals(whenChanged)) {
                            // Handle entry addition
                            writer.writeComment("Added entry: " + entry.getName());
                            writer.writeEntry(entry);
                            writer.flush();
                        } else {
                            // Handle entry modification
                            writer.writeComment("Modified entry: " + entry.getName());
                            writer.writeEntry(entry);
                            writer.flush();
                        }
                    }
                } else {
                    reader.readReference(); // Read and ignore reference
                }
            }
        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
        } catch (final SearchResultReferenceIOException e) {
            System.err.println("Got search reference(s): " + e.getReference().getURIs());
        } catch (final IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
        }
        // --- JCite ADNotification ---
    }

    /**
     * Use the LDAP assertion control to modify Babs Jensen's description if
     * her entry does not have a description, yet.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useAssertionControl(Connection connection) throws LdapException {
        // --- JCite assertion ---
        if (isSupported(AssertionRequestControl.OID)) {
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";

            final ModifyRequest request =
                    Requests.newModifyRequest(dn)
                        .addControl(AssertionRequestControl.newControl(
                                true, Filter.valueOf("!(description=*)")))
                        .addModification(ModificationType.ADD, "description",
                                "Created using LDAP assertion control");

            connection.modify(request);

            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                writer.writeEntry(connection.readEntry(dn, "description"));
                writer.close();
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            }
        } else {
            System.err.println("AssertionRequestControl not supported.");
        }
        // --- JCite assertion ---
    }

    /**
     * Use the LDAP Authorization Identity Controls to get the authorization ID.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useAuthorizationIdentityRequestControl(Connection connection) throws LdapException {
        // --- JCite authzid ---
        if (isSupported(AuthorizationIdentityRequestControl.OID)) {
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";
            final char[] pwd = "hifalutin".toCharArray();

            System.out.println("Binding as " + dn);
            final BindRequest request =
                    Requests.newSimpleBindRequest(dn, pwd)
                        .addControl(AuthorizationIdentityRequestControl.newControl(true));

            final BindResult result = connection.bind(request);
            try {
                final AuthorizationIdentityResponseControl control =
                        result.getControl(AuthorizationIdentityResponseControl.DECODER,
                                new DecodeOptions());
                System.out.println("Authorization ID returned: "
                                + control.getAuthorizationID());
            } catch (final DecodeException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
            }
        } else {
            System.err.println("AuthorizationIdentityRequestControl not supported.");
        }
        // --- JCite authzid ---
    }

    /**
     * Use the GetEffectiveRights Request Control to determine what sort of
     * access a user has to particular attributes on an entry.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useGetEffectiveRightsRequestControl(Connection connection) throws LdapException {
        // --- JCite effective rights ---
        if (isSupported(GetEffectiveRightsRequestControl.OID)) {
            final String authDN = "uid=kvaughan,ou=People,dc=example,dc=com";

            final SearchRequest request =
                    Requests.newSearchRequest(
                            "dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                            "(uid=bjensen)", "cn", "aclRights", "aclRightsInfo")
                            .addControl(GetEffectiveRightsRequestControl.newControl(
                                    true, authDN, "cn"));

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
            } catch (final LdapException e) {
                System.err.println(e.getMessage());
                System.exit(e.getResult().getResultCode().intValue());
            } catch (final SearchResultReferenceIOException e) {
                System.err.println("Got search reference(s): " + e.getReference().getURIs());
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            }
        } else {
            System.err.println("GetEffectiveRightsRequestControl not supported.");
        }
        // --- JCite effective rights ---
    }

    /**
     * Use the ManageDsaIT Request Control to show the difference between a
     * referral accessed with and without use of the control.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useManageDsaITRequestControl(Connection connection) throws LdapException {
        // --- JCite manage DsaIT ---
        if (isSupported(ManageDsaITRequestControl.OID)) {
            final String dn = "dc=ref,dc=com";

            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                System.out.println("Referral without the ManageDsaIT control.");
                SearchRequest request = Requests.newSearchRequest(dn,
                        SearchScope.SUBORDINATES, "(objectclass=*)", "");
                final ConnectionEntryReader reader = connection.search(request);
                while (reader.hasNext()) {
                    if (reader.isReference()) {
                        final SearchResultReference ref = reader.readReference();
                        System.out.println("Reference: " + ref.getURIs());
                    }
                }

                System.out.println("Referral with the ManageDsaIT control.");
                request.addControl(ManageDsaITRequestControl.newControl(true));
                final SearchResultEntry entry = connection.searchSingleEntry(request);
                writer.writeEntry(entry);
                writer.close();
            } catch (final LdapException e) {
                System.err.println(e.getMessage());
                System.exit(e.getResult().getResultCode().intValue());
            } catch (final SearchResultReferenceIOException e) {
                System.err.println("Got search reference(s): " + e.getReference().getURIs());
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            }
        } else {
            System.err.println("ManageDsaITRequestControl not supported.");
        }
        // --- JCite manage DsaIT ---
    }

    /**
     * Use the Matched Values Request Control to show read only one attribute
     * value.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useMatchedValuesRequestControl(Connection connection) throws LdapException {
        // --- JCite matched values ---
        if (isSupported(MatchedValuesRequestControl.OID)) {
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";
            final SearchRequest request =
                    Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT,
                            "(objectclass=*)", "cn")
                            .addControl(MatchedValuesRequestControl.newControl(
                                    true, "(cn=Babs Jensen)"));

            final SearchResultEntry entry = connection.searchSingleEntry(request);
            System.out.println("Reading entry with matched values request.");
            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                writer.writeEntry(entry);
                writer.close();
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            }
        } else {
            System.err.println("MatchedValuesRequestControl not supported.");
        }
        // --- JCite matched values ---
    }

    /**
     * Check the Password Expired Response Control. To get this code to output
     * something, you must first set up an appropriate password policy and wait
     * for Barbara Jensen's password to expire.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     */
    static void usePasswordExpiredResponseControl(Connection connection) {
        // --- JCite password expired ---
        if (isSupported(PasswordExpiredResponseControl.OID)) {
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";
            final char[] pwd = "hifalutin".toCharArray();

            try {
                connection.bind(dn, pwd);
            } catch (final LdapException e) {
                final Result result = e.getResult();
                try {
                    final PasswordExpiredResponseControl control =
                            result.getControl(PasswordExpiredResponseControl.DECODER,
                                    new DecodeOptions());
                    if (control != null && control.hasValue()) {
                        System.out.println("Password expired for " + dn);
                    }
                } catch (final DecodeException de) {
                    System.err.println(de.getMessage());
                    System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
                }
            }
        } else {
            System.err.println("PasswordExpiredResponseControl not supported.");
        }
        // --- JCite password expired ---
    }

    /**
     * Check the Password Expiring Response Control. To get this code to output
     * something, you must first set up an appropriate password policy and wait
     * for Barbara Jensen's password to get old enough that the server starts
     * warning about expiration.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void usePasswordExpiringResponseControl(Connection connection) throws LdapException {
        // --- JCite password expiring ---
        if (isSupported(PasswordExpiringResponseControl.OID)) {
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";
            final char[] pwd = "hifalutin".toCharArray();

            final BindResult result = connection.bind(dn, pwd);
            try {
                final PasswordExpiringResponseControl control =
                        result.getControl(PasswordExpiringResponseControl.DECODER,
                                new DecodeOptions());
                if (control != null && control.hasValue()) {
                    System.out.println("Password for " + dn + " expires in "
                            + control.getSecondsUntilExpiration() + " seconds.");
                }
            } catch (final DecodeException de) {
                System.err.println(de.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
            }
        } else {
            System.err.println("PasswordExpiringResponseControl not supported");
        }
        // --- JCite password expiring ---
    }

    /**
     * Use the Password Policy Request and Response Controls. To get this code
     * to output something, you must first set up an appropriate password policy
     * and wait for Barbara Jensen's password to get old enough that the server
     * starts warning about expiration, or for the password to expire.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     */
    static void usePasswordPolicyRequestControl(Connection connection) {
        // --- JCite password policy ---
        if (isSupported(PasswordPolicyRequestControl.OID)) {
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";
            final char[] pwd = "hifalutin".toCharArray();

            try {
                final BindRequest request = Requests.newSimpleBindRequest(dn, pwd)
                        .addControl(PasswordPolicyRequestControl.newControl(true));

                final BindResult result = connection.bind(request);

                final PasswordPolicyResponseControl control =
                        result.getControl(PasswordPolicyResponseControl.DECODER,
                                new DecodeOptions());
                if (control != null && control.getWarningType() != null) {
                    System.out.println("Password policy warning "
                            + control.getWarningType() + ", value "
                            + control.getWarningValue() + " for " + dn);
                }
            } catch (final LdapException e) {
                final Result result = e.getResult();
                try {
                    final PasswordPolicyResponseControl control =
                            result.getControl(PasswordPolicyResponseControl.DECODER,
                                    new DecodeOptions());
                    if (control != null) {
                        System.out.println("Password policy error "
                                + control.getErrorType() + " for " + dn);
                    }
                } catch (final DecodeException de) {
                    System.err.println(de.getMessage());
                    System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
                }
            } catch (final DecodeException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
            }
        } else {
            System.err.println("PasswordPolicyRequestControl not supported");
        }
        // --- JCite password policy ---
    }

    /**
     * Use Permissive Modify Request Control to try to add an attribute that
     * already exists.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void usePermissiveModifyRequestControl(Connection connection) throws LdapException {
        // --- JCite permissive modify ---
        if (isSupported(PermissiveModifyRequestControl.OID)) {
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";

            final ModifyRequest request =
                    Requests.newModifyRequest(dn)
                        .addControl(PermissiveModifyRequestControl.newControl(true))
                        .addModification(ModificationType.ADD, "uid", "bjensen");

            connection.modify(request);
            System.out.println("Permissive modify did not complain about "
                    + "attempt to add uid: bjensen to " + dn + ".");
        } else {
            System.err.println("PermissiveModifyRequestControl not supported");
        }
        // --- JCite permissive modify ---
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
     * @throws LdapException
     *             Operation failed.
     */
    static void usePersistentSearchRequestControl(Connection connection) throws LdapException {
        // --- JCite psearch ---
        if (isSupported(PersistentSearchRequestControl.OID)) {
            final SearchRequest request =
                    Requests.newSearchRequest(
                            "dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
                            "(objectclass=inetOrgPerson)", "cn")
                            .addControl(PersistentSearchRequestControl.newControl(
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
                        System.out.println("Entry changed: " + entry.getName());

                        final EntryChangeNotificationResponseControl control =
                                entry.getControl(
                                        EntryChangeNotificationResponseControl.DECODER,
                                        new DecodeOptions());

                        final PersistentSearchChangeType type = control.getChangeType();
                        System.out.println("Change type: " + type);
                        if (type.equals(PersistentSearchChangeType.MODIFY_DN)) {
                            System.out.println("Previous DN: " + control.getPreviousName());
                        }
                        System.out.println("Change number: " + control.getChangeNumber());
                        System.out.println(); // Add a blank line.
                    }
                }
            } catch (final DecodeException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
            } catch (final LdapException e) {
                System.err.println(e.getMessage());
                System.exit(e.getResult().getResultCode().intValue());
            } catch (final SearchResultReferenceIOException e) {
                System.err.println("Got search reference(s): " + e.getReference().getURIs());
            }
        } else {
            System.err.println("PersistentSearchRequestControl not supported.");
        }
        // --- JCite psearch ---
    }


    /**
     * Use Post Read Controls to get entry content after a modification.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void usePostReadRequestControl(Connection connection) throws LdapException {
        // --- JCite post read ---
        if (isSupported(PostReadRequestControl.OID)) {
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";

            final ModifyRequest request =
                    Requests.newModifyRequest(dn)
                    .addControl(PostReadRequestControl.newControl(true, "description"))
                    .addModification(ModificationType.REPLACE,
                            "description", "Using the PostReadRequestControl");

            final Result result = connection.modify(request);
            try {
                final PostReadResponseControl control =
                        result.getControl(PostReadResponseControl.DECODER,
                                new DecodeOptions());
                final Entry entry = control.getEntry();

                final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
                writer.writeEntry(entry);
                writer.close();
            } catch (final DecodeException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            }
        } else {
            System.err.println("PostReadRequestControl not supported");
        }
        // --- JCite post read ---
    }

    /**
     * Use Pre Read Controls to get entry content before a modification.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void usePreReadRequestControl(Connection connection) throws LdapException {
        // --- JCite pre read ---
        if (isSupported(PreReadRequestControl.OID)) {
            final String dn = "uid=bjensen,ou=People,dc=example,dc=com";

            final ModifyRequest request =
                    Requests.newModifyRequest(dn)
                    .addControl(PreReadRequestControl.newControl(true, "mail"))
                    .addModification(
                            ModificationType.REPLACE, "mail", "modified@example.com");

            final Result result = connection.modify(request);
            try {
                final PreReadResponseControl control =
                        result.getControl(PreReadResponseControl.DECODER,
                                new DecodeOptions());
                final Entry entry = control.getEntry();

                final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
                writer.writeEntry(entry);
                writer.close();
            } catch (final DecodeException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            }
        } else {
            System.err.println("PreReadRequestControl not supported");
        }
        // --- JCite pre read ---
    }

    /**
     * Use proxied authorization to modify an identity as another user.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useProxiedAuthV2RequestControl(Connection connection) throws LdapException {
        // --- JCite proxied authzv2 ---
        if (isSupported(ProxiedAuthV2RequestControl.OID)) {
            final String bindDN = "cn=My App,ou=Apps,dc=example,dc=com";
            final String targetDn = "uid=bjensen,ou=People,dc=example,dc=com";
            final String authzId = "dn:uid=kvaughan,ou=People,dc=example,dc=com";

            final ModifyRequest request =
                    Requests.newModifyRequest(targetDn)
                    .addControl(ProxiedAuthV2RequestControl.newControl(authzId))
                    .addModification(ModificationType.REPLACE, "description",
                            "Done with proxied authz");

            connection.bind(bindDN, "password".toCharArray());
            connection.modify(request);
            final Entry entry = connection.readEntry(targetDn, "description");

            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                writer.writeEntry(entry);
                writer.close();
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            }
        } else {
            System.err.println("ProxiedAuthV2RequestControl not supported");
        }
        // --- JCite proxied authzv2 ---
    }

    /**
     * Use the server-side sort controls.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    // --- JCite server-side sort ---
    static void useServerSideSortRequestControl(Connection connection) throws LdapException {
        if (isSupported(ServerSideSortRequestControl.OID)) {
            final SearchRequest request =
                    Requests.newSearchRequest("ou=People,dc=example,dc=com",
                            SearchScope.WHOLE_SUBTREE, "(sn=Jensen)", "cn")
                            .addControl(ServerSideSortRequestControl.newControl(
                                            true, new SortKey("cn")));

            final SearchResultHandler resultHandler = new MySearchResultHandler();
            final Result result = connection.search(request, resultHandler);

            try {
                final ServerSideSortResponseControl control =
                        result.getControl(ServerSideSortResponseControl.DECODER,
                                new DecodeOptions());
                if (control != null && control.getResult() == ResultCode.SUCCESS) {
                    System.out.println("# Entries are sorted.");
                } else {
                    System.out.println("# Entries not necessarily sorted");
                }
            } catch (final DecodeException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
            }
        } else {
            System.err.println("ServerSideSortRequestControl not supported");
        }
    }

    private static class MySearchResultHandler implements SearchResultHandler {

        @Override
        public boolean handleEntry(SearchResultEntry entry) {
            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                writer.writeEntry(entry);
                writer.flush();
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                return false;
            }
            return true;
        }

        @Override
        public boolean handleReference(SearchResultReference reference) {
            System.out.println("Got a reference: " + reference);
            return false;
        }
    }
    // --- JCite server-side sort ---

    /**
     * Use the simple paged results mechanism.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useSimplePagedResultsControl(Connection connection) throws LdapException {
        // --- JCite simple paged results ---
        if (isSupported(SimplePagedResultsControl.OID)) {
            ByteString cookie = ByteString.empty();
            SearchRequest request;
            final SearchResultHandler resultHandler = new MySearchResultHandler();
            Result result;

            int page = 1;
            do {
                System.out.println("# Simple paged results: Page " + page);

                request =
                        Requests.newSearchRequest("dc=example,dc=com",
                                SearchScope.WHOLE_SUBTREE, "(sn=Jensen)", "cn")
                                .addControl(SimplePagedResultsControl.newControl(
                                        true, 3, cookie));

                result = connection.search(request, resultHandler);
                try {
                    SimplePagedResultsControl control =
                            result.getControl(SimplePagedResultsControl.DECODER,
                                    new DecodeOptions());
                    cookie = control.getCookie();
                } catch (final DecodeException e) {
                    System.err.println(e.getMessage());
                    System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
                }

                ++page;
            } while (cookie.length() != 0);
        } else {
            System.err.println("SimplePagedResultsControl not supported");
        }
        // --- JCite simple paged results ---
    }

    /**
     * Use the subentries request control.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useSubentriesRequestControl(Connection connection) throws LdapException {
        // --- JCite subentries ---
        if (isSupported(SubentriesRequestControl.OID)) {
            final SearchRequest request =
                    Requests.newSearchRequest("dc=example,dc=com",
                                SearchScope.WHOLE_SUBTREE,
                                "cn=*Class of Service", "cn", "subtreeSpecification")
                            .addControl(SubentriesRequestControl.newControl(
                                true, true));

            final ConnectionEntryReader reader = connection.search(request);
            final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
            try {
                while (reader.hasNext()) {
                    if (reader.isEntry()) {
                        final SearchResultEntry entry = reader.readEntry();
                        writer.writeEntry(entry);
                    }
                }
                writer.close();
            } catch (final LdapException e) {
                System.err.println(e.getMessage());
                System.exit(e.getResult().getResultCode().intValue());
            } catch (final SearchResultReferenceIOException e) {
                System.err.println("Got search reference(s): " + e.getReference().getURIs());
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            }
        } else {
            System.err.println("SubentriesRequestControl not supported");
        }
        // --- JCite subentries ---
    }

    /**
     * Use the subtree delete control.
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useSubtreeDeleteRequestControl(Connection connection) throws LdapException {
        // --- JCite tree delete ---
        if (isSupported(SubtreeDeleteRequestControl.OID)) {

            final String dn = "ou=Apps,dc=example,dc=com";
            final DeleteRequest request =
                    Requests.newDeleteRequest(dn)
                            .addControl(SubtreeDeleteRequestControl.newControl(true));

            final Result result = connection.delete(request);
            if (result.isSuccess()) {
                System.out.println("Successfully deleted " + dn
                        + " and all entries below.");
            } else {
                System.err.println("Result: " + result.getDiagnosticMessage());
            }
        } else {
            System.err.println("SubtreeDeleteRequestControl not supported");
        }
        // --- JCite tree delete ---
    }

    /**
     * Use the virtual list view controls. In order to set up OpenDJ directory
     * server to produce the following output with the example code, use OpenDJ
     * Control Panel &gt; Manage Indexes &gt; New VLV Index... to set up a
     * virtual list view index for people by last name, using the filter
     * {@code (|(givenName=*)(sn=*))}, and sorting first by surname, {@code sn},
     * in ascending order, then by given name also in ascending order
     *
     * @param connection
     *            Active connection to LDAP server containing <a
     *            href="http://opendj.forgerock.org/Example.ldif"
     *            >Example.ldif</a> content.
     * @throws LdapException
     *             Operation failed.
     */
    static void useVirtualListViewRequestControl(Connection connection) throws LdapException {
        // --- JCite vlv ---
        if (isSupported(VirtualListViewRequestControl.OID)) {
            ByteString contextID = ByteString.empty();

            // Add a window of 2 entries on either side of the first sn=Jensen entry.
            final SearchRequest request =
                    Requests.newSearchRequest("ou=People,dc=example,dc=com",
                            SearchScope.WHOLE_SUBTREE, "(sn=*)", "sn", "givenName")
                            .addControl(ServerSideSortRequestControl.newControl(
                                    true, new SortKey("sn")))
                            .addControl(
                                    VirtualListViewRequestControl.newAssertionControl(
                                            true,
                                            ByteString.valueOfUtf8("Jensen"),
                                            2, 2, contextID));

            final SearchResultHandler resultHandler = new MySearchResultHandler();
            final Result result = connection.search(request, resultHandler);

            try {
                final ServerSideSortResponseControl sssControl =
                        result.getControl(ServerSideSortResponseControl.DECODER,
                                new DecodeOptions());
                if (sssControl != null && sssControl.getResult() == ResultCode.SUCCESS) {
                    System.out.println("# Entries are sorted.");
                } else {
                    System.out.println("# Entries not necessarily sorted");
                }

                final VirtualListViewResponseControl vlvControl =
                        result.getControl(VirtualListViewResponseControl.DECODER,
                                new DecodeOptions());
                System.out.println("# Position in list: "
                        + vlvControl.getTargetPosition() + "/"
                        + vlvControl.getContentCount());
            } catch (final DecodeException e) {
                System.err.println(e.getMessage());
                System.exit(ResultCode.CLIENT_SIDE_DECODING_ERROR.intValue());
            }
        } else {
            System.err.println("VirtualListViewRequestControl not supported");
        }
        // --- JCite vlv ---
    }

    // --- JCite check support ---
    /**
     * Controls supported by the LDAP server.
     */
    private static Collection<String> controls;

    /**
     * Populate the list of supported LDAP control OIDs.
     *
     * @param connection
     *            Active connection to the LDAP server.
     * @throws LdapException
     *             Failed to get list of controls.
     */
    static void checkSupportedControls(Connection connection) throws LdapException {
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
        return controls != null && controls.contains(control);
    }
    // --- JCite check support ---

    /**
     * Constructor not used.
     */
    private Controls() {
        // Not used.
    }
}
