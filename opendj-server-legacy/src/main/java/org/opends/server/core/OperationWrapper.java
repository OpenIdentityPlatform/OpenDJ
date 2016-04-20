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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.controls.ControlDecoder;
import org.opends.server.types.AdditionalLogItem;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;

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

  @Override
  public void addRequestControl(Control control)
  {
    operation.addRequestControl(control);
  }

  @Override
  public void addResponseControl(Control control)
  {
    operation.addResponseControl(control);
  }

  @Override
  public void appendErrorMessage(LocalizableMessage message)
  {
    operation.appendErrorMessage(message);
  }

  @Override
  public void appendMaskedErrorMessage(LocalizableMessage maskedMessage)
  {
    operation.appendMaskedErrorMessage(maskedMessage);
  }

  @Override
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    return operation.cancel(cancelRequest);
  }

  @Override
  public void abort(CancelRequest cancelRequest)
  {
    operation.abort(cancelRequest);
  }

  @Override
  public void disconnectClient(
          DisconnectReason disconnectReason,
          boolean sendNotification,
          LocalizableMessage message
  )
  {
    operation.disconnectClient(
      disconnectReason, sendNotification, message);
  }

  @Override
  public boolean dontSynchronize()
  {
    return operation.dontSynchronize();
  }

  @Override
  public <T> T getAttachment(String name)
  {
    return operation.getAttachment(name);
  }

  @Override
  public Map<String, Object> getAttachments()
  {
    return operation.getAttachments();
  }

  @Override
  public DN getAuthorizationDN()
  {
    return operation.getAuthorizationDN();
  }

  @Override
  public Entry getAuthorizationEntry()
  {
    return operation.getAuthorizationEntry();
  }

  @Override
  public DN getProxiedAuthorizationDN()
  {
    return operation.getProxiedAuthorizationDN();
  }

  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    operation.setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

  @Override
  public CancelRequest getCancelRequest()
  {
    return operation.getCancelRequest();
  }

  @Override
  public CancelResult getCancelResult()
  {
    return operation.getCancelResult();
  }

  @Override
  public ClientConnection getClientConnection()
  {
    return operation.getClientConnection();
  }

  @Override
  public long getConnectionID()
  {
    return operation.getConnectionID();
  }

  @Override
  public LocalizableMessageBuilder getErrorMessage()
  {
    return operation.getErrorMessage();
  }

  @Override
  public LocalizableMessageBuilder getMaskedErrorMessage()
  {
    return operation.getMaskedErrorMessage();
  }

  @Override
  public ResultCode getMaskedResultCode()
  {
    return operation.getMaskedResultCode();
  }

  @Override
  public DN getMatchedDN()
  {
    return operation.getMatchedDN();
  }

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

  @Override
  public long getOperationID()
  {
    return operation.getOperationID();
  }

  @Override
  public OperationType getOperationType()
  {
    return operation.getOperationType();
  }

  @Override
  public long getProcessingStartTime()
  {
    return operation.getProcessingStartTime();
  }

  @Override
  public long getProcessingStopTime()
  {
    return operation.getProcessingStopTime();
  }

  @Override
  public long getProcessingTime()
  {
    return operation.getProcessingTime();
  }

  @Override
  public long getProcessingNanoTime()
  {
    return operation.getProcessingNanoTime();
  }

  @Override
  public List<String> getReferralURLs()
  {
    return operation.getReferralURLs();
  }

  @Override
  public List<Control> getRequestControls()
  {
    return operation.getRequestControls();
  }

  @Override
  public <T extends Control> T getRequestControl(
      ControlDecoder<T> d)throws DirectoryException
  {
    return operation.getRequestControl(d);
  }

  @Override
  public List<Control> getResponseControls()
  {
    return operation.getResponseControls();
  }

  @Override
  public ResultCode getResultCode()
  {
    return operation.getResultCode();
  }

  @Override
  public boolean isInnerOperation()
  {
    return operation.isInnerOperation();
  }

  @Override
  public boolean isInternalOperation()
  {
    return operation.isInternalOperation();
  }

  @Override
  public boolean isSynchronizationOperation()
  {
    return operation.isSynchronizationOperation();
  }

  @Override
  public void operationCompleted()
  {
    operation.operationCompleted();
  }

  @Override
  public <T> T removeAttachment(String name)
  {
    return operation.removeAttachment(name);
  }

  @Override
  public void removeResponseControl(Control control)
  {
    operation.removeResponseControl(control);
  }

  @Override
  public <T> T setAttachment(String name, Object value)
  {
    return operation.setAttachment(name, value);
  }

  @Override
  public void setAttachments(Map<String, Object> attachments)
  {
    operation.setAttachments(attachments);
  }

  @Override
  public void setAuthorizationEntry(Entry authorizationEntry)
  {
    operation.setAuthorizationEntry(authorizationEntry);
  }

  @Override
  public void setDontSynchronize(boolean dontSynchronize)
  {
    operation.setDontSynchronize(dontSynchronize);
  }

  @Override
  public void setErrorMessage(LocalizableMessageBuilder errorMessage)
  {
    operation.setErrorMessage(errorMessage);
  }

  @Override
  public void setInnerOperation(boolean isInnerOperation)
  {
    operation.setInnerOperation(isInnerOperation);
  }

  @Override
  public void setInternalOperation(boolean isInternalOperation)
  {
    operation.setInternalOperation(isInternalOperation);
  }

  @Override
  public void setMaskedErrorMessage(LocalizableMessageBuilder maskedErrorMessage)
  {
    operation.setMaskedErrorMessage(maskedErrorMessage);
  }

  @Override
  public void setMaskedResultCode(ResultCode maskedResultCode)
  {
    operation.setMaskedResultCode(maskedResultCode);
  }

  @Override
  public void setMatchedDN(DN matchedDN)
  {
    operation.setMatchedDN(matchedDN);
  }

  @Override
  public void setReferralURLs(List<String> referralURLs)
  {
    operation.setReferralURLs(referralURLs);
  }

  @Override
  public void setResponseData(DirectoryException directoryException)
  {
    operation.setResponseData(directoryException);
  }

  @Override
  public void setResultCode(ResultCode resultCode)
  {
    operation.setResultCode(resultCode);
  }

  @Override
  public void setSynchronizationOperation(boolean isSynchronizationOperation)
  {
    operation.setSynchronizationOperation(isSynchronizationOperation);
  }

  @Override
  public final int hashCode()
  {
    return getClientConnection().hashCode() * (int) getOperationID();
  }

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

  @Override
  public String toString()
  {
    return "Wrapped " + operation;
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    operation.toString(buffer);
  }

  @Override
  final synchronized public void checkIfCanceled(boolean signalTooLate)
      throws CanceledOperationException {
    operation.checkIfCanceled(signalTooLate);
  }

  @Override
  public void registerPostResponseCallback(Runnable callback)
  {
    operation.registerPostResponseCallback(callback);
  }

  @Override
  public void run()
  {
    operation.run();
  }

  @Override
  public List<AdditionalLogItem> getAdditionalLogItems()
  {
    return operation.getAdditionalLogItems();
  }

  @Override
  public void addAdditionalLogItem(AdditionalLogItem item)
  {
    operation.addAdditionalLogItem(item);
  }
}
