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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LockManager;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PreParseModifyDNOperation;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.AccessLogger.*;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation that may be used to alter the DN of an entry
 * in the Directory Server.
 */
public class ModifyDNOperation
       extends Operation
       implements PreParseModifyDNOperation, PreOperationModifyDNOperation,
                  PostOperationModifyDNOperation, PostResponseModifyDNOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

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

  // The cancel request issued for this modify DN operation.
  private CancelRequest cancelRequest;

  // The current DN of the entry.
  private DN entryDN;

  // The new parent for the entry.
  private DN newSuperior;

  // The proxied authorization target DN for this operation.
  private DN proxiedAuthorizationDN;

  // The current entry, before it is renamed.
  private Entry currentEntry;

  // The new entry, as it will appear after it has been renamed.
  private Entry newEntry;

  // The set of response controls for this modify DN operation.
  private List<Control> responseControls;

  // The set of modifications applied to attributes in the entry in the course
  // of processing the modify DN.
  private List<Modification> modifications;

  // The change number that has been assigned to this operation.
  private long changeNumber;

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;

  // The new RDN for the entry.
  private RDN newRDN;



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
  public ModifyDNOperation(ClientConnection clientConnection, long operationID,
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
    currentEntry     = null;
    newEntry         = null;
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
  public ModifyDNOperation(ClientConnection clientConnection, long operationID,
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
    currentEntry     = null;
    newEntry         = null;
  }



  /**
   * Retrieves the raw, unprocessed entry DN as included in the client request.
   * The DN that is returned may or may not be a valid DN, since no validation
   * will have been performed upon it.
   *
   * @return  The raw, unprocessed entry DN as included in the client request.
   */
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }



  /**
   * Specifies the raw, unprocessed entry DN as included in the client request.
   * This should only be called by pre-parse plugins.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in the client
   *                     request.
   */
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }



  /**
   * Retrieves the DN of the entry to rename.  This should not be called by
   * pre-parse plugins because the processed DN will not be available yet.
   * Instead, they should call the <CODE>getRawEntryDN</CODE> method.
   *
   * @return  The DN of the entry to rename, or <CODE>null</CODE> if the raw
   *          entry DN has not yet been processed.
   */
  public final DN getEntryDN()
  {
    return entryDN;
  }



  /**
   * Retrieves the raw, unprocessed newRDN as included in the request from the
   * client.  This may or may not contain a valid RDN, as no validation will
   * have been performed on it.
   *
   * @return  The raw, unprocessed newRDN as included in the request from the
   *          client.
   */
  public final ByteString getRawNewRDN()
  {
    return rawNewRDN;
  }



  /**
   * Specifies the raw, unprocessed newRDN as included in the request from the
   * client.  This should only be called by pre-parse plugins and should not be
   * used in later stages of processing.
   *
   * @param  rawNewRDN  The raw, unprocessed newRDN as included in the request
   *                    from the client.
   */
  public final void setRawNewRDN(ByteString rawNewRDN)
  {
    this.rawNewRDN = rawNewRDN;

    newRDN = null;
  }



  /**
   * Retrieves the new RDN to use for the entry.  This should not be called by
   * pre-parse plugins, because the processed newRDN will not yet be available.
   * Pre-parse plugins should instead use the <CODE>getRawNewRDN</CODE> method.
   *
   * @return  The new RDN to use for the entry, or <CODE>null</CODE> if the raw
   *          newRDN has not yet been processed.
   */
  public final RDN getNewRDN()
  {
    return newRDN;
  }



  /**
   * Indicates whether the current RDN value should be removed from the entry.
   *
   * @return  <CODE>true</CODE> if the current RDN value should be removed from
   *          the entry, or <CODE>false</CODE> if not.
   */
  public final boolean deleteOldRDN()
  {
    return deleteOldRDN;
  }



  /**
   * Specifies whether the current RDN value should be removed from the entry.
   *
   * @param  deleteOldRDN  Specifies whether the current RDN value should be
   *                       removed from the entry.
   */
  public final void setDeleteOldRDN(boolean deleteOldRDN)
  {
    this.deleteOldRDN = deleteOldRDN;
  }



  /**
   * Retrieves the raw, unprocessed newSuperior from the client request.  This
   * may or may not contain a valid DN, as no validation will have been
   * performed on it.
   *
   * @return  The raw, unprocessed newSuperior from the client request, or
   *          <CODE>null</CODE> if there is none.
   */
  public final ByteString getRawNewSuperior()
  {
    return rawNewSuperior;
  }



  /**
   * Specifies the raw, unprocessed newSuperior for this modify DN operation, as
   * provided in the request from the client.  This method should only be called
   * by pre-parse plugins.
   *
   * @param  rawNewSuperior  The raw, unprocessed newSuperior as provided in the
   *                         request from the client.
   */
  public final void setRawNewSuperior(ByteString rawNewSuperior)
  {
    this.rawNewSuperior = rawNewSuperior;

    newSuperior = null;
  }



  /**
   * Retrieves the newSuperior DN for the entry.  This should not be called by
   * pre-parse plugins, because the processed DN will not yet be available at
   * that time.  Instead, they should use the <CODE>getRawNewSuperior</CODE>
   * method.
   *
   * @return  The newSuperior DN for the entry, or <CODE>null</CODE> if there is
   *          no newSuperior DN for this request or if the raw newSuperior has
   *          not yet been processed.
   */
  public final DN getNewSuperior()
  {
    return newSuperior;
  }



  /**
   * Retrieves the set of modifications applied to attributes of the target
   * entry in the course of processing this modify DN operation.  This will
   * include attribute-level changes from the modify DN itself (e.g., removing
   * old RDN values if deleteOldRDN is set, or adding new RDN values that don't
   * already exist), but it may also be used by pre-operation plugins to cause
   * additional changes in the entry.  In this case, those plugins may add
   * modifications to this list (they may not remove existing modifications) if
   * any changes should be processed in addition to the core modify DN
   * processing.  Backends may read this list to identify which attribute-level
   * changes were applied in order to more easily apply updates to attribute
   * indexes.
   *
   * @return  The set of modifications applied to attributes during the course
   *          of the modify DN processing, or <CODE>null</CODE> if that
   *          information is not yet available (e.g., during pre-parse plugins).
   */
  public final List<Modification> getModifications()
  {
    return modifications;
  }



  /**
   * Adds the provided modification to the set of modifications to be applied
   * as part of the update.  This should only be called by pre-operation
   * plugins.
   *
   * @param  modification  The modification to add to the set of modifications
   *                       to apply to the entry.
   */
  public final void addModification(Modification modification)
  {
    modifications.add(modification);
  }



  /**
   * Retrieves the current entry, before it is renamed.  This will not be
   * available to pre-parse plugins or during the conflict resolution portion of
   * the synchronization processing.
   *
   * @return  The current entry, or <CODE>null</CODE> if it is not yet
   *           available.
   */
  public final Entry getOriginalEntry()
  {
    return currentEntry;
  }



  /**
   * Retrieves the new entry, as it will appear after it is renamed.  This will
   * not be  available to pre-parse plugins or during the conflict resolution
   * portion of the synchronization processing.
   *
   * @return  The updated entry, or <CODE>null</CODE> if it is not yet
   *           available.
   */
  public final Entry getUpdatedEntry()
  {
    return newEntry;
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
   * Retrieves the change number that has been assigned to this operation.
   *
   * @return  The change number that has been assigned to this operation, or -1
   *          if none has been assigned yet or if there is no applicable
   *          synchronization mechanism in place that uses change numbers.
   */
  public final long getChangeNumber()
  {
    return changeNumber;
  }



  /**
   * Specifies the change number that has been assigned to this operation by the
   * synchronization mechanism.
   *
   * @param  changeNumber  The change number that has been assigned to this
   *                       operation by the synchronization mechanism.
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
      new String[] { LOG_ELEMENT_DELETE_OLD_RDN, String.valueOf(deleteOldRDN) },
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
      indicateCancelled(cancelRequest);
      processingStopTime = System.currentTimeMillis();
      return;
    }


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
modifyDNProcessing:
    {
      // Invoke the pre-parse modify DN plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseModifyDNPlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logModifyDNRequest(this);
        logModifyDNResponse(this);
        pluginConfigManager.invokePostResponseModifyDNPlugins(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        logModifyDNRequest(this);
        break modifyDNProcessing;
      }
      else if (preParseResult.skipCoreProcessing())
      {
        skipPostOperation = false;
        break modifyDNProcessing;
      }


      // Log the modify DN request message.
      logModifyDNRequest(this);


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logModifyDNResponse(this);
        pluginConfigManager.invokePostResponseModifyDNPlugins(this);
        return;
      }


      // Process the entry DN, newRDN, and newSuperior elements from their raw
      // forms as provided by the client to the forms required for the rest of
      // the modify DN processing.
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
        appendErrorMessage(de.getErrorMessage());
        skipPostOperation = true;

        break modifyDNProcessing;
      }

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
        appendErrorMessage(de.getErrorMessage());
        skipPostOperation = true;

        break modifyDNProcessing;
      }

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
          appendErrorMessage(de.getErrorMessage());
          skipPostOperation = true;

          break modifyDNProcessing;
        }
      }


      // Construct the new DN to use for the entry.
      DN parentDN;
      if (newSuperior == null)
      {
        parentDN = entryDN.getParentDNInSuffix();
      }
      else
      {
        parentDN = newSuperior;
      }

      if ((parentDN == null) || parentDN.isNullDN())
      {
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(getMessage(MSGID_MODDN_NO_PARENT,
                                      String.valueOf(entryDN)));
        break modifyDNProcessing;
      }

      DN newDN = parentDN.concat(newRDN);

      // Get the backend for the current entry, and the backend for the new
      // entry.  If either is null, or if they are different, then fail.
      Backend currentBackend = DirectoryServer.getBackend(entryDN);
      if (currentBackend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(getMessage(MSGID_MODDN_NO_BACKEND_FOR_CURRENT_ENTRY,
                                      String.valueOf(entryDN)));
        break modifyDNProcessing;
      }

      Backend newBackend = DirectoryServer.getBackend(newDN);
      if (newBackend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(getMessage(MSGID_MODDN_NO_BACKEND_FOR_NEW_ENTRY,
                                      String.valueOf(entryDN),
                                      String.valueOf(newDN)));
        break modifyDNProcessing;
      }
      else if (! currentBackend.equals(newBackend))
      {
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(getMessage(MSGID_MODDN_DIFFERENT_BACKENDS,
                                      String.valueOf(entryDN),
                                      String.valueOf(newDN)));
        break modifyDNProcessing;
      }


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logModifyDNResponse(this);
        pluginConfigManager.invokePostResponseModifyDNPlugins(this);
        return;
      }


      // Acquire write locks for the current and new DN.
      Lock currentLock = null;
      for (int i=0; i < 3; i++)
      {
        currentLock = LockManager.lockWrite(entryDN);
        if (currentLock != null)
        {
          break;
        }
      }

      if (currentLock == null)
      {
        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(getMessage(MSGID_MODDN_CANNOT_LOCK_CURRENT_DN,
                                      String.valueOf(entryDN)));

        skipPostOperation = true;
        break modifyDNProcessing;
      }

      Lock newLock = null;
      try
      {
        for (int i=0; i < 3; i++)
        {
          newLock = LockManager.lockWrite(newDN);
          if (newLock != null)
          {
            break;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        LockManager.unlock(entryDN, currentLock);

        if (newLock != null)
        {
          LockManager.unlock(newDN, newLock);
        }

        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(getMessage(MSGID_MODDN_EXCEPTION_LOCKING_NEW_DN,
                                      String.valueOf(entryDN),
                                      String.valueOf(newDN),
                                      getExceptionMessage(e)));

        skipPostOperation = true;
        break modifyDNProcessing;
      }

      if (newLock == null)
      {
        LockManager.unlock(entryDN, currentLock);

        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(getMessage(MSGID_MODDN_CANNOT_LOCK_NEW_DN,
                                      String.valueOf(entryDN),
                                      String.valueOf(newDN)));

        skipPostOperation = true;
        break modifyDNProcessing;
      }


      try
      {
        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          logModifyDNResponse(this);
          pluginConfigManager.invokePostResponseModifyDNPlugins(this);
          return;
        }


        // Get the current entry from the appropriate backend.  If it doesn't
        // exist, then fail.
        try
        {
          currentEntry = currentBackend.getEntry(entryDN);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResultCode(de.getResultCode());
          appendErrorMessage(de.getErrorMessage());
          setMatchedDN(de.getMatchedDN());
          setReferralURLs(de.getReferralURLs());

          break modifyDNProcessing;
        }

        if (currentEntry == null)
        {
          // See if one of the entry's ancestors exists.
          parentDN = entryDN.getParentDNInSuffix();
          while (parentDN != null)
          {
            try
            {
              if (DirectoryServer.entryExists(parentDN))
              {
                setMatchedDN(parentDN);
                break;
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              break;
            }

            parentDN = parentDN.getParentDNInSuffix();
          }

          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(getMessage(MSGID_MODDN_NO_CURRENT_ENTRY,
                                        String.valueOf(entryDN)));

          break modifyDNProcessing;
        }


        // Invoke any conflict resolution processing that might be needed by the
        // synchronization provider.
        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            SynchronizationProviderResult result =
                 provider.handleConflictResolution(this);
            if (! result.continueOperationProcessing())
            {
              break modifyDNProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ErrorLogCategory.SYNCHRONIZATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_MODDN_SYNCH_CONFLICT_RESOLUTION_FAILED,
                     getConnectionID(), getOperationID(),
                     getExceptionMessage(de));

            setResponseData(de);
            break modifyDNProcessing;
          }
        }


        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        boolean                    noOp            = false;
        LDAPPreReadRequestControl  preReadRequest  = null;
        LDAPPostReadRequestControl postReadRequest = null;
        List<Control> requestControls = getRequestControls();
        if ((requestControls != null) && (! requestControls.isEmpty()))
        {
          for (int i=0; i < requestControls.size(); i++)
          {
            Control c   = requestControls.get(i);
            String  oid = c.getOID();

            if (oid.equals(OID_LDAP_ASSERTION))
            {
              LDAPAssertionRequestControl assertControl;
              if (c instanceof LDAPAssertionRequestControl)
              {
                assertControl = (LDAPAssertionRequestControl) c;
              }
              else
              {
                try
                {
                  assertControl = LDAPAssertionRequestControl.decodeControl(c);
                  requestControls.set(i, assertControl);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyDNProcessing;
                }
              }

              try
              {
                // FIXME -- We need to determine whether the current user has
                //          permission to make this determination.
                SearchFilter filter = assertControl.getSearchFilter();
                if (! filter.matchesEntry(currentEntry))
                {
                  setResultCode(ResultCode.ASSERTION_FAILED);

                  appendErrorMessage(getMessage(MSGID_MODDN_ASSERTION_FAILED,
                                                String.valueOf(entryDN)));

                  break modifyDNProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                setResultCode(ResultCode.PROTOCOL_ERROR);

                int msgID = MSGID_MODDN_CANNOT_PROCESS_ASSERTION_FILTER;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              de.getErrorMessage()));

                break modifyDNProcessing;
              }
            }
            else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
            {
              noOp = true;
            }
            else if (oid.equals(OID_LDAP_READENTRY_PREREAD))
            {
              if (c instanceof LDAPAssertionRequestControl)
              {
                preReadRequest = (LDAPPreReadRequestControl) c;
              }
              else
              {
                try
                {
                  preReadRequest = LDAPPreReadRequestControl.decodeControl(c);
                  requestControls.set(i, preReadRequest);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyDNProcessing;
                }
              }
            }
            else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
            {
              if (c instanceof LDAPAssertionRequestControl)
              {
                postReadRequest = (LDAPPostReadRequestControl) c;
              }
              else
              {
                try
                {
                  postReadRequest = LDAPPostReadRequestControl.decodeControl(c);
                  requestControls.set(i, postReadRequest);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyDNProcessing;
                }
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V1))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
              {
                int msgID = MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES;
                appendErrorMessage(getMessage(msgID));
                setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break modifyDNProcessing;
              }


              ProxiedAuthV1Control proxyControl;
              if (c instanceof ProxiedAuthV1Control)
              {
                proxyControl = (ProxiedAuthV1Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV1Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyDNProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break modifyDNProcessing;
              }

              if (AccessControlConfigManager.getInstance()
                      .getAccessControlHandler().isProxiedAuthAllowed(this,
                      authorizationEntry) == false) {
                setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

                int msgID = MSGID_MODDN_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN)));

                skipPostOperation = true;
                break modifyDNProcessing;
              }
              setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                proxiedAuthorizationDN = DN.nullDN();
              }
              else
              {
                proxiedAuthorizationDN = authorizationEntry.getDN();
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V2))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
              {
                int msgID = MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES;
                appendErrorMessage(getMessage(msgID));
                setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break modifyDNProcessing;
              }


              ProxiedAuthV2Control proxyControl;
              if (c instanceof ProxiedAuthV2Control)
              {
                proxyControl = (ProxiedAuthV2Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV2Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyDNProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break modifyDNProcessing;
              }
              if (AccessControlConfigManager.getInstance()
                  .getAccessControlHandler().isProxiedAuthAllowed(this,
                                                authorizationEntry) == false) {
                setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

                int msgID = MSGID_MODDN_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN)));

                skipPostOperation = true;
                break modifyDNProcessing;
              }


              setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                proxiedAuthorizationDN = DN.nullDN();
              }
              else
              {
                proxiedAuthorizationDN = authorizationEntry.getDN();
              }
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              Backend backend = DirectoryServer.getBackend(entryDN);
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

                int msgID = MSGID_MODDN_UNSUPPORTED_CRITICAL_CONTROL;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              oid));

                break modifyDNProcessing;
              }
            }
          }
        }


        // Check to see if the client has permission to perform the
        // modify DN.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry or new superior
        // already exists may have already exposed sensitive information
        // to the client.
        if (AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(this) == false) {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          int msgID = MSGID_MODDN_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
          appendErrorMessage(getMessage(msgID, String.valueOf(entryDN)));

          skipPostOperation = true;
          break modifyDNProcessing;
        }

        // Duplicate the entry and set its new DN.  Also, create an empty list
        // to hold the attribute-level modifications.
        newEntry = currentEntry.duplicate(false);
        newEntry.setDN(newDN);
        modifications = new ArrayList<Modification>();



        // If we should delete the old RDN values from the entry, then do so.
        if (deleteOldRDN)
        {
          RDN currentRDN = entryDN.getRDN();
          int numValues  = currentRDN.getNumValues();
          for (int i=0; i < numValues; i++)
          {
            LinkedHashSet<AttributeValue> valueSet =
                 new LinkedHashSet<AttributeValue>(1);
            valueSet.add(currentRDN.getAttributeValue(i));

            Attribute a = new Attribute(currentRDN.getAttributeType(i),
                                        currentRDN.getAttributeName(i),
                                        valueSet);

            // If the associated attribute type is marked NO-USER-MODIFICATION,
            // then refuse the update.
            if (a.getAttributeType().isNoUserModification())
            {
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                int msgID = MSGID_MODDN_OLD_RDN_ATTR_IS_NO_USER_MOD;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              a.getName()));
                break modifyDNProcessing;
              }
            }

            LinkedList<AttributeValue> missingValues =
                 new LinkedList<AttributeValue>();
            newEntry.removeAttribute(a, missingValues);

            if (missingValues.isEmpty())
            {
              modifications.add(new Modification(ModificationType.DELETE, a));
            }
          }
        }


        // Add the new RDN values to the entry.
        int newRDNValues = newRDN.getNumValues();
        for (int i=0; i < newRDNValues; i++)
        {
          LinkedHashSet<AttributeValue> valueSet =
               new LinkedHashSet<AttributeValue>(1);
          valueSet.add(newRDN.getAttributeValue(i));

          Attribute a = new Attribute(newRDN.getAttributeType(i),
                                      newRDN.getAttributeName(i),
                                      valueSet);

          LinkedList<AttributeValue> duplicateValues =
               new LinkedList<AttributeValue>();
          newEntry.addAttribute(a, duplicateValues);

          if (duplicateValues.isEmpty())
          {
            // If the associated attribute type is marked NO-USER-MODIFICATION,
            // then refuse the update.
            if (a.getAttributeType().isNoUserModification())
            {
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                int msgID = MSGID_MODDN_NEW_RDN_ATTR_IS_NO_USER_MOD;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              a.getName()));
                break modifyDNProcessing;
              }
            }
            else
            {
              modifications.add(new Modification(ModificationType.ADD, a));
            }
          }
        }


        // Make sure that the resulting entry is valid as per the server schema.
        if (DirectoryServer.checkSchema())
        {
          StringBuilder invalidReason = new StringBuilder();
          if (! newEntry.conformsToSchema(null, false, true, true,
                                          invalidReason))
          {
            setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
            appendErrorMessage(getMessage(MSGID_MODDN_VIOLATES_SCHEMA,
                                          String.valueOf(entryDN),
                                          String.valueOf(invalidReason)));
            break modifyDNProcessing;
          }

          for (int i=0; i < newRDNValues; i++)
          {
            AttributeType at = newRDN.getAttributeType(i);
            if (at.isObsolete())
            {
              setResultCode(ResultCode.CONSTRAINT_VIOLATION);
              appendErrorMessage(getMessage(MSGID_MODDN_NEWRDN_ATTR_IS_OBSOLETE,
                                            String.valueOf(entryDN),
                                            at.getNameOrOID()));
              break modifyDNProcessing;
            }
          }
        }


        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          logModifyDNResponse(this);
          pluginConfigManager.invokePostResponseModifyDNPlugins(this);
          return;
        }


        // Get a count of the current number of modifications.  The
        // pre-operation plugins may alter this list, and we need to be able to
        // identify which changes were made after they're done.
        int modCount = modifications.size();


        // If the operation is not a synchronization operation,
        // Invoke the pre-operation modify DN plugins.
        if (!isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
            pluginConfigManager.invokePreOperationModifyDNPlugins(this);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the request
            // and result and return.
            setResultCode(ResultCode.CANCELED);

            int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
            appendErrorMessage(getMessage(msgID));

            processingStopTime = System.currentTimeMillis();
            logModifyDNResponse(this);
            pluginConfigManager.invokePostResponseModifyDNPlugins(this);
            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break modifyDNProcessing;
          }
          else if (preOpResult.skipCoreProcessing())
          {
            skipPostOperation = false;
            break modifyDNProcessing;
          }
        }


        // Check to see if any of the pre-operation plugins made any changes to
        // the entry.  If so, then apply them.
        if (modifications.size() > modCount)
        {
          for (int i=modCount; i < modifications.size(); i++)
          {
            Modification m = modifications.get(i);
            Attribute    a = m.getAttribute();

            switch (m.getModificationType())
            {
              case ADD:
                LinkedList<AttributeValue> duplicateValues =
                     new LinkedList<AttributeValue>();
                newEntry.addAttribute(a, duplicateValues);
                break;
              case DELETE:
                LinkedList<AttributeValue> missingValues =
                     new LinkedList<AttributeValue>();
                newEntry.removeAttribute(a, missingValues);
                break;
              case REPLACE:
                duplicateValues = new LinkedList<AttributeValue>();
                newEntry.removeAttribute(a.getAttributeType(), a.getOptions());
                newEntry.addAttribute(a, duplicateValues);
                break;
              case INCREMENT:
                List<Attribute> attrList =
                     newEntry.getAttribute(a.getAttributeType(),
                                           a.getOptions());
                if ((attrList == null) || attrList.isEmpty())
                {
                  setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);

                  int msgID = MSGID_MODDN_PREOP_INCREMENT_NO_ATTR;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));

                  break modifyDNProcessing;
                }
                else if (attrList.size() > 1)
                {
                  setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  int msgID = MSGID_MODDN_PREOP_INCREMENT_MULTIPLE_VALUES;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));

                  break modifyDNProcessing;
                }

                LinkedHashSet<AttributeValue> values =
                     attrList.get(0).getValues();
                if ((values == null) || values.isEmpty())
                {
                  setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);

                  int msgID = MSGID_MODDN_PREOP_INCREMENT_NO_ATTR;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));

                  break modifyDNProcessing;
                }
                else if (values.size() > 1)
                {
                  setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  int msgID = MSGID_MODDN_PREOP_INCREMENT_MULTIPLE_VALUES;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));

                  break modifyDNProcessing;
                }

                long currentLongValue;
                try
                {
                  AttributeValue v = values.iterator().next();
                  currentLongValue = Long.parseLong(v.getStringValue());
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                  }

                  setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  int msgID = MSGID_MODDN_PREOP_INCREMENT_VALUE_NOT_INTEGER;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));

                  break modifyDNProcessing;
                }

                LinkedHashSet<AttributeValue> newValues = a.getValues();
                if ((newValues == null) || newValues.isEmpty())
                {
                  setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  int msgID = MSGID_MODDN_PREOP_INCREMENT_NO_AMOUNT;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));

                  break modifyDNProcessing;
                }
                else if (newValues.size() > 1)
                {
                  setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  int msgID = MSGID_MODDN_PREOP_INCREMENT_MULTIPLE_AMOUNTS;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));

                  break modifyDNProcessing;
                }

                long incrementAmount;
                try
                {
                  AttributeValue v = values.iterator().next();
                  incrementAmount = Long.parseLong(v.getStringValue());
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                  }

                  setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  int msgID = MSGID_MODDN_PREOP_INCREMENT_AMOUNT_NOT_INTEGER;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));

                  break modifyDNProcessing;
                }

                long newLongValue = currentLongValue + incrementAmount;
                ByteString newValueOS =
                     new ASN1OctetString(String.valueOf(newLongValue));

                newValues = new LinkedHashSet<AttributeValue>(1);
                newValues.add(new AttributeValue(a.getAttributeType(),
                                                 newValueOS));

                List<Attribute> newAttrList = new ArrayList<Attribute>(1);
                newAttrList.add(new Attribute(a.getAttributeType(),
                                              a.getName(), newValues));
                newEntry.putAttribute(a.getAttributeType(), newAttrList);

                break;
            }
          }


          // Make sure that the updated entry still conforms to the server
          // schema.
          if (DirectoryServer.checkSchema())
          {
            StringBuilder invalidReason = new StringBuilder();
            if (! newEntry.conformsToSchema(null, false, true, true,
                                            invalidReason))
            {
              setResultCode(ResultCode.OBJECTCLASS_VIOLATION);

              appendErrorMessage(getMessage(MSGID_MODDN_PREOP_VIOLATES_SCHEMA,
                                            String.valueOf(entryDN),
                                            String.valueOf(invalidReason)));
              break modifyDNProcessing;
            }
          }
        }


        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          logModifyDNResponse(this);
          pluginConfigManager.invokePostResponseModifyDNPlugins(this);
          return;
        }


        // Actually perform the modify DN operation.  This should include taking
        // care of any synchronization that might be needed.
        try
        {
          // If it is not a private backend, then check to see if the server or
          // backend is operating in read-only mode.
          if (! currentBackend.isPrivateBackend())
          {
            switch (DirectoryServer.getWritabilityMode())
            {
              case DISABLED:
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(getMessage(MSGID_MODDN_SERVER_READONLY,
                                              String.valueOf(entryDN)));
                break modifyDNProcessing;

              case INTERNAL_ONLY:
                if (! (isInternalOperation() || isSynchronizationOperation()))
                {
                  setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  appendErrorMessage(getMessage(MSGID_MODDN_SERVER_READONLY,
                                                String.valueOf(entryDN)));
                  break modifyDNProcessing;
                }
            }

            switch (currentBackend.getWritabilityMode())
            {
              case DISABLED:
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(getMessage(MSGID_MODDN_BACKEND_READONLY,
                                              String.valueOf(entryDN)));
                break modifyDNProcessing;

              case INTERNAL_ONLY:
                if (! (isInternalOperation() || isSynchronizationOperation()))
                {
                  setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  appendErrorMessage(getMessage(MSGID_MODDN_BACKEND_READONLY,
                                                String.valueOf(entryDN)));
                  break modifyDNProcessing;
                }
            }
          }


          if (noOp)
          {
            appendErrorMessage(getMessage(MSGID_MODDN_NOOP));

            // FIXME -- We must set a result code other than SUCCESS.
          }
          else
          {
            for (SynchronizationProvider provider :
                 DirectoryServer.getSynchronizationProviders())
            {
              try
              {
                SynchronizationProviderResult result =
                     provider.doPreOperation(this);
                if (! result.continueOperationProcessing())
                {
                  break modifyDNProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                logError(ErrorLogCategory.SYNCHRONIZATION,
                         ErrorLogSeverity.SEVERE_ERROR,
                         MSGID_MODDN_SYNCH_PREOP_FAILED, getConnectionID(),
                         getOperationID(), getExceptionMessage(de));

                setResponseData(de);
                break modifyDNProcessing;
              }
            }

            currentBackend.renameEntry(entryDN, newEntry, this);
          }

          if (preReadRequest != null)
          {
            Entry entry = currentEntry.duplicate(true);

            if (! preReadRequest.allowsAttribute(
                       DirectoryServer.getObjectClassAttributeType()))
            {
              entry.removeAttribute(
                   DirectoryServer.getObjectClassAttributeType());
            }

            if (! preReadRequest.returnAllUserAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getUserAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            if (! preReadRequest.returnAllOperationalAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getOperationalAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            // FIXME -- Check access controls on the entry to see if it should
            //          be returned or if any attributes need to be stripped
            //          out..
            SearchResultEntry searchEntry = new SearchResultEntry(entry);
            LDAPPreReadResponseControl responseControl =
                 new LDAPPreReadResponseControl(preReadRequest.getOID(),
                                                preReadRequest.isCritical(),
                                                searchEntry);

            responseControls.add(responseControl);
          }

          if (postReadRequest != null)
          {
            Entry entry = newEntry.duplicate(true);

            if (! postReadRequest.allowsAttribute(
                       DirectoryServer.getObjectClassAttributeType()))
            {
              entry.removeAttribute(
                   DirectoryServer.getObjectClassAttributeType());
            }

            if (! postReadRequest.returnAllUserAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getUserAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! postReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            if (! postReadRequest.returnAllOperationalAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getOperationalAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! postReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            // FIXME -- Check access controls on the entry to see if it should
            //          be returned or if any attributes need to be stripped
            //          out..
            SearchResultEntry searchEntry = new SearchResultEntry(entry);
            LDAPPostReadResponseControl responseControl =
                 new LDAPPostReadResponseControl(postReadRequest.getOID(),
                                                 postReadRequest.isCritical(),
                                                 searchEntry);

            responseControls.add(responseControl);
          }

          setResultCode(ResultCode.SUCCESS);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResultCode(de.getResultCode());
          appendErrorMessage(de.getErrorMessage());
          setMatchedDN(de.getMatchedDN());
          setReferralURLs(de.getReferralURLs());

          break modifyDNProcessing;
        }
        catch (CancelledOperationException coe)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, coe);
          }

          CancelResult cancelResult = coe.getCancelResult();

          setCancelResult(cancelResult);
          setResultCode(cancelResult.getResultCode());

          String message = coe.getMessage();
          if ((message != null) && (message.length() > 0))
          {
            appendErrorMessage(message);
          }

          break modifyDNProcessing;
        }
      }
      finally
      {
        LockManager.unlock(entryDN, currentLock);
        LockManager.unlock(newDN, newLock);

        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            provider.doPostOperation(this);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ErrorLogCategory.SYNCHRONIZATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_MODDN_SYNCH_POSTOP_FAILED, getConnectionID(),
                     getOperationID(), getExceptionMessage(de));

            setResponseData(de);
            break;
          }
        }
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);


    // Invoke the post-operation modify DN plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOperationResult =
           pluginConfigManager.invokePostOperationModifyDNPlugins(this);
      if (postOperationResult.connectionTerminated())
      {
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_POSTOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();
        logModifyDNResponse(this);
        pluginConfigManager.invokePostResponseModifyDNPlugins(this);
        return;
      }
    }


    // Notify any change notification listeners that might be registered with
    // the server.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      for (ChangeNotificationListener changeListener :
           DirectoryServer.getChangeNotificationListeners())
      {
        try
        {
          changeListener.handleModifyDNOperation(this, currentEntry, newEntry);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_MODDN_ERROR_NOTIFYING_CHANGE_LISTENER;
          String message = getMessage(msgID, getExceptionMessage(e));
          logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
      }
    }


    // Stop the processing timer.
    processingStopTime = System.currentTimeMillis();


    // Send the modify DN response to the client.
    clientConnection.sendResponse(this);


    // Log the modify DN response.
    logModifyDNResponse(this);


    // Notify any persistent searches that might be registered with the server.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      for (PersistentSearch persistentSearch :
           DirectoryServer.getPersistentSearches())
      {
        try
        {
          persistentSearch.processModifyDN(this, currentEntry, newEntry);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_MODDN_ERROR_NOTIFYING_PERSISTENT_SEARCH;
          String message = getMessage(msgID, String.valueOf(persistentSearch),
                                      getExceptionMessage(e));
          logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);

          DirectoryServer.deregisterPersistentSearch(persistentSearch);
        }
      }
    }


    // Invoke the post-response modify DN plugins.
    pluginConfigManager.invokePostResponseModifyDNPlugins(this);
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
}

