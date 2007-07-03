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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.opends.server.api.ClientConnection;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawFilter;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;

/**
 * This abstract class wraps/decorates a given search operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the SearchOperationBasis.
 */
public abstract class SearchOperationWrapper implements SearchOperation
{
  private SearchOperation search;

  /**
   * Creates a new search operation based on the provided search operation.
   *
   * @param search The search operation to wrap
   */
  protected SearchOperationWrapper(SearchOperation search){
    this.search = search;
  }

  /**
   * {@inheritDoc}
   */
  public void addRequestControl(Control control)
  {
    search.addRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void addResponseControl(Control control)
  {
    search.addResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void appendAdditionalLogMessage(String message)
  {
    search.appendAdditionalLogMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public void appendErrorMessage(String message)
  {
    search.appendErrorMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    return search.cancel(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void disconnectClient(DisconnectReason disconnectReason,
      boolean sendNotification, String message, int messageID)
  {
    search.disconnectClient(disconnectReason, sendNotification, message,
        messageID);
  }

  /**
   * {@inheritDoc}
   */
  public boolean dontSynchronize()
  {
    return search.dontSynchronize();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getAdditionalLogMessage()
  {
    return search.getAdditionalLogMessage();
  }

  /**
   * {@inheritDoc}
   */
  public Object getAttachment(String name)
  {
    return search.getAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public Map<String, Object> getAttachments()
  {
    return search.getAttachments();
  }

  /**
   * {@inheritDoc}
   */
  public DN getAuthorizationDN()
  {
    return search.getAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getAuthorizationEntry()
  {
    return search.getAuthorizationEntry();
  }

  /**
   * {@inheritDoc}
   */
  public CancelRequest getCancelRequest()
  {
    return search.getCancelRequest();
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult getCancelResult()
  {
    return search.getCancelResult();
  }

  /**
   * {@inheritDoc}
   */
  public ClientConnection getClientConnection()
  {
    return search.getClientConnection();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getCommonLogElements()
  {
    return search.getCommonLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public long getConnectionID()
  {
    return search.getConnectionID();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getErrorMessage()
  {
    return search.getErrorMessage();
  }

  /**
   * {@inheritDoc}
   */
  public DN getMatchedDN()
  {
    return search.getMatchedDN();
  }

  /**
   * {@inheritDoc}
   */
  public int getMessageID()
  {
    return search.getMessageID();
  }

  /**
   * {@inheritDoc}
   */
  public long getOperationID()
  {
    return search.getOperationID();
  }

  /**
   * {@inheritDoc}
   */
  public OperationType getOperationType()
  {
    return search.getOperationType();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStartTime()
  {
    return search.getProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStopTime()
  {
    return search.getProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingTime()
  {
    return search.getProcessingTime();
  }

  /**
   * {@inheritDoc}
   */
  public List<String> getReferralURLs()
  {
    return search.getReferralURLs();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getRequestControls()
  {
    return search.getRequestControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getRequestLogElements()
  {
    return search.getRequestLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getResponseControls()
  {
    return search.getResponseControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getResponseLogElements()
  {
    return search.getResponseLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public ResultCode getResultCode()
  {
    return search.getResultCode();
  }

  /**
   * {@inheritDoc}
   */
  public void indicateCancelled(CancelRequest cancelRequest)
  {
    search.indicateCancelled(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isInternalOperation()
  {
    return search.isInternalOperation();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSynchronizationOperation()
  {
    return search.isSynchronizationOperation();
  }

  /**
   * {@inheritDoc}
   */
  public void operationCompleted()
  {
    search.operationCompleted();
  }

  /**
   * {@inheritDoc}
   */
  public Object removeAttachment(String name)
  {
    return search.removeAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public void removeRequestControl(Control control)
  {
    search.removeRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void removeResponseControl(Control control)
  {
    search.removeResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public boolean returnEntry(Entry entry, List<Control> controls)
  {
    return search.returnEntry(entry, controls);
  }

  /**
   * {@inheritDoc}
   */
  public boolean returnReference(SearchResultReference reference)
  {
    return search.returnReference(reference);
  }

  /**
   * {@inheritDoc}
   */
  public void setAdditionalLogMessage(StringBuilder additionalLogMessage)
  {
    search.setAdditionalLogMessage(additionalLogMessage);
  }

  /**
   * {@inheritDoc}
   */
  public Object setAttachment(String name, Object value)
  {
    return search.setAttachment(name, value);
  }

  /**
   * {@inheritDoc}
   */
  public void setAttachments(Map<String, Object> attachments)
  {
    search.setAttachments(attachments);
  }

  /**
   * {@inheritDoc}
   */
  public void setAuthorizationEntry(Entry authorizationEntry)
  {
    search.setAuthorizationEntry(authorizationEntry);
  }

  /**
   * {@inheritDoc}
   */
  public boolean setCancelRequest(CancelRequest cancelRequest)
  {
    return search.setCancelRequest(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void setCancelResult(CancelResult cancelResult)
  {
    search.setCancelResult(cancelResult);
  }

  /**
   * {@inheritDoc}
   */
  public void setDontSynchronize(boolean dontSynchronize)
  {
    search.setDontSynchronize(dontSynchronize);
  }

  /**
   * {@inheritDoc}
   */
  public void setErrorMessage(StringBuilder errorMessage)
  {
    search.setErrorMessage(errorMessage);
  }

  /**
   * {@inheritDoc}
   */
  public void setInternalOperation(boolean isInternalOperation)
  {
    search.setInternalOperation(isInternalOperation);
  }

  /**
   * {@inheritDoc}
   */
  public void setMatchedDN(DN matchedDN)
  {
    search.setMatchedDN(matchedDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStartTime()
  {
    search.setProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStopTime()
  {
    search.setProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setReferralURLs(List<String> referralURLs)
  {
    search.setReferralURLs(referralURLs);
  }

  /**
   * {@inheritDoc}
   */
  public void setResponseData(DirectoryException directoryException)
  {
    search.setResponseData(directoryException);
  }

  /**
   * {@inheritDoc}
   */
  public void setResultCode(ResultCode resultCode)
  {
    search.setResultCode(resultCode);
  }

  /**
   * {@inheritDoc}
   */
  public void setSynchronizationOperation(boolean isSynchronizationOperation)
  {
    search.setSynchronizationOperation(isSynchronizationOperation);
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
  public void toString(StringBuilder buffer)
  {
    search.toString(buffer);
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