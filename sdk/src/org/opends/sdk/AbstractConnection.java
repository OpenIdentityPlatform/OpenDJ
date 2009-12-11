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
import java.util.LinkedList;
import java.util.List;

import org.opends.sdk.requests.Requests;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.*;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Validator;



/**
 * This class provides a skeletal implementation of the {@code
 * Connection} interface, to minimize the effort required to implement
 * this interface.
 */
public abstract class AbstractConnection implements Connection
{

  private static final class SingleEntryHandler implements
      SearchResultHandler<Void>
  {
    private volatile SearchResultEntry firstEntry = null;

    private volatile SearchResultReference firstReference = null;

    private volatile int entryCount = 0;



    public void handleEntry(Void p, SearchResultEntry entry)
    {
      if (firstEntry == null)
      {
        firstEntry = entry;
      }
      entryCount++;
    }



    public void handleReference(Void p, SearchResultReference reference)
    {
      if (firstReference == null)
      {
        firstReference = reference;
      }
    }

  }



  /**
   * Creates a new abstract connection.
   */
  protected AbstractConnection()
  {
    // No implementation required.
  }



  public Result add(Entry entry) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return add(Requests.newAddRequest(entry));
  }



  public Result add(String... ldifLines) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      LocalizedIllegalArgumentException, IllegalStateException,
      NullPointerException
  {
    return add(Requests.newAddRequest(ldifLines));
  }



  public BindResult bind(String name, String password)
      throws ErrorResultException, InterruptedException,
      LocalizedIllegalArgumentException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return bind(Requests.newSimpleBindRequest(name, password));
  }



  public CompareResult compare(String name,
      String attributeDescription, String assertionValue)
      throws ErrorResultException, InterruptedException,
      LocalizedIllegalArgumentException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return compare(Requests.newCompareRequest(name,
        attributeDescription, assertionValue));
  }



  public Result delete(String name) throws ErrorResultException,
      InterruptedException, LocalizedIllegalArgumentException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return delete(Requests.newDeleteRequest(name));
  }



  public GenericExtendedResult extendedRequest(String requestName,
      ByteString requestValue) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return extendedRequest(Requests.newGenericExtendedRequest(
        requestName, requestValue));
  }



  public Result modify(String... ldifLines)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, LocalizedIllegalArgumentException,
      IllegalStateException, NullPointerException
  {
    return modify(Requests.newModifyRequest(ldifLines));
  }



  public Result modifyDN(String name, String newRDN)
      throws ErrorResultException, InterruptedException,
      LocalizedIllegalArgumentException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return modifyDN(Requests.newModifyDNRequest(name, newRDN));
  }



  public SearchResultEntry readEntry(DN baseObject,
      String... attributeDescriptions) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    SearchRequest request = Requests.newSearchRequest(baseObject,
        SearchScope.BASE_OBJECT, Filter.getObjectClassPresentFilter(),
        attributeDescriptions);
    return searchSingleEntry(request);
  }



  public SearchResultEntry readEntry(String baseObject,
      String... attributeDescriptions) throws ErrorResultException,
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
  public Schema readSchema(DN name) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException
  {
    return Schema.readSchema(this, name);
  }



  /**
   * {@inheritDoc}
   */
  public Schema readSchema(String name) throws ErrorResultException,
      InterruptedException, LocalizedIllegalArgumentException,
      UnsupportedOperationException, IllegalStateException
  {
    return readSchema(DN.valueOf(name));
  }



  /**
   * {@inheritDoc}
   */
  public Schema readSchemaForEntry(DN name)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException
  {
    return Schema.readSchemaForEntry(this, name);
  }



  /**
   * {@inheritDoc}
   */
  public Schema readSchemaForEntry(String name)
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
  public Result search(SearchRequest request,
      Collection<? super SearchResultEntry> entries)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return search(request, entries, null);
  }



  public Result search(SearchRequest request,
      final Collection<? super SearchResultEntry> entries,
      final Collection<? super SearchResultReference> references)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    Validator.ensureNotNull(request, entries);

    // FIXME: does this need to be thread safe?
    SearchResultHandler<Void> handler = new SearchResultHandler<Void>()
    {

      public void handleEntry(Void p, SearchResultEntry entry)
      {
        entries.add(entry);
      }



      public void handleReference(Void p,
          SearchResultReference reference)
      {
        if (references != null)
        {
          references.add(reference);
        }
      }
    };

    return search(request, handler, null);
  }



  public List<SearchResultEntry> search(String baseObject,
      SearchScope scope, String filter, String... attributeDescriptions)
      throws ErrorResultException, InterruptedException,
      LocalizedIllegalArgumentException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    List<SearchResultEntry> entries = new LinkedList<SearchResultEntry>();
    SearchRequest request = Requests.newSearchRequest(baseObject,
        scope, filter, attributeDescriptions);
    search(request, entries);
    return entries;
  }



  public SearchResultEntry searchSingleEntry(SearchRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    SingleEntryHandler handler = new SingleEntryHandler();
    search(request, handler, null);

    if (handler.entryCount == 0)
    {
      // Did not find any entries.
      Result result = Responses.newResult(
          ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED)
          .setDiagnosticMessage(
              ERR_NO_SEARCH_RESULT_ENTRIES.get().toString());
      throw ErrorResultException.wrap(result);
    }
    else if (handler.entryCount > 1)
    {
      // Got more entries than expected.
      Result result = Responses.newResult(
          ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED)
          .setDiagnosticMessage(
              ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES.get(
                  handler.entryCount).toString());
      throw ErrorResultException.wrap(result);
    }
    else if (handler.firstReference != null)
    {
      // Got an unexpected search result reference.
      Result result = Responses.newResult(
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



  public SearchResultEntry searchSingleEntry(String baseObject,
      SearchScope scope, String filter, String... attributeDescriptions)
      throws ErrorResultException, InterruptedException,
      LocalizedIllegalArgumentException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    SearchRequest request = Requests.newSearchRequest(baseObject,
        scope, filter, attributeDescriptions);
    return searchSingleEntry(request);
  }

}
