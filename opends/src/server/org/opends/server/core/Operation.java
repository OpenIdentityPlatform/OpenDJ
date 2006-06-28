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
package org.opends.server.core;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opends.server.api.ClientConnection;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.Debug.*;




/**
 * This class defines a generic operation that may be processed by the Directory
 * Server.  Specific subclasses should implement specific functionality
 * appropriate for the type of operation.
 */
public abstract class Operation
       implements Runnable
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.core.Operation";



  /**
   * The set of response controls that will always be returned for an abandon
   * operation.
   */
  protected static final List<Control> NO_RESPONSE_CONTROLS =
       new ArrayList<Control>(0);



  /**
   * The client connection with which this operation is associated.
   */
  protected ClientConnection clientConnection;



  /**
   * The message ID for this operation.
   */
  protected int messageID;



  /**
   * The operation ID for this operation.
   */
  protected long operationID;



  // Indicates whether this is an internal operation triggered within the server
  // itself rather than requested by an external client.
  private boolean isInternalOperation;

  // Indicates whether this operation is involved in data synchronization
  // processing.
  private boolean isSynchronizationOperation;

  // The cancel result for this operation.
  private CancelResult cancelResult;

  // The authorization DN for this operation.
  private DN authorizationDN;

  // The matched DN for this operation.
  private DN matchedDN;

  // A set of attachments associated with this operation that might be used by
  // various components during its processing.
  private Map<String,Object> attachments;

  // The set of controls included in the request from the client.
  private List<Control> requestControls;

  // The set of referral URLs for this operation.
  private List<String> referralURLs;

  // The result code for this operation.
  private ResultCode resultCode;

  // The error message for this operation.
  private StringBuilder errorMessage;



  /**
   * Creates a new operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The identifier assigned to this operation for
   *                           the client connection.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   */
  protected Operation(ClientConnection clientConnection, long operationID,
                      int messageID, List<Control> requestControls)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(clientConnection),
                            String.valueOf(messageID),
                            String.valueOf(requestControls));

    this.clientConnection = clientConnection;
    this.operationID      = operationID;
    this.messageID        = messageID;
    this.requestControls  = requestControls;

    resultCode                 = ResultCode.UNDEFINED;
    errorMessage               = new StringBuilder();
    attachments                = new HashMap<String,Object>();
    matchedDN                  = null;
    referralURLs               = null;
    cancelResult               = null;
    isInternalOperation        = false;
    isSynchronizationOperation = false;
    authorizationDN =
         clientConnection.getAuthenticationInfo().getAuthorizationDN();
  }



  /**
   * Retrieves the operation type for this operation.
   *
   * @return  The operation type for this operation.
   */
  public abstract OperationType getOperationType();



  /**
   * Retrieves a set of standard elements that should be logged in all requests
   * and responses for all types of operations.  Each element in the array will
   * itself be a two-element array in which the first element is the name of the
   * field and the second is a string representation of the value, or
   * <CODE>null</CODE> if there is no value for that field.
   *
   * @return  A standard set of elements that should be logged in requests and
   *          responses for all types of operations.
   */
  public String[][] getCommonLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return new String[][]
    {
      new String[] { LOG_ELEMENT_CONNECTION_ID,
                     String.valueOf(getConnectionID()) },
      new String[] { LOG_ELEMENT_OPERATION_ID, String.valueOf(operationID) },
      new String[] { LOG_ELEMENT_MESSAGE_ID, String.valueOf(messageID) }
    };
  }



  /**
   * Retrieves a standard set of elements that should be logged in requests for
   * this type of operation.  Each element in the array will itself be a
   * two-element array in which the first element is the name of the field and
   * the second is a string representation of the value, or <CODE>null</CODE> if
   * there is no value for that field.
   *
   * @return  A standard set of elements that should be logged in requests for
   *          this type of operation.
   */
  public abstract String[][] getRequestLogElements();



  /**
   * Retrieves a standard set of elements that should be logged in responses for
   * this type of operation.  Each element in the array will itself be a
   * two-element array in which the first element is the name of the field and
   * the second is a string representation of the value, or <CODE>null</CODE> if
   * there is no value for that field.
   *
   * @return  A standard set of elements that should be logged in responses for
   *          this type of operation.
   */
  public abstract String[][] getResponseLogElements();




  /**
   * Retrieves the client connection with which this operation is associated.
   *
   * @return  The client connection with which this operation is associated.
   */
  public final ClientConnection getClientConnection()
  {
    assert debugEnter(CLASS_NAME, "getClientConnection");

    return clientConnection;
  }



  /**
   * Retrieves the unique identifier that is assigned to the client connection
   * that submitted this operation.
   *
   * @return  The unique identifier that is assigned to the client connection
   *          that submitted this operation.
   */
  public final long getConnectionID()
  {
    assert debugEnter(CLASS_NAME, "getConnectionID");

    return clientConnection.getConnectionID();
  }



  /**
   * Retrieves the operation ID for this operation.
   *
   * @return  The operation ID for this operation.
   */
  public final long getOperationID()
  {
    assert debugEnter(CLASS_NAME, "getOperationID");

    return operationID;
  }



  /**
   * Retrieves the message ID assigned to this operation.
   *
   * @return  The message ID assigned to this operation.
   */
  public final int getMessageID()
  {
    assert debugEnter(CLASS_NAME, "getMessageID");

    return messageID;
  }



  /**
   * Retrieves the set of controls included in the request from the client.
   * Note that it is only acceptable for the caller to alter the contents of the
   * returned list in pre-parse plugins.
   *
   * @return  The set of controls included in the request from the client.
   */
  public List<Control> getRequestControls()
  {
    assert debugEnter(CLASS_NAME, "getRequestControls");

    return requestControls;
  }



  /**
   * Retrieves the set of controls to include in the response to the client.
   * Note that the contents of this list should not be altered after
   * post-operation plugins have been called.
   *
   * @return  The set of controls to include in the response to the client.
   */
  public abstract List<Control> getResponseControls();



  /**
   * Retrieves the result code for this operation.
   *
   * @return  The result code associated for this operation, or
   *          <CODE>null</CODE> if the operation has not yet completed.
   */
  public ResultCode getResultCode()
  {
    assert debugEnter(CLASS_NAME, "getResultCode");

    return resultCode;
  }



  /**
   * Specifies the result code for this operation.
   *
   * @param  resultCode  The result code for this operation.
   */
  public void setResultCode(ResultCode resultCode)
  {
    assert debugEnter(CLASS_NAME, "setResultCode", String.valueOf(resultCode));

    this.resultCode = resultCode;
  }



  /**
   * Retrieves the error message for this operation.  If it is non-null, then
   * its contents may be altered by the caller.
   *
   * @return  The error message for this operation, or <CODE>null</CODE> if the
   *          operation has not yet completed or does not have an error message.
   */
  public StringBuilder getErrorMessage()
  {
    assert debugEnter(CLASS_NAME, "getErrorMessage");

    return errorMessage;
  }



  /**
   * Specifies the error message for this operation.
   *
   * @param  errorMessage  The error message for this operation.
   */
  public void setErrorMessage(StringBuilder errorMessage)
  {
    assert debugEnter(CLASS_NAME, "setErrorMessage",
                      String.valueOf(errorMessage));

    this.errorMessage = errorMessage;
  }



  /**
   * Appends the provided message to the error message buffer.  If the buffer
   * has not yet been created, then this will create it first and then add the
   * provided message.
   *
   * @param  message  The message to append to the error message buffer.
   */
  public void appendErrorMessage(String message)
  {
    assert debugEnter(CLASS_NAME, "appendErrorMessage",
                      String.valueOf(message));

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
   * Retrieves the matched DN for this operation.
   *
   * @return  The matched DN for this operation, or <CODE>null</CODE> if the
   *          operation has not yet completed or does not have a matched DN.
   */
  public DN getMatchedDN()
  {
    assert debugEnter(CLASS_NAME, "getMatchedDN");

    return matchedDN;
  }



  /**
   * Specifies the matched DN for this operation.
   *
   * @param  matchedDN  The matched DN for this operation.
   */
  public void setMatchedDN(DN matchedDN)
  {
    assert debugEnter(CLASS_NAME, "setMatchedDN", String.valueOf(matchedDN));

    this.matchedDN = matchedDN;
  }



  /**
   * Retrieves the set of referral URLs for this operation.  If it is non-null
   * then its contents may be altered by the caller.
   *
   * @return  The set of referral URLs for this operation, or <CODE>null</CODE>
   *          if the operation is not yet complete or does not have a set of
   *          referral URLs.
   */
  public List<String> getReferralURLs()
  {
    assert debugEnter(CLASS_NAME, "getReferralURLs");

    return referralURLs;
  }



  /**
   * Specifies the set of referral URLs for this operation.
   *
   * @param  referralURLs  The set of referral URLs for this operation.
   */
  public void setReferralURLs(List<String> referralURLs)
  {
    assert debugEnter(CLASS_NAME, "setReferralURLs",
                      String.valueOf(referralURLs));

    this.referralURLs = referralURLs;
  }



  /**
   * Sets the response elements for this operation based on the information
   * contained in the provided <CODE>DirectoryException</CODE> object.
   *
   * @param  directoryException  The exception containing the information to use
   *                             for the response elements.
   */
  public void setResponseData(DirectoryException directoryException)
  {
    assert debugEnter(CLASS_NAME, "setResponseData");

    this.resultCode   = directoryException.getResultCode();
    this.matchedDN    = directoryException.getMatchedDN();
    this.referralURLs = directoryException.getReferralURLs();

    appendErrorMessage(directoryException.getErrorMessage());
  }



  /**
   * Indicates whether this is an internal operation rather than one that was
   * requested by an external client.
   *
   * @return  <CODE>true</CODE> if this is an internal operation, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean isInternalOperation()
  {
    assert debugEnter(CLASS_NAME, "isInternalOperation");

    return isInternalOperation;
  }



  /**
   * Specifies whether this is an internal operation rather than one that was
   * requested by an external client.
   *
   * @param  isInternalOperation  Specifies whether this is an internal
   *                              operation rather than one that was requested
   *                              by an external client.
   */
  public void setInternalOperation(boolean isInternalOperation)
  {
    assert debugEnter(CLASS_NAME, "setInternalOperation",
                      String.valueOf(isInternalOperation));

    this.isInternalOperation = isInternalOperation;
  }



  /**
   * Indicates whether this is a synchronization operation rather than one that
   * was requested by an external client.
   *
   * @return  <CODE>true</CODE> if this is a data synchronization operation, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean isSynchronizationOperation()
  {
    assert debugEnter(CLASS_NAME, "isSynchronizationOperation");

    return isSynchronizationOperation;
  }



  /**
   * Specifies whether this is a synchronization operation rather than one that
   * was requested by an external client.
   *
   * @param  isSynchronizationOperation  Specifies whether this is a
   *                                     synchronization operation rather than
   *                                     one that was requested by an external
   *                                     client.
   */
  public void setSynchronizationOperation(boolean isSynchronizationOperation)
  {
    assert debugEnter(CLASS_NAME, "setSynchronizationOperation",
                      String.valueOf(isSynchronizationOperation));

    this.isSynchronizationOperation = isSynchronizationOperation;
  }



  /**
   * Retrieves the authorization DN for this operation.  In many cases, it will
   * be the same as the DN of the authenticated user for the underlying
   * connection, or the null DN if no authentication has been performed on that
   * connection.  However, it may be some other value if special processing has
   * been requested (e.g., the operation included a proxied authorization
   * control).
   *
   * @return  The authorization DN for this operation.
   */
  public DN getAuthorizationDN()
  {
    assert debugEnter(CLASS_NAME, "getAuthorizationDN");

    if (authorizationDN == null)
    {
      AuthenticationInfo authInfo = clientConnection.getAuthenticationInfo();
      if (authInfo == null)
      {
        return new DN(new RDN[0]);
      }
      else
      {
        return authInfo.getAuthorizationDN();
      }
    }
    else
    {
      return authorizationDN;
    }
  }



  /**
   * Specifies the authorization DN for this operation.
   *
   * @param  authorizationDN  The authorization DN for this operation, or
   *                          <CODE>null</CODE> if it should use the DN of the
   *                          authenticated user.
   */
  public void setAuthorizationDN(DN authorizationDN)
  {
    assert debugEnter(CLASS_NAME, "setAuthorizationDN",
                      String.valueOf(authorizationDN));

    this.authorizationDN = authorizationDN;
  }



  /**
   * Retrieves the set of attachments defined for this operation, as a mapping
   * between the attachment name and the associated object.
   *
   * @return  The set of attachments defined for this operation.
   */
  public Map<String,Object> getAttachments()
  {
    assert debugEnter(CLASS_NAME, "getAttachments");

    return attachments;
  }



  /**
   * Retrieves the attachment with the specified name.
   *
   * @param  name  The name for the attachment to retrieve.  It will be treated
   *               in a case-sensitive manner.
   *
   * @return  The requested attachment object, or <CODE>null</CODE> if it does
   *          not exist.
   */
  public Object getAttachment(String name)
  {
    assert debugEnter(CLASS_NAME, "getAttachment", String.valueOf(name));

    return attachments.get(name);
  }



  /**
   * Removes the attachment with the specified name.
   *
   * @param  name  The name for the attachment to remove.  It will be treated in
   *               a case-sensitive manner.
   *
   * @return  The attachment that was removed, or <CODE>null</CODE> if it does
   *          not exist.
   */
  public Object removeAttachment(String name)
  {
    assert debugEnter(CLASS_NAME, "removeAttachment", String.valueOf(name));

    return attachments.remove(name);
  }



  /**
   * Sets the value of the specified attachment.  If an attachment already
   * exists with the same name, it will be replaced.  Otherwise, a new
   * attachment will be added.
   *
   * @param  name   The name to use for the attachment.
   * @param  value  The value to use for the attachment.
   *
   * @return  The former value held by the attachment with the given name, or
   *          <CODE>null</CODE> if there was previously no such attachment.
   */
  public Object setAttachment(String name, Object value)
  {
    assert debugEnter(CLASS_NAME, "putAttachment", String.valueOf(name),
                      String.valueOf(value));

    return attachments.put(name, value);
  }



  /**
   * Performs the work of actually processing this operation.  This should
   * include all processing for the operation, including invoking plugins,
   * logging messages, performing access control, managing synchronization, and
   * any other work that might need to be done in the course of processing.
   */
  public abstract void run();



  /**
   * Indicates that processing on this operation has completed successfully and
   * that the client should perform any associated cleanup work.
   */
  public void operationCompleted()
  {
    assert debugEnter(CLASS_NAME, "operationCompleted");


    // Notify the client connection that this operation is complete and that it
    // no longer needs to be retained.
    clientConnection.removeOperationInProgress(messageID);
  }



  /**
   * Attempts to cancel this operation before processing has completed.
   *
   * @param  cancelRequest  Information about the way in which the operation
   *                        should be canceled.
   *
   * @return  A code providing information on the result of the cancellation.
   */
  public abstract CancelResult cancel(CancelRequest cancelRequest);



  /**
   * Retrieves the cancel request that has been issued for this operation, if
   * there is one.
   *
   * @return  The cancel request that has been issued for this operation, or
   *          <CODE>null</CODE> if there has not been any request to cancel.
   */
  public abstract CancelRequest getCancelRequest();



  /**
   * Retrieves the cancel result for this operation.
   *
   * @return  The cancel result for this operation.  It will be
   *          <CODE>null</CODE> if the operation has not seen and reacted to a
   *          cancel request.
   */
  public CancelResult getCancelResult()
  {
    assert debugEnter(CLASS_NAME, "getCancelResult");

    return cancelResult;
  }



  /**
   * Specifies the cancel result for this operation.
   *
   * @param  cancelResult  The cancel result for this operation.
   */
  public void setCancelResult(CancelResult cancelResult)
  {
    assert debugEnter(CLASS_NAME, "setCancelResult",
                      String.valueOf(cancelResult));

    this.cancelResult = cancelResult;
  }



  /**
   * Retrieves a string representation of this operation.
   *
   * @return  A string representation of this operation.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this operation to the provided buffer.
   *
   * @param  buffer  The buffer into which a string representation of this
   *                 operation should be appended.
   */
  public abstract void toString(StringBuilder buffer);
}

