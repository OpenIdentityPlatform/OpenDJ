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
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;

/**
 * This abstract class wraps/decorates a given delete operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the DeleteOperationBasis.
 */
public abstract class DeleteOperationWrapper implements DeleteOperation
{
  DeleteOperation delete;

  /**
   * Creates a new delete operation based on the provided delete operation.
   *
   * @param delete The delete operation to wrap
   */
  public DeleteOperationWrapper(DeleteOperation delete){
    this.delete = delete;
  }

  /**
   * {@inheritDoc}
   */
  public void addRequestControl(Control control)
  {
    delete.addRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void addResponseControl(Control control)
  {
    delete.addResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void appendAdditionalLogMessage(String message)
  {
    delete.appendAdditionalLogMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public void appendErrorMessage(String message)
  {
    delete.appendErrorMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    return delete.cancel(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void disconnectClient(DisconnectReason disconnectReason,
      boolean sendNotification, String message, int messageID)
  {
    delete.disconnectClient(disconnectReason, sendNotification,
        message, messageID);
  }

  /**
   * {@inheritDoc}
   */
  public boolean dontSynchronize()
  {
    return delete.dontSynchronize();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getAdditionalLogMessage()
  {
    return delete.getAdditionalLogMessage();
  }

  /**
   * {@inheritDoc}
   */
  public Object getAttachment(String name)
  {
    return delete.getAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public Map<String, Object> getAttachments()
  {
    return delete.getAttachments();
  }

  /**
   * {@inheritDoc}
   */
  public DN getAuthorizationDN()
  {
    return delete.getAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getAuthorizationEntry()
  {
    return delete.getAuthorizationEntry();
  }

  /**
   * {@inheritDoc}
   */
  public CancelRequest getCancelRequest()
  {
    return delete.getCancelRequest();
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult getCancelResult()
  {
    return delete.getCancelResult();
  }

  /**
   * {@inheritDoc}
   */
  public ClientConnection getClientConnection()
  {
    return delete.getClientConnection();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getCommonLogElements()
  {
    return delete.getCommonLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public long getConnectionID()
  {
    return delete.getConnectionID();
  }

  /**
   * {@inheritDoc}
   */
  public DN getEntryDN()
  {
    return delete.getEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getErrorMessage()
  {
    return delete.getErrorMessage();
  }

  /**
   * {@inheritDoc}
   */
  public DN getMatchedDN()
  {
    return delete.getMatchedDN();
  }

  /**
   * {@inheritDoc}
   */
  public int getMessageID()
  {
    return delete.getMessageID();
  }

  /**
   * {@inheritDoc}
   */
  public long getOperationID()
  {
    return delete.getOperationID();
  }

  /**
   * {@inheritDoc}
   */
  public OperationType getOperationType()
  {
    return delete.getOperationType();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStartTime()
  {
    return delete.getProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStopTime()
  {
    return delete.getProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingTime()
  {
    return delete.getProcessingTime();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawEntryDN()
  {
    return delete.getRawEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public List<String> getReferralURLs()
  {
    return delete.getReferralURLs();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getRequestControls()
  {
    return delete.getRequestControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getRequestLogElements()
  {
    return delete.getRequestLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getResponseControls()
  {
    return delete.getResponseControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getResponseLogElements()
  {
    return delete.getResponseLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public ResultCode getResultCode()
  {
    return delete.getResultCode();
  }

  /**
   * {@inheritDoc}
   */
  public void indicateCancelled(CancelRequest cancelRequest)
  {
    delete.indicateCancelled(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isInternalOperation()
  {
    return delete.isInternalOperation();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSynchronizationOperation()
  {
    return delete.isSynchronizationOperation();
  }

  /**
   * {@inheritDoc}
   */
  public void operationCompleted()
  {
    delete.operationCompleted();
  }

  /**
   * {@inheritDoc}
   */
  public Object removeAttachment(String name)
  {
    return delete.removeAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public void removeRequestControl(Control control)
  {
    delete.removeRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void removeResponseControl(Control control)
  {
    delete.removeResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void setAdditionalLogMessage(StringBuilder additionalLogMessage)
  {
    delete.setAdditionalLogMessage(additionalLogMessage);
  }

  /**
   * {@inheritDoc}
   */
  public Object setAttachment(String name, Object value)
  {
    return delete.setAttachment(name, value);
  }


  /**
   * {@inheritDoc}
   */
  public void setAttachments(Map<String, Object> attachments)
  {
    delete.setAttachments(attachments);
  }

   /**
    * {@inheritDoc}
    */
  public void setAuthorizationEntry(Entry authorizationEntry)
  {
    delete.setAuthorizationEntry(authorizationEntry);
  }

  /**
   * {@inheritDoc}
   */
  public boolean setCancelRequest(CancelRequest cancelRequest)
  {
    return delete.setCancelRequest(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void setCancelResult(CancelResult cancelResult)
  {
    delete.setCancelResult(cancelResult);
  }

  /**
   * {@inheritDoc}
   */
  public void setDontSynchronize(boolean dontSynchronize)
  {
    delete.setDontSynchronize(dontSynchronize);
  }

  /**
   * {@inheritDoc}
   */
  public void setErrorMessage(StringBuilder errorMessage)
  {
    delete.setErrorMessage(errorMessage);
  }

  /**
   * {@inheritDoc}
   */
  public void setInternalOperation(boolean isInternalOperation)
  {
    delete.setInternalOperation(isInternalOperation);
  }

  /**
   * {@inheritDoc}
   */
  public void setMatchedDN(DN matchedDN)
  {
    delete.setMatchedDN(matchedDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStartTime()
  {
    delete.setProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStopTime()
  {
    delete.setProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    delete.setRawEntryDN(rawEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setReferralURLs(List<String> referralURLs)
  {
    delete.setReferralURLs(referralURLs);
  }

  /**
   * {@inheritDoc}
   */
  public void setResponseData(DirectoryException directoryException)
  {
    delete.setResponseData(directoryException);
  }

  /**
   * {@inheritDoc}
   */
  public void setResultCode(ResultCode resultCode)
  {
    delete.setResultCode(resultCode);
  }

  /**
   * {@inheritDoc}
   */
  public void setSynchronizationOperation(boolean isSynchronizationOperation)
  {
    delete.setSynchronizationOperation(isSynchronizationOperation);
  }

  /**
   * {@inheritDoc}
   */
  public final long getChangeNumber()
  {
    return delete.getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  public final void setChangeNumber(long changeNumber)
  {
    delete.setChangeNumber(changeNumber);
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return delete.toString();
  }

  /**
   * {@inheritDoc}
   */
  public void toString(StringBuilder buffer)
  {
    delete.toString(buffer);
  }

  /**
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN()
  {
    return delete.getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    delete.setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
