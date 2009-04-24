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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.MessageBuilder;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.types.operation.PostOperationExtendedOperation;
import org.opends.server.types.operation.PostResponseExtendedOperation;
import org.opends.server.types.operation.PreOperationExtendedOperation;
import org.opends.server.types.operation.PreParseExtendedOperation;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

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
  private ByteString requestValue;

  // The value for the response associated with this extended operation.
  private ByteString responseValue;

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
                           String requestOID, ByteString requestValue)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.requestOID   = requestOID;
    this.requestValue = requestValue;

    responseOID      = null;
    responseValue    = null;
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;

    if (requestOID.equals(OID_CANCEL_REQUEST))
    {
      cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
          ERR_CANNOT_CANCEL_CANCEL.get());
    }
    if(requestOID.equals(OID_START_TLS_REQUEST))
    {
      cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
          ERR_CANNOT_CANCEL_START_TLS.get());
    }
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
  public final ByteString getRequestValue()
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
  public final void setRequestValue(ByteString requestValue)
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
  public final ByteString getResponseValue()
  {
    return responseValue;
  }



  /**
   * {@inheritDoc}
   */
  public final void setResponseValue(ByteString responseValue)
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
  @Override
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    // Log the extended request message.
    logExtendedRequest(this);

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();

    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the pre-parse extended plugins.
      PluginResult.PreParse preParseResult =
           pluginConfigManager.invokePreParseExtendedPlugins(this);

      if(!preParseResult.continueProcessing())
      {
        setResultCode(preParseResult.getResultCode());
        appendErrorMessage(preParseResult.getErrorMessage());
        setMatchedDN(preParseResult.getMatchedDN());
        setReferralURLs(preParseResult.getReferralURLs());
        return;
      }

      checkIfCanceled(false);


      // Get the extended operation handler for the request OID.  If there is
      // none, then fail.
      ExtendedOperationHandler handler =
           DirectoryServer.getExtendedOperationHandler(requestOID);
      if (handler == null)
      {
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(ERR_EXTENDED_NO_HANDLER.get(
                String.valueOf(requestOID)));
        return;
      }


      // Look at the controls included in the request and ensure that all
      // critical controls are supported by the handler.
      List<Control> requestControls = getRequestControls();
      if ((requestControls != null) && (! requestControls.isEmpty()))
      {
        for (Control c : requestControls)
        {
          try
          {
            if (!AccessControlConfigManager.getInstance()
                .getAccessControlHandler().isAllowed(
                    this.getAuthorizationDN(), this, c))
            {
              setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
              appendErrorMessage(ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS
                  .get(c.getOID()));
              return;
            }
          }
          catch (DirectoryException e)
          {
            setResultCode(e.getResultCode());
            appendErrorMessage(e.getMessageObject());
            return;
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

            return;
          }
        }
      }


      // Check to see if the client has permission to perform the
      // extended operation.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.
      try
      {
        if (AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(this) == false)
        {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          appendErrorMessage(ERR_EXTENDED_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS
              .get(String.valueOf(requestOID)));
          return;
        }
      }
      catch (DirectoryException e)
      {
        setResultCode(e.getResultCode());
        appendErrorMessage(e.getMessageObject());
        return;
      }

      try
      {
        // Invoke the pre-operation extended plugins.
        PluginResult.PreOperation preOpResult =
            pluginConfigManager.invokePreOperationExtendedPlugins(this);
        if(!preOpResult.continueProcessing())
        {
          setResultCode(preOpResult.getResultCode());
          appendErrorMessage(preOpResult.getErrorMessage());
          setMatchedDN(preOpResult.getMatchedDN());
          setReferralURLs(preOpResult.getReferralURLs());
          return;
        }

        checkIfCanceled(false);

        // Actually perform the processing for this operation.
        handler.processExtendedOperation(this);

      }
      finally
      {
        pluginConfigManager.invokePostOperationExtendedPlugins(this);
      }

    }
    catch(CanceledOperationException coe)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, coe);
      }

      setResultCode(ResultCode.CANCELED);
      cancelResult = new CancelResult(ResultCode.CANCELED, null);

      appendErrorMessage(coe.getCancelRequest().getCancelReason());
    }
    finally
    {
      // Stop the processing timer.
      setProcessingStopTime();

      // Send the response to the client.
      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELED ||
          cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.notifyAbandonedOperations())
      {
        clientConnection.sendResponse(this);
      }

      if(requestOID.equals(OID_START_TLS_REQUEST))
      {
        clientConnection.finishBindOrStartTLS();
      }

      // Log the extended response.
      logExtendedResponse(this);

      // Invoke the post-response extended plugins.
      pluginConfigManager.invokePostResponseExtendedPlugins(this);

      // If no cancel result, set it
      if(cancelResult == null)
      {
        cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
      }
    }
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

