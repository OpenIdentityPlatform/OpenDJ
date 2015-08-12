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
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * An example client application which applies update operations to a directory server
 * using the asynchronous APIs.
 * The update operations are read from an LDIF file, or stdin if no filename is provided.
 * This example takes the following command line parameters,
 * reading from stdin if no LDIF file is provided:
 *
 * <pre>
 *  {@code <host> <port> <username> <password> [<ldifFile>]}
 * </pre>
 */
public final class ModifyAsync {
    /** Connection to the LDAP server. */
    private static Connection connection;
    /** Result for the modify operation. */
    private static int resultCode;
    /** Count down latch to wait for modify operation to complete. */
    private static final CountDownLatch COMPLETION_LATCH = new CountDownLatch(1);

    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: host, port, username, password,
     *            LDIF file name containing the update operations.
     *            Stdin is used if no LDIF file name is provided.
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
        final char[] password = args[3].toCharArray();

        // Create the LDIF reader using either the named file, if provided, or stdin.
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
        final String[] ldifLines = getInputLines(ldif);

        // Connect to the server, bind, and request the modifications.
        new LDAPConnectionFactory(hostName, port)
                .getConnectionAsync()
                .thenAsync(new AsyncFunction<Connection, BindResult, LdapException>() {
                    @Override
                    public Promise<BindResult, LdapException> apply(Connection connection)
                            throws LdapException {
                        ModifyAsync.connection = connection;
                        return connection.bindAsync(
                                Requests.newSimpleBindRequest(userName, password));
                    }
                })
                .thenAsync(new AsyncFunction<BindResult, Result, LdapException>() {
                    @Override
                    public Promise<Result, LdapException> apply(BindResult bindResult)
                            throws LdapException {
                        return connection.modifyAsync(
                                Requests.newModifyRequest(ldifLines));
                    }
                })
                .thenOnResult(new ResultHandler<Result>() {
                    @Override
                    public void handleResult(Result result) {
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
     * Returns the lines from the input stream.
     * @param in    The input stream.
     * @return The lines from the input stream.
     */
    private static String[] getInputLines(final InputStream in) {
        String line;
        final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        final List<String> lines = new ArrayList<>();
        try {
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
        }
        return lines.toArray(new String[lines.size()]);
    }

    private ModifyAsync() {
        // Not used.
    }
}
