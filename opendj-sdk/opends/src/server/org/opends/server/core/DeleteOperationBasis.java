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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;


import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ENTRY_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ERROR_MESSAGE;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_MATCHED_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_PROCESSING_TIME;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_REFERRAL_URLS;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_RESULT_CODE;
import static org.opends.server.loggers.AccessLogger.logDeleteRequest;
import static org.opends.server.loggers.AccessLogger.logDeleteResponse;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AbstractOperation;
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
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PreParseDeleteOperation;
import org.opends.server.workflowelement.localbackend.*;



/**
 * This class defines an operation that may be used to remove an entry from the
 * Directory Server.
 */
public class DeleteOperationBasis
       extends AbstractOperation
       implements PreParseDeleteOperation,
                  DeleteOperation,
                  PostResponseDeleteOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  // The raw, unprocessed entry DN as included in the client request.
  private ByteString rawEntryDN;

  // The cancel request that has been issued for this delete operation.
  private CancelRequest cancelRequest;

  // The DN of the entry for the delete operation.
  private DN entryDN;

  // The proxied authorization target DN for this operation.
  private DN proxiedAuthorizationDN;

  // The set of response controls for this delete operation.
  private List<Control> responseControls;

  // The change number that has been assigned to this operation.
  private long changeNumber;


  /**
   * Creates a new delete operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw, unprocessed DN of the entry to delete,
   *                           as included in the client request.
   */
  public DeleteOperationBasis(ClientConnection clientConnection,
                         long operationID,
                         int messageID, List<Control> requestControls,
                         ByteString rawEntryDN)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN = rawEntryDN;

    entryDN          = null;
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
    changeNumber     = -1;
  }



  /**
   * Creates a new delete operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  entryDN           The entry DN for this delete operation.
   */
  public DeleteOperationBasis(ClientConnection clientConnection,
                         long operationID,
                         int messageID, List<Control> requestControls,
                         DN entryDN)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN = entryDN;

    rawEntryDN       = new ASN1OctetString(entryDN.toString());
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
    changeNumber     = -1;
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
      setMatchedDN(de.getMatchedDN());
      setReferralURLs(de.getReferralURLs());
    }

    return entryDN;
  }

  /**
   * {@inheritDoc}
   */
  public final long getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * {@inheritDoc}
   */
  public final void setChangeNumber(long changeNumber)
  {
    this.changeNumber = changeNumber;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.DELETE;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final void disconnectClient(DisconnectReason disconnectReason,
                                     boolean sendNotification, Message message
  )
  {
    // Before calling clientConnection.disconnect, we need to mark this
    // operation as cancelled so that the attempt to cancel it later won't cause
    // an unnecessary delay.
    setCancelResult(CancelResult.CANCELED);

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
      new String[] { LOG_ELEMENT_ENTRY_DN, String.valueOf(rawEntryDN) }
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
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
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
  public
  boolean setCancelRequest(CancelRequest cancelRequest)
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
    buffer.append("DeleteOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(")");
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
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();

    // Start the processing timer.
    setProcessingStartTime();

    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      setProcessingStopTime();
      return;
    }


    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
deleteProcessing:
    {
      // Invoke the pre-parse delete plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseDeletePlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        appendErrorMessage(ERR_CANCELED_BY_PREPARSE_DISCONNECT.get());

        setProcessingStopTime();

        logDeleteRequest(this);
        logDeleteResponse(this);
        pluginConfigManager.invokePostResponseDeletePlugins(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        logDeleteRequest(this);
        break deleteProcessing;
      }
      else if (preParseResult.skipCoreProcessing())
      {
        break deleteProcessing;
      }


      // Log the delete request message.
      logDeleteRequest(this);


      // Check for a request to cancel this operation.
      if (cancelRequest != null)
      {
        break deleteProcessing;
      }


      // Process the entry DN to convert it from its raw form as provided by the
      // client to the form required for the rest of the delete processing.
      DN entryDN = getEntryDN();
      if (entryDN == null){
        break deleteProcessing;
      }


      // Retrieve the network group attached to the client connection
      // and get a workflow to process the operation.
      NetworkGroup ng = getClientConnection().getNetworkGroup();
      Workflow workflow = ng.getWorkflowCandidate(entryDN);
      if (workflow == null)
      {
        // We have found no workflow for the requested base DN, just return
        // a no such entry result code and stop the processing.
        updateOperationErrMsgAndResCode();
        break deleteProcessing;
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
      logDeleteResponse(this);

      return;
    }

    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);

      // Stop the processing timer.
      setProcessingStopTime();

      // Log the delete response message.
      logDeleteResponse(this);

      // Invoke the post-response delete plugins.
      invokePostResponsePlugins(workflowExecuted);

      return;
    }

    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);

    // Stop the processing timer.
    setProcessingStopTime();

    // Send the delete response to the client.
    getClientConnection().sendResponse(this);

    // Log the delete response.
    logDeleteResponse(this);

    // Notifies any persistent searches that might be registered with the
    // server.
    notifyPersistentSearches(workflowExecuted);

    // Invoke the post-response delete plugins.
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
          LocalBackendDeleteOperation localOperation =
            (LocalBackendDeleteOperation)localOp;
          pluginConfigManager.invokePostResponseDeletePlugins(localOperation);
        }
      }
    }
    else
    {
      // Invoke the post response plugins that have been registered with
      // the current operation
      pluginConfigManager.invokePostResponseDeletePlugins(this);
    }
  }


  /**
   * Notifies any persistent searches that might be registered with the server.
   * If no workflow has been executed then don't notify persistent searches.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been
   *                         executed
   */
  private void notifyPersistentSearches(boolean workflowExecuted)
  {
    if (! workflowExecuted)
    {
      return;
    }

    List localOperations =
      (List)getAttachment(Operation.LOCALBACKENDOPERATIONS);

    if (localOperations != null)
    {
      for (Object localOp : localOperations)
      {
        LocalBackendDeleteOperation localOperation =
          (LocalBackendDeleteOperation)localOp;
        // Notify any persistent searches that might be registered with the
        // server.
        if (getResultCode() == ResultCode.SUCCESS)
        {
          for (PersistentSearch persistentSearch :
            DirectoryServer.getPersistentSearches())
          {
            try
            {
              persistentSearch.processDelete(localOperation,
                  localOperation.getEntryToDelete());
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              Message message = ERR_DELETE_ERROR_NOTIFYING_PERSISTENT_SEARCH.
                  get(String.valueOf(persistentSearch), getExceptionMessage(e));
              logError(message);

              DirectoryServer.deregisterPersistentSearch(persistentSearch);
            }
          }
        }
      }
    }
  }


  /**
   * Updates the error message and the result code of the operation.
   *
   * This method is called because no workflows were found to process
   * the operation.
   */
  private void updateOperationErrMsgAndResCode()
  {
    setResultCode(ResultCode.NO_SUCH_OBJECT);
    appendErrorMessage(ERR_DELETE_NO_SUCH_ENTRY.get(
            String.valueOf(getEntryDN())));
  }


  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public Entry getEntryToDelete() {
    // TODO Auto-generated method stub
    return null;
  }

}

