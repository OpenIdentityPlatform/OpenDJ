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
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.ResultCode;

/**
 * This abstract class wraps/decorates a given add operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the AddOperationBasis.
 */
public abstract class AddOperationWrapper implements AddOperation
{
  private AddOperation add;

  /**
   * Creates a new add operation based on the provided add operation.
   *
   * @param add The add operation to wrap
   */
  public AddOperationWrapper(AddOperation add){
    this.add = add;
  }

  /**
   * {@inheritDoc}
   */
  public void addObjectClass(ObjectClass objectClass, String name)
  {
    add.addObjectClass(objectClass, name);
  }

  /**
   * {@inheritDoc}
   */
  public void addRawAttribute(RawAttribute rawAttribute)
  {
    add.addRawAttribute(rawAttribute);
  }

  /**
   * {@inheritDoc}
   */
  public void addRequestControl(Control control)
  {
    add.addRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void addResponseControl(Control control)
  {
    add.addResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void appendAdditionalLogMessage(String message)
  {
    add.appendAdditionalLogMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public void appendErrorMessage(String message)
  {
    add.appendErrorMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    return add.cancel(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void disconnectClient(DisconnectReason disconnectReason,
      boolean sendNotification, String message, int messageID)
  {
    add.disconnectClient(disconnectReason, sendNotification, message,
        messageID);
  }

  /**
   * {@inheritDoc}
   */
  public boolean dontSynchronize()
  {
    return add.dontSynchronize();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getAdditionalLogMessage()
  {
    return add.getAdditionalLogMessage();
  }

  /**
   * {@inheritDoc}
   */
  public Object getAttachment(String name)
  {
    return add.getAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public Map<String, Object> getAttachments()
  {
    return add.getAttachments();
  }

  /**
   * {@inheritDoc}
   */
  public DN getAuthorizationDN()
  {
    return add.getAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getAuthorizationEntry()
  {
    return add.getAuthorizationEntry();
  }

  /**
   * {@inheritDoc}
   */
  public CancelRequest getCancelRequest()
  {
    return add.getCancelRequest();
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult getCancelResult()
  {
    return add.getCancelResult();
  }

  /**
   * {@inheritDoc}
   */
  public long getChangeNumber()
  {
    return add.getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  public ClientConnection getClientConnection()
  {
    return add.getClientConnection();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getCommonLogElements()
  {
    return add.getCommonLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public long getConnectionID()
  {
    return add.getConnectionID();
  }

  /**
   * {@inheritDoc}
   */
  public DN getEntryDN()
  {
    return add.getEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getErrorMessage()
  {
    return add.getErrorMessage();
  }

  /**
   * {@inheritDoc}
   */
  public DN getMatchedDN()
  {
    return add.getMatchedDN();
  }

  /**
   * {@inheritDoc}
   */
  public int getMessageID()
  {
    return add.getMessageID();
  }

  /**
   * {@inheritDoc}
   */
  public Map<ObjectClass, String> getObjectClasses()
  {
    return add.getObjectClasses();
  }

  /**
   * {@inheritDoc}
   */
  public Map<AttributeType, List<Attribute>> getOperationalAttributes()
  {
    return add.getOperationalAttributes();
  }

  /**
   * {@inheritDoc}
   */
  public long getOperationID()
  {
    return add.getOperationID();
  }

  /**
   * {@inheritDoc}
   */
  public OperationType getOperationType()
  {
    return add.getOperationType();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStartTime()
  {
    return add.getProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStopTime()
  {
    return add.getProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingTime()
  {
    return add.getProcessingTime();
  }

  /**
   * {@inheritDoc}
   */
  public List<RawAttribute> getRawAttributes()
  {
    return add.getRawAttributes();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawEntryDN()
  {
    return add.getRawEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public List<String> getReferralURLs()
  {
    return add.getReferralURLs();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getRequestControls()
  {
    return add.getRequestControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getRequestLogElements()
  {
    return add.getRequestLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getResponseControls()
  {
    return add.getResponseControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getResponseLogElements()
  {
    return add.getResponseLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public ResultCode getResultCode()
  {
    return add.getResultCode();
  }

  /**
   * {@inheritDoc}
   */
  public Map<AttributeType, List<Attribute>> getUserAttributes()
  {
    return add.getUserAttributes();
  }

  /**
   * {@inheritDoc}
   */
  public void indicateCancelled(CancelRequest cancelRequest)
  {
    add.indicateCancelled(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isInternalOperation()
  {
    return add.isInternalOperation();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSynchronizationOperation()
  {
    return add.isSynchronizationOperation();
  }

  /**
   * {@inheritDoc}
   */
  public void operationCompleted()
  {
    add.operationCompleted();
  }

  /**
   * {@inheritDoc}
   */
  public Object removeAttachment(String name)
  {
    return add.removeAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public void removeAttribute(AttributeType attributeType)
  {
    add.removeAttribute(attributeType);
  }

  /**
   * {@inheritDoc}
   */
  public void removeObjectClass(ObjectClass objectClass)
  {
    add.removeObjectClass(objectClass);
  }

  /**
   * {@inheritDoc}
   */
  public void removeRequestControl(Control control)
  {
    add.removeRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void removeResponseControl(Control control)
  {
    add.removeResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void setAdditionalLogMessage(StringBuilder additionalLogMessage)
  {
    add.setAdditionalLogMessage(additionalLogMessage);
  }

  /**
   * {@inheritDoc}
   */
  public Object setAttachment(String name, Object value)
  {
    return add.setAttachment(name, value);
  }

  /**
   * {@inheritDoc}
   */
  public void setAttachments(Map<String, Object> attachments)
  {
    add.setAttachments(attachments);
  }

  /**
   * {@inheritDoc}
   */
  public void setAttribute(AttributeType attributeType,
      List<Attribute> attributeList)
  {
    add.setAttribute(attributeType, attributeList);
  }

  /**
   * {@inheritDoc}
   */
  public void setAuthorizationEntry(Entry authorizationEntry)
  {
    add.setAuthorizationEntry(authorizationEntry);
  }

  /**
   * {@inheritDoc}
   */
  public boolean setCancelRequest(CancelRequest cancelRequest)
  {
    return add.setCancelRequest(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void setCancelResult(CancelResult cancelResult)
  {
    add.setCancelResult(cancelResult);
  }

  /**
   * {@inheritDoc}
   */
  public void setChangeNumber(long changeNumber)
  {
    add.setChangeNumber(changeNumber);
  }

  /**
   * {@inheritDoc}
   */
  public void setDontSynchronize(boolean dontSynchronize)
  {
    add.setDontSynchronize(dontSynchronize);
  }

  /**
   * {@inheritDoc}
   */
  public void setErrorMessage(StringBuilder errorMessage)
  {
    add.setErrorMessage(errorMessage);
  }

  /**
   * {@inheritDoc}
   */
  public void setInternalOperation(boolean isInternalOperation)
  {
    add.setInternalOperation(isInternalOperation);
  }

  /**
   * {@inheritDoc}
   */
  public void setMatchedDN(DN matchedDN)
  {
    add.setMatchedDN(matchedDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStartTime()
  {
    add.setProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStopTime()
  {
    add.setProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setRawAttributes(List<RawAttribute> rawAttributes)
  {
    add.setRawAttributes(rawAttributes);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    add.setRawEntryDN(rawEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setReferralURLs(List<String> referralURLs)
  {
    add.setReferralURLs(referralURLs);
  }

  /**
   * {@inheritDoc}
   */
  public void setResponseData(DirectoryException directoryException)
  {
    add.setResponseData(directoryException);
  }

  /**
   * {@inheritDoc}
   */
  public void setResultCode(ResultCode resultCode)
  {
    add.setResultCode(resultCode);
  }

  /**
   * {@inheritDoc}
   */
  public void setSynchronizationOperation(boolean isSynchronizationOperation)
  {
    add.setSynchronizationOperation(isSynchronizationOperation);
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return add.toString();
  }

  /**
   * {@inheritDoc}
   */
  public void toString(StringBuilder buffer)
  {
    add.toString(buffer);
  }

  /**
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN()
  {
    return add.getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    add.setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
