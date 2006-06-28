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



import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.types.Control;
import org.opends.server.types.ResultCode;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;




/**
 * This class defines an operation that may be used to abandon an operation that
 * may already be in progress in the Directory Server.
 */
public class AbandonOperation
       extends Operation
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.AbandonOperation";



  // The message ID of the operation that should be abandoned.
  private int idToAbandon;

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;



  /**
   * Creates a new abandon operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  idToAbandon       The message ID of the operation that should be
   *                           abandoned.
   */
  public AbandonOperation(ClientConnection clientConnection, long operationID,
                          int messageID, List<Control> requestControls,
                          int idToAbandon)
  {
    super(clientConnection, operationID, messageID, requestControls);

    assert debugConstructor(CLASS_NAME, String.valueOf(clientConnection),
                            String.valueOf(operationID),
                            String.valueOf(messageID),
                            String.valueOf(requestControls),
                            String.valueOf(idToAbandon));

    this.idToAbandon = idToAbandon;
  }



  /**
   * Retrieves the message ID of the operation that should be abandoned.
   *
   * @return  The message ID of the operation that should be abandoned.
   */
  public int getIDToAbandon()
  {
    assert debugEnter(CLASS_NAME, "getIDToAbandon");

    return idToAbandon;
  }



  /**
   * Retrieves the time that processing started for this operation.
   *
   * @return  The time that processing started for this operation.
   */
  public long getProcessingStartTime()
  {
    assert debugEnter(CLASS_NAME, "getProcessingStartTime");

    return processingStartTime;
  }



  /**
   * Retrieves the time that processing stopped for this operation.  This will
   * actually hold a time immediately before the result was logged.
   *
   * @return  The time that processing stopped for this operation.
   */
  public long getProcessingStopTime()
  {
    assert debugEnter(CLASS_NAME, "getProcessingStopTime");

    return processingStopTime;
  }



  /**
   * Retrieves the length of time in milliseconds that the server spent
   * processing this operation.  This should not be called until after the
   * server has sent the response to the client.
   *
   * @return  The length of time in milliseconds that the server spent
   *          processing this operation.
   */
  public long getProcessingTime()
  {
    assert debugEnter(CLASS_NAME, "getProcessingTime");

    return (processingStopTime - processingStartTime);
  }



  /**
   * Retrieves the operation type for this operation.
   *
   * @return  The operation type for this operation.
   */
  public OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.ABANDON;
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
  public String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return new String[][]
    {
      new String[] { LOG_ELEMENT_ID_TO_ABANDON, String.valueOf(idToAbandon) }
    };
  }



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
  public String[][] getResponseLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    // There is no response for an abandon.  However, we will still want to log
    // information about whether it was successful.
    String resultCode = String.valueOf(getResultCode().getIntValue());

    String errorMessage;
    StringBuilder errorMessageBuffer = getErrorMessage();
    if (errorMessageBuffer == null)
    {
      errorMessage = null;
    }
    else
    {
      errorMessage = errorMessageBuffer.toString();
    }

    String processingTime =
         String.valueOf(processingStopTime - processingStartTime);

    return new String[][]
    {
      new String[] { LOG_ELEMENT_RESULT_CODE, resultCode },
      new String[] { LOG_ELEMENT_ERROR_MESSAGE, errorMessage },
      new String[] { LOG_ELEMENT_PROCESSING_TIME, processingTime }
    };
  }



  /**
   * Retrieves the set of controls to include in the response to the client.
   * Note that the contents of this list should not be altered after
   * post-operation plugins have been called.  Note that abandon operations
   * must never have an associated response, so this method will not be used for
   * this type of operation.
   *
   * @return  The set of controls to include in the response to the client.
   */
  public List<Control> getResponseControls()
  {
    assert debugEnter(CLASS_NAME, "getResponseControls");

    // An abandon operation can never have a response, so just return an empty
    // list.
    return NO_RESPONSE_CONTROLS;
  }



  /**
   * Performs the work of actually processing this operation.  This should
   * include all processing for the operation, including invoking plugins,
   * logging messages, performing access control, managing synchronization, and
   * any other work that might need to be done in the course of processing.
   */
  public void run()
  {
    assert debugEnter(CLASS_NAME, "run");

    setResultCode(ResultCode.UNDEFINED);


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;


    // Start the processing timer.
    processingStartTime = System.currentTimeMillis();


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
abandonProcessing:
    {
      // Invoke the pre-parse abandon plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseAbandonPlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing.  Log the request and result and
        // return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logAbandonRequest(this);
        logAbandonResult(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        break abandonProcessing;
      }


      // Log the abandon request message.
      logAbandonRequest(this);


      // Actually perform the abandon operation.  Make sure to set the result
      // code to reflect whether the abandon was successful and an error message
      // if it was not.  Even though there is no response, the result should
      // still be logged.
      Operation operation =
           clientConnection.getOperationInProgress(idToAbandon);
      if (operation == null)
      {
        setResultCode(ResultCode.NO_SUCH_OPERATION);
        appendErrorMessage(getMessage(MSGID_ABANDON_OP_NO_SUCH_OPERATION,
                                      idToAbandon));
      }
      else
      {
        // Even though it is technically illegal to send a response for
        // operations that have been abandoned, it may be a good idea to do so
        // to ensure that the requestor isn't left hanging.  This will be a
        // configurable option in the server.
        boolean notifyRequestor = DirectoryServer.notifyAbandonedOperations();
        String cancelReason = getMessage(MSGID_CANCELED_BY_ABANDON_REQUEST,
                                         messageID);
        StringBuilder cancelResponse = new StringBuilder();
        CancelResult result =
             operation.cancel(new CancelRequest(notifyRequestor, cancelReason,
                                                cancelResponse));
        setResultCode(result.getResultCode());
        setErrorMessage(cancelResponse);
      }
    }


    // Invoke the post-operation abandon plugins.
    if (! skipPostOperation)
    {
      pluginConfigManager.invokePostOperationAbandonPlugins(this);
    }


    // Stop the processing timer.
    processingStopTime = System.currentTimeMillis();


    // Log the result of the abandon operation.
    logAbandonResult(this);
  }



  /**
   * Attempts to cancel this operation before processing has completed.  Note
   * that an abandon operation may not be canceled, so this should never do
   * anything.
   *
   * @param  cancelRequest  Information about the way in which the operation
   *                        should be canceled.
   *
   * @return  A code providing information on the result of the cancellation.
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    assert debugEnter(CLASS_NAME, "cancel", String.valueOf(cancelRequest));

    cancelRequest.addResponseMessage(getMessage(MSGID_CANNOT_CANCEL_ABANDON));
    return CancelResult.CANNOT_CANCEL;
  }



  /**
   * Retrieves the cancel request that has been issued for this operation, if
   * there is one.  Note that an abandon operation may not be canceled, so this
   * will always return <CODE>null</CODE>.
   *
   * @return  The cancel request that has been issued for this operation, or
   *          <CODE>null</CODE> if there has not been any request to cancel.
   */
  public CancelRequest getCancelRequest()
  {
    assert debugEnter(CLASS_NAME, "getCancelRequest");

    return null;
  }



  /**
   * Appends a string representation of this operation to the provided buffer.
   *
   * @param  buffer  The buffer into which a string representation of this
   *                 operation should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append("AbandonOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", idToAbandon=");
    buffer.append(idToAbandon);
    buffer.append(")");
  }
}

