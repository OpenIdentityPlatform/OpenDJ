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
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;


/**
 * This abstract class is a generic operation wrapper intended to be
 * subclassed by a specific operation wrapper.
 */
public class OperationWrapper implements Operation
{
  // The wrapped operation.
  private Operation operation;


  /**
   * Creates a new generic operation wrapper.
   *
   * @param operation  the generic operation to wrap
   */
  public OperationWrapper(Operation operation)
  {
    this.operation = operation;
  }

  /**
   * {@inheritDoc}
   */
  public void addRequestControl(Control control)
  {
    operation.addRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void addResponseControl(Control control)
  {
    operation.addResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void appendAdditionalLogMessage(String message)
  {
    operation.appendAdditionalLogMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public void appendErrorMessage(String message)
  {
    operation.appendErrorMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    return operation.cancel(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void disconnectClient(
    DisconnectReason disconnectReason,
    boolean sendNotification,
    String message,
    int messageID)
  {
    operation.disconnectClient(
      disconnectReason, sendNotification, message, messageID);
  }

  /**
   * {@inheritDoc}
   */
  public boolean dontSynchronize()
  {
    return operation.dontSynchronize();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getAdditionalLogMessage()
  {
    return operation.getAdditionalLogMessage();
  }

  /**
   * {@inheritDoc}
   */
  public Object getAttachment(String name)
  {
    return operation.getAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public Map<String, Object> getAttachments()
  {
    return operation.getAttachments();
  }

  /**
   * {@inheritDoc}
   */
  public DN getAuthorizationDN()
  {
    return operation.getAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getAuthorizationEntry()
  {
    return operation.getAuthorizationEntry();
  }

  /**
   * {@inheritDoc}
   */
  public CancelRequest getCancelRequest()
  {
    return operation.getCancelRequest();
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult getCancelResult()
  {
    return operation.getCancelResult();
  }

  /**
   * {@inheritDoc}
   */
  public ClientConnection getClientConnection()
  {
    return operation.getClientConnection();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getCommonLogElements()
  {
    return operation.getCommonLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public long getConnectionID()
  {
    return operation.getConnectionID();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getErrorMessage()
  {
    return operation.getErrorMessage();
  }

  /**
   * {@inheritDoc}
   */
  public DN getMatchedDN()
  {
    return operation.getMatchedDN();
  }

  /**
   * {@inheritDoc}
   */
  public int getMessageID()
  {
    return operation.getMessageID();
  }

  /**
   * {@inheritDoc}
   */
  public long getOperationID()
  {
    return operation.getOperationID();
  }

  /**
   * {@inheritDoc}
   */
  public OperationType getOperationType()
  {
    return operation.getOperationType();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStartTime()
  {
    return operation.getProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStopTime()
  {
    return operation.getProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingTime()
  {
    return operation.getProcessingTime();
  }

  /**
   * {@inheritDoc}
   */
  public List<String> getReferralURLs()
  {
    return operation.getReferralURLs();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getRequestControls()
  {
    return operation.getRequestControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getRequestLogElements()
  {
    return operation.getRequestLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getResponseControls()
  {
    return operation.getResponseControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getResponseLogElements()
  {
    return operation.getResponseLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public ResultCode getResultCode()
  {
    return operation.getResultCode();
  }

  /**
   * {@inheritDoc}
   */
  public void indicateCancelled(CancelRequest cancelRequest)
  {
    operation.indicateCancelled(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isInternalOperation()
  {
    return operation.isInternalOperation();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSynchronizationOperation()
  {
    return operation.isSynchronizationOperation();
  }

  /**
   * {@inheritDoc}
   */
  public void operationCompleted()
  {
    operation.operationCompleted();
  }

  /**
   * {@inheritDoc}
   */
  public Object removeAttachment(String name)
  {
    return operation.removeAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public void removeRequestControl(Control control)
  {
    operation.removeRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void removeResponseControl(Control control)
  {
    operation.removeResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void setAdditionalLogMessage(StringBuilder additionalLogMessage)
  {
    operation.setAdditionalLogMessage(additionalLogMessage);
  }

  /**
   * {@inheritDoc}
   */
  public Object setAttachment(String name, Object value)
  {
    return operation.setAttachment(name, value);
  }

  /**
   * {@inheritDoc}
   */
  public void setAttachments(Map<String, Object> attachments)
  {
    operation.setAttachments(attachments);
  }

  /**
   * {@inheritDoc}
   */
  public void setAuthorizationEntry(Entry authorizationEntry)
  {
    operation.setAuthorizationEntry(authorizationEntry);
  }

  /**
   * {@inheritDoc}
   */
  public boolean setCancelRequest(CancelRequest cancelRequest)
  {
    return operation.setCancelRequest(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void setCancelResult(CancelResult cancelResult)
  {
    operation.setCancelResult(cancelResult);
  }

  /**
   * {@inheritDoc}
   */
  public void setDontSynchronize(boolean dontSynchronize)
  {
    operation.setDontSynchronize(dontSynchronize);
  }

  /**
   * {@inheritDoc}
   */
  public void setErrorMessage(StringBuilder errorMessage)
  {
    operation.setErrorMessage(errorMessage);
  }

  /**
   * {@inheritDoc}
   */
  public void setInternalOperation(boolean isInternalOperation)
  {
    operation.setInternalOperation(isInternalOperation);
  }

  /**
   * {@inheritDoc}
   */
  public void setMatchedDN(DN matchedDN)
  {
    operation.setMatchedDN(matchedDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStartTime()
  {
    operation.setProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStopTime()
  {
    operation.setProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setReferralURLs(List<String> referralURLs)
  {
    operation.setReferralURLs(referralURLs);
  }

  /**
   * {@inheritDoc}
   */
  public void setResponseData(DirectoryException directoryException)
  {
    operation.setResponseData(directoryException);
  }

  /**
   * {@inheritDoc}
   */
  public void setResultCode(ResultCode resultCode)
  {
    operation.setResultCode(resultCode);
  }

  /**
   * {@inheritDoc}
   */
  public void setSynchronizationOperation(boolean isSynchronizationOperation)
  {
    operation.setSynchronizationOperation(isSynchronizationOperation);
  }

  /**
   * {@inheritDoc}
   */
  public void toString(StringBuilder buffer)
  {
    operation.toString(buffer);
  }

}

