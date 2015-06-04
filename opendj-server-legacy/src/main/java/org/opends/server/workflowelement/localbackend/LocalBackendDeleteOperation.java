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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.workflowelement.localbackend;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DeleteOperationWrapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostSynchronizationDeleteOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;

import static org.opends.messages.CoreMessages.*;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The backend in which the operation is to be processed. */
  private Backend<?> backend;

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
   * @param wfe
   *          The local backend work-flow element.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  public void processLocalDelete(final LocalBackendWorkflowElement wfe)
      throws CanceledOperationException
  {
    this.backend = wfe.getBackend();

    clientConnection = getClientConnection();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    try
    {
      AtomicBoolean executePostOpPlugins = new AtomicBoolean(false);
      processDelete(executePostOpPlugins);

      // Invoke the post-operation or post-synchronization delete plugins.
      PluginConfigManager pluginConfigManager =
          DirectoryServer.getPluginConfigManager();
      if (isSynchronizationOperation())
      {
        if (getResultCode() == ResultCode.SUCCESS)
        {
          pluginConfigManager.invokePostSynchronizationDeletePlugins(this);
        }
      }
      else if (executePostOpPlugins.get())
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

      // Check to see if the client has permission to perform the
      // delete.

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
        if (!AccessControlConfigManager.getInstance().getAccessControlHandler()
            .isAllowed(this))
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
        PluginResult.PreOperation preOpResult =
            DirectoryServer.getPluginConfigManager()
                .invokePreOperationDeletePlugins(this);
        if (!preOpResult.continueProcessing())
        {
          setResultCode(preOpResult.getResultCode());
          appendErrorMessage(preOpResult.getErrorMessage());
          setMatchedDN(preOpResult.getMatchedDN());
          setReferralURLs(preOpResult.getReferralURLs());
          return;
        }
      }

      // Get the backend to use for the delete. If there is none, then fail.
      if (backend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_DELETE_NO_SUCH_ENTRY.get(entryDN));
        return;
      }

      LocalBackendWorkflowElement.checkIfBackendIsWritable(backend, this,
          entryDN, ERR_DELETE_SERVER_READONLY, ERR_DELETE_BACKEND_READONLY);

      // The selected backend will have the responsibility of making sure that
      // the entry actually exists and does not have any children (or possibly
      // handling a subtree delete). But we will need to check if there are
      // any subordinate backends that should stop us from attempting the
      // delete.
      for (Backend<?> b : backend.getSubordinateBackends())
      {
        for (DN dn : b.getBaseDNs())
        {
          if (dn.isDescendantOf(entryDN))
          {
            setResultCodeAndMessageNoInfoDisclosure(entry,
                ResultCode.NOT_ALLOWED_ON_NONLEAF,
                ERR_DELETE_HAS_SUB_BACKEND.get(entryDN, dn));
            return;
          }
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
      }

      LocalBackendWorkflowElement.addPreReadResponse(this, preReadRequest,
          entry);

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

  private DN findMatchedDN(DN entryDN)
  {
    try
    {
      DN matchedDN = entryDN.getParentDNInSuffix();
      while (matchedDN != null)
      {
        if (DirectoryServer.entryExists(matchedDN))
        {
          return matchedDN;
        }

        matchedDN = matchedDN.getParentDNInSuffix();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
    return null;
  }

  /**
   * Performs any request control processing needed for this operation.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              operation to fail.
   */
  private void handleRequestControls() throws DirectoryException
  {
    LocalBackendWorkflowElement.removeAllDisallowedControls(entryDN, this);

    List<Control> requestControls = getRequestControls();
    if (requestControls != null && !requestControls.isEmpty())
    {
      for (Control c : requestControls)
      {
        final String oid = c.getOID();
        if (OID_LDAP_ASSERTION.equals(oid))
        {
          LDAPAssertionRequestControl assertControl =
                getRequestControl(LDAPAssertionRequestControl.DECODER);

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

          // Check if the current user has permission to make
          // this determination.
          if (!AccessControlConfigManager.getInstance().
            getAccessControlHandler().isAllowed(this, entry, filter))
          {
            throw new DirectoryException(
              ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
          }

          try
          {
            if (!filter.matchesEntry(entry))
            {
              throw newDirectoryException(entry, ResultCode.ASSERTION_FAILED,
                  ERR_DELETE_ASSERTION_FAILED.get(entryDN));
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
          preReadRequest =
                getRequestControl(LDAPPreReadRequestControl.DECODER);
        }
        else if (LocalBackendWorkflowElement.processProxyAuthControls(this, oid))
        {
          continue;
        }
        // NYI -- Add support for additional controls.
        else if (c.isCritical()
            && (backend == null || !backend.supportsControl(oid)))
        {
          throw newDirectoryException(entry,
              ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
              ERR_DELETE_UNSUPPORTED_CRITICAL_CONTROL.get(entryDN, oid));
        }
      }
    }
  }

  private DN getName(Entry e)
  {
    return e != null ? e.getName() : DN.rootDN();
  }

  /**
   * Handle conflict resolution.
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   */
  private boolean handleConflictResolution() {
      for (SynchronizationProvider<?> provider :
          DirectoryServer.getSynchronizationProviders()) {
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

  /**
   * Invoke post operation synchronization providers.
   */
  private void processSynchPostOperationPlugins() {
      for (SynchronizationProvider<?> provider :
          DirectoryServer.getSynchronizationProviders()) {
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
      for (SynchronizationProvider<?> provider :
          DirectoryServer.getSynchronizationProviders()) {
          try {
              SynchronizationProviderResult result =
                  provider.doPreOperation(this);
              if (! result.continueProcessing()) {
                  setResultCode(result.getResultCode());
                  appendErrorMessage(result.getErrorMessage());
                  setMatchedDN(result.getMatchedDN());
                  setReferralURLs(result.getReferralURLs());
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
}
