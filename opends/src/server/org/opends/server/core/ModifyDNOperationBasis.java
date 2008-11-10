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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.MessageBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.types.operation.PreParseModifyDNOperation;
import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.AccessLogger.*;

import org.opends.server.types.*;
import org.opends.server.workflowelement.localbackend.*;

import static org.opends.server.loggers.debug.DebugLogger.*;

import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.CoreMessages.*;


/**
 * This class defines an operation that may be used to alter the DN of an entry
 * in the Directory Server.
 */
public class ModifyDNOperationBasis
       extends AbstractOperation
       implements ModifyDNOperation,
                  PreParseModifyDNOperation,
                  PostResponseModifyDNOperation
                  {
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  // Indicates whether to delete the old RDN value from the entry.
  private boolean deleteOldRDN;

  // The raw, unprocessed current DN of the entry as included in the request
  // from the client.
  private ByteString rawEntryDN;

  // The raw, unprocessed newRDN as included in the request from the client.
  private ByteString rawNewRDN;

  // The raw, unprocessed newSuperior as included in the request from the
  // client.
  private ByteString rawNewSuperior;

  // The current DN of the entry.
  private DN entryDN;

  // The new parent for the entry.
  private DN newSuperior;

  // The proxied authorization target DN for this operation.
  private DN proxiedAuthorizationDN;

  // The set of response controls for this modify DN operation.
  private List<Control> responseControls;

  // The set of modifications applied to attributes in the entry in the course
  // of processing the modify DN.
  private List<Modification> modifications;

  // The change number that has been assigned to this operation.
  private long changeNumber;

  // The new RDN for the entry.
  private RDN newRDN;

  // The new entry DN
  private DN newDN = null;

  /**
   * Creates a new modify DN operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw, unprocessed entry DN as included in the
   *                           client request.
   * @param  rawNewRDN         The raw, unprocessed newRDN as included in the
   *                           client request.
   * @param  deleteOldRDN      Indicates whether to delete the old RDN value
   *                           from the entry.
   * @param  rawNewSuperior    The raw, unprocessed newSuperior as included in
   *                           the client request.
   */
  public ModifyDNOperationBasis(ClientConnection clientConnection,
      long operationID,
      int messageID, List<Control> requestControls,
      ByteString rawEntryDN, ByteString rawNewRDN,
      boolean deleteOldRDN, ByteString rawNewSuperior)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN      = rawEntryDN;
    this.rawNewRDN       = rawNewRDN;
    this.deleteOldRDN    = deleteOldRDN;
    this.rawNewSuperior  = rawNewSuperior;

    entryDN          = null;
    newRDN           = null;
    newSuperior      = null;
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
    modifications    = null;
    changeNumber     = -1;
  }



  /**
   * Creates a new modify DN operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  entryDN           The current entry DN for this modify DN
   *                           operation.
   * @param  newRDN            The new RDN for this modify DN operation.
   * @param  deleteOldRDN      Indicates whether to delete the old RDN value
   *                           from the entry.
   * @param  newSuperior       The newSuperior DN for this modify DN operation.
   */
  public ModifyDNOperationBasis(ClientConnection clientConnection,
      long operationID,
      int messageID, List<Control> requestControls,
      DN entryDN, RDN newRDN, boolean deleteOldRDN,
      DN newSuperior)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN      = entryDN;
    this.newRDN       = newRDN;
    this.deleteOldRDN = deleteOldRDN;
    this.newSuperior  = newSuperior;

    rawEntryDN = new ASN1OctetString(entryDN.toString());
    rawNewRDN  = new ASN1OctetString(newRDN.toString());

    if (newSuperior == null)
    {
      rawNewSuperior = null;
    }
    else
    {
      rawNewSuperior = new ASN1OctetString(newSuperior.toString());
    }

    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
    modifications    = null;
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
    }
    return entryDN;
  }

  /**
   * {@inheritDoc}
   */
  public final ByteString getRawNewRDN()
  {
    return rawNewRDN;
  }

  /**
   * {@inheritDoc}
   */
  public final void setRawNewRDN(ByteString rawNewRDN)
  {
    this.rawNewRDN = rawNewRDN;

    newRDN = null;
    newDN = null;
  }

  /**
   * {@inheritDoc}
   */
  public final RDN getNewRDN()
  {
    try
    {
      if (newRDN == null)
      {
        newRDN = RDN.decode(rawNewRDN.stringValue());
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
    }
    return newRDN;
  }


  /**
   * {@inheritDoc}
   */
  public final boolean deleteOldRDN()
  {
    return deleteOldRDN;
  }

  /**
   * {@inheritDoc}
   */
  public final void setDeleteOldRDN(boolean deleteOldRDN)
  {
    this.deleteOldRDN = deleteOldRDN;
  }

  /**
   * {@inheritDoc}
   */
  public final ByteString getRawNewSuperior()
  {
    return rawNewSuperior;
  }

  /**
   * {@inheritDoc}
   */
  public final void setRawNewSuperior(ByteString rawNewSuperior)
  {
    this.rawNewSuperior = rawNewSuperior;

    newSuperior = null;
    newDN = null;
  }

  /**
   * {@inheritDoc}
   */
  public final DN getNewSuperior()
  {
    if (rawNewSuperior == null)
    {
      newSuperior = null;
    }
    else
    {
      try
      {
        if (newSuperior == null)
        {
          newSuperior = DN.decode(rawNewSuperior);
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
      }
    }
    return newSuperior;
  }


  /**
   * {@inheritDoc}
   */
  public final List<Modification> getModifications()
  {
    return modifications;
  }


  /**
   * {@inheritDoc}
   */
  public final void addModification(Modification modification)
  {
    if (modifications == null)
    {
      modifications = new ArrayList<Modification>();
    }
    if (modification != null)
    {
      modifications.add(modification);
    }
  }


  /**
   * {@inheritDoc}
   */
  public final Entry getOriginalEntry()
  {
    return null;
  }


  /**
   * {@inheritDoc}
   */
  public final Entry getUpdatedEntry()
  {
    return null;
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

    return OperationType.MODIFY_DN;
  }


  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    String newSuperiorStr;
    if (rawNewSuperior == null)
    {
      newSuperiorStr = null;
    }
    else
    {
      newSuperiorStr = rawNewSuperior.stringValue();
    }

    return new String[][]
                        {
        new String[] { LOG_ELEMENT_ENTRY_DN, String.valueOf(rawEntryDN) },
        new String[] { LOG_ELEMENT_NEW_RDN, String.valueOf(newRDN) },
        new String[] { LOG_ELEMENT_DELETE_OLD_RDN,
            String.valueOf(deleteOldRDN) },
        new String[] { LOG_ELEMENT_NEW_SUPERIOR, newSuperiorStr }
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

    // Log the modify DN request message.
    logModifyDNRequest(this);

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
        DirectoryServer.getPluginConfigManager();

    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;


    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the pre-parse modify DN plugins.
      PluginResult.PreParse preParseResult =
          pluginConfigManager.invokePreParseModifyDNPlugins(this);

      if(!preParseResult.continueProcessing())
      {
        setResultCode(preParseResult.getResultCode());
        appendErrorMessage(preParseResult.getErrorMessage());
        setMatchedDN(preParseResult.getMatchedDN());
        setReferralURLs(preParseResult.getReferralURLs());
        return;
      }

      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Process the entry DN, newRDN, and newSuperior elements from their raw
      // forms as provided by the client to the forms required for the rest of
      // the modify DN processing.
      DN entryDN = getEntryDN();
      if (entryDN == null)
      {
        return;
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
        return;
      }
      workflow.execute(this);
      workflowExecuted = true;
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

      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELED ||
          cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.notifyAbandonedOperations())
      {
        clientConnection.sendResponse(this);
      }

      // Log the modify DN response.
      logModifyDNResponse(this);

      // Invoke the post-response callbacks.
      if (workflowExecuted) {
        invokePostResponseCallbacks();
      }

      // Invoke the post-response modify DN plugins.
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
          LocalBackendModifyDNOperation localOperation =
            (LocalBackendModifyDNOperation)localOp;
          pluginConfigManager.invokePostResponseModifyDNPlugins(localOperation);
        }
      }
    }
    else
    {
      // Invoke the post response plugins that have been registered with
      // the current operation
      pluginConfigManager.invokePostResponseModifyDNPlugins(this);
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
    appendErrorMessage(ERR_MODDN_NO_BACKEND_FOR_CURRENT_ENTRY.get(
            String.valueOf(entryDN)));
  }


  /**
   * {@inheritDoc}
   */
  @Override()
  public final void toString(StringBuilder buffer)
  {
    buffer.append("ModifyDNOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(", newRDN=");
    buffer.append(rawNewRDN);
    buffer.append(", deleteOldRDN=");
    buffer.append(deleteOldRDN);

    if (rawNewSuperior != null)
    {
      buffer.append(", newSuperior=");
      buffer.append(rawNewSuperior);
    }
    buffer.append(")");
  }


  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN dn)
  {
    proxiedAuthorizationDN = dn;
  }


  /**
   * {@inheritDoc}
   */
  public DN getNewDN()
  {
    if (newDN == null)
    {
      // Construct the new DN to use for the entry.
      DN parentDN = null;
      if (newSuperior == null)
      {
        if (getEntryDN() != null)
        {
          parentDN = entryDN.getParentDNInSuffix();
        }
      }
      else
      {
        parentDN = newSuperior;
      }

      if ((parentDN == null) || parentDN.isNullDN())
      {
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(ERR_MODDN_NO_PARENT.get(String.valueOf(entryDN)));
      }
      newDN = parentDN.concat(newRDN);
    }
    return newDN;
  }

}

