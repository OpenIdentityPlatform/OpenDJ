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
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.HashSet;

import org.opends.messages.Message;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.backends.ndb.AbstractTransaction;
import org.opends.server.backends.ndb.BackendImpl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.workflowelement.localbackend.LocalBackendAddOperation;
import
  org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;



/**
 * This class defines an operation used to add an entry in a local backend
 * of the Directory Server.
 */
public class NDBAddOperation
       extends LocalBackendAddOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates a new operation that may be used to add a new entry in a
   * local backend of the Directory Server.
   *
   * @param add The operation to enhance.
   */
  public NDBAddOperation(AddOperation add)
  {
    super(add);

    NDBWorkflowElement.attachLocalOperation (add, this);
  }



  /**
   * Process this add operation against a local backend.
   *
   * @param  wfe The local backend work-flow element.
   *
   * @throws CanceledOperationException if this operation should be
   * cancelled
   */
  @Override
  public void processLocalAdd(final LocalBackendWorkflowElement wfe)
    throws CanceledOperationException {
    boolean executePostOpPlugins = false;

    this.backend = wfe.getBackend();
    BackendImpl ndbBackend = (BackendImpl) backend;
    ClientConnection clientConnection = getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
addProcessing:
    {
      // Process the entry DN and set of attributes to convert them from their
      // raw forms as provided by the client to the forms required for the rest
      // of the add processing.
      entryDN = getEntryDN();
      if (entryDN == null)
      {
        break addProcessing;
      }

      objectClasses = getObjectClasses();
      userAttributes = getUserAttributes();
      operationalAttributes = getOperationalAttributes();

      if ((objectClasses == null ) || (userAttributes == null) ||
          (operationalAttributes == null))
      {
        break addProcessing;
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      DN parentDN = entryDN.getParentDNInSuffix();

      AbstractTransaction txn =
        new AbstractTransaction(ndbBackend.getRootContainer());

      try
      {
        // Check for a request to cancel this operation.
        checkIfCanceled(false);

        // Invoke any conflict resolution processing that might be needed by the
        // synchronization provider.
        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            SynchronizationProviderResult result =
                provider.handleConflictResolution(this);
            if (! result.continueProcessing())
            {
              setResultCode(result.getResultCode());
              appendErrorMessage(result.getErrorMessage());
              setMatchedDN(result.getMatchedDN());
              setReferralURLs(result.getReferralURLs());
              break addProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_ADD_SYNCH_CONFLICT_RESOLUTION_FAILED.get(
                          getConnectionID(), getOperationID(),
                          getExceptionMessage(de)));

            setResponseData(de);
            break addProcessing;
          }
        }

        for (AttributeType at : userAttributes.keySet())
        {
          // If the attribute type is marked "NO-USER-MODIFICATION" then fail
          // unless this is an internal operation or is related to
          // synchronization in some way.
          // This must be done before running the password policy code
          // and any other code that may add attributes marked as
          // "NO-USER-MODIFICATION"
          //
          // Note that doing this checks at this time
          // of the processing does not make it possible for pre-parse plugins
          // to add NO-USER-MODIFICATION attributes to the entry.
          if (at.isNoUserModification())
          {
            if (! (isInternalOperation() || isSynchronizationOperation()))
            {
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_ADD_ATTR_IS_NO_USER_MOD.get(
                                      String.valueOf(entryDN),
                                      at.getNameOrOID()));

              break addProcessing;
            }
          }
        }

        for (AttributeType at : operationalAttributes.keySet())
        {
          if (at.isNoUserModification())
          {
            if (! (isInternalOperation() || isSynchronizationOperation()))
            {
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_ADD_ATTR_IS_NO_USER_MOD.get(
                                      String.valueOf(entryDN),
                                      at.getNameOrOID()));

              break addProcessing;
            }
          }
        }

        // Get the parent entry, if it exists.
        Entry parentEntry = null;
        if (parentDN != null)
        {
          try
          {
            parentEntry = ndbBackend.getEntryNoCommit(parentDN, txn,
              NdbOperation.LockMode.LM_Read);
            if (parentEntry == null)
            {
              DN matchedDN = parentDN.getParentDNInSuffix();
              while (matchedDN != null)
              {
                try
                {
                  if (DirectoryServer.entryExists(matchedDN))
                  {
                    setMatchedDN(matchedDN);
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

                matchedDN = matchedDN.getParentDNInSuffix();
              }


              // The parent doesn't exist, so this add can't be successful.
              setResultCode(ResultCode.NO_SUCH_OBJECT);
              appendErrorMessage(ERR_ADD_NO_PARENT.get(String.valueOf(entryDN),
                                      String.valueOf(parentDN)));
              break addProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            setResponseData(de);
            break addProcessing;
          }
        }


        // Check to make sure that all of the RDN attributes are included as
        // attribute values.  If not, then either add them or report an error.
        try
        {
          addRDNAttributesIfNecessary();
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break addProcessing;
        }


        // Check to make sure that all objectclasses have their superior classes
        // listed in the entry.  If not, then add them.
        HashSet<ObjectClass> additionalClasses = null;
        for (ObjectClass oc : objectClasses.keySet())
        {
          ObjectClass superiorClass = oc.getSuperiorClass();
          if ((superiorClass != null) &&
              (! objectClasses.containsKey(superiorClass)))
          {
            if (additionalClasses == null)
            {
              additionalClasses = new HashSet<ObjectClass>();
            }

            additionalClasses.add(superiorClass);
          }
        }

        if (additionalClasses != null)
        {
          for (ObjectClass oc : additionalClasses)
          {
            addObjectClassChain(oc);
          }
        }


        // Create an entry object to encapsulate the set of attributes and
        // objectclasses.
        entry = new Entry(entryDN, objectClasses, userAttributes,
                          operationalAttributes);

        // Check to see if the entry includes a privilege specification.  If so,
        // then the requester must have the PRIVILEGE_CHANGE privilege.
        AttributeType privType =
             DirectoryServer.getAttributeType(OP_ATTR_PRIVILEGE_NAME, true);
        if (entry.hasAttribute(privType) &&
            (! clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE, this)))
        {

          appendErrorMessage(
               ERR_ADD_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES.get());
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          break addProcessing;
        }


        // If it's not a synchronization operation, then check
        // to see if the entry contains one or more passwords and if they
        // are valid in accordance with the password policies associated with
        // the user.  Also perform any encoding that might be required by
        // password storage schemes.
        if (! isSynchronizationOperation())
        {
          try
          {
            handlePasswordPolicy();
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            setResponseData(de);
            break addProcessing;
          }
        }


        // If the server is configured to check schema and the
        // operation is not a synchronization operation,
        // check to see if the entry is valid according to the server schema,
        // and also whether its attributes are valid according to their syntax.
        if ((DirectoryServer.checkSchema()) && (! isSynchronizationOperation()))
        {
          try
          {
            checkSchema(parentEntry);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            setResponseData(de);
            break addProcessing;
          }
        }


        // Get the backend in which the add is to be performed.
        if (backend == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(Message.raw("No backend for entry " +
                                         entryDN.toString())); // TODO: i18n
          break addProcessing;
        }


        // Check to see if there are any controls in the request. If so, then
        // see if there is any special processing required.
        try
        {
          processControls(parentDN);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break addProcessing;
        }


        // Check to see if the client has permission to perform the add.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists or
        // if the parent entry does not exist may have already exposed
        // sensitive information to the client.
        try
        {
          if (AccessControlConfigManager.getInstance()
              .getAccessControlHandler().isAllowed(this) == false)
          {
            setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
            appendErrorMessage(ERR_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS
                .get(String.valueOf(entryDN)));
            break addProcessing;
          }
        }
        catch (DirectoryException e)
        {
          setResultCode(e.getResultCode());
          appendErrorMessage(e.getMessageObject());
          break addProcessing;
        }

        // Check for a request to cancel this operation.
        checkIfCanceled(false);

        // If the operation is not a synchronization operation,
        // Invoke the pre-operation add plugins.
        if (! isSynchronizationOperation())
        {
          executePostOpPlugins = true;
          PluginResult.PreOperation preOpResult =
            pluginConfigManager.invokePreOperationAddPlugins(this);
          if (!preOpResult.continueProcessing())
          {
            setResultCode(preOpResult.getResultCode());
            appendErrorMessage(preOpResult.getErrorMessage());
            setMatchedDN(preOpResult.getMatchedDN());
            setReferralURLs(preOpResult.getReferralURLs());
            break addProcessing;
          }
        }


        // If it is not a private backend, then check to see if the server or
        // backend is operating in read-only mode.
        if (! backend.isPrivateBackend())
        {
          switch (DirectoryServer.getWritabilityMode())
          {
            case DISABLED:
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_ADD_SERVER_READONLY.get(
                                      String.valueOf(entryDN)));
              break addProcessing;

            case INTERNAL_ONLY:
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(ERR_ADD_SERVER_READONLY.get(
                                        String.valueOf(entryDN)));
                break addProcessing;
              }
              break;
          }

          switch (backend.getWritabilityMode())
          {
            case DISABLED:
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_ADD_BACKEND_READONLY.get(
                                      String.valueOf(entryDN)));
              break addProcessing;

            case INTERNAL_ONLY:
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(ERR_ADD_BACKEND_READONLY.get(
                                        String.valueOf(entryDN)));
                break addProcessing;
              }
              break;
          }
        }


        try
        {
          if (noOp)
          {
            appendErrorMessage(INFO_ADD_NOOP.get());
            setResultCode(ResultCode.NO_OPERATION);
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
                if (! result.continueProcessing())
                {
                  setResultCode(result.getResultCode());
                  appendErrorMessage(result.getErrorMessage());
                  setMatchedDN(result.getMatchedDN());
                  setReferralURLs(result.getReferralURLs());
                  break addProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                logError(ERR_ADD_SYNCH_PREOP_FAILED.get(getConnectionID(),
                              getOperationID(), getExceptionMessage(de)));
                setResponseData(de);
                break addProcessing;
              }
            }

            ndbBackend.addEntry(entry, this, txn);
          }

          if (postReadRequest != null)
          {
            addPostReadResponse();
          }


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
          break addProcessing;
        }
      }
      finally
      {
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

            logError(ERR_ADD_SYNCH_POSTOP_FAILED.get(getConnectionID(),
                getOperationID(), getExceptionMessage(de)));
            setResponseData(de);
            break;
          }
        }
        try {
          txn.close();
        } catch (Exception ex) {
          if (debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, ex);
          }
        }
      }
    }

    // Invoke the post-operation or post-synchronization add plugins.
    if (isSynchronizationOperation())
    {
      if (getResultCode() == ResultCode.SUCCESS)
      {
        pluginConfigManager.invokePostSynchronizationAddPlugins(this);
      }
    }
    else if (executePostOpPlugins)
    {
      // FIXME -- Should this also be done while holding the locks?
      PluginResult.PostOperation postOpResult =
          pluginConfigManager.invokePostOperationAddPlugins(this);
      if(!postOpResult.continueProcessing())
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
              changeListener.handleAddOperation(NDBAddOperation.this, entry);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              logError(ERR_ADD_ERROR_NOTIFYING_CHANGE_LISTENER
                  .get(getExceptionMessage(e)));
            }
          }
        }
      });
    }
  }
}
