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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import static org.opends.sdk.CoreMessages.ERR_NO_SEARCH_RESULT_ENTRIES;
import static org.opends.sdk.CoreMessages.ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES;
import static org.opends.sdk.CoreMessages.ERR_UNEXPECTED_SEARCH_RESULT_REFERENCES;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.schema.Schema;



/**
 * This class provides a skeletal implementation of the
 * {@code AsynchronousConnection} interface, to minimize the effort required to
 * implement this interface.
 */
public abstract class AbstractAsynchronousConnection implements
    AsynchronousConnection
{

  private static final class SingleEntryFuture implements
      FutureResult<SearchResultEntry>, SearchResultHandler
  {
    private final ResultHandler<? super SearchResultEntry> handler;

    private volatile SearchResultEntry firstEntry = null;

    private volatile SearchResultReference firstReference = null;

    private volatile int entryCount = 0;

    private volatile FutureResult<Result> future = null;



    private SingleEntryFuture(
        final ResultHandler<? super SearchResultEntry> handler)
    {
      this.handler = handler;
    }



    public boolean cancel(final boolean mayInterruptIfRunning)
    {
      return future.cancel(mayInterruptIfRunning);
    }



    public SearchResultEntry get() throws ErrorResultException,
        InterruptedException
    {
      future.get();
      return get0();
    }



    public SearchResultEntry get(final long timeout, final TimeUnit unit)
        throws ErrorResultException, TimeoutException, InterruptedException
    {
      future.get(timeout, unit);
      return get0();
    }



    public int getRequestID()
    {
      return future.getRequestID();
    }



    public boolean handleEntry(final SearchResultEntry entry)
    {
      if (firstEntry == null)
      {
        firstEntry = entry;
      }
      entryCount++;
      return true;
    }



    public void handleErrorResult(final ErrorResultException error)
    {
      if (handler != null)
      {
        handler.handleErrorResult(error);
      }
    }



    public boolean handleReference(final SearchResultReference reference)
    {
      if (firstReference == null)
      {
        firstReference = reference;
      }
      return true;
    }



    public void handleResult(final Result result)
    {
      if (handler != null)
      {
        try
        {
          handler.handleResult(get0());
        }
        catch (final ErrorResultException e)
        {
          handler.handleErrorResult(e);
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



    private SearchResultEntry get0() throws ErrorResultException
    {
      if (entryCount == 0)
      {
        // Did not find any entries.
        final Result result = Responses.newResult(
            ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED).setDiagnosticMessage(
            ERR_NO_SEARCH_RESULT_ENTRIES.get().toString());
        throw ErrorResultException.wrap(result);
      }
      else if (entryCount > 1)
      {
        // Got more entries than expected.
        final Result result = Responses
            .newResult(ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED)
            .setDiagnosticMessage(
                ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES.get(entryCount).toString());
        throw ErrorResultException.wrap(result);
      }
      else if (firstReference != null)
      {
        // Got an unexpected search result reference.
        final Result result = Responses.newResult(
            ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED)
            .setDiagnosticMessage(
                ERR_UNEXPECTED_SEARCH_RESULT_REFERENCES.get(
                    firstReference.getURIs().iterator().next()).toString());
        throw ErrorResultException.wrap(result);
      }
      else
      {
        return firstEntry;
      }
    }



    private void setResultFuture(final FutureResult<Result> future)
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
  public FutureResult<Result> add(final AddRequest request,
      final ResultHandler<? super Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return add(request, handler, null);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<BindResult> bind(final BindRequest request,
      final ResultHandler<? super BindResult> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return bind(request, handler, null);
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
  public FutureResult<CompareResult> compare(final CompareRequest request,
      final ResultHandler<? super CompareResult> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return compare(request, handler, null);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> delete(final DeleteRequest request,
      final ResultHandler<? super Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return delete(request, handler, null);
  }



  /**
   * {@inheritDoc}
   */
  public <R extends ExtendedResult> FutureResult<R> extendedRequest(
      final ExtendedRequest<R> request, final ResultHandler<? super R> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return extendedRequest(request, handler, null);
  }



  /**
   * {@inheritDoc}
   */
  public Connection getSynchronousConnection()
  {
    return new SynchronousConnection(this);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> modify(final ModifyRequest request,
      final ResultHandler<? super Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return modify(request, handler, null);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> modifyDN(final ModifyDNRequest request,
      final ResultHandler<? super Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return modifyDN(request, handler, null);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<SearchResultEntry> readEntry(final DN name,
      final Collection<String> attributeDescriptions,
      final ResultHandler<? super SearchResultEntry> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final SearchRequest request = Requests.newSearchRequest(name,
        SearchScope.BASE_OBJECT, Filter.getObjectClassPresentFilter());
    request.getAttributes().addAll(attributeDescriptions);
    return searchSingleEntry(request, handler);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<RootDSE> readRootDSE(
      final ResultHandler<? super RootDSE> handler)
      throws UnsupportedOperationException, IllegalStateException
  {
    return RootDSE.readRootDSE(this, handler);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Schema> readSchema(final DN name,
      final ResultHandler<? super Schema> handler)
      throws UnsupportedOperationException, IllegalStateException
  {
    return Schema.readSchema(this, name, handler);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Schema> readSchemaForEntry(final DN name,
      final ResultHandler<? super Schema> handler)
      throws UnsupportedOperationException, IllegalStateException
  {
    return Schema.readSchema(this, name, handler);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> search(final SearchRequest request,
      final SearchResultHandler handler) throws UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return search(request, handler, null);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<SearchResultEntry> searchSingleEntry(
      final SearchRequest request,
      final ResultHandler<? super SearchResultEntry> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final SingleEntryFuture innerFuture = new SingleEntryFuture(handler);
    final FutureResult<Result> future = search(request, innerFuture);
    innerFuture.setResultFuture(future);
    return innerFuture;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Sub-classes should provide an implementation which returns an appropriate
   * description of the connection which may be used for debugging purposes.
   */
  public abstract String toString();

}
