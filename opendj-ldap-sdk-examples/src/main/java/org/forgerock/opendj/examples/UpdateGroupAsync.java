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
 *      Copyright 2015 ForgeRock AS.
 *
 */

package org.forgerock.opendj.examples;

import static org.forgerock.util.Utils.closeSilently;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.RootDSE;
import org.forgerock.opendj.ldap.controls.PermissiveModifyRequestControl;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.promise.ResultHandler;

import java.util.concurrent.CountDownLatch;

/**
 * This command-line client demonstrates adding and removing a member from a
 * (potentially large) static group using the asynchronous APIs.
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
public final class UpdateGroupAsync {
    /** Connection to the LDAP server. */
    private static Connection connection;
    /** Result for the operation. */
    private static int resultCode;
    /** Count down latch to wait for modify operation to complete. */
    private static final CountDownLatch COMPLETION_LATCH = new CountDownLatch(1);

    /**
     * Updates the group as necessary.
     *
     * @param args
     *            The command line arguments: host, port, group-dn, member-dn, (add|del)
     */
    public static void main(String[] args) {
        if (args.length != 5) {
            printUsage();
        }
        final String host              = args[0];
        final int port                 = Integer.parseInt(args[1]);
        final String groupDn           = args[2];
        final String memberDn          = args[3];
        final ModificationType modType = getModificationType(args[4]);

        // Connect, bind, update group.
        new LDAPConnectionFactory(host, port)
                .getConnectionAsync()
                .thenAsync(new AsyncFunction<Connection, BindResult, LdapException>() {
                    @Override
                    public Promise<BindResult, LdapException> apply(Connection connection)
                            throws LdapException {
                        UpdateGroupAsync.connection = connection;
                        return connection.bindAsync(
                                Requests.newSimpleBindRequest("cn=Directory Manager", "password".toCharArray()));
                    }
                })
                .thenAsync(new AsyncFunction<BindResult, RootDSE, LdapException>() {
                    @Override
                    public Promise<RootDSE, LdapException> apply(BindResult bindResult)
                            throws LdapException {
                        return RootDSE.readRootDSEAsync(connection, null);
                    }
                })
                .thenAsync(new AsyncFunction<RootDSE, Result, LdapException>() {
                    @Override
                    public Promise<Result, LdapException> apply(RootDSE rootDSE) throws LdapException {
                        // If the directory supports the Permissive Modify request control,
                        // then the modification type does not matter.
                        if (rootDSE.getSupportedControls().contains(PermissiveModifyRequestControl.OID)) {
                            log("Updating group membership.");
                            return connection.modifyAsync(
                                    Requests.newModifyRequest(groupDn)
                                            .addControl(PermissiveModifyRequestControl.newControl(true))
                                            .addModification(modType, "member", memberDn));
                        } else {
                            return connection
                                    // Check whether the member is present.
                                    .compareAsync(Requests.newCompareRequest(groupDn, "member", memberDn))
                                    .thenAsync(new AsyncFunction<CompareResult, Result, LdapException>() {
                                        @Override
                                        public Promise<Result, LdapException> apply(CompareResult compareResult)
                                                throws LdapException {
                                            ResultCode rc = compareResult.getResultCode();
                                            // Only add the member if missing from the group.
                                            if (modType.equals(ModificationType.ADD)
                                                    && rc.equals(ResultCode.COMPARE_FALSE)) {
                                                log("Adding " + memberDn + " to " + groupDn + ".");
                                                return connection.modifyAsync(
                                                        Requests.newModifyRequest(groupDn)
                                                                .addModification(modType, "member", memberDn));
                                            // Only delete if present in the group.
                                            } else if (modType.equals(ModificationType.DELETE)
                                                    && rc.equals(ResultCode.COMPARE_TRUE)) {
                                                log("Deleting " + memberDn + " from " + groupDn + ".");
                                                return connection.modifyAsync(
                                                        Requests.newModifyRequest(groupDn)
                                                                .addModification(modType, "member", memberDn));
                                            } else {
                                                return Promises.newResultPromise(
                                                        Responses.newResult(ResultCode.SUCCESS));
                                            }
                                        }
                                    });
                        }
                    }
                })
                .thenOnResult(new ResultHandler<Result>() {
                    @Override
                    public void handleResult(Result result) {
                        final String op = (modType == ModificationType.ADD) ? "added to" : "deleted from";
                        log(memberDn + " has been " + op + " the group " + groupDn + ".");
                        resultCode = result.getResultCode().intValue();
                        COMPLETION_LATCH.countDown();
                    }
                })
                .thenOnException(new ExceptionHandler<LdapException>() {
                    @Override
                    public void handleException(LdapException e) {
                        System.err.println(e.getMessage());
                        resultCode = e.getResult().getResultCode().intValue();
                        COMPLETION_LATCH.countDown();
                    }
                });

        try {
            COMPLETION_LATCH.await();
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_USER_CANCELLED.intValue());
            return;
        }

        closeSilently(connection);
        System.exit(resultCode);
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
     * Log a message to System.out.
     *
     * @param message   The message to write to the console.
     */
    private static void log(final String message) {
        System.out.println(message);
    }

    /**
     * Constructor not used.
     */
    private UpdateGroupAsync() {
        // Not used.
    }
}
