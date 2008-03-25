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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;


import java.util.LinkedHashSet;
import java.util.List;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.RawFilter;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;


/**
 * This abstract class wraps/decorates a given search operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the SearchOperationBasis.
 */
public abstract class SearchOperationWrapper extends OperationWrapper
       implements SearchOperation
{
  // The wrapped operation.
  private SearchOperation search;

  /**
   * Creates a new search operation based on the provided search operation.
   *
   * @param search The search operation to wrap
   */
  protected SearchOperationWrapper(SearchOperation search)
  {
    super(search);
    this.search = search;
  }

  /**
   * {@inheritDoc}
   */
  public boolean returnEntry(Entry entry, List<Control> controls)
  {
    boolean result;

    result = this.search.returnEntry(entry, controls);

    return result;
  }

  /**
   * {@inheritDoc}
   */
  public boolean returnReference(DN dn, SearchResultReference reference)
  {
    boolean result;

    result = this.search.returnReference(dn, reference);

    return result;
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return search.toString();
  }

  /**
   * {@inheritDoc}
   */
  public LinkedHashSet<String> getAttributes()
  {
    return search.getAttributes();
  }

  /**
   * {@inheritDoc}
   */
  public DN getBaseDN()
  {
    return search.getBaseDN();
  }

  /**
   * {@inheritDoc}
   */
  public DereferencePolicy getDerefPolicy()
  {
    return search.getDerefPolicy();
  }

  /**
   * {@inheritDoc}
   */
  public int getEntriesSent()
  {
    return search.getEntriesSent();
  }

  /**
   * {@inheritDoc}
   */
  public SearchFilter getFilter()
  {
    return search.getFilter();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawBaseDN()
  {
    return search.getRawBaseDN();
  }

  /**
   * {@inheritDoc}
   */
  public RawFilter getRawFilter()
  {
    return search.getRawFilter();
  }

  /**
   * {@inheritDoc}
   */
  public int getReferencesSent()
  {
    return search.getReferencesSent();
  }

  /**
   * {@inheritDoc}
   */
  public SearchScope getScope()
  {
    return search.getScope();
  }

  /**
   * {@inheritDoc}
   */
  public int getSizeLimit()
  {
    return search.getSizeLimit();
  }

  /**
   * {@inheritDoc}
   */
  public int getTimeLimit()
  {
    return search.getTimeLimit();
  }

  /**
   * {@inheritDoc}
   */
  public boolean getTypesOnly()
  {
    return search.getTypesOnly();
  }

  /**
   * {@inheritDoc}
   */
  public void sendSearchResultDone()
  {
    search.sendSearchResultDone();
  }

  /**
   * {@inheritDoc}
   */
  public void setAttributes(LinkedHashSet<String> attributes)
  {
    search.setAttributes(attributes);
  }

  /**
   * {@inheritDoc}
   */
  public void setBaseDN(DN baseDN)
  {
    search.setBaseDN(baseDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setDerefPolicy(DereferencePolicy derefPolicy)
  {
    search.setDerefPolicy(derefPolicy);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawBaseDN(ByteString rawBaseDN)
  {
    search.setRawBaseDN(rawBaseDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawFilter(RawFilter rawFilter)
  {
    search.setRawFilter(rawFilter);
  }

  /**
   * {@inheritDoc}
   */
  public void setScope(SearchScope scope)
  {
    search.setScope(scope);
  }

  /**
   * {@inheritDoc}
   */
  public void setSizeLimit(int sizeLimit)
  {
    search.setSizeLimit(sizeLimit);
  }

  /**
   * {@inheritDoc}
   */
  public void setTimeLimit(int timeLimit)
  {
    search.setTimeLimit(timeLimit);
  }

  /**
   * {@inheritDoc}
   */
  public void setTypesOnly(boolean typesOnly)
  {
    search.setTypesOnly(typesOnly);
  }

  /**
   * {@inheritDoc}
   */
  public void setTimeLimitExpiration(Long timeLimitExpiration)
  {
    search.setTimeLimitExpiration(timeLimitExpiration);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isReturnLDAPSubentries()
  {
    return search.isReturnLDAPSubentries();
  }

  /**
   * {@inheritDoc}
   */
  public void setReturnLDAPSubentries(boolean returnLDAPSubentries)
  {
    search.setReturnLDAPSubentries(returnLDAPSubentries);
  }

  /**
   * {@inheritDoc}
   */
  public MatchedValuesControl getMatchedValuesControl()
  {
    return search.getMatchedValuesControl();
  }

  /**
   * {@inheritDoc}
   */
  public void setMatchedValuesControl(MatchedValuesControl controls)
  {
    search.setMatchedValuesControl(controls);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isIncludeUsableControl()
  {
    return search.isIncludeUsableControl();
  }

  /**
   * {@inheritDoc}
   */
  public void setIncludeUsableControl(boolean includeUsableControl)
  {
    search.setIncludeUsableControl(includeUsableControl);
  }

  /**
   * {@inheritDoc}
   */
  public void setPersistentSearch(PersistentSearch psearch)
  {
    search.setPersistentSearch(psearch);
  }

  /**
   * {@inheritDoc}
   */
  public PersistentSearch getPersistentSearch()
  {
    return search.getPersistentSearch();
  }

  /**
   * {@inheritDoc}
   */
  public Long getTimeLimitExpiration()
  {
    return search.getTimeLimitExpiration();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isClientAcceptsReferrals()
  {
    return search.isClientAcceptsReferrals();
  }

  /**
   * {@inheritDoc}
   */
  public void setClientAcceptsReferrals(boolean clientAcceptReferrals)
  {
    search.setClientAcceptsReferrals(clientAcceptReferrals);
  }

  /**
   * {@inheritDoc}
   */
  public void incrementEntriesSent()
  {
    search.incrementEntriesSent();
  }

  /**
   * {@inheritDoc}
   */
  public void incrementReferencesSent()
  {
    search.incrementReferencesSent();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSendResponse()
  {
    return search.isSendResponse();
  }

  /**
   * {@inheritDoc}
   */
  public void setSendResponse(boolean sendResponse)
  {
    search.setSendResponse(sendResponse);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isRealAttributesOnly(){
    return search.isRealAttributesOnly();
  }

  /**
   * {@inheritDoc}
   */
  public void setRealAttributesOnly(boolean realAttributesOnly){
    search.setRealAttributesOnly(realAttributesOnly);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isVirtualAttributesOnly(){
    return search.isVirtualAttributesOnly();
  }

  /**
   * {@inheritDoc}
   */
  public void setVirtualAttributesOnly(boolean virtualAttributesOnly){
    search.setVirtualAttributesOnly(virtualAttributesOnly);
  }

  /**
   * {@inheritDoc}
   * @throws DirectoryException
   */
  public void sendSearchEntry(SearchResultEntry entry)
    throws DirectoryException
    {
    search.sendSearchEntry(entry);
  }

  /**
   * {@inheritDoc}
   * @throws DirectoryException
   */
  public boolean sendSearchReference(SearchResultReference reference)
    throws DirectoryException
    {
    return search.sendSearchReference(reference);
  }

  /**
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN()
  {
    return search.getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN){
    search.setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
