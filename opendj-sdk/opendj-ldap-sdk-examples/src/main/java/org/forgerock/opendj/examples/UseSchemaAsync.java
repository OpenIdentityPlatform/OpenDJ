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

import static org.forgerock.util.Utils.closeSilently;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.promise.ResultHandler;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * This example command-line client application validates an entry
 * against the directory server schema before adding it
 * using the asynchronous APIs.
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
public final class UseSchemaAsync {
    /** Connection to the LDAP server. */
    private static Connection connection;
    /** Result for the operation. */
    private static int resultCode;
    /** Count down latch to wait for modify operation to complete. */
    private static final CountDownLatch COMPLETION_LATCH = new CountDownLatch(1);

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
        final char[] bindPassword = args[3].toCharArray();

        // Read an entry from System.in.
        final Entry entry;
        try {
            System.out.println("Enter entry to add in LDIF format:");
            entry = new LDIFEntryReader(System.in).readEntry();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            return;
        }
        final String entryDn = entry.getName().toString();

        // Connect, bind, read schema, and add entry if valid according to schema.
        new LDAPConnectionFactory(host, port)
                .getConnectionAsync()
                .thenAsync(new AsyncFunction<Connection, BindResult, LdapException>() {
                    @Override
                    public Promise<BindResult, LdapException> apply(Connection connection)
                            throws LdapException {
                        UseSchemaAsync.connection = connection;
                        return connection.bindAsync(
                                Requests.newSimpleBindRequest(bindDn, bindPassword));
                    }
                })
                .thenAsync(new AsyncFunction<BindResult, Schema, LdapException>() {
                    @Override
                    public Promise<Schema, LdapException> apply(BindResult bindResult)
                            throws LdapException {
                        return Schema.readSchemaForEntryAsync(connection, DN.rootDN());
                    }
                })
                .thenAsync(new AsyncFunction<Schema, Result, LdapException>() {
                    @Override
                    public Promise<Result, LdapException> apply(Schema schema)
                            throws LdapException {
                        final List<LocalizableMessage> schemaErrors = new LinkedList<>();
                        boolean isValid = schema.validateEntry(
                                entry,
                                SchemaValidationPolicy.defaultPolicy(),
                                schemaErrors);
                        if (isValid) {
                            System.out.println("Processing ADD request for " + entryDn);
                            return connection.addAsync(Requests.newAddRequest(entry));
                        } else {
                            for (LocalizableMessage error : schemaErrors) {
                                System.err.println(error);
                            }
                            return Promises.newExceptionPromise(
                                    LdapException.newLdapException(
                                            ResultCode.CLIENT_SIDE_PARAM_ERROR,
                                            "Entry does not conform to schema."));
                        }
                    }
                })
                .thenOnResult(new ResultHandler<Result>() {
                    @Override
                    public void handleResult(Result result) {
                        System.out.println("ADD operation successful for DN " + entryDn);
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
        }  catch (InterruptedException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_USER_CANCELLED.intValue());
            return;
        }

        closeSilently(connection);
        System.exit(resultCode);
    }

    private UseSchemaAsync() {
        // Not used.
    }
}
