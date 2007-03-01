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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types.operation;



import java.util.List;
import java.util.Map;

import org.opends.server.api.ClientConnection;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.Control;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.OperationType;



/**
 * This class defines a set of methods that are available for use by
 * all types of plugins involved in operation processing (pre-parse,
 * pre-operation, post-operation, post-response, search result entry,
 * search result reference, and intermediate response).  Note that
 * this interface is intended only to define an API for use by plugins
 * and is not intended to be implemented by any custom classes.
 */
public interface PluginOperation
{
  /**
   * Retrieves the operation type for this operation.
   *
   * @return  The operation type for this operation.
   */
  public OperationType getOperationType();



  /**
   * Retrieves the client connection with which this operation is
   * associated.
   *
   * @return  The client connection with which this operation is
   *          associated.
   */
  public ClientConnection getClientConnection();



  /**
   * Terminates the client connection being used to process this
   * operation.  The plugin must return a result indicating that the
   * client connection has been teriminated.
   *
   * @param  disconnectReason  The disconnect reason that provides the
   *                           generic cause for the disconnect.
   * @param  sendNotification  Indicates whether to try to provide
   *                           notification to the client that the
   *                           connection will be closed.
   * @param  message           The message to send to the client.  It
   *                           may be <CODE>null</CODE> if no
   *                           notification is to be sent.
   * @param  messageID         The unique identifier associated with
   *                           the message to send to the client.  It
   *                           may be -1 if no notification is to be
   *                           sent.
   */
  public void disconnectClient(DisconnectReason disconnectReason,
                               boolean sendNotification,
                               String message, int messageID);



  /**
   * Retrieves the unique identifier that is assigned to the client
   * connection that submitted this operation.
   *
   * @return  The unique identifier that is assigned to the client
   *          connection that submitted this operation.
   */
  public long getConnectionID();



  /**
   * Retrieves the operation ID for this operation.
   *
   * @return  The operation ID for this operation.
   */
  public long getOperationID();



  /**
   * Retrieves the message ID assigned to this operation.
   *
   * @return  The message ID assigned to this operation.
   */
  public int getMessageID();



  /**
   * Retrieves the set of controls included in the request from the
   * client.  The contents of this list must not be altered.
   *
   * @return  The set of controls included in the request from the
   *          client.
   */
  public List<Control> getRequestControls();



  /**
   * Retrieves the set of controls to include in the response to the
   * client.  The contents of this list must not be altered.
   *
   * @return  The set of controls to include in the response to the
   *          client.
   */
  public List<Control> getResponseControls();



  /**
   * Indicates whether this is an internal operation rather than one
   * that was requested by an external client.
   *
   * @return  <CODE>true</CODE> if this is an internal operation, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean isInternalOperation();



  /**
   * Indicates whether this is a synchronization operation rather than
   * one that was requested by an external client.
   *
   * @return  <CODE>true</CODE> if this is a data synchronization
   *          operation, or <CODE>false</CODE> if it is not.
   */
  public boolean isSynchronizationOperation();



  /**
   * Retrieves the set of attachments defined for this operation, as a
   * mapping between the attachment name and the associated object.
   *
   * @return  The set of attachments defined for this operation.
   */
  public Map<String,Object> getAttachments();



  /**
   * Retrieves the attachment with the specified name.
   *
   * @param  name  The name for the attachment to retrieve.  It will
   *               be treated in a case-sensitive manner.
   *
   * @return  The requested attachment object, or <CODE>null</CODE> if
   *          it does not exist.
   */
  public Object getAttachment(String name);



  /**
   * Removes the attachment with the specified name.
   *
   * @param  name  The name for the attachment to remove.  It will be
   *               treated in a case-sensitive manner.
   *
   * @return  The attachment that was removed, or <CODE>null</CODE> if
   *          it does not exist.
   */
  public Object removeAttachment(String name);



  /**
   * Sets the value of the specified attachment.  If an attachment
   * already exists with the same name, it will be replaced.
   * Otherwise, a new attachment will be added.
   *
   * @param  name   The name to use for the attachment.
   * @param  value  The value to use for the attachment.
   *
   * @return  The former value held by the attachment with the given
   *          name, or <CODE>null</CODE> if there was previously no
   *          such attachment.
   */
  public Object setAttachment(String name, Object value);



  /**
   * Retrieves the time that processing started for this operation.
   *
   * @return  The time that processing started for this operation.
   */
  public long getProcessingStartTime();



  /**
   * Retrieves the cancel request that has been issued for this
   * operation, if there is one.
   *
   * @return  The cancel request that has been issued for this
   *          operation, or <CODE>null</CODE> if there has not been
   *          any request to cancel.
   */
  public CancelRequest getCancelRequest();



  /**
   * Retrieves a string representation of this operation.
   *
   * @return  A string representation of this operation.
   */
  public String toString();



  /**
   * Appends a string representation of this operation to the provided
   * buffer.
   *
   * @param  buffer  The buffer into which a string representation of
   *                 this operation should be appended.
   */
  public void toString(StringBuilder buffer);
}

