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
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.AuthenticationType;
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
 * This abstract class wraps/decorates a given bind operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the BindOperationBasis.
 */
public abstract class BindOperationWrapper implements BindOperation
{
  private BindOperation bind;

  /**
   * Creates a new bind operation based on the provided bind operation.
   *
   * @param bind The bind operation to wrap
   */
  protected BindOperationWrapper(BindOperation bind){
    this.bind = bind;
  }

  /**
   * {@inheritDoc}
   */
  public void addRequestControl(Control control)
  {
    bind.addRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void addResponseControl(Control control)
  {
    bind.addResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void appendAdditionalLogMessage(String message)
  {
    bind.appendAdditionalLogMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public void appendErrorMessage(String message)
  {
    bind.appendErrorMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    return bind.cancel(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void disconnectClient(DisconnectReason disconnectReason,
      boolean sendNotification, String message, int messageID)
  {
    bind.disconnectClient(disconnectReason, sendNotification, message,
        messageID);
  }

  /**
   * {@inheritDoc}
   */
  public boolean dontSynchronize()
  {
    return bind.dontSynchronize();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getAdditionalLogMessage()
  {
    return bind.getAdditionalLogMessage();
  }

  /**
   * {@inheritDoc}
   */
  public Object getAttachment(String name)
  {
    return bind.getAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public Map<String, Object> getAttachments()
  {
    return bind.getAttachments();
  }

  /**
   * {@inheritDoc}
   */
  public AuthenticationInfo getAuthenticationInfo()
  {
    return bind.getAuthenticationInfo();
  }

  /**
   * {@inheritDoc}
   */
  public AuthenticationType getAuthenticationType()
  {
    return bind.getAuthenticationType();
  }

  /**
   * {@inheritDoc}
   */
  public int getAuthFailureID()
  {
    return bind.getAuthFailureID();
  }

  /**
   * {@inheritDoc}
   */
  public String getAuthFailureReason()
  {
    return bind.getAuthFailureReason();
  }

  /**
   * {@inheritDoc}
   */
  public DN getAuthorizationDN()
  {
    return bind.getAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getAuthorizationEntry()
  {
    return bind.getAuthorizationEntry();
  }

  /**
   * {@inheritDoc}
   */
  public DN getBindDN()
  {
    return bind.getBindDN();
  }

  /**
   * {@inheritDoc}
   */
  public CancelRequest getCancelRequest()
  {
    return bind.getCancelRequest();
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult getCancelResult()
  {
    return bind.getCancelResult();
  }

  /**
   * {@inheritDoc}
   */
  public ClientConnection getClientConnection()
  {
    return bind.getClientConnection();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getCommonLogElements()
  {
    return bind.getCommonLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public long getConnectionID()
  {
    return bind.getConnectionID();
  }

  /**
   * {@inheritDoc}
   */
  public StringBuilder getErrorMessage()
  {
    return bind.getErrorMessage();
  }

  /**
   * {@inheritDoc}
   */
  public DN getMatchedDN()
  {
    return bind.getMatchedDN();
  }

  /**
   * {@inheritDoc}
   */
  public int getMessageID()
  {
    return bind.getMessageID();
  }

  /**
   * {@inheritDoc}
   */
  public long getOperationID()
  {
    return bind.getOperationID();
  }

  /**
   * {@inheritDoc}
   */
  public OperationType getOperationType()
  {
    return bind.getOperationType();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStartTime()
  {
    return bind.getProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingStopTime()
  {
    return bind.getProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public long getProcessingTime()
  {
    return bind.getProcessingTime();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawBindDN()
  {
    return bind.getRawBindDN();
  }

  /**
   * {@inheritDoc}
   */
  public List<String> getReferralURLs()
  {
    return bind.getReferralURLs();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getRequestControls()
  {
    return bind.getRequestControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getRequestLogElements()
  {
    return bind.getRequestLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public List<Control> getResponseControls()
  {
    return bind.getResponseControls();
  }

  /**
   * {@inheritDoc}
   */
  public String[][] getResponseLogElements()
  {
    return bind.getResponseLogElements();
  }

  /**
   * {@inheritDoc}
   */
  public ResultCode getResultCode()
  {
    return bind.getResultCode();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getSASLAuthUserEntry()
  {
    return bind.getSASLAuthUserEntry();
  }

  /**
   * {@inheritDoc}
   */
  public ASN1OctetString getSASLCredentials()
  {
    return bind.getSASLCredentials();
  }

  /**
   * {@inheritDoc}
   */
  public String getSASLMechanism()
  {
    return bind.getSASLMechanism();
  }

  /**
   * {@inheritDoc}
   */
  public ASN1OctetString getServerSASLCredentials()
  {
    return bind.getServerSASLCredentials();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getSimplePassword()
  {
    return bind.getSimplePassword();
  }

  /**
   * {@inheritDoc}
   */
  public DN getUserEntryDN()
  {
    return bind.getUserEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public void indicateCancelled(CancelRequest cancelRequest)
  {
    bind.indicateCancelled(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isInternalOperation()
  {
    return bind.isInternalOperation();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSynchronizationOperation()
  {
    return bind.isSynchronizationOperation();
  }

  /**
   * {@inheritDoc}
   */
  public void operationCompleted()
  {
    bind.operationCompleted();
  }

  /**
   * {@inheritDoc}
   */
  public Object removeAttachment(String name)
  {
    return bind.removeAttachment(name);
  }

  /**
   * {@inheritDoc}
   */
  public void removeRequestControl(Control control)
  {
    bind.removeRequestControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void removeResponseControl(Control control)
  {
    bind.removeResponseControl(control);
  }

  /**
   * {@inheritDoc}
   */
  public void setAdditionalLogMessage(StringBuilder additionalLogMessage)
  {
    bind.setAdditionalLogMessage(additionalLogMessage);
  }

  /**
   * {@inheritDoc}
   */
  public Object setAttachment(String name, Object value)
  {
    return bind.setAttachment(name, value);
  }

  /**
   * {@inheritDoc}
   */
  public void setAttachments(Map<String, Object> attachments)
  {
    bind.setAttachments(attachments);
  }

  /**
   * {@inheritDoc}
   */
  public void setAuthenticationInfo(AuthenticationInfo authInfo)
  {
    bind.setAuthenticationInfo(authInfo);
  }

  /**
   * {@inheritDoc}
   */
  public void setAuthFailureReason(int id, String reason)
  {
    bind.setAuthFailureReason(id, reason);
  }

  /**
   * {@inheritDoc}
   */
  public void setAuthorizationEntry(Entry authorizationEntry)
  {
    bind.setAuthorizationEntry(authorizationEntry);
  }

  /**
   * {@inheritDoc}
   */
  public boolean setCancelRequest(CancelRequest cancelRequest)
  {
    return bind.setCancelRequest(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  public void setCancelResult(CancelResult cancelResult)
  {
    bind.setCancelResult(cancelResult);
  }

  /**
   * {@inheritDoc}
   */
  public void setDontSynchronize(boolean dontSynchronize)
  {
    bind.setDontSynchronize(dontSynchronize);
  }

  /**
   * {@inheritDoc}
   */
  public void setErrorMessage(StringBuilder errorMessage)
  {
    bind.setErrorMessage(errorMessage);
  }

  /**
   * {@inheritDoc}
   */
  public void setInternalOperation(boolean isInternalOperation)
  {
    bind.setInternalOperation(isInternalOperation);
  }

  /**
   * {@inheritDoc}
   */
  public void setMatchedDN(DN matchedDN)
  {
    bind.setMatchedDN(matchedDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStartTime()
  {
    bind.setProcessingStartTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setProcessingStopTime()
  {
    bind.setProcessingStopTime();
  }

  /**
   * {@inheritDoc}
   */
  public void setRawBindDN(ByteString rawBindDN)
  {
    bind.setRawBindDN(rawBindDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setReferralURLs(List<String> referralURLs)
  {
    bind.setReferralURLs(referralURLs);
  }

  /**
   * {@inheritDoc}
   */
  public void setResponseData(DirectoryException directoryException)
  {
    bind.setResponseData(directoryException);
  }

  /**
   * {@inheritDoc}
   */
  public void setResultCode(ResultCode resultCode)
  {
    bind.setResultCode(resultCode);
  }

  /**
   * {@inheritDoc}
   */
  public void setSASLAuthUserEntry(Entry saslAuthUserEntry)
  {
    bind.setSASLAuthUserEntry(saslAuthUserEntry);
  }

  /**
   * {@inheritDoc}
   */
  public void setSASLCredentials(String saslMechanism,
      ASN1OctetString saslCredentials)
  {
    bind.setSASLCredentials(saslMechanism, saslCredentials);
  }

  /**
   * {@inheritDoc}
   */
  public void setServerSASLCredentials(ASN1OctetString serverSASLCredentials)
  {
    bind.setServerSASLCredentials(serverSASLCredentials);
  }

  /**
   * {@inheritDoc}
   */
  public void setSimplePassword(ByteString simplePassword)
  {
    bind.setSimplePassword(simplePassword);
  }

  /**
   * {@inheritDoc}
   */
  public void setSynchronizationOperation(boolean isSynchronizationOperation)
  {
    bind.setSynchronizationOperation(isSynchronizationOperation);
  }

  /**
   * {@inheritDoc}
   */
  public void setUserEntryDN(DN userEntryDN){
    bind.setUserEntryDN(userEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return bind.toString();
  }

  /**
   * {@inheritDoc}
   */
  public void toString(StringBuilder buffer)
  {
    bind.toString(buffer);
  }

  /**
   * {@inheritDoc}
   */
  public void setProtocolVersion(String protocolVersion)
  {
    bind.setProtocolVersion(protocolVersion);
  }

  /**
   * {@inheritDoc}
   */
  public String getProtocolVersion()
  {
    return bind.getProtocolVersion();
  }

}
