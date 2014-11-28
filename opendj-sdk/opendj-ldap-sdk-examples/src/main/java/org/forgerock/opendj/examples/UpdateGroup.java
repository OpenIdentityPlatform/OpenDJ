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

import java.util.Collection;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.RootDSE;
import org.forgerock.opendj.ldap.controls.PermissiveModifyRequestControl;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.CompareResult;

/**
 * This command-line client demonstrates adding and removing a member from a
 * (potentially large) static group.
 *
 * The client takes as arguments the host and port of the directory server, the
 * group DN, the member DN, and whether to "add" or "del" the specified member
 * from the group. The client uses the Permissive Modify control if it is
 * available to avoid having to check whether the member belongs to the group or
 * not.
 *
 * This client expects a group that is a <code>groupOfNames</code> such as:
 *
 * <pre>
 * dn: cn=My Static Group,ou=Groups,dc=example,dc=com
 * cn: My Static Group
 * objectClass: groupOfNames
 * objectClass: top
 * ou: Groups
 * member: uid=ahunter,ou=People,dc=example,dc=com
 * member: uid=bjensen,ou=People,dc=example,dc=com
 * member: uid=tmorris,ou=People,dc=example,dc=com
 * </pre>
 *
 * This client connects as <code>cn=Directory Manager</code> with password
 * <code>password</code>. Not a best practice; in real code use application
 * specific credentials to connect, and ensure that your application has access
 * to use the Permissive Modify control if your directory server supports it.
 */
public final class UpdateGroup {

    /**
     * Connect to the directory server to update the group.
     *
     * @param args
     *            The command line arguments: host, port, group-dn, member-dn,
     *            {add|del}
     */
    public static void main(String[] args) {
        if (args.length != 5) {
            printUsage();
        }
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        final String groupDN = args[2];
        final String memberDN = args[3];
        final ModificationType modType = getModificationType(args[4]);

        final LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port);
        Connection connection = null;
        try {
            connection = factory.getConnection();

            Collection<String> controls =
                    RootDSE.readRootDSE(connection).getSupportedControls();

            final String user = "cn=Directory Manager";
            final char[] password = "password".toCharArray();
            connection.bind(user, password);

            // --- JCite permissive ---
            if (controls.contains(PermissiveModifyRequestControl.OID)) {

                final ModifyRequest request = Requests.newModifyRequest(groupDN)
                        .addControl(PermissiveModifyRequestControl.newControl(true))
                        .addModification(modType, "member", memberDN);
                connection.modify(request);

            } else {
                // --- JCite permissive ---

                // --- JCite without permissive ---
                System.out.println("Checking whether the entry with DN "
                        + memberDN + " belongs to the group with DN " + groupDN
                        + "...");
                final CompareRequest request =
                        Requests.newCompareRequest(groupDN, "member", memberDN);
                CompareResult result = connection.compare(request);

                if (modType == ModificationType.ADD
                        && result.getResultCode() == ResultCode.COMPARE_FALSE) {
                    System.out.println("Member does not yet belong to group."
                            + " Adding it...");
                    final ModifyRequest addMember =
                            Requests.newModifyRequest(groupDN)
                                .addModification(modType, "member", memberDN);
                    connection.modify(addMember);
                }

                if (modType == ModificationType.DELETE
                        && result.getResultCode() == ResultCode.COMPARE_TRUE) {
                    System.out.println("Member belongs to group."
                            + " Removing it...");
                    final ModifyRequest delMember =
                            Requests.newModifyRequest(groupDN)
                                .addModification(modType, "member", memberDN);
                    connection.modify(delMember);
                }
                // --- JCite without permissive ---
            }

            String op = (modType == ModificationType.ADD) ? "added to" : "deleted from";
            System.out.println("The entry with DN " + memberDN + " has been "
                    + op + " the group with DN " + groupDN + ".");

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
     * Return the modification type for the update operation.
     * @param operation Operation specified as an argument (add or del).
     */
    private static ModificationType getModificationType(String operation) {
        final boolean isAdd = "add".equalsIgnoreCase(operation);
        if (!isAdd && !"del".equalsIgnoreCase(operation)) {
            printUsage();
        }
        return isAdd ? ModificationType.ADD : ModificationType.DELETE;
    }

    /**
     * Print usage then exit.
     */
    private static void printUsage() {
        System.err.println("Usage: host port group-dn member-dn {add|del}");
        System.err.println("For example: localhost 1389 "
                + "cn=Static,ou=Groups,dc=example,dc=com "
                + "uid=user.5150,ou=People,dc=example,dc=com "
                + "del");
        System.exit(1);
    }

    /**
     * Constructor not used.
     */
    private UpdateGroup() {
        // Not used.
    }
}
