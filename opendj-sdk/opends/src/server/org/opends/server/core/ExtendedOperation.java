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



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostOperationExtendedOperation;
import org.opends.server.types.operation.PostResponseExtendedOperation;
import org.opends.server.types.operation.PreOperationExtendedOperation;
import org.opends.server.types.operation.PreParseExtendedOperation;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines an extended operation, which can perform virtually any
 * kind of task.
 */
public class ExtendedOperation
       extends Operation
       implements PreParseExtendedOperation, PreOperationExtendedOperation,
                  PostOperationExtendedOperation, PostResponseExtendedOperation
{



  // The value for the request associated with this extended operation.
  private ASN1OctetString requestValue;

  // The value for the response associated with this extended operation.
  private ASN1OctetString responseValue;

  // Indicates whether a response has yet been sent for this operation.
  private boolean responseSent;

  // The cancel request that has been issued for this extended operation.
  private CancelRequest cancelRequest;

  // The set of response controls for this extended operation.
  private List<Control> responseControls;

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;

  // The OID for the request associated with this extended operation.
  private String requestOID;

  // The OID for the response associated with this extended operation.
  private String responseOID;



  /**
   * Creates a new extended operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  requestOID        The OID for the request associated with this
   *                           extended operation.
   * @param  requestValue      The value for the request associated with this
   *                           extended operation.
   */
  public ExtendedOperation(ClientConnection clientConnection, long operationID,
                           int messageID, List<Control> requestControls,
                           String requestOID, ASN1OctetString requestValue)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.requestOID   = requestOID;
    this.requestValue = requestValue;

    responseOID      = null;
    responseValue    = null;
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
    responseSent     = false;
  }



  /**
   * Retrieves the OID for the request associated with this extended operation.
   *
   * @return  The OID for the request associated with this extended operation.
   */
  public final String getRequestOID()
  {
    return requestOID;
  }



  /**
   * Specifies the OID for the request associated with this extended operation.
   * This should only be called by pre-parse plugins.
   *
   * @param  requestOID  The OID for the request associated with this extended
   *                     operation.
   */
  public final void setRequestOID(String requestOID)
  {
    this.requestOID = requestOID;
  }



  /**
   * Retrieves the value for the request associated with this extended
   * operation.
   *
   * @return  The value for the request associated with this extended operation.
   */
  public final ASN1OctetString getRequestValue()
  {
    return requestValue;
  }



  /**
   * Specifies the value for the request associated with this extended
   * operation.  This should only be called by pre-parse plugins.
   *
   * @param  requestValue  The value for the request associated with this
   *                       extended operation.
   */
  public final void setRequestValue(ASN1OctetString requestValue)
  {
    this.requestValue = requestValue;
  }



  /**
   * Retrieves the OID to include in the response to the client.  This should
   * not be called by pre-parse or pre-operation plugins.
   *
   * @return  The OID to include in the response to the client.
   */
  public final String getResponseOID()
  {
    return responseOID;
  }



  /**
   * Specifies the OID to include in the response to the client.  This should
   * not be called by post-response plugins.
   *
   * @param  responseOID  The OID to include in the response to the client.
   */
  public final void setResponseOID(String responseOID)
  {
    this.responseOID = responseOID;
  }



  /**
   * Retrieves the value to include in the response to the client.  This should
   * not be called by pre-parse or pre-operation plugins.
   *
   * @return  The value to include in the response to the client.
   */
  public final ASN1OctetString getResponseValue()
  {
    return responseValue;
  }



  /**
   * Specifies the value to include in the response to the client.  This should
   * not be called by post-response plugins.
   *
   * @param  responseValue  The value to include in the response to the client.
   */
  public final void setResponseValue(ASN1OctetString responseValue)
  {
    this.responseValue = responseValue;
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

    return OperationType.EXTENDED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void disconnectClient(DisconnectReason disconnectReason,
                                     boolean sendNotification, String message,
                                     int messageID)
  {
    // Before calling clientConnection.disconnect, we need to mark this
    // operation as cancelled so that the attempt to cancel it later won't cause
    // an unnecessary delay.
    setCancelResult(CancelResult.CANCELED);

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
      new String[] { LOG_ELEMENT_EXTENDED_REQUEST_OID, requestOID }
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

    String matchedDNStr;
    DN matchedDN = getMatchedDN();
    if (matchedDN == null)
    {
      matchedDNStr = null;
    }
    else
    {
      matchedDNStr = matchedDN.toString();
    }

    String referrals;
    List<String> referralURLs = getReferralURLs();
    if ((referralURLs == null) || referralURLs.isEmpty())
    {
      referrals = null;
    }
    else
    {
      StringBuilder buffer = new StringBuilder();
      Iterator<String> iterator = referralURLs.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
      }

      referrals = buffer.toString();
    }

    String processingTime =
         String.valueOf(processingStopTime - processingStartTime);

    return new String[][]
    {
      new String[] { LOG_ELEMENT_RESULT_CODE, resultCode },
      new String[] { LOG_ELEMENT_ERROR_MESSAGE, errorMessage },
      new String[] { LOG_ELEMENT_MATCHED_DN, matchedDNStr },
      new String[] { LOG_ELEMENT_REFERRAL_URLS, referrals },
      new String[] { LOG_ELEMENT_EXTENDED_RESPONSE_OID, responseOID },
      new String[] { LOG_ELEMENT_PROCESSING_TIME, processingTime }
    };
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
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


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      if (! (requestOID.equals(OID_CANCEL_REQUEST) ||
             requestOID.equals(OID_START_TLS_REQUEST)))
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        return;
      }
    }


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
extendedProcessing:
    {
      // Invoke the pre-parse extended plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseExtendedPlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logExtendedRequest(this);
        logExtendedResponse(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        logExtendedRequest(this);
        break extendedProcessing;
      }


      // Log the extended request message.
      logExtendedRequest(this);


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        if (! (requestOID.equals(OID_CANCEL_REQUEST) ||
               requestOID.equals(OID_START_TLS_REQUEST)))
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          return;
        }
      }


      // Get the extended operation handler for the request OID.  If there is
      // none, then fail.
      ExtendedOperationHandler handler =
           DirectoryServer.getExtendedOperationHandler(requestOID);
      if (handler == null)
      {
        setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
        appendErrorMessage(getMessage(MSGID_EXTENDED_NO_HANDLER,
                                      String.valueOf(requestOID)));
        break extendedProcessing;
      }


      // Look at the controls included in the request and ensure that all
      // critical controls are supported by the handler.
      List<Control> requestControls = getRequestControls();
      if ((requestControls != null) && (! requestControls.isEmpty()))
      {
        for (Control c : requestControls)
        {
          if (! c.isCritical())
          {
            // The control isn't critical, so we don't care if it's supported
            // or not.
          }
          else if (! handler.supportsControl(c.getOID()))
          {
            setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

            int msgID = MSGID_EXTENDED_UNSUPPORTED_CRITICAL_CONTROL;
            appendErrorMessage(getMessage(msgID, String.valueOf(requestOID),
                                          c.getOID()));

            break extendedProcessing;
          }
        }
      }


      // Check to see if the client has permission to perform the
      // extended operation.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.
      if (AccessControlConfigManager.getInstance()
          .getAccessControlHandler().isAllowed(this) == false) {
        setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

        int msgID = MSGID_EXTENDED_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
        appendErrorMessage(getMessage(msgID, String.valueOf(requestOID)));

        skipPostOperation = true;
        break extendedProcessing;
      }

      // Invoke the pre-operation extended plugins.
      PreOperationPluginResult preOpResult =
           pluginConfigManager.invokePreOperationExtendedPlugins(this);
      if (preOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the result
        // and return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logExtendedResponse(this);
        return;
      }
      else if (preOpResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        break extendedProcessing;
      }


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        if (! (requestOID.equals(OID_CANCEL_REQUEST) ||
               requestOID.equals(OID_START_TLS_REQUEST)))
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          return;
        }
      }


      // Actually perform the processing for this operation.
      handler.processExtendedOperation(this);
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);


    // Invoke the post-operation extended plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOpResult =
           pluginConfigManager.invokePostOperationExtendedPlugins(this);
      if (postOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the result and
        // return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logExtendedResponse(this);
        return;
      }
    }


    // Stop the processing timer.
    processingStopTime = System.currentTimeMillis();


    // Send the response to the client, if it has not already been sent.
    if (! responseSent)
    {
      responseSent = true;
      clientConnection.sendResponse(this);
    }


    // Log the extended response.
    logExtendedResponse(this);



    // Invoke the post-response extended plugins.
    pluginConfigManager.invokePostResponseExtendedPlugins(this);
  }



  /**
   * Sends an extended response to the client if none has already been sent.
   * Note that extended operation handlers are strongly discouraged from using
   * this method when it is not necessary because its use will prevent the
   * response from being sent after post-operation plugin processing, which may
   * impact the result that should be included.  Nevertheless, it may be needed
   * in some special cases in which the response must be sent before the
   * extended operation handler completes its processing (e.g., the StartTLS
   * operation in which the response must be sent in the clear before actually
   * enabling TLS protection).
   */
  public final void sendExtendedResponse()
  {
    if (! responseSent)
    {
      responseSent = true;
      clientConnection.sendResponse(this);
    }
  }



  /**
   * Indicates that the response for this extended operation has been sent from
   * somewhere outside of this class.  This should only be used by the StartTLS
   * extended operation for the case in which it needs to send a response in the
   * clear after TLS negotiation has already started on the connection.
   */
  public final void setResponseSent()
  {
    this.responseSent = true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final CancelResult cancel(CancelRequest cancelRequest)
  {
    this.cancelRequest = cancelRequest;

    CancelResult cancelResult = getCancelResult();
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
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      cancelResult = getCancelResult();
    }

    if (cancelResult == null)
    {
      // This can happen in some rare cases (e.g., if a client disconnects and
      // there is still a lot of data to send to that client), and in this case
      // we'll prevent the cancel thread from blocking for a long period of
      // time.
      cancelResult = CancelResult.CANNOT_CANCEL;
    }

    return cancelResult;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final CancelRequest getCancelRequest()
  {
    return cancelRequest;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  protected boolean setCancelRequest(CancelRequest cancelRequest)
  {
    this.cancelRequest = cancelRequest;
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void toString(StringBuilder buffer)
  {
    buffer.append("ExtendedOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", oid=");
    buffer.append(requestOID);
    buffer.append(")");
  }
}

