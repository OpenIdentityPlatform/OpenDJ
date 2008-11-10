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
 */
package org.opends.server.types;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;


import static org.opends.server.core.CoreConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.operation.PostResponseOperation;
import org.opends.server.types.operation.PreParseOperation;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.debug.
    DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.loggers.debug.DebugTracer;


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
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public abstract class AbstractOperation
       implements Operation, PreParseOperation, PostResponseOperation,
                  Runnable
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

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


  /**
   * Wether nanotime was used for this operation.
   */
  protected final boolean useNanoTime;


  /**
   * The cancel request for this operation.
   */
  protected CancelRequest cancelRequest;


  /**
   * The cancel result for this operation.
   */
  protected CancelResult cancelResult;

  // Indicates whether this is an internal operation triggered within
  // the server itself rather than requested by an external client.
  private boolean isInternalOperation;

  // Indicates whether this operation is involved in data
  // synchronization processing.
  private boolean isSynchronizationOperation;

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
  private MessageBuilder additionalLogMessage;

  // The error message for this operation that should be included in
  // the log and in the response to the client.
  private MessageBuilder errorMessage;

  // Indicates whether this operation nneds to be synchronized to
  // other copies of the data.
  private boolean dontSynchronizeFlag;

  // The time that processing started on this operation in
  // milliseconds.
  private long processingStartTime;

  // The time that processing ended on this operation in
  // milliseconds.
  private long processingStopTime;

  // The time that processing started on this operation in
  // nanoseconds.
  private long processingStartNanoTime;

  // The time that processing ended on this operation in
  // nanoseconds.
  private long processingStopNanoTime;

  // The callbacks to be invoked once a response has been sent.
  private List<Runnable> postResponseCallbacks = null;

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
      this.requestControls = new ArrayList<Control>(0);
    }
    else
    {
      this.requestControls  = requestControls;
    }

    resultCode                 = ResultCode.UNDEFINED;
    additionalLogMessage       = new MessageBuilder();
    errorMessage               = new MessageBuilder();
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
   * Retrieves the operation type for this operation.
   *
   * @return  The operation type for this operation.
   */
  public abstract OperationType getOperationType();



  /**
   * Terminates the client connection being used to process this
   * operation.  If this is called by a plugin, then that plugin must
   * return a result indicating that the  client connection has been
   * teriminated.
   *
   * @param  disconnectReason  The disconnect reason that provides the
   *                           generic cause for the disconnect.
   * @param  sendNotification  Indicates whether to try to provide
   *                           notification
   *                           to the client that the connection will
   *                           be closed.
   * @param  message           The message to send to the client.  It
   *                           may be {@code null} if no notification
   *                           is to be sent.
   */
  public void disconnectClient(DisconnectReason disconnectReason,
                               boolean sendNotification,
                               Message message)
  {
    clientConnection.disconnect(disconnectReason, sendNotification,
            message);
  }



  /**
   * Retrieves a set of standard elements that should be logged in all
   * requests and responses for all types of operations.  Each element
   * in the array will itself be a two-element array in which the
   * first element is the name of the field and the second is a string
   * representation of the value, or {@code null} if there is no value
   * for that field.
   *
   * @return  A standard set of elements that should be logged in
   *          requests and responses for all types of operations.
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
   * Retrieves a standard set of elements that should be logged in
   * requests for this type of operation.  Each element in the array
   * will itself be a two-element array in which the first element is
   * the name of the field and the second is a string representation
   * of the value, or {@code null} if there is no value for that
   * field.
   *
   * @return  A standard set of elements that should be logged in
   *          requests for this type of operation.
   */
  public abstract String[][] getRequestLogElements();



  /**
   * Retrieves a standard set of elements that should be logged in
   * responses for this type of operation.  Each element in the array
   * will itself be a two-element array in which the first element is
   * the name of the field and the second is a string representation
   * of the value, or {@code null} if there is no value for that
   * field.
   *
   * @return  A standard set of elements that should be logged in
   *          responses for this type of operation.
   */
  public abstract String[][] getResponseLogElements();



  /**
   * Retrieves the client connection with which this operation is
   * associated.
   *
   * @return  The client connection with which this operation is
   *          associated.
   */
  public final ClientConnection getClientConnection()
  {
    return clientConnection;
  }



  /**
   * Retrieves the unique identifier that is assigned to the client
   * connection that submitted this operation.
   *
   * @return  The unique identifier that is assigned to the client
   *          connection that submitted this operation.
   */
  public final long getConnectionID()
  {
    return clientConnection.getConnectionID();
  }



  /**
   * Retrieves the operation ID for this operation.
   *
   * @return  The operation ID for this operation.
   */
  public final long getOperationID()
  {
    return operationID;
  }



  /**
   * Retrieves the message ID assigned to this operation.
   *
   * @return  The message ID assigned to this operation.
   */
  public final int getMessageID()
  {
    return messageID;
  }



  /**
   * Retrieves the set of controls included in the request from the
   * client.  The returned list must not be altered.
   *
   * @return  The set of controls included in the request from the
   *          client.
   */
  public final List<Control> getRequestControls()
  {
    return requestControls;
  }



  /**
   * Adds the provided control to the set of request controls for this
   * operation.  This method may only be called by pre-parse plugins.
   *
   * @param  control  The control to add to the set of request
   *                  controls for this operation.
   */
  public final void addRequestControl(Control control)
  {
    requestControls.add(control);
  }



  /**
   * Removes the provided control from the set of request controls for
   * this operation.  This method may only be called by pre-parse
   * plugins.
   *
   * @param  control  The control to remove from the set of request
   *                  controls for this operation.
   */
  public final void removeRequestControl(Control control)
  {
    requestControls.remove(control);
  }



  /**
   * Retrieves the set of controls to include in the response to the
   * client.  The contents of this list must not be altered.
   *
   * @return  The set of controls to include in the response to the
   *          client.
   */
  public abstract List<Control> getResponseControls();



  /**
   * Adds the provided control to the set of controls to include in
   * the response to the client.  This method may not be called by
   * post-response plugins.
   *
   * @param  control  The control to add to the set of controls to
   *                  include in the response to the client.
   */
  public abstract void addResponseControl(Control control);



  /**
   * Removes the provided control from the set of controls to include
   * in the response to the client.  This method may not be called by
   * post-response plugins.
   *
   * @param  control  The control to remove from the set of controls
   *                  to include in the response to the client.
   */
  public abstract void removeResponseControl(Control control);



  /**
   * Retrieves the result code for this operation.
   *
   * @return  The result code associated for this operation, or
   *          {@code UNDEFINED} if the operation has not yet
   *          completed.
   */
  public final ResultCode getResultCode()
  {
    return resultCode;
  }



  /**
   * Specifies the result code for this operation.  This method may
   * not be called by post-response plugins.
   *
   * @param  resultCode  The result code for this operation.
   */
  public final void setResultCode(ResultCode resultCode)
  {
    this.resultCode = resultCode;
  }



  /**
   * Retrieves the error message for this operation.  Its contents may
   * be altered by pre-parse, pre-operation, and post-operation
   * plugins, but not by post-response plugins.
   *
   * @return  The error message for this operation.
   */
  public final MessageBuilder getErrorMessage()
  {
    return errorMessage;
  }



  /**
   * Specifies the error message for this operation.  This method may
   * not be called by post-response plugins.
   *
   * @param  errorMessage  The error message for this operation.
   */
  public final void setErrorMessage(MessageBuilder errorMessage)
  {
    if (errorMessage == null)
    {
      this.errorMessage = new MessageBuilder();
    }
    else
    {
      this.errorMessage = errorMessage;
    }
  }



  /**
   * Appends the provided message to the error message buffer.  If the
   * buffer has not yet been created, then this will create it first
   * and then add the provided message.  This method may not be called
   * by post-response plugins.
   *
   * @param  message  The message to append to the error message
   *                  buffer.
   */
  public final void appendErrorMessage(Message message)
  {
    if (errorMessage == null)
    {
      errorMessage = new MessageBuilder(message);
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
   * Retrieves the additional log message for this operation, which
   * should be written to the log but not included in the response to
   * the client.  The contents of this buffer may be altered by
   * pre-parse, pre-operation, and post-operation plugins, but not by
   * post-response plugins.
   *
   * @return  The additional log message for this operation.
   */
  public final MessageBuilder getAdditionalLogMessage()
  {
    return additionalLogMessage;
  }



  /**
   * Specifies the additional log message for this operation, which
   * should be written to the log but not included in the response to
   * the client.  This method may not be called by post-response
   * plugins.
   *
   * @param  additionalLogMessage  The additional log message for this
   *                               operation.
   */
  public final void setAdditionalLogMessage(
                         MessageBuilder additionalLogMessage)
  {
    if (additionalLogMessage == null)
    {
      this.additionalLogMessage = new MessageBuilder();
    }
    else
    {
      this.additionalLogMessage = additionalLogMessage;
    }
  }



  /**
   * Appends the provided message to the additional log information
   * for this operation.  This method may not be called by
   * post-response plugins.
   *
   * @param  message  The message that should be appended to the
   *                  additional log information for this operation.
   */
  public final void appendAdditionalLogMessage(Message message)
  {
    if (additionalLogMessage == null)
    {
      additionalLogMessage = new MessageBuilder(message);
    }
    else
    {
      additionalLogMessage.append(message);
    }
  }



  /**
   * Retrieves the matched DN for this operation.
   *
   * @return  The matched DN for this operation, or {@code null} if
   *          the operation has not yet completed or does not have a
   *          matched DN.
   */
  public final DN getMatchedDN()
  {
    return matchedDN;
  }



  /**
   * Specifies the matched DN for this operation.  This may not be
   * called by post-response plugins.
   *
   * @param  matchedDN  The matched DN for this operation.
   */
  public final void setMatchedDN(DN matchedDN)
  {
    this.matchedDN = matchedDN;
  }



  /**
   * Retrieves the set of referral URLs for this operation.  Its
   * contents must not be altered by the caller.
   *
   * @return  The set of referral URLs for this operation, or
   *          {@code null} if the operation is not yet complete or
   *          does not have a set of referral URLs.
   */
  public final List<String> getReferralURLs()
  {
    return referralURLs;
  }



  /**
   * Specifies the set of referral URLs for this operation.  This may
   * not be called by post-response plugins.
   *
   * @param  referralURLs  The set of referral URLs for this
   *                       operation.
   */
  public final void setReferralURLs(List<String> referralURLs)
  {
    this.referralURLs = referralURLs;
  }



  /**
   * Sets the response elements for this operation based on the
   * information contained in the provided {@code DirectoryException}
   * object.  This method may not be called by post-response plugins.
   *
   * @param  directoryException  The exception containing the
   *                             information to use for the response
   *                             elements.
   */
  public final void setResponseData(
                         DirectoryException directoryException)
  {
    this.resultCode   = directoryException.getResultCode();
    this.matchedDN    = directoryException.getMatchedDN();
    this.referralURLs = directoryException.getReferralURLs();

    appendErrorMessage(directoryException.getMessageObject());
  }



  /**
   * Indicates whether this is an internal operation rather than one
   * that was requested by an external client.
   *
   * @return  {@code true} if this is an internal operation, or
   *          {@code false} if it is not.
   */
  public final boolean isInternalOperation()
  {
    return isInternalOperation;
  }



  /**
   * Specifies whether this is an internal operation rather than one
   * that was requested by an external client.  This may not be called
   * from within a plugin.
   *
   * @param  isInternalOperation  Specifies whether this is an
   *                              internal operation rather than one
   *                              that was requested by an external
   *                              client.
   */
  public final void setInternalOperation(boolean isInternalOperation)
  {
    this.isInternalOperation = isInternalOperation;
  }



  /**
   * Indicates whether this is a synchronization operation rather than
   * one that was requested by an external client.
   *
   * @return  {@code true} if this is a data synchronization
   *          operation, or {@code false} if it is not.
   */
  public final boolean isSynchronizationOperation()
  {
    return isSynchronizationOperation;
  }



  /**
   * Specifies whether this is a synchronization operation rather than
   * one that was requested by an external client.  This method may
   * not be called from within a plugin.
   *
   * @param  isSynchronizationOperation  Specifies whether this is a
   *                                     synchronization operation
   *                                     rather than one that was
   *                                     requested by an external
   *                                     client.
   */
  public final void setSynchronizationOperation(
                         boolean isSynchronizationOperation)
  {
    this.isSynchronizationOperation = isSynchronizationOperation;
  }



  /**
   * Indicates whether this operation needs to be synchronized to
   * other copies of the data.
   *
   * @return  {@code true} if this operation should not be
   *          synchronized, or {@code false} if it should be
   *          synchronized.
   */
  public boolean dontSynchronize()
  {
    return dontSynchronizeFlag;
  }



  /**
   * Specifies whether this operation must be synchronized to other
   * copies of the data.
   *
   * @param  dontSynchronize  Specifies whether this operation must be
   *                          synchronized to other copies
   *                          of the data.
   */
  public final void setDontSynchronize(boolean dontSynchronize)
  {
    this.dontSynchronizeFlag = dontSynchronize;
  }



  /**
   * Retrieves the entry for the user that should be considered the
   * authorization identity for this operation.  In many cases, it
   * will be the same as the authorization entry for the underlying
   * client connection, or {@code null} if no authentication has been
   * performed on that connection.  However, it may be some other
   * value if special processing has been requested (e.g., the
   * operation included a proxied authorization control).  This method
   * should not be called by pre-parse plugins because the correct
   * value may not yet have been determined.
   *
   * @return  The entry for the user that should be considered the
   *          authorization identity for this operation, or
   *          {@code null} if the authorization identity should be the
   *          unauthenticated  user.
   */
  public final Entry getAuthorizationEntry()
  {
    return authorizationEntry;
  }



  /**
   * Provides the entry for the user that should be considered the
   * authorization identity for this operation.  This must not be
   * called from within a plugin.
   *
   * @param  authorizationEntry  The entry for the user that should be
   *                             considered the authorization identity
   *                             for this operation, or {@code null}
   *                             if it should be the unauthenticated
   *                             user.
   */
  public final void setAuthorizationEntry(Entry authorizationEntry)
  {
    this.authorizationEntry = authorizationEntry;
  }



  /**
   * Retrieves the authorization DN for this operation.  In many
   * cases, it will be the same as the DN of the authenticated user
   * for the underlying connection, or the null DN if no
   * authentication has been performed on that connection.  However,
   * it may be some other value if special processing has been
   * requested (e.g., the operation included a proxied authorization
   * control).  This method should not be called by pre-parse plugins
   * because the correct value may not have yet been determined.
   *
   * @return  The authorization DN for this operation, or the null DN
   *          if it should be the unauthenticated user..
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
   * Retrieves the set of attachments defined for this operation, as a
   * mapping between the attachment name and the associated object.
   *
   * @return  The set of attachments defined for this operation.
   */
  public final Map<String,Object> getAttachments()
  {
    return attachments;
  }



  /**
   * Set the attachments to the operation.
   *
   * @param attachments - Attachments to register within the
   *                      operation
   */
  public final void setAttachments(Map<String, Object> attachments)
  {
    this.attachments = attachments;
  }



  /**
   * Retrieves the attachment with the specified name.
   *
   * @param  name  The name for the attachment to retrieve.  It will
   *               be treated in a case-sensitive manner.
   *
   * @return  The requested attachment object, or {@code null} if it
   *          does not exist.
   */
  public final Object getAttachment(String name)
  {
    return attachments.get(name);
  }



  /**
   * Removes the attachment with the specified name.
   *
   * @param  name  The name for the attachment to remove.  It will be
   *               treated in a case-sensitive manner.
   *
   * @return  The attachment that was removed, or {@code null} if it
   *          does not exist.
   */
  public final Object removeAttachment(String name)
  {
    return attachments.remove(name);
  }



  /**
   * Sets the value of the specified attachment.  If an attachment
   * already exists with the same name, it will be replaced.
   * Otherwise, a new attachment will be added.
   *
   * @param  name   The name to use for the attachment.
   * @param  value  The value to use for the attachment.
   *
   * @return  The former value held by the attachment with the given
   *          name, or {@code null} if there was previously no such
   *          attachment.
   */
  public final Object setAttachment(String name, Object value)
  {
    return attachments.put(name, value);
  }



  /**
   * Indicates that processing on this operation has completed
   * successfully and that the client should perform any associated
   * cleanup work.
   */
  public final void operationCompleted()
  {
    // Notify the client connection that this operation is complete
    // and that it no longer needs to be retained.
    clientConnection.removeOperationInProgress(messageID);
  }



  /**
   * Attempts to cancel this operation before processing has
   * completed.
   *
   * @param  cancelRequest  Information about the way in which the
   *                        operation should be canceled.
   *
   * @return  A code providing information on the result of the
   *          cancellation.
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    abort(cancelRequest);

    long stopWaitingTime = System.currentTimeMillis() + 5000;
    while ((cancelResult == null) &&
        (System.currentTimeMillis() < stopWaitingTime))
    {
      try
      {
        Thread.sleep(50);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
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



  /**
   * Attempts to cancel this operation before processing has
   * completed without waiting for a cancel result.
   *
   * @param  cancelRequest  Information about the way in which the
   *                        operation should be canceled.
   */
  public synchronized void abort(CancelRequest cancelRequest)
  {
    if(cancelResult == null && this.cancelRequest == null)
    {
      this.cancelRequest = cancelRequest;
    }
  }



  /**
   * {@inheritDoc}
   */
  public synchronized final void
    checkIfCanceled(boolean signalTooLate)
      throws CanceledOperationException {
    if(cancelRequest != null)
    {
      throw new CanceledOperationException(cancelRequest);
    }

    if(signalTooLate && cancelResult != null)
    {
      cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
    }
  }



  /**
   * {@inheritDoc}
   */
  public final CancelRequest getCancelRequest()
  {
    return cancelRequest;
  }

  /**
   * {@inheritDoc}
   */
  public final CancelResult getCancelResult()
  {
    return cancelResult;
  }



  /**
   * Retrieves a string representation of this operation.
   *
   * @return  A string representation of this operation.
   */
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this operation to the provided
   * buffer.
   *
   * @param  buffer  The buffer into which a string representation of
   *                 this operation should be appended.
   */
  public abstract void toString(StringBuilder buffer);



  /**
   * Retrieves the time that processing started for this operation.
   *
   * @return  The time that processing started for this operation.
   */
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



  /**
   * Retrieves the time that processing stopped for this operation.
   * This will actually hold a time immediately before the response
   * was sent to the client.
   *
   * @return  The time that processing stopped for this operation.
   */
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



  /**
   * Retrieves the length of time in milliseconds that
   * the server spent processing this operation.  This should not be
   * called until after the server has sent the response to the
   * client.
   *
   * @return  The length of time in milliseconds that
   *          the server spent processing this operation.
   */
  public final long getProcessingTime()
  {
    return (processingStopTime - processingStartTime);
  }



  /**
   * Retrieves the length of time in nanoseconds that
   * the server spent processing this operation if available.
   * This should not be called until after the server has sent the
   * response to the client.
   *
   * @return  The length of time in nanoseconds that the server
   *          spent processing this operation or -1 if its not
   *          available.
   */
  public final long getProcessingNanoTime()
  {
    if(useNanoTime)
    {
      return (processingStopNanoTime - processingStartNanoTime);
    }
    else
    {
      return -1;
    }
  }



  /**
   * Performs the work of actually processing this operation.  This
   * should include all processing for the operation, including
   * invoking pre-parse and post-response plugins, logging messages
   * and any other work that might need to be done in the course of
   * processing.
   */
  public abstract void run();



  /**
   * {@inheritDoc}
   */
  public final void registerPostResponseCallback(Runnable callback)
  {
    if (postResponseCallbacks == null)
    {
      postResponseCallbacks = new LinkedList<Runnable>();
    }
    postResponseCallbacks.add(callback);
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
  }
}

