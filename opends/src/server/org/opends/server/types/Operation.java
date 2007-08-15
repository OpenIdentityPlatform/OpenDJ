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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;
import org.opends.messages.Message;



import java.util.List;
import java.util.Map;

import org.opends.server.api.ClientConnection;
import org.opends.messages.MessageBuilder;


/**
 * This interface defines a generic operation that may be processed by
 * the Directory Server.  Specific subclasses should implement
 * specific functionality appropriate for the type of operation.
 * <BR><BR>
 * Note that this class is not intended to be subclassed by any
 * third-party code outside of the OpenDS project.  It should only be
 * extended by the operation types included in the
 * {@code org.opends.server.core} package.
 */
public interface Operation
{
  /**
   * Identifier used to get the local operation [if any] in the
   * attachments.
   */
  public static final String LOCALBACKENDOPERATIONS =
    "LocalBackendOperations";

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
  public abstract void disconnectClient(
          DisconnectReason disconnectReason,
          boolean sendNotification, Message message
  );

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
  public abstract String[][] getCommonLogElements();

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
  public abstract ClientConnection getClientConnection();

  /**
   * Retrieves the unique identifier that is assigned to the client
   * connection that submitted this operation.
   *
   * @return  The unique identifier that is assigned to the client
   *          connection that submitted this operation.
   */
  public abstract long getConnectionID();

  /**
   * Retrieves the operation ID for this operation.
   *
   * @return  The operation ID for this operation.
   */
  public abstract long getOperationID();

  /**
   * Retrieves the message ID assigned to this operation.
   *
   * @return  The message ID assigned to this operation.
   */
  public abstract int getMessageID();

  /**
   * Retrieves the set of controls included in the request from the
   * client.  The returned list must not be altered.
   *
   * @return  The set of controls included in the request from the
   *          client.
   */
  public abstract List<Control> getRequestControls();

  /**
   * Adds the provided control to the set of request controls for this
   * operation.  This method may only be called by pre-parse plugins.
   *
   * @param  control  The control to add to the set of request
   *                  controls for this operation.
   */
  public abstract void addRequestControl(Control control);

  /**
   * Removes the provided control from the set of request controls for
   * this operation.  This method may only be called by pre-parse
   * plugins.
   *
   * @param  control  The control to remove from the set of request
   *                  controls for this operation.
   */
  public abstract void removeRequestControl(Control control);

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
  public abstract ResultCode getResultCode();

  /**
   * Specifies the result code for this operation.  This method may
   * not be called by post-response plugins.
   *
   * @param  resultCode  The result code for this operation.
   */
  public abstract void setResultCode(ResultCode resultCode);

  /**
   * Retrieves the error message for this operation.  Its contents may
   * be altered by pre-parse, pre-operation, and post-operation
   * plugins, but not by post-response plugins.
   *
   * @return  The error message for this operation.
   */
  public abstract MessageBuilder getErrorMessage();

  /**
   * Specifies the error message for this operation.  This method may
   * not be called by post-response plugins.
   *
   * @param  errorMessage  The error message for this operation.
   */
  public abstract void setErrorMessage(MessageBuilder errorMessage);

  /**
   * Appends the provided message to the error message buffer.  If the
   * buffer has not yet been created, then this will create it first
   * and then add the provided message.  This method may not be called
   * by post-response plugins.
   *
   * @param  message  The message to append to the error message
   */
  public abstract void appendErrorMessage(Message message);

  /**
   * Retrieves the additional log message for this operation, which
   * should be written to the log but not included in the response to
   * the client.  The contents of this buffer may be altered by
   * pre-parse, pre-operation, and post-operation plugins, but not by
   * post-response plugins.
   *
   * @return  The additional log message for this operation.
   */
  public abstract MessageBuilder getAdditionalLogMessage();

  /**
   * Specifies the additional log message for this operation, which
   * should be written to the log but not included in the response to
   * the client.  This method may not be called by post-response
   * plugins.
   *
   * @param  additionalLogMessage  The additional log message for this
   */
  public abstract void setAdditionalLogMessage(
      MessageBuilder additionalLogMessage);

  /**
   * Appends the provided message to the additional log information
   * for this operation.  This method may not be called by
   * post-response plugins.
   *
   * @param  message  The message that should be appended to the
   */
  public abstract void appendAdditionalLogMessage(Message message);

  /**
   * Retrieves the matched DN for this operation.
   *
   * @return  The matched DN for this operation, or {@code null} if
   *          the operation has not yet completed or does not have a
   *          matched DN.
   */
  public abstract DN getMatchedDN();

  /**
   * Specifies the matched DN for this operation.  This may not be
   * called by post-response plugins.
   *
   * @param  matchedDN  The matched DN for this operation.
   */
  public abstract void setMatchedDN(DN matchedDN);

  /**
   * Retrieves the set of referral URLs for this operation.  Its
   * contents must not be altered by the caller.
   *
   * @return  The set of referral URLs for this operation, or
   *          {@code null} if the operation is not yet complete or
   *          does not have a set of referral URLs.
   */
  public abstract List<String> getReferralURLs();

  /**
   * Specifies the set of referral URLs for this operation.  This may
   * not be called by post-response plugins.
   *
   * @param  referralURLs  The set of referral URLs for this
   *                       operation.
   */
  public abstract void setReferralURLs(List<String> referralURLs);

  /**
   * Sets the response elements for this operation based on the
   * information contained in the provided {@code DirectoryException}
   * object.  This method may not be called by post-response plugins.
   *
   * @param  directoryException  The exception containing the
   *                             information to use for the response
   *                             elements.
   */
  public abstract void setResponseData(
      DirectoryException directoryException);

  /**
   * Indicates whether this is an internal operation rather than one
   * that was requested by an external client.
   *
   * @return  {@code true} if this is an internal operation, or
   *          {@code false} if it is not.
   */
  public abstract boolean isInternalOperation();

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
  public abstract void setInternalOperation(boolean
      isInternalOperation);

  /**
   * Indicates whether this is a synchronization operation rather than
   * one that was requested by an external client.
   *
   * @return  {@code true} if this is a data synchronization
   *          operation, or {@code false} if it is not.
   */
  public abstract boolean isSynchronizationOperation();

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
  public abstract void setSynchronizationOperation(
      boolean isSynchronizationOperation);

  /**
   * Specifies whether this operation must be synchronized to other
   * copies of the data.
   *
   * @param  dontSynchronize  Specifies whether this operation must be
   *                          synchronized to other copies
   *                          of the data.
   */
  public abstract void setDontSynchronize(boolean dontSynchronize);

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
  public abstract Entry getAuthorizationEntry();

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
  public abstract void setAuthorizationEntry(Entry
      authorizationEntry);

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
  public abstract DN getAuthorizationDN();

  /**
   * Retrieves the set of attachments defined for this operation, as a
   * mapping between the attachment name and the associated object.
   *
   * @return  The set of attachments defined for this operation.
   */
  public abstract Map<String, Object> getAttachments();

  /**
   * Retrieves the attachment with the specified name.
   *
   * @param  name  The name for the attachment to retrieve.  It will
   *               be treated in a case-sensitive manner.
   *
   * @return  The requested attachment object, or {@code null} if it
   *          does not exist.
   */
  public abstract Object getAttachment(String name);

  /**
   * Removes the attachment with the specified name.
   *
   * @param  name  The name for the attachment to remove.  It will be
   *               treated in a case-sensitive manner.
   *
   * @return  The attachment that was removed, or {@code null} if it
   *          does not exist.
   */
  public abstract Object removeAttachment(String name);

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
  public abstract Object setAttachment(String name, Object value);

  /**
   * Retrieves the time that processing started for this operation.
   *
   * @return  The time that processing started for this operation.
   */
  public abstract long getProcessingStartTime();

  /**
   * Retrieves the time that processing stopped for this operation.
   * This will actually hold a time immediately before the response
   * was sent to the client.
   *
   * @return  The time that processing stopped for this operation.
   */
  public abstract long getProcessingStopTime();

  /**
   * Retrieves the length of time in milliseconds that the server
   * spent processing this operation.  This should not be called until
   * after the server has sent the response to the client.
   *
   * @return  The length of time in milliseconds that the server spent
   *          processing this operation.
   */
  public abstract long getProcessingTime();

  /**
   * Indicates that processing on this operation has completed
   * successfully and that the client should perform any associated
   * cleanup work.
   */
  public abstract void operationCompleted();

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
  public abstract CancelResult cancel(CancelRequest cancelRequest);

  /**
   * Sets the cancel request for this operation, if applicable.  This
   * should only be used for testing purposes (e.g., for ensuring a
   * cancel request is submitted before processing begins on an
   * operation, or to allow for cancelling an internal operation).  It
   * must not be used for any other purpose.
   *
   * @param  cancelRequest  The cancel request to set for this
   *                        operation.
   *
   * @return  {@code true} if the cancel request was set, or
   *          {@code false} if it was not for some reason (e.g., the
   *          specified operation cannot be cancelled).
   */
  public abstract boolean setCancelRequest(CancelRequest
      cancelRequest);

  /**
   * Retrieves the cancel request that has been issued for this
   * operation, if there is one.  This method should not be called by
   * post-operation or post-response plugins.
   *
   * @return  The cancel request that has been issued for this
   *          operation, or {@code null} if there has not been any
   *          request to cancel.
   */
  public abstract CancelRequest getCancelRequest();

  /**
   * Retrieves the cancel result for this operation.
   *
   * @return  The cancel result for this operation.  It will be
   *          {@code null} if the operation has not seen and reacted
   *          to a cancel request.
   */
  public abstract CancelResult getCancelResult();

  /**
   * Specifies the cancel result for this operation.
   *
   * @param  cancelResult  The cancel result for this operation.
   */
  public abstract void setCancelResult(CancelResult cancelResult);

  /**
   * Indicates that this operation has been cancelled.  If
   * appropriate, it will send a response to the client to indicate
   * that.  This method must not be called by abandon, bind, or unbind
   * operations under any circumstances, nor by extended operations if
   * the request OID is that of the cancel or the StartTLS operation.
   *
   * @param  cancelRequest  The request to cancel this operation.
   */
  public abstract void indicateCancelled(CancelRequest cancelRequest);

  /**
   * Retrieves a string representation of this operation.
   *
   * @return  A string representation of this operation.
   */
  public abstract String toString();

  /**
   * Appends a string representation of this operation to the provided
   * buffer.
   *
   * @param  buffer  The buffer into which a string representation of
   *                 this operation should be appended.
   */
  public abstract void toString(StringBuilder buffer);

  /**
   * Indicates whether this operation needs to be synchronized to
   * other copies of the data.
   *
   * @return  <CODE>true</CODE> if this operation don't need to be
   *                            synchronized, or
   *          <CODE>false</CODE> if it needs to be synchronized.
   */
  public abstract boolean dontSynchronize();

  /**
   * Set the time at which the processing stopped for this operation.
   * This will actually hold a time immediately before the response
   * was sent to the client.
   */
  public abstract void setProcessingStopTime();

  /**
   * Set the time at which the processing started for this operation.
   */
  public abstract void setProcessingStartTime();

  /**
   * Set the attachments to the operation.
   *
   * @param attachments - Attachments to register within the
   *                      operation
   */
  public abstract void setAttachments(Map<String,
      Object> attachments);

}

