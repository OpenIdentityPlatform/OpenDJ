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
package org.opends.server.core;



import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostOperationAbandonOperation;
import org.opends.server.types.operation.PreParseAbandonOperation;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;




/**
 * This class defines an operation that may be used to abandon an operation that
 * may already be in progress in the Directory Server.
 */
public class AbandonOperation
       extends Operation
       implements PreParseAbandonOperation, PostOperationAbandonOperation
{



  // The message ID of the operation that should be abandoned.
  private final int idToAbandon;

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


    this.idToAbandon = idToAbandon;
  }



  /**
   * Retrieves the message ID of the operation that should be abandoned.
   *
   * @return  The message ID of the operation that should be abandoned.
   */
  public final int getIDToAbandon()
  {

    return idToAbandon;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final long getProcessingStartTime()
  {

    return processingStartTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final long getProcessingStopTime()
  {

    return processingStopTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final long getProcessingTime()
  {

    return (processingStopTime - processingStartTime);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final OperationType getOperationType()
  {

    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.ABANDON;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void disconnectClient(DisconnectReason disconnectReason,
                                     boolean sendNotification, String message,
                                     int messageID)
  {
    // Since abandon operations can't be cancelled, we don't need to do anything
    // but forward the request on to the client connection.
    clientConnection.disconnect(disconnectReason, sendNotification, message,
                                messageID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getRequestLogElements()
  {

    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return new String[][]
    {
      new String[] { LOG_ELEMENT_ID_TO_ABANDON, String.valueOf(idToAbandon) }
    };
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getResponseLogElements()
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
   * {@inheritDoc}
   */
  @Override()
  public final List<Control> getResponseControls()
  {

    // An abandon operation can never have a response, so just return an empty
    // list.
    return NO_RESPONSE_CONTROLS;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void addResponseControl(Control control)
  {
    // An abandon operation can never have a response, so just ignore this.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void removeResponseControl(Control control)
  {
    // An abandon operation can never have a response, so just ignore this.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void run()
  {

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
   * {@inheritDoc}
   */
  @Override()
  public final CancelResult cancel(CancelRequest cancelRequest)
  {

    cancelRequest.addResponseMessage(getMessage(MSGID_CANNOT_CANCEL_ABANDON));
    return CancelResult.CANNOT_CANCEL;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final CancelRequest getCancelRequest()
  {

    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  boolean setCancelRequest(CancelRequest cancelRequest)
  {

    // Abandon operations cannot be canceled.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void toString(StringBuilder buffer)
  {

    buffer.append("AbandonOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", idToAbandon=");
    buffer.append(idToAbandon);
    buffer.append(")");
  }
}

