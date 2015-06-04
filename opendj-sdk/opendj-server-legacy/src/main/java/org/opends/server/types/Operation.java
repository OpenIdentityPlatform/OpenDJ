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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.controls.ControlDecoder;

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
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface Operation extends Runnable
{
  /**
   * Identifier used to get the local operation [if any] in the
   * attachments.
   */
  String LOCALBACKENDOPERATIONS = "LocalBackendOperations";

  /**
   * Retrieves the operation type for this operation.
   *
   * @return  The operation type for this operation.
   */
  OperationType getOperationType();

  /**
   * Terminates the client connection being used to process this
   * operation.  If this is called by a plugin, then that plugin must
   * return a result indicating that the  client connection has been
   * terminated.
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
  void disconnectClient(DisconnectReason disconnectReason, boolean sendNotification, LocalizableMessage message);

  /**
   * Retrieves the client connection with which this operation is
   * associated.
   *
   * @return  The client connection with which this operation is
   *          associated.
   */
  ClientConnection getClientConnection();

  /**
   * Retrieves the unique identifier that is assigned to the client
   * connection that submitted this operation.
   *
   * @return  The unique identifier that is assigned to the client
   *          connection that submitted this operation.
   */
  long getConnectionID();

  /**
   * Retrieves the operation ID for this operation.
   *
   * @return  The operation ID for this operation.
   */
  long getOperationID();

  /**
   * Retrieves the message ID assigned to this operation.
   *
   * @return  The message ID assigned to this operation.
   */
  int getMessageID();

  /**
   * Retrieves the set of controls included in the request from the
   * client.  The returned list must not be altered.
   *
   * @return  The set of controls included in the request from the
   *          client.
   */
  List<Control> getRequestControls();

  /**
   * Retrieves a control included in the request from the client.
   *
   * @param <T>
   *          The type of control requested.
   * @param d
   *          The requested control's decoder.
   * @return The decoded form of the requested control included in the
   *         request from the client or <code>null</code> if the
   *         control was not found.
   * @throws DirectoryException
   *           if an error occurs while decoding the control.
   */
  <T extends Control> T getRequestControl(ControlDecoder<T> d) throws DirectoryException;

  /**
   * Adds the provided control to the set of request controls for this
   * operation.  This method may only be called by pre-parse plugins.
   *
   * @param  control  The control to add to the set of request
   *                  controls for this operation.
   */
  void addRequestControl(Control control);

  /**
   * Retrieves the set of controls to include in the response to the
   * client.  The contents of this list must not be altered.
   *
   * @return  The set of controls to include in the response to the
   *          client.
   */
  List<Control> getResponseControls();

  /**
   * Adds the provided control to the set of controls to include in
   * the response to the client.  This method may not be called by
   * post-response plugins.
   *
   * @param  control  The control to add to the set of controls to
   *                  include in the response to the client.
   */
  void addResponseControl(Control control);

  /**
   * Removes the provided control from the set of controls to include
   * in the response to the client.  This method may not be called by
   * post-response plugins.
   *
   * @param  control  The control to remove from the set of controls
   *                  to include in the response to the client.
   */
  void removeResponseControl(Control control);

  /**
   * Retrieves the result code for this operation.
   *
   * @return  The result code associated for this operation, or
   *          {@code UNDEFINED} if the operation has not yet
   *          completed.
   */
  ResultCode getResultCode();

  /**
   * Specifies the result code for this operation.  This method may
   * not be called by post-response plugins.
   *
   * @param  resultCode  The result code for this operation.
   */
  void setResultCode(ResultCode resultCode);

  /**
   * Retrieves the real, masked result code for this operation.
   *
   * @return The real, masked result code associated for this operation, or
   *         {@code UNDEFINED} if the operation has not yet completed.
   */
  ResultCode getMaskedResultCode();

  /**
   * Specifies the real, masked result code for this operation. This method may
   * not be called by post-response plugins.
   *
   * @param maskedResultCode
   *          The real, masked result code for this operation.
   */
  void setMaskedResultCode(ResultCode maskedResultCode);

  /**
   * Retrieves the error message for this operation.  Its contents may
   * be altered by pre-parse, pre-operation, and post-operation
   * plugins, but not by post-response plugins.
   *
   * @return  The error message for this operation.
   */
  LocalizableMessageBuilder getErrorMessage();

  /**
   * Specifies the error message for this operation.  This method may
   * not be called by post-response plugins.
   *
   * @param  errorMessage  The error message for this operation.
   */
  void setErrorMessage(LocalizableMessageBuilder errorMessage);

  /**
   * Appends the provided message to the error message buffer.  If the
   * buffer has not yet been created, then this will create it first
   * and then add the provided message.  This method may not be called
   * by post-response plugins.
   *
   * @param  message  The message to append to the error message
   */
  void appendErrorMessage(LocalizableMessage message);

  /**
   * Retrieves the real, masked error message for this operation. Its contents
   * may be altered by pre-parse, pre-operation, and post-operation plugins, but
   * not by post-response plugins.
   *
   * @return The real, masked error message for this operation.
   */
  LocalizableMessageBuilder getMaskedErrorMessage();

  /**
   * Specifies the real, masked error message for this operation. This method
   * may not be called by post-response plugins.
   *
   * @param maskedErrorMessage
   *          The real, masked error message for this operation.
   */
  void setMaskedErrorMessage(LocalizableMessageBuilder maskedErrorMessage);

  /**
   * Appends the provided message to the real, masked error message buffer. If
   * the buffer has not yet been created, then this will create it first and
   * then add the provided message. This method may not be called by
   * post-response plugins.
   *
   * @param maskedMessage
   *          The message to append to the real, masked error message
   */
  void appendMaskedErrorMessage(LocalizableMessage maskedMessage);

  /**
   * Returns an unmodifiable list containing the additional log items for this
   * operation, which should be written to the log but not included in the
   * response to the client.
   *
   * @return An unmodifiable list containing the additional log items for this
   *         operation.
   */
  List<AdditionalLogItem> getAdditionalLogItems();

  /**
   * Adds an additional log item to this operation, which should be written to
   * the log but not included in the response to the client. This method may not
   * be called by post-response plugins.
   *
   * @param item
   *          The additional log item for this operation.
   */
  void addAdditionalLogItem(AdditionalLogItem item);

  /**
   * Retrieves the matched DN for this operation.
   *
   * @return  The matched DN for this operation, or {@code null} if
   *          the operation has not yet completed or does not have a
   *          matched DN.
   */
  DN getMatchedDN();

  /**
   * Specifies the matched DN for this operation.  This may not be
   * called by post-response plugins.
   *
   * @param  matchedDN  The matched DN for this operation.
   */
  void setMatchedDN(DN matchedDN);

  /**
   * Retrieves the set of referral URLs for this operation.  Its
   * contents must not be altered by the caller.
   *
   * @return  The set of referral URLs for this operation, or
   *          {@code null} if the operation is not yet complete or
   *          does not have a set of referral URLs.
   */
  List<String> getReferralURLs();

  /**
   * Specifies the set of referral URLs for this operation.  This may
   * not be called by post-response plugins.
   *
   * @param  referralURLs  The set of referral URLs for this
   *                       operation.
   */
  void setReferralURLs(List<String> referralURLs);

  /**
   * Sets the response elements for this operation based on the
   * information contained in the provided {@code DirectoryException}
   * object.  This method may not be called by post-response plugins.
   *
   * @param  directoryException  The exception containing the
   *                             information to use for the response
   *                             elements.
   */
  void setResponseData(DirectoryException directoryException);

  /**
   * Indicates whether this is an internal operation rather than one
   * that was requested by an external client.
   *
   * @return  {@code true} if this is an internal operation, or
   *          {@code false} if it is not.
   */
  boolean isInternalOperation();

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
  void setInternalOperation(boolean isInternalOperation);

  /**
   * Indicates whether this is an inner operation rather than one that was
   * directly requested by an external client. Said otherwise, inner operations
   * include internal operations, but also operations in the server indirectly
   * mandated by external requests like Rest2LDAP for example. This may not be
   * called from within a plugin.
   *
   * @return {@code true} if this is an inner operation, or {@code false} if it
   *         is not.
   */
  boolean isInnerOperation();

  /**
   * Specifies whether this is an inner operation rather than one that was
   * directly requested by an external client. Said otherwise, inner operations
   * include internal operations, but also operations in the server indirectly
   * mandated by external requests like Rest2LDAP for example. This may not be
   * called from within a plugin.
   *
   * @param isInnerOperation
   *          Specifies whether this is an inner operation rather than one that
   *          was requested by an external client.
   */
  void setInnerOperation(boolean isInnerOperation);

  /**
   * Indicates whether this is a synchronization operation rather than
   * one that was requested by an external client.
   *
   * @return  {@code true} if this is a data synchronization
   *          operation, or {@code false} if it is not.
   */
  boolean isSynchronizationOperation();

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
  void setSynchronizationOperation(boolean isSynchronizationOperation);

  /**
   * Specifies whether this operation must be synchronized to other
   * copies of the data.
   *
   * @param  dontSynchronize  Specifies whether this operation must be
   *                          synchronized to other copies
   *                          of the data.
   */
  void setDontSynchronize(boolean dontSynchronize);

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
  Entry getAuthorizationEntry();

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
  void setAuthorizationEntry(Entry authorizationEntry);

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
  DN getAuthorizationDN();

  /**
   * Retrieves the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @return  The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  DN getProxiedAuthorizationDN();

  /**
   * Set the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @param proxiedAuthorizationDN
   *          The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  void setProxiedAuthorizationDN(DN proxiedAuthorizationDN);

  /**
   * Retrieves the set of attachments defined for this operation, as a
   * mapping between the attachment name and the associated object.
   *
   * @return  The set of attachments defined for this operation.
   */
  Map<String, Object> getAttachments();

  /**
   * Retrieves the attachment with the specified name.
   *
   * @param <T> the type of the attached object
   * @param  name  The name for the attachment to retrieve.  It will
   *               be treated in a case-sensitive manner.
   *
   * @return  The requested attachment object, or {@code null} if it
   *          does not exist.
   */
  <T> T getAttachment(String name);

  /**
   * Removes the attachment with the specified name.
   *
   * @param <T> the type of the attached object
   * @param  name  The name for the attachment to remove.  It will be
   *               treated in a case-sensitive manner.
   *
   * @return  The attachment that was removed, or {@code null} if it
   *          does not exist.
   */
  <T> T removeAttachment(String name);

  /**
   * Sets the value of the specified attachment.  If an attachment
   * already exists with the same name, it will be replaced.
   * Otherwise, a new attachment will be added.
   *
   * @param <T> the type of the attached object
   * @param  name   The name to use for the attachment.
   * @param  value  The value to use for the attachment.
   *
   * @return  The former value held by the attachment with the given
   *          name, or {@code null} if there was previously no such
   *          attachment.
   */
  <T> T setAttachment(String name, Object value);

  /**
   * Retrieves the time that processing started for this operation.
   *
   * @return  The time that processing started for this operation.
   */
  long getProcessingStartTime();

  /**
   * Retrieves the time that processing stopped for this operation.
   * This will actually hold a time immediately before the response
   * was sent to the client.
   *
   * @return  The time that processing stopped for this operation.
   */
  long getProcessingStopTime();

  /**
   * Retrieves the length of time in milliseconds that the server
   * spent processing this operation.  This should not be called until
   * after the server has sent the response to the client.
   *
   * @return  The length of time in milliseconds that the server spent
   *          processing this operation.
   */
  long getProcessingTime();

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
  long getProcessingNanoTime();

  /**
   * Indicates that processing on this operation has completed
   * successfully and that the client should perform any associated
   * cleanup work.
   */
  void operationCompleted();

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
  CancelResult cancel(CancelRequest cancelRequest);

  /**
   * Attempts to abort this operation before processing has
   * completed.
   *
   * @param  cancelRequest  Information about the way in which the
   *                        operation should be canceled.
   */
  void abort(CancelRequest cancelRequest);

  /**
   * Retrieves the cancel request that has been issued for this
   * operation, if there is one.  This method should not be called by
   * post-operation or post-response plugins.
   *
   * @return  The cancel request that has been issued for this
   *          operation, or {@code null} if there has not been any
   *          request to cancel.
   */
  CancelRequest getCancelRequest();

  /**
   * Retrieves the cancel result for this operation.
   *
   * @return  The cancel result for this operation.  It will be
   *          {@code null} if the operation has not seen and reacted
   *          to a cancel request.
   */
  CancelResult getCancelResult();

  /**
   * Retrieves a string representation of this operation.
   *
   * @return  A string representation of this operation.
   */
  @Override String toString();

  /**
   * Appends a string representation of this operation to the provided
   * buffer.
   *
   * @param  buffer  The buffer into which a string representation of
   *                 this operation should be appended.
   */
  void toString(StringBuilder buffer);

  /**
   * Indicates whether this operation needs to be synchronized to
   * other copies of the data.
   *
   * @return  {@code true} if this operation should not be
   *          synchronized, or {@code false} if it should be
   *          synchronized.
   */
  boolean dontSynchronize();

  /**
   * Set the attachments to the operation.
   *
   * @param attachments - Attachments to register within the
   *                      operation
   */
  void setAttachments(Map<String, Object> attachments);

  /**
   * Checks to see if this operation requested to cancel in which case
   * CanceledOperationException will be thrown.
   *
   * @param signalTooLate <code>true</code> to signal that any further
   *                      cancel requests will be too late after
   *                      return from this call or <code>false</code>
   *                      otherwise.
   *
   * @throws CanceledOperationException if this operation should
   * be cancelled.
   */
  void checkIfCanceled(boolean signalTooLate) throws CanceledOperationException;

  /**
   * Registers a callback which should be run once this operation has
   * completed and the response sent back to the client.
   *
   * @param callback
   *          The callback to be run once this operation has completed
   *          and the response sent back to the client.
   */
  void registerPostResponseCallback(Runnable callback);

  /**
   * Performs the work of actually processing this operation. This should
   * include all processing for the operation, including invoking pre-parse and
   * post-response plugins, logging messages and any other work that might need
   * to be done in the course of processing.
   */
  @Override
  void run();
}

