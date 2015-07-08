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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.protocols.internal;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.SearchOperationBasis;
import org.opends.server.types.*;

/**
 * This class defines a subclass of the core search operation that is
 * to be used for internal searches.  The primary difference between
 * this class and the core search operation is that the search entries
 * and references will be queued in memory rather than sent to a
 * client since there is no real client.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class InternalSearchOperation
       extends SearchOperationBasis
{
  /** The internal search listener for this search, if one was provided. */
  private InternalSearchListener searchListener;

  /** The set of matching entries returned for this search. */
  private LinkedList<SearchResultEntry> entryList;

  /** The set of search references returned for this search. */
  private List<SearchResultReference> referenceList;

  /**
   * Creates a new internal search operation with the provided information.
   *
   * @param internalConnection
   *          The internal client connection with which this internal search
   *          operation is associated.
   * @param operationID
   *          The operation ID for this internal search.
   * @param messageID
   *          The message ID for this internal search.
   * @param request
   *          The search request
   */
  public InternalSearchOperation(ClientConnection internalConnection, long operationID, int messageID,
      SearchRequest request)
  {
    this(internalConnection, operationID, messageID, request, null);
  }

  /**
   * Creates a new internal search operation with the provided information.
   *
   * @param  internalConnection  The internal client connection with
   *                             which this internal search operation
   *                             is associated.
   * @param  operationID         The operation ID for this internal
   *                             search.
   * @param  messageID           The message ID for this internal
   *                             search.
   * @param  request             The search request
   * @param  searchListener      The internal search listener that
   *                             should be used to process the
   *                             results, or <CODE>null</CODE> if
   *                             they should be collected internally.
   */
  public InternalSearchOperation(ClientConnection internalConnection, long operationID, int messageID,
      SearchRequest request, InternalSearchListener searchListener)
  {
    this(internalConnection, operationID, messageID,
        request.getControls(),
        request.getName(), request.getScope(),
        request.getDereferenceAliasesPolicy(),
        request.getSizeLimit(), request.getTimeLimit(), request.isTypesOnly(),
        request.getFilter(), request.getAttributes(),
        searchListener);
  }

  private InternalSearchOperation(
              ClientConnection internalConnection,
              long operationID, int messageID,
              List<Control> requestControls, DN baseDN,
              SearchScope scope, DereferenceAliasesPolicy derefPolicy,
              int sizeLimit, int timeLimit, boolean typesOnly,
              SearchFilter filter, Set<String> attributes,
              InternalSearchListener searchListener)
  {
    super(internalConnection, operationID, messageID, requestControls,
          baseDN, scope, derefPolicy, sizeLimit, timeLimit,
          typesOnly, filter, attributes);




    if (searchListener == null)
    {
      this.searchListener = null;
      this.entryList      = new LinkedList<>();
      this.referenceList  = new LinkedList<>();
    }
    else
    {
      this.searchListener = searchListener;
      this.entryList      = null;
      this.referenceList  = null;
    }

    setInternalOperation(true);
  }

  /**
   * Retrieves the set of search result entries returned for this
   * search.
   *
   * @return  The set of search result entries returned for this
   *          search, or <CODE>null</CODE> if a custom internal search
   *          listener is to be used.
   */
  public LinkedList<SearchResultEntry> getSearchEntries()
  {
    return entryList;
  }



  /**
   * Provides the provided search result entry to the internal search
   * listener if one was provided, or stores it in an internal list
   * otherwise.
   *
   * @param  searchEntry  The search result entry returned for this
   *                      search.
   *
   * @throws  DirectoryException  If a problem occurs while processing
   *                              the provided entry and the search
   *                              should be terminated.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  public void addSearchEntry(SearchResultEntry searchEntry)
         throws DirectoryException
  {
    if (searchListener == null)
    {
      entryList.add(searchEntry);
    }
    else
    {
      searchListener.handleInternalSearchEntry(this, searchEntry);
    }
  }



  /**
   * Retrieves the set of search result references returned for this
   * search.
   *
   * @return  The set of search result references returned for this
   *          search, or <CODE>null</CODE> if a custom internal search
   *          listener is to be used.
   */
  public List<SearchResultReference> getSearchReferences()
  {
    return referenceList;
  }



  /**
   * Provides the provided search result reference to the internal
   * search listener if one was provided, or stores it in an internal
   * list otherwise.
   *
   * @param  searchReference  The search result reference returned for
   *                          this search.
   *
   * @throws  DirectoryException  If a problem occurs while processing
   *                              the provided reference and the
   *                              search should be terminated.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  public void addSearchReference(
                   SearchResultReference searchReference)
         throws DirectoryException
  {
    if (searchListener == null)
    {
      referenceList.add(searchReference);
    }
    else
    {
      searchListener.handleInternalSearchReference(this,
                                                   searchReference);
    }
  }



  /**
   * Sends the provided search result entry to the client.
   *
   * @param  searchEntry  The search result entry to be sent to the
   *                      client.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to send the entry to the client and
   *                              the search should be terminated.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override
  public void sendSearchEntry(SearchResultEntry searchEntry)
         throws DirectoryException
  {
    addSearchEntry(searchEntry);
  }



  /**
   * Sends the provided search result reference to the client.
   *
   * @param  searchReference  The search result reference to be sent
   *                          to the client.
   *
   * @return  {@code true} if the client is able to accept referrals,
   *          or {@code false} if the client cannot handle referrals
   *          and no more attempts should be made to send them for the
   *          associated search operation.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to send the reference to the client
   *                              and the search should be terminated.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override
  public boolean sendSearchReference(
                      SearchResultReference searchReference)
         throws DirectoryException
  {
    addSearchReference(searchReference);
    return true;
  }
}

