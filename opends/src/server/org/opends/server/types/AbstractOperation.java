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
package org.opends.server.types;



import static org.opends.server.core.CoreConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.operation.PostResponseOperation;
import org.opends.server.types.operation.PreParseOperation;
import org.opends.server.core.DirectoryServer;



/**
 * This class defines a generic operation that may be processed by the
 * Directory Server.  Specific subclasses should implement specific
 * functionality appropriate for the type of operation.
 * <BR><BR>
 * Note that this class is not intended to be subclassed by any
 * third-party code outside of the OpenDS project.  It should only be
 * extended by the operation types included in the
 * {@code org.opends.server.core} package.
 */
public abstract class AbstractOperation
       implements Operation, PreParseOperation, PostResponseOperation,
                  Runnable
{
  /**
   * The set of response controls that will always be returned for
   * an abandon operation.
   */
  protected static final List<Control> NO_RESPONSE_CONTROLS =
       new ArrayList<Control>(0);

  /**
   * The client connection with which this operation is associated.
   */
  protected final ClientConnection clientConnection;


  /**
   * The message ID for this operation.
   */
  protected final int messageID;



  /**
   * The operation ID for this operation.
   */
  protected final long operationID;



  // Indicates whether this is an internal operation triggered within
  // the server itself rather than requested by an external client.
  private boolean isInternalOperation;

  // Indicates whether this operation is involved in data
  // synchronization processing.
  private boolean isSynchronizationOperation;

  // The cancel result for this operation.
  private CancelResult cancelResult;

  // The matched DN for this operation.
  private DN matchedDN;

  // The entry for the authorization identify for this operation.
  private Entry authorizationEntry;

  // A set of attachments associated with this operation that might
  // be used by various components during its processing.
  private Map<String,Object> attachments;

  // The set of controls included in the request from the client.
  private List<Control> requestControls;

  // The set of referral URLs for this operation.
  private List<String> referralURLs;

  // The result code for this operation.
  private ResultCode resultCode;

  // Additional information that should be included in the log but
  // not sent to the client.
  private StringBuilder additionalLogMessage;

  // The error message for this operation that should be included in
  // the log and in the response to the client.
  private StringBuilder errorMessage;

  // Indicates whether this operation nneds to be synchronized to
  // other copies of the data.
  private boolean dontSynchronizeFlag;

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;

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

    if (requestControls == null)
    {
      this.requestControls = new ArrayList<Control>(0);
    }
    else
    {
      this.requestControls  = requestControls;
    }

    resultCode                 = ResultCode.UNDEFINED;
    additionalLogMessage       = new StringBuilder();
    errorMessage               = new StringBuilder();
    attachments                = new HashMap<String,Object>();
    matchedDN                  = null;
    referralURLs               = null;
    cancelResult               = null;
    isInternalOperation        = false;
    isSynchronizationOperation = false;
    authorizationEntry         =
         clientConnection.getAuthenticationInfo().
          getAuthorizationEntry();
  }


  /**
   * {@inheritDoc}
   */
  public abstract OperationType getOperationType();

  /**
   * {@inheritDoc}
   */
  public abstract void disconnectClient(
      DisconnectReason disconnectReason,
      boolean sendNotification,
      String message, int messageID);

  /**
   * {@inheritDoc}
   */
  public final String[][] getCommonLogElements()
  {
    // Note that no debugging will be done in this method because
    // it is a likely candidate for being called by the logging
    // subsystem.

    return new String[][]
    {
      new String[] { LOG_ELEMENT_CONNECTION_ID,
                     String.valueOf(getConnectionID()) },
      new String[] { LOG_ELEMENT_OPERATION_ID,
          String.valueOf(operationID) },
      new String[] { LOG_ELEMENT_MESSAGE_ID,
          String.valueOf(messageID) }
    };
  }

  /**
   * {@inheritDoc}
   */
  public abstract String[][] getRequestLogElements();

  /**
   * {@inheritDoc}
   */
  public abstract String[][] getResponseLogElements();

  /**
   * {@inheritDoc}
   */
  public final ClientConnection getClientConnection()
  {
    return clientConnection;
  }

  /**
   * {@inheritDoc}
   */
  public final long getConnectionID()
  {
    return clientConnection.getConnectionID();
  }

  /**
   * {@inheritDoc}
   */
  public final long getOperationID()
  {
    return operationID;
  }

  /**
   * {@inheritDoc}
   */
  public final int getMessageID()
  {
    return messageID;
  }

  /**
   * {@inheritDoc}
   */
  public final List<Control> getRequestControls()
  {
    return requestControls;
  }

  /**
   * {@inheritDoc}
   */
  public final void addRequestControl(Control control)
  {
    requestControls.add(control);
  }

  /**
   * {@inheritDoc}
   */
  public final void removeRequestControl(Control control)
  {
    requestControls.remove(control);
  }

  /**
   * {@inheritDoc}
   */
  public abstract List<Control> getResponseControls();

  /**
   * {@inheritDoc}
   */
  public abstract void addResponseControl(Control control);

  /**
   * {@inheritDoc}
   */
  public abstract void removeResponseControl(Control control);

  /**
   * {@inheritDoc}
   */
  public final ResultCode getResultCode()
  {
    return resultCode;
  }

  /**
   * {@inheritDoc}
   */
  public final void setResultCode(ResultCode resultCode)
  {
    this.resultCode = resultCode;
  }

  /**
   * {@inheritDoc}
   */
  public final StringBuilder getErrorMessage()
  {
    return errorMessage;
  }

  /**
   * {@inheritDoc}
   */
  public final void setErrorMessage(StringBuilder errorMessage)
  {
    if (errorMessage == null)
    {
      this.errorMessage = new StringBuilder();
    }
    else
    {
      this.errorMessage = errorMessage;
    }
  }

  /**
   * {@inheritDoc}
   */
  public final void appendErrorMessage(String message)
  {
    if (errorMessage == null)
    {
      errorMessage = new StringBuilder(message);
    }
    else
    {
      if (errorMessage.length() > 0)
      {
        errorMessage.append("  ");
      }

      errorMessage.append(message);
    }
  }

  /**
   * {@inheritDoc}
   */
  public final StringBuilder getAdditionalLogMessage()
  {
    return additionalLogMessage;
  }


  /**
   * {@inheritDoc}
   */
  public final void setAdditionalLogMessage(StringBuilder
      additionalLogMessage)
  {
    if (additionalLogMessage == null)
    {
      this.additionalLogMessage = new StringBuilder();
    }
    else
    {
      this.additionalLogMessage = additionalLogMessage;
    }
  }

  /**
   * {@inheritDoc}
   */
  public final void appendAdditionalLogMessage(String message)
  {
    if (additionalLogMessage == null)
    {
      additionalLogMessage = new StringBuilder(message);
    }
    else
    {
      additionalLogMessage.append(message);
    }
  }

  /**
   * {@inheritDoc}
   */
  public final DN getMatchedDN()
  {
    return matchedDN;
  }

  /**
   * {@inheritDoc}
   */
  public final void setMatchedDN(DN matchedDN)
  {
    this.matchedDN = matchedDN;
  }

  /**
   * {@inheritDoc}
   */
  public final List<String> getReferralURLs()
  {
    return referralURLs;
  }

  /**
   * {@inheritDoc}
   */
  public final void setReferralURLs(List<String> referralURLs)
  {
    this.referralURLs = referralURLs;
  }

  /**
   * {@inheritDoc}
   */
  public final void setResponseData(DirectoryException
      directoryException)
  {
    this.resultCode   = directoryException.getResultCode();
    this.matchedDN    = directoryException.getMatchedDN();
    this.referralURLs = directoryException.getReferralURLs();

    appendErrorMessage(directoryException.getErrorMessage());
  }

  /**
   * {@inheritDoc}
   */
  public final boolean isInternalOperation()
  {
    return isInternalOperation;
  }

  /**
   * {@inheritDoc}
   */
  public final void setInternalOperation(boolean isInternalOperation)
  {
    this.isInternalOperation = isInternalOperation;
  }

  /**
   * {@inheritDoc}
   */
  public final boolean isSynchronizationOperation()
  {
    return isSynchronizationOperation;
  }

  /**
   * {@inheritDoc}
   */
  public final void setSynchronizationOperation(
                         boolean isSynchronizationOperation)
  {
    this.isSynchronizationOperation = isSynchronizationOperation;
  }

  /**
   * {@inheritDoc}
   */
  public final void setDontSynchronize(boolean dontSynchronize)
  {
    this.dontSynchronizeFlag = dontSynchronize;
  }

  /**
   * {@inheritDoc}
   */
  public final Entry getAuthorizationEntry()
  {
    return authorizationEntry;
  }

  /**
   * {@inheritDoc}
   */
  public final void setAuthorizationEntry(Entry authorizationEntry)
  {
    this.authorizationEntry = authorizationEntry;
  }

  /**
   * {@inheritDoc}
   */
  public final DN getAuthorizationDN()
  {
    if (authorizationEntry == null)
    {
      return DN.nullDN();
    }
    else
    {
      return authorizationEntry.getDN();
    }
  }

  /**
   * {@inheritDoc}
   */
  public final Map<String,Object> getAttachments()
  {
    return attachments;
  }

  /**
   * {@inheritDoc}
   */
  public final Object getAttachment(String name)
  {
    return attachments.get(name);
  }

  /**
   * {@inheritDoc}
   */
  public final Object removeAttachment(String name)
  {
    return attachments.remove(name);
  }

  /**
   * {@inheritDoc}
   */
  public final Object setAttachment(String name, Object value)
  {
    return attachments.put(name, value);
  }

  /**
   * Performs the work of actually processing this operation.
   * This should include all processing for the operation, including
   * invoking plugins, logging messages, performing access control,
   * managing synchronization, and any other work that might need to
   * be done in the course of processing.
   */
  /*public abstract void run();*/

  /**
   * {@inheritDoc}
   */
  public final void operationCompleted()
  {
    // Notify the client connection that this operation is complete
    // and that it no longer needs to be retained.
    clientConnection.removeOperationInProgress(messageID);
  }

  /**
   * {@inheritDoc}
   */
  public abstract CancelResult cancel(CancelRequest cancelRequest);

  /**
   * {@inheritDoc}
   */
  public abstract boolean setCancelRequest(CancelRequest
      cancelRequest);

  /**
   * {@inheritDoc}
   */
  public abstract CancelRequest getCancelRequest();

  /**
   * {@inheritDoc}
   */
  public final CancelResult getCancelResult()
  {
    return cancelResult;
  }

  /**
   * {@inheritDoc}
   */
  public final void setCancelResult(CancelResult cancelResult)
  {
    this.cancelResult = cancelResult;
  }

  /**
   * {@inheritDoc}
   */
  public final void indicateCancelled(CancelRequest cancelRequest)
  {
    setCancelResult(CancelResult.CANCELED);

    if (cancelRequest.notifyOriginalRequestor() ||
        DirectoryServer.notifyAbandonedOperations())
    {
      setResultCode(ResultCode.CANCELED);

      String cancelReason = cancelRequest.getCancelReason();
      if (cancelReason != null)
      {
        appendErrorMessage(cancelReason);
      }

      clientConnection.sendResponse(this);
    }
  }

  /**
   * {@inheritDoc}
   */
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /**
   * {@inheritDoc}
   */
  public abstract void toString(StringBuilder buffer);

  /**
   * {@inheritDoc}
   */
  public boolean dontSynchronize()
  {
    return dontSynchronizeFlag;
  }

  /**
   * {@inheritDoc}
   */
  public final void setAttachments(Map<String, Object> attachments){
    this.attachments = attachments;
  }

  /**
   * {@inheritDoc}
   */
  public final long getProcessingStartTime()
  {
    return processingStartTime;
  }

  /**
   * {@inheritDoc}
   */
  public final long getProcessingStopTime()
  {
    return processingStopTime;
  }

  /**
   * {@inheritDoc}
   */
  public final void setProcessingStopTime()
  {
    this.processingStopTime = System.currentTimeMillis();
  }

  /**
   * {@inheritDoc}
   */
  public final void setProcessingStartTime()
  {
    processingStartTime = System.currentTimeMillis();
  }

  /**
   * {@inheritDoc}
   */
  public final long getProcessingTime()
  {
    return (processingStopTime - processingStartTime);
  }

  /**
   * Performs the work of actually processing this operation.  This
   * should include all processing for the operation, including
   * invoking pre-parse and post-response plugins, logging messages
   * and any other work that might need to be done in the course of
   * processing.
   */
  public abstract void run();
}

