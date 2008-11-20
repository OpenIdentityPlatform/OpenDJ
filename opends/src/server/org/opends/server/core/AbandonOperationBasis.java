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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ERROR_MESSAGE;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ID_TO_ABANDON;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_PROCESSING_TIME;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_RESULT_CODE;
import static org.opends.server.loggers.AccessLogger.logAbandonRequest;
import static org.opends.server.loggers.AccessLogger.logAbandonResult;
import static org.opends.messages.CoreMessages.*;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationAbandonOperation;
import org.opends.server.types.operation.PreParseAbandonOperation;


/**
 * This class defines an operation that may be used to abandon an operation
 * that may already be in progress in the Directory Server.
 */
public class AbandonOperationBasis extends AbstractOperation
    implements Runnable,
               AbandonOperation,
               PreParseAbandonOperation,
               PostOperationAbandonOperation
{

  // The message ID of the operation that should be abandoned.
  private final int idToAbandon;


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
  public AbandonOperationBasis(
      ClientConnection clientConnection,
      long operationID,
      int messageID,
      List<Control> requestControls,
      int idToAbandon)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.idToAbandon = idToAbandon;
    this.cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
        ERR_CANNOT_CANCEL_ABANDON.get());
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
    MessageBuilder errorMessageBuffer = getErrorMessage();
    if (errorMessageBuffer == null)
    {
      errorMessage = null;
    }
    else
    {
      errorMessage = errorMessageBuffer.toString();
    }

    String processingTime =
         String.valueOf(getProcessingTime());

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
   * Performs the work of actually processing this operation.  This
   * should include all processing for the operation, including
   * invoking plugins, logging messages, performing access control,
   * managing synchronization, and any other work that might need to
   * be done in the course of processing.
   */
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    // Log the abandon request message.
    logAbandonRequest(this);

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
abandonProcessing:
    {
      // Invoke the pre-parse abandon plugins.
      PluginResult.PreParse preParseResult =
           pluginConfigManager.invokePreParseAbandonPlugins(this);
      if (!preParseResult.continueProcessing())
      {
        setResultCode(preParseResult.getResultCode());
        appendErrorMessage(preParseResult.getErrorMessage());
        setMatchedDN(preParseResult.getMatchedDN());
        setReferralURLs(preParseResult.getReferralURLs());
        break abandonProcessing;
      }

      // Actually perform the abandon operation.  Make sure to set the result
      // code to reflect whether the abandon was successful and an error message
      // if it was not.  Even though there is no response, the result should
      // still be logged.
      //
      // Even though it is technically illegal to send a response for
      // operations that have been abandoned, it may be a good idea to do so
      // to ensure that the requestor isn't left hanging.  This will be a
      // configurable option in the server.
      boolean notifyRequestor = DirectoryServer.notifyAbandonedOperations();

      Message cancelReason = INFO_CANCELED_BY_ABANDON_REQUEST.get(messageID);

      CancelRequest _cancelRequest = new CancelRequest(notifyRequestor,
                                                       cancelReason);

      CancelResult result = clientConnection.cancelOperation(idToAbandon,
                                                             _cancelRequest);

      setResultCode(result.getResultCode());
      appendErrorMessage(result.getResponseMessage());

      PluginResult.PostOperation postOpResult =
          pluginConfigManager.invokePostOperationAbandonPlugins(this);
      if (!postOpResult.continueProcessing())
      {
        setResultCode(preParseResult.getResultCode());
        appendErrorMessage(preParseResult.getErrorMessage());
        setMatchedDN(preParseResult.getMatchedDN());
        setReferralURLs(preParseResult.getReferralURLs());
        break abandonProcessing;
      }
    }


    // Stop the processing timer.
    setProcessingStopTime();


    // Log the result of the abandon operation.
    logAbandonResult(this);
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
