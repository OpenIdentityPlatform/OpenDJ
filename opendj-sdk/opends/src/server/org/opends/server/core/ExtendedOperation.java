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
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines an extended operation, which can perform virtually any
 * kind of task.
 */
public class ExtendedOperation
       extends Operation
{
  /*** The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.ExtendedOperation";



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

    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(clientConnection),
                              String.valueOf(operationID),
                              String.valueOf(messageID),
                              String.valueOf(requestControls),
                              String.valueOf(requestOID),
                              String.valueOf(requestValue)
                            });

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
  public String getRequestOID()
  {
    assert debugEnter(CLASS_NAME, "getRequestOID");

    return requestOID;
  }



  /**
   * Specifies the OID for the request associated with this extended operation.
   *
   * @param  requestOID  The OID for the request associated with this extended
   *                     operation.
   */
  public void setRequestOID(String requestOID)
  {
    assert debugEnter(CLASS_NAME, "setRequestOID", String.valueOf(requestOID));

    this.requestOID = requestOID;
  }



  /**
   * Retrieves the value for the request associated with this extended
   * operation.
   *
   * @return  The value for the request associated with this extended operation.
   */
  public ASN1OctetString getRequestValue()
  {
    assert debugEnter(CLASS_NAME, "getRequestValue");

    return requestValue;
  }



  /**
   * Specifies the value for the request associated with this extended
   * operation.
   *
   * @param  requestValue  The value for the request associated with this
   *                       extended operation.
   */
  public void setRequestValue(ASN1OctetString requestValue)
  {
    assert debugEnter(CLASS_NAME, "setRequestValue",
                      String.valueOf(requestValue));

    this.requestValue = requestValue;
  }



  /**
   * Retrieves the OID to include in the response to the client.
   *
   * @return  The OID to include in the response to the client.
   */
  public String getResponseOID()
  {
    assert debugEnter(CLASS_NAME, "getResponseOID");

    return responseOID;
  }



  /**
   * Specifies the OID to include in the response to the client.
   *
   * @param  responseOID  The OID to include in the response to the client.
   */
  public void setResponseOID(String responseOID)
  {
    assert debugEnter(CLASS_NAME, "setResponseOID",
                      String.valueOf(responseOID));

    this.responseOID = responseOID;
  }



  /**
   * Retrieves the value to include in the response to the client.
   *
   * @return  The value to include in the response to the client.
   */
  public ASN1OctetString getResponseValue()
  {
    assert debugEnter(CLASS_NAME, "getResponseValue");

    return responseValue;
  }



  /**
   * Specifies the value to include in the response to the client.
   *
   * @param  responseValue  The value to include in the response to the client.
   */
  public void setResponseValue(ASN1OctetString responseValue)
  {
    assert debugEnter(CLASS_NAME, "setResponseValue",
                      String.valueOf(responseValue));

    this.responseValue = responseValue;
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
   * actually hold a time immediately before the response was sent to the
   * client.
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

    return OperationType.EXTENDED;
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
      new String[] { LOG_ELEMENT_EXTENDED_REQUEST_OID, requestOID }
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
   * Retrieves the set of controls to include in the response to the client.
   * Note that the contents of this list should not be altered after
   * post-operation plugins have been called.
   *
   * @return  The set of controls to include in the response to the client.
   */
  public List<Control> getResponseControls()
  {
    assert debugEnter(CLASS_NAME, "getResponseControls");

    return responseControls;
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


      // Check the set of controls included in the request.  If there are any,
      // see if any special processing is required.  This should also include
      // taking care of any synchronization that might be needed.
      // NYI


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
  public void sendExtendedResponse()
  {
    assert debugEnter(CLASS_NAME, "sendExtendedResponse");

    if (! responseSent)
    {
      responseSent = true;
      clientConnection.sendResponse(this);
    }
  }



  /**
   * Indicates whether the response for this extended operation has been sent
   * from somewhere outside of this class.  This should only be used by the
   * StartTLS extended operation for the case in which it needs to send a
   * response in the clear after TLS negotiation has already started on the
   * connection.
   */
  public void setResponseSent()
  {
    assert debugEnter(CLASS_NAME, "setResponseSent",
                      String.valueOf(responseSent));

    this.responseSent = true;
  }



  /**
   * Attempts to cancel this operation before processing has completed.
   *
   * @param  cancelRequest  Information about the way in which the operation
   *                        should be canceled.
   *
   * @return  A code providing information on the result of the cancellation.
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    assert debugEnter(CLASS_NAME, "cancel", String.valueOf(cancelRequest));

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
        assert debugException(CLASS_NAME, "cancel", e);
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
   * Retrieves the cancel request that has been issued for this operation, if
   * there is one.
   *
   * @return  The cancel request that has been issued for this operation, or
   *          <CODE>null</CODE> if there has not been any request to cancel.
   */
  public CancelRequest getCancelRequest()
  {
    assert debugEnter(CLASS_NAME, "getCancelRequest");

    return cancelRequest;
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

    buffer.append("ExtendedOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", oid=");
    buffer.append(requestOID);
    buffer.append(")");
  }
}

