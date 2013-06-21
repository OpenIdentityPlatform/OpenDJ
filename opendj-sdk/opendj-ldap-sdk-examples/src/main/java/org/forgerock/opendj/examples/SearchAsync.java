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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.examples;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CancelExtendedRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/**
 * An example client application which searches a Directory Server using the
 * asynchronous APIs. This example takes the following command line parameters:
 *
 * <pre>
 *  &lt;host> &lt;port> &lt;username> &lt;password>
 *      &lt;baseDN> &lt;scope> &lt;filter> [&lt;attibute> &lt;attribute> ...]
 * </pre>
 */
public final class SearchAsync {
    private static final class BindResultHandlerImpl implements ResultHandler<BindResult> {

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleErrorResult(final ErrorResultException error) {
            System.err.println(error.getMessage());
            resultCode = error.getResult().getResultCode().intValue();
            COMPLETION_LATCH.countDown();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleResult(final BindResult result) {
            // Bind succeeded: initiate search.
            final SearchRequest request =
                    Requests.newSearchRequest(baseDN, scope, filter, attributes);
            final FutureResult<Result> futureResult =
                    connection.searchAsync(request, null, new SearchResultHandlerImpl());
            requestID = futureResult.getRequestID();
        }

    }

    private static final class ConnectResultHandlerImpl implements ResultHandler<Connection> {

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleErrorResult(final ErrorResultException error) {
            System.err.println(error.getMessage());
            resultCode = error.getResult().getResultCode().intValue();
            COMPLETION_LATCH.countDown();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleResult(final Connection connection) {
            // Connect succeeded: save connection and initiate bind.
            SearchAsync.connection = connection;

            final BindRequest request =
                    Requests.newSimpleBindRequest(userName, password.toCharArray());
            connection.bindAsync(request, null, new BindResultHandlerImpl());
        }

    }

    private static final class SearchResultHandlerImpl implements SearchResultHandler {

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized boolean handleEntry(final SearchResultEntry entry) {
            try {
                if (entryCount < 10) {
                    WRITER.writeComment("Search result entry: " + entry.getName().toString());
                    WRITER.writeEntry(entry);
                    ++entryCount;
                } else { // Cancel the search.
                    CancelExtendedRequest request = Requests.newCancelExtendedRequest(requestID);
                    connection.extendedRequestAsync(request, null, new CancelResultHandlerImpl());
                    return false;
                }
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                resultCode = ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
                COMPLETION_LATCH.countDown();
                return false;
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleErrorResult(final ErrorResultException error) {
            System.err.println(error.getMessage());
            resultCode = error.getResult().getResultCode().intValue();
            COMPLETION_LATCH.countDown();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized boolean handleReference(final SearchResultReference reference) {
            try {
                WRITER.writeComment("Search result reference: " + reference.getURIs().toString());
            } catch (final IOException e) {
                System.err.println(e.getMessage());
                resultCode = ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
                COMPLETION_LATCH.countDown();
                return false;
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleResult(final Result result) {
            resultCode = result.getResultCode().intValue();
            COMPLETION_LATCH.countDown();
        }

    }

    private static final CountDownLatch COMPLETION_LATCH = new CountDownLatch(1);
    private static final CountDownLatch CANCEL_LATCH = new CountDownLatch(1);
    private static final LDIFEntryWriter WRITER = new LDIFEntryWriter(System.out);
    private static String userName;
    private static String password;
    private static String baseDN;
    private static SearchScope scope;
    private static String filter;
    private static String[] attributes;
    private static Connection connection = null;
    private static int resultCode = 0;

    static int requestID;
    static int entryCount = 0;

    private static final class CancelResultHandlerImpl implements ResultHandler<ExtendedResult> {

        @Override
        public void handleErrorResult(final ErrorResultException error) {
            System.err.println("Cancel request failed with result code: "
                    + error.getResult().getResultCode().intValue());
            CANCEL_LATCH.countDown();
        }

        @Override
        public void handleResult(final ExtendedResult result) {
            System.err.println("Cancel request succeeded");
            CANCEL_LATCH.countDown();
        }

    }

    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: host, port, username, password,
     *            base DN, scope, filter, and zero or more attributes to be
     *            retrieved.
     */
    public static void main(final String[] args) {
        if (args.length < 7) {
            System.err.println("Usage: host port username password baseDN scope "
                    + "filter [attribute ...]");
            System.exit(1);
        }

        // Parse command line arguments.
        final String hostName = args[0];
        final int port = Integer.parseInt(args[1]);
        userName = args[2];
        password = args[3];
        baseDN = args[4];
        final String scopeString = args[5];
        filter = args[6];
        if (args.length > 7) {
            attributes = Arrays.copyOfRange(args, 7, args.length);
        } else {
            attributes = new String[0];
        }

        if (scopeString.equalsIgnoreCase("base")) {
            scope = SearchScope.BASE_OBJECT;
        } else if (scopeString.equalsIgnoreCase("one")) {
            scope = SearchScope.SINGLE_LEVEL;
        } else if (scopeString.equalsIgnoreCase("sub")) {
            scope = SearchScope.WHOLE_SUBTREE;
        } else if (scopeString.equalsIgnoreCase("subordinates")) {
            scope = SearchScope.SUBORDINATES;
        } else {
            System.err.println("Unknown scope: " + scopeString);
            System.exit(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
            return;
        }

        // Initiate the asynchronous connect, bind, and search.
        final LDAPConnectionFactory factory = new LDAPConnectionFactory(hostName, port);
        factory.getConnectionAsync(new ConnectResultHandlerImpl());

        // Await completion.
        try {
            COMPLETION_LATCH.await();
        } catch (final InterruptedException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_USER_CANCELLED.intValue());
            return;
        }

        try {
            WRITER.flush();
        } catch (final IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
            return;
        }

        // Await completion of the cancel request.
        try {
            CANCEL_LATCH.await();
        } catch (final InterruptedException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_USER_CANCELLED.intValue());
            return;
        }

        if (connection != null) {
            connection.close();
        }

        System.exit(resultCode);
    }

    private SearchAsync() {
        // Not used.
    }
}
