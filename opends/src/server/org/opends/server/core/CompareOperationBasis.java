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
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.AccessLogger.logCompareRequest;
import static org.opends.server.loggers.AccessLogger.logCompareResponse;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.messages.CoreMessages.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostResponseCompareOperation;
import org.opends.server.types.operation.PreParseCompareOperation;
import org.opends.server.workflowelement.localbackend.
       LocalBackendCompareOperation;


/**
 * This class defines an operation that may be used to determine whether a
 * specified entry in the Directory Server contains a given attribute-value
 * pair.
 */
public class CompareOperationBasis
             extends AbstractOperation
             implements PreParseCompareOperation, CompareOperation,
                        Runnable, PostResponseCompareOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  // The attribute type for this compare operation.
  private AttributeType attributeType;

  // The assertion value for the compare operation.
  private ByteString assertionValue;

  // The raw, unprocessed entry DN as included in the client request.
  private ByteString rawEntryDN;

  // The cancel request that has been issued for this compare operation.
  private CancelRequest cancelRequest;

  // The DN of the entry for the compare operation.
  private DN entryDN;

  // The proxied authorization target DN for this operation.
  private DN proxiedAuthorizationDN;

  // The set of response controls for this compare operation.
  private List<Control> responseControls;

  // The attribute type for the compare operation.
  private String rawAttributeType;



  /**
   * Creates a new compare operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw, unprocessed entry DN as provided in the
   *                           client request.  This may or may not be a valid
   *                           DN as no validation will have been performed yet.
   * @param  rawAttributeType  The raw attribute type for the compare operation.
   * @param  assertionValue    The assertion value for the compare operation.
   */
  public CompareOperationBasis(
                          ClientConnection clientConnection, long operationID,
                          int messageID, List<Control> requestControls,
                          ByteString rawEntryDN, String rawAttributeType,
                          ByteString assertionValue)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN       = rawEntryDN;
    this.rawAttributeType = rawAttributeType;
    this.assertionValue   = assertionValue;

    responseControls       = new ArrayList<Control>();
    entryDN                = null;
    attributeType          = null;
    cancelRequest          = null;
    proxiedAuthorizationDN = null;
  }



  /**
   * Creates a new compare operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  entryDN           The entry DN for this compare operation.
   * @param  attributeType     The attribute type for this compare operation.
   * @param  assertionValue    The assertion value for the compare operation.
   */
  public CompareOperationBasis(
                          ClientConnection clientConnection, long operationID,
                          int messageID, List<Control> requestControls,
                          DN entryDN, AttributeType attributeType,
                          ByteString assertionValue)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN        = entryDN;
    this.attributeType  = attributeType;
    this.assertionValue = assertionValue;

    responseControls       = new ArrayList<Control>();
    rawEntryDN             = new ASN1OctetString(entryDN.toString());
    rawAttributeType       = attributeType.getNameOrOID();
    cancelRequest          = null;
    proxiedAuthorizationDN = null;
  }



  /**
   * {@inheritDoc}
   */
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }



  /**
   * {@inheritDoc}
   */
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }



  /**
   * {@inheritDoc}
   */
  public final DN getEntryDN()
  {
    return entryDN;
  }



  /**
   * {@inheritDoc}
   */
  public final String getRawAttributeType()
  {
    return rawAttributeType;
  }



  /**
   * {@inheritDoc}
   */
  public final void setRawAttributeType(String rawAttributeType)
  {
    this.rawAttributeType = rawAttributeType;

    attributeType = null;
  }



  /**
   * {@inheritDoc}
   */
  public final AttributeType getAttributeType()
  {
    return attributeType;
  }



  /**
   * {@inheritDoc}
   */
  public void setAttributeType(AttributeType attributeType)
  {
    this.attributeType = attributeType;
  }



  /**
   * {@inheritDoc}
   */
  public final ByteString getAssertionValue()
  {
    return assertionValue;
  }



  /**
   * {@inheritDoc}
   */
  public final void setAssertionValue(ByteString assertionValue)
  {
    this.assertionValue = assertionValue;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.COMPARE;
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
      new String[] { LOG_ELEMENT_ENTRY_DN, String.valueOf(rawEntryDN) },
      new String[] { LOG_ELEMENT_COMPARE_ATTR, rawAttributeType }
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
      new String[] { LOG_ELEMENT_PROCESSING_TIME, processingTime }
    };
  }



  /**
   * Retrieves the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @return  The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
  }



  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
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

    // Start the processing timer.
    setProcessingStartTime();

    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      setProcessingStopTime();
      return;
    }

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();

    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
compareProcessing:
    {
      // Invoke the pre-parse compare plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseComparePlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        appendErrorMessage(ERR_CANCELED_BY_PREPARSE_DISCONNECT.get());

        setProcessingStopTime();

        logCompareRequest(this);
        logCompareResponse(this);
        pluginConfigManager.invokePostResponseComparePlugins(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        logCompareRequest(this);
        break compareProcessing;
      }
      else if (preParseResult.skipCoreProcessing())
      {
        break compareProcessing;
      }


      // Log the compare request message.
      logCompareRequest(this);


      // Check for a request to cancel this operation.
      if (cancelRequest != null)
      {
        break compareProcessing;
      }


      // Process the entry DN to convert it from the raw form to the form
      // required for the rest of the compare processing.
      try
      {
        if (entryDN == null)
        {
          entryDN = DN.decode(rawEntryDN);
        }
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getMessageObject());

        break compareProcessing;
      }


      // Retrieve the network group registered with the client connection
      // and get a workflow to process the operation.
      NetworkGroup ng = getClientConnection().getNetworkGroup();
      Workflow workflow = ng.getWorkflowCandidate(entryDN);
      if (workflow == null)
      {
        // We have found no workflow for the requested base DN, just return
        // a no such entry result code and stop the processing.
        updateOperationErrMsgAndResCode();
        break compareProcessing;
      }
      workflow.execute(this);
      workflowExecuted = true;

    } // end of processing block


    // Check for a terminated connection.
    if (getCancelResult() == CancelResult.CANCELED)
    {
      // Stop the processing timer.
      setProcessingStopTime();

      // Log the add response message.
      logCompareResponse(this);

      return;
    }

    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);

      // Stop the processing timer.
      setProcessingStopTime();

      // Log the compare response message.
      logCompareResponse(this);

      // Invoke the post-response compare plugins.
      invokePostResponsePlugins(workflowExecuted);

      return;
    }

    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);

    // Stop the processing timer.
    setProcessingStopTime();

    // Send the compare response to the client.
    clientConnection.sendResponse(this);

    // Log the compare response message.
    logCompareResponse(this);

    // Invoke the post-response compare plugins.
    invokePostResponsePlugins(workflowExecuted);

  }


  /**
   * Invokes the post response plugins. If a workflow has been executed
   * then invoke the post response plugins provided by the workflow
   * elements of the worklfow, otherwise invoke the post reponse plugins
   * that have been registered with the current operation.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been
   *                         executed
   */
  private void invokePostResponsePlugins(boolean workflowExecuted)
  {
    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();

    // Invoke the post response plugins
    if (workflowExecuted)
    {
      // Invoke the post response plugins that have been registered by
      // the workflow elements
      List localOperations =
        (List)getAttachment(Operation.LOCALBACKENDOPERATIONS);

      if (localOperations != null)
      {
        for (Object localOp : localOperations)
        {
          LocalBackendCompareOperation localOperation =
            (LocalBackendCompareOperation)localOp;
          pluginConfigManager.invokePostResponseComparePlugins(localOperation);
        }
      }
    }
    else
    {
      // Invoke the post response plugins that have been registered with
      // the current operation
      pluginConfigManager.invokePostResponseComparePlugins(this);
    }
  }


  /**
   * Updates the error message and the result code of the operation.
   *
   * This method is called because no workflow was found to process
   * the operation.
   */
  private void updateOperationErrMsgAndResCode()
  {
    setResultCode(ResultCode.NO_SUCH_OBJECT);
    appendErrorMessage(
      ERR_COMPARE_NO_SUCH_ENTRY.get(String.valueOf(getEntryDN())));
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
    buffer.append("CompareOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(", attr=");
    buffer.append(rawAttributeType);
    buffer.append(")");
  }


  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public Entry getEntryToCompare()
  {
    return null;
  }

}
