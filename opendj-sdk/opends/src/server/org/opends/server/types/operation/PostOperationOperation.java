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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types.operation;



import java.util.List;
import java.util.Map;

import org.opends.server.api.ClientConnection;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;



/**
 * This class defines a set of methods that are available for use by
 * post-operation plugins for all types of operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
public interface PostOperationOperation
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
   * Adds the provided control to the set of controls to include in
   * the response to the client.
   *
   * @param  control  The control to add to the set of controls to
   *                  include in the response to the client.
   */
  public void addResponseControl(Control control);



  /**
   * Removes the provided control from the set of controls to include
   * in the response to the client.
   *
   * @param  control  The control to remove from the set of controls
   *                  to include in the response to the client.
   */
  public void removeResponseControl(Control control);



  /**
   * Retrieves the result code for this operation.
   *
   * @return  The result code associated for this operation, or
   *          <CODE>UNDEFINED</CODE> if the operation has not yet
   *          completed.
   */
  public ResultCode getResultCode();



  /**
   * Specifies the result code for this operation.
   *
   * @param  resultCode  The result code for this operation.
   */
  public void setResultCode(ResultCode resultCode);



  /**
   * Retrieves the error message for this operation.  Its contents may
   * be altered by the caller.
   *
   * @return  The error message for this operation.
   */
  public StringBuilder getErrorMessage();



  /**
   * Specifies the error message for this operation.
   *
   * @param  errorMessage  The error message for this operation.
   */
  public void setErrorMessage(StringBuilder errorMessage);



  /**
   * Appends the provided message to the error message buffer.  If the
   * buffer has not yet been created, then this will create it first
   * and then add the provided message.
   *
   * @param  message  The message to append to the error message
   *                  buffer.
   */
  public void appendErrorMessage(String message);



  /**
   * Retrieves the additional log message for this operation, which
   * should be written to the log but not included in the response to
   * the client.  The contents of this buffer may be altered by the
   * caller.
   *
   * @return  The additional log message for this operation.
   */
  public StringBuilder getAdditionalLogMessage();



  /**
   * Specifies the additional log message for this operation, which
   * should be written to the log but not included in the response to
   * the client.
   *
   * @param  additionalLogMessage  The additional log message for this
   *                               operation.
   */
  public void setAdditionalLogMessage(
                   StringBuilder additionalLogMessage);



  /**
   * Appends the provided message to the additional log information
   * for this operation.
   *
   * @param  message  The message that should be appended to the
   *                  additional log information for this operation.
   */
  public void appendAdditionalLogMessage(String message);



  /**
   * Retrieves the matched DN for this operation.
   *
   * @return  The matched DN for this operation, or <CODE>null</CODE>
   *          if the operation has not yet completed or does not have
   *          a matched DN.
   */
  public DN getMatchedDN();



  /**
   * Specifies the matched DN for this operation.
   *
   * @param  matchedDN  The matched DN for this operation.
   */
  public void setMatchedDN(DN matchedDN);



  /**
   * Retrieves the set of referral URLs for this operation.  Its
   * contents must not be altered by the caller.
   *
   * @return  The set of referral URLs for this operation, or
   *          <CODE>null</CODE> if the operation is not yet complete
   *          or does not have a set of referral URLs.
   */
  public List<String> getReferralURLs();



  /**
   * Specifies the set of referral URLs for this operation.
   *
   * @param  referralURLs  The set of referral URLs for this
   *                       operation.
   */
  public void setReferralURLs(List<String> referralURLs);



  /**
   * Sets the response elements for this operation based on the
   * information contained in the provided
   * <CODE>DirectoryException</CODE> object.
   *
   * @param  directoryException  The exception containing the
   *                             information to use for the response
   *                             elements.
   */
  public void setResponseData(DirectoryException directoryException);



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
   * Retrieves the authorization DN for this operation.  In many
   * cases, it will be the same as the DN of the authenticated user
   * for the underlying connection, or the null DN if no
   * authentication has been performed on that connection.  However,
   * it may be some other value if special processing has been
   * requested (e.g., the operation included a proxied authorization
   * control).
   *
   * @return  The authorization DN for this operation.
   */
  public DN getAuthorizationDN();



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

