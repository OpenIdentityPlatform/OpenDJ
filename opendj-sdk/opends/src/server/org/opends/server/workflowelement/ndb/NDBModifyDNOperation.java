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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.ndb;



import com.mysql.cluster.ndbj.NdbOperation;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.backends.ndb.AbstractTransaction;
import org.opends.server.backends.ndb.BackendImpl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PostSynchronizationModifyDNOperation;

import
  org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import
  org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation used to move an entry in a NDB backend
 * of the Directory Server.
 */
public class NDBModifyDNOperation
  extends LocalBackendModifyDNOperation
  implements PreOperationModifyDNOperation,
             PostOperationModifyDNOperation,
             PostResponseModifyDNOperation,
             PostSynchronizationModifyDNOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates a new operation that may be used to move an entry in a
   * NDB backend of the Directory Server.
   *
   * @param operation The operation to enhance.
   */
  public NDBModifyDNOperation (ModifyDNOperation operation)
  {
    super(operation);
    NDBWorkflowElement.attachLocalOperation (operation, this);
  }



  /**
   * Process this modify DN operation in a NDB backend.
   *
   * @param  wfe The local backend work-flow element.
   *
   * @throws CanceledOperationException if this operation should be
   * cancelled
   */
  @Override
  public void processLocalModifyDN(final LocalBackendWorkflowElement wfe)
    throws CanceledOperationException {
    boolean executePostOpPlugins = false;

    this.backend = wfe.getBackend();
    BackendImpl ndbBackend = (BackendImpl) backend;

    clientConnection = getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
modifyDNProcessing:
    {
      // Process the entry DN, newRDN, and newSuperior elements from their raw
      // forms as provided by the client to the forms required for the rest of
      // the modify DN processing.
      entryDN = getEntryDN();

      newRDN = getNewRDN();
      if (newRDN == null)
      {
        break modifyDNProcessing;
      }

      DN newSuperior = getNewSuperior();
      if ((newSuperior == null) &&
          (getRawNewSuperior() != null))
      {
        break modifyDNProcessing;
      }

      // Construct the new DN to use for the entry.
      DN parentDN;
      if (newSuperior == null)
      {
        parentDN = entryDN.getParentDNInSuffix();
      }
      else
      {
        if(newSuperior.isDescendantOf(entryDN))
        {
          setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          appendErrorMessage(ERR_MODDN_NEW_SUPERIOR_IN_SUBTREE.get(
              String.valueOf(entryDN), String.valueOf(newSuperior)));
          break modifyDNProcessing;
        }
        parentDN = newSuperior;
      }

      if ((parentDN == null) || parentDN.isNullDN())
      {
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(ERR_MODDN_NO_PARENT.get(String.valueOf(entryDN)));
        break modifyDNProcessing;
      }

      DN newDN = parentDN.concat(newRDN);

      // Get the backend for the current entry, and the backend for the new
      // entry.  If either is null, or if they are different, then fail.
      Backend currentBackend = backend;
      if (currentBackend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_MODDN_NO_BACKEND_FOR_CURRENT_ENTRY.get(
                                String.valueOf(entryDN)));
        break modifyDNProcessing;
      }

      Backend newBackend = DirectoryServer.getBackend(newDN);
      if (newBackend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_MODDN_NO_BACKEND_FOR_NEW_ENTRY.get(
                                String.valueOf(entryDN),
                                String.valueOf(newDN)));
        break modifyDNProcessing;
      }
      else if (! currentBackend.equals(newBackend))
      {
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(ERR_MODDN_DIFFERENT_BACKENDS.get(
                                String.valueOf(entryDN),
                                String.valueOf(newDN)));
        break modifyDNProcessing;
      }


      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      AbstractTransaction txn =
        new AbstractTransaction(ndbBackend.getRootContainer());

      try
      {
        // Get the current entry from the appropriate backend.  If it doesn't
        // exist, then fail.
        try
        {
          currentEntry = ndbBackend.getEntryNoCommit(entryDN, txn,
            NdbOperation.LockMode.LM_Exclusive);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyDNProcessing;
        }

        if (getOriginalEntry() == null)
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
          appendErrorMessage(ERR_MODDN_NO_CURRENT_ENTRY.get(
                                  String.valueOf(entryDN)));
          break modifyDNProcessing;
        }

        if(!handleConflictResolution()) {
            break modifyDNProcessing;
        }


        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        try
        {
          handleRequestControls();
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyDNProcessing;
        }


        // Check to see if the client has permission to perform the
        // modify DN.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry or new superior
        // already exists may have already exposed sensitive information
        // to the client.
        try
        {
          if (!AccessControlConfigManager.getInstance()
              .getAccessControlHandler().isAllowed(this))
          {
            setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
            appendErrorMessage(ERR_MODDN_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS
                .get(String.valueOf(entryDN)));
            break modifyDNProcessing;
          }
        }
        catch (DirectoryException e)
        {
          setResultCode(e.getResultCode());
          appendErrorMessage(e.getMessageObject());
          break modifyDNProcessing;
        }

        // Duplicate the entry and set its new DN.  Also, create an empty list
        // to hold the attribute-level modifications.
        newEntry = currentEntry.duplicate(false);
        newEntry.setDN(newDN);

        // init the modifications
        addModification(null);
        List<Modification> modifications = this.getModifications();



        // Apply any changes to the entry based on the change in its RDN.  Also,
        // perform schema checking on the updated entry.
        try
        {
          applyRDNChanges(modifications);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyDNProcessing;
        }


        // Check for a request to cancel this operation.
        checkIfCanceled(false);

        // Get a count of the current number of modifications.  The
        // pre-operation plugins may alter this list, and we need to be able to
        // identify which changes were made after they're done.
        int modCount = modifications.size();


        // If the operation is not a synchronization operation,
        // Invoke the pre-operation modify DN plugins.
        if (! isSynchronizationOperation())
        {
          executePostOpPlugins = true;
          PluginResult.PreOperation preOpResult =
              pluginConfigManager.invokePreOperationModifyDNPlugins(this);
          if (!preOpResult.continueProcessing())
          {
            setResultCode(preOpResult.getResultCode());
            appendErrorMessage(preOpResult.getErrorMessage());
            setMatchedDN(preOpResult.getMatchedDN());
            setReferralURLs(preOpResult.getReferralURLs());
            break modifyDNProcessing;
          }
        }


        // Check to see if any of the pre-operation plugins made any changes to
        // the entry.  If so, then apply them.
        if (modifications.size() > modCount)
        {
          try
          {
            applyPreOpModifications(modifications, modCount);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            setResponseData(de);
            break modifyDNProcessing;
          }
        }


        // Actually perform the modify DN operation.
        // This should include taking
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
                appendErrorMessage(ERR_MODDN_SERVER_READONLY.get(
                                        String.valueOf(entryDN)));
                break modifyDNProcessing;

              case INTERNAL_ONLY:
                if (! (isInternalOperation() || isSynchronizationOperation()))
                {
                  setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  appendErrorMessage(ERR_MODDN_SERVER_READONLY.get(
                                          String.valueOf(entryDN)));
                  break modifyDNProcessing;
                }
            }

            switch (currentBackend.getWritabilityMode())
            {
              case DISABLED:
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(ERR_MODDN_BACKEND_READONLY.get(
                                        String.valueOf(entryDN)));
                break modifyDNProcessing;

              case INTERNAL_ONLY:
                if (! (isInternalOperation() || isSynchronizationOperation()))
                {
                  setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  appendErrorMessage(ERR_MODDN_BACKEND_READONLY.get(
                                          String.valueOf(entryDN)));
                  break modifyDNProcessing;
                }
            }
          }


          if (noOp)
          {
            appendErrorMessage(INFO_MODDN_NOOP.get());
            setResultCode(ResultCode.NO_OPERATION);
          }
          else
          {
              if(!processPreOperation()) {
                  break modifyDNProcessing;
              }
              ndbBackend.renameEntry(entryDN, newEntry, this, txn);
          }


          // Attach the pre-read and/or post-read controls to the response if
          // appropriate.
          processReadEntryControls();


          if (! noOp)
          {
            setResultCode(ResultCode.SUCCESS);
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyDNProcessing;
        }
      }
      finally
      {
        processSynchPostOperationPlugins();
        try {
          txn.close();
        } catch (Exception ex) {
          if (debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, ex);
          }
        }
      }
    }

    // Invoke the post-operation or post-synchronization modify DN plugins.
    if (isSynchronizationOperation())
    {
      if (getResultCode() == ResultCode.SUCCESS)
      {
        pluginConfigManager.invokePostSynchronizationModifyDNPlugins(this);
      }
    }
    else if (executePostOpPlugins)
    {
      PluginResult.PostOperation postOpResult =
           pluginConfigManager.invokePostOperationModifyDNPlugins(this);
      if (!postOpResult.continueProcessing())
      {
        setResultCode(postOpResult.getResultCode());
        appendErrorMessage(postOpResult.getErrorMessage());
        setMatchedDN(postOpResult.getMatchedDN());
        setReferralURLs(postOpResult.getReferralURLs());
        return;
      }
    }


    // Register a post-response call-back which will notify persistent
    // searches and change listeners.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      registerPostResponseCallback(new Runnable()
      {

        public void run()
        {
          // Notify change listeners.
          for (ChangeNotificationListener changeListener : DirectoryServer
              .getChangeNotificationListeners())
          {
            try
            {
              changeListener.handleModifyDNOperation(
                  NDBModifyDNOperation.this, currentEntry, newEntry);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              Message message = ERR_MODDN_ERROR_NOTIFYING_CHANGE_LISTENER
                  .get(getExceptionMessage(e));
              logError(message);
            }
          }
        }
      });
    }
  }
}
