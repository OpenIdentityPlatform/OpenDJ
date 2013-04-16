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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.core;

import java.util.List;
import java.util.Map;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.ClientConnection;
import org.opends.server.controls.ControlDecoder;
import org.opends.server.types.*;


/**
 * This abstract class is a generic operation wrapper intended to be subclassed
 * by a specific operation wrapper.
 *
 * @param <W>
 *          the type of the object wrapped by this class
 */
public class OperationWrapper<W extends Operation> implements Operation
{
  /** The wrapped operation. */
  private W operation;


  /**
   * Creates a new generic operation wrapper.
   *
   * @param operation  the generic operation to wrap
   */
  public OperationWrapper(W operation)
  {
    this.operation = operation;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addRequestControl(Control control)
  {
    operation.addRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addResponseControl(Control control)
  {
    operation.addResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void appendErrorMessage(Message message)
  {
    operation.appendErrorMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    return operation.cancel(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void abort(CancelRequest cancelRequest)
  {
    operation.abort(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void disconnectClient(
          DisconnectReason disconnectReason,
          boolean sendNotification,
          Message message
  )
  {
    operation.disconnectClient(
      disconnectReason, sendNotification, message);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean dontSynchronize()
  {
    return operation.dontSynchronize();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object getAttachment(String name)
  {
    return operation.getAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> getAttachments()
  {
    return operation.getAttachments();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getAuthorizationDN()
  {
    return operation.getAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Entry getAuthorizationEntry()
  {
    return operation.getAuthorizationEntry();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CancelRequest getCancelRequest()
  {
    return operation.getCancelRequest();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CancelResult getCancelResult()
  {
    return operation.getCancelResult();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ClientConnection getClientConnection()
  {
    return operation.getClientConnection();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[][] getCommonLogElements()
  {
    return operation.getCommonLogElements();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getConnectionID()
  {
    return operation.getConnectionID();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MessageBuilder getErrorMessage()
  {
    return operation.getErrorMessage();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getMatchedDN()
  {
    return operation.getMatchedDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getMessageID()
  {
    return operation.getMessageID();
  }

  /**
   * Returns the wrapped {@link Operation}.
   *
   * @return the wrapped {@link Operation}.
   */
  protected W getOperation()
  {
    return operation;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getOperationID()
  {
    return operation.getOperationID();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OperationType getOperationType()
  {
    return operation.getOperationType();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getProcessingStartTime()
  {
    return operation.getProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getProcessingStopTime()
  {
    return operation.getProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getProcessingTime()
  {
    return operation.getProcessingTime();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getProcessingNanoTime()
  {
    return operation.getProcessingNanoTime();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> getReferralURLs()
  {
    return operation.getReferralURLs();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Control> getRequestControls()
  {
    return operation.getRequestControls();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T extends Control> T getRequestControl(
      ControlDecoder<T> d)throws DirectoryException
  {
    return operation.getRequestControl(d);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[][] getRequestLogElements()
  {
    return operation.getRequestLogElements();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Control> getResponseControls()
  {
    return operation.getResponseControls();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[][] getResponseLogElements()
  {
    return operation.getResponseLogElements();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ResultCode getResultCode()
  {
    return operation.getResultCode();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isInnerOperation()
  {
    return operation.isInnerOperation();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isInternalOperation()
  {
    return operation.isInternalOperation();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSynchronizationOperation()
  {
    return operation.isSynchronizationOperation();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void operationCompleted()
  {
    operation.operationCompleted();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object removeAttachment(String name)
  {
    return operation.removeAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeRequestControl(Control control)
  {
    operation.removeRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeResponseControl(Control control)
  {
    operation.removeResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object setAttachment(String name, Object value)
  {
    return operation.setAttachment(name, value);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAttachments(Map<String, Object> attachments)
  {
    operation.setAttachments(attachments);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAuthorizationEntry(Entry authorizationEntry)
  {
    operation.setAuthorizationEntry(authorizationEntry);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setDontSynchronize(boolean dontSynchronize)
  {
    operation.setDontSynchronize(dontSynchronize);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setErrorMessage(MessageBuilder errorMessage)
  {
    operation.setErrorMessage(errorMessage);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setInnerOperation(boolean isInnerOperation)
  {
    operation.setInnerOperation(isInnerOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setInternalOperation(boolean isInternalOperation)
  {
    operation.setInternalOperation(isInternalOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setMatchedDN(DN matchedDN)
  {
    operation.setMatchedDN(matchedDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setReferralURLs(List<String> referralURLs)
  {
    operation.setReferralURLs(referralURLs);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setResponseData(DirectoryException directoryException)
  {
    operation.setResponseData(directoryException);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setResultCode(ResultCode resultCode)
  {
    operation.setResultCode(resultCode);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSynchronizationOperation(boolean isSynchronizationOperation)
  {
    operation.setSynchronizationOperation(isSynchronizationOperation);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final int hashCode()
  {
    return getClientConnection().hashCode() * (int) getOperationID();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean equals(Object obj)
  {
    if (this == obj)
    {
      return true;
    }

    if (obj instanceof Operation)
    {
      Operation other = (Operation) obj;
      if (other.getClientConnection().equals(getClientConnection()))
      {
        return other.getOperationID() == getOperationID();
      }
    }

    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    operation.toString(buffer);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized final void checkIfCanceled(boolean signalTooLate)
      throws CanceledOperationException {
    operation.checkIfCanceled(signalTooLate);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void registerPostResponseCallback(Runnable callback)
  {
    operation.registerPostResponseCallback(callback);
  }

  /** {@inheritDoc} */
  @Override
  public void run()
  {
    operation.run();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<AdditionalLogItem> getAdditionalLogItems()
  {
    return operation.getAdditionalLogItems();
  }

  /**
   *{@inheritDoc}
   */
  @Override
  public void addAdditionalLogItem(AdditionalLogItem item)
  {
    operation.addAdditionalLogItem(item);
  }

}

