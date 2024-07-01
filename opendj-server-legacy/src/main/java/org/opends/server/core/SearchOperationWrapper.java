/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
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

  @Override
  public boolean returnEntry(Entry entry, List<Control> controls)
  {
    return getOperation().returnEntry(entry, controls);
  }

  @Override
  public boolean returnEntry(Entry entry, List<Control> controls,
                             boolean evaluateAci)
  {
    return getOperation().returnEntry(entry, controls, evaluateAci);
  }

  @Override
  public boolean returnReference(DN dn, SearchResultReference reference)
  {
    return getOperation().returnReference(dn, reference);
  }

  @Override
  public boolean returnReference(DN dn, SearchResultReference reference,
                                 boolean evaluateAci)
  {
    return getOperation().returnReference(dn, reference, evaluateAci);
  }

  @Override
  public String toString()
  {
    return getOperation().toString();
  }

  @Override
  public Set<String> getAttributes()
  {
    return getOperation().getAttributes();
  }

  @Override
  public DN getBaseDN()
  {
    return getOperation().getBaseDN();
  }

  @Override
  public DereferenceAliasesPolicy getDerefPolicy()
  {
    return getOperation().getDerefPolicy();
  }

  @Override
  public int getEntriesSent()
  {
    return getOperation().getEntriesSent();
  }

  @Override
  public SearchFilter getFilter()
  {
    return getOperation().getFilter();
  }

  @Override
  public ByteString getRawBaseDN()
  {
    return getOperation().getRawBaseDN();
  }

  @Override
  public RawFilter getRawFilter()
  {
    return getOperation().getRawFilter();
  }

  @Override
  public int getReferencesSent()
  {
    return getOperation().getReferencesSent();
  }

  @Override
  public SearchScope getScope()
  {
    return getOperation().getScope();
  }

  @Override
  public int getSizeLimit()
  {
    return getOperation().getSizeLimit();
  }

  @Override
  public int getTimeLimit()
  {
    return getOperation().getTimeLimit();
  }

  @Override
  public boolean getTypesOnly()
  {
    return getOperation().getTypesOnly();
  }

  @Override
  public void sendSearchResultDone()
  {
    getOperation().sendSearchResultDone();
  }

  @Override
  public void setAttributes(Set<String> attributes)
  {
    getOperation().setAttributes(attributes);
  }

  @Override
  public void setBaseDN(DN baseDN)
  {
    getOperation().setBaseDN(baseDN);
  }

  @Override
  public void setDerefPolicy(DereferenceAliasesPolicy derefPolicy)
  {
    getOperation().setDerefPolicy(derefPolicy);
  }

  @Override
  public void setRawBaseDN(ByteString rawBaseDN)
  {
    getOperation().setRawBaseDN(rawBaseDN);
  }

  @Override
  public void setRawFilter(RawFilter rawFilter)
  {
    getOperation().setRawFilter(rawFilter);
  }

  @Override
  public void setScope(SearchScope scope)
  {
    getOperation().setScope(scope);
  }

  @Override
  public void setSizeLimit(int sizeLimit)
  {
    getOperation().setSizeLimit(sizeLimit);
  }

  @Override
  public void setTimeLimit(int timeLimit)
  {
    getOperation().setTimeLimit(timeLimit);
  }

  @Override
  public void setTypesOnly(boolean typesOnly)
  {
    getOperation().setTypesOnly(typesOnly);
  }

  @Override
  public void setTimeLimitExpiration(long timeLimitExpiration)
  {
    getOperation().setTimeLimitExpiration(timeLimitExpiration);
  }

  @Override
  public boolean isReturnSubentriesOnly()
  {
    return getOperation().isReturnSubentriesOnly();
  }

  @Override
  public void setReturnSubentriesOnly(boolean returnLDAPSubentries)
  {
    getOperation().setReturnSubentriesOnly(returnLDAPSubentries);
  }

  @Override
  public MatchedValuesControl getMatchedValuesControl()
  {
    return getOperation().getMatchedValuesControl();
  }

  @Override
  public void setMatchedValuesControl(MatchedValuesControl controls)
  {
    getOperation().setMatchedValuesControl(controls);
  }

  @Override
  public boolean isIncludeUsableControl()
  {
    return getOperation().isIncludeUsableControl();
  }

  @Override
  public void setIncludeUsableControl(boolean includeUsableControl)
  {
    getOperation().setIncludeUsableControl(includeUsableControl);
  }

  @Override
  public long getTimeLimitExpiration()
  {
    return getOperation().getTimeLimitExpiration();
  }

  @Override
  public boolean isClientAcceptsReferrals()
  {
    return getOperation().isClientAcceptsReferrals();
  }

  @Override
  public void setClientAcceptsReferrals(boolean clientAcceptReferrals)
  {
    getOperation().setClientAcceptsReferrals(clientAcceptReferrals);
  }

  @Override
  public boolean isSendResponse()
  {
    return getOperation().isSendResponse();
  }

  @Override
  public void setSendResponse(boolean sendResponse)
  {
    getOperation().setSendResponse(sendResponse);
  }

  @Override
  public boolean isRealAttributesOnly(){
    return getOperation().isRealAttributesOnly();
  }

  @Override
  public void setRealAttributesOnly(boolean realAttributesOnly){
    getOperation().setRealAttributesOnly(realAttributesOnly);
  }

  @Override
  public boolean isVirtualAttributesOnly()
  {
    return getOperation().isVirtualAttributesOnly();
  }

  @Override
  public void setVirtualAttributesOnly(boolean virtualAttributesOnly){
    getOperation().setVirtualAttributesOnly(virtualAttributesOnly);
  }

  @Override
  public void sendSearchEntry(SearchResultEntry entry)
      throws DirectoryException
  {
    getOperation().sendSearchEntry(entry);
  }

  @Override
  public boolean sendSearchReference(SearchResultReference reference)
      throws DirectoryException
  {
    return getOperation().sendSearchReference(reference);
  }
}
