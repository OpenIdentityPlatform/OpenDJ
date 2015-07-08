/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Reject;
import org.opends.server.api.ClientConnection;
import org.opends.server.controls.ControlDecoder;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.operation.PostResponseOperation;
import org.opends.server.types.operation.PreParseOperation;

/**
 * This class defines a generic operation that may be processed by the
 * Directory Server.  Specific subclasses should implement specific
 * functionality appropriate for the type of operation.
 * <BR><BR>
 * Note that this class is not intended to be subclassed by any
 * third-party code outside of the OpenDJ project.  It should only be
 * extended by the operation types included in the
 * {@code org.opends.server.core} package.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public abstract class AbstractOperation
       implements Operation, PreParseOperation, PostResponseOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The set of response controls that will always be returned for
   * an abandon operation.
   */
  protected static final List<Control> NO_RESPONSE_CONTROLS = new ArrayList<>(0);

  /** The client connection with which this operation is associated. */
  protected final ClientConnection clientConnection;

  /** The message ID for this operation. */
  protected final int messageID;

  /** The operation ID for this operation. */
  protected final long operationID;

  /** Whether nanotime was used for this operation. */
  protected final boolean useNanoTime;

  /** The cancel request for this operation. */
  protected CancelRequest cancelRequest;

  /** The cancel result for this operation. */
  protected CancelResult cancelResult;

  /**
   * Indicates whether this is an internal operation triggered within the server
   * itself rather than requested by an external client.
   */
  private boolean isInternalOperation;
  private Boolean isInnerOperation;

  /**
   * Indicates whether this operation is involved in data synchronization
   * processing.
   */
  private boolean isSynchronizationOperation;

  /** The matched DN for this operation. */
  private DN matchedDN;

  /** The entry for the authorization identify for this operation. */
  private Entry authorizationEntry;

  /**
   * A set of attachments associated with this operation that might be used by
   * various components during its processing.
   */
  private Map<String,Object> attachments;

  /** The set of controls included in the request from the client. */
  private List<Control> requestControls;

  /** The set of referral URLs for this operation. */
  private List<String> referralURLs;

  /** The result code for this operation. */
  private ResultCode resultCode;

  /**
   * The real, masked result code  for this operation that will not be included
   * in the response to the client, but will be logged.
   */
  private ResultCode maskedResultCode;

  /**
   * Additional information that should be included in the log but not sent to
   * the client.
   */
  private List<AdditionalLogItem> additionalLogItems;

  /**
   * The error message for this operation that should be included in the log and
   * in the response to the client.
   */
  private LocalizableMessageBuilder errorMessage;

  /**
   * The real, masked error message for this operation that will not be included
   * in the response to the client, but will be logged.
   */
  private LocalizableMessageBuilder maskedErrorMessage;

  /**
   * Indicates whether this operation needs to be synchronized to other copies
   * of the data.
   */
  private boolean dontSynchronizeFlag;

  /** The time that processing started on this operation in milliseconds. */
  private long processingStartTime;

  /** The time that processing ended on this operation in milliseconds. */
  private long processingStopTime;

  /** The time that processing started on this operation in nanoseconds. */
  private long processingStartNanoTime;

  /** The time that processing ended on this operation in nanoseconds. */
  private long processingStopNanoTime;

  /** The callbacks to be invoked once a response has been sent. */
  private List<Runnable> postResponseCallbacks;

  /**
   * Creates a new operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this
   *                           operation is associated.
   * @param  operationID       The identifier assigned to this
   *                           operation for the client connection.
   * @param  messageID         The message ID of the request with
   *                           which this operation is associated.
   * @param  requestControls   The set of controls included in the
   *                           request.
   */
  protected AbstractOperation(ClientConnection clientConnection,
                      long operationID,
                      int messageID, List<Control> requestControls)
  {
    this.clientConnection = clientConnection;
    this.operationID      = operationID;
    this.messageID        = messageID;
    this.useNanoTime = DirectoryServer.getUseNanoTime();

    if (requestControls == null)
    {
      this.requestControls = new ArrayList<>(0);
    }
    else
    {
      this.requestControls  = requestControls;
    }

    resultCode                 = ResultCode.UNDEFINED;
    additionalLogItems         = null;
    errorMessage               = new LocalizableMessageBuilder();
    attachments                = new HashMap<>();
    matchedDN                  = null;
    referralURLs               = null;
    cancelResult               = null;
    isInternalOperation        = false;
    isSynchronizationOperation = false;
    authorizationEntry         =
         clientConnection.getAuthenticationInfo().
          getAuthorizationEntry();
  }


  /** {@inheritDoc} */
  @Override
  public void disconnectClient(DisconnectReason disconnectReason,
                               boolean sendNotification,
                               LocalizableMessage message)
  {
    clientConnection.disconnect(disconnectReason, sendNotification, message);
  }

  /** {@inheritDoc} */
  @Override
  public final ClientConnection getClientConnection()
  {
    return clientConnection;
  }

  /** {@inheritDoc} */
  @Override
  public final long getConnectionID()
  {
    return clientConnection.getConnectionID();
  }

  /** {@inheritDoc} */
  @Override
  public final long getOperationID()
  {
    return operationID;
  }

  /** {@inheritDoc} */
  @Override
  public final int getMessageID()
  {
    return messageID;
  }

  /** {@inheritDoc} */
  @Override
  public final List<Control> getRequestControls()
  {
    return requestControls;
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public final <T extends Control> T getRequestControl(
      ControlDecoder<T> d) throws DirectoryException
  {
    String oid = d.getOID();
    for(int i = 0; i < requestControls.size(); i++)
    {
      Control c = requestControls.get(i);
      if(c.getOID().equals(oid))
      {
        if(c instanceof LDAPControl)
        {
          T decodedControl = d.decode(c.isCritical(),
              ((LDAPControl) c).getValue());
          requestControls.set(i, decodedControl);
          return decodedControl;
        }
        else
        {
          return (T)c;
        }
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public final void addRequestControl(Control control)
  {
    requestControls.add(control);
  }

  /** {@inheritDoc} */
  @Override
  public final ResultCode getResultCode()
  {
    return resultCode;
  }

  /** {@inheritDoc} */
  @Override
  public final void setResultCode(ResultCode resultCode)
  {
    this.resultCode = resultCode;
  }

  /** {@inheritDoc} */
  @Override
  public final ResultCode getMaskedResultCode()
  {
    return maskedResultCode;
  }

  /** {@inheritDoc} */
  @Override
  public final void setMaskedResultCode(ResultCode maskedResultCode)
  {
    this.maskedResultCode = maskedResultCode;
  }

  /** {@inheritDoc} */
  @Override
  public final LocalizableMessageBuilder getErrorMessage()
  {
    return errorMessage;
  }

  /** {@inheritDoc} */
  @Override
  public final void setErrorMessage(LocalizableMessageBuilder errorMessage)
  {
    this.errorMessage = errorMessage;
  }

  /** {@inheritDoc} */
  @Override
  public final void appendErrorMessage(LocalizableMessage message)
  {
    if (errorMessage == null)
    {
      errorMessage = new LocalizableMessageBuilder();
    }
    if (message != null)
    {
      if (errorMessage.length() > 0)
      {
        errorMessage.append("  ");
      }
      errorMessage.append(message);
    }
  }

  /** {@inheritDoc} */
  @Override
  public final LocalizableMessageBuilder getMaskedErrorMessage()
  {
    return maskedErrorMessage;
  }

  /** {@inheritDoc} */
  @Override
  public final void setMaskedErrorMessage(LocalizableMessageBuilder maskedErrorMessage)
  {
    this.maskedErrorMessage = maskedErrorMessage;
  }

  /** {@inheritDoc} */
  @Override
  public final void appendMaskedErrorMessage(LocalizableMessage maskedMessage)
  {
    if (maskedErrorMessage == null)
    {
      maskedErrorMessage = new LocalizableMessageBuilder();
    }
    else if (maskedErrorMessage.length() > 0)
    {
      maskedErrorMessage.append("  ");
    }

    maskedErrorMessage.append(maskedMessage);
  }

  /** {@inheritDoc} */
  @Override
  public List<AdditionalLogItem> getAdditionalLogItems()
  {
    if (additionalLogItems != null)
    {
      return Collections.unmodifiableList(additionalLogItems);
    }
    return Collections.emptyList();
  }

  /** {@inheritDoc} */
  @Override
  public void addAdditionalLogItem(AdditionalLogItem item)
  {
    Reject.ifNull(item);
    if (additionalLogItems == null)
    {
      additionalLogItems = new LinkedList<>();
    }
    additionalLogItems.add(item);
  }

  /** {@inheritDoc} */
  @Override
  public final DN getMatchedDN()
  {
    return matchedDN;
  }

  /** {@inheritDoc} */
  @Override
  public final void setMatchedDN(DN matchedDN)
  {
    this.matchedDN = matchedDN;
  }

  /** {@inheritDoc} */
  @Override
  public final List<String> getReferralURLs()
  {
    return referralURLs;
  }

  /** {@inheritDoc} */
  @Override
  public final void setReferralURLs(List<String> referralURLs)
  {
    this.referralURLs = referralURLs;
  }

  /** {@inheritDoc} */
  @Override
  public final void setResponseData(
                         DirectoryException directoryException)
  {
    this.resultCode       = directoryException.getResultCode();
    this.maskedResultCode = directoryException.getMaskedResultCode();
    this.matchedDN        = directoryException.getMatchedDN();
    this.referralURLs     = directoryException.getReferralURLs();

    appendErrorMessage(directoryException.getMessageObject());
    final LocalizableMessage maskedMessage = directoryException.getMaskedMessage();
    if (maskedMessage != null) {
      appendMaskedErrorMessage(maskedMessage);
    }
  }

  /** {@inheritDoc} */
  @Override
  public final boolean isInternalOperation()
  {
    return isInternalOperation;
  }

  /** {@inheritDoc} */
  @Override
  public final void setInternalOperation(boolean isInternalOperation)
  {
    this.isInternalOperation = isInternalOperation;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInnerOperation()
  {
    if (this.isInnerOperation != null)
    {
      return this.isInnerOperation;
    }
    return isInternalOperation();
  }

  /** {@inheritDoc} */
  @Override
  public void setInnerOperation(boolean isInnerOperation)
  {
    this.isInnerOperation = isInnerOperation;
  }


  /** {@inheritDoc} */
  @Override
  public final boolean isSynchronizationOperation()
  {
    return isSynchronizationOperation;
  }

  /** {@inheritDoc} */
  @Override
  public final void setSynchronizationOperation(
                         boolean isSynchronizationOperation)
  {
    this.isSynchronizationOperation = isSynchronizationOperation;
  }

  /** {@inheritDoc} */
  @Override
  public boolean dontSynchronize()
  {
    return dontSynchronizeFlag;
  }

  /** {@inheritDoc} */
  @Override
  public final void setDontSynchronize(boolean dontSynchronize)
  {
    this.dontSynchronizeFlag = dontSynchronize;
  }

  /** {@inheritDoc} */
  @Override
  public final Entry getAuthorizationEntry()
  {
    return authorizationEntry;
  }

  /** {@inheritDoc} */
  @Override
  public final void setAuthorizationEntry(Entry authorizationEntry)
  {
    this.authorizationEntry = authorizationEntry;
  }

  /** {@inheritDoc} */
  @Override
  public final DN getAuthorizationDN()
  {
    if (authorizationEntry != null)
    {
      return authorizationEntry.getName();
    }
    return DN.rootDN();
  }

  /** {@inheritDoc} */
  @Override
  public final Map<String,Object> getAttachments()
  {
    return attachments;
  }

  /** {@inheritDoc} */
  @Override
  public final void setAttachments(Map<String, Object> attachments)
  {
    this.attachments = attachments;
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public final <T> T getAttachment(String name)
  {
    return (T) attachments.get(name);
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public final <T> T removeAttachment(String name)
  {
    return (T) attachments.remove(name);
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public final <T> T setAttachment(String name, Object value)
  {
    return (T) attachments.put(name, value);
  }

  /** {@inheritDoc} */
  @Override
  public final void operationCompleted()
  {
    // Notify the client connection that this operation is complete
    // and that it no longer needs to be retained.
    clientConnection.removeOperationInProgress(messageID);
  }

  /** {@inheritDoc} */
  @Override
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    abort(cancelRequest);

    long stopWaitingTime = System.currentTimeMillis() + 5000;
    while (cancelResult == null && System.currentTimeMillis() < stopWaitingTime)
    {
      try
      {
        Thread.sleep(50);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    if (cancelResult == null)
    {
      // This can happen in some rare cases (e.g., if a client
      // disconnects and there is still a lot of data to send to
      // that client), and in this case we'll prevent the cancel
      // thread from blocking for a long period of time.
      cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL, null);
    }

    return cancelResult;
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void abort(CancelRequest cancelRequest)
  {
    if(cancelResult == null && this.cancelRequest == null)
    {
      this.cancelRequest = cancelRequest;
    }
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized void checkIfCanceled(boolean signalTooLate)
      throws CanceledOperationException
  {
    if(cancelRequest != null)
    {
      throw new CanceledOperationException(cancelRequest);
    }

    if(signalTooLate && cancelResult != null)
    {
      cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
    }
  }

  /** {@inheritDoc} */
  @Override
  public final CancelRequest getCancelRequest()
  {
    return cancelRequest;
  }

  /** {@inheritDoc} */
  @Override
  public final CancelResult getCancelResult()
  {
    return cancelResult;
  }

  /** {@inheritDoc} */
  @Override
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /** {@inheritDoc} */
  @Override
  public final long getProcessingStartTime()
  {
    return processingStartTime;
  }

  /**
   * Set the time at which the processing started for this operation.
   */
  public final void setProcessingStartTime()
  {
    processingStartTime = System.currentTimeMillis();
    if(useNanoTime)
    {
      processingStartNanoTime = System.nanoTime();
    }
  }

  /** {@inheritDoc} */
  @Override
  public final long getProcessingStopTime()
  {
    return processingStopTime;
  }

  /**
   * Set the time at which the processing stopped for this operation.
   * This will actually hold a time immediately before the response
   * was sent to the client.
   */
  public final void setProcessingStopTime()
  {
    this.processingStopTime = System.currentTimeMillis();
    if(useNanoTime)
    {
      this.processingStopNanoTime = System.nanoTime();
    }
  }

  /** {@inheritDoc} */
  @Override
  public final long getProcessingTime()
  {
    return processingStopTime - processingStartTime;
  }

  /** {@inheritDoc} */
  @Override
  public final long getProcessingNanoTime()
  {
    if(useNanoTime)
    {
      return processingStopNanoTime - processingStartNanoTime;
    }
    return -1;
  }

  /** {@inheritDoc} */
  @Override
  public final void registerPostResponseCallback(Runnable callback)
  {
    if (postResponseCallbacks == null)
    {
      postResponseCallbacks = new LinkedList<>();
    }
    postResponseCallbacks.add(callback);
  }

  /** {@inheritDoc} */
  @Override
  public final int hashCode()
  {
    return clientConnection.hashCode() * (int) operationID;
  }

  /** {@inheritDoc} */
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
      if (other.getClientConnection().equals(clientConnection))
      {
        return other.getOperationID() == operationID;
      }
    }
    return false;
  }



  /**
   * Invokes the post response callbacks that were registered with
   * this operation.
   */
  protected final void invokePostResponseCallbacks()
  {
    if (postResponseCallbacks != null)
    {
      for (Runnable callback : postResponseCallbacks)
      {
        try
        {
          callback.run();
        }
        catch (Exception e)
        {
          // Should not happen.
          logger.traceException(e);
        }
      }
    }
  }

  /**
   * Updates the error message and the result code of the operation. This method
   * is called because no workflows were found to process the operation.
   */
  public void updateOperationErrMsgAndResCode()
  {
    // do nothing by default
  }
}
