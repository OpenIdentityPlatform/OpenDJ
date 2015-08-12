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
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;

/**
 * An interactive command-line client that performs a search and simple bind
 * using the asynchronous APIs.
 * <br>
 * The client prompts for email address and for a password,
 * and then searches based on the email address,
 * to bind as the user with the password.
 * <br>
 * If successful, the client displays the common name from the user's entry.
 * <ul>
 * <li>host - host name of the directory server</li>
 * <li>port - port number of the directory server</li>
 * <li>base-dn - base DN for the search, e.g. dc=example,dc=com</li>
 * </ul>
 * All arguments are required.
 */
public final class SearchBindAsync {
    /** Connection to the LDAP server. */
    private static Connection connection;
    /** Email address provided by user. */
    private static String mail;
    /** Password provided by user. */
    private static char[] password;
    /** Bind DN returned by the search. */
    private static String bindDn;
    /** Result for the operation. */
    private static int resultCode;
    /** Count down latch to wait for modify operation to complete. */
    private static final CountDownLatch COMPLETION_LATCH = new CountDownLatch(1);

    /**
     * Prompts for email and password, search and bind, then display message.
     *
     * @param args
     *            The command line arguments: host, port, base-dn.
     */
    public static void main(final String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: host port base-dn");
            System.err.println("For example: localhost 1389 dc=example,dc=com");
            System.exit(1);
        }
        final String host   = args[0];
        final int    port   = Integer.parseInt(args[1]);
        final String baseDn = args[2];

        // Prompt for email address and password.
        try {
            mail = getInputLine("Email address:");
            password = getInputLine("Password:").toCharArray();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
            return;
        }

        // Connect to the server, search for the user entry based on email address, and bind.
        new LDAPConnectionFactory(host, port)
                .getConnectionAsync()
                .thenAsync(new AsyncFunction<Connection, SearchResultEntry, LdapException>() {
                    @Override
                    public Promise<SearchResultEntry, LdapException> apply(Connection connection)
                            throws LdapException {
                        SearchBindAsync.connection = connection;
                        return connection.searchSingleEntryAsync(
                                Requests.newSingleEntrySearchRequest(
                                        baseDn,
                                        SearchScope.WHOLE_SUBTREE,
                                        Filter.equality("mail", mail).toString(),
                                        "cn"));
                    }
                })
                .thenAsync(new AsyncFunction<SearchResultEntry, BindResult, LdapException>() {
                    @Override
                    public Promise<BindResult, LdapException> apply(SearchResultEntry searchResultEntry)
                            throws LdapException {
                        SearchBindAsync.bindDn = searchResultEntry.getName().toString();
                        return SearchBindAsync.connection.bindAsync(
                                Requests.newSimpleBindRequest(bindDn, password));
                    }
                })
                .thenOnResult(new ResultHandler<Result>() {
                    @Override
                    public void handleResult(Result result) {
                        if (result.getResultCode() == ResultCode.SUCCESS) {
                            System.out.println("Authenticated as " + SearchBindAsync.bindDn + ".");
                        }
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

    /**
     * Returns an input string from System.in, after prompting on System.out.
     * <br>
     * Note: The input is echoed to the display.
     *
     * @param prompt    The prompt asking for input.
     * @return An input string from System.in.
     * @throws IOException Failed to read from System.in.
     */
    private static String getInputLine(final String prompt) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print(prompt + " ");
        return reader.readLine();
    }

    /**
     * Constructor not used.
     */
    private SearchBindAsync() {
        // Not used
    }
}
