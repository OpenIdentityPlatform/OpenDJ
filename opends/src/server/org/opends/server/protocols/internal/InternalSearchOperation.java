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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.internal;



import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;




/**
 * This class defines a subclass of the core search operation that is
 * to be used for internal searches.  The primary difference between
 * this class and the core search operation is that the search entries
 * and references will be queued in memory rather than sent to a
 * client since there is no real client.
 */
public class InternalSearchOperation
       extends SearchOperation
{



  // The internal search listener for this search, if one was
  // provided.
  private InternalSearchListener searchListener;

  // The set of matching entries returned for this search.
  private LinkedList<SearchResultEntry> entryList;

  // The set of search references returned for this search.
  private LinkedList<SearchResultReference> referenceList;



  /**
   * Creates a new internal search operation with the provided
   * information.
   *
   * @param  internalConnection  The internal client connection with
   *                             which this internal search operation
   *                             is associated.
   * @param  operationID         The operation ID for this internal
   *                             search.
   * @param  messageID           The message ID for this internal
   *                             search.
   * @param  requestControls     The set of request controls for this
   *                             internal search.
   * @param  rawBaseDN           The raw base DN for this internal
   *                             search.
   * @param  scope               The scope for this internal search.
   * @param  derefPolicy         The alias dereferencing policy for
   *                             this internal search.
   * @param  sizeLimit           The size limit for this internal
   *                             search.
   * @param  timeLimit           The time limit for this internal
   *                             search.
   * @param  typesOnly           The typesOnly flag for this internal
   *                             search.
   * @param  rawFilter           The raw filter for this internal
   *                             search.
   * @param  attributes          The names of the requested attributes
   *                             for this internal search.
   * @param  searchListener      The internal search listener that
   *                             should be used to process the
   *                             results, or <CODE>null</CODE> if
   *                             they should be collected internally.
   */
  public InternalSearchOperation(
              ClientConnection internalConnection,
              long operationID, int messageID,
              List<Control> requestControls, ByteString rawBaseDN,
              SearchScope scope, DereferencePolicy derefPolicy,
              int sizeLimit, int timeLimit, boolean typesOnly,
              LDAPFilter rawFilter, LinkedHashSet<String> attributes,
              InternalSearchListener searchListener)
  {
    super(internalConnection, operationID, messageID, requestControls,
          rawBaseDN, scope, derefPolicy, sizeLimit, timeLimit,
          typesOnly, rawFilter, attributes);




    if (searchListener == null)
    {
      this.searchListener = null;
      this.entryList      = new LinkedList<SearchResultEntry>();
      this.referenceList  = new LinkedList<SearchResultReference>();
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
   * Creates a new internal search operation with the provided
   * information.
   *
   * @param  internalConnection  The internal client connection with
   *                             which this internal search operation
   *                             is associated.
   * @param  operationID         The operation ID for this internal
   *                             search.
   * @param  messageID           The message ID for this internal
   *                             search.
   * @param  requestControls     The set of request controls for this
   *                             internal search.
   * @param  baseDN              The base DN for this internal search.
   * @param  scope               The scope for this internal search.
   * @param  derefPolicy         The alias dereferencing policy for
   *                             this internal search.
   * @param  sizeLimit           The size limit for this internal
   *                             search.
   * @param  timeLimit           The time limit for this internal
   *                             search.
   * @param  typesOnly           The typesOnly flag for this internal
   *                             search.
   * @param  filter              The filter for this internal search.
   * @param  attributes          The names of the requested attributes
   *                             for this internal search.
   * @param  searchListener      The internal search listener that
   *                             should be used to process the
   *                             results, or <CODE>null</CODE> if
   *                             they should be collected internally.
   */
  public InternalSearchOperation(
              ClientConnection internalConnection,
              long operationID, int messageID,
              List<Control> requestControls, DN baseDN,
              SearchScope scope, DereferencePolicy derefPolicy,
              int sizeLimit, int timeLimit, boolean typesOnly,
              SearchFilter filter, LinkedHashSet<String> attributes,
              InternalSearchListener searchListener)
  {
    super(internalConnection, operationID, messageID, requestControls,
          baseDN, scope, derefPolicy, sizeLimit, timeLimit,
          typesOnly, filter, attributes);




    if (searchListener == null)
    {
      this.searchListener = null;
      this.entryList      = new LinkedList<SearchResultEntry>();
      this.referenceList  = new LinkedList<SearchResultReference>();
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
   */
  public void addSearchEntry(SearchResultEntry searchEntry)
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
  public LinkedList<SearchResultReference> getSearchReferences()
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
   */
  public void addSearchReference(
                   SearchResultReference searchReference)
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
}

