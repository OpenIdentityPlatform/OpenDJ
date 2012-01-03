/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.ldap;



import static org.forgerock.opendj.ldap.CoreMessages.ERR_NO_SEARCH_RESULT_ENTRIES;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_UNEXPECTED_SEARCH_RESULT_REFERENCES;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.opendj.ldap.requests.*;
import org.forgerock.opendj.ldap.responses.*;
import org.forgerock.opendj.ldif.ConnectionEntryReader;

import com.forgerock.opendj.util.Validator;



/**
 * This class provides a skeletal implementation of the {@code Connection}
 * interface, to minimize the effort required to implement this interface.
 */
public abstract class AbstractConnection implements Connection
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



    @Override
    public boolean cancel(final boolean mayInterruptIfRunning)
    {
      return future.cancel(mayInterruptIfRunning);
    }



    @Override
    public SearchResultEntry get() throws ErrorResultException,
        InterruptedException
    {
      future.get();
      return get0();
    }



    @Override
    public SearchResultEntry get(final long timeout, final TimeUnit unit)
        throws ErrorResultException, TimeoutException, InterruptedException
    {
      future.get(timeout, unit);
      return get0();
    }



    @Override
    public int getRequestID()
    {
      return future.getRequestID();
    }



    @Override
    public boolean handleEntry(final SearchResultEntry entry)
    {
      if (firstEntry == null)
      {
        firstEntry = entry;
      }
      entryCount++;
      return true;
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      if (handler != null)
      {
        handler.handleErrorResult(error);
      }
    }



    @Override
    public boolean handleReference(final SearchResultReference reference)
    {
      if (firstReference == null)
      {
        firstReference = reference;
      }
      return true;
    }



    @Override
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



    @Override
    public boolean isCancelled()
    {
      return future.isCancelled();
    }



    @Override
    public boolean isDone()
    {
      return future.isDone();
    }



    private SearchResultEntry get0() throws ErrorResultException
    {
      if (entryCount == 0)
      {
        // Did not find any entries.
        throw newErrorResult(ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED,
            ERR_NO_SEARCH_RESULT_ENTRIES.get().toString());
      }
      else if (entryCount > 1)
      {
        // Got more entries than expected.
        throw newErrorResult(
            ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED,
            ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES.get(entryCount).toString());
      }
      else if (firstReference != null)
      {
        // Got an unexpected search result reference.
        throw newErrorResult(
            ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED,
            ERR_UNEXPECTED_SEARCH_RESULT_REFERENCES.get(
                firstReference.getURIs().iterator().next()).toString());
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



  private static final class SingleEntryHandler implements SearchResultHandler
  {
    private volatile SearchResultEntry firstEntry = null;

    private volatile SearchResultReference firstReference = null;

    private volatile int entryCount = 0;



    @Override
    public boolean handleEntry(final SearchResultEntry entry)
    {
      if (firstEntry == null)
      {
        firstEntry = entry;
      }
      entryCount++;
      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      // Ignore.
    }



    @Override
    public boolean handleReference(final SearchResultReference reference)
    {
      if (firstReference == null)
      {
        firstReference = reference;
      }
      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleResult(final Result result)
    {
      // Ignore.
    }

  }



  /**
   * Creates a new abstract connection.
   */
  protected AbstractConnection()
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Result add(final Entry entry) throws ErrorResultException,
      InterruptedException
  {
    return add(Requests.newAddRequest(entry));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Result add(final String... ldifLines) throws ErrorResultException,
      InterruptedException
  {
    return add(Requests.newAddRequest(ldifLines));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public BindResult bind(final String name, final char[] password)
      throws ErrorResultException, InterruptedException
  {
    return bind(Requests.newSimpleBindRequest(name, password));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void close()
  {
    close(Requests.newUnbindRequest(), null);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public CompareResult compare(final String name,
      final String attributeDescription, final String assertionValue)
      throws ErrorResultException, InterruptedException
  {
    return compare(Requests.newCompareRequest(name, attributeDescription,
        assertionValue));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Result delete(final String name) throws ErrorResultException,
      InterruptedException
  {
    return delete(Requests.newDeleteRequest(name));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R extends ExtendedResult> R extendedRequest(
      final ExtendedRequest<R> request) throws ErrorResultException,
      InterruptedException
  {
    return extendedRequest(request, null);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public GenericExtendedResult extendedRequest(final String requestName,
      final ByteString requestValue) throws ErrorResultException,
      InterruptedException
  {
    return extendedRequest(Requests.newGenericExtendedRequest(requestName,
        requestValue));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Result modify(final String... ldifLines) throws ErrorResultException,
      InterruptedException
  {
    return modify(Requests.newModifyRequest(ldifLines));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Result modifyDN(final String name, final String newRDN)
      throws ErrorResultException, InterruptedException
  {
    return modifyDN(Requests.newModifyDNRequest(name, newRDN));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResultEntry readEntry(final DN baseObject,
      final String... attributeDescriptions) throws ErrorResultException,
      InterruptedException
  {
    final SearchRequest request = Requests.newSearchRequest(baseObject,
        SearchScope.BASE_OBJECT, Filter.getObjectClassPresentFilter(),
        attributeDescriptions);
    return searchSingleEntry(request);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResultEntry readEntry(final String baseObject,
      final String... attributeDescriptions) throws ErrorResultException,
      InterruptedException
  {
    return readEntry(DN.valueOf(baseObject));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<SearchResultEntry> readEntryAsync(final DN name,
      final Collection<String> attributeDescriptions,
      final ResultHandler<? super SearchResultEntry> handler)
  {
    final SearchRequest request = Requests.newSearchRequest(name,
        SearchScope.BASE_OBJECT, Filter.getObjectClassPresentFilter());
    if (attributeDescriptions != null)
    {
      request.getAttributes().addAll(attributeDescriptions);
    }
    return searchSingleEntryAsync(request, handler);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConnectionEntryReader search(final SearchRequest request)
  {
    return new ConnectionEntryReader(this, request);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Result search(final SearchRequest request,
      final Collection<? super SearchResultEntry> entries)
      throws ErrorResultException, InterruptedException
  {
    return search(request, entries, null);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Result search(final SearchRequest request,
      final Collection<? super SearchResultEntry> entries,
      final Collection<? super SearchResultReference> references)
      throws ErrorResultException, InterruptedException
  {
    Validator.ensureNotNull(request, entries);

    // FIXME: does this need to be thread safe?
    final SearchResultHandler handler = new SearchResultHandler()
    {

      @Override
      public boolean handleEntry(final SearchResultEntry entry)
      {
        entries.add(entry);
        return true;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public void handleErrorResult(final ErrorResultException error)
      {
        // Ignore.
      }



      @Override
      public boolean handleReference(final SearchResultReference reference)
      {
        if (references != null)
        {
          references.add(reference);
        }
        return true;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public void handleResult(final Result result)
      {
        // Ignore.
      }
    };

    return search(request, handler);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConnectionEntryReader search(final String baseObject,
      final SearchScope scope, final String filter,
      final String... attributeDescriptions)
  {
    final SearchRequest request = Requests.newSearchRequest(baseObject, scope,
        filter, attributeDescriptions);
    return search(request);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResultEntry searchSingleEntry(final SearchRequest request)
      throws ErrorResultException, InterruptedException
  {
    final SingleEntryHandler handler = new SingleEntryHandler();
    search(request, handler);

    if (handler.entryCount == 0)
    {
      // Did not find any entries.
      throw newErrorResult(ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED,
          ERR_NO_SEARCH_RESULT_ENTRIES.get().toString());
    }
    else if (handler.entryCount > 1)
    {
      // Got more entries than expected.
      throw newErrorResult(ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED,
          ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES.get(handler.entryCount)
              .toString());
    }
    else if (handler.firstReference != null)
    {
      // Got an unexpected search result reference.
      throw newErrorResult(
          ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED,
          ERR_UNEXPECTED_SEARCH_RESULT_REFERENCES.get(
              handler.firstReference.getURIs().iterator().next()).toString());
    }
    else
    {
      return handler.firstEntry;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResultEntry searchSingleEntry(final String baseObject,
      final SearchScope scope, final String filter,
      final String... attributeDescriptions) throws ErrorResultException,
      InterruptedException
  {
    final SearchRequest request = Requests.newSearchRequest(baseObject, scope,
        filter, attributeDescriptions);
    return searchSingleEntry(request);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<SearchResultEntry> searchSingleEntryAsync(
      final SearchRequest request,
      final ResultHandler<? super SearchResultEntry> handler)
  {
    final SingleEntryFuture innerFuture = new SingleEntryFuture(handler);
    final FutureResult<Result> future = searchAsync(request, null, innerFuture);
    innerFuture.setResultFuture(future);
    return innerFuture;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Sub-classes should provide an implementation which returns an appropriate
   * description of the connection which may be used for debugging purposes.
   */
  @Override
  public abstract String toString();

}
