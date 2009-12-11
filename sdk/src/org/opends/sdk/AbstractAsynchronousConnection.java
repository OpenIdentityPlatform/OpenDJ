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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import static com.sun.opends.sdk.messages.Messages.*;

import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opends.sdk.requests.Requests;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.responses.SearchResultReference;
import org.opends.sdk.schema.Schema;



/**
 * This class provides a skeletal implementation of the {@code
 * AsynchronousConnection} interface, to minimize the effort required to
 * implement this interface.
 */
public abstract class AbstractAsynchronousConnection implements
    AsynchronousConnection
{

  private static final class SingleEntryFuture<P> implements
      ResultFuture<SearchResultEntry>, ResultHandler<Result, P>,
      SearchResultHandler<P>
  {
    private final ResultHandler<? super SearchResultEntry, P> handler;

    private volatile SearchResultEntry firstEntry = null;

    private volatile SearchResultReference firstReference = null;

    private volatile int entryCount = 0;

    private volatile ResultFuture<Result> future = null;



    private SingleEntryFuture(
        ResultHandler<? super SearchResultEntry, P> handler)
    {
      this.handler = handler;
    }



    public boolean cancel(boolean mayInterruptIfRunning)
    {
      return future.cancel(mayInterruptIfRunning);
    }



    public SearchResultEntry get() throws ErrorResultException,
        CancellationException, InterruptedException
    {
      future.get();
      return get0();
    }



    public SearchResultEntry get(long timeout, TimeUnit unit)
        throws ErrorResultException, TimeoutException,
        CancellationException, InterruptedException
    {
      future.get(timeout, unit);
      return get0();
    }



    private SearchResultEntry get0() throws ErrorResultException
    {
      if (entryCount == 0)
      {
        // Did not find any entries.
        Result result = Responses.newResult(
            ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED)
            .setDiagnosticMessage(
                ERR_NO_SEARCH_RESULT_ENTRIES.get().toString());
        throw ErrorResultException.wrap(result);
      }
      else if (entryCount > 1)
      {
        // Got more entries than expected.
        Result result = Responses.newResult(
            ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED)
            .setDiagnosticMessage(
                ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES.get(entryCount)
                    .toString());
        throw ErrorResultException.wrap(result);
      }
      else if (firstReference != null)
      {
        // Got an unexpected search result reference.
        Result result = Responses.newResult(
            ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED)
            .setDiagnosticMessage(
                ERR_UNEXPECTED_SEARCH_RESULT_REFERENCES.get(
                    firstReference.getURIs().iterator().next())
                    .toString());
        throw ErrorResultException.wrap(result);
      }
      else
      {
        return firstEntry;
      }
    }



    public int getMessageID()
    {
      return future.getMessageID();
    }



    public void handleEntry(P p, SearchResultEntry entry)
    {
      if (firstEntry == null)
      {
        firstEntry = entry;
      }
      entryCount++;
    }



    public void handleErrorResult(P p, ErrorResultException error)
    {
      if (handler != null)
      {
        handler.handleErrorResult(p, error);
      }
    }



    public void handleReference(P p, SearchResultReference reference)
    {
      if (firstReference == null)
      {
        firstReference = reference;
      }
    }



    public void handleResult(P p, Result result)
    {
      if (handler != null)
      {
        try
        {
          handler.handleResult(p, get0());
        }
        catch (ErrorResultException e)
        {
          handler.handleErrorResult(p, e);
        }
      }
    }



    public boolean isCancelled()
    {
      return future.isCancelled();
    }



    public boolean isDone()
    {
      return future.isDone();
    }



    private void setResultFuture(ResultFuture<Result> future)
    {
      this.future = future;
    }
  }



  /**
   * Creates a new abstract connection.
   */
  protected AbstractAsynchronousConnection()
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public void close()
  {
    close(Requests.newUnbindRequest(), null);
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<SearchResultEntry> readEntry(DN name,
      Collection<String> attributeDescriptions,
      ResultHandler<? super SearchResultEntry, P> handler, P p)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    SearchRequest request = Requests.newSearchRequest(name,
        SearchScope.BASE_OBJECT, Filter.getObjectClassPresentFilter())
        .addAttribute(attributeDescriptions);
    return searchSingleEntry(request, handler, p);
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<RootDSE> readRootDSE(
      ResultHandler<RootDSE, P> handler, P p)
      throws UnsupportedOperationException, IllegalStateException
  {
    return RootDSE.readRootDSE(this, handler, p);
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<Schema> readSchema(DN name,
      ResultHandler<Schema, P> handler, P p)
      throws UnsupportedOperationException, IllegalStateException
  {
    return Schema.readSchema(this, name, handler, p);
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<Schema> readSchemaForEntry(DN name,
      ResultHandler<Schema, P> handler, P p)
      throws UnsupportedOperationException, IllegalStateException
  {
    return Schema.readSchema(this, name, handler, p);
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<SearchResultEntry> searchSingleEntry(
      SearchRequest request,
      ResultHandler<? super SearchResultEntry, P> handler, P p)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final SingleEntryFuture<P> innerFuture = new SingleEntryFuture<P>(
        handler);
    final ResultFuture<Result> future = search(request, innerFuture,
        innerFuture, p);
    innerFuture.setResultFuture(future);
    return innerFuture;
  }

}
