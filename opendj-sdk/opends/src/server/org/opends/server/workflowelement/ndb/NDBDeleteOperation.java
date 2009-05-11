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

import org.opends.messages.Message;
import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.backends.ndb.AbstractTransaction;
import org.opends.server.backends.ndb.BackendImpl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.types.operation.PostSynchronizationDeleteOperation;

import
  org.opends.server.workflowelement.localbackend.LocalBackendDeleteOperation;
import
  org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation used to delete an entry in a NDB backend
 * of the Directory Server.
 */
public class NDBDeleteOperation
       extends LocalBackendDeleteOperation
       implements PreOperationDeleteOperation, PostOperationDeleteOperation,
                  PostResponseDeleteOperation,
                  PostSynchronizationDeleteOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates a new operation that may be used to delete an entry from a
   * NDB backend of the Directory Server.
   *
   * @param delete The operation to enhance.
   */
  public NDBDeleteOperation(DeleteOperation delete)
  {
    super(delete);
    NDBWorkflowElement.attachLocalOperation (delete, this);
  }



  /**
   * Process this delete operation in a NDB backend.
   *
   * @param  wfe The local backend work-flow element.
   *
   * @throws CanceledOperationException if this operation should be
   * cancelled
   */
  @Override
  public void processLocalDelete(final LocalBackendWorkflowElement wfe)
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
deleteProcessing:
    {
      // Process the entry DN to convert it from its raw form as provided by the
      // client to the form required for the rest of the delete processing.
      entryDN = getEntryDN();
      if (entryDN == null){
        break deleteProcessing;
      }

      AbstractTransaction txn =
        new AbstractTransaction(ndbBackend.getRootContainer());

      try
      {
        // Get the entry to delete.  If it doesn't exist, then fail.
        try
        {
          entry = ndbBackend.getEntryNoCommit(entryDN, txn,
            NdbOperation.LockMode.LM_Exclusive);

          if (entry == null)
          {
            setResultCode(ResultCode.NO_SUCH_OBJECT);
            appendErrorMessage(ERR_DELETE_NO_SUCH_ENTRY.get(
                                    String.valueOf(entryDN)));

            try
            {
              DN parentDN = entryDN.getParentDNInSuffix();
              while (parentDN != null)
              {
                if (DirectoryServer.entryExists(parentDN))
                {
                  setMatchedDN(parentDN);
                  break;
                }

                parentDN = parentDN.getParentDNInSuffix();
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }

            break deleteProcessing;
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break deleteProcessing;
        }

        if(!handleConflictResolution()) {
            break deleteProcessing;
        }

        // Check to see if the client has permission to perform the
        // delete.

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
          break deleteProcessing;
        }


        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists may
        // have already exposed sensitive information to the client.
        try
        {
          if (!AccessControlConfigManager.getInstance()
              .getAccessControlHandler().isAllowed(this))
          {
            setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
            appendErrorMessage(ERR_DELETE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS
                .get(String.valueOf(entryDN)));
            break deleteProcessing;
          }
        }
        catch (DirectoryException e)
        {
          setResultCode(e.getResultCode());
          appendErrorMessage(e.getMessageObject());
          break deleteProcessing;
        }

        // Check for a request to cancel this operation.
        checkIfCanceled(false);


        // If the operation is not a synchronization operation,
        // invoke the pre-delete plugins.
        if (! isSynchronizationOperation())
        {
          executePostOpPlugins = true;
          PluginResult.PreOperation preOpResult =
               pluginConfigManager.invokePreOperationDeletePlugins(this);
          if (!preOpResult.continueProcessing())
          {
            setResultCode(preOpResult.getResultCode());
            appendErrorMessage(preOpResult.getErrorMessage());
            setMatchedDN(preOpResult.getMatchedDN());
            setReferralURLs(preOpResult.getReferralURLs());
            break deleteProcessing;
          }
        }


        // Get the backend to use for the delete.  If there is none, then fail.
        if (backend == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(ERR_DELETE_NO_SUCH_ENTRY.get(
                                  String.valueOf(entryDN)));
          break deleteProcessing;
        }


        // If it is not a private backend, then check to see if the server or
        // backend is operating in read-only mode.
        if (! backend.isPrivateBackend())
        {
          switch (DirectoryServer.getWritabilityMode())
          {
            case DISABLED:
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_DELETE_SERVER_READONLY.get(
                                      String.valueOf(entryDN)));
              break deleteProcessing;

            case INTERNAL_ONLY:
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(ERR_DELETE_SERVER_READONLY.get(
                                        String.valueOf(entryDN)));
                break deleteProcessing;
              }
          }

          switch (backend.getWritabilityMode())
          {
            case DISABLED:
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_DELETE_BACKEND_READONLY.get(
                                      String.valueOf(entryDN)));
              break deleteProcessing;

            case INTERNAL_ONLY:
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(ERR_DELETE_BACKEND_READONLY.get(
                                        String.valueOf(entryDN)));
                break deleteProcessing;
              }
          }
        }


        // The selected backend will have the responsibility of making sure that
        // the entry actually exists and does not have any children (or possibly
        // handling a subtree delete).  But we will need to check if there are
        // any subordinate backends that should stop us from attempting the
        // delete.
        Backend[] subBackends = backend.getSubordinateBackends();
        for (Backend b : subBackends)
        {
          DN[] baseDNs = b.getBaseDNs();
          for (DN dn : baseDNs)
          {
            if (dn.isDescendantOf(entryDN))
            {
              setResultCode(ResultCode.NOT_ALLOWED_ON_NONLEAF);
              appendErrorMessage(ERR_DELETE_HAS_SUB_BACKEND.get(
                                      String.valueOf(entryDN),
                                      String.valueOf(dn)));
              break deleteProcessing;
            }
          }
        }


        // Actually perform the delete.
        try
        {
          if (noOp)
          {
            setResultCode(ResultCode.NO_OPERATION);
            appendErrorMessage(INFO_DELETE_NOOP.get());
          }
          else
          {
              if(!processPreOperation()) {
                  break deleteProcessing;
              }
              ndbBackend.deleteEntry(entryDN, entry, this, txn);
          }


          processPreReadControl();


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
          break deleteProcessing;
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

    // Invoke the post-operation or post-synchronization delete plugins.
    if (isSynchronizationOperation())
    {
      if (getResultCode() == ResultCode.SUCCESS)
      {
        pluginConfigManager.invokePostSynchronizationDeletePlugins(this);
      }
    }
    else if (executePostOpPlugins)
    {
      PluginResult.PostOperation postOpResult =
          pluginConfigManager.invokePostOperationDeletePlugins(this);
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
              changeListener.handleDeleteOperation(
                  NDBDeleteOperation.this, entry);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              Message message = ERR_DELETE_ERROR_NOTIFYING_CHANGE_LISTENER
                  .get(getExceptionMessage(e));
              logError(message);
            }
          }
        }
      });
    }
  }
}
