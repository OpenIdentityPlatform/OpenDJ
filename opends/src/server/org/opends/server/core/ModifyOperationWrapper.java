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

import java.util.List;
import java.util.Map;

import org.opends.server.api.ClientConnection;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;

/**
 * This abstract class wraps/decorates a given modify operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the ModifyOperationBasis.
 */
public abstract class ModifyOperationWrapper implements ModifyOperation
{
  private ModifyOperation modify;

  /**
   * Creates a new modify operation based on the provided modify operation.
   *
   * @param modify The modify operation to wrap
   */
  protected ModifyOperationWrapper(ModifyOperation modify){
    this.modify = modify;
  }

  /**
   * {@inheritDoc}
   */
  public void addModification(Modification modification)
    throws DirectoryException
  {
    modify.addModification(modification);
  }

  /**
   * {@inheritDoc}
   */
  public void addRawModification(RawModification rawModification)
  {
    modify.addRawModification(rawModification);
  }

  /**
   * {@inheritDoc}
   */
  public void addResponseControl(Control control)
  {
    modify.addResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    return modify.cancel(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void disconnectClient(DisconnectReason disconnectReason,
      boolean sendNotification, String message, int messageID)
  {
    modify.disconnectClient(disconnectReason, sendNotification,
        message, messageID);
  }

  /**
   * {@inheritDoc}
   */
  public boolean dontSynchronize()
  {
    return modify.dontSynchronize();
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object obj)
  {
    return modify.equals(obj);
  }

  /**
   * {@inheritDoc}
   */
  public CancelRequest getCancelRequest()
  {
    return modify.getCancelRequest();
  }

  /**
   * {@inheritDoc}
   */
  public DN getEntryDN()
  {
    return modify.getEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public List<Modification> getModifications()
  {
    return modify.getModifications();
  }

  /**
   * {@inheritDoc}
   */
  public OperationType getOperationType()
  {
    return modify.getOperationType();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStartTime()
  {
    return modify.getProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStopTime()
  {
    return modify.getProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingTime()
  {
    return modify.getProcessingTime();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawEntryDN()
  {
    return modify.getRawEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public List<RawModification> getRawModifications()
  {
    return modify.getRawModifications();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getRequestLogElements()
  {
    return modify.getRequestLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getResponseControls()
  {
    return modify.getResponseControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getResponseLogElements()
  {
    return modify.getResponseLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return modify.hashCode();
  }

  /**
   * {@inheritDoc}
   */
  public void removeResponseControl(Control control)
  {
    modify.removeResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public boolean setCancelRequest(CancelRequest cancelRequest){
    return modify.setCancelRequest(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    modify.setRawEntryDN(rawEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawModifications(List<RawModification> rawModifications)
  {
    modify.setRawModifications(rawModifications);
  }

  /**
   * {@inheritDoc}
   */
  public void toString(StringBuilder buffer)
  {
    modify.toString(buffer);
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStopTime(){
    modify.setProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStartTime(){
    modify.setProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public void addRequestControl(Control control)
  {
    modify.addRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void appendAdditionalLogMessage(String message)
  {
    modify.appendAdditionalLogMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public void appendErrorMessage(String message)
  {
    modify.appendErrorMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getAdditionalLogMessage()
  {
    return modify.getAdditionalLogMessage();
  }

  /**
   * {@inheritDoc}
   */
  public Object getAttachment(String name)
  {
    return modify.getAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public Map<String, Object> getAttachments()
  {
    return modify.getAttachments();
  }

  /**
   * {@inheritDoc}
   */
  public DN getAuthorizationDN()
  {
    return modify.getAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getAuthorizationEntry()
  {
    return modify.getAuthorizationEntry();
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult getCancelResult()
  {
    return modify.getCancelResult();
  }

  /**
   * {@inheritDoc}
   */
  public ClientConnection getClientConnection()
  {
    return modify.getClientConnection();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getCommonLogElements()
  {
    return modify.getCommonLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public long getConnectionID()
  {
    return modify.getConnectionID();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getErrorMessage()
  {
    return modify.getErrorMessage();
  }

  /**
   * {@inheritDoc}
   */
  public DN getMatchedDN()
  {
    return modify.getMatchedDN();
  }

  /**
   * {@inheritDoc}
   */
  public int getMessageID()
  {
    return modify.getMessageID();
  }

  /**
   * {@inheritDoc}
   */
  public long getOperationID()
  {
    return modify.getOperationID();
  }

  /**
   * {@inheritDoc}
   */
  public List<String> getReferralURLs()
  {
    return modify.getReferralURLs();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getRequestControls()
  {
    return modify.getRequestControls();
  }

  /**
   * {@inheritDoc}
   */
  public ResultCode getResultCode()
  {
    return modify.getResultCode();
  }

  /**
   * {@inheritDoc}
   */
  public void indicateCancelled(CancelRequest cancelRequest)
  {
    modify.indicateCancelled(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isInternalOperation()
  {
    return modify.isInternalOperation();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSynchronizationOperation()
  {
    return modify.isSynchronizationOperation();
  }

  /**
   * {@inheritDoc}
   */
  public void operationCompleted()
  {
    modify.operationCompleted();
  }

  /**
   * {@inheritDoc}
   */
  public Object removeAttachment(String name)
  {
    return modify.removeAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public void removeRequestControl(Control control)
  {
    modify.removeRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void setAdditionalLogMessage(StringBuilder additionalLogMessage)
  {
    modify.setAdditionalLogMessage(additionalLogMessage);
  }

  /**
   * {@inheritDoc}
   */
  public Object setAttachment(String name, Object value)
  {
    return modify.setAttachment(name, value);
  }

  /**
   * {@inheritDoc}
   */
  public void setAttachments(Map<String, Object> attachments)
  {
    modify.setAttachments(attachments);
  }

  /**
   * {@inheritDoc}
   */
  public void setAuthorizationEntry(Entry authorizationEntry)
  {
    modify.setAuthorizationEntry(authorizationEntry);
  }

  /**
   * {@inheritDoc}
   */
  public void setCancelResult(CancelResult cancelResult)
  {
    modify.setCancelResult(cancelResult);
  }

  /**
   * {@inheritDoc}
   */
  public void setDontSynchronize(boolean dontSynchronize)
  {
    modify.setDontSynchronize(dontSynchronize);
  }

  /**
   * {@inheritDoc}
   */
  public void setErrorMessage(StringBuilder errorMessage)
  {
    modify.setErrorMessage(errorMessage);
  }

  /**
   * {@inheritDoc}
   */
  public void setInternalOperation(boolean isInternalOperation)
  {
    modify.setInternalOperation(isInternalOperation);
  }

  /**
   * {@inheritDoc}
   */
  public void setMatchedDN(DN matchedDN)
  {
    modify.setMatchedDN(matchedDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setReferralURLs(List<String> referralURLs)
  {
    modify.setReferralURLs(referralURLs);
  }

  /**
   * {@inheritDoc}
   */
  public void setResponseData(DirectoryException directoryException)
  {
    modify.setResponseData(directoryException);
  }

  /**
   * {@inheritDoc}
   */
  public void setResultCode(ResultCode resultCode)
  {
    modify.setResultCode(resultCode);
  }

  /**
   * {@inheritDoc}
   */
  public void setSynchronizationOperation(boolean isSynchronizationOperation)
  {
    modify.setSynchronizationOperation(isSynchronizationOperation);
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return modify.toString();
  }

  /**
   * {@inheritDoc}
   */
  public final long getChangeNumber(){
    return modify.getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  public void setChangeNumber(long changeNumber)
  {
    modify.setChangeNumber(changeNumber);
  }

  /**
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN()
  {
    return modify.getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN){
    modify.setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
