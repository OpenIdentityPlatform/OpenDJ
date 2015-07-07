/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.core;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PreParseDeleteOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendDeleteOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The raw, unprocessed entry DN as included in the client request. */
  private ByteString rawEntryDN;

  /** The DN of the entry for the delete operation. */
  private DN entryDN;

  /** The proxied authorization target DN for this operation. */
  private DN proxiedAuthorizationDN;

  /** The set of response controls for this delete operation. */
  private List<Control> responseControls;

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
    responseControls = new ArrayList<>();
    cancelRequest    = null;
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

    rawEntryDN       = ByteString.valueOf(entryDN.toString());
    responseControls = new ArrayList<>();
    cancelRequest    = null;
  }

  /** {@inheritDoc} */
  @Override
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }

  /** {@inheritDoc} */
  @Override
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }

  /** {@inheritDoc} */
  @Override
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
      logger.traceException(de);

      setResultCode(de.getResultCode());
      appendErrorMessage(de.getMessageObject());
      setMatchedDN(de.getMatchedDN());
      setReferralURLs(de.getReferralURLs());
    }

    return entryDN;
  }

  /** {@inheritDoc} */
  @Override
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.
    return OperationType.DELETE;
  }

  /** {@inheritDoc} */
  @Override
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
  }

  /** {@inheritDoc} */
  @Override
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }

  /** {@inheritDoc} */
  @Override
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }

  /** {@inheritDoc} */
  @Override
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }

  /** {@inheritDoc} */
  @Override
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

  /** {@inheritDoc} */
  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  /** {@inheritDoc} */
  @Override
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    // Log the delete request message.
    logDeleteRequest(this);

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
        DirectoryServer.getPluginConfigManager();

    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;

    try
    {
      // Invoke the pre-parse delete plugins.
      PluginResult.PreParse preParseResult =
          pluginConfigManager.invokePreParseDeletePlugins(this);
      if(!preParseResult.continueProcessing())
      {
        setResultCode(preParseResult.getResultCode());
        appendErrorMessage(preParseResult.getErrorMessage());
        setMatchedDN(preParseResult.getMatchedDN());
        setReferralURLs(preParseResult.getReferralURLs());
        return;
      }


      // Check for a request to cancel this operation.
      checkIfCanceled(false);


      // Process the entry DN to convert it from its raw form as provided by the
      // client to the form required for the rest of the delete processing.
      DN entryDN = getEntryDN();
      if (entryDN == null){
        return;
      }

      workflowExecuted = execute(this, entryDN);
    }
    catch(CanceledOperationException coe)
    {
      logger.traceException(coe);

      setResultCode(ResultCode.CANCELLED);
      cancelResult = new CancelResult(ResultCode.CANCELLED, null);

      appendErrorMessage(coe.getCancelRequest().getCancelReason());
    }
    finally
    {
      // Stop the processing timer.
      setProcessingStopTime();

      // Log the delete response.
      logDeleteResponse(this);

      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELLED ||
          cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.notifyAbandonedOperations())
      {
        clientConnection.sendResponse(this);
      }


      // Invoke the post-response callbacks.
      if (workflowExecuted) {
        invokePostResponseCallbacks();
      }

      // Invoke the post-response delete plugins.
      invokePostResponsePlugins(workflowExecuted);

      // If no cancel result, set it
      if(cancelResult == null)
      {
        cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
      }
    }
  }


  /**
   * Invokes the post response plugins. If a workflow has been executed
   * then invoke the post response plugins provided by the workflow
   * elements of the workflow, otherwise invoke the post response plugins
   * that have been registered with the current operation.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been executed
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
      List<LocalBackendDeleteOperation> localOperations =
        (List)getAttachment(Operation.LOCALBACKENDOPERATIONS);

      if (localOperations != null)
      {
        for (LocalBackendDeleteOperation localOperation : localOperations)
        {
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

  /** {@inheritDoc} */
  @Override
  public void updateOperationErrMsgAndResCode()
  {
    setResultCode(ResultCode.NO_SUCH_OBJECT);
    appendErrorMessage(ERR_DELETE_NO_SUCH_ENTRY.get(getEntryDN()));
  }


  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  @Override
  public Entry getEntryToDelete() {
    return null;
  }

}
