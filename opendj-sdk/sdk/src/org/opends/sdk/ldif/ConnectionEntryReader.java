/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.ldif;



import java.io.InterruptedIOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.opends.sdk.*;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.*;

import com.sun.opends.sdk.util.Validator;



/**
 * A {@code ConnectionEntryReader} is a bridge from
 * {@code AsynchronousConnection}s to {@code EntryReader}s. A connection entry
 * reader allows applications to iterate over search results as they are
 * returned from the server during a search operation.
 * <p>
 * The Search operation is performed synchronously, blocking until a search
 * result entry is received. If a search result indicates that the search
 * operation has failed for some reason then the error result is propagated to
 * the caller using an {@code ErrorResultIOException}. If a search result
 * reference is returned then it is propagated to the caller using a
 * {@code SearchResultReferenceIOException}.
 * <p>
 * The following code illustrates how a {@code ConnectionEntryReader} may be
 * used:
 *
 * <pre>
 * Connection connection = ...;
 * ConnectionEntryReader results = connection.search(
 *     &quot;dc=example,dc=com&quot;,
 *     SearchScope.WHOLE_SUBTREE,
 *     &quot;(objectClass=person)&quot;);
 * SearchResultEntry entry;
 * try
 * {
 *   while ((entry = results.readEntry()) != null)
 *   {
 *     // Process search result entry.
 *   }
 * }
 * catch (Exception e)
 * {
 *   // Handle exceptions
 * }
 * finally
 * {
 *   results.close();
 * }
 * </pre>
 */
public final class ConnectionEntryReader implements EntryReader
{

  /**
   * Result handler that places all responses in a queue.
   */
  private final static class BufferHandler implements SearchResultHandler
  {
    private final BlockingQueue<Response> responses;
    private volatile boolean isInterrupted = false;



    private BufferHandler(final BlockingQueue<Response> responses)
    {
      this.responses = responses;
    }



    @Override
    public boolean handleEntry(final SearchResultEntry entry)
    {
      try
      {
        responses.put(entry);
        return true;
      }
      catch (final InterruptedException e)
      {
        // Prevent the reader from waiting for a result that will never arrive.
        isInterrupted = true;

        Thread.currentThread().interrupt();
        return false;
      }
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      try
      {
        responses.put(error.getResult());
      }
      catch (final InterruptedException e)
      {
        // Prevent the reader from waiting for a result that will never arrive.
        isInterrupted = true;

        Thread.currentThread().interrupt();
      }
    }



    @Override
    public boolean handleReference(final SearchResultReference reference)
    {
      try
      {
        responses.put(reference);
        return true;
      }
      catch (final InterruptedException e)
      {
        // Prevent the reader from waiting for a result that will never arrive.
        isInterrupted = true;

        Thread.currentThread().interrupt();
        return false;
      }
    }



    @Override
    public void handleResult(final Result result)
    {
      try
      {
        responses.put(result);
      }
      catch (final InterruptedException e)
      {
        // Prevent the reader from waiting for a result that will never arrive.
        isInterrupted = true;

        Thread.currentThread().interrupt();
      }
    }
  }



  private final BufferHandler buffer;
  private final FutureResult<Result> future;



  /**
   * Creates a new connection entry reader whose destination is the provided
   * connection using an unbounded {@code LinkedBlockingQueue}.
   *
   * @param connection
   *          The connection to use.
   * @param searchRequest
   *          The search request to retrieve entries with.
   * @throws NullPointerException
   *           If {@code connection} was {@code null}.
   */
  public ConnectionEntryReader(final AsynchronousConnection connection,
      final SearchRequest searchRequest) throws NullPointerException
  {
    this(connection, searchRequest, new LinkedBlockingQueue<Response>());
  }



  /**
   * Creates a new connection entry reader whose destination is the provided
   * connection.
   *
   * @param connection
   *          The connection to use.
   * @param searchRequest
   *          The search request to retrieve entries with.
   * @param entries
   *          The {@code BlockingQueue} implementation to use when queuing the
   *          returned entries.
   * @throws NullPointerException
   *           If {@code connection} was {@code null}.
   */
  public ConnectionEntryReader(final AsynchronousConnection connection,
      final SearchRequest searchRequest, final BlockingQueue<Response> entries)
      throws NullPointerException
  {
    Validator.ensureNotNull(connection);
    buffer = new BufferHandler(entries);
    future = connection.search(searchRequest, buffer);
  }



  /**
   * Closes this connection entry reader, cancelling the search request if it is
   * still active.
   */
  @Override
  public void close()
  {
    // Cancel the search if it is still running.
    future.cancel(true);
  }



  /**
   * Returns the next search result entry contained in the search results,
   * waiting if necessary until one becomes available.
   *
   * @return The next search result entry, or {@code null} if there are no more
   *         entries in the search results.
   * @throws SearchResultReferenceIOException
   *           If the next search response was a search result reference. This
   *           connection entry reader may still contain remaining search
   *           results and references which can be retrieved using additional
   *           calls to this method.
   * @throws ErrorResultIOException
   *           If the result code indicates that the search operation failed for
   *           some reason.
   * @throws InterruptedIOException
   *           If the current thread was interrupted while waiting.
   */
  @Override
  public SearchResultEntry readEntry() throws SearchResultReferenceIOException,
      ErrorResultIOException, InterruptedIOException
  {
    Response r;
    try
    {
      while ((r = buffer.responses.poll(50, TimeUnit.MILLISECONDS)) == null)
      {
        if (buffer.isInterrupted)
        {
          // The worker thread processing the result was interrupted so no
          // result will ever arrive. We don't want to hang this thread forever
          // while we wait, so terminate now.
          r = Responses.newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR);
          break;
        }
      }
    }
    catch (final InterruptedException e)
    {
      throw new InterruptedIOException(e.getMessage());
    }

    if (r instanceof SearchResultEntry)
    {
      return (SearchResultEntry) r;
    }
    else if (r instanceof SearchResultReference)
    {
      throw new SearchResultReferenceIOException((SearchResultReference) r);
    }
    else if (r instanceof Result)
    {
      final Result result = (Result) r;
      if (result.isSuccess())
      {
        return null;
      }
      else
      {
        throw new ErrorResultIOException(ErrorResultException.wrap(result));
      }
    }
    else
    {
      throw new RuntimeException("Unexpected response type: "
          + r.getClass().toString());
    }
  }
}
