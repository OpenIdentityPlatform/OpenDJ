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
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;


import org.opends.messages.MessageBuilder;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.backends.ndb.AbstractTransaction;
import org.opends.server.backends.ndb.BackendImpl;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostSynchronizationModifyOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.util.TimeThread;
import
  org.opends.server.workflowelement.localbackend.LocalBackendModifyOperation;
import
  org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;



/**
 * This class defines an operation used to modify an entry in a NDB backend
 * of the Directory Server.
 */
public class NDBModifyOperation
       extends LocalBackendModifyOperation
       implements PreOperationModifyOperation, PostOperationModifyOperation,
                  PostResponseModifyOperation,
                  PostSynchronizationModifyOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates a new operation that may be used to modify an entry in a
   * NDB backend of the Directory Server.
   *
   * @param modify The operation to enhance.
   */
  public NDBModifyOperation(ModifyOperation modify)
  {
    super(modify);
    NDBWorkflowElement.attachLocalOperation (modify, this);
  }



  /**
   * Process this modify operation against a NDB backend.
   *
   * @param  wfe The local backend work-flow element.
   *
   * @throws CanceledOperationException if this operation should be
   * cancelled
   */
  @Override
  public void processLocalModify(final LocalBackendWorkflowElement wfe)
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
modifyProcessing:
    {
      entryDN = getEntryDN();
      if (entryDN == null){
        break modifyProcessing;
      }

      // Process the modifications to convert them from their raw form to the
      // form required for the rest of the modify processing.
      modifications = getModifications();
      if (modifications == null)
      {
        break modifyProcessing;
      }

      if (modifications.isEmpty())
      {
        setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        appendErrorMessage(ERR_MODIFY_NO_MODIFICATIONS.get(
                                String.valueOf(entryDN)));
        break modifyProcessing;
      }


      // If the user must change their password before doing anything else, and
      // if the target of the modify operation isn't the user's own entry, then
      // reject the request.
      if ((! isInternalOperation()) && clientConnection.mustChangePassword())
      {
        DN authzDN = getAuthorizationDN();
        if ((authzDN != null) && (! authzDN.equals(entryDN)))
        {
          // The user will not be allowed to do anything else before the
          // password gets changed.  Also note that we haven't yet checked the
          // request controls so we need to do that now to see if the password
          // policy request control was provided.
          for (Control c : getRequestControls())
          {
            if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
            {
              pwPolicyControlRequested = true;
              pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
              break;
            }
          }

          setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD.get());
          break modifyProcessing;
        }
      }


      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      AbstractTransaction txn =
        new AbstractTransaction(ndbBackend.getRootContainer());

      try
      {
        // Check for a request to cancel this operation.
        checkIfCanceled(false);


        try
        {
          // Get the entry to modify.  If it does not exist, then fail.
          currentEntry = ndbBackend.getEntryNoCommit(entryDN, txn,
            NdbOperation.LockMode.LM_Exclusive);

          if (currentEntry == null)
          {
            setResultCode(ResultCode.NO_SUCH_OBJECT);
            appendErrorMessage(ERR_MODIFY_NO_SUCH_ENTRY.get(
                String.valueOf(entryDN)));

            // See if one of the entry's ancestors exists.
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

            break modifyProcessing;
          }

          // Check to see if there are any controls in the request.  If so, then
          // see if there is any special processing required.
          processRequestControls();

          // Get the password policy state object for the entry that can be used
          // to perform any appropriate password policy processing.  Also, see
          // if the entry is being updated by the end user or an administrator.
          selfChange = entryDN.equals(getAuthorizationDN());

          // FIXME -- Need a way to enable debug mode.
          pwPolicyState = new PasswordPolicyState(currentEntry, false,
                                                  TimeThread.getTime(), true);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyProcessing;
        }


        // Create a duplicate of the entry and apply the changes to it.
        modifiedEntry = currentEntry.duplicate(false);

        if (! noOp)
        {
            if(!handleConflictResolution()) {
                break modifyProcessing;
            }
        }


        try
        {
          handleSchemaProcessing();
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyProcessing;
        }


        // Check to see if the client has permission to perform the modify.
        // The access control check is not made any earlier because the handler
        // needs access to the modified entry.

        // FIXME: for now assume that this will check all permissions
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists may have
        // already exposed sensitive information to the client.
        try
        {
          if (!AccessControlConfigManager.getInstance()
              .getAccessControlHandler().isAllowed(this))
          {
            setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
            appendErrorMessage(ERR_MODIFY_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS
                .get(String.valueOf(entryDN)));
            break modifyProcessing;
          }
        }
        catch (DirectoryException e)
        {
          setResultCode(e.getResultCode());
          appendErrorMessage(e.getMessageObject());
          break modifyProcessing;
        }


        try
        {
          handleInitialPasswordPolicyProcessing();

          wasLocked = false;
          if (passwordChanged)
          {
            performAdditionalPasswordChangedProcessing();
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyProcessing;
        }


        if ((! passwordChanged) && (! isInternalOperation()) &&
            pwPolicyState.mustChangePassword())
        {
          // The user will not be allowed to do anything else before the
          // password gets changed.
          pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
          setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD.get());
          break modifyProcessing;
        }


        // If the server is configured to check the schema and the
        // operation is not a sycnhronization operation,
        // make sure that the new entry is valid per the server schema.
        if ((DirectoryServer.checkSchema()) && (! isSynchronizationOperation()))
        {
          MessageBuilder invalidReason = new MessageBuilder();
          if (! modifiedEntry.conformsToSchema(null, false, false, false,
              invalidReason))
          {
            setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
            appendErrorMessage(ERR_MODIFY_VIOLATES_SCHEMA.get(
                                    String.valueOf(entryDN), invalidReason));
            break modifyProcessing;
          }
        }


        // Check for a request to cancel this operation.
        checkIfCanceled(false);

        // If the operation is not a synchronization operation,
        // Invoke the pre-operation modify plugins.
        if (! isSynchronizationOperation())
        {
          executePostOpPlugins = true;
          PluginResult.PreOperation preOpResult =
            pluginConfigManager.invokePreOperationModifyPlugins(this);
          if (!preOpResult.continueProcessing())
          {
            setResultCode(preOpResult.getResultCode());
            appendErrorMessage(preOpResult.getErrorMessage());
            setMatchedDN(preOpResult.getMatchedDN());
            setReferralURLs(preOpResult.getReferralURLs());
            break modifyProcessing;
          }
        }


        // Actually perform the modify operation.  This should also include
        // taking care of any synchronization that might be needed.
        if (backend == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(ERR_MODIFY_NO_BACKEND_FOR_ENTRY.get(
                                  String.valueOf(entryDN)));
          break modifyProcessing;
        }

        try
        {
          try
          {
            checkWritability();
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            setResponseData(de);
            break modifyProcessing;
          }


          if (noOp)
          {
            appendErrorMessage(INFO_MODIFY_NOOP.get());
            setResultCode(ResultCode.NO_OPERATION);
          }
          else
          {
              if(!processPreOperation()) {
                  break modifyProcessing;
              }

            ndbBackend.replaceEntry(currentEntry, modifiedEntry, this, txn);



            // See if we need to generate any account status notifications as a
            // result of the changes.
            if (passwordChanged || enabledStateChanged || wasLocked)
            {
              handleAccountStatusNotifications();
            }
          }


          // Handle any processing that may be needed for the pre-read and/or
          // post-read controls.
          handleReadEntryProcessing();


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
          break modifyProcessing;
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

    // If the password policy request control was included, then make sure we
    // send the corresponding response control.
    if (pwPolicyControlRequested)
    {
      addResponseControl(new PasswordPolicyResponseControl(null, 0,
                                                           pwpErrorType));
    }

    // Invoke the post-operation or post-synchronization modify plugins.
    if (isSynchronizationOperation())
    {
      if (getResultCode() == ResultCode.SUCCESS)
      {
        pluginConfigManager.invokePostSynchronizationModifyPlugins(this);
      }
    }
    else if (executePostOpPlugins)
    {
      // FIXME -- Should this also be done while holding the locks?
      PluginResult.PostOperation postOpResult =
           pluginConfigManager.invokePostOperationModifyPlugins(this);
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
              changeListener
                  .handleModifyOperation(NDBModifyOperation.this,
                      currentEntry, modifiedEntry);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              Message message = ERR_MODIFY_ERROR_NOTIFYING_CHANGE_LISTENER
                  .get(getExceptionMessage(e));
              logError(message);
            }
          }
        }
      });
    }
  }
}
