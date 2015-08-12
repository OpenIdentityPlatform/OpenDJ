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
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.TreeMapEntry;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * A command-line client that creates, updates, renames, and deletes a
 * short-lived entry in order to demonstrate LDAP write operations
 * using the asynchronous APIs.
 * <br>
 * The client takes as arguments the host and port for the directory server,
 * and expects to find the entries and access control instructions as defined in
 * <a href="http://opendj.forgerock.org/Example.ldif">Example.ldif</a>.
 *
 * <ul>
 * <li>host - host name of the directory server</li>
 * <li>port - port number of the directory server</li>
 * </ul>
 *
 * All arguments are required.
 */
public final class ShortLifeAsync {
    /** The short-lived entry. */
    private static Entry entry;
    /** Writer for displaying LDIF to System.out. */
    private static LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
    /** Connection to the LDAP server. */
    private static Connection connection;
    /** Result for the operation. */
    private static int resultCode;
    /** Count down latch to wait for modify operation to complete. */
    private static final CountDownLatch COMPLETION_LATCH = new CountDownLatch(1);

    /**
     * Adds, modifies, renames, and deletes an entry.
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
        final int    port = Integer.parseInt(args[1]);

        // User credentials of a "Directory Administrators" group member.
        // Kirsten Vaughan is authorized to create, update, and delete entries.
        //
        // Alternatively, prompt an administrator user for credentials,
        // or get the application its own account with access to update data.
        final String adminDn  = "uid=kvaughan,ou=people,dc=example,dc=com";
        final char[] adminPwd = "bribery".toCharArray();

        // Prepare an entry to add to the directory.
        final String entryDn = "cn=Bob,ou=People,dc=example,dc=com";
        entry = new LinkedHashMapEntry(entryDn)
            .addAttribute("cn", "Bob")
            .addAttribute("objectclass", "top")
            .addAttribute("objectclass", "person")
            .addAttribute("objectclass", "organizationalPerson")
            .addAttribute("objectclass", "inetOrgPerson")
            .addAttribute("mail", "subgenius@example.com")
            .addAttribute("sn", "Dobbs");

        new LDAPConnectionFactory(host, port)
                .getConnectionAsync()
                .thenAsync(new AsyncFunction<Connection, BindResult, LdapException>() {
                    @Override
                    public Promise<BindResult, LdapException> apply(Connection connection)
                            throws LdapException {
                        ShortLifeAsync.connection = connection;
                        return connection.bindAsync(
                                Requests.newSimpleBindRequest(adminDn, adminPwd));
                    }
                })
                .thenAsync(new AsyncFunction<BindResult, Result, LdapException>() {
                    @Override
                    public Promise<Result, LdapException> apply(BindResult bindResult)
                            throws LdapException {
                        log("Adding the entry...");
                        log(entry);
                        return connection.addAsync(Requests.newAddRequest(entry));
                    }
                })
                .thenAsync(new AsyncFunction<Result, Result, LdapException>() {
                    @Override
                    public Promise<Result, LdapException> apply(Result result)
                            throws LdapException {
                        Entry old = TreeMapEntry.deepCopyOfEntry(entry);
                        entry = entry
                                .replaceAttribute("mail", "spammer@example.com")
                                .addAttribute("description", "Good user gone bad");
                        log("Updating mail address, adding description...");
                        log(entry);
                        ModifyRequest request = Entries.diffEntries(old, entry);
                        return connection.modifyAsync(request);
                    }
                })
                .thenAsync(new AsyncFunction<Result, Result, LdapException>() {
                    @Override
                    public Promise<Result, LdapException> apply(Result result)
                            throws LdapException {
                        entry = entry.setName("cn=Renamed,ou=People,dc=example,dc=com");
                        log("Renaming the entry...");
                        log(entry);
                        return connection.modifyDNAsync(
                                Requests.newModifyDNRequest(entryDn, "cn=Renamed"));
                    }
                })
                .thenAsync(new AsyncFunction<Result, Result, LdapException>() {
                    @Override
                    public Promise<Result, LdapException> apply(Result result)
                            throws LdapException {
                        final String newDn = entryDn.replace("Bob", "Renamed");
                        log("Deleting " + newDn + "...");
                        return connection.deleteAsync(
                                Requests.newDeleteRequest(newDn));
                    }
                })
                .thenOnResult(new ResultHandler<Result>() {
                    @Override
                    public void handleResult(Result result) {
                        resultCode = result.getResultCode().intValue();
                        log("... done.");
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
        }  catch (InterruptedException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_USER_CANCELLED.intValue());
            return;
        }

        closeSilently(connection);
        System.exit(resultCode);
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
     * Log an entry in LDIF form.
     *
     * @param entry     The entry to log.
     */
    private static void log(final Entry entry) {
        try {
            writer.writeEntry(entry);
            writer.flush();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
        }
    }

    /**
     * Constructor not used.
     */
    private ShortLifeAsync() {
        // Not used.
    }
}
