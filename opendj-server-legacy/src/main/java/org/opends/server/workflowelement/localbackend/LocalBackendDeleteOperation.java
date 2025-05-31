/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2022-2025 3A Systems, LLC.
 */
package org.opends.server.workflowelement.localbackend;

import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.TransactionSpecificationRequestControl;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DeleteOperationWrapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PersistentSearch;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.*;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.types.AbstractOperation.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

/**
 * This class defines an operation used to delete an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendDeleteOperation
       extends DeleteOperationWrapper
       implements PreOperationDeleteOperation, PostOperationDeleteOperation,
                  PostResponseDeleteOperation,
                  PostSynchronizationDeleteOperation, RollbackOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The backend in which the operation is to be processed. */
  private LocalBackend<?> backend;

  /** Indicates whether the LDAP no-op control has been requested. */
  private boolean noOp;

  /** The client connection on which this operation was requested. */
  private ClientConnection clientConnection;

  /** The DN of the entry to be deleted. */
  private DN entryDN;

  /** The entry to be deleted. */
  private Entry entry;

  /** The pre-read request control included in the request, if applicable. */
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
  @Override
  public Entry getEntryToDelete()
  {
    return entry;
  }



  /**
   * Process this delete operation in a local backend.
   *
   * @param backend
   *          The backend on which operation is performed.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  public void processLocalDelete(final LocalBackend<?> backend)
      throws CanceledOperationException
  {
    this.backend = backend;

    clientConnection = getClientConnection();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    try
    {
      AtomicBoolean executePostOpPlugins = new AtomicBoolean(false);
      processDelete(executePostOpPlugins);

      // Invoke the post-operation or post-synchronization delete plugins.
      if (isSynchronizationOperation())
      {
        if (getResultCode() == ResultCode.SUCCESS)
        {
          getPluginConfigManager().invokePostSynchronizationDeletePlugins(this);
        }
      }
      else if (executePostOpPlugins.get())
      {
        if (!processOperationResult(this, getPluginConfigManager().invokePostOperationDeletePlugins(this)))
        {
          return;
        }
      }
    }
    finally
    {
      LocalBackendWorkflowElement.filterNonDisclosableMatchedDN(this);
    }

    // Register a post-response call-back which will notify persistent
    // searches and change listeners.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      registerPostResponseCallback(new Runnable()
      {
        @Override
        public void run()
        {
          for (PersistentSearch psearch : backend.getPersistentSearches())
          {
            psearch.processDelete(entry);
          }
        }
      });
    }
  }

  private void processDelete(AtomicBoolean executePostOpPlugins)
      throws CanceledOperationException
  {
    // Process the entry DN to convert it from its raw form as provided by the
    // client to the form required for the rest of the delete processing.
    entryDN = getEntryDN();
    if (entryDN == null)
    {
      return;
    }

    // Get the backend to use for the delete. If there is none, then fail.
    if (backend == null)
    {
      setResultCode(ResultCode.NO_SUCH_OBJECT);
      appendErrorMessage(ERR_DELETE_NO_SUCH_ENTRY.get(entryDN));
      return;
    }

    /*
     * Grab a write lock on the entry and its subtree in order to prevent concurrent updates to
     * subordinate entries.
     */
    final DNLock subtreeLock = DirectoryServer.getLockManager().tryWriteLockSubtree(entryDN);
    try
    {
      if (subtreeLock == null)
      {
        setResultCode(ResultCode.BUSY);
        appendErrorMessage(ERR_DELETE_CANNOT_LOCK_ENTRY.get(entryDN));
        return;
      }

      // Get the entry to delete. If it doesn't exist, then fail.
      entry = backend.getEntry(entryDN);
      if (entry == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_DELETE_NO_SUCH_ENTRY.get(entryDN));

        setMatchedDN(findMatchedDN(entryDN));
        return;
      }

      if (!handleConflictResolution())
      {
        return;
      }

      // Check to see if the client has permission to perform the delete.

      // Check to see if there are any controls in the request. If so, then
      // see if there is any special processing required.
      handleRequestControls();

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.

      // FIXME: earlier checks to see if the entry already exists may
      // have already exposed sensitive information to the client.
      try
      {
        if (!getAccessControlHandler().isAllowed(this))
        {
          setResultCodeAndMessageNoInfoDisclosure(entry,
              ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_DELETE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(entryDN));
          return;
        }
      }
      catch (DirectoryException e)
      {
        setResultCode(e.getResultCode());
        appendErrorMessage(e.getMessageObject());
        return;
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // If the operation is not a synchronization operation,
      // invoke the pre-delete plugins.
      if (!isSynchronizationOperation())
      {
        executePostOpPlugins.set(true);
        if (!processOperationResult(this, getPluginConfigManager().invokePreOperationDeletePlugins(this)))
        {
          return;
        }
      }

      LocalBackendWorkflowElement.checkIfBackendIsWritable(backend, this,
          entryDN, ERR_DELETE_SERVER_READONLY, ERR_DELETE_BACKEND_READONLY);

      // The selected backend will have the responsibility of making sure that
      // the entry actually exists and does not have any children (or possibly
      // handling a subtree delete). But we will need to check if there are
      // any subordinate backends that should stop us from attempting the delete
      BackendConfigManager backendConfigManager =
          DirectoryServer.getInstance().getServerContext().getBackendConfigManager();
      for (DN dn : backendConfigManager.findSubordinateLocalNamingContextsForEntry(entryDN))
      {
    	  if (dn.isInScopeOf(entryDN, SearchScope.WHOLE_SUBTREE)) {
    		  setResultCodeAndMessageNoInfoDisclosure(entry,ResultCode.NOT_ALLOWED_ON_NONLEAF, ERR_DELETE_HAS_SUB_BACKEND.get(entryDN, dn));
              return;
  	      }
      }

      // Actually perform the delete.
      if (noOp)
      {
        setResultCode(ResultCode.NO_OPERATION);
        appendErrorMessage(INFO_DELETE_NOOP.get());
      }
      else
      {
        if (!processPreOperation())
        {
          return;
        }
        backend.deleteEntry(entryDN, this);
        if (trx!=null) {
          trx.success(this);
        }
      }

      LocalBackendWorkflowElement.addPreReadResponse(this, preReadRequest, entry);

      if (!noOp)
      {
        setResultCode(ResultCode.SUCCESS);
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      setResponseData(de);
    }
    finally
    {
      if (subtreeLock != null)
      {
        subtreeLock.unlock();
      }
      processSynchPostOperationPlugins();
    }
  }

  private AccessControlHandler<?> getAccessControlHandler()
  {
    return AccessControlConfigManager.getInstance().getAccessControlHandler();
  }

  private DirectoryException newDirectoryException(Entry entry,
      ResultCode resultCode, LocalizableMessage message) throws DirectoryException
  {
    return LocalBackendWorkflowElement.newDirectoryException(this, entry,
        entryDN,
        resultCode, message, ResultCode.NO_SUCH_OBJECT,
        ERR_DELETE_NO_SUCH_ENTRY.get(entryDN));
  }

  private void setResultCodeAndMessageNoInfoDisclosure(Entry entry,
      ResultCode resultCode, LocalizableMessage message) throws DirectoryException
  {
    LocalBackendWorkflowElement.setResultCodeAndMessageNoInfoDisclosure(this,
        entry, entryDN, resultCode, message, ResultCode.NO_SUCH_OBJECT,
        ERR_DELETE_NO_SUCH_ENTRY.get(entryDN));
  }

  /**
   * Performs any request control processing needed for this operation.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              operation to fail.
   */
  private void handleRequestControls() throws DirectoryException
  {
    LocalBackendWorkflowElement.evaluateProxyAuthControls(this);
    LocalBackendWorkflowElement.removeAllDisallowedControls(entryDN, this);

    for (Control c : getRequestControls())
    {
      final String oid = c.getOID();
      if (OID_LDAP_ASSERTION.equals(oid))
      {
        LDAPAssertionRequestControl assertControl = getRequestControl(LDAPAssertionRequestControl.DECODER);

        SearchFilter filter;
        try
        {
          filter = assertControl.getSearchFilter();
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          throw newDirectoryException(entry, de.getResultCode(),
              ERR_DELETE_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
        }

        // Check if the current user has permission to make this determination.
        if (!getAccessControlHandler().isAllowed(this, entry, filter))
        {
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
        }

        try
        {
          if (!filter.matchesEntry(entry))
          {
            throw newDirectoryException(entry, ResultCode.ASSERTION_FAILED, ERR_DELETE_ASSERTION_FAILED.get(entryDN));
          }
        }
        catch (DirectoryException de)
        {
          if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
          {
            throw de;
          }

          logger.traceException(de);

          throw newDirectoryException(entry, de.getResultCode(),
              ERR_DELETE_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
        }
      }
      else if (OID_LDAP_NOOP_OPENLDAP_ASSIGNED.equals(oid))
      {
        noOp = true;
      }
      else if (OID_LDAP_READENTRY_PREREAD.equals(oid))
      {
        preReadRequest = getRequestControl(LDAPPreReadRequestControl.DECODER);
      }
      else if (LocalBackendWorkflowElement.isProxyAuthzControl(oid))
      {
        continue;
      }
      else if (TransactionSpecificationRequestControl.OID.equals(oid))
      {
        trx=getClientConnection().getTransaction(((LDAPControl)c).getValue().toString());
      }
      else if (c.isCritical() && !backend.supportsControl(oid))
      {
        throw newDirectoryException(entry, ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
            ERR_DELETE_UNSUPPORTED_CRITICAL_CONTROL.get(entryDN, oid));
      }
    }
  }
  ClientConnection.Transaction trx=null;

  /**
   * Handle conflict resolution.
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   */
  private boolean handleConflictResolution() {
      for (SynchronizationProvider<?> provider : getSynchronizationProviders()) {
          try {
              SynchronizationProviderResult result =
                  provider.handleConflictResolution(this);
              if (! result.continueProcessing()) {
                  setResultCodeAndMessageNoInfoDisclosure(entry,
                      result.getResultCode(), result.getErrorMessage());
                  setMatchedDN(result.getMatchedDN());
                  setReferralURLs(result.getReferralURLs());
                  return false;
              }
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_DELETE_SYNCH_CONFLICT_RESOLUTION_FAILED,
                  getConnectionID(), getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return false;
          }
      }
      return true;
  }

  /** Invoke post operation synchronization providers. */
  private void processSynchPostOperationPlugins() {
      for (SynchronizationProvider<?> provider : getSynchronizationProviders()) {
          try {
              provider.doPostOperation(this);
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_DELETE_SYNCH_POSTOP_FAILED, getConnectionID(),
                      getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return;
          }
      }
  }

  /**
   * Process pre operation.
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   */
  private boolean processPreOperation() {
      for (SynchronizationProvider<?> provider : getSynchronizationProviders()) {
          try {
              if (!processOperationResult(this, provider.doPreOperation(this))) {
                  return false;
              }
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_DELETE_SYNCH_PREOP_FAILED, getConnectionID(),
                      getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return false;
          }
      }
      return true;
  }

  @Override
  public void rollback() throws CanceledOperationException, DirectoryException {
    backend.addEntry(entry,null);
  }
}
