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



import static com.sun.opends.sdk.messages.Messages.ERR_NO_SEARCH_RESULT_ENTRIES;
import static com.sun.opends.sdk.messages.Messages.ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES;
import static com.sun.opends.sdk.messages.Messages.ERR_UNEXPECTED_SEARCH_RESULT_REFERENCES;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.opends.sdk.ldif.ConnectionEntryReader;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.*;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Validator;



/**
 * This class provides a skeletal implementation of the {@code Connection}
 * interface, to minimize the effort required to implement this interface.
 */
public abstract class AbstractConnection implements Connection
{

  private static final class SingleEntryHandler implements SearchResultHandler
  {
    private volatile SearchResultEntry firstEntry = null;

    private volatile SearchResultReference firstReference = null;

    private volatile int entryCount = 0;



    public boolean handleEntry(final SearchResultEntry entry)
    {
      if (firstEntry == null)
      {
        firstEntry = entry;
      }
      entryCount++;
      return true;
    }



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
    public void handleErrorResult(ErrorResultException error)
    {
      // Ignore.
    }



    /**
     * {@inheritDoc}
     */
    public void handleResult(Result result)
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
  public Result add(final Entry entry) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return add(Requests.newAddRequest(entry));
  }



  /**
   * {@inheritDoc}
   */
  public Result add(final String... ldifLines) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      LocalizedIllegalArgumentException, IllegalStateException,
      NullPointerException
  {
    return add(Requests.newAddRequest(ldifLines));
  }



  /**
   * {@inheritDoc}
   */
  public BindResult bind(final String name, final String password)
      throws ErrorResultException, InterruptedException,
      LocalizedIllegalArgumentException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return bind(Requests.newSimpleBindRequest(name, password));
  }



  /**
   * {@inheritDoc}
   */
  public CompareResult compare(final String name,
      final String attributeDescription, final String assertionValue)
      throws ErrorResultException, InterruptedException,
      LocalizedIllegalArgumentException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return compare(Requests.newCompareRequest(name, attributeDescription,
        assertionValue));
  }



  /**
   * {@inheritDoc}
   */
  public Result delete(final String name) throws ErrorResultException,
      InterruptedException, LocalizedIllegalArgumentException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return delete(Requests.newDeleteRequest(name));
  }



  /**
   * {@inheritDoc}
   */
  public GenericExtendedResult extendedRequest(final String requestName,
      final ByteString requestValue) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return extendedRequest(Requests.newGenericExtendedRequest(requestName,
        requestValue));
  }



  /**
   * {@inheritDoc}
   */
  public Result modify(final String... ldifLines) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      LocalizedIllegalArgumentException, IllegalStateException,
      NullPointerException
  {
    return modify(Requests.newModifyRequest(ldifLines));
  }



  /**
   * {@inheritDoc}
   */
  public Result modifyDN(final String name, final String newRDN)
      throws ErrorResultException, InterruptedException,
      LocalizedIllegalArgumentException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return modifyDN(Requests.newModifyDNRequest(name, newRDN));
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry readEntry(final DN baseObject,
      final String... attributeDescriptions) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    final SearchRequest request = Requests.newSearchRequest(baseObject,
        SearchScope.BASE_OBJECT, Filter.getObjectClassPresentFilter(),
        attributeDescriptions);
    return searchSingleEntry(request);
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry readEntry(final String baseObject,
      final String... attributeDescriptions) throws ErrorResultException,
      InterruptedException, LocalizedIllegalArgumentException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return readEntry(DN.valueOf(baseObject));
  }



  /**
   * {@inheritDoc}
   */
  public RootDSE readRootDSE() throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException
  {
    return RootDSE.readRootDSE(this);
  }



  /**
   * {@inheritDoc}
   */
  public Schema readSchema(final DN name) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException
  {
    return Schema.readSchema(this, name);
  }



  /**
   * {@inheritDoc}
   */
  public Schema readSchema(final String name) throws ErrorResultException,
      InterruptedException, LocalizedIllegalArgumentException,
      UnsupportedOperationException, IllegalStateException
  {
    return readSchema(DN.valueOf(name));
  }



  /**
   * {@inheritDoc}
   */
  public Schema readSchemaForEntry(final DN name) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException
  {
    return Schema.readSchemaForEntry(this, name);
  }



  /**
   * {@inheritDoc}
   */
  public Schema readSchemaForEntry(final String name)
      throws ErrorResultException, InterruptedException,
      LocalizedIllegalArgumentException, UnsupportedOperationException,
      IllegalStateException
  {
    return readSchemaForEntry(DN.valueOf(name));
  }



  /**
   * {@inheritDoc}
   */
  public Schema readSchemaForRootDSE() throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException
  {
    return readSchemaForEntry(DN.rootDN());
  }



  /**
   * {@inheritDoc}
   */
  public Result search(final SearchRequest request,
      final Collection<? super SearchResultEntry> entries)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return search(request, entries, null);
  }



  /**
   * {@inheritDoc}
   */
  public Result search(final SearchRequest request,
      final Collection<? super SearchResultEntry> entries,
      final Collection<? super SearchResultReference> references)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    Validator.ensureNotNull(request, entries);

    // FIXME: does this need to be thread safe?
    final SearchResultHandler handler = new SearchResultHandler()
    {

      public boolean handleEntry(final SearchResultEntry entry)
      {
        entries.add(entry);
        return true;
      }



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
      public void handleErrorResult(ErrorResultException error)
      {
        // Ignore.
      }



      /**
       * {@inheritDoc}
       */
      public void handleResult(Result result)
      {
        // Ignore.
      }
    };

    return search(request, handler);
  }



  /**
   * {@inheritDoc}
   */
  public ConnectionEntryReader search(final String baseObject,
      final SearchScope scope, final String filter,
      final String... attributeDescriptions)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final BlockingQueue<Response> entries = new LinkedBlockingQueue<Response>();
    final SearchRequest request = Requests.newSearchRequest(baseObject, scope,
        filter, attributeDescriptions);
    return search(request, entries);
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry searchSingleEntry(final SearchRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final SingleEntryHandler handler = new SingleEntryHandler();
    search(request, handler);

    if (handler.entryCount == 0)
    {
      // Did not find any entries.
      final Result result = Responses.newResult(
          ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED).setDiagnosticMessage(
          ERR_NO_SEARCH_RESULT_ENTRIES.get().toString());
      throw ErrorResultException.wrap(result);
    }
    else if (handler.entryCount > 1)
    {
      // Got more entries than expected.
      final Result result = Responses.newResult(
          ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED)
          .setDiagnosticMessage(
              ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES.get(handler.entryCount)
                  .toString());
      throw ErrorResultException.wrap(result);
    }
    else if (handler.firstReference != null)
    {
      // Got an unexpected search result reference.
      final Result result = Responses.newResult(
          ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED)
          .setDiagnosticMessage(
              ERR_UNEXPECTED_SEARCH_RESULT_REFERENCES.get(
                  handler.firstReference.getURIs().iterator().next())
                  .toString());
      throw ErrorResultException.wrap(result);
    }
    else
    {
      return handler.firstEntry;
    }
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry searchSingleEntry(final String baseObject,
      final SearchScope scope, final String filter,
      final String... attributeDescriptions) throws ErrorResultException,
      InterruptedException, LocalizedIllegalArgumentException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final SearchRequest request = Requests.newSearchRequest(baseObject, scope,
        filter, attributeDescriptions);
    return searchSingleEntry(request);
  }

}
