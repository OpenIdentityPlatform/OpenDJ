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



import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ENTRY_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ERROR_MESSAGE;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_MATCHED_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_PROCESSING_TIME;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_REFERRAL_URLS;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_RESULT_CODE;
import static org.opends.server.loggers.AccessLogger.logModifyRequest;
import static org.opends.server.loggers.AccessLogger.logModifyResponse;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.types.LDAPException;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.Operation;
import org.opends.server.types.RawModification;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Modification;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PreParseModifyOperation;
import org.opends.server.workflowelement.localbackend.*;



/**
 * This class defines an operation that may be used to modify an entry in the
 * Directory Server.
 */
public class ModifyOperationBasis
       extends AbstractOperation implements ModifyOperation,
       PreParseModifyOperation,
       PostResponseModifyOperation
       {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  // The raw, unprocessed entry DN as included by the client request.
  private ByteString rawEntryDN;

  // The DN of the entry for the modify operation.
  private DN entryDN;

  // The proxied authorization target DN for this operation.
  private DN proxiedAuthorizationDN;

  // The set of response controls for this modify operation.
  private List<Control> responseControls;

  // The raw, unprocessed set of modifications as included in the client
  // request.
  private List<RawModification> rawModifications;

  // The set of modifications for this modify operation.
  private List<Modification> modifications;

  // The cancel request that has been issued for this modify operation.
  CancelRequest cancelRequest;

  // The change number that has been assigned to this operation.
  private long changeNumber;

  /**
   * Creates a new modify operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw, unprocessed DN of the entry to modify,
   *                           as included in the client request.
   * @param  rawModifications  The raw, unprocessed set of modifications for
   *                           this modify operation as included in the client
   *                           request.
   */
  public ModifyOperationBasis(ClientConnection clientConnection,
      long operationID,
      int messageID, List<Control> requestControls,
      ByteString rawEntryDN,
      List<RawModification> rawModifications)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN       = rawEntryDN;
    this.rawModifications = rawModifications;

    entryDN          = null;
    modifications    = null;
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
  }

  /**
   * Creates a new modify operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  entryDN           The entry DN for the modify operation.
   * @param  modifications     The set of modifications for this modify
   *                           operation.
   */
  public ModifyOperationBasis(ClientConnection clientConnection,
      long operationID,
      int messageID, List<Control> requestControls,
      DN entryDN, List<Modification> modifications)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN       = entryDN;
    this.modifications = modifications;

    rawEntryDN = new ASN1OctetString(entryDN.toString());

    rawModifications = new ArrayList<RawModification>(modifications.size());
    for (Modification m : modifications)
    {
      rawModifications.add(new LDAPModification(m.getModificationType(),
          new LDAPAttribute(m.getAttribute())));
    }

    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
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
    if (entryDN == null){
      try {
        entryDN = DN.decode(rawEntryDN);
      }
      catch (DirectoryException de) {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getErrorMessage());
      }
    }
    return entryDN;
  }

  /**
   * {@inheritDoc}
   */
  public final List<RawModification> getRawModifications()
  {
    return rawModifications;
  }

  /**
   * {@inheritDoc}
   */
  public final void addRawModification(RawModification rawModification)
  {
    rawModifications.add(rawModification);

    modifications = null;
  }

  /**
   * {@inheritDoc}
   */
  public final void setRawModifications(List<RawModification> rawModifications)
  {
    this.rawModifications = rawModifications;

    modifications = null;
  }

  /**
   * {@inheritDoc}
   */
  public final List<Modification> getModifications()
  {
    if (modifications == null)
    {
      modifications = new ArrayList<Modification>(rawModifications.size());
      try {
        for (RawModification m : rawModifications)
        {
          modifications.add(m.toModification());
        }
      }
      catch (LDAPException le)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, le);
        }
        setResultCode(ResultCode.valueOf(le.getResultCode()));
        appendErrorMessage(le.getMessage());
        modifications = null;
      }
    }
    return modifications;
  }

  /**
   * {@inheritDoc}
   */
  public final void addModification(Modification modification)
  throws DirectoryException
  {
    modifications.add(modification);
  }

  /**
   * {@inheritDoc}
   */
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
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.MODIFY;
  }

  /**
   * {@inheritDoc}
   */
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
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }

  /**
   * {@inheritDoc}
   */
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }

  /**
   * {@inheritDoc}
   */
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }

  /**
   * {@inheritDoc}
   */
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
  public final CancelRequest getCancelRequest()
  {
    return cancelRequest;
  }

  /**
   * {@inheritDoc}
   */
  public boolean setCancelRequest(CancelRequest cancelRequest)
  {
    this.cancelRequest = cancelRequest;
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public final void toString(StringBuilder buffer)
  {
    buffer.append("ModifyOperation(connID=");
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
  public final long getChangeNumber(){
    return changeNumber;
  }

  /**
   * {@inheritDoc}
   */
  public void setChangeNumber(long changeNumber)
  {
    this.changeNumber = changeNumber;
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
    if (getCancelRequest() != null)
    {
      indicateCancelled(getCancelRequest());
      setProcessingStopTime();
      return;
    }


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
modifyProcessing:
    {
      // Invoke the pre-parse modify plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseModifyPlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        setProcessingStopTime();

        logModifyRequest(this);
        logModifyResponse(this);
        pluginConfigManager.invokePostResponseModifyPlugins(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        logModifyRequest(this);
        break modifyProcessing;
      }
      else if (preParseResult.skipCoreProcessing())
      {
        break modifyProcessing;
      }

      // Log the modify request message.
      logModifyRequest(this);

      // Check for and handle a request to cancel this operation.
      if (getCancelRequest() != null)
      {
        indicateCancelled(getCancelRequest());
        setProcessingStopTime();
        pluginConfigManager.invokePostResponseModifyPlugins(this);
        return;
      }


      // Process the entry DN to convert it from the raw form to the form
      // required for the rest of the modify processing.
      DN entryDN = getEntryDN();
      if (entryDN == null){
        break modifyProcessing;
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
        break modifyProcessing;
      }
      workflow.execute(this);
    }

    // Check for and handle a request to cancel this operation.
    if ((getCancelRequest() != null) ||
        (getCancelResult() == CancelResult.CANCELED))
    {
      if (getCancelRequest() != null){
        indicateCancelled(getCancelRequest());
      }
      setProcessingStopTime();
      logModifyResponse(this);
      invokePostResponsePlugins();
      return;
    }

    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);

    // -- DONE AT A LOWER LEVEL --
    // Notify any change notification listeners that might be registered with
    // the server.

    // Stop the processing timer.
    setProcessingStopTime();

    // Send the modify response to the client.
    getClientConnection().sendResponse(this);

    // Log the modify response.
    logModifyResponse(this);

    // Check wether there are local operations in attachments
    List localOperations =
      (List)getAttachment(Operation.LOCALBACKENDOPERATIONS);
    if (localOperations != null && (! localOperations.isEmpty())){
      for (Object localOp : localOperations)
      {
        LocalBackendModifyOperation localOperation =
          (LocalBackendModifyOperation)localOp;
        // Notify any persistent searches that might be registered with
        // the server.
        if (localOperation.getResultCode() == ResultCode.SUCCESS)
        {
          for (PersistentSearch persistentSearch :
               DirectoryServer.getPersistentSearches())
          {
            try
            {
              persistentSearch.processModify(localOperation,
                  localOperation.getCurrentEntry(),
                  localOperation.getModifiedEntry());
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              int    msgID   = MSGID_MODIFY_ERROR_NOTIFYING_PERSISTENT_SEARCH;
              String message = getMessage(msgID,
                                          String.valueOf(persistentSearch),
                                          getExceptionMessage(e));
              logError(ErrorLogCategory.CORE_SERVER,
                       ErrorLogSeverity.SEVERE_ERROR,
                       message, msgID);

              DirectoryServer.deregisterPersistentSearch(persistentSearch);
            }
          }
        }

        // Invoke the post-response modify plugins.
        pluginConfigManager.invokePostResponseModifyPlugins(localOperation);
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
    appendErrorMessage(getMessage(MSGID_MODIFY_NO_SUCH_ENTRY,
                                  String.valueOf(getEntryDN())));
  }

  /**
   * Execute the postResponseModifyPlugins.
   */
  private void invokePostResponsePlugins()
  {
    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();

    // Check wether there are local operations in attachments
    List localOperations =
      (List)getAttachment(Operation.LOCALBACKENDOPERATIONS);

    if (localOperations != null && (! localOperations.isEmpty())){
      for (Object localOp : localOperations)
      {
        LocalBackendModifyOperation localOperation =
          (LocalBackendModifyOperation)localOp;
        // Invoke the post-response add plugins.
        pluginConfigManager.invokePostResponseModifyPlugins(localOperation);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public Entry getCurrentEntry() {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public List<AttributeValue> getCurrentPasswords()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public Entry getModifiedEntry()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public List<AttributeValue> getNewPasswords()
  {
    return null;
  }

}

