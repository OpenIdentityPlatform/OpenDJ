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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostOperationExtendedOperation;
import org.opends.server.types.operation.PostResponseExtendedOperation;
import org.opends.server.types.operation.PreOperationExtendedOperation;
import org.opends.server.types.operation.PreParseExtendedOperation;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines an extended operation, which can perform virtually any
 * kind of task.
 */
public class ExtendedOperationBasis
       extends AbstractOperation
       implements ExtendedOperation,
                  PreParseExtendedOperation,
                  PreOperationExtendedOperation,
                  PostOperationExtendedOperation,
                  PostResponseExtendedOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

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
  public ExtendedOperationBasis(ClientConnection clientConnection,
                           long operationID,
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
   * {@inheritDoc}
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
   * {@inheritDoc}
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
   * {@inheritDoc}
   */
  public final String getResponseOID()
  {
    return responseOID;
  }



  /**
   * {@inheritDoc}
   */
  public final void setResponseOID(String responseOID)
  {
    this.responseOID = responseOID;
  }



  /**
   * {@inheritDoc}
   */
  public final ASN1OctetString getResponseValue()
  {
    return responseValue;
  }



  /**
   * {@inheritDoc}
   */
  public final void setResponseValue(ASN1OctetString responseValue)
  {
    this.responseValue = responseValue;
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
                                     boolean sendNotification, Message message
  )
  {
    clientConnection.disconnect(disconnectReason, sendNotification,
            message);
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
    MessageBuilder errorMessageBuffer = getErrorMessage();
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
         String.valueOf(getProcessingTime());

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
   * Performs the work of actually processing this operation.  This
   * should include all processing for the operation, including
   * invoking plugins, logging messages, performing access control,
   * managing synchronization, and any other work that might need to
   * be done in the course of processing.
   */
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;


    // Start the processing timer.
    setProcessingStartTime();


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      if (! (requestOID.equals(OID_CANCEL_REQUEST) ||
             requestOID.equals(OID_START_TLS_REQUEST)))
      {
        indicateCancelled(cancelRequest);
        setProcessingStopTime();
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

        appendErrorMessage(ERR_CANCELED_BY_PREPARSE_DISCONNECT.get());

        setProcessingStopTime();

        logExtendedRequest(this);
        logExtendedResponse(this);
        pluginConfigManager.invokePostResponseExtendedPlugins(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        logExtendedRequest(this);
        break extendedProcessing;
      }
      else if (preParseResult.skipCoreProcessing())
      {
        skipPostOperation = false;
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
          setProcessingStopTime();
          pluginConfigManager.invokePostResponseExtendedPlugins(this);
          return;
        }
      }


      // Get the extended operation handler for the request OID.  If there is
      // none, then fail.
      ExtendedOperationHandler handler =
           DirectoryServer.getExtendedOperationHandler(requestOID);
      if (handler == null)
      {
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(ERR_EXTENDED_NO_HANDLER.get(
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
          if (!AccessControlConfigManager.getInstance().
                  getAccessControlHandler().
                  isAllowed(this.getAuthorizationDN(), this, c)) {
            setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

            appendErrorMessage(ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(
                    c.getOID()));
            skipPostOperation=true;
            break extendedProcessing;
          }
          if (! c.isCritical())
          {
            // The control isn't critical, so we don't care if it's supported
            // or not.
          }
          else if (! handler.supportsControl(c.getOID()))
          {
            setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

            appendErrorMessage(ERR_EXTENDED_UNSUPPORTED_CRITICAL_CONTROL.get(
                    String.valueOf(requestOID),
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

        appendErrorMessage(ERR_EXTENDED_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                String.valueOf(requestOID)));

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

        appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());
        setProcessingStopTime();

        logExtendedResponse(this);
        pluginConfigManager.invokePostResponseExtendedPlugins(this);
        return;
      }
      else if (preOpResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        break extendedProcessing;
      }
      else if (preOpResult.skipCoreProcessing())
      {
        skipPostOperation = false;
        break extendedProcessing;
      }


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        if (! (requestOID.equals(OID_CANCEL_REQUEST) ||
               requestOID.equals(OID_START_TLS_REQUEST)))
        {
          indicateCancelled(cancelRequest);
          setProcessingStopTime();
          pluginConfigManager.invokePostResponseExtendedPlugins(this);
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

        appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

        setProcessingStopTime();

        logExtendedResponse(this);
        pluginConfigManager.invokePostResponseExtendedPlugins(this);
        return;
      }
    }


    // Stop the processing timer.
    setProcessingStopTime();


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
   * {@inheritDoc}
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
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
  public boolean setCancelRequest(CancelRequest cancelRequest)
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

