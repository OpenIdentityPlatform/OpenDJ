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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.core;


import java.util.List;
import java.util.Set;

import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.types.*;


/**
 * This abstract class wraps/decorates a given search operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the SearchOperationBasis.
 */
public abstract class SearchOperationWrapper extends
    OperationWrapper<SearchOperation> implements SearchOperation
{

  /**
   * Creates a new search operation based on the provided search operation.
   *
   * @param search The search operation to wrap
   */
  protected SearchOperationWrapper(SearchOperation search)
  {
    super(search);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean returnEntry(Entry entry, List<Control> controls)
  {
    return getOperation().returnEntry(entry, controls);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean returnEntry(Entry entry, List<Control> controls,
                             boolean evaluateAci)
  {
    return getOperation().returnEntry(entry, controls, evaluateAci);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean returnReference(DN dn, SearchResultReference reference)
  {
    return getOperation().returnReference(dn, reference);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean returnReference(DN dn, SearchResultReference reference,
                                 boolean evaluateAci)
  {
    return getOperation().returnReference(dn, reference, evaluateAci);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return getOperation().toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getAttributes()
  {
    return getOperation().getAttributes();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getBaseDN()
  {
    return getOperation().getBaseDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DereferencePolicy getDerefPolicy()
  {
    return getOperation().getDerefPolicy();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getEntriesSent()
  {
    return getOperation().getEntriesSent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchFilter getFilter()
  {
    return getOperation().getFilter();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getRawBaseDN()
  {
    return getOperation().getRawBaseDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RawFilter getRawFilter()
  {
    return getOperation().getRawFilter();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getReferencesSent()
  {
    return getOperation().getReferencesSent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchScope getScope()
  {
    return getOperation().getScope();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getSizeLimit()
  {
    return getOperation().getSizeLimit();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getTimeLimit()
  {
    return getOperation().getTimeLimit();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean getTypesOnly()
  {
    return getOperation().getTypesOnly();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendSearchResultDone()
  {
    getOperation().sendSearchResultDone();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAttributes(Set<String> attributes)
  {
    getOperation().setAttributes(attributes);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setBaseDN(DN baseDN)
  {
    getOperation().setBaseDN(baseDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setDerefPolicy(DereferencePolicy derefPolicy)
  {
    getOperation().setDerefPolicy(derefPolicy);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawBaseDN(ByteString rawBaseDN)
  {
    getOperation().setRawBaseDN(rawBaseDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawFilter(RawFilter rawFilter)
  {
    getOperation().setRawFilter(rawFilter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setScope(SearchScope scope)
  {
    getOperation().setScope(scope);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSizeLimit(int sizeLimit)
  {
    getOperation().setSizeLimit(sizeLimit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setTimeLimit(int timeLimit)
  {
    getOperation().setTimeLimit(timeLimit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setTypesOnly(boolean typesOnly)
  {
    getOperation().setTypesOnly(typesOnly);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setTimeLimitExpiration(Long timeLimitExpiration)
  {
    getOperation().setTimeLimitExpiration(timeLimitExpiration);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isReturnSubentriesOnly()
  {
    return getOperation().isReturnSubentriesOnly();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setReturnSubentriesOnly(boolean returnLDAPSubentries)
  {
    getOperation().setReturnSubentriesOnly(returnLDAPSubentries);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MatchedValuesControl getMatchedValuesControl()
  {
    return getOperation().getMatchedValuesControl();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setMatchedValuesControl(MatchedValuesControl controls)
  {
    getOperation().setMatchedValuesControl(controls);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isIncludeUsableControl()
  {
    return getOperation().isIncludeUsableControl();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setIncludeUsableControl(boolean includeUsableControl)
  {
    getOperation().setIncludeUsableControl(includeUsableControl);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getTimeLimitExpiration()
  {
    return getOperation().getTimeLimitExpiration();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isClientAcceptsReferrals()
  {
    return getOperation().isClientAcceptsReferrals();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setClientAcceptsReferrals(boolean clientAcceptReferrals)
  {
    getOperation().setClientAcceptsReferrals(clientAcceptReferrals);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void incrementEntriesSent()
  {
    getOperation().incrementEntriesSent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void incrementReferencesSent()
  {
    getOperation().incrementReferencesSent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSendResponse()
  {
    return getOperation().isSendResponse();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSendResponse(boolean sendResponse)
  {
    getOperation().setSendResponse(sendResponse);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isRealAttributesOnly(){
    return getOperation().isRealAttributesOnly();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRealAttributesOnly(boolean realAttributesOnly){
    getOperation().setRealAttributesOnly(realAttributesOnly);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isVirtualAttributesOnly(){
    return getOperation().isVirtualAttributesOnly();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setVirtualAttributesOnly(boolean virtualAttributesOnly){
    getOperation().setVirtualAttributesOnly(virtualAttributesOnly);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendSearchEntry(SearchResultEntry entry)
    throws DirectoryException
    {
    getOperation().sendSearchEntry(entry);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean sendSearchReference(SearchResultReference reference)
    throws DirectoryException
    {
    return getOperation().sendSearchReference(reference);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getProxiedAuthorizationDN()
  {
    return getOperation().getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN){
    getOperation().setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
