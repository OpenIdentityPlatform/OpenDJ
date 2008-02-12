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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.localbackend;



import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.messages.Message;
import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.DeleteOperationWrapper;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LockManager;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.types.operation.PostSynchronizationDeleteOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation used to delete an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendDeleteOperation
       extends DeleteOperationWrapper
       implements PreOperationDeleteOperation, PostOperationDeleteOperation,
                  PostResponseDeleteOperation,
                  PostSynchronizationDeleteOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The backend in which the operation is to be processed.
  private Backend backend;

  // Indicates whether the LDAP no-op control has been requested.
  private boolean noOp;

  // Indicates whether to skip post-operation processing.
  private boolean skipPostOperation;

  // The client connection on which this operation was requested.
  private ClientConnection clientConnection;

  // The DN of the entry to be deleted.
  private DN entryDN;

  // The entry to be deleted.
  private Entry entry;

  // The pre-read request control included in the request, if applicable.
  private LDAPPreReadRequestControl preReadRequest;



  /**
   * Creates a new operation that may be used to delete an entry from a
   * local backend of the Directory Server.
   *
   * @param delete The operation to enhance.
   */
  public LocalBackendDeleteOperation(DeleteOperation delete)
  {
    super(delete);
    LocalBackendWorkflowElement.attachLocalOperation (delete, this);
  }



  /**
   * Retrieves the entry to be deleted.
   *
   * @return  The entry to be deleted, or <CODE>null</CODE> if the entry is not
   *          yet available.
   */
  public Entry getEntryToDelete()
  {
    return entry;
  }



  /**
   * Process this delete operation in a local backend.
   *
   * @param  backend  The backend in which the delete operation should be
   *                  processed.
   */
  void processLocalDelete(Backend backend)
  {
    this.backend = backend;

    clientConnection = getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    skipPostOperation = false;

    // Check for a request to cancel this operation.
    if (cancelIfRequested())
    {
      return;
    }

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

      // Grab a write lock on the entry.
      Lock entryLock = null;
      for (int i=0; i < 3; i++)
      {
        entryLock = LockManager.lockWrite(entryDN);
        if (entryLock != null)
        {
          break;
        }
      }

      if (entryLock == null)
      {
        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(ERR_DELETE_CANNOT_LOCK_ENTRY.get(
                                String.valueOf(entryDN)));
        break deleteProcessing;
      }

      try
      {
        // Get the entry to delete.  If it doesn't exist, then fail.
        try
        {
          entry = backend.getEntry(entryDN);
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
              break deleteProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_DELETE_SYNCH_CONFLICT_RESOLUTION_FAILED.get(
                          getConnectionID(), getOperationID(),
                          getExceptionMessage(de)));
            setResponseData(de);
            break deleteProcessing;
          }
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
        if (! AccessControlConfigManager.getInstance().
                   getAccessControlHandler().isAllowed(this))
        {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          appendErrorMessage(ERR_DELETE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                                  String.valueOf(entryDN)));
          skipPostOperation = true;
          break deleteProcessing;
        }

        // Check for a request to cancel this operation.
        if (cancelIfRequested())
        {
          return;
        }


        // If the operation is not a synchronization operation,
        // invoke the pre-delete plugins.
        if (! isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
               pluginConfigManager.invokePreOperationDeletePlugins(this);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the request
            // and result and return.
            setResultCode(ResultCode.CANCELED);
            appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());
            setProcessingStopTime();
            return;
          }
          else if (preOpResult.sendResponseImmediately() ||
                   preOpResult.skipCoreProcessing())
          {
            skipPostOperation = true;
            break deleteProcessing;
          }
        }


        // Check for a request to cancel this operation.
        if (cancelIfRequested())
        {
          return;
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
            for (SynchronizationProvider provider :
                 DirectoryServer.getSynchronizationProviders())
            {
              try
              {
                SynchronizationProviderResult result =
                     provider.doPreOperation(this);
                if (! result.continueOperationProcessing())
                {
                  break deleteProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                logError(ERR_DELETE_SYNCH_PREOP_FAILED.get(getConnectionID(),
                              getOperationID(), getExceptionMessage(de)));
                setResponseData(de);
                break deleteProcessing;
              }
            }

            backend.deleteEntry(entryDN, this);
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
        catch (CancelledOperationException coe)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, coe);
          }

          CancelResult cancelResult = coe.getCancelResult();

          setCancelResult(cancelResult);
          setResultCode(cancelResult.getResultCode());

          Message message = coe.getMessageObject();
          if ((message != null) && (message.length() > 0))
          {
            appendErrorMessage(message);
          }
          break deleteProcessing;
        }
      }
      finally
      {
        LockManager.unlock(entryDN, entryLock);

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

            logError(ERR_DELETE_SYNCH_POSTOP_FAILED.get(getConnectionID(),
                          getOperationID(), getExceptionMessage(de)));
            setResponseData(de);
            break;
          }
        }
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);


    // Invoke the post-operation or post-synchronization delete plugins.
    if (isSynchronizationOperation())
    {
      if (getResultCode() == ResultCode.SUCCESS)
      {
        pluginConfigManager.invokePostSynchronizationDeletePlugins(this);
      }
    }
    else if (! skipPostOperation)
    {
      PostOperationPluginResult postOperationResult =
           pluginConfigManager.invokePostOperationDeletePlugins(this);
      if (postOperationResult.connectionTerminated())
      {
        setResultCode(ResultCode.CANCELED);
        appendErrorMessage(ERR_CANCELED_BY_POSTOP_DISCONNECT.get());
        setProcessingStopTime();
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
          changeListener.handleDeleteOperation(this, entry);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_DELETE_ERROR_NOTIFYING_CHANGE_LISTENER.get(
              getExceptionMessage(e));
          logError(message);
        }
      }
    }

    // Stop the processing timer.
    setProcessingStopTime();
  }



  /**
   * Checks to determine whether there has been a request to cancel this
   * operation.  If so, then set the cancel result and processing stop time.
   *
   * @return  {@code true} if there was a cancel request, or {@code false} if
   *          not.
   */
  private boolean cancelIfRequested()
  {
    if (getCancelRequest() == null)
    {
      return false;
    }

    indicateCancelled(getCancelRequest());
    setProcessingStopTime();
    return true;
  }



  /**
   * Performs any request control processing needed for this operation.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              operation to fail.
   */
  private void handleRequestControls()
          throws DirectoryException
  {
    List<Control> requestControls = getRequestControls();
    if ((requestControls != null) && (! requestControls.isEmpty()))
    {
      for (int i=0; i < requestControls.size(); i++)
      {
        Control c   = requestControls.get(i);
        String  oid = c.getOID();

        if (!AccessControlConfigManager.getInstance().
                 getAccessControlHandler().isAllowed(entryDN, this, c))
        {
          skipPostOperation = true;
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                         ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
        }

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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }

          try
          {
            // FIXME -- We need to determine whether the current user has
            //          permission to make this determination.
            SearchFilter filter = assertControl.getSearchFilter();
            if (! filter.matchesEntry(entry))
            {
              throw new DirectoryException(ResultCode.ASSERTION_FAILED,
                                           ERR_DELETE_ASSERTION_FAILED.get(
                                                String.valueOf(entryDN)));
            }
          }
          catch (DirectoryException de)
          {
            if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
            {
              throw de;
            }

            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                           ERR_DELETE_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                String.valueOf(entryDN),
                                de.getMessageObject()));
          }
        }
        else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
        {
          noOp = true;
        }
        else if (oid.equals(OID_LDAP_READENTRY_PREREAD))
        {
          if (c instanceof LDAPPreReadRequestControl)
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V1))
        {
          // The requester must have the PROXIED_AUTH privilige in order to
          // be able to use this control.
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }


          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.nullDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getDN());
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V2))
        {
          // The requester must have the PROXIED_AUTH privilige in order to
          // be able to use this control.
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }


          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.nullDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getDN());
          }
        }

        // NYI -- Add support for additional controls.

        else if (c.isCritical())
        {
          if ((backend == null) || (! backend.supportsControl(oid)))
          {
            throw new DirectoryException(
                           ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                           ERR_DELETE_UNSUPPORTED_CRITICAL_CONTROL.get(
                                String.valueOf(entryDN), oid));
          }
        }
      }
    }
  }



  /**
   * Performs any processing needed for the LDAP pre-read control.
   */
  private void processPreReadControl()
  {
    if (preReadRequest != null)
    {
      Entry entryCopy = entry.duplicate(true);

      if (! preReadRequest.allowsAttribute(
                 DirectoryServer.getObjectClassAttributeType()))
      {
        entryCopy.removeAttribute(
             DirectoryServer.getObjectClassAttributeType());
      }

      if (! preReadRequest.returnAllUserAttributes())
      {
        Iterator<AttributeType> iterator =
             entryCopy.getUserAttributes().keySet().iterator();
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
             entryCopy.getOperationalAttributes().keySet().iterator();
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
      SearchResultEntry searchEntry = new SearchResultEntry(entryCopy);
      LDAPPreReadResponseControl responseControl =
           new LDAPPreReadResponseControl(preReadRequest.getOID(),
                                          preReadRequest.isCritical(),
                                          searchEntry);
      addResponseControl(responseControl);
    }
  }
}

