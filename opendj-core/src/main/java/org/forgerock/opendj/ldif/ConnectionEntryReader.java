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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.util.Reject;

import static org.forgerock.opendj.ldap.LdapException.*;

/**
 * A {@code ConnectionEntryReader} is a bridge from {@code Connection}s to
 * {@code EntryReader}s. A connection entry reader allows applications to
 * iterate over search results as they are returned from the server during a
 * search operation.
 * <p>
 * The Search operation is performed synchronously, blocking until a search
 * result entry is received. If a search result indicates that the search
 * operation has failed for some reason then the error result is propagated to
 * the caller using an {@code LdapException}. If a search result
 * reference is returned then it is propagated to the caller using a
 * {@code SearchResultReferenceIOException}.
 * <p>
 * The following code illustrates how a {@code ConnectionEntryReader} may be
 * used:
 *
 * <pre>
 * Connection connection = ...;
 * ConnectionEntryReader reader = connection.search(&quot;dc=example,dc=com&quot;,
 *     SearchScope.WHOLE_SUBTREE, &quot;(objectClass=person)&quot;);
 * try
 * {
 *   while (reader.hasNext())
 *   {
 *     if (reader.isEntry())
 *     {
 *       SearchResultEntry entry = reader.readEntry();
 *
 *       // Handle entry...
 *     }
 *     else
 *     {
 *       SearchResultReference ref = reader.readReference();
 *
 *       // Handle continuation reference...
 *     }
 *   }
 *
 *   Result result = reader.readResult();
 *   // Handle controls included with the search result...
 * }
 * catch (IOException e)
 * {
 *   // Handle exceptions...
 * }
 * finally
 * {
 *   reader.close();
 * }
 * </pre>
 *
 * <b>NOTE:</b> although this class is non-final, sub-classing is not supported
 * except when creating mock objects for unit tests. This class has been
 * selected specifically because it is the only aspect of the {@code Connection}
 * interface which is not mockable.
 */
public class ConnectionEntryReader implements EntryReader {
    /*
     * See OPENDJ-1124 for more discussion about why this class is non-final.
     */

    /**
     * Result handler that places all responses in a queue.
     */
    private static final class BufferHandler implements SearchResultHandler, LdapResultHandler<Result> {
        private final BlockingQueue<Response> responses;
        private volatile boolean isInterrupted;

        private BufferHandler(final BlockingQueue<Response> responses) {
            this.responses = responses;
        }

        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            try {
                responses.put(entry);
                return true;
            } catch (final InterruptedException e) {
                // Prevent the reader from waiting for a result that will never
                // arrive.
                isInterrupted = true;
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public void handleException(final LdapException error) {
            try {
                responses.put(error.getResult());
            } catch (final InterruptedException e) {
                // Prevent the reader from waiting for a result that will never
                // arrive.
                isInterrupted = true;
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public boolean handleReference(final SearchResultReference reference) {
            try {
                responses.put(reference);
                return true;
            } catch (final InterruptedException e) {
                // Prevent the reader from waiting for a result that will never
                // arrive.
                isInterrupted = true;
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public void handleResult(final Result result) {
            try {
                responses.put(result);
            } catch (final InterruptedException e) {
                // Prevent the reader from waiting for a result that will never
                // arrive.
                isInterrupted = true;
                Thread.currentThread().interrupt();
            }
        }
    }

    private final BufferHandler buffer;
    private final LdapPromise<Result> promise;
    private Response nextResponse;

    /**
     * Creates a new connection entry reader whose destination is the provided
     * connection using an unbounded {@code LinkedBlockingQueue}.
     *
     * @param connection
     *            The connection to use.
     * @param searchRequest
     *            The search request to retrieve entries with.
     * @throws NullPointerException
     *             If {@code connection} was {@code null}.
     */
    public ConnectionEntryReader(final Connection connection, final SearchRequest searchRequest) {
        this(connection, searchRequest, new LinkedBlockingQueue<Response>());
    }

    /**
     * Creates a new connection entry reader whose destination is the provided
     * connection.
     *
     * @param connection
     *            The connection to use.
     * @param searchRequest
     *            The search request to retrieve entries with.
     * @param entries
     *            The {@code BlockingQueue} implementation to use when queuing
     *            the returned entries.
     * @throws NullPointerException
     *             If {@code connection} was {@code null}.
     */
    public ConnectionEntryReader(final Connection connection, final SearchRequest searchRequest,
        final BlockingQueue<Response> entries) {
        Reject.ifNull(connection);
        buffer = new BufferHandler(entries);
        promise = connection.searchAsync(searchRequest, buffer).thenOnResult(buffer).thenOnException(buffer);
    }

    /**
     * Closes this connection entry reader, canceling the search request if it
     * is still active.
     */
    @Override
    public void close() {
        // Cancel the search if it is still running.
        promise.cancel(true);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasNext() throws LdapException {
        // Poll for the next response if needed.
        final Response r = getNextResponse();
        if (!(r instanceof Result)) {
            // Entry or reference.
            return true;
        }

        // Final result.
        final Result result = (Result) r;
        if (result.isSuccess()) {
            return false;
        }

        throw newLdapException(result);
    }

    /**
     * Waits for the next search result entry or reference to become available
     * and returns {@code true} if it is an entry, or {@code false} if it is a
     * reference.
     *
     * @return {@code true} if the next search result is an entry, or
     *         {@code false} if it is a reference.
     * @throws LdapException
     *             If there are no more search result entries or references and
     *             the search result code indicates that the search operation
     *             failed for some reason.
     * @throws NoSuchElementException
     *             If there are no more search result entries or references and
     *             the search result code indicates that the search operation
     *             succeeded.
     */
    public boolean isEntry() throws LdapException {
        // Throws LdapException if search returned error.
        if (!hasNext()) {
            // Search has completed successfully.
            throw new NoSuchElementException();
        }

        // Entry or reference?
        final Response r = nextResponse;
        if (r instanceof SearchResultEntry) {
            return true;
        } else if (r instanceof SearchResultReference) {
            return false;
        } else {
            throw new RuntimeException("Unexpected response type: " + r.getClass());
        }
    }

    /**
     * Waits for the next search result entry or reference to become available
     * and returns {@code true} if it is a reference, or {@code false} if it is
     * an entry.
     *
     * @return {@code true} if the next search result is a reference, or
     *         {@code false} if it is an entry.
     * @throws LdapException
     *             If there are no more search result entries or references and
     *             the search result code indicates that the search operation
     *             failed for some reason.
     * @throws NoSuchElementException
     *             If there are no more search result entries or references and
     *             the search result code indicates that the search operation
     *             succeeded.
     */
    public boolean isReference() throws LdapException {
        return !isEntry();
    }

    /**
     * Waits for the next search result entry or reference to become available
     * and, if it is an entry, returns it as a {@code SearchResultEntry}. If the
     * next search response is a reference then this method will throw a
     * {@code SearchResultReferenceIOException}.
     *
     * @return The next search result entry.
     * @throws SearchResultReferenceIOException
     *             If the next search response was a search result reference.
     *             This connection entry reader may still contain remaining
     *             search results and references which can be retrieved using
     *             additional calls to this method.
     * @throws LdapException
     *             If there are no more search result entries or references and
     *             the search result code indicates that the search operation
     *             failed for some reason.
     * @throws NoSuchElementException
     *             If there are no more search result entries or references and
     *             the search result code indicates that the search operation
     *             succeeded.
     */
    @Override
    public SearchResultEntry readEntry() throws SearchResultReferenceIOException, LdapException {
        if (isEntry()) {
            final SearchResultEntry entry = (SearchResultEntry) nextResponse;
            nextResponse = null;
            return entry;
        } else {
            final SearchResultReference reference = (SearchResultReference) nextResponse;
            nextResponse = null;
            throw new SearchResultReferenceIOException(reference);
        }
    }

    /**
     * Waits for the next search result entry or reference to become available
     * and, if it is a reference, returns it as a {@code SearchResultReference}.
     * If the next search response is an entry then this method will return
     * {@code null}.
     *
     * @return The next search result reference, or {@code null} if the next
     *         response was a search result entry.
     * @throws LdapException
     *             If there are no more search result entries or references and
     *             the search result code indicates that the search operation
     *             failed for some reason.
     * @throws NoSuchElementException
     *             If there are no more search result entries or references and
     *             the search result code indicates that the search operation
     *             succeeded.
     */
    public SearchResultReference readReference() throws LdapException {
        if (isReference()) {
            final SearchResultReference reference = (SearchResultReference) nextResponse;
            nextResponse = null;
            return reference;
        } else {
            return null;
        }
    }

    /**
     * Waits for the next search response to become available and returns it if
     * it is a search result indicating that the search completed successfully.
     * If the search result indicates that the search failed then an
     * {@link LdapException} is thrown. Otherwise, if the search
     * response represents an entry or reference then an
     * {@code IllegalStateException} is thrown.
     * <p>
     * This method should only be called if {@link #hasNext()} has, or will,
     * return {@code false}.
     * <p>
     * It is not necessary to call this method once all search result entries
     * have been processed, but it may be useful to do so in order to inspect
     * any controls which were included with the result. For example, this
     * method may be called in order to obtain the next paged results cookie
     * once the current page of results has been processed.
     *
     * @return The search result indicating success.
     * @throws LdapException
     *             If the search result indicates that the search operation
     *             failed for some reason.
     * @throws IllegalStateException
     *             If there are remaining search result entries or references to
     *             be processed. In other words, if {@link #hasNext()} would
     *             return {@code true}.
     */
    public Result readResult() throws LdapException {
        if (hasNext()) {
            throw new IllegalStateException();
        } else {
            return (Result) nextResponse;
        }
    }

    private Response getNextResponse() throws LdapException {
        while (nextResponse == null) {
            try {
                nextResponse = buffer.responses.poll(50, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                throw newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
            }

            if (nextResponse == null && buffer.isInterrupted) {
                // The worker thread processing the result was interrupted so no
                // result will ever arrive. We don't want to hang this thread
                // forever while we wait, so terminate now.
                nextResponse = Responses.newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR);
                break;
            }
        }
        return nextResponse;
    }
}
