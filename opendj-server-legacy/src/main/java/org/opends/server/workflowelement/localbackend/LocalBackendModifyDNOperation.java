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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions copyright 2024-2025 3A Systems,LLC.
 */
package org.opends.server.workflowelement.localbackend;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.TransactionSpecificationRequestControl;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyDNOperationWrapper;
import org.opends.server.core.PersistentSearch;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.Modification;
import org.forgerock.opendj.ldap.RDN;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.operation.*;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.types.AbstractOperation.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

/**
 * This class defines an operation used to move an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendModifyDNOperation
  extends ModifyDNOperationWrapper
  implements PreOperationModifyDNOperation,
             PostOperationModifyDNOperation,
             PostResponseModifyDNOperation,
             PostSynchronizationModifyDNOperation, RollbackOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The backend in which the operation is to be processed. */
  private LocalBackend<?> backend;

  /** Indicates whether the no-op control was included in the request. */
  private boolean noOp;

  /** The client connection on which this operation was requested. */
  private ClientConnection clientConnection;

  /** The original DN of the entry. */
  private DN entryDN;

  /** The current entry, before it is renamed. */
  private Entry currentEntry;

  /** The new entry, as it will appear after it has been renamed. */
  private Entry newEntry;

  /** The LDAP post-read request control, if present in the request. */
  private LDAPPostReadRequestControl postReadRequest;

  /** The LDAP pre-read request control, if present in the request. */
  private LDAPPreReadRequestControl preReadRequest;

  /** The new RDN for the entry. */
  private RDN newRDN;



  /**
   * Creates a new operation that may be used to move an entry in a
   * local backend of the Directory Server.
   *
   * @param operation The operation to enhance.
   */
  public LocalBackendModifyDNOperation (ModifyDNOperation operation)
  {
    super(operation);
    LocalBackendWorkflowElement.attachLocalOperation (operation, this);
  }



  /**
   * Retrieves the current entry, before it is renamed.  This will not be
   * available to pre-parse plugins or during the conflict resolution portion of
   * the synchronization processing.
   *
   * @return  The current entry, or <CODE>null</CODE> if it is not yet
   *           available.
   */
  @Override
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
  @Override
  public final Entry getUpdatedEntry()
  {
    return newEntry;
  }



  /**
   * Process this modify DN operation in a local backend.
   *
   * @param backend
   *          The backend on which operation is performed.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  public void processLocalModifyDN(final LocalBackend<?> backend)
      throws CanceledOperationException
  {
    this.backend = backend;

    clientConnection = getClientConnection();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    try
    {
      AtomicBoolean executePostOpPlugins = new AtomicBoolean(false);
      processModifyDN(executePostOpPlugins);

      // Invoke the post-operation or post-synchronization modify DN plugins.
      if (isSynchronizationOperation())
      {
        if (getResultCode() == ResultCode.SUCCESS)
        {
          getPluginConfigManager().invokePostSynchronizationModifyDNPlugins(this);
        }
      }
      else if (executePostOpPlugins.get())
      {
        if (!processOperationResult(this, getPluginConfigManager().invokePostOperationModifyDNPlugins(this)))
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
            psearch.processModifyDN(newEntry, currentEntry.getName());
          }
        }
      });
    }
  }

  private void processModifyDN(AtomicBoolean executePostOpPlugins)
      throws CanceledOperationException
  {
    // Process the entry DN, newRDN, and newSuperior elements from their raw
    // forms as provided by the client to the forms required for the rest of
    // the modify DN processing.
    entryDN = getEntryDN();

    newRDN = getNewRDN();
    if (newRDN == null)
    {
      return;
    }

    DN newSuperior = getNewSuperior();
    if (newSuperior == null && getRawNewSuperior() != null)
    {
      return;
    }

    // Construct the new DN to use for the entry.
    BackendConfigManager backendConfigManager =
        DirectoryServer.getInstance().getServerContext().getBackendConfigManager();
    DN parentDN;
    if (newSuperior == null)
    {
      parentDN = backendConfigManager.getParentDNInSuffix(entryDN);
    }
    else
    {
      if (newSuperior.isSubordinateOrEqualTo(entryDN))
      {
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(ERR_MODDN_NEW_SUPERIOR_IN_SUBTREE.get(entryDN, newSuperior));
        return;
      }
      parentDN = newSuperior;
    }

    if (parentDN == null || parentDN.isRootDN())
    {
      setResultCode(ResultCode.UNWILLING_TO_PERFORM);
      appendErrorMessage(ERR_MODDN_NO_PARENT.get(entryDN));
      return;
    }

    DN newDN = parentDN.child(newRDN);

    // Get the backend for the current entry, and the backend for the new
    // entry. If either is null, or if they are different, then fail.
    LocalBackend<?> currentBackend = backend;
    if (currentBackend == null)
    {
      setResultCode(ResultCode.NO_SUCH_OBJECT);
      appendErrorMessage(ERR_MODDN_NO_BACKEND_FOR_CURRENT_ENTRY.get(entryDN));
      return;
    }

    LocalBackend<?> newBackend = backendConfigManager.findLocalBackendForEntry(newDN);
    if (newBackend == null)
    {
      setResultCode(ResultCode.NO_SUCH_OBJECT);
      appendErrorMessage(ERR_MODDN_NO_BACKEND_FOR_NEW_ENTRY.get(entryDN, newDN));
      return;
    }
    else if (!currentBackend.equals(newBackend))
    {
      setResultCode(ResultCode.UNWILLING_TO_PERFORM);
      appendErrorMessage(ERR_MODDN_DIFFERENT_BACKENDS.get(entryDN, newDN));
      return;
    }

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    /*
     * Acquire subtree write locks for the current and new DN. Be careful to avoid deadlocks by
     * taking the locks in a well defined order.
     */
    DNLock currentLock = null;
    DNLock newLock = null;
    try
    {
      if (entryDN.compareTo(newDN) < 0)
      {
        currentLock = DirectoryServer.getLockManager().tryWriteLockSubtree(entryDN);
        newLock = DirectoryServer.getLockManager().tryWriteLockSubtree(newDN);
      }
      else
      {
        newLock = DirectoryServer.getLockManager().tryWriteLockSubtree(newDN);
        currentLock = DirectoryServer.getLockManager().tryWriteLockSubtree(entryDN);
      }

      if (currentLock == null)
      {
        setResultCode(ResultCode.BUSY);
        appendErrorMessage(ERR_MODDN_CANNOT_LOCK_CURRENT_DN.get(entryDN));
        return;
      }

      if (newLock == null)
      {
        setResultCode(ResultCode.BUSY);
        appendErrorMessage(ERR_MODDN_CANNOT_LOCK_NEW_DN.get(entryDN, newDN));
        return;
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // Get the current entry from the appropriate backend. If it doesn't
      // exist, then fail.
      currentEntry = currentBackend.getEntry(entryDN);

      if (getOriginalEntry() == null)
      {
        // See if one of the entry's ancestors exists.
        setMatchedDN(findMatchedDN(entryDN));

        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_MODDN_NO_CURRENT_ENTRY.get(entryDN));
        return;
      }

      // Check to see if there are any controls in the request. If so, then
      // see if there is any special processing required.
      handleRequestControls();

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
        if (!getAccessControlHandler().isAllowed(this))
        {
          setResultCodeAndMessageNoInfoDisclosure(currentEntry, entryDN,
              ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_MODDN_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(entryDN));
          return;
        }
      }
      catch (DirectoryException e)
      {
        setResultCode(e.getResultCode());
        appendErrorMessage(e.getMessageObject());
        return;
      }

      // Duplicate the entry and set its new DN. Also, create an empty list
      // to hold the attribute-level modifications.
      newEntry = currentEntry.duplicate(false);
      newEntry.setDN(newDN);

      // init the modifications
      addModification(null);
      List<Modification> modifications = getModifications();

      if (!handleConflictResolution())
      {
        return;
      }

      // Apply any changes to the entry based on the change in its RDN.
      // Also perform schema checking on the updated entry.
      applyRDNChanges(modifications);

      // If the operation is not a synchronization operation,
      // - Apply the RDN changes.
      // - Invoke the pre-operation modify DN plugins.
      // - apply additional modifications provided by the plugins.
      // If the operation is a synchronization operation
      // - apply the operation as it was originally done on the master.
      if (!isSynchronizationOperation())
      {
        // Check for a request to cancel this operation.
        checkIfCanceled(false);

        // Get a count of the current number of modifications. The
        // pre-operation plugins may alter this list, and we need to be able
        // to identify which changes were made after they're done.
        int modCount = modifications.size();

        executePostOpPlugins.set(true);
        if (!processOperationResult(this, getPluginConfigManager().invokePreOperationModifyDNPlugins(this)))
        {
          return;
        }

        // Check to see if any of the pre-operation plugins made any changes
        // to the entry. If so, then apply them.
        if (modifications.size() > modCount)
        {
          applyPreOpModifications(modifications, modCount, true);
        }
      }
      else
      {
        applyPreOpModifications(modifications, 0, false);
      }

      LocalBackendWorkflowElement.checkIfBackendIsWritable(currentBackend,
          this, entryDN, ERR_MODDN_SERVER_READONLY, ERR_MODDN_BACKEND_READONLY);

      if (noOp)
      {
        appendErrorMessage(INFO_MODDN_NOOP.get());
        setResultCode(ResultCode.NO_OPERATION);
      }
      else
      {
        if (!processPreOperation())
        {
          return;
        }
        currentBackend.renameEntry(entryDN, newEntry, this);
        if (trx!=null) {
          trx.success(this);
        }
      }

      // Attach the pre-read and/or post-read controls to the response if
      // appropriate.
      LocalBackendWorkflowElement.addPreReadResponse(this, preReadRequest,
          currentEntry);
      LocalBackendWorkflowElement.addPostReadResponse(this, postReadRequest,
          newEntry);

      if (!noOp)
      {
        setResultCode(ResultCode.SUCCESS);
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      setResponseData(de);
      return;
    }
    finally
    {
      if (currentLock != null)
      {
        currentLock.unlock();
      }
      if (newLock != null)
      {
        newLock.unlock();
      }
      processSynchPostOperationPlugins();
    }
  }

  private DirectoryException newDirectoryException(Entry entry,
      ResultCode resultCode, LocalizableMessage message) throws DirectoryException
  {
    return LocalBackendWorkflowElement.newDirectoryException(this, entry, null,
        resultCode, message, ResultCode.NO_SUCH_OBJECT,
        ERR_MODDN_NO_CURRENT_ENTRY.get(entryDN));
  }

  private void setResultCodeAndMessageNoInfoDisclosure(Entry entry, DN entryDN,
      ResultCode realResultCode, LocalizableMessage realMessage) throws DirectoryException
  {
    LocalBackendWorkflowElement.setResultCodeAndMessageNoInfoDisclosure(this,
        entry, entryDN, realResultCode, realMessage, ResultCode.NO_SUCH_OBJECT,
        ERR_MODDN_NO_CURRENT_ENTRY.get(entryDN));
  }

  /**
   * Processes the set of controls included in the request.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify DN operation to fail.
   */
  private void handleRequestControls() throws DirectoryException
  {
    LocalBackendWorkflowElement.evaluateProxyAuthControls(this);
    LocalBackendWorkflowElement.removeAllDisallowedControls(entryDN, this);

    for (ListIterator<Control> iter = getRequestControls().listIterator(); iter.hasNext();)
    {
      final Control c = iter.next();
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

          throw newDirectoryException(currentEntry, de.getResultCode(),
              ERR_MODDN_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
        }

        // Check if the current user has permission to make this determination.
        if (!getAccessControlHandler().isAllowed(this, currentEntry, filter))
        {
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
        }

        try
        {
          if (!filter.matchesEntry(currentEntry))
          {
            throw newDirectoryException(currentEntry, ResultCode.ASSERTION_FAILED,
                ERR_MODDN_ASSERTION_FAILED.get(entryDN));
          }
        }
        catch (DirectoryException de)
        {
          if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
          {
            throw de;
          }

          logger.traceException(de);

          throw newDirectoryException(currentEntry, de.getResultCode(),
              ERR_MODDN_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
        }
      }
      else if (OID_LDAP_NOOP_OPENLDAP_ASSIGNED.equals(oid))
      {
        noOp = true;
      }
      else if (OID_LDAP_READENTRY_PREREAD.equals(oid))
      {
        preReadRequest = getRequestControl(LDAPPreReadRequestControl.DECODER);
        iter.set(preReadRequest);
      }
      else if (OID_LDAP_READENTRY_POSTREAD.equals(oid))
      {
        if (c instanceof LDAPPostReadRequestControl)
        {
          postReadRequest = (LDAPPostReadRequestControl) c;
        }
        else
        {
          postReadRequest = getRequestControl(LDAPPostReadRequestControl.DECODER);
          iter.set(postReadRequest);
        }
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
        throw new DirectoryException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
            ERR_MODDN_UNSUPPORTED_CRITICAL_CONTROL.get(entryDN, oid));
      }
    }
  }
  ClientConnection.Transaction trx=null;

  private AccessControlHandler<?> getAccessControlHandler()
  {
    return AccessControlConfigManager.getInstance().getAccessControlHandler();
  }

  /**
   * Updates the entry so that its attributes are changed to reflect the changes
   * to the RDN.  This also performs schema checking on the updated entry.
   *
   * @param  modifications  A list to hold the modifications made to the entry.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify DN operation to fail.
   */
  private void applyRDNChanges(List<Modification> modifications)
          throws DirectoryException
  {
    // If we should delete the old RDN values from the entry, then do so.
    if (deleteOldRDN())
    {
      for (AVA ava : entryDN.rdn())
      {
        Attribute a = Attributes.create(
            ava.getAttributeType(),
            ava.getAttributeName(),
            ava.getAttributeValue());

        // If the associated attribute type is marked NO-USER-MODIFICATION, then
        // refuse the update.
        if (a.getAttributeDescription().getAttributeType().isNoUserModification()
            && !isInternalOperation()
            && !isSynchronizationOperation())
        {
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODDN_OLD_RDN_ATTR_IS_NO_USER_MOD.get(entryDN, a.getAttributeDescription()));
        }

        List<ByteString> missingValues = new LinkedList<>();
        newEntry.removeAttribute(a, missingValues);

        if (missingValues.isEmpty())
        {
          modifications.add(new Modification(ModificationType.DELETE, a));
        }
      }
    }


    // Add the new RDN values to the entry.
    for (AVA ava : newRDN)
    {
      Attribute a = Attributes.create(
          ava.getAttributeType(),
          ava.getAttributeName(),
          ava.getAttributeValue());

      List<ByteString> duplicateValues = new LinkedList<>();
      newEntry.addAttribute(a, duplicateValues);

      if (duplicateValues.isEmpty())
      {
        // If the associated attribute type is marked NO-USER-MODIFICATION, then
        // refuse the update.
        if (a.getAttributeDescription().getAttributeType().isNoUserModification())
        {
          if (!isInternalOperation() && !isSynchronizationOperation())
          {
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_MODDN_NEW_RDN_ATTR_IS_NO_USER_MOD.get(entryDN, a.getAttributeDescription()));
          }
        }
        else
        {
          modifications.add(new Modification(ModificationType.ADD, a));
        }
      }
    }

    // If the server is configured to check the schema and the operation is not
    // a synchronization operation, make sure that the resulting entry is valid
    // as per the server schema.
    if (DirectoryServer.getCoreConfigManager().isCheckSchema() && !isSynchronizationOperation())
    {
      LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
      if (! newEntry.conformsToSchema(null, false, true, true,
                                      invalidReason))
      {
        throw new DirectoryException(newEntry.getTypeConformsToSchemaError(),
            ERR_MODDN_VIOLATES_SCHEMA.get(entryDN, invalidReason));
      }

      for (AVA ava : newRDN)
      {
        AttributeType at = ava.getAttributeType();
        if (at.isObsolete())
        {
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODDN_NEWRDN_ATTR_IS_OBSOLETE.get(entryDN, at.getNameOrOID()));
        }
      }
    }
  }



  /**
   * Applies any modifications performed during pre-operation plugin processing.
   * This also performs schema checking for the updated entry.
   *
   * @param  modifications  A list containing the modifications made to the
   *                        entry.
   * @param  startPos       The position in the list at which the pre-operation
   *                        modifications start.
   * @param  checkSchema    A boolean allowing to control if schema must be
   *                        checked
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify DN operation to fail.
   */
  private void applyPreOpModifications(List<Modification> modifications,
                                       int startPos, boolean checkSchema)
          throws DirectoryException
  {
    for (int i=startPos; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      switch (m.getModificationType().asEnum())
      {
        case ADD:
          List<ByteString> duplicateValues = new LinkedList<>();
          newEntry.addAttribute(a, duplicateValues);
          break;

        case DELETE:
          List<ByteString> missingValues = new LinkedList<>();
          newEntry.removeAttribute(a, missingValues);
          break;

        case REPLACE:
          newEntry.replaceAttribute(a);
          break;

        case INCREMENT:
          newEntry.incrementAttribute(a);
          break;
      }
    }


    // Make sure that the updated entry still conforms to the server
    // schema.
    if (DirectoryServer.getCoreConfigManager().isCheckSchema() && checkSchema)
    {
      LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
      if (! newEntry.conformsToSchema(null, false, true, true,
                                      invalidReason))
      {
        throw new DirectoryException(newEntry.getTypeConformsToSchemaError(),
            ERR_MODDN_PREOP_VIOLATES_SCHEMA.get(entryDN, invalidReason));
      }
    }
  }



  /**
   * Handle conflict resolution.
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   */
  private boolean handleConflictResolution()
  {
      for (SynchronizationProvider<?> provider : getSynchronizationProviders()) {
          try {
              if (!processOperationResult(this, provider.handleConflictResolution(this))) {
                  return false;
              }
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_MODDN_SYNCH_CONFLICT_RESOLUTION_FAILED,
                  getConnectionID(), getOperationID(), getExceptionMessage(de));

              setResponseData(de);
              return false;
          }
      }
      return true;
  }

  /**
   * Process pre operation.
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   */
  private boolean processPreOperation()
  {
      for (SynchronizationProvider<?> provider : getSynchronizationProviders()) {
          try {
              if (!processOperationResult(this, provider.doPreOperation(this))) {
                  return false;
              }
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_MODDN_SYNCH_PREOP_FAILED, getConnectionID(),
                      getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return false;
          }
      }
      return true;
  }

  /**
   * Invoke post operation synchronization providers.
   */
  private void processSynchPostOperationPlugins()
  {
      for (SynchronizationProvider<?> provider : DirectoryServer
              .getSynchronizationProviders()) {
          try {
              provider.doPostOperation(this);
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_MODDN_SYNCH_POSTOP_FAILED, getConnectionID(),
                      getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return;
          }
      }
  }

  @Override
  public void rollback() throws CanceledOperationException, DirectoryException {
     backend.renameEntry(newEntry.getName(), currentEntry, this);
  }
}
